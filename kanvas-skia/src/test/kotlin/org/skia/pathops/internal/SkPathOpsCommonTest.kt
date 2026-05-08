package org.skia.pathops.internal

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.math.SkPoint

/**
 * Unit tests for [SortContourList] + the new [SkOpContour] driver
 * wrappers (Phase D1.2.h.1).
 */
class SkPathOpsCommonTest {

    private fun pt(x: Float, y: Float) = SkPoint(fX = x, fY = y)

    private fun newContourList(globalState: SkOpGlobalState?): SkOpContourHead {
        val head = SkOpContourHead()
        if (globalState != null) head.setGlobalState(globalState)
        return head
    }

    /**
     * Convenience : drop a line segment into [contour] so its `count` is
     * non-zero and `bounds` reflects the line's bbox.
     */
    private fun pushLine(contour: SkOpContour, x0: Float, y0: Float, x1: Float, y1: Float) {
        contour.appendSegment().addLine(arrayOf(pt(x0, y0), pt(x1, y1)), contour)
        contour.complete()
    }

    // ─── SortContourList ──────────────────────────────────────────

    @Test
    fun `SortContourList returns false on an empty list`() {
        val head = newContourList(null)
        val ref = ContourHeadRef(head)
        assertFalse(SortContourList(ref, evenOdd = false, oppEvenOdd = false))
    }

    @Test
    fun `SortContourList drops empty contours`() {
        // Single populated contour, plus one empty appendage.
        val head = newContourList(null)
        pushLine(head, 0f, 0f, 10f, 0f)
        head.appendContour() // empty
        val ref = ContourHeadRef(head)
        assertTrue(SortContourList(ref, evenOdd = false, oppEvenOdd = false))
        // The empty tail is filtered out → fHead is the only survivor.
        assertSame(head, ref.head)
        assertNull(ref.head!!.next())
    }

    @Test
    fun `SortContourList orders contours by bounds-top then bounds-left`() {
        val gs = SkOpGlobalState()
        val head = newContourList(gs)
        // First contour at (10, 20).
        pushLine(head, 10f, 20f, 20f, 20f)
        // Second contour at (0, 5) — lex smaller than first.
        val c2 = head.appendContour()
        c2.setGlobalState(gs)
        pushLine(c2, 0f, 5f, 10f, 5f)
        // Third contour at (0, 5), but lower x — smaller than c2.
        val c3 = head.appendContour()
        c3.setGlobalState(gs)
        pushLine(c3, -5f, 5f, 0f, 5f)
        val ref = ContourHeadRef(head)
        assertTrue(SortContourList(ref, evenOdd = false, oppEvenOdd = false))
        // Sorted order : c3 (left=-5, top=5) → c2 (left=0, top=5) → head (top=20).
        assertSame(c3, ref.head)
        assertSame(c2, ref.head!!.next())
        assertSame(head, ref.head!!.next()!!.next())
        assertNull(ref.head!!.next()!!.next()!!.next())
    }

    @Test
    fun `SortContourList stamps oppXor based on operand`() {
        val head = newContourList(null)
        pushLine(head, 0f, 0f, 10f, 0f)
        head.setOperand(false) // first operand
        val c2 = head.appendContour()
        pushLine(c2, 0f, 5f, 10f, 5f)
        c2.setOperand(true) // second operand
        val ref = ContourHeadRef(head)
        // evenOdd = true (subtrahend's parity), oppEvenOdd = false.
        assertTrue(SortContourList(ref, evenOdd = true, oppEvenOdd = false))
        // operand=false → oppXor = oppEvenOdd = false.
        assertFalse(head.oppXor())
        // operand=true → oppXor = evenOdd = true.
        assertTrue(c2.oppXor())
    }

    @Test
    fun `SortContourList writes the new head onto globalState`() {
        val gs = SkOpGlobalState()
        val head = newContourList(gs)
        pushLine(head, 10f, 20f, 20f, 20f)
        val c2 = head.appendContour()
        c2.setGlobalState(gs)
        pushLine(c2, 0f, 5f, 10f, 5f) // lex-smaller
        val ref = ContourHeadRef(head)
        assertTrue(SortContourList(ref, evenOdd = false, oppEvenOdd = false))
        assertSame(c2, gs.contourHead())
    }

    // ─── SkOpContour driver wrappers ──────────────────────────────

    @Test
    fun `Contour calcAngles walks every segment without throwing`() {
        val head = newContourList(null)
        pushLine(head, 0f, 0f, 10f, 0f)
        head.appendContour().also { pushLine(it, 0f, 5f, 10f, 5f) }
        // Trivial single-line segments have no interior spans, so
        // calcAngles is a structural walk that should just return.
        head.calcAngles() // does not throw
    }

    @Test
    fun `Contour sortAngles returns true when every segment sort succeeds`() {
        val head = newContourList(null)
        pushLine(head, 0f, 0f, 10f, 0f)
        head.calcAngles() // pre-req for sortAngles
        // Single segment, single span — sort is trivially OK.
        assertTrue(head.sortAngles())
    }

    // ─── missingCoincidence / moveMultiples / moveNearby (D1.2.h.2) ──

    @Test
    fun `Segment missingCoincidence returns false on a done segment`() {
        val a = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        a.markAllDone()
        org.junit.jupiter.api.Assertions.assertFalse(a.missingCoincidence())
    }

    @Test
    fun `Segment missingCoincidence returns false on a single-span segment`() {
        // Single line, no inner spans, no opp loop — nothing to find.
        val head = newContourList(SkOpGlobalState().also { it.setCoincidence(SkOpCoincidence()) })
        pushLine(head, 0f, 0f, 10f, 0f)
        org.junit.jupiter.api.Assertions.assertFalse(head.fHead.missingCoincidence())
    }

    @Test
    fun `Segment moveMultiples returns true on a single-span segment`() {
        val a = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        // No span has spanAddsCount > 1 → loop is a no-op.
        assertTrue(a.moveMultiples())
    }

    @Test
    fun `Segment moveNearby returns true on a single-span segment`() {
        val head = newContourList(null)
        pushLine(head, 0f, 0f, 10f, 0f)
        // No alias spans, no near-by adjacent pairs to merge.
        assertTrue(head.fHead.moveNearby())
    }

    // ─── Contour driver wrappers (D1.2.h.2) ───────────────────────

    @Test
    fun `Contour missingCoincidence false on plain non-coincident contours`() {
        val gs = SkOpGlobalState().also { it.setCoincidence(SkOpCoincidence()) }
        val head = newContourList(gs)
        pushLine(head, 0f, 0f, 10f, 0f)
        head.appendContour().also {
            it.setGlobalState(gs)
            pushLine(it, 0f, 5f, 10f, 5f)
        }
        org.junit.jupiter.api.Assertions.assertFalse(head.missingCoincidence())
    }

    @Test
    fun `Contour moveMultiples true on plain contours`() {
        val head = newContourList(null)
        pushLine(head, 0f, 0f, 10f, 0f)
        head.appendContour().also { pushLine(it, 0f, 5f, 10f, 5f) }
        assertTrue(head.moveMultiples())
    }

    @Test
    fun `Contour moveNearby true on plain contours`() {
        val head = newContourList(null)
        pushLine(head, 0f, 0f, 10f, 0f)
        head.appendContour().also { pushLine(it, 0f, 5f, 10f, 5f) }
        assertTrue(head.moveNearby())
    }

    // ─── globalState contourHead get/set ──────────────────────────

    // ─── AddIntersectTs (D1.2.h.3) ────────────────────────────────

    @Test
    fun `AddIntersectTs returns false when test bounds is fully above next`() {
        val gs = SkOpGlobalState().also { it.setCoincidence(SkOpCoincidence()) }
        val above = newContourList(gs)
        // above is at y=0..0
        pushLine(above, 0f, 0f, 10f, 0f)
        val below = SkOpContourHead().also { it.setGlobalState(gs) }
        pushLine(below, 0f, 100f, 10f, 100f) // bounds.top = 100, well above
        val coin = gs.coincidence()!!
        assertFalse(AddIntersectTs(above, below, coin))
    }

    @Test
    fun `AddIntersectTs returns true on disjoint horizontally`() {
        val gs = SkOpGlobalState().also { it.setCoincidence(SkOpCoincidence()) }
        val left = newContourList(gs)
        pushLine(left, 0f, 0f, 10f, 0f)
        val right = SkOpContourHead().also { it.setGlobalState(gs) }
        pushLine(right, 100f, 0f, 110f, 0f) // bounds disjoint
        val coin = gs.coincidence()!!
        assertTrue(AddIntersectTs(left, right, coin))
        // No coincidence pair added.
        assertNull(coin.fHead)
    }

    @Test
    fun `AddIntersectTs splices ptT loops on line-line intersection`() {
        val gs = SkOpGlobalState().also { it.setCoincidence(SkOpCoincidence()) }
        // Two crossing diagonals : (0,0)-(10,10) and (0,10)-(10,0).
        val a = newContourList(gs)
        pushLine(a, 0f, 0f, 10f, 10f)
        val b = SkOpContourHead().also { it.setGlobalState(gs) }
        pushLine(b, 0f, 10f, 10f, 0f)
        val coin = gs.coincidence()!!
        assertTrue(AddIntersectTs(a, b, coin))
        // After intersection, both segments have a fresh interior pt-T at t=0.5.
        org.junit.jupiter.api.Assertions.assertEquals(2, a.fHead.count())
        org.junit.jupiter.api.Assertions.assertEquals(2, b.fHead.count())
        // No coincidence pair (lines just cross — single intersection).
        assertNull(coin.fHead)
    }

    @Test
    fun `AddIntersectTs is a no-op when self-intersecting on a single segment`() {
        val gs = SkOpGlobalState().also { it.setCoincidence(SkOpCoincidence()) }
        val head = newContourList(gs)
        pushLine(head, 0f, 0f, 10f, 0f)
        val coin = gs.coincidence()!!
        // Self-intersect on a single-segment contour : wn loop never starts
        // (no segment after wt) → no work.
        assertTrue(AddIntersectTs(head, head, coin))
        assertNull(coin.fHead)
    }

    // ─── globalState contourHead get/set ──────────────────────────

    // ─── HandleCoincidence + walker statics (D1.2.h.4) ────────────

    @Test
    fun `calc_angles walks every contour without throwing`() {
        val gs = SkOpGlobalState()
        val head = newContourList(gs)
        pushLine(head, 0f, 0f, 10f, 0f)
        head.appendContour().also { it.setGlobalState(gs); pushLine(it, 0f, 5f, 10f, 5f) }
        calc_angles(head)
    }

    @Test
    fun `missing_coincidence false on plain non-coincident contours`() {
        val gs = SkOpGlobalState().also { it.setCoincidence(SkOpCoincidence()) }
        val head = newContourList(gs)
        pushLine(head, 0f, 0f, 10f, 0f)
        head.appendContour().also { it.setGlobalState(gs); pushLine(it, 0f, 5f, 10f, 5f) }
        assertFalse(missing_coincidence(head))
    }

    @Test
    fun `move_multiples and move_nearby return true on single-segment chain`() {
        val head = newContourList(null)
        pushLine(head, 0f, 0f, 10f, 0f)
        assertTrue(move_multiples(head))
        assertTrue(move_nearby(head))
    }

    @Test
    fun `sort_angles returns true on single-segment chain`() {
        val head = newContourList(null)
        pushLine(head, 0f, 0f, 10f, 0f)
        head.calcAngles()
        assertTrue(sort_angles(head))
    }

    @Test
    fun `HandleCoincidence returns true on a non-overlapping contour pair`() {
        val gs = SkOpGlobalState().also { it.setCoincidence(SkOpCoincidence()) }
        val head = newContourList(gs)
        pushLine(head, 0f, 0f, 10f, 0f)
        head.appendContour().also { it.setGlobalState(gs); pushLine(it, 0f, 5f, 10f, 5f) }
        // No coincidence to fix — orchestrator just walks the pipeline
        // and returns true.
        assertTrue(HandleCoincidence(head, gs.coincidence()!!))
    }

    @Test
    fun `HandleCoincidence returns true on an empty coincidence container`() {
        val gs = SkOpGlobalState().also { it.setCoincidence(SkOpCoincidence()) }
        val head = newContourList(gs)
        pushLine(head, 0f, 0f, 10f, 0f)
        // Single contour, single segment — every step should short-circuit
        // on the empty / single-element fast path.
        assertTrue(HandleCoincidence(head, gs.coincidence()!!))
    }

    // ─── globalState contourHead get/set ──────────────────────────

    // ─── AngleWinding (D1.2.h.5.3) ────────────────────────────────

    @Test
    fun `AngleWinding returns null on a span with no angle ring`() {
        val a = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        val winding = intArrayOf(0)
        val sortable = booleanArrayOf(false)
        // Single-line segment, calcAngles is a no-op → spanToAngle returns
        // null → AngleWinding fast-fails.
        assertNull(AngleWinding(a.fHead, a.fTail, winding, sortable))
        assertEquals(SkOpSpan.SK_MinS32, winding[0])
    }

    // ─── FindSortableTop stub / bridgeOp skeleton (D1.2.h.5.4) ────

    @Test
    fun `FindSortableTop returns null on an empty contour list`() {
        val head = newContourList(null)
        // No segments → contour.count() == 0 → contour.findSortableTop
        // sets fDone and returns null → FindSortableTop returns null.
        assertNull(FindSortableTop(head))
    }

    @Test
    fun `FindSortableTop returns the head span on a fresh single-line contour`() {
        val gs = SkOpGlobalState()
        val head = newContourList(gs)
        pushLine(head, 0f, 0f, 10f, 0f)
        // First non-done span on the only segment → fHead. sortableTop
        // succeeds with empty hit list (single-segment contour, no
        // ray crossings) → findSortableTop returns fHead.
        org.junit.jupiter.api.Assertions.assertSame(head.fHead.fHead, FindSortableTop(head))
    }

    @Test
    fun `bridgeOp on a fresh single-segment contour completes without emitting`() {
        val gs = SkOpGlobalState().also { it.setCoincidence(SkOpCoincidence()) }
        val head = newContourList(gs)
        pushLine(head, 0f, 0f, 10f, 0f)
        val writer = SkPathWriter(org.skia.foundation.SkPathFillType.kEvenOdd)
        // FindSortableTop returns the head span ; bridgeOp's inner
        // loop sees windValue=0 / oppValue=0 throughout, activeOp
        // returns false → markAndChaseDone path, no curves emitted.
        assertTrue(bridgeOp(head, org.skia.pathops.SkPathOp.kUnion,
            xorMask = SkPathOpsMask.kWinding,
            xorOpMask = SkPathOpsMask.kWinding,
            writer = writer))
    }

    @Test
    fun `findChaseOp returns true with null result on empty chase buffer`() {
        val chase = mutableListOf<SkOpSpanBase>()
        val s = arrayOfNulls<SkOpSpanBase>(1)
        val e = arrayOfNulls<SkOpSpanBase>(1)
        val r = arrayOfNulls<SkOpSegment>(1).apply { this[0] = null }
        assertTrue(findChaseOp(chase, s, e, r))
        assertNull(r[0])
    }

    // ─── globalState contourHead get/set ──────────────────────────

    @Test
    fun `GlobalState contourHead get-set roundtrip`() {
        val gs = SkOpGlobalState()
        assertNull(gs.contourHead())
        val head = newContourList(null)
        gs.setContourHead(head)
        assertSame(head, gs.contourHead())
        gs.setContourHead(null)
        assertNull(gs.contourHead())
    }
}
