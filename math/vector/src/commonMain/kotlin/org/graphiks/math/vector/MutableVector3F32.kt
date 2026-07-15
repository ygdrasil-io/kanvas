package org.graphiks.math.vector

import org.graphiks.math.scalar.nearlyZero

/**
 * Mutable 3-D float vector.
 *
 * Provides in-place mutation (`set`, `offset`, `scale`, `negate`) and
 * conversion to immutable [Vector3F32].
 */
public class MutableVector3F32(public var x: Float, public var y: Float, public var z: Float) {

    /** Sets all three components. */
    public fun set(x: Float, y: Float, z: Float) { this.x = x; this.y = y; this.z = z }

    /** Adds `(dx, dy, dz)` to this vector. */
    public fun offset(dx: Float, dy: Float, dz: Float) { x += dx; y += dy; z += dz }

    /** Multiplies all components by [s]. */
    public fun scale(s: Float) { x *= s; y *= s; z *= s }

    /** Negates all components in place. */
    public fun negate() { x = -x; y = -y; z = -z }

    /** Euclidean length. */
    public fun length(): Float = lengthAsDouble(x, y, z).toFloat()

    /**
     * Normalizes this vector in place. Returns `false` if the vector
     * was near-zero (reset to zero).
     */
    public fun normalize(): Boolean {
        val len = lengthAsDouble(x, y, z)
        if (nearlyZero(len.toFloat())) {
            set(0f, 0f, 0f)
            return false
        }
        set(
            (x.toDouble() / len).toFloat(),
            (y.toDouble() / len).toFloat(),
            (z.toDouble() / len).toFloat(),
        )
        return true
    }

    /** Returns an immutable copy as a [Vector3F32]. */
    public fun toVector(): Vector3F32 = Vector3F32.of(x, y, z)

    public companion object {
        /** Creates a [MutableVector3F32] from components. */
        public fun of(x: Float = 0f, y: Float = 0f, z: Float = 0f): MutableVector3F32 = MutableVector3F32(x, y, z)

        /** Creates a [MutableVector3F32] from an immutable [Vector3F32]. */
        public fun from(v: Vector3F32): MutableVector3F32 = MutableVector3F32(v.x, v.y, v.z)
    }
}
