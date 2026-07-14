package org.graphiks.math.geometry

import kotlin.test.Test

class Point2F64Test {

    @Test
    fun `constructs and exposes x y`() {
        val p = Point2F64(3.0, 4.0)
        kotlin.test.assertEquals(3.0, p.x)
        kotlin.test.assertEquals(4.0, p.y)
    }

    @Test
    fun `default ctor is zero`() {
        val p = Point2F64()
        kotlin.test.assertEquals(0.0, p.x)
        kotlin.test.assertEquals(0.0, p.y)
    }

    @Test
    fun `plusAssign and minusAssign`() {
        val p = Point2F64(1.0, 2.0)
        p += Point2F64(3.0, 4.0)
        kotlin.test.assertEquals(4.0, p.x)
        kotlin.test.assertEquals(6.0, p.y)
        p -= Point2F64(1.0, 1.0)
        kotlin.test.assertEquals(3.0, p.x)
        kotlin.test.assertEquals(5.0, p.y)
    }

    @Test
    fun `divAssign and timesAssign`() {
        val p = Point2F64(6.0, 8.0)
        p /= 2.0
        kotlin.test.assertEquals(3.0, p.x)
        kotlin.test.assertEquals(4.0, p.y)
        p *= 3.0
        kotlin.test.assertEquals(9.0, p.x)
        kotlin.test.assertEquals(12.0, p.y)
    }

    @Test
    fun `dot product`() {
        val a = Point2F64(3.0, 4.0)
        val b = Point2F64(1.0, 2.0)
        kotlin.test.assertEquals(11.0, a.dot(b))
    }

    @Test
    fun `cross product`() {
        val a = Point2F64(3.0, 4.0)
        val b = Point2F64(1.0, 2.0)
        kotlin.test.assertEquals(2.0, a.cross(b))
    }

    @Test
    fun `length and lengthSquared`() {
        val p = Point2F64(3.0, 4.0)
        kotlin.test.assertEquals(25.0, p.lengthSquared())
        kotlin.test.assertEquals(5.0, p.length())
    }

    @Test
    fun `normalize`() {
        val p = Point2F64(3.0, 0.0)
        p.normalize()
        kotlin.test.assertEquals(1.0, p.x, 1e-15)
        kotlin.test.assertEquals(0.0, p.y, 1e-15)
    }

    @Test
    fun `isFinite`() {
        kotlin.test.assertTrue(Point2F64(1.0, 2.0).isFinite())
        kotlin.test.assertFalse(Point2F64(Double.NaN, 0.0).isFinite())
    }

    @Test
    fun `distance and distanceSquared`() {
        val a = Point2F64(0.0, 0.0)
        val b = Point2F64(3.0, 4.0)
        kotlin.test.assertEquals(25.0, a.distanceSquared(b))
        kotlin.test.assertEquals(5.0, a.distance(b))
    }

    @Test
    fun `plus and minus operators`() {
        val a = Point2F64(1.0, 2.0)
        val b = Point2F64(3.0, 4.0)
        val sum = a + b
        kotlin.test.assertEquals(4.0, sum.x)
        kotlin.test.assertEquals(6.0, sum.y)
        val diff = a - b
        kotlin.test.assertEquals(-2.0, diff.x)
        kotlin.test.assertEquals(-2.0, diff.y)
    }

    @Test
    fun `approximatelyZero`() {
        kotlin.test.assertTrue(Point2F64(0.0, 0.0).approximatelyZero())
        kotlin.test.assertTrue(Point2F64(1e-10, -1e-10).approximatelyZero())
        kotlin.test.assertFalse(Point2F64(0.001, 0.0).approximatelyZero())
    }

    @Test
    fun `Mid`() {
        val mid = Point2F64.midpoint(Point2F64.of(0.0, 0.0), Point2F64.of(2.0, 4.0))
        kotlin.test.assertEquals(1.0, mid.x)
        kotlin.test.assertEquals(2.0, mid.y)
    }
}
