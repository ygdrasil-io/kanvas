package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.BindGroupLayoutDescriptor
import io.ygdrasil.webgpu.BindGroupLayoutEntry
import io.ygdrasil.webgpu.BlendComponent
import io.ygdrasil.webgpu.BlendState
import io.ygdrasil.webgpu.BufferBindingLayout
import io.ygdrasil.webgpu.ColorTargetState
import io.ygdrasil.webgpu.DepthStencilState
import io.ygdrasil.webgpu.FragmentState
import io.ygdrasil.webgpu.GPUBlendOperation
import io.ygdrasil.webgpu.GPUBufferBindingType
import io.ygdrasil.webgpu.GPUColorWrite
import io.ygdrasil.webgpu.GPUCompareFunction
import io.ygdrasil.webgpu.GPUBindGroupLayout
import io.ygdrasil.webgpu.GPUDevice
import io.ygdrasil.webgpu.GPUErrorFilter
import io.ygdrasil.webgpu.GPUPipelineLayout
import io.ygdrasil.webgpu.GPURenderPipeline
import io.ygdrasil.webgpu.GPUSamplerBindingType
import io.ygdrasil.webgpu.GPUShaderModule
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
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendFactor
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode
import org.graphiks.kanvas.gpu.renderer.pipelines.GPUDualUvVertexPipelineRequest
import org.graphiks.kanvas.gpu.renderer.pipelines.GPUFullscreenPipelineRequest
import org.graphiks.kanvas.gpu.renderer.pipelines.GPUPipelineProvider
import org.graphiks.kanvas.gpu.renderer.pipelines.GPUPipelineResolution
import org.graphiks.kanvas.gpu.renderer.pipelines.GPUPipelineKeyPreimage
import org.graphiks.kanvas.gpu.renderer.pipelines.GPUPipelineKeys
import org.graphiks.kanvas.gpu.renderer.pipelines.GPURenderStepPipelineRequest
import org.graphiks.kanvas.gpu.renderer.pipelines.GPUTexturedVertexPipelineRequest
import org.graphiks.kanvas.gpu.renderer.state.GPUBlendMode as ContractGPUBlendMode
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUCacheTelemetry
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUTelemetryLedger

private const val VERTEX_COLOR_STRIDE_BYTES: Int = 32
private const val TEXT_ATLAS_VERTEX_STRIDE_BYTES: Int = 16

internal class GPUBackendPipelineProvider(
    private val device: GPUDevice,
    private val deviceGeneration: GPUDeviceGeneration,
) : GPUPipelineProvider<GPURenderPipeline, GPUBindGroupLayout>, AutoCloseable {
    private val moduleCache = GPUExecutionObjectCache(
        domain = GPUExecutionCacheDomain.Module,
        dispose = GPUShaderModule::close,
    )
    private val bindGroupLayoutCache =
        GPUExecutionObjectCache(
            domain = GPUExecutionCacheDomain.BindGroupLayout,
            dispose = GPUBindGroupLayout::close,
        )
    private val pipelineLayoutCache =
        GPUExecutionObjectCache(
            domain = GPUExecutionCacheDomain.PipelineLayout,
            dispose = GPUPipelineLayout::close,
        )
    private val renderPipelineCache =
        GPUExecutionObjectCache(
            domain = GPUExecutionCacheDomain.RenderPipeline,
            dispose = GPURenderPipeline::close,
        )
    private var ledger = GPUTelemetryLedger.empty()
    private val recordedDumpLines = mutableListOf<String>()
    private val recordedPreimageKeys = linkedSetOf<String>()

    val cacheTelemetry: List<GPUCacheTelemetry>
        get() = ledger.cacheTelemetry

    val dumpLines: List<String>
        get() = recordedDumpLines.toList()

    override fun resolveFullscreenPipeline(
        request: GPUFullscreenPipelineRequest,
    ): GPUPipelineResolution<GPURenderPipeline, GPUBindGroupLayout> =
        resolveFullscreenPipelineForBackendBlend(
            wgsl = request.shaderSource,
            targetFormat = request.colorFormat,
            blendMode = request.blendMode.toGPUBackendBlendModeForFullscreen(),
        )

    override fun resolveTexturedVertexPipeline(
        request: GPUTexturedVertexPipelineRequest,
    ): GPUPipelineResolution<GPURenderPipeline, GPUBindGroupLayout> =
        error(
            "GPUBackendPipelineProvider.resolveTexturedVertexPipeline remains recorder-local in " +
                "backend render recorder until the recorder-local provider extraction task migrates it",
        )

    override fun resolveDualUvVertexPipeline(
        request: GPUDualUvVertexPipelineRequest,
    ): GPUPipelineResolution<GPURenderPipeline, GPUBindGroupLayout> =
        error(
            "GPUBackendPipelineProvider.resolveDualUvVertexPipeline remains recorder-local in " +
                "backend render recorder until the recorder-local provider extraction task migrates it",
        )

    override fun resolveRenderStepPipeline(
        request: GPURenderStepPipelineRequest,
    ): GPUPipelineResolution<GPURenderPipeline, GPUBindGroupLayout> =
        error(
            "GPUBackendPipelineProvider.resolveRenderStepPipeline is not routed until " +
                "render-step pipeline resolution is migrated",
        )

    internal fun resolveFullscreenPipelineForBackendBlend(
        wgsl: String,
        targetFormat: GPUTextureFormat,
        blendMode: GPUBlendMode? = null,
    ): GPUPipelineResolution<GPURenderPipeline, GPUBindGroupLayout> {
        val keys = fullscreenExecutionCacheKeys(wgsl = wgsl, targetFormat = targetFormat, blendMode = blendMode)
        recordPreimages(keys)
        val bindGroupLayout = bindGroupLayout(keys = keys)
        val shader = shaderModule(wgsl = wgsl, keys = keys)
        val pipelineLayout = pipelineLayout(
            bindGroupLayout = bindGroupLayout,
            keys = keys,
        )
        val pipeline = renderPipeline(
            shader = shader,
            pipelineLayout = pipelineLayout,
            targetFormat = targetFormat,
            keys = keys,
            blendMode = blendMode,
        )
        return GPUPipelineResolution(
            pipeline = pipeline,
            bindGroupLayouts = listOf(bindGroupLayout),
        )
    }

    internal fun resolveFullscreenTexturePipeline(
        wgsl: String,
        targetFormat: GPUTextureFormat,
        textureFormat: GPUTextureFormat,
        blendMode: GPUBlendMode?,
    ): GPUPipelineResolution<GPURenderPipeline, GPUBindGroupLayout> {
        val keys = fullscreenTextureExecutionCacheKeys(
            wgsl = wgsl,
            targetFormat = targetFormat,
            textureFormat = textureFormat,
            blendMode = blendMode,
        )
        recordPreimages(keys)
        val bindGroupLayout = bindGroupLayout(keys = keys)
        val textureBindGroupLayout = textureBindGroupLayout(keys = keys)
        val shader = shaderModule(wgsl = wgsl, keys = keys)
        val pipelineLayout = texturePipelineLayout(
            bindGroupLayouts = listOf(bindGroupLayout, textureBindGroupLayout),
            keys = keys,
        )
        val pipeline = renderPipeline(
            shader = shader,
            pipelineLayout = pipelineLayout,
            targetFormat = targetFormat,
            keys = keys,
            blendMode = blendMode,
        )
        return GPUPipelineResolution(
            pipeline = pipeline,
            bindGroupLayouts = listOf(bindGroupLayout, textureBindGroupLayout),
        )
    }

    internal fun resolveBlendTexturePipeline(
        wgsl: String,
        targetFormat: GPUTextureFormat,
        textureFormat: GPUTextureFormat,
    ): GPUPipelineResolution<GPURenderPipeline, GPUBindGroupLayout> {
        val keys = blendTextureExecutionCacheKeys(
            wgsl = wgsl,
            targetFormat = targetFormat,
            textureFormat = textureFormat,
        )
        recordPreimages(keys)
        val bindGroupLayout = bindGroupLayout(keys = keys)
        val textureBindGroupLayout = blendTextureBindGroupLayout(keys = keys)
        val shader = shaderModule(wgsl = wgsl, keys = keys)
        val pipelineLayout = texturePipelineLayout(
            bindGroupLayouts = listOf(bindGroupLayout, textureBindGroupLayout),
            keys = keys,
        )
        val pipeline = renderPipeline(
            shader = shader,
            pipelineLayout = pipelineLayout,
            targetFormat = targetFormat,
            keys = keys,
        )
        return GPUPipelineResolution(
            pipeline = pipeline,
            bindGroupLayouts = listOf(bindGroupLayout, textureBindGroupLayout),
        )
    }

    internal fun resolveTextAtlasPipeline(
        wgsl: String,
        targetFormat: GPUTextureFormat,
        textureFormat: GPUTextureFormat,
        blendMode: GPUBlendMode?,
    ): GPUPipelineResolution<GPURenderPipeline, GPUBindGroupLayout> {
        val keys = textAtlasExecutionCacheKeys(
            wgsl = wgsl,
            targetFormat = targetFormat,
            textureFormat = textureFormat,
            blendMode = blendMode,
        )
        recordPreimages(keys)
        val bindGroupLayout = bindGroupLayout(keys = keys)
        val textureBindGroupLayout = textureBindGroupLayout(keys = keys)
        val shader = shaderModule(wgsl = wgsl, keys = keys)
        val pipelineLayout = texturePipelineLayout(
            bindGroupLayouts = listOf(bindGroupLayout, textureBindGroupLayout),
            keys = keys,
        )
        val pipeline = textAtlasRenderPipeline(
            shader = shader,
            pipelineLayout = pipelineLayout,
            targetFormat = targetFormat,
            keys = keys,
            blendMode = blendMode,
        )
        return GPUPipelineResolution(
            pipeline = pipeline,
            bindGroupLayouts = listOf(bindGroupLayout, textureBindGroupLayout),
        )
    }

    internal fun resolveColorGlyphPipeline(
        wgsl: String,
        targetFormat: GPUTextureFormat,
        textureFormat: GPUTextureFormat,
        blendMode: GPUBlendMode?,
    ): GPUPipelineResolution<GPURenderPipeline, GPUBindGroupLayout> {
        val keys = colorGlyphExecutionCacheKeys(
            wgsl = wgsl,
            targetFormat = targetFormat,
            textureFormat = textureFormat,
            blendMode = blendMode,
        )
        recordPreimages(keys)
        val bindGroupLayout = bindGroupLayout(keys = keys)
        val textureBindGroupLayout = textureBindGroupLayout(keys = keys)
        val shader = shaderModule(wgsl = wgsl, keys = keys)
        val pipelineLayout = texturePipelineLayout(
            bindGroupLayouts = listOf(bindGroupLayout, textureBindGroupLayout),
            keys = keys,
        )
        val pipeline = textAtlasRenderPipeline(
            shader = shader,
            pipelineLayout = pipelineLayout,
            targetFormat = targetFormat,
            keys = keys,
            blendMode = blendMode,
        )
        return GPUPipelineResolution(
            pipeline = pipeline,
            bindGroupLayouts = listOf(bindGroupLayout, textureBindGroupLayout),
        )
    }

    /** Records stable cache-key preimage dumps once per cache key. */
    fun recordPreimages(keys: FullscreenExecutionCacheKeys) {
        keys.preimageDumpLines().forEach { line ->
            if (recordedPreimageKeys.add(line)) {
                recordedDumpLines += line
            }
        }
    }

    /** Returns a cached shader module for the stable fullscreen WGSL identity. */
    fun shaderModule(
        wgsl: String,
        keys: FullscreenExecutionCacheKeys,
    ): GPUShaderModule {
        val decision = moduleCache.getOrCreate(
            request = request(
                domain = GPUExecutionCacheDomain.Module,
                keyHash = keys.moduleKeyHash,
                subjectHash = keys.moduleSubjectHash,
            ),
        ) {
            device.createShaderModule(ShaderModuleDescriptor(code = wgsl))
        }
        record(decision)
        return decision.readyHandle()
    }

    /** Returns the cached bind-group layout accepted by the fullscreen uniform lane. */
    fun bindGroupLayout(
        keys: FullscreenExecutionCacheKeys,
    ): GPUBindGroupLayout {
        val decision = bindGroupLayoutCache.getOrCreate(
            request = request(
                domain = GPUExecutionCacheDomain.BindGroupLayout,
                keyHash = keys.bindGroupLayoutKeyHash,
                subjectHash = keys.bindGroupLayoutSubjectHash,
            ),
        ) {
            device.createBindGroupLayout(
                BindGroupLayoutDescriptor(
                    entries = listOf(
                    BindGroupLayoutEntry(
                        binding = 0u,
                        visibility = GPUShaderStage.Vertex or GPUShaderStage.Fragment,
                        buffer = BufferBindingLayout(type = GPUBufferBindingType.Uniform),
                    ),
                    ),
                ),
            )
        }
        record(decision)
        return decision.readyHandle()
    }

    /** Returns the cached pipeline layout derived from the stable bind-group layout key. */
    fun pipelineLayout(
        bindGroupLayout: GPUBindGroupLayout,
        keys: FullscreenExecutionCacheKeys,
    ): GPUPipelineLayout {
        val decision = pipelineLayoutCache.getOrCreate(
            request = request(
                domain = GPUExecutionCacheDomain.PipelineLayout,
                keyHash = keys.pipelineLayoutKeyHash,
                subjectHash = keys.pipelineLayoutSubjectHash,
            ),
        ) {
            device.createPipelineLayout(
                PipelineLayoutDescriptor(bindGroupLayouts = listOf(bindGroupLayout)),
            )
        }
        record(decision)
        return decision.readyHandle()
    }

    /** Returns the cached texture bind-group layout (binding 1=texture, binding 2=sampler) at @group(1). */
    fun textureBindGroupLayout(
        keys: FullscreenExecutionCacheKeys,
    ): GPUBindGroupLayout {
        val decision = bindGroupLayoutCache.getOrCreate(
            request = request(
                domain = GPUExecutionCacheDomain.BindGroupLayout,
                keyHash = keys.textureBindGroupLayoutKeyHash,
                subjectHash = keys.textureBindGroupLayoutSubjectHash,
            ),
        ) {
            device.createBindGroupLayout(
                BindGroupLayoutDescriptor(
                    entries = listOf(
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
        record(decision)
        return decision.readyHandle()
    }

    /** Returns cached dual-texture bind-group layout (bindings 1-4: src tex+sampler, dst tex+sampler) at @group(1). */
    fun blendTextureBindGroupLayout(
        keys: FullscreenExecutionCacheKeys,
    ): GPUBindGroupLayout {
        val decision = bindGroupLayoutCache.getOrCreate(
            request = request(
                domain = GPUExecutionCacheDomain.BindGroupLayout,
                keyHash = keys.textureBindGroupLayoutKeyHash,
                subjectHash = keys.textureBindGroupLayoutSubjectHash,
            ),
        ) {
            device.createBindGroupLayout(
                BindGroupLayoutDescriptor(
                    entries = listOf(
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
        record(decision)
        return decision.readyHandle()
    }

    /** Returns the cached pipeline layout for texture passes with two bind-group layouts. */
    fun texturePipelineLayout(
        bindGroupLayouts: List<GPUBindGroupLayout>,
        keys: FullscreenExecutionCacheKeys,
    ): GPUPipelineLayout {
        val decision = pipelineLayoutCache.getOrCreate(
            request = request(
                domain = GPUExecutionCacheDomain.PipelineLayout,
                keyHash = keys.pipelineLayoutKeyHash,
                subjectHash = keys.pipelineLayoutSubjectHash,
            ),
        ) {
            device.createPipelineLayout(
                PipelineLayoutDescriptor(bindGroupLayouts = bindGroupLayouts),
            )
        }
        record(decision)
        return decision.readyHandle()
    }

    /** Returns the cached render pipeline for the module, layout, target, and blend-state facts. */
    fun renderPipeline(
        shader: GPUShaderModule,
        pipelineLayout: GPUPipelineLayout,
        targetFormat: GPUTextureFormat,
        keys: FullscreenExecutionCacheKeys,
        blendMode: GPUBlendMode? = null,
    ): GPURenderPipeline {
        val decision = renderPipelineCache.getOrCreate(
            request = request(
                domain = GPUExecutionCacheDomain.RenderPipeline,
                keyHash = keys.renderPipelineKeyHash,
                subjectHash = keys.renderPipelineSubjectHash,
            ),
        ) {
            device.createRenderPipelineWithValidationScope(
                RenderPipelineDescriptor(
                    layout = pipelineLayout,
                    vertex = VertexState(module = shader, entryPoint = "vs_main"),
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
                        module = shader,
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
        }
        record(decision)
        return decision.readyHandle()
    }

    /** Returns the cached stencil-write render pipeline with vertex buffer input, no color writes, and winding stencil ops. */
    fun stencilWriteRenderPipeline(
        wgsl: String,
        targetFormat: GPUTextureFormat,
        keys: FullscreenExecutionCacheKeys,
    ): GPURenderPipeline {
        val decision = renderPipelineCache.getOrCreate(
            request = request(
                domain = GPUExecutionCacheDomain.RenderPipeline,
                keyHash = keys.renderPipelineKeyHash,
                subjectHash = keys.renderPipelineSubjectHash,
            ),
        ) {
            val shader = device.createShaderModule(ShaderModuleDescriptor(code = wgsl))
            try {
                val pipelineLayout = device.createPipelineLayout(
                    PipelineLayoutDescriptor(bindGroupLayouts = emptyList()),
                )
                device.createRenderPipelineWithValidationScope(
                    RenderPipelineDescriptor(
                        layout = pipelineLayout,
                        vertex = VertexState(
                            module = shader,
                            entryPoint = "vs_main",
                            buffers = listOf(
                                VertexBufferLayout(
                                    arrayStride = 8uL,
                                    attributes = listOf(
                                        VertexAttribute(
                                            shaderLocation = 0u,
                                            offset = 0uL,
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
                                passOp = GPUStencilOperation.IncrementWrap,
                            ),
                            stencilBack = StencilFaceState(
                                compare = GPUCompareFunction.Always,
                                failOp = GPUStencilOperation.Keep,
                                depthFailOp = GPUStencilOperation.Keep,
                                passOp = GPUStencilOperation.DecrementWrap,
                            ),
                            stencilReadMask = 0xFFu,
                            stencilWriteMask = 0xFFu,
                        ),
                        fragment = FragmentState(
                            module = shader,
                            entryPoint = "fs_main",
                            targets = listOf(
                                ColorTargetState(
                                    format = targetFormat,
                                    blend = BlendState(
                                        color = BlendComponent(
                                            operation = GPUBlendOperation.Add,
                                            srcFactor = io.ygdrasil.webgpu.GPUBlendFactor.One,
                                            dstFactor = io.ygdrasil.webgpu.GPUBlendFactor.Zero,
                                        ),
                                        alpha = BlendComponent(
                                            operation = GPUBlendOperation.Add,
                                            srcFactor = io.ygdrasil.webgpu.GPUBlendFactor.One,
                                            dstFactor = io.ygdrasil.webgpu.GPUBlendFactor.Zero,
                                        ),
                                    ),
                                    writeMask = GPUColorWrite.None,
                                ),
                            ),
                        ),
                    ),
                )
            } finally {
                shader.close()
            }
        }
        record(decision)
        return decision.readyHandle()
    }

    /** Returns the cached stencil-test render pipeline that fills pixels where stencil != 0 with the fragment color. */
    fun stencilTestRenderPipeline(
        shader: GPUShaderModule,
        pipelineLayout: GPUPipelineLayout,
        targetFormat: GPUTextureFormat,
        keys: FullscreenExecutionCacheKeys,
        blendMode: GPUBlendMode? = null,
    ): GPURenderPipeline {
        val decision = renderPipelineCache.getOrCreate(
            request = request(
                domain = GPUExecutionCacheDomain.RenderPipeline,
                keyHash = keys.renderPipelineKeyHash,
                subjectHash = keys.renderPipelineSubjectHash,
            ),
        ) {
            device.createRenderPipelineWithValidationScope(
                RenderPipelineDescriptor(
                    layout = pipelineLayout,
                    vertex = VertexState(module = shader, entryPoint = "vs_main"),
                    primitive = PrimitiveState(),
                    depthStencil = DepthStencilState(
                        format = GPUTextureFormat.Depth24PlusStencil8,
                        depthWriteEnabled = false,
                        depthCompare = GPUCompareFunction.Always,
                        stencilFront = StencilFaceState(
                            compare = GPUCompareFunction.NotEqual,
                            failOp = GPUStencilOperation.Keep,
                            depthFailOp = GPUStencilOperation.Keep,
                            passOp = GPUStencilOperation.Keep,
                        ),
                        stencilBack = StencilFaceState(
                            compare = GPUCompareFunction.NotEqual,
                            failOp = GPUStencilOperation.Keep,
                            depthFailOp = GPUStencilOperation.Keep,
                            passOp = GPUStencilOperation.Keep,
                        ),
                        stencilReadMask = 0xFFu,
                        stencilWriteMask = 0xFFu,
                    ),
                    fragment = FragmentState(
                        module = shader,
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
        }
        record(decision)
        return decision.readyHandle()
    }

    /** Returns the cached vertex-color render pipeline with interleaved position+color vertex buffer input. */
    fun vertexColorRenderPipeline(
        shader: GPUShaderModule,
        pipelineLayout: GPUPipelineLayout,
        targetFormat: GPUTextureFormat,
        keys: FullscreenExecutionCacheKeys,
        blendMode: GPUBlendMode? = null,
    ): GPURenderPipeline {
        val decision = renderPipelineCache.getOrCreate(
            request = request(
                domain = GPUExecutionCacheDomain.RenderPipeline,
                keyHash = keys.renderPipelineKeyHash,
                subjectHash = keys.renderPipelineSubjectHash,
            ),
        ) {
            device.createRenderPipelineWithValidationScope(
                RenderPipelineDescriptor(
                    layout = pipelineLayout,
                    vertex = VertexState(
                        module = shader,
                        entryPoint = "vs_main",
                        buffers = listOf(
                            VertexBufferLayout(
                                arrayStride = VERTEX_COLOR_STRIDE_BYTES.toULong(),
                                attributes = listOf(
                                    VertexAttribute(
                                        shaderLocation = 0u,
                                        offset = 0uL,
                                        format = GPUVertexFormat.Float32x2,
                                    ),
                            VertexAttribute(
                                shaderLocation = 1u,
                                offset = 16uL,
                                format = GPUVertexFormat.Float32x4,
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
                        module = shader,
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
        }
        record(decision)
        return decision.readyHandle()
    }

    /** Returns the cached text atlas A8 render pipeline with position+texcoord vertex buffers and indexed draw. */
    fun textAtlasRenderPipeline(
        shader: GPUShaderModule,
        pipelineLayout: GPUPipelineLayout,
        targetFormat: GPUTextureFormat,
        keys: FullscreenExecutionCacheKeys,
        blendMode: GPUBlendMode? = null,
    ): GPURenderPipeline {
        val decision = renderPipelineCache.getOrCreate(
            request = request(
                domain = GPUExecutionCacheDomain.RenderPipeline,
                keyHash = keys.renderPipelineKeyHash,
                subjectHash = keys.renderPipelineSubjectHash,
            ),
        ) {
            device.createRenderPipelineWithValidationScope(
                RenderPipelineDescriptor(
                    layout = pipelineLayout,
                    vertex = VertexState(
                        module = shader,
                        entryPoint = "vs_main",
                        buffers = listOf(
                            VertexBufferLayout(
                                arrayStride = TEXT_ATLAS_VERTEX_STRIDE_BYTES.toULong(),
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
                        module = shader,
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
        }
        record(decision)
        return decision.readyHandle()
    }

    private fun request(
        domain: GPUExecutionCacheDomain,
        keyHash: String,
        subjectHash: String,
    ): GPUExecutionCacheRequest =
        GPUExecutionCacheRequest(
            domain = domain,
            keyHash = keyHash,
            subjectHash = subjectHash,
            deviceGeneration = deviceGeneration,
            expectedDeviceGeneration = deviceGeneration,
            ownerScope = "GPUResourceProvider",
        )

    private fun record(decision: GPUExecutionCacheDecision<*>) {
        decision.cacheEvents.forEach { event ->
            ledger = ledger.recordCacheEvent(event)
        }
        recordedDumpLines += decision.dumpLines()
    }

    internal fun resetLogicalSessionState() {
        ledger = GPUTelemetryLedger.empty()
        recordedDumpLines.clear()
        recordedPreimageKeys.clear()
    }

    override fun close() {
        var firstFailure: Throwable? = null
        listOf(
            renderPipelineCache,
            pipelineLayoutCache,
            bindGroupLayoutCache,
            moduleCache,
        ).forEach { cache ->
            try {
                cache.close()
            } catch (failure: Throwable) {
                if (firstFailure == null) {
                    firstFailure = failure
                } else {
                    firstFailure.addSuppressed(failure)
                }
            }
        }
        firstFailure?.let { throw it }
    }
}

internal fun ContractGPUBlendMode?.toGPUBackendBlendModeForFullscreen(): GPUBlendMode? = when (this) {
    null -> null
    ContractGPUBlendMode.Src -> GPUBlendMode.SRC
    ContractGPUBlendMode.SrcOver -> GPUBlendMode.SRC_OVER
    ContractGPUBlendMode.Multiply -> GPUBlendMode.MULTIPLY
    ContractGPUBlendMode.Screen -> GPUBlendMode.SCREEN
    ContractGPUBlendMode.DstOver -> error(
        "GPUBackendPipelineProvider does not support GPU blend mode DstOver yet",
    )
    ContractGPUBlendMode.Custom -> error(
        "GPUBackendPipelineProvider does not support GPU blend mode Custom yet",
    )
}

internal data class FullscreenExecutionCacheKeys(
    val moduleKeyHash: String,
    val moduleSubjectHash: String,
    val modulePreimage: String,
    val bindGroupLayoutKeyHash: String,
    val bindGroupLayoutSubjectHash: String,
    val bindGroupLayoutPreimage: String,
    val textureBindGroupLayoutKeyHash: String = "",
    val textureBindGroupLayoutSubjectHash: String = "",
    val textureBindGroupLayoutPreimage: String = "",
    val pipelineLayoutKeyHash: String,
    val pipelineLayoutSubjectHash: String,
    val pipelineLayoutPreimage: String,
    val renderPipelineKeyHash: String,
    val renderPipelineSubjectHash: String,
    val renderPipelinePreimage: String,
) {
    /** Emits backend-neutral cache-key preimage dumps without backend handles. */
    fun preimageDumpLines(): List<String> {
        val lines = mutableListOf(
            preimageDumpLine(
                domain = GPUExecutionCacheDomain.Module.telemetryDomain,
                keyHash = moduleKeyHash,
                subjectHash = moduleSubjectHash,
                preimage = modulePreimage,
            ),
            preimageDumpLine(
                domain = GPUExecutionCacheDomain.BindGroupLayout.telemetryDomain,
                keyHash = bindGroupLayoutKeyHash,
                subjectHash = bindGroupLayoutSubjectHash,
                preimage = bindGroupLayoutPreimage,
            ),
        )
        if (textureBindGroupLayoutKeyHash.isNotEmpty()) {
            lines += preimageDumpLine(
                domain = GPUExecutionCacheDomain.BindGroupLayout.telemetryDomain,
                keyHash = textureBindGroupLayoutKeyHash,
                subjectHash = textureBindGroupLayoutSubjectHash,
                preimage = textureBindGroupLayoutPreimage,
            )
        }
        lines += listOf(
            preimageDumpLine(
                domain = GPUExecutionCacheDomain.PipelineLayout.telemetryDomain,
                keyHash = pipelineLayoutKeyHash,
                subjectHash = pipelineLayoutSubjectHash,
                preimage = pipelineLayoutPreimage,
            ),
            preimageDumpLine(
                domain = GPUExecutionCacheDomain.RenderPipeline.telemetryDomain,
                keyHash = renderPipelineKeyHash,
                subjectHash = renderPipelineSubjectHash,
                preimage = renderPipelinePreimage,
            ),
        )
        return lines
    }

    private fun preimageDumpLine(
        domain: String,
        keyHash: String,
        subjectHash: String,
        preimage: String,
    ): String =
        "execution.cache.preimage domain=$domain key=$keyHash subject=$subjectHash " +
            "deviceScope=runtime-helper preimage=${preimage.dumpPreimage()}"
}

internal fun fullscreenTextureExecutionCacheKeys(
    wgsl: String,
    targetFormat: GPUTextureFormat,
    textureFormat: GPUTextureFormat,
    blendMode: GPUBlendMode? = null,
): FullscreenExecutionCacheKeys {
    val blendLabel = blendMode?.gpuLabel ?: "src_over"
    val targetFormatClass = targetFormat.toBackendColorFormat()
    val wgslHash = stableSha256(wgsl)
    val bindGroupLayoutPreimage = listOf(
        "kind=bind-group-layout",
        "role=fullscreen-uniform",
        "version=1",
        "binding=0",
        "visibility=fragment",
        "bufferType=uniform",
        "dynamicOffsets=false",
    ).joinToString("\n")
    val bindGroupLayoutHash = stableSha256(bindGroupLayoutPreimage)
    val textureBindGroupLayoutPreimage = listOf(
        "kind=bind-group-layout",
        "role=fullscreen-texture-sampler",
        "version=1",
        "binding=1:type=texture:format=${textureFormat.name}",
        "binding=2:type=sampler:filtering",
        "visibility=fragment",
    ).joinToString("\n")
    val textureBindGroupLayoutHash = stableSha256(textureBindGroupLayoutPreimage)
    val modulePreimage = listOf(
        "kind=wgsl-module",
        "role=fullscreen-texture-pass",
        "entryPoints=vs_main,fs_main",
        "wgsl=$wgslHash",
    ).joinToString("\n")
    val moduleHash = stableSha256(modulePreimage)
    val pipelineLayoutPreimage = listOf(
        "kind=pipeline-layout",
        "role=fullscreen-texture-pass",
        "version=1",
        "bindGroupLayouts=$bindGroupLayoutHash,$textureBindGroupLayoutHash",
    ).joinToString("\n")
    val pipelineLayoutHash = stableSha256(pipelineLayoutPreimage)
    val renderPreimage = GPUPipelineKeyPreimage.Render(
        renderStepIdentity = "gpu-backend.fullscreen-texture-pass",
        renderStepVersion = "1",
        primitiveTopology = "triangle-list",
        materialKeyHash = stableSha256("material:fullscreen-texture-color-uniform:v1"),
        materialProgramId = "wgsl.fullscreen-texture-color",
        materialDictionaryVersion = "runtime-helper-v1",
        materialLayoutHash = "$bindGroupLayoutHash,$textureBindGroupLayoutHash",
        snippetIdentityHash = stableSha256("snippet:fullscreen-texture:v1"),
        moduleHash = moduleHash,
        vertexLayoutHash = stableSha256("vertex-layout:fullscreen-triangle:vertex-index-only"),
        targetFormatClass = targetFormatClass,
        blendStateHash = stableSha256("blend:$blendLabel-premul:v1"),
        sampleStateHash = stableSha256("sample-state:count=1:mask=all"),
        bindGroupLayoutHash = "$bindGroupLayoutHash,$textureBindGroupLayoutHash",
        capabilityClass = "webgpu-wgsl-fullscreen-texture-pass",
        capabilityFacts = listOf("adapter-backed-helper", "targetFormat=$targetFormatClass", "textureFormat=${textureFormat.name}"),
        rendererSalt = "kgpu-m26-001",
    )
    val canonicalRenderPreimage = GPUPipelineKeys.canonicalRenderPreimage(renderPreimage)
    val renderPipelineKey = GPUPipelineKeys.renderPipelineKey(renderPreimage).value

    return FullscreenExecutionCacheKeys(
        moduleKeyHash = "module:$moduleHash",
        moduleSubjectHash = "wgsl:$wgslHash",
        modulePreimage = modulePreimage,
        bindGroupLayoutKeyHash = "bind-group-layout:$bindGroupLayoutHash",
        bindGroupLayoutSubjectHash = "layout-shape:$bindGroupLayoutHash",
        bindGroupLayoutPreimage = bindGroupLayoutPreimage,
        textureBindGroupLayoutKeyHash = "bind-group-layout:$textureBindGroupLayoutHash",
        textureBindGroupLayoutSubjectHash = "layout-shape:$textureBindGroupLayoutHash",
        textureBindGroupLayoutPreimage = textureBindGroupLayoutPreimage,
        pipelineLayoutKeyHash = "pipeline-layout:$pipelineLayoutHash",
        pipelineLayoutSubjectHash = "bind-groups:$bindGroupLayoutHash,$textureBindGroupLayoutHash",
        pipelineLayoutPreimage = pipelineLayoutPreimage,
        renderPipelineKeyHash = renderPipelineKey,
        renderPipelineSubjectHash = stableSha256(canonicalRenderPreimage),
        renderPipelinePreimage = canonicalRenderPreimage,
    )
}

internal fun blendTextureExecutionCacheKeys(
    wgsl: String,
    targetFormat: GPUTextureFormat,
    textureFormat: GPUTextureFormat,
): FullscreenExecutionCacheKeys {
    val targetFormatClass = targetFormat.toBackendColorFormat()
    val wgslHash = stableSha256(wgsl)
    val bindGroupLayoutPreimage = listOf(
        "kind=bind-group-layout",
        "role=blend-uniform",
        "version=1",
        "binding=0",
        "visibility=fragment",
        "bufferType=uniform",
        "dynamicOffsets=false",
    ).joinToString("\n")
    val bindGroupLayoutHash = stableSha256(bindGroupLayoutPreimage)
    val textureBindGroupLayoutPreimage = listOf(
        "kind=bind-group-layout",
        "role=blend-dual-texture-sampler",
        "version=1",
        "binding=1:type=texture:format=${textureFormat.name}",
        "binding=2:type=sampler:filtering",
        "binding=3:type=texture:format=${textureFormat.name}",
        "binding=4:type=sampler:filtering",
        "visibility=fragment",
    ).joinToString("\n")
    val textureBindGroupLayoutHash = stableSha256(textureBindGroupLayoutPreimage)
    val modulePreimage = listOf(
        "kind=wgsl-module",
        "role=blend-pass",
        "entryPoints=vs_main,fs_main",
        "wgsl=$wgslHash",
    ).joinToString("\n")
    val moduleHash = stableSha256(modulePreimage)
    val pipelineLayoutPreimage = listOf(
        "kind=pipeline-layout",
        "role=blend-pass",
        "version=1",
        "bindGroupLayouts=$bindGroupLayoutHash,$textureBindGroupLayoutHash",
    ).joinToString("\n")
    val pipelineLayoutHash = stableSha256(pipelineLayoutPreimage)
    val renderPreimage = GPUPipelineKeyPreimage.Render(
        renderStepIdentity = "gpu-backend.blend-pass",
        renderStepVersion = "1",
        primitiveTopology = "triangle-list",
        materialKeyHash = stableSha256("material:blend-uniform:v1"),
        materialProgramId = "wgsl.blend-pass",
        materialDictionaryVersion = "runtime-helper-v1",
        materialLayoutHash = "$bindGroupLayoutHash,$textureBindGroupLayoutHash",
        snippetIdentityHash = stableSha256("snippet:blend-pass:v1"),
        moduleHash = moduleHash,
        vertexLayoutHash = stableSha256("vertex-layout:fullscreen-triangle:vertex-index-only"),
        targetFormatClass = targetFormatClass,
        blendStateHash = stableSha256("blend:src_over-premul:v1"),
        sampleStateHash = stableSha256("sample-state:count=1:mask=all"),
        bindGroupLayoutHash = "$bindGroupLayoutHash,$textureBindGroupLayoutHash",
        capabilityClass = "webgpu-wgsl-blend-pass",
        capabilityFacts = listOf("adapter-backed-helper", "targetFormat=$targetFormatClass", "textureFormat=${textureFormat.name}"),
        rendererSalt = "kgpu-m26-001",
    )
    val canonicalRenderPreimage = GPUPipelineKeys.canonicalRenderPreimage(renderPreimage)
    val renderPipelineKey = GPUPipelineKeys.renderPipelineKey(renderPreimage).value

    return FullscreenExecutionCacheKeys(
        moduleKeyHash = "module:$moduleHash",
        moduleSubjectHash = "wgsl:$wgslHash",
        modulePreimage = modulePreimage,
        bindGroupLayoutKeyHash = "bind-group-layout:$bindGroupLayoutHash",
        bindGroupLayoutSubjectHash = "layout-shape:$bindGroupLayoutHash",
        bindGroupLayoutPreimage = bindGroupLayoutPreimage,
        textureBindGroupLayoutKeyHash = "bind-group-layout:$textureBindGroupLayoutHash",
        textureBindGroupLayoutSubjectHash = "layout-shape:$textureBindGroupLayoutHash",
        textureBindGroupLayoutPreimage = textureBindGroupLayoutPreimage,
        pipelineLayoutKeyHash = "pipeline-layout:$pipelineLayoutHash",
        pipelineLayoutSubjectHash = "bind-groups:$bindGroupLayoutHash,$textureBindGroupLayoutHash",
        pipelineLayoutPreimage = pipelineLayoutPreimage,
        renderPipelineKeyHash = renderPipelineKey,
        renderPipelineSubjectHash = stableSha256(canonicalRenderPreimage),
        renderPipelinePreimage = canonicalRenderPreimage,
    )
}

internal fun fullscreenExecutionCacheKeys(
    wgsl: String,
    targetFormat: GPUTextureFormat,
    blendMode: GPUBlendMode? = null,
): FullscreenExecutionCacheKeys {
    val blendLabel = blendMode?.gpuLabel ?: "src_over"
    val targetFormatClass = targetFormat.toBackendColorFormat()
    val wgslHash = stableSha256(wgsl)
    val bindGroupLayoutPreimage = listOf(
        "kind=bind-group-layout",
        "role=fullscreen-uniform",
        "version=1",
        "binding=0",
        "visibility=fragment",
        "bufferType=uniform",
        "dynamicOffsets=false",
    ).joinToString("\n")
    val bindGroupLayoutHash = stableSha256(bindGroupLayoutPreimage)
    val modulePreimage = listOf(
        "kind=wgsl-module",
        "role=fullscreen-pass",
        "entryPoints=vs_main,fs_main",
        "wgsl=$wgslHash",
    ).joinToString("\n")
    val moduleHash = stableSha256(modulePreimage)
    val pipelineLayoutPreimage = listOf(
        "kind=pipeline-layout",
        "role=fullscreen-pass",
        "version=1",
        "bindGroupLayouts=$bindGroupLayoutHash",
    ).joinToString("\n")
    val pipelineLayoutHash = stableSha256(pipelineLayoutPreimage)
    val renderPreimage = GPUPipelineKeyPreimage.Render(
        renderStepIdentity = "gpu-backend.fullscreen-pass",
        renderStepVersion = "1",
        primitiveTopology = "triangle-list",
        materialKeyHash = stableSha256("material:fullscreen-solid-color-uniform:v1"),
        materialProgramId = "wgsl.fullscreen-solid-color",
        materialDictionaryVersion = "runtime-helper-v1",
        materialLayoutHash = bindGroupLayoutHash,
        snippetIdentityHash = stableSha256("snippet:fullscreen-solid-color:v1"),
        moduleHash = moduleHash,
        vertexLayoutHash = stableSha256("vertex-layout:fullscreen-triangle:vertex-index-only"),
        targetFormatClass = targetFormatClass,
        blendStateHash = stableSha256("blend:$blendLabel-premul:v1"),
        sampleStateHash = stableSha256("sample-state:count=1:mask=all"),
        bindGroupLayoutHash = bindGroupLayoutHash,
        capabilityClass = "webgpu-wgsl-fullscreen-pass",
        capabilityFacts = listOf("adapter-backed-helper", "targetFormat=$targetFormatClass"),
        rendererSalt = "kgpu-m11-001",
    )
    val canonicalRenderPreimage = GPUPipelineKeys.canonicalRenderPreimage(renderPreimage)
    val renderPipelineKey = GPUPipelineKeys.renderPipelineKey(renderPreimage).value

    return FullscreenExecutionCacheKeys(
        moduleKeyHash = "module:$moduleHash",
        moduleSubjectHash = "wgsl:$wgslHash",
        modulePreimage = modulePreimage,
        bindGroupLayoutKeyHash = "bind-group-layout:$bindGroupLayoutHash",
        bindGroupLayoutSubjectHash = "layout-shape:$bindGroupLayoutHash",
        bindGroupLayoutPreimage = bindGroupLayoutPreimage,
        pipelineLayoutKeyHash = "pipeline-layout:$pipelineLayoutHash",
        pipelineLayoutSubjectHash = "bind-groups:$bindGroupLayoutHash",
        pipelineLayoutPreimage = pipelineLayoutPreimage,
        renderPipelineKeyHash = renderPipelineKey,
        renderPipelineSubjectHash = stableSha256(canonicalRenderPreimage),
        renderPipelinePreimage = canonicalRenderPreimage,
    )
}

internal fun textAtlasExecutionCacheKeys(
    wgsl: String,
    targetFormat: GPUTextureFormat,
    textureFormat: GPUTextureFormat,
    blendMode: GPUBlendMode? = null,
): FullscreenExecutionCacheKeys {
    val blendLabel = blendMode?.gpuLabel ?: "src_over"
    val targetFormatClass = targetFormat.toBackendColorFormat()
    val wgslHash = stableSha256(wgsl)
    val bindGroupLayoutPreimage = listOf(
        "kind=bind-group-layout",
        "role=text-atlas-uniform",
        "version=1",
        "binding=0",
        "visibility=vertex|fragment",
        "bufferType=uniform",
        "dynamicOffsets=false",
    ).joinToString("\n")
    val bindGroupLayoutHash = stableSha256(bindGroupLayoutPreimage)
    val textureBindGroupLayoutPreimage = listOf(
        "kind=bind-group-layout",
        "role=text-atlas-sampler",
        "version=1",
        "binding=1:type=texture:format=${textureFormat.name}",
        "binding=2:type=sampler:filtering",
        "visibility=fragment",
    ).joinToString("\n")
    val textureBindGroupLayoutHash = stableSha256(textureBindGroupLayoutPreimage)
    val modulePreimage = listOf(
        "kind=wgsl-module",
        "role=text-atlas-pass",
        "entryPoints=vs_main,fs_main",
        "wgsl=$wgslHash",
    ).joinToString("\n")
    val moduleHash = stableSha256(modulePreimage)
    val pipelineLayoutPreimage = listOf(
        "kind=pipeline-layout",
        "role=text-atlas-pass",
        "version=1",
        "bindGroupLayouts=$bindGroupLayoutHash,$textureBindGroupLayoutHash",
    ).joinToString("\n")
    val pipelineLayoutHash = stableSha256(pipelineLayoutPreimage)
    val renderPreimage = GPUPipelineKeyPreimage.Render(
        renderStepIdentity = "gpu-backend.text-atlas-pass",
        renderStepVersion = "1",
        primitiveTopology = "triangle-list",
        materialKeyHash = stableSha256("material:text-atlas-uniform:v1"),
        materialProgramId = "wgsl.text-atlas-a8",
        materialDictionaryVersion = "runtime-helper-v1",
        materialLayoutHash = "$bindGroupLayoutHash,$textureBindGroupLayoutHash",
        snippetIdentityHash = stableSha256("snippet:text-atlas-a8:v1"),
        moduleHash = moduleHash,
        vertexLayoutHash = stableSha256("vertex-layout:text-atlas:float32x2+float32x2"),
        targetFormatClass = targetFormatClass,
        blendStateHash = stableSha256("blend:$blendLabel-premul:v1"),
        sampleStateHash = stableSha256("sample-state:count=1:mask=all"),
        bindGroupLayoutHash = "$bindGroupLayoutHash,$textureBindGroupLayoutHash",
        capabilityClass = "webgpu-wgsl-text-atlas-pass",
        capabilityFacts = listOf("adapter-backed-helper", "targetFormat=$targetFormatClass", "textureFormat=${textureFormat.name}"),
        rendererSalt = "kgpu-m26-001",
    )
    val canonicalRenderPreimage = GPUPipelineKeys.canonicalRenderPreimage(renderPreimage)
    val renderPipelineKey = GPUPipelineKeys.renderPipelineKey(renderPreimage).value

    return FullscreenExecutionCacheKeys(
        moduleKeyHash = "module:$moduleHash",
        moduleSubjectHash = "wgsl:$wgslHash",
        modulePreimage = modulePreimage,
        bindGroupLayoutKeyHash = "bind-group-layout:$bindGroupLayoutHash",
        bindGroupLayoutSubjectHash = "layout-shape:$bindGroupLayoutHash",
        bindGroupLayoutPreimage = bindGroupLayoutPreimage,
        textureBindGroupLayoutKeyHash = "bind-group-layout:$textureBindGroupLayoutHash",
        textureBindGroupLayoutSubjectHash = "layout-shape:$textureBindGroupLayoutHash",
        textureBindGroupLayoutPreimage = textureBindGroupLayoutPreimage,
        pipelineLayoutKeyHash = "pipeline-layout:$pipelineLayoutHash",
        pipelineLayoutSubjectHash = "bind-groups:$bindGroupLayoutHash,$textureBindGroupLayoutHash",
        pipelineLayoutPreimage = pipelineLayoutPreimage,
        renderPipelineKeyHash = renderPipelineKey,
        renderPipelineSubjectHash = stableSha256(canonicalRenderPreimage),
        renderPipelinePreimage = canonicalRenderPreimage,
    )
}

internal fun colorGlyphExecutionCacheKeys(
    wgsl: String,
    targetFormat: GPUTextureFormat,
    textureFormat: GPUTextureFormat,
    blendMode: GPUBlendMode? = null,
): FullscreenExecutionCacheKeys {
    val blendLabel = blendMode?.gpuLabel ?: "src_over"
    val targetFormatClass = targetFormat.toBackendColorFormat()
    val wgslHash = stableSha256(wgsl)
    val bindGroupLayoutPreimage = listOf(
        "kind=bind-group-layout",
        "role=color-glyph-uniform",
        "version=1",
        "binding=0",
        "visibility=vertex|fragment",
        "bufferType=uniform",
        "dynamicOffsets=false",
    ).joinToString("\n")
    val bindGroupLayoutHash = stableSha256(bindGroupLayoutPreimage)
    val textureBindGroupLayoutPreimage = listOf(
        "kind=bind-group-layout",
        "role=color-glyph-sampler",
        "version=1",
        "binding=1:type=texture:format=${textureFormat.name}",
        "binding=2:type=sampler:filtering",
        "visibility=fragment",
    ).joinToString("\n")
    val textureBindGroupLayoutHash = stableSha256(textureBindGroupLayoutPreimage)
    val modulePreimage = listOf(
        "kind=wgsl-module",
        "role=color-glyph-pass",
        "entryPoints=vs_main,fs_main",
        "wgsl=$wgslHash",
    ).joinToString("\n")
    val moduleHash = stableSha256(modulePreimage)
    val pipelineLayoutPreimage = listOf(
        "kind=pipeline-layout",
        "role=color-glyph-pass",
        "version=1",
        "bindGroupLayouts=$bindGroupLayoutHash,$textureBindGroupLayoutHash",
    ).joinToString("\n")
    val pipelineLayoutHash = stableSha256(pipelineLayoutPreimage)
    val renderPreimage = GPUPipelineKeyPreimage.Render(
        renderStepIdentity = "gpu-backend.color-glyph-pass",
        renderStepVersion = "1",
        primitiveTopology = "triangle-list",
        materialKeyHash = stableSha256("material:color-glyph-uniform:v1"),
        materialProgramId = "wgsl.color-glyph",
        materialDictionaryVersion = "runtime-helper-v1",
        materialLayoutHash = "$bindGroupLayoutHash,$textureBindGroupLayoutHash",
        snippetIdentityHash = stableSha256("snippet:color-glyph:v1"),
        moduleHash = moduleHash,
        vertexLayoutHash = stableSha256("vertex-layout:color-glyph:float32x2+float32x2"),
        targetFormatClass = targetFormatClass,
        blendStateHash = stableSha256("blend:$blendLabel-premul:v1"),
        sampleStateHash = stableSha256("sample-state:count=1:mask=all"),
        bindGroupLayoutHash = "$bindGroupLayoutHash,$textureBindGroupLayoutHash",
        capabilityClass = "webgpu-wgsl-color-glyph-pass",
        capabilityFacts = listOf("adapter-backed-helper", "targetFormat=$targetFormatClass", "textureFormat=${textureFormat.name}"),
        rendererSalt = "kgpu-m34-001",
    )
    val canonicalRenderPreimage = GPUPipelineKeys.canonicalRenderPreimage(renderPreimage)
    val renderPipelineKey = GPUPipelineKeys.renderPipelineKey(renderPreimage).value

    return FullscreenExecutionCacheKeys(
        moduleKeyHash = "module:$moduleHash",
        moduleSubjectHash = "wgsl:$wgslHash",
        modulePreimage = modulePreimage,
        bindGroupLayoutKeyHash = "bind-group-layout:$bindGroupLayoutHash",
        bindGroupLayoutSubjectHash = "layout-shape:$bindGroupLayoutHash",
        bindGroupLayoutPreimage = bindGroupLayoutPreimage,
        textureBindGroupLayoutKeyHash = "bind-group-layout:$textureBindGroupLayoutHash",
        textureBindGroupLayoutSubjectHash = "layout-shape:$textureBindGroupLayoutHash",
        textureBindGroupLayoutPreimage = textureBindGroupLayoutPreimage,
        pipelineLayoutKeyHash = "pipeline-layout:$pipelineLayoutHash",
        pipelineLayoutSubjectHash = "bind-groups:$bindGroupLayoutHash,$textureBindGroupLayoutHash",
        pipelineLayoutPreimage = pipelineLayoutPreimage,
        renderPipelineKeyHash = renderPipelineKey,
        renderPipelineSubjectHash = stableSha256(canonicalRenderPreimage),
        renderPipelinePreimage = canonicalRenderPreimage,
    )
}

internal fun stencilExecutionCacheKeys(
    wgsl: String,
    targetFormat: GPUTextureFormat,
    vertexStage: Boolean,
    blendMode: GPUBlendMode? = null,
): FullscreenExecutionCacheKeys {
    val blendLabel = blendMode?.gpuLabel ?: "src_over"
    val targetFormatClass = targetFormat.toBackendColorFormat()
    val wgslHash = stableSha256(wgsl)
        val role = if (vertexStage) "stencil-write-vertex" else "stencil-test-fullscreen"
        val bindGroupLayoutPreimage = listOf(
            "kind=bind-group-layout",
            "role=$role",
            "version=1",
            "binding=0",
            "visibility=vertex|fragment",
            "bufferType=uniform",
            "dynamicOffsets=false",
        ).joinToString("\n")
    val bindGroupLayoutHash = stableSha256(bindGroupLayoutPreimage)
    val modulePreimage = listOf(
        "kind=wgsl-module",
        "role=$role",
        "entryPoints=vs_main,fs_main",
        "wgsl=$wgslHash",
    ).joinToString("\n")
    val moduleHash = stableSha256(modulePreimage)
    val pipelineLayoutPreimage = listOf(
        "kind=pipeline-layout",
        "role=$role",
        "version=1",
        "bindGroupLayouts=$bindGroupLayoutHash",
    ).joinToString("\n")
    val pipelineLayoutHash = stableSha256(pipelineLayoutPreimage)
    val renderPreimage = GPUPipelineKeyPreimage.Render(
        renderStepIdentity = "gpu-backend.$role",
        renderStepVersion = "1",
        primitiveTopology = "triangle-list",
        materialKeyHash = stableSha256("material:$role:v1"),
        materialProgramId = "wgsl.$role",
        materialDictionaryVersion = "runtime-helper-v1",
        materialLayoutHash = bindGroupLayoutHash,
        snippetIdentityHash = stableSha256("snippet:$role:v1"),
        moduleHash = moduleHash,
        vertexLayoutHash = if (vertexStage) stableSha256("vertex-layout:float32x2:stencil") else stableSha256("vertex-layout:fullscreen-triangle:vertex-index-only"),
        targetFormatClass = targetFormatClass,
        blendStateHash = stableSha256("blend:$blendLabel-premul:v1"),
        sampleStateHash = stableSha256("sample-state:count=1:mask=all"),
        bindGroupLayoutHash = bindGroupLayoutHash,
        capabilityClass = "webgpu-wgsl-$role",
        capabilityFacts = listOf("adapter-backed-helper", "targetFormat=$targetFormatClass"),
        rendererSalt = "kgpu-m28-001",
    )
    val canonicalRenderPreimage = GPUPipelineKeys.canonicalRenderPreimage(renderPreimage)
    val renderPipelineKey = GPUPipelineKeys.renderPipelineKey(renderPreimage).value

    return FullscreenExecutionCacheKeys(
        moduleKeyHash = "module:$moduleHash",
        moduleSubjectHash = "wgsl:$wgslHash",
        modulePreimage = modulePreimage,
        bindGroupLayoutKeyHash = "bind-group-layout:$bindGroupLayoutHash",
        bindGroupLayoutSubjectHash = "layout-shape:$bindGroupLayoutHash",
        bindGroupLayoutPreimage = bindGroupLayoutPreimage,
        pipelineLayoutKeyHash = "pipeline-layout:$pipelineLayoutHash",
        pipelineLayoutSubjectHash = "bind-groups:$bindGroupLayoutHash",
        pipelineLayoutPreimage = pipelineLayoutPreimage,
        renderPipelineKeyHash = renderPipelineKey,
        renderPipelineSubjectHash = stableSha256(canonicalRenderPreimage),
        renderPipelinePreimage = canonicalRenderPreimage,
    )
}

private fun String.dumpPreimage(): String =
    lineSequence()
        .map { line -> line.trim() }
        .filter { line -> line.isNotEmpty() }
        .joinToString(";")

private fun <T : Any> GPUExecutionCacheDecision<T>.readyHandle(): T =
    when (this) {
        is GPUExecutionCacheDecision.Ready -> handle
        is GPUExecutionCacheDecision.Refused ->
            error("GPU execution cache refused materialization with $diagnosticCode")
        is GPUExecutionCacheDecision.Evicted ->
            error("GPU execution cache entry was evicted before materialization")
    }

internal fun GPUDevice.createRenderPipelineWithValidationScope(
    descriptor: RenderPipelineDescriptor,
): GPURenderPipeline {
    pushErrorScope(GPUErrorFilter.Validation)
    val pipeline = createRenderPipeline(descriptor)
    val validationError = runBlocking { popErrorScope().getOrThrow() }
    if (validationError != null) {
        pipeline.close()
        error("GPU render pipeline validation failed: ${validationError.message}")
    }
    return pipeline
}

private fun stableSha256(input: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
    return "sha256:" + digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
}

private fun toBackendBlendFactor(f: GPUBlendFactor): io.ygdrasil.webgpu.GPUBlendFactor = when (f) {
    GPUBlendFactor.Zero -> io.ygdrasil.webgpu.GPUBlendFactor.Zero
    GPUBlendFactor.One -> io.ygdrasil.webgpu.GPUBlendFactor.One
    GPUBlendFactor.Src -> io.ygdrasil.webgpu.GPUBlendFactor.Src
    GPUBlendFactor.OneMinusSrc -> io.ygdrasil.webgpu.GPUBlendFactor.OneMinusSrc
    GPUBlendFactor.Dst -> io.ygdrasil.webgpu.GPUBlendFactor.Dst
    GPUBlendFactor.OneMinusDst -> io.ygdrasil.webgpu.GPUBlendFactor.OneMinusDst
    GPUBlendFactor.SrcAlpha -> io.ygdrasil.webgpu.GPUBlendFactor.SrcAlpha
    GPUBlendFactor.OneMinusSrcAlpha -> io.ygdrasil.webgpu.GPUBlendFactor.OneMinusSrcAlpha
    GPUBlendFactor.DstAlpha -> io.ygdrasil.webgpu.GPUBlendFactor.DstAlpha
    GPUBlendFactor.OneMinusDstAlpha -> io.ygdrasil.webgpu.GPUBlendFactor.OneMinusDstAlpha
    GPUBlendFactor.SrcAlphaSaturated -> io.ygdrasil.webgpu.GPUBlendFactor.SrcAlphaSaturated
    GPUBlendFactor.Constant -> io.ygdrasil.webgpu.GPUBlendFactor.Constant
    GPUBlendFactor.OneMinusConstant -> io.ygdrasil.webgpu.GPUBlendFactor.OneMinusConstant
}

internal fun blendStateFor(blendMode: GPUBlendMode?): BlendState {
    val mode = blendMode
    if (mode == null || mode == GPUBlendMode.SRC_OVER || mode.requiresDestinationRead) {
        return BlendState(
            color = BlendComponent(GPUBlendOperation.Add, io.ygdrasil.webgpu.GPUBlendFactor.One, io.ygdrasil.webgpu.GPUBlendFactor.OneMinusSrcAlpha),
            alpha = BlendComponent(GPUBlendOperation.Add, io.ygdrasil.webgpu.GPUBlendFactor.One, io.ygdrasil.webgpu.GPUBlendFactor.OneMinusSrcAlpha),
        )
    }
    return BlendState(
        color = BlendComponent(
            operation = GPUBlendOperation.Add,
            srcFactor = toBackendBlendFactor(mode.colorSrcFactor),
            dstFactor = toBackendBlendFactor(mode.colorDstFactor),
        ),
        alpha = BlendComponent(
            operation = GPUBlendOperation.Add,
            srcFactor = toBackendBlendFactor(mode.alphaSrcFactor),
            dstFactor = toBackendBlendFactor(mode.alphaDstFactor),
        ),
    )
}
