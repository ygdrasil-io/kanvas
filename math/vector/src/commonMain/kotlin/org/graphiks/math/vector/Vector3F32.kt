package org.graphiks.math.vector

import org.graphiks.math.scalar.nearlyZero

public data class Vector3F32 internal constructor(public val x: Float, public val y: Float, public val z: Float) {

    public operator fun unaryMinus(): Vector3F32 = Vector3F32(-x, -y, -z)
    public operator fun plus(v: Vector3F32): Vector3F32 = Vector3F32(x + v.x, y + v.y, z + v.z)
    public operator fun minus(v: Vector3F32): Vector3F32 = Vector3F32(x - v.x, y - v.y, z - v.z)
    public operator fun times(v: Vector3F32): Vector3F32 = Vector3F32(x * v.x, y * v.y, z * v.z)
    public operator fun times(s: Float): Vector3F32 = Vector3F32(x * s, y * s, z * s)
    public operator fun div(s: Float): Vector3F32 = Vector3F32(x / s, y / s, z / s)

    public fun lengthSquared(): Float = x * x + y * y + z * z
    public fun length(): Float = kotlin.math.sqrt(lengthSquared())
    public fun dot(v: Vector3F32): Float = x * v.x + y * v.y + z * v.z
    public fun cross(v: Vector3F32): Vector3F32 = Vector3F32(
        y * v.z - z * v.y, z * v.x - x * v.z, x * v.y - y * v.x
    )
    public fun normalize(): Vector3F32 {
        val len = length()
        return if (nearlyZero(len)) Vector3F32(0f, 0f, 0f) else this / len
    }
    public fun isFinite(): Boolean = x.isFinite() && y.isFinite() && z.isFinite()

    public companion object {
        public val Zero: Vector3F32 = Vector3F32(0f, 0f, 0f)
        public fun of(x: Float = 0f, y: Float = 0f, z: Float = 0f): Vector3F32 = Vector3F32(x, y, z)
    }
}

public operator fun Float.times(v: Vector3F32): Vector3F32 = v * this
