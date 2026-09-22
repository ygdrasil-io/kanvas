package org.graphiks.math.matrix

import org.graphiks.math.geometry.Point2I32
import org.graphiks.math.geometry.RectF64
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.geometry.RRectF32
import org.graphiks.math.geometry.RectI32
import org.graphiks.math.geometry.roundOutToRectI32OrNull

/** Immutable local/device/layer mapping sealed before a layer graph is published. */
public class LayerMappingF64 private constructor(
    private val localToDeviceF64: Matrix3x3F64,
    private val deviceToLayerF64: Matrix3x3F64,
    private val localToLayerF64: Matrix3x3F64,
    private val layerOriginDeviceI32: Point2I32,
) {
    public fun copyLocalToDeviceF64(): Matrix3x3F64 = localToDeviceF64.copy()
    public fun copyDeviceToLayerF64(): Matrix3x3F64 = deviceToLayerF64.copy()
    public fun copyLocalToLayerF64(): Matrix3x3F64 = localToLayerF64.copy()
    public fun copyLayerOriginDeviceI32(): Point2I32 = Point2I32(layerOriginDeviceI32.x, layerOriginDeviceI32.y)

    /** Projects a sealed device-space texel rectangle into checked layer texels. */
    public fun mapDeviceRectToLayerI32OrNull(boundsDeviceI32: RectI32): RectI32? =
        deviceToLayerF64.mapRectBoundsF64OrNull(RectF64(
            boundsDeviceI32.left.toDouble(),
            boundsDeviceI32.top.toDouble(),
            boundsDeviceI32.right.toDouble(),
            boundsDeviceI32.bottom.toDouble(),
        ))?.roundOutToRectI32OrNull()

    /** W6b's target-local spelling of the same sealed device-to-layer mapping. */
    public fun mapDeviceRectToTargetI32OrNull(boundsDeviceI32: RectI32): RectI32? =
        mapDeviceRectToLayerI32OrNull(boundsDeviceI32)

    /**
     * Translation of an already raster-admitted analytic shape; no second projection.
     * The native ABI has frozen this shape as F32, so rebasing uses the same F32 subtraction.
     */
    public fun mapDeviceRectToLayerF32OrNull(boundsDeviceF32: RectF32): RectF32? {
        fun edge(valueF32: Float, originI32: Int): Float? {
            val resultF32 = valueF32 - originI32.toFloat()
            return resultF32.takeIf(Float::isFinite)
        }
        return RectF32(
            edge(boundsDeviceF32.left, layerOriginDeviceI32.x) ?: return null,
            edge(boundsDeviceF32.top, layerOriginDeviceI32.y) ?: return null,
            edge(boundsDeviceF32.right, layerOriginDeviceI32.x) ?: return null,
            edge(boundsDeviceF32.bottom, layerOriginDeviceI32.y) ?: return null,
        ).takeUnless { it.isEmpty }
    }

    public fun mapDeviceRRectToLayerF32OrNull(shapeDeviceF32: RRectF32): RRectF32? =
        mapDeviceRectToLayerF32OrNull(shapeDeviceF32.rect)?.let { rect ->
            RRectF32.of(rect, shapeDeviceF32.topLeft, shapeDeviceF32.topRight,
                shapeDeviceF32.bottomRight, shapeDeviceF32.bottomLeft)
        }

    public companion object {
        public fun ofOrNull(
            localToDeviceF64: Matrix3x3F64,
            layerOriginDeviceI32: Point2I32,
        ): LayerMappingF64? {
            if (!localToDeviceF64.isFinite()) return null
            // A layer mapping is a sealed reversible coordinate contract.  A reflection has a
            // finite inverse and is therefore valid; a collapsed transform is not.
            if (localToDeviceF64.invertFiniteOrNull() == null) return null
            val deviceToLayerF64 = Matrix3x3F64(
                txF64 = -layerOriginDeviceI32.x.toDouble(),
                tyF64 = -layerOriginDeviceI32.y.toDouble(),
            )
            val localToLayerF64 = deviceToLayerF64.timesCheckedOrNull(localToDeviceF64) ?: return null
            return LayerMappingF64(
                localToDeviceF64.copy(),
                deviceToLayerF64,
                localToLayerF64,
                Point2I32(layerOriginDeviceI32.x, layerOriginDeviceI32.y),
            )
        }
    }
}

/** Returns finite device bounds, refusing every projective rectangle crossing W=0. */
public fun Matrix3x3F64.mapRectBoundsF64OrNull(boundsF64: RectF64): RectF64? {
    if (!isFinite() || !boundsF64.isFinite() || boundsF64.isEmpty) return null
    data class HomogeneousPointF64(val xF64: Double, val yF64: Double, val wF64: Double)
    fun map(xF64: Double, yF64: Double): HomogeneousPointF64? {
        val x = sxF64 * xF64 + kxF64 * yF64 + txF64
        val y = kyF64 * xF64 + syF64 * yF64 + tyF64
        val w = persp0F64 * xF64 + persp1F64 * yF64 + persp2F64
        return HomogeneousPointF64(x, y, w).takeIf { it.xF64.isFinite() && it.yF64.isFinite() && it.wF64.isFinite() }
    }
    val points = listOf(
        map(boundsF64.left, boundsF64.top),
        map(boundsF64.right, boundsF64.top),
        map(boundsF64.right, boundsF64.bottom),
        map(boundsF64.left, boundsF64.bottom),
    ).map { it ?: return null }
    if (points.any { it.wF64 == 0.0 }) return null
    val positiveW = points.first().wF64 > 0.0
    if (points.any { (it.wF64 > 0.0) != positiveW }) return null
    val divided = points.map { point ->
        val x = point.xF64 / point.wF64
        val y = point.yF64 / point.wF64
        if (!x.isFinite() || !y.isFinite()) return null
        x to y
    }
    return RectF64(
        divided.minOf { it.first },
        divided.minOf { it.second },
        divided.maxOf { it.first },
        divided.maxOf { it.second },
    ).takeUnless { it.isEmpty }
}

private fun Matrix3x3F64.timesCheckedOrNull(other: Matrix3x3F64): Matrix3x3F64? {
    val left = doubleArrayOf(sxF64, kxF64, txF64, kyF64, syF64, tyF64, persp0F64, persp1F64, persp2F64)
    val right = doubleArrayOf(other.sxF64, other.kxF64, other.txF64, other.kyF64, other.syF64, other.tyF64,
        other.persp0F64, other.persp1F64, other.persp2F64)
    val values = DoubleArray(9) { indexI32 ->
        val rowI32 = indexI32 / 3
        val columnI32 = indexI32 % 3
        val valueF64 = left[rowI32 * 3] * right[columnI32] +
            left[rowI32 * 3 + 1] * right[columnI32 + 3] +
            left[rowI32 * 3 + 2] * right[columnI32 + 6]
        if (valueF64.isFinite()) valueF64 else return null
    }
    return Matrix3x3F64(
        values[0], values[1], values[2], values[3], values[4], values[5], values[6], values[7], values[8],
    )
}
