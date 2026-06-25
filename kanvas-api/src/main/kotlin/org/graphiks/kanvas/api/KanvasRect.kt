package org.graphiks.kanvas.api

data class KanvasPoint(val x: Float, val y: Float)

data class KanvasRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val isEmpty: Boolean get() = width <= 0f || height <= 0f

    companion object {
        fun fromLTRB(left: Float, top: Float, right: Float, bottom: Float): KanvasRect =
            KanvasRect(left, top, right, bottom)

        fun fromXYWH(x: Float, y: Float, w: Float, h: Float): KanvasRect =
            KanvasRect(x, y, x + w, y + h)
    }
}

data class KanvasRRectCornerRadii(val x: Float, val y: Float) {
    companion object {
        val ZERO = KanvasRRectCornerRadii(0f, 0f)
    }
}

data class KanvasRRect(
    val rect: KanvasRect,
    val topLeft: KanvasRRectCornerRadii,
    val topRight: KanvasRRectCornerRadii,
    val bottomRight: KanvasRRectCornerRadii,
    val bottomLeft: KanvasRRectCornerRadii,
) {
    constructor(rect: KanvasRect, radiusX: Float, radiusY: Float) : this(
        rect = rect,
        topLeft = KanvasRRectCornerRadii(radiusX, radiusY),
        topRight = KanvasRRectCornerRadii(radiusX, radiusY),
        bottomRight = KanvasRRectCornerRadii(radiusX, radiusY),
        bottomLeft = KanvasRRectCornerRadii(radiusX, radiusY),
    )

    constructor(rect: KanvasRect, radii: KanvasRRectCornerRadii) : this(
        rect = rect,
        topLeft = radii,
        topRight = radii,
        bottomRight = radii,
        bottomLeft = radii,
    )
}
