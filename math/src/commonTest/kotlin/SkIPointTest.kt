package org.graphiks.math

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

class SkIPointTest {

    @Test
    fun `Make and accessors store coordinates`() {
        val p = SkIPoint.Make(3, 4)
        assertEquals(3, p.x())
        assertEquals(4, p.y())
    }

    @Test
    fun `default is zero`() {
        val p = SkIPoint()
        assertTrue(p.isZero())
    }

    @Test
    fun `set replaces both axes`() {
        val p = SkIPoint(1, 2)
        p.set(5, -7)
        assertTrue(p.equals(5, -7))
    }

    @Test
    fun `offset adds to both axes`() {
        val p = SkIPoint(1, 2)
        p.offset(10, 20)
        assertTrue(p.equals(11, 22))
    }

    @Test
    fun `negate flips signs`() {
        val p = SkIPoint(2, -3)
        p.negate()
        assertTrue(p.equals(-2, 3))
    }

    @Test
    fun `equals overload returns false on mismatch`() {
        val p = SkIPoint(2, 3)
        assertTrue(p.equals(2, 3))
        assertFalse(p.equals(2, 4))
    }

    @Test
    fun `unaryMinus returns a negated copy`() {
        val p = SkIPoint(2, -3)
        val q = -p
        assertTrue(q.equals(-2, 3))
        assertTrue(p.equals(2, -3))
    }

    @Test
    fun `plus and minus operators produce new points`() {
        val a = SkIPoint(2, 3)
        val b = SkIPoint(5, 7)
        val sum = a + b
        val diff = b - a
        assertTrue(sum.equals(7, 10))
        assertTrue(diff.equals(3, 4))
    }

    // ── Saturating arithmetic (Skia's Sk32_sat_add / Sk32_sat_sub) ──────

    @Test
    fun `plusAssign saturates on Int overflow`() {
        val p = SkIPoint(Int.MAX_VALUE, 0)
        p += SkIVector(1, 0)
        assertEquals(Int.MAX_VALUE, p.fX, "plusAssign should saturate at MAX_VALUE")
    }

    @Test
    fun `minusAssign saturates on Int underflow`() {
        val p = SkIPoint(Int.MIN_VALUE, 0)
        p -= SkIVector(1, 0)
        assertEquals(Int.MIN_VALUE, p.fX, "minusAssign should saturate at MIN_VALUE")
    }

    @Test
    fun `plus operator saturates on overflow`() {
        val a = SkIPoint(Int.MAX_VALUE - 5, 0)
        val sum = a + SkIVector(100, 0)
        assertEquals(Int.MAX_VALUE, sum.fX)
    }

    @Test
    fun `minus operator saturates on underflow`() {
        val a = SkIPoint(Int.MIN_VALUE + 5, 0)
        val diff = a - SkIPoint(100, 0)
        assertEquals(Int.MIN_VALUE, diff.fX)
    }

    @Test
    fun `saturating ops are exact when inputs do not overflow`() {
        val a = SkIPoint(1_000_000_000, -2_000_000_000)
        val b = SkIPoint(123_456_789, 987_654_321)
        val sum = a + b
        assertEquals(1_123_456_789, sum.fX)
        assertEquals(-1_012_345_679, sum.fY)
        val diff = a - b
        assertEquals(876_543_211, diff.fX)
        assertEquals(Int.MIN_VALUE, diff.fY) // -2e9 - 9.87e8 underflows → saturates
    }
}
