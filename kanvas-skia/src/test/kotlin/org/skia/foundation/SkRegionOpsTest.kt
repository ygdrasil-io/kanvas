package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.math.SkIRect

/**
 * Phase G8 — focused coverage for [SkRegion.op] + [SkRegion.translate].
 *
 * The wide existing [SkRegionTest] suite already covers the band-merge
 * driver across every opcode ; this file pins down a few "the GM port
 * depends on this" invariants in isolation so a regression jumps out
 * even when running just this class.
 */
class SkRegionOpsTest {

    private fun rectsOf(rgn: SkRegion): List<SkIRect> {
        val it = SkRegion.Iterator(rgn)
        val out = mutableListOf<SkIRect>()
        while (!it.done()) { out.add(it.rect()); it.next() }
        return out
    }

    // ─── op : kUnion ─────────────────────────────────────────────────

    @Test
    fun `union of two non-overlapping rects bounds union of inputs`() {
        val r = SkRegion(SkIRect(0, 0, 10, 10))
        assertTrue(r.op(SkIRect(20, 20, 30, 30), SkRegion.Op.kUnion))
        assertEquals(SkIRect(0, 0, 30, 30), r.getBounds())
        // Both rects survive as individual pieces.
        val rects = rectsOf(r)
        assertEquals(2, rects.size)
        assertTrue(rects.contains(SkIRect(0, 0, 10, 10)))
        assertTrue(rects.contains(SkIRect(20, 20, 30, 30)))
    }

    @Test
    fun `union of two adjacent same-X rects coalesces into one taller rect`() {
        // Sierpinski stack-up: two abutting same-X rects must collapse.
        val r = SkRegion(SkIRect(0, 0, 27, 9))
        assertTrue(r.op(SkIRect(0, 9, 27, 18), SkRegion.Op.kUnion))
        assertTrue(r.isRect())
        assertEquals(SkIRect(0, 0, 27, 18), r.getBounds())
    }

    // ─── op : kIntersect ─────────────────────────────────────────────

    @Test
    fun `intersection of overlapping rects is the overlap rect`() {
        val r = SkRegion(SkIRect(0, 0, 20, 20))
        assertTrue(r.op(SkIRect(10, 10, 30, 30), SkRegion.Op.kIntersect))
        assertTrue(r.isRect())
        assertEquals(SkIRect(10, 10, 20, 20), r.getBounds())
    }

    @Test
    fun `intersection of disjoint rects is empty`() {
        val r = SkRegion(SkIRect(0, 0, 10, 10))
        assertFalse(r.op(SkIRect(20, 20, 30, 30), SkRegion.Op.kIntersect))
        assertTrue(r.isEmpty())
    }

    // ─── op : kDifference ────────────────────────────────────────────

    @Test
    fun `difference removes the second rect from the first`() {
        // Outer 100x100 minus a 20x20 hole interior — bounds unchanged,
        // hole pixels excluded.
        val r = SkRegion(SkIRect(0, 0, 100, 100))
        assertTrue(r.op(SkIRect(40, 40, 60, 60), SkRegion.Op.kDifference))
        assertTrue(r.isComplex())
        assertEquals(SkIRect(0, 0, 100, 100), r.getBounds())
        assertFalse(r.contains(50, 50))  // hole interior
        assertTrue(r.contains(0, 0))     // outside hole
        assertTrue(r.contains(99, 99))   // outside hole
        // Edge cases: the hole's exclusive corners are still in the region.
        assertTrue(r.contains(39, 39))
        assertTrue(r.contains(60, 60))
    }

    // ─── op : kXOR ───────────────────────────────────────────────────

    @Test
    fun `xor of overlapping rects is symmetric difference`() {
        val r = SkRegion(SkIRect(0, 0, 20, 20))
        assertTrue(r.op(SkIRect(10, 10, 30, 30), SkRegion.Op.kXOR))
        assertTrue(r.isComplex())
        // Overlap interior excluded ; the unique parts of either input remain.
        assertFalse(r.contains(15, 15))
        assertTrue(r.contains(0, 0))
        assertTrue(r.contains(25, 25))
    }

    // ─── translate ──────────────────────────────────────────────────

    @Test
    fun `translate shifts the bounds correctly on a rect region`() {
        val r = SkRegion(SkIRect(0, 0, 10, 20))
        r.translate(5, 7)
        assertEquals(SkIRect(5, 7, 15, 27), r.getBounds())
        assertTrue(r.isRect())
        // Contained pixels also shift.
        assertTrue(r.contains(5, 7))
        assertTrue(r.contains(14, 26))
        assertFalse(r.contains(0, 0))
    }

    @Test
    fun `translate shifts bounds on a complex region`() {
        // Build a 2-rect complex region and translate.
        val r = SkRegion(SkIRect(0, 0, 10, 10))
        assertTrue(r.op(SkIRect(20, 20, 30, 30), SkRegion.Op.kUnion))
        assertTrue(r.isComplex())
        r.translate(100, 200)
        assertEquals(SkIRect(100, 200, 130, 230), r.getBounds())
        assertTrue(r.contains(105, 205))   // shifted first piece
        assertTrue(r.contains(125, 225))   // shifted second piece
        assertFalse(r.contains(5, 5))      // pre-shift coordinates no longer in
    }

    @Test
    fun `translate is a no-op on an empty region`() {
        val r = SkRegion()
        r.translate(10, 20)
        assertTrue(r.isEmpty())
        assertEquals(SkIRect(0, 0, 0, 0), r.getBounds())
    }

    @Test
    fun `translate by zero leaves the region unchanged`() {
        val r = SkRegion(SkIRect(1, 2, 3, 4))
        r.translate(0, 0)
        assertEquals(SkIRect(1, 2, 3, 4), r.getBounds())
        assertTrue(r.isRect())
    }

    @Test
    fun `translate is composable with negative offsets`() {
        val r = SkRegion(SkIRect(10, 20, 30, 40))
        r.translate(-5, -10)
        assertEquals(SkIRect(5, 10, 25, 30), r.getBounds())
        // Round-trip back.
        r.translate(5, 10)
        assertEquals(SkIRect(10, 20, 30, 40), r.getBounds())
    }
}
