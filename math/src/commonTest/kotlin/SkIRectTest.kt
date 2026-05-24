package org.graphiks.math

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

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
    fun `isEmpty checks 64-bit dimensions and int32 fit`() {
        assertTrue(SkIRect.MakeEmpty().isEmpty)
        assertTrue(SkIRect.MakeLTRB(5, 5, 5, 10).isEmpty)
        assertTrue(SkIRect.MakeLTRB(5, 10, 10, 5).isEmpty)
        assertFalse(SkIRect.MakeLTRB(0, 0, 1, 1).isEmpty)
        // Upstream `SkIRect::isEmpty()` also returns true when width64()|height64()
        // do not fit in int32 — the rect is considered corrupted/empty. A rect like
        // (MIN_VALUE, 0, MAX_VALUE, 1) has width64() = UINT_MAX > INT_MAX → empty.
        val overflow = SkIRect.MakeLTRB(Int.MIN_VALUE, 0, Int.MAX_VALUE, 1)
        assertTrue(overflow.isEmpty, "width64 > Int.MAX_VALUE must be treated as empty")
        // But isEmpty64() only checks `right <= left || bottom <= top` — so the
        // same rect is *not* empty under that lenient predicate.
        assertFalse(overflow.isEmpty64(), "isEmpty64 ignores int32 overflow")
    }

    @Test
    fun `MakeXYWH saturates instead of wrapping on int32 overflow`() {
        // Upstream uses Sk32_sat_add: x + w must clamp at Int.MAX_VALUE.
        val r = SkIRect.MakeXYWH(Int.MAX_VALUE - 1, 0, 10, 10)
        assertEquals(Int.MAX_VALUE, r.right, "right should saturate at Int.MAX_VALUE")
        assertEquals(10, r.bottom)
    }

    @Test
    fun `MakePtSize delegates to MakeXYWH saturation semantics`() {
        val max = SkIRect.MakePtSize(SkIPoint(Int.MAX_VALUE - 1, Int.MAX_VALUE - 2), SkISize(10, 20))
        assertEquals(Int.MAX_VALUE - 1, max.left)
        assertEquals(Int.MAX_VALUE - 2, max.top)
        assertEquals(Int.MAX_VALUE, max.right)
        assertEquals(Int.MAX_VALUE, max.bottom)

        val min = SkIRect.MakePtSize(SkIPoint(Int.MIN_VALUE + 1, Int.MIN_VALUE + 2), SkISize(-10, -20))
        assertEquals(Int.MIN_VALUE + 1, min.left)
        assertEquals(Int.MIN_VALUE + 2, min.top)
        assertEquals(Int.MIN_VALUE, min.right)
        assertEquals(Int.MIN_VALUE, min.bottom)
    }

    @Test
    fun `setXYWH offset inset adjust all saturate`() {
        // setXYWH: w pushes right past MAX_VALUE → clamps.
        val r = SkIRect.MakeEmpty()
        r.setXYWH(Int.MAX_VALUE - 5, 0, 100, 1)
        assertEquals(Int.MAX_VALUE, r.right)

        // offset: dx pushes right past MAX_VALUE → clamps; left can drop to MIN_VALUE.
        val o = SkIRect.MakeLTRB(Int.MIN_VALUE + 1, 0, Int.MAX_VALUE - 1, 10)
        o.offset(-5, 0)
        assertEquals(Int.MIN_VALUE, o.left, "left saturates at MIN_VALUE")
        o.offset(10, 0)
        // right was MAX_VALUE - 1 - 5 + 10 = MAX_VALUE + 4 → saturates.
        assertEquals(Int.MAX_VALUE, o.right, "right saturates at MAX_VALUE")

        // inset: outsetting with very large dx must saturate, not wrap.
        // (outset(dx) calls inset(-dx) — note `-Int.MAX_VALUE == Int.MIN_VALUE + 1`.)
        val ins = SkIRect.MakeLTRB(0, 0, 10, 10)
        ins.outset(Int.MAX_VALUE, 0)
        assertEquals(Int.MIN_VALUE + 1, ins.left)
        assertEquals(Int.MAX_VALUE, ins.right)
        // Saturating inset() with full MIN_VALUE also clamps both ends.
        val ins2 = SkIRect.MakeLTRB(0, 0, 10, 10)
        ins2.inset(Int.MIN_VALUE, 0)
        assertEquals(Int.MIN_VALUE, ins2.left)
        assertEquals(Int.MAX_VALUE, ins2.right)

        // adjust: large dR pushes right past MAX_VALUE.
        val a = SkIRect.MakeLTRB(0, 0, 10, 10)
        a.adjust(0, 0, Int.MAX_VALUE, 0)
        assertEquals(Int.MAX_VALUE, a.right)
    }

    @Test
    fun `makeOffset makeInset makeOutset saturate`() {
        val r = SkIRect.MakeLTRB(0, 0, 10, 10)
        val off = r.makeOffset(Int.MAX_VALUE, 0)
        assertEquals(Int.MAX_VALUE, off.left)
        assertEquals(Int.MAX_VALUE, off.right)

        val ins = r.makeInset(Int.MIN_VALUE, 0)
        // left += MIN_VALUE saturates at MIN_VALUE; right -= MIN_VALUE saturates at MAX_VALUE.
        assertEquals(Int.MIN_VALUE, ins.left)
        assertEquals(Int.MAX_VALUE, ins.right)

        // makeOutset(dx) delegates to makeInset(-dx), and `-Int.MAX_VALUE == Int.MIN_VALUE + 1`.
        val out = r.makeOutset(Int.MAX_VALUE, 0)
        assertEquals(Int.MIN_VALUE + 1, out.left)
        assertEquals(Int.MAX_VALUE, out.right)
    }

    @Test
    fun `offsetTo uses 64-bit pinned arithmetic`() {
        // Upstream: fRight = Sk64_pin_to_s32((int64_t)fRight + newX - fLeft).
        // Verify no double-overflow in int32 when newX - left would wrap.
        val r = SkIRect.MakeLTRB(Int.MIN_VALUE, 0, Int.MIN_VALUE + 10, 5)
        r.offsetTo(Int.MAX_VALUE - 9, 0)
        assertEquals(Int.MAX_VALUE - 9, r.left)
        // right should be left + width = MAX_VALUE - 9 + 10 = MAX_VALUE + 1 → saturates.
        assertEquals(Int.MAX_VALUE, r.right)
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
