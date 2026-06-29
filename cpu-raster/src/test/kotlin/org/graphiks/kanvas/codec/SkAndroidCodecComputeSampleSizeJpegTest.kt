package org.graphiks.kanvas.codec

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.graphiks.math.SkISize
import org.graphiks.kanvas.codec.jpeg.JpegEncoder
import org.graphiks.kanvas.codec.png.PngEncoder
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkEncodedImageFormat

/**
 * R-suivi.35 verification — JPEG-specific sample-size picker. libjpeg's
 * native DCT scaling supports `M/8` factors with `M ∈ {1, 2, 4, 8}` ;
 * the picker must clamp the requested sample size to that set.
 */
class AndroidCodecComputeSampleSizeJpegTest {

    @Test
    fun `JPEG computeSampleSize is clamped to {1, 2, 4, 8}`() {
        val codec = AndroidCodec.MakeFromData(synthJpeg(64, 64))!!
        assertEquals(SkEncodedImageFormat.kJPEG, codec.getEncodedFormat())
        // For every requested target size, the answer must be one of {1, 2, 4, 8}.
        val targets = listOf(
            SkISize.Make(1, 1),
            SkISize.Make(2, 2),
            SkISize.Make(4, 4),
            SkISize.Make(8, 8),
            SkISize.Make(16, 16),
            SkISize.Make(32, 32),
            SkISize.Make(64, 64),
            SkISize.Make(100, 100),
        )
        for (target in targets) {
            val s = codec.computeSampleSize(target)
            assertTrue(s in setOf(1, 2, 4, 8), "JPEG sampleSize must be in {1,2,4,8} ; got $s for target $target")
        }
    }

    @Test
    fun `JPEG computeSampleSize caps at 8`() {
        // 64x64 source, asking for 1x1 — the generic picker would pick 64,
        // but JPEG must clamp to 8.
        val codec = AndroidCodec.MakeFromData(synthJpeg(64, 64))!!
        val s = codec.computeSampleSize(SkISize.Make(1, 1))
        assertEquals(8, s)
    }

    @Test
    fun `JPEG computeSampleSize picks 4 for quarter-size target`() {
        val codec = AndroidCodec.MakeFromData(synthJpeg(64, 64))!!
        // 16x16 → 64/4 = 16 → sample size 4.
        val s = codec.computeSampleSize(SkISize.Make(16, 16))
        assertEquals(4, s)
    }

    @Test
    fun `JPEG computeSampleSize picks 2 for half-size target`() {
        val codec = AndroidCodec.MakeFromData(synthJpeg(64, 64))!!
        val s = codec.computeSampleSize(SkISize.Make(32, 32))
        assertEquals(2, s)
    }

    @Test
    fun `JPEG computeSampleSize returns 1 when request is at-or-larger than source`() {
        val codec = AndroidCodec.MakeFromData(synthJpeg(64, 64))!!
        assertEquals(1, codec.computeSampleSize(SkISize.Make(64, 64)))
        assertEquals(1, codec.computeSampleSize(SkISize.Make(128, 128)))
    }

    @Test
    fun `PNG computeSampleSize still uses generic pow-2`() {
        // Verifies the format dispatch doesn't accidentally apply
        // JPEG clamping to other formats.
        val codec = AndroidCodec.MakeFromData(synthPng(64, 64))!!
        assertEquals(SkEncodedImageFormat.kPNG, codec.getEncodedFormat())
        // 64x64 asking for 1x1 picks 64 (largest pow-2, no libjpeg cap).
        val s = codec.computeSampleSize(SkISize.Make(1, 1))
        assertEquals(64, s)
    }

    // ─── Helpers ──────────────────────────────────────────────────────

    private fun synthJpeg(width: Int, height: Int): ByteArray {
        val img = SkBitmap(width, height)
        for (y in 0 until height) for (x in 0 until width) {
            val r = (x * 255 / maxOf(1, width - 1)).coerceIn(0, 255)
            val g = (y * 255 / maxOf(1, height - 1)).coerceIn(0, 255)
            img.setPixel(x, y, (0xFF shl 24) or (r shl 16) or (g shl 8) or 0x40)
        }
        return JpegEncoder.encode(img) ?: error("Synthetic JPEG encode failed")
    }

    private fun synthPng(width: Int, height: Int): ByteArray {
        val img = SkBitmap(width, height)
        for (y in 0 until height) for (x in 0 until width) {
            img.setPixel(x, y, (0xFF shl 24) or (x * 4) or ((y * 4) shl 8))
        }
        return PngEncoder.encode(img) ?: error("Synthetic PNG encode failed")
    }
}
