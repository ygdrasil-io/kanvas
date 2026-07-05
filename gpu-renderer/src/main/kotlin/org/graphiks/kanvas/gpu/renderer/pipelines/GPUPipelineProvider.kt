package org.graphiks.kanvas.gpu.renderer.pipelines

import io.ygdrasil.webgpu.GPUTextureFormat
import org.graphiks.kanvas.gpu.renderer.state.GPUBlendMode

/** Resolves backend-neutral GPU pipeline requests into backend-specific handles. */
interface GPUPipelineProvider<PipelineHandle : Any, BindGroupLayoutHandle : Any> {
    /** Resolves a fullscreen uniform pipeline for the requested shader, target format, and blend mode. */
    fun resolveFullscreenPipeline(
        request: GPUFullscreenPipelineRequest,
    ): GPUPipelineResolution<PipelineHandle, BindGroupLayoutHandle>

    /** Resolves a textured vertex pipeline for recorder-local textured geometry. */
    fun resolveTexturedVertexPipeline(
        request: GPUTexturedVertexPipelineRequest,
    ): GPUPipelineResolution<PipelineHandle, BindGroupLayoutHandle>

    /** Resolves a dual-UV textured vertex pipeline for recorder-local dual-source geometry. */
    fun resolveDualUvVertexPipeline(
        request: GPUDualUvVertexPipelineRequest,
    ): GPUPipelineResolution<PipelineHandle, BindGroupLayoutHandle>

    /** Resolves a render-step pipeline for a stable render-step identity and version. */
    fun resolveRenderStepPipeline(
        request: GPURenderStepPipelineRequest,
    ): GPUPipelineResolution<PipelineHandle, BindGroupLayoutHandle>
}

/** Backend-specific pipeline plus any bind-group layouts needed to encode commands. */
data class GPUPipelineResolution<out PipelineHandle : Any, out BindGroupLayoutHandle : Any>(
    val pipeline: PipelineHandle,
    val bindGroupLayouts: List<BindGroupLayoutHandle> = emptyList(),
)

/** Fullscreen pipeline request expressed with generic GPU contract enums. */
data class GPUFullscreenPipelineRequest(
    val shaderSource: String,
    val colorFormat: GPUTextureFormat,
    val blendMode: GPUBlendMode? = null,
) {
    init {
        require(shaderSource.isNotBlank()) { "shaderSource must not be blank" }
    }
}

/** Textured vertex pipeline request expressed without backend-native handles. */
data class GPUTexturedVertexPipelineRequest(
    val shaderSource: String,
    val colorFormat: GPUTextureFormat,
    val textureFormat: GPUTextureFormat,
    val blendMode: GPUBlendMode? = null,
) {
    init {
        require(shaderSource.isNotBlank()) { "shaderSource must not be blank" }
    }
}

/** Dual-UV vertex pipeline request expressed without backend-native handles. */
data class GPUDualUvVertexPipelineRequest(
    val shaderSource: String,
    val colorFormat: GPUTextureFormat,
    val textureFormat: GPUTextureFormat,
    val blendMode: GPUBlendMode? = null,
) {
    init {
        require(shaderSource.isNotBlank()) { "shaderSource must not be blank" }
    }
}

/** Render-step pipeline request keyed by stable render-step identity and version. */
data class GPURenderStepPipelineRequest(
    val renderStepIdentity: String,
    val renderStepVersion: String,
    val colorFormat: GPUTextureFormat,
    val blendMode: GPUBlendMode? = null,
) {
    init {
        require(renderStepIdentity.isNotBlank()) { "renderStepIdentity must not be blank" }
        require(renderStepVersion.isNotBlank()) { "renderStepVersion must not be blank" }
    }
}
