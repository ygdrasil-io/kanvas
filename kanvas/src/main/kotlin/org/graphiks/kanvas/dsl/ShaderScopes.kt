package org.graphiks.kanvas.dsl

import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.Point2F32

@KanvasDsl
class LinearGradientScope {
    var start: Point2F32 = Point2F32.Origin
    var end: Point2F32 = Point2F32.Origin
    var tileMode: TileMode = TileMode.CLAMP
    private val stops = mutableListOf<GradientStop>()
    fun stop(position: Float, color: ColorARGB) { stops.add(GradientStop(position, color)) }
    internal fun build() = Shader.LinearGradient(start, end, stops.toList(), tileMode)
}

fun linearGradient(block: LinearGradientScope.() -> Unit): Shader.LinearGradient {
    return LinearGradientScope().apply(block).build()
}

@KanvasDsl
class RadialGradientScope {
    var center: Point2F32 = Point2F32.Origin
    var radius: Float = 0f
    var tileMode: TileMode = TileMode.CLAMP
    private val stops = mutableListOf<GradientStop>()
    fun stop(position: Float, color: ColorARGB) { stops.add(GradientStop(position, color)) }
    internal fun build() = Shader.RadialGradient(center, radius, stops.toList(), tileMode)
}

fun radialGradient(block: RadialGradientScope.() -> Unit): Shader.RadialGradient {
    return RadialGradientScope().apply(block).build()
}
