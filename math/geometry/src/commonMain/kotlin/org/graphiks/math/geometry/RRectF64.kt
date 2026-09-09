package org.graphiks.math.geometry

import kotlin.math.min

/** F64 elliptical corner radii. */
public data class CornerRadiiF64(public val xF64: Double, public val yF64: Double) {
    public fun isFinite(): Boolean = xF64.isFinite() && yF64.isFinite()
    public companion object { public val Zero: CornerRadiiF64 = CornerRadiiF64(0.0, 0.0); public fun of(radiusF64: Double): CornerRadiiF64 = CornerRadiiF64(radiusF64, radiusF64); public fun of(xF64: Double, yF64: Double): CornerRadiiF64 = CornerRadiiF64(xF64, yF64) }
}

/** Axis-aligned rounded rectangle snapshot with F64 bounds and radii. */
public class RRectF64 private constructor(
    rectF64: RectF64,
    public val topLeft: CornerRadiiF64,
    public val topRight: CornerRadiiF64,
    public val bottomRight: CornerRadiiF64,
    public val bottomLeft: CornerRadiiF64,
    private val hasSkiaNormalizedRadiiF64: Boolean,
) {
    private val rectSnapshotF64: RectF64 = rectF64.copyF64()
    internal val leftF64: Double get() = rectSnapshotF64.left
    internal val topF64: Double get() = rectSnapshotF64.top
    internal val rightF64: Double get() = rectSnapshotF64.right
    internal val bottomF64: Double get() = rectSnapshotF64.bottom
    public fun copyRectF64(): RectF64 = rectSnapshotF64.copyF64()
    public fun isFinite(): Boolean = rectSnapshotF64.isFinite() && topLeft.isFinite() && topRight.isFinite() && bottomRight.isFinite() && bottomLeft.isFinite()
    public fun copyF64(): RRectF64 = RRectF64(
        copyRectF64(), topLeft, topRight, bottomRight, bottomLeft, hasSkiaNormalizedRadiiF64,
    )

    /**
     * Applies the current Skia RRect contract once in F64: clamp negative radii, then uniformly
     * scale every corner so opposing radii fit the bounds.  The result is immutable and marked so
     * subsequent path materialization consumes this exact canonical authority unchanged.
     */
    public fun normalizedForSkiaF64(): RRectF64 {
        if (hasSkiaNormalizedRadiiF64) return this
        if (!isFinite()) return copyF64()
        val boundsF64 = copyRectF64()
        val normalizedTopLeft = topLeft.nonNegativeF64()
        val normalizedTopRight = topRight.nonNegativeF64()
        val normalizedBottomRight = bottomRight.nonNegativeF64()
        val normalizedBottomLeft = bottomLeft.nonNegativeF64()
        val widthF64 = (boundsF64.right - boundsF64.left).coerceAtLeast(0.0)
        val heightF64 = (boundsF64.bottom - boundsF64.top).coerceAtLeast(0.0)
        val scaleF64 = min(
            1.0,
            min(
                ratioOrOneForSkiaRRectF64(widthF64, normalizedTopLeft.xF64 + normalizedTopRight.xF64),
                min(
                    ratioOrOneForSkiaRRectF64(widthF64, normalizedBottomLeft.xF64 + normalizedBottomRight.xF64),
                    min(
                        ratioOrOneForSkiaRRectF64(heightF64, normalizedTopLeft.yF64 + normalizedBottomLeft.yF64),
                        ratioOrOneForSkiaRRectF64(heightF64, normalizedTopRight.yF64 + normalizedBottomRight.yF64),
                    ),
                ),
            ),
        )
        return RRectF64(
            boundsF64,
            normalizedTopLeft.scaledF64(scaleF64),
            normalizedTopRight.scaledF64(scaleF64),
            normalizedBottomRight.scaledF64(scaleF64),
            normalizedBottomLeft.scaledF64(scaleF64),
            hasSkiaNormalizedRadiiF64 = true,
        )
    }
    public companion object {
        public fun of(rect: RectF64, topLeft: CornerRadiiF64 = CornerRadiiF64.Zero, topRight: CornerRadiiF64 = CornerRadiiF64.Zero, bottomRight: CornerRadiiF64 = CornerRadiiF64.Zero, bottomLeft: CornerRadiiF64 = CornerRadiiF64.Zero): RRectF64 = RRectF64(rect, topLeft, topRight, bottomRight, bottomLeft, hasSkiaNormalizedRadiiF64 = false)
        public fun of(rect: RectF64, radiusF64: Double): RRectF64 { val r = CornerRadiiF64.of(radiusF64); return RRectF64(rect, r, r, r, r, hasSkiaNormalizedRadiiF64 = false) }
    }
}

private fun CornerRadiiF64.nonNegativeF64(): CornerRadiiF64 = CornerRadiiF64.of(
    xF64.coerceAtLeast(0.0), yF64.coerceAtLeast(0.0),
)

private fun CornerRadiiF64.scaledF64(scaleF64: Double): CornerRadiiF64 = CornerRadiiF64.of(
    xF64 * scaleF64, yF64 * scaleF64,
)

private fun ratioOrOneForSkiaRRectF64(limitF64: Double, sumF64: Double): Double =
    if (sumF64 > limitF64 && sumF64 > 0.0) limitF64 / sumF64 else 1.0
