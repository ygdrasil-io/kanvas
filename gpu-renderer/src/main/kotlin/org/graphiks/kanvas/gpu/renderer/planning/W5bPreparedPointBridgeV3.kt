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
    fun lower(request: GPUPreparedSurfaceFrameRequest, budgetBytesI64: Long): GPUPreparedSurfaceFrameResult? {
        val semantics = request.semanticsByCommandId.values.filterIsInstance<GPUDrawSemanticPayload.CorePrimitive>()
            .sortedBy { it.payloadRef.commandIdValue }
        if (semantics.size != request.semanticsByCommandId.size || semantics.none { semantic ->
                semantic.sourceFamily == GPUCorePrimitiveSourceFamily.PointLine &&
                    request.w5bPointBlends[semantic.payloadRef.commandIdValue]?.let { blend ->
                        blend is BlendPlan.DestinationReadV1 || blend is BlendPlan.FixedFunctionV1 &&
                            blend.mode == org.graphiks.kanvas.render.ir.BlendMode.PLUS
                    } == true
            }) return null
        if (semantics.any { it.coverageMode != GPUCorePrimitiveCoverageMode.FullOrScissor ||
                it.clipCoveragePlan != org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoveragePlan.NoClip &&
                it.clipCoveragePlan !is org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoveragePlan.Scissor } ||
            request.targetFormat != GPUColorFormat.RGBA8UnormSrgb || request.readbackRequestId == null) return null
        return try {
            require(semantics.all { it.hasStructuralIntegrity() })
            val sources = semantics.map { semantic ->
                requireNotNull((semantic.material as? GPUCorePrimitiveMaterialPayload.SolidColor)?.w5aAuthority)
                    .also { require(it.validates(semantic.payloadRef.commandIdValue)) }
            }
            val interned = MaterialPlanTable.intern(sources.map { it.sourcePlanTable })
            val draws = semantics.mapIndexed { ordinalI32, semantic ->
                val commandI32 = semantic.payloadRef.commandIdValue
                val material = interned.remap(ordinalI32, sources[ordinalI32].ref)
                val scissor = semantic.scissorBounds.let { RectI32(it.left, it.top, it.right, it.bottom) }
                val blend = requireNotNull(request.w5bPointBlends[commandI32])
                when (val geometry = semantic.geometry) {
                    is GPUCorePrimitiveGeometry.TriangulatedPath -> {
                        require(semantic.sourceFamily == GPUCorePrimitiveSourceFamily.PointLine &&
                            geometry.geometryMode == GPUCorePrimitiveGeometryMode.DirectTriangles)
                        W5bPointDraw.of(commandI32, material, geometry.vertices.toFloatArray(),
                            geometry.indices.toIntArray(), geometry.sourceContourStarts.toIntArray(),
                            geometry.coverBounds.let { RectI32(it.left, it.top, it.right, it.bottom) }, scissor, blend)
                    }
                    is GPUCorePrimitiveGeometry.Rect -> {
                        val edges = listOf(geometry.left, geometry.top, geometry.right, geometry.bottom)
                        require(edges.all { it.toInt().toFloat() == it })
                        SolidRectDraw.ofMaterial(commandI32, material,
                            RectI32(geometry.left.toInt(), geometry.top.toInt(), geometry.right.toInt(), geometry.bottom.toInt()),
                            scissor, CoveragePlan.FullOrScissor, SamplePlan.SingleSample, blend)
                    }
                    else -> error("unsupported.w5b.point-frame-geometry")
                }
            }
            val capability = request.capabilities.toPlanCapabilitySnapshot(request.baseTaskList.capabilitySeal.deviceGeneration)
                as? GpuPlanCapabilityAdapterResult.Supported ?: error("unsupported.w5b.point-capability")
            val budget = PlanBudget(budgetBytesI64)
            val graph = W5bCorePrimitiveGraph.seal(PlanId("w5b.points.${request.baseTaskList.frameId.value}"),
                SizeI32(request.targetBounds.width, request.targetBounds.height), capability.snapshot, budget, draws, interned.table)
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
        }
    }

    private fun refused(message: String) = GPUPreparedSurfaceFrameResult.Refused(GPUDiagnostic(
        GPUDiagnosticCode("invalid.w5b.prepared-points"), GPUDiagnosticDomain.Resources,
        GPUDiagnosticSeverity.Error, message))
}
