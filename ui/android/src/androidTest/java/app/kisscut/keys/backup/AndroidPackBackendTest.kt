package app.kisscut.keys.backup

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.kisscut.keys.App
import app.kisscut.keys.TestLibrary
import app.kisscut.keys.debug.SeedData
import app.kisscut.keys.shared.core.pack.PackException
import app.kisscut.keys.shared.core.pack.PackProblem
import app.kisscut.keys.shared.core.pack.PackSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Backup, share and import on the device: real zip, real converters, real content uris (docs/IMPORT_EXPORT.md). */
@RunWith(AndroidJUnit4::class)
class AndroidPackBackendTest {
    private val context = ApplicationProvider.getApplicationContext<App>()
    private val from = TestLibrary()
    private val to = TestLibrary()

    @After
    fun close() {
        from.close()
        to.close()
    }

    private suspend fun TestLibrary.hashes() = stickers.allOnce().map { it.hash }.toSet()

    private suspend fun TestLibrary.names() = collections.observeAll().first().map { it.name }.toSet()

    @Test
    fun aBackupRestoresIntoAnotherLibrary() = runBlocking {
        val cats = from.collections.create("Cats", null)
        from.collections.create("Grumpy", cats.id).also { from.face(1, it.id) }
        from.face(2, cats.id)
        from.gif()
        from.add(SeedData.photo(3, 1600, 1200)) // converted to 512px at save; the backup keeps the hash
        val file = File(from.dir, "backup.stickerpack")

        val result = from.packBackend().backUp(Uri.fromFile(file).toString(), PackSettings("dark")) { _, _ -> }
        assertEquals(4, result.stickers)

        val summary = to.packBackend().import(Uri.fromFile(file).toString()) { _, _ -> }
        assertEquals(4, summary.added)
        assertEquals(PackSettings("dark"), summary.settings)
        assertEquals(from.hashes(), to.hashes())
        assertEquals(setOf("Cats", "Grumpy"), to.names())

        val again = from.packBackend().import(Uri.fromFile(file).toString()) { _, _ -> }
        assertEquals(0, again.added, "restoring into the same library adds nothing")
    }

    @Test
    fun aSharedCollectionIsAFileProviderUriThatImports() = runBlocking {
        val cats = from.collections.create("Cats & Co", null)
        from.face(1, cats.id)
        from.face(2, null) // not in the collection: not shared

        val uri = from.packBackend().exportForShare(cats.id, cats.name) { _, _ -> }
        assertTrue(uri.startsWith("content://${context.packageName}.fileprovider/"), uri)
        assertTrue(uri.endsWith("Cats-Co.stickerpack"), uri)

        val summary = to.packBackend().import(uri) { _, _ -> }
        assertEquals(1, summary.added)
        assertEquals(setOf("Cats & Co"), to.names())
    }

    @Test
    fun aFileThatIsNotAPackIsRefused() = runBlocking {
        val file = File(to.dir, "photo.stickerpack").apply { writeBytes(SeedData.face(1, size = 64)) }
        val problem = assertFailsWith<PackException> {
            to.packBackend().import(Uri.fromFile(file).toString()) { _, _ -> }
        }.problem
        assertEquals(PackProblem.NotAPack, problem)
        assertEquals(emptySet(), to.hashes())
    }

    @Test
    fun fileNamesAreSafe() {
        assertEquals("Cats-Co.stickerpack", AndroidPackBackend.fileName("  Cats & Co! "))
        assertEquals("stickers.stickerpack", AndroidPackBackend.fileName("..//"))
    }
}
