package org.graphiks.kanvas.gpu.renderer.recording

import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoveragePlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionPlan
import org.graphiks.kanvas.gpu.renderer.color.GPUColorFormat
import org.graphiks.kanvas.gpu.renderer.color.GPUColorInterpretation
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnostic
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticCode
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticDomain
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticSeverity
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageRefusalCodes
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveRenderPipelineStructuralKey
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImageGeometry
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImageGeometryClass
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImageBindingLayoutTopology
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImageVertex
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryAllocation
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryBudgetPlan
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
import org.graphiks.kanvas.gpu.renderer.resources.GPUPreparedImageFrameResourcePlan
import org.graphiks.kanvas.gpu.renderer.resources.GPUPreparedImageBindingInput
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourcePreparationRequest
import org.graphiks.kanvas.gpu.renderer.resources.GPUUploadLayout
import org.graphiks.kanvas.gpu.renderer.resources.buildPreparedImageFrameResourcePlanFromBindings
import org.graphiks.kanvas.gpu.renderer.state.GPULoadStorePlan
import org.graphiks.kanvas.gpu.renderer.state.GPUStorePlan

data class GPUPreparedSurfaceFrameRequest(
    val baseTaskList: GPUTaskList,
    val capabilities: GPUCapabilities,
    val target: GPUFrameTargetRef,
    val targetBounds: GPUPixelBounds,
    val semanticsByCommandId: Map<Int, GPUDrawSemanticPayload>,
    val readbackRequestId: GPUReadbackRequestID?,
    val targetFormat: GPUColorFormat = GPUColorFormat.RGBA8Unorm,
)

sealed interface GPUPreparedSurfaceFrameResult {
    data class Recorded(val taskList: GPUTaskList) : GPUPreparedSurfaceFrameResult
    data class Refused(val diagnostic: GPUDiagnostic) : GPUPreparedSurfaceFrameResult
}

/**
 * Builds a handle-free prepared frame while keeping semantic/resource authorities immutable.
 *
 * Validation and all resource planning finish before any output task collection is constructed.
 */
class GPUPreparedSurfaceFrameTaskListBuilder(
    private val readbackLayoutPlanner: GPUReadbackLayoutPlanner = GPUReadbackLayoutPlanner(),
) {
    fun build(
        request: GPUPreparedSurfaceFrameRequest,
        configuredAggregateBudgetBytes: Long = 1L shl 30,
    ): GPUPreparedSurfaceFrameResult {
        request.baseTaskList.tasks.filterIsInstance<GPUTask.Refused>().firstOrNull()?.let {
            return GPUPreparedSurfaceFrameResult.Refused(it.diagnostic.atRecordingBoundary())
        }
        request.baseTaskList.diagnostics.firstOrNull(GPUDiagnostic::isTerminal)?.let {
            return GPUPreparedSurfaceFrameResult.Refused(it.atRecordingBoundary())
        }
        val baseRenders = request.baseTaskList.tasks.filterIsInstance<GPUTask.Render>()
        if (baseRenders.isEmpty() || request.baseTaskList.tasks.any { it !is GPUTask.Render }) {
            return refused(
                "invalid.recording.prepared_surface_base_tasks",
                "Prepared surfaces require one accepted render-only base task list.",
            )
        }
        if (request.targetBounds.left != 0 || request.targetBounds.top != 0 ||
            request.targetBounds.width <= 0 || request.targetBounds.height <= 0
        ) {
            return refused(
                "invalid.recording.prepared_surface_target",
                "Prepared surfaces require one non-empty zero-origin target.",
            )
        }
        if (configuredAggregateBudgetBytes <= 0L) {
            return refused(
                "invalid.recording.prepared_surface_budget",
                "Prepared-surface aggregate budget must be positive.",
            )
        }

        val packets = baseRenders.flatMap(GPUTask.Render::drawPackets)
            .sortedBy(GPUDrawPacket::originalPaintOrder)
        val commandIds = packets.map(GPUDrawPacket::commandIdValue)
        val semanticRefs = request.semanticsByCommandId.values
            .map { semantic -> semantic.payloadRef.commandIdValue }
        if (commandIds.distinct().size != commandIds.size ||
            packets.map(GPUDrawPacket::originalPaintOrder).distinct().size != packets.size ||
            commandIds.toSet() != request.semanticsByCommandId.keys ||
            semanticRefs.distinct().size != semanticRefs.size ||
            semanticRefs.toSet() != request.semanticsByCommandId.keys ||
            request.semanticsByCommandId.any { (commandId, semantic) ->
                semantic.payloadRef.commandIdValue != commandId
            }
        ) {
            return refused(
                "invalid.recording.prepared_surface_semantics",
                "Every accepted packet requires one unique semantic with the identical command identity.",
            )
        }
        val unsupported = request.semanticsByCommandId.values.firstOrNull {
            it !is GPUDrawSemanticPayload.CorePrimitive &&
                it !is GPUDrawSemanticPayload.SampledImage
        }
        if (unsupported != null) {
            return refused(
                "unsupported.recording.prepared_surface_semantic_type",
                "Prepared surfaces accept only CorePrimitive and SampledImage semantics.",
            )
        }
        val invalidImage = request.semanticsByCommandId.values
            .filterIsInstance<GPUDrawSemanticPayload.SampledImage>()
            .firstOrNull { semantic ->
                semantic.artifact.colorInterpretation !=
                    GPUColorInterpretation.EncodedPremulSrgb.value ||
                    semantic.targetBounds != request.targetBounds ||
                    !semantic.hasCanonicalHashIntegrity()
            }
        if (invalidImage != null) {
            return refused(
                "invalid.recording.prepared_image_semantic",
                "Prepared images require canonical EncodedPremulSrgb artifact and target authority.",
            )
        }

        val allCore = request.semanticsByCommandId.values
            .all { it is GPUDrawSemanticPayload.CorePrimitive }
        if (allCore) {
            @Suppress("UNCHECKED_CAST")
            val coreSemantics = request.semanticsByCommandId as
                Map<Int, GPUDrawSemanticPayload.CorePrimitive>
            return when (
                val core = GPUCorePrimitivePreparedFrameTaskListAssembler(readbackLayoutPlanner).build(
                    GPUCorePrimitivePreparedFrameRequest(
                        baseTaskList = request.baseTaskList,
                        capabilities = request.capabilities,
                        target = request.target,
                        targetBounds = request.targetBounds,
                        semanticsByCommandId = coreSemantics,
                        readbackRequestId = request.readbackRequestId,
                        configuredAggregateBudgetBytes = configuredAggregateBudgetBytes,
                        targetFormat = request.targetFormat,
                    ),
                )
            ) {
                is GPUCorePrimitivePreparedFrameResult.Recorded ->
                    GPUPreparedSurfaceFrameResult.Recorded(core.taskList)
                is GPUCorePrimitivePreparedFrameResult.Refused ->
                    GPUPreparedSurfaceFrameResult.Refused(core.diagnostic)
            }
        }
        val invalidRoutePacket = packets.firstOrNull { packet ->
            val semantic = request.semanticsByCommandId.getValue(packet.commandIdValue)
            semantic is GPUDrawSemanticPayload.SampledImage &&
                (packet.renderStepId.value != semantic.payloadRef.renderStepIdentity ||
                    semantic.payloadRef.renderStepIdentity != "image.draw.texture_upload")
        }
        if (invalidRoutePacket != null) {
            return refused(
                "invalid.recording.prepared_surface_route_identity",
                "Prepared-surface packets and semantics must retain one identical closed render route.",
            )
        }
        val invalidCoreAuthority = packets.firstOrNull { packet ->
            val semantic = request.semanticsByCommandId.getValue(packet.commandIdValue) as?
                GPUDrawSemanticPayload.CorePrimitive ?: return@firstOrNull false
            val coverage = packet.clipCoveragePlan
            val execution = packet.clipExecutionPlan
            coverage == null || execution == null ||
                coverage != semantic.clipCoveragePlan ||
                semantic.clipExecutionPlanIdentity?.let { identity ->
                    execution.canonicalIdentity() != identity
                } == true
        }
        if (invalidCoreAuthority != null) {
            return refused(
                "invalid.recording.prepared_surface_core_authority",
                "Mixed prepared surfaces require exact packet clip coverage and execution authorities.",
            )
        }

        val imagePackets = packets.filter { packet ->
            request.semanticsByCommandId.getValue(packet.commandIdValue) is
                GPUDrawSemanticPayload.SampledImage
        }
        val imageSemantics = imagePackets.associate { packet ->
            packet.commandIdValue to
                request.semanticsByCommandId.getValue(packet.commandIdValue)
                    as GPUDrawSemanticPayload.SampledImage
        }
        val imagePlans = imageSemantics.values
            .groupBy { semantic -> semantic.artifact.key }
            .toSortedMap(compareBy { key -> key.value })
            .values
            .mapIndexed { index, semantics ->
                val artifact = semantics.first().artifact
                if (semantics.any { it.artifact.contentHash != artifact.contentHash }) {
                    return refused(
                        "invalid.recording.prepared_image_artifact_identity",
                        "One prepared-image artifact key must identify one exact immutable byte artifact.",
                    )
                }
                buildPreparedImageFrameResourcePlanFromBindings(
                    artifact = artifact,
                    bindingInputs = semantics.map { semantic ->
                        GPUPreparedImageBindingInput(
                            packetId = packetForSemantic(packets, semantic).packetId.value,
                            sampling = semantic.sampling,
                        )
                    },
                    bindingLayoutHash = GPUPreparedImageBindingLayoutTopology.IDENTITY,
                    capabilities = request.capabilities,
                    frameIdentity = request.baseTaskList.frameId.value.toString(),
                    uploadTaskId = GPUTaskID(
                        "task.prepared-surface.image-upload.${request.baseTaskList.frameId.value}.$index",
                    ),
                )
            }
        val imagePlanByArtifactKey = imagePlans.associateBy { plan ->
            plan.bindingRequests.first().artifactKey
        }

        val readbackRequest = request.readbackRequestId?.let { requestId ->
            GPUFrameReadbackRequest(
                requestId = requestId,
                sourceBounds = request.targetBounds,
                pixelFormat = GPUReadbackPixelFormat.Rgba8Unorm,
                outputColorInterpretation = GPUColorInterpretation.EncodedPremulSrgb,
            )
        }
        val readbackPlan = readbackRequest?.let { frameReadback ->
            when (val plan = readbackLayoutPlanner.plan(frameReadback, request.capabilities)) {
                is GPUReadbackLayoutPlan.Planned -> plan
                is GPUReadbackLayoutPlan.Refused ->
                    return GPUPreparedSurfaceFrameResult.Refused(plan.diagnostic)
            }
        }
        val enclosingAllocations = buildList {
            imagePlans.forEach { plan -> addAll(plan.memoryAllocations) }
            readbackPlan?.let { plan ->
                add(
                    GPUFrameMemoryAllocation(
                        label = "prepared-surface.readback",
                        category = GPUFrameMemoryCategory.ReadbackStaging,
                        bytes = plan.stagingDescriptor.minimumBufferBytes,
                        resourceKind = GPUFrameMemoryResourceKind.Buffer,
                        extent = null,
                    ),
                )
            }
        }
        val conflictingEnclosingAllocation = enclosingAllocations
            .groupBy(GPUFrameMemoryAllocation::label)
            .values.firstOrNull { sameLabel -> sameLabel.distinct().size > 1 }
        if (conflictingEnclosingAllocation != null) {
            return refused(
                "invalid.recording.prepared_surface_resource_identity",
                "Prepared-surface memory allocation identities must be exact and unique.",
            )
        }
        val coreAssembly = prepareMixedCoreAuthority(
            request = request,
            packets = packets,
            configuredAggregateBudgetBytes = configuredAggregateBudgetBytes,
            additionalMemoryAllocations = enclosingAllocations.distinct(),
        )
        if (coreAssembly is MixedCoreAssembly.Refused) {
            return GPUPreparedSurfaceFrameResult.Refused(coreAssembly.diagnostic)
        }
        coreAssembly as MixedCoreAssembly.Prepared
        val targetBytes = try {
            Math.multiplyExact(
                Math.multiplyExact(request.targetBounds.width.toLong(), request.targetBounds.height.toLong()),
                4L,
            )
        } catch (_: ArithmeticException) {
            return refused(
                "invalid.recording.prepared_surface_budget",
                "Prepared-surface target byte size overflowed.",
            )
        }
        val memoryBudget = coreAssembly.memoryBudget ?: GPUFrameMemoryBudgetPlanner.plan(
            GPUFrameMemoryBudgetRequest(
                allocations = listOf(
                    GPUFrameMemoryAllocation(
                        label = "prepared-surface.scene-target",
                        category = GPUFrameMemoryCategory.CanonicalTarget,
                        bytes = targetBytes,
                        resourceKind = GPUFrameMemoryResourceKind.Texture2D,
                        extent = request.targetBounds,
                    ),
                ) + enclosingAllocations,
                configuredAggregateBudgetBytes = configuredAggregateBudgetBytes,
                deviceLimits = requireNotNull(request.capabilities.limits),
            ),
        )
        memoryBudget.diagnostic?.let { diagnostic ->
            return GPUPreparedSurfaceFrameResult.Refused(diagnostic)
        }

        val preparations = mutableListOf<GPUResourcePreparationRequest>()
        preparations += coreAssembly.preparations
            .filterNot { preparation -> preparation.resource == request.target }
        preparations += corePrimitiveTargetPreparation(
            request.target,
            request.targetBounds,
            request.targetFormat,
        )
        imagePlans.forEach { plan ->
            preparations += plan.preparationRequests
        }
        val readbackStaging = readbackPlan?.let {
            GPUFrameBufferRef("buffer.prepared-surface.readback.${request.baseTaskList.frameId.value}")
        }
        if (readbackPlan != null && readbackStaging != null) {
            preparations += GPUResourcePreparationRequest(
                resource = readbackStaging,
                descriptor = GPUFrameBufferDescriptor(
                    readbackPlan.stagingDescriptor.minimumBufferBytes,
                    4L,
                ),
                role = GPUFrameResourceRole.ReadbackStaging,
                usages = setOf(
                    GPUFrameResourceUsage.CopyDestination,
                    GPUFrameResourceUsage.MapRead,
                ),
                lifetime = GPUFrameResourceLifetime.FrameLocal,
                byteSize = readbackPlan.stagingDescriptor.minimumBufferBytes,
                diagnosticLabel = "prepared-surface.readback",
            )
        }
        val duplicatePreparation = preparations.groupBy { it.resource }.values
            .firstOrNull { group -> group.size > 1 }
        if (duplicatePreparation != null) {
            return refused(
                "invalid.recording.prepared_surface_resource_identity",
                "Prepared-surface resource identities must be unique before task emission.",
            )
        }

        val recordingId = baseRenders.first().recordingId
        val prepareTask = GPUTask.PrepareResources(
            taskId = GPUTaskID("task.prepared-surface.prepare.${request.baseTaskList.frameId.value}"),
            recordingId = recordingId,
            phase = GPUTaskPhase.Prepare,
            requests = preparations,
        )
        val uploads = imagePlans.map { plan ->
            GPUTask.Upload(
                taskId = plan.uploadTaskId,
                recordingId = recordingId,
                phase = GPUTaskPhase.Upload,
                staging = plan.stagingRef,
                destination = plan.frameTextureRef,
                layout = plan.uploadTaskLayout,
                preparedImagePlan = plan,
            )
        }
        val baseRenderByPacketId = baseRenders.flatMap { render ->
            render.drawPackets.map { packet -> packet.packetId to render }
        }.toMap()
        val preparedRenderByPacketId = baseRenderByPacketId +
            coreAssembly.renderByPacketId
        val orderedPreparedPackets = packets.flatMap { packet ->
            val semantic = request.semanticsByCommandId.getValue(packet.commandIdValue)
            if (semantic is GPUDrawSemanticPayload.CorePrimitive) {
                coreAssembly.packetByCommandId.getValue(packet.commandIdValue)
            } else {
                listOf(packet.withSemantic(semantic))
            }
        }
        val routeRuns = orderedPreparedPackets.contiguousRouteRuns(preparedRenderByPacketId)
        val renders = routeRuns.mapIndexed { index, run ->
            val original = preparedRenderByPacketId.getValue(run.first().packetId)
            val uses = if (run.first().semanticPayload is GPUDrawSemanticPayload.SampledImage) {
                run.flatMap { packet ->
                    val semantic = packet.semanticPayload as GPUDrawSemanticPayload.SampledImage
                    val plan = imagePlanByArtifactKey.getValue(semantic.artifact.key)
                    listOf(
                        GPUFrameResourceUse(
                            plan.frameTextureRef,
                            GPUFrameResourceRole.StorageData,
                            GPUFrameResourceUsage.TextureBinding,
                            GPUFrameResourceLifetime.FrameLocal,
                            write = false,
                        ),
                        GPUFrameResourceUse(
                            plan.uniformRef,
                            GPUFrameResourceRole.UniformData,
                            GPUFrameResourceUsage.Uniform,
                            GPUFrameResourceLifetime.FrameLocal,
                            write = false,
                        ),
                    )
                }.distinct()
            } else {
                run.flatMap { packet ->
                    coreAssembly.resourceUsesByCommandId[packet.commandIdValue].orEmpty()
                }.distinct()
            }
            GPUTask.Render(
                taskId = GPUTaskID(
                    "task.prepared-surface.render.${request.baseTaskList.frameId.value}.$index",
                ),
                recordingId = recordingId,
                phase = GPUTaskPhase.Render,
                target = request.target,
                loadStore = GPULoadStorePlan(
                    loadOp = if (index == 0) "clear" else "load",
                    storePlan = GPUStorePlan.Store,
                ),
                samplePlan = original.samplePlan,
                resourceUses = uses,
                provisionalSegmentKey = original.provisionalSegmentKey,
                drawPackets = run,
                batchEligibilityByPacketId = run.associate { packet ->
                    packet.packetId to
                        preparedRenderByPacketId.getValue(packet.packetId)
                            .batchEligibilityByPacketId.getValue(packet.packetId)
                },
                sampleContinuationKey = original.sampleContinuationKey,
                depthStencilLoadStore = original.depthStencilLoadStore?.takeIf {
                    run.any { packet ->
                        packet.role == org.graphiks.kanvas.gpu.renderer.passes
                            .GPUDrawPacketRole.PathStencilProducer ||
                            packet.role == org.graphiks.kanvas.gpu.renderer.passes
                                .GPUDrawPacketRole.PathStencilCover
                    }
                },
                preparedImageBindingsByPacketId = run.mapNotNull { packet ->
                    val semantic = packet.semanticPayload as? GPUDrawSemanticPayload.SampledImage
                        ?: return@mapNotNull null
                    val binding = imagePlanByArtifactKey.getValue(semantic.artifact.key)
                        .bindingRequests.single { request ->
                            request.packetId == packet.packetId.value
                        }
                    packet.packetId to binding
                }.toMap(),
            )
        }

        val dependencies = mutableListOf<GPUTaskDependency>()
        uploads.forEachIndexed { index, upload ->
            dependencies += dependency(
                prepareTask.taskId,
                upload.taskId,
                "prepared-image-resource-order",
                "prepared.image.prepare-before-upload",
                "prepared-image.prepare.$index",
            )
        }
        renders.forEachIndexed { index, render ->
            dependencies += dependency(
                prepareTask.taskId,
                render.taskId,
                "prepared-surface-resource-order",
                "prepared.surface.prepare-before-consumer",
                "prepared-surface.prepare.$index",
            )
            render.drawPackets
                .mapNotNull { packet -> packet.semanticPayload as? GPUDrawSemanticPayload.SampledImage }
                .map { semantic -> imagePlanByArtifactKey.getValue(semantic.artifact.key).uploadTaskId }
                .distinct()
                .forEach { uploadTaskId ->
                    dependencies += dependency(
                        uploadTaskId,
                        render.taskId,
                        "prepared-image-resource-order",
                        "prepared.image.upload-before-consumer",
                        "prepared-image.consumer.${dependencies.size}",
                    )
                }
        }
        renders.zipWithNext().forEachIndexed { index, (from, to) ->
            dependencies += dependency(
                from.taskId,
                to.taskId,
                "prepared-scene-order",
                "preserve.prepared-scene.order",
                "prepared-surface.paint.$index",
            )
        }
        val tasks = mutableListOf<GPUTask>(prepareTask)
        tasks += uploads
        tasks += renders
        if (readbackRequest != null && readbackStaging != null) {
            val readbackTask = GPUTask.Readback(
                taskId = GPUTaskID("task.prepared-surface.readback.${request.baseTaskList.frameId.value}"),
                recordingId = recordingId,
                phase = GPUTaskPhase.Readback,
                source = request.target,
                staging = readbackStaging,
                request = readbackRequest,
            )
            tasks += readbackTask
            dependencies += dependency(
                renders.last().taskId,
                readbackTask.taskId,
                "prepared-surface-readback-order",
                "prepared.surface.render-before-readback",
                "prepared-surface.readback",
            )
        }
        val colorDiagnostic = GPUDiagnostic(
            code = GPUDiagnosticCode("info.recording.prepared_image_color_contract"),
            domain = GPUDiagnosticDomain.Color,
            severity = GPUDiagnosticSeverity.Info,
            message =
                "Prepared color images upload straight encoded sRGB bytes through an sRGB source " +
                    "texture and shade as linear-premultiplied values into the declared target.",
            facts = mapOf(
                "image.upload.format" to "RGBA8UnormSrgb",
                "image.upload.encoding" to "StraightEncodedSrgb",
                "image.upload.interpretation" to "StraightEncodedSrgb",
                "image.target.format" to request.targetFormat.value,
                "image.shader.interpretation" to "LinearPremul",
                "image.attachment.srgbConversion" to
                    (request.targetFormat == GPUColorFormat.RGBA8UnormSrgb).toString(),
            ),
        )
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
                diagnostics = request.baseTaskList.diagnostics + colorDiagnostic,
            ),
        )
    }

    private fun prepareMixedCoreAuthority(
        request: GPUPreparedSurfaceFrameRequest,
        packets: List<GPUDrawPacket>,
        configuredAggregateBudgetBytes: Long,
        additionalMemoryAllocations: List<GPUFrameMemoryAllocation>,
    ): MixedCoreAssembly {
        val corePackets = packets.mapNotNull { packet ->
            val semantic = request.semanticsByCommandId.getValue(packet.commandIdValue) as?
                GPUDrawSemanticPayload.CorePrimitive ?: return@mapNotNull null
            packet.withSemantic(semantic)
        }
        if (corePackets.isEmpty()) {
            return MixedCoreAssembly.Prepared(
                packetByCommandId = emptyMap(),
                resourceUsesByCommandId = emptyMap(),
                preparations = emptyList(),
                memoryBudget = null,
            )
        }
        val baseRenderByPacketId = request.baseTaskList.tasks.filterIsInstance<GPUTask.Render>()
            .flatMap { render -> render.drawPackets.map { packet -> packet.packetId to render } }
            .toMap()
        val coreRenders = corePackets.mapIndexed { index, packet ->
            val base = baseRenderByPacketId.getValue(packet.packetId)
            GPUTask.Render(
                taskId = GPUTaskID("task.prepared-surface.core-base.$index"),
                recordingId = base.recordingId,
                phase = GPUTaskPhase.Render,
                target = base.target,
                loadStore = base.loadStore,
                samplePlan = base.samplePlan,
                resourceUses = base.resourceUses,
                provisionalSegmentKey = base.provisionalSegmentKey,
                drawPackets = listOf(packet),
                batchEligibilityByPacketId = mapOf(
                    packet.packetId to base.batchEligibilityByPacketId.getValue(packet.packetId),
                ),
                sampleContinuationKey = base.sampleContinuationKey,
                depthStencilLoadStore = base.depthStencilLoadStore,
            )
        }
        val coreBase = GPUTaskList(
            frameId = request.baseTaskList.frameId,
            capabilitySeal = request.baseTaskList.capabilitySeal,
            recordingSeals = request.baseTaskList.recordingSeals,
            expectedReplayKeyHash = request.baseTaskList.expectedReplayKeyHash,
            tasks = coreRenders,
            dependencies = emptyList(),
            phaseOrder = request.baseTaskList.phaseOrder,
            memoryBudget = request.baseTaskList.memoryBudget,
            diagnostics = request.baseTaskList.diagnostics,
        )
        val coreSemantics = request.semanticsByCommandId.mapNotNull { (commandId, semantic) ->
            (semantic as? GPUDrawSemanticPayload.CorePrimitive)?.let { commandId to it }
        }.toMap()
        return when (
            val result = GPUCorePrimitivePreparedFrameTaskListAssembler(readbackLayoutPlanner).build(
                GPUCorePrimitivePreparedFrameRequest(
                    baseTaskList = coreBase,
                    capabilities = request.capabilities,
                    target = request.target,
                    targetBounds = request.targetBounds,
                    semanticsByCommandId = coreSemantics,
                    readbackRequestId = null,
                    configuredAggregateBudgetBytes = configuredAggregateBudgetBytes,
                    targetFormat = request.targetFormat,
                ),
                additionalMemoryAllocations = additionalMemoryAllocations,
            )
        ) {
            is GPUCorePrimitivePreparedFrameResult.Refused ->
                MixedCoreAssembly.Refused(result.diagnostic)
            is GPUCorePrimitivePreparedFrameResult.Recorded -> {
                val renders = result.taskList.tasks.filterIsInstance<GPUTask.Render>()
                val consumerByCommandId = coreSemantics.keys.associateWith { commandId ->
                    renders.singleOrNull { render ->
                        render.drawPackets.any { packet -> packet.commandIdValue == commandId }
                    }
                }
                if (consumerByCommandId.values.any { it == null } ||
                    renders.any { render ->
                        render.drawPackets.none { packet -> packet.commandIdValue in coreSemantics }
                    }
                ) {
                    MixedCoreAssembly.Refused(
                        diagnostic(
                            "unsupported.recording.prepared_surface_core_producer_topology",
                            "Mixed prepared surfaces do not yet interleave core producer passes with image runs.",
                        ),
                    )
                } else {
                    MixedCoreAssembly.Prepared(
                        packetByCommandId = consumerByCommandId.mapValues { (commandId, render) ->
                            val packetsForCommand = requireNotNull(render).drawPackets.filter { packet ->
                                packet.commandIdValue == commandId
                            }
                            if (packetsForCommand.any { packet ->
                                    packet.role == org.graphiks.kanvas.gpu.renderer.passes
                                        .GPUDrawPacketRole.PathStencilProducer
                                }
                            ) {
                                packetsForCommand
                            } else {
                                packetsForCommand.map(
                                    GPUDrawPacket::withoutPreparedPathDepthStencil,
                                )
                            }
                        },
                        renderByPacketId = renders.flatMap { render ->
                            render.drawPackets.map { packet -> packet.packetId to render }
                        }.toMap(),
                        resourceUsesByCommandId = consumerByCommandId.mapValues { (commandId, render) ->
                            val exactRender = requireNotNull(render)
                            val hasPath = exactRender.drawPackets.any { packet ->
                                packet.commandIdValue == commandId &&
                                    packet.role in setOf(
                                        org.graphiks.kanvas.gpu.renderer.passes
                                            .GPUDrawPacketRole.PathStencilProducer,
                                        org.graphiks.kanvas.gpu.renderer.passes
                                            .GPUDrawPacketRole.PathStencilCover,
                                    )
                            }
                            exactRender.resourceUses.filter { use ->
                                hasPath ||
                                    use.role != GPUFrameResourceRole.PathDepthStencil
                            }
                        },
                        preparations = result.taskList.tasks
                            .filterIsInstance<GPUTask.PrepareResources>()
                            .flatMap(GPUTask.PrepareResources::requests),
                        memoryBudget = result.taskList.memoryBudget,
                    )
                }
            }
        }
    }

    private fun refused(code: String, message: String) =
        GPUPreparedSurfaceFrameResult.Refused(diagnostic(code, message))
}

private sealed interface MixedCoreAssembly {
    data class Prepared(
        val packetByCommandId: Map<Int, List<GPUDrawPacket>>,
        val renderByPacketId: Map<
            org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketID,
            GPUTask.Render,
            > = emptyMap(),
        val resourceUsesByCommandId: Map<Int, List<GPUFrameResourceUse>>,
        val preparations: List<GPUResourcePreparationRequest>,
        val memoryBudget: GPUFrameMemoryBudgetPlan?,
    ) : MixedCoreAssembly

    data class Refused(val diagnostic: GPUDiagnostic) : MixedCoreAssembly
}

/** Public module boundary for the validated four-corner prepared-image geometry value. */
fun buildPreparedImageGeometry(
    geometryClass: GPUPreparedImageGeometryClass,
    vertices: List<GPUPreparedImageVertex>,
): GPUPreparedImageGeometry = GPUPreparedImageGeometry(
    geometryClass = geometryClass,
    vertices = vertices,
    indices = listOf(0, 1, 2, 0, 2, 3),
)

private fun packetForSemantic(
    packets: List<GPUDrawPacket>,
    semantic: GPUDrawSemanticPayload.SampledImage,
): GPUDrawPacket = packets.single {
    it.commandIdValue == semantic.payloadRef.commandIdValue
}

private data class PreparedRouteRunKey(
    val semanticKind: String,
    val renderStepId: String,
    val renderStepVersion: Int,
    val renderPipelineKey: String?,
    val bindingLayoutHash: String,
    val samplePlanKey: String,
    val target: String,
    val loadStore: GPULoadStorePlan,
    val provisionalSegmentKey:
        org.graphiks.kanvas.gpu.renderer.passes.GPUProvisionalRenderSegmentKey,
    val depthStencilLoadStore: GPUDepthStencilLoadStorePlan?,
    val targetStateHash: String,
    val continuationKey: String?,
)

private fun List<GPUDrawPacket>.contiguousRouteRuns(
    baseRenderByPacketId: Map<org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketID, GPUTask.Render>,
): List<List<GPUDrawPacket>> {
    val runs = mutableListOf<MutableList<GPUDrawPacket>>()
    forEach { packet ->
        val render = baseRenderByPacketId.getValue(packet.packetId)
        val coreRouteIdentity = if (
            packet.semanticPayload is GPUDrawSemanticPayload.CorePrimitive
        ) {
            render.taskId.value
        } else {
            null
        }
        val key = PreparedRouteRunKey(
            semanticKind = when (packet.semanticPayload) {
                is GPUDrawSemanticPayload.SampledImage -> "sampled-image"
                is GPUDrawSemanticPayload.CorePrimitive -> "core-primitive"
                else -> "unsupported"
            },
            renderStepId = coreRouteIdentity ?: packet.renderStepId.value,
            renderStepVersion = if (coreRouteIdentity == null) packet.renderStepVersion else 0,
            renderPipelineKey = if (coreRouteIdentity == null) {
                packet.renderPipelineKey?.value
            } else {
                null
            },
            bindingLayoutHash = coreRouteIdentity ?: packet.bindingLayoutHash,
            samplePlanKey = render.samplePlan.specializationKey,
            target = render.target.value,
            loadStore = render.loadStore,
            provisionalSegmentKey = render.provisionalSegmentKey,
            depthStencilLoadStore = render.depthStencilLoadStore,
            targetStateHash = coreRouteIdentity ?: packet.targetStateHash,
            continuationKey = render.sampleContinuationKey?.toString(),
        )
        val current = runs.lastOrNull()
        val currentKey = current?.firstOrNull()?.let { first ->
            val firstRender = baseRenderByPacketId.getValue(first.packetId)
            val firstCoreRouteIdentity = if (
                first.semanticPayload is GPUDrawSemanticPayload.CorePrimitive
            ) {
                firstRender.taskId.value
            } else {
                null
            }
            PreparedRouteRunKey(
                semanticKind = when (first.semanticPayload) {
                    is GPUDrawSemanticPayload.SampledImage -> "sampled-image"
                    is GPUDrawSemanticPayload.CorePrimitive -> "core-primitive"
                    else -> "unsupported"
                },
                renderStepId = firstCoreRouteIdentity ?: first.renderStepId.value,
                renderStepVersion =
                    if (firstCoreRouteIdentity == null) first.renderStepVersion else 0,
                renderPipelineKey = if (firstCoreRouteIdentity == null) {
                    first.renderPipelineKey?.value
                } else {
                    null
                },
                bindingLayoutHash = firstCoreRouteIdentity ?: first.bindingLayoutHash,
                samplePlanKey = firstRender.samplePlan.specializationKey,
                target = firstRender.target.value,
                loadStore = firstRender.loadStore,
                provisionalSegmentKey = firstRender.provisionalSegmentKey,
                depthStencilLoadStore = firstRender.depthStencilLoadStore,
                targetStateHash = firstCoreRouteIdentity ?: first.targetStateHash,
                continuationKey = firstRender.sampleContinuationKey?.toString(),
            )
        }
        if (current == null || currentKey != key) {
            runs += mutableListOf(packet)
        } else {
            current += packet
        }
    }
    return runs
}

private fun GPUDrawPacket.withSemantic(
    semantic: GPUDrawSemanticPayload,
    clipCoverageOverride: GPUClipCoveragePlan? = clipCoveragePlan,
    clipExecutionOverride: GPUClipExecutionPlan? = clipExecutionPlan,
) = GPUDrawPacket(
    packetId = packetId,
    commandIdValue = commandIdValue,
    analysisRecordId = analysisRecordId,
    passId = passId,
    layerId = layerId,
    bindingListId = bindingListId,
    insertionReasonCode = insertionReasonCode,
    sortKey = sortKey,
    sortKeyPreimage = sortKeyPreimage,
    renderStepId = renderStepId,
    renderStepVersion = renderStepVersion,
    role = role,
    blendPlan = blendPlan,
    renderPipelineKey = renderPipelineKey,
    computePipelineKey = computePipelineKey,
    bindingLayoutHash = bindingLayoutHash,
    uniformSlot = uniformSlot,
    resourceSlot = resourceSlot,
    semanticPayload = semantic,
    vertexSourceLabel = vertexSourceLabel,
    scissorBoundsHash = scissorBoundsHash,
    targetStateHash = targetStateHash,
    originalPaintOrder = originalPaintOrder,
    resourceGeneration = resourceGeneration,
    frameProvenance = frameProvenance,
    clipCoveragePlan = clipCoverageOverride,
    clipExecutionPlan = clipExecutionOverride,
    diagnostics = diagnostics,
    clipProducerAuthority = clipProducerAuthority,
)

private fun GPUDrawPacket.withoutPreparedPathDepthStencil(): GPUDrawPacket {
    val authority = requireNotNull(corePrimitivePreparedAuthority)
    val structural = authority.structuralPipelineKey.copy(
        depthStencil = GPUCorePrimitiveRenderPipelineStructuralKey.DepthStencil.None,
    )
    val pipeline = structural.stableRenderPipelineKey(CORE_PRIMITIVE_RENDER_PIPELINE_KEY)
    val rebuilt = GPUDrawPacket(
        packetId = packetId,
        commandIdValue = commandIdValue,
        analysisRecordId = analysisRecordId,
        passId = passId,
        layerId = layerId,
        bindingListId = bindingListId,
        insertionReasonCode = insertionReasonCode,
        sortKey = sortKey,
        sortKeyPreimage = sortKeyPreimage,
        renderStepId = renderStepId,
        renderStepVersion = renderStepVersion,
        role = role,
        blendPlan = blendPlan,
        renderPipelineKey = pipeline,
        computePipelineKey = computePipelineKey,
        bindingLayoutHash = bindingLayoutHash,
        uniformSlot = uniformSlot,
        resourceSlot = resourceSlot,
        semanticPayload = semanticPayload,
        vertexSourceLabel = vertexSourceLabel,
        scissorBoundsHash = scissorBoundsHash,
        targetStateHash = targetStateHash,
        originalPaintOrder = originalPaintOrder,
        resourceGeneration = resourceGeneration,
        frameProvenance = frameProvenance,
        clipCoveragePlan = clipCoveragePlan,
        clipExecutionPlan = clipExecutionPlan,
        diagnostics = diagnostics,
        clipProducerAuthority = clipProducerAuthority,
    )
    return rebuilt.attachCorePrimitivePreparedAuthority(
        authority.copy(
            structuralPipelineKey = structural,
            renderPipelineKey = pipeline,
        ),
    )
}

private fun dependency(
    from: GPUTaskID,
    to: GPUTaskID,
    kind: String,
    reason: String,
    token: String,
) = GPUTaskDependency(
    fromTaskId = from,
    toTaskId = to,
    dependencyKind = kind,
    useToken = GPUTaskUseToken(token),
    reasonCode = reason,
)

private fun diagnostic(code: String, message: String) = GPUDiagnostic(
    code = GPUDiagnosticCode(code),
    domain = GPUDiagnosticDomain.Recording,
    severity = GPUDiagnosticSeverity.Error,
    message = message,
)

private fun GPUDiagnostic.atRecordingBoundary(): GPUDiagnostic =
    if (code.value in GPUPreparedImageRefusalCodes.ALL) {
        copy(facts = facts + ("boundary" to "recording"))
    } else {
        this
    }
