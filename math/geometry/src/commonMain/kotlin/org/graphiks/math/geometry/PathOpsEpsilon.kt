package org.graphiks.math.geometry

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

// ─── Epsilon constants ────────────────────────────────────────────────

/** Single-precision epsilon = 2^-23 ≈ 1.19209290e-7 */
public const val FLT_EPSILON: Double = 1.1920928955078125e-7

/** Double-precision epsilon = 2^-52 ≈ 2.220446e-16 */
public const val DBL_EPSILON: Double = 2.220446049250313e-16

/** `FLT_EPSILON` cubed. */
public const val FLT_EPSILON_CUBED: Double = FLT_EPSILON * FLT_EPSILON * FLT_EPSILON

/** `FLT_EPSILON / 2`. */
public const val FLT_EPSILON_HALF: Double = FLT_EPSILON / 2

/** `FLT_EPSILON * 2`. */
public const val FLT_EPSILON_DOUBLE: Double = FLT_EPSILON * 2

/** `FLT_EPSILON * 16`. */
public const val FLT_EPSILON_ORDERABLE_ERR: Double = FLT_EPSILON * 16

/** `FLT_EPSILON` squared. */
public const val FLT_EPSILON_SQUARED: Double = FLT_EPSILON * FLT_EPSILON

/** Square root of `FLT_EPSILON`. */
public const val FLT_EPSILON_SQRT: Double = 0.00034526697709225118

/** `1 / FLT_EPSILON`. */
public const val FLT_EPSILON_INVERSE: Double = 1 / FLT_EPSILON

/** `DBL_EPSILON * 4`. */
public const val DBL_EPSILON_ERR: Double = DBL_EPSILON * 4

/** `DBL_EPSILON * 16`. */
public const val DBL_EPSILON_SUBDIVIDE_ERR: Double = DBL_EPSILON * 16

/** Rough epsilon threshold (`FLT_EPSILON * 64`). */
public const val ROUGH_EPSILON: Double = FLT_EPSILON * 64

/** More-rough epsilon threshold (`FLT_EPSILON * 256`). */
public const val MORE_ROUGH_EPSILON: Double = FLT_EPSILON * 256

/** Way-rough epsilon threshold (`FLT_EPSILON * 2048`). */
public const val WAY_ROUGH_EPSILON: Double = FLT_EPSILON * 2048

/** Bump epsilon threshold (`FLT_EPSILON * 4096`). */
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

private fun signBitTo2sComplement64(bits: Long): Long =
    if (bits < 0) -(bits and 0x7FFFFFFFFFFFFFFFL) else bits

private fun doubleAs2sComplement(x: Double): Long = signBitTo2sComplement64(x.toRawBits())

private fun argumentsDenormalizedDouble(a: Double, b: Double, epsilon: Int): Boolean {
    val denorm = DBL_EPSILON * epsilon / 2
    return abs(a) <= denorm && abs(b) <= denorm
}

private fun equalUlpsDouble(a: Double, b: Double, epsilon: Int, depsilon: Int): Boolean {
    if (argumentsDenormalizedDouble(a, b, depsilon)) return true
    val aBits = doubleAs2sComplement(a)
    val bBits = doubleAs2sComplement(b)
    return aBits < bBits + epsilon && bBits < aBits + epsilon
}

private fun equalUlpsNoNormalCheckDouble(a: Double, b: Double, epsilon: Int): Boolean {
    val aBits = doubleAs2sComplement(a)
    val bBits = doubleAs2sComplement(b)
    return aBits < bBits + epsilon && bBits < aBits + epsilon
}

private fun equalUlpsPinDouble(a: Double, b: Double, epsilon: Int, depsilon: Int): Boolean {
    if (!a.isFinite() || !b.isFinite()) return false
    if (argumentsDenormalizedDouble(a, b, depsilon)) return true
    val aBits = doubleAs2sComplement(a)
    val bBits = doubleAs2sComplement(b)
    return aBits < bBits + epsilon && bBits < aBits + epsilon
}

private fun dEqualUlpsDouble(a: Double, b: Double, epsilon: Int): Boolean {
    val aBits = doubleAs2sComplement(a)
    val bBits = doubleAs2sComplement(b)
    return aBits < bBits + epsilon && bBits < aBits + epsilon
}

private fun notEqualUlpsDouble(a: Double, b: Double, epsilon: Int): Boolean {
    if (argumentsDenormalizedDouble(a, b, epsilon)) return false
    val aBits = doubleAs2sComplement(a)
    val bBits = doubleAs2sComplement(b)
    return aBits >= bBits + epsilon || bBits >= aBits + epsilon
}

private fun dNotEqualUlpsDouble(a: Double, b: Double, epsilon: Int): Boolean {
    val aBits = doubleAs2sComplement(a)
    val bBits = doubleAs2sComplement(b)
    return aBits >= bBits + epsilon || bBits >= aBits + epsilon
}

private fun notEqualUlpsPinDouble(a: Double, b: Double, epsilon: Int): Boolean {
    if (!a.isFinite() || !b.isFinite()) return false
    if (argumentsDenormalizedDouble(a, b, epsilon)) return false
    val aBits = doubleAs2sComplement(a)
    val bBits = doubleAs2sComplement(b)
    return aBits >= bBits + epsilon || bBits >= aBits + epsilon
}

private fun lessUlpsDouble(a: Double, b: Double, epsilon: Int): Boolean {
    if (argumentsDenormalizedDouble(a, b, epsilon)) return a <= b - DBL_EPSILON * epsilon
    val aBits = doubleAs2sComplement(a)
    val bBits = doubleAs2sComplement(b)
    return aBits <= bBits - epsilon
}

private fun lessOrEqualUlpsDouble(a: Double, b: Double, epsilon: Int): Boolean {
    if (argumentsDenormalizedDouble(a, b, epsilon)) return a < b + DBL_EPSILON * epsilon
    val aBits = doubleAs2sComplement(a)
    val bBits = doubleAs2sComplement(b)
    return aBits < bBits + epsilon
}

// ─── Public ULPs predicates ───────────────────────────────────────────

/** Epsilon-aware predicates used by the path-operations geometry code. */
public object PathOpsEpsilon {

    /** Returns `true` if [a] and [b] are within 16 ULPs */
    public fun almostEqualUlps(a: Float, b: Float): Boolean = equalUlps(a, b, 16, 16)

    /** Double-precision overload for [almostEqualUlps]. */
    public fun almostEqualUlps(a: Double, b: Double): Boolean = equalUlpsDouble(a, b, 16, 16)

    /** Like [almostEqualUlps] but skips the denormalized check. */
    public fun almostEqualUlpsNoNormalCheck(a: Float, b: Float): Boolean = equalUlpsNoNormalCheck(a, b, 16)

    /** Double-precision overload for [almostEqualUlpsNoNormalCheck]. */
    public fun almostEqualUlpsNoNormalCheck(a: Double, b: Double): Boolean = equalUlpsNoNormalCheckDouble(a, b, 16)

    /** Like [almostEqualUlps] but pins non-finite values. */
    public fun almostEqualUlpsPin(a: Float, b: Float): Boolean = equalUlpsPin(a, b, 16, 16)

    /** Double-precision overload for [almostEqualUlpsPin]. */
    public fun almostEqualUlpsPin(a: Double, b: Double): Boolean = equalUlpsPinDouble(a, b, 16, 16)

    /** Returns `true` if [a] and [b] are within 2 ULPs. */
    public fun almostBEqualUlps(a: Float, b: Float): Boolean = equalUlps(a, b, 2, 2)

    /** Double-precision overload for [almostBEqualUlps]. */
    public fun almostBEqualUlps(a: Double, b: Double): Boolean = equalUlpsDouble(a, b, 2, 2)

    /** Returns `true` if [a] and [b] are within 8 ULPs. */
    public fun almostPEqualUlps(a: Float, b: Float): Boolean = equalUlps(a, b, 8, 8)

    /** Double-precision overload for [almostPEqualUlps]. */
    public fun almostPEqualUlps(a: Double, b: Double): Boolean = equalUlpsDouble(a, b, 8, 8)

    /** Double-precision 16-ULP equality. */
    public fun almostDEqualUlps(a: Float, b: Float): Boolean = dEqualUlps(a, b, 16)

    /** Double-precision overload with proper Double ULP comparison. */
    public fun almostDEqualUlps(a: Double, b: Double): Boolean = dEqualUlpsDouble(a, b, 16)

    /** Returns `true` if [a] and [b] are NOT within 16 ULPs. */
    public fun notAlmostEqualUlps(a: Float, b: Float): Boolean = notEqualUlps(a, b, 16)

    /** Double-precision overload for [notAlmostEqualUlps]. */
    public fun notAlmostEqualUlps(a: Double, b: Double): Boolean = notEqualUlpsDouble(a, b, 16)

    /** Returns `true` if [a] and [b] are NOT within 16 ULPs (pinned). */
    public fun notAlmostEqualUlpsPin(a: Float, b: Float): Boolean = notEqualUlpsPin(a, b, 16)

    /** Double-precision overload for [notAlmostEqualUlpsPin]. */
    public fun notAlmostEqualUlpsPin(a: Double, b: Double): Boolean = notEqualUlpsPinDouble(a, b, 16)

    /** Double-precision inequality variant of [almostDEqualUlps]. */
    public fun notAlmostDEqualUlps(a: Float, b: Float): Boolean = dNotEqualUlps(a, b, 16)

    /** Double-precision overload for [notAlmostDEqualUlps]. */
    public fun notAlmostDEqualUlps(a: Double, b: Double): Boolean = dNotEqualUlpsDouble(a, b, 16)

    /** Returns `true` if [a] and [b] are within 256 ULPs and 1024-denormalized ULPs. */
    public fun roughlyEqualUlps(a: Float, b: Float): Boolean = equalUlps(a, b, 256, 1024)

    /** Double-precision overload for [roughlyEqualUlps]. */
    public fun roughlyEqualUlps(a: Double, b: Double): Boolean = equalUlpsDouble(a, b, 256, 1024)

    /** Returns `true` if [b] is between [a] and [c] within 2 ULPs. */
    public fun almostBetweenUlps(a: Float, b: Float, c: Float): Boolean =
        if (a <= c) lessOrEqualUlps(a, b, 2) && lessOrEqualUlps(b, c, 2)
        else lessOrEqualUlps(b, a, 2) && lessOrEqualUlps(c, b, 2)

    /** Double-precision overload for [almostBetweenUlps]. */
    public fun almostBetweenUlps(a: Double, b: Double, c: Double): Boolean =
        if (a <= c) lessOrEqualUlpsDouble(a, b, 2) && lessOrEqualUlpsDouble(b, c, 2)
        else lessOrEqualUlpsDouble(b, a, 2) && lessOrEqualUlpsDouble(c, b, 2)

    /** Returns `true` if [a] < [b] within 16 ULPs. */
    public fun almostLessUlps(a: Float, b: Float): Boolean = lessUlps(a, b, 16)

    /** Double-precision overload for [almostLessUlps]. */
    public fun almostLessUlps(a: Double, b: Double): Boolean = lessUlpsDouble(a, b, 16)

    /** Returns `true` if [a] <= [b] within 16 ULPs. */
    public fun almostLessOrEqualUlps(a: Float, b: Float): Boolean = lessOrEqualUlps(a, b, 16)

    /** Double-precision overload for [almostLessOrEqualUlps]. */
    public fun almostLessOrEqualUlps(a: Double, b: Double): Boolean = lessOrEqualUlpsDouble(a, b, 16)

    /** Returns the ULP distance between [a] and [b] */
    public fun ulpsDistance(a: Float, b: Float): Int {
        val aBits = a.toRawBits()
        val bBits = b.toRawBits()
        if ((aBits < 0) != (bBits < 0)) {
            return if (a == b) 0 else Int.MAX_VALUE
        }
        return abs(aBits - bBits)
    }

    // ─── Approximate predicates (zero / equal / between, etc.) ───────────

    /** Returns `true` if [x] is 0 or 1. */
    public fun zeroOrOne(x: Double): Boolean = x == 0.0 || x == 1.0

    /** Returns `true` if [x] is within FLT_EPSILON of zero. */
    public fun approximatelyZero(x: Double): Boolean = abs(x) < FLT_EPSILON

    /** Single-precision overload for [approximatelyZero]. */
    public fun approximatelyZero(x: Float): Boolean = abs(x) < FLT_EPSILON

    /** Returns `true` if [x] is within DBL_EPSILON_ERR of zero. */
    public fun preciselyZero(x: Double): Boolean = abs(x) < DBL_EPSILON_ERR

    /** Returns `true` if [x] is within DBL_EPSILON_SUBDIVIDE_ERR of zero. */
    public fun preciselySubdivideZero(x: Double): Boolean = abs(x) < DBL_EPSILON_SUBDIVIDE_ERR

    /** Returns `true` if [x] is within FLT_EPSILON_HALF of zero. */
    public fun approximatelyZeroHalf(x: Double): Boolean = abs(x) < FLT_EPSILON_HALF

    /** Returns `true` if [x] is within FLT_EPSILON_DOUBLE of zero. */
    public fun approximatelyZeroDouble(x: Double): Boolean = abs(x) < FLT_EPSILON_DOUBLE

    /** Returns `true` if [x] is within FLT_EPSILON_ORDERABLE_ERR of zero. */
    public fun approximatelyZeroOrderable(x: Double): Boolean = abs(x) < FLT_EPSILON_ORDERABLE_ERR

    /** Returns `true` if [x] is within FLT_EPSILON_SQUARED of zero. */
    public fun approximatelyZeroSquared(x: Double): Boolean = abs(x) < FLT_EPSILON_SQUARED

    /** Returns `true` if [x] is within FLT_EPSILON_SQRT of zero. */
    public fun approximatelyZeroSqrt(x: Double): Boolean = abs(x) < FLT_EPSILON_SQRT

    /** Returns `true` if [x] is within ROUGH_EPSILON of zero. */
    public fun roughlyZero(x: Double): Boolean = abs(x) < ROUGH_EPSILON

    /** Returns `true` if `|x| > 1 / FLT_EPSILON`. */
    public fun approximatelyZeroInverse(x: Double): Boolean = abs(x) > FLT_EPSILON_INVERSE

    /** Returns `true` if `|x| < |y * FLT_EPSILON|`. */
    public fun approximatelyZeroWhenComparedTo(x: Double, y: Double): Boolean =
        x == 0.0 || abs(x) < abs(y * FLT_EPSILON)

    /** Returns `true` if `|x| < |y * DBL_EPSILON|`. */
    public fun preciselyZeroWhenComparedTo(x: Double, y: Double): Boolean =
        x == 0.0 || abs(x) < abs(y * DBL_EPSILON)

    /** Returns `true` if `|x| < |y * ROUGH_EPSILON|`. */
    public fun roughlyZeroWhenComparedTo(x: Double, y: Double): Boolean =
        x == 0.0 || abs(x) < abs(y * ROUGH_EPSILON)

    /** Returns `true` if `x` and `y` are approximately equal. For Ts in [0, 1]; for general magnitudes use [almostEqualUlps]. */
    public fun approximatelyEqual(x: Double, y: Double): Boolean = approximatelyZero(x - y)

    /** Returns `true` if `x` and `y` are precisely equal. */
    public fun preciselyEqual(x: Double, y: Double): Boolean = preciselyZero(x - y)

    /** Returns `true` if `x` and `y` are equal within subdivide precision. */
    public fun preciselySubdivideEqual(x: Double, y: Double): Boolean = preciselySubdivideZero(x - y)

    /** Returns `true` if `x` and `y` are equal within half-FLT_EPSILON. */
    public fun approximatelyEqualHalf(x: Double, y: Double): Boolean = approximatelyZeroHalf(x - y)

    /** Returns `true` if `x` and `y` are equal within double FLT_EPSILON. */
    public fun approximatelyEqualDouble(x: Double, y: Double): Boolean = approximatelyZeroDouble(x - y)

    /** Returns `true` if `x` and `y` are equal within orderable epsilon. */
    public fun approximatelyEqualOrderable(x: Double, y: Double): Boolean = approximatelyZeroOrderable(x - y)

    /** Returns `true` if `x` and `y` are approximately equal (squared epsilon). */
    public fun approximatelyEqualSquared(x: Double, y: Double): Boolean = approximatelyEqual(x, y)

    /** Returns `true` if `x >= y + FLT_EPSILON`. */
    public fun approximatelyGreater(x: Double, y: Double): Boolean = x - FLT_EPSILON >= y

    /** Returns `true` if `x >= y + FLT_EPSILON_DOUBLE`. */
    public fun approximatelyGreaterDouble(x: Double, y: Double): Boolean = x - FLT_EPSILON_DOUBLE >= y

    /** Returns `true` if `x >= y + FLT_EPSILON_ORDERABLE_ERR`. */
    public fun approximatelyGreaterOrderable(x: Double, y: Double): Boolean = x - FLT_EPSILON_ORDERABLE_ERR >= y

    /** Returns `true` if `x + FLT_EPSILON > y`. */
    public fun approximatelyGreaterOrEqual(x: Double, y: Double): Boolean = x + FLT_EPSILON > y

    /** Returns `true` if `x + FLT_EPSILON_DOUBLE > y`. */
    public fun approximatelyGreaterOrEqualDouble(x: Double, y: Double): Boolean = x + FLT_EPSILON_DOUBLE > y

    /** Returns `true` if `x + FLT_EPSILON_ORDERABLE_ERR > y`. */
    public fun approximatelyGreaterOrEqualOrderable(x: Double, y: Double): Boolean = x + FLT_EPSILON_ORDERABLE_ERR > y

    /** Returns `true` if `x + FLT_EPSILON <= y`. */
    public fun approximatelyLesser(x: Double, y: Double): Boolean = x + FLT_EPSILON <= y

    /** Returns `true` if `x + FLT_EPSILON_DOUBLE <= y`. */
    public fun approximatelyLesserDouble(x: Double, y: Double): Boolean = x + FLT_EPSILON_DOUBLE <= y

    /** Returns `true` if `x + FLT_EPSILON_ORDERABLE_ERR <= y`. */
    public fun approximatelyLesserOrderable(x: Double, y: Double): Boolean = x + FLT_EPSILON_ORDERABLE_ERR <= y

    /** Returns `true` if `x - FLT_EPSILON < y`. */
    public fun approximatelyLesserOrEqual(x: Double, y: Double): Boolean = x - FLT_EPSILON < y

    /** Returns `true` if `x - FLT_EPSILON_DOUBLE < y`. */
    public fun approximatelyLesserOrEqualDouble(x: Double, y: Double): Boolean = x - FLT_EPSILON_DOUBLE < y

    /** Returns `true` if `x - FLT_EPSILON_ORDERABLE_ERR < y`. */
    public fun approximatelyLesserOrEqualOrderable(x: Double, y: Double): Boolean = x - FLT_EPSILON_ORDERABLE_ERR < y

    /** Returns `true` if `x > 1 - FLT_EPSILON`. */
    public fun approximatelyGreaterThanOne(x: Double): Boolean = x > 1 - FLT_EPSILON

    /** Returns `true` if `x > 1 - DBL_EPSILON_ERR`. */
    public fun preciselyGreaterThanOne(x: Double): Boolean = x > 1 - DBL_EPSILON_ERR

    /** Returns `true` if `x < FLT_EPSILON`. */
    public fun approximatelyLessThanZero(x: Double): Boolean = x < FLT_EPSILON

    /** Returns `true` if `x < DBL_EPSILON_ERR`. */
    public fun preciselyLessThanZero(x: Double): Boolean = x < DBL_EPSILON_ERR

    /** Returns `true` if `x < FLT_EPSILON`. */
    public fun approximatelyNegative(x: Double): Boolean = x < FLT_EPSILON

    /** Returns `true` if `x < FLT_EPSILON_ORDERABLE_ERR`. */
    public fun approximatelyNegativeOrderable(x: Double): Boolean = x < FLT_EPSILON_ORDERABLE_ERR

    /** Returns `true` if `x < DBL_EPSILON_ERR`. */
    public fun preciselyNegative(x: Double): Boolean = x < DBL_EPSILON_ERR

    /** Returns `true` if `x < 1 + FLT_EPSILON`. */
    public fun approximatelyOneOrLess(x: Double): Boolean = x < 1 + FLT_EPSILON

    /** Returns `true` if `x < 1 + FLT_EPSILON_DOUBLE`. */
    public fun approximatelyOneOrLessDouble(x: Double): Boolean = x < 1 + FLT_EPSILON_DOUBLE

    /** Returns `true` if `x > -FLT_EPSILON`. */
    public fun approximatelyPositive(x: Double): Boolean = x > -FLT_EPSILON

    /** Returns `true` if `x > -(FLT_EPSILON_SQUARED)`. */
    public fun approximatelyPositiveSquared(x: Double): Boolean = x > -(FLT_EPSILON_SQUARED)

    /** Returns `true` if `x > -FLT_EPSILON_DOUBLE`. */
    public fun approximatelyPositiveDouble(x: Double): Boolean = x > -FLT_EPSILON_DOUBLE

    /** Returns `true` if `x > -FLT_EPSILON`. */
    public fun approximatelyZeroOrMore(x: Double): Boolean = x > -FLT_EPSILON

    /** Returns `true` if `x > -FLT_EPSILON_DOUBLE`. */
    public fun approximatelyZeroOrMoreDouble(x: Double): Boolean = x > -FLT_EPSILON_DOUBLE

    /** Returns `true` if [b] is approximately between [a] and [c] (orderable). */
    public fun approximatelyBetweenOrderable(a: Double, b: Double, c: Double): Boolean =
        if (a <= c) approximatelyNegativeOrderable(a - b) && approximatelyNegativeOrderable(b - c)
        else approximatelyNegativeOrderable(b - a) && approximatelyNegativeOrderable(c - b)

    /** Returns `true` if [b] is approximately between [a] and [c]. */
    public fun approximatelyBetween(a: Double, b: Double, c: Double): Boolean =
        if (a <= c) approximatelyNegative(a - b) && approximatelyNegative(b - c)
        else approximatelyNegative(b - a) && approximatelyNegative(c - b)

    /** Returns `true` if [b] is precisely between [a] and [c]. */
    public fun preciselyBetween(a: Double, b: Double, c: Double): Boolean =
        if (a <= c) preciselyNegative(a - b) && preciselyNegative(b - c)
        else preciselyNegative(b - a) && preciselyNegative(c - b)

    /** Returns `true` if `(a - b) * (c - b) <= 0`. */
    public fun between(a: Double, b: Double, c: Double): Boolean = (a - b) * (c - b) <= 0

    /** Returns `true` if `|x - y| < ROUGH_EPSILON`. */
    public fun roughlyEqual(x: Double, y: Double): Boolean = abs(x - y) < ROUGH_EPSILON

    /** Returns `true` if `x < ROUGH_EPSILON`. */
    public fun roughlyNegative(x: Double): Boolean = x < ROUGH_EPSILON

    /** Returns `true` if [b] is roughly between [a] and [c]. */
    public fun roughlyBetween(a: Double, b: Double, c: Double): Boolean =
        if (a <= c) roughlyNegative(a - b) && roughlyNegative(b - c)
        else roughlyNegative(b - a) && roughlyNegative(c - b)

    /** Returns `true` if `|x - y| < MORE_ROUGH_EPSILON`. */
    public fun moreRoughlyEqual(x: Double, y: Double): Boolean = abs(x - y) < MORE_ROUGH_EPSILON

    // ─── T value & sign helpers ───────────────────────────────────────────

    /** Linear interpolation */
    public fun interpolate(a: Double, b: Double, t: Double): Double = a + (b - a) * t

    /** Returns -1 / 0 / 1 for negative / zero / positive */
    public fun sign(x: Double): Int = (if (x > 0) 1 else 0) - (if (x < 0) 1 else 0)

    /** Returns 0 / 1 / 2 for negative / zero / positive */
    public fun sideIndex(x: Double): Int = (if (x > 0) 1 else 0) + (if (x >= 0) 1 else 0)

    /** Returns 1 / 2 / 4 for negative / zero / positive */
    public fun sideMask(x: Double): Int = 1 shl sideIndex(x)

    /** Pins a t-value into `[0, 1]` using the precise epsilon thresholds. */
    public fun pinT(t: Double): Double = when {
        preciselyLessThanZero(t) -> 0.0
        preciselyGreaterThanOne(t) -> 1.0
        else -> t
    }
}
