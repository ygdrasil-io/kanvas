package org.graphiks.math.geometry

/**
 * Immutable single 2D line segment.
 *
 * Its endpoints are points in affine space; their order defines the segment
 * direction from [start] to [end].
 */
public data class Line2F32(
    public val start: Point2F32,
    public val end: Point2F32,
) {
    /** Returns an independently mutable copy of this segment. */
    public fun toMutable(): MutableLine2F32 = MutableLine2F32(start, end)
}

/** Creates an immutable 2D line segment. */
public fun lineOf(start: Point2F32, end: Point2F32): Line2F32 = Line2F32(start, end)

/**
 * Mutable single 2D line segment.
 *
 * Equality remains identity-based. Use [hasSameEndpointsAs] when comparing
 * endpoint values.
 */
public class MutableLine2F32(
    public var start: Point2F32,
    public var end: Point2F32,
) {
    /** Replaces both endpoints of this segment. */
    public fun set(start: Point2F32, end: Point2F32) {
        this.start = start
        this.end = end
    }

    /** Compares endpoint values without changing identity-based equality. */
    public fun hasSameEndpointsAs(other: Line2F32): Boolean = start == other.start && end == other.end

    /** Compares endpoint values without changing identity-based equality. */
    public fun hasSameEndpointsAs(other: MutableLine2F32): Boolean = start == other.start && end == other.end

    /** Returns an immutable snapshot of this segment. */
    public fun toImmutable(): Line2F32 = Line2F32(start, end)
}

/** Creates a mutable 2D line segment. */
public fun mutableLineOf(start: Point2F32, end: Point2F32): MutableLine2F32 = MutableLine2F32(start, end)
