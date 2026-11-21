package com.example.stickers.shared.core

import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Reads at most [maxBytes] + 1 bytes. A result longer than [maxBytes] means the stream is too
 * large (see [ByteSource]); nothing beyond that is read.
 */
fun InputStream.readCapped(maxBytes: Int): ByteArray {
    val limit = maxBytes.toLong() + 1
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    var total = 0L
    while (total < limit) {
        val n = read(buffer, 0, minOf(buffer.size.toLong(), limit - total).toInt())
        if (n < 0) break
        out.write(buffer, 0, n)
        total += n
    }
    return out.toByteArray()
}
