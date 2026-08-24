package org.graphiks.kanvas.types

import org.graphiks.math.geometry.RectF32

data class CornerRadii(val x: Float, val y: Float)

data class RRect(
    val rect: RectF32,
    val topLeft: CornerRadii = CornerRadii(0f, 0f),
    val topRight: CornerRadii = CornerRadii(0f, 0f),
    val bottomRight: CornerRadii = CornerRadii(0f, 0f),
    val bottomLeft: CornerRadii = CornerRadii(0f, 0f),
) {
    constructor(rect: RectF32, radius: Float) : this(
        rect,
        CornerRadii(radius, radius),
        CornerRadii(radius, radius),
        CornerRadii(radius, radius),
        CornerRadii(radius, radius),
    )
}
