package app.kisscut.keys.common

import app.kisscut.keys.shared.core.buildCollectionTree
import app.kisscut.keys.shared.core.favoriteActionValue
import app.kisscut.keys.shared.db.Sticker
import app.kisscut.keys.shared.db.StickerCollection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A place stickers or a collection can move to. [collectionId] null = Unsorted (stickers) or top level. */
data class MoveTarget(val collectionId: Long?, val name: String?, val depth: Int)

/** Select mode over a sticker grid. Stays on with nothing selected until closed. */
data class SelectionUi(val active: Boolean = false, val selected: Set<Long> = emptySet()) {
    val count: Int get() = selected.size
}

sealed interface StickerDialog {
    data class Move(val count: Int, val targets: List<MoveTarget>) : StickerDialog
    data class Delete(val count: Int) : StickerDialog
}

/** All collections, fully expanded, depth first (for move pickers). */
fun allTargets(all: List<StickerCollection>): List<MoveTarget> =
    buildCollectionTree(
        all,
        all.map { it.id }.toSet(),
    ).map { MoveTarget(it.collection.id, it.collection.name, it.depth) }

/**
 * Select mode shared by the Library and Inside-a-collection screens: long-press starts it, taps toggle,
 * and the action bar moves, favorites or deletes. The owner keeps [visible] and [collections] current.
 */
class StickerSelection(private val scope: CoroutineScope, private val backend: LibraryBackend) {
    private val _state = MutableStateFlow(SelectionUi())
    val state: StateFlow<SelectionUi> = _state.asStateFlow()
    private val _dialog = MutableStateFlow<StickerDialog?>(null)
    val dialog: StateFlow<StickerDialog?> = _dialog.asStateFlow()

    var collections: List<StickerCollection> = emptyList()
    private var visible: List<Sticker> = emptyList()

    /** Call when the shown stickers change; drops selected ones that are gone. */
    fun onVisible(list: List<Sticker>) {
        visible = list
        val ids = list.map { it.id }.toSet()
        _state.update { it.copy(selected = it.selected intersect ids) }
    }

    fun start(firstId: Long? = null) = _state.update { SelectionUi(active = true, selected = setOfNotNull(firstId)) }

    fun toggle(id: Long) = _state.update { s ->
        if (!s.active) s else s.copy(selected = if (id in s.selected) s.selected - id else s.selected + id)
    }

    fun selectAll() = _state.update { it.copy(active = true, selected = visible.map { s -> s.id }.toSet()) }

    fun exit() {
        _state.value = SelectionUi()
        _dialog.value = null
    }

    fun openMove() {
        val n = _state.value.count
        if (n == 0) return
        _dialog.value = StickerDialog.Move(n, listOf(MoveTarget(null, null, 1)) + allTargets(collections))
    }

    fun openDelete() {
        val n = _state.value.count
        if (n > 0) _dialog.value = StickerDialog.Delete(n)
    }

    fun dismissDialog() {
        _dialog.value = null
    }

    fun moveTo(collectionId: Long?) = act { ids -> backend.moveStickers(ids, collectionId) }

    /** Favorites all, or unfavorites all when every selected one is already a favorite. */
    fun favorite() {
        val chosen = visible.filter { it.id in _state.value.selected }
        val value = favoriteActionValue(chosen)
        act { ids -> backend.setFavorite(ids, value) }
    }

    fun delete() = act { ids -> backend.deleteStickers(ids) }

    private fun act(block: suspend (List<Long>) -> Unit) {
        val ids = _state.value.selected.toList()
        if (ids.isEmpty()) return
        exit()
        scope.launch { block(ids) }
    }
}
