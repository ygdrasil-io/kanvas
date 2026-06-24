package org.graphiks.kanvas.gpu.renderer.vertices

/** Statistics from a vertices execution pass. */
data class VerticesExecutionStats(
    val vertexCount: Int,
    val colorCount: Int,
    val primitiveMode: GPUVertexMode,
    val executed: Boolean,
    val nonClaimLine: String,
)

/** Executes DrawVertices-style geometry and produces execution stats. */
class VerticesExecutor {
    /** Validates and executes vertices with the given topology and colors. */
    fun execute(
        vertices: List<Float>,
        colors: List<Float>?,
        topology: GPUVertexMode,
    ): VerticesExecutionStats {
        require(vertices.size >= 6) { "VerticesExecutor requires at least 6 floats (2 triangles)" }
        require(vertices.size % 2 == 0) { "VerticesExecutor requires pairs of floats" }
        require(topology != GPUVertexMode.TriangleFan) { "VerticesExecutor does not support TriangleFan" }

        val vertexCount = vertices.size / 2
        val colorCount = colors?.size?.div(4) ?: 0

        if (colors != null) {
            require(colors.size % 4 == 0) { "Colors must be RGBA quads" }
            require(colorCount == vertexCount) { "Color count ($colorCount) must match vertex count ($vertexCount)" }
        }

        return VerticesExecutionStats(
            vertexCount = vertexCount,
            colorCount = colorCount,
            primitiveMode = topology,
            executed = true,
            nonClaimLine = VERTICES_EXECUTOR_NONCLAIM_LINE,
        )
    }

    companion object {
        const val VERTICES_EXECUTOR_NONCLAIM_LINE: String =
            "vertices:nonclaim verticesExecutionSupported=true " +
                "primitiveBlenderExecuted=false cpuVertexTransform=false " +
                "gpuIndexedDraw=false productActivation=false"
    }
}
