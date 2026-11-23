package app.kisscut.keys.backup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import app.kisscut.keys.R
import app.kisscut.keys.common.TargetSheet
import app.kisscut.keys.shared.core.pack.ImportSummary
import app.kisscut.keys.shared.core.pack.PackProblem
import app.kisscut.keys.ui.theme.StickerType

object BackupTags {
    const val WORKING = "backup:working"
    const val RESULT = "backup:result"
    const val FAILED = "backup:failed"
    const val CANCEL = "backup:cancel"
    const val OK = "backup:ok"
}

/** Every backup dialog, shown over any screen (a pack opened from another app lands here too). */
@Composable
fun BackupDialogs(vm: BackupViewModel) {
    val dialog by vm.dialog.collectAsState()
    when (val d = dialog) {
        null -> Unit
        is BackupDialog.PickCollection -> TargetSheet(
            title = stringResource(R.string.backup_share_pick),
            targets = d.targets,
            noneLabel = "",
            noneIcon = Icons.Outlined.Inbox,
            empty = stringResource(R.string.backup_share_empty),
            onPick = { id -> id?.let(vm::share) },
            onDismiss = vm::dismiss,
        )
        is BackupDialog.Working -> AlertDialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            title = { Text(stringResource(workingTitle(d.task))) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.testTag(BackupTags.WORKING),
                ) {
                    if (d.total > 0) {
                        LinearProgressIndicator(
                            progress = { d.done.toFloat() / d.total },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(stringResource(R.string.backup_progress, d.done, d.total), style = StickerType.Body)
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = vm::cancel, modifier = Modifier.testTag(BackupTags.CANCEL)) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
        is BackupDialog.BackedUp -> Result(
            title = stringResource(R.string.backup_backed_up_title),
            lines = listOf(pluralStringResource(R.plurals.backup_backed_up, d.stickers, d.stickers)),
            onOk = vm::dismiss,
        )
        is BackupDialog.Imported -> Result(
            title = stringResource(R.string.import_title),
            lines = importLines(d.summary),
            onOk = vm::dismiss,
        )
        is BackupDialog.Failed -> AlertDialog(
            onDismissRequest = vm::dismiss,
            title = { Text(stringResource(failedTitle(d.task))) },
            text = {
                Text(
                    stringResource(problemText(d.problem)),
                    style = StickerType.Body,
                    modifier = Modifier.testTag(BackupTags.FAILED),
                )
            },
            confirmButton = {
                TextButton(onClick = vm::dismiss, modifier = Modifier.testTag(BackupTags.OK)) {
                    Text(stringResource(R.string.backup_ok))
                }
            },
        )
    }
}

@Composable
private fun Result(title: String, lines: List<String>, onOk: () -> Unit) = AlertDialog(
    onDismissRequest = onOk,
    title = { Text(title) },
    text = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.testTag(BackupTags.RESULT)) {
            lines.forEach { Text(it, style = StickerType.Body) }
        }
    },
    confirmButton = {
        TextButton(onClick = onOk, modifier = Modifier.testTag(BackupTags.OK)) {
            Text(stringResource(R.string.backup_ok))
        }
    },
)

@Composable
private fun importLines(s: ImportSummary): List<String> = buildList {
    add(pluralStringResource(R.plurals.import_added, s.added, s.added))
    if (s.duplicates > 0) add(pluralStringResource(R.plurals.import_duplicates, s.duplicates, s.duplicates))
    if (s.rejectedTotal > 0) add(pluralStringResource(R.plurals.import_rejected, s.rejectedTotal, s.rejectedTotal))
    if (s.collectionsCreated > 0) {
        add(pluralStringResource(R.plurals.import_collections_created, s.collectionsCreated, s.collectionsCreated))
    }
    if (s.collectionsMerged > 0) {
        add(pluralStringResource(R.plurals.import_collections_merged, s.collectionsMerged, s.collectionsMerged))
    }
    if (s.collectionsFlattened > 0) {
        add(pluralStringResource(R.plurals.import_flattened, s.collectionsFlattened, s.collectionsFlattened))
    }
}

private fun workingTitle(task: BackupTask) = when (task) {
    BackupTask.BACK_UP -> R.string.backup_working_back_up
    BackupTask.SHARE -> R.string.backup_working_share
    BackupTask.IMPORT -> R.string.backup_working_import
}

private fun failedTitle(task: BackupTask) = when (task) {
    BackupTask.BACK_UP -> R.string.backup_failed_back_up
    BackupTask.SHARE -> R.string.backup_failed_share
    BackupTask.IMPORT -> R.string.backup_failed_import
}

private fun problemText(problem: PackProblem?) = when (problem) {
    PackProblem.NotAPack -> R.string.pack_problem_not_a_pack
    is PackProblem.TooNew -> R.string.pack_problem_too_new
    PackProblem.Malformed -> R.string.pack_problem_malformed
    is PackProblem.UnsafePath -> R.string.pack_problem_unsafe
    is PackProblem.OverLimit -> R.string.pack_problem_too_big
    PackProblem.NotEnoughSpace -> R.string.pack_problem_space
    null -> R.string.pack_problem_io
}
