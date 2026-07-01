package org.graphiks.kanvas.operators

import org.graphiks.kanvas.types.*

/** Adds two [Point]s component-wise. */
operator fun Point.plus(p: Point) = Point(x + p.x, y + p.y)
/** Subtracts two [Point]s component-wise. */
operator fun Point.minus(p: Point) = Point(x - p.x, y - p.y)
/** Scales this [Point] by a scalar factor. */
operator fun Point.times(s: Float) = Point(x * s, y * s)
/** Divides this [Point] by a scalar divisor. */
operator fun Point.div(s: Float) = Point(x / s, y / s)
