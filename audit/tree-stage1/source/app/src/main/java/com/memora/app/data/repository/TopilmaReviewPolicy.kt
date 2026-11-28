package com.memora.app.data.repository

import com.memora.app.data.local.TopilmaReviewRating

object TopilmaReviewPolicy {
    fun firstDelayDays(stableSeed: Int): Int = 1 + (stableSeed and Int.MAX_VALUE) % 3

    fun nextIntervalDays(
        currentIntervalDays: Int,
        reviewCount: Int,
        rating: TopilmaReviewRating,
        stableSeed: Int
    ): Int {
        val normal = when {
            reviewCount <= 0 -> 3
            currentIntervalDays <= 3 -> 7
            currentIntervalDays <= 7 -> 14
            currentIntervalDays <= 14 -> 30
            else -> (currentIntervalDays * 1.8).toInt().coerceAtMost(365)
        }
        val seed = stableSeed and Int.MAX_VALUE
        return when (rating) {
            TopilmaReviewRating.SOONER -> 1 + seed % 3
            TopilmaReviewRating.NORMAL -> normal
            TopilmaReviewRating.LATER -> (normal * (1.5 + (seed % 11) / 10.0)).toInt().coerceAtMost(730)
        }
    }
}
