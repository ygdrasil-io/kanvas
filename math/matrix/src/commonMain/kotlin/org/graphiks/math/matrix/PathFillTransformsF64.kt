package org.graphiks.math.matrix

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import org.graphiks.math.geometry.PathF32
import org.graphiks.math.geometry.PathFillInputF64
import org.graphiks.math.geometry.PathFillSegmentF64
import org.graphiks.math.geometry.Point2F64
import org.graphiks.math.vector.Vector2F64

/** Maps a path into an immutable F64 device-space fill input. */
public fun Matrix3x3F32.mapPathFillInputF64(path: PathF32): PathFillInputF64 {
    requireFiniteMatrix()
    require(!hasPerspective()) { "mapPathFillInputF64 requires an affine Matrix3x3F32" }

    val source = PathFillInputF64.fromPathF32(path)
    val mapped = source.map { segment ->
        when (segment) {
            is PathFillSegmentF64.MoveTo -> PathFillSegmentF64.MoveTo(mapPointF64(segment.point))
            is PathFillSegmentF64.LineTo -> PathFillSegmentF64.LineTo(mapPointF64(segment.point))
            is PathFillSegmentF64.QuadTo -> PathFillSegmentF64.QuadTo(
                control = mapPointF64(segment.control),
                point = mapPointF64(segment.point),
            )

            is PathFillSegmentF64.CubicTo -> PathFillSegmentF64.CubicTo(
                control1 = mapPointF64(segment.control1),
                control2 = mapPointF64(segment.control2),
                point = mapPointF64(segment.point),
            )

            is PathFillSegmentF64.ArcTo -> {
                val metadata = transformArcMetadataF64(
                    radius = segment.radius,
                    xAxisRotationDegreesF64 = segment.xAxisRotationDegreesF64,
                    sweep = segment.sweep,
                )
                PathFillSegmentF64.ArcTo(
                    radius = metadata.radius,
                    xAxisRotationDegreesF64 = metadata.xAxisRotationDegreesF64,
                    largeArc = segment.largeArc,
                    sweep = metadata.sweep,
                    point = mapPointF64(segment.point),
                )
            }

            PathFillSegmentF64.Close -> PathFillSegmentF64.Close
        }
    }
    return PathFillInputF64.of(source.fillRule, mapped)
}

private fun Matrix3x3F32.requireFiniteMatrix() {
    require(
        sx.isFinite() && kx.isFinite() && tx.isFinite() &&
            ky.isFinite() && sy.isFinite() && ty.isFinite() &&
            persp0.isFinite() && persp1.isFinite() && persp2.isFinite(),
    ) { "mapPathFillInputF64 requires finite Matrix3x3F32 coefficients" }
}

private fun Matrix3x3F32.mapPointF64(point: Point2F64): Point2F64 {
    val mapped = Point2F64(
        exactF64(sx) * point.x + exactF64(kx) * point.y + exactF64(tx),
        exactF64(ky) * point.x + exactF64(sy) * point.y + exactF64(ty),
    )
    require(mapped.isFinite()) { "mapPathFillInputF64 produced a non-finite coordinate" }
    return mapped
}

private data class TransformedArcMetadataF64(
    val radius: Vector2F64,
    val xAxisRotationDegreesF64: Double,
    val sweep: Boolean,
)

private fun Matrix3x3F32.transformArcMetadataF64(
    radius: Vector2F64,
    xAxisRotationDegreesF64: Double,
    sweep: Boolean,
): TransformedArcMetadataF64 {
    require(radius.isFinite() && xAxisRotationDegreesF64.isFinite()) {
        "mapPathFillInputF64 requires finite arc metadata"
    }
    if (sx == 1f && kx == 0f && ky == 0f && sy == 1f) {
        return TransformedArcMetadataF64(
            radius = Vector2F64(abs(radius.x), abs(radius.y)),
            xAxisRotationDegreesF64 = xAxisRotationDegreesF64,
            sweep = sweep,
        )
    }

    val angle = xAxisRotationDegreesF64 * PI / 180.0
    val cosAngle = cos(angle)
    val sinAngle = sin(angle)
    val radiusX = abs(radius.x)
    val radiusY = abs(radius.y)

    val xAxisX = cosAngle * radiusX
    val xAxisY = sinAngle * radiusX
    val yAxisX = -sinAngle * radiusY
    val yAxisY = cosAngle * radiusY

    val transformedXAxisX = exactF64(sx) * xAxisX + exactF64(kx) * xAxisY
    val transformedXAxisY = exactF64(ky) * xAxisX + exactF64(sy) * xAxisY
    val transformedYAxisX = exactF64(sx) * yAxisX + exactF64(kx) * yAxisY
    val transformedYAxisY = exactF64(ky) * yAxisX + exactF64(sy) * yAxisY

    val xAxisLengthSquared =
        transformedXAxisX * transformedXAxisX + transformedXAxisY * transformedXAxisY
    val yAxisLengthSquared =
        transformedYAxisX * transformedYAxisX + transformedYAxisY * transformedYAxisY
    val axisDot =
        transformedXAxisX * transformedYAxisX + transformedXAxisY * transformedYAxisY
    val dotTolerance = 1e-6 * sqrt(xAxisLengthSquared * yAxisLengthSquared)

    val transformed = if (abs(axisDot) <= dotTolerance) {
        val transformedRadiusX = sqrt(xAxisLengthSquared)
        val transformedRadiusY = sqrt(yAxisLengthSquared)
        val transformedRotation = when {
            transformedRadiusX > 0.0 -> atan2(transformedXAxisY, transformedXAxisX) * 180.0 / PI
            transformedRadiusY > 0.0 -> atan2(-transformedYAxisX, transformedYAxisY) * 180.0 / PI
            else -> xAxisRotationDegreesF64
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
            xAxisRotationDegreesF64
        }
        Triple(sqrt(major), sqrt(minor), transformedRotation)
    }

    require(
        transformed.first.isFinite() && transformed.second.isFinite() && transformed.third.isFinite(),
    ) { "mapPathFillInputF64 produced non-finite arc metadata" }
    return TransformedArcMetadataF64(
        radius = Vector2F64(transformed.first, transformed.second),
        xAxisRotationDegreesF64 = transformed.third,
        sweep = if (exactF64(sx) * exactF64(sy) - exactF64(kx) * exactF64(ky) < 0.0) !sweep else sweep,
    )
}

/** Restores the F32 IEEE-754 payload at the Kotlin/JS boundary before F64 work begins. */
private fun exactF64(valueF32: Float): Double = Float.fromBits(valueF32.toRawBits()).toDouble()
