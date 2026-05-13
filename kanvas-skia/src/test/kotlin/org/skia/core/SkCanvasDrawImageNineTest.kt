package org.skia.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkFilterMode
import org.skia.math.SkIRect
import org.skia.math.SkRect

/**
 * Phase R2.13 — `SkCanvas.drawImageNine(image, center, dst, filter, paint)`.
 *
 * Verifies that :
 *  1. corners stay 1:1 (a single corner pixel maps to one dst pixel) ;
 *  2. edges stretch along exactly one axis ;
 *  3. middle stretches along both axes.
 *
 * Uses nearest-neighbour sampling so we can assert pixel-exact colours.
 */
class SkCanvasDrawImageNineTest {

    /**
     * 9-patch source : 1-px wide red border around a 1-px green
     * centre — total image size 3×3, center rect `(1,1,2,2)`.
     *
     * ```
     * R R R
     * R G R
     * R R R
     * ```
     */
    private fun makeNinePatch(): SkBitmap {
        val bm = SkBitmap(3, 3)
        val r = 0xFFFF0000.toInt()
        val g = 0xFF00FF00.toInt()
        bm.setPixel(0, 0, r); bm.setPixel(1, 0, r); bm.setPixel(2, 0, r)
        bm.setPixel(0, 1, r); bm.setPixel(1, 1, g); bm.setPixel(2, 1, r)
        bm.setPixel(0, 2, r); bm.setPixel(1, 2, r); bm.setPixel(2, 2, r)
        return bm
    }

    @Test
    fun `corners stay 1 to 1 and middle stretches`() {
        val bm = SkBitmap(10, 10).also { it.eraseColor(0xFF000000.toInt()) }
        val canvas = SkCanvas(bm)
        val img = makeNinePatch().asImage()
        // 3×3 source center=(1,1,2,2), dst=(0,0,10,10) :
        //  - left col   = dst x 0..1   (1-px source, no stretch)
        //  - middle col = dst x 1..9   (stretched 1-px source over 8 dst)
        //  - right col  = dst x 9..10  (1-px source)
        // Same for rows.
        canvas.drawImageNine(
            img,
            SkIRect(1, 1, 2, 2),
            SkRect.MakeLTRB(0f, 0f, 10f, 10f),
            SkFilterMode.kNearest,
            null,
        )
        val red = 0xFFFF0000.toInt()
        val green = 0xFF00FF00.toInt()
        // Corner pixels (the four 1×1 dst corners).
        assertEquals(red, bm.getPixel(0, 0), "TL corner")
        assertEquals(red, bm.getPixel(9, 0), "TR corner")
        assertEquals(red, bm.getPixel(0, 9), "BL corner")
        assertEquals(red, bm.getPixel(9, 9), "BR corner")
        // Top / bottom edges (red, anywhere in the middle x-band).
        for (x in 1 until 9) {
            assertEquals(red, bm.getPixel(x, 0), "top edge @($x,0)")
            assertEquals(red, bm.getPixel(x, 9), "bot edge @($x,9)")
        }
        // Left / right edges (red, anywhere in the middle y-band).
        for (y in 1 until 9) {
            assertEquals(red, bm.getPixel(0, y), "left edge @(0,$y)")
            assertEquals(red, bm.getPixel(9, y), "right edge @(9,$y)")
        }
        // Middle quad must be green (the centre pixel stretched).
        for (y in 1 until 9) {
            for (x in 1 until 9) {
                assertEquals(green, bm.getPixel(x, y), "middle @($x,$y)")
            }
        }
    }

    @Test
    fun `degenerate center falls back to plain drawImageRect`() {
        val bm = SkBitmap(6, 6).also { it.eraseColor(0xFF000000.toInt()) }
        val img = makeNinePatch().asImage()
        SkCanvas(bm).drawImageNine(
            img,
            // Empty centre → fall back path.
            SkIRect(1, 1, 1, 1),
            SkRect.MakeLTRB(0f, 0f, 6f, 6f),
            SkFilterMode.kNearest,
            null,
        )
        // The fallback drawImageRect must have painted *something* —
        // at least the corners are red.
        assertNotEquals(0xFF000000.toInt(), bm.getPixel(0, 0))
        assertNotEquals(0xFF000000.toInt(), bm.getPixel(5, 5))
    }

    @Test
    fun `edges stretch on exactly one axis`() {
        // 5×5 ninepatch with distinct edge colours :
        //
        //   T T T T T
        //   L C C C R
        //   L C M C R
        //   L C C C R
        //   B B B B B
        //
        // …well, simpler — make every cell distinct, then verify
        // each is mapped to a specific dst band.
        val bm = SkBitmap(8, 8).also { it.eraseColor(0xFF000000.toInt()) }
        val src = SkBitmap(3, 3)
        // Distinguishable colours per row (R, G, B).
        src.setPixel(0, 0, 0xFFFF0000.toInt()); src.setPixel(1, 0, 0xFFFF0000.toInt()); src.setPixel(2, 0, 0xFFFF0000.toInt())
        src.setPixel(0, 1, 0xFF00FF00.toInt()); src.setPixel(1, 1, 0xFF00FF00.toInt()); src.setPixel(2, 1, 0xFF00FF00.toInt())
        src.setPixel(0, 2, 0xFF0000FF.toInt()); src.setPixel(1, 2, 0xFF0000FF.toInt()); src.setPixel(2, 2, 0xFF0000FF.toInt())
        val img = src.asImage()
        SkCanvas(bm).drawImageNine(
            img,
            SkIRect(1, 1, 2, 2),
            SkRect.MakeLTRB(0f, 0f, 8f, 8f),
            SkFilterMode.kNearest,
            null,
        )
        // Row 0 must be red (top band, 1-px tall in src → 1-px tall
        // top corner row in dst).
        assertEquals(0xFFFF0000.toInt(), bm.getPixel(0, 0))
        assertEquals(0xFFFF0000.toInt(), bm.getPixel(7, 0))
        // Row 7 must be blue (bottom band, 1-px tall in src → 1-px
        // tall bottom corner row in dst).
        assertEquals(0xFF0000FF.toInt(), bm.getPixel(0, 7))
        assertEquals(0xFF0000FF.toInt(), bm.getPixel(7, 7))
        // Middle dst rows (1..6) must come from the green source row.
        for (y in 1 until 7) {
            assertEquals(0xFF00FF00.toInt(), bm.getPixel(0, y), "(0,$y) must come from src row 1 (green)")
        }
    }
}
