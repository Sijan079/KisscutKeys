package app.kisscut.keys

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.kisscut.keys.common.LocalThumbnails
import app.kisscut.keys.nav.AppNav
import app.kisscut.keys.save.AppSaveBackend
import app.kisscut.keys.save.SaveSheet
import app.kisscut.keys.save.SaveViewModel
import app.kisscut.keys.setup.KeyboardSetupScreen
import app.kisscut.keys.setup.KeyboardSetupViewModel
import app.kisscut.keys.setup.SetupAction
import app.kisscut.keys.share.sharedImageUris
import kotlinx.coroutines.flow.map
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

    private val pickImages = registerForActivityResult(PickMultipleVisualMedia(MAX_PICK)) { uris ->
        if (uris.isNotEmpty()) openSave(uris.map { it.toString() }, fromShare = false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            takeShare(intent)
            launchedByShare = pending.isNotEmpty()
        } else {
            pending = savedInstanceState.getStringArrayList(KEY_PENDING).orEmpty()
            fromShare = savedInstanceState.getBoolean(KEY_FROM_SHARE)
            session = savedInstanceState.getInt(KEY_SESSION)
        }
        val container = (application as App).container
        setContent {
            StickersTheme {
                val addStickers = { pickImages.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) }
                // Keyboard setup opens first until it is finished or skipped, except when a share started us.
                val dismissed by container.setupStore.dismissed.collectAsState(initial = null)
                var startAtSetup by rememberSaveable { mutableStateOf<Boolean?>(null) }
                if (startAtSetup == null) dismissed?.let { startAtSetup = !it && !launchedByShare }
                val start = startAtSetup
                if (start == null) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
                } else {
                    CompositionLocalProvider(LocalThumbnails provides container::thumbnailOf) {
                        AppNav(
                            backend = container.libraryBackend,
                            onAdd = addStickers,
                            startAtSetup = start,
                            setupScreen = { leave -> Setup(container, addStickers, leave) },
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
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        takeShare(intent)
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

    private companion object {
        const val MAX_PICK = 50
        const val KEY_PENDING = "pending_uris"
        const val KEY_FROM_SHARE = "from_share"
        const val KEY_SESSION = "save_session"
    }
}
