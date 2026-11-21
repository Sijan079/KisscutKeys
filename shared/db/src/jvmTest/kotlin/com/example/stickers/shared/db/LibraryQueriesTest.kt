package com.example.stickers.shared.db

import app.cash.turbine.test
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Queries added for the Library and Collections screens (M3). */
class LibraryQueriesTest {
    private val db = inMemoryStickerDatabase()
    private val stickers = db.stickerDao()
    private val collections = db.collectionDao()

    @AfterTest
    fun close() = db.close()

    private suspend fun collection(name: String, parentId: Long? = null, sortOrder: Int = 0): Long =
        collections.insert(StickerCollection(name = name, parentId = parentId, sortOrder = sortOrder, createdAt = 0))

    @Test
    fun observeAllWithCountsListsEveryLevelWithDirectCounts() = runTest {
        val a = collection("A", sortOrder = 1)
        val b = collection("B", sortOrder = 0)
        val a1 = collection("A1", parentId = a)
        stickers.insert(testSticker("x", collectionId = a))
        stickers.insert(testSticker("y", collectionId = a1))
        stickers.insert(testSticker("z", collectionId = a1))
        stickers.insert(testSticker("loose"))

        val rows = collections.observeAllWithCounts().first()
        assertEquals(listOf("B", "A1", "A"), rows.map { it.collection.name })
        assertEquals(mapOf(b to 0, a to 1, a1 to 2), rows.associate { it.collection.id to it.stickerCount })
    }

    @Test
    fun observeAllWithCountsUpdatesWhenStickersMove() = runTest {
        val a = collection("A")
        val id = stickers.insert(testSticker("x"))
        collections.observeAllWithCounts().test {
            assertEquals(0, awaitItem().single().stickerCount)
            stickers.moveTo(listOf(id), a)
            assertEquals(1, awaitItem().single().stickerCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun previewsAreTheNewestThreePerCollection() = runTest {
        val a = collection("A")
        val b = collection("B")
        for (i in 1..5) stickers.insert(testSticker("a$i", collectionId = a, addedAt = i * 10L))
        stickers.insert(testSticker("b1", collectionId = b, addedAt = 5))
        stickers.insert(testSticker("loose", addedAt = 99))

        val previews = stickers.observePreviews(limit = 3).first()
        assertEquals(setOf("a5", "a4", "a3", "b1"), previews.map { it.hash }.toSet())
    }

    @Test
    fun inTreeShowsStickersOfTheCollectionAndAllDescendantsNewestFirst() = runTest {
        val top = collection("Top")
        val mid = collection("Mid", parentId = top)
        val low = collection("Low", parentId = mid)
        val other = collection("Other")
        stickers.insert(testSticker("t", collectionId = top, addedAt = 1))
        stickers.insert(testSticker("m", collectionId = mid, addedAt = 2))
        stickers.insert(testSticker("l", collectionId = low, addedAt = 3))
        stickers.insert(testSticker("o", collectionId = other, addedAt = 4))

        assertEquals(listOf("l", "m", "t"), stickers.observeInTree(top).first().map { it.hash })
        assertEquals(listOf("l", "m"), stickers.observeInTree(mid).first().map { it.hash })
    }
}
