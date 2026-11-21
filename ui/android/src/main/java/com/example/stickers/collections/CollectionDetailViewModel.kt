package com.example.stickers.collections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.stickers.common.CollectionDialog
import com.example.stickers.common.CollectionEditor
import com.example.stickers.common.LibraryBackend
import com.example.stickers.common.SelectionUi
import com.example.stickers.common.StickerDialog
import com.example.stickers.common.StickerSelection
import com.example.stickers.shared.core.CollectionRepository
import com.example.stickers.shared.core.CollectionSummary
import com.example.stickers.shared.core.StickerKind
import com.example.stickers.shared.core.collectionAncestors
import com.example.stickers.shared.core.filterKind
import com.example.stickers.shared.db.Sticker
import com.example.stickers.shared.db.StickerCollection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

/** What an Inside-a-collection screen shows: a real collection, or the Favorites / Unsorted system rows. */
sealed interface CollectionTarget {
    data class Collection(val id: Long) : CollectionTarget
    data object Favorites : CollectionTarget
    data object Unsorted : CollectionTarget
}

data class Crumb(val id: Long, val name: String)

data class CollectionDetailUiState(
    val loaded: Boolean = false,
    /** The collection was deleted (here or elsewhere); the screen should close. */
    val gone: Boolean = false,
    /** Null for Favorites and Unsorted (the UI names them). */
    val name: String? = null,
    /** Parents, top level first (not including this collection). */
    val breadcrumb: List<Crumb> = emptyList(),
    val totalCount: Int = 0,
    val children: List<CollectionSummary> = emptyList(),
    val canAddChild: Boolean = false,
    /** Up to three stickers for the decoration next to the title. */
    val art: List<Sticker> = emptyList(),
    /** Stickers directly here, before the kind filter. */
    val directCount: Int = 0,
    val kind: StickerKind = StickerKind.ALL,
    val stickers: List<Sticker> = emptyList(),
    val selection: SelectionUi = SelectionUi(),
    val stickerDialog: StickerDialog? = null,
    val collectionDialog: CollectionDialog? = null,
) {
    val isEmpty: Boolean get() = loaded && children.isEmpty() && directCount == 0
}

class CollectionDetailViewModel(
    private val backend: LibraryBackend,
    val target: CollectionTarget,
) : ViewModel() {
    private val kind = MutableStateFlow(StickerKind.ALL)
    val selection = StickerSelection(viewModelScope, backend)
    val editor = CollectionEditor(viewModelScope, backend)

    private val direct = when (target) {
        is CollectionTarget.Collection -> backend.observeInCollection(target.id)
        CollectionTarget.Favorites -> backend.observeFavorites()
        CollectionTarget.Unsorted -> backend.observeUnsorted()
    }

    private val content = combine(direct, kind) { list, k -> list to list.filterKind(k) }
        .onEach { (_, shown) -> selection.onVisible(shown) }

    private val dialogs = combine(selection.state, selection.dialog, editor.dialog) { s, sd, cd -> Triple(s, sd, cd) }

    val state: StateFlow<CollectionDetailUiState> = combine(
        backend.observeCollections(),
        backend.observeSummaries(),
        content,
        kind,
        dialogs,
    ) { all, summaries, (directList, shown), k, (sel, stickerDialog, collectionDialog) ->
        selection.collections = all
        val base = CollectionDetailUiState(
            loaded = true,
            directCount = directList.size,
            kind = k,
            stickers = shown,
            selection = sel,
            stickerDialog = stickerDialog,
            collectionDialog = collectionDialog,
        )
        when (target) {
            is CollectionTarget.Collection -> forCollection(base, target.id, all, summaries, directList)
            else -> base.copy(totalCount = directList.size, art = pickArt(directList))
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, CollectionDetailUiState())

    private fun forCollection(
        base: CollectionDetailUiState,
        id: Long,
        all: List<StickerCollection>,
        summaries: Map<Long, CollectionSummary>,
        directList: List<Sticker>,
    ): CollectionDetailUiState {
        val me = all.firstOrNull { it.id == id } ?: return base.copy(gone = true)
        val summary = summaries[id]
        val byId = all.associateBy { it.id }
        val children = all.filter { it.parentId == id }
            .sortedWith(compareBy({ it.sortOrder }, { it.id }))
            .mapNotNull { summaries[it.id] }
        return base.copy(
            name = me.name,
            breadcrumb = collectionAncestors(all, id).mapNotNull { a -> byId[a]?.let { Crumb(it.id, it.name) } },
            totalCount = summary?.totalCount ?: base.directCount,
            children = children,
            canAddChild = (summary?.depth ?: CollectionRepository.MAX_DEPTH) < CollectionRepository.MAX_DEPTH,
            art = pickArt(directList + summary?.previews.orEmpty()),
        )
    }

    /** Die-cut stills look best scattered next to the title; others only fill in. */
    private fun pickArt(candidates: List<Sticker>): List<Sticker> =
        candidates.distinctBy { it.id }.sortedByDescending { it.hasAlpha && !it.isAnimated }.take(ART_COUNT)

    fun setKind(k: StickerKind) {
        kind.value = k
    }

    private companion object {
        const val ART_COUNT = 3
    }
}
