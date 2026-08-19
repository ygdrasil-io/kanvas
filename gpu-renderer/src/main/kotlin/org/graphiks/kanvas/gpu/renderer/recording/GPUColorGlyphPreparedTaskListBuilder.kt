package org.graphiks.kanvas.gpu.renderer.recording

import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoveragePlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionPlan
import org.graphiks.kanvas.gpu.renderer.color.GPUColorFormat
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketID
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketRole
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchEligibility
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchKind
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchQueueGuard
import org.graphiks.kanvas.gpu.renderer.passes.GPUProvisionalRenderSegmentKey
import org.graphiks.kanvas.gpu.renderer.passes.GPURenderStepID
import org.graphiks.kanvas.gpu.renderer.passes.GPUSamplePlan
import org.graphiks.kanvas.gpu.renderer.payloads.COLOR_GLYPH_RENDER_STEP_IDENTITY
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryBudgetPlan
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryCategory
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.gpu.renderer.state.GPULoadStorePlan
import org.graphiks.kanvas.gpu.renderer.state.GPUStorePlan

/** Closed typed request for adapting one prepared COLRv0 semantic to the common task builder. */
internal data class GPUColorGlyphPreparedTaskListRequest(
    val frameId: GPUFrameID,
    val recordingId: GPURecordingID,
    val capabilities: GPUCapabilities,
    val deviceGeneration: GPUDeviceGenerationID,
    val target: GPUFrameTargetRef,
    val semantic: GPUDrawSemanticPayload.ColorGlyph,
    val readbackRequestId: GPUReadbackRequestID?,
    val configuredAggregateBudgetBytes: Long = 1L shl 30,
) {
    init {
        require(configuredAggregateBudgetBytes > 0L)
    }
}

/** Recording either succeeds with one immutable task list or refuses before native materialization. */
internal sealed interface GPUColorGlyphPreparedTaskListResult {
    data class Recorded(val taskList: GPUTaskList) : GPUColorGlyphPreparedTaskListResult
    data class Refused(
        val diagnostic: org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnostic,
    ) : GPUColorGlyphPreparedTaskListResult
}

/**
 * Thin compatibility adapter for tests that still submit one prepared COLRv0 semantic directly.
 *
 * All resource identities, budgets, uploads and dependencies are owned by the heterogeneous
 * [GPUPreparedSurfaceFrameTaskListBuilder]. This adapter only creates its one-packet base request.
 */
internal class GPUColorGlyphPreparedTaskListBuilder(
    readbackLayoutPlanner: GPUReadbackLayoutPlanner = GPUReadbackLayoutPlanner(),
) {
    private val commonBuilder = GPUPreparedSurfaceFrameTaskListBuilder(readbackLayoutPlanner)

    fun build(request: GPUColorGlyphPreparedTaskListRequest): GPUColorGlyphPreparedTaskListResult {
        val semantic = request.semantic
        val packet = colorGlyphPacket(semantic)
        val seal = GPUFrameCapabilitySeal.capture(
            request.frameId,
            request.deviceGeneration,
            request.capabilities,
        )
        val replayHash = "color-glyph:${semantic.canonicalHash}"
        val baseTaskList = GPUTaskList(
            frameId = request.frameId,
            capabilitySeal = seal,
            recordingSeals = listOf(
                GPURecordingSeal(
                    request.recordingId,
                    0L,
                    replayHash,
                    replayHash,
                    seal.sealHash,
                ),
            ),
            expectedReplayKeyHash = replayHash,
            tasks = listOf(
                GPUTask.Render(
                    taskId = GPUTaskID(
                        "task.color-glyph.base.${request.frameId.value}." +
                            semantic.payloadRef.commandIdValue,
                    ),
                    recordingId = request.recordingId,
                    phase = GPUTaskPhase.Render,
                    target = request.target,
                    loadStore = GPULoadStorePlan("clear", GPUStorePlan.Store),
                    samplePlan = GPUSamplePlan.SingleSampleFrame,
                    provisionalSegmentKey =
                        GPUProvisionalRenderSegmentKey("segment.color-glyph.adapter"),
                    drawPackets = listOf(packet),
                    batchEligibilityByPacketId = mapOf(
                        packet.packetId to GPUPassBatchEligibility(
                            kind = GPUPassBatchKind.Isolated,
                            queueGuard = GPUPassBatchQueueGuard(emptyList(), emptyList()),
                        ),
                    ),
                ),
            ),
            dependencies = emptyList(),
            phaseOrder = GPUTaskPhase.entries,
            memoryBudget = GPUFrameMemoryBudgetPlan(
                peakFrameTransientBytes = 0L,
                targetResidentBytes = 0L,
                categoryTotals = GPUFrameMemoryCategory.entries.associateWith { 0L },
                deviceLimitFacts = emptyList(),
                configuredAggregateBudgetBytes = 1L,
                diagnostic = null,
            ),
        )
        return when (
            val result = commonBuilder.build(
                request = GPUPreparedSurfaceFrameRequest(
                    baseTaskList = baseTaskList,
                    capabilities = request.capabilities,
                    target = request.target,
                    targetBounds = semantic.targetBounds,
                    semanticsByCommandId = mapOf(
                        semantic.payloadRef.commandIdValue to semantic,
                    ),
                    readbackRequestId = request.readbackRequestId,
                    targetFormat = GPUColorFormat.RGBA8UnormSrgb,
                ),
                configuredAggregateBudgetBytes = request.configuredAggregateBudgetBytes,
            )
        ) {
            is GPUPreparedSurfaceFrameResult.Recorded ->
                GPUColorGlyphPreparedTaskListResult.Recorded(result.taskList)
            is GPUPreparedSurfaceFrameResult.Refused ->
                GPUColorGlyphPreparedTaskListResult.Refused(result.diagnostic)
        }
    }

    private fun colorGlyphPacket(semantic: GPUDrawSemanticPayload.ColorGlyph): GPUDrawPacket {
        val commandId = semantic.payloadRef.commandIdValue
        return GPUDrawPacket(
            packetId = GPUDrawPacketID("packet.color-glyph.$commandId"),
            commandIdValue = commandId,
            analysisRecordId = "analysis.color-glyph.$commandId",
            passId = "pass.color-glyph.$commandId",
            layerId = "root",
            bindingListId = "bindings.color-glyph.$commandId",
            insertionReasonCode = "color-glyph-colrv0",
            sortKey = commandId.toLong(),
            sortKeyPreimage = "paint-order:$commandId",
            renderStepId = GPURenderStepID(COLOR_GLYPH_RENDER_STEP_IDENTITY),
            renderStepVersion = 1,
            role = GPUDrawPacketRole.Shading,
            blendPlan = preparedColorGlyphBlendPlan(),
            renderPipelineKey = COLOR_GLYPH_RENDER_PIPELINE_KEY,
            bindingLayoutHash = COLOR_GLYPH_BINDING_LAYOUT_HASH,
            uniformSlot = semantic.payloadRef.uniformSlot,
            clipCoveragePlan = GPUClipCoveragePlan.NoClip,
            clipExecutionPlan = GPUClipExecutionPlan.NoClip,
            vertexSourceLabel = COLOR_GLYPH_VERTEX_SOURCE_LABEL,
            scissorBoundsHash = colorGlyphScissorAuthority(semantic.scissorBounds),
            targetStateHash = COLOR_GLYPH_TARGET_STATE_HASH,
            originalPaintOrder = commandId,
            resourceGeneration = PREPARED_FRAME_LATE_BOUND_RESOURCE_GENERATION,
            frameProvenance = requireNotNull(semantic.frameProvenance),
        )
    }
}
