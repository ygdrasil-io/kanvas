package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionPlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipMaskSampling
import org.graphiks.kanvas.gpu.renderer.color.GPUColorInterpretation
import org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUDrawCommandID
import org.graphiks.kanvas.gpu.renderer.commands.GPUFrameProvenance
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
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameID
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackRequestID
import org.graphiks.kanvas.gpu.renderer.recording.GPURecorder
import org.graphiks.kanvas.gpu.renderer.recording.GPURecordingID
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskList
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveCoverageMode
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.passes.GPUCoverageConsumption
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageArtifactResult
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageRefusalCodes
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageUploadArtifact
import org.graphiks.kanvas.canvas.DisplayOp

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
)

internal sealed interface GPUPreparedSurfaceFrameBuildResult {
    data class Ready(
        val taskList: GPUTaskList,
        val readbackRequestId: GPUReadbackRequestID,
        val visualOperationCount: Int,
        val stateEventCount: Int,
    ) : GPUPreparedSurfaceFrameBuildResult

    data class Refused(val diagnostic: GPUDiagnostic) : GPUPreparedSurfaceFrameBuildResult
}

/** Builds one handle-free prepared Surface frame without creating or submitting GPU resources. */
internal object GPUPreparedSurfaceFrameBuilder {
    fun build(
        request: GPUPreparedSurfaceFrameBuildRequest,
    ): GPUPreparedSurfaceFrameBuildResult {
        validateTargetBounds(request)?.let { return GPUPreparedSurfaceFrameBuildResult.Refused(it) }
        validateTargetFormat(request)?.let { return GPUPreparedSurfaceFrameBuildResult.Refused(it) }
        validateFrameIdentities(request)?.let { return GPUPreparedSurfaceFrameBuildResult.Refused(it) }

        return try {
            val mapping = GPUOpMapper.mapOperations(
                operations = request.candidate.operations,
                target = request.targetFacts,
                config = request.candidate.config,
                capabilities = request.capabilities,
            )
            val preparedImages = prepareImageVisuals(
                mapping = mapping,
                operations = request.candidate.operations,
                target = request.targetFacts,
            )
            if (preparedImages is PreparedImageVisuals.Refused) {
                return GPUPreparedSurfaceFrameBuildResult.Refused(preparedImages.diagnostic)
            }
            preparedImages as PreparedImageVisuals.Ready
            val preparedMapping = mapping.copy(visualCommands = preparedImages.visualCommands)
            val recorder = GPURecorder(
                recordingId = request.recordingId,
                frameId = request.frameId,
                capabilities = request.capabilities,
                deviceGeneration = request.deviceGeneration,
            )
            preparedMapping.visualCommands.forEach { visual -> recorder.record(visual.normalized) }
            val recording = recorder.close()
            recording.taskList.diagnostics.firstOrNull { diagnostic -> diagnostic.isTerminal }?.let {
                return GPUPreparedSurfaceFrameBuildResult.Refused(it.atSurfaceBoundary())
            }
            val semantics = when (val gathered = GPUPreparedSurfaceSemanticBuilder.gather(
                visualCommands = preparedMapping.visualCommands,
                recording = recording,
                targetBounds = request.targetBounds,
                imageArtifactsByCommandId = preparedImages.artifactsByCommandId,
            )) {
                is GPUPreparedSurfaceSemanticGatherResult.Gathered -> gathered.semanticsByCommandId
                is GPUPreparedSurfaceSemanticGatherResult.Refused ->
                    return GPUPreparedSurfaceFrameBuildResult.Refused(gathered.diagnostic)
            }
            when (val prepared = GPUPreparedSurfaceFrameTaskListBuilder().build(
                GPUPreparedSurfaceFrameRequest(
                    baseTaskList = recording.taskList,
                    capabilities = request.capabilities,
                    target = request.target,
                    targetBounds = request.targetBounds,
                    semanticsByCommandId = semantics,
                    readbackRequestId = request.readbackRequestId,
                ),
            )) {
                is GPUPreparedSurfaceFrameResult.Recorded -> {
                    validateEncodedPremulSrgbOutput(request, preparedMapping, semantics)?.let {
                        return GPUPreparedSurfaceFrameBuildResult.Refused(it)
                    }
                    GPUPreparedSurfaceFrameBuildResult.Ready(
                        taskList = prepared.taskList,
                        readbackRequestId = request.readbackRequestId,
                        visualOperationCount = preparedMapping.visualCommands.size,
                        stateEventCount = mapping.stateEvents.count { event ->
                            event.kind == GPUFramePathStateKind.Transform ||
                                event.kind == GPUFramePathStateKind.Clip ||
                                event.kind == GPUFramePathStateKind.Annotation
                        },
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

private sealed interface PreparedImageVisuals {
    data class Ready(
        val visualCommands: List<GPUFramePathVisualCommand>,
        val artifactsByCommandId: Map<Int, GPUPreparedImageUploadArtifact>,
    ) : PreparedImageVisuals

    data class Refused(val diagnostic: GPUDiagnostic) : PreparedImageVisuals
}

internal data class GPUPreparedImageCommandSource(
    val commandId: Int,
    val operationIndex: Int,
    val operation: DisplayOp.DrawImage,
)

private data class GPUPreparedVisualAssociation(
    val visual: GPUFramePathVisualCommand,
    val imageSource: GPUPreparedImageCommandSource?,
)

private fun GPUPreparedImageCommandSource.matchesExactOperation(
    command: NormalizedDrawCommand.DrawImageRect,
    operations: List<DisplayOp>,
): Boolean {
    val indexedOperation = operations.getOrNull(operationIndex)
    return commandId == command.commandId.value &&
        indexedOperation === operation &&
        indexedOperation is DisplayOp.DrawImage &&
        indexedOperation.image === operation.image
}

private fun prepareImageVisuals(
    mapping: GPUOpMapping,
    operations: List<DisplayOp>,
    target: GPUTargetFacts,
): PreparedImageVisuals {
    val orderedAssociations = mutableListOf<GPUPreparedVisualAssociation>()
    val mappedCoreVisuals = mapping.visualCommands.iterator()
    var provenance = GPUFrameProvenance.None
    operations.forEachIndexed { operationIndex, operation ->
        when (operation) {
            is DisplayOp.Annotation -> {
                if (operation.key == GPU_FRAME_PROVENANCE_ANNOTATION_KEY) {
                    GPUFrameProvenance.fromAnnotationValue(operation.value)?.let { provenance = it }
                }
            }
            is DisplayOp.SetTransform,
            is DisplayOp.SetClip,
            is DisplayOp.FlushAndSnapshot,
            -> Unit
            is DisplayOp.DrawImage -> {
                val commandId = orderedAssociations.size
                val rawNormalized = operation.toImageRectCommand(
                    cmdId = GPUDrawCommandID(commandId),
                    target = target,
                )
                val normalized = rawNormalized.copy(
                    ordering = rawNormalized.ordering.copy(paintOrder = commandId),
                    source = rawNormalized.source.copy(frameProvenance = provenance),
                )
                val clipCoverage = if (normalized.clip.coverageRequest == null) {
                    org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoveragePlan.NoClip
                } else {
                    org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoveragePlan.Refused(
                        "unsupported.clip.prepared_image_execution_unclassified",
                    )
                }
                val clipExecution = if (clipCoverage ==
                    org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoveragePlan.NoClip
                ) {
                    GPUClipExecutionPlan.NoClip
                } else {
                    GPUClipExecutionPlan.Refused(
                        code = "unsupported.clip.prepared_image_execution_unclassified",
                        message = "Prepared image clip execution must be classified before recording.",
                    )
                }
                orderedAssociations += GPUPreparedVisualAssociation(
                    visual = GPUFramePathVisualCommand(
                        normalized = normalized,
                        targetSpaceBounds = normalized.bounds,
                        geometryCoverage = GPUCoverageConsumption.FullOrScissor,
                        clipCoverage = clipCoverage,
                        clipExecutionPlan = clipExecution,
                        blendPlan = normalized.blend.canonicalBlendPlan(),
                        provenance = provenance,
                    ),
                    imageSource = GPUPreparedImageCommandSource(
                        commandId = commandId,
                        operationIndex = operationIndex,
                        operation = operation,
                    ),
                )
            }
            is DisplayOp.DrawColor,
            is DisplayOp.Clear,
            is DisplayOp.DrawPoint,
            is DisplayOp.DrawPoints,
            is DisplayOp.DrawRect,
            is DisplayOp.DrawRRect,
            is DisplayOp.DrawDRRect,
            is DisplayOp.DrawPath,
            -> {
                if (!mappedCoreVisuals.hasNext()) {
                    return PreparedImageVisuals.Refused(
                        imageCommandSourceDiagnostic(
                            message = "Prepared Surface operation lost its normalized visual command.",
                            facts = mapOf("operationIndex" to operationIndex.toString()),
                        ),
                    )
                }
                orderedAssociations += GPUPreparedVisualAssociation(
                    visual = mappedCoreVisuals.next().withPreparedCommandIdentity(
                        commandId = orderedAssociations.size,
                        provenance = provenance,
                    ),
                    imageSource = null,
                )
            }
            else -> Unit
        }
    }
    if (mappedCoreVisuals.hasNext()) {
        return PreparedImageVisuals.Refused(
            imageCommandSourceDiagnostic(
                message = "Prepared Surface mapping retained an unassociated visual command.",
            ),
        )
    }
    val artifacts = linkedMapOf<Int, GPUPreparedImageUploadArtifact>()
    val visuals = mutableListOf<GPUFramePathVisualCommand>()
    for ((visual, source) in orderedAssociations) {
        val command = visual.normalized as? NormalizedDrawCommand.DrawImageRect
        if (command == null) {
            if (source != null) {
                return PreparedImageVisuals.Refused(
                    imageCommandSourceDiagnostic(
                        message = "Prepared image source was associated with a non-image command.",
                        facts = mapOf(
                            "commandId" to source.commandId.toString(),
                            "operationIndex" to source.operationIndex.toString(),
                        ),
                    ),
                )
            }
            visuals += visual
            continue
        }
        if (source == null || !source.matchesExactOperation(command, operations)) {
            return PreparedImageVisuals.Refused(
                diagnostic(
                    code = "invalid.surface.prepared.image-command-source",
                    message = "Prepared image command requires its exact indexed Surface operation.",
                    facts = mapOf(
                        "commandId" to command.commandId.value.toString(),
                        "operationIndex" to (source?.operationIndex?.toString() ?: "missing"),
                        "commandImageSourceId" to command.imageSourceId,
                        "operationImageSourceId" to (source?.operation?.image?.sourceId ?: "missing"),
                    ),
                ),
            )
        }
        val artifact = when (val prepared = GPUPreparedSurfaceImageSource.prepare(source.operation.image)) {
            is GPUPreparedImageArtifactResult.Ready -> prepared.artifact
            is GPUPreparedImageArtifactResult.Refused -> return PreparedImageVisuals.Refused(
                diagnostic(
                    code = prepared.code,
                    message = "Surface image source could not produce an exact prepared artifact.",
                    facts = prepared.facts + mapOf(
                        "boundary" to "surface",
                        "commandId" to command.commandId.value.toString(),
                        "operationIndex" to source.operationIndex.toString(),
                    ),
                ),
            )
        }
        val material = command.material as? GPUMaterialDescriptor.ImageDraw
            ?: return PreparedImageVisuals.Refused(
                diagnostic(
                    code = "invalid.surface.prepared.image-material",
                    message = "Prepared Surface image command lost its image material descriptor.",
                ),
            )
        val commandId = command.commandId.value
        artifacts[commandId] = artifact
        visuals += visual.copy(
            normalized = command.copy(
                material = material.copy(rgbaPixels = artifact.tightRgba8BytesForUpload()),
                pixelsWidth = artifact.width,
                pixelsHeight = artifact.height,
                pixelsFormat = "RGBA8Unorm",
                pixelsRowBytes = artifact.pixelLayout.normalizedRgba8RowBytes,
                pixelsGeneration = artifact.sourceGeneration,
                pixelsContentHash = artifact.contentHash,
                pixelsProvenance = "prepared-surface-artifact",
            ),
        )
    }
    return PreparedImageVisuals.Ready(visuals, artifacts)
}

private fun GPUFramePathVisualCommand.withPreparedCommandIdentity(
    commandId: Int,
    provenance: GPUFrameProvenance,
): GPUFramePathVisualCommand {
    val identity = GPUDrawCommandID(commandId)
    val command = when (val current = normalized) {
        is NormalizedDrawCommand.FillRect -> current.copy(
            commandId = identity,
            ordering = current.ordering.copy(paintOrder = commandId),
            source = current.source.copy(frameProvenance = provenance),
        )
        is NormalizedDrawCommand.FillRRect -> current.copy(
            commandId = identity,
            ordering = current.ordering.copy(paintOrder = commandId),
            source = current.source.copy(frameProvenance = provenance),
        )
        is NormalizedDrawCommand.FillPath -> current.copy(
            commandId = identity,
            ordering = current.ordering.copy(paintOrder = commandId),
            source = current.source.copy(frameProvenance = provenance),
        )
        else -> error("Prepared Surface core mapping produced an unsupported command family.")
    }
    return copy(normalized = command, provenance = provenance)
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
