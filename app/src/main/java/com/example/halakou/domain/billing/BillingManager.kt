package com.example.halakou.domain.billing

import android.content.Context
import android.content.SharedPreferences
import com.example.halakou.data.security.AdminAuthenticator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class SubscriptionStatus {
    FREE_TIER,
    ACTIVE_PRO,
    LIFETIME_ADMIN_GOD_MODE
}

data class BillingUiState(
    val status: SubscriptionStatus = SubscriptionStatus.FREE_TIER,
    val dailyRequestsUsed: Int = 0,
    val dailyLimit: Int = 25,
    val isPaywallVisible: Boolean = false,
    val planPriceText: String = "$1.00 / month",
    val productId: String = BillingManager.SKU_MONTHLY_1USD
)

/**
 * Pragmatic Indie Monetization & Subscription Manager.
 * 
 * Manages the $1/month micro-subscription via Google Play Billing.
 * Decoupled from the Compose UI layer using StateFlow.
 * Automatically gives creator permanent VIP / God Mode status when Admin Key is detected.
 */
class BillingManager(
    context: Context,
    val adminAuthenticator: AdminAuthenticator
) {
    // Secondary constructor accepting AdminManager for backward compatibility
    constructor(context: Context, adminManager: com.example.halakou.data.security.AdminManager) : 
        this(context, adminManager.authenticator)

    companion object {
        const val SKU_MONTHLY_1USD = "halakou.monthly.1usd"
        const val DEFAULT_FREE_DAILY_LIMIT = 25

        private const val PREFS_NAME = "halakou_billing_prefs"
        private const val PREF_IS_SUBSCRIBED = "pref_is_pro_subscribed"
        private const val PREF_USAGE_COUNT = "pref_daily_usage_count"
        private const val PREF_USAGE_DATE = "pref_daily_usage_date"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _billingState = MutableStateFlow(BillingUiState())
    val billingState: StateFlow<BillingUiState> = _billingState.asStateFlow()

    init {
        refreshState()
    }

    /**
     * Synchronizes local billing state with subscription status and daily usage counters.
     */
    fun refreshState() {
        val isAdmin = adminAuthenticator.isGodModeUnlocked.value
        val isSubscribed = prefs.getBoolean(PREF_IS_SUBSCRIBED, false)

        val today = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        val savedDate = prefs.getString(PREF_USAGE_DATE, "") ?: ""
        var usageCount = prefs.getInt(PREF_USAGE_COUNT, 0)

        if (savedDate != today) {
            // New calendar day: reset usage
            usageCount = 0
            prefs.edit().putString(PREF_USAGE_DATE, today).putInt(PREF_USAGE_COUNT, 0).apply()
        }

        val status = when {
            isAdmin -> SubscriptionStatus.LIFETIME_ADMIN_GOD_MODE
            isSubscribed -> SubscriptionStatus.ACTIVE_PRO
            else -> SubscriptionStatus.FREE_TIER
        }

        _billingState.value = BillingUiState(
            status = status,
            dailyRequestsUsed = usageCount,
            dailyLimit = if (status == SubscriptionStatus.FREE_TIER) DEFAULT_FREE_DAILY_LIMIT else Int.MAX_VALUE,
            isPaywallVisible = false,
            planPriceText = "$1.00 / month"
        )
    }

    /**
     * Verifies if user has available quota or an active Pro/GodMode subscription.
     */
    fun canExecuteRequest(): Boolean {
        refreshState()
        val current = _billingState.value
        if (current.status == SubscriptionStatus.LIFETIME_ADMIN_GOD_MODE ||
            current.status == SubscriptionStatus.ACTIVE_PRO
        ) {
            return true
        }

        return current.dailyRequestsUsed < DEFAULT_FREE_DAILY_LIMIT
    }

    /**
     * Increments usage count for free users.
     */
    fun recordRequestUsage() {
        val current = _billingState.value
        if (current.status == SubscriptionStatus.LIFETIME_ADMIN_GOD_MODE ||
            current.status == SubscriptionStatus.ACTIVE_PRO
        ) {
            return
        }

        val newCount = current.dailyRequestsUsed + 1
        val today = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        prefs.edit().putString(PREF_USAGE_DATE, today).putInt(PREF_USAGE_COUNT, newCount).apply()

        _billingState.value = current.copy(dailyRequestsUsed = newCount)
    }

    fun setPaywallVisible(visible: Boolean) {
        _billingState.value = _billingState.value.copy(isPaywallVisible = visible)
    }

    /**
     * Processes successful Google Play Billing purchase.
     */
    fun onPurchaseSuccessful() {
        prefs.edit().putBoolean(PREF_IS_SUBSCRIBED, true).apply()
        refreshState()
    }

    fun restorePurchases() {
        refreshState()
    }
}
