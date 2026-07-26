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
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedAtlasSourceBlend
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImagePipelineKey

internal data class GPUWgpu4kPreparedImagePipelineHandles(
    val contract: GPUPreparedImageShaderContract,
    val bindGroupLayout: GPUBindGroupLayout,
    val shader: GPUShaderModule,
    val pipelineLayout: GPUPipelineLayout,
    val pipeline: GPURenderPipeline,
    val deviceGeneration: GPUDeviceGenerationID,
)

/**
 * Session-owned prepared-image cache. Its closed ownership set is shader module, reflected
 * bind-group layout, pipeline layout and keyed render pipelines only.
 */
internal class GPUWgpu4kPreparedImageSessionCache(
    private val device: GPUDevice,
    initialDeviceGeneration: GPUDeviceGenerationID,
) : AutoCloseable {
    private var generation = initialDeviceGeneration
    private var bindGroupLayout: GPUBindGroupLayout? = null
    private var shader: GPUShaderModule? = null
    private var pipelineLayout: GPUPipelineLayout? = null
    private var contract: GPUPreparedImageShaderContract? = null
    private val pipelines = linkedMapOf<GPUPreparedImagePipelineKey, GPURenderPipeline>()
    private val owned = mutableListOf<AutoCloseable>()
    private var closed = false

    internal val deviceGeneration: GPUDeviceGenerationID
        @Synchronized get() = generation

    @Synchronized
    fun acquire(
        key: GPUPreparedImagePipelineKey,
        actualDeviceGeneration: GPUDeviceGenerationID = generation,
    ): GPUWgpu4kPreparedImagePipelineHandles {
        check(!closed) { "Prepared-image session cache is closed" }
        if (actualDeviceGeneration != generation) {
            retireOwnedOnce()
            generation = actualDeviceGeneration
        }
        validateKey(key)
        ensureInvariants()
        val pipeline = pipelines.getOrPut(key) {
            track(
                device.createRenderPipeline(
                    RenderPipelineDescriptor(
                        label = "Kanvas.session.preparedImage.pipeline.${pipelines.size}",
                        layout = requireNotNull(pipelineLayout),
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
                ),
            )
        }
        return GPUWgpu4kPreparedImagePipelineHandles(
            requireNotNull(contract),
            requireNotNull(bindGroupLayout),
            requireNotNull(shader),
            requireNotNull(pipelineLayout),
            pipeline,
            generation,
        )
    }

    @Synchronized
    fun invalidateForDeviceLoss() {
        if (closed) return
        retireOwnedOnce()
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

    private fun validateKey(key: GPUPreparedImagePipelineKey) {
        require(key.bindingLayoutHash == preparedImageBindingLayoutContract().identity)
        require(key.targetFormat.equals("rgba8unorm", ignoreCase = true))
        require(key.destinationBlendState.equals("SrcOver", ignoreCase = true) ||
            key.destinationBlendState.equals("src_over", ignoreCase = true)
        )
        require(key.atlasSourceBlend == null ||
            key.atlasSourceBlend in GPUPreparedAtlasSourceBlend.entries
        )
        require((key.atlasColorMode == "none") == (key.atlasSourceBlend == null))
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
}

private fun closeCreatedOnce(handles: List<AutoCloseable>, cause: Throwable) {
    handles.asReversed().forEach { handle ->
        try {
            handle.close()
        } catch (closeFailure: Throwable) {
            cause.addSuppressed(closeFailure)
        }
    }
}
