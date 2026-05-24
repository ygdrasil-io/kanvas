package org.skia.codec.wbmp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.codec.SkCodec
import org.skia.foundation.SkEncodedImageFormat
import org.skia.foundation.SkAlphaType
import org.skia.foundation.SkColorType
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/**
 * D3.3 verification suite for [SkWbmpCodec].
 *
 * Covers the deliberately-loose WBMP header sniff (type byte 0,
 * fixed header byte with bits 0..4 + 7 zero, valid VLQ width and
 * height) and decode of a 1-bit raster through ImageIO.
 *
 * The "loose magic" property gets its own test : an arbitrary
 * `00 00`-prefixed buffer that fails one of the WBMP invariants
 * (e.g. zero width) must NOT dispatch to [SkWbmpCodec], or every
 * `00 00`-prefixed unrelated file would be misclassified.
 */
class SkWbmpCodecTest {

    @Test
    fun `MakeFromData rejects clearly non-WBMP bytes`() {
        assertNull(SkCodec.MakeFromData(ByteArray(0)))
        assertNull(SkCodec.MakeFromData("not-a-wbmp".toByteArray()))
        // Type byte != 0 : doesn't match upstream's "B&W, no compression".
        assertNull(SkCodec.MakeFromData(byteArrayOf(0x01, 0x00, 0x10, 0x10)))
    }

    @Test
    fun `MakeFromData rejects a 00 00 prefix without a valid VLQ width`() {
        // A buffer that starts with 00 00 but has zero width — the
        // upstream walker rejects width == 0.
        val bad = byteArrayOf(0x00, 0x00, 0x00, 0x00)
        assertNull(SkCodec.MakeFromData(bad))
    }

    @Test
    fun `MakeFromData dispatches a synthetic WBMP to SkWbmpCodec`() {
        val bytes = synthWbmp(width = 8, height = 8)
        val codec = SkCodec.MakeFromData(bytes)
        assertNotNull(codec)
        assertEquals(SkEncodedImageFormat.kWBMP, codec!!.getEncodedFormat())
        assertEquals(8, codec.dimensions().width)
        assertEquals(8, codec.dimensions().height)
    }

    @Test
    fun `getImage produces an 8888 sRGB unpremul bitmap`() {
        val codec = SkCodec.MakeFromData(synthWbmp(8, 8))!!
        val info = codec.getInfo()
        assertEquals(SkColorType.kRGBA_8888, info.colorType)
        assertEquals(SkAlphaType.kUnpremul, info.alphaType)
        assertTrue(info.colorSpace.isSRGB())
        val (bitmap, result) = codec.getImage()
        assertEquals(SkCodec.Result.kSuccess, result)
        assertNotNull(bitmap)
        // WBMP is 1-bit B&W : every pixel must be either fully black
        // or fully white, both fully opaque.
        for (y in 0 until 8) for (x in 0 until 8) {
            val px = bitmap!!.getPixel(x, y)
            assertTrue(
                px == 0xFF000000.toInt() || px == 0xFFFFFFFF.toInt(),
                "WBMP pixel ($x,$y) must be pure B/W : got ${px.toString(16)}",
            )
        }
    }

    private fun synthWbmp(width: Int, height: Int): ByteArray {
        // ImageIO's WBMP writer takes a TYPE_BYTE_BINARY image.
        val img = BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY)
        for (y in 0 until height) for (x in 0 until width) {
            // Checkerboard — guarantees mixed black/white pixels.
            img.setRGB(x, y, if ((x + y) and 1 == 0) 0xFFFFFFFF.toInt() else 0xFF000000.toInt())
        }
        return ByteArrayOutputStream().also { ImageIO.write(img, "wbmp", it) }.toByteArray()
    }
}
