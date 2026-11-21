package com.example.stickers.shared.core

import kotlin.test.Test
import kotlin.test.assertEquals

class ThumbnailSampleSizeTest {
    @Test
    fun smallImagesAreNotSubsampled() {
        assertEquals(1, AndroidThumbnailer.sampleSize(longest = 300, target = 384))
        assertEquals(1, AndroidThumbnailer.sampleSize(longest = 767, target = 384))
    }

    @Test
    fun largeImagesDecodeNoSmallerThanTheTarget() {
        assertEquals(2, AndroidThumbnailer.sampleSize(longest = 768, target = 384))
        assertEquals(32, AndroidThumbnailer.sampleSize(longest = ImportLimits.MAX_DIMENSION, target = 384))
    }
}
