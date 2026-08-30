package org.graphiks.math.geometry

import org.graphiks.math.vector.Vector2F64
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** The aggregate winding direction of the closed contours in a path. */
public enum class ContourOrientation { CLOCKWISE, COUNTER_CLOCKWISE, MIXED, UNDEFINED }

/** Structural information about a [PathF32] independent of a renderer. */
public data class PathTopologyI32(
    public val contourCount: Int,
    public val closedContourCount: Int,
    public val orientation: ContourOrientation,
    public val inverseFill: Boolean,
)

/** Renderer-neutral queries over immutable [PathF32] values. */
public object PathAnalysisF32 {
    /** Returns tight analytic extrema bounds, or null for an empty path. */
    public fun bounds(path: PathF32): RectF32? {
        var left = Float.POSITIVE_INFINITY
        var top = Float.POSITIVE_INFINITY
        var right = Float.NEGATIVE_INFINITY
        var bottom = Float.NEGATIVE_INFINITY
        fun include(point: Point2F32) {
            if (!point.isFinite()) return
            left = min(left, point.x)
            top = min(top, point.y)
            right = max(right, point.x)
            bottom = max(bottom, point.y)
        }
        var contourStart = Point2F32.Origin
        var current = Point2F32.Origin
        var started = false
        path.forEach { segment ->
            when (segment) {
                is PathSegmentF32.MoveTo -> {
                    current = segment.point
                    contourStart = current
                    started = true
                    include(current)
                }
                is PathSegmentF32.LineTo -> if (started) {
                    include(current)
                    current = segment.point
                    include(current)
                }
                is PathSegmentF32.QuadTo -> if (started) {
                    includeQuadExtrema(current, segment.control, segment.point, ::include)
                    current = segment.point
                }
                is PathSegmentF32.CubicTo -> if (started) {
                    includeCubicExtrema(current, segment.control1, segment.control2, segment.point, ::include)
                    current = segment.point
                }
                is PathSegmentF32.ArcTo -> if (started) {
                    val arc = ArcEndpointF64(
                        current.toPoint2F64(),
                        segment.point.toPoint2F64(),
                        Vector2F64(segment.radius.x.toDouble(), segment.radius.y.toDouble()),
                        segment.xAxisRotation.toDouble(),
                        segment.largeArc,
                        segment.sweep,
                    )
                    (arcCenterF64(arc)?.extrema() ?: listOf(arc.start, arc.end))
                        .forEach { include(it.toPoint2F32()) }
                    current = segment.point
                }
                PathSegmentF32.Close -> if (started) current = contourStart
            }
        }
        return if (left.isFinite()) RectF32(left, top, right, bottom) else null
    }

    /** Tests fill membership using the path's fill rule. Points on a boundary are outside. */
    public fun contains(path: PathF32, point: Point2F32): Boolean {
        if (!point.isFinite()) return false
        val normalization = pathNormalizationF64(listOf(path))
        val normalizedPoint = normalization.normalize(point)
        val contours = PathFlattenerF64.flatten(NormalizedPathF64(path, normalization), closeForFill = true)
        var winding = 0
        for (contour in contours) {
            for ((first, second) in contour.points.zipWithNext().map { it.first.point to it.second.point }) {
                if (PathPredicatesF64.onSegment(normalizedPoint, first, second)) return false
                if (first.y == second.y || normalizedPoint.y < min(first.y, second.y) || normalizedPoint.y >= max(first.y, second.y)) continue
                val x = first.x + (normalizedPoint.y - first.y) * (second.x - first.x) / (second.y - first.y)
                if (x > normalizedPoint.x) winding += if (second.y > first.y) 1 else -1
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

    /** Returns the rectangle described by the canonical [PathBuilder.addRect] contour, if any. */
    public fun rect(path: PathF32): RectF32? {
        val values = path.toList()
        if (values.size != 5 || values[0] !is PathSegmentF32.MoveTo || values[4] != PathSegmentF32.Close ||
            values.subList(1, 4).any { it !is PathSegmentF32.LineTo }
        ) return null
        val points = listOf((values[0] as PathSegmentF32.MoveTo).point) + values.subList(1, 4).map { (it as PathSegmentF32.LineTo).point }
        val result = RectF32(points.minOf { it.x }, points.minOf { it.y }, points.maxOf { it.x }, points.maxOf { it.y })
        if (result.isEmpty) return null
        return result.takeIf {
            points == listOf(
                Point2F32(result.left, result.top),
                Point2F32(result.right, result.top),
                Point2F32(result.right, result.bottom),
                Point2F32(result.left, result.bottom),
            )
        }
    }

    /** Returns the bounds of the canonical four-cubic [PathBuilder.addOval] contour, if any. */
    public fun oval(path: PathF32): RectF32? {
        val values = path.toList()
        if (values.size != 6 || values[0] !is PathSegmentF32.MoveTo || values[5] != PathSegmentF32.Close ||
            values.subList(1, 5).any { it !is PathSegmentF32.CubicTo }
        ) return null
        val start = (values[0] as PathSegmentF32.MoveTo).point
        val cubics = values.subList(1, 5).map { it as PathSegmentF32.CubicTo }
        val points = listOf(start) + cubics.map { it.point }
        if (points.last() != start) return null
        val result = RectF32(points.minOf { it.x }, points.minOf { it.y }, points.maxOf { it.x }, points.maxOf { it.y })
        if (result.isEmpty) return null
        val left = result.left.toDouble()
        val top = result.top.toDouble()
        val right = result.right.toDouble()
        val bottom = result.bottom.toDouble()
        val centerX = (left + right) * 0.5
        val centerY = (top + bottom) * 0.5
        val radiusX = (right - left) * 0.5
        val radiusY = (bottom - top) * 0.5
        val control = 0.5522847498
        val expected = listOf(
            PathSegmentF32.CubicTo(Point2F32((centerX + radiusX).toFloat(), (centerY + control * radiusY).toFloat()), Point2F32((centerX + control * radiusX).toFloat(), (centerY + radiusY).toFloat()), Point2F32(centerX.toFloat(), (centerY + radiusY).toFloat())),
            PathSegmentF32.CubicTo(Point2F32((centerX - control * radiusX).toFloat(), (centerY + radiusY).toFloat()), Point2F32((centerX - radiusX).toFloat(), (centerY + control * radiusY).toFloat()), Point2F32((centerX - radiusX).toFloat(), centerY.toFloat())),
            PathSegmentF32.CubicTo(Point2F32((centerX - radiusX).toFloat(), (centerY - control * radiusY).toFloat()), Point2F32((centerX - control * radiusX).toFloat(), (centerY - radiusY).toFloat()), Point2F32(centerX.toFloat(), (centerY - radiusY).toFloat())),
            PathSegmentF32.CubicTo(Point2F32((centerX + control * radiusX).toFloat(), (centerY - radiusY).toFloat()), Point2F32((centerX + radiusX).toFloat(), (centerY - control * radiusY).toFloat()), Point2F32((centerX + radiusX).toFloat(), centerY.toFloat())),
        )
        return result.takeIf { start == Point2F32((centerX + radiusX).toFloat(), centerY.toFloat()) && cubics == expected }
    }

    /** Returns the rounded rectangle described by the canonical [PathBuilder.addRRect] contour, if any. */
    public fun rrect(path: PathF32): RRectF32? {
        val values = path.toList()
        if (values.size != 10 || values[0] !is PathSegmentF32.MoveTo || values[9] != PathSegmentF32.Close ||
            (1..8).any { index -> if (index % 2 == 1) values[index] !is PathSegmentF32.LineTo else values[index] !is PathSegmentF32.ArcTo }
        ) return null
        val move = (values[0] as PathSegmentF32.MoveTo).point
        val lines = listOf(1, 3, 5, 7).map { values[it] as PathSegmentF32.LineTo }
        val arcs = listOf(2, 4, 6, 8).map { values[it] as PathSegmentF32.ArcTo }
        if (arcs.any { it.xAxisRotation != 0f || it.largeArc || !it.sweep || it.radius.x < 0f || it.radius.y < 0f }) return null
        val endpoints = listOf(move) + lines.map { it.point } + arcs.map { it.point }
        val bounds = RectF32(endpoints.minOf { it.x }, endpoints.minOf { it.y }, endpoints.maxOf { it.x }, endpoints.maxOf { it.y })
        if (bounds.isEmpty) return null
        val topLeft = arcs[3].radius.toCornerRadii()
        val topRight = arcs[0].radius.toCornerRadii()
        val bottomRight = arcs[1].radius.toCornerRadii()
        val bottomLeft = arcs[2].radius.toCornerRadii()
        val expected = listOf(
            move == Point2F32(bounds.left + topLeft.x, bounds.top),
            lines[0].point == Point2F32(bounds.right - topRight.x, bounds.top),
            arcs[0].point == Point2F32(bounds.right, bounds.top + topRight.y),
            lines[1].point == Point2F32(bounds.right, bounds.bottom - bottomRight.y),
            arcs[1].point == Point2F32(bounds.right - bottomRight.x, bounds.bottom),
            lines[2].point == Point2F32(bounds.left + bottomLeft.x, bounds.bottom),
            arcs[2].point == Point2F32(bounds.left, bounds.bottom - bottomLeft.y),
            lines[3].point == Point2F32(bounds.left, bounds.top + topLeft.y),
            arcs[3].point == Point2F32(bounds.left + topLeft.x, bounds.top),
        )
        return if (expected.all { it }) RRectF32(bounds, topLeft, topRight, bottomRight, bottomLeft) else null
    }

    /** Returns the only line segment in a path, if it has exactly one. */
    public fun line(path: PathF32): Line2F32? =
        if (path.segmentCount == 2 && path.segmentAt(0) is PathSegmentF32.MoveTo && path.segmentAt(1) is PathSegmentF32.LineTo) {
            Line2F32((path.segmentAt(0) as PathSegmentF32.MoveTo).point, (path.segmentAt(1) as PathSegmentF32.LineTo).point)
        } else null

    /** A single, linear contour is convex when all non-collinear turns have the same sign. */
    public fun isConvex(path: PathF32): Boolean {
        val normalization = pathNormalizationF64(listOf(path))
        val contours = PathFlattenerF64.flatten(NormalizedPathF64(path, normalization))
        if (contours.size != 1) return false
        val points = buildList {
            contours.single().points.map { it.point }.forEach { point -> if (isEmpty() || last() != point) add(point) }
        }.let { if (it.size > 1 && it.first() == it.last()) it.dropLast(1) else it }
        if (points.size < 3) return true
        var sign = 0.0
        for (index in points.indices) {
            val a = points[index]
            val b = points[(index + 1) % points.size]
            val c = points[(index + 2) % points.size]
            val cross = (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)
            if (cross != 0.0) {
                if (sign == 0.0) sign = cross else if (sign * cross < 0.0) return false
            }
        }
        return true
    }

    /** Paths interpolate when their command verbs have the same shape. */
    public fun isInterpolatable(first: PathF32, second: PathF32): Boolean =
        first.segmentCount == second.segmentCount && first.zip(second).all { (a, b) -> a::class == b::class }

    /** Summarizes contours, explicit closure, screen-space winding and inverse fill. */
    public fun topology(path: PathF32): PathTopologyI32 {
        val normalization = pathNormalizationF64(listOf(path))
        val contours = PathFlattenerF64.flatten(NormalizedPathF64(path, normalization))
        val directions = contours.filter { it.closed }.mapNotNull { contour ->
            val area = signedAreaF64(contour.points.map { it.point })
            when {
                area > 0.0 -> ContourOrientation.CLOCKWISE
                area < 0.0 -> ContourOrientation.COUNTER_CLOCKWISE
                else -> null
            }
        }.toSet()
        val orientation = when (directions.size) {
            0 -> ContourOrientation.UNDEFINED
            1 -> directions.single()
            else -> ContourOrientation.MIXED
        }
        return PathTopologyI32(contours.size, contours.count { it.closed }, orientation, path.fillRule.isInverse())
    }
}

private fun includeQuadExtrema(a: Point2F32, c: Point2F32, b: Point2F32, include: (Point2F32) -> Unit) {
    include(a)
    include(b)
    fun root(p0: Float, p1: Float, p2: Float): Float? {
        val denominator = p0 - 2f * p1 + p2
        return if (denominator != 0f) (p0 - p1) / denominator else null
    }
    fun point(t: Float): Point2F32 {
        val u = 1f - t
        return Point2F32(u * u * a.x + 2f * u * t * c.x + t * t * b.x, u * u * a.y + 2f * u * t * c.y + t * t * b.y)
    }
    listOfNotNull(root(a.x, c.x, b.x), root(a.y, c.y, b.y)).distinct().filter { it > 0f && it < 1f }.forEach { include(point(it)) }
}

private fun includeCubicExtrema(a: Point2F32, c1: Point2F32, c2: Point2F32, b: Point2F32, include: (Point2F32) -> Unit) {
    include(a)
    include(b)
    fun roots(p0: Float, p1: Float, p2: Float, p3: Float): List<Float> {
        val aa = (-p0 + 3f * p1 - 3f * p2 + p3).toDouble()
        val bb = (2f * (p0 - 2f * p1 + p2)).toDouble()
        val cc = (p1 - p0).toDouble()
        if (abs(aa) < 1e-12) return if (abs(bb) < 1e-12) emptyList() else listOf((-cc / bb).toFloat())
        val discriminant = bb * bb - 4.0 * aa * cc
        if (discriminant < 0.0) return emptyList()
        val root = sqrt(discriminant)
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

private fun signedAreaF64(points: List<Point2F64>): Double = points.zipWithNext().sumOf { (a, b) -> a.x * b.y - a.y * b.x } * 0.5

internal fun FillRule.isInverse(): Boolean = this == FillRule.INVERSE_WINDING || this == FillRule.INVERSE_EVEN_ODD

private fun org.graphiks.math.vector.Vector2F32.toCornerRadii(): CornerRadiiF32 = CornerRadiiF32.of(x, y)
