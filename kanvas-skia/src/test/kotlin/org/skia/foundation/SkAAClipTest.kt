package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.math.SkIRect

/**
 * Phase I3.2.a — covers the core data model + read-only / promotion
 * APIs on [SkAAClip].
 *
 * Set ops (`op`) and AA-aware [setPath] are deferred to Phase I3.2.b ;
 * this suite asserts the construction / canonicalisation contract so
 * I3.2.b can be reviewed against a stable surface.
 */
class SkAAClipTest {

    // ─── Construction ───────────────────────────────────────────────

    @Test
    fun `default ctor produces empty clip`() {
        val c = SkAAClip()
        assertTrue(c.isEmpty())
        assertFalse(c.isRect())
        assertEquals(SkIRect(0, 0, 0, 0), c.getBounds())
        assertEquals(0, c.getRowCount())
        assertEquals(0, c.computeRunCount())
    }

    @Test
    fun `rect ctor produces single-band rect with full coverage`() {
        val c = SkAAClip(SkIRect(10, 20, 30, 40))
        assertFalse(c.isEmpty())
        assertTrue(c.isRect())
        assertEquals(SkIRect(10, 20, 30, 40), c.getBounds())
        assertEquals(1, c.getRowCount())
        assertEquals(1, c.computeRunCount())
    }

    @Test
    fun `rect ctor with empty rect collapses to empty clip`() {
        assertTrue(SkAAClip(SkIRect(10, 10, 10, 10)).isEmpty())
        assertTrue(SkAAClip(SkIRect(20, 0, 10, 10)).isEmpty())
    }

    @Test
    fun `copy ctor mirrors the source`() {
        val src = SkAAClip(SkIRect(1, 2, 3, 4))
        val cp = SkAAClip(src)
        assertEquals(src.getBounds(), cp.getBounds())
        assertEquals(src.isRect(), cp.isRect())
        assertNotSame(src, cp)
        cp.setEmpty()
        assertTrue(cp.isEmpty())
        assertFalse(src.isEmpty())
    }

    // ─── State mutators ─────────────────────────────────────────────

    @Test
    fun `setEmpty clears a previously-rect clip`() {
        val c = SkAAClip(SkIRect(0, 0, 10, 10))
        assertFalse(c.setEmpty())
        assertTrue(c.isEmpty())
        assertEquals(SkIRect(0, 0, 0, 0), c.getBounds())
    }

    @Test
    fun `setRect resets to a fresh rect`() {
        val c = SkAAClip(SkIRect(0, 0, 10, 10))
        assertTrue(c.setRect(SkIRect(50, 60, 70, 80)))
        assertEquals(SkIRect(50, 60, 70, 80), c.getBounds())
        assertTrue(c.isRect())
    }

    @Test
    fun `setRect with empty rect collapses to empty`() {
        val c = SkAAClip(SkIRect(0, 0, 10, 10))
        assertFalse(c.setRect(SkIRect(0, 0, 0, 0)))
        assertTrue(c.isEmpty())
    }

    @Test
    fun `getBounds returns defensive copy`() {
        val c = SkAAClip(SkIRect(1, 2, 3, 4))
        val b = c.getBounds()
        b.left = -999
        assertEquals(1, c.getBounds().left)
    }

    // ─── setRegion (binary → AA promotion) ──────────────────────────

    @Test
    fun `setRegion empty produces empty clip`() {
        val c = SkAAClip(SkIRect(0, 0, 10, 10))
        assertFalse(c.setRegion(SkRegion()))
        assertTrue(c.isEmpty())
    }

    @Test
    fun `setRegion rect promotes to single-band rect clip`() {
        val r = SkRegion(SkIRect(10, 20, 30, 40))
        val c = SkAAClip()
        assertTrue(c.setRegion(r))
        assertTrue(c.isRect())
        assertEquals(SkIRect(10, 20, 30, 40), c.getBounds())
        assertEquals(1, c.getRowCount())
        assertEquals(1, c.computeRunCount())
    }

    @Test
    fun `setRegion with two disjoint rects produces interleaved alpha runs`() {
        // Two disjoint rects on the same Y range : [0, 10) and [20, 30)
        // both at y=[0, 10). The promoted AA clip covers x=[0, 30) with
        // alpha runs (10, 0xFF), (10, 0x00), (10, 0xFF).
        val rgn = SkRegion(SkIRect(0, 0, 10, 10))
        rgn.op(SkIRect(20, 0, 30, 10), SkRegion.Op.kUnion)
        val c = SkAAClip()
        assertTrue(c.setRegion(rgn))
        assertEquals(SkIRect(0, 0, 30, 10), c.getBounds())
        // Single band (one Y range) with 3 runs (rect, gap, rect).
        assertEquals(1, c.getRowCount())
        assertEquals(3, c.computeRunCount())
        // No longer a "rect fast path" — there's a gap.
        assertFalse(c.isRect())
    }

    @Test
    fun `setRegion with stacked rects on separate Y ranges produces multiple bands`() {
        // Stacked rects : [0, 10) at y=[0, 5) and [0, 10) at y=[10, 20).
        val rgn = SkRegion(SkIRect(0, 0, 10, 5))
        rgn.op(SkIRect(0, 10, 10, 20), SkRegion.Op.kUnion)
        val c = SkAAClip()
        assertTrue(c.setRegion(rgn))
        assertEquals(SkIRect(0, 0, 10, 20), c.getBounds())
        // The Y range covers [0, 20), but rows 5..10 are uncovered —
        // SkRegion encodes only the two source rects, so
        // SkAAClip.setRegion produces 2 distinct Y bands.
        assertEquals(2, c.getRowCount())
    }

    @Test
    fun `setRegion canonicalises adjacent equal-runs bands into one`() {
        // Two stacked, identically-sized rects at y=[0, 5) and y=[5,
        // 10) would already be canonical-merged inside SkRegion (the
        // I3.1.b op canonicalisation does it). Verify SkAAClip
        // preserves the single-band promotion.
        val rgn = SkRegion(SkIRect(0, 0, 10, 5))
        rgn.op(SkIRect(0, 5, 10, 10), SkRegion.Op.kUnion)
        // After SkRegion canonicalisation this is a single rect [0, 10) × [0, 10).
        assertTrue(rgn.isRect())
        val c = SkAAClip()
        assertTrue(c.setRegion(rgn))
        assertTrue(c.isRect())
        assertEquals(1, c.getRowCount())
    }
}
