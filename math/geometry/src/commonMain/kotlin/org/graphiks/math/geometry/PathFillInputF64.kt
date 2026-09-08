package org.graphiks.math.geometry

import org.graphiks.math.vector.Vector2F64
import kotlin.math.min

/** An immutable device-space path command represented with F64 coordinates. */
public sealed interface PathFillSegmentF64 {
    public data class MoveTo(public val point: Point2F64) : PathFillSegmentF64

    public data class LineTo(public val point: Point2F64) : PathFillSegmentF64

    public data class QuadTo(
        public val control: Point2F64,
        public val point: Point2F64,
    ) : PathFillSegmentF64

    public data class CubicTo(
        public val control1: Point2F64,
        public val control2: Point2F64,
        public val point: Point2F64,
    ) : PathFillSegmentF64

    public data class ArcTo(
        public val radius: Vector2F64,
        public val xAxisRotationDegreesF64: Double,
        public val largeArc: Boolean,
        public val sweep: Boolean,
        public val point: Point2F64,
    ) : PathFillSegmentF64

    public data object Close : PathFillSegmentF64
}

/** Builds the canonical closed F64 contour without narrowing source coordinates to F32. */
public fun RectF64.toPathFillInputF64(): PathFillInputF64 = materializePathFillInputF64(beforeMaterializationI64 = null)

/** Builds a canonical Rect path while admitting every collection and command before allocation. */
public fun RectF64.toPathFillInputF64(
    beforeMaterializationI64: (PathStrokeWorkUsageI64) -> Unit,
): PathFillInputF64 = materializePathFillInputF64(beforeMaterializationI64)

private fun RectF64.materializePathFillInputF64(
    beforeMaterializationI64: ((PathStrokeWorkUsageI64) -> Unit)?,
): PathFillInputF64 {
    debitPathInputCollectionBeforeMaterializationI64(beforeMaterializationI64)
    val segmentsF64 = ArrayList<PathFillSegmentF64>(5)
    debitPathInputSegmentBeforeMaterializationI64(beforeMaterializationI64)
    segmentsF64 += PathFillSegmentF64.MoveTo(Point2F64(left, top))
    debitPathInputSegmentBeforeMaterializationI64(beforeMaterializationI64)
    segmentsF64 += PathFillSegmentF64.LineTo(Point2F64(right, top))
    debitPathInputSegmentBeforeMaterializationI64(beforeMaterializationI64)
    segmentsF64 += PathFillSegmentF64.LineTo(Point2F64(right, bottom))
    debitPathInputSegmentBeforeMaterializationI64(beforeMaterializationI64)
    segmentsF64 += PathFillSegmentF64.LineTo(Point2F64(left, bottom))
    debitPathInputSegmentBeforeMaterializationI64(beforeMaterializationI64)
    segmentsF64 += PathFillSegmentF64.Close
    debitPathInputCollectionBeforeMaterializationI64(beforeMaterializationI64)
    return PathFillInputF64.of(FillRule.WINDING, segmentsF64)
}

/** Builds the canonical rounded-rectangle contour with F64 endpoints and arc radii. */
public fun RRectF64.toPathFillInputF64(): PathFillInputF64 = materializePathFillInputF64(beforeMaterializationI64 = null)

/** Builds a canonical rounded-rectangle path while admitting every collection and command before allocation. */
public fun RRectF64.toPathFillInputF64(
    beforeMaterializationI64: (PathStrokeWorkUsageI64) -> Unit,
): PathFillInputF64 = materializePathFillInputF64(beforeMaterializationI64)

private fun RRectF64.materializePathFillInputF64(
    beforeMaterializationI64: ((PathStrokeWorkUsageI64) -> Unit)?,
): PathFillInputF64 {
    debitPathInputCollectionBeforeMaterializationI64(beforeMaterializationI64)
    val segmentsF64 = ArrayList<PathFillSegmentF64>(10)
    debitPathInputSegmentBeforeMaterializationI64(beforeMaterializationI64)
    val left = leftF64
    val top = topF64
    val right = rightF64
    val bottom = bottomF64
    val scale = normalizedRadiiScaleF64(this, left, top, right, bottom)
    val topLeftX = topLeft.xF64.coerceAtLeast(0.0) * scale
    val topLeftY = topLeft.yF64.coerceAtLeast(0.0) * scale
    val topRightX = topRight.xF64.coerceAtLeast(0.0) * scale
    val topRightY = topRight.yF64.coerceAtLeast(0.0) * scale
    val bottomRightX = bottomRight.xF64.coerceAtLeast(0.0) * scale
    val bottomRightY = bottomRight.yF64.coerceAtLeast(0.0) * scale
    val bottomLeftX = bottomLeft.xF64.coerceAtLeast(0.0) * scale
    val bottomLeftY = bottomLeft.yF64.coerceAtLeast(0.0) * scale
    segmentsF64 += PathFillSegmentF64.MoveTo(Point2F64(left + topLeftX, top))
    debitPathInputSegmentBeforeMaterializationI64(beforeMaterializationI64)
    segmentsF64 += PathFillSegmentF64.LineTo(Point2F64(right - topRightX, top))
    debitPathInputSegmentBeforeMaterializationI64(beforeMaterializationI64)
    segmentsF64 += PathFillSegmentF64.ArcTo(Vector2F64(topRightX, topRightY), 0.0, false, true, Point2F64(right, top + topRightY))
    debitPathInputSegmentBeforeMaterializationI64(beforeMaterializationI64)
    segmentsF64 += PathFillSegmentF64.LineTo(Point2F64(right, bottom - bottomRightY))
    debitPathInputSegmentBeforeMaterializationI64(beforeMaterializationI64)
    segmentsF64 += PathFillSegmentF64.ArcTo(Vector2F64(bottomRightX, bottomRightY), 0.0, false, true, Point2F64(right - bottomRightX, bottom))
    debitPathInputSegmentBeforeMaterializationI64(beforeMaterializationI64)
    segmentsF64 += PathFillSegmentF64.LineTo(Point2F64(left + bottomLeftX, bottom))
    debitPathInputSegmentBeforeMaterializationI64(beforeMaterializationI64)
    segmentsF64 += PathFillSegmentF64.ArcTo(Vector2F64(bottomLeftX, bottomLeftY), 0.0, false, true, Point2F64(left, bottom - bottomLeftY))
    debitPathInputSegmentBeforeMaterializationI64(beforeMaterializationI64)
    segmentsF64 += PathFillSegmentF64.LineTo(Point2F64(left, top + topLeftY))
    debitPathInputSegmentBeforeMaterializationI64(beforeMaterializationI64)
    segmentsF64 += PathFillSegmentF64.ArcTo(Vector2F64(topLeftX, topLeftY), 0.0, false, true, Point2F64(left + topLeftX, top))
    debitPathInputSegmentBeforeMaterializationI64(beforeMaterializationI64)
    segmentsF64 += PathFillSegmentF64.Close
    debitPathInputCollectionBeforeMaterializationI64(beforeMaterializationI64)
    return PathFillInputF64.of(FillRule.WINDING, segmentsF64)
}

private const val pathInputCollectionSnapshotByteCountI64: Long = 16L
private const val pathInputSegmentSnapshotByteCountI64: Long = 64L

private fun debitPathInputCollectionBeforeMaterializationI64(
    beforeMaterializationI64: ((PathStrokeWorkUsageI64) -> Unit)?,
) {
    beforeMaterializationI64?.invoke(
        PathStrokeWorkUsageI64(snapshotByteCountI64 = pathInputCollectionSnapshotByteCountI64),
    )
}

private fun debitPathInputSegmentBeforeMaterializationI64(
    beforeMaterializationI64: ((PathStrokeWorkUsageI64) -> Unit)?,
) {
    beforeMaterializationI64?.invoke(
        PathStrokeWorkUsageI64(
            attemptedGeometryUnitCountI64 = 1L,
            snapshotByteCountI64 = pathInputSegmentSnapshotByteCountI64,
        ),
    )
}

private fun normalizedRadiiScaleF64(rrect: RRectF64, left: Double, top: Double, right: Double, bottom: Double): Double {
    val width = (right - left).coerceAtLeast(0.0)
    val height = (bottom - top).coerceAtLeast(0.0)
    val topLeftX = rrect.topLeft.xF64.coerceAtLeast(0.0)
    val topLeftY = rrect.topLeft.yF64.coerceAtLeast(0.0)
    val topRightX = rrect.topRight.xF64.coerceAtLeast(0.0)
    val topRightY = rrect.topRight.yF64.coerceAtLeast(0.0)
    val bottomRightX = rrect.bottomRight.xF64.coerceAtLeast(0.0)
    val bottomRightY = rrect.bottomRight.yF64.coerceAtLeast(0.0)
    val bottomLeftX = rrect.bottomLeft.xF64.coerceAtLeast(0.0)
    val bottomLeftY = rrect.bottomLeft.yF64.coerceAtLeast(0.0)
    val scale = min(1.0, min(
        ratioOrOneF64(width, topLeftX + topRightX),
        min(ratioOrOneF64(width, bottomLeftX + bottomRightX), min(
            ratioOrOneF64(height, topLeftY + bottomLeftY), ratioOrOneF64(height, topRightY + bottomRightY),
        )),
    ))
    return scale
}

private fun ratioOrOneF64(limit: Double, sum: Double): Double = if (sum > limit && sum > 0.0) limit / sum else 1.0

/** An immutable snapshot of the commands used to prepare a path fill. */
public class PathFillInputF64 private constructor(
    public val fillRule: FillRule,
    segments: Collection<PathFillSegmentF64>,
) : Iterable<PathFillSegmentF64> {
    private val values: List<PathFillSegmentF64> = segments.toList()

    public val segmentCountI32: Int get() = values.size

    public fun segmentAtI32(indexI32: Int): PathFillSegmentF64 = values[indexI32]

    override fun iterator(): Iterator<PathFillSegmentF64> = ReadOnlyPathFillIterator(values)

    public companion object {
        /** Reconstructs every F32 payload as an exact F64 value. */
        public fun fromPathF32(path: PathF32): PathFillInputF64 {
            return fromPathF32(path) { }
        }

        /** Reconstructs F32 commands only after the caller has admitted each retained snapshot. */
        public fun fromPathF32(
            path: PathF32,
            beforeMaterializationI64: (PathStrokeWorkUsageI64) -> Unit,
        ): PathFillInputF64 {
            beforeMaterializationI64(PathStrokeWorkUsageI64(snapshotByteCountI64 = 16L))
            val values = mutableListOf<PathFillSegmentF64>()
            var hasCurrentContour = false
            path.forEach { segment ->
                beforeMaterializationI64(PathStrokeWorkUsageI64(snapshotByteCountI64 = 16L))
                when (segment) {
                    is PathSegmentF32.MoveTo -> {
                        values += PathFillSegmentF64.MoveTo(segment.point.toExactPoint2F64())
                        hasCurrentContour = true
                    }

                    is PathSegmentF32.LineTo -> {
                        if (!hasCurrentContour) {
                            beforeMaterializationI64(PathStrokeWorkUsageI64(snapshotByteCountI64 = 16L))
                            values += PathFillSegmentF64.MoveTo(Point2F64.Origin)
                            hasCurrentContour = true
                        }
                        values += PathFillSegmentF64.LineTo(segment.point.toExactPoint2F64())
                    }

                    is PathSegmentF32.QuadTo -> {
                        if (!hasCurrentContour) {
                            beforeMaterializationI64(PathStrokeWorkUsageI64(snapshotByteCountI64 = 16L))
                            values += PathFillSegmentF64.MoveTo(Point2F64.Origin)
                            hasCurrentContour = true
                        }
                        values += PathFillSegmentF64.QuadTo(
                            control = segment.control.toExactPoint2F64(),
                            point = segment.point.toExactPoint2F64(),
                        )
                    }

                    is PathSegmentF32.CubicTo -> {
                        if (!hasCurrentContour) {
                            beforeMaterializationI64(PathStrokeWorkUsageI64(snapshotByteCountI64 = 16L))
                            values += PathFillSegmentF64.MoveTo(Point2F64.Origin)
                            hasCurrentContour = true
                        }
                        values += PathFillSegmentF64.CubicTo(
                            control1 = segment.control1.toExactPoint2F64(),
                            control2 = segment.control2.toExactPoint2F64(),
                            point = segment.point.toExactPoint2F64(),
                        )
                    }

                    is PathSegmentF32.ArcTo -> {
                        if (!hasCurrentContour) {
                            beforeMaterializationI64(PathStrokeWorkUsageI64(snapshotByteCountI64 = 16L))
                            values += PathFillSegmentF64.MoveTo(Point2F64.Origin)
                            hasCurrentContour = true
                        }
                        values += PathFillSegmentF64.ArcTo(
                            radius = Vector2F64(exactF64(segment.radius.x), exactF64(segment.radius.y)),
                            xAxisRotationDegreesF64 = exactF64(segment.xAxisRotation),
                            largeArc = segment.largeArc,
                            sweep = segment.sweep,
                            point = segment.point.toExactPoint2F64(),
                        )
                    }

                    PathSegmentF32.Close -> values += PathFillSegmentF64.Close
                }
            }
            beforeMaterializationI64(PathStrokeWorkUsageI64(snapshotByteCountI64 = 16L))
            return PathFillInputF64(path.fillRule, values)
        }

        public fun of(
            fillRule: FillRule,
            segments: Collection<PathFillSegmentF64>,
        ): PathFillInputF64 = PathFillInputF64(fillRule, segments)
    }
}

/** Restores the F32 IEEE-754 payload at the Kotlin/JS boundary before F64 work begins. */
private fun exactF64(valueF32: Float): Double = Float.fromBits(valueF32.toRawBits()).toDouble()

private fun Point2F32.toExactPoint2F64(): Point2F64 = Point2F64(exactF64(x), exactF64(y))

/** Converts one immutable F32 command on demand, after the caller has admitted its work. */
public fun PathSegmentF32.toPathFillSegmentF64(): PathFillSegmentF64 = when (this) {
    is PathSegmentF32.MoveTo -> PathFillSegmentF64.MoveTo(point.toExactPoint2F64())
    is PathSegmentF32.LineTo -> PathFillSegmentF64.LineTo(point.toExactPoint2F64())
    is PathSegmentF32.QuadTo -> PathFillSegmentF64.QuadTo(control.toExactPoint2F64(), point.toExactPoint2F64())
    is PathSegmentF32.CubicTo -> PathFillSegmentF64.CubicTo(control1.toExactPoint2F64(), control2.toExactPoint2F64(), point.toExactPoint2F64())
    is PathSegmentF32.ArcTo -> PathFillSegmentF64.ArcTo(Vector2F64(exactF64(radius.x), exactF64(radius.y)), exactF64(xAxisRotation), largeArc, sweep, point.toExactPoint2F64())
    PathSegmentF32.Close -> PathFillSegmentF64.Close
}

private class ReadOnlyPathFillIterator<T>(private val values: List<T>) : MutableIterator<T> {
    private var nextIndex: Int = 0

    override fun hasNext(): Boolean = nextIndex < values.size

    override fun next(): T {
        if (!hasNext()) throw NoSuchElementException()
        return values[nextIndex++]
    }

    override fun remove(): Nothing = throw UnsupportedOperationException("PathFillInputF64 iterators are read-only")
}
