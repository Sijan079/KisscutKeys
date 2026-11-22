package com.example.stickers.shared.core.gif

import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import javax.imageio.metadata.IIOMetadataNode
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/** Checks the writer's output with an independent decoder (the JDK's ImageIO). */
class GifWriterImageIoTest {
    @Test
    fun imageIoReadsEveryFrameAsWritten() {
        val r = Random(4)
        val frames = listOf(
            GifFrame(0, 0, 300, 200, IntArray(256) { (0xFF shl 24) or r.nextInt(0x1000000) }, -1,
                GifDisposal.KEEP, 7, ByteArray(300 * 200) { r.nextInt(256).toByte() }),
            GifFrame(20, 30, 51, 17, IntArray(5) { (0xFF shl 24) or r.nextInt(0x1000000) }, 4,
                GifDisposal.CLEAR, 120, ByteArray(51 * 17) { r.nextInt(5).toByte() }),
        )
        val writer = GifWriter(300, 200, 0)
        frames.forEach(writer::add)
        val bytes = writer.finish()

        val reader = ImageIO.getImageReadersByFormatName("gif").next()
        ImageIO.createImageInputStream(ByteArrayInputStream(bytes)).use { stream ->
            reader.input = stream
            assertEquals(frames.size, reader.getNumImages(true))
            frames.forEachIndexed { i, f ->
                // ImageIO returns each frame's own rectangle, palette indices unchanged.
                val raster = reader.read(i).raster
                assertEquals(f.width to f.height, raster.width to raster.height)
                for (p in f.indices.indices) {
                    val sample = raster.getSample(p % f.width, p / f.width, 0)
                    assertEquals(f.indices[p].toInt() and 0xFF, sample, "frame $i px $p")
                }
                val tree = reader.getImageMetadata(i).getAsTree("javax_imageio_gif_image_1.0") as IIOMetadataNode
                val descriptor = tree.getElementsByTagName("ImageDescriptor").item(0) as IIOMetadataNode
                assertEquals(f.left.toString(), descriptor.getAttribute("imageLeftPosition"))
                assertEquals(f.top.toString(), descriptor.getAttribute("imageTopPosition"))
                val control = tree.getElementsByTagName("GraphicControlExtension").item(0) as IIOMetadataNode
                assertEquals(f.delayCs.toString(), control.getAttribute("delayTime"))
                assertEquals(if (f.disposal == GifDisposal.CLEAR) "restoreToBackgroundColor" else "doNotDispose",
                    control.getAttribute("disposalMethod"))
            }
        }
    }
}
