/*
 * Copyright 2026 The kanvas-skia authors.
 *
 * Mirrors Skia's `src/pathops/SkPathOpsTCurve.h` — the polymorphic
 * curve abstraction used by the TSect (Bézier-clipping) machinery.
 *
 * Phase D1.1.e.2.a — interface only. Concrete implementations live
 * in [SkTQuad], [SkTConic], [SkTCubic]. The TSect / TSpan classes
 * that consume this interface ship in D1.1.e.2.b/c.
 *
 * # Why an abstraction layer ?
 *
 * The Bézier-clipping intersection algorithm (TSect) is identical
 * for quadratic / cubic / conic — only the per-curve operations
 * (subdivide, evaluate, hull intersection, …) vary. A virtual /
 * polymorphic interface lets `SkTSect.BinarySearch` operate on any
 * curve type uniformly, with zero per-pair specialization.
 */
package org.skia.pathops.internal


import org.skia.math.SkDLine
import org.skia.math.SkDPoint
import org.skia.math.SkDVector
internal interface SkTCurve {
    /** Number of control points stored (3 for quad/conic, 4 for cubic). */
    fun pointCount(): Int

    /** Index of the last control point (`pointCount() - 1`). */
    fun pointLast(): Int

    /** Maximum number of intersections this curve type can produce with another curve. */
    fun maxIntersections(): Int

    /** Returns true if this curve is a [SkDConic] (rational form). */
    fun isConic(): Boolean

    /** Indexed access to the control points. */
    operator fun get(n: Int): SkDPoint

    /** Mutable indexed access (used during subdivision). */
    operator fun set(n: Int, p: SkDPoint)

    /**
     * True if all control points are approximately coincident.
     * Mirrors the per-curve `collapsed()`.
     */
    fun collapsed(): Boolean

    /**
     * True if all interior controls' chord-vector dot products with
     * the chord direction are positive (i.e. the controls don't
     * overshoot the endpoints). Mirrors `controlsInside()`.
     */
    fun controlsInside(): Boolean

    /** Tangent vector `dB/dt` at parameter [t]. */
    fun dxdyAtT(t: Double): SkDVector

    /** Evaluate the curve at parameter [t]. */
    fun ptAtT(t: Double): SkDPoint

    /**
     * Quick reject for hull-overlap tests against another curve.
     * Returns true if the hulls *might* intersect ; the [isLinearOut]
     * `BooleanArray(1)` is set to true if either curve's hull has
     * collapsed to a line.
     */
    fun hullIntersects(quad: SkDQuad, isLinearOut: BooleanArray): Boolean
    fun hullIntersects(conic: SkDConic, isLinearOut: BooleanArray): Boolean
    fun hullIntersects(cubic: SkDCubic, isLinearOut: BooleanArray): Boolean

    /** Polymorphic hull-overlap dispatcher. */
    fun hullIntersects(curve: SkTCurve, isLinearOut: BooleanArray): Boolean

    /** Intersect this curve (treated as the curve-1 input) with [line]. */
    fun intersectRay(intersections: SkIntersections, line: SkDLine): Int

    /**
     * Factory for a fresh instance of the *same* concrete type with
     * uninitialized control points. Used by TSect when allocating
     * sub-curves during binary search. Mirrors the upstream `make`
     * method (the Skia version takes an `SkArenaAlloc&` ; we use
     * Kotlin GC instead).
     */
    fun make(): SkTCurve

    /** Fill [endPt] (length 2) with the two control points other than `pts[oddMan]`. */
    fun otherPts(oddMan: Int, endPt: Array<SkDPoint?>)

    /** Compute and write the curve's tight bounds into [out]. */
    fun setBounds(out: SkDRect)

    /**
     * Compute the sub-curve covering parametric interval `[t1, t2]`
     * and write it into [out] (must be the same concrete type as
     * this).
     */
    fun subDivide(t1: Double, t2: Double, out: SkTCurve)
}
