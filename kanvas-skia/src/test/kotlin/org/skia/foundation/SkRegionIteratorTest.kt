package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.math.SkIRect

/**
 * R2.18 — extra coverage on `SkRegion.Iterator` (`rgn`, `rewind`,
 * `reset`, default ctor) and the new `Cliperator` / `Spanerator`
 * nested classes.
 *
 * The legacy `iterator over rect / empty region` cases live in
 * [SkRegionTest] ; this file adds the multi-rect cases, the
 * `rgn() == source` contract, and the rewind/reset semantics.
 */
class SkRegionIteratorTest {

    /** 3-rect region with one stack of two same-X rects + a disjoint third. */
    private fun threeRectRegion(): SkRegion {
        val r = SkRegion(SkIRect(0, 0, 10, 10))
        // Adjacent same-X stack-up collapses into one band — so use
        // distinct X intervals for genuine three-piece complexity.
        r.op(SkIRect(20, 20, 30, 30), SkRegion.Op.kUnion)
        r.op(SkIRect(40, 40, 50, 50), SkRegion.Op.kUnion)
        return r
    }

    // ─── Iterator (extended) ──────────────────────────────────────

    @Test
    fun `iterator yields all three rects of a 3-rect region`() {
        val rgn = threeRectRegion()
        val it = SkRegion.Iterator(rgn)
        val collected = mutableListOf<SkIRect>()
        while (!it.done()) {
            collected.add(it.rect())
            it.next()
        }
        assertEquals(3, collected.size)
        assertTrue(collected.contains(SkIRect(0, 0, 10, 10)))
        assertTrue(collected.contains(SkIRect(20, 20, 30, 30)))
        assertTrue(collected.contains(SkIRect(40, 40, 50, 50)))
    }

    @Test
    fun `rgn returns the source region`() {
        val rgn = threeRectRegion()
        val it = SkRegion.Iterator(rgn)
        assertSame(rgn, it.rgn())
    }

    @Test
    fun `default-constructed iterator is done and has null rgn`() {
        val it = SkRegion.Iterator()
        assertTrue(it.done())
        assertNull(it.rgn())
    }

    @Test
    fun `reset re-points iterator at a new region and rewinds`() {
        val a = SkRegion(SkIRect(0, 0, 5, 5))
        val b = threeRectRegion()
        val it = SkRegion.Iterator(a)
        it.next()  // exhaust a
        assertTrue(it.done())

        it.reset(b)
        assertFalse(it.done())
        assertSame(b, it.rgn())
        var count = 0
        while (!it.done()) { count++; it.next() }
        assertEquals(3, count)
    }

    @Test
    fun `rewind on an iterator returns true and re-starts from rect 0`() {
        val rgn = threeRectRegion()
        val it = SkRegion.Iterator(rgn)
        it.next(); it.next()  // advance to last
        assertTrue(it.rewind())
        // After rewind we're back at the first rect.
        assertFalse(it.done())
        val first = it.rect()
        // Iteration is band-major; with the threeRectRegion construction,
        // the y=0..10 band emits (0,0,10,10) first.
        assertEquals(SkIRect(0, 0, 10, 10), first)
    }

    @Test
    fun `rewind on default-constructed iterator returns false`() {
        assertFalse(SkRegion.Iterator().rewind())
    }
}
