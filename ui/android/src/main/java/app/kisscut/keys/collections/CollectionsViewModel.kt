package app.kisscut.keys.collections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kisscut.keys.common.CollectionDialog
import app.kisscut.keys.common.CollectionEditor
import app.kisscut.keys.common.LibraryBackend
import app.kisscut.keys.shared.core.CollectionSummary
import app.kisscut.keys.shared.core.buildCollectionTree
import app.kisscut.keys.shared.core.collectionAncestors
import app.kisscut.keys.shared.db.StickerCollection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class CollectionRowUi(val summary: CollectionSummary, val hasChildren: Boolean, val expanded: Boolean) {
    val id: Long get() = summary.collection.id
}

data class CollectionsUiState(
    val loaded: Boolean = false,
    val rows: List<CollectionRowUi> = emptyList(),
    val favoritesCount: Int = 0,
    val unsortedCount: Int = 0,
    val dialog: CollectionDialog? = null,
)

/** Collections list: Favorites, the nested tree with expand/collapse and totals, Unsorted, New. */
class CollectionsViewModel(backend: LibraryBackend) : ViewModel() {
    private val expanded = MutableStateFlow<Set<Long>>(emptySet())
    val editor = CollectionEditor(
        viewModelScope,
        backend,
        onCreated = { created -> created.parentId?.let { expandTo(it) } },
    )
    private var latestAll = emptyList<StickerCollection>()

    private val counts = combine(
        backend.observeFavorites().map { it.size },
        backend.observeUnsorted().map { it.size },
    ) { f, u -> f to u }

    val state: StateFlow<CollectionsUiState> = combine(
        backend.observeCollections(),
        backend.observeSummaries(),
        expanded,
        counts,
        editor.dialog,
    ) { all, summaries, open, (favorites, unsorted), dialog ->
        latestAll = all
        val rows = buildCollectionTree(all, open).mapNotNull { row ->
            summaries[row.collection.id]?.let { CollectionRowUi(it, row.hasChildren, row.isExpanded) }
        }
        CollectionsUiState(true, rows, favorites, unsorted, dialog)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, CollectionsUiState())

    fun toggleExpanded(id: Long) = expanded.update { if (id in it) it - id else it + id }

    /** Opens [parentId] and its ancestors so a new collection inside it is visible. */
    private fun expandTo(parentId: Long) = expanded.update { it + collectionAncestors(latestAll, parentId) + parentId }
}
