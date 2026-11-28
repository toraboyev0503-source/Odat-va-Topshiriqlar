package com.memora.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteHighlightPolicyTest {
    @Test fun newHighlightDoesNotChangeText() {
        val text = NoteBlock.Text(text = "Hello world")
        val next = NoteHighlightPolicy.setColor(text, 0, 5, "YELLOW")
        assertEquals(text.text, next.text)
        assertTrue(next.styles.any { it.start == 0 && it.end == 5 && it.highlight == "YELLOW" })
    }

    @Test fun recolorKeepsOtherPartsOfPreviousHighlights() {
        val first = NoteHighlightPolicy.setColor(NoteBlock.Text(text = "abcdefgh"), 1, 7, "GREEN")
        val second = NoteHighlightPolicy.setColor(first, 3, 5, "ROSE")
        assertTrue(second.styles.any { it.start == 1 && it.end == 3 && it.highlight == "GREEN" })
        assertTrue(second.styles.any { it.start == 3 && it.end == 5 && it.highlight == "ROSE" })
        assertTrue(second.styles.any { it.start == 5 && it.end == 7 && it.highlight == "GREEN" })
    }

    @Test fun clearKeepsBoldFormatting() {
        val original = NoteBlock.Text(text = "abcdefgh", styles = listOf(
            TextInlineStyle(0, 8, bold = true), TextInlineStyle(2, 6, highlight = "YELLOW")
        ))
        val next = NoteHighlightPolicy.setColor(original, 3, 5, null)
        assertTrue(next.styles.any { it.start == 0 && it.end == 8 && it.bold })
        assertTrue(next.styles.none { it.highlight != null && it.start < 5 && it.end > 3 })
    }

    @Test fun clippingPreventsOutOfRangeStyle() {
        val next = NoteHighlightPolicy.setColor(NoteBlock.Text(text = "abc"), -20, 40, "YELLOW")
        assertEquals(0, next.styles.single().start)
        assertEquals(3, next.styles.single().end)
    }
    @Test fun tappingHighlightFindsWholeRun() {
        val original = NoteBlock.Text(text = "abcdefghi")
        val marked = NoteHighlightPolicy.setColor(original, 2, 7, "YELLOW")
        val found = NoteHighlightPolicy.runAt(marked, 4)
        assertEquals(NoteHighlightPolicy.HighlightRun(2, 7, "YELLOW"), found)
        assertEquals(null, NoteHighlightPolicy.runAt(marked, 1))
    }

    @Test fun recoloredPartIsAnIndependentTapTarget() {
        val first = NoteHighlightPolicy.setColor(NoteBlock.Text(text = "abcdefgh"), 1, 7, "YELLOW")
        val next = NoteHighlightPolicy.setColor(first, 3, 5, "GREEN")
        assertEquals(NoteHighlightPolicy.HighlightRun(3, 5, "GREEN"), NoteHighlightPolicy.runAt(next, 4))
        assertEquals(NoteHighlightPolicy.HighlightRun(1, 3, "YELLOW"), NoteHighlightPolicy.runAt(next, 2))
    }

    @Test fun deletingMarkerMakesItPlainText() {
        val before = NoteHighlightPolicy.setColor(NoteBlock.Text(text = "abcdefgh"), 1, 5, "ROSE")
        val after = NoteHighlightPolicy.setColor(before, 1, 5, null)
        assertEquals(null, NoteHighlightPolicy.runAt(after, 3))
        assertEquals("abcdefgh", after.text)
    }
}
