package app.kisscut.keys

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.provider.MediaStore
import java.io.ByteArrayOutputStream

/** Generated test images (no downloads, no committed binaries). */
object TestImageFiles {
    /** PNG of [width]x[height]; transparent corners when [transparent], else fully opaque. */
    fun png(width: Int = 64, height: Int = 64, transparent: Boolean = true, seed: Int = 0): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(155, 140, 255 - seed % 200))
        if (transparent) bitmap.setPixel(0, 0, Color.TRANSPARENT)
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    /** Square PNG of [size] px: a filled circle of [color] on a transparent background (a die-cut sticker). */
    fun circlePng(size: Int = 200, color: Int = Color.rgb(255, 122, 89)): ByteArray {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        android.graphics.Canvas(bitmap).drawCircle(size / 2f, size / 2f, size * 0.4f, paint)
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    /** Lossless WebP (API 30+ encoder), same pixels as [png]. */
    fun webp(width: Int = 64, height: Int = 64, transparent: Boolean = true): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(155, 140, 255))
        if (transparent) bitmap.setPixel(0, 0, Color.TRANSPARENT)
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 100, it) }
            .toByteArray()
    }

    /** Opaque PNG with a colour gradient (so scaling changes pixels) and a fully transparent top-left block. */
    fun gradientPng(width: Int, height: Int): ByteArray {
        val pixels = IntArray(width * height) { i ->
            val x = i % width
            val y = i / width
            val corner = x < width / 8 && y < height / 8
            if (corner) Color.TRANSPARENT else Color.rgb(x * 255 / width, y * 255 / height, 90)
        }
        val bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    /**
     * JPEG of [width]x[height], left half red and right half blue, with EXIF [orientation]
     * (6 = the camera was turned 90 degrees clockwise). ExifInterface can only save to a file, hence [context].
     */
    fun halvesJpeg(context: Context, width: Int, height: Int, orientation: Int): ByteArray {
        val pixels = IntArray(width * height) { i -> if (i % width < width / 2) Color.RED else Color.BLUE }
        val bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        val file = java.io.File(context.cacheDir, "exif-test.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        android.media.ExifInterface(file.path).apply {
            setAttribute(android.media.ExifInterface.TAG_ORIENTATION, orientation.toString())
            saveAttributes()
        }
        return file.readBytes().also { file.delete() }
    }

    /** The classic 1x1 transparent GIF89a. */
    val GIF_1X1: ByteArray = byteArrayOf(
        0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 0x01, 0x00, 0x01, 0x00, 0x80.toByte(), 0x00, 0x00,
        0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x00, 0x00, 0x00,
        0x21, 0xF9.toByte(), 0x04, 0x01, 0x00, 0x00, 0x00, 0x00,
        0x2C, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00,
        0x02, 0x02, 0x44, 0x01, 0x00, 0x3B,
    )

    /** Inserts [bytes] into MediaStore (no permission needed on API 29+) and returns its content URI. */
    fun insertImage(context: Context, bytes: ByteArray, name: String, mime: String = "image/png"): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, mime)
        }
        val resolver = context.contentResolver
        val uri = checkNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
        resolver.openOutputStream(uri)!!.use { it.write(bytes) }
        return uri
    }
}
