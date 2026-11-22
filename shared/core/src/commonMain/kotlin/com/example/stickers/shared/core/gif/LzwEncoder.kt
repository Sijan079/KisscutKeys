package com.example.stickers.shared.core.gif

/**
 * GIF's variable-width LZW (GIF89a appendix F). The string table is an open-addressing hash from
 * (prefix code, next index) to code. Codes grow from minCodeSize + 1 to 12 bits; when all 4096 codes
 * are used a clear code starts a fresh table. Output goes into data sub-blocks of up to 255 bytes,
 * then the block terminator.
 */
internal class LzwEncoder(private val minCodeSize: Int) {
    private val keys = IntArray(HASH_SIZE)
    private val codes = ShortArray(HASH_SIZE)
    private val block = ByteArray(255)
    private var blockSize = 0
    private var bitBuffer = 0
    private var bitCount = 0

    fun encode(indices: ByteArray, out: ByteSink) {
        val clear = 1 shl minCodeSize
        val end = clear + 1
        var codeSize = minCodeSize + 1
        var next = clear + 2
        keys.fill(EMPTY)
        put(clear, codeSize, out)
        if (indices.isNotEmpty()) {
            var current = indices[0].toInt() and 0xFF
            require(current < clear) { "index past the code size" }
            for (i in 1 until indices.size) {
                val b = indices[i].toInt() and 0xFF
                require(b < clear) { "index past the code size" }
                val key = (current shl 8) or b
                var slot = (key * HASH_MULTIPLIER ushr (32 - HASH_BITS))
                while (keys[slot] != EMPTY && keys[slot] != key) slot = (slot + 1) and (HASH_SIZE - 1)
                if (keys[slot] == key) {
                    current = codes[slot].toInt()
                    continue
                }
                put(current, codeSize, out)
                if (next < MAX_CODES) {
                    keys[slot] = key
                    codes[slot] = next.toShort()
                    next++
                    // The decoder adds each entry one code later, so widen once the new entry needs it.
                    if (next > (1 shl codeSize) && codeSize < MAX_CODE_SIZE) codeSize++
                } else {
                    put(clear, codeSize, out)
                    keys.fill(EMPTY)
                    next = clear + 2
                    codeSize = minCodeSize + 1
                }
                current = b
            }
            put(current, codeSize, out)
        }
        put(end, codeSize, out)
        if (bitCount > 0) {
            byte(bitBuffer and 0xFF, out)
            bitBuffer = 0
            bitCount = 0
        }
        flushBlock(out)
        out.u8(0) // block terminator
    }

    private fun put(code: Int, size: Int, out: ByteSink) {
        bitBuffer = bitBuffer or (code shl bitCount)
        bitCount += size
        while (bitCount >= 8) {
            byte(bitBuffer and 0xFF, out)
            bitBuffer = bitBuffer ushr 8
            bitCount -= 8
        }
    }

    private fun byte(b: Int, out: ByteSink) {
        block[blockSize++] = b.toByte()
        if (blockSize == block.size) flushBlock(out)
    }

    private fun flushBlock(out: ByteSink) {
        if (blockSize == 0) return
        out.u8(blockSize)
        out.bytes(block, 0, blockSize)
        blockSize = 0
    }

    private companion object {
        const val MAX_CODE_SIZE = 12
        const val MAX_CODES = 1 shl MAX_CODE_SIZE
        const val HASH_BITS = 13 // 8192 slots for at most 4096 entries: probes stay short
        const val HASH_SIZE = 1 shl HASH_BITS
        const val HASH_MULTIPLIER = -0x61c88647 // 2^32 / golden ratio, for Fibonacci hashing
        const val EMPTY = -1
    }
}
