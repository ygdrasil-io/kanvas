package org.skia.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.skia.foundation.SK_ColorBLUE
import org.skia.foundation.SK_ColorGREEN
import org.skia.foundation.SK_ColorRED
import org.skia.foundation.SK_ColorWHITE
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkFilterMode
import org.skia.foundation.SkImage
import org.skia.math.SkRect

/**
 * S7-C — verifies the full N × M lattice tessellation in
 * [SkCanvas.drawImageLattice]. Builds a simple 9-patch source image
 * with distinguishable corner / edge / centre colours, draws it into
 * an oversized destination, then samples back the destination at
 * known pixel positions to confirm corners stayed pixel-sized while
 * edges / centre stretched to fill the slack.
 *
 * Source layout — 6 × 6 image, divs at `[2, 4]` on both axes :
 *
 * ```
 *   ┌──┬──┬──┐ rows 0..1  : top corners (RED) + top-mid (GREEN)
 *   │RR│GG│RR│
 *   ├──┼──┼──┤ rows 2..3  : left-mid (GREEN) + centre (BLUE) + right-mid (GREEN)
 *   │GG│BB│GG│
 *   ├──┼──┼──┤ rows 4..5  : bottom corners (RED) + bottom-mid (GREEN)
 *   │RR│GG│RR│
 *   └──┴──┴──┘
 * ```
 *
 * Drawn into a 30 × 30 dst, the corners stay 2 × 2 (pixel-equal to
 * source) and the centre / edges expand to fill the remaining
 * `30 − 2 − 2 = 26` pixels along each axis.
 */
class SkCanvasDrawImageLatticeFullTest {

    private fun build9PatchImage(): SkImage {
        val bm = SkBitmap(6, 6)
        // Fill the 9 cells per the layout above.
        // Corners (RED) — outer 2×2 blocks.
        fillRect(bm, 0, 0, 2, 2, SK_ColorRED)
        fillRect(bm, 4, 0, 2, 2, SK_ColorRED)
        fillRect(bm, 0, 4, 2, 2, SK_ColorRED)
        fillRect(bm, 4, 4, 2, 2, SK_ColorRED)
        // Edges (GREEN) — 2-pixel wide strips between corners.
        fillRect(bm, 2, 0, 2, 2, SK_ColorGREEN) // top-mid
        fillRect(bm, 2, 4, 2, 2, SK_ColorGREEN) // bottom-mid
        fillRect(bm, 0, 2, 2, 2, SK_ColorGREEN) // left-mid
        fillRect(bm, 4, 2, 2, 2, SK_ColorGREEN) // right-mid
        // Centre (BLUE).
        fillRect(bm, 2, 2, 2, 2, SK_ColorBLUE)
        return SkImage.Make(bm)
    }

    private fun fillRect(bm: SkBitmap, x: Int, y: Int, w: Int, h: Int, c: Int) {
        for (yy in y until y + h) for (xx in x until x + w) {
            bm.setPixel(xx, yy, c)
        }
    }

    @Test
    fun `9-patch corners stay pixel-sized when stretched into a larger dst`() {
        val image = build9PatchImage()
        val dstBitmap = SkBitmap(30, 30).also { it.eraseColor(SK_ColorWHITE) }
        val canvas = SkCanvas(dstBitmap)

        val lattice = SkLattice(
            xDivs = intArrayOf(2, 4),
            yDivs = intArrayOf(2, 4),
        )
        canvas.drawImageLattice(
            image,
            lattice,
            SkRect.MakeWH(30f, 30f),
            SkFilterMode.kNearest,
            null,
        )

        // Corners are at (0..1, 0..1), (28..29, 0..1), (0..1, 28..29),
        // (28..29, 28..29) — all RED.
        assertEquals(SK_ColorRED, dstBitmap.getPixel(0, 0), "top-left corner pixel")
        assertEquals(SK_ColorRED, dstBitmap.getPixel(1, 1), "top-left corner pixel inner")
        assertEquals(SK_ColorRED, dstBitmap.getPixel(29, 0), "top-right corner pixel")
        assertEquals(SK_ColorRED, dstBitmap.getPixel(0, 29), "bottom-left corner pixel")
        assertEquals(SK_ColorRED, dstBitmap.getPixel(29, 29), "bottom-right corner pixel")
    }

    @Test
    fun `9-patch edges stretch along their flexible axis`() {
        val image = build9PatchImage()
        val dstBitmap = SkBitmap(30, 30).also { it.eraseColor(SK_ColorWHITE) }
        val canvas = SkCanvas(dstBitmap)

        val lattice = SkLattice(
            xDivs = intArrayOf(2, 4),
            yDivs = intArrayOf(2, 4),
        )
        canvas.drawImageLattice(
            image,
            lattice,
            SkRect.MakeWH(30f, 30f),
            SkFilterMode.kNearest,
            null,
        )

        // Top-mid edge (GREEN) — between x = 2 and x = 28, y = 0..1.
        // Sample x = 15 (middle of the stretched span).
        assertEquals(SK_ColorGREEN, dstBitmap.getPixel(15, 0), "top-mid edge")
        assertEquals(SK_ColorGREEN, dstBitmap.getPixel(15, 1), "top-mid edge inner")

        // Bottom-mid edge (GREEN) — y = 28..29, x = 15.
        assertEquals(SK_ColorGREEN, dstBitmap.getPixel(15, 29), "bottom-mid edge")

        // Left-mid edge (GREEN) — x = 0..1, y = 15.
        assertEquals(SK_ColorGREEN, dstBitmap.getPixel(0, 15), "left-mid edge")
        // Right-mid edge (GREEN).
        assertEquals(SK_ColorGREEN, dstBitmap.getPixel(29, 15), "right-mid edge")
    }

    @Test
    fun `9-patch centre stretches to fill the whole flex region`() {
        val image = build9PatchImage()
        val dstBitmap = SkBitmap(30, 30).also { it.eraseColor(SK_ColorWHITE) }
        val canvas = SkCanvas(dstBitmap)

        val lattice = SkLattice(
            xDivs = intArrayOf(2, 4),
            yDivs = intArrayOf(2, 4),
        )
        canvas.drawImageLattice(
            image,
            lattice,
            SkRect.MakeWH(30f, 30f),
            SkFilterMode.kNearest,
            null,
        )

        // Centre (BLUE) — flex region runs from (2..28, 2..28).
        assertEquals(SK_ColorBLUE, dstBitmap.getPixel(15, 15), "centre middle")
        assertEquals(SK_ColorBLUE, dstBitmap.getPixel(3, 3), "centre near top-left")
        assertEquals(SK_ColorBLUE, dstBitmap.getPixel(27, 27), "centre near bottom-right")
    }

    @Test
    fun `degenerate lattice with bad divs falls back to drawImageRect`() {
        val image = build9PatchImage()
        val dstBitmap = SkBitmap(12, 12).also { it.eraseColor(SK_ColorWHITE) }
        val canvas = SkCanvas(dstBitmap)

        // Out-of-range div (10 > image.width=6) — should fall back.
        val badLattice = SkLattice(xDivs = intArrayOf(10), yDivs = intArrayOf(2))
        canvas.drawImageLattice(
            image,
            badLattice,
            SkRect.MakeWH(12f, 12f),
            SkFilterMode.kNearest,
            null,
        )
        // The whole image is stretched to fill — top-left pixel is RED
        // (corner of the source image).
        assertEquals(SK_ColorRED, dstBitmap.getPixel(0, 0))
    }

    @Test
    fun `transparent rect type leaves destination untouched`() {
        val image = build9PatchImage()
        val dstBitmap = SkBitmap(30, 30).also { it.eraseColor(SK_ColorWHITE) }
        val canvas = SkCanvas(dstBitmap)

        // Mark the centre cell (idx = 4 in row-major 3×3) as transparent.
        val rectTypes = Array(9) { SkLattice.RectType.kDefault }
        rectTypes[4] = SkLattice.RectType.kTransparent
        val lattice = SkLattice(
            xDivs = intArrayOf(2, 4),
            yDivs = intArrayOf(2, 4),
            rectTypes = rectTypes,
        )
        canvas.drawImageLattice(
            image,
            lattice,
            SkRect.MakeWH(30f, 30f),
            SkFilterMode.kNearest,
            null,
        )

        // Corners still RED (lattice cells 0, 2, 6, 8).
        assertEquals(SK_ColorRED, dstBitmap.getPixel(0, 0))
        // Centre untouched — should be the WHITE eraseColor, not BLUE.
        assertEquals(SK_ColorWHITE, dstBitmap.getPixel(15, 15))
    }
}
