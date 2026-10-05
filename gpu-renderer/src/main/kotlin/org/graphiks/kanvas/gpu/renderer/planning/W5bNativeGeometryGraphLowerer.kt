package org.graphiks.kanvas.gpu.renderer.planning

import org.graphiks.kanvas.gpu.renderer.materials.composedStopAllocationLabelV5
import org.graphiks.kanvas.gpu.renderer.materials.noiseAllocationLabelV1

import org.graphiks.kanvas.gpu.plan.colorSourceCoordinatesV4

import org.graphiks.kanvas.gpu.plan.materialPlanRef

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
        val targetFormat = graph.colorFormat.resolveGpuTargetFormat()
        val identity = "w3.session.${request.deviceGeneration.value}.${bounds.width}x${bounds.height}." +
            targetFormat.nativeFormat.value
        val target = GPUFrameTargetRef("$identity.target")
        val staging = GPUFrameBufferRef("$identity.staging")
        val table = requireNotNull(graph.materialPlanTableOrNull())
        fun resource(id: PlanResourceId) = graph.resources().single { it.id == id }
        val targetResource = graph.resources().single { it.role == PlanResourceRole.LogicalTarget }
        val stagingResource = graph.resources().single { it.role == PlanResourceRole.ReadbackStaging }
        val targetPreparation = GPUResourcePreparationRequest(target,
            GPUFrameTextureDescriptor(bounds, targetFormat.nativeFormat, 1), GPUFrameResourceRole.SceneTarget,
            setOf(GPUFrameResourceUsage.RenderAttachment, GPUFrameResourceUsage.CopySource), GPUFrameResourceLifetime.FrameLocal,
            targetResource.byteSize, "$identity.target")
        val stagingPreparation = GPUResourcePreparationRequest(staging,
            GPUFrameBufferDescriptor(stagingResource.byteSize, graph.capabilities.copyBytesPerRowAlignment.toLong()),
            GPUFrameResourceRole.ReadbackStaging, setOf(GPUFrameResourceUsage.CopyDestination, GPUFrameResourceUsage.MapRead),
            GPUFrameResourceLifetime.FrameLocal, stagingResource.byteSize, "$identity.staging")
        val allocations = graph.resources().map { item -> GPUFrameMemoryAllocation(
            if(item.role == PlanResourceRole.GradientStopData) graph.composedStopAllocationLabelV5(request.w5aCompositeSessionIdentity ?: identity) ?: "$identity.${item.id.value}"
                else if(item.role == PlanResourceRole.NoiseTableData) graph.noiseAllocationLabelV1()
                else "$identity.${item.id.value}",
            when (item.role) {
                PlanResourceRole.LogicalTarget -> GPUFrameMemoryCategory.CanonicalTarget
                PlanResourceRole.ReadbackStaging -> GPUFrameMemoryCategory.ReadbackStaging
                PlanResourceRole.DestinationSnapshot -> GPUFrameMemoryCategory.DestinationSnapshot
                else -> GPUFrameMemoryCategory.ReusableScratch
            }, item.byteSize, if (item.kind == PlanResourceKind.Buffer) GPUFrameMemoryResourceKind.Buffer else GPUFrameMemoryResourceKind.Texture2D,
            item.copyExtent()?.let { GPUPixelBounds(0, 0, it.width, it.height) }, item.firstPassIndex, item.lastPassIndexExclusive) }
        val memory = GPUFrameMemoryBudgetPlanner.plan(GPUFrameMemoryBudgetRequest(allocations,
            graph.budget.maxFrameLocalBytes, limits))
        require(memory.diagnostic == null && memory.peakFrameTransientBytes + memory.targetResidentBytes == graph.peakFrameLocalBytes)
        val seal = GPUFrameCapabilitySeal.capture(request.frameId, request.deviceGeneration, request.capabilities)
        val replay = "w5b.geometry:${graph.id.value}"
        val recording = GPURecordingSeal(request.recordingId, 0L, replay, replay, seal.sealHash)
        val packets = mutableListOf<GPUDrawPacket>()
        val scratches = mutableListOf<W5bGeometryScratchV3>()
        val analyticUniformSeals = mutableMapOf<GPUDrawPacketID, GPUCorePrimitiveAnalyticShapeUniformSeal>()
        for (lane in lanes) {
            if (graph.capabilityId == W4cPathFillPlanCompiler.W7_HARD_PATH_ROOT_CAPABILITY_ID) {
                if (scratches.isEmpty()) {
                    val hardPath = lowerHardPathRoot(request, lanes, table, targetResource, stagingResource,
                        target, staging, bounds, targetFormat.nativeFormat, seal.sealHash, maxBuffer, maxDynamic)
                    packets += hardPath.packets
                    scratches += hardPath.scratches
                }
                continue
            }
            val commands = lane.commandIndicesI32().toSet()
            val draws = graph.passes().flatMap { pass -> when (pass) {
                is PlanPass.RenderPass -> pass.draws()
                is PlanPass.StencilCover -> listOf(pass.draw)
                else -> emptyList()
            } }.filter { it.commandIndex in commands }
            val data = requireNotNull(lane.drawDataResources)
            when (lane.capabilityId) {
                W4eClipPlanCompiler.W5A_HARD_CAPABILITY_ID -> {
                    val scratch = lowerW5bW4eLaneV3(request, lane, target, staging, seal)
                    packets += scratch.allPackets.filter { it.w4ePreparedPath != null }
                    scratches += scratch
                }
                W4dGeneralPathPlanCompiler.W5A_HARD_CAPABILITY_ID -> {
                    val source = lane.sourceGraph
                    val sourcePasses = source.passes().filterIsInstance<PlanPass.PathRenderPass>()
                    val authority = GPUPlanW4dGeneralPreparedAuthority.issueAfterFullGraphValidation(source, sourcePasses)
                    require(authority.preflightRevalidates(source, sourcePasses))
                    val builder = W4dGeneralPathGraphLowerer()
                    val sealedColors = draws.associateBy { it.commandIndex }
                    val built = sourcePasses.mapIndexed { index, pass -> builder.packet(pass, index, bounds,
                        targetFormat.nativeFormat, source, finalBlend = sealedColors.getValue(pass.draw.commandIndex).blend,
                        w5bMaterial = if (pass.phase == PathRenderPhase.SingleSampleStencilProducer) null else
                            W5aMaterialPlanLowerer().material(table, sealedColors.getValue(pass.draw.commandIndex).materialAuthority,
                                pass.draw.commandIndex,
                                sealedColors.getValue(pass.draw.commandIndex).materialAuthority.takeIf { it.colorSourceCoordinatesV4() != null }
                                    ?.let(graph::packedMaterialSourceV4))) }
                    val bindings = W5bGeneralResourceBindingsV3.issue(graph, lane, target, staging)
                    val native = requireNotNull(authority.bindNativeMaterializationFrame(identity, seal.sealHash,
                        request.deviceGeneration, sourcePasses.zip(built).associate { (pass, packet) -> pass.id.value to packet.structuralPipelineKey },
                        sourcePasses.associate { it.id.value to requireNotNull(authority.nativeUniformPayloadFor(it)) },
                        limits.minUniformBufferOffsetAlignment, maxBuffer, maxDynamic, bindings))
                    require(listOf(resource(data.vertex).byteSize, resource(data.index).byteSize, resource(data.uniform).byteSize) ==
                        listOf(native.frameResources.vertexCapacityBytes, native.frameResources.indexCapacityBytes, native.frameResources.uniformCapacityBytes))
                    packets += built.map { it.packet }
                    scratches += W5bGeometryScratchV3.General(graph.id.value, source, authority, native,
                        built.map { it.packet }, built.map { it.structuralPipelineKey }, maxBuffer, maxDynamic)
                }
                W3SolidRectPlanCompiler.W5A_CAPABILITY_ID -> {
                    require(draws.all { it is SolidRectDraw })
                    val builder = GpuPlanTaskListLowerer()
                    val built = draws.mapIndexed { index, draw -> builder.packet(draw,
                        if (draw.materialAuthority.colorSourceCoordinatesV4() != null) org.graphiks.math.color.ColorF32.Transparent
                        else requireNotNull(W5aMaterialPlanLowerer().lower(table,
                            draw.materialAuthority.materialPlanRef())), index, bounds, table, null,
                        draw.materialAuthority.takeIf { it.colorSourceCoordinatesV4() != null }?.let(graph::packedMaterialSourceV4),
                        nativeFormat = targetFormat.nativeFormat) }
                    val scratch = (builder.sealW3Scratch(request, target, staging, bounds, seal.sealHash, built,
                        nativeFormat = targetFormat.nativeFormat) as
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
                    val built = passes.map { builder.w5bPacket(it, paths, table, bounds, graph) }
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
                W4dPathStrokePlanCompiler.CAPABILITY_ID, W4dPathStrokePlanCompiler.W5B_CAPABILITY_ID -> {
                    val paths = draws.map { it as PathDraw }
                    val passes = graph.passes().filter { pass -> when (pass) {
                        is PlanPass.RenderPass -> pass.draws().singleOrNull()?.commandIndex in commands
                        is PlanPass.StencilGeometryProducerV3 -> pass.commandIndexI32 in commands
                        is PlanPass.StencilCover -> pass.draw.commandIndex in commands
                        else -> false
                    } }
                    val builder = W4dPathStrokeGraphLowerer()
                    val built = passes.map { builder.w5bPacket(it, paths, table, bounds,graph) }
                    val footprint = (PathStrokePlanBudget.calculate(graph.targetExtent, paths.map { when (val geometry = it.copyPathGeometry()) {
                        is PathDrawGeometry.Fill -> geometry.valueF32
                        is PathDrawGeometry.Stroke -> geometry.valueF32.copyFillGeometryF32()
                        else -> error("W4d keeps only its fill or stroke geometry")
                    } },
                        graph.capabilities, graph.budget, usesW5aMaterialContract = true) as PathStrokePlanBudgetResult.WithinBudget).footprint
                    require(listOf(resource(data.vertex).byteSize, resource(data.index).byteSize, resource(data.uniform).byteSize) ==
                        listOf(footprint.vertexCapacityBytes, footprint.indexCapacityBytes, footprint.uniformCapacityBytes))
                    var cursorI32 = 0
                    val visuals = paths.map { draw ->
                        val start = cursorI32
                        cursorI32 += if (draw.strategy == PathFillStrategy.StencilCover) 2 else 1
                        W4dPathStrokeGraphLowerer.W4dVisualDraw(draw, start,
                            passes.filterIsInstance<PlanPass.StencilCover>().singleOrNull { it.draw.commandIndex == draw.commandIndex }?.atomicGroup?.value)
                    }
                    val facts = W4dPathStrokeGraphLowerer.W4dGraph(W4dPathStrokePlanCompiler.W5B_CAPABILITY_ID,
                        targetResource, stagingResource, resource(data.vertex), resource(data.index), resource(data.uniform),
                        lane.depthStencil?.let(::resource), passes, graph.passes().last() as PlanPass.ReadbackPass, visuals, footprint, table)
                    val scratch = requireNotNull(builder.sealScratch(facts, built, graph.id.value, W4dPathStrokePlanCompiler.W5B_CAPABILITY_ID, target, staging, bounds,
                        lane.depthStencil, seal.sealHash, request.deviceGeneration.value, maxBuffer, maxDynamic, graph))
                    packets += built.map { it.packet }
                    scratches += W5bGeometryScratchV3.PathStroke(scratch, built.map { it.packet }, built.map { it.structuralPipelineKey })
                }
                W4aAnalyticRectPlanCompiler.W5A_CAPABILITY_ID, W4aAnalyticRectPlanCompiler.W5B_CAPABILITY_ID -> {
                    val analytic = draws.map { it as AnalyticRectDraw }
                    val footprint = (AnalyticRectPlanBudget.calculate(graph.targetExtent, analytic.size,
                        graph.capabilities, graph.budget) as AnalyticRectPlanBudgetResult.WithinBudget).footprint
                    val vertexResource = resource(data.vertex)
                    val indexResource = resource(data.index)
                    val uniformResource = resource(data.uniform)
                    require(vertexResource.byteSize == footprint.vertexCapacityBytes &&
                        indexResource.byteSize == footprint.indexCapacityBytes && uniformResource.byteSize == footprint.uniformCapacityBytes)
                    val built = analytic.mapIndexed { index, draw -> W4aAnalyticRectGraphLowerer().packet(draw,
                        if (draw.materialAuthority is PlanDrawMaterialAuthority.MaterialV4) org.graphiks.math.color.ColorF32.Transparent
                        else requireNotNull(W5aMaterialPlanLowerer().lower(table,
                            draw.materialAuthority.materialPlanRef())), index, bounds, table, w5b = true,
                        packedSourceV4 = draw.materialAuthority.takeIf { it.colorSourceCoordinatesV4() != null }?.let(graph::packedMaterialSourceV4)) }
                    val lanePackets = built.map { it.packet }
                    val semantics = lanePackets.map { it.semanticPayload as GPUDrawSemanticPayload.CorePrimitive }
                    val semanticAuthorities = semantics.map(GPUCorePrimitivePreparedSemanticAuthority::capture)
                    val payloads = semantics.mapIndexed { index, semantic ->
                        (buildCorePrimitiveAnalyticShapeUniform(semantic, semanticAuthorities[index]) as
                            GPUCorePrimitiveAnalyticShapeUniformBuildResult.Accepted).bytes }
                    val uniformPlan = (GPUUniformSlabPlanner.plan(W4aSessionScratchV1.SOURCE_LABEL,
                        request.deviceGeneration.value, footprint.uniformStrideBytes, uniformResource.byteSize,
                        lanePackets.mapIndexed { index, packet -> GPUUniformSlabPayload("analytic-shape-draw-${packet.commandIdValue}", payloads[index]) },
                        maxBuffer, maxDynamic) as GPUUniformSlabPlanningResult.Accepted).plan
                    val keys = lanePackets.mapIndexed { index, packet -> corePrimitiveRenderPipelineStructuralKey(semantics[index],
                        requireNotNull(packet.clipExecutionPlan), requireNotNull(packet.blendPlan), 1,
                        GPUColorFormat.RGBA8UnormSrgb.corePrimitiveStructuralColorFormat()) }
                    val scratch = W4aSessionScratchV1(graph.id.value, seal.sealHash, request.deviceGeneration.value,
                        target, staging, bounds, vertexResource.id, indexResource.id, uniformResource.id,
                        lanePackets.map { it.packetId }, lanePackets.map { it.commandIdValue }, built.map { it.scratchDraw }, keys.first(),
                        uniformPlan, footprint.uniformStrideBytes, footprint.vertexUsefulBytes, footprint.indexUsefulBytes,
                        footprint.uniformUsefulBytes, vertexResource.byteSize, indexResource.byteSize, uniformResource.byteSize,
                        requireNotNull(corePrimitiveFramePoolCapacitiesOrNull(footprint.vertexUsefulBytes,
                            footprint.indexUsefulBytes, footprint.uniformUsefulBytes)), maxBuffer, maxDynamic)
                    lanePackets.forEachIndexed { index, packet ->
                        analyticUniformSeals[packet.packetId] = GPUCorePrimitiveAnalyticShapeUniformSeal(uniformPlan, index,
                            packet.commandIdValue, packet.packetId, semanticAuthorities[index], semantics[index].scissorBounds,
                            keys[index], requireNotNull(packet.renderPipelineKey), CORE_PRIMITIVE_ANALYTIC_SHAPE_BINDING_LAYOUT_HASH,
                            PREPARED_FRAME_LATE_BOUND_RESOURCE_GENERATION, payloads[index])
                    }
                    packets += lanePackets
                    scratches += W5bGeometryScratchV3.AnalyticRect(scratch, lanePackets, keys, payloads)
                }
                W4bAnalyticRRectPlanCompiler.W5B_CAPABILITY_ID, W4bAnalyticRRectPlanCompiler.CAPABILITY_ID -> {
                    val analytic = draws.map { it as AnalyticRRectDraw }
                    val footprint = (AnalyticRRectPlanBudget.calculate(graph.targetExtent, analytic.size,
                        graph.capabilities, graph.budget) as AnalyticRRectPlanBudgetResult.WithinBudget).footprint
                    val vertexResource = resource(data.vertex)
                    val indexResource = resource(data.index)
                    val uniformResource = resource(data.uniform)
                    require(vertexResource.byteSize == footprint.vertexCapacityBytes &&
                        indexResource.byteSize == footprint.indexCapacityBytes && uniformResource.byteSize == footprint.uniformCapacityBytes)
                    val built = analytic.mapIndexed { index, draw -> W4bAnalyticRRectGraphLowerer().packet(draw,
                        if (draw.materialAuthority is PlanDrawMaterialAuthority.MaterialV4) org.graphiks.math.color.ColorF32.Transparent
                        else requireNotNull(W5aMaterialPlanLowerer().lower(table,
                            draw.materialAuthority.materialPlanRef())), index, bounds, table, w5b = true,
                        packedSourceV4=draw.materialAuthority.takeIf { it.colorSourceCoordinatesV4() != null }?.let(graph::packedMaterialSourceV4)) }
                    val lanePackets = built.map { it.packet }
                    val semantics = lanePackets.map { it.semanticPayload as GPUDrawSemanticPayload.CorePrimitive }
                    val semanticAuthorities = semantics.map(GPUCorePrimitivePreparedSemanticAuthority::capture)
                    val payloads = semantics.mapIndexed { index, semantic ->
                        (buildCorePrimitiveAnalyticShapeUniform(semantic, semanticAuthorities[index]) as
                            GPUCorePrimitiveAnalyticShapeUniformBuildResult.Accepted).bytes }
                    val uniformPlan = (GPUUniformSlabPlanner.plan(W4bSessionScratchV1.SOURCE_LABEL,
                        request.deviceGeneration.value, footprint.uniformStrideBytes, uniformResource.byteSize,
                        lanePackets.mapIndexed { index, packet -> GPUUniformSlabPayload("analytic-shape-draw-${packet.commandIdValue}", payloads[index]) },
                        maxBuffer, maxDynamic) as GPUUniformSlabPlanningResult.Accepted).plan
                    val keys = lanePackets.mapIndexed { index, packet -> corePrimitiveRenderPipelineStructuralKey(semantics[index],
                        requireNotNull(packet.clipExecutionPlan), requireNotNull(packet.blendPlan), 1,
                        GPUColorFormat.RGBA8UnormSrgb.corePrimitiveStructuralColorFormat()) }
                    val scratch = W4bSessionScratchV1(graph.id.value, seal.sealHash, request.deviceGeneration.value,
                        target, staging, bounds, vertexResource.id, indexResource.id, uniformResource.id,
                        lanePackets.map { it.packetId }, lanePackets.map { it.commandIdValue }, built.map { it.scratchDraw }, keys.first(),
                        uniformPlan, footprint.uniformStrideBytes, footprint.vertexUsefulBytes, footprint.indexUsefulBytes,
                        footprint.uniformUsefulBytes, vertexResource.byteSize, indexResource.byteSize, uniformResource.byteSize,
                        requireNotNull(corePrimitiveFramePoolCapacitiesOrNull(footprint.vertexUsefulBytes,
                            footprint.indexUsefulBytes, footprint.uniformUsefulBytes)), maxBuffer, maxDynamic, graph, lane)
                    lanePackets.forEachIndexed { index, packet ->
                        analyticUniformSeals[packet.packetId] = GPUCorePrimitiveAnalyticShapeUniformSeal(uniformPlan, index,
                            packet.commandIdValue, packet.packetId, semanticAuthorities[index], semantics[index].scissorBounds,
                            keys[index], requireNotNull(packet.renderPipelineKey), CORE_PRIMITIVE_ANALYTIC_SHAPE_BINDING_LAYOUT_HASH,
                            PREPARED_FRAME_LATE_BOUND_RESOURCE_GENERATION, payloads[index])
                    }
                    packets += lanePackets
                    scratches += W5bGeometryScratchV3.AnalyticRRect(scratch, lanePackets, keys, payloads)
                }
                else -> error("W5b native geometry lane is not yet lowered: ${lane.capabilityId}")
            }
        }
        val readback = GPUFrameReadbackRequest(GPUReadbackRequestID("w3.${graph.id.value}.readback"), bounds,
            GPUReadbackPixelFormat.Rgba8Unorm, GPUColorInterpretation.EncodedPremulSrgb)
        val witness = W5bPreparedFrameWitnessV3(graph, scratches.first(), seal, recording, memory,
            targetPreparation, stagingPreparation, readback, packets, geometryLanes = scratches)
        scratches.forEach { scratch -> witness.packetsFor(scratch).forEachIndexed { index, packet ->
            if (scratch is W5bGeometryScratchV3.W4e) packet.attachW5bW4eFrameWitnessV3(witness)
            else packet.attachCorePrimitivePreparedAuthority(GPUCorePrimitivePreparedPacketAuthority.plannedW5b(
                (scratch as W5bGeometryScratchV3.Pooled).packetStructuralPipelineKeys[index], requireNotNull(packet.renderPipelineKey), witness, analyticUniformSeals[packet.packetId], scratch as? W5bGeometryScratchV3.General))
        } }
        witness.w4eLane?.prefixRenders?.forEach { it.drawPackets.single().attachW5bW4eFrameWitnessV3(witness) }
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
    } catch (failure: HardPathLoweringFailure) { failure.result }
      catch (failure: IllegalArgumentException) { invalid(failure.message ?: "Invalid W5b native geometry authority") }
      catch (failure: IllegalStateException) { invalid(failure.message ?: "Invalid W5b native geometry lane") }

    private class HardPathLoweringFailure(val result: GpuPlanLoweringResult) : RuntimeException()

    private data class HardPathRootPackets(
        val packets: List<GPUDrawPacket>,
        val scratches: List<W5bGeometryScratchV3>,
    )

    private fun lowerHardPathRoot(
        request: GpuPlanLoweringRequest,
        lanes: List<W5bGeometryLanePlanV3>,
        table: MaterialPlanTable,
        targetResource: PlanResource,
        stagingResource: PlanResource,
        target: GPUFrameTargetRef,
        staging: GPUFrameBufferRef,
        bounds: GPUPixelBounds,
        nativeFormat: GPUColorFormat,
        capabilitySealHash: String,
        maxBufferSize: Long,
        maxDynamicUniformBuffers: Long,
    ): HardPathRootPackets {
        val graph = request.graph
        val publication = requireNotNull(graph.hardPathRootPublicationV1OrNull())
        require(publication.authenticates(graph) && lanes.size == 2)
        val pathPartition = publication.physicalPartitions().single {
            it.kind == W7HardPathRootFrameV1.PartitionKind.Path
        }
        val colorPartition = publication.physicalPartitions().single {
            it.kind == W7HardPathRootFrameV1.PartitionKind.DrawColor
        }
        val pathLane = lanes.single {
            it.hardPathPartitionKind == W7HardPathRootFrameV1.PartitionKind.Path
        }
        val colorLane = lanes.single {
            it.hardPathPartitionKind == W7HardPathRootFrameV1.PartitionKind.DrawColor
        }
        val rootSnapshot = pathLane.sourceGraph
        require(rootSnapshot === colorLane.sourceGraph && rootSnapshot !== graph &&
            rootSnapshot.capabilityId == W4cPathFillPlanCompiler.W7_HARD_PATH_ROOT_CAPABILITY_ID &&
            rootSnapshot.verifyW5bGeometryCompilerWitness() && rootSnapshot.w5bGeometryLanes().isEmpty())

        fun exactPartition(lane: W5bGeometryLanePlanV3,
            partition: W7HardPathRootFrameV1.PhysicalPartition) {
            val data = requireNotNull(lane.drawDataResources)
            val laneResources = listOfNotNull(data.vertex, data.index, data.uniform, lane.depthStencil)
            require(lane.commandIndicesI32() == partition.commandIndicesI32() &&
                lane.depthStencil == partition.depthStencil && laneResources.size == partition.resourceIds().size &&
                laneResources.toSet() == partition.resourceIds().toSet())
        }
        exactPartition(pathLane, pathPartition)
        exactPartition(colorLane, colorPartition)
        require(pathLane.depthStencil == pathPartition.depthStencil && colorLane.depthStencil == null)

        val slots = publication.commandSlots()
        val pathSlots = slots.filter { it.kind == W7HardPathRootFrameV1.SlotKind.Path }
        val colorSlots = slots.filter { it.kind == W7HardPathRootFrameV1.SlotKind.DrawColor }
        require(pathSlots.map { it.originalCommandIndexI32 } == pathPartition.commandIndicesI32() &&
            colorSlots.map { it.originalCommandIndexI32 } == colorPartition.commandIndicesI32())
        val paths = pathSlots.map { requireNotNull(it.draw) as PathFillDraw }
        val colors = colorSlots.map { requireNotNull(it.draw) as SolidRectDraw }
        require(colors.all { it.materialAuthority is PlanDrawMaterialAuthority.LegacyColorV1 })

        val resourceById = graph.resources().associateBy { it.id }
        fun resource(id: PlanResourceId): PlanResource = requireNotNull(resourceById[id])
        fun exactCapacity(data: PlanDrawDataResources, capacities: GPUCorePrimitiveFramePoolCapacities) {
            require(listOf(resource(data.vertex).byteSize, resource(data.index).byteSize,
                resource(data.uniform).byteSize) ==
                listOf(capacities.vertexBytes, capacities.indexBytes, capacities.uniformBytes))
        }

        val pathCommands = pathPartition.commandIndicesI32().toSet()
        val colorCommands = colorPartition.commandIndicesI32().toSet()
        val colorPassIdsByCommand = rootSnapshot.passes().filterIsInstance<PlanPass.RenderPass>()
            .flatMap { pass -> pass.draws().filter { it.commandIndex in colorCommands }
                .map { it.commandIndex to pass.id.value } }
            .groupBy({ it.first }, { it.second })
        require(colors.all { colorPassIdsByCommand[it.commandIndex]?.size == 1 })
        val pathPasses = rootSnapshot.passes().filter { pass -> when (pass) {
            is PlanPass.RenderPass -> pass.draws().singleOrNull()?.commandIndex in pathCommands
            is PlanPass.StencilGeometryProducerV3 -> pass.commandIndexI32 in pathCommands
            is PlanPass.StencilCover -> pass.draw.commandIndex in pathCommands
            else -> false
        } }
        val pathBuilder = W4cPathFillGraphLowerer()
        val pathBuilt = pathPasses.map { pathBuilder.w5bPacket(it, paths, table, bounds, rootSnapshot) }
        val footprint = (PathFillPlanBudget.calculate(graph.targetExtent, paths.map { it.copyGeometryF32() },
            graph.capabilities, graph.budget, usesW5aMaterialContract = true) as
            PathFillPlanBudgetResult.WithinBudget).footprint
        var firstPassIndex = 0
        val visuals = paths.map { draw ->
            val visual = W4cPathFillGraphLowerer.W4cVisualDraw(draw, firstPassIndex,
                pathPasses.filterIsInstance<PlanPass.StencilCover>()
                    .singleOrNull { it.draw.commandIndex == draw.commandIndex }?.atomicGroup?.value)
            firstPassIndex += if (draw.strategy == PathFillStrategy.StencilCover) 2 else 1
            visual
        }
        require(firstPassIndex == pathPasses.size)
        val pathData = requireNotNull(pathLane.drawDataResources)
        val pathFacts = W4cPathFillGraphLowerer.W4cGraph(
            W4cPathFillPlanCompiler.W5B_CAPABILITY_ID,
            targetResource, stagingResource, resource(pathData.vertex), resource(pathData.index),
            resource(pathData.uniform), pathLane.depthStencil?.let(::resource), pathPasses,
            rootSnapshot.passes().last() as PlanPass.ReadbackPass, visuals, footprint, table,
        )
        val pathScratch = requireNotNull(pathBuilder.sealScratch(
            pathFacts, pathBuilt, graph.id.value, target, staging, bounds, pathLane.depthStencil,
            capabilitySealHash, request.deviceGeneration.value, maxBufferSize, maxDynamicUniformBuffers,
            rootSnapshot,
        ))
        exactCapacity(pathData, pathScratch.poolCapacities)
        val pathGeometryScratch = W5bGeometryScratchV3.PathFill(pathScratch,
            pathBuilt.map { it.packet }, pathBuilt.map { it.structuralPipelineKey })

        val colorBuilder = GpuPlanTaskListLowerer()
        val colorPackets = colors.map { draw ->
            val color = (draw.materialAuthority as PlanDrawMaterialAuthority.LegacyColorV1).copyColorF32()
            colorBuilder.packet(draw, color, draw.commandIndex, bounds, null, null,
                nativeFormat = nativeFormat,
                passId = requireNotNull(colorPassIdsByCommand[draw.commandIndex]?.singleOrNull()))
        }
        val colorScratch = when (val sealed = colorBuilder.sealW3Scratch(request, target, staging, bounds,
            capabilitySealHash, colorPackets, nativeFormat)) {
            is GpuPlanTaskListLowerer.W3SessionScratchSealResult.Sealed -> sealed.scratch
            is GpuPlanTaskListLowerer.W3SessionScratchSealResult.Unsupported ->
                throw HardPathLoweringFailure(GpuPlanLoweringResult.UnsupportedCapability(sealed.diagnostic))
            is GpuPlanTaskListLowerer.W3SessionScratchSealResult.Invalid ->
                throw HardPathLoweringFailure(GpuPlanLoweringResult.InvalidPlan(sealed.diagnostic))
        }
        val colorData = requireNotNull(colorLane.drawDataResources)
        exactCapacity(colorData, colorScratch.poolCapacities)
        val colorGeometryScratch = W5bGeometryScratchV3.Direct(colorScratch)

        val pathPacketsByPass = pathPasses.zip(pathBuilt).associate { (pass, built) -> pass.id to built.packet }
        val colorPacketsByCommand = colors.zip(colorPackets).associate { (draw, packet) -> draw.commandIndex to packet }
        val chronologicalPackets = rootSnapshot.passes().flatMap { pass ->
            when (pass) {
                is PlanPass.RenderPass -> pass.draws().map { draw ->
                    when (draw.commandIndex) {
                        in pathCommands -> requireNotNull(pathPacketsByPass[pass.id])
                        in colorCommands -> requireNotNull(colorPacketsByCommand[draw.commandIndex])
                        else -> error("HardPath pass is not owned by a published physical partition")
                    }
                }
                is PlanPass.StencilGeometryProducerV3,
                is PlanPass.StencilCover -> listOf(requireNotNull(pathPacketsByPass[pass.id]))
                else -> emptyList()
            }
        }
        require(chronologicalPackets.size == pathBuilt.size + colorPackets.size &&
            chronologicalPackets.map { it.packetId }.distinct().size == chronologicalPackets.size &&
            chronologicalPackets.all { it.commandIdValue in pathCommands || it.commandIdValue in colorCommands })
        return HardPathRootPackets(chronologicalPackets,
            listOf(pathGeometryScratch, colorGeometryScratch))
    }

    private fun invalid(message: String) = GpuPlanLoweringResult.InvalidPlan(RenderDiagnostic(
        RenderDiagnosticCode("w5b.geometry.incompatible-plan"), RenderDiagnosticDomain.RESOURCE, RenderDiagnosticSeverity.ERROR, message))
}
