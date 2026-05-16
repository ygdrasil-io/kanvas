package org.skia.pathops.internal


import org.graphiks.math.SkDPoint
import org.graphiks.math.between
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for [SkTSect] skeleton + linked-list lifecycle
 * (Phase D1.1.e.2.c.1).
 */
class SkTSectTest {

    private fun quadCurve() = SkTQuad(SkDQuad(arrayOf(
        SkDPoint(0.0, 0.0), SkDPoint(50.0, 100.0), SkDPoint(100.0, 0.0),
    )))

    // ─── Construction ───────────────────────────────────────────────

    @Test
    fun `constructor creates one initial span covering 0 to 1`() {
        val s = SkTSect(quadCurve())
        val head = s.fHead
        assertNotNull(head)
        assertEquals(0.0, head!!.startT())
        assertEquals(1.0, head.endT())
        assertEquals(1, s.fActiveCount)
        assertNull(head.fPrev)
        assertNull(head.fNext)
    }

    @Test
    fun `pointLast returns the wrapped curve's last control point`() {
        val s = SkTSect(quadCurve())
        assertEquals(SkDPoint(100.0, 0.0), s.pointLast())
    }

    @Test
    fun `resetRemovedEnds clears the start and end flags`() {
        val s = SkTSect(quadCurve())
        s.fRemovedStartT = true; s.fRemovedEndT = true
        s.resetRemovedEnds()
        assertFalse(s.fRemovedStartT); assertFalse(s.fRemovedEndT)
    }

    // ─── Allocation / linking ───────────────────────────────────────

    @Test
    fun `addOne allocates a fresh span and increments active count`() {
        val s = SkTSect(quadCurve())
        assertEquals(1, s.fActiveCount)
        val newSpan = s.addOne()
        assertEquals(2, s.fActiveCount)
        assertNotSame(s.fHead, newSpan)
        assertFalse(newSpan.fDeleted)
    }

    @Test
    fun `addOne recycles from the deleted free list`() {
        val s = SkTSect(quadCurve())
        val original = s.addOne()
        s.markSpanGone(original)
        assertTrue(original.fDeleted)
        // Next addOne should recycle the same instance.
        val recycled = s.addOne()
        assertSame(original, recycled)
        assertFalse(recycled.fDeleted)
    }

    @Test
    fun `addFollowing links a span between prior and prior_next`() {
        val s = SkTSect(quadCurve())
        // Split head to create a 2-span list.
        val initial = s.fHead!!
        val second = s.addOne()
        assertTrue(second.splitAt(initial, 0.5))
        // Now insert a span after the second — at the end (next == null).
        val third = s.addFollowing(second)
        assertSame(second, third.fPrev)
        assertNull(third.fNext)
        assertEquals(second.fEndT, third.fStartT, 1e-9)
        assertEquals(1.0, third.fEndT, 1e-9)
    }

    @Test
    fun `addSplitAt creates a new span covering the right half`() {
        val s = SkTSect(quadCurve())
        val initial = s.fHead!!
        val newSpan = s.addSplitAt(initial, 0.5)
        assertEquals(0.5, newSpan.fStartT, 1e-9)
        assertEquals(1.0, newSpan.fEndT, 1e-9)
        assertEquals(0.0, initial.fStartT, 1e-9)
        assertEquals(0.5, initial.fEndT, 1e-9)
    }

    // ─── Linked-list traversal ──────────────────────────────────────

    @Test
    fun `prev returns the prior sibling`() {
        val s = SkTSect(quadCurve())
        val initial = s.fHead!!
        val second = s.addOne()
        second.splitAt(initial, 0.5)
        assertSame(initial, s.prev(second))
    }

    @Test
    fun `tail returns the span with the largest endT`() {
        val s = SkTSect(quadCurve())
        val initial = s.fHead!!
        val second = s.addOne()
        second.splitAt(initial, 0.5)
        assertSame(second, s.tail())
    }

    @Test
    fun `boundsMax returns the span with the largest fBoundsMax`() {
        val s = SkTSect(quadCurve())
        val initial = s.fHead!!
        val half = s.addOne()
        half.splitAt(initial, 0.5)
        // After split, both spans have ~half the bounds. boundsMax
        // returns the larger.
        val largest = s.boundsMax()
        assertNotNull(largest)
        // Both halves are roughly the same size — just verify it picks one.
        assertTrue(largest === initial || largest === half)
    }

    @Test
    fun `spanAtT returns the span containing t and prior in out param`() {
        val s = SkTSect(quadCurve())
        val initial = s.fHead!!
        val half = s.addOne()
        half.splitAt(initial, 0.5)
        val priorSpan = arrayOfNulls<SkTSpan>(1)
        // t = 0.25 is in initial (covers [0, 0.5]).
        assertSame(initial, s.spanAtT(0.25, priorSpan))
        assertNull(priorSpan[0])
        // t = 0.75 is in half (covers [0.5, 1]).
        assertSame(half, s.spanAtT(0.75, priorSpan))
        assertSame(initial, priorSpan[0])
    }

    // ─── Counters ───────────────────────────────────────────────────

    @Test
    fun `collapsed counts spans with fCollapsed=true`() {
        val s = SkTSect(quadCurve())
        assertEquals(0, s.collapsed())
        // Force a collapse on the head.
        s.fHead!!.fCollapsed = true
        assertEquals(1, s.collapsed())
    }

    @Test
    fun `countConsecutiveSpans walks adjacent spans`() {
        val s = SkTSect(quadCurve())
        val initial = s.fHead!!
        val half = s.addOne()
        half.splitAt(initial, 0.5)
        val third = s.addOne()
        third.splitAt(half, 0.75)
        val lastPtr = arrayOfNulls<SkTSpan>(1)
        val n = s.countConsecutiveSpans(initial, lastPtr)
        assertEquals(3, n)
        assertSame(third, lastPtr[0])
    }

    @Test
    fun `coincidentHasT returns false on empty coincident list`() {
        val s = SkTSect(quadCurve())
        assertFalse(s.coincidentHasT(0.5))
    }

    @Test
    fun `hasBounded returns false when no spans bound the test span`() {
        val s = SkTSect(quadCurve())
        val test = s.addOne()
        assertFalse(s.hasBounded(test))
    }

    @Test
    fun `hasBounded returns true when an active span bounds the test span`() {
        val s = SkTSect(quadCurve())
        val test = s.addOne()
        s.fHead!!.addBounded(test)
        assertTrue(s.hasBounded(test))
    }

    // ─── Removal / lifecycle ────────────────────────────────────────

    @Test
    fun `markSpanGone pushes to deleted list and decrements active count`() {
        val s = SkTSect(quadCurve())
        val span = s.addOne()
        val priorActive = s.fActiveCount
        assertTrue(s.markSpanGone(span))
        assertTrue(span.fDeleted)
        assertEquals(priorActive - 1, s.fActiveCount)
    }

    @Test
    fun `unlinkSpan removes from the doubly-linked active list`() {
        val s = SkTSect(quadCurve())
        val initial = s.fHead!!
        val second = s.addOne()
        second.splitAt(initial, 0.5)
        assertTrue(s.unlinkSpan(second))
        assertNull(initial.fNext)
    }

    @Test
    fun `removeSpan unlinks and marks gone`() {
        val s = SkTSect(quadCurve())
        val initial = s.fHead!!
        val second = s.addOne()
        second.splitAt(initial, 0.5)
        assertTrue(s.removeSpan(second))
        assertNull(initial.fNext)
        assertTrue(second.fDeleted)
    }

    @Test
    fun `removedEndCheck flips fRemovedStartT for span at startT=0`() {
        val s = SkTSect(quadCurve())
        val span = s.addOne()
        span.fStartT = 0.0; span.fEndT = 0.5
        s.removedEndCheck(span)
        assertTrue(s.fRemovedStartT)
        assertFalse(s.fRemovedEndT)
    }

    @Test
    fun `removedEndCheck flips fRemovedEndT for span at endT=1`() {
        val s = SkTSect(quadCurve())
        val span = s.addOne()
        span.fStartT = 0.5; span.fEndT = 1.0
        s.removedEndCheck(span)
        assertFalse(s.fRemovedStartT)
        assertTrue(s.fRemovedEndT)
    }

    @Test
    fun `deleteEmptySpans drops spans with empty bounded list`() {
        val s = SkTSect(quadCurve())
        val initial = s.fHead!!
        val second = s.addOne()
        second.splitAt(initial, 0.5)
        // initial has no bounded entries → should be removed.
        // second has none either → should also be removed.
        assertTrue(s.deleteEmptySpans())
        assertNull(s.fHead)
        assertEquals(0, s.fActiveCount)
    }
}
