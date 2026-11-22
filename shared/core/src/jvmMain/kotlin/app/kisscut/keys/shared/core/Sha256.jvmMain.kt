package app.kisscut.keys.shared.core

import java.security.MessageDigest

actual fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { b ->
        (b.toInt() and 0xFF).toString(16).padStart(2, '0')
    }
