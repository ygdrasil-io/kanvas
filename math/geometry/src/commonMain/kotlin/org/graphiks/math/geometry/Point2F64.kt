package org.graphiks.math.geometry

import org.graphiks.math.geometry.PathOpsEpsilon.almostDEqualUlps
import org.graphiks.math.geometry.PathOpsEpsilon.almostEqualUlps
import org.graphiks.math.geometry.PathOpsEpsilon.almostEqualUlpsNoNormalCheck
import org.graphiks.math.geometry.PathOpsEpsilon.almostPEqualUlps
import org.graphiks.math.geometry.PathOpsEpsilon.approximatelyEqual
import org.graphiks.math.geometry.PathOpsEpsilon.approximatelyZero
import org.graphiks.math.geometry.PathOpsEpsilon.roughlyEqual
import org.graphiks.math.geometry.PathOpsEpsilon.roughlyEqualUlps
import org.graphiks.math.geometry.PathOpsEpsilon.roughlyZeroWhenComparedTo
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import org.graphiks.math.vector.Vector2F32

import kotlin.ConsistentCopyVisibility

/**
 * Double-precision 2D point/vector. [Vector2F64] is a typealias for [Point2F64].
 *
 * Mutable by design — used by the pathops intersection machinery for in-place
 * operations. Use [Point2F64.of] to construct from components.
 */
@ConsistentCopyVisibility
public data class Point2F64 internal constructor(var x: Double, var y: Double) {

    internal constructor() : this(0.0, 0.0)

    // ─── Vector operations ──────────────────────────────────────────────────

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

    /** Cross product with ULP-based zero check */
    public fun crossCheck(a: Point2F64): Double {
        val xy = x * a.y
        val yx = y * a.x
        return if (almostEqualUlps(xy, yx)) 0.0 else xy - yx
    }

    /** Cross product with no-normal-check ULP comparison. */
    public fun crossNoNormalCheck(a: Point2F64): Double {
        val xy = x * a.y
        val yx = y * a.x
        return if (almostEqualUlpsNoNormalCheck(xy, yx)) 0.0 else xy - yx
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

    // ─── Point operations ───────────────────────────────────────────────────

    /** Sets this point from a [Vector2F32]. */
    public fun set(pt: Vector2F32) { x = pt.x.toDouble(); y = pt.y.toDouble() }

    /** Returns `this + v`. */
    public operator fun plus(v: Point2F64): Point2F64 = Point2F64(x + v.x, y + v.y)

    /** Returns `this - v`. */
    public operator fun minus(v: Point2F64): Point2F64 = Point2F64(x - v.x, y - v.y)

    /**
     * Double-precision approximate equality
     */
    public fun approximatelyDEqual(a: Point2F64): Boolean {
        if (approximatelyEqual(x, a.x) && approximatelyEqual(y, a.y)) return true
        if (!roughlyEqualUlps(x, a.x) || !roughlyEqualUlps(y, a.y)) return false
        val dist = distance(a)
        val tiniest = min(min(min(x, a.x), y), a.y)
        var largest = max(max(max(x, a.x), y), a.y)
        largest = max(largest, -tiniest)
        return almostDEqualUlps(largest, largest + dist)
    }

    /** Double-precision approximate equality with a [Vector2F32]. */
    public fun approximatelyDEqual(a: Vector2F32): Boolean =
        approximatelyDEqual(Point2F64(a.x.toDouble(), a.y.toDouble()))

    /**
     * Standard approximate equality
     */
    public fun approximatelyEqual(a: Point2F64): Boolean {
        if (approximatelyEqual(x, a.x) && approximatelyEqual(y, a.y)) return true
        if (!roughlyEqualUlps(x, a.x) || !roughlyEqualUlps(y, a.y)) return false
        val dist = distance(a)
        val tiniest = min(min(min(x, a.x), y), a.y)
        var largest = max(max(max(x, a.x), y), a.y)
        largest = max(largest, -tiniest)
        return almostPEqualUlps(largest, largest + dist)
    }

    /** Approximate equality with a [Vector2F32]. */
    public fun approximatelyEqual(a: Vector2F32): Boolean =
        approximatelyEqual(Point2F64(a.x.toDouble(), a.y.toDouble()))

    /** Returns `true` if both components are approximately zero. */
    public fun approximatelyZero(): Boolean = approximatelyZero(x) && approximatelyZero(y)

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

    /** Rough equality check */
    public fun roughlyEqual(a: Point2F64): Boolean {
        if (roughlyEqual(x, a.x) && roughlyEqual(y, a.y)) return true
        val dist = distance(a)
        val tiniest = min(min(min(x, a.x), y), a.y)
        var largest = max(max(max(x, a.x), y), a.y)
        largest = max(largest, -tiniest)
        return roughlyEqualUlps(largest, largest + dist)
    }

    public companion object {
        /** Creates a [Point2F64] from components. */
        public fun of(x: Double = 0.0, y: Double = 0.0): Point2F64 = Point2F64(x, y)

        /** Creates a [Point2F64] from a [Vector2F32]. */
        public fun from(v: Vector2F32): Point2F64 = Point2F64(v.x.toDouble(), v.y.toDouble())

        /** Returns the midpoint of [a] and [b]. */
        public fun midpoint(a: Point2F64, b: Point2F64): Point2F64 =
            Point2F64((a.x + b.x) / 2, (a.y + b.y) / 2)

        /** Approximate equality for [Vector2F32] points. */
        public fun approximatelyEqualVectors(a: Vector2F32, b: Vector2F32): Boolean {
            if (approximatelyEqual(a.x.toDouble(), b.x.toDouble())
                && approximatelyEqual(a.y.toDouble(), b.y.toDouble())) return true
            if (!roughlyEqualUlps(a.x, b.x) || !roughlyEqualUlps(a.y, b.y)) return false
            val dA = Point2F64(a.x.toDouble(), a.y.toDouble())
            val dB = Point2F64(b.x.toDouble(), b.y.toDouble())
            val dist = dA.distance(dB)
            val tiniest = min(min(min(a.x, b.x), a.y), b.y)
            var largest = max(max(max(a.x, b.x), a.y), b.y)
            largest = max(largest, -tiniest)
            return almostDEqualUlps(largest.toDouble(), largest.toDouble() + dist)
        }

        /** Rough equality for [Vector2F32] points. */
        public fun roughlyEqualVectors(a: Vector2F32, b: Vector2F32): Boolean {
            if (!roughlyEqualUlps(a.x, b.x) && !roughlyEqualUlps(a.y, b.y)) return false
            val dA = Point2F64(a.x.toDouble(), a.y.toDouble())
            val dB = Point2F64(b.x.toDouble(), b.y.toDouble())
            val dist = dA.distance(dB)
            val tiniest = min(min(min(a.x, b.x), a.y), b.y)
            var largest = max(max(max(a.x, b.x), a.y), b.y)
            largest = max(largest, -tiniest)
            return almostDEqualUlps(largest.toDouble(), largest.toDouble() + dist)
        }

        /** Very rough equality for [Vector2F32] points. */
        public fun wayRoughlyEqualVectors(a: Vector2F32, b: Vector2F32): Boolean {
            val largest = max(
                kotlin.math.abs(a.x),
                max(kotlin.math.abs(a.y), max(kotlin.math.abs(b.x), kotlin.math.abs(b.y))),
            )
            val dx = a.x - b.x; val dy = a.y - b.y
            val largestDiff = max(kotlin.math.abs(dx), kotlin.math.abs(dy))
            return roughlyZeroWhenComparedTo(largestDiff.toDouble(), largest.toDouble())
        }
    }
}

/**
 * Alias for [Point2F64] — the same type is used for both points and
 * vectors.
 */
public typealias Vector2F64 = Point2F64
