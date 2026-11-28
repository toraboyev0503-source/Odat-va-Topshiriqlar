package com.memora.app.billing

object SubscriptionCatalog {
    const val PRODUCT_ID = "memora_premium"
    const val FREE_TRIAL_TAG = "memora-free-trial"

    const val MONTHLY = "monthly"
    const val QUARTERLY = "quarterly"
    const val SEMIANNUAL = "semiannual"
    const val ANNUAL = "annual"

    val basePlanOrder = listOf(MONTHLY, QUARTERLY, SEMIANNUAL, ANNUAL)
}

data class SubscriptionPlan(
    val basePlanId: String,
    val formattedPrice: String,
    val billingPeriod: String,
    val hasOneMonthFreeTrial: Boolean
)

enum class EntitlementMode {
    CHECKING,
    ACTIVE,
    OFFLINE_GRACE,
    PENDING,
    READ_ONLY,
    CONFIGURATION_ERROR,
    DEBUG
}

data class EntitlementState(
    val mode: EntitlementMode,
    val canWrite: Boolean,
    val validUntilMillis: Long? = null,
    val basePlanId: String? = null
) {
    companion object {
        fun checking() = EntitlementState(EntitlementMode.CHECKING, canWrite = false)
        fun readOnly() = EntitlementState(EntitlementMode.READ_ONLY, canWrite = false)
    }
}

internal data class CachedEntitlement(
    val verifiedAtDeviceMillis: Long,
    val serverTimeMillis: Long,
    val entitlementExpiryMillis: Long,
    val basePlanId: String?
)

object EntitlementPolicy {
    const val OFFLINE_WINDOW_MILLIS = 7L * 24L * 60L * 60L * 1_000L
    private const val CLOCK_ROLLBACK_TOLERANCE_MILLIS = 5L * 60L * 1_000L

    internal fun fromCache(
        cached: CachedEntitlement?,
        nowMillis: Long
    ): EntitlementState {
        if (cached == null) return EntitlementState.readOnly()
        if (nowMillis < cached.verifiedAtDeviceMillis - CLOCK_ROLLBACK_TOLERANCE_MILLIS) {
            return EntitlementState.readOnly()
        }
        val cacheValidUntil = cached.verifiedAtDeviceMillis + OFFLINE_WINDOW_MILLIS
        val deviceExpiry = cached.verifiedAtDeviceMillis +
            (cached.entitlementExpiryMillis - cached.serverTimeMillis).coerceAtLeast(0L)
        val validUntil = minOf(cacheValidUntil, deviceExpiry)
        return if (nowMillis <= validUntil) {
            EntitlementState(
                mode = EntitlementMode.OFFLINE_GRACE,
                canWrite = true,
                validUntilMillis = validUntil,
                basePlanId = cached.basePlanId
            )
        } else {
            EntitlementState.readOnly()
        }
    }
}
