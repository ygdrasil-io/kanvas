package org.graphiks.kanvas.gpu.renderer.planning

import org.graphiks.kanvas.gpu.plan.AttachmentLoadPlan
import org.graphiks.kanvas.gpu.plan.BinaryMaskFetchPlan
import org.graphiks.kanvas.gpu.plan.BinaryMaskedPathDraw
import org.graphiks.kanvas.gpu.plan.CoveragePlan
import org.graphiks.kanvas.gpu.plan.GeneralPathDraw
import org.graphiks.kanvas.gpu.plan.PathDrawGeometry
import org.graphiks.kanvas.gpu.plan.PathFillStrategy
import org.graphiks.kanvas.gpu.plan.PathRenderPhase
import org.graphiks.kanvas.gpu.plan.PlanPass
import org.graphiks.kanvas.gpu.plan.PlanPassDependency
import org.graphiks.kanvas.gpu.plan.PlanResource
import org.graphiks.kanvas.gpu.plan.PlanResourceKind
import org.graphiks.kanvas.gpu.plan.PlanResourceRole
import org.graphiks.kanvas.gpu.plan.PlanResourceUsage
import org.graphiks.kanvas.gpu.plan.PlanTextureFormat
import org.graphiks.kanvas.gpu.plan.RenderGraph
import org.graphiks.kanvas.gpu.plan.SamplePlan
import org.graphiks.kanvas.gpu.plan.W4dGeneralPathPlanCompiler
import org.graphiks.kanvas.gpu.renderer.clips.GPUBounds
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoveragePlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionPlan
import org.graphiks.kanvas.gpu.renderer.color.GPUColorFormat
import org.graphiks.kanvas.gpu.renderer.color.GPUColorInterpretation
import org.graphiks.kanvas.gpu.renderer.commands.GPUFrameProvenance
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketID
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketRole
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitivePreparedPacketAuthority
import org.graphiks.kanvas.gpu.renderer.passes.GPUPlanW4dGeneralPreparedAuthority
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchEligibility
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchKind
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchQueueGuard
import org.graphiks.kanvas.gpu.renderer.passes.GPURenderStepID
import org.graphiks.kanvas.gpu.renderer.passes.GPUSamplePlan
import org.graphiks.kanvas.gpu.renderer.passes.GPUW4dBinaryMaskConsumerPlan
import org.graphiks.kanvas.gpu.renderer.passes.canonicalIdentity
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
import org.graphiks.kanvas.gpu.renderer.pipelines.GPURenderPipelineKey
import org.graphiks.kanvas.gpu.renderer.recording.CORE_PRIMITIVE_BINDING_LAYOUT_HASH
import org.graphiks.kanvas.gpu.renderer.recording.CORE_PRIMITIVE_RENDER_PIPELINE_KEY
import org.graphiks.kanvas.gpu.renderer.recording.CORE_PRIMITIVE_VERTEX_SOURCE_LABEL
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameCapabilitySeal
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameReadbackRequest
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackPixelFormat
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackRequestID
import org.graphiks.kanvas.gpu.renderer.recording.GPURecordingSeal
import org.graphiks.kanvas.gpu.renderer.recording.GPUTask
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskDependency
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskAtomicGroupID
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskID
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskList
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskPhase
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskUseToken
import org.graphiks.kanvas.gpu.renderer.recording.PREPARED_FRAME_LATE_BOUND_RESOURCE_GENERATION
import org.graphiks.kanvas.gpu.renderer.recording.canonicalSolidRectSrcOverBlendPlan
import org.graphiks.kanvas.gpu.renderer.recording.corePrimitiveScissorAuthority
import org.graphiks.kanvas.gpu.renderer.recording.corePrimitiveTargetStateHash
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryBudgetPlan
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryCategory
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceLifetime
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTextureDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourcePreparationRequest
import org.graphiks.kanvas.gpu.renderer.state.GPULoadStorePlan
import org.graphiks.kanvas.gpu.renderer.state.GPUPathSourceAuthority
import org.graphiks.kanvas.gpu.renderer.state.GPUStorePlan
import org.graphiks.kanvas.render.ir.RenderDiagnostic
import org.graphiks.kanvas.render.ir.RenderDiagnosticCode
import org.graphiks.kanvas.render.ir.RenderDiagnosticDomain
import org.graphiks.kanvas.render.ir.RenderDiagnosticSeverity
import org.graphiks.math.geometry.FillRule
import org.graphiks.math.geometry.PathFillGeometryF32

/** Lowers one fully validated W4d.2 path graph into handle-free prepared task facts. */
internal class W4dGeneralPathGraphLowerer {
    fun lower(request: GpuPlanLoweringRequest): GpuPlanLoweringResult = try {
        val graph = preflight(request.graph) ?: return invalid("The graph is not the exact W4d.2 topology.")
        val bounds = GPUPixelBounds(0, 0, request.graph.targetExtent.width, request.graph.targetExtent.height)
        val session = "w4d-general.session.${request.deviceGeneration.value}.${bounds.width}x${bounds.height}"
        val target = GPUFrameTargetRef("$session.logical-target")
        val staging = GPUFrameBufferRef("$session.staging")
        val seal = GPUFrameCapabilitySeal.capture(request.frameId, request.deviceGeneration, request.capabilities)
        val preparedAuthority = GPUPlanW4dGeneralPreparedAuthority.issueAfterFullGraphValidation(
            request.graph,
            graph.pathPasses,
        )
        if (!preparedAuthority.preflightRevalidates(request.graph, graph.pathPasses)) {
            return invalid("The W4d.2 prepared authority did not revalidate the graph.")
        }
        val packets = graph.pathPasses.mapIndexed { index, pass -> packet(pass, index, bounds) }
        graph.pathPasses.zip(packets).forEach { (pass, built) ->
            val publicPipelineKey = built.packet.renderPipelineKey
                ?: return invalid("The W4d.2 packet lacks a render pipeline key.")
            built.packet.attachCorePrimitivePreparedAuthority(
                GPUCorePrimitivePreparedPacketAuthority.plannedW4dGeneral(
                    packet = built.packet,
                    pass = pass,
                    structuralPipelineKey = built.structuralPipelineKey,
                    renderPipelineKey = publicPipelineKey,
                    authority = preparedAuthority,
                ),
            )
        }
        val renders = graph.pathPasses.zip(packets).map { (pass, built) ->
            GPUTask.Render(
                taskId = GPUTaskID("task.w4d-general.${request.graph.id.value}.${pass.id.value}"),
                recordingId = request.recordingId,
                phase = GPUTaskPhase.Render,
                target = if (pass.target == graph.logicalTargetId) {
                    target
                } else {
                    GPUFrameTargetRef("$session.${pass.target.value}")
                },
                loadStore = GPULoadStorePlan(loadLabel(pass), GPUStorePlan.Store),
                samplePlan = samplePlan(pass.draw.sample),
                drawPackets = listOf(built.packet),
                batchEligibilityByPacketId = mapOf(
                    built.packet.packetId to GPUPassBatchEligibility(
                        kind = GPUPassBatchKind.SolidFill,
                        queueGuard = GPUPassBatchQueueGuard(emptyList(), emptyList()),
                    ),
                ),
            )
        }
        val prepare = GPUTask.PrepareResources(
            GPUTaskID("task.w4d-general.${request.graph.id.value}.prepare"),
            request.recordingId,
            GPUTaskPhase.Prepare,
            listOf(
                GPUResourcePreparationRequest(
                    target,
                    GPUFrameTextureDescriptor(bounds, GPUColorFormat.RGBA8UnormSrgb, 1),
                    GPUFrameResourceRole.SceneTarget,
                    setOf(GPUFrameResourceUsage.RenderAttachment, GPUFrameResourceUsage.CopySource),
                    GPUFrameResourceLifetime.FrameLocal,
                    graph.logicalTargetBytes,
                    "$session.logical-target",
                ),
                GPUResourcePreparationRequest(
                    staging,
                    GPUFrameBufferDescriptor(graph.readbackBytes, request.graph.capabilities.copyBytesPerRowAlignment.toLong()),
                    GPUFrameResourceRole.ReadbackStaging,
                    setOf(GPUFrameResourceUsage.CopyDestination, GPUFrameResourceUsage.MapRead),
                    GPUFrameResourceLifetime.FrameLocal,
                    graph.readbackBytes,
                    "$session.staging",
                ),
            ),
        )
        val readback = GPUTask.Readback(
            GPUTaskID("task.w4d-general.${request.graph.id.value}.readback"),
            request.recordingId,
            GPUTaskPhase.Readback,
            target,
            staging,
            GPUFrameReadbackRequest(
                GPUReadbackRequestID("w4d-general.${request.graph.id.value}.readback"),
                bounds,
                GPUReadbackPixelFormat.Rgba8Unorm,
                GPUColorInterpretation.EncodedPremulSrgb,
            ),
        )
        val renderIds = renders.map(GPUTask.Render::taskId)
        val dependencies = buildList {
            add(GPUTaskDependency(
                prepare.taskId,
                renderIds.first(),
                "resource-prepare",
                GPUTaskUseToken("w4d-general.${request.graph.id.value}.prepare"),
                "w4d-general-resource-availability",
            ))
            renderIds.zipWithNext().forEachIndexed { index, (before, after) ->
                val previousGroup = graph.pathPasses[index].atomicGroup?.value
                val nextGroup = graph.pathPasses[index + 1].atomicGroup?.value
                add(GPUTaskDependency(
                    before,
                    after,
                    "plan-pass-dependency",
                    GPUTaskUseToken("w4d-general.${request.graph.id.value}.$index"),
                    "w4d-general-path-order",
                    if (previousGroup != null && previousGroup == nextGroup) {
                        GPUTaskAtomicGroupID(previousGroup)
                    } else {
                        null
                    },
                ))
            }
            add(GPUTaskDependency(
                renderIds.last(),
                readback.taskId,
                "plan-pass-dependency",
                GPUTaskUseToken("w4d-general.${request.graph.id.value}.readback"),
                "w4d-general-render-before-readback",
            ))
        }
        val replay = "w4d-general:${request.graph.id.value}"
        GpuPlanLoweringResult.Lowered(
            GPUTaskList(
                request.frameId,
                seal,
                listOf(GPURecordingSeal(request.recordingId, 0L, replay, replay, seal.sealHash)),
                replay,
                listOf(prepare) + renders + readback,
                dependencies,
                GPUTaskPhase.entries,
                memoryBudget(request.graph, graph.logicalTargetBytes),
            ),
            readback.request.requestId.value,
        )
    } catch (_: IllegalArgumentException) {
        invalid("The W4d.2 graph cannot be represented by renderer values.")
    }

    /** Revalidates the entire immutable graph before any W4d.2 packet is converted or published. */
    private fun preflight(graph: RenderGraph): ValidatedGraph? {
        if (!graph.verifyW4dGeneralCompilerWitness()) return null
        if (graph.capabilityId !in setOf(
                W4dGeneralPathPlanCompiler.HARD_CAPABILITY_ID,
                W4dGeneralPathPlanCompiler.AA_CAPABILITY_ID,
            )
        ) return null
        val passes = graph.passes()
        val readback = passes.lastOrNull() as? PlanPass.ReadbackPass ?: return null
        val pathPasses = passes.dropLast(1).filterIsInstance<PlanPass.PathRenderPass>()
        if (pathPasses.isEmpty() || passes.dropLast(1).any {
                it !is PlanPass.PathRenderPass && it !is PlanPass.PathMaskClearPass
            }
        ) return null
        if (graph.dependencies() != passes.zipWithNext().map { (before, after) ->
                PlanPassDependency(before.id, after.id)
            }
        ) return null
        val resourcesById = graph.resources().associateBy(PlanResource::id)
        val logical = graph.resources().singleOrNull { resource ->
            resource.role == PlanResourceRole.LogicalTarget &&
                resource.kind == PlanResourceKind.Texture2D &&
                resource.sampleCountI32 == 1 &&
                resource.format == PlanTextureFormat.Color(graph.colorFormat) &&
                PlanResourceUsage.RenderAttachment in resource.usages() &&
                PlanResourceUsage.CopySource in resource.usages()
        } ?: return null
        val staging = graph.resources().singleOrNull { it.role == PlanResourceRole.ReadbackStaging }
            ?.takeIf { resource -> resource.kind == PlanResourceKind.Buffer &&
                PlanResourceUsage.CopyDestination in resource.usages() &&
                PlanResourceUsage.MapRead in resource.usages() }
            ?: return null
        if (readback.source != logical.id || readback.staging != staging.id) return null
        if (passes.dropLast(1).filterIsInstance<PlanPass.PathMaskClearPass>().any { clear ->
                !matchesMaskClear(clear, resourcesById)
            }
        ) return null
        if (!maskClearsImmediatelyPrecedeTheirProducer(passes.dropLast(1))) return null
        if (pathPasses.any { pass -> !matchesPassResources(pass, resourcesById, graph.colorFormat) }) return null
        val colorPasses = pathPasses.filter { pass -> pass.phase.isColorProducing() }
        if (colorPasses.isEmpty() || colorPasses.map { it.draw.commandIndex }.distinct().size != graph.visualCommandCount ||
            colorPasses.zipWithNext().any { (before, after) -> before.draw.commandIndex >= after.draw.commandIndex }
        ) return null
        if (colorPasses.any { pass -> pass.store.name != "Store" }) return null
        val usesAa = pathPasses.any { pass -> pass.draw.sample == SamplePlan.Multisample4 }
        return when (graph.capabilityId) {
            W4dGeneralPathPlanCompiler.HARD_CAPABILITY_ID -> {
                if (usesAa || graph.resources().any { resource ->
                        resource.role in setOf(PlanResourceRole.MultisampleColorTarget, PlanResourceRole.PathHardEdgeMask,
                            PlanResourceRole.PathHardEdgeDepthStencil)
                    } || pathPasses.any { it.resolveTarget != null }
                ) null else ValidatedGraph(pathPasses, logical.id, logical.byteSize, staging.byteSize)
            }
            W4dGeneralPathPlanCompiler.AA_CAPABILITY_ID -> {
                val multisample = graph.resources().singleOrNull { it.role == PlanResourceRole.MultisampleColorTarget }
                    ?.takeIf { resource -> resource.kind == PlanResourceKind.Texture2D && resource.sampleCountI32 == 4 &&
                        resource.format == PlanTextureFormat.Color(graph.colorFormat) &&
                        PlanResourceUsage.RenderAttachment in resource.usages() }
                    ?: return null
                if (!usesAa || colorPasses.any { it.target != multisample.id } ||
                    colorPasses.dropLast(1).any { it.resolveTarget != null } ||
                    colorPasses.last().resolveTarget != logical.id
                ) null else ValidatedGraph(pathPasses, logical.id, logical.byteSize, staging.byteSize)
            }
            else -> null
        }
    }

    private fun packet(
        pass: PlanPass.PathRenderPass,
        paintOrder: Int,
        bounds: GPUPixelBounds,
    ): BuiltPacket {
        val draw = pass.draw
        val scissor = draw.copyScissorI32()
        val scissorBounds = GPUPixelBounds(scissor.left, scissor.top, scissor.right, scissor.bottom)
        val binaryMaskConsumer = (draw as? BinaryMaskedPathDraw)?.let { binary ->
            GPUW4dBinaryMaskConsumerPlan.exact(
                maskResourceId = binary.mask.value,
                commandIdValue = binary.commandIndex,
                renderPipelineKey = GPURenderPipelineKey(
                    "$CORE_PRIMITIVE_RENDER_PIPELINE_KEY.w4d-binary-mask-consumer-v1",
                ),
            )
        }
        val scissorClip = if (scissorBounds == bounds) {
            GPUClipCoveragePlan.NoClip to GPUClipExecutionPlan.NoClip
        } else {
            GPUClipCoveragePlan.Scissor(GPUBounds(
                scissorBounds.left.toFloat(), scissorBounds.top.toFloat(),
                scissorBounds.right.toFloat(), scissorBounds.bottom.toFloat(),
            )) to GPUClipExecutionPlan.ScissorOnly(scissorBounds)
        }
        val producer = pass.phase.isStencilProducer()
        val clip = if (producer) GPUClipCoveragePlan.NoClip to GPUClipExecutionPlan.NoClip else scissorClip
        val geometryInput = if (binaryMaskConsumer != null) {
            binaryMaskCoverGeometryInput(scissorBounds)
        } else {
            val geometry = fillGeometry(draw.copyPathGeometry())
            when (draw.strategy) {
                PathFillStrategy.DirectTriangle -> {
                    val direct = requireNotNull(geometry.copyDirectTriangleF32OrNull())
                    GPUCorePrimitiveGeometryInput.TriangulatedPath(
                        vertices = direct.copyVerticesF32().toList(),
                        indices = direct.copyIndicesI32().toList(),
                        sourceContourStarts = listOf(0),
                        sourceVertexCount = direct.vertexCountI32,
                        coverBounds = scissorBounds,
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
                        coverBounds = scissorBounds,
                        geometryMode = GPUCorePrimitiveGeometryMode.StencilEdgeFan,
                        fillRule = when (geometry.fillRule) {
                            FillRule.WINDING -> GPUCorePrimitiveFillRule.Winding
                            FillRule.EVEN_ODD -> GPUCorePrimitiveFillRule.EvenOdd
                            else -> error("W4d.2 rejects inverse path fills")
                        },
                        inverseFill = false,
                        sourceAuthority = GPUPathSourceAuthority.W4dPlannedPathStrokeV1,
                    )
                }
            }
        }
        val semantic = GPUCorePrimitivePayloadGatherer().gatherPlannedW4dSemantic(
            GPUCorePrimitivePayloadInput(
                commandIdValue = draw.commandIndex,
                sourceFamily = GPUCorePrimitiveSourceFamily.Path,
                geometry = geometryInput,
                premultipliedRgba = listOf(draw.color.red, draw.color.green, draw.color.blue, draw.color.alpha),
                targetBounds = bounds,
                scissorBounds = scissorBounds,
                clipCoveragePlan = clip.first,
                clipExecutionPlanIdentity = clip.second.canonicalIdentity(),
                blendPlanIdentity = canonicalSolidRectSrcOverBlendPlan().canonicalIdentity(),
                frameProvenance = GPUFrameProvenance.None,
                coverageMode = coverageMode(pass),
            ),
        )
        val blend = canonicalSolidRectSrcOverBlendPlan()
        val sampleCount = samplePlan(pass.draw.sample).sampleCount
        val role = when {
            pass.phase.isStencilProducer() -> GPUDrawPacketRole.PathStencilProducer
            pass.phase.isStencilCover() -> GPUDrawPacketRole.PathStencilCover
            else -> GPUDrawPacketRole.Shading
        }
        val structural = when (role) {
            GPUDrawPacketRole.Shading -> corePrimitiveRenderPipelineStructuralKey(
                semantic, clip.second, blend, sampleCount,
                GPUColorFormat.RGBA8UnormSrgb.corePrimitiveStructuralColorFormat(),
            )
            GPUDrawPacketRole.PathStencilProducer ->
                org.graphiks.kanvas.gpu.renderer.passes.corePrimitivePathStencilRenderPipelineStructuralKey(
                    semantic,
                    org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveRenderPipelineStructuralKey.Role.PathStencilProducer,
                    clip.second,
                    blend,
                    sampleCount,
                    GPUColorFormat.RGBA8UnormSrgb.corePrimitiveStructuralColorFormat(),
                )
            GPUDrawPacketRole.PathStencilCover ->
                org.graphiks.kanvas.gpu.renderer.passes.corePrimitivePathStencilRenderPipelineStructuralKey(
                    semantic,
                    org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveRenderPipelineStructuralKey.Role.PathStencilCover,
                    clip.second,
                    blend,
                    sampleCount,
                    GPUColorFormat.RGBA8UnormSrgb.corePrimitiveStructuralColorFormat(),
                )
            else -> error("W4d.2 emits only path shading and stencil roles")
        }
        val roleLabel = when (role) {
            GPUDrawPacketRole.Shading -> "color"
            GPUDrawPacketRole.PathStencilProducer -> "producer"
            GPUDrawPacketRole.PathStencilCover -> "cover"
        }
        return BuiltPacket(GPUDrawPacket(
            packetId = GPUDrawPacketID("packet.w4d-general.${draw.commandIndex}.${pass.id.value}"),
            commandIdValue = draw.commandIndex,
            analysisRecordId = "analysis.w4d_general_path_draw.${draw.commandIndex}",
            passId = pass.id.value,
            layerId = "root",
            bindingListId = "binding.w4d-general.${draw.commandIndex}.$roleLabel",
            insertionReasonCode = "w4d-general-path-$roleLabel",
            sortKey = paintOrder.toLong(),
            sortKeyPreimage = "paint-order:$paintOrder",
            renderStepId = GPURenderStepID(CORE_PRIMITIVE_RENDER_STEP_IDENTITY),
            renderStepVersion = 1,
            role = role,
            blendPlan = blend,
            renderPipelineKey = binaryMaskConsumer?.renderPipelineKey
                ?: structural.stableRenderPipelineKey(CORE_PRIMITIVE_RENDER_PIPELINE_KEY),
            bindingLayoutHash = binaryMaskConsumer?.bindingLayoutHash ?: CORE_PRIMITIVE_BINDING_LAYOUT_HASH,
            uniformSlot = semantic.payloadRef.uniformSlot,
            resourceSlot = binaryMaskConsumer?.resourceSlot,
            w4dBinaryMaskConsumer = binaryMaskConsumer,
            semanticPayload = semantic,
            vertexSourceLabel = CORE_PRIMITIVE_VERTEX_SOURCE_LABEL,
            scissorBoundsHash = corePrimitiveScissorAuthority(scissorBounds),
            targetStateHash = corePrimitiveTargetStateHash(sampleCount, GPUColorFormat.RGBA8UnormSrgb),
            originalPaintOrder = paintOrder,
            resourceGeneration = PREPARED_FRAME_LATE_BOUND_RESOURCE_GENERATION,
            frameProvenance = GPUFrameProvenance.None,
            clipCoveragePlan = clip.first,
            clipExecutionPlan = clip.second,
        ), structural)
    }

    private fun fillGeometry(geometry: PathDrawGeometry): PathFillGeometryF32 = when (geometry) {
        is PathDrawGeometry.Fill -> geometry.valueF32
        is PathDrawGeometry.Stroke -> geometry.valueF32.copyFillGeometryF32()
    }

    /** The binary mask is sampled over the exact target scissor, never over the producer edges. */
    private fun binaryMaskCoverGeometryInput(
        scissorBounds: GPUPixelBounds,
    ): GPUCorePrimitiveGeometryInput.TriangulatedPath = GPUCorePrimitiveGeometryInput.TriangulatedPath(
        vertices = listOf(
            scissorBounds.left.toFloat(), scissorBounds.top.toFloat(),
            scissorBounds.right.toFloat(), scissorBounds.top.toFloat(),
            scissorBounds.right.toFloat(), scissorBounds.bottom.toFloat(),
            scissorBounds.left.toFloat(), scissorBounds.bottom.toFloat(),
        ),
        indices = listOf(0, 1, 2, 0, 2, 3),
        sourceContourStarts = listOf(0),
        sourceVertexCount = 4,
        coverBounds = scissorBounds,
        geometryMode = GPUCorePrimitiveGeometryMode.DirectTriangles,
        fillRule = GPUCorePrimitiveFillRule.Winding,
        inverseFill = false,
        sourceAuthority = GPUPathSourceAuthority.W4dPlannedPathStrokeV1,
    )

    private fun samplePlan(sample: SamplePlan): GPUSamplePlan = when (sample) {
        SamplePlan.SingleSample -> GPUSamplePlan.SingleSampleFrame
        SamplePlan.Multisample4 -> GPUSamplePlan.MultisampleFrame(4)
    }

    private fun matchesPassResources(
        pass: PlanPass.PathRenderPass,
        resourcesById: Map<org.graphiks.kanvas.gpu.plan.PlanResourceId, PlanResource>,
        colorFormat: org.graphiks.kanvas.gpu.plan.PlanLogicalColorFormat,
    ): Boolean {
        val target = resourcesById[pass.target] ?: return false
        val expectedSamples = when (pass.draw.sample) {
            SamplePlan.SingleSample -> 1
            SamplePlan.Multisample4 -> 4
        }
        if (target.kind != PlanResourceKind.Texture2D || target.sampleCountI32 != expectedSamples ||
            PlanResourceUsage.RenderAttachment !in target.usages()
        ) return false
        val data = pass.drawDataResources
        if (!matchesBuffer(resourcesById[data.vertex], PlanResourceRole.VertexData, PlanResourceUsage.Vertex) ||
            !matchesBuffer(resourcesById[data.index], PlanResourceRole.IndexData, PlanResourceUsage.Index) ||
            !matchesBuffer(resourcesById[data.uniform], PlanResourceRole.UniformData, PlanResourceUsage.Uniform)
        ) return false
        fun depth(role: PlanResourceRole, sampleCount: Int): Boolean {
            val resource = pass.depthStencil?.let(resourcesById::get) ?: return false
            return resource.role == role && resource.kind == PlanResourceKind.Texture2D &&
                resource.format == PlanTextureFormat.DepthStencil(
                    org.graphiks.kanvas.gpu.plan.PlanDepthStencilFormat.Depth24PlusStencil8,
                ) && resource.sampleCountI32 == sampleCount &&
                PlanResourceUsage.DepthStencilAttachment in resource.usages()
        }
        fun color(role: PlanResourceRole, sampleCount: Int): Boolean =
            target.role == role && target.sampleCountI32 == sampleCount &&
                target.format == PlanTextureFormat.Color(colorFormat)
        fun mask(): Boolean =
            target.role == PlanResourceRole.PathHardEdgeMask && target.sampleCountI32 == 1 &&
                target.format == PlanTextureFormat.CoverageMask &&
                target.usages().containsAll(
                    setOf(PlanResourceUsage.RenderAttachment, PlanResourceUsage.Sampled),
                )
        return when (pass.phase) {
            PathRenderPhase.SingleSampleDirectColor,
            -> pass.draw is GeneralPathDraw && pass.draw.sample == SamplePlan.SingleSample &&
                pass.draw.coverage == CoveragePlan.FullOrScissor && color(PlanResourceRole.LogicalTarget, 1) &&
                pass.depthStencil == null && pass.atomicGroup == null
            PathRenderPhase.SingleSampleStencilProducer,
            PathRenderPhase.SingleSampleStencilColorCover,
            -> pass.draw is GeneralPathDraw && pass.draw.sample == SamplePlan.SingleSample &&
                pass.draw.coverage == CoveragePlan.FullOrScissor && color(PlanResourceRole.LogicalTarget, 1) &&
                depth(PlanResourceRole.DepthStencil, 1) && canonicalGroupMatches(pass)
            PathRenderPhase.MultisampleDirectColor,
            -> pass.draw is GeneralPathDraw && pass.draw.sample == SamplePlan.Multisample4 &&
                pass.draw.coverage == CoveragePlan.StencilAA4 && color(PlanResourceRole.MultisampleColorTarget, 4) &&
                depth(PlanResourceRole.DepthStencil, 4) && pass.atomicGroup == null
            PathRenderPhase.MultisampleStencilProducer,
            PathRenderPhase.MultisampleStencilColorCover,
            -> pass.draw is GeneralPathDraw && pass.draw.sample == SamplePlan.Multisample4 &&
                pass.draw.coverage == CoveragePlan.StencilAA4 && color(PlanResourceRole.MultisampleColorTarget, 4) &&
                depth(PlanResourceRole.DepthStencil, 4) && canonicalGroupMatches(pass)
            PathRenderPhase.HardEdgeMaskProducer,
            -> pass.draw is GeneralPathDraw && pass.draw.sample == SamplePlan.SingleSample &&
                pass.draw.coverage == CoveragePlan.FullOrScissor && mask() && pass.depthStencil == null &&
                canonicalGroupMatches(pass)
            PathRenderPhase.HardEdgeMaskStencilProducer,
            PathRenderPhase.HardEdgeMaskStencilCover,
            -> pass.draw is GeneralPathDraw && pass.draw.sample == SamplePlan.SingleSample &&
                pass.draw.coverage == CoveragePlan.FullOrScissor && mask() &&
                depth(PlanResourceRole.PathHardEdgeDepthStencil, 1) && canonicalGroupMatches(pass)
            PathRenderPhase.HardEdgeBinaryColorCover -> {
                val binary = pass.draw as? BinaryMaskedPathDraw
                binary != null && binary.maskFetch == BinaryMaskFetchPlan.TextureLoadUnfiltered &&
                    binary.broadcastSampleCountI32 == 4 && binary.coverage == CoveragePlan.BinaryMaskCover4 &&
                    color(PlanResourceRole.MultisampleColorTarget, 4) &&
                    resourcesById[binary.mask]?.let { resource ->
                        resource.role == PlanResourceRole.PathHardEdgeMask &&
                            resource.kind == PlanResourceKind.Texture2D &&
                            resource.format == PlanTextureFormat.CoverageMask &&
                            resource.sampleCountI32 == 1 &&
                            resource.usages().containsAll(
                                setOf(PlanResourceUsage.RenderAttachment, PlanResourceUsage.Sampled),
                            )
                    } == true && canonicalGroupMatches(pass)
            }
        }
    }

    private fun matchesBuffer(
        resource: PlanResource?,
        role: PlanResourceRole,
        usage: PlanResourceUsage,
    ): Boolean = resource?.kind == PlanResourceKind.Buffer && resource.role == role &&
        resource.usages().containsAll(setOf(usage, PlanResourceUsage.CopyDestination))

    private fun canonicalGroupMatches(pass: PlanPass.PathRenderPass): Boolean =
        pass.atomicGroup?.value == "w4d.2:${pass.draw.commandIndex}"

    private fun matchesMaskClear(
        clear: PlanPass.PathMaskClearPass,
        resourcesById: Map<org.graphiks.kanvas.gpu.plan.PlanResourceId, PlanResource>,
    ): Boolean = resourcesById[clear.target]?.let { mask ->
        mask.role == PlanResourceRole.PathHardEdgeMask && mask.kind == PlanResourceKind.Texture2D &&
            mask.format == PlanTextureFormat.CoverageMask && mask.sampleCountI32 == 1 &&
            mask.usages().containsAll(
                setOf(PlanResourceUsage.RenderAttachment, PlanResourceUsage.Sampled),
            )
    } == true

    private fun maskClearsImmediatelyPrecedeTheirProducer(passes: List<PlanPass>): Boolean =
        passes.withIndex().filter { (_, pass) -> pass is PlanPass.PathMaskClearPass }.all { (index, pass) ->
            val clear = pass as PlanPass.PathMaskClearPass
            val producer = passes.getOrNull(index + 1) as? PlanPass.PathRenderPass ?: return@all false
            producer.target == clear.target && producer.atomicGroup == clear.atomicGroup &&
                producer.phase in setOf(
                    PathRenderPhase.HardEdgeMaskProducer,
                    PathRenderPhase.HardEdgeMaskStencilProducer,
                )
        }

    private fun PathRenderPhase.isColorProducing(): Boolean = this in setOf(
        PathRenderPhase.SingleSampleDirectColor,
        PathRenderPhase.SingleSampleStencilColorCover,
        PathRenderPhase.MultisampleDirectColor,
        PathRenderPhase.MultisampleStencilColorCover,
        PathRenderPhase.HardEdgeBinaryColorCover,
    )

    private fun PathRenderPhase.isStencilProducer(): Boolean = this in setOf(
        PathRenderPhase.SingleSampleStencilProducer,
        PathRenderPhase.MultisampleStencilProducer,
        PathRenderPhase.HardEdgeMaskStencilProducer,
    )

    private fun PathRenderPhase.isStencilCover(): Boolean = this in setOf(
        PathRenderPhase.SingleSampleStencilColorCover,
        PathRenderPhase.MultisampleStencilColorCover,
        PathRenderPhase.HardEdgeMaskStencilCover,
    )

    private fun coverageMode(pass: PlanPass.PathRenderPass): GPUCorePrimitiveCoverageMode = when {
        pass.draw.coverage == CoveragePlan.StencilAA4 -> GPUCorePrimitiveCoverageMode.StencilAA
        pass.phase.isStencilProducer() || pass.phase.isStencilCover() -> GPUCorePrimitiveCoverageMode.Stencil1x
        else -> GPUCorePrimitiveCoverageMode.FullOrScissor
    }

    private fun loadLabel(pass: PlanPass.PathRenderPass): String = when (pass.phase) {
        PathRenderPhase.HardEdgeMaskProducer,
        PathRenderPhase.HardEdgeMaskStencilProducer,
        -> "clear"
        else -> loadLabel(pass.load)
    }

    private fun loadLabel(load: AttachmentLoadPlan): String = when (load) {
        AttachmentLoadPlan.ClearTransparent -> "clear"
        AttachmentLoadPlan.Load -> "load"
    }

    private fun memoryBudget(graph: RenderGraph, targetBytes: Long): GPUFrameMemoryBudgetPlan = GPUFrameMemoryBudgetPlan(
        peakFrameTransientBytes = graph.peakFrameLocalBytes - targetBytes,
        targetResidentBytes = targetBytes,
        categoryTotals = GPUFrameMemoryCategory.entries.associateWith { category ->
            if (category == GPUFrameMemoryCategory.CanonicalTarget) targetBytes else 0L
        },
        deviceLimitFacts = emptyList(),
        configuredAggregateBudgetBytes = graph.budget.maxFrameLocalBytes,
        diagnostic = null,
    )

    private data class ValidatedGraph(
        val pathPasses: List<PlanPass.PathRenderPass>,
        val logicalTargetId: org.graphiks.kanvas.gpu.plan.PlanResourceId,
        val logicalTargetBytes: Long,
        val readbackBytes: Long,
    )

    private data class BuiltPacket(
        val packet: GPUDrawPacket,
        val structuralPipelineKey: org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveRenderPipelineStructuralKey,
    )

    private fun invalid(message: String): GpuPlanLoweringResult.InvalidPlan =
        GpuPlanLoweringResult.InvalidPlan(
            RenderDiagnostic(
                RenderDiagnosticCode("w4d-general.lowering.incompatible_plan"),
                RenderDiagnosticDomain.RESOURCE,
                RenderDiagnosticSeverity.ERROR,
                message,
            ),
        )
}
