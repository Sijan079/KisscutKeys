package app.kisscut.keys

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.activity.viewModels
import androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.kisscut.keys.backup.AndroidPackBackend
import app.kisscut.keys.backup.BackupDialogs
import app.kisscut.keys.backup.BackupViewModel
import app.kisscut.keys.common.LocalThumbnails
import app.kisscut.keys.common.TopLevel
import app.kisscut.keys.nav.AppNav
import app.kisscut.keys.save.AppSaveBackend
import app.kisscut.keys.save.SaveSheet
import app.kisscut.keys.save.SaveViewModel
import app.kisscut.keys.settings.SettingsActions
import app.kisscut.keys.settings.SettingsScreen
import app.kisscut.keys.settings.SettingsViewModel
import app.kisscut.keys.settings.ThemeMode
import app.kisscut.keys.setup.KeyboardSetupScreen
import app.kisscut.keys.setup.KeyboardSetupViewModel
import app.kisscut.keys.setup.SetupAction
import app.kisscut.keys.share.sharedImageUris
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import app.kisscut.keys.ui.theme.StickersTheme

/**
 * Single activity. Also the share target for `ACTION_SEND` / `ACTION_SEND_MULTIPLE` with any image type:
 * a share opens the Save sheet, and Done returns to the app that shared.
 */
class MainActivity : ComponentActivity() {
    /** URIs waiting in the Save sheet; empty = no sheet. */
    private var pending by mutableStateOf<List<String>>(emptyList())
    private var fromShare = false
    private var launchedByShare = false

    /** Bumped on every new Save request, so the same URIs picked again get a fresh ViewModel. */
    private var session by mutableIntStateOf(0)

    /** Bumped each time the keyboard's toolbar asks for the Settings screen ([EXTRA_OPEN_SETTINGS]). */
    private var settingsRequests by mutableIntStateOf(0)

    private val pickImages = registerForActivityResult(PickMultipleVisualMedia(MAX_PICK)) { uris ->
        if (uris.isNotEmpty()) openSave(uris.map { it.toString() }, fromShare = false)
    }

    private val backups: BackupViewModel by viewModels {
        viewModelFactory {
            initializer {
                val c = (application as App).container
                BackupViewModel(c.packBackend, c.themeStore, c.collections.observeAll())
            }
        }
    }

    /** Settings "Back up everything": the user picks where the backup file goes. */
    private val createBackup = registerForActivityResult(CreateDocument(PACK_MIME)) { uri ->
        uri?.let { backups.backUp(it.toString()) }
    }

    /** Settings "Import a pack". Any file type: packs arrive typed as zip, octet-stream or nothing. */
    private val openPack = registerForActivityResult(OpenDocument()) { uri ->
        uri?.let { backups.import(it.toString()) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            takeShare(intent)
            launchedByShare = pending.isNotEmpty() || takePack(intent) || takeSettings(intent)
        } else {
            pending = savedInstanceState.getStringArrayList(KEY_PENDING).orEmpty()
            fromShare = savedInstanceState.getBoolean(KEY_FROM_SHARE)
            session = savedInstanceState.getInt(KEY_SESSION)
        }
        val container = (application as App).container
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) { backups.shareRequests.collect(::sharePack) }
        }
        setContent {
            val theme by container.themeStore.theme.collectAsState(initial = null)
            val dark = (theme ?: ThemeMode.SYSTEM).isDark(isSystemInDarkTheme())
            // Status and navigation bar icons follow the app theme, not the system one.
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
            StickersTheme(dark = dark) {
                val addStickers = { pickImages.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) }
                // Keyboard setup opens first until it is finished or skipped, except when a share started us.
                val dismissed by container.setupStore.dismissed.collectAsState(initial = null)
                var startAtSetup by rememberSaveable { mutableStateOf<Boolean?>(null) }
                if (startAtSetup == null) dismissed?.let { startAtSetup = !it && !launchedByShare }
                val start = startAtSetup
                if (start == null || theme == null) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
                } else {
                    CompositionLocalProvider(LocalThumbnails provides container::thumbnailOf) {
                        AppNav(
                            backend = container.libraryBackend,
                            onAdd = addStickers,
                            startAtSetup = start,
                            setupScreen = { leave -> Setup(container, addStickers, leave) },
                            settingsScreen = { go, openSetup, openLicenses ->
                                Settings(container, go, openSetup, openLicenses)
                            },
                            settingsRequests = settingsRequests,
                        )
                    }
                }
                val uris = pending
                if (uris.isNotEmpty()) {
                    val vm: SaveViewModel = viewModel(
                        key = "save:$session",
                        factory = viewModelFactory {
                            initializer {
                                SaveViewModel(
                                    AppSaveBackend(contentResolver, container.collections, container.importer),
                                    uris,
                                )
                            }
                        },
                    )
                    SaveSheet(vm, onFinished = ::finishSave)
                }
                BackupDialogs(backups)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        takeShare(intent)
        takePack(intent)
        takeSettings(intent)
    }

    private fun takeSettings(intent: Intent): Boolean {
        if (!intent.getBooleanExtra(EXTRA_OPEN_SETTINGS, false)) return false
        intent.removeExtra(EXTRA_OPEN_SETTINGS)
        settingsRequests++
        return true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArrayList(KEY_PENDING, ArrayList(pending))
        outState.putBoolean(KEY_FROM_SHARE, fromShare)
        outState.putInt(KEY_SESSION, session)
    }

    private fun takeShare(intent: Intent) {
        val uris = intent.sharedImageUris()
        if (uris.isNotEmpty()) openSave(uris.map { it.toString() }, fromShare = true)
    }

    /** A sticker pack opened from another app (Files, a chat): import it. True when there was one. */
    private fun takePack(intent: Intent): Boolean {
        val uri = intent.data?.takeIf { intent.action == Intent.ACTION_VIEW } ?: return false
        backups.import(uri.toString())
        return true
    }

    private fun sharePack(uri: String) {
        val send = Intent(Intent.ACTION_SEND)
            .setType(PACK_MIME)
            .putExtra(Intent.EXTRA_STREAM, uri.toUri())
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(send, null))
    }

    @Composable
    private fun Settings(
        container: AppContainer,
        onNavigate: (TopLevel) -> Unit,
        openSetup: () -> Unit,
        openLicenses: () -> Unit,
    ) {
        val vm: SettingsViewModel = viewModel(
            factory = viewModelFactory {
                initializer {
                    SettingsViewModel(
                        container.themeStore,
                        container.keyboardSettingsStore,
                        container.stickers.observeAll().map { it.size },
                        container::storageBytes,
                    )
                }
            },
        )
        val state by vm.state.collectAsState()
        SettingsScreen(
            state,
            SettingsActions(
                onTheme = vm::setTheme,
                onKeyboard = vm::setKeyboard,
                onKeyboardSetup = openSetup,
                onBackUp = { createBackup.launch(backupFileName()) },
                onShare = backups::chooseCollectionToShare,
                onImport = { openPack.launch(arrayOf("*/*")) },
                onLicenses = openLicenses,
            ),
            onNavigate = onNavigate,
            version = container.appVersion,
        )
    }

    /** Opens a new Save sheet for [uris]. Shares finish the activity on Done; picks stay in the app. */
    internal fun openSave(uris: List<String>, fromShare: Boolean) {
        session++
        pending = uris
        this.fromShare = fromShare
    }

    @Composable
    private fun Setup(container: AppContainer, addStickers: () -> Unit, leave: () -> Unit) {
        val vm: KeyboardSetupViewModel = viewModel(
            factory = viewModelFactory {
                initializer {
                    KeyboardSetupViewModel(
                        container.keyboardStatus,
                        container.stickers.observeAll().map { it.size },
                        container.setupStore,
                    )
                }
            },
        )
        KeyboardSetupScreen(
            vm,
            onAction = { action ->
                when (action) {
                    SetupAction.OPEN_SETTINGS -> container.keyboardStatus.openSettings()
                    SetupAction.PICK_KEYBOARD -> container.keyboardStatus.showPicker()
                    SetupAction.ADD_STICKERS -> addStickers()
                    SetupAction.DONE -> leave()
                }
            },
            onLeave = leave,
        )
    }

    private fun finishSave() {
        pending = emptyList()
        if (fromShare) finish()
    }

    companion object {
        /** Opens the app on its Settings screen (the keyboard toolbar's Settings button). */
        const val EXTRA_OPEN_SETTINGS = "app.kisscut.keys.OPEN_SETTINGS"

        private const val MAX_PICK = 50

        /** Packs have no registered type; other apps see a generic file. */
        private const val PACK_MIME = "application/octet-stream"

        private fun backupFileName(): String =
            "kisscut-backup-" + SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) +
                AndroidPackBackend.EXTENSION
        private const val KEY_PENDING = "pending_uris"
        private const val KEY_FROM_SHARE = "from_share"
        private const val KEY_SESSION = "save_session"
    }
}
