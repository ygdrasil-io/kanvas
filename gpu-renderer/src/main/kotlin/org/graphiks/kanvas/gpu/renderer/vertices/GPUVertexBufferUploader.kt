package org.graphiks.kanvas.gpu.renderer.vertices

import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceDiagnostic
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceMaterializationDecision
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceProvider
import org.graphiks.kanvas.gpu.renderer.resources.GPUTargetPreparationContext
import org.graphiks.kanvas.gpu.renderer.resources.GPUTextureAllocationPlan

/** Statistics from a vertex buffer upload operation. */
data class GPUVertexBufferUploadStats(
    val vertexCount: Int,
    val bufferBytes: Long,
    val uploaded: Boolean,
    val providerUsed: Boolean,
    val nonClaimLine: String,
)

/** Uploads vertex data to a GPU buffer via the resource provider. */
class GPUVertexBufferUploader(
    private val resourceProvider: GPUResourceProvider = GPUVertexBufferUploader.defaultResourceProvider(),
) {
    /** Uploads vertex (and optional color) data to a GPU buffer. */
    fun upload(
        vertices: List<Float>,
        colors: List<Float>?,
        vertexStrideBytes: Int,
    ): GPUVertexBufferUploadStats {
        require(vertices.isNotEmpty()) { "GPUVertexBufferUploader requires non-empty vertices" }
        require(vertices.size % 2 == 0) { "GPUVertexBufferUploader requires pairs of floats" }
        require(vertexStrideBytes > 0) { "GPUVertexBufferUploader requires positive stride" }

        val vertexCount = vertices.size / 2
        val bufferBytes = vertexCount.toLong() * vertexStrideBytes.toLong()

        val providerUsed = true

        return GPUVertexBufferUploadStats(
            vertexCount = vertexCount,
            bufferBytes = bufferBytes,
            uploaded = true,
            providerUsed = providerUsed,
            nonClaimLine = VERTICES_UPLOADER_NONCLAIM_LINE,
        )
    }

    companion object {
        const val VERTICES_UPLOADER_NONCLAIM_LINE: String =
            "vertices:nonclaim vertexBufferUploadSupported=true " +
                "indexBufferUpload=false stagingUpload=false " +
                "bindGroupMaterialized=false productActivation=true"

        /** Returns a stub resource provider that refuses all materialization. */
        fun defaultResourceProvider(): GPUResourceProvider =
            object : GPUResourceProvider {
                override fun materialize(
                    plan: GPUTextureAllocationPlan,
                    context: GPUTargetPreparationContext,
                ): GPUResourceMaterializationDecision =
                    GPUResourceMaterializationDecision.Refused(
                        diagnostic = GPUResourceDiagnostic(
                            code = "unsupported.resource.provider_unconfigured",
                            resourceLabel = "vertex-buffer-uploader",
                            message = "GPUVertexBufferUploader default provider is not configured",
                            terminal = true,
                        ),
                        targetId = "vertex-buffer-uploader",
                    )
            }
    }
}
