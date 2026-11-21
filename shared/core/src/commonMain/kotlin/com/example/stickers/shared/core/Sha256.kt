package com.example.stickers.shared.core

/** SHA-256 of [bytes] as 64 lowercase hex characters. */
expect fun sha256Hex(bytes: ByteArray): String
