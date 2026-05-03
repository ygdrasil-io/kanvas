package org.skia.math

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.sqrt

class SkPointTest {

    @Test
    fun `Make and accessors store coordinates`() {
        val p = SkPoint.Make(3f, 4f)
        assertEquals(3f, p.x())
        assertEquals(4f, p.y())
        assertEquals(3f, p.fX)
        assertEquals(4f, p.fY)
    }

    @Test
    fun `default ctor is zero point`() {
        val p = SkPoint()
        assertTrue(p.isZero())
        assertEquals(0f, p.fX)
        assertEquals(0f, p.fY)
    }

    @Test
    fun `iset promotes ints to floats`() {
        val p = SkPoint()
        p.iset(5, -7)
        assertEquals(5f, p.fX)
        assertEquals(-7f, p.fY)
    }

    @Test
    fun `iset from SkIPoint matches`() {
        val p = SkPoint()
        p.iset(SkIPoint(2, 3))
        assertTrue(p.equals(2f, 3f))
    }

    @Test
    fun `setAbs takes absolute value of both axes`() {
        val p = SkPoint()
        p.setAbs(SkPoint(-2.5f, 4f))
        assertTrue(p.equals(2.5f, 4f))
    }

    @Test
    fun `offset adds to both axes`() {
        val p = SkPoint(1f, 2f)
        p.offset(3f, 4f)
        assertTrue(p.equals(4f, 6f))
    }

    @Test
    fun `length is Euclidean magnitude`() {
        assertEquals(5f, SkPoint(3f, 4f).length())
        assertEquals(5f, SkPoint.Length(3f, 4f))
        assertEquals(0f, SkPoint(0f, 0f).length())
    }

    @Test
    fun `length uses double-precision fallback for huge inputs`() {
        // Float overflow case: x*x + y*y > Float.MAX_VALUE but the
        // result is representable. Skia's algorithm switches to double.
        val big = 1e20f
        val expected = (big.toDouble() * sqrt(2.0)).toFloat()
        assertEquals(expected, SkPoint.Length(big, big), expected * 1e-6f)
    }

    @Test
    fun `normalize scales to unit length`() {
        val p = SkPoint(3f, 4f)
        assertTrue(p.normalize())
        assertEquals(1f, p.length(), 1e-6f)
        assertEquals(0.6f, p.fX, 1e-6f)
        assertEquals(0.8f, p.fY, 1e-6f)
    }

    @Test
    fun `normalize on near-zero returns false and zeros the point`() {
        val p = SkPoint(0f, 0f)
        assertFalse(p.normalize())
        assertTrue(p.isZero())
    }

    @Test
    fun `setLength scales preserving direction`() {
        val p = SkPoint(3f, 4f)
        assertTrue(p.setLength(10f))
        assertEquals(10f, p.length(), 1e-6f)
        assertEquals(6f, p.fX, 1e-6f)
        assertEquals(8f, p.fY, 1e-6f)
    }

    @Test
    fun `Normalize companion returns prior length`() {
        val p = SkPoint(3f, 4f)
        val prior = SkPoint.Normalize(p)
        assertEquals(5f, prior, 1e-6f)
        assertEquals(1f, p.length(), 1e-6f)
    }

    @Test
    fun `scale value mutates in place`() {
        val p = SkPoint(2f, 3f)
        p.scale(4f)
        assertTrue(p.equals(8f, 12f))
    }

    @Test
    fun `scale into dst leaves source untouched`() {
        val src = SkPoint(2f, 3f)
        val dst = SkPoint()
        src.scale(4f, dst)
        assertTrue(src.equals(2f, 3f))
        assertTrue(dst.equals(8f, 12f))
    }

    @Test
    fun `negate flips signs in place`() {
        val p = SkPoint(2f, -3f)
        p.negate()
        assertTrue(p.equals(-2f, 3f))
    }

    @Test
    fun `unaryMinus returns a new negated point`() {
        val p = SkPoint(2f, -3f)
        val q = -p
        assertTrue(q.equals(-2f, 3f))
        // original untouched
        assertTrue(p.equals(2f, -3f))
    }

    @Test
    fun `plusAssign adds vector in place`() {
        val p = SkPoint(1f, 2f)
        p += SkVector(3f, 4f)
        assertTrue(p.equals(4f, 6f))
    }

    @Test
    fun `minusAssign subtracts vector in place`() {
        val p = SkPoint(5f, 6f)
        p -= SkVector(1f, 2f)
        assertTrue(p.equals(4f, 4f))
    }

    @Test
    fun `times returns a new scaled point`() {
        val p = SkPoint(2f, 3f) * 2.5f
        assertTrue(p.equals(5f, 7.5f))
    }

    @Test
    fun `timesAssign mutates in place`() {
        val p = SkPoint(2f, 3f)
        p *= 2.5f
        assertTrue(p.equals(5f, 7.5f))
    }

    @Test
    fun `plus and minus operators compose like vector arithmetic`() {
        val a = SkPoint(2f, 3f)
        val b = SkPoint(5f, 7f)
        val sum = a + b
        val diff = b - a
        assertTrue(sum.equals(7f, 10f))
        assertTrue(diff.equals(3f, 4f))
    }

    @Test
    fun `dot and cross products`() {
        val a = SkVector(1f, 2f)
        val b = SkVector(3f, 4f)
        assertEquals(11f, SkPoint.DotProduct(a, b))     // 1*3 + 2*4
        assertEquals(-2f, SkPoint.CrossProduct(a, b))   // 1*4 - 2*3
        assertEquals(11f, a.dot(b))
        assertEquals(-2f, a.cross(b))
    }

    @Test
    fun `Distance is the magnitude of the difference`() {
        assertEquals(5f, SkPoint.Distance(SkPoint(0f, 0f), SkPoint(3f, 4f)))
        assertEquals(5f, SkPoint.Distance(SkPoint(1f, 1f), SkPoint(4f, 5f)))
    }

    @Test
    fun `equals overload uses raw float equality`() {
        val p = SkPoint(2f, 3f)
        assertTrue(p.equals(2f, 3f))
        assertFalse(p.equals(2f, 3.0001f))
        // NaN-aware: float `==` says NaN != NaN.
        val nan = SkPoint(Float.NaN, Float.NaN)
        assertFalse(nan.equals(Float.NaN, Float.NaN))
    }

    @Test
    fun `data class equals is structural`() {
        assertEquals(SkPoint(1f, 2f), SkPoint(1f, 2f))
        assertNotEquals(SkPoint(1f, 2f), SkPoint(1f, 3f))
    }

    @Test
    fun `SkVector typealias is interchangeable with SkPoint`() {
        val v: SkVector = SkPoint(1f, 0f)
        val p: SkPoint = SkVector(0f, 1f)
        assertTrue(v.equals(1f, 0f))
        assertTrue(p.equals(0f, 1f))
    }

    @Test
    fun `isFinite catches NaN and infinity`() {
        assertTrue(SkPoint(1f, 2f).isFinite())
        assertFalse(SkPoint(Float.NaN, 2f).isFinite())
        assertFalse(SkPoint(1f, Float.POSITIVE_INFINITY).isFinite())
    }

    @Test
    fun `Offset companion shifts an array of points in place`() {
        val pts = arrayOf(SkPoint(1f, 1f), SkPoint(2f, 2f), SkPoint(3f, 3f))
        SkPoint.Offset(pts, pts.size, SkVector(10f, 20f))
        assertTrue(pts[0].equals(11f, 21f))
        assertTrue(pts[1].equals(12f, 22f))
        assertTrue(pts[2].equals(13f, 23f))
    }
}
