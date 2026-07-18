package org.graphiks.kanvas.gpu.renderer.recording

import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoveragePlan
import org.graphiks.kanvas.gpu.renderer.commands.GPUFrameProvenance
import org.graphiks.kanvas.gpu.renderer.color.GPUColorFormat
import org.graphiks.kanvas.gpu.renderer.color.GPUColorInterpretation
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnostic
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticCode
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticDomain
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticSeverity
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketID
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketRole
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlan
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchEligibility
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchKind
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchQueueGuard
import org.graphiks.kanvas.gpu.renderer.passes.GPUSamplePlan
import org.graphiks.kanvas.gpu.renderer.passes.GPURenderStepID
import org.graphiks.kanvas.gpu.renderer.payloads.CORE_PRIMITIVE_RENDER_STEP_IDENTITY
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.pipelines.GPURenderPipelineKey
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryAllocation
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryBudgetPlanner
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryBudgetRequest
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryCategory
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryResourceKind
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceLifetime
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTextureDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourcePreparationRequest
import org.graphiks.kanvas.gpu.renderer.state.GPULoadStorePlan
import org.graphiks.kanvas.gpu.renderer.state.GPUStorePlan

const val CORE_PRIMITIVE_RENDER_PIPELINE_KEY = "pipeline.core-primitive.rgba8unorm.single-sample"
const val CORE_PRIMITIVE_BINDING_LAYOUT_HASH = "layout.core-primitive.uniform32"
const val CORE_PRIMITIVE_TARGET_STATE_HASH = "target.rgba8unorm.single-sample"

data class GPUCorePrimitivePreparedDraw(
    val commandIdValue: Int,
    val paintOrder: Int,
    val blendPlan: GPUBlendPlan,
    val frameProvenance: GPUFrameProvenance,
    val clipCoveragePlan: GPUClipCoveragePlan,
)

data class GPUCorePrimitivePreparedFrameRequest(
    val baseTaskList: GPUTaskList,
    val capabilities: GPUCapabilities,
    val target: GPUFrameTargetRef,
    val targetBounds: GPUPixelBounds,
    val draws: List<GPUCorePrimitivePreparedDraw>,
    val semanticsByCommandId: Map<Int, GPUDrawSemanticPayload.CorePrimitive>,
    val readbackRequestId: GPUReadbackRequestID? = null,
    val configuredAggregateBudgetBytes: Long = 1L shl 30,
)

sealed interface GPUCorePrimitivePreparedFrameResult {
    data class Recorded(val taskList: GPUTaskList) : GPUCorePrimitivePreparedFrameResult
    data class Refused(val diagnostic: GPUDiagnostic) : GPUCorePrimitivePreparedFrameResult
}

/** Adds the canonical target/readback envelope without re-planning blend, geometry, or clip routing. */
class GPUCorePrimitivePreparedFrameTaskListBuilder(
    private val readbackLayoutPlanner: GPUReadbackLayoutPlanner = GPUReadbackLayoutPlanner(),
) {
    fun build(request: GPUCorePrimitivePreparedFrameRequest): GPUCorePrimitivePreparedFrameResult {
        if (request.targetBounds.left != 0 || request.targetBounds.top != 0 ||
            request.targetBounds.width <= 0 || request.targetBounds.height <= 0
        ) {
            return refused(
                "unsupported.recording.core_primitive_target",
                "Prepared core primitive recording requires one non-empty zero-origin target.",
            )
        }
        if (request.configuredAggregateBudgetBytes <= 0L) {
            return refused(
                "invalid.recording.core_primitive_budget",
                "Core primitive aggregate budget must be positive.",
            )
        }
        if (request.draws.isEmpty() || request.draws.map { it.commandIdValue }.distinct().size != request.draws.size) {
            return refused(
                "unsupported.recording.core_primitive_base_tasks",
                "Every Slice 12A visual command must produce one uniquely identified prepared draw.",
            )
        }
        if (request.draws.map(GPUCorePrimitivePreparedDraw::commandIdValue).toSet() !=
            request.semanticsByCommandId.keys
        ) {
            return refused(
                "invalid.recording.core_primitive_semantics",
                "Every core packet requires exactly one gathered semantic payload.",
            )
        }
        val limits = request.capabilities.limits ?: return refused(
            "unsupported.recording.core_primitive_limits_unavailable",
            "Prepared core primitive recording requires observed device limits.",
        )
        val targetBytes = try {
            Math.multiplyExact(
                Math.multiplyExact(request.targetBounds.width.toLong(), request.targetBounds.height.toLong()),
                4L,
            )
        } catch (_: ArithmeticException) {
            return refused(
                "unsupported.recording.core_primitive_target_size",
                "Core primitive target byte size exceeds signed 64-bit arithmetic.",
            )
        }
        val readbackRequest = request.readbackRequestId?.let { requestId ->
            GPUFrameReadbackRequest(
                requestId,
                request.targetBounds,
                GPUReadbackPixelFormat.Rgba8Unorm,
                GPUColorInterpretation("srgb-premul"),
            )
        }
        val readbackPlan = readbackRequest?.let { frameReadback ->
            when (val plan = readbackLayoutPlanner.plan(frameReadback, request.capabilities)) {
                is GPUReadbackLayoutPlan.Planned -> plan
                is GPUReadbackLayoutPlan.Refused -> return GPUCorePrimitivePreparedFrameResult.Refused(plan.diagnostic)
            }
        }
        val staging = readbackPlan?.let {
            GPUFrameBufferRef("buffer.core-primitive.readback.${request.baseTaskList.frameId.value}")
        }
        val preparations = mutableListOf(
            GPUResourcePreparationRequest(
                resource = request.target,
                descriptor = GPUFrameTextureDescriptor(
                    request.targetBounds,
                    GPUColorFormat("rgba8unorm"),
                    1,
                ),
                role = GPUFrameResourceRole.SceneTarget,
                usages = setOf(GPUFrameResourceUsage.RenderAttachment, GPUFrameResourceUsage.CopySource),
                lifetime = GPUFrameResourceLifetime.FrameLocal,
                byteSize = targetBytes,
                diagnosticLabel = "core-primitive.scene-target",
            ),
        )
        if (readbackPlan != null && staging != null) {
            preparations += GPUResourcePreparationRequest(
                resource = staging,
                descriptor = GPUFrameBufferDescriptor(readbackPlan.stagingDescriptor.minimumBufferBytes, 4L),
                role = GPUFrameResourceRole.ReadbackStaging,
                usages = setOf(GPUFrameResourceUsage.CopyDestination, GPUFrameResourceUsage.MapRead),
                lifetime = GPUFrameResourceLifetime.FrameLocal,
                byteSize = readbackPlan.stagingDescriptor.minimumBufferBytes,
                diagnosticLabel = "core-primitive.readback",
            )
        }
        val allocations = mutableListOf(
            GPUFrameMemoryAllocation(
                "core-primitive.scene-target",
                GPUFrameMemoryCategory.CanonicalTarget,
                targetBytes,
                GPUFrameMemoryResourceKind.Texture2D,
                request.targetBounds,
            ),
        )
        if (readbackPlan != null) {
            allocations += GPUFrameMemoryAllocation(
                "core-primitive.readback",
                GPUFrameMemoryCategory.ReadbackStaging,
                readbackPlan.stagingDescriptor.minimumBufferBytes,
                GPUFrameMemoryResourceKind.Buffer,
                null,
            )
        }
        val memoryBudget = GPUFrameMemoryBudgetPlanner.plan(
            GPUFrameMemoryBudgetRequest(allocations, request.configuredAggregateBudgetBytes, limits),
        )
        memoryBudget.diagnostic?.let { return GPUCorePrimitivePreparedFrameResult.Refused(it) }

        val prepareId = GPUTaskID("task.core-primitive.prepare.${request.baseTaskList.frameId.value}")
        val recordingId = request.baseTaskList.recordingSeals.single().recordingId
        val preparedPackets = request.draws.sortedBy(GPUCorePrimitivePreparedDraw::paintOrder).map { draw ->
            packet(draw, requireNotNull(request.semanticsByCommandId[draw.commandIdValue]))
        }
        val preparedRenders = listOf(
            GPUTask.Render(
                taskId = GPUTaskID("task.core-primitive.render.${request.baseTaskList.frameId.value}"),
                recordingId = recordingId,
                phase = GPUTaskPhase.Render,
                target = request.target,
                loadStore = GPULoadStorePlan("clear", GPUStorePlan.Store),
                samplePlan = GPUSamplePlan.SingleSampleFrame,
                drawPackets = preparedPackets,
                batchEligibilityByPacketId = preparedPackets.associate { packet ->
                    packet.packetId to GPUPassBatchEligibility(
                        kind = GPUPassBatchKind.SolidFill,
                        queueGuard = GPUPassBatchQueueGuard(emptyList(), emptyList()),
                    )
                },
            ),
        )
        val tasks = mutableListOf<GPUTask>(
            GPUTask.PrepareResources(
                prepareId,
                preparedRenders.first().recordingId,
                GPUTaskPhase.Prepare,
                preparations,
            ),
        )
        tasks += preparedRenders
        val dependencies = mutableListOf<GPUTaskDependency>()
        val orderedIds = listOf(prepareId) + preparedRenders.map(GPUTask.Render::taskId)
        orderedIds.zipWithNext().forEachIndexed { index, (from, to) ->
            dependencies += dependency(from, to, index)
        }
        if (readbackRequest != null && staging != null) {
            val readbackId = GPUTaskID("task.core-primitive.readback.${request.baseTaskList.frameId.value}")
            tasks += GPUTask.Readback(
                readbackId,
                preparedRenders.last().recordingId,
                GPUTaskPhase.Readback,
                request.target,
                staging,
                readbackRequest,
            )
            dependencies += dependency(preparedRenders.last().taskId, readbackId, dependencies.size)
        }
        return GPUCorePrimitivePreparedFrameResult.Recorded(
            GPUTaskList(
                frameId = request.baseTaskList.frameId,
                capabilitySeal = request.baseTaskList.capabilitySeal,
                recordingSeals = request.baseTaskList.recordingSeals,
                expectedReplayKeyHash = request.baseTaskList.expectedReplayKeyHash,
                tasks = tasks,
                dependencies = dependencies,
                phaseOrder = request.baseTaskList.phaseOrder,
                memoryBudget = memoryBudget,
                diagnostics = emptyList(),
            ),
        )
    }

    private fun packet(
        draw: GPUCorePrimitivePreparedDraw,
        semantic: GPUDrawSemanticPayload.CorePrimitive,
    ): GPUDrawPacket = GPUDrawPacket(
        packetId = GPUDrawPacketID("packet.${draw.commandIdValue}.0"),
        commandIdValue = draw.commandIdValue,
        analysisRecordId = "analysis.core-primitive.${draw.commandIdValue}",
        passId = "pass.core-primitive.prepared",
        layerId = "root",
        bindingListId = "bindings.core-primitive.${draw.commandIdValue}",
        insertionReasonCode = "core-primitive",
        sortKey = draw.paintOrder.toLong(),
        sortKeyPreimage = "paint-order:${draw.paintOrder}",
        renderStepId = GPURenderStepID(CORE_PRIMITIVE_RENDER_STEP_IDENTITY),
        renderStepVersion = 1,
        role = GPUDrawPacketRole.Shading,
        blendPlan = draw.blendPlan,
        renderPipelineKey = GPURenderPipelineKey(CORE_PRIMITIVE_RENDER_PIPELINE_KEY),
        bindingLayoutHash = CORE_PRIMITIVE_BINDING_LAYOUT_HASH,
        uniformSlot = semantic.payloadRef.uniformSlot,
        semanticPayload = semantic,
        vertexSourceLabel = "core-primitive-device-geometry",
        scissorBoundsHash = semantic.scissorBounds.canonicalScissor(),
        targetStateHash = CORE_PRIMITIVE_TARGET_STATE_HASH,
        originalPaintOrder = draw.paintOrder,
        resourceGeneration = PREPARED_FRAME_LATE_BOUND_RESOURCE_GENERATION,
        frameProvenance = draw.frameProvenance,
        clipCoveragePlan = draw.clipCoveragePlan,
    )

    private fun dependency(from: GPUTaskID, to: GPUTaskID, index: Int) = GPUTaskDependency(
        from,
        to,
        "prepared-scene-order",
        GPUTaskUseToken("prepared-core-primitive.$index"),
        "preserve.prepared-scene.order",
    )

    private fun refused(code: String, message: String) = GPUCorePrimitivePreparedFrameResult.Refused(
        GPUDiagnostic(
            GPUDiagnosticCode(code),
            GPUDiagnosticDomain.Recording,
            GPUDiagnosticSeverity.Error,
            message,
        ),
    )
}

private fun GPUPixelBounds.canonicalScissor(): String =
    "scissor_${left.toFloat()}_${top.toFloat()}_${right.toFloat()}_${bottom.toFloat()}"
