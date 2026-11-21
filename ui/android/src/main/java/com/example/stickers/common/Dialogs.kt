package com.example.stickers.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.stickers.R
import com.example.stickers.shared.core.DeleteMode
import com.example.stickers.ui.theme.StickerShapes
import com.example.stickers.ui.theme.StickerSizes
import com.example.stickers.ui.theme.StickerType
import com.example.stickers.ui.theme.StickersTheme

object DialogTags {
    const val NAME_FIELD = "dialog:name"
    const val CONFIRM = "dialog:confirm"
    const val DELETE_ONLY = "dialog:delete-only"
    const val DELETE_EVERYTHING = "dialog:delete-everything"
    fun target(id: Long?) = "target:${id ?: "none"}"
    fun menu(which: String) = "menu:$which"
}

@Composable
fun StickerDialogs(selection: StickerSelection, dialog: StickerDialog?) {
    when (dialog) {
        null -> Unit
        is StickerDialog.Move -> TargetSheet(
            title = pluralStringResource(R.plurals.move_stickers_title, dialog.count, dialog.count),
            targets = dialog.targets,
            noneLabel = stringResource(R.string.move_unsorted_target),
            noneIcon = Icons.Outlined.Inbox,
            empty = null,
            onPick = selection::moveTo,
            onDismiss = selection::dismissDialog,
        )
        is StickerDialog.Delete -> AlertDialog(
            onDismissRequest = selection::dismissDialog,
            title = { Text(pluralStringResource(R.plurals.delete_stickers_title, dialog.count, dialog.count)) },
            text = { Text(stringResource(R.string.delete_stickers_body), style = StickerType.Body) },
            confirmButton = {
                TextButton(onClick = selection::delete, modifier = Modifier.testTag(DialogTags.CONFIRM)) {
                    Text(stringResource(R.string.delete_confirm), color = StickersTheme.colors.danger)
                }
            },
            dismissButton = {
                TextButton(onClick = selection::dismissDialog) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
fun CollectionDialogs(editor: CollectionEditor, dialog: CollectionDialog?) {
    when (dialog) {
        null -> Unit
        is CollectionDialog.Menu -> MenuSheet(dialog, editor)
        is CollectionDialog.Name -> NameDialog(dialog, editor)
        is CollectionDialog.Move -> TargetSheet(
            title = stringResource(R.string.move_collection_title, dialog.name),
            targets = dialog.targets,
            noneLabel = stringResource(R.string.move_top_level),
            noneIcon = Icons.Outlined.Folder,
            empty = stringResource(R.string.move_nowhere),
            onPick = editor::confirmMove,
            onDismiss = editor::dismiss,
        )
        is CollectionDialog.Delete -> DeleteCollectionDialog(dialog, editor)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MenuSheet(dialog: CollectionDialog.Menu, editor: CollectionEditor) {
    ModalBottomSheet(
        onDismissRequest = editor::dismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = StickerShapes.Sheet,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(
                dialog.name,
                style = StickerType.Heading,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            if (editor.canHaveChildren(dialog.id)) {
                SheetRow(Icons.Outlined.CreateNewFolder, stringResource(R.string.menu_new_inside), "new") {
                    editor.openNew(dialog.id)
                }
            }
            SheetRow(
                Icons.Outlined.Edit,
                stringResource(R.string.menu_rename),
                "rename",
            ) { editor.openRename(dialog.id) }
            SheetRow(
                Icons.Outlined.DriveFileMove,
                stringResource(R.string.menu_move),
                "move",
            ) { editor.openMove(dialog.id) }
            SheetRow(
                Icons.Outlined.Delete,
                stringResource(R.string.menu_delete),
                "delete",
                tint = StickersTheme.colors.danger,
            ) { editor.openDelete(dialog.id) }
        }
    }
}

@Composable
private fun SheetRow(icon: ImageVector, label: String, tag: String, tint: Color? = null, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp)
            .testTag(DialogTags.menu(tag)),
    ) {
        Icon(icon, contentDescription = null, tint = tint ?: StickersTheme.colors.violetIcon)
        Text(label, style = StickerType.Body, color = tint ?: MaterialTheme.colorScheme.onSurface)
    }
}

/** A picker of [targets] (depth-indented); a null id is shown as [noneLabel]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TargetSheet(
    title: String,
    targets: List<MoveTarget>,
    noneLabel: String,
    noneIcon: ImageVector,
    empty: String?,
    onPick: (Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = StickerShapes.Sheet,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(
                title,
                style = StickerType.Heading,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            if (targets.isEmpty() && empty != null) {
                Text(
                    empty,
                    style = StickerType.Body,
                    color = StickersTheme.colors.muted,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                )
            }
            Column(Modifier.verticalScroll(rememberScrollState())) {
                for (t in targets) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = StickerSizes.MinTouchTarget + 4.dp)
                            .clickable(role = Role.Button) { onPick(t.collectionId) }
                            .padding(start = 24.dp + 22.dp * (t.depth - 1), end = 24.dp)
                            .testTag(DialogTags.target(t.collectionId)),
                    ) {
                        Icon(
                            if (t.collectionId == null) noneIcon else Icons.Outlined.Folder,
                            contentDescription = null,
                            tint = StickersTheme.colors.muted,
                            modifier = Modifier.size(22.dp),
                        )
                        Text(t.name ?: noneLabel, style = StickerType.Body, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}

@Composable
private fun NameDialog(dialog: CollectionDialog.Name, editor: CollectionEditor) {
    val title = when {
        dialog.renameId != null -> stringResource(R.string.rename_title)
        dialog.parentName != null -> stringResource(R.string.save_new_collection_in, dialog.parentName)
        else -> stringResource(R.string.new_collection_title)
    }
    AlertDialog(
        onDismissRequest = editor::dismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = dialog.text,
                    onValueChange = editor::setName,
                    label = { Text(stringResource(R.string.new_collection_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag(DialogTags.NAME_FIELD),
                )
                if (dialog.refused) {
                    Text(
                        stringResource(R.string.name_refused),
                        style = StickerType.Label,
                        color = StickersTheme.colors.danger,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = editor::confirmName,
                enabled = dialog.canConfirm,
                modifier = Modifier.testTag(DialogTags.CONFIRM),
            ) {
                Text(
                    stringResource(
                        if (dialog.renameId != null) R.string.rename_save else R.string.new_collection_create,
                    ),
                )
            }
        },
        dismissButton = { TextButton(onClick = editor::dismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun DeleteCollectionDialog(dialog: CollectionDialog.Delete, editor: CollectionEditor) {
    AlertDialog(
        onDismissRequest = editor::dismiss,
        title = { Text(stringResource(R.string.delete_collection_title, dialog.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DeleteChoice(
                    title = stringResource(R.string.delete_only),
                    body = stringResource(R.string.delete_only_body),
                    color = MaterialTheme.colorScheme.onSurface,
                    tag = DialogTags.DELETE_ONLY,
                ) { editor.confirmDelete(DeleteMode.COLLECTION_ONLY) }
                DeleteChoice(
                    title = stringResource(R.string.delete_everything),
                    body = pluralStringResource(R.plurals.delete_everything_body, dialog.totalCount, dialog.totalCount),
                    color = StickersTheme.colors.danger,
                    tag = DialogTags.DELETE_EVERYTHING,
                ) { editor.confirmDelete(DeleteMode.EVERYTHING) }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = editor::dismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun DeleteChoice(title: String, body: String, color: Color, tag: String, onClick: () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = StickerSizes.MinTouchTarget)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp)
            .testTag(tag),
    ) {
        Text(title, style = StickerType.Heading.copy(fontSize = StickerType.Body.fontSize), color = color)
        Text(
            body,
            style = StickerType.Body.copy(fontSize = StickerType.Label.fontSize),
            color = StickersTheme.colors.muted,
        )
    }
}
