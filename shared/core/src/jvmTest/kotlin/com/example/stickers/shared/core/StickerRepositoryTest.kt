package com.example.stickers.shared.core

import app.cash.turbine.test
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StickerRepositoryTest {
    private val db = inMemoryStickerDatabase()
    private val files = FakeFileStore()
    private val clock = TestClock(start = 1_000, step = 1_000)
    private val repo = StickerRepository(db, files, clock)
    private val collections = CollectionRepository(db, files, TestClock())

    @AfterTest
    fun close() = db.close()

    private suspend fun stored(hash: String): NewSticker {
        files.write(FileArea.STICKERS, "$hash.png", byteArrayOf(1))
        files.write(FileArea.THUMBS, "$hash.webp", byteArrayOf(2))
        return newSticker(hash)
    }

    // --- add and dedupe ----------------------------------------------------------------------

    @Test
    fun addStoresStickerWithAddedAtFromClock() = runTest {
        val cats = collections.create("Cats", null)

        val result = repo.add(stored("a"), collectionId = cats.id)

        assertTrue(result.isNew)
        assertEquals(cats.id, result.sticker.collectionId)
        assertEquals(1_000L, result.sticker.addedAt)
        assertEquals(listOf("a"), repo.observeInCollection(cats.id).first().map { it.hash })
    }

    @Test
    fun addingDuplicateHashReturnsExistingAndCreatesNoSecondRow() = runTest {
        val cats = collections.create("Cats", null)
        val first = repo.add(stored("same"), collectionId = null)

        val second = repo.add(stored("same"), collectionId = cats.id)

        assertFalse(second.isNew)
        assertEquals(first.sticker.id, second.sticker.id)
        assertEquals(null, second.sticker.collectionId, "not moved unless asked")
        assertEquals(1, repo.observeAll().first().size)
    }

    @Test
    fun addingDuplicateCanMoveExistingIntoChosenCollection() = runTest {
        val cats = collections.create("Cats", null)
        val first = repo.add(stored("same"), collectionId = null)

        val second = repo.add(stored("same"), collectionId = cats.id, moveIfExisting = true)

        assertEquals(first.sticker.id, second.sticker.id)
        assertEquals(cats.id, second.sticker.collectionId)
        assertEquals(listOf("same"), repo.observeInCollection(cats.id).first().map { it.hash })
        assertEquals(1, repo.observeAll().first().size)
    }

    @Test
    fun addRejectsBadInput() = runTest {
        assertFailsWith<IllegalArgumentException> { repo.add(newSticker("x").copy(hash = " "), null) }
        assertFailsWith<IllegalArgumentException> { repo.add(newSticker("x").copy(mimeType = "image/bmp"), null) }
    }

    // --- lists through the repository --------------------------------------------------------

    @Test
    fun unsortedFavoritesAndRecentsThroughRepository() = runTest {
        val cats = collections.create("Cats", null)
        val a = repo.add(stored("a"), null).sticker // addedAt 1000
        val b = repo.add(stored("b"), cats.id).sticker // 2000
        val c = repo.add(stored("c"), null).sticker // 3000

        assertEquals(listOf("c", "a"), repo.observeUnsorted().first().map { it.hash })

        repo.setFavorite(listOf(a.id, b.id), true)
        assertEquals(listOf("b", "a"), repo.observeFavorites().first().map { it.hash })

        repo.markUsed(b.id) // used at 4000
        repo.markUsed(a.id) // used at 5000
        assertEquals(listOf("a", "b"), repo.observeRecents().first().map { it.hash })
        assertEquals(listOf("a"), repo.observeRecents(limit = 1).first().map { it.hash })
        assertEquals(listOf("c", "b", "a"), repo.observeAll().first().map { it.hash })
        assertEquals(c.id, repo.allOnce().last().id)
    }

    @Test
    fun recentsFlowUpdatesWhenAStickerIsUsed() = runTest {
        val a = repo.add(stored("a"), null).sticker
        repo.observeRecents().test {
            assertEquals(emptyList(), awaitItem())
            repo.markUsed(a.id)
            assertEquals(listOf("a"), awaitItem().map { it.hash })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun moveToMovesSelectedStickers() = runTest {
        val cats = collections.create("Cats", null)
        val a = repo.add(stored("a"), null).sticker
        repo.add(stored("b"), null)

        repo.moveTo(listOf(a.id), cats.id)

        assertEquals(listOf("a"), repo.observeInCollection(cats.id).first().map { it.hash })
        assertEquals(listOf("b"), repo.observeUnsorted().first().map { it.hash })
    }

    // --- delete --------------------------------------------------------------------------------

    @Test
    fun deleteRemovesRowsAndBothFiles() = runTest {
        val a = repo.add(stored("a"), null).sticker
        repo.add(stored("b"), null)

        val report = repo.delete(listOf(a.id))

        assertEquals(listOf("b"), repo.observeAll().first().map { it.hash })
        assertFalse(files.has(FileArea.STICKERS, "a.png"))
        assertFalse(files.has(FileArea.THUMBS, "a.webp"))
        assertTrue(files.has(FileArea.STICKERS, "b.png"))
        assertEquals(emptyList(), report.failed)
    }

    @Test
    fun deleteKeepsGoingWhenAFileCannotBeDeleted() = runTest {
        val failing = FakeFileStore(failDeletes = setOf("a.png"))
        val repo = StickerRepository(db, failing, clock)
        val a = repo.add(newSticker("a"), null).sticker
        val b = repo.add(newSticker("b"), null).sticker
        failing.write(FileArea.STICKERS, "b.png", byteArrayOf(1))

        val report = repo.delete(listOf(a.id, b.id))

        assertEquals(emptyList(), repo.observeAll().first())
        assertEquals(listOf(FileArea.STICKERS to "a.png"), report.failed)
        assertFalse(failing.has(FileArea.STICKERS, "b.png"))
    }
}
