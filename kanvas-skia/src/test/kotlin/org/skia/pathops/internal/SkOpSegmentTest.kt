package org.skia.pathops.internal

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.math.SkPoint
import org.skia.math.SkRect

/**
 * Unit tests for [SkOpSegment] data model + structural methods
 * (Phase D1.2.c).
 */
class SkOpSegmentTest {

    private fun pt(x: Float, y: Float) = SkPoint(fX = x, fY = y)

    // ─── addLine / addQuad / addCubic / addConic ───────────────────

    @Test
    fun `addLine sets fHead to t=0 and fTail to t=1 with linked list`() {
        val seg = SkOpSegment()
        val p0 = pt(0f, 0f); val p1 = pt(10f, 0f)
        seg.addLine(arrayOf(p0, p1), null)
        assertEquals(0.0, seg.head().t())
        assertEquals(1.0, seg.tail().t())
        assertEquals(p0, seg.head().pt())
        assertEquals(p1, seg.tail().pt())
        assertSame(seg.tail(), seg.head().next())
        assertSame(seg.head(), seg.tail().prev())
        assertEquals(SkOpSegment.SegVerb.kLine, seg.verb())
        assertEquals(1, seg.count())
        assertFalse(seg.done())
    }

    @Test
    fun `addLine rejects coincident endpoints`() {
        val seg = SkOpSegment()
        val p = pt(5f, 5f)
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException::class.java) {
            seg.addLine(arrayOf(p, p), null)
        }
    }

    @Test
    fun `addLine bounds equal endpoint bbox`() {
        val seg = SkOpSegment()
        seg.addLine(arrayOf(pt(0f, 0f), pt(10f, 5f)), null)
        val b = seg.bounds()
        assertEquals(0f, b.left); assertEquals(0f, b.top)
        assertEquals(10f, b.right); assertEquals(5f, b.bottom)
    }

    @Test
    fun `addQuad sets verb and 3 points`() {
        val seg = SkOpSegment()
        val p0 = pt(0f, 0f); val p1 = pt(50f, 100f); val p2 = pt(100f, 0f)
        seg.addQuad(arrayOf(p0, p1, p2), null)
        assertEquals(SkOpSegment.SegVerb.kQuad, seg.verb())
        assertEquals(p2, seg.tail().pt())
        assertEquals(p2, seg.lastPt())
    }

    @Test
    fun `addCubic sets verb and 4 points`() {
        val seg = SkOpSegment()
        val pts = arrayOf(pt(0f, 0f), pt(0f, 10f), pt(10f, 10f), pt(10f, 0f))
        seg.addCubic(pts, null)
        assertEquals(SkOpSegment.SegVerb.kCubic, seg.verb())
        assertEquals(pts[3], seg.tail().pt())
    }

    @Test
    fun `addConic stores weight`() {
        val seg = SkOpSegment()
        val pts = arrayOf(pt(0f, 0f), pt(50f, 100f), pt(100f, 0f))
        seg.addConic(pts, 0.7071f, null)
        assertEquals(SkOpSegment.SegVerb.kConic, seg.verb())
        assertEquals(0.7071f, seg.weight())
    }

    // ─── Simple accessors ─────────────────────────────────────────

    @Test
    fun `setContour and setNext setPrev round-trip`() {
        val seg = SkOpSegment()
        seg.addLine(arrayOf(pt(0f, 0f), pt(1f, 0f)), null)
        val contour = SkOpContour()
        val next = SkOpSegment(); next.addLine(arrayOf(pt(1f, 0f), pt(2f, 0f)), null)
        val prev = SkOpSegment(); prev.addLine(arrayOf(pt(-1f, 0f), pt(0f, 0f)), null)
        seg.setContour(contour); seg.setNext(next); seg.setPrev(prev)
        assertSame(contour, seg.contour())
        assertSame(next, seg.next())
        assertSame(prev, seg.prev())
    }

    @Test
    fun `isHorizontal true for line on the y axis`() {
        val seg = SkOpSegment()
        seg.addLine(arrayOf(pt(0f, 5f), pt(10f, 5f)), null)
        assertTrue(seg.isHorizontal())
        assertFalse(seg.isVertical())
    }

    @Test
    fun `isVertical true for line on the x axis`() {
        val seg = SkOpSegment()
        seg.addLine(arrayOf(pt(5f, 0f), pt(5f, 10f)), null)
        assertTrue(seg.isVertical())
        assertFalse(seg.isHorizontal())
    }

    @Test
    fun `visited returns false on first call, true thereafter`() {
        val seg = SkOpSegment()
        seg.addLine(arrayOf(pt(0f, 0f), pt(1f, 1f)), null)
        assertFalse(seg.visited())
        assertTrue(seg.visited())
        seg.resetVisited()
        assertFalse(seg.visited())
    }

    @Test
    fun `done returns false until fDoneCount catches fCount`() {
        val seg = SkOpSegment()
        seg.addLine(arrayOf(pt(0f, 0f), pt(1f, 1f)), null)
        assertFalse(seg.done())
        seg.fDoneCount = 1
        assertTrue(seg.done())
    }

    @Test
    fun `bumpCount increments`() {
        val seg = SkOpSegment()
        seg.addLine(arrayOf(pt(0f, 0f), pt(1f, 1f)), null)
        val before = seg.count()
        seg.bumpCount()
        assertEquals(before + 1, seg.count())
    }

    // ─── insert / contains ────────────────────────────────────────

    @Test
    fun `insert adds a fresh span between prev and prev next`() {
        val seg = SkOpSegment()
        seg.addLine(arrayOf(pt(0f, 0f), pt(1f, 0f)), null)
        val originalNext = seg.head().next()
        val mid = seg.insert(seg.head())
        assertNotSame(originalNext, mid)
        assertSame(seg.head(), mid.prev())
        assertSame(mid, seg.head().next())
        assertSame(originalNext, mid.next())
    }

    @Test
    fun `contains returns true for head and tail t-values`() {
        val seg = SkOpSegment()
        seg.addLine(arrayOf(pt(0f, 0f), pt(1f, 0f)), null)
        assertTrue(seg.contains(0.0))
        assertTrue(seg.contains(1.0))
    }

    @Test
    fun `contains returns false for an unregistered t`() {
        val seg = SkOpSegment()
        seg.addLine(arrayOf(pt(0f, 0f), pt(1f, 0f)), null)
        assertFalse(seg.contains(0.5))
    }

    // ─── joinEnds ─────────────────────────────────────────────────

    @Test
    fun `joinEnds splices tail ptT with start head ptT loop`() {
        val a = SkOpSegment(); a.addLine(arrayOf(pt(0f, 0f), pt(1f, 0f)), null)
        val b = SkOpSegment(); b.addLine(arrayOf(pt(1f, 0f), pt(2f, 0f)), null)
        a.joinEnds(b)
        // a.tail.ptT.next now points to b.head.ptT.
        assertSame(b.head().ptT(), a.tail().ptT().next())
    }

    // ─── spanToAngle ──────────────────────────────────────────────

    @Test
    fun `spanToAngle picks toAngle when start_t lt end_t`() {
        val seg = SkOpSegment()
        seg.addLine(arrayOf(pt(0f, 0f), pt(1f, 0f)), null)
        val angle = SkOpAngle()
        seg.head().setToAngle(angle)
        assertSame(angle, seg.spanToAngle(seg.head(), seg.tail()))
    }

    // ─── Comparable ───────────────────────────────────────────────

    @Test
    fun `compareTo orders by bounds top`() {
        val a = SkOpSegment(); a.addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null) // top=0
        val b = SkOpSegment(); b.addLine(arrayOf(pt(0f, 5f), pt(10f, 5f)), null) // top=5
        assertTrue(a < b)
        assertEquals(0, a.compareTo(SkOpSegment().also {
            it.addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        }))
    }

    // ─── Static helpers ───────────────────────────────────────────

    @Test
    fun `SpanSign returns the negated start windValue when forward`() {
        val seg = SkOpSegment()
        seg.addLine(arrayOf(pt(0f, 0f), pt(1f, 0f)), null)
        seg.head().setWindValue(2)
        // start (head, t=0) < end (tail, t=1) → return -head.windValue = -2.
        assertEquals(-2, SkOpSegment.SpanSign(seg.head(), seg.tail()))
    }

    @Test
    fun `OppSign mirrors SpanSign for opp values`() {
        val seg = SkOpSegment()
        seg.addLine(arrayOf(pt(0f, 0f), pt(1f, 0f)), null)
        seg.head().setOppValue(3)
        assertEquals(-3, SkOpSegment.OppSign(seg.head(), seg.tail()))
    }

    @Test
    fun `UseInnerWinding rejects equal arguments`() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException::class.java) {
            SkOpSegment.UseInnerWinding(2, 2)
        }
    }

    @Test
    fun `UseInnerWinding returns absOut greater than absIn when absolutes differ`() {
        // Mirrors upstream : `absOut == absIn ? outerWinding < 0 : absOut > absIn`.
        assertTrue(SkOpSegment.UseInnerWinding(3, 1))
        assertFalse(SkOpSegment.UseInnerWinding(1, 3))
    }

    @Test
    fun `UseInnerWinding tie-breaks by sign of outer when absolutes match`() {
        assertTrue(SkOpSegment.UseInnerWinding(-2, 2))
        assertFalse(SkOpSegment.UseInnerWinding(2, -2))
    }
}
