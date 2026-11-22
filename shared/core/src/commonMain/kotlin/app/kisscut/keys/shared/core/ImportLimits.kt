package app.kisscut.keys.shared.core

/** Import safety limits, in one place (docs/IMPORT_EXPORT.md). Pack limits arrive with M5. */
object ImportLimits {
    /** Max bytes of a single sticker file. */
    const val MAX_STICKER_BYTES: Int = 10 * 1024 * 1024

    /** Max width or height in pixels. Guards decoding against images that claim huge sizes. */
    const val MAX_DIMENSION: Int = 16_384

    /** Longest side of a stored still image, in pixels. Larger ones are scaled down at import. */
    const val STORED_MAX_SIDE: Int = 512

    /** Largest GIF canvas decoded (2048 x 2048); one ARGB canvas is then at most 16 MB. */
    const val GIF_MAX_CANVAS_PIXELS: Int = 2048 * 2048

    /** Most frames read from one GIF. */
    const val GIF_MAX_FRAMES: Int = 5000

    /** Most pixels touched decoding one GIF (canvas plus frame area, summed over frames); bounds CPU time. */
    const val GIF_MAX_WORK_PIXELS: Long = 1_000_000_000L

    /** Longest side of a generated thumbnail, in pixels. */
    const val THUMB_MAX_SIDE: Int = 384
}
