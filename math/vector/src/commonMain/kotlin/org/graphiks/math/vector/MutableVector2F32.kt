package org.graphiks.math.vector

import org.graphiks.math.scalar.nearlyZero

public class MutableVector2F32(public var x: Float, public var y: Float) {

    public fun set(x: Float, y: Float) { this.x = x; this.y = y }
    public fun offset(dx: Float, dy: Float) { x += dx; y += dy }
    public fun scale(s: Float) { x *= s; y *= s }
    public fun negate() { x = -x; y = -y }

    public fun length(): Float {
        val mag2 = x * x + y * y
        if (mag2.isFinite()) return kotlin.math.sqrt(mag2)
        val xx = x.toDouble(); val yy = y.toDouble()
        return kotlin.math.sqrt(xx * xx + yy * yy).toFloat()
    }

    public fun normalize(): Boolean {
        val xx = x.toDouble(); val yy = y.toDouble()
        val dmag = kotlin.math.sqrt(xx * xx + yy * yy)
        val dscale = 1.0 / dmag
        val nx = (xx * dscale).toFloat(); val ny = (yy * dscale).toFloat()
        if (!nx.isFinite() || !ny.isFinite() || (nx == 0f && ny == 0f)) { set(0f, 0f); return false }
        set(nx, ny); return true
    }

    public fun setLength(length: Float): Boolean {
        val xx = x.toDouble(); val yy = y.toDouble()
        val dmag = kotlin.math.sqrt(xx * xx + yy * yy)
        val dscale = length.toDouble() / dmag
        val nx = (xx * dscale).toFloat(); val ny = (yy * dscale).toFloat()
        if (!nx.isFinite() || !ny.isFinite() || (nx == 0f && ny == 0f)) { set(0f, 0f); return false }
        set(nx, ny); return true
    }

    public fun toVector(): Vector2F32 = Vector2F32.of(x, y)
    public fun isZero(): Boolean = nearlyZero(x) && nearlyZero(y)

    public companion object {
        public fun of(x: Float = 0f, y: Float = 0f): MutableVector2F32 = MutableVector2F32(x, y)
        public fun from(v: Vector2F32): MutableVector2F32 = MutableVector2F32(v.x, v.y)
    }
}
