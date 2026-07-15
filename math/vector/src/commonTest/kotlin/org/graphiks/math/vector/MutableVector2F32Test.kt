package org.graphiks.math.vector

import kotlin.test.*

class MutableVector2F32Test {

    @Test
    fun testOf() {
        val v = MutableVector2F32.of(1f, 2f)
        assertEquals(1f, v.x)
        assertEquals(2f, v.y)
    }

    @Test
    fun testDefault() {
        val v = MutableVector2F32.of()
        assertEquals(0f, v.x)
        assertEquals(0f, v.y)
    }

    @Test
    fun testSet() {
        val v = MutableVector2F32.of(1f, 2f)
        v.set(3f, 4f)
        assertEquals(3f, v.x)
        assertEquals(4f, v.y)
    }

    @Test
    fun testOffset() {
        val v = MutableVector2F32.of(1f, 2f)
        v.offset(3f, 4f)
        assertEquals(4f, v.x)
        assertEquals(6f, v.y)
    }

    @Test
    fun testScale() {
        val v = MutableVector2F32.of(3f, 4f)
        v.scale(2f)
        assertEquals(6f, v.x)
        assertEquals(8f, v.y)
    }

    @Test
    fun testNegate() {
        val v = MutableVector2F32.of(1f, -2f)
        v.negate()
        assertEquals(-1f, v.x)
        assertEquals(2f, v.y)
    }

    @Test
    fun testLength() {
        assertEquals(5f, MutableVector2F32.of(3f, 4f).length())
    }

    @Test
    fun testNormalize() {
        val v = MutableVector2F32.of(3f, 4f)
        assertTrue(v.normalize())
        assertTrue(kotlin.math.abs(v.length() - 1f) < 1e-6f)
    }

    @Test
    fun testNormalizeZero() {
        val v = MutableVector2F32.of(0f, 0f)
        assertFalse(v.normalize())
        assertEquals(0f, v.x)
        assertEquals(0f, v.y)
    }

    @Test
    fun testSetLength() {
        val v = MutableVector2F32.of(1f, 0f)
        assertTrue(v.setLength(2f))
        assertEquals(2f, v.x)
        assertEquals(0f, v.y)
    }

    @Test
    fun testToVector() {
        val v = MutableVector2F32.of(1f, 2f).toVector()
        assertEquals(1f, v.x)
        assertEquals(2f, v.y)
    }

    @Test
    fun testFrom() {
        val v = MutableVector2F32.from(Vector2F32(3f, 4f))
        assertEquals(3f, v.x)
        assertEquals(4f, v.y)
    }

    @Test
    fun testXSet() {
        val v = MutableVector2F32.of(1f, 2f)
        v.x = 10f
        assertEquals(10f, v.x)
    }

    @Test
    fun testYSet() {
        val v = MutableVector2F32.of(1f, 2f)
        v.y = 20f
        assertEquals(20f, v.y)
    }
}
