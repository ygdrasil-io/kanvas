package org.graphiks.kanvas.gpu.renderer.recording

import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
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
const val CORE_PRIMITIVE_VERTEX_SOURCE_LABEL = "core-primitive-device-geometry"

internal fun corePrimitiveRenderPipelineKey(): GPURenderPipelineKey =
    GPURenderPipelineKey(CORE_PRIMITIVE_RENDER_PIPELINE_KEY)

internal fun corePrimitiveTargetDescriptor(bounds: GPUPixelBounds): GPUFrameTextureDescriptor =
    GPUFrameTextureDescriptor(bounds, GPUColorFormat("rgba8unorm"), 1)

internal fun corePrimitiveTargetByteSize(bounds: GPUPixelBounds): Long =
    Math.multiplyExact(Math.multiplyExact(bounds.width.toLong(), bounds.height.toLong()), 4L)

internal fun corePrimitiveTargetPreparation(
    target: GPUFrameTargetRef,
    bounds: GPUPixelBounds,
): GPUResourcePreparationRequest = GPUResourcePreparationRequest(
    resource = target,
    descriptor = corePrimitiveTargetDescriptor(bounds),
    role = GPUFrameResourceRole.SceneTarget,
    usages = setOf(GPUFrameResourceUsage.RenderAttachment, GPUFrameResourceUsage.CopySource),
    lifetime = GPUFrameResourceLifetime.FrameLocal,
    byteSize = corePrimitiveTargetByteSize(bounds),
    diagnosticLabel = "core-primitive.scene-target",
)

internal fun isCanonicalCorePrimitiveTargetPreparation(
    request: GPUResourcePreparationRequest,
    target: GPUFrameTargetRef,
    bounds: GPUPixelBounds,
): Boolean {
    val expected = try {
        corePrimitiveTargetPreparation(target, bounds)
    } catch (_: ArithmeticException) {
        return false
    }
    return request.resource == expected.resource &&
        request.descriptor == expected.descriptor &&
        request.role == expected.role &&
        request.usages == expected.usages &&
        request.lifetime == expected.lifetime &&
        request.byteSize == expected.byteSize
}

internal fun corePrimitiveScissorAuthority(bounds: GPUPixelBounds): String =
    "scissor_${bounds.left.toFloat()}_${bounds.top.toFloat()}_${bounds.right.toFloat()}_${bounds.bottom.toFloat()}"

data class GPUCorePrimitivePreparedFrameRequest(
    val baseTaskList: GPUTaskList,
    val capabilities: GPUCapabilities,
    val target: GPUFrameTargetRef,
    val targetBounds: GPUPixelBounds,
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
        request.baseTaskList.tasks.filterIsInstance<GPUTask.Refused>().firstOrNull()?.let {
            return GPUCorePrimitivePreparedFrameResult.Refused(it.diagnostic)
        }
        request.baseTaskList.diagnostics.firstOrNull(GPUDiagnostic::isTerminal)?.let {
            return GPUCorePrimitivePreparedFrameResult.Refused(it)
        }
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
        val baseRenders = request.baseTaskList.tasks.filterIsInstance<GPUTask.Render>()
        if (baseRenders.isEmpty() || request.baseTaskList.tasks.any { it !is GPUTask.Render }) {
            return refused(
                "unsupported.recording.core_primitive_base_tasks",
                "Prepared core primitives require an accepted render-only base task list.",
            )
        }
        val basePackets = baseRenders.flatMap(GPUTask.Render::drawPackets)
        if (basePackets.any { it.clipCoveragePlan is org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoveragePlan.Mask }) {
            return refused(
                "unsupported.recording.core_primitive_clip_topology_unavailable",
                "Complex clip coverage requires B2 producer and consumer tasks before prepared recording.",
            )
        }
        if (basePackets.map(GPUDrawPacket::commandIdValue).distinct().size != basePackets.size ||
            basePackets.map(GPUDrawPacket::commandIdValue).toSet() != request.semanticsByCommandId.keys ||
            basePackets.any { it.clipCoveragePlan == null }
        ) {
            return refused(
                "invalid.recording.core_primitive_semantics",
                "Every accepted base packet requires exactly one gathered semantic payload and clip plan.",
            )
        }
        val limits = request.capabilities.limits ?: return refused(
            "unsupported.recording.core_primitive_limits_unavailable",
            "Prepared core primitive recording requires observed device limits.",
        )
        val targetBytes = try {
            corePrimitiveTargetByteSize(request.targetBounds)
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
            corePrimitiveTargetPreparation(request.target, request.targetBounds),
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
        val preparedRenders = baseRenders.mapIndexed { renderIndex, baseRender ->
            val preparedPackets = baseRender.drawPackets.map { basePacket ->
                packet(basePacket, requireNotNull(request.semanticsByCommandId[basePacket.commandIdValue]))
            }
            GPUTask.Render(
                taskId = baseRender.taskId,
                recordingId = baseRender.recordingId,
                phase = GPUTaskPhase.Render,
                target = request.target,
                loadStore = GPULoadStorePlan(
                    if (renderIndex == 0) "clear" else "load",
                    GPUStorePlan.Store,
                ),
                samplePlan = GPUSamplePlan.SingleSampleFrame,
                resourceUses = baseRender.resourceUses,
                provisionalSegmentKey = baseRender.provisionalSegmentKey,
                drawPackets = preparedPackets,
                batchEligibilityByPacketId = preparedPackets.associate { packet ->
                    packet.packetId to (
                        baseRender.batchEligibilityByPacketId[packet.packetId]
                            ?: GPUPassBatchEligibility(
                                kind = GPUPassBatchKind.SolidFill,
                                queueGuard = GPUPassBatchQueueGuard(emptyList(), emptyList()),
                            )
                        )
                },
                sampleContinuationKey = baseRender.sampleContinuationKey,
                compositeMembership = baseRender.compositeMembership,
            )
        }
        val tasks = mutableListOf<GPUTask>(
            GPUTask.PrepareResources(
                prepareId,
                preparedRenders.first().recordingId,
                GPUTaskPhase.Prepare,
                preparations,
            ),
        )
        tasks += preparedRenders
        val renderIds = preparedRenders.map(GPUTask.Render::taskId).toSet()
        val baseDependencies = request.baseTaskList.dependencies.filter {
            it.fromTaskId in renderIds && it.toTaskId in renderIds
        }
        if (baseDependencies.size != request.baseTaskList.dependencies.size) {
            return refused(
                "unsupported.recording.core_primitive_base_dependencies",
                "Prepared core primitives cannot discard non-render base dependencies.",
            )
        }
        val incomingRenderIds = baseDependencies.map(GPUTaskDependency::toTaskId).toSet()
        val dependencies = baseDependencies.toMutableList()
        preparedRenders.map(GPUTask.Render::taskId)
            .filterNot(incomingRenderIds::contains)
            .forEachIndexed { index, root -> dependencies += dependency(prepareId, root, index) }
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
            val outgoingRenderIds = baseDependencies.map(GPUTaskDependency::fromTaskId).toSet()
            preparedRenders.map(GPUTask.Render::taskId)
                .filterNot(outgoingRenderIds::contains)
                .forEachIndexed { index, sink ->
                    dependencies += dependency(sink, readbackId, dependencies.size + index)
                }
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
                diagnostics = request.baseTaskList.diagnostics,
            ),
        )
    }

    private fun packet(
        basePacket: GPUDrawPacket,
        semantic: GPUDrawSemanticPayload.CorePrimitive,
    ): GPUDrawPacket = GPUDrawPacket(
        packetId = basePacket.packetId,
        commandIdValue = basePacket.commandIdValue,
        analysisRecordId = basePacket.analysisRecordId,
        passId = basePacket.passId,
        layerId = basePacket.layerId,
        bindingListId = basePacket.bindingListId,
        insertionReasonCode = basePacket.insertionReasonCode,
        sortKey = basePacket.sortKey,
        sortKeyPreimage = basePacket.sortKeyPreimage,
        renderStepId = GPURenderStepID(CORE_PRIMITIVE_RENDER_STEP_IDENTITY),
        renderStepVersion = 1,
        role = basePacket.role,
        blendPlan = basePacket.blendPlan,
        renderPipelineKey = corePrimitiveRenderPipelineKey(),
        bindingLayoutHash = CORE_PRIMITIVE_BINDING_LAYOUT_HASH,
        uniformSlot = semantic.payloadRef.uniformSlot,
        resourceSlot = basePacket.resourceSlot,
        semanticPayload = semantic,
        vertexSourceLabel = CORE_PRIMITIVE_VERTEX_SOURCE_LABEL,
        scissorBoundsHash = corePrimitiveScissorAuthority(semantic.scissorBounds),
        targetStateHash = CORE_PRIMITIVE_TARGET_STATE_HASH,
        originalPaintOrder = basePacket.originalPaintOrder,
        resourceGeneration = PREPARED_FRAME_LATE_BOUND_RESOURCE_GENERATION,
        frameProvenance = basePacket.frameProvenance,
        clipCoveragePlan = basePacket.clipCoveragePlan,
        diagnostics = basePacket.diagnostics,
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
