package org.skia.pathops.internal


import org.graphiks.math.SkDPoint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.graphiks.math.SkPoint

/**
 * Unit tests for [SkDCurve], [SkDCurveSweep], and the verb-dispatched
 * [pointAtT] / [slopeAtT] / [intersectRay] helpers (Phase D1.2.b.2.0).
 */
class SkDCurveTest {

    private fun pt(x: Float, y: Float) = SkPoint(fX = x, fY = y)
    private fun dpt(x: Double, y: Double) = SkDPoint(x, y)

    // ─── SkDCurve : storage + accessors ──────────────────────────────

    @Test
    fun `default SkDCurve has unset verb and zero-points`() {
        val c = SkDCurve()
        assertEquals(SkOpSegment.SegVerb.kUnset, c.fVerb)
        for (i in 0..3) {
            assertEquals(0.0, c[i].x); assertEquals(0.0, c[i].y)
        }
    }

    @Test
    fun `index access reads and writes the underlying SkDPoint array`() {
        val c = SkDCurve()
        c[2] = dpt(7.5, -3.25)
        assertEquals(7.5, c[2].x); assertEquals(-3.25, c[2].y)
    }

    @Test
    fun `copyFrom deep-copies points weight and verb`() {
        val src = SkDCurve().apply {
            fVerb = SkOpSegment.SegVerb.kConic
            fWeight = 0.7071
            fPts[0] = dpt(1.0, 2.0); fPts[1] = dpt(3.0, 4.0); fPts[2] = dpt(5.0, 6.0)
        }
        val dst = SkDCurve().copyFrom(src)
        assertEquals(SkOpSegment.SegVerb.kConic, dst.fVerb)
        assertEquals(0.7071, dst.fWeight)
        assertEquals(dpt(1.0, 2.0), dst.fPts[0])
        // Mutating src after the copy must not affect dst.
        src.fPts[0] = dpt(99.0, 99.0)
        assertEquals(dpt(1.0, 2.0), dst.fPts[0])
    }

    @Test
    fun `asLine asQuad asConic asCubic snapshots are independent of the source`() {
        val c = SkDCurve().apply {
            fVerb = SkOpSegment.SegVerb.kCubic
            fPts[0] = dpt(0.0, 0.0); fPts[1] = dpt(1.0, 1.0)
            fPts[2] = dpt(2.0, 1.0); fPts[3] = dpt(3.0, 0.0)
        }
        val cubic = c.asCubic()
        c.fPts[0] = dpt(99.0, 99.0)
        assertEquals(dpt(0.0, 0.0), cubic[0])
    }

    // ─── SkDCurveSweep : line / quad / cubic ─────────────────────────

    @Test
    fun `setCurveHullSweep on a line gives parallel sweep vectors`() {
        val s = SkDCurveSweep().apply {
            fCurve.fVerb = SkOpSegment.SegVerb.kLine
            fCurve[0] = dpt(0.0, 0.0); fCurve[1] = dpt(10.0, 5.0)
        }
        s.setCurveHullSweep(SkOpSegment.SegVerb.kLine)
        assertFalse(s.isCurve())
        assertTrue(s.isOrdered())
        assertEquals(s.fSweep[0].x, s.fSweep[1].x)
        assertEquals(s.fSweep[0].y, s.fSweep[1].y)
        assertEquals(10.0, s.fSweep[0].x); assertEquals(5.0, s.fSweep[0].y)
    }

    @Test
    fun `setCurveHullSweep on a non-degenerate quad sets isCurve true`() {
        val s = SkDCurveSweep().apply {
            fCurve.fVerb = SkOpSegment.SegVerb.kQuad
            fCurve[0] = dpt(0.0, 0.0); fCurve[1] = dpt(5.0, 10.0); fCurve[2] = dpt(10.0, 0.0)
        }
        s.setCurveHullSweep(SkOpSegment.SegVerb.kQuad)
        assertTrue(s.isCurve())
        assertTrue(s.isOrdered())
        // sweep[0] = (5, 10), sweep[1] = (10, 0).
        assertEquals(5.0, s.fSweep[0].x); assertEquals(10.0, s.fSweep[0].y)
        assertEquals(10.0, s.fSweep[1].x); assertEquals(0.0, s.fSweep[1].y)
    }

    @Test
    fun `setCurveHullSweep on a degenerate (collinear) quad sets isCurve false`() {
        // Three collinear points : the sweep cross is zero, fIsCurve = false.
        val s = SkDCurveSweep().apply {
            fCurve.fVerb = SkOpSegment.SegVerb.kQuad
            fCurve[0] = dpt(0.0, 0.0); fCurve[1] = dpt(5.0, 0.0); fCurve[2] = dpt(10.0, 0.0)
        }
        s.setCurveHullSweep(SkOpSegment.SegVerb.kQuad)
        assertFalse(s.isCurve())
    }

    @Test
    fun `setCurveHullSweep on a convex cubic sets isCurve true and ordered true`() {
        // S-shape pulled enough to remain convex (third vector between first two).
        val s = SkDCurveSweep().apply {
            fCurve.fVerb = SkOpSegment.SegVerb.kCubic
            fCurve[0] = dpt(0.0, 0.0)
            fCurve[1] = dpt(1.0, 5.0)
            fCurve[2] = dpt(5.0, 5.0)
            fCurve[3] = dpt(10.0, 0.0)
        }
        s.setCurveHullSweep(SkOpSegment.SegVerb.kCubic)
        assertTrue(s.isCurve())
        assertTrue(s.isOrdered())
    }

    // ─── SkOpSegment.subDivide ───────────────────────────────────────

    @Test
    fun `subDivide on a line writes endpoints and returns false`() {
        val seg = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 5f)), null)
        val edge = SkDCurve()
        val result = seg.subDivide(seg.fHead, seg.fTail, edge)
        assertFalse(result)
        assertEquals(SkOpSegment.SegVerb.kLine, edge.fVerb)
        assertEquals(dpt(0.0, 0.0), edge[0])
        assertEquals(dpt(10.0, 5.0), edge[1])
    }

    @Test
    fun `subDivide on a full quad reuses the original control point and returns false`() {
        val pts = arrayOf(pt(0f, 0f), pt(5f, 10f), pt(10f, 0f))
        val seg = SkOpSegment().also {
            it.init(pts, 1f, null, SkOpSegment.SegVerb.kQuad)
        }
        // Full range (fHead..fTail covers t=0..1) → upstream skips the
        // pinned subDivide and returns false ; the original control is
        // copied over directly.
        val edge = SkDCurve()
        assertFalse(seg.subDivide(seg.fHead, seg.fTail, edge))
        assertEquals(dpt(5.0, 10.0), edge[1])
    }

    @Test
    fun `subDivide on a full cubic copies all 4 points unchanged`() {
        val pts = arrayOf(pt(0f, 0f), pt(0f, 5f), pt(10f, 5f), pt(10f, 0f))
        val seg = SkOpSegment().also {
            it.init(pts, 1f, null, SkOpSegment.SegVerb.kCubic)
        }
        val edge = SkDCurve()
        // Full range → returns false (copies originals, no pinned subDivide).
        assertFalse(seg.subDivide(seg.fHead, seg.fTail, edge))
        assertEquals(dpt(0.0, 0.0), edge[0])
        assertEquals(dpt(0.0, 5.0), edge[1])
        assertEquals(dpt(10.0, 5.0), edge[2])
        assertEquals(dpt(10.0, 0.0), edge[3])
    }

    @Test
    fun `subDivide on a sub-range of a quad pins the middle control via SkDQuad subDivide`() {
        // Sub-range start=t=0 → end=t=0.5 ; the curve at t=0.5 is (5, 5)
        // for a (0,0)-(5,10)-(10,0) quad.
        val pts = arrayOf(pt(0f, 0f), pt(5f, 10f), pt(10f, 0f))
        val seg = SkOpSegment().also {
            it.init(pts, 1f, null, SkOpSegment.SegVerb.kQuad)
        }
        // Insert a span at t=0.5 with the curve's actual point.
        val mid = seg.insert(seg.fHead).also {
            it.fPtT.fT = 0.5
            it.fPtT.fPt = pt(5f, 5f)
        }
        val edge = SkDCurve()
        // Sub-range → upstream calls the per-curve pinned subDivide and
        // returns true.
        assertTrue(seg.subDivide(seg.fHead, mid, edge))
        assertEquals(dpt(0.0, 0.0), edge[0])
        assertEquals(dpt(5.0, 5.0), edge[2])  // sub-range endpoint
        // The newly-pinned middle is *not* the original (5, 10).
        assertNotEquals(dpt(5.0, 10.0), edge[1])
    }

    // ─── Verb-dispatched helpers ─────────────────────────────────────

    @Test
    fun `pointAtT on a line returns the lerp endpoint`() {
        val c = SkDCurve().apply {
            fVerb = SkOpSegment.SegVerb.kLine
            fPts[0] = dpt(0.0, 0.0); fPts[1] = dpt(10.0, 0.0)
        }
        val mid = c.pointAtT(0.5)
        assertEquals(5.0, mid.x); assertEquals(0.0, mid.y)
    }

    @Test
    fun `slopeAtT on a quad reads the dxdy at t`() {
        val c = SkDCurve().apply {
            fVerb = SkOpSegment.SegVerb.kQuad
            fPts[0] = dpt(0.0, 0.0); fPts[1] = dpt(5.0, 10.0); fPts[2] = dpt(10.0, 0.0)
        }
        val v = c.slopeAtT(0.5)
        // Tangent at t=0.5 on a symmetric arch is purely horizontal.
        assertEquals(0.0, v.y, 1e-9)
        assertTrue(v.x > 0.0)
    }
}
