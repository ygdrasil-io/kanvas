package org.graphiks.kanvas.gpu.renderer.geometry

/** Exact raw non-AA stencil edge-fan ABI shared by recording and pure preflight. */
internal fun corePrimitiveClipStencilEdgeFan(
    ndcVertices: List<Float>,
    contourStarts: List<Int>,
): GeometryTriangleData {
    require(ndcVertices.size >= 6 && ndcVertices.size % 2 == 0 &&
        ndcVertices.all(Float::isFinite)
    ) { "Clip-stencil edge fan requires finite x/y pairs" }
    val vertexCount = ndcVertices.size / 2
    require(contourStarts.isNotEmpty() && contourStarts.first() == 0 &&
        contourStarts.zipWithNext().all { (left, right) -> left < right } &&
        contourStarts.last() in 0 until vertexCount &&
        contourStarts.indices.all { index ->
            val start = contourStarts[index]
            val end = contourStarts.getOrElse(index + 1) { vertexCount }
            end - start >= 3
        }
    ) { "Clip-stencil edge fan requires triangle-capable contours" }
    return PathTessellator().stencilEdgeFan(
        FlattenedPath(
            points = ndcVertices.chunked(2).map { pair -> Point(pair[0], pair[1]) },
            contourStarts = contourStarts,
        ),
    )
}
