package org.graphiks.kanvas.codec.bmp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.graphiks.kanvas.codec.Codec
import org.graphiks.kanvas.image.Bitmap
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.color.icc.IccProfileWriter
import org.graphiks.math.color.ColorARGB
import java.io.ByteArrayOutputStream

class BmpEncoderTest {

    @Test
    fun `unsupported source color type is refused without writing to stream`() {
        val dst = ByteArrayOutputStream().also { it.write(0x2A) }

        assertFalse(BmpEncoder.encode(dst, Bitmap(1, 1, ColorType.RGB_565)))
        assertEquals(listOf(0x2A.toByte()), dst.toByteArray().toList())
    }

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
            val expected = src.getArgb(x, y)
            val actualArgb = decoded.getArgb(x, y)
            assertEquals(ColorARGB.fromPackedInt(expected).red, ColorARGB.fromPackedInt(actualArgb).red, "R($x,$y)")
            assertEquals(ColorARGB.fromPackedInt(expected).green, ColorARGB.fromPackedInt(actualArgb).green, "G($x,$y)")
            assertEquals(ColorARGB.fromPackedInt(expected).blue, ColorARGB.fromPackedInt(actualArgb).blue, "B($x,$y)")
        }
    }

    @Test
    fun `BGR_888 format drops alpha and uses 24-bit pixels`() {
        val src = Bitmap(2, 2)
        src.setArgb(0, 0, 0x80FF0000.toInt())
        src.setArgb(1, 0, 0xFF00FF00.toInt())
        src.setArgb(0, 1, 0x4000FFFF.toInt())
        src.setArgb(1, 1, 0x00112233)
        val bytes = BmpEncoder.encode(src, BmpEncoder.Options(format = BmpEncoder.BmpFormat.kBGR_888))!!
        val bpp = readU16LE(bytes, 14 + 14)
        assertEquals(24, bpp)
        val decoded = decodeBmp(bytes)
        assertEquals(0xFF, ColorARGB.fromPackedInt(decoded.getArgb(0, 0)).red)
        assertEquals(0xFF, ColorARGB.fromPackedInt(decoded.getArgb(1, 0)).green)
        assertEquals(0x11, ColorARGB.fromPackedInt(decoded.getArgb(1, 1)).red)
    }

    @Test
    fun `row padding aligns rows to a multiple of 4 bytes`() {
        val src = solidBitmap(3, 2, 0xFF808080.toInt())
        val bytes = BmpEncoder.encode(src, BmpEncoder.Options(format = BmpEncoder.BmpFormat.kBGR_888))!!
        val pixelDataSize = readU32LE(bytes, 14 + 20)
        assertEquals(12 * 2, pixelDataSize, "row size must be aligned to 4 bytes")
    }

    @Test
    fun `Encode degenerate bitmap returns null`() {
        val bytes = BmpEncoder.encode(Bitmap(0, 0))
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
        val src = Bitmap(2, 1)
        src.setArgb(0, 0, 0x40FF0000.toInt())
        src.setArgb(1, 0, 0x80FFFFFF.toInt())
        val bytes = BmpEncoder.encode(src)!!
        val bpp = readU16LE(bytes, 14 + 14)
        assertEquals(32, bpp)
        val dataOffset = readU32LE(bytes, 10)
        val p0a = bytes[dataOffset + 3].toInt() and 0xFF
        assertEquals(ColorARGB.fromPackedInt(src.getArgb(0, 0)).alpha, p0a)
        val p1a = bytes[dataOffset + 7].toInt() and 0xFF
        assertEquals(ColorARGB.fromPackedInt(src.getArgb(1, 0)).alpha, p1a)
    }

    private fun makeGradient(width: Int, height: Int): Bitmap {
        val b = Bitmap(width, height)
        for (y in 0 until height) for (x in 0 until width) {
            val r = (x * 255 / maxOf(1, width - 1)).coerceIn(0, 255)
            val g = (y * 255 / maxOf(1, height - 1)).coerceIn(0, 255)
            b.setArgb(x, y, (0xFF shl 24) or (r shl 16) or (g shl 8) or 0x40)
        }
        return b
    }

    private fun decodeBmp(bytes: ByteArray): Bitmap {
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
        val src = Bitmap(4, 2)
        for (y in 0 until 2) for (x in 0 until 4) {
            src.setArgb(x, y, if (x < 2) 0xFFFF0000.toInt() else 0xFF00FF00.toInt())
        }
        val bytes = BmpEncoder.encode(src, BmpEncoder.Options(
            format = BmpEncoder.BmpFormat.kBGR_888,
            compression = BmpEncoder.Compression.RLE8,
        ))!!
        assertEquals(1, readU32LE(bytes, 14 + 16), "compression must be BI_RLE8")
        val decoded = decodeBmp(bytes)
        for (y in 0 until 2) for (x in 0 until 4) {
            assertEquals(ColorARGB.fromPackedInt(src.getArgb(x, y)).red, ColorARGB.fromPackedInt(decoded.getArgb(x, y)).red, "R($x,$y)")
            assertEquals(ColorARGB.fromPackedInt(src.getArgb(x, y)).green, ColorARGB.fromPackedInt(decoded.getArgb(x, y)).green, "G($x,$y)")
            assertEquals(ColorARGB.fromPackedInt(src.getArgb(x, y)).blue, ColorARGB.fromPackedInt(decoded.getArgb(x, y)).blue, "B($x,$y)")
        }
    }

    @Test
    fun `RLE4 encode round-trips through decoder`() {
        val src = solidBitmap(4, 1, 0xFF0000FF.toInt())
        val bytes = BmpEncoder.encode(src, BmpEncoder.Options(
            format = BmpEncoder.BmpFormat.kBGR_888,
            compression = BmpEncoder.Compression.RLE4,
        ))!!
        assertEquals(2, readU32LE(bytes, 14 + 16), "compression must be BI_RLE4")
        val decoded = decodeBmp(bytes)
        for (x in 0 until 4) {
            assertEquals(0xFF, ColorARGB.fromPackedInt(decoded.getArgb(x, 0)).blue, "B($x,0)")
        }
    }

    @Test
    fun `RLE encode rejects non-palette input`() {
        val src = Bitmap(17, 17)
        for (i in 0 until 289) {
            src.setArgb(i % 17, i / 17, (0xFF shl 24) or i)
        }
        val bytes = BmpEncoder.encode(src, BmpEncoder.Options(
            compression = BmpEncoder.Compression.RLE8,
        ))
        assertNull(bytes, "RLE8 with >256 palette colors should return null")
    }

    @Test
    fun `RLE8 encodes alternating pixels using absolute mode`() {
        val src = Bitmap(4, 1)
        src.setArgb(0, 0, 0xFFFF0000.toInt())
        src.setArgb(1, 0, 0xFF00FF00.toInt())
        src.setArgb(2, 0, 0xFF0000FF.toInt())
        src.setArgb(3, 0, 0xFFFF0000.toInt())
        val bytes = BmpEncoder.encode(src, BmpEncoder.Options(
            compression = BmpEncoder.Compression.RLE8,
        ))!!
        assertEquals(1, readU32LE(bytes, 14 + 16), "compression must be BI_RLE8")
        val decoded = decodeBmp(bytes)
        for (x in 0 until 4) {
            assertEquals(ColorARGB.fromPackedInt(src.getArgb(x, 0)).red, ColorARGB.fromPackedInt(decoded.getArgb(x, 0)).red, "R($x,0)")
            assertEquals(ColorARGB.fromPackedInt(src.getArgb(x, 0)).green, ColorARGB.fromPackedInt(decoded.getArgb(x, 0)).green, "G($x,0)")
            assertEquals(ColorARGB.fromPackedInt(src.getArgb(x, 0)).blue, ColorARGB.fromPackedInt(decoded.getArgb(x, 0)).blue, "B($x,0)")
        }
    }

    @Test
    fun `RLE4 rejects more than 16 colors`() {
        val src = Bitmap(5, 5)
        for (y in 0 until 5) for (x in 0 until 5) {
            val r = x * 50
            val g = y * 50
            src.setArgb(x, y, (0xFF shl 24) or (r shl 16) or (g shl 8))
        }
        val bytes = BmpEncoder.encode(src, BmpEncoder.Options(
            compression = BmpEncoder.Compression.RLE4,
        ))
        assertNull(bytes, "RLE4 with >16 unique colors should return null")
    }

    @Test
    fun `RLE4 handles isolated single pixels before a run using encoded runs`() {
        val src = Bitmap(4, 1)
        src.setArgb(0, 0, 0xFFFF0000.toInt())
        src.setArgb(1, 0, 0xFF00FF00.toInt())
        src.setArgb(2, 0, 0xFF00FF00.toInt())
        src.setArgb(3, 0, 0xFF00FF00.toInt())
        val bytes = BmpEncoder.encode(src, BmpEncoder.Options(
            compression = BmpEncoder.Compression.RLE4,
        ))!!
        assertEquals(2, readU32LE(bytes, 14 + 16))
        val decoded = decodeBmp(bytes)
        for (x in 0 until 4) {
            assertEquals(ColorARGB.fromPackedInt(src.getArgb(x, 0)).red, ColorARGB.fromPackedInt(decoded.getArgb(x, 0)).red, "R($x,0)")
            assertEquals(ColorARGB.fromPackedInt(src.getArgb(x, 0)).green, ColorARGB.fromPackedInt(decoded.getArgb(x, 0)).green, "G($x,0)")
            assertEquals(ColorARGB.fromPackedInt(src.getArgb(x, 0)).blue, ColorARGB.fromPackedInt(decoded.getArgb(x, 0)).blue, "B($x,0)")
        }
    }

    @Test
    fun `RLE4 handles two isolated pixels before a run using encoded runs`() {
        val src = Bitmap(4, 1)
        src.setArgb(0, 0, 0xFFFF0000.toInt())
        src.setArgb(1, 0, 0xFF0000FF.toInt())
        src.setArgb(2, 0, 0xFF00FF00.toInt())
        src.setArgb(3, 0, 0xFF00FF00.toInt())
        val bytes = BmpEncoder.encode(src, BmpEncoder.Options(
            compression = BmpEncoder.Compression.RLE4,
        ))!!
        assertEquals(2, readU32LE(bytes, 14 + 16))
        val decoded = decodeBmp(bytes)
        for (x in 0 until 4) {
            assertEquals(ColorARGB.fromPackedInt(src.getArgb(x, 0)).red, ColorARGB.fromPackedInt(decoded.getArgb(x, 0)).red, "R($x,0)")
            assertEquals(ColorARGB.fromPackedInt(src.getArgb(x, 0)).green, ColorARGB.fromPackedInt(decoded.getArgb(x, 0)).green, "G($x,0)")
            assertEquals(ColorARGB.fromPackedInt(src.getArgb(x, 0)).blue, ColorARGB.fromPackedInt(decoded.getArgb(x, 0)).blue, "B($x,0)")
        }
    }

    private fun readU16LE(buf: ByteArray, off: Int): Int =
        (buf[off].toInt() and 0xFF) or ((buf[off + 1].toInt() and 0xFF) shl 8)

    @Test
    fun `V5 encode with ICC profile round-trips through decoder`() {
        val src = Bitmap(2, 2)
        src.setArgb(0, 0, 0xFFFF0000.toInt())
        src.setArgb(1, 0, 0xFF00FF00.toInt())
        src.setArgb(0, 1, 0xFF0000FF.toInt())
        src.setArgb(1, 1, 0xFFFFFFFF.toInt())
        val iccBytes = createValidIccProfile()
        val bytes = BmpEncoder.encode(src, BmpEncoder.Options(iccProfile = iccBytes))!!
        val dibSize = readU32LE(bytes, 14)
        assertEquals(124, dibSize, "V5 header must be 124 bytes")
        val profile = decodedCodec(bytes)?.getICCProfile()
        assertNotNull(profile, "encoded V5 BMP with ICC must expose profile on decode")
    }

    private fun createValidIccProfile(): ByteArray {
        return IccProfileWriter.writeMatrixTrc(requireNotNull(org.graphiks.kanvas.color.ColorProfiles.sRGB().transferFunction), requireNotNull(org.graphiks.kanvas.color.ColorProfiles.sRGB().toXyzD50))
    }

    private fun decodedCodec(bytes: ByteArray): Codec? {
        return Codec.MakeFromData(bytes)
    }

    private fun solidBitmap(width: Int, height: Int, argb: Int): Bitmap = Bitmap(width, height).also { bitmap ->
        for (y in 0 until height) for (x in 0 until width) bitmap.setArgb(x, y, argb)
    }
}
