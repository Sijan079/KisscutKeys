package com.example.stickers.shared.db

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StickerDaoTest {
    private val db = inMemoryStickerDatabase()
    private val dao = db.stickerDao()
    private val collections = db.collectionDao()

    @AfterTest
    fun close() = db.close()

    private suspend fun collection(name: String, parentId: Long? = null): Long =
        collections.insert(StickerCollection(name = name, parentId = parentId, sortOrder = 0, createdAt = 0))

    private fun List<Sticker>.hashes() = map { it.hash }

    @Test
    fun insertDuplicateHashIsIgnored() = runTest {
        val first = dao.insert(testSticker(hash = "abc"))
        val second = dao.insert(testSticker(hash = "abc", addedAt = 2_000L))

        assertEquals(-1L, second)
        assertEquals(first, dao.findByHash("abc")?.id)
        assertEquals(1_000L, dao.findByHash("abc")?.addedAt)
    }

    @Test
    fun observeAllIsNewestFirstWithIdBreakingTies() = runTest {
        dao.insert(testSticker("old", addedAt = 1_000))
        dao.insert(testSticker("new", addedAt = 3_000))
        dao.insert(testSticker("tieA", addedAt = 2_000))
        dao.insert(testSticker("tieB", addedAt = 2_000))

        assertEquals(listOf("new", "tieB", "tieA", "old"), dao.observeAll().first().hashes())
    }

    @Test
    fun observeUnsortedOnlyShowsStickersWithoutCollection() = runTest {
        val c = collection("Cats")
        dao.insert(testSticker("loose1", addedAt = 1_000))
        dao.insert(testSticker("inCats", collectionId = c, addedAt = 2_000))
        dao.insert(testSticker("loose2", addedAt = 3_000))

        assertEquals(listOf("loose2", "loose1"), dao.observeUnsorted().first().hashes())
    }

    @Test
    fun observeInCollectionOnlyShowsDirectStickers() = runTest {
        val parent = collection("Animals")
        val child = collection("Cats", parentId = parent)
        dao.insert(testSticker("a", collectionId = parent, addedAt = 1_000))
        dao.insert(testSticker("b", collectionId = parent, addedAt = 2_000))
        dao.insert(testSticker("c", collectionId = child, addedAt = 3_000))

        assertEquals(listOf("b", "a"), dao.observeInCollection(parent).first().hashes())
    }

    @Test
    fun observeFavoritesIsNewestFirst() = runTest {
        dao.insert(testSticker("fav1", isFavorite = true, addedAt = 1_000))
        dao.insert(testSticker("plain", addedAt = 2_000))
        dao.insert(testSticker("fav2", isFavorite = true, addedAt = 3_000))

        assertEquals(listOf("fav2", "fav1"), dao.observeFavorites().first().hashes())
    }

    @Test
    fun observeRecentsSkipsUnusedOrdersByLastUseAndRespectsLimit() = runTest {
        dao.insert(testSticker("never"))
        dao.insert(testSticker("usedEarly", lastUsedAt = 1_000))
        dao.insert(testSticker("usedLate", lastUsedAt = 5_000))
        dao.insert(testSticker("usedMid", lastUsedAt = 3_000))

        assertEquals(listOf("usedLate", "usedMid", "usedEarly"), dao.observeRecents(limit = 10).first().hashes())
        assertEquals(listOf("usedLate", "usedMid"), dao.observeRecents(limit = 2).first().hashes())
    }

    @Test
    fun markUsedIncrementsCountAndSetsTime() = runTest {
        val id = dao.insert(testSticker("s"))
        dao.markUsed(id, now = 7_000)
        dao.markUsed(id, now = 9_000)

        val s = dao.findByHash("s")!!
        assertEquals(2, s.useCount)
        assertEquals(9_000L, s.lastUsedAt)
    }

    @Test
    fun moveSetFavoriteAndDeleteActOnlyOnGivenIds() = runTest {
        val c = collection("Cats")
        val a = dao.insert(testSticker("a"))
        val b = dao.insert(testSticker("b"))
        val untouched = dao.insert(testSticker("x"))

        dao.moveTo(listOf(a, b), c)
        dao.setFavorite(listOf(a), true)
        assertEquals(setOf("a", "b"), dao.observeInCollection(c).first().hashes().toSet())
        assertEquals(listOf("a"), dao.observeFavorites().first().hashes())

        dao.moveTo(listOf(a), null)
        assertEquals(setOf("a", "x"), dao.observeUnsorted().first().hashes().toSet())

        dao.delete(listOf(a, b))
        assertEquals(listOf(untouched), dao.allOnce().map { it.id })
    }

    @Test
    fun deletingACollectionRowLeavesItsStickersUnsorted() = runTest {
        val c = collection("Cats")
        dao.insert(testSticker("s", collectionId = c))

        collections.deleteRow(c)

        assertEquals(null, dao.findByHash("s")!!.collectionId)
        assertTrue(dao.observeUnsorted().first().isNotEmpty())
    }
}
