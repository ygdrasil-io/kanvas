package org.graphiks.math

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.math.sqrt

/**
 * Sanity checks for [SkV3] — the minimal vector type used by
 * `Sk3DView` / `SkCamera3D`.
 */
class SkV3Test {

    private fun assertVec(expected: SkV3, actual: SkV3, eps: Float = 1e-5f) {
        assertEquals(expected.x, actual.x, eps)
        assertEquals(expected.y, actual.y, eps)
        assertEquals(expected.z, actual.z, eps)
    }

    @Test
    fun `plus minus and unary minus`() {
        val a = SkV3(1f, 2f, 3f); val b = SkV3(4f, 5f, 6f)
        assertVec(SkV3(5f, 7f, 9f), a + b)
        assertVec(SkV3(-3f, -3f, -3f), a - b)
        assertVec(SkV3(-1f, -2f, -3f), -a)
    }

    @Test
    fun `scalar multiplication on both sides`() {
        val a = SkV3(1f, 2f, 3f)
        assertVec(SkV3(2f, 4f, 6f), a * 2f)
        assertVec(SkV3(2f, 4f, 6f), 2f * a)
    }

    @Test
    fun `dot product matches the manual formula`() {
        val a = SkV3(1f, 2f, 3f); val b = SkV3(4f, 5f, 6f)
        val expected = 1f * 4f + 2f * 5f + 3f * 6f
        assertEquals(expected, a.dot(b))
        assertEquals(expected, SkV3.Dot(a, b))
    }

    @Test
    fun `operator times SkV3 is component-wise, matching Skia upstream`() {
        // Upstream `SkV3 operator*(const SkV3&)` (include/core/SkM44.h:74)
        // returns the component-wise product. Previously this Kotlin port
        // returned the dot product as a Float — see audit divergence #3.
        val a = SkV3(2f, 3f, 4f)
        val b = SkV3(5f, 6f, 7f)
        assertVec(SkV3(10f, 18f, 28f), a * b)
        // Sanity: dot product is now exposed only via `dot` / `Dot`.
        assertEquals(56f, a.dot(b))
        assertEquals(56f, SkV3.Dot(a, b))
    }

    @Test
    fun `Cross companion mirrors instance cross`() {
        val a = SkV3(1f, 2f, 3f); val b = SkV3(4f, 5f, 6f)
        assertVec(a.cross(b), SkV3.Cross(a, b))
    }

    @Test
    fun `cross product satisfies right-hand rule`() {
        val x = SkV3(1f, 0f, 0f); val y = SkV3(0f, 1f, 0f)
        assertVec(SkV3(0f, 0f, 1f), x.cross(y))
        assertVec(SkV3(0f, 0f, -1f), y.cross(x))
    }

    @Test
    fun `length and lengthSquared`() {
        val v = SkV3(2f, 3f, 6f)
        assertEquals(49f, v.lengthSquared(), 1e-4f)
        assertEquals(7f, v.length(), 1e-4f)
    }

    @Test
    fun `normalize returns unit length`() {
        val v = SkV3(3f, 0f, 4f).normalize()
        assertEquals(1f, v.length(), 1e-5f)
        assertEquals(0.6f, v.x, 1e-5f); assertEquals(0f, v.y); assertEquals(0.8f, v.z, 1e-5f)
    }

    @Test
    fun `normalize of zero is zero`() {
        val z = SkV3(0f, 0f, 0f).normalize()
        assertEquals(0f, z.length())
    }

    @Test
    fun `length of unit axes is 1`() {
        for (axis in listOf(SkV3(1f, 0f, 0f), SkV3(0f, 1f, 0f), SkV3(0f, 0f, 1f))) {
            assertEquals(1f, axis.length(), 1e-6f)
            assertEquals(1f, sqrt(axis.dot(axis).toDouble()).toFloat(), 1e-6f)
        }
    }
}
