package app.kisscut.keys.shared.db

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CollectionDaoTest {
    private val db = inMemoryStickerDatabase()
    private val dao = db.collectionDao()
    private val stickers = db.stickerDao()

    @AfterTest
    fun close() = db.close()

    private suspend fun add(name: String, parentId: Long? = null, sortOrder: Int = 0): Long =
        dao.insert(StickerCollection(name = name, parentId = parentId, sortOrder = sortOrder, createdAt = 0))

    /**
     * Tree used by several tests:
     *   Animals (depth 1)
     *     Cats (2)
     *       Kittens (3)
     *     Dogs (2)
     *   Memes (1)
     */
    private suspend fun tree(): Map<String, Long> {
        val animals = add("Animals", sortOrder = 0)
        val memes = add("Memes", sortOrder = 1)
        val cats = add("Cats", animals, sortOrder = 0)
        val dogs = add("Dogs", animals, sortOrder = 1)
        val kittens = add("Kittens", cats, sortOrder = 0)
        return mapOf("Animals" to animals, "Memes" to memes, "Cats" to cats, "Dogs" to dogs, "Kittens" to kittens)
    }

    @Test
    fun observeChildrenIsOrderedBySortOrderWithDirectCounts() = runTest {
        val t = tree()
        stickers.insert(testSticker("a1", collectionId = t["Animals"]))
        stickers.insert(testSticker("c1", collectionId = t["Cats"]))
        stickers.insert(testSticker("c2", collectionId = t["Cats"]))

        val roots = dao.observeChildren(null).first()
        assertEquals(listOf("Animals", "Memes"), roots.map { it.collection.name })
        assertEquals(listOf(1, 0), roots.map { it.stickerCount })

        val underAnimals = dao.observeChildren(t["Animals"]).first()
        assertEquals(listOf("Cats", "Dogs"), underAnimals.map { it.collection.name })
        assertEquals(listOf(2, 0), underAnimals.map { it.stickerCount })
    }

    @Test
    fun descendantIdsIncludesSelfAndAllLevels() = runTest {
        val t = tree()
        assertEquals(
            setOf(t["Animals"], t["Cats"], t["Dogs"], t["Kittens"]),
            dao.descendantIds(t.getValue("Animals")).toSet(),
        )
        assertEquals(setOf(t["Memes"]), dao.descendantIds(t.getValue("Memes")).toSet())
    }

    @Test
    fun totalStickerCountCountsDescendants() = runTest {
        val t = tree()
        stickers.insert(testSticker("a", collectionId = t["Animals"]))
        stickers.insert(testSticker("c", collectionId = t["Cats"]))
        stickers.insert(testSticker("k1", collectionId = t["Kittens"]))
        stickers.insert(testSticker("k2", collectionId = t["Kittens"]))
        stickers.insert(testSticker("m", collectionId = t["Memes"]))
        stickers.insert(testSticker("loose"))

        assertEquals(4, dao.totalStickerCount(t.getValue("Animals")))
        assertEquals(3, dao.totalStickerCount(t.getValue("Cats")))
        assertEquals(0, dao.totalStickerCount(t.getValue("Dogs")))
    }

    @Test
    fun depthCountsFromRootAsOne() = runTest {
        val t = tree()
        assertEquals(1, dao.depthOf(t.getValue("Animals")))
        assertEquals(2, dao.depthOf(t.getValue("Cats")))
        assertEquals(3, dao.depthOf(t.getValue("Kittens")))
    }

    @Test
    fun subtreeHeightCountsLevelsIncludingSelf() = runTest {
        val t = tree()
        assertEquals(3, dao.subtreeHeight(t.getValue("Animals")))
        assertEquals(2, dao.subtreeHeight(t.getValue("Cats")))
        assertEquals(1, dao.subtreeHeight(t.getValue("Kittens")))
    }

    @Test
    fun renameMoveAndReorder() = runTest {
        val t = tree()
        dao.rename(t.getValue("Dogs"), "Puppies")
        assertEquals("Puppies", dao.getById(t.getValue("Dogs"))?.name)

        dao.move(t.getValue("Dogs"), newParentId = null, newSortOrder = 2)
        assertEquals(listOf("Animals", "Memes", "Puppies"), dao.observeChildren(null).first().map { it.collection.name })

        dao.setSortOrder(t.getValue("Memes"), 0)
        dao.setSortOrder(t.getValue("Animals"), 1)
        assertEquals(listOf("Memes", "Animals", "Puppies"), dao.observeChildren(null).first().map { it.collection.name })
    }

    @Test
    fun maxSortOrderAmongSiblings() = runTest {
        val t = tree()
        assertEquals(1, dao.maxSortOrder(null))
        assertEquals(1, dao.maxSortOrder(t.getValue("Animals")))
        assertNull(dao.maxSortOrder(t.getValue("Dogs")))
    }

    @Test
    fun observeByIdEmitsNullAfterDelete() = runTest {
        val t = tree()
        assertEquals("Memes", dao.observeById(t.getValue("Memes")).first()?.name)
        dao.deleteRow(t.getValue("Memes"))
        assertNull(dao.observeById(t.getValue("Memes")).first())
    }
}
