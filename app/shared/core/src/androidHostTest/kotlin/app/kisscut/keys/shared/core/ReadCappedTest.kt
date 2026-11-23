package app.kisscut.keys.shared.core

import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class ReadCappedTest {
    @Test
    fun readsEverythingUnderTheCap() {
        val bytes = ByteArray(5000) { it.toByte() }
        assertContentEquals(bytes, ByteArrayInputStream(bytes).readCapped(maxBytes = 5000))
    }

    @Test
    fun stopsOneByteAfterTheCap() {
        var served = 0L
        val endless = object : InputStream() {
            override fun read(): Int = 7.also { served++ }
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                b.fill(7, off, off + len)
                served += len
                return len
            }
        }

        val read = endless.readCapped(maxBytes = 100_000)

        assertEquals(100_001, read.size, "one extra byte tells the caller it is over the cap")
        assertEquals(true, served <= 100_001 + 8192, "does not keep reading")
    }
}
