package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.BindGroupDescriptor
import io.ygdrasil.webgpu.BindGroupEntry
import io.ygdrasil.webgpu.BufferBinding
import io.ygdrasil.webgpu.BufferDescriptor
import io.ygdrasil.webgpu.Extent3D
import io.ygdrasil.webgpu.GPUBindGroup
import io.ygdrasil.webgpu.GPUBuffer
import io.ygdrasil.webgpu.GPUBufferUsage
import io.ygdrasil.webgpu.GPUDevice
import io.ygdrasil.webgpu.GPUTexture
import io.ygdrasil.webgpu.GPUTextureAspect
import io.ygdrasil.webgpu.GPUTextureFormat
import io.ygdrasil.webgpu.GPUTextureUsage
import io.ygdrasil.webgpu.GPUTextureView
import io.ygdrasil.webgpu.GPUTextureViewDimension
import io.ygdrasil.webgpu.TextureDescriptor
import io.ygdrasil.webgpu.TextureViewDescriptor
import java.util.Collections
import java.util.IdentityHashMap
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.collections.immutableList
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.recording.GPUPreparedTextDrawUniformBufferPlan
import org.graphiks.kanvas.gpu.renderer.recording.GPUPreparedTextInstanceBufferPlan
import org.graphiks.kanvas.gpu.renderer.recording.GPUPreparedTextMaterialUniformBufferPlan
import org.graphiks.kanvas.gpu.renderer.recording.GPUPreparedTextRenderBinding
import org.graphiks.kanvas.gpu.renderer.resources.GPUMaterialTextureFrameResourcePlan
import org.graphiks.kanvas.gpu.renderer.resources.GPUPreparedTextureUploadLayout
import org.graphiks.kanvas.gpu.renderer.resources.GPUR8FrameResourcePlan

internal sealed interface GPUPreparedTextTextureUploadPlan {
    val exactScopeKey: GPUPreparedNativeScopeKey

    data class Atlas(
        override val exactScopeKey: GPUPreparedNativeScopeKey,
        val resourcePlan: GPUR8FrameResourcePlan,
    ) : GPUPreparedTextTextureUploadPlan

    data class Material(
        override val exactScopeKey: GPUPreparedNativeScopeKey,
        val resourcePlan: GPUMaterialTextureFrameResourcePlan,
    ) : GPUPreparedTextTextureUploadPlan
}

internal data class GPUPreparedTextRenderRunPlan(
    val sourceScopeIndices: List<Int>,
    val packets: List<GPUDrawSemanticPayload.TextA8>,
    val bindings: List<GPUPreparedTextRenderBinding>,
    val exactScopeKeys: List<GPUPreparedNativeScopeKey>,
    val textureUploads: List<GPUPreparedTextTextureUploadPlan>,
) {
    init {
        require(packets.isNotEmpty() && packets.size == bindings.size)
        require(sourceScopeIndices == exactScopeKeys.map { it.sourceStepIndex })
        require(sourceScopeIndices.distinct().size == sourceScopeIndices.size)
        require(bindings.map { it.packetId }.distinct().size == bindings.size)
        require(bindings.zip(packets).all { (binding, semantic) ->
            binding.compositeProgram.pipelineKey.isNotBlank() &&
                binding.preflightSeal.semanticCanonicalHash == semantic.canonicalHash
        })
        val uploadKeys = exactScopeKeys.filter {
            it.operationKind == GPUEncoderOperationKind.Upload
        }
        require(textureUploads.map { it.exactScopeKey }.toSet() == uploadKeys.toSet())
        require(textureUploads.map { it.exactScopeKey.sourceStepIndex }.distinct().size ==
            textureUploads.size
        )
        val requiredAtlases = bindings
            .map(GPUPreparedTextRenderBinding::atlasResourcePlan)
            .distinctByIdentity()
        val suppliedAtlases = textureUploads
            .filterIsInstance<GPUPreparedTextTextureUploadPlan.Atlas>()
            .map(GPUPreparedTextTextureUploadPlan.Atlas::resourcePlan)
            .distinctByIdentity()
        require(requiredAtlases.size == suppliedAtlases.size &&
            requiredAtlases.all { required -> suppliedAtlases.any { it === required } }
        )
        val requiredMaterials = bindings
            .flatMap(GPUPreparedTextRenderBinding::materialSampledResourcePlans)
            .map(GPUMaterialTextureFrameResourcePlan::resourceKey)
            .toSet()
        val suppliedMaterials = textureUploads
            .filterIsInstance<GPUPreparedTextTextureUploadPlan.Material>()
            .map { it.resourcePlan.resourceKey }
            .toSet()
        require(requiredMaterials == suppliedMaterials)
    }
}

/**
 * Materializes one accepted TextA8 lot into frame-local resources plus target-free ordered runs.
 */
internal class GPUWgpu4kPreparedTextRenderRunMaterializer(
    private val device: GPUDevice,
    private val sessionCache: GPUWgpu4kPreparedTextSessionCache,
) {
    fun materializeAcceptedRun(
        plan: GPUPreparedTextRenderRunPlan,
        actualDeviceGeneration: GPUDeviceGenerationID,
    ): GPUPreparedRenderRunMaterialization {
        val acquisitions = when (
            val result = sessionCache.acquireBatch(
                plan.bindings.map(GPUPreparedTextRenderBinding::nativeProgram),
                actualDeviceGeneration,
                plan.bindings.groupBy(
                    keySelector = { it.nativeProgram.pipelineKey },
                    valueTransform = { it.materialSampledResourcePlans },
                ).mapValues { (_, resourceLists) ->
                    resourceLists.flatten().distinctBy { it.resourceKey }
                },
            )
        ) {
            is GPUPreparedTextCacheBatchAcquire.Ready -> result.pipelinesByKey
            is GPUPreparedTextCacheBatchAcquire.Refused ->
                return GPUPreparedRenderRunMaterialization.Refused(
                    code = result.code,
                    message = result.message,
                    facts = mapOf("boundary" to "native"),
                )
        }

        val created = mutableListOf<AutoCloseable>()
        return try {
            val renderScopeKeys = plan.exactScopeKeys.filter {
                it.operationKind == GPUEncoderOperationKind.Render
            }
            require(renderScopeKeys.size == plan.packets.size)

            val instancePlan = plan.bindings.map(GPUPreparedTextRenderBinding::instanceBufferPlan)
                .singleIdentity("instance buffer")
            val drawUniformPlan = plan.bindings.map(
                GPUPreparedTextRenderBinding::drawUniformBufferPlan,
            ).singleIdentity("draw-uniform buffer")
            val materialUniformPlan = plan.bindings
                .mapNotNull(GPUPreparedTextRenderBinding::materialUniformBufferPlan)
                .singleIdentityOrNull("material-uniform buffer")

            val instanceBuffer = createVertexBuffer(
                instancePlan.byteSize,
                "Kanvas.frame.preparedText.instances",
            ).track(created)
            val drawUniformBuffer = createUniformBuffer(
                drawUniformPlan.byteSize,
                "Kanvas.frame.preparedText.draw-uniforms",
            ).track(created)
            val materialUniformBuffer = materialUniformPlan?.let { uniformPlan ->
                createUniformBuffer(
                    uniformPlan.byteSize,
                    "Kanvas.frame.preparedText.material-uniforms",
                ).track(created)
            }
            val instanceOperand = GPUPreparedNativeBufferOperand(
                instanceBuffer,
                actualDeviceGeneration,
                GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion,
                instancePlan.byteSize,
            )
            val drawUniformOperand = GPUPreparedNativeBufferOperand(
                drawUniformBuffer,
                actualDeviceGeneration,
                GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion,
                drawUniformPlan.byteSize,
            )
            val materialUniformOperand = materialUniformBuffer?.let { buffer ->
                GPUPreparedNativeBufferOperand(
                    buffer,
                    actualDeviceGeneration,
                    GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion,
                    requireNotNull(materialUniformPlan).byteSize,
                )
            }
            val renderScopeIndices = renderScopeKeys.map { it.sourceStepIndex }
            val bufferUploads = buildList {
                add(
                    preparedTextBufferUpload(
                        role = "instances",
                        bytes = instancePlan.bytesForUpload(),
                        destination = instanceOperand,
                        destinationLabel = instancePlan.bufferRef.value,
                        renderScopeIndices = renderScopeIndices,
                    ),
                )
                add(
                    preparedTextBufferUpload(
                        role = "draw-uniforms",
                        bytes = drawUniformPlan.bytesForUpload(),
                        destination = drawUniformOperand,
                        destinationLabel = drawUniformPlan.bufferRef.value,
                        renderScopeIndices = renderScopeIndices,
                    ),
                )
                if (materialUniformPlan != null) {
                    add(
                        preparedTextBufferUpload(
                            role = "material-uniforms",
                            bytes = materialUniformPlan.bytesForUpload(),
                            destination = requireNotNull(materialUniformOperand),
                            destinationLabel = materialUniformPlan.bufferRef.value,
                            renderScopeIndices = renderScopeIndices,
                        ),
                    )
                }
            }

            val atlasNative = IdentityHashMap<GPUR8FrameResourcePlan, NativeTexture>()
            val materialNative = linkedMapOf<String, NativeTexture>()
            val textureUploads = mutableListOf<GPUPreparedNativeScopeOperand.TextureUpload>()
            plan.textureUploads.forEach { upload ->
                when (upload) {
                    is GPUPreparedTextTextureUploadPlan.Atlas -> {
                        val atlasPlan = upload.resourcePlan
                        val texture = createTexture(
                            width = atlasPlan.artifactWidth,
                            height = atlasPlan.artifactHeight,
                            format = GPUTextureFormat.R8Unorm,
                            label = "Kanvas.frame.preparedText.text-atlas",
                        ).track(created)
                        val view = createView(
                            texture,
                            GPUTextureFormat.R8Unorm,
                            "Kanvas.frame.preparedText.text-atlas-view",
                        ).track(created)
                        atlasNative[atlasPlan] = NativeTexture(texture, view)
                        textureUploads += preparedTextTextureUpload(
                            role = "text-atlas",
                            scope = upload.exactScopeKey,
                            bytes = atlasPlan.bytesForUpload(),
                            width = atlasPlan.artifactWidth,
                            height = atlasPlan.artifactHeight,
                            bytesPerRow = atlasPlan.uploadTaskLayout.bytesPerRow,
                            rowsPerImage = atlasPlan.uploadTaskLayout.rowsPerImage,
                            texture = texture,
                            generation = actualDeviceGeneration,
                        )
                    }
                    is GPUPreparedTextTextureUploadPlan.Material -> {
                        val resource = upload.resourcePlan
                        val format = if (resource.alphaOnly) {
                            GPUTextureFormat.RGBA8Unorm
                        } else {
                            GPUTextureFormat.RGBA8UnormSrgb
                        }
                        val texture = createTexture(
                            resource.width,
                            resource.height,
                            format,
                            "Kanvas.frame.preparedText.material-texture.${resource.resourceKey}",
                        ).track(created)
                        val view = createView(
                            texture,
                            format,
                            "Kanvas.frame.preparedText.material-texture-view.${resource.resourceKey}",
                        ).track(created)
                        materialNative[resource.resourceKey] = NativeTexture(texture, view)
                        textureUploads += preparedTextTextureUpload(
                            role = "material:${resource.resourceKey}",
                            scope = upload.exactScopeKey,
                            bytes = resource.bytesForUpload(),
                            width = resource.width,
                            height = resource.height,
                            bytesPerRow = resource.uploadTaskLayout.bytesPerRow,
                            rowsPerImage = resource.uploadTaskLayout.rowsPerImage,
                            texture = texture,
                            generation = actualDeviceGeneration,
                        )
                    }
                }
            }

            val renderRuns = plan.packets.indices.map { index ->
                val packet = plan.packets[index]
                val binding = plan.bindings[index]
                val acquisition = acquisitions.getValue(binding.nativeProgram.pipelineKey)
                val drawGroup = createDrawGroup(
                    acquisition,
                    drawUniformBuffer,
                    binding.drawUniformSlice.sizeBytes,
                ).track(created)
                val materialGroup = createMaterialGroup(
                    binding,
                    acquisition,
                    materialUniformBuffer,
                    materialNative,
                ).track(created)
                val atlas = atlasNative.getValue(binding.atlasResourcePlan)
                val atlasGroup = createAtlasGroup(acquisition, atlas.view).track(created)
                val commands = listOf(
                    GPUPreparedNativeRenderCommand.SetPipeline(
                        GPUPreparedNativeRenderPipelineOperand.fromPreparedTextAcquisition(
                            acquisition,
                            actualDeviceGeneration,
                        ),
                    ),
                    GPUPreparedNativeRenderCommand.SetBindGroup(
                        0,
                        GPUPreparedNativeBindGroupOperand(
                            drawGroup,
                            actualDeviceGeneration,
                            GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion,
                        ),
                        dynamicOffsets = listOf(binding.drawUniformSlice.offsetBytes),
                    ),
                    GPUPreparedNativeRenderCommand.SetBindGroup(
                        1,
                        GPUPreparedNativeBindGroupOperand(
                            materialGroup,
                            actualDeviceGeneration,
                            GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion,
                        ),
                        dynamicOffsets = if (binding.materialUniformBufferPlan == null) {
                            emptyList()
                        } else {
                            listOf(binding.materialUniformOffsetBytes)
                        },
                    ),
                    GPUPreparedNativeRenderCommand.SetBindGroup(
                        2,
                        GPUPreparedNativeBindGroupOperand(
                            atlasGroup,
                            actualDeviceGeneration,
                            GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion,
                        ),
                    ),
                    GPUPreparedNativeRenderCommand.SetVertexBuffer(
                        slot = 0,
                        buffer = instanceOperand,
                        offset = 0L,
                        size = requireNotNull(instanceOperand.byteCapacity),
                        vertexStrideBytes = 64L,
                    ),
                    GPUPreparedNativeRenderCommand.SetScissor(
                        packet.scissorBounds.left,
                        packet.scissorBounds.top,
                        packet.scissorBounds.width,
                        packet.scissorBounds.height,
                    ),
                    GPUPreparedNativeRenderCommand.Draw(
                        GPUPreparedNativeDrawCall.Draw(
                            vertexCount = 6,
                            instanceCount = binding.instanceCount,
                            firstVertex = 0,
                            firstInstance = binding.firstInstance,
                        ),
                    ),
                )
                GPUPreparedNativeScopeOperand.PreparedTextRenderRun(
                    sourceStepIndex = renderScopeKeys[index].sourceStepIndex,
                    commands = commands,
                    exactOperandKeys = renderScopeKeys[index].operandKeys,
                    semanticPayloads = listOf(packet),
                )
            }
            val operandsByStep = (textureUploads + renderRuns).associateBy {
                it.sourceStepIndex
            }
            val ordered = plan.exactScopeKeys.map { key ->
                operandsByStep.getValue(key.sourceStepIndex)
            }
            val owner = GPUPreparedRenderRunOwnedResources(created)
            created.clear()
            GPUPreparedRenderRunMaterialization.Ready(
                scopeOperands = immutableList(ordered),
                uniformUploads = immutableList(bufferUploads),
                ownedResources = immutableList(listOf(owner)),
            )
        } catch (failure: Throwable) {
            failedPreparedTextMaterialization(created, failure)
        }
    }

    private fun createVertexBuffer(
        size: Long,
        label: String,
    ): GPUBuffer = device.createBuffer(
        BufferDescriptor(
            size = size.toULong(),
            usage = GPUBufferUsage.Vertex or GPUBufferUsage.CopyDst,
            mappedAtCreation = false,
            label = label,
        ),
    )

    private fun createUniformBuffer(
        size: Long,
        label: String,
    ): GPUBuffer = device.createBuffer(
        BufferDescriptor(
            size = size.toULong(),
            usage = GPUBufferUsage.Uniform or GPUBufferUsage.CopyDst,
            mappedAtCreation = false,
            label = label,
        ),
    )

    private fun createTexture(
        width: Int,
        height: Int,
        format: GPUTextureFormat,
        label: String,
    ): GPUTexture = device.createTexture(
        TextureDescriptor(
            size = Extent3D(width.toUInt(), height.toUInt(), 1u),
            format = format,
            usage = GPUTextureUsage.CopyDst or GPUTextureUsage.TextureBinding,
            mipLevelCount = 1u,
            sampleCount = 1u,
            label = label,
        ),
    )

    private fun createView(
        texture: GPUTexture,
        format: GPUTextureFormat,
        label: String,
    ): GPUTextureView = texture.createView(
        TextureViewDescriptor(
            format = format,
            dimension = GPUTextureViewDimension.TwoD,
            usage = GPUTextureUsage.TextureBinding,
            aspect = GPUTextureAspect.All,
            baseMipLevel = 0u,
            mipLevelCount = 1u,
            baseArrayLayer = 0u,
            arrayLayerCount = 1u,
            label = label,
        ),
    )

    private fun createDrawGroup(
        acquisition: GPUWgpu4kPreparedTextPipelineAcquisition,
        drawUniformBuffer: GPUBuffer,
        logicalSize: Long,
    ): GPUBindGroup = device.createBindGroup(
        BindGroupDescriptor(
            label = "Kanvas.frame.preparedText.draw-group",
            layout = acquisition.drawBindGroupLayout,
            entries = listOf(
                BindGroupEntry(
                    binding = 0u,
                    resource = BufferBinding(
                        buffer = drawUniformBuffer,
                        offset = 0uL,
                        size = logicalSize.toULong(),
                    ),
                ),
            ),
        ),
    )

    private fun createMaterialGroup(
        binding: GPUPreparedTextRenderBinding,
        acquisition: GPUWgpu4kPreparedTextPipelineAcquisition,
        materialUniformBuffer: GPUBuffer?,
        materialNative: Map<String, NativeTexture>,
    ): GPUBindGroup {
        val program = binding.nativeProgram
        val entries = buildList {
            program.materialUniformBinding?.let { uniform ->
                add(
                    BindGroupEntry(
                        binding = uniform.binding.toUInt(),
                        resource = BufferBinding(
                            buffer = requireNotNull(materialUniformBuffer),
                            offset = 0uL,
                            size = uniform.minBindingSizeBytes.toULong(),
                        ),
                    ),
                )
            }
            program.materialSampledBindings.zip(binding.materialSampledResourcePlans)
                .forEach { (sampled, resource) ->
                    add(
                        BindGroupEntry(
                            binding = sampled.textureBinding.toUInt(),
                            resource = materialNative.getValue(resource.resourceKey).view,
                        ),
                    )
                    val sampler =
                        acquisition.materialSamplersByResourceKey.getValue(resource.resourceKey)
                    add(
                        BindGroupEntry(
                            binding = sampled.samplerBinding.toUInt(),
                            resource = sampler,
                        ),
                    )
                }
        }
        return device.createBindGroup(
            BindGroupDescriptor(
                label = "Kanvas.frame.preparedText.material-group",
                layout = acquisition.materialBindGroupLayout,
                entries = entries,
            ),
        )
    }

    private fun createAtlasGroup(
        acquisition: GPUWgpu4kPreparedTextPipelineAcquisition,
        atlasView: GPUTextureView,
    ): GPUBindGroup = device.createBindGroup(
        BindGroupDescriptor(
            label = "Kanvas.frame.preparedText.atlas-group",
            layout = acquisition.atlasBindGroupLayout,
            entries = listOf(
                BindGroupEntry(0u, atlasView),
                BindGroupEntry(1u, acquisition.atlasSampler),
            ),
        ),
    )
}

private class PreparedTextTextureUploadLayout(
    override val bytesPerRow: Long,
    override val rowsPerImage: Int,
    override val width: Int,
    override val height: Int,
    bytes: ByteArray,
) : GPUPreparedTextureUploadLayout {
    private val snapshot = bytes.copyOf()

    init {
        require(width > 0 && height > 0 && rowsPerImage == height)
        require(snapshot.size.toLong() == Math.multiplyExact(bytesPerRow, height.toLong()))
    }

    override fun bytesForUpload(): ByteArray = snapshot.copyOf()
}

private data class NativeTexture(
    val texture: GPUTexture,
    val view: GPUTextureView,
)

private fun preparedTextTextureUpload(
    role: String,
    scope: GPUPreparedNativeScopeKey,
    bytes: ByteArray,
    width: Int,
    height: Int,
    bytesPerRow: Long,
    rowsPerImage: Int,
    texture: GPUTexture,
    generation: GPUDeviceGenerationID,
): GPUPreparedNativeScopeOperand.TextureUpload {
    require(scope.operandKeys.size == 2)
    val layout = PreparedTextTextureUploadLayout(
        bytesPerRow,
        rowsPerImage,
        width,
        height,
        bytes,
    )
    return GPUPreparedNativeScopeOperand.TextureUpload(
        sourceStepIndex = scope.sourceStepIndex,
        data = GPUPreparedNativeUploadData(scope.operandKeys[0], bytes),
        destination = GPUPreparedNativeTextureOperand(
            texture,
            generation,
            GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion,
        ),
        destinationKey = scope.operandKeys[1],
        layout = layout,
        uploadRole = role,
    )
}

private fun preparedTextBufferUpload(
    role: String,
    bytes: ByteArray,
    destination: GPUPreparedNativeBufferOperand,
    destinationLabel: String,
    renderScopeIndices: List<Int>,
): GPUPreparedNativeBufferUpload = GPUPreparedNativeBufferUpload(
    data = GPUPreparedNativeUploadData(
        GPUPreparedNativeOperandKey(
            GPUPreparedNativeOperandRole.UploadSource,
            GPUPreparedNativeOperandKind.Buffer,
            gpuPreparedNativeBindingKey("prepared-text-upload-data:$destinationLabel"),
        ),
        bytes,
    ),
    destination = destination,
    destinationKey = GPUPreparedNativeOperandKey(
        GPUPreparedNativeOperandRole.UploadDestination,
        GPUPreparedNativeOperandKind.Buffer,
        gpuPreparedNativeBindingKey("prepared-text-buffer:$destinationLabel"),
    ),
    destinationOffset = 0L,
    consumerSourceStepIndices = renderScopeIndices,
    uploadRole = role,
)

private fun <T : Any> List<T>.distinctByIdentity(): List<T> {
    val identities = Collections.newSetFromMap(IdentityHashMap<T, Boolean>())
    return filter(identities::add)
}

private fun <T : Any> List<T>.singleIdentity(label: String): T {
    val distinct = distinctByIdentity()
    require(distinct.size == 1) { "Prepared-text $label must be one frame-global plan" }
    return distinct.single()
}

private fun <T : Any> List<T>.singleIdentityOrNull(label: String): T? {
    if (isEmpty()) return null
    return singleIdentity(label)
}

private fun <T : AutoCloseable> T.track(handles: MutableList<AutoCloseable>): T =
    also(handles::add)

private fun failedPreparedTextMaterialization(
    handles: MutableList<AutoCloseable>,
    failure: Throwable,
): GPUPreparedRenderRunMaterialization.Refused {
    val owner = GPUPreparedRenderRunOwnedResources(handles)
    handles.clear()
    val rollbackFailure = runCatching(owner::close).exceptionOrNull()
    return GPUPreparedRenderRunMaterialization.Refused(
        code = "failed.prepared_text.materialization",
        message = "Prepared-text native materialization failed: " +
            "${failure::class.simpleName.orEmpty()}: ${failure.message.orEmpty()}.",
        facts = mapOf("boundary" to "native"),
        retainedCloseOwner = owner.takeIf { rollbackFailure != null },
    )
}
