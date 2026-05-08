package org.skia.pathops.internal

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.math.SkPoint

/**
 * Unit tests for [SkCoincidentSpans] data model + simple methods, plus
 * [SkOpCoincidence.Ordered] (Phase D1.2.g.0).
 */
class SkOpCoincidenceTest {

    private fun pt(x: Float, y: Float) = SkPoint(fX = x, fY = y)

    // ─── SkOpCoincidence skeleton ──────────────────────────────────

    @Test
    fun `default SkOpCoincidence has null head and tip`() {
        val c = SkOpCoincidence()
        assertTrue(c.isEmpty())
        assertNull(c.fHead)
        assertNull(c.fTop)
    }

    // ─── SkCoincidentSpans data model ──────────────────────────────

    @Test
    fun `default SkCoincidentSpans has null endpoints`() {
        val s = SkCoincidentSpans()
        assertNull(s.coinPtTStart())
        assertNull(s.coinPtTEnd())
        assertNull(s.oppPtTStart())
        assertNull(s.oppPtTEnd())
        assertNull(s.next())
        // flipped() with both null pts is `0.0 > 0.0` → false.
        assertFalse(s.flipped())
    }

    @Test
    fun `set canonicalises starts and ends and marks pt-Ts coincident`() {
        // Two collinear lines forming a coincidence pair.
        val a = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        val b = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        val s = SkCoincidentSpans()
        s.set(null, a.fHead.ptT(), a.fTail.ptT(), b.fHead.ptT(), b.fTail.ptT())
        assertSame(a.fHead.ptT(), s.coinPtTStart())
        assertSame(a.fTail.ptT(), s.coinPtTEnd())
        assertSame(b.fHead.ptT(), s.oppPtTStart())
        assertSame(b.fTail.ptT(), s.oppPtTEnd())
        // setXxx implementations call ptT.setCoincident().
        assertTrue(a.fHead.ptT().coincident())
        assertTrue(b.fTail.ptT().coincident())
    }

    @Test
    fun `flipped is true when oppPtTStart_t is greater than oppPtTEnd_t`() {
        val a = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        val b = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        val s = SkCoincidentSpans()
        // Pass opp endpoints in reverse t order.
        s.set(null, a.fHead.ptT(), a.fTail.ptT(), b.fTail.ptT(), b.fHead.ptT())
        assertTrue(s.flipped())
    }

    @Test
    fun `contains is true when both points are inside the coin range`() {
        val a = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        val b = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        val s = SkCoincidentSpans()
        s.set(null, a.fHead.ptT(), a.fTail.ptT(), b.fHead.ptT(), b.fTail.ptT())
        // Both endpoints are inside [0..1].
        assertTrue(s.contains(a.fHead.ptT(), a.fTail.ptT()))
        assertTrue(s.contains(b.fHead.ptT(), b.fTail.ptT()))
    }

    @Test
    fun `extend returns false when range already covers given pt-Ts`() {
        val a = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        val b = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        val s = SkCoincidentSpans()
        s.set(null, a.fHead.ptT(), a.fTail.ptT(), b.fHead.ptT(), b.fTail.ptT())
        // Identical extend → no-op.
        assertFalse(s.extend(a.fHead.ptT(), a.fTail.ptT(), b.fHead.ptT(), b.fTail.ptT()))
    }

    // ─── Ordered ──────────────────────────────────────────────────

    @Test
    fun `Ordered returns true when coinSeg verb is lower than oppSeg verb`() {
        val line = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        val quad = SkOpSegment().addQuad(arrayOf(pt(0f, 0f), pt(5f, 5f), pt(10f, 0f)), null)
        // SegVerb.kLine.ordinal < SegVerb.kQuad.ordinal → line < quad.
        assertTrue(SkOpCoincidence.Ordered(line, quad))
        assertFalse(SkOpCoincidence.Ordered(quad, line))
    }

    @Test
    fun `Ordered breaks ties by control-point coordinates`() {
        val a = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        val b = SkOpSegment().addLine(arrayOf(pt(1f, 0f), pt(11f, 0f)), null)
        // Same verb ; first coord 0 < 1 → a < b.
        assertTrue(SkOpCoincidence.Ordered(a, b))
        assertFalse(SkOpCoincidence.Ordered(b, a))
    }

    @Test
    fun `Ordered returns true when both segments are identical`() {
        val a = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        // Same verb, same coords → returns true (the "all-equal" sentinel).
        assertTrue(SkOpCoincidence.Ordered(a, a))
    }

    @Test
    fun `Ordered pt-T overload delegates to the segment overload`() {
        val line = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        val quad = SkOpSegment().addQuad(arrayOf(pt(0f, 0f), pt(5f, 5f), pt(10f, 0f)), null)
        assertTrue(SkOpCoincidence.Ordered(line.fHead.ptT(), quad.fHead.ptT()))
    }
}
