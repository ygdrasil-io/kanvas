/*
 * Copyright 2026 The kanvas-skia authors.
 *
 * Mirrors free functions and helpers from Skia's
 * `src/pathops/SkPathOpsWinding.cpp` — the ray-tracing winding
 * suite that determines a span's absolute winding number by firing
 * a perpendicular ray and counting curve crossings.
 *
 * Phase D1.2.h.5.5 — Foundation : `SkOpRayDir` enum, axis-projection
 * helpers, `get_t_guess`, `SkOpRayHit` + `makeTestBase`, comparators.
 *
 * Phase D1.2.h.5.6 — `CurveIntercept` dispatch + `SkOpSegment.rayCheck`
 * + `SkOpSegment.windingSpanAtT` + `SkOpContour.rayCheck`.
 *
 * Phase D1.2.h.5.7 — `SkOpSpan.sortableTop`, the winding-accumulation
 * walker that consumes the hit list and assigns absolute winding
 * sums to the spans crossed by the ray.
 *
 * Phase D1.2.h.5.8 — `findSortableTop` × 3 (Segment / Contour /
 * global) ; replaces the h.5.4 `FindSortableTop` stub. **Closes
 * the winding suite** — `bridgeOp` now drives a real walker, so
 * `Op` becomes functional for non-rect non-empty inputs (modulo
 * the upstream-bug workarounds the algorithm relies on).
 */
package org.skia.pathops.internal


import org.skia.math.SkDLine
import org.skia.math.SkDPoint
import org.skia.math.SkDVector
import org.skia.math.approximately_between
import org.skia.math.approximately_equal
import org.skia.math.approximately_zero
import org.skia.math.between
import org.skia.math.roughly_equal
import org.skia.math.SkPoint
import org.skia.math.SkRect

/**
 * One of four axis-aligned ray directions. Used by [SkOpRayHit] to
 * encode the perpendicular ray's heading from a span's starting
 * point. Mirrors `enum class SkOpRayDir`
 * (`src/pathops/SkPathOpsWinding.cpp:48`).
 *
 * The encoding is non-trivial — `xy_index(dir) & 1` tells whether
 * the ray's axis is X (`kLeft` / `kRight` → `1` ? no, `0` for
 * `kLeft`, `0` for `kRight` ; the bit-trick `& 1` returns `0` for
 * `kLeft` (0) / `kRight` (2) and `1` for `kTop` (1) / `kBottom`
 * (3)) — leaving the helpers to do the actual sign work.
 */
internal enum class SkOpRayDir { kLeft, kTop, kRight, kBottom }

/**
 * `0` when [dir] is X-axis-aligned (`kLeft` / `kRight`), `1` when
 * Y-aligned (`kTop` / `kBottom`). Used to index the X / Y component
 * of points, vectors, and rect sides. Mirrors `xy_index`
 * (`SkPathOpsWinding.cpp:64`).
 */
internal fun xy_index(dir: SkOpRayDir): Int = dir.ordinal and 1

/**
 * Read [pt]'s X (when [dir] is X-aligned) or Y (when Y-aligned)
 * component. Mirrors `pt_xy` (`SkPathOpsWinding.cpp:68`).
 */
internal fun pt_xy(pt: SkPoint, dir: SkOpRayDir): Float =
    if (xy_index(dir) == 0) pt.fX else pt.fY

/**
 * Read [pt]'s **other** component (Y when X-aligned, X when Y).
 * Mirrors `pt_yx` (`SkPathOpsWinding.cpp:72`).
 */
internal fun pt_yx(pt: SkPoint, dir: SkOpRayDir): Float =
    if (xy_index(dir) == 0) pt.fY else pt.fX

/**
 * Read [v]'s X component for X-aligned [dir], Y for Y-aligned.
 * Mirrors `pt_dxdy` (`SkPathOpsWinding.cpp:76`).
 */
internal fun pt_dxdy(v: SkDVector, dir: SkOpRayDir): Double =
    if (xy_index(dir) == 0) v.x else v.y

/**
 * Read [v]'s **other** component (Y for X-aligned dir, X for Y).
 * Mirrors `pt_dydx` (`SkPathOpsWinding.cpp:80`).
 */
internal fun pt_dydx(v: SkDVector, dir: SkOpRayDir): Double =
    if (xy_index(dir) == 0) v.y else v.x

/**
 * Pull the side of [r] that the ray would exit on. Mirrors
 * `rect_side` (`SkPathOpsWinding.cpp:84`) — indexes the rect's
 * `[fLeft, fTop, fRight, fBottom]` array directly via [dir].
 */
internal fun rect_side(r: SkRect, dir: SkOpRayDir): Float = when (dir) {
    SkOpRayDir.kLeft -> r.left
    SkOpRayDir.kTop -> r.top
    SkOpRayDir.kRight -> r.right
    SkOpRayDir.kBottom -> r.bottom
}

/**
 * True iff [pt]'s perpendicular projection onto the ray's axis
 * falls within [rect]'s extent. Used by [SkOpSegment.rayCheck]
 * (D1.2.h.5.6) to cull segments before running the heavier
 * `CurveIntercept` dispatch. Mirrors `sideways_overlap`
 * (`SkPathOpsWinding.cpp:88`).
 */
internal fun sideways_overlap(rect: SkRect, pt: SkPoint, dir: SkOpRayDir): Boolean {
    val (lo, mid, hi) = if (xy_index(dir) == 0) {
        Triple(rect.top, pt.fY, rect.bottom)
    } else {
        Triple(rect.left, pt.fX, rect.right)
    }
    return approximately_between(lo.toDouble(), mid.toDouble(), hi.toDouble())
}

/**
 * True for the "left-of" / "top-of" directions (`kLeft`, `kTop`).
 * Used to flip comparison sense on the right / bottom sides.
 * Mirrors `less_than` (`SkPathOpsWinding.cpp:93`).
 */
internal fun less_than(dir: SkOpRayDir): Boolean = (dir.ordinal and 2) == 0

/**
 * True iff the segment's tangent at the hit point is "going
 * counter-clockwise" relative to the ray direction. Used by
 * [SkOpSpan.sortableTop] (D1.2.h.5.7) to assign sign to the
 * winding contribution at each crossing. Mirrors `ccw_dxdy`
 * (`SkPathOpsWinding.cpp:97`).
 */
internal fun ccw_dxdy(v: SkDVector, dir: SkOpRayDir): Boolean {
    val vPartPos = pt_dydx(v, dir) > 0
    val leftBottom = ((dir.ordinal + 1) and 2) != 0
    return vPartPos == leftBottom
}

/**
 * Pseudo-random t-value generator that the [SkOpSpan.sortableTop]
 * walker cycles through when the perpendicular ray gets stuck
 * (parallel to a curve, hitting a vertex, etc.).
 *
 * Returns a t in `(0, 1)` — `0.5` for `tTry == 0`, then bisects
 * progressively : `0.25, 0.75, 0.125, 0.375, 0.625, 0.875, ...`.
 * `dirOffset` is the low bit of `tTry` — flips between
 * `kLeft / kRight` or `kTop / kBottom`.
 *
 * Mirrors `get_t_guess` (`SkPathOpsWinding.cpp:239`).
 */
internal fun get_t_guess(tTry: Int, dirOffset: IntArray): Double {
    var t = 0.5
    dirOffset[0] = tTry and 1
    val tBase = tTry shr 1
    var tBits = 0
    var n = tTry
    while (true) {
        n = n shr 1
        if (n == 0) break
        t /= 2
        ++tBits
    }
    if (tBits != 0) {
        val tIndex = (tBase - 1) and ((1 shl tBits) - 1)
        t += t * 2 * tIndex
    }
    return t
}

/**
 * One ray-vs-curve crossing record. Linked via [fNext] into a
 * per-`sortableTop` list ; sorted by point coordinate via
 * [hit_compare_x] / [hit_compare_y] before the winding walk.
 *
 * Mirrors `struct SkOpRayHit`
 * (`src/pathops/SkPathOpsWinding.cpp:103`).
 */
internal class SkOpRayHit {
    var fNext: SkOpRayHit? = null
    var fSpan: SkOpSpan? = null
    var fPt: SkPoint = SkPoint()
    var fT: Double = 0.0
    var fSlope: SkDVector = SkDVector()
    var fValid: Boolean = false

    /**
     * Initialise this hit as the test base — the span being asked
     * for its winding sum. Sets [fT], [fSlope], [fPt] from [span]
     * at parameter [t] (interpolated between span.t() and
     * span.next().t()), and returns the most-perpendicular ray
     * direction (`kLeft` if the slope is more vertical, `kTop` if
     * more horizontal).
     *
     * Mirrors `SkOpRayHit::makeTestBase`
     * (`SkPathOpsWinding.cpp:104`).
     */
    fun makeTestBase(span: SkOpSpan, t: Double): SkOpRayDir {
        fNext = null
        fSpan = span
        fT = span.t() * (1 - t) + span.next()!!.t() * t
        val segment = span.segment()!!
        fSlope = segment.dSlopeAtT(fT)
        fPt = segment.ptAtT(fT)
        fValid = true
        return if (kotlin.math.abs(fSlope.x) < kotlin.math.abs(fSlope.y))
            SkOpRayDir.kLeft else SkOpRayDir.kTop
    }
}

// ─── Hit comparators (D1.2.h.5.5) ─────────────────────────────────

internal val hit_compare_x: Comparator<SkOpRayHit> =
    Comparator { a, b -> a.fPt.fX.compareTo(b.fPt.fX) }

internal val reverse_hit_compare_x: Comparator<SkOpRayHit> =
    Comparator { a, b -> b.fPt.fX.compareTo(a.fPt.fX) }

internal val hit_compare_y: Comparator<SkOpRayHit> =
    Comparator { a, b -> a.fPt.fY.compareTo(b.fPt.fY) }

internal val reverse_hit_compare_y: Comparator<SkOpRayHit> =
    Comparator { a, b -> b.fPt.fY.compareTo(a.fPt.fY) }

// ─── CurveIntercept dispatch (D1.2.h.5.6) ─────────────────────────

/**
 * Find t-values where the curve crosses the axis-aligned line
 * `(axis = horizontal/vertical based on [dir], axisIntercept)`.
 *
 * Mirrors `CurveIntercept[verb*2 + xy_index(dir)]` dispatch table
 * (`src/pathops/SkPathOpsCurve.h:414`) and the per-verb static
 * helpers (`line_intercept_h/v`, `quad_intercept_h/v`,
 * `conic_intercept_h/v`, `cubic_intercept_h/v`).
 *
 * Returns the number of in-range roots written into [roots].
 */
internal fun CurveIntercept(
    verb: SkOpSegment.SegVerb,
    dir: SkOpRayDir,
    pts: Array<SkPoint>,
    weight: Float,
    axisIntercept: Float,
    roots: DoubleArray,
): Int {
    // Upstream `CurveIntercept[verb * 2 + xy_index(dir)]` :
    // `xy_index = 0` (kLeft / kRight) → horizontal-intercept variant
    // (the ray is horizontal, so we ask where the curve crosses
    // y = axisIntercept).
    // `xy_index = 1` (kTop / kBottom) → vertical-intercept variant
    // (the ray is vertical, so we ask where the curve crosses
    // x = axisIntercept).
    val horizontal = xy_index(dir) == 0
    return when (verb) {
        SkOpSegment.SegVerb.kLine -> {
            if (horizontal) {
                if (pts[0].fY == pts[1].fY) return 0
                val line = SkDLine().apply { set(pts[0], pts[1]) }
                roots[0] = SkIntersections.HorizontalIntercept(line, axisIntercept.toDouble())
                if (between(0.0, roots[0], 1.0)) 1 else 0
            } else {
                if (pts[0].fX == pts[1].fX) return 0
                val line = SkDLine().apply { set(pts[0], pts[1]) }
                roots[0] = SkIntersections.VerticalIntercept(line, axisIntercept.toDouble())
                if (between(0.0, roots[0], 1.0)) 1 else 0
            }
        }
        SkOpSegment.SegVerb.kQuad -> {
            val quad = SkDQuad().apply { set(pts[0], pts[1], pts[2]) }
            if (horizontal) SkIntersections.HorizontalIntercept(quad, axisIntercept, roots)
            else SkIntersections.VerticalIntercept(quad, axisIntercept, roots)
        }
        SkOpSegment.SegVerb.kConic -> {
            val conic = SkDConic().apply { set(pts[0], pts[1], pts[2], weight) }
            if (horizontal) SkIntersections.HorizontalIntercept(conic, axisIntercept, roots)
            else SkIntersections.VerticalIntercept(conic, axisIntercept, roots)
        }
        SkOpSegment.SegVerb.kCubic -> {
            val cubic = SkDCubic().apply { set(pts[0], pts[1], pts[2], pts[3]) }
            if (horizontal) cubic.horizontalIntersect(axisIntercept.toDouble(), roots)
            else cubic.verticalIntersect(axisIntercept.toDouble(), roots)
        }
        SkOpSegment.SegVerb.kUnset -> error("invalid verb (kUnset)")
    }
}

// ─── rayCheck (D1.2.h.5.6) ────────────────────────────────────────

/**
 * Walk this segment's spans and find the one whose t-range contains
 * [tHit]. Returns null when [tHit] sits exactly on a span boundary
 * (which the upstream `windingSpanAtT` treats as ambiguous).
 *
 * Mirrors `SkOpSegment::windingSpanAtT`
 * (`src/pathops/SkPathOpsWinding.cpp:208`).
 */
internal fun SkOpSegment.windingSpanAtT(tHit: Double): SkOpSpan? {
    var span: SkOpSpan = fHead
    while (true) {
        val next = span.next() ?: break
        if (approximately_equal(tHit, next.t())) return null
        if (tHit < next.t()) return span
        if (next.final()) break
        span = next.upCast()
    }
    return null
}

/**
 * Fire the perpendicular ray from [base] in direction [dir] at this
 * segment ; for each curve crossing, prepend a fresh [SkOpRayHit]
 * onto [hitsHead].
 *
 * Skips :
 *  - segments whose bbox doesn't overlap the ray's perpendicular
 *    axis at [base]'s point ([sideways_overlap]),
 *  - segments fully on the wrong side of the ray ([less_than] /
 *    `rect_side` cull),
 *  - root t-values that match `base.fT` exactly when the ray's
 *    own segment is `this`,
 *  - intersection points that match `base.fPt` (same span) or
 *    sit on the wrong side of the ray,
 *  - cubics whose t-value roughly equals `base.fT` and pt roughly
 *    equals `base.fPt` (defensive against the `(rarely expect this)`
 *    upstream comment),
 *  - root t-values where the slope is too parallel to the ray (the
 *    `pt_dydx * 10000 > pt_dxdy` test),
 *  - spans with both `windValue == 0` and `oppValue == 0`.
 *
 * Mirrors `SkOpSegment::rayCheck`
 * (`src/pathops/SkPathOpsWinding.cpp:138`).
 */
internal fun SkOpSegment.rayCheck(
    base: SkOpRayHit,
    dir: SkOpRayDir,
    hitsHead: Array<SkOpRayHit?>,
) {
    if (!sideways_overlap(bounds(), base.fPt, dir)) return
    val baseXY = pt_xy(base.fPt, dir)
    val boundsXY = rect_side(bounds(), dir)
    val checkLessThan = less_than(dir)
    if (!approximately_equal(baseXY.toDouble(), boundsXY.toDouble()) &&
        (baseXY < boundsXY) == checkLessThan) {
        return
    }
    val baseYX = pt_yx(base.fPt, dir)
    val tVals = DoubleArray(3)
    val roots = CurveIntercept(verb(), dir, pts(), weight(), baseYX, tVals)
    for (index in 0 until roots) {
        val t = tVals[index]
        if (base.fSpan?.segment() === this && approximately_equal(base.fT, t)) continue
        var slope = SkDVector()
        var pt = SkPoint()
        var valid = false
        when {
            approximately_zero(t) -> pt = pts()[0]
            approximately_equal(t, 1.0) -> pt = pts()[segVerbToPoints(verb())]
            else -> {
                require(between(0.0, t, 1.0))
                pt = ptAtT(t)
                if (SkDPoint.ApproximatelyEqual(pt, base.fPt)) {
                    if (base.fSpan?.segment() === this) continue
                } else {
                    val ptXY = pt_xy(pt, dir)
                    if (!approximately_equal(baseXY.toDouble(), ptXY.toDouble()) &&
                        (baseXY < ptXY) == checkLessThan) continue
                    slope = dSlopeAtT(t)
                    if (verb() == SkOpSegment.SegVerb.kCubic &&
                        base.fSpan?.segment() === this &&
                        roughly_equal(base.fT, t) &&
                        SkDPoint.RoughlyEqual(pt, base.fPt)) continue
                    if (kotlin.math.abs(pt_dydx(slope, dir) * 10000) >
                        kotlin.math.abs(pt_dxdy(slope, dir))) {
                        valid = true
                    }
                }
            }
        }
        val span = windingSpanAtT(t)
        if (span == null) {
            valid = false
        } else if (span.windValue() == 0 && span.oppValue() == 0) {
            continue
        }
        val newHit = SkOpRayHit().apply {
            fNext = hitsHead[0]
            fPt = pt
            fSlope = slope
            fSpan = span
            fT = t
            fValid = valid
        }
        hitsHead[0] = newHit
    }
}

/**
 * Per-contour rayCheck driver : early-out when the contour's bbox
 * is fully on the wrong side of the ray, otherwise walk every
 * segment calling [SkOpSegment.rayCheck].
 *
 * Mirrors `SkOpContour::rayCheck`
 * (`src/pathops/SkPathOpsWinding.cpp:123`).
 */
internal fun SkOpContour.rayCheck(
    base: SkOpRayHit,
    dir: SkOpRayDir,
    hitsHead: Array<SkOpRayHit?>,
) {
    val baseXY = pt_xy(base.fPt, dir)
    val boundsXY = rect_side(bounds(), dir)
    val checkLessThan = less_than(dir)
    if (!approximately_equal(baseXY.toDouble(), boundsXY.toDouble()) &&
        (baseXY < boundsXY) == checkLessThan) {
        return
    }
    var seg: SkOpSegment? = fHead
    while (seg != null) {
        seg.rayCheck(base, dir, hitsHead)
        seg = seg.next()
    }
}

// ─── SkOpSpan.sortableTop (D1.2.h.5.7) ───────────────────────────

/**
 * Compute this span's absolute winding number by firing a
 * perpendicular ray and counting curve crossings.
 *
 * Pipeline :
 *  1. Pick a t-value via [get_t_guess] (cycles through
 *     `0.5, 0.25, 0.75, …` on retry via [SkOpSpan.fTopTTry]).
 *  2. Initialise a `SkOpRayHit` test base via [SkOpRayHit.makeTestBase] ;
 *     bail when the slope is `(0, 0)` (degenerate).
 *  3. Bail when the segment is curved AND the slope is exactly
 *     parallel to the ray (would cause numerical instability).
 *  4. Walk every contour calling [SkOpContour.rayCheck] to populate
 *     a linked list of [SkOpRayHit].
 *  5. Sort the hits along the ray's perpendicular axis (via the
 *     [hit_compare_x] / [hit_compare_y] / reversed comparators
 *     selected from `xy_index(dir)` and `less_than(dir)`).
 *  6. Walk the sorted hits accumulating wind / oppWind, assigning
 *     `windSum` / `oppSum` to each span (or the segment's CCW flag
 *     when the global state is in `kFixWinding` phase).
 *
 * Returns false on any abort path : zero slope, parallel slope,
 * adjacent equal hits (numerical degeneracy), invalid hit, or
 * null span on a hit. The caller then retries with a new t-guess
 * up to `kMaxWindingTries`.
 *
 * Mirrors `SkOpSpan::sortableTop`
 * (`src/pathops/SkPathOpsWinding.cpp:255`).
 */
internal fun SkOpSpan.sortableTop(contourHead: SkOpContour): Boolean {
    val dirOffset = IntArray(1)
    val t = get_t_guess(fTopTTry++, dirOffset)
    val hitBase = SkOpRayHit()
    var dir = hitBase.makeTestBase(this, t)
    if (hitBase.fSlope.x == 0.0 && hitBase.fSlope.y == 0.0) return false
    val newDir = SkOpRayDir.entries[dir.ordinal + dirOffset[0]]
    dir = newDir
    val baseSeg = hitBase.fSpan!!.segment()!!
    if (baseSeg.verb() != SkOpSegment.SegVerb.kLine && pt_dydx(hitBase.fSlope, dir) == 0.0) {
        return false
    }
    // 4. Walk every contour collecting hits.
    val hitsHead = arrayOf<SkOpRayHit?>(hitBase)
    var contour: SkOpContour? = contourHead
    while (contour != null) {
        if (contour.count() != 0) contour.rayCheck(hitBase, dir, hitsHead)
        contour = contour.next()
    }
    // 5. Linearise the linked list and sort.
    val sorted = mutableListOf<SkOpRayHit>()
    var hit: SkOpRayHit? = hitsHead[0]
    while (hit != null) { sorted.add(hit); hit = hit.fNext }
    val cmp = if (xy_index(dir) == 1) {
        if (less_than(dir)) hit_compare_y else reverse_hit_compare_y
    } else {
        if (less_than(dir)) hit_compare_x else reverse_hit_compare_x
    }
    sorted.sortWith(cmp)
    // 6. Accumulate winding.
    var last: SkPoint? = null
    var wind = 0
    var oppWind = 0
    for (index in sorted.indices) {
        val h = sorted[index]
        if (!h.fValid) return false
        val ccw = ccw_dxdy(h.fSlope, dir)
        val span = h.fSpan ?: return false
        val hitSegment = span.segment() ?: return false
        if (span.windValue() == 0 && span.oppValue() == 0) continue
        if (last != null && SkDPoint.ApproximatelyEqual(last, h.fPt)) return false
        if (index < sorted.size - 1) {
            val nxt = sorted[index + 1].fPt
            if (SkDPoint.ApproximatelyEqual(nxt, h.fPt)) return false
        }
        val operand = hitSegment.operand()
        if (operand) { val tmp = wind; wind = oppWind; oppWind = tmp }
        val lastWind = wind
        val lastOpp = oppWind
        val windValue = if (ccw) -span.windValue() else span.windValue()
        val oppValue = if (ccw) -span.oppValue() else span.oppValue()
        wind += windValue
        oppWind += oppValue
        var sumSet = false
        val spanSum = span.windSum()
        val windSum = if (SkOpSegment.UseInnerWinding(lastWind, wind)) wind else lastWind
        if (spanSum == SkOpSpan.SK_MinS32) {
            span.setWindSum(windSum)
            sumSet = true
        }
        val oSpanSum = span.oppSum()
        val oppSum = if (SkOpSegment.UseInnerWinding(lastOpp, oppWind)) oppWind else lastOpp
        if (oSpanSum == SkOpSpan.SK_MinS32) {
            span.setOppSum(oppSum)
        }
        if (sumSet) {
            if (globalState()?.phase() == SkOpPhase.kFixWinding) {
                hitSegment.contour()?.setCcw(if (ccw) 1 else 0)
            } else {
                val nextSpan = span.next() ?: return false
                hitSegment.markAndChaseWinding(span, nextSpan, windSum, oppSum, null)
                hitSegment.markAndChaseWinding(nextSpan, span, windSum, oppSum, null)
            }
        }
        if (operand) { val tmp = wind; wind = oppWind; oppWind = tmp }
        last = h.fPt
        globalState()?.bumpNested()
    }
    return true
}

// ─── findSortableTop x 3 (D1.2.h.5.8) ────────────────────────────

/**
 * Walk this segment's spans returning the first one whose winding
 * is already known (`windSum != SK_MinS32`) or that successfully
 * computed its winding via [sortableTop]. Returns null when every
 * span is done or every [sortableTop] retry failed.
 *
 * Mirrors `SkOpSegment::findSortableTop`
 * (`src/pathops/SkPathOpsWinding.cpp:390`).
 */
internal fun SkOpSegment.findSortableTop(contourHead: SkOpContour): SkOpSpan? {
    var span: SkOpSpan = fHead
    while (true) {
        val next = span.next() ?: break
        if (!span.done()) {
            if (span.windSum() != SkOpSpan.SK_MinS32) return span
            if (span.sortableTop(contourHead)) return span
        }
        if (next.final()) break
        span = next.upCast()
    }
    return null
}

/**
 * Walk this contour's segments calling [SkOpSegment.findSortableTop].
 * When every segment is already done, set this contour's `fDone`
 * and return null.
 *
 * Mirrors `SkOpContour::findSortableTop`
 * (`src/pathops/SkPathOpsWinding.cpp:408`).
 */
internal fun SkOpContour.findSortableTop(contourHead: SkOpContour): SkOpSpan? {
    var allDone = true
    if (fCount != 0) {
        var seg: SkOpSegment? = fHead
        while (seg != null) {
            if (!seg.done()) {
                allDone = false
                seg.findSortableTop(contourHead)?.let { return it }
            }
            seg = seg.next()
        }
    }
    if (allDone) fDone = true
    return null
}
