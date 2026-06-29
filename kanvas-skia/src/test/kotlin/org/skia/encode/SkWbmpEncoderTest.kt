package org.skia.encode

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.graphiks.kanvas.codec.Codec
import org.skia.foundation.SkBitmap
import java.io.ByteArrayOutputStream

/**
 * R-suivi.19 verification suite for [SkWbmpEncoder].
 *
 * Asserts :
 *  - the framing starts with the WBMP type/header bytes ;
 *  - the width / height are encoded as multi-byte unsigned integers
 *    (single-byte for small values, multi-byte for ≥128) ;
 *  - 1-bit-per-pixel pixel data is byte-aligned per row ;
 *  - luminance thresholding picks the expected pattern for a
 *    black-and-white checkerboard.
 */
class SkWbmpEncoderTest {

    @Test
    fun `WBMP framing starts with type=0 fixHeader=0 then dimensions`() {
        val src = checkerboard(8, 8)
        val bytes = SkWbmpEncoder.Encode(src)!!.toByteArray()
        // Type field
        assertEquals(0.toByte(), bytes[0])
        // FixHeaderField
        assertEquals(0.toByte(), bytes[1])
        // Width = 8 → single-byte 0x08
        assertEquals(0x08.toByte(), bytes[2])
        // Height = 8 → single-byte 0x08
        assertEquals(0x08.toByte(), bytes[3])
    }

    @Test
    fun `multi-byte width encodes correctly for 200 pixels`() {
        // 200 = 0b1100_1000 = 0x01 0x48 in WBMP multi-byte format.
        // Low 7 bits : 0x48 (= 200 & 0x7F = 0x48). High bits : 200 >> 7 = 0x01.
        // Encoded as : [0x81, 0x48] (0x80 on the leading byte to flag "more follows").
        val src = SkBitmap(200, 1)
        for (i in 0 until 200) src.pixels[i] = 0xFFFFFFFF.toInt() // white
        val bytes = SkWbmpEncoder.Encode(src)!!.toByteArray()
        assertEquals(0.toByte(), bytes[0])
        assertEquals(0.toByte(), bytes[1])
        assertEquals(0x81.toByte(), bytes[2]) // leading byte : continuation + 0x01
        assertEquals(0x48.toByte(), bytes[3]) // low 7 bits of width
        assertEquals(0x01.toByte(), bytes[4]) // height = 1
    }

    @Test
    fun `white pixel encodes as bit=1, black as bit=0`() {
        val src = SkBitmap(8, 1)
        // White, black, white, black, …
        for (i in 0 until 8) {
            src.pixels[i] = if (i % 2 == 0) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
        }
        val bytes = SkWbmpEncoder.Encode(src)!!.toByteArray()
        // Header is 4 bytes (type, fixHeader, width=8, height=1).
        // Pixel data : 1 byte, MSB-first : 0b10101010 = 0xAA.
        assertEquals(5, bytes.size)
        assertEquals(0xAA.toByte(), bytes[4])
    }

    @Test
    fun `row padding zero-fills trailing bits when width is not a multiple of 8`() {
        // Width 5, height 1 — 5 white pixels, then 3 trailing padding bits.
        val src = SkBitmap(5, 1)
        for (i in 0 until 5) src.pixels[i] = 0xFFFFFFFF.toInt()
        val bytes = SkWbmpEncoder.Encode(src)!!.toByteArray()
        // Type, fixHeader, width=5, height=1 → 4-byte header + 1-byte row.
        assertEquals(5, bytes.size)
        // Pixel byte : 5 ones then 3 zeros (right-padded) → 0b11111000 = 0xF8.
        assertEquals(0xF8.toByte(), bytes[4])
    }

    @Test
    fun `real WBMP fixture re-encodes with valid header and round-trips pixels`() {
        val src = decodeWbmp(readFixture("/codec-real-images/wbmp/type0_3x2.wbmp"))
        val bytes = SkWbmpEncoder.Encode(src)!!.toByteArray()
        assertEquals(0.toByte(), bytes[0], "type field")
        assertEquals(0.toByte(), bytes[1], "fixed header field")
        assertEquals(0x03.toByte(), bytes[2], "width")
        assertEquals(0x02.toByte(), bytes[3], "height")
        assertEquals(6, bytes.size, "4-byte header + 2 one-byte rows")

        val roundTrip = decodeWbmp(bytes)
        assertSamePixels(src, roundTrip, "type0_3x2.wbmp")
    }

    @Test
    fun `luminance threshold picks bright colours as white`() {
        val src = SkBitmap(4, 1)
        src.pixels[0] = 0xFFFFFFFF.toInt() // pure white → 1
        src.pixels[1] = 0xFF000000.toInt() // pure black → 0
        src.pixels[2] = 0xFF808080.toInt() // mid-grey (Y≈128) → > 127.5 → 1
        src.pixels[3] = 0xFF7F7F7F.toInt() // just under mid-grey → < 127.5 → 0
        val bytes = SkWbmpEncoder.Encode(src)!!.toByteArray()
        // Row byte = 0b1010_xxxx (4-pixel row, 4 trailing pad bits) = 0xA0.
        assertEquals(0xA0.toByte(), bytes[4])
    }

    @Test
    fun `Encode degenerate bitmap returns null`() {
        assertNull(SkWbmpEncoder.Encode(SkBitmap(0, 0)))
    }

    @Test
    fun `Encode to OutputStream agrees with Encode to SkData`() {
        val src = checkerboard(8, 8)
        val viaData = SkWbmpEncoder.Encode(src)!!.toByteArray()
        val baos = ByteArrayOutputStream()
        assertTrue(SkWbmpEncoder.Encode(baos, src))
        val viaStream = baos.toByteArray()
        assertEquals(viaData.toList(), viaStream.toList())
    }

    @Test
    fun `Encode an 8x8 B&W checkerboard produces non-empty output`() {
        val src = checkerboard(8, 8)
        val data = SkWbmpEncoder.Encode(src)
        assertNotNull(data)
        val bytes = data!!.toByteArray()
        // 4-byte header + 8 rows × 1 byte = 12 bytes.
        assertEquals(12, bytes.size)
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

    private fun readFixture(path: String): ByteArray {
        val stream = javaClass.getResourceAsStream(path)
        assertNotNull(stream, "missing real-image fixture $path")
        return stream!!.use { it.readBytes() }
    }

    private fun assertSamePixels(expected: SkBitmap, actual: SkBitmap, label: String) {
        assertEquals(expected.width, actual.width, "$label width")
        assertEquals(expected.height, actual.height, "$label height")
        for (y in 0 until expected.height) for (x in 0 until expected.width) {
            assertEquals(expected.getPixel(x, y), actual.getPixel(x, y), "$label pixel($x,$y)")
        }
    }
}
