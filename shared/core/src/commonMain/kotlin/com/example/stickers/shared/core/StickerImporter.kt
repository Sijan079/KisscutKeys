package com.example.stickers.shared.core

import com.example.stickers.shared.db.Sticker
import com.example.stickers.shared.core.gif.shrinkGif
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * Where incoming image bytes come from (a content URI on Android, a zip entry later).
 * Must return all bytes when there are at most [maxBytes]; when there are more it may stop early
 * but must return more than [maxBytes], so the importer can tell the file is too large.
 */
fun interface ByteSource {
    suspend fun readAtMost(maxBytes: Int): ByteArray
}

enum class RejectReason {
    /** Over [ImportLimits.MAX_STICKER_BYTES] or [ImportLimits.MAX_DIMENSION]. */
    TOO_LARGE,

    /** Not a PNG, JPEG, WebP or GIF. */
    UNSUPPORTED_TYPE,

    /** Could not be read or decoded. */
    UNREADABLE,

    /** Storing failed (disk or database); nothing was kept. */
    FAILED,
}

sealed interface ImportResult {
    data class Added(val sticker: Sticker) : ImportResult

    /** Same bytes were already saved; nothing changed. */
    data class Duplicate(val sticker: Sticker) : ImportResult

    data class Rejected(val reason: RejectReason) : ImportResult
}

/**
 * Imports one image: size cap, type from magic bytes, SHA-256 dedupe of the received bytes, stored format
 * (still images at most 512px, lossless; GIFs over 512px shrunk; see docs/IMPORT_EXPORT.md), files named
 * by hash, thumbnail, row. A failure after writing removes this import's files, so nothing half-saved stays.
 */
class StickerImporter(
    private val stickers: StickerRepository,
    private val files: FileStore,
    private val thumbnailer: Thumbnailer,
    private val converter: StillConverter,
) {
    /** [onProgress] reports 0 to 1 while a large GIF is shrunk (the only slow step); nothing otherwise. */
    suspend fun import(source: ByteSource, collectionId: Long?, onProgress: (Float) -> Unit = {}): ImportResult {
        val bytes = try {
            source.readAtMost(ImportLimits.MAX_STICKER_BYTES)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return ImportResult.Rejected(RejectReason.UNREADABLE)
        }
        if (bytes.size > ImportLimits.MAX_STICKER_BYTES) return ImportResult.Rejected(RejectReason.TOO_LARGE)

        val info = ImageSniffer.sniff(bytes) ?: return ImportResult.Rejected(RejectReason.UNSUPPORTED_TYPE)
        if (info.width > ImportLimits.MAX_DIMENSION || info.height > ImportLimits.MAX_DIMENSION) {
            return ImportResult.Rejected(RejectReason.TOO_LARGE)
        }

        val hash = sha256Hex(bytes)
        // Duplicate share is a no-op: no decode, no writes, no move.
        stickers.findByHash(hash)?.let { return ImportResult.Duplicate(it) }

        val (storedBytes, storedInfo) = storedForm(bytes, info, onProgress)
            ?: return ImportResult.Rejected(RejectReason.UNREADABLE)

        val thumb = guarded { thumbnailer.thumbnail(storedBytes, storedInfo) }
            ?: return ImportResult.Rejected(RejectReason.UNREADABLE)

        val filePath = "$hash.${storedInfo.type.extension}"
        val thumbPath = "$hash.webp"
        return try {
            files.write(FileArea.STICKERS, filePath, storedBytes)
            files.write(FileArea.THUMBS, thumbPath, thumb.webpBytes)
            val result = stickers.add(
                NewSticker(filePath, thumbPath, storedInfo.type.mimeType, info.isAnimated, thumb.hasAlpha, hash),
                collectionId,
            )
            if (result.isNew) ImportResult.Added(result.sticker) else ImportResult.Duplicate(result.sticker)
        } catch (e: Exception) {
            // NonCancellable: a cancelled import (sheet closed mid-save) must still clean up.
            withContext(NonCancellable) {
                // Another import of the same bytes may have won the race; its files are ours too then.
                val owner = runCatching { stickers.findByHash(hash) }.getOrNull()
                if (owner == null) {
                    runCatching { files.delete(FileArea.STICKERS, filePath) }
                    runCatching { files.delete(FileArea.THUMBS, thumbPath) }
                }
            }
            if (e is CancellationException) throw e
            ImportResult.Rejected(RejectReason.FAILED)
        }
    }

    /** The bytes to keep and what they are: converted stills, shrunk large GIFs, the rest as received. */
    private suspend fun storedForm(
        bytes: ByteArray,
        info: ImageInfo,
        onProgress: (Float) -> Unit,
    ): Pair<ByteArray, ImageInfo>? {
        if (info.type == ImageType.GIF) return storedGif(bytes, info, onProgress)
        if (!isConvertibleStill(info)) return bytes to info
        val converted = guarded { converter.convert(bytes, info) } ?: return null
        if (keepsOriginal(bytes.size, converted)) return bytes to info
        val convertedInfo = ImageInfo(converted.type, converted.width, converted.height, false, info.mayHaveAlpha)
        return converted.bytes to convertedInfo
    }

    /**
     * GIFs over the stored size are shrunk and stay GIF. The received bytes are kept when the GIF is small
     * enough already, cannot be shrunk (broken, over the GIF limits) or would not get smaller.
     */
    private suspend fun storedGif(
        bytes: ByteArray,
        info: ImageInfo,
        onProgress: (Float) -> Unit,
    ): Pair<ByteArray, ImageInfo> {
        if (maxOf(info.width, info.height) <= ImportLimits.STORED_MAX_SIDE) return bytes to info
        val shrunk = guarded { shrinkGif(bytes, ImportLimits.STORED_MAX_SIDE, onProgress) }
        if (shrunk == null || shrunk.bytes.size >= bytes.size) return bytes to info
        val shrunkInfo = ImageInfo(ImageType.GIF, shrunk.width, shrunk.height, info.isAnimated, mayHaveAlpha = true)
        return shrunk.bytes to shrunkInfo
    }

    /** Decoder failures (anything but cancellation) count as "unreadable". */
    private suspend fun <T : Any> guarded(block: suspend () -> T?): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
}
