package org.graphiks.math

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test

/**
 * Coverage for the iso-aligned `SkRect` and `SkIRect` helpers.
 * Mirrors the upstream Skia behavioural expectations for intersect /
 * join / round / contains / sort / NaN propagation.
 */
class SkRectTest {

    // ─── Factories ───────────────────────────────────────────────────────

    @Test
    fun `MakeLTRB stores corners verbatim`() {
        val r = SkRect.MakeLTRB(1f, 2f, 3f, 4f)
        assertEquals(1f, r.left); assertEquals(2f, r.top)
        assertEquals(3f, r.right); assertEquals(4f, r.bottom)
        assertEquals(2f, r.width()); assertEquals(2f, r.height())
    }

    @Test
    fun `MakeXYWH adds w h to origin`() {
        val r = SkRect.MakeXYWH(10f, 20f, 30f, 40f)
        assertEquals(40f, r.right); assertEquals(60f, r.bottom)
    }

    @Test
    fun `MakeWH origin is zero`() {
        val r = SkRect.MakeWH(5f, 6f)
        assertTrue(r.equalsLTRB(SkRect.MakeLTRB(0f, 0f, 5f, 6f)))
    }

    @Test
    fun `Make from SkIRect promotes to float`() {
        val r = SkRect.Make(SkIRect.MakeLTRB(1, 2, 3, 4))
        assertTrue(r.equalsLTRB(SkRect.MakeLTRB(1f, 2f, 3f, 4f)))
    }

    // ─── Predicates ──────────────────────────────────────────────────────

    @Test
    fun `isEmpty when degenerate or NaN`() {
        assertTrue(SkRect.MakeEmpty().isEmpty)
        assertTrue(SkRect.MakeLTRB(5f, 5f, 5f, 10f).isEmpty)        // zero width
        assertTrue(SkRect.MakeLTRB(5f, 10f, 10f, 5f).isEmpty)       // negative height
        assertTrue(SkRect.MakeLTRB(Float.NaN, 0f, 1f, 1f).isEmpty)  // NaN ⇒ empty (Skia)
        assertFalse(SkRect.MakeLTRB(0f, 0f, 1f, 1f).isEmpty)
    }

    @Test
    fun `isSorted vs isFinite`() {
        assertTrue(SkRect.MakeLTRB(0f, 0f, 1f, 1f).isSorted())
        assertFalse(SkRect.MakeLTRB(2f, 0f, 1f, 1f).isSorted())
        assertTrue(SkRect.MakeLTRB(0f, 0f, 1f, 1f).isFinite())
        assertFalse(SkRect.MakeLTRB(0f, 0f, Float.POSITIVE_INFINITY, 1f).isFinite())
        assertFalse(SkRect.MakeLTRB(0f, 0f, Float.NaN, 1f).isFinite())
    }

    @Test
    fun `centerX and centerY use double-precision midpoint`() {
        val r = SkRect.MakeLTRB(-1e30f, -1e30f, 1e30f, 1e30f)
        // Naive (a+b)/2 would overflow to Inf in float. Skia's
        // sk_float_midpoint stays finite via double.
        assertTrue(r.centerX().isFinite())
        assertEquals(0f, r.centerX(), 1e25f)
        assertEquals(0f, r.centerY(), 1e25f)
    }

    // ─── Mutators ────────────────────────────────────────────────────────

    @Test
    fun `setLTRB and setXYWH replace coordinates`() {
        val r = SkRect.MakeEmpty()
        r.setLTRB(1f, 2f, 3f, 4f)
        assertTrue(r.equalsLTRB(SkRect.MakeLTRB(1f, 2f, 3f, 4f)))
        r.setXYWH(0f, 0f, 5f, 6f)
        assertTrue(r.equalsLTRB(SkRect.MakeLTRB(0f, 0f, 5f, 6f)))
    }

    @Test
    fun `offset and offsetTo`() {
        val r = SkRect.MakeXYWH(10f, 20f, 5f, 6f)
        r.offset(2f, 3f)
        assertTrue(r.equalsLTRB(SkRect.MakeLTRB(12f, 23f, 17f, 29f)))
        r.offsetTo(0f, 0f)
        assertTrue(r.equalsLTRB(SkRect.MakeLTRB(0f, 0f, 5f, 6f)))
    }

    @Test
    fun `inset and outset are symmetric`() {
        val r = SkRect.MakeLTRB(0f, 0f, 10f, 10f)
        r.inset(1f, 2f)
        assertTrue(r.equalsLTRB(SkRect.MakeLTRB(1f, 2f, 9f, 8f)))
        r.outset(1f, 2f)
        assertTrue(r.equalsLTRB(SkRect.MakeLTRB(0f, 0f, 10f, 10f)))
    }

    @Test
    fun `adjust shifts each edge independently`() {
        val r = SkRect.MakeLTRB(0f, 0f, 10f, 10f)
        r.adjust(1f, 2f, 3f, 4f)
        assertTrue(r.equalsLTRB(SkRect.MakeLTRB(1f, 2f, 13f, 14f)))
    }

    @Test
    fun `sort and makeSorted swap reversed edges`() {
        val r = SkRect.MakeLTRB(10f, 20f, 0f, 5f)
        r.sort()
        assertTrue(r.equalsLTRB(SkRect.MakeLTRB(0f, 5f, 10f, 20f)))
        // makeSorted leaves source untouched
        val src = SkRect.MakeLTRB(10f, 20f, 0f, 5f)
        val sorted = src.makeSorted()
        assertTrue(sorted.equalsLTRB(SkRect.MakeLTRB(0f, 5f, 10f, 20f)))
        assertTrue(src.equalsLTRB(SkRect.MakeLTRB(10f, 20f, 0f, 5f)))
    }

    // ─── make* return new rects ──────────────────────────────────────────

    @Test
    fun `makeOffset Inset Outset return new rects`() {
        val r = SkRect.MakeLTRB(0f, 0f, 10f, 10f)
        assertTrue(r.makeOffset(2f, 3f).equalsLTRB(SkRect.MakeLTRB(2f, 3f, 12f, 13f)))
        assertTrue(r.makeInset(1f, 2f).equalsLTRB(SkRect.MakeLTRB(1f, 2f, 9f, 8f)))
        assertTrue(r.makeOutset(1f, 2f).equalsLTRB(SkRect.MakeLTRB(-1f, -2f, 11f, 12f)))
        // Source is untouched
        assertTrue(r.equalsLTRB(SkRect.MakeLTRB(0f, 0f, 10f, 10f)))
    }

    // ─── Containment ─────────────────────────────────────────────────────

    @Test
    fun `contains point is half-open`() {
        val r = SkRect.MakeLTRB(0f, 0f, 10f, 10f)
        assertTrue(r.contains(0f, 0f))           // top-left included
        assertTrue(r.contains(9.99f, 9.99f))     // interior
        assertFalse(r.contains(10f, 5f))         // right edge excluded
        assertFalse(r.contains(5f, 10f))         // bottom edge excluded
        assertFalse(r.contains(-1f, 5f))
    }

    @Test
    fun `contains rect requires non-empty inner`() {
        val outer = SkRect.MakeLTRB(0f, 0f, 10f, 10f)
        assertTrue(outer.contains(SkRect.MakeLTRB(2f, 2f, 8f, 8f)))
        assertTrue(outer.contains(SkRect.MakeLTRB(0f, 0f, 10f, 10f)))   // identical edges OK
        assertFalse(outer.contains(SkRect.MakeEmpty()))                   // empty inner
        assertFalse(outer.contains(SkRect.MakeLTRB(5f, 5f, 11f, 8f)))    // overflow right
    }

    // ─── Intersection ────────────────────────────────────────────────────

    @Test
    fun `intersect overlapping rects clips to overlap`() {
        val a = SkRect.MakeLTRB(0f, 0f, 10f, 10f)
        assertTrue(a.intersect(SkRect.MakeLTRB(5f, 5f, 15f, 15f)))
        assertTrue(a.equalsLTRB(SkRect.MakeLTRB(5f, 5f, 10f, 10f)))
    }

    @Test
    fun `intersect non-overlapping returns false leaves unchanged`() {
        val a = SkRect.MakeLTRB(0f, 0f, 5f, 5f)
        assertFalse(a.intersect(SkRect.MakeLTRB(10f, 10f, 20f, 20f)))
        assertTrue(a.equalsLTRB(SkRect.MakeLTRB(0f, 0f, 5f, 5f)))   // unchanged
    }

    @Test
    fun `intersects predicate is NaN-safe`() {
        val a = SkRect.MakeLTRB(0f, 0f, 10f, 10f)
        val nan = SkRect.MakeLTRB(Float.NaN, 0f, 5f, 5f)
        assertFalse(a.intersects(nan))   // NaN propagates to false
        assertTrue(a.intersects(SkRect.MakeLTRB(5f, 5f, 15f, 15f)))
    }

    @Test
    fun `Intersects companion accepts loose coordinates`() {
        assertTrue(SkRect.Intersects(0f, 0f, 10f, 10f, 5f, 5f, 15f, 15f))
        assertFalse(SkRect.Intersects(0f, 0f, 5f, 5f, 10f, 10f, 20f, 20f))
    }

    // ─── Join ────────────────────────────────────────────────────────────

    @Test
    fun `join expands to encompass r`() {
        val a = SkRect.MakeLTRB(0f, 0f, 5f, 5f)
        a.join(SkRect.MakeLTRB(3f, 3f, 10f, 10f))
        assertTrue(a.equalsLTRB(SkRect.MakeLTRB(0f, 0f, 10f, 10f)))
    }

    @Test
    fun `join with empty r is no-op`() {
        val a = SkRect.MakeLTRB(0f, 0f, 5f, 5f)
        a.join(SkRect.MakeEmpty())
        assertTrue(a.equalsLTRB(SkRect.MakeLTRB(0f, 0f, 5f, 5f)))
    }

    @Test
    fun `join into empty becomes r`() {
        val a = SkRect.MakeEmpty()
        a.join(SkRect.MakeLTRB(1f, 2f, 3f, 4f))
        assertTrue(a.equalsLTRB(SkRect.MakeLTRB(1f, 2f, 3f, 4f)))
    }

    // ─── Round ───────────────────────────────────────────────────────────

    @Test
    fun `round nearest-int`() {
        val r = SkRect.MakeLTRB(0.4f, 0.6f, 9.5f, 10.4f)
        val ir = r.round()
        // half-toward-+∞ (matches Skia `sk_float_round`): 0.4 → 0, 0.6 → 1,
        // 9.5 (tie) → 10, 10.4 → 10.
        assertEquals(0, ir.left); assertEquals(1, ir.top)
        assertEquals(10, ir.right); assertEquals(10, ir.bottom)
    }

    @Test
    fun `round at half-integer ties rounds toward positive infinity`() {
        // Sanity check that SkRect.round propagates SkScalarRound semantics:
        // ties at .5 round up (matches upstream Skia `floor(x + 0.5)`).
        val r = SkRect.MakeLTRB(0.5f, 1.5f, 2.5f, 3.5f)
        val ir = r.round()
        assertEquals(1, ir.left); assertEquals(2, ir.top)
        assertEquals(3, ir.right); assertEquals(4, ir.bottom)
    }

    @Test
    fun `roundOut floors min ceils max`() {
        val r = SkRect.MakeLTRB(0.1f, 0.9f, 9.1f, 9.9f)
        val ir = r.roundOut()
        assertEquals(0, ir.left); assertEquals(0, ir.top)
        assertEquals(10, ir.right); assertEquals(10, ir.bottom)
    }

    @Test
    fun `roundIn ceils min floors max`() {
        val r = SkRect.MakeLTRB(0.1f, 0.9f, 9.1f, 9.9f)
        val ir = r.roundIn()
        assertEquals(1, ir.left); assertEquals(1, ir.top)
        assertEquals(9, ir.right); assertEquals(9, ir.bottom)
    }

    // ─── Bounds from points ──────────────────────────────────────────────

    @Test
    fun `Bounds returns tight bbox`() {
        val pts = arrayOf(SkPoint(1f, 2f), SkPoint(5f, 7f), SkPoint(-3f, 4f))
        val b = SkRect.Bounds(pts)!!
        assertTrue(b.equalsLTRB(SkRect.MakeLTRB(-3f, 2f, 5f, 7f)))
    }

    @Test
    fun `Bounds returns null on non-finite point`() {
        val pts = arrayOf(SkPoint(1f, 2f), SkPoint(Float.NaN, 0f))
        assertNull(SkRect.Bounds(pts))
    }

    @Test
    fun `Bounds of empty array is empty rect`() {
        val b = SkRect.Bounds(emptyArray())
        assertTrue(b!!.isEmpty)
    }

    // ─── Corner accessors ────────────────────────────────────────────────

    @Test
    fun `TL TR BL BR return corners`() {
        val r = SkRect.MakeLTRB(1f, 2f, 3f, 4f)
        assertEquals(SkPoint(1f, 2f), r.TL())
        assertEquals(SkPoint(3f, 2f), r.TR())
        assertEquals(SkPoint(1f, 4f), r.BL())
        assertEquals(SkPoint(3f, 4f), r.BR())
    }
}
