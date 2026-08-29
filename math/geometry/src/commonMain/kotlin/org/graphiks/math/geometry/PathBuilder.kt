package org.graphiks.math.geometry

import org.graphiks.math.vector.Vector2F32
import kotlin.math.min

/** Incrementally constructs immutable [PathF32] values. */
public class PathBuilder(
    private val fillRule: FillRule = FillRule.WINDING,
) {
    private val segments = mutableListOf<PathSegmentF32>()

    public fun moveTo(x: Float, y: Float): PathBuilder = append(PathSegmentF32.MoveTo(Point2F32(x, y)))

    public fun lineTo(x: Float, y: Float): PathBuilder = append(PathSegmentF32.LineTo(Point2F32(x, y)))

    public fun quadTo(controlX: Float, controlY: Float, x: Float, y: Float): PathBuilder =
        append(PathSegmentF32.QuadTo(Point2F32(controlX, controlY), Point2F32(x, y)))

    public fun cubicTo(
        control1X: Float,
        control1Y: Float,
        control2X: Float,
        control2Y: Float,
        x: Float,
        y: Float,
    ): PathBuilder = append(
        PathSegmentF32.CubicTo(
            Point2F32(control1X, control1Y),
            Point2F32(control2X, control2Y),
            Point2F32(x, y),
        ),
    )

    public fun arcTo(
        radiusX: Float,
        radiusY: Float,
        xAxisRotation: Float,
        largeArc: Boolean,
        sweep: Boolean,
        x: Float,
        y: Float,
    ): PathBuilder = append(
        PathSegmentF32.ArcTo(
            Vector2F32(radiusX, radiusY),
            xAxisRotation,
            largeArc,
            sweep,
            Point2F32(x, y),
        ),
    )

    public fun close(): PathBuilder = append(PathSegmentF32.Close)

    /** Appends a clockwise, closed rectangle contour. */
    public fun addRect(rect: RectF32): PathBuilder = apply {
        moveTo(rect.left, rect.top)
        lineTo(rect.right, rect.top)
        lineTo(rect.right, rect.bottom)
        lineTo(rect.left, rect.bottom)
        close()
    }

    /** Appends a clockwise, closed oval contour using four cubic Bézier curves. */
    public fun addOval(rect: RectF32): PathBuilder = apply {
        val centerX = rect.centerX()
        val centerY = rect.centerY()
        val radiusX = rect.width() / 2f
        val radiusY = rect.height() / 2f
        val controlScale = 0.5522847498f

        moveTo(centerX + radiusX, centerY)
        cubicTo(
            centerX + radiusX, centerY - controlScale * radiusY,
            centerX + controlScale * radiusX, centerY - radiusY,
            centerX, centerY - radiusY,
        )
        cubicTo(
            centerX - controlScale * radiusX, centerY - radiusY,
            centerX - radiusX, centerY - controlScale * radiusY,
            centerX - radiusX, centerY,
        )
        cubicTo(
            centerX - radiusX, centerY + controlScale * radiusY,
            centerX - controlScale * radiusX, centerY + radiusY,
            centerX, centerY + radiusY,
        )
        cubicTo(
            centerX + controlScale * radiusX, centerY + radiusY,
            centerX + radiusX, centerY + controlScale * radiusY,
            centerX + radiusX, centerY,
        )
        close()
    }

    /** Appends a clockwise, closed rounded-rectangle contour. */
    public fun addRRect(rrect: RRectF32): PathBuilder = apply {
        val rect = rrect.rect
        val (topLeft, topRight, bottomRight, bottomLeft) = normalizedRadii(rrect)

        moveTo(rect.left + topLeft.x, rect.top)
        lineTo(rect.right - topRight.x, rect.top)
        arcTo(topRight.x, topRight.y, 0f, largeArc = false, sweep = true, rect.right, rect.top + topRight.y)
        lineTo(rect.right, rect.bottom - bottomRight.y)
        arcTo(bottomRight.x, bottomRight.y, 0f, largeArc = false, sweep = true, rect.right - bottomRight.x, rect.bottom)
        lineTo(rect.left + bottomLeft.x, rect.bottom)
        arcTo(bottomLeft.x, bottomLeft.y, 0f, largeArc = false, sweep = true, rect.left, rect.bottom - bottomLeft.y)
        lineTo(rect.left, rect.top + topLeft.y)
        arcTo(topLeft.x, topLeft.y, 0f, largeArc = false, sweep = true, rect.left + topLeft.x, rect.top)
        close()
    }

    /** Appends every contour in [path]. */
    public fun addPath(path: PathF32): PathBuilder = apply { segments.addAll(path) }

    /** Returns an immutable snapshot of the commands appended so far. */
    public fun build(): PathF32 = PathF32(fillRule, segments)

    private fun append(segment: PathSegmentF32): PathBuilder = apply { segments += segment }

    private fun normalizedRadii(rrect: RRectF32): Array<CornerRadiiF32> {
        val width = rrect.rect.width().coerceAtLeast(0f)
        val height = rrect.rect.height().coerceAtLeast(0f)
        val topLeft = rrect.topLeft.nonNegative()
        val topRight = rrect.topRight.nonNegative()
        val bottomRight = rrect.bottomRight.nonNegative()
        val bottomLeft = rrect.bottomLeft.nonNegative()
        val scale = min(
            1f,
            min(
                ratioOrOne(width, topLeft.x + topRight.x),
                min(
                    ratioOrOne(width, bottomLeft.x + bottomRight.x),
                    min(
                        ratioOrOne(height, topLeft.y + bottomLeft.y),
                        ratioOrOne(height, topRight.y + bottomRight.y),
                    ),
                ),
            ),
        )
        return arrayOf(
            topLeft.scaled(scale),
            topRight.scaled(scale),
            bottomRight.scaled(scale),
            bottomLeft.scaled(scale),
        )
    }

    private fun ratioOrOne(limit: Float, sum: Float): Float =
        if (sum > limit && sum > 0f) limit / sum else 1f

    private fun CornerRadiiF32.nonNegative(): CornerRadiiF32 =
        CornerRadiiF32.of(x.coerceAtLeast(0f), y.coerceAtLeast(0f))

    private fun CornerRadiiF32.scaled(scale: Float): CornerRadiiF32 =
        CornerRadiiF32.of(x * scale, y * scale)
}
