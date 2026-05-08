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
    /** Convenience : `contour()?.globalState()`. */
    fun globalState(): SkOpGlobalState? = fContour?.globalState()
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

    /**
     * Double-precision point on this segment's curve at parameter [t].
     * Mirrors `SkOpSegment::dPtAtT` (`SkOpSegment.h:209`) — dispatches
     * by [fVerb] to the per-curve `ptAtT`.
     */
    fun dPtAtT(t: Double): SkDPoint = when (fVerb) {
        SegVerb.kLine -> SkDLine().apply { set(fPts[0], fPts[1]) }.ptAtT(t)
        SegVerb.kQuad -> SkDQuad().apply { set(fPts[0], fPts[1], fPts[2]) }.ptAtT(t)
        SegVerb.kConic -> SkDConic().apply { set(fPts[0], fPts[1], fPts[2], fWeight) }.ptAtT(t)
        SegVerb.kCubic -> SkDCubic().apply { set(fPts[0], fPts[1], fPts[2], fPts[3]) }.ptAtT(t)
        SegVerb.kUnset -> error("verb not set")
    }

    /**
     * Accumulate ray-vs-curve intersections into [ix]. Mirrors the
     * `CurveIntersectRay[verb]` dispatch table in `SkPathOpsCurve.h`.
     */
    fun intersectRay(ray: SkDLine, ix: SkIntersections): Int = when (fVerb) {
        SegVerb.kLine -> ix.intersectRay(SkDLine().apply { set(fPts[0], fPts[1]) }, ray)
        SegVerb.kQuad -> ix.intersectRay(SkDQuad().apply { set(fPts[0], fPts[1], fPts[2]) }, ray)
        SegVerb.kConic -> ix.intersectRay(SkDConic().apply { set(fPts[0], fPts[1], fPts[2], fWeight) }, ray)
        SegVerb.kCubic -> ix.intersectRay(SkDCubic().apply { set(fPts[0], fPts[1], fPts[2], fPts[3]) }, ray)
        SegVerb.kUnset -> error("verb not set")
    }

    /**
     * Double-precision tangent vector on this segment's curve at [t].
     * Mirrors `SkOpSegment::dSlopeAtT` (`SkOpSegment.h:213`).
     */
    fun dSlopeAtT(t: Double): SkDVector = when (fVerb) {
        SegVerb.kLine -> {
            val a = fPts[0]; val b = fPts[1]
            SkDVector((b.fX - a.fX).toDouble(), (b.fY - a.fY).toDouble())
        }
        SegVerb.kQuad -> SkDQuad().apply { set(fPts[0], fPts[1], fPts[2]) }.dxdyAtT(t)
        SegVerb.kConic -> SkDConic().apply { set(fPts[0], fPts[1], fPts[2], fWeight) }.dxdyAtT(t)
        SegVerb.kCubic -> SkDCubic().apply { set(fPts[0], fPts[1], fPts[2], fPts[3]) }.dxdyAtT(t)
        SegVerb.kUnset -> error("verb not set")
    }

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

    /**
     * Decrement [fCount] (and [fDoneCount] when [span] was already
     * marked done) on a span being unlinked. Mirrors
     * `SkOpSegment::release(SkOpSpan*)` (`SkOpSegment.cpp:504`).
     * Note : the actual span-list unlink happens in
     * [SkOpSpan.release].
     */
    fun release(span: SkOpSpan) {
        if (span.done()) --fDoneCount
        --fCount
        require(fCount >= fDoneCount)
    }

    /**
     * Walk every span and return the first non-`kNo` answer to
     * `SkOpSpanBase.collapsed(s, e)`. Mirrors
     * `SkOpSegment::collapsed(double, double)`
     * (`SkOpSegment.cpp:338`). Used by [SkOpCoincidence.addIfMissing]
     * (lands in D1.2.g.c.3).
     */
    fun collapsed(s: Double, e: Double): SkOpSpanBase.Collapsed {
        var span: SkOpSpanBase = fHead
        while (true) {
            val result = span.collapsed(s, e)
            if (result != SkOpSpanBase.Collapsed.kNo) return result
            val nxt = span.upCastable()?.next() ?: break
            span = nxt
        }
        return SkOpSpanBase.Collapsed.kNo
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

    // ─── Subdivision (D1.2.b.2.0) ──────────────────────────────────

    /**
     * Pin this segment between the spans [start] and [end] into the
     * provided [edge] carrier. Mirrors `SkOpSegment::subDivide`
     * (`src/pathops/SkOpSegment.cpp:1624`).
     *
     * Always sets the carrier's verb + endpoints (`edge[0]` =
     * [start]'s point, `edge[N]` = [end]'s point with `N` =
     * [segVerbToPoints]). For non-line verbs, also computes any
     * intermediate control point(s) — by reusing the original `fPts`
     * directly when the span pair already covers the full curve
     * (`(0,1)` or `(1,0)`), otherwise by calling the per-curve pinned
     * `subDivide` to produce a re-parameterised middle.
     *
     * Returns `true` iff the result is *non-line* (i.e. has middle
     * control points) ; mirrors upstream's bool return.
     */
    fun subDivide(start: SkOpSpanBase, end: SkOpSpanBase, edge: SkDCurve): Boolean {
        require(start !== end)
        val startPtT = start.ptT()
        val endPtT = end.ptT()
        edge.fVerb = fVerb
        edge.fPts[0] = SkDPoint(startPtT.fPt.fX.toDouble(), startPtT.fPt.fY.toDouble())
        val points = segVerbToPoints(fVerb)
        edge.fPts[points] = SkDPoint(endPtT.fPt.fX.toDouble(), endPtT.fPt.fY.toDouble())
        if (fVerb == SegVerb.kLine) return false
        val startT = startPtT.fT
        val endT = endPtT.fT
        if ((startT == 0.0 || endT == 0.0) && (startT == 1.0 || endT == 1.0)) {
            // Span pair covers the entire native parameterisation —
            // reuse the original control points directly (preserving
            // the upstream optimisation).
            when (fVerb) {
                SegVerb.kQuad -> {
                    edge.fPts[1] = SkDPoint(fPts[1].fX.toDouble(), fPts[1].fY.toDouble())
                }
                SegVerb.kConic -> {
                    edge.fPts[1] = SkDPoint(fPts[1].fX.toDouble(), fPts[1].fY.toDouble())
                    edge.fWeight = fWeight.toDouble()
                }
                SegVerb.kCubic -> {
                    if (startT == 0.0) {
                        edge.fPts[1] = SkDPoint(fPts[1].fX.toDouble(), fPts[1].fY.toDouble())
                        edge.fPts[2] = SkDPoint(fPts[2].fX.toDouble(), fPts[2].fY.toDouble())
                    } else {
                        // (startT, endT) = (1, 0) — the curve is being
                        // walked tail-to-head ; flip the controls.
                        edge.fPts[1] = SkDPoint(fPts[2].fX.toDouble(), fPts[2].fY.toDouble())
                        edge.fPts[2] = SkDPoint(fPts[1].fX.toDouble(), fPts[1].fY.toDouble())
                    }
                }
                else -> error("unreachable")
            }
            return false
        }
        // General sub-range : delegate to the per-curve pinned
        // subDivide that consumes the start / end points and emits the
        // middle control(s).
        when (fVerb) {
            SegVerb.kQuad -> {
                val q = SkDQuad().apply { set(fPts[0], fPts[1], fPts[2]) }
                edge.fPts[1] = q.subDivide(edge.fPts[0], edge.fPts[2], startT, endT)
            }
            SegVerb.kConic -> {
                val c = SkDConic().apply { set(fPts[0], fPts[1], fPts[2], fWeight) }
                val weightOut = FloatArray(1)
                edge.fPts[1] = c.subDivide(edge.fPts[0], edge.fPts[2], startT, endT, weightOut)
                edge.fWeight = weightOut[0].toDouble()
            }
            SegVerb.kCubic -> {
                val cu = SkDCubic().apply { set(fPts[0], fPts[1], fPts[2], fPts[3]) }
                val mid = arrayOf(SkDPoint(), SkDPoint())
                cu.subDivide(edge.fPts[0], edge.fPts[3], startT, endT, mid)
                edge.fPts[1] = mid[0]
                edge.fPts[2] = mid[1]
            }
            else -> error("unreachable")
        }
        return true
    }

    // ─── Angle ring construction (D1.2.c.2.a) ──────────────────────

    /**
     * Allocate a fresh [SkOpAngle] that wraps the curve from
     * `fHead.next()` back to `fHead`, and stash it in `fHead.toAngle`.
     * Returns the new angle.
     *
     * Mirrors `SkOpSegment::addStartSpan` (`SkOpSegment.h:91`). Used
     * by [calcAngles] when the segment's head needs an outgoing
     * angle.
     */
    fun addStartSpan(): SkOpAngle {
        val angle = SkOpAngle()
        angle.set(fHead, fHead.next()!!)
        fHead.setToAngle(angle)
        return angle
    }

    /**
     * Allocate a fresh [SkOpAngle] that wraps the curve from `fTail`
     * back to `fTail.prev()`, and stash it in `fTail.fromAngle`.
     * Returns the new angle.
     *
     * Mirrors `SkOpSegment::addEndSpan` (`SkOpSegment.h:73`). Used by
     * [calcAngles] when the segment's tail needs an incoming angle.
     */
    fun addEndSpan(): SkOpAngle {
        val angle = SkOpAngle()
        angle.set(fTail, fTail.prev()!!)
        fTail.setFromAngle(angle)
        return angle
    }

    /**
     * Walk every span in this segment and attach `fromAngle` /
     * `toAngle` for each non-canceled span pair. Mirrors
     * `SkOpSegment::calcAngles` (`SkOpSegment.cpp:292`).
     *
     * Each span gets :
     *  - a `toAngle` looking forward toward `span.next` (if active),
     *  - a `fromAngle` looking backward toward `span.prev` (if its
     *    predecessor was active).
     *
     * Head / tail get their angles via [addStartSpan] / [addEndSpan]
     * when the canonical head / tail state isn't already simple
     * (i.e. has only the segment's own pt-T in its loop).
     */
    fun calcAngles() {
        var activePrior = !fHead.isCanceled()
        if (activePrior && !fHead.simple()) addStartSpan()
        var prior: SkOpSpan = fHead
        var spanBase: SkOpSpanBase = fHead.next() ?: return
        while (spanBase !== fTail) {
            if (activePrior) {
                val priorAngle = SkOpAngle()
                priorAngle.set(spanBase, prior)
                spanBase.setFromAngle(priorAngle)
            }
            val span = spanBase.upCast()
            val active = !span.isCanceled()
            val next = span.next() ?: break
            if (active) {
                val angle = SkOpAngle()
                angle.set(span, next)
                span.setToAngle(angle)
            }
            activePrior = active
            prior = span
            spanBase = next
        }
        if (activePrior && !fTail.simple()) addEndSpan()
    }

    /**
     * Build radial-CCW angle rings at every span by gathering all
     * angles from coincident pt-T loops and inserting them into a
     * common sort ring. Mirrors `SkOpSegment::sortAngles`
     * (`SkOpSegment.cpp:1549`).
     *
     * For each span :
     *  1. Splice the span's own `fromAngle` and `toAngle` together if
     *     both exist (`fromAngle.insert(toAngle)`).
     *  2. Walk the span's pt-T loop ; for every coincident span on
     *     other segments, insert that span's angles into the ring
     *     (skipping ones already in the loop).
     *  3. If the resulting loop has only one element, drop the
     *     angle pointers — a 1-loop carries no sort information.
     *
     * Returns false if the inner safety net trips (1000-iteration
     * loop guard from upstream).
     */
    fun sortAngles(): Boolean {
        var span: SkOpSpanBase = fHead
        outer@ while (true) {
            val fromAngle = span.fromAngle()
            val toAngle = if (span.final()) null else span.upCast().toAngle()
            if (fromAngle == null && toAngle == null) {
                // Skip — no angles to sort here.
                if (span.final()) break@outer
                span = span.upCast().next() ?: break@outer
                continue
            }
            var baseAngle: SkOpAngle? = fromAngle
            if (fromAngle != null && toAngle != null) {
                if (!fromAngle.insert(toAngle)) return false
            } else if (fromAngle == null) {
                baseAngle = toAngle
            }
            var ptT: SkOpPtT? = span.ptT()
            val stopPtT = ptT
            var safetyNet = 1000
            do {
                if (--safetyNet == 0) return false
                val oSpan = ptT?.span()
                if (oSpan === span) {
                    ptT = ptT?.next()
                    continue
                }
                val oAngle = oSpan?.fromAngle()
                if (oAngle != null) {
                    if (!oAngle.loopContains(baseAngle!!)) {
                        baseAngle.insert(oAngle)
                    }
                }
                if (oSpan != null && !oSpan.final()) {
                    val toA = oSpan.upCast().toAngle()
                    if (toA != null) {
                        if (!toA.loopContains(baseAngle!!)) {
                            baseAngle.insert(toA)
                        }
                    }
                }
                ptT = ptT?.next()
            } while (ptT != null && ptT !== stopPtT)
            // 1-element loops carry no sort info — clear angle pointers.
            if (baseAngle != null && baseAngle.loopCount() == 1) {
                if (fromAngle != null) span.fFromAngle = null
                if (toAngle != null) span.upCast().fToAngle = null
            }
            if (span.final()) break@outer
            span = span.upCast().next() ?: break@outer
        }
        return true
    }

    // ─── Winding marking (D1.2.c.2.b) ──────────────────────────────

    /**
     * Mark every span in this segment as done. Mirrors
     * `SkOpSegment::markAllDone` (`SkOpSegment.cpp:858`).
     */
    fun markAllDone() {
        var span: SkOpSpan = fHead
        while (true) {
            markDone(span)
            val nxt = span.next()?.upCastable() ?: break
            span = nxt
        }
    }

    /**
     * Mark a single span as done — increments [fDoneCount] (driving
     * [done]). Mirrors `SkOpSegment::markDone` (`SkOpSegment.cpp:1014`).
     */
    fun markDone(span: SkOpSpan) {
        require(this === span.segment())
        if (span.done()) return
        span.setDone(true)
        ++fDoneCount
    }

    /**
     * Set [span]'s windSum to [winding]. Mirrors
     * `SkOpSegment::markWinding(SkOpSpan*, int)` (`SkOpSegment.cpp:1027`).
     * Returns `false` if the span is already done (caller treats this
     * as a no-op).
     */
    fun markWinding(span: SkOpSpan, winding: Int): Boolean {
        require(this === span.segment())
        require(winding != 0)
        if (span.done()) return false
        span.setWindSum(winding)
        return true
    }

    /**
     * Set [span]'s windSum + oppSum. Mirrors
     * `SkOpSegment::markWinding(SkOpSpan*, int, int)`
     * (`SkOpSegment.cpp:1041`).
     */
    fun markWinding(span: SkOpSpan, winding: Int, oppWinding: Int): Boolean {
        require(this === span.segment())
        require(winding != 0 || oppWinding != 0)
        if (span.done()) return false
        span.setWindSum(winding)
        span.setOppSum(oppWinding)
        return true
    }

    /**
     * Walk coincident spans across segments to chase a marking
     * forward. Mirrors `SkOpSegment::nextChase` (`SkOpSegment.cpp:1077`)
     * — the cornerstone helper for `markAndChase*`.
     *
     * Returns the next segment to mark (and updates [startPtr] /
     * [stepPtr] / [minPtr] in place), or `null` when the chase
     * terminates. When the chase visits a 3+-element angle ring, it
     * stops early and writes the visited span into [last].
     */
    fun nextChase(
        startPtr: Array<SkOpSpanBase?>,
        stepPtr: IntArray,
        minPtr: Array<SkOpSpan?>?,
        last: Array<SkOpSpanBase?>?,
    ): SkOpSegment? {
        val origStart = startPtr[0]!!
        val step = stepPtr[0]
        val endSpan: SkOpSpanBase = if (step > 0) origStart.upCast().next()!! else origStart.prev()!!
        val angle: SkOpAngle? = if (step > 0) endSpan.fromAngle() else endSpan.upCast().toAngle()
        val foundSpan: SkOpSpanBase
        val otherEnd: SkOpSpanBase?
        val other: SkOpSegment
        if (angle == null) {
            if (endSpan.t() != 0.0 && endSpan.t() != 1.0) return null
            val otherPtT = endSpan.ptT().next() ?: return null
            foundSpan = otherPtT.span() ?: return null
            other = foundSpan.segment() ?: return null
            otherEnd = if (step > 0) {
                if (foundSpan.upCastable() != null) foundSpan.upCast().next() else null
            } else foundSpan.prev()
        } else {
            if (angle.loopCount() > 2) {
                if (last != null) last[0] = endSpan
                return null
            }
            val next = angle.next() ?: return null
            other = next.segment() ?: return null
            foundSpan = next.start()!!
            otherEnd = next.end()
        }
        if (otherEnd == null) return null
        val foundStep = foundSpan.step(otherEnd)
        if (stepPtr[0] != foundStep) {
            if (last != null) last[0] = endSpan
            return null
        }
        val origMin = if (step < 0) origStart.prev()!! else origStart.upCast()
        val foundMin = foundSpan.starter(otherEnd)
        if (foundMin.windValue() != origMin.windValue() ||
            foundMin.oppValue() != origMin.oppValue()) {
            if (last != null) last[0] = endSpan
            return null
        }
        startPtr[0] = foundSpan
        stepPtr[0] = foundStep
        if (minPtr != null) minPtr[0] = foundMin
        return other
    }

    /**
     * Mark `(start, end)`'s starter span done, then chase along
     * coincident segments marking each as we go. Mirrors
     * `SkOpSegment::markAndChaseDone` (`SkOpSegment.cpp:865`).
     *
     * Returns `false` if the 1000-iteration safety net trips.
     */
    fun markAndChaseDone(
        start: SkOpSpanBase,
        end: SkOpSpanBase,
        found: Array<SkOpSpanBase?>?,
    ): Boolean {
        val step = start.step(end)
        val minSpan = start.starter(end)
        markDone(minSpan)
        val startArr = arrayOf<SkOpSpanBase?>(start)
        val stepArr = intArrayOf(step)
        val minArr = arrayOf<SkOpSpan?>(minSpan)
        val lastArr = arrayOf<SkOpSpanBase?>(null)
        var other: SkOpSegment? = this
        var priorDone: SkOpSpan? = null
        var lastDone: SkOpSpan? = null
        var safetyNet = 1000
        while (true) {
            other = other!!.nextChase(startArr, stepArr, minArr, lastArr)
            if (other == null) break
            if (--safetyNet == 0) return false
            if (other.done()) {
                require(lastArr[0] == null)
                break
            }
            val curMin = minArr[0]!!
            if (lastDone === curMin || priorDone === curMin) {
                if (found != null) found[0] = null
                return true
            }
            other.markDone(curMin)
            priorDone = lastDone
            lastDone = curMin
        }
        if (found != null) found[0] = lastArr[0]
        return true
    }

    /**
     * Mark `(start, end)` with [winding] and chase along coincident
     * segments. Mirrors the unary-winding overload of
     * `SkOpSegment::markAndChaseWinding` (`SkOpSegment.cpp:898`).
     */
    fun markAndChaseWinding(
        start: SkOpSpanBase,
        end: SkOpSpanBase,
        winding: Int,
        lastPtr: Array<SkOpSpanBase?>?,
    ): Boolean {
        val spanStart = start.starter(end)
        val step = start.step(end)
        val success = markWinding(spanStart, winding)
        val startArr = arrayOf<SkOpSpanBase?>(start)
        val stepArr = intArrayOf(step)
        val minArr = arrayOf<SkOpSpan?>(spanStart)
        val lastArr = arrayOf<SkOpSpanBase?>(null)
        var other: SkOpSegment? = this
        var safetyNet = 1000
        while (true) {
            other = other!!.nextChase(startArr, stepArr, minArr, lastArr)
            if (other == null) break
            if (--safetyNet == 0) return false
            val cur = minArr[0]!!
            if (cur.windSum() != SkOpSpan.SK_MinS32) {
                require(lastArr[0] == null)
                break
            }
            other.markWinding(cur, winding)
        }
        if (lastPtr != null) lastPtr[0] = lastArr[0]
        return success
    }

    /**
     * Binary-winding overload : marks both `winding` and `oppWinding`,
     * with operand-cross handling that flips the pair when chasing
     * across an operand boundary. Mirrors
     * `SkOpSegment::markAndChaseWinding(int, int)` (`SkOpSegment.cpp:923`).
     */
    fun markAndChaseWinding(
        start: SkOpSpanBase,
        end: SkOpSpanBase,
        winding: Int,
        oppWinding: Int,
        lastPtr: Array<SkOpSpanBase?>?,
    ): Boolean {
        val spanStart = start.starter(end)
        val step = start.step(end)
        val success = markWinding(spanStart, winding, oppWinding)
        val startArr = arrayOf<SkOpSpanBase?>(start)
        val stepArr = intArrayOf(step)
        val minArr = arrayOf<SkOpSpan?>(spanStart)
        val lastArr = arrayOf<SkOpSpanBase?>(null)
        var other: SkOpSegment? = this
        var safetyNet = 1000
        while (true) {
            other = other!!.nextChase(startArr, stepArr, minArr, lastArr)
            if (other == null) break
            if (--safetyNet == 0) return false
            val cur = minArr[0]!!
            if (cur.windSum() != SkOpSpan.SK_MinS32) {
                if (operand() == other.operand()) {
                    // Mismatch → upstream sets a "winding failed" flag on
                    // the global state but lets the operation succeed.
                    // We don't have global state, so just succeed.
                    require(lastArr[0] == null)
                } else {
                    if (cur.windSum() != oppWinding) return false
                    if (cur.oppSum() != winding) return false
                    require(lastArr[0] == null)
                }
                break
            }
            if (operand() == other.operand()) {
                other.markWinding(cur, winding, oppWinding)
            } else {
                other.markWinding(cur, oppWinding, winding)
            }
        }
        if (lastPtr != null) lastPtr[0] = lastArr[0]
        return success
    }

    /**
     * Mark the angle's starter span with `max(maxWinding, sumWinding)`
     * (per [UseInnerWinding]) and chase. Mirrors
     * `SkOpSegment::markAngle` (`SkOpSegment.cpp:960`) — unary form.
     */
    fun markAngle(
        maxWindingIn: Int,
        sumWinding: Int,
        angle: SkOpAngle,
        result: Array<SkOpSpanBase?>?,
    ): Boolean {
        require(angle.segment() === this)
        var maxWinding = maxWindingIn
        if (UseInnerWinding(maxWinding, sumWinding)) maxWinding = sumWinding
        return markAndChaseWinding(angle.start()!!, angle.end()!!, maxWinding, result)
    }

    /**
     * Binary-winding form. Mirrors `SkOpSegment::markAngle`
     * (`SkOpSegment.cpp:984`).
     */
    fun markAngle(
        maxWindingIn: Int,
        sumWinding: Int,
        oppMaxWindingIn: Int,
        oppSumWinding: Int,
        angle: SkOpAngle,
        result: Array<SkOpSpanBase?>?,
    ): Boolean {
        require(angle.segment() === this)
        var maxWinding = maxWindingIn
        var oppMaxWinding = oppMaxWindingIn
        if (UseInnerWinding(maxWinding, sumWinding)) maxWinding = sumWinding
        if (oppMaxWinding != oppSumWinding && UseInnerWinding(oppMaxWinding, oppSumWinding)) {
            oppMaxWinding = oppSumWinding
        }
        return markAndChaseWinding(angle.start()!!, angle.end()!!,
                                   maxWinding, oppMaxWinding, result)
    }

    /**
     * True if this segment is on the operand path (i.e. the second
     * input to a binary path-op). Mirrors `SkOpSegment::operand` —
     * delegates to the contour. Returns false when the contour is
     * null (test fixtures).
     */
    fun operand(): Boolean = fContour?.operand() ?: false
    /** Convenience : `contour()?.isXor()`. */
    fun isXor(): Boolean = fContour?.isXor() ?: false
    /** Convenience : `contour()?.oppXor()`. */
    fun oppXor(): Boolean = fContour?.oppXor() ?: false

    // ─── Winding queries (D1.2.c.2.c) ──────────────────────────────

    /**
     * Compute the winding sum *up to* the (start, end) span pair from
     * the [sumWindingIn] running total. Mirrors the inline
     * `SkOpSegment::setUpWinding` (`SkOpSegment.h:369`) — writes
     * `[maxWinding] = sumWinding`, then `sumWinding -= deltaSum`
     * (with the SK_MinS32 sentinel pass-through).
     *
     * Returns `(maxOut, sumOut)` as a pair.
     */
    fun setUpWinding(
        start: SkOpSpanBase,
        end: SkOpSpanBase,
        sumWindingIn: Int,
    ): Pair<Int, Int> {
        val deltaSum = SpanSign(start, end)
        val maxOut = sumWindingIn
        val sumOut = if (sumWindingIn == SkOpSpan.SK_MinS32) sumWindingIn
                     else sumWindingIn - deltaSum
        return maxOut to sumOut
    }

    /**
     * Unary 3-out form. Mirrors `setUpWindings(start, end,
     * sumMiWinding, maxWinding, sumWinding)` (`SkOpSegment.cpp:1521`).
     */
    fun setUpWindings(
        start: SkOpSpanBase,
        end: SkOpSpanBase,
        sumMiWindingInOut: IntArray,
    ): Pair<Int, Int> {
        val deltaSum = SpanSign(start, end)
        val maxOut = sumMiWindingInOut[0]
        sumMiWindingInOut[0] -= deltaSum
        val sumOut = sumMiWindingInOut[0]
        return maxOut to sumOut
    }

    /**
     * Binary 4-out form. Mirrors `setUpWindings(start, end,
     * sumMiWinding, sumSuWinding, maxWinding, sumWinding,
     * oppMaxWinding, oppSumWinding)` (`SkOpSegment.cpp:1529`).
     *
     * Returns `BinaryWindings(max, sum, oppMax, oppSum)`. When this
     * segment is on the operand path, the (sum, max) and
     * (oppSum, oppMax) channels swap.
     */
    fun setUpWindings(
        start: SkOpSpanBase,
        end: SkOpSpanBase,
        sumMiWindingInOut: IntArray,
        sumSuWindingInOut: IntArray,
    ): BinaryWindings {
        val deltaSum = SpanSign(start, end)
        val oppDeltaSum = OppSign(start, end)
        return if (operand()) {
            val maxOut = sumSuWindingInOut[0]
            sumSuWindingInOut[0] -= deltaSum
            val sumOut = sumSuWindingInOut[0]
            val oppMaxOut = sumMiWindingInOut[0]
            sumMiWindingInOut[0] -= oppDeltaSum
            val oppSumOut = sumMiWindingInOut[0]
            BinaryWindings(maxOut, sumOut, oppMaxOut, oppSumOut)
        } else {
            val maxOut = sumMiWindingInOut[0]
            sumMiWindingInOut[0] -= deltaSum
            val sumOut = sumMiWindingInOut[0]
            val oppMaxOut = sumSuWindingInOut[0]
            sumSuWindingInOut[0] -= oppDeltaSum
            val oppSumOut = sumSuWindingInOut[0]
            BinaryWindings(maxOut, sumOut, oppMaxOut, oppSumOut)
        }
    }

    /** Tuple type for the binary-op `setUpWindings` result. */
    data class BinaryWindings(val max: Int, val sum: Int, val oppMax: Int, val oppSum: Int)

    /**
     * Read the winding sum at the (start, end) span pair, applying
     * the inner-winding rule. Mirrors
     * `SkOpSegment::updateWinding(SkOpSpanBase*, SkOpSpanBase*)`
     * (`SkOpSegment.cpp:1743`).
     *
     * Returns `SK_MinS32` (the upstream sentinel) when the span's
     * winding sum hasn't been computed yet — upstream calls
     * `computeWindSum` here, which lands in a later sub-slice. Until
     * then, callers must precondition this with a manual `markWinding`.
     */
    fun updateWinding(start: SkOpSpanBase, end: SkOpSpanBase): Int {
        val lesser = start.starter(end)
        val winding = lesser.windSum()
        if (winding == SkOpSpan.SK_MinS32) {
            // TODO (D1.2.c.2.x) : call lesser.computeWindSum() once it
            // ports. For now we propagate the sentinel.
            return winding
        }
        val spanWinding = SpanSign(start, end)
        if (winding != 0 && UseInnerWinding(winding - spanWinding, winding) &&
            winding != Int.MAX_VALUE) {
            return winding - spanWinding
        }
        return winding
    }

    /**
     * Angle-input form : reads at `(angle.end, angle.start)`.
     * Mirrors `updateWinding(SkOpAngle*)` (`SkOpSegment.cpp:1760`).
     */
    fun updateWinding(angle: SkOpAngle): Int =
        updateWinding(angle.end()!!, angle.start()!!)

    /**
     * Angle-input reversed form : reads at `(angle.start, angle.end)`.
     * Mirrors `updateWindingReverse` (`SkOpSegment.cpp:1766`).
     */
    fun updateWindingReverse(angle: SkOpAngle): Int =
        updateWinding(angle.start()!!, angle.end()!!)

    /**
     * Read the opp-winding sum at the (start, end) span pair, applying
     * the inner-winding rule. Mirrors
     * `SkOpSegment::updateOppWinding(SkOpSpanBase*, SkOpSpanBase*)`
     * (`SkOpSegment.cpp:1720`).
     */
    fun updateOppWinding(start: SkOpSpanBase, end: SkOpSpanBase): Int {
        val lesser = start.starter(end)
        var oppWinding = lesser.oppSum()
        val oppSpanWinding = OppSign(start, end)
        if (oppSpanWinding != 0 &&
            UseInnerWinding(oppWinding - oppSpanWinding, oppWinding) &&
            oppWinding != Int.MAX_VALUE) {
            oppWinding -= oppSpanWinding
        }
        return oppWinding
    }

    fun updateOppWinding(angle: SkOpAngle): Int =
        updateOppWinding(angle.end()!!, angle.start()!!)

    fun updateOppWindingReverse(angle: SkOpAngle): Int =
        updateOppWinding(angle.start()!!, angle.end()!!)

    /**
     * The angle's starter span's `windSum`. Mirrors
     * `SkOpSegment::windSum(const SkOpAngle*)` (`SkOpSegment.cpp:1784`).
     */
    fun windSum(angle: SkOpAngle): Int =
        angle.start()!!.starter(angle.end()!!).windSum()

    /**
     * Decide whether the `(start, end)` span pair is "active" for
     * unary path operations (Simplify / AsWinding). Mirrors
     * `SkOpSegment::activeWinding(start, end, sumWinding)`
     * (`SkOpSegment.cpp:163`).
     *
     * The 2×2 `kUnaryActiveEdge` table boils down to "active iff
     * `from != to`" — an edge is part of the boundary when the
     * winding crosses zero.
     */
    fun activeWinding(start: SkOpSpanBase, end: SkOpSpanBase, sumWindingInOut: IntArray): Boolean {
        val (max, sum) = setUpWinding(start, end, sumWindingInOut[0])
        sumWindingInOut[0] = sum
        val from = max != 0
        val to = sum != 0
        return from != to
    }

    /**
     * Convenience overload : computes `sumWinding` from
     * `updateWinding(end, start)` first.
     */
    fun activeWinding(start: SkOpSpanBase, end: SkOpSpanBase): Boolean {
        val sumArr = intArrayOf(updateWinding(end, start))
        return activeWinding(start, end, sumArr)
    }

    // ─── Sum propagation (D1.2.c.2.d) ──────────────────────────────

    /**
     * Transfer the winding sum from [baseAngle]'s computed span to
     * the next-adjacent angle [nextAngle] in the CCW ring. Mirrors
     * `SkOpSegment::ComputeOneSum` (`SkOpSegment.cpp:349`).
     *
     * For unary (`Simplify` / `AsWinding`) include types, only the
     * `windSum` channel is propagated. For binary (`Op`), both
     * `windSum` and `oppSum` are propagated, with an operand-cross
     * swap when [baseAngle]'s segment is on the operand path.
     *
     * Returns `false` when [markAngle] declines (the marker chase hit
     * the safety net or a winding mismatch).
     */
    fun ComputeOneSum(
        baseAngle: SkOpAngle,
        nextAngle: SkOpAngle,
        includeType: SkOpAngle.IncludeType,
    ): Boolean {
        val baseSegment = baseAngle.segment()!!
        val sumMi = intArrayOf(baseSegment.updateWindingReverse(baseAngle))
        val binary = includeType >= SkOpAngle.IncludeType.kBinarySingle
        val sumSu = intArrayOf(0)
        if (binary) {
            sumSu[0] = baseSegment.updateOppWindingReverse(baseAngle)
            if (baseSegment.operand()) {
                val tmp = sumMi[0]; sumMi[0] = sumSu[0]; sumSu[0] = tmp
            }
        }
        val nextSegment = nextAngle.segment()!!
        val lastArr = arrayOf<SkOpSpanBase?>(null)
        if (binary) {
            val w = nextSegment.setUpWindings(nextAngle.start()!!, nextAngle.end()!!, sumMi, sumSu)
            if (!nextSegment.markAngle(w.max, w.sum, w.oppMax, w.oppSum, nextAngle, lastArr)) {
                return false
            }
        } else {
            val (max, sum) = nextSegment.setUpWindings(nextAngle.start()!!, nextAngle.end()!!, sumMi)
            if (!nextSegment.markAngle(max, sum, nextAngle, lastArr)) return false
        }
        nextAngle.setLastMarked(lastArr[0])
        return true
    }

    /**
     * Reverse-direction transfer : reads [baseAngle] in its
     * "forward" direction (vs. `Reverse`'s reversed read), and
     * writes [nextAngle] from `(end, start)` rather than `(start,
     * end)`. Mirrors `SkOpSegment::ComputeOneSumReverse`
     * (`SkOpSegment.cpp:384`).
     */
    fun ComputeOneSumReverse(
        baseAngle: SkOpAngle,
        nextAngle: SkOpAngle,
        includeType: SkOpAngle.IncludeType,
    ): Boolean {
        val baseSegment = baseAngle.segment()!!
        val sumMi = intArrayOf(baseSegment.updateWinding(baseAngle))
        val binary = includeType >= SkOpAngle.IncludeType.kBinarySingle
        val sumSu = intArrayOf(0)
        if (binary) {
            sumSu[0] = baseSegment.updateOppWinding(baseAngle)
            if (baseSegment.operand()) {
                val tmp = sumMi[0]; sumMi[0] = sumSu[0]; sumSu[0] = tmp
            }
        }
        val nextSegment = nextAngle.segment()!!
        val lastArr = arrayOf<SkOpSpanBase?>(null)
        if (binary) {
            val w = nextSegment.setUpWindings(nextAngle.end()!!, nextAngle.start()!!, sumMi, sumSu)
            if (!nextSegment.markAngle(w.max, w.sum, w.oppMax, w.oppSum, nextAngle, lastArr)) {
                return false
            }
        } else {
            val (max, sum) = nextSegment.setUpWindings(nextAngle.end()!!, nextAngle.start()!!, sumMi)
            if (!nextSegment.markAngle(max, sum, nextAngle, lastArr)) return false
        }
        nextAngle.setLastMarked(lastArr[0])
        return true
    }

    /**
     * Walk the CCW angle ring at the (start, end) span pair and
     * propagate winding sums until every adjacent orderable angle
     * has a computed sum. Mirrors `SkOpSegment::computeSum`
     * (`SkOpSegment.cpp:420`).
     *
     * Strategy : two passes.
     *  1. Forward (CCW) walk : when an angle has a known windSum,
     *     adopt it as the "base" and transfer to the next-adjacent
     *     angles via [ComputeOneSum]. Stop if 3 consecutive angles
     *     are unorderable.
     *  2. If the forward pass left the firstAngle un-summed but
     *     a base was found, run a reverse pass via
     *     [ComputeOneSumReverse] to back-fill.
     *
     * Returns the final `windSum` at `start.starter(end)`, or
     * `SK_MinS32` (the upstream `SK_NaN32` sentinel) when no
     * propagation could occur.
     */
    fun computeSum(
        start: SkOpSpanBase,
        end: SkOpSpanBase,
        includeType: SkOpAngle.IncludeType,
    ): Int {
        require(includeType != SkOpAngle.IncludeType.kUnaryXor)
        var firstAngle = spanToAngle(end, start) ?: return SkOpSpan.SK_MinS32
        if (firstAngle.next() == null) return SkOpSpan.SK_MinS32
        var baseAngle: SkOpAngle? = null
        var tryReverse = false
        // CCW walk : start at firstAngle.previous(), advance.
        var angle = firstAngle.previous()
        var next = angle.next()!!
        firstAngle = next
        do {
            val prior = angle
            angle = next
            next = angle.next()!!
            if (prior.unorderable() || angle.unorderable() || next.unorderable()) {
                baseAngle = null
                continue
            }
            val testWinding = angle.starter()!!.windSum()
            if (testWinding != SkOpSpan.SK_MinS32) {
                baseAngle = angle
                tryReverse = true
                continue
            }
            if (baseAngle != null) {
                ComputeOneSum(baseAngle, angle, includeType)
                baseAngle = if (angle.starter()!!.windSum() != SkOpSpan.SK_MinS32) angle else null
            }
        } while (next !== firstAngle)
        if (baseAngle != null && firstAngle.starter()!!.windSum() == SkOpSpan.SK_MinS32) {
            firstAngle = baseAngle
            tryReverse = true
        }
        if (tryReverse) {
            baseAngle = null
            var prior = firstAngle
            do {
                angle = prior
                prior = angle.previous()
                next = angle.next()!!
                if (prior.unorderable() || angle.unorderable() || next.unorderable()) {
                    baseAngle = null
                    continue
                }
                val testWinding = angle.starter()!!.windSum()
                if (testWinding != SkOpSpan.SK_MinS32) {
                    baseAngle = angle
                    continue
                }
                if (baseAngle != null) {
                    ComputeOneSumReverse(baseAngle, angle, includeType)
                    baseAngle = if (angle.starter()!!.windSum() != SkOpSpan.SK_MinS32) angle else null
                }
            } while (prior !== firstAngle)
        }
        return start.starter(end).windSum()
    }

    // ─── Pt-T linking + utilities (D1.2.c.2.e) ─────────────────────

    /**
     * Float-precision point on this segment's curve at parameter [t].
     * Convenience wrapper around [dPtAtT] mirroring the upstream
     * `SkOpSegment::ptAtT` (`SkOpSegment.h:319`).
     */
    fun ptAtT(t: Double): SkPoint = dPtAtT(t).asSkPoint()

    /**
     * True iff the curve's midpoint between `t1` and `t2` lies far
     * enough from the chord (`> 2 ×` chord-length squared, or
     * `> 2 × FLT_EPSILON`) that the two t-positions are *not* the
     * same intersection. Mirrors `SkOpSegment::ptsDisjoint`
     * (`SkOpSegment.cpp:1504`).
     *
     * For lines this trivially returns false (lines can't loop).
     */
    fun ptsDisjoint(t1: Double, pt1: SkPoint, t2: Double, pt2: SkPoint): Boolean {
        if (fVerb == SegVerb.kLine) return false
        val midT = (t1 + t2) / 2
        val midPt = ptAtT(midT)
        val dx = (pt1.fX - pt2.fX).toDouble()
        val dy = (pt1.fY - pt2.fY).toDouble()
        val seDistSq = maxOf(dx * dx + dy * dy, FLT_EPSILON * 2)
        val dx1 = (midPt.fX - pt1.fX).toDouble()
        val dy1 = (midPt.fY - pt1.fY).toDouble()
        if (dx1 * dx1 + dy1 * dy1 > seDistSq * 2) return true
        val dx2 = (midPt.fX - pt2.fX).toDouble()
        val dy2 = (midPt.fY - pt2.fY).toDouble()
        return dx2 * dx2 + dy2 * dy2 > seDistSq * 2
    }

    /**
     * True iff [base] and the candidate `(testParent, testT, testPt)`
     * tuple represent the *same* intersection. Mirrors
     * `SkOpSegment::match` (`SkOpSegment.cpp:1056`).
     *
     * Three cases :
     *  - Same segment + precisely-equal t → true.
     *  - Approximately-equal points (any segments) → true unless we
     *    can show they're disjoint (curve loops back to the same pt).
     *  - Otherwise false.
     */
    fun match(base: SkOpPtT, testParent: SkOpSegment, testT: Double, testPt: SkPoint): Boolean {
        require(this === base.span()?.segment())
        if (this === testParent && precisely_equal(base.fT, testT)) return true
        if (!SkDPoint.ApproximatelyEqual(testPt, base.fPt)) return false
        return this !== testParent || !ptsDisjoint(base.fT, base.fPt, testT, testPt)
    }

    /**
     * Insert (or find) a pt-T at parameter [t] with explicit point
     * [pt]. Walks `fHead..fTail` in t-order ; returns the existing
     * pt-T when one already lives at that t (or matches via [match]),
     * otherwise allocates a fresh [SkOpSpan] before the next-larger-t
     * span. Mirrors `SkOpSegment::addT(double, const SkPoint&)`
     * (`SkOpSegment.cpp:259`).
     *
     * Returns `null` only on the upstream `FAIL_WITH_NULL_IF` paths
     * (no prev or hit fTail unexpectedly).
     */
    fun addT(t: Double, pt: SkPoint): SkOpPtT? {
        var spanBase: SkOpSpanBase? = fHead
        while (spanBase != null) {
            val result = spanBase.ptT()
            if (t == result.fT || (!zero_or_one(t) && match(result, this, t, pt))) {
                spanBase.bumpSpanAdds()
                return result
            }
            if (t < result.fT) {
                val prev = result.span()?.prev() ?: return null
                val span = insert(prev)
                span.init(this, prev, t, pt)
                span.bumpSpanAdds()
                ++fCount
                // Flag the global state so addExpanded callers can
                // detect that a fresh span was allocated.
                globalState()?.setAllocatedOpSpan()
                return span.fPtT
            }
            if (spanBase === fTail) return null
            spanBase = spanBase.upCast().next()
        }
        return null
    }

    /** Convenience overload : derives `pt` via [ptAtT]. */
    fun addT(t: Double): SkOpPtT? = addT(t, ptAtT(t))

    /**
     * Break a span at parameter [newT] so the coincident sub-range
     * doesn't change the angle of the remainder. Resets the
     * `allocatedOpSpan` flag, calls [addT], and (when a previous
     * non-self-loop oppPrev exists on [test]'s ptT) splices the new
     * pt-T into [test]'s loop via [SkOpPtT.addOpp] +
     * [SkOpSpanBase.mergeMatches] + [SkOpSpanBase.checkForCollapsedCoincidence].
     *
     * `startOverOut[0]` is OR-ed with the post-`addT` allocatedOpSpan
     * flag — caller (`SkOpCoincidence.addExpanded`) restarts its walk
     * when a fresh span has been inserted, since the linked list it
     * was iterating may have grown.
     *
     * Mirrors `SkOpSegment::addExpanded(double, const SkOpSpanBase*,
     * bool*)` (`SkOpSegment.cpp:235`).
     */
    fun addExpanded(newT: Double, test: SkOpSpanBase, startOverOut: BooleanArray): Boolean {
        if (this.contains(newT)) return true
        globalState()?.resetAllocatedOpSpan()
        if (!between(0.0, newT, 1.0)) return false
        val newPtT = addT(newT) ?: return false
        if (globalState()?.allocatedOpSpan() == true) startOverOut[0] = true
        newPtT.fPt = ptAtT(newT)
        val oppPrev = test.ptT().oppPrev(newPtT) ?: return true
        test.mergeMatches(newPtT.span()!!)
        test.ptT().addOpp(newPtT, oppPrev)
        test.checkForCollapsedCoincidence()
        return true
    }

    /**
     * Walk `fHead..fTail` looking for a pt-T at parameter [t]. Returns
     * the pt-T if one already exists at that t (or matches via
     * [match]) ; otherwise null. When [opp] is non-null, the result
     * must additionally have a sibling on [opp] in its loop.
     *
     * Mirrors `SkOpSegment::existing(double, const SkOpSegment*)`
     * (`SkOpSegment.cpp:203`). Used by [SkOpCoincidence.addOrOverlap]
     * (D1.2.g.c.3) to skip pt-T allocation when a usable one already
     * sits at the desired t-value.
     */
    fun existing(t: Double, opp: SkOpSegment?): SkOpPtT? {
        var test: SkOpSpanBase = fHead
        val pt = ptAtT(t)
        var testPtT: SkOpPtT
        while (true) {
            testPtT = test.ptT()
            if (testPtT.fT == t) break
            if (!match(testPtT, this, t, pt)) {
                if (t < testPtT.fT) return null
            } else {
                if (opp == null) return testPtT
                // Walk testPtT's loop looking for a sibling on `this`
                // at the same t / pt.
                var loop: SkOpPtT = testPtT.next() ?: return null
                var found = false
                while (loop !== testPtT) {
                    if (loop.span()?.segment() === this && loop.fT == t && loop.fPt == pt) {
                        found = true; break
                    }
                    loop = loop.next() ?: return null
                }
                if (!found) return null
                break
            }
            if (test === fTail) return null
            test = test.upCast().next() ?: return null
        }
        return if (opp != null && test.contains(opp) == null) null else testPtT
    }

    /**
     * Emit the curve segment from [start] to [end] into [path].
     * Sub-divides the curve, runs the hull-sweep classification, and
     * dispatches to the appropriate [SkPathWriter] verb. Mirrors
     * `SkOpSegment::addCurveTo` (`SkOpSegment.cpp:172`).
     *
     * Returns `false` when the starter span was already emitted (the
     * upstream `FAIL_IF(spanStart->alreadyAdded())` guard) — caller
     * treats this as an abort signal.
     */
    fun addCurveTo(start: SkOpSpanBase, end: SkOpSpanBase, path: SkPathWriter): Boolean {
        val spanStart = start.starter(end)
        if (spanStart.alreadyAdded()) return false
        spanStart.markAdded()
        val curvePart = SkDCurveSweep()
        start.segment()!!.subDivide(start, end, curvePart.fCurve)
        curvePart.setCurveHullSweep(fVerb)
        // Drop to a line when the hull collapsed (matches upstream).
        val verb = if (curvePart.isCurve()) fVerb else SegVerb.kLine
        path.deferredMove(start.ptT())
        when (verb) {
            SegVerb.kLine -> if (!path.deferredLine(end.ptT())) return false
            SegVerb.kQuad -> path.quadTo(curvePart.fCurve[1].asSkPoint(), end.ptT())
            SegVerb.kConic -> path.conicTo(
                curvePart.fCurve[1].asSkPoint(),
                end.ptT(),
                curvePart.fCurve.fWeight.toFloat(),
            )
            SegVerb.kCubic -> path.cubicTo(
                curvePart.fCurve[1].asSkPoint(),
                curvePart.fCurve[2].asSkPoint(),
                end.ptT(),
            )
            SegVerb.kUnset -> error("verb not set")
        }
        return true
    }

    /**
     * Reset a single span's wind / opp values to 0 and mark it done.
     * Mirrors `SkOpSegment::clearOne` (`SkOpSegment.cpp:332`).
     */
    fun clearOne(span: SkOpSpan) {
        span.setWindValue(0)
        span.setOppValue(0)
        markDone(span)
    }

    /**
     * Clear every span on this segment via [clearOne] then drop the
     * segment from coincidence tracking. Mirrors
     * `SkOpSegment::clearAll` (`SkOpSegment.cpp:323`).
     *
     * The coincidence-release step is a no-op until D1.2.g lands the
     * SkOpCoincidence collection.
     */
    fun clearAll() {
        var span: SkOpSpan? = fHead
        while (span != null) {
            clearOne(span)
            span = span.next()?.upCastable()
        }
        // TODO (D1.2.g) : globalState().coincidence().release(this).
    }

    // ─── Coincidence helpers (D1.2.c.2.f / D1.2.g.0) ───────────────

    /**
     * True iff this segment is "close" to [opp] at parameter [t] —
     * the perpendicular ray at the curve point hits [opp] within
     * roughly-equal distance. Mirrors `SkOpSegment::isClose`
     * (`SkOpSegment.cpp:839`). Used by [SkCoincidentSpans.expand].
     */
    fun isClose(t: Double, opp: SkOpSegment): Boolean {
        val cPt = dPtAtT(t)
        val dxdy = dSlopeAtT(t)
        val perp = SkDLine().apply {
            this[0] = SkDPoint(cPt.x, cPt.y)
            this[1] = SkDPoint(cPt.x + dxdy.y, cPt.y - dxdy.x)
        }
        val ix = SkIntersections()
        opp.intersectRay(perp, ix)
        for (index in 0 until ix.used()) {
            if (cPt.roughlyEqual(ix.pt(index))) return true
        }
        return false
    }

    /**
     * Find the first span whose `done` flag is unset. Mirrors
     * `SkOpSegment::undoneSpan` (`SkOpSegment.cpp:1708`). Used by
     * the contour walker to seed the next sort.
     */
    fun undoneSpan(): SkOpSpan? {
        var span: SkOpSpan = fHead
        while (true) {
            val next = span.next()
            if (!span.done()) return span
            if (next == null || next.final()) return null
            span = next.upCast()
        }
    }

    /**
     * True iff the prior↔current pt-T pair represents a coincidence
     * with [opp]. Samples the curve at the average t and projects a
     * perpendicular ray through [opp]'s sub-curve ; coincidence
     * holds when (a) the midpoint already coincides with one of the
     * pt-T endpoints, or (b) the perpendicular ray intersects [opp]
     * within `[0, 1]` at a point approximately-equal to our midpoint.
     *
     * Mirrors `SkOpSegment::testForCoincidence` (`SkOpSegment.cpp:1671`).
     */
    fun testForCoincidence(
        priorPtT: SkOpPtT,
        ptT: SkOpPtT,
        prior: SkOpSpanBase,
        spanBase: SkOpSpanBase,
        opp: SkOpSegment,
    ): Boolean {
        val midT = (prior.t() + spanBase.t()) / 2
        val midPt = ptAtT(midT)
        var coincident = true
        if (!SkDPoint.ApproximatelyEqual(priorPtT.fPt, midPt) &&
            !SkDPoint.ApproximatelyEqual(ptT.fPt, midPt)) {
            if (priorPtT.span() === ptT.span()) return false
            coincident = false
            val curvePart = SkDCurve()
            subDivide(prior, spanBase, curvePart)
            curvePart.fVerb = fVerb
            val dxdy = curvePart.slopeAtT(0.5)
            val partMidPt = curvePart.pointAtT(0.5)
            val ray = SkDLine().apply {
                this[0] = SkDPoint(midPt.fX.toDouble(), midPt.fY.toDouble())
                this[1] = SkDPoint(partMidPt.x + dxdy.y, partMidPt.y - dxdy.x)
            }
            val oppPart = SkDCurve()
            opp.subDivide(priorPtT.span()!!, ptT.span()!!, oppPart)
            oppPart.fVerb = opp.verb()
            val ix = SkIntersections()
            oppPart.intersectRay(ray, ix)
            for (index in 0 until ix.used()) {
                if (!between(0.0, ix.t(0, index), 1.0)) continue
                val oppPt = ix.pt(index)
                if (oppPt.approximatelyDEqual(SkDPoint(midPt.fX.toDouble(), midPt.fY.toDouble()))) {
                    coincident = true
                }
            }
        }
        return coincident
    }

    /**
     * True iff the closest-point pair across [refSpan]'s and
     * [checkSpan]'s pt-T loops is approximately coincident. Writes
     * the result into [foundOut]`[0]`. Returns `false` only on the
     * 100-iteration safety net trip from upstream.
     *
     * Mirrors `SkOpSegment::spansNearby` (`SkOpSegment.cpp:1370`).
     */
    fun spansNearby(
        refSpan: SkOpSpanBase,
        checkSpan: SkOpSpanBase,
        foundOut: BooleanArray,
    ): Boolean {
        val refHead = refSpan.ptT()
        val checkHead = checkSpan.ptT()
        // Cheap reject : if the heads are far apart, all pairs are.
        if (!SkDPoint.WayRoughlyEqual(refHead.fPt, checkHead.fPt)) {
            foundOut[0] = false
            return true
        }
        var distSqBest = Double.POSITIVE_INFINITY
        var refBest: SkOpPtT? = null
        var checkBest: SkOpPtT? = null
        var ref: SkOpPtT = refHead
        outer@ while (true) {
            if (!ref.deleted()) {
                while (ref.ptAlreadySeen(refHead)) {
                    val n = ref.next() ?: break@outer
                    if (n === refHead) break@outer
                    ref = n
                }
                val refSeg = ref.span()?.segment()
                var check: SkOpPtT = checkHead
                var escapeHatch = 100
                inner@ while (true) {
                    if (!check.deleted()) {
                        while (check.ptAlreadySeen(checkHead)) {
                            val n = check.next() ?: break@inner
                            if (n === checkHead) break@inner
                            check = n
                        }
                        val dx = (ref.fPt.fX - check.fPt.fX).toDouble()
                        val dy = (ref.fPt.fY - check.fPt.fY).toDouble()
                        val distSq = dx * dx + dy * dy
                        val checkSeg = check.span()?.segment()
                        val disjointOrDifferent =
                            refSeg !== checkSeg ||
                            !(refSeg ?: this).ptsDisjoint(ref.fT, ref.fPt, check.fT, check.fPt)
                        if (distSqBest > distSq && disjointOrDifferent) {
                            distSqBest = distSq
                            refBest = ref
                            checkBest = check
                        }
                        if (--escapeHatch <= 0) return false
                    }
                    val n = check.next() ?: break@inner
                    if (n === checkHead) break@inner
                    check = n
                }
            }
            val n = ref.next() ?: break@outer
            if (n === refHead) break@outer
            ref = n
        }
        foundOut[0] = checkBest != null && refBest != null &&
            (refBest.span()?.segment() ?: this).match(refBest, checkBest.span()!!.segment()!!,
                checkBest.fT, checkBest.fPt)
        return true
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
         * Walk every span starting from [span] and reset the
         * visited flag on every segment in each span's pt-T loop.
         * Mirrors `SkOpSegment::ClearVisited` (`SkOpSegment.cpp:1140`).
         */
        fun ClearVisited(start: SkOpSpanBase) {
            var span: SkOpSpanBase? = start
            while (span != null) {
                var ptT: SkOpPtT? = span.ptT().next()
                val stopPtT = span.ptT()
                while (ptT != null && ptT !== stopPtT) {
                    val opp = ptT.span()?.segment()
                    opp?.resetVisited()
                    ptT = ptT.next()
                }
                span = if (span.final()) null else span.upCast().next()
            }
        }

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
         * `SkOpSegment::UseInnerWinding` (`SkOpSegment.cpp:1775`).
         *
         * Bug-fix slice (D1.2.c.2.c) : the original D1.2.c port had
         * the comparison flipped (`absOut > absIn` instead of
         * `absOut < absIn`). The new winding-query callers in this
         * slice surfaced the inversion ; fixed here.
         */
        fun UseInnerWinding(outerWinding: Int, innerWinding: Int): Boolean {
            // Upstream guards via SK_MaxS32 ; we don't have that
            // sentinel yet, so the only invariant we enforce is that
            // both inputs are real winding integers.
            val absOut = if (outerWinding < 0) -outerWinding else outerWinding
            val absIn = if (innerWinding < 0) -innerWinding else innerWinding
            return if (absOut == absIn) outerWinding < 0 else absOut < absIn
        }
    }
}
