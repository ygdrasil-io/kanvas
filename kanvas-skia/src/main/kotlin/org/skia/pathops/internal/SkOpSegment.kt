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
