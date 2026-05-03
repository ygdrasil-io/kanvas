package org.skia.math

/**
 * Integer 2-D point. Mirrors Skia's `SkIPoint`. Mutable to match the C++
 * struct (used as both a value carrier and an in-place destination).
 */
public data class SkIPoint(public var fX: Int = 0, public var fY: Int = 0) {

    public fun x(): Int = fX

    public fun y(): Int = fY

    public fun isZero(): Boolean = fX == 0 && fY == 0

    public fun set(x: Int, y: Int) {
        fX = x
        fY = y
    }

    public fun offset(dx: Int, dy: Int) {
        fX += dx
        fY += dy
    }

    public fun negate() {
        fX = -fX
        fY = -fY
    }

    public fun equals(x: Int, y: Int): Boolean = fX == x && fY == y

    public companion object {
        public fun Make(x: Int, y: Int): SkIPoint = SkIPoint(x, y)
    }
}
