package com.memora.app.memories

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Random

/** Pure offline scheduling rules; original creation timestamps are never overwritten. */
data class RecallCandidate(val id: String, val createdAt: Long)
data class RecallSlot(val id: String, val scheduledAt: Long)

object MemoryRecallPlanner {
    fun choose(
        day: LocalDate,
        zone: ZoneId,
        candidates: List<RecallCandidate>,
        recentlyShown: Set<String>,
        count: Int,
        minimumAgeDays: Long = 7
    ): List<RecallSlot> {
        require(count in setOf(1, 3, 5))
        val nowStart = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val cutoff = day.minusDays(minimumAgeDays).atStartOfDay(zone).toInstant().toEpochMilli()
        val eligible = candidates.filter { it.createdAt < cutoff && it.createdAt > 0L }
            .distinctBy { it.id }
        val fresh = eligible.filterNot { it.id in recentlyShown }
        val pool = if (fresh.isNotEmpty()) fresh else eligible
        val random = Random(day.toEpochDay() xor 0x41D3L)
        val ranked = pool.sortedBy { it.createdAt }
        val chosen = mutableListOf<RecallCandidate>()
        val groups = listOf(
            ranked.take((ranked.size + 2) / 3),
            ranked.drop((ranked.size + 2) / 3).take((ranked.size + 2) / 3),
            ranked.drop(((ranked.size + 2) / 3) * 2)
        )
        while (chosen.size < minOf(count, ranked.size)) {
            val weighted = listOf(0,0,0,0,0,1,1,1,2,2)
            val pick = weighted[random.nextInt(weighted.size)]
            val remaining = groups[pick].filterNot { it in chosen }.ifEmpty { ranked.filterNot { it in chosen } }
            if (remaining.isEmpty()) break
            chosen += remaining[random.nextInt(remaining.size)]
        }
        // Preserve the original hour/minute as long as selected times are well separated.
        // If too close, shift only the delivery time, never the 'written at' metadata.
        val slots = chosen.map { candidate ->
            val local = Instant.ofEpochMilli(candidate.createdAt).atZone(zone)
            val whenAt = day.atTime(local.hour, local.minute).atZone(zone).toInstant().toEpochMilli()
            RecallSlot(candidate.id, whenAt)
        }.sortedBy { it.scheduledAt }
        val spacing = if (count == 5) 90L else if (count == 3) 120L else 0L
        var last = nowStart - spacing * 60_000L
        val end = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 60_000
        val scheduled = slots.map { slot ->
            val time = maxOf(slot.scheduledAt, last + spacing * 60_000L)
            last = time
            slot.copy(scheduledAt = time)
        }
        // In crowded late-night selections, shift the whole sequence earlier without
        // compressing gaps or changing the note's original createdAt metadata.
        val overflow = maxOf(0, (scheduled.lastOrNull()?.scheduledAt ?: end) - end)
        return scheduled.map { it.copy(scheduledAt = it.scheduledAt - overflow) }
    }

    fun albumTimes(day: LocalDate, zone: ZoneId, count: Int, randomTime: Boolean, startHour: Int, startMinute: Int): List<Long> {
        require(count in setOf(1, 3, 5))
        val gap = if (count == 5) 135 else if (count == 3) 180 else 0
        val start = if (randomTime) {
            val r = Random(day.toEpochDay() xor 0x731AL)
            // Fit all selected reminders between 08:00 and 21:00.
            8 * 60 + r.nextInt(maxOf(1, 13 * 60 - gap * (count - 1)))
        } else startHour.coerceIn(0, 23) * 60 + startMinute.coerceIn(0, 59)
        val safeStart = start.coerceAtMost(23 * 60 + 59 - gap * (count - 1))
        return (0 until count).map { index ->
            day.atStartOfDay().plusMinutes((safeStart + gap * index).toLong()).atZone(zone).toInstant().toEpochMilli()
        }
    }
}
