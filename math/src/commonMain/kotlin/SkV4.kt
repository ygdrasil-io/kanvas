package org.graphiks.math

/**
 * Four-component float vector. Iso-aligned port of upstream Skia's
 * `SkV4` ([include/core/SkM44.h:98](https://github.com/google/skia/blob/main/include/core/SkM44.h#L98)).
 *
 * Used as the homogeneous 4-vector by [SkM44] (rows, columns, mapped
 * points). Mirrors C++ struct semantics: component-wise `+ - *`
 * operators, scalar product, dot / normalize, indexed access.
 *
 * Stored immutable; use [copy] for in-place semantics.
 */
public data class SkV4(
    public val x: Float,
    public val y: Float,
    public val z: Float,
    public val w: Float,
) {

    public operator fun unaryMinus(): SkV4 = SkV4(-x, -y, -z, -w)

    public operator fun plus(v: SkV4): SkV4 = SkV4(x + v.x, y + v.y, z + v.z, w + v.w)
    public operator fun minus(v: SkV4): SkV4 = SkV4(x - v.x, y - v.y, z - v.z, w - v.w)

    /** Component-wise product. */
    public operator fun times(v: SkV4): SkV4 = SkV4(x * v.x, y * v.y, z * v.z, w * v.w)
    /** Scalar product on the right. */
    public operator fun times(s: Float): SkV4 = SkV4(x * s, y * s, z * s, w * s)

    public fun lengthSquared(): Float = Dot(this, this)
    public fun length(): Float = SkScalarSqrt(lengthSquared())

    public fun dot(v: SkV4): Float = Dot(this, v)
    public fun normalize(): SkV4 = Normalize(this)

    /** Indexed access mirrors C++ `operator[](int)`. */
    public operator fun get(i: Int): Float = when (i) {
        0 -> x
        1 -> y
        2 -> z
        3 -> w
        else -> throw IndexOutOfBoundsException("SkV4 index $i out of range [0..3]")
    }

    public companion object {
        public fun Dot(a: SkV4, b: SkV4): Float =
            SkMathScalar.dot4(a.x, a.y, a.z, a.w, b.x, b.y, b.z, b.w)
        public fun Normalize(v: SkV4): SkV4 = v * (1.0f / v.length())
    }
}

/** Scalar product on the left (`s * v`). */
public operator fun Float.times(v: SkV4): SkV4 = v * this
