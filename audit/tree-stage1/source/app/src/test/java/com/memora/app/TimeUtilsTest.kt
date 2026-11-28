package com.memora.app

import com.memora.app.util.TimeUtils
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeUtilsTest {
    @Test
    fun monthRangeIsOrdered() {
        val range = TimeUtils.monthRange(2026, 8)
        assertTrue(requireNotNull(range.start) < requireNotNull(range.end))
    }

    @Test
    fun yearRangeIsOrdered() {
        val range = TimeUtils.yearRange(2026)
        assertTrue(requireNotNull(range.start) < requireNotNull(range.end))
    }
}
