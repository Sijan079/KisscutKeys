package app.kisscut.keys

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.kisscut.keys.common.AppLibraryBackend
import app.kisscut.keys.common.LocalThumbnails
import app.kisscut.keys.debug.SeedData
import app.kisscut.keys.nav.AppNav
import app.kisscut.keys.shared.core.AndroidFileStore
import app.kisscut.keys.shared.core.AndroidStillConverter
import app.kisscut.keys.shared.core.AndroidThumbnailer
import app.kisscut.keys.shared.core.ByteSource
import app.kisscut.keys.shared.core.CollectionRepository
import app.kisscut.keys.shared.core.FileArea
import app.kisscut.keys.shared.core.ImportResult
import app.kisscut.keys.shared.core.StickerImporter
import app.kisscut.keys.shared.core.StickerRepository
import app.kisscut.keys.shared.db.Sticker
import app.kisscut.keys.shared.db.StickerDatabase
import app.kisscut.keys.shared.db.buildStickerDatabase
import app.kisscut.keys.ui.theme.StickersTheme
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
