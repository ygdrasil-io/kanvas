package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.math.SkIRect

/**
 * Phase I3.1.a — covers the core data model + queries on [SkRegion].
 *
 * Set ops (`op`) and `setPath` are deferred to Phase I3.1.b ; this
 * suite asserts that the unsupported calls throw clearly so I3.1.b
 * can be reviewed against a stable contract.
 */
class SkRegionTest {

    // ─── Construction ───────────────────────────────────────────────

    @Test
    fun `default ctor produces empty region`() {
        val r = SkRegion()
        assertTrue(r.isEmpty())
        assertFalse(r.isRect())
        assertFalse(r.isComplex())
        assertEquals(SkIRect(0, 0, 0, 0), r.getBounds())
        assertEquals(0, r.computeRegionComplexity())
    }

    @Test
    fun `rect ctor with non-empty rect produces rect region`() {
        val r = SkRegion(SkIRect(10, 20, 30, 40))
        assertFalse(r.isEmpty())
        assertTrue(r.isRect())
        assertFalse(r.isComplex())
        assertEquals(SkIRect(10, 20, 30, 40), r.getBounds())
        assertEquals(1, r.computeRegionComplexity())
    }

    @Test
    fun `rect ctor with empty rect produces empty region`() {
        assertTrue(SkRegion(SkIRect(10, 10, 10, 10)).isEmpty())
        // Inverted (right < left) is empty per SkIRect semantics.
        assertTrue(SkRegion(SkIRect(20, 0, 10, 10)).isEmpty())
    }

    @Test
    fun `copy ctor mirrors the source`() {
        val src = SkRegion(SkIRect(1, 2, 3, 4))
        val cp = SkRegion(src)
        assertEquals(src.getBounds(), cp.getBounds())
        assertEquals(src.isRect(), cp.isRect())
        assertNotSame(src, cp)
        // Mutating the copy doesn't affect the source.
        cp.setEmpty()
        assertTrue(cp.isEmpty())
        assertFalse(src.isEmpty())
    }

    // ─── State mutators ─────────────────────────────────────────────

    @Test
    fun `setEmpty clears a previously rectangular region`() {
        val r = SkRegion(SkIRect(0, 0, 10, 10))
        assertFalse(r.setEmpty())  // contract: returns false for empty
        assertTrue(r.isEmpty())
        assertEquals(SkIRect(0, 0, 0, 0), r.getBounds())
    }

    @Test
    fun `setRect resets to a fresh rectangle`() {
        val r = SkRegion(SkIRect(0, 0, 10, 10))
        assertTrue(r.setRect(SkIRect(50, 60, 70, 80)))
        assertEquals(SkIRect(50, 60, 70, 80), r.getBounds())
        assertTrue(r.isRect())
    }

    @Test
    fun `setRect with empty rect collapses to empty region`() {
        val r = SkRegion(SkIRect(0, 0, 10, 10))
        assertFalse(r.setRect(SkIRect(0, 0, 0, 0)))
        assertTrue(r.isEmpty())
    }

    @Test
    fun `set deep-copies the source bounds and rect flag`() {
        val src = SkRegion(SkIRect(1, 2, 3, 4))
        val dst = SkRegion()
        assertTrue(dst.set(src))
        assertEquals(src.getBounds(), dst.getBounds())
        assertTrue(dst.isRect())
        // getBounds() must return defensive copies — mutation should
        // not infect the source.
        val b = dst.getBounds()
        b.left = -999
        assertEquals(1, dst.getBounds().left)
    }

    // ─── Containment ────────────────────────────────────────────────

    @Test
    fun `contains point returns true for inside, false for edges and outside`() {
        val r = SkRegion(SkIRect(10, 20, 30, 40))
        assertTrue(r.contains(10, 20))   // top-left corner included
        assertTrue(r.contains(29, 39))   // last interior pixel
        assertFalse(r.contains(30, 40))  // exclusive right/bottom
        assertFalse(r.contains(9, 20))   // outside-left
        assertFalse(r.contains(10, 19))  // outside-top
    }

    @Test
    fun `empty region contains nothing`() {
        val r = SkRegion()
        assertFalse(r.contains(0, 0))
        assertFalse(r.contains(SkIRect(0, 0, 10, 10)))
    }

    @Test
    fun `contains rect for fully-inside rect`() {
        val r = SkRegion(SkIRect(0, 0, 100, 100))
        assertTrue(r.contains(SkIRect(10, 10, 50, 50)))
        // rect == region : inclusive bound is inside.
        assertTrue(r.contains(SkIRect(0, 0, 100, 100)))
    }

    @Test
    fun `contains rect rejects partial overlap and outside`() {
        val r = SkRegion(SkIRect(0, 0, 100, 100))
        // Hangs over the right edge.
        assertFalse(r.contains(SkIRect(50, 50, 110, 60)))
        // Fully outside (above).
        assertFalse(r.contains(SkIRect(0, -10, 10, -1)))
    }

    @Test
    fun `contains rect with empty rect returns false`() {
        val r = SkRegion(SkIRect(0, 0, 100, 100))
        assertFalse(r.contains(SkIRect(0, 0, 0, 0)))
    }

    // ─── Iterator ───────────────────────────────────────────────────

    @Test
    fun `iterator over empty region is immediately done`() {
        val it = SkRegion.Iterator(SkRegion())
        assertTrue(it.done())
        assertThrows(NoSuchElementException::class.java) { it.rect() }
    }

    @Test
    fun `iterator over rect region emits exactly one rect`() {
        val r = SkRegion(SkIRect(1, 2, 3, 4))
        val it = SkRegion.Iterator(r)
        assertFalse(it.done())
        assertEquals(SkIRect(1, 2, 3, 4), it.rect())
        it.next()
        assertTrue(it.done())
    }

    @Test
    fun `iterator next at end is idempotent`() {
        val it = SkRegion.Iterator(SkRegion(SkIRect(0, 0, 1, 1)))
        it.next()
        assertTrue(it.done())
        it.next()  // second call must not throw
        assertTrue(it.done())
    }

    @Test
    fun `iterator returns fresh SkIRect copies (not shared mutable state)`() {
        val r = SkRegion(SkIRect(0, 0, 10, 10))
        val it = SkRegion.Iterator(r)
        val first = it.rect()
        first.left = -42
        // Source region untouched.
        assertEquals(0, r.getBounds().left)
    }

    // ─── Set ops (Phase I3.1.b) ─────────────────────────────────────

    private fun rectsOf(rgn: SkRegion): List<SkIRect> {
        val it = SkRegion.Iterator(rgn)
        val out = mutableListOf<SkIRect>()
        while (!it.done()) { out.add(it.rect()); it.next() }
        return out
    }

    @Test
    fun `kReplace copies rgn regardless of receiver state`() {
        val a = SkRegion(SkIRect(0, 0, 10, 10))
        val b = SkRegion(SkIRect(20, 20, 30, 30))
        assertTrue(a.op(b, SkRegion.Op.kReplace))
        assertEquals(SkIRect(20, 20, 30, 30), a.getBounds())
        assertTrue(a.isRect())
    }

    @Test
    fun `union of two disjoint rects produces complex region`() {
        val a = SkRegion(SkIRect(0, 0, 10, 10))
        assertTrue(a.op(SkIRect(20, 20, 30, 30), SkRegion.Op.kUnion))
        assertFalse(a.isRect())
        assertTrue(a.isComplex())
        // Bounds spans both pieces.
        assertEquals(SkIRect(0, 0, 30, 30), a.getBounds())
        // Complexity = 2 (one rect per piece).
        assertEquals(2, a.computeRegionComplexity())
        // Iterator emits both.
        val rects = rectsOf(a)
        assertEquals(2, rects.size)
        assertTrue(rects.contains(SkIRect(0, 0, 10, 10)))
        assertTrue(rects.contains(SkIRect(20, 20, 30, 30)))
    }

    @Test
    fun `union of overlapping rects collapses to one rect when merger is rect`() {
        // [0,10)x[0,20) ∪ [0,10)x[10,30) = [0,10)x[0,30)
        val a = SkRegion(SkIRect(0, 0, 10, 20))
        assertTrue(a.op(SkIRect(0, 10, 10, 30), SkRegion.Op.kUnion))
        assertTrue(a.isRect())
        assertEquals(SkIRect(0, 0, 10, 30), a.getBounds())
    }

    @Test
    fun `intersect of disjoint rects yields empty`() {
        val a = SkRegion(SkIRect(0, 0, 10, 10))
        assertFalse(a.op(SkIRect(20, 20, 30, 30), SkRegion.Op.kIntersect))
        assertTrue(a.isEmpty())
    }

    @Test
    fun `intersect of overlapping rects yields the overlap`() {
        val a = SkRegion(SkIRect(0, 0, 20, 20))
        assertTrue(a.op(SkIRect(10, 10, 30, 30), SkRegion.Op.kIntersect))
        assertTrue(a.isRect())
        assertEquals(SkIRect(10, 10, 20, 20), a.getBounds())
    }

    @Test
    fun `intersect of contained rect yields the inner rect`() {
        val a = SkRegion(SkIRect(0, 0, 100, 100))
        assertTrue(a.op(SkIRect(10, 10, 20, 20), SkRegion.Op.kIntersect))
        assertEquals(SkIRect(10, 10, 20, 20), a.getBounds())
    }

    @Test
    fun `difference of full enclosing rect yields empty`() {
        val a = SkRegion(SkIRect(10, 10, 20, 20))
        assertFalse(a.op(SkIRect(0, 0, 100, 100), SkRegion.Op.kDifference))
        assertTrue(a.isEmpty())
    }

    @Test
    fun `difference cuts a hole in the receiver`() {
        // Outer 100x100 minus a 20x20 hole in the middle → complex
        // 4-or-fewer-rect region.
        val a = SkRegion(SkIRect(0, 0, 100, 100))
        assertTrue(a.op(SkIRect(40, 40, 60, 60), SkRegion.Op.kDifference))
        assertTrue(a.isComplex())
        // Bounds unchanged (the hole is interior).
        assertEquals(SkIRect(0, 0, 100, 100), a.getBounds())
        // The hole pixel must NOT be contained.
        assertFalse(a.contains(50, 50))
        // A pixel just outside the hole IS contained.
        assertTrue(a.contains(0, 0))
        assertTrue(a.contains(99, 99))
    }

    @Test
    fun `xor of overlapping rects is symmetric difference`() {
        val a = SkRegion(SkIRect(0, 0, 20, 20))
        assertTrue(a.op(SkIRect(10, 10, 30, 30), SkRegion.Op.kXOR))
        assertTrue(a.isComplex())
        // Overlap (10..20, 10..20) is excluded ; both unique parts remain.
        assertFalse(a.contains(15, 15))  // overlap interior excluded
        assertTrue(a.contains(0, 0))     // a-only kept
        assertTrue(a.contains(25, 25))   // b-only kept
    }

    @Test
    fun `reverseDifference equals (rgn DIFF this)`() {
        // reverseDifference(A, B) = B - A. With A ⊃ B (outer ⊃ inner),
        // B - A = ∅, so op() returns false and the result is empty.
        val a = SkRegion(SkIRect(0, 0, 100, 100))
        assertFalse(a.op(SkIRect(40, 40, 60, 60), SkRegion.Op.kReverseDifference))
        assertTrue(a.isEmpty())
    }

    @Test
    fun `reverseDifference picks up rgn-only area`() {
        val a = SkRegion(SkIRect(0, 0, 10, 10))
        // B - A where A is small rect, B is bigger rect containing it.
        assertTrue(a.op(SkIRect(0, 0, 30, 30), SkRegion.Op.kReverseDifference))
        // Result = B - A = the rest of B outside A.
        assertFalse(a.isEmpty())
        // Pixels inside A should NOT be in result.
        assertFalse(a.contains(5, 5))
        // Pixels in B but not in A should be in result.
        assertTrue(a.contains(15, 15))
    }

    @Test
    fun `union of empty with non-empty yields the non-empty operand`() {
        val a = SkRegion()
        assertTrue(a.op(SkIRect(0, 0, 10, 10), SkRegion.Op.kUnion))
        assertEquals(SkIRect(0, 0, 10, 10), a.getBounds())
    }

    @Test
    fun `intersect with empty yields empty`() {
        val a = SkRegion(SkIRect(0, 0, 10, 10))
        assertFalse(a.op(SkRegion(), SkRegion.Op.kIntersect))
        assertTrue(a.isEmpty())
    }

    @Test
    fun `op canonicalises adjacent equal-X bands into a single band`() {
        // Build a 100x100 square via two unioned 100x50 strips that
        // share the same X interval. The result should canonicalise
        // back to a single rect.
        val a = SkRegion(SkIRect(0, 0, 100, 50))
        assertTrue(a.op(SkIRect(0, 50, 100, 100), SkRegion.Op.kUnion))
        assertTrue(a.isRect()) { "expected coalesced rect, got ${rectsOf(a)}" }
        assertEquals(SkIRect(0, 0, 100, 100), a.getBounds())
    }

    @Test
    fun `setPath stub still throws UnsupportedOperationException pending I3 1 c`() {
        val r = SkRegion()
        val emptyPath = SkPathBuilder().detach()
        val ex = assertThrows(UnsupportedOperationException::class.java) {
            r.setPath(emptyPath, SkRegion(SkIRect(0, 0, 10, 10)))
        }
        assertTrue(ex.message?.contains("I3.1.c") == true)
    }
}
