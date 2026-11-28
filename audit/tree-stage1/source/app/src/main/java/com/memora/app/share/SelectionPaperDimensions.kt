package com.memora.app.share

/** Pure dimension policy. Pixel values match the Memora Paper 1080px share canvas. */
internal object SelectionPaperDimensions {
    const val TEXT_TOP = 98
    private const val FOOTER_SPACE = 158
    private const val MIN_HEIGHT = 235

    /** No A4-sized blank area below short snippets. */
    fun heightForText(measuredTextHeight: Int): Int =
        (TEXT_TOP + measuredTextHeight.coerceAtLeast(0) + FOOTER_SPACE).coerceAtLeast(MIN_HEIGHT)
}
