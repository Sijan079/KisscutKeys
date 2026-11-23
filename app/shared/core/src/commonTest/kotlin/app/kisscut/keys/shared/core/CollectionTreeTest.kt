package app.kisscut.keys.shared.core

import app.kisscut.keys.shared.db.StickerCollection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CollectionTreeTest {
    private fun c(id: Long, parentId: Long?, sortOrder: Int = 0) =
        StickerCollection(id = id, name = "c$id", parentId = parentId, sortOrder = sortOrder, createdAt = 0)

    // Cats(1) > Grumpy(2) > Tiny(4); Cats(1) > Happy(3); Dogs(5)
    private val all = listOf(
        c(5, null, sortOrder = 1),
        c(3, 1, sortOrder = 1),
        c(2, 1, sortOrder = 0),
        c(1, null, sortOrder = 0),
        c(4, 2, sortOrder = 0),
    )

    @Test
    fun collapsedTreeShowsTopLevelInSortOrder() {
        val rows = buildCollectionTree(all, expanded = emptySet())
        assertEquals(listOf(1L, 5L), rows.map { it.collection.id })
        assertTrue(rows[0].hasChildren)
        assertFalse(rows[0].isExpanded)
        assertFalse(rows[1].hasChildren)
        assertEquals(listOf(1, 1), rows.map { it.depth })
    }

    @Test
    fun expandingShowsChildrenDepthFirst() {
        val rows = buildCollectionTree(all, expanded = setOf(1L, 2L))
        assertEquals(listOf(1L, 2L, 4L, 3L, 5L), rows.map { it.collection.id })
        assertEquals(listOf(1, 2, 3, 2, 1), rows.map { it.depth })
        assertTrue(rows[0].isExpanded)
    }

    @Test
    fun childrenOfACollapsedParentStayHidden() {
        val rows = buildCollectionTree(all, expanded = setOf(2L))
        assertEquals(listOf(1L, 5L), rows.map { it.collection.id })
    }

    @Test
    fun onlyRowsAboveTheDepthCapCanHoldNewChildren() {
        val rows = buildCollectionTree(all, expanded = setOf(1L, 2L)).associateBy { it.collection.id }
        assertTrue(rows.getValue(1).canHaveChildren)
        assertTrue(rows.getValue(2).canHaveChildren)
        assertFalse(rows.getValue(4).canHaveChildren)
    }

    @Test
    fun ancestorsAreListedFromTheTop() {
        assertEquals(listOf(1L, 2L), collectionAncestors(all, 4))
        assertEquals(emptyList(), collectionAncestors(all, 1))
        assertEquals(emptyList(), collectionAncestors(all, 99))
    }

    @Test
    fun brokenParentLinksDoNotLoop() {
        val looped = listOf(c(1, 2), c(2, 1), c(3, null))
        assertEquals(listOf(3L), buildCollectionTree(looped, expanded = setOf(1L, 2L)).map { it.collection.id })
        assertTrue(collectionAncestors(looped, 2).size <= 2)
    }
}
