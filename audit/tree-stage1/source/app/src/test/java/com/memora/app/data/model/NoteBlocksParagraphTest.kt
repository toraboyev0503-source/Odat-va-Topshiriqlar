package com.memora.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteBlocksParagraphTest {
    @Test
    fun legacyBlankLinesBecomeRealParagraphBlocks() {
        val blocks = NoteBlocksCodec.legacy("Birinchi abzas.\n\nIkkinchi abzas.")
        val textBlocks = blocks.filterIsInstance<NoteBlock.Text>()
        assertEquals(2, textBlocks.size)
        assertEquals("Birinchi abzas.", textBlocks[0].text)
        assertEquals("Ikkinchi abzas.", textBlocks[1].text)
    }

    @Test
    fun enterStyleSplitPreservesTextAndCaret() {
        val first = NoteBlock.Text(text = "BirinchiIkkinchi")
        val result = NoteBlocksCodec.splitParagraph(listOf(first), first.id, 8)
        val textBlocks = result.blocks.filterIsInstance<NoteBlock.Text>()
        assertEquals(2, textBlocks.size)
        assertEquals("Birinchi", textBlocks[0].text)
        assertEquals("Ikkinchi", textBlocks[1].text)
        assertEquals(textBlocks[1].id, result.focusTextId)
        assertEquals(0, result.focusCursor)
    }

    @Test
    fun mergePreviousRestoresSingleParagraph() {
        val a = NoteBlock.Text(text = "Birinchi ")
        val b = NoteBlock.Text(text = "ikkinchi")
        val result = NoteBlocksCodec.mergeWithPrevious(listOf(a, b), b.id)
        requireNotNull(result)
        val textBlocks = result.blocks.filterIsInstance<NoteBlock.Text>()
        assertEquals(1, textBlocks.size)
        assertEquals("Birinchi ikkinchi", textBlocks.single().text)
        assertEquals(a.text.length, result.focusCursor)
        assertTrue(result.focusTextId == a.id)
    }
}
