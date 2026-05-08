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
