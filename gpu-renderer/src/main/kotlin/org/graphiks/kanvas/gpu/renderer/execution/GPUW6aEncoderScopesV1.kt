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
        val pass = graph.passes()[index - 1]
        val referenced = if (render != null) listOf(render.target) + render.resourceUses.map { it.resource }
            else (step as GPUFrameStep.ReadbackCopyStep).let { listOf(it.source, it.staging) }
        val labels = referenced.map { "${it::class.simpleName}:${it.value}@${requireNotNull(generations[it])}" }
        val composite = pass is PlanPass.LayerComposite
        val kind = if (render == null) GPUEncoderOperationKind.Readback else if (composite) GPUEncoderOperationKind.LayerComposite else GPUEncoderOperationKind.Render
        val stream = if (kind != GPUEncoderOperationKind.Render) null else GPUPassCommandStream("w6a.stream.$index", "w6a.packets.$index", pass.id.value,
            buildList {
                add(GPUPassCommand.BeginRenderPass(corePrimitiveTargetStateHash(1, GPUColorFormat.RGBA8UnormSrgb),
                    "${render!!.loadStore.loadOp}:${render.loadStore.storePlan.name}:${render.loadStore.clearColorLabel ?: "none"}"))
                render.drawPackets.forEach { packet ->
                    add(GPUPassCommand.SetRenderPipeline(requireNotNull(packet.renderPipelineKey), packet.packetId))
                    add(GPUPassCommand.SetBindGroup(packet.bindingLayoutHash, packet.uniformSlot, null, packet.packetId))
                    packet.scissorBoundsHash?.let { add(GPUPassCommand.SetScissor(it, packet.packetId)) }
                    add(GPUPassCommand.Draw(packet.vertexSourceLabel, packet.packetId))
                }
                add(GPUPassCommand.EndRenderPass(pass.id.value))
            })
        val keys = if (render == null) listOf(
            key(GPUPreparedNativeOperandRole.ReadbackSource, GPUPreparedNativeOperandKind.Texture, "w6a.$index.source"),
            key(GPUPreparedNativeOperandRole.ReadbackDestination, GPUPreparedNativeOperandKind.Buffer, "w6a.$index.readback", GPUPreparedNativeOperandOwnership.OutputOwnedReadback))
        else buildList {
            add(key(GPUPreparedNativeOperandRole.RenderColorTarget, GPUPreparedNativeOperandKind.TextureView, "w6a.$index.target"))
            repeat(if (composite) 1 else render.drawPackets.size) { draw ->
                add(key(GPUPreparedNativeOperandRole.RenderPipeline, GPUPreparedNativeOperandKind.RenderPipeline, "w6a.$index.pipeline.$draw"))
                add(key(GPUPreparedNativeOperandRole.RenderBindGroup, GPUPreparedNativeOperandKind.BindGroup, "w6a.$index.bind.$draw"))
            }
        }
        GPUCommandEncoderScopePlan(index, kind, sourceTaskIds = step.sourceTaskIds, sourcePacketIds = render?.drawPackets.orEmpty().map { it.packetId },
            facadeOperationClasses = stream?.commandLabels ?: if (composite) listOf("beginRenderPass", "setRenderPipeline", "setBindGroup", "draw", "endRenderPass") else listOf("copyTextureToBuffer"),
            targetGeneration = targetGeneration, resourceGenerationLabels = labels, passCommandStream = stream).attachNativeOperandKeys(keys)
    }
}
