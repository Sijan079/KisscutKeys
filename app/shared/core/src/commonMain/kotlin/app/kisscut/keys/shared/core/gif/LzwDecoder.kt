package app.kisscut.keys.shared.core.gif

/**
 * GIF's variable-width LZW (GIF89a appendix F), read from the data sub-blocks that start at [start].
 * Codes are 1 bit wider than [minCodeSize] and grow to at most 12 bits. When the table is full the
 * stream may go on at 12 bits without a clear code; new entries are then not added.
 */
internal class LzwDecoder(private val bytes: ByteArray, start: Int, private val minCodeSize: Int) {
    private var pos = start
    private var blockLeft = 0
    private var ended = false
    private var bitBuffer = 0
    private var bitCount = 0
    private val prefix = ShortArray(MAX_CODES)
    private val suffix = ByteArray(MAX_CODES)
    private val first = ByteArray(MAX_CODES)
    private val length = ShortArray(MAX_CODES)

    /**
     * Fills [out] from the start and returns how many pixels were decoded. Fewer than [out]'s size means
     * the data was truncated or broken (an undefined code); extra data after a full frame is ignored.
     */
    fun decodeInto(out: ByteArray): Int {
        val clear = 1 shl minCodeSize
        val end = clear + 1
        for (c in 0 until clear) {
            suffix[c] = c.toByte()
            first[c] = c.toByte()
            length[c] = 1
        }
        var codeSize = minCodeSize + 1
        var next = clear + 2
        var prev = -1
        var written = 0
        while (written < out.size) {
            val code = readCode(codeSize) ?: break
            if (code == clear) {
                codeSize = minCodeSize + 1
                next = clear + 2
                prev = -1
                continue
            }
            if (code == end) break
            if (prev == -1) {
                if (code > clear) break // only a literal may follow a clear code
                out[written++] = code.toByte()
                prev = code
                continue
            }
            val known = code < next
            if (!known && code != next) break // undefined code
            if (next < MAX_CODES) {
                // New entry: the previous string plus the first byte of this one. For code == next
                // (the "KwKwK" case) that first byte is the previous string's own first byte.
                prefix[next] = prev.toShort()
                suffix[next] = if (known) first[code] else first[prev]
                first[next] = first[prev]
                length[next] = (length[prev] + 1).toShort()
                next++
                if (next == 1 shl codeSize && codeSize < MAX_CODE_SIZE) codeSize++
            } else if (!known) {
                break
            }
            written = emit(code, out, written)
            prev = code
        }
        return written
    }

    /** Writes the string for [code] at [at], cut at the end of [out]; returns the new write position. */
    private fun emit(code: Int, out: ByteArray, at: Int): Int {
        val len = length[code].toInt()
        var c = code
        var i = at + len - 1
        while (i >= at) {
            if (i < out.size) out[i] = suffix[c]
            c = prefix[c].toInt()
            i--
        }
        return minOf(at + len, out.size)
    }

    /** Next [size]-bit code, least significant bit first, or null at the end of the data. */
    private fun readCode(size: Int): Int? {
        while (bitCount < size) {
            val byte = nextByte() ?: return null
            bitBuffer = bitBuffer or (byte shl bitCount)
            bitCount += 8
        }
        val code = bitBuffer and ((1 shl size) - 1)
        bitBuffer = bitBuffer ushr size
        bitCount -= size
        return code
    }

    private fun nextByte(): Int? {
        if (ended) return null
        if (blockLeft == 0) {
            if (pos >= bytes.size) return null.also { ended = true }
            blockLeft = bytes[pos++].toInt() and 0xFF
            if (blockLeft == 0) return null.also { ended = true }
        }
        if (pos >= bytes.size) return null.also { ended = true }
        blockLeft--
        return bytes[pos++].toInt() and 0xFF
    }

    private companion object {
        const val MAX_CODE_SIZE = 12
        const val MAX_CODES = 1 shl MAX_CODE_SIZE
    }
}
