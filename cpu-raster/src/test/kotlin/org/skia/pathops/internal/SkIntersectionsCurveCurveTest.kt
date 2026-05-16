package org.skia.pathops.internal


import org.graphiks.math.SkDPoint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.sqrt

/**
 * Unit tests for the 6 curve-curve `SkIntersections.intersect(...)`
 * wrappers shipped in Phase D1.1.e.3 — the public API that
 * dispatches through `SkTSect.BinarySearch`.
 */
class SkIntersectionsCurveCurveTest {

    private val arcWeight = (sqrt(2.0) / 2).toFloat()

    private fun quad(
        x0: Double, y0: Double,
        x1: Double, y1: Double,
        x2: Double, y2: Double,
    ) = SkDQuad(arrayOf(SkDPoint(x0, y0), SkDPoint(x1, y1), SkDPoint(x2, y2)))

    private fun cubic(
        x0: Double, y0: Double,
        x1: Double, y1: Double,
        x2: Double, y2: Double,
        x3: Double, y3: Double,
    ) = SkDCubic(arrayOf(SkDPoint(x0, y0), SkDPoint(x1, y1), SkDPoint(x2, y2), SkDPoint(x3, y3)))

    private fun conic(
        x0: Double, y0: Double,
        x1: Double, y1: Double,
        x2: Double, y2: Double,
        weight: Float = arcWeight,
    ) = SkDConic(SkDQuad(arrayOf(SkDPoint(x0, y0), SkDPoint(x1, y1), SkDPoint(x2, y2))), weight)

    // ─── Disjoint cases — all 6 wrappers return 0 cleanly ──────────

    @Test
    fun `quad-quad disjoint returns 0`() {
        val ix = SkIntersections()
        val a = quad(0.0, 0.0, 50.0, 100.0, 100.0, 0.0)
        val b = quad(500.0, 500.0, 550.0, 600.0, 600.0, 500.0)
        assertEquals(0, ix.intersect(a, b))
    }

    @Test
    fun `conic-quad disjoint returns 0`() {
        val ix = SkIntersections()
        val k = conic(0.0, 0.0, 50.0, 100.0, 100.0, 0.0, 1f)
        val q = quad(500.0, 500.0, 550.0, 600.0, 600.0, 500.0)
        assertEquals(0, ix.intersect(k, q))
    }

    @Test
    fun `conic-conic disjoint returns 0`() {
        val ix = SkIntersections()
        val k1 = conic(0.0, 0.0, 50.0, 100.0, 100.0, 0.0, 1f)
        val k2 = conic(500.0, 500.0, 550.0, 600.0, 600.0, 500.0, 1f)
        assertEquals(0, ix.intersect(k1, k2))
    }

    @Test
    fun `cubic-quad disjoint returns 0`() {
        val ix = SkIntersections()
        val c = cubic(0.0, 0.0, 0.0, 1000.0, 100.0, 1000.0, 100.0, 0.0)
        val q = quad(500.0, 500.0, 550.0, 600.0, 600.0, 500.0)
        assertEquals(0, ix.intersect(c, q))
    }

    @Test
    fun `cubic-conic disjoint returns 0`() {
        val ix = SkIntersections()
        val c = cubic(0.0, 0.0, 0.0, 1000.0, 100.0, 1000.0, 100.0, 0.0)
        val k = conic(500.0, 500.0, 550.0, 600.0, 600.0, 500.0, 1f)
        assertEquals(0, ix.intersect(c, k))
    }

    @Test
    fun `cubic-cubic disjoint returns 0`() {
        val ix = SkIntersections()
        val c1 = cubic(0.0, 0.0, 0.0, 1000.0, 100.0, 1000.0, 100.0, 0.0)
        val c2 = cubic(500.0, 500.0, 500.0, 600.0, 600.0, 600.0, 600.0, 500.0)
        assertEquals(0, ix.intersect(c1, c2))
    }

    // ─── Shared-endpoint cases — all 6 produce ≥1 hit ──────────────

    @Test
    fun `quad-quad sharing tail-to-head reports endpoint match`() {
        val ix = SkIntersections()
        val a = quad(0.0, 0.0, 50.0, 100.0, 100.0, 0.0)
        val b = quad(100.0, 0.0, 150.0, 100.0, 200.0, 0.0)
        assertTrue(ix.intersect(a, b) >= 1)
    }

    @Test
    fun `cubic-cubic sharing endpoint reports endpoint match`() {
        val ix = SkIntersections()
        val c1 = cubic(0.0, 0.0, 0.0, 1000.0, 100.0, 1000.0, 100.0, 0.0)
        val c2 = cubic(100.0, 0.0, 100.0, 1000.0, 200.0, 1000.0, 200.0, 0.0)
        assertTrue(ix.intersect(c1, c2) >= 1)
    }

    @Test
    fun `cubic-quad sharing endpoint reports endpoint match`() {
        val ix = SkIntersections()
        val c = cubic(0.0, 0.0, 0.0, 1000.0, 100.0, 1000.0, 100.0, 0.0)
        val q = quad(100.0, 0.0, 150.0, 100.0, 200.0, 0.0)
        assertTrue(ix.intersect(c, q) >= 1)
    }

    // ─── Don't-crash cases on harder geometry ──────────────────────

    @Test
    fun `quad-quad crossing X doesn't crash`() {
        val ix = SkIntersections()
        val a = quad(0.0, 0.0, 50.0, 100.0, 100.0, 0.0)
        val b = quad(0.0, 100.0, 50.0, 0.0, 100.0, 100.0)
        ix.intersect(a, b) // no assertion — just verify no crash / no hang
    }

    @Test
    fun `cubic-cubic crossing doesn't crash`() {
        val ix = SkIntersections()
        val c1 = cubic(0.0, 0.0, 0.0, 1000.0, 100.0, 1000.0, 100.0, 0.0)
        val c2 = cubic(0.0, 1000.0, 0.0, 0.0, 100.0, 0.0, 100.0, 1000.0)
        ix.intersect(c1, c2) // no assertion
    }
}
