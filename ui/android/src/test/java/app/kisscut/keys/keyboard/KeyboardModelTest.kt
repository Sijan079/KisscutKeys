package app.kisscut.keys.keyboard

import app.kisscut.keys.common.FakeLibraryBackend
import app.kisscut.keys.shared.core.KeyboardTab
import app.kisscut.keys.shared.core.StickerKind
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class KeyboardModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val backend = FakeLibraryBackend().apply {
        addCollection(1, "Reactions", null)
        addCollection(2, "Work", null)
        addCollection(3, "Memes", 1)
        addCollection(4, "Cats", 1)
        addSticker(10, collectionId = 1)
        addSticker(11, collectionId = 3, animated = true)
        addSticker(12, collectionId = 4)
        addSticker(13, collectionId = 2, favorite = true)
    }

    private fun TestScope.model(): Pair<KeyboardModel, () -> Unit> {
        val scope = TestScope(dispatcher)
        val m = KeyboardModel(backend, scope)
        advanceUntilIdle()
        return m to { scope.cancel() }
    }

    private val KeyboardModel.s get() = state.value

    @Test
    fun emptyLibraryIsFlagged() = runTest(dispatcher) {
        val (m, stop) = model().also { backend.stickers.value = emptyList(); advanceUntilIdle() }
        assertTrue(m.s.loaded)
        assertTrue(m.s.libraryEmpty)
        stop()
    }

    @Test
    fun startsOnFirstNonEmptyTabWhenNothingWasUsed() = runTest(dispatcher) {
        val (m, stop) = model()
        // Recents is empty; Favorites has sticker 13.
        assertEquals(KeyboardTab.Favorites, m.s.tab)
        assertEquals(listOf(13L), m.s.stickers.map { it.id })
        assertFalse(m.s.libraryEmpty)
        stop()
    }

    @Test
    fun startsOnRecentsOnceSomethingWasSent() = runTest(dispatcher) {
        backend.stickers.value = backend.stickers.value.map { if (it.id == 12L) it.copy(lastUsedAt = 5) else it }
        val (m, stop) = model()
        assertEquals(KeyboardTab.Recents, m.s.tab)
        assertEquals(listOf(12L), m.s.stickers.map { it.id })
        stop()
    }

    @Test
    fun collectionTabShowsWholeTreeAndChipsNarrowIt() = runTest(dispatcher) {
        val (m, stop) = model()
        m.selectTab(KeyboardTab.Collection(1, "Reactions"))
        advanceUntilIdle()
        assertEquals(listOf(3L, 4L), m.s.chips.map { it.id })
        assertNull(m.s.chip)
        assertEquals(setOf(10L, 11L, 12L), m.s.stickers.map { it.id }.toSet())

        m.selectChip(4)
        advanceUntilIdle()
        assertEquals(4L, m.s.chip)
        assertEquals(listOf(12L), m.s.stickers.map { it.id })

        m.selectChip(null)
        advanceUntilIdle()
        assertEquals(3, m.s.stickers.size)
        stop()
    }

    @Test
    fun kindFilterKeepsOnlyThatKind() = runTest(dispatcher) {
        val (m, stop) = model()
        m.selectTab(KeyboardTab.Collection(1, "Reactions"))
        m.selectKind(StickerKind.ANIMATED)
        advanceUntilIdle()
        assertEquals(listOf(11L), m.s.stickers.map { it.id })
        m.selectKind(StickerKind.STATIC)
        advanceUntilIdle()
        assertEquals(setOf(10L, 12L), m.s.stickers.map { it.id }.toSet())
        stop()
    }

    @Test
    fun switchingTabsClearsTheChip() = runTest(dispatcher) {
        val (m, stop) = model()
        m.selectTab(KeyboardTab.Collection(1, "Reactions"))
        m.selectChip(3)
        m.selectTab(KeyboardTab.Collection(2, "Work"))
        advanceUntilIdle()
        assertTrue(m.s.chips.isEmpty())
        assertNull(m.s.chip)
        assertEquals(listOf(13L), m.s.stickers.map { it.id })
        stop()
    }

    @Test
    fun unsortedTabAppearsOnlyWithUnsortedStickers() = runTest(dispatcher) {
        val (m, stop) = model()
        assertFalse(KeyboardTab.Unsorted in m.s.tabs)
        backend.addSticker(20, collectionId = null)
        advanceUntilIdle()
        assertEquals(KeyboardTab.Unsorted, m.s.tabs.last())
        m.selectTab(KeyboardTab.Unsorted)
        advanceUntilIdle()
        assertEquals(listOf(20L), m.s.stickers.map { it.id })
        stop()
    }

    @Test
    fun deletedCollectionFallsBackToStartTab() = runTest(dispatcher) {
        val (m, stop) = model()
        m.selectTab(KeyboardTab.Collection(2, "Work"))
        advanceUntilIdle()
        backend.collections.value = backend.collections.value.filter { it.id != 2L }
        backend.stickers.value = backend.stickers.value.filter { it.id != 13L }
        advanceUntilIdle()
        // No favorites or recents left: first non-empty tab is Reactions.
        assertEquals(KeyboardTab.Collection(1, "Reactions"), m.s.tab)
        stop()
    }

    @Test
    fun renamedCollectionKeepsSelection() = runTest(dispatcher) {
        val (m, stop) = model()
        m.selectTab(KeyboardTab.Collection(2, "Work"))
        advanceUntilIdle()
        backend.renameCollection(2, "Office")
        advanceUntilIdle()
        assertEquals(KeyboardTab.Collection(2, "Office"), m.s.tab)
        stop()
    }
}
