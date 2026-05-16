package org.skia.pathops.internal


import org.graphiks.math.SkDLine
import org.graphiks.math.SkDPoint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for `SkIntersections.intersect/intersectRay/horizontal/
 * vertical(SkDQuad, ...)` (Phase D1.1.d.1).
 */
class SkDQuadLineIntersectionTest {

    private fun quadOf(
        x0: Double, y0: Double,
        x1: Double, y1: Double,
        x2: Double, y2: Double,
    ): SkDQuad = SkDQuad(arrayOf(SkDPoint(x0, y0), SkDPoint(x1, y1), SkDPoint(x2, y2)))

    private fun lineOf(x0: Double, y0: Double, x1: Double, y1: Double): SkDLine =
        SkDLine(arrayOf(SkDPoint(x0, y0), SkDPoint(x1, y1)))

    // ─── intersect (segment vs segment) ─────────────────────────────

    @Test
    fun `intersect on a peak quad and horizontal segment crossing it twice gives 2`() {
        // Quad : (0,0) - (50, 1000) - (100, 0). Peak at y=500 (t=0.5).
        // Horizontal segment y=250 from x=0 to x=100 crosses the quad twice.
        val ix = SkIntersections()
        val q = quadOf(0.0, 0.0, 50.0, 1000.0, 100.0, 0.0)
        val l = lineOf(0.0, 250.0, 100.0, 250.0)
        val n = ix.intersect(q, l)
        assertEquals(2, n)
        // Symmetric : sum of quad t's around 0.5 = 1.
        assertEquals(1.0, ix.t(0, 0) + ix.t(0, 1), 1e-9)
    }

    @Test
    fun `intersect on a quad and a tangent line gives 1`() {
        // Quad's apex is at (50, 500). Horizontal line y = 500 touches the
        // quad once at its apex (tangent).
        val ix = SkIntersections()
        val q = quadOf(0.0, 0.0, 50.0, 1000.0, 100.0, 0.0)
        val l = lineOf(0.0, 500.0, 100.0, 500.0)
        val n = ix.intersect(q, l)
        assertEquals(1, n)
        assertEquals(0.5, ix.t(0, 0), 1e-9)
    }

    @Test
    fun `intersect on a quad and a horizontal line above the peak gives 0`() {
        val ix = SkIntersections()
        val q = quadOf(0.0, 0.0, 50.0, 1000.0, 100.0, 0.0)
        val l = lineOf(0.0, 600.0, 100.0, 600.0)
        assertEquals(0, ix.intersect(q, l))
    }

    @Test
    fun `intersect on a quad endpoint touches counts at t=0`() {
        val ix = SkIntersections()
        // Quad starts at (0, 0). Diagonal line passes through (0, 0).
        val q = quadOf(0.0, 0.0, 50.0, 100.0, 100.0, 0.0)
        val l = lineOf(0.0, 0.0, 50.0, 50.0)
        val n = ix.intersect(q, l)
        assertTrue(n >= 1) { "expected ≥1 endpoint hit, got $n" }
        assertEquals(0.0, ix.t(0, 0), 1e-9)
        assertEquals(0.0, ix.t(1, 0), 1e-9)
    }

    // ─── intersectRay (infinite line, no segment-bounds check) ──────

    @Test
    fun `intersectRay on a quad and infinite line yields the same t`() {
        val ix = SkIntersections()
        val q = quadOf(0.0, 0.0, 50.0, 1000.0, 100.0, 0.0)
        val l = lineOf(0.0, 250.0, 1.0, 250.0) // short segment, but we ignore bounds
        val n = ix.intersectRay(q, l)
        assertEquals(2, n)
        assertEquals(1.0, ix.t(0, 0) + ix.t(0, 1), 1e-9)
    }

    // ─── horizontal / vertical line crossings ───────────────────────

    @Test
    fun `horizontal returns 2 crossings of a half-height line`() {
        val ix = SkIntersections()
        val q = quadOf(0.0, 0.0, 50.0, 1000.0, 100.0, 0.0)
        assertEquals(2, ix.horizontal(q, 0.0, 100.0, 250.0, false))
    }

    @Test
    fun `vertical returns 1 crossing for a peak quad at midpoint x`() {
        val ix = SkIntersections()
        // X-monotonic quad (control inside [0, 100]).
        val q = quadOf(0.0, 0.0, 50.0, 1000.0, 100.0, 0.0)
        val n = ix.vertical(q, 0.0, 1000.0, 50.0, false)
        assertEquals(1, n)
        assertEquals(0.5, ix.t(0, 0), 1e-9)
    }

    @Test
    fun `horizontal flipped mirrors each curve-2 t around 0_5 (no reorder)`() {
        val ix = SkIntersections()
        val q = quadOf(0.0, 0.0, 50.0, 1000.0, 100.0, 0.0)
        // Without flip, endpoints map left=0, right=1.
        ix.horizontal(q, 0.0, 100.0, 250.0, false)
        val t1Before = doubleArrayOf(ix.t(1, 0), ix.t(1, 1))

        val ix2 = SkIntersections()
        ix2.horizontal(q, 0.0, 100.0, 250.0, true)
        val t1After = doubleArrayOf(ix2.t(1, 0), ix2.t(1, 1))
        // SkIntersections.flip mirrors each t-value in-place without
        // reordering entries — so index i maps to (1 - t1Before[i]).
        assertEquals(1 - t1Before[0], t1After[0], 1e-9)
        assertEquals(1 - t1Before[1], t1After[1], 1e-9)
    }

    // ─── HorizontalIntercept / VerticalIntercept static helpers ─────

    @Test
    fun `HorizontalIntercept on a peak quad at y=250 gives the same 2 t's`() {
        val q = quadOf(0.0, 0.0, 50.0, 1000.0, 100.0, 0.0)
        val roots = DoubleArray(2)
        val n = SkIntersections.HorizontalIntercept(q, 250f, roots)
        assertEquals(2, n)
        assertEquals(1.0, roots[0] + roots[1], 1e-9)
    }

    @Test
    fun `VerticalIntercept on a monotonic-x quad gives the unique t`() {
        val q = quadOf(0.0, 0.0, 50.0, 1000.0, 100.0, 0.0)
        val roots = DoubleArray(2)
        val n = SkIntersections.VerticalIntercept(q, 50f, roots)
        assertEquals(1, n)
        assertEquals(0.5, roots[0], 1e-9)
    }

    // ─── nearPoint helper on SkDQuad ────────────────────────────────

    @Test
    fun `nearPoint accepts a point near the curve and returns its t`() {
        val q = quadOf(0.0, 0.0, 50.0, 1000.0, 100.0, 0.0)
        // Point (50, 500) is the curve apex (t=0.5 exactly).
        // The opp parameter defines the chord direction.
        val t = q.nearPoint(SkDPoint(50.0, 500.0), SkDPoint(100.0, 500.0))
        assertEquals(0.5, t, 1e-9)
    }

    @Test
    fun `nearPoint rejects a point far off the curve`() {
        val q = quadOf(0.0, 0.0, 50.0, 1000.0, 100.0, 0.0)
        // (50, -500) is outside the bbox.
        val t = q.nearPoint(SkDPoint(50.0, -500.0), SkDPoint(100.0, -500.0))
        assertEquals(-1.0, t, 1e-9)
    }
}
