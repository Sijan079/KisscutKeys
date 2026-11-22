package app.kisscut.keys

import android.app.Application
import android.content.Context
import androidx.annotation.VisibleForTesting
import java.io.File
import app.kisscut.keys.common.AppLibraryBackend
import app.kisscut.keys.common.LibraryBackend
import app.kisscut.keys.setup.AndroidKeyboardStatus
import app.kisscut.keys.setup.DataStoreSetupStore
import app.kisscut.keys.setup.SetupStore
import app.kisscut.keys.shared.core.AndroidFileStore
import app.kisscut.keys.shared.core.FileArea
import app.kisscut.keys.shared.core.AndroidStillConverter
import app.kisscut.keys.shared.core.AndroidThumbnailer
import app.kisscut.keys.shared.core.CollectionRepository
import app.kisscut.keys.shared.core.FileStore
import app.kisscut.keys.shared.core.StickerImporter
import app.kisscut.keys.shared.core.StickerRepository
import app.kisscut.keys.shared.db.Sticker
import app.kisscut.keys.shared.db.StickerDatabase
import app.kisscut.keys.shared.db.createStickerDatabase

class App : Application() {
    /** Replaced only by keyboard tests, to point the keyboard at an isolated library. */
    lateinit var container: AppContainer
        @VisibleForTesting internal set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/**
 * Manual DI (docs/ARCHITECTURE.md). Built once in [App.onCreate]; the keyboard service reaches it
 * through `(applicationContext as App).container`. Everything is lazy so startup stays cheap.
 */
class AppContainer(
    context: Context,
    /** Tests only: an in-memory database and a separate files folder instead of the real library. */
    databaseOverride: StickerDatabase? = null,
    filesDir: File = context.applicationContext.filesDir,
) {
    private val appContext = context.applicationContext
    private val clock: () -> Long = { System.currentTimeMillis() }

    /** The one database shared by the app and the keyboard. */
    val database: StickerDatabase by lazy { databaseOverride ?: createStickerDatabase(appContext) }
    val androidFileStore: AndroidFileStore by lazy { AndroidFileStore(filesDir) }
    val fileStore: FileStore get() = androidFileStore
    val collections: CollectionRepository by lazy { CollectionRepository(database, fileStore, clock) }
    val stickers: StickerRepository by lazy { StickerRepository(database, fileStore, clock) }
    val importer: StickerImporter by lazy {
        StickerImporter(stickers, fileStore, AndroidThumbnailer(), AndroidStillConverter())
    }
    val libraryBackend: LibraryBackend by lazy { AppLibraryBackend(stickers, collections) }
    val keyboardStatus: AndroidKeyboardStatus by lazy { AndroidKeyboardStatus(appContext) }
    val setupStore: SetupStore by lazy { DataStoreSetupStore(appContext) }

    /** Thumbnail file of [sticker] for Coil; null when the stored name is not a safe file name. */
    fun thumbnailOf(sticker: Sticker): File? =
        runCatching { androidFileStore.file(FileArea.THUMBS, sticker.thumbPath) }.getOrNull()

    /** Stored original of [sticker] (what the keyboard sends); null when the name is not safe. */
    fun originalOf(sticker: Sticker): File? =
        runCatching { androidFileStore.file(FileArea.STICKERS, sticker.filePath) }.getOrNull()
}
