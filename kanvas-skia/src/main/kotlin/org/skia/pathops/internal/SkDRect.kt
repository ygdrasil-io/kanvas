/*
 * Copyright 2026 The kanvas-skia authors.
 *
 * Mirrors Skia's `src/pathops/SkPathOpsRect.h` — `SkDRect`, the
 * double-precision rectangle used by the pathops machinery to track
 * curve / segment bounds.
 *
 * Phase D1.1.a — port of the data type + add / contains / intersects.
 * The `setBounds(SkDQuad/Cubic/Conic, ...)` overloads need the
 * curve types from D1.1.b and are deferred to that slice.
 */
package org.skia.pathops.internal

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
}
