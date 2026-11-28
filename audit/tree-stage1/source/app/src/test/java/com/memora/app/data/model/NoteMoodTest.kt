package com.memora.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NoteMoodTest {
    @Test
    fun storedMoodRoundTripsForEveryValue() {
        NoteMood.entries.forEach { mood ->
            assertEquals(mood, NoteMood.fromStored(mood.name))
        }
    }

    @Test
    fun unknownOrMissingMoodIsIgnoredSafely() {
        assertNull(NoteMood.fromStored(null))
        assertNull(NoteMood.fromStored(""))
        assertNull(NoteMood.fromStored("UNKNOWN"))
    }
}
