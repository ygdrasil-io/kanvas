package org.skia.foundation


import org.graphiks.math.SkColorSetARGB
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.graphiks.math.SkIRect

/**
 * Phase R2.12 — covers [SkImage.makeSubset].
 *
 * The contract mirrors upstream's `SkImage::makeSubset` (`SkImage.h:758-778`) :
 *  - empty / out-of-bounds subsets return `null`.
 *  - in-bounds subsets return a fresh image with pixel-identical
 *    contents.
 *  - colour-type and colour-space metadata propagate through.
 */
class SkImageSubsetTest {

    private fun gradientImage(w: Int, h: Int): SkImage {
        val bm = SkBitmap(w, h, SkColorSpace.makeSRGB(), SkColorType.kRGBA_8888)
        for (y in 0 until h) for (x in 0 until w) {
            val r = (x * 31 + y * 7) and 0xFF
            val g = (y * 53) and 0xFF
            val b = (x * 17 + y * 11) and 0xFF
            bm.pixels[y * w + x] = SkColorSetARGB(0xFF, r, g, b)
        }
        return bm.asImage()
    }

    @Test
    fun `interior subset returns pixel-identical block`() {
        val src = gradientImage(10, 10)
        val sub = src.makeSubset(SkIRect.MakeLTRB(2, 3, 7, 8))
        assertNotNull(sub)
        assertEquals(5, sub!!.width)
        assertEquals(5, sub.height)
        for (y in 0 until 5) for (x in 0 until 5) {
            assertEquals(
                src.peekPixel(2 + x, 3 + y),
                sub.peekPixel(x, y),
                "pixel at sub ($x, $y) must match src (${2 + x}, ${3 + y})",
            )
        }
    }

    @Test
    fun `subset preserves colorType and colorSpace`() {
        val cs = SkColorSpace.makeSRGB()
        val img = SkImage(4, 4, IntArray(16), SkColorType.kAlpha_8, cs)
        val sub = img.makeSubset(SkIRect.MakeWH(2, 2))!!
        assertEquals(SkColorType.kAlpha_8, sub.colorType)
        // Singleton sRGB → reference equality holds.
        assertSame(cs, sub.colorSpace)
    }

    @Test
    fun `full-bounds subset still allocates a fresh buffer`() {
        val src = gradientImage(3, 3)
        val sub = src.makeSubset(SkIRect.MakeWH(3, 3))!!
        // The returned image holds its own pixels — mutating the
        // source's `pixels` array (we can't; it's `internal val`)
        // wouldn't leak into the subset. We assert non-aliasing via
        // the array reference inequality.
        assertNotSame(src.pixels, sub.pixels)
        // …and a pixel-by-pixel equality check.
        for (y in 0 until 3) for (x in 0 until 3) {
            assertEquals(src.peekPixel(x, y), sub.peekPixel(x, y))
        }
    }

    @Test
    fun `empty subset returns null`() {
        val img = gradientImage(8, 8)
        assertNull(img.makeSubset(SkIRect.MakeLTRB(3, 4, 3, 4)))
        assertNull(img.makeSubset(SkIRect.MakeLTRB(5, 5, 1, 1)))
    }

    @Test
    fun `out-of-bounds subset returns null`() {
        val img = gradientImage(4, 4)
        // Right past image.
        assertNull(img.makeSubset(SkIRect.MakeLTRB(0, 0, 5, 4)))
        // Bottom past image.
        assertNull(img.makeSubset(SkIRect.MakeLTRB(0, 0, 4, 5)))
        // Negative left.
        assertNull(img.makeSubset(SkIRect.MakeLTRB(-1, 0, 3, 3)))
        // Entirely outside.
        assertNull(img.makeSubset(SkIRect.MakeLTRB(10, 10, 12, 12)))
    }
}
