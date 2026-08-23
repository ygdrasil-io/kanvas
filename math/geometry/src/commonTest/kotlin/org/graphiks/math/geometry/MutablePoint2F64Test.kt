package org.graphiks.math.geometry

import org.graphiks.math.vector.Vector2F64 as F64Vector
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MutablePoint2F64Test {
    @Test
    fun `mutable point and vector conversions do not share state`() {
        val point = Point2F64(4.0, 8.0)
        val mutable = point.toMutable()

        mutable.translateBy(F64Vector(3.0, -2.0))

        assertEquals(4.0, point.x)
        assertEquals(8.0, point.y)
        assertEquals(7.0, mutable.x)
        assertEquals(6.0, mutable.y)
    }

    @Test
    fun `component comparisons accept immutable and mutable points`() {
        val point = MutablePoint2F64(2.0, -4.0)

        assertTrue(point.hasSameComponentsAs(Point2F64(2.0, -4.0)))
        assertTrue(point.hasSameComponentsAs(MutablePoint2F64(2.0, -4.0)))
        assertFalse(point.hasSameComponentsAs(Point2F64(2.0, 4.0)))
    }

    @Test
    fun `to immutable copies mutable components`() {
        val mutable = MutablePoint2F64(2.0, -4.0)
        val immutable = mutable.toImmutable()

        mutable.x = 10.0
        mutable.y = 11.0

        assertEquals(2.0, immutable.x)
        assertEquals(-4.0, immutable.y)
    }
}
