package app.kisscut.keys.save

import app.kisscut.keys.shared.core.CollectionTreeRow
import app.kisscut.keys.shared.core.ImageInfo

/** Header facts about one incoming file, for the preview line. */
data class ItemDetails(val info: ImageInfo, val sizeBytes: Long)

sealed interface ItemPreview {
    data object Loading : ItemPreview
    data class Ready(val details: ItemDetails) : ItemPreview

    /** Not a PNG, JPEG, WebP or GIF, too large, or unreadable. Saving will report it as rejected. */
    data object Unsupported : ItemPreview
}

data class SaveItem(val uri: String, val preview: ItemPreview)

/** The "New collection" dialog. */
data class NewCollectionDraft(val name: String = "") {
    val canConfirm: Boolean get() = name.isNotBlank()
}

data class SaveSummary(val added: Int, val duplicates: Int, val rejected: Int)

sealed interface SavePhase {
    data object Picking : SavePhase
    /** [percent] is set while the current item is a large GIF being shrunk. */
    data class Saving(val done: Int, val total: Int, val percent: Int? = null) : SavePhase
    data class Done(val summary: SaveSummary) : SavePhase
}

data class SaveUiState(
    val items: List<SaveItem> = emptyList(),
    val tree: List<CollectionTreeRow> = emptyList(),
    /** null = Unsorted. */
    val selectedId: Long? = null,
    /** Names from the top level down to the selected collection; empty for Unsorted. */
    val selectedPath: List<String> = emptyList(),
    /** Where "New collection" creates it; null = top level. */
    val newCollectionParentName: String? = null,
    val newCollection: NewCollectionDraft? = null,
    val phase: SavePhase = SavePhase.Picking,
)
