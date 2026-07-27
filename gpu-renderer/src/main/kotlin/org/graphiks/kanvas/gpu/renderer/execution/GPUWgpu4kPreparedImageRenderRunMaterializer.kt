package org.graphiks.kanvas.gpu.renderer.execution

import java.security.MessageDigest
import java.util.IdentityHashMap
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.collections.immutableList
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUPreparedImageRefusalCodes
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole
import org.graphiks.kanvas.gpu.renderer.resources.GPUImageFrameResourcePlan
import org.graphiks.kanvas.gpu.renderer.resources.GPUPreparedImageUniformAllocation
import org.graphiks.kanvas.gpu.renderer.resources.GPU_PREPARED_IMAGE_UNIFORM_ALLOCATION_SIZE_BYTES

private data class GPUPreparedImageRenderRunPlanSnapshot(
    val sourceScopeIndices: List<Int>,
    val packets: List<GPUDrawSemanticPayload.SampledImage>,
    val resources: List<GPUImageFrameResourcePlan>,
    val uniformAllocations: List<GPUPreparedImageUniformAllocation>,
    val exactScopeKeys: List<GPUPreparedNativeScopeKey>,
)

@ConsistentCopyVisibility
internal data class GPUPreparedImageRenderRunPlan private constructor(
    private val snapshot: GPUPreparedImageRenderRunPlanSnapshot,
) {
    constructor(
        sourceScopeIndices: List<Int>,
        packets: List<GPUDrawSemanticPayload.SampledImage>,
        resources: List<GPUImageFrameResourcePlan>,
        uniformAllocations: List<GPUPreparedImageUniformAllocation>,
        exactScopeKeys: List<GPUPreparedNativeScopeKey>,
    ) : this(
        GPUPreparedImageRenderRunPlanSnapshot(
            sourceScopeIndices = immutableList(sourceScopeIndices),
            packets = immutableList(packets),
            resources = immutableList(resources),
            uniformAllocations = immutableList(uniformAllocations),
            exactScopeKeys = immutableList(exactScopeKeys),
        ),
    )

    val sourceScopeIndices get() = snapshot.sourceScopeIndices
    val packets get() = snapshot.packets
    val resources get() = snapshot.resources
    val uniformAllocations get() = snapshot.uniformAllocations
    val exactScopeKeys get() = snapshot.exactScopeKeys

    init {
        require(this.packets.isNotEmpty() && this.resources.isNotEmpty())
        require(this.packets.size == this.uniformAllocations.size)
        require(this.sourceScopeIndices.size == this.resources.size + 1)
        require(this.sourceScopeIndices.distinct().size == this.sourceScopeIndices.size &&
            this.sourceScopeIndices.all { it >= 0 }
        )
        require(this.exactScopeKeys.map(GPUPreparedNativeScopeKey::sourceStepIndex) ==
            this.sourceScopeIndices
        ) { "Prepared-image exact scope keys must match the accepted source scopes in order" }
        require(this.exactScopeKeys.map(GPUPreparedNativeScopeKey::operationKind) ==
            List(this.resources.size) { GPUEncoderOperationKind.Upload } +
            GPUEncoderOperationKind.Render
        ) { "Prepared-image exact scope keys must retain uploads plus one render run" }
        require(this.exactScopeKeys.all { it.operandKeys.isNotEmpty() }) {
            "Prepared-image exact scope keys must retain preflight operand authority"
        }
    }

    fun copy(
        sourceScopeIndices: List<Int> = this.sourceScopeIndices,
        packets: List<GPUDrawSemanticPayload.SampledImage> = this.packets,
        resources: List<GPUImageFrameResourcePlan> = this.resources,
        uniformAllocations: List<GPUPreparedImageUniformAllocation> = this.uniformAllocations,
        exactScopeKeys: List<GPUPreparedNativeScopeKey> = this.exactScopeKeys,
    ): GPUPreparedImageRenderRunPlan = GPUPreparedImageRenderRunPlan(
        sourceScopeIndices,
        packets,
        resources,
        uniformAllocations,
        exactScopeKeys,
    )
}

internal sealed interface GPUPreparedRenderRunMaterialization {
    data class Ready(
        val scopeOperands: List<GPUPreparedNativeScopeOperand>,
        val uniformUploads: List<GPUPreparedNativeBufferUpload>,
        val ownedResources: List<AutoCloseable>,
    ) : GPUPreparedRenderRunMaterialization

    data class Refused(
        val code: String,
        val message: String,
        val facts: Map<String, String> = emptyMap(),
        val retainedCloseOwner: AutoCloseable? = null,
    ) :
        GPUPreparedRenderRunMaterialization {
        init {
            require(code.isNotBlank() && message.isNotBlank())
        }
    }
}

/**
 * Materializes only image-owned operands. It cannot acquire a scene target, create a native frame
 * draft, register a payload, or select a product route.
 */
internal class GPUWgpu4kPreparedImageRenderRunMaterializer(
    private val sessionCache: GPUWgpu4kPreparedImageSessionCache,
    private val handleFactory: GPUPreparedImageNativeHandleFactory,
) {
    fun materializeAcceptedRun(
        plan: GPUPreparedImageRenderRunPlan,
        actualDeviceGeneration: GPUDeviceGenerationID,
    ): GPUPreparedRenderRunMaterialization {
        GPUPreparedImagePlanValidator.validatePlan(plan)?.let {
            return GPUPreparedRenderRunMaterialization.Refused(
                code = it.first,
                message = it.second,
                facts = mapOf("boundary" to "native"),
            )
        }
        val pipelineByKey = when (
            val acquired = sessionCache.acquireBatch(
                plan.packets.map(GPUDrawSemanticPayload.SampledImage::pipelineKey),
                actualDeviceGeneration,
            )
        ) {
            is GPUPreparedImageCacheBatchAcquire.Ready -> acquired.pipelinesByKey
            is GPUPreparedImageCacheBatchAcquire.Refused -> {
                return GPUPreparedRenderRunMaterialization.Refused(
                    code = acquired.code,
                    message = acquired.message,
                    facts = mapOf("boundary" to "native"),
                )
            }
        }
        val created = mutableListOf<AutoCloseable>()
        return try {
            val uploadScopeIndices = plan.sourceScopeIndices.take(plan.resources.size)
            val renderScopeIndex = plan.sourceScopeIndices.last()
            val uploadScopeKeys = plan.exactScopeKeys.take(plan.resources.size)
            val renderScopeKey = plan.exactScopeKeys.last()
            val uploads = mutableListOf<GPUPreparedNativeScopeOperand.TextureUpload>()
            val uniformUploads = mutableListOf<GPUPreparedNativeBufferUpload>()
            val bindingByPacketId = linkedMapOf<String, MaterializedBinding>()
            val samplers =
                linkedMapOf<GPUPreparedImageSamplerKey, io.ygdrasil.webgpu.GPUSampler>()
            val uniformBytesByPacketId = plan.packets.mapIndexed { index, packet ->
                plan.uniformAllocations[index].packetId to preparedImageUniformBytes(packet)
            }.toMap()
            val pipelineByPacketId = plan.packets.mapIndexed { index, packet ->
                plan.uniformAllocations[index].packetId to pipelineByKey.getValue(packet.pipelineKey)
            }.toMap()
            plan.resources.forEachIndexed { resourceIndex, resource ->
                val allocations = resource.bindingRequests.map { request ->
                    request.uniformAllocation
                }
                val uniformBufferSize = allocations.maxOf { allocation ->
                    Math.addExact(allocation.offset, allocation.size)
                }
                val uniformSlab = ByteArray(uniformBufferSize.toInt())
                allocations.forEach { allocation ->
                    uniformBytesByPacketId.getValue(allocation.packetId).copyInto(
                        uniformSlab,
                        destinationOffset = allocation.offset.toInt(),
                    )
                }
                val texture = handleFactory.createTexture(resource).track(created)
                val view = handleFactory.createTextureView(texture, resource).track(created)
                val uniformBuffer = handleFactory.createUniformBuffer(uniformBufferSize).track(created)
                val uniformBufferOperand = GPUPreparedNativeBufferOperand(
                    uniformBuffer,
                    actualDeviceGeneration,
                    GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion,
                    byteCapacity = uniformBufferSize,
                )
                uniformUploads += GPUPreparedNativeBufferUpload(
                    data = GPUPreparedNativeUploadData(
                        GPUPreparedNativeOperandKey(
                            GPUPreparedNativeOperandRole.UploadSource,
                            GPUPreparedNativeOperandKind.Buffer,
                            gpuPreparedNativeBindingKey(
                                "prepared-image-uniform-data:${resource.uniformRef.value}",
                            ),
                        ),
                        uniformSlab,
                    ),
                    destination = uniformBufferOperand,
                    destinationKey = GPUPreparedNativeOperandKey(
                        GPUPreparedNativeOperandRole.UploadDestination,
                        GPUPreparedNativeOperandKind.Buffer,
                        gpuPreparedNativeBindingKey(
                            "GPUFrameBufferRef:${resource.uniformRef.value}",
                        ),
                    ),
                    destinationOffset = 0L,
                    consumerSourceStepIndices = listOf(renderScopeIndex),
                )
                val bindingKeys = resource.preparedImageNativeBindingKeys(
                    deviceGeneration = actualDeviceGeneration.value,
                )
                val bindGroups =
                    linkedMapOf<GPUPreparedImageBindingKey, io.ygdrasil.webgpu.GPUBindGroup>()
                resource.bindingRequests.forEach { request ->
                    val allocation = request.uniformAllocation
                    val samplerKey =
                        bindingKeys.samplerKeysByPacketId.getValue(request.packetId)
                    val sampler = samplers.getOrPut(samplerKey) {
                        handleFactory.createSampler(request.sampler).track(created)
                    }
                    val bindingKey =
                        bindingKeys.bindingKeysByPacketId.getValue(request.packetId)
                    val bindGroup = bindGroups.getOrPut(bindingKey) {
                        handleFactory.createBindGroup(
                            pipelineByPacketId.getValue(request.packetId).bindGroupLayout,
                            request,
                            uniformBuffer,
                            view,
                            sampler,
                        ).track(created)
                    }
                    check(bindingByPacketId.put(
                        request.packetId,
                        MaterializedBinding(bindGroup, allocation),
                    ) == null) {
                        "Prepared-image packet binding must be unique across the accepted run"
                    }
                }
                val exactUploadKeys = uploadScopeKeys[resourceIndex].operandKeys
                val uploadDataKey = exactUploadKeys[0]
                val destinationKey = exactUploadKeys[1]
                uploads += GPUPreparedNativeScopeOperand.TextureUpload(
                    sourceStepIndex = uploadScopeIndices[resourceIndex],
                    data = GPUPreparedNativeUploadData(
                        uploadDataKey,
                        resource.uploadLayout.bytesForUpload(),
                    ),
                    destination = GPUPreparedNativeTextureOperand(
                        texture,
                        actualDeviceGeneration,
                        GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion,
                    ),
                    destinationKey = destinationKey,
                    layout = resource.uploadLayout,
                )
            }
            val drawEntries = plan.packets.mapIndexed { index, packet ->
                val allocation = plan.uniformAllocations[index]
                val binding = requireNotNull(bindingByPacketId[allocation.packetId])
                check(binding.allocation == allocation)
                val pipeline = requireNotNull(pipelineByKey[packet.pipelineKey])
                val uniformBytes = uniformBytesByPacketId.getValue(allocation.packetId)
                GPUPreparedNativeScopeOperand.PreparedImageDrawEntry(
                    pipeline = GPUPreparedNativeRenderPipelineOperand(
                        pipeline.pipeline,
                        actualDeviceGeneration,
                    ),
                    bindGroup = GPUPreparedNativeBindGroupOperand(
                        binding.bindGroup,
                        actualDeviceGeneration,
                        GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion,
                    ),
                    dynamicUniformOffset = allocation.offset,
                    uniformBytes = uniformBytes,
                    scissor = packet.scissorBounds,
                )
            }
            val render = GPUPreparedNativeScopeOperand.PreparedImageRenderRun(
                sourceStepIndex = renderScopeIndex,
                drawEntries = drawEntries,
                exactOperandKeys = renderScopeKey.operandKeys,
            )
            val scopes = (uploads + render).sortedBy(
                GPUPreparedNativeScopeOperand::sourceStepIndex,
            )
            val owner = GPUPreparedRenderRunOwnedResources(created)
            created.clear()
            GPUPreparedRenderRunMaterialization.Ready(
                scopeOperands = immutableList(scopes),
                uniformUploads = immutableList(uniformUploads),
                ownedResources = immutableList(listOf(owner)),
            )
        } catch (failure: Throwable) {
            return failedPreparedImageMaterialization(created, failure)
        }
    }

    fun materializeAcceptedFrame(
        imageFrames: List<GPUPreparedSurfaceImageFramePlan>,
        runs: List<GPUPreparedSurfaceImageRenderRunPlan>,
        actualDeviceGeneration: GPUDeviceGenerationID,
    ): GPUPreparedRenderRunMaterialization {
        val pipelineByKey = when (
            val acquired = sessionCache.acquireBatch(
                runs.flatMap { run ->
                    run.packets.map(GPUDrawSemanticPayload.SampledImage::pipelineKey)
                },
                actualDeviceGeneration,
            )
        ) {
            is GPUPreparedImageCacheBatchAcquire.Ready -> acquired.pipelinesByKey
            is GPUPreparedImageCacheBatchAcquire.Refused -> {
                return GPUPreparedRenderRunMaterialization.Refused(
                    code = acquired.code,
                    message = acquired.message,
                    facts = mapOf("boundary" to "native"),
                )
            }
        }
        val created = mutableListOf<AutoCloseable>()
        return try {
            val uploads = mutableListOf<GPUPreparedNativeScopeOperand.TextureUpload>()
            val renders = mutableListOf<GPUPreparedNativeScopeOperand.PreparedImageRenderRun>()
            val uniformUploads = mutableListOf<GPUPreparedNativeBufferUpload>()
            val bindingByPacketId = linkedMapOf<String, MaterializedBinding>()
            val samplers =
                linkedMapOf<GPUPreparedImageSamplerKey, io.ygdrasil.webgpu.GPUSampler>()
            val bindGroups =
                linkedMapOf<GPUPreparedImageBindingKey, io.ygdrasil.webgpu.GPUBindGroup>()
            val uniformBytesByPacketId = buildMap {
                runs.forEach { run ->
                    run.packets.forEachIndexed { index, packet ->
                        put(
                            run.uniformAllocations[index].packetId,
                            preparedImageUniformBytes(packet),
                        )
                    }
                }
            }
            val pipelineByPacketId = buildMap {
                runs.forEach { run ->
                    run.packets.forEachIndexed { index, packet ->
                        put(
                            run.uniformAllocations[index].packetId,
                            pipelineByKey.getValue(packet.pipelineKey),
                        )
                    }
                }
            }
            imageFrames.forEach { imageFrame ->
                val resource = imageFrame.resourcePlan
                val allocations = resource.bindingRequests.map { request ->
                    request.uniformAllocation
                }
                val uniformBufferSize = allocations.maxOf { allocation ->
                    Math.addExact(allocation.offset, allocation.size)
                }
                val uniformSlab = ByteArray(uniformBufferSize.toInt())
                allocations.forEach { allocation ->
                    uniformBytesByPacketId.getValue(allocation.packetId).copyInto(
                        uniformSlab,
                        destinationOffset = allocation.offset.toInt(),
                    )
                }
                val texture = handleFactory.createTexture(resource).track(created)
                val view = handleFactory.createTextureView(texture, resource).track(created)
                val uniformBuffer = handleFactory.createUniformBuffer(uniformBufferSize).track(created)
                val uniformBufferOperand = GPUPreparedNativeBufferOperand(
                    uniformBuffer,
                    actualDeviceGeneration,
                    GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion,
                    byteCapacity = uniformBufferSize,
                )
                uniformUploads += GPUPreparedNativeBufferUpload(
                    data = GPUPreparedNativeUploadData(
                        GPUPreparedNativeOperandKey(
                            GPUPreparedNativeOperandRole.UploadSource,
                            GPUPreparedNativeOperandKind.Buffer,
                            gpuPreparedNativeBindingKey(
                                "prepared-image-uniform-data:${resource.uniformRef.value}",
                            ),
                        ),
                        uniformSlab,
                    ),
                    destination = uniformBufferOperand,
                    destinationKey = GPUPreparedNativeOperandKey(
                        GPUPreparedNativeOperandRole.UploadDestination,
                        GPUPreparedNativeOperandKind.Buffer,
                        gpuPreparedNativeBindingKey(
                            "GPUFrameBufferRef:${resource.uniformRef.value}",
                        ),
                    ),
                    destinationOffset = 0L,
                    consumerSourceStepIndices = imageFrame.consumerRenderScopeIndices,
                )
                val bindingKeys = resource.preparedImageNativeBindingKeys(
                    deviceGeneration = actualDeviceGeneration.value,
                )
                resource.bindingRequests.forEach { request ->
                    val allocation = request.uniformAllocation
                    val samplerKey =
                        bindingKeys.samplerKeysByPacketId.getValue(request.packetId)
                    val sampler = samplers.getOrPut(samplerKey) {
                        handleFactory.createSampler(request.sampler).track(created)
                    }
                    val bindingKey =
                        bindingKeys.bindingKeysByPacketId.getValue(request.packetId)
                    val bindGroup = bindGroups.getOrPut(bindingKey) {
                        handleFactory.createBindGroup(
                            pipelineByPacketId.getValue(request.packetId).bindGroupLayout,
                            request,
                            uniformBuffer,
                            view,
                            sampler,
                        ).track(created)
                    }
                    bindingByPacketId[request.packetId] =
                        MaterializedBinding(bindGroup, allocation)
                }
                val exactUploadKeys = imageFrame.uploadScopeKey.operandKeys
                uploads += GPUPreparedNativeScopeOperand.TextureUpload(
                    sourceStepIndex = imageFrame.uploadScopeKey.sourceStepIndex,
                    data = GPUPreparedNativeUploadData(
                        exactUploadKeys[0],
                        resource.uploadLayout.bytesForUpload(),
                    ),
                    destination = GPUPreparedNativeTextureOperand(
                        texture,
                        actualDeviceGeneration,
                        GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion,
                    ),
                    destinationKey = exactUploadKeys[1],
                    layout = resource.uploadLayout,
                )
            }
            runs.forEach { run ->
                val drawEntries = run.packets.mapIndexed { index, packet ->
                    val allocation = run.uniformAllocations[index]
                    val binding = bindingByPacketId.getValue(allocation.packetId)
                    val pipeline = pipelineByKey.getValue(packet.pipelineKey)
                    GPUPreparedNativeScopeOperand.PreparedImageDrawEntry(
                        pipeline = GPUPreparedNativeRenderPipelineOperand(
                            pipeline.pipeline,
                            actualDeviceGeneration,
                        ),
                        bindGroup = GPUPreparedNativeBindGroupOperand(
                            binding.bindGroup,
                            actualDeviceGeneration,
                            GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion,
                        ),
                        dynamicUniformOffset = allocation.offset,
                        uniformBytes = uniformBytesByPacketId.getValue(allocation.packetId),
                        scissor = packet.scissorBounds,
                    )
                }
                renders += GPUPreparedNativeScopeOperand.PreparedImageRenderRun(
                    sourceStepIndex = run.sourceScopeIndex,
                    drawEntries = drawEntries,
                    exactOperandKeys = run.exactScopeKey.operandKeys,
                )
            }
            val owner = GPUPreparedRenderRunOwnedResources(created)
            created.clear()
            GPUPreparedRenderRunMaterialization.Ready(
                scopeOperands = immutableList(
                    (uploads + renders).sortedBy(
                        GPUPreparedNativeScopeOperand::sourceStepIndex,
                    ),
                ),
                uniformUploads = immutableList(uniformUploads),
                ownedResources = immutableList(listOf(owner)),
            )
        } catch (failure: Throwable) {
            return failedPreparedImageMaterialization(created, failure)
        }
    }
}

internal object GPUPreparedImagePlanValidator {
    fun validateFramePlans(
        imageFrames: List<GPUPreparedSurfaceImageFramePlan>,
        runs: List<GPUPreparedSurfaceImageRenderRunPlan>,
    ): Pair<String, String>? {
        if (imageFrames.isEmpty() || runs.isEmpty()) {
            return "unsupported.prepared_image.plan_identity" to
                "Prepared-image frames require at least one artifact and one render run."
        }
        val artifactKeys = imageFrames.map { frame -> frame.resourcePlan.artifactKey }
        if (artifactKeys.distinct().size != imageFrames.size) {
            return "unsupported.prepared_image.artifact_identity" to
                "Prepared-image frames require exactly one upload resource per artifact."
        }
        val uploadScopeIndices = imageFrames.map { frame ->
            frame.uploadScopeKey.sourceStepIndex
        }
        val renderScopeIndices = runs.map { run -> run.sourceScopeIndex }
        if ((uploadScopeIndices + renderScopeIndices).distinct().size !=
            uploadScopeIndices.size + renderScopeIndices.size
        ) {
            return "unsupported.prepared_image.scope_identity" to
                "Prepared-image frame upload and render scopes must be globally unique."
        }
        val frameByArtifact = imageFrames.associateBy { frame ->
            frame.resourcePlan.artifactKey
        }
        val packetIds = runs.flatMap { run ->
            run.uniformAllocations.map(GPUPreparedImageUniformAllocation::packetId)
        }
        if (packetIds.distinct().size != packetIds.size) {
            return "unsupported.prepared_image.packet_identity" to
                "Prepared-image frame packet identities must be globally unique."
        }
        imageFrames.forEach { imageFrame ->
            val resource = imageFrame.resourcePlan
            val consumers = runs.filter { run ->
                run.resourcePlans.any { it === resource }
            }
            if (consumers.map { run -> run.sourceScopeIndex } !=
                imageFrame.consumerRenderScopeIndices
            ) {
                return "unsupported.prepared_image.plan_identity" to
                    "Prepared-image frame consumers must exactly match the accepted render runs."
            }
            val consumerBindings = consumers.flatMap { run ->
                run.orderedBindings.filter { binding ->
                    binding.artifactKey == resource.artifactKey
                }
            }
            if (consumerBindings != resource.bindingRequests) {
                return GPUPreparedImageRefusalCodes.NATIVE_BINDING to
                    "Prepared-image frame bindings must exactly partition every artifact resource."
            }
        }
        runs.forEach { run ->
            val runResources = mutableListOf<GPUImageFrameResourcePlan>()
            val runUploadScopeKeys = mutableListOf<GPUPreparedNativeScopeKey>()
            run.resourcePlans.forEach { resource ->
                val imageFrame = frameByArtifact[resource.artifactKey]
                    ?: return "unsupported.prepared_image.artifact_identity" to
                        "Prepared-image render runs must consume a frame-global artifact."
                if (imageFrame.resourcePlan !== resource) {
                    return "unsupported.prepared_image.plan_identity" to
                        "Prepared-image runs must retain the exact frame-global resource plan."
                }
                val bindings = run.orderedBindings.filter { binding ->
                    binding.artifactKey == resource.artifactKey
                }
                runResources += resource.copy(bindingRequests = bindings)
                runUploadScopeKeys += imageFrame.uploadScopeKey
            }
            val runPlan = GPUPreparedImageRenderRunPlan(
                sourceScopeIndices =
                    runUploadScopeKeys.map(GPUPreparedNativeScopeKey::sourceStepIndex) +
                        run.sourceScopeIndex,
                packets = run.packets,
                resources = runResources,
                uniformAllocations = run.uniformAllocations,
                exactScopeKeys = runUploadScopeKeys + run.exactScopeKey,
            )
            validatePlan(
                plan = runPlan,
                validateUploadProvenance = false,
            )?.let { return it }
        }
        return null
    }

    fun validatePlan(
        plan: GPUPreparedImageRenderRunPlan,
    ): Pair<String, String>? = validatePlan(
        plan = plan,
        validateUploadProvenance = true,
    )

    private fun validatePlan(
        plan: GPUPreparedImageRenderRunPlan,
        validateUploadProvenance: Boolean,
    ): Pair<String, String>? {
        val bindingLayoutIdentity = preparedImageBindingLayoutContract().identity
        if (plan.packets.any { packet ->
                packet.pipelineKey.bindingLayoutHash != bindingLayoutIdentity
            } ||
            plan.resources.any { resource ->
                resource.bindingRequests.any { request ->
                    request.bindingLayoutHash != bindingLayoutIdentity
                }
            }
        ) {
            return GPUPreparedImageRefusalCodes.NATIVE_BINDING to
                "Prepared-image runs require the canonical reflected ABI112 binding identity."
        }
        val uploadScopeKeys = plan.exactScopeKeys.take(plan.resources.size)
        if (uploadScopeKeys.any { scope ->
                scope.operandKeys.map { key -> key.role to key.kind } != listOf(
                    GPUPreparedNativeOperandRole.UploadSource to
                        GPUPreparedNativeOperandKind.Buffer,
                    GPUPreparedNativeOperandRole.UploadDestination to
                        GPUPreparedNativeOperandKind.Texture,
                ) ||
                    scope.operandKeys.any {
                        it.ownership != GPUPreparedNativeOperandOwnership.Borrowed
                    }
            }
        ) {
            return "unsupported.prepared_image.scope_identity" to
                "Prepared-image upload scopes require exact borrowed preflight source and destination keys."
        }
        val renderScopeKey = plan.exactScopeKeys.last()
        if (renderScopeKey.operandKeys.map { key -> key.role to key.kind } !=
            listOf(
                    GPUPreparedNativeOperandRole.RenderColorTarget to
                        GPUPreparedNativeOperandKind.TextureView,
                ) + plan.packets.flatMap {
                    listOf(
                        GPUPreparedNativeOperandRole.RenderPipeline to
                            GPUPreparedNativeOperandKind.RenderPipeline,
                        GPUPreparedNativeOperandRole.RenderBindGroup to
                            GPUPreparedNativeOperandKind.BindGroup,
                    )
                } ||
            renderScopeKey.operandKeys.any {
                it.ownership != GPUPreparedNativeOperandOwnership.Borrowed
            }
        ) {
            return "unsupported.prepared_image.scope_identity" to
                "Prepared-image render run requires the exact borrowed target and packet bridges."
        }
        if (plan.uniformAllocations.any {
                it.offset < 0L ||
                    it.size != GPUPreparedImageUniformAbi.BYTE_SIZE.toLong() ||
                    it.offset > Int.MAX_VALUE.toLong() - it.size
            }
        ) {
            return "unsupported.prepared_image.uniform_allocation" to
                "Prepared-image run requires exact ABI112 uniform allocations."
        }
        if (plan.uniformAllocations.map { it.packetId }.distinct().size != plan.packets.size) {
            return "unsupported.prepared_image.packet_identity" to
                "Prepared-image run packet identities must be unique."
        }
        val bindingPacketIds = plan.resources.flatMap { it.bindingRequests }.map { it.packetId }
        val allocationPacketIds = plan.uniformAllocations.map { it.packetId }
        if (bindingPacketIds.size != plan.packets.size ||
            bindingPacketIds.toSet() != allocationPacketIds.toSet()
        ) {
            return GPUPreparedImageRefusalCodes.NATIVE_BINDING to
                "Prepared-image run bindings must exactly cover uniform packet identities."
        }
        val packetById = plan.uniformAllocations.mapIndexed { index, allocation ->
            allocation.packetId to plan.packets[index]
        }.toMap()
        val resourceArtifactKeys = plan.resources.map { resource ->
            val artifactKeys = resource.bindingRequests.map { it.artifactKey }.distinct()
            if (artifactKeys.size != 1 ||
                artifactKeys.singleOrNull() != resource.artifactKey ||
                resource.bindingRequests.any { request ->
                    packetById[request.packetId]?.artifact?.key != request.artifactKey
                }
            ) {
                return "unsupported.prepared_image.artifact_identity" to
                    "Prepared-image bindings must retain their consuming packet artifact."
            }
            resource.artifactKey
        }
        if (resourceArtifactKeys.distinct().size != resourceArtifactKeys.size) {
            return "unsupported.prepared_image.artifact_identity" to
                "Prepared-image runs require exactly one upload resource per artifact."
        }
        plan.resources.forEach { resource ->
            val logicalRowBytes = try {
                Math.multiplyExact(resource.artifactWidth.toLong(), 4L)
            } catch (_: ArithmeticException) {
                return "unsupported.prepared_image.upload_provenance" to
                    "Prepared-image upload provenance dimensions overflowed."
            }
            val packetArtifacts = resource.bindingRequests.map { request ->
                packetById.getValue(request.packetId).artifact
            }
            if (resource.artifactWidth <= 0 ||
                resource.artifactHeight <= 0 ||
                resource.textureDescriptor.width != resource.artifactWidth ||
                resource.textureDescriptor.height != resource.artifactHeight ||
                resource.uploadLayout.width != resource.artifactWidth ||
                resource.uploadLayout.height != resource.artifactHeight ||
                resource.uploadLayout.logicalBytesPerRow != logicalRowBytes ||
                packetArtifacts.any { artifact ->
                    artifact.key != resource.artifactKey ||
                        artifact.width != resource.artifactWidth ||
                        artifact.height != resource.artifactHeight ||
                        artifact.contentHash != resource.artifactContentHash
                } ||
                (
                    validateUploadProvenance &&
                        resource.uploadLayout.logicalBytesForHash().preparedImageSha256() !=
                        resource.artifactContentHash
                )
            ) {
                return "unsupported.prepared_image.upload_provenance" to
                    "Prepared-image upload bytes must retain artifact dimensions and content hash."
            }
        }
        val allocationByPacketId = plan.uniformAllocations.associateBy { it.packetId }
        if (plan.resources.any { resource ->
                resource.bindingRequests.any { request ->
                    allocationByPacketId[request.packetId] != request.uniformAllocation
                }
            }
        ) {
            return "unsupported.prepared_image.uniform_identity" to
                "Prepared-image run allocations must equal their sealed binding allocations."
        }
        plan.resources.forEach { resource ->
            val uniformPreparation = resource.preparationRequests.singleOrNull { request ->
                request.resource == resource.uniformRef &&
                    request.role == GPUFrameResourceRole.UniformData
            }
            val descriptor = uniformPreparation?.descriptor as? GPUFrameBufferDescriptor
                ?: return "unsupported.prepared_image.uniform_preparation" to
                    "Prepared-image resources require one exact uniform buffer preparation."
            if (uniformPreparation.byteSize != descriptor.byteSize) {
                return "unsupported.prepared_image.uniform_preparation" to
                    "Prepared-image uniform preparation size must equal its buffer descriptor."
            }
            val allocations = resource.bindingRequests.map { it.uniformAllocation }
            if (allocations.any { allocation ->
                    allocation.size != GPU_PREPARED_IMAGE_UNIFORM_ALLOCATION_SIZE_BYTES
                }
            ) {
                return "unsupported.prepared_image.uniform_allocation" to
                    "Prepared-image bindings require exact ABI112 uniform allocations."
            }
            if (allocations.any { allocation ->
                    allocation.offset % descriptor.alignmentBytes != 0L
                }
            ) {
                return "unsupported.prepared_image.uniform_alignment" to
                    "Prepared-image uniform offsets must satisfy the prepared buffer alignment."
            }
            val ordered = allocations.sortedBy { it.offset }
            if (ordered.zipWithNext().any { (left, right) ->
                    Math.addExact(left.offset, left.size) > right.offset
                }
            ) {
                return "unsupported.prepared_image.uniform_overlap" to
                    "Prepared-image uniform allocations must not overlap within one resource."
            }
            if (allocations.any { allocation ->
                    Math.addExact(allocation.offset, allocation.size) > descriptor.byteSize
                }
            ) {
                return "unsupported.prepared_image.uniform_range" to
                    "Prepared-image uniform allocations must fit their prepared buffer."
            }
        }
        val uploadIndices = plan.sourceScopeIndices.take(plan.resources.size)
        val renderIndex = plan.sourceScopeIndices.last()
        if (uploadIndices.any { uploadIndex -> uploadIndex >= renderIndex }) {
            return "unsupported.prepared_image.upload_order" to
                "Every prepared-image upload must precede each of its consuming renders."
        }
        if (plan.packets.any { packet ->
                (packet.atlasColorPremultipliedRgba == null) !=
                    (packet.atlasSourceBlend == null)
            }
        ) {
            return "unsupported.prepared_image.atlas_blend" to
                "Prepared-image run must retain one of the five closed atlas source modes."
        }
        return null
    }
}

private fun ByteArray.preparedImageSha256(): String =
    MessageDigest.getInstance("SHA-256").digest(this).joinToString("") { byte ->
        "%02x".format(byte)
    }

private fun preparedImageUniformBytes(
    packet: GPUDrawSemanticPayload.SampledImage,
): ByteArray {
    val targetWidth = packet.targetBounds.right - packet.targetBounds.left
    val targetHeight = packet.targetBounds.bottom - packet.targetBounds.top
    val positions = packet.geometry.vertices.map { vertex ->
        (vertex.x / targetWidth.toFloat() * 2f - 1f) to
            (1f - vertex.y / targetHeight.toFloat() * 2f)
    }
    return GPUPreparedImageUniformAbi.pack(
        GPUPreparedImageUniformInput(
            positions = positions,
            uvs = packet.geometry.vertices.map { it.u to it.v },
            tintPremultipliedRgba = packet.tintPremultipliedRgba,
            atlasColorPremultipliedRgba = packet.atlasColorPremultipliedRgba,
            alphaOnly = packet.artifact.alphaOnly,
            atlasSourceBlend = packet.atlasSourceBlend,
        ),
    )
}

private data class MaterializedBinding(
    val bindGroup: io.ygdrasil.webgpu.GPUBindGroup,
    val allocation: GPUPreparedImageUniformAllocation,
)

internal class GPUPreparedRenderRunOwnedResources(
    handles: List<AutoCloseable>,
) : AutoCloseable {
    private var pending = handles.asReversed().distinctNativeIdentities().toMutableList()

    @Synchronized
    internal fun ownedHandlesSnapshot(): List<AutoCloseable> = pending.toList()

    @Synchronized
    internal fun detachOwnedHandles(handles: Collection<AutoCloseable>) {
        pending.removeAll { candidate -> handles.any { it === candidate } }
    }

    @Synchronized
    override fun close() {
        var firstFailure: Throwable? = null
        val iterator = pending.iterator()
        while (iterator.hasNext()) {
            val handle = iterator.next()
            try {
                handle.close()
                iterator.remove()
            } catch (failure: Throwable) {
                if (firstFailure == null) firstFailure = failure else firstFailure.addSuppressed(failure)
            }
        }
        firstFailure?.let {
            throw IllegalStateException(
                "Prepared-image owner retains ${pending.size} handle(s) after close failure",
                it,
            )
        }
    }
}

private fun <T : AutoCloseable> T.track(handles: MutableList<AutoCloseable>): T =
    also(handles::add)

private fun List<AutoCloseable>.distinctNativeIdentities(): List<AutoCloseable> {
    val identities = java.util.Collections.newSetFromMap(
        IdentityHashMap<AutoCloseable, Boolean>(),
    )
    return filter(identities::add)
}

private fun failedPreparedImageMaterialization(
    handles: MutableList<AutoCloseable>,
    failure: Throwable,
): GPUPreparedRenderRunMaterialization.Refused {
    val owner = GPUPreparedRenderRunOwnedResources(handles)
    handles.clear()
    val rollbackFailure = runCatching(owner::close).exceptionOrNull()
    val retainedOwner = owner.takeIf { rollbackFailure != null }
    return GPUPreparedRenderRunMaterialization.Refused(
        code = "failed.prepared_image.materialization",
        message = "Prepared-image native materialization failed: " +
            "${failure::class.simpleName.orEmpty()}: ${failure.message.orEmpty()}." +
            rollbackFailure?.let {
                " Rollback retained native ownership after close failure: " +
                    "${it::class.simpleName.orEmpty()}: ${it.message.orEmpty()}."
            }.orEmpty(),
        facts = mapOf("boundary" to "native"),
        retainedCloseOwner = retainedOwner,
    )
}
