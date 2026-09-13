package org.graphiks.kanvas.gpu.renderer.planning

import org.graphiks.kanvas.gpu.plan.W5eImagePlanDiagnostics
import org.graphiks.kanvas.gpu.renderer.passes.W5ePreparedFrameWitnessV1
import org.graphiks.kanvas.render.ir.*

/** Lowers only the planner-issued physical construction bridge, then seals all image sources. */
internal class W5eImagePlanLowerer {
    fun lower(request: GpuPlanLoweringRequest): GpuPlanLoweringResult = try {
        val bridge = requireNotNull(request.graph.w5eImageConstructionOrNull()) { W5eImagePlanDiagnostics.InvalidContract }
        require(request.graph.visualCommandCount == bridge.visualCommandCountI32 && request.graph.materialPlanTableOrNull() === bridge.materialTable)
        when (val lowered = GpuPlanTaskListLowerer().lower(request.copy(graph = bridge.constructionGraph))) {
            is GpuPlanLoweringResult.Lowered -> {
                val witness = W5ePreparedFrameWitnessV1(bridge, lowered.taskList)
                lowered.copy(taskList = lowered.taskList.withW5eConstructionV1(bridge, witness))
            }
            else -> lowered
        }
    } catch (failure: IllegalArgumentException) {
        GpuPlanLoweringResult.InvalidPlan(RenderDiagnostic(RenderDiagnosticCode(W5eImagePlanDiagnostics.InvalidContract),
            RenderDiagnosticDomain.RESOURCE, RenderDiagnosticSeverity.ERROR, failure.message.orEmpty()))
    }
}
