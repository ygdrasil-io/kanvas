package org.graphiks.math.vector

import org.graphiks.math.scalar.nearlyZero

/**
 * Immutable 2-D float vector.
 *
 * Iso-aligned port of Skia's `SkV2`
 * ([include/core/SkM44.h](https://github.com/google/skia/blob/main/include/core/SkM44.h)).
 */
public data class Vector2F32 internal constructor(public val x: Float, public val y: Float) {

    /** Returns `(-x, -y)`. */
    public operator fun unaryMinus(): Vector2F32 = Vector2F32(-x, -y)

    /** Component-wise addition. */
    public operator fun plus(v: Vector2F32): Vector2F32 = Vector2F32(x + v.x, y + v.y)

    /** Component-wise subtraction. */
    public operator fun minus(v: Vector2F32): Vector2F32 = Vector2F32(x - v.x, y - v.y)

    /** Component-wise multiplication. */
    public operator fun times(v: Vector2F32): Vector2F32 = Vector2F32(x * v.x, y * v.y)

    /** Uniform scaling. */
    public operator fun times(s: Float): Vector2F32 = Vector2F32(x * s, y * s)

    /** Division by a scalar. */
    public operator fun div(s: Float): Vector2F32 = Vector2F32(x / s, y / s)

    /** Squared Euclidean length. */
    public fun lengthSquared(): Float = x * x + y * y

    /**
     * Euclidean length. Uses double-precision for overflow safety.
     */
    public fun length(): Float {
        val mag2 = x * x + y * y
        if (mag2.isFinite()) return kotlin.math.sqrt(mag2)
        val xx = x.toDouble()
        val yy = y.toDouble()
        return kotlin.math.sqrt(xx * xx + yy * yy).toFloat()
    }

    /** Dot product. */
    public fun dot(v: Vector2F32): Float = x * v.x + y * v.y

    /** 2-D cross product (scalar z-component). */
    public fun cross(v: Vector2F32): Float = x * v.y - y * v.x

    /** Returns a unit vector, or `(0, 0)` if this vector is near-zero. */
    public fun normalize(): Vector2F32 {
        val len = length()
        return if (nearlyZero(len)) Vector2F32(0f, 0f) else this / len
    }

    /** Euclidean distance to [other]. */
    public fun distanceTo(other: Vector2F32): Float = (this - other).length()

    /** Returns `true` if both components are finite. */
    public fun isFinite(): Boolean = x.isFinite() && y.isFinite()

    /** Returns `true` if both components are within tolerance of zero. */
    public fun isZero(): Boolean = nearlyZero(x) && nearlyZero(y)

    public companion object {
        /** Zero vector. */
        public val Zero: Vector2F32 = Vector2F32(0f, 0f)
        /** Unit vector along the X axis. */
        public val UnitX: Vector2F32 = Vector2F32(1f, 0f)
        /** Unit vector along the Y axis. */
        public val UnitY: Vector2F32 = Vector2F32(0f, 1f)
        /** Creates a [Vector2F32] from components. */
        public fun of(x: Float = 0f, y: Float = 0f): Vector2F32 = Vector2F32(x, y)
    }
}

/** Scalar-times-vector: `s * v`. */
public operator fun Float.times(v: Vector2F32): Vector2F32 = v * this
