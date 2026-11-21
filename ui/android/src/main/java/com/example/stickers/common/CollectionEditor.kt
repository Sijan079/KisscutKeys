package com.example.stickers.common

import com.example.stickers.shared.core.CollectionRepository
import com.example.stickers.shared.core.CollectionRuleException
import com.example.stickers.shared.core.CollectionSummary
import com.example.stickers.shared.core.DeleteMode
import com.example.stickers.shared.core.canMoveCollection
import com.example.stickers.shared.db.StickerCollection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface CollectionDialog {
    /** Long-press menu: rename, move, delete. */
    data class Menu(val id: Long, val name: String) : CollectionDialog

    /** New collection ([renameId] null) or rename. [parentName] null = top level. */
    data class Name(
        val text: String,
        val renameId: Long? = null,
        val parentId: Long? = null,
        val parentName: String? = null,
        val refused: Boolean = false,
    ) : CollectionDialog {
        val canConfirm: Boolean get() = text.isNotBlank()
    }

    /** Targets include "top level" (null id) when allowed; only moves the rules accept are listed. */
    data class Move(val id: Long, val name: String, val targets: List<MoveTarget>) : CollectionDialog

    data class Delete(val id: Long, val name: String, val totalCount: Int, val childCount: Int) : CollectionDialog
}

/**
 * Collection editing shared by the Collections list and Inside a collection. Rules live in
 * CollectionRepository; this only offers choices that pass them and keeps dialogs open when refused.
 */
class CollectionEditor(
    private val scope: CoroutineScope,
    private val backend: LibraryBackend,
    private val onCreated: (StickerCollection) -> Unit = {},
    private val onDeleted: (Long) -> Unit = {},
) {
    private val _dialog = MutableStateFlow<CollectionDialog?>(null)
    val dialog: StateFlow<CollectionDialog?> = _dialog.asStateFlow()

    private var all: List<StickerCollection> = emptyList()
    private var summaries: Map<Long, CollectionSummary> = emptyMap()

    init {
        scope.launch { backend.observeCollections().collect { all = it } }
        scope.launch { backend.observeSummaries().collect { summaries = it } }
    }

    private fun byId(id: Long) = all.firstOrNull { it.id == id }

    fun openMenu(id: Long) {
        val c = byId(id) ?: return
        _dialog.value = CollectionDialog.Menu(c.id, c.name)
    }

    /** New collection inside [parentId] (null = top level); refused at the depth cap by the repository. */
    fun openNew(parentId: Long?) {
        _dialog.value = CollectionDialog.Name(
            text = "",
            parentId = parentId,
            parentName = parentId?.let { byId(it)?.name },
        )
    }

    fun canHaveChildren(id: Long): Boolean = (summaries[id]?.depth ?: Int.MAX_VALUE) < CollectionRepository.MAX_DEPTH

    fun openRename(id: Long) {
        val c = byId(id) ?: return
        _dialog.value = CollectionDialog.Name(text = c.name, renameId = c.id)
    }

    fun openMove(id: Long) {
        val c = byId(id) ?: return
        val top = if (canMoveCollection(all, id, null)) listOf(MoveTarget(null, null, 1)) else emptyList()
        val inside = allTargets(all).filter { it.collectionId != null && canMoveCollection(all, id, it.collectionId) }
        _dialog.value = CollectionDialog.Move(c.id, c.name, top + inside)
    }

    fun openDelete(id: Long) {
        val c = byId(id) ?: return
        val s = summaries[id]
        _dialog.value = CollectionDialog.Delete(c.id, c.name, s?.totalCount ?: 0, s?.childCount ?: 0)
    }

    fun setName(text: String) = _dialog.update { d -> if (d is CollectionDialog.Name) d.copy(text = text) else d }

    fun dismiss() {
        _dialog.value = null
    }

    fun confirmName() {
        val d = _dialog.value as? CollectionDialog.Name ?: return
        if (!d.canConfirm) return
        scope.launch {
            try {
                if (d.renameId != null) {
                    backend.renameCollection(d.renameId, d.text)
                } else {
                    onCreated(backend.createCollection(d.text, d.parentId))
                }
                _dialog.value = null
            } catch (e: CollectionRuleException) {
                _dialog.update { if (it is CollectionDialog.Name) it.copy(refused = true) else it }
            }
        }
    }

    fun confirmMove(newParentId: Long?) {
        val d = _dialog.value as? CollectionDialog.Move ?: return
        _dialog.value = null
        scope.launch {
            try {
                backend.moveCollection(d.id, newParentId)
            } catch (e: CollectionRuleException) {
                // Only allowed targets are offered; a refusal here means the tree changed meanwhile.
            }
        }
    }

    fun confirmDelete(mode: DeleteMode) {
        val d = _dialog.value as? CollectionDialog.Delete ?: return
        _dialog.value = null
        scope.launch {
            try {
                backend.deleteCollection(d.id, mode)
                onDeleted(d.id)
            } catch (e: CollectionRuleException) {
                // Already gone.
            }
        }
    }
}
