package com.example.stickers

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.stickers.save.AppSaveBackend
import com.example.stickers.save.SaveSheet
import com.example.stickers.save.SaveViewModel
import com.example.stickers.share.sharedImageUris
import com.example.stickers.ui.theme.StickerShapes
import com.example.stickers.ui.theme.StickerSizes
import com.example.stickers.ui.theme.StickerType
import com.example.stickers.ui.theme.StickersTheme

/**
 * Single activity. Also the share target for `ACTION_SEND` / `ACTION_SEND_MULTIPLE` with any image type:
 * a share opens the Save sheet, and Done returns to the app that shared.
 */
class MainActivity : ComponentActivity() {
    /** URIs waiting in the Save sheet; empty = no sheet. */
    private var pending by mutableStateOf<List<String>>(emptyList())
    private var fromShare = false

    private val pickImages = registerForActivityResult(PickMultipleVisualMedia(MAX_PICK)) { uris ->
        if (uris.isNotEmpty()) {
            pending = uris.map { it.toString() }
            fromShare = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            takeShare(intent)
        } else {
            pending = savedInstanceState.getStringArrayList(KEY_PENDING).orEmpty()
            fromShare = savedInstanceState.getBoolean(KEY_FROM_SHARE)
        }
        val container = (application as App).container
        setContent {
            StickersTheme {
                HomeScreen(onAddStickers = { pickImages.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) })
                val uris = pending
                if (uris.isNotEmpty()) {
                    val vm: SaveViewModel = viewModel(
                        key = "save:" + uris.joinToString("\n"),
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
    }

    private fun takeShare(intent: Intent) {
        val uris = intent.sharedImageUris()
        if (uris.isNotEmpty()) {
            pending = uris.map { it.toString() }
            fromShare = true
        }
    }

    private fun finishSave() {
        pending = emptyList()
        if (fromShare) finish()
    }

    private companion object {
        const val MAX_PICK = 50
        const val KEY_PENDING = "pending_uris"
        const val KEY_FROM_SHARE = "from_share"
    }
}

/** Placeholder home until the Library screen (M3): title, one line, and "Add stickers". */
@Composable
private fun HomeScreen(onAddStickers: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .safeDrawingPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(R.string.home_empty_body),
                style = StickerType.Body,
                color = StickersTheme.colors.muted,
            )
            Button(
                onClick = onAddStickers,
                shape = StickerShapes.Pill,
                colors = ButtonDefaults.buttonColors(
                    containerColor = StickersTheme.colors.lime,
                    contentColor = StickersTheme.colors.onLime,
                ),
                modifier = Modifier
                    .heightIn(min = StickerSizes.MinTouchTarget)
                    .testTag("add_stickers"),
            ) {
                Text(stringResource(R.string.home_add_stickers), style = StickerType.Button)
            }
        }
    }
}
