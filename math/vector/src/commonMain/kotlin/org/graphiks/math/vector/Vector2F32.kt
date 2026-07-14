package org.graphiks.math.vector

import org.graphiks.math.scalar.nearlyZero

public data class Vector2F32 internal constructor(public val x: Float, public val y: Float) {

    public operator fun unaryMinus(): Vector2F32 = Vector2F32(-x, -y)
    public operator fun plus(v: Vector2F32): Vector2F32 = Vector2F32(x + v.x, y + v.y)
    public operator fun minus(v: Vector2F32): Vector2F32 = Vector2F32(x - v.x, y - v.y)
    public operator fun times(v: Vector2F32): Vector2F32 = Vector2F32(x * v.x, y * v.y)
    public operator fun times(s: Float): Vector2F32 = Vector2F32(x * s, y * s)
    public operator fun div(s: Float): Vector2F32 = Vector2F32(x / s, y / s)

    public fun lengthSquared(): Float = x * x + y * y
    public fun length(): Float {
        val mag2 = x * x + y * y
        if (mag2.isFinite()) return kotlin.math.sqrt(mag2)
        val xx = x.toDouble()
        val yy = y.toDouble()
        return kotlin.math.sqrt(xx * xx + yy * yy).toFloat()
    }
    public fun dot(v: Vector2F32): Float = x * v.x + y * v.y
    public fun cross(v: Vector2F32): Float = x * v.y - y * v.x
    public fun normalize(): Vector2F32 {
        val len = length()
        return if (nearlyZero(len)) Vector2F32(0f, 0f) else this / len
    }
    public fun distanceTo(other: Vector2F32): Float = (this - other).length()
    public fun isFinite(): Boolean = x.isFinite() && y.isFinite()
    public fun isZero(): Boolean = nearlyZero(x) && nearlyZero(y)

    public companion object {
        public val Zero: Vector2F32 = Vector2F32(0f, 0f)
        public val UnitX: Vector2F32 = Vector2F32(1f, 0f)
        public val UnitY: Vector2F32 = Vector2F32(0f, 1f)
        public fun of(x: Float = 0f, y: Float = 0f): Vector2F32 = Vector2F32(x, y)
    }
}

public operator fun Float.times(v: Vector2F32): Vector2F32 = v * this
