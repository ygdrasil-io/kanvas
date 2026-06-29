package org.skia.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.foundation.SkEncodedOrigin
import org.skia.foundation.SkBitmap
import org.graphiks.math.SkColor
import org.skia.foundation.SkImageInfo

/**
 * Unit tests for [PixmapUtils.Orient] and [PixmapUtils.SwapWidthHeight].
 *
 * Origin coverage (R-suivi.9 complete — all 8 origins implemented).
 * See [PixmapUtilsOrientAllOriginsTest] for exhaustive coverage of
 * the six rotation/transpose origins on a 4×4 source ; this file keeps
 * the original 2×2 row-flip smoke tests.
 */
class PixmapUtilsTest {

    private val opaqueRed: SkColor = (0xFFFF0000).toInt()
    private val opaqueBlue: SkColor = (0xFF0000FF).toInt()

    /**
     * Build a 2×2 bitmap with distinguishable rows :
     * - row 0 (top)    : red, red
     * - row 1 (bottom) : blue, blue
     */
    private fun twoToneBitmap(): SkBitmap {
        val b = SkBitmap(2, 2)
        b.setPixel(0, 0, opaqueRed); b.setPixel(1, 0, opaqueRed)
        b.setPixel(0, 1, opaqueBlue); b.setPixel(1, 1, opaqueBlue)
        return b
    }

    @Test
    fun `Orient kTopLeft is an identity copy`() {
        val src = twoToneBitmap()
        val dst = SkBitmap(2, 2)
        val ok = PixmapUtils.Orient(dst, src, SkEncodedOrigin.kTopLeft)
        assertTrue(ok)
        assertEquals(opaqueRed, dst.getPixel(0, 0))
        assertEquals(opaqueRed, dst.getPixel(1, 0))
        assertEquals(opaqueBlue, dst.getPixel(0, 1))
        assertEquals(opaqueBlue, dst.getPixel(1, 1))
    }

    @Test
    fun `Orient kBottomLeft flips rows vertically`() {
        val src = twoToneBitmap()
        val dst = SkBitmap(2, 2)
        val ok = PixmapUtils.Orient(dst, src, SkEncodedOrigin.kBottomLeft)
        assertTrue(ok)
        // Source row 0 (red) should now be at the bottom (y = 1).
        assertEquals(opaqueRed, dst.getPixel(0, 1))
        assertEquals(opaqueRed, dst.getPixel(1, 1))
        // Source row 1 (blue) should now be at the top (y = 0).
        assertEquals(opaqueBlue, dst.getPixel(0, 0))
        assertEquals(opaqueBlue, dst.getPixel(1, 0))
    }

    @Test
    fun `Orient with mismatched dimensions returns false`() {
        val src = twoToneBitmap()
        val dst = SkBitmap(3, 3)
        val ok = PixmapUtils.Orient(dst, src, SkEncodedOrigin.kTopLeft)
        assertFalse(ok)
    }

    @Test
    fun `SwapWidthHeight swaps the two dimensions`() {
        val info = SkImageInfo.Make(width = 7, height = 11)
        val swapped = PixmapUtils.SwapWidthHeight(info)
        assertEquals(11, swapped.width)
        assertEquals(7, swapped.height)
        assertEquals(info.colorType, swapped.colorType)
        assertEquals(info.alphaType, swapped.alphaType)
    }
}
