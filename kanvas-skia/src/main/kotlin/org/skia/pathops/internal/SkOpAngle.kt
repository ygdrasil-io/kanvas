/*
 * Copyright 2026 The kanvas-skia authors.
 *
 * Mirrors Skia's `class SkOpAngle` from `src/pathops/SkOpAngle.{h,cpp}`.
 *
 * Phase D1.2.b — data model + linked-list ops + simple accessors.
 *
 * # What is an angle ?
 *
 * `SkOpAngle` is the radial-sort key used by the contour walker to
 * order outgoing segments at an intersection point. At each
 * intersection, multiple curve-pieces (some belonging to the
 * subject path, some to the operand) emanate radially ; the walker
 * needs to know "which piece comes next, going CCW from this one"
 * to assemble the correct boundary of the boolean result.
 *
 * Each `SkOpAngle` represents the curve from its `fStart` SpanBase to
 * its `fEnd` SpanBase ; instances are linked into a circular loop
 * (via [fNext]) sharing a common origin point. The loop is sorted
 * CCW by the [insert] method (D1.2.b.2 algorithm).
 *
 * Phase D1.2.b ports only the data fields, the constructor / set,
 * the linked-list helpers (`previous` / `loopContains` / `loopCount`),
 * and the trivial accessors. The CCW sort (`insert` / `after` /
 * `orderable` / `convexHullOverlaps` / `endsIntersect` / ~30 helpers
 * + `SkDCurve` / `SkDCurveSweep` dependencies) is deferred — landing
 * incrementally as the consumers (D1.2.c SkOpSegment) need it.
 *
 * Sector encoding (per upstream comment) :
 *  - The unit circle is divided into 32 sectors numbered 0..31.
 *  - `fSectorStart` / `fSectorEnd` are the start / end sectors of
 *    the angle ; `fSectorMask` is the bitmask of all covered sectors.
 *  - This pre-quantization speeds up the `oppositePlanes` /
 *    `convexHullOverlaps` reject path.
 */
package org.skia.pathops.internal

internal class SkOpAngle {

    /** Path-op include policy. Mirrors `SkOpAngle::IncludeType`. */
    enum class IncludeType { kUnaryWinding, kUnaryXor, kBinarySingle, kBinaryOpp }

    /** Start span of the curve segment this angle represents. */
    var fStart: SkOpSpanBase? = null

    /** End span (may equal `fComputedEnd` after `setSpans` aligns them). */
    var fEnd: SkOpSpanBase? = null

    /** Initially equal to `fEnd` ; may be overridden during `setSpans` (D1.2.b.2). */
    var fComputedEnd: SkOpSpanBase? = null

    /** Next angle in the radial-sorted CCW loop. Self-loop initially. */
    var fNext: SkOpAngle? = null

    /** Last marked span (used by the walker — D1.2.h). */
    var fLastMarked: SkOpSpanBase? = null

    /** Bitmask of sectors covered by this angle. */
    var fSectorMask: Int = 0

    /** Start sector of this angle in 1/32 of a circle (0..31). */
    var fSectorStart: Byte = -1

    /** End sector. */
    var fSectorEnd: Byte = -1

    /** True when the angle cannot be ordered (parallel / coincident with another). */
    var fUnorderable: Boolean = false

    /** Lazy sector-computation flag : set on construction, cleared after [computeSector]. */
    var fComputeSector: Boolean = false

    /** True once [computeSector] has run successfully. */
    var fComputedSector: Boolean = false

    /** True if this angle should re-check coincidence after sort (debug). */
    var fCheckCoincidence: Boolean = false

    /** Set when tangent-divergence math gives ambiguous magnitudes. */
    var fTangentsAmbiguous: Boolean = false

    // ─── Simple accessors ──────────────────────────────────────────

    fun start(): SkOpSpanBase? = fStart
    fun end(): SkOpSpanBase? = fEnd
    fun next(): SkOpAngle? = fNext
    fun lastMarked(): SkOpSpanBase? = fLastMarked
    fun setLastMarked(marked: SkOpSpanBase) { fLastMarked = marked }
    fun tangentsAmbiguous(): Boolean = fTangentsAmbiguous
    fun unorderable(): Boolean = fUnorderable

    /**
     * Owning segment — derived from `fStart.segment()`. Mirrors
     * `SkOpAngle::segment()`.
     */
    fun segment(): SkOpSegment? = fStart?.segment()

    /** Mirrors `SkOpAngle::starter` — returns `fStart.starter(fEnd)`. */
    fun starter(): SkOpSpan? = fStart?.starter(fEnd!!)

    // ─── Initialization ────────────────────────────────────────────

    /**
     * Initialize this angle to represent the curve segment from [start]
     * to [end]. `fNext` is reset to null (caller is responsible for
     * splicing into a sort loop via [insert] — D1.2.b.2).
     *
     * Mirrors the lifecycle prefix of `SkOpAngle::set` ; the
     * `setSpans` follow-on call (which projects the curve to compute
     * sectors + tangent half-line) is deferred to D1.2.b.2.
     */
    fun set(start: SkOpSpanBase, end: SkOpSpanBase) {
        require(start !== end)
        fStart = start
        fComputedEnd = end
        fEnd = end
        fNext = null
        fComputeSector = false
        fComputedSector = false
        fCheckCoincidence = false
        fTangentsAmbiguous = false
        // setSpans() lands in D1.2.b.2.
    }

    // ─── Linked-list helpers ──────────────────────────────────────

    /**
     * Walk the circular loop and return the angle whose `fNext` is
     * `this`. O(n) ; used by the sorter when an out-of-order pair is
     * detected. Mirrors `SkOpAngle::previous`.
     */
    fun previous(): SkOpAngle {
        var last = fNext!!
        while (true) {
            val next = last.fNext!!
            if (next === this) return last
            last = next
        }
    }

    /**
     * True iff [angle] is in this' loop AND its (start.segment, start.t,
     * end.t) tuple is the t-reversed mirror of one of the loop's entries.
     * Mirrors `SkOpAngle::loopContains`.
     */
    fun loopContains(angle: SkOpAngle): Boolean {
        if (fNext == null) return false
        val first: SkOpAngle = this
        var loop: SkOpAngle = this
        val tSegment = angle.fStart?.segment()
        val tStart = angle.fStart!!.t()
        val tEnd = angle.fEnd!!.t()
        do {
            val lSegment = loop.fStart?.segment()
            if (lSegment !== tSegment) {
                loop = loop.fNext ?: return false
                continue
            }
            val lStart = loop.fStart!!.t()
            if (lStart != tEnd) {
                loop = loop.fNext ?: return false
                continue
            }
            val lEnd = loop.fEnd!!.t()
            if (lEnd == tStart) return true
            loop = loop.fNext ?: return false
        } while (loop !== first)
        return false
    }

    /** Count entries in this' circular loop. Mirrors `SkOpAngle::loopCount`. */
    fun loopCount(): Int {
        var count = 0
        val first: SkOpAngle = this
        var next: SkOpAngle? = this
        do {
            next = next?.fNext
            ++count
        } while (next != null && next !== first)
        return count
    }

    // ─── Deferred (D1.2.b.2) ──────────────────────────────────────
    // - insert(other) : the CCW-sort splice
    // - after / orderable / convexHullOverlaps / endsIntersect
    // - checkParallel / checkCrossesZero / oppositePlanes
    // - endToSide / midToSide / lineOnOneSide / linesOnOriginalSide
    // - merge / midT / tangentsDiverge / alignmentSameSide
    // - findSector / setSector / computeSector / setSpans
    // - distEndRatio
    //
    // These all depend on SkDCurve / SkDCurveSweep (separate dispatcher
    // not yet ported) and on SkOpSegment.verb() / pts() / weight()
    // (land in D1.2.c). The data fields above are sufficient for
    // SkOpSpan / SkOpSegment to reference SkOpAngle by type.
}
