package com.example.halakou.data.remote

import com.example.halakou.domain.model.FreeModelCategory
import com.example.halakou.domain.model.FreeModelInfo
import com.example.halakou.domain.model.LlmProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class FreeModelDiscoveryService(
    private val client: OkHttpClient = defaultClient
) {
    companion object {
        private val defaultClient = OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Curated fallback list of guaranteed free models across Vision, Reasoning, and Coding.
     */
    val curatedFreeModels: List<FreeModelInfo> = listOf(
        FreeModelInfo(
            id = "google/gemini-2.0-flash-exp:free",
            name = "Gemini 2.0 Flash (Free)",
            provider = LlmProvider.OPENROUTER,
            isVisionCapable = true,
            category = FreeModelCategory.VISION,
            description = "Google's ultra-fast multimodal model supporting real-time image, visual question answering, and OCR.",
            contextLength = 1048576
        ),
        FreeModelInfo(
            id = "meta-llama/llama-3.2-11b-vision-instruct:free",
            name = "Llama 3.2 11B Vision (Free)",
            provider = LlmProvider.OPENROUTER,
            isVisionCapable = true,
            category = FreeModelCategory.VISION,
            description = "Meta's flagship open-weights vision-language model for image reasoning, diagram analysis, and chat.",
            contextLength = 131072
        ),
        FreeModelInfo(
            id = "gemini-2.5-flash",
            name = "Google Gemini 2.5 Flash (Direct Free Tier)",
            provider = LlmProvider.GEMINI,
            isVisionCapable = true,
            category = FreeModelCategory.VISION,
            description = "Google AI Studio generous 15 RPM / 1M TPM free tier with native multimodal image & document understanding.",
            contextLength = 1000000
        ),
        FreeModelInfo(
            id = "deepseek/deepseek-r1:free",
            name = "DeepSeek R1 Reasoning (Free)",
            provider = LlmProvider.OPENROUTER,
            isVisionCapable = false,
            category = FreeModelCategory.REASONING,
            description = "Open-weights chain-of-thought mathematical and logical reasoning model competing with OpenAI o1.",
            contextLength = 65536
        ),
        FreeModelInfo(
            id = "Atria-Dawn-Preview",
            name = "Atria ASI Dawn (744B MoE)",
            provider = LlmProvider.ATRIA_ASI,
            isVisionCapable = false,
            category = FreeModelCategory.REASONING,
            description = "Shanghai AI Lab 744-billion parameter agentic MoE model with continuous reasoning & free test tier.",
            contextLength = 128000
        ),
        FreeModelInfo(
            id = "qwen/qwen-2.5-coder-32b-instruct:free",
            name = "Qwen 2.5 Coder 32B (Free)",
            provider = LlmProvider.OPENROUTER,
            isVisionCapable = false,
            category = FreeModelCategory.CODING,
            description = "Top-tier open-source coding engine with specialized syntax analysis and bug fixing.",
            contextLength = 32768
        ),
        FreeModelInfo(
            id = "meta-llama/llama-3.3-70b-instruct:free",
            name = "Llama 3.3 70B Instruct (Free)",
            provider = LlmProvider.OPENROUTER,
            isVisionCapable = false,
            category = FreeModelCategory.GENERAL,
            description = "High-intelligence open flagship model for natural dialog, writing, and problem-solving.",
            contextLength = 131072
        ),
        FreeModelInfo(
            id = "llava:latest",
            name = "Ollama LLaVA (Local Vision)",
            provider = LlmProvider.OLLAMA,
            isVisionCapable = true,
            category = FreeModelCategory.VISION,
            description = "100% private, self-hosted visual reasoning running directly on your local computer.",
            contextLength = 8192
        )
    )

    /**
     * Discovers all free models dynamically from OpenRouter public catalog and local Ollama.
     */
    suspend fun discoverAllFreeModels(ollamaBaseUrl: String = "http://10.0.2.2:11434"): List<FreeModelInfo> = withContext(Dispatchers.IO) {
        val discoveredList = mutableListOf<FreeModelInfo>()

        // 1. Fetch live free models from OpenRouter catalog
        try {
            val request = Request.Builder()
                .url("https://openrouter.ai/api/v1/models")
                .header("User-Agent", "halakou-open-source/1.0")
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                val json = JSONObject(body)
                val data = json.optJSONArray("data")
                if (data != null) {
                    for (i in 0 until data.length()) {
                        val m = data.getJSONObject(i)
                        val id = m.optString("id", "")
                        val name = m.optString("name", id)
                        val description = m.optString("description", "")
                        val pricing = m.optJSONObject("pricing")
                        val promptCost = pricing?.optString("prompt", "0") ?: "0"
                        val completionCost = pricing?.optString("completion", "0") ?: "0"

                        val isFree = id.endsWith(":free") || (promptCost == "0" && completionCost == "0")

                        if (isFree) {
                            val contextLength = m.optInt("context_length", 32768)
                            val architecture = m.optJSONObject("architecture")
                            val modality = architecture?.optString("modality", "") ?: ""

                            val isVision = id.contains("vision", ignoreCase = true) ||
                                    id.contains("flash", ignoreCase = true) ||
                                    modality.contains("image", ignoreCase = true) ||
                                    description.contains("vision", ignoreCase = true) ||
                                    description.contains("image input", ignoreCase = true)

                            val category = when {
                                isVision -> FreeModelCategory.VISION
                                id.contains("r1", ignoreCase = true) || id.contains("reason", ignoreCase = true) || id.contains("thinking", ignoreCase = true) -> FreeModelCategory.REASONING
                                id.contains("coder", ignoreCase = true) || id.contains("code", ignoreCase = true) -> FreeModelCategory.CODING
                                else -> FreeModelCategory.GENERAL
                            }

                            discoveredList.add(
                                FreeModelInfo(
                                    id = id,
                                    name = name,
                                    provider = LlmProvider.OPENROUTER,
                                    isVisionCapable = isVision,
                                    category = category,
                                    description = description.take(150),
                                    contextLength = contextLength
                                )
                            )
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Graceful network fallback
        }

        // 2. Discover local Ollama models
        try {
            val rootUrl = ollamaBaseUrl.trimEnd('/')
            val request = Request.Builder()
                .url("$rootUrl/api/tags")
                .build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                val json = JSONObject(body)
                val models = json.optJSONArray("models")
                if (models != null) {
                    for (i in 0 until models.length()) {
                        val m = models.getJSONObject(i)
                        val name = m.optString("name", "")
                        val isVision = name.contains("llava", ignoreCase = true) || name.contains("vision", ignoreCase = true) || name.contains("bakllava", ignoreCase = true)
                        discoveredList.add(
                            FreeModelInfo(
                                id = name,
                                name = "$name (Ollama Local)",
                                provider = LlmProvider.OLLAMA,
                                isVisionCapable = isVision,
                                category = if (isVision) FreeModelCategory.VISION else FreeModelCategory.GENERAL,
                                description = "Local hardware model running privately on your machine.",
                                contextLength = 8192
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {}

        // 3. Merge with curated fallback models ensuring no duplicates
        val allIds = discoveredList.map { it.id }.toSet()
        val missingCurated = curatedFreeModels.filter { it.id !in allIds }
        val combined = discoveredList + missingCurated

        // Sort: Vision first, then Reasoning, then Coding, then General
        combined.sortedWith(
            compareBy<FreeModelInfo> {
                when (it.category) {
                    FreeModelCategory.VISION -> 0
                    FreeModelCategory.REASONING -> 1
                    FreeModelCategory.CODING -> 2
                    FreeModelCategory.GENERAL -> 3
                }
            }.thenBy { it.name }
        )
    }

    /**
     * Auto-detect and pick the best Vision model.
     */
    fun findBestVisionModel(models: List<FreeModelInfo>): FreeModelInfo? {
        return models.find { it.isVisionCapable && it.id.contains("gemini", ignoreCase = true) }
            ?: models.find { it.isVisionCapable }
    }

    /**
     * Auto-detect and pick the best Reasoning model.
     */
    fun findBestReasoningModel(models: List<FreeModelInfo>): FreeModelInfo? {
        return models.find { it.category == FreeModelCategory.REASONING && (it.id.contains("r1", ignoreCase = true) || it.id.contains("atria", ignoreCase = true)) }
            ?: models.find { it.category == FreeModelCategory.REASONING }
    }

    /**
     * Auto-detect and pick the best Coding model.
     */
    fun findBestCodingModel(models: List<FreeModelInfo>): FreeModelInfo? {
        return models.find { it.category == FreeModelCategory.CODING }
            ?: models.firstOrNull()
    }
}
