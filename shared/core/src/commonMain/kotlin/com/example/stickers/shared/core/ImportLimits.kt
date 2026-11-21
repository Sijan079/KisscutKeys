package com.example.stickers.shared.core

/** Import safety limits, in one place (docs/IMPORT_EXPORT.md). Pack limits arrive with M5. */
object ImportLimits {
    /** Max bytes of a single sticker file. */
    const val MAX_STICKER_BYTES: Int = 10 * 1024 * 1024

    /** Max width or height in pixels. Guards decoding against images that claim huge sizes. */
    const val MAX_DIMENSION: Int = 16_384

    /** Longest side of a generated thumbnail, in pixels. */
    const val THUMB_MAX_SIDE: Int = 384
}
