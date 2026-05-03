package org.skia.math

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

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
}
