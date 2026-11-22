package com.example.stickers.save

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.example.stickers.R
import com.example.stickers.shared.core.CollectionTreeRow
import com.example.stickers.shared.core.ImageType
import com.example.stickers.ui.theme.StickerShapes
import com.example.stickers.ui.theme.StickerSizes
import com.example.stickers.ui.theme.StickerType
import com.example.stickers.ui.theme.StickersTheme

/** The Save bottom sheet (UI_SPEC screen 6). [onFinished] runs on Done or when the sheet is dismissed. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveSheet(viewModel: SaveViewModel, onFinished: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onFinished,
        sheetState = sheetState,
        shape = StickerShapes.Sheet,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        SaveSheetContent(
            state = state,
            onToggle = viewModel::toggleExpanded,
            onSelect = viewModel::select,
            onNewCollection = viewModel::openNewCollection,
            onSave = viewModel::save,
            onDone = onFinished,
        )
    }
    state.newCollection?.let { draft ->
        NewCollectionDialog(
            draft = draft,
            onNameChange = viewModel::setNewCollectionName,
            onConfirm = viewModel::confirmNewCollection,
            onDismiss = viewModel::dismissNewCollection,
        )
    }
}

@Composable
fun SaveSheetContent(
    state: SaveUiState,
    onToggle: (Long) -> Unit,
    onSelect: (Long?) -> Unit,
    onNewCollection: () -> Unit,
    onSave: () -> Unit,
    onDone: () -> Unit,
) {
    // The content scrolls; the button stays pinned below it, so it is visible on short screens too.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, bottom = 24.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when (val phase = state.phase) {
            is SavePhase.Done -> DoneContent(phase.summary, onDone)
            else -> {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = pluralStringResource(R.plurals.save_title, state.items.size, state.items.size),
                        style = SheetTitle,
                    )
                    Previews(state.items)
                    SaveToHeader(state.selectedPath)
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        UnsortedRow(selected = state.selectedId == null, onClick = { onSelect(null) })
                        state.tree.forEach { row ->
                            CollectionRow(
                                row = row,
                                selected = row.collection.id == state.selectedId,
                                onClick = { onSelect(row.collection.id) },
                                onToggle = { onToggle(row.collection.id) },
                            )
                        }
                        NewCollectionRow(state.newCollectionParentName, onNewCollection)
                    }
                }
                val saving = state.phase as? SavePhase.Saving
                PrimaryButton(
                    text = if (saving?.percent != null) {
                        stringResource(R.string.save_progress_percent, saving.done + 1, saving.total, saving.percent)
                    } else if (saving != null) {
                        stringResource(R.string.save_progress, saving.done, saving.total)
                    } else {
                        stringResource(
                            R.string.save_button_to,
                            state.selectedPath.lastOrNull() ?: stringResource(R.string.save_unsorted),
                        )
                    },
                    enabled = saving == null,
                    onClick = onSave,
                    modifier = Modifier.testTag(SaveTags.SAVE_BUTTON),
                )
            }
        }
    }
}

// --- preview ---------------------------------------------------------------------------------

@Composable
private fun Previews(items: List<SaveItem>) {
    if (items.size == 1) {
        val item = items.single()
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            PreviewTile(item)
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(typeLabel(item.preview), style = StickerType.Heading)
                detailLine(item.preview)?.let {
                    Text(it, style = StickerType.Body.copy(fontSize = 14.sp), color = StickersTheme.colors.muted)
                }
            }
        }
    } else {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(0.dp)) {
            items(items, key = { it.uri }) { PreviewTile(it) }
        }
        Text(
            stringResource(R.string.save_count_images, items.size),
            style = StickerType.Body.copy(fontSize = 14.sp),
            color = StickersTheme.colors.muted,
        )
    }
}

@Composable
private fun PreviewTile(item: SaveItem) {
    val animated = (item.preview as? ItemPreview.Ready)?.details?.info?.isAnimated == true
    Box(
        modifier = Modifier
            .size(88.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(StickersTheme.colors.raised),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = item.uri,
            contentDescription = stringResource(R.string.save_cd_preview),
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(72.dp),
        )
        if (animated) {
            Text(
                text = stringResource(R.string.save_badge_gif),
                style = StickerType.Label.copy(fontSize = 10.sp, fontWeight = FontWeight(800)),
                // Same badge colors as the library tiles: dark in both themes (design boards).
                color = StickersTheme.colors.badgeText,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .testTag(SaveTags.GIF_BADGE)
                    .clip(RoundedCornerShape(8.dp))
                    .background(StickersTheme.colors.badgeFill)
                    .padding(horizontal = 7.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun typeLabel(preview: ItemPreview): String = when (preview) {
    ItemPreview.Loading -> stringResource(R.string.save_item_loading)
    ItemPreview.Unsupported -> stringResource(R.string.save_item_unsupported)
    is ItemPreview.Ready -> {
        val info = preview.details.info
        when (info.type) {
            ImageType.PNG -> stringResource(R.string.save_type_png)
            ImageType.JPEG -> stringResource(R.string.save_type_jpeg)
            ImageType.WEBP -> stringResource(
                if (info.isAnimated) R.string.save_type_webp_animated else R.string.save_type_webp,
            )
            ImageType.GIF -> stringResource(
                if (info.isAnimated) R.string.save_type_gif_animated else R.string.save_type_gif,
            )
        }
    }
}

@Composable
private fun detailLine(preview: ItemPreview): String? {
    val details = (preview as? ItemPreview.Ready)?.details ?: return null
    val size = Formatter.formatShortFileSize(LocalContext.current, details.sizeBytes)
    return stringResource(R.string.save_item_details, size, details.info.width, details.info.height)
}

// --- picker ----------------------------------------------------------------------------------

@Composable
private fun SaveToHeader(path: List<String>) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.save_to),
            style = StickerType.Label.copy(fontSize = 13.sp, fontWeight = FontWeight(800)),
            color = StickersTheme.colors.muted,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = if (path.isEmpty()) {
                stringResource(R.string.save_unsorted)
            } else {
                path.joinToString(stringResource(R.string.save_path_separator))
            },
            style = StickerType.Label.copy(fontSize = 13.sp),
            color = StickersTheme.colors.accentText,
            modifier = Modifier.testTag(SaveTags.PATH),
        )
    }
}

@Composable
private fun UnsortedRow(selected: Boolean, onClick: () -> Unit) {
    PickerRow(
        label = stringResource(R.string.save_unsorted),
        icon = Icons.Outlined.Inbox,
        depth = 1,
        selected = selected,
        bold = false,
        onClick = onClick,
    )
}

@Composable
private fun CollectionRow(row: CollectionTreeRow, selected: Boolean, onClick: () -> Unit, onToggle: () -> Unit) {
    PickerRow(
        label = row.collection.name,
        icon = Icons.Outlined.Folder,
        depth = row.depth,
        selected = selected,
        bold = row.hasChildren,
        onClick = onClick,
    ) {
        if (row.hasChildren) {
            val name = row.collection.name
            IconButton(onClick = onToggle, modifier = Modifier.size(StickerSizes.MinTouchTarget)) {
                Icon(
                    imageVector = if (row.isExpanded) Icons.Outlined.ExpandMore else Icons.Outlined.ChevronRight,
                    contentDescription = stringResource(
                        if (row.isExpanded) R.string.save_cd_collapse else R.string.save_cd_expand,
                        name,
                    ),
                    tint = StickersTheme.colors.muted,
                )
            }
        }
    }
}

@Composable
private fun PickerRow(
    label: String,
    icon: ImageVector,
    depth: Int,
    selected: Boolean,
    bold: Boolean,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit = {},
) {
    val colors = StickersTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = StickerSizes.MinTouchTarget)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) colors.raised else MaterialTheme.colorScheme.surface)
            .semantics { this.selected = selected }
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(start = (14 + 28 * (depth - 1)).dp, end = 4.dp),
    ) {
        Icon(icon, contentDescription = null, tint = if (selected) colors.accentText else colors.muted)
        Text(
            text = label,
            style = StickerType.Body.copy(
                fontWeight = FontWeight(
                    when {
                        selected -> 800
                        bold -> 700
                        else -> 600
                    },
                ),
            ),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 12.dp),
        )
        trailing()
        if (selected) {
            val selectedDescription = stringResource(R.string.save_cd_selected)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(colors.lime)
                    .semantics { contentDescription = selectedDescription },
            ) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    tint = colors.onLime,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

@Composable
private fun NewCollectionRow(parentName: String?, onClick: () -> Unit) {
    val colors = StickersTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = StickerSizes.MinTouchTarget)
            .clip(RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .testTag(SaveTags.NEW_COLLECTION)
            .padding(start = 42.dp, end = 12.dp),
    ) {
        Icon(Icons.Outlined.Add, contentDescription = null, tint = colors.accentText)
        Text(
            text = parentName?.let { stringResource(R.string.save_new_collection_in, it) }
                ?: stringResource(R.string.save_new_collection),
            style = StickerType.Body.copy(fontWeight = FontWeight(700)),
            color = colors.muted,
            modifier = Modifier.padding(vertical = 12.dp),
        )
    }
}

// --- done and dialog -------------------------------------------------------------------------

@Composable
private fun DoneContent(summary: SaveSummary, onDone: () -> Unit) {
    Text(
        text = stringResource(if (summary.added > 0) R.string.save_done_title else R.string.save_done_nothing_new),
        style = SheetTitle,
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.testTag(SaveTags.SUMMARY)) {
        val lines = listOf(
            R.plurals.save_done_added to summary.added,
            R.plurals.save_done_duplicates to summary.duplicates,
            R.plurals.save_done_rejected to summary.rejected,
        ).filter { it.second > 0 }
        lines.forEach { (res, n) ->
            Text(pluralStringResource(res, n, n), style = StickerType.Body, color = StickersTheme.colors.muted)
        }
    }
    PrimaryButton(
        text = stringResource(R.string.save_done_button),
        enabled = true,
        onClick = onDone,
        modifier = Modifier.testTag(SaveTags.DONE_BUTTON),
    )
}

@Composable
private fun NewCollectionDialog(
    draft: NewCollectionDraft,
    onNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_collection_title), style = StickerType.Heading) },
        text = {
            OutlinedTextField(
                value = draft.name,
                onValueChange = onNameChange,
                label = { Text(stringResource(R.string.new_collection_name)) },
                singleLine = true,
                modifier = Modifier.testTag(SaveTags.NEW_COLLECTION_NAME),
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = draft.canConfirm) {
                Text(stringResource(R.string.new_collection_create), color = StickersTheme.colors.accentText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.new_collection_cancel), color = StickersTheme.colors.muted)
            }
        },
    )
}

@Composable
private fun PrimaryButton(text: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = StickersTheme.colors
    val configuration = LocalConfiguration.current
    val height = saveButtonHeight(configuration.screenWidthDp, configuration.screenHeightDp)
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = StickerShapes.Pill,
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.lime,
            contentColor = colors.onLime,
            disabledContainerColor = colors.lime.copy(alpha = 0.6f),
            disabledContentColor = colors.onLime,
        ),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = height),
    ) {
        Text(text, style = StickerType.Button.copy(fontSize = 17.sp))
    }
}

/**
 * Save button height by screen size. Short screens (small phones, landscape) get the 48dp touch
 * minimum so the sheet keeps room for the picker; regular phones get the design's 56dp; tablets 64dp.
 * It is a minimum: large font scales still grow the button.
 */
internal fun saveButtonHeight(widthDp: Int, heightDp: Int): Dp = when {
    heightDp < SHORT_SCREEN_DP -> StickerSizes.MinTouchTarget
    widthDp >= TABLET_WIDTH_DP -> 64.dp
    else -> 56.dp
}

private const val SHORT_SCREEN_DP = 700
private const val TABLET_WIDTH_DP = 600

private val SheetTitle = StickerType.Title.copy(fontSize = 26.sp, letterSpacing = (-0.5).sp)

object SaveTags {
    const val SAVE_BUTTON = "save_button"
    const val PATH = "save_path"
    const val NEW_COLLECTION = "save_new_collection"
    const val NEW_COLLECTION_NAME = "save_new_collection_name"
    const val SUMMARY = "save_summary"
    const val DONE_BUTTON = "save_done_button"
    const val GIF_BADGE = "save_gif_badge"
}
