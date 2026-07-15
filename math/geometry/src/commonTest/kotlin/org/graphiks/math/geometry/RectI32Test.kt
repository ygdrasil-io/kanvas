package org.graphiks.math.geometry

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

class RectI32Test {

    @Test
    fun `ofLTRB stores corners`() {
        val r = RectI32.ofLTRB(1, 2, 3, 4)
        assertEquals(1, r.left); assertEquals(2, r.top)
        assertEquals(3, r.right); assertEquals(4, r.bottom)
        assertEquals(2, r.width()); assertEquals(2, r.height())
    }

    @Test
    fun `ofOriginSize adds w h to origin`() {
        val r = RectI32.ofOriginSize(10, 20, 30, 40)
        assertEquals(40, r.right); assertEquals(60, r.bottom)
    }

    @Test
    fun `width and height wrap on overflow but width64 stays exact`() {
        val r = RectI32.ofLTRB(Int.MIN_VALUE, 0, Int.MAX_VALUE, 0)
        assertTrue(r.width() < 0)
        assertEquals(Int.MAX_VALUE.toLong() - Int.MIN_VALUE.toLong(), r.width64())
    }

    @Test
    fun `isEmpty checks 64-bit dimensions and int32 fit`() {
        assertTrue(RectI32.Empty.isEmpty)
        assertTrue(RectI32.ofLTRB(5, 5, 5, 10).isEmpty)
        assertTrue(RectI32.ofLTRB(5, 10, 10, 5).isEmpty)
        assertFalse(RectI32.ofLTRB(0, 0, 1, 1).isEmpty)
        val overflow = RectI32.ofLTRB(Int.MIN_VALUE, 0, Int.MAX_VALUE, 1)
        assertTrue(overflow.isEmpty, "width64 > Int.MAX_VALUE must be treated as empty")
        assertFalse(overflow.isEmpty64(), "isEmpty64 ignores int32 overflow")
    }

    @Test
    fun `ofOriginSize saturates instead of wrapping on int32 overflow`() {
        val r = RectI32.ofOriginSize(Int.MAX_VALUE - 1, 0, 10, 10)
        assertEquals(Int.MAX_VALUE, r.right, "right should saturate at Int.MAX_VALUE")
        assertEquals(10, r.bottom)
    }

    @Test
    fun `fromPointSize delegates to ofOriginSize saturation semantics`() {
        val max = RectI32.fromPointSize(Vector2I32(Int.MAX_VALUE - 1, Int.MAX_VALUE - 2), SizeI32(10, 20))
        assertEquals(Int.MAX_VALUE - 1, max.left)
        assertEquals(Int.MAX_VALUE - 2, max.top)
        assertEquals(Int.MAX_VALUE, max.right)
        assertEquals(Int.MAX_VALUE, max.bottom)

        val min = RectI32.fromPointSize(Vector2I32(Int.MIN_VALUE + 1, Int.MIN_VALUE + 2), SizeI32(-10, -20))
        assertEquals(Int.MIN_VALUE + 1, min.left)
        assertEquals(Int.MIN_VALUE + 2, min.top)
        assertEquals(Int.MIN_VALUE, min.right)
        assertEquals(Int.MIN_VALUE, min.bottom)
    }

    @Test
    fun `setXYWH offset inset adjust all saturate`() {
        val r = RectI32(0, 0, 0, 0)
        r.setXYWH(Int.MAX_VALUE - 5, 0, 100, 1)
        assertEquals(Int.MAX_VALUE, r.right)

        val o = RectI32.ofLTRB(Int.MIN_VALUE + 1, 0, Int.MAX_VALUE - 1, 10)
        o.offset(-5, 0)
        assertEquals(Int.MIN_VALUE, o.left, "left saturates at MIN_VALUE")
        o.offset(10, 0)
        assertEquals(Int.MAX_VALUE, o.right, "right saturates at MAX_VALUE")

        val ins = RectI32.ofLTRB(0, 0, 10, 10)
        ins.outset(Int.MAX_VALUE, 0)
        assertEquals(Int.MIN_VALUE + 1, ins.left)
        assertEquals(Int.MAX_VALUE, ins.right)

        val minOutset = RectI32.ofLTRB(0, 0, 10, 10)
        minOutset.outset(Int.MIN_VALUE, 0)
        assertEquals(Int.MAX_VALUE, minOutset.left)
        assertEquals(Int.MIN_VALUE + 10, minOutset.right)

        val ins2 = RectI32.ofLTRB(0, 0, 10, 10)
        ins2.inset(Int.MIN_VALUE, 0)
        assertEquals(Int.MIN_VALUE, ins2.left)
        assertEquals(Int.MAX_VALUE, ins2.right)

        val a = RectI32.ofLTRB(0, 0, 10, 10)
        a.adjust(0, 0, Int.MAX_VALUE, 0)
        assertEquals(Int.MAX_VALUE, a.right)
    }

    @Test
    fun `offsetBy insetBy outsetBy saturate`() {
        val r = RectI32.ofLTRB(0, 0, 10, 10)
        val off = r.offsetBy(Int.MAX_VALUE, 0)
        assertEquals(Int.MAX_VALUE, off.left)
        assertEquals(Int.MAX_VALUE, off.right)

        val ins = r.insetBy(Int.MIN_VALUE, 0)
        assertEquals(Int.MIN_VALUE, ins.left)
        assertEquals(Int.MAX_VALUE, ins.right)

        val out = r.outsetBy(Int.MAX_VALUE, 0)
        assertEquals(Int.MIN_VALUE + 1, out.left)
        assertEquals(Int.MAX_VALUE, out.right)

        val minOut = r.outsetBy(Int.MIN_VALUE, 0)
        assertEquals(Int.MAX_VALUE, minOut.left)
        assertEquals(Int.MIN_VALUE + 10, minOut.right)
    }

    @Test
    fun `offsetTo uses 64-bit pinned arithmetic`() {
        val r = RectI32.ofLTRB(Int.MIN_VALUE, 0, Int.MIN_VALUE + 10, 5)
        r.offsetTo(Int.MAX_VALUE - 9, 0)
        assertEquals(Int.MAX_VALUE - 9, r.left)
        assertEquals(Int.MAX_VALUE, r.right)
    }

    @Test
    fun `setLTRB and setXYWH replace coordinates`() {
        val r = RectI32(0, 0, 0, 0)
        r.setLTRB(1, 2, 3, 4)
        assertEquals(RectI32(1, 2, 3, 4), r)
        r.setXYWH(0, 0, 5, 6)
        assertEquals(RectI32(0, 0, 5, 6), r)
    }

    @Test
    fun `offset offsetTo inset outset adjust`() {
        val r = RectI32.ofOriginSize(10, 20, 5, 6)
        r.offset(2, 3)
        assertEquals(RectI32(12, 23, 17, 29), r)
        r.offsetTo(0, 0)
        assertEquals(RectI32(0, 0, 5, 6), r)
        r.inset(1, 1)
        assertEquals(RectI32(1, 1, 4, 5), r)
        r.outset(1, 1)
        assertEquals(RectI32(0, 0, 5, 6), r)
        r.adjust(1, 2, 3, 4)
        assertEquals(RectI32(1, 2, 8, 10), r)
    }

    @Test
    fun `sort and makeSorted swap reversed edges`() {
        val r = RectI32.ofLTRB(10, 20, 0, 5)
        r.sort()
        assertEquals(RectI32(0, 5, 10, 20), r)
    }

    @Test
    fun `contains point is half-open`() {
        val r = RectI32.ofLTRB(0, 0, 10, 10)
        assertTrue(r.contains(0, 0))
        assertTrue(r.contains(9, 9))
        assertFalse(r.contains(10, 5))
        assertFalse(r.contains(5, 10))
    }

    @Test
    fun `contains rect requires non-empty inner`() {
        val outer = RectI32.ofLTRB(0, 0, 10, 10)
        assertTrue(outer.contains(RectI32.ofLTRB(2, 2, 8, 8)))
        assertTrue(outer.contains(RectI32.ofLTRB(0, 0, 10, 10)))
        assertFalse(outer.contains(RectI32.Empty))
        assertFalse(outer.contains(RectI32.ofLTRB(5, 5, 11, 8)))
    }

    @Test
    fun `intersect overlapping clips to overlap`() {
        val a = RectI32.ofLTRB(0, 0, 10, 10)
        assertTrue(a.intersect(RectI32.ofLTRB(5, 5, 15, 15)))
        assertEquals(RectI32(5, 5, 10, 10), a)
    }

    @Test
    fun `intersect non-overlapping returns false unchanged`() {
        val a = RectI32.ofLTRB(0, 0, 5, 5)
        assertFalse(a.intersect(RectI32.ofLTRB(10, 10, 20, 20)))
        assertEquals(RectI32(0, 0, 5, 5), a)
    }

    @Test
    fun `intersects companion`() {
        assertTrue(RectI32.intersects(RectI32.ofLTRB(0, 0, 10, 10), RectI32.ofLTRB(5, 5, 15, 15)))
        assertFalse(RectI32.intersects(RectI32.ofLTRB(0, 0, 5, 5), RectI32.ofLTRB(10, 10, 20, 20)))
    }

    @Test
    fun `join expands to encompass r and skips empty args`() {
        val a = RectI32.ofLTRB(0, 0, 5, 5)
        a.join(RectI32.ofLTRB(3, 3, 10, 10))
        assertEquals(RectI32(0, 0, 10, 10), a)

        val b = RectI32.ofLTRB(0, 0, 5, 5)
        b.join(RectI32.Empty)
        assertEquals(RectI32(0, 0, 5, 5), b)

        val c = RectI32(0, 0, 0, 0)
        c.join(RectI32.ofLTRB(1, 2, 3, 4))
        assertEquals(RectI32(1, 2, 3, 4), c)
    }

    @Test
    fun `topLeft returns Vector2I32`() {
        val r = RectI32.ofLTRB(3, 5, 10, 20)
        assertEquals(Vector2I32(3, 5), r.topLeft())
    }
}
