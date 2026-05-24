package org.graphiks.math

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.Test

class SkV4Test {

    @Test
    fun `accessors and equality`() {
        val v = SkV4(1f, 2f, 3f, 4f)
        assertEquals(1f, v.x); assertEquals(2f, v.y); assertEquals(3f, v.z); assertEquals(4f, v.w)
        assertEquals(v, SkV4(1f, 2f, 3f, 4f))
    }

    @Test
    fun `indexed access mirrors C++ operator subscript`() {
        val v = SkV4(10f, 20f, 30f, 40f)
        assertEquals(10f, v[0])
        assertEquals(20f, v[1])
        assertEquals(30f, v[2])
        assertEquals(40f, v[3])
        assertFailsWith<IndexOutOfBoundsException> { v[4] }
        assertFailsWith<IndexOutOfBoundsException> { v[-1] }
    }

    @Test
    fun `dot and length`() {
        val a = SkV4(1f, 2f, 2f, 4f)
        assertEquals(25f, a.lengthSquared())
        assertEquals(5f, a.length(), 1e-6f)
        assertEquals(25f, a.dot(a))
    }

    @Test
    fun `arithmetic operators`() {
        val a = SkV4(1f, 2f, 3f, 4f)
        val b = SkV4(5f, 6f, 7f, 8f)
        assertEquals(SkV4(-1f, -2f, -3f, -4f), -a)
        assertEquals(SkV4(6f, 8f, 10f, 12f), a + b)
        assertEquals(SkV4(-4f, -4f, -4f, -4f), a - b)
        assertEquals(SkV4(5f, 12f, 21f, 32f), a * b)
        assertEquals(SkV4(2f, 4f, 6f, 8f), a * 2f)
        assertEquals(SkV4(2f, 4f, 6f, 8f), 2f * a)
    }

    @Test
    fun `normalize produces unit vector`() {
        val v = SkV4(1f, 2f, 2f, 4f).normalize()
        assertEquals(1f, v.length(), 1e-6f)
    }
}
