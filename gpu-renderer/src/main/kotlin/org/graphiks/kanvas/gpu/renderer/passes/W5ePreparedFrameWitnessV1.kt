package org.graphiks.kanvas.gpu.renderer.passes

import org.graphiks.kanvas.gpu.plan.*
import org.graphiks.kanvas.gpu.renderer.materials.W5aPacketMaterialSourceV2
import org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlan
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep

/** Complete image/geometry/source mapping, issued once after authentic W5b construction lowering. */
internal class W5ePreparedFrameWitnessV1(val bridge: W5eImageConstructionPlanV1, packets: List<GPUDrawPacket>) {
    private val packets = packets.toList()
    private val geometry = requireNotNull(packets.firstOrNull()?.w5bFinalFrameWitnessV3)
    val sources: Map<GPUDrawPacketID, W5aPacketMaterialSourceV2> = bridge.imageDraws().zip(packets).associate { (image, packet) ->
        require(bridge.materialTable.authenticatesImage(image.materialAuthority.ref, image.execution)) { W5eImagePlanDiagnostics.InvalidContract }
        packet.packetId to W5aPacketMaterialSourceV2.issueImageV3(bridge.materialTable, image.materialAuthority.ref, image.commandIndex)
    }
    val canonicalIdentity: String = bridge.canonicalIdentity
    init {
        require(geometry.graph === bridge.constructionGraph && packets.size == bridge.imageDraws().size &&
            packets.map { it.commandIdValue } == bridge.imageDraws().map { it.commandIndex } &&
            packets.all { it.w5bFinalFrameWitnessV3 === geometry && it.role == GPUDrawPacketRole.Shading }) {
            W5eImagePlanDiagnostics.InvalidContract
        }
    }
    fun owns(packet: GPUDrawPacket): Boolean = packets.any { it === packet } &&
        packet.w5bFinalFrameWitnessV3 === geometry && sources[packet.packetId]?.commandIdI32 == packet.commandIdValue
    fun validates(frame: GPUFramePlan): Boolean {
        val actual = frame.steps.filterIsInstance<GPUFrameStep.RenderPassStep>().flatMap { it.drawPackets }
        return geometry.validates(frame) && actual.size == packets.size && actual.zip(packets).all { (a, b) -> a === b } &&
            actual.all { it.w5eImageFrameWitnessV1 === this && owns(it) } &&
            frame.capabilitySeal.deviceGeneration.value == bridge.constructionGraph.capabilities.deviceGeneration &&
            bridge.peakBytesI64 <= frame.memoryBudget.configuredAggregateBudgetBytes
    }
}

/** Construction provenance makes a missing V3 binding terminal; neutral source cannot escape. */
internal fun GPUDrawPacket.materialSourcePartitionV3(): W5aPacketMaterialSourceV2? {
    if (w5bFinalFrameWitnessV3?.graph?.capabilityId != W5eImagePlanCompiler.CONSTRUCTION_CAPABILITY_ID) {
        require(w5eImageFrameWitnessV1 == null) { W5eImagePlanDiagnostics.InvalidContract }
        return w5aSourceStageV2
    }
    val witness = requireNotNull(w5eImageFrameWitnessV1) { W5eImagePlanDiagnostics.InvalidContract }
    require(witness.owns(this)) { W5eImagePlanDiagnostics.InvalidContract }
    return requireNotNull(witness.sources[packetId]) { W5eImagePlanDiagnostics.InvalidContract }
}
