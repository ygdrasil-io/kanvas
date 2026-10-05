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
        val left = rect.left.toDouble()
        val top = rect.top.toDouble()
        val right = rect.right.toDouble()
        val bottom = rect.bottom.toDouble()
        val centerX = (left + right) * 0.5
        val centerY = (top + bottom) * 0.5
        val radiusX = (right - left) * 0.5
        val radiusY = (bottom - top) * 0.5
        val controlScale = 0.5522847498

        moveTo((centerX + radiusX).toFloat(), centerY.toFloat())
        cubicTo(
            (centerX + radiusX).toFloat(), (centerY + controlScale * radiusY).toFloat(),
            (centerX + controlScale * radiusX).toFloat(), (centerY + radiusY).toFloat(),
            centerX.toFloat(), (centerY + radiusY).toFloat(),
        )
        cubicTo(
            (centerX - controlScale * radiusX).toFloat(), (centerY + radiusY).toFloat(),
            (centerX - radiusX).toFloat(), (centerY + controlScale * radiusY).toFloat(),
            (centerX - radiusX).toFloat(), centerY.toFloat(),
        )
        cubicTo(
            (centerX - radiusX).toFloat(), (centerY - controlScale * radiusY).toFloat(),
            (centerX - controlScale * radiusX).toFloat(), (centerY - radiusY).toFloat(),
            centerX.toFloat(), (centerY - radiusY).toFloat(),
        )
        cubicTo(
            (centerX + controlScale * radiusX).toFloat(), (centerY - radiusY).toFloat(),
            (centerX + radiusX).toFloat(), (centerY - controlScale * radiusY).toFloat(),
            (centerX + radiusX).toFloat(), centerY.toFloat(),
        )
        close()
    }

    /** Appends a clockwise, closed rounded-rectangle contour. */
    public fun addRRect(rrect: RRectF32): PathBuilder = apply {
        val rect = rrect.rect
        val (topLeft, topRight, bottomRight, bottomLeft) = normalizedRadii(rrect)
        val left = rect.left.toDouble()
        val top = rect.top.toDouble()
        val right = rect.right.toDouble()
        val bottom = rect.bottom.toDouble()

        moveTo((left + topLeft.x).toFloat(), top.toFloat())
        lineTo((right - topRight.x).toFloat(), top.toFloat())
        arcTo(topRight.x, topRight.y, 0f, largeArc = false, sweep = true, right.toFloat(), (top + topRight.y).toFloat())
        lineTo(right.toFloat(), (bottom - bottomRight.y).toFloat())
        arcTo(bottomRight.x, bottomRight.y, 0f, largeArc = false, sweep = true, (right - bottomRight.x).toFloat(), bottom.toFloat())
        lineTo((left + bottomLeft.x).toFloat(), bottom.toFloat())
        arcTo(bottomLeft.x, bottomLeft.y, 0f, largeArc = false, sweep = true, left.toFloat(), (bottom - bottomLeft.y).toFloat())
        lineTo(left.toFloat(), (top + topLeft.y).toFloat())
        arcTo(topLeft.x, topLeft.y, 0f, largeArc = false, sweep = true, (left + topLeft.x).toFloat(), top.toFloat())
        close()
    }

    /** Appends every contour in [path]. */
    public fun addPath(path: PathF32): PathBuilder = apply { segments.addAll(path) }

    /** Returns an immutable snapshot of the commands appended so far. */
    public fun build(): PathF32 = PathF32(fillRule, segments)

    private fun append(segment: PathSegmentF32): PathBuilder = apply { segments += segment }

    private fun normalizedRadii(rrect: RRectF32): Array<CornerRadiiF32> {
        val width = (rrect.rect.right.toDouble() - rrect.rect.left.toDouble()).coerceAtLeast(0.0)
        val height = (rrect.rect.bottom.toDouble() - rrect.rect.top.toDouble()).coerceAtLeast(0.0)
        val topLeft = rrect.topLeft.nonNegative()
        val topRight = rrect.topRight.nonNegative()
        val bottomRight = rrect.bottomRight.nonNegative()
        val bottomLeft = rrect.bottomLeft.nonNegative()
        val scale = min(
            1.0,
            min(
                ratioOrOne(width, topLeft.x.toDouble() + topRight.x.toDouble()),
                min(
                    ratioOrOne(width, bottomLeft.x.toDouble() + bottomRight.x.toDouble()),
                    min(
                        ratioOrOne(height, topLeft.y.toDouble() + bottomLeft.y.toDouble()),
                        ratioOrOne(height, topRight.y.toDouble() + bottomRight.y.toDouble()),
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

    private fun ratioOrOne(limit: Double, sum: Double): Double =
        if (sum > limit && sum > 0.0) limit / sum else 1.0

    private fun CornerRadiiF32.nonNegative(): CornerRadiiF32 =
        CornerRadiiF32.of(x.coerceAtLeast(0f), y.coerceAtLeast(0f))

    private fun CornerRadiiF32.scaled(scale: Double): CornerRadiiF32 =
        CornerRadiiF32.of((x.toDouble() * scale).toFloat(), (y.toDouble() * scale).toFloat())
}
