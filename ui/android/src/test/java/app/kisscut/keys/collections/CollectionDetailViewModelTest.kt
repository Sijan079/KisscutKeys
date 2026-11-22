package app.kisscut.keys.collections

import app.kisscut.keys.common.FakeLibraryBackend
import app.kisscut.keys.shared.core.DeleteMode
import app.kisscut.keys.shared.core.StickerKind
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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CollectionDetailViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val backend = FakeLibraryBackend()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(target: CollectionTarget) =
        CollectionDetailViewModel(backend, target).also { advanceUntilIdle() }

    private val CollectionDetailViewModel.s get() = state.value

    // Reactions(1) > Memes(2) > Classics(3)
    private fun seed() {
        backend.addCollection(1, "Reactions", null)
        backend.addCollection(2, "Memes", 1)
        backend.addCollection(3, "Classics", 2)
        backend.addSticker(10, collectionId = 2)
        backend.addSticker(11, collectionId = 2, animated = true)
        backend.addSticker(12, collectionId = 3)
        backend.addSticker(13, favorite = true)
    }

    @Test
    fun breadcrumbChildrenAndDirectStickers() = runTest(dispatcher) {
        seed()
        val vm = viewModel(CollectionTarget.Collection(2))

        assertEquals("Memes", vm.s.name)
        assertEquals(listOf("Reactions"), vm.s.breadcrumb.map { it.name })
        assertEquals(listOf("Classics"), vm.s.children.map { it.collection.name })
        assertEquals(3, vm.s.totalCount)
        assertEquals(listOf(11L, 10L), vm.s.stickers.map { it.id }, "direct stickers only, newest first")
        assertTrue(vm.s.canAddChild)
        assertEquals(3, vm.s.art.size)
    }

    @Test
    fun deepestLevelCannotHaveChildren() = runTest(dispatcher) {
        seed()
        val vm = viewModel(CollectionTarget.Collection(3))

        assertFalse(vm.s.canAddChild)
        assertEquals(listOf("Reactions", "Memes"), vm.s.breadcrumb.map { it.name })
    }

    @Test
    fun kindChipsFilterButCountsStay() = runTest(dispatcher) {
        seed()
        val vm = viewModel(CollectionTarget.Collection(2))

        vm.setKind(StickerKind.ANIMATED)
        advanceUntilIdle()
        assertEquals(listOf(11L), vm.s.stickers.map { it.id })
        assertEquals(2, vm.s.directCount)
    }

    @Test
    fun emptyCollectionSaysSo() = runTest(dispatcher) {
        backend.addCollection(1, "Empty", null)
        val vm = viewModel(CollectionTarget.Collection(1))

        assertTrue(vm.s.isEmpty)
    }

    @Test
    fun deletingTheCollectionMarksTheScreenGone() = runTest(dispatcher) {
        seed()
        val vm = viewModel(CollectionTarget.Collection(3))

        vm.editor.openDelete(3)
        vm.editor.confirmDelete(DeleteMode.COLLECTION_ONLY)
        advanceUntilIdle()
        assertTrue(vm.s.gone)
    }

    @Test
    fun favoritesAndUnsortedTargets() = runTest(dispatcher) {
        seed()
        backend.addSticker(14)
        val favorites = viewModel(CollectionTarget.Favorites)
        val unsorted = viewModel(CollectionTarget.Unsorted)

        assertNull(favorites.s.name)
        assertEquals(listOf(13L), favorites.s.stickers.map { it.id })
        assertEquals(listOf(14L, 13L), unsorted.s.stickers.map { it.id })
        assertTrue(unsorted.s.children.isEmpty())
    }

    @Test
    fun selectModeWorksInsideACollection() = runTest(dispatcher) {
        seed()
        val vm = viewModel(CollectionTarget.Collection(2))

        vm.selection.start(10)
        vm.selection.moveTo(null)
        advanceUntilIdle()
        assertNull(backend.stickers.value.first { it.id == 10L }.collectionId)
        assertEquals(listOf(11L), vm.s.stickers.map { it.id })
    }

    @Test
    fun titleArtPrefersDieCutStills() = runTest(dispatcher) {
        seed()
        val vm = viewModel(CollectionTarget.Collection(2))

        // Memes: 10 (still), 11 (animated), and 12 from Classics (still): the GIF comes last.
        assertEquals(listOf(10L, 12L, 11L), vm.s.art.map { it.id })
    }
}
