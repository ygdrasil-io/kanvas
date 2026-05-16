package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.graphiks.math.SkIRect

/**
 * S7-A verification suite for [SkBitmap.eraseArea] — fills inside the
 * supplied rectangle (clipped to bitmap bounds) without disturbing
 * pixels outside it. Mirrors the upstream `SkBitmap::eraseArea`
 * contract used by `gm/skbug_257.cpp` (off-diagonal checker quadrants).
 */
class SkBitmapEraseAreaTest {

    private val red = 0xFFFF0000.toInt()
    private val blue = 0xFF0000FF.toInt()

    @Test
    fun `eraseArea fills the requested rectangle and leaves the rest unchanged`() {
        val bm = SkBitmap(8, 8)
        bm.eraseColor(red)
        bm.eraseArea(SkIRect.MakeLTRB(2, 3, 5, 6), blue)

        for (y in 0 until 8) {
            for (x in 0 until 8) {
                val expected = if (x in 2 until 5 && y in 3 until 6) blue else red
                assertEquals(expected, bm.getPixel(x, y), "pixel ($x, $y)")
            }
        }
    }

    @Test
    fun `eraseArea clips out-of-bounds rectangles`() {
        val bm = SkBitmap(4, 4)
        bm.eraseColor(red)
        // Spills past every edge — only the (0..4, 0..4) interior is
        // actually written.
        bm.eraseArea(SkIRect.MakeLTRB(-2, -2, 10, 10), blue)
        for (y in 0 until 4) {
            for (x in 0 until 4) {
                assertEquals(blue, bm.getPixel(x, y), "pixel ($x, $y)")
            }
        }
    }

    @Test
    fun `eraseArea on empty intersection is a no-op`() {
        val bm = SkBitmap(4, 4)
        bm.eraseColor(red)
        // Entirely outside the bitmap.
        bm.eraseArea(SkIRect.MakeLTRB(10, 10, 20, 20), blue)
        // Empty rectangle.
        bm.eraseArea(SkIRect.MakeLTRB(1, 1, 1, 1), blue)
        for (y in 0 until 4) {
            for (x in 0 until 4) {
                assertEquals(red, bm.getPixel(x, y), "pixel ($x, $y)")
            }
        }
    }

    @Test
    fun `eraseArea matches the manual setPixel loop on the diagonal-quadrants pattern`() {
        // Reproduces the Skbug257GM checker setup : two off-diagonal
        // quadrants of a 2*size x 2*size bitmap are flipped from c1 to c2.
        val size = 6
        val expected = SkBitmap(2 * size, 2 * size)
        expected.eraseColor(red)
        for (yy in 0 until size) {
            for (xx in 0 until size) {
                expected.setPixel(xx, yy, blue)                    // top-left
                expected.setPixel(xx + size, yy + size, blue)      // bottom-right
            }
        }

        val actual = SkBitmap(2 * size, 2 * size)
        actual.eraseColor(red)
        actual.eraseArea(SkIRect.MakeLTRB(0, 0, size, size), blue)
        actual.eraseArea(SkIRect.MakeLTRB(size, size, 2 * size, 2 * size), blue)

        for (y in 0 until 2 * size) {
            for (x in 0 until 2 * size) {
                assertEquals(expected.getPixel(x, y), actual.getPixel(x, y), "pixel ($x, $y)")
            }
        }
    }
}
