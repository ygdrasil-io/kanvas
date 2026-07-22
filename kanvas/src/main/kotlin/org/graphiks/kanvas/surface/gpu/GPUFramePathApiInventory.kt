package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoveragePlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionPlan
import org.graphiks.kanvas.gpu.renderer.commands.GPUBounds
import org.graphiks.kanvas.gpu.renderer.commands.GPUDrawCommandID
import org.graphiks.kanvas.gpu.renderer.commands.GPUFrameProvenance
import org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts
import org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnostic
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticCode
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticDomain
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticSeverity
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlan
import org.graphiks.kanvas.gpu.renderer.passes.GPUCoverageConsumption
import org.graphiks.kanvas.gpu.renderer.product.GPUProductFlagConfig
import org.graphiks.kanvas.gpu.renderer.recording.GPUCorePrimitivePreparedFrameRequest
import org.graphiks.kanvas.gpu.renderer.recording.GPUCorePrimitivePreparedFrameResult
import org.graphiks.kanvas.gpu.renderer.recording.GPUCorePrimitivePreparedFrameTaskListBuilder
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameID
import org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlan
import org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlanner
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackRequestID
import org.graphiks.kanvas.gpu.renderer.recording.GPURecorder
import org.graphiks.kanvas.gpu.renderer.recording.GPURecording
import org.graphiks.kanvas.gpu.renderer.recording.GPURecordingID
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.surface.RenderConfig

const val GPU_FRAME_PROVENANCE_ANNOTATION_KEY: String = "kanvas.frame.provenance"

enum class GPUFramePathStateKind {
    Transform,
    Clip,
    Annotation,
    FlushSnapshot,
}

data class GPUFramePathStateEvent(
    val operationIndex: Int,
    val kind: GPUFramePathStateKind,
)

data class GPUFramePathTelemetryInput(
    val commandId: GPUDrawCommandID,
    val paintOrder: Int,
    val provenance: GPUFrameProvenance,
)

data class GPUFramePathVisualCommand(
    val normalized: NormalizedDrawCommand,
    val targetSpaceBounds: GPUBounds,
    val geometryCoverage: GPUCoverageConsumption,
    val clipCoverage: GPUClipCoveragePlan,
    val clipExecutionPlan: GPUClipExecutionPlan,
    val blendPlan: GPUBlendPlan,
    val provenance: GPUFrameProvenance,
    val geometryRefusal: GPUCorePrimitiveGeometryRefusal? = null,
)

data class GPUFramePathInventoryPlan(
    val visualCommands: List<GPUFramePathVisualCommand>,
    val normalizedCommands: List<NormalizedDrawCommand>,
    val stateEvents: List<GPUFramePathStateEvent>,
    val telemetryInputs: List<GPUFramePathTelemetryInput>,
    val recording: GPURecording,
    val framePlan: GPUFramePlan,
    val legacyDump: GPULegacyImmediatePathDump,
)

/**
 * Read-only evidence harness over the production [GPUOpMapper] → recorder → planner contracts.
 *
 * Until the Task 12A-C Surface cutover, this inventory is neither the active Surface route nor
 * proof that a product frame reached one native queue submit. It only makes handle-free route
 * decisions and prepared-frame evidence inspectable by tests.
 */
object GPUFramePathApiInventory {
    fun plan(
        operations: List<DisplayOp>,
        target: GPUTargetFacts,
        config: RenderConfig,
        capabilities: GPUCapabilities = GPUProductFlagConfig.fromSystemProperties().buildCapabilities(),
        deviceGeneration: GPUDeviceGenerationID = GPUDeviceGenerationID(0),
    ): GPUFramePathInventoryPlan {
        val mapping = GPUOpMapper.mapOperations(operations, target, config, capabilities)
        val visual = mapping.visualCommands

        val recorder = GPURecorder(
            recordingId = GPURecordingID("kanvas.frame-path"),
            frameId = GPUFrameID(0),
            capabilities = capabilities,
            deviceGeneration = deviceGeneration,
        )
        visual.forEach { recorder.record(it.normalized) }
        val recording = recorder.close()
        val framePlan = GPUFramePlanner.plan(recording.taskList)
        return GPUFramePathInventoryPlan(
            visualCommands = visual.toList(),
            normalizedCommands = visual.map(GPUFramePathVisualCommand::normalized),
            stateEvents = mapping.stateEvents,
            telemetryInputs = visual.map { command ->
                GPUFramePathTelemetryInput(
                    commandId = command.normalized.commandId,
                    paintOrder = command.normalized.ordering.paintOrder,
                    provenance = command.provenance,
                )
            },
            recording = recording,
            framePlan = framePlan,
            legacyDump = mapping.legacyDump,
        )
    }

    /** Adds the canonical native frame envelope after the mapper and recorder have made route decisions. */
    fun prepareNativeTaskList(
        inventory: GPUFramePathInventoryPlan,
        capabilities: GPUCapabilities,
        targetBounds: GPUPixelBounds,
        readbackRequestId: GPUReadbackRequestID? = null,
    ): GPUCorePrimitivePreparedFrameResult {
        val semantics = when (val gathered = gatherCorePrimitiveSemantics(inventory, targetBounds)) {
            is GPUCorePrimitiveSemanticGatherResult.Gathered -> gathered.semantics
            is GPUCorePrimitiveSemanticGatherResult.Refused -> return GPUCorePrimitivePreparedFrameResult.Refused(
                GPUDiagnostic(
                    code = GPUDiagnosticCode(gathered.code),
                    domain = GPUDiagnosticDomain.Recording,
                    severity = GPUDiagnosticSeverity.Error,
                    message = gathered.message,
                    facts = gathered.facts,
                ),
            )
        }
        return GPUCorePrimitivePreparedFrameTaskListBuilder().build(
            GPUCorePrimitivePreparedFrameRequest(
                baseTaskList = inventory.recording.taskList,
                capabilities = capabilities,
                target = GPUFrameTargetRef("frame.scene"),
                targetBounds = targetBounds,
                semanticsByCommandId = semantics,
                readbackRequestId = readbackRequestId,
            ),
        )
    }

    /** Delegates exact handle-free semantics to the production builder. */
    internal fun gatherCorePrimitiveSemantics(
        inventory: GPUFramePathInventoryPlan,
        targetBounds: GPUPixelBounds,
    ): GPUCorePrimitiveSemanticGatherResult = GPUCorePrimitiveSemanticBuilder.gather(
        visualCommands = inventory.visualCommands,
        recording = inventory.recording,
        targetBounds = targetBounds,
    )
}
