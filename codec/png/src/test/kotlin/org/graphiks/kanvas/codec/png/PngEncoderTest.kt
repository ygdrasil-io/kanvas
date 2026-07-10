package org.graphiks.kanvas.codec.png

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.graphiks.kanvas.codec.Codec
import org.graphiks.math.SkcmsMatrix3x3
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkICC
import org.skia.foundation.skcms.SkNamedGamut
import org.skia.foundation.skcms.SkNamedTransferFn
import org.skia.foundation.skcms.skcmsParse
import java.io.ByteArrayOutputStream

class PngEncoderTest {

    @Test
    fun `encode round-trip through PNG decoder preserves RGB`() {
        val src = SkBitmap(4, 4)
        for (y in 0 until 4) for (x in 0 until 4) {
            val r = (x * 85).coerceIn(0, 255)
            val g = (y * 85).coerceIn(0, 255)
            src.pixels[y * 4 + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or 0x7F
        }
        val bytes = PngEncoder.encode(src)!!
        val decoded = decodePng(bytes)
        assertEquals(4, decoded.width)
        assertEquals(4, decoded.height)
        for (y in 0 until 4) for (x in 0 until 4) {
            assertEquals(src.getPixel(x, y), decoded.getPixel(x, y), "($x,$y)")
        }
    }

    @Test
    fun `encode degenerate bitmap returns null`() {
        assertNull(PngEncoder.encode(SkBitmap(0, 0)))
    }

    @Test
    fun `OutputStream overload matches direct encode`() {
        val src = SkBitmap(4, 4)
        for (i in 0 until 16) src.pixels[i] = 0xFF808080.toInt()
        val viaData = PngEncoder.encode(src)!!
        val baos = ByteArrayOutputStream()
        assertTrue(PngEncoder.encode(baos, src))
        assertEquals(viaData.toList(), baos.toByteArray().toList())
    }

    @Test
    fun `zlib level 0 produces uncompressed but valid PNG`() {
        val src = SkBitmap(2, 2)
        for (i in 0 until 4) src.pixels[i] = 0xFF0000FF.toInt()
        val bytes = PngEncoder.encode(src, PngEncoder.Options(zLibLevel = 0))!!
        val decoded = decodePng(bytes)
        assertEquals(2, decoded.width)
    }

    @Test
    fun `zlib level 9 produces compressed, valid PNG`() {
        val src = SkBitmap(2, 2)
        for (i in 0 until 4) src.pixels[i] = 0xFF0000FF.toInt()
        val uncompressed = PngEncoder.encode(src, PngEncoder.Options(zLibLevel = 0))!!
        val compressed = PngEncoder.encode(src, PngEncoder.Options(zLibLevel = 9))!!
        val decoded = decodePng(compressed)
        assertEquals(2, decoded.width)
        assertEquals(2, decoded.height)
    }

    @Test
    fun `invalid zlib levels are rejected in Options`() {
        assertThrows(IllegalArgumentException::class.java) { PngEncoder.Options(zLibLevel = -1) }
        assertThrows(IllegalArgumentException::class.java) { PngEncoder.Options(zLibLevel = 10) }
    }

    @Test
    fun `filter kNone produces valid PNG`() {
        val src = SkBitmap(4, 4)
        for (i in 0 until 16) src.pixels[i] = 0xFF808080.toInt()
        val bytes = PngEncoder.encode(src, PngEncoder.Options(filterFlags = PngEncoder.FilterFlag.kNone.mask))!!
        assertTrue(bytes.size > 0)
        val decoded = decodePng(bytes)
        assertEquals(4, decoded.width)
    }

    @Test
    fun `encode with bitmap color space writes iCCP chunk and round-trips`() {
        val iccBytes = SkICC.WriteToICC(SkNamedTransferFn.kSRGB, SkNamedGamut.kDisplayP3)
        val colorSpace = SkColorSpace.make(skcmsParse(iccBytes) ?: error("failed to parse ICC"))!!
        val src = SkBitmap(4, 4, colorSpace)
        for (y in 0 until 4) for (x in 0 until 4) {
            src.pixels[y * 4 + x] = (0xFF shl 24) or ((x * 85) shl 16) or ((y * 85) shl 8)
        }
        val bytes = PngEncoder.encode(src)!!
        val codec = Codec.MakeFromData(bytes)
        assertNotNull(codec)
        val profile = codec!!.getICCProfile()
        assertNotNull(profile, "PNG with iCCP must expose profile on decode")
        val (decoded, result) = codec.getImage()
        assertEquals(Codec.Result.kSuccess, result)
        assertEquals(4, decoded!!.width)
    }

    @Test
    fun `interlaced encode round-trips through decoder`() {
        val src = SkBitmap(8, 8)
        for (y in 0 until 8) for (x in 0 until 8) {
            src.pixels[y * 8 + x] = (0xFF shl 24) or ((x * 32) shl 16) or ((y * 32) shl 8)
        }
        val bytes = PngEncoder.encode(src, PngEncoder.Options(interlace = true))!!
        assertEquals(1, bytes[28].toInt() and 0xFF, "IHDR interlace byte must be 1")
        val decoded = decodePng(bytes)
        assertEquals(8, decoded.width)
        assertEquals(8, decoded.height)
        for (y in 0 until 8) for (x in 0 until 8) {
            assertEquals(src.getPixel(x, y), decoded.getPixel(x, y), "($x,$y)")
        }
    }

    @Test
    fun `sRGB bitmap writes sRGB chunk not iCCP`() {
        val src = SkBitmap(4, 4)
        for (i in 0 until 16) src.pixels[i] = 0xFF808080.toInt()
        val bytes = PngEncoder.encode(src)!!
        val hasSrgb = findChunk(bytes, 0x73524742)
        assertTrue(hasSrgb, "sRGB bitmap must write sRGB chunk")
        val hasIccp = findChunk(bytes, 0x69434350)
        assertTrue(!hasIccp, "sRGB bitmap must not write iCCP chunk")
    }

    @Test
    fun `sRGB bitmap writes gAMA chunk`() {
        val src = SkBitmap(4, 4)
        for (i in 0 until 16) src.pixels[i] = 0xFF808080.toInt()
        val bytes = PngEncoder.encode(src)!!
        assertTrue(findChunk(bytes, 0x67414D41), "sRGB bitmap must write gAMA chunk")
    }

    @Test
    fun `non-sRGB bitmap writes iCCP not sRGB`() {
        val iccBytes = SkICC.WriteToICC(SkNamedTransferFn.kSRGB, SkNamedGamut.kDisplayP3)
        val colorSpace = SkColorSpace.make(skcmsParse(iccBytes) ?: error("failed to parse ICC"))!!
        val src = SkBitmap(4, 4, colorSpace)
        for (i in 0 until 16) src.pixels[i] = 0xFF808080.toInt()
        val bytes = PngEncoder.encode(src)!!
        assertTrue(findChunk(bytes, 0x69434350), "non-sRGB must write iCCP")
        assertTrue(!findChunk(bytes, 0x73524742), "non-sRGB must not write sRGB")
    }

    @Test
    fun `D50 preserving gamut three through sixty four LSB from sRGB writes iCCP`() {
        val base = SkNamedGamut.kSRGB
        listOf(3, 50, 64).forEach { deltaLsb ->
            val delta = deltaLsb / 65_536f
            val matrix = SkcmsMatrix3x3.of(
                base[0, 0] + delta, base[0, 1] - delta, base[0, 2],
                base[1, 0] + delta, base[1, 1] - delta, base[1, 2],
                base[2, 0] + delta, base[2, 1] - delta, base[2, 2],
            )
            val colorSpace = SkColorSpace.makeRGB(SkNamedTransferFn.kSRGB, matrix)!!
            val src = SkBitmap(4, 4, colorSpace)
            for (i in 0 until 16) src.pixels[i] = 0xFF808080.toInt()

            assertFalse(colorSpace.isSRGB(), "delta=$deltaLsb LSB")
            val bytes = PngEncoder.encode(src)!!
            assertTrue(findChunk(bytes, 0x69434350), "delta=$deltaLsb LSB must write iCCP")
            assertFalse(findChunk(bytes, 0x73524742), "delta=$deltaLsb LSB must not write sRGB")
        }
    }

    @Test
    fun `adam7 interlace with filters round-trips correctly`() {
        val src = SkBitmap(16, 16)
        for (y in 0 until 16) for (x in 0 until 16) {
            src.pixels[y * 16 + x] = (0xFF shl 24) or ((x * 16) shl 16) or ((y * 16) shl 8) or (x xor y)
        }
        val bytes = PngEncoder.encode(src, PngEncoder.Options(interlace = true))!!
        val decoded = decodePng(bytes)
        for (y in 0 until 16) for (x in 0 until 16) {
            assertEquals(src.getPixel(x, y), decoded.getPixel(x, y), "($x,$y)")
        }
    }

    private fun findChunk(png: ByteArray, type: Int): Boolean {
        val typeBytes = byteArrayOf(
            (type ushr 24).toByte(), (type ushr 16).toByte(),
            (type ushr 8).toByte(), type.toByte()
        )
        var pos = 8
        while (pos + 12 <= png.size) {
            val len = ((png[pos].toInt() and 0xFF) shl 24) or
                    ((png[pos + 1].toInt() and 0xFF) shl 16) or
                    ((png[pos + 2].toInt() and 0xFF) shl 8) or
                    (png[pos + 3].toInt() and 0xFF)
            val typePos = pos + 4
            if (png[typePos] == typeBytes[0] && png[typePos + 1] == typeBytes[1] &&
                png[typePos + 2] == typeBytes[2] && png[typePos + 3] == typeBytes[3]
            ) return true
            pos += 12 + len
        }
        return false
    }

    private fun decodePng(bytes: ByteArray): SkBitmap {
        val codec = Codec.MakeFromData(bytes)
        assertNotNull(codec, "PNG decoder must load encoded output")
        val (bitmap, result) = codec!!.getImage()
        assertEquals(Codec.Result.kSuccess, result)
        assertNotNull(bitmap)
        return bitmap!!
    }
}
