package org.graphiks.math.geometry

import org.graphiks.math.vector.Vector2F64
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Double-precision 2D line segment.
 *
 * Used by path-ops (path operations) for exact geometric intersection
 * calculations. The companion object provides static helpers for
 * finding exact and approximate points on horizontal/vertical edges.
 */
public class Line2F64(source: Array<Point2F64>) {

    init {
        require(source.size == 2) { "Line2F64 requires exactly 2 points (got ${source.size})" }
    }

    private val pts: Array<Point2F64> = arrayOf(source[0], source[1])

    public constructor() : this(arrayOf(Point2F64.Origin, Point2F64.Origin))

    public val start: Point2F64 get() = pts[0]

    public val end: Point2F64 get() = pts[1]

    public fun direction(): Vector2F64 = end - start

    public operator fun get(n: Int): Point2F64 {
        require(n in 0..1)
        return pts[n]
    }

    public operator fun set(n: Int, p: Point2F64) {
        require(n in 0..1)
        pts[n] = p
    }

    /** Returns a defensive copy of the endpoints array. */
    public fun toPointsArray(): Array<Point2F64> = arrayOf(pts[0], pts[1])

    /** Sets both endpoints from [Point2F32] values. */
    fun set(p0: Point2F32, p1: Point2F32): Line2F64 {
        pts[0] = p0.toPoint2F64()
        pts[1] = p1.toPoint2F64()
        return this
    }

    /** Returns the point at parameter `t` along this segment (0 → p0, 1 → p1). */
    fun ptAtT(t: Double): Point2F64 {
        if (0.0 == t) return Point2F64(pts[0].x, pts[0].y)
        if (1.0 == t) return Point2F64(pts[1].x, pts[1].y)
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
        if (!almostBetweenUlpsF64(pts[0].x, xy.x, pts[1].x)
            || !almostBetweenUlpsF64(pts[0].y, xy.y, pts[1].y)) return -1.0
        val len: Vector2F64 = pts[1] - pts[0]
        val denom = len.x * len.x + len.y * len.y
        val ab0: Vector2F64 = xy - pts[0]
        val numer = len.dot(ab0)
        if (!isBetweenF64(0.0, numer, denom)) return -1.0
        if (denom == 0.0) return 0.0
        var t = numer / denom
        val realPt: Point2F64 = ptAtT(t)
        val dist = pathOpsDistanceTo(realPt, xy)
        val tiniest = min(min(min(pts[0].x, pts[0].y), pts[1].x), pts[1].y)
        var largest = max(max(max(pts[0].x, pts[0].y), pts[1].x), pts[1].y)
        largest = max(largest, -tiniest)
        if (!PathPredicatesF64.almostEqualUlps(largest, largest + dist)) return -1.0
        if (unequal != null && unequal.isNotEmpty()) {
            unequal[0] = largest.toFloat() != (largest + dist).toFloat()
        }
        t = pinUnitIntervalF64(t)
        return t
    }

    /**
     * Returns `true` if [xy] lies approximately on the ray through
     * this segment
     */
    fun nearRay(xy: Point2F64): Boolean {
        val len: Vector2F64 = pts[1] - pts[0]
        val denom = len.x * len.x + len.y * len.y
        val ab0: Vector2F64 = xy - pts[0]
        val numer = len.dot(ab0)
        val t = numer / denom
        val realPt: Point2F64 = ptAtT(t)
        val dist = pathOpsDistanceTo(realPt, xy)
        val tiniest = min(min(min(pts[0].x, pts[0].y), pts[1].x), pts[1].y)
        var largest = max(max(max(pts[0].x, pts[0].y), pts[1].x), pts[1].y)
        largest = max(largest, -tiniest)
        return PathPredicatesF64.almostEqualUlps(largest, largest + dist, maxUlps = 256)
    }

    override fun equals(other: Any?): Boolean {
        if (other !is Line2F64) return false
        return pts[0] == other.pts[0] && pts[1] == other.pts[1]
    }

    override fun hashCode(): Int = 31 * pts[0].hashCode() + pts[1].hashCode()

    override fun toString(): String = "Line2F64([${pts[0].x}, ${pts[0].y}], [${pts[1].x}, ${pts[1].y}])"

    companion object {
        /** Performs the computation. */
        fun exactPointH(xy: Point2F64, left: Double, right: Double, y: Double): Double {
            if (xy.y == y) {
                if (xy.x == left) return 0.0
                if (xy.x == right) return 1.0
            }
            return -1.0
        }

        /** Performs the computation. */
        fun nearPointH(xy: Point2F64, left: Double, right: Double, y: Double): Double {
            if (!PathPredicatesF64.almostEqualUlps(xy.y, y, maxUlps = 2)) return -1.0
            if (!almostBetweenUlpsF64(left, xy.x, right)) return -1.0
            var t = (xy.x - left) / (right - left)
            t = pinUnitIntervalF64(t)
            val realPtX = (1 - t) * left + t * right
            val dx = xy.y - y; val dy = xy.x - realPtX
            val dist = sqrt(dx * dx + dy * dy)
            val tiniest = min(min(y, left), right)
            var largest = max(max(y, left), right)
            largest = max(largest, -tiniest)
            if (!PathPredicatesF64.almostEqualUlps(largest, largest + dist)) return -1.0
            return t
        }

        /** Performs the computation. */
        fun exactPointV(xy: Point2F64, top: Double, bottom: Double, x: Double): Double {
            if (xy.x == x) {
                if (xy.y == top) return 0.0
                if (xy.y == bottom) return 1.0
            }
            return -1.0
        }

        /** Performs the computation. */
        fun nearPointV(xy: Point2F64, top: Double, bottom: Double, x: Double): Double {
            if (!PathPredicatesF64.almostEqualUlps(xy.x, x, maxUlps = 2)) return -1.0
            if (!almostBetweenUlpsF64(top, xy.y, bottom)) return -1.0
            var t = (xy.y - top) / (bottom - top)
            t = pinUnitIntervalF64(t)
            val realPtY = (1 - t) * top + t * bottom
            val dx = xy.x - x; val dy = xy.y - realPtY
            val dist = sqrt(dx * dx + dy * dy)
            val tiniest = min(min(x, top), bottom)
            var largest = max(max(x, top), bottom)
            largest = max(largest, -tiniest)
            if (!PathPredicatesF64.almostEqualUlps(largest, largest + dist)) return -1.0
            return t
        }
    }
}

/** Retains the raw PathOps distance formula used by the near-point predicates. */
private fun pathOpsDistanceTo(from: Point2F64, to: Point2F64): Double {
    val dx = from.x - to.x
    val dy = from.y - to.y
    return sqrt(dx * dx + dy * dy)
}

private fun almostBetweenUlpsF64(a: Double, b: Double, c: Double): Boolean {
    val lower = min(a, c)
    val upper = max(a, c)
    return (b >= lower || PathPredicatesF64.almostEqualUlps(lower, b, maxUlps = 2)) &&
        (b <= upper || PathPredicatesF64.almostEqualUlps(b, upper, maxUlps = 2))
}

private fun isBetweenF64(a: Double, b: Double, c: Double): Boolean = (a - b) * (c - b) <= 0.0

private fun pinUnitIntervalF64(t: Double): Double = when {
    t < PathPredicatesF64.EPSILON_F64 * 4.0 -> 0.0
    t > 1.0 - PathPredicatesF64.EPSILON_F64 * 4.0 -> 1.0
    else -> t
}
