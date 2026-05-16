package org.skia.foundation


import org.graphiks.math.SkColorSetARGB
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.foundation.SkEncodedImageFormat

/**
 * Phase R2.12 — covers the [SkImage.encodeToData] member contract.
 *
 * Specifically checks that the member returns an [SkData] wrapper
 * (not a raw `ByteArray`, which is what the previous extension on
 * the encode package returned), the default format is PNG, and
 * unsupported formats short-circuit to `null`. The deeper
 * round-trip / quality / encoder-equality assertions live in the
 * encode package's [org.skia.encode.SkImageEncodeTest].
 */
class SkImageEncodeMemberTest {

    private fun makeImage(): SkImage {
        val bm = SkBitmap(4, 4, SkColorSpace.makeSRGB(), SkColorType.kRGBA_8888)
        for (i in bm.pixels.indices) bm.pixels[i] = SkColorSetARGB(0xFF, i * 16, 0, 255 - i * 16)
        return bm.asImage()
    }

    @Test
    fun `default member returns SkData with PNG bytes`() {
        val img = makeImage()
        val data: SkData? = img.encodeToData()
        assertNotNull(data)
        val bytes = data!!.toByteArray()
        assertTrue(bytes.size >= 8, "PNG must be at least 8 bytes (signature)")
        // PNG signature header.
        assertEquals(0x89.toByte(), bytes[0])
        assertEquals(0x50.toByte(), bytes[1])
        assertEquals(0x4E.toByte(), bytes[2])
        assertEquals(0x47.toByte(), bytes[3])
    }

    @Test
    fun `explicit format and quality also returns SkData`() {
        val img = makeImage()
        val png = img.encodeToData(SkEncodedImageFormat.kPNG, 100)
        val jpg = img.encodeToData(SkEncodedImageFormat.kJPEG, 80)
        assertNotNull(png)
        assertNotNull(jpg)
        // JPEG starts with SOI (FFD8).
        val jpgBytes = jpg!!.toByteArray()
        assertEquals(0xFF.toByte(), jpgBytes[0])
        assertEquals(0xD8.toByte(), jpgBytes[1])
    }

    @Test
    fun `unsupported encoder formats return null SkData`() {
        val img = makeImage()
        assertNull(img.encodeToData(SkEncodedImageFormat.kGIF, 100))
        assertNull(img.encodeToData(SkEncodedImageFormat.kBMP, 100))
        assertNull(img.encodeToData(SkEncodedImageFormat.kWBMP, 100))
        assertNull(img.encodeToData(SkEncodedImageFormat.kWEBP, 100))
    }
}
