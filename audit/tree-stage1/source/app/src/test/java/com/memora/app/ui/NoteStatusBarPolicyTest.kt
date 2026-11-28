package com.memora.app.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteStatusBarPolicyTest {
    @Test fun firstPageStartsVisibleAndHidesOnlyAfterHeaderPasses() {
        val policy = NoteStatusBarPolicy(hideAtPx = 68, restoreAtPx = 12)
        assertFalse(policy.update(0))
        assertFalse(policy.update(67))
        assertTrue(policy.update(68))
        assertTrue(policy.update(290))
    }

    @Test fun smallReverseScrollDoesNotCauseStatusBarFlicker() {
        val policy = NoteStatusBarPolicy(hideAtPx = 68, restoreAtPx = 12)
        assertTrue(policy.update(72))
        assertTrue(policy.update(60))
        assertTrue(policy.update(16))
        assertFalse(policy.update(12))
        assertFalse(policy.update(30))
        assertTrue(policy.update(68))
    }
}
