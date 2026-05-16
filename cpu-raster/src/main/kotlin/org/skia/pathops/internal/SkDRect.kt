/*
 * Copyright 2026 The kanvas-skia authors.
 *
 * Mirrors Skia's `src/pathops/SkPathOpsRect.{h,cpp}` — `SkDRect`,
 * the double-precision rectangle used by the pathops machinery to
 * track curve / segment bounds.
 *
 * Phases :
 *  - D1.1.a — data type + add / contains / intersects.
 *  - D1.1.b — `setBounds(SkDQuad/Cubic/Conic, sub, startT, endT)`
 *    overloads (curve-tight bounds via per-axis extrema).
 */
package org.skia.pathops.internal


import org.graphiks.math.SkDPoint
import org.graphiks.math.approximately_between
import kotlin.math.max
import kotlin.math.min

/**
 * Double-precision rectangle. Mirrors
 * [`SkDRect`](https://github.com/google/skia/blob/main/src/pathops/SkPathOpsRect.h#L21).
 *
 * Field names use Kotlin convention (`left` / `top` / `right` / `bottom`) ;
 * upstream uses `fLeft` / `fTop` / `fRight` / `fBottom`.
 */
internal data class SkDRect(
    var left: Double = 0.0,
    var top: Double = 0.0,
    var right: Double = 0.0,
    var bottom: Double = 0.0,
) {

    /** Mirrors `SkDRect::add(const SkDPoint&)`. */
    fun add(pt: SkDPoint) {
        left = min(left, pt.x)
        top = min(top, pt.y)
        right = max(right, pt.x)
        bottom = max(bottom, pt.y)
    }

    /** Mirrors `SkDRect::contains`. */
    fun contains(pt: SkDPoint): Boolean =
        approximately_between(left, pt.x, right)
            && approximately_between(top, pt.y, bottom)

    /**
     * Mirrors `SkDRect::intersects`. Pre-condition : both rects are
     * sorted (left ≤ right, top ≤ bottom).
     */
    fun intersects(r: SkDRect): Boolean =
        r.left <= right && left <= r.right && r.top <= bottom && top <= r.bottom

    /** Mirrors `SkDRect::set(const SkDPoint&)` — collapse to a single point. */
    fun set(pt: SkDPoint) {
        left = pt.x; right = pt.x
        top = pt.y; bottom = pt.y
    }

    fun width(): Double = right - left
    fun height(): Double = bottom - top

    /** Mirrors `SkDRect::valid` — true iff `left ≤ right && top ≤ bottom`. */
    fun valid(): Boolean = left <= right && top <= bottom

    /**
     * Mirrors the upstream `debugInit` (sets all fields to NaN so any
     * use without a prior `setBounds` is loud at runtime). We use
     * `Double.NaN` ; in `valid()` and `intersects()` NaN returns
     * false, which matches the upstream "loud failure" intent.
     */
    fun debugInit() {
        left = Double.NaN
        top = Double.NaN
        right = Double.NaN
        bottom = Double.NaN
    }

    // ─── Curve-tight setBounds (Phase D1.1.b) ────────────────────────
    //
    // Each overload sets the rect to the tight bounds of `sub` (a
    // sub-curve of `curve` between parameters `startT` and `endT`).
    // Algorithm : seed with the endpoints, then add interior extrema
    // (per-axis derivative roots in [0, 1]). Mirrors
    // `SkDRect::setBounds(SkDQuad/Cubic/Conic, sub, startT, endT)` in
    // `src/pathops/SkPathOpsRect.cpp`.

    /** Tight bounds of a sub-quadratic. */
    fun setBounds(curve: SkDQuad, sub: SkDQuad, startT: Double, endT: Double) {
        set(sub[0])
        add(sub[2])
        val tValues = DoubleArray(2)
        var roots = 0
        if (!sub.monotonicInX()) {
            roots = SkDQuad.FindExtrema(doubleArrayOf(sub[0].x, sub[1].x, sub[2].x), tValues)
        }
        if (!sub.monotonicInY()) {
            val rest = DoubleArray(1)
            val n = SkDQuad.FindExtrema(doubleArrayOf(sub[0].y, sub[1].y, sub[2].y), rest)
            if (n > 0) tValues[roots++] = rest[0]
        }
        for (index in 0 until roots) {
            val t = startT + (endT - startT) * tValues[index]
            add(curve.ptAtT(t))
        }
    }

    /** Tight bounds of a sub-conic. */
    fun setBounds(curve: SkDConic, sub: SkDConic, startT: Double, endT: Double) {
        set(sub[0])
        add(sub[2])
        val tValues = DoubleArray(2)
        var roots = 0
        if (!sub.monotonicInX()) {
            roots = SkDConic.FindExtrema(doubleArrayOf(sub[0].x, sub[1].x, sub[2].x), sub.weight, tValues)
        }
        if (!sub.monotonicInY()) {
            val rest = DoubleArray(1)
            val n = SkDConic.FindExtrema(doubleArrayOf(sub[0].y, sub[1].y, sub[2].y), sub.weight, rest)
            if (n > 0) tValues[roots++] = rest[0]
        }
        for (index in 0 until roots) {
            val t = startT + (endT - startT) * tValues[index]
            add(curve.ptAtT(t))
        }
    }

    /** Tight bounds of a sub-cubic. */
    fun setBounds(curve: SkDCubic, sub: SkDCubic, startT: Double, endT: Double) {
        set(sub[0])
        add(sub[3])
        val tValues = DoubleArray(4)
        var roots = 0
        if (!sub.monotonicInX()) {
            roots = SkDCubic.FindExtrema(doubleArrayOf(sub[0].x, sub[1].x, sub[2].x, sub[3].x), tValues)
        }
        if (!sub.monotonicInY()) {
            val rest = DoubleArray(2)
            val n = SkDCubic.FindExtrema(doubleArrayOf(sub[0].y, sub[1].y, sub[2].y, sub[3].y), rest)
            for (i in 0 until n) tValues[roots++] = rest[i]
        }
        for (index in 0 until roots) {
            val t = startT + (endT - startT) * tValues[index]
            add(curve.ptAtT(t))
        }
    }

    /** Convenience overload : full-range tight bounds (`startT=0, endT=1`). */
    fun setBounds(curve: SkDQuad) = setBounds(curve, curve, 0.0, 1.0)

    /** Convenience overload : full-range tight bounds. */
    fun setBounds(curve: SkDConic) = setBounds(curve, curve, 0.0, 1.0)

    /** Convenience overload : full-range tight bounds. */
    fun setBounds(curve: SkDCubic) = setBounds(curve, curve, 0.0, 1.0)
}
