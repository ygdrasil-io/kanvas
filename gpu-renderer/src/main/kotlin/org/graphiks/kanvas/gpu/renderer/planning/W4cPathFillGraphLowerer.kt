package org.graphiks.kanvas.gpu.renderer.planning

import org.graphiks.kanvas.gpu.plan.AttachmentLoadPlan
import org.graphiks.kanvas.gpu.plan.AttachmentStorePlan
import org.graphiks.kanvas.gpu.plan.MaterialPlanTable
import org.graphiks.kanvas.gpu.plan.PathFillDraw
import org.graphiks.kanvas.gpu.plan.PathFillMemoryFootprint
import org.graphiks.kanvas.gpu.plan.PathFillPlanBudget
import org.graphiks.kanvas.gpu.plan.PathFillPlanBudgetResult
import org.graphiks.kanvas.gpu.plan.PathFillStrategy
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
import org.graphiks.kanvas.gpu.plan.W4cPathFillPlanCompiler
import org.graphiks.kanvas.gpu.plan.W4cPlanDiagnostics
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
import org.graphiks.kanvas.gpu.renderer.passes.W4cSessionScratchDrawV1
import org.graphiks.kanvas.gpu.renderer.passes.W4cSessionScratchV1
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
import org.graphiks.kanvas.gpu.renderer.recording.GPUCorePrimitiveW4cPreparedFrameRequest
import org.graphiks.kanvas.gpu.renderer.recording.GPUCorePrimitiveW4cPreparedFrameTaskListAssembler
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
import org.graphiks.math.color.ColorF32
import org.graphiks.math.geometry.PathFillGeometryF32

/** Lowers only the authenticated W4c path-fill graph; it never re-enters Scene IR or legacy tessellation. */
internal class W4cPathFillGraphLowerer {
    fun lower(request: GpuPlanLoweringRequest): GpuPlanLoweringResult = try {
        if (!(W4cPathFillPlanCompiler.isHistoricalCapabilityId(request.graph.capabilityId) ||
                W4cPathFillPlanCompiler.isW5aMaterialCapabilityId(request.graph.capabilityId))) {
            return invalid("The graph is not a W4c path-fill graph.")
        }
        val current = when (val adapted = request.capabilities.toPlanCapabilitySnapshot(request.deviceGeneration)) {
            is GpuPlanCapabilityAdapterResult.Supported -> adapted.snapshot
            is GpuPlanCapabilityAdapterResult.Unsupported -> return GpuPlanLoweringResult.UnsupportedCapability(adapted.diagnostic)
        }
        if (request.graph.capabilities != current) return capability(
            W4cPlanDiagnostics.CapabilityOperation,
            "The W4c graph capability snapshot is stale.",
        )
        if (!hasExactW4cCapabilityFacts(request.graph)) return capability(
            missingW4cCapabilityFact(request.graph),
            "The W4c graph lacks required depth-stencil capability facts.",
        )
        if (request.graph.budget != request.currentBudget) return invalid("The W4c graph budget is stale.")
        val graph = validateW4cGraph(request.graph) ?: return invalid("The graph is not the exact W4c topology.")
        val limits = request.capabilities.limits
            ?: return capability(W4cPlanDiagnostics.CapabilityBufferSize, "W4c lowering requires observed renderer limits.")
        val maxBufferSize = limits.maxBufferSize
            ?: return capability(W4cPlanDiagnostics.CapabilityBufferSize, "W4c lowering requires an observed maxBufferSize.")
        val maxDynamicUniformBuffers = limits.maxDynamicUniformBuffersPerPipelineLayout
            ?.takeIf { it >= 1L }
            ?: return capability(
                W4cPlanDiagnostics.CapabilityDynamicUniform,
                "W4c lowering requires one dynamic uniform buffer binding.",
            )
        val targetBounds = GPUPixelBounds(0, 0, request.graph.targetExtent.width, request.graph.targetExtent.height)
        val sessionIdentity = w4cSessionIdentity(request.deviceGeneration, targetBounds)
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
        ) ?: return invalid("The W4c graph memory facts cannot be represented by the renderer.")

        val builtPasses = graph.renderPasses.map { pass ->
            builtPass(pass, graph.visualDraws, graph.materialPlanTable, targetBounds)
        }
        val capabilitySeal = GPUFrameCapabilitySeal.capture(request.frameId, request.deviceGeneration, request.capabilities)
        val scratch = sealScratch(
            graph = graph,
            builtPasses = builtPasses,
            planId = request.graph.id.value,
            target = target,
            staging = staging,
            targetBounds = targetBounds,
            depthStencilResourceId = graph.depthStencil?.id,
            capabilitySealHash = capabilitySeal.sealHash,
            deviceGeneration = request.deviceGeneration.value,
            maxBufferSize = maxBufferSize,
            maxDynamicUniformBuffers = maxDynamicUniformBuffers,
        ) ?: return invalid("W4c scratch packing is invalid.")
        builtPasses.forEach { built ->
            val publicPipelineKey = built.packet.renderPipelineKey ?: return invalid("W4c packet lacks a public pipeline key.")
            built.packet.attachCorePrimitivePreparedAuthority(
                GPUCorePrimitivePreparedPacketAuthority.plannedW4c(
                    packet = built.packet,
                    structuralPipelineKey = built.structuralPipelineKey,
                    renderPipelineKey = publicPipelineKey,
                    planId = request.graph.id.value,
                    capabilitySealHash = capabilitySeal.sealHash,
                    scratch = scratch,
                ),
            )
        }
        val replay = "w4c:${request.graph.id.value}"
        val renders = graph.renderPasses.zip(builtPasses).map { (pass, built) ->
            renderTask(
                pass = pass,
                packet = built.packet,
                target = target,
                depthStencil = depthStencil,
                recordingId = request.recordingId,
                planId = request.graph.id.value,
            ) ?: return invalid("W4c plan pass cannot be represented by a render task.")
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
            GPUReadbackRequestID("w4c.${request.graph.id.value}.readback"),
            targetBounds,
            GPUReadbackPixelFormat.Rgba8Unorm,
            GPUColorInterpretation.EncodedPremulSrgb,
        )
        when (val assembled = GPUCorePrimitiveW4cPreparedFrameTaskListAssembler().buildPreplanned(
            GPUCorePrimitiveW4cPreparedFrameRequest(
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
            ),
        )) {
            is GPUCorePrimitivePreparedFrameResult.Recorded ->
                GpuPlanLoweringResult.Lowered(assembled.taskList, readback.requestId.value)
            is GPUCorePrimitivePreparedFrameResult.Refused -> invalid(assembled.diagnostic.message)
        }
    } catch (error: IllegalArgumentException) {
        invalid(error.message ?: "The graph cannot be lowered into W4c renderer values.")
    }

    private fun validateW4cGraph(graph: RenderGraph): W4cGraph? {
        if (!(W4cPathFillPlanCompiler.isHistoricalCapabilityId(graph.capabilityId) ||
                W4cPathFillPlanCompiler.isW5aMaterialCapabilityId(graph.capabilityId)) ||
            graph.colorFormat != PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL ||
            !hasExactW4cCapabilityFacts(graph)
        ) return null
        val passes = graph.passes()
        val readback = passes.lastOrNull() as? PlanPass.ReadbackPass ?: return null
        val renderPasses = passes.dropLast(1)
        if (renderPasses.isEmpty() || renderPasses.any { pass ->
                pass !is PlanPass.RenderPass && pass !is PlanPass.StencilProducer && pass !is PlanPass.StencilCover
            }
        ) return null
        val visual = visualDraws(renderPasses) ?: return null
        if (visual.size !in 1..512 || graph.visualCommandCount != visual.size ||
            visual.zipWithNext().any { (first, second) -> first.draw.commandIndex >= second.draw.commandIndex }
        ) return null
        val materialPlanTable = graph.materialPlanTableOrNull()
        if (W4cPathFillPlanCompiler.isW5aMaterialCapabilityId(graph.capabilityId)) {
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
        val footprint = when (val result = PathFillPlanBudget.calculate(
            graph.targetExtent,
            visual.map { visualDraw -> visualDraw.draw.copyGeometryF32() },
            graph.capabilities,
            graph.budget,
        )) {
            is PathFillPlanBudgetResult.WithinBudget -> result.footprint
            is PathFillPlanBudgetResult.Exceeded,
            is PathFillPlanBudgetResult.Invalid,
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
        return W4cGraph(
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

    private fun hasExactW4cCapabilityFacts(graph: RenderGraph): Boolean {
        val capabilities = graph.capabilities
        val policy = capabilities.bufferAllocationPolicy
        return graph.colorFormat in capabilities.supportedFormats() &&
            capabilities.maxDynamicUniformBuffersPerPipelineLayout >= 1 &&
            W4C_REQUIRED_OPERATIONS.all { it in capabilities.supportedOperations() } &&
            PlanDepthStencilFormat.Depth24PlusStencil8 in capabilities.supportedDepthStencilFormats() &&
            capabilities.copyBytesPerRowAlignment.isPositivePowerOfTwo() &&
            capabilities.minUniformBufferOffsetAlignment.isPositivePowerOfTwo() &&
            policy.growth == PlanBufferGrowth.PowerOfTwo &&
            policy.vertexFloorBytes.isPositivePowerOfTwo() &&
            policy.indexFloorBytes.isPositivePowerOfTwo() &&
            policy.uniformFloorBytes.isPositivePowerOfTwo()
    }

    private fun missingW4cCapabilityFact(graph: RenderGraph): RenderDiagnosticCode =
        if (PlanDepthStencilFormat.Depth24PlusStencil8 !in graph.capabilities.supportedDepthStencilFormats()) {
            W4cPlanDiagnostics.CapabilityDepthStencilFormat
        } else {
            W4cPlanDiagnostics.CapabilityOperation
        }

    private fun visualDraws(renderPasses: List<PlanPass>): List<W4cVisualDraw>? {
        val visual = mutableListOf<W4cVisualDraw>()
        var passIndex = 0
        var renderOrdinal = 0
        var producerOrdinal = 0
        var coverOrdinal = 0
        var firstColor = true
        while (passIndex < renderPasses.size) {
            when (val pass = renderPasses[passIndex]) {
                is PlanPass.RenderPass -> {
                    val draw = pass.draws().singleOrNull() as? PathFillDraw ?: return null
                    if (pass.ordinal != renderOrdinal++ || draw.strategy != PathFillStrategy.DirectTriangle ||
                        pass.load != (if (firstColor) AttachmentLoadPlan.ClearTransparent else AttachmentLoadPlan.Load) ||
                        pass.store != AttachmentStorePlan.Store || !isExactPathFillDraw(draw)
                    ) return null
                    visual += W4cVisualDraw(draw, passIndex, null)
                    firstColor = false
                    passIndex += 1
                }
                is PlanPass.StencilProducer -> {
                    val cover = renderPasses.getOrNull(passIndex + 1) as? PlanPass.StencilCover ?: return null
                    val draw = pass.draw as? PathFillDraw ?: return null
                    if (pass.ordinal != producerOrdinal++ || cover.ordinal != coverOrdinal++ ||
                        draw !== cover.draw || draw.strategy != PathFillStrategy.StencilCover ||
                        pass.load != (if (firstColor) AttachmentLoadPlan.ClearTransparent else AttachmentLoadPlan.Load) ||
                        pass.store != AttachmentStorePlan.Store || cover.load != AttachmentLoadPlan.Load ||
                        cover.store != AttachmentStorePlan.Store ||
                        pass.depthStencilAccess != PlanDepthStencilAccess.Write ||
                        cover.depthStencilAccess != PlanDepthStencilAccess.ReadWrite ||
                        pass.depthStencilLoadStore != PlanDepthStencilLoadStore.ClearZeroStore ||
                        cover.depthStencilLoadStore != PlanDepthStencilLoadStore.LoadStoreTestReset ||
                        pass.atomicGroup != cover.atomicGroup || pass.atomicGroup.value != "w4c:${draw.commandIndex}" ||
                        !isExactPathFillDraw(draw)
                    ) return null
                    visual += W4cVisualDraw(draw, passIndex, pass.atomicGroup.value)
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
        visual: List<W4cVisualDraw>,
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

    private fun isExactPathFillDraw(draw: PathFillDraw): Boolean {
        val geometry = draw.copyGeometryF32()
        val scissor = draw.copyScissorI32()
        val direct = geometry.copyDirectTriangleF32OrNull()
        val stencil = geometry.copyStencilEdgeFanF32OrNull()
        return draw.commandIndex >= 0 && !scissor.isEmpty64() &&
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
        visualDraws: List<W4cVisualDraw>,
        materialPlanTable: MaterialPlanTable?,
        targetBounds: GPUPixelBounds,
    ): W4cBuiltPass = when (pass) {
        is PlanPass.RenderPass -> {
            val draw = pass.draws().singleOrNull() as? PathFillDraw
                ?: error("Validated W4c graph contains a non-fill direct draw")
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
            val draw = pass.draw as? PathFillDraw
                ?: error("Validated W4c graph contains a non-fill stencil producer")
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
            val draw = pass.draw as? PathFillDraw
                ?: error("Validated W4c graph contains a non-fill stencil cover")
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
        else -> error("Validated W4c graph contains an unsupported pass")
    }

    private fun paintOrder(draw: PathFillDraw, visualDraws: List<W4cVisualDraw>): Int =
        visualDraws.indexOfFirst { visual -> visual.draw === draw }
            .takeIf { index -> index >= 0 }
            ?: error("Validated W4c pass has no visual draw order")

    private fun packet(
        draw: PathFillDraw,
        paintOrder: Int,
        passId: String,
        role: GPUDrawPacketRole,
        coverageMode: GPUCorePrimitiveCoverageMode,
        clipCoverage: GPUClipCoveragePlan,
        clipExecution: GPUClipExecutionPlan,
        materialPlanTable: MaterialPlanTable?,
        targetBounds: GPUPixelBounds,
    ): W4cBuiltPass {
        val geometry = draw.copyGeometryF32()
        val scissor = draw.copyScissorI32()
        val plannedScissor = GPUPixelBounds(scissor.left, scissor.top, scissor.right, scissor.bottom)
        val color = when (role) {
            // The stencil producer does not write color.  Keep its ABI payload strictly
            // geometry/coverage-derived: it must never force or observe the W5 material.
            GPUDrawPacketRole.PathStencilProducer -> ColorF32.Transparent
            GPUDrawPacketRole.Shading, GPUDrawPacketRole.PathStencilCover -> resolveMaterialColor(materialPlanTable, draw.materialAuthority)
                ?: error("W5 material authority is invalid for a color-writing path phase")
            else -> error("W4c emits only direct and path-stencil roles")
        }
        val semantic = GPUCorePrimitivePayloadGatherer().gatherPlannedW4cSemantic(
            GPUCorePrimitivePayloadInput(
                commandIdValue = draw.commandIndex,
                sourceFamily = GPUCorePrimitiveSourceFamily.Path,
                geometry = geometryInput(geometry, plannedScissor, draw.strategy),
                premultipliedRgba = listOf(color.red, color.green, color.blue, color.alpha),
                targetBounds = targetBounds,
                scissorBounds = plannedScissor,
                clipCoveragePlan = clipCoverage,
                clipExecutionPlanIdentity = clipExecution.canonicalIdentity(),
                blendPlanIdentity = canonicalSolidRectSrcOverBlendPlan().canonicalIdentity(),
                frameProvenance = GPUFrameProvenance.None,
                coverageMode = coverageMode,
            ),
        )
        val blend = canonicalSolidRectSrcOverBlendPlan()
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
            else -> error("W4c emits only direct and path-stencil roles")
        }
        val roleLabel = when (role) {
            GPUDrawPacketRole.Shading -> "direct"
            GPUDrawPacketRole.PathStencilProducer -> "producer"
            GPUDrawPacketRole.PathStencilCover -> "cover"
        }
        val analysisRecordId = "analysis.w4c_path_fill.${draw.commandIndex}"
        return W4cBuiltPass(
            packet = GPUDrawPacket(
                packetId = GPUDrawPacketID("packet.w4c.${draw.commandIndex}.$roleLabel"),
                commandIdValue = draw.commandIndex,
                analysisRecordId = analysisRecordId,
                passId = passId,
                layerId = "root",
                bindingListId = "binding.w4c.${draw.commandIndex}.$roleLabel",
                insertionReasonCode = "w4c-path-fill-$roleLabel",
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
                sourceAuthority = GPUPathSourceAuthority.W4cPlannedPathFillV1,
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
                    else -> error("W4c graph retained an inverse fill")
                },
                inverseFill = false,
                sourceAuthority = GPUPathSourceAuthority.W4cPlannedPathFillV1,
            )
        }
    }

    private fun clipFor(draw: PathFillDraw, target: GPUPixelBounds): W4cClip {
        val scissor = draw.copyScissorI32()
        val bounds = GPUPixelBounds(scissor.left, scissor.top, scissor.right, scissor.bottom)
        return if (bounds == target) {
            W4cClip(GPUClipCoveragePlan.NoClip, GPUClipExecutionPlan.NoClip)
        } else {
            W4cClip(
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

    private fun sealScratch(
        graph: W4cGraph,
        builtPasses: List<W4cBuiltPass>,
        planId: String,
        target: GPUFrameTargetRef,
        staging: GPUFrameBufferRef,
        targetBounds: GPUPixelBounds,
        depthStencilResourceId: org.graphiks.kanvas.gpu.plan.PlanResourceId?,
        capabilitySealHash: String,
        deviceGeneration: Long,
        maxBufferSize: Long,
        maxDynamicUniformBuffers: Long,
    ): W4cSessionScratchV1? {
        val payloads = graph.visualDraws.flatMap { visual ->
            val passCount = if (visual.draw.strategy == PathFillStrategy.StencilCover) 2 else 1
            (0 until passCount).map { passOffset ->
                val semantic = builtPasses.getOrNull(visual.firstPassIndex + passOffset)
                    ?.packet?.semanticPayload as? GPUDrawSemanticPayload.CorePrimitive ?: return null
                val label = if (passOffset == 0 && passCount == 2) {
                    "path-fill-producer-${visual.draw.commandIndex}"
                } else {
                    "path-fill-color-${visual.draw.commandIndex}"
                }
                val bytes = semantic.payloadRef.uniformBlock?.bytes ?: return null
                if (bytes.size.toLong() != W4cSessionScratchV1.UNIFORM_PAYLOAD_BYTES) return null
                GPUUniformSlabPayload(label, bytes.map(Int::toByte).toByteArray())
            }
        }
        val uniformPlan = when (val planned = GPUUniformSlabPlanner.plan(
            sourceLabel = W4cSessionScratchV1.SOURCE_LABEL,
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
        val draws = mutableListOf<W4cSessionScratchDrawV1>()
        var vertexOffset = 0L
        var indexOffset = 0L
        var uniformSlot = 0
        graph.visualDraws.forEach { visual ->
            val geometry = visual.draw.copyGeometryF32()
            val vertexRange = try { Math.multiplyExact(geometry.vertexCostI64, VERTEX_BYTES) } catch (_: ArithmeticException) { return null }
            val indexRange = try { Math.multiplyExact(geometry.indexCostI64, INDEX_BYTES) } catch (_: ArithmeticException) { return null }
            val scissor = visual.draw.copyScissorI32()
            draws += W4cSessionScratchDrawV1(
                commandId = visual.draw.commandIndex,
                strategy = visual.draw.strategy,
                geometryF32 = geometry,
                scissorBounds = GPUPixelBounds(scissor.left, scissor.top, scissor.right, scissor.bottom),
                vertexOffsetBytes = vertexOffset,
                vertexRangeBytes = vertexRange,
                indexOffsetBytes = indexOffset,
                indexRangeBytes = indexRange,
                uniformSlotIndex = uniformSlot + if (visual.draw.strategy == PathFillStrategy.StencilCover) 1 else 0,
                producerUniformSlotIndex = if (visual.draw.strategy == PathFillStrategy.StencilCover) uniformSlot else null,
                atomicGroupId = visual.atomicGroupId,
            )
            vertexOffset = try { Math.addExact(vertexOffset, vertexRange) } catch (_: ArithmeticException) { return null }
            indexOffset = try { Math.addExact(indexOffset, indexRange) } catch (_: ArithmeticException) { return null }
            uniformSlot += if (visual.draw.strategy == PathFillStrategy.StencilCover) 2 else 1
        }
        return try {
            W4cSessionScratchV1(
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
            taskId = GPUTaskID("task.w4c.$planId.base.${pass.id.value}"),
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
        shape: W4cGraph,
        bounds: GPUPixelBounds,
        generation: GPUDeviceGenerationID,
        deviceLimitFacts: List<GPUCapabilityFact>,
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
        val identity = w4cSessionIdentity(generation, bounds)
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

    private fun w4cSessionIdentity(generation: GPUDeviceGenerationID, bounds: GPUPixelBounds): String =
        "w4c.session.${generation.value}.${bounds.width}x${bounds.height}.rgba8unorm-srgb"

    private fun Int.isPositivePowerOfTwo(): Boolean = this > 0 && this and (this - 1) == 0

    private fun Long.isPositivePowerOfTwo(): Boolean = this > 0L && this and (this - 1L) == 0L

    private data class W4cGraph(
        val target: PlanResource,
        val staging: PlanResource,
        val vertex: PlanResource,
        val index: PlanResource,
        val uniform: PlanResource,
        val depthStencil: PlanResource?,
        val renderPasses: List<PlanPass>,
        val readback: PlanPass.ReadbackPass,
        val visualDraws: List<W4cVisualDraw>,
        val footprint: PathFillMemoryFootprint,
        val materialPlanTable: MaterialPlanTable?,
    )

    private fun resolveMaterialColor(
        table: MaterialPlanTable?,
        authority: PlanDrawMaterialAuthority,
    ): ColorF32? = when (authority) {
        is PlanDrawMaterialAuthority.LegacyColorV1 -> authority.copyColorF32()
        is PlanDrawMaterialAuthority.MaterialV1 -> table?.let { W5aMaterialPlanLowerer().lower(it, authority.ref) }
    }

    private data class W4cVisualDraw(
        val draw: PathFillDraw,
        val firstPassIndex: Int,
        val atomicGroupId: String?,
    )

    private data class W4cBuiltPass(
        val packet: GPUDrawPacket,
        val structuralPipelineKey: GPUCorePrimitiveRenderPipelineStructuralKey,
    )

    private data class W4cClip(
        val coverage: GPUClipCoveragePlan,
        val execution: GPUClipExecutionPlan,
    )

    private fun invalid(message: String): GpuPlanLoweringResult.InvalidPlan =
        GpuPlanLoweringResult.InvalidPlan(diagnostic("w4c.lowering.incompatible_plan", RenderDiagnosticDomain.RESOURCE, message))

    private fun capability(code: RenderDiagnosticCode, message: String): GpuPlanLoweringResult.UnsupportedCapability =
        GpuPlanLoweringResult.UnsupportedCapability(diagnostic(code.value, RenderDiagnosticDomain.CAPABILITY, message))

    private fun diagnostic(code: String, domain: RenderDiagnosticDomain, message: String): RenderDiagnostic =
        RenderDiagnostic(RenderDiagnosticCode(code), domain, RenderDiagnosticSeverity.ERROR, message)

    private companion object {
        val W4C_REQUIRED_OPERATIONS: Set<PlanOperationCapability> = setOf(
            PlanOperationCapability.RenderPass,
            PlanOperationCapability.CopyUpload,
            PlanOperationCapability.UniformBuffer,
            PlanOperationCapability.Readback,
            PlanOperationCapability.DepthStencilAttachment,
            PlanOperationCapability.StencilCover,
        )
        const val VERTEX_BYTES: Long = 8L
        const val INDEX_BYTES: Long = 4L
    }
}
