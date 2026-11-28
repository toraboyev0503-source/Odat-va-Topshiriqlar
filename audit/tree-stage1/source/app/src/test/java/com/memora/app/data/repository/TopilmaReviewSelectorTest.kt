package com.memora.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TopilmaReviewSelectorTest {
    private data class Card(val id: Int, val source: String, val favorite: Boolean = false)

    private fun choose(cards: List<Card>, target: Int) = TopilmaReviewSelector.select(
        cards, target, { it.source }, { it.favorite }
    )

    @Test fun allFavoritesStillFillTarget() {
        val result = choose((1..10).map { Card(it, "S$it", true) }, 5)
        assertEquals(5, result.size)
        assertEquals(5, result.distinctBy { it.id }.size)
    }

    @Test fun favorsDifferentSourcesBeforeRepeats() {
        val result = choose(listOf(Card(1, "A"), Card(2, "A"), Card(3, "B"), Card(4, "C")), 3)
        assertEquals(listOf(1, 3, 4), result.map { it.id })
    }

    @Test fun returnsEveryCandidateWhenBelowTarget() {
        assertEquals(2, choose(listOf(Card(1, "A"), Card(2, "B")), 5).size)
        assertTrue(choose(emptyList(), 5).isEmpty())
    }

    @Test fun softFavoriteLimitWorksWhenAlternativesExist() {
        val result = choose((1..5).map { Card(it, "S$it", true) } +
            (6..14).map { Card(it, "S$it") }, 5)
        assertEquals(5, result.size)
        assertEquals(2, result.count { it.favorite })
    }
}
