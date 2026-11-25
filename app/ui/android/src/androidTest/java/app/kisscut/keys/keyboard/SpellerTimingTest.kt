package app.kisscut.keys.keyboard

import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.kisscut.keys.shared.core.words.Speller
import app.kisscut.keys.shared.core.words.WordList
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
}
