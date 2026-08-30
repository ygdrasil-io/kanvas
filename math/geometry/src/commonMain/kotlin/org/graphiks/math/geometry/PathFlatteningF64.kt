package org.graphiks.math.geometry

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

internal data class PathFlatteningPolicyF64(
    val tolerance: Double = 2.0.pow(-23),
    val limits: PathOpsLimitsI32 = PathOpsLimitsI32(),
) {
    init {
        require(tolerance.isFinite() && tolerance > 0.0)
    }
}

internal data class FlattenedPointF64(
    val point: Point2F64,
    val sourceSegmentIndex: Int,
    val t: Double,
    val originalPointF32: Point2F32?,
)

internal data class FlattenedContourF64(
    val points: List<FlattenedPointF64>,
    val closed: Boolean,
)

internal object PathFlattenerF64 {
    fun flatten(
        normalizedPath: NormalizedPathF64,
        policy: PathFlatteningPolicyF64 = PathFlatteningPolicyF64(),
        closeForFill: Boolean = false,
    ): List<FlattenedContourF64> {
        val result = mutableListOf<FlattenedContourF64>()
        val normalization = normalizedPath.normalization
        var points = mutableListOf<FlattenedPointF64>()
        var contourStart = Point2F32.Origin
        var current = Point2F32.Origin
        var started = false
        var closed = false
        var emittedEdges = 0

        fun add(point: Point2F64, sourceSegmentIndex: Int, t: Double, originalPoint: Point2F32?) {
            points += FlattenedPointF64(point, sourceSegmentIndex, t, originalPoint)
        }
        fun incrementEdges() {
            emittedEdges += 1
            if (emittedEdges > policy.limits.maxFlattenedEdgesPerOperand) throw IllegalStateException("path-flattening-limit")
        }
        fun finish() {
            if (!started) return
            if (closeForFill && !closed && points.size > 1 && points.last().point != points.first().point) {
                incrementEdges()
                add(normalization.normalize(contourStart), -1, 1.0, contourStart)
            }
            result += FlattenedContourF64(points.toList(), closed)
        }
        fun beginSegment(sourceSegmentIndex: Int) {
            add(normalization.normalize(current), sourceSegmentIndex, 0.0, current)
        }
        fun flattenQuad(start: Point2F64, control: Point2F64, end: Point2F64, sourceSegmentIndex: Int) {
            fun recurse(a: Point2F64, c: Point2F64, b: Point2F64, t0: Double, t1: Double, depth: Int) {
                if (pointToSegmentDistanceF64(c, a, b) <= policy.tolerance) {
                    incrementEdges()
                    add(b, sourceSegmentIndex, t1, if (t1 == 1.0) current else null)
                    return
                }
                if (depth >= policy.limits.maxSubdivisionDepth) throw IllegalStateException("path-flattening-convergence")
                val ac = midpointF64(a, c)
                val cb = midpointF64(c, b)
                val middle = midpointF64(ac, cb)
                val split = (t0 + t1) * 0.5
                recurse(a, ac, middle, t0, split, depth + 1)
                recurse(middle, cb, b, split, t1, depth + 1)
            }
            recurse(start, control, end, 0.0, 1.0, 0)
        }
        fun flattenCubic(start: Point2F64, control1: Point2F64, control2: Point2F64, end: Point2F64, sourceSegmentIndex: Int) {
            fun recurse(a: Point2F64, c1: Point2F64, c2: Point2F64, b: Point2F64, t0: Double, t1: Double, depth: Int) {
                if (max(pointToSegmentDistanceF64(c1, a, b), pointToSegmentDistanceF64(c2, a, b)) <= policy.tolerance) {
                    incrementEdges()
                    add(b, sourceSegmentIndex, t1, if (t1 == 1.0) current else null)
                    return
                }
                if (depth >= policy.limits.maxSubdivisionDepth) throw IllegalStateException("path-flattening-convergence")
                val a1 = midpointF64(a, c1)
                val a2 = midpointF64(c1, c2)
                val a3 = midpointF64(c2, b)
                val b1 = midpointF64(a1, a2)
                val b2 = midpointF64(a2, a3)
                val middle = midpointF64(b1, b2)
                val split = (t0 + t1) * 0.5
                recurse(a, a1, b1, middle, t0, split, depth + 1)
                recurse(middle, b2, a3, b, split, t1, depth + 1)
            }
            recurse(start, control1, control2, end, 0.0, 1.0, 0)
        }
        fun flattenArc(arc: ArcCenterF64, end: Point2F64, sourceSegmentIndex: Int) {
            fun recurse(a: Point2F64, b: Point2F64, t0: Double, t1: Double, depth: Int) {
                val split = (t0 + t1) * 0.5
                val middle = arc.pointAt(split)
                if (pointToSegmentDistanceF64(middle, a, b) <= policy.tolerance) {
                    incrementEdges()
                    add(if (t1 == 1.0) end else b, sourceSegmentIndex, t1, if (t1 == 1.0) current else null)
                    return
                }
                if (depth >= policy.limits.maxSubdivisionDepth) throw IllegalStateException("path-flattening-convergence")
                recurse(a, middle, t0, split, depth + 1)
                recurse(middle, b, split, t1, depth + 1)
            }
            recurse(arc.pointAt(0.0), end, 0.0, 1.0, 0)
        }

        normalizedPath.path.forEachIndexed { index, segment ->
            when (segment) {
                is PathSegmentF32.MoveTo -> {
                    finish()
                    points = mutableListOf()
                    contourStart = segment.point
                    current = segment.point
                    started = true
                    closed = false
                    add(normalization.normalize(current), index, 0.0, current)
                }
                is PathSegmentF32.LineTo -> if (started) {
                    beginSegment(index)
                    incrementEdges()
                    current = segment.point
                    add(normalization.normalize(current), index, 1.0, current)
                }
                is PathSegmentF32.QuadTo -> if (started) {
                    beginSegment(index)
                    val start = normalization.normalize(current)
                    val control = normalization.normalize(segment.control)
                    current = segment.point
                    flattenQuad(start, control, normalization.normalize(current), index)
                }
                is PathSegmentF32.CubicTo -> if (started) {
                    beginSegment(index)
                    val start = normalization.normalize(current)
                    val control1 = normalization.normalize(segment.control1)
                    val control2 = normalization.normalize(segment.control2)
                    current = segment.point
                    flattenCubic(start, control1, control2, normalization.normalize(current), index)
                }
                is PathSegmentF32.ArcTo -> if (started) {
                    beginSegment(index)
                    val start = normalization.normalize(current)
                    current = segment.point
                    val end = normalization.normalize(current)
                    val arc = ArcEndpointF64(
                        start = start,
                        end = end,
                        radius = normalization.normalizeVector(segment.radius),
                        xAxisRotationDegrees = segment.xAxisRotation.toDouble(),
                        largeArc = segment.largeArc,
                        sweep = segment.sweep,
                    )
                    val center = arcCenterF64(arc)
                    if (center == null) {
                        incrementEdges()
                        add(end, index, 1.0, current)
                    } else {
                        flattenArc(center, end, index)
                    }
                }
                PathSegmentF32.Close -> if (started) {
                    if (current != contourStart) {
                        incrementEdges()
                        current = contourStart
                        add(normalization.normalize(current), index, 1.0, current)
                    }
                    current = contourStart
                    closed = true
                }
            }
        }
        finish()
        return result
    }
}

internal fun stableHypotF64(x: Double, y: Double): Double {
    val scale = max(abs(x), abs(y))
    return if (scale == 0.0) 0.0 else scale * sqrt((x / scale) * (x / scale) + (y / scale) * (y / scale))
}

private fun midpointF64(first: Point2F64, second: Point2F64): Point2F64 = Point2F64(
    first.x * 0.5 + second.x * 0.5,
    first.y * 0.5 + second.y * 0.5,
)

private fun pointToSegmentDistanceF64(point: Point2F64, start: Point2F64, end: Point2F64): Double {
    val deltaX = end.x - start.x
    val deltaY = end.y - start.y
    val lengthSquared = deltaX * deltaX + deltaY * deltaY
    if (lengthSquared == 0.0) return stableHypotF64(point.x - start.x, point.y - start.y)
    val projection = (((point.x - start.x) * deltaX + (point.y - start.y) * deltaY) / lengthSquared).coerceIn(0.0, 1.0)
    return stableHypotF64(point.x - (start.x + projection * deltaX), point.y - (start.y + projection * deltaY))
}
