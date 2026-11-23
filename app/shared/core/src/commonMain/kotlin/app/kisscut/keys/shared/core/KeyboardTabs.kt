package app.kisscut.keys.shared.core

import app.kisscut.keys.shared.db.StickerCollection

/** One tab in the keyboard's top row (docs/KEYBOARD.md). */
sealed interface KeyboardTab {
    /** System emoji by category (ROADMAP M6). Typed as text; never a start tab. */
    data object Emoji : KeyboardTab
    data object Recents : KeyboardTab
    data object Favorites : KeyboardTab
    data class Collection(val id: Long, val name: String) : KeyboardTab

    /** Only shown while there are unsorted stickers, so they stay reachable. */
    data object Unsorted : KeyboardTab
}

/** Emoji, Recents, Favorites, top-level collections in their order, then Unsorted when [hasUnsorted]. */
fun keyboardTabs(collections: List<StickerCollection>, hasUnsorted: Boolean): List<KeyboardTab> = buildList {
    add(KeyboardTab.Emoji)
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

/** Opens on Recents when it has stickers, else the first sticker tab that has any, else Recents. Never Emoji. */
fun startTab(tabs: List<KeyboardTab>, hasStickers: (KeyboardTab) -> Boolean): KeyboardTab =
    tabs.firstOrNull { it != KeyboardTab.Emoji && hasStickers(it) } ?: KeyboardTab.Recents
