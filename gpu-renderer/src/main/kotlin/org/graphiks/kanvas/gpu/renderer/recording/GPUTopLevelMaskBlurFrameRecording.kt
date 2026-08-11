package org.graphiks.kanvas.gpu.renderer.recording

import org.graphiks.kanvas.gpu.renderer.capabilities.GPULimits
import org.graphiks.kanvas.gpu.renderer.color.GPUColorFormat
import org.graphiks.kanvas.gpu.renderer.color.GPUColorInterpretation
import org.graphiks.kanvas.gpu.renderer.commands.GPUDrawCommandID
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationReadMember
import org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationSnapshotGroup
import org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationSnapshotGroupKey
import org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationSnapshotGroupingResult
import org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationSnapshotMaterialization
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnostic
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticCode
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticDomain
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticSeverity
import org.graphiks.kanvas.gpu.renderer.filters.MaskBlurPlan
import org.graphiks.kanvas.gpu.renderer.filters.MaskBlurPlanner
import org.graphiks.kanvas.gpu.renderer.filters.MaskBlurRequest
import org.graphiks.kanvas.gpu.renderer.filters.blurKernelUniform
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendDestinationReadRequirement
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketID
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketRole
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchEligibility
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchKind
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchQueueGuard
import org.graphiks.kanvas.gpu.renderer.passes.GPUProvisionalRenderSegmentKey
import org.graphiks.kanvas.gpu.renderer.passes.GPUSamplePlan
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.payloads.GPUMaskBlurLocalGeometry
import org.graphiks.kanvas.gpu.renderer.payloads.GPUMaskBlurStage
import org.graphiks.kanvas.gpu.renderer.payloads.MASK_BLUR_COMPOSITE_RENDER_STEP_IDENTITY
import org.graphiks.kanvas.gpu.renderer.payloads.maskBlurRenderStepId
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryAllocation
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryBudgetPlanner
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryBudgetRequest
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryCategory
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryResourceKind
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceLifetime
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUse
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTextureDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTextureRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourcePreparationRequest
import org.graphiks.kanvas.gpu.renderer.resources.GPUTextureCopyLayout
import org.graphiks.kanvas.gpu.renderer.state.GPULoadStorePlan
import org.graphiks.kanvas.gpu.renderer.state.GPUTargetIdentity
import org.graphiks.kanvas.gpu.renderer.state.GPUStorePlan

/**
 * Records the prepared top-level mask blur lane for one surface frame.
 *
 * Task 11 restored top-level (non-saveLayer) mask blur on core primitives: each
 * `MaskBlur`-semantic draw expands into the closed five-stage chain
 * (local shape mask → blur-h → blur-v → style → scene composite), faithful to the
 * legacy dispatcher semantics (GPUMaskBlurDispatch.kt: local-space mask, separable
 * Gaussian, style formulas, color × coverage shade). The chain reuses
 * [MaskBlurPlanner] + [blurKernelUniform] for planning and kernels, and the
 * destination-snapshot machinery for destination-reading composites (DARKEN etc.).
 *
 * All non-blur packets keep their recorded scene renders; each blur draw's recorded
 * single-packet render is replaced by its chain, inserted at the draw's paint order.
 */
internal const val TOP_LEVEL_MASK_BLUR_MAX_TEXTURE_DIMENSION = 4096

internal const val TOP_LEVEL_MASK_BLUR_TARGET_STATE_MASK = "target.mask-blur.mask.single-sample"
internal const val TOP_LEVEL_MASK_BLUR_TARGET_STATE_LOCAL = "target.mask-blur.local.single-sample"
internal const val TOP_LEVEL_MASK_BLUR_TARGET_STATE_COMPOSITE = "target.mask-blur.composite.single-sample"
internal const val TOP_LEVEL_MASK_BLUR_LAYOUT_MASK = "layout.mask-blur.mask.v1"
internal const val TOP_LEVEL_MASK_BLUR_LAYOUT_BLUR = "layout.mask-blur.blur.v1"
internal const val TOP_LEVEL_MASK_BLUR_LAYOUT_STYLE = "layout.mask-blur.style.v1"
internal const val TOP_LEVEL_MASK_BLUR_LAYOUT_COMPOSITE = "layout.mask-blur.composite.v1"
internal const val TOP_LEVEL_MASK_BLUR_LAYOUT_COMPOSITE_DST = "layout.mask-blur.composite-dst.v1"
internal const val TOP_LEVEL_MASK_BLUR_VERTEX_SOURCE_LABEL = "fullscreen-triangle"

internal const val TOP_LEVEL_MASK_BLUR_MASK_STEP = "mask-blur.mask"
internal const val TOP_LEVEL_MASK_BLUR_MASK_BLUR_H_STEP = "mask-blur.blur-h"
internal const val TOP_LEVEL_MASK_BLUR_MASK_BLUR_V_STEP = "mask-blur.blur-v"
internal const val TOP_LEVEL_MASK_BLUR_MASK_STYLE_STEP = "mask-blur.style"

internal fun topLevelMaskBlurScissorAuthority(bounds: GPUPixelBounds): String =
    "scissor_${bounds.left.toFloat()}_${bounds.top.toFloat()}_${bounds.right.toFloat()}_${bounds.bottom.toFloat()}"

internal fun topLevelMaskBlurLocalScissor(width: Int, height: Int): GPUPixelBounds =
    GPUPixelBounds(0, 0, width, height)

/** One blur draw's recorded chain: ordered renders plus the snapshot plan when dst-read. */
internal data class GPUTopLevelMaskBlurChain(
    val commandId: Int,
    val paintOrder: Int,
    val maskTarget: GPUFrameTargetRef,
    val blurHTarget: GPUFrameTargetRef,
    val blurVTarget: GPUFrameTargetRef,
    val styledTarget: GPUFrameTargetRef,
    val renderTasks: List<GPUTask.Render>,
    val internalDependencies: List<GPUTaskDependency>,
    val finalTaskId: GPUTaskID,
    val compositePacketId: GPUDrawPacketID,
    val maskPreparation: GPUResourcePreparationRequest,
    val localPreparation: GPUResourcePreparationRequest,
    val blurVPreparation: GPUResourcePreparationRequest,
    val styledPreparation: GPUResourcePreparationRequest,
    val allocations: List<GPUFrameMemoryAllocation>,
    val bytesPerTexture: Long,
)

/**
 * Builds the complete prepared frame for a surface whose draw set contains top-level
 * mask blur commands. Non-blur packets keep their recorded scene renders; the blur
 * chains are inserted at their paint positions with the destination snapshot and
 * readback envelope applied.
 */
internal fun buildTopLevelMaskBlurFrame(
    request: GPUPreparedSurfaceFrameRequest,
    configuredAggregateBudgetBytes: Long,
    maskBlurIntermediateBudgetBytes: Long,
): GPUPreparedSurfaceFrameResult {
    val limits = request.capabilities.limits ?: return refusedBlur(
        "unsupported.recording.mask_blur_limits_unavailable",
        "Prepared top-level mask blur recording requires observed device limits.",
    )
    val blurPackets = request.baseTaskList.tasks.filterIsInstance<GPUTask.Render>()
        .flatMap(GPUTask.Render::drawPackets)
        .filter { packet ->
            request.semanticsByCommandId[packet.commandIdValue] is GPUDrawSemanticPayload.MaskBlur
        }
        .sortedBy(GPUDrawPacket::originalPaintOrder)
    if (blurPackets.isEmpty()) {
        return refusedBlur(
            "invalid.recording.mask_blur_packets",
            "Top-level mask blur recording requires at least one MaskBlur packet.",
        )
    }
    val blurRenderByPacketId = request.baseTaskList.tasks.filterIsInstance<GPUTask.Render>()
        .flatMap { render -> render.drawPackets.map { packet -> packet.packetId to render } }
        .toMap()
    val blurPacketRenders = blurPackets.map { packet ->
        blurRenderByPacketId.getValue(packet.packetId)
    }
    if (blurPacketRenders.any { render -> render.drawPackets.size != 1 } ||
        blurPacketRenders.any { render ->
            render.target.value != "frame.scene" ||
                render.loadStore != GPULoadStorePlan("load", GPUStorePlan.Store)
        }
    ) {
        return refusedBlur(
            "invalid.recording.mask_blur_render_authority",
            "Every MaskBlur packet requires one exact single-packet scene render authority.",
        )
    }

    // Re-plan each blur draw with the configured intermediate budget so the legacy
    // budget gate stays reachable, then verify the immutable semantic plan facts.
    val chains = mutableListOf<GPUTopLevelMaskBlurChain>()
    val frameId = request.baseTaskList.frameId
    val recordingId = blurPacketRenders.first().recordingId
    for ((chainIndex, packet) in blurPackets.withIndex()) {
        val semantic = request.semanticsByCommandId.getValue(packet.commandIdValue)
            as GPUDrawSemanticPayload.MaskBlur
        val replanned = when (
            val plan = MaskBlurPlanner.plan(
                MaskBlurRequest(
                    bounds = semantic.deviceBounds,
                    clipBounds = semantic.deviceBounds,
                    targetWidth = request.targetBounds.width,
                    targetHeight = request.targetBounds.height,
                    style = semantic.style,
                    sigma = semantic.effectiveSigma / semantic.scale,
                    maxTextureDimension2D = TOP_LEVEL_MASK_BLUR_MAX_TEXTURE_DIMENSION,
                    maxIntermediateBytes = maskBlurIntermediateBudgetBytes,
                ),
            )
        ) {
            is MaskBlurPlan.Ready -> plan
            is MaskBlurPlan.Refused -> return refusedBlur(
                plan.code,
                "Top-level mask blur intermediate budget or sigma gate refused the draw.",
            )
            MaskBlurPlan.Identity -> return refusedBlur(
                "unsupported.recording.mask_blur_identity",
                "Top-level mask blur recording refuses zero-sigma identity plans.",
            )
        }
        if (replanned.deviceBounds.left != semantic.deviceBounds.left ||
            replanned.deviceBounds.top != semantic.deviceBounds.top ||
            replanned.deviceBounds.right != semantic.deviceBounds.right ||
            replanned.deviceBounds.bottom != semantic.deviceBounds.bottom ||
            replanned.localWidth != semantic.localWidth ||
            replanned.localHeight != semantic.localHeight ||
            replanned.scale != semantic.scale ||
            replanned.style != semantic.style ||
            replanned.effectiveSigma != semantic.effectiveSigma ||
            blurKernelUniform(replanned).tapCount != semantic.tapCount
        ) {
            return refusedBlur(
                "invalid.recording.mask_blur_plan_substitution",
                "The planned top-level mask blur facts contradict the gathered semantic.",
            )
        }
        chains += buildBlurChain(
            request = request,
            semantic = semantic,
            packet = packet,
            frameId = frameId,
            recordingId = recordingId,
            chainIndex = chainIndex,
            limits = limits,
        )
    }

    val sceneRenders = request.baseTaskList.tasks.filterIsInstance<GPUTask.Render>()
        .filter { render -> render.drawPackets.none { packet ->
            request.semanticsByCommandId[packet.commandIdValue] is GPUDrawSemanticPayload.MaskBlur
        } }
        .map { render -> retargetSceneRender(render, request.target) }
    // Ordered scene timeline: the recorded scene renders (each at its command paint
    // order) with the blur composites inserted at their own paint positions.
    val orderedRenders = buildList {
        addAll(sceneRenders)
        addAll(chains.flatMap(GPUTopLevelMaskBlurChain::renderTasks))
    }.sortedBy { render ->
        render.drawPackets.minOf { packet -> packet.originalPaintOrder }
    }
    val renderIds = orderedRenders.map(GPUTask.Render::taskId).distinct()
    if (renderIds.size != orderedRenders.size) {
        return refusedBlur(
            "invalid.recording.mask_blur_render_identity",
            "Top-level mask blur renders must retain unique task identities.",
        )
    }

    val prepareId = GPUTaskID("task.mask-blur.prepare.${frameId.value}")
    val allPreparations = mutableListOf(
        GPUResourcePreparationRequest(
            resource = request.target,
            descriptor = GPUFrameTextureDescriptor(
                request.targetBounds,
                request.targetFormat,
                1,
            ),
            role = GPUFrameResourceRole.SceneTarget,
            usages = setOf(GPUFrameResourceUsage.RenderAttachment, GPUFrameResourceUsage.CopySource),
            lifetime = GPUFrameResourceLifetime.FrameLocal,
            byteSize = corePrimitiveTargetByteSize(request.targetBounds),
            diagnosticLabel = "mask-blur.scene-target",
        ),
    )
    chains.forEach { chain ->
        allPreparations += chain.maskPreparation
        allPreparations += chain.localPreparation
        allPreparations += chain.blurVPreparation
        allPreparations += chain.styledPreparation
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
        when (val plan = GPUReadbackLayoutPlanner().plan(frameReadback, request.capabilities)) {
            is GPUReadbackLayoutPlan.Planned -> plan
            is GPUReadbackLayoutPlan.Refused ->
                return refusedBlur(plan.diagnostic.code.value, plan.diagnostic.message)
        }
    }
    val staging = readbackPlan?.let {
        GPUFrameBufferRef("buffer.mask-blur.readback.${frameId.value}")
    }
    readbackPlan?.let { plan ->
        allPreparations += GPUResourcePreparationRequest(
            staging!!,
            GPUFrameBufferDescriptor(plan.stagingDescriptor.minimumBufferBytes, 4L),
            GPUFrameResourceRole.ReadbackStaging,
            setOf(GPUFrameResourceUsage.CopyDestination, GPUFrameResourceUsage.MapRead),
            GPUFrameResourceLifetime.FrameLocal,
            plan.stagingDescriptor.minimumBufferBytes,
            "mask-blur.readback",
        )
    }

    // Destination-reading composites: one TextureCopy snapshot per dst-read blur draw,
    // consumed by that draw's composite render (copy-then-formula lane).
    val dstReadPackets = chains.mapNotNull { chain ->
        val composite = chain.renderTasks.flatMap(GPUTask.Render::drawPackets)
            .firstOrNull { it.renderStepId.value == MASK_BLUR_COMPOSITE_RENDER_STEP_IDENTITY }

        composite?.takeIf { it.blendPlan?.destinationReadRequirement ==
            GPUBlendDestinationReadRequirement.DestinationTextureRequired
        }?.let { packet -> chain.commandId to packet }
    }.toMap()
    val logicalBytesPerRow = Math.multiplyExact(request.targetBounds.width.toLong(), 4L)
    val paddedBytesPerRow = topLevelMaskBlurAlignUp(logicalBytesPerRow, limits.copyBytesPerRowAlignment)
    val copiedBytes = Math.multiplyExact(paddedBytesPerRow, request.targetBounds.height.toLong())
    val textureBytes = Math.multiplyExact(logicalBytesPerRow, request.targetBounds.height.toLong())
    val dstSnapshotsByCommandId = linkedMapOf<Int, GPUDestinationSnapshotPlan>()
    dstReadPackets.values.forEachIndexed { index, packet ->
        val snapshot = GPUFrameTextureRef(
            "texture.mask-blur.destination-snapshot.${frameId.value}.$index",
        )
        dstSnapshotsByCommandId[packet.commandIdValue] = GPUDestinationSnapshotPlan(
            groupIndex = index,
            packet = packet,
            snapshot = snapshot,
            copiedBytes = copiedBytes,
            paddedBytesPerRow = paddedBytesPerRow,
            preparation = GPUResourcePreparationRequest(
                resource = snapshot,
                descriptor = GPUFrameTextureDescriptor(
                    request.targetBounds,
                    request.targetFormat,
                    1,
                ),
                role = GPUFrameResourceRole.DestinationSnapshot,
                usages = setOf(GPUFrameResourceUsage.CopyDestination, GPUFrameResourceUsage.TextureBinding),
                lifetime = GPUFrameResourceLifetime.FrameLocal,
                byteSize = textureBytes,
                diagnosticLabel = "mask-blur.destination-snapshot.${packet.packetId.value}",
            ),
            allocation = GPUFrameMemoryAllocation(
                "mask-blur.destination-snapshot.${packet.packetId.value}",
                GPUFrameMemoryCategory.DestinationSnapshot,
                textureBytes,
                GPUFrameMemoryResourceKind.Texture2D,
                request.targetBounds,
            ),
        )
        allPreparations += dstSnapshotsByCommandId.getValue(packet.commandIdValue).preparation
    }

    val allocations = mutableListOf(
        GPUFrameMemoryAllocation(
            "mask-blur.scene-target",
            GPUFrameMemoryCategory.CanonicalTarget,
            corePrimitiveTargetByteSize(request.targetBounds),
            GPUFrameMemoryResourceKind.Texture2D,
            request.targetBounds,
        ),
    )
    chains.forEach { chain -> allocations += chain.allocations }
    dstSnapshotsByCommandId.values.forEach { plan -> allocations += plan.allocation }
    readbackPlan?.let { plan ->
        allocations += GPUFrameMemoryAllocation(
            "mask-blur.readback",
            GPUFrameMemoryCategory.ReadbackStaging,
            plan.stagingDescriptor.minimumBufferBytes,
            GPUFrameMemoryResourceKind.Buffer,
            null,
        )
    }
    val memoryBudget = GPUFrameMemoryBudgetPlanner.plan(
        GPUFrameMemoryBudgetRequest(allocations, configuredAggregateBudgetBytes, limits),
    )
    memoryBudget.diagnostic?.let { return refusedBlur(it.code.value, it.message) }

    val tasks = mutableListOf<GPUTask>()
    tasks += GPUTask.PrepareResources(prepareId, recordingId, GPUTaskPhase.Prepare, allPreparations)
    tasks += orderedRenders
    val dependencies = mutableListOf<GPUTaskDependency>()
    orderedRenders.zipWithNext().forEachIndexed { index, (from, to) ->
        dependencies += GPUTaskDependency(
            from.taskId,
            to.taskId,
            "render-order",
            GPUTaskUseToken("mask-blur.paint-order.$index"),
            "preserve.paint.order",
        )
    }
    dependencies += GPUTaskDependency(
        prepareId,
        orderedRenders.first().taskId,
        "prepare-render",
        GPUTaskUseToken("mask-blur.prepare.$frameId"),
        "preserve.prepared.order",
    )
    chains.forEach { chain ->
        dependencies += chain.internalDependencies
    }
    if (dstSnapshotsByCommandId.isNotEmpty()) {
        val destinationTask = GPUTask.DestinationSnapshots(
            taskId = GPUTaskID("task.mask-blur.destination-snapshots.${frameId.value}"),
            recordingId = recordingId,
            phase = GPUTaskPhase.Copy,
            payload = GPUDestinationSnapshotTaskPayload(
                grouping = GPUDestinationSnapshotGroupingResult(
                    groups = dstSnapshotsByCommandId.values.map { plan ->
                        val render = orderedRenders.single { render ->
                            render.drawPackets.any { it.packetId == plan.packet.packetId }
                        }
                        GPUDestinationSnapshotGroup(
                            key = GPUDestinationSnapshotGroupKey(
                                target = GPUTargetIdentity(request.target.value),
                                targetGeneration = plan.packet.resourceGeneration,
                                deviceGeneration = request.baseTaskList.capabilitySeal.deviceGeneration,
                                format = request.targetFormat,
                                colorInterpretation = topLevelMaskBlurSnapshotColorInterpretation(
                                    request.targetFormat,
                                ),
                                sampleContinuation = render.sampleContinuationKey,
                                sourceIntermediate = null,
                            ),
                            logicalBounds = request.targetBounds,
                            members = listOf(
                                GPUDestinationReadMember(
                                    commandId = plan.packet.commandIdValue.toString(),
                                    accessIndex = plan.groupIndex,
                                    logicalBounds = request.targetBounds,
                                ),
                            ),
                            copiedBytes = plan.copiedBytes,
                            decisionDump = listOf(
                                "mask-blur:destination-snapshot packet=${plan.packet.packetId.value}",
                            ),
                        )
                    },
                    materializations = dstSnapshotsByCommandId.values.map { plan ->
                        GPUDestinationSnapshotMaterialization.TextureCopy(
                            groupIndex = plan.groupIndex,
                            logicalBounds = request.targetBounds,
                        )
                    },
                    totalCopiedBytes = dstSnapshotsByCommandId.values.fold(0L) { total, plan ->
                        Math.addExact(total, plan.copiedBytes)
                    },
                    refusals = emptyList(),
                    decisionDump = listOf("mask-blur:destination-copy-then-formula"),
                ),
                operations = dstSnapshotsByCommandId.values.map { plan ->
                    val render = orderedRenders.single { render ->
                        render.drawPackets.any { it.packetId == plan.packet.packetId }
                    }
                    GPUDestinationSnapshotOperation.TextureCopy(
                        groupIndex = plan.groupIndex,
                        source = request.target,
                        snapshot = plan.snapshot,
                        logicalBounds = request.targetBounds,
                        copyLayout = GPUTextureCopyLayout(
                            bytesPerRow = plan.paddedBytesPerRow,
                            rowsPerImage = request.targetBounds.height,
                        ),
                        consumers = listOf(
                            GPUDestinationSnapshotConsumerRef(
                                groupingCommandId = plan.packet.commandIdValue.toString(),
                                renderTaskId = render.taskId,
                                packetId = plan.packet.packetId,
                                commandId = GPUDrawCommandID(plan.packet.commandIdValue),
                            ),
                        ),
                    )
                },
            ),
        )
        tasks += destinationTask
        dependencies += GPUTaskDependency(
            prepareId,
            destinationTask.taskId,
            "prepare-copy",
            GPUTaskUseToken("mask-blur.dst.prepare.$frameId"),
            "preserve.prepared.order",
        )
        dependencies += GPUTaskDependency(
            destinationTask.taskId,
            orderedRenders.first { render ->
                render.drawPackets.any { packet ->
                    packet.renderStepId.value == MASK_BLUR_COMPOSITE_RENDER_STEP_IDENTITY &&
                        dstSnapshotsByCommandId.containsKey(packet.commandIdValue)
                }
            }.taskId,
            "copy-render",
            GPUTaskUseToken("mask-blur.dst.composite.$frameId"),
            "preserve.copy-then-formula",
        )
    }
    if (readbackRequest != null && staging != null && readbackPlan != null) {
        val readbackId = GPUTaskID("task.mask-blur.readback.${frameId.value}")
        tasks += GPUTask.Readback(
            readbackId,
            recordingId,
            GPUTaskPhase.Readback,
            request.target,
            staging,
            readbackRequest,
        )
        dependencies += GPUTaskDependency(
            orderedRenders.last().taskId,
            readbackId,
            "render-readback",
            GPUTaskUseToken("mask-blur.readback.$frameId"),
            "preserve.prepared.order",
        )
    }
    return GPUPreparedSurfaceFrameResult.Recorded(
        GPUTaskList(
            frameId = request.baseTaskList.frameId,
            capabilitySeal = request.baseTaskList.capabilitySeal,
            recordingSeals = request.baseTaskList.recordingSeals,
            expectedReplayKeyHash = request.baseTaskList.expectedReplayKeyHash,
            tasks = tasks,
            dependencies = dependencies.distinct(),
            phaseOrder = request.baseTaskList.phaseOrder,
            memoryBudget = memoryBudget,
            diagnostics = request.baseTaskList.diagnostics,
        ),
    )
}

private fun buildBlurChain(
    request: GPUPreparedSurfaceFrameRequest,
    semantic: GPUDrawSemanticPayload.MaskBlur,
    packet: GPUDrawPacket,
    frameId: GPUFrameID,
    recordingId: GPURecordingID,
    chainIndex: Int,
    limits: GPULimits,
): GPUTopLevelMaskBlurChain {
    val commandId = semantic.payloadRef.commandIdValue
    val suffix = "mask-blur.$commandId.$chainIndex"
    val maskTarget = GPUFrameTargetRef("target.mask-blur.mask.$suffix")
    val blurHTarget = GPUFrameTargetRef("target.mask-blur.blur-h.$suffix")
    val blurVTarget = GPUFrameTargetRef("target.mask-blur.blur-v.$suffix")
    val styledTarget = GPUFrameTargetRef("target.mask-blur.styled.$suffix")
    val localBounds = GPUPixelBounds(0, 0, semantic.localWidth, semantic.localHeight)
    val localDescriptor = GPUFrameTextureDescriptor(localBounds, GPUColorFormat.RGBA8Unorm, 1)
    val bytesPerTexture = Math.multiplyExact(
        Math.multiplyExact(semantic.localWidth.toLong(), semantic.localHeight.toLong()),
        4L,
    )
    val maskPreparation = GPUResourcePreparationRequest(
        resource = maskTarget,
        descriptor = localDescriptor,
        role = GPUFrameResourceRole.FilterTarget,
        usages = setOf(GPUFrameResourceUsage.RenderAttachment, GPUFrameResourceUsage.TextureBinding),
        lifetime = GPUFrameResourceLifetime.FrameLocal,
        byteSize = bytesPerTexture,
        diagnosticLabel = "mask-blur.mask.$commandId",
    )
    val localPreparation = GPUResourcePreparationRequest(
        resource = blurHTarget,
        descriptor = localDescriptor,
        role = GPUFrameResourceRole.FilterTarget,
        usages = setOf(GPUFrameResourceUsage.RenderAttachment, GPUFrameResourceUsage.TextureBinding),
        lifetime = GPUFrameResourceLifetime.FrameLocal,
        byteSize = bytesPerTexture,
        diagnosticLabel = "mask-blur.blur-h.$commandId",
    )
    val blurVPreparation = GPUResourcePreparationRequest(
        resource = blurVTarget,
        descriptor = localDescriptor,
        role = GPUFrameResourceRole.FilterTarget,
        usages = setOf(GPUFrameResourceUsage.RenderAttachment, GPUFrameResourceUsage.TextureBinding),
        lifetime = GPUFrameResourceLifetime.FrameLocal,
        byteSize = bytesPerTexture,
        diagnosticLabel = "mask-blur.blur-v.$commandId",
    )
    val styledPreparation = GPUResourcePreparationRequest(
        resource = styledTarget,
        descriptor = localDescriptor,
        role = GPUFrameResourceRole.FilterTarget,
        usages = setOf(GPUFrameResourceUsage.RenderAttachment, GPUFrameResourceUsage.TextureBinding),
        lifetime = GPUFrameResourceLifetime.FrameLocal,
        byteSize = bytesPerTexture,
        diagnosticLabel = "mask-blur.styled.$commandId",
    )
    val allocations = buildList {
        add(
            GPUFrameMemoryAllocation(
                "mask-blur.mask.$commandId",
                GPUFrameMemoryCategory.ReusableScratch,
                bytesPerTexture,
                GPUFrameMemoryResourceKind.Texture2D,
                localBounds,
            ),
        )
        add(
            GPUFrameMemoryAllocation(
                "mask-blur.blur-h.$commandId",
                GPUFrameMemoryCategory.ReusableScratch,
                bytesPerTexture,
                GPUFrameMemoryResourceKind.Texture2D,
                localBounds,
            ),
        )
        add(
            GPUFrameMemoryAllocation(
                "mask-blur.blur-v.$commandId",
                GPUFrameMemoryCategory.ReusableScratch,
                bytesPerTexture,
                GPUFrameMemoryResourceKind.Texture2D,
                localBounds,
            ),
        )
        add(
            GPUFrameMemoryAllocation(
                "mask-blur.styled.$commandId",
                GPUFrameMemoryCategory.ReusableScratch,
                bytesPerTexture,
                GPUFrameMemoryResourceKind.Texture2D,
                localBounds,
            ),
        )
    }

    val maskPacket = stagePacket(
        stage = GPUMaskBlurStage.Mask,
        stepId = TOP_LEVEL_MASK_BLUR_MASK_STEP,
        targetStateHash = TOP_LEVEL_MASK_BLUR_TARGET_STATE_MASK,
        layoutHash = TOP_LEVEL_MASK_BLUR_LAYOUT_MASK,
        base = packet,
        semantic = semantic,
        scissor = localBounds,
        blendPlan = topLevelMaskBlurReplaceBlendPlan("mask-blur-mask"),
        isComposite = false,
    )
    val blurH = stagePacket(
        stage = GPUMaskBlurStage.BlurH,
        stepId = TOP_LEVEL_MASK_BLUR_MASK_BLUR_H_STEP,
        targetStateHash = TOP_LEVEL_MASK_BLUR_TARGET_STATE_LOCAL,
        layoutHash = TOP_LEVEL_MASK_BLUR_LAYOUT_BLUR,
        base = packet,
        semantic = semantic,
        scissor = localBounds,
        blendPlan = topLevelMaskBlurReplaceBlendPlan("mask-blur-blur-h"),
        isComposite = false,
    )
    val blurV = stagePacket(
        stage = GPUMaskBlurStage.BlurV,
        stepId = TOP_LEVEL_MASK_BLUR_MASK_BLUR_V_STEP,
        targetStateHash = TOP_LEVEL_MASK_BLUR_TARGET_STATE_LOCAL,
        layoutHash = TOP_LEVEL_MASK_BLUR_LAYOUT_BLUR,
        base = packet,
        semantic = semantic,
        scissor = localBounds,
        blendPlan = topLevelMaskBlurReplaceBlendPlan("mask-blur-blur-v"),
        isComposite = false,
    )
    val style = stagePacket(
        stage = GPUMaskBlurStage.Style,
        stepId = TOP_LEVEL_MASK_BLUR_MASK_STYLE_STEP,
        targetStateHash = TOP_LEVEL_MASK_BLUR_TARGET_STATE_LOCAL,
        layoutHash = TOP_LEVEL_MASK_BLUR_LAYOUT_STYLE,
        base = packet,
        semantic = semantic,
        scissor = localBounds,
        blendPlan = topLevelMaskBlurReplaceBlendPlan("mask-blur-style"),
        isComposite = false,
    )
    val composite = stagePacket(
        stage = GPUMaskBlurStage.Composite,
        stepId = MASK_BLUR_COMPOSITE_RENDER_STEP_IDENTITY,
        targetStateHash = TOP_LEVEL_MASK_BLUR_TARGET_STATE_COMPOSITE,
        layoutHash = if (
            packet.blendPlan?.destinationReadRequirement ==
            GPUBlendDestinationReadRequirement.DestinationTextureRequired
        ) {
            TOP_LEVEL_MASK_BLUR_LAYOUT_COMPOSITE_DST
        } else {
            TOP_LEVEL_MASK_BLUR_LAYOUT_COMPOSITE
        },
        base = packet,
        semantic = semantic,
        scissor = semantic.scissorBounds,
        blendPlan = requireNotNull(packet.blendPlan),
        isComposite = true,
    )

    val maskRenderTask = maskRender(
        taskId = GPUTaskID("task.mask-blur.mask.$suffix"),
        recordingId = recordingId,
        target = maskTarget,
        loadStore = GPULoadStorePlan("clear", GPUStorePlan.Store),
        packets = listOf(maskPacket),
        resourceUses = listOf(maskWriteUse(maskTarget)),
        provisionalKey = "mask-blur.mask.$suffix",
        suffix = suffix,
    )
    val maskFinal = maskRenderTask
    val blurHRender = maskRender(
        taskId = GPUTaskID("task.mask-blur.blur-h.$suffix"),
        recordingId = recordingId,
        target = blurHTarget,
        loadStore = GPULoadStorePlan("clear", GPUStorePlan.Store),
        packets = listOf(blurH),
        resourceUses = listOf(
            maskWriteUse(blurHTarget),
            GPUFrameResourceUse(
                maskTarget,
                GPUFrameResourceRole.FilterTarget,
                GPUFrameResourceUsage.TextureBinding,
                GPUFrameResourceLifetime.FrameLocal,
                write = false,
            ),
        ),
        provisionalKey = "mask-blur.blur-h.$suffix",
        suffix = suffix,
    )
    val blurVRender = maskRender(
        taskId = GPUTaskID("task.mask-blur.blur-v.$suffix"),
        recordingId = recordingId,
        target = blurVTarget,
        loadStore = GPULoadStorePlan("clear", GPUStorePlan.Store),
        packets = listOf(blurV),
        resourceUses = listOf(
            maskWriteUse(blurVTarget),
            GPUFrameResourceUse(
                blurHTarget,
                GPUFrameResourceRole.FilterTarget,
                GPUFrameResourceUsage.TextureBinding,
                GPUFrameResourceLifetime.FrameLocal,
                write = false,
            ),
        ),
        provisionalKey = "mask-blur.blur-v.$suffix",
        suffix = suffix,
    )
    val styleRender = maskRender(
        taskId = GPUTaskID("task.mask-blur.style.$suffix"),
        recordingId = recordingId,
        target = styledTarget,
        loadStore = GPULoadStorePlan("clear", GPUStorePlan.Store),
        packets = listOf(style),
        resourceUses = listOf(
            maskWriteUse(styledTarget),
            GPUFrameResourceUse(
                blurVTarget,
                GPUFrameResourceRole.FilterTarget,
                GPUFrameResourceUsage.TextureBinding,
                GPUFrameResourceLifetime.FrameLocal,
                write = false,
            ),
            GPUFrameResourceUse(
                maskTarget,
                GPUFrameResourceRole.FilterTarget,
                GPUFrameResourceUsage.TextureBinding,
                GPUFrameResourceLifetime.FrameLocal,
                write = false,
            ),
        ),
        provisionalKey = "mask-blur.style.$suffix",
        suffix = suffix,
    )
    val compositeRender = maskRender(
        taskId = GPUTaskID("task.mask-blur.composite.$suffix"),
        recordingId = recordingId,
        target = request.target,
        loadStore = GPULoadStorePlan("load", GPUStorePlan.Store),
        packets = listOf(composite),
        resourceUses = listOf(
            GPUFrameResourceUse(
                styledTarget,
                GPUFrameResourceRole.FilterTarget,
                GPUFrameResourceUsage.TextureBinding,
                GPUFrameResourceLifetime.FrameLocal,
                write = false,
            ),
        ),
        provisionalKey = "mask-blur.composite.$suffix",
        suffix = suffix,
    )
    val chainRenders = listOf(maskFinal, blurHRender, blurVRender, styleRender, compositeRender)
    val internalDependencies = mutableListOf<GPUTaskDependency>()
    listOf(
        maskFinal.taskId to blurHRender.taskId,
        blurHRender.taskId to blurVRender.taskId,
        blurVRender.taskId to styleRender.taskId,
        styleRender.taskId to compositeRender.taskId,
    ).forEachIndexed { index, (from, to) ->
        internalDependencies += chainDependency(from, to, "stage.$index", frameId)
    }
    return GPUTopLevelMaskBlurChain(
        commandId = commandId,
        paintOrder = packet.originalPaintOrder,
        maskTarget = maskTarget,
        blurHTarget = blurHTarget,
        blurVTarget = blurVTarget,
        styledTarget = styledTarget,
        renderTasks = chainRenders,
        internalDependencies = internalDependencies,
        finalTaskId = compositeRender.taskId,
        compositePacketId = composite.packetId,
        maskPreparation = maskPreparation,
        localPreparation = localPreparation,
        blurVPreparation = blurVPreparation,
        styledPreparation = styledPreparation,
        allocations = allocations,
        bytesPerTexture = bytesPerTexture,
    )
}

private fun stagePacket(
    stage: GPUMaskBlurStage,
    stepId: String,
    targetStateHash: String,
    layoutHash: String,
    base: GPUDrawPacket,
    semantic: GPUDrawSemanticPayload.MaskBlur,
    scissor: GPUPixelBounds,
    blendPlan: org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlan,
    isComposite: Boolean,
): GPUDrawPacket = GPUDrawPacket(
    packetId = GPUDrawPacketID("packet.mask-blur.${stage.wireId}.${semantic.payloadRef.commandIdValue}"),
    commandIdValue = semantic.payloadRef.commandIdValue,
    analysisRecordId = base.analysisRecordId,
    passId = "pass.mask-blur.${stage.wireId}.${semantic.payloadRef.commandIdValue}",
    layerId = "root",
    bindingListId = "bindings.mask-blur.${stage.wireId}.${semantic.payloadRef.commandIdValue}",
    insertionReasonCode = "mask-blur-${stage.wireId}",
    sortKey = if (isComposite) base.sortKey else stage.ordinal.toLong(),
    sortKeyPreimage = "mask-blur-stage:${stage.wireId}:${semantic.payloadRef.commandIdValue}",
    renderStepId = org.graphiks.kanvas.gpu.renderer.passes.GPURenderStepID(stepId),
    renderStepVersion = 1,
    role = GPUDrawPacketRole.Shading,
    blendPlan = blendPlan,
    renderPipelineKey = org.graphiks.kanvas.gpu.renderer.pipelines.GPURenderPipelineKey(
        "pipeline.mask-blur.${stage.wireId}.rgba8unorm",
    ),
    bindingLayoutHash = layoutHash,
    uniformSlot = semantic.payloadRef.uniformSlot,
    semanticPayload = semantic,
    vertexSourceLabel = TOP_LEVEL_MASK_BLUR_VERTEX_SOURCE_LABEL,
    scissorBoundsHash = topLevelMaskBlurScissorAuthority(scissor),
    targetStateHash = targetStateHash,
    originalPaintOrder = base.originalPaintOrder,
    resourceGeneration = PREPARED_FRAME_LATE_BOUND_RESOURCE_GENERATION,
    frameProvenance = base.frameProvenance,
    clipCoveragePlan = semantic.clipCoveragePlan,
    clipExecutionPlan = if (isComposite) base.clipExecutionPlan else null,
)

private fun maskRender(
    taskId: GPUTaskID,
    recordingId: GPURecordingID,
    target: GPUFrameTargetRef,
    loadStore: GPULoadStorePlan,
    packets: List<GPUDrawPacket>,
    resourceUses: List<GPUFrameResourceUse>,
    provisionalKey: String,
    suffix: String,
): GPUTask.Render = GPUTask.Render(
    taskId = taskId,
    recordingId = recordingId,
    phase = GPUTaskPhase.Render,
    target = target,
    loadStore = loadStore,
    samplePlan = GPUSamplePlan.SingleSampleFrame,
    resourceUses = resourceUses,
    provisionalSegmentKey = GPUProvisionalRenderSegmentKey(provisionalKey),
    drawPackets = packets,
    batchEligibilityByPacketId = packets.associate { packet ->
        packet.packetId to GPUPassBatchEligibility(
            kind = GPUPassBatchKind.SolidFill,
            queueGuard = GPUPassBatchQueueGuard(emptyList(), emptyList()),
        )
    },
)

/** Retargets one recorded scene render to the prepared session target (core-lane convention). */
private fun retargetSceneRender(render: GPUTask.Render, target: GPUFrameTargetRef): GPUTask.Render =
    GPUTask.Render(
        taskId = render.taskId,
        recordingId = render.recordingId,
        phase = render.phase,
        target = target,
        loadStore = render.loadStore,
        samplePlan = render.samplePlan,
        resourceUses = render.resourceUses,
        provisionalSegmentKey = render.provisionalSegmentKey,
        drawPackets = render.drawPackets,
        batchEligibilityByPacketId = render.batchEligibilityByPacketId,
        sampleContinuationKey = render.sampleContinuationKey,
        compositeMembership = render.compositeMembership,
        depthStencilLoadStore = render.depthStencilLoadStore,
        preparedImageBindingsByPacketId = render.preparedImageBindingsByPacketId,
        preparedTextBindingsByPacketId = render.preparedTextBindingsByPacketId,
    )

private fun maskWriteUse(target: GPUFrameTargetRef): GPUFrameResourceUse = GPUFrameResourceUse(
    target,
    GPUFrameResourceRole.FilterTarget,
    GPUFrameResourceUsage.RenderAttachment,
    GPUFrameResourceLifetime.FrameLocal,
    write = true,
)


private fun chainDependency(from: GPUTaskID, to: GPUTaskID, token: String, frameId: GPUFrameID) =
    GPUTaskDependency(
        from,
        to,
        "mask-blur-chain",
        GPUTaskUseToken("mask-blur.chain.$token.$frameId"),
        "preserve.mask-blur.stage-order",
    )

private fun topLevelMaskBlurReplaceBlendPlan(stateId: String): org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlan.FixedFunctionBlend =
    org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlan.FixedFunctionBlend(
        mode = org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode.SRC,
        state = org.graphiks.kanvas.gpu.renderer.state.GPUFixedFunctionBlendState(
            stateId = stateId,
            color = org.graphiks.kanvas.gpu.renderer.state.GPUFixedFunctionBlendComponent("one", "zero", "add"),
            alpha = org.graphiks.kanvas.gpu.renderer.state.GPUFixedFunctionBlendComponent("one", "zero", "add"),
            writeMask = "rgba",
        ),
        sourceCoverageEncoding = org.graphiks.kanvas.gpu.renderer.passes.GPUSourceCoverageEncoding.None,
    )

private fun topLevelMaskBlurAlignUp(value: Long, alignment: Long): Long {
    val remainder = value % alignment
    return if (remainder == 0L) value else Math.addExact(value, alignment - remainder)
}

private fun topLevelMaskBlurSnapshotColorInterpretation(
    format: GPUColorFormat,
): GPUColorInterpretation = when (format) {
    GPUColorFormat.RGBA8Unorm -> GPUColorInterpretation.EncodedPremulSrgb
    GPUColorFormat.RGBA8UnormSrgb -> GPUColorInterpretation.LinearPremul
    GPUColorFormat.BGRA8Unorm -> GPUColorInterpretation.EncodedPremulSrgb
    else -> throw IllegalArgumentException(
        "Prepared mask blur destination snapshots require RGBA8Unorm, RGBA8UnormSrgb, or BGRA8Unorm.",
    )
}

private data class GPUDestinationSnapshotPlan(
    val groupIndex: Int,
    val packet: GPUDrawPacket,
    val snapshot: GPUFrameTextureRef,
    val copiedBytes: Long,
    val paddedBytesPerRow: Long,
    val preparation: GPUResourcePreparationRequest,
    val allocation: GPUFrameMemoryAllocation,
)

private fun refusedBlur(code: String, message: String): GPUPreparedSurfaceFrameResult =
    GPUPreparedSurfaceFrameResult.Refused(
        GPUDiagnostic(
            GPUDiagnosticCode(code),
            GPUDiagnosticDomain.Recording,
            GPUDiagnosticSeverity.Error,
            message,
        ),
    )

/** Static destination-read scene composite WGSL (formula blend of color x coverage with the dst snapshot). */
internal val MASK_BLUR_COMPOSITE_DST_WGSL: String = """
struct CompositeDstUniforms {
    deviceBounds: vec4f,
    color: vec4f,
    blendMode: u32,
    _pad0: u32,
};

@group(0) @binding(0) var<uniform> uniforms: CompositeDstUniforms;
@group(0) @binding(1) var maskTexture: texture_2d<f32>;
@group(0) @binding(2) var maskSampler: sampler;
@group(0) @binding(3) var dstTexture: texture_2d<f32>;
@group(0) @binding(4) var dstSampler: sampler;

@vertex
fn vs_main(@builtin(vertex_index) vertex_index: u32) -> @builtin(position) vec4f {
    var positions = array<vec2f, 3>(
        vec2f(-1.0, -1.0),
        vec2f(3.0, -1.0),
        vec2f(-1.0, 3.0),
    );
    return vec4f(positions[vertex_index], 0.0, 1.0);
}

${org.graphiks.kanvas.gpu.renderer.materials.GPUBlendFormulaLibrary.advancedBlendDispatcherWgsl()}

fn kanvasSrgbToLinear(c: f32) -> f32 {
    if (c <= 0.04045) {
        return c / 12.92;
    }
    return pow((c + 0.055) / 1.055, 2.4);
}

@fragment
fn fs_main(@builtin(position) position: vec4f) -> @location(0) vec4f {
    let localSize = max(uniforms.deviceBounds.zw - uniforms.deviceBounds.xy, vec2f(1.0));
    let uv = (position.xy - uniforms.deviceBounds.xy) / localSize;
    let coverage = textureSample(maskTexture, maskSampler, uv).a;
    let src = uniforms.color * coverage;
    let dstDims = textureDimensions(dstTexture);
    let dstUv = position.xy / vec2f(f32(dstDims.x), f32(dstDims.y));
    let dstEncoded = textureSample(dstTexture, dstSampler, dstUv);
    // wgpu4k does NOT auto-decode sRGB samples on this backend (verified empirically),
    // so the dst snapshot must be decoded manually per the Graphite kTextureCopy model.
    // A spec-compliant backend WOULD decode sRGB samples automatically and would
    // double-decode here — flagged for a wgpu4k ticket per the project's
    // wgpu4k-evolution policy before any "complete" claim.
    let dst = vec4f(kanvasSrgbToLinear(dstEncoded.r), kanvasSrgbToLinear(dstEncoded.g), kanvasSrgbToLinear(dstEncoded.b), dstEncoded.a);
    let blended = blendPremul(src, dst, uniforms.blendMode);
    return blended;
}
""".trimIndent()

/** Refuses blur composites whose clip execution is beyond the lane scope (NoClip or ScissorOnly only). */
internal fun topLevelMaskBlurCompositeClipRefusal(
    packet: org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket,
): String? {
    val plan = packet.clipExecutionPlan ?: return null
    if (plan is org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionPlan.NoClip ||
        plan is org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionPlan.ScissorOnly
    ) {
        return null
    }
    return "unsupported.native-mask-blur.clip"
}
