package org.graphiks.kanvas.gpu.renderer.recording

import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket
import org.graphiks.kanvas.gpu.renderer.passes.materialSourcePartitionV3

/** One immutable packet/source/template authority, shared by exact native projections. */
internal class GPUW5hSourceAuthorityRootV1(frame: GPUFramePlan) {
    val frameIdentity: String = frame.stableHash()
    private val packets = java.util.Collections.unmodifiableMap(frame.steps
        .filterIsInstance<GPUFrameStep.RenderPassStep>().flatMap { it.drawPackets }
        .filter { it.materialSourcePartitionV3() != null }.associateBy { it.packetId.value })
    private val templates = java.util.Collections.unmodifiableMap(frame.w5aGeometryHostTemplatesV1.associateBy { it.packetId })
    fun template(packet: GPUDrawPacket): GPUW5aGeometryHostTemplateV1? =
        templates[packet.packetId.value].takeIf { packets[packet.packetId.value] === packet }
    fun owns(frame: GPUFramePlan): Boolean {
        if(frame.w5hSourceAuthorityRootV1 !== this) return false
        val selected=frame.steps.filterIsInstance<GPUFrameStep.RenderPassStep>().flatMap { it.drawPackets }
            .filter { it.materialSourcePartitionV3() != null }
        return selected.map { it.packetId.value }.distinct().size == selected.size &&
            frame.w5aGeometryHostTemplatesV1.map { it.packetId } == selected.map { it.packetId.value } && selected.all { packet ->
                packets[packet.packetId.value] === packet && frame.w5aGeometryHostTemplatesV1.singleOrNull {
                    it.packetId == packet.packetId.value } === templates[packet.packetId.value]
            }
    }
}
