package org.graphiks.math.geometry

import org.graphiks.math.vector.Vector2F32
import kotlin.test.Test

class Line2F64Test {

    @Test
    fun `constructs with two default points`() {
        val line = Line2F64()
        kotlin.test.assertEquals(0.0, line[0].x)
        kotlin.test.assertEquals(0.0, line[1].y)
    }

    @Test
    fun `set from Vector2F32 pair`() {
        val line = Line2F64()
        line.set(Vector2F32.of(1f, 2f), Vector2F32.of(3f, 4f))
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
            Line2F64(arrayOf(Point2F64()))
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
