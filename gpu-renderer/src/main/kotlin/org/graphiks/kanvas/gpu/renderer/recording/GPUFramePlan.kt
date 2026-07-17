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
import org.graphiks.kanvas.gpu.renderer.color.GPUColorFormat
import org.graphiks.kanvas.gpu.renderer.color.GPUColorInterpretation
import org.graphiks.kanvas.gpu.renderer.commands.GPUDrawCommandID
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnostic
import org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationSnapshotGroupKey
import org.graphiks.kanvas.gpu.renderer.intermediates.GPUIntermediateIdentity
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlan
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketID
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchKind
import org.graphiks.kanvas.gpu.renderer.passes.GPUSampleContinuationRequest
import org.graphiks.kanvas.gpu.renderer.passes.GPUSamplePlan
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.pipelines.GPUComputePipelineKey
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryBudgetPlan
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryCategory
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUse
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTextureDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTextureRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceCopyRegion
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourcePreparationRequest
import org.graphiks.kanvas.gpu.renderer.resources.GPUTextureCopyLayout
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
    ) : GPUFrameStep {
        val drawPackets: List<GPUDrawPacket> = immutableList(drawPackets)
        val resourceUses: List<GPUFrameResourceUse> = immutableList(resourceUses)
        val batches: List<GPUFrameRenderBatch> = immutableList(batches)
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
    ) : GPUFrameStep {
        override val sourceTaskIds: List<GPUTaskID> = immutableList(sourceTaskIds)
        override val executionKind = GPUFrameStepExecutionKind.Encoder
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
        "useToken=${useToken?.value ?: "none"} reason=$reasonCode"

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
        categoryTotals = immutableMap(categoryTotals),
        deviceLimitFacts = immutableList(deviceLimitFacts),
        diagnostic = diagnostic?.snapshot(),
    )

private fun GPUCapabilities.canonicalSnapshotHash(): String =
    CanonicalHashSink("GPUCapabilities/v2").apply {
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
            samplePlan("samplePlan", value.samplePlan)
            nullable("sampleContinuation", value.sampleContinuation) { continuation ->
                string("target", continuation.key.target.value)
                long("targetGeneration", continuation.key.targetGeneration)
                long("deviceGeneration", continuation.key.deviceGeneration.value)
                string("colorFormat", continuation.key.colorFormat.value)
                string("colorInterpretation", continuation.key.colorInterpretation.value)
                string("samplePlan", continuation.key.samplePlan.specializationKey)
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
            long("sourceOffsetBytes", value.layout.sourceOffsetBytes)
            long("bytesPerRow", value.layout.bytesPerRow)
            int("rowsPerImage", value.layout.rowsPerImage)
            long("byteSize", value.layout.byteSize)
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
        is GPUDrawSemanticPayload.ColorGlyph -> {
            string("canonicalHash", value.canonicalHash)
            string("planArtifactId", value.planArtifactKey.artifactID.value.toString())
            int("planArtifactGeneration", value.planArtifactKey.generation.value)
            string("planArtifactFingerprint", value.planArtifactKey.contentFingerprint)
            string("atlasArtifactId", value.atlasArtifactKey.artifactID.value.toString())
            int("atlasArtifactGeneration", value.atlasArtifactKey.generation.value)
            string("atlasArtifactFingerprint", value.atlasArtifactKey.contentFingerprint)
            string("atlasBytesSha256", value.atlasBytesSha256)
            long("atlasGeneration", value.atlasGeneration)
            int("atlasWidth", value.atlasWidth)
            int("atlasHeight", value.atlasHeight)
            string("atlasFormat", value.atlasFormat.gpuLabel)
            int("atlasByteCount", value.atlasA8Bytes.size)
            int("layerCount", value.layers.size)
            int("vertexFloatCount", value.vertexData.size)
            int("indexCount", value.indexData.size)
            int("uniformByteCount", value.uniformBytes.size)
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
    val body = when (this) {
        is GPUFrameStep.RenderPassStep ->
            "render target=${target.value} load=${loadStore.loadOp} store=${loadStore.storePlan.name} " +
                "clear=${loadStore.clearColorLabel ?: "none"} sample=${samplePlan.specializationKey} " +
                "uses=${resourceUses.joinToString(";") { it.stableDump() }.ifEmpty { "none" }} " +
                "continuation=${sampleContinuation?.let { continuation ->
                    "${continuation.key.target.value}@${continuation.key.targetGeneration}:" +
                        "${continuation.key.deviceGeneration.value}:" +
                        "${continuation.key.colorAttachment.value}:" +
                        "${continuation.loadTransition.name}:" +
                        "${continuation.storeAction.name}:" +
                        continuation.resolveAction.name
                } ?: "none"} " +
                "batches=${batches.joinToString(";") { batch ->
                    "${batch.batchId}:${batch.kind.name}:${batch.packets.joinToString(",") { it.packetId.value }}"
                }} packets=${drawPackets.joinToString(";") { packet -> packet.stableDump() }}"
        is GPUFrameStep.ComputePassStep ->
            "compute target=${target.value} uses=${resourceUses.joinToString(";") { it.stableDump() }} " +
                "dispatches=${dispatches.joinToString(";") { it.stableDump() }}"
        is GPUFrameStep.PrepareResourcesStep ->
            "prepare resources=${requests.joinToString(";") { it.stableDump() }}"
        is GPUFrameStep.UploadResourceStep ->
            "upload staging=${staging.value} destination=${destination.value} " +
                "offset=${layout.sourceOffsetBytes} bytesPerRow=${layout.bytesPerRow} " +
                "rowsPerImage=${layout.rowsPerImage} bytes=${layout.byteSize}"
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
    }
    return "step index=$index kind=${executionKind.name} tasks=$tasks $body"
}

private fun GPUFrameMemoryBudgetPlan.dumpLine(): String =
    "memory peakTransient=$peakFrameTransientBytes targetResident=$targetResidentBytes " +
        "configured=$configuredAggregateBudgetBytes categories=${GPUFrameMemoryCategory.entries.joinToString(",") { category ->
            "${category.name}:${categoryTotals[category] ?: 0L}"
        }} limits=${deviceLimitFacts.joinToString(";") { fact ->
            "${fact.name}|${fact.source}|${fact.value}|${fact.affectsValidity}|${fact.evidenceLabel}"
        }} budgetDiagnostic=${diagnostic?.dumpLine("budget") ?: "none"}"

private fun GPUDrawPacket.stableDump(): String =
    "${packetId.value}|command=$commandIdValue|analysis=$analysisRecordId|pass=$passId|layer=$layerId|" +
        "bindings=$bindingListId|insertion=$insertionReasonCode|sort=$sortKey|preimage=$sortKeyPreimage|" +
        "step=${renderStepId.value}@$renderStepVersion|role=${role.name}|blend=${blendPlan}|" +
        "renderPipeline=${renderPipelineKey?.value ?: "none"}|" +
        "computePipeline=${computePipelineKey?.value ?: "none"}|layout=$bindingLayoutHash|" +
        "uniform=${uniformSlot?.let { "${it.slotId.value},${it.fingerprint.value},${it.byteOffset}" } ?: "none"}|" +
        "resource=${resourceSlot?.let { "${it.slotId.value},${it.fingerprint.value},${it.bindingIndex}" } ?: "none"}|" +
        "semantic=${semanticPayload?.stableDump() ?: "none"}|" +
        "vertex=$vertexSourceLabel|scissor=${scissorBoundsHash ?: "none"}|target=$targetStateHash|" +
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
        is GPUDrawSemanticPayload.ColorGlyph ->
            "$common,colorGlyphHash=$canonicalHash," +
                "plan=${planArtifactKey.artifactID.value}@${planArtifactKey.generation.value}/" +
                "${planArtifactKey.contentFingerprint}," +
                "atlasArtifact=${atlasArtifactKey.artifactID.value}@${atlasArtifactKey.generation.value}/" +
                "${atlasArtifactKey.contentFingerprint}," +
                "atlasBytesSha256=$atlasBytesSha256," +
                "atlas=${atlasWidth}x$atlasHeight:${atlasFormat.gpuLabel}:$atlasGeneration," +
                "atlasBytes=${atlasA8Bytes.size},layers=${layers.size}," +
                "vertexFloats=${vertexData.size},indices=${indexData.size},uniformBytes=${uniformBytes.size}," +
                "target=$targetBounds,scissor=$scissorBounds)"
    }
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
