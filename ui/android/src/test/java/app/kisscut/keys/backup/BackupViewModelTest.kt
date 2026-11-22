package app.kisscut.keys.backup

import app.kisscut.keys.settings.ThemeMode
import app.kisscut.keys.settings.ThemeStore
import app.kisscut.keys.shared.core.RejectReason
import app.kisscut.keys.shared.core.pack.ExportResult
import app.kisscut.keys.shared.core.pack.ImportSummary
import app.kisscut.keys.shared.core.pack.PackException
import app.kisscut.keys.shared.core.pack.PackProblem
import app.kisscut.keys.shared.core.pack.PackSettings
import app.kisscut.keys.shared.db.StickerCollection
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class BackupViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private class FakeThemes : ThemeStore {
        override val theme = MutableStateFlow(ThemeMode.DARK)
        override suspend fun setTheme(mode: ThemeMode) {
            theme.value = mode
        }
    }

    private class FakeBackend : PackBackend {
        var settingsSeen: PackSettings? = null
        var gate: CompletableDeferred<Unit>? = null
        var failWith: Exception? = null
        var cancelled = false
        var summary = summary()
        var sharedName: String? = null

        override suspend fun backUp(uri: String, settings: PackSettings, onProgress: Progress): ExportResult {
            settingsSeen = settings
            onProgress(1, 2)
            wait()
            onProgress(2, 2)
            return ExportResult(stickers = 2, collections = 1, bytes = 10)
        }

        override suspend fun exportForShare(collectionId: Long, name: String, onProgress: Progress): String {
            sharedName = name
            wait()
            return "content://pack/$collectionId"
        }

        override suspend fun import(uri: String, onProgress: Progress): ImportSummary {
            wait()
            return summary
        }

        private suspend fun wait() {
            failWith?.let { throw it }
            try {
                gate?.await()
            } catch (e: kotlinx.coroutines.CancellationException) {
                cancelled = true
                throw e
            }
        }
    }

    private val backend = FakeBackend()
    private val themes = FakeThemes()
    private val collections = MutableStateFlow(
        listOf(StickerCollection(1, "Cats", null, 0, 0), StickerCollection(2, "Grumpy", 1, 0, 0)),
    )

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm() = BackupViewModel(backend, themes, collections)

    @Test
    fun backUpIncludesTheThemeAndReportsProgressThenTheCount() = runTest(dispatcher) {
        backend.gate = CompletableDeferred()
        val vm = vm()
        vm.backUp("content://doc")
        advanceUntilIdle()
        assertEquals(BackupDialog.Working(BackupTask.BACK_UP, 1, 2), vm.dialog.value)
        assertEquals(PackSettings(theme = "dark"), backend.settingsSeen)

        backend.gate!!.complete(Unit)
        advanceUntilIdle()
        assertEquals(BackupDialog.BackedUp(2), vm.dialog.value)
        vm.dismiss()
        assertNull(vm.dialog.value)
    }

    @Test
    fun cancelStopsTheTaskAndClosesTheDialog() = runTest(dispatcher) {
        backend.gate = CompletableDeferred()
        val vm = vm()
        vm.import("content://pack")
        advanceUntilIdle()
        assertIs<BackupDialog.Working>(vm.dialog.value)
        vm.dismiss() // cannot dismiss a running task by tapping outside
        assertIs<BackupDialog.Working>(vm.dialog.value)

        vm.cancel()
        advanceUntilIdle()
        assertTrue(backend.cancelled)
        assertNull(vm.dialog.value)
    }

    @Test
    fun importShowsTheSummaryAndAppliesAPacksTheme() = runTest(dispatcher) {
        backend.summary = summary(settings = PackSettings(theme = "light"))
        val vm = vm()
        vm.import("content://pack")
        advanceUntilIdle()
        assertEquals(BackupDialog.Imported(backend.summary), vm.dialog.value)
        assertEquals(ThemeMode.LIGHT, themes.theme.value)
    }

    @Test
    fun failuresNameTheProblem() = runTest(dispatcher) {
        val vm = vm()
        backend.failWith = PackException(PackProblem.TooNew(2))
        vm.import("content://pack")
        advanceUntilIdle()
        assertEquals(BackupDialog.Failed(BackupTask.IMPORT, PackProblem.TooNew(2)), vm.dialog.value)

        vm.dismiss()
        backend.failWith = IOException("disk full")
        vm.backUp("content://doc")
        advanceUntilIdle()
        assertEquals(BackupDialog.Failed(BackupTask.BACK_UP, null), vm.dialog.value)
    }

    @Test
    fun shareAsksForACollectionThenHandsOverThePack() = runTest(dispatcher) {
        val vm = vm()
        vm.chooseCollectionToShare()
        advanceUntilIdle()
        val pick = assertIs<BackupDialog.PickCollection>(vm.dialog.value)
        assertEquals(listOf("Cats", "Grumpy"), pick.targets.map { it.name })

        vm.share(2)
        advanceUntilIdle()
        assertEquals("Grumpy", backend.sharedName)
        assertEquals("content://pack/2", vm.shareRequests.first())
        assertNull(vm.dialog.value)
    }

    @Test
    fun onlyOneTaskRunsAtATime() = runTest(dispatcher) {
        backend.gate = CompletableDeferred()
        val vm = vm()
        vm.import("content://a")
        advanceUntilIdle()
        vm.backUp("content://b")
        advanceUntilIdle()
        assertEquals(BackupTask.IMPORT, (vm.dialog.value as BackupDialog.Working).task)
        assertNull(backend.settingsSeen)
    }

    private companion object {
        fun summary(settings: PackSettings? = null) = ImportSummary(
            added = 3, duplicates = 1, rejected = mapOf(RejectReason.UNSUPPORTED_TYPE to 1), missing = 0,
            collectionsCreated = 1, collectionsMerged = 0, collectionsFlattened = 0, settings = settings,
        )
    }
}
