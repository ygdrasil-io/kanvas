package org.graphiks.math.geometry

import kotlin.math.abs
import kotlin.math.sqrt

/** Boolean operations supported by [PathOpsF32]. */
public enum class PathBooleanOp { DIFFERENCE, INTERSECT, UNION, XOR, REVERSE_DIFFERENCE }

/** Renderer-neutral boolean operations over immutable [PathF32] values. */
public object PathOpsF32 {
    /**
     * Combines two finite paths and returns finite [PathF32] geometry.
     *
 * Rectangle inputs retain exact rectangular region geometry. Other paths
 * use adaptive shared flattening followed by a
     * deterministic edge arrangement; inverse fills are unbounded and are
     * therefore rejected for this finite-returning operation.
     */
    public fun op(first: PathF32, second: PathF32, op: PathBooleanOp): PathF32 {
        require(!first.fillRule.isInverse() && !second.fillRule.isInverse()) { "Boolean operations require finite fill rules" }
        val firstRect = PathAnalysisF32.rect(first)
        val secondRect = PathAnalysisF32.rect(second)
        if (firstRect != null && secondRect != null) {
            val regionOp = when (op) {
                PathBooleanOp.DIFFERENCE -> RegionBooleanOp.DIFFERENCE
                PathBooleanOp.INTERSECT -> RegionBooleanOp.INTERSECT
                PathBooleanOp.UNION -> RegionBooleanOp.UNION
                PathBooleanOp.XOR -> RegionBooleanOp.XOR
                PathBooleanOp.REVERSE_DIFFERENCE -> RegionBooleanOp.REVERSE_DIFFERENCE
            }
            return pathFromRegion(RegionF32(firstRect).op(RegionF32(secondRect), regionOp), first.fillRule)
        }
        return arrangement(first, second, op)
    }

    /** Returns an independent copy with identical geometric membership. */
    public fun simplify(path: PathF32): PathF32 = PathBuilder(path.fillRule).addPath(path).build()

    /** Returns an independent path using non-zero winding fill. */
    public fun asWinding(path: PathF32): PathF32 = PathBuilder(FillRule.WINDING).addPath(path).build()
}

private fun pathFromRegion(region: RegionF32, fillRule: FillRule): PathF32 {
    val builder = PathBuilder(fillRule)
    region.rects.forEach(builder::addRect)
    return builder.build()
}

private data class Edge(val start: Point2F32, val end: Point2F32)

private fun arrangement(first: PathF32, second: PathF32, operation: PathBooleanOp): PathF32 {
    val edges = (flattenedEdgesF32(first) + flattenedEdgesF32(second)).filter { it.start != it.end }
    if (edges.isEmpty()) return PathBuilder(first.fillRule).build()
    val selected = linkedMapOf<Pair<String, String>, Edge>()
    edges.forEach { edge ->
        val cuts = mutableSetOf(0f, 1f)
        edges.forEach { other -> segmentIntersectionParameters(edge, other)?.let { (t, _) -> if (t > 0f && t < 1f) cuts += t } }
        cuts.sorted().zipWithNext().forEach { (from, to) ->
            val a = interpolate(edge, from); val b = interpolate(edge, to)
            val dx = b.x - a.x; val dy = b.y - a.y; val length = sqrt(dx * dx + dy * dy)
            if (length == 0f) return@forEach
            val midpoint = interpolate(Edge(a, b), .5f)
            val epsilon = 1e-4f * maxOf(1f, length)
            val left = Point2F32(midpoint.x - dy / length * epsilon, midpoint.y + dx / length * epsilon)
            val right = Point2F32(midpoint.x + dy / length * epsilon, midpoint.y - dx / length * epsilon)
            val leftInside = operation.apply(PathAnalysisF32.contains(first, left), PathAnalysisF32.contains(second, left))
            val rightInside = operation.apply(PathAnalysisF32.contains(first, right), PathAnalysisF32.contains(second, right))
            if (leftInside != rightInside) {
                val chosen = if (leftInside) Edge(a, b) else Edge(b, a)
                selected[pointKey(chosen.start) to pointKey(chosen.end)] = chosen
            }
        }
    }
    val pending = selected.values.toMutableList()
    val builder = PathBuilder(first.fillRule)
    while (pending.isNotEmpty()) {
        val firstEdge = pending.removeAt(0)
        builder.moveTo(firstEdge.start.x, firstEdge.start.y).lineTo(firstEdge.end.x, firstEdge.end.y)
        var current = firstEdge.end
        while (pointKey(current) != pointKey(firstEdge.start)) {
            val index = pending.indexOfFirst { pointKey(it.start) == pointKey(current) }
            if (index < 0) break
            val next = pending.removeAt(index)
            builder.lineTo(next.end.x, next.end.y)
            current = next.end
        }
        if (pointKey(current) == pointKey(firstEdge.start)) builder.close()
    }
    return builder.build()
}

private fun flattenedEdgesF32(path: PathF32): List<Edge> {
    val normalization = pathNormalizationF64(listOf(path))
    return PathFlattenerF64.flatten(NormalizedPathF64(path, normalization), closeForFill = true).flatMap { contour ->
        contour.points.zipWithNext().map { (first, second) ->
            Edge(normalization.denormalize(first.point), normalization.denormalize(second.point))
        }
    }
}

private fun PathBooleanOp.apply(a: Boolean, b: Boolean): Boolean = when (this) {
    PathBooleanOp.DIFFERENCE -> a && !b
    PathBooleanOp.INTERSECT -> a && b
    PathBooleanOp.UNION -> a || b
    PathBooleanOp.XOR -> a != b
    PathBooleanOp.REVERSE_DIFFERENCE -> b && !a
}

private fun segmentIntersectionParameters(first: Edge, second: Edge): Pair<Float, Float>? {
    val rx = first.end.x - first.start.x; val ry = first.end.y - first.start.y
    val sx = second.end.x - second.start.x; val sy = second.end.y - second.start.y
    val denominator = rx * sy - ry * sx
    if (abs(denominator) < 1e-7f) return null
    val qpx = second.start.x - first.start.x; val qpy = second.start.y - first.start.y
    val t = (qpx * sy - qpy * sx) / denominator; val u = (qpx * ry - qpy * rx) / denominator
    return if (t in -1e-6f..1.000001f && u in -1e-6f..1.000001f) t to u else null
}

private fun interpolate(edge: Edge, t: Float): Point2F32 = Point2F32(edge.start.x + (edge.end.x - edge.start.x) * t, edge.start.y + (edge.end.y - edge.start.y) * t)
private fun pointKey(point: Point2F32): String = "${(point.x * 1_000_000f).toInt()}:${(point.y * 1_000_000f).toInt()}"
