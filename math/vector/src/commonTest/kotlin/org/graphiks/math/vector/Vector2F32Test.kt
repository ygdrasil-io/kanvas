package org.graphiks.math.vector

import kotlin.test.*

class Vector2F32Test {

    @Test
    fun testConstants() {
        assertEquals(0f, Vector2F32.Zero.x)
        assertEquals(0f, Vector2F32.Zero.y)
        assertEquals(1f, Vector2F32.UnitX.x)
        assertEquals(0f, Vector2F32.UnitX.y)
    }

    @Test
    fun testPlus() {
        val a = Vector2F32(1f, 2f)
        val b = Vector2F32(3f, 4f)
        assertEquals(Vector2F32(4f, 6f), a + b)
    }

    @Test
    fun testMinus() {
        val a = Vector2F32(5f, 7f)
        val b = Vector2F32(2f, 3f)
        assertEquals(Vector2F32(3f, 4f), a - b)
    }

    @Test
    fun testTimesScalar() {
        val a = Vector2F32(1f, 2f)
        assertEquals(Vector2F32(3f, 6f), a * 3f)
        assertEquals(Vector2F32(3f, 6f), 3f * a)
    }

    @Test
    fun testTimesComponentWise() {
        val a = Vector2F32(2f, 3f)
        val b = Vector2F32(4f, 5f)
        assertEquals(Vector2F32(8f, 15f), a * b)
    }

    @Test
    fun testDiv() {
        assertEquals(Vector2F32(2f, 3f), Vector2F32(4f, 6f) / 2f)
    }

    @Test
    fun testUnaryMinus() {
        assertEquals(Vector2F32(-1f, 2f), -Vector2F32(1f, -2f))
    }

    @Test
    fun testLength() {
        assertEquals(5f, Vector2F32(3f, 4f).length())
        assertEquals(0f, Vector2F32(0f, 0f).length())
    }

    @Test
    fun testLengthSquared() {
        assertEquals(25f, Vector2F32(3f, 4f).lengthSquared())
    }

    @Test
    fun testDot() {
        assertEquals(11f, Vector2F32.of(1f, 2f).dot(Vector2F32.of(3f, 4f)))
    }

    @Test
    fun testCross() {
        assertEquals(-2f, Vector2F32(1f, 2f).cross(Vector2F32(3f, 4f)))
    }

    @Test
    fun testNormalize() {
        val v = Vector2F32(3f, 4f).normalize()
        assertTrue(kotlin.math.abs(v.length() - 1f) < 1e-6f)
    }

    @Test
    fun testNormalizeZero() {
        val v = Vector2F32(0f, 0f).normalize()
        assertEquals(0f, v.x)
        assertEquals(0f, v.y)
    }

    @Test
    fun testDistance() {
        assertEquals(5f, Vector2F32(0f, 0f).distanceTo(Vector2F32(3f, 4f)))
    }

    @Test
    fun testIsFinite() {
        assertTrue(Vector2F32(1f, 2f).isFinite())
        assertFalse(Vector2F32(Float.NaN, 2f).isFinite())
    }

    @Test
    fun testIsZero() {
        assertTrue(Vector2F32(0f, 0f).isZero())
        assertFalse(Vector2F32(1f, 0f).isZero())
    }

    @Test
    fun testLengthOverflowFallback() {
        val x = Float.MAX_VALUE
        val y = 0f
        val v = Vector2F32(x, y)
        assertEquals(Float.MAX_VALUE, v.length())
    }
}
