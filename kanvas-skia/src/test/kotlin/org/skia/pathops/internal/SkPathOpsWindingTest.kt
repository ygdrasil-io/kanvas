package org.skia.pathops.internal

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.math.SkPoint
import org.skia.math.SkRect

/**
 * Unit tests for the foundation pieces of the ray-tracing winding
 * suite (Phase D1.2.h.5.5).
 */
class SkPathOpsWindingTest {

    private fun pt(x: Float, y: Float) = SkPoint(fX = x, fY = y)

    // ─── Axis-projection helpers ──────────────────────────────────

    @Test
    fun `xy_index returns 0 for X-aligned dirs and 1 for Y-aligned`() {
        assertEquals(0, xy_index(SkOpRayDir.kLeft))
        assertEquals(1, xy_index(SkOpRayDir.kTop))
        assertEquals(0, xy_index(SkOpRayDir.kRight))
        assertEquals(1, xy_index(SkOpRayDir.kBottom))
    }

    @Test
    fun `pt_xy and pt_yx pick the right component per dir`() {
        val p = pt(3f, 7f)
        assertEquals(3f, pt_xy(p, SkOpRayDir.kLeft))
        assertEquals(3f, pt_xy(p, SkOpRayDir.kRight))
        assertEquals(7f, pt_xy(p, SkOpRayDir.kTop))
        assertEquals(7f, pt_xy(p, SkOpRayDir.kBottom))
        assertEquals(7f, pt_yx(p, SkOpRayDir.kLeft))
        assertEquals(3f, pt_yx(p, SkOpRayDir.kTop))
    }

    @Test
    fun `pt_dxdy and pt_dydx pick the right axis of an SkDVector`() {
        val v = SkDVector(2.0, 5.0)
        assertEquals(2.0, pt_dxdy(v, SkOpRayDir.kLeft))
        assertEquals(5.0, pt_dxdy(v, SkOpRayDir.kTop))
        assertEquals(5.0, pt_dydx(v, SkOpRayDir.kLeft))
        assertEquals(2.0, pt_dydx(v, SkOpRayDir.kTop))
    }

    @Test
    fun `rect_side returns the correct face per dir`() {
        val r = SkRect.MakeLTRB(1f, 2f, 3f, 4f)
        assertEquals(1f, rect_side(r, SkOpRayDir.kLeft))
        assertEquals(2f, rect_side(r, SkOpRayDir.kTop))
        assertEquals(3f, rect_side(r, SkOpRayDir.kRight))
        assertEquals(4f, rect_side(r, SkOpRayDir.kBottom))
    }

    @Test
    fun `less_than is true for kLeft-kTop and false for kRight-kBottom`() {
        assertTrue(less_than(SkOpRayDir.kLeft))
        assertTrue(less_than(SkOpRayDir.kTop))
        assertFalse(less_than(SkOpRayDir.kRight))
        assertFalse(less_than(SkOpRayDir.kBottom))
    }

    @Test
    fun `sideways_overlap rejects out-of-range and accepts in-range`() {
        val r = SkRect.MakeLTRB(0f, 10f, 100f, 50f)
        // For kLeft (X-aligned), the perpendicular axis is Y.
        // pt's Y must fall within [r.top, r.bottom] = [10, 50].
        assertTrue(sideways_overlap(r, pt(0f, 30f), SkOpRayDir.kLeft))
        assertFalse(sideways_overlap(r, pt(0f, 5f), SkOpRayDir.kLeft))
    }

    // ─── ccw_dxdy ─────────────────────────────────────────────────

    @Test
    fun `ccw_dxdy returns true when slope is CCW relative to ray`() {
        // For kLeft (dir=0) : leftBottom = ((0+1) & 2) != 0 = false.
        // vPartPos = pt_dydx(v, kLeft) > 0 = v.y > 0.
        // Returns vPartPos == leftBottom = (v.y > 0) == false → v.y <= 0.
        assertTrue(ccw_dxdy(SkDVector(1.0, -1.0), SkOpRayDir.kLeft))
        assertFalse(ccw_dxdy(SkDVector(1.0, 1.0), SkOpRayDir.kLeft))
    }

    // ─── get_t_guess ──────────────────────────────────────────────

    @Test
    fun `get_t_guess returns 05 on tTry 0`() {
        val dirOff = IntArray(1)
        assertEquals(0.5, get_t_guess(0, dirOff), 1e-12)
        assertEquals(0, dirOff[0])
    }

    @Test
    fun `get_t_guess generates bisecting t-values for higher tTry`() {
        val dirOff = IntArray(1)
        // tTry = 1 → 0.5 with dirOffset = 1 (low bit).
        assertEquals(0.5, get_t_guess(1, dirOff), 1e-12)
        assertEquals(1, dirOff[0])
        // tTry = 2 / 3 → quarter-bisects.
        val t2 = get_t_guess(2, dirOff)
        assertTrue(t2 in 0.0..1.0)
        val t3 = get_t_guess(3, dirOff)
        assertTrue(t3 in 0.0..1.0)
    }

    // ─── SkOpRayHit ───────────────────────────────────────────────

    @Test
    fun `SkOpRayHit makeTestBase fills fields and picks dir from slope`() {
        val seg = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        val hit = SkOpRayHit()
        // The line is horizontal — slope.x > slope.y in absolute → kTop.
        val dir = hit.makeTestBase(seg.fHead, 0.5)
        assertEquals(SkOpRayDir.kTop, dir)
        assertEquals(0.5, hit.fT, 1e-12)
        assertEquals(5f, hit.fPt.fX, 1e-4f)
        assertEquals(0f, hit.fPt.fY, 1e-4f)
        assertSame(seg.fHead, hit.fSpan)
        assertTrue(hit.fValid)
    }

    @Test
    fun `SkOpRayHit makeTestBase returns kLeft for vertical line`() {
        val seg = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(0f, 10f)), null)
        val hit = SkOpRayHit()
        val dir = hit.makeTestBase(seg.fHead, 0.5)
        assertEquals(SkOpRayDir.kLeft, dir)
    }

    // ─── Hit comparators ──────────────────────────────────────────

    @Test
    fun `hit_compare_x sorts ascending by fPt_fX`() {
        val a = SkOpRayHit().apply { fPt = pt(1f, 0f) }
        val b = SkOpRayHit().apply { fPt = pt(2f, 0f) }
        assertTrue(hit_compare_x.compare(a, b) < 0)
        assertTrue(reverse_hit_compare_x.compare(a, b) > 0)
    }

    @Test
    fun `hit_compare_y sorts ascending by fPt_fY`() {
        val a = SkOpRayHit().apply { fPt = pt(0f, 5f) }
        val b = SkOpRayHit().apply { fPt = pt(0f, 7f) }
        assertTrue(hit_compare_y.compare(a, b) < 0)
        assertTrue(reverse_hit_compare_y.compare(a, b) > 0)
    }
}
