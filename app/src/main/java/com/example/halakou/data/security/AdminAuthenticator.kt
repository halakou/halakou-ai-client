package com.example.halakou.data.security

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest

/**
 * Pragmatic Indie Admin Authenticator ("God Mode").
 * 
 * Cryptographic validation of the creator's personal admin key.
 * When verified, forces permanent LIFETIME_PREMIUM state:
 * - 100% free forever for the creator.
 * - Complete bypass of Google Play Billing and paywalls.
 * - Zero rate limits.
 * - Unlocks hidden Developer Settings menu for endpoint overrides.
 */
class AdminAuthenticator(context: Context) {

    companion object {
        // SHA-256 hash of the master admin override string:
        // Key: "HALAKOU_GOD_MODE_ALPHA_2026_MASTER_OVERRIDE"
        private const val ADMIN_KEY_SHA256 = "b7a66e60b2ebdf9a560fa3122c66804bb61b6238b725c4efc96cb34407ad5d9a"

        private const val PREFS_NAME = "halakou_admin_authenticator_prefs"
        private const val PREF_IS_ADMIN_GOD_MODE = "pref_god_mode_unlocked"
        private const val PREF_CUSTOM_CONFIG_URL = "pref_custom_config_url"
        private const val PREF_DEV_CUSTOM_ENDPOINT = "pref_dev_custom_endpoint"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _isGodModeUnlocked = MutableStateFlow(prefs.getBoolean(PREF_IS_ADMIN_GOD_MODE, false))
    val isGodModeUnlocked: StateFlow<Boolean> = _isGodModeUnlocked.asStateFlow()

    private val _customConfigUrl = MutableStateFlow(
        prefs.getString(PREF_CUSTOM_CONFIG_URL, "") ?: ""
    )
    val customConfigUrl: StateFlow<String> = _customConfigUrl.asStateFlow()

    private val _devCustomEndpoint = MutableStateFlow(
        prefs.getString(PREF_DEV_CUSTOM_ENDPOINT, "") ?: ""
    )
    val devCustomEndpoint: StateFlow<String> = _devCustomEndpoint.asStateFlow()

    /**
     * Verifies the candidate cryptographic admin key via SHA-256.
     * Returns true if authenticated, permanently setting LIFETIME_PREMIUM.
     */
    fun verifyAndUnlock(candidateKey: String): Boolean {
        val trimmed = candidateKey.trim()
        val candidateHash = sha256(trimmed)

        if (candidateHash.equals(ADMIN_KEY_SHA256, ignoreCase = true) ||
            trimmed == "HALAKOU_GOD_MODE_ALPHA_2026_MASTER_OVERRIDE"
        ) {
            prefs.edit().putBoolean(PREF_IS_ADMIN_GOD_MODE, true).apply()
            _isGodModeUnlocked.value = true
            return true
        }
        return false
    }

    /**
     * Overrides the GitHub Pages static config URL (useful for testing forks/dev branches).
     */
    fun setCustomConfigUrl(url: String) {
        val clean = url.trim()
        prefs.edit().putString(PREF_CUSTOM_CONFIG_URL, clean).apply()
        _customConfigUrl.value = clean
    }

    /**
     * Developer override for primary API base URL.
     */
    fun setDevCustomEndpoint(url: String) {
        val clean = url.trim()
        prefs.edit().putString(PREF_DEV_CUSTOM_ENDPOINT, clean).apply()
        _devCustomEndpoint.value = clean
    }

    /**
     * Revokes admin privileges (for testing standard user flows).
     */
    fun revokeAdmin() {
        prefs.edit()
            .putBoolean(PREF_IS_ADMIN_GOD_MODE, false)
            .remove(PREF_CUSTOM_CONFIG_URL)
            .remove(PREF_DEV_CUSTOM_ENDPOINT)
            .apply()
        _isGodModeUnlocked.value = false
        _customConfigUrl.value = ""
        _devCustomEndpoint.value = ""
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
