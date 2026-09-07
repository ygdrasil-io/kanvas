package org.graphiks.math.matrix

import kotlin.math.abs
import org.graphiks.math.geometry.PathF32
import org.graphiks.math.geometry.PathStrokeDrawMode
import org.graphiks.math.geometry.PathStrokeOutlineIntervalF64
import org.graphiks.math.geometry.PathStrokePolicyF64
import org.graphiks.math.geometry.PathStrokePreparationResult
import org.graphiks.math.geometry.PathStrokeProjectionF64
import org.graphiks.math.geometry.PathStrokeProjectionIntervalResultF64
import org.graphiks.math.geometry.PathStrokeProjectionPointResultF64
import org.graphiks.math.geometry.PathStrokeStyleF64
import org.graphiks.math.geometry.PathStrokeWorkUsageI64
import org.graphiks.math.geometry.Point2F64

/**
 * Finite affine projection for the axis-aligned W4d transform lane.
 *
 * This adapter deliberately has no projective cases: its certificate can only be bounded or
 * non-finite, never horizon-crossing or unbounded.
 */
public class AxisAlignedPathStrokeProjectionF64 private constructor(
    private val sxF64: Double,
    private val syF64: Double,
    private val txF64: Double,
    private val tyF64: Double,
) : PathStrokeProjectionF64 {
    public val maximumMagnificationF64: Double = maxOf(abs(sxF64), abs(syF64))

    override fun projectPointF64(pointF64: Point2F64): PathStrokeProjectionPointResultF64 {
        if (!pointF64.isFinite()) return PathStrokeProjectionPointResultF64.NonFinite
        val projectedF64 = Point2F64(
            canonicalAxisAlignedCoordinateF64(sxF64 * pointF64.x + txF64),
            canonicalAxisAlignedCoordinateF64(syF64 * pointF64.y + tyF64),
        )
        return if (projectedF64.isFinite()) PathStrokeProjectionPointResultF64.Ready(projectedF64)
        else PathStrokeProjectionPointResultF64.NonFinite
    }

    override fun certifyOutlineIntervalF64(
        intervalF64: PathStrokeOutlineIntervalF64,
    ): PathStrokeProjectionIntervalResultF64 {
        if (!intervalF64.boundsF64.isFiniteForAxisAlignedProjectionF64() ||
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

    public companion object {
        public fun of(matrixF32: Matrix3x3F32): AxisAlignedPathStrokeProjectionF64 {
            val coefficientsF64 = AxisAlignedMatrixCoefficientsF64.from(matrixF32)
            require(coefficientsF64.kxF64 == 0.0 && coefficientsF64.kyF64 == 0.0) {
                "preparePathStrokeGeometryF32 requires an axis-aligned Matrix3x3F32"
            }
            require(
                coefficientsF64.persp0F64 == 0.0 && coefficientsF64.persp1F64 == 0.0 &&
                    coefficientsF64.persp2F64 == 1.0,
            ) { "preparePathStrokeGeometryF32 requires an affine Matrix3x3F32" }
            return AxisAlignedPathStrokeProjectionF64(
                sxF64 = coefficientsF64.sxF64,
                syF64 = coefficientsF64.syF64,
                txF64 = coefficientsF64.txF64,
                tyF64 = coefficientsF64.tyF64,
            )
        }
    }
}

/** Widens the matrix before dispatching shared transformed stroke preparation. */
public fun Matrix3x3F32.preparePathStrokeGeometryF32(
    path: PathF32,
    styleF64: PathStrokeStyleF64,
    mode: PathStrokeDrawMode,
    policyF64: PathStrokePolicyF64 = PathStrokePolicyF64(),
    frameWorkUsageBeforeI64: PathStrokeWorkUsageI64 = PathStrokeWorkUsageI64(),
): PathStrokePreparationResult = toMatrix3x3F64().preparePathStrokeGeometryF32(
    path = path,
    styleF64 = styleF64,
    mode = mode,
    policyF64 = policyF64,
    frameWorkUsageBeforeI64 = frameWorkUsageBeforeI64,
)

private data class AxisAlignedMatrixCoefficientsF64(
    val sxF64: Double,
    val kxF64: Double,
    val txF64: Double,
    val kyF64: Double,
    val syF64: Double,
    val tyF64: Double,
    val persp0F64: Double,
    val persp1F64: Double,
    val persp2F64: Double,
) {
    companion object {
        fun from(matrixF32: Matrix3x3F32): AxisAlignedMatrixCoefficientsF64 = AxisAlignedMatrixCoefficientsF64(
            sxF64 = canonicalAxisAlignedCoefficientF64(matrixF32.sx),
            kxF64 = canonicalAxisAlignedCoefficientF64(matrixF32.kx),
            txF64 = canonicalAxisAlignedCoefficientF64(matrixF32.tx),
            kyF64 = canonicalAxisAlignedCoefficientF64(matrixF32.ky),
            syF64 = canonicalAxisAlignedCoefficientF64(matrixF32.sy),
            tyF64 = canonicalAxisAlignedCoefficientF64(matrixF32.ty),
            persp0F64 = canonicalAxisAlignedCoefficientF64(matrixF32.persp0),
            persp1F64 = canonicalAxisAlignedCoefficientF64(matrixF32.persp1),
            persp2F64 = canonicalAxisAlignedCoefficientF64(matrixF32.persp2),
        )
    }
}

private fun canonicalAxisAlignedCoefficientF64(valueF32: Float): Double {
    val valueF64 = Float.fromBits(valueF32.toRawBits()).toDouble()
    require(valueF64.isFinite()) { "preparePathStrokeGeometryF32 requires finite Matrix3x3F32 coefficients" }
    return canonicalAxisAlignedCoordinateF64(valueF64)
}

private fun canonicalAxisAlignedCoordinateF64(valueF64: Double): Double = if (valueF64 == 0.0) 0.0 else valueF64

private fun org.graphiks.math.geometry.PathStrokeBoundsF64.isFiniteForAxisAlignedProjectionF64(): Boolean =
    leftF64.isFinite() && topF64.isFinite() && rightF64.isFinite() && bottomF64.isFinite()
