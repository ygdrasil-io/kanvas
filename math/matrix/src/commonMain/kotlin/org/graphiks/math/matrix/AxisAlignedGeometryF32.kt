package org.graphiks.math.matrix

import kotlin.math.abs
import org.graphiks.math.geometry.CornerRadiiF32
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.geometry.RRectF32
import org.graphiks.math.geometry.RectF32

/** Maps an axis-aligned rectangle under a scale-plus-translation transform. */
public fun Matrix3x3F32.mapAxisAlignedRect(rect: RectF32): RectF32 {
    require(isScaleTranslate()) { "mapAxisAlignedRect requires a scale-translate matrix" }
    val topLeft = transform(Point2F32(rect.left, rect.top))
    val bottomRight = transform(Point2F32(rect.right, rect.bottom))
    return RectF32.ofLTRB(
        minOf(topLeft.x, bottomRight.x),
        minOf(topLeft.y, bottomRight.y),
        maxOf(topLeft.x, bottomRight.x),
        maxOf(topLeft.y, bottomRight.y),
    )
}

/** Maps an axis-aligned rounded rectangle while preserving its per-corner radii. */
public fun RRectF32.mapAxisAligned(matrix: Matrix3x3F32): RRectF32 {
    require(matrix.isScaleTranslate()) { "mapAxisAligned requires a scale-translate matrix" }
    fun CornerRadiiF32.map(): CornerRadiiF32 = CornerRadiiF32.of(abs(x * matrix.sx), abs(y * matrix.sy))
    fun sourceCorner(deviceLeft: Boolean, deviceTop: Boolean): CornerRadiiF32 {
        val sourceLeft = if (matrix.sx < 0f) !deviceLeft else deviceLeft
        val sourceTop = if (matrix.sy < 0f) !deviceTop else deviceTop
        return when {
            sourceLeft && sourceTop -> topLeft
            !sourceLeft && sourceTop -> topRight
            !sourceLeft -> bottomRight
            else -> bottomLeft
        }
    }
    return RRectF32.of(
        rect = matrix.mapAxisAlignedRect(rect),
        topLeft = sourceCorner(deviceLeft = true, deviceTop = true).map(),
        topRight = sourceCorner(deviceLeft = false, deviceTop = true).map(),
        bottomRight = sourceCorner(deviceLeft = false, deviceTop = false).map(),
        bottomLeft = sourceCorner(deviceLeft = true, deviceTop = false).map(),
    )
}
