package org.graphiks.math.vector

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class Vector2F32Test {
    @Test
    fun `axes expose literal components`() {
        assertEquals(0f, Vector2F32.Zero.x)
        assertEquals(0f, Vector2F32.Zero.y)
        assertEquals(1f, Vector2F32.UnitX.x)
        assertEquals(0f, Vector2F32.UnitX.y)
        assertEquals(0f, Vector2F32.UnitY.x)
        assertEquals(1f, Vector2F32.UnitY.y)
    }

    @Test
    fun `arithmetic applies independent scalar expectations`() {
        val a = Vector2F32(5f, -7f)
        val b = Vector2F32(-2f, 3f)

        val sum = a + b
        assertEquals(3f, sum.x)
        assertEquals(-4f, sum.y)
        val difference = a - b
        assertEquals(7f, difference.x)
        assertEquals(-10f, difference.y)
        val negated = -a
        assertEquals(-5f, negated.x)
        assertEquals(7f, negated.y)
        val scaled = a * 2f
        assertEquals(10f, scaled.x)
        assertEquals(-14f, scaled.y)
        val divided = a / 2f
        assertEquals(2.5f, divided.x)
        assertEquals(-3.5f, divided.y)
    }

    @Test
    fun `scalar can multiply vector from the left`() {
        val scaled = 3f * Vector2F32(2f, -4f)

        assertEquals(6f, scaled.x)
        assertEquals(-12f, scaled.y)
    }

    @Test
    fun `dot and cross use independent scalar expectations`() {
        val a = Vector2F32(3f, -4f)
        val b = Vector2F32(-2f, 5f)

        assertEquals(3f * -2f + -4f * 5f, a.dot(b))
        assertEquals(3f * 5f - -4f * -2f, a.cross(b))
    }

    @Test
    fun `length scales large finite components without overflow`() {
        assertEquals(25f, Vector2F32(3f, 4f).lengthSquared())
        assertEquals(5f, Vector2F32(3f, 4f).length())
        assertEquals(Float.MAX_VALUE, Vector2F32(Float.MAX_VALUE, 0f).length())
    }

    @Test
    fun `length classifies zero NaN and infinity`() {
        assertEquals(0f, Vector2F32(-0f, 0f).length())
        assertTrue(Vector2F32(Float.NaN, Float.POSITIVE_INFINITY).length().isNaN())
        assertEquals(
            Float.POSITIVE_INFINITY,
            Vector2F32(Float.NEGATIVE_INFINITY, 1f).length(),
        )
    }

    @Test
    fun `normalization uses literal unit components and collapses near zero`() {
        val normalized = Vector2F32(3f, 4f).normalized()

        assertEquals(0.6f, normalized.x, 1e-6f)
        assertEquals(0.8f, normalized.y, 1e-6f)
        val nearZero = Vector2F32(1e-8f, -1e-8f).normalized()
        assertEquals(0f, nearZero.x)
        assertEquals(0f, nearZero.y)
    }

    @Test
    fun `finite and zero checks classify components`() {
        assertTrue(Vector2F32(1f, 2f).isFinite())
        assertFalse(Vector2F32(Float.NaN, 2f).isFinite())
        assertTrue(Vector2F32(1e-8f, -1e-8f).isZero())
        assertFalse(Vector2F32(1e-4f, 0f).isZero())
    }

    @Test
    fun `fallback equality hashes component bits without identity shortcut`() {
        val first = Vector2F32(Float.NaN, -0f)
        val sameBits = Vector2F32(Float.NaN, -0f)
        val positiveZero = Vector2F32(Float.NaN, 0f)

        assertEquals(first, sameBits)
        assertEquals(first.hashCode(), sameBits.hashCode())
        assertNotEquals(first, positiveZero)
    }

    @Test
    fun `mutable conversion copies components without aliasing`() {
        val immutable = Vector2F32(2f, 3f)
        val mutable = immutable.toMutable()

        mutable.x = 9f
        assertEquals(2f, immutable.x)
        assertEquals(9f, mutable.x)
    }
}
