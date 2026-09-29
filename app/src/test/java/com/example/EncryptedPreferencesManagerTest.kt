package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.halakou.data.security.EncryptedPreferencesManager
import com.example.halakou.domain.model.LlmProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EncryptedPreferencesManagerTest {

    private lateinit var context: Context
    private lateinit var encryptedPrefs: EncryptedPreferencesManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        encryptedPrefs = EncryptedPreferencesManager(context, "test_encrypted_api_keys")
        encryptedPrefs.clearAll()
    }

    @Test
    fun `save and retrieve API key for provider`() {
        val testKey = "sk-ant-api03-test-token-key-halakou"
        encryptedPrefs.saveApiKey(LlmProvider.ANTHROPIC, testKey)

        assertTrue(encryptedPrefs.hasApiKey(LlmProvider.ANTHROPIC))
        assertEquals(testKey, encryptedPrefs.getApiKey(LlmProvider.ANTHROPIC))
    }

    @Test
    fun `whitespace is trimmed when saving API key`() {
        val testKeyWithSpaces = "   atria_sec_99482710482_key   "
        encryptedPrefs.saveApiKey(LlmProvider.ATRIA_ASI, testKeyWithSpaces)

        assertEquals("atria_sec_99482710482_key", encryptedPrefs.getApiKey(LlmProvider.ATRIA_ASI))
    }

    @Test
    fun `saving blank key removes key from storage`() {
        encryptedPrefs.saveApiKey(LlmProvider.OPENROUTER, "sk-or-v1-valid-key")
        assertTrue(encryptedPrefs.hasApiKey(LlmProvider.OPENROUTER))

        encryptedPrefs.saveApiKey(LlmProvider.OPENROUTER, "   ")
        assertFalse(encryptedPrefs.hasApiKey(LlmProvider.OPENROUTER))
        assertNull(encryptedPrefs.getApiKey(LlmProvider.OPENROUTER))
    }

    @Test
    fun `removeApiKey removes specific provider key`() {
        encryptedPrefs.saveApiKey(LlmProvider.DEEPSEEK, "sk-deepseek-test-key")
        encryptedPrefs.saveApiKey(LlmProvider.ATRIA_ASI, "atria-test-key")

        assertTrue(encryptedPrefs.hasApiKey(LlmProvider.DEEPSEEK))
        assertTrue(encryptedPrefs.hasApiKey(LlmProvider.ATRIA_ASI))

        encryptedPrefs.removeApiKey(LlmProvider.DEEPSEEK)

        assertFalse(encryptedPrefs.hasApiKey(LlmProvider.DEEPSEEK))
        assertTrue(encryptedPrefs.hasApiKey(LlmProvider.ATRIA_ASI))
    }

    @Test
    fun `putSecureString and getSecureString roundtrip consistency`() {
        val secureKey = "custom_proxy_auth_token"
        val secretValue = "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"

        encryptedPrefs.putSecureString(secureKey, secretValue)
        val retrieved = encryptedPrefs.getSecureString(secureKey)

        assertEquals(secretValue, retrieved)
    }

    @Test
    fun `clearAll removes all stored keys`() {
        encryptedPrefs.saveApiKey(LlmProvider.OPENROUTER, "key1")
        encryptedPrefs.saveApiKey(LlmProvider.ANTHROPIC, "key2")

        encryptedPrefs.clearAll()

        assertFalse(encryptedPrefs.hasApiKey(LlmProvider.OPENROUTER))
        assertFalse(encryptedPrefs.hasApiKey(LlmProvider.ANTHROPIC))
    }
}
