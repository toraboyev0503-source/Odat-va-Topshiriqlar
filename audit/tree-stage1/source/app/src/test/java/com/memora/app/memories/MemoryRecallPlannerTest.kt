package com.memora.app.memories

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class MemoryRecallPlannerTest {
    private val day = LocalDate.of(2026, 11, 20)
    private val zone = ZoneId.of("Asia/Tashkent")
    private fun at(daysAgo: Long, hour: Int): Long = day.minusDays(daysAgo)
        .atTime(hour, 12).atZone(zone).toInstant().toEpochMilli()

    @Test fun onlyMatureNotesAreSelected() {
        val pool = listOf(RecallCandidate("new", at(1, 10)), RecallCandidate("old", at(20, 14)))
        assertEquals(listOf("old"), MemoryRecallPlanner.choose(day, zone, pool, emptySet(), 3).map { it.id })
    }

    @Test fun recentEntriesAreAvoidedWhenOthersAreAvailable() {
        val pool = listOf(RecallCandidate("recent", at(40, 10)), RecallCandidate("fresh", at(50, 11)))
        assertEquals(listOf("fresh"), MemoryRecallPlanner.choose(day, zone, pool, setOf("recent"), 1).map { it.id })
    }

    @Test fun noteIdNotSelectedTwiceAndGapPreserved() {
        val pool = (0..9).map { RecallCandidate("$it", at(40 + it.toLong(), 12)) }
        val slots = MemoryRecallPlanner.choose(day, zone, pool, emptySet(), 5)
        assertEquals(5, slots.size)
        assertEquals(5, slots.map { it.id }.distinct().size)
        assertTrue(slots.zipWithNext().all { (a,b) -> b.scheduledAt - a.scheduledAt >= 90 * 60_000L })
    }

    @Test fun singleReminderPreservesOriginalHour() {
        val slots = MemoryRecallPlanner.choose(day, zone, listOf(RecallCandidate("a", at(12, 14))), emptySet(), 1)
        assertEquals(14, java.time.Instant.ofEpochMilli(slots.single().scheduledAt).atZone(zone).hour)
    }

    @Test fun randomAlbumTimesHaveMinimumSpacingAndDayBounds() {
        listOf(1, 3, 5).forEach { count ->
            val slots = MemoryRecallPlanner.albumTimes(day, zone, count, true, 9, 0)
            assertEquals(count, slots.size)
            assertEquals(slots, MemoryRecallPlanner.albumTimes(day, zone, count, true, 9, 0))
            val gap = if (count == 5) 135 else if (count == 3) 180 else 0
            assertTrue(slots.zipWithNext().all { (a,b) -> b - a >= gap * 60_000L })
            assertFalse(slots.any { java.time.Instant.ofEpochMilli(it).atZone(zone).toLocalDate() != day })
        }
    }
}
