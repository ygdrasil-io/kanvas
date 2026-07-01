package org.graphiks.kanvas.dsl

import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Point

@KanvasDsl
class LinearGradientScope {
    var start: Point = Point.ZERO
    var end: Point = Point.ZERO
    var tileMode: TileMode = TileMode.CLAMP
    private val stops = mutableListOf<GradientStop>()
    fun stop(position: Float, color: Color) { stops.add(GradientStop(position, color)) }
    internal fun build() = Shader.LinearGradient(start, end, stops.toList(), tileMode)
}

fun linearGradient(block: LinearGradientScope.() -> Unit): Shader.LinearGradient {
    return LinearGradientScope().apply(block).build()
}

@KanvasDsl
class RadialGradientScope {
    var center: Point = Point.ZERO
    var radius: Float = 0f
    var tileMode: TileMode = TileMode.CLAMP
    private val stops = mutableListOf<GradientStop>()
    fun stop(position: Float, color: Color) { stops.add(GradientStop(position, color)) }
    internal fun build() = Shader.RadialGradient(center, radius, stops.toList(), tileMode)
}

fun radialGradient(block: RadialGradientScope.() -> Unit): Shader.RadialGradient {
    return RadialGradientScope().apply(block).build()
}
