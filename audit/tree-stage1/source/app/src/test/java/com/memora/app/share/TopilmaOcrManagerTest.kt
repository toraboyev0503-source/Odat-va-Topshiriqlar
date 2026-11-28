package com.memora.app.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TopilmaOcrManagerTest {
    @Test
    fun geometricOrderIgnoresShuffledMlKitIndices() {
        val tokens = listOf(
            token(8, 0, 0, "hushyor", 10, 90),
            token(8, 0, 1, "kuzatish", 90, 90),
            token(4, 0, 0, "Bu", 10, 10),
            token(4, 0, 1, "davlat", 45, 10),
            token(9, 0, 0, "Bu", 10, 64),
            token(9, 0, 1, "hukumatlar", 45, 64),
            token(1, 0, 0, "yetishish", 10, 37),
            token(1, 0, 1, "emas,", 100, 37)
        )

        val keys = tokens.mapTo(linkedSetOf()) { it.key }
        assertEquals(
            "Bu davlat yetishish emas, Bu hukumatlar hushyor kuzatish",
            TopilmaOcrManager.selectedText(tokens, keys)
        )
    }

    @Test
    fun continuousRangeWorksForwardAndBackward() {
        val tokens = listOf(
            token(5, 0, 0, "bir", 10, 10),
            token(2, 0, 0, "ikki", 60, 10),
            token(9, 0, 0, "uch", 10, 40),
            token(1, 0, 0, "to‘rt", 60, 40)
        )
        val ordered = TopilmaOcrManager.readingOrder(tokens)
        val forward = TopilmaOcrManager.rangeKeys(tokens, ordered.first().key, ordered.last().key)
        val backward = TopilmaOcrManager.rangeKeys(tokens, ordered.last().key, ordered.first().key)

        assertEquals(forward, backward)
        assertEquals(4, forward.size)
        assertEquals("bir ikki uch to‘rt", TopilmaOcrManager.selectedText(tokens, forward))
    }

    @Test
    fun punctuationAndParagraphsAreReconstructedWithoutRewritingWords() {
        val tokens = listOf(
            token(0, 0, 0, "Salom", 10, 10),
            token(0, 0, 1, ",", 70, 10),
            token(0, 0, 2, "dunyo", 82, 10),
            token(0, 0, 3, "!", 150, 10),
            token(3, 0, 0, "Yangi", 10, 70),
            token(3, 0, 1, "abzas.", 70, 70)
        )
        val keys = tokens.mapTo(linkedSetOf()) { it.key }
        val text = TopilmaOcrManager.selectedText(tokens, keys)

        assertEquals("Salom, dunyo!\n\nYangi abzas.", text)
        assertTrue(text.contains("dunyo"))
    }

    private fun token(
        block: Int,
        line: Int,
        index: Int,
        text: String,
        left: Int,
        top: Int
    ) = TopilmaOcrManager.Token(
        blockIndex = block,
        lineIndex = line,
        tokenIndex = index,
        text = text,
        bounds = TopilmaOcrManager.Bounds(left, top, left + text.length * 10 + 14, top + 20)
    )
}
