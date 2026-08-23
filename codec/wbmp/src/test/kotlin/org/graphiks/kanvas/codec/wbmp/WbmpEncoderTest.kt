package org.graphiks.kanvas.codec.wbmp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.graphiks.kanvas.codec.Codec
import org.graphiks.kanvas.image.Bitmap
import org.graphiks.kanvas.image.ColorType
import java.io.ByteArrayOutputStream

class WbmpEncoderTest {

    @Test
    fun `unsupported source color type is refused without writing to stream`() {
        val dst = ByteArrayOutputStream().also { it.write(0x2A) }

        assertFalse(WbmpEncoder.encode(dst, Bitmap(1, 1, ColorType.RGB_565)))
        assertEquals(listOf(0x2A.toByte()), dst.toByteArray().toList())
    }

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
        val src = bitmap(200, 1, 0xFFFFFFFF.toInt())
        val bytes = WbmpEncoder.encode(src)!!
        assertEquals(0.toByte(), bytes[0])
        assertEquals(0.toByte(), bytes[1])
        assertEquals(0x81.toByte(), bytes[2])
        assertEquals(0x48.toByte(), bytes[3])
        assertEquals(0x01.toByte(), bytes[4])
    }

    @Test
    fun `white pixel encodes as bit=1, black as bit=0`() {
        val src = Bitmap(8, 1)
        for (i in 0 until 8) {
            src.setArgb(i, 0, if (i % 2 == 0) 0xFFFFFFFF.toInt() else 0xFF000000.toInt())
        }
        val bytes = WbmpEncoder.encode(src)!!
        assertEquals(5, bytes.size)
        assertEquals(0xAA.toByte(), bytes[4])
    }

    @Test
    fun `row padding zero-fills trailing bits when width is not a multiple of 8`() {
        val src = bitmap(5, 1, 0xFFFFFFFF.toInt())
        val bytes = WbmpEncoder.encode(src)!!
        assertEquals(5, bytes.size)
        assertEquals(0xF8.toByte(), bytes[4])
    }

    @Test
    fun `round-trip encode then decode produces same pixels`() {
        val src = Bitmap(8, 4)
        for (y in 0 until 4) for (x in 0 until 8) {
            src.setArgb(x, y, if ((x + y) and 1 == 0) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
        }
        val bytes = WbmpEncoder.encode(src)!!
        val decoded = decodeWbmp(bytes)
        assertEquals(8, decoded.width)
        assertEquals(4, decoded.height)
        for (y in 0 until 4) for (x in 0 until 8) {
            assertEquals(src.getArgb(x, y), decoded.getArgb(x, y), "($x,$y)")
        }
    }

    @Test
    fun `luminance threshold picks bright colours as white`() {
        val src = Bitmap(4, 1)
        src.setArgb(0, 0, 0xFFFFFFFF.toInt())
        src.setArgb(1, 0, 0xFF000000.toInt())
        src.setArgb(2, 0, 0xFF808080.toInt())
        src.setArgb(3, 0, 0xFF7F7F7F.toInt())
        val bytes = WbmpEncoder.encode(src)!!
        assertEquals(0xA0.toByte(), bytes[4])
    }

    @Test
    fun `encode degenerate bitmap returns null`() {
        assertNull(WbmpEncoder.encode(Bitmap(0, 0)))
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

    private fun checkerboard(width: Int, height: Int): Bitmap {
        val b = Bitmap(width, height)
        for (y in 0 until height) for (x in 0 until width) {
            val white = (x + y) % 2 == 0
            b.setArgb(x, y, if (white) 0xFFFFFFFF.toInt() else 0xFF000000.toInt())
        }
        return b
    }

    private fun decodeWbmp(bytes: ByteArray): Bitmap {
        val codec = Codec.MakeFromData(bytes)
        assertNotNull(codec, "pure Kotlin WBMP codec must decode WBMP bytes")
        val (bitmap, result) = codec!!.getImage()
        assertEquals(Codec.Result.kSuccess, result)
        assertNotNull(bitmap)
        return bitmap!!
    }

    private fun bitmap(width: Int, height: Int, argb: Int): Bitmap = Bitmap(width, height).also { bitmap ->
        for (y in 0 until height) for (x in 0 until width) bitmap.setArgb(x, y, argb)
    }
}
