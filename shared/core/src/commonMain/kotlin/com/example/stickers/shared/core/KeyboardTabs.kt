package com.example.stickers.shared.core

import com.example.stickers.shared.db.StickerCollection

/** One tab in the keyboard's top row (docs/KEYBOARD.md). */
sealed interface KeyboardTab {
    data object Recents : KeyboardTab
    data object Favorites : KeyboardTab
    data class Collection(val id: Long, val name: String) : KeyboardTab

    /** Only shown while there are unsorted stickers, so they stay reachable. */
    data object Unsorted : KeyboardTab
}

/** Recents, Favorites, top-level collections in their order, then Unsorted when [hasUnsorted]. */
fun keyboardTabs(collections: List<StickerCollection>, hasUnsorted: Boolean): List<KeyboardTab> = buildList {
    add(KeyboardTab.Recents)
    add(KeyboardTab.Favorites)
    collections.filter { it.parentId == null }
        .sortedWith(compareBy({ it.sortOrder }, { it.id }))
        .forEach { add(KeyboardTab.Collection(it.id, it.name)) }
    if (hasUnsorted) add(KeyboardTab.Unsorted)
}

/** The second chip row: direct subcollections of a collection tab, in order. Empty for other tabs. */
fun keyboardChips(collections: List<StickerCollection>, tab: KeyboardTab): List<StickerCollection> {
    if (tab !is KeyboardTab.Collection) return emptyList()
    return collections.filter { it.parentId == tab.id }.sortedWith(compareBy({ it.sortOrder }, { it.id }))
}

/** Opens on Recents when it has stickers, else the first tab that has any, else Recents. */
fun startTab(tabs: List<KeyboardTab>, hasStickers: (KeyboardTab) -> Boolean): KeyboardTab =
    tabs.firstOrNull(hasStickers) ?: KeyboardTab.Recents
