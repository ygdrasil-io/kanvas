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

    // ─── coverage(x, y) (Phase I3.3) ────────────────────────────────

    @Test
    fun `coverage of empty clip returns 0 for any point`() {
        val c = SkAAClip()
        assertEquals(0, c.coverage(0, 0))
        assertEquals(0, c.coverage(-100, 100))
    }

    @Test
    fun `coverage of rect clip is 255 inside, 0 outside (half-open)`() {
        val c = SkAAClip(SkIRect(10, 20, 30, 40))
        // Inside corners (inclusive on left/top, exclusive on right/bottom).
        assertEquals(255, c.coverage(10, 20))
        assertEquals(255, c.coverage(29, 39))
        // Outside.
        assertEquals(0, c.coverage(30, 40))
        assertEquals(0, c.coverage(9, 20))
        assertEquals(0, c.coverage(10, 19))
    }

    @Test
    fun `coverage of two-rect clip returns 0 in the gap`() {
        val rgn = SkRegion(SkIRect(0, 0, 10, 10))
        rgn.op(SkIRect(20, 0, 30, 10), SkRegion.Op.kUnion)
        val c = SkAAClip()
        c.setRegion(rgn)
        assertEquals(255, c.coverage(5, 5))   // first rect
        assertEquals(0, c.coverage(15, 5))    // gap
        assertEquals(255, c.coverage(25, 5))  // second rect
        assertEquals(0, c.coverage(5, 15))    // outside Y
    }

    @Test
    fun `coverage on subpixel-positioned AA rect returns fractional alpha at edges`() {
        // Rect [0.5, 0.5, 9.5, 9.5] doAA=true → edge pixels get half coverage.
        val c = SkAAClip()
        val path = SkPathBuilder().addRect(org.skia.math.SkRect.MakeLTRB(0.5f, 0.5f, 9.5f, 9.5f)).detach()
        c.setPath(path, SkRegion(SkIRect(-100, -100, 100, 100)), doAA = true)
        // Centre pixel (4, 4) is fully inside → 255.
        assertEquals(255, c.coverage(4, 4))
        // Edge pixel (0, 4) only the right half is covered → ~half (in 0..127 ish).
        val edgeAlpha = c.coverage(0, 4)
        assertTrue(edgeAlpha in 1..200) { "expected fractional alpha at edge, got $edgeAlpha" }
        // Far outside → 0.
        assertEquals(0, c.coverage(-50, 50))
    }

    // ─── setPath (Phase I3.2.b) ─────────────────────────────────────

    @Test
    fun `setPath empty path with non-inverse fill yields empty clip`() {
        val c = SkAAClip()
        val empty = SkPathBuilder().detach()
        assertFalse(c.setPath(empty, SkRegion(SkIRect(0, 0, 10, 10)), doAA = true))
        assertTrue(c.isEmpty())
    }

    @Test
    fun `setPath empty clip yields empty regardless of doAA`() {
        val c = SkAAClip()
        val path = SkPathBuilder().addRect(org.skia.math.SkRect.MakeLTRB(0f, 0f, 10f, 10f)).detach()
        assertFalse(c.setPath(path, SkRegion(), doAA = false))
        assertTrue(c.isEmpty())
        assertFalse(c.setPath(path, SkRegion(), doAA = true))
        assertTrue(c.isEmpty())
    }

    @Test
    fun `setPath rect doAA false delegates through SkRegion+setRegion`() {
        val c = SkAAClip()
        val path = SkPathBuilder().addRect(org.skia.math.SkRect.MakeLTRB(0f, 0f, 10f, 10f)).detach()
        assertTrue(c.setPath(path, SkRegion(SkIRect(-100, -100, 100, 100)), doAA = false))
        assertEquals(SkIRect(0, 0, 10, 10), c.getBounds())
        assertTrue(c.isRect())
    }

    @Test
    fun `setPath rect doAA true on integer-aligned rect yields full coverage rect clip`() {
        // An integer-aligned rect path under 4×4 supersampling → every
        // sub-sample inside, all alpha=255, single rect band.
        val c = SkAAClip()
        val path = SkPathBuilder().addRect(org.skia.math.SkRect.MakeLTRB(2f, 3f, 12f, 13f)).detach()
        assertTrue(c.setPath(path, SkRegion(SkIRect(-100, -100, 100, 100)), doAA = true))
        assertEquals(SkIRect(2, 3, 12, 13), c.getBounds())
        assertTrue(c.isRect())
    }

    @Test
    fun `setPath rect doAA true on subpixel-positioned rect yields fractional edges`() {
        // Rect [0.5, 0.5, 9.5, 9.5] : centre integer pixels are full
        // coverage, edge pixels (rows 0/9, cols 0/9) get half coverage.
        val c = SkAAClip()
        val path = SkPathBuilder().addRect(org.skia.math.SkRect.MakeLTRB(0.5f, 0.5f, 9.5f, 9.5f)).detach()
        assertTrue(c.setPath(path, SkRegion(SkIRect(-100, -100, 100, 100)), doAA = true))
        // Bounds clip to the integer span [0, 10) × [0, 10).
        assertEquals(SkIRect(0, 0, 10, 10), c.getBounds())
        // It's no longer a "rect" — edge alpha runs are partial.
        assertFalse(c.isRect()) { "expected fractional-edge clip, got rect-fast-path" }
        // At least 3 distinct Y bands : top half-coverage row, full
        // coverage middle, bottom half-coverage row.
        assertTrue(c.getRowCount() >= 3) { "expected ≥ 3 bands for fractional edges, got ${c.getRowCount()}" }
    }

    @Test
    fun `setPath path entirely outside clip yields empty`() {
        val c = SkAAClip()
        val path = SkPathBuilder().addRect(org.skia.math.SkRect.MakeLTRB(200f, 200f, 300f, 300f)).detach()
        assertFalse(c.setPath(path, SkRegion(SkIRect(0, 0, 100, 100)), doAA = true))
        assertTrue(c.isEmpty())
    }

    @Test
    fun `setPath inverse fill rasterises clip minus path interior`() {
        // 100×100 clip minus a 10×10 hole → bounds = clip ;
        // coverage at hole interior should be 0, outside hole 0xFF.
        val c = SkAAClip()
        val path = SkPathBuilder()
            .addRect(org.skia.math.SkRect.MakeLTRB(40f, 40f, 50f, 50f))
            .setFillType(SkPathFillType.kInverseWinding)
            .detach()
        val clip = SkRegion(SkIRect(0, 0, 100, 100))
        assertTrue(c.setPath(path, clip, doAA = true))
        assertEquals(SkIRect(0, 0, 100, 100), c.getBounds())
        // Multiple bands : top/bottom strips full coverage, middle
        // strip with hole → at least 3 distinct Y regions.
        assertTrue(c.getRowCount() >= 3)
        assertFalse(c.isRect())
    }

    // ─── Set ops (Phase I3.2.c) ─────────────────────────────────────

    @Test
    fun `op kReplace copies other regardless of receiver state`() {
        val a = SkAAClip(SkIRect(0, 0, 10, 10))
        val b = SkAAClip(SkIRect(20, 20, 30, 30))
        assertTrue(a.op(b, SkRegion.Op.kReplace))
        assertEquals(SkIRect(20, 20, 30, 30), a.getBounds())
    }

    @Test
    fun `op union of two disjoint rects produces wider clip`() {
        val a = SkAAClip(SkIRect(0, 0, 10, 10))
        assertTrue(a.op(SkIRect(20, 0, 30, 10), SkRegion.Op.kUnion))
        // Tightening should retain the full 30-wide span (the gap
        // contributes alpha-zero runs but tight bounds keep both
        // edges).
        assertEquals(SkIRect(0, 0, 30, 10), a.getBounds())
        assertFalse(a.isRect())
    }

    @Test
    fun `op union of overlapping rects on stacked Y collapses to one rect`() {
        // [0,10)x[0,5) ∪ [0,10)x[5,10) → [0,10)x[0,10)
        val a = SkAAClip(SkIRect(0, 0, 10, 5))
        assertTrue(a.op(SkIRect(0, 5, 10, 10), SkRegion.Op.kUnion))
        assertEquals(SkIRect(0, 0, 10, 10), a.getBounds())
        assertTrue(a.isRect())
    }

    @Test
    fun `op intersect of disjoint rects yields empty`() {
        val a = SkAAClip(SkIRect(0, 0, 10, 10))
        assertFalse(a.op(SkIRect(20, 20, 30, 30), SkRegion.Op.kIntersect))
        assertTrue(a.isEmpty())
    }

    @Test
    fun `op intersect of overlapping rects yields the overlap`() {
        val a = SkAAClip(SkIRect(0, 0, 20, 20))
        assertTrue(a.op(SkIRect(10, 10, 30, 30), SkRegion.Op.kIntersect))
        assertEquals(SkIRect(10, 10, 20, 20), a.getBounds())
        assertTrue(a.isRect())
    }

    @Test
    fun `op difference of fully-enclosing rect yields empty`() {
        val a = SkAAClip(SkIRect(10, 10, 20, 20))
        assertFalse(a.op(SkIRect(0, 0, 100, 100), SkRegion.Op.kDifference))
        assertTrue(a.isEmpty())
    }

    @Test
    fun `op difference cuts a hole`() {
        val a = SkAAClip(SkIRect(0, 0, 100, 100))
        assertTrue(a.op(SkIRect(40, 40, 60, 60), SkRegion.Op.kDifference))
        assertEquals(SkIRect(0, 0, 100, 100), a.getBounds())
        assertFalse(a.isRect())
    }

    @Test
    fun `op xor of overlapping rects produces symmetric difference`() {
        val a = SkAAClip(SkIRect(0, 0, 20, 20))
        assertTrue(a.op(SkIRect(10, 10, 30, 30), SkRegion.Op.kXOR))
        assertFalse(a.isRect())
        // Bounds span the union of both rects.
        assertEquals(SkIRect(0, 0, 30, 30), a.getBounds())
    }

    @Test
    fun `op reverseDifference yields other minus this`() {
        val a = SkAAClip(SkIRect(0, 0, 100, 100))
        assertFalse(a.op(SkIRect(40, 40, 60, 60), SkRegion.Op.kReverseDifference))
        assertTrue(a.isEmpty())
    }

    @Test
    fun `op intersect of two AA clips with subpixel rects combines fractional edges`() {
        // Both clips have fractional edges from setPath ; intersect
        // should multiply edge alphas (a * b / 255).
        val a = SkAAClip()
        val pa = SkPathBuilder().addRect(org.skia.math.SkRect.MakeLTRB(0.5f, 0.5f, 9.5f, 9.5f)).detach()
        a.setPath(pa, SkRegion(SkIRect(-100, -100, 100, 100)), doAA = true)

        val b = SkAAClip()
        val pb = SkPathBuilder().addRect(org.skia.math.SkRect.MakeLTRB(2f, 2f, 8f, 8f)).detach()
        b.setPath(pb, SkRegion(SkIRect(-100, -100, 100, 100)), doAA = true)

        assertTrue(a.op(b, SkRegion.Op.kIntersect))
        // Intersect bounds = inner rect.
        assertEquals(SkIRect(2, 2, 8, 8), a.getBounds())
        assertTrue(a.isRect())
    }

    @Test
    fun `op union of empty operand with non-empty yields non-empty`() {
        val a = SkAAClip()
        val b = SkAAClip(SkIRect(0, 0, 10, 10))
        assertTrue(a.op(b, SkRegion.Op.kUnion))
        assertEquals(SkIRect(0, 0, 10, 10), a.getBounds())
    }

    @Test
    fun `op intersect with empty yields empty`() {
        val a = SkAAClip(SkIRect(0, 0, 10, 10))
        assertFalse(a.op(SkAAClip(), SkRegion.Op.kIntersect))
        assertTrue(a.isEmpty())
    }

    @Test
    fun `setPath triangle doAA true yields complex coverage region`() {
        val path = SkPathBuilder()
            .moveTo(0f, 0f)
            .lineTo(20f, 0f)
            .lineTo(10f, 20f)
            .close()
            .detach()
        val c = SkAAClip()
        val clip = SkRegion(SkIRect(-100, -100, 100, 100))
        assertTrue(c.setPath(path, clip, doAA = true))
        assertFalse(c.isEmpty())
        assertFalse(c.isRect())
        // Triangle baseline at y=0 ; apex at y=20. Bounds should
        // span the triangle.
        val b = c.getBounds()
        assertTrue(b.top >= 0 && b.top <= 1)
        assertTrue(b.bottom in 19..21)
    }
}
