package org.graphiks.kanvas.operators

import org.graphiks.kanvas.types.*

operator fun Point.plus(p: Point) = Point(x + p.x, y + p.y)
operator fun Point.minus(p: Point) = Point(x - p.x, y - p.y)
operator fun Point.times(s: Float) = Point(x * s, y * s)
operator fun Point.div(s: Float) = Point(x / s, y / s)
