package com.example.halakou.data.security

import android.content.Context
import android.content.SharedPreferences
import com.example.BuildConfig
import com.example.halakou.domain.model.LlmProvider
import com.example.halakou.domain.model.ModelSettings

/**
 * Secure Vault Repository managing encrypted LLM keys and model hyper-parameters.
 * Uses KeyStoreManager to encrypt all sensitive tokens before writing to disk.
 */
class SecureVaultRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("halakou_secure_vault_prefs", Context.MODE_PRIVATE)

    private val encryptedPrefs = EncryptedPreferencesManager(context)
    private val keyStoreManager = KeyStoreManager()

    fun isHardwareBacked(): Boolean = encryptedPrefs.isHardwareEncrypted() || keyStoreManager.isHardwareBacked()

    /**
     * Store an API key securely encrypted at rest via EncryptedSharedPreferences.
     */
    fun saveApiKey(provider: LlmProvider, rawKey: String) {
        encryptedPrefs.saveApiKey(provider, rawKey)
    }

    /**
     * Retrieve the decrypted API key.
     * For Gemini, if user hasn't set one, check BuildConfig.GEMINI_API_KEY as fallback.
     */
    fun getApiKey(provider: LlmProvider): String {
        val storedKey = encryptedPrefs.getApiKey(provider)
        if (!storedKey.isNullOrBlank()) return storedKey

        // Check if environment / BuildConfig has a pre-injected key
        if (provider == LlmProvider.GEMINI) {
            val buildKey = BuildConfig.GEMINI_API_KEY
            if (buildKey.isNotBlank() && buildKey != "MY_GEMINI_API_KEY") {
                return buildKey
            }
        }
        return ""
    }

    fun hasApiKey(provider: LlmProvider): Boolean {
        if (provider.isLocal) return true // Ollama doesn't mandate an auth key
        return getApiKey(provider).isNotBlank()
    }

    fun removeApiKey(provider: LlmProvider) {
        encryptedPrefs.removeApiKey(provider)
    }

    /**
     * Base URL for providers (especially local Ollama or OpenAI-compatible reverse proxies)
     */
    fun getBaseUrl(provider: LlmProvider): String {
        val custom = prefs.getString("base_url_${provider.id}", null)
        return if (!custom.isNullOrBlank()) custom else provider.defaultBaseUrl
    }

    fun setBaseUrl(provider: LlmProvider, url: String) {
        prefs.edit().putString("base_url_${provider.id}", url.trim()).apply()
    }

    /**
     * Model Hyperparameters
     */
    fun getModelSettings(provider: LlmProvider): ModelSettings {
        val temp = prefs.getFloat("setting_temp_${provider.id}", 0.7f)
        val maxTokens = prefs.getInt("setting_tokens_${provider.id}", 4096)
        val prompt = prefs.getString(
            "setting_prompt_${provider.id}",
            "You are halakou, an intelligent, transparent, and direct AI assistant. Answer concisely, provide high quality code when requested, and utilize available tools when up-to-date facts or calculations are needed."
        ) ?: ""
        return ModelSettings(
            temperature = temp,
            maxTokens = maxTokens,
            systemPrompt = prompt
        )
    }

    fun saveModelSettings(provider: LlmProvider, settings: ModelSettings) {
        prefs.edit()
            .putFloat("setting_temp_${provider.id}", settings.temperature)
            .putInt("setting_tokens_${provider.id}", settings.maxTokens)
            .putString("setting_prompt_${provider.id}", settings.systemPrompt)
            .apply()
    }

    /**
     * Tool enablement states
     */
    fun isToolEnabled(toolName: String): Boolean {
        return prefs.getBoolean("tool_enabled_$toolName", true)
    }

    fun setToolEnabled(toolName: String, enabled: Boolean) {
        prefs.edit().putBoolean("tool_enabled_$toolName", enabled).apply()
    }
}
