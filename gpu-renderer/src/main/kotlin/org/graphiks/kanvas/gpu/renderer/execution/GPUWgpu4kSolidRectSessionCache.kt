package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.BindGroupLayoutDescriptor
import io.ygdrasil.webgpu.BindGroupLayoutEntry
import io.ygdrasil.webgpu.BlendComponent
import io.ygdrasil.webgpu.BlendState
import io.ygdrasil.webgpu.BufferBindingLayout
import io.ygdrasil.webgpu.ColorTargetState
import io.ygdrasil.webgpu.FragmentState
import io.ygdrasil.webgpu.GPUBindGroupLayout
import io.ygdrasil.webgpu.GPUBufferBindingType
import io.ygdrasil.webgpu.GPUBlendFactor
import io.ygdrasil.webgpu.GPUBlendOperation
import io.ygdrasil.webgpu.GPUDevice
import io.ygdrasil.webgpu.GPUPipelineLayout
import io.ygdrasil.webgpu.GPURenderPipeline
import io.ygdrasil.webgpu.GPUShaderModule
import io.ygdrasil.webgpu.GPUShaderStage
import io.ygdrasil.webgpu.GPUTextureFormat
import io.ygdrasil.webgpu.MultisampleState
import io.ygdrasil.webgpu.PipelineLayoutDescriptor
import io.ygdrasil.webgpu.PrimitiveState
import io.ygdrasil.webgpu.RenderPipelineDescriptor
import io.ygdrasil.webgpu.ShaderModuleDescriptor
import io.ygdrasil.webgpu.VertexState

/** Exact native descriptor authority for the reusable SolidRect pipeline objects. */
internal data class GPUWgpu4kSolidRectPipelineCacheKey(
    val targetFormat: String,
    val sampleCount: Int,
    val shaderIdentity: String = SOLID_RECT_SHADER_IDENTITY,
    val bindingLayoutIdentity: String = SOLID_RECT_BINDING_LAYOUT_IDENTITY,
) {
    init {
        require(targetFormat.isNotBlank())
        require(sampleCount > 0)
        require(shaderIdentity.isNotBlank())
        require(bindingLayoutIdentity.isNotBlank())
    }
}

internal data class GPUSolidRectNativeCacheCounters(
    val invariantCreations: Long = 0L,
    val invariantReuses: Long = 0L,
    val invariantInvalidations: Long = 0L,
)

/**
 * Synchronized single-entry cache for descriptor-compatible SolidRect invariant objects.
 *
 * A changed key creates its replacement before retiring the old allocation. Failed closes remain
 * owned and are retried by [close].
 */
internal class GPUSolidRectSessionNativeCache<K, H : AutoCloseable>(
    private val factory: (K) -> H,
) : AutoCloseable {
    private data class Entry<K, H>(val key: K, val handles: H)

    private var current: Entry<K, H>? = null
    private val retired = mutableListOf<H>()
    private var closed = false
    private var creations = 0L
    private var reuses = 0L
    private var invalidations = 0L

    @Synchronized
    fun acquire(key: K): H {
        check(!closed) { "The SolidRect native session cache is closed" }
        current?.takeIf { it.key == key }?.let {
            reuses += 1L
            return it.handles
        }

        val replacement = factory(key)
        val previous = current
        require(previous == null || replacement !== previous.handles) {
            "A changed SolidRect descriptor key requires a fresh native allocation"
        }
        creations += 1L
        current = Entry(key, replacement)
        previous?.let {
            invalidations += 1L
            retired += it.handles
            closeRetired()
        }
        return replacement
    }

    @Synchronized
    fun counters(): GPUSolidRectNativeCacheCounters = GPUSolidRectNativeCacheCounters(
        invariantCreations = creations,
        invariantReuses = reuses,
        invariantInvalidations = invalidations,
    )

    @Synchronized
    override fun close() {
        if (closed && current == null && retired.isEmpty()) return
        closed = true
        current?.let {
            retired += it.handles
            current = null
        }
        closeRetired()
        if (retired.isNotEmpty()) {
            error("SolidRect native cache retained ${retired.size} invariant allocation(s)")
        }
    }

    private fun closeRetired() {
        val iterator = retired.iterator()
        while (iterator.hasNext()) {
            try {
                iterator.next().close()
                iterator.remove()
            } catch (_: Throwable) {
                // Keep the exact allocation for the next session-close retry.
            }
        }
    }
}

internal class GPUWgpu4kSolidRectInvariantHandles(
    val bindGroupLayout: GPUBindGroupLayout,
    val shader: GPUShaderModule,
    val pipelineLayout: GPUPipelineLayout,
    val pipeline: GPURenderPipeline,
    private val owned: GPUSolidRectCachedHandleSet,
) : AutoCloseable by owned

/** Public-wgpu4k SolidRect invariant cache owned by one prepared scene session. */
internal class GPUWgpu4kSolidRectSessionCache(
    private val device: GPUDevice,
) : AutoCloseable {
    private val preRegistrationHandles = GPUPreRegistrationNativeHandleLedger()
    private val cache = GPUSolidRectSessionNativeCache<
        GPUWgpu4kSolidRectPipelineCacheKey,
        GPUWgpu4kSolidRectInvariantHandles,
    >(::createInvariants)

    fun acquire(key: GPUWgpu4kSolidRectPipelineCacheKey): GPUWgpu4kSolidRectInvariantHandles =
        cache.acquire(key)

    fun counters(): GPUSolidRectNativeCacheCounters = cache.counters()

    override fun close() {
        var firstFailure: Throwable? = null
        try {
            cache.close()
        } catch (failure: Throwable) {
            firstFailure = failure
        }
        if (!preRegistrationHandles.closeRetainingFailures()) {
            val failure = IllegalStateException(
                "SolidRect session cache retained ${preRegistrationHandles.pendingHandleCount} failed setup handle(s)",
            )
            if (firstFailure == null) firstFailure = failure else firstFailure.addSuppressed(failure)
        }
        firstFailure?.let { throw it }
    }

    private fun createInvariants(
        key: GPUWgpu4kSolidRectPipelineCacheKey,
    ): GPUWgpu4kSolidRectInvariantHandles {
        require(key.targetFormat == RGBA8_UNORM) {
            "SolidRect native cache accepts only rgba8unorm"
        }
        requireCleanSetupLedger()
        return try {
            val bindGroupLayout = device.createBindGroupLayout(
                BindGroupLayoutDescriptor(
                    label = "Kanvas.session.solidRect.bindGroupLayout",
                    entries = listOf(
                        BindGroupLayoutEntry(
                            binding = 0u,
                            visibility = GPUShaderStage.Fragment,
                            buffer = BufferBindingLayout(type = GPUBufferBindingType.Uniform),
                        ),
                    ),
                ),
            ).tracked()
            val shader = device.createShaderModule(
                ShaderModuleDescriptor(
                    label = "Kanvas.session.solidRect.shader",
                    code = SOLID_RECT_WGSL,
                ),
            ).tracked()
            val pipelineLayout = device.createPipelineLayout(
                PipelineLayoutDescriptor(
                    label = "Kanvas.session.solidRect.pipelineLayout",
                    bindGroupLayouts = listOf(bindGroupLayout),
                ),
            ).tracked()
            val pipeline = device.createRenderPipeline(
                RenderPipelineDescriptor(
                    label = "Kanvas.session.solidRect.pipeline",
                    layout = pipelineLayout,
                    vertex = VertexState(module = shader, entryPoint = "vs_main"),
                    primitive = PrimitiveState(),
                    multisample = MultisampleState(count = key.sampleCount.toUInt()),
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
            val owned = GPUSolidRectCachedHandleSet(
                listOf(bindGroupLayout, shader, pipelineLayout, pipeline),
            )
            preRegistrationHandles.transferAll()
            GPUWgpu4kSolidRectInvariantHandles(
                bindGroupLayout,
                shader,
                pipelineLayout,
                pipeline,
                owned,
            )
        } catch (failure: Throwable) {
            preRegistrationHandles.closeRetainingFailures()
            throw failure
        }
    }

    private fun requireCleanSetupLedger() {
        check(preRegistrationHandles.closeRetainingFailures()) {
            "SolidRect cache cannot allocate while failed setup handles remain quarantined"
        }
    }

    private fun <T : AutoCloseable> T.tracked(): T = preRegistrationHandles.track(this)
}

internal class GPUSolidRectCachedHandleSet(handles: List<AutoCloseable>) : AutoCloseable {
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
        firstFailure?.let { throw IllegalStateException("SolidRect cached handles remain live", it) }
    }
}

internal const val SOLID_RECT_SHADER_IDENTITY = "solid-rect-wgsl-v1"
internal const val SOLID_RECT_BINDING_LAYOUT_IDENTITY = "fragment-uniform64-v1"
internal const val SOLID_RECT_RGBA8_UNORM = "rgba8unorm"

internal val SOLID_RECT_WGSL = """
    struct SolidRectBlock {
        rect: vec4<f32>,
        radii: vec4<f32>,
        color: vec4<f32>,
        reserved: vec4<f32>,
    }

    @group(0) @binding(0) var<uniform> solid: SolidRectBlock;

    @vertex
    fn vs_main(@builtin(vertex_index) vertex_index: u32) -> @builtin(position) vec4<f32> {
        var positions = array<vec2<f32>, 3>(
            vec2<f32>(-1.0, -1.0),
            vec2<f32>(3.0, -1.0),
            vec2<f32>(-1.0, 3.0),
        );
        return vec4<f32>(positions[vertex_index], 0.0, 1.0);
    }

    @fragment
    fn fs_main(@builtin(position) position: vec4<f32>) -> @location(0) vec4<f32> {
        if (position.x < solid.rect.x || position.x >= solid.rect.z ||
            position.y < solid.rect.y || position.y >= solid.rect.w) {
            discard;
        }
        return vec4<f32>(solid.color.rgb * solid.color.a, solid.color.a);
    }
""".trimIndent()

private const val RGBA8_UNORM = SOLID_RECT_RGBA8_UNORM
