package org.skia.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.foundation.SK_ColorBLACK
import org.skia.foundation.SK_ColorRED
import org.skia.foundation.SK_ColorWHITE
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkClipOp
import org.skia.foundation.SkColorGetA
import org.skia.foundation.SkColorGetB
import org.skia.foundation.SkColorGetG
import org.skia.foundation.SkColorGetR
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkColorType
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkRRect
import org.skia.math.SkRect

/**
 * Unit tests for [SkClipOp.kDifference] semantics on [SkCanvas.clipPath]
 * / [SkCanvas.clipRRect] / [SkCanvas.clipRect] (Phase 7q+).
 *
 * Coverage :
 *  - `clipPath(circle, kDifference)` cuts a hole — drawRect fills the
 *    canvas everywhere outside the circle.
 *  - `clipRRect(rrect, kDifference)` cuts a rounded-rect hole.
 *  - `clipRect(rect, kDifference)` cuts a rectangular hole.
 *  - kDifference composes correctly with a prior clipRect (parent
 *    clip is preserved minus the difference shape).
 *  - Two successive kDifference ops on disjoint shapes punch two
 *    holes (additive cuts).
 */
class ClipPathDifferenceTest {

    private fun canvas(w: Int, h: Int): Pair<SkBitmap, SkCanvas> {
        val bm = SkBitmap(w, h, SkColorSpace.makeSRGB(), SkColorType.kRGBA_8888)
            .also { it.eraseColor(SK_ColorWHITE) }
        return bm to SkCanvas(bm)
    }

    @Test
    fun `clipPath kDifference on circle leaves a hole at the centre`() {
        val (bm, c) = canvas(100, 100)
        c.clipPath(SkPath.Circle(50f, 50f, 30f), SkClipOp.kDifference)
        c.drawRect(SkRect.MakeWH(100f, 100f), SkPaint().apply { color = SK_ColorRED })

        // Centre pixel inside the circle — masked out, stays white.
        val centre = bm.getPixel(50, 50)
        assertEquals(0xFFFFFFFF.toInt(), centre)

        // Far corner — outside the circle, gets red.
        val corner = bm.getPixel(2, 2)
        assertEquals(0xFF, SkColorGetA(corner))
        assertTrue(SkColorGetR(corner) > 200) { "corner R=${SkColorGetR(corner)}" }
    }

    @Test
    fun `clipRRect kDifference cuts a rounded-rect hole`() {
        val (bm, c) = canvas(100, 100)
        val rr = SkRRect.MakeRectXY(SkRect.MakeLTRB(20f, 20f, 80f, 80f), 15f, 15f)
        c.clipRRect(rr, SkClipOp.kDifference)
        c.drawRect(SkRect.MakeWH(100f, 100f), SkPaint().apply { color = SK_ColorBLACK })

        // Inside the rrect — stays white.
        val inside = bm.getPixel(50, 50)
        assertEquals(0xFFFFFFFF.toInt(), inside)

        // Outside the rrect — black.
        val outside = bm.getPixel(2, 2)
        assertEquals(0, SkColorGetR(outside))
    }

    @Test
    fun `clipRect kDifference cuts a rectangular hole`() {
        val (bm, c) = canvas(100, 100)
        c.clipRect(SkRect.MakeLTRB(30f, 30f, 70f, 70f), SkClipOp.kDifference)
        c.drawRect(SkRect.MakeWH(100f, 100f), SkPaint().apply { color = SK_ColorRED })

        // Inside the difference rect — stays white.
        val inside = bm.getPixel(50, 50)
        assertEquals(0xFFFFFFFF.toInt(), inside)

        // Outside (corner) — red.
        val outside = bm.getPixel(2, 2)
        assertTrue(SkColorGetR(outside) > 200)
    }

    @Test
    fun `kDifference composes with a prior clipRect intersection`() {
        // First restrict to a 60×100 vertical strip, then cut a 30×30
        // hole. Outside the strip stays white (parent clip), inside
        // strip + outside hole gets red, inside hole stays white.
        val (bm, c) = canvas(100, 100)
        c.clipRect(SkRect.MakeLTRB(20f, 0f, 80f, 100f))
        c.clipPath(SkPath.Rect(SkRect.MakeLTRB(35f, 35f, 65f, 65f)), SkClipOp.kDifference)
        c.drawRect(SkRect.MakeWH(100f, 100f), SkPaint().apply { color = SK_ColorRED })

        // Outside the parent rect — white.
        val left = bm.getPixel(5, 50)
        assertEquals(0xFFFFFFFF.toInt(), left)

        // Inside the parent rect, outside the hole — red.
        val ring = bm.getPixel(25, 50)
        assertTrue(SkColorGetR(ring) > 200) { "ring R=${SkColorGetR(ring)}" }

        // Inside the hole — white.
        val hole = bm.getPixel(50, 50)
        assertEquals(0xFFFFFFFF.toInt(), hole)
    }

    @Test
    fun `two successive kDifference ops punch two holes`() {
        val (bm, c) = canvas(120, 60)
        c.clipPath(SkPath.Circle(30f, 30f, 15f), SkClipOp.kDifference)
        c.clipPath(SkPath.Circle(90f, 30f, 15f), SkClipOp.kDifference)
        c.drawRect(SkRect.MakeWH(120f, 60f), SkPaint().apply { color = SK_ColorBLACK })

        // Both circle interiors must remain white.
        val hole1 = bm.getPixel(30, 30)
        val hole2 = bm.getPixel(90, 30)
        assertEquals(0xFFFFFFFF.toInt(), hole1) { "hole1 leaked" }
        assertEquals(0xFFFFFFFF.toInt(), hole2) { "hole2 leaked" }

        // The middle (between holes) gets black.
        val mid = bm.getPixel(60, 30)
        assertEquals(0xFF, SkColorGetA(mid))
        assertEquals(0, SkColorGetR(mid))
    }

    @Test
    fun `kDifference inside save - restore reverts the cut`() {
        val (bm, c) = canvas(100, 100)
        c.save()
        c.clipPath(SkPath.Circle(50f, 50f, 30f), SkClipOp.kDifference)
        c.drawRect(SkRect.MakeWH(100f, 100f), SkPaint().apply { color = SK_ColorRED })
        c.restore()

        // After restore, full clip ; draw blue everywhere — the
        // previously-masked-out interior becomes blue.
        c.drawRect(SkRect.MakeWH(100f, 100f), SkPaint().apply { color = 0xFF0000FF.toInt() })

        // The hole — was white from the masked-out red, now blue.
        val hole = bm.getPixel(50, 50)
        assertTrue(SkColorGetB(hole) > 200) { "hole B=${SkColorGetB(hole)}" }
        assertEquals(0, SkColorGetR(hole))
        assertEquals(0, SkColorGetG(hole))
    }
}
