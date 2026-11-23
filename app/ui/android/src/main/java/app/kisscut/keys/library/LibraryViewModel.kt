package app.kisscut.keys.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kisscut.keys.common.LibraryBackend
import app.kisscut.keys.common.SelectionUi
import app.kisscut.keys.common.StickerDialog
import app.kisscut.keys.common.StickerSelection
import app.kisscut.keys.shared.core.Hero
import app.kisscut.keys.shared.core.HeroReason
import app.kisscut.keys.shared.core.LibrarySections
import app.kisscut.keys.shared.core.StickerKind
import app.kisscut.keys.shared.core.filterKind
import app.kisscut.keys.shared.core.librarySections
import app.kisscut.keys.shared.core.pickHero
import app.kisscut.keys.shared.db.Sticker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlin.random.Random

sealed interface LibraryFilter {
    data object All : LibraryFilter
    data object Favorites : LibraryFilter
    data object Recents : LibraryFilter
    data class InCollection(val id: Long) : LibraryFilter
}

/** One round item in the filter rail. [name] and [preview] are set for collections. */
data class RailItem(val filter: LibraryFilter, val name: String? = null, val preview: Sticker? = null)

data class LibraryUiState(
    val loaded: Boolean = false,
    /** Every sticker in the library, whatever the filter. */
    val totalCount: Int = 0,
    val rail: List<RailItem> = SYSTEM_RAIL,
    val filter: LibraryFilter = LibraryFilter.All,
    val kind: StickerKind = StickerKind.ALL,
    /**
     * The big tile; not part of [stickers]. Hidden in select mode and in short lists. With [sections] it is
     * the first Most used sticker and [stickers] keeps every sticker (the "All stickers" grid).
     */
    val hero: Hero? = null,
    /** Most used, New and Least used above "All stickers": only the All filter, outside select mode. */
    val sections: LibrarySections? = null,
    val stickers: List<Sticker> = emptyList(),
    val collectionNames: Map<Long, String> = emptyMap(),
    val selection: SelectionUi = SelectionUi(),
    val dialog: StickerDialog? = null,
) {
    val isLibraryEmpty: Boolean get() = loaded && totalCount == 0
    val isViewEmpty: Boolean get() = loaded && hero == null && stickers.isEmpty()

    /** Every sticker on screen once, hero first. Sections only repeat stickers from the grid. */
    val shown: List<Sticker> get() = if (sections != null) stickers else listOfNotNull(hero?.sticker) + stickers

    companion object {
        val SYSTEM_RAIL = listOf(
            RailItem(LibraryFilter.All),
            RailItem(LibraryFilter.Favorites),
            RailItem(LibraryFilter.Recents),
        )

        /** The hero tile needs a few stickers around it to look right. */
        const val HERO_MIN = 4
    }
}

/**
 * Library: filter rail, Static/Animated chips, sections or grid with hero tile, select mode.
 * [seed] picks the Least used stickers; one per screen instance, so the pick holds while the app is open.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModel(
    private val backend: LibraryBackend,
    private val seed: Long = Random.nextLong(),
) : ViewModel() {
    private val filter = MutableStateFlow<LibraryFilter>(LibraryFilter.All)
    private val kind = MutableStateFlow(StickerKind.ALL)
    val selection = StickerSelection(viewModelScope, backend)

    private val library = combine(
        backend.observeAll(),
        backend.observeCollections(),
        backend.observeSummaries(),
    ) { all, collections, summaries ->
        selection.collections = collections
        val current = filter.value
        if (current is LibraryFilter.InCollection && collections.none { it.id == current.id }) {
            filter.value = LibraryFilter.All // its collection was deleted
        }
        val rail = LibraryUiState.SYSTEM_RAIL + collections
            .filter { it.parentId == null }
            .sortedWith(compareBy({ it.sortOrder }, { it.id }))
            .map { RailItem(LibraryFilter.InCollection(it.id), it.name, summaries[it.id]?.previews?.firstOrNull()) }
        Triple(all.size, rail, collections.associate { it.id to it.name })
    }

    private val view = combine(filter.flatMapLatest { f -> source(f).map { f to it } }, kind) { (f, list), k ->
        Triple(f, k, list.filterKind(k))
    }.onEach { (_, _, list) -> selection.onVisible(list) }

    val state: StateFlow<LibraryUiState> = combine(
        library,
        view,
        selection.state,
        selection.dialog,
    ) { (total, rail, names), (f, k, list), sel, dialog ->
        val sections = if (f == LibraryFilter.All && !sel.active) librarySections(list, seed) else null
        val hero = when {
            sections != null -> sections.mostUsed.firstOrNull()?.let { Hero(it, HeroReason.MOST_USED) }
            !sel.active && list.size >= LibraryUiState.HERO_MIN -> pickHero(list)
            else -> null
        }
        LibraryUiState(
            loaded = true,
            totalCount = total,
            rail = rail,
            filter = f,
            kind = k,
            hero = hero,
            sections = sections,
            stickers = if (hero == null || sections != null) list else list.filter { it.id != hero.sticker.id },
            collectionNames = names,
            selection = sel,
            dialog = dialog,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, LibraryUiState())

    private fun source(f: LibraryFilter) = when (f) {
        LibraryFilter.All -> backend.observeAll()
        LibraryFilter.Favorites -> backend.observeFavorites()
        LibraryFilter.Recents -> backend.observeRecents()
        is LibraryFilter.InCollection -> backend.observeInTree(f.id)
    }

    fun setFilter(f: LibraryFilter) {
        filter.value = f
    }

    fun setKind(k: StickerKind) {
        kind.value = k
    }
}
