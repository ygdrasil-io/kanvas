package org.graphiks.math.geometry

import org.graphiks.math.vector.Vector2F64
import kotlin.math.max
import kotlin.math.min

/** A point on a measured path and its normalized tangent. */
public data class PathLocationF32(public val point: Point2F32, public val tangent: Point2F32)

/** Measures one contour of an immutable [PathF32] at a time. */
public class PathMeasureF32(path: PathF32, forceClosed: Boolean = false) {
    private val normalization = pathNormalizationF64(listOf(path))
    private val sourceGeometry = sourceGeometryF64(path)

    private val contours: List<MeasuredContourF64> = PathFlattenerF64
        .flatten(NormalizedPathF64(path, normalization))
        .map { contour ->
            val segments = contour.points.zipWithNext().map { (first, second) ->
                MeasuredSegmentF64(first, second, sourceGeometry[second.sourceSegmentIndex], normalization.scale)
            }.toMutableList()
            if ((contour.closed || forceClosed) && contour.points.size > 1 && contour.points.last().point != contour.points.first().point) {
                segments += MeasuredSegmentF64(contour.points.last(), contour.points.first(), null, normalization.scale)
            }
            MeasuredContourF64(segments, contour.closed || forceClosed)
        }
    private var contourIndex = 0

    /** Length of the current contour, or zero when there is none. */
    public val length: Float get() = roundedF32(contours.getOrNull(contourIndex)?.length ?: 0.0)

    /** Whether the current contour has an explicit or forced closing edge. */
    public val isClosed: Boolean get() = contours.getOrNull(contourIndex)?.closed ?: false

    /** Clamps [distance] to the current contour and returns its point and normalized tangent. */
    public fun position(distance: Float): PathLocationF32? = positionF64(distance.toDouble())

    private fun positionF64(distance: Double): PathLocationF32? {
        val contour = contours.getOrNull(contourIndex) ?: return null
        if (contour.segments.isEmpty()) return null
        var remaining = clampDistanceF64(distance, contour.length)
        contour.segments.forEachIndexed { index, segment ->
            if (remaining <= segment.length || index == contour.segments.lastIndex) return segment.at(remaining, normalization)
            remaining -= segment.length
        }
        return null
    }

    /** Returns a line representation of the requested current-contour interval. */
    public fun segment(startDistance: Float, stopDistance: Float, startWithMoveTo: Boolean = true): PathF32? {
        val contour = contours.getOrNull(contourIndex) ?: return null
        val firstDistance = clampDistanceF64(startDistance.toDouble(), contour.length)
        val secondDistance = clampDistanceF64(stopDistance.toDouble(), contour.length)
        val start = min(firstDistance, secondDistance)
        val stop = max(firstDistance, secondDistance)
        if (start >= stop) return null
        val startLocation = positionF64(start) ?: return null
        val stopLocation = positionF64(stop) ?: return null
        val builder = PathBuilder()
        if (startWithMoveTo) builder.moveTo(startLocation.point.x, startLocation.point.y)
        return builder.lineTo(stopLocation.point.x, stopLocation.point.y).build()
    }

    /** Selects the next contour, returning false when already on the final contour. */
    public fun nextContour(): Boolean = if (contourIndex + 1 < contours.size) {
        contourIndex += 1
        true
    } else false
}

private data class MeasuredSegmentF64(
    val first: FlattenedPointF64,
    val second: FlattenedPointF64,
    val geometry: SourceGeometryF64?,
    val normalizationScale: Double,
) {
    val length: Double = stableHypotF64(second.point.x - first.point.x, second.point.y - first.point.y) / normalizationScale

    fun at(distance: Double, normalization: PathNormalizationF64): PathLocationF32 {
        val fraction = if (length == 0.0) 0.0 else (distance / length).coerceIn(0.0, 1.0)
        val point = Point2F64(
            first.point.x + (second.point.x - first.point.x) * fraction,
            first.point.y + (second.point.y - first.point.y) * fraction,
        )
        val t = first.t + (second.t - first.t) * fraction
        val derivative = geometry?.derivativeAt(t) ?: Vector2F64(second.point.x - first.point.x, second.point.y - first.point.y)
        val tangentLength = stableHypotF64(derivative.x, derivative.y)
        val tangent = if (tangentLength == 0.0) Point2F32(0f, 0f) else Point2F32(
            (derivative.x / tangentLength).toFloat(),
            (derivative.y / tangentLength).toFloat(),
        )
        return PathLocationF32(normalization.denormalize(point), tangent)
    }
}

private data class MeasuredContourF64(
    val segments: List<MeasuredSegmentF64>,
    val closed: Boolean,
) {
    val length: Double = segments.sumOf { it.length }
}

private sealed interface SourceGeometryF64 {
    fun derivativeAt(t: Double): Vector2F64
}

private data class LineGeometryF64(val start: Point2F64, val end: Point2F64) : SourceGeometryF64 {
    override fun derivativeAt(t: Double): Vector2F64 = end - start
}

private data class QuadGeometryF64(val start: Point2F64, val control: Point2F64, val end: Point2F64) : SourceGeometryF64 {
    override fun derivativeAt(t: Double): Vector2F64 {
        val u = 1.0 - t
        return Vector2F64(
            2.0 * u * (control.x - start.x) + 2.0 * t * (end.x - control.x),
            2.0 * u * (control.y - start.y) + 2.0 * t * (end.y - control.y),
        )
    }
}

private data class CubicGeometryF64(val start: Point2F64, val control1: Point2F64, val control2: Point2F64, val end: Point2F64) : SourceGeometryF64 {
    override fun derivativeAt(t: Double): Vector2F64 {
        val u = 1.0 - t
        return Vector2F64(
            3.0 * u * u * (control1.x - start.x) + 6.0 * u * t * (control2.x - control1.x) + 3.0 * t * t * (end.x - control2.x),
            3.0 * u * u * (control1.y - start.y) + 6.0 * u * t * (control2.y - control1.y) + 3.0 * t * t * (end.y - control2.y),
        )
    }
}

private data class ArcGeometryF64(val arc: ArcCenterF64?, val start: Point2F64, val end: Point2F64) : SourceGeometryF64 {
    override fun derivativeAt(t: Double): Vector2F64 = arc?.derivativeAt(t) ?: end - start
}

private fun sourceGeometryF64(path: PathF32): Map<Int, SourceGeometryF64> {
    val geometry = mutableMapOf<Int, SourceGeometryF64>()
    var contourStart = Point2F64.Origin
    var current = Point2F64.Origin
    var started = false
    path.forEachIndexed { index, segment ->
        when (segment) {
            is PathSegmentF32.MoveTo -> {
                current = segment.point.toPoint2F64()
                contourStart = current
                started = true
            }
            is PathSegmentF32.LineTo -> if (started) {
                val end = segment.point.toPoint2F64()
                geometry[index] = LineGeometryF64(current, end)
                current = end
            }
            is PathSegmentF32.QuadTo -> if (started) {
                val end = segment.point.toPoint2F64()
                geometry[index] = QuadGeometryF64(current, segment.control.toPoint2F64(), end)
                current = end
            }
            is PathSegmentF32.CubicTo -> if (started) {
                val end = segment.point.toPoint2F64()
                geometry[index] = CubicGeometryF64(current, segment.control1.toPoint2F64(), segment.control2.toPoint2F64(), end)
                current = end
            }
            is PathSegmentF32.ArcTo -> if (started) {
                val end = segment.point.toPoint2F64()
                val endpoint = ArcEndpointF64(
                    current,
                    end,
                    Vector2F64(segment.radius.x.toDouble(), segment.radius.y.toDouble()),
                    segment.xAxisRotation.toDouble(),
                    segment.largeArc,
                    segment.sweep,
                )
                geometry[index] = ArcGeometryF64(arcCenterF64(endpoint), current, end)
                current = end
            }
            PathSegmentF32.Close -> if (started) {
                geometry[index] = LineGeometryF64(current, contourStart)
                current = contourStart
            }
        }
    }
    return geometry
}

private fun clampDistanceF64(distance: Double, length: Double): Double = when {
    distance.isNaN() || distance <= 0.0 -> 0.0
    distance == Double.POSITIVE_INFINITY -> length
    else -> distance.coerceIn(0.0, length)
}

private fun roundedF32(value: Double): Float = Float.fromBits(value.toFloat().toRawBits())
