/*
 * Copyright 2026 The kanvas-skia authors.
 *
 * Mirrors Skia's `struct SkDCurve` and `class SkDCurveSweep` from
 * `src/pathops/SkPathOpsCurve.{h,cpp}`.
 *
 * Phase D1.2.b.2.0 — curve dispatcher prerequisites for SkOpAngle.setSpans
 * (D1.2.b.2.a) and onward. Ships :
 *  - [SkDCurve] : a discriminated curve carrier (line / quad / conic /
 *    cubic) with index access on its 4-point storage and as-Line /
 *    as-Quad / as-Conic / as-Cubic snapshot accessors.
 *  - [SkDCurveSweep] : pairs an [SkDCurve] with two sweep vectors and
 *    `fIsCurve` / `fOrdered` flags ; [setCurveHullSweep] computes the
 *    hull sweep used by SkOpAngle to coarse-sort tangent directions.
 *  - Verb-dispatched curve helpers : [pointAtT], [slopeAtT],
 *    [intersectRay] — wrappers that read [SkDCurve] and dispatch to the
 *    right per-curve method.
 *
 * SkPathOpsCurve.cpp's `SkDCurve::nearPoint` and `setQuadBounds /
 * setConicBounds / setCubicBounds` are deferred — they're consumed by
 * coincidence + bounds work in later slices.
 */
package org.skia.pathops.internal


import org.skia.math.FLT_EPSILON
import org.skia.math.SkDLine
import org.skia.math.SkDPoint
import org.skia.math.SkDVector
import kotlin.math.abs
import kotlin.math.max
import org.skia.math.SkPoint

/**
 * Number of control points (excluding the start point at index 0) for
 * a given segment verb. Mirrors `SkPathOpsVerbToPoints` from
 * `src/pathops/SkPathOpsTypes.h`.
 */
internal fun segVerbToPoints(verb: SkOpSegment.SegVerb): Int = when (verb) {
    SkOpSegment.SegVerb.kLine -> 1
    SkOpSegment.SegVerb.kQuad, SkOpSegment.SegVerb.kConic -> 2
    SkOpSegment.SegVerb.kCubic -> 3
    SkOpSegment.SegVerb.kUnset -> error("verb not set")
}

/**
 * A discriminated curve carrier — holds the (up to) 4 control points
 * of a line / quad / conic / cubic, plus an optional conic weight, plus
 * a verb tag identifying which alternative is active. Mirrors the
 * upstream `union SkDCurve { SkDLine; SkDQuad; SkDConic; SkDCubic }`.
 *
 * Storage layout : a single [Array]`<SkDPoint>`(4) holding all control
 * points ; the [fVerb] tag indicates how many slots are valid (2 / 3 /
 * 3 / 4 for line / quad / conic / cubic). The [asLine] / [asQuad] /
 * [asConic] / [asCubic] accessors return value-type snapshots built
 * from the stored points (not in-place views — modifications to the
 * snapshots do not propagate back).
 */
internal class SkDCurve {
    /** Control points (point 0..N where N = [segVerbToPoints]). */
    val fPts: Array<SkDPoint> = Array(4) { SkDPoint() }

    /** Conic weight (only meaningful when [fVerb] == kConic). */
    var fWeight: Double = 1.0

    var fVerb: SkOpSegment.SegVerb = SkOpSegment.SegVerb.kUnset

    /** Mirrors `SkDCurve::operator[](int)` — read-only point access. */
    operator fun get(n: Int): SkDPoint = fPts[n]

    /** Mirrors `SkDCurve::operator[](int)` — write point at index. */
    operator fun set(n: Int, v: SkDPoint) {
        fPts[n] = SkDPoint(v.x, v.y)
    }

    /** Deep-copy [other] into this. Mirrors `SkDCurve::operator=`. */
    fun copyFrom(other: SkDCurve): SkDCurve {
        for (i in 0..3) fPts[i] = SkDPoint(other.fPts[i].x, other.fPts[i].y)
        fWeight = other.fWeight
        fVerb = other.fVerb
        return this
    }

    fun asLine(): SkDLine = SkDLine(arrayOf(
        SkDPoint(fPts[0].x, fPts[0].y),
        SkDPoint(fPts[1].x, fPts[1].y),
    ))

    fun asQuad(): SkDQuad = SkDQuad(arrayOf(
        SkDPoint(fPts[0].x, fPts[0].y),
        SkDPoint(fPts[1].x, fPts[1].y),
        SkDPoint(fPts[2].x, fPts[2].y),
    ))

    fun asConic(): SkDConic = SkDConic(
        SkDQuad(arrayOf(
            SkDPoint(fPts[0].x, fPts[0].y),
            SkDPoint(fPts[1].x, fPts[1].y),
            SkDPoint(fPts[2].x, fPts[2].y),
        )),
        fWeight.toFloat(),
    )

    fun asCubic(): SkDCubic = SkDCubic(arrayOf(
        SkDPoint(fPts[0].x, fPts[0].y),
        SkDPoint(fPts[1].x, fPts[1].y),
        SkDPoint(fPts[2].x, fPts[2].y),
        SkDPoint(fPts[3].x, fPts[3].y),
    ))
}

/**
 * Pairs an [SkDCurve] with two **sweep vectors** spanning the curve's
 * hull, plus flags telling whether the carrier is curved at all
 * ([isCurve]) and whether the sweep is ordered counter-clockwise
 * ([isOrdered]). Mirrors `SkDCurveSweep` from `SkPathOpsCurve.h`.
 *
 * SkOpAngle uses the sweep to coarse-quantize a curve's tangent
 * direction into 32 sectors before falling back to numerical curve-
 * versus-curve comparisons.
 */
internal class SkDCurveSweep {
    val fCurve: SkDCurve = SkDCurve()
    val fSweep: Array<SkDVector> = arrayOf(SkDVector(0.0, 0.0), SkDVector(0.0, 0.0))
    private var fIsCurve: Boolean = false
    private var fOrdered: Boolean = true

    fun isCurve(): Boolean = fIsCurve
    fun isOrdered(): Boolean = fOrdered

    /**
     * Compute [fSweep] from [fCurve]'s control points. Mirrors
     * `SkDCurveSweep::setCurveHullSweep` (`SkPathOpsCurve.cpp:90`).
     *
     * For lines : both sweep vectors are the (start → end) direction ;
     * `fIsCurve` is false ; `fOrdered` is true.
     *
     * For quads / conics : sweep[0] = pt1 − pt0, sweep[1] = pt2 − pt0.
     * If sweep[0] is "roughly zero" relative to the curve's largest
     * coordinate, fall through to sweep[1].
     *
     * For cubics : sweep[0] = pt1 − pt0, sweep[1] = pt2 − pt0,
     * thirdSweep = pt3 − pt0. If thirdSweep falls between sweep[0] and
     * sweep[1] (same-sign cross), the hull is convex and the sweep is
     * ordered ; otherwise we re-shuffle to keep the wider span.
     *
     * `fIsCurve` ends up true iff the sweep vectors are not parallel.
     */
    fun setCurveHullSweep(verb: SkOpSegment.SegVerb) {
        fOrdered = true
        fSweep[0] = fCurve.fPts[1] - fCurve.fPts[0]
        if (verb == SkOpSegment.SegVerb.kLine) {
            fSweep[1] = SkDVector(fSweep[0].x, fSweep[0].y)
            fIsCurve = false
            return
        }
        fSweep[1] = fCurve.fPts[2] - fCurve.fPts[0]
        // Track the largest (absolute) coordinate of any control point —
        // used to decide whether sweep[0] is "roughly zero" at the curve's
        // own scale.
        var maxVal = 0.0
        for (i in 0..segVerbToPoints(verb)) {
            maxVal = max(maxVal, max(abs(fCurve.fPts[i].x), abs(fCurve.fPts[i].y)))
        }
        val onSetIsCurve = run {
            if (verb != SkOpSegment.SegVerb.kCubic) {
                if (roughlyZeroWhenComparedTo(fSweep[0].x, maxVal) &&
                    roughlyZeroWhenComparedTo(fSweep[0].y, maxVal)) {
                    fSweep[0] = SkDVector(fSweep[1].x, fSweep[1].y)
                }
                return@run Unit
            }
            // Cubic-specific : also consider the third sweep vector
            // (pt3 − pt0). Re-orders the sweep so it spans the widest
            // angular extent of the hull.
            val thirdSweep = fCurve.fPts[3] - fCurve.fPts[0]
            if (fSweep[0].x == 0.0 && fSweep[0].y == 0.0) {
                fSweep[0] = SkDVector(fSweep[1].x, fSweep[1].y)
                fSweep[1] = SkDVector(thirdSweep.x, thirdSweep.y)
                if (roughlyZeroWhenComparedTo(fSweep[0].x, maxVal) &&
                    roughlyZeroWhenComparedTo(fSweep[0].y, maxVal)) {
                    fSweep[0] = SkDVector(fSweep[1].x, fSweep[1].y)
                    fCurve.fPts[1] = SkDPoint(fCurve.fPts[3].x, fCurve.fPts[3].y)
                }
                return@run Unit
            }
            val s1x3 = fSweep[0].crossCheck(thirdSweep)
            val s3x2 = thirdSweep.crossCheck(fSweep[1])
            if (s1x3 * s3x2 >= 0) return@run Unit // third on/between first two — already convex
            val s2x1 = fSweep[1].crossCheck(fSweep[0])
            // FIXME (upstream) : a > 180° cubic sweep mis-orders here.
            require(s1x3 * s2x1 < 0 || s1x3 * s3x2 < 0)
            if (s3x2 * s2x1 < 0) {
                require(s2x1 * s1x3 > 0)
                fSweep[0] = SkDVector(fSweep[1].x, fSweep[1].y)
                fOrdered = false
            }
            fSweep[1] = SkDVector(thirdSweep.x, thirdSweep.y)
        }
        fIsCurve = fSweep[0].crossCheck(fSweep[1]) != 0.0
        // onSetIsCurve serves only to anchor the goto-equivalent control
        // flow ; the final fIsCurve assignment lives outside the run {}.
        @Suppress("UNUSED_EXPRESSION") onSetIsCurve
    }
}

/**
 * Mirrors upstream's `roughly_zero_when_compared_to` from
 * `src/pathops/SkPathOpsTypes.h` — true when [x] is small enough
 * (relative to [y]) that adding it to [y] produces no observable
 * change in the float-32 sense.
 */
private fun roughlyZeroWhenComparedTo(x: Double, y: Double): Boolean =
    x == 0.0 || abs(x) < abs(y) * FLT_EPSILON

// ─── Verb-dispatched curve helpers ─────────────────────────────────

/**
 * Mirrors the `CurveDDPointAtT[verb]` dispatch table in
 * `SkPathOpsCurve.h`. Returns the curve's `(x, y)` at parameter [t].
 */
internal fun SkDCurve.pointAtT(t: Double): SkDPoint = when (fVerb) {
    SkOpSegment.SegVerb.kLine -> asLine().ptAtT(t)
    SkOpSegment.SegVerb.kQuad -> asQuad().ptAtT(t)
    SkOpSegment.SegVerb.kConic -> asConic().ptAtT(t)
    SkOpSegment.SegVerb.kCubic -> asCubic().ptAtT(t)
    SkOpSegment.SegVerb.kUnset -> error("verb not set")
}

/**
 * Mirrors `CurveDDSlopeAtT[verb]` — returns the tangent vector at [t].
 */
internal fun SkDCurve.slopeAtT(t: Double): SkDVector = when (fVerb) {
    SkOpSegment.SegVerb.kLine -> fPts[1] - fPts[0]
    SkOpSegment.SegVerb.kQuad -> asQuad().dxdyAtT(t)
    SkOpSegment.SegVerb.kConic -> asConic().dxdyAtT(t)
    SkOpSegment.SegVerb.kCubic -> asCubic().dxdyAtT(t)
    SkOpSegment.SegVerb.kUnset -> error("verb not set")
}

/**
 * Mirrors `CurveDIntersectRay[verb]` — accumulates [ray] ↔ this-curve
 * intersections into [ix].
 */
internal fun SkDCurve.intersectRay(ray: SkDLine, ix: SkIntersections) {
    when (fVerb) {
        SkOpSegment.SegVerb.kLine -> ix.intersectRay(asLine(), ray)
        SkOpSegment.SegVerb.kQuad -> ix.intersectRay(asQuad(), ray)
        SkOpSegment.SegVerb.kConic -> ix.intersectRay(asConic(), ray)
        SkOpSegment.SegVerb.kCubic -> ix.intersectRay(asCubic(), ray)
        SkOpSegment.SegVerb.kUnset -> error("verb not set")
    }
}
