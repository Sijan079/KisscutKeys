package com.example.stickers.shared.core

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/** Tiny hand-built image headers. The sniffer reads headers only, so pixel data can be dummy bytes. */
object TestImages {
    private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

    fun png(
        width: Int = 4,
        height: Int = 4,
        colorType: Int = 6,
        extraChunks: List<String> = emptyList(),
    ): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(PNG_SIGNATURE)
        val ihdr = ByteArrayOutputStream().apply {
            writeIntBe(width)
            writeIntBe(height)
            write(byteArrayOf(8, colorType.toByte(), 0, 0, 0))
        }.toByteArray()
        out.chunk("IHDR", ihdr)
        extraChunks.forEach { out.chunk(it, ByteArray(8)) }
        out.chunk("IDAT", ByteArray(12))
        out.chunk("IEND", ByteArray(0))
        return out.toByteArray()
    }

    /** A real PNG encoded by ImageIO, to check the sniffer against an encoder's output. */
    fun realPng(width: Int = 3, height: Int = 2): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        image.setRGB(0, 0, 0x00FFFFFF)
        return ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
    }

    /** A real single-frame GIF encoded by ImageIO. */
    fun realGif(width: Int = 5, height: Int = 7): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_BYTE_INDEXED)
        return ByteArrayOutputStream().also { ImageIO.write(image, "gif", it) }.toByteArray()
    }

    /**
     * GIF89a with [frames] image descriptors. [transparent] adds a Graphic Control Extension with the
     * transparency flag before each frame. [commentWith2C] adds a comment block full of 0x2C bytes,
     * which a naive scanner would count as frames.
     */
    fun gif(
        width: Int = 4,
        height: Int = 3,
        frames: Int = 1,
        transparent: Boolean = false,
        commentWith2C: Boolean = false,
        trailer: Boolean = true,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        out.write("GIF89a".toByteArray())
        out.writeShortLe(width)
        out.writeShortLe(height)
        out.write(byteArrayOf(0x80.toByte(), 0, 0)) // global color table, 2 colors
        out.write(ByteArray(6))
        if (commentWith2C) {
            out.write(byteArrayOf(0x21, 0xFE.toByte(), 4, 0x2C, 0x2C, 0x2C, 0x2C, 0))
        }
        repeat(frames) {
            if (transparent) out.write(byteArrayOf(0x21, 0xF9.toByte(), 4, 1, 0, 0, 0, 0))
            out.write(0x2C)
            out.writeShortLe(0)
            out.writeShortLe(0)
            out.writeShortLe(width)
            out.writeShortLe(height)
            out.write(0) // no local color table
            out.write(byteArrayOf(2, 2, 0x4C, 0x01, 0)) // LZW min size, one sub-block, terminator
        }
        if (trailer) out.write(0x3B)
        return out.toByteArray()
    }

    fun webpLossy(width: Int = 6, height: Int = 5): ByteArray {
        val data = ByteArrayOutputStream().apply {
            write(byteArrayOf(0x10, 0x02, 0x00)) // frame tag
            write(byteArrayOf(0x9D.toByte(), 0x01, 0x2A))
            writeShortLe(width)
            writeShortLe(height)
            write(ByteArray(8))
        }.toByteArray()
        return riff("VP8 ", data)
    }

    fun webpLossless(width: Int = 6, height: Int = 5, alpha: Boolean = true): ByteArray {
        val bits = (width - 1) or ((height - 1) shl 14) or ((if (alpha) 1 else 0) shl 28)
        val data = ByteArrayOutputStream().apply {
            write(0x2F)
            writeIntLe(bits)
            write(ByteArray(8))
        }.toByteArray()
        return riff("VP8L", data)
    }

    fun webpExtended(width: Int = 300, height: Int = 200, alpha: Boolean, animated: Boolean): ByteArray {
        val flags = (if (alpha) 0x10 else 0) or (if (animated) 0x02 else 0)
        val data = ByteArrayOutputStream().apply {
            write(byteArrayOf(flags.toByte(), 0, 0, 0))
            writeInt24Le(width - 1)
            writeInt24Le(height - 1)
        }.toByteArray()
        return riff("VP8X", data)
    }

    private fun riff(fourCc: String, chunkData: ByteArray): ByteArray {
        val body = ByteArrayOutputStream().apply {
            write("WEBP".toByteArray())
            write(fourCc.toByteArray())
            writeIntLe(chunkData.size)
            write(chunkData)
        }.toByteArray()
        return ByteArrayOutputStream().apply {
            write("RIFF".toByteArray())
            writeIntLe(body.size)
            write(body)
        }.toByteArray()
    }

    private fun ByteArrayOutputStream.chunk(type: String, data: ByteArray) {
        writeIntBe(data.size)
        write(type.toByteArray())
        write(data)
        writeIntBe(0) // CRC, not checked by the sniffer
    }

    private fun ByteArrayOutputStream.writeIntBe(v: Int) =
        write(byteArrayOf((v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte()))

    private fun ByteArrayOutputStream.writeIntLe(v: Int) =
        write(byteArrayOf(v.toByte(), (v ushr 8).toByte(), (v ushr 16).toByte(), (v ushr 24).toByte()))

    private fun ByteArrayOutputStream.writeInt24Le(v: Int) =
        write(byteArrayOf(v.toByte(), (v ushr 8).toByte(), (v ushr 16).toByte()))

    private fun ByteArrayOutputStream.writeShortLe(v: Int) = write(byteArrayOf(v.toByte(), (v ushr 8).toByte()))
}
