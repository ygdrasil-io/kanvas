package org.graphiks.kanvas.codec.wbmp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.graphiks.kanvas.codec.Codec
import org.skia.foundation.SkBitmap
import java.io.ByteArrayOutputStream

class WbmpEncoderTest {

    @Test
    fun `WBMP framing starts with type=0 fixHeader=0 then dimensions`() {
        val src = checkerboard(8, 8)
        val bytes = WbmpEncoder.encode(src)!!
        assertEquals(0.toByte(), bytes[0])
        assertEquals(0.toByte(), bytes[1])
        assertEquals(0x08.toByte(), bytes[2])
        assertEquals(0x08.toByte(), bytes[3])
    }

    @Test
    fun `multi-byte width encodes correctly for 200 pixels`() {
        val src = SkBitmap(200, 1)
        for (i in 0 until 200) src.pixels[i] = 0xFFFFFFFF.toInt()
        val bytes = WbmpEncoder.encode(src)!!
        assertEquals(0.toByte(), bytes[0])
        assertEquals(0.toByte(), bytes[1])
        assertEquals(0x81.toByte(), bytes[2])
        assertEquals(0x48.toByte(), bytes[3])
        assertEquals(0x01.toByte(), bytes[4])
    }

    @Test
    fun `white pixel encodes as bit=1, black as bit=0`() {
        val src = SkBitmap(8, 1)
        for (i in 0 until 8) {
            src.pixels[i] = if (i % 2 == 0) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
        }
        val bytes = WbmpEncoder.encode(src)!!
        assertEquals(5, bytes.size)
        assertEquals(0xAA.toByte(), bytes[4])
    }

    @Test
    fun `row padding zero-fills trailing bits when width is not a multiple of 8`() {
        val src = SkBitmap(5, 1)
        for (i in 0 until 5) src.pixels[i] = 0xFFFFFFFF.toInt()
        val bytes = WbmpEncoder.encode(src)!!
        assertEquals(5, bytes.size)
        assertEquals(0xF8.toByte(), bytes[4])
    }

    @Test
    fun `round-trip encode then decode produces same pixels`() {
        val src = SkBitmap(8, 4)
        for (y in 0 until 4) for (x in 0 until 8) {
            src.pixels[y * 8 + x] = if ((x + y) and 1 == 0) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
        val bytes = WbmpEncoder.encode(src)!!
        val decoded = decodeWbmp(bytes)
        assertEquals(8, decoded.width)
        assertEquals(4, decoded.height)
        for (y in 0 until 4) for (x in 0 until 8) {
            assertEquals(src.getPixel(x, y), decoded.getPixel(x, y), "($x,$y)")
        }
    }

    @Test
    fun `luminance threshold picks bright colours as white`() {
        val src = SkBitmap(4, 1)
        src.pixels[0] = 0xFFFFFFFF.toInt()
        src.pixels[1] = 0xFF000000.toInt()
        src.pixels[2] = 0xFF808080.toInt()
        src.pixels[3] = 0xFF7F7F7F.toInt()
        val bytes = WbmpEncoder.encode(src)!!
        assertEquals(0xA0.toByte(), bytes[4])
    }

    @Test
    fun `encode degenerate bitmap returns null`() {
        assertNull(WbmpEncoder.encode(SkBitmap(0, 0)))
    }

    @Test
    fun `OutputStream overload matches direct encode`() {
        val src = checkerboard(8, 8)
        val viaData = WbmpEncoder.encode(src)!!
        val baos = ByteArrayOutputStream()
        assertTrue(WbmpEncoder.encode(baos, src))
        assertEquals(viaData.toList(), baos.toByteArray().toList())
    }

    @Test
    fun `encode an 8x8 B&W checkerboard produces non-empty output`() {
        val src = checkerboard(8, 8)
        val data = WbmpEncoder.encode(src)
        assertNotNull(data)
        assertEquals(12, data!!.size)
    }

    private fun checkerboard(width: Int, height: Int): SkBitmap {
        val b = SkBitmap(width, height)
        for (y in 0 until height) for (x in 0 until width) {
            val white = (x + y) % 2 == 0
            b.pixels[y * width + x] = if (white) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
        }
        return b
    }

    private fun decodeWbmp(bytes: ByteArray): SkBitmap {
        val codec = Codec.MakeFromData(bytes)
        assertNotNull(codec, "pure Kotlin WBMP codec must decode WBMP bytes")
        val (bitmap, result) = codec!!.getImage()
        assertEquals(Codec.Result.kSuccess, result)
        assertNotNull(bitmap)
        return bitmap!!
    }
}
