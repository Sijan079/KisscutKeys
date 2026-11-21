package com.example.stickers.shared.core

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CollectionRepositoryTest {
    private val db = inMemoryStickerDatabase()
    private val files = FakeFileStore()
    private val repo = CollectionRepository(db, files, TestClock())

    @AfterTest
    fun close() = db.close()

    private suspend fun names(parentId: Long?) = repo.observeChildren(parentId).first().map { it.collection.name }

    // --- create / rename ---------------------------------------------------------------------

    @Test
    fun createTrimsNameAndAppendsAfterSiblings() = runTest {
        repo.create("Cats", parentId = null)
        val dogs = repo.create("  Dogs \n", parentId = null)

        assertEquals("Dogs", dogs.name)
        assertEquals(listOf("Cats", "Dogs"), names(null))
    }

    @Test
    fun createRejectsBlankName() = runTest {
        assertFailsWith<CollectionRuleException.BlankName> { repo.create("   ", parentId = null) }
        assertEquals(emptyList(), names(null))
    }

    @Test
    fun createRejectsMissingParent() = runTest {
        assertFailsWith<CollectionRuleException.NotFound> { repo.create("Orphan", parentId = 999) }
    }

    @Test
    fun createAllowsDepthThreeAndRejectsDepthFour() = runTest {
        val l1 = repo.create("L1", null)
        val l2 = repo.create("L2", l1.id)
        val l3 = repo.create("L3", l2.id)

        assertFailsWith<CollectionRuleException.TooDeep> { repo.create("L4", l3.id) }
        assertEquals(emptyList(), names(l3.id))
    }

    // --- move / reorder -----------------------------------------------------------------------

    @Test
    fun moveAppendsUnderNewParent() = runTest {
        val animals = repo.create("Animals", null)
        repo.create("Birds", animals.id)
        val cats = repo.create("Cats", null)

        repo.move(cats.id, newParentId = animals.id)

        assertEquals(listOf("Birds", "Cats"), names(animals.id))
        assertEquals(listOf("Animals"), names(null))
    }

    @Test
    fun moveToTopLevel() = runTest {
        val animals = repo.create("Animals", null)
        val cats = repo.create("Cats", animals.id)

        repo.move(cats.id, newParentId = null)

        assertEquals(listOf("Animals", "Cats"), names(null))
    }

    @Test
    fun moveRejectsMovingIntoItself() = runTest {
        val a = repo.create("A", null)
        assertFailsWith<CollectionRuleException.Cycle> { repo.move(a.id, newParentId = a.id) }
        assertEquals(listOf("A"), names(null))
    }

    @Test
    fun moveRejectsMovingIntoOwnDescendantAndLeavesTreeUnchanged() = runTest {
        val a = repo.create("A", null)
        val b = repo.create("B", a.id)
        val c = repo.create("C", b.id)

        assertFailsWith<CollectionRuleException.Cycle> { repo.move(a.id, newParentId = c.id) }

        assertEquals(listOf("A"), names(null))
        assertEquals(listOf("B"), names(a.id))
        assertEquals(listOf("C"), names(b.id))
    }

    @Test
    fun moveCountsTheWholeSubtreeAgainstTheDepthCap() = runTest {
        // Two-level subtree X > Y.
        val x = repo.create("X", null)
        repo.create("Y", x.id)
        // Target parents at depth 1 and depth 2.
        val p1 = repo.create("P1", null)
        val p2 = repo.create("P2", p1.id)

        // Under depth 2 the subtree would reach depth 4: rejected, nothing changes.
        assertFailsWith<CollectionRuleException.TooDeep> { repo.move(x.id, newParentId = p2.id) }
        assertEquals(emptyList(), names(p2.id))
        assertEquals(listOf("X", "P1"), names(null))

        // Under depth 1 it reaches exactly depth 3: allowed.
        repo.move(x.id, newParentId = p1.id)
        assertEquals(listOf("P2", "X"), names(p1.id))
    }

    @Test
    fun moveRejectsMissingCollectionOrParent() = runTest {
        val a = repo.create("A", null)
        assertFailsWith<CollectionRuleException.NotFound> { repo.move(999, newParentId = null) }
        assertFailsWith<CollectionRuleException.NotFound> { repo.move(a.id, newParentId = 999) }
    }

    @Test
    fun reorderSetsSiblingOrder() = runTest {
        val a = repo.create("A", null)
        val b = repo.create("B", null)
        val c = repo.create("C", null)

        repo.reorder(parentId = null, orderedIds = listOf(c.id, a.id, b.id))

        assertEquals(listOf("C", "A", "B"), names(null))
    }

    @Test
    fun reorderRejectsIdsThatAreNotExactlyTheSiblings() = runTest {
        val a = repo.create("A", null)
        val b = repo.create("B", null)
        val child = repo.create("Child", a.id)

        assertFailsWith<IllegalArgumentException> { repo.reorder(null, listOf(b.id)) }
        assertFailsWith<IllegalArgumentException> { repo.reorder(null, listOf(b.id, a.id, child.id)) }
        assertEquals(listOf("A", "B"), names(null))
    }

    // --- delete -------------------------------------------------------------------------------

    private val stickers = db.stickerDao()

    /** Inserts a sticker row and writes its two files into the fake store. */
    private suspend fun sticker(hash: String, collectionId: Long?): Long {
        files.write(FileArea.STICKERS, "$hash.png", byteArrayOf(1))
        files.write(FileArea.THUMBS, "$hash.webp", byteArrayOf(2))
        return stickers.insert(
            com.example.stickers.shared.db.Sticker(
                filePath = "$hash.png", thumbPath = "$hash.webp", mimeType = "image/png",
                isAnimated = false, hasAlpha = true, hash = hash, collectionId = collectionId, addedAt = 0,
            ),
        )
    }

    private suspend fun collectionOf(hash: String) = stickers.findByHash(hash)?.collectionId

    @Test
    fun deleteCollectionOnlyAtMidLevelMovesChildrenAndStickersUp() = runTest {
        val a = repo.create("A", null)
        repo.create("A-first", a.id)
        val b = repo.create("B", a.id)
        repo.create("C", b.id)
        repo.create("D", b.id)
        sticker("inB1", b.id)
        sticker("inB2", b.id)

        repo.delete(b.id, DeleteMode.COLLECTION_ONLY)

        assertEquals(listOf("A-first", "C", "D"), names(a.id), "children appended under the parent, order kept")
        assertEquals(a.id, collectionOf("inB1"))
        assertEquals(a.id, collectionOf("inB2"))
        assertEquals(2, repo.totalStickerCount(a.id))
        assertEquals(null, repo.observeById(b.id).first())
        assertEquals(true, files.has(FileArea.STICKERS, "inB1.png"), "no files deleted")
    }

    @Test
    fun deleteCollectionOnlyAtTopLevelSendsStickersToUnsorted() = runTest {
        val a = repo.create("A", null)
        repo.create("Other", null)
        val b = repo.create("B", a.id)
        sticker("inA", a.id)
        sticker("inB", b.id)

        repo.delete(a.id, DeleteMode.COLLECTION_ONLY)

        assertEquals(listOf("Other", "B"), names(null))
        assertEquals(null, collectionOf("inA"))
        assertEquals(b.id, collectionOf("inB"))
    }

    @Test
    fun deleteEverythingRemovesSubtreeStickersAndFilesOnly() = runTest {
        val a = repo.create("A", null)
        val b = repo.create("B", a.id)
        val c = repo.create("C", b.id)
        val keep = repo.create("Keep", null)
        sticker("inA", a.id)
        sticker("inB", b.id)
        sticker("inC", c.id)
        sticker("inKeep", keep.id)
        sticker("loose", null)

        val report = repo.delete(a.id, DeleteMode.EVERYTHING)

        assertEquals(listOf("Keep"), names(null))
        for (gone in listOf("inA", "inB", "inC")) {
            assertEquals(null, stickers.findByHash(gone), "$gone row deleted, not left in Unsorted")
            assertEquals(false, files.has(FileArea.STICKERS, "$gone.png"))
            assertEquals(false, files.has(FileArea.THUMBS, "$gone.webp"))
        }
        for (kept in listOf("inKeep", "loose")) {
            assertEquals(true, files.has(FileArea.STICKERS, "$kept.png"))
        }
        assertEquals(keep.id, collectionOf("inKeep"))
        assertEquals(emptyList(), report.failed)
    }

    @Test
    fun deleteEverythingKeepsGoingWhenAFileCannotBeDeleted() = runTest {
        val failing = FakeFileStore(failDeletes = setOf("stuck.png"))
        val repo = CollectionRepository(db, failing, TestClock())
        val a = repo.create("A", null)
        failing.write(FileArea.STICKERS, "stuck.png", byteArrayOf(1))
        failing.write(FileArea.THUMBS, "stuck.webp", byteArrayOf(1))
        failing.write(FileArea.STICKERS, "ok.png", byteArrayOf(1))
        stickers.insert(
            com.example.stickers.shared.db.Sticker(
                filePath = "stuck.png", thumbPath = "stuck.webp", mimeType = "image/png",
                isAnimated = false, hasAlpha = true, hash = "stuck", collectionId = a.id, addedAt = 0,
            ),
        )
        stickers.insert(
            com.example.stickers.shared.db.Sticker(
                filePath = "ok.png", thumbPath = "ok.webp", mimeType = "image/png",
                isAnimated = false, hasAlpha = true, hash = "ok", collectionId = a.id, addedAt = 0,
            ),
        )

        val report = repo.delete(a.id, DeleteMode.EVERYTHING)

        assertEquals(listOf(FileArea.STICKERS to "stuck.png"), report.failed)
        assertEquals(null, stickers.findByHash("stuck"))
        assertEquals(null, stickers.findByHash("ok"))
        assertEquals(false, failing.has(FileArea.STICKERS, "ok.png"))
        assertEquals(false, failing.has(FileArea.THUMBS, "stuck.webp"))
    }

    @Test
    fun deleteRejectsMissingCollection() = runTest {
        assertFailsWith<CollectionRuleException.NotFound> { repo.delete(999, DeleteMode.EVERYTHING) }
    }

    @Test
    fun renameTrimsAndRejectsBlank() = runTest {
        val c = repo.create("Cats", null)

        repo.rename(c.id, "  Kittens  ")
        assertEquals("Kittens", repo.observeById(c.id).first()?.name)

        assertFailsWith<CollectionRuleException.BlankName> { repo.rename(c.id, " ") }
        assertEquals("Kittens", repo.observeById(c.id).first()?.name)
    }

    // --- observeAll (Save sheet picker) -------------------------------------------------------

    @Test
    fun observeAllReturnsEveryLevelAndUpdates() = runTest {
        val cats = repo.create("Cats", parentId = null)
        val grumpy = repo.create("Grumpy", parentId = cats.id)
        repo.create("Tiny", parentId = grumpy.id)

        assertEquals(setOf("Cats", "Grumpy", "Tiny"), repo.observeAll().first().map { it.name }.toSet())

        repo.create("Dogs", parentId = null)
        assertEquals(4, repo.observeAll().first().size)
    }

    // --- observeSummaries (Collections list) --------------------------------------------------

    @Test
    fun summariesCountDescendantsAndPreviewStickers() = runTest {
        val stickers = StickerRepository(db, files, TestClock())
        val cats = repo.create("Cats", parentId = null)
        val grumpy = repo.create("Grumpy", parentId = cats.id)
        stickers.add(newSticker("a"), cats.id)
        stickers.add(newSticker("b"), grumpy.id)
        stickers.add(newSticker("c"), grumpy.id)

        val s = repo.observeSummaries().first()
        assertEquals(3, s.getValue(cats.id).totalCount)
        assertEquals(1, s.getValue(cats.id).childCount)
        assertEquals(listOf("a", "c", "b"), s.getValue(cats.id).previews.map { it.hash })
        assertEquals(2, s.getValue(grumpy.id).depth)
    }
}
