package app.kisscut.keys.shared.core

import androidx.room.Room
import app.kisscut.keys.shared.db.StickerDatabase
import app.kisscut.keys.shared.db.buildStickerDatabase

fun inMemoryStickerDatabase(): StickerDatabase =
    Room.inMemoryDatabaseBuilder<StickerDatabase>().buildStickerDatabase()

/** In-memory [FileStore]. Paths listed in [failDeletes] report a failed delete and stay. */
class FakeFileStore(private val failDeletes: Set<String> = emptySet()) : FileStore {
    val files = mutableMapOf<Pair<FileArea, String>, ByteArray>()

    fun has(area: FileArea, path: String) = (area to path) in files

    override suspend fun write(area: FileArea, relativePath: String, bytes: ByteArray) {
        files[area to relativePath] = bytes
    }

    override suspend fun exists(area: FileArea, relativePath: String) = has(area, relativePath)

    override suspend fun delete(area: FileArea, relativePath: String): Boolean {
        if (relativePath in failDeletes) return false
        files.remove(area to relativePath)
        return true
    }
}

/** Monotonic test clock: each call returns the next value. */
class TestClock(start: Long = 1_000L, private val step: Long = 1_000L) : () -> Long {
    private var t = start - step
    override fun invoke(): Long {
        t += step
        return t
    }
}

fun newSticker(hash: String, mime: String = "image/png") = NewSticker(
    filePath = "$hash.png",
    thumbPath = "$hash.webp",
    mimeType = mime,
    isAnimated = false,
    hasAlpha = true,
    hash = hash,
)
