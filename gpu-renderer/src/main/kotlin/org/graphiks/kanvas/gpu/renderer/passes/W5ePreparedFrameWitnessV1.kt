package org.graphiks.kanvas.gpu.renderer.passes

import org.graphiks.kanvas.gpu.plan.*
import org.graphiks.kanvas.gpu.renderer.materials.W5aPacketMaterialSourceV2
import org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlan
import org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlanner
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep
import org.graphiks.kanvas.gpu.renderer.recording.GPUTask
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskList

/**
 * Exact typed source overlay, sealed before native allocation. Only logical packets, immutable
 * plans and identities are retained; no native handle is captured. Public type visibility lets
 * the frame transport carry the proof, while issuance and consumption remain renderer-owned.
 */
public class W5ePreparedFrameWitnessV1 internal constructor(internal val bridge: W5eImageConstructionPlanV1, base: GPUTaskList) {
    private val packets = base.tasks.filterIsInstance<GPUTask.Render>().flatMap { it.drawPackets }
    internal val canonicalIdentity: String = bridge.canonicalIdentity
    private val expectedByCommandI32 = bridge.imageDraws().associateBy { it.commandIndex }
    private val ordinaryByCommandI32 = bridge.ordinarySources()
    private val consumers = packets.filter { packet ->
        packet.commandIdValue in expectedByCommandI32.keys + ordinaryByCommandI32.keys && packet.isColorConsumerV3()
    }
    private val sources: Map<GPUDrawPacketID, W5aPacketMaterialSourceV2> = consumers.associate { packet ->
        val image = expectedByCommandI32[packet.commandIdValue]
        val source = if (image != null) {
            require(bridge.materialTable.authenticatesImage(image.materialAuthority.ref, image.execution)) { W5eImagePlanDiagnostics.InvalidContract }
            W5aPacketMaterialSourceV2.issueImageV3(bridge.materialTable, image.materialAuthority.ref, image.commandIndex)
        } else when (val authority = ordinaryByCommandI32.getValue(packet.commandIdValue)) {
            is PlanDrawMaterialAuthority.MaterialV1 -> W5aPacketMaterialSourceV2.issue(bridge.materialTable,
                authority.ref, packet.commandIdValue, authority.coordinates)
            is PlanDrawMaterialAuthority.MaterialV2 -> W5aPacketMaterialSourceV2.issue(bridge.materialTable,
                authority.ref, packet.commandIdValue, authority.coordinates)
            else -> error(W5eImagePlanDiagnostics.InvalidContract)
        }
        packet.packetId to source
    }
    init {
        require(base.w5eConstructionV1 == null && base.w5ePreparedFrameV1 == null &&
            packets.none { it.commandIdValue in bridge.omittedConstructionIndicesI32() } &&
            packets.map { it.packetId }.distinct().size == packets.size &&
            consumers.groupingBy { it.commandIdValue }.eachCount() ==
                (expectedByCommandI32.keys + ordinaryByCommandI32.keys).associateWith { 1 } &&
            consumers.map { it.commandIdValue } == bridge.colorConstructionOrderI32() &&
            consumers.all { it.w5aSourceStageV2 != null }) { W5eImagePlanDiagnostics.InvalidContract }
        packets.forEach { it.attachW5eImageFrameWitnessV1(this) }
    }
    // Capture the complete, already lowered geometry envelope once. The expected proof is
    // transported independently of packet witnesses, so removing either side fails closed.
    private val frame = GPUFramePlanner.plan(base.withW5eConstructionV1(bridge, this)).also {
        require(!it.atomicallyRefused) { W5eImagePlanDiagnostics.InvalidContract }
    }
    private val frameHash = frame.stableHash()

    internal fun owns(packet: GPUDrawPacket): Boolean = packets.any { it === packet }
    internal fun source(packet: GPUDrawPacket): W5aPacketMaterialSourceV2? {
        require(owns(packet) && packet.w5eImageFrameWitnessV1 === this) { W5eImagePlanDiagnostics.InvalidContract }
        if (packet !in consumers) return packet.w5aSourceStageV2
        return requireNotNull(sources[packet.packetId]) { W5eImagePlanDiagnostics.InvalidContract }
    }
    internal fun validates(actual: GPUFramePlan): Boolean {
        val actualPackets = actual.steps.filterIsInstance<GPUFrameStep.RenderPassStep>().flatMap { it.drawPackets }
        return actual.w5eConstructionV1 === bridge && actual.w5ePreparedFrameV1 === this && actual.capabilitySeal === frame.capabilitySeal &&
            actual.stableHash() == frameHash && actualPackets.size == packets.size &&
            actualPackets.zip(packets).all { (a, b) -> a === b && a.w5eImageFrameWitnessV1 === this } &&
            bridge.peakBytesI64 <= actual.memoryBudget.configuredAggregateBudgetBytes
    }
}

private fun GPUDrawPacket.isColorConsumerV3(): Boolean = when (role) {
    GPUDrawPacketRole.Shading, GPUDrawPacketRole.PathStencilCover -> w5aSourceStageV2 != null
    GPUDrawPacketRole.W4ePrepared -> w4ePreparedPath?.phase in setOf(
        PathRenderPhase.SingleSampleDirectColor, PathRenderPhase.SingleSampleStencilColorCover,
        PathRenderPhase.MultisampleDirectColor, PathRenderPhase.MultisampleStencilColorCover,
        PathRenderPhase.HardEdgeBinaryColorCover)
    else -> false
}

/** Native validates the independently transported frame proof before consuming this partition. */
internal fun GPUDrawPacket.materialSourcePartitionV3(): W5aPacketMaterialSourceV2? =
    w5eImageFrameWitnessV1?.source(this) ?: w5aSourceStageV2
