package app.kisscut.keys.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kisscut.keys.common.MoveTarget
import app.kisscut.keys.common.allTargets
import app.kisscut.keys.settings.ThemeMode
import app.kisscut.keys.settings.ThemeStore
import app.kisscut.keys.shared.core.pack.ImportSummary
import app.kisscut.keys.shared.core.pack.PackException
import app.kisscut.keys.shared.core.pack.PackProblem
import app.kisscut.keys.shared.core.pack.PackSettings
import app.kisscut.keys.shared.db.StickerCollection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

enum class BackupTask { BACK_UP, SHARE, IMPORT }

/** What the backup dialogs show; null means none. */
sealed interface BackupDialog {
    /** Pick the collection to share. */
    data class PickCollection(val targets: List<MoveTarget>) : BackupDialog

    /** Running; [total] is 0 until known. Cancel stops it and keeps nothing. */
    data class Working(val task: BackupTask, val done: Int = 0, val total: Int = 0) : BackupDialog

    data class BackedUp(val stickers: Int) : BackupDialog

    data class Imported(val summary: ImportSummary) : BackupDialog

    /** [problem] is null for an unexpected error (file unreadable, disk full). */
    data class Failed(val task: BackupTask, val problem: PackProblem?) : BackupDialog
}

/**
 * Back up, share and import (Settings, docs/IMPORT_EXPORT.md). Activity-scoped, so a pack opened from
 * another app uses the same dialogs. A restored backup applies its theme.
 */
class BackupViewModel(
    private val backend: PackBackend,
    private val themes: ThemeStore,
    private val collections: Flow<List<StickerCollection>>,
) : ViewModel() {
    private val _dialog = MutableStateFlow<BackupDialog?>(null)
    val dialog: StateFlow<BackupDialog?> = _dialog.asStateFlow()

    private val shares = Channel<String>(Channel.BUFFERED)

    /** Content uris of packs ready for the share sheet. */
    val shareRequests: Flow<String> = shares.receiveAsFlow()

    private var job: Job? = null

    /** [uri] is the document the user created for the backup. */
    fun backUp(uri: String) = run(BackupTask.BACK_UP) { progress ->
        val theme = themes.theme.first()
        val result = backend.backUp(uri, PackSettings(theme = theme.key), progress)
        BackupDialog.BackedUp(result.stickers)
    }

    fun chooseCollectionToShare() {
        viewModelScope.launch {
            _dialog.value = BackupDialog.PickCollection(allTargets(collections.first()))
        }
    }

    fun share(collectionId: Long) {
        val name = (_dialog.value as? BackupDialog.PickCollection)
            ?.targets?.firstOrNull { it.collectionId == collectionId }?.name.orEmpty()
        run(BackupTask.SHARE) { progress ->
            shares.send(backend.exportForShare(collectionId, name, progress))
            null
        }
    }

    fun import(uri: String) = run(BackupTask.IMPORT) { progress ->
        val summary = backend.import(uri, progress)
        summary.settings?.theme?.let { themes.setTheme(ThemeMode.of(it)) }
        BackupDialog.Imported(summary)
    }

    /** Stops the running task; it removes what it wrote. */
    fun cancel() {
        job?.cancel()
    }

    fun dismiss() {
        if (_dialog.value !is BackupDialog.Working) _dialog.value = null
    }

    private fun run(task: BackupTask, block: suspend (Progress) -> BackupDialog?) {
        if (job?.isActive == true) return
        _dialog.value = BackupDialog.Working(task)
        job = viewModelScope.launch {
            _dialog.value = try {
                block { done, total -> _dialog.value = BackupDialog.Working(task, done, total) }
            } catch (e: CancellationException) {
                _dialog.value = null
                throw e
            } catch (e: PackException) {
                BackupDialog.Failed(task, e.problem)
            } catch (e: Exception) {
                BackupDialog.Failed(task, null)
            }
        }
    }
}
