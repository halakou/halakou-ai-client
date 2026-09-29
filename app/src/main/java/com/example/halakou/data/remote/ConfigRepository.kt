package com.example.halakou.data.remote

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.halakou.domain.model.LlmProvider
import com.example.halakou.domain.orchestrator.OrchestratedEndpoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

data class RemoteAppConfig(
    val version: String = "2.0.0",
    val textEndpoints: List<OrchestratedEndpoint> = emptyList(),
    val visionEndpoints: List<OrchestratedEndpoint> = emptyList(),
    val freeDailyLimit: Int = 25,
    val isLoaded: Boolean = false,
    val lastUpdated: String = ""
)

/**
 * Serverless State Management via GitHub Pages.
 * 
 * Fetches static `config.json` hosted on GitHub Pages or raw GitHub,
 * caches it locally for instant zero-latency offline startup, and
 * updates the client-side circuit breaker candidate pool.
 */
class ConfigRepository(
    private val context: Context,
    private val client: OkHttpClient = defaultClient
) {
    companion object {
        private const val TAG = "ConfigRepository"
        private const val PREFS_NAME = "halakou_remote_config_prefs"
        private const val KEY_CACHED_JSON = "cached_config_json"

        // Default static endpoints hosted on GitHub Pages / GitHub raw
        const val PRIMARY_CONFIG_URL = "https://raw.githubusercontent.com/halakouai/halakou/gh-pages/config.json"
        const val FALLBACK_CONFIG_URL = "https://halakouai.github.io/halakou/config.json"

        private val defaultClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _configState = MutableStateFlow(RemoteAppConfig())
    val configState: StateFlow<RemoteAppConfig> = _configState.asStateFlow()

    init {
        // 1. Instant zero-latency load from local cache or bundled assets
        loadInitialConfig()
    }

    /**
     * Loads config from cache or APK assets so app starts in 0ms without waiting for network.
     */
    fun loadInitialConfig() {
        val cachedJson = prefs.getString(KEY_CACHED_JSON, null)
        if (!cachedJson.isNullOrBlank()) {
            val parsed = parseConfigJson(cachedJson)
            if (parsed != null) {
                _configState.value = parsed
                return
            }
        }

        // Fallback: Read bundled assets/default_config.json
        try {
            val assetJson = readAssetFile("default_config.json")
            if (assetJson.isNotBlank()) {
                val parsed = parseConfigJson(assetJson)
                if (parsed != null) {
                    _configState.value = parsed
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not load asset config: ${e.message}")
        }
    }

    /**
     * Fetches the latest dynamic config from GitHub Pages asynchronously.
     */
    suspend fun refreshRemoteConfig(customUrl: String? = null): Boolean = withContext(Dispatchers.IO) {
        val targetUrls = if (!customUrl.isNullOrBlank()) {
            listOf(customUrl)
        } else {
            listOf(PRIMARY_CONFIG_URL, FALLBACK_CONFIG_URL)
        }

        for (url in targetUrls) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "halakou-android-client/2.0")
                    .header("Cache-Control", "no-cache")
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val bodyString = response.body?.string() ?: ""
                    response.close()

                    if (bodyString.isNotBlank()) {
                        val parsed = parseConfigJson(bodyString)
                        if (parsed != null) {
                            // Persist to local cache
                            prefs.edit().putString(KEY_CACHED_JSON, bodyString).apply()
                            _configState.value = parsed
                            Log.d(TAG, "Successfully refreshed config from $url")
                            return@withContext true
                        }
                    }
                } else {
                    response.close()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to fetch remote config from $url: ${e.message}")
            }
        }
        return@withContext false
    }

    private fun parseConfigJson(jsonStr: String): RemoteAppConfig? {
        return try {
            val root = JSONObject(jsonStr)
            val version = root.optString("version", "2.0.0")
            val generatedAt = root.optString("generated_at", "")

            val monetizationObj = root.optJSONObject("monetization")
            val freeLimit = monetizationObj?.optInt("free_daily_limit", 25) ?: 25

            val textList = mutableListOf<OrchestratedEndpoint>()
            val textArray = root.optJSONArray("text_models")
            if (textArray != null) {
                for (i in 0 until textArray.length()) {
                    val item = textArray.getJSONObject(i)
                    textList.add(mapToOrchestratedEndpoint(item, false))
                }
            }

            val visionList = mutableListOf<OrchestratedEndpoint>()
            val visionArray = root.optJSONArray("vision_models")
            if (visionArray != null) {
                for (i in 0 until visionArray.length()) {
                    val item = visionArray.getJSONObject(i)
                    visionList.add(mapToOrchestratedEndpoint(item, true))
                }
            }

            RemoteAppConfig(
                version = version,
                textEndpoints = textList,
                visionEndpoints = visionList,
                freeDailyLimit = freeLimit,
                isLoaded = true,
                lastUpdated = generatedAt
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing config JSON", e)
            null
        }
    }

    private fun mapToOrchestratedEndpoint(json: JSONObject, isVisionDefault: Boolean): OrchestratedEndpoint {
        val providerStr = json.optString("provider", "atria").lowercase()
        val provider = when {
            providerStr.contains("atria") -> LlmProvider.ATRIA_ASI
            providerStr.contains("openrouter") -> LlmProvider.OPENROUTER
            providerStr.contains("gemini") -> LlmProvider.GEMINI
            providerStr.contains("deepseek") -> LlmProvider.DEEPSEEK
            providerStr.contains("ollama") -> LlmProvider.OLLAMA
            else -> LlmProvider.ATRIA_ASI
        }

        return OrchestratedEndpoint(
            key = json.optString("id", "${provider.id}-${json.optString("model")}"),
            provider = provider,
            modelId = json.optString("model", provider.defaultModel),
            baseUrl = json.optString("base_url", provider.defaultBaseUrl),
            isVisionSupported = json.optBoolean("is_vision", isVisionDefault),
            isPrimaryText = json.optBoolean("is_primary_text", false),
            priority = json.optInt("priority", 10),
            latencyMs = json.optLong("latency_ms", 120),
            isHealthy = json.optBoolean("is_healthy", true)
        )
    }

    private fun readAssetFile(filename: String): String {
        return context.assets.open(filename).use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                reader.readText()
            }
        }
    }
}
