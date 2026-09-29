package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.halakou.data.security.AdminAuthenticator
import com.example.halakou.domain.billing.BillingManager
import com.example.halakou.domain.billing.SubscriptionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AdminBillingIntegrationTest {

    private lateinit var context: Context
    private lateinit var adminAuthenticator: AdminAuthenticator
    private lateinit var billingManager: BillingManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        adminAuthenticator = AdminAuthenticator(context)
        adminAuthenticator.revokeAdmin() // Ensure clean baseline state
        billingManager = BillingManager(context, adminAuthenticator)
        billingManager.refreshState()
    }

    @Test
    fun `regular free user has quota limit and gets blocked at 25 requests`() {
        assertEquals(SubscriptionStatus.FREE_TIER, billingManager.billingState.value.status)
        assertEquals(25, billingManager.billingState.value.dailyLimit)

        // Consume all 25 requests
        for (i in 1..25) {
            assertTrue("Request $i should be allowed", billingManager.canExecuteRequest())
            billingManager.recordRequestUsage()
        }

        // 26th request must be rejected to trigger paywall
        assertFalse("26th request must be blocked by billing quota", billingManager.canExecuteRequest())
    }

    @Test
    fun `invalid master key fails authentication`() {
        val result = adminAuthenticator.verifyAndUnlock("WRONG_KEY_12345")
        assertFalse(result)
        assertFalse(adminAuthenticator.isGodModeUnlocked.value)
    }

    @Test
    fun `valid creator admin key unlocks God Mode and permanently bypasses billing`() {
        // First exhaust the regular user quota
        for (i in 1..25) {
            billingManager.recordRequestUsage()
        }
        assertFalse(billingManager.canExecuteRequest())

        // Unlock God Mode with creator cryptographic key
        val unlocked = adminAuthenticator.verifyAndUnlock("HALAKOU_GOD_MODE_ALPHA_2026_MASTER_OVERRIDE")
        assertTrue(unlocked)
        assertTrue(adminAuthenticator.isGodModeUnlocked.value)

        // Verify BillingManager now completely bypasses quota and limits
        billingManager.refreshState()
        assertEquals(SubscriptionStatus.LIFETIME_ADMIN_GOD_MODE, billingManager.billingState.value.status)
        assertTrue("Admin God Mode must bypass billing quota even at 25+ requests", billingManager.canExecuteRequest())
    }

    @Test
    fun `purchasing subscription unlocks active pro status`() {
        billingManager.onPurchaseSuccessful()
        assertEquals(SubscriptionStatus.ACTIVE_PRO, billingManager.billingState.value.status)
        assertTrue(billingManager.canExecuteRequest())
    }
}
