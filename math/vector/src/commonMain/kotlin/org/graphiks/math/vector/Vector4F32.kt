package org.graphiks.math.vector

import org.graphiks.math.scalar.nearlyZero

/**
 * Immutable 4-D float vector / homogeneous coordinate.
 *
 */
public data class Vector4F32 internal constructor(
    public val x: Float, public val y: Float, public val z: Float, public val w: Float
) {
    /** Returns `(-x, -y, -z, -w)`. */
    public operator fun unaryMinus(): Vector4F32 = Vector4F32(-x, -y, -z, -w)

    /** Component-wise addition. */
    public operator fun plus(v: Vector4F32): Vector4F32 = Vector4F32(x + v.x, y + v.y, z + v.z, w + v.w)

    /** Component-wise subtraction. */
    public operator fun minus(v: Vector4F32): Vector4F32 = Vector4F32(x - v.x, y - v.y, z - v.z, w - v.w)

    /** Component-wise multiplication. */
    public operator fun times(v: Vector4F32): Vector4F32 = Vector4F32(x * v.x, y * v.y, z * v.z, w * v.w)

    /** Uniform scaling. */
    public operator fun times(s: Float): Vector4F32 = Vector4F32(x * s, y * s, z * s, w * s)

    /** Division by a scalar. */
    public operator fun div(s: Float): Vector4F32 = Vector4F32(x / s, y / s, z / s, w / s)

    /** Indexed access: 0 → x, 1 → y, 2 → z, 3 → w. */
    public operator fun get(i: Int): Float = when (i) {
        0 -> x; 1 -> y; 2 -> z; 3 -> w
        else -> throw IndexOutOfBoundsException("$i")
    }

    /** Squared Euclidean length. */
    public fun lengthSquared(): Float = x * x + y * y + z * z + w * w

    /** Euclidean length. */
    public fun length(): Float = kotlin.math.sqrt(lengthSquared())

    /** Dot product. */
    public fun dot(v: Vector4F32): Float = x * v.x + y * v.y + z * v.z + w * v.w

    /** Returns a unit vector, or `(0, 0, 0, 0)` if this vector is near-zero. */
    public fun normalize(): Vector4F32 {
        val len = length()
        return if (nearlyZero(len)) Vector4F32(0f, 0f, 0f, 0f) else this / len
    }

    public companion object {
        /** Creates a [Vector4F32] from components. */
        public fun of(x: Float = 0f, y: Float = 0f, z: Float = 0f, w: Float = 0f): Vector4F32 =
            Vector4F32(x, y, z, w)
    }
}

/** Scalar-times-vector: `s * v`. */
public operator fun Float.times(v: Vector4F32): Vector4F32 = v * this
