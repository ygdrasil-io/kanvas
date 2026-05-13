package org.skia.math

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

    /** Skia's `operator*(SkV3, SkV3)` is the dot product (NOT component-wise). */
    public operator fun times(o: SkV3): Float = x * o.x + y * o.y + z * o.z

    public fun dot(o: SkV3): Float = x * o.x + y * o.y + z * o.z

    public fun cross(o: SkV3): SkV3 = SkV3(
        y * o.z - z * o.y,
        z * o.x - x * o.z,
        x * o.y - y * o.x,
    )

    public fun lengthSquared(): Float = this.dot(this)
    public fun length(): Float = sqrt(lengthSquared().toDouble()).toFloat()

    /** Returns a unit-length copy ; if the vector is zero-length, returns zero. */
    public fun normalize(): SkV3 {
        val len = length()
        return if (len > 0f) this * (1f / len) else SkV3(0f, 0f, 0f)
    }

    public companion object {
        public val ZERO: SkV3 = SkV3(0f, 0f, 0f)
    }
}

/** Scalar `*` on the left ; matches Skia's `operator*(float, SkV3)`. */
public operator fun Float.times(v: SkV3): SkV3 = v * this
