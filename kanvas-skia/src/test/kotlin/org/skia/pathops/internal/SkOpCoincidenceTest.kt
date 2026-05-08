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

    // ─── release(SkOpSegment) (D1.2.g.b) ──────────────────────────

    @Test
    fun `release of a touched segment unlinks the entry`() {
        val a = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        val b = SkOpSegment().addLine(arrayOf(pt(1f, 0f), pt(11f, 0f)), null)
        val c = SkOpCoincidence()
        c.add(a.fHead.ptT(), a.fTail.ptT(), b.fHead.ptT(), b.fTail.ptT())
        assertTrue(c.fHead != null)
        c.release(a)
        assertNull(c.fHead)
    }

    @Test
    fun `release of an untouched segment is a no-op`() {
        val a = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        val b = SkOpSegment().addLine(arrayOf(pt(1f, 0f), pt(11f, 0f)), null)
        val x = SkOpSegment().addLine(arrayOf(pt(2f, 0f), pt(12f, 0f)), null)
        val c = SkOpCoincidence()
        c.add(a.fHead.ptT(), a.fTail.ptT(), b.fHead.ptT(), b.fTail.ptT())
        c.release(x)
        assertTrue(c.fHead != null)
    }

    @Test
    fun `release of opp segment also unlinks`() {
        val a = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        val b = SkOpSegment().addLine(arrayOf(pt(1f, 0f), pt(11f, 0f)), null)
        val c = SkOpCoincidence()
        c.add(a.fHead.ptT(), a.fTail.ptT(), b.fHead.ptT(), b.fTail.ptT())
        // b lives on the opp side of the entry, should still match.
        c.release(b)
        assertNull(c.fHead)
    }

    // ─── releaseDeleted (D1.2.g.b) ────────────────────────────────

    @Test
    fun `releaseDeleted prunes entries whose coinPtTStart is deleted`() {
        val a = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        val b = SkOpSegment().addLine(arrayOf(pt(1f, 0f), pt(11f, 0f)), null)
        val c = SkOpCoincidence()
        c.add(a.fHead.ptT(), a.fTail.ptT(), b.fHead.ptT(), b.fTail.ptT())
        // Mark the coin-side start pt-T deleted (a.fHead.ptT()).
        c.fHead!!.coinPtTStart()!!.setDeleted()
        c.releaseDeleted()
        assertNull(c.fHead)
    }

    @Test
    fun `releaseDeleted leaves alive entries intact`() {
        val a = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        val b = SkOpSegment().addLine(arrayOf(pt(1f, 0f), pt(11f, 0f)), null)
        val c = SkOpCoincidence()
        c.add(a.fHead.ptT(), a.fTail.ptT(), b.fHead.ptT(), b.fTail.ptT())
        c.releaseDeleted()
        assertTrue(c.fHead != null)
    }

    @Test
    fun `releaseDeleted prunes from the middle of a chain`() {
        val a1 = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        val b1 = SkOpSegment().addLine(arrayOf(pt(1f, 0f), pt(11f, 0f)), null)
        val a2 = SkOpSegment().addLine(arrayOf(pt(2f, 0f), pt(12f, 0f)), null)
        val b2 = SkOpSegment().addLine(arrayOf(pt(3f, 0f), pt(13f, 0f)), null)
        val a3 = SkOpSegment().addLine(arrayOf(pt(4f, 0f), pt(14f, 0f)), null)
        val b3 = SkOpSegment().addLine(arrayOf(pt(5f, 0f), pt(15f, 0f)), null)
        val c = SkOpCoincidence()
        c.add(a1.fHead.ptT(), a1.fTail.ptT(), b1.fHead.ptT(), b1.fTail.ptT())
        c.add(a2.fHead.ptT(), a2.fTail.ptT(), b2.fHead.ptT(), b2.fTail.ptT())
        c.add(a3.fHead.ptT(), a3.fTail.ptT(), b3.fHead.ptT(), b3.fTail.ptT())
        // Middle entry (most recently added is at fHead.next, since prepend).
        c.fHead!!.next()!!.coinPtTStart()!!.setDeleted()
        c.releaseDeleted()
        // Two entries remain : the head (a3, b3) and the tail (a1, b1).
        assertSame(a3.fHead.ptT(), c.fHead!!.coinPtTStart())
        assertSame(a1.fHead.ptT(), c.fHead!!.next()!!.coinPtTStart())
        assertNull(c.fHead!!.next()!!.next())
    }

    // ─── restoreHead (D1.2.g.b) ───────────────────────────────────

    @Test
    fun `restoreHead splices fTop onto end of fHead and clears fTop`() {
        val a = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        val b = SkOpSegment().addLine(arrayOf(pt(1f, 0f), pt(11f, 0f)), null)
        val x = SkOpSegment().addLine(arrayOf(pt(2f, 0f), pt(12f, 0f)), null)
        val y = SkOpSegment().addLine(arrayOf(pt(3f, 0f), pt(13f, 0f)), null)
        val c = SkOpCoincidence()
        c.add(a.fHead.ptT(), a.fTail.ptT(), b.fHead.ptT(), b.fTail.ptT())
        // Park a separate entry on fTop (manually — restoreHead is the
        // only public consumer of fTop in this slice).
        val parked = SkCoincidentSpans()
        parked.set(null, x.fHead.ptT(), x.fTail.ptT(), y.fHead.ptT(), y.fTail.ptT())
        c.fTop = parked
        c.restoreHead()
        assertNull(c.fTop)
        // fHead retains its (a, b) entry, with parked appended.
        assertSame(a.fHead.ptT(), c.fHead!!.coinPtTStart())
        assertSame(parked, c.fHead!!.next())
    }

    @Test
    fun `restoreHead prunes entries whose coin segment is done`() {
        val a = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        val b = SkOpSegment().addLine(arrayOf(pt(1f, 0f), pt(11f, 0f)), null)
        val c = SkOpCoincidence()
        c.add(a.fHead.ptT(), a.fTail.ptT(), b.fHead.ptT(), b.fTail.ptT())
        a.markAllDone()
        assertTrue(a.done())
        c.restoreHead()
        assertNull(c.fHead)
    }

    @Test
    fun `restoreHead with empty fHead promotes fTop directly`() {
        val a = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        val b = SkOpSegment().addLine(arrayOf(pt(1f, 0f), pt(11f, 0f)), null)
        val c = SkOpCoincidence()
        val parked = SkCoincidentSpans()
        parked.set(null, a.fHead.ptT(), a.fTail.ptT(), b.fHead.ptT(), b.fTail.ptT())
        c.fTop = parked
        c.restoreHead()
        assertSame(parked, c.fHead)
        assertNull(c.fTop)
    }

    // ─── fixUp (D1.2.g.b) ─────────────────────────────────────────

    @Test
    fun `fixUp rewires endpoint when no collapse occurs`() {
        // Build (a, b) coincidence ; fix up b.fHead.ptT() → some other
        // pt-T living on a different span (b.fTail.ptT()'s span ≠
        // b.fHead.ptT()'s span, so no collapse).
        val a = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        val b = SkOpSegment().addLine(arrayOf(pt(1f, 0f), pt(11f, 0f)), null)
        val x = SkOpSegment().addLine(arrayOf(pt(2f, 0f), pt(12f, 0f)), null)
        val c = SkOpCoincidence()
        c.add(a.fHead.ptT(), a.fTail.ptT(), b.fHead.ptT(), b.fTail.ptT())
        val deleted = b.fHead.ptT()
        val kept = x.fHead.ptT() // span differs from b.fTail.ptT's span
        c.fixUp(deleted, kept)
        // oppPtTStart should now be `kept`.
        assertSame(kept, c.fHead!!.oppPtTStart())
    }

    @Test
    fun `fixUp releases entry when replacement collapses the range`() {
        val a = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        val b = SkOpSegment().addLine(arrayOf(pt(1f, 0f), pt(11f, 0f)), null)
        val c = SkOpCoincidence()
        c.add(a.fHead.ptT(), a.fTail.ptT(), b.fHead.ptT(), b.fTail.ptT())
        // Collapse : redirect b.fHead.ptT() (oppPtTStart) to a pt-T
        // whose span is the same as oppPtTEnd's span — i.e. b.fTail.
        c.fixUp(b.fHead.ptT(), b.fTail.ptT())
        assertNull(c.fHead)
    }

    // ─── markCollapsed (D1.2.g.b) ─────────────────────────────────

    @Test
    fun `markCollapsed leaves entry alive when test is not an endpoint`() {
        val a = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        val b = SkOpSegment().addLine(arrayOf(pt(1f, 0f), pt(11f, 0f)), null)
        val x = SkOpSegment().addLine(arrayOf(pt(2f, 0f), pt(12f, 0f)), null)
        val c = SkOpCoincidence()
        c.add(a.fHead.ptT(), a.fTail.ptT(), b.fHead.ptT(), b.fTail.ptT())
        c.markCollapsed(x.fHead.ptT())
        assertTrue(c.fHead != null)
    }

    @Test
    fun `markCollapsed releases entry whose endpoint loop already contains test`() {
        val a = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        val b = SkOpSegment().addLine(arrayOf(pt(1f, 0f), pt(11f, 0f)), null)
        val c = SkOpCoincidence()
        c.add(a.fHead.ptT(), a.fTail.ptT(), b.fHead.ptT(), b.fTail.ptT())
        // Splice a.fHead.ptT() into a.fTail.ptT()'s opp loop so that
        // collapsed(a.fHead.ptT()) is true on the (a.fHead, a.fTail)
        // coin-side : fCoinPtTStart === test && fCoinPtTEnd.contains(test).
        a.fTail.ptT().insert(a.fHead.ptT())
        c.markCollapsed(a.fHead.ptT())
        assertNull(c.fHead)
    }

    // ─── overlap (D1.2.g.c.1) ─────────────────────────────────────

    /** Build a free-floating pt-T at [t] sharing [span]'s segment. */
    private fun stubPtT(span: SkOpSpanBase, t: Double): SkOpPtT {
        val p = SkOpPtT()
        p.fT = t
        p.fSpan = span
        p.fNext = p
        return p
    }

    @Test
    fun `overlap returns true and writes the t-intersection`() {
        val a = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        val s1 = stubPtT(a.fHead, 0.1)
        val e1 = stubPtT(a.fHead, 0.6)
        val s2 = stubPtT(a.fHead, 0.4)
        val e2 = stubPtT(a.fHead, 0.9)
        val c = SkOpCoincidence()
        val out = DoubleArray(2)
        assertTrue(c.overlap(s1, e1, s2, e2, out))
        assertEquals(0.4, out[0], 1e-12)
        assertEquals(0.6, out[1], 1e-12)
    }

    @Test
    fun `overlap returns false on disjoint ranges`() {
        val a = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        val s1 = stubPtT(a.fHead, 0.0)
        val e1 = stubPtT(a.fHead, 0.3)
        val s2 = stubPtT(a.fHead, 0.5)
        val e2 = stubPtT(a.fHead, 0.9)
        val c = SkOpCoincidence()
        val out = DoubleArray(2)
        assertFalse(c.overlap(s1, e1, s2, e2, out))
    }

    @Test
    fun `overlap handles ranges given in reverse t order`() {
        val a = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        // First range : 0.6 → 0.1 (reverse).
        val s1 = stubPtT(a.fHead, 0.6)
        val e1 = stubPtT(a.fHead, 0.1)
        val s2 = stubPtT(a.fHead, 0.4)
        val e2 = stubPtT(a.fHead, 0.9)
        val c = SkOpCoincidence()
        val out = DoubleArray(2)
        assertTrue(c.overlap(s1, e1, s2, e2, out))
        assertEquals(0.4, out[0], 1e-12)
        assertEquals(0.6, out[1], 1e-12)
    }

    // ─── TRange (D1.2.g.c.1) ──────────────────────────────────────

    @Test
    fun `TRange returns sentinel 1 when no bracket can be found`() {
        // overS lives on `over` but `over`'s spans don't reference coin.
        val over = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        val coin = SkOpSegment().addLine(arrayOf(pt(0f, 5f), pt(10f, 5f)), null)
        assertEquals(1.0, SkOpCoincidence.TRange(over.fHead.ptT(), 0.5, coin), 1e-12)
    }

    @Test
    fun `TRange linearly maps t between two bracketing pt-Ts`() {
        val over = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        val coin = SkOpSegment().addLine(arrayOf(pt(20f, 0f), pt(30f, 0f)), null)
        // Splice : `over`'s spans now each contain a `coin` pt-T.
        over.fHead.ptT().insert(coin.fHead.ptT())
        over.fTail.ptT().insert(coin.fTail.ptT())
        // `over` runs [0..1] and `coin` also [0..1] — identity map.
        assertEquals(0.5, SkOpCoincidence.TRange(over.fHead.ptT(), 0.5, coin), 1e-12)
        assertEquals(0.0, SkOpCoincidence.TRange(over.fHead.ptT(), 0.0, coin), 1e-12)
        assertEquals(1.0, SkOpCoincidence.TRange(over.fHead.ptT(), 1.0, coin), 1e-12)
    }

    @Test
    fun `TRange remaps onto a different coin t-range`() {
        val over = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        val coin = SkOpSegment().addLine(arrayOf(pt(20f, 0f), pt(30f, 0f)), null)
        // Force the `coin`-side endpoints to non-(0,1) t-values via stub
        // pt-Ts. The mapping is then [0..1] (over) → [0.2..0.8] (coin).
        val coinStartStub = stubPtT(coin.fHead, 0.2)
        val coinEndStub = stubPtT(coin.fTail, 0.8)
        over.fHead.ptT().insert(coinStartStub)
        over.fTail.ptT().insert(coinEndStub)
        assertEquals(0.5, SkOpCoincidence.TRange(over.fHead.ptT(), 0.5, coin), 1e-12)
        // sRatio = (0.25 - 0) / (1 - 0) = 0.25 → 0.2 + (0.8 - 0.2) * 0.25 = 0.35
        assertEquals(0.35, SkOpCoincidence.TRange(over.fHead.ptT(), 0.25, coin), 1e-12)
    }

    // ─── checkOverlap (D1.2.g.c.1) ────────────────────────────────

    /**
     * Build a synthetic [SkCoincidentSpans] with [fT]-stubbed
     * endpoints. Allows checkOverlap tests to exercise partial-
     * overlap logic without porting `SkOpSegment.addT`.
     */
    private fun stubEntry(
        coinSeg: SkOpSegment, oppSeg: SkOpSegment,
        coinTs: Double, coinTe: Double,
        oppTs: Double, oppTe: Double,
    ): SkCoincidentSpans {
        val s = SkCoincidentSpans()
        s.set(
            null,
            stubPtT(coinSeg.fHead, coinTs),
            stubPtT(coinSeg.fTail, coinTe),
            stubPtT(oppSeg.fHead, oppTs),
            stubPtT(oppSeg.fTail, oppTe),
        )
        return s
    }

    @Test
    fun `checkOverlap on empty chain returns true with no overlaps`() {
        val a = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        val b = SkOpSegment().addLine(arrayOf(pt(1f, 0f), pt(11f, 0f)), null)
        val c = SkOpCoincidence()
        val overlaps = mutableListOf<SkCoincidentSpans>()
        assertTrue(c.checkOverlap(null, a, b, 0.0, 1.0, 0.0, 1.0, overlaps))
        assertTrue(overlaps.isEmpty())
    }

    @Test
    fun `checkOverlap returns false when candidate is fully inside an entry`() {
        val a = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        val b = SkOpSegment().addLine(arrayOf(pt(1f, 0f), pt(11f, 0f)), null)
        val c = SkOpCoincidence()
        val entry = stubEntry(a, b, 0.0, 1.0, 0.0, 1.0)
        val overlaps = mutableListOf<SkCoincidentSpans>()
        // Candidate [0.2..0.5] / [0.2..0.5] is fully inside [0..1] / [0..1].
        assertFalse(c.checkOverlap(entry, a, b, 0.2, 0.5, 0.2, 0.5, overlaps))
    }

    @Test
    fun `checkOverlap appends partially-overlapping entries`() {
        val a = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        val b = SkOpSegment().addLine(arrayOf(pt(1f, 0f), pt(11f, 0f)), null)
        val c = SkOpCoincidence()
        val entry = stubEntry(a, b, 0.2, 0.7, 0.2, 0.7)
        val overlaps = mutableListOf<SkCoincidentSpans>()
        // Candidate [0.0..0.4] / [0.0..0.4] partially overlaps
        // [0.2..0.7] / [0.2..0.7] : straddles the start.
        assertTrue(c.checkOverlap(entry, a, b, 0.0, 0.4, 0.0, 0.4, overlaps))
        assertEquals(1, overlaps.size)
        assertSame(entry, overlaps[0])
    }

    @Test
    fun `checkOverlap skips entries on a different segment pair`() {
        val a = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        val b = SkOpSegment().addLine(arrayOf(pt(1f, 0f), pt(11f, 0f)), null)
        val x = SkOpSegment().addLine(arrayOf(pt(2f, 0f), pt(12f, 0f)), null)
        val y = SkOpSegment().addLine(arrayOf(pt(3f, 0f), pt(13f, 0f)), null)
        val c = SkOpCoincidence()
        // Entry on (x, y) — checkOverlap is for (a, b).
        val entry = stubEntry(x, y, 0.0, 1.0, 0.0, 1.0)
        val overlaps = mutableListOf<SkCoincidentSpans>()
        assertTrue(c.checkOverlap(entry, a, b, 0.0, 1.0, 0.0, 1.0, overlaps))
        assertTrue(overlaps.isEmpty())
    }

    // ─── addOrOverlap (D1.2.g.c.4) ────────────────────────────────

    @Test
    fun `addOrOverlap returns false when fTop is null`() {
        val a = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        val b = SkOpSegment().addLine(arrayOf(pt(1f, 0f), pt(11f, 0f)), null)
        val c = SkOpCoincidence()
        val addedOut = booleanArrayOf(false)
        assertFalse(c.addOrOverlap(a, b, 0.0, 1.0, 0.0, 1.0, addedOut))
        assertFalse(addedOut[0])
    }

    @Test
    fun `addOrOverlap on fresh segment pair adds a new entry on fHead`() {
        val a = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        val b = SkOpSegment().addLine(arrayOf(pt(1f, 0f), pt(11f, 0f)), null)
        // Park a dummy entry on fTop on a different (x, y) pair so
        // checkOverlap has something to walk past.
        val x = SkOpSegment().addLine(arrayOf(pt(20f, 0f), pt(30f, 0f)), null)
        val y = SkOpSegment().addLine(arrayOf(pt(21f, 0f), pt(31f, 0f)), null)
        val c = SkOpCoincidence()
        c.fTop = stubEntry(x, y, 0.0, 1.0, 0.0, 1.0)
        val addedOut = booleanArrayOf(false)
        assertTrue(c.addOrOverlap(a, b, 0.0, 1.0, 0.0, 1.0, addedOut))
        assertTrue(addedOut[0])
        assertSame(a.fHead.ptT(), c.fHead!!.coinPtTStart())
        assertSame(a.fTail.ptT(), c.fHead!!.coinPtTEnd())
        assertSame(b.fHead.ptT(), c.fHead!!.oppPtTStart())
        assertSame(b.fTail.ptT(), c.fHead!!.oppPtTEnd())
    }

    @Test
    fun `addOrOverlap returns true and skips when range fully inside an existing fHead entry`() {
        val a = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        val b = SkOpSegment().addLine(arrayOf(pt(1f, 0f), pt(11f, 0f)), null)
        val x = SkOpSegment().addLine(arrayOf(pt(20f, 0f), pt(30f, 0f)), null)
        val y = SkOpSegment().addLine(arrayOf(pt(21f, 0f), pt(31f, 0f)), null)
        val c = SkOpCoincidence()
        c.fTop = stubEntry(x, y, 0.0, 1.0, 0.0, 1.0)
        // Pre-existing fHead entry on (a, b) covering [0..1].
        c.fHead = stubEntry(a, b, 0.0, 1.0, 0.0, 1.0)
        val addedOut = booleanArrayOf(false)
        // Candidate [0.2..0.5] is fully inside [0..1] → checkOverlap
        // bails out, addOrOverlap returns true with addedOut[0] still false.
        assertTrue(c.addOrOverlap(a, b, 0.2, 0.5, 0.2, 0.5, addedOut))
        assertFalse(addedOut[0])
    }

    // ─── addIfMissing (D1.2.g.c.4) ────────────────────────────────

    @Test
    fun `addIfMissing returns true on coin-side collapsed-range short-circuit`() {
        // Set up : `over` segment whose head/tail spans don't contain
        // either coinSeg or oppSeg → TRange returns the sentinel `1`
        // for both coinTs and coinTe → coinSeg.collapsed sees a
        // (1.0, 1.0) range and reports kYes (both endpoints fall on
        // the only loop entry's t).
        val over = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        val coinSeg = SkOpSegment().addLine(arrayOf(pt(0f, 5f), pt(10f, 5f)), null)
        val oppSeg = SkOpSegment().addLine(arrayOf(pt(0f, 9f), pt(10f, 9f)), null)
        val c = SkOpCoincidence()
        val addedOut = booleanArrayOf(false)
        // tStart < tEnd is required ; use 0.2..0.5.
        // TRange returns 1 for both → collapsed(1,1) on a fresh line
        // returns kNo (no entry brackets [1..1]) — so we expect the
        // routine to fall through to addOrOverlap, which fails on
        // fTop == null and returns true (cf. addOrOverlap path).
        assertTrue(c.addIfMissing(over.fHead.ptT(), over.fHead.ptT(),
            0.2, 0.5, coinSeg, oppSeg, addedOut))
        assertFalse(addedOut[0])
    }

    @Test
    fun `addIfMissing falls through to addOrOverlap on a non-collapsed remap`() {
        // over runs [0..1] on (0,0)→(10,0).
        // Splice coinSeg pt-Ts into over's spans so TRange identity-maps.
        val over = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        val coinSeg = SkOpSegment().addLine(arrayOf(pt(0f, 5f), pt(10f, 5f)), null)
        val oppSeg = SkOpSegment().addLine(arrayOf(pt(0f, 9f), pt(10f, 9f)), null)
        // Splice coinSeg head/tail into over's head/tail so
        // TRange(over.fHead, t, coinSeg) == t.
        over.fHead.ptT().insert(coinSeg.fHead.ptT())
        over.fTail.ptT().insert(coinSeg.fTail.ptT())
        // Same for oppSeg.
        over.fHead.ptT().insert(oppSeg.fHead.ptT())
        over.fTail.ptT().insert(oppSeg.fTail.ptT())
        // Park a dummy fTop so addOrOverlap doesn't immediately fail.
        val x = SkOpSegment().addLine(arrayOf(pt(40f, 0f), pt(50f, 0f)), null)
        val y = SkOpSegment().addLine(arrayOf(pt(41f, 0f), pt(51f, 0f)), null)
        val c = SkOpCoincidence()
        c.fTop = stubEntry(x, y, 0.0, 1.0, 0.0, 1.0)
        val addedOut = booleanArrayOf(false)
        assertTrue(c.addIfMissing(over.fHead.ptT(), over.fHead.ptT(),
            0.0, 1.0, coinSeg, oppSeg, addedOut))
        // A fresh coincidence pair (coinSeg, oppSeg) should now be on fHead.
        assertTrue(addedOut[0])
        assertSame(coinSeg.fHead.ptT(), c.fHead!!.coinPtTStart())
        assertSame(oppSeg.fHead.ptT(), c.fHead!!.oppPtTStart())
    }
}
