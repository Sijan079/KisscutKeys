package com.example.stickers

import android.app.Application
import android.content.Context
import java.io.File
import com.example.stickers.common.AppLibraryBackend
import com.example.stickers.common.LibraryBackend
import com.example.stickers.shared.core.AndroidFileStore
import com.example.stickers.shared.core.FileArea
import com.example.stickers.shared.core.AndroidThumbnailer
import com.example.stickers.shared.core.CollectionRepository
import com.example.stickers.shared.core.FileStore
import com.example.stickers.shared.core.StickerImporter
import com.example.stickers.shared.core.StickerRepository
import com.example.stickers.shared.db.Sticker
import com.example.stickers.shared.db.StickerDatabase
import com.example.stickers.shared.db.createStickerDatabase

class App : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/**
 * Manual DI (docs/ARCHITECTURE.md). Built once in [App.onCreate]; the keyboard service reaches it
 * through `(applicationContext as App).container`. Everything is lazy so startup stays cheap.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val clock: () -> Long = { System.currentTimeMillis() }

    /** The one database shared by the app and the keyboard. */
    val database: StickerDatabase by lazy { createStickerDatabase(appContext) }
    val androidFileStore: AndroidFileStore by lazy { AndroidFileStore(appContext.filesDir) }
    val fileStore: FileStore get() = androidFileStore
    val collections: CollectionRepository by lazy { CollectionRepository(database, fileStore, clock) }
    val stickers: StickerRepository by lazy { StickerRepository(database, fileStore, clock) }
    val importer: StickerImporter by lazy { StickerImporter(stickers, fileStore, AndroidThumbnailer()) }
    val libraryBackend: LibraryBackend by lazy { AppLibraryBackend(stickers, collections) }

    /** Thumbnail file of [sticker] for Coil; null when the stored name is not a safe file name. */
    fun thumbnailOf(sticker: Sticker): File? =
        runCatching { androidFileStore.file(FileArea.THUMBS, sticker.thumbPath) }.getOrNull()
}
