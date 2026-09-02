package org.graphiks.math.geometry

import org.graphiks.math.vector.Vector2F32

/** The rule used to decide which parts of a path are filled. */
public enum class FillRule {
    WINDING,
    EVEN_ODD,
    INVERSE_WINDING,
    INVERSE_EVEN_ODD,
}

/** An immutable command in a [PathF32]. */
public sealed interface PathSegmentF32 {
    public data class MoveTo(public val point: Point2F32) : PathSegmentF32

    public data class LineTo(public val point: Point2F32) : PathSegmentF32

    public data class QuadTo(
        public val control: Point2F32,
        public val point: Point2F32,
    ) : PathSegmentF32

    public data class CubicTo(
        public val control1: Point2F32,
        public val control2: Point2F32,
        public val point: Point2F32,
    ) : PathSegmentF32

    /** An SVG-style elliptical arc command. */
    public data class ArcTo(
        public val radius: Vector2F32,
        public val xAxisRotation: Float,
        public val largeArc: Boolean,
        public val sweep: Boolean,
        public val point: Point2F32,
    ) : PathSegmentF32

    public data object Close : PathSegmentF32
}

/**
 * An immutable geometric path.
 *
 * Use [PathBuilder] to incrementally create paths. The constructor snapshots its
 * segments, so later changes to a source collection cannot change this value.
 */
public class PathF32 internal constructor(
    public val fillRule: FillRule,
    segments: Collection<PathSegmentF32>,
) : Iterable<PathSegmentF32> {
    private val values: List<PathSegmentF32> = segments.toList()

    public val segmentCount: Int get() = values.size

    public fun segmentAt(index: Int): PathSegmentF32 = values[index]

    /**
     * The backing list is intentionally never exposed: on the JVM a list
     * iterator can otherwise be cast to [MutableIterator] and used to remove
     * a segment from this immutable path.
     */
    override fun iterator(): Iterator<PathSegmentF32> = ReadOnlyPathIterator(values)

    override fun equals(other: Any?): Boolean =
        other is PathF32 && fillRule == other.fillRule && values == other.values

    override fun hashCode(): Int = 31 * fillRule.hashCode() + values.hashCode()

    override fun toString(): String = "PathF32(fillRule=$fillRule, segments=$values)"
}

private class ReadOnlyPathIterator<T>(private val values: List<T>) : MutableIterator<T> {
    private var nextIndex: Int = 0

    override fun hasNext(): Boolean = nextIndex < values.size

    override fun next(): T {
        if (!hasNext()) throw NoSuchElementException()
        return values[nextIndex++]
    }

    override fun remove(): Nothing = throw UnsupportedOperationException("PathF32 iterators are read-only")
}
