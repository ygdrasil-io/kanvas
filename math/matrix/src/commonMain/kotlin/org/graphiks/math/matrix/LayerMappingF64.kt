package org.graphiks.math.matrix

import org.graphiks.math.geometry.Point2I32
import org.graphiks.math.geometry.RectF64

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

    public companion object {
        public fun ofOrNull(
            localToDeviceF64: Matrix3x3F64,
            layerOriginDeviceI32: Point2I32,
        ): LayerMappingF64? {
            if (!localToDeviceF64.isFinite()) return null
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
