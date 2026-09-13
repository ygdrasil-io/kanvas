package org.graphiks.kanvas.gpu.renderer.planning

import org.graphiks.kanvas.gpu.plan.W5eImagePlanDiagnostics
import org.graphiks.kanvas.gpu.renderer.passes.W5ePreparedFrameWitnessV1
import org.graphiks.kanvas.gpu.renderer.recording.GPUTask
import org.graphiks.kanvas.render.ir.*

/** Lowers only the planner-issued physical construction bridge, then seals all image sources. */
internal class W5eImagePlanLowerer {
    fun lower(request: GpuPlanLoweringRequest): GpuPlanLoweringResult = try {
        val bridge = requireNotNull(request.graph.w5eImageConstructionOrNull()) { W5eImagePlanDiagnostics.InvalidContract }
        require(request.graph.visualCommandCount == bridge.imageDraws().size && request.graph.materialPlanTableOrNull() === bridge.materialTable)
        when (val lowered = GpuPlanTaskListLowerer().lower(request.copy(graph = bridge.constructionGraph))) {
            is GpuPlanLoweringResult.Lowered -> {
                val packets = lowered.taskList.tasks.filterIsInstance<GPUTask.Render>().flatMap { it.drawPackets }
                val witness = W5ePreparedFrameWitnessV1(bridge, packets)
                packets.forEach { it.attachW5eImageFrameWitnessV1(witness) }
                lowered
            }
            else -> lowered
        }
    } catch (failure: IllegalArgumentException) {
        GpuPlanLoweringResult.InvalidPlan(RenderDiagnostic(RenderDiagnosticCode(W5eImagePlanDiagnostics.InvalidContract),
            RenderDiagnosticDomain.RESOURCE, RenderDiagnosticSeverity.ERROR, failure.message.orEmpty()))
    }
}
