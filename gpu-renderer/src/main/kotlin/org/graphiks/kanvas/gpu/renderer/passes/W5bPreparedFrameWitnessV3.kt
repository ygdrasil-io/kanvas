package org.graphiks.kanvas.gpu.renderer.passes

import org.graphiks.kanvas.gpu.plan.RenderGraph
import org.graphiks.kanvas.gpu.plan.PlanPass
import org.graphiks.kanvas.gpu.plan.BlendPlan
import org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlan
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameCapabilitySeal
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameReadbackRequest
import org.graphiks.kanvas.gpu.renderer.recording.GPURecordingSeal
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskID
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskDependency
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskUseToken
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskPhase
import org.graphiks.kanvas.gpu.renderer.recording.snapshotForFramePlan
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryBudgetPlan
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourcePreparationRequest
import org.graphiks.kanvas.gpu.renderer.collections.immutableList
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.gpu.renderer.state.GPULoadStorePlan
import org.graphiks.kanvas.gpu.renderer.state.GPUStorePlan

/** Unforgeable renderer token for the sealed initial clear, never a synthetic draw. */
class W5bInitialClearV3 private constructor(
    internal val witness: W5bPreparedFrameWitnessV3?,
    internal val clearOnly: W5bClearOnlyFrameWitnessV3?,
) {
    internal constructor(witness: W5bPreparedFrameWitnessV3) : this(witness, null)
    internal constructor(witness: W5bClearOnlyFrameWitnessV3) : this(null, witness)
    internal val graph: RenderGraph get() = witness?.graph ?: requireNotNull(clearOnly).graph
    internal fun matches(target: GPUFrameTargetRef,
        loadStore: GPULoadStorePlan, sample: GPUSamplePlan): Boolean {
        val first = graph.passes().filterIsInstance<PlanPass.RenderPass>().firstOrNull() ?: return false
        return first.draws().isEmpty() && first.destinationVersionAfter?.valueI64 == 0L &&
            first.load == org.graphiks.kanvas.gpu.plan.AttachmentLoadPlan.ClearTransparent &&
            first.store == org.graphiks.kanvas.gpu.plan.AttachmentStorePlan.Store &&
            target == (witness?.scratch?.target ?: clearOnly?.target) && loadStore == GPULoadStorePlan("clear", GPUStorePlan.Store) &&
            sample == GPUSamplePlan.SingleSampleFrame
    }
}

/** Output initialization only: no material, geometry scratch, pipeline or destination snapshot. */
internal class W5bClearOnlyFrameWitnessV3(
    val graph: RenderGraph,
    val target: GPUFrameTargetRef,
    val staging: org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferRef,
    private val capabilitySeal: GPUFrameCapabilitySeal,
    private val recordingSeal: GPURecordingSeal,
    memoryBudget: GPUFrameMemoryBudgetPlan,
    targetPreparation: GPUResourcePreparationRequest,
    stagingPreparation: GPUResourcePreparationRequest,
    private val readbackRequest: GPUFrameReadbackRequest,
) {
    val capabilitySealHash: String get() = capabilitySeal.sealHash
    val prepareTaskId = GPUTaskID("task.w5b.${graph.id.value}.prepare")
    val clearTaskId = GPUTaskID("task.w5b.${graph.id.value}.initial-clear")
    val readbackTaskId = GPUTaskID("task.w5b.${graph.id.value}.readback")
    val dependencies = immutableList(listOf(prepareTaskId, clearTaskId, readbackTaskId).zipWithNext { before, after ->
        GPUTaskDependency(before, after, "w5b-clear-order", GPUTaskUseToken("${before.value}->${after.value}"), "w5b-clear-order")
    })
    private val preparations = immutableList(listOf(targetPreparation, stagingPreparation))
    private val memory = memoryBudget.snapshotForFramePlan()

    init {
        require(graph.visualCommandCount == 0 && graph.materialPlanTableOrNull() == null)
        require(graph.passes().size == 2 && graph.resources().size == 2)
        val clear = graph.passes().first() as PlanPass.RenderPass
        require(clear.draws().isEmpty() && clear.destinationVersionAfter?.valueI64 == 0L)
        require(clear.load == org.graphiks.kanvas.gpu.plan.AttachmentLoadPlan.ClearTransparent)
        require(clear.store == org.graphiks.kanvas.gpu.plan.AttachmentStorePlan.Store)
        require(graph.passes().last() is PlanPass.ReadbackPass)
        require(targetPreparation.resource == target && stagingPreparation.resource == staging)
        require(recordingSeal.capabilitySealHash == capabilitySealHash)
    }

    /** Exact post-surface-split frame: no filtered-away steps or additional authorities. */
    fun validates(frame: GPUFramePlan): Boolean {
        if (frame.steps.size != 3 || frame.capabilitySeal !== capabilitySeal || frame.frameId != capabilitySeal.frameId ||
            frame.recordingSeals != listOf(recordingSeal) || frame.memoryBudget != memory ||
            frame.dependencies != dependencies || frame.phaseOrder != GPUTaskPhase.entries ||
            frame.diagnostics.isNotEmpty() || frame.elidedNoOpDraws.isNotEmpty() || frame.atomicallyRefused) return false
        val prepare = frame.steps[0] as? GPUFrameStep.PrepareResourcesStep ?: return false
        val clear = frame.steps[1] as? GPUFrameStep.RenderPassStep ?: return false
        val readback = frame.steps[2] as? GPUFrameStep.ReadbackCopyStep ?: return false
        return prepare.sourceTaskIds == listOf(prepareTaskId) && prepare.requests == preparations &&
            clear.sourceTaskIds == listOf(clearTaskId) && clear.w5bInitialClearV3?.clearOnly === this &&
            clear.drawPackets.isEmpty() && clear.target == target && clear.loadStore == GPULoadStorePlan("clear", GPUStorePlan.Store) &&
            clear.samplePlan == GPUSamplePlan.SingleSampleFrame && clear.resourceUses.isEmpty() && clear.batches.isEmpty() &&
            clear.frameProvenanceByPacketId.isEmpty() && clear.preparedImageBindingsByPacketId.isEmpty() &&
            clear.preparedTextBindingsByPacketId.isEmpty() && clear.sampleContinuation == null &&
            clear.depthStencilLoadStore == null && clear.w4eMaskContinuation == null && clear.w4eSceneContinuation == null &&
            readback.sourceTaskIds == listOf(readbackTaskId) && readback.request == readbackRequest &&
            readback.source == target && readback.staging == staging &&
            readback.request.requestId.value == "w3.${graph.id.value}.readback"
    }
}

/** Separate W5b execution authority; the W3 object is reused solely for exact V/I/U packing. */
internal class W5bPreparedFrameWitnessV3(
    val graph: RenderGraph,
    val scratch: W3SessionScratchV1,
    val clipPrefixV4: org.graphiks.kanvas.gpu.renderer.planning.W4eClipGraphLowerer.ClipPrefixV4? = null,
) {
    init {
        require(graph.id.value == scratch.planId)
        require(graph.passes().any { it is PlanPass.TextureCopy } ||
            graph.capabilityId == org.graphiks.kanvas.gpu.plan.W5bCorePrimitiveGraph.CAPABILITY_ID)
        require(scratch.fitsDeviceLimits(graph.capabilities.maxBufferSizeBytes,
            graph.capabilities.maxDynamicUniformBuffersPerPipelineLayout.toLong()))
    }

    fun validates(frame: GPUFramePlan): Boolean {
        val allRenders = frame.steps.filterIsInstance<GPUFrameStep.RenderPassStep>()
        val prefix = clipPrefixV4
        val prefixRenders = if (prefix == null) emptyList() else allRenders.take(prefix.renders.size)
        if (prefix != null) {
            val prepare = frame.steps.firstOrNull() as? GPUFrameStep.PrepareResourcesStep ?: return false
            if (frame.steps.count { it is GPUFrameStep.PrepareResourcesStep } != 1 ||
                frame.steps.any { it !is GPUFrameStep.PrepareResourcesStep && it !is GPUFrameStep.RenderPassStep &&
                    it !is GPUFrameStep.CopyDestinationStep && it !is GPUFrameStep.ReadbackCopyStep } ||
                frame.steps.lastOrNull() !is GPUFrameStep.ReadbackCopyStep ||
                prepare.sourceTaskIds.singleOrNull()?.value != "task.w5b.${graph.id.value}.prepare" ||
                prepare.requests.size != prefix.preparations.size + 3 ||
                prepare.requests.map { it.resource.value }.toSet() !=
                (prefix.preparations.map { it.resource.value } + scratch.target.value + scratch.staging.value +
                    (scratch.target.value.removeSuffix(".target") + ".snapshot")).toSet() ||
                frame.memoryBudget.targetResidentBytes + frame.memoryBudget.peakFrameTransientBytes != graph.peakFrameLocalBytes) return false
            val taskIds = frame.steps.map { it.sourceTaskIds.singleOrNull() ?: return false }
            if (frame.dependencies.map { it.fromTaskId to it.toTaskId } != taskIds.zipWithNext()) return false
        }
        if (prefix != null && (!prefix.authority.validatesRenderSteps(frame.frameId.value, frame.capabilitySeal.sealHash, prefixRenders) ||
            prefixRenders.map { it.sourceTaskIds.singleOrNull() } != prefix.renders.map { it.taskId } ||
            frame.steps.filterIsInstance<GPUFrameStep.PrepareResourcesStep>().flatMap { it.requests }
                .filter { request -> prefix.preparations.any { it.resource == request.resource } } != prefix.preparations)) return false
        val renders = allRenders.drop(prefixRenders.size)
        val planned = graph.passes().filterIsInstance<PlanPass.RenderPass>()
        if (renders.size != planned.size || frame.capabilitySeal.sealHash != scratch.capabilitySealHash) return false
        val operations = frame.steps.filter { it is GPUFrameStep.RenderPassStep ||
            it is GPUFrameStep.CopyDestinationStep || it is GPUFrameStep.ReadbackCopyStep }
        if (operations.size != graph.passes().size) return false
        if (operations.zip(graph.passes()).any { (actual, expected) -> when (expected) {
            is PlanPass.RenderPass -> actual !is GPUFrameStep.RenderPassStep || actual.target != scratch.target ||
                (if (expected.draws().isEmpty()) actual.w5bInitialClearV3?.witness !== this else actual.w5bInitialClearV3 != null) ||
                actual.samplePlan != GPUSamplePlan.SingleSampleFrame || actual.loadStore.loadOp !=
                (if (expected.load == org.graphiks.kanvas.gpu.plan.AttachmentLoadPlan.ClearTransparent) "clear" else "load")
            is PlanPass.TextureCopy -> actual !is GPUFrameStep.CopyDestinationStep ||
                actual.source != scratch.target || actual.snapshot.value != scratch.target.value.removeSuffix(".target") + ".snapshot" ||
                actual.logicalBounds != scratch.targetBounds || actual.consumers.size != 1 ||
                actual.sourceKey.deviceGeneration != frame.capabilitySeal.deviceGeneration
            is PlanPass.ReadbackPass -> actual !is GPUFrameStep.ReadbackCopyStep || actual.source != scratch.target ||
                actual.staging != scratch.staging || actual.request.requestId.value != "w3.${graph.id.value}.readback"
            is PlanPass.ClipMaskInitialize, is PlanPass.ClipMaskProducer, is PlanPass.ClipMaskFold ->
                prefix == null || actual !is GPUFrameStep.RenderPassStep || actual !in prefixRenders ||
                    actual.drawPackets.singleOrNull()?.w4ePreparedClipPass?.passId != expected.id.value
            else -> true
        } }) return false
        if (renders.zip(planned).any { (actual, sealed) ->
            actual.drawPackets.map { it.commandIdValue } != sealed.draws().map { it.commandIndex } ||
                actual.drawPackets.any { it.corePrimitivePreparedAuthority?.w5bFrameWitnessV3 !== this } ||
                actual.drawPackets.zip(sealed.draws()).any { (packet, draw) ->
                    val expected = draw.blend as? BlendPlan.DestinationReadV1
                    (packet.blendPlan as? GPUBlendPlan.ShaderBlendWithDstRead)?.sealedW5b != expected
                }
        }) return false
        val packets = renders.flatMap { it.drawPackets }
        return scratch.packetIds == packets.map { it.packetId } &&
            scratch.commandIds == packets.map { it.commandIdValue } &&
            scratch.hasExactUniformPayloads(graph.capabilities.minUniformBufferOffsetAlignment.toLong(), packets)
    }
}
