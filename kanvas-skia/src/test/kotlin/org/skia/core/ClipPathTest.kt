package org.skia.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SK_ColorWHITE
import org.skia.foundation.SkBitmap
import org.graphiks.math.SkColorGetA
import org.graphiks.math.SkColorGetB
import org.graphiks.math.SkColorGetG
import org.graphiks.math.SkColorGetR
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkColorType
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathFillType
import org.skia.foundation.SkRRect
import org.graphiks.math.SkRect

/**
 * Unit tests for [SkCanvas.clipPath] and [SkCanvas.clipRRect] (Phase 7q).
 *
 * Coverage :
 *  - Circle clip on a filled rect : pixels inside the circle are written ;
 *    pixels outside stay at the bg colour.
 *  - Inverse-fill clip : the complement of the path is what gets filled.
 *  - Composed clip stacks (clipPath after clipRect) intersect correctly.
 *  - clipRRect produces a rounded-rect alpha mask (corners are
 *    transparent, centre fully opaque).
 *  - Empty intersection collapses to a no-op draw.
 *  - `save` / `clipPath` / `restore` cycle reverts the clip.
 */
class ClipPathTest {

    private fun canvas(w: Int, h: Int): Pair<SkBitmap, SkCanvas> {
        // Use 8888 sRGB so getPixel returns canonical SkColor values
        // without colorspace conversion tricks.
        val bm = SkBitmap(w, h, SkColorSpace.makeSRGB(), SkColorType.kRGBA_8888)
            .also { it.eraseColor(SK_ColorWHITE) }
        return bm to SkCanvas(bm)
    }

    @Test
    fun `clipPath on circle restricts drawRect to circle interior`() {
        val (bm, c) = canvas(100, 100)
        c.clipPath(SkPath.Circle(50f, 50f, 30f), doAntiAlias = false)
        c.drawRect(SkRect.MakeWH(100f, 100f), SkPaint().apply { color = SK_ColorRED })

        // Centre pixel inside the circle → red.
        val centre = bm.getPixel(50, 50)
        assertEquals(0xFF, SkColorGetA(centre))
        assertTrue(SkColorGetR(centre) > 200) { "centre R=${SkColorGetR(centre)} < 200" }

        // A pixel far outside the circle (corner) → unchanged white bg.
        val corner = bm.getPixel(2, 2)
        assertEquals(0xFFFFFFFF.toInt(), corner)
    }

    @Test
    fun `inverse-winding clipPath fills the complement`() {
        val (bm, c) = canvas(100, 100)
        // Inverse-winding circle clip → drawing fills everything *outside*
        // the circle.
        val invCircle = SkPath.Circle(50f, 50f, 30f).makeFillType(SkPathFillType.kInverseWinding)
        c.clipPath(invCircle, doAntiAlias = false)
        c.drawRect(SkRect.MakeWH(100f, 100f), SkPaint().apply { color = SK_ColorRED })

        // Inside the circle (centre) — unchanged white bg.
        val centre = bm.getPixel(50, 50)
        assertEquals(0xFFFFFFFF.toInt(), centre)

        // Outside the circle (corner) — red.
        val corner = bm.getPixel(2, 2)
        assertEquals(0xFF, SkColorGetA(corner))
        assertTrue(SkColorGetR(corner) > 200)
    }

    @Test
    fun `clipPath composes with clipRect via intersection`() {
        val (bm, c) = canvas(100, 100)
        // First clip to the right half (50..100), then to a circle at the
        // canvas centre. The intersection is the right half of the circle.
        c.clipRect(SkRect.MakeLTRB(50f, 0f, 100f, 100f))
        c.clipPath(SkPath.Circle(50f, 50f, 30f), doAntiAlias = false)
        c.drawRect(SkRect.MakeWH(100f, 100f), SkPaint().apply { color = SK_ColorRED })

        // A pixel inside the circle but in the LEFT half — masked out by
        // the rect clip, must be unchanged white.
        val leftInside = bm.getPixel(30, 50)
        assertEquals(0xFFFFFFFF.toInt(), leftInside)

        // A pixel inside the circle in the RIGHT half — red.
        val rightInside = bm.getPixel(60, 50)
        assertTrue(SkColorGetR(rightInside) > 200)
    }

    @Test
    fun `clipRRect masks corners and fills centre`() {
        val (bm, c) = canvas(100, 100)
        val rr = SkRRect.MakeRectXY(SkRect.MakeWH(100f, 100f), 25f, 25f)
        c.clipRRect(rr, doAntiAlias = false)
        c.drawRect(SkRect.MakeWH(100f, 100f), SkPaint().apply { color = SK_ColorBLACK })

        // Centre pixel — fully inside the rrect, drawn black.
        val centre = bm.getPixel(50, 50)
        assertEquals(0xFF, SkColorGetA(centre))
        assertEquals(0, SkColorGetR(centre))

        // A pixel near a corner — outside the rrect, stays white.
        val tl = bm.getPixel(2, 2)
        assertEquals(0xFFFFFFFF.toInt(), tl)
    }

    @Test
    fun `save - clipPath - restore reverts the clip`() {
        val (bm, c) = canvas(100, 100)
        c.save()
        c.clipPath(SkPath.Circle(50f, 50f, 30f), doAntiAlias = false)
        c.drawRect(SkRect.MakeWH(100f, 100f), SkPaint().apply { color = SK_ColorRED })
        c.restore()

        // After restore, clip is back to the full canvas. Draw blue
        // everywhere — the previously-masked-out corners become blue.
        c.drawRect(SkRect.MakeWH(100f, 100f), SkPaint().apply { color = 0xFF0000FF.toInt() })

        // Corner — was white (masked out red), now blue.
        val corner = bm.getPixel(2, 2)
        assertEquals(0xFF, SkColorGetA(corner))
        assertTrue(SkColorGetB(corner) > 200) { "corner B=${SkColorGetB(corner)}" }
        assertEquals(0, SkColorGetR(corner))
        assertEquals(0, SkColorGetG(corner))
    }

    @Test
    fun `clipPath with non-overlapping path collapses to empty draws`() {
        val (bm, c) = canvas(100, 100)
        // Path entirely outside the canvas — clip becomes empty.
        c.clipPath(SkPath.Circle(-100f, -100f, 10f), doAntiAlias = false)
        c.drawRect(SkRect.MakeWH(100f, 100f), SkPaint().apply { color = SK_ColorRED })

        // Every pixel must remain at the bg colour.
        for (x in 0 until 100 step 20) {
            for (y in 0 until 100 step 20) {
                assertEquals(0xFFFFFFFF.toInt(), bm.getPixel(x, y)) {
                    "($x, $y) leaked through empty clip"
                }
            }
        }
    }
}
