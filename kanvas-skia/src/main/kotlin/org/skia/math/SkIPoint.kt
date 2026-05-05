package org.skia.math

/**
 * Integer 2-D point. Iso-aligned port of Skia's `SkIPoint`
 * ([include/private/base/SkPoint_impl.h](https://github.com/google/skia/blob/main/include/private/base/SkPoint_impl.h)).
 *
 * Mutable to match the C++ struct (used as both a value carrier and an
 * in-place destination). Skia's `SkIVector` is a typealias of `SkIPoint`;
 * see [SkIVector] below.
 *
 * **Saturating arithmetic:** the `+=`, `-=`, `+`, `-` operators saturate
 * at `Int.MIN_VALUE` / `Int.MAX_VALUE` instead of wrapping, mirroring
 * Skia's `Sk32_sat_add` / `Sk32_sat_sub`
 * ([SkSafe32.h](https://github.com/google/skia/blob/main/include/private/base/SkSafe32.h)).
 * Wrap-around on int overflow would silently corrupt clip rects in
 * upstream callers, so the saturating semantics are load-bearing.
 */
public data class SkIPoint(public var fX: Int = 0, public var fY: Int = 0) {

    public fun x(): Int = fX

    public fun y(): Int = fY

    public fun isZero(): Boolean = fX == 0 && fY == 0

    public fun set(x: Int, y: Int) {
        fX = x
        fY = y
    }

    public fun negate() {
        fX = -fX
        fY = -fY
    }

    public operator fun unaryMinus(): SkIPoint = SkIPoint(-fX, -fY)

    /** Saturating in-place add (mirrors `Sk32_sat_add`). */
    public operator fun plusAssign(v: SkIVector) {
        fX = sk32SatAdd(fX, v.fX)
        fY = sk32SatAdd(fY, v.fY)
    }

    /** Saturating in-place subtract (mirrors `Sk32_sat_sub`). */
    public operator fun minusAssign(v: SkIVector) {
        fX = sk32SatSub(fX, v.fX)
        fY = sk32SatSub(fY, v.fY)
    }

    /** Saturating subtract — returns a new vector. */
    public operator fun minus(b: SkIPoint): SkIVector =
        SkIPoint(sk32SatSub(fX, b.fX), sk32SatSub(fY, b.fY))

    /** Saturating add — returns a new point. */
    public operator fun plus(v: SkIVector): SkIPoint =
        SkIPoint(sk32SatAdd(fX, v.fX), sk32SatAdd(fY, v.fY))

    /**
     * `offset(dx, dy)` is the **non-saturating** in-place add upstream uses
     * for hot-path geometry where the inputs are pre-validated. Wraps on
     * overflow — match this with care.
     */
    public fun offset(dx: Int, dy: Int) {
        fX += dx
        fY += dy
    }

    public fun equals(x: Int, y: Int): Boolean = fX == x && fY == y

    public companion object {
        public fun Make(x: Int, y: Int): SkIPoint = SkIPoint(x, y)

        /** Pin a 64-bit value to `[Int.MIN_VALUE, Int.MAX_VALUE]`. Skia: `Sk64_pin_to_s32`. */
        internal fun sk64PinToS32(x: Long): Int = when {
            x < Int.MIN_VALUE.toLong() -> Int.MIN_VALUE
            x > Int.MAX_VALUE.toLong() -> Int.MAX_VALUE
            else -> x.toInt()
        }

        internal fun sk32SatAdd(a: Int, b: Int): Int = sk64PinToS32(a.toLong() + b.toLong())
        internal fun sk32SatSub(a: Int, b: Int): Int = sk64PinToS32(a.toLong() - b.toLong())
    }
}

/** Skia's `SkIVector` is a typealias of `SkIPoint`. */
public typealias SkIVector = SkIPoint
