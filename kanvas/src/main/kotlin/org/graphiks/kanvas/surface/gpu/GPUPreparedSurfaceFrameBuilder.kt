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
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameID
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackRequestID
import org.graphiks.kanvas.gpu.renderer.recording.GPURecorder
import org.graphiks.kanvas.gpu.renderer.recording.GPURecordingID
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskList
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveCoverageMode
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUPreparedImageRefusalCodes
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
            mapping.preparedRefusal?.let { refusal ->
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
            val preparedImages = collectPreparedImageVisuals(
                mapping = mapping,
                operations = request.candidate.operations,
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
                    targetFormat = GPUColorFormat(request.targetFacts.colorFormat),
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

private sealed interface PreparedVisualSource {
    val operationIndex: Int

    data class Image(
        override val operationIndex: Int,
        val image: org.graphiks.kanvas.image.Image,
    ) : PreparedVisualSource

    data class Core(
        override val operationIndex: Int,
    ) : PreparedVisualSource
}

private fun collectPreparedImageVisuals(
    mapping: GPUOpMapping,
    operations: List<DisplayOp>,
): PreparedImageVisuals {
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
    return PreparedImageVisuals.Ready(mapping.visualCommands.toList(), artifacts)
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
