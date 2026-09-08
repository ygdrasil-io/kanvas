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
public fun RectF64.toPathFillInputF64(): PathFillInputF64 = PathFillInputF64.of(
    FillRule.WINDING,
    listOf(
        PathFillSegmentF64.MoveTo(Point2F64(left, top)),
        PathFillSegmentF64.LineTo(Point2F64(right, top)),
        PathFillSegmentF64.LineTo(Point2F64(right, bottom)),
        PathFillSegmentF64.LineTo(Point2F64(left, bottom)),
        PathFillSegmentF64.Close,
    ),
)

/** Builds the canonical rounded-rectangle contour with F64 endpoints and arc radii. */
public fun RRectF64.toPathFillInputF64(): PathFillInputF64 {
    val rect = copyRectF64()
    val radii = normalizedRadiiF64(this)
    val tl = radii[0]; val tr = radii[1]; val br = radii[2]; val bl = radii[3]
    return PathFillInputF64.of(
        FillRule.WINDING,
        listOf(
            PathFillSegmentF64.MoveTo(Point2F64(rect.left + tl.xF64, rect.top)),
            PathFillSegmentF64.LineTo(Point2F64(rect.right - tr.xF64, rect.top)),
            PathFillSegmentF64.ArcTo(Vector2F64(tr.xF64, tr.yF64), 0.0, false, true, Point2F64(rect.right, rect.top + tr.yF64)),
            PathFillSegmentF64.LineTo(Point2F64(rect.right, rect.bottom - br.yF64)),
            PathFillSegmentF64.ArcTo(Vector2F64(br.xF64, br.yF64), 0.0, false, true, Point2F64(rect.right - br.xF64, rect.bottom)),
            PathFillSegmentF64.LineTo(Point2F64(rect.left + bl.xF64, rect.bottom)),
            PathFillSegmentF64.ArcTo(Vector2F64(bl.xF64, bl.yF64), 0.0, false, true, Point2F64(rect.left, rect.bottom - bl.yF64)),
            PathFillSegmentF64.LineTo(Point2F64(rect.left, rect.top + tl.yF64)),
            PathFillSegmentF64.ArcTo(Vector2F64(tl.xF64, tl.yF64), 0.0, false, true, Point2F64(rect.left + tl.xF64, rect.top)),
            PathFillSegmentF64.Close,
        ),
    )
}

private fun normalizedRadiiF64(rrect: RRectF64): Array<CornerRadiiF64> {
    val rect = rrect.copyRectF64()
    val width = (rect.right - rect.left).coerceAtLeast(0.0)
    val height = (rect.bottom - rect.top).coerceAtLeast(0.0)
    val tl = CornerRadiiF64.of(rrect.topLeft.xF64.coerceAtLeast(0.0), rrect.topLeft.yF64.coerceAtLeast(0.0))
    val tr = CornerRadiiF64.of(rrect.topRight.xF64.coerceAtLeast(0.0), rrect.topRight.yF64.coerceAtLeast(0.0))
    val br = CornerRadiiF64.of(rrect.bottomRight.xF64.coerceAtLeast(0.0), rrect.bottomRight.yF64.coerceAtLeast(0.0))
    val bl = CornerRadiiF64.of(rrect.bottomLeft.xF64.coerceAtLeast(0.0), rrect.bottomLeft.yF64.coerceAtLeast(0.0))
    val scale = min(1.0, min(
        ratioOrOneF64(width, tl.xF64 + tr.xF64),
        min(ratioOrOneF64(width, bl.xF64 + br.xF64), min(
            ratioOrOneF64(height, tl.yF64 + bl.yF64), ratioOrOneF64(height, tr.yF64 + br.yF64),
        )),
    ))
    fun scaled(value: CornerRadiiF64): CornerRadiiF64 = CornerRadiiF64.of(value.xF64 * scale, value.yF64 * scale)
    return arrayOf(scaled(tl), scaled(tr), scaled(br), scaled(bl))
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
            val values = mutableListOf<PathFillSegmentF64>()
            var hasCurrentContour = false
            path.forEach { segment ->
                when (segment) {
                    is PathSegmentF32.MoveTo -> {
                        values += PathFillSegmentF64.MoveTo(segment.point.toExactPoint2F64())
                        hasCurrentContour = true
                    }

                    is PathSegmentF32.LineTo -> {
                        if (!hasCurrentContour) {
                            values += PathFillSegmentF64.MoveTo(Point2F64.Origin)
                            hasCurrentContour = true
                        }
                        values += PathFillSegmentF64.LineTo(segment.point.toExactPoint2F64())
                    }

                    is PathSegmentF32.QuadTo -> {
                        if (!hasCurrentContour) {
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
