package org.graphiks.kanvas.codec.bmp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.graphiks.kanvas.codec.Codec
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkICC
import org.skia.foundation.skcms.SkNamedGamut
import org.skia.foundation.skcms.SkNamedTransferFn
import org.graphiks.math.SkColorGetA
import org.graphiks.math.SkColorGetB
import org.graphiks.math.SkColorGetG
import org.graphiks.math.SkColorGetR
import java.io.ByteArrayOutputStream

class BmpEncoderTest {

    @Test
    fun `Encode returns non-null bytes starting with BM signature`() {
        val bitmap = makeGradient(4, 4)
        val bytes = BmpEncoder.encode(bitmap)!!
        assertTrue(bytes.size > 14 + 40, "BMP must carry both headers + pixel data")
        assertEquals('B'.code.toByte(), bytes[0])
        assertEquals('M'.code.toByte(), bytes[1])
    }

    @Test
    fun `Encode reports a valid file size in the file header`() {
        val bitmap = makeGradient(4, 4)
        val bytes = BmpEncoder.encode(bitmap)!!
        val fileSize = readU32LE(bytes, 2)
        assertEquals(bytes.size, fileSize)
        assertEquals(54, readU32LE(bytes, 10))
    }

    @Test
    fun `BGRA round-trip via pure Kotlin BMP codec preserves RGB channels byte-identical`() {
        val src = makeGradient(4, 4)
        val bytes = BmpEncoder.encode(src)!!
        val decoded = decodeBmp(bytes)
        assertEquals(src.width, decoded.width)
        assertEquals(src.height, decoded.height)
        for (y in 0 until src.height) for (x in 0 until src.width) {
            val expected = src.getPixel(x, y)
            val actualArgb = decoded.getPixel(x, y)
            assertEquals(SkColorGetR(expected), SkColorGetR(actualArgb), "R($x,$y)")
            assertEquals(SkColorGetG(expected), SkColorGetG(actualArgb), "G($x,$y)")
            assertEquals(SkColorGetB(expected), SkColorGetB(actualArgb), "B($x,$y)")
        }
    }

    @Test
    fun `BGR_888 format drops alpha and uses 24-bit pixels`() {
        val src = SkBitmap(2, 2)
        src.pixels[0] = 0x80FF0000.toInt()
        src.pixels[1] = 0xFF00FF00.toInt()
        src.pixels[2] = 0x4000FFFF.toInt()
        src.pixels[3] = 0x00112233
        val bytes = BmpEncoder.encode(src, BmpEncoder.Options(format = BmpEncoder.BmpFormat.kBGR_888))!!
        val bpp = readU16LE(bytes, 14 + 14)
        assertEquals(24, bpp)
        val decoded = decodeBmp(bytes)
        assertEquals(0xFF, SkColorGetR(decoded.getPixel(0, 0)))
        assertEquals(0xFF, SkColorGetG(decoded.getPixel(1, 0)))
        assertEquals(0x11, SkColorGetR(decoded.getPixel(1, 1)))
    }

    @Test
    fun `row padding aligns rows to a multiple of 4 bytes`() {
        val src = SkBitmap(3, 2)
        for (i in 0 until 6) src.pixels[i] = 0xFF808080.toInt()
        val bytes = BmpEncoder.encode(src, BmpEncoder.Options(format = BmpEncoder.BmpFormat.kBGR_888))!!
        val pixelDataSize = readU32LE(bytes, 14 + 20)
        assertEquals(12 * 2, pixelDataSize, "row size must be aligned to 4 bytes")
    }

    @Test
    fun `Encode degenerate bitmap returns null`() {
        val bytes = BmpEncoder.encode(SkBitmap(0, 0))
        assertNull(bytes)
    }

    @Test
    fun `Encode to OutputStream agrees with Encode to ByteArray`() {
        val src = makeGradient(4, 4)
        val viaData = BmpEncoder.encode(src)!!
        val baos = ByteArrayOutputStream()
        assertTrue(BmpEncoder.encode(baos, src))
        val viaStream = baos.toByteArray()
        assertEquals(viaData.toList(), viaStream.toList())
    }

    @Test
    fun `Encode preserves alpha in BGRA_8888 output`() {
        val src = SkBitmap(2, 1)
        src.pixels[0] = 0x40FF0000.toInt()
        src.pixels[1] = 0x80FFFFFF.toInt()
        val bytes = BmpEncoder.encode(src)!!
        val bpp = readU16LE(bytes, 14 + 14)
        assertEquals(32, bpp)
        val dataOffset = readU32LE(bytes, 10)
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

    private fun readU32LE(buf: ByteArray, off: Int): Int =
        (buf[off].toInt() and 0xFF) or
            ((buf[off + 1].toInt() and 0xFF) shl 8) or
            ((buf[off + 2].toInt() and 0xFF) shl 16) or
            ((buf[off + 3].toInt() and 0xFF) shl 24)

    @Test
    fun `RLE8 encode round-trips through decoder`() {
        val src = SkBitmap(4, 2)
        for (y in 0 until 2) for (x in 0 until 4) {
            src.pixels[y * 4 + x] = if (x < 2) 0xFFFF0000.toInt() else 0xFF00FF00.toInt()
        }
        val bytes = BmpEncoder.encode(src, BmpEncoder.Options(
            format = BmpEncoder.BmpFormat.kBGR_888,
            compression = BmpEncoder.Compression.RLE8,
        ))!!
        assertEquals(1, readU32LE(bytes, 14 + 16), "compression must be BI_RLE8")
        val decoded = decodeBmp(bytes)
        for (y in 0 until 2) for (x in 0 until 4) {
            assertEquals(SkColorGetR(src.getPixel(x, y)), SkColorGetR(decoded.getPixel(x, y)), "R($x,$y)")
            assertEquals(SkColorGetG(src.getPixel(x, y)), SkColorGetG(decoded.getPixel(x, y)), "G($x,$y)")
            assertEquals(SkColorGetB(src.getPixel(x, y)), SkColorGetB(decoded.getPixel(x, y)), "B($x,$y)")
        }
    }

    @Test
    fun `RLE4 encode round-trips through decoder`() {
        val src = SkBitmap(4, 1)
        for (x in 0 until 4) src.pixels[x] = 0xFF0000FF.toInt()
        val bytes = BmpEncoder.encode(src, BmpEncoder.Options(
            format = BmpEncoder.BmpFormat.kBGR_888,
            compression = BmpEncoder.Compression.RLE4,
        ))!!
        assertEquals(2, readU32LE(bytes, 14 + 16), "compression must be BI_RLE4")
        val decoded = decodeBmp(bytes)
        for (x in 0 until 4) {
            assertEquals(0xFF, SkColorGetB(decoded.getPixel(x, 0)), "B($x,0)")
        }
    }

    @Test
    fun `RLE encode rejects non-palette input`() {
        val src = SkBitmap(17, 17)
        for (i in 0 until 289) {
            src.pixels[i] = (0xFF shl 24) or i
        }
        val bytes = BmpEncoder.encode(src, BmpEncoder.Options(
            compression = BmpEncoder.Compression.RLE8,
        ))
        assertNull(bytes, "RLE8 with >256 palette colors should return null")
    }

    @Test
    fun `RLE8 encodes alternating pixels using absolute mode`() {
        val src = SkBitmap(4, 1)
        src.pixels[0] = 0xFFFF0000.toInt()
        src.pixels[1] = 0xFF00FF00.toInt()
        src.pixels[2] = 0xFF0000FF.toInt()
        src.pixels[3] = 0xFFFF0000.toInt()
        val bytes = BmpEncoder.encode(src, BmpEncoder.Options(
            compression = BmpEncoder.Compression.RLE8,
        ))!!
        assertEquals(1, readU32LE(bytes, 14 + 16), "compression must be BI_RLE8")
        val decoded = decodeBmp(bytes)
        for (x in 0 until 4) {
            assertEquals(SkColorGetR(src.getPixel(x, 0)), SkColorGetR(decoded.getPixel(x, 0)), "R($x,0)")
            assertEquals(SkColorGetG(src.getPixel(x, 0)), SkColorGetG(decoded.getPixel(x, 0)), "G($x,0)")
            assertEquals(SkColorGetB(src.getPixel(x, 0)), SkColorGetB(decoded.getPixel(x, 0)), "B($x,0)")
        }
    }

    @Test
    fun `RLE4 rejects more than 16 colors`() {
        val src = SkBitmap(5, 5)
        for (y in 0 until 5) for (x in 0 until 5) {
            val r = x * 50
            val g = y * 50
            src.pixels[y * 5 + x] = (0xFF shl 24) or (r shl 16) or (g shl 8)
        }
        val bytes = BmpEncoder.encode(src, BmpEncoder.Options(
            compression = BmpEncoder.Compression.RLE4,
        ))
        assertNull(bytes, "RLE4 with >16 unique colors should return null")
    }

    private fun readU16LE(buf: ByteArray, off: Int): Int =
        (buf[off].toInt() and 0xFF) or ((buf[off + 1].toInt() and 0xFF) shl 8)

    @Test
    fun `V5 encode with ICC profile round-trips through decoder`() {
        val src = SkBitmap(2, 2)
        src.pixels[0] = 0xFFFF0000.toInt()
        src.pixels[1] = 0xFF00FF00.toInt()
        src.pixels[2] = 0xFF0000FF.toInt()
        src.pixels[3] = 0xFFFFFFFF.toInt()
        val iccBytes = createValidIccProfile()
        val bytes = BmpEncoder.encode(src, BmpEncoder.Options(iccProfile = iccBytes))!!
        val dibSize = readU32LE(bytes, 14)
        assertEquals(124, dibSize, "V5 header must be 124 bytes")
        val profile = decodedCodec(bytes)?.getICCProfile()
        assertNotNull(profile, "encoded V5 BMP with ICC must expose profile on decode")
    }

    private fun createValidIccProfile(): ByteArray {
        return SkICC.WriteToICC(SkNamedTransferFn.kSRGB, SkNamedGamut.kSRGB)
    }

    private fun decodedCodec(bytes: ByteArray): Codec? {
        return Codec.MakeFromData(bytes)
    }
}
