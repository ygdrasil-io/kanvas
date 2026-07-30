package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.BindGroupLayoutDescriptor
import io.ygdrasil.webgpu.BindGroupLayoutEntry
import io.ygdrasil.webgpu.BlendComponent
import io.ygdrasil.webgpu.BlendState
import io.ygdrasil.webgpu.BufferBindingLayout
import io.ygdrasil.webgpu.ColorTargetState
import io.ygdrasil.webgpu.FragmentState
import io.ygdrasil.webgpu.GPUAddressMode
import io.ygdrasil.webgpu.GPUBlendFactor
import io.ygdrasil.webgpu.GPUBlendOperation
import io.ygdrasil.webgpu.GPUBufferBindingType
import io.ygdrasil.webgpu.GPUDevice
import io.ygdrasil.webgpu.GPUFilterMode
import io.ygdrasil.webgpu.GPUBindGroupLayout
import io.ygdrasil.webgpu.GPUPipelineLayout
import io.ygdrasil.webgpu.GPURenderPipeline
import io.ygdrasil.webgpu.GPUSampler
import io.ygdrasil.webgpu.GPUSamplerBindingType
import io.ygdrasil.webgpu.GPUShaderModule
import io.ygdrasil.webgpu.GPUShaderStage
import io.ygdrasil.webgpu.GPUTextureFormat
import io.ygdrasil.webgpu.GPUTextureSampleType
import io.ygdrasil.webgpu.GPUTextureViewDimension
import io.ygdrasil.webgpu.GPUVertexFormat
import io.ygdrasil.webgpu.PipelineLayoutDescriptor
import io.ygdrasil.webgpu.PrimitiveState
import io.ygdrasil.webgpu.RenderPipelineDescriptor
import io.ygdrasil.webgpu.SamplerBindingLayout
import io.ygdrasil.webgpu.SamplerDescriptor
import io.ygdrasil.webgpu.ShaderModuleDescriptor
import io.ygdrasil.webgpu.TextureBindingLayout
import io.ygdrasil.webgpu.VertexAttribute
import io.ygdrasil.webgpu.VertexBufferLayout
import io.ygdrasil.webgpu.VertexState

/** Session-local evidence for invariant reuse; ColorGlyph atlases are frame-owned R8 resources. */
internal data class GPUColorGlyphNativeCacheCounters(
    val invariantCreations: Long = 0L,
    val atlasCreations: Long = 0L,
    val atlasUploads: Long = 0L,
    val atlasReuses: Long = 0L,
    val atlasInvalidations: Long = 0L,
    val currentAtlasBytes: Long = 0L,
    val atlasPeakResidentBytes: Long = 0L,
)

/** Native objects whose descriptors do not vary between ColorGlyph frames in one session. */
internal class GPUWgpu4kColorGlyphInvariantHandles(
    val bindGroupLayout: GPUBindGroupLayout,
    val shader: GPUShaderModule,
    val pipelineLayout: GPUPipelineLayout,
    val pipeline: GPURenderPipeline,
    val sampler: GPUSampler,
    private val owned: GPUColorGlyphCachedHandleSet,
) : AutoCloseable by owned

/** Public-wgpu4k invariant cache owned by one prepared scene session. */
internal class GPUWgpu4kColorGlyphSessionCache(
    private val device: GPUDevice,
) : AutoCloseable {
    private val preRegistrationHandles = GPUPreRegistrationNativeHandleLedger()
    private var invariants: GPUWgpu4kColorGlyphInvariantHandles? = null
    private var invariantCreations = 0L
    private var closed = false

    @Synchronized
    fun acquire(): GPUWgpu4kColorGlyphInvariantHandles {
        check(!closed) { "The ColorGlyph native invariant cache is closed" }
        invariants?.let { return it }
        return createInvariants().also {
            invariants = it
            invariantCreations += 1L
        }
    }

    @Synchronized
    fun counters(): GPUColorGlyphNativeCacheCounters =
        GPUColorGlyphNativeCacheCounters(invariantCreations = invariantCreations)

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        var firstFailure: Throwable? = null
        try {
            invariants?.close()
            invariants = null
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
                                format = GPUTextureFormat.RGBA8UnormSrgb,
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

    private fun requireCleanSetupLedger() {
        check(preRegistrationHandles.closeRetainingFailures()) {
            "ColorGlyph cache cannot allocate while failed setup handles remain quarantined"
        }
    }

    private fun <T : AutoCloseable> T.tracked(): T = preRegistrationHandles.track(this)

}

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
