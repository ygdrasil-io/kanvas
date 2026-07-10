package org.graphiks.kanvas.codec.png

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.graphiks.kanvas.codec.Codec
import org.graphiks.kanvas.color.ColorProfile
import org.graphiks.kanvas.color.cicp.CicpColorInfo
import org.graphiks.kanvas.color.cicp.toColorProfile
import org.graphiks.math.SkcmsMatrix3x3
import org.skia.foundation.SkAlphaType
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkColorType
import org.skia.foundation.SkICC
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPixmap
import org.skia.foundation.skcms.SkNamedGamut
import org.skia.foundation.skcms.SkNamedTransferFn
import org.skia.foundation.skcms.SkcmsICCProfile
import org.skia.foundation.skcms.skcmsParse
import org.skia.foundation.stream.SkWStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

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
    fun `HDR unsupported and non-serializable color profiles are refused without OutputStream output`() {
        val hdr = CicpColorInfo(primaries = 9, transfer = 16, matrix = 0, fullRange = true)
            .toColorProfile()
            .getOrThrow()
        val nonSerializableMatrix = SkcmsMatrix3x3.of(
            SkNamedGamut.kSRGB[0, 0] + 0.01f, SkNamedGamut.kSRGB[0, 1], SkNamedGamut.kSRGB[0, 2],
            SkNamedGamut.kSRGB[1, 0], SkNamedGamut.kSRGB[1, 1], SkNamedGamut.kSRGB[1, 2],
            SkNamedGamut.kSRGB[2, 0], SkNamedGamut.kSRGB[2, 1], SkNamedGamut.kSRGB[2, 2],
        )
        val refusedColorSpaces = listOf(
            SkColorSpace.makeProfileAware(SkcmsICCProfile.fromColorProfile(hdr)),
            SkColorSpace.makeProfileAware(
                SkcmsICCProfile.fromColorProfile(ColorProfile.unsupported("icc.profile.unsupported")),
            ),
            SkColorSpace.makeRGB(SkNamedTransferFn.kSRGB, nonSerializableMatrix)!!,
        )

        refusedColorSpaces.forEach { colorSpace ->
            val src = SkBitmap(1, 1, colorSpace).also { it.pixels[0] = 0xFF336699.toInt() }
            val output = ByteArrayOutputStream()

            assertNull(PngEncoder.encode(src))
            assertFalse(PngEncoder.encode(output, src))
            assertEquals(0, output.size())
        }
    }

    @Test
    fun `SkPixmap overload preserves serializable color space`() {
        val colorSpace = SkColorSpace.makeRGB(SkNamedTransferFn.kSRGB, SkNamedGamut.kDisplayP3)!!
        val info = SkImageInfo.Make(
            width = 1,
            height = 1,
            colorType = SkColorType.kRGBA_8888,
            alphaType = SkAlphaType.kUnpremul,
            colorSpace = colorSpace,
        )
        val pixels = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(0, 0xFF336699.toInt())
        val pixmap = SkPixmap(info, pixels, 4)

        val bytes = PngEncoder.encode(pixmap)!!

        assertTrue(findChunk(bytes, 0x69434350), "Display-P3 pixmap must write iCCP")
        assertFalse(findChunk(bytes, 0x73524742), "Display-P3 pixmap must not be retagged sRGB")
    }

    @Test
    fun `refusal propagates through SkWStream and SkPixmap overloads without output`() {
        val unsupported = SkColorSpace.makeProfileAware(
            SkcmsICCProfile.fromColorProfile(ColorProfile.unsupported("icc.profile.unsupported")),
        )
        val bitmap = SkBitmap(1, 1, unsupported).also { it.pixels[0] = 0xFF336699.toInt() }
        val info = SkImageInfo.Make(
            width = 1,
            height = 1,
            colorType = SkColorType.kRGBA_8888,
            alphaType = SkAlphaType.kUnpremul,
            colorSpace = unsupported,
        )
        val pixels = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(0, 0xFF336699.toInt())
        val pixmap = SkPixmap(info, pixels, 4)
        val bitmapStream = RecordingWStream()
        val pixmapStream = RecordingWStream()

        assertFalse(PngEncoder.encode(bitmapStream, bitmap))
        assertEquals(0L, bitmapStream.bytesWritten())
        assertNull(PngEncoder.encode(pixmap))
        assertFalse(PngEncoder.encode(pixmapStream, pixmap))
        assertEquals(0L, pixmapStream.bytesWritten())
    }

    @Test
    fun `tEXt comments use exact Latin-1 wire bytes`() {
        val src = SkBitmap(1, 1)
        val bytes = PngEncoder.encode(
            src,
            PngEncoder.Options(comments = listOf("Résumé", "café")),
        )!!

        assertEquals(
            "Résumé\u0000café".toByteArray(Charsets.ISO_8859_1).toList(),
            chunkData(bytes, 0x74455874).toList(),
        )
    }

    @Test
    fun `tEXt accepts a 79-byte Latin-1 keyword`() {
        val keyword = "R\u00e9sum\u00e9" + "x".repeat(73)
        val bytes = PngEncoder.encode(
            SkBitmap(1, 1),
            PngEncoder.Options(comments = listOf(keyword, "ok")),
        )!!

        assertEquals(79, chunkData(bytes, 0x74455874).indexOf(0))
    }

    @Test
    fun `tEXt rejects invalid keyword and non Latin-1 text without output`() {
        val src = SkBitmap(1, 1)
        val invalidComments = listOf(
            listOf("a".repeat(80), "text"),
            listOf(" leading", "text"),
            listOf("trailing ", "text"),
            listOf("two  spaces", "text"),
            listOf("bad\u0080keyword", "text"),
            listOf("keyword", "snowman \u2603"),
            listOf("keyword", "nul\u0000text"),
        )

        invalidComments.forEach { comments ->
            val output = ByteArrayOutputStream()
            assertFalse(PngEncoder.encode(output, src, PngEncoder.Options(comments = comments)), comments.toString())
            assertEquals(0, output.size(), comments.toString())
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

    private fun chunkData(png: ByteArray, type: Int): ByteArray {
        var pos = 8
        while (pos + 12 <= png.size) {
            val len = ((png[pos].toInt() and 0xFF) shl 24) or
                ((png[pos + 1].toInt() and 0xFF) shl 16) or
                ((png[pos + 2].toInt() and 0xFF) shl 8) or
                (png[pos + 3].toInt() and 0xFF)
            val chunkType = ((png[pos + 4].toInt() and 0xFF) shl 24) or
                ((png[pos + 5].toInt() and 0xFF) shl 16) or
                ((png[pos + 6].toInt() and 0xFF) shl 8) or
                (png[pos + 7].toInt() and 0xFF)
            if (chunkType == type) return png.copyOfRange(pos + 8, pos + 8 + len)
            pos += 12 + len
        }
        error("chunk not found: ${type.toString(16)}")
    }

    private class RecordingWStream : SkWStream() {
        private val output = ByteArrayOutputStream()

        override fun write(buffer: ByteArray, size: Int): Boolean {
            output.write(buffer, 0, size)
            return true
        }

        override fun bytesWritten(): Long = output.size().toLong()
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
