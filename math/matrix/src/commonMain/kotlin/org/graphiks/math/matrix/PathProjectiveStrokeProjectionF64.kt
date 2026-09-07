package org.graphiks.math.matrix

import kotlin.math.sqrt
import org.graphiks.math.geometry.PathStrokeOutlineIntervalF64
import org.graphiks.math.geometry.PathStrokeProjectionF64
import org.graphiks.math.geometry.PathStrokeProjectionIntervalResultF64
import org.graphiks.math.geometry.PathStrokeProjectionPointResultF64
import org.graphiks.math.geometry.Point2F64

/** Returns a pure projective point/interval authority; geometry remains responsible for subdivision. */
public fun Matrix3x3F64.toPathStrokeProjectionF64(): PathStrokeProjectionF64 =
    MatrixPathStrokeProjectionF64(this)

private class MatrixPathStrokeProjectionF64(
    private val matrixF64: Matrix3x3F64,
) : PathStrokeProjectionF64 {
    override fun projectPointF64(pointF64: Point2F64): PathStrokeProjectionPointResultF64 {
        if (!matrixF64.isFinite() || !pointF64.isFinite()) return PathStrokeProjectionPointResultF64.NonFinite
        val homogeneousF64 = matrixF64.projectHomogeneousPointF64(pointF64) ?: return PathStrokeProjectionPointResultF64.NonFinite
        return projectFiniteHomogeneousPointF64(homogeneousF64)
            ?.let(PathStrokeProjectionPointResultF64::Ready)
            ?: PathStrokeProjectionPointResultF64.NonFinite
    }

    override fun certifyOutlineIntervalF64(
        intervalF64: PathStrokeOutlineIntervalF64,
    ): PathStrokeProjectionIntervalResultF64 {
        if (!matrixF64.isFinite() || !intervalF64.sourceSagittaUpperBoundF64.isFinite() ||
            intervalF64.sourceSagittaUpperBoundF64 < 0.0
        ) {
            return PathStrokeProjectionIntervalResultF64.NonFinite
        }
        val boundsF64 = intervalF64.boundsF64
        val wIntervalF64 = when (
            val resultF64 = matrixF64.projectiveWIntervalForBoundsF64(
                boundsF64.leftF64,
                boundsF64.topF64,
                boundsF64.rightF64,
                boundsF64.bottomF64,
            )
        ) {
            is PathProjectiveIntervalResultF64.Ready -> resultF64.intervalF64
            PathProjectiveIntervalResultF64.NonFinite -> return PathStrokeProjectionIntervalResultF64.NonFinite
        }
        if (wIntervalF64.containsZeroF64()) return PathStrokeProjectionIntervalResultF64.HorizonCrossing

        val xyIntervalsF64 = matrixF64.projectiveXYIntervalForBoundsF64(
            boundsF64.leftF64,
            boundsF64.topF64,
            boundsF64.rightF64,
            boundsF64.bottomF64,
        ) ?: return PathStrokeProjectionIntervalResultF64.NonFinite
        val minimumAbsWF64 = wIntervalF64.minimumAbsoluteValueF64()
        if (!minimumAbsWF64.isFinite() || minimumAbsWF64 <= 0.0) {
            return PathStrokeProjectionIntervalResultF64.Unbounded
        }
        val maximumAbsWF64 = wIntervalF64.maximumAbsoluteValueF64()
        val maximumAbsXF64 = xyIntervalsF64.xIntervalF64.maximumAbsoluteValueF64()
        val maximumAbsYF64 = xyIntervalsF64.yIntervalF64.maximumAbsoluteValueF64()
        val denominatorF64 = minimumAbsWF64 * minimumAbsWF64
        if (!maximumAbsWF64.isFinite() || !maximumAbsXF64.isFinite() || !maximumAbsYF64.isFinite() ||
            !denominatorF64.isFinite() || denominatorF64 <= 0.0
        ) {
            return PathStrokeProjectionIntervalResultF64.Unbounded
        }
        val derivativeXXF64 = (kotlin.math.abs(matrixF64.sxF64) * maximumAbsWF64 +
            kotlin.math.abs(matrixF64.persp0F64) * maximumAbsXF64) / denominatorF64
        val derivativeXYF64 = (kotlin.math.abs(matrixF64.kxF64) * maximumAbsWF64 +
            kotlin.math.abs(matrixF64.persp1F64) * maximumAbsXF64) / denominatorF64
        val derivativeYXF64 = (kotlin.math.abs(matrixF64.kyF64) * maximumAbsWF64 +
            kotlin.math.abs(matrixF64.persp0F64) * maximumAbsYF64) / denominatorF64
        val derivativeYYF64 = (kotlin.math.abs(matrixF64.syF64) * maximumAbsWF64 +
            kotlin.math.abs(matrixF64.persp1F64) * maximumAbsYF64) / denominatorF64
        val maximumDerivativeF64 = sqrt(
            derivativeXXF64 * derivativeXXF64 + derivativeXYF64 * derivativeXYF64 +
                derivativeYXF64 * derivativeYXF64 + derivativeYYF64 * derivativeYYF64,
        )
        val deviceSagittaF64 = maximumDerivativeF64 * intervalF64.sourceSagittaUpperBoundF64
        return if (maximumDerivativeF64.isFinite() && deviceSagittaF64.isFinite() && deviceSagittaF64 >= 0.0) {
            PathStrokeProjectionIntervalResultF64.Bounded(nextUpProjectiveF64(deviceSagittaF64))
        } else {
            PathStrokeProjectionIntervalResultF64.Unbounded
        }
    }
}

internal data class ProjectiveHomogeneousPointF64(
    val xF64: Double,
    val yF64: Double,
    val wF64: Double,
)

internal fun Matrix3x3F64.projectHomogeneousPointF64(pointF64: Point2F64): ProjectiveHomogeneousPointF64? {
    return projectHomogeneousCoordinatesF64(pointF64.x, pointF64.y, 1.0)
}

/** Applies the matrix to a homogeneous source control without performing the projective divide. */
internal fun Matrix3x3F64.projectHomogeneousCoordinatesF64(
    xF64: Double,
    yF64: Double,
    wF64: Double,
): ProjectiveHomogeneousPointF64? {
    val transformedXF64 = sxF64 * xF64 + kxF64 * yF64 + txF64 * wF64
    val transformedYF64 = kyF64 * xF64 + syF64 * yF64 + tyF64 * wF64
    val transformedWF64 = persp0F64 * xF64 + persp1F64 * yF64 + persp2F64 * wF64
    return ProjectiveHomogeneousPointF64(transformedXF64, transformedYF64, transformedWF64)
        .takeIf { it.xF64.isFinite() && it.yF64.isFinite() && it.wF64.isFinite() }
}

internal fun projectFiniteHomogeneousPointF64(pointF64: ProjectiveHomogeneousPointF64): Point2F64? {
    if (!pointF64.xF64.isFinite() || !pointF64.yF64.isFinite() || !pointF64.wF64.isFinite() || pointF64.wF64 == 0.0) {
        return null
    }
    return Point2F64(pointF64.xF64 / pointF64.wF64, pointF64.yF64 / pointF64.wF64)
        .takeIf(Point2F64::isFinite)
}
