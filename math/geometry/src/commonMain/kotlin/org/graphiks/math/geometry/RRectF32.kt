package org.graphiks.math.geometry

/**
 * Elliptical radii of one rounded-rectangle corner.
 *
 * The x and y radii are distinct geometric quantities: they describe the
 * semi-axes of the corner ellipse, not a translation vector or an extent.
 */
public data class CornerRadiiF32(
    public val x: Float,
    public val y: Float,
) {
    public companion object {
        /** Zero radii produce a square corner. */
        public val Zero: CornerRadiiF32 = CornerRadiiF32(0f, 0f)

        /** Creates elliptical corner radii. */
        public fun of(x: Float, y: Float): CornerRadiiF32 = CornerRadiiF32(x, y)

        /** Creates circular corner radii. */
        public fun of(radius: Float): CornerRadiiF32 = CornerRadiiF32(radius, radius)
    }
}

/**
 * Axis-aligned rounded rectangle with independently elliptical corner radii.
 *
 * The bounds are represented by [RectF32]. Corner radii follow clockwise
 * order from the top-left corner.
 */
public data class RRectF32(
    public val rect: RectF32,
    public val topLeft: CornerRadiiF32 = CornerRadiiF32.Zero,
    public val topRight: CornerRadiiF32 = CornerRadiiF32.Zero,
    public val bottomRight: CornerRadiiF32 = CornerRadiiF32.Zero,
    public val bottomLeft: CornerRadiiF32 = CornerRadiiF32.Zero,
) {
    public companion object {
        /** Creates a rounded rectangle with per-corner radii. */
        public fun of(
            rect: RectF32,
            topLeft: CornerRadiiF32 = CornerRadiiF32.Zero,
            topRight: CornerRadiiF32 = CornerRadiiF32.Zero,
            bottomRight: CornerRadiiF32 = CornerRadiiF32.Zero,
            bottomLeft: CornerRadiiF32 = CornerRadiiF32.Zero,
        ): RRectF32 = RRectF32(rect, topLeft, topRight, bottomRight, bottomLeft)

        /** Creates a rounded rectangle with the same circular radius at every corner. */
        public fun of(rect: RectF32, radius: Float): RRectF32 {
            val radii = CornerRadiiF32.of(radius)
            return RRectF32(rect, radii, radii, radii, radii)
        }
    }
}
