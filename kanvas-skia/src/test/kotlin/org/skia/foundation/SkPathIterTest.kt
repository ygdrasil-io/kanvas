package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Coverage for the R2.9 verb iterators — [SkPath.Iter], [SkPath.RawIter],
 * and the [SkPath.Verb] enum they emit. Mirrors the behaviours
 * required by the upstream `SkPath::Iter` / `SkPath::RawIter` contract
 * (`include/core/SkPath.h:774-1018`).
 */
class SkPathIterTest {

    // --- Iter --------------------------------------------------------------

    @Test
    fun `Iter walks a simple 3-line open path producing the canonical verb sequence`() {
        // Triangle as an open polyline (no close).
        val path = SkPathBuilder()
            .moveTo(0f, 0f)
            .lineTo(10f, 0f)
            .lineTo(10f, 10f)
            .lineTo(0f, 0f)
            .detach()
        val it = SkPath.Iter(path)
        val verbs = drainVerbs(it)
        assertEquals(
            listOf(
                SkPath.Verb.kMoveVerb,
                SkPath.Verb.kLineVerb,
                SkPath.Verb.kLineVerb,
                SkPath.Verb.kLineVerb,
                SkPath.Verb.kDoneVerb,
            ),
            verbs,
        )
    }

    @Test
    fun `Iter writes line endpoints with pts0 = last pen position and pts1 = end`() {
        val path = SkPathBuilder()
            .moveTo(1f, 2f)
            .lineTo(3f, 4f)
            .detach()
        val it = SkPath.Iter(path)
        val pts = FloatArray(8)

        assertEquals(SkPath.Verb.kMoveVerb, it.next(pts))
        assertEquals(1f, pts[0]); assertEquals(2f, pts[1])

        assertEquals(SkPath.Verb.kLineVerb, it.next(pts))
        // pts[0..1] = last pen, pts[2..3] = end
        assertEquals(1f, pts[0]); assertEquals(2f, pts[1])
        assertEquals(3f, pts[2]); assertEquals(4f, pts[3])
        assertFalse(it.isCloseLine())

        assertEquals(SkPath.Verb.kDoneVerb, it.next(pts))
    }

    @Test
    fun `forceClose=true on an unclosed triangle injects line + close before kDone`() {
        // Open triangle: move + 2 lines, no close, last point != move.
        val path = SkPathBuilder()
            .moveTo(0f, 0f)
            .lineTo(10f, 0f)
            .lineTo(10f, 10f)
            .detach()
        val it = SkPath.Iter(path, forceClose = true)
        val verbs = drainVerbs(it)
        assertEquals(
            listOf(
                SkPath.Verb.kMoveVerb,
                SkPath.Verb.kLineVerb,
                SkPath.Verb.kLineVerb,
                SkPath.Verb.kLineVerb,   // synthetic close-line back to (0,0)
                SkPath.Verb.kCloseVerb,
                SkPath.Verb.kDoneVerb,
            ),
            verbs,
        )
    }

    @Test
    fun `forceClose synthetic line endpoints close the contour back to the move target`() {
        val path = SkPathBuilder()
            .moveTo(0f, 0f)
            .lineTo(10f, 0f)
            .lineTo(10f, 10f)
            .detach()
        val iter = SkPath.Iter(path, forceClose = true)
        val pts = FloatArray(8)
        // Walk past move + 2 explicit lines.
        repeat(3) { iter.next(pts) }
        // Synthetic line back to (0, 0).
        assertEquals(SkPath.Verb.kLineVerb, iter.next(pts))
        assertEquals(10f, pts[0]); assertEquals(10f, pts[1])
        assertEquals(0f, pts[2]);  assertEquals(0f, pts[3])
        assertTrue(iter.isCloseLine())
        // Then the synthetic close.
        assertEquals(SkPath.Verb.kCloseVerb, iter.next(pts))
        assertEquals(0f, pts[0]); assertEquals(0f, pts[1])
        // Done.
        assertEquals(SkPath.Verb.kDoneVerb, iter.next(pts))
    }

    @Test
    fun `forceClose on an already-closed contour does not duplicate the close`() {
        val path = SkPathBuilder()
            .moveTo(0f, 0f)
            .lineTo(10f, 0f)
            .close()
            .detach()
        val it = SkPath.Iter(path, forceClose = true)
        val verbs = drainVerbs(it)
        // Walk: move, line, then explicit kClose — endpoints coincide
        // so autoClose emits a bare kCloseVerb (no synthetic line).
        assertEquals(
            listOf(
                SkPath.Verb.kMoveVerb,
                SkPath.Verb.kLineVerb,
                SkPath.Verb.kLineVerb,   // synthetic line: (10,0) → (0,0)
                SkPath.Verb.kCloseVerb,
                SkPath.Verb.kDoneVerb,
            ),
            verbs,
        )
    }

    @Test
    fun `Iter Kotlin-friendly next returns null when stream exhausted`() {
        val path = SkPathBuilder().moveTo(0f, 0f).lineTo(1f, 1f).detach()
        val it = SkPath.Iter(path)
        assertNotNull(it.next())   // move
        assertNotNull(it.next())   // line
        assertNull(it.next())      // done → null
    }

    @Test
    fun `Iter Kotlin-friendly next returns correct point counts per verb`() {
        // Closed contour ending at the move target so the explicit
        // kClose maps straight to a kCloseVerb (no synthetic close-line).
        val path = SkPathBuilder()
            .moveTo(0f, 0f)
            .lineTo(1f, 0f)
            .quadTo(2f, 0f, 2f, 1f)
            .cubicTo(3f, 1f, 3f, 2f, 0f, 0f)
            .close()
            .detach()
        val it = SkPath.Iter(path)
        assertEquals(1, it.next()!!.second.size)  // move
        assertEquals(2, it.next()!!.second.size)  // line
        assertEquals(3, it.next()!!.second.size)  // quad
        assertEquals(4, it.next()!!.second.size)  // cubic
        assertEquals(1, it.next()!!.second.size)  // close (endpoints coincide)
        assertNull(it.next())                     // done
    }

    @Test
    fun `Iter isClosedContour reflects explicit close and forceClose`() {
        val open = SkPathBuilder().moveTo(0f, 0f).lineTo(1f, 0f).detach()
        assertFalse(SkPath.Iter(open, forceClose = false).isClosedContour())
        assertTrue(SkPath.Iter(open, forceClose = true).isClosedContour())

        val closed = SkPathBuilder().moveTo(0f, 0f).lineTo(1f, 0f).close().detach()
        assertTrue(SkPath.Iter(closed, forceClose = false).isClosedContour())
    }

    @Test
    fun `Iter setPath rearms the cursor for a new path`() {
        // pathB needs at least one geometry verb after the move —
        // upstream `SkPath::Iter::next` treats a trailing kMove with
        // nothing after as kDone_Verb (`SkPath.cpp:513`).
        val pathA = SkPathBuilder().moveTo(0f, 0f).lineTo(1f, 0f).detach()
        val pathB = SkPathBuilder().moveTo(2f, 2f).lineTo(3f, 3f).detach()
        val it = SkPath.Iter(pathA)
        // Drain pathA.
        val pts = FloatArray(8)
        while (it.next(pts) != SkPath.Verb.kDoneVerb) { /* drain */ }
        it.setPath(pathB, forceClose = false)
        assertEquals(SkPath.Verb.kMoveVerb, it.next(pts))
        assertEquals(2f, pts[0]); assertEquals(2f, pts[1])
        assertEquals(SkPath.Verb.kLineVerb, it.next(pts))
        assertEquals(SkPath.Verb.kDoneVerb, it.next(pts))
    }

    @Test
    fun `Iter on an empty path returns kDoneVerb immediately`() {
        val empty = SkPathBuilder().detach()
        val it = SkPath.Iter(empty)
        val pts = FloatArray(8)
        assertEquals(SkPath.Verb.kDoneVerb, it.next(pts))
        assertNull(it.next())
    }

    // --- RawIter -----------------------------------------------------------

    @Test
    fun `RawIter walks a conic-bearing path and exposes conic weights`() {
        // Quarter-circle conic with weight √2/2.
        val w = 0.707106781f
        val path = SkPathBuilder()
            .moveTo(0f, 1f)
            .conicTo(1f, 1f, 1f, 0f, w)
            .detach()
        val it = SkPath.RawIter(path)
        val pts = FloatArray(8)

        assertEquals(SkPath.Verb.kMoveVerb, it.next(pts))
        assertEquals(SkPath.Verb.kConicVerb, it.next(pts))
        // Control point at (1, 1), end at (1, 0).
        assertEquals(0f, pts[0]); assertEquals(1f, pts[1])
        assertEquals(1f, pts[2]); assertEquals(1f, pts[3])
        assertEquals(1f, pts[4]); assertEquals(0f, pts[5])
        assertEquals(w, it.conicWeight(), 1e-6f)

        assertEquals(SkPath.Verb.kDoneVerb, it.next(pts))
    }

    @Test
    fun `RawIter emits the literal verb stream without synthetic close insertion`() {
        // Open path: no kClose stored, RawIter must not invent one.
        val path = SkPathBuilder()
            .moveTo(0f, 0f)
            .lineTo(10f, 0f)
            .lineTo(10f, 10f)
            .detach()
        val it = SkPath.RawIter(path)
        val verbs = drainRawVerbs(it)
        assertEquals(
            listOf(
                SkPath.Verb.kMoveVerb,
                SkPath.Verb.kLineVerb,
                SkPath.Verb.kLineVerb,
                SkPath.Verb.kDoneVerb,
            ),
            verbs,
        )
    }

    @Test
    fun `RawIter peek returns the next verb without advancing`() {
        val path = SkPathBuilder().moveTo(0f, 0f).lineTo(1f, 0f).detach()
        val it = SkPath.RawIter(path)
        assertEquals(SkPath.Verb.kMoveVerb, it.peek())
        // peek again — still kMoveVerb (no advance).
        assertEquals(SkPath.Verb.kMoveVerb, it.peek())
        it.next(FloatArray(8))
        assertEquals(SkPath.Verb.kLineVerb, it.peek())
        it.next(FloatArray(8))
        assertEquals(SkPath.Verb.kDoneVerb, it.peek())
    }

    @Test
    fun `RawIter setPath rearms the cursor for a new path`() {
        val pathA = SkPathBuilder().moveTo(0f, 0f).lineTo(1f, 0f).detach()
        val pathB = SkPathBuilder().moveTo(5f, 5f).detach()
        val it = SkPath.RawIter(pathA)
        val pts = FloatArray(8)
        while (it.next(pts) != SkPath.Verb.kDoneVerb) { /* drain */ }
        it.setPath(pathB)
        assertEquals(SkPath.Verb.kMoveVerb, it.next(pts))
        assertEquals(5f, pts[0]); assertEquals(5f, pts[1])
        assertEquals(SkPath.Verb.kDoneVerb, it.next(pts))
    }

    // --- helpers -----------------------------------------------------------

    private fun drainVerbs(it: SkPath.Iter): List<SkPath.Verb> {
        val out = mutableListOf<SkPath.Verb>()
        val pts = FloatArray(8)
        var v: SkPath.Verb
        do {
            v = it.next(pts); out += v
        } while (v != SkPath.Verb.kDoneVerb)
        return out
    }

    private fun drainRawVerbs(it: SkPath.RawIter): List<SkPath.Verb> {
        val out = mutableListOf<SkPath.Verb>()
        val pts = FloatArray(8)
        var v: SkPath.Verb
        do {
            v = it.next(pts); out += v
        } while (v != SkPath.Verb.kDoneVerb)
        return out
    }
}
