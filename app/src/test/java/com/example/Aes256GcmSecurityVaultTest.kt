package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.halakou.data.security.SecurityVault
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.crypto.AEADBadTagException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Aes256GcmSecurityVaultTest {

    private lateinit var context: Context
    private lateinit var securityVault: SecurityVault

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        securityVault = SecurityVault(context)
    }

    @Test
    fun `AES-256-GCM encryption and decryption consistency`() {
        val secretKey = securityVault.generateAes256Key()
        assertEquals(32, secretKey.encoded.size) // 256 bits = 32 bytes

        val originalApiKey = "atria_live_sec_994827461938472910482"
        val (iv, cipherText) = securityVault.encryptAes256Gcm(originalApiKey, secretKey)

        assertEquals(12, iv.size) // Standard GCM IV is 12 bytes
        assertTrue(cipherText.isNotEmpty())
        assertNotEquals(originalApiKey, String(cipherText))

        val decrypted = securityVault.decryptAes256Gcm(cipherText, iv, secretKey)
        assertEquals(originalApiKey, decrypted)
    }

    @Test
    fun `AES-256-GCM uses randomized IVs for identical plaintexts`() {
        val secretKey = securityVault.generateAes256Key()
        val plainText = "same_secret_api_token"

        val (iv1, cipher1) = securityVault.encryptAes256Gcm(plainText, secretKey)
        val (iv2, cipher2) = securityVault.encryptAes256Gcm(plainText, secretKey)

        // Randomized IV prevents replay and frequency analysis
        assertFalse(iv1.contentEquals(iv2))
        assertFalse(cipher1.contentEquals(cipher2))

        assertEquals(plainText, securityVault.decryptAes256Gcm(cipher1, iv1, secretKey))
        assertEquals(plainText, securityVault.decryptAes256Gcm(cipher2, iv2, secretKey))
    }

    @Test(expected = Exception::class)
    fun `tampered ciphertext fails GCM authentication tag verification`() {
        val secretKey = securityVault.generateAes256Key()
        val (iv, cipherText) = securityVault.encryptAes256Gcm("uncompromised_data", secretKey)

        // Tamper with the ciphertext or auth tag
        val tamperedCipher = cipherText.copyOf()
        tamperedCipher[0] = (tamperedCipher[0].toInt() xor 0xFF).toByte()

        securityVault.decryptAes256Gcm(tamperedCipher, iv, secretKey)
    }
}
