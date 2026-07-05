/*
 * Copyright 2026 The Kanvas authors.
 *
 * Mirrors Skia's `src/pathops/SkPathOpsPoint.h` — `SkDPoint` and
 * `SkDVector` (double-precision point and direction in 2D).
 *
 * Phase D1.1.a — port of the data types + the equality / distance /
 * midpoint helpers used by all subsequent intersection routines.
 *
 * Test-only operator overloads in upstream (`+=`, `-=`, `/=`, `*=`)
 * are ported as Kotlin operator funs since they're cheap and the
 * pathops tests will use them.
 */
package org.graphiks.math

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import org.graphiks.math.SkPoint

/**
 * Double-precision 2D vector. Mirrors
 * [`SkDVector`](https://github.com/google/skia/blob/main/src/pathops/SkPathOpsPoint.h#L19).
 *
 * Field names use Kotlin convention (`x` / `y`) ; in upstream they are
 * `fX` / `fY`.
 */
public data class SkDVector(var x: Double, var y: Double) {

    constructor() : this(0.0, 0.0)

    /** Mirrors `SkDVector::operator+=(const SkDVector&)`. */
    operator fun plusAssign(v: SkDVector) { x += v.x; y += v.y }

    /** Mirrors `SkDVector::operator-=(const SkDVector&)`. */
    operator fun minusAssign(v: SkDVector) { x -= v.x; y -= v.y }

    /** Mirrors `SkDVector::operator/=(double)`. */
    operator fun divAssign(s: Double) { x /= s; y /= s }

    /** Mirrors `SkDVector::operator*=(double)`. */
    operator fun timesAssign(s: Double) { x *= s; y *= s }

    /** Mirrors `SkDVector::asSkVector`. */
    fun asSkPoint(): SkPoint = SkPoint(fX = x.toFloat(), fY = y.toFloat())

    /** 2D scalar cross product. Mirrors `SkDVector::cross`. */
    fun cross(a: SkDVector): Double = x * a.y - y * a.x

    /**
     * Cross product that treats nearly-coincident inputs as zero
     * (ULPs epsilon = 16). Mirrors `SkDVector::crossCheck`.
     */
    fun crossCheck(a: SkDVector): Double {
        val xy = x * a.y
        val yx = y * a.x
        return if (AlmostEqualUlps(xy, yx)) 0.0 else xy - yx
    }

    /**
     * Cross product without the denormal-skip (allows tinier numbers
     * to compare equal). Mirrors `SkDVector::crossNoNormalCheck`.
     */
    fun crossNoNormalCheck(a: SkDVector): Double {
        val xy = x * a.y
        val yx = y * a.x
        return if (AlmostEqualUlpsNoNormalCheck(xy, yx)) 0.0 else xy - yx
    }

    /** Dot product. Mirrors `SkDVector::dot`. */
    fun dot(a: SkDVector): Double = x * a.x + y * a.y

    /** L2 length. Mirrors `SkDVector::length`. */
    fun length(): Double = sqrt(lengthSquared())

    /** L2 length squared (cheaper, no sqrt). Mirrors `SkDVector::lengthSquared`. */
    fun lengthSquared(): Double = x * x + y * y

    /**
     * Normalize in place. Mirrors `SkDVector::normalize`.
     * Uses IEEE divide so a zero-length vector becomes `(±Inf, ±Inf)` /
     * `(NaN, NaN)` rather than throwing.
     */
    fun normalize(): SkDVector {
        val invLen = 1.0 / length()
        x *= invLen
        y *= invLen
        return this
    }

    fun isFinite(): Boolean = x.isFinite() && y.isFinite()
}

/**
 * Double-precision 2D point. Mirrors
 * [`SkDPoint`](https://github.com/google/skia/blob/main/src/pathops/SkPathOpsPoint.h#L102).
 */
public data class SkDPoint(var x: Double, var y: Double) {

    constructor() : this(0.0, 0.0)

    fun set(pt: SkPoint) { x = pt.fX.toDouble(); y = pt.fY.toDouble() }

    /** Mirrors `SkDPoint::operator+=(const SkDVector&)`. */
    operator fun plusAssign(v: SkDVector) { x += v.x; y += v.y }

    /** Mirrors `SkDPoint::operator-=(const SkDVector&)`. */
    operator fun minusAssign(v: SkDVector) { x -= v.x; y -= v.y }

    /** Mirrors `SkDPoint::operator+(const SkDVector&)`. */
    operator fun plus(v: SkDVector): SkDPoint = SkDPoint(x + v.x, y + v.y)

    /** Mirrors `SkDPoint::operator-(const SkDVector&)`. */
    operator fun minus(v: SkDVector): SkDPoint = SkDPoint(x - v.x, y - v.y)

    /**
     * Magnitude-aware approximate equality. Mirrors
     * `SkDPoint::approximatelyDEqual`. Returns true if the two points
     * agree within ULPs scaled by the largest magnitude in play.
     */
    fun approximatelyDEqual(a: SkDPoint): Boolean {
        if (approximately_equal(x, a.x) && approximately_equal(y, a.y)) return true
        if (!RoughlyEqualUlps(x, a.x) || !RoughlyEqualUlps(y, a.y)) return false
        val dist = distance(a)
        val tiniest = min(min(min(x, a.x), y), a.y)
        var largest = max(max(max(x, a.x), y), a.y)
        largest = max(largest, -tiniest)
        return AlmostDequalUlps(largest, largest + dist)
    }

    fun approximatelyDEqual(a: SkPoint): Boolean = approximatelyDEqual(SkDPoint(a.fX.toDouble(), a.fY.toDouble()))

    /** Mirrors `SkDPoint::approximatelyEqual`. Same as DEqual but uses [AlmostPequalUlps]. */
    fun approximatelyEqual(a: SkDPoint): Boolean {
        if (approximately_equal(x, a.x) && approximately_equal(y, a.y)) return true
        if (!RoughlyEqualUlps(x, a.x) || !RoughlyEqualUlps(y, a.y)) return false
        val dist = distance(a)
        val tiniest = min(min(min(x, a.x), y), a.y)
        var largest = max(max(max(x, a.x), y), a.y)
        largest = max(largest, -tiniest)
        return AlmostPequalUlps(largest, largest + dist)
    }

    fun approximatelyEqual(a: SkPoint): Boolean = approximatelyEqual(SkDPoint(a.fX.toDouble(), a.fY.toDouble()))

    fun approximatelyZero(): Boolean = approximately_zero(x) && approximately_zero(y)

    /** Mirrors `SkDPoint::asSkPoint`. */
    fun asSkPoint(): SkPoint = SkPoint(fX = x.toFloat(), fY = y.toFloat())

    /** Mirrors `SkDPoint::distance`. */
    fun distance(a: SkDPoint): Double {
        val dx = x - a.x; val dy = y - a.y
        return sqrt(dx * dx + dy * dy)
    }

    /** Mirrors `SkDPoint::distanceSquared`. */
    fun distanceSquared(a: SkDPoint): Double {
        val dx = x - a.x; val dy = y - a.y
        return dx * dx + dy * dy
    }

    /** Magnitude-aware roughly-equal. Mirrors `SkDPoint::roughlyEqual`. */
    fun roughlyEqual(a: SkDPoint): Boolean {
        if (roughly_equal(x, a.x) && roughly_equal(y, a.y)) return true
        val dist = distance(a)
        val tiniest = min(min(min(x, a.x), y), a.y)
        var largest = max(max(max(x, a.x), y), a.y)
        largest = max(largest, -tiniest)
        return RoughlyEqualUlps(largest, largest + dist)
    }

    companion object {
        /** Mirrors `SkDPoint::Mid`. */
        fun Mid(a: SkDPoint, b: SkDPoint): SkDPoint =
            SkDPoint((a.x + b.x) / 2, (a.y + b.y) / 2)

        /**
         * Static magnitude-aware approximate equality on [SkPoint]s.
         * Mirrors `SkDPoint::ApproximatelyEqual`.
         */
        fun ApproximatelyEqual(a: SkPoint, b: SkPoint): Boolean {
            if (approximately_equal(a.fX.toDouble(), b.fX.toDouble())
                && approximately_equal(a.fY.toDouble(), b.fY.toDouble())) return true
            if (!RoughlyEqualUlps(a.fX, b.fX) || !RoughlyEqualUlps(a.fY, b.fY)) return false
            val dA = SkDPoint(a.fX.toDouble(), a.fY.toDouble())
            val dB = SkDPoint(b.fX.toDouble(), b.fY.toDouble())
            val dist = dA.distance(dB)
            val tiniest = min(min(min(a.fX, b.fX), a.fY), b.fY)
            var largest = max(max(max(a.fX, b.fX), a.fY), b.fY)
            largest = max(largest, -tiniest)
            return AlmostDequalUlps(largest.toDouble(), largest.toDouble() + dist)
        }

        /**
         * Same as [ApproximatelyEqual] but uses `RoughlyEqualUlps`
         * (looser tolerance) for the cheap path. Mirrors
         * `SkDPoint::RoughlyEqual` (`SkPathOpsPoint.h:252`).
         */
        fun RoughlyEqual(a: SkPoint, b: SkPoint): Boolean {
            if (!RoughlyEqualUlps(a.fX, b.fX) && !RoughlyEqualUlps(a.fY, b.fY)) return false
            val dA = SkDPoint(a.fX.toDouble(), a.fY.toDouble())
            val dB = SkDPoint(b.fX.toDouble(), b.fY.toDouble())
            val dist = dA.distance(dB)
            val tiniest = min(min(min(a.fX, b.fX), a.fY), b.fY)
            var largest = max(max(max(a.fX, b.fX), a.fY), b.fY)
            largest = max(largest, -tiniest)
            return AlmostDequalUlps(largest.toDouble(), largest.toDouble() + dist)
        }

        /** Light-weight inequality check. Mirrors `SkDPoint::WayRoughlyEqual`. */
        fun WayRoughlyEqual(a: SkPoint, b: SkPoint): Boolean {
            val largest = max(
                kotlin.math.abs(a.fX),
                max(kotlin.math.abs(a.fY), max(kotlin.math.abs(b.fX), kotlin.math.abs(b.fY))),
            )
            val dx = a.fX - b.fX; val dy = a.fY - b.fY
            val largestDiff = max(kotlin.math.abs(dx), kotlin.math.abs(dy))
            return roughly_zero_when_compared_to(largestDiff.toDouble(), largest.toDouble())
        }
    }

    /**
     * Vector difference of two points. Mirrors
     * `operator-(SkDPoint, SkDPoint)`. Moved from top-level extension
     * into SkDPoint at math-3 since cross-module imports of extension
     * operators are awkward — member operators are auto-resolved.
     */
    public operator fun minus(b: SkDPoint): SkDVector = SkDVector(x - b.x, y - b.y)
}
