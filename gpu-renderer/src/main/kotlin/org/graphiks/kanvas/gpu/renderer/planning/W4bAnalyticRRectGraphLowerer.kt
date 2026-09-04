package org.graphiks.kanvas.gpu.renderer.planning

import kotlin.math.ceil
import kotlin.math.floor
import org.graphiks.kanvas.gpu.plan.AnalyticRRectMemoryFootprint
import org.graphiks.kanvas.gpu.plan.AnalyticRRectPlanBudget
import org.graphiks.kanvas.gpu.plan.AnalyticRRectPlanBudgetResult
import org.graphiks.kanvas.gpu.plan.AnalyticRRectDraw
import org.graphiks.kanvas.gpu.plan.AttachmentLoadPlan
import org.graphiks.kanvas.gpu.plan.AttachmentStorePlan
import org.graphiks.kanvas.gpu.plan.BlendPlan
import org.graphiks.kanvas.gpu.plan.CoveragePlan
import org.graphiks.kanvas.gpu.plan.PlanBufferGrowth
import org.graphiks.kanvas.gpu.plan.PlanDrawDataResources
import org.graphiks.kanvas.gpu.plan.PlanLogicalColorFormat
import org.graphiks.kanvas.gpu.plan.PlanOperationCapability
import org.graphiks.kanvas.gpu.plan.PlanPass
import org.graphiks.kanvas.gpu.plan.PlanPassDependency
import org.graphiks.kanvas.gpu.plan.PlanResource
import org.graphiks.kanvas.gpu.plan.PlanResourceKind
import org.graphiks.kanvas.gpu.plan.PlanResourceLifetime
import org.graphiks.kanvas.gpu.plan.PlanResourceRole
import org.graphiks.kanvas.gpu.plan.PlanResourceUsage
import org.graphiks.kanvas.gpu.plan.RenderGraph
import org.graphiks.kanvas.gpu.plan.SamplePlan
import org.graphiks.kanvas.gpu.plan.W4bAnalyticRRectPlanCompiler
import org.graphiks.kanvas.gpu.plan.W4bPlanDiagnostics
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.clips.GPUBounds
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoveragePlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionPlan
import org.graphiks.kanvas.gpu.renderer.color.GPUColorFormat
import org.graphiks.kanvas.gpu.renderer.color.GPUColorInterpretation
import org.graphiks.kanvas.gpu.renderer.commands.GPUFrameProvenance
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitivePreparedPacketAuthority
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitivePreparedSemanticAuthority
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveRenderPipelineStructuralKey
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveW4bPlannedRRectAuthority
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketID
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketRole
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchEligibility
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchKind
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchQueueGuard
import org.graphiks.kanvas.gpu.renderer.passes.GPURenderStepID
import org.graphiks.kanvas.gpu.renderer.passes.GPUSamplePlan
import org.graphiks.kanvas.gpu.renderer.passes.W4bSessionScratchDrawV1
import org.graphiks.kanvas.gpu.renderer.passes.W4bSessionScratchV1
import org.graphiks.kanvas.gpu.renderer.passes.buildCorePrimitiveAnalyticShapeUniform
import org.graphiks.kanvas.gpu.renderer.passes.canonicalIdentity
import org.graphiks.kanvas.gpu.renderer.passes.corePrimitiveRenderPipelineStructuralKey
import org.graphiks.kanvas.gpu.renderer.passes.corePrimitiveStructuralColorFormat
import org.graphiks.kanvas.gpu.renderer.payloads.CORE_PRIMITIVE_RENDER_STEP_IDENTITY
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveCoverageMode
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitivePayloadGatherer
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitivePayloadInput
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveSourceFamily
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.recording.CORE_PRIMITIVE_ANALYTIC_SHAPE_BINDING_LAYOUT_HASH
import org.graphiks.kanvas.gpu.renderer.recording.CORE_PRIMITIVE_RENDER_PIPELINE_KEY
import org.graphiks.kanvas.gpu.renderer.recording.CORE_PRIMITIVE_VERTEX_SOURCE_LABEL
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameCapabilitySeal
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameReadbackRequest
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackPixelFormat
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackRequestID
import org.graphiks.kanvas.gpu.renderer.recording.GPURecordingSeal
import org.graphiks.kanvas.gpu.renderer.recording.GPUTask
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskID
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskList
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskPhase
import org.graphiks.kanvas.gpu.renderer.recording.PREPARED_FRAME_LATE_BOUND_RESOURCE_GENERATION
import org.graphiks.kanvas.gpu.renderer.recording.canonicalSolidRectSrcOverBlendPlan
import org.graphiks.kanvas.gpu.renderer.recording.corePrimitiveScissorAuthority
import org.graphiks.kanvas.gpu.renderer.recording.corePrimitiveTargetStateHash
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryAllocation
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryBudgetPlan
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryCategory
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryResourceKind
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUUniformSlabPayload
import org.graphiks.kanvas.gpu.renderer.resources.GPUUniformSlabPlanner
import org.graphiks.kanvas.gpu.renderer.resources.GPUUniformSlabPlanningResult
import org.graphiks.kanvas.gpu.renderer.resources.corePrimitiveFramePoolCapacitiesOrNull
import org.graphiks.kanvas.gpu.renderer.state.GPULoadStorePlan
import org.graphiks.kanvas.gpu.renderer.state.GPUStorePlan
import org.graphiks.kanvas.render.ir.DrawOrigin
import org.graphiks.kanvas.render.ir.RenderDiagnostic
import org.graphiks.kanvas.render.ir.RenderDiagnosticCode
import org.graphiks.kanvas.render.ir.RenderDiagnosticDomain
import org.graphiks.kanvas.render.ir.RenderDiagnosticSeverity
import org.graphiks.math.geometry.RRectF32
import org.graphiks.math.geometry.RectI32

/** Lowers only the closed W4b analytic-RRect graph; it never re-enters Scene IR or legacy routes. */
internal class W4bAnalyticRRectGraphLowerer {
    fun lower(request: GpuPlanLoweringRequest): GpuPlanLoweringResult = try {
        val graph = validateW4bGraph(request.graph)
            ?: return invalid("The graph is not the exact W4b topology.")
        val limits = request.capabilities.limits
            ?: return capability(W4bPlanDiagnostics.CapabilityBufferSize, "W4b lowering requires observed renderer limits.")
        val maxBufferSize = limits.maxBufferSize
            ?: return capability(W4bPlanDiagnostics.CapabilityBufferSize, "W4b lowering requires an observed maxBufferSize.")
        val maxDynamicUniformBuffers = limits.maxDynamicUniformBuffersPerPipelineLayout
            ?.takeIf { it >= 1L }
            ?: return capability(
                W4bPlanDiagnostics.CapabilityDynamicUniform,
                "W4b lowering requires at least one dynamic uniform buffer binding.",
            )
        val targetBounds = GPUPixelBounds(0, 0, request.graph.targetExtent.width, request.graph.targetExtent.height)
        val sessionIdentity = w4bSessionIdentity(request.deviceGeneration, targetBounds)
        val target = GPUFrameTargetRef("$sessionIdentity.target")
        val staging = GPUFrameBufferRef("$sessionIdentity.staging")
        val memory = memoryBudget(
            request.graph,
            graph,
            targetBounds,
            request.deviceGeneration,
            limits.capabilityFacts("frame-memory-budget"),
        ) ?: return invalid("The W4b graph memory facts cannot be represented by the renderer.")
        val builtPackets = graph.draws.mapIndexed { paintOrder, draw -> packet(draw, paintOrder, targetBounds) }
        val capabilitySeal = GPUFrameCapabilitySeal.capture(request.frameId, request.deviceGeneration, request.capabilities)
        val scratch = sealScratch(
            graph = graph,
            planId = request.graph.id.value,
            builtPackets = builtPackets,
            target = target,
            staging = staging,
            targetBounds = targetBounds,
            capabilitySealHash = capabilitySeal.sealHash,
            deviceGeneration = request.deviceGeneration.value,
            maxBufferSize = maxBufferSize,
            maxDynamicUniformBuffers = maxDynamicUniformBuffers,
        ) ?: return invalid("W4b scratch packing is invalid.")
        val packets = builtPackets.map { built ->
            built.packet.attachCorePrimitivePreparedAuthority(
                GPUCorePrimitivePreparedPacketAuthority.plannedW4b(
                    structuralPipelineKey = built.structuralPipelineKey,
                    renderPipelineKey = requireNotNull(built.packet.renderPipelineKey),
                    scratch = scratch,
                ),
            )
        }
        val replay = "w4b:${request.graph.id.value}"
        val render = GPUTask.Render(
            taskId = GPUTaskID("task.w4b.${request.graph.id.value}.base-render"),
            recordingId = request.recordingId,
            phase = GPUTaskPhase.Render,
            target = target,
            loadStore = GPULoadStorePlan("clear", GPUStorePlan.Store),
            samplePlan = GPUSamplePlan.SingleSampleFrame,
            drawPackets = packets,
            batchEligibilityByPacketId = packets.associate { packet ->
                packet.packetId to GPUPassBatchEligibility(
                    kind = GPUPassBatchKind.SolidFill,
                    queueGuard = GPUPassBatchQueueGuard(emptyList(), emptyList()),
                )
            },
        )
        val base = GPUTaskList(
            request.frameId,
            capabilitySeal,
            listOf(GPURecordingSeal(request.recordingId, 0L, replay, replay, capabilitySeal.sealHash)),
            replay,
            listOf(render),
            emptyList(),
            GPUTaskPhase.entries,
            memory,
        )
        val readback = GPUFrameReadbackRequest(
            GPUReadbackRequestID("w4b.${request.graph.id.value}.readback"),
            targetBounds,
            GPUReadbackPixelFormat.Rgba8Unorm,
            GPUColorInterpretation.EncodedPremulSrgb,
        )
        GpuPlanLoweringResult.Lowered(base, readback.requestId.value)
    } catch (error: IllegalArgumentException) {
        invalid(error.message ?: "The graph cannot be lowered into W4b renderer values.")
    }

    private fun validateW4bGraph(graph: RenderGraph): W4bGraph? {
        if (graph.capabilityId != W4bAnalyticRRectPlanCompiler.CAPABILITY_ID ||
            graph.colorFormat != PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL ||
            !hasExactW4bCapabilityFacts(graph)
        ) return null
        val passes = graph.passes()
        val render = passes.getOrNull(0) as? PlanPass.RenderPass ?: return null
        val readback = passes.getOrNull(1) as? PlanPass.ReadbackPass ?: return null
        if (passes.size != 2 || render.ordinal != 0 || readback.ordinal != 0 ||
            render.load != AttachmentLoadPlan.ClearTransparent || render.store != AttachmentStorePlan.Store
        ) return null
        val planDraws = render.draws()
        if (planDraws.any { it !is AnalyticRRectDraw }) return null
        val draws = planDraws.filterIsInstance<AnalyticRRectDraw>()
        if (draws.size !in 1..512 || graph.visualCommandCount != draws.size ||
            draws.zipWithNext().any { (first, second) -> first.commandIndex >= second.commandIndex } ||
            draws.none { draw -> draw.origin == DrawOrigin.RRECT }
        ) return null
        val footprint = when (val result = AnalyticRRectPlanBudget.calculate(
            graph.targetExtent,
            draws.size,
            graph.capabilities,
            graph.budget,
        )) {
            is AnalyticRRectPlanBudgetResult.WithinBudget -> result.footprint
            is AnalyticRRectPlanBudgetResult.Exceeded,
            is AnalyticRRectPlanBudgetResult.Invalid,
            -> return null
        }
        val expectedResources = expectedResources(graph, footprint) ?: return null
        val resources = graph.resources()
        if (resources.size != expectedResources.size ||
            !resources.zip(expectedResources).all { (actual, expected) -> actual.matches(expected) }
        ) return null
        val target = resources[0]
        val staging = resources[1]
        val vertex = resources[2]
        val index = resources[3]
        val uniform = resources[4]
        val expectedBindings = PlanDrawDataResources(vertex.id, index.id, uniform.id)
        if (render.id != PlanPass.RenderPass(
                ordinal = 0,
                target = target.id,
                draws = emptyList(),
                load = AttachmentLoadPlan.ClearTransparent,
                store = AttachmentStorePlan.Store,
                drawDataResources = expectedBindings,
            ).id ||
            readback.id != PlanPass.ReadbackPass(0, target.id, staging.id, footprint.readbackBytesPerRow).id ||
            render.target != target.id ||
            render.drawDataResources != expectedBindings ||
            readback.source != target.id ||
            readback.staging != staging.id ||
            readback.bytesPerRow != footprint.readbackBytesPerRow ||
            graph.dependencies() != listOf(PlanPassDependency(render.id, readback.id)) ||
            graph.peakFrameLocalBytes != footprint.peakBytes ||
            listOf(staging.byteSize, vertex.byteSize, index.byteSize, uniform.byteSize)
                .any { bytes -> bytes > graph.capabilities.maxBufferSizeBytes } ||
            draws.any { draw -> !isExactAnalyticDraw(draw, graph.targetExtent) }
        ) return null
        return W4bGraph(target, staging, vertex, index, uniform, render, readback, draws, footprint)
    }

    private fun hasExactW4bCapabilityFacts(graph: RenderGraph): Boolean {
        val capabilities = graph.capabilities
        val policy = capabilities.bufferAllocationPolicy
        return capabilities.maxDynamicUniformBuffersPerPipelineLayout >= 1 &&
            capabilities.supportedOperations() == PlanOperationCapability.entries.toSet() &&
            capabilities.copyBytesPerRowAlignment.isPositivePowerOfTwo() &&
            capabilities.minUniformBufferOffsetAlignment.isPositivePowerOfTwo() &&
            policy.growth == PlanBufferGrowth.PowerOfTwo &&
            policy.vertexFloorBytes.isPositivePowerOfTwo() &&
            policy.indexFloorBytes.isPositivePowerOfTwo() &&
            policy.uniformFloorBytes.isPositivePowerOfTwo()
    }

    private fun expectedResources(
        graph: RenderGraph,
        footprint: AnalyticRRectMemoryFootprint,
    ): List<PlanResource>? = try {
        listOf(
            PlanResource.of(
                PlanResourceRole.LogicalTarget,
                0,
                PlanResourceKind.Texture2D,
                graph.colorFormat,
                graph.targetExtent,
                footprint.targetBytes,
                setOf(PlanResourceUsage.RenderAttachment, PlanResourceUsage.CopySource),
                PlanResourceLifetime.FrameLocal,
                0,
                2,
            ),
            PlanResource.of(
                PlanResourceRole.ReadbackStaging,
                0,
                PlanResourceKind.Buffer,
                null,
                null,
                footprint.readbackBytes,
                setOf(PlanResourceUsage.CopyDestination, PlanResourceUsage.MapRead),
                PlanResourceLifetime.FrameLocal,
                1,
                2,
            ),
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
                2,
            ),
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
                2,
            ),
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
                2,
            ),
        )
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun PlanResource.matches(expected: PlanResource): Boolean =
        id == expected.id &&
            role == expected.role &&
            ordinal == expected.ordinal &&
            kind == expected.kind &&
            format == expected.format &&
            copyExtent() == expected.copyExtent() &&
            byteSize == expected.byteSize &&
            usages() == expected.usages() &&
            lifetime == expected.lifetime &&
            firstPassIndex == expected.firstPassIndex &&
            lastPassIndexExclusive == expected.lastPassIndexExclusive

    private fun isExactAnalyticDraw(draw: AnalyticRRectDraw, extent: org.graphiks.math.geometry.SizeI32): Boolean {
        val shape = draw.copyDeviceShape()
        val raster = draw.copyRasterBounds()
        val scissor = draw.copyScissor()
        val expectedRaster = exactRasterBounds(shape.rect) ?: return false
        val target = RectI32(0, 0, extent.width, extent.height)
        val targetRaster = intersect(expectedRaster, target) ?: return false
        val radii = radii(shape)
        val color = draw.color
        return hasValidCanonicalDeviceShape(shape) &&
            (draw.origin != DrawOrigin.RECT || radii.all { radius -> radius.toRawBits() == 0f.toRawBits() }) &&
            draw.coverage == CoveragePlan.AnalyticScalarAA &&
            draw.sample == SamplePlan.SingleSample &&
            draw.blend == BlendPlan.SrcOver &&
            draw.commandIndex >= 0 &&
            listOf(color.red, color.green, color.blue, color.alpha).all(Float::isFinite) &&
            color.red in 0f..color.alpha && color.green in 0f..color.alpha && color.blue in 0f..color.alpha &&
            raster == expectedRaster &&
            !scissor.isEmpty64() &&
            scissor.left >= targetRaster.left && scissor.top >= targetRaster.top &&
            scissor.right <= targetRaster.right && scissor.bottom <= targetRaster.bottom
    }

    private fun hasValidCanonicalDeviceShape(shape: RRectF32): Boolean {
        val rect = shape.rect
        val radii = radii(shape)
        if (listOf(rect.left, rect.top, rect.right, rect.bottom).any { value -> !value.isFinite() } ||
            rect.left >= rect.right || rect.top >= rect.bottom ||
            radii.any { radius -> !radius.isFinite() || radius < 0f || (radius == 0f && radius.toRawBits() != 0f.toRawBits()) }
        ) return false
        if (radii.chunked(2).any { (x, y) -> (x == 0f) != (y == 0f) }) return false
        val width = rect.right.toDouble() - rect.left.toDouble()
        val height = rect.bottom.toDouble() - rect.top.toDouble()
        return radii[0].toDouble() + radii[2].toDouble() <= width &&
            radii[4].toDouble() + radii[6].toDouble() <= width &&
            radii[1].toDouble() + radii[7].toDouble() <= height &&
            radii[3].toDouble() + radii[5].toDouble() <= height
    }

    private fun radii(shape: RRectF32): List<Float> = listOf(
        shape.topLeft.x,
        shape.topLeft.y,
        shape.topRight.x,
        shape.topRight.y,
        shape.bottomRight.x,
        shape.bottomRight.y,
        shape.bottomLeft.x,
        shape.bottomLeft.y,
    )

    private fun exactRasterBounds(device: org.graphiks.math.geometry.RectF32): RectI32? {
        if (listOf(device.left, device.top, device.right, device.bottom).any { value -> !value.isFinite() } ||
            device.left >= device.right || device.top >= device.bottom
        ) return null
        val values = listOf(
            floor(device.left.toDouble()),
            floor(device.top.toDouble()),
            ceil(device.right.toDouble()),
            ceil(device.bottom.toDouble()),
        )
        if (values.any { value -> value < Int.MIN_VALUE.toDouble() || value > Int.MAX_VALUE.toDouble() }) return null
        return RectI32(values[0].toInt(), values[1].toInt(), values[2].toInt(), values[3].toInt())
            .takeUnless(RectI32::isEmpty64)
    }

    private fun intersect(first: RectI32, second: RectI32): RectI32? =
        first.copy().takeIf { candidate -> candidate.intersect(second) }

    private fun packet(
        draw: AnalyticRRectDraw,
        paintOrder: Int,
        target: GPUPixelBounds,
    ): W4bBuiltPacket {
        val shape = draw.copyDeviceShape()
        val raster = draw.copyRasterBounds()
        val scissor = draw.copyScissor()
        val packetId = GPUDrawPacketID("packet.w4b.${draw.commandIndex}")
        val plannedScissor = GPUPixelBounds(scissor.left, scissor.top, scissor.right, scissor.bottom)
        val scratchDraw = W4bSessionScratchDrawV1(
            packetId,
            draw.commandIndex,
            draw.origin,
            shape,
            raster,
            plannedScissor,
        )
        val plannedAuthority = GPUCorePrimitiveW4bPlannedRRectAuthority(scratchDraw)
        val plannedClip = if (plannedScissor == target) {
            GPUClipCoveragePlan.NoClip
        } else {
            GPUClipCoveragePlan.Scissor(
                GPUBounds(
                    plannedScissor.left.toFloat(),
                    plannedScissor.top.toFloat(),
                    plannedScissor.right.toFloat(),
                    plannedScissor.bottom.toFloat(),
                ),
            )
        }
        val plannedExecution = if (plannedScissor == target) {
            GPUClipExecutionPlan.NoClip
        } else {
            GPUClipExecutionPlan.ScissorOnly(plannedScissor)
        }
        val blend = canonicalSolidRectSrcOverBlendPlan()
        val analysisRecordId = "analysis.fill_rrect.${draw.commandIndex}"
        val semantic = GPUCorePrimitivePayloadGatherer().gatherSemantic(
            GPUCorePrimitivePayloadInput(
                commandIdValue = draw.commandIndex,
                sourceFamily = GPUCorePrimitiveSourceFamily.RRect,
                geometry = plannedAuthority.geometryInput,
                premultipliedRgba = listOf(draw.color.red, draw.color.green, draw.color.blue, draw.color.alpha),
                targetBounds = target,
                scissorBounds = plannedScissor,
                clipCoveragePlan = plannedClip,
                clipExecutionPlanIdentity = plannedExecution.canonicalIdentity(),
                blendPlanIdentity = blend.canonicalIdentity(),
                frameProvenance = GPUFrameProvenance.None,
                coverageMode = GPUCorePrimitiveCoverageMode.ScalarAA,
                analysisRecordId = analysisRecordId,
                analysisCommandFamily = "FillRRect",
                rrectGeometryAuthority = plannedAuthority.authority,
            ),
        )
        val structuralKey = corePrimitiveRenderPipelineStructuralKey(
            semantic,
            plannedExecution,
            blend,
            sampleCount = 1,
            colorFormat = GPUColorFormat.RGBA8UnormSrgb.corePrimitiveStructuralColorFormat(),
        )
        return W4bBuiltPacket(
            GPUDrawPacket(
                packetId = packetId,
                commandIdValue = draw.commandIndex,
                analysisRecordId = analysisRecordId,
                passId = "pass.w4b.main",
                layerId = "root",
                bindingListId = "binding.w4b.${draw.commandIndex}",
                insertionReasonCode = "w4b-analytic-rrect",
                sortKey = paintOrder.toLong(),
                sortKeyPreimage = "paint-order:$paintOrder",
                renderStepId = GPURenderStepID(CORE_PRIMITIVE_RENDER_STEP_IDENTITY),
                renderStepVersion = 1,
                role = GPUDrawPacketRole.Shading,
                blendPlan = blend,
                renderPipelineKey = structuralKey.stableRenderPipelineKey(CORE_PRIMITIVE_RENDER_PIPELINE_KEY),
                bindingLayoutHash = CORE_PRIMITIVE_ANALYTIC_SHAPE_BINDING_LAYOUT_HASH,
                uniformSlot = semantic.payloadRef.uniformSlot,
                semanticPayload = semantic,
                vertexSourceLabel = CORE_PRIMITIVE_VERTEX_SOURCE_LABEL,
                scissorBoundsHash = corePrimitiveScissorAuthority(plannedScissor),
                targetStateHash = corePrimitiveTargetStateHash(1, GPUColorFormat.RGBA8UnormSrgb),
                originalPaintOrder = paintOrder,
                resourceGeneration = PREPARED_FRAME_LATE_BOUND_RESOURCE_GENERATION,
                frameProvenance = GPUFrameProvenance.None,
                clipCoveragePlan = plannedClip,
                clipExecutionPlan = plannedExecution,
            ),
            scratchDraw,
            structuralKey,
        )
    }

    private fun sealScratch(
        graph: W4bGraph,
        planId: String,
        builtPackets: List<W4bBuiltPacket>,
        target: GPUFrameTargetRef,
        staging: GPUFrameBufferRef,
        targetBounds: GPUPixelBounds,
        capabilitySealHash: String,
        deviceGeneration: Long,
        maxBufferSize: Long,
        maxDynamicUniformBuffers: Long,
    ): W4bSessionScratchV1? {
        val payloads = builtPackets.map { built ->
            val semantic = built.packet.semanticPayload as? GPUDrawSemanticPayload.CorePrimitive ?: return null
            val authority = GPUCorePrimitivePreparedSemanticAuthority.capture(semantic)
            val bytes = when (val result = buildCorePrimitiveAnalyticShapeUniform(semantic, authority)) {
                is org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveAnalyticShapeUniformBuildResult.Accepted -> result.bytes
                is org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveAnalyticShapeUniformBuildResult.Refused -> return null
            }
            if (bytes.size.toLong() != W4bSessionScratchV1.UNIFORM_PAYLOAD_BYTES) return null
            GPUUniformSlabPayload("analytic-rrect-draw-${built.packet.commandIdValue}", bytes)
        }
        val uniformPlan = when (val planned = GPUUniformSlabPlanner.plan(
            sourceLabel = W4bSessionScratchV1.SOURCE_LABEL,
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
        val structuralKey = builtPackets.firstOrNull()?.structuralPipelineKey ?: return null
        if (builtPackets.any { built -> built.structuralPipelineKey != structuralKey }) return null
        val poolCapacities = corePrimitiveFramePoolCapacitiesOrNull(
            graph.footprint.vertexUsefulBytes,
            graph.footprint.indexUsefulBytes,
            graph.footprint.uniformUsefulBytes,
        ) ?: return null
        return try {
            W4bSessionScratchV1(
                planId = planId,
                capabilitySealHash = capabilitySealHash,
                deviceGeneration = deviceGeneration,
                target = target,
                staging = staging,
                targetBounds = targetBounds,
                vertexResourceId = graph.vertex.id,
                indexResourceId = graph.index.id,
                uniformResourceId = graph.uniform.id,
                packetIds = builtPackets.map { built -> built.packet.packetId },
                commandIds = builtPackets.map { built -> built.packet.commandIdValue },
                draws = builtPackets.map(W4bBuiltPacket::scratchDraw),
                structuralPipelineKey = structuralKey,
                uniformPlan = uniformPlan,
                uniformStrideBytes = graph.footprint.uniformStrideBytes,
                vertexUsefulBytes = graph.footprint.vertexUsefulBytes,
                indexUsefulBytes = graph.footprint.indexUsefulBytes,
                uniformUsefulBytes = graph.footprint.uniformUsefulBytes,
                vertexCapacityBytes = graph.vertex.byteSize,
                indexCapacityBytes = graph.index.byteSize,
                uniformCapacityBytes = graph.uniform.byteSize,
                poolCapacities = poolCapacities,
                maxBufferSize = maxBufferSize,
                maxDynamicUniformBuffersPerPipelineLayout = maxDynamicUniformBuffers,
            )
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun memoryBudget(
        graph: RenderGraph,
        shape: W4bGraph,
        bounds: GPUPixelBounds,
        generation: GPUDeviceGenerationID,
        deviceLimitFacts: List<org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilityFact>,
    ): GPUFrameMemoryBudgetPlan? {
        val transient = try {
            Math.addExact(
                Math.addExact(shape.staging.byteSize, shape.vertex.byteSize),
                Math.addExact(shape.index.byteSize, shape.uniform.byteSize),
            )
        } catch (_: ArithmeticException) {
            return null
        }
        val peak = try { Math.addExact(shape.target.byteSize, transient) } catch (_: ArithmeticException) { return null }
        if (peak != graph.peakFrameLocalBytes || peak > graph.budget.maxFrameLocalBytes) return null
        val identity = w4bSessionIdentity(generation, bounds)
        val allocations = listOf(
            GPUFrameMemoryAllocation(
                "$identity.target",
                GPUFrameMemoryCategory.CanonicalTarget,
                shape.target.byteSize,
                GPUFrameMemoryResourceKind.Texture2D,
                bounds,
            ),
            GPUFrameMemoryAllocation(
                "$identity.staging",
                GPUFrameMemoryCategory.ReadbackStaging,
                shape.staging.byteSize,
                GPUFrameMemoryResourceKind.Buffer,
                null,
            ),
            GPUFrameMemoryAllocation(
                "$identity.vertex",
                GPUFrameMemoryCategory.ReusableScratch,
                shape.vertex.byteSize,
                GPUFrameMemoryResourceKind.Buffer,
                null,
            ),
            GPUFrameMemoryAllocation(
                "$identity.index",
                GPUFrameMemoryCategory.ReusableScratch,
                shape.index.byteSize,
                GPUFrameMemoryResourceKind.Buffer,
                null,
            ),
            GPUFrameMemoryAllocation(
                "$identity.uniform",
                GPUFrameMemoryCategory.ReusableScratch,
                shape.uniform.byteSize,
                GPUFrameMemoryResourceKind.Buffer,
                null,
            ),
        )
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

    private fun w4bSessionIdentity(generation: GPUDeviceGenerationID, bounds: GPUPixelBounds): String =
        "w4b.session.${generation.value}.${bounds.width}x${bounds.height}.rgba8unorm-srgb"

    private fun Int.isPositivePowerOfTwo(): Boolean = this > 0 && this and (this - 1) == 0
    private fun Long.isPositivePowerOfTwo(): Boolean = this > 0L && this and (this - 1L) == 0L

    private data class W4bGraph(
        val target: PlanResource,
        val staging: PlanResource,
        val vertex: PlanResource,
        val index: PlanResource,
        val uniform: PlanResource,
        val render: PlanPass.RenderPass,
        val readback: PlanPass.ReadbackPass,
        val draws: List<AnalyticRRectDraw>,
        val footprint: AnalyticRRectMemoryFootprint,
    )

    private data class W4bBuiltPacket(
        val packet: GPUDrawPacket,
        val scratchDraw: W4bSessionScratchDrawV1,
        val structuralPipelineKey: GPUCorePrimitiveRenderPipelineStructuralKey,
    )

    private fun invalid(message: String): GpuPlanLoweringResult.InvalidPlan =
        GpuPlanLoweringResult.InvalidPlan(diagnostic("w4b.lowering.incompatible_plan", RenderDiagnosticDomain.RESOURCE, message))

    private fun capability(
        code: RenderDiagnosticCode,
        message: String,
    ): GpuPlanLoweringResult.UnsupportedCapability = GpuPlanLoweringResult.UnsupportedCapability(
        diagnostic(code.value, RenderDiagnosticDomain.CAPABILITY, message),
    )

    private fun diagnostic(
        code: String,
        domain: RenderDiagnosticDomain,
        message: String,
    ): RenderDiagnostic = RenderDiagnostic(
        RenderDiagnosticCode(code),
        domain,
        RenderDiagnosticSeverity.ERROR,
        message,
    )
}
