package app.kisscut.keys.library

import app.kisscut.keys.common.FakeLibraryBackend
import app.kisscut.keys.common.StickerDialog
import app.kisscut.keys.shared.core.HeroReason
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
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val backend = FakeLibraryBackend()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel() = LibraryViewModel(backend).also { advanceUntilIdle() }

    private val LibraryViewModel.s get() = state.value

    @Test
    fun emptyLibraryIsFlaggedAfterLoading() = runTest(dispatcher) {
        val vm = LibraryViewModel(backend)
        assertFalse(vm.s.isLibraryEmpty, "not before the first load")
        advanceUntilIdle()
        assertTrue(vm.s.isLibraryEmpty)
    }

    @Test
    fun showsEveryStickerNewestFirstWithTheHeroSeparate() = runTest(dispatcher) {
        for (i in 1L..5L) backend.addSticker(i)
        val vm = viewModel()

        assertEquals(5, vm.s.totalCount)
        assertEquals(5L, vm.s.hero?.sticker?.id)
        assertEquals(HeroReason.NEWEST, vm.s.hero?.reason)
        assertEquals(listOf(4L, 3L, 2L, 1L), vm.s.stickers.map { it.id })
    }

    @Test
    fun heroIsTheMostUsedOnceStickersAreUsed() = runTest(dispatcher) {
        for (i in 1L..4L) backend.addSticker(i, useCount = if (i == 2L) 9 else 0, lastUsedAt = if (i == 2L) 1 else null)
        val vm = viewModel()

        assertEquals(2L, vm.s.hero?.sticker?.id)
        assertEquals(HeroReason.MOST_USED, vm.s.hero?.reason)
    }

    @Test
    fun shortListsHaveNoHero() = runTest(dispatcher) {
        for (i in 1L..3L) backend.addSticker(i)
        val vm = viewModel()

        assertNull(vm.s.hero)
        assertEquals(3, vm.s.stickers.size)
    }

    @Test
    fun railListsSystemFiltersThenTopLevelCollections() = runTest(dispatcher) {
        backend.addCollection(1, "Reactions", null)
        backend.addCollection(2, "Memes", 1)
        backend.addCollection(3, "Work", null)
        backend.addSticker(10, collectionId = 2)
        val vm = viewModel()

        val rail = vm.s.rail
        assertEquals(
            listOf(LibraryFilter.All, LibraryFilter.Favorites, LibraryFilter.Recents) +
                listOf(LibraryFilter.InCollection(1), LibraryFilter.InCollection(3)),
            rail.map { it.filter },
        )
        assertEquals("Reactions", rail[3].name)
        assertEquals(10L, rail[3].preview?.id, "preview comes from a subcollection when the top is empty")
    }

    @Test
    fun favoritesFilterShowsOnlyFavorites() = runTest(dispatcher) {
        backend.addSticker(1, favorite = true)
        backend.addSticker(2)
        val vm = viewModel()

        vm.setFilter(LibraryFilter.Favorites)
        advanceUntilIdle()
        assertEquals(listOf(1L), vm.s.shown.map { it.id })
        assertEquals(2, vm.s.totalCount, "the title count stays the whole library")
    }

    @Test
    fun recentsFilterShowsUsedStickersMostRecentFirst() = runTest(dispatcher) {
        backend.addSticker(1, lastUsedAt = 50)
        backend.addSticker(2)
        backend.addSticker(3, lastUsedAt = 90)
        val vm = viewModel()

        vm.setFilter(LibraryFilter.Recents)
        advanceUntilIdle()
        assertEquals(listOf(3L, 1L), vm.s.shown.map { it.id })
    }

    @Test
    fun collectionFilterIncludesSubcollections() = runTest(dispatcher) {
        backend.addCollection(1, "Reactions", null)
        backend.addCollection(2, "Memes", 1)
        backend.addSticker(1, collectionId = 1)
        backend.addSticker(2, collectionId = 2)
        backend.addSticker(3)
        val vm = viewModel()

        vm.setFilter(LibraryFilter.InCollection(1))
        advanceUntilIdle()
        assertEquals(setOf(1L, 2L), vm.s.shown.map { it.id }.toSet())
    }

    @Test
    fun deletedCollectionFilterFallsBackToAll() = runTest(dispatcher) {
        backend.addCollection(1, "Reactions", null)
        backend.addSticker(1)
        val vm = viewModel()
        vm.setFilter(LibraryFilter.InCollection(1))
        advanceUntilIdle()

        backend.collections.value = emptyList()
        advanceUntilIdle()
        assertEquals(LibraryFilter.All, vm.s.filter)
        assertEquals(listOf(1L), vm.s.shown.map { it.id })
    }

    @Test
    fun kindChipsFilterStaticAndAnimated() = runTest(dispatcher) {
        backend.addSticker(1)
        backend.addSticker(2, animated = true)
        val vm = viewModel()

        vm.setKind(StickerKind.ANIMATED)
        advanceUntilIdle()
        assertEquals(listOf(2L), vm.s.shown.map { it.id })
        vm.setKind(StickerKind.STATIC)
        advanceUntilIdle()
        assertEquals(listOf(1L), vm.s.shown.map { it.id })
    }

    // --- sections ------------------------------------------------------------------------------

    /** 20 stickers; 7, 3 and 12 used (most to least). */
    private fun addSectionLibrary() {
        val uses = mapOf(7L to 5, 3L to 4, 12L to 2)
        for (i in 1L..20L) backend.addSticker(i, useCount = uses[i] ?: 0, lastUsedAt = uses[i]?.let { i })
    }

    @Test
    fun allViewShowsSectionsWithTheMostUsedAsTheHeroAndEveryStickerBelow() = runTest(dispatcher) {
        addSectionLibrary()
        val vm = LibraryViewModel(backend, seed = 5).also { advanceUntilIdle() }

        val sections = vm.s.sections!!
        assertEquals(listOf(7L, 3L, 12L), sections.mostUsed.map { it.id })
        assertEquals(7L, vm.s.hero?.sticker?.id)
        assertEquals(HeroReason.MOST_USED, vm.s.hero?.reason)
        assertEquals((20L downTo 1L).toList(), vm.s.stickers.map { it.id }, "All stickers keeps every sticker")
        assertEquals(20, vm.s.shown.size)
        assertEquals(6, sections.new.size)
        assertEquals(6, sections.leastUsed.size)
    }

    @Test
    fun theSameSeedPicksTheSameLeastUsed() = runTest(dispatcher) {
        addSectionLibrary()
        val a = LibraryViewModel(backend, seed = 9).also { advanceUntilIdle() }
        val b = LibraryViewModel(backend, seed = 9).also { advanceUntilIdle() }
        assertEquals(a.s.sections!!.leastUsed, b.s.sections!!.leastUsed)
    }

    @Test
    fun otherFiltersAndSmallLibrariesHaveNoSections() = runTest(dispatcher) {
        addSectionLibrary()
        val vm = viewModel()
        vm.setFilter(LibraryFilter.Recents)
        advanceUntilIdle()
        assertNull(vm.s.sections)

        vm.setFilter(LibraryFilter.All)
        vm.setKind(StickerKind.ANIMATED) // none animated: too few for sections
        advanceUntilIdle()
        assertNull(vm.s.sections)
    }

    @Test
    fun selectModeHidesSectionsAndSelectAllTakesEveryStickerOnce() = runTest(dispatcher) {
        addSectionLibrary()
        val vm = viewModel()

        vm.selection.start(7)
        advanceUntilIdle()
        assertNull(vm.s.sections)
        assertNull(vm.s.hero)

        vm.selection.selectAll()
        advanceUntilIdle()
        assertEquals(20, vm.s.selection.count)
    }

    // --- select mode ---------------------------------------------------------------------------

    @Test
    fun longPressStartsSelectModeAndHidesTheHero() = runTest(dispatcher) {
        for (i in 1L..5L) backend.addSticker(i)
        val vm = viewModel()

        vm.selection.start(3)
        advanceUntilIdle()
        assertTrue(vm.s.selection.active)
        assertEquals(1, vm.s.selection.count)
        assertNull(vm.s.hero)
        assertEquals(5, vm.s.stickers.size)
    }

    @Test
    fun selectCountsTogglesAndSelectAll() = runTest(dispatcher) {
        for (i in 1L..5L) backend.addSticker(i)
        val vm = viewModel()

        vm.selection.start(1)
        vm.selection.toggle(2)
        vm.selection.toggle(3)
        vm.selection.toggle(1)
        advanceUntilIdle()
        assertEquals(setOf(2L, 3L), vm.s.selection.selected)

        vm.selection.selectAll()
        advanceUntilIdle()
        assertEquals(5, vm.s.selection.count)

        vm.selection.exit()
        advanceUntilIdle()
        assertFalse(vm.s.selection.active)
    }

    @Test
    fun selectAllOnlyTakesWhatIsShown() = runTest(dispatcher) {
        backend.addSticker(1, favorite = true)
        backend.addSticker(2)
        val vm = viewModel()
        vm.setFilter(LibraryFilter.Favorites)
        advanceUntilIdle()

        vm.selection.selectAll()
        advanceUntilIdle()
        assertEquals(setOf(1L), vm.s.selection.selected)
    }

    @Test
    fun favoriteFavoritesAllUnlessAllAreFavorites() = runTest(dispatcher) {
        backend.addSticker(1, favorite = true)
        backend.addSticker(2)
        val vm = viewModel()

        vm.selection.start(1)
        vm.selection.toggle(2)
        vm.selection.favorite()
        advanceUntilIdle()
        assertTrue(backend.stickers.value.all { it.isFavorite })
        assertFalse(vm.s.selection.active, "select mode closes after an action")

        vm.selection.start(1)
        vm.selection.toggle(2)
        vm.selection.favorite()
        advanceUntilIdle()
        assertTrue(backend.stickers.value.none { it.isFavorite })
    }

    @Test
    fun moveOffersUnsortedAndEveryCollectionThenMoves() = runTest(dispatcher) {
        backend.addCollection(1, "Reactions", null)
        backend.addCollection(2, "Memes", 1)
        backend.addSticker(5)
        val vm = viewModel()

        vm.selection.start(5)
        vm.selection.openMove()
        advanceUntilIdle()
        val dialog = assertIs<StickerDialog.Move>(vm.s.dialog)
        assertEquals(listOf(null, 1L, 2L), dialog.targets.map { it.collectionId })
        assertEquals(listOf(1, 1, 2), dialog.targets.map { it.depth })

        vm.selection.moveTo(2)
        advanceUntilIdle()
        assertEquals(2L, backend.stickers.value.single().collectionId)
        assertNull(vm.s.dialog)
    }

    @Test
    fun deleteAsksThenRemoves() = runTest(dispatcher) {
        backend.addSticker(1)
        backend.addSticker(2)
        val vm = viewModel()

        vm.selection.start(1)
        vm.selection.openDelete()
        advanceUntilIdle()
        assertEquals(1, assertIs<StickerDialog.Delete>(vm.s.dialog).count)

        vm.selection.delete()
        advanceUntilIdle()
        assertEquals(listOf(2L), backend.stickers.value.map { it.id })
    }

    @Test
    fun actionsNeedASelection() = runTest(dispatcher) {
        backend.addSticker(1)
        val vm = viewModel()

        vm.selection.start()
        vm.selection.openDelete()
        vm.selection.openMove()
        advanceUntilIdle()
        assertNull(vm.s.dialog)
    }

    @Test
    fun stickersRemovedElsewhereLeaveTheSelection() = runTest(dispatcher) {
        backend.addSticker(1)
        backend.addSticker(2)
        val vm = viewModel()
        vm.selection.start(1)
        vm.selection.toggle(2)
        advanceUntilIdle()

        backend.stickers.value = backend.stickers.value.filter { it.id != 1L }
        advanceUntilIdle()
        assertEquals(setOf(2L), vm.s.selection.selected)
    }
}
