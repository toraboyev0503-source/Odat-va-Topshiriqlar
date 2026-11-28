package com.memora.app.data.repository

import kotlin.math.ceil

/**
 * Deterministic, independently tested selection policy. Favors source diversity,
 * caps favorites when alternatives exist and always fills available slots.
 * [scored] must already be ordered by review priority.
 */
internal object TopilmaReviewSelector {
    fun <T> select(
        scored: List<T>,
        target: Int,
        sourceId: (T) -> String?,
        favorite: (T) -> Boolean
    ): List<T> {
        if (target <= 0) return emptyList()
        if (scored.size <= target) return scored
        val favoriteLimit = maxOf(1, ceil(target * .25).toInt())
        val selected = linkedSetOf<T>()
        val usedSources = mutableSetOf<String?>()
        var favoriteCount = 0
        for (preferNewSource in listOf(true, false)) {
            for (candidate in scored) {
                if (selected.size >= target) break
                if (candidate in selected) continue
                if (preferNewSource && sourceId(candidate) in usedSources) continue
                if (favorite(candidate) && favoriteCount >= favoriteLimit) continue
                selected += candidate
                usedSources += sourceId(candidate)
                if (favorite(candidate)) favoriteCount++
            }
        }
        // Favorites are allowed to exceed the soft cap when no alternatives exist.
        for (candidate in scored) {
            if (selected.size >= target) break
            selected += candidate
        }
        return selected.toList()
    }
}
