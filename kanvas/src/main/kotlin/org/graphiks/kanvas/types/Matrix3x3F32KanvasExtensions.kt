package org.graphiks.kanvas.types

import kotlin.math.abs
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.matrix.Matrix3x3F32

fun Matrix3x3F32.mapPoint(point: Point): Point {
    val transformed = transform(Point2F32(point.x, point.y))
    return Point(transformed.x, transformed.y)
}

/** Maps an axis-aligned rectangle under a scale-plus-translation transform. */
fun Matrix3x3F32.mapAxisAlignedRect(rect: Rect): Rect {
    require(isScaleTranslate()) { "mapAxisAlignedRect requires a scale-translate matrix" }
    val topLeft = mapPoint(Point(rect.left, rect.top))
    val bottomRight = mapPoint(Point(rect.right, rect.bottom))
    return Rect.fromLTRB(
        minOf(topLeft.x, bottomRight.x),
        minOf(topLeft.y, bottomRight.y),
        maxOf(topLeft.x, bottomRight.x),
        maxOf(topLeft.y, bottomRight.y),
    )
}

/** Maps an axis-aligned rounded rectangle while preserving its per-corner radii. */
fun RRect.mapAxisAligned(matrix: Matrix3x3F32): RRect {
    require(matrix.isScaleTranslate()) { "mapAxisAligned requires a scale-translate matrix" }
    fun CornerRadii.map(): CornerRadii = CornerRadii(abs(x * matrix.sx), abs(y * matrix.sy))
    fun sourceCorner(deviceLeft: Boolean, deviceTop: Boolean): CornerRadii {
        val sourceLeft = if (matrix.sx < 0f) !deviceLeft else deviceLeft
        val sourceTop = if (matrix.sy < 0f) !deviceTop else deviceTop
        return when {
            sourceLeft && sourceTop -> topLeft
            !sourceLeft && sourceTop -> topRight
            !sourceLeft -> bottomRight
            else -> bottomLeft
        }
    }
    return RRect(
        rect = matrix.mapAxisAlignedRect(rect),
        topLeft = sourceCorner(deviceLeft = true, deviceTop = true).map(),
        topRight = sourceCorner(deviceLeft = false, deviceTop = true).map(),
        bottomRight = sourceCorner(deviceLeft = false, deviceTop = false).map(),
        bottomLeft = sourceCorner(deviceLeft = true, deviceTop = false).map(),
    )
}
