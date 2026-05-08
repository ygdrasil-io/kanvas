package org.skia.pathops.internal

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.math.SkPoint

/**
 * Unit tests for [SkOpPtT] / [SkOpSpanBase] / [SkOpSpan] data model
 * (Phase D1.2.a).
 */
class SkOpSpanTest {

    private fun pt(x: Float, y: Float) = SkPoint(fX = x, fY = y)

    // ─── SkOpPtT ───────────────────────────────────────────────────

    @Test
    fun `SkOpPtT init populates t, pt, span, and self-loops fNext`() {
        val span = SkOpSpanBase()
        val ptt = SkOpPtT()
        ptt.init(span, 0.5, pt(1f, 2f), false)
        assertEquals(0.5, ptt.fT)
        assertEquals(pt(1f, 2f), ptt.fPt)
        assertSame(span, ptt.span())
        assertSame(ptt, ptt.next()) // self-loop initially
        assertFalse(ptt.deleted())
        assertFalse(ptt.duplicate())
        assertFalse(ptt.coincident())
    }

    @Test
    fun `setCoincident flips the flag and setDeleted asserts`() {
        val span = SkOpSpanBase()
        val ptt = SkOpPtT()
        ptt.init(span, 0.5, pt(0f, 0f), false)
        ptt.setCoincident()
        assertTrue(ptt.coincident())
        ptt.setDeleted()
        assertTrue(ptt.deleted())
        // Once deleted, setCoincident should fail.
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException::class.java) {
            ptt.setCoincident()
        }
    }

    @Test
    fun `insert splices a new entry into the loop`() {
        val span = SkOpSpanBase()
        val a = SkOpPtT(); a.init(span, 0.0, pt(0f, 0f), false)
        val b = SkOpPtT(); b.init(span, 0.5, pt(0f, 0f), false)
        a.insert(b)
        assertSame(b, a.next())
        assertSame(a, b.next()) // b's next was set to a's old next (which was a)
    }

    @Test
    fun `addOpp splices opp loop with given oppPrev`() {
        // Build two single-entry loops and splice them.
        val span1 = SkOpSpanBase(); val span2 = SkOpSpanBase()
        val a = SkOpPtT(); a.init(span1, 0.0, pt(0f, 0f), false)
        val b = SkOpPtT(); b.init(span2, 0.0, pt(0f, 0f), false)
        // a's loop is [a → a], b's loop is [b → b].
        // oppPrev for splicing : in upstream this is b.fNext (= b).
        a.addOpp(b, b)
        // After addOpp : a → b → a (single loop).
        assertSame(b, a.next())
        assertSame(a, b.next())
    }

    @Test
    fun `oppPrev returns null when the opp loop closes back to this`() {
        val span = SkOpSpanBase()
        val a = SkOpPtT(); a.init(span, 0.0, pt(0f, 0f), false)
        val b = SkOpPtT(); b.init(span, 0.5, pt(0f, 0f), false)
        a.addOpp(b, b)
        // Now a → b → a → b → … . oppPrev(b) walks from b.next = a — equals this → return null.
        assertNull(a.oppPrev(b))
    }

    @Test
    fun `starter returns the entry with smaller fT`() {
        val span = SkOpSpanBase()
        val a = SkOpPtT(); a.init(span, 0.25, pt(0f, 0f), false)
        val b = SkOpPtT(); b.init(span, 0.75, pt(0f, 0f), false)
        assertSame(a, a.starter(b))
        assertSame(a, b.starter(a))
    }

    @Test
    fun `Overlaps detects overlapping ranges and computes (sOut, eOut)`() {
        val span = SkOpSpanBase()
        val s1 = SkOpPtT(); s1.init(span, 0.0, pt(0f, 0f), false)
        val e1 = SkOpPtT(); e1.init(span, 0.6, pt(0f, 0f), false)
        val s2 = SkOpPtT(); s2.init(span, 0.3, pt(0f, 0f), false)
        val e2 = SkOpPtT(); e2.init(span, 0.9, pt(0f, 0f), false)
        val sOut = arrayOfNulls<SkOpPtT>(1)
        val eOut = arrayOfNulls<SkOpPtT>(1)
        assertTrue(SkOpPtT.Overlaps(s1, e1, s2, e2, sOut, eOut))
        // Overlap range : [0.3, 0.6].
        assertSame(s2, sOut[0])
        assertSame(e1, eOut[0])
    }

    @Test
    fun `Overlaps returns false on disjoint ranges`() {
        val span = SkOpSpanBase()
        val s1 = SkOpPtT(); s1.init(span, 0.0, pt(0f, 0f), false)
        val e1 = SkOpPtT(); e1.init(span, 0.2, pt(0f, 0f), false)
        val s2 = SkOpPtT(); s2.init(span, 0.5, pt(0f, 0f), false)
        val e2 = SkOpPtT(); e2.init(span, 0.9, pt(0f, 0f), false)
        val sOut = arrayOfNulls<SkOpPtT>(1)
        val eOut = arrayOfNulls<SkOpPtT>(1)
        assertFalse(SkOpPtT.Overlaps(s1, e1, s2, e2, sOut, eOut))
    }

    // ─── SkOpSpanBase ─────────────────────────────────────────────

    @Test
    fun `initBase populates segment, prev, fPtT, and self-loops fCoinEnd`() {
        val seg = SkOpSegment()
        val prev = SkOpSpan()
        val span = SkOpSpanBase()
        span.initBase(seg, prev, 0.5, pt(2f, 3f))
        assertSame(seg, span.segment())
        assertSame(prev, span.prev())
        assertEquals(0.5, span.t())
        assertEquals(pt(2f, 3f), span.pt())
        assertSame(span, span.coinEnd()) // self-loop
        assertFalse(span.chased())
        assertTrue(span.aligned())
    }

    @Test
    fun `final returns true iff t equals 1`() {
        val seg = SkOpSegment()
        val span1 = SkOpSpanBase(); span1.initBase(seg, null, 0.5, pt(0f, 0f))
        val span2 = SkOpSpanBase(); span2.initBase(seg, null, 1.0, pt(0f, 0f))
        assertFalse(span1.final())
        assertTrue(span2.final())
    }

    @Test
    fun `step returns +1 when t lt end_t and -1 otherwise`() {
        val seg = SkOpSegment()
        val a = SkOpSpanBase(); a.initBase(seg, null, 0.25, pt(0f, 0f))
        val b = SkOpSpanBase(); b.initBase(seg, null, 0.75, pt(0f, 0f))
        assertEquals(1, a.step(b))
        assertEquals(-1, b.step(a))
    }

    @Test
    fun `insertCoinEnd splices the coincident-end loop`() {
        val seg = SkOpSegment()
        val a = SkOpSpanBase(); a.initBase(seg, null, 0.0, pt(0f, 0f))
        val b = SkOpSpanBase(); b.initBase(seg, null, 0.5, pt(0f, 0f))
        a.insertCoinEnd(b)
        assertTrue(a.containsCoinEnd(b))
        assertTrue(b.containsCoinEnd(a))
    }

    @Test
    fun `upCastable returns null on final span`() {
        val seg = SkOpSegment()
        val span = SkOpSpanBase(); span.initBase(seg, null, 1.0, pt(0f, 0f))
        assertNull(span.upCastable())
    }

    @Test
    fun `upCastable returns the span when not final`() {
        val seg = SkOpSegment()
        val span = SkOpSpan(); span.init(seg, null, 0.5, pt(0f, 0f))
        assertNotNull(span.upCastable())
    }

    // ─── SkOpSpan ──────────────────────────────────────────────────

    @Test
    fun `SkOpSpan init clears bookkeeping`() {
        val seg = SkOpSegment()
        val span = SkOpSpan()
        span.init(seg, null, 0.25, pt(1f, 1f))
        assertEquals(0.25, span.t())
        assertEquals(SkOpSpan.SK_MinS32, span.windSum())
        assertEquals(SkOpSpan.SK_MinS32, span.oppSum())
        // Default winding contribution is 1 (matches upstream
        // SkOpSpan::init — each input edge contributes one to the
        // winding count). oppValue starts at 0.
        assertEquals(1, span.windValue())
        assertEquals(0, span.oppValue())
        assertFalse(span.done())
        assertFalse(span.alreadyAdded())
        assertSame(span, span.fCoincident) // self-loop
    }

    @Test
    fun `setWindValue and setOppValue write the value`() {
        val seg = SkOpSegment()
        val span = SkOpSpan(); span.init(seg, null, 0.5, pt(0f, 0f))
        span.setWindValue(2)
        assertEquals(2, span.windValue())
        span.setOppValue(3)
        assertEquals(3, span.oppValue())
    }

    @Test
    fun `setWindValue requires non-negative input`() {
        val seg = SkOpSegment()
        val span = SkOpSpan(); span.init(seg, null, 0.5, pt(0f, 0f))
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException::class.java) {
            span.setWindValue(-1)
        }
    }

    @Test
    fun `isCanceled true when both wind and opp values are zero`() {
        val seg = SkOpSegment()
        val span = SkOpSpan(); span.init(seg, null, 0.5, pt(0f, 0f))
        // After init : windValue=1 (default contribution), oppValue=0.
        assertFalse(span.isCanceled())
        span.setWindValue(0)
        assertTrue(span.isCanceled())
    }

    @Test
    fun `clearCoincident returns false when already self-referential`() {
        val seg = SkOpSegment()
        val span = SkOpSpan(); span.init(seg, null, 0.5, pt(0f, 0f))
        assertFalse(span.clearCoincident())
    }

    @Test
    fun `insertCoincidence splices the coincidence loop`() {
        val seg = SkOpSegment()
        val a = SkOpSpan(); a.init(seg, null, 0.25, pt(0f, 0f))
        val b = SkOpSpan(); b.init(seg, null, 0.75, pt(0f, 0f))
        a.insertCoincidence(b)
        assertTrue(a.containsCoincidence(b))
        assertTrue(b.containsCoincidence(a))
        assertTrue(a.isCoincident())
    }

    @Test
    fun `markAdded and alreadyAdded`() {
        val seg = SkOpSegment()
        val span = SkOpSpan(); span.init(seg, null, 0.5, pt(0f, 0f))
        assertFalse(span.alreadyAdded())
        span.markAdded()
        assertTrue(span.alreadyAdded())
    }

    @Test
    fun `setDone and done`() {
        val seg = SkOpSegment()
        val span = SkOpSpan(); span.init(seg, null, 0.5, pt(0f, 0f))
        assertFalse(span.done())
        span.setDone(true)
        assertTrue(span.done())
    }

    // ─── SkOpPtT.active (D1.2.g.c.2) ──────────────────────────────

    @Test
    fun `active returns this when not deleted`() {
        val span = SkOpSpanBase()
        val a = SkOpPtT(); a.init(span, 0.5, pt(0f, 0f), false)
        assertSame(a, a.active())
    }

    @Test
    fun `active walks to a non-deleted alias on the same span`() {
        val span = SkOpSpanBase()
        val a = SkOpPtT(); a.init(span, 0.0, pt(0f, 0f), false)
        val b = SkOpPtT(); b.init(span, 0.5, pt(0f, 0f), false)
        a.insert(b)
        a.setDeleted()
        // a is deleted, b is alive on the same span → active should pick b.
        assertSame(b, a.active())
    }

    @Test
    fun `active returns null when no alive alias exists`() {
        val span = SkOpSpanBase()
        val a = SkOpPtT(); a.init(span, 0.5, pt(0f, 0f), false)
        a.setDeleted()
        // a's loop is just [a] — no alive alias.
        assertNull(a.active())
    }

    // ─── SkOpSpanBase.collapsed(s, e) (D1.2.g.c.2) ────────────────

    @Test
    fun `SpanBase collapsed returns kNo on a singleton loop`() {
        val seg = SkOpSegment()
        val span = SkOpSpanBase(); span.initBase(seg, null, 0.0, pt(0f, 0f))
        assertEquals(SkOpSpanBase.Collapsed.kNo, span.collapsed(0.5, 0.7))
    }

    @Test
    fun `SpanBase collapsed returns kYes when both s and e fall inside the loop t-range`() {
        val seg = SkOpSegment()
        val span = SkOpSpanBase(); span.initBase(seg, null, 0.0, pt(0f, 0f))
        // Splice another pt-T (same segment) at t=0.8 into the loop.
        val alias = SkOpPtT()
        alias.init(SkOpSpanBase().also { it.initBase(seg, null, 0.8, pt(0f, 0f)) }, 0.8, pt(0f, 0f), false)
        span.fPtT.insert(alias)
        // Range (0.0..0.8) covers s=0.2 and e=0.5.
        assertEquals(SkOpSpanBase.Collapsed.kYes, span.collapsed(0.2, 0.5))
    }

    // ─── release / merge / addOpp (D1.2.g.c.3) ────────────────────

    @Test
    fun `Segment release decrements fCount and fDoneCount when span is done`() {
        val a = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        val mid = a.addT(0.5)!!
        assertEquals(2, a.count())
        val midSpan = mid.span()!! as SkOpSpan
        a.markDone(midSpan)
        a.release(midSpan)
        assertEquals(1, a.count())
    }

    @Test
    fun `Segment release with non-done span decrements only fCount`() {
        val a = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        val mid = a.addT(0.5)!!
        assertEquals(2, a.count())
        a.release(mid.span()!! as SkOpSpan)
        assertEquals(1, a.count())
    }

    @Test
    fun `SkOpSpan release unlinks the span and rewires the pt-T loop`() {
        val a = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        val mid = a.addT(0.5)!!
        val midSpan = mid.span() as SkOpSpan
        midSpan.release(a.fHead.ptT())
        // fHead's next now skips the released span.
        assertSame(a.fTail, a.fHead.next())
        assertSame(a.fHead, a.fTail.prev())
        assertTrue(mid.deleted())
    }

    @Test
    fun `SpanBase merge splices spanPtT into this loop and releases the span`() {
        val a = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        // Create a mid-span at t=0.5 — to be merged into fHead's loop.
        val mid = a.addT(0.5)!!
        val midSpan = mid.span() as SkOpSpan
        // Pre-condition checks : t == 0 != 0.5 and 0.5 is not zero_or_one.
        a.fHead.merge(midSpan)
        // After merge : mid is marked deleted (release sets it) ; mid's
        // span pointer is rewired to fHead's span ; mid pt-T sits right
        // after a.fHead.ptT() in the merged loop.
        assertTrue(mid.deleted())
        assertSame(a.fHead, mid.span())
        assertSame(mid, a.fHead.ptT().next())
    }

    @Test
    fun `computeWindSum returns the stored fWindSum (D1_2_h_5_3 stub)`() {
        // Stub : the full ray-tracing implementation lands in a future
        // winding slice ; for now computeWindSum just returns the
        // stored value.
        val seg = SkOpSegment()
        val span = SkOpSpan(); span.init(seg, null, 0.5, pt(0f, 0f))
        // Default fWindSum is SK_MinS32.
        assertEquals(SkOpSpan.SK_MinS32, span.computeWindSum())
        span.setWindSum(7)
        assertEquals(7, span.computeWindSum())
    }

    @Test
    fun `addOpp returns true when opp is already in the loop`() {
        val span1 = SkOpSpanBase()
        val span2 = SkOpSpanBase()
        val a = SkOpPtT(); a.init(span1, 0.0, pt(0f, 0f), false)
        val b = SkOpPtT(); b.init(span2, 0.0, pt(0f, 0f), false)
        // Pre-splice via addOpp(SkOpPtT, SkOpPtT) so b is already in a's loop.
        a.addOpp(b, b)
        // Now the SpanBase-level addOpp should short-circuit (oppPrev null).
        assertTrue(span1.addOpp(span2))
    }
}
