package org.graphiks.math

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Coverage for the iso-aligned `SkIRect` helpers.
 * Mirrors Skia's behavioural expectations for intersect / join / contains
 * / sort, plus the 64-bit width/height variant for overflow safety.
 */
class SkIRectTest {

    @Test
    fun `MakeLTRB stores corners`() {
        val r = SkIRect.MakeLTRB(1, 2, 3, 4)
        assertEquals(1, r.left); assertEquals(2, r.top)
        assertEquals(3, r.right); assertEquals(4, r.bottom)
        assertEquals(2, r.width()); assertEquals(2, r.height())
    }

    @Test
    fun `MakeXYWH adds w h to origin`() {
        val r = SkIRect.MakeXYWH(10, 20, 30, 40)
        assertEquals(40, r.right); assertEquals(60, r.bottom)
    }

    @Test
    fun `width and height wrap on overflow but width64 stays exact`() {
        val r = SkIRect.MakeLTRB(Int.MIN_VALUE, 0, Int.MAX_VALUE, 0)
        // width = MAX - MIN = wraps in int32 (Skia's Sk32_can_overflow_sub).
        // width64 stays exact in long.
        assertTrue(r.width() < 0)   // wrapped
        assertEquals(Int.MAX_VALUE.toLong() - Int.MIN_VALUE.toLong(), r.width64())
    }

    @Test
    fun `isEmpty checks 64-bit dimensions`() {
        assertTrue(SkIRect.MakeEmpty().isEmpty)
        assertTrue(SkIRect.MakeLTRB(5, 5, 5, 10).isEmpty)
        assertTrue(SkIRect.MakeLTRB(5, 10, 10, 5).isEmpty)
        assertFalse(SkIRect.MakeLTRB(0, 0, 1, 1).isEmpty)
        // A rect that LOOKS empty in int32 due to overflow but isn't in 64-bit
        val overflow = SkIRect.MakeLTRB(Int.MIN_VALUE, 0, Int.MAX_VALUE, 1)
        assertFalse(overflow.isEmpty, "width64 > 0 should mean non-empty")
    }

    @Test
    fun `setLTRB and setXYWH replace coordinates`() {
        val r = SkIRect.MakeEmpty()
        r.setLTRB(1, 2, 3, 4)
        assertEquals(SkIRect(1, 2, 3, 4), r)
        r.setXYWH(0, 0, 5, 6)
        assertEquals(SkIRect(0, 0, 5, 6), r)
    }

    @Test
    fun `offset offsetTo inset outset adjust`() {
        val r = SkIRect.MakeXYWH(10, 20, 5, 6)
        r.offset(2, 3)
        assertEquals(SkIRect(12, 23, 17, 29), r)
        r.offsetTo(0, 0)
        assertEquals(SkIRect(0, 0, 5, 6), r)
        r.inset(1, 1)
        assertEquals(SkIRect(1, 1, 4, 5), r)
        r.outset(1, 1)
        assertEquals(SkIRect(0, 0, 5, 6), r)
        r.adjust(1, 2, 3, 4)
        assertEquals(SkIRect(1, 2, 8, 10), r)
    }

    @Test
    fun `sort and makeSorted swap reversed edges`() {
        val r = SkIRect.MakeLTRB(10, 20, 0, 5)
        r.sort()
        assertEquals(SkIRect(0, 5, 10, 20), r)
    }

    @Test
    fun `contains point is half-open`() {
        val r = SkIRect.MakeLTRB(0, 0, 10, 10)
        assertTrue(r.contains(0, 0))
        assertTrue(r.contains(9, 9))
        assertFalse(r.contains(10, 5))
        assertFalse(r.contains(5, 10))
    }

    @Test
    fun `contains rect requires non-empty inner`() {
        val outer = SkIRect.MakeLTRB(0, 0, 10, 10)
        assertTrue(outer.contains(SkIRect.MakeLTRB(2, 2, 8, 8)))
        assertTrue(outer.contains(SkIRect.MakeLTRB(0, 0, 10, 10)))
        assertFalse(outer.contains(SkIRect.MakeEmpty()))
        assertFalse(outer.contains(SkIRect.MakeLTRB(5, 5, 11, 8)))
    }

    @Test
    fun `intersect overlapping clips to overlap`() {
        val a = SkIRect.MakeLTRB(0, 0, 10, 10)
        assertTrue(a.intersect(SkIRect.MakeLTRB(5, 5, 15, 15)))
        assertEquals(SkIRect(5, 5, 10, 10), a)
    }

    @Test
    fun `intersect non-overlapping returns false unchanged`() {
        val a = SkIRect.MakeLTRB(0, 0, 5, 5)
        assertFalse(a.intersect(SkIRect.MakeLTRB(10, 10, 20, 20)))
        assertEquals(SkIRect(0, 0, 5, 5), a)
    }

    @Test
    fun `Intersects companion`() {
        assertTrue(SkIRect.Intersects(SkIRect.MakeLTRB(0, 0, 10, 10), SkIRect.MakeLTRB(5, 5, 15, 15)))
        assertFalse(SkIRect.Intersects(SkIRect.MakeLTRB(0, 0, 5, 5), SkIRect.MakeLTRB(10, 10, 20, 20)))
    }

    @Test
    fun `join expands to encompass r and skips empty args`() {
        val a = SkIRect.MakeLTRB(0, 0, 5, 5)
        a.join(SkIRect.MakeLTRB(3, 3, 10, 10))
        assertEquals(SkIRect(0, 0, 10, 10), a)

        val b = SkIRect.MakeLTRB(0, 0, 5, 5)
        b.join(SkIRect.MakeEmpty())
        assertEquals(SkIRect(0, 0, 5, 5), b)

        val c = SkIRect.MakeEmpty()
        c.join(SkIRect.MakeLTRB(1, 2, 3, 4))
        assertEquals(SkIRect(1, 2, 3, 4), c)
    }

    @Test
    fun `topLeft returns SkIPoint`() {
        val r = SkIRect.MakeLTRB(3, 5, 10, 20)
        assertEquals(SkIPoint(3, 5), r.topLeft())
    }
}
