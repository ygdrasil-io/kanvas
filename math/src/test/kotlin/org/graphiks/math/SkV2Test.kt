package org.graphiks.math

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.math.sqrt

class SkV2Test {

    @Test
    fun `accessors and equality`() {
        val v = SkV2(3f, 4f)
        assertEquals(3f, v.x)
        assertEquals(4f, v.y)
        assertEquals(v, SkV2(3f, 4f))
    }

    @Test
    fun `length and lengthSquared`() {
        val v = SkV2(3f, 4f)
        assertEquals(25f, v.lengthSquared())
        assertEquals(5f, v.length(), 1e-6f)
    }

    @Test
    fun `dot and cross`() {
        val a = SkV2(1f, 2f)
        val b = SkV2(3f, 4f)
        assertEquals(11f, a.dot(b))
        assertEquals(-2f, a.cross(b))
    }

    @Test
    fun `arithmetic operators`() {
        val a = SkV2(1f, 2f)
        val b = SkV2(3f, 4f)
        assertEquals(SkV2(-1f, -2f), -a)
        assertEquals(SkV2(4f, 6f), a + b)
        assertEquals(SkV2(-2f, -2f), a - b)
        assertEquals(SkV2(3f, 8f), a * b)
        assertEquals(SkV2(2f, 4f), a * 2f)
        assertEquals(SkV2(2f, 4f), 2f * a)
        assertEquals(SkV2(0.5f, 1f), a / 2f)
    }

    @Test
    fun `normalize produces unit length`() {
        val v = SkV2(3f, 4f).normalize()
        assertEquals(1f, sqrt((v.x * v.x + v.y * v.y).toDouble()).toFloat(), 1e-6f)
    }
}
