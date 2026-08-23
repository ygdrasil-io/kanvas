package org.graphiks.math.vector

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class Vector2F64Test {
    @Test
    fun `F64 arithmetic and products use Double literals`() {
        val a = Vector2F64(3.0, -4.0)
        val b = Vector2F64(-2.0, 5.0)

        val sum = a + b
        assertEquals(1.0, sum.x)
        assertEquals(1.0, sum.y)
        assertEquals(-26.0, a.dot(b))
        assertEquals(7.0, a.cross(b))
        assertEquals(5.0, a.length())
        val normalized = a.normalized()
        assertEquals(0.6, normalized.x, 1e-12)
        assertEquals(-0.8, normalized.y, 1e-12)
    }

    @Test
    fun `F64 mutable conversion does not alias`() {
        val immutable = Vector2F64(2.0, 3.0)
        val mutable = immutable.toMutable()

        mutable.y = 11.0
        assertEquals(3.0, immutable.y)
        assertEquals(11.0, mutable.y)
    }

    @Test
    fun `F64 equality remains exact by component bits`() {
        assertEquals(Vector2F64(Double.NaN, -0.0), Vector2F64(Double.NaN, -0.0))
        assertNotEquals(Vector2F64(Double.NaN, -0.0), Vector2F64(Double.NaN, 0.0))
    }
}
