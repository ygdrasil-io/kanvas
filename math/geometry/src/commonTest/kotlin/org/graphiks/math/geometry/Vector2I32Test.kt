package org.graphiks.math.geometry

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

class Vector2I32Test {

    @Test
    fun `Make and accessors store coordinates`() {
        val p = Vector2I32.Make(3, 4)
        assertEquals(3, p.x)
        assertEquals(4, p.y)
    }

    @Test
    fun `default is zero`() {
        val p = Vector2I32()
        assertTrue(p.isZero())
    }

    @Test
    fun `set replaces both axes`() {
        val p = Vector2I32(1, 2)
        p.set(5, -7)
        assertTrue(p.equals(5, -7))
    }

    @Test
    fun `offset adds to both axes`() {
        val p = Vector2I32(1, 2)
        p.offset(10, 20)
        assertTrue(p.equals(11, 22))
    }

    @Test
    fun `negate flips signs`() {
        val p = Vector2I32(2, -3)
        p.negate()
        assertTrue(p.equals(-2, 3))
    }

    @Test
    fun `equals overload returns false on mismatch`() {
        val p = Vector2I32(2, 3)
        assertTrue(p.equals(2, 3))
        assertFalse(p.equals(2, 4))
    }

    @Test
    fun `unaryMinus returns a negated copy`() {
        val p = Vector2I32(2, -3)
        val q = -p
        assertTrue(q.equals(-2, 3))
        assertTrue(p.equals(2, -3))
    }

    @Test
    fun `plus and minus operators produce new points`() {
        val a = Vector2I32(2, 3)
        val b = Vector2I32(5, 7)
        val sum = a + b
        val diff = b - a
        assertTrue(sum.equals(7, 10))
        assertTrue(diff.equals(3, 4))
    }

    @Test
    fun `plusAssign saturates on Int overflow`() {
        val p = Vector2I32(Int.MAX_VALUE, 0)
        p += Vector2I32(1, 0)
        assertEquals(Int.MAX_VALUE, p.x, "plusAssign should saturate at MAX_VALUE")
    }

    @Test
    fun `minusAssign saturates on Int underflow`() {
        val p = Vector2I32(Int.MIN_VALUE, 0)
        p -= Vector2I32(1, 0)
        assertEquals(Int.MIN_VALUE, p.x, "minusAssign should saturate at MIN_VALUE")
    }

    @Test
    fun `plus operator saturates on overflow`() {
        val a = Vector2I32(Int.MAX_VALUE - 5, 0)
        val sum = a + Vector2I32(100, 0)
        assertEquals(Int.MAX_VALUE, sum.x)
    }

    @Test
    fun `minus operator saturates on underflow`() {
        val a = Vector2I32(Int.MIN_VALUE + 5, 0)
        val diff = a - Vector2I32(100, 0)
        assertEquals(Int.MIN_VALUE, diff.x)
    }

    @Test
    fun `saturating ops are exact when inputs do not overflow`() {
        val a = Vector2I32(1_000_000_000, -2_000_000_000)
        val b = Vector2I32(123_456_789, 987_654_321)
        val sum = a + b
        assertEquals(1_123_456_789, sum.x)
        assertEquals(-1_012_345_679, sum.y)
        val diff = a - b
        assertEquals(876_543_211, diff.x)
        assertEquals(Int.MIN_VALUE, diff.y) // -2e9 - 9.87e8 underflows → saturates
    }
}
