package org.graphiks.kanvas.dsl

import org.graphiks.kanvas.geometry.Path as KanvasPath

/**
 * DSL builder scope for constructing a [KanvasPath] declaratively.
 *
 * Inside a lambda with [PathScope] as its receiver, call path-building
 * methods such as [moveTo], [lineTo], [quadTo], [cubicTo], [arcTo], and
 * [close] to describe a sequence of segments. The resulting [KanvasPath]
 * is produced via [KanvasPath.Companion.invoke] factory function defined
 * in the [KanvasPath] companion.
 *
 * @sample
 * val p = KanvasPath {
 *     moveTo(0f, 0f)
 *     lineTo(100f, 0f)
 *     lineTo(100f, 100f)
 *     close()
 * }
 */
@KanvasDsl
class PathScope {
    private val path = KanvasPath()

    /** Moves the current point to (x, y) without drawing a segment. */
    fun moveTo(x: Float, y: Float) { path.moveTo(x, y) }

    /** Adds a straight-line segment from the current point to (x, y). */
    fun lineTo(x: Float, y: Float) { path.lineTo(x, y) }

    /** Adds a quadratic Bézier curve from the current point through (cx, cy) to (x, y). */
    fun quadTo(cx: Float, cy: Float, x: Float, y: Float) { path.quadTo(cx, cy, x, y) }

    /** Adds a cubic Bézier curve from the current point through (cx1, cy1) and (cx2, cy2) to (x, y). */
    fun cubicTo(cx1: Float, cy1: Float, cx2: Float, cy2: Float, x: Float, y: Float) {
        path.cubicTo(cx1, cy1, cx2, cy2, x, y)
    }

    /**
     * Adds an arc segment approximating an elliptical arc.
     *
     * @param rx              x-axis radius of the ellipse.
     * @param ry              y-axis radius of the ellipse.
     * @param xAxisRotation   rotation of the ellipse x-axis, in degrees.
     * @param largeArc        true to choose the larger of the two possible arcs.
     * @param sweep           true for the sweep-positive (clockwise) arc.
     * @param x               destination x-coordinate.
     * @param y               destination y-coordinate.
     */
    fun arcTo(
        rx: Float, ry: Float, xAxisRotation: Float,
        largeArc: Boolean, sweep: Boolean, x: Float, y: Float,
    ) {
        path.arcTo(rx, ry, xAxisRotation, largeArc, sweep, x, y)
    }

    /** Closes the current contour by adding a line segment back to the contour's start point. */
    fun close() { path.close() }

    /** Returns the accumulated [KanvasPath] and resets the builder. */
    internal fun build(): KanvasPath = path
}
