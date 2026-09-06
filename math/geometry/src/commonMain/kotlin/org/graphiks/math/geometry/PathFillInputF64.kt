package org.graphiks.math.geometry

import org.graphiks.math.vector.Vector2F64

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

private class ReadOnlyPathFillIterator<T>(private val values: List<T>) : MutableIterator<T> {
    private var nextIndex: Int = 0

    override fun hasNext(): Boolean = nextIndex < values.size

    override fun next(): T {
        if (!hasNext()) throw NoSuchElementException()
        return values[nextIndex++]
    }

    override fun remove(): Nothing = throw UnsupportedOperationException("PathFillInputF64 iterators are read-only")
}
