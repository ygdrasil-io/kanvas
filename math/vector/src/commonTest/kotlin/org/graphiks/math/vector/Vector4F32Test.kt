package org.graphiks.math.vector

import kotlin.test.*

class Vector4F32Test {

    @Test
    fun testConstruct() {
        val v = Vector4F32(1f, 2f, 3f, 4f)
        assertEquals(1f, v.x)
        assertEquals(2f, v.y)
        assertEquals(3f, v.z)
        assertEquals(4f, v.w)
    }

    @Test
    fun testIndexedGet() {
        val v = Vector4F32(1f, 2f, 3f, 4f)
        assertEquals(1f, v[0])
        assertEquals(2f, v[1])
        assertEquals(3f, v[2])
        assertEquals(4f, v[3])
    }

    @Test
    fun testIndexedGetOutOfBounds() {
        assertFailsWith<IndexOutOfBoundsException> { Vector4F32(1f, 2f, 3f, 4f)[4] }
    }

    @Test
    fun testPlus() {
        val a = Vector4F32(1f, 2f, 3f, 4f)
        val b = Vector4F32(5f, 6f, 7f, 8f)
        assertEquals(Vector4F32(6f, 8f, 10f, 12f), a + b)
    }

    @Test
    fun testTimesScalar() {
        assertEquals(Vector4F32(2f, 4f, 6f, 8f), Vector4F32(1f, 2f, 3f, 4f) * 2f)
    }

    @Test
    fun testTimesComponentWise() {
        val a = Vector4F32(1f, 2f, 3f, 4f)
        val b = Vector4F32(2f, 3f, 4f, 5f)
        assertEquals(Vector4F32(2f, 6f, 12f, 20f), a * b)
    }

    @Test
    fun testLength() {
        val v = Vector4F32(1f, 2f, 2f, 4f)
        assertEquals(5f, v.length())
    }

    @Test
    fun testLengthAndNormalizeLargeFiniteVector() {
        val v = Vector4F32(1e30f, 1e30f, 1e30f, 1e30f)
        assertEquals(2e30f, v.length(), 2e24f)

        val normalized = v.normalize()
        assertEquals(1f, normalized.length(), 1e-6f)
        assertEquals(0.5f, normalized.x, 1e-6f)
        assertEquals(0.5f, normalized.w, 1e-6f)
    }

    @Test
    fun testDot() {
        assertEquals(70f, Vector4F32(1f, 2f, 3f, 4f).dot(Vector4F32(5f, 6f, 7f, 8f)))
    }

    @Test
    fun testNormalize() {
        val v = Vector4F32(3f, 4f, 0f, 0f).normalize()
        assertTrue(kotlin.math.abs(v.length() - 1f) < 1e-6f)
    }
}
