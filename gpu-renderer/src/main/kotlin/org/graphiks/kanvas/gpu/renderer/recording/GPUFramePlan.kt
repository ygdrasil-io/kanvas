package org.graphiks.kanvas.gpu.renderer.recording

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilityFact
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCopyAsDrawImplementationCapability
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUImplementationIdentity
import org.graphiks.kanvas.gpu.renderer.capabilities.dumpLabel
import org.graphiks.kanvas.gpu.renderer.capabilities.dumpLabels
import org.graphiks.kanvas.gpu.renderer.collections.immutableList
import org.graphiks.kanvas.gpu.renderer.collections.immutableMap
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoveragePlan
import org.graphiks.kanvas.gpu.renderer.color.GPUColorFormat
import org.graphiks.kanvas.gpu.renderer.color.GPUColorInterpretation
import org.graphiks.kanvas.gpu.renderer.commands.GPUDrawCommandID
import org.graphiks.kanvas.gpu.renderer.commands.GPUFrameProvenance
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnostic
import org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationSnapshotGroupKey
import org.graphiks.kanvas.gpu.renderer.intermediates.GPUIntermediateIdentity
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlan
import org.graphiks.kanvas.gpu.renderer.passes.GPUClipProducerAuthority
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketID
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchKind
import org.graphiks.kanvas.gpu.renderer.passes.GPUSampleContinuationRequest
import org.graphiks.kanvas.gpu.renderer.passes.GPUSamplePlan
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometry
import org.graphiks.kanvas.gpu.renderer.pipelines.GPUComputePipelineKey
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryAllocation
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryBudgetPlan
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryCategory
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUse
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTextureDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTextureRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUSamplerDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPUImageBindingRequest
import org.graphiks.kanvas.gpu.renderer.resources.GPUImageFrameResourcePlan
import org.graphiks.kanvas.gpu.renderer.resources.GPUMaterialTextureFrameResourcePlan
import org.graphiks.kanvas.gpu.renderer.resources.GPUR8FrameResourcePlan
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceCopyRegion
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourcePreparationRequest
import org.graphiks.kanvas.gpu.renderer.resources.GPUTextureCopyLayout
import org.graphiks.kanvas.gpu.renderer.resources.GPUTextureDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPUTextureFrameResourcePlan
import org.graphiks.kanvas.gpu.renderer.resources.GPUUploadLayout
import org.graphiks.kanvas.gpu.renderer.state.GPULoadStorePlan

/** Validated frame identity. */
@JvmInline
value class GPUFrameID(val value: Long) {
    init {
        require(value >= 0L) { "GPUFrameID.value must be non-negative" }
    }
}

/**
 * Canonical handle-free capability snapshot sealed for exactly one frame/device generation.
 *
 * The constructor is private so destination payloads cannot self-declare backend support. The
 * owner captures this value only from the selected device's real [GPUCapabilities] snapshot.
 */
class GPUFrameCapabilitySeal private constructor(
    val frameId: GPUFrameID,
    val deviceGeneration: GPUDeviceGenerationID,
    val implementation: GPUImplementationIdentity,
    val capabilitySnapshotId: String,
    val capabilitySnapshotHash: String,
    val copyAsDrawCapability: GPUCopyAsDrawImplementationCapability?,
    val sealHash: String,
) {
    companion object {
        internal fun capture(
            frameId: GPUFrameID,
            deviceGeneration: GPUDeviceGenerationID,
            capabilities: GPUCapabilities,
        ): GPUFrameCapabilitySeal {
            val snapshotHash = capabilities.canonicalSnapshotHash()
            val sealHash = CanonicalHashSink("GPUFrameCapabilitySeal/v1")
                .long("frameId", frameId.value)
                .long("deviceGeneration", deviceGeneration.value)
                .string("capabilitySnapshotHash", snapshotHash)
                .finish()
            return GPUFrameCapabilitySeal(
                frameId = frameId,
                deviceGeneration = deviceGeneration,
                implementation = capabilities.implementation,
                capabilitySnapshotId = capabilities.snapshotId,
                capabilitySnapshotHash = snapshotHash,
                copyAsDrawCapability = capabilities.copyAsDrawCapability,
                sealHash = sealHash,
            )
        }
    }
}

/** Validated task identity scoped to one frame task list. */
@JvmInline
value class GPUTaskID(val value: String) {
    init {
        require(value.isNotBlank()) { "GPUTaskID.value must not be blank" }
    }
}

/** Validated CPU-facing readback request identity. */
@JvmInline
value class GPUReadbackRequestID(val value: String) {
    init {
        require(value.isNotBlank()) { "GPUReadbackRequestID.value must not be blank" }
    }
}

/** Validated dependency-use identity owned by recording rather than resource pooling. */
@JvmInline
value class GPUTaskUseToken(val value: String) {
    init {
        require(value.isNotBlank()) { "GPUTaskUseToken.value must not be blank" }
    }
}

/** Stable child provenance consumed by a refused composite scope. */
@JvmInline
value class GPUCompositeProvenanceToken(val value: String) {
    init {
        require(value.isNotBlank()) { "GPUCompositeProvenanceToken.value must not be blank" }
    }
}

/** Pixel layout requested by a CPU-facing evidence readback. */
enum class GPUReadbackPixelFormat {
    Rgba8Unorm,
}

/** Handle-free readback request carried through frame planning. */
data class GPUFrameReadbackRequest(
    val requestId: GPUReadbackRequestID,
    val sourceBounds: GPUPixelBounds,
    val pixelFormat: GPUReadbackPixelFormat,
    val outputColorInterpretation: GPUColorInterpretation,
    val bufferOffsetBytes: Long = 0L,
) {
    init {
        require(bufferOffsetBytes >= 0L) {
            "GPUFrameReadbackRequest.bufferOffsetBytes must be non-negative"
        }
    }
}

/** Handle-free compute dispatch recorded before preflight. */
data class GPUComputeDispatch(
    val programKey: GPUComputePipelineKey,
    val workgroupCountX: Int,
    val workgroupCountY: Int,
    val workgroupCountZ: Int,
) {
    init {
        require(workgroupCountX > 0) { "GPUComputeDispatch.workgroupCountX must be positive" }
        require(workgroupCountY > 0) { "GPUComputeDispatch.workgroupCountY must be positive" }
        require(workgroupCountZ > 0) { "GPUComputeDispatch.workgroupCountZ must be positive" }
    }
}

/** Logical transition between a parent target and an isolated child target. */
enum class GPUTargetTransitionKind {
    EnterChild,
    CompositeChild,
    ReturnToParent,
}

/** Handle-free surface-output identity scoped to one frame. */
@JvmInline
value class GPUSurfaceOutputRef(val value: String) {
    init {
        require(value.isNotBlank()) { "GPUSurfaceOutputRef.value must not be blank" }
    }
}

/** Facts required to acquire the late surface output during preflight. */
data class GPUSurfaceOutputDescriptor(
    val output: GPUSurfaceOutputRef,
    val width: Int,
    val height: Int,
    val format: GPUColorFormat,
    val targetGeneration: Long,
) {
    init {
        require(width > 0) { "GPUSurfaceOutputDescriptor.width must be positive" }
        require(height > 0) { "GPUSurfaceOutputDescriptor.height must be positive" }
        require(targetGeneration >= 0L) {
            "GPUSurfaceOutputDescriptor.targetGeneration must be non-negative"
        }
    }
}

/** Seal proving recording insertion and replay compatibility before linearization. */
data class GPURecordingSeal(
    val recordingId: GPURecordingID,
    val insertionOrder: Long,
    val compatibilityKeyHash: String,
    val replayKeyHash: String,
    val capabilitySealHash: String,
) {
    init {
        require(insertionOrder >= 0L) { "GPURecordingSeal.insertionOrder must be non-negative" }
        require(compatibilityKeyHash.isNotBlank()) {
            "GPURecordingSeal.compatibilityKeyHash must not be blank"
        }
        require(replayKeyHash.isNotBlank()) { "GPURecordingSeal.replayKeyHash must not be blank" }
        require(capabilitySealHash.isNotBlank()) {
            "GPURecordingSeal.capabilitySealHash must not be blank"
        }
    }
}

/** Canonical evidence for a draw packet intentionally elided because its blend is a true NoOp. */
data class GPUFrameElidedNoOpDraw(
    val taskId: GPUTaskID,
    val packetId: GPUDrawPacketID,
    val commandId: GPUDrawCommandID,
    val mode: GPUBlendMode,
    val reason: String,
) {
    init {
        require(reason.isNotBlank()) { "GPUFrameElidedNoOpDraw.reason must not be blank" }
    }
}

/** Execution lane assigned to a frame step without encoding facade commands. */
enum class GPUFrameStepExecutionKind {
    Preflight,
    Encoder,
    DependencyOnly,
    PostSubmitHost,
    RefusalEvidence,
}

/** Closed immutable algebra consumed as the sole semantic input to preflight. */
sealed interface GPUFrameStep {
    val sourceTaskIds: List<GPUTaskID>
    val executionKind: GPUFrameStepExecutionKind

    class RenderPassStep(
        val target: GPUFrameTargetRef,
        val loadStore: GPULoadStorePlan,
        val samplePlan: GPUSamplePlan,
        resourceUses: List<GPUFrameResourceUse> = emptyList(),
        drawPackets: List<GPUDrawPacket>,
        sourceTaskIds: List<GPUTaskID>,
        batches: List<GPUFrameRenderBatch> = listOf(
            GPUFrameRenderBatch(
                batchId = "batch.direct",
                kind = GPUPassBatchKind.Isolated,
                packets = drawPackets,
                sourceTaskIds = sourceTaskIds,
            ),
        ),
        val sampleContinuation: GPUSampleContinuationRequest? = null,
        val depthStencilLoadStore: GPUDepthStencilLoadStorePlan? = null,
        preparedImageBindingsByPacketId:
            Map<GPUDrawPacketID, GPUImageBindingRequest> = emptyMap(),
        preparedTextBindingsByPacketId:
            Map<GPUDrawPacketID, GPUPreparedTextRenderBinding> = emptyMap(),
    ) : GPUFrameStep {
        val drawPackets: List<GPUDrawPacket> = immutableList(drawPackets)
        val resourceUses: List<GPUFrameResourceUse> = immutableList(resourceUses)
        val batches: List<GPUFrameRenderBatch> = immutableList(batches)
        val frameProvenanceByPacketId: Map<GPUDrawPacketID, GPUFrameProvenance> = immutableMap(
            drawPackets.associate { packet -> packet.packetId to packet.frameProvenance },
        )
        val preparedImageBindingsByPacketId:
            Map<GPUDrawPacketID, GPUImageBindingRequest> =
            immutableMap(preparedImageBindingsByPacketId)
        val preparedTextBindingsByPacketId:
            Map<GPUDrawPacketID, GPUPreparedTextRenderBinding> =
            immutableMap(preparedTextBindingsByPacketId)
        override val sourceTaskIds: List<GPUTaskID> = immutableList(sourceTaskIds)
        override val executionKind = GPUFrameStepExecutionKind.Encoder

        init {
            require(drawPackets.isNotEmpty()) {
                "GPUFrameStep.RenderPassStep.drawPackets must not be empty"
            }
            require(drawPackets.map(GPUDrawPacket::packetId).distinct().size == drawPackets.size) {
                "GPUFrameStep.RenderPassStep.drawPackets must have unique packet IDs"
            }
            require(drawPackets.map(GPUDrawPacket::targetStateHash).distinct().size == 1) {
                "GPUFrameStep.RenderPassStep.drawPackets must share one target state"
            }
            require(sourceTaskIds.isNotEmpty() && sourceTaskIds.distinct().size == sourceTaskIds.size) {
                "GPUFrameStep.RenderPassStep.sourceTaskIds must be non-empty and unique"
            }
            require(batches.isNotEmpty()) {
                "GPUFrameStep.RenderPassStep.batches must not be empty"
            }
            require(
                batches.flatMap(GPUFrameRenderBatch::packets).map(GPUDrawPacket::packetId) ==
                    drawPackets.map(GPUDrawPacket::packetId),
            ) {
                "GPUFrameStep.RenderPassStep.batches must exactly partition drawPackets in order"
            }
            require(
                batches.flatMap(GPUFrameRenderBatch::sourceTaskIds).distinct() == sourceTaskIds,
            ) {
                "GPUFrameStep.RenderPassStep batch sourceTaskIds must exactly cover the step sourceTaskIds"
            }
            require(sampleContinuation == null || sampleContinuation.key.samplePlan == samplePlan) {
                "GPUFrameStep.RenderPassStep sample continuation must match the render sample plan"
            }
            val preparedImagePacketIds = drawPackets
                .filter { packet -> packet.semanticPayload is GPUDrawSemanticPayload.SampledImage }
                .map(GPUDrawPacket::packetId)
                .toSet()
            require(preparedImageBindingsByPacketId.keys == preparedImagePacketIds &&
                preparedImageBindingsByPacketId.all { (packetId, binding) ->
                    packetId.value == binding.packetId
                }
            ) {
                "GPUFrameStep.RenderPassStep prepared-image bindings must exactly cover image packets"
            }
            val preparedTextPacketIds = drawPackets
                .filter { packet ->
                    packet.semanticPayload is GPUDrawSemanticPayload.TextA8 ||
                        (packet.semanticPayload is GPUDrawSemanticPayload.ColorGlyph &&
                            packet.semanticPayload.instances.isNotEmpty() &&
                            packet.semanticPayload.material != null)
                }
                .map(GPUDrawPacket::packetId)
                .toSet()
            require(preparedTextBindingsByPacketId.keys == preparedTextPacketIds &&
                preparedTextBindingsByPacketId.all { (packetId, binding) ->
                    packetId == binding.packetId
                }
            ) {
                "GPUFrameStep.RenderPassStep prepared-text bindings must exactly cover text packets"
            }
        }
    }

    class ComputePassStep(
        val target: GPUFrameTargetRef,
        resourceUses: List<GPUFrameResourceUse>,
        dispatches: List<GPUComputeDispatch>,
        sourceTaskIds: List<GPUTaskID>,
    ) : GPUFrameStep {
        val resourceUses: List<GPUFrameResourceUse> = immutableList(resourceUses)
        val dispatches: List<GPUComputeDispatch> = immutableList(dispatches)
        override val sourceTaskIds: List<GPUTaskID> = immutableList(sourceTaskIds)
        override val executionKind = GPUFrameStepExecutionKind.Encoder
    }

    class PrepareResourcesStep(
        requests: List<GPUResourcePreparationRequest>,
        sourceTaskIds: List<GPUTaskID>,
    ) : GPUFrameStep {
        val requests: List<GPUResourcePreparationRequest> = immutableList(requests)
        override val sourceTaskIds: List<GPUTaskID> = immutableList(sourceTaskIds)
        override val executionKind = GPUFrameStepExecutionKind.Preflight
    }

    class UploadResourceStep(
        val staging: GPUFrameBufferRef,
        val destination: GPUFrameResourceRef,
        val layout: GPUUploadLayout,
        sourceTaskIds: List<GPUTaskID>,
        val textureResourcePlan: GPUTextureFrameResourcePlan? = null,
        val destinationKind: GPUUploadDestinationKind =
            if (textureResourcePlan == null) GPUUploadDestinationKind.Buffer else GPUUploadDestinationKind.Texture,
    ) : GPUFrameStep {
        override val sourceTaskIds: List<GPUTaskID> = immutableList(sourceTaskIds)
        override val executionKind = GPUFrameStepExecutionKind.Encoder
        val imageResourcePlan: GPUImageFrameResourcePlan?
            get() = textureResourcePlan as? GPUImageFrameResourcePlan
        val r8ResourcePlan: GPUR8FrameResourcePlan?
            get() = textureResourcePlan as? GPUR8FrameResourcePlan
        val materialResourcePlan: GPUMaterialTextureFrameResourcePlan?
            get() = textureResourcePlan as? GPUMaterialTextureFrameResourcePlan

        init {
            when (val plan = textureResourcePlan) {
                null -> require(destinationKind == GPUUploadDestinationKind.Buffer) {
                    "Texture frame uploads require an exact texture plan; buffer uploads forbid one"
                }
                is GPUImageFrameResourcePlan,
                is GPUR8FrameResourcePlan,
                is GPUMaterialTextureFrameResourcePlan,
                -> {
                    require(destinationKind == GPUUploadDestinationKind.Texture) {
                        "Texture frame uploads require an exact texture plan; buffer uploads forbid one"
                    }
                    require(staging == plan.stagingRef &&
                        destination == plan.frameTextureRef &&
                        layout == plan.uploadTaskLayout
                    ) {
                        "Texture frame upload must retain its exact plan authority"
                    }
                }
            }
        }
    }

    class CopyResourceStep(
        val source: GPUFrameResourceRef,
        val destination: GPUFrameResourceRef,
        regions: List<GPUResourceCopyRegion>,
        sourceTaskIds: List<GPUTaskID>,
    ) : GPUFrameStep {
        val regions: List<GPUResourceCopyRegion> = immutableList(regions)
        override val sourceTaskIds: List<GPUTaskID> = immutableList(sourceTaskIds)
        override val executionKind = GPUFrameStepExecutionKind.Encoder
    }

    class DependencyBarrierStep(
        orderedUseTokens: List<GPUTaskUseToken>,
        val reasonCode: String,
        sourceTaskIds: List<GPUTaskID>,
    ) : GPUFrameStep {
        val orderedUseTokens: List<GPUTaskUseToken> = immutableList(orderedUseTokens)
        override val sourceTaskIds: List<GPUTaskID> = immutableList(sourceTaskIds)
        override val executionKind = GPUFrameStepExecutionKind.DependencyOnly

        init {
            require(reasonCode.isNotBlank()) {
                "GPUFrameStep.DependencyBarrierStep.reasonCode must not be blank"
            }
        }
    }

    class CopyDestinationStep(
        val source: GPUFrameTargetRef,
        val sourceKey: GPUDestinationSnapshotGroupKey,
        val snapshot: GPUFrameTextureRef,
        val logicalBounds: GPUPixelBounds,
        val copyLayout: GPUTextureCopyLayout,
        consumers: List<GPUDestinationSnapshotConsumerRef>,
        sourceTaskIds: List<GPUTaskID>,
    ) : GPUFrameStep {
        val consumers: List<GPUDestinationSnapshotConsumerRef> = immutableList(consumers)
        override val sourceTaskIds: List<GPUTaskID> = immutableList(sourceTaskIds)
        override val executionKind = GPUFrameStepExecutionKind.Encoder
    }

    class CopyAsDrawMaterializationStep(
        val source: GPUFrameTextureRef,
        val sourceKey: GPUDestinationSnapshotGroupKey,
        val sourceIntermediate: GPUIntermediateIdentity,
        val snapshot: GPUFrameTextureRef,
        val logicalBounds: GPUPixelBounds,
        val capabilitySealHash: String,
        consumers: List<GPUDestinationSnapshotConsumerRef>,
        sourceTaskIds: List<GPUTaskID>,
    ) : GPUFrameStep {
        val consumers: List<GPUDestinationSnapshotConsumerRef> = immutableList(consumers)
        override val sourceTaskIds: List<GPUTaskID> = immutableList(sourceTaskIds)
        override val executionKind = GPUFrameStepExecutionKind.Encoder

        init {
            require(capabilitySealHash.isNotBlank()) {
                "CopyAsDrawMaterializationStep.capabilitySealHash must not be blank"
            }
        }
    }

    class TargetTransitionStep(
        val parent: GPUFrameTargetRef,
        val child: GPUFrameTargetRef,
        val transitionKind: GPUTargetTransitionKind,
        sourceTaskIds: List<GPUTaskID>,
    ) : GPUFrameStep {
        override val sourceTaskIds: List<GPUTaskID> = immutableList(sourceTaskIds)
        override val executionKind = GPUFrameStepExecutionKind.DependencyOnly
    }

    class ReadbackCopyStep(
        val source: GPUFrameTargetRef,
        val staging: GPUFrameBufferRef,
        val request: GPUFrameReadbackRequest,
        sourceTaskIds: List<GPUTaskID>,
    ) : GPUFrameStep {
        override val sourceTaskIds: List<GPUTaskID> = immutableList(sourceTaskIds)
        override val executionKind = GPUFrameStepExecutionKind.Encoder
    }

    class AcquireSurfaceOutput(
        val descriptor: GPUSurfaceOutputDescriptor,
        sourceTaskIds: List<GPUTaskID>,
    ) : GPUFrameStep {
        override val sourceTaskIds: List<GPUTaskID> = immutableList(sourceTaskIds)
        override val executionKind = GPUFrameStepExecutionKind.Preflight
    }

    class SurfaceBlitRenderPassStep(
        val scene: GPUFrameTargetRef,
        val output: GPUSurfaceOutputRef,
        sourceTaskIds: List<GPUTaskID>,
    ) : GPUFrameStep {
        override val sourceTaskIds: List<GPUTaskID> = immutableList(sourceTaskIds)
        override val executionKind = GPUFrameStepExecutionKind.Encoder
    }

    class PostSubmitPresentAction(
        val output: GPUSurfaceOutputRef,
        sourceTaskIds: List<GPUTaskID>,
    ) : GPUFrameStep {
        override val sourceTaskIds: List<GPUTaskID> = immutableList(sourceTaskIds)
        override val executionKind = GPUFrameStepExecutionKind.PostSubmitHost
    }

    class RefusedLeafDrawStep(
        val commandId: GPUDrawCommandID,
        diagnostic: GPUDiagnostic,
        sourceTaskIds: List<GPUTaskID>,
    ) : GPUFrameStep {
        val diagnostic: GPUDiagnostic = diagnostic.snapshot()
        override val sourceTaskIds: List<GPUTaskID> = immutableList(sourceTaskIds)
        override val executionKind = GPUFrameStepExecutionKind.RefusalEvidence
    }

    class RefusedCompositeCommandStep(
        val commandId: GPUDrawCommandID,
        provenanceTokens: List<GPUCompositeProvenanceToken>,
        diagnostic: GPUDiagnostic,
        sourceTaskIds: List<GPUTaskID>,
    ) : GPUFrameStep {
        val provenanceTokens: List<GPUCompositeProvenanceToken> = immutableList(provenanceTokens)
        val diagnostic: GPUDiagnostic = diagnostic.snapshot()
        override val sourceTaskIds: List<GPUTaskID> = immutableList(sourceTaskIds)
        override val executionKind = GPUFrameStepExecutionKind.RefusalEvidence
    }

    /** Materializes or reuses an offscreen layer target before rendering the layer children. */
    class LayerTargetPrepareStep(
        val targetLabel: String,
        val descriptorHash: String,
        val usageLabel: String,
        val byteEstimate: Long,
        sourceTaskIds: List<GPUTaskID>,
    ) : GPUFrameStep {
        override val sourceTaskIds: List<GPUTaskID> = immutableList(sourceTaskIds)
        override val executionKind = GPUFrameStepExecutionKind.Preflight

        init {
            require(targetLabel.isNotBlank()) { "GPUFrameStep.LayerTargetPrepareStep.targetLabel must not be blank" }
            require(descriptorHash.isNotBlank()) { "GPUFrameStep.LayerTargetPrepareStep.descriptorHash must not be blank" }
            require(usageLabel.isNotBlank()) { "GPUFrameStep.LayerTargetPrepareStep.usageLabel must not be blank" }
            require(byteEstimate >= 0L) { "GPUFrameStep.LayerTargetPrepareStep.byteEstimate must be non-negative" }
        }
    }

    /** Renders the layer children into the isolated layer target scope. */
    class LayerChildrenRenderStep(
        val scopeLabel: String,
        val targetLabel: String,
        val childrenLabel: String,
        val tokenLabel: String,
        sourceTaskIds: List<GPUTaskID>,
    ) : GPUFrameStep {
        override val sourceTaskIds: List<GPUTaskID> = immutableList(sourceTaskIds)
        // The children pass is realized by the layer-target RenderPassStep; this step is
        // ordering evidence only, so it must not claim an encoder scope.
        override val executionKind = GPUFrameStepExecutionKind.DependencyOnly

        init {
            require(scopeLabel.isNotBlank()) { "GPUFrameStep.LayerChildrenRenderStep.scopeLabel must not be blank" }
            require(targetLabel.isNotBlank()) { "GPUFrameStep.LayerChildrenRenderStep.targetLabel must not be blank" }
            require(childrenLabel.isNotBlank()) { "GPUFrameStep.LayerChildrenRenderStep.childrenLabel must not be blank" }
            require(tokenLabel.isNotBlank()) { "GPUFrameStep.LayerChildrenRenderStep.tokenLabel must not be blank" }
        }
    }

    /** Composites an isolated layer source back into its parent target. */
    class LayerCompositeRenderStep(
        val sourceLabel: String,
        val parentTargetLabel: String,
        val blendModeLabel: String,
        val blendPlan: GPUBlendPlan,
        val routeLabel: String,
        val tokenLabel: String,
        val alpha: Float,
        val clipLabel: String?,
        sourceTaskIds: List<GPUTaskID>,
    ) : GPUFrameStep {
        override val sourceTaskIds: List<GPUTaskID> = immutableList(sourceTaskIds)
        override val executionKind = GPUFrameStepExecutionKind.Encoder

        init {
            require(sourceLabel.isNotBlank()) { "GPUFrameStep.LayerCompositeRenderStep.sourceLabel must not be blank" }
            require(parentTargetLabel.isNotBlank()) { "GPUFrameStep.LayerCompositeRenderStep.parentTargetLabel must not be blank" }
            require(blendModeLabel.isNotBlank()) { "GPUFrameStep.LayerCompositeRenderStep.blendModeLabel must not be blank" }
            require(routeLabel.isNotBlank()) { "GPUFrameStep.LayerCompositeRenderStep.routeLabel must not be blank" }
            require(tokenLabel.isNotBlank()) { "GPUFrameStep.LayerCompositeRenderStep.tokenLabel must not be blank" }
            require(alpha in 0f..1f) { "GPUFrameStep.LayerCompositeRenderStep.alpha must be in 0f..1f" }
        }
    }
}

/** One adjacent batch retained inside a single render-pass step. */
class GPUFrameRenderBatch(
    val batchId: String,
    val kind: GPUPassBatchKind,
    packets: List<GPUDrawPacket>,
    sourceTaskIds: List<GPUTaskID>,
) {
    val packets: List<GPUDrawPacket> = immutableList(packets)
    val sourceTaskIds: List<GPUTaskID> = immutableList(sourceTaskIds)

    init {
        require(batchId.isNotBlank()) { "GPUFrameRenderBatch.batchId must not be blank" }
        require(packets.isNotEmpty()) { "GPUFrameRenderBatch.packets must not be empty" }
        require(sourceTaskIds.isNotEmpty() && sourceTaskIds.distinct().size == sourceTaskIds.size) {
            "GPUFrameRenderBatch.sourceTaskIds must be non-empty and unique"
        }
    }
}

/** Immutable deterministic result of task-list linearization. */
class GPUFramePlan(
    val frameId: GPUFrameID,
    val capabilitySeal: GPUFrameCapabilitySeal,
    recordingSeals: List<GPURecordingSeal>,
    steps: List<GPUFrameStep>,
    memoryBudget: GPUFrameMemoryBudgetPlan,
    diagnostics: List<GPUDiagnostic>,
    dependencies: List<GPUTaskDependency> = emptyList(),
    phaseOrder: List<GPUTaskPhase> = GPUTaskPhase.entries,
    elidedNoOpDraws: List<GPUFrameElidedNoOpDraw> = emptyList(),
    val atomicallyRefused: Boolean = false,
) {
    val recordingSeals: List<GPURecordingSeal> = immutableList(recordingSeals)
    val steps: List<GPUFrameStep> = immutableList(steps)
    val memoryBudget: GPUFrameMemoryBudgetPlan = memoryBudget.snapshotForFramePlan()
    val diagnostics: List<GPUDiagnostic> = immutableList(diagnostics.map(GPUDiagnostic::snapshot))
    val dependencies: List<GPUTaskDependency> = immutableList(dependencies)
    val phaseOrder: List<GPUTaskPhase> = immutableList(phaseOrder)
    val elidedNoOpDraws: List<GPUFrameElidedNoOpDraw> = immutableList(elidedNoOpDraws)

    init {
        require(frameId == capabilitySeal.frameId) {
            "GPUFramePlan.frameId must match GPUFrameCapabilitySeal.frameId"
        }
        require(phaseOrder.distinct().size == phaseOrder.size) {
            "GPUFramePlan.phaseOrder must not contain duplicates"
        }
        require(!atomicallyRefused || steps.isEmpty()) {
            "GPUFramePlan atomically refused plans must not retain steps"
        }
        require(!atomicallyRefused || diagnostics.any(GPUDiagnostic::isTerminal)) {
            "GPUFramePlan atomically refused plans require a terminal diagnostic"
        }
    }

    fun dumpLines(): List<String> =
        listOf(
            "frame id=${frameId.value} capabilitySeal=${capabilitySeal.sealHash} " +
                "refused=$atomicallyRefused seals=${recordingSeals.size} " +
                "steps=${steps.size} diagnostics=${diagnostics.size}",
            memoryBudget.dumpLine(),
            capabilitySeal.dumpLine(),
            "phase-order ${phaseOrder.joinToString(",", transform = GPUTaskPhase::name)}",
        ) +
            recordingSeals.map { seal ->
                "seal recording=${seal.recordingId.value} insertion=${seal.insertionOrder} " +
                "compatibility=${seal.compatibilityKeyHash} replay=${seal.replayKeyHash} " +
                    "capabilitySeal=${seal.capabilitySealHash}"
            } +
            dependencies.mapIndexed { index, dependency -> dependency.dumpLine(index) } +
            elidedNoOpDraws.mapIndexed { index, evidence -> evidence.dumpLine(index) } +
            steps.mapIndexed { index, step -> step.dumpLine(index) } +
            diagnostics.mapIndexed { index, diagnostic -> diagnostic.dumpLine("diagnostic[$index]") }

    fun stableHash(): String {
        return canonicalPreimageHash()
    }
}

private fun GPUDiagnostic.snapshot(): GPUDiagnostic = copy(facts = immutableMap(facts))

private fun GPUTaskDependency.dumpLine(index: Int): String =
    "dependency index=$index kind=$dependencyKind from=${fromTaskId.value} to=${toTaskId.value} " +
        "useToken=${useToken?.value ?: "none"} atomicGroup=${atomicGroupId?.value ?: "none"} reason=$reasonCode"

private fun GPUFrameCapabilitySeal.dumpLine(): String {
    val copyAsDraw = copyAsDrawCapability?.let { capability ->
        "${capability.implementationId}@${capability.implementationVersion}:${capability.available}"
    } ?: "none"
    return "capability frame=${frameId.value} deviceGeneration=${deviceGeneration.value} " +
        "facade=${implementation.facadeName} implementation=${implementation.implementationName} " +
        "adapter=${implementation.adapterName} device=${implementation.deviceName} " +
        "vendorId=${implementation.vendorId ?: "none"} deviceId=${implementation.deviceId ?: "none"} " +
        "snapshotId=$capabilitySnapshotId snapshotHash=$capabilitySnapshotHash " +
        "copyAsDraw=$copyAsDraw seal=$sealHash"
}

private fun GPUFrameElidedNoOpDraw.dumpLine(index: Int): String =
    "elided-noop index=$index task=${taskId.value} packet=${packetId.value} " +
        "command=${commandId.value} mode=${mode.name} reason=$reason"

internal fun GPUFrameMemoryBudgetPlan.snapshotForFramePlan(): GPUFrameMemoryBudgetPlan =
    copy(
        allocations = immutableList(allocations),
        categoryTotals = immutableMap(categoryTotals),
        deviceLimitFacts = immutableList(deviceLimitFacts),
        diagnostic = diagnostic?.snapshot(),
    )

/**
 * Canonical, handle-free identity of every capability fact that can affect renderer validity.
 *
 * Frame sealing and prepared material compilation deliberately share this single authority.
 */
fun GPUCapabilities.canonicalSnapshotHash(): String =
    CanonicalHashSink("GPUCapabilities/v3").apply {
        implementation("implementation", implementation)
        string("snapshotId", snapshotId)
        list("facts", facts.sortedWith(capabilityFactComparator)) { fact(it) }
        list("knownUnsupportedFacts", knownUnsupportedFacts.sortedWith(capabilityFactComparator)) {
            fact(it)
        }
        nullable("limits", limits) { limits ->
            tag("GPULimits")
            long("maxTextureDimension2D", limits.maxTextureDimension2D)
            long("copyBytesPerRowAlignment", limits.copyBytesPerRowAlignment)
            long("minUniformBufferOffsetAlignment", limits.minUniformBufferOffsetAlignment)
            nullable("maxBufferSize", limits.maxBufferSize) { maxBufferSize ->
                long("value", maxBufferSize)
            }
            nullable(
                "maxDynamicUniformBuffersPerPipelineLayout",
                limits.maxDynamicUniformBuffersPerPipelineLayout,
            ) { maxDynamicUniformBuffersPerPipelineLayout ->
                long("value", maxDynamicUniformBuffersPerPipelineLayout)
            }
            string("source", limits.source)
        }
        list("supportedTextureFormats", supportedTextureFormats.map { it.dumpLabel() }.sorted()) {
            string("format", it)
        }
        nullable("supportedTextureUsage", supportedTextureUsage) { usage ->
            list("labels", usage.dumpLabels().sorted()) { label ->
                string("usage", label)
            }
        }
        list(
            "textureFormatSampleSupport",
            textureFormatSampleSupport.entries.sortedBy { it.key.dumpLabel() },
        ) { (format, support) ->
            string("format", format.dumpLabel())
            list("renderAttachmentSampleCounts", support.renderAttachmentSampleCounts.sorted()) {
                int("sampleCount", it)
            }
            list("resolveSourceSampleCounts", support.resolveSourceSampleCounts.sorted()) {
                int("sampleCount", it)
            }
        }
        list("rendererFeatures", rendererFeatures.map { it.dumpLabel }.sorted()) {
            string("feature", it)
        }
        nullable("copyAsDraw", copyAsDrawCapability) { capability ->
            copyAsDrawCapability("value", capability)
        }
    }.finish()

private val capabilityFactComparator: Comparator<GPUCapabilityFact> =
    compareBy<GPUCapabilityFact>(
        { it.name },
        { it.source },
        { it.value },
        { it.affectsValidity },
        { it.evidenceLabel },
    )

private fun GPUFramePlan.canonicalPreimageHash(): String =
    CanonicalHashSink("GPUFramePlan/v3").apply {
        long("frameId", frameId.value)
        capabilitySeal("capabilitySeal", capabilitySeal)
        bool("atomicallyRefused", atomicallyRefused)
        list("recordingSeals", recordingSeals) { seal ->
            tag("GPURecordingSeal")
            string("recordingId", seal.recordingId.value)
            long("insertionOrder", seal.insertionOrder)
            string("compatibilityKeyHash", seal.compatibilityKeyHash)
            string("replayKeyHash", seal.replayKeyHash)
            string("capabilitySealHash", seal.capabilitySealHash)
        }
        list("phaseOrder", phaseOrder) { phase -> string("phase", phase.name) }
        list("dependencies", dependencies) { dependency ->
            tag("GPUTaskDependency")
            string("fromTaskId", dependency.fromTaskId.value)
            string("toTaskId", dependency.toTaskId.value)
            string("dependencyKind", dependency.dependencyKind)
            nullableString("useToken", dependency.useToken?.value)
            nullableString("atomicGroupId", dependency.atomicGroupId?.value)
            string("reasonCode", dependency.reasonCode)
        }
        list("elidedNoOpDraws", elidedNoOpDraws) { evidence ->
            tag("GPUFrameElidedNoOpDraw")
            string("taskId", evidence.taskId.value)
            string("packetId", evidence.packetId.value)
            int("commandId", evidence.commandId.value)
            string("mode", evidence.mode.name)
            string("reason", evidence.reason)
        }
        memoryBudget("memoryBudget", memoryBudget)
        list("steps", steps) { step(it) }
        list("diagnostics", diagnostics) { diagnostic("diagnostic", it) }
    }.finish()

private class CanonicalHashSink(rootTag: String) {
    private val bytes = ByteArrayOutputStream()
    private val output = DataOutputStream(bytes)

    init {
        string("root", rootTag)
    }

    fun tag(value: String): CanonicalHashSink = string("type", value)

    fun string(name: String, value: String): CanonicalHashSink = apply {
        field(1, name)
        val encoded = value.toByteArray(Charsets.UTF_8)
        output.writeInt(encoded.size)
        output.write(encoded)
    }

    fun nullableString(name: String, value: String?): CanonicalHashSink = apply {
        field(2, name)
        output.writeBoolean(value != null)
        if (value != null) {
            val encoded = value.toByteArray(Charsets.UTF_8)
            output.writeInt(encoded.size)
            output.write(encoded)
        }
    }

    fun int(name: String, value: Int): CanonicalHashSink = apply {
        field(3, name)
        output.writeInt(value)
    }

    fun long(name: String, value: Long): CanonicalHashSink = apply {
        field(4, name)
        output.writeLong(value)
    }

    fun bool(name: String, value: Boolean): CanonicalHashSink = apply {
        field(5, name)
        output.writeBoolean(value)
    }

    fun byteArray(name: String, value: ByteArray): CanonicalHashSink = apply {
        field(9, name)
        output.writeInt(value.size)
        output.write(value)
    }

    fun <T> list(
        name: String,
        values: List<T>,
        encode: CanonicalHashSink.(T) -> Unit,
    ): CanonicalHashSink = apply {
        field(6, name)
        output.writeInt(values.size)
        values.forEach { value ->
            field(7, "item")
            encode(value)
        }
    }

    fun <T> nullable(
        name: String,
        value: T?,
        encode: CanonicalHashSink.(T) -> Unit,
    ): CanonicalHashSink = apply {
        field(8, name)
        output.writeBoolean(value != null)
        if (value != null) encode(value)
    }

    fun finish(): String {
        output.flush()
        return MessageDigest.getInstance("SHA-256")
            .digest(bytes.toByteArray())
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    private fun field(kind: Int, name: String) {
        output.writeByte(kind)
        val encoded = name.toByteArray(Charsets.UTF_8)
        output.writeInt(encoded.size)
        output.write(encoded)
    }
}

private fun CanonicalHashSink.implementation(name: String, value: GPUImplementationIdentity) {
    tag(name)
    tag("GPUImplementationIdentity")
    string("facadeName", value.facadeName)
    string("implementationName", value.implementationName)
    string("adapterName", value.adapterName)
    string("deviceName", value.deviceName)
    nullableString("vendorId", value.vendorId)
    nullableString("deviceId", value.deviceId)
}

private fun CanonicalHashSink.fact(value: GPUCapabilityFact) {
    tag("GPUCapabilityFact")
    string("name", value.name)
    string("source", value.source)
    string("value", value.value)
    bool("affectsValidity", value.affectsValidity)
    string("evidenceLabel", value.evidenceLabel)
}

private fun CanonicalHashSink.copyAsDrawCapability(
    name: String,
    value: GPUCopyAsDrawImplementationCapability,
) {
    tag(name)
    tag("GPUCopyAsDrawImplementationCapability")
    string("implementationId", value.implementationId)
    string("implementationVersion", value.implementationVersion)
    bool("available", value.available)
}

private fun CanonicalHashSink.capabilitySeal(name: String, value: GPUFrameCapabilitySeal) {
    tag(name)
    tag("GPUFrameCapabilitySeal")
    long("frameId", value.frameId.value)
    long("deviceGeneration", value.deviceGeneration.value)
    implementation("implementation", value.implementation)
    string("capabilitySnapshotId", value.capabilitySnapshotId)
    string("capabilitySnapshotHash", value.capabilitySnapshotHash)
    nullable("copyAsDrawCapability", value.copyAsDrawCapability) {
        copyAsDrawCapability("value", it)
    }
    string("sealHash", value.sealHash)
}

private fun CanonicalHashSink.memoryBudget(name: String, value: GPUFrameMemoryBudgetPlan) {
    tag(name)
    tag("GPUFrameMemoryBudgetPlan")
    long("peakFrameTransientBytes", value.peakFrameTransientBytes)
    long("targetResidentBytes", value.targetResidentBytes)
    list("categoryTotals", GPUFrameMemoryCategory.entries) { category ->
        string("category", category.name)
        long("bytes", value.categoryTotals[category] ?: 0L)
    }
    list("deviceLimitFacts", value.deviceLimitFacts) { fact(it) }
    long("configuredAggregateBudgetBytes", value.configuredAggregateBudgetBytes)
    nullable("diagnostic", value.diagnostic) { diagnostic("value", it) }
    list("allocations", value.allocations) { memoryAllocation(it) }
}

private fun CanonicalHashSink.memoryAllocation(value: GPUFrameMemoryAllocation) {
    tag("GPUFrameMemoryAllocation")
    string("label", value.label)
    string("category", value.category.name)
    long("bytes", value.bytes)
    string("resourceKind", value.resourceKind.name)
    nullable("extent", value.extent) { bounds("value", it) }
}

private fun CanonicalHashSink.step(value: GPUFrameStep) {
    tag(value.canonicalTypeTag())
    string("executionKind", value.executionKind.name)
    list("sourceTaskIds", value.sourceTaskIds) { string("taskId", it.value) }
    when (value) {
        is GPUFrameStep.RenderPassStep -> {
            resourceRef("target", value.target)
            list("resourceUses", value.resourceUses) { resourceUse(it) }
            loadStore("loadStore", value.loadStore)
            nullable("depthStencilLoadStore", value.depthStencilLoadStore) { authority ->
                when (authority) {
                    is GPUDepthStencilLoadStorePlan.WritableStencil -> {
                        tag("WritableStencil")
                        string("loadOperation", authority.loadOperation.name)
                        string("storeOperation", authority.storeOperation.name)
                        nullable("clearValue", authority.clearValue) { long("value", it.toLong()) }
                    }
                    GPUDepthStencilLoadStorePlan.ReadOnlyKeep -> tag("ReadOnlyKeep")
                }
            }
            samplePlan("samplePlan", value.samplePlan)
            nullable("sampleContinuation", value.sampleContinuation) { continuation ->
                string("target", continuation.key.target.value)
                long("targetGeneration", continuation.key.targetGeneration)
                long("deviceGeneration", continuation.key.deviceGeneration.value)
                string("colorFormat", continuation.key.colorFormat.value)
                string("colorInterpretation", continuation.key.colorInterpretation.value)
                string("samplePlan", continuation.key.samplePlan.specializationKey)
                string("attachmentAuthority", continuation.key.attachmentAuthority.name)
                string("colorAttachment", continuation.key.colorAttachment.value)
                nullable("depthStencilAttachment", continuation.key.depthStencilAttachment) {
                    string("target", it.value)
                }
                string("loadTransition", continuation.loadTransition.name)
                string("storeAction", continuation.storeAction.name)
                string("resolveAction", continuation.resolveAction.name)
            }
            list("batches", value.batches) { batch ->
                string("batchId", batch.batchId)
                string("kind", batch.kind.name)
                list("sourceTaskIds", batch.sourceTaskIds) { string("taskId", it.value) }
                list("packets", batch.packets) { packet(it) }
            }
            list("drawPackets", value.drawPackets) { packet(it) }
            list(
                "preparedImageBindings",
                value.drawPackets
                    .filter { packet -> packet.semanticPayload is GPUDrawSemanticPayload.SampledImage }
                    .map { packet ->
                        value.preparedImageBindingsByPacketId.getValue(packet.packetId)
                },
            ) { preparedImageBinding(it) }
            if (value.preparedTextBindingsByPacketId.isNotEmpty()) {
                list(
                    "preparedTextBindings",
                    value.drawPackets
                        .filter { packet ->
                            packet.semanticPayload is GPUDrawSemanticPayload.TextA8 ||
                                (packet.semanticPayload is GPUDrawSemanticPayload.ColorGlyph &&
                                    packet.semanticPayload.instances.isNotEmpty() &&
                                    packet.semanticPayload.material != null)
                        }
                        .map { packet ->
                            value.preparedTextBindingsByPacketId.getValue(packet.packetId)
                        },
                ) { preparedTextBinding(it) }
            }
        }
        is GPUFrameStep.ComputePassStep -> {
            resourceRef("target", value.target)
            list("resourceUses", value.resourceUses) { resourceUse(it) }
            list("dispatches", value.dispatches) { dispatch(it) }
        }
        is GPUFrameStep.PrepareResourcesStep ->
            list("requests", value.requests) { preparationRequest(it) }
        is GPUFrameStep.UploadResourceStep -> {
            resourceRef("staging", value.staging)
            resourceRef("destination", value.destination)
            string("destinationKind", value.destinationKind.name)
            long("sourceOffsetBytes", value.layout.sourceOffsetBytes)
            long("bytesPerRow", value.layout.bytesPerRow)
            int("rowsPerImage", value.layout.rowsPerImage)
            long("byteSize", value.layout.byteSize)
            when (val plan = value.textureResourcePlan) {
                null ->
                    nullable("preparedImagePlan", value.imageResourcePlan) { imageResourcePlan(it) }
                is GPUImageFrameResourcePlan ->
                    nullable("preparedImagePlan", plan) { imageResourcePlan(it) }
                is GPUR8FrameResourcePlan ->
                    nullable("preparedR8Plan", plan) { r8ResourcePlan(it) }
                is GPUMaterialTextureFrameResourcePlan ->
                    nullable("preparedMaterialTexturePlan", plan) {
                        materialTextureResourcePlan(it)
                    }
            }
        }
        is GPUFrameStep.CopyResourceStep -> {
            resourceRef("source", value.source)
            resourceRef("destination", value.destination)
            list("regions", value.regions) { region ->
                long("sourceOffsetBytes", region.sourceOffsetBytes)
                long("destinationOffsetBytes", region.destinationOffsetBytes)
                nullable("logicalBounds", region.logicalBounds) { bounds("value", it) }
                long("byteSize", region.byteSize)
            }
        }
        is GPUFrameStep.DependencyBarrierStep -> {
            string("reasonCode", value.reasonCode)
            list("orderedUseTokens", value.orderedUseTokens) { string("token", it.value) }
        }
        is GPUFrameStep.CopyDestinationStep -> {
            resourceRef("source", value.source)
            destinationSourceKey("sourceKey", value.sourceKey)
            resourceRef("snapshot", value.snapshot)
            bounds("logicalBounds", value.logicalBounds)
            long("bytesPerRow", value.copyLayout.bytesPerRow)
            int("rowsPerImage", value.copyLayout.rowsPerImage)
            list("consumers", value.consumers) { destinationConsumer(it) }
        }
        is GPUFrameStep.CopyAsDrawMaterializationStep -> {
            resourceRef("source", value.source)
            destinationSourceKey("sourceKey", value.sourceKey)
            string("sourceIntermediate", value.sourceIntermediate.value)
            resourceRef("snapshot", value.snapshot)
            bounds("logicalBounds", value.logicalBounds)
            string("capabilitySealHash", value.capabilitySealHash)
            list("consumers", value.consumers) { destinationConsumer(it) }
        }
        is GPUFrameStep.TargetTransitionStep -> {
            resourceRef("parent", value.parent)
            resourceRef("child", value.child)
            string("transitionKind", value.transitionKind.name)
        }
        is GPUFrameStep.ReadbackCopyStep -> {
            resourceRef("source", value.source)
            resourceRef("staging", value.staging)
            string("requestId", value.request.requestId.value)
            bounds("sourceBounds", value.request.sourceBounds)
            string("pixelFormat", value.request.pixelFormat.name)
            string("outputColorInterpretation", value.request.outputColorInterpretation.value)
            long("bufferOffsetBytes", value.request.bufferOffsetBytes)
        }
        is GPUFrameStep.AcquireSurfaceOutput -> surfaceDescriptor(value.descriptor)
        is GPUFrameStep.SurfaceBlitRenderPassStep -> {
            resourceRef("scene", value.scene)
            string("output", value.output.value)
        }
        is GPUFrameStep.PostSubmitPresentAction -> string("output", value.output.value)
        is GPUFrameStep.RefusedLeafDrawStep -> {
            int("commandId", value.commandId.value)
            diagnostic("diagnostic", value.diagnostic)
        }
        is GPUFrameStep.RefusedCompositeCommandStep -> {
            int("commandId", value.commandId.value)
            list("provenanceTokens", value.provenanceTokens) { string("token", it.value) }
            diagnostic("diagnostic", value.diagnostic)
        }
        is GPUFrameStep.LayerTargetPrepareStep -> {
            string("targetLabel", value.targetLabel)
            string("descriptorHash", value.descriptorHash)
            string("usageLabel", value.usageLabel)
            long("byteEstimate", value.byteEstimate)
        }
        is GPUFrameStep.LayerChildrenRenderStep -> {
            string("scopeLabel", value.scopeLabel)
            string("targetLabel", value.targetLabel)
            string("childrenLabel", value.childrenLabel)
            string("tokenLabel", value.tokenLabel)
        }
        is GPUFrameStep.LayerCompositeRenderStep -> {
            string("sourceLabel", value.sourceLabel)
            string("parentTargetLabel", value.parentTargetLabel)
            string("blendModeLabel", value.blendModeLabel)
            blendPlan(value.blendPlan)
            string("routeLabel", value.routeLabel)
            string("tokenLabel", value.tokenLabel)
            long("alphaRawBits", value.alpha.toRawBits().toLong())
            nullableString("clipLabel", value.clipLabel)
        }
    }
}

private fun CanonicalHashSink.imageResourcePlan(value: GPUImageFrameResourcePlan) {
    tag("GPUPreparedImageFrameResourcePlan")
    string("stagingRef", value.stagingRef.value)
    string("textureRef", value.textureRef.value)
    string("frameTextureRef", value.frameTextureRef.value)
    string("uniformRef", value.uniformRef.value)
    textureDescriptor("textureDescriptor", value.textureDescriptor)
    tag("GPUPreparedImageUploadLayout")
    long("sourceBytesPerRow", value.uploadLayout.sourceBytesPerRow)
    long("logicalBytesPerRow", value.uploadLayout.logicalBytesPerRow)
    long("bytesPerRow", value.uploadLayout.bytesPerRow)
    int("rowsPerImage", value.uploadLayout.rowsPerImage)
    int("width", value.uploadLayout.width)
    int("height", value.uploadLayout.height)
    byteArray("logicalPayloadBytes", value.uploadLayout.logicalBytesForHash())
    tag("GPUUploadLayout")
    long("sourceOffsetBytes", value.uploadTaskLayout.sourceOffsetBytes)
    long("bytesPerRow", value.uploadTaskLayout.bytesPerRow)
    int("rowsPerImage", value.uploadTaskLayout.rowsPerImage)
    long("byteSize", value.uploadTaskLayout.byteSize)
    list("bindingRequests", value.bindingRequests) { preparedImageBinding(it) }
    list("preparationRequests", value.preparationRequests) { preparationRequest(it) }
    list("memoryAllocations", value.memoryAllocations) { memoryAllocation(it) }
}

private fun CanonicalHashSink.r8ResourcePlan(value: GPUR8FrameResourcePlan) {
    tag("GPUR8FrameResourcePlan")
    string("stagingRef", value.stagingRef.value)
    string("frameTextureRef", value.frameTextureRef.value)
    tag("GPUUploadLayout")
    long("sourceOffsetBytes", value.uploadTaskLayout.sourceOffsetBytes)
    long("bytesPerRow", value.uploadTaskLayout.bytesPerRow)
    int("rowsPerImage", value.uploadTaskLayout.rowsPerImage)
    long("byteSize", value.uploadTaskLayout.byteSize)
    string("artifactKey", value.artifactKey)
    int("artifactWidth", value.artifactWidth)
    int("artifactHeight", value.artifactHeight)
    int("artifactRowBytes", value.artifactRowBytes)
    long("artifactGeneration", value.artifactGeneration)
    string("artifactContentHash", value.artifactContentHash)
    byteArray("uploadBytes", value.bytesForUpload())
    list("preparationRequests", value.preparationRequests) { preparationRequest(it) }
    list("memoryAllocations", value.memoryAllocations) { memoryAllocation(it) }
}

private fun CanonicalHashSink.materialTextureResourcePlan(
    value: GPUMaterialTextureFrameResourcePlan,
) {
    tag("GPUPreparedMaterialTextureFrameResourcePlan")
    string("stagingRef", value.stagingRef.value)
    string("frameTextureRef", value.frameTextureRef.value)
    long("sourceOffsetBytes", value.uploadTaskLayout.sourceOffsetBytes)
    long("bytesPerRow", value.uploadTaskLayout.bytesPerRow)
    int("rowsPerImage", value.uploadTaskLayout.rowsPerImage)
    long("byteSize", value.uploadTaskLayout.byteSize)
    string("resourceKey", value.resourceKey)
    int("width", value.width)
    int("height", value.height)
    string("samplingFilterMode", value.samplingFilterMode)
    bool("alphaOnly", value.alphaOnly)
    string("contentHash", value.contentHash)
    byteArray("uploadBytes", value.bytesForUpload())
    list("preparationRequests", value.preparationRequests) { preparationRequest(it) }
    list("memoryAllocations", value.memoryAllocations) { memoryAllocation(it) }
}

private fun CanonicalHashSink.preparedImageBinding(value: GPUImageBindingRequest) {
    tag("GPUPreparedImageBindingRequest")
    string("packetId", value.packetId)
    string("artifactKey", value.artifactKey.value)
    textureDescriptor("texture", value.texture)
    tag("GPUTextureViewDescriptor")
    string("textureDescriptorHash", value.view.textureDescriptorHash)
    string("viewDimension", value.view.viewDimension)
    int("mipRangeFirst", value.view.mipRange.first)
    int("mipRangeLast", value.view.mipRange.last)
    int("arrayLayerRangeFirst", value.view.arrayLayerRange.first)
    int("arrayLayerRangeLast", value.view.arrayLayerRange.last)
    samplerDescriptor("sampler", value.sampler)
    string("bindingLayoutHash", value.bindingLayoutHash)
    tag("GPUPreparedImageUniformAllocation")
    string("uniformPacketId", value.uniformAllocation.packetId)
    long("uniformOffset", value.uniformAllocation.offset)
    long("uniformSize", value.uniformAllocation.size)
}

private fun CanonicalHashSink.preparedTextBinding(value: GPUPreparedTextRenderBinding) {
    tag("GPUPreparedTextRenderBinding")
    string("packetId", value.packetId.value)
    string("atlasStagingRef", value.atlasResourcePlan.stagingRef.value)
    string("atlasTextureRef", value.atlasResourcePlan.frameTextureRef.value)
    string("atlasArtifactKey", value.atlasResourcePlan.artifactKey)
    long("atlasGeneration", value.atlasResourcePlan.artifactGeneration)
    string("atlasContentHash", value.atlasResourcePlan.artifactContentHash)
    tag("GPUPreparedTextInstanceBufferPlan")
    string("bufferRef", value.instanceBufferPlan.bufferRef.value)
    int("strideBytes", value.instanceBufferPlan.strideBytes)
    int("alignmentBytes", value.instanceBufferPlan.alignmentBytes)
    int("instanceCount", value.instanceBufferPlan.instanceCount)
    long("byteSize", value.instanceBufferPlan.byteSize)
    string("contentHash", value.instanceBufferPlan.contentHash)
    int("firstInstance", value.firstInstance)
    int("drawInstanceCount", value.instanceCount)
    nullable("materialUniformBuffer", value.materialUniformBufferPlan) { plan ->
        string("bufferRef", plan.bufferRef.value)
        long("alignmentBytes", plan.alignmentBytes)
        long("byteSize", plan.byteSize)
        string("contentHash", plan.contentHash)
    }
    long("materialUniformOffsetBytes", value.materialUniformOffsetBytes)
    long("materialUniformSizeBytes", value.materialUniformSizeBytes)
    list("materialSampledResources", value.materialSampledResourcePlans) { plan ->
        string("resourceKey", plan.resourceKey)
        string("textureRef", plan.frameTextureRef.value)
        string("contentHash", plan.contentHash)
    }
    if (value.hasTextA8Composite) {
        tag("GPUPreparedTextCompositeBinding")
        val plan = value.drawUniformBufferPlan
        val slice = value.drawUniformSlice
        val program = value.compositeProgram
        preparedTextAffine("deviceToLocal", value.preflightSeal.textA8Composite!!.deviceToLocal)
        string("drawUniformBufferRef", plan.bufferRef.value)
        long("drawUniformAlignmentBytes", plan.alignmentBytes)
        long("drawUniformLogicalSliceSizeBytes", plan.logicalSliceSizeBytes)
        long("drawUniformBufferByteSize", plan.byteSize)
        string("drawUniformBufferContentHash", plan.contentHash)
        list("drawUniformSlices", plan.slices) { preparedTextDrawUniformSlice(it) }
        tag("selectedDrawUniformSlice")
        preparedTextDrawUniformSlice(slice)
        string("compositeSourceHash", program.sourceHash)
        string("compositeAbiHash", program.abiHash)
        string("compositePipelineKey", program.pipelineKey)
        string("compositeVertexEntryPoint", program.vertexEntryPoint)
        string("compositeFragmentEntryPoint", program.fragmentEntryPoint)
        preparedTextVertexLayout("compositeVertexLayout", program.vertexLayout)
    }
    tag("GPUPreparedTextBindingPreflightSeal")
    string("semanticCanonicalHash", value.preflightSeal.semanticCanonicalHash)
    string("sealAtlasKey", value.preflightSeal.atlasKey)
    int("sealAtlasWidth", value.preflightSeal.atlasWidth)
    int("sealAtlasHeight", value.preflightSeal.atlasHeight)
    int("sealAtlasRowBytes", value.preflightSeal.atlasRowBytes)
    long("sealAtlasGeneration", value.preflightSeal.atlasGeneration)
    string("sealAtlasContentHash", value.preflightSeal.atlasContentHash)
    int("sealPageIndex", value.preflightSeal.pageIndex)
    int("sealInstanceStrideBytes", value.preflightSeal.instanceStrideBytes)
    int("sealFirstInstance", value.preflightSeal.firstInstance)
    int("sealInstanceCount", value.preflightSeal.instanceCount)
    long("sealInstanceBufferByteSize", value.preflightSeal.instanceBufferByteSize)
    string("sealInstanceBufferContentHash", value.preflightSeal.instanceBufferContentHash)
    long("sealMaterialUniformOffsetBytes", value.preflightSeal.materialUniformOffsetBytes)
    long("sealMaterialUniformSizeBytes", value.preflightSeal.materialUniformSizeBytes)
    string("sealMaterialKey", value.preflightSeal.materialKey)
    string("sealMaterialWgslSourceHash", value.preflightSeal.materialWgslSourceHash)
    string("sealMaterialEntryPoint", value.preflightSeal.materialEntryPoint)
    string("sealMaterialAbiHash", value.preflightSeal.materialAbiHash)
    string("sealMaterialUniformContentHash", value.preflightSeal.materialUniformContentHash)
    list("sealMaterialSampledResourceFacts", value.preflightSeal.materialSampledResourceFacts) {
        string("fact", it)
    }
    bounds("sealTargetBounds", value.preflightSeal.targetBounds)
    bounds("sealScissorBounds", value.preflightSeal.scissorBounds)
    string("sealClipIdentity", value.preflightSeal.clipIdentity)
    string("sealBlendPlanIdentity", value.preflightSeal.blendPlanIdentity)
    string("sealCapabilitySnapshotHash", value.preflightSeal.capabilitySnapshotHash)
    value.preflightSeal.packetAuthority?.let { seal ->
        tag("GPUPreparedTextPacketAuthoritySeal")
        int("sealPacketCommandIdValue", seal.commandIdValue)
        string("sealPacketRenderStepIdentity", seal.renderStepIdentity)
        string("sealPacketRenderPipelineKey", seal.renderPipelineKey)
        string("sealPacketBindingLayoutHash", seal.bindingLayoutHash)
        nullable("sealPacketUniformSlot", seal.uniformSlot) { slot ->
            string("slotId", slot.slotId.value)
            string("fingerprint", slot.fingerprint.value)
            long("byteOffset", slot.byteOffset)
        }
        string("sealPacketVertexSourceLabel", seal.vertexSourceLabel)
        string("sealPacketTargetStateHash", seal.targetStateHash)
        nullable("sealPacketScissorBoundsHash", seal.scissorBoundsHash) {
            string("value", it)
        }
    }
    value.preflightSeal.textA8Composite?.let { seal ->
        tag("GPUPreparedTextCompositePreflightSeal")
        preparedTextAffine("sealDeviceToLocal", seal.deviceToLocal)
        string("sealDrawUniformBufferRef", seal.drawUniformBufferRef.value)
        long("sealDrawUniformAlignmentBytes", seal.drawUniformAlignmentBytes)
        long(
            "sealDrawUniformLogicalSliceSizeBytes",
            seal.drawUniformLogicalSliceSizeBytes,
        )
        long("sealDrawUniformBufferByteSize", seal.drawUniformBufferByteSize)
        string("sealDrawUniformBufferContentHash", seal.drawUniformBufferContentHash)
        tag("sealDrawUniformSlice")
        preparedTextDrawUniformSlice(seal.drawUniformSlice)
        string("sealCompositeSourceHash", seal.compositeSourceHash)
        string("sealCompositeAbiHash", seal.compositeAbiHash)
        string("sealCompositePipelineKey", seal.compositePipelineKey)
        string(
            "sealCompositeSourceCoverageEncoding",
            seal.compositeSourceCoverageEncoding.name,
        )
        string("sealCompositeClipVariant", seal.clipPlan.variant.name)
        string("sealCompositeClipIdentity", seal.clipPlan.executionPlanIdentity)
        nullable("sealCompositeCoverageMaskResource", seal.coverageMaskResource) {
            string("value", it.value)
        }
        when (val clipPlan = seal.clipPlan) {
            is GPUPreparedTextClipPlan.Direct -> tag("sealCompositeDirectClip")
            is GPUPreparedTextClipPlan.CoverageMask -> {
                tag("sealCompositeCoverageMaskClip")
                string("contentKey", clipPlan.contentKey)
                string("orderingToken", clipPlan.orderingToken)
            }
            is GPUPreparedTextClipPlan.Analytic -> {
                tag("sealCompositeAnalyticClip")
                int("leftBits", clipPlan.left.toRawBits())
                int("topBits", clipPlan.top.toRawBits())
                int("rightBits", clipPlan.right.toRawBits())
                int("bottomBits", clipPlan.bottom.toRawBits())
                int("radiusXBits", clipPlan.radiusX.toRawBits())
                int("radiusYBits", clipPlan.radiusY.toRawBits())
            }
        }
        string("sealCompositeVertexEntryPoint", seal.compositeVertexEntryPoint)
        string("sealCompositeFragmentEntryPoint", seal.compositeFragmentEntryPoint)
        preparedTextVertexLayout("sealCompositeVertexLayout", seal.compositeVertexLayout)
    }
    value.preflightSeal.colorGlyphClip?.let { seal ->
        tag("GPUPreparedColorGlyphClipPreflightSeal")
        string("sealColorGlyphClipSemanticIdentity", seal.semanticIdentity)
        string("sealColorGlyphClipExecutionPlanIdentity", seal.executionPlanIdentity)
        when (seal) {
            is GPUPreparedColorGlyphClipPreflightSeal.NonMask -> {
                tag("sealColorGlyphNonMaskClip")
                nullable("sealColorGlyphAnalyticRect", seal.analyticRect) { facts ->
                    int("leftBits", facts.left.toRawBits())
                    int("topBits", facts.top.toRawBits())
                    int("rightBits", facts.right.toRawBits())
                    int("bottomBits", facts.bottom.toRawBits())
                    nullable("scissor", facts.scissor) { scissor -> bounds("value", scissor) }
                    bool("antiAlias", facts.antiAlias)
                }
            }
            is GPUPreparedColorGlyphClipPreflightSeal.CoverageMask -> {
                tag("sealColorGlyphCoverageMaskClip")
                string("resource", seal.resource.value)
                string("orderingToken", seal.orderingToken)
            }
        }
    }
}

private fun CanonicalHashSink.preparedTextAffine(
    name: String,
    value: org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedTextDeviceToLocalAffine,
) {
    tag(name)
    list("rawBits", value.rawBits()) { bits -> int("bits", bits) }
}

private fun CanonicalHashSink.preparedTextDrawUniformSlice(
    value: GPUPreparedTextDrawUniformSlice,
) {
    string("packetId", value.packetId.value)
    long("offsetBytes", value.offsetBytes)
    long("sizeBytes", value.sizeBytes)
    string("contentHash", value.contentHash)
}

private fun CanonicalHashSink.preparedTextVertexLayout(
    name: String,
    value: org.graphiks.kanvas.gpu.renderer.wgsl.GPUPreparedTextVertexLayout,
) {
    tag(name)
    long("arrayStrideBytes", value.arrayStrideBytes)
    string("stepMode", value.stepMode)
    list("attributes", value.attributes) { attribute ->
        int("location", attribute.location)
        long("offsetBytes", attribute.offsetBytes)
        string("format", attribute.format)
    }
}

private fun CanonicalHashSink.textureDescriptor(name: String, value: GPUTextureDescriptor) {
    tag(name)
    tag("GPUTextureDescriptor")
    int("width", value.width)
    int("height", value.height)
    string("format", value.format)
    list("usageLabels", value.usageLabels.sorted()) { string("usage", it) }
    int("sampleCount", value.sampleCount)
}

private fun CanonicalHashSink.samplerDescriptor(name: String, value: GPUSamplerDescriptor) {
    tag(name)
    tag("GPUSamplerDescriptor")
    string("addressModeU", value.addressModeU)
    string("addressModeV", value.addressModeV)
    string("magFilter", value.magFilter)
    string("minFilter", value.minFilter)
    string("mipmapFilter", value.mipmapFilter)
    string("lodMinClamp", value.lodMinClamp)
    string("lodMaxClamp", value.lodMaxClamp)
    string("compareMode", value.compareMode)
    int("maxAnisotropy", value.maxAnisotropy)
    list("capabilityRequirements", value.capabilityRequirements.sorted()) {
        string("requirement", it)
    }
}

private fun GPUFrameStep.canonicalTypeTag(): String = when (this) {
    is GPUFrameStep.RenderPassStep -> "RenderPassStep"
    is GPUFrameStep.ComputePassStep -> "ComputePassStep"
    is GPUFrameStep.PrepareResourcesStep -> "PrepareResourcesStep"
    is GPUFrameStep.UploadResourceStep -> "UploadResourceStep"
    is GPUFrameStep.CopyResourceStep -> "CopyResourceStep"
    is GPUFrameStep.DependencyBarrierStep -> "DependencyBarrierStep"
    is GPUFrameStep.CopyDestinationStep -> "CopyDestinationStep"
    is GPUFrameStep.CopyAsDrawMaterializationStep -> "CopyAsDrawMaterializationStep"
    is GPUFrameStep.TargetTransitionStep -> "TargetTransitionStep"
    is GPUFrameStep.ReadbackCopyStep -> "ReadbackCopyStep"
    is GPUFrameStep.AcquireSurfaceOutput -> "AcquireSurfaceOutput"
    is GPUFrameStep.SurfaceBlitRenderPassStep -> "SurfaceBlitRenderPassStep"
    is GPUFrameStep.PostSubmitPresentAction -> "PostSubmitPresentAction"
    is GPUFrameStep.RefusedLeafDrawStep -> "RefusedLeafDrawStep"
    is GPUFrameStep.RefusedCompositeCommandStep -> "RefusedCompositeCommandStep"
    is GPUFrameStep.LayerTargetPrepareStep -> "LayerTargetPrepareStep"
    is GPUFrameStep.LayerChildrenRenderStep -> "LayerChildrenRenderStep"
    is GPUFrameStep.LayerCompositeRenderStep -> "LayerCompositeRenderStep"
}

private fun CanonicalHashSink.packet(value: GPUDrawPacket) {
    tag("GPUDrawPacket")
    string("packetId", value.packetId.value)
    int("commandIdValue", value.commandIdValue)
    string("analysisRecordId", value.analysisRecordId)
    string("passId", value.passId)
    string("layerId", value.layerId)
    string("bindingListId", value.bindingListId)
    string("insertionReasonCode", value.insertionReasonCode)
    long("sortKey", value.sortKey)
    string("sortKeyPreimage", value.sortKeyPreimage)
    string("renderStepId", value.renderStepId.value)
    int("renderStepVersion", value.renderStepVersion)
    string("role", value.role.name)
    nullable("blendPlan", value.blendPlan) { blendPlan(it) }
    nullableString("renderPipelineKey", value.renderPipelineKey?.value)
    nullableString("computePipelineKey", value.computePipelineKey?.value)
    string("bindingLayoutHash", value.bindingLayoutHash)
    nullable("uniformSlot", value.uniformSlot) { slot ->
        string("slotId", slot.slotId.value)
        string("fingerprint", slot.fingerprint.value)
        long("byteOffset", slot.byteOffset)
    }
    nullable("resourceSlot", value.resourceSlot) { slot ->
        string("slotId", slot.slotId.value)
        string("fingerprint", slot.fingerprint.value)
        int("bindingIndex", slot.bindingIndex)
    }
    nullable("semanticPayload", value.semanticPayload) { payload ->
        semanticPayload(payload)
    }
    string("vertexSourceLabel", value.vertexSourceLabel)
    nullableString("scissorBoundsHash", value.scissorBoundsHash)
    nullableString("clipExecutionPlan", value.clipExecutionPlan?.canonicalIdentity())
    nullableString("clipProducerAuthority", value.clipProducerAuthority?.selectorIdentity)
    string("targetStateHash", value.targetStateHash)
    int("originalPaintOrder", value.originalPaintOrder)
    long("resourceGeneration", value.resourceGeneration)
    list("diagnostics", value.diagnostics) { diagnostic ->
        string("code", diagnostic.code)
        nullableString("passId", diagnostic.passId)
        nullableString("invocationId", diagnostic.invocationId)
        bool("terminal", diagnostic.terminal)
    }
}

private fun CanonicalHashSink.semanticPayload(value: GPUDrawSemanticPayload) {
    tag(value.canonicalType)
    val ref = value.payloadRef
    int("commandIdValue", ref.commandIdValue)
    string("renderStepIdentity", ref.renderStepIdentity)
    nullable("uniformSlot", ref.uniformSlot) { slot ->
        string("slotId", slot.slotId.value)
        string("fingerprint", slot.fingerprint.value)
        long("byteOffset", slot.byteOffset)
    }
    nullable("uniformBlock", ref.uniformBlock) { block ->
        string("fingerprint", block.fingerprint.value)
        string("packingPlanHash", block.packingPlanHash)
        long("byteSize", block.byteSize)
        bool("zeroedPadding", block.zeroedPadding)
        string("scope", block.scope)
        list("bytes", block.bytes) { byte -> int("byte", byte) }
        list("fields", block.fields) { field ->
            string("fieldPath", field.fieldPath)
            long("byteOffset", field.byteOffset)
            long("byteSize", field.byteSize)
            string("valueClass", field.valueClass)
            bool("zeroFilled", field.zeroFilled)
        }
    }
    nullable("resourceSlot", ref.resourceSlot) { slot ->
        string("slotId", slot.slotId.value)
        string("fingerprint", slot.fingerprint.value)
        int("bindingIndex", slot.bindingIndex)
    }
    nullable("gradientStore", ref.gradientStore) { store -> string("fingerprint", store.fingerprint.value) }
    nullable("resourceBlock", ref.resourceBlock) { block -> string("fingerprint", block.fingerprint.value) }
    when (value) {
        is GPUDrawSemanticPayload.SolidRect -> Unit
        is GPUDrawSemanticPayload.Vertices -> {
            string("canonicalHash", value.canonicalHash)
            string("artifactKey", value.artifact.key)
            string("topology", value.topologyIdentity.sourceLabel)
            string("targetFormat", value.targetFormat)
            string("clipIdentity", value.clipIdentity)
            string("clipCoverageIdentity", value.clipCoverageIdentity)
            nullableString("primitiveBlendIdentity", value.primitiveBlendIdentity)
            string("finalBlendIdentity", value.finalBlendIdentity)
            string("capabilitySnapshotHash", value.capabilitySnapshotHash)
            string("drawProvenance", value.drawProvenance)
            string("frameProvenance", value.frameProvenance.annotationValue)
            bounds("targetBounds", value.targetBounds)
            bounds("scissorBounds", value.scissorBounds)
            list("transformBytes", value.transformBytes) { bits -> int("bits", bits) }
        }
        is GPUDrawSemanticPayload.CorePrimitive -> {
            string("sourceFamily", value.sourceFamily.name)
            string("canonicalHash", value.canonicalHash)
            string("blendPlanIdentity", value.blendPlanIdentity)
            nullableString("clipExecutionPlanIdentity", value.clipExecutionPlanIdentity)
            string("frameProvenance", value.frameProvenance.annotationValue)
            string("coverageMode", value.coverageMode.name)
            bounds("targetBounds", value.targetBounds)
            bounds("scissorBounds", value.scissorBounds)
            list("premultipliedRgba", value.premultipliedRgba) { channel ->
                int("channelBits", channel.toRawBits())
            }
            tag(value.geometry.canonicalType)
            when (val geometry = value.geometry) {
                is GPUCorePrimitiveGeometry.Rect -> {
                    int("leftBits", geometry.left.toRawBits())
                    int("topBits", geometry.top.toRawBits())
                    int("rightBits", geometry.right.toRawBits())
                    int("bottomBits", geometry.bottom.toRawBits())
                }
                is GPUCorePrimitiveGeometry.RRect -> {
                    int("leftBits", geometry.left.toRawBits())
                    int("topBits", geometry.top.toRawBits())
                    int("rightBits", geometry.right.toRawBits())
                    int("bottomBits", geometry.bottom.toRawBits())
                    list("radii", geometry.radii) { radius -> int("radiusBits", radius.toRawBits()) }
                }
                is GPUCorePrimitiveGeometry.TriangulatedPath -> {
                    list("vertices", geometry.vertices) { coordinate -> int("coordinateBits", coordinate.toRawBits()) }
                    list("indices", geometry.indices) { index -> int("index", index) }
                    list("sourceContourStarts", geometry.sourceContourStarts) { index -> int("index", index) }
                    int("sourceVertexCount", geometry.sourceVertexCount)
                    bounds("coverBounds", geometry.coverBounds)
                    string("geometryMode", geometry.geometryMode.name)
                    string("fillRule", geometry.fillRule.name)
                    bool("inverseFill", geometry.inverseFill)
                    nullable("strokeStyle", geometry.strokeStyle) { stroke ->
                        int("widthBits", stroke.width.toRawBits())
                        string("cap", stroke.cap)
                        string("join", stroke.join)
                        int("miterLimitBits", stroke.miterLimit.toRawBits())
                        list("dashIntervals", stroke.dashIntervals) { interval ->
                            int("intervalBits", interval.toRawBits())
                        }
                        int("dashPhaseBits", stroke.dashPhase.toRawBits())
                        string("loweringProof", stroke.loweringProof.name)
                    }
                }
            }
            when (val clip = value.clipCoveragePlan) {
                GPUClipCoveragePlan.NoClip -> tag("NoClip")
                is GPUClipCoveragePlan.Scissor -> {
                    tag("Scissor")
                    int("leftBits", clip.bounds.left.toRawBits())
                    int("topBits", clip.bounds.top.toRawBits())
                    int("rightBits", clip.bounds.right.toRawBits())
                    int("bottomBits", clip.bounds.bottom.toRawBits())
                }
                is GPUClipCoveragePlan.AnalyticIntersection -> {
                    tag("AnalyticIntersection")
                    list("elements", clip.elements) { element ->
                        string("operation", element.operation.name)
                        string("kind", element.kind.name)
                        bool("antiAlias", element.antiAlias)
                        string("fillRule", element.fillRule.name)
                        bool("inverseFill", element.inverseFill)
                        int("vertexCount", element.vertexCount)
                        list("values", element.values) { scalar -> int("scalarBits", scalar.toRawBits()) }
                    }
                }
                is GPUClipCoveragePlan.Mask -> {
                    tag("Mask")
                    string("contentKey", clip.contentKey)
                    int("width", clip.width)
                    int("height", clip.height)
                    int("sampleCount", clip.sampleCount)
                    long("resolvedBytes", clip.resolvedBytes)
                    long("requiredBytes", clip.requiredBytes)
                    list("elements", clip.elements) { element ->
                        string("operation", element.operation.name)
                        string("kind", element.kind.name)
                        bool("antiAlias", element.antiAlias)
                        string("fillRule", element.fillRule.name)
                        bool("inverseFill", element.inverseFill)
                        int("vertexCount", element.vertexCount)
                        list("values", element.values) { scalar -> int("scalarBits", scalar.toRawBits()) }
                    }
                }
                is GPUClipCoveragePlan.Refused -> {
                    tag("Refused")
                    string("code", clip.code)
                }
            }
        }
        is GPUDrawSemanticPayload.RegisteredUniformRect -> {
            string("program", value.program.wireId)
            string("canonicalHash", value.canonicalHash)
            int("uniformByteCount", value.uniformBytes.size)
            bounds("targetBounds", value.targetBounds)
            bounds("scissorBounds", value.scissorBounds)
        }
        is GPUDrawSemanticPayload.SeparableBlurRect -> {
            string("canonicalHash", value.canonicalHash)
            bounds("sourceBounds", value.sourceBounds)
            bounds("targetBounds", value.targetBounds)
            int("effectiveSigmaBits", value.effectiveSigma.toRawBits())
            int("tapCount", value.tapCount)
            list("sourcePremultipliedRgba", value.sourcePremultipliedRgba) { channel ->
                int("channelBits", channel.toRawBits())
            }
            list("clearPremultipliedRgba", value.clearPremultipliedRgba) { channel ->
                int("channelBits", channel.toRawBits())
            }
            list("weights", value.weights) { weight -> int("weightBits", weight.toRawBits()) }
        }
        is GPUDrawSemanticPayload.SampledImage -> {
            string("canonicalHash", value.canonicalHash)
            string("artifactKey", value.artifact.key.value)
            string("artifactContentHash", value.artifact.contentHash)
            int("artifactWidth", value.artifact.width)
            int("artifactHeight", value.artifact.height)
            bool("artifactAlphaOnly", value.artifact.alphaOnly)
            string("sampling", value.sampling.name)
            string("geometryClass", value.geometry.geometryClass.name)
            list("vertices", value.geometry.vertices) { vertex ->
                int("xBits", vertex.x.toRawBits())
                int("yBits", vertex.y.toRawBits())
                int("uBits", vertex.u.toRawBits())
                int("vBits", vertex.v.toRawBits())
            }
            list("indices", value.geometry.indices) { index -> int("index", index) }
            list("tintPremultipliedRgba", value.tintPremultipliedRgba) { channel -> int("channelBits", channel.toRawBits()) }
            nullable("atlasColorPremultipliedRgba", value.atlasColorPremultipliedRgba) { color ->
                list("channels", color) { channel -> int("channelBits", channel.toRawBits()) }
            }
            nullableString("atlasSourceBlend", value.atlasSourceBlend?.name)
            string("blendPlanIdentity", value.blendPlanIdentity)
            string("frameProvenance", value.frameProvenance.annotationValue)
            bounds("targetBounds", value.targetBounds)
            bounds("scissorBounds", value.scissorBounds)
        }
        is GPUDrawSemanticPayload.TextA8 -> {
            string("canonicalHash", value.canonicalHash)
            string("atlasKey", value.atlas.key)
            string("atlasContentHash", value.atlas.contentHash)
            int("atlasWidth", value.atlas.width)
            int("atlasHeight", value.atlas.height)
            int("atlasRowBytes", value.atlas.rowBytes)
            int("atlasGeneration", value.atlasGeneration.value)
            int("pageIndex", value.pageIndex)
            int("instanceCount", value.instances.size)
            string("materialKey", value.material.materialKey)
            string("materialAbiHash", value.material.abiHash)
            string("clipIdentity", value.clipIdentity)
            string("blendPlanIdentity", value.blendPlanIdentity)
            string("capabilitySnapshotHash", value.capabilitySnapshotHash)
            string("frameProvenance", value.frameProvenance.annotationValue)
            list("deviceToLocalRawBits", value.deviceToLocal.rawBits()) { bits ->
                int("bits", bits)
            }
            bounds("targetBounds", value.targetBounds)
            bounds("scissorBounds", value.scissorBounds)
        }
        is GPUDrawSemanticPayload.ColorGlyph -> {
            string("canonicalHash", value.canonicalHash)
            string("planArtifactId", value.planArtifactKey.artifactID.value.toString())
            int("planArtifactGeneration", value.planArtifactKey.generation.value)
            string("planArtifactFingerprint", value.planArtifactKey.contentFingerprint)
            string("atlasArtifactId", value.atlasArtifactKey.artifactID.value.toString())
            int("atlasArtifactGeneration", value.atlasArtifactKey.generation.value)
            string("atlasArtifactFingerprint", value.atlasArtifactKey.contentFingerprint)
            string("atlasBytesSha256", value.atlasBytesSha256)
            string("atlasKey", value.atlas.key)
            int("atlasRowBytes", value.atlas.rowBytes)
            string("atlasContentHash", value.atlas.contentHash)
            long("atlasGeneration", value.atlasGeneration)
            int("atlasWidth", value.atlasWidth)
            int("atlasHeight", value.atlasHeight)
            string("atlasFormat", value.atlasFormat.gpuLabel)
            int("atlasByteCount", value.atlas.byteSize)
            int("layerCount", value.layers.size)
            int("vertexFloatCount", value.vertexData.size)
            int("indexCount", value.indexData.size)
            int("uniformByteCount", value.uniformBytes.size)
            int("preparedInstanceCount", value.instances.size)
            nullableString("preparedMaterialKey", value.material?.materialKey)
            nullableString("preparedMaterialAbiHash", value.material?.abiHash)
            nullableString("preparedClipIdentity", value.clipIdentity)
            nullableString("preparedBlendPlanIdentity", value.blendPlanIdentity)
            nullableString("preparedCapabilitySnapshotHash", value.capabilitySnapshotHash)
            nullableString("preparedFrameProvenance", value.frameProvenance?.annotationValue)
            bounds("targetBounds", value.targetBounds)
            bounds("scissorBounds", value.scissorBounds)
        }
    }
}

private fun CanonicalHashSink.blendPlan(value: GPUBlendPlan) {
    when (value) {
        is GPUBlendPlan.FixedFunctionBlend -> {
            tag("FixedFunctionBlend")
            string("mode", value.mode.name)
            string("sourceCoverageEncoding", value.sourceCoverageEncoding.name)
            string("stateId", value.state.stateId)
            string("colorSourceFactor", value.state.color.sourceFactor)
            string("colorDestinationFactor", value.state.color.destinationFactor)
            string("colorOperation", value.state.color.operation)
            string("alphaSourceFactor", value.state.alpha.sourceFactor)
            string("alphaDestinationFactor", value.state.alpha.destinationFactor)
            string("alphaOperation", value.state.alpha.operation)
            string("writeMask", value.state.writeMask)
        }
        is GPUBlendPlan.ShaderBlendNoDstRead -> {
            tag("ShaderBlendNoDstRead")
            string("mode", value.mode.name)
            string("formulaId", value.formulaId)
            string("sourceCoverageEncoding", value.sourceCoverageEncoding.name)
        }
        is GPUBlendPlan.ShaderBlendWithDstRead -> {
            tag("ShaderBlendWithDstRead")
            string("mode", value.mode.name)
            string("formulaId", value.formulaId)
            string("sourceCoverageEncoding", value.sourceCoverageEncoding.name)
        }
        is GPUBlendPlan.LayerCompositeBlend -> {
            tag("LayerCompositeBlend")
            string("layerOrderingToken", value.layerOrderingToken)
            blendPlan(value.child)
        }
        is GPUBlendPlan.NoOp -> {
            tag("NoOp")
            string("mode", value.mode.name)
            string("reason", value.reason)
        }
        is GPUBlendPlan.UnsupportedBlend -> {
            tag("UnsupportedBlend")
            string("mode", value.mode.name)
            string("diagnosticCode", value.diagnostic.code)
            string("diagnosticMode", value.diagnostic.mode.name)
            string("diagnosticMessage", value.diagnostic.message)
            bool("diagnosticTerminal", value.diagnostic.terminal)
            string("refusalScope", value.refusalScope.name)
        }
    }
}

private fun CanonicalHashSink.loadStore(name: String, value: GPULoadStorePlan) {
    tag(name)
    tag("GPULoadStorePlan")
    string("loadOp", value.loadOp)
    string("storePlan", value.storePlan.name)
    nullableString("clearColorLabel", value.clearColorLabel)
}

private fun CanonicalHashSink.samplePlan(name: String, value: GPUSamplePlan) {
    tag(name)
    when (value) {
        GPUSamplePlan.SingleSampleFrame -> tag("SingleSampleFrame")
        is GPUSamplePlan.MultisampleFrame -> {
            tag("MultisampleFrame")
            int("sampleCount", value.sampleCount)
        }
        is GPUSamplePlan.LocalResolveApproximation -> {
            tag("LocalResolveApproximation")
            int("sourceSampleCount", value.sourceSampleCount)
        }
    }
}

private fun CanonicalHashSink.resourceRef(name: String, value: GPUFrameResourceRef) {
    tag(name)
    tag(
        when (value) {
            is GPUFrameTextureRef -> "GPUFrameTextureRef"
            is GPUFrameBufferRef -> "GPUFrameBufferRef"
            is GPUFrameTargetRef -> "GPUFrameTargetRef"
        },
    )
    string("value", value.value)
}

private fun CanonicalHashSink.resourceUse(value: GPUFrameResourceUse) {
    tag("GPUFrameResourceUse")
    resourceRef("resource", value.resource)
    string("role", value.role.name)
    string("usage", value.usage.name)
    string("lifetime", value.lifetime.name)
    bool("write", value.write)
}

private fun CanonicalHashSink.dispatch(value: GPUComputeDispatch) {
    tag("GPUComputeDispatch")
    string("programKey", value.programKey.value)
    int("workgroupCountX", value.workgroupCountX)
    int("workgroupCountY", value.workgroupCountY)
    int("workgroupCountZ", value.workgroupCountZ)
}

private fun CanonicalHashSink.preparationRequest(value: GPUResourcePreparationRequest) {
    tag("GPUResourcePreparationRequest")
    resourceRef("resource", value.resource)
    when (val descriptor = value.descriptor) {
        is GPUFrameTextureDescriptor -> {
            tag("GPUFrameTextureDescriptor")
            bounds("logicalBounds", descriptor.logicalBounds)
            string("format", descriptor.format.value)
            int("sampleCount", descriptor.sampleCount)
        }
        is GPUFrameBufferDescriptor -> {
            tag("GPUFrameBufferDescriptor")
            long("byteSize", descriptor.byteSize)
            long("alignmentBytes", descriptor.alignmentBytes)
        }
    }
    string("role", value.role.name)
    list("usages", value.usages.sortedBy { it.name }) { string("usage", it.name) }
    string("lifetime", value.lifetime.name)
    long("byteSize", value.byteSize)
    string("diagnosticLabel", value.diagnosticLabel)
}

private fun CanonicalHashSink.bounds(name: String, value: GPUPixelBounds) {
    tag(name)
    tag("GPUPixelBounds")
    int("left", value.left)
    int("top", value.top)
    int("right", value.right)
    int("bottom", value.bottom)
}

private fun CanonicalHashSink.surfaceDescriptor(value: GPUSurfaceOutputDescriptor) {
    tag("GPUSurfaceOutputDescriptor")
    string("output", value.output.value)
    int("width", value.width)
    int("height", value.height)
    string("format", value.format.value)
    long("targetGeneration", value.targetGeneration)
}

private fun CanonicalHashSink.destinationSourceKey(
    name: String,
    value: GPUDestinationSnapshotGroupKey,
) {
    tag(name)
    tag("GPUDestinationSnapshotGroupKey")
    string("target", value.target.value)
    long("targetGeneration", value.targetGeneration)
    long("deviceGeneration", value.deviceGeneration.value)
    string("format", value.format.value)
    string("colorInterpretation", value.colorInterpretation.value)
    nullable("sampleContinuation", value.sampleContinuation) { continuation ->
        string("sampleTarget", continuation.target.value)
        long("sampleTargetGeneration", continuation.targetGeneration)
        long("sampleDeviceGeneration", continuation.deviceGeneration.value)
        string("sampleColorFormat", continuation.colorFormat.value)
        string("sampleColorInterpretation", continuation.colorInterpretation.value)
        int("sampleCount", continuation.samplePlan.sampleCount)
        string("attachmentAuthority", continuation.attachmentAuthority.name)
        string("colorAttachment", continuation.colorAttachment.value)
        nullableString("depthStencilAttachment", continuation.depthStencilAttachment?.value)
    }
    nullableString("sourceIntermediate", value.sourceIntermediate?.value)
}

private fun CanonicalHashSink.destinationConsumer(value: GPUDestinationSnapshotConsumerRef) {
    tag("GPUDestinationSnapshotConsumerRef")
    string("groupingCommandId", value.groupingCommandId)
    string("renderTaskId", value.renderTaskId.value)
    string("packetId", value.packetId.value)
    int("commandId", value.commandId.value)
}

private fun CanonicalHashSink.diagnostic(name: String, value: GPUDiagnostic) {
    tag(name)
    tag("GPUDiagnostic")
    string("code", value.code.value)
    string("domain", value.domain.name)
    string("severity", value.severity.name)
    string("message", value.message)
    list("facts", value.facts.toSortedMap().entries.toList()) { entry ->
        string("key", entry.key)
        string("value", entry.value)
    }
    bool("isTerminal", value.isTerminal)
    bool("isRetryable", value.isRetryable)
}

private fun GPUFrameStep.dumpLine(index: Int): String {
    val tasks = sourceTaskIds.joinToString(",", transform = GPUTaskID::value)
    val preparedTextDumpSuffix =
        (this as? GPUFrameStep.RenderPassStep)
            ?.takeIf { it.preparedTextBindingsByPacketId.isNotEmpty() }
            ?.let { render ->
                " preparedTextBindings=${render.drawPackets
                    .filter { packet ->
                        packet.semanticPayload is GPUDrawSemanticPayload.TextA8 ||
                            (packet.semanticPayload is GPUDrawSemanticPayload.ColorGlyph &&
                                packet.semanticPayload.instances.isNotEmpty() &&
                                packet.semanticPayload.material != null)
                    }
                    .joinToString(";") { packet ->
                        render.preparedTextBindingsByPacketId
                            .getValue(packet.packetId)
                            .stableDump()
                    }}"
            }
            .orEmpty()
    val body = when (this) {
        is GPUFrameStep.RenderPassStep ->
            "render target=${target.value} load=${loadStore.loadOp} store=${loadStore.storePlan.name} " +
                "clear=${loadStore.clearColorLabel ?: "none"} sample=${samplePlan.specializationKey} " +
                "depthStencil=${depthStencilLoadStore.stableDump()} " +
                "uses=${resourceUses.joinToString(";") { it.stableDump() }.ifEmpty { "none" }} " +
                "continuation=${sampleContinuation?.let { continuation ->
                    "${continuation.key.target.value}@${continuation.key.targetGeneration}:" +
                        "${continuation.key.deviceGeneration.value}:" +
                        "${continuation.key.attachmentAuthority.name}:" +
                        "${continuation.key.colorAttachment.value}:" +
                        "${continuation.loadTransition.name}:" +
                        "${continuation.storeAction.name}:" +
                        continuation.resolveAction.name
                } ?: "none"} " +
                "batches=${batches.joinToString(";") { batch ->
                    "${batch.batchId}:${batch.kind.name}:${batch.packets.joinToString(",") { it.packetId.value }}"
                }} packets=${drawPackets.joinToString(";") { packet -> packet.stableDump() }} " +
                "preparedImageBindings=${drawPackets
                    .filter { packet -> packet.semanticPayload is GPUDrawSemanticPayload.SampledImage }
                    .joinToString(";") { packet ->
                        preparedImageBindingsByPacketId.getValue(packet.packetId).stableDump()
                    }.ifEmpty { "none" }}" +
                preparedTextDumpSuffix
        is GPUFrameStep.ComputePassStep ->
            "compute target=${target.value} uses=${resourceUses.joinToString(";") { it.stableDump() }} " +
                "dispatches=${dispatches.joinToString(";") { it.stableDump() }}"
        is GPUFrameStep.PrepareResourcesStep ->
            "prepare resources=${requests.joinToString(";") { it.stableDump() }}"
        is GPUFrameStep.UploadResourceStep ->
            "upload kind=${destinationKind.name} staging=${staging.value} destination=${destination.value} " +
                "offset=${layout.sourceOffsetBytes} bytesPerRow=${layout.bytesPerRow} " +
                "rowsPerImage=${layout.rowsPerImage} bytes=${layout.byteSize} " +
                when (val plan = textureResourcePlan) {
                    null -> "preparedImagePlan=none"
                    is GPUImageFrameResourcePlan ->
                        "preparedImagePlan=${plan.stableDump()}"
                    is GPUR8FrameResourcePlan ->
                        "preparedR8Plan=${plan.stableDump()}"
                    is GPUMaterialTextureFrameResourcePlan ->
                        "preparedMaterialTexturePlan=${plan.stableDump()}"
                }
        is GPUFrameStep.CopyResourceStep ->
            "copy source=${source.value} destination=${destination.value} " +
                "regions=${regions.joinToString(";") { it.stableDump() }}"
        is GPUFrameStep.DependencyBarrierStep ->
            "barrier reason=$reasonCode tokens=${orderedUseTokens.joinToString(",") { it.value }}"
        is GPUFrameStep.CopyDestinationStep ->
            "destination-copy source=${source.value} ${sourceKey.dumpDestinationSourceKey()} " +
                "snapshot=${snapshot.value} bounds=$logicalBounds " +
                "bytesPerRow=${copyLayout.bytesPerRow} rowsPerImage=${copyLayout.rowsPerImage} " +
                "consumers=${consumers.joinToString(";") { it.dumpDestinationConsumer() }}"
        is GPUFrameStep.CopyAsDrawMaterializationStep ->
            "copy-as-draw source=${source.value} ${sourceKey.dumpDestinationSourceKey()} " +
                "sourceIntermediate=${sourceIntermediate.value} snapshot=${snapshot.value} bounds=$logicalBounds " +
                "capabilitySeal=$capabilitySealHash " +
                "consumers=${consumers.joinToString(";") { it.dumpDestinationConsumer() }}"
        is GPUFrameStep.TargetTransitionStep ->
            "target-transition parent=${parent.value} child=${child.value} kind=${transitionKind.name}"
        is GPUFrameStep.ReadbackCopyStep ->
            "readback source=${source.value} staging=${staging.value} request=${request.requestId.value} " +
                "bounds=${request.sourceBounds} format=${request.pixelFormat.name} " +
                "color=${request.outputColorInterpretation.value} offset=${request.bufferOffsetBytes}"
        is GPUFrameStep.AcquireSurfaceOutput ->
            "acquire-output output=${descriptor.output.value} size=${descriptor.width}x${descriptor.height} " +
                "format=${descriptor.format.value} generation=${descriptor.targetGeneration}"
        is GPUFrameStep.SurfaceBlitRenderPassStep ->
            "surface-blit scene=${scene.value} output=${output.value}"
        is GPUFrameStep.PostSubmitPresentAction -> "present output=${output.value}"
        is GPUFrameStep.RefusedLeafDrawStep ->
            "refused-leaf command=${commandId.value} ${diagnostic.dumpLine("refusal")}"
        is GPUFrameStep.RefusedCompositeCommandStep ->
            "refused-composite command=${commandId.value} " +
                "provenance=${provenanceTokens.joinToString(",") { it.value }} ${diagnostic.dumpLine("refusal")}"
        is GPUFrameStep.LayerTargetPrepareStep ->
            "layer-target-prepare target=$targetLabel descriptor=$descriptorHash " +
                "usage=$usageLabel bytes=$byteEstimate"
        is GPUFrameStep.LayerChildrenRenderStep ->
            "layer-children scope=$scopeLabel target=$targetLabel children=$childrenLabel token=$tokenLabel"
        is GPUFrameStep.LayerCompositeRenderStep ->
            "layer-composite source=$sourceLabel parent=$parentTargetLabel blend=$blendModeLabel " +
                "route=$routeLabel token=$tokenLabel alpha=$alpha clip=${clipLabel ?: "none"}"
    }
    return "step index=$index kind=${executionKind.name} tasks=$tasks $body"
}

private fun GPUImageFrameResourcePlan.stableDump(): String =
    "{staging=${stagingRef.value},texture=${textureRef.value},frameTexture=${frameTextureRef.value}," +
        "uniform=${uniformRef.value},textureDescriptor=${textureDescriptor.stableDump()}," +
        "upload={sourceBytesPerRow=${uploadLayout.sourceBytesPerRow}," +
        "logicalBytesPerRow=${uploadLayout.logicalBytesPerRow}," +
        "bytesPerRow=${uploadLayout.bytesPerRow},rowsPerImage=${uploadLayout.rowsPerImage}," +
        "size=${uploadLayout.width}x${uploadLayout.height}," +
        "nativePayloadByteSize=${uploadLayout.bytesForUpload().size}," +
        "payloadByteSize=${uploadLayout.logicalBytesForHash().size}," +
        "payloadSha256=${uploadLayout.logicalBytesForHash().sha256()}}," +
        "taskLayout={offset=${uploadTaskLayout.sourceOffsetBytes}," +
        "bytesPerRow=${uploadTaskLayout.bytesPerRow},rowsPerImage=${uploadTaskLayout.rowsPerImage}," +
        "byteSize=${uploadTaskLayout.byteSize}}," +
        "bindings=${bindingRequests.mapIndexed { index, binding ->
            "$index:${binding.stableDump()}"
        }.joinToString(";").ifEmpty { "none" }}," +
        "preparations=${preparationRequests.mapIndexed { index, request ->
            "$index:${request.stableDump()}"
        }.joinToString(";").ifEmpty { "none" }}," +
        "allocations=${memoryAllocations.mapIndexed { index, allocation ->
            "$index:{label=${allocation.label},category=${allocation.category.name}," +
                "bytes=${allocation.bytes},kind=${allocation.resourceKind.name}," +
                "extent=${allocation.extent ?: "none"}}"
        }.joinToString(";").ifEmpty { "none" }}}"

private fun GPUR8FrameResourcePlan.stableDump(): String =
    "{staging=${stagingRef.value},frameTexture=${frameTextureRef.value}," +
        "taskLayout={offset=${uploadTaskLayout.sourceOffsetBytes}," +
        "bytesPerRow=${uploadTaskLayout.bytesPerRow},rowsPerImage=${uploadTaskLayout.rowsPerImage}," +
        "byteSize=${uploadTaskLayout.byteSize}}," +
        "artifact={key=$artifactKey,size=${artifactWidth}x$artifactHeight,rowBytes=$artifactRowBytes," +
        "generation=$artifactGeneration,contentHash=$artifactContentHash}," +
        "uploadBytes=${bytesForUpload().size},uploadSha256=${bytesForUpload().sha256()}," +
        "preparations=${preparationRequests.mapIndexed { index, request ->
            "$index:${request.stableDump()}"
        }.joinToString(";").ifEmpty { "none" }}," +
        "allocations=${memoryAllocations.mapIndexed { index, allocation ->
            "$index:{label=${allocation.label},category=${allocation.category.name}," +
                "bytes=${allocation.bytes},kind=${allocation.resourceKind.name}," +
                "extent=${allocation.extent ?: "none"}}"
        }.joinToString(";").ifEmpty { "none" }}}"

private fun GPUMaterialTextureFrameResourcePlan.stableDump(): String =
    "{staging=${stagingRef.value},frameTexture=${frameTextureRef.value}," +
        "taskLayout={offset=${uploadTaskLayout.sourceOffsetBytes}," +
        "bytesPerRow=${uploadTaskLayout.bytesPerRow},rowsPerImage=${uploadTaskLayout.rowsPerImage}," +
        "byteSize=${uploadTaskLayout.byteSize}}," +
        "resource={key=$resourceKey,size=${width}x$height,sampling=$samplingFilterMode," +
        "alphaOnly=$alphaOnly,contentHash=$contentHash}," +
        "uploadBytes=${bytesForUpload().size},uploadSha256=${bytesForUpload().sha256()}," +
        "preparations=${preparationRequests.mapIndexed { index, request ->
            "$index:${request.stableDump()}"
        }.joinToString(";").ifEmpty { "none" }}," +
        "allocations=${memoryAllocations.mapIndexed { index, allocation ->
            "$index:{label=${allocation.label},category=${allocation.category.name}," +
                "bytes=${allocation.bytes},kind=${allocation.resourceKind.name}," +
                "extent=${allocation.extent ?: "none"}}"
        }.joinToString(";").ifEmpty { "none" }}}"

private fun GPUImageBindingRequest.stableDump(): String =
    "{packet=$packetId,artifact=${artifactKey.value},texture=${texture.stableDump()}," +
        "view={descriptorHash=${view.textureDescriptorHash},dimension=${view.viewDimension}," +
        "mips=${view.mipRange.first}..${view.mipRange.last}," +
        "layers=${view.arrayLayerRange.first}..${view.arrayLayerRange.last}}," +
        "sampler=${sampler.stableDump()},bindingLayout=$bindingLayoutHash," +
        "uniform={packet=${uniformAllocation.packetId},offset=${uniformAllocation.offset}," +
        "size=${uniformAllocation.size}}}"

private fun GPUPreparedTextRenderBinding.stableDump(): String =
    "{packet=${packetId.value}," +
        "atlas={staging=${atlasResourcePlan.stagingRef.value}," +
        "texture=${atlasResourcePlan.frameTextureRef.value}," +
        "key=${atlasResourcePlan.artifactKey},generation=${atlasResourcePlan.artifactGeneration}," +
        "contentHash=${atlasResourcePlan.artifactContentHash}}," +
        "instances={buffer=${instanceBufferPlan.bufferRef.value}," +
        "stride=${instanceBufferPlan.strideBytes},alignment=${instanceBufferPlan.alignmentBytes}," +
        "count=${instanceBufferPlan.instanceCount},bytes=${instanceBufferPlan.byteSize}," +
        "contentHash=${instanceBufferPlan.contentHash}}," +
        "range={first=$firstInstance,count=$instanceCount}," +
        "materialUniform=${materialUniformBufferPlan?.let { plan ->
            "{buffer=${plan.bufferRef.value},alignment=${plan.alignmentBytes}," +
                "bytes=${plan.byteSize},contentHash=${plan.contentHash}," +
                "offset=$materialUniformOffsetBytes,size=$materialUniformSizeBytes}"
        } ?: "none"}," +
        "materialResources=${materialSampledResourcePlans.joinToString(";") { plan ->
            "{key=${plan.resourceKey},texture=${plan.frameTextureRef.value}," +
                "contentHash=${plan.contentHash}}"
        }.ifEmpty { "none" }}," +
        "textA8Composite=${if (hasTextA8Composite) textA8CompositeStableDump() else "none"}," +
        "preflightSeal={semantic=${preflightSeal.semanticCanonicalHash}," +
        "atlas=${preflightSeal.atlasKey}@${preflightSeal.atlasGeneration}/" +
        "${preflightSeal.atlasContentHash}:${preflightSeal.atlasWidth}x" +
        "${preflightSeal.atlasHeight}:${preflightSeal.atlasRowBytes}," +
        "page=${preflightSeal.pageIndex}," +
        "instances=${preflightSeal.firstInstance}+${preflightSeal.instanceCount}@" +
        "${preflightSeal.instanceStrideBytes}:${preflightSeal.instanceBufferByteSize}/" +
        "${preflightSeal.instanceBufferContentHash}," +
        "material=${preflightSeal.materialKey}/${preflightSeal.materialAbiHash}/" +
        "${preflightSeal.materialEntryPoint}/${preflightSeal.materialWgslSourceHash}," +
        "uniform=${preflightSeal.materialUniformOffsetBytes}+" +
        "${preflightSeal.materialUniformSizeBytes}/" +
        "${preflightSeal.materialUniformContentHash}," +
        "resources=${preflightSeal.materialSampledResourceFacts.joinToString("|").ifEmpty { "none" }}," +
        "target=${preflightSeal.targetBounds},scissor=${preflightSeal.scissorBounds}," +
        "clip=${preflightSeal.clipIdentity},blend=${preflightSeal.blendPlanIdentity}," +
        "capability=${preflightSeal.capabilitySnapshotHash}," +
        "packet=${preflightSeal.packetAuthority?.let { seal ->
            "${seal.commandIdValue}/${seal.renderStepIdentity}/" +
                "${seal.renderPipelineKey}/${seal.bindingLayoutHash}/" +
                "${seal.uniformSlot?.let { slot ->
                    "${slot.slotId.value}:${slot.fingerprint.value}:${slot.byteOffset}"
                } ?: "none"}/" +
                "${seal.vertexSourceLabel}/${seal.targetStateHash}/" +
                (seal.scissorBoundsHash ?: "none")
        } ?: "none"}," +
        "textA8Composite=${preflightSeal.textA8Composite?.stableDump() ?: "none"}," +
        "colorGlyphClip=${preflightSeal.colorGlyphClip?.stableDump() ?: "none"}}}"

private fun GPUPreparedColorGlyphClipPreflightSeal.stableDump(): String = when (this) {
    is GPUPreparedColorGlyphClipPreflightSeal.NonMask ->
        "non-mask:$semanticIdentity:$executionPlanIdentity:" +
            (analyticRect?.let { facts ->
                "${facts.left.toRawBits()},${facts.top.toRawBits()}," +
                    "${facts.right.toRawBits()},${facts.bottom.toRawBits()}," +
                    "${facts.scissor ?: "none"},${facts.antiAlias}"
            } ?: "none")
    is GPUPreparedColorGlyphClipPreflightSeal.CoverageMask ->
        "coverage-mask:$semanticIdentity:$executionPlanIdentity:${resource.value}:$orderingToken"
}

private fun GPUPreparedTextRenderBinding.textA8CompositeStableDump(): String {
    val plan = drawUniformBufferPlan
    val program = compositeProgram
    return "{deviceToLocalBits=" +
        preflightSeal.textA8Composite!!.deviceToLocal.rawBits().joinToString(",") +
        ",drawUniform={buffer=${plan.bufferRef.value},alignment=${plan.alignmentBytes}," +
        "logicalSliceBytes=${plan.logicalSliceSizeBytes},bytes=${plan.byteSize}," +
        "contentHash=${plan.contentHash},slices=${plan.slices.joinToString("|") {
            it.stableDump()
        }}},selectedSlice=${drawUniformSlice.stableDump()}," +
        "composite={sourceHash=${program.sourceHash},abiHash=${program.abiHash}," +
        "pipelineKey=${program.pipelineKey},vertexEntry=${program.vertexEntryPoint}," +
        "fragmentEntry=${program.fragmentEntryPoint}," +
        "vertexLayout=${program.vertexLayout.stableDump()}}}"
}

private fun GPUPreparedTextCompositePreflightSeal.stableDump(): String =
    "{deviceToLocalBits=${deviceToLocal.rawBits().joinToString(",")}," +
        "drawUniform={buffer=${drawUniformBufferRef.value}," +
        "alignment=$drawUniformAlignmentBytes," +
        "logicalSliceBytes=$drawUniformLogicalSliceSizeBytes," +
        "bytes=$drawUniformBufferByteSize,contentHash=$drawUniformBufferContentHash}," +
        "slice=${drawUniformSlice.stableDump()}," +
        "composite={sourceHash=$compositeSourceHash,abiHash=$compositeAbiHash," +
        "pipelineKey=$compositePipelineKey,vertexEntry=$compositeVertexEntryPoint," +
        "sourceCoverageEncoding=${compositeSourceCoverageEncoding.name}," +
        "clipVariant=${clipPlan.variant.name}," +
        "clipIdentity=${clipPlan.executionPlanIdentity}," +
        "coverageMaskResource=${coverageMaskResource?.value.orEmpty()}," +
        "clipFacts=${clipPlan.stableDump()}," +
        "fragmentEntry=$compositeFragmentEntryPoint," +
        "vertexLayout=${compositeVertexLayout.stableDump()}}}"

private fun GPUPreparedTextDrawUniformSlice.stableDump(): String =
    "${packetId.value}@${offsetBytes}+${sizeBytes}/$contentHash"

private fun GPUPreparedTextClipPlan.stableDump(): String = when (this) {
    is GPUPreparedTextClipPlan.Direct -> "direct"
    is GPUPreparedTextClipPlan.CoverageMask -> "coverage-mask:$contentKey:$orderingToken"
    is GPUPreparedTextClipPlan.Analytic ->
        "${left.toRawBits()},${top.toRawBits()},${right.toRawBits()}," +
            "${bottom.toRawBits()},${radiusX.toRawBits()},${radiusY.toRawBits()}"
}

private fun org.graphiks.kanvas.gpu.renderer.wgsl.GPUPreparedTextVertexLayout.stableDump():
    String =
    "{stride=$arrayStrideBytes,stepMode=$stepMode,attributes=${attributes.joinToString("|") {
        "${it.location}@${it.offsetBytes}:${it.format}"
    }}}"

private fun GPUTextureDescriptor.stableDump(): String =
    "{width=$width,height=$height,format=$format," +
        "usages=${usageLabels.sorted().joinToString(",")},sampleCount=$sampleCount}"

private fun GPUSamplerDescriptor.stableDump(): String =
    "{addressU=$addressModeU,addressV=$addressModeV,mag=$magFilter,min=$minFilter," +
        "mipmap=$mipmapFilter,lodMin=$lodMinClamp,lodMax=$lodMaxClamp,compare=$compareMode," +
        "anisotropy=$maxAnisotropy," +
        "requirements=${capabilityRequirements.sorted().joinToString(",")}}"

private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
    .digest(this)
    .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

private fun GPUFrameMemoryBudgetPlan.dumpLine(): String =
    "memory peakTransient=$peakFrameTransientBytes targetResident=$targetResidentBytes " +
        "configured=$configuredAggregateBudgetBytes categories=${GPUFrameMemoryCategory.entries.joinToString(",") { category ->
            "${category.name}:${categoryTotals[category] ?: 0L}"
        }} limits=${deviceLimitFacts.joinToString(";") { fact ->
            "${fact.name}|${fact.source}|${fact.value}|${fact.affectsValidity}|${fact.evidenceLabel}"
        }} allocations=${allocations.mapIndexed { index, allocation ->
            "$index:{label=${allocation.label},category=${allocation.category.name}," +
                "bytes=${allocation.bytes},kind=${allocation.resourceKind.name}," +
                "extent=${allocation.extent ?: "none"}}"
        }.joinToString(";").ifEmpty { "none" }} " +
        "budgetDiagnostic=${diagnostic?.dumpLine("budget") ?: "none"}"

private fun GPUDepthStencilLoadStorePlan?.stableDump(): String = when (this) {
    null -> "none"
    GPUDepthStencilLoadStorePlan.ReadOnlyKeep -> "read-only-keep"
    is GPUDepthStencilLoadStorePlan.WritableStencil ->
        "writable-stencil:${loadOperation.name}:${storeOperation.name}:${clearValue ?: "none"}"
}

private fun GPUDrawPacket.stableDump(): String =
    "${packetId.value}|command=$commandIdValue|analysis=$analysisRecordId|pass=$passId|layer=$layerId|" +
        "bindings=$bindingListId|insertion=$insertionReasonCode|sort=$sortKey|preimage=$sortKeyPreimage|" +
        "step=${renderStepId.value}@$renderStepVersion|role=${role.name}|blend=${blendPlan}|" +
        "renderPipeline=${renderPipelineKey?.value ?: "none"}|" +
        "computePipeline=${computePipelineKey?.value ?: "none"}|layout=$bindingLayoutHash|" +
        "uniform=${uniformSlot?.let { "${it.slotId.value},${it.fingerprint.value},${it.byteOffset}" } ?: "none"}|" +
        "resource=${resourceSlot?.let { "${it.slotId.value},${it.fingerprint.value},${it.bindingIndex}" } ?: "none"}|" +
        "semantic=${semanticPayload?.stableDump() ?: "none"}|" +
        "vertex=$vertexSourceLabel|scissor=${scissorBoundsHash ?: "none"}|" +
        "clipExecution=${clipExecutionPlan?.canonicalIdentity() ?: "none"}|" +
        "clipProducer=${clipProducerAuthority?.selectorIdentity ?: "none"}|target=$targetStateHash|" +
        "order=$originalPaintOrder|generation=$resourceGeneration|" +
        "diagnostics=${diagnostics.joinToString(";") { diagnostic ->
            "${diagnostic.code}|${diagnostic.passId}|${diagnostic.invocationId}|${diagnostic.terminal}"
        }}"

private fun GPUDrawSemanticPayload.stableDump(): String {
    val ref = payloadRef
    val block = ref.uniformBlock
    val common = "$canonicalType(command=${ref.commandIdValue},step=${ref.renderStepIdentity}," +
        "slot=${ref.uniformSlot?.let { "${it.slotId.value},${it.fingerprint.value},${it.byteOffset}" } ?: "none"}," +
        "fingerprint=${block?.fingerprint?.value ?: "none"},packing=${block?.packingPlanHash ?: "none"}," +
        "byteSize=${block?.byteSize ?: 0},zeroedPadding=${block?.zeroedPadding ?: false}," +
        "bytes=${block?.bytes?.joinToString(",") ?: "none"}," +
        "fields=${block?.fields?.joinToString(",") { field ->
            "${field.fieldPath}@${field.byteOffset}+${field.byteSize}:${field.valueClass}:zero=${field.zeroFilled}"
        } ?: "none"}"
    return when (this) {
        is GPUDrawSemanticPayload.SolidRect -> "$common)"
        is GPUDrawSemanticPayload.Vertices ->
            "$common,preparedVerticesHash=$canonicalHash,artifact=${artifact.key}," +
                "topology=${topologyIdentity.sourceLabel},transform=${transformBytes.joinToString(",")}," +
                "target=$targetBounds,scissor=$scissorBounds,targetFormat=$targetFormat," +
                "clip=$clipIdentity,coverage=$clipCoverageIdentity," +
                "primitiveBlend=${primitiveBlendIdentity ?: "none"},finalBlend=$finalBlendIdentity," +
                "capability=$capabilitySnapshotHash,drawProvenance=$drawProvenance," +
                "frameProvenance=${frameProvenance.annotationValue})"
        is GPUDrawSemanticPayload.CorePrimitive ->
            "$common,corePrimitiveHash=$canonicalHash,family=${sourceFamily.name}," +
                "geometry=${geometry.canonicalType},color=${premultipliedRgba.joinToString(",")}," +
                "target=$targetBounds,scissor=$scissorBounds,clip=${clipCoveragePlan.stableCoreDump()}," +
                "clipExecution=${clipExecutionPlanIdentity ?: "none"}," +
                "blend=$blendPlanIdentity,provenance=${frameProvenance.annotationValue})"
        is GPUDrawSemanticPayload.RegisteredUniformRect ->
            "$common,program=${program.wireId},registeredUniformHash=$canonicalHash," +
                "uniformBytes=${uniformBytes.size},target=$targetBounds,scissor=$scissorBounds)"
        is GPUDrawSemanticPayload.SeparableBlurRect ->
            "$common,separableBlurHash=$canonicalHash,source=$sourceBounds,target=$targetBounds," +
                "sigma=$effectiveSigma,taps=$tapCount,weights=${weights.joinToString(",")}," +
                "sourcePremul=${sourcePremultipliedRgba.joinToString(",")}," +
                "clearPremul=${clearPremultipliedRgba.joinToString(",")})"
        is GPUDrawSemanticPayload.SampledImage ->
            "$common,${stableDumpLine()})"
        is GPUDrawSemanticPayload.TextA8 ->
            "$common,textA8Hash=$canonicalHash," +
                "atlas=${atlas.key}@${atlasGeneration.value}/${atlas.contentHash}:page=$pageIndex," +
                "atlasKey=${atlas.key},atlasWidth=${atlas.width},atlasHeight=${atlas.height}," +
                "atlasRowBytes=${atlas.rowBytes},atlasContentHash=${atlas.contentHash}," +
                "instances=${instances.size},material=${material.materialKey}/${material.abiHash}," +
                "deviceToLocalBits=${deviceToLocal.rawBits().joinToString(",")}," +
                "clip=$clipIdentity,blend=$blendPlanIdentity,capability=$capabilitySnapshotHash," +
                "provenance=${frameProvenance.annotationValue},target=$targetBounds,scissor=$scissorBounds)"
        is GPUDrawSemanticPayload.ColorGlyph ->
            "$common,colorGlyphHash=$canonicalHash," +
                "plan=${planArtifactKey.artifactID.value}@${planArtifactKey.generation.value}/" +
                "${planArtifactKey.contentFingerprint}," +
                "atlasArtifact=${atlasArtifactKey.artifactID.value}@${atlasArtifactKey.generation.value}/" +
                "${atlasArtifactKey.contentFingerprint}," +
                "atlasBytesSha256=$atlasBytesSha256," +
                "atlasKey=${atlas.key},atlasRowBytes=${atlas.rowBytes}," +
                "atlasContentHash=${atlas.contentHash}," +
                "atlas=${atlasWidth}x$atlasHeight:${atlasFormat.gpuLabel}:$atlasGeneration," +
                "atlasBytes=${atlas.byteSize},layers=${layers.size}," +
                "vertexFloats=${vertexData.size},indices=${indexData.size},uniformBytes=${uniformBytes.size}," +
                "preparedInstances=${instances.size}," +
                "preparedMaterial=${material?.let { "${it.materialKey}/${it.abiHash}" } ?: "none"}," +
                "preparedClip=${clipIdentity ?: "none"},preparedBlend=${blendPlanIdentity ?: "none"}," +
                "preparedCapability=${capabilitySnapshotHash ?: "none"}," +
                "preparedProvenance=${frameProvenance?.annotationValue ?: "none"}," +
                "target=$targetBounds,scissor=$scissorBounds)"
    }
}

internal fun GPUClipCoveragePlan.stableCoreDump(): String = when (this) {
    GPUClipCoveragePlan.NoClip -> "none"
    is GPUClipCoveragePlan.Scissor ->
        "scissor:${bounds.left.toRawBits()}:${bounds.top.toRawBits()}:" +
            "${bounds.right.toRawBits()}:${bounds.bottom.toRawBits()}"
    is GPUClipCoveragePlan.AnalyticIntersection ->
        "analytic-intersection:${elements.joinToString(";") { element ->
            "${element.operation.name}/${element.kind.name}/vertices=${element.vertexCount}/" +
                "aa=${element.antiAlias}/fill=${element.fillRule.name}/inverse=${element.inverseFill}/" +
                "values=${element.values.joinToString(",") { value -> value.toRawBits().toString() }}"
        }}"
    is GPUClipCoveragePlan.Mask ->
        "mask:$contentKey:${width}x$height:samples=$sampleCount:resolvedBytes=$resolvedBytes:" +
            "requiredBytes=$requiredBytes:${elements.joinToString(";") { element ->
                "${element.operation.name}/${element.kind.name}/vertices=${element.vertexCount}/" +
                    "aa=${element.antiAlias}/fill=${element.fillRule.name}/inverse=${element.inverseFill}/" +
                    "values=${element.values.joinToString(",") { value -> value.toRawBits().toString() }}"
            }}"
    is GPUClipCoveragePlan.Refused -> "refused:$code"
}

private fun GPUFrameResourceUse.stableDump(): String =
    "${resource.value}|${role.name}|${usage.name}|${lifetime.name}|write=$write"

private fun GPUComputeDispatch.stableDump(): String =
    "${programKey.value}|$workgroupCountX,$workgroupCountY,$workgroupCountZ"

private fun GPUResourcePreparationRequest.stableDump(): String {
    val descriptorDump = when (val value = descriptor) {
        is GPUFrameTextureDescriptor ->
            "texture|bounds=${value.logicalBounds}|format=${value.format.value}|samples=${value.sampleCount}"
        is GPUFrameBufferDescriptor ->
            "buffer|bytes=${value.byteSize}|alignment=${value.alignmentBytes}"
    }
    return "${resource.value}|$descriptorDump|role=${role.name}|" +
        "usages=${usages.sortedBy { usage -> usage.name }.joinToString(",") { it.name }}|" +
        "lifetime=${lifetime.name}|bytes=$byteSize|label=$diagnosticLabel"
}

private fun GPUResourceCopyRegion.stableDump(): String =
    "source=$sourceOffsetBytes|destination=$destinationOffsetBytes|bounds=${logicalBounds ?: "none"}|bytes=$byteSize"

private fun GPUDiagnostic.dumpLine(prefix: String): String =
    "$prefix code=${code.value} domain=${domain.name} severity=${severity.name} " +
        "message=$message facts=${facts.toSortedMap().entries.joinToString(",") { (key, value) -> "$key=$value" }} " +
        "terminal=$isTerminal retryable=$isRetryable"

private fun GPUDestinationSnapshotGroupKey.dumpDestinationSourceKey(): String {
    val continuation = sampleContinuation?.let { value ->
        "sampleTarget=${value.target.value} " +
            "sampleTargetGeneration=${value.targetGeneration} " +
            "sampleDeviceGeneration=${value.deviceGeneration.value} " +
            "sampleFormat=${value.colorFormat.value} " +
            "sampleColor=${value.colorInterpretation.value} " +
            "sampleCount=${value.samplePlan.sampleCount} " +
            "attachmentAuthority=${value.attachmentAuthority.name} " +
            "colorAttachment=${value.colorAttachment.value} " +
            "depthStencilAttachment=${value.depthStencilAttachment?.value ?: "none"}"
    } ?: "sampleContinuation=none"
    return "sourceTarget=${target.value} targetGeneration=$targetGeneration " +
        "deviceGeneration=${deviceGeneration.value} format=${format.value} " +
        "color=${colorInterpretation.value} $continuation " +
        "sourceIntermediate=${sourceIntermediate?.value ?: "none"}"
}

private fun GPUDestinationSnapshotConsumerRef.dumpDestinationConsumer(): String =
    "consumerGrouping=$groupingCommandId,consumerTask=${renderTaskId.value}," +
        "consumerPacket=${packetId.value},consumerCommand=${commandId.value}"
