package org.graphiks.kanvas.gpu.renderer.planning

import org.graphiks.kanvas.gpu.plan.*
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.passes.*
import org.graphiks.kanvas.gpu.renderer.recording.*
import org.graphiks.kanvas.gpu.renderer.resources.*
import org.graphiks.kanvas.gpu.renderer.state.*

/** The exact W4e source graph supplies all native math, slices and mask consumers. */
internal fun lowerW5bW4eLaneV3(request: GpuPlanLoweringRequest, lane: W5bGeometryLanePlanV3,
    target: GPUFrameTargetRef, staging: GPUFrameBufferRef, capabilitySeal: GPUFrameCapabilitySeal): W5bGeometryScratchV3.W4e {
    val graph = request.graph
    val source = lane.sourceGraph
    require(graph.w5bGeometryLanes().any { it === lane } == true)
    require(source.capabilityId == W4eClipPlanCompiler.W5A_HARD_CAPABILITY_ID && source.verifyW4eCompilerWitness())
    val authority = GPUPlanW4ePreparedAuthority.issueAfterFullGraphValidation(source)
    require(authority.revalidates(source))
    val builder = W4eClipGraphLowerer()
    val bounds = GPUPixelBounds(0, 0, graph.targetExtent.width, graph.targetExtent.height)
    val session = "w5b.w4e.${request.deviceGeneration.value}.${graph.id.value}"
    val refs = source.resources().associate { resource -> resource.id.value to when (resource.role) {
        PlanResourceRole.LogicalTarget -> target
        PlanResourceRole.ReadbackStaging -> staging
        else -> builder.ref(session, resource)
    } }
    require(source.resources().all { original -> graph.resources().singleOrNull { it.id == original.id }?.let {
        it.role == original.role && it.kind == original.kind && it.format == original.format &&
            it.byteSize == original.byteSize && it.copyExtent() == original.copyExtent() &&
            it.sampleCountI32 == original.sampleCountI32 && it.usages() == original.usages()
    } == true })
    val originalById = source.resources().associateBy { it.id.value }
    val sourcePaths = source.passes().filterIsInstance<PlanPass.PathRenderPass>()
    val renders = linkedMapOf<PlanPassId, GPUTask.Render>()
    graph.passes().forEachIndexed { indexI32, pass ->
        val draw = when (pass) {
            is PlanPass.RenderPass -> pass.draws().singleOrNull() as? W5bW4ePathDraw
            is PlanPass.StencilCover -> pass.draw as W5bW4ePathDraw
            else -> null
        }
        val sourcePass = when (pass) {
            is PlanPass.StencilGeometryProducerV3 -> sourcePaths.single { it.draw.commandIndex == pass.commandIndexI32 &&
                it.phase == PathRenderPhase.SingleSampleStencilProducer }
            else -> draw?.nativeColorPass
        }
        val prefix = pass is PlanPass.ClipMaskInitialize || pass is PlanPass.ClipMaskProducer || pass is PlanPass.ClipMaskFold
        if (sourcePass == null && !prefix) return@forEachIndexed
        val consumer = sourcePass?.takeUnless { it.phase == PathRenderPhase.SingleSampleStencilProducer }
            ?.let { authority.consumerFor(it.id.value) }
        val prepared = sourcePass?.let { requireNotNull(authority.pathFor(it.id.value)) }
        val packet = if (prepared == null) builder.preparedClipPacket(pass, indexI32,
            requireNotNull(authority.clipPassFor(pass.id.value))) else builder.pathPacket(prepared, consumer, indexI32, draw?.blend)
        if (draw != null) {
            val material = draw.materialAuthority as PlanDrawMaterialAuthority.MaterialV1
            packet.attachW5aSourceStageV2(org.graphiks.kanvas.gpu.renderer.materials.W5aPacketMaterialSourceV2.issue(
                requireNotNull(graph.materialPlanTableOrNull()), material.ref, packet.commandIdValue))
        }
        val uses = builder.resourceUses(sourcePass ?: pass, refs, originalById, consumer, prepared).map { use ->
            if (pass is PlanPass.StencilGeometryProducerV3 && use.resource == target &&
                use.role == GPUFrameResourceRole.SceneTarget && use.usage == GPUFrameResourceUsage.RenderAttachment)
                use.copy(write = false) else use
        }.toMutableList()
        if (draw?.blend is BlendPlan.DestinationReadV1) uses += GPUFrameResourceUse(
            GPUFrameTextureRef(target.value.removeSuffix(".target") + ".snapshot"), GPUFrameResourceRole.DestinationSnapshot,
            GPUFrameResourceUsage.TextureBinding, GPUFrameResourceLifetime.FrameLocal, false)
        val renderTarget = if (sourcePass != null) target else refs.getValue(when (pass) {
            is PlanPass.ClipMaskInitialize -> pass.output.value
            is PlanPass.ClipMaskProducer -> pass.target.value
            is PlanPass.ClipMaskFold -> pass.output.value
            else -> error("Not a W4e producer")
        }) as GPUFrameTargetRef
        val load = when (pass) {
            is PlanPass.RenderPass -> pass.load
            is PlanPass.StencilGeometryProducerV3 -> pass.load
            is PlanPass.StencilCover -> pass.load
            else -> AttachmentLoadPlan.ClearTransparent
        }
        renders[pass.id] = GPUTask.Render(GPUTaskID("task.w5b.${graph.id.value}.${pass.id.value}"), request.recordingId,
            GPUTaskPhase.Render, renderTarget, GPULoadStorePlan(if (load == AttachmentLoadPlan.ClearTransparent) "clear" else "load", GPUStorePlan.Store),
            GPUSamplePlan.SingleSampleFrame, resourceUses = uses, drawPackets = listOf(packet),
            batchEligibilityByPacketId = mapOf(packet.packetId to GPUPassBatchEligibility(kind = GPUPassBatchKind.SolidFill,
                queueGuard = GPUPassBatchQueueGuard(emptyList(), emptyList()))),
            depthStencilLoadStore = sourcePass?.let(builder::depthStencilLoadStore))
    }
    val frame = authority.issueFrameAuthority(request.frameId.value, capabilitySeal.sealHash, renders.values.toList())
    renders.values.forEach { it.drawPackets.single().attachW4ePreparedFrameAuthority(frame) }
    require(frame.validatesRenders(request.frameId.value, capabilitySeal.sealHash, renders.values.toList()))
    val preparations = source.resources().filter { it.role !in setOf(PlanResourceRole.LogicalTarget, PlanResourceRole.ReadbackStaging) }
        .map { builder.preparation(it, refs.getValue(it.id.value), bounds, graph.capabilities.copyBytesPerRowAlignment.toLong()) }
    return W5bGeometryScratchV3.W4e(graph.id.value, capabilitySeal.sealHash, request.deviceGeneration.value,
        target, staging, bounds, source, authority, frame, renders, preparations)
}
