package org.graphiks.math.geometry

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import org.graphiks.math.vector.Vector2F32

/**
 * Double-precision 2D point/vector. Merges Skia's `SkDPoint` and `SkDVector`
 * into one type; [Vector2F64] is a typealias for [Point2F64].
 */
public data class Point2F64(var x: Double, var y: Double) {

    public constructor() : this(0.0, 0.0)

    // ─── From SkDVector ──────────────────────────────────────────────────

    /** Adds [v] to this vector in place. */
    public operator fun plusAssign(v: Point2F64) { x += v.x; y += v.y }

    /** Subtracts [v] from this vector in place. */
    public operator fun minusAssign(v: Point2F64) { x -= v.x; y -= v.y }

    /** Divides both components by [s] in place. */
    public operator fun divAssign(s: Double) { x /= s; y /= s }

    /** Multiplies both components by [s] in place. */
    public operator fun timesAssign(s: Double) { x *= s; y *= s }

    /** 2-D cross product (scalar z-component). */
    public fun cross(a: Point2F64): Double = x * a.y - y * a.x

    /** Cross product with ULP-based zero check. Mirrors `SkDVector::crossCheck`. */
    public fun crossCheck(a: Point2F64): Double {
        val xy = x * a.y
        val yx = y * a.x
        return if (AlmostEqualUlps(xy, yx)) 0.0 else xy - yx
    }

    /** Cross product with no-normal-check ULP comparison. */
    public fun crossNoNormalCheck(a: Point2F64): Double {
        val xy = x * a.y
        val yx = y * a.x
        return if (AlmostEqualUlpsNoNormalCheck(xy, yx)) 0.0 else xy - yx
    }

    /** Dot product. */
    public fun dot(a: Point2F64): Double = x * a.x + y * a.y

    /** Euclidean length. */
    public fun length(): Double = sqrt(lengthSquared())

    /** Squared Euclidean length. */
    public fun lengthSquared(): Double = x * x + y * y

    /** Normalizes this vector in place. */
    public fun normalize(): Point2F64 {
        val invLen = 1.0 / length()
        x *= invLen
        y *= invLen
        return this
    }

    /** Returns `true` if both components are finite. */
    public fun isFinite(): Boolean = x.isFinite() && y.isFinite()

    // ─── From SkDPoint ───────────────────────────────────────────────────

    /** Sets this point from a [Vector2F32]. */
    public fun set(pt: Vector2F32) { x = pt.x.toDouble(); y = pt.y.toDouble() }

    /** Returns `this + v`. */
    public operator fun plus(v: Point2F64): Point2F64 = Point2F64(x + v.x, y + v.y)

    /** Returns `this - v`. */
    public operator fun minus(v: Point2F64): Point2F64 = Point2F64(x - v.x, y - v.y)

    /**
     * Double-precision approximate equality. Mirrors `SkDPoint::approximatelyDEqual`.
     */
    public fun approximatelyDEqual(a: Point2F64): Boolean {
        if (approximately_equal(x, a.x) && approximately_equal(y, a.y)) return true
        if (!RoughlyEqualUlps(x, a.x) || !RoughlyEqualUlps(y, a.y)) return false
        val dist = distance(a)
        val tiniest = min(min(min(x, a.x), y), a.y)
        var largest = max(max(max(x, a.x), y), a.y)
        largest = max(largest, -tiniest)
        return AlmostDequalUlps(largest, largest + dist)
    }

    /** Double-precision approximate equality with a [Vector2F32]. */
    public fun approximatelyDEqual(a: Vector2F32): Boolean =
        approximatelyDEqual(Point2F64(a.x.toDouble(), a.y.toDouble()))

    /**
     * Standard approximate equality. Mirrors `SkDPoint::approximatelyEqual`.
     */
    public fun approximatelyEqual(a: Point2F64): Boolean {
        if (approximately_equal(x, a.x) && approximately_equal(y, a.y)) return true
        if (!RoughlyEqualUlps(x, a.x) || !RoughlyEqualUlps(y, a.y)) return false
        val dist = distance(a)
        val tiniest = min(min(min(x, a.x), y), a.y)
        var largest = max(max(max(x, a.x), y), a.y)
        largest = max(largest, -tiniest)
        return AlmostPequalUlps(largest, largest + dist)
    }

    /** Approximate equality with a [Vector2F32]. */
    public fun approximatelyEqual(a: Vector2F32): Boolean =
        approximatelyEqual(Point2F64(a.x.toDouble(), a.y.toDouble()))

    /** Returns `true` if both components are approximately zero. */
    public fun approximatelyZero(): Boolean = approximately_zero(x) && approximately_zero(y)

    /** Converts to a [Vector2F32]. */
    public fun asVector2F32(): Vector2F32 = Vector2F32.of(x = x.toFloat(), y = y.toFloat())

    /** Euclidean distance to [a]. */
    public fun distance(a: Point2F64): Double {
        val dx = x - a.x; val dy = y - a.y
        return sqrt(dx * dx + dy * dy)
    }

    /** Squared Euclidean distance to [a]. */
    public fun distanceSquared(a: Point2F64): Double {
        val dx = x - a.x; val dy = y - a.y
        return dx * dx + dy * dy
    }

    /** Rough equality check. Mirrors `SkDPoint::roughlyEqual`. */
    public fun roughlyEqual(a: Point2F64): Boolean {
        if (roughly_equal(x, a.x) && roughly_equal(y, a.y)) return true
        val dist = distance(a)
        val tiniest = min(min(min(x, a.x), y), a.y)
        var largest = max(max(max(x, a.x), y), a.y)
        largest = max(largest, -tiniest)
        return RoughlyEqualUlps(largest, largest + dist)
    }

    public companion object {
        /** Returns the midpoint of [a] and [b]. Mirrors `SkDPoint::Mid`. */
        public fun Mid(a: Point2F64, b: Point2F64): Point2F64 =
            Point2F64((a.x + b.x) / 2, (a.y + b.y) / 2)

        /** Approximate equality for [Vector2F32] points. */
        public fun ApproximatelyEqual(a: Vector2F32, b: Vector2F32): Boolean {
            if (approximately_equal(a.x.toDouble(), b.x.toDouble())
                && approximately_equal(a.y.toDouble(), b.y.toDouble())) return true
            if (!RoughlyEqualUlps(a.x, b.x) || !RoughlyEqualUlps(a.y, b.y)) return false
            val dA = Point2F64(a.x.toDouble(), a.y.toDouble())
            val dB = Point2F64(b.x.toDouble(), b.y.toDouble())
            val dist = dA.distance(dB)
            val tiniest = min(min(min(a.x, b.x), a.y), b.y)
            var largest = max(max(max(a.x, b.x), a.y), b.y)
            largest = max(largest, -tiniest)
            return AlmostDequalUlps(largest.toDouble(), largest.toDouble() + dist)
        }

        /** Rough equality for [Vector2F32] points. */
        public fun RoughlyEqual(a: Vector2F32, b: Vector2F32): Boolean {
            if (!RoughlyEqualUlps(a.x, b.x) && !RoughlyEqualUlps(a.y, b.y)) return false
            val dA = Point2F64(a.x.toDouble(), a.y.toDouble())
            val dB = Point2F64(b.x.toDouble(), b.y.toDouble())
            val dist = dA.distance(dB)
            val tiniest = min(min(min(a.x, b.x), a.y), b.y)
            var largest = max(max(max(a.x, b.x), a.y), b.y)
            largest = max(largest, -tiniest)
            return AlmostDequalUlps(largest.toDouble(), largest.toDouble() + dist)
        }

        /** Very rough equality for [Vector2F32] points. */
        public fun WayRoughlyEqual(a: Vector2F32, b: Vector2F32): Boolean {
            val largest = max(
                kotlin.math.abs(a.x),
                max(kotlin.math.abs(a.y), max(kotlin.math.abs(b.x), kotlin.math.abs(b.y))),
            )
            val dx = a.x - b.x; val dy = a.y - b.y
            val largestDiff = max(kotlin.math.abs(dx), kotlin.math.abs(dy))
            return roughly_zero_when_compared_to(largestDiff.toDouble(), largest.toDouble())
        }
    }
}

/**
 * Alias for [Point2F64] — the same type is used for both points and
 * vectors, mirroring Skia's convention.
 */
public typealias Vector2F64 = Point2F64
