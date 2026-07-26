package org.graphiks.kanvas.gpu.renderer.execution

import java.util.IdentityHashMap
import org.graphiks.kanvas.gpu.renderer.collections.immutableList
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole
import org.graphiks.kanvas.gpu.renderer.resources.GPUPreparedImageFrameResourcePlan
import org.graphiks.kanvas.gpu.renderer.resources.GPUPreparedImageUniformAllocation
import org.graphiks.kanvas.gpu.renderer.resources.GPU_PREPARED_IMAGE_UNIFORM_ALLOCATION_SIZE_BYTES

internal class GPUPreparedImageRenderRunPlan(
    sourceScopeIndices: List<Int>,
    packets: List<GPUDrawSemanticPayload.SampledImage>,
    resources: List<GPUPreparedImageFrameResourcePlan>,
    uniformAllocations: List<GPUPreparedImageUniformAllocation>,
    exactScopeKeys: List<GPUPreparedNativeScopeKey>,
) {
    val sourceScopeIndices = immutableList(sourceScopeIndices)
    val packets = immutableList(packets)
    val resources = immutableList(resources)
    val uniformAllocations = immutableList(uniformAllocations)
    val exactScopeKeys = immutableList(exactScopeKeys)

    init {
        require(this.packets.isNotEmpty() && this.resources.isNotEmpty())
        require(this.packets.size == this.uniformAllocations.size)
        require(this.sourceScopeIndices.size == this.resources.size + this.packets.size)
        require(this.sourceScopeIndices.distinct().size == this.sourceScopeIndices.size &&
            this.sourceScopeIndices.all { it >= 0 }
        )
        require(this.exactScopeKeys.map(GPUPreparedNativeScopeKey::sourceStepIndex) ==
            this.sourceScopeIndices
        ) { "Prepared-image exact scope keys must match the accepted source scopes in order" }
        require(this.exactScopeKeys.map(GPUPreparedNativeScopeKey::operationKind) ==
            List(this.resources.size) { GPUEncoderOperationKind.Upload } +
            List(this.packets.size) { GPUEncoderOperationKind.Render }
        ) { "Prepared-image exact scope keys must retain upload-then-render operation kinds" }
        require(this.exactScopeKeys.all { it.operandKeys.isNotEmpty() }) {
            "Prepared-image exact scope keys must retain preflight operand authority"
        }
    }
}

internal sealed interface GPUPreparedRenderRunMaterialization {
    data class Ready(
        val scopeOperands: List<GPUPreparedNativeScopeOperand>,
        val uniformUploads: List<GPUPreparedNativeBufferUpload>,
        val ownedResources: List<AutoCloseable>,
    ) : GPUPreparedRenderRunMaterialization

    data class Refused(val code: String, val message: String) :
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
    ): GPUPreparedRenderRunMaterialization {
        validatePlan(plan)?.let {
            return GPUPreparedRenderRunMaterialization.Refused(it.first, it.second)
        }
        val created = mutableListOf<AutoCloseable>()
        return try {
            val uploadScopeIndices = plan.sourceScopeIndices.take(plan.resources.size)
            val renderScopeIndices = plan.sourceScopeIndices.drop(plan.resources.size)
            val uploadScopeKeys = plan.exactScopeKeys.take(plan.resources.size)
            val renderScopeKeys = plan.exactScopeKeys.drop(plan.resources.size)
            val uploads = mutableListOf<GPUPreparedNativeScopeOperand.TextureUpload>()
            val uniformUploads = mutableListOf<GPUPreparedNativeBufferUpload>()
            val bindingByPacketId = linkedMapOf<String, MaterializedBinding>()
            val renderIndexByPacketId = plan.uniformAllocations.mapIndexed { index, allocation ->
                allocation.packetId to renderScopeIndices[index]
            }.toMap()
            val uniformBytesByPacketId = plan.packets.mapIndexed { index, packet ->
                plan.uniformAllocations[index].packetId to preparedImageUniformBytes(packet)
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
                val consumerIndices = resource.bindingRequests.map { request ->
                    renderIndexByPacketId.getValue(request.packetId)
                }
                val texture = handleFactory.createTexture(resource).track(created)
                val view = handleFactory.createTextureView(texture, resource).track(created)
                val uniformBuffer = handleFactory.createUniformBuffer(uniformBufferSize).track(created)
                val uniformBufferOperand = GPUPreparedNativeBufferOperand(
                    uniformBuffer,
                    sessionCache.deviceGeneration,
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
                    consumerSourceStepIndices = consumerIndices,
                )
                val samplers = linkedMapOf<Any, io.ygdrasil.webgpu.GPUSampler>()
                resource.bindingRequests.forEach { request ->
                    val allocation = request.uniformAllocation
                    val sampler = samplers.getOrPut(request.sampler) {
                        handleFactory.createSampler(request.sampler).track(created)
                    }
                    val bindGroup = handleFactory.createBindGroup(
                        request,
                        uniformBuffer,
                        view,
                        sampler,
                    ).track(created)
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
                        sessionCache.deviceGeneration,
                        GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion,
                    ),
                    destinationKey = destinationKey,
                    layout = resource.uploadLayout,
                )
            }
            val renders = plan.packets.mapIndexed { index, packet ->
                val allocation = plan.uniformAllocations[index]
                val binding = requireNotNull(bindingByPacketId[allocation.packetId])
                check(binding.allocation == allocation)
                val cacheKey = packet.pipelineKey.copy(
                    bindingLayoutHash = PREPARED_IMAGE_BINDING_LAYOUT_HASH,
                )
                val pipeline = sessionCache.acquire(cacheKey)
                val uniformBytes = uniformBytesByPacketId.getValue(allocation.packetId)
                GPUPreparedNativeScopeOperand.PreparedImageRenderRun(
                    sourceStepIndex = renderScopeIndices[index],
                    pipeline = GPUPreparedNativeRenderPipelineOperand(
                        pipeline.pipeline,
                        sessionCache.deviceGeneration,
                    ),
                    bindGroup = GPUPreparedNativeBindGroupOperand(
                        binding.bindGroup,
                        sessionCache.deviceGeneration,
                        GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion,
                    ),
                    dynamicUniformOffset = allocation.offset,
                    uniformBytes = uniformBytes,
                    scissor = packet.scissorBounds,
                    exactOperandKeys = renderScopeKeys[index].operandKeys,
                )
            }
            val scopes = (uploads + renders).sortedBy(GPUPreparedNativeScopeOperand::sourceStepIndex)
            val owner = GPUPreparedImageRunOwnedResources(created)
            created.clear()
            GPUPreparedRenderRunMaterialization.Ready(
                scopeOperands = immutableList(scopes),
                uniformUploads = immutableList(uniformUploads),
                ownedResources = immutableList(listOf(owner)),
            )
        } catch (failure: Throwable) {
            closeRunHandlesOnce(created, failure)
            throw failure
        }
    }

    private fun validatePlan(
        plan: GPUPreparedImageRenderRunPlan,
    ): Pair<String, String>? {
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
        val renderScopeKeys = plan.exactScopeKeys.drop(plan.resources.size)
        if (renderScopeKeys.any { scope ->
                scope.operandKeys.map { key -> key.role to key.kind } != listOf(
                    GPUPreparedNativeOperandRole.RenderColorTarget to
                        GPUPreparedNativeOperandKind.TextureView,
                    GPUPreparedNativeOperandRole.RenderPipeline to
                        GPUPreparedNativeOperandKind.RenderPipeline,
                    GPUPreparedNativeOperandRole.RenderBindGroup to
                        GPUPreparedNativeOperandKind.BindGroup,
                ) ||
                    scope.operandKeys.any {
                        it.ownership != GPUPreparedNativeOperandOwnership.Borrowed
                    }
            }
        ) {
            return "unsupported.prepared_image.scope_identity" to
                "Prepared-image render scopes require exact borrowed target and native bridge keys."
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
            return "unsupported.prepared_image.binding_identity" to
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
        val renderIndices = plan.sourceScopeIndices.drop(plan.resources.size)
        val renderIndexByPacketId = plan.uniformAllocations.mapIndexed { index, allocation ->
            allocation.packetId to renderIndices[index]
        }.toMap()
        if (plan.resources.indices.any { resourceIndex ->
                val uploadIndex = uploadIndices[resourceIndex]
                plan.resources[resourceIndex].bindingRequests.any { request ->
                    uploadIndex >= renderIndexByPacketId.getValue(request.packetId)
                }
            }
        ) {
            return "unsupported.prepared_image.upload_order" to
                "Every prepared-image upload must precede each of its consuming renders."
        }
        if (plan.packets.any { packet ->
                packet.pipelineKey.atlasSourceBlend != packet.atlasSourceBlend ||
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

private class GPUPreparedImageRunOwnedResources(
    handles: List<AutoCloseable>,
) : AutoCloseable {
    private var pending = handles.asReversed().distinctNativeIdentities().toMutableList()

    @Synchronized
    override fun close() {
        val handles = pending
        pending = mutableListOf()
        var firstFailure: Throwable? = null
        handles.forEach { handle ->
            try {
                handle.close()
            } catch (failure: Throwable) {
                if (firstFailure == null) firstFailure = failure else firstFailure.addSuppressed(failure)
            }
        }
        firstFailure?.let { throw IllegalStateException("Prepared-image run close failed", it) }
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

private fun closeRunHandlesOnce(
    handles: MutableList<AutoCloseable>,
    cause: Throwable,
) {
    val pending = handles.asReversed().toList()
    handles.clear()
    pending.distinctNativeIdentities().forEach { handle ->
        try {
            handle.close()
        } catch (closeFailure: Throwable) {
            cause.addSuppressed(closeFailure)
        }
    }
}
