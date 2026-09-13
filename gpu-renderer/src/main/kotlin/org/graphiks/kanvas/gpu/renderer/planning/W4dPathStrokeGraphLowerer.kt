package org.graphiks.kanvas.gpu.renderer.planning

import org.graphiks.kanvas.gpu.plan.AttachmentLoadPlan
import org.graphiks.kanvas.gpu.plan.AttachmentStorePlan
import org.graphiks.kanvas.gpu.plan.MaterialPlanTable
import org.graphiks.kanvas.gpu.plan.PathDraw
import org.graphiks.kanvas.gpu.plan.PathDrawGeometry
import org.graphiks.kanvas.gpu.plan.PathFillDraw
import org.graphiks.kanvas.gpu.plan.PathFillMemoryFootprint
import org.graphiks.kanvas.gpu.plan.PathStrokePlanBudget
import org.graphiks.kanvas.gpu.plan.PathStrokePlanBudgetResult
import org.graphiks.kanvas.gpu.plan.PathFillStrategy
import org.graphiks.kanvas.gpu.plan.PathStrokeDraw
import org.graphiks.kanvas.gpu.plan.PlanBufferGrowth
import org.graphiks.kanvas.gpu.plan.PlanDepthStencilAccess
import org.graphiks.kanvas.gpu.plan.PlanDepthStencilFormat
import org.graphiks.kanvas.gpu.plan.PlanDepthStencilLoadStore
import org.graphiks.kanvas.gpu.plan.PlanDrawDataResources
import org.graphiks.kanvas.gpu.plan.PlanDrawMaterialAuthority
import org.graphiks.kanvas.gpu.plan.PlanLogicalColorFormat
import org.graphiks.kanvas.gpu.plan.PlanOperationCapability
import org.graphiks.kanvas.gpu.plan.PlanPass
import org.graphiks.kanvas.gpu.plan.PlanPassDependency
import org.graphiks.kanvas.gpu.plan.PlanResource
import org.graphiks.kanvas.gpu.plan.PlanResourceKind
import org.graphiks.kanvas.gpu.plan.PlanResourceLifetime
import org.graphiks.kanvas.gpu.plan.PlanResourceRole
import org.graphiks.kanvas.gpu.plan.PlanResourceUsage
import org.graphiks.kanvas.gpu.plan.PlanTextureFormat
import org.graphiks.kanvas.gpu.plan.RenderGraph
import org.graphiks.kanvas.gpu.plan.W4dPathStrokePlanCompiler
import org.graphiks.kanvas.gpu.plan.W4dPlanDiagnostics
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilityFact
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.clips.GPUBounds
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoveragePlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionPlan
import org.graphiks.kanvas.gpu.renderer.color.GPUColorFormat
import org.graphiks.kanvas.gpu.renderer.color.GPUColorInterpretation
import org.graphiks.kanvas.gpu.renderer.commands.GPUFrameProvenance
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitivePreparedPacketAuthority
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveRenderPipelineStructuralKey
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketID
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketRole
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchEligibility
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchKind
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchQueueGuard
import org.graphiks.kanvas.gpu.renderer.passes.GPURenderStepID
import org.graphiks.kanvas.gpu.renderer.passes.GPUSamplePlan
import org.graphiks.kanvas.gpu.renderer.passes.W4dSessionScratchDrawV1
import org.graphiks.kanvas.gpu.renderer.passes.W4dSessionScratchV1
import org.graphiks.kanvas.gpu.renderer.passes.canonicalIdentity
import org.graphiks.kanvas.gpu.renderer.passes.corePrimitivePathStencilRenderPipelineStructuralKey
import org.graphiks.kanvas.gpu.renderer.passes.corePrimitiveRenderPipelineStructuralKey
import org.graphiks.kanvas.gpu.renderer.passes.corePrimitiveStructuralColorFormat
import org.graphiks.kanvas.gpu.renderer.payloads.CORE_PRIMITIVE_RENDER_STEP_IDENTITY
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveCoverageMode
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveFillRule
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometryInput
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometryMode
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitivePayloadGatherer
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitivePayloadInput
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveSourceFamily
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.recording.CORE_PRIMITIVE_BINDING_LAYOUT_HASH
import org.graphiks.kanvas.gpu.renderer.recording.CORE_PRIMITIVE_RENDER_PIPELINE_KEY
import org.graphiks.kanvas.gpu.renderer.recording.CORE_PRIMITIVE_VERTEX_SOURCE_LABEL
import org.graphiks.kanvas.gpu.renderer.recording.GPUCorePrimitivePreparedFrameResult
import org.graphiks.kanvas.gpu.renderer.recording.GPUCorePrimitiveW4dPreparedFrameRequest
import org.graphiks.kanvas.gpu.renderer.recording.GPUCorePrimitiveW4dPreparedFrameTaskListAssembler
import org.graphiks.kanvas.gpu.renderer.recording.GPUDepthStencilLoadStorePlan
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameCapabilitySeal
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameReadbackRequest
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackPixelFormat
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackRequestID
import org.graphiks.kanvas.gpu.renderer.recording.GPURecordingSeal
import org.graphiks.kanvas.gpu.renderer.recording.GPUStencilLoadOperation
import org.graphiks.kanvas.gpu.renderer.recording.GPUTask
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskID
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskList
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskPhase
import org.graphiks.kanvas.gpu.renderer.recording.PREPARED_FRAME_LATE_BOUND_RESOURCE_GENERATION
import org.graphiks.kanvas.gpu.renderer.recording.canonicalSolidRectSrcOverBlendPlan
import org.graphiks.kanvas.gpu.renderer.recording.corePrimitiveScissorAuthority
import org.graphiks.kanvas.gpu.renderer.recording.corePrimitiveTargetStateHash
import org.graphiks.kanvas.gpu.renderer.resources.GPUCorePrimitiveFramePoolCapacities
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryAllocation
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryBudgetPlan
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryCategory
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryResourceKind
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceLifetime
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUse
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTextureDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTextureRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourcePreparationRequest
import org.graphiks.kanvas.gpu.renderer.resources.GPUUniformSlabPayload
import org.graphiks.kanvas.gpu.renderer.resources.GPUUniformSlabPlanner
import org.graphiks.kanvas.gpu.renderer.resources.GPUUniformSlabPlanningResult
import org.graphiks.kanvas.gpu.renderer.resources.corePrimitiveFramePoolCapacitiesOrNull
import org.graphiks.kanvas.gpu.renderer.state.GPULoadStorePlan
import org.graphiks.kanvas.gpu.renderer.state.GPUPathSourceAuthority
import org.graphiks.kanvas.gpu.renderer.state.GPUStorePlan
import org.graphiks.kanvas.render.ir.RenderDiagnostic
import org.graphiks.kanvas.render.ir.RenderDiagnosticCode
import org.graphiks.kanvas.render.ir.RenderDiagnosticDomain
import org.graphiks.kanvas.render.ir.RenderDiagnosticSeverity
import org.graphiks.math.geometry.FillRule
import org.graphiks.math.geometry.PathFillGeometryF32
import org.graphiks.math.geometry.PathStrokeDrawMode
import org.graphiks.math.geometry.PathStrokeWidthF64
import org.graphiks.math.color.ColorF32

/** Lowers only the authenticated W4d path-draw graph; it never re-enters Scene IR or legacy tessellation. */
internal class W4dPathStrokeGraphLowerer {
    internal fun w5bPacket(pass: PlanPass, draws: List<PathDraw>, table: MaterialPlanTable,
        bounds: GPUPixelBounds): W4dBuiltPass {
        val command = when (pass) {
            is PlanPass.RenderPass -> pass.draws().single().commandIndex
            is PlanPass.StencilGeometryProducerV3 -> pass.commandIndexI32
            is PlanPass.StencilCover -> pass.draw.commandIndex
            else -> error("Not a W5b path geometry pass")
        }
        val draw = draws.single { it.commandIndex == command }
        val producer = pass is PlanPass.StencilGeometryProducerV3
        if (producer) require(pass.copyGeometry() == draw.copyPathGeometry() && pass.copyScissorI32() == draw.copyScissorI32())
        val clip = clipFor(draw, bounds)
        return packet(draw, draws.indexOf(draw), pass.id.value,
            if (producer) GPUDrawPacketRole.PathStencilProducer else if (pass is PlanPass.StencilCover)
                GPUDrawPacketRole.PathStencilCover else GPUDrawPacketRole.Shading,
            if (draw.strategy == PathFillStrategy.StencilCover) GPUCorePrimitiveCoverageMode.Stencil1x else GPUCorePrimitiveCoverageMode.FullOrScissor,
            if (producer) GPUClipCoveragePlan.NoClip else clip.coverage,
            if (producer) GPUClipExecutionPlan.NoClip else clip.execution, table, bounds)
    }

    fun lower(request: GpuPlanLoweringRequest): GpuPlanLoweringResult = try {
        if (!(W4dPathStrokePlanCompiler.isHistoricalCapabilityId(request.graph.capabilityId) ||
                W4dPathStrokePlanCompiler.isW5aMaterialCapabilityId(request.graph.capabilityId))) {
            return invalid("The graph is not a W4d path-draw graph.")
        }
        if (!request.graph.verifyW4dCompilerWitness()) {
            return invalid("The W4d graph lacks its compiler-issued witness.")
        }
        val current = when (val adapted = request.capabilities.toPlanCapabilitySnapshot(request.deviceGeneration)) {
            is GpuPlanCapabilityAdapterResult.Supported -> adapted.snapshot
            is GpuPlanCapabilityAdapterResult.Unsupported -> return GpuPlanLoweringResult.UnsupportedCapability(adapted.diagnostic)
        }
        if (request.graph.capabilities != current) return capability(
            W4dPlanDiagnostics.CapabilityUnavailable,
            "The W4d graph capability snapshot is stale.",
        )
        if (!hasExactW4dCapabilityFacts(request.graph)) return capability(
            missingW4dCapabilityFact(request.graph),
            "The W4d graph lacks required depth-stencil capability facts.",
        )
        if (request.graph.budget != request.currentBudget) return invalid("The W4d graph budget is stale.")
        val graph = validateW4dGraph(request.graph) ?: return invalid("The graph is not the exact W4d topology.")
        val limits = request.capabilities.limits
            ?: return capability(W4dPlanDiagnostics.CapabilityUnavailable, "W4d lowering requires observed renderer limits.")
        val maxBufferSize = limits.maxBufferSize
            ?: return capability(W4dPlanDiagnostics.CapabilityUnavailable, "W4d lowering requires an observed maxBufferSize.")
        val maxDynamicUniformBuffers = limits.maxDynamicUniformBuffersPerPipelineLayout
            ?.takeIf { it >= 1L }
            ?: return capability(
                W4dPlanDiagnostics.CapabilityUnavailable,
                "W4d lowering requires one dynamic uniform buffer binding.",
            )
        val targetBounds = GPUPixelBounds(0, 0, request.graph.targetExtent.width, request.graph.targetExtent.height)
        val sessionIdentity = request.w5aCompositeSessionIdentity ?: w4dSessionIdentity(request.deviceGeneration, targetBounds)
        val target = GPUFrameTargetRef("$sessionIdentity.target")
        val staging = GPUFrameBufferRef("$sessionIdentity.staging")
        val depthStencil = graph.depthStencil?.let { GPUFrameTextureRef("$sessionIdentity.depth-stencil") }
        val targetPreparation = GPUResourcePreparationRequest(
            target,
            GPUFrameTextureDescriptor(targetBounds, GPUColorFormat.RGBA8UnormSrgb, 1),
            GPUFrameResourceRole.SceneTarget,
            setOf(GPUFrameResourceUsage.RenderAttachment, GPUFrameResourceUsage.CopySource),
            GPUFrameResourceLifetime.FrameLocal,
            graph.target.byteSize,
            "$sessionIdentity.target",
        )
        val stagingPreparation = GPUResourcePreparationRequest(
            staging,
            GPUFrameBufferDescriptor(graph.staging.byteSize, request.graph.capabilities.copyBytesPerRowAlignment.toLong()),
            GPUFrameResourceRole.ReadbackStaging,
            setOf(GPUFrameResourceUsage.CopyDestination, GPUFrameResourceUsage.MapRead),
            GPUFrameResourceLifetime.FrameLocal,
            graph.staging.byteSize,
            "$sessionIdentity.staging",
        )
        val memory = memoryBudget(
            request.graph,
            graph,
            targetBounds,
            request.deviceGeneration,
            limits.capabilityFacts("frame-memory-budget"),
            request.w5aCompositeSessionIdentity,
        ) ?: return invalid("The W4d graph memory facts cannot be represented by the renderer.")

        val builtPasses = graph.renderPasses.map { pass ->
            builtPass(pass, graph.visualDraws, graph.materialPlanTable, targetBounds)
        }
        val capabilitySeal = GPUFrameCapabilitySeal.capture(request.frameId, request.deviceGeneration, request.capabilities)
        val scratch = sealScratch(
            graph = graph,
            builtPasses = builtPasses,
            planId = request.graph.id.value,
            capabilityId = request.graph.capabilityId,
            target = target,
            staging = staging,
            targetBounds = targetBounds,
            depthStencilResourceId = graph.depthStencil?.id,
            capabilitySealHash = capabilitySeal.sealHash,
            deviceGeneration = request.deviceGeneration.value,
            maxBufferSize = maxBufferSize,
            maxDynamicUniformBuffers = maxDynamicUniformBuffers,
        ) ?: return invalid("W4d scratch packing is invalid.")
        builtPasses.forEach { built ->
            val publicPipelineKey = built.packet.renderPipelineKey ?: return invalid("W4d packet lacks a public pipeline key.")
            built.packet.attachCorePrimitivePreparedAuthority(
                GPUCorePrimitivePreparedPacketAuthority.plannedW4d(
                    packet = built.packet,
                    structuralPipelineKey = built.structuralPipelineKey,
                    renderPipelineKey = publicPipelineKey,
                    planId = request.graph.id.value,
                    capabilitySealHash = capabilitySeal.sealHash,
                    scratch = scratch,
                ),
            )
        }
        val replay = "w4d:${request.graph.id.value}"
        val renders = graph.renderPasses.zip(builtPasses).map { (pass, built) ->
            renderTask(
                pass = pass,
                packet = built.packet,
                target = target,
                depthStencil = depthStencil,
                recordingId = request.recordingId,
                planId = request.graph.id.value,
            ) ?: return invalid("W4d plan pass cannot be represented by a render task.")
        }
        val base = GPUTaskList(
            request.frameId,
            capabilitySeal,
            listOf(GPURecordingSeal(request.recordingId, 0L, replay, replay, capabilitySeal.sealHash)),
            replay,
            renders,
            emptyList(),
            GPUTaskPhase.entries,
            memory,
        )
        val readback = GPUFrameReadbackRequest(
            GPUReadbackRequestID("w4d.${request.graph.id.value}.readback"),
            targetBounds,
            GPUReadbackPixelFormat.Rgba8Unorm,
            GPUColorInterpretation.EncodedPremulSrgb,
        )
        when (val assembled = GPUCorePrimitiveW4dPreparedFrameTaskListAssembler().buildPreplanned(
            GPUCorePrimitiveW4dPreparedFrameRequest(
                planId = request.graph.id,
                baseTaskList = base,
                target = target,
                targetPreparation = targetPreparation,
                staging = staging,
                stagingPreparation = stagingPreparation,
                readbackRequest = readback,
                memoryBudget = memory,
                renderPassIds = graph.renderPasses.map(PlanPass::id),
                readbackPassId = graph.readback.id,
                scratch = scratch,
                compositeWitness = request.w5aCompositeSessionIdentity?.let {
                    org.graphiks.kanvas.gpu.renderer.recording.GPUW5aCompositeLaneWitnessV1(
                        it, requireNotNull(request.w5aCompositeLaneOrdinal), request.graph.id.value,
                        renders.flatMap { render -> render.drawPackets }.map { packet -> packet.packetId },
                    )
                },
            ),
        )) {
            is GPUCorePrimitivePreparedFrameResult.Recorded ->
                GpuPlanLoweringResult.Lowered(assembled.taskList, readback.requestId.value)
            is GPUCorePrimitivePreparedFrameResult.Refused -> invalid(assembled.diagnostic.message)
        }
    } catch (_: ArithmeticException) {
        invalid("The W4d graph cannot be represented with checked I64 arithmetic.")
    } catch (error: IllegalArgumentException) {
        invalid(error.message ?: "The graph cannot be lowered into W4d renderer values.")
    }

    private fun validateW4dGraph(graph: RenderGraph): W4dGraph? {
        if (!(W4dPathStrokePlanCompiler.isHistoricalCapabilityId(graph.capabilityId) ||
                W4dPathStrokePlanCompiler.isW5aMaterialCapabilityId(graph.capabilityId)) ||
            graph.colorFormat != PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL ||
            !hasExactW4dCapabilityFacts(graph)
        ) return null
        val passes = graph.passes()
        val readback = passes.lastOrNull() as? PlanPass.ReadbackPass ?: return null
        val renderPasses = passes.dropLast(1)
        if (renderPasses.isEmpty() || renderPasses.any { pass ->
                pass !is PlanPass.RenderPass && pass !is PlanPass.StencilProducer && pass !is PlanPass.StencilCover
            }
        ) return null
        val visual = visualDraws(renderPasses) ?: return null
        if (visual.size !in 1..512 || visual.none { it.draw is PathStrokeDraw } ||
            graph.visualCommandCount != visual.size ||
            visual.zipWithNext().any { (first, second) -> first.draw.commandIndex >= second.draw.commandIndex }
        ) return null
        val materialPlanTable = graph.materialPlanTableOrNull()
        if (W4dPathStrokePlanCompiler.isW5aMaterialCapabilityId(graph.capabilityId)) {
            val table = materialPlanTable ?: return null
            if (visual.any { visualDraw ->
                    val authority = visualDraw.draw.materialAuthority as? PlanDrawMaterialAuthority.MaterialV1
                        ?: return@any true
                    runCatching { W5aMaterialPlanLowerer().lower(table, authority.ref) }.getOrNull() == null
                }
            ) return null
        } else if (materialPlanTable != null ||
            visual.any { it.draw.materialAuthority !is PlanDrawMaterialAuthority.LegacyColorV1 }
        ) return null
        val footprint = when (val result = PathStrokePlanBudget.calculate(
            graph.targetExtent,
            visual.map { visualDraw -> visualDraw.draw.copyFillGeometryF32() },
            graph.capabilities,
            graph.budget,
            W4dPathStrokePlanCompiler.isW5aMaterialCapabilityId(graph.capabilityId),
        )) {
            is PathStrokePlanBudgetResult.WithinBudget -> result.footprint
            is PathStrokePlanBudgetResult.Exceeded,
            is PathStrokePlanBudgetResult.Invalid,
            -> return null
        }
        val expectedResources = expectedResources(graph, footprint, renderPasses, visual.any { it.draw.strategy == PathFillStrategy.StencilCover })
            ?: return null
        val resources = graph.resources()
        if (resources.size != expectedResources.size ||
            !resources.zip(expectedResources).all { (actual, expected) -> actual.matches(expected) }
        ) return null
        val target = resources[0]
        val staging = resources[1]
        val vertex = resources[2]
        val index = resources[3]
        val uniform = resources[4]
        val depthStencil = resources.getOrNull(5)
        val bindings = PlanDrawDataResources(vertex.id, index.id, uniform.id)
        if (readback.ordinal != 0 ||
            readback.id != PlanPass.ReadbackPass(0, target.id, staging.id, footprint.readbackBytesPerRow).id ||
            readback.source != target.id || readback.staging != staging.id ||
            readback.bytesPerRow != footprint.readbackBytesPerRow ||
            graph.dependencies() != passes.zipWithNext().map { (before, after) -> PlanPassDependency(before.id, after.id) } ||
            graph.peakFrameLocalBytes != footprint.peakBytes ||
            listOf(staging.byteSize, vertex.byteSize, index.byteSize, uniform.byteSize).any { bytes ->
                bytes > graph.capabilities.maxBufferSizeBytes
            } ||
            !hasExactPasses(renderPasses, target.id, depthStencil?.id, bindings, visual)
        ) return null
        return W4dGraph(
            capabilityId = graph.capabilityId,
            target = target,
            staging = staging,
            vertex = vertex,
            index = index,
            uniform = uniform,
            depthStencil = depthStencil,
            renderPasses = renderPasses,
            readback = readback,
            visualDraws = visual,
            footprint = footprint,
            materialPlanTable = materialPlanTable,
        )
    }

    private fun hasExactW4dCapabilityFacts(graph: RenderGraph): Boolean {
        val capabilities = graph.capabilities
        val policy = capabilities.bufferAllocationPolicy
        val usesStencil = graph.passes().any { pass -> pass is PlanPass.StencilProducer }
        return graph.colorFormat in capabilities.supportedFormats() &&
            capabilities.maxDynamicUniformBuffersPerPipelineLayout >= 1 &&
            W4D_REQUIRED_OPERATIONS.all { it in capabilities.supportedOperations() } &&
            (!usesStencil || (
                PlanOperationCapability.DepthStencilAttachment in capabilities.supportedOperations() &&
                    PlanOperationCapability.StencilCover in capabilities.supportedOperations() &&
                    PlanDepthStencilFormat.Depth24PlusStencil8 in capabilities.supportedDepthStencilFormats()
                )) &&
            capabilities.copyBytesPerRowAlignment.isPositivePowerOfTwo() &&
            capabilities.minUniformBufferOffsetAlignment.isPositivePowerOfTwo() &&
            policy.growth == PlanBufferGrowth.PowerOfTwo &&
            policy.vertexFloorBytes.isPositivePowerOfTwo() &&
            policy.indexFloorBytes.isPositivePowerOfTwo() &&
            policy.uniformFloorBytes.isPositivePowerOfTwo()
    }

    private fun missingW4dCapabilityFact(graph: RenderGraph): RenderDiagnosticCode =
        if (PlanDepthStencilFormat.Depth24PlusStencil8 !in graph.capabilities.supportedDepthStencilFormats()) {
            W4dPlanDiagnostics.CapabilityUnavailable
        } else {
            W4dPlanDiagnostics.CapabilityUnavailable
        }

    private fun visualDraws(renderPasses: List<PlanPass>): List<W4dVisualDraw>? {
        val visual = mutableListOf<W4dVisualDraw>()
        var passIndex = 0
        var renderOrdinal = 0
        var producerOrdinal = 0
        var coverOrdinal = 0
        var firstColor = true
        while (passIndex < renderPasses.size) {
            when (val pass = renderPasses[passIndex]) {
                is PlanPass.RenderPass -> {
                    val draw = pass.draws().singleOrNull() as? PathDraw ?: return null
                    if (pass.ordinal != renderOrdinal++ || draw.strategy != PathFillStrategy.DirectTriangle ||
                        pass.load != (if (firstColor) AttachmentLoadPlan.ClearTransparent else AttachmentLoadPlan.Load) ||
                        pass.store != AttachmentStorePlan.Store || !isExactPathDraw(draw)
                    ) return null
                    visual += W4dVisualDraw(draw, passIndex, null)
                    firstColor = false
                    passIndex += 1
                }
                is PlanPass.StencilProducer -> {
                    val cover = renderPasses.getOrNull(passIndex + 1) as? PlanPass.StencilCover ?: return null
                    val draw = pass.draw
                    if (pass.ordinal != producerOrdinal++ || cover.ordinal != coverOrdinal++ ||
                        draw !== cover.draw || draw.strategy != PathFillStrategy.StencilCover ||
                        pass.load != (if (firstColor) AttachmentLoadPlan.ClearTransparent else AttachmentLoadPlan.Load) ||
                        pass.store != AttachmentStorePlan.Store || cover.load != AttachmentLoadPlan.Load ||
                        cover.store != AttachmentStorePlan.Store ||
                        pass.depthStencilAccess != PlanDepthStencilAccess.Write ||
                        cover.depthStencilAccess != PlanDepthStencilAccess.ReadWrite ||
                        pass.depthStencilLoadStore != PlanDepthStencilLoadStore.ClearZeroStore ||
                        cover.depthStencilLoadStore != PlanDepthStencilLoadStore.LoadStoreTestReset ||
                        pass.atomicGroup != cover.atomicGroup ||
                        pass.atomicGroup.value != draw.expectedAtomicGroupId() ||
                        !isExactPathDraw(draw)
                    ) return null
                    visual += W4dVisualDraw(draw, passIndex, pass.atomicGroup.value)
                    firstColor = false
                    passIndex += 2
                }
                is PlanPass.StencilCover -> return null
                else -> return null
            }
        }
        return visual
    }

    private fun hasExactPasses(
        renderPasses: List<PlanPass>,
        targetId: org.graphiks.kanvas.gpu.plan.PlanResourceId,
        depthStencilId: org.graphiks.kanvas.gpu.plan.PlanResourceId?,
        bindings: PlanDrawDataResources,
        visual: List<W4dVisualDraw>,
    ): Boolean {
        val usesStencil = visual.any { it.draw.strategy == PathFillStrategy.StencilCover }
        if (usesStencil != (depthStencilId != null)) return false
        return renderPasses.all { pass ->
            when (pass) {
                is PlanPass.RenderPass ->
                    pass.target == targetId && pass.drawDataResources == bindings && pass.draws().size == 1
                is PlanPass.StencilProducer ->
                    pass.target == targetId && pass.depthStencil == depthStencilId && pass.drawDataResources == bindings
                is PlanPass.StencilCover ->
                    pass.target == targetId && pass.depthStencil == depthStencilId && pass.drawDataResources == bindings
                else -> false
            }
        }
    }

    private fun isExactPathDraw(draw: PathDraw): Boolean {
        val geometry = draw.copyFillGeometryF32()
        val scissor = draw.copyScissorI32()
        val direct = geometry.copyDirectTriangleF32OrNull()
        val stencil = geometry.copyStencilEdgeFanF32OrNull()
        val exactMode = when (draw) {
            is org.graphiks.kanvas.gpu.plan.W5bW4ePathDraw -> false
            is org.graphiks.kanvas.gpu.plan.GeneralPathDraw -> false
            is PathFillDraw -> true
            is PathStrokeDraw -> when (draw.mode) {
                PathStrokeDrawMode.Stroke -> when (val width = draw.styleF64.widthF64) {
                    PathStrokeWidthF64.Hairline -> true
                    is PathStrokeWidthF64.Finite -> width.valueF64 > 0.0
                }
                PathStrokeDrawMode.StrokeAndFill -> draw.styleF64.widthF64 is PathStrokeWidthF64.Finite
            }
        }
        return draw.commandIndex >= 0 && !scissor.isEmpty64() && exactMode &&
            draw.coverage == org.graphiks.kanvas.gpu.plan.CoveragePlan.FullOrScissor &&
            draw.sample == org.graphiks.kanvas.gpu.plan.SamplePlan.SingleSample &&
            draw.blend == org.graphiks.kanvas.gpu.plan.BlendPlan.SrcOver &&
            when (draw.strategy) {
                PathFillStrategy.DirectTriangle ->
                    direct != null && stencil == null && geometry.fillRule == FillRule.WINDING
                PathFillStrategy.StencilCover ->
                    direct == null && stencil != null && geometry.fillRule in setOf(FillRule.WINDING, FillRule.EVEN_ODD)
            }
    }

    private fun expectedResources(
        graph: RenderGraph,
        footprint: PathFillMemoryFootprint,
        renderPasses: List<PlanPass>,
        usesStencil: Boolean,
    ): List<PlanResource>? = try {
        val passCount = renderPasses.size + 1
        val firstStencilPassIndex = renderPasses.indexOfFirst { pass -> pass is PlanPass.StencilProducer }
        buildList {
            add(
                PlanResource.of(
                    PlanResourceRole.LogicalTarget,
                    0,
                    PlanResourceKind.Texture2D,
                    PlanTextureFormat.Color(graph.colorFormat),
                    graph.targetExtent,
                    footprint.targetBytes,
                    setOf(PlanResourceUsage.RenderAttachment, PlanResourceUsage.CopySource),
                    PlanResourceLifetime.FrameLocal,
                    0,
                    passCount,
                ),
            )
            add(
                PlanResource.of(
                    PlanResourceRole.ReadbackStaging,
                    0,
                    PlanResourceKind.Buffer,
                    null,
                    null,
                    footprint.readbackBytes,
                    setOf(PlanResourceUsage.CopyDestination, PlanResourceUsage.MapRead),
                    PlanResourceLifetime.FrameLocal,
                    renderPasses.size,
                    passCount,
                ),
            )
            add(
                PlanResource.of(
                    PlanResourceRole.VertexData,
                    0,
                    PlanResourceKind.Buffer,
                    null,
                    null,
                    footprint.vertexCapacityBytes,
                    setOf(PlanResourceUsage.Vertex, PlanResourceUsage.CopyDestination),
                    PlanResourceLifetime.FrameLocal,
                    0,
                    passCount,
                ),
            )
            add(
                PlanResource.of(
                    PlanResourceRole.IndexData,
                    0,
                    PlanResourceKind.Buffer,
                    null,
                    null,
                    footprint.indexCapacityBytes,
                    setOf(PlanResourceUsage.Index, PlanResourceUsage.CopyDestination),
                    PlanResourceLifetime.FrameLocal,
                    0,
                    passCount,
                ),
            )
            add(
                PlanResource.of(
                    PlanResourceRole.UniformData,
                    0,
                    PlanResourceKind.Buffer,
                    null,
                    null,
                    footprint.uniformCapacityBytes,
                    setOf(PlanResourceUsage.Uniform, PlanResourceUsage.CopyDestination),
                    PlanResourceLifetime.FrameLocal,
                    0,
                    passCount,
                ),
            )
            if (usesStencil) {
                add(
                    PlanResource.of(
                        PlanResourceRole.DepthStencil,
                        0,
                        PlanResourceKind.Texture2D,
                        PlanTextureFormat.DepthStencil(PlanDepthStencilFormat.Depth24PlusStencil8),
                        graph.targetExtent,
                        footprint.depthStencilBytes,
                        setOf(PlanResourceUsage.DepthStencilAttachment),
                        PlanResourceLifetime.FrameLocal,
                        firstStencilPassIndex,
                        passCount,
                    ),
                )
            }
        }
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun PlanResource.matches(expected: PlanResource): Boolean =
        id == expected.id && role == expected.role && ordinal == expected.ordinal && kind == expected.kind &&
            format == expected.format && copyExtent() == expected.copyExtent() && byteSize == expected.byteSize &&
            usages() == expected.usages() && lifetime == expected.lifetime &&
            firstPassIndex == expected.firstPassIndex && lastPassIndexExclusive == expected.lastPassIndexExclusive

    private fun builtPass(
        pass: PlanPass,
        visualDraws: List<W4dVisualDraw>,
        materialPlanTable: MaterialPlanTable?,
        targetBounds: GPUPixelBounds,
    ): W4dBuiltPass = when (pass) {
        is PlanPass.RenderPass -> {
            val draw = pass.draws().singleOrNull() as? PathDraw
                ?: error("Validated W4d graph contains a non-path direct draw")
            val clip = clipFor(draw, targetBounds)
            packet(
                draw = draw,
                paintOrder = paintOrder(draw, visualDraws),
                passId = pass.id.value,
                role = GPUDrawPacketRole.Shading,
                coverageMode = GPUCorePrimitiveCoverageMode.FullOrScissor,
                clipCoverage = clip.coverage,
                clipExecution = clip.execution,
                materialPlanTable = materialPlanTable,
                targetBounds = targetBounds,
            )
        }
        is PlanPass.StencilProducer -> {
            val draw = pass.draw
            packet(
                draw = draw,
                paintOrder = paintOrder(draw, visualDraws),
                passId = pass.id.value,
                role = GPUDrawPacketRole.PathStencilProducer,
                coverageMode = GPUCorePrimitiveCoverageMode.Stencil1x,
                clipCoverage = GPUClipCoveragePlan.NoClip,
                clipExecution = GPUClipExecutionPlan.NoClip,
                materialPlanTable = materialPlanTable,
                targetBounds = targetBounds,
            )
        }
        is PlanPass.StencilCover -> {
            val draw = pass.draw
            val clip = clipFor(draw, targetBounds)
            packet(
                draw = draw,
                paintOrder = paintOrder(draw, visualDraws),
                passId = pass.id.value,
                role = GPUDrawPacketRole.PathStencilCover,
                coverageMode = GPUCorePrimitiveCoverageMode.Stencil1x,
                clipCoverage = clip.coverage,
                clipExecution = clip.execution,
                materialPlanTable = materialPlanTable,
                targetBounds = targetBounds,
            )
        }
        else -> error("Validated W4d graph contains an unsupported pass")
    }

    private fun paintOrder(draw: PathDraw, visualDraws: List<W4dVisualDraw>): Int =
        visualDraws.indexOfFirst { visual -> visual.draw === draw }
            .takeIf { index -> index >= 0 }
            ?: error("Validated W4d pass has no visual draw order")

    private fun packet(
        draw: PathDraw,
        paintOrder: Int,
        passId: String,
        role: GPUDrawPacketRole,
        coverageMode: GPUCorePrimitiveCoverageMode,
        clipCoverage: GPUClipCoveragePlan,
        clipExecution: GPUClipExecutionPlan,
        materialPlanTable: MaterialPlanTable?,
        targetBounds: GPUPixelBounds,
    ): W4dBuiltPass {
        val geometry = draw.copyFillGeometryF32()
        val scissor = draw.copyScissorI32()
        val plannedScissor = GPUPixelBounds(scissor.left, scissor.top, scissor.right, scissor.bottom)
        val color = when (role) {
            GPUDrawPacketRole.PathStencilProducer -> if (
                materialPlanTable == null
            ) {
                resolveMaterialColor(materialPlanTable, draw.materialAuthority)
                    ?: error("Historical W4d color authority is invalid")
            } else {
                ColorF32.Transparent
            }
            GPUDrawPacketRole.Shading, GPUDrawPacketRole.PathStencilCover ->
                resolveMaterialColor(materialPlanTable, draw.materialAuthority)
                    ?: error("W5 material authority is invalid for a color-writing path phase")
            else -> error("W4d emits only direct and path-stencil roles")
        }
        val blend = if (role != GPUDrawPacketRole.PathStencilProducer) W5bBlendPlanLowerer.lower(draw.blend)
            else canonicalSolidRectSrcOverBlendPlan()
        val semantic = GPUCorePrimitivePayloadGatherer().gatherPlannedW4dSemantic(
            GPUCorePrimitivePayloadInput(
                commandIdValue = draw.commandIndex,
                sourceFamily = GPUCorePrimitiveSourceFamily.Path,
                geometry = geometryInput(geometry, plannedScissor, draw.strategy),
                premultipliedRgba = listOf(color.red, color.green, color.blue, color.alpha),
                material = if (role == GPUDrawPacketRole.PathStencilProducer) null else
                    W5aMaterialPlanLowerer().material(materialPlanTable, draw.materialAuthority, draw.commandIndex),
                targetBounds = targetBounds,
                scissorBounds = plannedScissor,
                clipCoveragePlan = clipCoverage,
                clipExecutionPlanIdentity = clipExecution.canonicalIdentity(),
                blendPlanIdentity = blend.canonicalIdentity(),
                frameProvenance = GPUFrameProvenance.None,
                coverageMode = coverageMode,
            ),
        )
        val structuralKey = when (role) {
            GPUDrawPacketRole.Shading -> corePrimitiveRenderPipelineStructuralKey(
                semantic,
                clipExecution,
                blend,
                sampleCount = 1,
                colorFormat = GPUColorFormat.RGBA8UnormSrgb.corePrimitiveStructuralColorFormat(),
            )
            GPUDrawPacketRole.PathStencilProducer -> corePrimitivePathStencilRenderPipelineStructuralKey(
                semantic,
                GPUCorePrimitiveRenderPipelineStructuralKey.Role.PathStencilProducer,
                clipExecution,
                blend,
                sampleCount = 1,
                colorFormat = GPUColorFormat.RGBA8UnormSrgb.corePrimitiveStructuralColorFormat(),
            )
            GPUDrawPacketRole.PathStencilCover -> corePrimitivePathStencilRenderPipelineStructuralKey(
                semantic,
                GPUCorePrimitiveRenderPipelineStructuralKey.Role.PathStencilCover,
                clipExecution,
                blend,
                sampleCount = 1,
                colorFormat = GPUColorFormat.RGBA8UnormSrgb.corePrimitiveStructuralColorFormat(),
            )
            else -> error("W4d emits only direct and path-stencil roles")
        }
        val roleLabel = when (role) {
            GPUDrawPacketRole.Shading -> "direct"
            GPUDrawPacketRole.PathStencilProducer -> "producer"
            GPUDrawPacketRole.PathStencilCover -> "cover"
        }
        val analysisRecordId = "analysis.w4d_path_draw.${draw.commandIndex}"
        return W4dBuiltPass(
            packet = GPUDrawPacket(
                packetId = GPUDrawPacketID("packet.w4d.${draw.commandIndex}.$roleLabel"),
                commandIdValue = draw.commandIndex,
                analysisRecordId = analysisRecordId,
                passId = passId,
                layerId = "root",
                bindingListId = "binding.w4d.${draw.commandIndex}.$roleLabel",
                insertionReasonCode = "w4d-path-draw-$roleLabel",
                sortKey = paintOrder.toLong(),
                sortKeyPreimage = "paint-order:$paintOrder",
                renderStepId = GPURenderStepID(CORE_PRIMITIVE_RENDER_STEP_IDENTITY),
                renderStepVersion = 1,
                role = role,
                blendPlan = blend,
                renderPipelineKey = structuralKey.stableRenderPipelineKey(CORE_PRIMITIVE_RENDER_PIPELINE_KEY),
                bindingLayoutHash = CORE_PRIMITIVE_BINDING_LAYOUT_HASH,
                uniformSlot = semantic.payloadRef.uniformSlot,
                semanticPayload = semantic,
                vertexSourceLabel = CORE_PRIMITIVE_VERTEX_SOURCE_LABEL,
                scissorBoundsHash = corePrimitiveScissorAuthority(plannedScissor),
                targetStateHash = corePrimitiveTargetStateHash(1, GPUColorFormat.RGBA8UnormSrgb),
                originalPaintOrder = paintOrder,
                resourceGeneration = PREPARED_FRAME_LATE_BOUND_RESOURCE_GENERATION,
                frameProvenance = GPUFrameProvenance.None,
                clipCoveragePlan = clipCoverage,
                clipExecutionPlan = clipExecution,
            ),
            structuralPipelineKey = structuralKey,
        )
    }

    private fun geometryInput(
        geometry: PathFillGeometryF32,
        scissor: GPUPixelBounds,
        strategy: PathFillStrategy,
    ): GPUCorePrimitiveGeometryInput.TriangulatedPath = when (strategy) {
        PathFillStrategy.DirectTriangle -> {
            val direct = requireNotNull(geometry.copyDirectTriangleF32OrNull())
            GPUCorePrimitiveGeometryInput.TriangulatedPath(
                vertices = direct.copyVerticesF32().toList(),
                indices = direct.copyIndicesI32().toList(),
                sourceContourStarts = listOf(0),
                sourceVertexCount = direct.vertexCountI32,
                coverBounds = scissor,
                geometryMode = GPUCorePrimitiveGeometryMode.DirectTriangles,
                fillRule = GPUCorePrimitiveFillRule.Winding,
                inverseFill = false,
                sourceAuthority = GPUPathSourceAuthority.W4dPlannedPathStrokeV1,
            )
        }
        PathFillStrategy.StencilCover -> {
            val fan = requireNotNull(geometry.copyStencilEdgeFanF32OrNull())
            GPUCorePrimitiveGeometryInput.TriangulatedPath(
                vertices = fan.copyVerticesF32().toList(),
                indices = fan.copyIndicesI32().toList(),
                sourceContourStarts = fan.copyContourStartsI32().toList(),
                sourceVertexCount = fan.edgeCountI32,
                coverBounds = scissor,
                geometryMode = GPUCorePrimitiveGeometryMode.StencilEdgeFan,
                fillRule = when (geometry.fillRule) {
                    FillRule.WINDING -> GPUCorePrimitiveFillRule.Winding
                    FillRule.EVEN_ODD -> GPUCorePrimitiveFillRule.EvenOdd
                    else -> error("W4d graph retained an inverse fill")
                },
                inverseFill = false,
                sourceAuthority = GPUPathSourceAuthority.W4dPlannedPathStrokeV1,
            )
        }
    }

    private fun clipFor(draw: PathDraw, target: GPUPixelBounds): W4dClip {
        val scissor = draw.copyScissorI32()
        val bounds = GPUPixelBounds(scissor.left, scissor.top, scissor.right, scissor.bottom)
        return if (bounds == target) {
            W4dClip(GPUClipCoveragePlan.NoClip, GPUClipExecutionPlan.NoClip)
        } else {
            W4dClip(
                GPUClipCoveragePlan.Scissor(
                    GPUBounds(
                        bounds.left.toFloat(),
                        bounds.top.toFloat(),
                        bounds.right.toFloat(),
                        bounds.bottom.toFloat(),
                    ),
                ),
                GPUClipExecutionPlan.ScissorOnly(bounds),
            )
        }
    }

    internal fun sealScratch(
        graph: W4dGraph,
        builtPasses: List<W4dBuiltPass>,
        planId: String,
        capabilityId: String,
        target: GPUFrameTargetRef,
        staging: GPUFrameBufferRef,
        targetBounds: GPUPixelBounds,
        depthStencilResourceId: org.graphiks.kanvas.gpu.plan.PlanResourceId?,
        capabilitySealHash: String,
        deviceGeneration: Long,
        maxBufferSize: Long,
        maxDynamicUniformBuffers: Long,
        w5bGraph: RenderGraph? = null,
    ): W4dSessionScratchV1? {
        val materialV2 = W4dPathStrokePlanCompiler.isW5aMaterialCapabilityId(graph.capabilityId) || w5bGraph != null
        val payloads = graph.visualDraws.flatMap { visual ->
            val passCount = if (materialV2 && visual.draw.strategy == PathFillStrategy.StencilCover) 2 else 1
            (0 until passCount).map { passOffset ->
                val semantic = builtPasses.getOrNull(visual.firstPassIndex + passOffset)
                    ?.packet?.semanticPayload as? GPUDrawSemanticPayload.CorePrimitive ?: return null
                val bytes = semantic.payloadRef.uniformBlock?.bytes ?: return null
                if (bytes.size.toLong() != W4dSessionScratchV1.UNIFORM_PAYLOAD_BYTES) return null
                val label = if (!materialV2) {
                    "path-draw-${visual.draw.commandIndex}"
                } else if (passOffset == 0 && passCount == 2) {
                    "path-draw-producer-${visual.draw.commandIndex}"
                } else "path-draw-color-${visual.draw.commandIndex}"
                GPUUniformSlabPayload(label, bytes.map(Int::toByte).toByteArray())
            }
        }
        val uniformPlan = when (val planned = GPUUniformSlabPlanner.plan(
            sourceLabel = W4dSessionScratchV1.SOURCE_LABEL,
            deviceGeneration = deviceGeneration,
            alignmentBytes = graph.footprint.uniformStrideBytes,
            uploadBudgetBytes = graph.uniform.byteSize,
            payloads = payloads,
            maxBufferSize = maxBufferSize,
            maxDynamicUniformBuffersPerPipelineLayout = maxDynamicUniformBuffers,
        )) {
            is GPUUniformSlabPlanningResult.Accepted -> planned.plan
            is GPUUniformSlabPlanningResult.Refused -> return null
        }
        val poolCapacities = corePrimitiveFramePoolCapacitiesOrNull(
            graph.footprint.vertexUsefulBytes,
            graph.footprint.indexUsefulBytes,
            uniformPlan.totalBytes,
        ) ?: return null
        val draws = mutableListOf<W4dSessionScratchDrawV1>()
        var vertexOffset = 0L
        var indexOffset = 0L
        var uniformSlot = 0
        graph.visualDraws.forEachIndexed { visualIndex, visual ->
            val geometry = visual.draw.copyFillGeometryF32()
            val vertexRange = try { Math.multiplyExact(geometry.vertexCostI64, VERTEX_BYTES) } catch (_: ArithmeticException) { return null }
            val indexRange = try { Math.multiplyExact(geometry.indexCostI64, INDEX_BYTES) } catch (_: ArithmeticException) { return null }
            val scissor = visual.draw.copyScissorI32()
            draws += W4dSessionScratchDrawV1(
                commandId = visual.draw.commandIndex,
                strategy = visual.draw.strategy,
                pathGeometry = visual.draw.copyPathGeometry(),
                mode = (visual.draw as? PathStrokeDraw)?.mode,
                styleF64 = (visual.draw as? PathStrokeDraw)?.styleF64,
                scissorBounds = GPUPixelBounds(scissor.left, scissor.top, scissor.right, scissor.bottom),
                vertexOffsetBytes = vertexOffset,
                vertexRangeBytes = vertexRange,
                indexOffsetBytes = indexOffset,
                indexRangeBytes = indexRange,
                uniformSlotIndex = if (materialV2) {
                    uniformSlot + if (visual.draw.strategy == PathFillStrategy.StencilCover) 1 else 0
                } else {
                    visualIndex
                },
                producerUniformSlotIndex = if (materialV2 && visual.draw.strategy == PathFillStrategy.StencilCover) uniformSlot else null,
                atomicGroupId = visual.atomicGroupId,
            )
            vertexOffset = try { Math.addExact(vertexOffset, vertexRange) } catch (_: ArithmeticException) { return null }
            indexOffset = try { Math.addExact(indexOffset, indexRange) } catch (_: ArithmeticException) { return null }
            uniformSlot += if (materialV2 && visual.draw.strategy == PathFillStrategy.StencilCover) 2 else 1
        }
        return try {
            W4dSessionScratchV1(
                planId = planId,
                capabilitySealHash = capabilitySealHash,
                deviceGeneration = deviceGeneration,
                target = target,
                staging = staging,
                targetBounds = targetBounds,
                vertexResourceId = graph.vertex.id,
                indexResourceId = graph.index.id,
                uniformResourceId = graph.uniform.id,
                depthStencilResourceId = depthStencilResourceId,
                targetBytes = graph.target.byteSize,
                stagingBytes = graph.staging.byteSize,
                capabilityId = capabilityId,
                renderPassIds = graph.renderPasses.map(PlanPass::id),
                readbackPassId = graph.readback.id,
                resourceLastPassIndexExclusive = graph.renderPasses.size + 1,
                depthStencilFirstPassIndex = if (w5bGraph != null && graph.depthStencil != null)
                    graph.renderPasses.indexOfFirst { it is PlanPass.StencilGeometryProducerV3 } else graph.depthStencil?.firstPassIndex,
                draws = draws,
                uniformPlan = uniformPlan,
                uniformStrideBytes = graph.footprint.uniformStrideBytes,
                vertexUsefulBytes = graph.footprint.vertexUsefulBytes,
                indexUsefulBytes = graph.footprint.indexUsefulBytes,
                uniformUsefulBytes = graph.footprint.uniformUsefulBytes,
                vertexCapacityBytes = graph.vertex.byteSize,
                indexCapacityBytes = graph.index.byteSize,
                uniformCapacityBytes = graph.uniform.byteSize,
                depthStencilBytes = graph.footprint.depthStencilBytes,
                poolCapacities = poolCapacities,
                maxBufferSize = maxBufferSize,
                maxDynamicUniformBuffersPerPipelineLayout = maxDynamicUniformBuffers,
                w5bGraph = w5bGraph,
            )
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun renderTask(
        pass: PlanPass,
        packet: GPUDrawPacket,
        target: GPUFrameTargetRef,
        depthStencil: GPUFrameTextureRef?,
        recordingId: org.graphiks.kanvas.gpu.renderer.recording.GPURecordingID,
        planId: String,
    ): GPUTask.Render? {
        val (loadStore, resourceUses, depthLoadStore) = when (pass) {
            is PlanPass.RenderPass -> Triple(loadStore(pass.load), emptyList(), null)
            is PlanPass.StencilProducer -> Triple(
                loadStore(pass.load),
                listOf(requireNotNull(depthStencil).pathDepthStencilUse()),
                GPUDepthStencilLoadStorePlan.WritableStencil(
                    GPUStencilLoadOperation.Clear,
                    GPUStorePlan.Store,
                    0u,
                ),
            )
            is PlanPass.StencilCover -> Triple(
                loadStore(pass.load),
                listOf(requireNotNull(depthStencil).pathDepthStencilUse()),
                GPUDepthStencilLoadStorePlan.WritableStencil(
                    GPUStencilLoadOperation.Load,
                    GPUStorePlan.Store,
                    null,
                ),
            )
            else -> return null
        }
        return GPUTask.Render(
            taskId = GPUTaskID("task.w4d.$planId.base.${pass.id.value}"),
            recordingId = recordingId,
            phase = GPUTaskPhase.Render,
            target = target,
            loadStore = loadStore,
            samplePlan = GPUSamplePlan.SingleSampleFrame,
            resourceUses = resourceUses,
            drawPackets = listOf(packet),
            batchEligibilityByPacketId = mapOf(
                packet.packetId to GPUPassBatchEligibility(
                    kind = GPUPassBatchKind.SolidFill,
                    queueGuard = GPUPassBatchQueueGuard(emptyList(), emptyList()),
                ),
            ),
            depthStencilLoadStore = depthLoadStore,
        )
    }

    private fun GPUFrameTextureRef.pathDepthStencilUse(): GPUFrameResourceUse = GPUFrameResourceUse(
        this,
        GPUFrameResourceRole.PathDepthStencil,
        GPUFrameResourceUsage.RenderAttachment,
        GPUFrameResourceLifetime.FrameLocal,
        write = true,
    )

    private fun loadStore(load: AttachmentLoadPlan): GPULoadStorePlan = GPULoadStorePlan(
        when (load) {
            AttachmentLoadPlan.ClearTransparent -> "clear"
            AttachmentLoadPlan.Load -> "load"
        },
        GPUStorePlan.Store,
    )

    private fun memoryBudget(
        graph: RenderGraph,
        shape: W4dGraph,
        bounds: GPUPixelBounds,
        generation: GPUDeviceGenerationID,
        deviceLimitFacts: List<GPUCapabilityFact>,
        compositeSessionIdentity: String? = null,
    ): GPUFrameMemoryBudgetPlan? {
        val transient = try {
            listOf(
                shape.staging.byteSize,
                shape.vertex.byteSize,
                shape.index.byteSize,
                shape.uniform.byteSize,
                shape.footprint.depthStencilBytes,
            ).fold(0L, Math::addExact)
        } catch (_: ArithmeticException) {
            return null
        }
        val peak = try { Math.addExact(shape.target.byteSize, transient) } catch (_: ArithmeticException) { return null }
        if (peak != graph.peakFrameLocalBytes || peak > graph.budget.maxFrameLocalBytes) return null
        val identity = compositeSessionIdentity ?: w4dSessionIdentity(generation, bounds)
        val allocations = buildList {
            add(GPUFrameMemoryAllocation("$identity.target", GPUFrameMemoryCategory.CanonicalTarget, shape.target.byteSize, GPUFrameMemoryResourceKind.Texture2D, bounds))
            add(GPUFrameMemoryAllocation("$identity.staging", GPUFrameMemoryCategory.ReadbackStaging, shape.staging.byteSize, GPUFrameMemoryResourceKind.Buffer, null))
            add(GPUFrameMemoryAllocation("$identity.vertex", GPUFrameMemoryCategory.ReusableScratch, shape.vertex.byteSize, GPUFrameMemoryResourceKind.Buffer, null))
            add(GPUFrameMemoryAllocation("$identity.index", GPUFrameMemoryCategory.ReusableScratch, shape.index.byteSize, GPUFrameMemoryResourceKind.Buffer, null))
            add(GPUFrameMemoryAllocation("$identity.uniform", GPUFrameMemoryCategory.ReusableScratch, shape.uniform.byteSize, GPUFrameMemoryResourceKind.Buffer, null))
            if (shape.depthStencil != null) {
                add(GPUFrameMemoryAllocation("$identity.depth-stencil", GPUFrameMemoryCategory.ReusableScratch, shape.footprint.depthStencilBytes, GPUFrameMemoryResourceKind.Texture2D, bounds))
            }
        }
        return GPUFrameMemoryBudgetPlan(
            peakFrameTransientBytes = transient,
            targetResidentBytes = shape.target.byteSize,
            categoryTotals = GPUFrameMemoryCategory.entries.associateWith { category ->
                allocations.filter { allocation -> allocation.category == category }.sumOf(GPUFrameMemoryAllocation::bytes)
            },
            deviceLimitFacts = deviceLimitFacts,
            configuredAggregateBudgetBytes = graph.budget.maxFrameLocalBytes,
            diagnostic = null,
            allocations = allocations,
        )
    }

    private fun w4dSessionIdentity(generation: GPUDeviceGenerationID, bounds: GPUPixelBounds): String =
        "w4d.session.${generation.value}.${bounds.width}x${bounds.height}.rgba8unorm-srgb"

    private fun Int.isPositivePowerOfTwo(): Boolean = this > 0 && this and (this - 1) == 0

    private fun Long.isPositivePowerOfTwo(): Boolean = this > 0L && this and (this - 1L) == 0L

    internal data class W4dGraph(
        val capabilityId: String,
        val target: PlanResource,
        val staging: PlanResource,
        val vertex: PlanResource,
        val index: PlanResource,
        val uniform: PlanResource,
        val depthStencil: PlanResource?,
        val renderPasses: List<PlanPass>,
        val readback: PlanPass.ReadbackPass,
        val visualDraws: List<W4dVisualDraw>,
        val footprint: PathFillMemoryFootprint,
        val materialPlanTable: MaterialPlanTable?,
    )

    private fun resolveMaterialColor(
        table: MaterialPlanTable?,
        authority: PlanDrawMaterialAuthority,
    ): ColorF32? = when (authority) {
        is PlanDrawMaterialAuthority.LegacyColorV1 -> authority.copyColorF32()
        is PlanDrawMaterialAuthority.MaterialV3 -> error(org.graphiks.kanvas.gpu.plan.W5eImagePlanDiagnostics.InvalidContract)
        is PlanDrawMaterialAuthority.MaterialV2 -> table?.let { W5aMaterialPlanLowerer().lower(it, authority.ref) }
        is PlanDrawMaterialAuthority.MaterialV1 -> table?.let { W5aMaterialPlanLowerer().lower(it, authority.ref) }
    }

    internal data class W4dVisualDraw(
        val draw: PathDraw,
        val firstPassIndex: Int,
        val atomicGroupId: String?,
    )

    internal data class W4dBuiltPass(
        val packet: GPUDrawPacket,
        val structuralPipelineKey: GPUCorePrimitiveRenderPipelineStructuralKey,
    )

    private data class W4dClip(
        val coverage: GPUClipCoveragePlan,
        val execution: GPUClipExecutionPlan,
    )

    private fun invalid(message: String): GpuPlanLoweringResult.InvalidPlan =
        GpuPlanLoweringResult.InvalidPlan(diagnostic("w4d.lowering.incompatible_plan", RenderDiagnosticDomain.RESOURCE, message))

    private fun capability(code: RenderDiagnosticCode, message: String): GpuPlanLoweringResult.UnsupportedCapability =
        GpuPlanLoweringResult.UnsupportedCapability(diagnostic(code.value, RenderDiagnosticDomain.CAPABILITY, message))

    private fun diagnostic(code: String, domain: RenderDiagnosticDomain, message: String): RenderDiagnostic =
        RenderDiagnostic(RenderDiagnosticCode(code), domain, RenderDiagnosticSeverity.ERROR, message)

    private companion object {
        val W4D_REQUIRED_OPERATIONS: Set<PlanOperationCapability> = setOf(
            PlanOperationCapability.RenderPass,
            PlanOperationCapability.CopyUpload,
            PlanOperationCapability.UniformBuffer,
            PlanOperationCapability.Readback,
        )
        const val VERTEX_BYTES: Long = 8L
        const val INDEX_BYTES: Long = 4L
    }
}

private fun PathDraw.copyFillGeometryF32(): PathFillGeometryF32 = when (val geometry = copyPathGeometry()) {
    is PathDrawGeometry.Fill -> geometry.valueF32
    is PathDrawGeometry.Stroke -> geometry.valueF32.copyFillGeometryF32()
    is PathDrawGeometry.InverseDomainSource -> error("W4d path stroke lowering cannot consume W4e inverse-domain source geometry")
    PathDrawGeometry.Empty -> error("W4d path stroke lowering cannot consume W4e inverse-domain empty geometry")
}

private fun PathDraw.expectedAtomicGroupId(): String = when (this) {
    is org.graphiks.kanvas.gpu.plan.W5bW4ePathDraw -> error("W4e geometry requires its own native authority")
    is org.graphiks.kanvas.gpu.plan.GeneralPathDraw -> error("General geometry requires its own native authority")
    is PathFillDraw -> "w4c:$commandIndex"
    is PathStrokeDraw -> "w4d:$commandIndex"
}
