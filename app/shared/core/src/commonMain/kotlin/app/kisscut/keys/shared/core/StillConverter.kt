package app.kisscut.keys.shared.core

/**
 * A still image re-encoded for storage (docs/IMPORT_EXPORT.md, Stored format). [scaled] is true when the
 * input was over [ImportLimits.STORED_MAX_SIDE]; [rotated] when its EXIF orientation had to be applied.
 */
class ConvertedStill(
    val bytes: ByteArray,
    val type: ImageType,
    val width: Int,
    val height: Int,
    val scaled: Boolean,
    val rotated: Boolean,
)

/** Decodes a still image, turns it upright, fits it to the stored size and encodes it losslessly. */
interface StillConverter {
    /** Returns null when the bytes cannot be decoded. */
    suspend fun convert(bytes: ByteArray, info: ImageInfo): ConvertedStill?
}

/** Which inputs go through [StillConverter]: still PNG, JPEG and WebP. GIFs are never turned into WebP. */
fun isConvertibleStill(info: ImageInfo): Boolean = !info.isAnimated && info.type != ImageType.GIF

/**
 * "Never store something bigger than the input": keep the received bytes when the conversion neither shrank
 * nor rotated the image and did not make the file smaller.
 */
fun keepsOriginal(originalSize: Int, converted: ConvertedStill): Boolean =
    !converted.scaled && !converted.rotated && originalSize <= converted.bytes.size
