package org.graphiks.math.geometry

import kotlin.math.ceil
import kotlin.math.floor

/** Immutable device quadrilaterals for the bounded square-point raster domain. */
public class PointSquaresF32 private constructor(verticesF32: FloatArray, boundsI32: RectI32) {
    private val vertices = verticesF32.copyOf()
    private val bounds = boundsI32.copy()
    public val pointCountI32: Int get() = vertices.size / 8
    public fun copyVerticesF32(): FloatArray = vertices.copyOf()
    public fun copyBoundsI32(): RectI32 = bounds.copy()
    public fun copyContourStartsI32(): IntArray = IntArray(pointCountI32) { it * 4 }
    public fun copyIndicesI32(): IntArray = IntArray(pointCountI32 * 6) {
        it / 6 * 4 + intArrayOf(0, 1, 2, 0, 2, 3)[it % 6]
    }

    public fun relativeToOriginI32OrNull(originI32: Point2I32): PointSquaresF32? {
        val localized = FloatArray(vertices.size)
        vertices.indices.forEach { index ->
            val valueF32 = vertices[index] - if (index % 2 == 0) originI32.x.toFloat() else originI32.y.toFloat()
            if (!valueF32.isFinite()) return null
            localized[index] = valueF32
        }
        fun edge(value: Int, offset: Int): Int? = (value.toLong() - offset.toLong())
            .takeIf { it in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong() }?.toInt()
        return PointSquaresF32(localized, RectI32(edge(bounds.left, originI32.x) ?: return null,
            edge(bounds.top, originI32.y) ?: return null, edge(bounds.right, originI32.x) ?: return null,
            edge(bounds.bottom, originI32.y) ?: return null))
    }

    public companion object {
        /** Bounds are rounded outward and clipped; vertices keep their exact raster shape. */
        public fun fromDeviceQuadsF32OrNull(verticesF32: FloatArray, targetI32: RectI32): PointSquaresF32? {
            if (verticesF32.isEmpty() || verticesF32.size % 8 != 0 ||
                verticesF32.any { !it.isFinite() } || targetI32.isEmpty) return null
            val x = verticesF32.indices.filter { it % 2 == 0 }.map { verticesF32[it].toDouble() }
            val y = verticesF32.indices.filter { it % 2 == 1 }.map { verticesF32[it].toDouble() }
            val left = maxOf(targetI32.left.toDouble(), floor(x.min()))
            val top = maxOf(targetI32.top.toDouble(), floor(y.min()))
            val right = minOf(targetI32.right.toDouble(), ceil(x.max()))
            val bottom = minOf(targetI32.bottom.toDouble(), ceil(y.max()))
            if (left >= right || top >= bottom) return null
            return PointSquaresF32(verticesF32, RectI32(left.toInt(), top.toInt(), right.toInt(), bottom.toInt()))
        }

        /** Hairlines cover exactly the device pixel containing each transformed point. */
        public fun hairlineDevicePointsF32OrNull(pointsF32: List<Point2F32>, targetI32: RectI32): PointSquaresF32? {
            if (pointsF32.size !in 1..Int.MAX_VALUE / 8 || pointsF32.any { !it.x.isFinite() || !it.y.isFinite() }) return null
            val quads = pointsF32.mapNotNull { point ->
                val leftF64 = floor(point.x.toDouble())
                val topF64 = floor(point.y.toDouble())
                if (leftF64 < targetI32.left || topF64 < targetI32.top || leftF64 >= targetI32.right || topF64 >= targetI32.bottom)
                    null
                else floatArrayOf(leftF64.toFloat(), topF64.toFloat(), (leftF64 + 1.0).toFloat(), topF64.toFloat(),
                    (leftF64 + 1.0).toFloat(), (topF64 + 1.0).toFloat(), leftF64.toFloat(), (topF64 + 1.0).toFloat())
            }
            return fromDeviceQuadsF32OrNull(quads.flatMap { it.toList() }.toFloatArray(), targetI32)
        }
    }
}
