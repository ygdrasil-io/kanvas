package org.graphiks.kanvas.gpu.renderer.planning

import org.graphiks.kanvas.gpu.renderer.recording.GPUW6aLayerFramePlan
import org.graphiks.kanvas.gpu.plan.FilterPassOperationV1
import org.graphiks.kanvas.gpu.plan.PlanPass
import org.graphiks.kanvas.render.ir.*

/** Visits the frozen multi-target graph; never lowers a fabricated standalone child frame. */
internal class W6aLayerGraphLowerer {
    fun lower(request: GpuPlanLoweringRequest): GpuPlanLoweringResult = try {
        request.graph.passes().filterIsInstance<PlanPass.FilterPass>().forEach { pass ->
            when (val operation = pass.operation) {
                is FilterPassOperationV1.MatrixConvolution,
                is FilterPassOperationV1.DisplacementMap,
                is FilterPassOperationV1.Magnifier,
                is FilterPassOperationV1.Lighting,
                is FilterPassOperationV1.Picture,
                is FilterPassOperationV1.RuntimeImageOpacity,
                -> throw IllegalArgumentException("W6d frozen operation ${operation.kind} is not executable until its owning slice.")
                else -> Unit
            }
        }
        val frame = GPUW6aLayerFramePlan(request)
        GpuPlanLoweringResult.Lowered(frame.taskList(), frame.readback.requestId.value)
    } catch (failure: IllegalArgumentException) {
        GpuPlanLoweringResult.InvalidPlan(RenderDiagnostic(RenderDiagnosticCode("w6a.layer.invalid_plan"),
            RenderDiagnosticDomain.RESOURCE, RenderDiagnosticSeverity.ERROR, failure.message ?: "Invalid layer frame"))
    }
}
