package org.graphiks.kanvas.types

data class Rect(
    var left: Float,
    var top: Float,
    var right: Float,
    var bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val isEmpty: Boolean get() = width <= 0f || height <= 0f
    val center: Point get() = Point((left + right) / 2f, (top + bottom) / 2f)

    companion object {
        fun fromLTRB(l: Float, t: Float, r: Float, b: Float) = Rect(l, t, r, b)
        fun fromXYWH(x: Float, y: Float, w: Float, h: Float) = Rect(x, y, x + w, y + h)
        val EMPTY get() = Rect(0f, 0f, 0f, 0f)
    }
}

operator fun Rect.contains(p: Point): Boolean = p.x in left..right && p.y in top..bottom
