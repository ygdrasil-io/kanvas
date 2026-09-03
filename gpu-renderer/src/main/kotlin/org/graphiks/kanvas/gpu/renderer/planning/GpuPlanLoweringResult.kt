package org.graphiks.kanvas.gpu.renderer.planning

import org.graphiks.kanvas.gpu.plan.PlanBudget
import org.graphiks.kanvas.gpu.plan.RenderGraph
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameID
import org.graphiks.kanvas.gpu.renderer.recording.GPURecordingID
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskList
import org.graphiks.kanvas.render.ir.RenderDiagnostic

public data class GpuPlanLoweringRequest(
    public val graph: RenderGraph,
    public val capabilities: GPUCapabilities,
    public val deviceGeneration: GPUDeviceGenerationID,
    public val currentBudget: PlanBudget,
    public val frameId: GPUFrameID,
    public val recordingId: GPURecordingID,
)

public sealed interface GpuPlanLoweringResult {
    public data class Lowered(
        public val taskList: GPUTaskList,
        public val readbackRequestId: String,
    ) : GpuPlanLoweringResult

    public data class InvalidPlan(public val diagnostic: RenderDiagnostic) : GpuPlanLoweringResult
    public data class UnsupportedCapability(public val diagnostic: RenderDiagnostic) : GpuPlanLoweringResult
}
