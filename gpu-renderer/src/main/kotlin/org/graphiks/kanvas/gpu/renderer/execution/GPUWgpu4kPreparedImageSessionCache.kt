package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.BindGroupLayoutDescriptor
import io.ygdrasil.webgpu.BindGroupLayoutEntry
import io.ygdrasil.webgpu.BlendComponent
import io.ygdrasil.webgpu.BlendState
import io.ygdrasil.webgpu.BufferBindingLayout
import io.ygdrasil.webgpu.ColorTargetState
import io.ygdrasil.webgpu.FragmentState
import io.ygdrasil.webgpu.GPUBindGroupLayout
import io.ygdrasil.webgpu.GPUBlendFactor
import io.ygdrasil.webgpu.GPUBlendOperation
import io.ygdrasil.webgpu.GPUBufferBindingType
import io.ygdrasil.webgpu.GPUDevice
import io.ygdrasil.webgpu.GPUPipelineLayout
import io.ygdrasil.webgpu.GPURenderPipeline
import io.ygdrasil.webgpu.GPUSamplerBindingType
import io.ygdrasil.webgpu.GPUShaderModule
import io.ygdrasil.webgpu.GPUShaderStage
import io.ygdrasil.webgpu.GPUTextureFormat
import io.ygdrasil.webgpu.GPUTextureSampleType
import io.ygdrasil.webgpu.GPUTextureViewDimension
import io.ygdrasil.webgpu.PipelineLayoutDescriptor
import io.ygdrasil.webgpu.PrimitiveState
import io.ygdrasil.webgpu.RenderPipelineDescriptor
import io.ygdrasil.webgpu.SamplerBindingLayout
import io.ygdrasil.webgpu.ShaderModuleDescriptor
import io.ygdrasil.webgpu.TextureBindingLayout
import io.ygdrasil.webgpu.VertexState
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageRefusalCodes
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImagePipelineKey

internal data class GPUPreparedImageCachedPipeline(
    val contract: GPUPreparedImageShaderContract,
    val bindGroupLayout: GPUBindGroupLayout,
    val shader: GPUShaderModule,
    val pipelineLayout: GPUPipelineLayout,
    val pipeline: GPURenderPipeline,
    val deviceGeneration: GPUDeviceGenerationID,
)

internal sealed interface GPUPreparedImageCacheAcquire {
    data class Ready(val pipeline: GPUPreparedImageCachedPipeline) :
        GPUPreparedImageCacheAcquire

    data class Refused(val code: String, val message: String) :
        GPUPreparedImageCacheAcquire
}

private data class GPUPreparedImageCanonicalPipelineState(
    val key: GPUPreparedImagePipelineKey,
    val targetFormat: GPUTextureFormat,
    val destinationBlendState: BlendState,
)

private sealed interface GPUPreparedImagePipelineCanonicalization {
    data class Ready(val state: GPUPreparedImageCanonicalPipelineState) :
        GPUPreparedImagePipelineCanonicalization

    data class Refused(val code: String, val message: String) :
        GPUPreparedImagePipelineCanonicalization
}

/**
 * Session-owned prepared-image cache. Its closed ownership set is shader module, reflected
 * bind-group layout, pipeline layout and keyed render pipelines only.
 */
internal class GPUWgpu4kPreparedImageSessionCache(
    private val device: GPUDevice,
    internal val deviceGeneration: GPUDeviceGenerationID,
) : AutoCloseable {
    private var bindGroupLayout: GPUBindGroupLayout? = null
    private var shader: GPUShaderModule? = null
    private var pipelineLayout: GPUPipelineLayout? = null
    private var contract: GPUPreparedImageShaderContract? = null
    private val pipelines = linkedMapOf<GPUPreparedImagePipelineKey, GPUPreparedImageCachedPipeline>()
    private val owned = mutableListOf<AutoCloseable>()
    private var closed = false

    @Synchronized
    fun acquire(
        key: GPUPreparedImagePipelineKey,
        actualDeviceGeneration: GPUDeviceGenerationID,
    ): GPUPreparedImageCacheAcquire {
        check(!closed) { "Prepared-image session cache is closed" }
        if (actualDeviceGeneration != deviceGeneration) {
            return GPUPreparedImageCacheAcquire.Refused(
                code = GPUPreparedImageRefusalCodes.NATIVE_GENERATION,
                message =
                    "Prepared-image cache device generation mismatch " +
                        "expected=${deviceGeneration.value} actual=${actualDeviceGeneration.value}",
            )
        }
        val canonical = when (val result = canonicalize(key)) {
            is GPUPreparedImagePipelineCanonicalization.Ready -> result.state
            is GPUPreparedImagePipelineCanonicalization.Refused ->
                return GPUPreparedImageCacheAcquire.Refused(result.code, result.message)
        }
        ensureInvariants()
        val descriptorLayout = requireNotNull(pipelineLayout)
        val cached = pipelines.getOrPut(canonical.key) {
            val pipeline = track(
                device.createRenderPipeline(
                    RenderPipelineDescriptor(
                        label = "Kanvas.session.preparedImage.pipeline.${pipelines.size}",
                        layout = descriptorLayout,
                        vertex = VertexState(
                            module = requireNotNull(shader),
                            entryPoint = "vs_main",
                        ),
                        primitive = PrimitiveState(),
                        fragment = FragmentState(
                            module = requireNotNull(shader),
                            entryPoint = "fs_main",
                            targets = listOf(
                                ColorTargetState(
                                    format = canonical.targetFormat,
                                    blend = canonical.destinationBlendState,
                                ),
                            ),
                        ),
                    ),
                ),
            )
            GPUPreparedImageCachedPipeline(
                requireNotNull(contract),
                requireNotNull(bindGroupLayout),
                requireNotNull(shader),
                descriptorLayout,
                pipeline,
                deviceGeneration,
            )
        }
        return GPUPreparedImageCacheAcquire.Ready(cached)
    }

    @Synchronized
    fun invalidateForDeviceLoss() {
        close()
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        retireOwnedOnce()
    }

    private fun ensureInvariants() {
        if (shader != null) return
        val created = mutableListOf<AutoCloseable>()
        try {
            val bindingLayoutContract = preparedImageBindingLayoutContract()
            val shaderContract = preparedImageShaderContract()
            val reflectedLayout = device.createBindGroupLayout(
                BindGroupLayoutDescriptor(
                    label =
                        "Kanvas.session.preparedImage.bindGroupLayout" +
                            bindingLayoutContract.group,
                    entries = listOf(
                        BindGroupLayoutEntry(
                            binding = bindingLayoutContract.uniformBinding.toUInt(),
                            visibility = GPUShaderStage.Vertex or GPUShaderStage.Fragment,
                            buffer = BufferBindingLayout(
                                type = GPUBufferBindingType.Uniform,
                                hasDynamicOffset = true,
                                minBindingSize =
                                    bindingLayoutContract.uniformMinBindingSize.toULong(),
                            ),
                        ),
                        BindGroupLayoutEntry(
                            binding = bindingLayoutContract.textureBinding.toUInt(),
                            visibility = GPUShaderStage.Fragment,
                            texture = TextureBindingLayout(
                                sampleType = GPUTextureSampleType.Float,
                                viewDimension = GPUTextureViewDimension.TwoD,
                                multisampled = false,
                            ),
                        ),
                        BindGroupLayoutEntry(
                            binding = bindingLayoutContract.samplerBinding.toUInt(),
                            visibility = GPUShaderStage.Fragment,
                            sampler = SamplerBindingLayout(GPUSamplerBindingType.Filtering),
                        ),
                    ),
                ),
            ).also(created::add)
            val module = device.createShaderModule(
                ShaderModuleDescriptor(
                    label = "Kanvas.session.preparedImage.shader",
                    code = GPU_PREPARED_IMAGE_WGSL,
                ),
            ).also(created::add)
            val layout = device.createPipelineLayout(
                PipelineLayoutDescriptor(
                    label = "Kanvas.session.preparedImage.pipelineLayout",
                    bindGroupLayouts = listOf(reflectedLayout),
                ),
            ).also(created::add)
            bindGroupLayout = reflectedLayout
            shader = module
            pipelineLayout = layout
            contract = shaderContract
            owned += created
        } catch (failure: Throwable) {
            closeCreatedOnce(created, failure)
            throw failure
        }
    }

    private fun <T : AutoCloseable> track(handle: T): T {
        owned += handle
        return handle
    }

    private fun retireOwnedOnce() {
        val pending = owned.asReversed().toList()
        owned.clear()
        pipelines.clear()
        bindGroupLayout = null
        shader = null
        pipelineLayout = null
        contract = null
        var firstFailure: Throwable? = null
        pending.forEach { handle ->
            try {
                handle.close()
            } catch (failure: Throwable) {
                if (firstFailure == null) firstFailure = failure else firstFailure.addSuppressed(failure)
            }
        }
        firstFailure?.let { throw IllegalStateException("Prepared-image session cache close failed", it) }
    }

    private fun canonicalize(
        key: GPUPreparedImagePipelineKey,
    ): GPUPreparedImagePipelineCanonicalization {
        val bindingLayoutIdentity = preparedImageBindingLayoutContract().identity
        if (key.bindingLayoutHash != bindingLayoutIdentity) {
            return GPUPreparedImagePipelineCanonicalization.Refused(
                code = GPUPreparedImageRefusalCodes.NATIVE_BINDING,
                message =
                    "Unsupported prepared-image binding layout " +
                        "expected=$bindingLayoutIdentity actual=${key.bindingLayoutHash}",
            )
        }
        val targetFormat = when (key.targetFormat.trim().lowercase()) {
            "rgba8unorm" -> GPUTextureFormat.RGBA8Unorm
            else ->
                return GPUPreparedImagePipelineCanonicalization.Refused(
                    code = GPUPreparedImageRefusalCodes.PIXEL_FORMAT,
                    message = "Unsupported prepared-image target format ${key.targetFormat}",
                )
        }
        val normalizedBlend = key.destinationBlendState
            .trim()
            .lowercase()
            .replace('_', '-')
        val destinationBlendState = when (normalizedBlend) {
            "src-over", "srcover" -> preparedImageSrcOverBlendState()
            else ->
                return GPUPreparedImagePipelineCanonicalization.Refused(
                    code = GPUPreparedImageRefusalCodes.NATIVE_BINDING,
                    message =
                        "Unsupported prepared-image destination blend state " +
                            key.destinationBlendState,
                )
        }
        return GPUPreparedImagePipelineCanonicalization.Ready(
            GPUPreparedImageCanonicalPipelineState(
                key = GPUPreparedImagePipelineKey(
                    destinationBlendState = "src-over",
                    targetFormat = "RGBA8Unorm",
                    bindingLayoutHash = bindingLayoutIdentity,
                ),
                targetFormat = targetFormat,
                destinationBlendState = destinationBlendState,
            ),
        )
    }
}

private fun preparedImageSrcOverBlendState(): BlendState = BlendState(
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
)

private fun closeCreatedOnce(handles: List<AutoCloseable>, cause: Throwable) {
    handles.asReversed().forEach { handle ->
        try {
            handle.close()
        } catch (closeFailure: Throwable) {
            cause.addSuppressed(closeFailure)
        }
    }
}
