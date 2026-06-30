package org.graphiks.kanvas.dsl

import org.graphiks.kanvas.geometry.Path as KanvasPath

@KanvasDsl
class PathScope {
    private val path = KanvasPath()
    fun moveTo(x: Float, y: Float) { path.moveTo(x, y) }
    fun lineTo(x: Float, y: Float) { path.lineTo(x, y) }
    fun quadTo(cx: Float, cy: Float, x: Float, y: Float) { path.quadTo(cx, cy, x, y) }
    fun cubicTo(cx1: Float, cy1: Float, cx2: Float, cy2: Float, x: Float, y: Float) { path.cubicTo(cx1, cy1, cx2, cy2, x, y) }
    fun arcTo(rx: Float, ry: Float, xAxisRotation: Float, largeArc: Boolean, sweep: Boolean, x: Float, y: Float) {
        path.arcTo(rx, ry, xAxisRotation, largeArc, sweep, x, y)
    }
    fun close() { path.close() }
    internal fun build(): KanvasPath = path
}

fun KanvasPath.Companion.invoke(block: PathScope.() -> Unit): KanvasPath {
    val scope = PathScope()
    scope.block()
    return scope.build()
}
