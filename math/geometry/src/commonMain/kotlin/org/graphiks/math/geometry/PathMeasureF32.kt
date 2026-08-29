package org.graphiks.math.geometry

import kotlin.math.sqrt

/** A point on a measured path and its normalized tangent. */
public data class PathLocationF32(public val point: Point2F32, public val tangent: Point2F32)

/**
 * Measures one contour of an immutable [PathF32] at a time.
 *
 * Curve lengths use the fixed subdivision documented by [PathAnalysisF32].
 * Distance-to-parameter mapping follows that same polyline, making results
 * deterministic across the supported Kotlin targets.
 */
public class PathMeasureF32(path: PathF32, forceClosed: Boolean = false) {
    private data class Segment(val points: List<Point2F32>) {
        val length: Float = points.zipWithNext().sumOf { (a, b) -> distance(a, b).toDouble() }.toFloat()
        fun at(distance: Float): PathLocationF32 {
            var remaining = distance.coerceIn(0f, length)
            points.zipWithNext().forEach { (a, b) ->
                val length = distance(a, b)
                if (remaining <= length || b == points.last()) {
                    val t = if (length == 0f) 0f else remaining / length
                    val point = Point2F32(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
                    return PathLocationF32(point, normalized(b.x - a.x, b.y - a.y))
                }
                remaining -= length
            }
            return PathLocationF32(points.last(), Point2F32(0f, 0f))
        }
    }

    private data class Contour(val segments: List<Segment>, val closed: Boolean) {
        val length: Float = segments.sumOf { it.length.toDouble() }.toFloat()
    }

    private val contours: List<Contour> = buildList {
        flattenedContours(path).forEach { contour ->
            val points = contour.points
            val segments = points.zipWithNext().map { (a, b) -> Segment(listOf(a, b)) }.toMutableList()
            if ((contour.closed || forceClosed) && points.size > 1 && points.last() != points.first()) segments += Segment(listOf(points.last(), points.first()))
            add(Contour(segments, contour.closed || forceClosed))
        }
    }
    private var contourIndex = 0

    /** Length of the current contour, or zero when there is none. */
    public val length: Float get() = contours.getOrNull(contourIndex)?.length ?: 0f

    /** Whether the current contour has an explicit or forced closing edge. */
    public val isClosed: Boolean get() = contours.getOrNull(contourIndex)?.closed ?: false

    /** Clamps [distance] to the current contour and returns its point and normalized tangent. */
    public fun position(distance: Float): PathLocationF32? {
        val contour = contours.getOrNull(contourIndex) ?: return null
        if (contour.segments.isEmpty()) return null
        var remaining = distance.coerceIn(0f, contour.length)
        contour.segments.forEach { segment ->
            if (remaining <= segment.length || segment === contour.segments.last()) return segment.at(remaining)
            remaining -= segment.length
        }
        return null
    }

    /** Returns a line representation of the requested current-contour interval. */
    public fun segment(startDistance: Float, stopDistance: Float, startWithMoveTo: Boolean = true): PathF32? {
        val start = position(startDistance) ?: return null
        val stop = position(stopDistance) ?: return null
        if (startDistance.coerceIn(0f, length) >= stopDistance.coerceIn(0f, length)) return null
        val builder = PathBuilder()
        if (startWithMoveTo) builder.moveTo(start.point.x, start.point.y)
        return builder.lineTo(stop.point.x, stop.point.y).build()
    }

    /** Selects the next contour, returning false when already on the final contour. */
    public fun nextContour(): Boolean = if (contourIndex + 1 < contours.size) { contourIndex++; true } else false
}

private fun distance(a: Point2F32, b: Point2F32): Float {
    val x = b.x - a.x; val y = b.y - a.y
    return sqrt(x * x + y * y)
}

private fun normalized(x: Float, y: Float): Point2F32 {
    val length = sqrt(x * x + y * y)
    return if (length == 0f) Point2F32(0f, 0f) else Point2F32(x / length, y / length)
}
