package org.graphiks.math.geometry

import org.graphiks.math.vector.Vector3F32
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Point3F32Test {
    @Test
    fun `point and vector operators preserve three dimensional result types`() {
        val point = Point3F32(10f, 20f, 30f)
        val delta = Vector3F32(3f, -5f, 7f)

        val moved: Point3F32 = point + delta
        val difference: Vector3F32 = moved - point

        assertEquals(13f, moved.x)
        assertEquals(15f, moved.y)
        assertEquals(37f, moved.z)
        assertEquals(3f, difference.x)
        assertEquals(-5f, difference.y)
        assertEquals(7f, difference.z)
    }

    @Test
    fun `distance and midpoint use primitive components`() {
        val point = Point3F32(1f, 2f, 3f)
        val other = Point3F32(4f, 6f, 6f)
        val midpoint = point.midpointTo(other)

        assertEquals(5.8309517f, point.distanceTo(other), 0.000001f)
        assertEquals(2.5f, midpoint.x)
        assertEquals(4f, midpoint.y)
        assertEquals(4.5f, midpoint.z)
    }

    @Test
    fun `finite check and origin report three dimensional primitive coordinates`() {
        assertTrue(Point3F32.Origin.isFinite())
        assertEquals(0f, Point3F32.Origin.z)
        assertFalse(Point3F32(0f, Float.POSITIVE_INFINITY, 0f).isFinite())
    }
}
