package com.memora.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class EditorInputNormalizerTest {
    @Test
    fun commaDoesNotCreateParagraph() {
        val result = EditorInputNormalizer.normalize("Salom", "Salom,", 6, false)
        assertEquals("Salom,", result.text)
        assertFalse(result.changed)
    }

    @Test
    fun normalLetterDoesNotCreateParagraph() {
        val result = EditorInputNormalizer.normalize("salo", "salom", 5, false)
        assertEquals("salom", result.text)
        assertFalse(result.changed)
    }

    @Test
    fun composingUpdateIsNeverTransformed() {
        val result = EditorInputNormalizer.normalize("haqi", "haqida", 6, true)
        assertEquals("haqida", result.text)
        assertFalse(result.changed)
    }

    @Test
    fun enterIsLeftForStructuralParagraphEditor() {
        val result = EditorInputNormalizer.normalize("Birinchi", "Birinchi\n", 9, false)
        assertEquals("Birinchi\n", result.text)
        assertEquals(9, result.cursor)
        assertFalse(result.changed)
    }

    @Test
    fun periodAtSentenceEndIsNotChanged() {
        val result = EditorInputNormalizer.normalize("Salom", "Salom.", 6, false)
        assertEquals("Salom.", result.text)
        assertEquals(6, result.cursor)
        assertFalse(result.changed)
    }

    @Test
    fun decimalPeriodIsNotChanged() {
        val result = EditorInputNormalizer.normalize("12", "12.", 3, false)
        assertEquals("12.", result.text)
        assertFalse(result.changed)
    }

    @Test
    fun enterAfterSentencePeriodCreatesOneStructuralParagraph() {
        assertEquals(
            16,
            EditorInputNormalizer.paragraphBreakCursor(
                previousText = "1. Oxirgi jumla.",
                incomingText = "1. Oxirgi jumla.\n",
                incomingCursor = 17
            )
        )
        assertEquals("2. ", EditorInputNormalizer.numberedListPrefix("1. Oxirgi jumla.", 16))
    }

    @Test
    fun composedEnterIsStillRecognizedWhenItIsAnExactNewlineInsertion() {
        assertEquals(
            5,
            EditorInputNormalizer.paragraphBreakCursor("Salom", "Salom\n", 6)
        )
    }

    @Test
    fun incompleteNumberedMarkerDoesNotKeepNumbering() {
        assertEquals("", EditorInputNormalizer.numberedListPrefix("7. ", 3))
    }
    @Test
    fun duplicateImeNewlineCallbackStillMeansOneParagraphBreak() {
        assertEquals(
            16,
            EditorInputNormalizer.paragraphBreakCursor(
                previousText = "1. Oxirgi jumla.",
                incomingText = "1. Oxirgi jumla.\n\n",
                incomingCursor = 18
            )
        )
    }

    @Test
    fun imeCommittedPeriodAndEnterArePreservedAndNumberingContinues() {
        val edit = EditorInputNormalizer.paragraphBreakEdit(
            previousText = "1. Oxirgi jumla",
            incomingText = "1. Oxirgi jumla.\n",
            incomingCursor = 17
        )
        requireNotNull(edit)
        assertEquals("1. Oxirgi jumla.", edit.text)
        assertEquals(16, edit.cursor)
        assertEquals("2. ", EditorInputNormalizer.numberedListPrefix(edit.text, edit.cursor))
    }

    @Test
    fun numberedListAlsoContinuesWhenMarkerHasNoSpace() {
        assertEquals("10. ", EditorInputNormalizer.numberedListPrefix("9.Band matni.", 13))
    }

}
