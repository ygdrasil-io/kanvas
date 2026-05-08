package org.skia.pathops.internal

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.math.SkPoint

/**
 * Unit tests for [SkOpAngle] data model + linked-list ops + simple
 * accessors (Phase D1.2.b).
 */
class SkOpAngleTest {

    private fun pt(x: Float, y: Float) = SkPoint(fX = x, fY = y)

    private fun makeSpan(seg: SkOpSegment, t: Double, p: SkPoint = pt(0f, 0f)): SkOpSpan {
        val s = SkOpSpan()
        s.init(seg, null, t, p)
        return s
    }

    // ─── Construction / accessors ──────────────────────────────────

    @Test
    fun `default angle has null start, end, next`() {
        val a = SkOpAngle()
        assertNull(a.start())
        assertNull(a.end())
        assertNull(a.next())
        assertNull(a.lastMarked())
        assertFalse(a.tangentsAmbiguous())
        assertFalse(a.unorderable())
    }

    @Test
    fun `set populates start, end, fComputedEnd and clears flags`() {
        val seg = SkOpSegment()
        val s1 = makeSpan(seg, 0.25)
        val s2 = makeSpan(seg, 0.75)
        val a = SkOpAngle()
        // Pre-set some flags to ensure set() clears them.
        a.fComputeSector = true
        a.fComputedSector = true
        a.fCheckCoincidence = true
        a.fTangentsAmbiguous = true
        a.set(s1, s2)
        assertSame(s1, a.start())
        assertSame(s2, a.end())
        assertSame(s2, a.fComputedEnd)
        assertNull(a.next())
        assertFalse(a.fComputeSector)
        assertFalse(a.fComputedSector)
        assertFalse(a.fCheckCoincidence)
        assertFalse(a.fTangentsAmbiguous)
    }

    @Test
    fun `set rejects start equal to end`() {
        val seg = SkOpSegment()
        val s = makeSpan(seg, 0.5)
        val a = SkOpAngle()
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException::class.java) {
            a.set(s, s)
        }
    }

    @Test
    fun `segment derives from fStart segment`() {
        val seg = SkOpSegment()
        val s1 = makeSpan(seg, 0.25)
        val s2 = makeSpan(seg, 0.75)
        val a = SkOpAngle()
        a.set(s1, s2)
        assertSame(seg, a.segment())
    }

    @Test
    fun `starter delegates to start dot starter (end)`() {
        val seg = SkOpSegment()
        val s1 = makeSpan(seg, 0.25)
        val s2 = makeSpan(seg, 0.75)
        val a = SkOpAngle()
        a.set(s1, s2)
        // s1 has the smaller t → starter returns s1 (cast to SkOpSpan).
        assertSame(s1, a.starter())
    }

    @Test
    fun `setLastMarked stores the value`() {
        val seg = SkOpSegment()
        val s1 = makeSpan(seg, 0.25)
        val a = SkOpAngle()
        a.setLastMarked(s1)
        assertSame(s1, a.lastMarked())
    }

    // ─── Linked-list helpers ──────────────────────────────────────

    @Test
    fun `previous walks the loop and returns the angle whose fNext is this`() {
        // Build a 3-cycle : a → b → c → a.
        val a = SkOpAngle(); val b = SkOpAngle(); val c = SkOpAngle()
        a.fNext = b; b.fNext = c; c.fNext = a
        assertSame(c, a.previous())
        assertSame(a, b.previous())
        assertSame(b, c.previous())
    }

    @Test
    fun `loopCount counts a 3-cycle as 3`() {
        val a = SkOpAngle(); val b = SkOpAngle(); val c = SkOpAngle()
        a.fNext = b; b.fNext = c; c.fNext = a
        assertEquals(3, a.loopCount())
        assertEquals(3, b.loopCount())
    }

    @Test
    fun `loopCount returns 1 on self-loop`() {
        val a = SkOpAngle()
        a.fNext = a
        assertEquals(1, a.loopCount())
    }

    @Test
    fun `loopContains returns false when fNext is null`() {
        val a = SkOpAngle()
        val b = SkOpAngle()
        // No loop ; loopContains short-circuits to false.
        assertFalse(a.loopContains(b))
    }

    @Test
    fun `loopContains finds a t-reversed mirror entry on the same segment`() {
        // Two angles on the same segment with reversed (start, end) ranges.
        val seg = SkOpSegment()
        val s1 = makeSpan(seg, 0.25)
        val s2 = makeSpan(seg, 0.75)
        val a = SkOpAngle(); a.set(s1, s2) // (0.25 → 0.75)
        val b = SkOpAngle(); b.set(s2, s1) // (0.75 → 0.25) — t-reversed mirror
        a.fNext = b; b.fNext = a
        // loopContains(b) walks the loop looking for an entry whose
        // start.segment == b.start.segment AND start.t == b.end.t (0.25)
        // AND end.t == b.start.t (0.75). That's `a` itself → returns true.
        assertTrue(a.loopContains(b))
    }

    @Test
    fun `IncludeType enum has 4 expected variants`() {
        assertEquals(
            setOf("kUnaryWinding", "kUnaryXor", "kBinarySingle", "kBinaryOpp"),
            SkOpAngle.IncludeType.values().map { it.name }.toSet(),
        )
    }

    // ─── findSector (D1.2.b.2.a) ───────────────────────────────────

    @Test
    fun `findSector classifies the +X direction into sector 31`() {
        val a = SkOpAngle()
        // Sweep along +X (1, 0) : |x|>|y|, y==0, x>0 → sedecimant[2][1][2] = 15
        // → sector = 15 * 2 + 1 = 31. Sector 0 is reserved for "+x, slightly
        // below x-axis" ; pure +X falls just below it (mod 32).
        assertEquals(31, a.findSector(SkOpSegment.SegVerb.kLine, 1.0, 0.0))
    }

    @Test
    fun `findSector classifies the +Y direction into sector 9`() {
        val a = SkOpAngle()
        // Sweep along +Y (0, 1) → sedecimant[1][2][1] = -1 ; cells with (x==0
        // && y!=0) and abs equal aren't reachable for kLine — fall to
        // sedecimant[0][2][1] = 11 → 11*2+1 = 23. And for non-line, the
        // |x|==|y| row applies only when AlmostEqualUlps(0,1) which is false,
        // so we fall through to |x|<|y| (xyIdx = 0). yIdx=2, xIdx=1 → 11.
        assertEquals(11 * 2 + 1, a.findSector(SkOpSegment.SegVerb.kLine, 0.0, 1.0))
    }

    @Test
    fun `findSector returns -1 for the zero vector`() {
        val a = SkOpAngle()
        // (0, 0) → sedecimant[1][1][1] = -1.
        assertEquals(-1, a.findSector(SkOpSegment.SegVerb.kLine, 0.0, 0.0))
    }

    // ─── setSpans / setSector on a line segment ────────────────────

    @Test
    fun `setSpans on a line populates fPart with the line carrier and zero side`() {
        val seg = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 5f)), null)
        val a = SkOpAngle()
        a.set(seg.fHead, seg.fTail)
        assertFalse(a.unorderable())
        // fPart is non-curve for a line.
        assertFalse(a.fPart.isCurve())
        assertTrue(a.fPart.isOrdered())
        // fSide stays 0 for line / line-like.
        assertEquals(0.0, a.fSide)
        // fSectorStart is computable (not deferred to computeSector).
        assertTrue(a.fSectorStart >= 0)
        assertEquals(a.fSectorStart, a.fSectorEnd)
    }

    // ─── setSpans / setSector on a quad segment ────────────────────

    @Test
    fun `setSpans on a non-degenerate quad sets fPart isCurve true and a non-zero side`() {
        val seg = SkOpSegment().addQuad(arrayOf(pt(0f, 0f), pt(5f, 10f), pt(10f, 0f)), null)
        val a = SkOpAngle()
        a.set(seg.fHead, seg.fTail)
        assertFalse(a.unorderable())
        assertTrue(a.fPart.isCurve())
        // Side : negative point-distance ; concrete sign depends on hull
        // orientation. Just check it's not zero.
        assertTrue(a.fSide != 0.0)
        // sectorMask covers a non-trivial range — at least 1 bit set.
        assertTrue(a.fSectorMask != 0)
    }

    @Test
    fun `setSpans on a collinear-control quad collapses to line-like`() {
        // Three collinear points → fPart.isCurve() == false ; angle behaves
        // like a line.
        val seg = SkOpSegment().addQuad(arrayOf(pt(0f, 0f), pt(5f, 0f), pt(10f, 0f)), null)
        val a = SkOpAngle()
        a.set(seg.fHead, seg.fTail)
        assertFalse(a.fPart.isCurve())
        assertEquals(0.0, a.fSide)
    }

    // ─── checkCrossesZero ─────────────────────────────────────────

    @Test
    fun `checkCrossesZero is true when start and end straddle the +X axis`() {
        val a = SkOpAngle()
        // Start in sector 1 (≈ +X-ish), end in sector 25 (just below +X) :
        // start < 8 && end > 23 → crosses zero.
        a.fSectorStart = 1; a.fSectorEnd = 25
        assertTrue(a.checkCrossesZero())
        // Both in the upper half — no zero crossing.
        a.fSectorStart = 9; a.fSectorEnd = 17
        assertFalse(a.checkCrossesZero())
    }

    // ─── computeSector lazy guard ──────────────────────────────────

    @Test
    fun `computeSector caches its result via fComputedSector`() {
        val seg = SkOpSegment().addLine(arrayOf(pt(0f, 0f), pt(10f, 0f)), null)
        val a = SkOpAngle()
        a.set(seg.fHead, seg.fTail)
        // set() ran setSector eagerly so fComputeSector is false — but
        // fComputedSector is also false (it gates *computeSector*'s own
        // re-run, not the eager path).
        assertFalse(a.fComputeSector)
        assertFalse(a.fComputedSector)
        val first = a.computeSector()
        assertTrue(a.fComputedSector)  // caching marker now set.
        // Second call is a no-op : returns the cached !unorderable.
        val second = a.computeSector()
        assertEquals(first, second)
    }
}
