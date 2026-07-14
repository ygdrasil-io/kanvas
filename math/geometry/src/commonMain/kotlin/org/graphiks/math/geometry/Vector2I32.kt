package org.graphiks.math.geometry

public data class Vector2I32(public var x: Int = 0, public var y: Int = 0) {

    public fun isZero(): Boolean = x == 0 && y == 0

    public fun set(x: Int, y: Int) {
        this.x = x
        this.y = y
    }

    public fun negate() {
        x = -x
        y = -y
    }

    public operator fun unaryMinus(): Vector2I32 = Vector2I32(-x, -y)

    public operator fun plusAssign(v: Vector2I32) {
        x = sk32SatAdd(x, v.x)
        y = sk32SatAdd(y, v.y)
    }

    public operator fun minusAssign(v: Vector2I32) {
        x = sk32SatSub(x, v.x)
        y = sk32SatSub(y, v.y)
    }

    public operator fun minus(b: Vector2I32): Vector2I32 =
        Vector2I32(sk32SatSub(x, b.x), sk32SatSub(y, b.y))

    public operator fun plus(v: Vector2I32): Vector2I32 =
        Vector2I32(sk32SatAdd(x, v.x), sk32SatAdd(y, v.y))

    public fun offset(dx: Int, dy: Int) {
        x += dx
        y += dy
    }

    public fun equals(x: Int, y: Int): Boolean = this.x == x && this.y == y

    public companion object {
        public fun Make(x: Int, y: Int): Vector2I32 = Vector2I32(x, y)

        public fun sk64PinToS32(x: Long): Int = when {
            x < Int.MIN_VALUE.toLong() -> Int.MIN_VALUE
            x > Int.MAX_VALUE.toLong() -> Int.MAX_VALUE
            else -> x.toInt()
        }

        public fun sk32SatAdd(a: Int, b: Int): Int = sk64PinToS32(a.toLong() + b.toLong())
        public fun sk32SatSub(a: Int, b: Int): Int = sk64PinToS32(a.toLong() - b.toLong())
    }
}

public typealias Point2I32 = Vector2I32
