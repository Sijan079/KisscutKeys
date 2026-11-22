package com.example.stickers.shared.core

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Stored format for still images (docs/IMPORT_EXPORT.md): decode, apply the EXIF orientation (BitmapFactory
 * ignores it, and most camera JPEGs carry one), scale down to fit [maxSide], encode lossless WebP (PNG on
 * API 26 to 28, which have no lossless WebP encoder). Never scales up.
 */
class AndroidStillConverter(private val maxSide: Int = ImportLimits.STORED_MAX_SIDE) : StillConverter {

    override suspend fun convert(bytes: ByteArray, info: ImageInfo): ConvertedStill? =
        withContext(Dispatchers.Default) { run(bytes) }

    private fun run(bytes: ByteArray): ConvertedStill? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val longest = max(bounds.outWidth, bounds.outHeight)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        if (longest > ImportLimits.MAX_DIMENSION) return null

        // Decode at most about 4x the target, so a 16384px image never needs a 1 GB bitmap.
        val options = BitmapFactory.Options().apply {
            inSampleSize = AndroidThumbnailer.sampleSize(longest, maxSide * DECODE_HEADROOM)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
        val orientation = orientationOf(bytes)
        var scaled: Bitmap? = null
        var upright: Bitmap? = null
        try {
            scaled = scaleToStore(decoded)
            upright = orient(scaled, orientation)
            val (format, type) = encoding()
            val out = ByteArrayOutputStream()
            if (!upright.compress(format, ENCODE_QUALITY, out)) return null
            return ConvertedStill(
                bytes = out.toByteArray(),
                type = type,
                width = upright.width,
                height = upright.height,
                scaled = longest > maxSide,
                // Only a real turn or flip counts (UNDEFINED and NORMAL leave the bitmap as it is).
                rotated = upright !== scaled,
            )
        } finally {
            if (upright != null && upright !== scaled) upright.recycle()
            if (scaled != null && scaled !== decoded) scaled.recycle()
            decoded.recycle()
        }
    }

    /**
     * Fits [bitmap] inside [maxSide] (same bitmap if it already fits). Halves with filtering while the half is
     * still at least the target, then one filtered scale (under 2x) to the exact size, so large reductions do
     * not alias. Tests use it as the reference for "same pixels as a 512px scale of the source".
     */
    fun scaleToStore(bitmap: Bitmap): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= maxSide) return bitmap
        val f = maxSide.toFloat() / longest
        val targetW = (bitmap.width * f).roundToInt().coerceAtLeast(1)
        val targetH = (bitmap.height * f).roundToInt().coerceAtLeast(1)
        var current = bitmap
        while (current.width / 2 >= targetW && current.height / 2 >= targetH) {
            val half = Bitmap.createScaledBitmap(current, current.width / 2, current.height / 2, true)
            if (current !== bitmap) current.recycle()
            current = half
        }
        val result = Bitmap.createScaledBitmap(current, targetW, targetH, true)
        if (current !== bitmap && current !== result) current.recycle()
        return result
    }

    private fun orientationOf(bytes: ByteArray): Int = runCatching {
        ExifInterface(ByteArrayInputStream(bytes))
            .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

    /** Turns [bitmap] upright for an EXIF [orientation] (same bitmap when nothing to do). */
    private fun orient(bitmap: Bitmap, orientation: Int): Bitmap {
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { m.setRotate(90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> m.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { m.setRotate(-90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> m.setRotate(-90f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, false)
    }

    @Suppress("DEPRECATION")
    private fun encoding(): Pair<Bitmap.CompressFormat, ImageType> = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> Bitmap.CompressFormat.WEBP_LOSSLESS to ImageType.WEBP
        // API 29: WEBP at quality 100 is lossless.
        Build.VERSION.SDK_INT == Build.VERSION_CODES.Q -> Bitmap.CompressFormat.WEBP to ImageType.WEBP
        else -> Bitmap.CompressFormat.PNG to ImageType.PNG
    }

    private companion object {
        const val DECODE_HEADROOM = 4

        /** Lossless WebP: quality is encoder effort, 100 = smallest file. PNG ignores it. */
        const val ENCODE_QUALITY = 100
    }
}
