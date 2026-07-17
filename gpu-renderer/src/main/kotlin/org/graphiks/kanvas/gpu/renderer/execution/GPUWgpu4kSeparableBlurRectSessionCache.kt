package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.BindGroupLayoutDescriptor
import io.ygdrasil.webgpu.BindGroupLayoutEntry
import io.ygdrasil.webgpu.BlendComponent
import io.ygdrasil.webgpu.BlendState
import io.ygdrasil.webgpu.BufferBindingLayout
import io.ygdrasil.webgpu.ColorTargetState
import io.ygdrasil.webgpu.Extent3D
import io.ygdrasil.webgpu.FragmentState
import io.ygdrasil.webgpu.GPUAddressMode
import io.ygdrasil.webgpu.GPUBindGroupLayout
import io.ygdrasil.webgpu.GPUBlendFactor
import io.ygdrasil.webgpu.GPUBlendOperation
import io.ygdrasil.webgpu.GPUBufferBindingType
import io.ygdrasil.webgpu.GPUDevice
import io.ygdrasil.webgpu.GPUFilterMode
import io.ygdrasil.webgpu.GPUPipelineLayout
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
import io.ygdrasil.webgpu.MultisampleState
import io.ygdrasil.webgpu.PipelineLayoutDescriptor
import io.ygdrasil.webgpu.PrimitiveState
import io.ygdrasil.webgpu.RenderPipelineDescriptor
import io.ygdrasil.webgpu.SamplerBindingLayout
import io.ygdrasil.webgpu.SamplerDescriptor
import io.ygdrasil.webgpu.ShaderModuleDescriptor
import io.ygdrasil.webgpu.TextureBindingLayout
import io.ygdrasil.webgpu.TextureDescriptor
import io.ygdrasil.webgpu.VertexState

internal data class GPUSeparableBlurNativeCacheCounters(
    val invariantCreations: Long = 0L,
    val invariantReuses: Long = 0L,
    val intermediateCreations: Long = 0L,
    val intermediateReuses: Long = 0L,
)

internal class GPUWgpu4kSeparableBlurRectInvariantHandles(
    val sourceBindGroupLayout: GPUBindGroupLayout,
    val blurBindGroupLayout: GPUBindGroupLayout,
    val sourcePipeline: GPURenderPipeline,
    val horizontalPipeline: GPURenderPipeline,
    val verticalPipeline: GPURenderPipeline,
    val sampler: GPUSampler,
    private val owned: GPUSeparableBlurCachedHandleSet,
) : AutoCloseable by owned

internal class GPUWgpu4kSeparableBlurRectIntermediateHandles(
    val width: Int,
    val height: Int,
    val sourceTexture: GPUTexture,
    val sourceView: GPUTextureView,
    val scratchTexture: GPUTexture,
    val scratchView: GPUTextureView,
    private val owned: GPUSeparableBlurCachedHandleSet,
) : AutoCloseable by owned

internal data class GPUWgpu4kSeparableBlurRectCacheLease(
    val invariants: GPUWgpu4kSeparableBlurRectInvariantHandles,
    val intermediates: GPUWgpu4kSeparableBlurRectIntermediateHandles,
)

/** Session cache for static blur programs and the serialized frame intermediates. */
internal class GPUWgpu4kSeparableBlurRectSessionCache(
    private val device: GPUDevice,
) : AutoCloseable {
    private val preRegistrationHandles = GPUPreRegistrationNativeHandleLedger()
    private var invariants: GPUWgpu4kSeparableBlurRectInvariantHandles? = null
    private var intermediates: GPUWgpu4kSeparableBlurRectIntermediateHandles? = null
    private var closed = false
    private var invariantCreations = 0L
    private var invariantReuses = 0L
    private var intermediateCreations = 0L
    private var intermediateReuses = 0L

    @Synchronized
    fun acquire(width: Int, height: Int): GPUWgpu4kSeparableBlurRectCacheLease {
        check(!closed) { "The separable blur native session cache is closed" }
        require(width in 1..2048 && height in 1..2048) {
            "The separable blur first slice accepts dimensions in 1..2048"
        }
        val invariantHandles = invariants?.also { invariantReuses += 1L } ?: run {
            requireCleanSetupLedger()
            try {
                createInvariants().also {
                    invariants = it
                    preRegistrationHandles.transferAll()
                    invariantCreations += 1L
                }
            } catch (failure: Throwable) {
                preRegistrationHandles.closeRetainingFailures()
                throw failure
            }
        }
        val existing = intermediates
        val intermediateHandles = if (existing != null && existing.width == width && existing.height == height) {
            intermediateReuses += 1L
            existing
        } else {
            existing?.close()
            requireCleanSetupLedger()
            try {
                createIntermediates(width, height).also {
                    intermediates = it
                    preRegistrationHandles.transferAll()
                    intermediateCreations += 1L
                }
            } catch (failure: Throwable) {
                preRegistrationHandles.closeRetainingFailures()
                throw failure
            }
        }
        return GPUWgpu4kSeparableBlurRectCacheLease(invariantHandles, intermediateHandles)
    }

    @Synchronized
    fun counters(): GPUSeparableBlurNativeCacheCounters = GPUSeparableBlurNativeCacheCounters(
        invariantCreations,
        invariantReuses,
        intermediateCreations,
        intermediateReuses,
    )

    @Synchronized
    override fun close() {
        if (closed && invariants == null && intermediates == null &&
            preRegistrationHandles.pendingHandleCount == 0
        ) return
        closed = true
        var firstFailure: Throwable? = null
        intermediates?.let { handles ->
            try {
                handles.close()
                intermediates = null
            } catch (failure: Throwable) {
                firstFailure = failure
            }
        }
        invariants?.let { handles ->
            try {
                handles.close()
                invariants = null
            } catch (failure: Throwable) {
                if (firstFailure == null) firstFailure = failure else firstFailure.addSuppressed(failure)
            }
        }
        if (!preRegistrationHandles.closeRetainingFailures()) {
            val failure = IllegalStateException(
                "Separable blur session cache retained " +
                    "${preRegistrationHandles.pendingHandleCount} failed setup handle(s)",
            )
            if (firstFailure == null) firstFailure = failure else firstFailure.addSuppressed(failure)
        }
        firstFailure?.let { throw it }
    }

    private fun createInvariants(): GPUWgpu4kSeparableBlurRectInvariantHandles {
        val pending = mutableListOf<AutoCloseable>()
        fun <T : AutoCloseable> T.track(): T = also {
            pending += it
            preRegistrationHandles.track(it)
        }
        return try {
            val sourceLayout = device.createBindGroupLayout(
                BindGroupLayoutDescriptor(
                    label = "Kanvas.session.separableBlur.sourceLayout",
                    entries = listOf(uniformLayoutEntry()),
                ),
            ).track()
            val blurLayout = device.createBindGroupLayout(
                BindGroupLayoutDescriptor(
                    label = "Kanvas.session.separableBlur.blurLayout",
                    entries = listOf(
                        uniformLayoutEntry(),
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
            ).track()
            val sourceShader = shader("source", SEPARABLE_BLUR_SOURCE_WGSL).track()
            val horizontalShader = shader("horizontal", SEPARABLE_BLUR_HORIZONTAL_WGSL).track()
            val verticalShader = shader("vertical", SEPARABLE_BLUR_VERTICAL_WGSL).track()
            val sourcePipelineLayout = device.createPipelineLayout(
                PipelineLayoutDescriptor(
                    label = "Kanvas.session.separableBlur.sourcePipelineLayout",
                    bindGroupLayouts = listOf(sourceLayout),
                ),
            ).track()
            val blurPipelineLayout = device.createPipelineLayout(
                PipelineLayoutDescriptor(
                    label = "Kanvas.session.separableBlur.blurPipelineLayout",
                    bindGroupLayouts = listOf(blurLayout),
                ),
            ).track()
            val sourcePipeline = pipeline("source", sourcePipelineLayout, sourceShader).track()
            val horizontalPipeline = pipeline("horizontal", blurPipelineLayout, horizontalShader).track()
            val verticalPipeline = pipeline("vertical", blurPipelineLayout, verticalShader).track()
            val sampler = device.createSampler(
                SamplerDescriptor(
                    addressModeU = GPUAddressMode.ClampToEdge,
                    addressModeV = GPUAddressMode.ClampToEdge,
                    magFilter = GPUFilterMode.Nearest,
                    minFilter = GPUFilterMode.Nearest,
                    label = "Kanvas.session.separableBlur.nearestSampler",
                ),
            ).track()
            GPUWgpu4kSeparableBlurRectInvariantHandles(
                sourceLayout,
                blurLayout,
                sourcePipeline,
                horizontalPipeline,
                verticalPipeline,
                sampler,
                GPUSeparableBlurCachedHandleSet(pending.toList()),
            )
        } catch (failure: Throwable) {
            throw failure
        }
    }

    private fun createIntermediates(width: Int, height: Int): GPUWgpu4kSeparableBlurRectIntermediateHandles {
        val pending = mutableListOf<AutoCloseable>()
        fun <T : AutoCloseable> T.track(): T = also {
            pending += it
            preRegistrationHandles.track(it)
        }
        return try {
            fun texture(label: String): GPUTexture = device.createTexture(
                TextureDescriptor(
                    size = Extent3D(width.toUInt(), height.toUInt()),
                    format = GPUTextureFormat.RGBA8Unorm,
                    usage = GPUTextureUsage.RenderAttachment or GPUTextureUsage.TextureBinding,
                    label = label,
                ),
            ).track()
            val sourceTexture = texture("Kanvas.session.separableBlur.sourceTexture")
            val sourceView = sourceTexture.createView().track()
            val scratchTexture = texture("Kanvas.session.separableBlur.scratchTexture")
            val scratchView = scratchTexture.createView().track()
            GPUWgpu4kSeparableBlurRectIntermediateHandles(
                width,
                height,
                sourceTexture,
                sourceView,
                scratchTexture,
                scratchView,
                GPUSeparableBlurCachedHandleSet(pending.toList()),
            )
        } catch (failure: Throwable) {
            throw failure
        }
    }

    private fun requireCleanSetupLedger() {
        check(preRegistrationHandles.closeRetainingFailures()) {
            "Separable blur cache cannot allocate while failed setup handles remain quarantined"
        }
    }

    private fun shader(label: String, source: String): GPUShaderModule = device.createShaderModule(
        ShaderModuleDescriptor(
            label = "Kanvas.session.separableBlur.$label.shader",
            code = source,
        ),
    )

    private fun pipeline(
        label: String,
        layout: GPUPipelineLayout,
        shader: GPUShaderModule,
    ): GPURenderPipeline = device.createRenderPipeline(
        RenderPipelineDescriptor(
            label = "Kanvas.session.separableBlur.$label.pipeline",
            layout = layout,
            vertex = VertexState(module = shader, entryPoint = "vs_main"),
            primitive = PrimitiveState(),
            multisample = MultisampleState(count = 1u),
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
    )

    private fun uniformLayoutEntry() = BindGroupLayoutEntry(
        binding = 0u,
        visibility = GPUShaderStage.Fragment,
        buffer = BufferBindingLayout(type = GPUBufferBindingType.Uniform),
    )
}

internal class GPUSeparableBlurCachedHandleSet(handles: List<AutoCloseable>) : AutoCloseable {
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
                if (firstFailure == null) firstFailure = failure else firstFailure.addSuppressed(failure)
            }
        }
        firstFailure?.let { throw IllegalStateException("Separable blur cached handles remain live", it) }
    }
}

private val SEPARABLE_BLUR_VERTEX_WGSL = """
@vertex
fn vs_main(@builtin(vertex_index) vertex_index: u32) -> @builtin(position) vec4f {
    var positions = array<vec2f, 3>(
        vec2f(-1.0, -1.0),
        vec2f(3.0, -1.0),
        vec2f(-1.0, 3.0),
    );
    return vec4f(positions[vertex_index], 0.0, 1.0);
}
""".trimIndent()

private val SEPARABLE_BLUR_SOURCE_WGSL = """
struct SourceUniforms { color: vec4f }
@group(0) @binding(0) var<uniform> uniforms: SourceUniforms;

$SEPARABLE_BLUR_VERTEX_WGSL

@fragment
fn fs_main() -> @location(0) vec4f {
    return uniforms.color;
}
""".trimIndent()

private val SEPARABLE_BLUR_HORIZONTAL_WGSL = blurWgsl(horizontal = true)
private val SEPARABLE_BLUR_VERTICAL_WGSL = blurWgsl(horizontal = false)

private fun blurWgsl(horizontal: Boolean): String {
    val offset = if (horizontal) {
        "vec2f(f32(i) - f32(half), 0.0) / size"
    } else {
        "vec2f(0.0, f32(i) - f32(half)) / size"
    }
    return """
struct BlurUniforms {
    tapCount: u32,
    _pad0: u32,
    targetSize: vec2f,
    _pad1: vec2f,
    _pad2: vec2f,
    weights: array<vec4f, 7>,
};

@group(0) @binding(0) var<uniform> uniforms: BlurUniforms;
@group(0) @binding(1) var inputTexture: texture_2d<f32>;
@group(0) @binding(2) var inputSampler: sampler;

$SEPARABLE_BLUR_VERTEX_WGSL

fn sampleDecal(uv: vec2f) -> vec4f {
    if (any(uv < vec2f(0.0)) || any(uv >= vec2f(1.0))) {
        return vec4f(0.0);
    }
    return textureSample(inputTexture, inputSampler, uv);
}

@fragment
fn fs_main(@builtin(position) position: vec4f) -> @location(0) vec4f {
    let size = max(uniforms.targetSize, vec2f(1.0));
    let uv = position.xy / size;
    let half = uniforms.tapCount / 2u;
    var result = vec4f(0.0);
    for (var i = 0u; i < 25u; i = i + 1u) {
        if (i >= uniforms.tapCount) {
            break;
        }
        let packedWeights = uniforms.weights[i / 4u];
        let weight = packedWeights[i % 4u];
        let sampleOffset = $offset;
        result += weight * sampleDecal(uv + sampleOffset);
    }
    return result;
}
""".trimIndent()
}
