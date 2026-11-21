package com.example.stickers.shared.core

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Decodes with BitmapFactory (first frame for GIF and animated WebP), downsampled while decoding so
 * memory stays bounded, then scales to [maxSide] and encodes WebP. `hasAlpha` is read from the pixels.
 * The die-cut outline is not drawn here yet (M3).
 */
class AndroidThumbnailer(private val maxSide: Int = ImportLimits.THUMB_MAX_SIDE) : Thumbnailer {

    override suspend fun thumbnail(bytes: ByteArray, info: ImageInfo): Thumbnail? =
        withContext(Dispatchers.Default) { decode(bytes) }

    private fun decode(bytes: ByteArray): Thumbnail? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val width = bounds.outWidth
        val height = bounds.outHeight
        if (width <= 0 || height <= 0) return null
        if (width > ImportLimits.MAX_DIMENSION || height > ImportLimits.MAX_DIMENSION) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(max(width, height), maxSide)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
        val scaled = scaleToFit(decoded, maxSide)
        try {
            val pixels = IntArray(scaled.width * scaled.height)
            scaled.getPixels(pixels, 0, scaled.width, 0, 0, scaled.width, scaled.height)
            val hasAlpha = scaled.hasAlpha() && hasTransparentPixel(pixels)
            val out = ByteArrayOutputStream()
            if (!scaled.compress(webpFormat(), WEBP_QUALITY, out)) return null
            return Thumbnail(out.toByteArray(), hasAlpha)
        } finally {
            if (scaled !== decoded) scaled.recycle()
            decoded.recycle()
        }
    }

    private fun scaleToFit(bitmap: Bitmap, maxSide: Int): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= maxSide) return bitmap
        val f = maxSide.toFloat() / longest
        val w = (bitmap.width * f).roundToInt().coerceAtLeast(1)
        val h = (bitmap.height * f).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, w, h, true)
    }

    @Suppress("DEPRECATION")
    private fun webpFormat(): Bitmap.CompressFormat =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            Bitmap.CompressFormat.WEBP
        }

    internal companion object {
        const val WEBP_QUALITY = 90

        /** Largest power of two that keeps the decoded longest side at or above [target]. */
        fun sampleSize(longest: Int, target: Int): Int {
            var sample = 1
            while (longest / (sample * 2) >= target) sample *= 2
            return sample
        }
    }
}
