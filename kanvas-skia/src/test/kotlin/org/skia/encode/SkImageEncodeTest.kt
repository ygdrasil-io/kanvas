package org.skia.encode

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.graphiks.kanvas.codec.Codec
import org.skia.foundation.SkEncodedImageFormat
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkColorType
import org.skia.foundation.SkImage

/**
 * D3.6 / R2.12 verification suite for `SkImage::encodeToData`.
 *
 * Phase R2.12 promoted the historical [SkImage.encodeToData] extension
 * to a member returning `SkData?` (matching the upstream
 * `sk_sp<SkData>` shape). This file still lives in the encode package
 * because the round-trip assertions need [Codec] and the per-format
 * encoders, but the call site now goes through the [SkImage] member.
 *
 * Covers :
 *  - default format (PNG) round-trips byte-identical via [Codec].
 *  - JPEG dispatch honours the [quality] argument (lower → smaller).
 *  - Unsupported formats (GIF / BMP / WBMP / WEBP / …) return `null`
 *    rather than crashing. This convenience API remains PNG/JPEG-only;
 *    direct BMP/WBMP/WebP encoder entry points are tested separately.
 *  - The member agrees pixel-for-pixel with calling
 *    [SkPngEncoder.Encode] directly (so it really is just plumbing,
 *    no transformation in between).
 */
class SkImageEncodeTest {

    @Test
    fun `default format is PNG and round-trips byte-identical`() {
        val image = makeImage(8, 8)
        val data = image.encodeToData()
        assertNotNull(data)
        val bytes = data!!.toByteArray()
        // PNG signature.
        assertEquals(0x89.toByte(), bytes[0])
        assertEquals(0x50.toByte(), bytes[1])
        // Decoded pixels must equal what the image exposes via peekPixel.
        val codec = Codec.MakeFromData(bytes)!!
        val (decoded, result) = codec.getImage()
        assertEquals(Codec.Result.kSuccess, result)
        assertNotNull(decoded)
        for (y in 0 until 8) for (x in 0 until 8) {
            assertEquals(
                image.peekPixel(x, y),
                decoded!!.getPixel(x, y),
                "PNG encodeToData must be lossless at ($x, $y)",
            )
        }
    }

    @Test
    fun `JPEG format honours the quality argument`() {
        val image = makeImage(64, 64)
        val highQ = image.encodeToData(SkEncodedImageFormat.kJPEG, quality = 100)!!.toByteArray()
        val lowQ = image.encodeToData(SkEncodedImageFormat.kJPEG, quality = 25)!!.toByteArray()
        assertTrue(
            lowQ.size < highQ.size,
            "low-quality JPEG must be smaller : q=25 → ${lowQ.size}, q=100 → ${highQ.size}",
        )
        // Output is a valid JPEG (SOI marker).
        assertEquals(0xFF.toByte(), lowQ[0])
        assertEquals(0xD8.toByte(), lowQ[1])
    }

    @Test
    fun `unsupported formats return null`() {
        val image = makeImage(2, 2)
        // The member convenience dispatch is intentionally PNG/JPEG-only.
        // BMP/WBMP/WebP have direct encoder APIs with their own tests.
        assertNull(image.encodeToData(SkEncodedImageFormat.kGIF, 100))
        assertNull(image.encodeToData(SkEncodedImageFormat.kBMP, 100))
        assertNull(image.encodeToData(SkEncodedImageFormat.kWBMP, 100))
        assertNull(image.encodeToData(SkEncodedImageFormat.kWEBP, 100))
    }

    @Test
    fun `PNG output agrees with calling SkPngEncoder Encode directly`() {
        val image = makeImage(4, 4)
        val viaMember = image.encodeToData(SkEncodedImageFormat.kPNG, 100)!!.toByteArray()
        // Reconstruct the bitmap the wrapper builds internally (sRGB,
        // 8888, image.peekPixel for every pixel).
        val bitmap = SkBitmap(4, 4, SkColorSpace.makeSRGB(), SkColorType.kRGBA_8888)
        for (y in 0 until 4) for (x in 0 until 4) {
            bitmap.pixels[y * 4 + x] = image.peekPixel(x, y)
        }
        val viaDirect = SkPngEncoder.Encode(bitmap)!!
        assertEquals(
            viaMember.toList(),
            viaDirect.toList(),
            "encodeToData must be a thin wrapper — no transformation",
        )
    }

    private fun makeImage(width: Int, height: Int): SkImage {
        val bitmap = SkBitmap(width, height, SkColorSpace.makeSRGB(), SkColorType.kRGBA_8888)
        for (y in 0 until height) for (x in 0 until width) {
            val r = (x * 255 / maxOf(1, width - 1)).coerceIn(0, 255)
            val g = (y * 255 / maxOf(1, height - 1)).coerceIn(0, 255)
            // Some non-trivial low-bits noise so the JPEG quality test
            // sees a meaningful size difference between q=25 and q=100.
            val b = ((x xor y) * 17) and 0xFF
            bitmap.pixels[y * width + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        return bitmap.asImage()
    }
}
