package com.example.stickers.shared.core.gif

import kotlin.test.Test
import kotlin.test.assertContentEquals

/** Expected canvases are written out by hand: `.` transparent, `R` red, `B` blue, `G` green. */
class GifCanvasTest {
    private val palette = intArrayOf(0xFFFF0000.toInt(), 0xFF0000FF.toInt(), 0xFF00FF00.toInt(), 0xFF000000.toInt())

    private fun frame(
        rows: String,
        left: Int = 0,
        top: Int = 0,
        disposal: GifDisposal = GifDisposal.KEEP,
        transparentIndex: Int = -1,
        missing: ByteArray? = null,
        framePalette: IntArray = palette,
    ): GifFrame {
        val lines = rows.trim().lines().map { it.trim() }
        val indices = lines.joinToString("").map { "RBGK.".indexOf(it).toByte() }.toByteArray()
        return GifFrame(
            left, top, lines[0].length, lines.size, framePalette, transparentIndex, disposal, 0, indices, missing,
        )
    }

    private fun expect(rows: String, actual: IntArray) {
        val expected = rows.trim().lines().joinToString("") { it.trim() }.map {
            when (it) {
                'R' -> palette[0]
                'B' -> palette[1]
                'G' -> palette[2]
                else -> 0
            }
        }.toIntArray()
        assertContentEquals(expected, actual)
    }

    private val red = frame(
        """
        RRRR
        RRRR
        RRRR
        """,
    )

    @Test
    fun keepDrawsOverThePreviousFrame() {
        val canvas = GifCanvas(4, 3)
        canvas.draw(red)
        expect(
            """
            RRRR
            RBBR
            RRRR
            """,
            canvas.draw(frame("BB", left = 1, top = 1)),
        )
    }

    @Test
    fun clearEmptiesOnlyThatFramesRectangleBeforeTheNextFrame() {
        val canvas = GifCanvas(4, 3)
        canvas.draw(red)
        expect(
            """
            BBRR
            BBRR
            RRRR
            """,
            canvas.draw(frame("BB\nBB", disposal = GifDisposal.CLEAR)),
        )
        expect(
            """
            ..RR
            ..RR
            RRRG
            """,
            canvas.draw(frame("G", left = 3, top = 2)),
        )
    }

    @Test
    fun restorePreviousPutsBackTheCanvasFromBeforeThatFrame() {
        val canvas = GifCanvas(4, 3)
        canvas.draw(red)
        canvas.draw(frame("BB\nBB", left = 1, disposal = GifDisposal.RESTORE_PREVIOUS))
        expect(
            """
            RRRR
            RRRR
            RRRG
            """,
            canvas.draw(frame("G", left = 3, top = 2)),
        )
    }

    @Test
    fun restorePreviousOnTheFirstFrameGoesBackToTransparent() {
        val canvas = GifCanvas(2, 1)
        canvas.draw(frame("RR", disposal = GifDisposal.RESTORE_PREVIOUS))
        expect("..", canvas.draw(frame(".", framePalette = palette.copyOf(1))))
    }

    @Test
    fun restoreAfterRestoreUsesTheLatestSavedCanvas() {
        val canvas = GifCanvas(3, 1)
        canvas.draw(frame("R"))
        canvas.draw(frame("B", left = 1, disposal = GifDisposal.RESTORE_PREVIOUS))
        canvas.draw(frame("G", left = 2, disposal = GifDisposal.RESTORE_PREVIOUS))
        expect("R.B", canvas.draw(frame("B", left = 2)))
    }

    @Test
    fun transparentIndexAndIndicesPastThePaletteDrawNothing() {
        val canvas = GifCanvas(4, 1)
        canvas.draw(frame("RRRR"))
        // Index 1 (blue) is transparent; K is index 3, past a 3-color palette.
        expect("GRRR", canvas.draw(frame("GBKB", transparentIndex = 1, framePalette = palette.copyOf(3))))
    }

    @Test
    fun missingPixelsDrawNothing() {
        val canvas = GifCanvas(4, 1)
        canvas.draw(frame("RRRR"))
        expect("BBRR", canvas.draw(frame("BBBB", missing = byteArrayOf(0, 0, 1, 1))))
    }

    @Test
    fun partsOutsideTheCanvasAreClipped() {
        val canvas = GifCanvas(3, 2)
        expect(
            """
            ...
            ..G
            """,
            canvas.draw(frame("GB\nBB", left = 2, top = 1, disposal = GifDisposal.CLEAR)),
        )
        // Clearing the clipped rectangle must stay inside the canvas too.
        expect("R..\n...", canvas.draw(frame("R")))
        expect("R..\n...", canvas.draw(frame("BB", left = 7, top = 7)))
    }
}
