package org.graphiks.kanvas.gpu.renderer.geometry

import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendTriangleData

/** 2D point with float coordinates. */
data class Point(val x: Float, val y: Float)

/** Path contour data model: ordered verbs and corresponding point data. */
data class PathData(
    val verbs: List<PathVerb>,
    val points: List<Point>,
) {
    init {
        require(verbs.isNotEmpty() || points.isEmpty()) {
            "PathData requires matching verbs and points"
        }
    }
}

/** Sealed path verb hierarchy: MoveTo, LineTo, QuadTo, CubicTo, Close. */
sealed interface PathVerb {
    /** Moves the current contour start to the given point without drawing. */
    data class MoveTo(val p: Point) : PathVerb
    /** Draws a straight line segment to the given point. */
    data class LineTo(val p: Point) : PathVerb
    /** Draws a quadratic bezier curve to p using control point c. */
    data class QuadTo(val c: Point, val p: Point) : PathVerb
    /** Draws a cubic bezier curve to p using control points c1 and c2. */
    data class CubicTo(val c1: Point, val c2: Point, val p: Point) : PathVerb
    /** Closes the current contour back to its start point. */
    data object Close : PathVerb
}

/**
 * Flat triangle list produced by [PathTessellator.triangulate].
 * Contains vertex positions and index buffer for GPU submission.
 */
data class TriangleList(
    val vertices: List<Point>,
    val indices: List<Int>,
) {
    val triangleCount: Int get() = indices.size / 3
}

/** Flattened path points and the point index where each contour begins. */
data class FlattenedPath(
    val points: List<Point>,
    val contourStarts: List<Int>,
)

/**
 * Flattens bezier curves into line segments and triangulates
 * the resulting polygon into a triangle fan.
 *
 * Enforces a configurable vertex budget (default 256) and
 * uses adaptive step counting for quad/cubic curves based on
 * a tolerance parameter.
 */
class PathTessellator(
    private val tolerance: Float = 0.25f,
    private val maxVertices: Int = 256,
) {
    /**
     * Flattens a [PathData] into a list of [Point]s by
     * approximating quad/cubic curves with line segments.
     */
    fun flatten(path: PathData): List<Point> = flattenWithContours(path).points

    /** Flattens a [PathData] while preserving each [PathVerb.MoveTo] boundary. */
    fun flattenWithContours(path: PathData): FlattenedPath {
        if (path.verbs.isEmpty()) return FlattenedPath(emptyList(), emptyList())

        val result = mutableListOf<Point>()
        val contourStarts = mutableListOf<Int>()
        var current = Point(0f, 0f)
        val contour = ContourState(start = current)
        for (verb in path.verbs) {
            when (verb) {
                is PathVerb.MoveTo -> {
                    current = verb.p
                    contour.start = verb.p
                    contour.isOpen = false
                    contour.hasExplicitStart = true
                }
                is PathVerb.LineTo -> {
                    beginContourIfNeeded(contour, current, result, contourStarts)
                    val next = verb.p
                    if (next != current) {
                        appendPoint(result, next)
                        current = next
                    }
                }
                is PathVerb.QuadTo -> {
                    beginContourIfNeeded(contour, current, result, contourStarts)
                    emitQuadraticSegments(current, verb.c, verb.p, result)
                    current = verb.p
                }
                is PathVerb.CubicTo -> {
                    beginContourIfNeeded(contour, current, result, contourStarts)
                    emitCubicSegments(current, verb.c1, verb.c2, verb.p, result)
                    current = verb.p
                }
                is PathVerb.Close -> {
                    if (contour.isOpen && current != contour.start) {
                        appendPoint(result, contour.start)
                    }
                    if (contour.isOpen) {
                        current = contour.start
                    } else if (contour.hasExplicitStart) {
                        contourStarts.add(result.size)
                        appendPoint(result, contour.start)
                        current = contour.start
                    }
                    contour.isOpen = false
                    contour.hasExplicitStart = false
                }
            }
        }

        return FlattenedPath(points = result, contourStarts = contourStarts)
    }

    private fun beginContourIfNeeded(
        contour: ContourState,
        current: Point,
        result: MutableList<Point>,
        contourStarts: MutableList<Int>,
    ) {
        if (contour.isOpen) return

        contour.start = current
        contourStarts.add(result.size)
        appendPoint(result, current)
        contour.isOpen = true
        contour.hasExplicitStart = false
    }

    private fun appendPoint(result: MutableList<Point>, point: Point) {
        if (result.size >= maxVertices) {
            throw IllegalStateException(
                "Path flattened to ${result.size + 1} vertices, exceeds budget of $maxVertices"
            )
        }
        result.add(point)
    }

    private fun emitQuadraticSegments(p0: Point, p1: Point, p2: Point, result: MutableList<Point>) {
        val steps = quadraticStepCount(p0, p1, p2)
        for (i in 1..steps) {
            val t = i.toFloat() / steps
            val x = (1 - t) * (1 - t) * p0.x + 2 * (1 - t) * t * p1.x + t * t * p2.x
            val y = (1 - t) * (1 - t) * p0.y + 2 * (1 - t) * t * p1.y + t * t * p2.y
            appendPoint(result, Point(x, y))
        }
    }

    private fun emitCubicSegments(p0: Point, p1: Point, p2: Point, p3: Point, result: MutableList<Point>) {
        val steps = cubicStepCount(p0, p1, p2, p3)
        for (i in 1..steps) {
            val t = i.toFloat() / steps
            val x = (1 - t).let { it * it * it } * p0.x +
                3 * (1 - t) * (1 - t) * t * p1.x +
                3 * (1 - t) * t * t * p2.x +
                t * t * t * p3.x
            val y = (1 - t).let { it * it * it } * p0.y +
                3 * (1 - t) * (1 - t) * t * p1.y +
                3 * (1 - t) * t * t * p2.y +
                t * t * t * p3.y
            appendPoint(result, Point(x, y))
        }
    }

    /**
     * Triangulates a list of flattened contour points into a
     * triangle fan indexed buffer. The first vertex serves as
     * the fan origin.
     */
    fun triangulate(points: List<Point>): TriangleList {
        if (points.size < 3) return TriangleList(emptyList(), emptyList())
        if (points.size > maxVertices) {
            throw IllegalStateException(
                "Path has ${points.size} vertices, exceeds budget of $maxVertices"
            )
        }

        val indices = mutableListOf<Int>()
        for (i in 1 until points.size - 1) {
            indices.add(0)
            indices.add(i)
            indices.add(i + 1)
        }

        return TriangleList(vertices = points, indices = indices)
    }

    /**
     * Produces one triangle from an exterior anchor to each contour edge for a
     * stencil write pass. Unlike [triangulate], contour boundaries are retained.
     */
    fun stencilEdgeFan(flattened: FlattenedPath): GPUBackendTriangleData {
        val anchor = Point(
            minOf(-1f, flattened.points.minOf { it.x } - 1f),
            minOf(-1f, flattened.points.minOf { it.y } - 1f),
        )
        val vertices = mutableListOf<Float>()
        val indices = mutableListOf<Int>()

        flattened.contourStarts.forEachIndexed { contourIndex, start ->
            val end = flattened.contourStarts.getOrElse(contourIndex + 1) { flattened.points.size }
            for (index in start until end) {
                val next = if (index + 1 == end) start else index + 1
                val base = vertices.size / 2
                vertices += listOf(
                    anchor.x,
                    anchor.y,
                    flattened.points[index].x,
                    flattened.points[index].y,
                    flattened.points[next].x,
                    flattened.points[next].y,
                )
                indices += listOf(base, base + 1, base + 2)
            }
        }
        return GPUBackendTriangleData(vertices.toFloatArray(), indices.toIntArray())
    }

    private fun quadraticStepCount(p0: Point, p1: Point, p2: Point): Int {
        val dx = p2.x - 2 * p1.x + p0.x
        val dy = p2.y - 2 * p1.y + p0.y
        val len = kotlin.math.sqrt(dx * dx + dy * dy)
        return (len / tolerance).toInt().coerceAtLeast(2)
    }

    private fun cubicStepCount(p0: Point, p1: Point, p2: Point, p3: Point): Int {
        val dx1 = p1.x - p0.x
        val dy1 = p1.y - p0.y
        val dx2 = p2.x - p1.x
        val dy2 = p2.y - p1.y
        val dx3 = p3.x - p2.x
        val dy3 = p3.y - p2.y
        val len = kotlin.math.sqrt(dx1 * dx1 + dy1 * dy1) +
            kotlin.math.sqrt(dx2 * dx2 + dy2 * dy2) +
            kotlin.math.sqrt(dx3 * dx3 + dy3 * dy3)
        return (len / tolerance).toInt().coerceAtLeast(2)
    }

    private data class ContourState(
        var start: Point,
        var isOpen: Boolean = false,
        var hasExplicitStart: Boolean = false,
    )
}
