package org.graphiks.math.geometry

/** F64 elliptical corner radii. */
public data class CornerRadiiF64(public val xF64: Double, public val yF64: Double) {
    public fun isFinite(): Boolean = xF64.isFinite() && yF64.isFinite()
    public companion object { public val Zero: CornerRadiiF64 = CornerRadiiF64(0.0, 0.0); public fun of(radiusF64: Double): CornerRadiiF64 = CornerRadiiF64(radiusF64, radiusF64); public fun of(xF64: Double, yF64: Double): CornerRadiiF64 = CornerRadiiF64(xF64, yF64) }
}

/** Axis-aligned rounded rectangle snapshot with F64 bounds and radii. */
public class RRectF64 private constructor(rectF64: RectF64, public val topLeft: CornerRadiiF64, public val topRight: CornerRadiiF64, public val bottomRight: CornerRadiiF64, public val bottomLeft: CornerRadiiF64) {
    private val rectSnapshotF64: RectF64 = rectF64.copyF64()
    internal val leftF64: Double get() = rectSnapshotF64.left
    internal val topF64: Double get() = rectSnapshotF64.top
    internal val rightF64: Double get() = rectSnapshotF64.right
    internal val bottomF64: Double get() = rectSnapshotF64.bottom
    public fun copyRectF64(): RectF64 = rectSnapshotF64.copyF64()
    public fun isFinite(): Boolean = rectSnapshotF64.isFinite() && topLeft.isFinite() && topRight.isFinite() && bottomRight.isFinite() && bottomLeft.isFinite()
    public fun copyF64(): RRectF64 = RRectF64(copyRectF64(), topLeft, topRight, bottomRight, bottomLeft)
    public companion object {
        public fun of(rect: RectF64, topLeft: CornerRadiiF64 = CornerRadiiF64.Zero, topRight: CornerRadiiF64 = CornerRadiiF64.Zero, bottomRight: CornerRadiiF64 = CornerRadiiF64.Zero, bottomLeft: CornerRadiiF64 = CornerRadiiF64.Zero): RRectF64 = RRectF64(rect, topLeft, topRight, bottomRight, bottomLeft)
        public fun of(rect: RectF64, radiusF64: Double): RRectF64 { val r = CornerRadiiF64.of(radiusF64); return RRectF64(rect, r, r, r, r) }
    }
}
