package com.memora.app.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class QuietNavigationPolicyTest {
    @Test fun primaryTabsSwitchWithoutMotion() {
        // Memora 0.9.40 replaces the legacy Topilmalar root tab with Calendar.
        for (tab in listOf("home", "notes_hub/{filter}", "calendar_root", "settings_hub")) {
            assertTrue(QuietNavigationPolicy.isPrimaryDestination(tab))
            assertFalse(QuietNavigationPolicy.needsDetailMotion("home", null, tab, null))
        }
        assertTrue(QuietNavigationPolicy.isPrimaryDestination("calendar_root"))
    }

    @Test fun detailsAndFormerTopilmaRoutesUseMotion() {
        assertFalse(QuietNavigationPolicy.isPrimaryDestination("reader/{id}"))
        assertFalse(QuietNavigationPolicy.isPrimaryDestination("editor/{type}/{id}"))
        assertFalse(QuietNavigationPolicy.isPrimaryDestination("memory_settings"))
        assertFalse(QuietNavigationPolicy.isPrimaryDestination("topilmalar/{tab}", "REVIEW"))
        assertFalse(QuietNavigationPolicy.isPrimaryDestination("topilmalar/{tab}", "CREATE"))
        assertTrue(QuietNavigationPolicy.needsDetailMotion("notes_hub/{filter}", null, "reader/{id}", null))
        assertTrue(QuietNavigationPolicy.needsDetailMotion("reader/{id}", null, "notes_hub/{filter}", null))
        assertTrue(QuietNavigationPolicy.needsDetailMotion("home", null, "memory_settings", null))
    }

    @Test fun chromeRemainsHiddenUntilDetailFullyLeaves() {
        assertTrue(QuietNavigationPolicy.showBottomBar(true, false, false))
        assertFalse(QuietNavigationPolicy.showBottomBar(true, false, true))
        assertFalse(QuietNavigationPolicy.showBottomBar(true, true, false))
        assertFalse(QuietNavigationPolicy.showBottomBar(false, false, false))
    }

    @Test fun detailMotionIsExactlyTheApprovedSpecification() {
        assertEquals(170, QuietNavigationPolicy.DETAIL_DURATION_MS)
        assertEquals(12, QuietNavigationPolicy.DETAIL_TRAVEL_DP)
    }
}
