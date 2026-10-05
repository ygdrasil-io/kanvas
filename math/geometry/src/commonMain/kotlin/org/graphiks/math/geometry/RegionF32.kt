package org.graphiks.math.geometry

/** Boolean operations supported by immutable [RegionF32] values. */
public enum class RegionBooleanOp { DIFFERENCE, INTERSECT, UNION, XOR, REVERSE_DIFFERENCE, REPLACE }

/**
 * An immutable region represented as a normalized collection of axis-aligned rectangles.
 *
 * [RectF32] itself is mutable, so all public rectangle values are defensive copies.
 */
public class RegionF32 private constructor(private val rectangles: List<RectF32>) {
    public constructor(rect: RectF32? = null) : this(
        rect?.takeIf { !it.isEmpty }?.let(::listOf)?.map(::copyRect) ?: emptyList(),
    )

    /** Snapshot rectangles composing this region. */
    public val rects: List<RectF32> get() = rectangles.map(::copyRect)
    public val isEmpty: Boolean get() = rectangles.isEmpty()
    public val isRect: Boolean get() = rectangles.size == 1
    public val isComplex: Boolean get() = rectangles.size > 1
    public val bounds: RectF32 get() = if (rectangles.isEmpty()) RectF32.Empty else RectF32(rectangles.minOf { it.left }, rectangles.minOf { it.top }, rectangles.maxOf { it.right }, rectangles.maxOf { it.bottom })

    public fun contains(x: Float, y: Float): Boolean = rectangles.any { it.contains(x, y) }
    public fun contains(point: Point2F32): Boolean = contains(point.x, point.y)
    public fun quickReject(rect: RectF32): Boolean = !rectangles.any { RectF32.intersects(it, rect) }
    public fun translated(dx: Float, dy: Float): RegionF32 = fromRects(rectangles.map { RectF32(it.left + dx, it.top + dy, it.right + dx, it.bottom + dy) })

    /** Returns a new region; neither operand is modified. */
    public fun op(other: RegionF32, op: RegionBooleanOp): RegionF32 = when (op) {
        RegionBooleanOp.UNION -> fromRects(rectangles + other.rectangles)
        RegionBooleanOp.INTERSECT -> fromRects(rectangles.flatMap { a -> other.rectangles.mapNotNull { b -> intersect(a, b) } })
        RegionBooleanOp.DIFFERENCE -> fromRects(subtract(rectangles, other.rectangles))
        RegionBooleanOp.REVERSE_DIFFERENCE -> fromRects(subtract(other.rectangles, rectangles))
        RegionBooleanOp.XOR -> fromRects(subtract(rectangles, other.rectangles) + subtract(other.rectangles, rectangles))
        RegionBooleanOp.REPLACE -> fromRects(other.rectangles)
    }

    public fun op(other: RectF32, op: RegionBooleanOp): RegionF32 = op(RegionF32(other), op)

    public companion object {
        public fun empty(): RegionF32 = RegionF32()
        public fun fromRects(rects: Iterable<RectF32>): RegionF32 = RegionF32(normalize(rects.toList()))
    }
}

private fun copyRect(rect: RectF32): RectF32 = RectF32(rect.left, rect.top, rect.right, rect.bottom)

private fun intersect(a: RectF32, b: RectF32): RectF32? = RectF32(maxOf(a.left, b.left), maxOf(a.top, b.top), minOf(a.right, b.right), minOf(a.bottom, b.bottom)).takeIf { !it.isEmpty }

private fun subtract(source: List<RectF32>, cutters: List<RectF32>): List<RectF32> {
    var remaining = source.map(::copyRect)
    cutters.forEach { cutter ->
        remaining = remaining.flatMap { value -> subtractOne(value, cutter) }
    }
    return remaining
}

private fun subtractOne(value: RectF32, cutter: RectF32): List<RectF32> {
    val overlap = intersect(value, cutter) ?: return listOf(copyRect(value))
    return listOf(
        RectF32(value.left, value.top, value.right, overlap.top),
        RectF32(value.left, overlap.bottom, value.right, value.bottom),
        RectF32(value.left, overlap.top, overlap.left, overlap.bottom),
        RectF32(overlap.right, overlap.top, value.right, overlap.bottom),
    ).filterNot { it.isEmpty }
}

/** Produces a deterministic, non-overlapping rectangle cover. */
private fun normalize(input: List<RectF32>): List<RectF32> {
    var result = emptyList<RectF32>()
    input.filterNot { it.isEmpty }.forEach { rect -> result = subtract(listOf(rect), result) + result }
    var changed: Boolean
    do {
        changed = false
        val pending = result.sortedWith(compareBy<RectF32>({ it.top }, { it.left }, { it.bottom }, { it.right })).toMutableList()
        val merged = mutableListOf<RectF32>()
        while (pending.isNotEmpty()) {
            var current = pending.removeAt(0)
            val index = pending.indexOfFirst { candidate ->
                (current.top == candidate.top && current.bottom == candidate.bottom && current.right == candidate.left) ||
                    (current.left == candidate.left && current.right == candidate.right && current.bottom == candidate.top)
            }
            if (index >= 0) {
                val candidate = pending.removeAt(index)
                current = RectF32(minOf(current.left, candidate.left), minOf(current.top, candidate.top), maxOf(current.right, candidate.right), maxOf(current.bottom, candidate.bottom))
                pending += current
                changed = true
            } else merged += current
        }
        result = merged
    } while (changed)
    return result.sortedWith(compareBy({ it.top }, { it.left }, { it.bottom }, { it.right }))
}
