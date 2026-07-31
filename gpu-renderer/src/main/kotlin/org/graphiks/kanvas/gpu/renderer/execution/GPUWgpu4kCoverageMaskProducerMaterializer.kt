package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.ArrayBuffer
import io.ygdrasil.webgpu.GPUQueue
import io.ygdrasil.webgpu.GPUTextureFormat
import io.ygdrasil.webgpu.GPUTextureUsage
import io.ygdrasil.webgpu.GPUTextureView
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.capabilities.GPULimits
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.passes.GPUCoverageMaskProducerUniformSlabSeal
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveRenderPipelineStructuralKey

/**
 * The single native owner of CoverageMask producer pipelines, pooled attachment, ABI64 upload,
 * producer operands, and lease lifetime. Callers retain only their already-sealed scope assembly.
 */
internal fun interface GPUWgpu4kCoverageMaskProducerMaterializerPort {
    fun materialize(
        request: GPUWgpu4kCoverageMaskProducerRequest,
    ): GPUWgpu4kCoverageMaskProducerMaterialization
}

internal class GPUWgpu4kCoverageMaskProducerMaterializer(
    private val queue: GPUQueue,
    private val sessionCache: GPUWgpu4kCorePrimitiveSessionCache,
    private val limits: GPULimits,
) : GPUWgpu4kCoverageMaskProducerMaterializerPort {
    override fun materialize(
        request: GPUWgpu4kCoverageMaskProducerRequest,
    ): GPUWgpu4kCoverageMaskProducerMaterialization {
        val structuralKeys = request.producerStructuralPipelineKeys
        val producers = structuralKeys.indices
        if (producers.isEmpty() ||
            request.scopes.flatMap(GPUWgpu4kCoverageMaskProducerScope::producerIndices) !=
            producers.toList()
        ) {
            return GPUWgpu4kCoverageMaskProducerMaterialization.Refused(
                "invalid.native.coverage-mask.producer-partition",
                "CoverageMask producer scopes must form one exact ordered producer partition.",
            )
        }
        val alignment = limits.minUniformBufferOffsetAlignment
        if (alignment <= 0L) {
            return GPUWgpu4kCoverageMaskProducerMaterialization.Refused(
                "unsupported.native.coverage-mask.uniform-alignment",
                "CoverageMask ABI64 requires a positive native uniform alignment.",
            )
        }
        val envelope = request.resourceEnvelope
        val uniformOffsets = envelope.producerUniformOffsets
        if (envelope.vertexBytes <= 0L || envelope.indexBytes <= 0L ||
            envelope.uniformBytes <= 0L || envelope.uniformBytes > Int.MAX_VALUE ||
            !envelope.hasExactUniformByteSize() ||
            uniformOffsets.size != structuralKeys.size ||
            uniformOffsets.any { offset ->
                offset < 0L || offset % alignment != 0L ||
                    offset > envelope.uniformBytes - COVERAGE_MASK_PRODUCER_UNIFORM_BYTES
            } ||
            uniformOffsets.distinct().size != uniformOffsets.size
        ) {
            return GPUWgpu4kCoverageMaskProducerMaterialization.Refused(
                "invalid.native.coverage-mask.resource-envelope",
                "CoverageMask pooled sizes, producer offsets, or uniform slab snapshot are invalid.",
            )
        }
        if (!envelope.isBorrowedFrom(request.uniformSlabSeal)) {
            return GPUWgpu4kCoverageMaskProducerMaterialization.Refused(
                "invalid.native.coverage-mask.uniform-abi",
                "CoverageMask resource envelope lost its exact borrowed slab authority.",
            )
        }

        val cacheKeys = linkedMapOf<
            GPUCorePrimitiveRenderPipelineStructuralKey,
            GPUWgpu4kCorePrimitivePipelineCacheKey
            >()
        structuralKeys.distinct().forEach { structuralKey ->
            val mapped = mapCorePrimitiveStructuralKeyToWgpu4kPipelineIdentity(structuralKey) as?
                GPUWgpu4kCorePrimitivePipelineMapping.Mapped
                ?: return GPUWgpu4kCoverageMaskProducerMaterialization.Refused(
                    "unsupported.native.coverage-mask.pipeline",
                    "CoverageMask contains a producer outside the closed native programs.",
                )
            if (mapped.componentIdentity !=
                PRODUCTION_CORE_PRIMITIVE_COVERAGE_MASK_PRODUCER_COMPONENT_IDENTITY
            ) {
                return GPUWgpu4kCoverageMaskProducerMaterialization.Refused(
                    "invalid.native.coverage-mask.pipeline",
                    "CoverageMask producer component identity was substituted.",
                )
            }
            cacheKeys[structuralKey] = GPUWgpu4kCorePrimitivePipelineCacheKey(
                mapped.componentIdentity,
                mapped.identity,
            )
        }

        val acquiredByStructural = linkedMapOf<
            GPUCorePrimitiveRenderPipelineStructuralKey,
            GPUWgpu4kCorePrimitiveSessionCacheAcquire.Acquired
            >()
        cacheKeys.forEach { (structuralKey, cacheKey) ->
            when (val acquired = sessionCache.acquire(cacheKey)) {
                is GPUWgpu4kCorePrimitiveSessionCacheAcquire.Acquired ->
                    acquiredByStructural[structuralKey] = acquired
                is GPUWgpu4kCorePrimitiveSessionCacheAcquire.Refused ->
                    return GPUWgpu4kCoverageMaskProducerMaterialization.Refused(
                        "unsupported.native.coverage-mask.pipeline-cache",
                        "CoverageMask producer pipeline acquisition was refused: ${acquired.reason}.",
                    )
            }
        }

        val maskRequirement = GPUWgpu4kCorePrimitiveCoverageMaskRequirement(
            request.maskBounds.width,
            request.maskBounds.height,
            GPUTextureFormat.RGBA8Unorm,
            1,
            GPUTextureUsage.RenderAttachment or GPUTextureUsage.TextureBinding,
        )
        val lease = when (
            val checkout = sessionCache.acquireFrame(
                GPUWgpu4kCorePrimitiveFramePoolRequirements(
                    deviceGeneration = request.deviceGeneration,
                    vertexBytes = envelope.vertexBytes,
                    indexBytes = envelope.indexBytes,
                    uniformBytes = envelope.uniformBytes,
                    componentIdentity =
                        PRODUCTION_CORE_PRIMITIVE_COVERAGE_MASK_PRODUCER_COMPONENT_IDENTITY,
                    coverageMask = maskRequirement,
                    coverageMaskConsumerBindGroupRequired =
                        envelope.coverageMaskConsumerBindGroupRequired,
                ),
            )
        ) {
            is GPUWgpu4kCorePrimitiveFramePoolCheckout.Acquired -> checkout.lease
            is GPUWgpu4kCorePrimitiveFramePoolCheckout.Refused ->
                return GPUWgpu4kCoverageMaskProducerMaterialization.Refused(
                    "unsupported.native.coverage-mask.frame-pool",
                    "CoverageMask producer frame-pool checkout was refused: ${checkout.reason}.",
                )
        }

        return try {
            val handles = requireNotNull(lease.handles.coverageMask)
            require(handles.requirement == maskRequirement)
            envelope.uploadUniformSlab(queue, lease.handles.uniformBuffer)
            val maskOperand = GPUPreparedNativeTextureViewOperand(
                handles.view,
                request.deviceGeneration,
                GPUPreparedNativeOperandOwnership.Borrowed,
            )
            val producerBindGroup = GPUPreparedNativeBindGroupOperand(
                lease.handles.bindGroup,
                request.deviceGeneration,
                GPUPreparedNativeOperandOwnership.Borrowed,
            )
            val pipelineOperands = acquiredByStructural.mapValues { (_, acquired) ->
                GPUPreparedNativeRenderPipelineOperand.fromCorePrimitiveAcquisition(
                    acquired,
                    request.deviceGeneration,
                )
            }
            val scopeOperands = request.scopes.mapIndexed { scopeIndex, scope ->
                val commands = scope.producerIndices.flatMap { producerIndex ->
                    listOf(
                        GPUPreparedNativeRenderCommand.SetPipeline(
                            requireNotNull(pipelineOperands[structuralKeys[producerIndex]]),
                        ),
                        GPUPreparedNativeRenderCommand.SetBindGroup(
                            0,
                            producerBindGroup,
                            listOf(uniformOffsets[producerIndex]),
                        ),
                        GPUPreparedNativeRenderCommand.Draw(
                            GPUPreparedNativeDrawCall.Draw(3),
                        ),
                    )
                }
                GPUPreparedNativeScopeOperand.Render(
                    sourceStepIndex = scope.sourceStepIndex,
                    pass = GPUPreparedNativeRenderPassConfig(
                        colorTarget = maskOperand,
                        loadOperation = if (scopeIndex == 0) {
                            GPUPreparedNativeLoadOperation.Clear
                        } else {
                            GPUPreparedNativeLoadOperation.Load
                        },
                        storeOperation = GPUPreparedNativeStoreOperation.Store,
                        clearColor = if (scopeIndex == 0) {
                            GPUPreparedNativeClearColor(1.0, 1.0, 1.0, 1.0)
                        } else {
                            null
                        },
                    ),
                    commands = commands,
                )
            }
            GPUWgpu4kCoverageMaskProducerMaterialization.Ready(
                scopeOperands,
                handles.view,
                GPUWgpu4kCorePrimitivePayloadLeaseLifecycle(lease),
                GPUWgpu4kCoverageMaskBorrowedResources(
                    GPUPreparedNativeBufferOperand(
                        lease.handles.vertexBuffer,
                        request.deviceGeneration,
                        GPUPreparedNativeOperandOwnership.Borrowed,
                        lease.capacities.vertexBytes,
                    ),
                    GPUPreparedNativeBufferOperand(
                        lease.handles.indexBuffer,
                        request.deviceGeneration,
                        GPUPreparedNativeOperandOwnership.Borrowed,
                        lease.capacities.indexBytes,
                    ),
                    GPUPreparedNativeBufferOperand(
                        lease.handles.uniformBuffer,
                        request.deviceGeneration,
                        GPUPreparedNativeOperandOwnership.Borrowed,
                        lease.capacities.uniformBytes,
                    ),
                    handles.consumerBindGroupOrNull?.let { bindGroup ->
                        GPUPreparedNativeBindGroupOperand(
                            bindGroup,
                            request.deviceGeneration,
                            GPUPreparedNativeOperandOwnership.Borrowed,
                        )
                    },
                ),
            )
        } catch (failure: Throwable) {
            if (lease.rollbackBeforeSubmit() !is
                GPUWgpu4kCorePrimitiveFramePoolLeaseTransition.Applied
            ) {
                lease.quarantineUncertain()
            }
            GPUWgpu4kCoverageMaskProducerMaterialization.Refused(
                "failed.native.coverage-mask.producer-materialization",
                "CoverageMask producer native materialization failed: " +
                    "${failure::class.simpleName.orEmpty()}: ${failure.message.orEmpty()}.",
            )
        }
    }

    private companion object {
        const val COVERAGE_MASK_PRODUCER_UNIFORM_BYTES = 64
    }
}

internal class GPUWgpu4kCoverageMaskProducerRequest private constructor(
    val uniformSlabSeal: GPUCoverageMaskProducerUniformSlabSeal,
    scopes: List<GPUWgpu4kCoverageMaskProducerScope>,
    val deviceGeneration: GPUDeviceGenerationID,
    val resourceEnvelope: GPUWgpu4kCoverageMaskResourceEnvelope,
) {
    val maskBounds: GPUPixelBounds = uniformSlabSeal.maskBounds
    val producerStructuralPipelineKeys: List<GPUCorePrimitiveRenderPipelineStructuralKey> =
        uniformSlabSeal.producerSlots.map { producer -> producer.structuralPipelineKey }
    val scopes: List<GPUWgpu4kCoverageMaskProducerScope> = scopes.toList()

    init {
        require(!maskBounds.isEmpty &&
            this.producerStructuralPipelineKeys.isNotEmpty() &&
            this.scopes.isNotEmpty() &&
            this.scopes.map(GPUWgpu4kCoverageMaskProducerScope::sourceStepIndex)
                .distinct().size == this.scopes.size
        )
    }

    companion object {
        fun borrowSealed(
            uniformSlabSeal: GPUCoverageMaskProducerUniformSlabSeal,
            scopes: List<GPUWgpu4kCoverageMaskProducerScope>,
            deviceGeneration: GPUDeviceGenerationID,
            resourceEnvelope: GPUWgpu4kCoverageMaskResourceEnvelope,
        ): GPUWgpu4kCoverageMaskProducerRequest = GPUWgpu4kCoverageMaskProducerRequest(
            uniformSlabSeal = uniformSlabSeal,
            scopes = scopes,
            deviceGeneration = deviceGeneration,
            resourceEnvelope = resourceEnvelope,
        )
    }
}

internal class GPUWgpu4kCoverageMaskResourceEnvelope private constructor(
    val vertexBytes: Long,
    val indexBytes: Long,
    val uniformBytes: Long,
    producerUniformOffsets: List<Long>,
    uniformSlabSnapshot: ByteArray,
    val coverageMaskConsumerBindGroupRequired: Boolean,
    borrowBuilderPacked: Boolean,
    private val borrowedUniformSlabSeal: GPUCoverageMaskProducerUniformSlabSeal?,
) {
    val producerUniformOffsets: List<Long> = producerUniformOffsets.toList()
    private val uniformSlabSnapshot: ByteArray = if (borrowBuilderPacked) {
        uniformSlabSnapshot
    } else {
        uniformSlabSnapshot.copyOf()
    }

    fun hasExactUniformByteSize(): Boolean =
        uniformSlabSnapshot.size.toLong() == uniformBytes

    fun isBorrowedFrom(seal: GPUCoverageMaskProducerUniformSlabSeal): Boolean =
        borrowedUniformSlabSeal === seal

    fun uploadUniformSlab(queue: GPUQueue, buffer: io.ygdrasil.webgpu.GPUBuffer) {
        queue.writeBuffer(
            buffer,
            0uL,
            ArrayBuffer.of(uniformSlabSnapshot),
            0uL,
            uniformBytes.toULong(),
        )
    }

    companion object {
        fun borrowBuilderPacked(
            vertexBytes: Long,
            indexBytes: Long,
            uniformSlabSeal: GPUCoverageMaskProducerUniformSlabSeal,
            coverageMaskConsumerBindGroupRequired: Boolean,
        ): GPUWgpu4kCoverageMaskResourceEnvelope {
            val producerUniformOffsets = uniformSlabSeal.producerSlots.map { producerSlot ->
                requireNotNull(uniformSlabSeal.plan.slots.getOrNull(producerSlot.slotIndex))
                    .alignedOffset
            }
            return GPUWgpu4kCoverageMaskResourceEnvelope(
                vertexBytes,
                indexBytes,
                uniformSlabSeal.plan.totalBytes,
                producerUniformOffsets,
                uniformSlabSeal.packedBytesForUpload(),
                coverageMaskConsumerBindGroupRequired,
                borrowBuilderPacked = true,
                borrowedUniformSlabSeal = uniformSlabSeal,
            )
        }
    }
}

internal class GPUWgpu4kCoverageMaskProducerScope(
    val sourceStepIndex: Int,
    producerIndices: List<Int>,
) {
    val producerIndices: List<Int> = producerIndices.toList()

    init {
        require(sourceStepIndex >= 0 && this.producerIndices.isNotEmpty() &&
            this.producerIndices.distinct().size == this.producerIndices.size
        )
    }
}

internal sealed interface GPUWgpu4kCoverageMaskProducerMaterialization {
    class Ready(
        scopeOperands: List<GPUPreparedNativeScopeOperand.Render>,
        val maskView: GPUTextureView,
        val leaseLifecycle: GPUPreparedNativeFrameLeaseLifecycle,
        val borrowedResources: GPUWgpu4kCoverageMaskBorrowedResources,
    ) : GPUWgpu4kCoverageMaskProducerMaterialization {
        val scopeOperands: List<GPUPreparedNativeScopeOperand.Render> = scopeOperands.toList()
    }

    data class Refused(
        val code: String,
        val message: String,
    ) : GPUWgpu4kCoverageMaskProducerMaterialization
}

internal class GPUWgpu4kCoverageMaskBorrowedResources(
    val vertexBuffer: GPUPreparedNativeBufferOperand,
    val indexBuffer: GPUPreparedNativeBufferOperand,
    val uniformBuffer: GPUPreparedNativeBufferOperand,
    val consumerBindGroup: GPUPreparedNativeBindGroupOperand?,
)
