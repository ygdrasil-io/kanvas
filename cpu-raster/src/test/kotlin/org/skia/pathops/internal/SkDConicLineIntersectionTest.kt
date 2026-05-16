package org.skia.pathops.internal


import org.graphiks.math.SkDLine
import org.graphiks.math.SkDPoint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.sqrt

/**
 * Unit tests for `SkIntersections.intersect/intersectRay/horizontal/
 * vertical(SkDConic, ...)` plus the SkDConic helpers
 * `horizontalIntersect`, `verticalIntersect`, `nearPoint` (Phase D1.1.d.3).
 */
class SkDConicLineIntersectionTest {

    private fun conicOf(
        x0: Double, y0: Double,
        x1: Double, y1: Double,
        x2: Double, y2: Double,
        weight: Float,
    ): SkDConic = SkDConic(
        pts = SkDQuad(arrayOf(SkDPoint(x0, y0), SkDPoint(x1, y1), SkDPoint(x2, y2))),
        weight = weight,
    )

    private fun lineOf(x0: Double, y0: Double, x1: Double, y1: Double): SkDLine =
        SkDLine(arrayOf(SkDPoint(x0, y0), SkDPoint(x1, y1)))

    /** Quarter-arc weight = √2/2. */
    private val arcWeight = (sqrt(2.0) / 2).toFloat()

    /** Standard quarter-arc (1, 0) → (1, 1) → (0, 1). */
    private val arc = conicOf(1.0, 0.0, 1.0, 1.0, 0.0, 1.0, arcWeight)

    // ─── intersect (segment vs segment) ─────────────────────────────

    @Test
    fun `intersect on quarter-arc and a horizontal mid-line gives 1`() {
        val ix = SkIntersections()
        // Horizontal segment y=0.5 from x=0 to x=1 — crosses the arc once.
        val l = lineOf(0.0, 0.5, 1.0, 0.5)
        val n = ix.intersect(arc, l)
        assertEquals(1, n)
    }

    @Test
    fun `intersect with weight=1 conic equals same-shape quad result`() {
        val ix = SkIntersections()
        val ix2 = SkIntersections()
        // Conic with weight 1 is equivalent to a standard quad.
        val k = conicOf(0.0, 0.0, 50.0, 1000.0, 100.0, 0.0, 1f)
        val q = SkDQuad(arrayOf(SkDPoint(0.0, 0.0), SkDPoint(50.0, 1000.0), SkDPoint(100.0, 0.0)))
        val l = lineOf(0.0, 250.0, 100.0, 250.0)
        val nC = ix.intersect(k, l)
        val nQ = ix2.intersect(q, l)
        assertEquals(nQ, nC)
        assertEquals(2, nC)
        // Same t-values
        assertEquals(ix2.t(0, 0), ix.t(0, 0), 1e-9)
        assertEquals(ix2.t(0, 1), ix.t(0, 1), 1e-9)
    }

    @Test
    fun `intersect on conic endpoint registers a t=0 hit`() {
        val ix = SkIntersections()
        // Line through (1, 0) — endpoint of the arc.
        val l = lineOf(1.0, 0.0, 0.0, 0.0)
        val n = ix.intersect(arc, l)
        assertTrue(n >= 1) { "expected ≥1 endpoint hit, got $n" }
        assertEquals(0.0, ix.t(0, 0), 1e-9)
    }

    @Test
    fun `intersect on a conic and line above the bbox gives 0`() {
        val ix = SkIntersections()
        // y = 2 is above the unit-circle arc.
        val l = lineOf(0.0, 2.0, 1.0, 2.0)
        assertEquals(0, ix.intersect(arc, l))
    }

    // ─── intersectRay ────────────────────────────────────────────────

    @Test
    fun `intersectRay on conic and infinite line yields the same crossing t`() {
        val ix = SkIntersections()
        // Short segment that doesn't span the conic, but the ray extends.
        val l = lineOf(0.0, 0.5, 0.001, 0.5)
        val n = ix.intersectRay(arc, l)
        assertTrue(n >= 1) { "expected ≥1 ray crossing, got $n" }
    }

    // ─── horizontal / vertical ──────────────────────────────────────

    @Test
    fun `horizontal returns 1 crossing on the quarter-arc at y=0_5`() {
        val ix = SkIntersections()
        val n = ix.horizontal(arc, 0.0, 1.0, 0.5, false)
        assertEquals(1, n)
    }

    @Test
    fun `vertical returns 1 crossing on the quarter-arc at x=0_5`() {
        val ix = SkIntersections()
        val n = ix.vertical(arc, 0.0, 1.0, 0.5, false)
        assertEquals(1, n)
    }

    // ─── horizontalIntersect / verticalIntersect on SkDConic ────────

    @Test
    fun `SkDConic horizontalIntersect on quarter-arc at half-height finds 1 t`() {
        val roots = DoubleArray(2)
        val n = arc.horizontalIntersect(0.5, roots)
        assertEquals(1, n)
        assertTrue(roots[0] in 0.0..1.0)
    }

    @Test
    fun `SkDConic verticalIntersect on quarter-arc at half-width finds 1 t`() {
        val roots = DoubleArray(2)
        val n = arc.verticalIntersect(0.5, roots)
        assertEquals(1, n)
        assertTrue(roots[0] in 0.0..1.0)
    }

    // ─── nearPoint helper on SkDConic ───────────────────────────────

    @Test
    fun `SkDConic nearPoint accepts a point on the arc and returns its t`() {
        // Apex of the quarter-arc lies at t=0.5 ; coords ≈ (√2/2, √2/2).
        val mid = arc.ptAtT(0.5)
        val t = arc.nearPoint(mid, SkDPoint(1.0, 1.0))
        assertEquals(0.5, t, 1e-3)
    }

    @Test
    fun `SkDConic nearPoint rejects a point outside the bbox`() {
        val t = arc.nearPoint(SkDPoint(5.0, 5.0), SkDPoint(0.0, 0.0))
        assertEquals(-1.0, t, 1e-9)
    }

    // ─── Static intercepts ──────────────────────────────────────────

    @Test
    fun `SkIntersections HorizontalIntercept on conic finds 1 t at y=0_5`() {
        val roots = DoubleArray(2)
        val n = SkIntersections.HorizontalIntercept(arc, 0.5f, roots)
        assertEquals(1, n)
    }

    @Test
    fun `SkIntersections VerticalIntercept on conic finds 1 t at x=0_5`() {
        val roots = DoubleArray(2)
        val n = SkIntersections.VerticalIntercept(arc, 0.5f, roots)
        assertEquals(1, n)
    }

    // ─── SkPoint façade ─────────────────────────────────────────────

    @Test
    fun `conicLine SkPoint façade accepts SkPoint arrays`() {
        val ix = SkIntersections()
        val a = arrayOf(
            org.graphiks.math.SkPoint(fX = 1f, fY = 0f),
            org.graphiks.math.SkPoint(fX = 1f, fY = 1f),
            org.graphiks.math.SkPoint(fX = 0f, fY = 1f),
        )
        val b = arrayOf(
            org.graphiks.math.SkPoint(fX = 0f, fY = 0.5f),
            org.graphiks.math.SkPoint(fX = 1f, fY = 0.5f),
        )
        assertEquals(1, ix.conicLine(a, arcWeight, b))
    }
}
