package org.graphiks.kanvas.gpu.renderer.planning

import org.graphiks.kanvas.gpu.plan.AttachmentLoadPlan
import org.graphiks.kanvas.gpu.plan.AttachmentStorePlan
import org.graphiks.kanvas.gpu.plan.BlendPlan
import org.graphiks.kanvas.gpu.plan.CoveragePlan
import org.graphiks.kanvas.gpu.plan.PlanLogicalColorFormat
import org.graphiks.kanvas.gpu.plan.PlanPass
import org.graphiks.kanvas.gpu.plan.PlanResource
import org.graphiks.kanvas.gpu.plan.PlanResourceKind
import org.graphiks.kanvas.gpu.plan.PlanResourceLifetime
import org.graphiks.kanvas.gpu.plan.PlanResourceRole
import org.graphiks.kanvas.gpu.plan.PlanResourceUsage
import org.graphiks.kanvas.gpu.plan.PlanTextureFormat
import org.graphiks.kanvas.gpu.plan.MaterialPlanRef
import org.graphiks.kanvas.gpu.plan.MaterialPlanTable
import org.graphiks.kanvas.gpu.plan.PlanDrawMaterialAuthority
import org.graphiks.kanvas.gpu.plan.RenderGraph
import org.graphiks.kanvas.gpu.plan.SamplePlan
import org.graphiks.kanvas.gpu.plan.SolidRectDraw
import org.graphiks.kanvas.gpu.plan.PlanDraw
import org.graphiks.kanvas.gpu.plan.W5bPointDraw
import org.graphiks.kanvas.gpu.plan.W5bCorePrimitiveGraph
import org.graphiks.kanvas.gpu.plan.W3SolidRectPlanCompiler
import org.graphiks.kanvas.gpu.plan.W3PlanDiagnostics
import org.graphiks.kanvas.gpu.plan.W4aAnalyticRectPlanCompiler
import org.graphiks.kanvas.gpu.plan.W4bAnalyticRRectPlanCompiler
import org.graphiks.kanvas.gpu.plan.W4cPathFillPlanCompiler
import org.graphiks.kanvas.gpu.plan.W4dPathStrokePlanCompiler
import org.graphiks.kanvas.gpu.plan.W4dGeneralPathPlanCompiler
import org.graphiks.kanvas.gpu.plan.W4eClipPlanCompiler
import org.graphiks.kanvas.gpu.renderer.analysis.corePrimitiveRectGeometryAuthority
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.clips.GPUBounds
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoveragePlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionPlan
import org.graphiks.kanvas.gpu.renderer.color.GPUColorFormat
import org.graphiks.kanvas.gpu.renderer.color.GPUColorInterpretation
import org.graphiks.kanvas.gpu.renderer.commands.GPUFrameProvenance
import org.graphiks.kanvas.gpu.renderer.commands.GPUTransformFacts
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketID
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketRole
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchEligibility
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchKind
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchQueueGuard
import org.graphiks.kanvas.gpu.renderer.passes.GPURenderStepID
import org.graphiks.kanvas.gpu.renderer.passes.GPUSamplePlan
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitivePreparedPacketAuthority
import org.graphiks.kanvas.gpu.renderer.passes.W3SessionScratchV1
import org.graphiks.kanvas.gpu.renderer.passes.canonicalIdentity
import org.graphiks.kanvas.gpu.renderer.passes.corePrimitiveRenderPipelineStructuralKey
import org.graphiks.kanvas.gpu.renderer.passes.corePrimitiveStructuralColorFormat
import org.graphiks.kanvas.gpu.renderer.payloads.CORE_PRIMITIVE_RENDER_STEP_IDENTITY
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveCoverageMode
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometryInput
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitivePayloadGatherer
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitivePayloadInput
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveRectRouteAuthority
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveSourceFamily
import org.graphiks.kanvas.gpu.renderer.pipelines.GPURenderPipelineKey
import org.graphiks.kanvas.gpu.renderer.recording.CORE_PRIMITIVE_BINDING_LAYOUT_HASH
import org.graphiks.kanvas.gpu.renderer.recording.CORE_PRIMITIVE_RENDER_PIPELINE_KEY
import org.graphiks.kanvas.gpu.renderer.recording.CORE_PRIMITIVE_VERTEX_SOURCE_LABEL
import org.graphiks.kanvas.gpu.renderer.recording.GPUCorePrimitivePreparedFrameResult
import org.graphiks.kanvas.gpu.renderer.recording.GPUW5aCompositeLaneWitnessV1
import org.graphiks.kanvas.gpu.renderer.recording.GPUCorePrimitivePreparedFrameTaskListAssembler
import org.graphiks.kanvas.gpu.renderer.recording.GPUCorePrimitivePreplannedFrameRequest
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
import org.graphiks.kanvas.gpu.renderer.recording.corePrimitiveScissorAuthority
import org.graphiks.kanvas.gpu.renderer.recording.corePrimitiveTargetStateHash
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryAllocation
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryBudgetPlan
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryCategory
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryResourceKind
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceLifetime
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTextureDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourcePreparationRequest
import org.graphiks.kanvas.gpu.renderer.resources.GPUUniformSlabPayload
import org.graphiks.kanvas.gpu.renderer.resources.GPUUniformSlabPlanner
import org.graphiks.kanvas.gpu.renderer.resources.GPUUniformSlabPlanningResult
import org.graphiks.kanvas.gpu.renderer.resources.corePrimitiveFramePoolCapacitiesOrNull
import org.graphiks.kanvas.gpu.renderer.state.GPULoadStorePlan
import org.graphiks.kanvas.gpu.renderer.state.GPUStorePlan
import org.graphiks.kanvas.render.ir.RenderDiagnostic
import org.graphiks.kanvas.render.ir.RenderDiagnosticCode
import org.graphiks.kanvas.render.ir.RenderDiagnosticDomain
import org.graphiks.kanvas.render.ir.RenderDiagnosticSeverity
import org.graphiks.math.color.ColorF32

/** Converts closed W3 through W4d.2 graphs into prepared frame tasks without invoking legacy planning. */
public class GpuPlanTaskListLowerer {
    public fun lower(request: GpuPlanLoweringRequest): GpuPlanLoweringResult {
        val current = when (val adapted = request.capabilities.toPlanCapabilitySnapshot(request.deviceGeneration)) {
            is GpuPlanCapabilityAdapterResult.Supported -> adapted.snapshot
            is GpuPlanCapabilityAdapterResult.Unsupported -> return GpuPlanLoweringResult.UnsupportedCapability(adapted.diagnostic)
        }
        if (request.graph.capabilities != current) return unsupported("The graph capability snapshot is stale.")
        if (request.graph.budget != request.currentBudget) return invalid("The graph budget is stale.")
        if (request.graph.verifyW5bGeometryCompilerWitness() && request.graph.visualCommandCount == 0)
            return lowerW3(request, current)
        return when (request.graph.capabilityId) {
            W4bAnalyticRRectPlanCompiler.W5B_CAPABILITY_ID -> W5bAnalyticRRectGraphLowerer().lower(request)
            org.graphiks.kanvas.gpu.plan.W5bGeometryLanePlanV3.COMPOSITE_CAPABILITY_ID,
            W4dPathStrokePlanCompiler.W5B_CAPABILITY_ID,
            W4cPathFillPlanCompiler.W5B_CAPABILITY_ID -> W5bNativeGeometryGraphLowerer().lower(request)
            W4aAnalyticRectPlanCompiler.W5B_CAPABILITY_ID -> W5bAnalyticRectGraphLowerer().lower(request)
            org.graphiks.kanvas.gpu.plan.W5aCompositePlanCompiler.CAPABILITY_ID ->
                W5aCompositeGraphLowerer().lower(request)
            W3SolidRectPlanCompiler.CAPABILITY_ID,
            W3SolidRectPlanCompiler.W5A_CAPABILITY_ID,
            W5bCorePrimitiveGraph.CAPABILITY_ID,
            -> lowerW3(request, current)
            W4aAnalyticRectPlanCompiler.HISTORICAL_CAPABILITY_ID,
            W4aAnalyticRectPlanCompiler.CAPABILITY_ID,
            -> W4aAnalyticRectGraphLowerer().lower(request)
            W4bAnalyticRRectPlanCompiler.HISTORICAL_CAPABILITY_ID,
            W4bAnalyticRRectPlanCompiler.CAPABILITY_ID,
            -> W4bAnalyticRRectGraphLowerer().lower(request)
            W4cPathFillPlanCompiler.HISTORICAL_CAPABILITY_ID,
            W4cPathFillPlanCompiler.CAPABILITY_ID,
            -> W4cPathFillGraphLowerer().lower(request)
            W4dPathStrokePlanCompiler.HISTORICAL_CAPABILITY_ID,
            W4dPathStrokePlanCompiler.CAPABILITY_ID,
            -> W4dPathStrokeGraphLowerer().lower(request)
            W4dGeneralPathPlanCompiler.HARD_CAPABILITY_ID,
            W4dGeneralPathPlanCompiler.AA_CAPABILITY_ID,
            W4dGeneralPathPlanCompiler.W5A_HARD_CAPABILITY_ID,
            W4dGeneralPathPlanCompiler.W5A_AA_CAPABILITY_ID,
            -> W4dGeneralPathGraphLowerer().lower(request)
            W4eClipPlanCompiler.HARD_CAPABILITY_ID,
            W4eClipPlanCompiler.AA_CAPABILITY_ID,
            W4eClipPlanCompiler.W5A_HARD_CAPABILITY_ID,
            W4eClipPlanCompiler.W5A_AA_CAPABILITY_ID,
            -> W4eClipGraphLowerer().lower(request)
            else -> invalid("Unknown gpu-plan capability id.")
        }
    }

    private fun lowerW3(
        request: GpuPlanLoweringRequest,
        current: org.graphiks.kanvas.gpu.plan.PlanCapabilitySnapshot,
    ): GpuPlanLoweringResult {
        val graph = validateW3Graph(request.graph) ?: return invalid("The graph is not the exact W3 topology.")
        if (graph.staging.byteSize > current.maxBufferSizeBytes) {
            return capability(
                W3PlanDiagnostics.CapabilityBufferSize,
                "W3 readback staging exceeds the sealed device buffer limit.",
            )
        }
        return lowerGraph(request, graph)
    }

    private fun lowerGraph(request: GpuPlanLoweringRequest, graph: W3Graph): GpuPlanLoweringResult = try {
        val targetBounds = GPUPixelBounds(0, 0, request.graph.targetExtent.width, request.graph.targetExtent.height)
        val sessionIdentity = request.w5aCompositeSessionIdentity
            ?: request.w5bPreparedTarget?.value?.removeSuffix(".target")
            ?: "w3.session.${request.deviceGeneration.value}.${request.graph.targetExtent.width}x${request.graph.targetExtent.height}.rgba8unorm-srgb"
        val target = request.w5bPreparedTarget ?: GPUFrameTargetRef("$sessionIdentity.target")
        val staging = GPUFrameBufferRef("$sessionIdentity.staging")
        val targetPreparation = GPUResourcePreparationRequest(target, GPUFrameTextureDescriptor(targetBounds, GPUColorFormat.RGBA8UnormSrgb, 1), GPUFrameResourceRole.SceneTarget, setOf(GPUFrameResourceUsage.RenderAttachment, GPUFrameResourceUsage.CopySource), GPUFrameResourceLifetime.FrameLocal, graph.target.byteSize, "$sessionIdentity.target")
        val stagingPreparation = GPUResourcePreparationRequest(staging, GPUFrameBufferDescriptor(graph.staging.byteSize, request.graph.capabilities.copyBytesPerRowAlignment.toLong()), GPUFrameResourceRole.ReadbackStaging, setOf(GPUFrameResourceUsage.CopyDestination, GPUFrameResourceUsage.MapRead), GPUFrameResourceLifetime.FrameLocal, graph.staging.byteSize, "$sessionIdentity.staging")
        val memory = memoryBudget(request.capabilities, request.graph, graph, targetBounds, request.deviceGeneration,
            request.w5aCompositeSessionIdentity ?: request.w5bPreparedTarget?.value?.removeSuffix(".target"))
            ?: return invalid("The graph memory facts cannot be represented by the renderer.")
        val readback = GPUFrameReadbackRequest(GPUReadbackRequestID("w3.${request.graph.id.value}.readback"), targetBounds, GPUReadbackPixelFormat.Rgba8Unorm, GPUColorInterpretation.EncodedPremulSrgb)
        val base = when (val rendered = renderOnlyTaskList(request, graph, target, staging, targetBounds, memory,
            targetPreparation, stagingPreparation, readback)) {
            is W3BaseTaskListResult.Ready -> rendered.taskList
            is W3BaseTaskListResult.Unsupported -> return GpuPlanLoweringResult.UnsupportedCapability(rendered.diagnostic)
            is W3BaseTaskListResult.Invalid -> return GpuPlanLoweringResult.InvalidPlan(rendered.diagnostic)
        }
        when (val assembled = GPUCorePrimitivePreparedFrameTaskListAssembler().buildPreplanned(
            GPUCorePrimitivePreplannedFrameRequest(request.graph.id, base, target, targetBounds, targetPreparation, staging, stagingPreparation, readback, memory, graph.render.id, graph.readback.id,
                request.w5aCompositeSessionIdentity?.let { GPUW5aCompositeLaneWitnessV1(it, requireNotNull(request.w5aCompositeLaneOrdinal), request.graph.id.value, base.tasks.filterIsInstance<GPUTask.Render>().flatMap(GPUTask.Render::drawPackets).map(GPUDrawPacket::packetId)) }, graph.destinationGraph),
        )) {
            is GPUCorePrimitivePreparedFrameResult.Recorded -> GpuPlanLoweringResult.Lowered(assembled.taskList, readback.requestId.value)
            is GPUCorePrimitivePreparedFrameResult.Refused -> invalid(assembled.diagnostic.message)
        }
    } catch (error: IllegalArgumentException) {
        invalid(error.message ?: "The graph cannot be lowered into W3 renderer values.")
    }

    private fun renderOnlyTaskList(
        request: GpuPlanLoweringRequest,
        graph: W3Graph,
        target: GPUFrameTargetRef,
        staging: GPUFrameBufferRef,
        targetBounds: GPUPixelBounds,
        memory: GPUFrameMemoryBudgetPlan,
        targetPreparation: GPUResourcePreparationRequest,
        stagingPreparation: GPUResourcePreparationRequest,
        readback: GPUFrameReadbackRequest,
    ): W3BaseTaskListResult {
        val packets = mutableListOf<GPUDrawPacket>()
        graph.draws.forEachIndexed { paintOrder, draw ->
            val color = resolveMaterialColor(graph.materialPlanTable, draw.materialAuthority)
                ?: return W3BaseTaskListResult.Invalid(invalidDiagnostic("W5 material authority is invalid."))
            packets += packet(draw, color, paintOrder, targetBounds, graph.materialPlanTable,
                request.w5bPreparedSemantics[draw.commandIndex])
        }
        val replay = "w3:${request.graph.id.value}"
        val seal = GPUFrameCapabilitySeal.capture(request.frameId, request.deviceGeneration, request.capabilities)
        if (graph.draws.isEmpty()) {
            val witness = org.graphiks.kanvas.gpu.renderer.passes.W5bClearOnlyFrameWitnessV3(
                requireNotNull(graph.destinationGraph), target, staging, seal,
                GPURecordingSeal(request.recordingId, 0L, replay, replay, seal.sealHash), memory,
                targetPreparation, stagingPreparation, readback)
            val render = GPUTask.Render(witness.clearTaskId,
                request.recordingId, GPUTaskPhase.Render, target, GPULoadStorePlan("clear", GPUStorePlan.Store),
                GPUSamplePlan.SingleSampleFrame, drawPackets = emptyList(), batchEligibilityByPacketId = emptyMap(),
                w5bInitialClearV3 = org.graphiks.kanvas.gpu.renderer.passes.W5bInitialClearV3(witness))
            return W3BaseTaskListResult.Ready(GPUTaskList(request.frameId, seal,
                listOf(GPURecordingSeal(request.recordingId, 0L, replay, replay, seal.sealHash)), replay,
                listOf(render), emptyList(), GPUTaskPhase.entries, memory))
        }
        val scratch = when (val sealed = sealW3Scratch(request, target, staging, targetBounds, seal.sealHash, packets)) {
            is W3SessionScratchSealResult.Sealed -> sealed.scratch
            is W3SessionScratchSealResult.Unsupported -> return W3BaseTaskListResult.Unsupported(sealed.diagnostic)
            is W3SessionScratchSealResult.Invalid -> return W3BaseTaskListResult.Invalid(sealed.diagnostic)
        }
        val w5bWitness = graph.destinationGraph?.let {
            val clip = graph.draws.filterIsInstance<W5bPointDraw>().mapNotNull { point -> point.clipOnly }.distinct().singleOrNull()
            org.graphiks.kanvas.gpu.renderer.passes.W5bPreparedFrameWitnessV3(it,
                org.graphiks.kanvas.gpu.renderer.passes.W5bGeometryScratchV3.Direct(scratch), seal,
                GPURecordingSeal(request.recordingId, 0L, replay, replay, seal.sealHash), memory,
                targetPreparation, stagingPreparation, readback, packets,
                clip?.let { plan -> W4eClipGraphLowerer().lowerClipOnly(request, plan) })
        }
        packets.forEach { packet ->
            val semantic = packet.semanticPayload as? org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload.CorePrimitive
                ?: return W3BaseTaskListResult.Invalid(invalidDiagnostic("W3 packet is missing CorePrimitive semantic authority."))
            val clip = packet.clipExecutionPlan
                ?: return W3BaseTaskListResult.Invalid(invalidDiagnostic("W3 packet is missing clip authority."))
            val blend = packet.blendPlan
                ?: return W3BaseTaskListResult.Invalid(invalidDiagnostic("W3 packet is missing blend authority."))
            val pipeline = packet.renderPipelineKey
                ?: return W3BaseTaskListResult.Invalid(invalidDiagnostic("W3 packet is missing render pipeline authority."))
            packet.attachCorePrimitivePreparedAuthority(
                if (w5bWitness != null) GPUCorePrimitivePreparedPacketAuthority.plannedW5b(
                    corePrimitiveRenderPipelineStructuralKey(semantic, clip, blend, 1,
                        GPUColorFormat.RGBA8UnormSrgb.corePrimitiveStructuralColorFormat()), pipeline, w5bWitness,
                ) else GPUCorePrimitivePreparedPacketAuthority.plannedW3(
                    structuralPipelineKey = corePrimitiveRenderPipelineStructuralKey(
                        semantic,
                        clip,
                        blend,
                        sampleCount = 1,
                        colorFormat = GPUColorFormat.RGBA8UnormSrgb.corePrimitiveStructuralColorFormat(),
                    ),
                    renderPipelineKey = pipeline,
                    scratch = scratch,
                ),
            )
        }
        val render = GPUTask.Render(GPUTaskID("task.w3.${request.graph.id.value}.base-render"), request.recordingId, GPUTaskPhase.Render, target, GPULoadStorePlan("clear", GPUStorePlan.Store), GPUSamplePlan.SingleSampleFrame, drawPackets = packets, batchEligibilityByPacketId = packets.associate { packet -> packet.packetId to GPUPassBatchEligibility(kind = GPUPassBatchKind.SolidFill, queueGuard = GPUPassBatchQueueGuard(emptyList(), emptyList())) })
        return W3BaseTaskListResult.Ready(
            GPUTaskList(request.frameId, seal, listOf(GPURecordingSeal(request.recordingId, 0L, replay, replay, seal.sealHash)), replay, listOf(render), emptyList(), GPUTaskPhase.entries, memory),
        )
    }

    internal fun sealW3Scratch(
        request: GpuPlanLoweringRequest,
        target: GPUFrameTargetRef,
        staging: GPUFrameBufferRef,
        targetBounds: GPUPixelBounds,
        capabilitySealHash: String,
        packets: List<GPUDrawPacket>,
    ): W3SessionScratchSealResult {
        val limits = request.capabilities.limits ?: return W3SessionScratchSealResult.Unsupported(
            capabilityDiagnostic(W3PlanDiagnostics.CapabilityBufferSize, "W3 scratch requires observed renderer limits."),
        )
        val maxBufferSize = limits.maxBufferSize ?: return W3SessionScratchSealResult.Unsupported(
            capabilityDiagnostic(W3PlanDiagnostics.CapabilityBufferSize, "W3 scratch requires an observed maxBufferSize."),
        )
        val maxDynamicUniformBuffers = limits.maxDynamicUniformBuffersPerPipelineLayout
            ?.takeIf { it >= 1L }
            ?: return W3SessionScratchSealResult.Unsupported(
                capabilityDiagnostic(
                    W3PlanDiagnostics.CapabilityDynamicUniform,
                    "W3 scratch requires at least one dynamic uniform buffer binding.",
                ),
            )
        val payloads = packets.map { packet ->
            val semantic = packet.semanticPayload as? org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload.CorePrimitive
                ?: return W3SessionScratchSealResult.Invalid(invalidDiagnostic("W3 packet is missing CorePrimitive semantic authority."))
            GPUUniformSlabPayload(
                "w3.draw.${packet.commandIdValue}",
                semantic.payloadRef.uniformBlock?.bytes?.map(Int::toByte)?.toByteArray()
                    ?: return W3SessionScratchSealResult.Invalid(invalidDiagnostic("W3 packet uniform payload is missing.")),
            )
        }
        val plan = when (val result = GPUUniformSlabPlanner.plan(
            sourceLabel = W3SessionScratchV1.SOURCE_LABEL,
            deviceGeneration = request.deviceGeneration.value,
            alignmentBytes = limits.minUniformBufferOffsetAlignment,
            uploadBudgetBytes = maxBufferSize,
            payloads = payloads,
            maxBufferSize = maxBufferSize,
            maxDynamicUniformBuffersPerPipelineLayout = maxDynamicUniformBuffers,
        )) {
            is GPUUniformSlabPlanningResult.Accepted -> result.plan
            is GPUUniformSlabPlanningResult.Refused -> return when (result.diagnostic.code) {
                "unsupported.uniform_slab_dynamic_uniform_unavailable" -> W3SessionScratchSealResult.Unsupported(
                    capabilityDiagnostic(W3PlanDiagnostics.CapabilityDynamicUniform, "W3 dynamic uniform support is unavailable."),
                )
                "unsupported.uniform_slab_max_buffer_size_exceeded",
                "unsupported.uniform_slab_budget_exceeded" -> W3SessionScratchSealResult.Unsupported(
                    capabilityDiagnostic(W3PlanDiagnostics.CapabilityBufferSize, "W3 uniform scratch exceeds the device buffer limit."),
                )
                else -> W3SessionScratchSealResult.Invalid(
                    invalidDiagnostic("W3 uniform scratch plan is invalid: ${result.diagnostic.code}."),
                )
            }
        }
        val first = packets.firstOrNull() ?: return W3SessionScratchSealResult.Invalid(
            invalidDiagnostic("W3 scratch requires at least one packet."),
        )
        val geometryBytes = packets.map { packet ->
            when (val geometry = (packet.semanticPayload as org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload.CorePrimitive).geometry) {
                is org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometry.TriangulatedPath ->
                    geometry.vertices.size.toLong() * 4L to geometry.indices.size.toLong() * 4L
                else -> 32L to 24L
            }
        }
        val vertexBytes = geometryBytes.sumOf { it.first }
        val indexBytes = geometryBytes.sumOf { it.second }
        val poolCapacities = corePrimitiveFramePoolCapacitiesOrNull(
            vertexBytes,
            indexBytes,
            plan.totalBytes,
        ) ?: return W3SessionScratchSealResult.Invalid(
            invalidDiagnostic("W3 pooled scratch capacity is invalid."),
        )
        val scratch = try {
            W3SessionScratchV1(
                planId = request.graph.id.value,
                capabilitySealHash = capabilitySealHash,
                deviceGeneration = request.deviceGeneration.value,
                target = target,
                staging = staging,
                targetBounds = targetBounds,
                packetIds = packets.map(GPUDrawPacket::packetId),
                commandIds = packets.map(GPUDrawPacket::commandIdValue),
                packetStructuralPipelineKeys = packets.map { packet ->
                    val semantic = packet.semanticPayload as? org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload.CorePrimitive
                        ?: throw IllegalArgumentException("W3 packet is missing CorePrimitive semantic authority.")
                    val clip = packet.clipExecutionPlan
                        ?: throw IllegalArgumentException("W3 packet is missing clip authority.")
                    val blend = packet.blendPlan
                        ?: throw IllegalArgumentException("W3 packet is missing blend authority.")
                    corePrimitiveRenderPipelineStructuralKey(
                        semantic, clip, blend, sampleCount = 1,
                        colorFormat = GPUColorFormat.RGBA8UnormSrgb.corePrimitiveStructuralColorFormat(),
                    )
                },
                uniformPlan = plan,
                maxBufferSize = maxBufferSize,
                maxDynamicUniformBuffersPerPipelineLayout = maxDynamicUniformBuffers,
                vertexBytes = vertexBytes,
                indexBytes = indexBytes,
                poolCapacities = poolCapacities,
                packetGeometryBytes = geometryBytes,
            )
        } catch (_: IllegalArgumentException) {
            return W3SessionScratchSealResult.Invalid(invalidDiagnostic("W3 scratch packing is invalid."))
        }
        return if (scratch.fitsDeviceLimits(maxBufferSize, maxDynamicUniformBuffers)) {
            W3SessionScratchSealResult.Sealed(scratch)
        } else {
            W3SessionScratchSealResult.Unsupported(
                capabilityDiagnostic(W3PlanDiagnostics.CapabilityBufferSize, "W3 scratch exceeds the sealed device limits."),
            )
        }
    }

    internal fun packet(draw: PlanDraw, color: ColorF32, paintOrder: Int, target: GPUPixelBounds, materialPlanTable: MaterialPlanTable?,
        prepared: org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload.CorePrimitive?): GPUDrawPacket {
        val bounds = when (draw) { is SolidRectDraw -> draw.copyVisibleBounds(); is W5bPointDraw -> draw.copyBoundsI32(); else -> error("Unknown direct geometry") }
        val scissor = when (draw) { is SolidRectDraw -> draw.copyScissor(); is W5bPointDraw -> draw.copyScissorI32(); else -> error("Unknown direct geometry") }
        require(bounds.roundTripsExactlyThroughF32() && scissor.roundTripsExactlyThroughF32()) {
            "W3 planned I32 bounds cannot round-trip exactly through renderer F32 values."
        }
        val rect = org.graphiks.kanvas.gpu.renderer.commands.GPURect(bounds.left.toFloat(), bounds.top.toFloat(), bounds.right.toFloat(), bounds.bottom.toFloat())
        val scissorBounds = GPUPixelBounds(scissor.left, scissor.top, scissor.right, scissor.bottom)
        val clip = if (scissorBounds == target) GPUClipCoveragePlan.NoClip else GPUClipCoveragePlan.Scissor(GPUBounds(scissor.left.toFloat(), scissor.top.toFloat(), scissor.right.toFloat(), scissor.bottom.toFloat()))
        val execution = if (scissorBounds == target) GPUClipExecutionPlan.NoClip else GPUClipExecutionPlan.ScissorOnly(scissorBounds)
        val blend = W5bBlendPlanLowerer.lower(draw.blend)
        val analysisRecordId = "analysis.fill_rect.${draw.commandIndex}"
        val semantic = if (draw is W5bPointDraw) {
            val authentic = requireNotNull(prepared)
            val geometry = authentic.geometry as org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometry.TriangulatedPath
            require(authentic.hasStructuralIntegrity() && authentic.sourceFamily == GPUCorePrimitiveSourceFamily.PointLine &&
                geometry.vertices == draw.copyVerticesF32().toList() && geometry.indices == draw.copyIndicesI32().toList() &&
                geometry.sourceContourStarts == draw.copyContourStartsI32().toList())
            GPUCorePrimitivePayloadGatherer().gatherSemantic(GPUCorePrimitivePayloadInput(draw.commandIndex,
                GPUCorePrimitiveSourceFamily.PointLine, GPUCorePrimitiveGeometryInput.TriangulatedPath(
                    geometry.vertices, geometry.indices, geometry.sourceContourStarts, geometry.sourceVertexCount,
                    geometry.coverBounds, geometry.geometryMode, geometry.fillRule, geometry.inverseFill,
                    geometry.strokeStyle, geometry.sourceAuthority), listOf(color.red, color.green, color.blue, color.alpha),
                target, scissorBounds, clip, execution.canonicalIdentity(), blend.canonicalIdentity(),
                GPUFrameProvenance.None, GPUCorePrimitiveCoverageMode.FullOrScissor, authentic.analysisRecordId,
                authentic.analysisCommandFamily, material = W5aMaterialPlanLowerer().material(materialPlanTable, draw.materialAuthority, draw.commandIndex)))
        } else GPUCorePrimitivePayloadGatherer().gatherSemantic(GPUCorePrimitivePayloadInput(draw.commandIndex, GPUCorePrimitiveSourceFamily.Rect, GPUCorePrimitiveGeometryInput.Rect(rect.left, rect.top, rect.right, rect.bottom), listOf(color.red, color.green, color.blue, color.alpha), target, scissorBounds, clip, execution.canonicalIdentity(), blend.canonicalIdentity(), GPUFrameProvenance.None, GPUCorePrimitiveCoverageMode.FullOrScissor, analysisRecordId, "FillRect", GPUCorePrimitiveRectRouteAuthority.RectAxisAligned, corePrimitiveRectGeometryAuthority(rect, GPUTransformFacts.identity()), material = W5aMaterialPlanLowerer().material(materialPlanTable, draw.materialAuthority, draw.commandIndex)))
        val structuralKey = corePrimitiveRenderPipelineStructuralKey(
            semantic,
            execution,
            blend,
            sampleCount = 1,
            colorFormat = GPUColorFormat.RGBA8UnormSrgb.corePrimitiveStructuralColorFormat(),
        )
        return GPUDrawPacket(GPUDrawPacketID("packet.w3.${draw.commandIndex}"), draw.commandIndex, analysisRecordId, "pass.w3.main", "root", "binding.w3.${draw.commandIndex}", "w3-solid-rect", paintOrder.toLong(), "paint-order:$paintOrder", GPURenderStepID(CORE_PRIMITIVE_RENDER_STEP_IDENTITY), 1, GPUDrawPacketRole.Shading, blend, structuralKey.stableRenderPipelineKey(CORE_PRIMITIVE_RENDER_PIPELINE_KEY), bindingLayoutHash = CORE_PRIMITIVE_BINDING_LAYOUT_HASH, uniformSlot = semantic.payloadRef.uniformSlot, semanticPayload = semantic, vertexSourceLabel = CORE_PRIMITIVE_VERTEX_SOURCE_LABEL, scissorBoundsHash = corePrimitiveScissorAuthority(scissorBounds), targetStateHash = corePrimitiveTargetStateHash(1, GPUColorFormat.RGBA8UnormSrgb), originalPaintOrder = paintOrder, resourceGeneration = PREPARED_FRAME_LATE_BOUND_RESOURCE_GENERATION, frameProvenance = GPUFrameProvenance.None, clipCoveragePlan = clip, clipExecutionPlan = execution)
    }

    private fun memoryBudget(capabilities: GPUCapabilities, graph: RenderGraph, shape: W3Graph, bounds: GPUPixelBounds, generation: org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID, compositeSessionIdentity: String?): GPUFrameMemoryBudgetPlan? {
        val limits = capabilities.limits ?: return null
        val pointClip = shape.draws.filterIsInstance<W5bPointDraw>().mapNotNull { it.clipOnly }.distinct().singleOrNull()
        if (pointClip != null) {
            val identity = compositeSessionIdentity ?: "w3.session.${generation.value}.${bounds.width}x${bounds.height}.rgba8unorm-srgb"
            val allocations = graph.resources().map { resource -> GPUFrameMemoryAllocation(
                when (resource.role) {
                    PlanResourceRole.LogicalTarget -> "$identity.target"
                    PlanResourceRole.DestinationSnapshot -> "$identity.snapshot"
                    PlanResourceRole.ReadbackStaging -> "$identity.staging"
                    else -> "w4e.${resource.id.value}"
                }, when (resource.role) {
                    PlanResourceRole.LogicalTarget -> GPUFrameMemoryCategory.CanonicalTarget
                    PlanResourceRole.DestinationSnapshot -> GPUFrameMemoryCategory.DestinationSnapshot
                    PlanResourceRole.ReadbackStaging -> GPUFrameMemoryCategory.ReadbackStaging
                    else -> GPUFrameMemoryCategory.ReusableScratch
                }, resource.byteSize,
                if (resource.kind == org.graphiks.kanvas.gpu.plan.PlanResourceKind.Buffer) GPUFrameMemoryResourceKind.Buffer else GPUFrameMemoryResourceKind.Texture2D,
                if (resource.kind == org.graphiks.kanvas.gpu.plan.PlanResourceKind.Buffer) null else bounds) }
            return org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryBudgetPlanner.plan(
                org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryBudgetRequest(allocations,
                    graph.budget.maxFrameLocalBytes, limits)).takeIf { it.diagnostic == null &&
                    it.targetResidentBytes + it.peakFrameTransientBytes == graph.peakFrameLocalBytes }
        }
        val snapshotBytes = if (graph.resources().any { it.role == PlanResourceRole.DestinationSnapshot }) shape.target.byteSize else 0L
        val transientBytesI64 = try { Math.addExact(shape.staging.byteSize, snapshotBytes) } catch (_: ArithmeticException) { return null }
        val totalBytesI64 = try { Math.addExact(shape.target.byteSize, transientBytesI64) } catch (_: ArithmeticException) { return null }
        if (totalBytesI64 != graph.peakFrameLocalBytes || graph.peakFrameLocalBytes > graph.budget.maxFrameLocalBytes) return null
        val identity = compositeSessionIdentity ?: "w3.session.${generation.value}.${bounds.width}x${bounds.height}.rgba8unorm-srgb"
        val target = GPUFrameMemoryAllocation("$identity.target", GPUFrameMemoryCategory.CanonicalTarget, shape.target.byteSize, GPUFrameMemoryResourceKind.Texture2D, bounds)
        val staging = GPUFrameMemoryAllocation("$identity.staging", GPUFrameMemoryCategory.ReadbackStaging, shape.staging.byteSize, GPUFrameMemoryResourceKind.Buffer, null)
        val snapshots = if (snapshotBytes == 0L) emptyList() else listOf(GPUFrameMemoryAllocation("$identity.snapshot", GPUFrameMemoryCategory.DestinationSnapshot, snapshotBytes, GPUFrameMemoryResourceKind.Texture2D, bounds))
        return GPUFrameMemoryBudgetPlan(transientBytesI64, shape.target.byteSize, GPUFrameMemoryCategory.entries.associateWith { category -> when (category) { GPUFrameMemoryCategory.CanonicalTarget -> shape.target.byteSize; GPUFrameMemoryCategory.ReadbackStaging -> shape.staging.byteSize; GPUFrameMemoryCategory.DestinationSnapshot -> snapshotBytes; else -> 0L } }, limits.capabilityFacts("frame-memory-budget"), graph.budget.maxFrameLocalBytes, null, listOf(target, staging) + snapshots)
    }

    private fun validateW3Graph(graph: RenderGraph): W3Graph? {
        val geometryClearOnly = graph.verifyW5bGeometryCompilerWitness() && graph.visualCommandCount == 0 &&
            graph.materialPlanTableOrNull() == null && graph.w5bGeometryLanes().isEmpty() && graph.passes().size == 2
        if ((!geometryClearOnly && graph.capabilityId !in setOf(W3SolidRectPlanCompiler.CAPABILITY_ID, W3SolidRectPlanCompiler.W5A_CAPABILITY_ID, W5bCorePrimitiveGraph.CAPABILITY_ID)) || graph.colorFormat != PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL) return null
        val resources = graph.resources()
        if (graph.capabilityId == W5bCorePrimitiveGraph.CAPABILITY_ID) {
            val target = resources.singleOrNull { it.role == PlanResourceRole.LogicalTarget } ?: return null
            val staging = resources.singleOrNull { it.role == PlanResourceRole.ReadbackStaging } ?: return null
            val renders = graph.passes().filterIsInstance<PlanPass.RenderPass>()
            val readback = graph.passes().lastOrNull() as? PlanPass.ReadbackPass ?: return null
            val draws = renders.flatMap { it.draws() }
            val clips = draws.filterIsInstance<W5bPointDraw>().mapNotNull { it.clipOnly }.distinct()
            if (clips.size > 1) return null
            val clip = clips.singleOrNull()
            if (clip != null && (graph.passes().take(clip.passes().size) != clip.passes() ||
                !clip.nativePayload.matchesDeclaredResources(resources))) return null
            if (draws.none { it is W5bPointDraw } || draws.any { it !is SolidRectDraw && it !is W5bPointDraw } ||
                draws.any { it.coverage != CoveragePlan.FullOrScissor } ||
                renders.any { it.target != target.id || it.destinationVersionAfter == null } ||
                graph.materialPlanTableOrNull() == null || resources.size !=
                2 + (if (draws.any { it.blend is BlendPlan.DestinationReadV1 }) 1 else 0) + (clip?.resources()?.size ?: 0)) return null
            return W3Graph(target, staging, renders.first(), readback, draws, graph.materialPlanTableOrNull(), graph)
        }
        if (resources.any { it.role == PlanResourceRole.DestinationSnapshot }) {
            val target = resources.singleOrNull { it.role == PlanResourceRole.LogicalTarget } ?: return null
            val staging = resources.singleOrNull { it.role == PlanResourceRole.ReadbackStaging } ?: return null
            val snapshot = resources.singleOrNull { it.role == PlanResourceRole.DestinationSnapshot } ?: return null
            val passes = graph.passes()
            val renders = passes.filterIsInstance<PlanPass.RenderPass>()
            val readback = passes.lastOrNull() as? PlanPass.ReadbackPass ?: return null
            val draws = renders.flatMap { it.draws() }
            if (resources.size != 3 || snapshot.byteSize != target.byteSize || snapshot.copyExtent() != graph.targetExtent ||
                graph.materialPlanTableOrNull() == null || draws.any { it !is SolidRectDraw || it.coverage != CoveragePlan.FullOrScissor || it.sample != SamplePlan.SingleSample } ||
                renders.any { it.target != target.id || it.destinationVersionAfter == null } ||
                graph.dependencies() != passes.zipWithNext { before, after -> org.graphiks.kanvas.gpu.plan.PlanPassDependency(before.id, after.id) }) return null
            return W3Graph(target, staging, renders.first(), readback, draws.filterIsInstance<SolidRectDraw>(), graph.materialPlanTableOrNull(), graph)
        }
        if (resources.size != 2) return null
        val target = resources.singleOrNull { it.role == PlanResourceRole.LogicalTarget } ?: return null
        val staging = resources.singleOrNull { it.role == PlanResourceRole.ReadbackStaging } ?: return null
        val expectedTargetBytes = try { Math.multiplyExact(Math.multiplyExact(graph.targetExtent.width.toLong(), graph.targetExtent.height.toLong()), 4L) } catch (_: ArithmeticException) { return null }
        val expectedTarget = try {
            PlanResource.of(PlanResourceRole.LogicalTarget, 0, PlanResourceKind.Texture2D, PlanTextureFormat.Color(graph.colorFormat), graph.targetExtent, expectedTargetBytes, setOf(PlanResourceUsage.RenderAttachment, PlanResourceUsage.CopySource), PlanResourceLifetime.FrameLocal, 0, 2)
        } catch (_: IllegalArgumentException) { return null }
        if (target.id != expectedTarget.id || target.ordinal != 0 || target.kind != PlanResourceKind.Texture2D || target.format != PlanTextureFormat.Color(graph.colorFormat) || target.copyExtent() != graph.targetExtent || target.byteSize != expectedTargetBytes || target.usages() != setOf(PlanResourceUsage.RenderAttachment, PlanResourceUsage.CopySource) || target.lifetime != PlanResourceLifetime.FrameLocal || target.firstPassIndex != 0 || target.lastPassIndexExclusive != 2) return null
        val passes = graph.passes()
        val render = passes.getOrNull(0) as? PlanPass.RenderPass ?: return null
        val readback = passes.getOrNull(1) as? PlanPass.ReadbackPass ?: return null
        val widthBytes = try { Math.multiplyExact(graph.targetExtent.width.toLong(), 4L) } catch (_: ArithmeticException) { return null }
        val alignment = graph.capabilities.copyBytesPerRowAlignment.toLong()
        val expectedRow = try { Math.addExact(widthBytes, (alignment - widthBytes % alignment) % alignment) } catch (_: ArithmeticException) { return null }
        val expectedStaging = try { Math.multiplyExact(expectedRow, graph.targetExtent.height.toLong()) } catch (_: ArithmeticException) { return null }
        val expectedStagingResource = try {
            PlanResource.of(PlanResourceRole.ReadbackStaging, 0, PlanResourceKind.Buffer, null, null, expectedStaging, setOf(PlanResourceUsage.CopyDestination, PlanResourceUsage.MapRead), PlanResourceLifetime.FrameLocal, 1, 2)
        } catch (_: IllegalArgumentException) { return null }
        val expectedRenderId = PlanPass.RenderPass(0, expectedTarget.id, emptyList(), AttachmentLoadPlan.ClearTransparent, AttachmentStorePlan.Store).id
        val expectedReadbackId = PlanPass.ReadbackPass(0, expectedTarget.id, expectedStagingResource.id, expectedRow).id
        val planDraws = render.draws()
        if (planDraws.any { it !is SolidRectDraw }) return null
        val draws = planDraws.filterIsInstance<SolidRectDraw>()
        if (staging.id != expectedStagingResource.id || staging.ordinal != 0 || staging.kind != PlanResourceKind.Buffer || staging.format != null || staging.copyExtent() != null || staging.byteSize != expectedStaging || staging.usages() != setOf(PlanResourceUsage.CopyDestination, PlanResourceUsage.MapRead) || staging.lifetime != PlanResourceLifetime.FrameLocal || staging.firstPassIndex != 1 || staging.lastPassIndexExclusive != 2 || render.ordinal != 0 || readback.ordinal != 0 || render.id != expectedRenderId || readback.id != expectedReadbackId || render.target != target.id || readback.source != target.id || readback.staging != staging.id || readback.bytesPerRow != expectedRow || render.load != AttachmentLoadPlan.ClearTransparent || render.store != AttachmentStorePlan.Store || render.drawDataResources != null || graph.dependencies().singleOrNull()?.let { it.before == render.id && it.after == readback.id } != true || graph.visualCommandCount != draws.size || (draws.size !in 1..512 && !(draws.isEmpty() && render.destinationVersionAfter?.valueI64 == 0L && graph.materialPlanTableOrNull() == null)) || graph.peakFrameLocalBytes != expectedTargetBytes + expectedStaging) return null
        val targetRect = org.graphiks.math.geometry.RectI32(0, 0, graph.targetExtent.width, graph.targetExtent.height)
        if (draws.any { draw -> draw.coverage != CoveragePlan.FullOrScissor || draw.sample != SamplePlan.SingleSample || draw.copyVisibleBounds().isEmpty || draw.copyScissor().isEmpty || !targetRect.copy().intersect(draw.copyVisibleBounds()) || !draw.copyVisibleBounds().copy().intersect(draw.copyScissor()) || draw.copyScissor() != draw.copyVisibleBounds() }) return null
        val table = graph.materialPlanTableOrNull()
        if (draws.any { draw ->
                when (val authority = draw.materialAuthority) {
                    is PlanDrawMaterialAuthority.LegacyColorV1 -> false
                    is PlanDrawMaterialAuthority.MaterialV1 -> table == null || authority.ref.indexI32 >= table.sizeI32
                }
            }
        ) return null
        when (graph.capabilityId) {
            W3SolidRectPlanCompiler.CAPABILITY_ID -> if (
                table != null || draws.any { it.materialAuthority !is PlanDrawMaterialAuthority.LegacyColorV1 }
            ) return null
            W3SolidRectPlanCompiler.W5A_CAPABILITY_ID -> if (
                table == null || draws.any { it.materialAuthority !is PlanDrawMaterialAuthority.MaterialV1 }
            ) return null
            else -> if (!geometryClearOnly || draws.isNotEmpty()) return null
        }
        return W3Graph(target, staging, render, readback, draws, table, graph.takeIf { draws.isEmpty() })
    }

    private data class W3Graph(
        val target: PlanResource,
        val staging: PlanResource,
        val render: PlanPass.RenderPass,
        val readback: PlanPass.ReadbackPass,
        val draws: List<PlanDraw>,
        val materialPlanTable: MaterialPlanTable?,
        val destinationGraph: RenderGraph? = null,
    )

    private fun resolveMaterialColor(
        table: MaterialPlanTable?,
        authority: PlanDrawMaterialAuthority,
    ): ColorF32? = when (authority) {
        is PlanDrawMaterialAuthority.LegacyColorV1 -> authority.copyColorF32()
        is PlanDrawMaterialAuthority.MaterialV1 -> W5aMaterialPlanLowerer().lower(requireNotNull(table), authority.ref)
    }

    private sealed interface W3BaseTaskListResult {
        data class Ready(val taskList: GPUTaskList) : W3BaseTaskListResult
        data class Unsupported(val diagnostic: RenderDiagnostic) : W3BaseTaskListResult
        data class Invalid(val diagnostic: RenderDiagnostic) : W3BaseTaskListResult
    }

    internal sealed interface W3SessionScratchSealResult {
        data class Sealed(val scratch: W3SessionScratchV1) : W3SessionScratchSealResult
        data class Unsupported(val diagnostic: RenderDiagnostic) : W3SessionScratchSealResult
        data class Invalid(val diagnostic: RenderDiagnostic) : W3SessionScratchSealResult
    }

    private fun org.graphiks.math.geometry.RectI32.roundTripsExactlyThroughF32(): Boolean =
        listOf(left, top, right, bottom).all { value ->
            val original = value.toLong().toDouble()
            val converted = value.toFloat().toDouble()
            converted.isFinite() && converted == original
        }

    private fun invalid(message: String) = GpuPlanLoweringResult.InvalidPlan(diagnostic(message, RenderDiagnosticDomain.RESOURCE))
    private fun unsupported(message: String) = GpuPlanLoweringResult.UnsupportedCapability(diagnostic(message, RenderDiagnosticDomain.CAPABILITY))
    private fun diagnostic(message: String, domain: RenderDiagnosticDomain) = RenderDiagnostic(RenderDiagnosticCode("w3.lowering.incompatible_plan"), domain, RenderDiagnosticSeverity.ERROR, message)
    private fun invalidDiagnostic(message: String): RenderDiagnostic = diagnostic(message, RenderDiagnosticDomain.RESOURCE)
    private fun capability(
        code: RenderDiagnosticCode,
        message: String,
    ): GpuPlanLoweringResult.UnsupportedCapability = GpuPlanLoweringResult.UnsupportedCapability(
        capabilityDiagnostic(code, message),
    )
    private fun capabilityDiagnostic(code: RenderDiagnosticCode, message: String): RenderDiagnostic = RenderDiagnostic(
        code,
        RenderDiagnosticDomain.CAPABILITY,
        RenderDiagnosticSeverity.ERROR,
        message,
    )
}
