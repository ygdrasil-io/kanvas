package org.skia.encode

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.graphiks.kanvas.codec.Codec
import org.skia.foundation.SkBitmap
import org.graphiks.math.SkColorGetA
import org.graphiks.math.SkColorGetB
import org.graphiks.math.SkColorGetG
import org.graphiks.math.SkColorGetR
import java.io.ByteArrayOutputStream

/**
 * R-suivi.19 verification suite for [SkBmpEncoder].
 *
 * Asserts :
 *  - the framing starts with the `'BM'` signature and a well-formed
 *    `BITMAPFILEHEADER` + `BITMAPINFOHEADER` ;
 *  - encode → decode round-trips opaque RGB pixels byte-identical
 *    through [Codec] / :codec:bmp ;
 *  - the [SkBmpEncoder.BmpFormat.kBGR_888] code path drops alpha and
 *    pads rows correctly when the width is not a multiple of 4 bytes.
 */
class SkBmpEncoderTest {

    @Test
    fun `Encode returns non-null bytes starting with 'BM' signature`() {
        val bitmap = makeGradient(4, 4)
        val data = SkBmpEncoder.Encode(bitmap)
        assertNotNull(data)
        val bytes = data!!.toByteArray()
        assertTrue(bytes.size > 14 + 40, "BMP must carry both headers + pixel data")
        assertEquals('B'.code.toByte(), bytes[0])
        assertEquals('M'.code.toByte(), bytes[1])
    }

    @Test
    fun `Encode reports a valid file size in the file header`() {
        val bitmap = makeGradient(4, 4)
        val bytes = SkBmpEncoder.Encode(bitmap)!!.toByteArray()
        val fileSize = readU32LE(bytes, 2)
        assertEquals(bytes.size, fileSize)
        // Pixel data offset should be 54 (14-byte file header + 40-byte DIB).
        assertEquals(54, readU32LE(bytes, 10))
    }

    @Test
    fun `BGRA round-trip via pure Kotlin BMP codec preserves RGB channels byte-identical`() {
        val src = makeGradient(4, 4)
        val bytes = SkBmpEncoder.Encode(src)!!.toByteArray()
        val decoded = decodeBmp(bytes)
        assertEquals(src.width, decoded.width)
        assertEquals(src.height, decoded.height)
        for (y in 0 until src.height) for (x in 0 until src.width) {
            val expected = src.getPixel(x, y)
            val actualArgb = decoded.getPixel(x, y)
            // RGB channels must match. Alpha handling varies by reader,
            // so we only assert on R / G / B here.
            assertEquals(SkColorGetR(expected), SkColorGetR(actualArgb), "R($x,$y)")
            assertEquals(SkColorGetG(expected), SkColorGetG(actualArgb), "G($x,$y)")
            assertEquals(SkColorGetB(expected), SkColorGetB(actualArgb), "B($x,$y)")
        }
    }

    @Test
    fun `real BMP fixtures re-encode with valid headers and round-trip pixels`() {
        val fixtures = listOf(
            "bottom_up_24.bmp",
            "palette_8.bmp",
            "top_down_32_alpha.bmp",
        )
        for (fixture in fixtures) {
            val src = decodeBmp(readFixture("/codec-real-images/bmp/$fixture"))
            val encoded = SkBmpEncoder.Encode(src)!!.toByteArray()
            assertEquals('B'.code.toByte(), encoded[0], "$fixture BMP signature byte 0")
            assertEquals('M'.code.toByte(), encoded[1], "$fixture BMP signature byte 1")
            assertEquals(encoded.size, readU32LE(encoded, 2), "$fixture file-size header")
            assertEquals(54, readU32LE(encoded, 10), "$fixture pixel-data offset")
            assertEquals(40, readU32LE(encoded, 14), "$fixture BITMAPINFOHEADER size")
            assertEquals(32, readU16LE(encoded, 14 + 14), "$fixture default encoder bpp")

            val roundTrip = decodeBmp(encoded)
            assertSamePixels(src, roundTrip, fixture)
        }
    }

    @Test
    fun `BGR_888 format drops alpha and uses 24-bit pixels`() {
        val src = SkBitmap(2, 2)
        src.pixels[0] = 0x80FF0000.toInt() // alpha 50%, red
        src.pixels[1] = 0xFF00FF00.toInt() // opaque green
        src.pixels[2] = 0x4000FFFF.toInt() // 25%, cyan
        src.pixels[3] = 0x00112233 // fully transparent (alpha dropped)

        val bytes = SkBmpEncoder.Encode(
            src,
            SkBmpEncoder.Options(format = SkBmpEncoder.BmpFormat.kBGR_888),
        )!!.toByteArray()
        // Bits per pixel = 24
        val bpp = readU16LE(bytes, 14 + 14)
        assertEquals(24, bpp)
        val decoded = decodeBmp(bytes)
        // RGB must come back ignoring alpha.
        assertEquals(0xFF, SkColorGetR(decoded.getPixel(0, 0)))
        assertEquals(0xFF, SkColorGetG(decoded.getPixel(1, 0)))
        // Pixel 3 was fully transparent ; without alpha its RGB still encodes.
        assertEquals(0x11, SkColorGetR(decoded.getPixel(1, 1)))
    }

    @Test
    fun `row padding aligns rows to a multiple of 4 bytes`() {
        // Width 3, 24bpp → 9 bytes per row + 3 bytes padding = 12.
        val src = SkBitmap(3, 2)
        for (i in 0 until 6) src.pixels[i] = 0xFF808080.toInt()
        val bytes = SkBmpEncoder.Encode(
            src,
            SkBmpEncoder.Options(format = SkBmpEncoder.BmpFormat.kBGR_888),
        )!!.toByteArray()
        val pixelDataSize = readU32LE(bytes, 14 + 20)
        assertEquals(12 * 2, pixelDataSize, "row size must be aligned to 4 bytes")
    }

    @Test
    fun `Encode degenerate bitmap returns null`() {
        // SkBitmap requires positive dimensions, so we can't construct
        // a zero-dim bitmap directly ; degenerate input is the contract
        // the encoder defines for any future negative case.
        val bytes = SkBmpEncoder.Encode(SkBitmap(0, 0))
        assertNull(bytes)
    }

    @Test
    fun `Encode to OutputStream agrees with Encode to SkData`() {
        val src = makeGradient(4, 4)
        val viaData = SkBmpEncoder.Encode(src)!!.toByteArray()
        val baos = ByteArrayOutputStream()
        assertTrue(SkBmpEncoder.Encode(baos, src))
        val viaStream = baos.toByteArray()
        assertEquals(viaData.toList(), viaStream.toList())
    }

    @Test
    fun `Encode preserves alpha when round-tripped through a BGRA-aware reader`() {
        val src = SkBitmap(2, 1)
        src.pixels[0] = 0x40FF0000.toInt()
        src.pixels[1] = 0x80FFFFFF.toInt()
        val bytes = SkBmpEncoder.Encode(src)!!.toByteArray()
        // 32-bit BMP file ; alpha byte follows BGR.
        val bpp = readU16LE(bytes, 14 + 14)
        assertEquals(32, bpp)
        val dataOffset = readU32LE(bytes, 10)
        // First pixel : B G R A bytes.
        val p0a = bytes[dataOffset + 3].toInt() and 0xFF
        assertEquals(SkColorGetA(src.pixels[0]), p0a)
        val p1a = bytes[dataOffset + 7].toInt() and 0xFF
        assertEquals(SkColorGetA(src.pixels[1]), p1a)
    }

    private fun makeGradient(width: Int, height: Int): SkBitmap {
        val b = SkBitmap(width, height)
        for (y in 0 until height) for (x in 0 until width) {
            val r = (x * 255 / maxOf(1, width - 1)).coerceIn(0, 255)
            val g = (y * 255 / maxOf(1, height - 1)).coerceIn(0, 255)
            b.pixels[y * width + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or 0x40
        }
        return b
    }

    private fun decodeBmp(bytes: ByteArray): SkBitmap {
        val codec = Codec.MakeFromData(bytes)
        assertNotNull(codec, "pure Kotlin BMP codec must decode the produced BMP")
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

    private fun readU32LE(buf: ByteArray, off: Int): Int =
        (buf[off].toInt() and 0xFF) or
            ((buf[off + 1].toInt() and 0xFF) shl 8) or
            ((buf[off + 2].toInt() and 0xFF) shl 16) or
            ((buf[off + 3].toInt() and 0xFF) shl 24)

    private fun readU16LE(buf: ByteArray, off: Int): Int =
        (buf[off].toInt() and 0xFF) or ((buf[off + 1].toInt() and 0xFF) shl 8)
}
