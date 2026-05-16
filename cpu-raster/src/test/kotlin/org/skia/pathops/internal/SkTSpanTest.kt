package org.skia.pathops.internal


import org.skia.math.SkDPoint
import org.skia.math.between
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for [SkTSpan] (Phase D1.1.e.2.b).
 *
 * Coverage : init / initBounds, bounded-list management
 * (addBounded / removeBounded / removeAllBounded / findOppSpan /
 * hasOppT / closestBoundedT), hull/linear intersect tests,
 * splitAt linkage, contains, linearT, markCoincident.
 */
class SkTSpanTest {

    private fun quadCurve() = SkTQuad(SkDQuad(arrayOf(
        SkDPoint(0.0, 0.0), SkDPoint(50.0, 100.0), SkDPoint(100.0, 0.0),
    )))

    private fun makeSpan(curve: SkTCurve = quadCurve()): SkTSpan {
        val s = SkTSpan(curve)
        s.init(curve)
        return s
    }

    // ─── init / initBounds ─────────────────────────────────────────

    @Test
    fun `init sets full t-range and resets bounded list`() {
        val s = makeSpan()
        assertEquals(0.0, s.startT())
        assertEquals(1.0, s.endT())
        assertNull(s.fBounded)
        assertNull(s.fPrev)
        assertNull(s.fNext)
    }

    @Test
    fun `initBounds returns true and computes valid bounds`() {
        val curve = quadCurve()
        val s = SkTSpan(curve)
        s.fStartT = 0.0; s.fEndT = 1.0
        assertTrue(s.initBounds(curve))
        assertTrue(s.fBounds.valid())
        assertFalse(s.fCollapsed) // peak quad is non-collapsed
        // Bounds should match the original peak quad : (0, 0)-(100, 50).
        assertEquals(0.0, s.fBounds.left, 1e-9)
        assertEquals(50.0, s.fBounds.bottom, 1e-9)
    }

    @Test
    fun `initBounds returns false on NaN t`() {
        val curve = quadCurve()
        val s = SkTSpan(curve)
        s.fStartT = Double.NaN; s.fEndT = 1.0
        assertFalse(s.initBounds(curve))
    }

    // ─── Bounded list ──────────────────────────────────────────────

    @Test
    fun `addBounded prepends and findOppSpan finds`() {
        val a = makeSpan()
        val b = makeSpan()
        a.addBounded(b)
        assertSame(b, a.findOppSpan(b))
        assertTrue(a.isBounded())
    }

    @Test
    fun `removeBounded drops the entry and reports empty when last removed`() {
        val a = makeSpan()
        val b = makeSpan()
        a.addBounded(b)
        assertTrue(a.removeBounded(b))
        assertNull(a.fBounded)
        assertFalse(a.isBounded())
    }

    @Test
    fun `removeAllBounded propagates removal to opposing spans`() {
        val a = makeSpan()
        val b = makeSpan()
        // Two-way bounded link.
        a.addBounded(b); b.addBounded(a)
        a.removeAllBounded()
        // After removal, b's bounded list no longer references a.
        assertNull(b.findOppSpan(a))
    }

    @Test
    fun `closestBoundedT returns the nearest endpoint t`() {
        val a = makeSpan()
        val b = makeSpan() // covers t in [0, 1] of the quad
        a.addBounded(b)
        // (0, 0) is the start of the quad → closest endpoint is t=0.
        assertEquals(0.0, a.closestBoundedT(SkDPoint(0.0, 0.0)))
        // (100, 0) is the end → closest is t=1.
        assertEquals(1.0, a.closestBoundedT(SkDPoint(100.0, 0.0)))
    }

    // ─── contains / hasOppT ────────────────────────────────────────

    @Test
    fun `contains accepts t inside this or any sibling`() {
        val s = makeSpan()
        assertTrue(s.contains(0.5))
        assertTrue(s.contains(0.0))
        assertTrue(s.contains(1.0))
    }

    @Test
    fun `hasOppT walks the bounded list`() {
        val a = makeSpan()
        val b = makeSpan() // covers t in [0, 1]
        assertFalse(a.hasOppT(0.5))
        a.addBounded(b)
        assertTrue(a.hasOppT(0.5))
    }

    // ─── linearT / markCoincident ──────────────────────────────────

    @Test
    fun `linearT projects a point onto the chord`() {
        val s = makeSpan() // chord (0, 0) → (100, 0)
        assertEquals(0.5, s.linearT(SkDPoint(50.0, 0.0)), 1e-9)
        assertEquals(0.0, s.linearT(SkDPoint(0.0, 0.0)), 1e-9)
        assertEquals(1.0, s.linearT(SkDPoint(100.0, 0.0)), 1e-9)
    }

    @Test
    fun `markCoincident sets both endpoint match flags`() {
        val s = makeSpan()
        s.markCoincident()
        assertTrue(s.fCoinStart.isMatch())
        assertTrue(s.fCoinEnd.isMatch())
    }

    // ─── splitAt linkage ───────────────────────────────────────────

    @Test
    fun `splitAt inserts the new span between work and work_next`() {
        val curve = quadCurve()
        val a = SkTSpan(curve); a.init(curve)
        val b = SkTSpan(curve)
        assertTrue(b.splitAt(a, 0.5))
        // After split : a covers [0, 0.5], b covers [0.5, 1] and is
        // a's next, with prev pointing back.
        assertEquals(0.0, a.fStartT); assertEquals(0.5, a.fEndT)
        assertEquals(0.5, b.fStartT); assertEquals(1.0, b.fEndT)
        assertSame(b, a.fNext)
        assertSame(a, b.fPrev)
    }

    @Test
    fun `splitAt at start collapses left half and returns false`() {
        val curve = quadCurve()
        val a = SkTSpan(curve); a.init(curve)
        val b = SkTSpan(curve)
        // t == startT collapses the left ; b takes the (collapsed) value.
        assertFalse(b.splitAt(a, 0.0))
        assertTrue(a.fCollapsed || b.fCollapsed)
    }

    // ─── Hulls intersect ───────────────────────────────────────────

    @Test
    fun `hullsIntersect returns nonzero for overlapping spans`() {
        val curve1 = quadCurve()
        val curve2 = SkTQuad(SkDQuad(arrayOf(
            SkDPoint(20.0, 20.0), SkDPoint(50.0, 80.0), SkDPoint(80.0, 20.0),
        )))
        val a = SkTSpan(curve1); a.init(curve1)
        val b = SkTSpan(curve2); b.init(curve2)
        val start = booleanArrayOf(false); val oppStart = booleanArrayOf(false)
        val r = a.hullsIntersect(b, start, oppStart)
        assertTrue(r != 0) { "expected non-zero hull intersection, got $r" }
    }

    @Test
    fun `hullsIntersect returns 0 for disjoint bounds`() {
        val curve1 = quadCurve()
        val curve2 = SkTQuad(SkDQuad(arrayOf(
            SkDPoint(500.0, 500.0), SkDPoint(550.0, 600.0), SkDPoint(600.0, 500.0),
        )))
        val a = SkTSpan(curve1); a.init(curve1)
        val b = SkTSpan(curve2); b.init(curve2)
        val start = booleanArrayOf(false); val oppStart = booleanArrayOf(false)
        assertEquals(0, a.hullsIntersect(b, start, oppStart))
    }
}
