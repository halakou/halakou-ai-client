package com.example.halakou.domain.orchestrator

import com.example.halakou.data.remote.LlmGateway
import com.example.halakou.data.security.SecureVaultRepository
import com.example.halakou.domain.model.ChatMessage
import com.example.halakou.domain.model.ModelSettings
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class OrchestratedResponse(
    val content: String,
    val endpointUsed: OrchestratedEndpoint,
    val wasFallback: Boolean = false,
    val fallbackEvent: FallbackEvent? = null
)

/**
 * Autonomous AI Orchestrator running locally on the device.
 * Slices through failures using a zero-dependency Circuit Breaker and Smart Multi-Modal Router.
 */
class ModelOrchestrator(
    val aggregator: AutonomousAggregator,
    val circuitBreaker: CircuitBreaker,
    val taskRouter: TaskRouter,
    val llmGateway: LlmGateway,
    val vaultRepo: SecureVaultRepository,
    val latencyMonitor: LatencyMonitor? = null
) {
    private val _fallbackEvents = MutableSharedFlow<FallbackEvent>(extraBufferCapacity = 5)
    val fallbackEvents: SharedFlow<FallbackEvent> = _fallbackEvents.asSharedFlow()

    /**
     * Executes generation autonomously with automatic Multi-Modal routing and Circuit Breaker failover.
     */
    suspend fun execute(
        userPrompt: String,
        hasImageAttachments: Boolean,
        messages: List<ChatMessage>,
        settings: ModelSettings,
        systemPromptWithTools: String,
        forcedEndpoint: OrchestratedEndpoint? = null
    ): Result<OrchestratedResponse> {
        val routing = taskRouter.resolveRoute(userPrompt, hasImageAttachments, forcedEndpoint)
        val candidateQueue = mutableListOf(routing.selectedEndpoint)
        candidateQueue.addAll(routing.candidateFallbacks.filter { circuitBreaker.canExecute(it.key) })

        var lastError: Throwable? = null
        var previousEndpoint: OrchestratedEndpoint? = null

        for (candidate in candidateQueue) {
            if (!circuitBreaker.canExecute(candidate.key)) {
                continue
            }

            // If a previous candidate failed and we are falling back:
            if (previousEndpoint != null) {
                val fallbackEvent = FallbackEvent(
                    fromModel = previousEndpoint.modelId,
                    toModel = candidate.modelId,
                    reason = lastError?.localizedMessage ?: "Endpoint failover"
                )
                _fallbackEvents.tryEmit(fallbackEvent)
            }

            val apiKey = vaultRepo.getApiKey(candidate.provider)
            val baseUrl = candidate.baseUrl

            val startCallTime = System.currentTimeMillis()
            val result = llmGateway.generateResponse(
                provider = candidate.provider,
                modelId = candidate.modelId,
                apiKey = apiKey,
                baseUrl = baseUrl,
                messages = messages,
                settings = settings,
                systemPromptWithTools = systemPromptWithTools
            )
            val elapsedMs = System.currentTimeMillis() - startCallTime
            latencyMonitor?.recordObservation(candidate.key, elapsedMs, result.isSuccess)

            if (result.isSuccess) {
                circuitBreaker.recordSuccess(candidate.key)
                val responseContent = result.getOrNull() ?: ""
                val wasFallback = previousEndpoint != null
                val fallbackEvent = if (wasFallback) {
                    FallbackEvent(
                        fromModel = previousEndpoint!!.modelId,
                        toModel = candidate.modelId,
                        reason = "Circuit Breaker auto-routed after failure"
                    )
                } else null

                return Result.success(
                    OrchestratedResponse(
                        content = responseContent,
                        endpointUsed = candidate,
                        wasFallback = wasFallback,
                        fallbackEvent = fallbackEvent
                    )
                )
            } else {
                val error = result.exceptionOrNull() ?: Exception("Unknown generation error")
                lastError = error
                val tripped = circuitBreaker.recordFailure(candidate.key, error)
                previousEndpoint = candidate

                // Continue loop immediately to fallback candidate without interrupting user!
            }
        }

        return Result.failure(
            lastError ?: Exception("All aggregated free endpoints in the circuit breaker pool failed.")
        )
    }
}
