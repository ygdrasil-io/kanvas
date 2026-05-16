package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.graphiks.math.SkPoint
import org.graphiks.math.SkVector
import kotlin.math.PI
import kotlin.math.abs

/**
 * Coverage for [SkContourMeasure] and [SkContourMeasureIter].
 * Smoke-tests the four core operations (length, getPosTan,
 * getSegment, isClosed) on simple paths whose arc-length we can
 * derive analytically, plus the iterator's multi-contour stepping.
 */
class SkContourMeasureTest {

    private val tolerance = 1e-3f

    @Test
    fun `length of unit square is 4`() {
        // Closed unit square — perimeter is exactly 4.
        val path = SkPathBuilder()
            .moveTo(0f, 0f)
            .lineTo(1f, 0f)
            .lineTo(1f, 1f)
            .lineTo(0f, 1f)
            .close()
            .detach()
        val iter = SkContourMeasureIter(path, forceClosed = false)
        val m = iter.next()
        assertNotNull(m)
        assertEquals(4f, m!!.length(), tolerance)
        assertTrue(m.isClosed())
    }

    @Test
    fun `length of straight line is the segment length`() {
        // Horizontal line of length 10.
        val path = SkPathBuilder().moveTo(0f, 0f).lineTo(10f, 0f).detach()
        val m = SkContourMeasureIter(path, forceClosed = false).next()
        assertNotNull(m)
        assertEquals(10f, m!!.length(), tolerance)
        assertFalse(m.isClosed())
    }

    @Test
    fun `getPosTan at midpoint of a horizontal line`() {
        val path = SkPathBuilder().moveTo(0f, 0f).lineTo(10f, 0f).detach()
        val m = SkContourMeasureIter(path, forceClosed = false).next()!!
        val pos = SkPoint()
        val tan = SkVector()
        assertTrue(m.getPosTan(5f, pos, tan))
        assertEquals(5f, pos.fX, tolerance)
        assertEquals(0f, pos.fY, tolerance)
        // Unit tangent pointing along +X.
        assertEquals(1f, tan.fX, tolerance)
        assertEquals(0f, tan.fY, tolerance)
    }

    @Test
    fun `getPosTan pins distance to 0 length range`() {
        val path = SkPathBuilder().moveTo(0f, 0f).lineTo(10f, 0f).detach()
        val m = SkContourMeasureIter(path, forceClosed = false).next()!!
        val pos = SkPoint()
        // Beyond end → pinned to length.
        assertTrue(m.getPosTan(999f, pos, null))
        assertEquals(10f, pos.fX, tolerance)
        // Below 0 → pinned to 0.
        assertTrue(m.getPosTan(-5f, pos, null))
        assertEquals(0f, pos.fX, tolerance)
    }

    @Test
    fun `getSegment extracts a sub-range as a polyline`() {
        // Horizontal segment 0..10; extract 2..8.
        val path = SkPathBuilder().moveTo(0f, 0f).lineTo(10f, 0f).detach()
        val m = SkContourMeasureIter(path, forceClosed = false).next()!!
        val dst = SkPathBuilder()
        assertTrue(m.getSegment(2f, 8f, dst, startWithMoveTo = true))
        val sub = dst.detach()
        // First and last points of the extracted polyline should match the
        // requested sub-range exactly (line case, no curve flattening).
        val pts = sub.points()
        assertEquals(2f, pts.first().fX, tolerance)
        assertEquals(8f, pts.last().fX, tolerance)
    }

    @Test
    fun `getSegment returns false on empty span`() {
        val path = SkPathBuilder().moveTo(0f, 0f).lineTo(10f, 0f).detach()
        val m = SkContourMeasureIter(path, forceClosed = false).next()!!
        val dst = SkPathBuilder()
        // startD >= stopD → false.
        assertFalse(m.getSegment(5f, 5f, dst, startWithMoveTo = true))
        assertFalse(m.getSegment(8f, 2f, dst, startWithMoveTo = true))
    }

    @Test
    fun `isClosed true for kClose-terminated contour, false for open`() {
        val open = SkPathBuilder().moveTo(0f, 0f).lineTo(1f, 0f).detach()
        assertFalse(SkContourMeasureIter(open, forceClosed = false).next()!!.isClosed())

        val closed = SkPathBuilder()
            .moveTo(0f, 0f).lineTo(1f, 0f).lineTo(1f, 1f).close().detach()
        assertTrue(SkContourMeasureIter(closed, forceClosed = false).next()!!.isClosed())
    }

    @Test
    fun `forceClosed reports isClosed=true and closes the polyline`() {
        // Open triangle — forceClosed should add the implicit closing edge.
        val tri = SkPathBuilder()
            .moveTo(0f, 0f)
            .lineTo(3f, 0f)
            .lineTo(0f, 4f)
            .detach()
        val m = SkContourMeasureIter(tri, forceClosed = true).next()!!
        assertTrue(m.isClosed())
        // Perimeter = 3 + 5 + 4 = 12 (Pythagorean closure).
        assertEquals(12f, m.length(), 1e-2f)
    }

    @Test
    fun `iterator walks all contours of a multi-move path`() {
        // Two disjoint horizontal segments.
        val path = SkPathBuilder()
            .moveTo(0f, 0f).lineTo(10f, 0f)
            .moveTo(0f, 5f).lineTo(20f, 5f)
            .detach()
        val iter = SkContourMeasureIter(path, forceClosed = false)
        val m1 = iter.next()
        val m2 = iter.next()
        val m3 = iter.next()
        assertNotNull(m1)
        assertNotNull(m2)
        assertNull(m3)
        assertEquals(10f, m1!!.length(), tolerance)
        assertEquals(20f, m2!!.length(), tolerance)
    }

    @Test
    fun `getMatrix populates rotation and translation`() {
        // Diagonal line — at midpoint, tangent is (1/√2, 1/√2).
        val path = SkPathBuilder().moveTo(0f, 0f).lineTo(10f, 10f).detach()
        val m = SkContourMeasureIter(path, forceClosed = false).next()!!
        val len = m.length()
        // Midpoint distance is len/2; position should be (5, 5).
        val slot = arrayOfNulls<org.graphiks.math.SkMatrix>(1)
        assertTrue(m.getMatrix(len * 0.5f, slot))
        val mx = slot[0]!!
        assertEquals(5f, mx.tx, 1e-2f)
        assertEquals(5f, mx.ty, 1e-2f)
        // Rotation block must be a unit cos/sin (length 1).
        val mag = mx.sx * mx.sx + mx.ky * mx.ky
        assertEquals(1f, mag, 1e-3f)
    }

    @Test
    fun `quad curve length is finite and close to chord lower bound`() {
        // Quadratic from (0,0) through (5,5) to (10,0).
        // The arc length is between the chord (10) and the control-polygon length (~14.14).
        val path = SkPathBuilder()
            .moveTo(0f, 0f)
            .quadTo(5f, 5f, 10f, 0f)
            .detach()
        val m = SkContourMeasureIter(path, forceClosed = false).next()!!
        val len = m.length()
        assertTrue(len in 10f..15f, "quad length $len out of expected range")
    }

    @Test
    fun `cubic curve length is finite and bounded`() {
        // S-curve cubic.
        val path = SkPathBuilder()
            .moveTo(0f, 0f)
            .cubicTo(0f, 5f, 10f, -5f, 10f, 0f)
            .detach()
        val m = SkContourMeasureIter(path, forceClosed = false).next()!!
        val len = m.length()
        // Chord = 10, hull length = 5 + sqrt(100+100) + 5 = 5 + 14.14 + 5 ≈ 24.
        assertTrue(len in 10f..30f, "cubic length $len out of expected range")
    }

    @Test
    fun `zero-length contour is skipped by iterator`() {
        // moveTo with no subsequent geometry.
        val path = SkPathBuilder()
            .moveTo(0f, 0f)
            .moveTo(5f, 5f).lineTo(10f, 5f)
            .detach()
        val iter = SkContourMeasureIter(path, forceClosed = false)
        val m = iter.next()
        assertNotNull(m)
        // The lone moveTo gets skipped — first measure is the second contour.
        assertEquals(5f, m!!.length(), tolerance)
        assertNull(iter.next())
    }

    @Test
    fun `circle arc length approximates 2 pi r`() {
        // Circle of radius 10 via four quads — flattening should land within 5% of 2*pi*r.
        val r = 10f
        val k = 0.5522847498f * r // cubic-quadrant approximation factor.
        val path = SkPathBuilder()
            .moveTo(r, 0f)
            .cubicTo(r, k, k, r, 0f, r)
            .cubicTo(-k, r, -r, k, -r, 0f)
            .cubicTo(-r, -k, -k, -r, 0f, -r)
            .cubicTo(k, -r, r, -k, r, 0f)
            .close()
            .detach()
        val m = SkContourMeasureIter(path, forceClosed = false, resScale = 2f).next()!!
        val expected = (2.0 * PI * r).toFloat()
        val err = abs(m.length() - expected) / expected
        assertTrue(err < 0.01f, "circle perimeter ${m.length()} vs $expected (rel err $err)")
    }
}
