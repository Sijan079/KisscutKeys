package app.kisscut.keys.shared.core.words

import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LearnedWordsRepositoryTest {
    private class FakeFile(var text: String? = null, val failReads: Boolean = false, val failWrites: Boolean = false) :
        LearnedWordsFile {
        var writes = 0

        override suspend fun read(): String? = if (failReads) error("unreadable") else text

        override suspend fun write(text: String) {
            if (failWrites) error("disk full")
            writes++
            this.text = text
        }

        override suspend fun delete() {
            text = null
        }
    }

    private val now = 1_000_000_000L

    private fun TestScope.repo(file: FakeFile) = LearnedWordsRepository(file, { now }, backgroundScope)

    @Test
    fun learnedWordsAreSavedAndLoadBack() = runTest {
        val file = FakeFile()
        val first = repo(file)
        first.typed("kain")
        first.typed("kain")
        first.undidAutocorrect("charot")
        testScheduler.runCurrent()
        assertEquals(setOf("kain", "charot"), first.words.value.known.toSet())

        val second = repo(file)
        testScheduler.runCurrent()
        assertEquals(setOf("kain", "charot"), second.words.value.known.toSet())
    }

    @Test
    fun aWordSeenOnceIsSavedToo() = runTest {
        val file = FakeFile()
        repo(file).typed("sana")
        testScheduler.runCurrent()
        val again = repo(file)
        again.typed("sana")
        testScheduler.runCurrent()
        assertTrue(again.words.value.isLearned("sana"))
    }

    @Test
    fun wordsTypedBeforeTheFileLoadsAreKept() = runTest {
        val file = FakeFile(LearnedWordsCodec.encode(listOf(LearnedWord("kain", 2, now))))
        val repo = repo(file)
        repo.typed("sana")
        repo.typed("sana")
        testScheduler.runCurrent()
        assertEquals(setOf("kain", "sana"), repo.words.value.known.toSet())
    }

    @Test
    fun clearForgetsEverythingAndDeletesTheFile() = runTest {
        val file = FakeFile()
        val repo = repo(file)
        repo.undidAutocorrect("charot")
        testScheduler.runCurrent()
        repo.clear()
        testScheduler.runCurrent()
        assertEquals(0, repo.words.value.entries.size)
        assertNull(file.text)
    }

    @Test
    fun manyChangesInARowAreSavedOnce() = runTest {
        val file = FakeFile()
        val repo = repo(file)
        repeat(10) { repo.typed("word") }
        testScheduler.runCurrent()
        assertEquals(1, file.writes)
        assertTrue(repo.words.value.isLearned("word"))
    }

    @Test
    fun anUnreadableFileStartsEmptyAndAFailedSaveKeepsWordsInMemory() = runTest {
        val unreadable = repo(FakeFile(failReads = true))
        testScheduler.runCurrent()
        assertEquals(0, unreadable.words.value.entries.size)

        val diskFull = repo(FakeFile(failWrites = true))
        diskFull.undidAutocorrect("charot")
        testScheduler.runCurrent()
        assertTrue(diskFull.words.value.isLearned("charot"))
    }

    @Test
    fun theCodecSkipsBadLines() {
        val text = LearnedWordsCodec.encode(listOf(LearnedWord("kain", 3, 7))) + "broken\nx\tno\t1\nsana\t0\t1\n"
        assertEquals(listOf(LearnedWord("kain", 3, 7)), LearnedWordsCodec.decode(text))
        assertFalse(LearnedWordsCodec.encode(emptyList()).contains('\t'))
    }
}
