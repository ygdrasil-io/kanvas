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
import org.graphiks.math.color.ColorMatrix3x3F32
import org.graphiks.kanvas.image.AlphaType
import org.graphiks.kanvas.image.Bitmap
import org.graphiks.kanvas.color.ImageColorSpace
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.color.icc.IccProfileWriter
import org.graphiks.kanvas.image.ImageInfo
import org.graphiks.kanvas.image.Pixmap
import org.graphiks.kanvas.color.icc.IccProfile
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PngEncoderTest {

    @Test
    fun `encode round-trip through PNG decoder preserves RGB`() {
        val src = Bitmap(4, 4)
        for (y in 0 until 4) for (x in 0 until 4) {
            val r = (x * 85).coerceIn(0, 255)
            val g = (y * 85).coerceIn(0, 255)
            src[y * 4 + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or 0x7F
        }
        val bytes = PngEncoder.encode(src)!!
        val decoded = decodePng(bytes)
        assertEquals(4, decoded.width)
        assertEquals(4, decoded.height)
        for (y in 0 until 4) for (x in 0 until 4) {
            assertEquals(src.getArgb(x, y), decoded.getArgb(x, y), "($x,$y)")
        }
    }

    @Test
    fun `encode degenerate bitmap returns null`() {
        assertNull(PngEncoder.encode(Bitmap(0, 0)))
    }

    @Test
    fun `OutputStream overload matches direct encode`() {
        val src = Bitmap(4, 4)
        for (i in 0 until 16) src[i] = 0xFF808080.toInt()
        val viaData = PngEncoder.encode(src)!!
        val baos = ByteArrayOutputStream()
        assertTrue(PngEncoder.encode(baos, src))
        assertEquals(viaData.toList(), baos.toByteArray().toList())
    }

    @Test
    fun `zlib level 0 produces uncompressed but valid PNG`() {
        val src = Bitmap(2, 2)
        for (i in 0 until 4) src[i] = 0xFF0000FF.toInt()
        val bytes = PngEncoder.encode(src, PngEncoder.Options(zLibLevel = 0))!!
        val decoded = decodePng(bytes)
        assertEquals(2, decoded.width)
    }

    @Test
    fun `zlib level 9 produces compressed, valid PNG`() {
        val src = Bitmap(2, 2)
        for (i in 0 until 4) src[i] = 0xFF0000FF.toInt()
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
        val src = Bitmap(4, 4)
        for (i in 0 until 16) src[i] = 0xFF808080.toInt()
        val bytes = PngEncoder.encode(src, PngEncoder.Options(filterFlags = PngEncoder.FilterFlag.kNone.mask))!!
        assertTrue(bytes.size > 0)
        val decoded = decodePng(bytes)
        assertEquals(4, decoded.width)
    }

    @Test
    fun `encode with bitmap color space writes iCCP chunk and round-trips`() {
        val iccBytes = IccProfileWriter.writeMatrixTrc(requireNotNull(org.graphiks.kanvas.color.ColorProfiles.sRGB().transferFunction), requireNotNull(org.graphiks.kanvas.color.ColorProfiles.displayP3().toXyzD50))
        val colorSpace = ImageColorSpace.fromIccProfile(IccProfile.parse(iccBytes).getOrThrow())
        val src = Bitmap(ImageInfo.make(4, 4, ColorType.RGBA_8888, AlphaType.UNPREMUL, colorSpace))
        for (y in 0 until 4) for (x in 0 until 4) {
            src[y * 4 + x] = (0xFF shl 24) or ((x * 85) shl 16) or ((y * 85) shl 8)
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
        val src = Bitmap(8, 8)
        for (y in 0 until 8) for (x in 0 until 8) {
            src[y * 8 + x] = (0xFF shl 24) or ((x * 32) shl 16) or ((y * 32) shl 8)
        }
        val bytes = PngEncoder.encode(src, PngEncoder.Options(interlace = true))!!
        assertEquals(1, bytes[28].toInt() and 0xFF, "IHDR interlace byte must be 1")
        val decoded = decodePng(bytes)
        assertEquals(8, decoded.width)
        assertEquals(8, decoded.height)
        for (y in 0 until 8) for (x in 0 until 8) {
            assertEquals(src.getArgb(x, y), decoded.getArgb(x, y), "($x,$y)")
        }
    }

    @Test
    fun `sRGB bitmap writes sRGB chunk not iCCP`() {
        val src = Bitmap(4, 4)
        for (i in 0 until 16) src[i] = 0xFF808080.toInt()
        val bytes = PngEncoder.encode(src)!!
        val hasSrgb = findChunk(bytes, 0x73524742)
        assertTrue(hasSrgb, "sRGB bitmap must write sRGB chunk")
        val hasIccp = findChunk(bytes, 0x69434350)
        assertTrue(!hasIccp, "sRGB bitmap must not write iCCP chunk")
    }

    @Test
    fun `sRGB bitmap writes gAMA chunk`() {
        val src = Bitmap(4, 4)
        for (i in 0 until 16) src[i] = 0xFF808080.toInt()
        val bytes = PngEncoder.encode(src)!!
        assertTrue(findChunk(bytes, 0x67414D41), "sRGB bitmap must write gAMA chunk")
    }

    @Test
    fun `non-sRGB bitmap writes iCCP not sRGB`() {
        val iccBytes = IccProfileWriter.writeMatrixTrc(requireNotNull(org.graphiks.kanvas.color.ColorProfiles.sRGB().transferFunction), requireNotNull(org.graphiks.kanvas.color.ColorProfiles.displayP3().toXyzD50))
        val colorSpace = ImageColorSpace.fromIccProfile(IccProfile.parse(iccBytes).getOrThrow())
        val src = Bitmap(ImageInfo.make(4, 4, ColorType.RGBA_8888, AlphaType.UNPREMUL, colorSpace))
        for (i in 0 until 16) src[i] = 0xFF808080.toInt()
        val bytes = PngEncoder.encode(src)!!
        assertTrue(findChunk(bytes, 0x69434350), "non-sRGB must write iCCP")
        assertTrue(!findChunk(bytes, 0x73524742), "non-sRGB must not write sRGB")
    }

    @Test
    fun `D50 preserving gamut three through sixty four LSB from sRGB writes iCCP`() {
        val base = requireNotNull(org.graphiks.kanvas.color.ColorProfiles.sRGB().toXyzD50)
        listOf(3, 50, 64).forEach { deltaLsb ->
            val delta = deltaLsb / 65_536f
            val matrix = ColorMatrix3x3F32.of(
                base[0, 0] + delta, base[0, 1] - delta, base[0, 2],
                base[1, 0] + delta, base[1, 1] - delta, base[1, 2],
                base[2, 0] + delta, base[2, 1] - delta, base[2, 2],
            )
            val colorSpace = ImageColorSpace.fromMatrixTrc(requireNotNull(org.graphiks.kanvas.color.ColorProfiles.sRGB().transferFunction), matrix)!!
            val src = Bitmap(ImageInfo.make(4, 4, ColorType.RGBA_8888, AlphaType.UNPREMUL, colorSpace))
            for (i in 0 until 16) src[i] = 0xFF808080.toInt()

            assertFalse(colorSpace.isSrgb(), "delta=$deltaLsb LSB")
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
        val nonSerializableMatrix = ColorMatrix3x3F32.of(
            requireNotNull(org.graphiks.kanvas.color.ColorProfiles.sRGB().toXyzD50)[0, 0] + 0.01f, requireNotNull(org.graphiks.kanvas.color.ColorProfiles.sRGB().toXyzD50)[0, 1], requireNotNull(org.graphiks.kanvas.color.ColorProfiles.sRGB().toXyzD50)[0, 2],
            requireNotNull(org.graphiks.kanvas.color.ColorProfiles.sRGB().toXyzD50)[1, 0], requireNotNull(org.graphiks.kanvas.color.ColorProfiles.sRGB().toXyzD50)[1, 1], requireNotNull(org.graphiks.kanvas.color.ColorProfiles.sRGB().toXyzD50)[1, 2],
            requireNotNull(org.graphiks.kanvas.color.ColorProfiles.sRGB().toXyzD50)[2, 0], requireNotNull(org.graphiks.kanvas.color.ColorProfiles.sRGB().toXyzD50)[2, 1], requireNotNull(org.graphiks.kanvas.color.ColorProfiles.sRGB().toXyzD50)[2, 2],
        )
        val refusedColorSpaces = listOf(
            ImageColorSpace.fromColorProfile(hdr),
            ImageColorSpace.fromColorProfile(ColorProfile.unsupported("icc.profile.unsupported")),
            ImageColorSpace.fromMatrixTrc(requireNotNull(org.graphiks.kanvas.color.ColorProfiles.sRGB().transferFunction), nonSerializableMatrix)!!,
        )

        refusedColorSpaces.forEach { colorSpace ->
            val src = Bitmap(ImageInfo.make(1, 1, ColorType.RGBA_8888, AlphaType.UNPREMUL, colorSpace)).also { it[0] = 0xFF336699.toInt() }
            val output = ByteArrayOutputStream()

            assertNull(PngEncoder.encode(src))
            assertFalse(PngEncoder.encode(output, src))
            assertEquals(0, output.size())
        }
    }

    @Test
    fun `Pixmap OutputStream overload preserves serializable color space`() {
        val colorSpace = ImageColorSpace.fromMatrixTrc(requireNotNull(org.graphiks.kanvas.color.ColorProfiles.sRGB().transferFunction), requireNotNull(org.graphiks.kanvas.color.ColorProfiles.displayP3().toXyzD50))!!
        val info = ImageInfo.make(
            width = 1,
            height = 1,
            colorType = ColorType.RGBA_8888,
            alphaType = AlphaType.UNPREMUL,
            colorSpace = colorSpace,
        )
        val pixels = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(0, 0xFF336699.toInt())
        val pixmap = Pixmap(info, pixels, 4)

        val output = ByteArrayOutputStream()

        assertTrue(PngEncoder.encode(output, pixmap))
        val bytes = output.toByteArray()

        assertTrue(findChunk(bytes, 0x69434350), "Display-P3 pixmap must write iCCP")
        assertFalse(findChunk(bytes, 0x73524742), "Display-P3 pixmap must not be retagged sRGB")
    }

    @Test
    fun `F16 Bitmap and Pixmap overloads refuse without output`() {
        val info = ImageInfo.make(1, 1, ColorType.RGBA_F16_NORM, AlphaType.PREMUL, ImageColorSpace.sRGB())
        val bitmap = Bitmap(info).also { it.setPremulRgbaF16(0, 0, 0.25f, 0.125f, 0.375f, 0.5f) }
        val pixmap = Pixmap(info, ByteBuffer.allocate(info.minRowBytes()), info.minRowBytes())
        val bitmapOutput = ByteArrayOutputStream()
        val pixmapOutput = ByteArrayOutputStream()

        assertNull(PngEncoder.encode(bitmap))
        assertFalse(PngEncoder.encode(bitmapOutput, bitmap))
        assertEquals(0, bitmapOutput.size())
        assertNull(PngEncoder.encode(pixmap))
        assertFalse(PngEncoder.encode(pixmapOutput, pixmap))
        assertEquals(0, pixmapOutput.size())
    }

    @Test
    fun `premultiplied and unknown alpha Bitmap and Pixmap inputs refuse without output`() {
        listOf(AlphaType.PREMUL, AlphaType.UNKNOWN).forEach { alphaType ->
            val info = ImageInfo.make(1, 1, ColorType.RGBA_8888, alphaType, ImageColorSpace.sRGB())
            val bitmap = Bitmap(info)
            val pixmap = Pixmap(info, ByteBuffer.allocate(info.minRowBytes()), info.minRowBytes())
            val bitmapOutput = ByteArrayOutputStream()
            val pixmapOutput = ByteArrayOutputStream()

            assertNull(PngEncoder.encode(bitmap), alphaType.name)
            assertFalse(PngEncoder.encode(bitmapOutput, bitmap), alphaType.name)
            assertEquals(0, bitmapOutput.size(), alphaType.name)
            assertNull(PngEncoder.encode(pixmap), alphaType.name)
            assertFalse(PngEncoder.encode(pixmapOutput, pixmap), alphaType.name)
            assertEquals(0, pixmapOutput.size(), alphaType.name)
        }
    }

    @Test
    fun `refusal propagates through OutputStream and Pixmap overloads without output`() {
        val unsupported = ImageColorSpace.fromColorProfile(ColorProfile.unsupported("icc.profile.unsupported"))
        val bitmap = Bitmap(ImageInfo.make(1, 1, ColorType.RGBA_8888, AlphaType.UNPREMUL, unsupported)).also { it[0] = 0xFF336699.toInt() }
        val info = ImageInfo.make(
            width = 1,
            height = 1,
            colorType = ColorType.RGBA_8888,
            alphaType = AlphaType.UNPREMUL,
            colorSpace = unsupported,
        )
        val pixels = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(0, 0xFF336699.toInt())
        val pixmap = Pixmap(info, pixels, 4)
        val bitmapOutput = ByteArrayOutputStream()
        val pixmapOutput = ByteArrayOutputStream()

        assertFalse(PngEncoder.encode(bitmapOutput, bitmap))
        assertEquals(0, bitmapOutput.size())
        assertNull(PngEncoder.encode(pixmap))
        assertFalse(PngEncoder.encode(pixmapOutput, pixmap))
        assertEquals(0, pixmapOutput.size())
    }

    @Test
    fun `tEXt comments use exact Latin-1 wire bytes`() {
        val src = Bitmap(1, 1)
        val text = "line one\n\u00a1\u00ff"
        val bytes = PngEncoder.encode(
            src,
            PngEncoder.Options(comments = listOf("Résumé", text)),
        )!!

        assertEquals(
            "Résumé\u0000$text".toByteArray(Charsets.ISO_8859_1).toList(),
            chunkData(bytes, 0x74455874).toList(),
        )
        val document = (PngDocument.open(bytes) as PngDocumentOpenResult.Success).document
        val metadata = document.tEXt.single() as PngMetadataValue.Resolved
        assertEquals(text, metadata.value.text)
    }

    @Test
    fun `tEXt accepts a 79-byte Latin-1 keyword`() {
        val keyword = "R\u00e9sum\u00e9" + "x".repeat(73)
        val bytes = PngEncoder.encode(
            Bitmap(1, 1),
            PngEncoder.Options(comments = listOf(keyword, "ok")),
        )!!

        assertEquals(79, chunkData(bytes, 0x74455874).indexOf(0))
    }

    @Test
    fun `tEXt rejects invalid keyword and non Latin-1 text without output`() {
        val src = Bitmap(1, 1)
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
    fun `tEXt rejects TAB and C1 controls without output`() {
        val src = Bitmap(1, 1)

        listOf("tab\ttext", "C1\u0085text").forEach { text ->
            val output = ByteArrayOutputStream()
            assertFalse(
                PngEncoder.encode(output, src, PngEncoder.Options(comments = listOf("Comment", text))),
                text,
            )
            assertEquals(0, output.size(), text)
        }
    }

    @Test
    fun `adam7 interlace with filters round-trips correctly`() {
        val src = Bitmap(16, 16)
        for (y in 0 until 16) for (x in 0 until 16) {
            src[y * 16 + x] = (0xFF shl 24) or ((x * 16) shl 16) or ((y * 16) shl 8) or (x xor y)
        }
        val bytes = PngEncoder.encode(src, PngEncoder.Options(interlace = true))!!
        val decoded = decodePng(bytes)
        for (y in 0 until 16) for (x in 0 until 16) {
            assertEquals(src.getArgb(x, y), decoded.getArgb(x, y), "($x,$y)")
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

    private fun decodePng(bytes: ByteArray): Bitmap {
        val codec = Codec.MakeFromData(bytes)
        assertNotNull(codec, "PNG decoder must load encoded output")
        val (bitmap, result) = codec!!.getImage()
        assertEquals(Codec.Result.kSuccess, result)
        assertNotNull(bitmap)
        return bitmap!!
    }
}
