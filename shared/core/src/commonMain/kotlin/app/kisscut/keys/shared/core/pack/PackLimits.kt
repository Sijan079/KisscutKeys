package app.kisscut.keys.shared.core.pack

import app.kisscut.keys.shared.core.ImportLimits

/**
 * `.stickerpack` limits (docs/IMPORT_EXPORT.md, Limits). The defaults are the real values; tests pass
 * smaller ones to reach the limits quickly.
 */
data class PackLimits(
    /** Size of the pack file itself. 1 GB so a full backup of a 1000+ sticker library fits. */
    val maxPackBytes: Long = 1L shl 30,
    val maxStickers: Int = 10_000,
    /** Zip entries read, of any kind. */
    val maxEntries: Int = 12_000,
    /** Bytes actually decompressed, summed over all entries; stops zip bombs. */
    val maxTotalUnpacked: Long = 2L shl 30,
    val maxManifestBytes: Int = 2 * 1024 * 1024,
    val maxStickerBytes: Int = ImportLimits.MAX_STICKER_BYTES,
    /** Free space needed on top of the pack size before an import starts. */
    val freeSpaceMargin: Long = 50L * 1024 * 1024,
)
