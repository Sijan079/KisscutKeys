package app.kisscut.keys

import android.app.Application
import android.content.Context
import androidx.annotation.VisibleForTesting
import java.io.File
import app.kisscut.keys.backup.AndroidPackBackend
import app.kisscut.keys.backup.PackBackend
import app.kisscut.keys.common.AppLibraryBackend
import app.kisscut.keys.common.LibraryBackend
import app.kisscut.keys.settings.DataStoreKeyboardSettingsStore
import app.kisscut.keys.settings.DataStoreThemeStore
import app.kisscut.keys.settings.KeyboardSettings
import app.kisscut.keys.settings.KeyboardSettingsStore
import app.kisscut.keys.settings.ThemeMode
import app.kisscut.keys.settings.ThemeStore
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
import app.kisscut.keys.shared.core.pack.PackExporter
import app.kisscut.keys.shared.core.pack.PackImporter
import app.kisscut.keys.shared.core.words.DiskLearnedWordsFile
import app.kisscut.keys.shared.core.words.LearnedWordsRepository
import app.kisscut.keys.shared.db.Sticker
import app.kisscut.keys.shared.db.StickerDatabase
import app.kisscut.keys.shared.db.createStickerDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

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
    private val filesDir: File = context.applicationContext.filesDir,
    /** Tests only: keyboard settings kept in memory, so the real settings file is never written. */
    keyboardSettingsOverride: KeyboardSettingsStore? = null,
    /** Where the learned words file lives: `no_backup`, never backed up or moved to a new phone. Tests pass another. */
    private val learnedWordsDir: File = context.applicationContext.noBackupFilesDir,
) {
    private val appContext = context.applicationContext
    private val clock: () -> Long = { System.currentTimeMillis() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

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
    val themeStore: ThemeStore by lazy { DataStoreThemeStore(appContext) }

    /** The theme setting as last read, for the keyboard (which builds its views synchronously). */
    val theme: StateFlow<ThemeMode> by lazy {
        themeStore.theme.stateIn(scope, SharingStarted.Eagerly, ThemeMode.SYSTEM)
    }

    val keyboardSettingsStore: KeyboardSettingsStore by lazy {
        keyboardSettingsOverride ?: DataStoreKeyboardSettingsStore(appContext)
    }

    /** Keyboard settings as last read, for the keyboard (read when a field opens). */
    val keyboardSettings: StateFlow<KeyboardSettings> by lazy {
        keyboardSettingsStore.settings.stateIn(scope, SharingStarted.Eagerly, KeyboardSettings())
    }

    /** The keyboard's learned words (docs/KEYBOARD.md, Learned words); the Settings screen can clear them. */
    val learnedWords: LearnedWordsRepository by lazy {
        LearnedWordsRepository(DiskLearnedWordsFile(learnedWordsDir), clock, scope)
    }

    val appVersion: String by lazy {
        runCatching { appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName }
            .getOrNull().orEmpty()
    }
    val packBackend: PackBackend by lazy {
        AndroidPackBackend(
            appContext,
            PackExporter(database, fileStore, clock, appVersion),
            PackImporter(database, importer, clock),
            filesDir,
        )
    }

    /** Bytes of stored stickers and thumbnails (the Settings storage row). */
    suspend fun storageBytes(): Long = withContext(Dispatchers.IO) {
        listOf("stickers", "thumbs").sumOf { dir ->
            File(filesDir, dir).listFiles().orEmpty().sumOf { it.length() }
        }
    }

    /** Thumbnail file of [sticker] for Coil; null when the stored name is not a safe file name. */
    fun thumbnailOf(sticker: Sticker): File? =
        runCatching { androidFileStore.file(FileArea.THUMBS, sticker.thumbPath) }.getOrNull()

    /** Stored original of [sticker] (what the keyboard sends); null when the name is not safe. */
    fun originalOf(sticker: Sticker): File? =
        runCatching { androidFileStore.file(FileArea.STICKERS, sticker.filePath) }.getOrNull()
}
