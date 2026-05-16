/*
 * Copyright 2026 The kanvas-skia authors.
 *
 * Mirrors Skia's `class SkTSpan` from `src/pathops/SkPathOpsTSect.h`
 * + the methods in `src/pathops/SkPathOpsTSect.cpp`.
 *
 * Phase D1.1.e.2.b — per-span state used by [SkTSect] (D1.1.e.2.c)
 * during Bézier-clipping intersection. A span owns a sub-curve over
 * a parametric `[fStartT, fEndT]` interval, tracks which spans of
 * the opposing curve might overlap it (`fBounded` linked list), and
 * carries perpendicular-coincidence state at the two endpoints.
 *
 * The Skia version uses an `SkArenaAlloc` for `SkTSpanBounded`
 * allocation. We use Kotlin GC instead — `SkTSpanBounded` is a plain
 * class with `next` linkage.
 */
package org.skia.pathops.internal


import org.graphiks.math.SkDPoint
import org.graphiks.math.approximately_zero_when_compared_to
import org.graphiks.math.between
import org.graphiks.math.precisely_zero_when_compared_to
import kotlin.math.abs
import kotlin.math.max

/**
 * Singly-linked list node referencing an opposing span that might
 * overlap the owning [SkTSpan]. Mirrors `struct SkTSpanBounded`.
 */
internal class SkTSpanBounded(var bounded: SkTSpan, var next: SkTSpanBounded? = null)

internal class SkTSpan(curve: SkTCurve) {

    /** Sub-curve over the `[fStartT, fEndT]` interval. */
    val fPart: SkTCurve = curve.make()

    /** Per-endpoint perpendicular-coincidence state. */
    val fCoinStart: SkTCoincident = SkTCoincident()
    val fCoinEnd: SkTCoincident = SkTCoincident()

    /** Head of the singly-linked list of opposing spans that may overlap. */
    var fBounded: SkTSpanBounded? = null

    /** Doubly-linked list of sibling spans (sorted by `fStartT`). */
    var fPrev: SkTSpan? = null
    var fNext: SkTSpan? = null

    /** Tight bounds of [fPart]. */
    var fBounds: SkDRect = SkDRect()

    /** Parametric interval covered by this span on the parent curve. */
    var fStartT: Double = 0.0
    var fEndT: Double = 1.0

    /** `max(fBounds.width, fBounds.height)` — used for size sorting. */
    var fBoundsMax: Double = 0.0

    /** True if all controls of [fPart] are approximately coincident. */
    var fCollapsed: Boolean = false

    /** True if the `fCoinStart`/`fCoinEnd` perp data has been computed. */
    var fHasPerp: Boolean = false

    /** True once [fPart]'s hull is detected as approximately linear. */
    var fIsLinear: Boolean = false

    /** True once [fPart]'s controls are confirmed to lie on the chord. */
    var fIsLine: Boolean = false

    /** True once removed from the active list (logically deleted). */
    var fDeleted: Boolean = false

    fun startT(): Double = fStartT
    fun endT(): Double = fEndT
    fun part(): SkTCurve = fPart
    fun pointCount(): Int = fPart.pointCount()
    fun pointFirst(): SkDPoint = fPart[0]
    fun pointLast(): SkDPoint = fPart[fPart.pointLast()]
    fun next(): SkTSpan? = fNext
    fun isBounded(): Boolean = fBounded != null

    // ─── Init / bounds reset ────────────────────────────────────────

    /** Mirrors `SkTSpan::init`. */
    fun init(c: SkTCurve) {
        fPrev = null; fNext = null
        fStartT = 0.0; fEndT = 1.0
        fBounded = null
        resetBounds(c)
    }

    /**
     * Recompute [fPart] (sub-curve over `[fStartT, fEndT]`), bounds,
     * collapsed flag, and reset the coincidence + perp state.
     * Mirrors `SkTSpan::initBounds`.
     *
     * Returns false if the bounds came out invalid (e.g. NaN inputs).
     */
    fun initBounds(c: SkTCurve): Boolean {
        if (fStartT.isNaN() || fEndT.isNaN()) return false
        c.subDivide(fStartT, fEndT, fPart)
        fPart.setBounds(fBounds)
        fCoinStart.init()
        fCoinEnd.init()
        fBoundsMax = max(fBounds.width(), fBounds.height())
        fCollapsed = fPart.collapsed()
        fHasPerp = false
        fDeleted = false
        return fBounds.valid()
    }

    /** Mirrors `SkTSpan::resetBounds` — clear linear flags + reinit bounds. */
    fun resetBounds(curve: SkTCurve) {
        fIsLinear = false; fIsLine = false
        initBounds(curve)
    }

    /** Mirrors `SkTSpan::reset` — clears the bounded list (used by SkTSect). */
    fun reset() { fBounded = null }

    // ─── Coincidence state ──────────────────────────────────────────

    /** Mirrors `SkTSpan::markCoincident`. */
    fun markCoincident() {
        fCoinStart.markCoincident()
        fCoinEnd.markCoincident()
    }

    // ─── Bounded-list operations ────────────────────────────────────

    /** Prepend [span] to the head of [fBounded]. Mirrors `SkTSpan::addBounded`. */
    fun addBounded(span: SkTSpan) {
        fBounded = SkTSpanBounded(span, fBounded)
    }

    /**
     * Drop [opp] from [fBounded]. Returns true if the bounded list is
     * now empty (and the caller should consider deleting the span).
     * Mirrors `SkTSpan::removeBounded`.
     */
    fun removeBounded(opp: SkTSpan): Boolean {
        if (fHasPerp) {
            var foundStart = false
            var foundEnd = false
            var b = fBounded
            while (b != null) {
                val test = b.bounded
                if (opp !== test) {
                    foundStart = foundStart || between(test.fStartT, fCoinStart.perpT(), test.fEndT)
                    foundEnd = foundEnd || between(test.fStartT, fCoinEnd.perpT(), test.fEndT)
                }
                b = b.next
            }
            if (!foundStart || !foundEnd) {
                fHasPerp = false
                fCoinStart.init()
                fCoinEnd.init()
            }
        }
        var prev: SkTSpanBounded? = null
        var bounded = fBounded
        while (bounded != null) {
            val nextNode = bounded.next
            if (opp === bounded.bounded) {
                if (prev != null) {
                    prev.next = nextNode
                    return false
                } else {
                    fBounded = nextNode
                    return fBounded == null
                }
            }
            prev = bounded
            bounded = nextNode
        }
        return false
    }

    /**
     * Remove this span from every opposing span's bounded list.
     * Returns true if any opposing span's list became empty (callers
     * use this to decide whether to delete those spans).
     * Mirrors `SkTSpan::removeAllBounded`.
     */
    fun removeAllBounded(): Boolean {
        var deleteSpan = false
        var b = fBounded
        while (b != null) {
            deleteSpan = b.bounded.removeBounded(this) || deleteSpan
            b = b.next
        }
        return deleteSpan
    }

    /** Find the bounded entry pointing to [opp], or null. */
    fun findOppSpan(opp: SkTSpan): SkTSpan? {
        var b = fBounded
        while (b != null) {
            if (opp === b.bounded) return b.bounded
            b = b.next
        }
        return null
    }

    /** True if any bounded entry's t-range contains [t]. */
    fun hasOppT(t: Double): Boolean = oppT(t) != null

    /** Find the bounded entry whose t-range contains [t], asserting non-null. */
    fun findOppT(t: Double): SkTSpan {
        val r = oppT(t)
        require(r != null) { "no opposing span containing t=$t" }
        return r
    }

    /**
     * Closest bounded-entry endpoint t-value to [pt] (squared
     * distance). Mirrors `SkTSpan::closestBoundedT`.
     */
    fun closestBoundedT(pt: SkDPoint): Double {
        var result = -1.0
        var closest = Double.MAX_VALUE
        var b = fBounded
        while (b != null) {
            val test = b.bounded
            val startDist = test.pointFirst().distanceSquared(pt)
            if (closest > startDist) { closest = startDist; result = test.fStartT }
            val endDist = test.pointLast().distanceSquared(pt)
            if (closest > endDist) { closest = endDist; result = test.fEndT }
            b = b.next
        }
        require(between(0.0, result, 1.0))
        return result
    }

    /** True if [t] lies in any sibling span's t-range. Mirrors `SkTSpan::contains`. */
    fun contains(t: Double): Boolean {
        var work: SkTSpan? = this
        while (work != null) {
            if (between(work.fStartT, t, work.fEndT)) return true
            work = work.fNext
        }
        return false
    }

    /** Find the bounded entry whose t-range contains [t], or null. Mirrors `SkTSpan::oppT`. */
    internal fun oppT(t: Double): SkTSpan? {
        var b = fBounded
        while (b != null) {
            val test = b.bounded
            if (between(test.fStartT, t, test.fEndT)) return test
            b = b.next
        }
        return null
    }

    // ─── Hull / linear intersect tests ──────────────────────────────

    /**
     * Test if this span's curve hull might intersect [opp]'s hull.
     * Returns 0 (no intersection), 1 (hulls intersect), 2 (only
     * common endpoints), or -1 (linear — caller should follow up
     * with [linearsIntersect]). Mirrors `SkTSpan::hullCheck`.
     */
    fun hullCheck(opp: SkTSpan, start: BooleanArray, oppStart: BooleanArray): Int {
        if (fIsLinear) return -1
        val ptsInCommon = booleanArrayOf(false)
        if (onlyEndPointsInCommon(opp, start, oppStart, ptsInCommon)) {
            require(ptsInCommon[0])
            return 2
        }
        val linearOut = booleanArrayOf(false)
        if (fPart.hullIntersects(opp.fPart, linearOut)) {
            if (!linearOut[0]) return 1
            fIsLinear = true
            fIsLine = fPart.controlsInside()
            return if (ptsInCommon[0]) 1 else -1
        }
        return if (ptsInCommon[0]) 2 else 0
    }

    /**
     * Two-pass hull-intersection check : first this vs opp, then if
     * inconclusive, opp vs this. Returns -1 if both linear (caller
     * uses [linearsIntersect] as fallback).
     * Mirrors `SkTSpan::hullsIntersect`.
     */
    fun hullsIntersect(opp: SkTSpan, start: BooleanArray, oppStart: BooleanArray): Int {
        if (!fBounds.intersects(opp.fBounds)) return 0
        var hullSect = hullCheck(opp, start, oppStart)
        if (hullSect >= 0) return hullSect
        hullSect = opp.hullCheck(this, oppStart, start)
        if (hullSect >= 0) return hullSect
        return -1
    }

    /**
     * Two-pass linear-intersect check (for spans whose hulls have
     * collapsed to a line). Mirrors `SkTSpan::linearsIntersect`.
     */
    fun linearsIntersect(span: SkTSpan): Boolean {
        var result = linearIntersects(span.fPart)
        if (result <= 1) return result != 0
        require(span.fIsLinear)
        result = span.linearIntersects(fPart)
        return result != 0
    }

    /**
     * Project [pt] onto the chord of this span. Mirrors `SkTSpan::linearT`.
     */
    fun linearT(pt: SkDPoint): Double {
        val len = pointLast() - pointFirst()
        return if (abs(len.x) > abs(len.y)) (pt.x - pointFirst().x) / len.x
        else (pt.y - pointFirst().y) / len.y
    }

    /**
     * Linear-vs-curve test : returns 0 (no intersect), 1 (crossings
     * exist), or 3 (collinear within tolerance — caller treats as
     * potential coincidence). Mirrors `SkTSpan::linearIntersects`.
     */
    private fun linearIntersects(q2: SkTCurve): Int {
        var start = 0
        var end = fPart.pointLast()
        if (!fPart.controlsInside()) {
            // Find the most-spread pair.
            var dist = 0.0
            for (outer in 0 until pointCount() - 1) {
                for (inner in outer + 1 until pointCount()) {
                    val test = (fPart[outer] - fPart[inner]).lengthSquared()
                    if (dist > test) continue
                    dist = test
                    start = outer; end = inner
                }
            }
        }
        val origX = fPart[start].x
        val origY = fPart[start].y
        val adj = fPart[end].x - origX
        val opp = fPart[end].y - origY
        val maxPart = max(abs(adj), abs(opp))
        var sign = 0.0
        for (n in 0 until q2.pointCount()) {
            val dx = q2[n].y - origY
            val dy = q2[n].x - origX
            val maxVal = max(maxPart, max(abs(dx), abs(dy)))
            val test = (q2[n].y - origY) * adj - (q2[n].x - origX) * opp
            if (precisely_zero_when_compared_to(test, maxVal)) return 1
            if (approximately_zero_when_compared_to(test, maxVal)) return 3
            if (n == 0) { sign = test; continue }
            if (test * sign < 0) return 1
        }
        return 0
    }

    /**
     * Return true iff [opp] shares an endpoint with this span and the
     * non-shared controls are all "inward" (negative dot products).
     * Mirrors `SkTSpan::onlyEndPointsInCommon`.
     */
    fun onlyEndPointsInCommon(
        opp: SkTSpan,
        start: BooleanArray,
        oppStart: BooleanArray,
        ptsInCommon: BooleanArray,
    ): Boolean {
        when {
            opp.pointFirst() == pointFirst() -> { start[0] = true; oppStart[0] = true }
            opp.pointFirst() == pointLast() -> { start[0] = false; oppStart[0] = true }
            opp.pointLast() == pointFirst() -> { start[0] = true; oppStart[0] = false }
            opp.pointLast() == pointLast() -> { start[0] = false; oppStart[0] = false }
            else -> { ptsInCommon[0] = false; return false }
        }
        ptsInCommon[0] = true
        val otherPts = arrayOfNulls<SkDPoint>(4)
        val oppOtherPts = arrayOfNulls<SkDPoint>(4)
        val baseIndex = if (start[0]) 0 else fPart.pointLast()
        fPart.otherPts(baseIndex, otherPts)
        opp.fPart.otherPts(if (oppStart[0]) 0 else opp.fPart.pointLast(), oppOtherPts)
        val base = fPart[baseIndex]
        for (o1 in 0 until pointCount() - 1) {
            val v1 = otherPts[o1]!! - base
            for (o2 in 0 until opp.pointCount() - 1) {
                val v2 = oppOtherPts[o2]!! - base
                if (v2.dot(v1) >= 0) return false
            }
        }
        return true
    }

    // ─── Splitting ──────────────────────────────────────────────────

    /**
     * Split [work] in two at the midpoint, with this taking the
     * second half. Convenience for `splitAt(work, midT)`.
     * Mirrors `SkTSpan::split`.
     */
    fun split(work: SkTSpan): Boolean = splitAt(work, (work.fStartT + work.fEndT) * 0.5)

    /**
     * Split [work] at [t], with this taking `[t, work.endT]` and
     * `work` keeping `[work.startT, t]`. Returns false if either
     * half is degenerate (collapses to a single t). Inserts `this`
     * between `work` and `work.fNext` in the doubly-linked list.
     * Mirrors `SkTSpan::splitAt`.
     */
    fun splitAt(work: SkTSpan, t: Double): Boolean {
        fStartT = t
        fEndT = work.fEndT
        if (fStartT == fEndT) { fCollapsed = true; return false }
        work.fEndT = t
        if (work.fStartT == work.fEndT) { work.fCollapsed = true; return false }
        fPrev = work
        fNext = work.fNext
        fIsLinear = work.fIsLinear
        fIsLine = work.fIsLine

        work.fNext = this
        if (fNext != null) fNext!!.fPrev = this
        // Migrate work's bounded list to this — they all overlap both halves.
        var bounded = work.fBounded
        fBounded = null
        while (bounded != null) {
            addBounded(bounded.bounded)
            bounded = bounded.next
        }
        bounded = fBounded
        while (bounded != null) {
            bounded.bounded.addBounded(this)
            bounded = bounded.next
        }
        return true
    }
}
