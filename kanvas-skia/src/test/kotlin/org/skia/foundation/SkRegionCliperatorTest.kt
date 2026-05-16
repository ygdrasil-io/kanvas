package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.graphiks.math.SkIRect

/**
 * R2.18 — covers `SkRegion.Cliperator` (region rect iterator clipped
 * against an `SkIRect`) and `SkRegion.Spanerator` (single-scanline
 * span iterator).
 */
class SkRegionCliperatorTest {

    private fun threeRectRegion(): SkRegion {
        val r = SkRegion(SkIRect(0, 0, 10, 10))
        r.op(SkIRect(20, 20, 30, 30), SkRegion.Op.kUnion)
        r.op(SkIRect(40, 40, 50, 50), SkRegion.Op.kUnion)
        return r
    }

    // ─── Cliperator ───────────────────────────────────────────────

    @Test
    fun `cliperator over empty clip returns done immediately`() {
        val c = SkRegion.Cliperator(threeRectRegion(), SkIRect(0, 0, 0, 0))
        assertTrue(c.done())
    }

    @Test
    fun `cliperator with clip covering full region yields all rects`() {
        val rgn = threeRectRegion()
        val c = SkRegion.Cliperator(rgn, SkIRect(-100, -100, 1000, 1000))
        val rects = mutableListOf<SkIRect>()
        while (!c.done()) { rects.add(c.rect()); c.next() }
        assertEquals(3, rects.size)
        assertTrue(rects.contains(SkIRect(0, 0, 10, 10)))
        assertTrue(rects.contains(SkIRect(20, 20, 30, 30)))
        assertTrue(rects.contains(SkIRect(40, 40, 50, 50)))
    }

    @Test
    fun `cliperator with clip overlapping only second rect skips the others`() {
        val rgn = threeRectRegion()
        val c = SkRegion.Cliperator(rgn, SkIRect(22, 22, 28, 28))
        assertFalse(c.done())
        assertEquals(SkIRect(22, 22, 28, 28), c.rect())
        c.next()
        assertTrue(c.done())
    }

    @Test
    fun `cliperator returns intersection rects (not raw region rects)`() {
        val rgn = SkRegion(SkIRect(0, 0, 100, 100))
        val c = SkRegion.Cliperator(rgn, SkIRect(10, 10, 50, 50))
        assertEquals(SkIRect(10, 10, 50, 50), c.rect())
        c.next()
        assertTrue(c.done())
    }

    // ─── Spanerator ───────────────────────────────────────────────

    @Test
    fun `spanerator on rect region yields one span at an in-range scanline`() {
        val rgn = SkRegion(SkIRect(10, 5, 30, 15))
        val sp = SkRegion.Spanerator(rgn, y = 8, left = 0, right = 100)
        val s = sp.next()
        assertEquals(SkRegion.Spanerator.Span(10, 30), s)
        // Exhausted.
        assertEquals(null, sp.next())
    }

    @Test
    fun `spanerator clips spans to the requested left-right window`() {
        val rgn = SkRegion(SkIRect(0, 0, 100, 10))
        val sp = SkRegion.Spanerator(rgn, y = 3, left = 20, right = 80)
        val s = sp.next()
        assertEquals(SkRegion.Spanerator.Span(20, 80), s)
        assertEquals(null, sp.next())
    }

    @Test
    fun `spanerator returns no spans for a y outside the region`() {
        val rgn = SkRegion(SkIRect(0, 0, 10, 10))
        // y = 100 is well below the bottom of the only band.
        val sp = SkRegion.Spanerator(rgn, y = 100, left = -10, right = 100)
        assertEquals(null, sp.next())
    }

    @Test
    fun `spanerator next(outLR) populates the out-array when a span is found`() {
        val rgn = SkRegion(SkIRect(5, 0, 15, 10))
        val sp = SkRegion.Spanerator(rgn, y = 3, left = 0, right = 100)
        val out = IntArray(2)
        assertTrue(sp.next(out))
        assertEquals(5, out[0])
        assertEquals(15, out[1])
        assertFalse(sp.next(out))
    }
}
