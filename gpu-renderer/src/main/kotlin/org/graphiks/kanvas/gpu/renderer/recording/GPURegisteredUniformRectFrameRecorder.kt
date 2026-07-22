package org.graphiks.kanvas.gpu.renderer.recording

import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
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
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchEligibility
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchKind
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchQueueGuard
import org.graphiks.kanvas.gpu.renderer.passes.GPURenderStepID
import org.graphiks.kanvas.gpu.renderer.passes.GPUSamplePlan
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.payloads.GPURegisteredUniformProgram
import org.graphiks.kanvas.gpu.renderer.payloads.GPURegisteredUniformRectPayloadGatherer
import org.graphiks.kanvas.gpu.renderer.payloads.REGISTERED_UNIFORM_RECT_RENDER_STEP_IDENTITY
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

/** One rectangle already lowered to pixels and bound to a closed registered shader ABI. */
data class GPURegisteredUniformRectResolvedDraw(
    val commandIdValue: Int,
    val bounds: GPUPixelBounds,
    val program: GPURegisteredUniformProgram,
    val uniformBytes: ByteArray,
    val scissorBounds: GPUPixelBounds = bounds,
    val paintOrder: Int = commandIdValue,
)

/** Handle-free input for one prepared batch which may mix registered uniform programs. */
data class GPURegisteredUniformRectFrameRecordingRequest(
    val frameId: GPUFrameID,
    val recordingId: GPURecordingID,
    val capabilities: GPUCapabilities,
    val deviceGeneration: GPUDeviceGenerationID,
    val target: GPUFrameTargetRef,
    val targetBounds: GPUPixelBounds,
    val draws: List<GPURegisteredUniformRectResolvedDraw>,
    val readbackRequestId: GPUReadbackRequestID? = null,
    val configuredAggregateBudgetBytes: Long = 1L shl 30,
)

sealed interface GPURegisteredUniformRectFrameRecordingResult {
    data class Recorded(
        val semantics: List<GPUDrawSemanticPayload.RegisteredUniformRect>,
        val taskList: GPUTaskList,
    ) : GPURegisteredUniformRectFrameRecordingResult

    data class Refused(val diagnostic: GPUDiagnostic) : GPURegisteredUniformRectFrameRecordingResult
}

/** Records one generic uniform-rectangle frame without carrying WGSL source through the frame plan. */
class GPURegisteredUniformRectFrameRecorder(
    private val gatherer: GPURegisteredUniformRectPayloadGatherer = GPURegisteredUniformRectPayloadGatherer(),
    private val readbackLayoutPlanner: GPUReadbackLayoutPlanner = GPUReadbackLayoutPlanner(),
) {
    fun record(
        request: GPURegisteredUniformRectFrameRecordingRequest,
    ): GPURegisteredUniformRectFrameRecordingResult {
        if (request.draws.isEmpty() ||
            request.draws.map { it.commandIdValue }.distinct().size != request.draws.size ||
            request.draws.any { it.commandIdValue < 0 || it.paintOrder < 0 }
        ) {
            return refused(
                "invalid.recording.registered_uniform_draws",
                "Registered uniform recording requires draws with unique non-negative identities.",
            )
        }
        if (request.targetBounds.left != 0 || request.targetBounds.top != 0) {
            return refused(
                "unsupported.recording.registered_uniform_target_origin",
                "Prepared registered uniform recording requires a zero-origin target.",
            )
        }
        if (request.draws.any { it.uniformBytes.size != it.program.uniformByteSize }) {
            return refused(
                "invalid.recording.registered_uniform_abi",
                "Every uniform block must match the exact byte size of its registered program.",
            )
        }
        if (request.draws.any { draw ->
                !request.targetBounds.contains(draw.bounds) || !draw.bounds.contains(draw.scissorBounds)
            }
        ) {
            return refused(
                "unsupported.recording.registered_uniform_bounds",
                "Every registered rectangle and scissor must be non-empty and contained by the target.",
            )
        }
        if (request.configuredAggregateBudgetBytes <= 0L) {
            return refused(
                "invalid.recording.registered_uniform_budget",
                "Registered uniform aggregate budget must be positive.",
            )
        }
        val limits = request.capabilities.limits ?: return refused(
            "unsupported.recording.registered_uniform_limits_unavailable",
            "Prepared registered uniform recording requires observed device limits.",
        )
        val targetBytes = try {
            Math.multiplyExact(
                Math.multiplyExact(request.targetBounds.width.toLong(), request.targetBounds.height.toLong()),
                4L,
            )
        } catch (_: ArithmeticException) {
            return refused(
                "unsupported.recording.registered_uniform_target_size",
                "Registered uniform target byte size exceeds signed 64-bit arithmetic.",
            )
        }
        val readbackRequest = request.readbackRequestId?.let { requestId ->
            GPUFrameReadbackRequest(
                requestId,
                request.targetBounds,
                GPUReadbackPixelFormat.Rgba8Unorm,
                GPUColorInterpretation.EncodedPremulSrgb,
            )
        }
        val readbackPlan = readbackRequest?.let { frameReadback ->
            when (val plan = readbackLayoutPlanner.plan(frameReadback, request.capabilities)) {
                is GPUReadbackLayoutPlan.Planned -> plan
                is GPUReadbackLayoutPlan.Refused ->
                    return GPURegisteredUniformRectFrameRecordingResult.Refused(plan.diagnostic)
            }
        }
        val staging = readbackPlan?.let {
            GPUFrameBufferRef("buffer.registered-uniform.readback.${request.frameId.value}")
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
                diagnosticLabel = "registered-uniform.scene-target",
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
                diagnosticLabel = "registered-uniform.readback",
            )
        }
        val allocations = mutableListOf(
            GPUFrameMemoryAllocation(
                "registered-uniform.scene-target",
                GPUFrameMemoryCategory.CanonicalTarget,
                targetBytes,
                GPUFrameMemoryResourceKind.Texture2D,
                request.targetBounds,
            ),
        )
        readbackPlan?.let { plan ->
            allocations += GPUFrameMemoryAllocation(
                "registered-uniform.readback",
                GPUFrameMemoryCategory.ReadbackStaging,
                plan.stagingDescriptor.minimumBufferBytes,
                GPUFrameMemoryResourceKind.Buffer,
                null,
            )
        }
        val memoryBudget = GPUFrameMemoryBudgetPlanner.plan(
            GPUFrameMemoryBudgetRequest(allocations, request.configuredAggregateBudgetBytes, limits),
        )
        memoryBudget.diagnostic?.let {
            return GPURegisteredUniformRectFrameRecordingResult.Refused(it)
        }

        val ordered = request.draws.sortedWith(
            compareBy<GPURegisteredUniformRectResolvedDraw> { it.paintOrder }.thenBy { it.commandIdValue },
        )
        val semantics = ordered.map { draw ->
            gatherer.gatherSemantic(
                draw.commandIdValue,
                draw.program,
                draw.uniformBytes,
                request.targetBounds,
                draw.scissorBounds,
            )
        }
        val packets = ordered.zip(semantics).map { (draw, semantic) -> packet(draw, semantic) }
        val prepareId = GPUTaskID("task.registered-uniform.prepare.${request.frameId.value}")
        val renderId = GPUTaskID("task.registered-uniform.render.${request.frameId.value}")
        val readbackId = readbackRequest?.let {
            GPUTaskID("task.registered-uniform.readback.${request.frameId.value}")
        }
        val tasks = mutableListOf<GPUTask>(
            GPUTask.PrepareResources(
                prepareId,
                request.recordingId,
                GPUTaskPhase.Prepare,
                preparations,
            ),
            GPUTask.Render(
                taskId = renderId,
                recordingId = request.recordingId,
                phase = GPUTaskPhase.Render,
                target = request.target,
                loadStore = GPULoadStorePlan("clear", GPUStorePlan.Store),
                samplePlan = GPUSamplePlan.SingleSampleFrame,
                drawPackets = packets,
                batchEligibilityByPacketId = packets.associate { packet ->
                    packet.packetId to GPUPassBatchEligibility(
                        kind = GPUPassBatchKind.SimpleGradient,
                        queueGuard = GPUPassBatchQueueGuard(emptyList(), emptyList()),
                    )
                },
            ),
        )
        val dependencies = mutableListOf(dependency(prepareId, renderId, 0))
        if (readbackRequest != null && staging != null && readbackId != null) {
            tasks += GPUTask.Readback(
                readbackId,
                request.recordingId,
                GPUTaskPhase.Readback,
                request.target,
                staging,
                readbackRequest,
            )
            dependencies += dependency(renderId, readbackId, 1)
        }
        val capabilitySeal = GPUFrameCapabilitySeal.capture(
            request.frameId,
            request.deviceGeneration,
            request.capabilities,
        )
        val replayHash = "registered-uniform:${semantics.joinToString(".") { it.canonicalHash }}"
        return GPURegisteredUniformRectFrameRecordingResult.Recorded(
            semantics,
            GPUTaskList(
                frameId = request.frameId,
                capabilitySeal = capabilitySeal,
                recordingSeals = listOf(
                    GPURecordingSeal(
                        request.recordingId,
                        0L,
                        replayHash,
                        replayHash,
                        capabilitySeal.sealHash,
                    ),
                ),
                expectedReplayKeyHash = replayHash,
                tasks = tasks,
                dependencies = dependencies,
                phaseOrder = GPUTaskPhase.entries,
                memoryBudget = memoryBudget,
            ),
        )
    }

    private fun packet(
        draw: GPURegisteredUniformRectResolvedDraw,
        semantic: GPUDrawSemanticPayload.RegisteredUniformRect,
    ) = GPUDrawPacket(
        packetId = GPUDrawPacketID("packet.registered-uniform.${draw.commandIdValue}"),
        commandIdValue = draw.commandIdValue,
        analysisRecordId = "analysis.registered-uniform.${draw.commandIdValue}",
        passId = "pass.registered-uniform.prepared",
        layerId = "root",
        bindingListId = "bindings.registered-uniform.${draw.commandIdValue}",
        insertionReasonCode = "registered-uniform-rect",
        sortKey = draw.paintOrder.toLong(),
        sortKeyPreimage = "paint-order:${draw.paintOrder}",
        renderStepId = GPURenderStepID(REGISTERED_UNIFORM_RECT_RENDER_STEP_IDENTITY),
        renderStepVersion = 1,
        role = GPUDrawPacketRole.Shading,
        blendPlan = canonicalSolidRectSrcOverBlendPlan(),
        renderPipelineKey = registeredUniformRectPipelineKey(draw.program),
        bindingLayoutHash = REGISTERED_UNIFORM_RECT_BINDING_LAYOUT_HASH,
        uniformSlot = semantic.payloadRef.uniformSlot,
        semanticPayload = semantic,
        vertexSourceLabel = REGISTERED_UNIFORM_RECT_VERTEX_SOURCE_LABEL,
        scissorBoundsHash = registeredUniformRectScissorAuthority(draw.scissorBounds),
        targetStateHash = REGISTERED_UNIFORM_RECT_TARGET_STATE_HASH,
        originalPaintOrder = draw.paintOrder,
        resourceGeneration = PREPARED_FRAME_LATE_BOUND_RESOURCE_GENERATION,
    )

    private fun dependency(from: GPUTaskID, to: GPUTaskID, index: Int) = GPUTaskDependency(
        from,
        to,
        "prepared-scene-order",
        GPUTaskUseToken("prepared-registered-uniform.$index"),
        "preserve.prepared-scene.order",
    )

    private fun refused(code: String, message: String) =
        GPURegisteredUniformRectFrameRecordingResult.Refused(
            GPUDiagnostic(
                GPUDiagnosticCode(code),
                GPUDiagnosticDomain.Recording,
                GPUDiagnosticSeverity.Error,
                message,
            ),
        )
}

internal fun registeredUniformRectPipelineKey(
    program: GPURegisteredUniformProgram,
): GPURenderPipelineKey = GPURenderPipelineKey(
    "pipeline.registered-uniform.${program.wireId}.rgba8unorm.single-sample",
)

internal fun registeredUniformRectScissorAuthority(bounds: GPUPixelBounds): String =
    "scissor_${bounds.left.toFloat()}_${bounds.top.toFloat()}_${bounds.right.toFloat()}_${bounds.bottom.toFloat()}"

internal const val REGISTERED_UNIFORM_RECT_BINDING_LAYOUT_HASH = "layout.registered-uniform.fragment-uniform-v1"
internal const val REGISTERED_UNIFORM_RECT_VERTEX_SOURCE_LABEL = "fullscreen-triangle"
internal const val REGISTERED_UNIFORM_RECT_TARGET_STATE_HASH = "target.rgba8unorm.single-sample"

private fun GPUPixelBounds.contains(other: GPUPixelBounds): Boolean =
    other.right > other.left && other.bottom > other.top &&
        other.left >= left && other.top >= top && other.right <= right && other.bottom <= bottom
