package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.ArrayBuffer
import io.ygdrasil.webgpu.BindGroupLayoutDescriptor
import io.ygdrasil.webgpu.BindGroupLayoutEntry
import io.ygdrasil.webgpu.BlendComponent
import io.ygdrasil.webgpu.BlendState
import io.ygdrasil.webgpu.BufferBindingLayout
import io.ygdrasil.webgpu.ColorTargetState
import io.ygdrasil.webgpu.Extent3D
import io.ygdrasil.webgpu.FragmentState
import io.ygdrasil.webgpu.GPUAddressMode
import io.ygdrasil.webgpu.GPUBlendFactor
import io.ygdrasil.webgpu.GPUBlendOperation
import io.ygdrasil.webgpu.GPUBufferBindingType
import io.ygdrasil.webgpu.GPUDevice
import io.ygdrasil.webgpu.GPUFilterMode
import io.ygdrasil.webgpu.GPUBindGroupLayout
import io.ygdrasil.webgpu.GPUPipelineLayout
import io.ygdrasil.webgpu.GPUQueue
import io.ygdrasil.webgpu.GPURenderPipeline
import io.ygdrasil.webgpu.GPUSampler
import io.ygdrasil.webgpu.GPUSamplerBindingType
import io.ygdrasil.webgpu.GPUShaderModule
import io.ygdrasil.webgpu.GPUShaderStage
import io.ygdrasil.webgpu.GPUTexture
import io.ygdrasil.webgpu.GPUTextureFormat
import io.ygdrasil.webgpu.GPUTextureSampleType
import io.ygdrasil.webgpu.GPUTextureUsage
import io.ygdrasil.webgpu.GPUTextureView
import io.ygdrasil.webgpu.GPUTextureViewDimension
import io.ygdrasil.webgpu.GPUVertexFormat
import io.ygdrasil.webgpu.PipelineLayoutDescriptor
import io.ygdrasil.webgpu.PrimitiveState
import io.ygdrasil.webgpu.RenderPipelineDescriptor
import io.ygdrasil.webgpu.SamplerBindingLayout
import io.ygdrasil.webgpu.SamplerDescriptor
import io.ygdrasil.webgpu.ShaderModuleDescriptor
import io.ygdrasil.webgpu.TexelCopyBufferLayout
import io.ygdrasil.webgpu.TexelCopyTextureInfo
import io.ygdrasil.webgpu.TextureBindingLayout
import io.ygdrasil.webgpu.TextureDescriptor
import io.ygdrasil.webgpu.VertexAttribute
import io.ygdrasil.webgpu.VertexBufferLayout
import io.ygdrasil.webgpu.VertexState
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload

/** Native objects whose descriptors do not vary between ColorGlyph frames in one session. */
internal class GPUWgpu4kColorGlyphInvariantHandles(
    val bindGroupLayout: GPUBindGroupLayout,
    val shader: GPUShaderModule,
    val pipelineLayout: GPUPipelineLayout,
    val pipeline: GPURenderPipeline,
    val sampler: GPUSampler,
    private val owned: GPUColorGlyphCachedHandleSet,
) : AutoCloseable by owned

/** One session-owned R8 atlas allocation. */
internal class GPUWgpu4kColorGlyphAtlasHandles(
    val texture: GPUTexture,
    val view: GPUTextureView,
    private val owned: GPUColorGlyphCachedHandleSet,
) : AutoCloseable by owned

/** Borrowed handles returned to one frame-local bind group and render payload. */
internal typealias GPUWgpu4kColorGlyphSessionCacheLease =
    GPUColorGlyphSessionNativeCacheLease<GPUWgpu4kColorGlyphInvariantHandles, GPUWgpu4kColorGlyphAtlasHandles>

/** Public-wgpu4k resource cache owned by one prepared scene session. */
internal class GPUWgpu4kColorGlyphSessionCache(
    private val device: GPUDevice,
    private val queue: GPUQueue,
) : AutoCloseable {
    private val preRegistrationHandles = GPUPreRegistrationNativeHandleLedger()
    private val cache = GPUColorGlyphSessionNativeCache(
        keyOf = { semantic: GPUDrawSemanticPayload.ColorGlyph -> semantic.nativeAtlasCacheKey() },
        invariantFactory = ::createInvariants,
        atlasFactory = ::createAtlas,
    )

    fun acquire(semantic: GPUDrawSemanticPayload.ColorGlyph): GPUWgpu4kColorGlyphSessionCacheLease =
        cache.acquire(semantic)

    fun counters(): GPUColorGlyphNativeCacheCounters = cache.counters()

    override fun close() {
        var firstFailure: Throwable? = null
        try {
            cache.close()
        } catch (failure: Throwable) {
            firstFailure = failure
        }
        if (!preRegistrationHandles.closeRetainingFailures()) {
            val failure = IllegalStateException(
                "ColorGlyph session cache retained ${preRegistrationHandles.pendingHandleCount} failed setup handle(s)",
            )
            if (firstFailure == null) firstFailure = failure else firstFailure.addSuppressed(failure)
        }
        firstFailure?.let { throw it }
    }

    private fun createInvariants(): GPUWgpu4kColorGlyphInvariantHandles {
        requireCleanSetupLedger()
        return try {
            val shaderPlan = when (val result = buildColorGlyphCompositeShader()) {
                is GPUColorGlyphCompositeShaderResult.Ready -> result.plan
                is GPUColorGlyphCompositeShaderResult.Rejected -> error(
                    "ColorGlyph parser-backed WGSL validation failed: ${result.reason}: ${result.message}",
                )
            }
            val bindGroupLayout = device.createBindGroupLayout(
                BindGroupLayoutDescriptor(
                    label = "Kanvas.session.colorGlyph.bindGroupLayout0",
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
            ).tracked()
            val shader = device.createShaderModule(
                ShaderModuleDescriptor(
                    label = "Kanvas.session.colorGlyph.shader",
                    code = shaderPlan.wgslSource,
                ),
            ).tracked()
            val pipelineLayout = device.createPipelineLayout(
                PipelineLayoutDescriptor(
                    label = "Kanvas.session.colorGlyph.pipelineLayout",
                    bindGroupLayouts = listOf(bindGroupLayout),
                ),
            ).tracked()
            val pipeline = device.createRenderPipeline(
                RenderPipelineDescriptor(
                    label = "Kanvas.session.colorGlyph.pipeline.srcOver",
                    layout = pipelineLayout,
                    vertex = VertexState(
                        module = shader,
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
                    fragment = FragmentState(
                        module = shader,
                        entryPoint = "fs_main",
                        targets = listOf(
                            ColorTargetState(
                                format = GPUTextureFormat.RGBA8Unorm,
                                blend = BlendState(
                                    color = BlendComponent(
                                        GPUBlendOperation.Add,
                                        GPUBlendFactor.One,
                                        GPUBlendFactor.OneMinusSrcAlpha,
                                    ),
                                    alpha = BlendComponent(
                                        GPUBlendOperation.Add,
                                        GPUBlendFactor.One,
                                        GPUBlendFactor.OneMinusSrcAlpha,
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ).tracked()
            val sampler = device.createSampler(
                SamplerDescriptor(
                    addressModeU = GPUAddressMode.ClampToEdge,
                    addressModeV = GPUAddressMode.ClampToEdge,
                    magFilter = GPUFilterMode.Nearest,
                    minFilter = GPUFilterMode.Nearest,
                    label = "Kanvas.session.colorGlyph.nearestSampler",
                ),
            ).tracked()
            val owned = GPUColorGlyphCachedHandleSet(
                listOf(bindGroupLayout, shader, pipelineLayout, pipeline, sampler),
            )
            preRegistrationHandles.transferAll()
            GPUWgpu4kColorGlyphInvariantHandles(
                bindGroupLayout,
                shader,
                pipelineLayout,
                pipeline,
                sampler,
                owned,
            )
        } catch (failure: Throwable) {
            preRegistrationHandles.closeRetainingFailures()
            throw failure
        }
    }

    private fun createAtlas(semantic: GPUDrawSemanticPayload.ColorGlyph): GPUWgpu4kColorGlyphAtlasHandles {
        requireCleanSetupLedger()
        return try {
            val texture = device.createTexture(
                TextureDescriptor(
                    size = Extent3D(semantic.atlasWidth.toUInt(), semantic.atlasHeight.toUInt()),
                    format = GPUTextureFormat.R8Unorm,
                    usage = GPUTextureUsage.TextureBinding or GPUTextureUsage.CopyDst,
                    label = "Kanvas.session.colorGlyph.atlas",
                ),
            ).tracked()
            val paddedBytesPerRow = alignCopyRow(semantic.atlasWidth)
            val padded = ByteArray(Math.multiplyExact(paddedBytesPerRow, semantic.atlasHeight))
            for (row in 0 until semantic.atlasHeight) {
                for (column in 0 until semantic.atlasWidth) {
                    padded[row * paddedBytesPerRow + column] =
                        semantic.atlasA8Bytes[row * semantic.atlasWidth + column].toByte()
                }
            }
            queue.writeTexture(
                destination = TexelCopyTextureInfo(texture = texture),
                data = ArrayBuffer.of(padded),
                dataLayout = TexelCopyBufferLayout(
                    offset = 0uL,
                    bytesPerRow = paddedBytesPerRow.toUInt(),
                    rowsPerImage = semantic.atlasHeight.toUInt(),
                ),
                size = Extent3D(semantic.atlasWidth.toUInt(), semantic.atlasHeight.toUInt()),
            )
            val view = texture.createView().tracked()
            val owned = GPUColorGlyphCachedHandleSet(listOf(texture, view))
            preRegistrationHandles.transferAll()
            GPUWgpu4kColorGlyphAtlasHandles(texture, view, owned)
        } catch (failure: Throwable) {
            preRegistrationHandles.closeRetainingFailures()
            throw failure
        }
    }

    private fun requireCleanSetupLedger() {
        check(preRegistrationHandles.closeRetainingFailures()) {
            "ColorGlyph cache cannot allocate while failed setup handles remain quarantined"
        }
    }

    private fun <T : AutoCloseable> T.tracked(): T = preRegistrationHandles.track(this)

    private fun alignCopyRow(width: Int): Int = Math.multiplyExact(
        Math.floorDiv(Math.addExact(width, COPY_ROW_ALIGNMENT - 1), COPY_ROW_ALIGNMENT),
        COPY_ROW_ALIGNMENT,
    )

    private companion object {
        const val COPY_ROW_ALIGNMENT = 256
    }
}

private fun GPUDrawSemanticPayload.ColorGlyph.nativeAtlasCacheKey() = GPUColorGlyphNativeAtlasCacheKey(
    artifactId = atlasArtifactKey.artifactID.value.toString(),
    generation = atlasArtifactKey.generation.value,
    contentFingerprint = atlasArtifactKey.contentFingerprint,
    atlasBytesSha256 = atlasBytesSha256,
    byteSize = atlasA8Bytes.size.toLong(),
    width = atlasWidth,
    height = atlasHeight,
    format = atlasFormat.gpuLabel,
)

/** Retryable reverse-order owner used by persistent cache entries. */
internal class GPUColorGlyphCachedHandleSet(handles: List<AutoCloseable>) : AutoCloseable {
    private val pending = handles.asReversed().toMutableList()

    @Synchronized
    override fun close() {
        var firstFailure: Throwable? = null
        val iterator = pending.iterator()
        while (iterator.hasNext()) {
            try {
                iterator.next().close()
                iterator.remove()
            } catch (failure: Throwable) {
                if (firstFailure == null) firstFailure = failure
            }
        }
        firstFailure?.let { throw IllegalStateException("ColorGlyph cached handles remain live", it) }
    }
}
