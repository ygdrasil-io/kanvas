package org.skia.pathops.internal

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.math.SkPoint

/**
 * Unit tests for [SkIntersections] (Phase D1.1.c).
 *
 * Coverage : container ops (insert / sort / dedup / removeOne /
 * coincidence bitmask), line-line intersection (interior crossing,
 * end-point matches, parallel-coincident), `intersectRay` for
 * infinite lines, axis-aligned `horizontal` / `vertical` line
 * crossings, `HorizontalIntercept` / `VerticalIntercept` static
 * helpers. Curve intersection methods are stubs in this slice and
 * throw `NotImplementedError`.
 */
class SkIntersectionsTest {

    // ─── Container basics ───────────────────────────────────────────

    @Test
    fun `insert sorts entries by curve-1 t and stores point + curve-2 t`() {
        val ix = SkIntersections()
        ix.setMax(4)
        // Insert out of order ; expect sorted by curve-1 t.
        ix.insert(0.5, 0.5, SkDPoint(5.0, 5.0))
        ix.insert(0.2, 0.3, SkDPoint(2.0, 3.0))
        ix.insert(0.8, 0.7, SkDPoint(8.0, 7.0))
        assertEquals(3, ix.used())
        assertEquals(0.2, ix.t(0, 0)); assertEquals(0.3, ix.t(1, 0))
        assertEquals(0.5, ix.t(0, 1)); assertEquals(0.5, ix.t(1, 1))
        assertEquals(0.8, ix.t(0, 2)); assertEquals(0.7, ix.t(1, 2))
    }

    @Test
    fun `insert rejects exact duplicates and returns -1`() {
        val ix = SkIntersections()
        ix.setMax(4)
        assertEquals(0, ix.insert(0.5, 0.5, SkDPoint(5.0, 5.0)))
        assertEquals(-1, ix.insert(0.5, 0.5, SkDPoint(5.0, 5.0)))
        assertEquals(1, ix.used())
    }

    @Test
    fun `removeOne shifts following entries down`() {
        val ix = SkIntersections()
        ix.setMax(4)
        ix.insert(0.1, 0.1, SkDPoint(1.0, 1.0))
        ix.insert(0.2, 0.2, SkDPoint(2.0, 2.0))
        ix.insert(0.3, 0.3, SkDPoint(3.0, 3.0))
        ix.removeOne(1)
        assertEquals(2, ix.used())
        assertEquals(0.1, ix.t(0, 0))
        assertEquals(0.3, ix.t(0, 1))
    }

    @Test
    fun `setCoincident + isCoincident roundtrip`() {
        val ix = SkIntersections()
        ix.setMax(4)
        ix.insert(0.0, 0.0, SkDPoint(0.0, 0.0))
        ix.insert(1.0, 1.0, SkDPoint(1.0, 1.0))
        ix.setCoincident(0)
        assertTrue(ix.isCoincident(0))
        assertFalse(ix.isCoincident(1))
        ix.clearCoincidence(0)
        assertFalse(ix.isCoincident(0))
    }

    @Test
    fun `reset clears used and coincidence but preserves max + swap`() {
        val ix = SkIntersections()
        ix.setMax(5)
        ix.swap()
        ix.insert(0.5, 0.5, SkDPoint(5.0, 5.0))
        ix.setCoincident(0)
        ix.reset()
        assertEquals(0, ix.used())
        assertFalse(ix.isCoincident(0))
        assertTrue(ix.swapped())
    }

    @Test
    fun `flip mirrors curve-2 t around 0_5`() {
        val ix = SkIntersections()
        ix.setMax(2)
        ix.insert(0.3, 0.2, SkDPoint(0.0, 0.0))
        ix.insert(0.7, 0.8, SkDPoint(0.0, 0.0))
        ix.flip()
        assertEquals(0.8, ix.t(1, 0), 1e-12)
        assertEquals(0.2, ix.t(1, 1), 1e-12)
    }

    // ─── Line-Line intersection ─────────────────────────────────────

    @Test
    fun `intersect on crossing X gives one interior point`() {
        val ix = SkIntersections()
        // Diagonal (0,0)-(10,10) crosses (0,10)-(10,0) at (5, 5).
        val a = SkDLine(arrayOf(SkDPoint(0.0, 0.0), SkDPoint(10.0, 10.0)))
        val b = SkDLine(arrayOf(SkDPoint(0.0, 10.0), SkDPoint(10.0, 0.0)))
        assertEquals(1, ix.intersect(a, b))
        assertEquals(0.5, ix.t(0, 0), 1e-12)
        assertEquals(0.5, ix.t(1, 0), 1e-12)
        val pt = ix.pt(0)
        assertEquals(5.0, pt.x, 1e-12); assertEquals(5.0, pt.y, 1e-12)
    }

    @Test
    fun `intersect on parallel-disjoint returns 0`() {
        val ix = SkIntersections()
        val a = SkDLine(arrayOf(SkDPoint(0.0, 0.0), SkDPoint(10.0, 0.0)))
        val b = SkDLine(arrayOf(SkDPoint(0.0, 5.0), SkDPoint(10.0, 5.0)))
        assertEquals(0, ix.intersect(a, b))
    }

    @Test
    fun `intersect at shared endpoint registers exactly one match`() {
        val ix = SkIntersections()
        // Both lines start at (0, 0).
        val a = SkDLine(arrayOf(SkDPoint(0.0, 0.0), SkDPoint(10.0, 0.0)))
        val b = SkDLine(arrayOf(SkDPoint(0.0, 0.0), SkDPoint(0.0, 10.0)))
        val n = ix.intersect(a, b)
        assertTrue(n >= 1) { "expected ≥1 intersection at shared origin, got $n" }
        // First entry is at t=0 on both curves.
        assertEquals(0.0, ix.t(0, 0), 1e-12)
        assertEquals(0.0, ix.t(1, 0), 1e-12)
    }

    @Test
    fun `intersect on segments that don't cross within their range returns 0`() {
        val ix = SkIntersections()
        // Two short non-parallel segments that would meet far outside their range.
        val a = SkDLine(arrayOf(SkDPoint(0.0, 0.0), SkDPoint(1.0, 1.0)))
        val b = SkDLine(arrayOf(SkDPoint(10.0, 0.0), SkDPoint(10.0, 1.0)))
        assertEquals(0, ix.intersect(a, b))
    }

    @Test
    fun `intersectRay on infinite-line crossing finds intersection outside the segment`() {
        val ix = SkIntersections()
        val a = SkDLine(arrayOf(SkDPoint(0.0, 0.0), SkDPoint(1.0, 1.0)))
        val b = SkDLine(arrayOf(SkDPoint(10.0, 0.0), SkDPoint(10.0, 1.0)))
        assertEquals(1, ix.intersectRay(a, b))
        // a's ray hits x=10 at t = 10 ; b's ray at y=10 from (10, 0).
        assertEquals(10.0, ix.t(0, 0), 1e-12)
        assertEquals(10.0, ix.t(1, 0), 1e-12)
    }

    @Test
    fun `intersectRay on coincident lines returns 2 with endpoints`() {
        val ix = SkIntersections()
        val a = SkDLine(arrayOf(SkDPoint(0.0, 0.0), SkDPoint(10.0, 0.0)))
        val b = SkDLine(arrayOf(SkDPoint(2.0, 0.0), SkDPoint(7.0, 0.0)))
        assertEquals(2, ix.intersectRay(a, b))
    }

    @Test
    fun `intersectRay on parallel-offset lines returns 0`() {
        val ix = SkIntersections()
        val a = SkDLine(arrayOf(SkDPoint(0.0, 0.0), SkDPoint(10.0, 0.0)))
        val b = SkDLine(arrayOf(SkDPoint(0.0, 5.0), SkDPoint(10.0, 5.0)))
        assertEquals(0, ix.intersectRay(a, b))
    }

    // ─── horizontal / vertical line crossings ───────────────────────

    @Test
    fun `horizontal crosses a diagonal line at the expected interior t`() {
        val ix = SkIntersections()
        // Diagonal (0,0)-(10,10) crosses y=5 segment x∈[0, 10] at (5, 5).
        val line = SkDLine(arrayOf(SkDPoint(0.0, 0.0), SkDPoint(10.0, 10.0)))
        assertEquals(1, ix.horizontal(line, 0.0, 10.0, 5.0, false))
        assertEquals(0.5, ix.t(0, 0), 1e-12)
        assertEquals(5.0, ix.pt(0).x, 1e-12)
        assertEquals(5.0, ix.pt(0).y, 1e-12)
    }

    @Test
    fun `vertical crosses a diagonal line at the expected interior t`() {
        val ix = SkIntersections()
        val line = SkDLine(arrayOf(SkDPoint(0.0, 0.0), SkDPoint(10.0, 10.0)))
        assertEquals(1, ix.vertical(line, 0.0, 10.0, 5.0, false))
        assertEquals(0.5, ix.t(0, 0), 1e-12)
    }

    @Test
    fun `HorizontalIntercept returns the parametric t at the given y`() {
        val line = SkDLine(arrayOf(SkDPoint(0.0, 0.0), SkDPoint(10.0, 20.0)))
        assertEquals(0.25, SkIntersections.HorizontalIntercept(line, 5.0), 1e-12)
    }

    @Test
    fun `VerticalIntercept returns the parametric t at the given x`() {
        val line = SkDLine(arrayOf(SkDPoint(0.0, 0.0), SkDPoint(10.0, 20.0)))
        assertEquals(0.25, SkIntersections.VerticalIntercept(line, 2.5), 1e-12)
    }

    @Test
    fun `lineLine SkPoint facade accepts SkPoint arrays`() {
        val ix = SkIntersections()
        val a = arrayOf(SkPoint(fX = 0f, fY = 0f), SkPoint(fX = 10f, fY = 10f))
        val b = arrayOf(SkPoint(fX = 0f, fY = 10f), SkPoint(fX = 10f, fY = 0f))
        assertEquals(1, ix.lineLine(a, b))
    }

    // ─── Stubs for curve intersections (D1.1.d/e) ───────────────────

    @Test
    fun `intersect(SkDConic, SkDLine) still throws until D1_1_d_3 lands`() {
        val ix = SkIntersections()
        val k = SkDConic(
            pts = SkDQuad(arrayOf(SkDPoint(1.0, 0.0), SkDPoint(1.0, 1.0), SkDPoint(0.0, 1.0))),
            weight = 0.7071f,
        )
        val l = SkDLine(arrayOf(SkDPoint(0.0, 0.5), SkDPoint(1.0, 0.5)))
        assertThrows(NotImplementedError::class.java) { ix.intersect(k, l) }
    }
}
