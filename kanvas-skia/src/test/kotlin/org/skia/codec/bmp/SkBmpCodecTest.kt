package org.skia.codec.bmp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.codec.SkCodec
import org.skia.codec.SkEncodedImageFormat
import org.skia.foundation.SkAlphaType
import org.skia.foundation.SkColorType
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/**
 * D3.3 verification suite for [SkBmpCodec].
 *
 * Covers `"BM"` signature dispatch, decode plumbing, pixel parity
 * (BMP is lossless so 24-bit RGB writes round-trip byte-identical
 * for opaque pixels), and rejection of non-BMP byte streams.
 */
class SkBmpCodecTest {

    @Test
    fun `MakeFromData rejects non-BMP bytes`() {
        assertNull(SkCodec.MakeFromData(ByteArray(0)))
        assertNull(SkCodec.MakeFromData("not-a-bmp".toByteArray()))
        // Missing the 'M' byte : not a BMP.
        assertNull(SkCodec.MakeFromData(byteArrayOf(0x42, 0x00, 0x00, 0x00)))
    }

    @Test
    fun `MakeFromData dispatches a synthetic BMP to SkBmpCodec`() {
        val bytes = synthBmp(width = 5, height = 3)
        val codec = SkCodec.MakeFromData(bytes)
        assertNotNull(codec)
        assertTrue(codec is SkBmpCodec)
        assertEquals(SkEncodedImageFormat.kBMP, codec!!.getEncodedFormat())
        assertEquals(5, codec.dimensions().width)
        assertEquals(3, codec.dimensions().height)
    }

    @Test
    fun `BMP round-trips RGB pixels byte-identically`() {
        // BMP is lossless ; a 24-bit BMP encode + decode of an opaque
        // image must come back pixel-identical.
        val src = BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until 4) for (x in 0 until 4) {
            val r = x * 60 + 0x20
            val g = y * 60 + 0x20
            src.setRGB(x, y, (r shl 16) or (g shl 8) or 0x80)
        }
        val bytes = ByteArrayOutputStream().also { ImageIO.write(src, "bmp", it) }.toByteArray()
        val codec = SkCodec.MakeFromData(bytes)!!
        val (bitmap, result) = codec.getImage()
        assertEquals(SkCodec.Result.kSuccess, result)
        assertNotNull(bitmap)
        for (y in 0 until 4) for (x in 0 until 4) {
            val expected = (0xFF shl 24) or (src.getRGB(x, y) and 0xFFFFFF)
            assertEquals(expected, bitmap!!.getPixel(x, y), "pixel ($x,$y)")
        }
    }

    @Test
    fun `getImage produces an 8888 sRGB unpremul bitmap`() {
        val codec = SkCodec.MakeFromData(synthBmp(8, 8))!!
        val info = codec.getInfo()
        assertEquals(SkColorType.kRGBA_8888, info.colorType)
        assertEquals(SkAlphaType.kUnpremul, info.alphaType)
        assertTrue(info.colorSpace.isSRGB())
    }

    private fun synthBmp(width: Int, height: Int): ByteArray {
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until height) for (x in 0 until width) {
            img.setRGB(x, y, (x shl 16) or (y shl 8) or 0x40)
        }
        return ByteArrayOutputStream().also { ImageIO.write(img, "bmp", it) }.toByteArray()
    }
}
