/*
 * Copyright 2026 The kanvas-skia authors.
 *
 * Mirrors Skia's `class SkOpAngle` from `src/pathops/SkOpAngle.{h,cpp}`.
 *
 * Phase D1.2.b — data model + linked-list ops + simple accessors.
 * Phase D1.2.b.2.a — geometry setup (`setSpans` / `setSector` /
 * `findSector` / `computeSector`).
 * Phase D1.2.b.2.b — comparison primitives (`oppositePlanes` /
 * `lineOnOneSide` / `linesOnOriginalSide` / `alignmentSameSide` /
 * `convexHullOverlaps` / `tangentsDiverge` / `distEndRatio` / `midT`).
 * Plus a fix to [checkCrossesZero] (the bit-twiddling was wrong in
 * D1.2.b.2.a — should be `end - start > 16`, not `start < 8 && end > 23`).
 * Phase D1.2.b.2.c — end-of-curve probes (`endToSide` / `midToSide` /
 * `checkParallel` / `endsIntersect`).
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

    /** Original (unmodified) curve segment from `fStart` to `fEnd`. */
    val fOriginalCurvePart: SkDCurve = SkDCurve()

    /** Working sweep / curve carrier — set by [setSpans]. */
    val fPart: SkDCurveSweep = SkDCurveSweep()

    /**
     * Sign-only "which side of the tangent the curve bends toward".
     * Set by [setSpans] for non-line verbs ; only the sign is consumed
     * (compare-only). Lines leave it as 0.
     */
    var fSide: Double = 0.0

    /**
     * Tangent half-line — used by [orderable] when both angles are
     * line-or-line-like. Mirrors `fTangentHalf` from upstream.
     */
    val fTangentHalf: SkLineParameters = SkLineParameters()

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
     * splicing into a sort loop via [insert] — D1.2.b.2.d). Also runs
     * [setSpans] + [setSector] to populate the geometric prep used by
     * the CCW comparators.
     *
     * Mirrors `SkOpAngle::set` (`SkOpAngle.cpp:973`).
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
        setSpans()
        setSector()
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

    // ─── Geometry setup (D1.2.b.2.a) ───────────────────────────────

    /**
     * Project the segment between [fStart] and [fEnd] into the working
     * carrier ([fPart]) and compute the tangent half-line + side sign
     * used by the CCW comparators. Mirrors `SkOpAngle::setSpans`
     * (`SkOpAngle.cpp:984`).
     *
     * For line / line-like (degenerate-curve) segments :
     *  - The tangent half-line is the line through the two endpoints.
     *  - [fSide] stays 0 — sign comparisons drop out.
     *
     * For quad / conic : `fSide` is `−tangent.pointDistance(curve[2])`
     * — the sign of the side the curve bends toward, normal-unscaled.
     *
     * For cubic : sample several `t` values bracketing inflection
     * points and pick the largest signed distance from the chord — gives
     * the most informative side sign even when the curve crosses its
     * own chord.
     */
    fun setSpans() {
        fUnorderable = false
        fLastMarked = null
        val start = fStart ?: run { fUnorderable = true; return }
        val segment = start.segment() ?: run { fUnorderable = true; return }
        // Guard against test fixtures that wrap an uninitialised segment :
        // upstream never hits this, but our tests build bare segments to
        // exercise the data-model API in isolation.
        if (segment.verb() == SkOpSegment.SegVerb.kUnset) { fUnorderable = true; return }
        // subDivide carriers into fPart.fCurve.
        segment.subDivide(start, fEnd!!, fPart.fCurve)
        fOriginalCurvePart.copyFrom(fPart.fCurve)
        val verb = segment.verb()
        fPart.setCurveHullSweep(verb)
        // Line-like : a curve whose hull collapsed to its endpoints. Set
        // up tangent[0..1] = (curve[0], curve[end]) so the line-tangent
        // path can sort it.
        if (verb != SkOpSegment.SegVerb.kLine && !fPart.isCurve()) {
            fPart.fCurve[1] = fPart.fCurve[segVerbToPoints(verb)]
            fOriginalCurvePart[1] = fPart.fCurve[1]
            val lineHalf = SkDLine().apply {
                set(fPart.fCurve[0].asSkPoint(), fPart.fCurve[1].asSkPoint())
            }
            fTangentHalf.lineEndPoints(lineHalf)
            fSide = 0.0
        }
        when (verb) {
            SkOpSegment.SegVerb.kLine -> {
                require(fStart !== fEnd)
                val pts = segment.pts()
                val cP1 = pts[if (start.t() < fEnd!!.t()) 1 else 0]
                val lineHalf = SkDLine().apply { set(start.pt(), cP1) }
                fTangentHalf.lineEndPoints(lineHalf)
                fSide = 0.0
            }
            SkOpSegment.SegVerb.kQuad,
            SkOpSegment.SegVerb.kConic -> {
                if (fPart.isCurve()) {
                    val tangentPart = SkLineParameters()
                    tangentPart.quadEndPoints(fPart.fCurve.asQuad())
                    fSide = -tangentPart.pointDistance(fPart.fCurve[2])
                }
            }
            SkOpSegment.SegVerb.kCubic -> {
                if (fPart.isCurve()) {
                    val tangentPart = SkLineParameters()
                    tangentPart.cubicPart(fPart.fCurve.asCubic())
                    fSide = -tangentPart.pointDistance(fPart.fCurve[3])
                    // Sample at points bracketing each inflection so a
                    // self-crossing cubic gets the most informative side.
                    val pts = segment.pts()
                    val testTs = DoubleArray(4)
                    var testCount = SkDCubic.FindInflections(pts, testTs)
                    val startT = start.t()
                    val endT = fEnd!!.t()
                    val limitT = endT
                    for (i in 0 until testCount) {
                        if (!between(startT, testTs[i], limitT)) testTs[i] = -1.0
                    }
                    testTs[testCount++] = startT
                    testTs[testCount++] = endT
                    val sortable = testTs.copyOfRange(0, testCount)
                    sortable.sort()
                    for (i in 0 until testCount) testTs[i] = sortable[i]
                    var bestSide = 0.0
                    val testCases = (testCount shl 1) - 1
                    var idx = 0
                    while (testTs[idx] < 0) idx++
                    idx = idx shl 1
                    val cubic = SkDCubic().apply { set(pts[0], pts[1], pts[2], pts[3]) }
                    while (idx < testCases) {
                        val testIndex = idx ushr 1
                        var testT = testTs[testIndex]
                        if ((idx and 1) == 1) testT = (testT + testTs[testIndex + 1]) / 2
                        val pt = cubic.ptAtT(testT)
                        val testPart = SkLineParameters()
                        testPart.cubicEndPoints(fPart.fCurve.asCubic())
                        val testSide = testPart.pointDistance(pt)
                        if (kotlin.math.abs(bestSide) < kotlin.math.abs(testSide)) {
                            bestSide = testSide
                        }
                        idx++
                    }
                    fSide = -bestSide
                }
            }
            SkOpSegment.SegVerb.kUnset -> error("verb not set")
        }
    }

    /**
     * Quantize the angle's sweep into 1/32-circle "sectors". Mirrors
     * `SkOpAngle::setSector` (`SkOpAngle.cpp:1074`).
     *
     * `fSectorStart` / `fSectorEnd` are in `0..31` (or -1 if deferred
     * to [computeSector]). `fSectorMask` is the bitmask of all sectors
     * spanned by the angle — used by the CCW reject path.
     *
     * The "exact compass point" trick : sectors with `(s & 3) == 3`
     * sit on `45°` boundaries. If both start + end land there, the
     * sector has zero width and can't act as a tie-breaker — we bump
     * it +1 or +31 (mod 32) into the curve's bend direction.
     */
    fun setSector() {
        val start = fStart ?: run { fUnorderable = true; return }
        val segment = start.segment() ?: run { fUnorderable = true; return }
        val verb = segment.verb()
        if (verb == SkOpSegment.SegVerb.kUnset) { fUnorderable = true; return }
        fSectorStart = findSector(verb, fPart.fSweep[0].x, fPart.fSweep[0].y).toByte()
        if (fSectorStart < 0) { deferTilLater(); return }
        if (!fPart.isCurve()) {
            fSectorEnd = fSectorStart
            fSectorMask = 1 shl fSectorStart.toInt()
            return
        }
        require(verb != SkOpSegment.SegVerb.kLine)
        fSectorEnd = findSector(verb, fPart.fSweep[1].x, fPart.fSweep[1].y).toByte()
        if (fSectorEnd < 0) { deferTilLater(); return }
        if (fSectorEnd == fSectorStart && (fSectorStart.toInt() and 3) != 3) {
            fSectorMask = 1 shl fSectorStart.toInt()
            return
        }
        var crossesZero = checkCrossesZero()
        var startMin = minOf(fSectorStart.toInt(), fSectorEnd.toInt())
        val curveBendsCCW = (fSectorStart.toInt() == startMin) xor crossesZero
        // Bump start / end off exact 45° boundaries.
        if ((fSectorStart.toInt() and 3) == 3) {
            fSectorStart = ((fSectorStart.toInt() + (if (curveBendsCCW) 1 else 31)) and 0x1f).toByte()
        }
        if ((fSectorEnd.toInt() and 3) == 3) {
            fSectorEnd = ((fSectorEnd.toInt() + (if (curveBendsCCW) 31 else 1)) and 0x1f).toByte()
        }
        crossesZero = checkCrossesZero()
        startMin = minOf(fSectorStart.toInt(), fSectorEnd.toInt())
        val end = maxOf(fSectorStart.toInt(), fSectorEnd.toInt())
        fSectorMask = if (!crossesZero) {
            // Ascending span [start, end].
            ((-1).toLong() and 0xffffffffL).toInt() ushr (31 - end + startMin) shl startMin
        } else {
            // Wraps through 0 : two halves.
            (((-1).toLong() and 0xffffffffL).toInt() ushr (31 - startMin)) or
                (((-1).toLong() and 0xffffffffL).toInt() shl end)
        }
    }

    private fun deferTilLater() {
        fSectorStart = -1; fSectorEnd = -1; fSectorMask = 0
        fComputeSector = true
    }

    /**
     * Quantize a sweep vector `(x, y)` into a sector in `0..31`, or -1
     * if the vector is exactly zero (curve too small to classify).
     * Mirrors `SkOpAngle::findSector` (`SkOpAngle.cpp:723`).
     *
     * The 32-sector partition is :
     *  - 8 quadrants (per sign of x, sign of y, |x|<|y| / |x|=|y| /
     *    |x|>|y|), each split into 2 half-sectors → 16 "sedecimants" ;
     *  - returns `2 * sedecimant + 1` so the values are odd 0..31.
     */
    fun findSector(verb: SkOpSegment.SegVerb, x: Double, y: Double): Int {
        val absX = kotlin.math.abs(x)
        val absY = kotlin.math.abs(y)
        val xy = if (verb == SkOpSegment.SegVerb.kLine || !AlmostEqualUlps(absX, absY)) absX - absY else 0.0
        // First index (0/1/2) : |x|<|y| / |x|=|y| / |x|>|y|.
        // Second index : sign of y. Third : sign of x.
        val sedecimant = arrayOf(
            // |x| < |y|
            arrayOf(intArrayOf( 4,  3,  2), intArrayOf( 7, -1, 15), intArrayOf(10, 11, 12)),
            // |x| == |y|
            arrayOf(intArrayOf( 5, -1,  1), intArrayOf(-1, -1, -1), intArrayOf( 9, -1, 13)),
            // |x| > |y|
            arrayOf(intArrayOf( 6,  3,  0), intArrayOf( 7, -1, 15), intArrayOf( 8, 11, 14)),
        )
        val xyIdx = (if (xy >= 0) 1 else 0) + (if (xy > 0) 1 else 0)
        val yIdx = (if (y >= 0) 1 else 0) + (if (y > 0) 1 else 0)
        val xIdx = (if (x >= 0) 1 else 0) + (if (x > 0) 1 else 0)
        val s = sedecimant[xyIdx][yIdx][xIdx]
        return if (s < 0) -1 else s * 2 + 1
    }

    /**
     * True if the angle's sweep crosses the +x axis (sector 0 / 31
     * boundary). The sectors `(31 → 0)` adjacency requires special
     * handling in [setSector]'s mask-build step. Mirrors
     * `SkOpAngle::checkCrossesZero` (`SkOpAngle.cpp:343`).
     *
     * The criterion is "the gap between min/max sectors exceeds 16" —
     * meaning the wrap-around path is *shorter* than the direct path.
     */
    fun checkCrossesZero(): Boolean {
        val start = minOf(fSectorStart.toInt(), fSectorEnd.toInt())
        val end = maxOf(fSectorStart.toInt(), fSectorEnd.toInt())
        return end - start > 16
    }

    /**
     * Recompute [fSectorStart] / [fSectorEnd] when the eager
     * computation in [setSpans] / [setSector] couldn't classify the
     * sweep. Walks coincident spans on the same segment to find a
     * nearby span at which the sector is computable, replays
     * [setSpans] + [setSector] there, then restores `fEnd`.
     *
     * Mirrors `SkOpAngle::computeSector` (`SkOpAngle.cpp:401`).
     */
    fun computeSector(): Boolean {
        if (fComputedSector) return !fUnorderable
        fComputedSector = true
        val start = fStart ?: run { fUnorderable = true; return false }
        val end0 = fEnd ?: run { fUnorderable = true; return false }
        val stepUp = start.t() < end0.t()
        var checkEnd: SkOpSpanBase? = end0
        if (checkEnd!!.final() && stepUp) { fUnorderable = true; return false }
        // Walk same-segment coincident spans until we find one whose
        // sector is computable (or run out of options).
        recompute@ while (checkEnd != null) {
            val other = checkEnd.segment() ?: run { fUnorderable = true; return false }
            var oSpan: SkOpSpanBase? = other.head()
            var found = false
            while (oSpan != null) {
                val osSegment = oSpan.segment()
                val mySegment = segment()
                val sameSegment = osSegment === mySegment
                val notCheckEnd = oSpan !== checkEnd
                val tEqual = approximately_equal(oSpan.t(), checkEnd.t())
                if (sameSegment && notCheckEnd && tEqual) { found = true; break }
                oSpan = if (oSpan.final()) null else oSpan.upCast().next()
            }
            if (found) break@recompute
            checkEnd = if (stepUp) {
                if (!checkEnd.final()) checkEnd.upCast().next() else null
            } else {
                checkEnd.prev()
            }
        }
        val computedEnd: SkOpSpanBase? = if (stepUp) {
            if (checkEnd != null) checkEnd.prev() else fEnd!!.segment()!!.head()
        } else {
            if (checkEnd != null) checkEnd.upCast().next() else fEnd!!.segment()!!.tail()
        }
        if (checkEnd === fEnd || computedEnd === fEnd || computedEnd === fStart) {
            fUnorderable = true; return false
        }
        if (computedEnd == null) { fUnorderable = true; return false }
        if (stepUp != (start.t() < computedEnd.t())) { fUnorderable = true; return false }
        val saveEnd = fEnd
        fComputedEnd = computedEnd
        fEnd = computedEnd
        setSpans()
        setSector()
        fEnd = saveEnd
        return !fUnorderable
    }

    // ─── Comparison primitives (D1.2.b.2.b) ────────────────────────

    /** Mid-T of the angle's parameter range. Mirrors `SkOpAngle::midT`. */
    fun midT(): Double = (fStart!!.t() + fEnd!!.t()) / 2

    /**
     * True iff this angle and [rh] are at least 8 sectors apart at
     * their start. Used by [endsIntersect] / [orderable] as a quick
     * "they're nowhere near each other" reject. Mirrors
     * `SkOpAngle::oppositePlanes`.
     */
    fun oppositePlanes(rh: SkOpAngle): Boolean {
        val startSpan = kotlin.math.abs(rh.fSectorStart - fSectorStart)
        return startSpan >= 8
    }

    /**
     * Ratio of the segment's longest control-pair length to [dist].
     * Used by [tangentsDiverge] to scale the displacement test. Mirrors
     * `SkOpAngle::distEndRatio`.
     */
    fun distEndRatio(dist: Double): Double {
        var longest = 0.0
        val seg = segment()!!
        val ptCount = segVerbToPoints(seg.verb())
        val pts = seg.pts()
        for (i in 0..ptCount - 1) {
            for (j in i + 1..ptCount) {
                val dx = (pts[j].fX - pts[i].fX).toDouble()
                val dy = (pts[j].fY - pts[i].fY).toDouble()
                val lenSq = dx * dx + dy * dy
                if (lenSq > longest) longest = lenSq
            }
        }
        return kotlin.math.sqrt(longest) / dist
    }

    /**
     * Core line-vs-curve-hull check. Returns :
     *  - 0  : test's hull is on the CW side of the line
     *  - 1  : test's hull is on the CCW side of the line
     *  - -1 : hull straddles the line (or is on it ambiguously)
     *  - -2 : all crosses are zero (caller treats as unorderable)
     *
     * Mirrors the upstream private `lineOnOneSide` overload.
     */
    fun lineOnOneSide(
        origin: SkDPoint,
        line: SkDVector,
        test: SkOpAngle,
        useOriginal: Boolean,
    ): Int {
        val crosses = DoubleArray(3)
        val testVerb = test.segment()!!.verb()
        val iMax = segVerbToPoints(testVerb)
        val testCurve = if (useOriginal) test.fOriginalCurvePart else test.fPart.fCurve
        for (index in 1..iMax) {
            val xy1 = line.x * (testCurve[index].y - origin.y)
            val xy2 = line.y * (testCurve[index].x - origin.x)
            crosses[index - 1] = if (AlmostBequalUlps(xy1, xy2)) 0.0 else xy1 - xy2
        }
        if (crosses[0] * crosses[1] < 0) return -1
        if (testVerb == SkOpSegment.SegVerb.kCubic) {
            if (crosses[0] * crosses[2] < 0 || crosses[1] * crosses[2] < 0) return -1
        }
        if (crosses[0] != 0.0) return if (crosses[0] < 0) 1 else 0
        if (crosses[1] != 0.0) return if (crosses[1] < 0) 1 else 0
        if (testVerb == SkOpSegment.SegVerb.kCubic && crosses[2] != 0.0) {
            return if (crosses[2] < 0) 1 else 0
        }
        return -2
    }

    /**
     * Line-vs-curve-hull check on the line variant of `this` against
     * the curve [test]. Sets [fUnorderable] when all crosses are zero
     * (returns -1 in that case). Mirrors the upstream non-`const` overload.
     */
    fun lineOnOneSide(test: SkOpAngle, useOriginal: Boolean): Int {
        require(!fPart.isCurve())
        require(test.fPart.isCurve())
        val origin = fPart.fCurve[0]
        val line = fPart.fCurve[1] - origin
        var result = lineOnOneSide(origin, line, test, useOriginal)
        if (result == -2) {
            fUnorderable = true
            result = -1
        }
        return result
    }

    /**
     * Compare two line-only angles using their *original* (un-translated)
     * sweep vectors. Returns :
     *  - 0 / 1 : standard CW / CCW order
     *  - 2     : exactly 180° apart (caller handles separately)
     *  - -1    : crossing or unorderable
     *
     * Mirrors `SkOpAngle::linesOnOriginalSide`.
     */
    fun linesOnOriginalSide(test: SkOpAngle): Int {
        require(!fPart.isCurve())
        require(!test.fPart.isCurve())
        val origin = fOriginalCurvePart[0]
        val line = fOriginalCurvePart[1] - origin
        val dots = DoubleArray(2)
        val crosses = DoubleArray(2)
        val testCurve = test.fOriginalCurvePart
        for (index in 0..1) {
            val testLine = testCurve[index] - origin
            val xy1 = line.x * testLine.y
            val xy2 = line.y * testLine.x
            dots[index] = line.x * testLine.x + line.y * testLine.y
            crosses[index] = if (AlmostBequalUlps(xy1, xy2)) 0.0 else xy1 - xy2
        }
        if (crosses[0] * crosses[1] < 0) return -1
        if (crosses[0] != 0.0) return if (crosses[0] < 0) 1 else 0
        if (crosses[1] != 0.0) return if (crosses[1] < 0) 1 else 0
        if ((dots[0] == 0.0 && dots[1] < 0) || (dots[0] < 0 && dots[1] == 0.0)) {
            return 2
        }
        fUnorderable = true
        return -1
    }

    /**
     * Re-orient [order] (0 / 1 / -1) when the curves' translation to a
     * common origin would flip a control point's side relative to the
     * other curve's chord. Pure side-effect on [order]. Mirrors
     * `SkOpAngle::alignmentSameSide`.
     */
    fun alignmentSameSide(test: SkOpAngle, order: IntArray) {
        if (order[0] < 0) return
        // Upstream comment : applying this to curves causes existing tests to
        // fail ; line-only is sufficient.
        if (fPart.isCurve()) return
        if (test.fPart.isCurve()) return
        val xOrigin = test.fPart.fCurve[0]
        val oOrigin = test.fOriginalCurvePart[0]
        if (xOrigin == oOrigin) return
        val iMax = segVerbToPoints(segment()!!.verb())
        val xLine = test.fPart.fCurve[1] - xOrigin
        val oLine = test.fOriginalCurvePart[1] - oOrigin
        for (index in 1..iMax) {
            val testPt = fPart.fCurve[index]
            val xCross = oLine.crossCheck(testPt - xOrigin)
            val oCross = xLine.crossCheck(testPt - oOrigin)
            if (oCross * xCross < 0) {
                order[0] = order[0] xor 1
                break
            }
        }
    }

    /**
     * True iff the tangent at this and [rh]'s start "diverge enough"
     * (the perpendicular displacement that aligns them is large enough
     * relative to the segment lengths). Mirrors
     * `SkOpAngle::tangentsDiverge`.
     */
    fun tangentsDiverge(rh: SkOpAngle, s0xt0: Double): Boolean {
        if (s0xt0 == 0.0) return false
        val sweep = fPart.fSweep
        val tweep = rh.fPart.fSweep
        val s0dt0 = sweep[0].dot(tweep[0])
        if (s0dt0 == 0.0) return true
        val m = s0xt0 / s0dt0
        val sDist = sweep[0].length() * m
        val tDist = tweep[0].length() * m
        val useS = kotlin.math.abs(sDist) < kotlin.math.abs(tDist)
        val mFactor = kotlin.math.abs(if (useS) distEndRatio(sDist) else rh.distEndRatio(tDist))
        fTangentsAmbiguous = mFactor in 50.0..200.0
        return mFactor < 50  // empirically found limit
    }

    /**
     * Compare two curve hulls by their sweep vectors. Returns :
     *  - 0 / 1 : standard CW / CCW order
     *  - -1    : hulls overlap — caller falls through to `endsIntersect`
     *
     * Mirrors `SkOpAngle::convexHullOverlaps`.
     */
    fun convexHullOverlaps(rh: SkOpAngle): Int {
        val sweep = fPart.fSweep
        val tweep = rh.fPart.fSweep
        val s0xs1 = sweep[0].crossCheck(sweep[1])
        val s0xt0 = sweep[0].crossCheck(tweep[0])
        val s1xt0 = sweep[1].crossCheck(tweep[0])
        var tBetweenS = if (s0xs1 > 0) (s0xt0 > 0 && s1xt0 < 0) else (s0xt0 < 0 && s1xt0 > 0)
        val s0xt1 = sweep[0].crossCheck(tweep[1])
        val s1xt1 = sweep[1].crossCheck(tweep[1])
        tBetweenS = tBetweenS || (if (s0xs1 > 0) (s0xt1 > 0 && s1xt1 < 0) else (s0xt1 < 0 && s1xt1 > 0))
        val t0xt1 = tweep[0].crossCheck(tweep[1])
        if (tBetweenS) return -1
        if ((s0xt0 == 0.0 && s1xt1 == 0.0) || (s1xt0 == 0.0 && s0xt1 == 0.0)) return -1
        var sBetweenT = if (t0xt1 > 0) (s0xt0 < 0 && s0xt1 > 0) else (s0xt0 > 0 && s0xt1 < 0)
        sBetweenT = sBetweenT || (if (t0xt1 > 0) (s1xt0 < 0 && s1xt1 > 0) else (s1xt0 > 0 && s1xt1 < 0))
        if (sBetweenT) return -1
        // Same half-plane → first pair's order is enough.
        if (s0xt0 >= 0 && s0xt1 >= 0 && s1xt0 >= 0 && s1xt1 >= 0) return 0
        if (s0xt0 <= 0 && s0xt1 <= 0 && s1xt0 <= 0 && s1xt1 <= 0) return 1
        // Outside sweeps span > 180° : check midpoint direction first,
        // then divergence as a last resort.
        val m0 = segment()!!.dPtAtT(midT()) - fPart.fCurve[0]
        val m1 = rh.segment()!!.dPtAtT(rh.midT()) - rh.fPart.fCurve[0]
        val m0xm1 = m0.crossCheck(m1)
        if (s0xt0 > 0 && m0xm1 > 0) return 0
        if (s0xt0 < 0 && m0xm1 < 0) return 1
        if (tangentsDiverge(rh, s0xt0)) return if (s0xt0 < 0) 1 else 0
        return if (m0xm1 < 0) 1 else 0
    }

    // ─── End-of-curve probes (D1.2.b.2.c) ──────────────────────────

    /**
     * Drop a perpendicular ray from the angle's [fEnd] point into the
     * other angle's curve and check which side of the original chord
     * the closest crossing falls on. Mirrors `SkOpAngle::endToSide`.
     *
     * Writes the side bit into [inside]`[0]`. Returns `true` when the
     * comparison is conclusive ; returns `false` (with [inside]
     * unchanged) when the perpendicular doesn't cleanly cross or the
     * crossing is too close to the chord's endpoint.
     */
    fun endToSide(rh: SkOpAngle, inside: BooleanArray): Boolean {
        val segment = segment()!!
        val rayEnd = SkDLine()
        rayEnd[0] = SkDPoint().apply { set(fEnd!!.pt()) }
        rayEnd[1] = SkDPoint(rayEnd[0].x, rayEnd[0].y)
        val slopeAtEnd = segment.dSlopeAtT(fEnd!!.t())
        rayEnd[1].x += slopeAtEnd.y
        rayEnd[1].y -= slopeAtEnd.x
        val iEnd = SkIntersections()
        val oppSegment = rh.segment()!!
        oppSegment.intersectRay(rayEnd, iEnd)
        val endDistOut = DoubleArray(1)
        val closestEnd = iEnd.closestTo(rh.fStart!!.t(), rh.fEnd!!.t(), rayEnd[0], endDistOut)
        if (closestEnd < 0) return false
        var endDist = endDistOut[0]
        if (endDist == 0.0) return false
        val start = SkDPoint().apply { set(fStart!!.pt()) }
        // Curve bbox of rh.fPart.fCurve.
        var minX = Double.POSITIVE_INFINITY; var minY = Double.POSITIVE_INFINITY
        var maxX = Double.NEGATIVE_INFINITY; var maxY = Double.NEGATIVE_INFINITY
        val curve = rh.fPart.fCurve
        val oppPts = segVerbToPoints(oppSegment.verb())
        for (i in 0..oppPts) {
            minX = minOf(minX, curve[i].x); minY = minOf(minY, curve[i].y)
            maxX = maxOf(maxX, curve[i].x); maxY = maxOf(maxY, curve[i].y)
        }
        val maxWidth = maxOf(maxX - minX, maxY - minY)
        endDist = if (maxWidth == 0.0) Double.NaN else endDist / maxWidth
        // The `!(x >= 5e-12)` form catches NaN.
        if (!(endDist >= 5e-12)) return false
        val endPt = rayEnd[0]
        val oppPt = iEnd.pt(closestEnd)
        val vLeft = endPt - start
        val vRight = oppPt - start
        val dir = vLeft.crossNoNormalCheck(vRight)
        if (dir == 0.0) return false
        inside[0] = dir < 0
        return true
    }

    /**
     * Drop a perpendicular ray from the angle's chord midpoint and
     * compare the "outside" intersection on each curve. Mirrors
     * `SkOpAngle::midToSide`.
     *
     * Returns `true` with [inside]`[0]` set when the comparison is
     * conclusive ; `false` when the rays miss or the crossings are
     * inconclusive.
     */
    fun midToSide(rh: SkOpAngle, inside: BooleanArray): Boolean {
        val segment = segment()!!
        val startPt = fStart!!.pt()
        val endPt = fEnd!!.pt()
        val dStartPt = SkDPoint().apply { set(startPt) }
        val rayMid = SkDLine()
        rayMid[0] = SkDPoint(
            (startPt.fX + endPt.fX) / 2.0,
            (startPt.fY + endPt.fY) / 2.0,
        )
        rayMid[1] = SkDPoint(
            rayMid[0].x + (endPt.fY - startPt.fY).toDouble(),
            rayMid[0].y - (endPt.fX - startPt.fX).toDouble(),
        )
        val iMid = SkIntersections()
        segment.intersectRay(rayMid, iMid)
        val iOutside = iMid.mostOutside(fStart!!.t(), fEnd!!.t(), dStartPt)
        if (iOutside < 0) return false
        val oppSegment = rh.segment()!!
        val oppMid = SkIntersections()
        oppSegment.intersectRay(rayMid, oppMid)
        val oppOutside = oppMid.mostOutside(rh.fStart!!.t(), rh.fEnd!!.t(), dStartPt)
        if (oppOutside < 0) return false
        val iSide = iMid.pt(iOutside) - dStartPt
        val oppSide = oppMid.pt(oppOutside) - dStartPt
        val dir = iSide.crossCheck(oppSide)
        if (dir == 0.0) return false
        inside[0] = dir < 0
        return true
    }

    /**
     * Last-resort comparison when sweeps appear parallel. Mirrors
     * `SkOpAngle::checkParallel`. Returns `true` if `this` sorts after
     * [rh] in the CCW order, `false` if before. May set
     * [fUnorderable] (and [rh].fUnorderable) when the mid-T cross
     * underflows to zero.
     */
    fun checkParallel(rh: SkOpAngle): Boolean {
        val scratch = arrayOf(SkDVector(0.0, 0.0), SkDVector(0.0, 0.0))
        val sweep: Array<SkDVector>
        val tweep: Array<SkDVector>
        if (fPart.isOrdered()) {
            sweep = fPart.fSweep
        } else {
            scratch[0] = fPart.fCurve[1] - fPart.fCurve[0]
            sweep = arrayOf(scratch[0], scratch[0])
        }
        if (rh.fPart.isOrdered()) {
            tweep = rh.fPart.fSweep
        } else {
            scratch[1] = rh.fPart.fCurve[1] - rh.fPart.fCurve[0]
            tweep = arrayOf(scratch[1], scratch[1])
        }
        val s0xt0 = sweep[0].crossCheck(tweep[0])
        if (tangentsDiverge(rh, s0xt0)) return s0xt0 < 0
        val inside = BooleanArray(1)
        if (!fEnd!!.contains(rh.fEnd!!)) {
            if (endToSide(rh, inside)) return inside[0]
            if (rh.endToSide(this, inside)) return !inside[0]
        }
        if (midToSide(rh, inside)) return inside[0]
        if (rh.midToSide(this, inside)) return !inside[0]
        // Last-ditch : mid-T cross-check.
        val m0 = segment()!!.dPtAtT(midT()) - fPart.fCurve[0]
        val m1 = rh.segment()!!.dPtAtT(rh.midT()) - rh.fPart.fCurve[0]
        val m0xm1 = m0.crossCheck(m1)
        if (m0xm1 == 0.0) {
            fUnorderable = true; rh.fUnorderable = true
            return true
        }
        return m0xm1 < 0
    }

    /**
     * Mirrors `SkOpAngle::endsIntersect`. The big driver behind
     * [orderable] for the curve-vs-curve case : projects each angle's
     * "tangent ray" through the other curve and decides which side the
     * intersection lands on.
     *
     * Returns `true` when `this` sorts after [rh], `false` otherwise.
     */
    fun endsIntersect(rh: SkOpAngle): Boolean {
        val lVerb = segment()!!.verb()
        val rVerb = rh.segment()!!.verb()
        val lPts = segVerbToPoints(lVerb)
        val rPts = segVerbToPoints(rVerb)
        val rays = arrayOf(
            SkDLine(arrayOf(fPart.fCurve[0], rh.fPart.fCurve[rPts])),
            SkDLine(arrayOf(fPart.fCurve[0], fPart.fCurve[lPts])),
        )
        if (fEnd!!.contains(rh.fEnd!!)) return checkParallel(rh)
        val smallTs = doubleArrayOf(-1.0, -1.0)
        val limited = booleanArrayOf(false, false)
        for (index in 0..1) {
            val cVerb = if (index == 1) rVerb else lVerb
            // Ray vs line is just their direct crossing — skip.
            if (cVerb == SkOpSegment.SegVerb.kLine) continue
            val seg = if (index == 1) rh.segment()!! else segment()!!
            val ix = SkIntersections()
            seg.intersectRay(rays[index], ix)
            val tStart = if (index == 1) rh.fStart!!.t() else fStart!!.t()
            val tEnd = if (index == 1) rh.fComputedEnd!!.t() else fComputedEnd!!.t()
            val testAscends = tStart < tEnd
            var t = if (testAscends) 0.0 else 1.0
            for (idx2 in 0 until ix.used()) {
                val testT = ix.t(0, idx2)
                if (!approximately_between_orderable(tStart, testT, tEnd)) continue
                if (approximately_equal_orderable(tStart, testT)) continue
                t = if (testAscends) maxOf(t, testT) else minOf(t, testT)
                smallTs[index] = t
                limited[index] = approximately_equal_orderable(t, tEnd)
            }
        }
        var sRayLonger = false
        var sCept = SkDVector(0.0, 0.0)
        var sCeptT = -1.0
        var sIndex = -1
        var useIntersect = false
        for (index in 0..1) {
            if (smallTs[index] < 0) continue
            val seg = if (index == 1) rh.segment()!! else segment()!!
            val dPt = seg.dPtAtT(smallTs[index])
            val cept = dPt - rays[index][0]
            // If the curve on this side is a line, drop hits whose ray
            // length is < half the chord (would have been caught by the
            // ordinary line-line crossing).
            if ((if (index == 1) lPts else rPts) == 1) {
                val total = rays[index][1] - rays[index][0]
                if (cept.lengthSquared() * 2 < total.lengthSquared()) continue
            }
            val end = rays[index][1] - rays[index][0]
            if (cept.x * end.x < 0 || cept.y * end.y < 0) continue
            val rayDist = cept.length()
            val endDist = end.length()
            val rayLonger = rayDist > endDist
            if (limited[0] && limited[1] && rayLonger) {
                useIntersect = true
                sRayLonger = rayLonger
                sCept = cept
                sCeptT = smallTs[index]
                sIndex = index
                break
            }
            var delta = kotlin.math.abs(rayDist - endDist)
            // Curve bbox to scale delta.
            var minX = Double.POSITIVE_INFINITY; var minY = Double.POSITIVE_INFINITY
            var maxX = Double.NEGATIVE_INFINITY; var maxY = Double.NEGATIVE_INFINITY
            val curve = if (index == 1) rh.fPart.fCurve else fPart.fCurve
            val ptCount = if (index == 1) rPts else lPts
            for (idx2 in 0..ptCount) {
                minX = minOf(minX, curve[idx2].x); minY = minOf(minY, curve[idx2].y)
                maxX = maxOf(maxX, curve[idx2].x); maxY = maxOf(maxY, curve[idx2].y)
            }
            val maxWidth = maxOf(maxX - minX, maxY - minY)
            delta = if (maxWidth == 0.0) Double.NaN else delta / maxWidth
            // skbug.com/40039654 : narrow band where translation flips
            // the original-vs-translated hull side ; treat as parallel.
            if (delta < 4e-3 && delta > 1e-3 && !useIntersect && fPart.isCurve() &&
                rh.fPart.isCurve() && fOriginalCurvePart[0] != fPart.fCurve[0]) {
                val origin = rh.fOriginalCurvePart[0]
                val count = segVerbToPoints(rh.segment()!!.verb())
                val line = rh.fOriginalCurvePart[count] - origin
                val originalSide = rh.lineOnOneSide(origin, line, this, true)
                if (originalSide >= 0) {
                    val translatedSide = rh.lineOnOneSide(origin, line, this, false)
                    if (originalSide != translatedSide) continue
                }
            }
            if (delta > 1e-3) {
                useIntersect = !useIntersect
                if (useIntersect) {
                    sRayLonger = rayLonger
                    sCept = cept
                    sCeptT = smallTs[index]
                    sIndex = index
                }
            }
        }
        if (useIntersect) {
            val curve = if (sIndex == 1) rh.fPart.fCurve else fPart.fCurve
            val seg = if (sIndex == 1) rh.segment()!! else segment()!!
            val tStart = if (sIndex == 1) rh.fStart!!.t() else fStart!!.t()
            val mid = seg.dPtAtT(tStart + (sCeptT - tStart) / 2) - curve[0]
            val septDir = mid.crossCheck(sCept)
            if (septDir == 0.0) return checkParallel(rh)
            return sRayLonger xor (sIndex == 0) xor (septDir < 0)
        }
        return checkParallel(rh)
    }

    // ─── Deferred (D1.2.b.2.d) ──────────────────────────────────
    // - orderable / after / insert / merge
}
