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

/** Native lane partitions are compiler-issued; source and destination use the shared W5b tail. */
internal class W5bNativeGeometryGraphLowerer {
    fun lower(request: GpuPlanLoweringRequest): GpuPlanLoweringResult = try {
        val graph = request.graph
        require(graph.verifyW5bGeometryCompilerWitness() && graph.w5bGeometryLanes().isNotEmpty())
        val lanes = graph.w5bGeometryLanes()
        val limits = requireNotNull(request.capabilities.limits)
        val maxBuffer = requireNotNull(limits.maxBufferSize)
        val maxDynamic = requireNotNull(limits.maxDynamicUniformBuffersPerPipelineLayout)
        val bounds = GPUPixelBounds(0, 0, graph.targetExtent.width, graph.targetExtent.height)
        val identity = "w5b.geometry.${request.deviceGeneration.value}.${bounds.width}x${bounds.height}.rgba8unorm-srgb"
        val target = GPUFrameTargetRef("$identity.target")
        val staging = GPUFrameBufferRef("$identity.staging")
        val table = requireNotNull(graph.materialPlanTableOrNull())
        fun resource(id: PlanResourceId) = graph.resources().single { it.id == id }
        val targetResource = graph.resources().single { it.role == PlanResourceRole.LogicalTarget }
        val stagingResource = graph.resources().single { it.role == PlanResourceRole.ReadbackStaging }
        val targetPreparation = GPUResourcePreparationRequest(target,
            GPUFrameTextureDescriptor(bounds, GPUColorFormat.RGBA8UnormSrgb, 1), GPUFrameResourceRole.SceneTarget,
            setOf(GPUFrameResourceUsage.RenderAttachment, GPUFrameResourceUsage.CopySource), GPUFrameResourceLifetime.FrameLocal,
            targetResource.byteSize, "$identity.target")
        val stagingPreparation = GPUResourcePreparationRequest(staging,
            GPUFrameBufferDescriptor(stagingResource.byteSize, graph.capabilities.copyBytesPerRowAlignment.toLong()),
            GPUFrameResourceRole.ReadbackStaging, setOf(GPUFrameResourceUsage.CopyDestination, GPUFrameResourceUsage.MapRead),
            GPUFrameResourceLifetime.FrameLocal, stagingResource.byteSize, "$identity.staging")
        val allocations = graph.resources().map { item -> GPUFrameMemoryAllocation("$identity.${item.id.value}",
            when (item.role) {
                PlanResourceRole.LogicalTarget -> GPUFrameMemoryCategory.CanonicalTarget
                PlanResourceRole.ReadbackStaging -> GPUFrameMemoryCategory.ReadbackStaging
                PlanResourceRole.DestinationSnapshot -> GPUFrameMemoryCategory.DestinationSnapshot
                else -> GPUFrameMemoryCategory.ReusableScratch
            }, item.byteSize, if (item.kind == PlanResourceKind.Buffer) GPUFrameMemoryResourceKind.Buffer else GPUFrameMemoryResourceKind.Texture2D,
            bounds.takeIf { item.kind == PlanResourceKind.Texture2D }, item.firstPassIndex, item.lastPassIndexExclusive) }
        val memory = GPUFrameMemoryBudgetPlanner.plan(GPUFrameMemoryBudgetRequest(allocations,
            graph.budget.maxFrameLocalBytes, limits))
        require(memory.diagnostic == null && memory.peakFrameTransientBytes + memory.targetResidentBytes == graph.peakFrameLocalBytes)
        val seal = GPUFrameCapabilitySeal.capture(request.frameId, request.deviceGeneration, request.capabilities)
        val replay = "w5b.geometry:${graph.id.value}"
        val recording = GPURecordingSeal(request.recordingId, 0L, replay, replay, seal.sealHash)
        val packets = mutableListOf<GPUDrawPacket>()
        val scratches = mutableListOf<W5bGeometryScratchV3>()
        for (lane in lanes) {
            val commands = lane.commandIndicesI32().toSet()
            val draws = graph.passes().flatMap { pass -> when (pass) {
                is PlanPass.RenderPass -> pass.draws()
                is PlanPass.StencilCover -> listOf(pass.draw)
                else -> emptyList()
            } }.filter { it.commandIndex in commands }
            val data = requireNotNull(lane.drawDataResources)
            when (lane.capabilityId) {
                W3SolidRectPlanCompiler.W5A_CAPABILITY_ID -> {
                    require(draws.all { it is SolidRectDraw })
                    val builder = GpuPlanTaskListLowerer()
                    val built = draws.mapIndexed { index, draw -> builder.packet(draw,
                        requireNotNull(W5aMaterialPlanLowerer().lower(table,
                            (draw.materialAuthority as PlanDrawMaterialAuthority.MaterialV1).ref)), index, bounds, table, null) }
                    val scratch = (builder.sealW3Scratch(request, target, staging, bounds, seal.sealHash, built) as
                        GpuPlanTaskListLowerer.W3SessionScratchSealResult.Sealed).scratch
                    require(listOf(resource(data.vertex).byteSize, resource(data.index).byteSize, resource(data.uniform).byteSize) ==
                        listOf(scratch.poolCapacities.vertexBytes, scratch.poolCapacities.indexBytes, scratch.poolCapacities.uniformBytes))
                    packets += built
                    scratches += W5bGeometryScratchV3.Direct(scratch)
                }
                W4cPathFillPlanCompiler.CAPABILITY_ID, W4cPathFillPlanCompiler.W5B_CAPABILITY_ID -> {
                    val paths = draws.map { it as PathFillDraw }
                    val passes = graph.passes().filter { pass -> when (pass) {
                        is PlanPass.RenderPass -> pass.draws().singleOrNull()?.commandIndex in commands
                        is PlanPass.StencilGeometryProducerV3 -> pass.commandIndexI32 in commands
                        is PlanPass.StencilCover -> pass.draw.commandIndex in commands
                        else -> false
                    } }
                    val builder = W4cPathFillGraphLowerer()
                    val built = passes.map { builder.w5bPacket(it, paths, table, bounds) }
                    val footprint = (PathFillPlanBudget.calculate(graph.targetExtent, paths.map { it.copyGeometryF32() },
                        graph.capabilities, graph.budget, usesW5aMaterialContract = true) as PathFillPlanBudgetResult.WithinBudget).footprint
                    require(listOf(resource(data.vertex).byteSize, resource(data.index).byteSize, resource(data.uniform).byteSize) ==
                        listOf(footprint.vertexCapacityBytes, footprint.indexCapacityBytes, footprint.uniformCapacityBytes))
                    var cursorI32 = 0
                    val visuals = paths.map { draw ->
                        val start = cursorI32
                        cursorI32 += if (draw.strategy == PathFillStrategy.StencilCover) 2 else 1
                        W4cPathFillGraphLowerer.W4cVisualDraw(draw, start,
                            passes.filterIsInstance<PlanPass.StencilCover>().singleOrNull { it.draw.commandIndex == draw.commandIndex }?.atomicGroup?.value)
                    }
                    val facts = W4cPathFillGraphLowerer.W4cGraph(W4cPathFillPlanCompiler.W5B_CAPABILITY_ID,
                        targetResource, stagingResource, resource(data.vertex), resource(data.index), resource(data.uniform),
                        lane.depthStencil?.let(::resource), passes, graph.passes().last() as PlanPass.ReadbackPass, visuals, footprint, table)
                    val scratch = requireNotNull(builder.sealScratch(facts, built, graph.id.value, target, staging, bounds,
                        lane.depthStencil, seal.sealHash, request.deviceGeneration.value, maxBuffer, maxDynamic, graph))
                    packets += built.map { it.packet }
                    scratches += W5bGeometryScratchV3.PathFill(scratch, built.map { it.packet }, built.map { it.structuralPipelineKey })
                }
                else -> error("W5b native geometry lane is not yet lowered: ${lane.capabilityId}")
            }
        }
        val readback = GPUFrameReadbackRequest(GPUReadbackRequestID("w3.${graph.id.value}.readback"), bounds,
            GPUReadbackPixelFormat.Rgba8Unorm, GPUColorInterpretation.EncodedPremulSrgb)
        val witness = W5bPreparedFrameWitnessV3(graph, scratches.first(), seal, recording, memory,
            targetPreparation, stagingPreparation, readback, packets, geometryLanes = scratches)
        scratches.forEach { scratch -> witness.packetsFor(scratch).forEachIndexed { index, packet ->
            packet.attachCorePrimitivePreparedAuthority(GPUCorePrimitivePreparedPacketAuthority.plannedW5b(
                scratch.packetStructuralPipelineKeys[index], requireNotNull(packet.renderPipelineKey), witness))
        } }
        val render = GPUTask.Render(GPUTaskID("task.w5b.geometry.${graph.id.value}.packing"), request.recordingId,
            GPUTaskPhase.Render, target, GPULoadStorePlan("clear", GPUStorePlan.Store), GPUSamplePlan.SingleSampleFrame,
            drawPackets = packets, batchEligibilityByPacketId = packets.associate { it.packetId to GPUPassBatchEligibility(
                kind = GPUPassBatchKind.SolidFill, queueGuard = GPUPassBatchQueueGuard(emptyList(), emptyList())) })
        val base = GPUTaskList(request.frameId, seal, listOf(recording), replay, listOf(render), emptyList(), GPUTaskPhase.entries, memory)
        when (val result = GPUCorePrimitivePreparedFrameTaskListAssembler().buildPreplanned(
            GPUCorePrimitivePreplannedFrameRequest(graph.id, base, target, bounds, targetPreparation, staging,
                stagingPreparation, readback, memory, graph.passes().first().id, graph.passes().last().id,
                w5bDestinationGraph = graph))) {
            is GPUCorePrimitivePreparedFrameResult.Recorded -> GpuPlanLoweringResult.Lowered(result.taskList, readback.requestId.value)
            is GPUCorePrimitivePreparedFrameResult.Refused -> invalid(result.diagnostic.message)
        }
    } catch (failure: IllegalArgumentException) { invalid(failure.message ?: "Invalid W5b native geometry authority") }
      catch (failure: IllegalStateException) { invalid(failure.message ?: "Invalid W5b native geometry lane") }

    private fun invalid(message: String) = GpuPlanLoweringResult.InvalidPlan(RenderDiagnostic(
        RenderDiagnosticCode("w5b.geometry.incompatible-plan"), RenderDiagnosticDomain.RESOURCE, RenderDiagnosticSeverity.ERROR, message))
}
