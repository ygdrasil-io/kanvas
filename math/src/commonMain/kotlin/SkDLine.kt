/*
 * Copyright 2026 The Kanvas authors.
 *
 * Mirrors Skia's `src/pathops/SkPathOpsLine.{h,cpp}` — `SkDLine`, the
 * double-precision line segment used by the pathops machinery.
 *
 * Phase D1.1.a — port of the data type + parametric helpers
 * (`ptAtT`, `exactPoint`, `nearPoint`, `nearRay`) and the H/V
 * convenience overloads. The actual line ↔ line / line ↔ curve
 * intersections live in subsequent slices (D1.1.b / D1.1.c).
 */
package org.graphiks.math

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import org.graphiks.math.SkPoint

/**
 * A line segment from `pts[0]` to `pts[1]` in double precision.
 * Mirrors
 * [`SkDLine`](https://github.com/google/skia/blob/main/src/pathops/SkPathOpsLine.h#L14).
 */
public data class SkDLine(val pts: Array<SkDPoint> = arrayOf(SkDPoint(), SkDPoint())) {

    init {
        require(pts.size == 2) { "SkDLine requires exactly 2 points (got ${pts.size})" }
    }

    operator fun get(n: Int): SkDPoint {
        require(n in 0..1)
        return pts[n]
    }

    operator fun set(n: Int, p: SkDPoint) {
        require(n in 0..1)
        pts[n] = p
    }

    /** Mirrors `SkDLine::set(const SkPoint pts[2])`. */
    fun set(p0: SkPoint, p1: SkPoint): SkDLine {
        pts[0] = SkDPoint(p0.fX.toDouble(), p0.fY.toDouble())
        pts[1] = SkDPoint(p1.fX.toDouble(), p1.fY.toDouble())
        return this
    }

    /**
     * Evaluate the line at parameter [t] (0 → `pts[0]`, 1 → `pts[1]`).
     * Mirrors `SkDLine::ptAtT`.
     */
    fun ptAtT(t: Double): SkDPoint {
        if (0.0 == t) return pts[0]
        if (1.0 == t) return pts[1]
        val oneT = 1 - t
        return SkDPoint(
            oneT * pts[0].x + t * pts[1].x,
            oneT * pts[0].y + t * pts[1].y,
        )
    }

    /**
     * Returns 0 / 1 if [xy] is exactly an endpoint, -1 otherwise.
     * Mirrors `SkDLine::exactPoint`.
     */
    fun exactPoint(xy: SkDPoint): Double = when {
        xy == pts[0] -> 0.0
        xy == pts[1] -> 1.0
        else -> -1.0
    }

    /**
     * Find the t-value of [xy] on this line if it lies near the segment
     * (within ULPs tolerance scaled by the segment's coordinate range).
     * Returns -1 if [xy] is not near the segment.
     *
     * Mirrors `SkDLine::nearPoint`.
     *
     * @param unequal optional out-param ; set to true if [xy] differs
     *                from the projected point at single-precision.
     */
    fun nearPoint(xy: SkDPoint, unequal: BooleanArray? = null): Double {
        if (!AlmostBetweenUlps(pts[0].x, xy.x, pts[1].x)
            || !AlmostBetweenUlps(pts[0].y, xy.y, pts[1].y)) return -1.0
        // project a perpendicular ray from xy to the line ; find t.
        val len = pts[1] - pts[0]
        val denom = len.x * len.x + len.y * len.y
        val ab0 = xy - pts[0]
        val numer = len.x * ab0.x + ab0.y * len.y
        if (!between(0.0, numer, denom)) return -1.0
        if (denom == 0.0) return 0.0
        var t = numer / denom
        val realPt = ptAtT(t)
        val dist = realPt.distance(xy)
        val tiniest = min(min(min(pts[0].x, pts[0].y), pts[1].x), pts[1].y)
        var largest = max(max(max(pts[0].x, pts[0].y), pts[1].x), pts[1].y)
        largest = max(largest, -tiniest)
        if (!AlmostEqualUlpsPin(largest, largest + dist)) return -1.0
        if (unequal != null && unequal.isNotEmpty()) {
            unequal[0] = largest.toFloat() != (largest + dist).toFloat()
        }
        t = SkPinT(t)
        return t
    }

    /**
     * Returns true if [xy] is near the infinite ray of this line
     * (uses [RoughlyEqualUlps], no segment-bounds check).
     * Mirrors `SkDLine::nearRay`.
     */
    fun nearRay(xy: SkDPoint): Boolean {
        val len = pts[1] - pts[0]
        val denom = len.x * len.x + len.y * len.y
        val ab0 = xy - pts[0]
        val numer = len.x * ab0.x + ab0.y * len.y
        val t = numer / denom
        val realPt = ptAtT(t)
        val dist = realPt.distance(xy)
        val tiniest = min(min(min(pts[0].x, pts[0].y), pts[1].x), pts[1].y)
        var largest = max(max(max(pts[0].x, pts[0].y), pts[1].x), pts[1].y)
        largest = max(largest, -tiniest)
        return RoughlyEqualUlps(largest, largest + dist)
    }

    // ─── Equality (default array equality is referential — override) ──

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SkDLine) return false
        return pts[0] == other.pts[0] && pts[1] == other.pts[1]
    }

    override fun hashCode(): Int = 31 * pts[0].hashCode() + pts[1].hashCode()

    companion object {
        /**
         * Fast-path : test if [xy] lies on a horizontal segment from
         * `(left, y)` to `(right, y)` with exact-equal endpoints.
         * Mirrors `SkDLine::ExactPointH`.
         */
        fun ExactPointH(xy: SkDPoint, left: Double, right: Double, y: Double): Double {
            if (xy.y == y) {
                if (xy.x == left) return 0.0
                if (xy.x == right) return 1.0
            }
            return -1.0
        }

        /** Mirrors `SkDLine::NearPointH`. */
        fun NearPointH(xy: SkDPoint, left: Double, right: Double, y: Double): Double {
            if (!AlmostBequalUlps(xy.y, y)) return -1.0
            if (!AlmostBetweenUlps(left, xy.x, right)) return -1.0
            var t = (xy.x - left) / (right - left)
            t = SkPinT(t)
            val realPtX = (1 - t) * left + t * right
            val dx = xy.y - y; val dy = xy.x - realPtX
            val dist = sqrt(dx * dx + dy * dy)
            val tiniest = min(min(y, left), right)
            var largest = max(max(y, left), right)
            largest = max(largest, -tiniest)
            if (!AlmostEqualUlps(largest, largest + dist)) return -1.0
            return t
        }

        /** Vertical analogue of [ExactPointH]. Mirrors `SkDLine::ExactPointV`. */
        fun ExactPointV(xy: SkDPoint, top: Double, bottom: Double, x: Double): Double {
            if (xy.x == x) {
                if (xy.y == top) return 0.0
                if (xy.y == bottom) return 1.0
            }
            return -1.0
        }

        /** Mirrors `SkDLine::NearPointV`. */
        fun NearPointV(xy: SkDPoint, top: Double, bottom: Double, x: Double): Double {
            if (!AlmostBequalUlps(xy.x, x)) return -1.0
            if (!AlmostBetweenUlps(top, xy.y, bottom)) return -1.0
            var t = (xy.y - top) / (bottom - top)
            t = SkPinT(t)
            val realPtY = (1 - t) * top + t * bottom
            val dx = xy.x - x; val dy = xy.y - realPtY
            val dist = sqrt(dx * dx + dy * dy)
            val tiniest = min(min(x, top), bottom)
            var largest = max(max(x, top), bottom)
            largest = max(largest, -tiniest)
            if (!AlmostEqualUlps(largest, largest + dist)) return -1.0
            return t
        }
    }
}
