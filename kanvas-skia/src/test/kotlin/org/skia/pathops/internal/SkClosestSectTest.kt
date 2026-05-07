package org.skia.pathops.internal

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for [SkClosestRecord] and [SkClosestSect] (Phase D1.1.e.2.c.4).
 */
class SkClosestSectTest {

    private fun spanWithCurve(curve: SkTCurve): SkTSpan {
        val s = SkTSpan(curve)
        s.init(curve)
        return s
    }

    // ─── SkClosestRecord ───────────────────────────────────────────

    @Test
    fun `findEnd records a match when corner points are approximately equal`() {
        val q1 = SkTQuad(SkDQuad(arrayOf(SkDPoint(0.0, 0.0), SkDPoint(50.0, 100.0), SkDPoint(100.0, 0.0))))
        val q2 = SkTQuad(SkDQuad(arrayOf(SkDPoint(100.0, 0.0), SkDPoint(50.0, -100.0), SkDPoint(200.0, 0.0))))
        val span1 = spanWithCurve(q1)
        val span2 = spanWithCurve(q2)
        val rec = SkClosestRecord()
        // span1[2] == (100, 0) ; span2[0] == (100, 0) → match.
        rec.findEnd(span1, span2, c1Index = 2, c2Index = 0)
        assertEquals(2, rec.fC1Index)
        assertEquals(0, rec.fC2Index)
        assertTrue(rec.fClosest < Float.MAX_VALUE.toDouble())
    }

    @Test
    fun `findEnd does nothing when corner points don't approximately match`() {
        val q1 = SkTQuad(SkDQuad(arrayOf(SkDPoint(0.0, 0.0), SkDPoint(5.0, 5.0), SkDPoint(10.0, 0.0))))
        val q2 = SkTQuad(SkDQuad(arrayOf(SkDPoint(100.0, 0.0), SkDPoint(105.0, 5.0), SkDPoint(110.0, 0.0))))
        val rec = SkClosestRecord()
        rec.findEnd(spanWithCurve(q1), spanWithCurve(q2), 0, 0)
        assertEquals(Float.MAX_VALUE.toDouble(), rec.fClosest)
        assertEquals(-1, rec.fC1Index)
    }

    @Test
    fun `compareTo orders by fClosest ascending`() {
        val a = SkClosestRecord(); a.fClosest = 1.0
        val b = SkClosestRecord(); b.fClosest = 2.0
        assertTrue(a < b)
    }

    @Test
    fun `reset clears state to defaults`() {
        val r = SkClosestRecord()
        r.fClosest = 0.5; r.fC1Index = 7
        r.reset()
        assertEquals(Float.MAX_VALUE.toDouble(), r.fClosest)
        assertEquals(-1, r.fC1Index)
    }

    // ─── SkClosestSect ─────────────────────────────────────────────

    @Test
    fun `find on disjoint spans returns false (no record)`() {
        val q1 = SkTQuad(SkDQuad(arrayOf(SkDPoint(0.0, 0.0), SkDPoint(5.0, 5.0), SkDPoint(10.0, 0.0))))
        val q2 = SkTQuad(SkDQuad(arrayOf(SkDPoint(100.0, 0.0), SkDPoint(105.0, 5.0), SkDPoint(110.0, 0.0))))
        val cs = SkClosestSect()
        assertFalse(cs.find(spanWithCurve(q1), spanWithCurve(q2)))
    }

    @Test
    fun `find on shared-endpoint spans promotes to a slot`() {
        val q1 = SkTQuad(SkDQuad(arrayOf(SkDPoint(0.0, 0.0), SkDPoint(50.0, 100.0), SkDPoint(100.0, 0.0))))
        val q2 = SkTQuad(SkDQuad(arrayOf(SkDPoint(100.0, 0.0), SkDPoint(150.0, 100.0), SkDPoint(200.0, 0.0))))
        val cs = SkClosestSect()
        assertTrue(cs.find(spanWithCurve(q1), spanWithCurve(q2)))
    }

    @Test
    fun `finish writes recorded matches to the intersections`() {
        val q1 = SkTQuad(SkDQuad(arrayOf(SkDPoint(0.0, 0.0), SkDPoint(50.0, 100.0), SkDPoint(100.0, 0.0))))
        val q2 = SkTQuad(SkDQuad(arrayOf(SkDPoint(100.0, 0.0), SkDPoint(150.0, 100.0), SkDPoint(200.0, 0.0))))
        val cs = SkClosestSect()
        cs.find(spanWithCurve(q1), spanWithCurve(q2))
        val ix = SkIntersections()
        ix.setMax(4)
        cs.finish(ix)
        assertEquals(1, ix.used())
    }
}
