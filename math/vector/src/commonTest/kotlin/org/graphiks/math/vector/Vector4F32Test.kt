package org.graphiks.math.vector

import kotlin.test.Test
import kotlin.test.assertEquals

class Vector4F32Test {
    @Test
    fun `4D axes expose literal components`() {
        assertEquals(0f, Vector4F32.Zero.w)
        assertEquals(1f, Vector4F32.UnitX.x)
        assertEquals(1f, Vector4F32.UnitY.y)
        assertEquals(1f, Vector4F32.UnitZ.z)
        assertEquals(1f, Vector4F32.UnitW.w)
    }

    @Test
    fun `4D arithmetic and dot use scalar literals`() {
        val a = Vector4F32(1f, 2f, 3f, 4f)
        val b = Vector4F32(5f, 6f, 7f, 8f)

        val difference = b - a
        assertEquals(4f, difference.x)
        assertEquals(4f, difference.y)
        assertEquals(4f, difference.z)
        assertEquals(4f, difference.w)
        assertEquals(70f, a.dot(b))
    }

    @Test
    fun `4D length and normalization scale large finite values`() {
        val value = Vector4F32(1e30f, 1e30f, 1e30f, 1e30f)

        assertEquals(2e30f, value.length(), 2e24f)
        val normalized = value.normalized()
        assertEquals(0.5f, normalized.x, 1e-6f)
        assertEquals(0.5f, normalized.y, 1e-6f)
        assertEquals(0.5f, normalized.z, 1e-6f)
        assertEquals(0.5f, normalized.w, 1e-6f)
    }
}
