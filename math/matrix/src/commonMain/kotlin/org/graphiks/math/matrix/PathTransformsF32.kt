package org.graphiks.math.matrix

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import org.graphiks.math.geometry.PathBuilder
import org.graphiks.math.geometry.PathF32
import org.graphiks.math.geometry.PathSegmentF32
import org.graphiks.math.vector.Vector2F32

/** Maps every command in an immutable path through this matrix. */
public fun Matrix3x3F32.map(path: PathF32): PathF32 {
    require(!hasPerspective() || path.none { segment ->
        segment is PathSegmentF32.QuadTo ||
            segment is PathSegmentF32.CubicTo ||
            segment is PathSegmentF32.ArcTo
    }) {
        "Projective Matrix3x3F32 cannot map paths containing curves"
    }
    val builder = PathBuilder(path.fillRule)
    path.forEach { segment ->
        when (segment) {
            is PathSegmentF32.MoveTo -> {
                val point = transform(segment.point)
                builder.moveTo(point.x, point.y)
            }
            is PathSegmentF32.LineTo -> {
                val point = transform(segment.point)
                builder.lineTo(point.x, point.y)
            }
            is PathSegmentF32.QuadTo -> {
                val control = transform(segment.control)
                val point = transform(segment.point)
                builder.quadTo(control.x, control.y, point.x, point.y)
            }
            is PathSegmentF32.CubicTo -> {
                val control1 = transform(segment.control1)
                val control2 = transform(segment.control2)
                val point = transform(segment.point)
                builder.cubicTo(
                    control1.x,
                    control1.y,
                    control2.x,
                    control2.y,
                    point.x,
                    point.y,
                )
            }
            is PathSegmentF32.ArcTo -> {
                val metadata = transformArcMetadata(
                    radius = segment.radius,
                    xAxisRotation = segment.xAxisRotation,
                    sweep = segment.sweep,
                )
                val point = transform(segment.point)
                builder.arcTo(
                    radiusX = metadata.radiusX,
                    radiusY = metadata.radiusY,
                    xAxisRotation = metadata.xAxisRotation,
                    largeArc = segment.largeArc,
                    sweep = metadata.sweep,
                    x = point.x,
                    y = point.y,
                )
            }
            PathSegmentF32.Close -> builder.close()
        }
    }
    return builder.build()
}

/** Returns a transformed immutable copy of this path. */
public fun PathF32.transformedBy(matrix: Matrix3x3F32): PathF32 = matrix.map(this)

private data class TransformedArcMetadata(
    val radiusX: Float,
    val radiusY: Float,
    val xAxisRotation: Float,
    val sweep: Boolean,
)

private fun Matrix3x3F32.transformArcMetadata(
    radius: Vector2F32,
    xAxisRotation: Float,
    sweep: Boolean,
): TransformedArcMetadata {
    if (sx == 1f && kx == 0f && ky == 0f && sy == 1f) {
        return TransformedArcMetadata(
            radiusX = abs(radius.x),
            radiusY = abs(radius.y),
            xAxisRotation = xAxisRotation,
            sweep = sweep,
        )
    }
    val angle = xAxisRotation.toDouble() * PI / 180.0
    val cosAngle = cos(angle)
    val sinAngle = sin(angle)
    val radiusX = abs(radius.x.toDouble())
    val radiusY = abs(radius.y.toDouble())

    val xAxisX = cosAngle * radiusX
    val xAxisY = sinAngle * radiusX
    val yAxisX = -sinAngle * radiusY
    val yAxisY = cosAngle * radiusY

    val transformedXAxisX = sx * xAxisX + kx * xAxisY
    val transformedXAxisY = ky * xAxisX + sy * xAxisY
    val transformedYAxisX = sx * yAxisX + kx * yAxisY
    val transformedYAxisY = ky * yAxisX + sy * yAxisY

    val xAxisLengthSquared =
        transformedXAxisX * transformedXAxisX + transformedXAxisY * transformedXAxisY
    val yAxisLengthSquared =
        transformedYAxisX * transformedYAxisX + transformedYAxisY * transformedYAxisY
    val axisDot =
        transformedXAxisX * transformedYAxisX + transformedXAxisY * transformedYAxisY
    val dotTolerance = 1e-6 * sqrt(xAxisLengthSquared * yAxisLengthSquared)

    val transformed: Triple<Double, Double, Double> = if (abs(axisDot) <= dotTolerance) {
        val transformedRadiusX = sqrt(xAxisLengthSquared)
        val transformedRadiusY = sqrt(yAxisLengthSquared)
        val transformedRotation = when {
            transformedRadiusX > 0.0 -> atan2(transformedXAxisY, transformedXAxisX) * 180.0 / PI
            transformedRadiusY > 0.0 -> atan2(-transformedYAxisX, transformedYAxisY) * 180.0 / PI
            else -> xAxisRotation.toDouble()
        }
        Triple(transformedRadiusX, transformedRadiusY, transformedRotation)
    } else {
        val covarianceXX =
            transformedXAxisX * transformedXAxisX + transformedYAxisX * transformedYAxisX
        val covarianceXY =
            transformedXAxisX * transformedXAxisY + transformedYAxisX * transformedYAxisY
        val covarianceYY =
            transformedXAxisY * transformedXAxisY + transformedYAxisY * transformedYAxisY
        val trace = covarianceXX + covarianceYY
        val diff = covarianceXX - covarianceYY
        val root = sqrt(diff * diff + 4.0 * covarianceXY * covarianceXY)
        val major = ((trace + root) / 2.0).coerceAtLeast(0.0)
        val minor = ((trace - root) / 2.0).coerceAtLeast(0.0)
        val transformedRotation = if (major > 0.0) {
            0.5 * atan2(2.0 * covarianceXY, diff) * 180.0 / PI
        } else {
            xAxisRotation.toDouble()
        }
        Triple(sqrt(major), sqrt(minor), transformedRotation)
    }

    return TransformedArcMetadata(
        radiusX = transformed.first.toFloat(),
        radiusY = transformed.second.toFloat(),
        xAxisRotation = transformed.third.toFloat(),
        sweep = if (det2x2() < 0f) !sweep else sweep,
    )
}
