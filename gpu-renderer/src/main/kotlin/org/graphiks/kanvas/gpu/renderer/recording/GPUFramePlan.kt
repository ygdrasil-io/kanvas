package org.graphiks.kanvas.gpu.renderer.recording

import java.security.MessageDigest
import org.graphiks.kanvas.gpu.renderer.color.GPUColorFormat
import org.graphiks.kanvas.gpu.renderer.color.GPUColorInterpretation
import org.graphiks.kanvas.gpu.renderer.commands.GPUDrawCommandID
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnostic
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket
import org.graphiks.kanvas.gpu.renderer.passes.GPUSamplePlan
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
) {
    init {
        require(insertionOrder >= 0L) { "GPURecordingSeal.insertionOrder must be non-negative" }
        require(compatibilityKeyHash.isNotBlank()) {
            "GPURecordingSeal.compatibilityKeyHash must not be blank"
        }
        require(replayKeyHash.isNotBlank()) { "GPURecordingSeal.replayKeyHash must not be blank" }
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
        drawPackets: List<GPUDrawPacket>,
        sourceTaskIds: List<GPUTaskID>,
    ) : GPUFrameStep {
        val drawPackets: List<GPUDrawPacket> = drawPackets.toList()
        override val sourceTaskIds: List<GPUTaskID> = sourceTaskIds.toList()
        override val executionKind = GPUFrameStepExecutionKind.Encoder
    }

    class ComputePassStep(
        val target: GPUFrameTargetRef,
        resourceUses: List<GPUFrameResourceUse>,
        dispatches: List<GPUComputeDispatch>,
        sourceTaskIds: List<GPUTaskID>,
    ) : GPUFrameStep {
        val resourceUses: List<GPUFrameResourceUse> = resourceUses.toList()
        val dispatches: List<GPUComputeDispatch> = dispatches.toList()
        override val sourceTaskIds: List<GPUTaskID> = sourceTaskIds.toList()
        override val executionKind = GPUFrameStepExecutionKind.Encoder
    }

    class PrepareResourcesStep(
        requests: List<GPUResourcePreparationRequest>,
        sourceTaskIds: List<GPUTaskID>,
    ) : GPUFrameStep {
        val requests: List<GPUResourcePreparationRequest> = requests.toList()
        override val sourceTaskIds: List<GPUTaskID> = sourceTaskIds.toList()
        override val executionKind = GPUFrameStepExecutionKind.Preflight
    }

    class UploadResourceStep(
        val staging: GPUFrameBufferRef,
        val destination: GPUFrameResourceRef,
        val layout: GPUUploadLayout,
        sourceTaskIds: List<GPUTaskID>,
    ) : GPUFrameStep {
        override val sourceTaskIds: List<GPUTaskID> = sourceTaskIds.toList()
        override val executionKind = GPUFrameStepExecutionKind.Encoder
    }

    class CopyResourceStep(
        val source: GPUFrameResourceRef,
        val destination: GPUFrameResourceRef,
        regions: List<GPUResourceCopyRegion>,
        sourceTaskIds: List<GPUTaskID>,
    ) : GPUFrameStep {
        val regions: List<GPUResourceCopyRegion> = regions.toList()
        override val sourceTaskIds: List<GPUTaskID> = sourceTaskIds.toList()
        override val executionKind = GPUFrameStepExecutionKind.Encoder
    }

    class DependencyBarrierStep(
        orderedUseTokens: List<GPUTaskUseToken>,
        val reasonCode: String,
        sourceTaskIds: List<GPUTaskID>,
    ) : GPUFrameStep {
        val orderedUseTokens: List<GPUTaskUseToken> = orderedUseTokens.toList()
        override val sourceTaskIds: List<GPUTaskID> = sourceTaskIds.toList()
        override val executionKind = GPUFrameStepExecutionKind.DependencyOnly

        init {
            require(reasonCode.isNotBlank()) {
                "GPUFrameStep.DependencyBarrierStep.reasonCode must not be blank"
            }
        }
    }

    class CopyDestinationStep(
        val source: GPUFrameTargetRef,
        val snapshot: GPUFrameTextureRef,
        val logicalBounds: GPUPixelBounds,
        val copyLayout: GPUTextureCopyLayout,
        sourceTaskIds: List<GPUTaskID>,
    ) : GPUFrameStep {
        override val sourceTaskIds: List<GPUTaskID> = sourceTaskIds.toList()
        override val executionKind = GPUFrameStepExecutionKind.Encoder
    }

    class CopyAsDrawMaterializationStep(
        val source: GPUFrameTargetRef,
        val snapshot: GPUFrameTextureRef,
        val logicalBounds: GPUPixelBounds,
        sourceTaskIds: List<GPUTaskID>,
    ) : GPUFrameStep {
        override val sourceTaskIds: List<GPUTaskID> = sourceTaskIds.toList()
        override val executionKind = GPUFrameStepExecutionKind.Encoder
    }

    class TargetTransitionStep(
        val parent: GPUFrameTargetRef,
        val child: GPUFrameTargetRef,
        val transitionKind: GPUTargetTransitionKind,
        sourceTaskIds: List<GPUTaskID>,
    ) : GPUFrameStep {
        override val sourceTaskIds: List<GPUTaskID> = sourceTaskIds.toList()
        override val executionKind = GPUFrameStepExecutionKind.DependencyOnly
    }

    class ReadbackCopyStep(
        val source: GPUFrameTargetRef,
        val staging: GPUFrameBufferRef,
        val request: GPUFrameReadbackRequest,
        sourceTaskIds: List<GPUTaskID>,
    ) : GPUFrameStep {
        override val sourceTaskIds: List<GPUTaskID> = sourceTaskIds.toList()
        override val executionKind = GPUFrameStepExecutionKind.Encoder
    }

    class AcquireSurfaceOutput(
        val descriptor: GPUSurfaceOutputDescriptor,
        sourceTaskIds: List<GPUTaskID>,
    ) : GPUFrameStep {
        override val sourceTaskIds: List<GPUTaskID> = sourceTaskIds.toList()
        override val executionKind = GPUFrameStepExecutionKind.Preflight
    }

    class SurfaceBlitRenderPassStep(
        val scene: GPUFrameTargetRef,
        val output: GPUSurfaceOutputRef,
        sourceTaskIds: List<GPUTaskID>,
    ) : GPUFrameStep {
        override val sourceTaskIds: List<GPUTaskID> = sourceTaskIds.toList()
        override val executionKind = GPUFrameStepExecutionKind.Encoder
    }

    class PostSubmitPresentAction(
        val output: GPUSurfaceOutputRef,
        sourceTaskIds: List<GPUTaskID>,
    ) : GPUFrameStep {
        override val sourceTaskIds: List<GPUTaskID> = sourceTaskIds.toList()
        override val executionKind = GPUFrameStepExecutionKind.PostSubmitHost
    }

    class RefusedLeafDrawStep(
        val commandId: GPUDrawCommandID,
        diagnostic: GPUDiagnostic,
        sourceTaskIds: List<GPUTaskID>,
    ) : GPUFrameStep {
        val diagnostic: GPUDiagnostic = diagnostic.snapshot()
        override val sourceTaskIds: List<GPUTaskID> = sourceTaskIds.toList()
        override val executionKind = GPUFrameStepExecutionKind.RefusalEvidence
    }

    class RefusedCompositeCommandStep(
        val commandId: GPUDrawCommandID,
        provenanceTokens: List<GPUCompositeProvenanceToken>,
        diagnostic: GPUDiagnostic,
        sourceTaskIds: List<GPUTaskID>,
    ) : GPUFrameStep {
        val provenanceTokens: List<GPUCompositeProvenanceToken> = provenanceTokens.toList()
        val diagnostic: GPUDiagnostic = diagnostic.snapshot()
        override val sourceTaskIds: List<GPUTaskID> = sourceTaskIds.toList()
        override val executionKind = GPUFrameStepExecutionKind.RefusalEvidence
    }
}

/** Immutable deterministic result of task-list linearization. */
class GPUFramePlan(
    val frameId: GPUFrameID,
    recordingSeals: List<GPURecordingSeal>,
    steps: List<GPUFrameStep>,
    memoryBudget: GPUFrameMemoryBudgetPlan,
    diagnostics: List<GPUDiagnostic>,
    val atomicallyRefused: Boolean = false,
) {
    val recordingSeals: List<GPURecordingSeal> = recordingSeals.toList()
    val steps: List<GPUFrameStep> = steps.toList()
    val memoryBudget: GPUFrameMemoryBudgetPlan = memoryBudget.snapshotForFramePlan()
    val diagnostics: List<GPUDiagnostic> = diagnostics.map(GPUDiagnostic::snapshot)

    fun dumpLines(): List<String> =
        listOf(
            "frame id=${frameId.value} refused=$atomicallyRefused seals=${recordingSeals.size} " +
                "steps=${steps.size} diagnostics=${diagnostics.size}",
            memoryBudget.dumpLine(),
        ) +
            recordingSeals.map { seal ->
                "seal recording=${seal.recordingId.value} insertion=${seal.insertionOrder} " +
                    "compatibility=${seal.compatibilityKeyHash} replay=${seal.replayKeyHash}"
            } +
            steps.mapIndexed { index, step -> step.dumpLine(index) } +
            diagnostics.mapIndexed { index, diagnostic -> diagnostic.dumpLine("diagnostic[$index]") }

    fun stableHash(): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(dumpLines().joinToString("\n").toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }
}

private fun GPUDiagnostic.snapshot(): GPUDiagnostic = copy(facts = facts.toMap())

internal fun GPUFrameMemoryBudgetPlan.snapshotForFramePlan(): GPUFrameMemoryBudgetPlan =
    copy(
        categoryTotals = categoryTotals.toMap(),
        deviceLimitFacts = deviceLimitFacts.toList(),
        diagnostic = diagnostic?.snapshot(),
    )

private fun GPUFrameStep.dumpLine(index: Int): String {
    val tasks = sourceTaskIds.joinToString(",", transform = GPUTaskID::value)
    val body = when (this) {
        is GPUFrameStep.RenderPassStep ->
            "render target=${target.value} load=${loadStore.loadOp} store=${loadStore.storePlan.name} " +
                "clear=${loadStore.clearColorLabel ?: "none"} sample=${samplePlan.specializationKey} " +
                "packets=${drawPackets.joinToString(";") { packet -> packet.stableDump() }}"
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
            "destination-copy source=${source.value} snapshot=${snapshot.value} bounds=$logicalBounds " +
                "bytesPerRow=${copyLayout.bytesPerRow} rowsPerImage=${copyLayout.rowsPerImage}"
        is GPUFrameStep.CopyAsDrawMaterializationStep ->
            "copy-as-draw source=${source.value} snapshot=${snapshot.value} bounds=$logicalBounds"
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
        "uniform=${uniformSlot?.slotId?.value ?: "none"}|resource=${resourceSlot?.slotId?.value ?: "none"}|" +
        "vertex=$vertexSourceLabel|scissor=${scissorBoundsHash ?: "none"}|target=$targetStateHash|" +
        "order=$originalPaintOrder|generation=$resourceGeneration|" +
        "diagnostics=${diagnostics.joinToString(";") { diagnostic ->
            "${diagnostic.code}|${diagnostic.passId}|${diagnostic.invocationId}|${diagnostic.terminal}"
        }}"

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
