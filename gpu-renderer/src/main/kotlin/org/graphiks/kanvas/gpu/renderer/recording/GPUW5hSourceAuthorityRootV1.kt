package org.graphiks.kanvas.gpu.renderer.recording

import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket
import org.graphiks.kanvas.gpu.renderer.passes.materialSourcePartitionV3
import org.graphiks.kanvas.gpu.renderer.passes.commonCoreSemanticAuthority

/** One immutable packet/source/template authority, shared by exact native projections. */
internal class GPUW5hSourceAuthorityRootV1(frame: GPUFramePlan) {
    val frameIdentity: String = frame.stableHash()
    private val packets = java.util.Collections.unmodifiableMap(frame.steps
        .filterIsInstance<GPUFrameStep.RenderPassStep>().flatMap { it.drawPackets }
        .filter { it.materialSourcePartitionV3() != null }.associateBy { it.packetId.value })
    private val templates = java.util.Collections.unmodifiableMap(frame.w5aGeometryHostTemplatesV1.associateBy { it.packetId })
    private val coreBindings = java.util.Collections.unmodifiableMap(packets.mapValues { it.value.commonCoreSemanticAuthority() })
    private val coreDispatchPackets = java.util.Collections.unmodifiableMap(frame.steps
        .filterIsInstance<GPUFrameStep.RenderPassStep>().flatMap { it.drawPackets }
        .filter { it.corePrimitivePreparedAuthority?.materialDispatchPlan != null }.associateBy { it.packetId })
    private val coreDispatches = java.util.Collections.unmodifiableMap(coreDispatchPackets.mapValues { (_, packet) ->
        val dispatch = requireNotNull(packet.corePrimitivePreparedAuthority?.materialDispatchPlan)
        requireNotNull(packet.commonCoreSemanticAuthority())
        require(dispatch.validatesUniformPacket(packet))
        dispatch to dispatch.geometry.arena.packedGeometryHash
    })
    fun coreBinding(packet: GPUDrawPacket) = coreBindings[packet.packetId.value].also {
        require(packets[packet.packetId.value] === packet && packet.commonCoreSemanticAuthority() === it)
    }
    fun template(packet: GPUDrawPacket): GPUW5aGeometryHostTemplateV1? =
        templates[packet.packetId.value].takeIf { packets[packet.packetId.value] === packet }
    fun owns(frame: GPUFramePlan): Boolean {
        if(frame.w5hSourceAuthorityRootV1 !== this) return false
        val selected=frame.steps.filterIsInstance<GPUFrameStep.RenderPassStep>().flatMap { it.drawPackets }
            .filter { it.materialSourcePartitionV3() != null }
        val coreSelected = frame.steps.filterIsInstance<GPUFrameStep.RenderPassStep>().flatMap { it.drawPackets }
            .filter { it.corePrimitivePreparedAuthority?.materialDispatchPlan != null }
        if (coreSelected.any { packet ->
                val dispatch = packet.corePrimitivePreparedAuthority?.materialDispatchPlan
                val retained = coreDispatches[packet.packetId]
                coreDispatchPackets[packet.packetId] !== packet || dispatch !== retained?.first ||
                    dispatch?.geometry?.arena?.packedGeometryHash != retained?.second || packet.commonCoreSemanticAuthority() == null ||
                    dispatch?.validatesUniformPacket(packet) != true
            }) return false
        return selected.map { it.packetId.value }.distinct().size == selected.size &&
            frame.w5aGeometryHostTemplatesV1.map { it.packetId } == selected.map { it.packetId.value } && selected.all { packet ->
                packets[packet.packetId.value] === packet && packet.commonCoreSemanticAuthority() === coreBindings[packet.packetId.value] &&
                    frame.w5aGeometryHostTemplatesV1.singleOrNull {
                    it.packetId == packet.packetId.value } === templates[packet.packetId.value]
            }
    }
}
