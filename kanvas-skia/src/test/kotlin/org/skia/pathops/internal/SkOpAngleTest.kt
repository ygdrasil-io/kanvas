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
}
