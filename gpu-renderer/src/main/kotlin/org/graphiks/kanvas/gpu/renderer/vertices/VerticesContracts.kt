package org.graphiks.kanvas.gpu.renderer.vertices

/** Vertices descriptor captured before buffer planning. */
data class GPUVerticesDescriptor(
    val primitiveMode: String,
    val vertexCount: Int,
    val indexCount: Int? = null,
    val hasColors: Boolean,
    val hasTexCoords: Boolean,
    val boundsLabel: String,
    val provenance: String,
)

/** Vertex layout plan. */
data class GPUVertexLayoutPlan(
    val attributes: List<String>,
    val strideBytes: Int,
    val offsets: Map<String, Int>,
    val shaderLocations: Map<String, Int>,
)

/** Vertex color plan. */
data class GPUVertexColorPlan(
    val colorFormat: String,
    val premulPolicy: String,
    val colorSpaceLabel: String,
)

/** Vertex texture-coordinate plan. */
data class GPUVertexTexCoordPlan(
    val coordinateSpaceLabel: String,
    val transformHash: String? = null,
)

/** Primitive blend plan for drawVertices-style input. */
data class GPUPrimitiveBlendPlan(
    val blendModeLabel: String,
    val requiresDestinationRead: Boolean,
)

/** Index buffer plan. */
data class GPUIndexBufferPlan(
    val indexFormat: String,
    val count: Int,
    val validationLabel: String,
)

/** Vertex buffer plan. */
data class GPUVertexBufferPlan(
    val byteCount: Long,
    val layout: GPUVertexLayoutPlan,
    val uploadRequirement: String,
)

/** Vertices route. */
sealed interface GPUVerticesRoute {
    /** Native vertex/index buffer route. */
    data class NativeBuffers(val vertexBufferPlan: GPUVertexBufferPlan, val indexBufferPlan: GPUIndexBufferPlan?) : GPUVerticesRoute

    /** CPU-prepared vertex buffers consumed by GPU. */
    data class PreparedBuffers(val artifactKey: String, val vertexBufferPlan: GPUVertexBufferPlan) : GPUVerticesRoute

    /** Refused vertices route. */
    data class Refused(val diagnostic: GPUVerticesDiagnostic) : GPUVerticesRoute
}

/** Vertices render-step plan. */
data class GPUVerticesRenderStepPlan(
    val renderStepLabel: String,
    val vertexLayoutHash: String,
    val primitiveMode: String,
)

/** Future mesh descriptor. */
data class GPUMeshDescriptor(
    val meshId: String,
    val topology: String,
    val vertexCount: Int,
    val boundsLabel: String,
)

/** Vertices diagnostic. */
data class GPUVerticesDiagnostic(
    val code: String,
    val verticesLabel: String,
    val message: String,
    val terminal: Boolean,
)
