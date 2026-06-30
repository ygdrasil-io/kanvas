package org.graphiks.kanvas.types

data class Point(val x: Float, val y: Float) {
    companion object {
        val ZERO = Point(0f, 0f)
    }
}

operator fun Point.plus(other: Point): Point = Point(x + other.x, y + other.y)
operator fun Point.minus(other: Point): Point = Point(x - other.x, y - other.y)
operator fun Point.times(scalar: Float): Point = Point(x * scalar, y * scalar)
operator fun Point.div(scalar: Float): Point = Point(x / scalar, y / scalar)
