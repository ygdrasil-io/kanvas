package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.math.SkPoint
import org.skia.math.SkVector

/**
 * Coverage for [SkPathMeasure] — the single-contour wrapper around
 * [SkContourMeasureIter]. The bulk of the measurement logic is
 * exercised by [SkContourMeasureTest]; this file focuses on the
 * wrapper's nextContour/setPath/lifecycle semantics.
 */
class SkPathMeasureTest {

    private val tolerance = 1e-3f

    @Test
    fun `default constructor reports zero length`() {
        val pm = SkPathMeasure()
        assertEquals(0f, pm.getLength(), tolerance)
        assertFalse(pm.isClosed())
        assertFalse(pm.getPosTan(0f, SkPoint(), SkVector()))
    }

    @Test
    fun `length and getPosTan match the underlying contour`() {
        val path = SkPathBuilder().moveTo(0f, 0f).lineTo(10f, 0f).detach()
        val pm = SkPathMeasure(path, forceClosed = false)
        assertEquals(10f, pm.getLength(), tolerance)
        val pos = SkPoint()
        assertTrue(pm.getPosTan(5f, pos, null))
        assertEquals(5f, pos.fX, tolerance)
        assertEquals(0f, pos.fY, tolerance)
    }

    @Test
    fun `isClosed mirrors the current contour`() {
        val openPath = SkPathBuilder().moveTo(0f, 0f).lineTo(1f, 0f).detach()
        assertFalse(SkPathMeasure(openPath, forceClosed = false).isClosed())

        val closedPath = SkPathBuilder()
            .moveTo(0f, 0f).lineTo(1f, 0f).lineTo(1f, 1f).close().detach()
        assertTrue(SkPathMeasure(closedPath, forceClosed = false).isClosed())
    }

    @Test
    fun `nextContour walks 2+ moveTo's then returns false`() {
        val path = SkPathBuilder()
            .moveTo(0f, 0f).lineTo(10f, 0f)
            .moveTo(0f, 5f).lineTo(30f, 5f)
            .moveTo(0f, 10f).lineTo(50f, 10f)
            .detach()
        val pm = SkPathMeasure(path, forceClosed = false)
        assertEquals(10f, pm.getLength(), tolerance)
        assertTrue(pm.nextContour())
        assertEquals(30f, pm.getLength(), tolerance)
        assertTrue(pm.nextContour())
        assertEquals(50f, pm.getLength(), tolerance)
        assertFalse(pm.nextContour())
        // After exhaustion, getLength returns 0.
        assertEquals(0f, pm.getLength(), tolerance)
    }

    @Test
    fun `setPath re-targets the wrapper`() {
        val first = SkPathBuilder().moveTo(0f, 0f).lineTo(5f, 0f).detach()
        val second = SkPathBuilder().moveTo(0f, 0f).lineTo(20f, 0f).detach()
        val pm = SkPathMeasure(first, forceClosed = false)
        assertEquals(5f, pm.getLength(), tolerance)
        pm.setPath(second, forceClosed = false)
        assertEquals(20f, pm.getLength(), tolerance)
    }

    @Test
    fun `setPath with null detaches the wrapper`() {
        val first = SkPathBuilder().moveTo(0f, 0f).lineTo(5f, 0f).detach()
        val pm = SkPathMeasure(first, forceClosed = false)
        assertEquals(5f, pm.getLength(), tolerance)
        pm.setPath(null, forceClosed = false)
        assertEquals(0f, pm.getLength(), tolerance)
    }

    @Test
    fun `getSegment delegates to the current contour`() {
        val path = SkPathBuilder().moveTo(0f, 0f).lineTo(10f, 0f).detach()
        val pm = SkPathMeasure(path, forceClosed = false)
        val dst = SkPathBuilder()
        assertTrue(pm.getSegment(2f, 8f, dst, startWithMoveTo = true))
        val sub = dst.detach()
        assertTrue(sub.countPoints() >= 2)
    }
}
