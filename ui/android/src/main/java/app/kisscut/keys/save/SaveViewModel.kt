package app.kisscut.keys.save

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kisscut.keys.shared.core.CollectionRepository
import app.kisscut.keys.shared.core.CollectionRuleException
import app.kisscut.keys.shared.core.ImportResult
import app.kisscut.keys.shared.core.buildCollectionTree
import app.kisscut.keys.shared.core.collectionAncestors
import app.kisscut.keys.shared.db.StickerCollection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the Save sheet needs from the app; faked in tests. */
interface SaveBackend {
    fun observeCollections(): Flow<List<StickerCollection>>
    suspend fun createCollection(name: String, parentId: Long?): StickerCollection

    /** Header facts for the preview, or null when the file cannot be saved. */
    suspend fun describe(uri: String): ItemDetails?
    /** [onProgress] gets 0 to 1 while a slow step (shrinking a large GIF) runs. */
    suspend fun import(uri: String, collectionId: Long?, onProgress: (Float) -> Unit): ImportResult
}

/** Save sheet: preview, nested collection picker, "New collection", Save. One per incoming share. */
class SaveViewModel(
    private val backend: SaveBackend,
    uris: List<String>,
) : ViewModel() {
    private val _state = MutableStateFlow(SaveUiState(items = uris.map { SaveItem(it, ItemPreview.Loading) }))
    val state: StateFlow<SaveUiState> = _state.asStateFlow()

    private var all: List<StickerCollection> = emptyList()
    private var expanded: Set<Long> = emptySet()
    private var saveStarted = false

    init {
        viewModelScope.launch {
            backend.observeCollections().collect {
                all = it
                // A selected collection deleted elsewhere falls back to Unsorted.
                val selected = _state.value.selectedId?.takeIf { id -> all.any { c -> c.id == id } }
                refresh(selected)
            }
        }
        viewModelScope.launch {
            for (uri in uris) {
                val preview = runCatching { backend.describe(uri) }.getOrNull()
                    ?.let { ItemPreview.Ready(it) } ?: ItemPreview.Unsupported
                _state.update { s ->
                    s.copy(items = s.items.map { if (it.uri == uri) it.copy(preview = preview) else it })
                }
            }
        }
    }

    fun toggleExpanded(id: Long) {
        expanded = if (id in expanded) expanded - id else expanded + id
        refresh(_state.value.selectedId)
    }

    /** null selects Unsorted. */
    fun select(id: Long?) = refresh(id)

    fun openNewCollection() = _state.update { it.copy(newCollection = NewCollectionDraft()) }

    fun setNewCollectionName(name: String) = _state.update { s ->
        s.copy(newCollection = s.newCollection?.copy(name = name))
    }

    fun dismissNewCollection() = _state.update { it.copy(newCollection = null) }

    fun confirmNewCollection() {
        val draft = _state.value.newCollection ?: return
        if (!draft.canConfirm) return
        val parentId = newCollectionParent(_state.value.selectedId)?.id
        viewModelScope.launch {
            val created = try {
                backend.createCollection(draft.name, parentId)
            } catch (e: CollectionRuleException) {
                return@launch // dialog stays open; the name or parent was refused
            }
            if (created !in all) all = all + created
            expanded = expanded + collectionAncestors(all, created.id)
            _state.update { it.copy(newCollection = null) }
            refresh(created.id)
        }
    }

    fun save() {
        if (saveStarted) return
        saveStarted = true
        val target = _state.value.selectedId
        val uris = _state.value.items.map { it.uri }
        viewModelScope.launch {
            var added = 0
            var duplicates = 0
            var rejected = 0
            _state.update { it.copy(phase = SavePhase.Saving(0, uris.size)) }
            uris.forEachIndexed { index, uri ->
                val result = backend.import(uri, target) { fraction ->
                    val percent = (fraction * 100).toInt().coerceIn(0, 100)
                    _state.update { s ->
                        val phase = s.phase
                        if (phase is SavePhase.Saving && phase.percent != percent) {
                            s.copy(phase = phase.copy(percent = percent))
                        } else {
                            s
                        }
                    }
                }
                when (result) {
                    is ImportResult.Added -> added++
                    is ImportResult.Duplicate -> duplicates++
                    is ImportResult.Rejected -> rejected++
                }
                _state.update { it.copy(phase = SavePhase.Saving(index + 1, uris.size)) }
            }
            _state.update { it.copy(phase = SavePhase.Done(SaveSummary(added, duplicates, rejected))) }
        }
    }

    private fun refresh(selectedId: Long?) {
        val byId = all.associateBy { it.id }
        val selected = selectedId?.let { byId[it] }
        val path = selected?.let { s -> collectionAncestors(all, s.id).mapNotNull { byId[it]?.name } + s.name }
        _state.update {
            it.copy(
                tree = buildCollectionTree(all, expanded),
                selectedId = selected?.id,
                selectedPath = path.orEmpty(),
                newCollectionParentName = newCollectionParent(selected?.id)?.name,
            )
        }
    }

    /** Inside the selected collection, or beside it when it is already at the depth cap. */
    private fun newCollectionParent(selectedId: Long?): StickerCollection? {
        val selected = selectedId?.let { id -> all.firstOrNull { it.id == id } } ?: return null
        val depth = collectionAncestors(all, selected.id).size + 1
        return if (depth < CollectionRepository.MAX_DEPTH) {
            selected
        } else {
            all.firstOrNull { it.id == selected.parentId }
        }
    }
}
