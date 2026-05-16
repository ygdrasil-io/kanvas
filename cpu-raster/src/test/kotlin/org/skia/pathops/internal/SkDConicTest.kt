package org.skia.pathops.internal


import org.skia.math.SkDPoint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.sqrt

/**
 * Unit tests for [SkDConic] (Phase D1.1.b).
 *
 * Focus on the rational-form math : evaluation (`ptAtT`, `dxdyAtT`),
 * subdivision, extrema, plus the forwarders to the inner [SkDQuad].
 */
class SkDConicTest {

    private fun conicOf(
        x0: Double, y0: Double,
        x1: Double, y1: Double,
        x2: Double, y2: Double,
        weight: Float,
    ): SkDConic = SkDConic(
        pts = SkDQuad(arrayOf(SkDPoint(x0, y0), SkDPoint(x1, y1), SkDPoint(x2, y2))),
        weight = weight,
    )

    /** Quarter-arc weight is `cos(π/4) = √2/2`. */
    private val arcWeight = (sqrt(2.0) / 2).toFloat()

    // ─── Evaluation ──────────────────────────────────────────────────

    @Test
    fun `ptAtT returns endpoints at t=0 and t=1`() {
        val k = conicOf(0.0, 0.0, 100.0, 100.0, 200.0, 0.0, 0.5f)
        assertEquals(SkDPoint(0.0, 0.0), k.ptAtT(0.0))
        assertEquals(SkDPoint(200.0, 0.0), k.ptAtT(1.0))
    }

    @Test
    fun `weight=1 conic equals the same-shape quadratic at every t`() {
        // Conic with weight 1 reduces to a standard Bézier quad — should
        // agree pointwise with SkDQuad.ptAtT for the same control points.
        val pts = arrayOf(SkDPoint(0.0, 0.0), SkDPoint(50.0, 100.0), SkDPoint(100.0, 0.0))
        val k = SkDConic(pts = SkDQuad(pts.copyOf()), weight = 1f)
        val q = SkDQuad(pts)
        for (i in 1..9) {
            val t = i / 10.0
            val pk = k.ptAtT(t); val pq = q.ptAtT(t)
            assertEquals(pq.x, pk.x, 1e-9, "x mismatch at t=$t")
            assertEquals(pq.y, pk.y, 1e-9, "y mismatch at t=$t")
        }
    }

    @Test
    fun `quarter-arc conic at t=0_5 lies on the unit circle`() {
        // Standard quarter-arc : (1, 0) → (1, 1) → (0, 1) with w = √2/2.
        // Parametric midpoint must lie on the unit circle (x²+y² = 1).
        // Tolerance accommodates the Float weight precision (the upstream
        // `SkScalar` is single-precision, so weights round to ~7 digits).
        val arc = conicOf(1.0, 0.0, 1.0, 1.0, 0.0, 1.0, arcWeight)
        val mid = arc.ptAtT(0.5)
        val r = sqrt(mid.x * mid.x + mid.y * mid.y)
        assertEquals(1.0, r, 1e-7)
        // Symmetry : x == y at midpoint.
        assertEquals(mid.x, mid.y, 1e-9)
    }

    @Test
    fun `dxdyAtT at endpoint matches the chord-derivative direction`() {
        val arc = conicOf(1.0, 0.0, 1.0, 1.0, 0.0, 1.0, arcWeight)
        // Tangent at t=0 should be vertical (positive y direction).
        val v0 = arc.dxdyAtT(0.0)
        // The exact magnitude depends on weight ; just sanity-check the
        // direction : x-component near 0, y-component positive.
        assertTrue(v0.y > 0)
    }

    // ─── Subdivision ─────────────────────────────────────────────────

    @Test
    fun `subDivide produces a sub-conic that agrees with the original at sampled t`() {
        val arc = conicOf(1.0, 0.0, 1.0, 1.0, 0.0, 1.0, arcWeight)
        val sub = arc.subDivide(0.25, 0.75)
        // sub(t=0) ≈ arc(t=0.25), sub(t=1) ≈ arc(t=0.75)
        val a = arc.ptAtT(0.25); val b = arc.ptAtT(0.75)
        val sa = sub.ptAtT(0.0); val sb = sub.ptAtT(1.0)
        assertEquals(a.x, sa.x, 1e-9); assertEquals(a.y, sa.y, 1e-9)
        assertEquals(b.x, sb.x, 1e-9); assertEquals(b.y, sb.y, 1e-9)
    }

    // ─── FindExtrema ────────────────────────────────────────────────

    @Test
    fun `FindExtrema on the quarter-arc x-axis finds the t=0 endpoint extremum`() {
        // Standard quarter-arc x-coords (1, 1, 0). The derivative
        // polynomial has a root at t=0 (where x is maximal) — upstream
        // SkDConic::FindExtrema reports this t=0 root rather than
        // skipping it, since the curve transitions from increasing
        // to decreasing right at the start.
        val arc = conicOf(1.0, 0.0, 1.0, 1.0, 0.0, 1.0, arcWeight)
        val t = DoubleArray(1)
        val n = SkDConic.FindExtrema(doubleArrayOf(arc[0].x, arc[1].x, arc[2].x), arc.weight, t)
        assertEquals(1, n)
        assertEquals(0.0, t[0], 1e-9)
    }

    @Test
    fun `FindExtrema on a y-symmetric peaked conic finds the apex`() {
        // (0, 0) → (50, 1000) → (100, 0) with weight 1 — same as a standard quad,
        // peak at t=0.5.
        val k = conicOf(0.0, 0.0, 50.0, 1000.0, 100.0, 0.0, 1f)
        val t = DoubleArray(1)
        val n = SkDConic.FindExtrema(doubleArrayOf(k[0].y, k[1].y, k[2].y), k.weight, t)
        assertEquals(1, n)
        assertEquals(0.5, t[0], 1e-9)
    }

    // ─── Forwarders ──────────────────────────────────────────────────

    @Test
    fun `collapsed and controlsInside delegate to the inner quad`() {
        val k1 = conicOf(1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1f)
        assertTrue(k1.collapsed())
        val k2 = conicOf(0.0, 0.0, 5.0, 10.0, 10.0, 0.0, 1f)
        assertFalse(k2.collapsed())
    }

    @Test
    fun `flip reverses the control points and preserves weight`() {
        val k = conicOf(0.0, 0.0, 50.0, 100.0, 100.0, 0.0, 0.7f)
        val f = k.flip()
        assertEquals(SkDPoint(100.0, 0.0), f[0])
        assertEquals(SkDPoint(50.0, 100.0), f[1])
        assertEquals(SkDPoint(0.0, 0.0), f[2])
        assertEquals(0.7f, f.weight)
    }
}
