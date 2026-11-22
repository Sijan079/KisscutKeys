package app.kisscut.keys.shared.core

import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AndroidFileStoreTest {
    private val root: File = createTempDirectory("filestore").toFile()
    private val store = AndroidFileStore(root)

    @AfterTest
    fun cleanup() {
        root.deleteRecursively()
    }

    @Test
    fun writesIntoTheAreaFolder() = runTest {
        store.write(FileArea.STICKERS, "abc.png", byteArrayOf(1, 2, 3))
        store.write(FileArea.THUMBS, "abc.webp", byteArrayOf(9))

        assertContentEquals(byteArrayOf(1, 2, 3), File(root, "stickers/abc.png").readBytes())
        assertContentEquals(byteArrayOf(9), File(root, "thumbs/abc.webp").readBytes())
        assertTrue(store.exists(FileArea.STICKERS, "abc.png"))
        assertFalse(store.exists(FileArea.THUMBS, "abc.png"))
    }

    @Test
    fun writeReplacesExistingFileAndLeavesNoTempFiles() = runTest {
        store.write(FileArea.STICKERS, "a.png", byteArrayOf(1))
        store.write(FileArea.STICKERS, "a.png", byteArrayOf(2, 2))

        assertContentEquals(byteArrayOf(2, 2), File(root, "stickers/a.png").readBytes())
        assertEquals(listOf("a.png"), File(root, "stickers").list()!!.toList())
    }

    @Test
    fun deleteRemovesFileAndTreatsMissingAsDeleted() = runTest {
        store.write(FileArea.STICKERS, "a.png", byteArrayOf(1))

        assertTrue(store.delete(FileArea.STICKERS, "a.png"))
        assertFalse(store.exists(FileArea.STICKERS, "a.png"))
        assertTrue(store.delete(FileArea.STICKERS, "never-existed.png"))
    }

    @Test
    fun rejectsPathsThatEscapeTheAreaFolder() = runTest {
        File(root, "secret.txt").writeText("keep")
        for (bad in listOf("../secret.txt", "..", ".", "", "sub/a.png", "sub\\a.png", "/abs.png")) {
            assertFailsWith<IllegalArgumentException>("write '$bad'") { store.write(FileArea.STICKERS, bad, byteArrayOf(1)) }
            assertFailsWith<IllegalArgumentException>("delete '$bad'") { store.delete(FileArea.STICKERS, bad) }
            assertFailsWith<IllegalArgumentException>("exists '$bad'") { store.exists(FileArea.STICKERS, bad) }
        }
        assertEquals("keep", File(root, "secret.txt").readText())
    }
}
