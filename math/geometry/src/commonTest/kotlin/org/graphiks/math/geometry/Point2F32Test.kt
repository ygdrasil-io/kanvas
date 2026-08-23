package org.graphiks.math.geometry

import org.graphiks.math.vector.Vector2F32
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Point2F32Test {
    @Test
    fun `point and vector operators preserve semantic result types`() {
        val p = Point2F32(10f, 20f)
        val d = Vector2F32(3f, -5f)

        val moved: Point2F32 = p + d
        val delta: Vector2F32 = moved - p

        assertEquals(13f, moved.x)
        assertEquals(15f, moved.y)
        assertEquals(3f, delta.x)
        assertEquals(-5f, delta.y)
    }

    @Test
    fun `distance expected value is computed from primitive components`() {
        assertEquals(5f, Point2F32(1f, 2f).distanceTo(Point2F32(4f, 6f)))
    }

    @Test
    fun `midpoint expected value is computed from primitive components`() {
        val midpoint = Point2F32(Float.MAX_VALUE, -6f)
            .midpointTo(Point2F32(Float.MAX_VALUE, 10f))

        assertEquals(Float.MAX_VALUE, midpoint.x)
        assertEquals(2f, midpoint.y)
    }

    @Test
    fun `finite check and origin report primitive coordinates`() {
        assertTrue(Point2F32.Origin.isFinite())
        assertEquals(0f, Point2F32.Origin.x)
        assertEquals(0f, Point2F32.Origin.y)
        assertFalse(Point2F32(Float.NaN, 0f).isFinite())
    }
}
