package app.kisscut.keys.shared.core

import app.kisscut.keys.shared.db.StickerCollection
import kotlin.test.Test
import kotlin.test.assertEquals

class KeyboardTabsTest {
    private fun c(id: Long, name: String, parent: Long? = null, order: Int = id.toInt()) =
        StickerCollection(id, name, parent, order, 0)

    private val all = listOf(
        c(2, "Work", order = 5),
        c(1, "Reactions", order = 1),
        c(3, "Memes", parent = 1, order = 2),
        c(4, "Cats", parent = 1, order = 1),
        c(5, "Classics", parent = 3),
    )

    @Test
    fun tabsAreEmojiThenSystemThenTopLevelInOrder() {
        assertEquals(
            listOf(
                KeyboardTab.Emoji,
                KeyboardTab.Recents,
                KeyboardTab.Favorites,
                KeyboardTab.Collection(1, "Reactions"),
                KeyboardTab.Collection(2, "Work"),
            ),
            keyboardTabs(all, hasUnsorted = false),
        )
    }

    @Test
    fun unsortedTabOnlyWhenItHasStickers() {
        assertEquals(KeyboardTab.Unsorted, keyboardTabs(all, hasUnsorted = true).last())
        assertEquals(
            listOf(KeyboardTab.Emoji, KeyboardTab.Recents, KeyboardTab.Favorites),
            keyboardTabs(emptyList(), false),
        )
    }

    @Test
    fun chipsAreDirectChildrenOfACollectionTab() {
        val chips = keyboardChips(all, KeyboardTab.Collection(1, "Reactions"))
        assertEquals(listOf("Cats", "Memes"), chips.map { it.name })
        assertEquals(emptyList(), keyboardChips(all, KeyboardTab.Collection(2, "Work")))
        assertEquals(emptyList(), keyboardChips(all, KeyboardTab.Recents))
    }

    @Test
    fun startsOnRecentsWhenItHasStickers() {
        val tabs = keyboardTabs(all, hasUnsorted = true)
        assertEquals(KeyboardTab.Recents, startTab(tabs) { true })
    }

    @Test
    fun startsOnFirstTabWithStickersOtherwise() {
        val tabs = keyboardTabs(all, hasUnsorted = true)
        assertEquals(KeyboardTab.Collection(2, "Work"), startTab(tabs) { it == KeyboardTab.Collection(2, "Work") })
        assertEquals(KeyboardTab.Unsorted, startTab(tabs) { it == KeyboardTab.Unsorted })
        assertEquals(KeyboardTab.Recents, startTab(tabs) { false })
    }

    @Test
    fun neverStartsOnEmoji() {
        val tabs = keyboardTabs(all, hasUnsorted = false)
        assertEquals(KeyboardTab.Recents, startTab(tabs) { it == KeyboardTab.Emoji })
    }
}
