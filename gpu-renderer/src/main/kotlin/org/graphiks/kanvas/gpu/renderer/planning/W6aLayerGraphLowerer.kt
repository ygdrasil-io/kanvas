package org.graphiks.kanvas.gpu.renderer.planning

import org.graphiks.kanvas.gpu.renderer.recording.GPUW6aLayerFramePlan
import org.graphiks.kanvas.render.ir.*

/** Visits the frozen multi-target graph; never lowers a fabricated standalone child frame. */
internal class W6aLayerGraphLowerer {
    fun lower(request: GpuPlanLoweringRequest): GpuPlanLoweringResult = try {
        val frame = GPUW6aLayerFramePlan(request)
        GpuPlanLoweringResult.Lowered(frame.taskList(), frame.readback.requestId.value)
    } catch (failure: IllegalArgumentException) {
        GpuPlanLoweringResult.InvalidPlan(RenderDiagnostic(RenderDiagnosticCode("w6a.layer.invalid_plan"),
            RenderDiagnosticDomain.RESOURCE, RenderDiagnosticSeverity.ERROR, failure.message ?: "Invalid layer frame"))
    }
}
