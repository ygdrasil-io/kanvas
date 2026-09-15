package org.graphiks.kanvas.gpu.renderer.planning

import org.graphiks.kanvas.gpu.plan.*
import org.graphiks.kanvas.gpu.renderer.color.GPUColorFormat
import org.graphiks.kanvas.gpu.renderer.payloads.*
import org.graphiks.kanvas.gpu.renderer.recording.*
import org.graphiks.kanvas.gpu.renderer.diagnostics.*
import org.graphiks.math.geometry.RectI32
import org.graphiks.math.geometry.SizeI32

/** Converts already admitted W5a squares and raw sources to a handle-free W5b graph. */
internal object W5bPreparedPointBridgeV3 {
    fun lower(request: GPUPreparedSurfaceFrameRequest,
        packets: List<org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket>,
        budgetBytesI64: Long): GPUPreparedSurfaceFrameResult? {
        val semantics = packets.mapNotNull { request.semanticsByCommandId[it.commandIdValue] as? GPUDrawSemanticPayload.CorePrimitive }
        if (semantics.size != request.semanticsByCommandId.size || semantics.none { semantic ->
                semantic.sourceFamily == GPUCorePrimitiveSourceFamily.PointLine &&
                    (request.w5hPointSources.containsKey(semantic.payloadRef.commandIdValue) || request.w5bPointBlends[semantic.payloadRef.commandIdValue]?.let { blend ->
                        blend is BlendPlan.DestinationReadV1 || blend is BlendPlan.FixedFunctionV1 &&
                            blend.mode == org.graphiks.kanvas.render.ir.BlendMode.PLUS
                    } == true)
            }) return null
        if (semantics.any { (it.coverageMode != GPUCorePrimitiveCoverageMode.FullOrScissor ||
                it.clipCoveragePlan != org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoveragePlan.NoClip &&
                it.clipCoveragePlan !is org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoveragePlan.Scissor) &&
                (it.sourceFamily != GPUCorePrimitiveSourceFamily.PointLine || request.w5bPointClips[it.payloadRef.commandIdValue] == null) } ||
            request.targetFormat != GPUColorFormat.RGBA8UnormSrgb || request.readbackRequestId == null) return null
        GPUFramePlanner.validateRecordingEnvelope(request.baseTaskList)?.let {
            return GPUPreparedSurfaceFrameResult.Refused(it)
        }
        return try {
            val base = request.baseTaskList
            val renders = base.tasks.filterIsInstance<GPUTask.Render>()
            require(renders.size == base.tasks.size && renders.flatMap { it.drawPackets } == packets) { "W5b base packet sequence changed" }
            require(base.diagnostics.isEmpty() && base.compositeCommands.isEmpty() && base.memoryBudget.diagnostic == null) { "W5b cannot discard base diagnostics or composite commands" }
            require(base.memoryBudget.allocations.isEmpty()) { "W5b cannot discard prepared base allocations" }
            require(base.phaseOrder == GPUTaskPhase.entries && base.recordingSeals.size == 1) { "W5b requires one canonical recording" }
            val recording = base.recordingSeals.single()
            require(recording.capabilitySealHash == base.capabilitySeal.sealHash) { "W5b recording capability identity changed" }
            // GPURecorder emits the logical frame.scene target; lowering maps that
            // one authenticated scene to the prepared session target, not vice versa.
            require(renders.all { it.recordingId == recording.recordingId && it.target.value == "frame.scene" &&
                it.phase == GPUTaskPhase.Render && it.compositeMembership == null &&
                it.loadStore == org.graphiks.kanvas.gpu.renderer.state.GPULoadStorePlan("load", org.graphiks.kanvas.gpu.renderer.state.GPUStorePlan.Store) &&
                it.resourceUses.isEmpty() && it.depthStencilLoadStore == null &&
                it.preparedImageBindingsByPacketId.isEmpty() && it.preparedTextBindingsByPacketId.isEmpty() &&
                it.samplePlan == org.graphiks.kanvas.gpu.renderer.passes.GPUSamplePlan.SingleSampleFrame }) { "W5b base render target, recording or sample authority changed" }
            val taskOrder = renders.map { it.taskId }
            require(taskOrder.distinct().size == taskOrder.size && base.dependencies.all {
                val from = taskOrder.indexOf(it.fromTaskId)
                val to = taskOrder.indexOf(it.toTaskId)
                from >= 0 && to > from
            }) { "W5b base dependency order changed" }
            packets.zip(semantics).forEach { (packet, semantic) ->
                if (semantic.sourceFamily == GPUCorePrimitiveSourceFamily.PointLine) {
                    require(request.w5bPointCaptures[packet.commandIdValue]?.validates(packet, semantic,
                        request.w5bPointBlends[packet.commandIdValue], request.w5bPointClips[packet.commandIdValue],
                        request.w5hPointSources[packet.commandIdValue]) == true) {
                        "W5b Point packet, geometry, material, blend or clip capture changed"
                    }
                }
                require(packet.hasCorePrimitiveSemanticAuthority(semantic, request.capabilities,
                    request.w5hPointSources[packet.commandIdValue])) { "W5b captured geometry authority changed" }
                require(semantic.targetBounds == request.targetBounds) { "W5b semantic target bounds changed" }
                require(semantic.payloadRef.commandIdValue == packet.commandIdValue) { "W5b semantic command identity changed" }
                // W5a intentionally replaces the analyzed route/material uniform with
                // CorePrimitive's captured geometry and source-stage uniform. The shared
                // semantic-authority validator above authenticates that route mapping.
                require(semantic.clipCoveragePlan == packet.clipCoveragePlan) { "W5b semantic clip coverage changed" }
                val execution = requireNotNull(packet.clipExecutionPlan) { "W5b packet clip execution is missing" }
                require(execution !is org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionPlan.Refused) { "W5b cannot discard a refused clip execution" }
                require(semantic.clipExecutionPlanIdentity?.let { it == execution.canonicalIdentity() } != false) { "W5b semantic clip execution changed" }
                require(packet.diagnostics.isEmpty()) { "W5b cannot discard packet diagnostics" }
            }
            val pendingSources = if (request.w5hPointSources.isEmpty()) null else semantics.map { semantic ->
                requireNotNull(request.w5hPointSources[semantic.payloadRef.commandIdValue]).also {
                    require((semantic.material as? GPUCorePrimitiveMaterialPayload.W5aMaterialPlanRefV1)?.ref == it.sourceRef)
                }
            }
            val sources = if (pendingSources != null) emptyList() else semantics.map { semantic ->
                requireNotNull(semantic.material.materialSourceAuthority)
                    .also { require(it.validates(semantic.payloadRef.commandIdValue)) }
            }
            val interned = if (pendingSources == null) MaterialPlanTable.intern(sources.map { it.sourcePlanTable }) else null
            val capability = request.capabilities.toPlanCapabilitySnapshot(request.baseTaskList.capabilitySeal.deviceGeneration)
                as? GpuPlanCapabilityAdapterResult.Supported ?: error("unsupported.w5b.point-capability")
            val budget = request.w5hPointBudget?.let { it.copy(maxFrameLocalBytes = minOf(it.maxFrameLocalBytes, budgetBytesI64)) }
                ?: PlanBudget(budgetBytesI64)
            val maskCommands = semantics.filter { it.clipCoveragePlan != org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoveragePlan.NoClip &&
                it.clipCoveragePlan !is org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoveragePlan.Scissor }
                .map { it.payloadRef.commandIdValue }.toSet()
            val clipOperations = maskCommands.map { requireNotNull(request.w5bPointClips[it]) }.distinctBy { it.canonicalId }
            require(clipOperations.size <= 1) { "unsupported.w5b.point-distinct-clips" }
            val clipOnly = clipOperations.singleOrNull()?.let { operations -> W4eClipPlanCompiler().sealClipOnly(
                operations, SizeI32(request.targetBounds.width, request.targetBounds.height), capability.snapshot,
                budget) }
            val draws = semantics.mapIndexed { ordinalI32, semantic ->
                val commandI32 = semantic.payloadRef.commandIdValue
                val material = if (pendingSources != null) MaterialPlanRef(ordinalI32)
                    else requireNotNull(interned).remap(ordinalI32, sources[ordinalI32].ref)
                val scissor = semantic.scissorBounds.let { RectI32(it.left, it.top, it.right, it.bottom) }
                val blend = requireNotNull(request.w5bPointBlends[commandI32])
                val pointClip = clipOnly.takeIf { commandI32 in maskCommands }
                when (val geometry = semantic.geometry) {
                    is GPUCorePrimitiveGeometry.TriangulatedPath -> {
                        require(semantic.sourceFamily == GPUCorePrimitiveSourceFamily.PointLine &&
                            geometry.geometryMode == GPUCorePrimitiveGeometryMode.DirectTriangles)
                        W5bPointDraw.of(commandI32, material, geometry.vertices.toFloatArray(),
                            geometry.indices.toIntArray(), geometry.sourceContourStarts.toIntArray(),
                            geometry.coverBounds.let { RectI32(it.left, it.top, it.right, it.bottom) }, scissor, blend, pointClip,
                            composedV5 = pendingSources != null)
                    }
                    is GPUCorePrimitiveGeometry.Rect -> {
                        val edges = listOf(geometry.left, geometry.top, geometry.right, geometry.bottom)
                        require(edges.all { it.toInt().toFloat() == it })
                        SolidRectDraw.ofMaterial(commandI32, material,
                            RectI32(geometry.left.toInt(), geometry.top.toInt(), geometry.right.toInt(), geometry.bottom.toInt()),
                            scissor, CoveragePlan.FullOrScissor, SamplePlan.SingleSample, blend, composedV5 = pendingSources != null)
                    }
                    else -> error("unsupported.w5b.point-frame-geometry")
                }
            }
            val id = PlanId("w5b.points.${request.baseTaskList.frameId.value}")
            val extent = SizeI32(request.targetBounds.width, request.targetBounds.height)
            val graph = if (pendingSources != null) W5hPreparedPointMaterialV6.seal(id, extent, capability.snapshot, budget, draws, pendingSources)
                else W5bCorePrimitiveGraph.seal(id, extent, capability.snapshot, budget, draws, requireNotNull(interned).table)
            when (val lowered = GpuPlanTaskListLowerer().lower(GpuPlanLoweringRequest(graph, request.capabilities,
                request.baseTaskList.capabilitySeal.deviceGeneration, budget, request.baseTaskList.frameId,
                request.baseTaskList.recordingSeals.single().recordingId, budgetBytesI64,
                w5bPreparedSemantics = semantics.associateBy { it.payloadRef.commandIdValue },
                w5bPreparedTarget = request.target))) {
                is GpuPlanLoweringResult.Lowered -> GPUPreparedSurfaceFrameResult.Recorded(lowered.taskList)
                is GpuPlanLoweringResult.InvalidPlan -> refused(lowered.diagnostic.message)
                is GpuPlanLoweringResult.UnsupportedCapability -> refused(lowered.diagnostic.message)
            }
        } catch (failure: IllegalArgumentException) {
            refused(failure.message.orEmpty())
        } catch (failure: IllegalStateException) {
            refused(failure.message.orEmpty())
        } catch (failure: ArithmeticException) {
            refused(failure.message.orEmpty())
        }
    }

    private fun refused(message: String) = GPUPreparedSurfaceFrameResult.Refused(GPUDiagnostic(
        GPUDiagnosticCode("invalid.w5b.prepared-points"), GPUDiagnosticDomain.Resources,
        GPUDiagnosticSeverity.Error, message))
}
