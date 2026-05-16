package org.skia.pathops.internal


import org.graphiks.math.SkDLine
import org.graphiks.math.SkDPoint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for `SkIntersections.intersect/intersectRay/horizontal/
 * vertical(SkDCubic, ...)` plus the SkDCubic helpers `binarySearch`,
 * `searchRoots`, `horizontalIntersect`, `verticalIntersect`,
 * `nearPoint` (Phase D1.1.d.2).
 */
class SkDCubicLineIntersectionTest {

    private fun cubicOf(
        x0: Double, y0: Double,
        x1: Double, y1: Double,
        x2: Double, y2: Double,
        x3: Double, y3: Double,
    ): SkDCubic = SkDCubic(arrayOf(
        SkDPoint(x0, y0), SkDPoint(x1, y1),
        SkDPoint(x2, y2), SkDPoint(x3, y3),
    ))

    private fun lineOf(x0: Double, y0: Double, x1: Double, y1: Double): SkDLine =
        SkDLine(arrayOf(SkDPoint(x0, y0), SkDPoint(x1, y1)))

    /** Symmetric peak cubic : (0,0)-(0,1000)-(100,1000)-(100,0). Peak y=750. */
    private val peakCubic = cubicOf(0.0, 0.0, 0.0, 1000.0, 100.0, 1000.0, 100.0, 0.0)

    // ─── intersect (segment vs segment) ─────────────────────────────

    @Test
    fun `intersect on a peak cubic and horizontal segment crossing it twice gives 2`() {
        val ix = SkIntersections()
        val l = lineOf(0.0, 375.0, 100.0, 375.0)
        val n = ix.intersect(peakCubic, l)
        assertEquals(2, n)
        // Symmetric : sum of cubic t's around 0.5 = 1.
        assertEquals(1.0, ix.t(0, 0) + ix.t(0, 1), 1e-6)
    }

    @Test
    fun `intersect on a peak cubic and tangent line at apex gives 1`() {
        val ix = SkIntersections()
        val l = lineOf(0.0, 750.0, 100.0, 750.0)
        val n = ix.intersect(peakCubic, l)
        assertEquals(1, n)
        assertEquals(0.5, ix.t(0, 0), 1e-6)
    }

    @Test
    fun `intersect on a peak cubic and a line above the peak gives 0`() {
        val ix = SkIntersections()
        val l = lineOf(0.0, 800.0, 100.0, 800.0)
        assertEquals(0, ix.intersect(peakCubic, l))
    }

    @Test
    fun `intersect at cubic endpoint registers a t=0 hit`() {
        val ix = SkIntersections()
        // Line through (0, 0) — endpoint of the cubic.
        val l = lineOf(0.0, 0.0, 50.0, 50.0)
        val n = ix.intersect(peakCubic, l)
        assertTrue(n >= 1) { "expected ≥1 endpoint hit, got $n" }
        assertEquals(0.0, ix.t(0, 0), 1e-9)
    }

    // ─── intersectRay (infinite line) ───────────────────────────────

    @Test
    fun `intersectRay on a cubic and infinite line yields the same t's`() {
        val ix = SkIntersections()
        // Short segment that doesn't span the cubic, but ray extends.
        val l = lineOf(0.0, 375.0, 1.0, 375.0)
        val n = ix.intersectRay(peakCubic, l)
        assertEquals(2, n)
        assertEquals(1.0, ix.t(0, 0) + ix.t(0, 1), 1e-6)
    }

    // ─── horizontal / vertical line crossings ───────────────────────

    @Test
    fun `horizontal returns 2 crossings of a sub-peak line`() {
        val ix = SkIntersections()
        assertEquals(2, ix.horizontal(peakCubic, 0.0, 100.0, 375.0, false))
    }

    @Test
    fun `vertical at midpoint x returns 1 crossing for a monotonic-x cubic`() {
        val ix = SkIntersections()
        val n = ix.vertical(peakCubic, 0.0, 1000.0, 50.0, false)
        assertEquals(1, n)
        assertEquals(0.5, ix.t(0, 0), 1e-6)
    }

    // ─── horizontalIntersect / verticalIntersect on SkDCubic ────────

    @Test
    fun `SkDCubic horizontalIntersect on peak cubic at half-peak finds 2 t's`() {
        val roots = DoubleArray(3)
        val n = peakCubic.horizontalIntersect(375.0, roots)
        assertEquals(2, n)
    }

    @Test
    fun `SkDCubic verticalIntersect on a monotonic-x cubic at midpoint finds 1 t`() {
        val roots = DoubleArray(3)
        val n = peakCubic.verticalIntersect(50.0, roots)
        assertEquals(1, n)
        assertEquals(0.5, roots[0], 1e-6)
    }

    // ─── binarySearch and searchRoots ───────────────────────────────

    @Test
    fun `binarySearch finds the t where cubic y equals target on a monotonic interval`() {
        // On (0, 0.5), cubic y is monotonic from 0 to 750. y=375 corresponds
        // to a unique t in this range.
        val t = peakCubic.binarySearch(0.0, 0.5, 375.0, SkDCubic.SearchAxis.kYAxis)
        assertTrue(t in 0.0..0.5) { "binarySearch returned t=$t outside [0, 0.5]" }
        // Verify : cubic.y(t) ≈ 375.
        assertEquals(375.0, peakCubic.ptAtT(t).y, 1.0)
    }

    @Test
    fun `searchRoots finds 2 t's where cubic y equals 375`() {
        val extremeTs = DoubleArray(6)
        val src = doubleArrayOf(peakCubic[0].y, peakCubic[1].y, peakCubic[2].y, peakCubic[3].y)
        val extrema = SkDCubic.FindExtrema(src, extremeTs)
        val roots = DoubleArray(3)
        val n = peakCubic.searchRoots(extremeTs, extrema, 375.0, SkDCubic.SearchAxis.kYAxis, roots)
        assertEquals(2, n)
    }

    // ─── nearPoint helper on SkDCubic ───────────────────────────────

    @Test
    fun `SkDCubic nearPoint accepts a point near the curve and returns its t`() {
        // Apex is at (50, 750) on the peak cubic.
        val t = peakCubic.nearPoint(SkDPoint(50.0, 750.0), SkDPoint(100.0, 750.0))
        assertEquals(0.5, t, 1e-3)
    }

    @Test
    fun `SkDCubic nearPoint rejects a point far off the curve`() {
        val t = peakCubic.nearPoint(SkDPoint(50.0, -500.0), SkDPoint(100.0, -500.0))
        assertEquals(-1.0, t, 1e-9)
    }

    // ─── SkPoint façade ─────────────────────────────────────────────

    @Test
    fun `cubicLine SkPoint façade accepts SkPoint arrays`() {
        val ix = SkIntersections()
        val a = arrayOf(
            org.graphiks.math.SkPoint(fX = 0f, fY = 0f),
            org.graphiks.math.SkPoint(fX = 0f, fY = 1000f),
            org.graphiks.math.SkPoint(fX = 100f, fY = 1000f),
            org.graphiks.math.SkPoint(fX = 100f, fY = 0f),
        )
        val b = arrayOf(
            org.graphiks.math.SkPoint(fX = 0f, fY = 375f),
            org.graphiks.math.SkPoint(fX = 100f, fY = 375f),
        )
        assertEquals(2, ix.cubicLine(a, b))
    }
}
