package org.graphiks.kanvas

data class KanvasPoint(val x: Float, val y: Float)

data class Rect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val isEmpty: Boolean get() = width <= 0f || height <= 0f

    companion object {
        fun fromLTRB(left: Float, top: Float, right: Float, bottom: Float): Rect =
            Rect(left, top, right, bottom)

        fun fromXYWH(x: Float, y: Float, w: Float, h: Float): Rect =
            Rect(x, y, x + w, y + h)
    }
}

data class RRectCornerRadii(val x: Float, val y: Float) {
    companion object {
        val ZERO = RRectCornerRadii(0f, 0f)
    }
}

data class RRect(
    val rect: Rect,
    val topLeft: RRectCornerRadii,
    val topRight: RRectCornerRadii,
    val bottomRight: RRectCornerRadii,
    val bottomLeft: RRectCornerRadii,
) {
    constructor(rect: Rect, radiusX: Float, radiusY: Float) : this(
        rect = rect,
        topLeft = RRectCornerRadii(radiusX, radiusY),
        topRight = RRectCornerRadii(radiusX, radiusY),
        bottomRight = RRectCornerRadii(radiusX, radiusY),
        bottomLeft = RRectCornerRadii(radiusX, radiusY),
    )

    constructor(rect: Rect, radii: RRectCornerRadii) : this(
        rect = rect,
        topLeft = radii,
        topRight = radii,
        bottomRight = radii,
        bottomLeft = radii,
    )
}
