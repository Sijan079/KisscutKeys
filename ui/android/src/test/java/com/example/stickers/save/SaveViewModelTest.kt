package com.example.stickers.save

import com.example.stickers.shared.core.ImageInfo
import com.example.stickers.shared.core.ImageType
import com.example.stickers.shared.core.ImportResult
import com.example.stickers.shared.core.RejectReason
import com.example.stickers.shared.db.Sticker
import com.example.stickers.shared.db.StickerCollection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
class SaveViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val backend = FakeSaveBackend()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(vararg uris: String): SaveViewModel =
        SaveViewModel(backend, uris.toList()).also { advanceUntilIdle() }

    // Reactions(1) > Memes(2) > Old(4); Reactions(1) > Cats(3); Work(5)
    private fun seedTree() {
        backend.add(1, "Reactions", null)
        backend.add(2, "Memes", 1)
        backend.add(3, "Cats", 1)
        backend.add(4, "Old", 2)
        backend.add(5, "Work", null)
    }

    // --- items and preview ---------------------------------------------------------------------

    @Test
    fun describesEveryIncomingItem() = runTest(dispatcher) {
        backend.details["content://a"] = ItemDetails(ImageInfo(ImageType.GIF, 512, 512, true, false), 1_200_000)

        val vm = viewModel("content://a", "content://b")

        val items = vm.state.value.items
        assertEquals(listOf("content://a", "content://b"), items.map { it.uri })
        assertEquals(ImageType.GIF, assertIs<ItemPreview.Ready>(items[0].preview).details.info.type)
        assertIs<ItemPreview.Unsupported>(items[1].preview)
    }

    @Test
    fun startsInUnsortedWithTopLevelTree() = runTest(dispatcher) {
        seedTree()

        val vm = viewModel("content://a")

        with(vm.state.value) {
            assertNull(selectedId)
            assertEquals(emptyList(), selectedPath)
            assertEquals(listOf("Reactions", "Work"), tree.map { it.collection.name })
            assertEquals(SavePhase.Picking, phase)
        }
    }

    // --- nested picker -------------------------------------------------------------------------

    @Test
    fun expandingAndSelectingANestedCollection() = runTest(dispatcher) {
        seedTree()
        val vm = viewModel("content://a")

        vm.toggleExpanded(1)
        vm.select(2)
        advanceUntilIdle()

        with(vm.state.value) {
            assertEquals(listOf("Reactions", "Memes", "Cats", "Work"), tree.map { it.collection.name })
            assertEquals(2L, selectedId)
            assertEquals(listOf("Reactions", "Memes"), selectedPath)
        }
    }

    @Test
    fun collapsingHidesChildren() = runTest(dispatcher) {
        seedTree()
        val vm = viewModel("content://a")

        vm.toggleExpanded(1)
        vm.toggleExpanded(1)
        advanceUntilIdle()

        assertEquals(listOf("Reactions", "Work"), vm.state.value.tree.map { it.collection.name })
    }

    // --- new collection ------------------------------------------------------------------------

    @Test
    fun newCollectionGoesInsideTheSelectedOneAndBecomesSelected() = runTest(dispatcher) {
        seedTree()
        val vm = viewModel("content://a")
        vm.toggleExpanded(1)
        vm.select(1)
        advanceUntilIdle()
        assertEquals("Reactions", vm.state.value.newCollectionParentName)

        vm.openNewCollection()
        vm.setNewCollectionName("  Thank you ")
        vm.confirmNewCollection()
        advanceUntilIdle()

        val created = backend.collections.value.single { it.name == "Thank you" }
        assertEquals(1L, created.parentId)
        with(vm.state.value) {
            assertNull(newCollection)
            assertEquals(created.id, selectedId)
            assertEquals(listOf("Reactions", "Thank you"), selectedPath)
            assertTrue(tree.any { it.collection.id == created.id }, "visible in the tree")
        }
    }

    @Test
    fun newCollectionFromUnsortedIsTopLevel() = runTest(dispatcher) {
        val vm = viewModel("content://a")
        assertNull(vm.state.value.newCollectionParentName)

        vm.openNewCollection()
        vm.setNewCollectionName("Cats")
        vm.confirmNewCollection()
        advanceUntilIdle()

        assertNull(backend.collections.value.single().parentId)
    }

    @Test
    fun newCollectionAtTheDepthCapGoesBesideTheSelectedOne() = runTest(dispatcher) {
        seedTree()
        val vm = viewModel("content://a")
        vm.toggleExpanded(1)
        vm.toggleExpanded(2)
        vm.select(4) // Reactions > Memes > Old: depth 3
        advanceUntilIdle()
        assertEquals("Memes", vm.state.value.newCollectionParentName)

        vm.openNewCollection()
        vm.setNewCollectionName("New")
        vm.confirmNewCollection()
        advanceUntilIdle()

        assertEquals(2L, backend.collections.value.single { it.name == "New" }.parentId)
    }

    @Test
    fun blankNameCannotBeConfirmed() = runTest(dispatcher) {
        val vm = viewModel("content://a")

        vm.openNewCollection()
        vm.setNewCollectionName("   ")
        assertEquals(false, vm.state.value.newCollection?.canConfirm)
        vm.confirmNewCollection()
        advanceUntilIdle()

        assertEquals(emptyList(), backend.collections.value)
        assertEquals("   ", vm.state.value.newCollection?.name, "dialog stays open")
    }

    @Test
    fun dismissingNewCollectionCreatesNothing() = runTest(dispatcher) {
        val vm = viewModel("content://a")

        vm.openNewCollection()
        vm.setNewCollectionName("Cats")
        vm.dismissNewCollection()
        advanceUntilIdle()

        assertNull(vm.state.value.newCollection)
        assertEquals(emptyList(), backend.collections.value)
    }

    // --- save ----------------------------------------------------------------------------------

    @Test
    fun saveImportsEveryItemIntoTheSelectedCollectionAndSummarises() = runTest(dispatcher) {
        seedTree()
        backend.results["content://dup"] = ImportResult.Duplicate(sticker(9))
        backend.results["content://bad"] = ImportResult.Rejected(RejectReason.UNSUPPORTED_TYPE)
        val vm = viewModel("content://a", "content://b", "content://dup", "content://bad")
        vm.toggleExpanded(1)
        vm.select(2)

        vm.save()
        advanceUntilIdle()

        assertEquals<List<Pair<String, Long?>>>(
            listOf("content://a", "content://b", "content://dup", "content://bad").map { it to 2L },
            backend.imported,
        )
        assertEquals(SavePhase.Done(SaveSummary(added = 2, duplicates = 1, rejected = 1)), vm.state.value.phase)
    }

    @Test
    fun saveTwiceDoesNotImportTwice() = runTest(dispatcher) {
        val vm = viewModel("content://a")

        vm.save()
        vm.save()
        advanceUntilIdle()

        assertEquals<List<Pair<String, Long?>>>(listOf("content://a" to null), backend.imported)
    }
}

private fun sticker(id: Long) = Sticker(
    id = id,
    filePath = "f$id.png",
    thumbPath = "f$id.webp",
    mimeType = "image/png",
    isAnimated = false,
    hasAlpha = true,
    hash = "h$id",
    collectionId = null,
    addedAt = 0,
)

class FakeSaveBackend : SaveBackend {
    val collections = MutableStateFlow<List<StickerCollection>>(emptyList())
    val details = mutableMapOf<String, ItemDetails>()
    val results = mutableMapOf<String, ImportResult>()
    val imported = mutableListOf<Pair<String, Long?>>()
    private var nextId = 100L

    fun add(id: Long, name: String, parentId: Long?) {
        collections.value = collections.value +
            StickerCollection(id, name, parentId, sortOrder = id.toInt(), createdAt = 0)
    }

    override fun observeCollections(): Flow<List<StickerCollection>> = collections

    override suspend fun createCollection(name: String, parentId: Long?): StickerCollection {
        val c = StickerCollection(nextId++, name.trim(), parentId, sortOrder = 999, createdAt = 0)
        collections.value = collections.value + c
        return c
    }

    override suspend fun describe(uri: String): ItemDetails? = details[uri]

    override suspend fun import(uri: String, collectionId: Long?): ImportResult {
        imported += uri to collectionId
        return results[uri] ?: ImportResult.Added(sticker(imported.size.toLong()))
    }
}
