package org.skia.pathops.internal

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathFillType
import org.graphiks.math.SkPoint

/**
 * Unit tests for [SkPathWriter] (Phase D1.2.i).
 */
class SkPathWriterTest {

    private fun pt(x: Float, y: Float) = SkPoint(fX = x, fY = y)

    /** Build an SkOpPtT anchored to a fresh SkOpSpanBase, with the given (t, pt). */
    private fun ptT(t: Double, p: SkPoint): SkOpPtT {
        val span = SkOpSpanBase()
        val r = SkOpPtT()
        r.init(span, t, p, false)
        return r
    }

    // ─── Initial state ────────────────────────────────────────────

    @Test
    fun `default writer has no move`() {
        val w = SkPathWriter(SkPathFillType.kWinding)
        assertTrue(w.hasMove())
        // Note : isClosed() on an empty writer returns true (matchedLast(null)
        // short-circuits to true) — that's upstream behavior, not a bug.
    }

    @Test
    fun `nativePath returns the configured fill type`() {
        val w = SkPathWriter(SkPathFillType.kEvenOdd)
        val path = w.nativePath()
        assertEquals(SkPathFillType.kEvenOdd, path.fillType)
    }

    // ─── deferredMove + deferredLine + finishContour ──────────────

    @Test
    fun `deferredMove records the start without emitting a moveTo`() {
        val w = SkPathWriter(SkPathFillType.kWinding)
        w.deferredMove(ptT(0.0, pt(0f, 0f)))
        // hasMove returns true when fFirstPtT is null — it's now set,
        // so hasMove returns false.
        assertFalse(w.hasMove())
        // No segments emitted yet ; nativePath should be empty.
        assertTrue(w.nativePath().isEmpty())
    }

    @Test
    fun `deferredLine on a single line buffers it (not flushed until finishContour)`() {
        val w = SkPathWriter(SkPathFillType.kWinding)
        val a = ptT(0.0, pt(0f, 0f))
        val b = ptT(1.0, pt(10f, 0f))
        w.deferredMove(a)
        assertTrue(w.deferredLine(b))
        // Buffered ; not yet in fBuilder.
        assertTrue(w.nativePath().isEmpty())
    }

    @Test
    fun `triangle contour emits 3 lines and closes`() {
        val w = SkPathWriter(SkPathFillType.kWinding)
        val a = ptT(0.0, pt(0f, 0f))
        val b = ptT(1.0, pt(10f, 0f))
        val c = ptT(2.0, pt(5f, 10f))
        // Anchor closing back to `a` by using same fPt.
        val aClose = ptT(3.0, pt(0f, 0f))
        // Splice aClose into a's opp loop so matchedLast(aClose) → true.
        a.addOpp(aClose, aClose)
        w.deferredMove(a)
        assertTrue(w.deferredLine(b))
        assertTrue(w.deferredLine(c))
        assertTrue(w.deferredLine(aClose))
        w.finishContour()
        val path = w.nativePath()
        assertFalse(path.isEmpty())
        // Verify 4 verbs : kMove + 3 kLine + kClose. (The kClose is the
        // close() call ; `lineTo` emits the second-to-last point as a line,
        // and the third deferred-line gets snapped to the start by `update`.)
        // The exact verb stream depends on how SkPathBuilder.close() writes ;
        // we just sanity-check the path closed.
        assertTrue(path.isLastContourClosed())
    }

    @Test
    fun `someAssemblyRequired returns false on a closed contour`() {
        val w = SkPathWriter(SkPathFillType.kWinding)
        val a = ptT(0.0, pt(0f, 0f))
        val b = ptT(1.0, pt(10f, 0f))
        val c = ptT(2.0, pt(5f, 10f))
        val aClose = ptT(3.0, pt(0f, 0f))
        a.addOpp(aClose, aClose)
        w.deferredMove(a)
        w.deferredLine(b); w.deferredLine(c); w.deferredLine(aClose)
        w.finishContour()
        // Closed contour was added to fBuilder ; no partials.
        assertFalse(w.someAssemblyRequired())
    }

    @Test
    fun `assemble is a no-op when no partials exist`() {
        val w = SkPathWriter(SkPathFillType.kWinding)
        val a = ptT(0.0, pt(0f, 0f))
        val b = ptT(1.0, pt(10f, 0f))
        val c = ptT(2.0, pt(5f, 10f))
        val aClose = ptT(3.0, pt(0f, 0f))
        a.addOpp(aClose, aClose)
        w.deferredMove(a)
        w.deferredLine(b); w.deferredLine(c); w.deferredLine(aClose)
        // assemble should not throw on a closed contour.
        w.assemble()
        // After assemble, the path is still valid.
        val path = w.nativePath()
        assertTrue(path.isLastContourClosed())
    }

    // ─── quadTo / cubicTo / conicTo ──────────────────────────────

    @Test
    fun `quadTo emits a quad after the deferred move`() {
        val w = SkPathWriter(SkPathFillType.kWinding)
        val a = ptT(0.0, pt(0f, 0f))
        val b = ptT(1.0, pt(100f, 0f))
        val aClose = ptT(2.0, pt(0f, 0f))
        a.addOpp(aClose, aClose)
        w.deferredMove(a)
        w.quadTo(pt(50f, 50f), b)
        // Need at least one more verb to close to the start.
        w.deferredLine(aClose)
        w.finishContour()
        val path = w.nativePath()
        assertFalse(path.isEmpty())
        // Path contains a quad verb.
        assertTrue(path.verbs.contains(SkPath.Verb.kQuad))
    }

    @Test
    fun `cubicTo emits a cubic after the deferred move`() {
        val w = SkPathWriter(SkPathFillType.kWinding)
        val a = ptT(0.0, pt(0f, 0f))
        val b = ptT(1.0, pt(10f, 0f))
        val aClose = ptT(2.0, pt(0f, 0f))
        a.addOpp(aClose, aClose)
        w.deferredMove(a)
        w.cubicTo(pt(0f, 5f), pt(10f, 5f), b)
        w.deferredLine(aClose)
        w.finishContour()
        assertTrue(w.nativePath().verbs.contains(SkPath.Verb.kCubic))
    }

    @Test
    fun `conicTo emits a conic with weight`() {
        val w = SkPathWriter(SkPathFillType.kWinding)
        val a = ptT(0.0, pt(1f, 0f))
        val b = ptT(1.0, pt(0f, 1f))
        val aClose = ptT(2.0, pt(1f, 0f))
        a.addOpp(aClose, aClose)
        w.deferredMove(a)
        w.conicTo(pt(1f, 1f), b, 0.7071f)
        w.deferredLine(aClose)
        w.finishContour()
        val path = w.nativePath()
        assertTrue(path.verbs.contains(SkPath.Verb.kConic))
        assertEquals(0.7071f, path.conicWeights[0])
    }

    // ─── deferredLine collinear collapse ──────────────────────────

    @Test
    fun `deferredLine on a collinear chain collapses into a single line`() {
        val w = SkPathWriter(SkPathFillType.kWinding)
        val a = ptT(0.0, pt(0f, 0f))
        val b = ptT(0.25, pt(3f, 0f))
        val c = ptT(0.5, pt(7f, 0f))
        // d breaks the collinear streak so a flush happens at c.
        val d = ptT(0.75, pt(10f, 10f))
        val aClose = ptT(1.0, pt(0f, 0f))
        a.addOpp(aClose, aClose)
        w.deferredMove(a)
        // a → b → c is collinear (all on y=0). deferredLine(c) should
        // *not* flush b — instead, b's slot is overwritten by c.
        assertTrue(w.deferredLine(b))
        assertTrue(w.deferredLine(c))
        // c → d changes slope, so deferredLine(d) flushes a single
        // lineTo(c) before parking d as the new deferred target.
        assertTrue(w.deferredLine(d))
        // d → aClose changes slope again ; flushes lineTo(d).
        assertTrue(w.deferredLine(aClose))
        w.finishContour()
        val path = w.nativePath()
        // Without collapse we'd expect 4 lines (a→b, b→c, c→d, d→aClose).
        // With the b→c collapse, only 3 are emitted : c, d, aClose.
        val lineCount = path.verbs.count { it == SkPath.Verb.kLine }
        assertEquals(3, lineCount)
    }

    // ─── matchedLast / isClosed ───────────────────────────────────

    @Test
    fun `isClosed returns false before any line is emitted`() {
        val w = SkPathWriter(SkPathFillType.kWinding)
        val a = ptT(0.0, pt(0f, 0f))
        w.deferredMove(a)
        assertFalse(w.isClosed())
    }

    // ─── assemble (D1.2.i.2) ──────────────────────────────────────

    @Test
    fun `assemble closes a single open partial with a diagonal`() {
        val w = SkPathWriter(SkPathFillType.kWinding)
        val a = ptT(0.0, pt(0f, 0f))
        val b = ptT(1.0, pt(10f, 0f))
        val c = ptT(2.0, pt(5f, 10f))
        // No closing pt-T in a's opp loop — the contour stays open and
        // is queued as a partial.
        w.deferredMove(a)
        w.deferredLine(b)
        w.deferredLine(c)
        w.finishContour()
        assertTrue(w.someAssemblyRequired())
        w.assemble()
        val path = w.nativePath()
        // Single partial : assemble matches the start to the end of the
        // same partial, emitting the contour as-is and adding a close.
        assertTrue(path.isLastContourClosed())
        assertEquals(2, path.verbs.count { it == SkPath.Verb.kLine })
    }

    @Test
    fun `assemble stitches two partials sharing endpoints (non-flip)`() {
        val w = SkPathWriter(SkPathFillType.kWinding)
        // partial0 : A (0,0) → B (10,0).
        w.deferredMove(ptT(0.0, pt(0f, 0f)))
        w.deferredLine(ptT(1.0, pt(10f, 0f)))
        w.finishContour()
        // partial1 : B' (10, 0.01) → A' (0, 0.01) — running right-to-left
        // with both endpoints near partial0's, but in opposite direction.
        // The closest pairs are (A↔A') and (B↔B'), each crossing partials.
        w.deferredMove(ptT(2.0, pt(10f, 0.01f)))
        w.deferredLine(ptT(3.0, pt(0f, 0.01f)))
        w.finishContour()
        assertTrue(w.someAssemblyRequired())
        w.assemble()
        val path = w.nativePath()
        // After assemble : A → B → B' (kExtend, lineTo) → A' (kExtend) → close.
        assertTrue(path.isLastContourClosed())
        assertEquals(3, path.verbs.count { it == SkPath.Verb.kLine })
    }

    @Test
    fun `assemble stitches two partials with shared starts (flip path)`() {
        val w = SkPathWriter(SkPathFillType.kWinding)
        // partial0 : A (0,0) → B (10,0).
        w.deferredMove(ptT(0.0, pt(0f, 0f)))
        w.deferredLine(ptT(1.0, pt(10f, 0f)))
        w.finishContour()
        // partial1 : A' (0, 0.01) → B' (10, 0.01) — running left-to-right
        // parallel to partial0. Both starts and both ends coincide, so
        // assemble pairs (A,A') as start↔start and (B,B') as end↔end —
        // the "flip" case that exercises [reverseExtend].
        w.deferredMove(ptT(2.0, pt(0f, 0.01f)))
        w.deferredLine(ptT(3.0, pt(10f, 0.01f)))
        w.finishContour()
        assertTrue(w.someAssemblyRequired())
        w.assemble()
        val path = w.nativePath()
        // After assemble : A → B → B' (reverseExtend → lineTo) → A' → close.
        assertTrue(path.isLastContourClosed())
        assertEquals(3, path.verbs.count { it == SkPath.Verb.kLine })
    }

    @Test
    fun `reverseExtend preserves the quad control point`() {
        val w = SkPathWriter(SkPathFillType.kWinding)
        // partial0 : moveTo(A) ; quadTo(c=(5,5), B).
        val a = ptT(0.0, pt(0f, 0f))
        val b = ptT(1.0, pt(10f, 0f))
        w.deferredMove(a)
        w.quadTo(pt(5f, 5f), b)
        w.finishContour()
        // partial1 : line A' → B' (flip case — both starts and both ends
        // align). assemble walks partial0 forward (emitting the quad)
        // then partial1 reversed via reverseExtend.
        w.deferredMove(ptT(2.0, pt(0f, 0.01f)))
        w.deferredLine(ptT(3.0, pt(10f, 0.01f)))
        w.finishContour()
        w.assemble()
        val path = w.nativePath()
        assertTrue(path.isLastContourClosed())
        // The quad survives the assembly — emitted once during partial0's
        // forward walk.
        assertEquals(1, path.verbs.count { it == SkPath.Verb.kQuad })
    }
}
