package com.example.halakou.data.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Enterprise-grade security manager utilizing the Android KeyStore provider
 * and AES-256-GCM authenticated encryption.
 *
 * Ensures that BYOK (Bring Your Own Key) secrets are never stored in plain text,
 * cannot be extracted via root or basic app inspect tools, and are hardware-backed
 * when supported by the device SoC (ARM TrustZone / StrongBox Keymaster).
 * Includes seamless fallback for JVM / unit test environments.
 */
class KeyStoreManager {

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val MASTER_KEY_ALIAS = "HalakouMasterKey_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH_BYTES = 12
        private const val GCM_TAG_LENGTH_BITS = 128
    }

    private var fallbackJvmKey: SecretKey? = null

    private val keyStore: KeyStore? = try {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    } catch (_: Exception) {
        null
    }

    init {
        ensureMasterKey()
    }

    private fun ensureMasterKey() {
        if (keyStore != null) {
            try {
                if (!keyStore.containsAlias(MASTER_KEY_ALIAS)) {
                    val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
                    val spec = KeyGenParameterSpec.Builder(
                        MASTER_KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                    )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .setRandomizedEncryptionRequired(true)
                        .build()
                    keyGenerator.init(spec)
                    keyGenerator.generateKey()
                }
                return
            } catch (_: Exception) {
                // Fall through to memory key if KeyStore generation fails (e.g. in test environment)
            }
        }

        // JVM / Robolectric environment fallback: generate standard 256-bit AES key
        if (fallbackJvmKey == null) {
            val keyGen = KeyGenerator.getInstance("AES")
            keyGen.init(256)
            fallbackJvmKey = keyGen.generateKey()
        }
    }

    private fun getSecretKey(): SecretKey {
        if (keyStore != null) {
            try {
                val key = keyStore.getKey(MASTER_KEY_ALIAS, null) as? SecretKey
                if (key != null) return key
            } catch (_: Exception) {}
        }
        return fallbackJvmKey ?: run {
            val keyGen = KeyGenerator.getInstance("AES")
            keyGen.init(256)
            val generated = keyGen.generateKey()
            fallbackJvmKey = generated
            generated
        }
    }

    /**
     * Checks if the key is inside hardware-backed secure storage (e.g. StrongBox / TEE).
     */
    fun isHardwareBacked(): Boolean {
        return try {
            val secretKey = getSecretKey()
            val factory = SecretKeyFactory.getInstance(secretKey.algorithm, ANDROID_KEYSTORE)
            val keyInfo = factory.getKeySpec(secretKey, KeyInfo::class.java) as KeyInfo
            keyInfo.isInsideSecureHardware
        } catch (_: Exception) {
            // Software fallback if query unsupported on certain emulators/OEMs/tests
            false
        }
    }

    /**
     * Encrypts plain text bytes into a Base64 string containing:
     * [12 bytes IV] + [Ciphertext + 16 bytes GCM Auth Tag]
     */
    fun encrypt(plainText: String): String {
        if (plainText.isBlank()) return ""
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
        val iv = cipher.iv
        val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        // Prepend IV to ciphertext
        val combined = ByteArray(iv.size + encryptedBytes.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(encryptedBytes, 0, combined, iv.size, encryptedBytes.size)

        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    /**
     * Decrypts a Base64 string back into the original plain text.
     */
    fun decrypt(encryptedBase64: String): String {
        if (encryptedBase64.isBlank()) return ""
        return try {
            val combined = Base64.decode(encryptedBase64, Base64.NO_WRAP)
            if (combined.size < GCM_IV_LENGTH_BYTES) return ""

            val iv = ByteArray(GCM_IV_LENGTH_BYTES)
            val cipherText = ByteArray(combined.size - GCM_IV_LENGTH_BYTES)

            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH_BYTES)
            System.arraycopy(combined, GCM_IV_LENGTH_BYTES, cipherText, 0, cipherText.size)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec)

            val decryptedBytes = cipher.doFinal(cipherText)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }
}
