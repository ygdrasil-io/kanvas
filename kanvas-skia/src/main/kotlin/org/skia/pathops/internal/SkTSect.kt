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
}
