package com.example.stickers

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.stickers.common.LocalThumbnails
import com.example.stickers.nav.AppNav
import com.example.stickers.save.AppSaveBackend
import com.example.stickers.save.SaveSheet
import com.example.stickers.save.SaveViewModel
import com.example.stickers.share.sharedImageUris
import com.example.stickers.ui.theme.StickersTheme

/**
 * Single activity. Also the share target for `ACTION_SEND` / `ACTION_SEND_MULTIPLE` with any image type:
 * a share opens the Save sheet, and Done returns to the app that shared.
 */
class MainActivity : ComponentActivity() {
    /** URIs waiting in the Save sheet; empty = no sheet. */
    private var pending by mutableStateOf<List<String>>(emptyList())
    private var fromShare = false

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
        } else {
            pending = savedInstanceState.getStringArrayList(KEY_PENDING).orEmpty()
            fromShare = savedInstanceState.getBoolean(KEY_FROM_SHARE)
            session = savedInstanceState.getInt(KEY_SESSION)
        }
        val container = (application as App).container
        setContent {
            StickersTheme {
                CompositionLocalProvider(LocalThumbnails provides container::thumbnailOf) {
                    AppNav(
                        backend = container.libraryBackend,
                        onAdd = { pickImages.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) },
                    )
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
