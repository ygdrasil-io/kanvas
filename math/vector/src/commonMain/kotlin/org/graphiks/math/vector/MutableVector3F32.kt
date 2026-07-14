package org.graphiks.math.vector

import org.graphiks.math.scalar.nearlyZero

public class MutableVector3F32(public var x: Float, public var y: Float, public var z: Float) {

    public fun set(x: Float, y: Float, z: Float) { this.x = x; this.y = y; this.z = z }
    public fun offset(dx: Float, dy: Float, dz: Float) { x += dx; y += dy; z += dz }
    public fun scale(s: Float) { x *= s; y *= s; z *= s }
    public fun negate() { x = -x; y = -y; z = -z }

    public fun length(): Float = kotlin.math.sqrt(x * x + y * y + z * z)
    public fun normalize(): Boolean {
        val len = length()
        return if (nearlyZero(len)) { set(0f, 0f, 0f); false }
        else { scale(1f / len); true }
    }

    public fun toVector(): Vector3F32 = Vector3F32.of(x, y, z)

    public companion object {
        public fun of(x: Float = 0f, y: Float = 0f, z: Float = 0f): MutableVector3F32 = MutableVector3F32(x, y, z)
        public fun from(v: Vector3F32): MutableVector3F32 = MutableVector3F32(v.x, v.y, v.z)
    }
}
