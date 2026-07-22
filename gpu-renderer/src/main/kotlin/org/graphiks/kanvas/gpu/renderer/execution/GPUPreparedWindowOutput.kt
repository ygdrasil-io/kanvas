package org.graphiks.kanvas.gpu.renderer.execution

import java.util.concurrent.atomic.AtomicBoolean
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.color.GPUColorFormat
import org.graphiks.kanvas.gpu.renderer.recording.GPUSurfaceOutputDescriptor
import org.graphiks.kanvas.gpu.renderer.recording.GPUSurfaceOutputRef
import org.graphiks.kanvas.gpu.renderer.recording.GPUTask
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskDependency
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskID
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskList
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskPhase
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef

internal data class GPUPreparedWindowOutputSnapshot(
    val output: GPUSurfaceOutputRef,
    val width: Int,
    val height: Int,
    val format: GPUColorFormat,
    val surfaceGeneration: Long,
) {
    init {
        require(width > 0 && height > 0)
        require(surfaceGeneration >= 0L)
    }
}

internal interface GPUPreparedWindowOutputController :
    GPUSurfaceOutputProvider,
    GPUPostSubmitPresentAccess,
    GPUAcquiredSurfaceNativeTargetResolver,
    AutoCloseable {
    val deviceGeneration: GPUDeviceGenerationID
    val adapterInfo: GPUBackendAdapterSummary?
    val availabilityDiagnostic: org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnostic?
        get() = null
    fun snapshot(): GPUPreparedWindowOutputSnapshot
    fun resize(width: Int, height: Int)
    override fun resolve(output: GPUAcquiredSurfaceOutput): GPUPreparedNativeTextureViewOperand? = null
    override fun discardAfterSubmit(output: GPUAcquiredSurfaceOutput): GPUSurfaceReleaseResult
}

/** Opaque reusable window binding owned by one backend session. */
class GPUPreparedWindowOutput internal constructor(
    private val controller: GPUPreparedWindowOutputController,
) : AutoCloseable {
    private val closed = AtomicBoolean(false)

    internal val isClosed: Boolean get() = closed.get()
    val adapterInfo: GPUBackendAdapterSummary? get() = controller.adapterInfo
    val colorFormat: GPUColorFormat get() = controller.snapshot().format

    fun resize(width: Int, height: Int) {
        check(!isClosed) { "GPUPreparedWindowOutput is closed" }
        controller.resize(width, height)
    }

    internal val deviceGeneration: GPUDeviceGenerationID get() = controller.deviceGeneration
    internal fun snapshot(): GPUPreparedWindowOutputSnapshot = controller.snapshot()
    internal val surfaceProvider: GPUSurfaceOutputProvider get() = controller
    internal val presenter: GPUPostSubmitPresentAccess get() = controller
    internal val nativeTargetResolver: GPUAcquiredSurfaceNativeTargetResolver get() = controller
    internal val availabilityDiagnostic get() = controller.availabilityDiagnostic

    internal fun matches(output: GPUAcquiredSurfaceOutput): Boolean {
        if (isClosed || output.deviceGeneration != deviceGeneration) return false
        val snapshot = controller.snapshot()
        return output.output == snapshot.output && output.targetGeneration == snapshot.surfaceGeneration
    }

    internal fun attachToFrame(taskList: GPUTaskList, sceneTarget: GPUFrameTargetRef): GPUTaskList {
        check(!isClosed) { "GPUPreparedWindowOutput is closed" }
        require(taskList.capabilitySeal.deviceGeneration == deviceGeneration) {
            "Prepared window output and frame must use the same device generation"
        }
        require(taskList.tasks.none { it is GPUTask.Output }) {
            "A prepared window output cannot decorate a frame that already owns an Output task"
        }
        val recordingId = taskList.recordingSeals.maxByOrNull { it.insertionOrder }?.recordingId
            ?: error("A prepared window frame requires one sealed recording")
        val snapshot = controller.snapshot()
        val taskId = GPUTaskID("task.window-output.${taskList.frameId.value}")
        require(taskList.tasks.none { it.taskId == taskId }) { "Window output task identity already exists" }
        val output = GPUTask.Output(
            taskId = taskId,
            recordingId = recordingId,
            phase = GPUTaskPhase.Output,
            scene = sceneTarget,
            descriptor = GPUSurfaceOutputDescriptor(
                output = snapshot.output,
                width = snapshot.width,
                height = snapshot.height,
                format = snapshot.format,
                targetGeneration = snapshot.surfaceGeneration,
            ),
        )
        return GPUTaskList(
            frameId = taskList.frameId,
            capabilitySeal = taskList.capabilitySeal,
            recordingSeals = taskList.recordingSeals,
            expectedReplayKeyHash = taskList.expectedReplayKeyHash,
            tasks = taskList.tasks + output,
            dependencies = taskList.dependencies + taskList.tasks.map { task ->
                GPUTaskDependency(
                    fromTaskId = task.taskId,
                    toTaskId = taskId,
                    dependencyKind = "window-output",
                    reasonCode = "present.canonical.scene-target",
                )
            },
            phaseOrder = if (GPUTaskPhase.Output in taskList.phaseOrder) {
                taskList.phaseOrder
            } else {
                taskList.phaseOrder + GPUTaskPhase.Output
            },
            memoryBudget = taskList.memoryBudget,
            diagnostics = taskList.diagnostics,
        )
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) controller.close()
    }
}

/** Captured, non-throwing result of the sole post-submit window action. */
internal sealed interface GPUPostSubmitPresentResult {
    data object Presented : GPUPostSubmitPresentResult
    data class Failed(val diagnostic: org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnostic) :
        GPUPostSubmitPresentResult
}

/** Executes presentation without treating it as queue-completion evidence. */
internal fun interface GPUPostSubmitPresentAccess {
    fun present(output: GPUAcquiredSurfaceOutput): GPUPostSubmitPresentResult

    fun discardAfterSubmit(output: GPUAcquiredSurfaceOutput): GPUSurfaceReleaseResult =
        GPUSurfaceReleaseResult.Released

    companion object {
        val Unavailable = GPUPostSubmitPresentAccess {
            GPUPostSubmitPresentResult.Failed(
                executionDiagnostic(
                    "unsupported.frame-execution.window-present-access",
                    "A prepared surface output has no post-submit presentation access.",
                ),
            )
        }
    }
}
