/*
 * Copyright 2026 The kanvas-skia authors.
 *
 * Mirrors Skia's `class SkOpSegment` from `src/pathops/SkOpSegment.{h,cpp}`.
 *
 * Phase D1.2.c — data model + structural / linked-list / span-list
 * methods. The full segment class is the largest single piece of
 * pathops (~1800 LOC of `.cpp`) ; algorithmic methods —
 *   active* / activeWinding / activeOp / addCurveTo / addExpanded /
 *   addMissing / addStartSpan / addEndSpan / calcAngles / sortAngles /
 *   ComputeOneSum / computeSum / clearAll / clearOne / findNextOp /
 *   findNextWinding / findNextXor / findSortableTop / markAllDone /
 *   markAndChase{Done,Winding} / markAngle / markDone / markWinding /
 *   match / missingCoincidence / moveMultiples / moveNearby /
 *   nextChase / rayCheck / release / setUpWinding{s} /
 *   spansNearby / subDivide / testForCoincidence / undoneSpan /
 *   updateOppWinding{,Reverse} / updateWinding{,Reverse} /
 *   windingSpanAtT / windSum
 * — are deferred to subsequent D1.2.c.* / D1.2.d / D1.2.h sub-slices.
 *
 * What ships here :
 *  - Construction / lifecycle : `init` + the per-verb factories
 *    (`addLine` / `addQuad` / `addCubic` / `addConic`).
 *  - Span-list ops : `insert(SkOpSpan)`.
 *  - Linked-list traversal : `next` / `prev` + setters.
 *  - Bounds & query accessors : bounds / count / done / verb /
 *    weight / pts / lastPt / contour / visited / resetVisited /
 *    isHorizontal / isVertical (bounds-based) / contains(t).
 *  - Joiner : `joinEnds` (uses [SkOpPtT.addOpp]).
 *  - Static helpers : `SpanSign` / `OppSign` / `UseInnerWinding`.
 *  - `spanToAngle(start, end)` — start.upCast.toAngle / fromAngle
 *    selector (no algorithmic content).
 *  - `Comparable` ordering by `fBounds.top`.
 */
package org.skia.pathops.internal

import org.skia.math.SkPoint
import org.skia.math.SkRect

internal class SkOpSegment : Comparable<SkOpSegment> {

    /** Head span — always has `t == 0`. Initialized in [init]. */
    val fHead: SkOpSpan = SkOpSpan()

    /** Tail span — always has `t == 1`. Initialized in [init]. */
    val fTail: SkOpSpanBase = SkOpSpanBase()

    /** Owning contour (set via [setContour] or [init]). */
    var fContour: SkOpContour? = null

    /** Singly-linked next sibling in the contour's segment list. */
    var fNext: SkOpSegment? = null

    /** Backward link (immutable in upstream — set once when the segment is added). */
    var fPrev: SkOpSegment? = null

    /** Control points (line: 2, quad/conic: 3, cubic: 4). */
    var fPts: Array<SkPoint> = emptyArray()

    /** Tight bounds of the segment. */
    var fBounds: SkRect = SkRect.MakeEmpty()

    /** Conic weight — 1 for line / quad / cubic. */
    var fWeight: Float = 1f

    /** Number of spans (initially 1 for an unintersected segment). */
    var fCount: Int = 0

    /** Number of processed spans — `done()` returns true once `fDoneCount == fCount`. */
    var fDoneCount: Int = 0

    /** Verb tag (kLine / kQuad / kConic / kCubic). */
    var fVerb: SegVerb = SegVerb.kUnset

    /** Used by missing-coincidence walker (D1.2.g). */
    var fVisited: Boolean = false

    /** Verb classification (mirrors `SkPath::Verb` subset relevant to pathops). */
    enum class SegVerb { kUnset, kLine, kQuad, kConic, kCubic }

    // ─── Construction ───────────────────────────────────────────────

    /**
     * Initialize this segment to wrap [pts] / [weight] / [verb] with
     * [parent] as the owning contour. `fHead` and `fTail` are
     * initialized to (0, pts[0]) and (1, pts[last]) respectively, with
     * `fHead.next == fTail` / `fTail.prev == fHead`. `fCount` is set
     * to 1 (one span between the two terminals).
     *
     * Caller is responsible for computing `fBounds` (the per-verb
     * `addXxx` factories below do this for lines ; curve bounds need
     * the SkDCurve dispatcher landing in D1.2.b.2 / D1.2.h).
     */
    fun init(pts: Array<SkPoint>, weight: Float, parent: SkOpContour?, verb: SegVerb) {
        fPts = pts
        fWeight = weight
        fContour = parent
        fVerb = verb
        fCount = 1
        fDoneCount = 0
        fVisited = false
        fHead.init(this, null, 0.0, pts[0])
        // Tail is a SkOpSpanBase, not a SkOpSpan — its `prev` is fHead.
        fTail.initBase(this, fHead, 1.0, pts[lastPtIndex()])
        fHead.fNext = fTail
    }

    /** Returns the index of the curve's terminal point. */
    private fun lastPtIndex(): Int = when (fVerb) {
        SegVerb.kLine -> 1
        SegVerb.kQuad, SegVerb.kConic -> 2
        SegVerb.kCubic -> 3
        SegVerb.kUnset -> error("verb not set yet")
    }

    /**
     * Initialize as a line segment. Mirrors `SkOpSegment::addLine`.
     * Caller must guarantee `pts[0] != pts[1]`.
     */
    fun addLine(pts: Array<SkPoint>, parent: SkOpContour?): SkOpSegment {
        require(pts.size >= 2 && pts[0] != pts[1]) { "addLine requires 2 distinct points" }
        init(pts, 1f, parent, SegVerb.kLine)
        fBounds = SkRect.Bounds(arrayOf(pts[0], pts[1])) ?: SkRect.MakeEmpty()
        return this
    }

    /**
     * Initialize as a quadratic. Bounds use the loose (control-point)
     * bbox until D1.2.b.2 wires curve-tight bounds via `SkDCurve`.
     */
    fun addQuad(pts: Array<SkPoint>, parent: SkOpContour?): SkOpSegment {
        require(pts.size >= 3) { "addQuad requires 3 control points" }
        init(pts, 1f, parent, SegVerb.kQuad)
        fBounds = SkRect.Bounds(pts) ?: SkRect.MakeEmpty()
        return this
    }

    /**
     * Initialize as a conic. Bounds use the loose (control-point) bbox.
     */
    fun addConic(pts: Array<SkPoint>, weight: Float, parent: SkOpContour?): SkOpSegment {
        require(pts.size >= 3) { "addConic requires 3 control points" }
        init(pts, weight, parent, SegVerb.kConic)
        fBounds = SkRect.Bounds(pts) ?: SkRect.MakeEmpty()
        return this
    }

    /**
     * Initialize as a cubic. Bounds use the loose (control-point) bbox.
     */
    fun addCubic(pts: Array<SkPoint>, parent: SkOpContour?): SkOpSegment {
        require(pts.size >= 4) { "addCubic requires 4 control points" }
        init(pts, 1f, parent, SegVerb.kCubic)
        fBounds = SkRect.Bounds(pts) ?: SkRect.MakeEmpty()
        return this
    }

    // ─── Simple accessors ──────────────────────────────────────────

    fun bounds(): SkRect = fBounds
    fun bumpCount() { ++fCount }
    fun contour(): SkOpContour? = fContour
    fun count(): Int = fCount
    fun head(): SkOpSpan = fHead
    fun tail(): SkOpSpanBase = fTail
    fun pts(): Array<SkPoint> = fPts
    fun weight(): Float = fWeight
    fun verb(): SegVerb = fVerb
    fun next(): SkOpSegment? = fNext
    fun prev(): SkOpSegment? = fPrev

    fun setContour(c: SkOpContour) { fContour = c }
    fun setNext(s: SkOpSegment?) { fNext = s }
    fun setPrev(s: SkOpSegment?) { fPrev = s }

    fun lastPt(): SkPoint = fPts[lastPtIndex()]

    fun isHorizontal(): Boolean = fBounds.top == fBounds.bottom
    fun isVertical(): Boolean = fBounds.left == fBounds.right

    fun resetVisited() { fVisited = false }

    /**
     * Mark visited and return the previous state. Mirrors `SkOpSegment::visited`
     * (returns false on first call, true thereafter).
     */
    fun visited(): Boolean {
        if (!fVisited) { fVisited = true; return false }
        return true
    }

    /**
     * `done()` returns true once every span has been processed.
     * Mirrors `SkOpSegment::done()`.
     */
    fun done(): Boolean {
        require(fDoneCount <= fCount)
        return fDoneCount == fCount
    }

    // ─── Span-list mutation ────────────────────────────────────────

    /**
     * Insert a fresh [SkOpSpan] right after [prev] in the linked list.
     * Mirrors `SkOpSegment::insert(SkOpSpan*)` — but uses Kotlin
     * allocation rather than the upstream arena.
     */
    fun insert(prev: SkOpSpan): SkOpSpan {
        val result = SkOpSpan()
        val next = prev.next()
        result.setPrev(prev)
        prev.setNext(result)
        // Reset the freshly-allocated span's t to a sentinel — the
        // caller (typically addT, in D1.2.d) will populate it.
        result.fPtT.fT = 0.0
        result.setNext(next)
        if (next != null) next.setPrev(result)
        return result
    }

    /**
     * True if any span (including head + tail) has the exact t [t].
     * Mirrors `SkOpSegment::contains(double)`.
     */
    fun contains(t: Double): Boolean {
        var span: SkOpSpanBase? = fHead
        while (span != null) {
            if (span.t() == t) return true
            span = if (span === fTail) null else (span as? SkOpSpan)?.next()
        }
        return false
    }

    /**
     * Splice the tail of this segment with the head of [start] —
     * forms the moveTo/lineTo round-trip at contour close.
     * Mirrors `SkOpSegment::joinEnds`.
     */
    fun joinEnds(start: SkOpSegment) {
        fTail.ptT().addOpp(start.fHead.ptT(), start.fHead.ptT())
    }

    // ─── Angle ↔ span dispatch ─────────────────────────────────────

    /**
     * Pick the "outgoing" angle at the (start, end) span pair :
     *  - if `start.t < end.t`, returns the start span's `toAngle` ;
     *  - otherwise returns `start.fromAngle` (start is the later end).
     * Mirrors `SkOpSegment::spanToAngle`.
     */
    fun spanToAngle(start: SkOpSpanBase, end: SkOpSpanBase): SkOpAngle? {
        require(start !== end)
        return if (start.t() < end.t()) start.upCast().toAngle()
        else start.fromAngle()
    }

    // ─── Comparable ────────────────────────────────────────────────

    override fun compareTo(other: SkOpSegment): Int = fBounds.top.compareTo(other.fBounds.top)

    companion object {
        /**
         * Sign of the wind value at the start of the (start, end) span
         * pair — negated when `start.t < end.t`. Mirrors `SkOpSegment::SpanSign`.
         */
        fun SpanSign(start: SkOpSpanBase, end: SkOpSpanBase): Int =
            if (start.t() < end.t()) -start.upCast().windValue()
            else end.upCast().windValue()

        /**
         * Sign of the opp value at the start of the (start, end) span
         * pair — negated when `start.t < end.t`. Mirrors `SkOpSegment::OppSign`.
         */
        fun OppSign(start: SkOpSpanBase, end: SkOpSpanBase): Int =
            if (start.t() < end.t()) -start.upCast().oppValue()
            else end.upCast().oppValue()

        /**
         * "Use the inner winding" rule from upstream — used during
         * winding propagation (D1.2.h). Mirrors
         * `SkOpSegment::UseInnerWinding`.
         *
         * If `outerWinding == innerWinding`, the result is undefined
         * upstream (asserts in DEBUG builds) — we mirror that.
         */
        fun UseInnerWinding(outerWinding: Int, innerWinding: Int): Boolean {
            require(outerWinding != innerWinding)
            val absOut = if (outerWinding < 0) -outerWinding else outerWinding
            val absIn = if (innerWinding < 0) -innerWinding else innerWinding
            return if (absOut == absIn) outerWinding < 0 else absOut > absIn
        }
    }
}
