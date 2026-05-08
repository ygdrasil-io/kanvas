package org.skia.pathops.internal

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
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
    fun `UseInnerWinding accepts equal arguments and tie-breaks by outer sign`() {
        // The original D1.2.c port asserted that equal args were rejected.
        // Upstream just asserts neither is SK_MaxS32 ; the equal case is a
        // valid input handled by the absOut==absIn branch.
        assertTrue(SkOpSegment.UseInnerWinding(-2, -2))   // outer < 0 → true
        assertFalse(SkOpSegment.UseInnerWinding(2, 2))    // outer > 0 → false
    }

    @Test
    fun `UseInnerWinding returns absOut less than absIn when absolutes differ`() {
        // Mirrors upstream : `absOut == absIn ? outerWinding < 0 : absOut < absIn`.
        // The D1.2.c port had this flipped ; D1.2.c.2.c restores upstream
        // parity (surfaced by the new winding-query callers).
        assertFalse(SkOpSegment.UseInnerWinding(3, 1))
        assertTrue(SkOpSegment.UseInnerWinding(1, 3))
    }

    @Test
    fun `UseInnerWinding tie-breaks by sign of outer when absolutes match`() {
        assertTrue(SkOpSegment.UseInnerWinding(-2, 2))
        assertFalse(SkOpSegment.UseInnerWinding(2, -2))
    }

    // ─── Angle ring construction (D1.2.c.2.a) ──────────────────────

    @Test
    fun `addStartSpan attaches a fresh angle to fHead's toAngle slot`() {
        val seg = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        val angle = seg.addStartSpan()
        assertSame(seg.fHead, angle.start())
        assertSame(seg.fTail, angle.end())
        assertSame(angle, seg.fHead.toAngle())
    }

    @Test
    fun `addEndSpan attaches a fresh angle to fTail's fromAngle slot`() {
        val seg = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        val angle = seg.addEndSpan()
        assertSame(seg.fTail, angle.start())
        assertSame(seg.fHead, angle.end())
        assertSame(angle, seg.fTail.fromAngle())
    }

    @Test
    fun `calcAngles is a no-op when head and tail are simple and active`() {
        // Single-span segment with simple head + tail — no angles attached.
        val seg = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        seg.calcAngles()
        // fHead is canonical, fTail is canonical — neither got angles.
        assertNull(seg.fHead.toAngle())
        assertNull(seg.fTail.fromAngle())
    }

    @Test
    fun `sortAngles returns true on a segment with no angles attached`() {
        // No angles on any span → outer loop short-circuits everywhere ;
        // method runs to completion and returns true.
        val seg = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        assertTrue(seg.sortAngles())
    }

    // ─── Winding marking (D1.2.c.2.b) ──────────────────────────────

    @Test
    fun `markDone increments fDoneCount and sets done flag`() {
        val seg = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        assertFalse(seg.fHead.done())
        assertEquals(0, seg.fDoneCount)
        seg.markDone(seg.fHead)
        assertTrue(seg.fHead.done())
        assertEquals(1, seg.fDoneCount)
    }

    @Test
    fun `markDone is idempotent on an already-done span`() {
        val seg = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        seg.markDone(seg.fHead)
        seg.markDone(seg.fHead) // second call is a no-op.
        assertEquals(1, seg.fDoneCount)
    }

    @Test
    fun `markAllDone marks every span until tail`() {
        val seg = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        seg.markAllDone()
        assertTrue(seg.fHead.done())
        // fCount=1 means there's only fHead as a SkOpSpan ; markAllDone
        // marks it and stops.
        assertEquals(1, seg.fDoneCount)
    }

    @Test
    fun `markWinding sets windSum and returns true on a fresh span`() {
        val seg = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        assertTrue(seg.markWinding(seg.fHead, 1))
        assertEquals(1, seg.fHead.windSum())
    }

    @Test
    fun `markWinding returns false on an already-done span`() {
        val seg = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        seg.markDone(seg.fHead)
        assertFalse(seg.markWinding(seg.fHead, 1))
    }

    @Test
    fun `markWinding binary form sets both windSum and oppSum`() {
        val seg = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        assertTrue(seg.markWinding(seg.fHead, 1, 2))
        assertEquals(1, seg.fHead.windSum())
        assertEquals(2, seg.fHead.oppSum())
    }

    @Test
    fun `nextChase returns null when endSpan is interior with no angle`() {
        // For a single-span line segment, nextChase from fHead (step=+1)
        // looks at endSpan = fTail. fTail.fromAngle is null (no angle
        // attached) and fTail.t == 1.0 — so it tries the pt-T loop.
        // The loop is a self-loop, so otherPtT === endSpan.ptT and
        // foundSpan == fTail. otherEnd = fTail.next, but fTail's
        // upCastable is null (it's the tail). Returns null.
        val seg = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        val startArr = arrayOf<SkOpSpanBase?>(seg.fHead)
        val stepArr = intArrayOf(1)
        val minArr = arrayOf<SkOpSpan?>(seg.fHead)
        val lastArr = arrayOf<SkOpSpanBase?>(null)
        val result = seg.nextChase(startArr, stepArr, minArr, lastArr)
        // Either null (terminates) or a non-null other segment ; for
        // this isolated line fixture the chase terminates.
        assertEquals(null, result)
    }

    // ─── Winding queries (D1.2.c.2.c) ──────────────────────────────

    @Test
    fun `setUpWinding returns max equals input and sum equals max minus deltaSum`() {
        // Single-line segment with windValue=1.
        val seg = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        seg.fHead.fWindValue = 1
        // SpanSign(start=fHead, end=fTail) : fHead.t=0 < fTail.t=1 → -windValue = -1.
        // setUpWinding(start, end, sumIn=5) → max=5, sum=5-(-1)=6.
        val (max, sum) = seg.setUpWinding(seg.fHead, seg.fTail, 5)
        assertEquals(5, max)
        assertEquals(6, sum)
    }

    @Test
    fun `setUpWinding propagates SK_MinS32 sentinel without subtracting`() {
        val seg = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        val (max, sum) = seg.setUpWinding(seg.fHead, seg.fTail, SkOpSpan.SK_MinS32)
        // Max gets the sentinel ; sum stays at the sentinel rather than
        // subtracting (would produce a meaningless wraparound).
        assertEquals(SkOpSpan.SK_MinS32, max)
        assertEquals(SkOpSpan.SK_MinS32, sum)
    }

    @Test
    fun `setUpWindings unary form mutates running total in place`() {
        val seg = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        seg.fHead.fWindValue = 2
        val sumMi = intArrayOf(10)
        // SpanSign = -2 ; sumMi becomes 10 - (-2) = 12 in place ; max=10, sum=12.
        val (max, sum) = seg.setUpWindings(seg.fHead, seg.fTail, sumMi)
        assertEquals(10, max)
        assertEquals(12, sum)
        assertEquals(12, sumMi[0])
    }

    @Test
    fun `windSum reads the angle starter span's windSum`() {
        val seg = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        seg.markWinding(seg.fHead, 7)
        val angle = SkOpAngle().also { it.set(seg.fHead, seg.fTail) }
        assertEquals(7, seg.windSum(angle))
    }

    @Test
    fun `updateWinding returns the lesser windSum when no inner-winding flip is needed`() {
        // windSum=5, SpanSign=-1. UseInnerWinding(5-(-1)=6, 5) :
        // absOut=6, absIn=5 → 6 < 5 is false → returns false.
        // → updateWinding returns winding (5) unchanged.
        val seg = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        seg.fHead.fWindValue = 1
        seg.markWinding(seg.fHead, 5)
        assertEquals(5, seg.updateWinding(seg.fHead, seg.fTail))
    }

    @Test
    fun `updateWinding returns SK_MinS32 sentinel when wind sum is unset`() {
        val seg = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        // fHead.windSum is SK_MinS32 by default.
        assertEquals(SkOpSpan.SK_MinS32, seg.updateWinding(seg.fHead, seg.fTail))
    }

    @Test
    fun `activeWinding flags a span where from and to differ on zero crossing`() {
        // A pre-marked span where winding crosses zero between max and sum.
        val seg = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        seg.fHead.fWindValue = 1
        // Set up : sumWindingIn = 1, SpanSign = -1, so sum = 1 - (-1) = 2.
        // from = (max=1) != 0 = true ; to = (sum=2) != 0 = true. Same → false.
        val sumArr = intArrayOf(1)
        assertFalse(seg.activeWinding(seg.fHead, seg.fTail, sumArr))
        // sumWindingIn=0, SpanSign=-1 → sum=0-(-1)=1. from=false, to=true → true.
        val sumArr2 = intArrayOf(0)
        assertTrue(seg.activeWinding(seg.fHead, seg.fTail, sumArr2))
    }

    // ─── Sum propagation (D1.2.c.2.d) ──────────────────────────────

    @Test
    fun `computeSum returns SK_MinS32 sentinel when no angle ring is attached`() {
        // No angles → spanToAngle returns null → computeSum bails.
        val seg = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        val result = seg.computeSum(seg.fHead, seg.fTail, SkOpAngle.IncludeType.kUnaryWinding)
        assertEquals(SkOpSpan.SK_MinS32, result)
    }

    @Test
    fun `computeSum rejects kUnaryXor`() {
        val seg = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException::class.java) {
            seg.computeSum(seg.fHead, seg.fTail, SkOpAngle.IncludeType.kUnaryXor)
        }
    }

    @Test
    fun `ComputeOneSum unary form transfers windSum from base to next via markAngle`() {
        // Two angles wrapping disjoint segments. baseAngle has its
        // windSum already set ; ComputeOneSum reads it, computes the
        // delta-adjusted sum for nextAngle, and marks it.
        val sa = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        val sb = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(0f, 10f)), null)
        sa.fHead.fWindValue = 1
        sa.markWinding(sa.fHead, 3)
        val baseA = SkOpAngle().also { it.set(sa.fHead, sa.fTail) }
        val nextA = SkOpAngle().also { it.set(sb.fHead, sb.fTail) }
        // Pre-condition : nextA's segment.fHead has no windSum set.
        assertEquals(SkOpSpan.SK_MinS32, sb.fHead.windSum())
        val result = sa.ComputeOneSum(baseA, nextA, SkOpAngle.IncludeType.kUnaryWinding)
        assertTrue(result)
        // Post : sb.fHead.windSum is now set.
        org.junit.jupiter.api.Assertions.assertNotEquals(SkOpSpan.SK_MinS32, sb.fHead.windSum())
    }

    @Test
    fun `ComputeOneSumReverse uses forward updateWinding for the base read`() {
        val sa = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        val sb = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(0f, 10f)), null)
        sa.fHead.fWindValue = 1
        sa.markWinding(sa.fHead, 3)
        val baseA = SkOpAngle().also { it.set(sa.fHead, sa.fTail) }
        val nextA = SkOpAngle().also { it.set(sb.fHead, sb.fTail) }
        val result = sa.ComputeOneSumReverse(baseA, nextA, SkOpAngle.IncludeType.kUnaryWinding)
        assertTrue(result)
        org.junit.jupiter.api.Assertions.assertNotEquals(SkOpSpan.SK_MinS32, sb.fHead.windSum())
    }
}
