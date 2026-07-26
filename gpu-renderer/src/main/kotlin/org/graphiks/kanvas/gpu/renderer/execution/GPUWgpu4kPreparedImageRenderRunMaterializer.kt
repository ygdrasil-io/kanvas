package org.graphiks.kanvas.gpu.renderer.execution

import java.util.IdentityHashMap
import org.graphiks.kanvas.gpu.renderer.collections.immutableList
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.resources.GPUPreparedImageFrameResourcePlan
import org.graphiks.kanvas.gpu.renderer.resources.GPUPreparedImageUniformAllocation

internal data class GPUPreparedImageRenderRunPlan(
    val sourceScopeIndices: List<Int>,
    val packets: List<GPUDrawSemanticPayload.SampledImage>,
    val resources: List<GPUPreparedImageFrameResourcePlan>,
    val uniformAllocations: List<GPUPreparedImageUniformAllocation>,
) {
    init {
        require(packets.isNotEmpty() && resources.isNotEmpty())
        require(packets.size == uniformAllocations.size)
        require(sourceScopeIndices.size == resources.size + packets.size)
        require(sourceScopeIndices.distinct().size == sourceScopeIndices.size &&
            sourceScopeIndices.all { it >= 0 }
        )
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
            val uploads = mutableListOf<GPUPreparedNativeScopeOperand.TextureUpload>()
            val uniformUploads = mutableListOf<GPUPreparedNativeBufferUpload>()
            val bindingByPacketId = linkedMapOf<String, MaterializedBinding>()
            val allocationByPacketId = plan.uniformAllocations.associateBy { it.packetId }
            val renderIndexByPacketId = plan.uniformAllocations.mapIndexed { index, allocation ->
                allocation.packetId to renderScopeIndices[index]
            }.toMap()
            val uniformBytesByPacketId = plan.packets.mapIndexed { index, packet ->
                plan.uniformAllocations[index].packetId to preparedImageUniformBytes(packet)
            }.toMap()
            plan.resources.forEachIndexed { resourceIndex, resource ->
                val allocations = resource.bindingRequests.map { request ->
                    allocationByPacketId.getValue(request.packetId)
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
                    val allocation = allocationByPacketId.getValue(request.packetId)
                    val sampler = samplers.getOrPut(request.sampler) {
                        handleFactory.createSampler(request.sampler).track(created)
                    }
                    val bindGroup = handleFactory.createBindGroup(
                        request.copy(uniformAllocation = allocation),
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
                val uploadDataKey = GPUPreparedNativeOperandKey(
                    GPUPreparedNativeOperandRole.UploadSource,
                    GPUPreparedNativeOperandKind.Buffer,
                    gpuPreparedNativeBindingKey(
                        "prepared-image-upload-data:${resource.stagingRef.value}",
                    ),
                )
                val destinationKey = GPUPreparedNativeOperandKey(
                    GPUPreparedNativeOperandRole.UploadDestination,
                    GPUPreparedNativeOperandKind.Texture,
                    gpuPreparedNativeBindingKey(
                        "GPUFrameTextureRef:${resource.frameTextureRef.value}",
                    ),
                )
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
                    exactOperandKeys = listOf(
                        GPUPreparedNativeOperandKey(
                            GPUPreparedNativeOperandRole.RenderPipeline,
                            GPUPreparedNativeOperandKind.RenderPipeline,
                            gpuPreparedNativeBindingKey(
                                "prepared-image:${packet.pipelineKey}:pipeline",
                            ),
                        ),
                        GPUPreparedNativeOperandKey(
                            GPUPreparedNativeOperandRole.RenderBindGroup,
                            GPUPreparedNativeOperandKind.BindGroup,
                            gpuPreparedNativeBindingKey(
                                "prepared-image:${allocation.packetId}:bind-group",
                            ),
                            GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion,
                        ),
                    ),
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
                resource.bindingRequests.any { request ->
                    packetById[request.packetId]?.artifact?.key != request.artifactKey
                }
            ) {
                return "unsupported.prepared_image.artifact_identity" to
                    "Prepared-image bindings must retain their consuming packet artifact."
            }
            artifactKeys.single()
        }
        if (resourceArtifactKeys.distinct().size != resourceArtifactKeys.size) {
            return "unsupported.prepared_image.artifact_identity" to
                "Prepared-image runs require exactly one upload resource per artifact."
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
