package org.skia.pathops.internal

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for [SkDLine] (Phase D1.1.a).
 *
 * Coverage : `ptAtT` evaluation at endpoints + interior, `exactPoint`
 * endpoint detection, `nearPoint` ULPs-tolerant projection, `nearRay`
 * for the unbounded variant, and the H/V convenience overloads.
 */
class SkDLineTest {

    private fun lineOf(x0: Double, y0: Double, x1: Double, y1: Double): SkDLine =
        SkDLine(arrayOf(SkDPoint(x0, y0), SkDPoint(x1, y1)))

    // ─── ptAtT ───────────────────────────────────────────────────────

    @Test
    fun `ptAtT returns endpoint for t=0 and t=1 exactly`() {
        val line = lineOf(0.0, 0.0, 10.0, 20.0)
        assertEquals(SkDPoint(0.0, 0.0), line.ptAtT(0.0))
        assertEquals(SkDPoint(10.0, 20.0), line.ptAtT(1.0))
    }

    @Test
    fun `ptAtT linearly interpolates at midpoint and quartiles`() {
        val line = lineOf(0.0, 0.0, 8.0, 16.0)
        assertEquals(SkDPoint(4.0, 8.0), line.ptAtT(0.5))
        assertEquals(SkDPoint(2.0, 4.0), line.ptAtT(0.25))
        assertEquals(SkDPoint(6.0, 12.0), line.ptAtT(0.75))
    }

    @Test
    fun `ptAtT extrapolates outside the unit interval`() {
        val line = lineOf(0.0, 0.0, 1.0, 1.0)
        assertEquals(SkDPoint(2.0, 2.0), line.ptAtT(2.0))
        assertEquals(SkDPoint(-1.0, -1.0), line.ptAtT(-1.0))
    }

    // ─── exactPoint ──────────────────────────────────────────────────

    @Test
    fun `exactPoint returns 0 or 1 only on bit-exact endpoints`() {
        val line = lineOf(0.0, 0.0, 10.0, 20.0)
        assertEquals(0.0, line.exactPoint(SkDPoint(0.0, 0.0)))
        assertEquals(1.0, line.exactPoint(SkDPoint(10.0, 20.0)))
        // A few ULPs off → -1 (1e-13 > ULP(20.0) ≈ 3.55e-15)
        assertEquals(-1.0, line.exactPoint(SkDPoint(10.0, 20.0 + 1e-13)))
        // Interior point → -1
        assertEquals(-1.0, line.exactPoint(SkDPoint(5.0, 10.0)))
    }

    // ─── nearPoint ───────────────────────────────────────────────────

    @Test
    fun `nearPoint finds the right t for an interior collinear point`() {
        val line = lineOf(0.0, 0.0, 10.0, 0.0)
        val t = line.nearPoint(SkDPoint(5.0, 0.0))
        assertEquals(0.5, t, 1e-12)
    }

    @Test
    fun `nearPoint returns -1 for points outside the segment range`() {
        val line = lineOf(0.0, 0.0, 10.0, 0.0)
        assertEquals(-1.0, line.nearPoint(SkDPoint(15.0, 0.0)))
        assertEquals(-1.0, line.nearPoint(SkDPoint(-5.0, 0.0)))
    }

    @Test
    fun `nearPoint returns -1 for points far off the line`() {
        val line = lineOf(0.0, 0.0, 10.0, 0.0)
        assertEquals(-1.0, line.nearPoint(SkDPoint(5.0, 5.0)))
    }

    @Test
    fun `nearPoint accepts a point a tiny epsilon off a diagonal line`() {
        // For a diagonal line both axes have non-degenerate range, so the
        // per-axis AlmostBetweenUlps allows ULP-scale slack at magnitude 10
        // (rather than requiring denormalized-zero on a horizontal segment).
        val line = lineOf(0.0, 0.0, 10.0, 10.0)
        val t = line.nearPoint(SkDPoint(5.0 + 1e-7, 5.0))
        assertTrue(t >= 0) { "expected near-line acceptance, got t=$t" }
    }

    @Test
    fun `nearPoint sets the unequal flag when the projection differs at single precision`() {
        val line = lineOf(0.0, 0.0, 10.0, 10.0)
        val flag = booleanArrayOf(false)
        // 1e-5 perpendicular slop : within ULPs tolerance at magnitude 10
        // (passes nearPoint), but `(10 + 1e-5).toFloat() != 10.0f` → flag set.
        val t = line.nearPoint(SkDPoint(5.0 + 1e-5, 5.0), flag)
        assertTrue(t >= 0) { "nearPoint should accept the offset, got t=$t" }
        assertTrue(flag[0]) { "off-line distance should mark unequal=true" }
    }

    // ─── nearRay ─────────────────────────────────────────────────────

    @Test
    fun `nearRay accepts points outside the segment range`() {
        val line = lineOf(0.0, 0.0, 10.0, 0.0)
        // Off the segment but on the infinite ray.
        assertTrue(line.nearRay(SkDPoint(15.0, 0.0)))
        assertTrue(line.nearRay(SkDPoint(-5.0, 0.0)))
    }

    @Test
    fun `nearRay rejects points off the line`() {
        val line = lineOf(0.0, 0.0, 10.0, 0.0)
        assertFalse(line.nearRay(SkDPoint(5.0, 100.0)))
    }

    // ─── H/V convenience overloads ──────────────────────────────────

    @Test
    fun `ExactPointH returns 0 or 1 on horizontal endpoints`() {
        assertEquals(0.0, SkDLine.ExactPointH(SkDPoint(0.0, 5.0), 0.0, 10.0, 5.0))
        assertEquals(1.0, SkDLine.ExactPointH(SkDPoint(10.0, 5.0), 0.0, 10.0, 5.0))
        assertEquals(-1.0, SkDLine.ExactPointH(SkDPoint(5.0, 5.0), 0.0, 10.0, 5.0))
        // Wrong y → -1
        assertEquals(-1.0, SkDLine.ExactPointH(SkDPoint(0.0, 6.0), 0.0, 10.0, 5.0))
    }

    @Test
    fun `NearPointH finds the parametric t on a horizontal segment`() {
        val t = SkDLine.NearPointH(SkDPoint(2.5, 5.0), 0.0, 10.0, 5.0)
        assertEquals(0.25, t, 1e-12)
    }

    @Test
    fun `ExactPointV mirrors ExactPointH on the vertical axis`() {
        assertEquals(0.0, SkDLine.ExactPointV(SkDPoint(5.0, 0.0), 0.0, 10.0, 5.0))
        assertEquals(1.0, SkDLine.ExactPointV(SkDPoint(5.0, 10.0), 0.0, 10.0, 5.0))
        assertEquals(-1.0, SkDLine.ExactPointV(SkDPoint(5.0, 5.0), 0.0, 10.0, 5.0))
    }

    @Test
    fun `NearPointV finds the parametric t on a vertical segment`() {
        val t = SkDLine.NearPointV(SkDPoint(5.0, 7.5), 0.0, 10.0, 5.0)
        assertEquals(0.75, t, 1e-12)
    }
}
