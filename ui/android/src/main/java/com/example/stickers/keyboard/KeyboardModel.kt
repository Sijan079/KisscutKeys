package com.example.stickers.keyboard

import com.example.stickers.common.LibraryBackend
import com.example.stickers.shared.core.KeyboardTab
import com.example.stickers.shared.core.StickerKind
import com.example.stickers.shared.core.filterKind
import com.example.stickers.shared.core.keyboardChips
import com.example.stickers.shared.core.keyboardTabs
import com.example.stickers.shared.core.startTab
import com.example.stickers.shared.db.Sticker
import com.example.stickers.shared.db.StickerCollection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Everything the sticker panel shows. One immutable state, like the app's screens. */
data class KeyboardUiState(
    val loaded: Boolean = false,
    /** No stickers anywhere: the panel shows the "open the app" message. */
    val libraryEmpty: Boolean = false,
    val tabs: List<KeyboardTab> = listOf(KeyboardTab.Recents, KeyboardTab.Favorites),
    val tab: KeyboardTab = KeyboardTab.Recents,
    /** Subcollections of the selected collection tab; empty hides the chip row. */
    val chips: List<StickerCollection> = emptyList(),
    /** Selected chip; null is "All" (the whole tab). */
    val chip: Long? = null,
    val kind: StickerKind = StickerKind.ALL,
    val stickers: List<Sticker> = emptyList(),
    /** Collection names by id, for content descriptions. */
    val collectionNames: Map<Long, String> = emptyMap(),
)

private data class Choice(val tab: KeyboardTab?, val chip: Long?, val kind: StickerKind)

private data class Library(
    val collections: List<StickerCollection>,
    val tabs: List<KeyboardTab>,
    val start: KeyboardTab,
    val empty: Boolean,
)

/**
 * State for the keyboard panel. Not an AndroidX ViewModel: the IME service owns [scope] and cancels it
 * in onDestroy. The tab is picked once data arrives (Recents if used before, else the first non-empty tab)
 * and then kept until the user changes it or the collection disappears.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class KeyboardModel(private val backend: LibraryBackend, scope: CoroutineScope) {
    private val choice = MutableStateFlow(Choice(tab = null, chip = null, kind = StickerKind.ALL))

    private val library: Flow<Library> = combine(
        backend.observeCollections(),
        backend.observeSummaries(),
        backend.observeRecents(),
        backend.observeFavorites(),
        backend.observeUnsorted(),
    ) { collections, summaries, recents, favorites, unsorted ->
        val tabs = keyboardTabs(collections, hasUnsorted = unsorted.isNotEmpty())
        val start = startTab(tabs) { tab ->
            when (tab) {
                KeyboardTab.Recents -> recents.isNotEmpty()
                KeyboardTab.Favorites -> favorites.isNotEmpty()
                KeyboardTab.Unsorted -> unsorted.isNotEmpty()
                is KeyboardTab.Collection -> (summaries[tab.id]?.totalCount ?: 0) > 0
            }
        }
        val total = unsorted.size + summaries.values.filter { it.depth == 1 }.sumOf { it.totalCount }
        Library(collections, tabs, start, empty = total == 0)
    }

    val state: StateFlow<KeyboardUiState> = combine(library, choice) { lib, c -> lib to c }
        .flatMapLatest { (lib, c) ->
            // A chosen tab that no longer exists (collection deleted or renamed) falls back to the start tab.
            val tab = c.tab?.let { chosen -> lib.tabs.firstOrNull { it.sameAs(chosen) } } ?: lib.start
            val chips = keyboardChips(lib.collections, tab)
            val chip = c.chip?.takeIf { id -> chips.any { it.id == id } }
            source(tab, chip).map { list ->
                KeyboardUiState(
                    loaded = true,
                    libraryEmpty = lib.empty,
                    tabs = lib.tabs,
                    tab = tab,
                    chips = chips,
                    chip = chip,
                    kind = c.kind,
                    stickers = list.filterKind(c.kind),
                    collectionNames = lib.collections.associate { it.id to it.name },
                )
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, KeyboardUiState())

    fun selectTab(tab: KeyboardTab) {
        choice.value = choice.value.copy(tab = tab, chip = null)
    }

    /** null selects "All". */
    fun selectChip(id: Long?) {
        choice.value = choice.value.copy(tab = choice.value.tab ?: state.value.tab, chip = id)
    }

    fun selectKind(kind: StickerKind) {
        choice.value = choice.value.copy(tab = choice.value.tab ?: state.value.tab, kind = kind)
    }

    private fun source(tab: KeyboardTab, chip: Long?): Flow<List<Sticker>> = when (tab) {
        KeyboardTab.Recents -> backend.observeRecents()
        KeyboardTab.Favorites -> backend.observeFavorites()
        KeyboardTab.Unsorted -> backend.observeUnsorted()
        is KeyboardTab.Collection -> backend.observeInTree(chip ?: tab.id)
    }

    /** Collection tabs match by id, so a rename keeps the selection. */
    private fun KeyboardTab.sameAs(other: KeyboardTab) =
        if (this is KeyboardTab.Collection && other is KeyboardTab.Collection) id == other.id else this == other
}
