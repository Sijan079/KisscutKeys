package app.kisscut.keys.shared.core

import app.kisscut.keys.shared.db.CollectionWithCount
import app.kisscut.keys.shared.db.Sticker
import app.kisscut.keys.shared.db.StickerCollection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LibraryRulesTest {
    private fun col(id: Long, parent: Long?, sort: Int = id.toInt()) =
        StickerCollection(id = id, name = "c$id", parentId = parent, sortOrder = sort, createdAt = 0)

    private fun sticker(
        id: Long,
        collectionId: Long? = null,
        addedAt: Long = id,
        useCount: Int = 0,
        lastUsedAt: Long? = null,
        animated: Boolean = false,
        favorite: Boolean = false,
    ) = Sticker(
        id = id, filePath = "$id.png", thumbPath = "$id.webp", mimeType = "image/png",
        isAnimated = animated, hasAlpha = true, hash = "h$id", collectionId = collectionId,
        isFavorite = favorite, useCount = useCount, lastUsedAt = lastUsedAt, addedAt = addedAt,
    )

    // Tree: 1 > (2 > 3), 4 ; counts direct: 1:2, 2:1, 3:4, 4:0
    private val tree = listOf(
        CollectionWithCount(col(1, null), 2),
        CollectionWithCount(col(2, 1), 1),
        CollectionWithCount(col(3, 2), 4),
        CollectionWithCount(col(4, null), 0),
    )

    @Test
    fun totalsIncludeEveryDescendant() {
        val s = summarizeCollections(tree, previews = emptyList())
        assertEquals(7, s.getValue(1).totalCount)
        assertEquals(5, s.getValue(2).totalCount)
        assertEquals(4, s.getValue(3).totalCount)
        assertEquals(0, s.getValue(4).totalCount)
        assertEquals(2, s.getValue(1).directCount)
    }

    @Test
    fun childCountIsDirectSubcollectionsOnly() {
        val s = summarizeCollections(tree, previews = emptyList())
        assertEquals(1, s.getValue(1).childCount)
        assertEquals(1, s.getValue(2).childCount)
        assertEquals(0, s.getValue(3).childCount)
    }

    @Test
    fun depthIsOneForTopLevel() {
        val s = summarizeCollections(tree, previews = emptyList())
        assertEquals(1, s.getValue(1).depth)
        assertEquals(3, s.getValue(3).depth)
    }

    @Test
    fun previewsUseOwnStickersFirstThenDescendantsUpToThree() {
        val previews = listOf(
            sticker(10, collectionId = 1, addedAt = 5),
            sticker(11, collectionId = 3, addedAt = 9),
            sticker(12, collectionId = 3, addedAt = 8),
            sticker(13, collectionId = 3, addedAt = 7),
        )
        val s = summarizeCollections(tree, previews)
        assertEquals(listOf(10L, 11L, 12L), s.getValue(1).previews.map { it.id })
        assertEquals(listOf(11L, 12L, 13L), s.getValue(2).previews.map { it.id })
        assertTrue(s.getValue(4).previews.isEmpty())
    }

    @Test
    fun heroIsTheMostUsedSticker() {
        val list = listOf(
            sticker(1, useCount = 2, lastUsedAt = 10),
            sticker(2, useCount = 5, lastUsedAt = 3),
            sticker(3, useCount = 5, lastUsedAt = 4),
        )
        val hero = pickHero(list)!!
        assertEquals(3L, hero.sticker.id) // tie broken by most recent use
        assertEquals(HeroReason.MOST_USED, hero.reason)
    }

    @Test
    fun heroFallsBackToNewestWhenNothingWasUsed() {
        val hero = pickHero(listOf(sticker(1, addedAt = 5), sticker(2, addedAt = 9), sticker(3, addedAt = 9)))!!
        assertEquals(3L, hero.sticker.id)
        assertEquals(HeroReason.NEWEST, hero.reason)
        assertNull(pickHero(emptyList()))
    }

    @Test
    fun kindFilterSplitsStaticAndAnimated() {
        val list = listOf(sticker(1), sticker(2, animated = true))
        assertEquals(listOf(1L, 2L), list.filterKind(StickerKind.ALL).map { it.id })
        assertEquals(listOf(1L), list.filterKind(StickerKind.STATIC).map { it.id })
        assertEquals(listOf(2L), list.filterKind(StickerKind.ANIMATED).map { it.id })
    }

    @Test
    fun favoriteActionFavoritesUnlessAllAreFavoritesAlready() {
        assertTrue(favoriteActionValue(listOf(sticker(1, favorite = true), sticker(2))))
        assertFalse(favoriteActionValue(listOf(sticker(1, favorite = true), sticker(2, favorite = true))))
        assertTrue(favoriteActionValue(emptyList()))
    }

    @Test
    fun collectionMoveRespectsCyclesAndDepth() {
        val all = tree.map { it.collection }
        // 2 (height 2) under 4 (depth 1): 1 + 2 = 3, fine.
        assertTrue(canMoveCollection(all, id = 2, newParentId = 4))
        // 2 under its own child 3: cycle.
        assertFalse(canMoveCollection(all, id = 2, newParentId = 3))
        assertFalse(canMoveCollection(all, id = 2, newParentId = 2))
        // 1 (height 3) under 4: 1 + 3 = 4, too deep.
        assertFalse(canMoveCollection(all, id = 1, newParentId = 4))
        // Top level is always fine.
        assertTrue(canMoveCollection(all, id = 3, newParentId = null))
        // Into its current parent: nothing to do.
        assertFalse(canMoveCollection(all, id = 3, newParentId = 2))
    }
}
