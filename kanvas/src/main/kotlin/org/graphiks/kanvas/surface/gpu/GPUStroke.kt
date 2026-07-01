package org.graphiks.kanvas.surface.gpu

data class StrokeGeometry(
    val vertices: List<Float>,
    val contourStarts: List<Int>,
)

/**
 * Converts a path's outline (from tessellated contour vertices) into
 * filled geometry representing the stroke area. Each contour edge is
 * offset by strokeWidth/2 on both sides, creating a quadrilateral per
 * segment. Adjacent quads share vertices at join points.
 *
 * Currently supports:
 * - Butt caps (flat end at each contour endpoint)
 * - Miter joins (extend offset edges until they intersect)
 * - No dashing support (dash patterns are refused)
 */
internal fun strokeToFillGeometry(
    contourVertices: List<Float>,
    contourStarts: List<Int>,
    strokeWidth: Float,
): StrokeGeometry {
    if (contourVertices.size < 4 || strokeWidth <= 0f) {
        // Degenerate stroke — return empty
        return StrokeGeometry(emptyList(), listOf(0))
    }

    val halfWidth = strokeWidth / 2f
    val result = mutableListOf<Float>()
    val contourResult = mutableListOf(0)

    // Helper: compute normal for edge (x1,y1)→(x2,y2), pointing left
    fun edgeNormal(x1: Float, y1: Float, x2: Float, y2: Float): Pair<Float, Float> {
        val dx = x2 - x1
        val dy = y2 - y1
        val len = kotlin.math.sqrt(dx * dx + dy * dy)
        if (len < 1e-6f) return Pair(0f, 0f)
        return Pair(-dy / len, dx / len) // left normal
    }

    // Process each contour
    for (ci in contourStarts.indices) {
        val start = contourStarts[ci]
        val end = if (ci + 1 < contourStarts.size) contourStarts[ci + 1] else contourVertices.size / 2
        val n = end - start

        if (n < 2) continue

        // Extract contour points
        val points = Array(n) { idx ->
            val i = (start + idx) * 2
            Pair(contourVertices[i], contourVertices[i + 1])
        }

        // Compute normals for each edge (the normal for each vertex is the
        // average of the normals of the two edges meeting at that vertex)
        val normals = Array(n) { i ->
            val prev = edgeNormal(
                points[(i + n - 1) % n].first, points[(i + n - 1) % n].second,
                points[i].first, points[i].second,
            )
            val next = edgeNormal(
                points[i].first, points[i].second,
                points[(i + 1) % n].first, points[(i + 1) % n].second,
            )
            val nx = prev.first + next.first
            val ny = prev.second + next.second
            val len = kotlin.math.sqrt(nx * nx + ny * ny)
            if (len < 1e-6f) Pair(0f, 0f)
            else Pair(nx / len * halfWidth, ny / len * halfWidth)
        }

        // Generate stroke quad: for each edge, add a quad (left side, right side)
        for (i in 0 until n - 1) {
            val p0 = points[i]; val p1 = points[i + 1]
            val n0 = normals[i]; val n1 = normals[i + 1]

            // Left offset edge
            val l0x = p0.first - n0.first; val l0y = p0.second - n0.second
            val l1x = p1.first - n1.first; val l1y = p1.second - n1.second
            // Right offset edge
            val r0x = p0.first + n0.first; val r0y = p0.second + n0.second
            val r1x = p1.first + n1.first; val r1y = p1.second + n1.second

            // Quad as two triangles (CW winding)
            result.addAll(listOf(l0x, l0y, r0x, r0y, r1x, r1y))
            result.addAll(listOf(l0x, l0y, r1x, r1y, l1x, l1y))
        }

        // Butt caps at endpoints (for open contours only)
        // When n < contourStarts size, we cap at start and end
        // [Simplified: just use the first and last edge normals for caps]
        if (n < contourVertices.size / 2) { // open contour
            // Start cap
            val sn = edgeNormal(points[1].first, points[1].second, points[0].first, points[0].second)
            val capNx = sn.first * halfWidth; val capNy = sn.second * halfWidth
            val pS = points[0]
            result.addAll(listOf(
                pS.first - capNx, pS.second - capNy,
                pS.first + capNx, pS.second + capNy,
                pS.first + capNx + normals[0].first, pS.second + capNy + normals[0].second,
            ))
            result.addAll(listOf(
                pS.first - capNx, pS.second - capNy,
                pS.first + capNx + normals[0].first, pS.second + capNy + normals[0].second,
                pS.first - capNx + normals[0].first, pS.second - capNy + normals[0].second,
            ))
            // End cap (backwards normal)
            val en = edgeNormal(points[n-2].first, points[n-2].second, points[n-1].first, points[n-1].second)
            val capEx = en.first * halfWidth; val capEy = en.second * halfWidth
            val pE = points[n-1]
            result.addAll(listOf(
                pE.first - capEx, pE.second - capEy,
                pE.first + capEx, pE.second + capEy,
                pE.first + capEx - normals[n-1].first, pE.second + capEy - normals[n-1].second,
            ))
            result.addAll(listOf(
                pE.first - capEx, pE.second - capEy,
                pE.first + capEx - normals[n-1].first, pE.second + capEy - normals[n-1].second,
                pE.first - capEx - normals[n-1].first, pE.second - capEy - normals[n-1].second,
            ))
        }

        contourResult.add(result.size / 2)
    }

    return StrokeGeometry(vertices = result, contourStarts = contourResult)
}
