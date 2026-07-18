package org.graphiks.kanvas.gpu.renderer.recording

import java.security.MessageDigest
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.color.GPUColorFormat
import org.graphiks.kanvas.gpu.renderer.color.GPUColorInterpretation
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionPlan
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnostic
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticCode
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticDomain
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticSeverity
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketID
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketRole
import org.graphiks.kanvas.gpu.renderer.passes.GPUClipProducerAuthority
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchEligibility
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchKind
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchQueueGuard
import org.graphiks.kanvas.gpu.renderer.passes.GPUProvisionalRenderSegmentKey
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
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUse
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTextureDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTextureRef
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

private fun GPUClipExecutionPlan.contentKeyOrNull(): String? = when (this) {
    is GPUClipExecutionPlan.StencilCoverage -> contentKey
    is GPUClipExecutionPlan.CoverageMask -> contentKey
    GPUClipExecutionPlan.NoClip,
    is GPUClipExecutionPlan.ScissorOnly,
    is GPUClipExecutionPlan.AnalyticCoverage,
    is GPUClipExecutionPlan.Refused,
    -> null
}

private fun GPUClipExecutionPlan.clipResourceKey(): String =
    MessageDigest.getInstance("SHA-256")
        .digest(canonicalIdentity().toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

private data class GPUCoreClipArtifactTopology(
    val contentKey: String,
    val preparations: List<GPUResourcePreparationRequest>,
    val allocations: List<GPUFrameMemoryAllocation>,
    val producerTasks: List<GPUTask.Render>,
    val producerDependencies: List<GPUTaskDependency>,
    val finalProducerId: GPUTaskID,
    val consumerResourceUse: GPUFrameResourceUse,
    val orderingToken: String,
)

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
        if (basePackets.map(GPUDrawPacket::commandIdValue).distinct().size != basePackets.size ||
            basePackets.map(GPUDrawPacket::commandIdValue).toSet() != request.semanticsByCommandId.keys ||
            basePackets.any { it.clipCoveragePlan == null }
        ) {
            return refused(
                "invalid.recording.core_primitive_semantics",
                "Every accepted base packet requires exactly one gathered semantic payload and clip plan.",
            )
        }
        if (basePackets.any { it.clipExecutionPlan == null }) {
            return refused(
                "invalid.recording.core_primitive_clip_execution_plan_missing",
                "Every core primitive packet requires one classified clip execution plan.",
            )
        }
        basePackets.mapNotNull(GPUDrawPacket::clipExecutionPlan)
            .filterIsInstance<GPUClipExecutionPlan.Refused>()
            .firstOrNull()
            ?.let { return refused(it.code, it.message) }
        val clipArtifacts = linkedMapOf<String, GPUClipExecutionPlan>()
        basePackets.forEach { packet ->
            val plan = requireNotNull(packet.clipExecutionPlan)
            val contentKey = plan.contentKeyOrNull() ?: return@forEach
            val previous = clipArtifacts[contentKey]
            if (previous != null && previous.canonicalIdentity() != plan.canonicalIdentity()) {
                return refused(
                    "invalid.recording.core_primitive_clip_content_key_collision",
                    "One clip content key identifies different full execution plans.",
                )
            }
            clipArtifacts.putIfAbsent(contentKey, plan)
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
        val unsupportedMultisampleClip = clipArtifacts.values.firstOrNull { plan ->
            when (plan) {
                is GPUClipExecutionPlan.StencilCoverage -> plan.sampleCount != 1
                is GPUClipExecutionPlan.CoverageMask -> plan.sampleCount != 1
                else -> false
            }
        }
        if (unsupportedMultisampleClip != null) {
            return refused(
                "unsupported.recording.core_primitive_clip_multisample_topology",
                "Core primitive clip producer topology currently requires single-sample plans.",
            )
        }
        val clipTopologies = clipArtifacts.map { (contentKey, plan) ->
            clipTopology(
                contentKey = contentKey,
                plan = plan,
                target = request.target,
                representative = basePackets.first { packet ->
                    packet.clipExecutionPlan?.contentKeyOrNull() == contentKey
                },
                recordingId = baseRenders.first { render ->
                    render.drawPackets.any { packet -> packet.clipExecutionPlan?.contentKeyOrNull() == contentKey }
                }.recordingId,
            )
        }
        val preparations = mutableListOf(
            corePrimitiveTargetPreparation(request.target, request.targetBounds),
        )
        preparations += clipTopologies.flatMap(GPUCoreClipArtifactTopology::preparations)
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
        allocations += clipTopologies.flatMap(GPUCoreClipArtifactTopology::allocations)
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
        val topologiesByContentKey = clipTopologies.associateBy(GPUCoreClipArtifactTopology::contentKey)
        val consumersByBaseTask = linkedMapOf<GPUTaskID, List<GPUTask.Render>>()
        var consumerOrdinal = 0
        baseRenders.forEach { baseRender ->
            consumersByBaseTask[baseRender.taskId] = baseRender.drawPackets.mapIndexed { packetIndex, basePacket ->
                val preparedPacket = packet(
                    basePacket,
                    requireNotNull(request.semanticsByCommandId[basePacket.commandIdValue]),
                )
                val topology = basePacket.clipExecutionPlan?.contentKeyOrNull()?.let(topologiesByContentKey::get)
                val resourceUses = baseRender.resourceUses + listOfNotNull(topology?.consumerResourceUse)
                GPUTask.Render(
                    taskId = GPUTaskID("${baseRender.taskId.value}.core-consumer.$packetIndex"),
                    recordingId = baseRender.recordingId,
                    phase = GPUTaskPhase.Render,
                    target = request.target,
                    loadStore = GPULoadStorePlan(
                        if (consumerOrdinal++ == 0) "clear" else "load",
                        GPUStorePlan.Store,
                    ),
                    samplePlan = GPUSamplePlan.SingleSampleFrame,
                    resourceUses = resourceUses,
                    provisionalSegmentKey = baseRender.provisionalSegmentKey,
                    drawPackets = listOf(preparedPacket),
                    batchEligibilityByPacketId = mapOf(
                        preparedPacket.packetId to (
                            baseRender.batchEligibilityByPacketId[basePacket.packetId]
                                ?: producerBatchEligibility()
                            ),
                    ),
                    sampleContinuationKey = null,
                    compositeMembership = baseRender.compositeMembership,
                )
            }
        }
        val preparedRenders = consumersByBaseTask.values.flatten()
        val tasks = mutableListOf<GPUTask>(
            GPUTask.PrepareResources(
                prepareId,
                preparedRenders.first().recordingId,
                GPUTaskPhase.Prepare,
                preparations,
            ),
        )
        tasks += clipTopologies.flatMap(GPUCoreClipArtifactTopology::producerTasks)
        tasks += preparedRenders
        val baseRenderIds = baseRenders.map(GPUTask.Render::taskId).toSet()
        val baseDependencies = request.baseTaskList.dependencies.filter { dependency ->
            dependency.fromTaskId in baseRenderIds && dependency.toTaskId in baseRenderIds
        }
        if (baseDependencies.size != request.baseTaskList.dependencies.size) {
            return refused(
                "unsupported.recording.core_primitive_base_dependencies",
                "Prepared core primitives cannot discard non-render base dependencies.",
            )
        }
        val dependencies = clipTopologies
            .flatMap(GPUCoreClipArtifactTopology::producerDependencies)
            .toMutableList()
        baseDependencies.forEach { dependency ->
            dependencies += dependency.copy(
                fromTaskId = consumersByBaseTask.getValue(dependency.fromTaskId).last().taskId,
                toTaskId = consumersByBaseTask.getValue(dependency.toTaskId).first().taskId,
            )
        }
        fun addPreparedOrderIfMissing(from: GPUTaskID, to: GPUTaskID, index: Int) {
            if (dependencies.none { it.fromTaskId == from && it.toTaskId == to }) {
                dependencies += dependency(from, to, index)
            }
        }
        consumersByBaseTask.values.forEach { consumers ->
            consumers.zipWithNext().forEachIndexed { index, (from, to) ->
                addPreparedOrderIfMissing(from.taskId, to.taskId, dependencies.size + index)
            }
        }
        val producedContentKeys = mutableSetOf<String>()
        var previousConsumer: GPUTask.Render? = null
        preparedRenders.forEachIndexed { index, consumer ->
            val previous = previousConsumer
            val plan = consumer.drawPackets.single().clipExecutionPlan
            val contentKey = plan?.contentKeyOrNull()
            val topology = contentKey?.let(topologiesByContentKey::get)
            val firstArtifactUse = topology != null && producedContentKeys.add(topology.contentKey)
            if (topology != null && firstArtifactUse) {
                val firstProducer = topology.producerTasks.first().taskId
                dependencies += if (previous == null) {
                    dependency(prepareId, firstProducer, dependencies.size + index)
                } else {
                    clipDependency(
                        previous.taskId,
                        firstProducer,
                        topology.orderingToken,
                        "paint-before-producer",
                    )
                }
            }
            when {
                topology != null -> {
                    dependencies += clipDependency(
                        topology.finalProducerId,
                        consumer.taskId,
                        topology.orderingToken,
                        "producer-before-consumer",
                    )
                    if (previous != null && !firstArtifactUse) {
                        addPreparedOrderIfMissing(previous.taskId, consumer.taskId, dependencies.size + index)
                    }
                }
                previous != null ->
                    addPreparedOrderIfMissing(previous.taskId, consumer.taskId, dependencies.size + index)
                else -> dependencies += dependency(prepareId, consumer.taskId, dependencies.size + index)
            }
            previousConsumer = consumer
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
                dependencies = dependencies.distinct(),
                phaseOrder = request.baseTaskList.phaseOrder,
                memoryBudget = memoryBudget,
                diagnostics = request.baseTaskList.diagnostics,
            ),
        )
    }

    private fun clipTopology(
        contentKey: String,
        plan: GPUClipExecutionPlan,
        target: GPUFrameTargetRef,
        representative: GPUDrawPacket,
        recordingId: GPURecordingID,
    ): GPUCoreClipArtifactTopology {
        val key = plan.clipResourceKey()
        return when (plan) {
            is GPUClipExecutionPlan.StencilCoverage -> {
                val resource = GPUFrameTextureRef("texture.core-primitive.clip-depth-stencil.$key")
                val producerId = GPUTaskID("task.core-primitive.clip-stencil.$key")
                val packet = clipProducerPacket(
                    base = representative,
                    plan = plan,
                    taskId = producerId,
                    role = GPUDrawPacketRole.StencilProducer,
                    renderStep = "clip.stencil.producer",
                    variant = "stencil",
                    authority = GPUClipProducerAuthority.Stencil(plan.producer),
                )
                val use = GPUFrameResourceUse(
                    resource,
                    GPUFrameResourceRole.ClipDepthStencil,
                    GPUFrameResourceUsage.RenderAttachment,
                    GPUFrameResourceLifetime.FrameLocal,
                    true,
                )
                GPUCoreClipArtifactTopology(
                    contentKey,
                    listOf(
                        GPUResourcePreparationRequest(
                            resource,
                            GPUFrameTextureDescriptor(
                                plan.bounds,
                                GPUColorFormat("depth24plus-stencil8"),
                                plan.sampleCount,
                            ),
                            GPUFrameResourceRole.ClipDepthStencil,
                            setOf(GPUFrameResourceUsage.RenderAttachment),
                            GPUFrameResourceLifetime.FrameLocal,
                            plan.depthStencilBytes,
                            "core-primitive.clip-depth-stencil.$key",
                        ),
                    ),
                    listOf(
                        GPUFrameMemoryAllocation(
                            "core-primitive.clip-depth-stencil.$key",
                            GPUFrameMemoryCategory.FrameLocalMsaaDepthStencil,
                            plan.depthStencilBytes,
                            GPUFrameMemoryResourceKind.Texture2D,
                            plan.bounds,
                        ),
                    ),
                    listOf(
                        GPUTask.Render(
                            producerId,
                            recordingId,
                            GPUTaskPhase.Render,
                            target,
                            GPULoadStorePlan("load", GPUStorePlan.Store),
                            GPUSamplePlan.SingleSampleFrame,
                            listOf(use),
                            GPUProvisionalRenderSegmentKey("clip.stencil.$key"),
                            listOf(packet),
                            mapOf(packet.packetId to producerBatchEligibility()),
                        ),
                    ),
                    emptyList(),
                    producerId,
                    use.copy(write = false),
                    plan.orderingToken.value,
                )
            }
            is GPUClipExecutionPlan.CoverageMask -> {
                val mask = GPUFrameTargetRef("target.core-primitive.clip-mask.$key")
                val maskUse = GPUFrameResourceUse(
                    mask,
                    GPUFrameResourceRole.ClipMask,
                    GPUFrameResourceUsage.RenderAttachment,
                    GPUFrameResourceLifetime.FrameLocal,
                    true,
                )
                val preparations = mutableListOf(
                    GPUResourcePreparationRequest(
                        mask,
                        GPUFrameTextureDescriptor(plan.bounds, GPUColorFormat("rgba8unorm"), 1),
                        GPUFrameResourceRole.ClipMask,
                        setOf(GPUFrameResourceUsage.RenderAttachment, GPUFrameResourceUsage.TextureBinding),
                        GPUFrameResourceLifetime.FrameLocal,
                        plan.resolvedBytes,
                        "core-primitive.clip-mask.$key",
                    ),
                )
                val allocations = mutableListOf(
                    GPUFrameMemoryAllocation(
                        "core-primitive.clip-mask.$key",
                        GPUFrameMemoryCategory.ReusableScratch,
                        plan.resolvedBytes,
                        GPUFrameMemoryResourceKind.Texture2D,
                        plan.bounds,
                    ),
                )
                val producerUses = mutableListOf(maskUse)
                if (plan.depthStencilRequired) {
                    val depthStencil = GPUFrameTextureRef("texture.core-primitive.clip-mask-depth-stencil.$key")
                    val depthUse = GPUFrameResourceUse(
                        depthStencil,
                        GPUFrameResourceRole.ClipDepthStencil,
                        GPUFrameResourceUsage.RenderAttachment,
                        GPUFrameResourceLifetime.FrameLocal,
                        true,
                    )
                    producerUses += depthUse
                    preparations += GPUResourcePreparationRequest(
                        depthStencil,
                        GPUFrameTextureDescriptor(
                            plan.bounds,
                            GPUColorFormat("depth24plus-stencil8"),
                            plan.sampleCount,
                        ),
                        GPUFrameResourceRole.ClipDepthStencil,
                        setOf(GPUFrameResourceUsage.RenderAttachment),
                        GPUFrameResourceLifetime.FrameLocal,
                        plan.depthStencilBytes,
                        "core-primitive.clip-mask-depth-stencil.$key",
                    )
                    allocations += GPUFrameMemoryAllocation(
                        "core-primitive.clip-mask-depth-stencil.$key",
                        GPUFrameMemoryCategory.FrameLocalMsaaDepthStencil,
                        plan.depthStencilBytes,
                        GPUFrameMemoryResourceKind.Texture2D,
                        plan.bounds,
                    )
                }
                val producerTasks = plan.producers.mapIndexed { index, producer ->
                    val producerId = GPUTaskID("task.core-primitive.clip-mask.$key.${producer.sourceOrder}")
                    val packet = clipProducerPacket(
                        base = representative,
                        plan = plan,
                        taskId = producerId,
                        role = GPUDrawPacketRole.ClipProducer,
                        renderStep = "clip.mask.producer",
                        variant = "${producer.combine.name}.${producer.sourceOrder}",
                        authority = GPUClipProducerAuthority.Mask(producer),
                    )
                    GPUTask.Render(
                        producerId,
                        recordingId,
                        GPUTaskPhase.Render,
                        mask,
                        GPULoadStorePlan(if (index == 0) "clear" else "load", GPUStorePlan.Store),
                        GPUSamplePlan.SingleSampleFrame,
                        producerUses,
                        GPUProvisionalRenderSegmentKey("clip.mask.$key.${producer.sourceOrder}"),
                        listOf(packet),
                        mapOf(packet.packetId to producerBatchEligibility()),
                    )
                }
                val producerDependencies = producerTasks.zipWithNext().mapIndexed { index, (from, to) ->
                    clipDependency(from.taskId, to.taskId, plan.orderingToken.value, "mask-producer.$index")
                }
                GPUCoreClipArtifactTopology(
                    contentKey,
                    preparations,
                    allocations,
                    producerTasks,
                    producerDependencies,
                    producerTasks.last().taskId,
                    GPUFrameResourceUse(
                        mask,
                        GPUFrameResourceRole.ClipMask,
                        GPUFrameResourceUsage.TextureBinding,
                        GPUFrameResourceLifetime.FrameLocal,
                        false,
                    ),
                    plan.orderingToken.value,
                )
            }
            GPUClipExecutionPlan.NoClip,
            is GPUClipExecutionPlan.ScissorOnly,
            is GPUClipExecutionPlan.AnalyticCoverage,
            is GPUClipExecutionPlan.Refused,
            -> error("Non-resource clip plans do not create artifact topology")
        }
    }

    private fun clipProducerPacket(
        base: GPUDrawPacket,
        plan: GPUClipExecutionPlan,
        taskId: GPUTaskID,
        role: GPUDrawPacketRole,
        renderStep: String,
        variant: String,
        authority: GPUClipProducerAuthority,
    ): GPUDrawPacket = GPUDrawPacket(
        packetId = GPUDrawPacketID("packet.${taskId.value}"),
        commandIdValue = base.commandIdValue,
        analysisRecordId = "analysis.${taskId.value}",
        passId = "pass.${taskId.value}",
        layerId = base.layerId,
        bindingListId = "bindings.${taskId.value}",
        insertionReasonCode = "$renderStep.$variant",
        sortKey = base.sortKey,
        sortKeyPreimage = base.sortKeyPreimage,
        renderStepId = GPURenderStepID(renderStep),
        renderStepVersion = 1,
        role = role,
        blendPlan = canonicalSolidRectSrcOverBlendPlan(),
        renderPipelineKey = GPURenderPipelineKey(
            "pipeline.$renderStep.${plan.clipResourceKey()}.${authority.selectorIdentity}",
        ),
        bindingLayoutHash = "layout.$renderStep.none",
        vertexSourceLabel = "clip-producer-authority",
        targetStateHash = "target.$renderStep.single-sample",
        originalPaintOrder = base.originalPaintOrder,
        resourceGeneration = PREPARED_FRAME_LATE_BOUND_RESOURCE_GENERATION,
        frameProvenance = base.frameProvenance,
        clipCoveragePlan = base.clipCoveragePlan,
        clipExecutionPlan = plan,
        clipProducerAuthority = authority,
    )

    private fun producerBatchEligibility() = GPUPassBatchEligibility(
        kind = GPUPassBatchKind.SolidFill,
        queueGuard = GPUPassBatchQueueGuard(emptyList(), emptyList()),
    )

    private fun clipDependency(
        from: GPUTaskID,
        to: GPUTaskID,
        orderingToken: String,
        reason: String,
    ) = GPUTaskDependency(
        from,
        to,
        "clip-producer-consumer",
        GPUTaskUseToken(orderingToken),
        "preserve.core-primitive.clip.$reason",
    )


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
        clipExecutionPlan = basePacket.clipExecutionPlan,
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
