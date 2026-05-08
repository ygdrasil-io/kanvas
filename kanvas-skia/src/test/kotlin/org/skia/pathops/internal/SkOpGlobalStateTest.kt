package org.skia.pathops.internal

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.math.SkPoint

/**
 * Unit tests for [SkOpGlobalState] + plumbing through
 * [SkOpContour] / [SkOpSegment] / [SkOpSpanBase] (Phase D1.2.g.c.3).
 */
class SkOpGlobalStateTest {

    private fun pt(x: Float, y: Float) = SkPoint(fX = x, fY = y)

    @Test
    fun `coincidence get-set roundtrip`() {
        val gs = SkOpGlobalState()
        assertNull(gs.coincidence())
        val coin = SkOpCoincidence()
        gs.setCoincidence(coin)
        assertSame(coin, gs.coincidence())
    }

    @Test
    fun `allocatedOpSpan flag toggles`() {
        val gs = SkOpGlobalState()
        assertFalse(gs.allocatedOpSpan())
        gs.setAllocatedOpSpan()
        assertTrue(gs.allocatedOpSpan())
        gs.resetAllocatedOpSpan()
        assertFalse(gs.allocatedOpSpan())
    }

    @Test
    fun `Contour globalState get-set roundtrip`() {
        val c = SkOpContour()
        assertNull(c.globalState())
        val gs = SkOpGlobalState()
        c.setGlobalState(gs)
        assertSame(gs, c.globalState())
    }

    @Test
    fun `Segment globalState walks through contour`() {
        val c = SkOpContour()
        val gs = SkOpGlobalState()
        c.setGlobalState(gs)
        val seg = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), c)
        assertSame(gs, seg.globalState())
    }

    @Test
    fun `SpanBase globalState walks through segment-contour`() {
        val c = SkOpContour()
        val gs = SkOpGlobalState()
        c.setGlobalState(gs)
        val seg = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), c)
        assertSame(gs, seg.fHead.globalState())
        assertSame(gs, seg.fTail.globalState())
    }

    @Test
    fun `SpanBase globalState returns null when no contour wired`() {
        val seg = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        assertNull(seg.fHead.globalState())
    }
}
