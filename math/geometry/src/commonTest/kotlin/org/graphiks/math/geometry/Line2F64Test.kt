package org.graphiks.math.geometry

import org.graphiks.math.vector.Vector2F64 as F64Vector
import kotlin.test.Test

class Line2F64Test {

    @Test
    fun `constructs with two default points`() {
        val line = Line2F64()
        kotlin.test.assertEquals(Point2F64.Origin, line.start)
        kotlin.test.assertEquals(Point2F64.Origin, line.end)
    }

    @Test
    fun `line direction is endpoint difference`() {
        val line = Line2F64(arrayOf(Point2F64(2.0, 7.0), Point2F64(11.0, -5.0)))

        val direction: F64Vector = line.direction()

        kotlin.test.assertEquals(9.0, direction.x)
        kotlin.test.assertEquals(-12.0, direction.y)
    }

    @Test
    fun `nearPoint preserves pathops distance underflow behavior end to end`() {
        val line = Line2F64(
            arrayOf(Point2F64(0.0, 0.0), Point2F64(1e-150, 0.0)),
        )
        val verticalOffset = 1e-163

        kotlin.test.assertEquals(0.0, verticalOffset * verticalOffset)
        kotlin.test.assertTrue(1e-150 + verticalOffset > 1e-150)
        kotlin.test.assertEquals(0.5, line.nearPoint(Point2F64(5e-151, verticalOffset)))
    }

    @Test
    fun `nearRay preserves pathops distance underflow behavior end to end`() {
        val line = Line2F64(
            arrayOf(Point2F64(0.0, 0.0), Point2F64(1e-150, 0.0)),
        )
        val verticalOffset = 1e-163

        kotlin.test.assertEquals(0.0, verticalOffset * verticalOffset)
        kotlin.test.assertTrue(1e-150 + verticalOffset > 1e-150)
        kotlin.test.assertTrue(line.nearRay(Point2F64(5e-151, verticalOffset)))
    }

    @Test
    fun `nearRay preserves its wider near-zero ULP tolerance`() {
        val line = Line2F64(
            arrayOf(Point2F64(0.0, 0.0), Point2F64(1e-14, 0.0)),
        )

        kotlin.test.assertTrue(line.nearRay(Point2F64(5e-15, 5e-14)))
    }

    @Test
    fun `nearPoint preserves pathops distance overflow behavior end to end`() {
        val baseline = 1e300
        val adjacent = 1.0000000000000002e300
        val horizontalOffset = adjacent - baseline
        val line = Line2F64(
            arrayOf(Point2F64(baseline, 0.0), Point2F64(baseline, 1.0)),
        )

        kotlin.test.assertTrue(horizontalOffset.isFinite())
        kotlin.test.assertEquals(Double.POSITIVE_INFINITY, horizontalOffset * horizontalOffset)
        kotlin.test.assertEquals(adjacent, baseline + horizontalOffset)
        kotlin.test.assertEquals(-1.0, line.nearPoint(Point2F64(adjacent, 0.5)))
    }

    @Test
    fun `nearRay preserves pathops distance overflow behavior end to end`() {
        val baseline = 1e300
        val adjacent = 1.0000000000000002e300
        val horizontalOffset = adjacent - baseline
        val line = Line2F64(
            arrayOf(Point2F64(baseline, 0.0), Point2F64(baseline, 1.0)),
        )

        kotlin.test.assertTrue(horizontalOffset.isFinite())
        kotlin.test.assertEquals(Double.POSITIVE_INFINITY, horizontalOffset * horizontalOffset)
        kotlin.test.assertEquals(adjacent, baseline + horizontalOffset)
        kotlin.test.assertFalse(line.nearRay(Point2F64(adjacent, 0.5)))
    }

    @Test
    fun `set from Point2F32 pair`() {
        val line = Line2F64()
        line.set(Point2F32(1f, 2f), Point2F32(3f, 4f))
        kotlin.test.assertEquals(1.0, line[0].x)
        kotlin.test.assertEquals(2.0, line[0].y)
        kotlin.test.assertEquals(3.0, line[1].x)
        kotlin.test.assertEquals(4.0, line[1].y)
    }

    @Test
    fun `ptAtT endpoints`() {
        val a = Point2F64(1.0, 2.0)
        val b = Point2F64(3.0, 4.0)
        val line = Line2F64(arrayOf(a, b))
        kotlin.test.assertEquals(a, line.ptAtT(0.0))
        kotlin.test.assertEquals(b, line.ptAtT(1.0))
    }

    @Test
    fun `ptAtT midpoint`() {
        val a = Point2F64(1.0, 2.0)
        val b = Point2F64(3.0, 4.0)
        val line = Line2F64(arrayOf(a, b))
        val mid = line.ptAtT(0.5)
        kotlin.test.assertEquals(2.0, mid.x)
        kotlin.test.assertEquals(3.0, mid.y)
    }

    @Test
    fun `exactPoint returns index or -1`() {
        val a = Point2F64(1.0, 2.0)
        val b = Point2F64(5.0, 6.0)
        val line = Line2F64(arrayOf(a, b))
        kotlin.test.assertEquals(0.0, line.exactPoint(a))
        kotlin.test.assertEquals(1.0, line.exactPoint(b))
        kotlin.test.assertEquals(-1.0, line.exactPoint(Point2F64(3.0, 4.0)))
    }

    @Test
    fun `constructor requires exactly 2 points`() {
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            Line2F64(arrayOf(Point2F64.Origin))
        }
    }

    @Test
    fun `equals and hashCode`() {
        val a = Line2F64(arrayOf(Point2F64(1.0, 2.0), Point2F64(3.0, 4.0)))
        val b = Line2F64(arrayOf(Point2F64(1.0, 2.0), Point2F64(3.0, 4.0)))
        kotlin.test.assertEquals(a, b)
        kotlin.test.assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `ExactPointH matches horizontal endpoints`() {
        val xy = Point2F64(4.0, 7.0)
        kotlin.test.assertEquals(0.0, Line2F64.exactPointH(xy, 4.0, 10.0, 7.0))
        kotlin.test.assertEquals(1.0, Line2F64.exactPointH(xy, 0.0, 4.0, 7.0))
        kotlin.test.assertEquals(-1.0, Line2F64.exactPointH(xy, 0.0, 10.0, 6.0))
    }

    @Test
    fun `ExactPointV matches vertical endpoints`() {
        val xy = Point2F64(5.0, 3.0)
        kotlin.test.assertEquals(0.0, Line2F64.exactPointV(xy, 3.0, 10.0, 5.0))
        kotlin.test.assertEquals(1.0, Line2F64.exactPointV(xy, 0.0, 3.0, 5.0))
        kotlin.test.assertEquals(-1.0, Line2F64.exactPointV(xy, 0.0, 10.0, 4.0))
    }
}
