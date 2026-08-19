package org.graphiks.math.geometry

import org.graphiks.math.vector.Vector2F32
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test

class RectF32Test {

    @Test
    fun `ofLTRB stores corners verbatim`() {
        val r = RectF32.ofLTRB(1f, 2f, 3f, 4f)
        assertEquals(1f, r.left); assertEquals(2f, r.top)
        assertEquals(3f, r.right); assertEquals(4f, r.bottom)
        assertEquals(2f, r.width()); assertEquals(2f, r.height())
    }

    @Test
    fun `ofOriginSize adds w h to origin`() {
        val r = RectF32.ofOriginSize(10f, 20f, 30f, 40f)
        assertEquals(40f, r.right); assertEquals(60f, r.bottom)
    }

    @Test
    fun `ofSize origin is zero`() {
        val r = RectF32.ofSize(5f, 6f)
        assertTrue(r.contentEqualsLTRB(RectF32.ofLTRB(0f, 0f, 5f, 6f)))
    }

    @Test
    fun `Make from SizeI32 promotes to float`() {
        val r = RectF32.from(RectI32.ofLTRB(1, 2, 3, 4))
        assertTrue(r.contentEqualsLTRB(RectF32.ofLTRB(1f, 2f, 3f, 4f)))
    }

    @Test
    fun `isEmpty when degenerate or NaN`() {
        assertTrue(RectF32.Empty.isEmpty)
        assertTrue(RectF32.ofLTRB(5f, 5f, 5f, 10f).isEmpty)
        assertTrue(RectF32.ofLTRB(5f, 10f, 10f, 5f).isEmpty)
        assertTrue(RectF32.ofLTRB(Float.NaN, 0f, 1f, 1f).isEmpty)
        assertFalse(RectF32.ofLTRB(0f, 0f, 1f, 1f).isEmpty)
    }

    @Test
    fun `isSorted vs isFinite`() {
        assertTrue(RectF32.ofLTRB(0f, 0f, 1f, 1f).isSorted())
        assertFalse(RectF32.ofLTRB(2f, 0f, 1f, 1f).isSorted())
        assertTrue(RectF32.ofLTRB(0f, 0f, 1f, 1f).isFinite())
        assertFalse(RectF32.ofLTRB(0f, 0f, Float.POSITIVE_INFINITY, 1f).isFinite())
        assertFalse(RectF32.ofLTRB(0f, 0f, Float.NaN, 1f).isFinite())
    }

    @Test
    fun `centerX and centerY use double-precision midpoint`() {
        val r = RectF32.ofLTRB(-1e30f, -1e30f, 1e30f, 1e30f)
        assertTrue(r.centerX().isFinite())
        assertEquals(0f, r.centerX(), 1e25f)
        assertEquals(0f, r.centerY(), 1e25f)
    }

    @Test
    fun `setLTRB and setXYWH replace coordinates`() {
        val r = RectF32(0f, 0f, 0f, 0f)
        r.setLTRB(1f, 2f, 3f, 4f)
        assertTrue(r.contentEqualsLTRB(RectF32.ofLTRB(1f, 2f, 3f, 4f)))
        r.setXYWH(0f, 0f, 5f, 6f)
        assertTrue(r.contentEqualsLTRB(RectF32.ofLTRB(0f, 0f, 5f, 6f)))
    }

    @Test
    fun `offset and offsetTo`() {
        val r = RectF32.ofOriginSize(10f, 20f, 5f, 6f)
        r.offset(2f, 3f)
        assertTrue(r.contentEqualsLTRB(RectF32.ofLTRB(12f, 23f, 17f, 29f)))
        r.offsetTo(0f, 0f)
        assertTrue(r.contentEqualsLTRB(RectF32.ofLTRB(0f, 0f, 5f, 6f)))
    }

    @Test
    fun `inset and outset are symmetric`() {
        val r = RectF32.ofLTRB(0f, 0f, 10f, 10f)
        r.inset(1f, 2f)
        assertTrue(r.contentEqualsLTRB(RectF32.ofLTRB(1f, 2f, 9f, 8f)))
        r.outset(1f, 2f)
        assertTrue(r.contentEqualsLTRB(RectF32.ofLTRB(0f, 0f, 10f, 10f)))
    }

    @Test
    fun `adjust shifts each edge independently`() {
        val r = RectF32.ofLTRB(0f, 0f, 10f, 10f)
        r.adjust(1f, 2f, 3f, 4f)
        assertTrue(r.contentEqualsLTRB(RectF32.ofLTRB(1f, 2f, 13f, 14f)))
    }

    @Test
    fun `sort and makeSorted swap reversed edges`() {
        val r = RectF32.ofLTRB(10f, 20f, 0f, 5f)
        r.sort()
        assertTrue(r.contentEqualsLTRB(RectF32.ofLTRB(0f, 5f, 10f, 20f)))
        val src = RectF32.ofLTRB(10f, 20f, 0f, 5f)
        val sorted = src.makeSorted()
        assertTrue(sorted.contentEqualsLTRB(RectF32.ofLTRB(0f, 5f, 10f, 20f)))
        assertTrue(src.contentEqualsLTRB(RectF32.ofLTRB(10f, 20f, 0f, 5f)))
    }

    @Test
    fun `offsetBy Inset Outset return new rects`() {
        val r = RectF32.ofLTRB(0f, 0f, 10f, 10f)
        assertTrue(r.offsetBy(2f, 3f).contentEqualsLTRB(RectF32.ofLTRB(2f, 3f, 12f, 13f)))
        assertTrue(r.insetBy(1f, 2f).contentEqualsLTRB(RectF32.ofLTRB(1f, 2f, 9f, 8f)))
        assertTrue(r.outsetBy(1f, 2f).contentEqualsLTRB(RectF32.ofLTRB(-1f, -2f, 11f, 12f)))
        assertTrue(r.contentEqualsLTRB(RectF32.ofLTRB(0f, 0f, 10f, 10f)))
    }

    @Test
    fun `contains point is half-open`() {
        val r = RectF32.ofLTRB(0f, 0f, 10f, 10f)
        assertTrue(r.contains(0f, 0f))
        assertTrue(r.contains(9.99f, 9.99f))
        assertFalse(r.contains(10f, 5f))
        assertFalse(r.contains(5f, 10f))
        assertFalse(r.contains(-1f, 5f))
    }

    @Test
    fun `contains rect requires non-empty inner`() {
        val outer = RectF32.ofLTRB(0f, 0f, 10f, 10f)
        assertTrue(outer.contains(RectF32.ofLTRB(2f, 2f, 8f, 8f)))
        assertTrue(outer.contains(RectF32.ofLTRB(0f, 0f, 10f, 10f)))
        assertFalse(outer.contains(RectF32.Empty))
        assertFalse(outer.contains(RectF32.ofLTRB(5f, 5f, 11f, 8f)))
    }

    @Test
    fun `intersect overlapping rects clips to overlap`() {
        val a = RectF32.ofLTRB(0f, 0f, 10f, 10f)
        assertTrue(a.intersect(RectF32.ofLTRB(5f, 5f, 15f, 15f)))
        assertTrue(a.contentEqualsLTRB(RectF32.ofLTRB(5f, 5f, 10f, 10f)))
    }

    @Test
    fun `intersect non-overlapping returns false leaves unchanged`() {
        val a = RectF32.ofLTRB(0f, 0f, 5f, 5f)
        assertFalse(a.intersect(RectF32.ofLTRB(10f, 10f, 20f, 20f)))
        assertTrue(a.contentEqualsLTRB(RectF32.ofLTRB(0f, 0f, 5f, 5f)))
    }

    @Test
    fun `intersects predicate is NaN-safe`() {
        val a = RectF32.ofLTRB(0f, 0f, 10f, 10f)
        val nan = RectF32.ofLTRB(Float.NaN, 0f, 5f, 5f)
        assertFalse(a.intersects(nan))
        assertTrue(a.intersects(RectF32.ofLTRB(5f, 5f, 15f, 15f)))
    }

    @Test
    fun `intersects companion accepts loose coordinates`() {
        assertTrue(RectF32.intersects(0f, 0f, 10f, 10f, 5f, 5f, 15f, 15f))
        assertFalse(RectF32.intersects(0f, 0f, 5f, 5f, 10f, 10f, 20f, 20f))
    }

    @Test
    fun `join expands to encompass r`() {
        val a = RectF32.ofLTRB(0f, 0f, 5f, 5f)
        a.join(RectF32.ofLTRB(3f, 3f, 10f, 10f))
        assertTrue(a.contentEqualsLTRB(RectF32.ofLTRB(0f, 0f, 10f, 10f)))
    }

    @Test
    fun `join with empty r is no-op`() {
        val a = RectF32.ofLTRB(0f, 0f, 5f, 5f)
        a.join(RectF32.Empty)
        assertTrue(a.contentEqualsLTRB(RectF32.ofLTRB(0f, 0f, 5f, 5f)))
    }

    @Test
    fun `join into empty becomes r`() {
        val a = RectF32(0f, 0f, 0f, 0f)
        a.join(RectF32.ofLTRB(1f, 2f, 3f, 4f))
        assertTrue(a.contentEqualsLTRB(RectF32.ofLTRB(1f, 2f, 3f, 4f)))
    }

    @Test
    fun `round nearest-int`() {
        val r = RectF32.ofLTRB(0.4f, 0.6f, 9.5f, 10.4f)
        val ir = r.round()
        assertEquals(0, ir.left); assertEquals(1, ir.top)
        assertEquals(10, ir.right); assertEquals(10, ir.bottom)
    }

    @Test
    fun `round at half-integer ties rounds toward positive infinity`() {
        val r = RectF32.ofLTRB(0.5f, 1.5f, 2.5f, 3.5f)
        val ir = r.round()
        assertEquals(1, ir.left); assertEquals(2, ir.top)
        assertEquals(3, ir.right); assertEquals(4, ir.bottom)

        val negative = RectF32.ofLTRB(-1.5f, -0.5f, 0.5f, 1.5f).round()
        assertEquals(-1, negative.left); assertEquals(0, negative.top)
        assertEquals(1, negative.right); assertEquals(2, negative.bottom)
    }

    @Test
    fun `roundOut floors min ceils max`() {
        val r = RectF32.ofLTRB(0.1f, 0.9f, 9.1f, 9.9f)
        val ir = r.roundOut()
        assertEquals(0, ir.left); assertEquals(0, ir.top)
        assertEquals(10, ir.right); assertEquals(10, ir.bottom)
    }

    @Test
    fun `roundIn ceils min floors max`() {
        val r = RectF32.ofLTRB(0.1f, 0.9f, 9.1f, 9.9f)
        val ir = r.roundIn()
        assertEquals(1, ir.left); assertEquals(1, ir.top)
        assertEquals(9, ir.right); assertEquals(9, ir.bottom)
    }

    @Test
    fun `bounds returns tight bbox`() {
        val pts = arrayOf(Vector2F32.of(1f, 2f), Vector2F32.of(5f, 7f), Vector2F32.of(-3f, 4f))
        val b = RectF32.bounds(pts)!!
        assertTrue(b.contentEqualsLTRB(RectF32.ofLTRB(-3f, 2f, 5f, 7f)))
    }

    @Test
    fun `bounds returns null on non-finite point`() {
        val pts = arrayOf(Vector2F32.of(1f, 2f), Vector2F32.of(Float.NaN, 0f))
        assertNull(RectF32.bounds(pts))
    }

    @Test
    fun `bounds of empty array is empty rect`() {
        val b = RectF32.bounds(emptyArray())
        assertNotNull(b)
        assertTrue(b.isEmpty)
    }

    @Test
    fun `corner accessors return corners`() {
        val r = RectF32.ofLTRB(1f, 2f, 3f, 4f)
        assertEquals(Vector2F32.of(1f, 2f), r.topLeft())
        assertEquals(Vector2F32.of(3f, 2f), r.topRight())
        assertEquals(Vector2F32.of(1f, 4f), r.bottomLeft())
        assertEquals(Vector2F32.of(3f, 4f), r.bottomRight())
    }
}
