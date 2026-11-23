package app.kisscut.keys.shared.core.pack

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CollectionPlanTest {
    private fun c(id: Long, parent: Long?, name: String = "c$id", sort: Int = 0) =
        PackCollection(id, name, parent, sort)

    private fun CollectionPlan.ids() = ordered.map { it.packId }

    @Test
    fun parentsComeBeforeChildrenSiblingsInOrder() {
        val plan = planCollections(listOf(c(3, 1, sort = 1), c(2, 1, sort = 0), c(1, null), c(4, null)))
        assertEquals(listOf(1L, 2L, 3L, 4L), plan.ids())
        assertEquals(1L, plan.ordered.first { it.packId == 3L }.parentPackId)
        assertEquals(0, plan.flattened)
    }

    @Test
    fun deeperThanTheCapIsFoldedIntoTheDeepestAllowedAncestor() {
        // 1 > 2 > 3 > 4 > 5
        val plan = planCollections(listOf(c(1, null), c(2, 1), c(3, 2), c(4, 3), c(5, 4)), maxDepth = 3)
        assertEquals(listOf(1L, 2L, 3L), plan.ids())
        assertEquals(mapOf(4L to 3L, 5L to 3L), plan.foldedInto)
        assertEquals(2, plan.flattened)
        assertEquals(3L, plan.targetOf(5))
        assertEquals(2L, plan.targetOf(2))
    }

    @Test
    fun brokenParentsMakeTopLevelCollections() {
        // 2's parent is missing; 3 and 4 point at each other.
        val plan = planCollections(listOf(c(2, 99), c(3, 4), c(4, 3), c(5, 3)))
        assertEquals(setOf(2L, 3L, 4L), plan.ordered.filter { it.parentPackId == null }.map { it.packId }.toSet())
        assertEquals(3L, plan.ordered.first { it.packId == 5L }.parentPackId)
    }

    @Test
    fun repeatedIdsKeepTheFirstAndBlankNamesGetOne() {
        val plan = planCollections(listOf(c(1, null, "First"), c(1, null, "Second"), c(2, null, "   ")))
        assertEquals(listOf("First", CollectionPlan.UNTITLED), plan.ordered.map { it.name })
    }

    @Test
    fun stickersWithUnknownCollectionsGoToUnsorted() {
        val plan = planCollections(listOf(c(1, null)))
        assertEquals(1L, plan.targetOf(1))
        assertNull(plan.targetOf(7))
        assertNull(plan.targetOf(null))
    }

    @Test
    fun aVeryLongHostileChainIsFlattenedQuickly() {
        val chain = (1L..50_000L).map { c(it, if (it == 1L) null else it - 1) } + c(50_001, 50_000) + c(1, 50_001)
        val plan = planCollections(chain)
        assertEquals(listOf(1L, 2L, 3L), plan.ids())
        assertEquals(49_998, plan.flattened)
    }
}
