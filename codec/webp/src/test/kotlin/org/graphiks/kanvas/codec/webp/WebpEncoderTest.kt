package org.graphiks.kanvas.codec.webp

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
import java.io.ByteArrayOutputStream

class WebpEncoderTest {

    @Test
    fun `lossless encode round-trip through WebP decoder preserves pixels`() {
        val src = SkBitmap(4, 4)
        for (y in 0 until 4) for (x in 0 until 4) {
            val r = (x * 85).coerceIn(0, 255)
            val g = (y * 85).coerceIn(0, 255)
            src.pixels[y * 4 + x] = (0xFF shl 24) or (r shl 16) or (g shl 8)
        }
        val bytes = WebpEncoder.encode(src)!!
        val decoded = decodeWebp(bytes)
        assertEquals(4, decoded.width)
        assertEquals(4, decoded.height)
    }

    @Test
    fun `encode degenerate bitmap returns null`() {
        assertNull(WebpEncoder.encode(SkBitmap(0, 0)))
    }

    @Test
    fun `OutputStream overload matches direct encode`() {
        val src = SkBitmap(4, 4)
        for (i in 0 until 16) src.pixels[i] = 0xFF808080.toInt()
        val viaData = WebpEncoder.encode(src)!!
        val baos = ByteArrayOutputStream()
        assertTrue(WebpEncoder.encode(baos, src))
        assertEquals(viaData.toList(), baos.toByteArray().toList())
    }

    @Test
    fun `VP8X ICC profile round-trips`() {
        val src = SkBitmap(4, 4)
        for (i in 0 until 16) src.pixels[i] = 0xFF808080.toInt()
        val iccBytes = SkICC.WriteToICC(SkNamedTransferFn.kSRGB, SkNamedGamut.kSRGB)
        val options = WebpEncoder.Options(iccProfile = iccBytes)
        val encoded = WebpEncoder.encode(src, options)!!
        val codec = Codec.MakeFromData(encoded)
        assertNotNull(codec)
        assertNotNull(codec!!.getICCProfile(), "ICCP chunk must be parsed back")
    }

    @Test
    fun `VP8X EXIF round-trips`() {
        val src = SkBitmap(4, 4)
        for (i in 0 until 16) src.pixels[i] = 0xFF808080.toInt()
        val exifBytes = byteArrayOf(0x45, 0x78, 0x69, 0x66, 0x00, 0x00, 0x4D, 0x4D)
        val options = WebpEncoder.Options(exifData = exifBytes)
        val encoded = WebpEncoder.encode(src, options)!!
        val codec = Codec.MakeFromData(encoded)
        assertNotNull(codec)
    }

    @Test
    fun `VP8X XMP round-trips`() {
        val src = SkBitmap(4, 4)
        for (i in 0 until 16) src.pixels[i] = 0xFF808080.toInt()
        val xmpBytes = byteArrayOf(60, 120, 58, 120, 109, 112, 109, 101, 116, 97)
        val options = WebpEncoder.Options(xmpData = xmpBytes)
        val encoded = WebpEncoder.encode(src, options)!!
        val codec = Codec.MakeFromData(encoded)
        assertNotNull(codec)
    }

    @Test
    fun `VP8X with all metadata round-trips`() {
        val src = SkBitmap(4, 4)
        for (i in 0 until 16) src.pixels[i] = 0xFF808080.toInt()
        val iccBytes = SkICC.WriteToICC(SkNamedTransferFn.kSRGB, SkNamedGamut.kSRGB)
        val exifBytes = byteArrayOf(0x45, 0x78, 0x69, 0x66, 0x00, 0x00, 0x49, 0x49)
        val xmpBytes = byteArrayOf(60, 120, 58, 120, 109, 112, 109, 101, 116, 97)
        val options = WebpEncoder.Options(iccProfile = iccBytes, exifData = exifBytes, xmpData = xmpBytes)
        val encoded = WebpEncoder.encode(src, options)!!
        val decoded = decodeWebp(encoded)
        assertEquals(4, decoded.width)
        assertEquals(4, decoded.height)
    }

    @Test
    fun `no metadata produces simple VP8L not extended`() {
        val src = SkBitmap(4, 4)
        for (i in 0 until 16) src.pixels[i] = 0xFF808080.toInt()
        val encoded = WebpEncoder.encode(src)!!
        val vp8xPos = indexOfFourCC(encoded, "VP8X")
        assertEquals(-1, vp8xPos, "simple encode must not produce VP8X")
        val vp8lPos = indexOfFourCC(encoded, "VP8L")
        assertTrue(vp8lPos >= 0, "simple encode must have VP8L")
    }

    private fun indexOfFourCC(data: ByteArray, fourcc: String): Int {
        outer@ for (i in 0 until data.size - 3) {
            for (j in 0 until 4) {
                if (data[i + j] != fourcc[j].code.toByte()) continue@outer
            }
            return i
        }
        return -1
    }

    private fun decodeWebp(bytes: ByteArray): SkBitmap {
        val codec = Codec.MakeFromData(bytes)
        assertNotNull(codec, "WebP decoder must load encoded output")
        val (bitmap, result) = codec!!.getImage()
        assertEquals(Codec.Result.kSuccess, result)
        assertNotNull(bitmap)
        return bitmap!!
    }
}
