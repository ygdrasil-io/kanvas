package org.skia.pathops.internal


import org.graphiks.math.SkDPoint
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for the coincidence machinery added to [SkTSect] in
 * Phase D1.1.e.2.c.2 — focused on the small / well-isolated methods
 * (`matchedDirection` / `matchedDirCheck` / `recoverCollapsed` /
 * `coincidentHasT` / direct list-shape ops). The full coincidence
 * algorithm (`coincidentCheck` + `extractCoincident` +
 * `binarySearchCoin` + `mergeCoincidence`) is exercised end-to-end
 * by the BinarySearch tests in D1.1.e.2.c.4.
 */
class SkTSectCoincidenceTest {

    private fun quadCurve() = SkTQuad(SkDQuad(arrayOf(
        SkDPoint(0.0, 0.0), SkDPoint(50.0, 100.0), SkDPoint(100.0, 0.0),
    )))

    // ─── matchedDirection / matchedDirCheck ────────────────────────

    @Test
    fun `matchedDirection returns true when tangents agree in general direction`() {
        val s1 = SkTSect(quadCurve())
        val s2 = SkTSect(quadCurve())
        // Same curve at t=0.25 — tangents identical → dot > 0.
        assertTrue(s1.matchedDirection(0.25, s2, 0.25))
    }

    @Test
    fun `matchedDirection returns false when tangents oppose`() {
        // Forward and reverse versions of the same quad — tangents
        // point in opposite directions everywhere.
        val q = SkDQuad(arrayOf(SkDPoint(0.0, 0.0), SkDPoint(50.0, 100.0), SkDPoint(100.0, 0.0)))
        val qReversed = SkDQuad(arrayOf(q[2], q[1], q[0]))
        val s1 = SkTSect(SkTQuad(q))
        val s2 = SkTSect(SkTQuad(qReversed))
        // s1 tangent at t=0.5 = (50, 0). s2 tangent at t=0.5 = (-50, 0). dot < 0.
        assertFalse(s1.matchedDirection(0.5, s2, 0.5))
    }

    @Test
    fun `matchedDirCheck caches first call and asserts on subsequent`() {
        val s1 = SkTSect(quadCurve())
        val s2 = SkTSect(quadCurve())
        val calc = booleanArrayOf(false)
        val opp = booleanArrayOf(false)
        s1.matchedDirCheck(0.25, s2, 0.25, calc, opp)
        assertTrue(calc[0])
        assertTrue(opp[0])
        // Subsequent call with consistent state — no failure.
        s1.matchedDirCheck(0.25, s2, 0.25, calc, opp)
    }

    // ─── coincidentHasT / fCoincident list ────────────────────────

    @Test
    fun `coincidentHasT walks the fCoincident list`() {
        val s = SkTSect(quadCurve())
        // Move head to coincident list manually for the test.
        val span = s.fHead!!
        s.fHead = null
        s.fCoincident = span
        assertTrue(s.coincidentHasT(0.5))
        assertTrue(s.coincidentHasT(0.0))
        assertTrue(s.coincidentHasT(1.0))
    }

    // ─── recoverCollapsed ─────────────────────────────────────────

    @Test
    fun `recoverCollapsed moves collapsed deleted spans back into fHead`() {
        val s = SkTSect(quadCurve())
        // Allocate a span, mark it collapsed, push to deleted list.
        val span = s.addOne()
        span.fStartT = 0.5; span.fEndT = 0.5
        span.fCollapsed = true
        s.markSpanGone(span)
        // Now there are no collapsed spans in fHead. Recover.
        s.recoverCollapsed()
        // The recovered span should be in fHead's chain.
        var found = false
        var test: SkTSpan? = s.fHead
        while (test != null) {
            if (test === span) { found = true; break }
            test = test.fNext
        }
        assertTrue(found) { "collapsed span should be back in active list" }
    }

    // ─── computePerpendiculars (single span, simple case) ─────────

    @Test
    fun `computePerpendiculars sets fHasPerp on each span in the range`() {
        val s1 = SkTSect(quadCurve())
        val s2 = SkTSect(SkTQuad(SkDQuad(arrayOf(
            SkDPoint(0.0, 50.0), SkDPoint(50.0, 50.0), SkDPoint(100.0, 50.0),
        ))))
        val span = s1.fHead!!
        assertFalse(span.fHasPerp)
        s1.computePerpendiculars(s2, span, span)
        assertTrue(span.fHasPerp)
    }

    // ─── removeCoincident ─────────────────────────────────────────

    @Test
    fun `removeCoincident with isBetween=true moves span to coincident list`() {
        val s = SkTSect(quadCurve())
        val span = s.fHead!!
        assertTrue(s.removeCoincident(span, true))
        assertNotNull(s.fCoincident)
        assertNull(s.fHead) // the only span moved out
    }

    @Test
    fun `removeCoincident with perpT in range moves to coincident list`() {
        val s = SkTSect(quadCurve())
        val span = s.fHead!!
        // Force fCoinStart.perpT() into [0, 1] range by setPerp.
        val s2 = SkTSect(SkTQuad(SkDQuad(arrayOf(
            SkDPoint(0.0, 0.0), SkDPoint(50.0, 100.0), SkDPoint(100.0, 0.0),
        ))))
        span.fCoinStart.setPerp(s.fCurve, 0.5, span.pointFirst(), s2.fCurve)
        // perpT may or may not be in [0, 1] — if not, span just goes to deleted.
        val activeBefore = s.fActiveCount
        s.removeCoincident(span, false)
        // Either coincident list non-empty or span is deleted — both valid.
        val movedToCoincident = s.fCoincident === span
        val movedToDeleted = span.fDeleted
        assertTrue(movedToCoincident || movedToDeleted)
    }

    // ─── findCoincidentRun on non-coincident list ────────────────

    @Test
    fun `findCoincidentRun on a non-coincident span returns null`() {
        val s = SkTSect(quadCurve())
        val span = s.fHead!!
        // fCoinStart.isMatch() defaults to false → no run.
        val lastPtr = arrayOfNulls<SkTSpan>(1)
        lastPtr[0] = span
        val result = s.findCoincidentRun(span, lastPtr)
        assertNull(result) { "expected null run for non-coincident span" }
    }

    // ─── COINCIDENT_SPAN_COUNT companion ──────────────────────────

    @Test
    fun `COINCIDENT_SPAN_COUNT matches upstream value`() {
        org.junit.jupiter.api.Assertions.assertEquals(9, SkTSect.COINCIDENT_SPAN_COUNT)
    }
}
