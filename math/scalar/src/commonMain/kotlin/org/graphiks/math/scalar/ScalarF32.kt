package org.graphiks.math.scalar

import kotlin.jvm.JvmInline
import kotlin.math.*

/**
 * Single-precision scalar value.
 *
 */
@JvmInline
public value class ScalarF32 internal constructor(public val value: Float) {

    /** Returns `true` if this value is within [tolerance] of zero. */
    public fun isNearlyZero(tolerance: Float = 1e-7f): Boolean = abs(value) <= tolerance

    /** Returns `true` if this value is within [tolerance] of [other]. */
    public fun isNearlyEqual(other: Float, tolerance: Float = 1e-7f): Boolean = abs(value - other) <= tolerance

    /** Clamps this value to the range `[min, max]`. */
    public fun clamp(min: Float, max: Float): Float = value.coerceIn(min, max)

    /** Returns the largest integer <= this value. */
    public fun floorToInt(): Int = floor(value).toInt()

    /** Returns the smallest integer >= this value. */
    public fun ceilToInt(): Int = ceil(value).toInt()

    /** Rounds this value to the nearest integer (ties toward +infinity). */
    public fun roundToInt(): Int = floor(value.toDouble() + 0.5).toInt()

    /** Returns `true` if this value is finite (not infinite or NaN). */
    public fun isFinite(): Boolean = value.isFinite()

    /** Returns `true` if this value is an integer. */
    public fun isInteger(): Boolean = value == floor(value)

    /** Returns `true` if this value is NaN. */
    public fun isNaN(): Boolean = value.isNaN()

    public companion object {
        /** Zero scalar. */
        public val Zero: ScalarF32 = ScalarF32(0f)
        /** One scalar. */
        public val One: ScalarF32 = ScalarF32(1f)
        /** Half (0.5) scalar. */
        public val Half: ScalarF32 = ScalarF32(0.5f)
        /** Pi. */
        public val Pi: ScalarF32 = ScalarF32(PI.toFloat())
        /** Two times Pi. */
        public val TwoPi: ScalarF32 = ScalarF32((2.0 * PI).toFloat())
        /** Pi divided by 2. */
        public val PiOver2: ScalarF32 = ScalarF32((PI / 2.0).toFloat())
        /** Square root of 2. */
        public val Sqrt2: ScalarF32 = ScalarF32(sqrt(2.0).toFloat())
        /** Default epsilon (1e-7). */
        public val Epsilon: ScalarF32 = ScalarF32(1e-7f)
        /** Maximum finite float value. */
        public val Max: ScalarF32 = ScalarF32(Float.MAX_VALUE)
        /** Positive infinity. */
        public val Infinity: ScalarF32 = ScalarF32(Float.POSITIVE_INFINITY)
        /** Negative infinity. */
        public val NegativeInfinity: ScalarF32 = ScalarF32(Float.NEGATIVE_INFINITY)
        /** Not-a-Number. */
        public val NaN: ScalarF32 = ScalarF32(Float.NaN)

        /** Creates a [ScalarF32] from a raw [Float] value. */
        public fun of(value: Float): ScalarF32 = ScalarF32(value)
    }
}

/**
 * Returns `true` if [value] is within [tolerance] of zero.
 */
public fun nearlyZero(value: Float, tolerance: Float = 1e-7f): Boolean = abs(value) <= tolerance

/**
 * Returns `true` if `abs(a - b) <= tolerance`.
 */
public fun nearlyEqual(a: Float, b: Float, tolerance: Float = 1e-7f): Boolean = abs(a - b) <= tolerance

/**
 * Clamps [value] to the range `[min, max]`.
 */
public fun clamp(value: Float, min: Float, max: Float): Float = value.coerceIn(min, max)

/**
 * Linearly interpolates between [a] and [b] by [t]: `a + (b - a) * t`.
 */
public fun interp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

/**
 * Alias for [interp].
 */
public fun lerp(a: Float, b: Float, t: Float): Float = interp(a, b, t)

/**
 * Returns 1f for positive, -1f for negative, 0f for zero.
 */
public fun sign(value: Float): Float = if (value > 0f) 1f else if (value < 0f) -1f else 0f

/**
 * Sine of [radians]. Snaps near-zero results to exactly 0f.
 */
public fun sin(radians: Float): Float {
    val r = kotlin.math.sin(radians.toDouble()).toFloat()
    return if (nearlyZero(r)) 0f else r
}

/**
 * Cosine of [radians]. Snaps near-zero results to exactly 0f.
 */
public fun cos(radians: Float): Float {
    val r = kotlin.math.cos(radians.toDouble()).toFloat()
    return if (nearlyZero(r)) 0f else r
}

/**
 * Tangent of [radians]. Snaps near-zero results to exactly 0f.
 */
public fun tan(radians: Float): Float {
    val r = kotlin.math.tan(radians.toDouble()).toFloat()
    return if (nearlyZero(r)) 0f else r
}

/**
 * Saturating 32-bit integer addition: clamps result to `[Int.MIN_VALUE, Int.MAX_VALUE]`.
 *
 * ([src/core/SkUtils.h](https://github.com/google/skia/blob/main/src/core/SkUtils.h)).
 */
public fun saturatingAdd32(a: Int, b: Int): Int {
    val sum = a.toLong() + b.toLong()
    return sum.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
}

/**
 * Saturating 32-bit integer subtraction: clamps result to `[Int.MIN_VALUE, Int.MAX_VALUE]`.
 *
 * ([src/core/SkUtils.h](https://github.com/google/skia/blob/main/src/core/SkUtils.h)).
 */
public fun saturatingSub32(a: Int, b: Int): Int {
    val diff = a.toLong() - b.toLong()
    return diff.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
}
