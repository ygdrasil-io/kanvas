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

    // ─── add (D1.2.g.a) ───────────────────────────────────────────

    @Test
    fun `add pushes a new entry on fHead in canonical order`() {
        // a < b lex (0,0)→(10,0) vs (1,0)→(11,0) — Ordered(a, b) = true.
        val a = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        val b = SkOpSegment().addLine(arrayOf(pt(1f, 0f), pt(11f, 0f)), null)
        val c = SkOpCoincidence()
        c.add(a.fHead.ptT(), a.fTail.ptT(), b.fHead.ptT(), b.fTail.ptT())
        val head = c.fHead!!
        assertSame(a.fHead.ptT(), head.coinPtTStart())
        assertSame(a.fTail.ptT(), head.coinPtTEnd())
        assertSame(b.fHead.ptT(), head.oppPtTStart())
        assertSame(b.fTail.ptT(), head.oppPtTEnd())
        assertNull(head.next())
    }

    @Test
    fun `add canonicalises when caller passes args in non-Ordered order`() {
        // Caller passes b as coin even though b > a lex. Implementation
        // must recurse and end up storing a as coin.
        val a = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        val b = SkOpSegment().addLine(arrayOf(pt(1f, 0f), pt(11f, 0f)), null)
        val c = SkOpCoincidence()
        c.add(b.fHead.ptT(), b.fTail.ptT(), a.fHead.ptT(), a.fTail.ptT())
        val head = c.fHead!!
        assertSame(a.fHead.ptT(), head.coinPtTStart())
        assertSame(b.fHead.ptT(), head.oppPtTStart())
    }

    @Test
    fun `add prepends so most recent entry is at fHead`() {
        val a1 = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        val a2 = SkOpSegment().addLine(arrayOf(pt(1f, 0f), pt(11f, 0f)), null)
        val b1 = SkOpSegment().addLine(arrayOf(pt(2f, 0f), pt(12f, 0f)), null)
        val b2 = SkOpSegment().addLine(arrayOf(pt(3f, 0f), pt(13f, 0f)), null)
        val c = SkOpCoincidence()
        c.add(a1.fHead.ptT(), a1.fTail.ptT(), a2.fHead.ptT(), a2.fTail.ptT())
        c.add(b1.fHead.ptT(), b1.fTail.ptT(), b2.fHead.ptT(), b2.fTail.ptT())
        // Most recent (b1, b2) is at the front.
        assertSame(b1.fHead.ptT(), c.fHead!!.coinPtTStart())
        assertSame(a1.fHead.ptT(), c.fHead!!.next()!!.coinPtTStart())
    }

    // ─── extend (D1.2.g.a) ────────────────────────────────────────

    @Test
    fun `extend returns false when fHead is empty`() {
        val a = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        val b = SkOpSegment().addLine(arrayOf(pt(1f, 0f), pt(11f, 0f)), null)
        val c = SkOpCoincidence()
        assertFalse(c.extend(a.fHead.ptT(), a.fTail.ptT(), b.fHead.ptT(), b.fTail.ptT()))
    }

    @Test
    fun `extend returns false when no entry has matching segment pair`() {
        val a = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        val b = SkOpSegment().addLine(arrayOf(pt(1f, 0f), pt(11f, 0f)), null)
        val x = SkOpSegment().addLine(arrayOf(pt(2f, 0f), pt(12f, 0f)), null)
        val y = SkOpSegment().addLine(arrayOf(pt(3f, 0f), pt(13f, 0f)), null)
        val c = SkOpCoincidence()
        c.add(a.fHead.ptT(), a.fTail.ptT(), b.fHead.ptT(), b.fTail.ptT())
        assertFalse(c.extend(x.fHead.ptT(), x.fTail.ptT(), y.fHead.ptT(), y.fTail.ptT()))
    }

    @Test
    fun `extend returns true when an entry with the same segment pair overlaps`() {
        val a = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        val b = SkOpSegment().addLine(arrayOf(pt(1f, 0f), pt(11f, 0f)), null)
        val c = SkOpCoincidence()
        c.add(a.fHead.ptT(), a.fTail.ptT(), b.fHead.ptT(), b.fTail.ptT())
        // Re-extending against the same pair overlaps trivially.
        assertTrue(c.extend(a.fHead.ptT(), a.fTail.ptT(), b.fHead.ptT(), b.fTail.ptT()))
    }

    // ─── contains (D1.2.g.a) ──────────────────────────────────────

    @Test
    fun `contains by segment-pair-and-T returns false on empty container`() {
        val a = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        val b = SkOpSegment().addLine(arrayOf(pt(1f, 0f), pt(11f, 0f)), null)
        val c = SkOpCoincidence()
        assertFalse(c.contains(a, b, 0.5))
    }

    @Test
    fun `contains by segment-pair-and-T finds entry on either side`() {
        val a = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        val b = SkOpSegment().addLine(arrayOf(pt(1f, 0f), pt(11f, 0f)), null)
        val c = SkOpCoincidence()
        c.add(a.fHead.ptT(), a.fTail.ptT(), b.fHead.ptT(), b.fTail.ptT())
        // Direct (coin = a, opp = b) match.
        assertTrue(c.contains(a, b, 0.5))
        // Swapped (coin = b, opp = a) — second branch of contains.
        assertTrue(c.contains(b, a, 0.5))
        // Out of range.
        assertFalse(c.contains(a, b, 1.5))
    }

    @Test
    fun `contains by four pt-Ts returns true when entry covers the range`() {
        val a = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        val b = SkOpSegment().addLine(arrayOf(pt(1f, 0f), pt(11f, 0f)), null)
        val c = SkOpCoincidence()
        c.add(a.fHead.ptT(), a.fTail.ptT(), b.fHead.ptT(), b.fTail.ptT())
        // Same range — trivially contained.
        assertTrue(c.contains(a.fHead.ptT(), a.fTail.ptT(), b.fHead.ptT(), b.fTail.ptT()))
    }

    @Test
    fun `contains by four pt-Ts returns false when fHead is empty`() {
        val a = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        val b = SkOpSegment().addLine(arrayOf(pt(1f, 0f), pt(11f, 0f)), null)
        val c = SkOpCoincidence()
        assertFalse(c.contains(a.fHead.ptT(), a.fTail.ptT(), b.fHead.ptT(), b.fTail.ptT()))
    }

    @Test
    fun `contains by four pt-Ts returns false when segment pair differs`() {
        val a = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        val b = SkOpSegment().addLine(arrayOf(pt(1f, 0f), pt(11f, 0f)), null)
        val x = SkOpSegment().addLine(arrayOf(pt(2f, 0f), pt(12f, 0f)), null)
        val y = SkOpSegment().addLine(arrayOf(pt(3f, 0f), pt(13f, 0f)), null)
        val c = SkOpCoincidence()
        c.add(a.fHead.ptT(), a.fTail.ptT(), b.fHead.ptT(), b.fTail.ptT())
        assertFalse(c.contains(x.fHead.ptT(), x.fTail.ptT(), y.fHead.ptT(), y.fTail.ptT()))
    }
}
