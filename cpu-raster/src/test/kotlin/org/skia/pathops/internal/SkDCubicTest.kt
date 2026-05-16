package org.skia.pathops.internal



import org.graphiks.math.SkDPoint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for [SkDCubic] (Phase D1.1.b).
 *
 * Coverage : evaluation (`ptAtT`, `dxdyAtT`), monotonicity, subdivision
 * (`subDivide`, `chopAt`), inflections / extrema / max-curvature root
 * finders, cubic-equation root finders (`RootsReal`, `RootsValidT`,
 * `Coefficients`), conversions (`toQuad`, `toFloatPoints`), and the
 * small predicates (`collapsed` / `controlsInside` / `endsAreExtremaInXOrY`).
 */
class SkDCubicTest {

    private fun cubicOf(
        x0: Double, y0: Double,
        x1: Double, y1: Double,
        x2: Double, y2: Double,
        x3: Double, y3: Double,
    ): SkDCubic = SkDCubic(arrayOf(
        SkDPoint(x0, y0), SkDPoint(x1, y1),
        SkDPoint(x2, y2), SkDPoint(x3, y3),
    ))

    // ─── Evaluation ──────────────────────────────────────────────────

    @Test
    fun `ptAtT returns endpoints at t=0 and t=1`() {
        val c = cubicOf(0.0, 0.0, 0.0, 1000.0, 100.0, 1000.0, 100.0, 0.0)
        assertEquals(SkDPoint(0.0, 0.0), c.ptAtT(0.0))
        assertEquals(SkDPoint(100.0, 0.0), c.ptAtT(1.0))
    }

    @Test
    fun `ptAtT at t=0_5 matches the de-Casteljau formula`() {
        // Symmetric cubic. At t=0.5 :
        //   x = 0.125·0 + 0.375·0 + 0.375·100 + 0.125·100 = 50
        //   y = 0.125·0 + 0.375·1000 + 0.375·1000 + 0.125·0 = 750
        val c = cubicOf(0.0, 0.0, 0.0, 1000.0, 100.0, 1000.0, 100.0, 0.0)
        val mid = c.ptAtT(0.5)
        assertEquals(50.0, mid.x, 1e-9)
        assertEquals(750.0, mid.y, 1e-9)
    }

    @Test
    fun `dxdyAtT at endpoints points along the chord-derivative`() {
        val c = cubicOf(0.0, 0.0, 0.0, 1000.0, 100.0, 1000.0, 100.0, 0.0)
        // Tangent at t=0 = 3 · (P1 - P0) = (0, 3000)
        val v0 = c.dxdyAtT(0.0)
        assertEquals(0.0, v0.x, 1e-9); assertEquals(3000.0, v0.y, 1e-9)
        // Tangent at t=1 = 3 · (P3 - P2) = (0, -3000)
        val v1 = c.dxdyAtT(1.0)
        assertEquals(0.0, v1.x, 1e-9); assertEquals(-3000.0, v1.y, 1e-9)
    }

    // ─── Monotonicity ────────────────────────────────────────────────

    @Test
    fun `monotonicInX detects axis monotonic curves`() {
        // x(t) goes 0 → 0 → 100 → 100 — controls are between [0, 100].
        assertTrue(cubicOf(0.0, 0.0, 0.0, 1000.0, 100.0, 1000.0, 100.0, 0.0).monotonicInX())
        // x(t) overshoots — control's X = 200 outside [0, 100].
        assertFalse(cubicOf(0.0, 0.0, 200.0, 1000.0, 100.0, 1000.0, 100.0, 0.0).monotonicInX())
    }

    // ─── Subdivision ─────────────────────────────────────────────────

    @Test
    fun `subDivide of full range returns the same cubic`() {
        val c = cubicOf(0.0, 0.0, 0.0, 1000.0, 100.0, 1000.0, 100.0, 0.0)
        assertEquals(c, c.subDivide(0.0, 1.0))
    }

    @Test
    fun `subDivide preserves curve geometry on a subinterval`() {
        val c = cubicOf(0.0, 0.0, 0.0, 1000.0, 100.0, 1000.0, 100.0, 0.0)
        val sub = c.subDivide(0.25, 0.75)
        // sub(t=0) = c(t=0.25) ; sub(t=1) = c(t=0.75) ; sub(t=0.5) = c(t=0.5)
        val a = c.ptAtT(0.25); val b = c.ptAtT(0.75); val mid = c.ptAtT(0.5)
        val sa = sub.ptAtT(0.0); val sb = sub.ptAtT(1.0); val smid = sub.ptAtT(0.5)
        assertEquals(a.x, sa.x, 1e-9); assertEquals(a.y, sa.y, 1e-9)
        assertEquals(b.x, sb.x, 1e-9); assertEquals(b.y, sb.y, 1e-9)
        assertEquals(mid.x, smid.x, 1e-9); assertEquals(mid.y, smid.y, 1e-9)
    }

    @Test
    fun `chopAt midpoint produces two halves whose sample points match the original`() {
        val c = cubicOf(0.0, 0.0, 0.0, 1000.0, 100.0, 1000.0, 100.0, 0.0)
        val pair = c.chopAt(0.5)
        val first = pair.first(); val second = pair.second()
        assertEquals(c[0], first[0])
        assertEquals(c[3], second[3])
        // Shared midpoint
        assertEquals(first[3], second[0])
        // first(t=0.5) ≈ c(t=0.25)
        val s1 = first.ptAtT(0.5); val ref1 = c.ptAtT(0.25)
        assertEquals(ref1.x, s1.x, 1e-9); assertEquals(ref1.y, s1.y, 1e-9)
    }

    // ─── Root finders ────────────────────────────────────────────────

    @Test
    fun `RootsReal solves x cubed minus 1 to give 1`() {
        val s = DoubleArray(3)
        // 1·t³ + 0·t² + 0·t - 1 = 0 → t = 1
        val n = SkDCubic.RootsReal(1.0, 0.0, 0.0, -1.0, s)
        assertEquals(1, n)
        assertEquals(1.0, s[0], 1e-9)
    }

    @Test
    fun `RootsReal solves x cubed minus x to give -1, 0, 1`() {
        val s = DoubleArray(3)
        // 1·t³ + 0·t² - 1·t + 0 = 0 → t = -1, 0, 1
        val n = SkDCubic.RootsReal(1.0, 0.0, -1.0, 0.0, s)
        assertEquals(3, n)
        val sorted = s.copyOfRange(0, 3).sortedArray()
        assertEquals(-1.0, sorted[0], 1e-9)
        assertEquals(0.0, sorted[1], 1e-9)
        assertEquals(1.0, sorted[2], 1e-9)
    }

    @Test
    fun `RootsValidT keeps only roots in 0 to 1`() {
        val t = DoubleArray(3)
        // x³ - x = 0 → roots -1, 0, 1 — only 0 and 1 in [0, 1].
        val n = SkDCubic.RootsValidT(1.0, 0.0, -1.0, 0.0, t)
        assertEquals(2, n)
        val sorted = t.copyOfRange(0, 2).sortedArray()
        assertEquals(0.0, sorted[0], 1e-9)
        assertEquals(1.0, sorted[1], 1e-9)
    }

    @Test
    fun `RootsReal degenerates to quadratic when A is small`() {
        val s = DoubleArray(3)
        // A=0, B=1, C=0, D=-1 → t² = 1 → t = ±1
        val n = SkDCubic.RootsReal(0.0, 1.0, 0.0, -1.0, s)
        assertEquals(2, n)
    }

    @Test
    fun `Coefficients converts cubic Bezier to monomial basis`() {
        // Source : pts on line y = t (P0=0, P1=1/3, P2=2/3, P3=1).
        // Should produce A=0, B=0, C=1, D=0 → f(t) = t.
        val src = doubleArrayOf(0.0, 1.0 / 3, 2.0 / 3, 1.0)
        val A = DoubleArray(1); val B = DoubleArray(1); val C = DoubleArray(1); val D = DoubleArray(1)
        SkDCubic.Coefficients(src, A, B, C, D)
        assertEquals(0.0, A[0], 1e-12)
        assertEquals(0.0, B[0], 1e-12)
        assertEquals(1.0, C[0], 1e-12)
        assertEquals(0.0, D[0], 1e-12)
    }

    @Test
    fun `FindExtrema returns 1 for a cubic with a single peak on the axis`() {
        // y(t) for y-coords (0, 1000, 1000, 0) — symmetric peak at t=0.5.
        val tValues = DoubleArray(2)
        val n = SkDCubic.FindExtrema(doubleArrayOf(0.0, 1000.0, 1000.0, 0.0), tValues)
        assertEquals(1, n)
        assertEquals(0.5, tValues[0], 1e-9)
    }

    @Test
    fun `findInflections finds 0 inflections on a smooth single-peak cubic`() {
        // The symmetric (0,0)-(0,1000)-(100,1000)-(100,0) cubic is convex
        // throughout — 0 inflections.
        val c = cubicOf(0.0, 0.0, 0.0, 1000.0, 100.0, 1000.0, 100.0, 0.0)
        val t = DoubleArray(2)
        val n = c.findInflections(t)
        assertEquals(0, n)
    }

    // ─── Conversions ────────────────────────────────────────────────

    @Test
    fun `toQuad reproduces a degree-elevated quadratic exactly`() {
        // Quad y = (1-t)²·0 + 2(1-t)t·100 + t²·0  has cubic form via degree elevation :
        //   Q0 = (0, 0)
        //   Q1 = 1/3·(0,0) + 2/3·(0, 100) = (0, 200/3)
        //   Q2 = 2/3·(0, 100) + 1/3·(0, 0) = (0, 200/3)
        //   Q3 = (0, 0)  -- but for a non-trivial example use x-monotonic curve.
        // Simpler : start with a quad, elevate, convert back, check equality.
        val origQuad = SkDQuad(arrayOf(SkDPoint(0.0, 0.0), SkDPoint(50.0, 100.0), SkDPoint(100.0, 0.0)))
        // Degree elevation : Q0=P0, Q1=1/3 P0 + 2/3 P1, Q2=2/3 P1 + 1/3 P2, Q3=P2.
        val q1 = SkDPoint(
            (origQuad[0].x + 2 * origQuad[1].x) / 3,
            (origQuad[0].y + 2 * origQuad[1].y) / 3,
        )
        val q2 = SkDPoint(
            (2 * origQuad[1].x + origQuad[2].x) / 3,
            (2 * origQuad[1].y + origQuad[2].y) / 3,
        )
        val cubic = SkDCubic(arrayOf(origQuad[0], q1, q2, origQuad[2]))
        val recovered = cubic.toQuad()
        // For a degree-elevated quadratic, both averages of the cubic
        // controls hit the same P1 → recovered must match the original.
        assertEquals(origQuad[0], recovered[0])
        assertEquals(origQuad[1].x, recovered[1].x, 1e-9)
        assertEquals(origQuad[1].y, recovered[1].y, 1e-9)
        assertEquals(origQuad[2], recovered[2])
    }

    @Test
    fun `toFloatPoints converts to single precision and reports finiteness`() {
        val c = cubicOf(0.0, 0.0, 1.5, 2.5, 3.5, 4.5, 5.0, 6.0)
        val out = Array(4) { org.graphiks.math.SkPoint() }
        assertTrue(c.toFloatPoints(out))
        assertEquals(0f, out[0].fX); assertEquals(0f, out[0].fY)
        assertEquals(1.5f, out[1].fX); assertEquals(2.5f, out[1].fY)
        assertEquals(5f, out[3].fX); assertEquals(6f, out[3].fY)
    }

    // ─── Small predicates ───────────────────────────────────────────

    @Test
    fun `collapsed detects all-coincident points`() {
        assertTrue(cubicOf(1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0).collapsed())
        assertFalse(cubicOf(0.0, 0.0, 0.0, 1.0, 1.0, 1.0, 1.0, 0.0).collapsed())
    }

    @Test
    fun `endsAreExtremaInXOrY accepts cubic with monotonic-ish controls`() {
        // X-monotonic over [0, 100] with controls at 0 and 100 → ends are X-extrema.
        assertTrue(cubicOf(0.0, 0.0, 0.0, 1000.0, 100.0, 1000.0, 100.0, 0.0).endsAreExtremaInXOrY())
    }
}
