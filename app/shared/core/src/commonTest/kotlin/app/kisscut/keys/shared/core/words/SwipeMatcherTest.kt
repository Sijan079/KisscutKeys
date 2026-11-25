package app.kisscut.keys.shared.core.words

import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Swipe matching on a QWERTY grid shaped like the keyboard's (keys 100 px wide, rows 150 px apart). */
class SwipeMatcherTest {
    companion object {
        val layout: KeyLayout = run {
            val rows = listOf("qwertyuiop" to 0f, "asdfghjkl" to 0.5f, "zxcvbnm" to 1.5f)
            val letters = ArrayList<Char>()
            val xs = ArrayList<Float>()
            val ys = ArrayList<Float>()
            rows.forEachIndexed { r, (keys, offset) ->
                keys.forEachIndexed { i, c ->
                    letters += c
                    xs += (i + offset + 0.5f) * 100f
                    ys += (r + 0.5f) * 150f
                }
            }
            KeyLayout(letters.toCharArray(), xs.toFloatArray(), ys.toFloatArray(), keyWidth = 100f)
        }

        /** A finger path through [word]'s keys: 8 points per stroke, wobbling up to [wobble] px. */
        fun pathFor(word: String, wobble: Float = 18f): FloatArray {
            val keys = Speller.key(word)
            val pts = ArrayList<Float>()
            for (i in 0 until keys.length - 1) {
                val (x1, y1) = layout.x(keys[i]) to layout.y(keys[i])
                val (x2, y2) = layout.x(keys[i + 1]) to layout.y(keys[i + 1])
                for (s in 0 until 8) {
                    val t = s / 8f
                    val n = pts.size / 2
                    pts += x1 + (x2 - x1) * t + wobble * sin(n * 1.7f)
                    pts += y1 + (y2 - y1) * t + wobble * sin(n * 2.3f + 1f)
                }
            }
            pts += layout.x(keys.last())
            pts += layout.y(keys.last())
            return pts.toFloatArray()
        }
    }

    private val list = WordList(
        listOf(
            Word("the", 222), Word("they", 190), Word("tie", 120), Word("hello", 120), Word("hell", 100),
            Word("world", 145), Word("would", 171), Word("word", 145), Word("don't", 170), Word("done", 160),
            Word("letter", 130), Word("litter", 70), Word("damn", 90, offensive = true), Word("dam", 80),
            Word("a", 200), Word("good", 160), Word("god", 140), Word("help", 140),
        ),
        emptyMap(),
    )
    private val matcher = SwipeMatcher(listOf(list))

    @Test
    fun matchesTheTracedWordFirst() {
        // "help" is more common than "hello" and traces almost the same keys; the end key decides.
        for (w in listOf("the", "hello", "help", "world", "would", "don't", "letter", "good")) {
            assertEquals(w, matcher.match(pathFor(w), layout).firstOrNull(), "swiped $w")
        }
    }

    @Test
    fun doubledLettersTraceTheKeyOnce() {
        assertEquals("hlo", SwipeMatcher.squeeze("hllo"))
        assertEquals("helo", SwipeMatcher.squeeze("hello"))
        // "good" and "god" trace the same keys; the more common one wins, the other is still offered.
        val got = matcher.match(pathFor("god"), layout)
        assertEquals(listOf("good", "god"), got.take(2))
    }

    @Test
    fun offersRunnerUpsAndNeverOffensiveWords() {
        val world = matcher.match(pathFor("world"), layout)
        assertTrue(world.size > 1, world.toString())
        assertFalse("damn" in matcher.match(pathFor("damn"), layout))
    }

    @Test
    fun aTapOrAWobbleIsNoSwipe() {
        assertTrue(matcher.match(floatArrayOf(150f, 75f, 170f, 80f), layout).isEmpty())
        assertTrue(matcher.match(floatArrayOf(150f, 75f), layout).isEmpty())
    }

    @Test
    fun resamplingKeepsTheEndsAndSpacesEvenly() {
        val out = FloatArray(SwipeMatcher.SAMPLES * 2)
        SwipeMatcher.resample(floatArrayOf(0f, 0f, 310f, 0f), 2, out)
        assertEquals(0f, out[0])
        assertEquals(310f, out[(SwipeMatcher.SAMPLES - 1) * 2], 0.01f)
        assertEquals(10f, out[2], 0.01f)
    }
}
