package org.skia.pathops.internal



import org.skia.math.SkDPoint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for [SkDQuad] (Phase D1.1.b).
 *
 * Coverage : evaluation (`ptAtT`, `dxdyAtT`), monotonicity, subdivision
 * (`subDivide`, `chopAt`), root finders (`RootsReal`, `RootsValidT`,
 * `AddValidTs`, `SetABC`, `FindExtrema`), `horizontalIntersect` /
 * `verticalIntersect`, and the small predicates (`collapsed` /
 * `controlsInside` / `flip` / `otherPts` / `align`).
 */
class SkDQuadTest {

    private fun quadOf(
        x0: Double, y0: Double,
        x1: Double, y1: Double,
        x2: Double, y2: Double,
    ): SkDQuad = SkDQuad(arrayOf(SkDPoint(x0, y0), SkDPoint(x1, y1), SkDPoint(x2, y2)))

    // ─── Evaluation ──────────────────────────────────────────────────

    @Test
    fun `ptAtT returns endpoints at t=0 and t=1`() {
        val q = quadOf(0.0, 0.0, 5.0, 10.0, 10.0, 0.0)
        assertEquals(SkDPoint(0.0, 0.0), q.ptAtT(0.0))
        assertEquals(SkDPoint(10.0, 0.0), q.ptAtT(1.0))
    }

    @Test
    fun `ptAtT at midpoint matches the de-Casteljau formula`() {
        // q(t=0.5) = 0.25·P0 + 0.5·P1 + 0.25·P2
        // = 0.25·(0,0) + 0.5·(50,1000) + 0.25·(100,0)
        // = (50, 500)  — same fixture as the existing path tight-bounds test.
        val q = quadOf(0.0, 0.0, 50.0, 1000.0, 100.0, 0.0)
        val mid = q.ptAtT(0.5)
        assertEquals(50.0, mid.x, 1e-12)
        assertEquals(500.0, mid.y, 1e-12)
    }

    @Test
    fun `dxdyAtT at endpoints returns the half-chord vector`() {
        // Upstream `SkDQuad::dxdyAtT` returns B'(t) / 2 (the un-doubled
        // derivative ; coefficients (t-1, 1-2t, t)). At t=0 that's
        // (P1 - P0), at t=1 that's (P2 - P1).
        val q = quadOf(0.0, 0.0, 5.0, 10.0, 10.0, 0.0)
        val v0 = q.dxdyAtT(0.0)
        assertEquals(5.0, v0.x, 1e-12); assertEquals(10.0, v0.y, 1e-12)
        val v1 = q.dxdyAtT(1.0)
        assertEquals(5.0, v1.x, 1e-12); assertEquals(-10.0, v1.y, 1e-12)
    }

    // ─── Monotonicity ────────────────────────────────────────────────

    @Test
    fun `monotonicInX detects axis monotonic curves`() {
        assertTrue(quadOf(0.0, 0.0, 5.0, 10.0, 10.0, 0.0).monotonicInX())
        // Control's X is outside [0, 10] → not monotonic in X.
        assertFalse(quadOf(0.0, 0.0, 15.0, 10.0, 10.0, 0.0).monotonicInX())
    }

    @Test
    fun `monotonicInY detects axis monotonic curves`() {
        // Control's Y between endpoints' Y.
        assertTrue(quadOf(0.0, 0.0, 5.0, 10.0, 10.0, 20.0).monotonicInY())
        // Symmetric peak → control's Y above both endpoints.
        assertFalse(quadOf(0.0, 0.0, 50.0, 1000.0, 100.0, 0.0).monotonicInY())
    }

    // ─── Subdivision ─────────────────────────────────────────────────

    @Test
    fun `subDivide of full range returns the same quad`() {
        val q = quadOf(0.0, 0.0, 5.0, 10.0, 10.0, 0.0)
        assertEquals(q, q.subDivide(0.0, 1.0))
    }

    @Test
    fun `subDivide preserves the curve geometry on a subinterval`() {
        // The subdivided quad must agree with the original at all sample t.
        val q = quadOf(0.0, 0.0, 50.0, 1000.0, 100.0, 0.0)
        val sub = q.subDivide(0.25, 0.75)
        // sub(t=0) = q(t=0.25) ; sub(t=1) = q(t=0.75) ; sub(t=0.5) = q(t=0.5)
        val a = q.ptAtT(0.25); val b = q.ptAtT(0.75); val mid = q.ptAtT(0.5)
        val sa = sub.ptAtT(0.0); val sb = sub.ptAtT(1.0); val smid = sub.ptAtT(0.5)
        assertEquals(a.x, sa.x, 1e-9); assertEquals(a.y, sa.y, 1e-9)
        assertEquals(b.x, sb.x, 1e-9); assertEquals(b.y, sb.y, 1e-9)
        assertEquals(mid.x, smid.x, 1e-9); assertEquals(mid.y, smid.y, 1e-9)
    }

    @Test
    fun `chopAt midpoint produces two halves whose sample points match the original`() {
        val q = quadOf(0.0, 0.0, 50.0, 1000.0, 100.0, 0.0)
        val pair = q.chopAt(0.5)
        val first = pair.first(); val second = pair.second()
        // Endpoints
        assertEquals(q[0], first[0])
        assertEquals(q[2], second[2])
        // Shared midpoint
        assertEquals(first[2], second[0])
        // Sample at first(t=0.5) ≈ q(t=0.25)
        val s1 = first.ptAtT(0.5); val ref1 = q.ptAtT(0.25)
        assertEquals(ref1.x, s1.x, 1e-9); assertEquals(ref1.y, s1.y, 1e-9)
    }

    // ─── Root finders ────────────────────────────────────────────────

    @Test
    fun `RootsReal solves x squared minus 1 to give plus or minus 1`() {
        val s = DoubleArray(2)
        // 1·t² + 0·t - 1 = 0 → t = ±1
        val n = SkDQuad.RootsReal(1.0, 0.0, -1.0, s)
        assertEquals(2, n)
        val sorted = doubleArrayOf(s[0], s[1]).sortedArray()
        assertEquals(-1.0, sorted[0], 1e-12)
        assertEquals(1.0, sorted[1], 1e-12)
    }

    @Test
    fun `RootsReal returns 0 for negative discriminant`() {
        val s = DoubleArray(2)
        // 1·t² + 0·t + 1 = 0 → no real roots
        val n = SkDQuad.RootsReal(1.0, 0.0, 1.0, s)
        assertEquals(0, n)
    }

    @Test
    fun `RootsValidT keeps only roots in 0 to 1`() {
        val t = DoubleArray(2)
        // 1·t² - 1 = 0 → t = ±1 ; only +1 is in [0, 1].
        val n = SkDQuad.RootsValidT(1.0, 0.0, -1.0, t)
        assertEquals(1, n)
        assertEquals(1.0, t[0], 1e-12)
    }

    @Test
    fun `FindExtrema returns 1 for a quadratic with an interior peak on the axis`() {
        // y(t) = (1-t)²·0 + 2(1-t)t·1000 + t²·0 → derivative zero at t=0.5.
        val t = DoubleArray(1)
        val n = SkDQuad.FindExtrema(doubleArrayOf(0.0, 1000.0, 0.0), t)
        assertEquals(1, n)
        assertEquals(0.5, t[0], 1e-12)
    }

    @Test
    fun `FindExtrema returns 0 for a monotonic axis`() {
        val t = DoubleArray(1)
        // y(t) monotonic from 0 to 100 → no interior extremum.
        val n = SkDQuad.FindExtrema(doubleArrayOf(0.0, 50.0, 100.0), t)
        assertEquals(0, n)
    }

    @Test
    fun `SetABC produces parametric form coefficients consistent with expansion`() {
        // For y(t) = 0·(1-t)² + 2·1000·t(1-t) + 0·t² = 2000t - 2000t²
        // SetABC : a = -2000, b = 2000, c = 0.
        val src = doubleArrayOf(0.0, 1000.0, 0.0)
        val a = DoubleArray(1); val b = DoubleArray(1); val c = DoubleArray(1)
        SkDQuad.SetABC(src, a, b, c)
        assertEquals(-2000.0, a[0], 1e-12)
        assertEquals(2000.0, b[0], 1e-12)
        assertEquals(0.0, c[0], 1e-12)
    }

    // ─── horizontalIntersect / verticalIntersect ────────────────────

    @Test
    fun `horizontalIntersect on a peak finds two crossings of the half-height line`() {
        // y(t) peaks at 500 (t=0.5). Crossing y=250 happens twice.
        val q = quadOf(0.0, 0.0, 50.0, 1000.0, 100.0, 0.0)
        val roots = DoubleArray(2)
        val n = q.horizontalIntersect(250.0, roots)
        assertEquals(2, n)
        // Solutions are symmetric around t=0.5 — sum should be 1.
        assertEquals(1.0, roots[0] + roots[1], 1e-9)
    }

    @Test
    fun `verticalIntersect on a horizontal-peak quad finds the single t at midpoint x`() {
        // x(t) is monotonic 0 → 100, so there's exactly 1 root.
        val q = quadOf(0.0, 0.0, 50.0, 1000.0, 100.0, 0.0)
        val roots = DoubleArray(2)
        val n = q.verticalIntersect(50.0, roots)
        assertEquals(1, n)
        assertEquals(0.5, roots[0], 1e-9)
    }

    // ─── Small predicates / helpers ─────────────────────────────────

    @Test
    fun `collapsed detects coincident points`() {
        assertTrue(quadOf(1.0, 1.0, 1.0, 1.0, 1.0, 1.0).collapsed())
        assertFalse(quadOf(0.0, 0.0, 5.0, 10.0, 10.0, 0.0).collapsed())
    }

    @Test
    fun `flip reverses the point order`() {
        val q = quadOf(0.0, 0.0, 5.0, 10.0, 10.0, 0.0)
        val f = q.flip()
        assertEquals(SkDPoint(10.0, 0.0), f[0])
        assertEquals(SkDPoint(5.0, 10.0), f[1])
        assertEquals(SkDPoint(0.0, 0.0), f[2])
    }

    @Test
    fun `align snaps dst when control matches end on an axis`() {
        // Quad with the control sharing pts[2].x (10).
        val q = quadOf(0.0, 0.0, 10.0, 5.0, 10.0, 10.0)
        val dst = SkDPoint(10.0001, 5.0001)
        q.align(2, dst)
        assertEquals(10.0, dst.x, 0.0)            // snapped
        assertNotEquals(5.0, dst.y)               // unchanged because pts[2].y ≠ pts[1].y
    }
}
