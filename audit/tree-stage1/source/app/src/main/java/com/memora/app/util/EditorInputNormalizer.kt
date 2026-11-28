package com.memora.app.util

/**
 * Conservative IME helper. Paragraph creation is structural: a newline emitted by the keyboard is
 * converted into a new Memora paragraph block instead of being stored inside a text block.
 */
object EditorInputNormalizer {
    data class Result(val text: String, val cursor: Int, val changed: Boolean)

    /**
     * A keyboard can commit punctuation and Enter in the same callback (for example
     * `"1. Matn" -> "1. Matn.\n"`). Keep every committed character, remove only the newline run,
     * and report the exact structural split position. This is intentionally independent of the
     * incoming selection offset because OEM keyboards report that offset inconsistently.
     */
    data class ParagraphBreakEdit(val text: String, val cursor: Int)

    fun normalize(
        previousText: String,
        incomingText: String,
        incomingCursor: Int,
        hasComposition: Boolean
    ): Result {
        val safeCursor = incomingCursor.coerceIn(0, incomingText.length)
        return Result(incomingText, safeCursor, false)
    }

    fun paragraphBreakEdit(
        previousText: String,
        incomingText: String,
        incomingCursor: Int
    ): ParagraphBreakEdit? {
        // A PlainTextEditor owns one paragraph, so any newly introduced newline is structural.
        if ('\n' !in incomingText || incomingText == previousText) return null

        val runs = Regex("\\n+").findAll(incomingText).toList()
        if (runs.isEmpty()) return null

        // Soft-keyboard Enter normally creates one newline run. For pasted multi-paragraph text,
        // process the first run here; the remaining text is preserved and can be normalized by the
        // paragraph codec on the following state update.
        val run = runs.first()
        val start = run.range.first
        val endExclusive = run.range.last + 1
        val committed = incomingText.removeRange(start, endExclusive)
        return ParagraphBreakEdit(
            text = committed,
            cursor = start.coerceIn(0, committed.length)
        )
    }

    /** Backward-compatible cursor helper used by tests and older callers. */
    fun paragraphBreakCursor(
        previousText: String,
        incomingText: String,
        incomingCursor: Int
    ): Int? = paragraphBreakEdit(previousText, incomingText, incomingCursor)?.cursor

    /** Returns only the next list marker, never a newline. */
    fun numberedListPrefix(text: String, cursor: Int): String {
        val before = text.substring(0, cursor.coerceIn(0, text.length))
        val currentLine = before.substringAfterLast('\n')
        val match = Regex("^\\s*(\\d+)\\.\\s*(.*)$").matchEntire(currentLine) ?: return ""
        val number = match.groupValues[1].toLongOrNull() ?: return ""
        val content = match.groupValues[2]
        if (content.isBlank() || number == Long.MAX_VALUE) return ""
        return "${number + 1}. "
    }
}
