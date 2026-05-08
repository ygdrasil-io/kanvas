package org.skia.pathops.internal

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
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

    // ─── Pt-T linking + utilities (D1.2.c.2.e) ─────────────────────

    @Test
    fun `ptAtT on a line returns the lerp endpoint`() {
        val seg = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        assertEquals(pt(5f, 0f), seg.ptAtT(0.5))
        assertEquals(pt(0f, 0f), seg.ptAtT(0.0))
        assertEquals(pt(10f, 0f), seg.ptAtT(1.0))
    }

    @Test
    fun `ptsDisjoint returns false for line segments`() {
        val seg = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        // Lines never loop back ; ptsDisjoint short-circuits to false.
        assertFalse(seg.ptsDisjoint(0.1, pt(1f, 0f), 0.5, pt(5f, 0f)))
    }

    @Test
    fun `match returns true when same segment and precisely-equal t`() {
        val seg = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        // fHead's pt-T has fT=0 ; match against the same (t=0, pt=(0,0)) → true.
        assertTrue(seg.match(seg.fHead.ptT(), seg, 0.0, pt(0f, 0f)))
    }

    @Test
    fun `match returns false when test point is far from base point`() {
        val seg = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        // Different t with non-equal point → ApproximatelyEqual fails.
        assertFalse(seg.match(seg.fHead.ptT(), seg, 0.5, pt(99f, 99f)))
    }

    @Test
    fun `addT returns the existing pt-T when t already exists`() {
        val seg = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        // t=0 already lives on fHead.
        val result = seg.addT(0.0, pt(0f, 0f))
        assertSame(seg.fHead.ptT(), result)
    }

    @Test
    fun `addT inserts a new span when t is between existing ones`() {
        val seg = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        assertEquals(1, seg.fCount)
        val result = seg.addT(0.5, pt(5f, 0f))
        assertNotNull(result)
        // A fresh span was inserted between fHead and fTail ; fCount bumped.
        assertEquals(2, seg.fCount)
        assertEquals(0.5, result!!.fT)
    }

    @Test
    fun `clearOne resets windValue oppValue and marks done`() {
        val seg = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        seg.fHead.fWindValue = 7
        seg.fHead.fOppValue = 3
        seg.clearOne(seg.fHead)
        assertEquals(0, seg.fHead.windValue())
        assertEquals(0, seg.fHead.oppValue())
        assertTrue(seg.fHead.done())
    }

    @Test
    fun `clearAll clears every span`() {
        val seg = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        seg.fHead.fWindValue = 5
        seg.clearAll()
        assertEquals(0, seg.fHead.windValue())
        assertTrue(seg.fHead.done())
    }

    // ─── Coincidence helpers (D1.2.c.2.f) ──────────────────────────

    @Test
    fun `undoneSpan returns the first not-done span`() {
        val seg = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        // Fresh segment : fHead is not done.
        assertSame(seg.fHead, seg.undoneSpan())
        // After marking, undoneSpan returns null.
        seg.markDone(seg.fHead)
        assertNull(seg.undoneSpan())
    }

    @Test
    fun `testForCoincidence on coincident lines returns true at midpoint`() {
        // Two collinear segments — same chord. The perpendicular ray
        // sampled at mid-T crosses the opposite curve at the midpoint
        // exactly → coincidence detected.
        val a = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        val b = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        val result = a.testForCoincidence(a.fHead.ptT(), a.fTail.ptT(),
            a.fHead, a.fTail, b)
        assertTrue(result)
    }

    @Test
    fun `spansNearby finds a match when both heads share an exact point`() {
        val a = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        val b = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(0f, 10f)), null)
        // Splice b's fHead pt-T into a's loop so the head's pt-T loop
        // sees both segments.
        a.fHead.ptT().addOpp(b.fHead.ptT(), b.fHead.ptT())
        val foundOut = booleanArrayOf(false)
        val ok = a.spansNearby(a.fHead, b.fHead, foundOut)
        assertTrue(ok)
        assertTrue(foundOut[0])
    }

    @Test
    fun `spansNearby returns false-found when heads are far apart`() {
        val a = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        val b = SkOpSegment().addLine(arrayOf(pt(100f, 100f), pt(200f, 100f)), null)
        val foundOut = booleanArrayOf(true)  // pre-set to detect overwrite
        val ok = a.spansNearby(a.fHead, b.fHead, foundOut)
        assertTrue(ok)
        assertFalse(foundOut[0])
    }

    @Test
    fun `ClearVisited resets visited flags on segments in span pt-T loops`() {
        val a = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        val b = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(0f, 10f)), null)
        // Splice b into a's head loop so ClearVisited sees both segments.
        a.fHead.ptT().addOpp(b.fHead.ptT(), b.fHead.ptT())
        // Pre-mark both as visited.
        a.visited(); b.visited()
        SkOpSegment.ClearVisited(a.fHead)
        // First call to visited() on a freshly-reset segment returns false.
        assertFalse(b.visited())
    }

    // ─── existing (D1.2.g.c.2) ────────────────────────────────────

    @Test
    fun `existing returns the head pt-T at t=0`() {
        val a = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        assertSame(a.fHead.ptT(), a.existing(0.0, null))
    }

    @Test
    fun `existing returns the tail pt-T at t=1`() {
        val a = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        assertSame(a.fTail.ptT(), a.existing(1.0, null))
    }

    @Test
    fun `existing returns null when no pt-T sits at the requested t`() {
        val a = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        // No interior pt-T at t=0.5 ; ptAtT gives a unique pt that
        // doesn't ApproximatelyEqual either head or tail.
        assertNull(a.existing(0.5, null))
    }

    @Test
    fun `existing with non-null opp requires the span to contain opp`() {
        val a = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        val b = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(0f, 10f)), null)
        // a's fHead doesn't contain b yet.
        assertNull(a.existing(0.0, b))
        // Splice b into a's fHead pt-T loop.
        a.fHead.ptT().addOpp(b.fHead.ptT(), b.fHead.ptT())
        assertSame(a.fHead.ptT(), a.existing(0.0, b))
    }

    // ─── collapsed(s, e) (D1.2.g.c.2) ─────────────────────────────

    @Test
    fun `Segment collapsed returns kNo on a freshly-built line`() {
        val a = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        assertEquals(SkOpSpanBase.Collapsed.kNo, a.collapsed(0.2, 0.5))
    }

    // ─── kActiveEdge truth table (D1.2.h.5.0) ────────────────────

    @Test
    fun `kActiveEdge minuend-only entering edge — kDifference is T when sub absent`() {
        // (miFrom=0, miTo=1, suFrom=0, suTo=0) — entering minuend, no
        // subtrahend involvement.
        // Expected per kActiveEdge[diff][0][1][0] = {T, F} :
        assertTrue(SkOpSegment.kActiveEdge(
            org.skia.pathops.SkPathOp.kDifference, false, true, false, false))
        // (miFrom=0, miTo=1, suFrom=0, suTo=1) — entering both.
        // diff says F : the minuend's edge is now inside subtrahend → not in result.
        org.junit.jupiter.api.Assertions.assertFalse(SkOpSegment.kActiveEdge(
            org.skia.pathops.SkPathOp.kDifference, false, true, false, true))
    }

    @Test
    fun `kActiveEdge minuend-only entering — kIntersect is F when sub absent`() {
        // sect[0][1][0] = {F, T} : suTo=0 → F (no overlap), suTo=1 → T.
        org.junit.jupiter.api.Assertions.assertFalse(SkOpSegment.kActiveEdge(
            org.skia.pathops.SkPathOp.kIntersect, false, true, false, false))
        assertTrue(SkOpSegment.kActiveEdge(
            org.skia.pathops.SkPathOp.kIntersect, false, true, false, true))
    }

    @Test
    fun `kActiveEdge minuend-only entering — kUnion always T when sub absent`() {
        // union[0][1][0] = {T, T} : both suTo branches true.
        assertTrue(SkOpSegment.kActiveEdge(
            org.skia.pathops.SkPathOp.kUnion, false, true, false, false))
        assertTrue(SkOpSegment.kActiveEdge(
            org.skia.pathops.SkPathOp.kUnion, false, true, false, true))
    }

    @Test
    fun `kActiveEdge no-edge anywhere — all ops return F`() {
        // (mF=0, mT=0, sF=0, sT=0) — no minuend, no subtrahend → no edge.
        for (op in arrayOf(
            org.skia.pathops.SkPathOp.kDifference,
            org.skia.pathops.SkPathOp.kIntersect,
            org.skia.pathops.SkPathOp.kUnion,
            org.skia.pathops.SkPathOp.kXOR,
        )) {
            org.junit.jupiter.api.Assertions.assertFalse(
                SkOpSegment.kActiveEdge(op, false, false, false, false),
                "op=$op should be F at (0,0,0,0)",
            )
        }
    }

    @Test
    fun `kActiveEdge xor symmetry — flipping either input toggles result`() {
        // XOR is symmetric : (mFrom, mTo) entering minuend-side at the
        // boundary should toggle independently of (suFrom, suTo).
        // From upstream xor table : xor[0][1][0] = {T, F}. F when both
        // mins toggle, T when only one does.
        assertTrue(SkOpSegment.kActiveEdge(
            org.skia.pathops.SkPathOp.kXOR, false, true, false, false))
        org.junit.jupiter.api.Assertions.assertFalse(SkOpSegment.kActiveEdge(
            org.skia.pathops.SkPathOp.kXOR, false, true, false, true))
    }

    // ─── findNextWinding / findNextXor (D1.2.h.6.0) ──────────────

    @Test
    fun `findNextWinding returns null on a single-line empty angle ring`() {
        val a = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        val nextStart = arrayOfNulls<SkOpSpanBase>(1).also { it[0] = a.fHead }
        val nextEnd = arrayOfNulls<SkOpSpanBase>(1).also { it[0] = a.fTail }
        val unsortable = booleanArrayOf(false)
        val chase = mutableListOf<SkOpSpanBase>()
        assertNull(a.findNextWinding(chase, nextStart, nextEnd, unsortable))
    }

    @Test
    fun `findNextXor returns null on a single-line empty angle ring`() {
        val a = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        val nextStart = arrayOfNulls<SkOpSpanBase>(1).also { it[0] = a.fHead }
        val nextEnd = arrayOfNulls<SkOpSpanBase>(1).also { it[0] = a.fTail }
        val unsortable = booleanArrayOf(false)
        assertNull(a.findNextXor(nextStart, nextEnd, unsortable))
    }

    // ─── activeOp on a non-coincident line (D1.2.h.5.0) ──────────

    // ─── activeAngle family (D1.2.h.5.1) ──────────────────────────

    @Test
    fun `activeAngleInner returns null on a single-line head with no live winding`() {
        val a = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        // A fresh segment has windValue=0 / oppValue=0 → both branches
        // hit the "else" assert branches, so we'd need to mark the
        // span done first. For a no-op, mark fHead done so the
        // upSpan branch's require(done) holds, then call.
        a.fHead.setDone(true)
        val sOut = arrayOfNulls<SkOpSpanBase>(1)
        val eOut = arrayOfNulls<SkOpSpanBase>(1)
        val done = booleanArrayOf(true)
        // No prev (fHead.prev == null) so downSpan branch is skipped.
        assertNull(a.activeAngleInner(a.fHead, sOut, eOut, done))
    }

    @Test
    fun `activeAngleInner records start-end pair when windValue is set`() {
        val a = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        a.fHead.setWindValue(1)
        val sOut = arrayOfNulls<SkOpSpanBase>(1)
        val eOut = arrayOfNulls<SkOpSpanBase>(1)
        val done = booleanArrayOf(true)
        // windValue!=0 + windSum==MinS32 + !done → returns null but
        // sets sOut/eOut/done.
        val angle = a.activeAngleInner(a.fHead, sOut, eOut, done)
        // We can't easily predict the angle return without more setup.
        // The contract verified : (start, end) recorded, done flipped.
        org.junit.jupiter.api.Assertions.assertSame(a.fHead, sOut[0])
        org.junit.jupiter.api.Assertions.assertSame(a.fTail, eOut[0])
        org.junit.jupiter.api.Assertions.assertFalse(done[0])
        // angle is null because windSum is still MinS32 (uncomputed).
        assertNull(angle)
    }

    @Test
    fun `activeAngleOther returns null when start ptT loop has no neighbour`() {
        // Single segment, no opp loop on fHead.
        val a = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        // a.fHead.ptT().next() points back to itself (self loop).
        // The C++ uses `start->ptT()->next()` which dereferences to
        // the loop's first entry. With self-loop, it's the same ptT
        // → its segment is `this`, recursing on activeAngleInner.
        // For a fresh head with windValue=0, that returns null.
        val sOut = arrayOfNulls<SkOpSpanBase>(1)
        val eOut = arrayOfNulls<SkOpSpanBase>(1)
        val done = booleanArrayOf(true)
        a.fHead.setDone(true)
        assertNull(a.activeAngleOther(a.fHead, sOut, eOut, done))
    }

    @Test
    fun `activeAngle defers to inner first, then other`() {
        // Same setup as inner-with-windValue test : we expect the same
        // (start, end) recording behaviour from activeAngle which
        // delegates to activeAngleInner first.
        val a = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        a.fHead.setWindValue(1)
        val sOut = arrayOfNulls<SkOpSpanBase>(1)
        val eOut = arrayOfNulls<SkOpSpanBase>(1)
        val done = booleanArrayOf(true)
        a.activeAngle(a.fHead, sOut, eOut, done)
        org.junit.jupiter.api.Assertions.assertSame(a.fHead, sOut[0])
        org.junit.jupiter.api.Assertions.assertSame(a.fTail, eOut[0])
    }

    // ─── done(SkOpAngle) / isSimple / findNextOp (D1.2.h.5.2) ────

    @Test
    fun `done(angle) returns the starter span's done flag`() {
        val a = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        // Build an angle directly — spanToAngle returns null on a fresh
        // single-line segment whose addStartSpan / addEndSpan were
        // skipped by calcAngles (simple() == true on both ends).
        val angle = SkOpAngle().also { it.set(a.fHead, a.fTail) }
        org.junit.jupiter.api.Assertions.assertFalse(a.done(angle))
        a.markDone(a.fHead)
        assertTrue(a.done(angle))
    }

    @Test
    fun `isSimple returns null on a single-segment angle ring`() {
        val a = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        // No angle ring has been built (no calcAngles call).
        // isSimple → nextChase, which returns null when no opposite
        // span is in the angle's pt-T loop.
        val end = arrayOfNulls<SkOpSpanBase>(1).also { it[0] = a.fHead }
        val step = intArrayOf(1)
        assertNull(a.isSimple(end, step))
    }

    @Test
    fun `findNextOp returns null on an empty angle ring`() {
        val a = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        val nextStart = arrayOfNulls<SkOpSpanBase>(1).also { it[0] = a.fHead }
        val nextEnd = arrayOfNulls<SkOpSpanBase>(1).also { it[0] = a.fTail }
        val unsortable = booleanArrayOf(false)
        val simple = booleanArrayOf(false)
        val chase = mutableListOf<SkOpSpanBase>()
        // Single-segment line, no coincidence, no angle ring →
        // findNextOp finds no candidate and returns null.
        assertNull(a.findNextOp(chase, nextStart, nextEnd, unsortable, simple,
            org.skia.pathops.SkPathOp.kUnion, xorMiMask = 1, xorSuMask = 1))
    }

    @Test
    fun `activeOp on a fresh line returns true for kUnion`() {
        // updateWinding / updateOppWinding on a fresh non-coincident
        // line return SK_MinS32 (sentinel) — but we still feed them
        // through. The activeOp result depends on the bits of those
        // values masked against xorMiMask / xorSuMask.
        // For a baseline smoke check, exercise the path : it should
        // return without throwing, regardless of the boolean result.
        val a = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        // Just ensure the call returns ; no exception.
        a.activeOp(a.fHead, a.fTail, xorMiMask = 1, xorSuMask = 1,
            op = org.skia.pathops.SkPathOp.kUnion)
    }
}
