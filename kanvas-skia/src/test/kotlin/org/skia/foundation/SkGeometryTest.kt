package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.math.SkPoint
import kotlin.math.abs

/**
 * S7-A verification suite for [SkGeometry] — the public chop helpers
 * extracted from `MandolineGM`'s private inlines. We pin the
 * shared-midpoint layout against hand-computed midpoints (`t = 0.5`
 * collapses every interpolation to plain averages) and exercise the
 * conic ratquad path against a canonical √2/2 quarter-circle conic.
 */
class SkGeometryTest {

    private fun assertPointEquals(expected: SkPoint, actual: SkPoint, tol: Float = 1e-5f) {
        assertTrue(abs(expected.fX - actual.fX) < tol, "x mismatch : exp=${expected.fX} got=${actual.fX}")
        assertTrue(abs(expected.fY - actual.fY) < tol, "y mismatch : exp=${expected.fY} got=${actual.fY}")
    }

    @Test
    fun `chopQuadAt midpoint equals control-point averages`() {
        val p0 = SkPoint(0f, 0f)
        val p1 = SkPoint(2f, 8f)
        val p2 = SkPoint(4f, 0f)
        val pts = arrayOf(p0, p1, p2, SkPoint(0f, 0f), SkPoint(0f, 0f))
        SkGeometry.chopQuadAt(pts, 0.5f)
        // Endpoints unchanged.
        assertPointEquals(p0, pts[0])
        assertPointEquals(p2, pts[4])
        // Inner control points = midpoints of the original legs.
        assertPointEquals(SkPoint(1f, 4f), pts[1])  // (p0 + p1) / 2
        assertPointEquals(SkPoint(3f, 4f), pts[3])  // (p1 + p2) / 2
        // Mid = midpoint of the two new controls = midpoint of original quad.
        assertPointEquals(SkPoint(2f, 4f), pts[2])
    }

    @Test
    fun `chopCubicAt midpoint equals control-point averages`() {
        val p0 = SkPoint(0f, 0f)
        val p1 = SkPoint(2f, 6f)
        val p2 = SkPoint(6f, 6f)
        val p3 = SkPoint(8f, 0f)
        val pts = arrayOf(p0, p1, p2, p3, SkPoint(0f, 0f), SkPoint(0f, 0f), SkPoint(0f, 0f))
        SkGeometry.chopCubicAt(pts, 0.5f)
        assertPointEquals(p0, pts[0])
        assertPointEquals(p3, pts[6])
        // First level (a, b, c).
        val a = SkPoint(1f, 3f)        // (p0 + p1) / 2
        val b = SkPoint(4f, 6f)        // (p1 + p2) / 2
        val c = SkPoint(7f, 3f)        // (p2 + p3) / 2
        // Second level (d, e).
        val d = SkPoint(2.5f, 4.5f)    // (a + b) / 2
        val e = SkPoint(5.5f, 4.5f)    // (b + c) / 2
        // Mid = (d + e) / 2.
        val mid = SkPoint(4f, 4.5f)
        assertPointEquals(a, pts[1])
        assertPointEquals(d, pts[2])
        assertPointEquals(mid, pts[3])
        assertPointEquals(e, pts[4])
        assertPointEquals(c, pts[5])
    }

    @Test
    fun `chopConicAt preserves the on-curve point at t`() {
        // Canonical quarter-circle conic on the unit circle :
        //   start (1, 0), control (1, 1), end (0, 1), weight √2/2.
        // The point at t = 0.5 on this conic lies on the unit circle
        // at angle 45° = (√2/2, √2/2) ≈ (0.7071, 0.7071).
        val p0 = SkPoint(1f, 0f)
        val p1 = SkPoint(1f, 1f)
        val p2 = SkPoint(0f, 1f)
        val w = (kotlin.math.sqrt(2.0) / 2.0).toFloat()
        val pts = arrayOf(p0, p1, p2, SkPoint(0f, 0f), SkPoint(0f, 0f))
        val (wL, wR) = SkGeometry.chopConicAt(pts, w, 0.5f)
        // Endpoints unchanged.
        assertPointEquals(p0, pts[0])
        assertPointEquals(p2, pts[4])
        // The shared midpoint sits on the unit circle at 45°.
        val expectedMid = SkPoint(w, w)
        assertPointEquals(expectedMid, pts[2], tol = 1e-4f)
        // Symmetry : a unit-circle quarter-arc chopped at its midpoint
        // produces two mirror-image conics, so wL == wR.
        assertTrue(abs(wL - wR) < 1e-4f, "expected symmetric weights, got wL=$wL wR=$wR")
        // Each half should still trace a 45° arc — its weight is
        // cos(22.5°) ≈ 0.9239.
        val expectedHalf = kotlin.math.cos(Math.PI / 8.0).toFloat()
        assertTrue(abs(wL - expectedHalf) < 1e-3f, "wL=$wL not ≈ $expectedHalf")
    }

    @Test
    fun `chopConicAt with weight 1 collapses to quad chop`() {
        // A conic with weight 1 is exactly a quadratic — chopping it
        // should match chopQuadAt's midpoint output.
        val p0 = SkPoint(0f, 0f)
        val p1 = SkPoint(2f, 8f)
        val p2 = SkPoint(4f, 0f)
        val conicPts = arrayOf(p0, p1, p2, SkPoint(0f, 0f), SkPoint(0f, 0f))
        SkGeometry.chopConicAt(conicPts, weight = 1f, t = 0.5f)
        val quadPts = arrayOf(p0, p1, p2, SkPoint(0f, 0f), SkPoint(0f, 0f))
        SkGeometry.chopQuadAt(quadPts, 0.5f)
        for (i in 0 until 5) {
            assertPointEquals(quadPts[i], conicPts[i], tol = 1e-4f)
        }
    }

    @Test
    fun `chop helpers reject undersized arrays`() {
        val tooSmall = arrayOf(SkPoint(0f, 0f), SkPoint(1f, 1f), SkPoint(2f, 0f))
        assertThrows(IllegalArgumentException::class.java) {
            SkGeometry.chopQuadAt(tooSmall, 0.5f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SkGeometry.chopCubicAt(tooSmall, 0.5f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SkGeometry.chopConicAt(tooSmall, weight = 0.7f, t = 0.5f)
        }
    }

    @Test
    fun `chopQuadAt at endpoints leaves the curve unchanged`() {
        val p0 = SkPoint(0f, 0f)
        val p1 = SkPoint(2f, 8f)
        val p2 = SkPoint(4f, 0f)
        val pts0 = arrayOf(p0, p1, p2, SkPoint(0f, 0f), SkPoint(0f, 0f))
        SkGeometry.chopQuadAt(pts0, 0f)
        // t=0 → left half collapses to the start point ; right half is
        // the entire original quad.
        assertPointEquals(p0, pts0[0])
        assertPointEquals(p0, pts0[1])
        assertPointEquals(p0, pts0[2])
        assertPointEquals(p1, pts0[3])
        assertPointEquals(p2, pts0[4])

        val pts1 = arrayOf(p0, p1, p2, SkPoint(0f, 0f), SkPoint(0f, 0f))
        SkGeometry.chopQuadAt(pts1, 1f)
        // t=1 → left half is the entire original quad ; right half
        // collapses to the end point.
        assertPointEquals(p0, pts1[0])
        assertPointEquals(p1, pts1[1])
        assertPointEquals(p2, pts1[2])
        assertPointEquals(p2, pts1[3])
        assertPointEquals(p2, pts1[4])

        // Suppress unused warning.
        assertEquals(5, pts1.size)
    }
}
