package com.memora.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ordinary IME typing must not rewrite unrelated blocks or change highlight offsets. */
class NoteFastEditTest {
    @Test fun editsOnlyTargetParagraph() {
        val before = (0 until 1000).map { i ->
            NoteBlock.Text(id = "p$i", text = "Paragraph $i")
        }
        val result = NoteBlocksCodec.editText(before, "p500", "Updated paragraph", emptyList(), 7)
        assertEquals(1000, result.blocks.size)
        assertEquals("Updated paragraph", (result.blocks[500] as NoteBlock.Text).text)
        assertSame(before[0], result.blocks[0])
        assertSame(before[999], result.blocks[999])
        assertEquals(7, result.focusCursor)
    }

    @Test fun preservesOtherStylesAndSplitsPastedParagraphs() {
        val highlight = TextInlineStyle(0, 5, highlight = "YELLOW")
        val original = listOf(NoteBlock.Text(id = "a", text = "First", styles = listOf(highlight)))
        val edit = NoteBlocksCodec.editText(original, "a", "First!", listOf(highlight), 6)
        assertEquals("YELLOW", (edit.blocks.first() as NoteBlock.Text).styles.first().highlight)
        val split = NoteBlocksCodec.editText(original, "a", "First\n\nSecond", emptyList(), 8)
        assertTrue(split.blocks.size >= 2)
    }
}
