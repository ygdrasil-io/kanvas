package org.skia.math

/**
 * Two-component float vector. Iso-aligned port of upstream Skia's `SkV2`
 * ([include/core/SkM44.h:19](https://github.com/google/skia/blob/main/include/core/SkM44.h#L19)).
 *
 * Used as the 2D counterpart to [SkV3] / [SkV4]; appears in upstream
 * sources mainly as a parameter type alongside [SkM44]. Mirrors the
 * C++ struct semantics — `+ - *` operators, dot / cross, length /
 * normalize.
 *
 * Stored as immutable `x, y`; use [copy] for in-place semantics. Mirrors
 * the surface area of `SkV3.kt` so the three vector types compose
 * uniformly.
 */
public data class SkV2(public val x: Float, public val y: Float) {

    public operator fun unaryMinus(): SkV2 = SkV2(-x, -y)

    public operator fun plus(v: SkV2): SkV2 = SkV2(x + v.x, y + v.y)
    public operator fun minus(v: SkV2): SkV2 = SkV2(x - v.x, y - v.y)

    /** Component-wise product, mirrors C++ `operator*(SkV2)`. */
    public operator fun times(v: SkV2): SkV2 = SkV2(x * v.x, y * v.y)
    /** Scalar product on the right. */
    public operator fun times(s: Float): SkV2 = SkV2(x * s, y * s)
    /** Scalar quotient on the right. */
    public operator fun div(s: Float): SkV2 = SkV2(x / s, y / s)

    public fun lengthSquared(): Float = Dot(this, this)
    public fun length(): Float = SkScalarSqrt(lengthSquared())

    public fun dot(v: SkV2): Float = Dot(this, v)
    public fun cross(v: SkV2): Float = Cross(this, v)
    public fun normalize(): SkV2 = Normalize(this)

    public companion object {
        public fun Dot(a: SkV2, b: SkV2): Float = a.x * b.x + a.y * b.y
        public fun Cross(a: SkV2, b: SkV2): Float = a.x * b.y - a.y * b.x
        public fun Normalize(v: SkV2): SkV2 = v * (1.0f / v.length())
    }
}

/** Scalar product on the left (`s * v`). */
public operator fun Float.times(v: SkV2): SkV2 = v * this
