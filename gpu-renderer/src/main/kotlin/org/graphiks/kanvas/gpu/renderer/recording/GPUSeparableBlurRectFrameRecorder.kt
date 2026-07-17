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
import org.graphiks.kanvas.gpu.renderer.filters.GaussianKernelCache
import org.graphiks.kanvas.gpu.renderer.filters.MAX_MASK_BLUR_TAPS
import org.graphiks.kanvas.gpu.renderer.filters.SeparableBlurQualityTier
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketID
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketRole
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchEligibility
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchKind
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchQueueGuard
import org.graphiks.kanvas.gpu.renderer.passes.GPURenderStepID
import org.graphiks.kanvas.gpu.renderer.passes.GPUSamplePlan
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.payloads.GPUSeparableBlurRectPayloadGatherer
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
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUse
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTextureDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourcePreparationRequest
import org.graphiks.kanvas.gpu.renderer.state.GPULoadStorePlan
import org.graphiks.kanvas.gpu.renderer.state.GPUStorePlan

/** Closed stages for one source -> horizontal -> vertical prepared blur. */
enum class GPUSeparableBlurRectStage(val wireId: String) {
    Source("source"),
    Horizontal("horizontal"),
    Vertical("vertical"),
}

data class GPUSeparableBlurRectFrameRecordingRequest(
    val frameId: GPUFrameID,
    val recordingId: GPURecordingID,
    val capabilities: GPUCapabilities,
    val deviceGeneration: GPUDeviceGenerationID,
    val target: GPUFrameTargetRef,
    val targetBounds: GPUPixelBounds,
    val sourceBounds: GPUPixelBounds,
    val sourcePremultipliedRgba: FloatArray,
    val clearPremultipliedRgba: FloatArray,
    val sigma: Float,
    val readbackRequestId: GPUReadbackRequestID? = null,
    val configuredAggregateBudgetBytes: Long = 1L shl 30,
)

sealed interface GPUSeparableBlurRectFrameRecordingResult {
    data class Recorded(
        val semantic: GPUDrawSemanticPayload.SeparableBlurRect,
        val taskList: GPUTaskList,
    ) : GPUSeparableBlurRectFrameRecordingResult

    data class Refused(val diagnostic: GPUDiagnostic) : GPUSeparableBlurRectFrameRecordingResult
}

/** Records the bounded real Gaussian blur slice without WGSL or native handles in the frame plan. */
class GPUSeparableBlurRectFrameRecorder(
    private val gatherer: GPUSeparableBlurRectPayloadGatherer = GPUSeparableBlurRectPayloadGatherer(),
    private val kernelCache: GaussianKernelCache = GaussianKernelCache(),
    private val readbackLayoutPlanner: GPUReadbackLayoutPlanner = GPUReadbackLayoutPlanner(),
) {
    fun record(request: GPUSeparableBlurRectFrameRecordingRequest): GPUSeparableBlurRectFrameRecordingResult {
        if (request.targetBounds.left != 0 || request.targetBounds.top != 0 ||
            request.targetBounds.width > MAX_BLUR_TARGET_DIMENSION ||
            request.targetBounds.height > MAX_BLUR_TARGET_DIMENSION
        ) {
            return refused(
                "unsupported.recording.separable_blur.target",
                "Prepared separable blur requires a zero-origin target up to 2048x2048.",
            )
        }
        if (!request.targetBounds.containsNonEmpty(request.sourceBounds)) {
            return refused(
                "unsupported.recording.separable_blur.source_bounds",
                "Prepared separable blur source bounds must be non-empty and contained by the target.",
            )
        }
        if (!request.sigma.isFinite() || request.sigma !in MIN_BLUR_SIGMA..MAX_BLUR_SIGMA) {
            return refused(
                "unsupported.recording.separable_blur.sigma",
                "Prepared separable blur sigma must be finite and in 0.5..12.",
            )
        }
        if (!request.sourcePremultipliedRgba.isPremultipliedRgba() ||
            !request.clearPremultipliedRgba.isPremultipliedRgba()
        ) {
            return refused(
                "invalid.recording.separable_blur.color",
                "Prepared separable blur requires finite premultiplied source and clear colors.",
            )
        }
        if (request.configuredAggregateBudgetBytes <= 0L) {
            return refused(
                "invalid.recording.separable_blur.budget",
                "Prepared separable blur aggregate budget must be positive.",
            )
        }
        val limits = request.capabilities.limits ?: return refused(
            "unsupported.recording.separable_blur.limits_unavailable",
            "Prepared separable blur requires observed device limits.",
        )
        val targetBytes = try {
            Math.multiplyExact(
                Math.multiplyExact(request.targetBounds.width.toLong(), request.targetBounds.height.toLong()),
                RGBA_BYTES_PER_PIXEL,
            )
        } catch (_: ArithmeticException) {
            return refused(
                "unsupported.recording.separable_blur.target_size",
                "Prepared separable blur target byte size exceeds signed 64-bit arithmetic.",
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
                is GPUReadbackLayoutPlan.Refused ->
                    return GPUSeparableBlurRectFrameRecordingResult.Refused(plan.diagnostic)
            }
        }
        val sourceTarget = GPUFrameTargetRef("target.blur.source.${request.frameId.value}")
        val scratchTarget = GPUFrameTargetRef("target.blur.scratch.${request.frameId.value}")
        val staging = readbackPlan?.let {
            GPUFrameBufferRef("buffer.blur.readback.${request.frameId.value}")
        }
        val textureDescriptor = GPUFrameTextureDescriptor(
            request.targetBounds,
            GPUColorFormat(RGBA8_UNORM),
            1,
        )
        val preparations = mutableListOf(
            GPUResourcePreparationRequest(
                request.target,
                textureDescriptor,
                GPUFrameResourceRole.SceneTarget,
                setOf(GPUFrameResourceUsage.RenderAttachment, GPUFrameResourceUsage.CopySource),
                GPUFrameResourceLifetime.FrameLocal,
                targetBytes,
                "separable-blur.scene-target",
            ),
            GPUResourcePreparationRequest(
                sourceTarget,
                textureDescriptor,
                GPUFrameResourceRole.FilterTarget,
                setOf(GPUFrameResourceUsage.RenderAttachment, GPUFrameResourceUsage.TextureBinding),
                GPUFrameResourceLifetime.FrameLocal,
                targetBytes,
                "separable-blur.source-target",
            ),
            GPUResourcePreparationRequest(
                scratchTarget,
                textureDescriptor,
                GPUFrameResourceRole.FilterTarget,
                setOf(GPUFrameResourceUsage.RenderAttachment, GPUFrameResourceUsage.TextureBinding),
                GPUFrameResourceLifetime.FrameLocal,
                targetBytes,
                "separable-blur.scratch-target",
            ),
        )
        if (readbackPlan != null && staging != null) {
            preparations += GPUResourcePreparationRequest(
                staging,
                GPUFrameBufferDescriptor(readbackPlan.stagingDescriptor.minimumBufferBytes, 4L),
                GPUFrameResourceRole.ReadbackStaging,
                setOf(GPUFrameResourceUsage.CopyDestination, GPUFrameResourceUsage.MapRead),
                GPUFrameResourceLifetime.FrameLocal,
                readbackPlan.stagingDescriptor.minimumBufferBytes,
                "separable-blur.readback",
            )
        }
        val allocations = mutableListOf(
            GPUFrameMemoryAllocation(
                "separable-blur.scene-target",
                GPUFrameMemoryCategory.CanonicalTarget,
                targetBytes,
                GPUFrameMemoryResourceKind.Texture2D,
                request.targetBounds,
            ),
            GPUFrameMemoryAllocation(
                "separable-blur.source-target",
                GPUFrameMemoryCategory.FilterTarget,
                targetBytes,
                GPUFrameMemoryResourceKind.Texture2D,
                request.targetBounds,
            ),
            GPUFrameMemoryAllocation(
                "separable-blur.scratch-target",
                GPUFrameMemoryCategory.FilterTarget,
                targetBytes,
                GPUFrameMemoryResourceKind.Texture2D,
                request.targetBounds,
            ),
        )
        readbackPlan?.let { plan ->
            allocations += GPUFrameMemoryAllocation(
                "separable-blur.readback",
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
            return GPUSeparableBlurRectFrameRecordingResult.Refused(it)
        }

        val tapCount = SeparableBlurQualityTier.NORMAL.tapCount(request.sigma)
            .coerceIn(3, MAX_MASK_BLUR_TAPS)
        val activeKernel = kernelCache.getOrCompute(request.sigma, tapCount)
        val paddedWeights = FloatArray(MAX_MASK_BLUR_TAPS)
        activeKernel.weights.copyInto(paddedWeights)
        val semantic = gatherer.gatherSemantic(
            commandIdValue = 1,
            sourcePremultipliedRgba = request.sourcePremultipliedRgba,
            clearPremultipliedRgba = request.clearPremultipliedRgba,
            sourceBounds = request.sourceBounds,
            targetBounds = request.targetBounds,
            effectiveSigma = request.sigma,
            tapCount = tapCount,
            weights = paddedWeights,
        )
        val prepareId = GPUTaskID("task.blur.prepare.${request.frameId.value}")
        val sourceId = GPUTaskID("task.blur.source.${request.frameId.value}")
        val horizontalId = GPUTaskID("task.blur.horizontal.${request.frameId.value}")
        val verticalId = GPUTaskID("task.blur.vertical.${request.frameId.value}")
        val readbackId = readbackRequest?.let { GPUTaskID("task.blur.readback.${request.frameId.value}") }
        val sourcePacket = packet(GPUSeparableBlurRectStage.Source, semantic, request.sourceBounds)
        val horizontalPacket = packet(GPUSeparableBlurRectStage.Horizontal, semantic, request.targetBounds)
        val verticalPacket = packet(GPUSeparableBlurRectStage.Vertical, semantic, request.targetBounds)
        val tasks = mutableListOf<GPUTask>(
            GPUTask.PrepareResources(prepareId, request.recordingId, GPUTaskPhase.Prepare, preparations),
            renderTask(sourceId, request.recordingId, sourceTarget, sourcePacket),
            renderTask(
                horizontalId,
                request.recordingId,
                scratchTarget,
                horizontalPacket,
                resourceUses = listOf(
                    GPUFrameResourceUse(
                        sourceTarget,
                        GPUFrameResourceRole.FilterTarget,
                        GPUFrameResourceUsage.TextureBinding,
                        GPUFrameResourceLifetime.FrameLocal,
                        write = false,
                    ),
                ),
            ),
            renderTask(
                verticalId,
                request.recordingId,
                request.target,
                verticalPacket,
                resourceUses = listOf(
                    GPUFrameResourceUse(
                        scratchTarget,
                        GPUFrameResourceRole.FilterTarget,
                        GPUFrameResourceUsage.TextureBinding,
                        GPUFrameResourceLifetime.FrameLocal,
                        write = false,
                    ),
                ),
            ),
        )
        val dependencies = mutableListOf(
            dependency(prepareId, sourceId, 0),
            dependency(sourceId, horizontalId, 1),
            dependency(horizontalId, verticalId, 2),
        )
        if (readbackRequest != null && staging != null && readbackId != null) {
            tasks += GPUTask.Readback(
                readbackId,
                request.recordingId,
                GPUTaskPhase.Readback,
                request.target,
                staging,
                readbackRequest,
            )
            dependencies += dependency(verticalId, readbackId, 3)
        }
        val capabilitySeal = GPUFrameCapabilitySeal.capture(
            request.frameId,
            request.deviceGeneration,
            request.capabilities,
        )
        val replayHash = "separable-blur:${semantic.canonicalHash}"
        return GPUSeparableBlurRectFrameRecordingResult.Recorded(
            semantic,
            GPUTaskList(
                request.frameId,
                capabilitySeal,
                listOf(
                    GPURecordingSeal(
                        request.recordingId,
                        0L,
                        replayHash,
                        replayHash,
                        capabilitySeal.sealHash,
                    ),
                ),
                replayHash,
                tasks,
                dependencies,
                GPUTaskPhase.entries,
                memoryBudget,
            ),
        )
    }

    private fun renderTask(
        taskId: GPUTaskID,
        recordingId: GPURecordingID,
        target: GPUFrameTargetRef,
        packet: GPUDrawPacket,
        resourceUses: List<GPUFrameResourceUse> = emptyList(),
    ) = GPUTask.Render(
        taskId = taskId,
        recordingId = recordingId,
        phase = GPUTaskPhase.Render,
        target = target,
        loadStore = GPULoadStorePlan("clear", GPUStorePlan.Store),
        samplePlan = GPUSamplePlan.SingleSampleFrame,
        resourceUses = resourceUses,
        drawPackets = listOf(packet),
        batchEligibilityByPacketId = mapOf(
            packet.packetId to GPUPassBatchEligibility(
                kind = GPUPassBatchKind.SimpleGradient,
                queueGuard = GPUPassBatchQueueGuard(emptyList(), emptyList()),
            ),
        ),
    )

    private fun packet(
        stage: GPUSeparableBlurRectStage,
        semantic: GPUDrawSemanticPayload.SeparableBlurRect,
        scissor: GPUPixelBounds,
    ) = GPUDrawPacket(
        packetId = GPUDrawPacketID("packet.separable-blur.${stage.wireId}"),
        commandIdValue = semantic.payloadRef.commandIdValue,
        analysisRecordId = "analysis.separable-blur.${stage.wireId}",
        passId = "pass.separable-blur.${stage.wireId}",
        layerId = "root",
        bindingListId = "bindings.separable-blur.${stage.wireId}",
        insertionReasonCode = "separable-blur-${stage.wireId}",
        sortKey = stage.ordinal.toLong(),
        sortKeyPreimage = "separable-blur-stage:${stage.ordinal}",
        renderStepId = GPURenderStepID(separableBlurRectRenderStepId(stage)),
        renderStepVersion = 1,
        role = GPUDrawPacketRole.Shading,
        blendPlan = canonicalSolidRectSrcOverBlendPlan(),
        renderPipelineKey = GPURenderPipelineKey("pipeline.separable-blur.${stage.wireId}.rgba8unorm"),
        bindingLayoutHash = when (stage) {
            GPUSeparableBlurRectStage.Source -> SEPARABLE_BLUR_SOURCE_BINDING_LAYOUT_HASH
            GPUSeparableBlurRectStage.Horizontal,
            GPUSeparableBlurRectStage.Vertical,
            -> SEPARABLE_BLUR_FILTER_BINDING_LAYOUT_HASH
        },
        uniformSlot = semantic.payloadRef.uniformSlot,
        semanticPayload = semantic,
        vertexSourceLabel = SEPARABLE_BLUR_VERTEX_SOURCE_LABEL,
        scissorBoundsHash = separableBlurRectScissorAuthority(scissor),
        targetStateHash = SEPARABLE_BLUR_TARGET_STATE_HASH,
        originalPaintOrder = stage.ordinal,
        resourceGeneration = PREPARED_FRAME_LATE_BOUND_RESOURCE_GENERATION,
    )

    private fun dependency(from: GPUTaskID, to: GPUTaskID, index: Int) = GPUTaskDependency(
        from,
        to,
        "prepared-separable-blur-order",
        GPUTaskUseToken("prepared-separable-blur.$index"),
        "preserve.prepared-separable-blur.order",
    )

    private fun refused(code: String, message: String) = GPUSeparableBlurRectFrameRecordingResult.Refused(
        GPUDiagnostic(
            GPUDiagnosticCode(code),
            GPUDiagnosticDomain.Recording,
            GPUDiagnosticSeverity.Error,
            message,
        ),
    )

    private companion object {
        const val RGBA8_UNORM = "rgba8unorm"
        const val RGBA_BYTES_PER_PIXEL = 4L
        const val MIN_BLUR_SIGMA = 0.5f
        const val MAX_BLUR_SIGMA = 12f
        const val MAX_BLUR_TARGET_DIMENSION = 2048
    }
}

internal fun separableBlurRectRenderStepId(stage: GPUSeparableBlurRectStage): String =
    "filter.blur.separable-rect.${stage.wireId}"

internal fun separableBlurRectScissorAuthority(bounds: GPUPixelBounds): String =
    "scissor_${bounds.left.toFloat()}_${bounds.top.toFloat()}_${bounds.right.toFloat()}_${bounds.bottom.toFloat()}"

internal const val SEPARABLE_BLUR_SOURCE_BINDING_LAYOUT_HASH = "layout.separable-blur.source-uniform-v1"
internal const val SEPARABLE_BLUR_FILTER_BINDING_LAYOUT_HASH = "layout.separable-blur.uniform-texture-sampler-v1"
internal const val SEPARABLE_BLUR_VERTEX_SOURCE_LABEL = "fullscreen-triangle"
internal const val SEPARABLE_BLUR_TARGET_STATE_HASH = "target.rgba8unorm.single-sample"

private fun GPUPixelBounds.containsNonEmpty(other: GPUPixelBounds): Boolean =
    other.right > other.left && other.bottom > other.top &&
        other.left >= left && other.top >= top && other.right <= right && other.bottom <= bottom

private fun FloatArray.isPremultipliedRgba(): Boolean =
    size == 4 && all { it.isFinite() && it in 0f..1f } &&
        this[0] <= this[3] && this[1] <= this[3] && this[2] <= this[3]
