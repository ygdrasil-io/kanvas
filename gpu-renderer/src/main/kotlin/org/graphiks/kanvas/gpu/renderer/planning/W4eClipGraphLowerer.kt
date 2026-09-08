package org.graphiks.kanvas.gpu.renderer.planning

import org.graphiks.kanvas.gpu.plan.AttachmentLoadPlan
import org.graphiks.kanvas.gpu.plan.PlanPass
import org.graphiks.kanvas.gpu.plan.PlanPassDependency
import org.graphiks.kanvas.gpu.plan.PlanResource
import org.graphiks.kanvas.gpu.plan.PlanResourceKind
import org.graphiks.kanvas.gpu.plan.PlanResourceLifetime
import org.graphiks.kanvas.gpu.plan.PlanResourceRole
import org.graphiks.kanvas.gpu.plan.PlanResourceUsage
import org.graphiks.kanvas.gpu.plan.PlanTextureFormat
import org.graphiks.kanvas.gpu.plan.SamplePlan
import org.graphiks.kanvas.gpu.plan.W4eClipPlanCompiler
import org.graphiks.kanvas.gpu.renderer.color.GPUColorFormat
import org.graphiks.kanvas.gpu.renderer.color.GPUColorInterpretation
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketID
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketRole
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchEligibility
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchKind
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchQueueGuard
import org.graphiks.kanvas.gpu.renderer.passes.GPUPlanW4ePreparedAuthority
import org.graphiks.kanvas.gpu.renderer.passes.GPUSamplePlan
import org.graphiks.kanvas.gpu.renderer.passes.GPUW4ePreparedClipConsumerAuthority
import org.graphiks.kanvas.gpu.renderer.passes.GPUW4ePreparedClipPassAuthority
import org.graphiks.kanvas.gpu.renderer.pipelines.GPURenderPipelineKey
import org.graphiks.kanvas.gpu.renderer.payloads.CORE_PRIMITIVE_RENDER_STEP_IDENTITY
import org.graphiks.kanvas.gpu.renderer.recording.GPUCorePrimitivePreparedFrameResult
import org.graphiks.kanvas.gpu.renderer.recording.GPUCorePrimitiveW4ePreparedFrameTaskListAssembler
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameCapabilitySeal
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameReadbackRequest
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackPixelFormat
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackRequestID
import org.graphiks.kanvas.gpu.renderer.recording.GPURecordingSeal
import org.graphiks.kanvas.gpu.renderer.recording.GPUTask
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskID
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskList
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskPhase
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryBudgetPlan
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryCategory
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceLifetime
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUse
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTextureDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourcePreparationRequest
import org.graphiks.kanvas.render.ir.RenderDiagnostic
import org.graphiks.kanvas.render.ir.RenderDiagnosticCode
import org.graphiks.kanvas.render.ir.RenderDiagnosticDomain
import org.graphiks.kanvas.render.ir.RenderDiagnosticSeverity

/** Mechanical lowering for a complete W4e graph; clip classification is closed before entry. */
internal class W4eClipGraphLowerer {
    fun lower(request: GpuPlanLoweringRequest): GpuPlanLoweringResult = try {
        val graph = request.graph
        if (!graph.verifyW4eCompilerWitness() || graph.capabilityId !in setOf(W4eClipPlanCompiler.HARD_CAPABILITY_ID, W4eClipPlanCompiler.AA_CAPABILITY_ID)) return invalid()
        val passes = graph.passes()
        if (passes.lastOrNull() !is PlanPass.ReadbackPass || graph.dependencies() != passes.zipWithNext().map { (a, b) -> PlanPassDependency(a.id, b.id) }) return invalid()
        val authority = GPUPlanW4ePreparedAuthority.issueAfterFullGraphValidation(graph)
        if (!authority.revalidates(graph)) return invalid()
        val bounds = GPUPixelBounds(0, 0, graph.targetExtent.width, graph.targetExtent.height)
        val session = "w4e.session.${request.deviceGeneration.value}.${bounds.width}x${bounds.height}"
        val refs = graph.resources().associate { resource -> resource.id.value to ref(session, resource) }
        val logical = graph.resources().singleOrNull { it.role == PlanResourceRole.LogicalTarget } ?: return invalid()
        val staging = graph.resources().singleOrNull { it.role == PlanResourceRole.ReadbackStaging } ?: return invalid()
        val target = refs.getValue(logical.id.value) as? GPUFrameTargetRef ?: return invalid()
        val stagingRef = refs.getValue(staging.id.value) as? GPUFrameBufferRef ?: return invalid()
        val seal = GPUFrameCapabilitySeal.capture(request.frameId, request.deviceGeneration, request.capabilities)
        val replay = "w4e:${graph.id.value}"
        if (passes.none { it is PlanPass.PathRenderPass }) return invalid()
        val packetFactory = W4dGeneralPathGraphLowerer()
        val renders = passes.dropLast(1).mapIndexed { index, pass ->
            val path = pass as? PlanPass.PathRenderPass
            val consumer = path?.let { authority.consumerFor(it.id.value) }
            val packet = path?.let {
                packetFactory.packetForSealedW4e(it, index, bounds, graph, consumer)
            } ?: markerPacket(pass, index, authority.clipPassFor(pass.id.value) ?: return invalid())
            val targetId = when (pass) {
                is PlanPass.ClipMaskInitialize -> pass.output
                is PlanPass.ClipMaskProducer -> pass.target
                is PlanPass.ClipMaskFold -> pass.output
                is PlanPass.PathRenderPass -> pass.target
                else -> return invalid()
            }
            val samples = when (pass) {
                is PlanPass.ClipMaskProducer -> pass.sampleCountI32
                is PlanPass.PathRenderPass -> if (pass.draw.sample == SamplePlan.Multisample4) 4 else 1
                else -> 1
            }
            GPUTask.Render(
                GPUTaskID("task.w4e.${graph.id.value}.${pass.id.value}"), request.recordingId, GPUTaskPhase.Render,
                refs.getValue(targetId.value) as? GPUFrameTargetRef ?: return invalid(),
                org.graphiks.kanvas.gpu.renderer.state.GPULoadStorePlan(loadLabel(pass), org.graphiks.kanvas.gpu.renderer.state.GPUStorePlan.Store),
                if (samples == 4) GPUSamplePlan.MultisampleFrame(4) else GPUSamplePlan.SingleSampleFrame,
                resourceUses = resourceUses(pass, refs, consumer),
                drawPackets = listOf(packet),
                batchEligibilityByPacketId = mapOf(packet.packetId to GPUPassBatchEligibility(
                    kind = GPUPassBatchKind.SolidFill,
                    queueGuard = GPUPassBatchQueueGuard(emptyList(), emptyList()),
                )),
            )
        }
        val preparations = graph.resources().map { resource -> preparation(resource, refs.getValue(resource.id.value), bounds, graph.capabilities.copyBytesPerRowAlignment.toLong()) }
        val readback = GPUFrameReadbackRequest(GPUReadbackRequestID("w4e.${graph.id.value}.readback"), bounds, GPUReadbackPixelFormat.Rgba8Unorm, GPUColorInterpretation.EncodedPremulSrgb)
        val base = GPUTaskList(request.frameId, seal, listOf(GPURecordingSeal(request.recordingId, 0L, replay, replay, seal.sealHash)), replay, emptyList(), emptyList(), GPUTaskPhase.entries, memory(graph, logical.byteSize))
        when (val assembled = GPUCorePrimitiveW4ePreparedFrameTaskListAssembler().build(base, preparations, renders, target, stagingRef, readback, memory(graph, logical.byteSize))) {
            is GPUCorePrimitivePreparedFrameResult.Recorded -> GpuPlanLoweringResult.Lowered(assembled.taskList, readback.requestId.value)
            is GPUCorePrimitivePreparedFrameResult.Refused -> invalid()
        }
    } catch (_: IllegalArgumentException) { invalid() }

    private fun ref(session: String, resource: PlanResource): GPUFrameResourceRef = when (resource.kind) {
        PlanResourceKind.Buffer -> GPUFrameBufferRef("$session.${resource.id.value}")
        PlanResourceKind.Texture2D -> GPUFrameTargetRef("$session.${resource.id.value}")
    }

    /** Marker packets are explicit Task 7 handoff contracts, never simulated clip execution. */
    private fun markerPacket(
        pass: PlanPass,
        index: Int,
        preparedPass: GPUW4ePreparedClipPassAuthority,
    ): GPUDrawPacket = GPUDrawPacket(
        packetId = GPUDrawPacketID("packet.w4e.${pass.id.value}"),
        commandIdValue = index,
        analysisRecordId = "w4e.sealed.${pass.id.value}",
        passId = pass.id.value,
        layerId = "root",
        bindingListId = "binding.w4e.${pass.id.value}",
        insertionReasonCode = "w4e-sealed-pass",
        sortKey = index.toLong(),
        sortKeyPreimage = "w4e-pass:$index",
        renderStepId = org.graphiks.kanvas.gpu.renderer.passes.GPURenderStepID(CORE_PRIMITIVE_RENDER_STEP_IDENTITY),
        renderStepVersion = 1,
        role = GPUDrawPacketRole.Shading,
        blendPlan = org.graphiks.kanvas.gpu.renderer.recording.canonicalSolidRectSrcOverBlendPlan(),
        renderPipelineKey = GPURenderPipelineKey("core-primitive.w4e.sealed-pass-v1"),
        bindingLayoutHash = org.graphiks.kanvas.gpu.renderer.recording.CORE_PRIMITIVE_BINDING_LAYOUT_HASH,
        vertexSourceLabel = org.graphiks.kanvas.gpu.renderer.recording.CORE_PRIMITIVE_VERTEX_SOURCE_LABEL,
        targetStateHash = "w4e.sealed-target",
        originalPaintOrder = index,
        resourceGeneration = org.graphiks.kanvas.gpu.renderer.recording.PREPARED_FRAME_LATE_BOUND_RESOURCE_GENERATION,
        w4ePreparedClipPass = preparedPass,
    )

    private fun loadLabel(pass: PlanPass): String = when (pass) {
        is PlanPass.ClipMaskInitialize -> "clear"
        is PlanPass.ClipMaskProducer -> "clear"
        is PlanPass.ClipMaskFold -> "clear"
        is PlanPass.PathRenderPass -> if (pass.load == AttachmentLoadPlan.ClearTransparent) "clear" else "load"
        else -> "load"
    }

    private fun resourceUses(
        pass: PlanPass,
        refs: Map<String, GPUFrameResourceRef>,
        consumer: GPUW4ePreparedClipConsumerAuthority?,
    ): List<GPUFrameResourceUse> {
        fun use(id: String, role: GPUFrameResourceRole, usage: GPUFrameResourceUsage, write: Boolean) =
            GPUFrameResourceUse(refs.getValue(id), role, usage, GPUFrameResourceLifetime.FrameLocal, write)
        return when (pass) {
            is PlanPass.ClipMaskInitialize -> listOf(use(pass.output.value, GPUFrameResourceRole.ClipMask, GPUFrameResourceUsage.RenderAttachment, true))
            is PlanPass.ClipMaskProducer -> buildList {
                add(use(pass.target.value, GPUFrameResourceRole.ClipMask, GPUFrameResourceUsage.RenderAttachment, true))
                pass.resolveTarget?.let { add(use(it.value, GPUFrameResourceRole.ClipMask, GPUFrameResourceUsage.RenderAttachment, true)) }
                pass.depthStencil?.let { add(use(it.value, GPUFrameResourceRole.ClipDepthStencil, GPUFrameResourceUsage.RenderAttachment, true)) }
            }
            is PlanPass.ClipMaskFold -> listOf(
                use(pass.previous.value, GPUFrameResourceRole.ClipMask, GPUFrameResourceUsage.TextureBinding, false),
                use(pass.source.value, GPUFrameResourceRole.ClipMask, GPUFrameResourceUsage.TextureBinding, false),
                use(pass.output.value, GPUFrameResourceRole.ClipMask, GPUFrameResourceUsage.RenderAttachment, true),
            )
            is PlanPass.PathRenderPass -> when (consumer) {
                is GPUW4ePreparedClipConsumerAuthority.Mask -> listOf(
                    use(consumer.maskResourceId, GPUFrameResourceRole.ClipMask, GPUFrameResourceUsage.TextureBinding, false),
                )
                is GPUW4ePreparedClipConsumerAuthority.InverseMask -> listOf(
                    use(consumer.maskResourceId, GPUFrameResourceRole.ClipMask, GPUFrameResourceUsage.TextureBinding, false),
                )
                is GPUW4ePreparedClipConsumerAuthority.InverseDomain,
                null,
                -> emptyList()
            }
            else -> emptyList()
        }
    }

    private fun preparation(resource: PlanResource, ref: GPUFrameResourceRef, bounds: GPUPixelBounds, alignment: Long): GPUResourcePreparationRequest {
        val role = when (resource.role) {
            PlanResourceRole.LogicalTarget -> GPUFrameResourceRole.SceneTarget
            PlanResourceRole.MultisampleColorTarget -> GPUFrameResourceRole.LayerTarget
            PlanResourceRole.ReadbackStaging -> GPUFrameResourceRole.ReadbackStaging
            PlanResourceRole.CoverageMaskDepthStencil -> GPUFrameResourceRole.ClipDepthStencil
            PlanResourceRole.DepthStencil, PlanResourceRole.PathHardEdgeDepthStencil -> GPUFrameResourceRole.PathDepthStencil
            PlanResourceRole.VertexData -> GPUFrameResourceRole.VertexData
            PlanResourceRole.IndexData -> GPUFrameResourceRole.IndexData
            PlanResourceRole.UniformData -> GPUFrameResourceRole.UniformData
            else -> GPUFrameResourceRole.ClipMask
        }
        val usages = resource.usages().map { usage -> when (usage) {
            PlanResourceUsage.RenderAttachment, PlanResourceUsage.DepthStencilAttachment -> GPUFrameResourceUsage.RenderAttachment
            PlanResourceUsage.Sampled -> GPUFrameResourceUsage.TextureBinding
            PlanResourceUsage.CopySource -> GPUFrameResourceUsage.CopySource
            PlanResourceUsage.CopyDestination -> GPUFrameResourceUsage.CopyDestination
            PlanResourceUsage.MapRead -> GPUFrameResourceUsage.MapRead
            PlanResourceUsage.Vertex -> GPUFrameResourceUsage.Vertex
            PlanResourceUsage.Index -> GPUFrameResourceUsage.Index
            PlanResourceUsage.Uniform -> GPUFrameResourceUsage.Uniform
        } }.toSet()
        val lifetime = when (resource.lifetime) { PlanResourceLifetime.FrameLocal -> GPUFrameResourceLifetime.FrameLocal }
        val descriptor = when (resource.kind) {
            PlanResourceKind.Buffer -> GPUFrameBufferDescriptor(resource.byteSize, alignment)
            PlanResourceKind.Texture2D -> GPUFrameTextureDescriptor(bounds, when (resource.format) {
                is PlanTextureFormat.Color -> GPUColorFormat.RGBA8UnormSrgb
                PlanTextureFormat.CoverageMask -> GPUColorFormat.RGBA8Unorm
                is PlanTextureFormat.DepthStencil -> GPUColorFormat("depth24plus-stencil8")
                null -> error("Texture format is required")
            }, resource.sampleCountI32)
        }
        return GPUResourcePreparationRequest(ref, descriptor, role, usages, lifetime, resource.byteSize, resource.id.value)
    }

    private fun memory(graph: org.graphiks.kanvas.gpu.plan.RenderGraph, targetBytes: Long) = GPUFrameMemoryBudgetPlan(graph.peakFrameLocalBytes - targetBytes, targetBytes, GPUFrameMemoryCategory.entries.associateWith { if (it == GPUFrameMemoryCategory.CanonicalTarget) targetBytes else 0L }, emptyList(), graph.budget.maxFrameLocalBytes, null)
    private fun invalid() = GpuPlanLoweringResult.InvalidPlan(RenderDiagnostic(RenderDiagnosticCode("w4e.lowering.incompatible_plan"), RenderDiagnosticDomain.RESOURCE, RenderDiagnosticSeverity.ERROR, "The sealed W4e graph cannot be lowered."))
}
