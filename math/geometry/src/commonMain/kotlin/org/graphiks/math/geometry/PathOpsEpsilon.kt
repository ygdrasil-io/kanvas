package org.graphiks.math.geometry

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

// ─── Epsilon constants ────────────────────────────────────────────────

/** Single-precision epsilon = 2^-23 ≈ 1.19209290e-7. Mirrors `FLT_EPSILON`. */
public const val FLT_EPSILON: Double = 1.1920928955078125e-7

/** Double-precision epsilon = 2^-52 ≈ 2.220446e-16. Mirrors `DBL_EPSILON`. */
public const val DBL_EPSILON: Double = 2.220446049250313e-16

public const val FLT_EPSILON_CUBED: Double = FLT_EPSILON * FLT_EPSILON * FLT_EPSILON
public const val FLT_EPSILON_HALF: Double = FLT_EPSILON / 2
public const val FLT_EPSILON_DOUBLE: Double = FLT_EPSILON * 2
public const val FLT_EPSILON_ORDERABLE_ERR: Double = FLT_EPSILON * 16
public const val FLT_EPSILON_SQUARED: Double = FLT_EPSILON * FLT_EPSILON

public const val FLT_EPSILON_SQRT: Double = 0.00034526697709225118

public const val FLT_EPSILON_INVERSE: Double = 1 / FLT_EPSILON
public const val DBL_EPSILON_ERR: Double = DBL_EPSILON * 4
public const val DBL_EPSILON_SUBDIVIDE_ERR: Double = DBL_EPSILON * 16
public const val ROUGH_EPSILON: Double = FLT_EPSILON * 64
public const val MORE_ROUGH_EPSILON: Double = FLT_EPSILON * 256
public const val WAY_ROUGH_EPSILON: Double = FLT_EPSILON * 2048
public const val BUMP_EPSILON: Double = FLT_EPSILON * 4096

// ─── ULPs comparisons ─────────────────────────────────────────────────

private fun signBitTo2sComplement(bits: Int): Int =
    if (bits < 0) -(bits and 0x7FFFFFFF) else bits

private fun floatAs2sComplement(x: Float): Int = signBitTo2sComplement(x.toRawBits())

private fun argumentsDenormalized(a: Float, b: Float, epsilon: Int): Boolean {
    val denorm = (FLT_EPSILON * epsilon / 2).toFloat()
    return abs(a) <= denorm && abs(b) <= denorm
}

private fun equalUlps(a: Float, b: Float, epsilon: Int, depsilon: Int): Boolean {
    if (argumentsDenormalized(a, b, depsilon)) return true
    val aBits = floatAs2sComplement(a)
    val bBits = floatAs2sComplement(b)
    return aBits < bBits + epsilon && bBits < aBits + epsilon
}

private fun equalUlpsNoNormalCheck(a: Float, b: Float, epsilon: Int): Boolean {
    val aBits = floatAs2sComplement(a)
    val bBits = floatAs2sComplement(b)
    return aBits < bBits + epsilon && bBits < aBits + epsilon
}

private fun equalUlpsPin(a: Float, b: Float, epsilon: Int, depsilon: Int): Boolean {
    if (!a.isFinite() || !b.isFinite()) return false
    if (argumentsDenormalized(a, b, depsilon)) return true
    val aBits = floatAs2sComplement(a)
    val bBits = floatAs2sComplement(b)
    return aBits < bBits + epsilon && bBits < aBits + epsilon
}

private fun dEqualUlps(a: Float, b: Float, epsilon: Int): Boolean {
    val aBits = floatAs2sComplement(a)
    val bBits = floatAs2sComplement(b)
    return aBits < bBits + epsilon && bBits < aBits + epsilon
}

private fun notEqualUlps(a: Float, b: Float, epsilon: Int): Boolean {
    if (argumentsDenormalized(a, b, epsilon)) return false
    val aBits = floatAs2sComplement(a)
    val bBits = floatAs2sComplement(b)
    return aBits >= bBits + epsilon || bBits >= aBits + epsilon
}

private fun dNotEqualUlps(a: Float, b: Float, epsilon: Int): Boolean {
    val aBits = floatAs2sComplement(a)
    val bBits = floatAs2sComplement(b)
    return aBits >= bBits + epsilon || bBits >= aBits + epsilon
}

private fun notEqualUlpsPin(a: Float, b: Float, epsilon: Int): Boolean {
    if (!a.isFinite() || !b.isFinite()) return false
    if (argumentsDenormalized(a, b, epsilon)) return false
    val aBits = floatAs2sComplement(a)
    val bBits = floatAs2sComplement(b)
    return aBits >= bBits + epsilon || bBits >= aBits + epsilon
}

private fun lessUlps(a: Float, b: Float, epsilon: Int): Boolean {
    if (argumentsDenormalized(a, b, epsilon)) return a <= b - FLT_EPSILON.toFloat() * epsilon
    val aBits = floatAs2sComplement(a)
    val bBits = floatAs2sComplement(b)
    return aBits <= bBits - epsilon
}

private fun lessOrEqualUlps(a: Float, b: Float, epsilon: Int): Boolean {
    if (argumentsDenormalized(a, b, epsilon)) return a < b + FLT_EPSILON.toFloat() * epsilon
    val aBits = floatAs2sComplement(a)
    val bBits = floatAs2sComplement(b)
    return aBits < bBits + epsilon
}

// ─── Public ULPs predicates ───────────────────────────────────────────

public fun AlmostEqualUlps(a: Float, b: Float): Boolean = equalUlps(a, b, 16, 16)
public fun AlmostEqualUlps(a: Double, b: Double): Boolean = AlmostEqualUlps(a.toFloat(), b.toFloat())

public fun AlmostEqualUlpsNoNormalCheck(a: Float, b: Float): Boolean = equalUlpsNoNormalCheck(a, b, 16)
public fun AlmostEqualUlpsNoNormalCheck(a: Double, b: Double): Boolean = AlmostEqualUlpsNoNormalCheck(a.toFloat(), b.toFloat())

public fun AlmostEqualUlpsPin(a: Float, b: Float): Boolean = equalUlpsPin(a, b, 16, 16)
public fun AlmostEqualUlpsPin(a: Double, b: Double): Boolean = AlmostEqualUlpsPin(a.toFloat(), b.toFloat())

public fun AlmostBequalUlps(a: Float, b: Float): Boolean = equalUlps(a, b, 2, 2)
public fun AlmostBequalUlps(a: Double, b: Double): Boolean = AlmostBequalUlps(a.toFloat(), b.toFloat())

public fun AlmostPequalUlps(a: Float, b: Float): Boolean = equalUlps(a, b, 8, 8)
public fun AlmostPequalUlps(a: Double, b: Double): Boolean = AlmostPequalUlps(a.toFloat(), b.toFloat())

public fun AlmostDequalUlps(a: Float, b: Float): Boolean = dEqualUlps(a, b, 16)
public fun AlmostDequalUlps(a: Double, b: Double): Boolean {
    val SK_ScalarMax = Float.MAX_VALUE.toDouble()
    if (abs(a) < SK_ScalarMax && abs(b) < SK_ScalarMax) {
        return AlmostDequalUlps(a.toFloat(), b.toFloat())
    }
    val denom = max(abs(a), abs(b))
    if (denom == 0.0) return false
    return abs(a - b) / denom < FLT_EPSILON * 16
}

public fun NotAlmostEqualUlps(a: Float, b: Float): Boolean = notEqualUlps(a, b, 16)
public fun NotAlmostEqualUlps(a: Double, b: Double): Boolean = NotAlmostEqualUlps(a.toFloat(), b.toFloat())

public fun NotAlmostEqualUlpsPin(a: Float, b: Float): Boolean = notEqualUlpsPin(a, b, 16)
public fun NotAlmostEqualUlpsPin(a: Double, b: Double): Boolean = NotAlmostEqualUlpsPin(a.toFloat(), b.toFloat())

public fun NotAlmostDequalUlps(a: Float, b: Float): Boolean = dNotEqualUlps(a, b, 16)
public fun NotAlmostDequalUlps(a: Double, b: Double): Boolean = NotAlmostDequalUlps(a.toFloat(), b.toFloat())

public fun RoughlyEqualUlps(a: Float, b: Float): Boolean = equalUlps(a, b, 256, 1024)
public fun RoughlyEqualUlps(a: Double, b: Double): Boolean = RoughlyEqualUlps(a.toFloat(), b.toFloat())

public fun AlmostBetweenUlps(a: Float, b: Float, c: Float): Boolean =
    if (a <= c) lessOrEqualUlps(a, b, 2) && lessOrEqualUlps(b, c, 2)
    else lessOrEqualUlps(b, a, 2) && lessOrEqualUlps(c, b, 2)

public fun AlmostBetweenUlps(a: Double, b: Double, c: Double): Boolean =
    AlmostBetweenUlps(a.toFloat(), b.toFloat(), c.toFloat())

public fun AlmostLessUlps(a: Float, b: Float): Boolean = lessUlps(a, b, 16)
public fun AlmostLessUlps(a: Double, b: Double): Boolean = AlmostLessUlps(a.toFloat(), b.toFloat())

public fun AlmostLessOrEqualUlps(a: Float, b: Float): Boolean = lessOrEqualUlps(a, b, 16)
public fun AlmostLessOrEqualUlps(a: Double, b: Double): Boolean = AlmostLessOrEqualUlps(a.toFloat(), b.toFloat())

public fun UlpsDistance(a: Float, b: Float): Int {
    val aBits = a.toRawBits()
    val bBits = b.toRawBits()
    if ((aBits < 0) != (bBits < 0)) {
        return if (a == b) 0 else Int.MAX_VALUE
    }
    return abs(aBits - bBits)
}

// ─── Approximate predicates (zero / equal / between, etc.) ───────────

public fun zero_or_one(x: Double): Boolean = x == 0.0 || x == 1.0

public fun approximately_zero(x: Double): Boolean = abs(x) < FLT_EPSILON
public fun approximately_zero(x: Float): Boolean = abs(x) < FLT_EPSILON
public fun precisely_zero(x: Double): Boolean = abs(x) < DBL_EPSILON_ERR
public fun precisely_subdivide_zero(x: Double): Boolean = abs(x) < DBL_EPSILON_SUBDIVIDE_ERR
public fun approximately_zero_half(x: Double): Boolean = abs(x) < FLT_EPSILON_HALF
public fun approximately_zero_double(x: Double): Boolean = abs(x) < FLT_EPSILON_DOUBLE
public fun approximately_zero_orderable(x: Double): Boolean = abs(x) < FLT_EPSILON_ORDERABLE_ERR
public fun approximately_zero_squared(x: Double): Boolean = abs(x) < FLT_EPSILON_SQUARED
public fun approximately_zero_sqrt(x: Double): Boolean = abs(x) < FLT_EPSILON_SQRT
public fun roughly_zero(x: Double): Boolean = abs(x) < ROUGH_EPSILON
public fun approximately_zero_inverse(x: Double): Boolean = abs(x) > FLT_EPSILON_INVERSE

public fun approximately_zero_when_compared_to(x: Double, y: Double): Boolean =
    x == 0.0 || abs(x) < abs(y * FLT_EPSILON)

public fun precisely_zero_when_compared_to(x: Double, y: Double): Boolean =
    x == 0.0 || abs(x) < abs(y * DBL_EPSILON)

public fun roughly_zero_when_compared_to(x: Double, y: Double): Boolean =
    x == 0.0 || abs(x) < abs(y * ROUGH_EPSILON)

/** For Ts in [0, 1]. For general magnitudes use [AlmostEqualUlps]. */
public fun approximately_equal(x: Double, y: Double): Boolean = approximately_zero(x - y)
public fun precisely_equal(x: Double, y: Double): Boolean = precisely_zero(x - y)
public fun precisely_subdivide_equal(x: Double, y: Double): Boolean = precisely_subdivide_zero(x - y)
public fun approximately_equal_half(x: Double, y: Double): Boolean = approximately_zero_half(x - y)
public fun approximately_equal_double(x: Double, y: Double): Boolean = approximately_zero_double(x - y)
public fun approximately_equal_orderable(x: Double, y: Double): Boolean = approximately_zero_orderable(x - y)
public fun approximately_equal_squared(x: Double, y: Double): Boolean = approximately_equal(x, y)

public fun approximately_greater(x: Double, y: Double): Boolean = x - FLT_EPSILON >= y
public fun approximately_greater_double(x: Double, y: Double): Boolean = x - FLT_EPSILON_DOUBLE >= y
public fun approximately_greater_orderable(x: Double, y: Double): Boolean = x - FLT_EPSILON_ORDERABLE_ERR >= y
public fun approximately_greater_or_equal(x: Double, y: Double): Boolean = x + FLT_EPSILON > y
public fun approximately_greater_or_equal_double(x: Double, y: Double): Boolean = x + FLT_EPSILON_DOUBLE > y
public fun approximately_greater_or_equal_orderable(x: Double, y: Double): Boolean = x + FLT_EPSILON_ORDERABLE_ERR > y
public fun approximately_lesser(x: Double, y: Double): Boolean = x + FLT_EPSILON <= y
public fun approximately_lesser_double(x: Double, y: Double): Boolean = x + FLT_EPSILON_DOUBLE <= y
public fun approximately_lesser_orderable(x: Double, y: Double): Boolean = x + FLT_EPSILON_ORDERABLE_ERR <= y
public fun approximately_lesser_or_equal(x: Double, y: Double): Boolean = x - FLT_EPSILON < y
public fun approximately_lesser_or_equal_double(x: Double, y: Double): Boolean = x - FLT_EPSILON_DOUBLE < y
public fun approximately_lesser_or_equal_orderable(x: Double, y: Double): Boolean = x - FLT_EPSILON_ORDERABLE_ERR < y

public fun approximately_greater_than_one(x: Double): Boolean = x > 1 - FLT_EPSILON
public fun precisely_greater_than_one(x: Double): Boolean = x > 1 - DBL_EPSILON_ERR
public fun approximately_less_than_zero(x: Double): Boolean = x < FLT_EPSILON
public fun precisely_less_than_zero(x: Double): Boolean = x < DBL_EPSILON_ERR
public fun approximately_negative(x: Double): Boolean = x < FLT_EPSILON
public fun approximately_negative_orderable(x: Double): Boolean = x < FLT_EPSILON_ORDERABLE_ERR
public fun precisely_negative(x: Double): Boolean = x < DBL_EPSILON_ERR
public fun approximately_one_or_less(x: Double): Boolean = x < 1 + FLT_EPSILON
public fun approximately_one_or_less_double(x: Double): Boolean = x < 1 + FLT_EPSILON_DOUBLE
public fun approximately_positive(x: Double): Boolean = x > -FLT_EPSILON
public fun approximately_positive_squared(x: Double): Boolean = x > -(FLT_EPSILON_SQUARED)
public fun approximately_positive_double(x: Double): Boolean = x > -FLT_EPSILON_DOUBLE
public fun approximately_zero_or_more(x: Double): Boolean = x > -FLT_EPSILON
public fun approximately_zero_or_more_double(x: Double): Boolean = x > -FLT_EPSILON_DOUBLE

public fun approximately_between_orderable(a: Double, b: Double, c: Double): Boolean =
    if (a <= c) approximately_negative_orderable(a - b) && approximately_negative_orderable(b - c)
    else approximately_negative_orderable(b - a) && approximately_negative_orderable(c - b)

public fun approximately_between(a: Double, b: Double, c: Double): Boolean =
    if (a <= c) approximately_negative(a - b) && approximately_negative(b - c)
    else approximately_negative(b - a) && approximately_negative(c - b)

public fun precisely_between(a: Double, b: Double, c: Double): Boolean =
    if (a <= c) precisely_negative(a - b) && precisely_negative(b - c)
    else precisely_negative(b - a) && precisely_negative(c - b)

public fun between(a: Double, b: Double, c: Double): Boolean = (a - b) * (c - b) <= 0

public fun roughly_equal(x: Double, y: Double): Boolean = abs(x - y) < ROUGH_EPSILON
public fun roughly_negative(x: Double): Boolean = x < ROUGH_EPSILON
public fun roughly_between(a: Double, b: Double, c: Double): Boolean =
    if (a <= c) roughly_negative(a - b) && roughly_negative(b - c)
    else roughly_negative(b - a) && roughly_negative(c - b)

public fun more_roughly_equal(x: Double, y: Double): Boolean = abs(x - y) < MORE_ROUGH_EPSILON

// ─── T value & sign helpers ───────────────────────────────────────────

/** Linear interpolation. Mirrors `SkDInterp`. */
public fun SkDInterp(A: Double, B: Double, t: Double): Double = A + (B - A) * t

/** Returns -1 / 0 / 1 for negative / zero / positive. Mirrors `SkDSign`. */
public fun SkDSign(x: Double): Int = (if (x > 0) 1 else 0) - (if (x < 0) 1 else 0)

/** Returns 0 / 1 / 2 for negative / zero / positive. Mirrors `SKDSide`. */
public fun SKDSide(x: Double): Int = (if (x > 0) 1 else 0) + (if (x >= 0) 1 else 0)

/** Returns 1 / 2 / 4 for negative / zero / positive. Mirrors `SkDSideBit`. */
public fun SkDSideBit(x: Double): Int = 1 shl SKDSide(x)

/** Pin a t-value into [0, 1] using `precisely_*` thresholds. Mirrors `SkPinT`. */
public fun SkPinT(t: Double): Double = when {
    precisely_less_than_zero(t) -> 0.0
    precisely_greater_than_one(t) -> 1.0
    else -> t
}
