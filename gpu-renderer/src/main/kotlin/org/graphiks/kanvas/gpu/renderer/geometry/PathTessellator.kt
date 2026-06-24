package org.graphiks.kanvas.gpu.renderer.geometry

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
    /** Moves the current contour start without drawing. */
    data object MoveTo : PathVerb
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
    fun flatten(path: PathData): List<Point> {
        if (path.verbs.isEmpty()) return emptyList()

        val result = mutableListOf<Point>()
        var contourStart = Point(0f, 0f)
        var current = Point(0f, 0f)
        var contourStartSet = false
        for (verb in path.verbs) {
            when (verb) {
                is PathVerb.MoveTo -> {
                    contourStart = current
                    contourStartSet = true
                }
                is PathVerb.LineTo -> {
                    if (!contourStartSet) {
                        contourStart = verb.p
                        contourStartSet = true
                    }
                    if (result.isEmpty()) {
                        result.add(contourStart)
                        current = contourStart
                    }
                    val next = verb.p
                    if (next != current) {
                        result.add(next)
                        current = next
                    }
                }
                is PathVerb.QuadTo -> {
                    if (!contourStartSet) {
                        contourStart = verb.p
                        contourStartSet = true
                    }
                    if (result.isEmpty()) {
                        result.add(current)
                    }
                    val segments = quadraticSegments(current, verb.c, verb.p)
                    for (pt in segments.drop(1)) {
                        result.add(pt)
                    }
                    current = verb.p
                }
                is PathVerb.CubicTo -> {
                    if (!contourStartSet) {
                        contourStart = verb.p
                        contourStartSet = true
                    }
                    if (result.isEmpty()) {
                        result.add(current)
                    }
                    val segments = cubicSegments(current, verb.c1, verb.c2, verb.p)
                    for (pt in segments.drop(1)) {
                        result.add(pt)
                    }
                    current = verb.p
                }
                is PathVerb.Close -> {
                    if (current != contourStart) {
                        result.add(contourStart)
                    }
                    current = contourStart
                }
            }
            if (result.size > maxVertices) {
                throw IllegalStateException(
                    "Path flattened to ${result.size} vertices, exceeds budget of $maxVertices"
                )
            }
        }

        return result
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

    private fun quadraticSegments(p0: Point, p1: Point, p2: Point): List<Point> {
        val result = mutableListOf(p0)
        val steps = quadraticStepCount(p0, p1, p2)
        for (i in 1 until steps) {
            val t = i.toFloat() / steps
            val x = (1 - t) * (1 - t) * p0.x + 2 * (1 - t) * t * p1.x + t * t * p2.x
            val y = (1 - t) * (1 - t) * p0.y + 2 * (1 - t) * t * p1.y + t * t * p2.y
            result.add(Point(x, y))
        }
        result.add(p2)
        return result
    }

    private fun cubicSegments(p0: Point, p1: Point, p2: Point, p3: Point): List<Point> {
        val result = mutableListOf(p0)
        val steps = cubicStepCount(p0, p1, p2, p3)
        for (i in 1 until steps) {
            val t = i.toFloat() / steps
            val x = (1 - t).let { it * it * it } * p0.x +
                3 * (1 - t) * (1 - t) * t * p1.x +
                3 * (1 - t) * t * t * p2.x +
                t * t * t * p3.x
            val y = (1 - t).let { it * it * it } * p0.y +
                3 * (1 - t) * (1 - t) * t * p1.y +
                3 * (1 - t) * t * t * p2.y +
                t * t * t * p3.y
            result.add(Point(x, y))
        }
        result.add(p3)
        return result
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
}
