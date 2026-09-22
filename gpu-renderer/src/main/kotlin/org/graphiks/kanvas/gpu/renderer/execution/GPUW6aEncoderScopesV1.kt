package org.graphiks.kanvas.gpu.renderer.execution

import org.graphiks.kanvas.gpu.plan.PlanPass
import org.graphiks.kanvas.gpu.renderer.color.GPUColorFormat
import org.graphiks.kanvas.gpu.renderer.passes.*
import org.graphiks.kanvas.gpu.renderer.recording.*
import org.graphiks.kanvas.gpu.renderer.resources.*

internal fun GPUW6aLayerFramePlan.encoderScopes(frame: GPUFramePlan, generations: Map<GPUFrameResourceRef, Long>, targetGeneration: Long): List<GPUCommandEncoderScopePlan> {
    require(validates(frame))
    fun key(role: GPUPreparedNativeOperandRole, kind: GPUPreparedNativeOperandKind, name: String,
        ownership: GPUPreparedNativeOperandOwnership = GPUPreparedNativeOperandOwnership.Borrowed) =
        GPUPreparedNativeOperandKey(role, kind, gpuPreparedNativeBindingKey(name), ownership)
    return steps.mapIndexedNotNull { index, step ->
        if (step is GPUFrameStep.PrepareResourcesStep) return@mapIndexedNotNull null
        val render = step as? GPUFrameStep.RenderPassStep
        val copy = step as? GPUFrameStep.CopyResourceStep
        val pass = graph.passes()[index - 1]
        val geometryBinding = physical.geometryBinding(pass.id)
        val indexedGeometry = geometryBinding != null
        val w4e = physical.w4eGeometryBinding(pass.id)?.let { requireNotNull(render).drawPackets.single() }
        val referenced = if (render != null) listOf(render.target) + render.resourceUses.map { it.resource }
            else copy?.let { listOf(it.source, it.destination) }
                ?: (step as GPUFrameStep.ReadbackCopyStep).let { listOf(it.source, it.staging) }
        val labels = referenced.map { "${it::class.simpleName}:${it.value}@${requireNotNull(generations[it])}" }
        val composite = pass is PlanPass.LayerComposite
        val fullscreen = pass is PlanPass.PictureSourcePass || pass is PlanPass.PictureComposite ||
            pass is PlanPass.FilterPass || pass is PlanPass.FilterComposite ||
            pass is PlanPass.PictureAggregateBeginPass || pass is PlanPass.PictureAggregateSealPass ||
            pass is PlanPass.FilterSourceClear || pass is PlanPass.FilterCoverageSourcePass
        val kind = if (copy != null) GPUEncoderOperationKind.Copy else if (render == null) GPUEncoderOperationKind.Readback
            else if (composite) GPUEncoderOperationKind.LayerComposite else GPUEncoderOperationKind.Render
        val stream = if (kind != GPUEncoderOperationKind.Render) null else if (w4e != null)
            GPUPassCommandStream("w6a.stream.$index", "w6a.packets.$index", pass.id.value, listOf(
                GPUPassCommand.BeginRenderPass(w4e.targetStateHash, "${render!!.loadStore.loadOp}:${render.loadStore.storePlan.name}:none"),
                GPUPassCommand.Draw(w4e.vertexSourceLabel, w4e.packetId), GPUPassCommand.EndRenderPass(pass.id.value)),
                sourcePassIds = listOf(pass.id.value))
            else GPUPassCommandStream("w6a.stream.$index", "w6a.packets.$index", pass.id.value,
            buildList {
                add(GPUPassCommand.BeginRenderPass(corePrimitiveTargetStateHash(1, GPUColorFormat.RGBA8UnormSrgb),
                    "${render!!.loadStore.loadOp}:${render.loadStore.storePlan.name}:${render.loadStore.clearColorLabel ?: "none"}"))
                render.drawPackets.forEach { packet ->
                    add(GPUPassCommand.SetRenderPipeline(requireNotNull(packet.renderPipelineKey), packet.packetId))
                    add(GPUPassCommand.SetBindGroup(packet.bindingLayoutHash, packet.uniformSlot, null, packet.packetId))
                    if (indexedGeometry) {
                        add(GPUPassCommand.SetVertexBuffer(0, packet.packetId))
                        if (geometryBinding.indexCountI32 > 0) add(GPUPassCommand.SetIndexBuffer(
                            if (geometryBinding.indexElementBytesI32 == 2) "uint16" else "uint32", packet.packetId))
                    }
                    packet.scissorBoundsHash?.let { add(GPUPassCommand.SetScissor(it, packet.packetId)) }
                    add(GPUPassCommand.Draw(packet.vertexSourceLabel, packet.packetId))
                }
                add(GPUPassCommand.EndRenderPass(pass.id.value))
            })
        val keys = if (w4e != null) w4eNativeOperandKeysV6(w4e, commonSource = true) else if (copy != null) listOf(
            key(GPUPreparedNativeOperandRole.CopySource, GPUPreparedNativeOperandKind.Texture, "w6a.$index.copy.source"),
            key(GPUPreparedNativeOperandRole.CopyDestination, GPUPreparedNativeOperandKind.Texture, "w6a.$index.copy.destination"),
        ) else if (render == null) listOf(
            key(GPUPreparedNativeOperandRole.ReadbackSource, GPUPreparedNativeOperandKind.Texture, "w6a.$index.source"),
            key(GPUPreparedNativeOperandRole.ReadbackDestination, GPUPreparedNativeOperandKind.Buffer, "w6a.$index.readback", GPUPreparedNativeOperandOwnership.OutputOwnedReadback))
        else buildList {
            add(key(GPUPreparedNativeOperandRole.RenderColorTarget, GPUPreparedNativeOperandKind.TextureView, "w6a.$index.target"))
            if (pass is PlanPass.StencilGeometryProducerV3 || pass is PlanPass.StencilCover)
                add(key(GPUPreparedNativeOperandRole.RenderDepthStencilTarget, GPUPreparedNativeOperandKind.TextureView, "w6a.$index.depth-stencil"))
            repeat(if (composite || fullscreen) 1 else render.drawPackets.size) { draw ->
                add(key(GPUPreparedNativeOperandRole.RenderPipeline, GPUPreparedNativeOperandKind.RenderPipeline, "w6a.$index.pipeline.$draw"))
                add(key(GPUPreparedNativeOperandRole.RenderBindGroup, GPUPreparedNativeOperandKind.BindGroup, "w6a.$index.bind.$draw"))
                if (indexedGeometry) {
                    add(key(GPUPreparedNativeOperandRole.RenderVertexBuffer, GPUPreparedNativeOperandKind.Buffer, "w6a.$index.vertex.$draw"))
                    if (geometryBinding.indexCountI32 > 0)
                        add(key(GPUPreparedNativeOperandRole.RenderIndexBuffer, GPUPreparedNativeOperandKind.Buffer, "w6a.$index.index.$draw"))
                }
            }
        }
        GPUCommandEncoderScopePlan(index, kind, sourceTaskIds = step.sourceTaskIds, sourcePacketIds = render?.drawPackets.orEmpty().map { it.packetId },
            facadeOperationClasses = stream?.commandLabels ?: if (composite || fullscreen) listOf("beginRenderPass", "setRenderPipeline", "setBindGroup", "draw", "endRenderPass")
                else if (copy != null) List(copy.regions.size) { "copyResource" } else listOf("copyTextureToBuffer"),
            targetGeneration = targetGeneration, resourceGenerationLabels = labels, passCommandStream = stream).attachNativeOperandKeys(keys, w6aFrameV1 = this)
    }
}
