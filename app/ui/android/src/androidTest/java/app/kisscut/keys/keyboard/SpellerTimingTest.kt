package app.kisscut.keys.keyboard

import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.kisscut.keys.shared.core.words.PersonalWords
import app.kisscut.keys.shared.core.words.Speller
import app.kisscut.keys.shared.core.words.SwipeMatcher
import app.kisscut.keys.shared.core.words.KeyLayout
import app.kisscut.keys.shared.core.words.WordList
import app.kisscut.keys.shared.core.words.WordPairs
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertTrue

/**
 * The real word list on a real device (docs/TESTING.md, Word list): loading stays off the typing path, and
 * suggestions for one keystroke fit well inside a frame on a mid-range phone.
 */
@RunWith(AndroidJUnit4::class)
class SpellerTimingTest {
    private val assets = InstrumentationRegistry.getInstrumentation().targetContext.assets

    private fun read(name: String) = assets.open("words/$name").bufferedReader().use { it.readText() }

    @Test
    fun loadsOnceAndSuggestsPerKeystrokeWithinAFrame() {
        val loadStart = SystemClock.elapsedRealtime()
        val speller = Speller(listOf(WordList.parse(read("en_US.txt"), read("en_US_fixes.txt"))))
        val loadMs = SystemClock.elapsedRealtime() - loadStart

        val sentence = "teh weather tomorow looks grate so lets meet at the beach becuase its been a long weak"
        // Warm up the JIT the way a few typed words would.
        repeat(3) { for (w in sentence.split(' ')) speller.suggest(w) }

        val times = ArrayList<Double>()
        for (word in sentence.split(' ')) {
            for (end in 1..word.length) {
                val start = System.nanoTime()
                speller.suggest(word.substring(0, end))
                times += (System.nanoTime() - start) / 1e6
            }
            val start = System.nanoTime()
            speller.autocorrect(word)
            times += (System.nanoTime() - start) / 1e6
        }
        times.sort()
        val median = times[times.size / 2]
        val p95 = times[(times.size * 95) / 100]
        val report = "load=${loadMs}ms keystrokes=${times.size} median=${"%.1f".format(median)}ms " +
            "p95=${"%.1f".format(p95)}ms max=${"%.1f".format(times.last())}ms"
        Log.i("SpellerTiming", report)
        assertTrue(loadMs < 3_000, report)
        assertTrue(p95 < 16.0, report)
    }

    @Test
    fun aFullLearnedListKeepsKeystrokesWithinAFrame() {
        val list = WordList.parse(read("en_US.txt"), read("en_US_fixes.txt"))
        val bundled = Speller(listOf(list))
        // The cap's worth of learned words, each one letter off a common word: the most matching work per key.
        val learned = list.words.asSequence().map { it.text }.filter { w -> w.all { it in 'a'..'z' } }
            .map { it + "z" }.take(PersonalWords.MAX_LEARNED).toList()
        val buildStart = SystemClock.elapsedRealtime()
        val speller = bundled.withLearned(learned)
        val buildMs = SystemClock.elapsedRealtime() - buildStart

        val sentence = "teh weather tomorow looks grate so lets meet at the beach becuase its been a long weak"
        repeat(3) { for (w in sentence.split(' ')) speller.suggest(w) }
        val times = ArrayList<Double>()
        for (word in sentence.split(' ')) {
            for (end in 1..word.length) {
                val start = System.nanoTime()
                speller.suggest(word.substring(0, end))
                times += (System.nanoTime() - start) / 1e6
            }
            val start = System.nanoTime()
            speller.autocorrect(word)
            times += (System.nanoTime() - start) / 1e6
        }
        times.sort()
        val p95 = times[(times.size * 95) / 100]
        val report = "learned=${learned.size} rebuild=${buildMs}ms median=${"%.1f".format(times[times.size / 2])}ms " +
            "p95=${"%.1f".format(p95)}ms max=${"%.1f".format(times.last())}ms"
        Log.i("SpellerTiming", report)
        assertTrue(buildMs < 500, report)
        assertTrue(p95 < 16.0, report)
    }

    @Test
    fun wordPairsLoadOffTheTypingPathAndKeepKeystrokesWithinAFrame() {
        val list = WordList.parse(read("en_US.txt"), read("en_US_fixes.txt"))
        val loadStart = SystemClock.elapsedRealtime()
        val pairs = WordPairs.parse(read("en_US_pairs.txt"))
        val loadMs = SystemClock.elapsedRealtime() - loadStart
        val speller = Speller(listOf(list), pairs)

        val words = "teh weather tomorow looks grate so lets meet at the beach becuase its been a long weak".split(' ')
        repeat(3) { words.zipWithNext { a, b -> speller.suggest(b, a) } }
        val times = ArrayList<Double>()
        words.zipWithNext { previous, word ->
            for (end in 1..word.length) {
                val start = System.nanoTime()
                speller.suggest(word.substring(0, end), previous)
                times += (System.nanoTime() - start) / 1e6
            }
            val start = System.nanoTime()
            speller.autocorrect(word, previous = previous)
            times += (System.nanoTime() - start) / 1e6
        }
        times.sort()
        val p95 = times[(times.size * 95) / 100]
        val report = "pairs=${pairs.size} load=${loadMs}ms median=${"%.1f".format(times[times.size / 2])}ms " +
            "p95=${"%.1f".format(p95)}ms max=${"%.1f".format(times.last())}ms"
        Log.i("SpellerTiming", report)
        assertTrue(loadMs < 3_000, report)
        assertTrue(p95 < 16.0, report)
    }

    @Test
    fun aSwipeIsMatchedWellUnderAQuarterSecond() {
        val list = WordList.parse(read("en_US.txt"), read("en_US_fixes.txt"))
        val matcher = SwipeMatcher(listOf(list))
        // A QWERTY grid like the keyboard's: keys 100 px wide, rows 150 px apart.
        val rows = listOf("qwertyuiop" to 0f, "asdfghjkl" to 0.5f, "zxcvbnm" to 1.5f)
        val letters = rows.flatMap { it.first.toList() }.toCharArray()
        val xs = rows.flatMap { (keys, offset) -> keys.indices.map { (it + offset + 0.5f) * 100f } }.toFloatArray()
        val ys = rows.flatMapIndexed { r, (keys, _) -> keys.map { (r + 0.5f) * 150f } }.toFloatArray()
        val layout = KeyLayout(letters, xs, ys, keyWidth = 100f)
        fun path(word: String): FloatArray = word.zipWithNext().flatMap { (a, b) ->
            val (ax, ay) = layout.x(a) to layout.y(a)
            val (bx, by) = layout.x(b) to layout.y(b)
            (0 until 8).flatMap { s -> listOf(ax + (bx - ax) * s / 8f, ay + (by - ay) * s / 8f) }
        }.toFloatArray() + floatArrayOf(layout.x(word.last()), layout.y(word.last()))

        val words = listOf("hello", "world", "tomorrow", "because", "the", "people", "really", "thanks")
        repeat(2) { for (w in words) matcher.match(path(w), layout) }
        val times = words.map { w ->
            val start = System.nanoTime()
            matcher.match(path(w), layout)
            (System.nanoTime() - start) / 1e6
        }.sorted()
        val report = "swipe median=${"%.1f".format(times[times.size / 2])}ms max=${"%.1f".format(times.last())}ms"
        Log.i("SpellerTiming", report)
        assertTrue(times.last() < 250.0, report)
    }
}
