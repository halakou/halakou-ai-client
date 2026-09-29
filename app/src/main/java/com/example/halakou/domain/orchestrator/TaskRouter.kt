package com.example.halakou.domain.orchestrator

enum class OrchestratedTaskType {
    TEXT_ONLY,
    VISION_MULTIMODAL,
    REASONING_MATH,
    CODE_SYNTHESIS
}

data class RoutingDecision(
    val selectedEndpoint: OrchestratedEndpoint,
    val taskType: OrchestratedTaskType,
    val isVisionBypass: Boolean,
    val candidateFallbacks: List<OrchestratedEndpoint>
)

/**
 * Intelligent Multi-Modal Router for halakou.
 * Routes text payloads to Atria ASI by default, but dynamically intercepts
 * visual or image-based queries and routes them to complementary Free Vision Models.
 */
class TaskRouter(
    private val aggregator: AutonomousAggregator,
    private val circuitBreaker: CircuitBreaker
) {
    /**
     * Determines whether the user's intent is visual / multimodal.
     */
    fun detectTaskType(userPrompt: String, hasImageAttachments: Boolean): OrchestratedTaskType {
        if (hasImageAttachments) return OrchestratedTaskType.VISION_MULTIMODAL

        val lower = userPrompt.lowercase()
        val visionKeywords = listOf(
            "describe this image", "what is in this picture", "look at this photo",
            "read text from image", "ocr this", "analyze this diagram", "visual question"
        )
        if (visionKeywords.any { lower.contains(it) }) {
            return OrchestratedTaskType.VISION_MULTIMODAL
        }

        if (lower.contains("prove that") || lower.contains("step by step reasoning") || lower.contains("solve math")) {
            return OrchestratedTaskType.REASONING_MATH
        }

        if (lower.contains("write code") || lower.contains("refactor") || lower.contains("kotlin function") || lower.contains("debug this error")) {
            return OrchestratedTaskType.CODE_SYNTHESIS
        }

        return OrchestratedTaskType.TEXT_ONLY
    }

    /**
     * Resolves the primary execution endpoint and pre-computes the fallback sequence.
     */
    fun resolveRoute(
        userPrompt: String,
        hasImageAttachments: Boolean,
        forcedEndpoint: OrchestratedEndpoint? = null
    ): RoutingDecision {
        val taskType = detectTaskType(userPrompt, hasImageAttachments)
        val allEndpoints = aggregator.getAllEndpoints()

        if (forcedEndpoint != null) {
            val fallbacks = allEndpoints.filter { it.key != forcedEndpoint.key }
            return RoutingDecision(forcedEndpoint, taskType, false, fallbacks)
        }

        return when (taskType) {
            OrchestratedTaskType.VISION_MULTIMODAL -> {
                // Atria ASI is text-only! Intercept and route to highest priority healthy Vision endpoint
                val visionEndpoints = allEndpoints.filter { it.isVisionSupported }
                val target = visionEndpoints.firstOrNull { circuitBreaker.canExecute(it.key) }
                    ?: visionEndpoints.firstOrNull()
                    ?: allEndpoints.first()

                val fallbacks = visionEndpoints.filter { it.key != target.key } +
                        allEndpoints.filter { !it.isVisionSupported }

                RoutingDecision(
                    selectedEndpoint = target,
                    taskType = taskType,
                    isVisionBypass = true,
                    candidateFallbacks = fallbacks
                )
            }
            OrchestratedTaskType.REASONING_MATH -> {
                // Primary is Atria ASI (744B MoE reasoning), with DeepSeek R1 as fallback
                val primaryAtria = allEndpoints.find { it.isPrimaryText && circuitBreaker.canExecute(it.key) }
                val target = primaryAtria ?: allEndpoints.first { circuitBreaker.canExecute(it.key) }
                val fallbacks = allEndpoints.filter { it.key != target.key }
                RoutingDecision(target, taskType, false, fallbacks)
            }
            else -> {
                // Standard text: Default strictly to Atria ASI (https://api.atria-asi.ai/)
                val primaryAtria = allEndpoints.find { it.isPrimaryText && circuitBreaker.canExecute(it.key) }
                val target = primaryAtria
                    ?: allEndpoints.firstOrNull { circuitBreaker.canExecute(it.key) }
                    ?: allEndpoints.first()

                val fallbacks = allEndpoints.filter { it.key != target.key }
                RoutingDecision(target, taskType, false, fallbacks)
            }
        }
    }
}
