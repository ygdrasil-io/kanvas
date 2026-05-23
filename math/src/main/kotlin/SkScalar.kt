package org.graphiks.math

import kotlin.math.pow
import kotlin.math.withSign

/**
 * Iso-aligned port of Skia's [`include/core/SkScalar.h`](https://github.com/google/skia/blob/main/include/core/SkScalar.h).
 *
 * `SkScalar` is a typealias for `Float`. Constants and helpers mirror the
 * upstream macro/inline names verbatim so hand-translated C++ code reads
 * 1:1 against the original.
 *
 * Everything here is a thin wrapper over [kotlin.math] (which itself
 * delegates to JVM `java.lang.Math`, an IEEE-754 single-precision
 * implementation bit-equivalent to upstream's `sk_float_*` to ≤ 1 ulp).
 */

public typealias SkScalar = Float

// ─── Constants ──────────────────────────────────────────────────────────────

private const val SK_MaxS32FitsInFloat: Int = 2147483520
private const val SK_MinS32FitsInFloat: Int = -SK_MaxS32FitsInFloat

public const val SK_Scalar1: SkScalar = 1.0f
public const val SK_ScalarHalf: SkScalar = 0.5f
public const val SK_ScalarSqrt2: SkScalar = 1.41421356f
public const val SK_ScalarPI: SkScalar = 3.14159265358979323846f
public const val SK_Scalar2PI: SkScalar = 6.28318530717958647692f
public const val SK_ScalarPIOver2: SkScalar = 1.57079632679489661923f
public const val SK_ScalarTanPIOver8: SkScalar = 0.414213562f
public const val SK_ScalarRoot2Over2: SkScalar = 0.707106781f
public const val SK_ScalarMax: SkScalar = 3.402823466e+38f
public const val SK_ScalarMin: SkScalar = -SK_ScalarMax
public const val SK_ScalarInfinity: SkScalar = Float.POSITIVE_INFINITY
public const val SK_ScalarNegativeInfinity: SkScalar = Float.NEGATIVE_INFINITY
public val SK_ScalarNaN: SkScalar = Float.NaN

/** `1f / 4096` ≈ 2.44e-4. Default tolerance for [SkScalarNearlyZero]. */
public const val SK_ScalarNearlyZero: SkScalar = 1.0f / (1 shl 12)

/** `1f / 65536` ≈ 1.526e-5. Tolerance for [SkScalarSinSnapToZero] / [SkScalarCosSnapToZero]. */
public const val SK_ScalarSinCosNearlyZero: SkScalar = 1.0f / (1 shl 16)

// ─── Trig ───────────────────────────────────────────────────────────────────

public fun SkScalarSin(radians: SkScalar): SkScalar =
    kotlin.math.sin(radians.toDouble()).toFloat()

public fun SkScalarCos(radians: SkScalar): SkScalar =
    kotlin.math.cos(radians.toDouble()).toFloat()

public fun SkScalarTan(radians: SkScalar): SkScalar =
    kotlin.math.tan(radians.toDouble()).toFloat()

public fun SkScalarASin(value: SkScalar): SkScalar =
    kotlin.math.asin(value.toDouble()).toFloat()

public fun SkScalarACos(value: SkScalar): SkScalar =
    kotlin.math.acos(value.toDouble()).toFloat()

public fun SkScalarATan2(y: SkScalar, x: SkScalar): SkScalar =
    kotlin.math.atan2(y.toDouble(), x.toDouble()).toFloat()

// ─── Arithmetic / transcendentals ───────────────────────────────────────────

public fun SkScalarAbs(value: SkScalar): SkScalar = kotlin.math.abs(value)

public fun SkScalarSqrt(value: SkScalar): SkScalar =
    kotlin.math.sqrt(value.toDouble()).toFloat()

public fun SkScalarPow(base: SkScalar, exp: SkScalar): SkScalar =
    base.toDouble().pow(exp.toDouble()).toFloat()

public fun SkScalarExp(value: SkScalar): SkScalar =
    kotlin.math.exp(value.toDouble()).toFloat()

public fun SkScalarLog(value: SkScalar): SkScalar =
    kotlin.math.ln(value.toDouble()).toFloat()

public fun SkScalarLog2(value: SkScalar): SkScalar =
    kotlin.math.log2(value.toDouble()).toFloat()

public fun SkScalarMod(x: SkScalar, y: SkScalar): SkScalar =
    (x.toDouble() % y.toDouble()).toFloat()

public fun SkScalarCopySign(value: SkScalar, sign: SkScalar): SkScalar =
    value.withSign(sign)

// ─── Rounding ───────────────────────────────────────────────────────────────

public fun SkScalarFloor(value: SkScalar): SkScalar =
    kotlin.math.floor(value.toDouble()).toFloat()

public fun SkScalarCeil(value: SkScalar): SkScalar =
    kotlin.math.ceil(value.toDouble()).toFloat()

/**
 * Round half toward +∞ (`floor(x + 0.5)`) — matches Skia's
 * `sk_float_round` / `SkScalarRoundToScalar` (`SkFloatingPoint.h:38`).
 *
 * Notably **not** `kotlin.math.round`, which is half-to-even (banker's
 * rounding): `round(0.5) == 0`, `round(2.5) == 2`. Upstream returns
 * `1` and `3` respectively. Ties at `±0.5`, `±2.5`, `±4.5`, … are the
 * observable divergence; non-tie inputs round identically.
 */
public fun SkScalarRound(value: SkScalar): SkScalar =
    kotlin.math.floor(value.toDouble() + 0.5).toFloat()

public fun SkScalarTruncToScalar(value: SkScalar): SkScalar =
    kotlin.math.truncate(value.toDouble()).toFloat()

private fun skFloatSaturate2Int(value: Float): Int {
    var x = if (value < SK_MaxS32FitsInFloat) value else SK_MaxS32FitsInFloat.toFloat()
    x = if (x > SK_MinS32FitsInFloat) x else SK_MinS32FitsInFloat.toFloat()
    return x.toInt()
}

public fun SkScalarFloorToInt(value: SkScalar): Int =
    skFloatSaturate2Int(kotlin.math.floor(value.toDouble()).toFloat())

public fun SkScalarCeilToInt(value: SkScalar): Int =
    skFloatSaturate2Int(kotlin.math.ceil(value.toDouble()).toFloat())

/**
 * Round half toward +∞ (`floor(x + 0.5)`) then truncate to int — matches
 * Skia's `sk_float_round2int` (`SkFloatingPoint.h:119`). See [SkScalarRound]
 * for the tie-breaking rationale.
 */
public fun SkScalarRoundToInt(value: SkScalar): Int = skFloatSaturate2Int(SkScalarRound(value))
public fun SkScalarTruncToInt(value: SkScalar): Int = skFloatSaturate2Int(value)

// ─── Casts ──────────────────────────────────────────────────────────────────

public fun SkIntToScalar(value: Int): SkScalar = value.toFloat()
public fun SkIntToFloat(value: Int): Float = value.toFloat()
public fun SkScalarToInt(value: SkScalar): Int = SkScalarTruncToInt(value)
public fun SkScalarToFloat(value: SkScalar): Float = value
public fun SkFloatToScalar(value: Float): SkScalar = value
public fun SkScalarToDouble(value: SkScalar): Double = value.toDouble()
public fun SkDoubleToScalar(value: Double): SkScalar = value.toFloat()

// ─── Convenience helpers ────────────────────────────────────────────────────

/** Fractional part: `x - trunc(x)`. */
public fun SkScalarFraction(x: SkScalar): SkScalar = x - SkScalarTruncToScalar(x)

public fun SkScalarSquare(x: SkScalar): SkScalar = x * x

public fun SkScalarInvert(x: SkScalar): SkScalar = SK_Scalar1 / x

public fun SkScalarHalf(a: SkScalar): SkScalar = a * SK_ScalarHalf

/** Midpoint, mirrors Skia's `sk_float_midpoint(a, b)`. */
public fun SkScalarAve(a: SkScalar, b: SkScalar): SkScalar = (0.5 * (a.toDouble() + b)).toFloat()

public fun SkDegreesToRadians(degrees: SkScalar): SkScalar =
    degrees * (SK_ScalarPI / 180.0f)

public fun SkRadiansToDegrees(radians: SkScalar): SkScalar =
    radians * (180.0f / SK_ScalarPI)

public fun SkScalarIsInt(x: SkScalar): Boolean = x == SkScalarFloor(x)

/** Returns `-1`, `0`, or `1` matching the sign of [x]. */
public fun SkScalarSignAsInt(x: SkScalar): Int = if (x < 0f) -1 else if (x > 0f) 1 else 0

/** Same as [SkScalarSignAsInt] but returns a [SkScalar]. */
public fun SkScalarSignAsScalar(x: SkScalar): SkScalar =
    if (x < 0f) -SK_Scalar1 else if (x > 0f) SK_Scalar1 else 0f

// ─── Tolerance predicates ───────────────────────────────────────────────────

/** `|x| <= tolerance`. Default tolerance: [SK_ScalarNearlyZero]. */
public fun SkScalarNearlyZero(
    x: SkScalar,
    tolerance: SkScalar = SK_ScalarNearlyZero,
): Boolean = SkScalarAbs(x) <= tolerance

/** `|x - y| <= tolerance`. Default tolerance: [SK_ScalarNearlyZero]. */
public fun SkScalarNearlyEqual(
    x: SkScalar,
    y: SkScalar,
    tolerance: SkScalar = SK_ScalarNearlyZero,
): Boolean = SkScalarAbs(x - y) <= tolerance

/**
 * `sin(radians)` snapped to `0f` when within [SK_ScalarSinCosNearlyZero] of zero.
 * Matches Skia's `SkScalarSinSnapToZero` — used by `SkMatrix::setRotate` so cardinal
 * angles produce bit-exact axis-aligned matrices.
 */
public fun SkScalarSinSnapToZero(radians: SkScalar): SkScalar {
    val v = SkScalarSin(radians)
    return if (SkScalarNearlyZero(v, SK_ScalarSinCosNearlyZero)) 0f else v
}

/** Cos counterpart of [SkScalarSinSnapToZero]. */
public fun SkScalarCosSnapToZero(radians: SkScalar): SkScalar {
    val v = SkScalarCos(radians)
    return if (SkScalarNearlyZero(v, SK_ScalarSinCosNearlyZero)) 0f else v
}

// ─── Misc ───────────────────────────────────────────────────────────────────

/**
 * Linear interpolation: returns `A` for `t == 0`, `B` for `t == 1`. `t` must
 * be in `[0, 1]` (Skia asserts it).
 */
public fun SkScalarInterp(a: SkScalar, b: SkScalar, t: SkScalar): SkScalar =
    a + (b - a) * t

/** Element-wise equality of two scalar arrays. */
public fun SkScalarsEqual(a: FloatArray, b: FloatArray, n: Int): Boolean {
    require(n >= 0)
    for (i in 0 until n) {
        if (a[i] != b[i]) return false
    }
    return true
}
