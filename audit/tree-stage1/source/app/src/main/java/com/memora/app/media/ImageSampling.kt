package com.memora.app.media

/** Shared, overflow-safe image limits for import and on-screen decoding. */
internal object ImageSampling {
    // Keep private copies sharp enough for a phone/tablet while avoiding transient 20–50 MB
    // bitmap allocations from modern 48/64/108 MP camera files.
    const val STORED_MAX_DIMENSION = 2_048
    const val STORED_MAX_PIXELS = 3_000_000L
    const val DISPLAY_MAX_DIMENSION = 1_440
    const val DISPLAY_MAX_PIXELS = 1_600_000L

    fun sampleSize(width: Int, height: Int, maxDimension: Int, maxPixels: Long): Int {
        require(width > 0 && height > 0)
        require(maxDimension > 0 && maxPixels > 0)
        var sample = 1
        while (true) {
            val sampledWidth = (width.toLong() / sample).coerceAtLeast(1L)
            val sampledHeight = (height.toLong() / sample).coerceAtLeast(1L)
            if (
                maxOf(sampledWidth, sampledHeight) <= maxDimension.toLong() &&
                sampledWidth * sampledHeight <= maxPixels
            ) {
                return sample
            }
            require(sample <= Int.MAX_VALUE / 2) { "Image dimensions are too large" }
            sample *= 2
        }
    }
}
