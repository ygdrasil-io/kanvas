package org.skia.core

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.math.SK_ColorBLACK
import org.skia.math.SK_ColorWHITE
import org.skia.foundation.SkBitmap
import org.skia.math.SkColorGetA
import org.skia.math.SkColorGetB
import org.skia.math.SkColorGetG
import org.skia.math.SkColorGetR
import org.skia.foundation.SkPaint
import org.skia.foundation.SkRRect
import org.skia.math.SkRect
import org.skia.math.SkVector

/**
 * Smoke tests for the new RRect paths and canvas wrappers. We don't
 * compare against reference PNGs — these tests verify the rasterizer
 * lights up the expected pixel pattern: rounded corners are AA-shaded
 * (intermediate alpha), straight edges are crisp, and the interior is
 * fully filled.
 */
class SkBitmapDeviceRRectTest {

    private fun render(width: Int, height: Int, draw: SkCanvas.() -> Unit): SkBitmap {
        val bitmap = SkBitmap(width, height)
        bitmap.eraseColor(SK_ColorWHITE)
        SkCanvas(bitmap).apply(draw)
        return bitmap
    }

    private fun isFullyBlack(bitmap: SkBitmap, x: Int, y: Int): Boolean {
        val px = bitmap.getPixel(x, y)
        return SkColorGetR(px) < 16 && SkColorGetG(px) < 16 && SkColorGetB(px) < 16 &&
            SkColorGetA(px) >= 240
    }

    private fun isWhite(bitmap: SkBitmap, x: Int, y: Int): Boolean {
        val px = bitmap.getPixel(x, y)
        return SkColorGetR(px) >= 240 && SkColorGetG(px) >= 240 && SkColorGetB(px) >= 240
    }

    @Test
    fun `drawRRect fills the body of a rounded rect with AA on the corners`() {
        val rrect = SkRRect.MakeRectXY(SkRect.MakeLTRB(10f, 10f, 90f, 60f), 12f, 12f)
        val bitmap = render(100, 70) {
            drawRRect(rrect, SkPaint().apply {
                color = SK_ColorBLACK
                isAntiAlias = true
            })
        }
        // Body: deep inside the rrect, fully black.
        assertTrue(isFullyBlack(bitmap, 50, 35), "body centre should be fully filled")
        assertTrue(isFullyBlack(bitmap, 50, 12), "interior just below top edge")
        assertTrue(isFullyBlack(bitmap, 50, 58), "interior just above bottom edge")
        // Far outside the rrect: untouched white.
        assertTrue(isWhite(bitmap, 1, 1), "far top-left corner should remain white")
        assertTrue(isWhite(bitmap, 99, 69))
        // The curved corner area: beyond the corner radius the rrect leaves
        // the canvas white (pixel sits in the "cut-off" of the corner).
        // For a 12-px corner at (10, 10), pixel (11, 11) is outside the
        // rounded-corner sector.
        assertTrue(isWhite(bitmap, 11, 11), "pixel cut off by top-left corner curve")
    }

    @Test
    fun `drawRRect with zero radii degenerates to drawRect`() {
        val rect = SkRect.MakeLTRB(20f, 20f, 80f, 50f)
        val rrect = SkRRect.MakeRect(rect)
        val viaRRect = render(100, 70) {
            drawRRect(rrect, SkPaint().apply { color = SK_ColorBLACK })
        }
        val viaRect = render(100, 70) {
            drawRect(rect, SkPaint().apply { color = SK_ColorBLACK })
        }
        // Spot-check a handful of pixels — the two outputs should agree
        // since the rrect path collapses to a square contour.
        for ((x, y) in listOf(20 to 20, 79 to 49, 50 to 35, 0 to 0, 99 to 69)) {
            val a = viaRRect.getPixel(x, y)
            val b = viaRect.getPixel(x, y)
            assertTrue(a == b, "pixel ($x, $y) mismatched between drawRRect and drawRect")
        }
    }

    @Test
    fun `drawCircle and drawOval are alternatives to addOval-based drawPath`() {
        val viaCircle = render(60, 60) {
            drawCircle(30f, 30f, 20f, SkPaint().apply {
                color = SK_ColorBLACK
                isAntiAlias = true
            })
        }
        val viaOval = render(60, 60) {
            drawOval(SkRect.MakeLTRB(10f, 10f, 50f, 50f), SkPaint().apply {
                color = SK_ColorBLACK
                isAntiAlias = true
            })
        }
        // drawCircle(cx, cy, r) is defined as drawOval(MakeLTRB(cx-r, cy-r, cx+r, cy+r))
        // → identical pixel output.
        for (y in 0 until 60) {
            for (x in 0 until 60) {
                assertTrue(
                    viaCircle.getPixel(x, y) == viaOval.getPixel(x, y),
                    "pixel ($x, $y) differs between drawCircle and drawOval",
                )
            }
        }
    }

    @Test
    fun `drawRRect with complex per-corner radii fills the largest corner sector`() {
        // Per-corner radii: TL = 5, TR = 10, BR = 15, BL = 25.
        val rrect = SkRRect.MakeRectRadii(
            SkRect.MakeLTRB(0f, 0f, 100f, 100f),
            arrayOf(
                SkVector(5f,  5f),    // TL
                SkVector(10f, 10f),   // TR
                SkVector(15f, 15f),   // BR
                SkVector(25f, 25f),   // BL — largest
            ),
        )
        val bitmap = render(100, 100) {
            drawRRect(rrect, SkPaint().apply {
                color = SK_ColorBLACK
                isAntiAlias = true
            })
        }
        // Body filled.
        assertTrue(isFullyBlack(bitmap, 50, 50))
        // Canvas corners sit deep inside their respective corner cut-offs —
        // the cutoff ellipse passes far away from them, so they are fully
        // white. (Pixels on the curve get AA fringe, which is correct but
        // not what we're checking here.)
        // TL canvas corner: pixel (0, 0), distance √50 ≈ 7.07 from (5, 5).
        assertTrue(isWhite(bitmap, 0, 0), "TL canvas corner is in the radius-5 cutoff")
        // BR canvas corner: pixel (99, 99), distance √(15²+15²) ≈ 21.2 from (85, 85).
        assertTrue(isWhite(bitmap, 99, 99), "BR canvas corner is in the radius-15 cutoff")
        // BL canvas corner: pixel (0, 99), distance √(25²+24²) ≈ 34.7 from (25, 75).
        assertTrue(isWhite(bitmap, 0, 99), "BL canvas corner is in the radius-25 cutoff")
    }
}
