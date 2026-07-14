package org.graphiks.math.vector

import org.graphiks.math.scalar.nearlyZero

public data class Vector4F32 internal constructor(
    public val x: Float, public val y: Float, public val z: Float, public val w: Float
) {
    public operator fun unaryMinus(): Vector4F32 = Vector4F32(-x, -y, -z, -w)
    public operator fun plus(v: Vector4F32): Vector4F32 = Vector4F32(x + v.x, y + v.y, z + v.z, w + v.w)
    public operator fun minus(v: Vector4F32): Vector4F32 = Vector4F32(x - v.x, y - v.y, z - v.z, w - v.w)
    public operator fun times(v: Vector4F32): Vector4F32 = Vector4F32(x * v.x, y * v.y, z * v.z, w * v.w)
    public operator fun times(s: Float): Vector4F32 = Vector4F32(x * s, y * s, z * s, w * s)
    public operator fun div(s: Float): Vector4F32 = Vector4F32(x / s, y / s, z / s, w / s)

    public operator fun get(i: Int): Float = when (i) {
        0 -> x; 1 -> y; 2 -> z; 3 -> w
        else -> throw IndexOutOfBoundsException("$i")
    }

    public fun lengthSquared(): Float = x * x + y * y + z * z + w * w
    public fun length(): Float = kotlin.math.sqrt(lengthSquared())
    public fun dot(v: Vector4F32): Float = x * v.x + y * v.y + z * v.z + w * v.w
    public fun normalize(): Vector4F32 {
        val len = length()
        return if (nearlyZero(len)) Vector4F32(0f, 0f, 0f, 0f) else this / len
    }

    public companion object {
        public fun of(x: Float = 0f, y: Float = 0f, z: Float = 0f, w: Float = 0f): Vector4F32 =
            Vector4F32(x, y, z, w)
    }
}

public operator fun Float.times(v: Vector4F32): Vector4F32 = v * this
