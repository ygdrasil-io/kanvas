/*
 * Copyright 2026 The kanvas-skia authors.
 *
 * Mirrors Skia's `class SkTSect` from `src/pathops/SkPathOpsTSect.h`.
 *
 * Phase D1.1.e.2.c.1 — skeleton + linked-list lifecycle. The
 * BinarySearch algorithm and its supporting geometric methods
 * (intersects, trim, coincidentCheck, computePerpendiculars,
 * extractCoincident, etc.) ship in subsequent c.2 / c.3 / c.4 / c.5
 * sub-slices.
 *
 * # Data model
 *
 * `SkTSect` owns a doubly-linked list of [SkTSpan]s ([fHead] →
 * [tail]) representing the parametric `[0, 1]` interval of a curve,
 * sliced into sub-curves. As Bézier-clipping progresses, spans get
 * sub-divided and trimmed. Spans removed from the active list are
 * pushed to a [fDeleted] free list (the upstream uses an arena
 * allocator + free list ; we use a plain MutableList + free list,
 * relying on Kotlin GC for cleanup).
 */
package org.skia.pathops.internal

internal class SkTSect(curve: SkTCurve) {

    /** Reference curve that this `SkTSect` represents. */
    val fCurve: SkTCurve = curve

    /** Head of the active doubly-linked list of spans. */
    var fHead: SkTSpan? = null

    /** Head of the coincident-spans list (filled by extractCoincident — D1.1.e.2.c.2). */
    var fCoincident: SkTSpan? = null

    /** Free list of recycled (logically deleted) spans. */
    private var fDeleted: SkTSpan? = null

    /** Count of active (non-deleted, non-coincident) spans. */
    var fActiveCount: Int = 0

    /** True iff the algorithm is stuck in a loop (safety net tripped). */
    var fHung: Boolean = false

    /** Set if this sect's `t = 0` endpoint has been removed (used by BinarySearch ends-check). */
    var fRemovedStartT: Boolean = false

    /** Set if this sect's `t = 1` endpoint has been removed. */
    var fRemovedEndT: Boolean = false

    init {
        resetRemovedEnds()
        val initial = addOne()
        initial.init(curve)
        fHead = initial
    }

    fun pointLast(): SkDPoint = fCurve[fCurve.pointLast()]

    fun resetRemovedEnds() {
        fRemovedStartT = false
        fRemovedEndT = false
    }

    // ─── Allocation / linking ───────────────────────────────────────

    /**
     * Allocate a fresh [SkTSpan] (recycled from [fDeleted] if available).
     * Resets its bounded list and increments [fActiveCount].
     * Mirrors `SkTSect::addOne`.
     */
    fun addOne(): SkTSpan {
        val result: SkTSpan
        val recycled = fDeleted
        if (recycled != null) {
            result = recycled
            fDeleted = recycled.fNext
        } else {
            result = SkTSpan(fCurve)
        }
        result.reset()
        result.fHasPerp = false
        result.fDeleted = false
        ++fActiveCount
        return result
    }

    /**
     * Append a new span after [prior] (or at the head if `prior == null`).
     * The new span covers `[prior.fEndT, prior.fNext.fStartT]` (or `[0, fHead.fStartT]`
     * / `[prior.fEndT, 1]` at the boundaries). Mirrors `SkTSect::addFollowing`.
     */
    fun addFollowing(prior: SkTSpan?): SkTSpan {
        val result = addOne()
        result.fStartT = prior?.fEndT ?: 0.0
        val next = if (prior != null) prior.fNext else fHead
        result.fEndT = next?.fStartT ?: 1.0
        result.fPrev = prior
        result.fNext = next
        if (prior != null) prior.fNext = result
        else fHead = result
        if (next != null) next.fPrev = result
        result.resetBounds(fCurve)
        return result
    }

    /**
     * Allocate a fresh span and split [span] at parameter [t] — the new
     * span takes `[t, span.fEndT]` and `span` is shortened to
     * `[span.fStartT, t]`. Mirrors `SkTSect::addSplitAt`.
     */
    fun addSplitAt(span: SkTSpan, t: Double): SkTSpan {
        val result = addOne()
        result.splitAt(span, t)
        result.initBounds(fCurve)
        span.initBounds(fCurve)
        return result
    }

    /**
     * Used by perpendicular-coincidence machinery (D1.1.e.2.c.2/3) :
     * register [span] as bounded by the span at parameter [t] (creating
     * one if necessary). Mirrors `SkTSect::addForPerp`.
     */
    fun addForPerp(span: SkTSpan, t: Double) {
        if (!span.hasOppT(t)) {
            val priorSpan = arrayOfNulls<SkTSpan>(1)
            var opp = spanAtT(t, priorSpan)
            if (opp == null) {
                opp = addFollowing(priorSpan[0])
            }
            opp.addBounded(span)
            span.addBounded(opp)
        }
    }

    // ─── Linked-list traversal ──────────────────────────────────────

    /** Find the prev sibling of [span] (slow O(n) walk). Mirrors `SkTSect::prev`. */
    fun prev(span: SkTSpan): SkTSpan? {
        var result: SkTSpan? = null
        var test = fHead
        while (span !== test) {
            result = test
            test = test?.fNext
            require(test != null)
        }
        return result
    }

    /** Find the span with `fEndT > result.fEndT` for all others. Mirrors `SkTSect::tail`. */
    fun tail(): SkTSpan? {
        var result = fHead ?: return null
        var next: SkTSpan? = result
        var safetyNet = 100_000
        while (true) {
            next = next?.fNext ?: break
            if (--safetyNet == 0) return null
            if (next.fEndT > result.fEndT) result = next
        }
        return result
    }

    /**
     * Find the largest non-collapsed (or collapsed-only-if-no-others)
     * span by [SkTSpan.fBoundsMax]. Returns null and sets [fHung] if
     * the safety net trips. Mirrors `SkTSect::boundsMax`.
     */
    fun boundsMax(): SkTSpan? {
        var test = fHead ?: return null
        var largest: SkTSpan = test
        var lCollapsed = largest.fCollapsed
        var safetyNet = 10_000
        while (true) {
            test = test.fNext ?: break
            if (--safetyNet == 0) {
                fHung = true
                return null
            }
            val tCollapsed = test.fCollapsed
            if ((lCollapsed && !tCollapsed)
                || (lCollapsed == tCollapsed && largest.fBoundsMax < test.fBoundsMax)
            ) {
                largest = test
                lCollapsed = test.fCollapsed
            }
        }
        return largest
    }

    /**
     * Find the span containing parameter [t]. Writes the prev sibling
     * into [priorSpan] (length-1 out array). Returns null if no span
     * contains [t]. Mirrors `SkTSect::spanAtT`.
     */
    fun spanAtT(t: Double, priorSpan: Array<SkTSpan?>): SkTSpan? {
        require(priorSpan.size >= 1)
        var test = fHead
        var prev: SkTSpan? = null
        while (test != null && test.fEndT < t) {
            prev = test
            test = test.fNext
        }
        priorSpan[0] = prev
        return if (test != null && test.fStartT <= t) test else null
    }

    // ─── Counters / queries ─────────────────────────────────────────

    /** Count collapsed spans in the active list. Mirrors `SkTSect::collapsed`. */
    fun collapsed(): Int {
        var result = 0
        var test = fHead
        while (test != null) {
            if (test.fCollapsed) ++result
            test = test.next()
        }
        return result
    }

    /**
     * Count spans starting at [first] that are consecutive in t-space
     * (each `fEndT == fNext.fStartT`). Writes the last consecutive
     * span into [lastPtr] (length-1 out array). Mirrors `SkTSect::countConsecutiveSpans`.
     */
    fun countConsecutiveSpans(first: SkTSpan, lastPtr: Array<SkTSpan?>): Int {
        var consecutive = 1
        var last: SkTSpan = first
        while (true) {
            val next = last.fNext ?: break
            if (next.fStartT > last.fEndT) break
            ++consecutive
            last = next
        }
        lastPtr[0] = last
        return consecutive
    }

    /** True if any coincident span contains parameter [t]. Mirrors `SkTSect::coincidentHasT`. */
    fun coincidentHasT(t: Double): Boolean {
        var test = fCoincident
        while (test != null) {
            if (between(test.fStartT, t, test.fEndT)) return true
            test = test.fNext
        }
        return false
    }

    /**
     * True if any active span is in [span]'s bounded list.
     * Mirrors `SkTSect::hasBounded`.
     */
    fun hasBounded(span: SkTSpan): Boolean {
        var test = fHead ?: return false
        while (true) {
            if (test.findOppSpan(span) != null) return true
            test = test.next() ?: return false
        }
    }

    // ─── Removal / lifecycle ────────────────────────────────────────

    /**
     * Decrement [fActiveCount], push [span] to the [fDeleted] free
     * list, mark it deleted. Returns false if [fActiveCount] would go
     * negative (signaling a logic bug). Mirrors `SkTSect::markSpanGone`.
     */
    fun markSpanGone(span: SkTSpan): Boolean {
        if (--fActiveCount < 0) return false
        span.fNext = fDeleted
        fDeleted = span
        span.fDeleted = true
        return true
    }

    /** Remove [span] from the doubly-linked active list. Mirrors `SkTSect::unlinkSpan`. */
    fun unlinkSpan(span: SkTSpan): Boolean {
        val prev = span.fPrev
        val next = span.fNext
        if (prev != null) {
            prev.fNext = next
            if (next != null) {
                next.fPrev = prev
                if (next.fStartT > next.fEndT) return false
            }
        } else {
            fHead = next
            if (next != null) next.fPrev = null
        }
        return true
    }

    /**
     * Track endpoint removal — used by BinarySearch's end-check loop.
     * Mirrors `SkTSect::removedEndCheck`.
     */
    fun removedEndCheck(span: SkTSpan) {
        if (span.fStartT == 0.0) fRemovedStartT = true
        if (span.fEndT == 1.0) fRemovedEndT = true
    }

    /** Remove [span] from the active list and mark it gone. Mirrors `SkTSect::removeSpan`. */
    fun removeSpan(span: SkTSpan): Boolean {
        removedEndCheck(span)
        if (!unlinkSpan(span)) return false
        return markSpanGone(span)
    }

    /**
     * Remove the inclusive range `(first, last]` from the active list,
     * marking each gone. Mirrors `SkTSect::removeSpanRange`.
     */
    fun removeSpanRange(first: SkTSpan, last: SkTSpan) {
        if (first === last) return
        var span: SkTSpan? = first
        val final = last.fNext
        var next = span?.fNext
        while (true) {
            span = next ?: break
            if (span === final) break
            next = span.fNext
            markSpanGone(span)
        }
        if (final != null) final.fPrev = first
        first.fNext = final
    }

    /**
     * Drop every active span whose bounded list is empty. Returns false
     * if a removal failed. Mirrors `SkTSect::deleteEmptySpans`.
     */
    fun deleteEmptySpans(): Boolean {
        var test: SkTSpan?
        var next = fHead
        var safetyHatch = 1_000
        while (true) {
            test = next ?: break
            next = test.fNext
            if (test.fBounded == null) {
                if (!removeSpan(test)) return false
            }
            if (--safetyHatch < 0) return false
        }
        return true
    }

    // ─── Coincidence machinery (Phase D1.1.e.2.c.2) ────────────────

    /**
     * Direction-match check : do the tangents at [t] (this curve) and
     * [t2] ([sect2]'s curve) point in the same general direction
     * (`dot >= 0`) ? Mirrors `SkTSect::matchedDirection`.
     */
    fun matchedDirection(t: Double, sect2: SkTSect, t2: Double): Boolean {
        val dxdy = fCurve.dxdyAtT(t)
        val dxdy2 = sect2.fCurve.dxdyAtT(t2)
        return dxdy.dot(dxdy2) >= 0
    }

    /**
     * Cached version of [matchedDirection] : on first call, computes
     * the result into [oppMatched] and sets [calcMatched] true ; on
     * subsequent calls, asserts the cached value matches.
     * Mirrors `SkTSect::matchedDirCheck`.
     */
    fun matchedDirCheck(
        t: Double, sect2: SkTSect, t2: Double,
        calcMatched: BooleanArray, oppMatched: BooleanArray,
    ) {
        if (calcMatched[0]) {
            require(oppMatched[0] == matchedDirection(t, sect2, t2))
        } else {
            oppMatched[0] = matchedDirection(t, sect2, t2)
            calcMatched[0] = true
        }
    }

    /**
     * Compute perpendicular-coincidence state at every span endpoint
     * in `[first, last]`. Mirrors `SkTSect::computePerpendiculars`.
     */
    fun computePerpendiculars(sect2: SkTSect, first: SkTSpan?, last: SkTSpan?) {
        if (last == null) return
        val opp = sect2.fCurve
        var work: SkTSpan? = first
        var prior: SkTSpan? = null
        while (true) {
            val w = work ?: break
            if (!w.fHasPerp && !w.fCollapsed) {
                if (prior != null) w.fCoinStart.copyFrom(prior.fCoinEnd)
                else w.fCoinStart.setPerp(fCurve, w.fStartT, w.pointFirst(), opp)
                if (w.fCoinStart.isMatch()) {
                    val perpT = w.fCoinStart.perpT()
                    if (sect2.coincidentHasT(perpT)) w.fCoinStart.init()
                    else sect2.addForPerp(w, perpT)
                }
                w.fCoinEnd.setPerp(fCurve, w.fEndT, w.pointLast(), opp)
                if (w.fCoinEnd.isMatch()) {
                    val perpT = w.fCoinEnd.perpT()
                    if (sect2.coincidentHasT(perpT)) w.fCoinEnd.init()
                    else sect2.addForPerp(w, perpT)
                }
                w.fHasPerp = true
            }
            if (w === last) break
            prior = w
            work = w.fNext
            require(work != null)
        }
    }

    /**
     * Top-level coincidence-detection loop : walk consecutive runs of
     * ≥ COINCIDENT_SPAN_COUNT spans, compute perpendiculars, and
     * extract any coincident sub-runs. Mirrors `SkTSect::coincidentCheck`.
     */
    fun coincidentCheck(sect2: SkTSect): Boolean {
        var first: SkTSpan? = fHead ?: return false
        var last: SkTSpan? = null
        var next: SkTSpan?
        do {
            val firstNN = first ?: break
            val lastPtr = arrayOfNulls<SkTSpan>(1)
            val consecutive = countConsecutiveSpans(firstNN, lastPtr)
            last = lastPtr[0]
            next = last?.fNext
            if (consecutive < COINCIDENT_SPAN_COUNT) {
                first = next
                continue
            }
            computePerpendiculars(sect2, firstNN, last)
            // Extract coincident sub-runs.
            var coinStart: SkTSpan? = firstNN
            do {
                val resultArr = arrayOfNulls<SkTSpan>(1)
                val success = extractCoincident(sect2, coinStart!!, last!!, resultArr)
                if (!success) return false
                coinStart = resultArr[0]
            } while (coinStart != null && !(last?.fDeleted ?: true))
            if (fHead == null || sect2.fHead == null) break
            if (next == null || next.fDeleted) break
            first = next
        } while (first != null)
        return true
    }

    /**
     * Force coincidence over the entire t-range when the standard
     * algorithm fails to converge. Mirrors `SkTSect::coincidentForce`.
     */
    fun coincidentForce(sect2: SkTSect, start1s: Double, start1e: Double) {
        val first = fHead ?: return
        val last = tail() ?: return
        val oppFirst = sect2.fHead ?: return
        val oppLast = sect2.tail() ?: return
        var deleteEmpty = updateBounded(first, last, oppFirst)
        deleteEmpty = sect2.updateBounded(oppFirst, oppLast, first) || deleteEmpty
        removeSpanRange(first, last)
        sect2.removeSpanRange(oppFirst, oppLast)
        first.fStartT = start1s
        first.fEndT = start1e
        first.resetBounds(fCurve)
        first.fCoinStart.setPerp(fCurve, start1s, fCurve[0], sect2.fCurve)
        first.fCoinEnd.setPerp(fCurve, start1e, pointLast(), sect2.fCurve)
        val oppMatched = first.fCoinStart.perpT() < first.fCoinEnd.perpT()
        var oppStartT = if (first.fCoinStart.perpT() == -1.0) 0.0
            else maxOf(0.0, first.fCoinStart.perpT())
        var oppEndT = if (first.fCoinEnd.perpT() == -1.0) 1.0
            else minOf(1.0, first.fCoinEnd.perpT())
        if (!oppMatched) { val t = oppStartT; oppStartT = oppEndT; oppEndT = t }
        oppFirst.fStartT = oppStartT
        oppFirst.fEndT = oppEndT
        oppFirst.resetBounds(sect2.fCurve)
        removeCoincident(first, false)
        sect2.removeCoincident(oppFirst, true)
        if (deleteEmpty) {
            deleteEmptySpans()
            sect2.deleteEmptySpans()
        }
    }

    /**
     * Walk [first] through [lastPtr] looking for the first / last
     * span in a fully-coincident run (both endpoints match the
     * opposing curve). Returns the first span of the run, or null
     * if none found. Mirrors `SkTSect::findCoincidentRun`.
     */
    fun findCoincidentRun(firstIn: SkTSpan, lastPtr: Array<SkTSpan?>): SkTSpan? {
        var work: SkTSpan? = firstIn
        var lastCandidate: SkTSpan? = null
        var first: SkTSpan? = null
        while (true) {
            val w = work ?: return null
            if (w.fCoinStart.isMatch()) {
                require(w.hasOppT(w.fCoinStart.perpT()))
                if (!w.fCoinEnd.isMatch()) break
                lastCandidate = w
                if (first == null) first = w
            } else if (first != null && w.fCollapsed) {
                lastPtr[0] = lastCandidate
                return first
            } else {
                lastCandidate = null
                require(first == null)
            }
            if (w === lastPtr[0]) return first
            work = w.fNext
            if (work == null) return null
        }
        if (lastCandidate != null) lastPtr[0] = lastCandidate
        return first
    }

    /**
     * Bisect to find the exact t-value where coincidence transitions
     * to non-coincidence. Mirrors `SkTSect::binarySearchCoin`.
     *
     * Returns true and writes [resultT] / [oppT] / [oppFirst] on
     * success ; false otherwise. Used by [extractCoincident] to
     * extend a coincident run into the previous span.
     */
    fun binarySearchCoin(
        sect2: SkTSect, tStart: Double, tStepIn: Double,
        resultT: DoubleArray, oppT: DoubleArray, oppFirst: Array<SkTSpan?>,
    ): Boolean {
        var tStep = tStepIn
        val work = SkTSpan(fCurve)
        var result = tStart
        work.fStartT = tStart; work.fEndT = tStart
        var last = fCurve.ptAtT(tStart)
        var oppPt = SkDPoint()
        var flip = false
        var contained = false
        val down = tStep < 0
        val opp = sect2.fCurve
        do {
            tStep *= 0.5
            work.fStartT += tStep
            if (flip) { tStep = -tStep; flip = false }
            work.initBounds(fCurve)
            if (work.fCollapsed) return false
            if (last.approximatelyEqual(work.pointFirst())) break
            last = work.pointFirst()
            work.fCoinStart.setPerp(fCurve, work.fStartT, last, opp)
            if (work.fCoinStart.isMatch()) {
                val oppTTest = work.fCoinStart.perpT()
                if (sect2.fHead?.contains(oppTTest) == true) {
                    oppT[0] = oppTTest
                    oppPt = work.fCoinStart.perpPt()
                    contained = true
                    if (if (down) result <= work.fStartT else result >= work.fStartT) {
                        oppFirst[0] = null
                        return false
                    }
                    result = work.fStartT
                    continue
                }
            }
            tStep = -tStep
            flip = true
        } while (true)
        if (!contained) return false
        if (last.approximatelyEqual(fCurve[0])) result = 0.0
        else if (last.approximatelyEqual(pointLast())) result = 1.0
        if (oppPt.approximatelyEqual(opp[0])) oppT[0] = 0.0
        else if (oppPt.approximatelyEqual(sect2.pointLast())) oppT[0] = 1.0
        resultT[0] = result
        return true
    }

    /**
     * Try to extract a coincident sub-run from `[first, last]` of this
     * sect that matches a sub-run on [sect2]. Writes the next start
     * span into [result] (length-1 out), or null if no further run.
     * Mirrors `SkTSect::extractCoincident`.
     */
    fun extractCoincident(
        sect2: SkTSect, firstIn: SkTSpan, lastIn: SkTSpan,
        result: Array<SkTSpan?>,
    ): Boolean {
        val lastPtr = arrayOfNulls<SkTSpan>(1)
        lastPtr[0] = lastIn
        var first = findCoincidentRun(firstIn, lastPtr)
        var last = lastPtr[0]
        if (first == null || last == null) { result[0] = null; return true }
        val startT = first.fStartT
        val oppStartTArr = doubleArrayOf(0.0)
        val oppEndTArr = doubleArrayOf(0.0)
        val prev = first.fPrev
        require(first.fCoinStart.isMatch())
        var oppFirst: SkTSpan? = first.findOppT(first.fCoinStart.perpT())
        val oppMatched = first.fCoinStart.perpT() < last.fCoinEnd.perpT()
        val coinStartArr = doubleArrayOf(0.0)
        var cutFirst: SkTSpan? = null
        val oppFirstArr = arrayOf(oppFirst)
        if (prev != null && prev.fEndT == startT
            && binarySearchCoin(sect2, startT, prev.fStartT - startT, coinStartArr, oppStartTArr, oppFirstArr)
            && prev.fStartT < coinStartArr[0] && coinStartArr[0] < startT
            && (run { cutFirst = prev.oppT(oppStartTArr[0]); cutFirst != null })
        ) {
            oppFirst = cutFirst
            first = addSplitAt(prev, coinStartArr[0])
            first.markCoincident()
            prev.fCoinEnd.markCoincident()
            val of = oppFirst!!
            if (of.fStartT < oppStartTArr[0] && oppStartTArr[0] < of.fEndT) {
                val oppHalf = sect2.addSplitAt(of, oppStartTArr[0])
                if (oppMatched) {
                    of.fCoinEnd.markCoincident()
                    oppHalf.markCoincident()
                    oppFirst = oppHalf
                } else {
                    of.markCoincident()
                    oppHalf.fCoinStart.markCoincident()
                }
            }
        } else {
            if (oppFirst == null) return false
            oppStartTArr[0] = if (oppMatched) oppFirst.fStartT else oppFirst.fEndT
        }
        require(last.fCoinEnd.isMatch())
        var oppLast: SkTSpan? = last.findOppT(last.fCoinEnd.perpT())
        oppEndTArr[0] = if (oppMatched) oppLast!!.fEndT else oppLast!!.fStartT
        if (!oppMatched) {
            val tmp = oppFirst; oppFirst = oppLast; oppLast = tmp
            val t = oppStartTArr[0]; oppStartTArr[0] = oppEndTArr[0]; oppEndTArr[0] = t
        }
        if (oppFirst == null) { result[0] = null; return true }
        if (oppLast == null) { result[0] = null; return true }
        var deleteEmpty = updateBounded(first, last, oppFirst)
        deleteEmpty = sect2.updateBounded(oppFirst, oppLast, first) || deleteEmpty
        removeSpanRange(first, last)
        sect2.removeSpanRange(oppFirst, oppLast)
        first.fEndT = last.fEndT
        first.resetBounds(fCurve)
        first.fCoinStart.setPerp(fCurve, first.fStartT, first.pointFirst(), sect2.fCurve)
        first.fCoinEnd.setPerp(fCurve, first.fEndT, first.pointLast(), sect2.fCurve)
        val newOppStartT = first.fCoinStart.perpT()
        val newOppEndT = first.fCoinEnd.perpT()
        if (between(0.0, newOppStartT, 1.0) && between(0.0, newOppEndT, 1.0)) {
            var os = newOppStartT; var oe = newOppEndT
            if (!oppMatched) { val t = os; os = oe; oe = t }
            oppFirst.fStartT = os
            oppFirst.fEndT = oe
            oppFirst.resetBounds(sect2.fCurve)
        }
        last = first.fNext
        if (!removeCoincident(first, false)) return false
        if (!sect2.removeCoincident(oppFirst, true)) return false
        if (deleteEmpty) {
            if (!deleteEmptySpans() || !sect2.deleteEmptySpans()) {
                result[0] = null
                return false
            }
        }
        result[0] = if (last != null && !last.fDeleted && fHead != null && sect2.fHead != null) last
            else null
        return true
    }

    /**
     * Merge adjacent coincident-list entries that share a boundary in
     * t-space, when the midpoint between them is also coincident.
     * Mirrors `SkTSect::mergeCoincidence`.
     */
    fun mergeCoincidence(sect2: SkTSect) {
        var smallLimit = 0.0
        outer@ while (true) {
            // find the smallest unprocessed span
            var smaller: SkTSpan? = null
            var test = fCoincident
            while (true) {
                if (test == null) return
                if (test.fStartT < smallLimit) { test = test.fNext; continue }
                if (smaller != null && smaller.fEndT < test.fStartT) { test = test.fNext; continue }
                smaller = test
                test = test.fNext
                if (test == null) break
            }
            if (smaller == null) return
            smallLimit = smaller.fEndT
            // find next larger span
            var prior: SkTSpan? = null
            var larger: SkTSpan? = null
            var largerPrior: SkTSpan? = null
            test = fCoincident
            while (test != null) {
                if (test.fStartT < smaller.fEndT) {
                    prior = test; test = test.fNext; continue
                }
                if (larger != null && larger.fStartT < test.fStartT) {
                    prior = test; test = test.fNext; continue
                }
                largerPrior = prior
                larger = test
                prior = test
                test = test.fNext
            }
            if (larger == null) continue@outer
            // check midpoint
            val midT = (smaller.fEndT + larger.fStartT) / 2
            val midPt = fCurve.ptAtT(midT)
            val coin = SkTCoincident()
            coin.setPerp(fCurve, midT, midPt, sect2.fCurve)
            if (coin.isMatch()) {
                smaller.fEndT = larger.fEndT
                smaller.fCoinEnd.copyFrom(larger.fCoinEnd)
                if (largerPrior != null) {
                    largerPrior.fNext = larger.fNext
                } else {
                    fCoincident = larger.fNext
                }
            }
        }
    }

    /**
     * Move collapsed spans from the deleted list back to the active
     * list, sorted by `fStartT`. Mirrors `SkTSect::recoverCollapsed`.
     */
    fun recoverCollapsed() {
        var deleted = fDeleted
        while (deleted != null) {
            val delNext = deleted.fNext
            if (deleted.fCollapsed) {
                // Insert into active list at sorted position.
                // Walk fHead forward until the next entry's endT > deleted.startT.
                if (fHead == null || (fHead?.fStartT ?: 0.0) > deleted.fStartT) {
                    deleted.fNext = fHead
                    fHead = deleted
                } else {
                    var cur = fHead!!
                    while (cur.fNext != null && cur.fNext!!.fEndT <= deleted.fStartT) cur = cur.fNext!!
                    deleted.fNext = cur.fNext
                    cur.fNext = deleted
                }
            }
            deleted = delNext
        }
    }

    /**
     * From [span]'s bounded list, remove every entry except [keep],
     * and propagate the removal to the opposing sect.
     * Mirrors `SkTSect::removeAllBut`.
     */
    fun removeAllBut(keep: SkTSpan, span: SkTSpan, opp: SkTSect) {
        var testBounded = span.fBounded
        while (testBounded != null) {
            val bounded = testBounded.bounded
            val next = testBounded.next
            if (bounded !== keep && !bounded.fDeleted) {
                span.removeBounded(bounded)
                if (bounded.removeBounded(span)) opp.removeSpan(bounded)
            }
            testBounded = next
        }
    }

    /**
     * Remove every span whose `fCoinStart`/`fCoinEnd` perp vectors
     * point in the same direction (an indication that the span is
     * outside the actual intersection region).
     * Mirrors `SkTSect::removeByPerpendicular`.
     */
    fun removeByPerpendicular(opp: SkTSect): Boolean {
        var test: SkTSpan? = fHead
        while (test != null) {
            val next = test.fNext
            if (test.fCoinStart.perpT() < 0 || test.fCoinEnd.perpT() < 0) {
                test = next; continue
            }
            val startV = test.fCoinStart.perpPt() - test.pointFirst()
            val endV = test.fCoinEnd.perpPt() - test.pointLast()
            if (startV.dot(endV) <= 0) { test = next; continue }
            if (!removeSpans(test, opp)) return false
            test = next
        }
        return true
    }

    /**
     * Move [span] from the active list to the coincident list (if
     * `isBetween` or its `fCoinStart.perpT()` is in `[0, 1]`), or
     * mark it gone otherwise. Mirrors `SkTSect::removeCoincident`.
     */
    fun removeCoincident(span: SkTSpan, isBetween: Boolean): Boolean {
        if (!unlinkSpan(span)) return false
        if (isBetween || between(0.0, span.fCoinStart.perpT(), 1.0)) {
            --fActiveCount
            span.fNext = fCoincident
            fCoincident = span
        } else {
            markSpanGone(span)
        }
        return true
    }

    /**
     * Remove [span] and propagate the removal to the opposing sect.
     * Mirrors `SkTSect::removeSpans`.
     */
    fun removeSpans(span: SkTSpan, opp: SkTSect): Boolean {
        var bounded = span.fBounded
        while (bounded != null) {
            val spanBounded = bounded.bounded
            val next = bounded.next
            if (span.removeBounded(spanBounded)) removeSpan(span)
            if (spanBounded.removeBounded(span)) opp.removeSpan(spanBounded)
            if (span.fDeleted && opp.hasBounded(span)) return false
            bounded = next
        }
        return true
    }

    /**
     * After a coincidence extraction, reset all spans in `[first, last]`
     * to point only at [oppFirst]. Returns true if any opposing span's
     * bounded list became empty (caller deletes empty spans).
     * Mirrors `SkTSect::updateBounded`.
     */
    fun updateBounded(first: SkTSpan, last: SkTSpan, oppFirst: SkTSpan): Boolean {
        var test: SkTSpan? = first
        val final = last.next()
        var deleteSpan = false
        do {
            deleteSpan = (test?.removeAllBounded() ?: false) || deleteSpan
            test = test?.fNext
        } while (test !== final && test != null)
        first.fBounded = null
        first.addBounded(oppFirst)
        return deleteSpan
    }

    // ─── Intersect machinery (Phase D1.1.e.2.c.3) ──────────────────

    /**
     * Test if [thisLine] is approximately parallel to the opposing
     * curve [opp] — heuristic used by [linesIntersect] to detect
     * coincidence early. Mirrors the static `is_parallel` template
     * helper in `SkPathOpsTSect.cpp` ; specialized to the conic case
     * (the upstream comment says "FIXME : breaks a lot of stuff now"
     * for non-conic, so the helper bails out).
     */
    fun isParallel(thisLine: SkDLine, opp: SkTCurve): Boolean {
        if (!opp.isConic()) return false
        var finds = 0
        // Perp at thisLine[1] : (thisLine[1].x + (thisLine[1].y - thisLine[0].y),
        //                        thisLine[1].y + (thisLine[0].x - thisLine[1].x))
        val perp1Pt0 = SkDPoint(
            thisLine[1].x + (thisLine[1].y - thisLine[0].y),
            thisLine[1].y + (thisLine[0].x - thisLine[1].x),
        )
        val thisPerp1 = SkDLine(arrayOf(perp1Pt0, thisLine[1]))
        val perpRayI = SkIntersections()
        opp.intersectRay(perpRayI, thisPerp1)
        for (pIndex in 0 until perpRayI.used()) {
            if (perpRayI.pt(pIndex).approximatelyEqual(thisPerp1[1])) finds++
        }
        // Perp at thisLine[0].
        val perp2Pt1 = SkDPoint(
            thisLine[0].x + (thisLine[1].y - thisLine[0].y),
            thisLine[0].y + (thisLine[0].x - thisLine[1].x),
        )
        val thisPerp2 = SkDLine(arrayOf(thisLine[0], perp2Pt1))
        opp.intersectRay(perpRayI, thisPerp2)
        for (pIndex in 0 until perpRayI.used()) {
            if (perpRayI.pt(pIndex).approximatelyEqual(thisPerp2[0])) finds++
        }
        return finds >= 2
    }

    /**
     * Find the intersection of [span]'s and [oppSpan]'s linearized
     * (chord) approximations, refining via tangent-line iteration if
     * not yet converged. Returns 0 (no intersect), 1 (one converged
     * intersection written to [i]), or 2 (curves are coincident over
     * a range). Mirrors `SkTSect::linesIntersect`.
     */
    fun linesIntersect(span: SkTSpan, opp: SkTSect, oppSpan: SkTSpan, i: SkIntersections): Int {
        val thisRayI = SkIntersections()
        val oppRayI = SkIntersections()
        var thisLine = SkDLine(arrayOf(span.pointFirst(), span.pointLast()))
        var oppLine = SkDLine(arrayOf(oppSpan.pointFirst(), oppSpan.pointLast()))
        var loopCount = 0
        var bestDistSq = Double.MAX_VALUE
        if (opp.fCurve.intersectRay(thisRayI, thisLine) == 0) return 0
        if (fCurve.intersectRay(oppRayI, oppLine) == 0) return 0
        // Coincidence detection : both endpoints of one chord land on the opp curve.
        if (thisRayI.used() > 1) {
            var ptMatches = 0
            for (tIndex in 0 until thisRayI.used()) {
                for (lIndex in 0..1) {
                    if (thisRayI.pt(tIndex).approximatelyEqual(thisLine[lIndex])) ptMatches++
                }
            }
            if (ptMatches == 2 || isParallel(thisLine, opp.fCurve)) return 2
        }
        if (oppRayI.used() > 1) {
            var ptMatches = 0
            for (oIndex in 0 until oppRayI.used()) {
                for (lIndex in 0..1) {
                    if (oppRayI.pt(oIndex).approximatelyEqual(oppLine[lIndex])) ptMatches++
                }
            }
            if (ptMatches == 2 || isParallel(oppLine, fCurve)) return 2
        }
        // Iterate : pick the closest pair, refine the lines using tangents at those t-values.
        do {
            var closest = Double.MAX_VALUE
            var closeIndex = 0
            var oppCloseIndex = 0
            for (index in 0 until oppRayI.used()) {
                if (!roughly_between(span.fStartT, oppRayI.t(0, index), span.fEndT)) continue
                for (oIndex in 0 until thisRayI.used()) {
                    if (!roughly_between(oppSpan.fStartT, thisRayI.t(0, oIndex), oppSpan.fEndT)) continue
                    val distSq = thisRayI.pt(oIndex).distanceSquared(oppRayI.pt(index))
                    if (closest > distSq) {
                        closest = distSq
                        closeIndex = index
                        oppCloseIndex = oIndex
                    }
                }
            }
            if (closest == Double.MAX_VALUE) break
            val oppIPt = thisRayI.pt(oppCloseIndex)
            val iPt = oppRayI.pt(closeIndex)
            if (between(span.fStartT, oppRayI.t(0, closeIndex), span.fEndT)
                && between(oppSpan.fStartT, thisRayI.t(0, oppCloseIndex), oppSpan.fEndT)
                && oppIPt.approximatelyEqual(iPt)
            ) {
                i.merge(oppRayI, closeIndex, thisRayI, oppCloseIndex)
                return i.used()
            }
            val distSq = oppIPt.distanceSquared(iPt)
            if (bestDistSq < distSq || ++loopCount > 5) return 0
            bestDistSq = distSq
            val oppStart = oppRayI.t(0, closeIndex)
            val newThis0 = fCurve.ptAtT(oppStart)
            val tangent = fCurve.dxdyAtT(oppStart)
            thisLine = SkDLine(arrayOf(newThis0, SkDPoint(newThis0.x + tangent.x, newThis0.y + tangent.y)))
            if (opp.fCurve.intersectRay(thisRayI, thisLine) == 0) break
            val start = thisRayI.t(0, oppCloseIndex)
            val newOpp0 = opp.fCurve.ptAtT(start)
            val oppTangent = opp.fCurve.dxdyAtT(start)
            oppLine = SkDLine(arrayOf(newOpp0, SkDPoint(newOpp0.x + oppTangent.x, newOpp0.y + oppTangent.y)))
            if (fCurve.intersectRay(oppRayI, oppLine) == 0) break
        } while (true)
        // Convergence may fail when curves are nearly coincident — fall back
        // to a perpendicular bisection.
        val oCoinS = SkTCoincident()
        val oCoinE = SkTCoincident()
        oCoinS.setPerp(opp.fCurve, oppSpan.fStartT, oppSpan.pointFirst(), fCurve)
        oCoinE.setPerp(opp.fCurve, oppSpan.fEndT, oppSpan.pointLast(), fCurve)
        var tStart = oCoinS.perpT()
        var tEnd = oCoinE.perpT()
        val swapped = tStart > tEnd
        if (swapped) { val t = tStart; tStart = tEnd; tEnd = t }
        tStart = maxOf(tStart, span.fStartT)
        tEnd = minOf(tEnd, span.fEndT)
        if (tStart > tEnd) return 0
        val perpS: SkDVector
        val perpE: SkDVector
        when {
            tStart == span.fStartT -> {
                val coinS = SkTCoincident()
                coinS.setPerp(fCurve, span.fStartT, span.pointFirst(), opp.fCurve)
                perpS = span.pointFirst() - coinS.perpPt()
            }
            swapped -> perpS = oCoinE.perpPt() - oppSpan.pointLast()
            else -> perpS = oCoinS.perpPt() - oppSpan.pointFirst()
        }
        when {
            tEnd == span.fEndT -> {
                val coinE = SkTCoincident()
                coinE.setPerp(fCurve, span.fEndT, span.pointLast(), opp.fCurve)
                perpE = span.pointLast() - coinE.perpPt()
            }
            swapped -> perpE = oCoinS.perpPt() - oppSpan.pointFirst()
            else -> perpE = oCoinE.perpPt() - oppSpan.pointLast()
        }
        if (perpS.dot(perpE) >= 0) return 0
        // Bisect to find the converged point.
        val coinW = SkTCoincident()
        var workT = tStart
        var tStep = tEnd - tStart
        var workPt = SkDPoint()
        do {
            tStep *= 0.5
            if (precisely_zero(tStep)) return 0
            workT += tStep
            workPt = fCurve.ptAtT(workT)
            coinW.setPerp(fCurve, workT, workPt, opp.fCurve)
            val perpT = coinW.perpT()
            if (if (coinW.isMatch()) !between(oppSpan.fStartT, perpT, oppSpan.fEndT) else perpT < 0) {
                continue
            }
            val perpW = workPt - coinW.perpPt()
            if ((perpS.dot(perpW) >= 0) == (tStep < 0)) tStep = -tStep
            if (workPt.approximatelyEqual(coinW.perpPt())) break
        } while (true)
        val oppTTest = coinW.perpT()
        if (opp.fHead?.contains(oppTTest) != true) return 0
        i.setMax(1)
        i.insert(workT, oppTTest, workPt)
        return 1
    }

    /**
     * Probe whether [span] (this sect) and [oppSpan] ([opp] sect) might
     * intersect. Writes `oppSpan`'s outcome into [oppResult] (length-1
     * out array). Returns -1 / 0 / 1 / 2 with the same semantics as
     * [SkTSpan.hullsIntersect] : -1 means linear (caller follows up
     * with `linearsIntersect`), 0 = no, 1 = yes, 2 = endpoint-touch only.
     * Mirrors `SkTSect::intersects`.
     */
    fun intersects(span: SkTSpan, opp: SkTSect, oppSpan: SkTSpan, oppResult: IntArray): Int {
        require(oppResult.size >= 1)
        val spanStart = booleanArrayOf(false)
        val oppStart = booleanArrayOf(false)
        var hullResult = span.hullsIntersect(oppSpan, spanStart, oppStart)
        if (hullResult >= 0) {
            if (hullResult == 2) {
                if (span.fBounded == null || span.fBounded?.next == null) {
                    if (spanStart[0]) span.fEndT = span.fStartT
                    else span.fStartT = span.fEndT
                } else {
                    hullResult = 1
                }
                if (oppSpan.fBounded == null || oppSpan.fBounded?.next == null) {
                    if (oppSpan.fBounded != null && oppSpan.fBounded?.bounded !== span) return 0
                    if (oppStart[0]) oppSpan.fEndT = oppSpan.fStartT
                    else oppSpan.fStartT = oppSpan.fEndT
                    oppResult[0] = 2
                } else {
                    oppResult[0] = 1
                }
            } else {
                oppResult[0] = 1
            }
            return hullResult
        }
        if (span.fIsLine && oppSpan.fIsLine) {
            val i = SkIntersections()
            val sects = linesIntersect(span, opp, oppSpan, i)
            if (sects == 2) { oppResult[0] = 1; return 1 }
            if (sects == 0) return -1
            removedEndCheck(span)
            span.fStartT = i.t(0, 0); span.fEndT = i.t(0, 0)
            opp.removedEndCheck(oppSpan)
            oppSpan.fStartT = i.t(1, 0); oppSpan.fEndT = i.t(1, 0)
            oppResult[0] = 2
            return 2
        }
        if (span.fIsLinear || oppSpan.fIsLinear) {
            val r = if (span.linearsIntersect(oppSpan)) 1 else 0
            oppResult[0] = r
            return r
        }
        oppResult[0] = 1
        return 1
    }

    /**
     * After [span] is split, walk its bounded list and re-test each
     * entry — drop those that no longer intersect, narrow those that
     * intersect at one endpoint. Mirrors `SkTSect::trim`.
     */
    fun trim(span: SkTSpan, opp: SkTSect): Boolean {
        if (!span.initBounds(fCurve)) return false
        var testBounded = span.fBounded
        while (testBounded != null) {
            val test = testBounded.bounded
            val next = testBounded.next
            val oppSects = IntArray(1)
            val sects = intersects(span, opp, test, oppSects)
            if (sects >= 1) {
                if (oppSects[0] == 2) {
                    test.initBounds(opp.fCurve)
                    opp.removeAllBut(span, test, this)
                }
                if (sects == 2) {
                    span.initBounds(fCurve)
                    removeAllBut(test, span, opp)
                    return true
                }
            } else {
                if (span.removeBounded(test)) removeSpan(span)
                if (test.removeBounded(span)) opp.removeSpan(test)
            }
            testBounded = next
        }
        return true
    }

    companion object {
        /** Coincidence is suspected once both sects have ≥9 consecutive spans. */
        const val COINCIDENT_SPAN_COUNT = 9

        // BinarySearch endpoint-equality bitset (matches upstream `enum`).
        const val kZeroS1Set: Int = 1
        const val kOneS1Set: Int = 2
        const val kZeroS2Set: Int = 4
        const val kOneS2Set: Int = 8

        /**
         * Detect endpoint coincidences between [sect1] and [sect2]
         * (exact-equal first, then approximately-equal). Returns the
         * `kXxxSet` bitmask of endpoints registered into [intersections].
         * Mirrors `SkTSect::EndsEqual` — used by BinarySearch's
         * end-cleanup pass.
         */
        fun EndsEqual(sect1: SkTSect, sect2: SkTSect, intersections: SkIntersections): Int {
            var zeroOneSet = 0
            if (sect1.fCurve[0] == sect2.fCurve[0]) {
                zeroOneSet = zeroOneSet or kZeroS1Set or kZeroS2Set
                intersections.insert(0.0, 0.0, sect1.fCurve[0])
            }
            if (sect1.fCurve[0] == sect2.pointLast()) {
                zeroOneSet = zeroOneSet or kZeroS1Set or kOneS2Set
                intersections.insert(0.0, 1.0, sect1.fCurve[0])
            }
            if (sect1.pointLast() == sect2.fCurve[0]) {
                zeroOneSet = zeroOneSet or kOneS1Set or kZeroS2Set
                intersections.insert(1.0, 0.0, sect1.pointLast())
            }
            if (sect1.pointLast() == sect2.pointLast()) {
                zeroOneSet = zeroOneSet or kOneS1Set or kOneS2Set
                intersections.insert(1.0, 1.0, sect1.pointLast())
            }
            // Approximate-equal pass for endpoints not already matched.
            if ((zeroOneSet and (kZeroS1Set or kZeroS2Set)) == 0
                && sect1.fCurve[0].approximatelyEqual(sect2.fCurve[0])
            ) {
                zeroOneSet = zeroOneSet or kZeroS1Set or kZeroS2Set
                intersections.insertNear(0.0, 0.0, sect1.fCurve[0], sect2.fCurve[0])
            }
            if ((zeroOneSet and (kZeroS1Set or kOneS2Set)) == 0
                && sect1.fCurve[0].approximatelyEqual(sect2.pointLast())
            ) {
                zeroOneSet = zeroOneSet or kZeroS1Set or kOneS2Set
                intersections.insertNear(0.0, 1.0, sect1.fCurve[0], sect2.pointLast())
            }
            if ((zeroOneSet and (kOneS1Set or kZeroS2Set)) == 0
                && sect1.pointLast().approximatelyEqual(sect2.fCurve[0])
            ) {
                zeroOneSet = zeroOneSet or kOneS1Set or kZeroS2Set
                intersections.insertNear(1.0, 0.0, sect1.pointLast(), sect2.fCurve[0])
            }
            if ((zeroOneSet and (kOneS1Set or kOneS2Set)) == 0
                && sect1.pointLast().approximatelyEqual(sect2.pointLast())
            ) {
                zeroOneSet = zeroOneSet or kOneS1Set or kOneS2Set
                intersections.insertNear(1.0, 1.0, sect1.pointLast(), sect2.pointLast())
            }
            return zeroOneSet
        }
    }
}
