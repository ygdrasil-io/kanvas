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
import org.graphiks.kanvas.gpu.renderer.payloads.GPUMaterialPayload
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPayloadGatherPlan
import org.graphiks.kanvas.gpu.renderer.payloads.GPUSolidPayloadGatherer
import org.graphiks.kanvas.gpu.renderer.pipelines.GPURenderPipelineKey
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameCapabilitySeal
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameID
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameReadbackRequest
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackPixelFormat
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackRequestID
import org.graphiks.kanvas.gpu.renderer.recording.GPURecordingID
import org.graphiks.kanvas.gpu.renderer.recording.GPURecordingSeal
import org.graphiks.kanvas.gpu.renderer.recording.GPUTask
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskDependency
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskID
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskList
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskPhase
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskUseToken
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

/** One opaque rectangle already lowered into target pixel coordinates. */
data class GPUSolidRectFrameResolvedDraw(
    val commandIdValue: Int,
    val bounds: GPUPixelBounds,
    val rgba: List<Float>,
    val scissorBounds: GPUPixelBounds = bounds,
    val paintOrder: Int = commandIdValue,
)

/** Public, handle-free input for one homogeneous prepared SolidRect frame. */
data class GPUSolidRectFrameRecordingRequest(
    val frameId: GPUFrameID,
    val recordingId: GPURecordingID,
    val capabilities: GPUCapabilities,
    val deviceGeneration: GPUDeviceGenerationID,
    val target: GPUFrameTargetRef,
    val targetBounds: GPUPixelBounds,
    val draws: List<GPUSolidRectFrameResolvedDraw>,
    val readbackRequestId: GPUReadbackRequestID? = null,
    val configuredAggregateBudgetBytes: Long = 1L shl 30,
)

sealed interface GPUSolidRectFrameRecordingResult {
    data class Recorded(
        val semantics: List<GPUDrawSemanticPayload.SolidRect>,
        val taskList: GPUTaskList,
    ) : GPUSolidRectFrameRecordingResult

    data class Refused(val diagnostic: GPUDiagnostic) : GPUSolidRectFrameRecordingResult
}

/** Complete recording facade for the cached native SolidRect prepared-session route. */
class GPUSolidRectFrameRecorder(
    private val gatherer: GPUSolidPayloadGatherer = GPUSolidPayloadGatherer(),
    private val readbackLayoutPlanner: GPUReadbackLayoutPlanner = GPUReadbackLayoutPlanner(),
) {
    fun record(request: GPUSolidRectFrameRecordingRequest): GPUSolidRectFrameRecordingResult {
        if (request.draws.isEmpty() || request.draws.map { it.commandIdValue }.distinct().size != request.draws.size) {
            return refused(
                "invalid.recording.solid_rect_draws",
                "SolidRect recording requires at least one draw with unique command IDs.",
            )
        }
        if (request.targetBounds.left != 0 || request.targetBounds.top != 0) {
            return refused(
                "unsupported.recording.solid_rect_target_origin",
                "Prepared SolidRect recording requires a zero-origin target.",
            )
        }
        if (request.configuredAggregateBudgetBytes <= 0L) {
            return refused("invalid.recording.solid_rect_budget", "SolidRect aggregate budget must be positive.")
        }
        if (request.draws.any { draw ->
                draw.rgba.size != 4 || draw.rgba.any { !it.isFinite() || it !in 0f..1f }
            }
        ) {
            return refused(
                "invalid.recording.solid_rect_color",
                "SolidRect colors require four finite normalized RGBA channels.",
            )
        }
        if (request.draws.any { draw ->
                !request.targetBounds.contains(draw.bounds) || !draw.bounds.contains(draw.scissorBounds)
            }
        ) {
            return refused(
                "unsupported.recording.solid_rect_bounds",
                "Every SolidRect and scissor must be non-empty and contained by the target.",
            )
        }
        val limits = request.capabilities.limits ?: return refused(
            "unsupported.recording.solid_rect_limits_unavailable",
            "Prepared SolidRect recording requires observed device limits.",
        )
        val targetBytes = try {
            Math.multiplyExact(
                Math.multiplyExact(request.targetBounds.width.toLong(), request.targetBounds.height.toLong()),
                4L,
            )
        } catch (_: ArithmeticException) {
            return refused(
                "unsupported.recording.solid_rect_target_size",
                "SolidRect target byte size exceeds signed 64-bit arithmetic.",
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
                is GPUReadbackLayoutPlan.Refused -> return GPUSolidRectFrameRecordingResult.Refused(plan.diagnostic)
            }
        }
        val staging = readbackPlan?.let {
            GPUFrameBufferRef("buffer.solid-rect.readback.${request.frameId.value}")
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
                diagnosticLabel = "solid-rect.scene-target",
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
                diagnosticLabel = "solid-rect.readback",
            )
        }
        val allocations = listOf(
            GPUFrameMemoryAllocation(
                "solid-rect.scene-target",
                GPUFrameMemoryCategory.CanonicalTarget,
                targetBytes,
                GPUFrameMemoryResourceKind.Texture2D,
                request.targetBounds,
            ),
        ) + if (readbackPlan == null) {
            emptyList()
        } else {
            listOf(
                GPUFrameMemoryAllocation(
                    "solid-rect.readback",
                    GPUFrameMemoryCategory.ReadbackStaging,
                    readbackPlan.stagingDescriptor.minimumBufferBytes,
                    GPUFrameMemoryResourceKind.Buffer,
                    null,
                ),
            )
        }
        val memoryBudget = GPUFrameMemoryBudgetPlanner.plan(
            GPUFrameMemoryBudgetRequest(allocations, request.configuredAggregateBudgetBytes, limits),
        )
        memoryBudget.diagnostic?.let { return GPUSolidRectFrameRecordingResult.Refused(it) }

        val ordered = request.draws.sortedWith(
            compareBy<GPUSolidRectFrameResolvedDraw> { it.paintOrder }.thenBy { it.commandIdValue },
        )
        val semantics = ordered.map(::gatherSemantic)
        val packets = ordered.zip(semantics).map { (draw, semantic) -> packet(draw, semantic) }
        val prepareId = GPUTaskID("task.solid-rect.prepare.${request.frameId.value}")
        val renderId = GPUTaskID("task.solid-rect.render.${request.frameId.value}")
        val readbackId = readbackRequest?.let {
            GPUTaskID("task.solid-rect.readback.${request.frameId.value}")
        }
        val prepare = GPUTask.PrepareResources(
            prepareId,
            request.recordingId,
            GPUTaskPhase.Prepare,
            preparations,
        )
        val render = GPUTask.Render(
            taskId = renderId,
            recordingId = request.recordingId,
            phase = GPUTaskPhase.Render,
            target = request.target,
            loadStore = GPULoadStorePlan("clear", GPUStorePlan.Store),
            samplePlan = GPUSamplePlan.SingleSampleFrame,
            drawPackets = packets,
            batchEligibilityByPacketId = packets.associate { packet ->
                packet.packetId to GPUPassBatchEligibility(
                    kind = GPUPassBatchKind.SolidFill,
                    queueGuard = GPUPassBatchQueueGuard(emptyList(), emptyList()),
                )
            },
        )
        val tasks = mutableListOf<GPUTask>(prepare, render)
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
        val seal = GPUFrameCapabilitySeal.capture(request.frameId, request.deviceGeneration, request.capabilities)
        val replayHash = "solid-rect:${semantics.joinToString(".") {
            requireNotNull(it.payloadRef.uniformSlot).fingerprint.value
        }}"
        return GPUSolidRectFrameRecordingResult.Recorded(
            semantics,
            GPUTaskList(
                frameId = request.frameId,
                capabilitySeal = seal,
                recordingSeals = listOf(
                    GPURecordingSeal(request.recordingId, 0L, replayHash, replayHash, seal.sealHash),
                ),
                expectedReplayKeyHash = replayHash,
                tasks = tasks,
                dependencies = dependencies,
                phaseOrder = GPUTaskPhase.entries,
                memoryBudget = memoryBudget,
            ),
        )
    }

    private fun gatherSemantic(draw: GPUSolidRectFrameResolvedDraw): GPUDrawSemanticPayload.SolidRect =
        gatherer.gatherSemantic(
            GPUPayloadGatherPlan(
                planHash = "solid.gather.${draw.commandIdValue}",
                commandFamily = "FillRect",
                materialAssemblyHash = "solid.material.prepared",
                renderStepIdentity = SOLID_RECT_RENDER_STEP,
                writePlanHash = "solid.write.prepared",
                bindingPlanHash = "solid.binding.prepared",
                uploadPlanHash = "solid.upload.prepared",
                dedupScope = "pass.solid-rect.prepared",
            ),
            GPUMaterialPayload(
                materialKeyHash = "solid.material.key.${draw.commandIdValue}",
                payloadClass = "solid-rgba-rect",
                valueFacts = mapOf(
                    "command.id" to draw.commandIdValue.toString(),
                    "rect.left" to draw.bounds.left.toFloat().toString(),
                    "rect.top" to draw.bounds.top.toFloat().toString(),
                    "rect.right" to draw.bounds.right.toFloat().toString(),
                    "rect.bottom" to draw.bounds.bottom.toFloat().toString(),
                    "radii.topLeft" to "0.0",
                    "radii.topRight" to "0.0",
                    "radii.bottomRight" to "0.0",
                    "radii.bottomLeft" to "0.0",
                    "color.r" to draw.rgba[0].toString(),
                    "color.g" to draw.rgba[1].toString(),
                    "color.b" to draw.rgba[2].toString(),
                    "color.a" to draw.rgba[3].toString(),
                ),
                resourceFacts = emptyMap(),
                diagnosticLabel = "solid.prepared.${draw.commandIdValue}",
            ),
        )

    private fun packet(
        draw: GPUSolidRectFrameResolvedDraw,
        semantic: GPUDrawSemanticPayload.SolidRect,
    ) = GPUDrawPacket(
        packetId = GPUDrawPacketID("packet.solid-rect.${draw.commandIdValue}"),
        commandIdValue = draw.commandIdValue,
        analysisRecordId = "analysis.solid-rect.${draw.commandIdValue}",
        passId = "pass.solid-rect.prepared",
        layerId = "root",
        bindingListId = "bindings.solid-rect.${draw.commandIdValue}",
        insertionReasonCode = "solid-fill",
        sortKey = draw.paintOrder.toLong(),
        sortKeyPreimage = "paint-order:${draw.paintOrder}",
        renderStepId = GPURenderStepID(SOLID_RECT_RENDER_STEP),
        renderStepVersion = 1,
        role = GPUDrawPacketRole.Shading,
        blendPlan = canonicalSolidRectSrcOverBlendPlan(),
        renderPipelineKey = GPURenderPipelineKey(SOLID_RECT_PIPELINE_KEY),
        bindingLayoutHash = SOLID_RECT_BINDING_LAYOUT_HASH,
        uniformSlot = semantic.payloadRef.uniformSlot,
        semanticPayload = semantic,
        vertexSourceLabel = "fullscreen-triangle",
        scissorBoundsHash = draw.scissorBounds.canonicalScissor(),
        targetStateHash = "target.rgba8unorm.single-sample",
        originalPaintOrder = draw.paintOrder,
        resourceGeneration = PREPARED_FRAME_LATE_BOUND_RESOURCE_GENERATION,
    )

    private fun dependency(from: GPUTaskID, to: GPUTaskID, index: Int) = GPUTaskDependency(
        from,
        to,
        "prepared-scene-order",
        GPUTaskUseToken("prepared-solid-rect.$index"),
        "preserve.prepared-scene.order",
    )

    private fun refused(code: String, message: String) = GPUSolidRectFrameRecordingResult.Refused(
        GPUDiagnostic(
            GPUDiagnosticCode(code),
            GPUDiagnosticDomain.Recording,
            GPUDiagnosticSeverity.Error,
            message,
        ),
    )
}

private fun GPUPixelBounds.contains(other: GPUPixelBounds): Boolean =
    other.right > other.left && other.bottom > other.top &&
        other.left >= left && other.top >= top && other.right <= right && other.bottom <= bottom

private fun GPUPixelBounds.canonicalScissor(): String =
    "scissor_${left.toFloat()}_${top.toFloat()}_${right.toFloat()}_${bottom.toFloat()}"

private const val SOLID_RECT_RENDER_STEP = "rect.fill.coverage"
private const val SOLID_RECT_PIPELINE_KEY = "pipeline.solid-rect.rgba8unorm.single-sample"
private const val SOLID_RECT_BINDING_LAYOUT_HASH = "layout.solid-rect.uniform64"
