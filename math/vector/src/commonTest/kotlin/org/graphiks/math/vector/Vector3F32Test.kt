package org.graphiks.math.vector

import kotlin.test.*

class Vector3F32Test {

    @Test
    fun testConstruct() {
        val v = Vector3F32(1f, 2f, 3f)
        assertEquals(1f, v.x)
        assertEquals(2f, v.y)
        assertEquals(3f, v.z)
    }

    @Test
    fun testPlus() {
        assertEquals(Vector3F32(5f, 7f, 9f), Vector3F32(1f, 2f, 3f) + Vector3F32(4f, 5f, 6f))
    }

    @Test
    fun testMinus() {
        assertEquals(Vector3F32(3f, 3f, 3f), Vector3F32(4f, 5f, 6f) - Vector3F32(1f, 2f, 3f))
    }

    @Test
    fun testUnaryMinus() {
        assertEquals(Vector3F32(-1f, 2f, -3f), -Vector3F32(1f, -2f, 3f))
    }

    @Test
    fun testTimesScalar() {
        assertEquals(Vector3F32(2f, 4f, 6f), Vector3F32(1f, 2f, 3f) * 2f)
        assertEquals(Vector3F32(2f, 4f, 6f), 2f * Vector3F32(1f, 2f, 3f))
    }

    @Test
    fun testTimesComponentWise() {
        assertEquals(Vector3F32(2f, 6f, 12f), Vector3F32(1f, 2f, 3f) * Vector3F32(2f, 3f, 4f))
    }

    @Test
    fun testDiv() {
        assertEquals(Vector3F32(1f, 2f, 3f), Vector3F32(2f, 4f, 6f) / 2f)
    }

    @Test
    fun testLength() {
        val v = Vector3F32(1f, 2f, 2f)
        assertEquals(3f, v.length())
    }

    @Test
    fun testDot() {
        assertEquals(32f, Vector3F32(1f, 2f, 3f).dot(Vector3F32(4f, 5f, 6f)))
    }

    @Test
    fun testCross() {
        val a = Vector3F32(1f, 0f, 0f)
        val b = Vector3F32(0f, 1f, 0f)
        assertEquals(Vector3F32(0f, 0f, 1f), a.cross(b))
    }

    @Test
    fun testNormalize() {
        val v = Vector3F32(3f, 0f, 0f).normalize()
        assertTrue(kotlin.math.abs(v.length() - 1f) < 1e-6f)
        assertEquals(1f, v.x)
    }

    @Test
    fun testIsFinite() {
        assertTrue(Vector3F32(1f, 2f, 3f).isFinite())
        assertFalse(Vector3F32(Float.NaN, 2f, 3f).isFinite())
    }
}
