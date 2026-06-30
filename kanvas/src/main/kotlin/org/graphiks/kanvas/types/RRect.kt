package org.graphiks.kanvas.types

data class CornerRadii(val x: Float, val y: Float)

data class RRect(
    val rect: Rect,
    val topLeft: CornerRadii = CornerRadii(0f, 0f),
    val topRight: CornerRadii = CornerRadii(0f, 0f),
    val bottomRight: CornerRadii = CornerRadii(0f, 0f),
    val bottomLeft: CornerRadii = CornerRadii(0f, 0f),
) {
    constructor(rect: Rect, radius: Float) : this(
        rect,
        CornerRadii(radius, radius),
        CornerRadii(radius, radius),
        CornerRadii(radius, radius),
        CornerRadii(radius, radius),
    )
}
