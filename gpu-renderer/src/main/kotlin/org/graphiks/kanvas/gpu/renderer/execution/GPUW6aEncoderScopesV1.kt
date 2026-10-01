package org.graphiks.kanvas.gpu.renderer.execution

import org.graphiks.kanvas.gpu.plan.FilterImplementationKindV1
import org.graphiks.kanvas.gpu.plan.PlanPass
import org.graphiks.kanvas.gpu.plan.aaCoverageBindingOrNullV1
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
        val aa = physical.w4dAaSourceBindings().singleOrNull { pass in it.passes() }
        val aaCoverage = pass.aaCoverageBindingOrNullV1()
        val inverseAaCoverage = (pass as? PlanPass.AaCoverageSourcePass)?.binding as?
            org.graphiks.kanvas.gpu.plan.PlanW4eInverseAaCoverageSourceBindingV1
        val inverseAaOperands = inverseAaCoverage?.let { binding ->
            require(binding.recipe.binding === binding && binding.validatesNativeOperationFacts())
            binding.nativeOperandSequenceV1()
        }
        val indexedAa = aa != null || aaCoverage != null || inverseAaCoverage != null
        val indexedGeometry = geometryBinding != null || indexedAa
        // A FilterCoverage source with a frozen stencil producer replays the producer and
        // cover in this one W6b scope.  It cannot use the single-packet W4e stream shell.
        val aaCoverageStencil = aaCoverage?.passes()?.firstOrNull()?.depthStencil != null
        val stencilCoverage = aaCoverageStencil ||
            (pass as? PlanPass.FilterCoverageSourcePass)?.rasterBinding?.depthStencil != null
        val w4e = physical.w4eGeometryBinding(pass.id)
            ?.takeUnless { stencilCoverage }
            ?.let { requireNotNull(render).drawPackets.single() }
        val referenced = if (render != null) listOf(render.target) + render.resourceUses.map { it.resource }
            else copy?.let { listOf(it.source, it.destination) }
                ?: (step as GPUFrameStep.ReadbackCopyStep).let { listOf(it.source, it.staging) }
        val labels = referenced.map { "${it::class.simpleName}:${it.value}@${requireNotNull(generations[it])}" }
        val composite = pass is PlanPass.LayerComposite || pass is PlanPass.PathAaColorComposite || pass is PlanPass.AaDeferredComposite
        val fullscreen = pass is PlanPass.PictureSourcePass || pass is PlanPass.PictureComposite ||
            pass is PlanPass.FilterPass || pass is PlanPass.FilterComposite ||
            pass is PlanPass.PictureAggregateBeginPass || pass is PlanPass.PictureAggregateSealPass ||
            pass is PlanPass.FilterSourceClear || pass is PlanPass.FilterCoverageSourcePass ||
            pass is PlanPass.FilterCoverageRetainPass || pass is PlanPass.AaDeferredComposite
        val frozenShadow = (pass as? PlanPass.FilterPass)?.operation?.kind in setOf(
            FilterImplementationKindV1.DROP_SHADOW_COLORIZE,
            FilterImplementationKindV1.DROP_SHADOW_COMPOSITE,
        )
        require(!frozenShadow || render != null && render.drawPackets.isEmpty()) {
            "W6b shadow lowering accepts only its frozen fullscreen pass."
        }
        val kind = if (copy != null) GPUEncoderOperationKind.Copy else if (render == null) GPUEncoderOperationKind.Readback
            else if (composite) GPUEncoderOperationKind.LayerComposite else GPUEncoderOperationKind.Render
        val stream = if (kind != GPUEncoderOperationKind.Render) null else if (inverseAaCoverage != null)
            GPUPassCommandStream("w6a.stream.$index", "w6a.packets.$index", pass.id.value, buildList {
                add(GPUPassCommand.BeginRenderPass("w7.inverse-aa.coverage", "${render!!.loadStore.loadOp}:${render.loadStore.storePlan.name}:none"))
                render.drawPackets.forEach { packet -> add(GPUPassCommand.Draw(packet.vertexSourceLabel, packet.packetId)) }
                add(GPUPassCommand.EndRenderPass(pass.id.value))
            }, sourcePassIds = listOf(pass.id.value))
            else if (w4e != null)
            GPUPassCommandStream("w6a.stream.$index", "w6a.packets.$index", pass.id.value, listOf(
                GPUPassCommand.BeginRenderPass(w4e.targetStateHash, "${render!!.loadStore.loadOp}:${render.loadStore.storePlan.name}:none"),
                GPUPassCommand.Draw(w4e.vertexSourceLabel, w4e.packetId), GPUPassCommand.EndRenderPass(pass.id.value)),
                sourcePassIds = listOf(pass.id.value))
            else GPUPassCommandStream("w6a.stream.$index", "w6a.packets.$index", pass.id.value,
            buildList {
                add(GPUPassCommand.BeginRenderPass(corePrimitiveTargetStateHash(render!!.samplePlan.sampleCount, GPUColorFormat.RGBA8UnormSrgb),
                    "${render.loadStore.loadOp}:${render.loadStore.storePlan.name}:${render.loadStore.clearColorLabel ?: "none"}"))
                render.drawPackets.forEach { packet ->
                    add(GPUPassCommand.SetRenderPipeline(requireNotNull(packet.renderPipelineKey), packet.packetId))
                    add(GPUPassCommand.SetBindGroup(packet.bindingLayoutHash, packet.uniformSlot, null, packet.packetId))
                    if (indexedGeometry) {
                        add(GPUPassCommand.SetVertexBuffer(0, packet.packetId))
                        if (indexedAa || requireNotNull(geometryBinding).indexCountI32 > 0) add(GPUPassCommand.SetIndexBuffer(
                            if (geometryBinding?.indexElementBytesI32 == 2) "uint16" else "uint32", packet.packetId))
                    }
                    packet.scissorBoundsHash?.let { add(GPUPassCommand.SetScissor(it, packet.packetId)) }
                    add(GPUPassCommand.Draw(packet.vertexSourceLabel, packet.packetId))
                }
                add(GPUPassCommand.EndRenderPass(pass.id.value))
            })
        // A frozen stencil-cover FilterCoverage source replays its existing W4 producer and
        // cover packets into the coverage target.  That is one sealed W6b pass with two
        // command groups, so its operand contract must describe both groups rather than the
        // ordinary single fullscreen FilterCoverage shell.
        val keys = if (inverseAaOperands != null) inverseAaOperands.map { operand -> when (operand) {
            org.graphiks.kanvas.gpu.plan.PlanW4eInverseAaCoverageSourceBindingV1.NativeOperandV1.MsaaColorTarget ->
                key(GPUPreparedNativeOperandRole.RenderMsaaColorTarget, GPUPreparedNativeOperandKind.TextureView, "w6a.$index.w7.inverse-aa.target")
            org.graphiks.kanvas.gpu.plan.PlanW4eInverseAaCoverageSourceBindingV1.NativeOperandV1.ResolveTarget ->
                key(GPUPreparedNativeOperandRole.RenderResolveTarget, GPUPreparedNativeOperandKind.TextureView, "w6a.$index.w7.inverse-aa.resolve")
            org.graphiks.kanvas.gpu.plan.PlanW4eInverseAaCoverageSourceBindingV1.NativeOperandV1.DepthStencilTarget ->
                key(GPUPreparedNativeOperandRole.RenderDepthStencilTarget, GPUPreparedNativeOperandKind.TextureView, "w6a.$index.w7.inverse-aa.depth-stencil")
            org.graphiks.kanvas.gpu.plan.PlanW4eInverseAaCoverageSourceBindingV1.NativeOperandV1.ProducerPipeline ->
                key(GPUPreparedNativeOperandRole.RenderPipeline, GPUPreparedNativeOperandKind.RenderPipeline, "w6a.$index.w7.inverse-aa.producer.pipeline")
            org.graphiks.kanvas.gpu.plan.PlanW4eInverseAaCoverageSourceBindingV1.NativeOperandV1.ProducerVertex ->
                key(GPUPreparedNativeOperandRole.RenderVertexBuffer, GPUPreparedNativeOperandKind.Buffer, "w6a.$index.w7.inverse-aa.producer.vertex")
            org.graphiks.kanvas.gpu.plan.PlanW4eInverseAaCoverageSourceBindingV1.NativeOperandV1.ProducerIndex ->
                key(GPUPreparedNativeOperandRole.RenderIndexBuffer, GPUPreparedNativeOperandKind.Buffer, "w6a.$index.w7.inverse-aa.producer.index")
            org.graphiks.kanvas.gpu.plan.PlanW4eInverseAaCoverageSourceBindingV1.NativeOperandV1.CoverPipeline ->
                key(GPUPreparedNativeOperandRole.RenderPipeline, GPUPreparedNativeOperandKind.RenderPipeline, "w6a.$index.w7.inverse-aa.cover.pipeline")
            org.graphiks.kanvas.gpu.plan.PlanW4eInverseAaCoverageSourceBindingV1.NativeOperandV1.CoverBindGroup ->
                key(GPUPreparedNativeOperandRole.RenderBindGroup, GPUPreparedNativeOperandKind.BindGroup, "w6a.$index.w7.inverse-aa.cover.bind")
        } } else if (stencilCoverage) buildList {
            add(key(GPUPreparedNativeOperandRole.RenderColorTarget, GPUPreparedNativeOperandKind.TextureView,
                "w6a.$index.coverage.target"))
            if (aaCoverageStencil) add(key(GPUPreparedNativeOperandRole.RenderResolveTarget,
                GPUPreparedNativeOperandKind.TextureView, "w6a.$index.coverage.resolve"))
            add(key(GPUPreparedNativeOperandRole.RenderDepthStencilTarget, GPUPreparedNativeOperandKind.TextureView,
                "w6a.$index.coverage.depth-stencil"))
            repeat(2) { packet ->
                add(key(GPUPreparedNativeOperandRole.RenderPipeline, GPUPreparedNativeOperandKind.RenderPipeline,
                    "w6a.$index.coverage.pipeline.$packet"))
                add(key(GPUPreparedNativeOperandRole.RenderBindGroup, GPUPreparedNativeOperandKind.BindGroup,
                    "w6a.$index.coverage.bind.$packet"))
                add(key(GPUPreparedNativeOperandRole.RenderVertexBuffer, GPUPreparedNativeOperandKind.Buffer,
                    "w6a.$index.coverage.vertex.$packet"))
                add(key(GPUPreparedNativeOperandRole.RenderIndexBuffer, GPUPreparedNativeOperandKind.Buffer,
                    "w6a.$index.coverage.index.$packet"))
            }
        } else if (w4e != null) w4eNativeOperandKeysV6(w4e, commonSource = true) else if (copy != null) listOf(
            key(GPUPreparedNativeOperandRole.CopySource, GPUPreparedNativeOperandKind.Texture, "w6a.$index.copy.source"),
            key(GPUPreparedNativeOperandRole.CopyDestination, GPUPreparedNativeOperandKind.Texture, "w6a.$index.copy.destination"),
        ) else if (render == null) listOf(
            key(GPUPreparedNativeOperandRole.ReadbackSource, GPUPreparedNativeOperandKind.Texture, "w6a.$index.source"),
            key(GPUPreparedNativeOperandRole.ReadbackDestination, GPUPreparedNativeOperandKind.Buffer, "w6a.$index.readback", GPUPreparedNativeOperandOwnership.OutputOwnedReadback))
        else buildList {
            add(key(GPUPreparedNativeOperandRole.RenderColorTarget, GPUPreparedNativeOperandKind.TextureView, "w6a.$index.target"))
            ((pass as? PlanPass.PathRenderPass)?.resolveTarget ?: aaCoverage?.passes()?.last()?.resolveTarget)?.let {
                add(key(GPUPreparedNativeOperandRole.RenderResolveTarget,
                    GPUPreparedNativeOperandKind.TextureView, "w6a.$index.resolve"))
            }
            (pass as? PlanPass.PathRenderPass)?.depthStencil?.let {
                add(key(GPUPreparedNativeOperandRole.RenderDepthStencilTarget,
                    GPUPreparedNativeOperandKind.TextureView, "w6a.$index.depth-stencil"))
            }
            if (pass is PlanPass.StencilGeometryProducerV3 || pass is PlanPass.StencilCover) {
                add(key(GPUPreparedNativeOperandRole.RenderDepthStencilTarget,
                    GPUPreparedNativeOperandKind.TextureView, "w6a.$index.depth-stencil"))
            }
            repeat(if (composite || fullscreen) 1 else render.drawPackets.size) { draw ->
                val deferredIdentity = (pass as? PlanPass.AaDeferredComposite)?.let { aaDeferredRecipe(it).canonicalLogicalEncodingV1 }.orEmpty()
                add(key(GPUPreparedNativeOperandRole.RenderPipeline, GPUPreparedNativeOperandKind.RenderPipeline, "w6a.$index.pipeline.$draw$deferredIdentity"))
                add(key(GPUPreparedNativeOperandRole.RenderBindGroup, GPUPreparedNativeOperandKind.BindGroup, "w6a.$index.bind.$draw"))
                if (indexedGeometry) {
                    add(key(GPUPreparedNativeOperandRole.RenderVertexBuffer, GPUPreparedNativeOperandKind.Buffer, "w6a.$index.vertex.$draw"))
                    if (indexedAa || requireNotNull(geometryBinding).indexCountI32 > 0)
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
