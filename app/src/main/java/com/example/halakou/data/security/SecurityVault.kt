package com.example.halakou.data.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM Security Vault for halakou.
 * 
 * Provides hardware-grade authenticated encryption (AES-256-GCM) with 128-bit authentication tag
 * and randomized initialization vectors for securely storing and retrieving user API keys.
 */
class SecurityVault(
    private val context: Context,
    private val keyStoreManager: KeyStoreManager = KeyStoreManager()
) {
    companion object {
        private const val PREFS_NAME = "halakou_security_vault"
        private const val ALGORITHM = "AES"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val TAG_LENGTH_BIT = 128
        private const val IV_LENGTH_BYTE = 12
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Stores an encrypted secret key under the specified alias.
     */
    fun storeSecret(alias: String, plainText: String) {
        val encrypted = keyStoreManager.encrypt(plainText)
        prefs.edit().putString("secret_$alias", encrypted).apply()
    }

    /**
     * Retrieves and decrypts the secret key for the specified alias.
     */
    fun getSecret(alias: String): String? {
        val encrypted = prefs.getString("secret_$alias", null) ?: return null
        return keyStoreManager.decrypt(encrypted)
    }

    /**
     * Checks if a secret exists for the specified alias.
     */
    fun hasSecret(alias: String): Boolean {
        return prefs.contains("secret_$alias")
    }

    /**
     * Removes a stored secret.
     */
    fun removeSecret(alias: String) {
        prefs.edit().remove("secret_$alias").apply()
    }

    /**
     * Standalone AES-256-GCM encryption utility for testing and deterministic cryptography.
     */
    fun encryptAes256Gcm(plainText: String, secretKey: SecretKey): Pair<ByteArray, ByteArray> {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val iv = ByteArray(IV_LENGTH_BYTE)
        SecureRandom().nextBytes(iv)
        val spec = GCMParameterSpec(TAG_LENGTH_BIT, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)
        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return Pair(iv, cipherText)
    }

    /**
     * Standalone AES-256-GCM decryption utility.
     */
    fun decryptAes256Gcm(cipherText: ByteArray, iv: ByteArray, secretKey: SecretKey): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(TAG_LENGTH_BIT, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        val plainBytes = cipher.doFinal(cipherText)
        return String(plainBytes, Charsets.UTF_8)
    }

    /**
     * Generates a 256-bit AES secret key.
     */
    fun generateAes256Key(): SecretKey {
        val keyGen = KeyGenerator.getInstance(ALGORITHM)
        keyGen.init(256)
        return keyGen.generateKey()
    }
}
