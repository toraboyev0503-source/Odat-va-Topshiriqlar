package com.memora.app.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectionPaperDimensionsTest {
    @Test fun threeLinesMakeCompactCard() {
        assertTrue(SelectionPaperDimensions.heightForText(170) < 550)
    }

    @Test fun heightTracksMeasuredLayout() {
        assertEquals(256, SelectionPaperDimensions.heightForText(0))
        assertEquals(200, SelectionPaperDimensions.heightForText(350) - SelectionPaperDimensions.heightForText(150))
    }
}
