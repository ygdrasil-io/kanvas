package org.graphiks.math.matrix

import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.geometry.PointSquaresF32
import org.graphiks.math.geometry.RectI32

/** The same device-square geometry is consumed by recorded and pre-publication point lanes. */
public fun Matrix3x3F32.preparePointSquaresF32OrNull(pointsF32: List<Point2F32>, widthF32: Float,
    targetI32: RectI32): PointSquaresF32? {
    if (pointsF32.size !in 1..Int.MAX_VALUE / 8 || !widthF32.isFinite() || widthF32 < 0f ||
        persp0 != 0f || persp1 != 0f || persp2 != 1f) return null
    if (widthF32 == 0f) return PointSquaresF32.hairlineDevicePointsF32OrNull(pointsF32.map(::transform), targetI32)
    val halfF32 = widthF32 * .5f
    val vertices = pointsF32.flatMap { point ->
        listOf(Point2F32(point.x - halfF32, point.y - halfF32), Point2F32(point.x + halfF32, point.y - halfF32),
            Point2F32(point.x + halfF32, point.y + halfF32), Point2F32(point.x - halfF32, point.y + halfF32))
            .map(::transform).flatMap { listOf(it.x, it.y) }
    }.toFloatArray()
    return PointSquaresF32.fromDeviceQuadsF32OrNull(vertices, targetI32)
}
