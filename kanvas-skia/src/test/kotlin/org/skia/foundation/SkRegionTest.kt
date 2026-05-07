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

    // ─── Set ops + setPath stubs ────────────────────────────────────

    @Test
    fun `op with region throws UnsupportedOperationException`() {
        val r = SkRegion(SkIRect(0, 0, 10, 10))
        val ex = assertThrows(UnsupportedOperationException::class.java) {
            r.op(SkRegion(SkIRect(5, 5, 15, 15)), SkRegion.Op.kUnion)
        }
        assertTrue(ex.message?.contains("I3.1.b") == true) {
            "Expected message to mention I3.1.b, got '${ex.message}'"
        }
    }

    @Test
    fun `op with rect throws UnsupportedOperationException`() {
        val r = SkRegion(SkIRect(0, 0, 10, 10))
        assertThrows(UnsupportedOperationException::class.java) {
            r.op(SkIRect(5, 5, 15, 15), SkRegion.Op.kIntersect)
        }
    }

    // setPath stub is exercised in I3.1.b once SkPath rasterisation lands.
}
