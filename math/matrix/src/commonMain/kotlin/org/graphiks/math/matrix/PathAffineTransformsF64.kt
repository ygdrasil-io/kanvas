package org.graphiks.math.matrix

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import org.graphiks.math.geometry.PathFillInputF64
import org.graphiks.math.geometry.PathFillSegmentF64
import org.graphiks.math.geometry.PathF32
import org.graphiks.math.geometry.PathSegmentF32
import org.graphiks.math.geometry.PathStrokeBoundsF64
import org.graphiks.math.geometry.PathStrokeOutlineIntervalF64
import org.graphiks.math.geometry.PathStrokeProjectionF64
import org.graphiks.math.geometry.PathStrokeProjectionIntervalResultF64
import org.graphiks.math.geometry.PathStrokeProjectionPointResultF64
import org.graphiks.math.geometry.PathStrokeWorkUsageI64
import org.graphiks.math.geometry.Point2F64
import org.graphiks.math.geometry.toPathFillSegmentF64
import org.graphiks.math.vector.Vector2F64

/** Charges transform work before its evaluation or snapshot allocation. */
internal fun interface PathTransformWorkDebitI64 {
    public fun debitBeforeTransformWorkI64(deltaI64: PathStrokeWorkUsageI64)
}

/** Maps a finite affine fill snapshot while charging each command before it is evaluated. */
internal fun Matrix3x3F64.mapAffinePathFillInputF64(
    inputF64: PathFillInputF64,
    debitI64: PathTransformWorkDebitI64,
): PathFillInputF64 {
    require(isFinite() && classifyPathTransform() != PathTransformClass.Perspective) {
        "mapAffinePathFillInputF64 requires finite affine Matrix3x3F64 coefficients"
    }
    debitI64.debitBeforeTransformWorkI64(PathStrokeWorkUsageI64(snapshotByteCountI64 = 16L))
    val mappedSegmentsF64 = ArrayList<PathFillSegmentF64>(inputF64.segmentCountI32)
    inputF64.forEach { segmentF64 ->
        debitI64.debitBeforeTransformWorkI64(
            PathStrokeWorkUsageI64(attemptedGeometryUnitCountI64 = 1L, snapshotByteCountI64 = 64L),
        )
        require(isFinitePathFillSegmentF64(segmentF64)) {
            "mapAffinePathFillInputF64 requires finite path input"
        }
        mappedSegmentsF64 += mapAffinePathFillSegmentF64(segmentF64)
    }
    debitI64.debitBeforeTransformWorkI64(PathStrokeWorkUsageI64(snapshotByteCountI64 = 16L))
    return PathFillInputF64.of(inputF64.fillRule, mappedSegmentsF64)
}

/** Maps an F32 path lazily: each source command is admitted before it is widened or retained. */
internal fun Matrix3x3F64.mapAffinePathF32ToFillInputF64(
    pathF32: PathF32,
    debitI64: PathTransformWorkDebitI64,
): PathFillInputF64 {
    require(isFinite() && classifyPathTransform() != PathTransformClass.Perspective) {
        "mapAffinePathF32ToFillInputF64 requires finite affine Matrix3x3F64 coefficients"
    }
    debitI64.debitBeforeTransformWorkI64(PathStrokeWorkUsageI64(snapshotByteCountI64 = 16L))
    val mappedSegmentsF64 = ArrayList<PathFillSegmentF64>()
    var hasContour = false
    fun debitAndAppend(segmentF32: PathSegmentF32) {
        debitI64.debitBeforeTransformWorkI64(
            PathStrokeWorkUsageI64(attemptedGeometryUnitCountI64 = 1L, snapshotByteCountI64 = 64L),
        )
        mappedSegmentsF64 += mapAffinePathFillSegmentF64(segmentF32.toPathFillSegmentF64())
    }
    pathF32.forEach { segmentF32 ->
        if (segmentF32 !is PathSegmentF32.MoveTo && segmentF32 !is PathSegmentF32.Close && !hasContour) {
            debitAndAppend(PathSegmentF32.MoveTo(org.graphiks.math.geometry.Point2F32.Origin))
            hasContour = true
        }
        debitAndAppend(segmentF32)
        if (segmentF32 is PathSegmentF32.MoveTo) hasContour = true
    }
    debitI64.debitBeforeTransformWorkI64(PathStrokeWorkUsageI64(snapshotByteCountI64 = 16L))
    return PathFillInputF64.of(pathF32.fillRule, mappedSegmentsF64)
}

internal fun Matrix3x3F64.mapAffinePathFillSegmentF64(segmentF64: PathFillSegmentF64): PathFillSegmentF64 =
    when (segmentF64) {
        is PathFillSegmentF64.MoveTo -> PathFillSegmentF64.MoveTo(mapAffinePointF64(segmentF64.point))
        is PathFillSegmentF64.LineTo -> PathFillSegmentF64.LineTo(mapAffinePointF64(segmentF64.point))
        is PathFillSegmentF64.QuadTo -> PathFillSegmentF64.QuadTo(
            control = mapAffinePointF64(segmentF64.control),
            point = mapAffinePointF64(segmentF64.point),
        )

        is PathFillSegmentF64.CubicTo -> PathFillSegmentF64.CubicTo(
            control1 = mapAffinePointF64(segmentF64.control1),
            control2 = mapAffinePointF64(segmentF64.control2),
            point = mapAffinePointF64(segmentF64.point),
        )

        is PathFillSegmentF64.ArcTo -> {
            val metadataF64 = transformAffineArcMetadataF64(
                radiusF64 = segmentF64.radius,
                xAxisRotationDegreesF64 = segmentF64.xAxisRotationDegreesF64,
                sweep = segmentF64.sweep,
            )
            PathFillSegmentF64.ArcTo(
                radius = metadataF64.radiusF64,
                xAxisRotationDegreesF64 = metadataF64.xAxisRotationDegreesF64,
                largeArc = segmentF64.largeArc,
                sweep = metadataF64.sweep,
                point = mapAffinePointF64(segmentF64.point),
            )
        }

        PathFillSegmentF64.Close -> PathFillSegmentF64.Close
    }

private fun Matrix3x3F64.mapAffinePointF64(pointF64: Point2F64): Point2F64 {
    val mappedF64 = Point2F64(
        x = sxF64 * pointF64.x + kxF64 * pointF64.y + txF64,
        y = kyF64 * pointF64.x + syF64 * pointF64.y + tyF64,
    )
    require(mappedF64.isFinite()) { "mapAffinePathFillInputF64 produced a non-finite coordinate" }
    return mappedF64
}

private data class TransformedAffineArcMetadataF64(
    val radiusF64: Vector2F64,
    val xAxisRotationDegreesF64: Double,
    val sweep: Boolean,
)

private fun Matrix3x3F64.transformAffineArcMetadataF64(
    radiusF64: Vector2F64,
    xAxisRotationDegreesF64: Double,
    sweep: Boolean,
): TransformedAffineArcMetadataF64 {
    require(radiusF64.isFinite() && xAxisRotationDegreesF64.isFinite()) {
        "mapAffinePathFillInputF64 requires finite arc metadata"
    }
    if (sxF64 == 1.0 && kxF64 == 0.0 && kyF64 == 0.0 && syF64 == 1.0) {
        return TransformedAffineArcMetadataF64(
            radiusF64 = Vector2F64(abs(radiusF64.x), abs(radiusF64.y)),
            xAxisRotationDegreesF64 = xAxisRotationDegreesF64,
            sweep = sweep,
        )
    }

    val angleF64 = xAxisRotationDegreesF64 * PI / 180.0
    val radiusXF64 = abs(radiusF64.x)
    val radiusYF64 = abs(radiusF64.y)
    val xAxisXF64 = cos(angleF64) * radiusXF64
    val xAxisYF64 = sin(angleF64) * radiusXF64
    val yAxisXF64 = -sin(angleF64) * radiusYF64
    val yAxisYF64 = cos(angleF64) * radiusYF64
    val transformedXAxisXF64 = sxF64 * xAxisXF64 + kxF64 * xAxisYF64
    val transformedXAxisYF64 = kyF64 * xAxisXF64 + syF64 * xAxisYF64
    val transformedYAxisXF64 = sxF64 * yAxisXF64 + kxF64 * yAxisYF64
    val transformedYAxisYF64 = kyF64 * yAxisXF64 + syF64 * yAxisYF64
    val transformedAxesDotF64 =
        transformedXAxisXF64 * transformedYAxisXF64 + transformedXAxisYF64 * transformedYAxisYF64
    if (classifyPathTransform() != PathTransformClass.GeneralAffine && transformedAxesDotF64 == 0.0) {
        val transformedRadiusXF64 = sqrt(
            transformedXAxisXF64 * transformedXAxisXF64 + transformedXAxisYF64 * transformedXAxisYF64,
        )
        val transformedRadiusYF64 = sqrt(
            transformedYAxisXF64 * transformedYAxisXF64 + transformedYAxisYF64 * transformedYAxisYF64,
        )
        val transformedRotationDegreesF64 = when {
            transformedRadiusXF64 > 0.0 -> atan2(transformedXAxisYF64, transformedXAxisXF64) * 180.0 / PI
            transformedRadiusYF64 > 0.0 -> atan2(-transformedYAxisXF64, transformedYAxisYF64) * 180.0 / PI
            else -> xAxisRotationDegreesF64
        }
        require(
            transformedRadiusXF64.isFinite() && transformedRadiusYF64.isFinite() &&
                transformedRotationDegreesF64.isFinite(),
        ) { "mapAffinePathFillInputF64 produced non-finite arc metadata" }
        return TransformedAffineArcMetadataF64(
            radiusF64 = Vector2F64(transformedRadiusXF64, transformedRadiusYF64),
            xAxisRotationDegreesF64 = transformedRotationDegreesF64,
            sweep = if (sxF64 * syF64 - kxF64 * kyF64 < 0.0) !sweep else sweep,
        )
    }
    val axesScaleF64 = max(
        max(abs(transformedXAxisXF64), abs(transformedXAxisYF64)),
        max(abs(transformedYAxisXF64), abs(transformedYAxisYF64)),
    )
    if (axesScaleF64 == 0.0) {
        return TransformedAffineArcMetadataF64(
            radiusF64 = Vector2F64(0.0, 0.0),
            xAxisRotationDegreesF64 = xAxisRotationDegreesF64,
            sweep = if (sxF64 * syF64 - kxF64 * kyF64 < 0.0) !sweep else sweep,
        )
    }
    val normalizedXAxisXF64 = transformedXAxisXF64 / axesScaleF64
    val normalizedXAxisYF64 = transformedXAxisYF64 / axesScaleF64
    val normalizedYAxisXF64 = transformedYAxisXF64 / axesScaleF64
    val normalizedYAxisYF64 = transformedYAxisYF64 / axesScaleF64
    val covarianceXXF64 = normalizedXAxisXF64 * normalizedXAxisXF64 + normalizedYAxisXF64 * normalizedYAxisXF64
    val covarianceXYF64 = normalizedXAxisXF64 * normalizedXAxisYF64 + normalizedYAxisXF64 * normalizedYAxisYF64
    val covarianceYYF64 = normalizedXAxisYF64 * normalizedXAxisYF64 + normalizedYAxisYF64 * normalizedYAxisYF64
    val traceF64 = covarianceXXF64 + covarianceYYF64
    val differenceF64 = covarianceXXF64 - covarianceYYF64
    val majorSquaredF64 = ((traceF64 + sqrt(differenceF64 * differenceF64 + 4.0 * covarianceXYF64 * covarianceXYF64)) / 2.0)
        .coerceAtLeast(0.0)
    val determinantF64 = normalizedXAxisXF64 * normalizedYAxisYF64 - normalizedXAxisYF64 * normalizedYAxisXF64
    val minorSquaredF64 = if (majorSquaredF64 == 0.0) 0.0 else {
        (determinantF64 * determinantF64 / majorSquaredF64).coerceAtLeast(0.0)
    }
    val transformedRotationDegreesF64 = if (majorSquaredF64 > 0.0) {
        0.5 * atan2(2.0 * covarianceXYF64, differenceF64) * 180.0 / PI
    } else {
        xAxisRotationDegreesF64
    }
    require(
        majorSquaredF64.isFinite() && minorSquaredF64.isFinite() && transformedRotationDegreesF64.isFinite(),
    ) { "mapAffinePathFillInputF64 produced non-finite arc metadata" }
    val majorRadiusF64 = axesScaleF64 * sqrt(majorSquaredF64)
    val minorRadiusF64 = axesScaleF64 * sqrt(minorSquaredF64)
    require(majorRadiusF64.isFinite() && minorRadiusF64.isFinite()) {
        "mapAffinePathFillInputF64 produced non-finite arc metadata"
    }
    return TransformedAffineArcMetadataF64(
        radiusF64 = Vector2F64(majorRadiusF64, minorRadiusF64),
        xAxisRotationDegreesF64 = transformedRotationDegreesF64,
        sweep = if (sxF64 * syF64 - kxF64 * kyF64 < 0.0) !sweep else sweep,
    )
}

internal class GeneralAffinePathStrokeProjectionF64 private constructor(
    private val matrixF64: Matrix3x3F64,
) : PathStrokeProjectionF64 {
    private val maximumMagnificationF64: Double = matrixF64.maximumAffineMagnificationF64()

    override fun projectPointF64(pointF64: Point2F64): PathStrokeProjectionPointResultF64 =
        if (!pointF64.isFinite()) {
            PathStrokeProjectionPointResultF64.NonFinite
        } else {
            try {
                PathStrokeProjectionPointResultF64.Ready(matrixF64.mapAffinePointF64(pointF64))
            } catch (_: IllegalArgumentException) {
                PathStrokeProjectionPointResultF64.NonFinite
            }
        }

    override fun certifyOutlineIntervalF64(
        intervalF64: PathStrokeOutlineIntervalF64,
    ): PathStrokeProjectionIntervalResultF64 {
        if (!intervalF64.boundsF64.isFiniteForAffineProjectionF64() ||
            !intervalF64.sourceSagittaUpperBoundF64.isFinite() || intervalF64.sourceSagittaUpperBoundF64 < 0.0
        ) {
            return PathStrokeProjectionIntervalResultF64.NonFinite
        }
        val deviceSagittaF64 = maximumMagnificationF64 * intervalF64.sourceSagittaUpperBoundF64
        return if (deviceSagittaF64.isFinite() && deviceSagittaF64 >= 0.0) {
            PathStrokeProjectionIntervalResultF64.Bounded(deviceSagittaF64)
        } else {
            PathStrokeProjectionIntervalResultF64.NonFinite
        }
    }

    internal companion object {
        internal fun of(matrixF64: Matrix3x3F64): GeneralAffinePathStrokeProjectionF64 {
            require(matrixF64.isFinite() && matrixF64.classifyPathTransform() != PathTransformClass.Perspective) {
                "GeneralAffinePathStrokeProjectionF64 requires a finite affine Matrix3x3F64"
            }
            return GeneralAffinePathStrokeProjectionF64(matrixF64)
        }
    }
}

internal fun Matrix3x3F64.toAffinePathStrokeProjectionF64(): PathStrokeProjectionF64 =
    GeneralAffinePathStrokeProjectionF64.of(this)

private fun Matrix3x3F64.maximumAffineMagnificationF64(): Double {
    val scaleF64 = max(max(abs(sxF64), abs(kxF64)), max(abs(kyF64), abs(syF64)))
    if (scaleF64 == 0.0) return 0.0
    val normalizedSxF64 = sxF64 / scaleF64
    val normalizedKxF64 = kxF64 / scaleF64
    val normalizedKyF64 = kyF64 / scaleF64
    val normalizedSyF64 = syF64 / scaleF64
    val firstColumnLengthSquaredF64 = normalizedSxF64 * normalizedSxF64 + normalizedKyF64 * normalizedKyF64
    val secondColumnLengthSquaredF64 = normalizedKxF64 * normalizedKxF64 + normalizedSyF64 * normalizedSyF64
    val columnDotF64 = normalizedSxF64 * normalizedKxF64 + normalizedKyF64 * normalizedSyF64
    val traceF64 = firstColumnLengthSquaredF64 + secondColumnLengthSquaredF64
    val differenceF64 = firstColumnLengthSquaredF64 - secondColumnLengthSquaredF64
    val largestEigenvalueF64 = (traceF64 + sqrt(differenceF64 * differenceF64 + 4.0 * columnDotF64 * columnDotF64)) / 2.0
    return scaleF64 * sqrt(largestEigenvalueF64.coerceAtLeast(0.0))
}

private fun PathStrokeBoundsF64.isFiniteForAffineProjectionF64(): Boolean =
    leftF64.isFinite() && topF64.isFinite() && rightF64.isFinite() && bottomF64.isFinite()

private fun isFinitePathFillSegmentF64(segmentF64: PathFillSegmentF64): Boolean = when (segmentF64) {
    is PathFillSegmentF64.MoveTo -> segmentF64.point.isFinite()
    is PathFillSegmentF64.LineTo -> segmentF64.point.isFinite()
    is PathFillSegmentF64.QuadTo -> segmentF64.control.isFinite() && segmentF64.point.isFinite()
    is PathFillSegmentF64.CubicTo ->
        segmentF64.control1.isFinite() && segmentF64.control2.isFinite() && segmentF64.point.isFinite()
    is PathFillSegmentF64.ArcTo ->
        segmentF64.radius.isFinite() && segmentF64.xAxisRotationDegreesF64.isFinite() && segmentF64.point.isFinite()
    PathFillSegmentF64.Close -> true
}
