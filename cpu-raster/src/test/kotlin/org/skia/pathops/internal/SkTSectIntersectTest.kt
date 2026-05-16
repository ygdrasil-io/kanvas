package org.skia.pathops.internal


import org.graphiks.math.SkDLine
import org.graphiks.math.SkDPoint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for the intersect machinery added to [SkTSect] in
 * Phase D1.1.e.2.c.3 — `intersects` / `linesIntersect` / `trim` /
 * `EndsEqual` / `isParallel`. The full BinarySearch end-to-end
 * exercise lands in D1.1.e.2.c.4.
 */
class SkTSectIntersectTest {

    private fun quadCurve(
        x0: Double = 0.0, y0: Double = 0.0,
        x1: Double = 50.0, y1: Double = 100.0,
        x2: Double = 100.0, y2: Double = 0.0,
    ) = SkTQuad(SkDQuad(arrayOf(SkDPoint(x0, y0), SkDPoint(x1, y1), SkDPoint(x2, y2))))

    // ─── EndsEqual ─────────────────────────────────────────────────

    @Test
    fun `EndsEqual detects exact head-to-head match`() {
        // Both sects start at (0, 0).
        val s1 = SkTSect(quadCurve(0.0, 0.0, 50.0, 100.0, 100.0, 0.0))
        val s2 = SkTSect(quadCurve(0.0, 0.0, 50.0, -100.0, 100.0, 0.0))
        val ix = SkIntersections()
        ix.setMax(4)
        val mask = SkTSect.EndsEqual(s1, s2, ix)
        assertTrue((mask and SkTSect.kZeroS1Set) != 0)
        assertTrue((mask and SkTSect.kZeroS2Set) != 0)
        // Both ends also match (100, 0) → tail-to-tail.
        assertTrue((mask and SkTSect.kOneS1Set) != 0)
        assertTrue((mask and SkTSect.kOneS2Set) != 0)
        assertTrue(ix.used() >= 2)
    }

    @Test
    fun `EndsEqual returns 0 when no endpoints align`() {
        val s1 = SkTSect(quadCurve(0.0, 0.0, 50.0, 100.0, 100.0, 0.0))
        val s2 = SkTSect(quadCurve(200.0, 200.0, 250.0, 300.0, 300.0, 200.0))
        val ix = SkIntersections()
        ix.setMax(4)
        val mask = SkTSect.EndsEqual(s1, s2, ix)
        assertEquals(0, mask)
        assertEquals(0, ix.used())
    }

    @Test
    fun `EndsEqual approximate-equal pass uses insertNear`() {
        // Endpoints that are approximately-but-not-exactly equal.
        val s1 = SkTSect(quadCurve(0.0, 0.0, 50.0, 100.0, 100.0, 0.0))
        val s2 = SkTSect(quadCurve(1e-9, 1e-9, 50.0, 100.0, 100.0, 0.0))
        val ix = SkIntersections()
        ix.setMax(4)
        val mask = SkTSect.EndsEqual(s1, s2, ix)
        // Either exact-match (zeroSet + zeroS2Set) or near-match path triggers.
        assertTrue((mask and (SkTSect.kZeroS1Set or SkTSect.kZeroS2Set)) != 0)
    }

    // ─── isParallel ────────────────────────────────────────────────

    @Test
    fun `isParallel returns false for non-conic opp curve`() {
        val s = SkTSect(quadCurve())
        val line = SkDLine(arrayOf(SkDPoint(0.0, 0.0), SkDPoint(10.0, 0.0)))
        val opp = SkTQuad(SkDQuad(arrayOf(SkDPoint(0.0, 0.0), SkDPoint(5.0, 5.0), SkDPoint(10.0, 0.0))))
        // Per upstream comment : non-conic opp always returns false.
        assertFalse(s.isParallel(line, opp))
    }

    // ─── intersects (hull-based dispatch) ──────────────────────────

    @Test
    fun `intersects returns nonzero for overlapping spans`() {
        val s1 = SkTSect(quadCurve(0.0, 0.0, 50.0, 100.0, 100.0, 0.0))
        val s2 = SkTSect(quadCurve(0.0, 50.0, 50.0, -50.0, 100.0, 50.0))
        val span1 = s1.fHead!!
        val span2 = s2.fHead!!
        val oppRes = IntArray(1)
        val r = s1.intersects(span1, s2, span2, oppRes)
        // hullsIntersect should return ≥1 for these overlapping shapes.
        assertTrue(r >= 1) { "expected intersects ≥ 1, got $r" }
    }

    @Test
    fun `intersects returns -1 or 0 for disjoint spans`() {
        val s1 = SkTSect(quadCurve(0.0, 0.0, 50.0, 100.0, 100.0, 0.0))
        val s2 = SkTSect(quadCurve(500.0, 500.0, 550.0, 600.0, 600.0, 500.0))
        val span1 = s1.fHead!!
        val span2 = s2.fHead!!
        val oppRes = IntArray(1)
        val r = s1.intersects(span1, s2, span2, oppRes)
        // Disjoint hulls return 0 from hullsIntersect → intersects returns 0.
        assertEquals(0, r)
    }

    // ─── trim ──────────────────────────────────────────────────────

    @Test
    fun `trim drops bounded entries that no longer intersect`() {
        val s1 = SkTSect(quadCurve(0.0, 0.0, 50.0, 100.0, 100.0, 0.0))
        val s2 = SkTSect(quadCurve(500.0, 500.0, 550.0, 600.0, 600.0, 500.0))
        val span1 = s1.fHead!!
        val span2 = s2.fHead!!
        // Add bidirectional bounded link.
        span1.addBounded(span2)
        span2.addBounded(span1)
        // trim should detect no intersection and unlink.
        assertTrue(s1.trim(span1, s2))
        // Either span1 was removed (fHead is null) or its bounded list is now empty.
        assertTrue(s1.fHead == null || span1.fBounded == null)
    }

    // ─── linesIntersect (linear span pair) ─────────────────────────

    @Test
    fun `linesIntersect on crossing line-spans returns 1 with intersection`() {
        // Two degenerate-line quads that cross at (5, 5).
        val q1 = quadCurve(0.0, 0.0, 5.0, 5.0, 10.0, 10.0)
        val q2 = quadCurve(0.0, 10.0, 5.0, 5.0, 10.0, 0.0)
        val s1 = SkTSect(q1)
        val s2 = SkTSect(q2)
        val span1 = s1.fHead!!
        val span2 = s2.fHead!!
        // Force fIsLine on both — so hullCheck doesn't short-circuit.
        span1.fIsLinear = true; span1.fIsLine = true
        span2.fIsLinear = true; span2.fIsLine = true
        val ix = SkIntersections()
        ix.setMax(2)
        val r = s1.linesIntersect(span1, s2, span2, ix)
        // Should report at least one intersection.
        assertTrue(r >= 1) { "expected ≥1 line intersection, got $r" }
    }

    // ─── kZeroS1Set bitset constants ──────────────────────────────

    @Test
    fun `BinarySearch endpoint bitset constants match upstream`() {
        assertEquals(1, SkTSect.kZeroS1Set)
        assertEquals(2, SkTSect.kOneS1Set)
        assertEquals(4, SkTSect.kZeroS2Set)
        assertEquals(8, SkTSect.kOneS2Set)
    }
}
