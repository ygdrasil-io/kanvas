package org.graphiks.kanvas.gpu.renderer.passes

import org.graphiks.kanvas.gpu.plan.RenderGraph
import org.graphiks.kanvas.gpu.plan.PlanPass
import org.graphiks.kanvas.gpu.plan.BlendPlan
import org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlan
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.gpu.renderer.state.GPULoadStorePlan

/** Unforgeable renderer token for the sealed initial clear, never a synthetic draw. */
class W5bInitialClearV3 internal constructor(internal val witness: W5bPreparedFrameWitnessV3) {
    internal fun matches(target: GPUFrameTargetRef,
        loadStore: GPULoadStorePlan, sample: GPUSamplePlan): Boolean {
        val first = witness.graph.passes().firstOrNull() as? PlanPass.RenderPass ?: return false
        return first.draws().isEmpty() && first.destinationVersionAfter?.valueI64 == 0L &&
            first.load == org.graphiks.kanvas.gpu.plan.AttachmentLoadPlan.ClearTransparent &&
            target == witness.scratch.target && loadStore.loadOp == "clear" &&
            sample == GPUSamplePlan.SingleSampleFrame
    }
}

/** Separate W5b execution authority; the W3 object is reused solely for exact V/I/U packing. */
internal class W5bPreparedFrameWitnessV3(
    val graph: RenderGraph,
    val scratch: W3SessionScratchV1,
) {
    init {
        require(graph.id.value == scratch.planId)
        require(graph.passes().any { it is PlanPass.TextureCopy })
        require(scratch.fitsDeviceLimits(graph.capabilities.maxBufferSizeBytes,
            graph.capabilities.maxDynamicUniformBuffersPerPipelineLayout.toLong()))
    }

    fun validates(frame: GPUFramePlan): Boolean {
        val renders = frame.steps.filterIsInstance<GPUFrameStep.RenderPassStep>()
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
