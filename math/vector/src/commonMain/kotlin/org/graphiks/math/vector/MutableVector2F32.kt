package org.graphiks.math.vector

import org.graphiks.math.scalar.nearlyZero

/**
 * Mutable 2-D float vector.
 *
 * Provides in-place mutation (`set`, `offset`, `scale`, `negate`) and
 * conversion to immutable [Vector2F32].
 */
public class MutableVector2F32(public var x: Float, public var y: Float) {

    /** Sets both components. */
    public fun set(x: Float, y: Float) { this.x = x; this.y = y }

    /** Adds `(dx, dy)` to this vector. */
    public fun offset(dx: Float, dy: Float) { x += dx; y += dy }

    /** Multiplies both components by [s]. */
    public fun scale(s: Float) { x *= s; y *= s }

    /** Negates both components in place. */
    public fun negate() { x = -x; y = -y }

    /**
     * Euclidean length. Uses double-precision for overflow safety.
     */
    public fun length(): Float {
        val mag2 = x * x + y * y
        if (mag2.isFinite()) return kotlin.math.sqrt(mag2)
        val xx = x.toDouble(); val yy = y.toDouble()
        return kotlin.math.sqrt(xx * xx + yy * yy).toFloat()
    }

    /**
     * Normalizes this vector in place. Returns `false` if the vector
     * was near-zero (reset to zero).
     */
    public fun normalize(): Boolean {
        val xx = x.toDouble(); val yy = y.toDouble()
        val dmag = kotlin.math.sqrt(xx * xx + yy * yy)
        val dscale = 1.0 / dmag
        val nx = (xx * dscale).toFloat(); val ny = (yy * dscale).toFloat()
        if (!nx.isFinite() || !ny.isFinite() || (nx == 0f && ny == 0f)) { set(0f, 0f); return false }
        set(nx, ny); return true
    }

    /**
     * Scales this vector to the given [length] in place. Returns `false`
     * if the vector was near-zero (reset to zero).
     */
    public fun setLength(length: Float): Boolean {
        val xx = x.toDouble(); val yy = y.toDouble()
        val dmag = kotlin.math.sqrt(xx * xx + yy * yy)
        val dscale = length.toDouble() / dmag
        val nx = (xx * dscale).toFloat(); val ny = (yy * dscale).toFloat()
        if (!nx.isFinite() || !ny.isFinite() || (nx == 0f && ny == 0f)) { set(0f, 0f); return false }
        set(nx, ny); return true
    }

    /** Returns an immutable copy as a [Vector2F32]. */
    public fun toVector(): Vector2F32 = Vector2F32.of(x, y)

    /** Returns `true` if both components are within tolerance of zero. */
    public fun isZero(): Boolean = nearlyZero(x) && nearlyZero(y)

    public companion object {
        /** Creates a [MutableVector2F32] from components. */
        public fun of(x: Float = 0f, y: Float = 0f): MutableVector2F32 = MutableVector2F32(x, y)

        /** Creates a [MutableVector2F32] from an immutable [Vector2F32]. */
        public fun from(v: Vector2F32): MutableVector2F32 = MutableVector2F32(v.x, v.y)
    }
}
