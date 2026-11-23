package app.kisscut.keys.shared.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Sha256Test {
    @Test
    fun knownVectors() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", sha256Hex(ByteArray(0)))
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            sha256Hex("abc".encodeToByteArray()),
        )
    }

    @Test
    fun hashIsLowercaseHexSoItIsASafeFileName() {
        val hash = sha256Hex(byteArrayOf(1, 2, 3))
        assertEquals(64, hash.length)
        assertTrue(hash.all { it in '0'..'9' || it in 'a'..'f' })
    }
}
