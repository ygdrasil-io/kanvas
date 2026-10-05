package org.graphiks.kanvas.gpu.renderer.execution

import org.graphiks.math.geometry.RectI32

/** Shared serialization of W4's already-sealed analytic raster quads. */
internal fun packW4RasterGeometry(boundsI32: List<RectI32>): GPUCorePrimitiveFrameGeometryArena {
    val vertices = FloatArray(Math.multiplyExact(boundsI32.size, 8))
    val indices = IntArray(Math.multiplyExact(boundsI32.size, 6))
    val slices = boundsI32.mapIndexed { drawIndex, bounds ->
        val vertexOffset = Math.multiplyExact(drawIndex, 8)
        vertices[vertexOffset] = bounds.left.toFloat(); vertices[vertexOffset + 1] = bounds.top.toFloat()
        vertices[vertexOffset + 2] = bounds.right.toFloat(); vertices[vertexOffset + 3] = bounds.top.toFloat()
        vertices[vertexOffset + 4] = bounds.right.toFloat(); vertices[vertexOffset + 5] = bounds.bottom.toFloat()
        vertices[vertexOffset + 6] = bounds.left.toFloat(); vertices[vertexOffset + 7] = bounds.bottom.toFloat()
        val indexOffset = Math.multiplyExact(drawIndex, 6)
        indices[indexOffset] = 0; indices[indexOffset + 1] = 2; indices[indexOffset + 2] = 1
        indices[indexOffset + 3] = 0; indices[indexOffset + 4] = 3; indices[indexOffset + 5] = 2
        GPUCorePrimitiveFrameGeometrySlice(indexOffset, 6, Math.multiplyExact(drawIndex, 4), 4, 3)
    }
    return GPUCorePrimitiveFrameGeometryArena(vertices, indices, slices)
}
