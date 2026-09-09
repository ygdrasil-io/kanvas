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
import org.graphiks.math.geometry.ClipGeometryF32
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
import org.graphiks.kanvas.gpu.renderer.passes.GPUW4eMaskContinuationRequest
import org.graphiks.kanvas.gpu.renderer.passes.GPUW4eMaskResolveAction
import org.graphiks.kanvas.gpu.renderer.passes.GPUW4eSceneContinuationRequest
import org.graphiks.kanvas.gpu.renderer.passes.GPUW4eSceneResolveAction
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
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryAllocation
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryBudgetPlanner
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryBudgetRequest
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryResourceKind
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
        val limits = request.capabilities.limits ?: return invalid()
        val seal = GPUFrameCapabilitySeal.capture(request.frameId, request.deviceGeneration, request.capabilities)
        val replay = "w4e:${graph.id.value}"
        if (passes.none { it is PlanPass.PathRenderPass }) return invalid()
        val renders = passes.dropLast(1).mapIndexed { index, pass ->
            val path = pass as? PlanPass.PathRenderPass
            val consumer = path?.let { authority.consumerFor(it.id.value) }
            val preparedPath = path?.let { authority.pathFor(it.id.value) ?: return invalid() }
            val packet = preparedPath?.let { pathPacket(it, consumer, index) }
                ?: preparedClipPacket(pass, index, authority.clipPassFor(pass.id.value) ?: return invalid())
            val targetId = when (pass) {
                is PlanPass.PathMaskClearPass -> pass.target
                is PlanPass.ClipMaskInitialize -> pass.output
                is PlanPass.ClipMaskProducer -> pass.target
                is PlanPass.ClipMaskFold -> pass.output
                is PlanPass.PathRenderPass -> pass.target
                else -> return invalid()
            }
            val samples = when (pass) {
                is PlanPass.ClipMaskProducer -> pass.sampleCountI32
                is PlanPass.PathRenderPass -> if (preparedPath?.sample == SamplePlan.Multisample4) 4 else 1
                else -> 1
            }
            GPUTask.Render(
                GPUTaskID("task.w4e.${graph.id.value}.${pass.id.value}"), request.recordingId, GPUTaskPhase.Render,
                refs.getValue(targetId.value) as? GPUFrameTargetRef ?: return invalid(),
                org.graphiks.kanvas.gpu.renderer.state.GPULoadStorePlan(loadLabel(pass), org.graphiks.kanvas.gpu.renderer.state.GPUStorePlan.Store),
                if (samples == 4) GPUSamplePlan.MultisampleFrame(4) else GPUSamplePlan.SingleSampleFrame,
                resourceUses = resourceUses(pass, refs, graph.resources().associateBy { it.id.value }, consumer, preparedPath),
                drawPackets = listOf(packet),
                batchEligibilityByPacketId = mapOf(packet.packetId to GPUPassBatchEligibility(
                    kind = GPUPassBatchKind.SolidFill,
                    queueGuard = GPUPassBatchQueueGuard(emptyList(), emptyList()),
                )),
                depthStencilLoadStore = path?.let(::depthStencilLoadStore),
                w4eMaskContinuation = (pass as? PlanPass.ClipMaskProducer)
                    ?.takeIf { producer -> producer.sampleCountI32 == 4 }
                    ?.let { producer ->
                        GPUW4eMaskContinuationRequest(
                            maskTargetResourceId = producer.target.value,
                            resolveMaskResourceId = producer.resolveTarget?.value,
                            resolveAction = if (producer.resolveTarget == null) {
                                GPUW4eMaskResolveAction.Skip
                            } else {
                                GPUW4eMaskResolveAction.ResolveCanonical
                            },
                        )
                    },
                w4eSceneContinuation = preparedPath
                    ?.takeIf { path -> path.sample == SamplePlan.Multisample4 }
                    ?.let { path ->
                        GPUW4eSceneContinuationRequest(
                            sceneTargetResourceId = path.targetResourceId,
                            resolveSceneResourceId = path.resolveTargetResourceId,
                            resolveAction = if (path.resolveTargetResourceId == null) {
                                GPUW4eSceneResolveAction.Skip
                            } else {
                                GPUW4eSceneResolveAction.ResolveCanonical
                            },
                        )
                    },
            )
        }
        val frameAuthority = authority.issueFrameAuthority(request.frameId.value, seal.sealHash, renders)
        renders.forEach { render ->
            render.drawPackets.single().attachW4ePreparedFrameAuthority(frameAuthority)
        }
        if (!frameAuthority.validatesRenders(request.frameId.value, seal.sealHash, renders)) return invalid()
        val preparations = graph.resources().map { resource -> preparation(resource, refs.getValue(resource.id.value), bounds, graph.capabilities.copyBytesPerRowAlignment.toLong()) }
        val readback = GPUFrameReadbackRequest(GPUReadbackRequestID("w4e.${graph.id.value}.readback"), bounds, GPUReadbackPixelFormat.Rgba8Unorm, GPUColorInterpretation.EncodedPremulSrgb)
        val memory = memory(
            graph,
            bounds,
            limits,
            request.rendererAggregateMemoryBudgetBytes ?: graph.budget.maxFrameLocalBytes,
        )
        if (!GPUFrameMemoryBudgetPlanner.hasExactLimitIndependentFacts(memory) || memory.diagnostic != null ||
            memory.peakFrameTransientBytes + memory.targetResidentBytes != graph.peakFrameLocalBytes
        ) return invalid()
        val base = GPUTaskList(request.frameId, seal, listOf(GPURecordingSeal(request.recordingId, 0L, replay, replay, seal.sealHash)), replay, emptyList(), emptyList(), GPUTaskPhase.entries, memory)
        val atomicGroups = renders.associate { render ->
            val packet = render.drawPackets.single()
            render.taskId to (packet.w4ePreparedClipPass?.atomicGroupId ?: packet.w4ePreparedPath?.atomicGroupId)
        }
        when (val assembled = GPUCorePrimitiveW4ePreparedFrameTaskListAssembler().build(
            base, preparations, renders, target, stagingRef, readback, memory, atomicGroups,
        )) {
            is GPUCorePrimitivePreparedFrameResult.Recorded -> GpuPlanLoweringResult.Lowered(assembled.taskList, readback.requestId.value)
            is GPUCorePrimitivePreparedFrameResult.Refused -> invalid()
        }
    } catch (_: IllegalArgumentException) { invalid() }

    private fun ref(session: String, resource: PlanResource): GPUFrameResourceRef = when (resource.kind) {
        PlanResourceKind.Buffer -> GPUFrameBufferRef("$session.${resource.id.value}")
        PlanResourceKind.Texture2D -> GPUFrameTargetRef("$session.${resource.id.value}")
    }

    /** Prefix packets carry sealed native W4e clip authority; materialization executes each pass. */
    private fun preparedClipPacket(
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
        renderStepId = org.graphiks.kanvas.gpu.renderer.passes.GPURenderStepID("w4e.prepared-handoff"),
        renderStepVersion = 1,
        role = GPUDrawPacketRole.W4ePrepared,
        blendPlan = org.graphiks.kanvas.gpu.renderer.recording.canonicalSolidRectSrcOverBlendPlan(),
        bindingLayoutHash = "w4e.prepared-handoff.no-bindings",
        vertexSourceLabel = "w4e.prepared-handoff.no-vertices",
        targetStateHash = "w4e.sealed-target",
        originalPaintOrder = index,
        resourceGeneration = org.graphiks.kanvas.gpu.renderer.recording.PREPARED_FRAME_LATE_BOUND_RESOURCE_GENERATION,
        w4ePreparedClipPass = preparedPass,
    )

    /** Complete path handoff with no W4d clip mapper, execution plan, or structural-key fallback. */
    private fun pathPacket(
        preparedPath: GPUW4ePreparedClipPassAuthority.Path,
        consumer: GPUW4ePreparedClipConsumerAuthority?,
        index: Int,
    ): GPUDrawPacket = GPUDrawPacket(
        packetId = GPUDrawPacketID("packet.w4e.${preparedPath.passId}"),
        commandIdValue = preparedPath.commandIdValue,
        analysisRecordId = "w4e.sealed.${preparedPath.passId}",
        passId = preparedPath.passId,
        layerId = "root",
        bindingListId = "binding.w4e.${preparedPath.passId}",
        insertionReasonCode = "w4e-sealed-path",
        sortKey = index.toLong(),
        sortKeyPreimage = "w4e-path:$index",
        renderStepId = org.graphiks.kanvas.gpu.renderer.passes.GPURenderStepID("w4e.prepared-path"),
        renderStepVersion = 1,
        role = GPUDrawPacketRole.W4ePrepared,
        blendPlan = org.graphiks.kanvas.gpu.renderer.recording.canonicalSolidRectSrcOverBlendPlan(),
        bindingLayoutHash = "w4e.prepared-path.sealed-bindings",
        vertexSourceLabel = "w4e.prepared-path.sealed-geometry",
        targetStateHash = "w4e.prepared-path.attachments",
        originalPaintOrder = index,
        resourceGeneration = org.graphiks.kanvas.gpu.renderer.recording.PREPARED_FRAME_LATE_BOUND_RESOURCE_GENERATION,
        w4ePreparedClipConsumer = consumer,
        w4ePreparedPath = preparedPath,
    )

    private fun loadLabel(pass: PlanPass): String = when (pass) {
        is PlanPass.ClipMaskInitialize -> "clear"
        is PlanPass.PathMaskClearPass -> "clear"
        is PlanPass.ClipMaskProducer -> "clear"
        is PlanPass.ClipMaskFold -> "clear"
        is PlanPass.PathRenderPass -> if (pass.load == AttachmentLoadPlan.ClearTransparent) "clear" else "load"
        else -> "load"
    }

    private fun depthStencilLoadStore(
        pass: PlanPass.PathRenderPass,
    ): org.graphiks.kanvas.gpu.renderer.recording.GPUDepthStencilLoadStorePlan? = when (
        pass.depthStencilLoadStore
    ) {
        null -> null
        org.graphiks.kanvas.gpu.plan.PlanDepthStencilLoadStore.ClearZeroStore ->
            org.graphiks.kanvas.gpu.renderer.recording.GPUDepthStencilLoadStorePlan.WritableStencil(
                org.graphiks.kanvas.gpu.renderer.recording.GPUStencilLoadOperation.Clear,
                org.graphiks.kanvas.gpu.renderer.state.GPUStorePlan.Store,
                0u,
            )
        org.graphiks.kanvas.gpu.plan.PlanDepthStencilLoadStore.LoadStoreTestReset ->
            org.graphiks.kanvas.gpu.renderer.recording.GPUDepthStencilLoadStorePlan.WritableStencil(
                org.graphiks.kanvas.gpu.renderer.recording.GPUStencilLoadOperation.Load,
                org.graphiks.kanvas.gpu.renderer.state.GPUStorePlan.Store,
                null,
            )
    }

    private fun resourceUses(
        pass: PlanPass,
        refs: Map<String, GPUFrameResourceRef>,
        resourcesById: Map<String, PlanResource>,
        consumer: GPUW4ePreparedClipConsumerAuthority?,
        preparedPath: GPUW4ePreparedClipPassAuthority.Path?,
    ): List<GPUFrameResourceUse> {
        fun use(id: String, role: GPUFrameResourceRole, usage: GPUFrameResourceUsage, write: Boolean) =
            GPUFrameResourceUse(refs.getValue(id), role, usage, GPUFrameResourceLifetime.FrameLocal, write)
        fun nativeData(resourceRole: PlanResourceRole, frameRole: GPUFrameResourceRole, usage: GPUFrameResourceUsage) =
            use(
                requireNotNull(resourcesById.values.singleOrNull { it.role == resourceRole }) {
                    "W4e native $resourceRole resource must be unique"
                }.id.value,
                frameRole,
                usage,
                false,
            )
        return when (pass) {
            is PlanPass.PathMaskClearPass -> listOf(
                use(pass.target.value, GPUFrameResourceRole.ClipMask, GPUFrameResourceUsage.RenderAttachment, true),
            )
            is PlanPass.ClipMaskInitialize -> listOf(use(pass.output.value, GPUFrameResourceRole.ClipMask, GPUFrameResourceUsage.RenderAttachment, true))
            is PlanPass.ClipMaskProducer -> buildList {
                add(use(pass.target.value, GPUFrameResourceRole.ClipMask, GPUFrameResourceUsage.RenderAttachment, true))
                pass.resolveTarget?.let { add(use(it.value, GPUFrameResourceRole.ClipMask, GPUFrameResourceUsage.RenderAttachment, true)) }
                pass.depthStencil?.let { add(use(it.value, GPUFrameResourceRole.ClipDepthStencil, GPUFrameResourceUsage.RenderAttachment, true)) }
                // The W4e native payload is graph-sealed rather than materializer-local.  Make
                // the producer's exact V/I or U binding visible to preflight and the frame seal.
                when (pass.copyGeometryF32()) {
                    is ClipGeometryF32.Path -> {
                        add(nativeData(PlanResourceRole.VertexData, GPUFrameResourceRole.VertexData, GPUFrameResourceUsage.Vertex))
                        add(nativeData(PlanResourceRole.IndexData, GPUFrameResourceRole.IndexData, GPUFrameResourceUsage.Index))
                    }
                    else -> add(nativeData(PlanResourceRole.UniformData, GPUFrameResourceRole.UniformData, GPUFrameResourceUsage.Uniform))
                }
            }
            is PlanPass.ClipMaskFold -> listOf(
                use(pass.previous.value, GPUFrameResourceRole.ClipMask, GPUFrameResourceUsage.TextureBinding, false),
                use(pass.source.value, GPUFrameResourceRole.ClipMask, GPUFrameResourceUsage.TextureBinding, false),
                use(pass.output.value, GPUFrameResourceRole.ClipMask, GPUFrameResourceUsage.RenderAttachment, true),
            )
            is PlanPass.PathRenderPass -> buildList {
                val path = requireNotNull(preparedPath)
                // The W4e MSAA scene attachment is a real sealed render target.  It must be
                // present in the exact use list (as well as the final 1x resolve) so preflight
                // and native materialization cannot manufacture an unaccounted attachment.
                val targetRole = sealedRoleFor(requireNotNull(resourcesById[path.targetResourceId]) {
                    "W4e path target must reference a declared resource"
                })
                add(use(path.targetResourceId, targetRole, GPUFrameResourceUsage.RenderAttachment, true))
                add(use(path.vertexResourceId, GPUFrameResourceRole.VertexData, GPUFrameResourceUsage.Vertex, false))
                add(use(path.indexResourceId, GPUFrameResourceRole.IndexData, GPUFrameResourceUsage.Index, false))
                add(use(path.uniformResourceId, GPUFrameResourceRole.UniformData, GPUFrameResourceUsage.Uniform, false))
                path.depthStencilResourceId?.let { depth ->
                    add(use(depth, GPUFrameResourceRole.PathDepthStencil, GPUFrameResourceUsage.RenderAttachment, true))
                }
                path.binarySourceMaskResourceId?.let { sourceMask ->
                    add(use(sourceMask, GPUFrameResourceRole.ClipMask, GPUFrameResourceUsage.TextureBinding, false))
                }
                path.resolveTargetResourceId?.let { resolve ->
                    add(use(resolve, GPUFrameResourceRole.SceneTarget, GPUFrameResourceUsage.RenderAttachment, true))
                }
                when (consumer) {
                    is GPUW4ePreparedClipConsumerAuthority.Mask -> if (
                        consumer.maskResourceId != path.binarySourceMaskResourceId
                    ) add(use(consumer.maskResourceId, GPUFrameResourceRole.ClipMask, GPUFrameResourceUsage.TextureBinding, false))
                    is GPUW4ePreparedClipConsumerAuthority.InverseMask -> if (
                        consumer.maskResourceId != path.binarySourceMaskResourceId
                    ) add(use(consumer.maskResourceId, GPUFrameResourceRole.ClipMask, GPUFrameResourceUsage.TextureBinding, false))
                    is GPUW4ePreparedClipConsumerAuthority.InverseDomain,
                    null,
                    -> Unit
                }
            }
            else -> emptyList()
        }
    }

    private fun sealedRoleFor(resource: PlanResource): GPUFrameResourceRole = when (resource.role) {
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

    private fun preparation(resource: PlanResource, ref: GPUFrameResourceRef, bounds: GPUPixelBounds, alignment: Long): GPUResourcePreparationRequest {
        val role = sealedRoleFor(resource)
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

    private fun memory(
        graph: org.graphiks.kanvas.gpu.plan.RenderGraph,
        bounds: GPUPixelBounds,
        limits: org.graphiks.kanvas.gpu.renderer.capabilities.GPULimits,
        aggregateBudgetBytes: Long,
    ): GPUFrameMemoryBudgetPlan = GPUFrameMemoryBudgetPlanner.plan(
        GPUFrameMemoryBudgetRequest(
            graph.resources().map { resource ->
                GPUFrameMemoryAllocation(
                    label = "w4e.${resource.id.value}",
                    category = when (resource.role) {
                        PlanResourceRole.LogicalTarget -> GPUFrameMemoryCategory.CanonicalTarget
                        PlanResourceRole.ReadbackStaging -> GPUFrameMemoryCategory.ReadbackStaging
                        PlanResourceRole.MultisampleColorTarget,
                        PlanResourceRole.CoverageMaskMultisampleScratch,
                        -> GPUFrameMemoryCategory.FrameLocalMsaaColor
                        PlanResourceRole.CoverageMaskDepthStencil,
                        PlanResourceRole.PathHardEdgeDepthStencil,
                        PlanResourceRole.DepthStencil,
                        -> if (resource.sampleCountI32 == 4) {
                            GPUFrameMemoryCategory.FrameLocalMsaaDepthStencil
                        } else {
                            GPUFrameMemoryCategory.ReusableScratch
                        }
                        else -> GPUFrameMemoryCategory.ReusableScratch
                    },
                    bytes = resource.byteSize,
                    resourceKind = when (resource.kind) {
                        PlanResourceKind.Texture2D -> GPUFrameMemoryResourceKind.Texture2D
                        PlanResourceKind.Buffer -> GPUFrameMemoryResourceKind.Buffer
                    },
                    extent = if (resource.kind == PlanResourceKind.Texture2D) bounds else null,
                    firstPassIndex = resource.firstPassIndex,
                    lastPassIndexExclusive = resource.lastPassIndexExclusive,
                )
            },
            aggregateBudgetBytes,
            limits,
        ),
    )
    private fun invalid() = GpuPlanLoweringResult.InvalidPlan(RenderDiagnostic(RenderDiagnosticCode("w4e.lowering.incompatible_plan"), RenderDiagnosticDomain.RESOURCE, RenderDiagnosticSeverity.ERROR, "The sealed W4e graph cannot be lowered."))
}
