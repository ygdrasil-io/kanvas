package org.graphiks.math.geometry

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** The aggregate winding direction of the closed contours in a path. */
public enum class ContourOrientation { CLOCKWISE, COUNTER_CLOCKWISE, MIXED, UNDEFINED }

/** Structural information about a [PathF32] independent of a renderer. */
public data class PathTopology(
    public val contourCount: Int,
    public val closedContourCount: Int,
    public val orientation: ContourOrientation,
    public val inverseFill: Boolean,
)

/**
 * Renderer-neutral queries over immutable [PathF32] values.
 *
 * Curves used by containment and path operations are flattened with fixed,
 * deterministic subdivisions (16 for quadratic/cubic curves and 24 for SVG
 * arcs).  This deliberately trades sub-pixel exactness for identical JVM and
 * JavaScript behaviour; exact Bézier extrema are still used for [bounds].
 */
public object PathAnalysisF32 {
    /** Returns tight extrema bounds for line and Bézier geometry, or null for an empty path. */
    public fun bounds(path: PathF32): RectF32? {
        var left = Float.POSITIVE_INFINITY
        var top = Float.POSITIVE_INFINITY
        var right = Float.NEGATIVE_INFINITY
        var bottom = Float.NEGATIVE_INFINITY
        fun include(point: Point2F32) {
            if (!point.isFinite()) return
            left = min(left, point.x); top = min(top, point.y)
            right = max(right, point.x); bottom = max(bottom, point.y)
        }
        var current = Point2F32.Origin
        var started = false
        path.forEach { segment ->
            when (segment) {
                is PathSegmentF32.MoveTo -> { current = segment.point; started = true; include(current) }
                is PathSegmentF32.LineTo -> if (started) { include(current); include(segment.point); current = segment.point }
                is PathSegmentF32.QuadTo -> if (started) {
                    includeQuadExtrema(current, segment.control, segment.point, ::include)
                    current = segment.point
                }
                is PathSegmentF32.CubicTo -> if (started) {
                    includeCubicExtrema(current, segment.control1, segment.control2, segment.point, ::include)
                    current = segment.point
                }
                is PathSegmentF32.ArcTo -> if (started) {
                    arcPolyline(current, segment).forEach(::include)
                    current = segment.point
                }
                PathSegmentF32.Close -> Unit
            }
        }
        return if (left.isFinite()) RectF32(left, top, right, bottom) else null
    }

    /** Tests fill membership using the path's fill rule. Points on a boundary are outside. */
    public fun contains(path: PathF32, point: Point2F32): Boolean {
        var winding = 0
        flattenedContours(path).forEach { contour ->
            contour.edges.forEach { (first, second) ->
                if (first.y == second.y || point.y < min(first.y, second.y) || point.y >= max(first.y, second.y)) return@forEach
                val x = first.x + (point.y - first.y) * (second.x - first.x) / (second.y - first.y)
                if (x > point.x) winding += if (second.y > first.y) 1 else -1
            }
        }
        val inside = when (path.fillRule) {
            FillRule.WINDING, FillRule.INVERSE_WINDING -> winding != 0
            FillRule.EVEN_ODD, FillRule.INVERSE_EVEN_ODD -> winding % 2 != 0
        }
        return if (path.fillRule.isInverse()) !inside else inside
    }

    /** Returns true only when all four corners are filled. */
    public fun conservativelyContainsRect(path: PathF32, rect: RectF32): Boolean =
        contains(path, Point2F32(rect.left, rect.top)) &&
            contains(path, Point2F32(rect.right, rect.top)) &&
            contains(path, Point2F32(rect.left, rect.bottom)) &&
            contains(path, Point2F32(rect.right, rect.bottom))

    /** Returns the rectangle described by a canonical single-contour path, if any. */
    public fun rect(path: PathF32): RectF32? {
        val values = path.toList()
        if (values.size !in 5..6 || values.firstOrNull() !is PathSegmentF32.MoveTo) return null
        val close = values.last() == PathSegmentF32.Close
        val lines = values.drop(1).dropLast(if (close) 1 else 0)
        if (lines.size !in 3..4 || lines.any { it !is PathSegmentF32.LineTo }) return null
        val points = buildList {
            add((values.first() as PathSegmentF32.MoveTo).point)
            lines.forEach { add((it as PathSegmentF32.LineTo).point) }
        }
        if (!close && points.last() != points.first()) return null
        val corners = if (points.last() == points.first()) points.dropLast(1) else points
        if (corners.size != 4) return null
        if ((0 until 4).any { index ->
                val a = corners[index]; val b = corners[(index + 1) % 4]
                a.x != b.x && a.y != b.y
            }) return null
        val result = RectF32(corners.minOf { it.x }, corners.minOf { it.y }, corners.maxOf { it.x }, corners.maxOf { it.y })
        return result.takeIf { !it.isEmpty }
    }

    /** Returns the bounds of a canonical four-cubic oval, if any. */
    public fun oval(path: PathF32): RectF32? {
        val values = path.toList()
        if (values.size != 6 || values[0] !is PathSegmentF32.MoveTo || values[5] != PathSegmentF32.Close ||
            values.subList(1, 5).any { it !is PathSegmentF32.CubicTo }) return null
        val points = listOf((values[0] as PathSegmentF32.MoveTo).point) + values.subList(1, 5).map { (it as PathSegmentF32.CubicTo).point }
        if (points.last() != points.first()) return null
        val bounds = RectF32(points.minOf { it.x }, points.minOf { it.y }, points.maxOf { it.x }, points.maxOf { it.y })
        return bounds.takeIf { !it.isEmpty }
    }

    /** Returns the rounded rectangle described by a canonical line/arc contour, if any. */
    public fun rrect(path: PathF32): RRectF32? {
        val values = path.toList()
        if (values.size != 10 || values[0] !is PathSegmentF32.MoveTo || values[9] != PathSegmentF32.Close) return null
        if ((1..8).any { index -> if (index % 2 == 1) values[index] !is PathSegmentF32.LineTo else values[index] !is PathSegmentF32.ArcTo }) return null
        val endpoints = listOf((values[0] as PathSegmentF32.MoveTo).point) + (1..8).map {
            when (val segment = values[it]) {
                is PathSegmentF32.LineTo -> segment.point
                is PathSegmentF32.ArcTo -> segment.point
                else -> error("checked above")
            }
        }
        val bounds = RectF32(endpoints.minOf { it.x }, endpoints.minOf { it.y }, endpoints.maxOf { it.x }, endpoints.maxOf { it.y })
        if (bounds.isEmpty) return null
        val arcs = listOf(2, 4, 6, 8).map { values[it] as PathSegmentF32.ArcTo }
        return RRectF32(bounds, arcs[3].radius.toCornerRadii(), arcs[0].radius.toCornerRadii(), arcs[1].radius.toCornerRadii(), arcs[2].radius.toCornerRadii())
    }

    /** Returns the only line segment in a path, if it has exactly one. */
    public fun line(path: PathF32): Line2F32? =
        if (path.segmentCount == 2 && path.segmentAt(0) is PathSegmentF32.MoveTo && path.segmentAt(1) is PathSegmentF32.LineTo) {
            Line2F32((path.segmentAt(0) as PathSegmentF32.MoveTo).point, (path.segmentAt(1) as PathSegmentF32.LineTo).point)
        } else null

    /** A single, linear contour is convex when all non-collinear turns have the same sign. */
    public fun isConvex(path: PathF32): Boolean {
        val contours = flattenedContours(path)
        if (contours.size != 1 || contours.single().points.size < 3) return contours.size <= 1
        val points = contours.single().points.dropLastWhile { it == contours.single().points.first() }
        var sign = 0f
        for (index in points.indices) {
            val a = points[index]; val b = points[(index + 1) % points.size]; val c = points[(index + 2) % points.size]
            val cross = (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)
            if (cross != 0f) {
                if (sign == 0f) sign = cross else if (sign * cross < 0f) return false
            }
        }
        return true
    }

    /** Paths interpolate when their command verbs have the same shape. */
    public fun isInterpolatable(first: PathF32, second: PathF32): Boolean =
        first.segmentCount == second.segmentCount && first.zip(second).all { (a, b) -> a::class == b::class }

    /** Summarizes contours, closure, screen-space winding and inverse fill. */
    public fun topology(path: PathF32): PathTopology {
        val contours = flattenedContours(path)
        val directions = contours.filter { it.closed && it.points.size >= 3 }.mapNotNull { contour ->
            val area = signedArea(contour.points)
            when {
                area > 0f -> ContourOrientation.CLOCKWISE
                area < 0f -> ContourOrientation.COUNTER_CLOCKWISE
                else -> null
            }
        }.toSet()
        val orientation = when (directions.size) {
            0 -> ContourOrientation.UNDEFINED
            1 -> directions.single()
            else -> ContourOrientation.MIXED
        }
        return PathTopology(contours.size, contours.count { it.closed }, orientation, path.fillRule.isInverse())
    }
}

internal data class FlattenedContour(val points: List<Point2F32>, val closed: Boolean) {
    val edges: List<Pair<Point2F32, Point2F32>> get() = points.zipWithNext()
}

/** Fixed subdivision shared by neutral path analyses, measuring and boolean operations. */
internal fun flattenedContours(path: PathF32): List<FlattenedContour> {
    val result = mutableListOf<FlattenedContour>()
    var points = mutableListOf<Point2F32>()
    var start = Point2F32.Origin
    var current = Point2F32.Origin
    var started = false
    var closed = false
    fun finish() {
        if (started) result += FlattenedContour(points.toList(), closed)
    }
    path.forEach { segment ->
        when (segment) {
            is PathSegmentF32.MoveTo -> { finish(); points = mutableListOf(segment.point); start = segment.point; current = start; started = true; closed = false }
            is PathSegmentF32.LineTo -> if (started) { points += segment.point; current = segment.point }
            is PathSegmentF32.QuadTo -> if (started) { points += quadPolyline(current, segment.control, segment.point).drop(1); current = segment.point }
            is PathSegmentF32.CubicTo -> if (started) { points += cubicPolyline(current, segment.control1, segment.control2, segment.point).drop(1); current = segment.point }
            is PathSegmentF32.ArcTo -> if (started) { points += arcPolyline(current, segment).drop(1); current = segment.point }
            PathSegmentF32.Close -> if (started) { if (current != start) points += start; current = start; closed = true }
        }
    }
    finish()
    return result
}

internal fun quadPolyline(a: Point2F32, control: Point2F32, b: Point2F32, steps: Int = 16): List<Point2F32> =
    (0..steps).map { index ->
        val t = index.toFloat() / steps; val u = 1f - t
        Point2F32(u * u * a.x + 2f * u * t * control.x + t * t * b.x, u * u * a.y + 2f * u * t * control.y + t * t * b.y)
    }

internal fun cubicPolyline(a: Point2F32, control1: Point2F32, control2: Point2F32, b: Point2F32, steps: Int = 16): List<Point2F32> =
    (0..steps).map { index ->
        val t = index.toFloat() / steps; val u = 1f - t
        Point2F32(u * u * u * a.x + 3f * u * u * t * control1.x + 3f * u * t * t * control2.x + t * t * t * b.x, u * u * u * a.y + 3f * u * u * t * control1.y + 3f * u * t * t * control2.y + t * t * t * b.y)
    }

internal fun arcPolyline(start: Point2F32, arc: PathSegmentF32.ArcTo, steps: Int = 24): List<Point2F32> {
    val rx = abs(arc.radius.x.toDouble()); val ry = abs(arc.radius.y.toDouble())
    if (rx == 0.0 || ry == 0.0 || start == arc.point) return listOf(start, arc.point)
    val phi = arc.xAxisRotation.toDouble() * PI / 180.0; val cosPhi = cos(phi); val sinPhi = sin(phi)
    val dx = (start.x - arc.point.x).toDouble() / 2.0; val dy = (start.y - arc.point.y).toDouble() / 2.0
    val x1 = cosPhi * dx + sinPhi * dy; val y1 = -sinPhi * dx + cosPhi * dy
    val scale = max(1.0, x1 * x1 / (rx * rx) + y1 * y1 / (ry * ry)); val arx = rx * sqrt(scale); val ary = ry * sqrt(scale)
    val sign = if (arc.largeArc == arc.sweep) -1.0 else 1.0
    val numerator = max(0.0, arx * arx * ary * ary - arx * arx * y1 * y1 - ary * ary * x1 * x1)
    val denominator = arx * arx * y1 * y1 + ary * ary * x1 * x1
    val factor = if (denominator == 0.0) 0.0 else sign * sqrt(numerator / denominator)
    val cxp = factor * arx * y1 / ary; val cyp = -factor * ary * x1 / arx
    val centerX = cosPhi * cxp - sinPhi * cyp + (start.x + arc.point.x) / 2.0; val centerY = sinPhi * cxp + cosPhi * cyp + (start.y + arc.point.y) / 2.0
    val startAngle = atan2((y1 - cyp) / ary, (x1 - cxp) / arx)
    var delta = atan2((-y1 - cyp) / ary, (-x1 - cxp) / arx) - startAngle
    if (!arc.sweep && delta > 0) delta -= 2 * PI
    if (arc.sweep && delta < 0) delta += 2 * PI
    return (0..steps).map { index ->
        val angle = startAngle + delta * index / steps
        Point2F32((centerX + arx * cosPhi * cos(angle) - ary * sinPhi * sin(angle)).toFloat(), (centerY + arx * sinPhi * cos(angle) + ary * cosPhi * sin(angle)).toFloat())
    }
}

private fun includeQuadExtrema(a: Point2F32, c: Point2F32, b: Point2F32, include: (Point2F32) -> Unit) {
    include(a); include(b)
    fun root(p0: Float, p1: Float, p2: Float): Float? { val d = p0 - 2f * p1 + p2; return if (d != 0f) (p0 - p1) / d else null }
    fun point(t: Float): Point2F32 {
        val u = 1f - t
        return Point2F32(u * u * a.x + 2f * u * t * c.x + t * t * b.x, u * u * a.y + 2f * u * t * c.y + t * t * b.y)
    }
    listOfNotNull(root(a.x, c.x, b.x), root(a.y, c.y, b.y)).distinct().filter { it > 0f && it < 1f }.forEach { include(point(it)) }
}

private fun includeCubicExtrema(a: Point2F32, c1: Point2F32, c2: Point2F32, b: Point2F32, include: (Point2F32) -> Unit) {
    include(a); include(b)
    fun roots(p0: Float, p1: Float, p2: Float, p3: Float): List<Float> {
        val aa = (-p0 + 3f * p1 - 3f * p2 + p3).toDouble(); val bb = (2f * (p0 - 2f * p1 + p2)).toDouble(); val cc = (p1 - p0).toDouble()
        if (abs(aa) < 1e-12) return if (abs(bb) < 1e-12) emptyList() else listOf((-cc / bb).toFloat())
        val disc = bb * bb - 4.0 * aa * cc; if (disc < 0.0) return emptyList(); val root = sqrt(disc)
        return listOf(((-bb + root) / (2.0 * aa)).toFloat(), ((-bb - root) / (2.0 * aa)).toFloat())
    }
    fun point(t: Float): Point2F32 {
        val u = 1f - t
        return Point2F32(
            u * u * u * a.x + 3f * u * u * t * c1.x + 3f * u * t * t * c2.x + t * t * t * b.x,
            u * u * u * a.y + 3f * u * u * t * c1.y + 3f * u * t * t * c2.y + t * t * t * b.y,
        )
    }
    (roots(a.x, c1.x, c2.x, b.x) + roots(a.y, c1.y, c2.y, b.y)).distinct().filter { it > 0f && it < 1f }.forEach { include(point(it)) }
}

private fun signedArea(points: List<Point2F32>): Float = points.zipWithNext().sumOf { (a, b) -> (a.x * b.y - a.y * b.x).toDouble() }.toFloat() / 2f
internal fun FillRule.isInverse(): Boolean = this == FillRule.INVERSE_WINDING || this == FillRule.INVERSE_EVEN_ODD
private fun org.graphiks.math.vector.Vector2F32.toCornerRadii(): CornerRadiiF32 = CornerRadiiF32.of(x, y)
