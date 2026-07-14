package org.graphiks.math.geometry

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import org.graphiks.math.vector.Vector2F32

/**
 * Double-precision 2D line segment.
 *
 * Used by path-ops (path operations) for exact geometric intersection
 * calculations. The companion object provides static helpers for
 * finding exact and approximate points on horizontal/vertical edges.
 */
public data class Line2F64(val pts: Array<Point2F64> = arrayOf(Point2F64(), Point2F64())) {

    init {
        require(pts.size == 2) { "Line2F64 requires exactly 2 points (got ${pts.size})" }
    }

    public operator fun get(n: Int): Point2F64 {
        require(n in 0..1)
        return pts[n]
    }

    public operator fun set(n: Int, p: Point2F64) {
        require(n in 0..1)
        pts[n] = p
    }

    /** Sets both endpoints from [Vector2F32] points. */
    fun set(p0: Vector2F32, p1: Vector2F32): Line2F64 {
        pts[0] = Point2F64(p0.x.toDouble(), p0.y.toDouble())
        pts[1] = Point2F64(p1.x.toDouble(), p1.y.toDouble())
        return this
    }

    /** Returns the point at parameter `t` along this segment (0 → p0, 1 → p1). */
    fun ptAtT(t: Double): Point2F64 {
        if (0.0 == t) return pts[0]
        if (1.0 == t) return pts[1]
        val oneT = 1 - t
        return Point2F64(
            oneT * pts[0].x + t * pts[1].x,
            oneT * pts[0].y + t * pts[1].y,
        )
    }

    /** Returns 0.0 or 1.0 if [xy] exactly equals an endpoint, otherwise -1.0. */
    fun exactPoint(xy: Point2F64): Double = when {
        xy == pts[0] -> 0.0
        xy == pts[1] -> 1.0
        else -> -1.0
    }

    /**
     * Returns the parameter `t` of [xy] on this segment if the point
     * lies approximately on it, otherwise -1.0. Mirrors
     * `Line::nearPoint`.
     */
    fun nearPoint(xy: Point2F64, unequal: BooleanArray? = null): Double {
        if (!AlmostBetweenUlps(pts[0].x, xy.x, pts[1].x)
            || !AlmostBetweenUlps(pts[0].y, xy.y, pts[1].y)) return -1.0
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
     * Returns `true` if [xy] lies approximately on the ray through
     * this segment
     */
    fun nearRay(xy: Point2F64): Boolean {
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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Line2F64) return false
        return pts[0] == other.pts[0] && pts[1] == other.pts[1]
    }

    override fun hashCode(): Int = 31 * pts[0].hashCode() + pts[1].hashCode()

    companion object {
        /** Performs the computation. */
        fun ExactPointH(xy: Point2F64, left: Double, right: Double, y: Double): Double {
            if (xy.y == y) {
                if (xy.x == left) return 0.0
                if (xy.x == right) return 1.0
            }
            return -1.0
        }

        /** Performs the computation. */
        fun NearPointH(xy: Point2F64, left: Double, right: Double, y: Double): Double {
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

        /** Performs the computation. */
        fun ExactPointV(xy: Point2F64, top: Double, bottom: Double, x: Double): Double {
            if (xy.x == x) {
                if (xy.y == top) return 0.0
                if (xy.y == bottom) return 1.0
            }
            return -1.0
        }

        /** Performs the computation. */
        fun NearPointV(xy: Point2F64, top: Double, bottom: Double, x: Double): Double {
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
