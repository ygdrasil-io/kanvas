package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.BindGroupLayoutDescriptor
import io.ygdrasil.webgpu.BindGroupLayoutEntry
import io.ygdrasil.webgpu.BindGroupDescriptor
import io.ygdrasil.webgpu.BindGroupEntry
import io.ygdrasil.webgpu.BufferBinding
import io.ygdrasil.webgpu.BufferBindingLayout
import io.ygdrasil.webgpu.BufferDescriptor
import io.ygdrasil.webgpu.Extent3D
import io.ygdrasil.webgpu.GPUBindGroup
import io.ygdrasil.webgpu.GPUBindGroupLayout
import io.ygdrasil.webgpu.GPUBuffer
import io.ygdrasil.webgpu.GPUBufferBindingType
import io.ygdrasil.webgpu.GPUBufferUsage
import io.ygdrasil.webgpu.GPUDevice
import io.ygdrasil.webgpu.GPUPipelineLayout
import io.ygdrasil.webgpu.GPURenderPipeline
import io.ygdrasil.webgpu.GPUShaderModule
import io.ygdrasil.webgpu.GPUShaderStage
import io.ygdrasil.webgpu.GPUTexture
import io.ygdrasil.webgpu.GPUTextureView
import io.ygdrasil.webgpu.PipelineLayoutDescriptor
import io.ygdrasil.webgpu.ShaderModuleDescriptor
import io.ygdrasil.webgpu.TextureDescriptor
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID

internal data class GPUCorePrimitiveNativeCacheCounters(
    val invariantCreations: Long = 0,
    val invariantReuses: Long = 0,
    val invariantInvalidations: Long = 0,
)

private const val CORE_PRIMITIVE_SESSION_PIPELINE_CACHE_MAX_ENTRIES = 16

internal data class GPUWgpu4kCorePrimitiveComponentIdentity(
    val shaderIdentity: String,
    val bindingLayoutIdentity: String,
    val vertexLayoutIdentity: String,
) {
    init {
        require(listOf(shaderIdentity, bindingLayoutIdentity, vertexLayoutIdentity).all(String::isNotBlank))
    }
}

internal data class GPUWgpu4kCorePrimitiveRenderPipelineIdentity(
    val targetFormat: String,
    val sampleCount: Int,
    val topology: String,
    val frontFace: String,
    val cullMode: String,
    val program: GPUWgpu4kCorePrimitivePipelineProgram,
) {
    init {
        require(targetFormat.isNotBlank() && sampleCount > 0)
        require(listOf(topology, frontFace, cullMode).all(String::isNotBlank))
    }
}

internal data class GPUWgpu4kCorePrimitivePipelineCacheKey(
    val componentIdentity: GPUWgpu4kCorePrimitiveComponentIdentity,
    val pipelineIdentity: GPUWgpu4kCorePrimitiveRenderPipelineIdentity,
) {
    constructor(targetFormat: String, sampleCount: Int) : this(
        componentIdentity = PRODUCTION_CORE_PRIMITIVE_COMPONENT_IDENTITY,
        pipelineIdentity = PRODUCTION_CORE_PRIMITIVE_PIPELINE_IDENTITY.copy(
            targetFormat = targetFormat,
            sampleCount = sampleCount,
        ),
    )
}

internal val PRODUCTION_CORE_PRIMITIVE_COMPONENT_IDENTITY =
    GPUWgpu4kCorePrimitiveComponentIdentity(
        shaderIdentity = CORE_PRIMITIVE_NATIVE_SHADER_IDENTITY,
        bindingLayoutIdentity = CORE_PRIMITIVE_NATIVE_BINDING_LAYOUT_IDENTITY,
        vertexLayoutIdentity = CORE_PRIMITIVE_NATIVE_VERTEX_LAYOUT_IDENTITY,
    )

internal val PRODUCTION_CORE_PRIMITIVE_ANALYTIC_CLIP_COMPONENT_IDENTITY =
    GPUWgpu4kCorePrimitiveComponentIdentity(
        shaderIdentity = CORE_PRIMITIVE_ANALYTIC_CLIP_NATIVE_SHADER_IDENTITY,
        bindingLayoutIdentity = CORE_PRIMITIVE_ANALYTIC_CLIP_NATIVE_BINDING_LAYOUT_IDENTITY,
        vertexLayoutIdentity = CORE_PRIMITIVE_NATIVE_VERTEX_LAYOUT_IDENTITY,
    )

private val PRODUCTION_CORE_PRIMITIVE_PIPELINE_IDENTITY =
    GPUWgpu4kCorePrimitiveRenderPipelineIdentity(
        targetFormat = "rgba8unorm",
        sampleCount = 1,
        topology = "triangle-list",
        frontFace = "ccw",
        cullMode = "none",
        program = GPUWgpu4kCorePrimitivePipelineProgram.DirectSrcOver,
    )

internal fun isSupportedCorePrimitivePipelineCacheKey(
    key: GPUWgpu4kCorePrimitivePipelineCacheKey,
): Boolean = isSupportedCorePrimitiveRenderPipelineIdentity(key.pipelineIdentity) &&
    key.hasCompatibleComponentIdentity()

private fun GPUWgpu4kCorePrimitivePipelineCacheKey.hasCompatibleComponentIdentity(): Boolean = when {
    pipelineIdentity.program.isAnalyticClip() ->
        componentIdentity == PRODUCTION_CORE_PRIMITIVE_ANALYTIC_CLIP_COMPONENT_IDENTITY
    else -> componentIdentity == PRODUCTION_CORE_PRIMITIVE_COMPONENT_IDENTITY
}

internal enum class GPUWgpu4kCorePrimitiveSessionCacheNativeResource {
    BindGroupLayout,
    ShaderModule,
    PipelineLayout,
    RenderPipeline,
}

internal sealed interface GPUWgpu4kCorePrimitiveSessionCacheRefusal {
    data class IncompatibleComponentIdentity(
        val expected: GPUWgpu4kCorePrimitiveComponentIdentity,
        val observed: GPUWgpu4kCorePrimitiveComponentIdentity,
    ) : GPUWgpu4kCorePrimitiveSessionCacheRefusal

    data class UnsupportedPipelineIdentity(
        val identity: GPUWgpu4kCorePrimitiveRenderPipelineIdentity,
    ) : GPUWgpu4kCorePrimitiveSessionCacheRefusal

    data class Saturated(val maxEntries: Int) : GPUWgpu4kCorePrimitiveSessionCacheRefusal
    data class NativeCreationFailed(
        val resource: GPUWgpu4kCorePrimitiveSessionCacheNativeResource,
        val failureType: String,
        val message: String,
        val pendingCleanupHandles: Int,
    ) : GPUWgpu4kCorePrimitiveSessionCacheRefusal

    data class CleanupPending(val pendingHandles: Int) : GPUWgpu4kCorePrimitiveSessionCacheRefusal
    data object Closing : GPUWgpu4kCorePrimitiveSessionCacheRefusal
    data object Closed : GPUWgpu4kCorePrimitiveSessionCacheRefusal
}

internal sealed interface GPUWgpu4kCorePrimitiveSessionCacheAcquire {
    data class Acquired(
        val handles: GPUWgpu4kCorePrimitiveInvariantHandles,
    ) : GPUWgpu4kCorePrimitiveSessionCacheAcquire

    data class Refused(
        val reason: GPUWgpu4kCorePrimitiveSessionCacheRefusal,
    ) : GPUWgpu4kCorePrimitiveSessionCacheAcquire
}

internal class GPUWgpu4kCorePrimitiveInvariantHandles(
    val bindGroupLayout: GPUBindGroupLayout,
    val shader: GPUShaderModule,
    val pipelineLayout: GPUPipelineLayout,
    val pipeline: GPURenderPipeline,
)

/** Native creation seam. Production accepts only the closed exact executable pipeline identities. */
internal interface GPUWgpu4kCorePrimitiveSessionNativeFactory {
    fun acceptsPipelineIdentity(identity: GPUWgpu4kCorePrimitiveRenderPipelineIdentity): Boolean
    fun createBindGroupLayout(componentIdentity: GPUWgpu4kCorePrimitiveComponentIdentity): GPUBindGroupLayout
    fun createShaderModule(
        componentIdentity: GPUWgpu4kCorePrimitiveComponentIdentity,
        plan: GPUCorePrimitiveNativeShaderPlan,
    ): GPUShaderModule
    fun createPipelineLayout(bindGroupLayout: GPUBindGroupLayout): GPUPipelineLayout
    fun createRenderPipeline(
        identity: GPUWgpu4kCorePrimitiveRenderPipelineIdentity,
        shader: GPUShaderModule,
        pipelineLayout: GPUPipelineLayout,
    ): GPURenderPipeline
}

private class GPUWgpu4kCorePrimitiveDeviceSessionNativeFactory(
    private val device: GPUDevice,
) : GPUWgpu4kCorePrimitiveSessionNativeFactory {
    override fun acceptsPipelineIdentity(
        identity: GPUWgpu4kCorePrimitiveRenderPipelineIdentity,
    ): Boolean = isSupportedCorePrimitiveRenderPipelineIdentity(identity)

    override fun createBindGroupLayout(
        componentIdentity: GPUWgpu4kCorePrimitiveComponentIdentity,
    ): GPUBindGroupLayout = device.createBindGroupLayout(
        BindGroupLayoutDescriptor(
            label = "Kanvas.session.corePrimitive.bindGroupLayout0.${componentIdentity.bindingLayoutIdentity}",
            entries = listOf(
                BindGroupLayoutEntry(
                    binding = 0u,
                    visibility = GPUShaderStage.Vertex or GPUShaderStage.Fragment,
                    buffer = BufferBindingLayout(
                        type = GPUBufferBindingType.Uniform,
                        hasDynamicOffset = true,
                        minBindingSize = if (
                            componentIdentity == PRODUCTION_CORE_PRIMITIVE_ANALYTIC_CLIP_COMPONENT_IDENTITY
                        ) 64uL else 32uL,
                    ),
                ),
            ),
        ),
    )

    override fun createShaderModule(
        componentIdentity: GPUWgpu4kCorePrimitiveComponentIdentity,
        plan: GPUCorePrimitiveNativeShaderPlan,
    ): GPUShaderModule =
        device.createShaderModule(
            ShaderModuleDescriptor(
                label = "Kanvas.session.corePrimitive.shader.${componentIdentity.shaderIdentity}",
                code = plan.wgslSource,
            ),
        )

    override fun createPipelineLayout(bindGroupLayout: GPUBindGroupLayout): GPUPipelineLayout =
        device.createPipelineLayout(
            PipelineLayoutDescriptor(
                label = "Kanvas.session.corePrimitive.pipelineLayout",
                bindGroupLayouts = listOf(bindGroupLayout),
            ),
        )

    override fun createRenderPipeline(
        identity: GPUWgpu4kCorePrimitiveRenderPipelineIdentity,
        shader: GPUShaderModule,
        pipelineLayout: GPUPipelineLayout,
    ): GPURenderPipeline {
        require(isSupportedCorePrimitiveRenderPipelineIdentity(identity)) {
            "The production CorePrimitive factory accepts only exact executable pipeline identities"
        }
        return device.createRenderPipeline(
            corePrimitiveWgpu4kRenderPipelineDescriptor(identity, shader, pipelineLayout),
        )
    }
}

private class GPUWgpu4kCorePrimitiveSharedComponentJournal(
    var bindGroupLayout: GPUBindGroupLayout? = null,
    var shader: GPUShaderModule? = null,
    var pipelineLayout: GPUPipelineLayout? = null,
) {
    val pendingHandleCount: Int
        get() = listOfNotNull(bindGroupLayout, shader, pipelineLayout).size

    /** Pipelines depend on layout+shader; the layout depends on the bind-group layout. */
    fun closeAfterPipelines(): Int {
        pipelineLayout = pipelineLayout.closeOrRetain()
        shader = shader.closeOrRetain()
        if (pipelineLayout == null) bindGroupLayout = bindGroupLayout.closeOrRetain()
        return pendingHandleCount
    }

    private fun <T : AutoCloseable> T?.closeOrRetain(): T? {
        this ?: return null
        return try {
            close()
            null
        } catch (_: Throwable) {
            this
        }
    }
}

/** One finite render-pipeline cache plus one shared component set owned by the prepared scene session. */
internal class GPUWgpu4kCorePrimitiveSessionCache(
    private val device: GPUDevice,
    deviceGeneration: GPUDeviceGenerationID,
    private val nativeFactory: GPUWgpu4kCorePrimitiveSessionNativeFactory =
        GPUWgpu4kCorePrimitiveDeviceSessionNativeFactory(device),
) : AutoCloseable {
    private enum class State { Open, Closing, Closed }

    private var state = State.Open
    private val sharedComponentsByIdentity = linkedMapOf<
        GPUWgpu4kCorePrimitiveComponentIdentity,
        GPUWgpu4kCorePrimitiveSharedComponentJournal,
    >()
    private val pendingSetupComponentsByIdentity = linkedMapOf<
        GPUWgpu4kCorePrimitiveComponentIdentity,
        GPUWgpu4kCorePrimitiveSharedComponentJournal,
    >()
    private val live = linkedMapOf<
        GPUWgpu4kCorePrimitivePipelineCacheKey,
        GPUWgpu4kCorePrimitiveInvariantHandles,
    >()
    private var creations = 0L
    private var reuses = 0L

    private val framePool = GPUWgpu4kCorePrimitiveFramePool(
        deviceGeneration,
        object : GPUWgpu4kCorePrimitiveFramePoolFactory {
            override fun createVertexBuffer(capacityBytes: Long): GPUBuffer = device.createBuffer(
                BufferDescriptor(
                    size = capacityBytes.toULong(),
                    usage = GPUBufferUsage.Vertex or GPUBufferUsage.CopyDst,
                    label = "Kanvas.session.corePrimitive.framePool.vertices",
                ),
            )

            override fun createIndexBuffer(capacityBytes: Long): GPUBuffer = device.createBuffer(
                BufferDescriptor(
                    size = capacityBytes.toULong(),
                    usage = GPUBufferUsage.Index or GPUBufferUsage.CopyDst,
                    label = "Kanvas.session.corePrimitive.framePool.indices",
                ),
            )

            override fun createUniformBuffer(capacityBytes: Long): GPUBuffer = device.createBuffer(
                BufferDescriptor(
                    size = capacityBytes.toULong(),
                    usage = GPUBufferUsage.Uniform or GPUBufferUsage.CopyDst,
                    label = "Kanvas.session.corePrimitive.framePool.uniforms",
                ),
            )

            override fun createBindGroup(
                componentIdentity: GPUWgpu4kCorePrimitiveComponentIdentity,
                uniformBuffer: GPUBuffer,
            ): GPUBindGroup = device.createBindGroup(
                BindGroupDescriptor(
                    label = if (componentIdentity == PRODUCTION_CORE_PRIMITIVE_COMPONENT_IDENTITY) {
                        "Kanvas.session.corePrimitive.framePool.bindGroup0"
                    } else {
                        "Kanvas.session.corePrimitive.framePool.bindGroup0." +
                            componentIdentity.bindingLayoutIdentity
                    },
                    layout = checkNotNull(
                        sharedComponentsByIdentity[componentIdentity]?.bindGroupLayout,
                    ) {
                        "CorePrimitive components must exist before the frame pool allocates a bind group"
                    },
                    entries = listOf(
                        BindGroupEntry(
                            binding = 0u,
                            resource = BufferBinding(
                                buffer = uniformBuffer,
                                offset = 0uL,
                                size = if (
                                    componentIdentity ==
                                    PRODUCTION_CORE_PRIMITIVE_ANALYTIC_CLIP_COMPONENT_IDENTITY
                                ) 64uL else CORE_PRIMITIVE_MIN_UNIFORM_BINDING_BYTES,
                            ),
                        ),
                    ),
                ),
            )

            override fun createPathDepthStencilTexture(
                requirement: GPUWgpu4kCorePrimitivePathDepthStencilRequirement,
            ): GPUTexture = device.createTexture(
                TextureDescriptor(
                    size = Extent3D(requirement.width.toUInt(), requirement.height.toUInt()),
                    format = requirement.format,
                    usage = requirement.usage,
                    sampleCount = requirement.sampleCount.toUInt(),
                    label = "Kanvas.session.corePrimitive.framePool.pathDepthStencil",
                ),
            )

            override fun createPathDepthStencilView(texture: GPUTexture): GPUTextureView =
                texture.createView()
        },
    )

    @Synchronized
    fun acquire(
        key: GPUWgpu4kCorePrimitivePipelineCacheKey,
    ): GPUWgpu4kCorePrimitiveSessionCacheAcquire {
        when (state) {
            State.Closing -> return refused(GPUWgpu4kCorePrimitiveSessionCacheRefusal.Closing)
            State.Closed -> return refused(GPUWgpu4kCorePrimitiveSessionCacheRefusal.Closed)
            State.Open -> Unit
        }
        if (!key.hasCompatibleComponentIdentity()) {
            if (key.componentIdentity != PRODUCTION_CORE_PRIMITIVE_COMPONENT_IDENTITY &&
                key.componentIdentity != PRODUCTION_CORE_PRIMITIVE_ANALYTIC_CLIP_COMPONENT_IDENTITY
            ) {
            return refused(
                GPUWgpu4kCorePrimitiveSessionCacheRefusal.IncompatibleComponentIdentity(
                    PRODUCTION_CORE_PRIMITIVE_COMPONENT_IDENTITY,
                    key.componentIdentity,
                ),
            )
            }
            return refused(
                GPUWgpu4kCorePrimitiveSessionCacheRefusal.UnsupportedPipelineIdentity(key.pipelineIdentity),
            )
        }
        if (!nativeFactory.acceptsPipelineIdentity(key.pipelineIdentity)) {
            return refused(
                GPUWgpu4kCorePrimitiveSessionCacheRefusal.UnsupportedPipelineIdentity(key.pipelineIdentity),
            )
        }
        live[key]?.let { handles ->
            reuses += 1
            return GPUWgpu4kCorePrimitiveSessionCacheAcquire.Acquired(handles)
        }
        if (live.size >= CORE_PRIMITIVE_SESSION_PIPELINE_CACHE_MAX_ENTRIES) {
            return refused(
                GPUWgpu4kCorePrimitiveSessionCacheRefusal.Saturated(
                    CORE_PRIMITIVE_SESSION_PIPELINE_CACHE_MAX_ENTRIES,
                ),
            )
        }
        pendingSetupComponentsByIdentity[key.componentIdentity]?.let { pending ->
            if (pending.closeAfterPipelines() != 0) {
                return refused(
                    GPUWgpu4kCorePrimitiveSessionCacheRefusal.CleanupPending(pending.pendingHandleCount),
                )
            }
            pendingSetupComponentsByIdentity.remove(key.componentIdentity)
        }
        return sharedComponentsByIdentity[key.componentIdentity]
            ?.let { components -> createAdditionalPipeline(key, components) }
            ?: createFirstPipeline(key)
    }

    @Synchronized
    fun acquireFrame(
        requirements: GPUWgpu4kCorePrimitiveFramePoolRequirements,
    ): GPUWgpu4kCorePrimitiveFramePoolCheckout = framePool.acquire(requirements)

    @Synchronized
    fun counters() = GPUCorePrimitiveNativeCacheCounters(creations, reuses, invariantInvalidations = 0)

    @Synchronized
    override fun close() {
        if (state == State.Closed) return
        state = State.Closing
        framePool.close()

        live.keys.toList().asReversed().forEach { key ->
            val pipeline = live.getValue(key).pipeline
            try {
                pipeline.close()
                live.remove(key)
            } catch (_: Throwable) {
                // Independent failed pipelines remain live; shared dependencies stay protected.
            }
        }
        if (live.isEmpty()) {
            sharedComponentsByIdentity.keys.toList().asReversed().forEach { identity ->
                val components = sharedComponentsByIdentity.getValue(identity)
                if (components.closeAfterPipelines() == 0) sharedComponentsByIdentity.remove(identity)
            }
        }
        pendingSetupComponentsByIdentity.keys.toList().asReversed().forEach { identity ->
            val pending = pendingSetupComponentsByIdentity.getValue(identity)
            if (pending.closeAfterPipelines() == 0) pendingSetupComponentsByIdentity.remove(identity)
        }

        val pendingHandles = live.size +
            sharedComponentsByIdentity.values.sumOf(GPUWgpu4kCorePrimitiveSharedComponentJournal::pendingHandleCount) +
            pendingSetupComponentsByIdentity.values.sumOf(
                GPUWgpu4kCorePrimitiveSharedComponentJournal::pendingHandleCount,
            )
        if (pendingHandles != 0) {
            error("CorePrimitive session cache retained $pendingHandles native handle(s)")
        }
        state = State.Closed
    }

    private fun createFirstPipeline(
        key: GPUWgpu4kCorePrimitivePipelineCacheKey,
    ): GPUWgpu4kCorePrimitiveSessionCacheAcquire {
        val components = GPUWgpu4kCorePrimitiveSharedComponentJournal()
        var resource = GPUWgpu4kCorePrimitiveSessionCacheNativeResource.ShaderModule
        return try {
            val shaderPlan = when (
                val shader = if (key.componentIdentity == PRODUCTION_CORE_PRIMITIVE_ANALYTIC_CLIP_COMPONENT_IDENTITY) {
                    buildCorePrimitiveAnalyticClipNativeShader()
                } else {
                    buildCorePrimitiveNativeShader()
                }
            ) {
                is GPUCorePrimitiveNativeShaderResult.Ready -> shader.plan
                is GPUCorePrimitiveNativeShaderResult.Rejected -> error(
                    "CorePrimitive parser-backed WGSL validation failed: ${shader.reason}: ${shader.message}",
                )
            }
            resource = GPUWgpu4kCorePrimitiveSessionCacheNativeResource.BindGroupLayout
            components.bindGroupLayout = nativeFactory.createBindGroupLayout(key.componentIdentity)
            resource = GPUWgpu4kCorePrimitiveSessionCacheNativeResource.ShaderModule
            components.shader = nativeFactory.createShaderModule(key.componentIdentity, shaderPlan)
            resource = GPUWgpu4kCorePrimitiveSessionCacheNativeResource.PipelineLayout
            components.pipelineLayout = nativeFactory.createPipelineLayout(requireNotNull(components.bindGroupLayout))
            resource = GPUWgpu4kCorePrimitiveSessionCacheNativeResource.RenderPipeline
            val pipeline = nativeFactory.createRenderPipeline(
                key.pipelineIdentity,
                requireNotNull(components.shader),
                requireNotNull(components.pipelineLayout),
            )
            sharedComponentsByIdentity[key.componentIdentity] = components
            install(key, components, pipeline)
        } catch (failure: Throwable) {
            val pending = components.closeAfterPipelines()
            if (pending != 0) pendingSetupComponentsByIdentity[key.componentIdentity] = components
            refused(
                GPUWgpu4kCorePrimitiveSessionCacheRefusal.NativeCreationFailed(
                    resource,
                    failure::class.simpleName.orEmpty(),
                    failure.message.orEmpty(),
                    pending,
                ),
            )
        }
    }

    private fun createAdditionalPipeline(
        key: GPUWgpu4kCorePrimitivePipelineCacheKey,
        components: GPUWgpu4kCorePrimitiveSharedComponentJournal,
    ): GPUWgpu4kCorePrimitiveSessionCacheAcquire = try {
        val pipeline = nativeFactory.createRenderPipeline(
            key.pipelineIdentity,
            requireNotNull(components.shader),
            requireNotNull(components.pipelineLayout),
        )
        install(key, components, pipeline)
    } catch (failure: Throwable) {
        refused(
            GPUWgpu4kCorePrimitiveSessionCacheRefusal.NativeCreationFailed(
                GPUWgpu4kCorePrimitiveSessionCacheNativeResource.RenderPipeline,
                failure::class.simpleName.orEmpty(),
                failure.message.orEmpty(),
                pendingCleanupHandles = 0,
            ),
        )
    }

    private fun install(
        key: GPUWgpu4kCorePrimitivePipelineCacheKey,
        components: GPUWgpu4kCorePrimitiveSharedComponentJournal,
        pipeline: GPURenderPipeline,
    ): GPUWgpu4kCorePrimitiveSessionCacheAcquire {
        val handles = GPUWgpu4kCorePrimitiveInvariantHandles(
            requireNotNull(components.bindGroupLayout),
            requireNotNull(components.shader),
            requireNotNull(components.pipelineLayout),
            pipeline,
        )
        live[key] = handles
        creations += 1
        return GPUWgpu4kCorePrimitiveSessionCacheAcquire.Acquired(handles)
    }

    private fun refused(
        reason: GPUWgpu4kCorePrimitiveSessionCacheRefusal,
    ) = GPUWgpu4kCorePrimitiveSessionCacheAcquire.Refused(reason)

    private companion object {
        val CORE_PRIMITIVE_MIN_UNIFORM_BINDING_BYTES = 32uL
    }
}
