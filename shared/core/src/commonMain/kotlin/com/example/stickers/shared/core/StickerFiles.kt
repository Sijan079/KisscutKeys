package com.example.stickers.shared.core

import com.example.stickers.shared.db.Sticker

/**
 * Deletes the original and thumbnail of each sticker. Call only after the rows are gone (committed).
 * A failed delete is reported and skipped; the startup orphan sweep cleans it up later.
 */
internal suspend fun FileStore.deleteFilesOf(stickers: List<Sticker>): FileDeleteReport {
    val failed = mutableListOf<Pair<FileArea, String>>()
    for (s in stickers) {
        for ((area, path) in listOf(FileArea.STICKERS to s.filePath, FileArea.THUMBS to s.thumbPath)) {
            val ok = runCatching { delete(area, path) }.getOrDefault(false)
            if (!ok) failed += area to path
        }
    }
    return FileDeleteReport(failed)
}
