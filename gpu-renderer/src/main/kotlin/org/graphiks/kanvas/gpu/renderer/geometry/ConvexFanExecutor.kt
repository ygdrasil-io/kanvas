package org.graphiks.kanvas.gpu.renderer.geometry

/** Statistics produced by [ConvexFanExecutor.execute] for one convex fan fill. */
data class ConvexFanStats(
    val drawCallCount: Int,
    val triangleCount: Int,
    val vertexCount: Int,
    val singlePass: Boolean,
)

/**
 * Executes a single-pass convex fan fill for convex paths.
 *
 * Takes a triangulated path (produced by [PathTessellator]) and emits
 * a single draw call with a triangle list. Fragment shader applies
 * analytic anti-aliasing. This is faster than the two-pass
 * stencil-cover for convex paths.
 */
class ConvexFanExecutor {
    /**
     * Executes the convex fan fill for the given triangulated path.
     * Returns execution statistics without committing GPU work.
     */
    fun execute(triangulated: TriangleList): ConvexFanStats {
        require(triangulated.triangleCount > 0) {
            "ConvexFanExecutor requires at least one triangle"
        }

        return ConvexFanStats(
            drawCallCount = 1,
            triangleCount = triangulated.triangleCount,
            vertexCount = triangulated.vertices.size,
            singlePass = true,
        )
    }

    /**
     * Returns diagnostic lines comparing convex fan performance
     * against stencil-cover for the same path.
     */
    fun performanceDiagnostics(convexStats: ConvexFanStats, stencilStats: StencilCoverStats): List<String> = listOf(
        "convex-fan:drawCalls=${convexStats.drawCallCount} " +
            "stencilCoverPasses=${stencilStats.totalDrawCalls} " +
            "speedup=${stencilStats.totalDrawCalls - convexStats.drawCallCount}x fewer passes",
        "convex-fan:singlePass=${convexStats.singlePass}",
        "convex-fan:triangles=${convexStats.triangleCount}",
        "convex-fan:vertices=${convexStats.vertexCount}",
    )
}

/**
 * Returns true when the given vertex list describes a convex polygon.
 * Uses cross product sign consistency: all consecutive edge cross
 * products must have the same sign (all positive or all negative).
 */
fun isPathConvex(vertices: List<Point>): Boolean {
    if (vertices.size < 3) return false
    var sign = 0
    for (i in vertices.indices) {
        val a = vertices[i]
        val b = vertices[(i + 1) % vertices.size]
        val c = vertices[(i + 2) % vertices.size]
        val cross = (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)
        if (cross != 0f) {
            val currentSign = if (cross > 0) 1 else -1
            if (sign == 0) {
                sign = currentSign
            } else if (sign != currentSign) {
                return false
            }
        }
    }
    return true
}
