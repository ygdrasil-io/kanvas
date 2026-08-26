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
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUPreparedImageRefusalCodes
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImageBindingLayoutTopology
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

internal sealed interface GPUPreparedImageCacheBatchAcquire {
    data class Ready(
        val pipelinesByKey: Map<GPUPreparedImagePipelineKey, GPUPreparedImageCachedPipeline>,
    ) : GPUPreparedImageCacheBatchAcquire

    data class Refused(val code: String, val message: String) :
        GPUPreparedImageCacheBatchAcquire
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
    private val counterRecorder: GPUPreparedImageNativeCounterRecorder =
        GPUPreparedImageNativeCounterRecorder(),
    private val shaderSource: String = GPU_PREPARED_IMAGE_WGSL,
) : AutoCloseable {
    private enum class Lifecycle {
        Active,
        Closing,
        Closed,
    }

    private var bindGroupLayout: GPUBindGroupLayout? = null
    private var shader: GPUShaderModule? = null
    private var pipelineLayout: GPUPipelineLayout? = null
    private var contract: GPUPreparedImageShaderContract? = null
    private val pipelines = linkedMapOf<GPUPreparedImagePipelineKey, GPUPreparedImageCachedPipeline>()
    private val owned = mutableListOf<AutoCloseable>()
    private var lifecycle = Lifecycle.Active

    fun acquire(
        key: GPUPreparedImagePipelineKey,
        actualDeviceGeneration: GPUDeviceGenerationID,
    ): GPUPreparedImageCacheAcquire =
        when (val batch = acquireBatch(listOf(key), actualDeviceGeneration)) {
            is GPUPreparedImageCacheBatchAcquire.Ready ->
                GPUPreparedImageCacheAcquire.Ready(batch.pipelinesByKey.getValue(key))
            is GPUPreparedImageCacheBatchAcquire.Refused ->
                GPUPreparedImageCacheAcquire.Refused(batch.code, batch.message)
        }

    @Synchronized
    fun acquireBatch(
        keys: List<GPUPreparedImagePipelineKey>,
        actualDeviceGeneration: GPUDeviceGenerationID,
    ): GPUPreparedImageCacheBatchAcquire {
        check(lifecycle == Lifecycle.Active) {
            "Prepared-image session cache is ${lifecycle.name.lowercase()}"
        }
        if (actualDeviceGeneration != deviceGeneration) {
            return GPUPreparedImageCacheBatchAcquire.Refused(
                code = GPUPreparedImageRefusalCodes.NATIVE_GENERATION,
                message =
                    "Prepared-image cache device generation mismatch " +
                        "expected=${deviceGeneration.value} actual=${actualDeviceGeneration.value}",
            )
        }
        val canonicalByRawKey =
            linkedMapOf<GPUPreparedImagePipelineKey, GPUPreparedImageCanonicalPipelineState>()
        keys.forEach { key ->
            when (val result = canonicalize(key)) {
                is GPUPreparedImagePipelineCanonicalization.Ready -> {
                    canonicalByRawKey[key] = result.state
                }
                is GPUPreparedImagePipelineCanonicalization.Refused ->
                    return GPUPreparedImageCacheBatchAcquire.Refused(
                        result.code,
                        result.message,
                    )
            }
        }
        if (canonicalByRawKey.isEmpty()) {
            return GPUPreparedImageCacheBatchAcquire.Ready(emptyMap())
        }
        ensureInvariants()?.let { return it }
        val descriptorLayout = requireNotNull(pipelineLayout)
        val acquiredByRawKey =
            linkedMapOf<GPUPreparedImagePipelineKey, GPUPreparedImageCachedPipeline>()
        canonicalByRawKey.forEach { (rawKey, canonical) ->
            val cached = pipelines[canonical.key]
            acquiredByRawKey[rawKey] = if (cached != null) {
                counterRecorder.recordPipelineReuse()
                cached
            } else {
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
                val created = GPUPreparedImageCachedPipeline(
                    requireNotNull(contract),
                    requireNotNull(bindGroupLayout),
                    requireNotNull(shader),
                    descriptorLayout,
                    pipeline,
                    deviceGeneration,
                )
                pipelines[canonical.key] = created
                counterRecorder.recordPipelineCreation()
                created
            }
        }
        return GPUPreparedImageCacheBatchAcquire.Ready(acquiredByRawKey.toMap())
    }

    @Synchronized
    fun invalidateForDeviceLoss() {
        close()
    }

    @Synchronized
    override fun close() {
        if (lifecycle == Lifecycle.Closed) return
        lifecycle = Lifecycle.Closing
        retireOwnedInDependencyOrder()
        pipelines.clear()
        bindGroupLayout = null
        shader = null
        pipelineLayout = null
        contract = null
        lifecycle = Lifecycle.Closed
    }

    private fun ensureInvariants(): GPUPreparedImageCacheBatchAcquire.Refused? {
        if (shader != null) return null
        val validated = when (val validation = validatePreparedImageShader(shaderSource)) {
            is GPUPreparedImageShaderValidationResult.Ready -> validation
            is GPUPreparedImageShaderValidationResult.Refused ->
                return GPUPreparedImageCacheBatchAcquire.Refused(
                    code = validation.code,
                    message =
                        "Prepared-image WGSL validation refused: " +
                            validation.facts.entries.joinToString(),
                )
        }
        val created = mutableListOf<AutoCloseable>()
        try {
            val bindingLayoutContract = validated.bindingLayout
            val shaderContract = validated.shaderContract
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
                    code = shaderSource,
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
            return null
        } catch (failure: Throwable) {
            owned += created
            try {
                retireOwnedInDependencyOrder()
            } catch (rollbackFailure: GPUOwnedNativeCloseIncompleteException) {
                lifecycle = Lifecycle.Closing
                failure.addSuppressed(rollbackFailure)
            }
            throw failure
        }
    }

    private fun <T : AutoCloseable> track(handle: T): T {
        owned += handle
        return handle
    }

    private fun retireOwnedInDependencyOrder() {
        while (owned.isNotEmpty()) {
            val pendingIndex = owned.lastIndex
            val handle = owned[pendingIndex]
            try {
                handle.close()
                owned.removeAt(pendingIndex)
            } catch (failure: Throwable) {
                throw GPUOwnedNativeCloseIncompleteException(
                    ownerLabel = "prepared-image-session-cache",
                    remainingOwnerCount = owned.size,
                    failures = listOf(failure),
                )
            }
        }
    }

    private fun canonicalize(
        key: GPUPreparedImagePipelineKey,
    ): GPUPreparedImagePipelineCanonicalization {
        val bindingLayoutIdentity = GPUPreparedImageBindingLayoutTopology.IDENTITY
        if (key.bindingLayoutHash != bindingLayoutIdentity) {
            return GPUPreparedImagePipelineCanonicalization.Refused(
                code = GPUPreparedImageRefusalCodes.NATIVE_BINDING,
                message =
                    "Unsupported prepared-image binding layout " +
                        "expected=$bindingLayoutIdentity actual=${key.bindingLayoutHash}",
            )
        }
        val targetFormat = when (key.targetFormat.trim().lowercase()) {
            "rgba8unormsrgb", "rgba8unorm-srgb" -> GPUTextureFormat.RGBA8UnormSrgb
            else ->
                return GPUPreparedImagePipelineCanonicalization.Refused(
                    code = GPUPreparedImageRefusalCodes.IMAGE_PROFILE_CONVERSION,
                    message = "Unsupported prepared-image target format ${key.targetFormat}",
                )
        }
        val normalizedBlend = key.destinationBlendState
            .trim()
            .lowercase()
            .replace('_', '-')
        val destinationBlendState = when (normalizedBlend) {
            "src-over",
            "srcover",
            PREPARED_IMAGE_CANONICAL_SRC_OVER_BLEND,
            -> preparedImageSrcOverBlendState()
            "src",
            PREPARED_IMAGE_CANONICAL_SRC_BLEND,
            -> preparedImageSrcBlendState()
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
                    destinationBlendState = if (normalizedBlend == "src" ||
                        normalizedBlend == PREPARED_IMAGE_CANONICAL_SRC_BLEND
                    ) "src" else "src-over",
                    targetFormat = "RGBA8UnormSrgb",
                    bindingLayoutHash = bindingLayoutIdentity,
                ),
                targetFormat = targetFormat,
                destinationBlendState = destinationBlendState,
            ),
        )
    }
}

private const val PREPARED_IMAGE_CANONICAL_SRC_OVER_BLEND =
    "fixed:src-over:none:one-isa:one:one-minus-src-alpha:" +
        "add:one:one-minus-src-alpha:add:rgba"

private const val PREPARED_IMAGE_CANONICAL_SRC_BLEND =
    "fixed:src:none:one:zero:add:one:zero:add:rgba"

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

private fun preparedImageSrcBlendState(): BlendState = BlendState(
    color = BlendComponent(
        GPUBlendOperation.Add,
        GPUBlendFactor.One,
        GPUBlendFactor.Zero,
    ),
    alpha = BlendComponent(
        GPUBlendOperation.Add,
        GPUBlendFactor.One,
        GPUBlendFactor.Zero,
    ),
)
