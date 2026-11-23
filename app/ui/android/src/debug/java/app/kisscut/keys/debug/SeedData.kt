package app.kisscut.keys.debug

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.Build
import app.kisscut.keys.AppContainer
import app.kisscut.keys.shared.core.ByteSource
import app.kisscut.keys.shared.core.CollectionRepository
import app.kisscut.keys.shared.core.DeleteMode
import app.kisscut.keys.shared.core.ImportResult
import kotlinx.coroutines.flow.first
import java.io.ByteArrayOutputStream
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Debug-only test data (docs/TESTING.md, Test data): nested collections and generated stickers of every
 * kind, imported through the real importer so thumbnails get the real cut line. Never in release builds.
 */
object SeedData {
    private val palette = listOf(
        0xFF9B8CFF, 0xFFFF7A59, 0xFF5CC8FF, 0xFFFFD23F, 0xFF3DDBB0, 0xFFFF6FB5, 0xFFC8F23D, 0xFFFF9F43,
    ).map { it.toInt() }

    /** Removes every collection and sticker (and their files). */
    suspend fun clear(c: AppContainer) {
        c.collections.observeAllOnce().filter { it.parentId == null }.forEach {
            c.collections.delete(it.id, DeleteMode.EVERYTHING)
        }
        c.stickers.delete(c.stickers.allOnce().map { it.id })
    }

    /**
     * Reactions > (Memes > Classics, Cats, Thank you), Barkada > (Beach, Food), Work; plus a few Unsorted.
     * Returns the number of stickers added.
     */
    suspend fun seed(c: AppContainer): Int {
        val col = c.collections
        val reactions = col.create("Reactions", null)
        val memes = col.create("Memes", reactions.id)
        val classics = col.create("Classics", memes.id)
        val cats = col.create("Cats", reactions.id)
        val thanks = col.create("Thank you", reactions.id)
        val barkada = col.create("Barkada", null)
        val beach = col.create("Beach", barkada.id)
        val food = col.create("Food", barkada.id)
        val work = col.create("Work", null)

        var n = 0
        suspend fun add(bytes: ByteArray, collectionId: Long?): Long? {
            val r = c.importer.import(ByteSource { bytes }, collectionId)
            return (r as? ImportResult.Added)?.sticker?.id?.also { n++ }
        }

        val added = mutableListOf<Long>()
        val plan = listOf(
            reactions.id to 6, memes.id to 3, classics.id to 2, cats.id to 3, thanks.id to 2,
            barkada.id to 3, beach.id to 2, food.id to 2, work.id to 3, null to 3,
        )
        var i = 0
        for ((target, count) in plan) {
            repeat(count) {
                val bytes = when (i % 9) {
                    4 -> photo(i)
                    7 -> blob(i)
                    8 -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) face(i, webp = true) else face(i)
                    else -> face(i)
                }
                add(bytes, target)?.let { added += it }
                i++
            }
        }
        add(blinkGif(palette[0]), reactions.id)?.let { added += it }
        add(blinkGif(palette[4]), memes.id)?.let { added += it }
        add(blinkGif(palette[7]), null)?.let { added += it }

        c.stickers.setFavorite(added.filterIndexed { idx, _ -> idx % 5 == 1 }, true)
        // Some use so the hero tile shows "MOST USED" and Recents has content.
        added.take(4).forEachIndexed { idx, id -> repeat(idx + 1) { c.stickers.markUsed(id) } }
        return n
    }

    private suspend fun CollectionRepository.observeAllOnce() = observeAll().first()

    // --- generated images ----------------------------------------------------------------------

    private fun png(bitmap: Bitmap, format: Bitmap.CompressFormat = Bitmap.CompressFormat.PNG): ByteArray =
        ByteArrayOutputStream().also { bitmap.compress(format, 100, it) }.toByteArray().also { bitmap.recycle() }

    /** Transparent round face: the cut line comes from the importer. */
    fun face(seed: Int, size: Int = 512, webp: Boolean = false): ByteArray {
        val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val cv = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val s = size / 48f
        p.color = palette[seed % palette.size]
        cv.drawCircle(24 * s, 24 * s, (19 - 1.5f * (seed / 24)) * s, p) // colour x mouth repeats every 24
        p.color = INK
        cv.drawCircle(18.5f * s, 21 * s, 2.4f * s, p)
        cv.drawCircle(29.5f * s, 21 * s, 2.4f * s, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 2.2f * s
        p.strokeCap = Paint.Cap.ROUND
        val mouth = Path()
        when (seed % 3) {
            0 -> { mouth.moveTo(17 * s, 28 * s); mouth.quadTo(24 * s, 35 * s, 31 * s, 28 * s) }
            1 -> { mouth.moveTo(18 * s, 29 * s); mouth.lineTo(30 * s, 29 * s) }
            else -> {
                p.style = Paint.Style.FILL
                mouth.moveTo(18 * s, 27 * s)
                mouth.quadTo(24 * s, 39 * s, 30 * s, 27 * s)
                mouth.close()
            }
        }
        cv.drawPath(mouth, p)
        @Suppress("DEPRECATION")
        val lossless = webp && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        val fmt = if (lossless) Bitmap.CompressFormat.WEBP_LOSSLESS else Bitmap.CompressFormat.PNG
        return png(b, fmt)
    }

    /** Transparent star-ish blob, to show the cut line following a shape. */
    fun blob(seed: Int, size: Int = 512): ByteArray {
        val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val cv = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette[(seed + 3) % palette.size] }
        val path = Path()
        val c = size / 2f
        for (k in 0 until 10) {
            val r = if (k % 2 == 0) size * 0.42f else size * 0.22f
            val a = Math.PI * 2 * k / 10 - Math.PI / 2
            val x = c + r * cos(a).toFloat()
            val y = c + r * sin(a).toFloat()
            if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        p.pathEffect = android.graphics.CornerPathEffect(size * 0.05f)
        cv.drawPath(path, p)
        return png(b)
    }

    /** Opaque "photo": flat sky, sun, two hills. Gets a plain rounded tile. */
    fun photo(seed: Int, w: Int = 640, h: Int = 480): ByteArray {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        cv.drawColor(if (seed % 2 == 0) 0xFF3DDBB0.toInt() else 0xFF5CC8FF.toInt())
        p.color = 0xFFFFD23F.toInt()
        cv.drawCircle(w * (0.2f + (seed % 5) * 0.05f), h * 0.3f, h * 0.11f, p)
        p.color = 0xFF123229.toInt()
        cv.drawCircle(w * 0.2f, h * 1.25f, h * 0.75f, p)
        p.color = 0xFF1F7A62.toInt()
        cv.drawCircle(w * 0.85f, h * 1.3f, h * 0.7f, p)
        return png(b)
    }

    /** Two-frame animated GIF (a blinking face) from a tiny uncompressed GIF encoder. */
    fun blinkGif(color: Int, size: Int = 96): ByteArray {
        fun frame(blink: Boolean) = IntArray(size * size) { i ->
            val x = i % size + 0.5f
            val y = i / size + 0.5f
            val s = size / 48f
            val eye = { cx: Float ->
                if (blink) {
                    kotlin.math.abs(y - 21 * s) < 0.8f * s && kotlin.math.abs(x - cx * s) < 2.6f * s
                } else {
                    hypot(x - cx * s, y - 21 * s) < 2.4f * s
                }
            }
            when {
                eye(18.5f) || eye(29.5f) -> 2
                hypot(x - 24 * s, y - 24 * s) < 21 * s -> 1
                else -> 0
            }
        }
        return TinyGif.encode(size, size, listOf(Color.TRANSPARENT, color, INK), listOf(frame(false), frame(true)))
    }

    private val INK = 0xFF14131C.toInt()
}

/** Minimal GIF89a writer: 256-colour table, LZW with only literal codes (clear every 250). Debug data only. */
internal object TinyGif {
    fun encode(w: Int, h: Int, colors: List<Int>, frames: List<IntArray>, delayCs: Int = 60): ByteArray {
        val out = ByteArrayOutputStream()
        fun u16(v: Int) { out.write(v and 0xFF); out.write((v shr 8) and 0xFF) }
        out.write("GIF89a".toByteArray())
        u16(w); u16(h)
        out.write(0xF7); out.write(0); out.write(0)
        for (k in 0 until 256) {
            val c = colors.getOrElse(k) { 0 }
            out.write((c shr 16) and 0xFF); out.write((c shr 8) and 0xFF); out.write(c and 0xFF)
        }
        out.write(byteArrayOf(0x21, 0xFF.toByte(), 0x0B) + "NETSCAPE2.0".toByteArray() + byteArrayOf(3, 1, 0, 0, 0))
        for (f in frames) {
            out.write(byteArrayOf(0x21, 0xF9.toByte(), 4, ((2 shl 2) or 1).toByte()))
            u16(delayCs); out.write(0); out.write(0)
            out.write(0x2C); u16(0); u16(0); u16(w); u16(h); out.write(0)
            out.write(8)
            val data = lzwLiterals(f)
            var pos = 0
            while (pos < data.size) {
                val n = minOf(255, data.size - pos)
                out.write(n); out.write(data, pos, n); pos += n
            }
            out.write(0)
        }
        out.write(0x3B)
        return out.toByteArray()
    }

    private fun lzwLiterals(indices: IntArray): ByteArray {
        val bytes = ByteArrayOutputStream()
        var acc = 0
        var bits = 0
        fun emit(code: Int) {
            acc = acc or (code shl bits); bits += 9
            while (bits >= 8) { bytes.write(acc and 0xFF); acc = acc ushr 8; bits -= 8 }
        }
        emit(CLEAR)
        indices.forEachIndexed { i, v ->
            if (i > 0 && i % 250 == 0) emit(CLEAR)
            emit(v)
        }
        emit(EOI)
        if (bits > 0) bytes.write(acc and 0xFF)
        return bytes.toByteArray()
    }

    private const val CLEAR = 256
    private const val EOI = 257
}
