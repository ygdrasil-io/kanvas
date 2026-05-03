package org.skia.math

public typealias SkScalar = Float

public const val SK_Scalar1: SkScalar = 1.0f
public const val SK_ScalarHalf: SkScalar = 0.5f
public const val SK_ScalarPI: SkScalar = 3.14159265358979323846f
public const val SK_Scalar2PI: SkScalar = 6.28318530717958647692f
public const val SK_ScalarPIOver2: SkScalar = 1.57079632679489661923f

public fun SkScalarSin(radians: SkScalar): SkScalar =
    kotlin.math.sin(radians.toDouble()).toFloat()

public fun SkScalarCos(radians: SkScalar): SkScalar =
    kotlin.math.cos(radians.toDouble()).toFloat()

public fun SkScalarTan(radians: SkScalar): SkScalar =
    kotlin.math.tan(radians.toDouble()).toFloat()

public fun SkScalarAbs(value: SkScalar): SkScalar = kotlin.math.abs(value)

public fun SkScalarSqrt(value: SkScalar): SkScalar =
    kotlin.math.sqrt(value.toDouble()).toFloat()

public fun SkScalarFloor(value: SkScalar): SkScalar =
    kotlin.math.floor(value.toDouble()).toFloat()

public fun SkScalarCeil(value: SkScalar): SkScalar =
    kotlin.math.ceil(value.toDouble()).toFloat()

public fun SkScalarRound(value: SkScalar): SkScalar =
    kotlin.math.round(value.toDouble()).toFloat()

public fun SkScalarFloorToInt(value: SkScalar): Int = kotlin.math.floor(value).toInt()
public fun SkScalarCeilToInt(value: SkScalar): Int = kotlin.math.ceil(value).toInt()
public fun SkScalarRoundToInt(value: SkScalar): Int = kotlin.math.round(value).toInt()

public fun SkIntToScalar(value: Int): SkScalar = value.toFloat()
public fun SkScalarToInt(value: SkScalar): Int = value.toInt()

public fun SkScalarDegreesToRadians(degrees: SkScalar): SkScalar =
    degrees * (SK_ScalarPI / 180.0f)

public fun SkScalarRadiansToDegrees(radians: SkScalar): SkScalar =
    radians * (180.0f / SK_ScalarPI)
