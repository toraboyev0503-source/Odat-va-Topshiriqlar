package com.memora.app.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotesArchivePolicyTest {
    @Test fun currentYearAlwaysOpen() {
        assertFalse(NotesArchivePolicy.archived(year = 2026, currentYear = 2026))
    }
    @Test fun closedYearsAreArchived() {
        assertTrue(NotesArchivePolicy.archived(year = 2025, currentYear = 2026))
        assertTrue(NotesArchivePolicy.archived(year = 2020, currentYear = 2026))
    }
    @Test fun futureDatesAreNotFalselyMarkedCompleted() {
        assertFalse(NotesArchivePolicy.archived(year = 2027, currentYear = 2026))
    }
}
