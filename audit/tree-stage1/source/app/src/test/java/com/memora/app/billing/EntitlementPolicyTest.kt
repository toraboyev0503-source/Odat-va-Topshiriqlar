package com.memora.app.billing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EntitlementPolicyTest {
    private val verifiedAt = 1_000_000_000L
    private val cached = CachedEntitlement(
        verifiedAtDeviceMillis = verifiedAt,
        serverTimeMillis = verifiedAt,
        entitlementExpiryMillis = verifiedAt + 30L * 24L * 60L * 60L * 1_000L,
        basePlanId = SubscriptionCatalog.MONTHLY
    )

    @Test
    fun cachedEntitlementAllowsSevenDaysOffline() {
        val state = EntitlementPolicy.fromCache(
            cached,
            verifiedAt + EntitlementPolicy.OFFLINE_WINDOW_MILLIS
        )
        assertTrue(state.canWrite)
        assertEquals(EntitlementMode.OFFLINE_GRACE, state.mode)
    }

    @Test
    fun cachedEntitlementExpiresAfterSevenDays() {
        val state = EntitlementPolicy.fromCache(
            cached,
            verifiedAt + EntitlementPolicy.OFFLINE_WINDOW_MILLIS + 1L
        )
        assertFalse(state.canWrite)
        assertEquals(EntitlementMode.READ_ONLY, state.mode)
    }

    @Test
    fun subscriptionExpiryWinsWhenItIsSoonerThanOfflineWindow() {
        val expiringSoon = cached.copy(
            entitlementExpiryMillis = cached.serverTimeMillis + 24L * 60L * 60L * 1_000L
        )
        val state = EntitlementPolicy.fromCache(
            expiringSoon,
            verifiedAt + 24L * 60L * 60L * 1_000L + 1L
        )
        assertFalse(state.canWrite)
        assertEquals(EntitlementMode.READ_ONLY, state.mode)
    }

    @Test
    fun clockRollbackInvalidatesCache() {
        val state = EntitlementPolicy.fromCache(cached, verifiedAt - 5L * 60L * 1_000L - 1L)
        assertFalse(state.canWrite)
    }
}
