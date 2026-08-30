package org.graphiks.math.geometry

import org.graphiks.math.vector.Vector2F64
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
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
        var left = Double.POSITIVE_INFINITY
        var top = Double.POSITIVE_INFINITY
        var right = Double.NEGATIVE_INFINITY
        var bottom = Double.NEGATIVE_INFINITY
        fun include(point: Point2F64) {
            if (!point.isFinite()) return
            left = min(left, point.x)
            top = min(top, point.y)
            right = max(right, point.x)
            bottom = max(bottom, point.y)
        }
        var contourStart = Point2F64.Origin
        var current = Point2F64.Origin
        var started = false
        path.forEach { segment ->
            when (segment) {
                is PathSegmentF32.MoveTo -> {
                    current = segment.point.toPoint2F64()
                    contourStart = current
                    started = true
                    include(current)
                }
                is PathSegmentF32.LineTo -> if (started) {
                    include(current)
                    current = segment.point.toPoint2F64()
                    include(current)
                }
                is PathSegmentF32.QuadTo -> if (started) {
                    includeQuadExtrema(current, segment.control.toPoint2F64(), segment.point.toPoint2F64(), ::include)
                    current = segment.point.toPoint2F64()
                }
                is PathSegmentF32.CubicTo -> if (started) {
                    includeCubicExtrema(current, segment.control1.toPoint2F64(), segment.control2.toPoint2F64(), segment.point.toPoint2F64(), ::include)
                    current = segment.point.toPoint2F64()
                }
                is PathSegmentF32.ArcTo -> if (started) {
                    val arc = ArcEndpointF64(
                        current,
                        segment.point.toPoint2F64(),
                        Vector2F64(segment.radius.x.toDouble(), segment.radius.y.toDouble()),
                        segment.xAxisRotation.toDouble(),
                        segment.largeArc,
                        segment.sweep,
                    )
                    (arcCenterF64(arc)?.extrema() ?: listOf(arc.start, arc.end))
                        .forEach(::include)
                    current = segment.point.toPoint2F64()
                }
                PathSegmentF32.Close -> if (started) current = contourStart
            }
        }
        return if (left.isFinite()) RectF32(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat()) else null
    }

    /** Tests fill membership using the path's fill rule. Points on a boundary are outside. */
    public fun contains(path: PathF32, point: Point2F32): Boolean {
        if (!point.isFinite()) return false
        if (isSourceBoundaryF64(path, point.toPoint2F64())) return false
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
        val width = bounds.right.toDouble() - bounds.left.toDouble()
        val height = bounds.bottom.toDouble() - bounds.top.toDouble()
        if (topLeft.x.toDouble() + topRight.x.toDouble() > width || bottomLeft.x.toDouble() + bottomRight.x.toDouble() > width ||
            topLeft.y.toDouble() + bottomLeft.y.toDouble() > height || topRight.y.toDouble() + bottomRight.y.toDouble() > height
        ) return null
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
        var sign = 0
        for (index in points.indices) {
            val a = points[index]
            val b = points[(index + 1) % points.size]
            val c = points[(index + 2) % points.size]
            val turn = OrientationPredicateF64.sign(a, b, c)
            if (turn != 0) {
                if (sign == 0) sign = turn else if (sign != turn) return false
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
            when (signedAreaSignF64(contour.points.map { it.point })) {
                1 -> ContourOrientation.CLOCKWISE
                -1 -> ContourOrientation.COUNTER_CLOCKWISE
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

private fun isSourceBoundaryF64(path: PathF32, point: Point2F64): Boolean {
    var contourStart = Point2F64.Origin
    var current = Point2F64.Origin
    var started = false
    path.forEach { segment ->
        if (when (segment) {
                is PathSegmentF32.MoveTo -> {
                    current = segment.point.toPoint2F64()
                    contourStart = current
                    started = true
                    false
                }
                is PathSegmentF32.LineTo -> if (started) {
                    val end = segment.point.toPoint2F64()
                    val result = PathPredicatesF64.onSegment(point, current, end)
                    current = end
                    result
                } else false
                is PathSegmentF32.QuadTo -> if (started) {
                    val end = segment.point.toPoint2F64()
                    val result = pointOnQuadF64(point, current, segment.control.toPoint2F64(), end)
                    current = end
                    result
                } else false
                is PathSegmentF32.CubicTo -> if (started) {
                    val end = segment.point.toPoint2F64()
                    val result = pointOnCubicF64(point, current, segment.control1.toPoint2F64(), segment.control2.toPoint2F64(), end)
                    current = end
                    result
                } else false
                is PathSegmentF32.ArcTo -> if (started) {
                    val end = segment.point.toPoint2F64()
                    val arc = ArcEndpointF64(
                        current,
                        end,
                        Vector2F64(segment.radius.x.toDouble(), segment.radius.y.toDouble()),
                        segment.xAxisRotation.toDouble(),
                        segment.largeArc,
                        segment.sweep,
                    )
                    val result = pointOnArcF64(point, arc)
                    current = end
                    result
                } else false
                PathSegmentF32.Close -> if (started) {
                    val result = PathPredicatesF64.onSegment(point, current, contourStart)
                    current = contourStart
                    result
                } else false
            }
        ) return true
    }
    return false
}

private fun pointOnQuadF64(point: Point2F64, start: Point2F64, control: Point2F64, end: Point2F64): Boolean {
    if (start == point || end == point) return true
    val candidatesX = quadraticRootsF64(start.x - 2.0 * control.x + end.x, 2.0 * (control.x - start.x), start.x - point.x)
    val candidatesY = quadraticRootsF64(start.y - 2.0 * control.y + end.y, 2.0 * (control.y - start.y), start.y - point.y)
    val candidates = (candidatesX + candidatesY).distinct()
    return candidates.any { t ->
        t in 0.0..1.0 && quadPointMatchesF64(point, start, control, end, t)
    }
}

private fun pointOnCubicF64(point: Point2F64, start: Point2F64, control1: Point2F64, control2: Point2F64, end: Point2F64): Boolean {
    if (start == point || end == point) return true
    val xCoordinates = doubleArrayOf(start.x, control1.x, control2.x, end.x)
    val yCoordinates = doubleArrayOf(start.y, control1.y, control2.y, end.y)
    val candidatesX = cubicRootCandidatesF64(
        -start.x + 3.0 * control1.x - 3.0 * control2.x + end.x,
        3.0 * (start.x - 2.0 * control1.x + control2.x),
        3.0 * (control1.x - start.x),
        start.x - point.x,
    )
    val candidatesY = cubicRootCandidatesF64(
        -start.y + 3.0 * control1.y - 3.0 * control2.y + end.y,
        3.0 * (start.y - 2.0 * control1.y + control2.y),
        3.0 * (control1.y - start.y),
        start.y - point.y,
    )

    fun matches(t: Double, xCritical: Boolean, yCritical: Boolean): Boolean =
        cubicPointMatchesF64(point, xCoordinates, yCoordinates, t, xCritical, yCritical)

    if (candidatesX.unconstrained && candidatesY.unconstrained) return false
    if (candidatesX.unconstrained) {
        return candidatesY.values.any { candidate ->
            matches(candidate.parameter, xCritical = false, yCritical = candidate.critical)
        }
    }
    if (candidatesY.unconstrained) {
        return candidatesX.values.any { candidate ->
            matches(candidate.parameter, xCritical = candidate.critical, yCritical = false)
        }
    }
    return candidatesX.values.any { xCandidate ->
        candidatesY.values.any { yCandidate ->
            PathPredicatesF64.almostEqualUlps(xCandidate.parameter, yCandidate.parameter, maxUlps = 32) &&
                matches(
                    (xCandidate.parameter + yCandidate.parameter) * 0.5,
                    xCritical = xCandidate.critical,
                    yCritical = yCandidate.critical,
                )
        }
    }
}

private fun pointOnArcF64(point: Point2F64, arc: ArcEndpointF64): Boolean {
    if (arc.start == point || arc.end == point) return true
    val center = arcCenterF64(arc) ?: return PathPredicatesF64.onSegment(point, arc.start, arc.end)
    val deltaX = point.x - center.center.x
    val deltaY = point.y - center.center.y
    val cosRotation = cos(center.rotationRadians)
    val sinRotation = sin(center.rotationRadians)
    val localX = cosRotation * deltaX + sinRotation * deltaY
    val localY = -sinRotation * deltaX + cosRotation * deltaY
    val angle = atan2(localY / center.radiusY, localX / center.radiusX)
    val distance = if (center.sweepAngle >= 0.0) {
        positiveAngleForBoundaryF64(angle - center.startAngle)
    } else {
        positiveAngleForBoundaryF64(center.startAngle - angle)
    }
    if (distance > abs(center.sweepAngle)) return false
    val signedDistance = if (center.sweepAngle >= 0.0) distance else -distance
    val t = if (center.sweepAngle == 0.0) 0.0 else signedDistance / center.sweepAngle
    return t in 0.0..1.0 && arcPointMatchesF64(center, point, t)
}

private fun quadraticRootsF64(a: Double, b: Double, c: Double): List<Double> {
    if (a == 0.0) return if (b == 0.0) emptyList() else listOf(-c / b)
    val discriminant = b * b - 4.0 * a * c
    if (discriminant < 0.0) return emptyList()
    val root = sqrt(discriminant)
    val q = -0.5 * (b + if (b >= 0.0) root else -root)
    return if (q == 0.0) listOf(-b / (2.0 * a)) else listOf(q / a, c / q)
}

private data class CubicRootCandidateF64(val parameter: Double, val critical: Boolean)

private data class CubicRootCandidatesF64(val values: List<CubicRootCandidateF64>, val unconstrained: Boolean)

private fun cubicRootCandidatesF64(a: Double, b: Double, c: Double, d: Double): CubicRootCandidatesF64 {
    if (a == 0.0) {
        if (b == 0.0 && c == 0.0) {
            return CubicRootCandidatesF64(emptyList(), unconstrained = d == 0.0)
        }
        return CubicRootCandidatesF64(
            quadraticRootsF64(b, c, d).filter { it in 0.0..1.0 }.map { CubicRootCandidateF64(it, critical = false) },
            unconstrained = false,
        )
    }
    fun valueAt(t: Double): Double = ((a * t + b) * t + c) * t + d

    val critical = quadraticRootsF64(3.0 * a, 2.0 * b, c).filter { it in 0.0..1.0 }.sorted()
    val partitions = buildList {
        add(0.0)
        addAll(critical)
        add(1.0)
    }.distinct()
    val roots = critical.map { CubicRootCandidateF64(it, critical = true) }.toMutableList()
    partitions.zipWithNext().forEach { (start, end) ->
        var low = start
        var high = end
        var lowValue = valueAt(low)
        val highValue = valueAt(high)
        if (lowValue == 0.0 || highValue == 0.0 || (lowValue < 0.0) == (highValue < 0.0)) return@forEach
        repeat(80) {
            val middle = (low + high) * 0.5
            val middleValue = valueAt(middle)
            if (middleValue == 0.0) {
                low = middle
                high = middle
                return@repeat
            }
            if ((lowValue < 0.0) == (middleValue < 0.0)) {
                low = middle
                lowValue = middleValue
            } else {
                high = middle
            }
        }
        roots += CubicRootCandidateF64((low + high) * 0.5, critical = false)
    }
    return CubicRootCandidatesF64(roots.distinctBy { it.parameter }, unconstrained = false)
}

private fun quadPointMatchesF64(point: Point2F64, start: Point2F64, control: Point2F64, end: Point2F64, t: Double): Boolean {
    val u = 1.0 - t
    val weights = doubleArrayOf(u * u, 2.0 * u * t, t * t)
    return bezierPointMatchesF64(
        point,
        doubleArrayOf(start.x, control.x, end.x),
        doubleArrayOf(start.y, control.y, end.y),
        weights,
        errorFactor = 16.0,
    )
}

private fun cubicPointMatchesF64(
    point: Point2F64,
    xCoordinates: DoubleArray,
    yCoordinates: DoubleArray,
    t: Double,
    xCritical: Boolean,
    yCritical: Boolean,
): Boolean {
    val weights = cubicBernsteinWeightsF64(t)
    return cubicCoordinateMatchesF64(xCoordinates, point.x, weights, xCritical) &&
        cubicCoordinateMatchesF64(yCoordinates, point.y, weights, yCritical)
}

private fun cubicCoordinateMatchesF64(
    coordinates: DoubleArray,
    target: Double,
    weights: Array<DoubleArray>,
    critical: Boolean,
): Boolean {
    var residual = doubleArrayOf()
    coordinates.indices.forEach { index ->
        val difference = ExpansionF64.twoDiff(coordinates[index], target)
        val weightedDifference = ExpansionF64.product(difference, weights[index])
        residual = ExpansionF64.expansionSum(residual, weightedDifference)
    }
    val coordinateScale = coordinates.maxOf(::abs)
    val errorBound = if (critical) {
        128.0 * PathPredicatesF64.EPSILON_F64 * PathPredicatesF64.EPSILON_F64 * coordinateScale
    } else {
        64.0 * PathPredicatesF64.EPSILON_F64 * coordinateScale
    }
    return abs(residual.sum()) <= errorBound
}

private fun cubicBernsteinWeightsF64(t: Double): Array<DoubleArray> {
    val tExpansion = doubleArrayOf(t)
    val uExpansion = ExpansionF64.twoDiff(1.0, t)
    val uSquared = ExpansionF64.product(uExpansion, uExpansion)
    val tSquared = ExpansionF64.product(tExpansion, tExpansion)
    val three = doubleArrayOf(3.0)
    return arrayOf(
        ExpansionF64.product(uSquared, uExpansion),
        ExpansionF64.product(ExpansionF64.product(three, uSquared), tExpansion),
        ExpansionF64.product(ExpansionF64.product(three, uExpansion), tSquared),
        ExpansionF64.product(tSquared, tExpansion),
    )
}

private fun bezierPointMatchesF64(
    point: Point2F64,
    xCoordinates: DoubleArray,
    yCoordinates: DoubleArray,
    weights: DoubleArray,
    errorFactor: Double,
): Boolean {
    fun residualMatches(coordinates: DoubleArray, target: Double): Boolean {
        val terms = DoubleArray(coordinates.size) { index -> weights[index] * (coordinates[index] - target) }
        val residual = terms.sum()
        val magnitude = terms.sumOf(::abs)
        return abs(residual) <= errorFactor * PathPredicatesF64.EPSILON_F64 * magnitude
    }
    return residualMatches(xCoordinates, point.x) && residualMatches(yCoordinates, point.y)
}

private fun arcPointMatchesF64(center: ArcCenterF64, point: Point2F64, t: Double): Boolean {
    val angle = center.startAngle + center.sweepAngle * t
    val cosAngle = cos(angle)
    val sinAngle = sin(angle)
    val cosRotation = cos(center.rotationRadians)
    val sinRotation = sin(center.rotationRadians)
    fun residualMatches(vararg terms: Double): Boolean =
        abs(terms.sum()) <= 64.0 * PathPredicatesF64.EPSILON_F64 * terms.sumOf(::abs)
    return residualMatches(
        center.center.x - point.x,
        center.radiusX * cosRotation * cosAngle,
        -center.radiusY * sinRotation * sinAngle,
    ) && residualMatches(
        center.center.y - point.y,
        center.radiusX * sinRotation * cosAngle,
        center.radiusY * cosRotation * sinAngle,
    )
}

private fun positiveAngleForBoundaryF64(angle: Double): Double {
    val result = angle % (2.0 * kotlin.math.PI)
    return if (result < 0.0) result + 2.0 * kotlin.math.PI else result
}

private fun includeQuadExtrema(a: Point2F64, c: Point2F64, b: Point2F64, include: (Point2F64) -> Unit) {
    include(a)
    include(b)
    fun root(p0: Double, p1: Double, p2: Double): Double? {
        val denominator = p0 - 2.0 * p1 + p2
        return if (denominator != 0.0) (p0 - p1) / denominator else null
    }
    fun point(t: Double): Point2F64 {
        val u = 1.0 - t
        return Point2F64(u * u * a.x + 2.0 * u * t * c.x + t * t * b.x, u * u * a.y + 2.0 * u * t * c.y + t * t * b.y)
    }
    listOfNotNull(root(a.x, c.x, b.x), root(a.y, c.y, b.y)).distinct().filter { it > 0.0 && it < 1.0 }.forEach { include(point(it)) }
}

private fun includeCubicExtrema(a: Point2F64, c1: Point2F64, c2: Point2F64, b: Point2F64, include: (Point2F64) -> Unit) {
    include(a)
    include(b)
    fun roots(p0: Double, p1: Double, p2: Double, p3: Double): List<Double> {
        val aa = -p0 + 3.0 * p1 - 3.0 * p2 + p3
        val bb = 2.0 * (p0 - 2.0 * p1 + p2)
        val cc = p1 - p0
        if (aa == 0.0) return if (bb == 0.0) emptyList() else listOf(-cc / bb)
        val discriminant = bb * bb - 4.0 * aa * cc
        if (discriminant < 0.0) return emptyList()
        val root = sqrt(discriminant)
        val q = -0.5 * (bb + if (bb >= 0.0) root else -root)
        return if (q == 0.0) listOf(-bb / (2.0 * aa)) else listOf(q / aa, cc / q)
    }
    fun point(t: Double): Point2F64 {
        val u = 1.0 - t
        return Point2F64(
            u * u * u * a.x + 3.0 * u * u * t * c1.x + 3.0 * u * t * t * c2.x + t * t * t * b.x,
            u * u * u * a.y + 3.0 * u * u * t * c1.y + 3.0 * u * t * t * c2.y + t * t * t * b.y,
        )
    }
    (roots(a.x, c1.x, c2.x, b.x) + roots(a.y, c1.y, c2.y, b.y)).distinct().filter { it > 0.0 && it < 1.0 }.forEach { include(point(it)) }
}

/** Returns an exact expansion sign for the contour's signed area. */
internal fun signedAreaSignF64(points: List<Point2F64>): Int {
    var exactSum = doubleArrayOf()
    points.zipWithNext().forEach { (first, second) ->
        val cross = ExpansionF64.expansionDiff(
            ExpansionF64.twoProduct(first.x, second.y),
            ExpansionF64.twoProduct(first.y, second.x),
        )
        exactSum = ExpansionF64.expansionSum(exactSum, cross)
    }
    return ExpansionF64.sign(exactSum)
}

internal fun FillRule.isInverse(): Boolean = this == FillRule.INVERSE_WINDING || this == FillRule.INVERSE_EVEN_ODD

private fun org.graphiks.math.vector.Vector2F32.toCornerRadii(): CornerRadiiF32 = CornerRadiiF32.of(x, y)
