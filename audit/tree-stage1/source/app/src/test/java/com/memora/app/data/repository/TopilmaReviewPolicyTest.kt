package com.memora.app.data.repository

import com.memora.app.data.local.TopilmaReviewRating
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TopilmaReviewPolicyTest {
    @Test fun firstReviewAlwaysFallsWithinOneToThreeDays() {
        repeat(1_000) { seed -> assertTrue(TopilmaReviewPolicy.firstDelayDays(seed) in 1..3) }
    }

    @Test fun normalReviewProgressivelyLengthens() {
        assertEquals(3, TopilmaReviewPolicy.nextIntervalDays(0, 0, TopilmaReviewRating.NORMAL, 1))
        assertEquals(7, TopilmaReviewPolicy.nextIntervalDays(3, 1, TopilmaReviewRating.NORMAL, 1))
        assertEquals(14, TopilmaReviewPolicy.nextIntervalDays(7, 2, TopilmaReviewRating.NORMAL, 1))
        assertEquals(30, TopilmaReviewPolicy.nextIntervalDays(14, 3, TopilmaReviewRating.NORMAL, 1))
    }

    @Test fun soonerAndLaterStayOnOppositeSidesOfNormal() {
        repeat(32) { seed ->
            val sooner = TopilmaReviewPolicy.nextIntervalDays(14, 3, TopilmaReviewRating.SOONER, seed)
            val normal = TopilmaReviewPolicy.nextIntervalDays(14, 3, TopilmaReviewRating.NORMAL, seed)
            val later = TopilmaReviewPolicy.nextIntervalDays(14, 3, TopilmaReviewRating.LATER, seed)
            assertTrue(sooner in 1..3)
            assertTrue(later > normal)
        }
    }
}
