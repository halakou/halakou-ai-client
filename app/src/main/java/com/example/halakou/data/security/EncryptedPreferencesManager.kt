package com.example.halakou.data.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.halakou.domain.model.LlmProvider

/**
 * Secure EncryptedSharedPreferences Wrapper
 * 
 * Provides hardware-backed encrypted storage for user-provided LLM API keys.
 * Encrypts keys at rest using:
 * - MasterKey with AES-256-GCM key scheme
 * - PrefKeyEncryptionScheme: AES256_SIV (Key encryption)
 * - PrefValueEncryptionScheme: AES256_GCM (Value encryption)
 *
 * Includes an automated AES-256-GCM authenticated cipher fallback for JVM test
 * environments or devices with non-standard KeyStore implementations.
 */
class EncryptedPreferencesManager(
    private val context: Context,
    prefFileName: String = DEFAULT_PREFS_FILE
) {
    companion object {
        private const val TAG = "EncryptedPrefsManager"
        const val DEFAULT_PREFS_FILE = "halakou_encrypted_api_keys"
        private const val KEY_PREFIX_API_KEY = "llm_api_key_"
    }

    private val fallbackCipher = KeyStoreManager()

    private val preferences: SharedPreferences by lazy {
        createEncryptedSharedPreferences(prefFileName)
    }

    private var isUsingFallback = false

    private fun createEncryptedSharedPreferences(fileName: String): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                fileName,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.w(TAG, "EncryptedSharedPreferences creation failed; engaging AES-256-GCM cipher fallback: ${e.message}")
            isUsingFallback = true
            context.getSharedPreferences("${fileName}_fallback", Context.MODE_PRIVATE)
        }
    }

    /**
     * Securely stores an LLM API key encrypted at rest.
     */
    fun saveApiKey(provider: LlmProvider, apiKey: String) {
        val trimmed = apiKey.trim()
        if (trimmed.isEmpty()) {
            removeApiKey(provider)
            return
        }
        val storageKey = "$KEY_PREFIX_API_KEY${provider.id}"
        if (isUsingFallback) {
            val encrypted = fallbackCipher.encrypt(trimmed)
            preferences.edit().putString(storageKey, encrypted).apply()
        } else {
            preferences.edit().putString(storageKey, trimmed).apply()
        }
    }

    /**
     * Retrieves and decrypts the stored API key for the specified provider.
     */
    fun getApiKey(provider: LlmProvider): String? {
        val storageKey = "$KEY_PREFIX_API_KEY${provider.id}"
        val raw = preferences.getString(storageKey, null) ?: return null
        if (raw.isBlank()) return null

        return if (isUsingFallback) {
            val decrypted = fallbackCipher.decrypt(raw)
            decrypted.ifBlank { null }
        } else {
            raw
        }
    }

    /**
     * Checks if a valid API key exists for the provider.
     */
    fun hasApiKey(provider: LlmProvider): Boolean {
        val key = getApiKey(provider)
        return !key.isNullOrBlank()
    }

    /**
     * Deletes the stored API key for the specified provider.
     */
    fun removeApiKey(provider: LlmProvider) {
        val storageKey = "$KEY_PREFIX_API_KEY${provider.id}"
        preferences.edit().remove(storageKey).apply()
    }

    /**
     * Secure string storage (encrypted at rest).
     */
    fun putSecureString(key: String, value: String) {
        if (isUsingFallback) {
            val encrypted = fallbackCipher.encrypt(value)
            preferences.edit().putString(key, encrypted).apply()
        } else {
            preferences.edit().putString(key, value).apply()
        }
    }

    /**
     * Secure string retrieval.
     */
    fun getSecureString(key: String, defaultValue: String? = null): String? {
        val raw = preferences.getString(key, null) ?: return defaultValue
        return if (isUsingFallback) {
            val decrypted = fallbackCipher.decrypt(raw)
            decrypted.ifBlank { defaultValue }
        } else {
            raw
        }
    }

    /**
     * Removes an arbitrary secure entry.
     */
    fun remove(key: String) {
        preferences.edit().remove(key).apply()
    }

    /**
     * Clears all encrypted entries in this vault.
     */
    fun clearAll() {
        preferences.edit().clear().apply()
    }

    /**
     * Returns true if storage is operating via Android Keystore MasterKey.
     */
    fun isHardwareEncrypted(): Boolean = !isUsingFallback
}
