package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.gpu.renderer.artifacts.GPUPreparedImageUploadArtifact
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionPlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipMaskSampling
import org.graphiks.kanvas.gpu.renderer.color.GPUColorFormat
import org.graphiks.kanvas.gpu.renderer.color.GPUColorInterpretation
import org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnostic
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticCode
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticDomain
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticSeverity
import org.graphiks.kanvas.gpu.renderer.recording.GPUPreparedSurfaceFrameRequest
import org.graphiks.kanvas.gpu.renderer.recording.GPUPreparedSurfaceFrameResult
import org.graphiks.kanvas.gpu.renderer.recording.GPUPreparedSurfaceFrameTaskListBuilder
import org.graphiks.kanvas.gpu.renderer.recording.GPUDestinationSnapshotOperation
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameID
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackRequestID
import org.graphiks.kanvas.gpu.renderer.recording.GPURecorder
import org.graphiks.kanvas.gpu.renderer.recording.GPURecordingID
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskList
import org.graphiks.kanvas.gpu.renderer.recording.GPUTask
import org.graphiks.kanvas.gpu.renderer.recording.PREPARED_VERTICES_UNMATERIALIZED_PREFLIGHT_REFUSAL_CODE
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveCoverageMode
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlan
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUPreparedImageRefusalCodes
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.glyph.gpu.GPUTextArtifactGeneration

internal data class GPUPreparedSurfaceFrameBuildRequest(
    val candidate: GPUPreparedSurfaceEligibility.Candidate,
    val targetFacts: GPUTargetFacts,
    val targetBounds: GPUPixelBounds,
    val capabilities: GPUCapabilities,
    val deviceGeneration: GPUDeviceGenerationID,
    val target: GPUFrameTargetRef,
    val recordingId: GPURecordingID,
    val frameId: GPUFrameID,
    val readbackRequestId: GPUReadbackRequestID,
    val includeReadback: Boolean = true,
)

/** Authenticated historical route facts carried from the exact prepared Task 5/8 graph. */
internal data class GPUPreparedSurfaceDestinationReadEvidence(
    val commandId: Int,
    val sourceLabel: String,
    val snapshotLabel: String,
    val modeLabel: String,
    val clipStrategy: String,
    val action: String,
) {
    init {
        require(commandId >= 0)
        require(sourceLabel.isNotBlank())
        require(snapshotLabel.isNotBlank())
        require(modeLabel.isNotBlank())
        require(clipStrategy == "direct" || clipStrategy == "alpha-mask")
        require(action == "copy-then-formula")
    }
}

internal sealed interface GPUPreparedSurfaceFrameBuildResult {
    data class NoOp(
        val stateEventCount: Int,
        val textMetrics: GPUPreparedTextFrameMetrics,
        val acceptedTextOperationIndices: Set<Int>,
        val elidedTextOperationIndices: Set<Int>,
        val culledTextOperationIndices: Set<Int>,
    ) : GPUPreparedSurfaceFrameBuildResult

    data class Ready(
        val taskList: GPUTaskList,
        val readbackRequestId: GPUReadbackRequestID,
        val visualOperationCount: Int,
        val stateEventCount: Int,
        val textMetrics: GPUPreparedTextFrameMetrics,
        val textCommandIds: Set<Int>,
        val pathStrokeCommandIds: Set<Int>,
        val destinationReadTextCommandIds: Set<Int> = emptySet(),
        val destinationReadEvidence: List<GPUPreparedSurfaceDestinationReadEvidence> =
            emptyList(),
    ) : GPUPreparedSurfaceFrameBuildResult

    data class Refused(val diagnostic: GPUDiagnostic) : GPUPreparedSurfaceFrameBuildResult
}

/** Builds one handle-free prepared Surface frame without creating or submitting GPU resources. */
internal object GPUPreparedSurfaceFrameBuilder {
    fun build(
        request: GPUPreparedSurfaceFrameBuildRequest,
        taskListBuilder: GPUPreparedSurfaceFrameTaskListBuilder =
            GPUPreparedSurfaceFrameTaskListBuilder(),
    ): GPUPreparedSurfaceFrameBuildResult {
        validateTargetBounds(request)?.let { return GPUPreparedSurfaceFrameBuildResult.Refused(it) }
        validateTargetFormat(request)?.let { return GPUPreparedSurfaceFrameBuildResult.Refused(it) }
        validateFrameIdentities(request)?.let { return GPUPreparedSurfaceFrameBuildResult.Refused(it) }
        return try {
            val hasPreparedText = request.candidate.operations.any { operation ->
                operation is org.graphiks.kanvas.canvas.DisplayOp.DrawText
            }
            val frameGeneration = if (hasPreparedText) {
                request.frameId.value
                    .takeIf { value -> value <= Int.MAX_VALUE.toLong() }
                    ?.toInt()
                    ?: return GPUPreparedSurfaceFrameBuildResult.Refused(
                        diagnostic(
                            code = "invalid.surface.prepared.text-generation",
                            message = "Prepared text frame generation exceeds the typed artifact range.",
                        ),
                    )
            } else {
                0
            }
            val textPreparation = GPUPreparedTextFramePreparer.prepareInventory(
                operations = request.candidate.operations,
                target = request.targetFacts,
                capabilities = request.capabilities,
                generation = GPUTextArtifactGeneration(frameGeneration),
            )
            if (textPreparation is GPUPreparedTextFrameInventoryPreparation.Refused) {
                val refusal = textPreparation.refusal
                return GPUPreparedSurfaceFrameBuildResult.Refused(
                    diagnostic(
                        code = refusal.code,
                        message = "Prepared Surface operation could not be lowered.",
                        facts = refusal.facts + mapOf(
                            "boundary" to "surface",
                            "commandId" to refusal.commandId.toString(),
                            "operationIndex" to refusal.operationIndex.toString(),
                        ),
                    ),
                )
            }
            textPreparation as GPUPreparedTextFrameInventoryPreparation.Ready
            val verticesPreparation = GPUPreparedVerticesFramePreparer.prepare(
                operations = request.candidate.operations,
                target = request.targetFacts,
                config = request.candidate.config,
                capabilities = request.capabilities,
                preparedTextInventory = textPreparation.inventory,
            )
            if (verticesPreparation is GPUPreparedVerticesFramePreparation.Refused) {
                val refusal = verticesPreparation.refusal
                return GPUPreparedSurfaceFrameBuildResult.Refused(
                    diagnostic(
                        code = refusal.code,
                        message = "Prepared Surface operation could not be lowered.",
                        facts = refusal.facts + mapOf(
                            "boundary" to "surface",
                            "commandId" to refusal.commandId.toString(),
                            "operationIndex" to refusal.operationIndex.toString(),
                        ),
                    ),
                )
            }
            verticesPreparation as GPUPreparedVerticesFramePreparation.Ready
            val mapping = verticesPreparation.mapping
            val verticesInventory = verticesPreparation.inventory
            val preparedImages = collectPreparedImageVisuals(
                mapping = mapping,
                operations = request.candidate.operations,
                inventory = textPreparation.inventory,
            )
            if (preparedImages is PreparedImageVisuals.Refused) {
                return GPUPreparedSurfaceFrameBuildResult.Refused(preparedImages.diagnostic)
            }
            preparedImages as PreparedImageVisuals.Ready
            val preparedMapping = mapping.copy(visualCommands = preparedImages.visualCommands)
            val preparedTextSemantics = when (
                val gathered = GPUPreparedTextSemanticBuilder.gather(
                    visualCommands = preparedMapping.visualCommands,
                    inventory = textPreparation.inventory,
                    targetBounds = request.targetBounds,
                    culledTextOperationIndices = mapping.culledTextOperationIndices,
                )
            ) {
                is GPUPreparedTextSemanticGatherResult.Gathered -> gathered.semanticsByCommandId
                is GPUPreparedTextSemanticGatherResult.Refused ->
                    return GPUPreparedSurfaceFrameBuildResult.Refused(
                        diagnostic(
                            code = gathered.code,
                            message = gathered.message,
                            facts = listOfNotNull(
                                gathered.commandId?.let { "commandId" to it.toString() },
                            ).toMap(),
                        ),
                    )
            }
            if (preparedMapping.visualCommands.isEmpty() && verticesInventory.mappedCommands.isEmpty()) {
                val textOperationIndices = request.candidate.operations.withIndex()
                    .filter { indexed -> indexed.value is DisplayOp.DrawText }
                    .mapTo(linkedSetOf()) { indexed -> indexed.index }
                val accountedTextOperationIndices = (
                    textPreparation.inventory.elidedTextOperationIndices +
                        mapping.culledTextOperationIndices
                    ).toSet()
                val hasOnlyTextAndStateOperations = request.candidate.operations.all { operation ->
                    operation is DisplayOp.DrawText ||
                        operation is DisplayOp.Annotation ||
                        operation is DisplayOp.SetTransform ||
                        operation is DisplayOp.SetClip
                }
                if (
                    !hasOnlyTextAndStateOperations ||
                    textOperationIndices.isEmpty() ||
                    textPreparation.inventory.acceptedTextOperationIndices != textOperationIndices ||
                    accountedTextOperationIndices != textOperationIndices ||
                    textPreparation.inventory.elidedTextOperationIndices
                        .any(mapping.culledTextOperationIndices::contains)
                ) {
                    return GPUPreparedSurfaceFrameBuildResult.Refused(
                        diagnostic(
                            code = "invalid.surface.prepared.empty-frame-accounting",
                            message =
                                "A prepared no-op frame requires every text operation to have exact elided or culled authority.",
                        ),
                    )
                }
                return GPUPreparedSurfaceFrameBuildResult.NoOp(
                    stateEventCount = mapping.stateEvents.count { event ->
                        event.kind == GPUFramePathStateKind.Transform ||
                            event.kind == GPUFramePathStateKind.Clip ||
                            event.kind == GPUFramePathStateKind.Annotation
                    },
                    textMetrics = textPreparation.metrics,
                    acceptedTextOperationIndices =
                        textPreparation.inventory.acceptedTextOperationIndices,
                    elidedTextOperationIndices =
                        textPreparation.inventory.elidedTextOperationIndices,
                    culledTextOperationIndices = mapping.culledTextOperationIndices,
                )
            }
            val recorder = GPURecorder(
                recordingId = request.recordingId,
                frameId = request.frameId,
                capabilities = request.capabilities,
                deviceGeneration = request.deviceGeneration,
            )
            val normalizedCommands = (
                preparedMapping.visualCommands.map(GPUFramePathVisualCommand::normalized) +
                    verticesInventory.normalizedCommands(request.targetFacts, request.capabilities)
                ).sortedBy { command -> command.commandId.value }
            normalizedCommands.forEach(recorder::record)
            val recording = recorder.close()
            recording.taskList.diagnostics.firstOrNull { diagnostic -> diagnostic.isTerminal }?.let {
                return GPUPreparedSurfaceFrameBuildResult.Refused(it.atSurfaceBoundary())
            }
            val verticesSemantics = when (val gathered = GPUPreparedVerticesSemanticBuilder.gather(
                normalizedCommands = normalizedCommands,
                inventory = verticesInventory,
                recording = recording,
                target = request.targetFacts,
                targetBounds = request.targetBounds,
            )) {
                is GPUPreparedVerticesSemanticGatherResult.Gathered -> gathered.semanticsByCommandId
                is GPUPreparedVerticesSemanticGatherResult.Refused ->
                    return GPUPreparedSurfaceFrameBuildResult.Refused(
                        diagnostic(gathered.code, gathered.message, gathered.facts),
                    )
            }
            val semantics = when (val gathered = GPUPreparedSurfaceSemanticBuilder.gather(
                visualCommands = preparedMapping.visualCommands,
                normalizedCommands = normalizedCommands,
                recording = recording,
                targetBounds = request.targetBounds,
                imageArtifactsByCommandId = preparedImages.artifactsByCommandId,
                textSemanticsByCommandId = preparedTextSemantics,
                verticesSemanticsByCommandId = verticesSemantics,
            )) {
                is GPUPreparedSurfaceSemanticGatherResult.Gathered -> gathered.semanticsByCommandId
                is GPUPreparedSurfaceSemanticGatherResult.Refused ->
                    return GPUPreparedSurfaceFrameBuildResult.Refused(gathered.diagnostic)
            }
            preflightUnmaterializedPreparedVertices(recording, semantics)?.let { diagnostic ->
                return GPUPreparedSurfaceFrameBuildResult.Refused(diagnostic)
            }
            when (val prepared = taskListBuilder.build(
                GPUPreparedSurfaceFrameRequest(
                    baseTaskList = recording.taskList,
                    capabilities = request.capabilities,
                    target = request.target,
                    targetBounds = request.targetBounds,
                    semanticsByCommandId = semantics,
                    readbackRequestId = request.readbackRequestId.takeIf { request.includeReadback },
                    targetFormat = GPUColorFormat(request.targetFacts.colorFormat),
                ),
            )) {
                is GPUPreparedSurfaceFrameResult.Recorded -> {
                    validateEncodedPremulSrgbOutput(request, preparedMapping, semantics)?.let {
                        return GPUPreparedSurfaceFrameBuildResult.Refused(it)
                    }
                    val destinationReadEvidence = prepared.taskList
                        .authenticatedDestinationReadEvidence(semantics)
                    GPUPreparedSurfaceFrameBuildResult.Ready(
                        taskList = prepared.taskList,
                        readbackRequestId = request.readbackRequestId,
                        visualOperationCount = preparedMapping.visualCommands.size,
                        stateEventCount = mapping.stateEvents.count { event ->
                            event.kind == GPUFramePathStateKind.Transform ||
                                event.kind == GPUFramePathStateKind.Clip ||
                                event.kind == GPUFramePathStateKind.Annotation
                        },
                        textMetrics = textPreparation.metrics,
                        textCommandIds = preparedImages.textCommandIds,
                        pathStrokeCommandIds = preparedImages.pathStrokeCommandIds,
                        destinationReadTextCommandIds = destinationReadEvidence
                            .mapTo(linkedSetOf()) { evidence -> evidence.commandId },
                        destinationReadEvidence = java.util.Collections.unmodifiableList(
                            ArrayList(destinationReadEvidence),
                        ),
                    )
                }
                is GPUPreparedSurfaceFrameResult.Refused ->
                    GPUPreparedSurfaceFrameBuildResult.Refused(prepared.diagnostic.atSurfaceBoundary())
            }
        } catch (failure: Exception) {
            GPUPreparedSurfaceFrameBuildResult.Refused(
                diagnostic(
                    code = "invalid.surface.prepared.frame-build-contract",
                    message = "Prepared Surface frame construction violated an internal contract.",
                    facts = mapOf("failureClass" to failure.javaClass.name),
                ),
            )
        }
    }
}

private fun GPUTaskList.authenticatedDestinationReadEvidence(
    semantics: Map<Int, GPUDrawSemanticPayload>,
): List<GPUPreparedSurfaceDestinationReadEvidence> {
    val rendersByTaskId = tasks.filterIsInstance<GPUTask.Render>()
        .associateBy(GPUTask.Render::taskId)
    return tasks.filterIsInstance<GPUTask.DestinationSnapshots>()
        .flatMap { task -> task.payload.operations }
        .map { operation ->
            val copy = operation as? GPUDestinationSnapshotOperation.TextureCopy
                ?: error("Prepared ColorGlyph destination evidence requires a texture copy")
            val consumer = copy.consumers.single()
            val commandId = consumer.commandId.value
            require(semantics[commandId] is GPUDrawSemanticPayload.ColorGlyph)
            val render = rendersByTaskId.getValue(consumer.renderTaskId)
            val packet = render.drawPackets.single { candidate ->
                candidate.packetId == consumer.packetId &&
                    candidate.commandIdValue == commandId
            }
            val blend = packet.blendPlan as? GPUBlendPlan.ShaderBlendWithDstRead
                ?: error("Prepared ColorGlyph destination evidence requires shader blending")
            GPUPreparedSurfaceDestinationReadEvidence(
                commandId = commandId,
                sourceLabel = packet.vertexSourceLabel,
                snapshotLabel = copy.snapshot.value,
                modeLabel = blend.mode.gpuLabel,
                clipStrategy = when (packet.clipExecutionPlan) {
                    is GPUClipExecutionPlan.CoverageMask -> "alpha-mask"
                    else -> "direct"
                },
                action = "copy-then-formula",
            )
        }
}

/**
 * Renderer-session-owned build authority.
 *
 * Its task-list builder retains the bounded, handle-free composite-program cache across warm
 * frames. Tests and diagnostic one-shot callers may keep using [GPUPreparedSurfaceFrameBuilder].
 */
internal class GPUPreparedSurfaceFrameBuildSession(
    private val taskListBuilder: GPUPreparedSurfaceFrameTaskListBuilder =
        GPUPreparedSurfaceFrameTaskListBuilder(),
) {
    fun build(request: GPUPreparedSurfaceFrameBuildRequest): GPUPreparedSurfaceFrameBuildResult =
        GPUPreparedSurfaceFrameBuilder.build(request, taskListBuilder)
}

private sealed interface PreparedImageVisuals {
    data class Ready(
        val visualCommands: List<GPUFramePathVisualCommand>,
        val artifactsByCommandId: Map<Int, GPUPreparedImageUploadArtifact>,
        val textCommandIds: Set<Int>,
        val pathStrokeCommandIds: Set<Int>,
    ) : PreparedImageVisuals

    data class Refused(val diagnostic: GPUDiagnostic) : PreparedImageVisuals
}

private sealed interface PreparedVisualSource {
    val operationIndex: Int

    data class Image(
        override val operationIndex: Int,
        val image: org.graphiks.kanvas.image.Image,
    ) : PreparedVisualSource

    data class Core(
        override val operationIndex: Int,
    ) : PreparedVisualSource

    data class Text(
        override val operationIndex: Int,
        val subRunIndex: Int,
    ) : PreparedVisualSource

    data class TextStroke(
        override val operationIndex: Int,
    ) : PreparedVisualSource
}

private fun collectPreparedImageVisuals(
    mapping: GPUOpMapping,
    operations: List<DisplayOp>,
    inventory: PreparedTextFrameInventory,
): PreparedImageVisuals {
    val textOperationIndices = operations.withIndex()
        .filter { indexed -> indexed.value is DisplayOp.DrawText }
        .mapTo(linkedSetOf()) { indexed -> indexed.index }
    if (
        inventory.elidedTextOperationIndices.any { operationIndex ->
            operationIndex !in textOperationIndices ||
                inventory.subRunsByOperationIndex[operationIndex].orEmpty().isNotEmpty() ||
                inventory.strokePathsByOperationIndex[operationIndex].orEmpty().isNotEmpty()
        } ||
        mapping.culledTextOperationIndices.any { operationIndex ->
            operationIndex !in textOperationIndices ||
                operationIndex in inventory.elidedTextOperationIndices ||
                (
                    inventory.subRunsByOperationIndex[operationIndex].orEmpty().isEmpty() &&
                        inventory.strokePathsByOperationIndex[operationIndex].orEmpty().isEmpty()
                )
        }
    ) {
        return PreparedImageVisuals.Refused(
            imageCommandSourceDiagnostic(
                message = "Prepared text elision and culling must retain exact inventory ownership.",
            ),
        )
    }
    operations.forEachIndexed { operationIndex, operation ->
        if (
            operation is DisplayOp.DrawText &&
            operationIndex !in inventory.elidedTextOperationIndices &&
            operationIndex !in mapping.culledTextOperationIndices &&
            inventory.subRunsByOperationIndex[operationIndex].orEmpty().isEmpty() &&
            inventory.strokePathsByOperationIndex[operationIndex].orEmpty().isEmpty()
        ) {
            return PreparedImageVisuals.Refused(
                imageCommandSourceDiagnostic(
                    message = "A non-elided prepared text operation has no exact visual source.",
                    facts = mapOf("operationIndex" to operationIndex.toString()),
                ),
            )
        }
    }
    val visualSources = operations.withIndex().flatMap { indexed ->
        val operationIndex = indexed.index
        when (val operation = indexed.value) {
            is DisplayOp.DrawImage ->
                listOf(PreparedVisualSource.Image(operationIndex, operation.image))
            is DisplayOp.DrawImageNine ->
                operation.decompose().map {
                    PreparedVisualSource.Image(operationIndex, operation.image)
                }
            is DisplayOp.DrawImageLattice ->
                operation.decompose().map { cell ->
                    if (cell.color == null) {
                        PreparedVisualSource.Image(operationIndex, operation.image)
                    } else {
                        PreparedVisualSource.Core(operationIndex)
                    }
                }
            is DisplayOp.DrawAtlas ->
                operation.texRects.map {
                    PreparedVisualSource.Image(operationIndex, operation.atlas)
                }
            is DisplayOp.DrawText -> {
                if (
                    operationIndex in inventory.elidedTextOperationIndices ||
                    operationIndex in mapping.culledTextOperationIndices
                ) {
                    return@flatMap emptyList()
                }
                val strokePaths = inventory.strokePathsByOperationIndex[operationIndex].orEmpty()
                if (strokePaths.isNotEmpty()) {
                    strokePaths.map { PreparedVisualSource.TextStroke(operationIndex) }
                } else {
                    inventory.subRunsByOperationIndex[operationIndex].orEmpty().map { subRun ->
                        PreparedVisualSource.Text(operationIndex, subRun.subRunIndex)
                    }
                }
            }
            else -> if (operation.isCorePreparedVisual()) {
                listOf(PreparedVisualSource.Core(operationIndex))
            } else {
                emptyList()
            }
        }
    }
    if (visualSources.size != mapping.visualCommands.size) {
        return PreparedImageVisuals.Refused(
            imageCommandSourceDiagnostic(
                message = "Prepared Surface operations and normalized visuals must be bijective.",
                facts = mapOf(
                    "operationCount" to visualSources.size.toString(),
                    "visualCount" to mapping.visualCommands.size.toString(),
                ),
            ),
        )
    }
    val artifacts = linkedMapOf<Int, GPUPreparedImageUploadArtifact>()
    val textCommandIds = linkedSetOf<Int>()
    val pathStrokeCommandIds = linkedSetOf<Int>()
    visualSources.zip(mapping.visualCommands).forEachIndexed { commandIndex, (source, visual) ->
        val operationIndex = source.operationIndex
        if (visual.normalized.commandId.value != commandIndex ||
            visual.normalized.ordering.paintOrder != commandIndex
        ) {
            return PreparedImageVisuals.Refused(
                imageCommandSourceDiagnostic(
                    message = "Prepared Surface command identity no longer matches source order.",
                    facts = mapOf(
                        "commandId" to visual.normalized.commandId.value.toString(),
                        "operationIndex" to operationIndex.toString(),
                    ),
                ),
            )
        }
        if (source is PreparedVisualSource.Core) {
            if (visual.normalized is NormalizedDrawCommand.DrawImageRect ||
                visual.preparedImage != null
            ) {
                return PreparedImageVisuals.Refused(
                    imageCommandSourceDiagnostic(
                        message = "Prepared core operation was associated with image facts.",
                        facts = mapOf(
                            "commandId" to commandIndex.toString(),
                            "operationIndex" to operationIndex.toString(),
                        ),
                    ),
                )
            }
            return@forEachIndexed
        }
        if (source is PreparedVisualSource.Text) {
            if (visual.normalized !is NormalizedDrawCommand.DrawTextRun ||
                visual.preparedText?.operationIndex != operationIndex ||
                visual.preparedText.subRunIndex != source.subRunIndex ||
                visual.preparedImage != null
            ) {
                return PreparedImageVisuals.Refused(
                    imageCommandSourceDiagnostic(
                        message = "Prepared text source was associated with different command facts.",
                        facts = mapOf(
                            "commandId" to commandIndex.toString(),
                            "operationIndex" to operationIndex.toString(),
                        ),
                    ),
                )
            }
            textCommandIds += commandIndex
            return@forEachIndexed
        }
        if (source is PreparedVisualSource.TextStroke) {
            if (visual.normalized !is NormalizedDrawCommand.FillPath ||
                visual.normalized.source.operation != "drawText.stroke-path" ||
                visual.preparedText != null ||
                visual.preparedImage != null
            ) {
                return PreparedImageVisuals.Refused(
                    imageCommandSourceDiagnostic(
                        message = "Prepared text stroke source lost its common path facts.",
                        facts = mapOf(
                            "commandId" to commandIndex.toString(),
                            "operationIndex" to operationIndex.toString(),
                        ),
                    ),
                )
            }
            textCommandIds += commandIndex
            pathStrokeCommandIds += commandIndex
            return@forEachIndexed
        }
        source as PreparedVisualSource.Image
        val command = visual.normalized as? NormalizedDrawCommand.DrawImageRect
            ?: return PreparedImageVisuals.Refused(
                imageCommandSourceDiagnostic(
                    message = "Prepared image source was associated with a non-image command.",
                    facts = mapOf(
                        "commandId" to commandIndex.toString(),
                        "operationIndex" to operationIndex.toString(),
                    ),
                ),
            )
        val prepared = visual.preparedImage
            ?: return PreparedImageVisuals.Refused(
                imageCommandSourceDiagnostic(
                    message = "Prepared image command lost its lowerer facts.",
                    facts = mapOf(
                        "commandId" to commandIndex.toString(),
                        "operationIndex" to operationIndex.toString(),
                    ),
                ),
            )
        val artifact = prepared.artifact
        val material = command.material as? GPUMaterialDescriptor.ImageDraw
            ?: return PreparedImageVisuals.Refused(
                diagnostic(
                    code = "invalid.surface.prepared.image-material",
                    message = "Prepared Surface image command lost its image material descriptor.",
                ),
            )
        if (command.imageSourceId != source.image.sourceId ||
            material.imageSourceId != source.image.sourceId ||
            command.pixelsWidth != artifact.width ||
            command.pixelsHeight != artifact.height ||
            command.pixelsRowBytes != artifact.pixelLayout.normalizedRgba8RowBytes ||
            command.pixelsGeneration != artifact.sourceGeneration ||
            command.pixelsContentHash != artifact.contentHash ||
            command.pixelsProvenance != "prepared-surface-artifact"
        ) {
            return PreparedImageVisuals.Refused(
                imageCommandSourceDiagnostic(
                    message = "Prepared image command does not match its exact lowerer artifact.",
                    facts = mapOf(
                        "commandId" to commandIndex.toString(),
                        "operationIndex" to operationIndex.toString(),
                    ),
                ),
            )
        }
        artifacts[commandIndex] = artifact
    }
    return PreparedImageVisuals.Ready(
        mapping.visualCommands.toList(),
        artifacts,
        textCommandIds.toSet(),
        pathStrokeCommandIds.toSet(),
    )
}

private fun DisplayOp.isCorePreparedVisual(): Boolean = when (this) {
    is DisplayOp.DrawColor,
    is DisplayOp.Clear,
    is DisplayOp.DrawPoint,
    is DisplayOp.DrawPoints,
    is DisplayOp.DrawRect,
    is DisplayOp.DrawRRect,
    is DisplayOp.DrawDRRect,
    is DisplayOp.DrawPath,
    -> true
    else -> false
}

private fun imageCommandSourceDiagnostic(
    message: String,
    facts: Map<String, String> = emptyMap(),
): GPUDiagnostic = diagnostic(
    code = "invalid.surface.prepared.image-command-source",
    message = message,
    facts = facts,
)

private fun GPUDiagnostic.atSurfaceBoundary(): GPUDiagnostic =
    if (code.value in GPUPreparedImageRefusalCodes.ALL) {
        copy(facts = facts + ("boundary" to "surface"))
    } else {
        this
    }

/**
 * The prepared target is currently a physical UNORM texture carrying the named
 * encoded-premul-sRGB convention. Opaque solids retain the same stored bytes as
 * the legacy sRGB attachment. Translucent solids need the legacy attachment's
 * linear-premul-to-sRGB store conversion, which this lane cannot express yet.
 */
private fun validateEncodedPremulSrgbOutput(
    request: GPUPreparedSurfaceFrameBuildRequest,
    mapping: GPUOpMapping,
    semantics: Map<Int, GPUDrawSemanticPayload>,
): GPUDiagnostic? {
    if (request.candidate.color.interpretation != GPUColorInterpretation.EncodedPremulSrgb) {
        return null
    }
    mapping.visualCommands.forEach { visual ->
        val commandId = visual.normalized.commandId.value
        val semantic = semantics[commandId] as? GPUDrawSemanticPayload.CorePrimitive ?: return@forEach
        if (semantic.premultipliedRgba[3] != 1f) {
            return diagnostic(
                code = "unsupported.surface.prepared.encoded-premul-srgb.translucent-solid",
                message = "Prepared Surface requires an explicit sRGB store conversion for translucent solids.",
                facts = mapOf("commandId" to commandId.toString()),
            )
        }
        val fractionalCoverageAuthority = semantic.fractionalCoverageAuthority(visual.clipExecutionPlan)
            ?: return@forEach
        return diagnostic(
            code = "unsupported.surface.prepared.encoded-premul-srgb.fractional-coverage",
            message = "Prepared Surface requires an explicit sRGB store conversion for fractional coverage.",
            facts = mapOf(
                "commandId" to commandId.toString(),
                "authority" to fractionalCoverageAuthority,
            ),
        )
    }
    return null
}

private fun GPUDrawSemanticPayload.CorePrimitive.fractionalCoverageAuthority(
    clipExecutionPlan: GPUClipExecutionPlan,
): String? = when (coverageMode) {
    GPUCorePrimitiveCoverageMode.ScalarAA -> "geometry.ScalarAA"
    GPUCorePrimitiveCoverageMode.StencilAA -> "geometry.StencilAA"
    GPUCorePrimitiveCoverageMode.FullOrScissor,
    GPUCorePrimitiveCoverageMode.Stencil1x,
    -> clipExecutionPlan.fractionalCoverageAuthority()
}

private fun GPUClipExecutionPlan.fractionalCoverageAuthority(): String? = when (this) {
    GPUClipExecutionPlan.NoClip,
    is GPUClipExecutionPlan.ScissorOnly,
    is GPUClipExecutionPlan.Refused,
    -> null
    is GPUClipExecutionPlan.AnalyticCoverage -> "clip.AnalyticCoverage.aa".takeIf { antiAlias }
    is GPUClipExecutionPlan.AnalyticIntersection ->
        "clip.AnalyticIntersection.aa".takeIf { elements.any { element -> element.antiAlias } }
    is GPUClipExecutionPlan.StencilCoverage -> "clip.StencilCoverage.msaa".takeIf { sampleCount > 1 }
    is GPUClipExecutionPlan.CoverageMask -> when {
        sampleCount > 1 -> "clip.CoverageMask.msaa"
        producers.any { producer -> producer.antiAlias } -> "clip.CoverageMask.aa"
        consumer.sampling != GPUClipMaskSampling.Nearest -> "clip.CoverageMask.filtered"
        else -> null
    }
}

private fun validateTargetBounds(request: GPUPreparedSurfaceFrameBuildRequest): GPUDiagnostic? =
    if (request.targetBounds.left != 0 || request.targetBounds.top != 0 ||
        request.targetBounds.width != request.targetFacts.width ||
        request.targetBounds.height != request.targetFacts.height
    ) {
        diagnostic(
            code = "invalid.surface.prepared.target-bounds",
            message = "Prepared Surface target facts and pixel bounds must share zero origin and size.",
            facts = mapOf(
                "factsSize" to "${request.targetFacts.width}x${request.targetFacts.height}",
                "bounds" to request.targetBounds.toString(),
            ),
        )
    } else {
        null
    }

private fun validateTargetFormat(request: GPUPreparedSurfaceFrameBuildRequest): GPUDiagnostic? =
    if (request.targetFacts.colorFormat != request.candidate.color.physicalFormat.value) {
        diagnostic(
            code = "invalid.surface.prepared.target-format",
            message = "Prepared Surface target format must match the admitted physical color format.",
            facts = mapOf(
                "targetFormat" to request.targetFacts.colorFormat,
                "candidateFormat" to request.candidate.color.physicalFormat.value,
            ),
        )
    } else {
        null
    }

private fun validateFrameIdentities(request: GPUPreparedSurfaceFrameBuildRequest): GPUDiagnostic? {
    val identities = listOf(
        request.target.value,
        request.recordingId.value,
        request.readbackRequestId.value,
    )
    return if (identities.distinct().size != identities.size) {
        diagnostic(
            code = "invalid.surface.prepared.frame-identities",
            message = "Prepared Surface target, recording, and readback identities must be unambiguous.",
        )
    } else {
        null
    }
}

/** Task 7 fail-closed preflight; Task 8 may replace this only with an authenticated native route. */
private fun preflightUnmaterializedPreparedVertices(
    recording: org.graphiks.kanvas.gpu.renderer.recording.GPURecording,
    semanticsByCommandId: Map<Int, GPUDrawSemanticPayload>,
): GPUDiagnostic? {
    val semanticOnlyCommandIds = recording.semanticOnlyDraws.map { draw -> draw.packet.commandIdValue }
    if (semanticOnlyCommandIds.isEmpty()) return null
    val verticesCommandIds = semanticsByCommandId.mapNotNull { (commandId, semantic) ->
        commandId.takeIf { semantic is GPUDrawSemanticPayload.Vertices }
    }
    if (
        semanticOnlyCommandIds.distinct().size != semanticOnlyCommandIds.size ||
        verticesCommandIds.distinct().size != verticesCommandIds.size ||
        semanticOnlyCommandIds.toSet() != verticesCommandIds.toSet()
    ) {
        return diagnostic(
            code = "invalid.surface.prepared.semantic-command-bijection",
            message = "Semantic-only recording evidence and prepared vertices payloads must be bijective.",
            facts = mapOf(
                "semanticOnlyCommandIds" to semanticOnlyCommandIds.joinToString(","),
                "verticesCommandIds" to verticesCommandIds.joinToString(","),
            ),
        )
    }
    return diagnostic(
        code = PREPARED_VERTICES_UNMATERIALIZED_PREFLIGHT_REFUSAL_CODE,
        message = "Prepared vertices semantics have no executable native materialization route.",
        facts = mapOf(
            "semanticOnlyCommandIds" to semanticOnlyCommandIds.joinToString(","),
            "verticesCommandIds" to verticesCommandIds.joinToString(","),
            "state" to "prepared_vertices_unmaterialized",
        ),
    )
}

private fun GPUCorePrimitiveSemanticGatherResult.Refused.toDiagnostic(): GPUDiagnostic = GPUDiagnostic(
    code = GPUDiagnosticCode(code),
    domain = GPUDiagnosticDomain.Recording,
    severity = GPUDiagnosticSeverity.Error,
    message = message,
    facts = facts,
)

private fun diagnostic(
    code: String,
    message: String,
    facts: Map<String, String> = emptyMap(),
): GPUDiagnostic = GPUDiagnostic(
    code = GPUDiagnosticCode(code),
    domain = GPUDiagnosticDomain.Recording,
    severity = GPUDiagnosticSeverity.Error,
    message = message,
    facts = facts,
)
