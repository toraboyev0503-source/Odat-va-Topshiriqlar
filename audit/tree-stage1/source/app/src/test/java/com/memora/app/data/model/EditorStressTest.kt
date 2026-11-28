package com.memora.app.data.model

import com.memora.app.util.EditorInputNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorStressTest {
    @Test
    fun thousandStructuralSplitsAndMergesStayConsistent() {
        var blocks: List<NoteBlock> = listOf(NoteBlock.Text(text = "0. start"))
        var activeId = (blocks.single() as NoteBlock.Text).id

        repeat(1_000) { index ->
            val active = blocks.filterIsInstance<NoteBlock.Text>().first { it.id == activeId }
            val prefix = if (index % 5 == 0) "${index + 1}. " else ""
            val result = NoteBlocksCodec.splitParagraph(
                blocks = blocks,
                id = activeId,
                cursor = active.text.length + 10_000,
                nextPrefix = prefix
            )
            blocks = result.blocks
            activeId = result.focusTextId
            assertTrue(result.focusCursor >= 0)
        }

        assertEquals(1_001, blocks.filterIsInstance<NoteBlock.Text>().size)

        repeat(1_000) {
            val result = NoteBlocksCodec.mergeWithPrevious(blocks, activeId)
            assertNotNull(result)
            blocks = result!!.blocks
            activeId = result.focusTextId
        }

        assertEquals(1, blocks.filterIsInstance<NoteBlock.Text>().size)
    }

    @Test
    fun hugePasteAndOutOfRangeCursorAreClampedSafely() {
        val source = NoteBlock.Text(text = "seed")
        val pasted = buildString {
            repeat(1_001) { index ->
                if (index > 0) append("\n\n")
                append("paragraph-").append(index)
            }
        }
        val result = NoteBlocksCodec.editText(
            blocks = listOf(source),
            id = source.id,
            text = pasted,
            styles = emptyList(),
            cursor = Int.MAX_VALUE
        )
        val texts = result.blocks.filterIsInstance<NoteBlock.Text>()
        assertEquals(1_001, texts.size)
        assertEquals(texts.last().id, result.focusTextId)
        assertEquals(texts.last().text.length, result.focusCursor)
    }

    @Test
    fun rapidImePunctuationAndEnterKeepsCommittedCharacters() {
        var previous = "1. Start"
        repeat(1_000) { index ->
            val incoming = previous + if (index % 2 == 0) ".\n" else ",\n"
            val edit = EditorInputNormalizer.paragraphBreakEdit(
                previousText = previous,
                incomingText = incoming,
                incomingCursor = Int.MAX_VALUE
            )
            assertNotNull(edit)
            assertTrue(edit!!.text.endsWith(if (index % 2 == 0) "." else ","))
            assertTrue(edit.cursor in 0..edit.text.length)
            previous = "${index + 2}. item"
        }
    }

    @Test
    fun numberedListDoesNotOverflow() {
        val marker = "${Long.MAX_VALUE}. final"
        assertEquals("", EditorInputNormalizer.numberedListPrefix(marker, Int.MAX_VALUE))
    }
}
