package com.example.halakou.data.security

import android.content.Context
import kotlinx.coroutines.flow.StateFlow

/**
 * Adapter delegating to [AdminAuthenticator] for backwards compatibility and clean architecture.
 */
class AdminManager(context: Context) {
    val authenticator = AdminAuthenticator(context)

    val isAdmin: StateFlow<Boolean> = authenticator.isGodModeUnlocked
    val adminProxyUrl: StateFlow<String> = authenticator.devCustomEndpoint

    fun verifyAndUnlock(candidateKey: String): Boolean {
        return authenticator.verifyAndUnlock(candidateKey)
    }

    fun setAdminProxyOverride(url: String) {
        authenticator.setDevCustomEndpoint(url)
    }

    fun revokeAdmin() {
        authenticator.revokeAdmin()
    }
}
