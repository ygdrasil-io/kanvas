package org.graphiks.math.scalar

import kotlin.math.*

@JvmInline
public value class ScalarF32 internal constructor(public val value: Float) {

    public fun isNearlyZero(tolerance: Float = 1e-7f): Boolean = abs(value) <= tolerance
    public fun isNearlyEqual(other: Float, tolerance: Float = 1e-7f): Boolean = abs(value - other) <= tolerance
    public fun clamp(min: Float, max: Float): Float = value.coerceIn(min, max)
    public fun floorToInt(): Int = floor(value).toInt()
    public fun ceilToInt(): Int = ceil(value).toInt()
    public fun roundToInt(): Int = round(value).toInt()
    public fun isFinite(): Boolean = value.isFinite()
    public fun isInteger(): Boolean = value == floor(value)
    public fun isNaN(): Boolean = value.isNaN()

    public companion object {
        public val Zero: ScalarF32 = ScalarF32(0f)
        public val One: ScalarF32 = ScalarF32(1f)
        public val Half: ScalarF32 = ScalarF32(0.5f)
        public val Pi: ScalarF32 = ScalarF32(PI.toFloat())
        public val TwoPi: ScalarF32 = ScalarF32((2.0 * PI).toFloat())
        public val PiOver2: ScalarF32 = ScalarF32((PI / 2.0).toFloat())
        public val Sqrt2: ScalarF32 = ScalarF32(sqrt(2.0).toFloat())
        public val Epsilon: ScalarF32 = ScalarF32(1e-7f)
        public val Max: ScalarF32 = ScalarF32(Float.MAX_VALUE)
        public val Infinity: ScalarF32 = ScalarF32(Float.POSITIVE_INFINITY)
        public val NegativeInfinity: ScalarF32 = ScalarF32(Float.NEGATIVE_INFINITY)
        public val NaN: ScalarF32 = ScalarF32(Float.NaN)

        public fun of(value: Float): ScalarF32 = ScalarF32(value)
    }
}

public fun nearlyZero(value: Float, tolerance: Float = 1e-7f): Boolean = abs(value) <= tolerance
public fun nearlyEqual(a: Float, b: Float, tolerance: Float = 1e-7f): Boolean = abs(a - b) <= tolerance
public fun clamp(value: Float, min: Float, max: Float): Float = value.coerceIn(min, max)
public fun interp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
public fun lerp(a: Float, b: Float, t: Float): Float = interp(a, b, t)
public fun sign(value: Float): Float = if (value > 0f) 1f else if (value < 0f) -1f else 0f

public fun sin(radians: Float): Float {
    val r = kotlin.math.sin(radians.toDouble()).toFloat()
    return if (nearlyZero(r)) 0f else r
}
public fun cos(radians: Float): Float {
    val r = kotlin.math.cos(radians.toDouble()).toFloat()
    return if (nearlyZero(r)) 0f else r
}
public fun tan(radians: Float): Float {
    val r = kotlin.math.tan(radians.toDouble()).toFloat()
    return if (nearlyZero(r)) 0f else r
}

public fun saturatingAdd32(a: Int, b: Int): Int {
    val sum = a.toLong() + b.toLong()
    return sum.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
}
public fun saturatingSub32(a: Int, b: Int): Int {
    val diff = a.toLong() - b.toLong()
    return diff.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
}
