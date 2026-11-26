package app.kisscut.keys.shared.core.words

import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class DiskLearnedWordsFileTest {
    private val root: File = Files.createTempDirectory("learned").toFile()
    private val dir = File(root, "no_backup")

    @AfterTest
    fun cleanUp() {
        root.deleteRecursively()
    }

    @Test
    fun writesReplaceTheFileAndDeleteRemovesIt() = runTest {
        val file = DiskLearnedWordsFile(dir)
        assertNull(file.read())
        file.write("one")
        file.write("two")
        assertEquals("two", file.read())
        assertEquals(listOf(DiskLearnedWordsFile.NAME), dir.list()!!.toList(), "no temporary file left behind")
        file.delete()
        assertNull(file.read())
        assertFalse(File(dir, DiskLearnedWordsFile.NAME).exists())
    }
}
