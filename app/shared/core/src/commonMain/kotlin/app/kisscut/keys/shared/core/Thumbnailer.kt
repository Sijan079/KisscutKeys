package app.kisscut.keys.shared.core

/** A small static WebP preview plus what decoding learned about the pixels. */
class Thumbnail(val webpBytes: ByteArray, val hasAlpha: Boolean)

/** Decodes an image (first frame if animated) into a [Thumbnail]. Platform code implements it. */
interface Thumbnailer {
    /** Returns null when the bytes cannot be decoded. */
    suspend fun thumbnail(bytes: ByteArray, info: ImageInfo): Thumbnail?
}
