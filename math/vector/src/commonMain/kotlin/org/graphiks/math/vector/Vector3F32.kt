package org.graphiks.math.vector

import org.graphiks.math.scalar.nearlyZero

/**
 * Immutable 3-D float vector.
 *
 */
public data class Vector3F32 internal constructor(public val x: Float, public val y: Float, public val z: Float) {

    /** Returns `(-x, -y, -z)`. */
    public operator fun unaryMinus(): Vector3F32 = Vector3F32(-x, -y, -z)

    /** Component-wise addition. */
    public operator fun plus(v: Vector3F32): Vector3F32 = Vector3F32(x + v.x, y + v.y, z + v.z)

    /** Component-wise subtraction. */
    public operator fun minus(v: Vector3F32): Vector3F32 = Vector3F32(x - v.x, y - v.y, z - v.z)

    /** Component-wise multiplication. */
    public operator fun times(v: Vector3F32): Vector3F32 = Vector3F32(x * v.x, y * v.y, z * v.z)

    /** Uniform scaling. */
    public operator fun times(s: Float): Vector3F32 = Vector3F32(x * s, y * s, z * s)

    /** Division by a scalar. */
    public operator fun div(s: Float): Vector3F32 = Vector3F32(x / s, y / s, z / s)

    /** Squared Euclidean length. */
    public fun lengthSquared(): Float = x * x + y * y + z * z

    /** Euclidean length. */
    public fun length(): Float = kotlin.math.sqrt(lengthSquared())

    /** Dot product. */
    public fun dot(v: Vector3F32): Float = x * v.x + y * v.y + z * v.z

    /** 3-D cross product. */
    public fun cross(v: Vector3F32): Vector3F32 = Vector3F32(
        y * v.z - z * v.y, z * v.x - x * v.z, x * v.y - y * v.x
    )

    /** Returns a unit vector, or `(0, 0, 0)` if this vector is near-zero. */
    public fun normalize(): Vector3F32 {
        val len = length()
        return if (nearlyZero(len)) Vector3F32(0f, 0f, 0f) else this / len
    }

    /** Returns `true` if all components are finite. */
    public fun isFinite(): Boolean = x.isFinite() && y.isFinite() && z.isFinite()

    public companion object {
        /** Zero vector. */
        public val Zero: Vector3F32 = Vector3F32(0f, 0f, 0f)
        /** Creates a [Vector3F32] from components. */
        public fun of(x: Float = 0f, y: Float = 0f, z: Float = 0f): Vector3F32 = Vector3F32(x, y, z)
    }
}

/** Scalar-times-vector: `s * v`. */
public operator fun Float.times(v: Vector3F32): Vector3F32 = v * this
