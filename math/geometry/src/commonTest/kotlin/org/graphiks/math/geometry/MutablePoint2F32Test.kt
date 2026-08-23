package org.graphiks.math.geometry

import org.graphiks.math.vector.Vector2F32
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MutablePoint2F32Test {
    @Test
    fun `translation applies a vector offset`() {
        val point = MutablePoint2F32(2f, -4f)

        point.translateBy(Vector2F32(3f, 5f))

        assertEquals(5f, point.x)
        assertEquals(1f, point.y)
    }

    @Test
    fun `component comparisons accept immutable and mutable points`() {
        val point = MutablePoint2F32(2f, -4f)

        assertTrue(point.hasSameComponentsAs(Point2F32(2f, -4f)))
        assertTrue(point.hasSameComponentsAs(MutablePoint2F32(2f, -4f)))
        assertFalse(point.hasSameComponentsAs(Point2F32(2f, 4f)))
    }

    @Test
    fun `immutable and mutable conversions do not alias components`() {
        val immutable = Point2F32(2f, -4f)
        val mutable = immutable.toMutable()

        mutable.x = 10f
        mutable.y = 11f
        val copied = mutable.toImmutable()
        mutable.x = -1f
        mutable.y = -2f

        assertEquals(2f, immutable.x)
        assertEquals(-4f, immutable.y)
        assertEquals(10f, copied.x)
        assertEquals(11f, copied.y)
    }
}
