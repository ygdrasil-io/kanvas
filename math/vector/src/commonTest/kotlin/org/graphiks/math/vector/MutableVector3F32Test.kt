package org.graphiks.math.vector

import kotlin.test.*

class MutableVector3F32Test {

    @Test
    fun testOf() {
        val v = MutableVector3F32.of(1f, 2f, 3f)
        assertEquals(1f, v.x)
        assertEquals(2f, v.y)
        assertEquals(3f, v.z)
    }

    @Test
    fun testSet() {
        val v = MutableVector3F32.of(1f, 2f, 3f)
        v.set(4f, 5f, 6f)
        assertEquals(4f, v.x)
        assertEquals(5f, v.y)
        assertEquals(6f, v.z)
    }

    @Test
    fun testOffset() {
        val v = MutableVector3F32.of(1f, 2f, 3f)
        v.offset(1f, 1f, 1f)
        assertEquals(2f, v.x)
        assertEquals(3f, v.y)
        assertEquals(4f, v.z)
    }

    @Test
    fun testScale() {
        val v = MutableVector3F32.of(1f, 2f, 3f)
        v.scale(2f)
        assertEquals(2f, v.x)
        assertEquals(4f, v.y)
        assertEquals(6f, v.z)
    }

    @Test
    fun testNegate() {
        val v = MutableVector3F32.of(1f, -2f, 3f)
        v.negate()
        assertEquals(-1f, v.x)
        assertEquals(2f, v.y)
        assertEquals(-3f, v.z)
    }

    @Test
    fun testLength() {
        val v = MutableVector3F32.of(1f, 2f, 2f)
        assertEquals(3f, v.length())
    }

    @Test
    fun testNormalize() {
        val v = MutableVector3F32.of(3f, 0f, 0f)
        assertTrue(v.normalize())
        assertTrue(kotlin.math.abs(v.length() - 1f) < 1e-6f)
    }

    @Test
    fun testNormalizeLargeFiniteVector() {
        val v = MutableVector3F32.of(1e30f, 1e30f, 1e30f)
        assertTrue(v.normalize())
        assertEquals(1f, v.length(), 1e-6f)
        assertTrue(v.x > 0f)
        assertTrue(v.y > 0f)
        assertTrue(v.z > 0f)
    }

    @Test
    fun testNormalizeZero() {
        val v = MutableVector3F32.of(0f, 0f, 0f)
        assertFalse(v.normalize())
        assertEquals(0f, v.x)
    }

    @Test
    fun testToVector() {
        val v = MutableVector3F32.of(1f, 2f, 3f).toVector()
        assertEquals(1f, v.x); assertEquals(2f, v.y); assertEquals(3f, v.z)
    }

    @Test
    fun testFrom() {
        val v = MutableVector3F32.from(Vector3F32(4f, 5f, 6f))
        assertEquals(4f, v.x); assertEquals(5f, v.y); assertEquals(6f, v.z)
    }
}
