package org.skia.encode

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.codec.SkCodec
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkColorType

/**
 * D3.5 verification suite for [SkJpegEncoder].
 *
 * Covers :
 *  - `Encode(bitmap, options)` returns non-null bytes with the JPEG
 *    SOI signature.
 *  - Encode → decode round-trip via [SkCodec] preserves opaque
 *    pixels within JPEG quantisation tolerance (lossy ; relaxed
 *    delta).
 *  - Lower [SkJpegEncoder.Options.quality] produces a smaller file
 *    than the default — confirms quality plumbing reaches the
 *    underlying writer.
 *  - [SkJpegEncoder.Options] rejects out-of-range quality.
 *  - JPEG output is always opaque even when the source bitmap had
 *    alpha < 255 — alpha is dropped per the
 *    [SkJpegEncoder.AlphaOption.kIgnore] default.
 */
class SkJpegEncoderTest {

    @Test
    fun `Encode produces JPEG bytes with the SOI signature`() {
        val bitmap = makeFlat(16, 16, 0xFF808080.toInt())
        val bytes = SkJpegEncoder.Encode(bitmap)
        assertNotNull(bytes)
        assertTrue(bytes!!.size > 0)
        assertEquals(0xFF.toByte(), bytes[0])
        assertEquals(0xD8.toByte(), bytes[1])
        assertEquals(0xFF.toByte(), bytes[2])
    }

    @Test
    fun `flat-colour JPEG round-trips within quantisation tolerance`() {
        val src = makeFlat(16, 16, 0xFF808080.toInt())
        val bytes = SkJpegEncoder.Encode(src, SkJpegEncoder.Options(quality = 100))!!
        val codec = SkCodec.MakeFromData(bytes)!!
        val (decoded, result) = codec.getImage()
        assertEquals(SkCodec.Result.kSuccess, result)
        assertNotNull(decoded)
        // JPEG is lossy ; even a flat mid-grey can drift a few ulps
        // on the chroma channels through the subsampling pipeline.
        for (y in 0 until 16) for (x in 0 until 16) {
            val px = decoded!!.getPixel(x, y)
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
        // A non-flat image is needed — flat colours hit the same DC
        // bins regardless of quality, so the file size barely moves.
        val bitmap = makeGradient(64, 64)
        val highQ = SkJpegEncoder.Encode(bitmap, SkJpegEncoder.Options(quality = 100))!!
        val lowQ = SkJpegEncoder.Encode(bitmap, SkJpegEncoder.Options(quality = 25))!!
        assertTrue(
            lowQ.size < highQ.size,
            "low-quality JPEG must be smaller : q=25 → ${lowQ.size}, q=100 → ${highQ.size}",
        )
    }

    @Test
    fun `Options rejects quality outside 0 to 100`() {
        assertThrows(IllegalArgumentException::class.java) {
            SkJpegEncoder.Options(quality = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SkJpegEncoder.Options(quality = 101)
        }
        SkJpegEncoder.Options(quality = 0)
        SkJpegEncoder.Options(quality = 100)
    }

    @Test
    fun `JPEG output drops alpha — every decoded pixel is fully opaque`() {
        // Source bitmap has alpha = 0x80 ; encoder must still produce
        // an opaque JPEG (alpha is dropped per kIgnore default).
        val src = makeFlat(8, 8, 0x80FFFFFF.toInt())
        val bytes = SkJpegEncoder.Encode(src)!!
        val (decoded, _) = SkCodec.MakeFromData(bytes)!!.getImage()
        assertNotNull(decoded)
        for (y in 0 until 8) for (x in 0 until 8) {
            val a = (decoded!!.getPixel(x, y) ushr 24) and 0xFF
            assertEquals(0xFF, a, "JPEG pixel ($x,$y) must be fully opaque after encode")
        }
    }

    private fun makeFlat(width: Int, height: Int, color: Int): SkBitmap {
        val b = SkBitmap(width, height, SkColorSpace.makeSRGB(), SkColorType.kRGBA_8888)
        for (y in 0 until height) for (x in 0 until width) {
            b.pixels[y * width + x] = color
        }
        return b
    }

    private fun makeGradient(width: Int, height: Int): SkBitmap {
        val b = SkBitmap(width, height, SkColorSpace.makeSRGB(), SkColorType.kRGBA_8888)
        for (y in 0 until height) for (x in 0 until width) {
            val r = (x * 255 / maxOf(1, width - 1)).coerceIn(0, 255)
            val g = (y * 255 / maxOf(1, height - 1)).coerceIn(0, 255)
            // Add some high-frequency noise so the JPEG quantizer has
            // something non-trivial to compress at low quality.
            val b2 = ((x xor y) * 17) and 0xFF
            b.pixels[y * width + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b2
        }
        return b
    }
}
