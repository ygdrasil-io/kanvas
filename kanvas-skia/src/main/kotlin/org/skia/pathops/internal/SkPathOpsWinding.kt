/*
 * Copyright 2026 The kanvas-skia authors.
 *
 * Mirrors free functions and helpers from Skia's
 * `src/pathops/SkPathOpsWinding.cpp` — the ray-tracing winding
 * suite that determines a span's absolute winding number by firing
 * a perpendicular ray and counting curve crossings.
 *
 * Phase D1.2.h.5.5 — Foundation : `SkOpRayDir` enum, axis-projection
 * helpers (`xy_index` / `pt_xy` / `pt_yx` / `pt_dxdy` / `pt_dydx` /
 * `rect_side` / `sideways_overlap` / `less_than` / `ccw_dxdy`),
 * `get_t_guess` t-value generator, `SkOpRayHit` data class +
 * `makeTestBase`, and the `hit_compare_x/y` ± reverse comparators.
 *
 * The remaining pieces (`SkOpSegment.rayCheck` /
 * `SkOpContour.rayCheck`, `SkOpSpan.sortableTop`,
 * `SkOpSegment.findSortableTop` / `SkOpContour.findSortableTop`,
 * `FindSortableTop` real impl) land in subsequent D1.2.h.5.x
 * sub-slices.
 */
package org.skia.pathops.internal

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
