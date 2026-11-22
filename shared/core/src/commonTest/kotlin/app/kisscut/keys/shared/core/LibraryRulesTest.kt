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

    // Sections: 20 stickers, ids 1..20, newest = highest id.
    private fun library(n: Int = 20, uses: Map<Long, Int> = emptyMap()) =
        (1L..n).map { sticker(it, useCount = uses[it] ?: 0, lastUsedAt = uses[it]?.let { _ -> it }) }

    private fun ids(list: List<Sticker>) = list.map { it.id }

    @Test
    fun smallLibrariesHaveNoSections() {
        assertNull(librarySections(library(LibrarySections.MIN_STICKERS - 1), seed = 1))
        assertTrue(librarySections(library(LibrarySections.MIN_STICKERS), seed = 1) != null)
    }

    @Test
    fun mostUsedIsTopThreeByUsesAndStartsWithTheHero() {
        val list = library(uses = mapOf(4L to 9, 7L to 2, 2L to 5, 11L to 1))
        val s = librarySections(list, seed = 1)!!
        assertEquals(listOf(4L, 2L, 7L), ids(s.mostUsed))
        assertEquals(pickHero(list)!!.sticker, s.mostUsed.first())
    }

    @Test
    fun mostUsedOnlyHoldsUsedStickers() {
        assertEquals(emptyList(), librarySections(library(), seed = 1)!!.mostUsed)
        assertEquals(listOf(5L), ids(librarySections(library(uses = mapOf(5L to 1)), seed = 1)!!.mostUsed))
    }

    @Test
    fun newIsTheSixNewestNotAlreadyInMostUsed() {
        val s = librarySections(library(uses = mapOf(20L to 3)), seed = 1)!!
        assertEquals(listOf(19L, 18L, 17L, 16L, 15L, 14L), ids(s.new))
    }

    @Test
    fun noStickerIsInTwoSections() {
        val list = library(uses = mapOf(20L to 3, 1L to 1, 2L to 1))
        for (seed in 0L..50L) {
            val s = librarySections(list, seed)!!
            val all = ids(s.mostUsed) + ids(s.new) + ids(s.leastUsed)
            assertEquals(all.size, all.toSet().size, "seed $seed: $all")
            assertEquals(LibrarySections.LEAST_USED_COUNT, s.leastUsed.size)
        }
    }

    @Test
    fun leastUsedComesFromTheFewestUses() {
        // 1..8 used 10 times each: 8, 7, 6 go to Most used (ties: used more recently), 20..15 to New.
        // Left: 1..5 (used) and 9..14 (never used); the six never-used ones are exactly the pick.
        val uses = (1L..8L).associateWith { 10 }
        val s = librarySections(library(uses = uses), seed = 7)!!
        assertEquals(listOf(8L, 7L, 6L), ids(s.mostUsed))
        assertEquals((9L..14L).toSet(), ids(s.leastUsed).toSet())
    }

    @Test
    fun leastUsedIncludesEveryStickerTiedAtTheCutoff() {
        // After Most used (none) and New (15..20), 14 stickers left, all unused: any of them may be picked.
        val picked = (0L..200L).flatMap { ids(librarySections(library(), it)!!.leastUsed) }.toSet()
        assertEquals((1L..14L).toSet(), picked)
    }

    @Test
    fun leastUsedIsStableForOneSeedAndChangesWithAnother() {
        val list = library()
        assertEquals(ids(librarySections(list, 42)!!.leastUsed), ids(librarySections(list, 42)!!.leastUsed))
        val picks = (0L..20L).map { ids(librarySections(list, it)!!.leastUsed) }.toSet()
        assertTrue(picks.size > 1, "every seed gave the same pick")
    }

    @Test
    fun usingOneStickerMovesAtMostOneLeastUsedTile() {
        val before = librarySections(library(), seed = 3)!!
        val usedId = before.leastUsed.first().id
        val after = librarySections(library(uses = mapOf(usedId to 1)), seed = 3)!!
        // It jumps to Most used; the other five stay where they were.
        assertEquals(listOf(usedId), ids(after.mostUsed))
        assertEquals(ids(before.leastUsed).drop(1), ids(after.leastUsed).take(5))
    }
}
