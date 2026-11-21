package com.example.stickers.collections

import com.example.stickers.common.CollectionDialog
import com.example.stickers.common.FakeLibraryBackend
import com.example.stickers.shared.core.DeleteMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CollectionsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val backend = FakeLibraryBackend()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel() = CollectionsViewModel(backend).also { advanceUntilIdle() }

    // Reactions(1) > Memes(2) > Classics(4); Reactions(1) > Cats(3); Work(5)
    private fun seedTree() {
        backend.addCollection(1, "Reactions", null)
        backend.addCollection(2, "Memes", 1)
        backend.addCollection(3, "Cats", 1)
        backend.addCollection(4, "Classics", 2)
        backend.addCollection(5, "Work", null)
        backend.addSticker(10, collectionId = 1)
        backend.addSticker(11, collectionId = 4)
        backend.addSticker(12, collectionId = 4)
        backend.addSticker(13, favorite = true)
        backend.addSticker(14)
    }

    private val CollectionsViewModel.s get() = state.value

    @Test
    fun topLevelRowsWithTotalsAndSystemCounts() = runTest(dispatcher) {
        seedTree()
        val vm = viewModel()

        assertEquals(listOf("Reactions", "Work"), vm.s.rows.map { it.summary.collection.name })
        val reactions = vm.s.rows.first()
        assertEquals(3, reactions.summary.totalCount)
        assertEquals(2, reactions.summary.childCount)
        assertTrue(reactions.hasChildren)
        assertEquals(1, vm.s.favoritesCount)
        assertEquals(2, vm.s.unsortedCount)
    }

    @Test
    fun expandShowsChildrenIndentedByDepth() = runTest(dispatcher) {
        seedTree()
        val vm = viewModel()

        vm.toggleExpanded(1)
        vm.toggleExpanded(2)
        advanceUntilIdle()
        assertEquals(
            listOf("Reactions", "Memes", "Classics", "Cats", "Work"),
            vm.s.rows.map { it.summary.collection.name },
        )
        assertEquals(listOf(1, 2, 3, 2, 1), vm.s.rows.map { it.summary.depth })

        vm.toggleExpanded(1)
        advanceUntilIdle()
        assertEquals(listOf("Reactions", "Work"), vm.s.rows.map { it.summary.collection.name })
    }

    @Test
    fun newCollectionAtTopLevelOrInsideAndShowsIt() = runTest(dispatcher) {
        seedTree()
        val vm = viewModel()

        vm.editor.openNew(parentId = 2)
        vm.editor.setName("  Fresh ")
        advanceUntilIdle()
        assertEquals("Memes", assertIs<CollectionDialog.Name>(vm.s.dialog).parentName)
        vm.editor.confirmName()
        advanceUntilIdle()

        assertNull(vm.s.dialog)
        assertTrue("Fresh" in vm.s.rows.map { it.summary.collection.name }, "parents expanded to show it")
    }

    @Test
    fun blankNameCannotBeConfirmed() = runTest(dispatcher) {
        val vm = viewModel()
        vm.editor.openNew(null)
        vm.editor.setName("   ")
        vm.editor.confirmName()
        advanceUntilIdle()

        assertEquals(false, assertIs<CollectionDialog.Name>(vm.s.dialog).canConfirm)
        assertTrue(backend.collections.value.isEmpty())
    }

    @Test
    fun newCollectionPastTheDepthCapIsRefusedAndStaysOpen() = runTest(dispatcher) {
        seedTree()
        val vm = viewModel()

        assertEquals(false, vm.editor.canHaveChildren(4))
        vm.editor.openNew(parentId = 4)
        vm.editor.setName("Too deep")
        vm.editor.confirmName()
        advanceUntilIdle()

        assertTrue(assertIs<CollectionDialog.Name>(vm.s.dialog).refused)
        assertTrue(backend.collections.value.none { it.name == "Too deep" })
    }

    @Test
    fun longPressMenuThenRename() = runTest(dispatcher) {
        seedTree()
        val vm = viewModel()

        vm.editor.openMenu(5)
        advanceUntilIdle()
        assertEquals("Work", assertIs<CollectionDialog.Menu>(vm.s.dialog).name)

        vm.editor.openRename(5)
        advanceUntilIdle()
        assertEquals("Work", assertIs<CollectionDialog.Name>(vm.s.dialog).text)
        vm.editor.setName("Office")
        vm.editor.confirmName()
        advanceUntilIdle()
        assertEquals("Office", backend.collections.value.first { it.id == 5L }.name)
    }

    @Test
    fun moveOnlyOffersAllowedParents() = runTest(dispatcher) {
        seedTree()
        val vm = viewModel()

        // Memes (2) has a child, so it can go to the top level, or under Work (depth 1) but not under
        // Cats (depth 2: 2 + 2 > 3), itself, its child, or its current parent.
        vm.editor.openMove(2)
        advanceUntilIdle()
        val move = assertIs<CollectionDialog.Move>(vm.s.dialog)
        assertEquals(listOf(null, 5L), move.targets.map { it.collectionId })

        vm.editor.confirmMove(5)
        advanceUntilIdle()
        assertEquals(5L, backend.collections.value.first { it.id == 2L }.parentId)
    }

    @Test
    fun deleteShowsCountsAndHonoursTheMode() = runTest(dispatcher) {
        seedTree()
        val vm = viewModel()

        vm.editor.openDelete(2)
        advanceUntilIdle()
        val d = assertIs<CollectionDialog.Delete>(vm.s.dialog)
        assertEquals(2, d.totalCount)
        assertEquals(1, d.childCount)

        vm.editor.confirmDelete(DeleteMode.COLLECTION_ONLY)
        advanceUntilIdle()
        assertEquals(1L, backend.collections.value.first { it.id == 4L }.parentId, "child moved up")
        assertEquals(5, backend.stickers.value.size, "nothing lost")

        vm.editor.openDelete(1)
        vm.editor.confirmDelete(DeleteMode.EVERYTHING)
        advanceUntilIdle()
        assertEquals(listOf("Work"), backend.collections.value.map { it.name })
        assertEquals(setOf(13L, 14L), backend.stickers.value.map { it.id }.toSet())
    }
}
