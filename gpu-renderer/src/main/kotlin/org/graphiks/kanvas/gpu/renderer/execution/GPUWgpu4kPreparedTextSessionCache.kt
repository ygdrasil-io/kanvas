package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.BindGroupLayoutDescriptor
import io.ygdrasil.webgpu.BindGroupLayoutEntry
import io.ygdrasil.webgpu.BlendComponent
import io.ygdrasil.webgpu.BlendState
import io.ygdrasil.webgpu.BufferBindingLayout
import io.ygdrasil.webgpu.ColorTargetState
import io.ygdrasil.webgpu.FragmentState
import io.ygdrasil.webgpu.GPUAddressMode
import io.ygdrasil.webgpu.GPUBindGroupLayout
import io.ygdrasil.webgpu.GPUBlendFactor
import io.ygdrasil.webgpu.GPUBlendOperation
import io.ygdrasil.webgpu.GPUBufferBindingType
import io.ygdrasil.webgpu.GPUColorWrite
import io.ygdrasil.webgpu.GPUDevice
import io.ygdrasil.webgpu.GPUFilterMode
import io.ygdrasil.webgpu.GPUMipmapFilterMode
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
import io.ygdrasil.webgpu.GPUVertexStepMode
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
import java.util.Collections
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.recording.GPUPreparedTextNativeProgramHandoff
import org.graphiks.kanvas.gpu.renderer.resources.GPUMaterialTextureFrameResourcePlan
import org.graphiks.kanvas.gpu.renderer.state.GPUFixedFunctionBlendState

internal sealed interface GPUWgpu4kPreparedTextPipelineAcquisition {
    val pipeline: GPURenderPipeline
    val drawBindGroupLayout: GPUBindGroupLayout
    val materialBindGroupLayout: GPUBindGroupLayout
    val atlasBindGroupLayout: GPUBindGroupLayout
    val coverageMaskBindGroupLayout: GPUBindGroupLayout?
    val atlasSampler: GPUSampler
    val materialSamplersByResourceKey: Map<String, GPUSampler>
}

private class IssuedGPUWgpu4kPreparedTextPipelineAcquisition(
    override val pipeline: GPURenderPipeline,
    override val drawBindGroupLayout: GPUBindGroupLayout,
    override val materialBindGroupLayout: GPUBindGroupLayout,
    override val atlasBindGroupLayout: GPUBindGroupLayout,
    override val coverageMaskBindGroupLayout: GPUBindGroupLayout?,
    override val atlasSampler: GPUSampler,
    materialSamplersByResourceKey: Map<String, GPUSampler>,
) : GPUWgpu4kPreparedTextPipelineAcquisition {
    override val materialSamplersByResourceKey: Map<String, GPUSampler> =
        Collections.unmodifiableMap(materialSamplersByResourceKey.toMap())
}

internal sealed interface GPUPreparedTextCacheBatchAcquire {
    data class Ready(
        val pipelinesByKey: Map<String, GPUWgpu4kPreparedTextPipelineAcquisition>,
    ) : GPUPreparedTextCacheBatchAcquire

    data class Refused(
        val code: String,
        val message: String,
    ) : GPUPreparedTextCacheBatchAcquire
}

internal data class GPUPreparedTextSamplerCacheSnapshot(
    val residentEntryCount: Int,
    val hitCount: Int,
    val missCount: Int,
)

private data class GPUPreparedTextSamplerState(
    val addressModeU: GPUAddressMode,
    val addressModeV: GPUAddressMode,
    val addressModeW: GPUAddressMode,
    val magFilter: GPUFilterMode,
    val minFilter: GPUFilterMode,
    val mipmapFilter: GPUMipmapFilterMode,
    val lodMinClampBits: Int,
    val lodMaxClampBits: Int,
    val compare: String?,
    val maxAnisotropy: UShort,
)

private class GPUWgpu4kPreparedTextCachedPipeline(
    val program: GPUPreparedTextNativeProgramHandoff,
    val shader: GPUShaderModule,
    val drawBindGroupLayout: GPUBindGroupLayout,
    val materialBindGroupLayout: GPUBindGroupLayout,
    val atlasBindGroupLayout: GPUBindGroupLayout,
    val coverageMaskBindGroupLayout: GPUBindGroupLayout?,
    val pipelineLayout: GPUPipelineLayout,
    val pipeline: GPURenderPipeline,
    val atlasSampler: GPUSampler,
    val owned: MutableList<AutoCloseable>,
)

/**
 * Device-generation-scoped prepared TextA8 pipeline cache.
 *
 * Only shader/layout/pipeline/sampler invariants enter this cache. Every texture, view, buffer,
 * bind group, host upload, and upload state remains frame-local.
 */
internal class GPUWgpu4kPreparedTextSessionCache(
    private val device: GPUDevice,
    private val generation: GPUDeviceGenerationID,
) : AutoCloseable {
    private enum class Lifecycle { Active, Closing, Closed }

    private val pipelines = linkedMapOf<String, GPUWgpu4kPreparedTextCachedPipeline>()
    private val materialSamplers = linkedMapOf<GPUPreparedTextSamplerState, GPUSampler>()
    private val retainedFailedSetup = mutableListOf<AutoCloseable>()
    private var materialSamplerHitCount = 0
    private var materialSamplerMissCount = 0
    private var lifecycle = Lifecycle.Active

    @Synchronized
    fun acquireBatch(
        programs: List<GPUPreparedTextNativeProgramHandoff>,
        generation: GPUDeviceGenerationID,
    ): GPUPreparedTextCacheBatchAcquire = acquireBatch(
        programs = programs,
        generation = generation,
        materialResourcesByPipelineKey = emptyMap(),
    )

    @Synchronized
    fun acquireBatch(
        programs: List<GPUPreparedTextNativeProgramHandoff>,
        generation: GPUDeviceGenerationID,
        materialResourcesByPipelineKey:
            Map<String, List<GPUMaterialTextureFrameResourcePlan>>,
    ): GPUPreparedTextCacheBatchAcquire {
        check(lifecycle == Lifecycle.Active) {
            "Prepared-text session cache is ${lifecycle.name.lowercase()}"
        }
        if (generation != this.generation) {
            return GPUPreparedTextCacheBatchAcquire.Refused(
                "stale.prepared_text.device_generation",
                "Prepared-text cache generation expected=${this.generation.value} " +
                    "actual=${generation.value}.",
            )
        }
        if (programs.isEmpty()) return GPUPreparedTextCacheBatchAcquire.Ready(emptyMap())
        val programsByKey = linkedMapOf<
            String,
            MutableList<GPUPreparedTextNativeProgramHandoff>,
            >()
        programs.forEach { program ->
            programsByKey.getOrPut(program.pipelineKey) { mutableListOf() } += program
        }
        programsByKey.forEach { (key, sameKeyPrograms) ->
            val canonical = sameKeyPrograms.first()
            if (sameKeyPrograms.any { candidate -> !candidate.sameProgramAs(canonical) } ||
                pipelines[key]?.program?.sameProgramAs(canonical) == false
            ) {
                return GPUPreparedTextCacheBatchAcquire.Refused(
                    "invalid.prepared_text.pipeline_key_collision",
                    "Prepared-text pipeline key $key names conflicting immutable programs.",
                )
            }
        }

        val createdEntries = mutableListOf<Pair<String, GPUWgpu4kPreparedTextCachedPipeline>>()
        val createdSamplers = mutableListOf<Pair<GPUPreparedTextSamplerState, GPUSampler>>()
        val samplerAliasesByPipelineKey = linkedMapOf<String, Map<String, GPUSampler>>()
        return try {
            programsByKey.forEach { (key, sameKeyPrograms) ->
                val cached = pipelines[key] ?: createPipeline(sameKeyPrograms.first()).also {
                    createdEntries += key to it
                }
                samplerAliasesByPipelineKey[key] = resolveMaterialSamplers(
                    resources = materialResourcesByPipelineKey[key].orEmpty(),
                    createdSamplers = createdSamplers,
                )
            }
            createdEntries.forEach { (key, entry) -> pipelines[key] = entry }
            val acquired = programsByKey.mapValues { (_, sameKeyPrograms) ->
                val cached = pipelines.getValue(sameKeyPrograms.first().pipelineKey)
                IssuedGPUWgpu4kPreparedTextPipelineAcquisition(
                    pipeline = cached.pipeline,
                    drawBindGroupLayout = cached.drawBindGroupLayout,
                    materialBindGroupLayout = cached.materialBindGroupLayout,
                    atlasBindGroupLayout = cached.atlasBindGroupLayout,
                    coverageMaskBindGroupLayout =
                        cached.coverageMaskBindGroupLayout,
                    atlasSampler = cached.atlasSampler,
                    materialSamplersByResourceKey =
                        samplerAliasesByPipelineKey[sameKeyPrograms.first().pipelineKey].orEmpty(),
                )
            }
            GPUPreparedTextCacheBatchAcquire.Ready(
                Collections.unmodifiableMap(acquired.toMap()),
            )
        } catch (failure: Throwable) {
            createdSamplers.asReversed().forEach { (state, sampler) ->
                materialSamplers.remove(state)
                closeHandlesRetainingFailures(mutableListOf(sampler))
            }
            createdEntries.asReversed().forEach { (_, entry) ->
                closeHandlesRetainingFailures(entry.owned)
            }
            GPUPreparedTextCacheBatchAcquire.Refused(
                "failed.prepared_text.pipeline_materialization",
                "Prepared-text pipeline acquisition failed: " +
                    "${failure::class.simpleName.orEmpty()}: ${failure.message.orEmpty()}.",
            )
        }
    }

    @Synchronized
    fun invalidateForDeviceLoss() {
        close()
    }

    @Synchronized
    fun samplerCacheSnapshot(): GPUPreparedTextSamplerCacheSnapshot =
        GPUPreparedTextSamplerCacheSnapshot(
            residentEntryCount = materialSamplers.size,
            hitCount = materialSamplerHitCount,
            missCount = materialSamplerMissCount,
        )

    @Synchronized
    override fun close() {
        if (lifecycle == Lifecycle.Closed && retainedFailedSetup.isEmpty()) return
        lifecycle = Lifecycle.Closing
        val failures = mutableListOf<Throwable>()
        val retainedBeforeThisAttempt = retainedFailedSetup.toList()
        pipelines.values.toList().asReversed().forEach { entry ->
            closeHandles(entry.owned, failures)
        }
        closeHandles(materialSamplers.values.toMutableList(), failures)
        retainedBeforeThisAttempt.asReversed().forEach { handle ->
            try {
                handle.close()
                retainedFailedSetup.removeAll { it === handle }
            } catch (failure: Throwable) {
                failures += failure
            }
        }
        pipelines.clear()
        materialSamplers.clear()
        if (retainedFailedSetup.isEmpty()) lifecycle = Lifecycle.Closed
        if (retainedFailedSetup.isNotEmpty()) {
            throw GPUOwnedNativeCloseIncompleteException(
                ownerLabel = "prepared-text-session-cache",
                remainingOwnerCount = retainedFailedSetup.size,
                failures = failures,
            )
        }
    }

    private fun createPipeline(
        program: GPUPreparedTextNativeProgramHandoff,
    ): GPUWgpu4kPreparedTextCachedPipeline {
        val created = mutableListOf<AutoCloseable>()
        try {
            val drawLayout = device.createBindGroupLayout(
                BindGroupLayoutDescriptor(
                    label = "Kanvas.session.preparedText.drawLayout",
                    entries = listOf(
                        BindGroupLayoutEntry(
                            binding = program.drawUniformBinding.toUInt(),
                            visibility = GPUShaderStage.Vertex or GPUShaderStage.Fragment,
                            buffer = BufferBindingLayout(
                                type = GPUBufferBindingType.Uniform,
                                hasDynamicOffset = true,
                                minBindingSize = 80uL,
                            ),
                        ),
                    ),
                ),
            ).track(created)
            val materialEntries = buildList {
                program.materialUniformBinding?.let { binding ->
                    add(
                        BindGroupLayoutEntry(
                            binding = binding.binding.toUInt(),
                            visibility = GPUShaderStage.Fragment,
                            buffer = BufferBindingLayout(
                                type = GPUBufferBindingType.Uniform,
                                hasDynamicOffset = true,
                                minBindingSize = binding.minBindingSizeBytes.toULong(),
                            ),
                        ),
                    )
                }
                program.materialSampledBindings.forEach { binding ->
                    add(
                        BindGroupLayoutEntry(
                            binding = binding.textureBinding.toUInt(),
                            visibility = GPUShaderStage.Fragment,
                            texture = TextureBindingLayout(
                                sampleType = GPUTextureSampleType.Float,
                                viewDimension = GPUTextureViewDimension.TwoD,
                                multisampled = false,
                            ),
                        ),
                    )
                    add(
                        BindGroupLayoutEntry(
                            binding = binding.samplerBinding.toUInt(),
                            visibility = GPUShaderStage.Fragment,
                            sampler = SamplerBindingLayout(GPUSamplerBindingType.Filtering),
                        ),
                    )
                }
            }
            val materialLayout = device.createBindGroupLayout(
                BindGroupLayoutDescriptor(
                    label = "Kanvas.session.preparedText.materialLayout",
                    entries = materialEntries,
                ),
            ).track(created)
            val atlasLayout = device.createBindGroupLayout(
                BindGroupLayoutDescriptor(
                    label = "Kanvas.session.preparedText.atlasLayout",
                    entries = listOf(
                        BindGroupLayoutEntry(
                            binding = program.atlasTextureBinding.toUInt(),
                            visibility = GPUShaderStage.Fragment,
                            texture = TextureBindingLayout(
                                sampleType = GPUTextureSampleType.Float,
                                viewDimension = GPUTextureViewDimension.TwoD,
                                multisampled = false,
                            ),
                        ),
                        BindGroupLayoutEntry(
                            binding = program.atlasSamplerBinding.toUInt(),
                            visibility = GPUShaderStage.Fragment,
                            sampler = SamplerBindingLayout(GPUSamplerBindingType.Filtering),
                        ),
                    ),
                ),
            ).track(created)
            val coverageMaskLayout = program.coverageMaskTextureBinding?.let { binding ->
                device.createBindGroupLayout(
                    BindGroupLayoutDescriptor(
                        label = "Kanvas.session.preparedText.coverageMaskLayout",
                        entries = listOf(
                            BindGroupLayoutEntry(
                                binding = binding.toUInt(),
                                visibility = GPUShaderStage.Fragment,
                                texture = TextureBindingLayout(
                                    sampleType = GPUTextureSampleType.Float,
                                    viewDimension = GPUTextureViewDimension.TwoD,
                                    multisampled = false,
                                ),
                            ),
                        ),
                    ),
                ).track(created)
            }
            val shader = device.createShaderModule(
                ShaderModuleDescriptor(
                    label = "Kanvas.session.preparedText.shader.${program.pipelineKey}",
                    code = program.wgslSource,
                ),
            ).track(created)
            val pipelineLayout = device.createPipelineLayout(
                PipelineLayoutDescriptor(
                    label = "Kanvas.session.preparedText.pipelineLayout",
                    bindGroupLayouts = buildList {
                        add(drawLayout)
                        add(materialLayout)
                        add(atlasLayout)
                        coverageMaskLayout?.let(::add)
                    },
                ),
            ).track(created)
            val pipeline = device.createRenderPipeline(
                RenderPipelineDescriptor(
                    label = "Kanvas.session.preparedText.pipeline.${program.pipelineKey}",
                    layout = pipelineLayout,
                    vertex = VertexState(
                        module = shader,
                        entryPoint = program.vertexEntryPoint,
                        buffers = listOf(
                            VertexBufferLayout(
                                arrayStride = 64uL,
                                stepMode = GPUVertexStepMode.Instance,
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
                                    VertexAttribute(
                                        shaderLocation = 3u,
                                        offset = 24uL,
                                        format = GPUVertexFormat.Float32x2,
                                    ),
                                    VertexAttribute(
                                        shaderLocation = 4u,
                                        offset = 32uL,
                                        format = GPUVertexFormat.Float32x4,
                                    ),
                                ),
                            ),
                        ),
                    ),
                    primitive = PrimitiveState(),
                    fragment = FragmentState(
                        module = shader,
                        entryPoint = program.fragmentEntryPoint,
                        targets = listOf(
                            ColorTargetState(
                                format = program.targetFormatClass.toPreparedTextTargetFormat(),
                                blend = program.fixedFunctionBlendState
                                    .toPreparedTextBlendState(),
                                writeMask = program.fixedFunctionBlendState
                                    .toPreparedTextWriteMask(),
                            ),
                        ),
                    ),
                ),
            ).track(created)
            val atlasSampler = createSampler(
                samplerState("nearest"),
                "atlas",
            ).track(created)
            return GPUWgpu4kPreparedTextCachedPipeline(
                program = program,
                shader = shader,
                drawBindGroupLayout = drawLayout,
                materialBindGroupLayout = materialLayout,
                atlasBindGroupLayout = atlasLayout,
                coverageMaskBindGroupLayout = coverageMaskLayout,
                pipelineLayout = pipelineLayout,
                pipeline = pipeline,
                atlasSampler = atlasSampler,
                owned = created,
            )
        } catch (failure: Throwable) {
            closeHandlesRetainingFailures(created)
            throw failure
        }
    }

    private fun resolveMaterialSamplers(
        resources: List<GPUMaterialTextureFrameResourcePlan>,
        createdSamplers:
            MutableList<Pair<GPUPreparedTextSamplerState, GPUSampler>>,
    ): Map<String, GPUSampler> {
        val filtersByResourceKey = linkedMapOf<String, String>()
        val aliases = linkedMapOf<String, GPUSampler>()
        resources.forEach { resource ->
            val previousFilter = filtersByResourceKey.put(
                resource.resourceKey,
                resource.samplingFilterMode,
            )
            require(previousFilter == null || previousFilter == resource.samplingFilterMode) {
                "Prepared-text material resource ${resource.resourceKey} changed sampler filter"
            }
            val state = samplerState(resource.samplingFilterMode)
            val sampler = materialSamplers[state]?.also {
                materialSamplerHitCount += 1
            } ?: createSampler(
                state,
                "material.${state.magFilter.name.lowercase()}",
            ).also { created ->
                materialSamplerMissCount += 1
                materialSamplers[state] = created
                createdSamplers += state to created
            }
            aliases[resource.resourceKey] = sampler
        }
        return aliases
    }

    private fun samplerState(filter: String): GPUPreparedTextSamplerState {
        val nativeFilter = when (filter) {
            "nearest" -> GPUFilterMode.Nearest
            "linear" -> GPUFilterMode.Linear
            else -> error("Unsupported prepared-text sampler filter $filter")
        }
        return GPUPreparedTextSamplerState(
            addressModeU = GPUAddressMode.ClampToEdge,
            addressModeV = GPUAddressMode.ClampToEdge,
            addressModeW = GPUAddressMode.ClampToEdge,
            magFilter = nativeFilter,
            minFilter = nativeFilter,
            mipmapFilter = GPUMipmapFilterMode.Nearest,
            lodMinClampBits = 0f.toRawBits(),
            lodMaxClampBits = 0f.toRawBits(),
            compare = null,
            maxAnisotropy = 1u.toUShort(),
        )
    }

    private fun createSampler(
        state: GPUPreparedTextSamplerState,
        role: String,
    ): GPUSampler {
        return device.createSampler(
            SamplerDescriptor(
                addressModeU = state.addressModeU,
                addressModeV = state.addressModeV,
                addressModeW = state.addressModeW,
                magFilter = state.magFilter,
                minFilter = state.minFilter,
                mipmapFilter = state.mipmapFilter,
                lodMinClamp = Float.fromBits(state.lodMinClampBits),
                lodMaxClamp = Float.fromBits(state.lodMaxClampBits),
                compare = null,
                maxAnisotropy = state.maxAnisotropy,
                label = "Kanvas.session.preparedText.$role",
            ),
        )
    }

    private fun closeHandlesRetainingFailures(handles: MutableList<AutoCloseable>) {
        closeHandles(handles, mutableListOf())
    }

    private fun closeHandles(
        handles: MutableList<AutoCloseable>,
        failures: MutableList<Throwable>,
    ) {
        for (index in handles.lastIndex downTo 0) {
            val handle = handles[index]
            try {
                handle.close()
                handles.removeAt(index)
            } catch (failure: Throwable) {
                failures += failure
                if (retainedFailedSetup.none { it === handle }) retainedFailedSetup += handle
                handles.removeAt(index)
            }
        }
    }

    private fun <T : AutoCloseable> T.track(handles: MutableList<AutoCloseable>): T =
        also(handles::add)
}

private fun GPUPreparedTextNativeProgramHandoff.sameProgramAs(
    other: GPUPreparedTextNativeProgramHandoff,
): Boolean = wgslSource == other.wgslSource &&
    vertexEntryPoint == other.vertexEntryPoint &&
    fragmentEntryPoint == other.fragmentEntryPoint &&
    abiHash == other.abiHash &&
    sourceHash == other.sourceHash &&
    targetFormatClass == other.targetFormatClass &&
    blendPlanIdentity == other.blendPlanIdentity &&
    fixedFunctionBlendState == other.fixedFunctionBlendState &&
    sourceCoverageEncoding == other.sourceCoverageEncoding &&
    clipVariant == other.clipVariant &&
    drawUniformBinding == other.drawUniformBinding &&
    materialUniformBinding == other.materialUniformBinding &&
    materialSampledBindings == other.materialSampledBindings &&
    atlasTextureBinding == other.atlasTextureBinding &&
    atlasSamplerBinding == other.atlasSamplerBinding &&
    vertexLayout == other.vertexLayout &&
    pipelineKey == other.pipelineKey

private fun String.toPreparedTextTargetFormat(): GPUTextureFormat = when (this) {
    "rgba8unorm" -> GPUTextureFormat.RGBA8Unorm
    "rgba8unorm-srgb" -> GPUTextureFormat.RGBA8UnormSrgb
    "bgra8unorm" -> GPUTextureFormat.BGRA8Unorm
    else -> error("Unsupported prepared-text target format: $this")
}

private fun GPUFixedFunctionBlendState.toPreparedTextBlendState(): BlendState = BlendState(
    color = BlendComponent(
        operation = color.operation.toPreparedTextBlendOperation(),
        srcFactor = color.sourceFactor.toPreparedTextBlendFactor(),
        dstFactor = color.destinationFactor.toPreparedTextBlendFactor(),
    ),
    alpha = BlendComponent(
        operation = alpha.operation.toPreparedTextBlendOperation(),
        srcFactor = alpha.sourceFactor.toPreparedTextBlendFactor(),
        dstFactor = alpha.destinationFactor.toPreparedTextBlendFactor(),
    ),
)

private fun String.toPreparedTextBlendFactor(): GPUBlendFactor = when (this) {
    "zero" -> GPUBlendFactor.Zero
    "one" -> GPUBlendFactor.One
    "src" -> GPUBlendFactor.Src
    "one-minus-src" -> GPUBlendFactor.OneMinusSrc
    "dst" -> GPUBlendFactor.Dst
    "one-minus-dst" -> GPUBlendFactor.OneMinusDst
    "src-alpha" -> GPUBlendFactor.SrcAlpha
    "one-minus-src-alpha" -> GPUBlendFactor.OneMinusSrcAlpha
    "dst-alpha" -> GPUBlendFactor.DstAlpha
    "one-minus-dst-alpha" -> GPUBlendFactor.OneMinusDstAlpha
    "src-alpha-saturated" -> GPUBlendFactor.SrcAlphaSaturated
    "constant" -> GPUBlendFactor.Constant
    "one-minus-constant" -> GPUBlendFactor.OneMinusConstant
    else -> error("Unsupported prepared-text fixed-function blend factor: $this")
}

private fun String.toPreparedTextBlendOperation(): GPUBlendOperation = when (this) {
    "add" -> GPUBlendOperation.Add
    "reverse-subtract" -> GPUBlendOperation.ReverseSubtract
    else -> error("Unsupported prepared-text fixed-function blend operation: $this")
}

private fun GPUFixedFunctionBlendState.toPreparedTextWriteMask(): GPUColorWrite = when (writeMask) {
    "rgba" -> GPUColorWrite.All
    "none" -> GPUColorWrite.None
    else -> error("Unsupported prepared-text write mask: $writeMask")
}
