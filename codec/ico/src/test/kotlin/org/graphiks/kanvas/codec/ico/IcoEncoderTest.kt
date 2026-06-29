package org.graphiks.kanvas.codec.ico

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.graphiks.kanvas.codec.Codec
import org.skia.foundation.SkBitmap
import java.io.ByteArrayOutputStream

class IcoEncoderTest {

    @Test
    fun `encode single 32x32 entry round-trips through ICO decoder`() {
        val bitmap = SkBitmap(32, 32)
        for (y in 0 until 32) for (x in 0 until 32) {
            bitmap.pixels[y * 32 + x] = (0xFF shl 24) or ((x * 8) shl 16) or ((y * 8) shl 8)
        }
        val bytes = IcoEncoder.encode(bitmap)!!
        // Verify starts with ICO magic (00 00 01 00 for ICO with 1 entry)
        assertEquals(0, bytes[0].toInt() and 0xFF)
        assertEquals(0, bytes[1].toInt() and 0xFF)
        assertEquals(1, bytes[2].toInt() and 0xFF) // type 1 = ICO
        assertEquals(0, bytes[3].toInt() and 0xFF)
        assertEquals(1, readU16LE(bytes, 4), "1 entry in directory")
        // Decode round-trip
        val codec = Codec.MakeFromData(bytes)
        assertNotNull(codec)
        val (decoded, result) = codec!!.getImage()
        assertEquals(Codec.Result.kSuccess, result)
        assertNotNull(decoded)
        assertEquals(32, decoded!!.width)
        assertEquals(32, decoded.height)
    }

    @Test
    fun `encode degenerate bitmap returns null`() {
        assertNull(IcoEncoder.encode(SkBitmap(0, 0)))
    }

    @Test
    fun `encode to OutputStream matches direct encode`() {
        val bitmap = SkBitmap(16, 16)
        for (i in 0 until 256) bitmap.pixels[i] = 0xFF808080.toInt()
        val viaData = IcoEncoder.encode(bitmap)!!
        val baos = ByteArrayOutputStream()
        assertTrue(IcoEncoder.encode(baos, bitmap))
        assertEquals(viaData.toList(), baos.toByteArray().toList())
    }

    @Test
    fun `encode multi-image ICO round-trips`() {
        val bm1 = SkBitmap(16, 16)
        for (i in 0 until 256) bm1.pixels[i] = 0xFFFF0000.toInt()
        val bm2 = SkBitmap(32, 32)
        for (i in 0 until 1024) bm2.pixels[i] = 0xFF00FF00.toInt()
        val entries = listOf(
            IcoEncoder.Entry(bm1),
            IcoEncoder.Entry(bm2),
        )
        val bytes = IcoEncoder.encode(entries)!!
        assertEquals(0, bytes[0].toInt() and 0xFF)
        assertEquals(0, bytes[1].toInt() and 0xFF)
        assertEquals(1, bytes[2].toInt() and 0xFF)
        assertEquals(0, bytes[3].toInt() and 0xFF)
        assertEquals(2, readU16LE(bytes, 4), "2 entries in directory")
        val codec = Codec.MakeFromData(bytes)
        assertNotNull(codec)
        val (decoded, result) = codec!!.getImage()
        assertEquals(Codec.Result.kSuccess, result)
        assertEquals(32, decoded!!.width)
        assertEquals(32, decoded.height)
    }

    @Test
    fun `encode multi-image ICO with BMP payload round-trips`() {
        val bm1 = SkBitmap(8, 8)
        for (i in 0 until 64) bm1.pixels[i] = 0xFF0000FF.toInt()
        val bm2 = SkBitmap(16, 16)
        for (i in 0 until 256) bm2.pixels[i] = 0xFFFF0000.toInt()
        val entries = listOf(
            IcoEncoder.Entry(bm1, IcoEncoder.PayloadFormat.PNG),
            IcoEncoder.Entry(bm2, IcoEncoder.PayloadFormat.BMP),
        )
        val bytes = IcoEncoder.encode(entries)!!
        assertEquals(2, readU16LE(bytes, 4), "2 entries")
        val codec = Codec.MakeFromData(bytes)
        assertNotNull(codec)
        val (decoded, result) = codec!!.getImage()
        assertEquals(Codec.Result.kSuccess, result)
        assertEquals(16, decoded!!.width)
    }

    @Test
    fun `dimensions 0 in ICO directory mean 256`() {
        val bitmap = SkBitmap(256, 256)
        for (i in 0 until 65536) bitmap.pixels[i] = 0xFF808080.toInt()
        val bytes = IcoEncoder.encode(bitmap)!!
        // Directory entry for 256 → width byte = 0, height byte = 0
        val dirWidth = bytes[6].toInt() and 0xFF
        assertEquals(0, dirWidth, "256 width encoded as 0 in ICO directory")
        val dirHeight = bytes[7].toInt() and 0xFF
        assertEquals(0, dirHeight, "256 height encoded as 0 in ICO directory")
    }

    private fun readU16LE(buf: ByteArray, off: Int): Int =
        (buf[off].toInt() and 0xFF) or ((buf[off + 1].toInt() and 0xFF) shl 8)
}
