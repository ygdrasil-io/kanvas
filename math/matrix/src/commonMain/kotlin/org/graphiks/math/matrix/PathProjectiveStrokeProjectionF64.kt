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
        val wIntervalF64 = when (val certificateF64 = correlatedOutlineWIntervalF64(matrixF64, intervalF64)) {
            is CorrelatedOutlineWResultF64.Ready -> certificateF64.intervalF64
            CorrelatedOutlineWResultF64.Horizon -> return PathStrokeProjectionIntervalResultF64.HorizonCrossing
            CorrelatedOutlineWResultF64.NonFinite -> return PathStrokeProjectionIntervalResultF64.NonFinite
            CorrelatedOutlineWResultF64.Unbounded -> return PathStrokeProjectionIntervalResultF64.Unbounded
        }

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

private sealed interface CorrelatedOutlineWResultF64 {
    data class Ready(val intervalF64: PathProjectiveIntervalF64) : CorrelatedOutlineWResultF64

    data object Horizon : CorrelatedOutlineWResultF64

    data object NonFinite : CorrelatedOutlineWResultF64

    data object Unbounded : CorrelatedOutlineWResultF64
}

/**
 * Retains the source-parameter correlation that an AABB loses.  The outline's sagitta certificate
 * bounds its displacement from the endpoint chord, so a linear W functional is sign-separated
 * whenever the endpoint chord has more margin than that displacement can consume.
 */
private fun correlatedOutlineWIntervalF64(
    matrixF64: Matrix3x3F64,
    intervalF64: PathStrokeOutlineIntervalF64,
): CorrelatedOutlineWResultF64 {
    val startF64 = intervalF64.primitiveF64.pointAtF64(intervalF64.startParameterF64)
    val endF64 = intervalF64.primitiveF64.pointAtF64(intervalF64.endParameterF64)
    if (!startF64.isFinite() || !endF64.isFinite()) return CorrelatedOutlineWResultF64.NonFinite
    val startHomogeneousF64 = matrixF64.projectHomogeneousPointF64(startF64) ?: return CorrelatedOutlineWResultF64.NonFinite
    val endHomogeneousF64 = matrixF64.projectHomogeneousPointF64(endF64) ?: return CorrelatedOutlineWResultF64.NonFinite
    val startWExpansionF64 = ProjectiveCompensatedF64(
        startHomogeneousF64.wF64,
        startHomogeneousF64.wResidualF64,
        startHomogeneousF64.wUncertaintyF64,
        startHomogeneousF64.wLowerTailF64,
        startHomogeneousF64.wUpperTailF64,
        startHomogeneousF64.wSubnormalUnitsF64,
    )
    val endWExpansionF64 = ProjectiveCompensatedF64(
        endHomogeneousF64.wF64,
        endHomogeneousF64.wResidualF64,
        endHomogeneousF64.wUncertaintyF64,
        endHomogeneousF64.wLowerTailF64,
        endHomogeneousF64.wUpperTailF64,
        endHomogeneousF64.wSubnormalUnitsF64,
    )
    val startWIntervalF64 = projectiveCompensatedIntervalF64(startWExpansionF64)
        ?: return CorrelatedOutlineWResultF64.NonFinite
    val endWIntervalF64 = projectiveCompensatedIntervalF64(endWExpansionF64)
        ?: return CorrelatedOutlineWResultF64.NonFinite
    val startSignF64 = projectiveWSignF64(startWExpansionF64)
    val endSignF64 = projectiveWSignF64(endWExpansionF64)
    if (startSignF64 == ProjectiveWSignF64.Root || endSignF64 == ProjectiveWSignF64.Root ||
        (startSignF64 == ProjectiveWSignF64.Positive && endSignF64 == ProjectiveWSignF64.Negative) ||
        (startSignF64 == ProjectiveWSignF64.Negative && endSignF64 == ProjectiveWSignF64.Positive)
    ) return CorrelatedOutlineWResultF64.Horizon
    if (startSignF64 != ProjectiveWSignF64.Positive && startSignF64 != ProjectiveWSignF64.Negative ||
        endSignF64 != ProjectiveWSignF64.Positive && endSignF64 != ProjectiveWSignF64.Negative
    ) {
        return CorrelatedOutlineWResultF64.Unbounded
    }
    val gradientLengthF64 = projectiveHypotF64(matrixF64.persp0F64, matrixF64.persp1F64)
    val maximumWDeviationF64 = nextUpProjectiveF64(gradientLengthF64 * intervalF64.sourceSagittaUpperBoundF64)
    if (!gradientLengthF64.isFinite() || !maximumWDeviationF64.isFinite()) {
        return CorrelatedOutlineWResultF64.Unbounded
    }
    val minimumEndpointWF64 = minOf(startWIntervalF64.minimumF64, endWIntervalF64.minimumF64)
    val maximumEndpointWF64 = maxOf(startWIntervalF64.maximumF64, endWIntervalF64.maximumF64)
    val minimumWF64 = nextDownProjectiveF64(minimumEndpointWF64 - maximumWDeviationF64)
    val maximumWF64 = nextUpProjectiveF64(maximumEndpointWF64 + maximumWDeviationF64)
    if (!minimumWF64.isFinite() || !maximumWF64.isFinite()) return CorrelatedOutlineWResultF64.Unbounded
    if (minimumWF64 <= 0.0 && maximumWF64 >= 0.0) return CorrelatedOutlineWResultF64.Unbounded
    return CorrelatedOutlineWResultF64.Ready(PathProjectiveIntervalF64(minimumWF64, maximumWF64))
}

internal data class ProjectiveHomogeneousPointF64(
    val xF64: Double,
    val yF64: Double,
    val wF64: Double,
    /** Exact low-order W contribution retained across de Casteljau subdivision. */
    val wResidualF64: Double = 0.0,
    /** Directed absolute enclosure for W terms below [wResidualF64]. */
    val wUncertaintyF64: Double = 0.0,
    /** Inclusive directed W tail lower bound. */
    val wLowerTailF64: Double = -wUncertaintyF64,
    /** Inclusive directed W tail upper bound. */
    val wUpperTailF64: Double = wUncertaintyF64,
    /** Exact W tail in units of [Double.MIN_VALUE]. */
    val wSubnormalUnitsF64: Double = 0.0,
)

internal fun Matrix3x3F64.projectHomogeneousPointF64(pointF64: Point2F64): ProjectiveHomogeneousPointF64? {
    val transformedF64 = projectHomogeneousCoordinatesF64(pointF64.x, pointF64.y, 1.0) ?: return null
    val wExpansionF64 = projectiveCompensatedCombinationF64(
        firstCoefficientF64 = persp0F64,
        secondCoefficientF64 = persp1F64,
        translationF64 = persp2F64,
        pointF64 = pointF64,
    ) ?: return null
    return transformedF64.copy(
        wF64 = wExpansionF64.leadingF64,
        wResidualF64 = wExpansionF64.residualF64,
        wUncertaintyF64 = wExpansionF64.uncertaintyF64,
        wLowerTailF64 = wExpansionF64.lowerTailF64,
        wUpperTailF64 = wExpansionF64.upperTailF64,
        wSubnormalUnitsF64 = wExpansionF64.subnormalUnitsF64,
    )
}

/** Applies the matrix to a homogeneous source control without performing the projective divide. */
internal fun Matrix3x3F64.projectHomogeneousCoordinatesF64(
    xF64: Double,
    yF64: Double,
    wF64: Double,
): ProjectiveHomogeneousPointF64? {
    val transformedXF64 = sxF64 * xF64 + kxF64 * yF64 + txF64 * wF64
    val transformedYF64 = kyF64 * xF64 + syF64 * yF64 + tyF64 * wF64
    val transformedWExpansionF64 = projectiveCompensatedHomogeneousCombinationF64(
        firstCoefficientF64 = persp0F64,
        firstValueF64 = xF64,
        secondCoefficientF64 = persp1F64,
        secondValueF64 = yF64,
        thirdCoefficientF64 = persp2F64,
        thirdValueF64 = wF64,
    ) ?: return null
    return ProjectiveHomogeneousPointF64(
        transformedXF64,
        transformedYF64,
        transformedWExpansionF64.leadingF64,
        transformedWExpansionF64.residualF64,
        transformedWExpansionF64.uncertaintyF64,
        transformedWExpansionF64.lowerTailF64,
        transformedWExpansionF64.upperTailF64,
        transformedWExpansionF64.subnormalUnitsF64,
    ).takeIf {
        it.xF64.isFinite() && it.yF64.isFinite() && it.wF64.isFinite() &&
            it.wResidualF64.isFinite() && it.wUncertaintyF64.isFinite() &&
            it.wLowerTailF64.isFinite() && it.wUpperTailF64.isFinite()
    }
}

internal fun projectFiniteHomogeneousPointF64(pointF64: ProjectiveHomogeneousPointF64): Point2F64? {
    val wExpansionF64 = ProjectiveCompensatedF64(
        pointF64.wF64,
        pointF64.wResidualF64,
        pointF64.wUncertaintyF64,
        pointF64.wLowerTailF64,
        pointF64.wUpperTailF64,
        pointF64.wSubnormalUnitsF64,
    )
    val effectiveWF64 = projectiveCompensatedValueF64(wExpansionF64)
    if (!pointF64.xF64.isFinite() || !pointF64.yF64.isFinite() || !effectiveWF64.isFinite() ||
        projectiveCompensatedSignF64(wExpansionF64) == 0
    ) {
        return null
    }
    return Point2F64(pointF64.xF64 / effectiveWF64, pointF64.yF64 / effectiveWF64)
        .takeIf(Point2F64::isFinite)
}

/** Scaled hypotenuse avoids both overflow and underflow in `sqrt(x*x + y*y)`. */
private fun projectiveHypotF64(xF64: Double, yF64: Double): Double {
    val maximumF64 = maxOf(kotlin.math.abs(xF64), kotlin.math.abs(yF64))
    if (!maximumF64.isFinite()) return Double.NaN
    if (maximumF64 == 0.0) return 0.0
    val minimumF64 = minOf(kotlin.math.abs(xF64), kotlin.math.abs(yF64))
    val ratioF64 = minimumF64 / maximumF64
    return nextUpProjectiveF64(maximumF64 * sqrt(1.0 + ratioF64 * ratioF64))
}
