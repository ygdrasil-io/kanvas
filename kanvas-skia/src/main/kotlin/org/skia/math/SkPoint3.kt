package org.skia.math

import kotlin.math.sqrt

/**
 * Iso-aligned port of Skia's `SkPoint3` ([include/core/SkPoint3.h](https://github.com/google/skia/blob/main/include/core/SkPoint3.h)).
 *
 * 3-component float vector. Used by `SkMatrix` to expose homogeneous
 * coordinates (no perspective divide) via `mapPointsToHomogeneous`,
 * and as the 3D scratch type for path / lighting math.
 */
public data class SkPoint3(
    public var fX: Float = 0f,
    public var fY: Float = 0f,
    public var fZ: Float = 0f,
) {
    public fun x(): Float = fX
    public fun y(): Float = fY
    public fun z(): Float = fZ

    public fun set(x: Float, y: Float, z: Float) {
        fX = x; fY = y; fZ = z
    }

    public fun length(): Float = Length(fX, fY, fZ)

    public fun lengthSquared(): Float = fX * fX + fY * fY + fZ * fZ

    public fun isFinite(): Boolean = fX.isFinite() && fY.isFinite() && fZ.isFinite()

    public operator fun unaryMinus(): SkPoint3 = SkPoint3(-fX, -fY, -fZ)

    public operator fun plus(b: SkPoint3): SkPoint3 = SkPoint3(fX + b.fX, fY + b.fY, fZ + b.fZ)
    public operator fun minus(b: SkPoint3): SkPoint3 = SkPoint3(fX - b.fX, fY - b.fY, fZ - b.fZ)
    public operator fun times(scale: Float): SkPoint3 = SkPoint3(fX * scale, fY * scale, fZ * scale)

    public companion object {
        public fun Make(x: Float, y: Float, z: Float): SkPoint3 = SkPoint3(x, y, z)

        public fun Length(x: Float, y: Float, z: Float): Float =
            sqrt((x.toDouble() * x + y.toDouble() * y + z.toDouble() * z)).toFloat()

        public fun DotProduct(a: SkPoint3, b: SkPoint3): Float =
            a.fX * b.fX + a.fY * b.fY + a.fZ * b.fZ

        public fun CrossProduct(a: SkPoint3, b: SkPoint3): SkPoint3 = SkPoint3(
            a.fY * b.fZ - a.fZ * b.fY,
            a.fZ * b.fX - a.fX * b.fZ,
            a.fX * b.fY - a.fY * b.fX,
        )
    }
}
