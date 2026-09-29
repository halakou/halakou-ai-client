package com.example.halakou.domain.orchestrator

import com.example.halakou.domain.model.LlmProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Autonomous Background Aggregator for halakou.
 * Maintains, discovers, scores, and health-checks free endpoints.
 */
class AutonomousAggregator(
    private val client: OkHttpClient = defaultClient
) {
    companion object {
        private val defaultClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    private val endpointsMap = ConcurrentHashMap<String, OrchestratedEndpoint>()

    init {
        registerDefaultPool()
    }

    private fun registerDefaultPool() {
        // Priority 1: Primary Text Engine strictly set to Atria ASI
        registerEndpoint(
            OrchestratedEndpoint(
                key = "atria-dawn-primary",
                provider = LlmProvider.ATRIA_ASI,
                modelId = "Atria-Dawn-Preview",
                baseUrl = "https://api.atria-asi.ai/v1",
                isVisionSupported = false,
                isPrimaryText = true,
                priority = 1
            )
        )

        // Priority 2: Primary Free Vision Multimodal Engine
        registerEndpoint(
            OrchestratedEndpoint(
                key = "openrouter-gemini-vision-free",
                provider = LlmProvider.OPENROUTER,
                modelId = "google/gemini-2.0-flash-exp:free",
                baseUrl = "https://openrouter.ai/api/v1",
                isVisionSupported = true,
                priority = 2
            )
        )

        // Priority 3: Meta Llama 3.2 Vision (Free Tier)
        registerEndpoint(
            OrchestratedEndpoint(
                key = "openrouter-llama-vision-free",
                provider = LlmProvider.OPENROUTER,
                modelId = "meta-llama/llama-3.2-11b-vision-instruct:free",
                baseUrl = "https://openrouter.ai/api/v1",
                isVisionSupported = true,
                priority = 3
            )
        )

        // Priority 4: Google Gemini Direct Multimodal Free Tier
        registerEndpoint(
            OrchestratedEndpoint(
                key = "gemini-direct-free",
                provider = LlmProvider.GEMINI,
                modelId = "gemini-2.5-flash",
                baseUrl = "https://generativelanguage.googleapis.com",
                isVisionSupported = true,
                priority = 4
            )
        )

        // Priority 5: High-speed Text Fallback (Llama 3.3 70B Free)
        registerEndpoint(
            OrchestratedEndpoint(
                key = "openrouter-llama70b-free",
                provider = LlmProvider.OPENROUTER,
                modelId = "meta-llama/llama-3.3-70b-instruct:free",
                baseUrl = "https://openrouter.ai/api/v1",
                isVisionSupported = false,
                priority = 5
            )
        )

        // Priority 6: Deep Reasoning Fallback (DeepSeek R1 Free)
        registerEndpoint(
            OrchestratedEndpoint(
                key = "openrouter-deepseek-r1-free",
                provider = LlmProvider.OPENROUTER,
                modelId = "deepseek/deepseek-r1:free",
                baseUrl = "https://openrouter.ai/api/v1",
                isVisionSupported = false,
                priority = 6
            )
        )

        // Priority 7: Local Offline Hardware Fallback (Ollama)
        registerEndpoint(
            OrchestratedEndpoint(
                key = "ollama-local-host",
                provider = LlmProvider.OLLAMA,
                modelId = "llama3:latest",
                baseUrl = "http://10.0.2.2:11434",
                isVisionSupported = false,
                priority = 7
            )
        )
    }

    fun registerEndpoint(endpoint: OrchestratedEndpoint) {
        endpointsMap[endpoint.key] = endpoint
    }

    /**
     * Synchronizes endpoints dynamically loaded from GitHub Pages static config.json.
     */
    fun syncFromRemoteConfig(config: com.example.halakou.data.remote.RemoteAppConfig) {
        for (ep in config.textEndpoints) {
            endpointsMap[ep.key] = ep
        }
        for (ep in config.visionEndpoints) {
            endpointsMap[ep.key] = ep
        }
    }

    fun getAllEndpoints(): List<OrchestratedEndpoint> {
        return endpointsMap.values.sortedBy { it.priority }
    }

    fun getHealthyEndpoints(): List<OrchestratedEndpoint> {
        return endpointsMap.values.filter { it.isHealthy }.sortedBy { it.priority }
    }

    /**
     * Background health probe to measure latency and test availability.
     */
    suspend fun probeEndpointsHealth() = withContext(Dispatchers.IO) {
        endpointsMap.values.forEach { ep ->
            try {
                val start = System.currentTimeMillis()
                val testUrl = if (ep.provider == LlmProvider.OPENROUTER) {
                    "https://openrouter.ai/api/v1/models"
                } else if (ep.provider == LlmProvider.OLLAMA) {
                    "${ep.baseUrl.trimEnd('/')}/api/tags"
                } else {
                    "${ep.baseUrl.trimEnd('/')}/"
                }

                val req = Request.Builder()
                    .url(testUrl)
                    .header("User-Agent", "halakou-health-probe/1.0")
                    .build()

                val resp = client.newCall(req).execute()
                ep.latencyMs = System.currentTimeMillis() - start
                ep.isHealthy = resp.isSuccessful || resp.code in listOf(401, 404, 405) // Server is responding
                ep.lastCheckTimestamp = System.currentTimeMillis()
                resp.close()
            } catch (_: Exception) {
                // Keep default status if probe fails or is blocked
                ep.lastCheckTimestamp = System.currentTimeMillis()
            }
        }
    }
}
