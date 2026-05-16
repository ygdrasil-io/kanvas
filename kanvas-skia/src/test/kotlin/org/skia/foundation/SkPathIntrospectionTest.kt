package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.graphiks.math.SkRect
import org.graphiks.math.SkVector

/**
 * Slice 3.1 — read-only introspection on `SkPath` (Skia parity).
 *
 * Covers `isLastContourClosed`, `isFinite`, `countPoints`, `countVerbs`,
 * `getSegmentMasks`, `getLastPt`, `isLine`, `isRect`, `isOval`, `isRRect`.
 * The detectors pattern-match on the canonical verb streams produced by
 * `SkPathBuilder.addRect` / `addOval` / `addRRect` (Phase 2 conic form).
 */
class SkPathIntrospectionTest {

    // --- isLastContourClosed --------------------------------------------

    @Test
    fun `empty path is not lastContourClosed`() {
        assertFalse(SkPathBuilder().detach().isLastContourClosed())
    }

    @Test
    fun `path ending with close is lastContourClosed`() {
        val p = SkPathBuilder().moveTo(0f, 0f).lineTo(1f, 1f).close().detach()
        assertTrue(p.isLastContourClosed())
    }

    @Test
    fun `path ending with line is not lastContourClosed`() {
        val p = SkPathBuilder().moveTo(0f, 0f).lineTo(1f, 1f).detach()
        assertFalse(p.isLastContourClosed())
    }

    // --- isFinite -------------------------------------------------------

    @Test
    fun `empty path isFinite`() {
        assertTrue(SkPathBuilder().detach().isFinite())
    }

    @Test
    fun `path with finite coords isFinite`() {
        val p = SkPathBuilder().moveTo(0f, 0f).lineTo(1e6f, -1e6f).detach()
        assertTrue(p.isFinite())
    }

    @Test
    fun `path with NaN coord is not isFinite`() {
        val p = SkPathBuilder().moveTo(0f, Float.NaN).detach()
        assertFalse(p.isFinite())
    }

    @Test
    fun `path with infinite coord is not isFinite`() {
        val p = SkPathBuilder().moveTo(0f, 0f).lineTo(Float.POSITIVE_INFINITY, 0f).detach()
        assertFalse(p.isFinite())
    }

    // --- countPoints / countVerbs ---------------------------------------

    @Test
    fun `empty path has 0 points and 0 verbs`() {
        val p = SkPathBuilder().detach()
        assertEquals(0, p.countPoints())
        assertEquals(0, p.countVerbs())
    }

    @Test
    fun `countPoints sums the new-points-per-verb`() {
        // move(1) + line(1) + quad(2) + conic(2) + cubic(3) + close(0) = 9 points.
        val p = SkPathBuilder()
            .moveTo(0f, 0f)
            .lineTo(1f, 1f)
            .quadTo(2f, 2f, 3f, 3f)
            .conicTo(4f, 4f, 5f, 5f, 0.5f)
            .cubicTo(6f, 6f, 7f, 7f, 8f, 8f)
            .close()
            .detach()
        assertEquals(9, p.countPoints())
        assertEquals(6, p.countVerbs())
    }

    // --- getSegmentMasks ------------------------------------------------

    @Test
    fun `empty path has zero segment mask`() {
        assertEquals(0, SkPathBuilder().detach().getSegmentMasks())
    }

    @Test
    fun `move-and-close-only path has zero segment mask`() {
        val p = SkPathBuilder().moveTo(0f, 0f).close().detach()
        assertEquals(0, p.getSegmentMasks())
    }

    @Test
    fun `line sets bit 1`() {
        val p = SkPathBuilder().moveTo(0f, 0f).lineTo(1f, 1f).detach()
        assertEquals(1, p.getSegmentMasks())
    }

    @Test
    fun `quad sets bit 2 and conic sets bit 4 and cubic sets bit 8`() {
        val p = SkPathBuilder()
            .moveTo(0f, 0f)
            .quadTo(1f, 0f, 1f, 1f)
            .conicTo(2f, 0f, 2f, 1f, 0.5f)
            .cubicTo(3f, 0f, 3f, 1f, 4f, 1f)
            .detach()
        assertEquals(2 or 4 or 8, p.getSegmentMasks())
    }

    @Test
    fun `addOval (4 conics) sets only the conic bit`() {
        val p = SkPathBuilder().addOval(SkRect.MakeLTRB(0f, 0f, 10f, 10f)).detach()
        assertEquals(4, p.getSegmentMasks())
    }

    @Test
    fun `addRRect (4 lines + 4 conics) sets line + conic bits`() {
        val rrect = SkRRect.MakeRectXY(SkRect.MakeLTRB(0f, 0f, 10f, 10f), 2f, 2f)
        val p = SkPathBuilder().addRRect(rrect).detach()
        assertEquals(1 or 4, p.getSegmentMasks())
    }

    // --- getLastPt -------------------------------------------------------

    @Test
    fun `empty path has null lastPt`() {
        assertNull(SkPathBuilder().detach().getLastPt())
    }

    @Test
    fun `lastPt returns the last appended point`() {
        val p = SkPathBuilder().moveTo(10f, 20f).lineTo(30f, 40f).detach()
        val lp = p.getLastPt()!!
        assertEquals(30f, lp.fX)
        assertEquals(40f, lp.fY)
    }

    @Test
    fun `lastPt after close returns the close-target (rewound to contour start)`() {
        // close() moves the pen back to (10, 20) but does not add a new point —
        // so the last *coord* in the array is still the lineTo end.
        val p = SkPathBuilder().moveTo(10f, 20f).lineTo(30f, 40f).close().detach()
        val lp = p.getLastPt()!!
        assertEquals(30f, lp.fX)
        assertEquals(40f, lp.fY)
    }

    // --- isLine ----------------------------------------------------------

    @Test
    fun `empty path is not a line`() {
        assertNull(SkPathBuilder().detach().isLine())
    }

    @Test
    fun `move-only path is not a line`() {
        assertNull(SkPathBuilder().moveTo(0f, 0f).detach().isLine())
    }

    @Test
    fun `move + line is a line`() {
        val p = SkPathBuilder().moveTo(1f, 2f).lineTo(3f, 4f).detach()
        val pair = p.isLine()!!
        assertEquals(1f, pair.first.fX); assertEquals(2f, pair.first.fY)
        assertEquals(3f, pair.second.fX); assertEquals(4f, pair.second.fY)
    }

    @Test
    fun `move + line + close is not a single-line path`() {
        // Skia is strict here: isLine requires exactly 2 verbs (kMove + kLine).
        val p = SkPathBuilder().moveTo(0f, 0f).lineTo(1f, 1f).close().detach()
        assertNull(p.isLine())
    }

    @Test
    fun `move + 2 lines is not a line`() {
        val p = SkPathBuilder().moveTo(0f, 0f).lineTo(1f, 1f).lineTo(2f, 2f).detach()
        assertNull(p.isLine())
    }

    // --- isRect ----------------------------------------------------------

    @Test
    fun `addRect output is detected as rect`() {
        val src = SkRect.MakeLTRB(5f, 7f, 15f, 17f)
        val p = SkPathBuilder().addRect(src).detach()
        val r = p.isRect()!!
        assertEquals(5f, r.left); assertEquals(7f, r.top)
        assertEquals(15f, r.right); assertEquals(17f, r.bottom)
    }

    @Test
    fun `addRect CCW also detected as rect`() {
        val src = SkRect.MakeLTRB(0f, 0f, 10f, 10f)
        val p = SkPathBuilder().addRect(src, SkPathDirection.kCCW).detach()
        assertNotNull(p.isRect())
    }

    @Test
    fun `path missing kClose is not a rect`() {
        val p = SkPathBuilder()
            .moveTo(0f, 0f).lineTo(10f, 0f).lineTo(10f, 10f).lineTo(0f, 10f)
            .detach()
        assertNull(p.isRect())
    }

    @Test
    fun `non-axis-aligned 4-point closed contour is not a rect`() {
        val p = SkPathBuilder()
            .moveTo(0f, 0f).lineTo(10f, 1f).lineTo(11f, 11f).lineTo(1f, 10f).close()
            .detach()
        assertNull(p.isRect())
    }

    @Test
    fun `oval is not a rect`() {
        val p = SkPathBuilder().addOval(SkRect.MakeLTRB(0f, 0f, 10f, 10f)).detach()
        assertNull(p.isRect())
    }

    // --- isOval ----------------------------------------------------------

    @Test
    fun `addOval output is detected as oval`() {
        val src = SkRect.MakeLTRB(0f, 0f, 100f, 50f)
        val p = SkPathBuilder().addOval(src).detach()
        val r = p.isOval()!!
        assertEquals(0f, r.left, 1e-4f); assertEquals(0f, r.top, 1e-4f)
        assertEquals(100f, r.right, 1e-4f); assertEquals(50f, r.bottom, 1e-4f)
    }

    @Test
    fun `addOval CCW also detected as oval`() {
        val src = SkRect.MakeLTRB(0f, 0f, 100f, 100f)
        val p = SkPathBuilder().addOval(src, SkPathDirection.kCCW).detach()
        assertNotNull(p.isOval())
    }

    @Test
    fun `addCircle (delegates to addOval) is detected as oval`() {
        val p = SkPathBuilder().addCircle(50f, 50f, 25f).detach()
        val r = p.isOval()!!
        assertEquals(25f, r.left, 1e-4f); assertEquals(75f, r.right, 1e-4f)
    }

    @Test
    fun `rect is not an oval`() {
        val p = SkPathBuilder().addRect(SkRect.MakeLTRB(0f, 0f, 10f, 10f)).detach()
        assertNull(p.isOval())
    }

    @Test
    fun `arc (move + 1 conic) is not an oval`() {
        val p = SkPathBuilder()
            .addArc(SkRect.MakeLTRB(0f, 0f, 100f, 100f), 0f, 90f)
            .detach()
        assertNull(p.isOval())
    }

    // --- isRRect ---------------------------------------------------------

    @Test
    fun `addRRect output is detected as rrect`() {
        val rect = SkRect.MakeLTRB(0f, 0f, 100f, 60f)
        val src = SkRRect.MakeRectXY(rect, 10f, 20f)
        val p = SkPathBuilder().addRRect(src).detach()
        val rr = p.isRRect()!!
        assertEquals(0f, rr.rect().left, 1e-4f)
        assertEquals(100f, rr.rect().right, 1e-4f)
        // All four corners share the (10, 20) radii.
        for (corner in SkRRect.Corner.entries) {
            val r = rr.radii(corner)
            assertEquals(10f, r.fX, 1e-3f)
            assertEquals(20f, r.fY, 1e-3f)
        }
    }

    @Test
    fun `complex rrect with per-corner radii is detected and radii preserved`() {
        val rect = SkRect.MakeLTRB(0f, 0f, 100f, 100f)
        val radii = arrayOf(
            SkVector(5f,  5f),    // TL
            SkVector(10f, 10f),   // TR
            SkVector(15f, 15f),   // BR
            SkVector(20f, 20f),   // BL
        )
        val src = SkRRect.MakeRectRadii(rect, radii)
        val p = SkPathBuilder().addRRect(src).detach()
        val rr = p.isRRect()!!
        assertEquals(5f,  rr.radii(SkRRect.Corner.kUpperLeft_Corner).fX,  1e-3f)
        assertEquals(10f, rr.radii(SkRRect.Corner.kUpperRight_Corner).fX, 1e-3f)
        assertEquals(15f, rr.radii(SkRRect.Corner.kLowerRight_Corner).fX, 1e-3f)
        assertEquals(20f, rr.radii(SkRRect.Corner.kLowerLeft_Corner).fX,  1e-3f)
    }

    @Test
    fun `pure-rect rrect (collapsed to addRect) is not detected as rrect`() {
        // The collapsed path has 5 verbs (move + 3 line + close), not 10.
        // Callers should fall back to isRect for that.
        val src = SkRRect.MakeRect(SkRect.MakeLTRB(0f, 0f, 10f, 10f))
        val p = SkPathBuilder().addRRect(src).detach()
        assertNull(p.isRRect())
        assertNotNull(p.isRect())
    }

    @Test
    fun `pure-oval rrect (collapsed to addOval) is not detected as rrect`() {
        val src = SkRRect.MakeOval(SkRect.MakeLTRB(0f, 0f, 10f, 10f))
        val p = SkPathBuilder().addRRect(src).detach()
        assertNull(p.isRRect())
        assertNotNull(p.isOval())
    }

    @Test
    fun `oval is not an rrect`() {
        val p = SkPathBuilder().addOval(SkRect.MakeLTRB(0f, 0f, 10f, 10f)).detach()
        assertNull(p.isRRect())
    }
}
