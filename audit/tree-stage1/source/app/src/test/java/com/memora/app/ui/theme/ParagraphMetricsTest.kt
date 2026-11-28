package com.memora.app.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class ParagraphMetricsTest {
    @Test
    fun paragraphGapIsExactlySixtyPercentOfLineHeight() {
        assertEquals(0.60f, ParagraphMetrics.GAP_RATIO, 0.0001f)
        assertEquals(14.4f, ParagraphMetrics.EDITOR_LINE_HEIGHT_SP * ParagraphMetrics.GAP_RATIO, 0.0001f)
        assertEquals(16.8f, ParagraphMetrics.READER_LINE_HEIGHT_SP * ParagraphMetrics.GAP_RATIO, 0.0001f)
    }
}
