package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.BindGroupLayoutDescriptor
import io.ygdrasil.webgpu.BindGroupLayoutEntry
import io.ygdrasil.webgpu.BufferBindingLayout
import io.ygdrasil.webgpu.ColorTargetState
import io.ygdrasil.webgpu.DepthStencilState
import io.ygdrasil.webgpu.FragmentState
import io.ygdrasil.webgpu.GPUBufferBindingType
import io.ygdrasil.webgpu.GPUCompareFunction
import io.ygdrasil.webgpu.GPUBindGroupLayout
import io.ygdrasil.webgpu.GPUDevice
import io.ygdrasil.webgpu.GPURenderPipeline
import io.ygdrasil.webgpu.GPUSamplerBindingType
import io.ygdrasil.webgpu.GPUShaderStage
import io.ygdrasil.webgpu.GPUStencilOperation
import io.ygdrasil.webgpu.GPUTextureFormat
import io.ygdrasil.webgpu.GPUTextureSampleType
import io.ygdrasil.webgpu.GPUTextureViewDimension
import io.ygdrasil.webgpu.GPUVertexFormat
import io.ygdrasil.webgpu.PipelineLayoutDescriptor
import io.ygdrasil.webgpu.PrimitiveState
import io.ygdrasil.webgpu.RenderPipelineDescriptor
import io.ygdrasil.webgpu.SamplerBindingLayout
import io.ygdrasil.webgpu.ShaderModuleDescriptor
import io.ygdrasil.webgpu.StencilFaceState
import io.ygdrasil.webgpu.TextureBindingLayout
import io.ygdrasil.webgpu.VertexAttribute
import io.ygdrasil.webgpu.VertexBufferLayout
import io.ygdrasil.webgpu.VertexState
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode
import org.graphiks.kanvas.gpu.renderer.wgsl.TexturedVerticesDualBlendWgsl
import org.graphiks.kanvas.gpu.renderer.wgsl.TexturedVerticesWgsl

/** Owns recorder-local GPU textured vertex pipeline caches. */
internal class GPURecorderPipelineProvider(
    private val device: GPUDevice,
    private val targetFormat: GPUTextureFormat,
) : AutoCloseable {
    private var texturedVertexPipelineCache = mutableMapOf<String, GPURenderPipeline>()
    private var texturedVertexBindGroupLayout: GPUBindGroupLayout? = null
    private var dualUVVertexPipelineCache = mutableMapOf<String, GPURenderPipeline>()
    private var dualUVVertexBindGroupLayout: GPUBindGroupLayout? = null

    /** Returns the recorder-local bind-group layout for single-texture vertex draws. */
    fun texturedVertexBindGroupLayout(): GPUBindGroupLayout =
        getOrCreateTexturedVertexBindGroupLayout()

    /** Returns the recorder-local render pipeline for single-texture vertex draws. */
    fun texturedVertexPipeline(
        colorFormat: GPUTextureFormat,
        blendMode: GPUBlendMode?,
    ): GPURenderPipeline =
        getOrCreateTexturedVertexPipeline(
            colorFormat = colorFormat.toBackendColorFormat(),
            blendMode = blendMode,
        )

    /** Returns the recorder-local bind-group layout for dual-UV vertex draws. */
    fun dualUVVertexBindGroupLayout(): GPUBindGroupLayout =
        getOrCreateDualUVVertexBindGroupLayout()

    /** Returns the recorder-local render pipeline for dual-UV vertex draws. */
    fun dualUVVertexPipeline(
        colorFormat: GPUTextureFormat,
        blendMode: GPUBlendMode?,
    ): GPURenderPipeline =
        getOrCreateDualUVVertexPipeline(
            colorFormat = colorFormat.toBackendColorFormat(),
            blendMode = blendMode,
        )

    override fun close() {
        closeCachedResources()
    }

    private fun closeCachedResources() {
        texturedVertexBindGroupLayout?.let { closeQuietly { it.close() } }
        dualUVVertexBindGroupLayout?.let { closeQuietly { it.close() } }
        texturedVertexPipelineCache.values.forEach { closeQuietly { it.close() } }
        dualUVVertexPipelineCache.values.forEach { closeQuietly { it.close() } }
        texturedVertexPipelineCache.clear()
        dualUVVertexPipelineCache.clear()
        texturedVertexBindGroupLayout = null
        dualUVVertexBindGroupLayout = null
    }

    private fun getOrCreateTexturedVertexBindGroupLayout(): GPUBindGroupLayout {
        if (texturedVertexBindGroupLayout == null) {
            texturedVertexBindGroupLayout = device.createBindGroupLayout(
                BindGroupLayoutDescriptor(
                    label = "texturedVertexLayout",
                    entries = listOf(
                        BindGroupLayoutEntry(
                            binding = 0u,
                            visibility = GPUShaderStage.Vertex or GPUShaderStage.Fragment,
                            buffer = BufferBindingLayout(type = GPUBufferBindingType.Uniform),
                        ),
                        BindGroupLayoutEntry(
                            binding = 1u,
                            visibility = GPUShaderStage.Fragment,
                            texture = TextureBindingLayout(
                                sampleType = GPUTextureSampleType.Float,
                                viewDimension = GPUTextureViewDimension.TwoD,
                                multisampled = false,
                            ),
                        ),
                        BindGroupLayoutEntry(
                            binding = 2u,
                            visibility = GPUShaderStage.Fragment,
                            sampler = SamplerBindingLayout(type = GPUSamplerBindingType.Filtering),
                        ),
                    ),
                ),
            )
        }
        return texturedVertexBindGroupLayout!!
    }

    private fun getOrCreateTexturedVertexPipeline(colorFormat: String, blendMode: GPUBlendMode?): GPURenderPipeline {
        val key = "$colorFormat:${blendMode?.name ?: "none"}"
        return texturedVertexPipelineCache.getOrPut(key) {
            createTexturedVertexPipeline(colorFormat, blendMode)
        }
    }

    private fun createTexturedVertexPipeline(colorFormat: String, blendMode: GPUBlendMode?): GPURenderPipeline {
        val shaderModule = device.createShaderModule(
            ShaderModuleDescriptor(label = "texturedVertex:$colorFormat", code = TexturedVerticesWgsl),
        )
        val bindGroupLayout = getOrCreateTexturedVertexBindGroupLayout()
        val pipelineLayout = device.createPipelineLayout(
            PipelineLayoutDescriptor(
                label = "texturedVertexLayout",
                bindGroupLayouts = listOf(bindGroupLayout),
            ),
        )
        try {
            return device.createRenderPipelineWithValidationScope(
                RenderPipelineDescriptor(
                    label = "texturedVertex:$colorFormat:${blendMode?.name ?: "none"}",
                    layout = pipelineLayout,
                    vertex = VertexState(
                        module = shaderModule,
                        entryPoint = "vs_main",
                        buffers = listOf(
                            VertexBufferLayout(
                                arrayStride = 16uL,
                                attributes = listOf(
                                    VertexAttribute(
                                        shaderLocation = 0u,
                                        offset = 0uL,
                                        format = GPUVertexFormat.Float32x2,
                                    ),
                                    VertexAttribute(
                                        shaderLocation = 1u,
                                        offset = 8uL,
                                        format = GPUVertexFormat.Float32x2,
                                    ),
                                ),
                            ),
                        ),
                    ),
                    primitive = PrimitiveState(),
                    depthStencil = DepthStencilState(
                        format = GPUTextureFormat.Depth24PlusStencil8,
                        depthWriteEnabled = false,
                        depthCompare = GPUCompareFunction.Always,
                        stencilFront = StencilFaceState(
                            compare = GPUCompareFunction.Always,
                            failOp = GPUStencilOperation.Keep,
                            depthFailOp = GPUStencilOperation.Keep,
                            passOp = GPUStencilOperation.Keep,
                        ),
                        stencilBack = StencilFaceState(
                            compare = GPUCompareFunction.Always,
                            failOp = GPUStencilOperation.Keep,
                            depthFailOp = GPUStencilOperation.Keep,
                            passOp = GPUStencilOperation.Keep,
                        ),
                        stencilReadMask = 0u,
                        stencilWriteMask = 0u,
                    ),
                    fragment = FragmentState(
                        module = shaderModule,
                        entryPoint = "fs_main",
                        targets = listOf(
                            ColorTargetState(
                                format = targetFormat,
                                blend = blendStateFor(blendMode),
                            ),
                        ),
                    ),
                ),
            )
        } finally {
            shaderModule.close()
            pipelineLayout.close()
        }
    }

    private fun getOrCreateDualUVVertexBindGroupLayout(): GPUBindGroupLayout {
        if (dualUVVertexBindGroupLayout == null) {
            dualUVVertexBindGroupLayout = device.createBindGroupLayout(
                BindGroupLayoutDescriptor(
                    label = "dualUVVertexLayout",
                    entries = listOf(
                        BindGroupLayoutEntry(
                            binding = 0u,
                            visibility = GPUShaderStage.Vertex or GPUShaderStage.Fragment,
                            buffer = BufferBindingLayout(type = GPUBufferBindingType.Uniform),
                        ),
                        BindGroupLayoutEntry(
                            binding = 1u,
                            visibility = GPUShaderStage.Fragment,
                            texture = TextureBindingLayout(
                                sampleType = GPUTextureSampleType.Float,
                                viewDimension = GPUTextureViewDimension.TwoD,
                                multisampled = false,
                            ),
                        ),
                        BindGroupLayoutEntry(
                            binding = 2u,
                            visibility = GPUShaderStage.Fragment,
                            sampler = SamplerBindingLayout(type = GPUSamplerBindingType.Filtering),
                        ),
                        BindGroupLayoutEntry(
                            binding = 3u,
                            visibility = GPUShaderStage.Fragment,
                            texture = TextureBindingLayout(
                                sampleType = GPUTextureSampleType.Float,
                                viewDimension = GPUTextureViewDimension.TwoD,
                                multisampled = false,
                            ),
                        ),
                        BindGroupLayoutEntry(
                            binding = 4u,
                            visibility = GPUShaderStage.Fragment,
                            sampler = SamplerBindingLayout(type = GPUSamplerBindingType.Filtering),
                        ),
                    ),
                ),
            )
        }
        return dualUVVertexBindGroupLayout!!
    }

    private fun getOrCreateDualUVVertexPipeline(colorFormat: String, blendMode: GPUBlendMode?): GPURenderPipeline {
        val key = "$colorFormat:${blendMode?.name ?: "none"}"
        return dualUVVertexPipelineCache.getOrPut(key) {
            createDualUVVertexPipeline(colorFormat, blendMode)
        }
    }

    private fun createDualUVVertexPipeline(colorFormat: String, blendMode: GPUBlendMode?): GPURenderPipeline {
        val shaderModule = device.createShaderModule(
            ShaderModuleDescriptor(label = "dualUVVertex:$colorFormat", code = TexturedVerticesDualBlendWgsl),
        )
        val bindGroupLayout = getOrCreateDualUVVertexBindGroupLayout()
        val pipelineLayout = device.createPipelineLayout(
            PipelineLayoutDescriptor(
                label = "dualUVVertexLayout",
                bindGroupLayouts = listOf(bindGroupLayout),
            ),
        )
        try {
            return device.createRenderPipelineWithValidationScope(
                RenderPipelineDescriptor(
                    label = "dualUVVertex:$colorFormat:${blendMode?.name ?: "none"}",
                    layout = pipelineLayout,
                    vertex = VertexState(
                        module = shaderModule,
                        entryPoint = "vs_main",
                        buffers = listOf(
                            VertexBufferLayout(
                                arrayStride = 24uL,
                                attributes = listOf(
                                    VertexAttribute(
                                        shaderLocation = 0u,
                                        offset = 0uL,
                                        format = GPUVertexFormat.Float32x2,
                                    ),
                                    VertexAttribute(
                                        shaderLocation = 1u,
                                        offset = 8uL,
                                        format = GPUVertexFormat.Float32x2,
                                    ),
                                    VertexAttribute(
                                        shaderLocation = 2u,
                                        offset = 16uL,
                                        format = GPUVertexFormat.Float32x2,
                                    ),
                                ),
                            ),
                        ),
                    ),
                    primitive = PrimitiveState(),
                    depthStencil = DepthStencilState(
                        format = GPUTextureFormat.Depth24PlusStencil8,
                        depthWriteEnabled = false,
                        depthCompare = GPUCompareFunction.Always,
                        stencilFront = StencilFaceState(
                            compare = GPUCompareFunction.Always,
                            failOp = GPUStencilOperation.Keep,
                            depthFailOp = GPUStencilOperation.Keep,
                            passOp = GPUStencilOperation.Keep,
                        ),
                        stencilBack = StencilFaceState(
                            compare = GPUCompareFunction.Always,
                            failOp = GPUStencilOperation.Keep,
                            depthFailOp = GPUStencilOperation.Keep,
                            passOp = GPUStencilOperation.Keep,
                        ),
                        stencilReadMask = 0u,
                        stencilWriteMask = 0u,
                    ),
                    fragment = FragmentState(
                        module = shaderModule,
                        entryPoint = "fs_main",
                        targets = listOf(
                            ColorTargetState(
                                format = targetFormat,
                                blend = blendStateFor(blendMode),
                            ),
                        ),
                    ),
                ),
            )
        } finally {
            shaderModule.close()
            pipelineLayout.close()
        }
    }

    private fun closeQuietly(block: () -> Unit) {
        try { block() } catch (_: Throwable) { }
    }
}
