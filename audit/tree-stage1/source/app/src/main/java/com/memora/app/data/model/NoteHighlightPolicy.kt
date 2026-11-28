package com.memora.app.data.model

/** Annotations are stored as style spans inside the note's blocksJson. They survive backup,
 * splitting/merging paragraphs and editor's offset adjustments without extra database tables.
 * An existing bold or italic span is never modified by the highlighter.
 */
object NoteHighlightPolicy {
    val colors = setOf("YELLOW", "GREEN", "ROSE")

    /** A tap inside a colored phrase selects its entire uninterrupted highlighted run.
     * This is independent from the Android text-selection toolbar, which is replaced in the reader.
     * Adjacent pieces of the same color are treated as one visible phrase.
     */
    data class HighlightRun(val start: Int, val end: Int, val color: String)

    fun runAt(block: NoteBlock.Text, offset: Int): HighlightRun? {
        if (offset !in block.text.indices) return null
        val marked = block.styles.filter { it.highlight in colors && it.end > it.start }
        fun colorAt(position: Int): String? = marked.lastOrNull {
            position >= it.start && position < it.end
        }?.highlight
        val color = colorAt(offset) ?: return null
        var left = offset
        var right = offset + 1
        while (left > 0 && colorAt(left - 1) == color) left--
        while (right < block.text.length && colorAt(right) == color) right++
        return HighlightRun(left, right, color)
    }

    fun setColor(block: NoteBlock.Text, start: Int, end: Int, color: String?): NoteBlock.Text {
        require(color == null || color in colors)
        val a = minOf(start, end).coerceIn(0, block.text.length)
        val b = maxOf(start, end).coerceIn(0, block.text.length)
        if (a >= b) return block
        val result = mutableListOf<TextInlineStyle>()
        for (style in block.styles) {
            if (style.highlight == null || style.end <= a || style.start >= b) {
                result += style
                continue
            }
            // Keep the annotated parts outside the changed range; preserve bold/italic.
            if (style.start < a) result += style.copy(end = a)
            if (style.end > b) result += style.copy(start = b)
            if (style.bold || style.italic) {
                result += style.copy(start = maxOf(a, style.start), end = minOf(b, style.end), highlight = null)
            }
        }
        if (color != null) result += TextInlineStyle(start = a, end = b, highlight = color)
        return block.copy(styles = result.filter { it.end > it.start }.sortedWith(compareBy({ it.start }, { it.end })))
    }
}
