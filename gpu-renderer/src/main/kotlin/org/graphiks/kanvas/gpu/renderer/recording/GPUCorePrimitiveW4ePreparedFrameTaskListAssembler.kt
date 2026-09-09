package org.graphiks.kanvas.gpu.renderer.recording

import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryBudgetPlan
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourcePreparationRequest

/** Closes the already-authenticated W4e frame facts without consulting clip planning APIs. */
internal class GPUCorePrimitiveW4ePreparedFrameTaskListAssembler {
    fun build(
        base: GPUTaskList,
        requests: List<GPUResourcePreparationRequest>,
        renders: List<GPUTask.Render>,
        target: GPUFrameTargetRef,
        staging: GPUFrameBufferRef,
        readback: GPUFrameReadbackRequest,
        memory: GPUFrameMemoryBudgetPlan,
        atomicGroupByRenderTaskId: Map<GPUTaskID, String?>,
    ): GPUCorePrimitivePreparedFrameResult {
        if (renders.isEmpty() || requests.isEmpty()) return refused("W4e prepared frame has no authenticated work.")
        val prefix = "task.w4e.${base.expectedReplayKeyHash}"
        val prepare = GPUTask.PrepareResources(GPUTaskID("$prefix.prepare"), renders.first().recordingId, GPUTaskPhase.Prepare, requests)
        val readbackTask = GPUTask.Readback(GPUTaskID("$prefix.readback"), renders.first().recordingId, GPUTaskPhase.Readback, target, staging, readback)
        val tasks = listOf(prepare) + renders + readbackTask
        val ids = tasks.map(GPUTask::taskId)
        val dependencies = ids.zipWithNext().mapIndexed { index, (before, after) ->
            val atomicGroup = atomicGroupByRenderTaskId[before]
                ?.takeIf { it == atomicGroupByRenderTaskId[after] }
                ?.let(::GPUTaskAtomicGroupID)
            GPUTaskDependency(
                before, after, if (index == 0) "resource-prepare" else "plan-pass-dependency",
                GPUTaskUseToken("$prefix.$index"), "w4e-sealed-order", atomicGroup,
            )
        }
        return GPUCorePrimitivePreparedFrameResult.Recorded(
            GPUTaskList(base.frameId, base.capabilitySeal, base.recordingSeals, base.expectedReplayKeyHash, tasks, dependencies, base.phaseOrder, memory, base.diagnostics),
        )
    }

    private fun refused(message: String) = GPUCorePrimitivePreparedFrameResult.Refused(
        org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnostic(
            org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticCode("w4e.lowering.incompatible_plan"),
            org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticDomain.Recording,
            org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticSeverity.Error,
            message,
        ),
    )
}
