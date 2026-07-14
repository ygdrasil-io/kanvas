package org.graphiks.math.geometry

/**
 * Mutable integer 2-D point/vector with saturating arithmetic.
 *
 *).
 *
 * Arithmetic operators (`+`, `-`, `+=`, `-=`) use saturating 32-bit
 * add/subtract .
 */
public data class Vector2I32(public var x: Int = 0, public var y: Int = 0) {

    /** Returns `true` if both components are zero. */
    public fun isZero(): Boolean = x == 0 && y == 0

    /** Sets both components. */
    public fun set(x: Int, y: Int) {
        this.x = x
        this.y = y
    }

    /** Negates both components in place. */
    public fun negate() {
        x = -x
        y = -y
    }

    /** Returns `(-x, -y)`. */
    public operator fun unaryMinus(): Vector2I32 = Vector2I32(-x, -y)

    /** Saturating addition: `this += v`. */
    public operator fun plusAssign(v: Vector2I32) {
        x = sk32SatAdd(x, v.x)
        y = sk32SatAdd(y, v.y)
    }

    /** Saturating subtraction: `this -= v`. */
    public operator fun minusAssign(v: Vector2I32) {
        x = sk32SatSub(x, v.x)
        y = sk32SatSub(y, v.y)
    }

    /** Saturating subtraction: returns `this - b`. */
    public operator fun minus(b: Vector2I32): Vector2I32 =
        Vector2I32(sk32SatSub(x, b.x), sk32SatSub(y, b.y))

    /** Saturating addition: returns `this + v`. */
    public operator fun plus(v: Vector2I32): Vector2I32 =
        Vector2I32(sk32SatAdd(x, v.x), sk32SatAdd(y, v.y))

    /** Offsets by `(dx, dy)` with no saturating checks. */
    public fun offset(dx: Int, dy: Int) {
        x += dx
        y += dy
    }

    /** Returns `true` if this point equals `(x, y)`. */
    public fun equals(x: Int, y: Int): Boolean = this.x == x && this.y == y

    public companion object {
        /** Creates a [Vector2I32] from components. */
        public fun Make(x: Int, y: Int): Vector2I32 = Vector2I32(x, y)

        /** Clamps a 64-bit value to the 32-bit signed range. */
        public fun sk64PinToS32(x: Long): Int = when {
            x < Int.MIN_VALUE.toLong() -> Int.MIN_VALUE
            x > Int.MAX_VALUE.toLong() -> Int.MAX_VALUE
            else -> x.toInt()
        }

        /** Saturating 32-bit addition. */
        public fun sk32SatAdd(a: Int, b: Int): Int = sk64PinToS32(a.toLong() + b.toLong())

        /** Saturating 32-bit subtraction. */
        public fun sk32SatSub(a: Int, b: Int): Int = sk64PinToS32(a.toLong() - b.toLong())
    }
}

/**
 * Alias for [Vector2I32] — the same type is used for both points and
 * vectors.
 */
public typealias Point2I32 = Vector2I32
