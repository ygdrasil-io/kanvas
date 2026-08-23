package org.graphiks.math.geometry

import org.graphiks.math.vector.Vector2F64 as F64Vector
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class Point2F64Test {
    @Test
    fun `point and vector operators preserve semantic result types`() {
        val point = Point2F64(10.0, 20.0)
        val delta = F64Vector(3.0, -5.0)

        val moved: Point2F64 = point + delta
        val difference: F64Vector = moved - point

        assertEquals(13.0, moved.x)
        assertEquals(15.0, moved.y)
        assertEquals(3.0, difference.x)
        assertEquals(-5.0, difference.y)
    }

    @Test
    fun `distance finite check and origin use immutable coordinates`() {
        assertEquals(5.0, Point2F64(1.0, 2.0).distanceTo(Point2F64(4.0, 6.0)))
        assertTrue(Point2F64.Origin.isFinite())
        assertEquals(0.0, Point2F64.Origin.x)
        assertEquals(0.0, Point2F64.Origin.y)
        assertFalse(Point2F64(Double.NaN, 0.0).isFinite())
    }

    @Test
    fun `midpoint preserves finite large F64 coordinates`() {
        val midpoint: Point2F64 = Point2F64(Double.MAX_VALUE, Double.MAX_VALUE)
            .midpointTo(Point2F64(Double.MAX_VALUE, -Double.MAX_VALUE))

        assertTrue(midpoint.x.isFinite())
        assertEquals(Double.MAX_VALUE, midpoint.x)
        assertTrue(midpoint.y.isFinite())
        assertEquals(0.0, midpoint.y)
    }

    @Test
    fun `named F32 and F64 conversions preserve literal components`() {
        val widened: Point2F64 = Point2F32(1.25f, -3.5f).toPoint2F64()
        val narrowed: Point2F32 = Point2F64(7.5, -9.25).toPoint2F32()

        assertEquals(1.25, widened.x)
        assertEquals(-3.5, widened.y)
        assertEquals(7.5f, narrowed.x)
        assertEquals(-9.25f, narrowed.y)
    }

    @Test
    fun `generated equality remains exact by component bits`() {
        assertEquals(Point2F64(Double.NaN, -0.0), Point2F64(Double.NaN, -0.0))
        assertNotEquals(Point2F64(Double.NaN, -0.0), Point2F64(Double.NaN, 0.0))
    }

}
