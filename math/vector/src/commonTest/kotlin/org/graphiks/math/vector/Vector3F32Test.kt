package org.graphiks.math.vector

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Vector3F32Test {
    @Test
    fun `3D axes expose literal components`() {
        assertEquals(0f, Vector3F32.Zero.x)
        assertEquals(1f, Vector3F32.UnitX.x)
        assertEquals(1f, Vector3F32.UnitY.y)
        assertEquals(1f, Vector3F32.UnitZ.z)
        assertEquals(0f, Vector3F32.UnitZ.x)
        assertEquals(0f, Vector3F32.UnitZ.y)
    }

    @Test
    fun `3D cross uses hand-derived components`() {
        val cross = Vector3F32(1f, 2f, 3f).cross(Vector3F32(4f, -5f, 6f))

        assertEquals(27f, cross.x)
        assertEquals(6f, cross.y)
        assertEquals(-13f, cross.z)
    }

    @Test
    fun `3D dot and arithmetic use scalar literals`() {
        val a = Vector3F32(1f, 2f, 3f)
        val b = Vector3F32(4f, 5f, 6f)

        assertEquals(32f, a.dot(b))
        val sum = a + b
        assertEquals(5f, sum.x)
        assertEquals(7f, sum.y)
        assertEquals(9f, sum.z)
        val scaled = 2f * a
        assertEquals(2f, scaled.x)
        assertEquals(4f, scaled.y)
        assertEquals(6f, scaled.z)
    }

    @Test
    fun `3D length and normalization scale large finite values`() {
        val value = Vector3F32(1e30f, 1e30f, 1e30f)
        val expectedLength = 1.7320508e30f

        assertEquals(expectedLength, value.length(), expectedLength * 1e-6f)
        val normalized = value.normalized()
        assertEquals(0.57735026f, normalized.x, 1e-6f)
        assertEquals(0.57735026f, normalized.y, 1e-6f)
        assertEquals(0.57735026f, normalized.z, 1e-6f)
    }

    @Test
    fun `3D length prioritizes NaN over infinity`() {
        assertTrue(Vector3F32(Float.NaN, Float.POSITIVE_INFINITY, 0f).length().isNaN())
        assertEquals(
            Float.POSITIVE_INFINITY,
            Vector3F32(Float.POSITIVE_INFINITY, 1f, 2f).length(),
        )
    }
}
