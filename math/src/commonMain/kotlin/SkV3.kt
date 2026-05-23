package org.graphiks.math

import kotlin.math.sqrt

/**
 * Iso-aligned port of Skia's `SkV3` ([include/core/SkM44.h](https://github.com/google/skia/blob/main/include/core/SkM44.h)).
 *
 * Lightweight 3-component float vector used by the 4×4 matrix /
 * pseudo-3D camera utilities (`Sk3DView`, `SkCamera3D`).
 *
 * Note : kept as a separate type from [SkPoint3] to mirror the upstream
 * naming. `SkPoint3` is a mutable data class used by the lighting math
 * and the matrix's homogeneous mapping ; `SkV3` is intentionally
 * immutable and provides the vector-style `+`, `-`, `*` (scalar) /
 * dot / cross / `normalize` / `length` operators that Skia's pseudo-3D
 * code is written against.
 */
public data class SkV3(
    public val x: Float,
    public val y: Float,
    public val z: Float,
) {

    public operator fun plus(o: SkV3): SkV3 = SkV3(x + o.x, y + o.y, z + o.z)
    public operator fun minus(o: SkV3): SkV3 = SkV3(x - o.x, y - o.y, z - o.z)
    public operator fun unaryMinus(): SkV3 = SkV3(-x, -y, -z)

    /** Scalar multiply. */
    public operator fun times(s: Float): SkV3 = SkV3(x * s, y * s, z * s)

    /**
     * Component-wise product, matching Skia's `SkV3 operator*(const SkV3&)`
     * (`include/core/SkM44.h:74`). For the dot product use [dot] / [Dot].
     *
     * Breaking change vs the previous Kotlin port, which returned a `Float`
     * (the dot product) here — that diverged from upstream semantics
     * (math audit divergence #3). Any caller relying on `a * b` as the
     * dot product must switch to `a.dot(b)`.
     */
    public operator fun times(o: SkV3): SkV3 = SkV3(x * o.x, y * o.y, z * o.z)

    public fun dot(o: SkV3): Float = Dot(this, o)

    public fun cross(o: SkV3): SkV3 = Cross(this, o)

    public fun lengthSquared(): Float = Dot(this, this)
    public fun length(): Float = sqrt(lengthSquared().toDouble()).toFloat()

    /** Returns a unit-length copy ; if the vector is zero-length, returns zero. */
    public fun normalize(): SkV3 {
        val len = length()
        return if (len > 0f) this * (1f / len) else SkV3(0f, 0f, 0f)
    }

    public companion object {
        public val ZERO: SkV3 = SkV3(0f, 0f, 0f)

        /** Dot product, mirrors C++ `SkV3::Dot`. */
        public fun Dot(a: SkV3, b: SkV3): Float =
            SkMathBackend.dot3(a.x, a.y, a.z, b.x, b.y, b.z)

        /** Cross product, mirrors C++ `SkV3::Cross`. */
        public fun Cross(a: SkV3, b: SkV3): SkV3 = SkV3(
            a.y * b.z - a.z * b.y,
            a.z * b.x - a.x * b.z,
            a.x * b.y - a.y * b.x,
        )
    }
}

/** Scalar `*` on the left ; matches Skia's `operator*(float, SkV3)`. */
public operator fun Float.times(v: SkV3): SkV3 = v * this
