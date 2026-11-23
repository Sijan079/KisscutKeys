package app.kisscut.keys.shared.core.gif

import java.awt.image.BufferedImage
import java.awt.image.IndexColorModel
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import javax.imageio.metadata.IIOMetadataNode
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Checks the reader against GIFs written by a real encoder (the JDK's ImageIO). */
class GifReaderImageIoTest {
    private fun indexedNoise(width: Int, height: Int, colors: Int, seed: Int): BufferedImage {
        val r = Random(seed)
        val rgb = ByteArray(colors) { r.nextInt(256).toByte() }
        val model = IndexColorModel(8, colors, rgb, rgb.reversedArray(), ByteArray(colors) { (it * 7).toByte() })
        val image = BufferedImage(width, height, BufferedImage.TYPE_BYTE_INDEXED, model)
        val raster = image.raster
        for (y in 0 until height) for (x in 0 until width) raster.setSample(x, y, 0, r.nextInt(colors))
        return image
    }

    private fun write(image: BufferedImage, interlaced: Boolean): ByteArray {
        val writer = ImageIO.getImageWritersByFormatName("gif").next()
        val param = writer.defaultWriteParam.apply {
            progressiveMode = if (interlaced) ImageWriteParam.MODE_DEFAULT else ImageWriteParam.MODE_DISABLED
        }
        val out = ByteArrayOutputStream()
        ImageIO.createImageOutputStream(out).use { stream ->
            writer.output = stream
            writer.write(null, IIOImage(image, null, null), param)
        }
        writer.dispose()
        return out.toByteArray()
    }

    private fun interlaceFlag(gif: ByteArray): Boolean {
        val reader = ImageIO.getImageReadersByFormatName("gif").next()
        ImageIO.createImageInputStream(ByteArrayInputStream(gif)).use { stream ->
            reader.input = stream
            val tree = reader.getImageMetadata(0).getAsTree("javax_imageio_gif_image_1.0") as IIOMetadataNode
            val descriptor = tree.getElementsByTagName("ImageDescriptor").item(0) as IIOMetadataNode
            return descriptor.getAttribute("interlaceFlag") == "TRUE"
        }
    }

    private fun check(width: Int, height: Int, colors: Int, interlaced: Boolean, seed: Int) {
        val image = indexedNoise(width, height, colors, seed)
        val gif = write(image, interlaced)
        assertEquals(interlaced, interlaceFlag(gif), "ImageIO wrote the interlace mode we asked for")

        val reader = GifReader(gif)
        assertEquals(width, reader.width)
        assertEquals(height, reader.height)
        val frame = reader.frames().next()
        assertNull(frame.missing)
        val expected = ByteArray(width * height) { image.raster.getSample(it % width, it / width, 0).toByte() }
        assertContentEquals(expected, frame.indices)

        val model = image.colorModel as IndexColorModel
        for (i in 0 until colors) {
            assertEquals(model.getRGB(i) or (0xFF shl 24), frame.palette[i], "palette entry $i")
        }
    }

    @Test
    fun fullPaletteNoise() = check(300, 300, 256, interlaced = false, seed = 1)

    @Test
    fun fullPaletteNoiseInterlaced() = check(301, 299, 256, interlaced = true, seed = 2)

    @Test
    fun smallPalettesAndOddSizes() {
        for ((i, colors) in listOf(2, 4, 16, 64).withIndex()) {
            check(17 + i, 9 + i, colors, interlaced = i % 2 == 0, seed = 10 + i)
        }
    }
}
