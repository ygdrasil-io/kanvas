package org.graphiks.kanvas.gpu.renderer.planning

import org.graphiks.kanvas.gpu.plan.*
import org.graphiks.kanvas.gpu.renderer.color.*
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.passes.*
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.recording.*
import org.graphiks.kanvas.gpu.renderer.resources.*
import org.graphiks.kanvas.gpu.renderer.state.*
import org.graphiks.kanvas.render.ir.*

/** W4b supplies immutable geometry and reservations; W5b supplies the ordered color tail. */
internal class W5bAnalyticRRectGraphLowerer {
    fun lower(request: GpuPlanLoweringRequest): GpuPlanLoweringResult = try {
        val graph = request.graph
        require(graph.capabilityId == W4bAnalyticRRectPlanCompiler.W5B_CAPABILITY_ID &&
            graph.verifyW5bGeometryCompilerWitness()) { "W5b Rect requires its compiler-issued geometry witness" }
        val limits = requireNotNull(request.capabilities.limits)
        val maxBuffer = requireNotNull(limits.maxBufferSize)
        val maxDynamic = requireNotNull(limits.maxDynamicUniformBuffersPerPipelineLayout)
        val draws = graph.passes().filterIsInstance<PlanPass.RenderPass>().flatMap { it.draws() }
            .map { it as AnalyticRRectDraw }
        val footprint = (AnalyticRRectPlanBudget.calculate(graph.targetExtent, draws.size,
            graph.capabilities, graph.budget) as AnalyticRRectPlanBudgetResult.WithinBudget).footprint
        fun resource(role: PlanResourceRole) = graph.resources().single { it.role == role }
        val vertexResource = resource(PlanResourceRole.VertexData)
        val indexResource = resource(PlanResourceRole.IndexData)
        val uniformResource = resource(PlanResourceRole.UniformData)
        require(vertexResource.byteSize == footprint.vertexCapacityBytes &&
            indexResource.byteSize == footprint.indexCapacityBytes && uniformResource.byteSize == footprint.uniformCapacityBytes)
        val bounds = GPUPixelBounds(0, 0, graph.targetExtent.width, graph.targetExtent.height)
        val identity = "w3.session.${request.deviceGeneration.value}.${bounds.width}x${bounds.height}.rgba8unorm-srgb"
        val target = GPUFrameTargetRef("$identity.target")
        val staging = GPUFrameBufferRef("$identity.staging")
        val targetPreparation = GPUResourcePreparationRequest(target,
            GPUFrameTextureDescriptor(bounds, GPUColorFormat.RGBA8UnormSrgb, 1), GPUFrameResourceRole.SceneTarget,
            setOf(GPUFrameResourceUsage.RenderAttachment, GPUFrameResourceUsage.CopySource),
            GPUFrameResourceLifetime.FrameLocal, footprint.targetBytes, "$identity.target")
        val stagingPreparation = GPUResourcePreparationRequest(staging,
            GPUFrameBufferDescriptor(footprint.readbackBytes, graph.capabilities.copyBytesPerRowAlignment.toLong()),
            GPUFrameResourceRole.ReadbackStaging, setOf(GPUFrameResourceUsage.CopyDestination, GPUFrameResourceUsage.MapRead),
            GPUFrameResourceLifetime.FrameLocal, footprint.readbackBytes, "$identity.staging")
        val allocations = graph.resources().map { resource ->
            GPUFrameMemoryAllocation("$identity.${resource.id.value}", when (resource.role) {
                PlanResourceRole.LogicalTarget -> GPUFrameMemoryCategory.CanonicalTarget
                PlanResourceRole.ReadbackStaging -> GPUFrameMemoryCategory.ReadbackStaging
                PlanResourceRole.DestinationSnapshot -> GPUFrameMemoryCategory.DestinationSnapshot
                else -> GPUFrameMemoryCategory.ReusableScratch
            }, resource.byteSize,
                if (resource.kind == PlanResourceKind.Buffer) GPUFrameMemoryResourceKind.Buffer else GPUFrameMemoryResourceKind.Texture2D,
                resource.copyExtent()?.let { GPUPixelBounds(0, 0, it.width, it.height) },
                resource.firstPassIndex, resource.lastPassIndexExclusive)
        }
        val memory = GPUFrameMemoryBudgetPlanner.plan(GPUFrameMemoryBudgetRequest(allocations,
            graph.budget.maxFrameLocalBytes, limits))
        require(memory.diagnostic == null && memory.peakFrameTransientBytes + memory.targetResidentBytes == graph.peakFrameLocalBytes)
        val table = requireNotNull(graph.materialPlanTableOrNull())
        val built = draws.mapIndexed { index, draw ->
            require(draw.materialAuthority !is PlanDrawMaterialAuthority.MaterialV4) { W5fPlanDiagnostics.Unpromoted }
            W4bAnalyticRRectGraphLowerer().packet(draw,
                requireNotNull(W5aMaterialPlanLowerer().lower(table,
                    draw.materialAuthority.materialPlanRef())), index, bounds, table, w5b = true)
        }
        val packets = built.map { it.packet }
        val semantics = packets.map { it.semanticPayload as GPUDrawSemanticPayload.CorePrimitive }
        val semanticAuthorities = semantics.map(GPUCorePrimitivePreparedSemanticAuthority::capture)
        val payloads = semantics.mapIndexed { index, semantic ->
            (buildCorePrimitiveAnalyticShapeUniform(semantic, semanticAuthorities[index]) as
                GPUCorePrimitiveAnalyticShapeUniformBuildResult.Accepted).bytes
        }
        val uniformPlan = (GPUUniformSlabPlanner.plan(W4bSessionScratchV1.SOURCE_LABEL,
            request.deviceGeneration.value, footprint.uniformStrideBytes, uniformResource.byteSize,
            packets.mapIndexed { index, packet -> GPUUniformSlabPayload("analytic-shape-draw-${packet.commandIdValue}", payloads[index]) },
            maxBuffer, maxDynamic) as GPUUniformSlabPlanningResult.Accepted).plan
        val keys = packets.mapIndexed { index, packet -> corePrimitiveRenderPipelineStructuralKey(semantics[index],
            requireNotNull(packet.clipExecutionPlan), requireNotNull(packet.blendPlan), 1,
            GPUColorFormat.RGBA8UnormSrgb.corePrimitiveStructuralColorFormat()) }
        val seal = GPUFrameCapabilitySeal.capture(request.frameId, request.deviceGeneration, request.capabilities)
        val replay = "w5b.w4b:${graph.id.value}"
        val recording = GPURecordingSeal(request.recordingId, 0L, replay, replay, seal.sealHash)
        val scratch = W4bSessionScratchV1(graph.id.value, seal.sealHash, request.deviceGeneration.value,
            target, staging, bounds, vertexResource.id, indexResource.id, uniformResource.id,
            packets.map { it.packetId }, packets.map { it.commandIdValue }, built.map { it.scratchDraw }, keys.first(),
            uniformPlan, footprint.uniformStrideBytes, footprint.vertexUsefulBytes, footprint.indexUsefulBytes,
            footprint.uniformUsefulBytes, vertexResource.byteSize, indexResource.byteSize, uniformResource.byteSize,
            requireNotNull(corePrimitiveFramePoolCapacitiesOrNull(footprint.vertexUsefulBytes,
                footprint.indexUsefulBytes, footprint.uniformUsefulBytes)), maxBuffer, maxDynamic, graph)
        val readback = GPUFrameReadbackRequest(GPUReadbackRequestID("w3.${graph.id.value}.readback"), bounds,
            GPUReadbackPixelFormat.Rgba8Unorm, GPUColorInterpretation.EncodedPremulSrgb)
        val witness = W5bPreparedFrameWitnessV3(graph, W5bGeometryScratchV3.AnalyticRRect(scratch, packets, keys, payloads),
            seal, recording, memory, targetPreparation, stagingPreparation, readback, packets)
        packets.forEachIndexed { index, packet ->
            val pipeline = requireNotNull(packet.renderPipelineKey)
            val uniformSeal = GPUCorePrimitiveAnalyticShapeUniformSeal(uniformPlan, index, packet.commandIdValue,
                packet.packetId, semanticAuthorities[index], semantics[index].scissorBounds, keys[index], pipeline,
                CORE_PRIMITIVE_ANALYTIC_SHAPE_BINDING_LAYOUT_HASH, PREPARED_FRAME_LATE_BOUND_RESOURCE_GENERATION, payloads[index])
            packet.attachCorePrimitivePreparedAuthority(GPUCorePrimitivePreparedPacketAuthority.plannedW5b(
                keys[index], pipeline, witness, uniformSeal))
        }
        val render = GPUTask.Render(GPUTaskID("task.w5b.w4b.${graph.id.value}.packing"), request.recordingId,
            GPUTaskPhase.Render, target, GPULoadStorePlan("clear", GPUStorePlan.Store), GPUSamplePlan.SingleSampleFrame,
            drawPackets = packets, batchEligibilityByPacketId = packets.associate { it.packetId to GPUPassBatchEligibility(
                kind = GPUPassBatchKind.SolidFill, queueGuard = GPUPassBatchQueueGuard(emptyList(), emptyList())) })
        val base = GPUTaskList(request.frameId, seal, listOf(recording), replay, listOf(render), emptyList(), GPUTaskPhase.entries, memory)
        when (val result = GPUCorePrimitivePreparedFrameTaskListAssembler().buildPreplanned(
            GPUCorePrimitivePreplannedFrameRequest(graph.id, base, target, bounds, targetPreparation, staging,
                stagingPreparation, readback, memory, graph.passes().filterIsInstance<PlanPass.RenderPass>().first().id,
                graph.passes().last().id, w5bDestinationGraph = graph))) {
            is GPUCorePrimitivePreparedFrameResult.Recorded -> GpuPlanLoweringResult.Lowered(result.taskList, readback.requestId.value)
            is GPUCorePrimitivePreparedFrameResult.Refused -> invalid(result.diagnostic.message)
        }
    } catch (error: IllegalArgumentException) {
        invalid(error.message ?: "W5b Rect lowering requires exact sealed geometry")
    }

    private fun invalid(message: String) = GpuPlanLoweringResult.InvalidPlan(RenderDiagnostic(
        RenderDiagnosticCode("w5b.rect.incompatible-plan"), RenderDiagnosticDomain.RESOURCE, RenderDiagnosticSeverity.ERROR, message))
}
