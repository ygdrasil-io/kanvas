package org.graphiks.math.geometry

/** Mutable F64 axis-aligned rectangle used only while preparing math snapshots. */
public data class RectF64(
    public var left: Double,
    public var top: Double,
    public var right: Double,
    public var bottom: Double,
) {
    public val isEmpty: Boolean get() = !(left < right && top < bottom)

    public fun isFinite(): Boolean = left.isFinite() && top.isFinite() && right.isFinite() && bottom.isFinite()

    public fun copyF64(): RectF64 = RectF64(left, top, right, bottom)

    public companion object {
        public fun ofLTRB(leftF64: Double, topF64: Double, rightF64: Double, bottomF64: Double): RectF64 =
            RectF64(leftF64, topF64, rightF64, bottomF64)
    }
}
