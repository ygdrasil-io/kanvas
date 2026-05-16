package org.graphiks.math

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.sqrt

class SkPoint3Test {

    @Test
    fun `default ctor is origin`() {
        val p = SkPoint3()
        assertEquals(0f, p.fX); assertEquals(0f, p.fY); assertEquals(0f, p.fZ)
    }

    @Test
    fun `Make and accessors`() {
        val p = SkPoint3.Make(1f, 2f, 3f)
        assertEquals(1f, p.x()); assertEquals(2f, p.y()); assertEquals(3f, p.z())
    }

    @Test
    fun `set replaces all three components`() {
        val p = SkPoint3()
        p.set(7f, 8f, 9f)
        assertEquals(SkPoint3(7f, 8f, 9f), p)
    }

    @Test
    fun `length and lengthSquared`() {
        val p = SkPoint3(3f, 4f, 12f)
        assertEquals(13f, p.length(), 1e-5f)
        assertEquals(169f, p.lengthSquared())
    }

    @Test
    fun `Length companion handles overflow via double`() {
        // 1e30² × 3 overflows float; double fallback in Length keeps it finite.
        val len = SkPoint3.Length(1e30f, 1e30f, 1e30f)
        assertTrue(len.isFinite())
        assertEquals((1e30 * sqrt(3.0)).toFloat(), len, 1e25f)
    }

    @Test
    fun `isFinite catches NaN and Inf`() {
        assertTrue(SkPoint3(1f, 2f, 3f).isFinite())
        assertFalse(SkPoint3(Float.NaN, 2f, 3f).isFinite())
        assertFalse(SkPoint3(1f, Float.POSITIVE_INFINITY, 3f).isFinite())
    }

    @Test
    fun `unaryMinus + - operators`() {
        val a = SkPoint3(1f, 2f, 3f)
        assertEquals(SkPoint3(-1f, -2f, -3f), -a)
        val b = SkPoint3(4f, 5f, 6f)
        assertEquals(SkPoint3(5f, 7f, 9f), a + b)
        assertEquals(SkPoint3(-3f, -3f, -3f), a - b)
        assertEquals(SkPoint3(2f, 4f, 6f), a * 2f)
    }

    @Test
    fun `DotProduct CrossProduct`() {
        val a = SkPoint3(1f, 0f, 0f)
        val b = SkPoint3(0f, 1f, 0f)
        assertEquals(0f, SkPoint3.DotProduct(a, b))
        assertEquals(SkPoint3(0f, 0f, 1f), SkPoint3.CrossProduct(a, b))
        // Standard formula: i × j = k.
    }
}
