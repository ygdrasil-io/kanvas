/*
 * Copyright 2026 The kanvas-skia authors.
 *
 * Mirrors Skia's `class SkOpContour` + `class SkOpContourHead` +
 * `class SkOpContourBuilder` from `src/pathops/SkOpContour.{h,cpp}`.
 *
 * Phase D1.2.e — data model + linked-list / segment-list management
 * + bounds + the simple builder that feeds segments from path verbs.
 *
 * What ships :
 *  - SkOpContour : segment-list owner with fHead (always-present
 *    initial slot), fTail, fNext, fBounds, fCount, fCcw, fDone,
 *    fOperand, fReverse, fXor, fOppXor.
 *  - SkOpContourHead : SkOpContour subclass with `appendContour` /
 *    `joinAllSegments` / `remove`.
 *  - SkOpContourBuilder : addLine / addQuad / addCubic / addConic /
 *    addCurve / flush. The "opposite line cancels both" optimization
 *    is preserved.
 *
 * Methods that depend on the SkOpSegment algorithm (calcAngles /
 *   sortAngles / missingCoincidence / moveMultiples / moveNearby /
 *   markAllDone / toPath / toReversePath / toPartialBackward /
 *   toPartialForward / undoneSpan / findSortableTop / rayCheck +
 *   the SkPathWriter dependency) are deferred to D1.2.h / D1.2.i.
 *
 * `joinSegments` IS shipped since `SkOpSegment.joinEnds` is from D1.2.c.
 */
package org.skia.pathops.internal

import org.skia.math.SkPoint
import org.skia.math.SkRect

internal open class SkOpContour : Comparable<SkOpContour> {

    /**
     * Always-present initial segment slot. Filled in by the first
     * `addLine` / `addQuad` / etc. call (matches upstream's pattern
     * where `fHead` is a value, not a pointer).
     */
    val fHead: SkOpSegment = SkOpSegment()

    /** Last segment in the list. Null when [fCount] == 0. */
    var fTail: SkOpSegment? = null

    /** Next contour in the linked contour list. */
    var fNext: SkOpContour? = null

    /** Union of all segment bounds. */
    var fBounds: SkRect = SkRect.MakeEmpty()

    /** -1 = not yet set ; 0 = clockwise ; 1 = counter-clockwise. */
    var fCcw: Int = -1

    /** Number of segments. */
    var fCount: Int = 0

    /** Set by the find-top algorithm (D1.2.h). */
    var fDone: Boolean = false

    /** True for the second argument of a binary operator. */
    var fOperand: Boolean = false

    /** True if this contour should be reversed when written back. */
    var fReverse: Boolean = false

    /** True if the original path had even-odd fill. */
    var fXor: Boolean = false

    /** True if the opposite path had even-odd fill. */
    var fOppXor: Boolean = false

    /**
     * Back-reference to the per-op global state (arena allocator,
     * coincidence container, winding-failed flag, …). Currently only
     * the coincidence container is wired ; the rest land alongside
     * their first consumer. Plumbed by [setGlobalState] (typically
     * during the `SkPathOpsOp` setup) and read by
     * [SkOpSpanBase.globalState].
     */
    var fGlobalState: SkOpGlobalState? = null

    init { reset() }

    /** Mirrors `SkOpContour::globalState()` (`SkOpContour.h`). */
    fun globalState(): SkOpGlobalState? = fGlobalState
    fun setGlobalState(s: SkOpGlobalState?) { fGlobalState = s }

    fun reset() {
        fTail = null
        fNext = null
        fCount = 0
        fDone = false
        fBounds = SkRect.MakeEmpty()
    }

    /**
     * Per-contour state init. Mirrors `SkOpContour::init` (without the
     * `SkOpGlobalState` since we don't have one).
     */
    fun init(operand: Boolean, isXor: Boolean) {
        fOperand = operand
        fXor = isXor
    }

    // ─── Accessors ─────────────────────────────────────────────────

    fun bounds(): SkRect = fBounds
    fun count(): Int = fCount
    fun done(): Boolean = fDone
    fun next(): SkOpContour? = fNext
    fun operand(): Boolean = fOperand
    fun oppXor(): Boolean = fOppXor
    fun isCcw(): Int = fCcw
    fun isXor(): Boolean = fXor
    fun reversed(): Boolean = fReverse

    fun first(): SkOpSegment {
        require(fCount > 0) { "first() called on empty contour" }
        return fHead
    }

    /** First control point of the first segment. Mirrors `SkOpContour::start`. */
    fun start(): SkPoint = fHead.pts()[0]

    /** Last control point of the tail segment. Mirrors `SkOpContour::end`. */
    fun end(): SkPoint = fTail!!.lastPt()

    fun setCcw(ccw: Int) { fCcw = ccw }
    fun setNext(c: SkOpContour?) { fNext = c }
    fun setOperand(op: Boolean) { fOperand = op }
    fun setOppXor(x: Boolean) { fOppXor = x }
    fun setReverse() { fReverse = true }
    fun setXor(x: Boolean) { fXor = x }

    /** Mirrors `SkOpContour::resetReverse`. */
    fun resetReverse() {
        var next: SkOpContour? = this
        while (next != null) {
            if (next.count() != 0) {
                next.fCcw = -1
                next.fReverse = false
            }
            next = next.fNext
        }
    }

    // ─── Segment-list mutation ────────────────────────────────────

    /**
     * Allocate or reuse the initial slot. Returns the segment to
     * initialize via [SkOpSegment.addLine] / `addQuad` / etc.
     * Mirrors `SkOpContour::appendSegment`.
     */
    fun appendSegment(): SkOpSegment {
        val result = if (fCount == 0) fHead else SkOpSegment()
        ++fCount
        result.setPrev(fTail)
        fTail?.setNext(result)
        fTail = result
        return result
    }

    fun addLine(pts: Array<SkPoint>): SkOpSegment {
        require(pts.size >= 2 && pts[0] != pts[1])
        return appendSegment().addLine(pts, this)
    }

    fun addQuad(pts: Array<SkPoint>) {
        appendSegment().addQuad(pts, this)
    }

    fun addCubic(pts: Array<SkPoint>) {
        appendSegment().addCubic(pts, this)
    }

    fun addConic(pts: Array<SkPoint>, weight: Float) {
        appendSegment().addConic(pts, weight, this)
    }

    /**
     * Walk the segment list and chain `tail.ptT` ↔ `next.head.ptT`.
     * Closes the contour into a loop. Mirrors `SkOpContour::joinSegments`.
     */
    fun joinSegments() {
        var segment: SkOpSegment? = fHead
        while (segment != null) {
            val next = segment.next()
            segment.joinEnds(next ?: fHead)
            segment = next
        }
    }

    // ─── Bounds ───────────────────────────────────────────────────

    /**
     * Recompute [fBounds] as the union of all segment bounds. Caller
     * should invoke after the contour is fully populated.
     * Mirrors `SkOpContour::setBounds`.
     */
    fun setBounds() {
        require(fCount > 0)
        var seg: SkOpSegment? = fHead
        fBounds = seg!!.bounds()
        seg = seg.next()
        while (seg != null) {
            fBounds = unionRect(fBounds, seg.bounds())
            seg = seg.next()
        }
    }

    /** Mirrors `SkOpContour::complete`. */
    fun complete() { setBounds() }

    // ─── Per-segment driver wrappers (D1.2.h.1) ────────────────────

    /**
     * Walk every segment and call [SkOpSegment.calcAngles]. Mirrors
     * `SkOpContour::calcAngles` (`SkOpContour.h:72`). Used as a
     * pre-pass during pathops' "fix coincidence" phase.
     */
    fun calcAngles() {
        require(fCount > 0)
        var seg: SkOpSegment? = fHead
        while (seg != null) {
            seg.calcAngles()
            seg = seg.next()
        }
    }

    /**
     * Walk every segment and call [SkOpSegment.sortAngles]. Returns
     * `false` on the first segment that fails to sort. Mirrors
     * `SkOpContour::sortAngles` (`SkOpContour.h:351`).
     */
    fun sortAngles(): Boolean {
        require(fCount > 0)
        var seg: SkOpSegment? = fHead
        while (seg != null) {
            if (!seg.sortAngles()) return false
            seg = seg.next()
        }
        return true
    }

    // ─── Comparable ───────────────────────────────────────────────

    /** Order by `bounds.top` ascending, ties broken by `bounds.left`. */
    override fun compareTo(other: SkOpContour): Int {
        val topCmp = fBounds.top.compareTo(other.fBounds.top)
        return if (topCmp != 0) topCmp else fBounds.left.compareTo(other.fBounds.left)
    }

    companion object {
        private fun unionRect(a: SkRect, b: SkRect): SkRect = SkRect(
            minOf(a.left, b.left), minOf(a.top, b.top),
            maxOf(a.right, b.right), maxOf(a.bottom, b.bottom),
        )
    }
}

/**
 * Subclass that manages the linked list of contours — adds new
 * contours, joins all segments, and removes the (empty) tail.
 * Mirrors `SkOpContourHead`.
 */
internal class SkOpContourHead : SkOpContour() {

    /**
     * Append a fresh empty contour to the tail of the list. Returns
     * the new contour. Mirrors `SkOpContourHead::appendContour`.
     */
    fun appendContour(): SkOpContour {
        val contour = SkOpContour()
        contour.setNext(null)
        var prev: SkOpContour = this
        while (true) {
            val next = prev.next() ?: break
            prev = next
        }
        prev.setNext(contour)
        return contour
    }

    /**
     * Walk every contour and call [SkOpContour.joinSegments] on each
     * non-empty one. Mirrors `SkOpContourHead::joinAllSegments`.
     */
    fun joinAllSegments() {
        var next: SkOpContour? = this
        while (next != null) {
            if (next.count() != 0) next.joinSegments()
            next = next.next()
        }
    }

    /**
     * Remove [contour] from the linked list. Asserts contour is the
     * tail (its `next == null`). Mirrors `SkOpContourHead::remove`.
     */
    fun remove(contour: SkOpContour) {
        if (contour === this) {
            require(count() == 0) { "head removal requires empty count" }
            return
        }
        require(contour.next() == null) { "remove() expects the tail contour" }
        var prev: SkOpContour = this
        while (true) {
            val next = prev.next()
            require(next != null) { "contour not found in list" }
            if (next === contour) break
            prev = next
        }
        prev.setNext(null)
    }
}

/**
 * Buffers the most recent line so consecutive forward+backward lines
 * cancel out (very common in stroke-path output). Other verbs flush
 * the buffer first. Mirrors `SkOpContourBuilder`.
 */
internal class SkOpContourBuilder(private var fContour: SkOpContour) {

    private val fLastLine: Array<SkPoint> = arrayOf(SkPoint(), SkPoint())
    private var fLastIsLine: Boolean = false

    fun contour(): SkOpContour = fContour

    fun setContour(contour: SkOpContour) {
        flush()
        fContour = contour
    }

    fun addConic(pts: Array<SkPoint>, weight: Float) {
        flush()
        fContour.addConic(pts, weight)
    }

    fun addCubic(pts: Array<SkPoint>) {
        flush()
        fContour.addCubic(pts)
    }

    fun addQuad(pts: Array<SkPoint>) {
        flush()
        fContour.addQuad(pts)
    }

    /**
     * Add a line — but if the previous buffered line is the *exact
     * opposite*, cancel both. Mirrors `SkOpContourBuilder::addLine`.
     */
    fun addLine(pts: Array<SkPoint>) {
        require(pts.size >= 2)
        if (fLastIsLine) {
            if (fLastLine[0] == pts[1] && fLastLine[1] == pts[0]) {
                fLastIsLine = false
                return
            }
            flush()
        }
        fLastLine[0] = pts[0]
        fLastLine[1] = pts[1]
        fLastIsLine = true
    }

    /**
     * Verb-based dispatcher. Used by SkOpEdgeBuilder (D1.2.f) when
     * walking SkPath verbs.
     */
    fun addCurve(verb: SkOpSegment.SegVerb, pts: Array<SkPoint>, weight: Float = 1f) {
        when (verb) {
            SkOpSegment.SegVerb.kLine -> addLine(arrayOf(pts[0], pts[1]))
            SkOpSegment.SegVerb.kQuad -> addQuad(arrayOf(pts[0], pts[1], pts[2]))
            SkOpSegment.SegVerb.kConic -> addConic(arrayOf(pts[0], pts[1], pts[2]), weight)
            SkOpSegment.SegVerb.kCubic -> addCubic(arrayOf(pts[0], pts[1], pts[2], pts[3]))
            SkOpSegment.SegVerb.kUnset -> error("unset verb in addCurve")
        }
    }

    /**
     * Push the buffered line (if any) to the underlying contour.
     * Mirrors `SkOpContourBuilder::flush`.
     */
    fun flush() {
        if (!fLastIsLine) return
        fContour.addLine(arrayOf(fLastLine[0], fLastLine[1]))
        fLastIsLine = false
    }
}
