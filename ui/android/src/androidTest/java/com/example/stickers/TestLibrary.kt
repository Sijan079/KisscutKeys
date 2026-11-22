package com.example.stickers

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.stickers.common.AppLibraryBackend
import com.example.stickers.common.LocalThumbnails
import com.example.stickers.debug.SeedData
import com.example.stickers.nav.AppNav
import com.example.stickers.shared.core.AndroidFileStore
import com.example.stickers.shared.core.AndroidStillConverter
import com.example.stickers.shared.core.AndroidThumbnailer
import com.example.stickers.shared.core.ByteSource
import com.example.stickers.shared.core.CollectionRepository
import com.example.stickers.shared.core.FileArea
import com.example.stickers.shared.core.ImportResult
import com.example.stickers.shared.core.StickerImporter
import com.example.stickers.shared.core.StickerRepository
import com.example.stickers.shared.db.Sticker
import com.example.stickers.shared.db.StickerDatabase
import com.example.stickers.shared.db.buildStickerDatabase
import com.example.stickers.ui.theme.StickersTheme
import java.io.File

/**
 * An isolated library for UI tests: in-memory database and a temp folder, so tests never touch the
 * emulator's real library. Uses the real repositories, importer and thumbnailer.
 */
class TestLibrary : AutoCloseable {
    private val context = ApplicationProvider.getApplicationContext<App>()
    val dir: File = File(context.cacheDir, "ui-test-${System.nanoTime()}").apply { mkdirs() }
    val db: StickerDatabase = Room.inMemoryDatabaseBuilder(context, StickerDatabase::class.java).buildStickerDatabase()
    val files = AndroidFileStore(dir)
    private var t = 1_000L
    private val clock = { t += 1_000; t }
    val collections = CollectionRepository(db, files, clock)
    val stickers = StickerRepository(db, files, clock)
    private val importer = StickerImporter(stickers, files, AndroidThumbnailer(), AndroidStillConverter())
    val backend = AppLibraryBackend(stickers, collections)
    var addTaps = 0
        private set

    suspend fun add(bytes: ByteArray, collectionId: Long? = null): Sticker =
        (importer.import(ByteSource { bytes }, collectionId) as ImportResult.Added).sticker

    suspend fun face(seed: Int, collectionId: Long? = null) = add(SeedData.face(seed, size = 128), collectionId)
    suspend fun gif(collectionId: Long? = null) = add(SeedData.blinkGif(0xFF9B8CFF.toInt(), size = 48), collectionId)
    suspend fun photo(seed: Int, collectionId: Long? = null) = add(SeedData.photo(seed, 160, 120), collectionId)

    fun fileExists(s: Sticker) = files.file(FileArea.STICKERS, s.filePath).exists()

    @Composable
    fun App(dark: Boolean = true) {
        StickersTheme(dark = dark) {
            CompositionLocalProvider(LocalThumbnails provides { s -> files.file(FileArea.THUMBS, s.thumbPath) }) {
                AppNav(backend, onAdd = { addTaps++ })
            }
        }
    }

    override fun close() {
        db.close()
        dir.deleteRecursively()
    }
}
