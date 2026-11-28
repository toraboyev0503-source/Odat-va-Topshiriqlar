package com.memora.app.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageSamplingTest {
    @Test
    fun modernCameraPhotoIsDecodedWithinStoredLimits() {
        val sample = ImageSampling.sampleSize(8_000, 6_000, ImageSampling.STORED_MAX_DIMENSION, ImageSampling.STORED_MAX_PIXELS)
        assertEquals(4, sample)
        assertTrue(8_000L / sample <= ImageSampling.STORED_MAX_DIMENSION.toLong())
        assertTrue((8_000L / sample) * (6_000L / sample) <= ImageSampling.STORED_MAX_PIXELS)
    }

    @Test
    fun alreadySmallImageNeedsNoSampling() {
        assertEquals(1, ImageSampling.sampleSize(1_200, 800, ImageSampling.STORED_MAX_DIMENSION, ImageSampling.STORED_MAX_PIXELS))
    }

    @Test
    fun veryWideImageUsesDimensionLimitWithoutOverflow() {
        assertEquals(8, ImageSampling.sampleSize(10_000, 300, ImageSampling.STORED_MAX_DIMENSION, ImageSampling.STORED_MAX_PIXELS))
    }
}
