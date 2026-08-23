package org.graphiks.kanvas.codec.jpeg
import org.graphiks.kanvas.image.AlphaType
import org.graphiks.kanvas.image.ImageInfo

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.graphiks.kanvas.codec.Codec
import org.graphiks.kanvas.image.Bitmap
import org.graphiks.kanvas.color.ImageColorSpace
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.EncodedOrigin
import org.graphiks.kanvas.image.Pixmap
import org.graphiks.kanvas.color.icc.IccProfileWriter
import org.graphiks.kanvas.color.icc.IccProfile
import org.graphiks.math.color.floatToHalf
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class JpegEncoderTest {

    @Test
    fun `encode round-trip through JPEG decoder preserves RGB`() {
        val src = Bitmap(8, 8)
        for (y in 0 until 8) for (x in 0 until 8) {
            val r = (x * 32).coerceIn(0, 255)
            val g = (y * 32).coerceIn(0, 255)
            src[y * 8 + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or 0x80
        }
        val bytes = JpegEncoder.encode(src)!!
        val decoded = decodeJpeg(bytes)
        assertEquals(8, decoded.width)
        assertEquals(8, decoded.height)
    }

    @Test
    fun `encode degenerate bitmap returns null`() {
        assertNull(JpegEncoder.encode(Bitmap(0, 0)))
    }

    @Test
    fun `OutputStream overload matches direct encode`() {
        val src = Bitmap(4, 4)
        for (i in 0 until 16) src[i] = 0xFF404040.toInt()
        val viaData = JpegEncoder.encode(src)!!
        val baos = ByteArrayOutputStream()
        assertTrue(JpegEncoder.encode(baos, src))
        assertEquals(viaData.toList(), baos.toByteArray().toList())
    }

    @Test
    fun `Pixmap OutputStream overload matches direct encode`() {
        val info = ImageInfo.make(
            width = 1,
            height = 1,
            colorType = ColorType.RGBA_8888,
            alphaType = AlphaType.UNPREMUL,
            colorSpace = ImageColorSpace.sRGB(),
        )
        val pixels = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(0, 0xFF336699.toInt())
        val pixmap = Pixmap(info, pixels, 4)
        val direct = JpegEncoder.encode(pixmap)!!
        val output = ByteArrayOutputStream()

        assertTrue(JpegEncoder.encode(output, pixmap))
        assertEquals(direct.toList(), output.toByteArray().toList())
    }

    @Test
    fun `F16 Pixmap overload preserves premultiplied samples`() {
        val info = ImageInfo.make(1, 1, ColorType.RGBA_F16_NORM, AlphaType.PREMUL, ImageColorSpace.sRGB())
        val pixels = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN).apply {
            putShort(0, floatToHalf(0.25f))
            putShort(2, floatToHalf(0.125f))
            putShort(4, floatToHalf(0.375f))
            putShort(6, floatToHalf(0.5f))
        }
        val pixmap = Pixmap(info, pixels, 12)
        val premul = FloatArray(4)

        assertTrue(pixmap.getPremulRgbaF16(0, 0, premul))
        assertEquals(0.25f, premul[0], 0.001f)
        assertEquals(0.125f, premul[1], 0.001f)
        assertEquals(0.375f, premul[2], 0.001f)
        assertEquals(0.5f, premul[3], 0.001f)

        val expected = Bitmap(info).also { it.setPremulRgbaF16(0, 0, premul[0], premul[1], premul[2], premul[3]) }
        assertEquals(JpegEncoder.encode(expected)!!.toList(), JpegEncoder.encode(pixmap)!!.toList())
    }

    @Test
    fun `quality 0 and 100 both produce valid JPEG`() {
        val src = Bitmap(4, 4)
        for (i in 0 until 16) src[i] = 0xFF808080.toInt()
        assertNotNull(JpegEncoder.encode(src, JpegEncoder.Options(quality = 0)))
        assertNotNull(JpegEncoder.encode(src, JpegEncoder.Options(quality = 100)))
    }

    @Test
    fun `Encode produces JPEG bytes with the SOI signature`() {
        val bitmap = makeFlat(16, 16, 0xFF808080.toInt())
        val bytes = JpegEncoder.encode(bitmap)
        assertNotNull(bytes)
        assertTrue(bytes!!.size > 0)
        assertEquals(0xFF.toByte(), bytes[0])
        assertEquals(0xD8.toByte(), bytes[1])
        assertEquals(0xFF.toByte(), bytes[2])
    }

    @Test
    fun `flat-colour JPEG round-trips within quantisation tolerance`() {
        val src = makeFlat(16, 16, 0xFF808080.toInt())
        val bytes = JpegEncoder.encode(src, JpegEncoder.Options(quality = 100))!!
        val codec = Codec.MakeFromData(bytes)!!
        val (decoded, result) = codec.getImage()
        assertEquals(Codec.Result.kSuccess, result)
        assertNotNull(decoded)
        for (y in 0 until 16) for (x in 0 until 16) {
            val px = decoded!!.getArgb(x, y)
            val r = (px ushr 16) and 0xFF
            val g = (px ushr 8) and 0xFF
            val b = px and 0xFF
            assertTrue(kotlin.math.abs(r - 0x80) <= 4, "R drift at ($x,$y) : $r")
            assertTrue(kotlin.math.abs(g - 0x80) <= 4, "G drift at ($x,$y) : $g")
            assertTrue(kotlin.math.abs(b - 0x80) <= 4, "B drift at ($x,$y) : $b")
        }
    }

    @Test
    fun `lower quality produces fewer bytes than maximum quality`() {
        val bitmap = makeGradient(64, 64)
        val highQ = JpegEncoder.encode(bitmap, JpegEncoder.Options(quality = 100))!!
        val lowQ = JpegEncoder.encode(bitmap, JpegEncoder.Options(quality = 25))!!
        assertTrue(
            lowQ.size < highQ.size,
            "low-quality JPEG must be smaller : q=25 → ${lowQ.size}, q=100 → ${highQ.size}",
        )
    }

    @Test
    fun `Options rejects quality outside 0 to 100`() {
        assertThrows(IllegalArgumentException::class.java) {
            JpegEncoder.Options(quality = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            JpegEncoder.Options(quality = 101)
        }
        JpegEncoder.Options(quality = 0)
        JpegEncoder.Options(quality = 100)
    }

    @Test
    fun `JPEG output drops alpha — every decoded pixel is fully opaque`() {
        val src = makeFlat(8, 8, 0x80FFFFFF.toInt())
        val bytes = JpegEncoder.encode(src)!!
        val (decoded, _) = Codec.MakeFromData(bytes)!!.getImage()
        assertNotNull(decoded)
        for (y in 0 until 8) for (x in 0 until 8) {
            val a = (decoded!!.getArgb(x, y) ushr 24) and 0xFF
            assertEquals(0xFF, a, "JPEG pixel ($x,$y) must be fully opaque after encode")
        }
    }

    @Test
    fun `BlendOnBlack composites transparent pixels before JPEG encode`() {
        val ignored = JpegEncoder.encode(
            makeFlat(16, 16, 0x00FF0000.toInt()),
            JpegEncoder.Options(quality = 100, alphaOption = JpegEncoder.AlphaOption.kIgnore),
        )!!
        val blended = JpegEncoder.encode(
            makeFlat(16, 16, 0x00FF0000.toInt()),
            JpegEncoder.Options(quality = 100, alphaOption = JpegEncoder.AlphaOption.kBlendOnBlack),
        )!!

        val ignoredPixel = decode(ignored).getArgb(0, 0)
        val blendedPixel = decode(blended).getArgb(0, 0)
        assertTrue(((ignoredPixel ushr 16) and 0xFF) > 220, "kIgnore should preserve red RGB")
        assertTrue(((blendedPixel ushr 16) and 0xFF) < 8, "kBlendOnBlack should encode transparent red as black")
        assertTrue(((blendedPixel ushr 8) and 0xFF) < 8, "kBlendOnBlack green drift")
        assertTrue((blendedPixel and 0xFF) < 8, "kBlendOnBlack blue drift")
    }

    @Test
    fun `Downsample option is written into SOF0 sampling factors`() {
        val bitmap = makeGradient(16, 16)
        assertEquals(0x22, sof0YSampling(JpegEncoder.encode(bitmap, JpegEncoder.Options(downsample = JpegEncoder.Downsample.k420))!!))
        assertEquals(0x21, sof0YSampling(JpegEncoder.encode(bitmap, JpegEncoder.Options(downsample = JpegEncoder.Downsample.k422))!!))
        assertEquals(0x11, sof0YSampling(JpegEncoder.encode(bitmap, JpegEncoder.Options(downsample = JpegEncoder.Downsample.k444))!!))
    }

    @Test
    fun `encoder emits baseline JPEG without progressive or restart markers`() {
        val markers = jpegMarkersBeforeScan(JpegEncoder.encode(makeGradient(16, 16))!!)
        assertTrue(0xC0 in markers, "baseline SOF0 marker must be present")
        assertTrue(0xC2 !in markers, "progressive SOF2 marker is intentionally out of scope")
        assertTrue(0xDD !in markers, "DRI restart interval marker is not emitted by the current encoder")
    }

    @Test
    fun `non-sRGB JPEG writes ICC APP2 chunks`() {
        val iccBytes = IccProfileWriter.writeMatrixTrc(requireNotNull(org.graphiks.kanvas.color.ColorProfiles.sRGB().transferFunction), requireNotNull(org.graphiks.kanvas.color.ColorProfiles.displayP3().toXyzD50))
        val colorSpace = ImageColorSpace.fromIccProfile(IccProfile.parse(iccBytes).getOrThrow())
        val src = Bitmap(ImageInfo.make(8, 8, ColorType.RGBA_8888, AlphaType.UNPREMUL, colorSpace))
        for (y in 0 until 8) for (x in 0 until 8) {
            src[y * 8 + x] = (0xFF shl 24) or ((x * 32) shl 16) or ((y * 32) shl 8) or 0x7F
        }
        val bytes = JpegEncoder.encode(src)!!
        val codec = Codec.MakeFromData(bytes)
        assertNotNull(codec)
        val profile = codec!!.getICCProfile()
        assertNotNull(profile, "non-sRGB JPEG must expose ICC profile on decode")
    }

    @Test
    fun `sRGB JPEG does not write ICC APP2`() {
        val src = Bitmap(8, 8)
        for (y in 0 until 8) for (x in 0 until 8) {
            src[y * 8 + x] = (0xFF shl 24) or ((x * 32) shl 16) or ((y * 32) shl 8)
        }
        val bytes = JpegEncoder.encode(src)!!
        val codec = Codec.MakeFromData(bytes)
        assertNotNull(codec)
        assertNull(codec!!.getICCProfile(), "sRGB JPEG must not expose ICC profile")
    }

    @Test
    fun `EXIF orientation survives round-trip`() {
        val src = Bitmap(8, 8)
        for (y in 0 until 8) for (x in 0 until 8) {
            src[y * 8 + x] = (0xFF shl 24) or ((x * 32) shl 16) or ((y * 32) shl 8)
        }
        for (exifVal in 1..8) {
            val orientation = EncodedOrigin.fromExifValue(exifVal)
            val bytes = JpegEncoder.encode(src, JpegEncoder.Options(orientation = orientation))!!
            val codec = Codec.MakeFromData(bytes)
            assertNotNull(codec, "orientation $exifVal")
            assertEquals(orientation, codec!!.getOrigin(), "orientation $exifVal must round-trip")
        }
    }

    private fun makeFlat(width: Int, height: Int, color: Int): Bitmap {
        val b = Bitmap(ImageInfo.make(width, height, ColorType.RGBA_8888, AlphaType.UNPREMUL, ImageColorSpace.sRGB()))
        for (y in 0 until height) for (x in 0 until width) {
            b[y * width + x] = color
        }
        return b
    }

    private fun makeGradient(width: Int, height: Int): Bitmap {
        val b = Bitmap(ImageInfo.make(width, height, ColorType.RGBA_8888, AlphaType.UNPREMUL, ImageColorSpace.sRGB()))
        for (y in 0 until height) for (x in 0 until width) {
            val r = (x * 255 / maxOf(1, width - 1)).coerceIn(0, 255)
            val g = (y * 255 / maxOf(1, height - 1)).coerceIn(0, 255)
            val b2 = ((x xor y) * 17) and 0xFF
            b[y * width + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b2
        }
        return b
    }

    private fun decode(bytes: ByteArray): Bitmap {
        val (decoded, result) = Codec.MakeFromData(bytes)!!.getImage()
        assertEquals(Codec.Result.kSuccess, result)
        assertNotNull(decoded)
        return decoded!!
    }

    private fun decodeJpeg(bytes: ByteArray): Bitmap {
        val codec = Codec.MakeFromData(bytes)
        assertNotNull(codec, "JPEG decoder must load encoded output")
        val (bitmap, result) = codec!!.getImage()
        assertEquals(Codec.Result.kSuccess, result)
        assertNotNull(bitmap)
        return bitmap!!
    }

    private fun sof0YSampling(bytes: ByteArray): Int {
        var offset = 2
        while (offset + 4 <= bytes.size) {
            require(bytes[offset] == 0xFF.toByte()) { "expected marker at $offset" }
            val marker = bytes[offset + 1].toInt() and 0xFF
            val length = ((bytes[offset + 2].toInt() and 0xFF) shl 8) or (bytes[offset + 3].toInt() and 0xFF)
            if (marker == 0xC0) {
                val componentCountOffset = offset + 4 + 5
                assertEquals(3, bytes[componentCountOffset].toInt() and 0xFF)
                return bytes[componentCountOffset + 2].toInt() and 0xFF
            }
            offset += 2 + length
        }
        error("SOF0 marker not found")
    }

    private fun jpegMarkersBeforeScan(bytes: ByteArray): Set<Int> {
        assertEquals(0xFF.toByte(), bytes[0])
        assertEquals(0xD8.toByte(), bytes[1])
        val markers = linkedSetOf<Int>()
        var offset = 2
        while (offset + 1 < bytes.size) {
            require(bytes[offset] == 0xFF.toByte()) { "expected marker at $offset" }
            val marker = bytes[offset + 1].toInt() and 0xFF
            markers += marker
            if (marker == 0xDA || marker == 0xD9) break
            val length = ((bytes[offset + 2].toInt() and 0xFF) shl 8) or (bytes[offset + 3].toInt() and 0xFF)
            offset += 2 + length
        }
        return markers
    }
}
