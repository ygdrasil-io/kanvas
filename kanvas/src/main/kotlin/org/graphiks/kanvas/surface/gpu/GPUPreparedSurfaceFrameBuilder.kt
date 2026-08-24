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
import org.graphiks.kanvas.gpu.renderer.layers.GPUPreflightCapabilities
import org.graphiks.kanvas.gpu.renderer.layers.GPUPreparedCompositeEntry
import org.graphiks.kanvas.gpu.renderer.layers.GPUPreparedCompositeScopeKind
import org.graphiks.kanvas.gpu.renderer.layers.GPUPreparedCompositeScopeState
import org.graphiks.kanvas.gpu.renderer.layers.GPUPreparedRectSnapshot
import org.graphiks.kanvas.types.Color
import org.graphiks.math.matrix.Matrix3x3F32
import org.graphiks.math.geometry.Point2F32
import org.graphiks.kanvas.gpu.renderer.recording.GPUPreparedLayerChildrenSpec
import org.graphiks.kanvas.gpu.renderer.recording.GPUPreparedSurfaceFrameRequest
import org.graphiks.kanvas.gpu.renderer.recording.GPUPreparedSaveLayerFrameHandling
import org.graphiks.kanvas.gpu.renderer.recording.GPUPreparedSurfaceFrameResult
import org.graphiks.kanvas.gpu.renderer.recording.GPUPreparedSurfaceFrameTaskListBuilder
import org.graphiks.kanvas.gpu.renderer.resources.GPUTargetPreparationContext
import org.graphiks.kanvas.gpu.renderer.recording.GPUDestinationSnapshotOperation
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameID
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackRequestID
import org.graphiks.kanvas.gpu.renderer.recording.GPURecorder
import org.graphiks.kanvas.gpu.renderer.recording.GPURecordingID
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskList
import org.graphiks.kanvas.gpu.renderer.recording.GPUTask
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveCoverageMode
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlan
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassCommand
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUPreparedImageRefusalCodes
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.glyph.gpu.GPUTextArtifactGeneration
import org.graphiks.kanvas.paint.BlendMode

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

/** Authenticated historical route facts carried from the exact prepared graph. */
internal data class GPUPreparedSurfaceDestinationReadEvidence(
    val commandId: Int,
    val operationFamily: String,
    val sourceLabel: String,
    val snapshotLabel: String,
    val modeLabel: String,
    val clipStrategy: String,
    val action: String,
) {
    init {
        require(commandId >= 0)
        require(operationFamily.isNotBlank())
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
        val compositeCommandCount: Int = 0,
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
            // Destination-reading frames whose first visual op is the reader fuse the
            // scene-target clear into that reader's render pass, while the destination
            // snapshot copy is ordered before the pass. On a fresh target the copy
            // accidentally captures the cleared state; on a retained prepared session
            // target it captures the previous frame's pixels. Synthesize
            // the frame's implicit clear as an explicit leading op so the copy always
            // captures the cleared target.
            val operations = request.candidate.operations.withSynthesizedDstReadSceneClear(
                interpretation = request.candidate.color.interpretation,
            )
            val hasCompositeOps = operations.any { operation ->
                operation is DisplayOp.BeginLayer ||
                    operation is DisplayOp.EndLayer ||
                    operation is DisplayOp.DrawPicture
            }
            // Composite frames are handled fail-closed BEFORE the text/vertices/image
            // preparers: those preparers do not understand layer structure (the mapper
            // records BeginLayer/EndLayer into the legacy dump and flat-renders layer
            // children), so a composite frame must either materialize through the real
            // saveLayer pipeline or refuse terminally — never silently fall back.
            // When composite commands are scheduled, the layer children are
            // elided from the flat pipeline (they render once into the isolated layer
            // target via RenderLayerChildren); mixed topologies whose coverage is
            // ambiguous (a DrawPicture the composite route cannot materialize) refuse
            // with unsupported.surface.prepared.mixed-composite-topology.
            val compositeHandling = if (hasCompositeOps) {
                prepareCompositeFrameHandling(
                    operations = operations,
                    capabilities = request.capabilities,
                    taskListBuilder = taskListBuilder,
                    context = contextFor(request),
                )
            } else {
                null
            }
            if (compositeHandling is CompositeFrameHandling.Refused) {
                return GPUPreparedSurfaceFrameBuildResult.Refused(
                    diagnostic(
                        code = compositeHandling.code,
                        message = "Prepared Surface composite could not be lowered.",
                        facts = compositeHandling.facts + mapOf(
                            "boundary" to "surface.composite",
                            "operationIndex" to
                                (compositeHandling.operationIndex?.toString() ?: "unknown"),
                        ),
                    ),
                )
            }
            // Composite scheduling evidence is only authoritative while the saveLayer
            // commands actually carry the frame's render work.
            val compositeScheduling = (compositeHandling as? CompositeFrameHandling.Ready)
                ?.takeIf { ready -> ready.handling.commands.isNotEmpty() }
            val elidedCompositeChildOperationIndices = compositeScheduling?.let {
                compositeCoveredOperationIndices(operations)
            } ?: emptySet()
            // A covered DrawPicture never reaches this elision: the capturer refuses unpainted
            // pictures inside saveLayer scopes (unsupported.composite.operation) and painted
            // pictures refuse below via compositeTopologyRefusal, so the flat mapper never
            // sees a picture in a covered range. The DrawPicture filter below is a defensive
            // guard only — picture-expanded children produce no flat commandIds
            // (commandIdsByOperationIndex records only top-level mapped ops), so they could
            // never ride the composite commands. Covered ops of EMPTY layers (no children or
            // fully offscreen device bounds) are elided too: the frame's uniform slab must
            // exactly cover the accepted packets, so children that never render into an
            // isolated target cannot ride the scene.
            val flatElidedOperationIndices = (
                elidedCompositeChildOperationIndices.filter { index ->
                    operations[index] is DisplayOp.DrawPicture
                }
                ).toSet() + compositeScheduling?.let { ready ->
                    emptyLayerCoveredOperationIndices(
                        ready = ready,
                        operations = operations,
                        coveredOperationIndices = elidedCompositeChildOperationIndices,
                        targetBounds = request.targetBounds,
                    )
                }.orEmpty()
            if (compositeHandling is CompositeFrameHandling.Ready) {
                compositeTopologyRefusal(
                    operations = operations,
                    coveredOperationIndices = elidedCompositeChildOperationIndices,
                )?.let { refusal ->
                    return GPUPreparedSurfaceFrameBuildResult.Refused(refusal)
                }
            }
            val hasPreparedText = operations.any { operation ->
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
                operations = operations,
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
                operations = operations,
                target = request.targetFacts,
                config = request.candidate.config,
                capabilities = request.capabilities,
                preparedTextInventory = textPreparation.inventory,
                mappingBoundary = flatElidedOperationIndices.let { elided ->
                    GPUPreparedFrameMappingBoundary { operations, target, config, capabilities,
                        textInventory, verticesInventory ->
                        GPUOpMapper.mapOperations(
                            operations = operations,
                            target = target,
                            config = config,
                            capabilities = capabilities,
                            preparedTextInventory = textInventory,
                            preparedVerticesInventory = verticesInventory,
                            elidedOperationIndices = elided,
                        )
                    }
                },
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
                operations = operations,
                inventory = textPreparation.inventory,
                elidedOperationIndices = flatElidedOperationIndices,
            )
            if (preparedImages is PreparedImageVisuals.Refused) {
                return GPUPreparedSurfaceFrameBuildResult.Refused(preparedImages.diagnostic)
            }
            preparedImages as PreparedImageVisuals.Ready
            val preparedMapping = mapping.copy(visualCommands = preparedImages.visualCommands)
            val childrenByTargetLabel = compositeScheduling?.let { ready ->
                childrenByTargetLabel(
                    ready = ready,
                    mapping = mapping,
                    coveredOperationIndices = elidedCompositeChildOperationIndices,
                    targetBounds = request.targetBounds,
                )
            } ?: emptyMap()
            val layerChildrenCommandIds = childrenByTargetLabel.values
                .flatMap(GPUPreparedLayerChildrenSpec::commandIds).toSet()
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
            if (
                preparedMapping.visualCommands.isEmpty() &&
                verticesInventory.mappedCommands.isEmpty() &&
                compositeScheduling == null
            ) {
                val textOperationIndices = operations.withIndex()
                    .filter { indexed -> indexed.value is DisplayOp.DrawText }
                    .mapTo(linkedSetOf()) { indexed -> indexed.index }
                val accountedTextOperationIndices = (
                    textPreparation.inventory.elidedTextOperationIndices +
                        mapping.culledTextOperationIndices
                    ).toSet()
                val hasOnlyTextAndStateOperations = operations.all { operation ->
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
                    maskBlurIntermediateBudgetBytes = request.candidate.config.maxMaskBlurIntermediateBytes.toLong(),
                ),
                allowEmptyBaseTaskList = compositeScheduling != null,
            )) {
                is GPUPreparedSurfaceFrameResult.Recorded -> {
                    validateEncodedPremulSrgbOutput(request, preparedMapping, semantics)?.let {
                        return GPUPreparedSurfaceFrameBuildResult.Refused(it)
                    }
                    val mergedTaskList = (compositeHandling as? CompositeFrameHandling.Ready)
                        ?.let { ready ->
                            val emptyLayerTargetLabels = childrenByTargetLabel
                                .filterValues { spec -> spec.bounds.isEmpty }
                                .keys + childrenByTargetLabel
                                .filterKeys { targetLabel ->
                                    childrenByTargetLabel.getValue(targetLabel).commandIds.isEmpty() &&
                                        !ready.capture.scopes.values.any { scope ->
                                            scope.id.value ==
                                                targetLabel.removePrefix("layer-target:") &&
                                                scope.entries.any { entry ->
                                                    entry is GPUPreparedCompositeEntry.Draw
                                                }
                                        }
                                }
                                .keys
                            taskListBuilder.mergeCompositeCommands(
                                taskList = prepared.taskList,
                                commands = if (emptyLayerTargetLabels.isEmpty()) {
                                    ready.handling.commands
                                } else {
                                    dropEmptyLayerCommands(
                                        ready.handling.commands,
                                        emptyLayerTargetLabels,
                                    )
                                },
                            )
                        }
                        ?: prepared.taskList
                    val splitTaskList = if (childrenByTargetLabel.isNotEmpty()) {
                        taskListBuilder.splitCompositeChildrenRenders(
                            taskList = mergedTaskList,
                            childrenPacketsByTargetLabel = childrenByTargetLabel,
                        )
                    } else {
                        mergedTaskList
                    }
                    val destinationReadEvidence = splitTaskList
                        .authenticatedDestinationReadEvidence(
                            semantics,
                            destinationReadOperationFamily(
                                operations,
                                mapping.commandIdsByOperationIndex,
                            ),
                        )
                    GPUPreparedSurfaceFrameBuildResult.Ready(
                        taskList = splitTaskList,
                        readbackRequestId = request.readbackRequestId,
                        visualOperationCount = preparedMapping.visualCommands.count { visual ->
                            visual.normalized.commandId.value !in layerChildrenCommandIds
                        },
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
                        compositeCommandCount = mergedTaskList.compositeCommands.size,
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

/**
 * Destination-reading prepared frames whose first visual op IS the reader fuse the scene-target
 * clear into that reader's render pass (`loadOp = "clear"` on the first scene render), while the
 * destination snapshot copy is ordered before that pass. On a fresh target the copy accidentally
 * captures the cleared state; on a retained prepared-session target it captures the
 * previous frame's pixels. Synthesize the frame's implicit clear as an explicit leading
 * [DisplayOp.Clear] so the copy always captures the cleared target, on fresh and retained sessions
 * alike.
 *
 * Evidence contract of the synthesized clear: the [DisplayOp.Clear] is a REAL op in the visual
 * stream. It maps to one full-target solid command, so the built frame reports
 * `visualOperationCount` N+1 (N recorded visuals), the native evidence reports one extra render
 * pass, draw, and pipeline bind, and the executor's `opsDispatched` is N+1 by design — the clear
 * IS a dispatched op. The synthesis never flips a builder-NoOp frame to Ready: it fires only for
 * frames whose first visual op is a non-empty destination-reading text op (empty-glyph text ops
 * are skipped when locating the first visual, and empty-glyph-only frames stay NoOp).
 *
 * Known non-synthesized shapes (documented, not handled): elidable non-empty text first ops whose
 * blend is outside [PREPARED_DST_READ_TEXT_BLEND_MODES] (e.g. an opaque DST_IN text elides to a
 * no-op while a later dst-read text fuses the clear). Such frames keep the retained-target
 * behavior above; no current test shape exercises them. LCD (subpixel) text cannot reach this
 * lane: its blend plan is always `ShaderBlendWithDstRead` (GPUSubpixelLcd.lcdBlendPlan), which the
 * prepared-surface lane refuses for TextA8 at `invalid.preflight.text.blend`
 * (GPUPreparedSurfaceFrameTaskListBuilder), so no LCD frame ever renders through the copy lane.
 */
private fun List<DisplayOp>.withSynthesizedDstReadSceneClear(
    interpretation: GPUColorInterpretation,
): List<DisplayOp> {
    // EncodedPremulSrgb targets refuse translucent solids (unsupported.surface.prepared.
    // encoded-premul-srgb.translucent-solid); those frames keep today's fused-clear behavior.
    if (interpretation == GPUColorInterpretation.EncodedPremulSrgb) return this
    // Composite frames own their background through the saveLayer pipeline.
    if (any { operation ->
            operation is DisplayOp.BeginLayer ||
                operation is DisplayOp.EndLayer ||
                operation is DisplayOp.DrawPicture
        }
    ) {
        return this
    }
    // Locate the first visual op that will actually paint: empty-glyph text ops are elided by
    // the prepared-text inventory, so they do not own the fused scene clear and must not block
    // the synthesis for a later destination-reading text op.
    val firstVisual = firstOrNull { visual ->
        visual.isVisualDraw() &&
            (visual !is DisplayOp.DrawText || visual.blob.glyphRuns.any { run -> run.glyphs.isNotEmpty() })
    } ?: return this
    val text = firstVisual as? DisplayOp.DrawText ?: return this
    if (text.paint.blendMode !in PREPARED_DST_READ_TEXT_BLEND_MODES) return this
    return listOf(
        DisplayOp.Clear(Color.TRANSPARENT),
    ) + this
}

private fun DisplayOp.isVisualDraw(): Boolean = when (this) {
    is DisplayOp.DrawRect,
    is DisplayOp.DrawRRect,
    is DisplayOp.DrawPath,
    is DisplayOp.DrawImage,
    is DisplayOp.DrawText,
    is DisplayOp.DrawVertices,
    is DisplayOp.DrawColor,
    is DisplayOp.Clear,
    is DisplayOp.DrawPoint,
    is DisplayOp.DrawPoints,
    is DisplayOp.DrawDRRect,
    is DisplayOp.DrawImageNine,
    is DisplayOp.DrawImageLattice,
    is DisplayOp.DrawMesh,
    is DisplayOp.DrawAtlas,
    -> true
    else -> false
}

/**
 * Text blend modes whose canonical scalar-coverage blend plan samples the destination texture
 * (`ShaderBlendWithDstRead`), mirroring GPUBlendPlanning's scalar-coverage fallback branch for
 * text semantics. A leading destination-reading draw therefore sees the frame's cleared state.
 *
 * The set is pinned by `GPUPreparedSurfaceFrameBuilderTextTest` against the planner itself
 * (`GPUBlendPlanner.plan` with scalar coverage and a non-proven-opaque source), so drift fails
 * loudly. Over-approximation is intentional: for a PROVEN-OPAQUE source alpha the planner
 * downgrades SRC/SRC_IN/SRC_OUT/DST_ATOP to fixed-function blends (no destination read), so the
 * mirror may synthesize a clear for an opaque non-reading text op — harmless, because the clear
 * rect writes transparent and the fused-clear render would have cleared the target anyway.
 * LCD (subpixel) coverage reads the destination regardless of mode, but LCD text never reaches
 * this lane (see [withSynthesizedDstReadSceneClear]); the mirror therefore does not model it.
 */
internal val PREPARED_DST_READ_TEXT_BLEND_MODES: Set<BlendMode> = setOf(
    BlendMode.SRC,
    BlendMode.SRC_IN,
    BlendMode.SRC_OUT,
    BlendMode.DST_ATOP,
    BlendMode.PLUS,
    BlendMode.MULTIPLY,
    BlendMode.OVERLAY,
    BlendMode.DARKEN,
    BlendMode.LIGHTEN,
    BlendMode.COLOR_DODGE,
    BlendMode.COLOR_BURN,
    BlendMode.HARD_LIGHT,
    BlendMode.SOFT_LIGHT,
    BlendMode.DIFFERENCE,
    BlendMode.EXCLUSION,
    BlendMode.HUE,
    BlendMode.SATURATION,
    BlendMode.COLOR,
    BlendMode.LUMINOSITY,
)

private fun GPUTaskList.authenticatedDestinationReadEvidence(
    semantics: Map<Int, GPUDrawSemanticPayload>,
    operationFamilyByCommandId: Map<Int, String>,
): List<GPUPreparedSurfaceDestinationReadEvidence> {
    val rendersByTaskId = tasks.filterIsInstance<GPUTask.Render>()
        .associateBy(GPUTask.Render::taskId)
    return tasks.filterIsInstance<GPUTask.DestinationSnapshots>()
        .flatMap { task -> task.payload.operations }
        .map { operation ->
            val copy = operation as? GPUDestinationSnapshotOperation.TextureCopy
                ?: error("Prepared destination evidence requires a texture copy")
            val consumer = copy.consumers.single()
            val commandId = consumer.commandId.value
            require(
                semantics[commandId] is GPUDrawSemanticPayload.ColorGlyph ||
                    semantics[commandId] is GPUDrawSemanticPayload.CorePrimitive ||
                    semantics[commandId] is GPUDrawSemanticPayload.MaskBlur,
            )
            val render = rendersByTaskId.getValue(consumer.renderTaskId)
            val packet = render.drawPackets.single { candidate ->
                candidate.packetId == consumer.packetId &&
                    candidate.commandIdValue == commandId
            }
            val blend = packet.blendPlan as? GPUBlendPlan.ShaderBlendWithDstRead
                ?: error("Prepared destination evidence requires shader blending")
            GPUPreparedSurfaceDestinationReadEvidence(
                commandId = commandId,
                operationFamily = operationFamilyByCommandId[commandId]
                    ?: error("Prepared destination evidence requires one exact source operation"),
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
 * Maps every recorded command id back to the public DisplayOp family that produced it, so the
 * destination-read route evidence names the operation the way the legacy renderer does
 * (`DrawRect:<commandId>`).
 */
private fun destinationReadOperationFamily(
    operations: List<DisplayOp>,
    commandIdsByOperationIndex: Map<Int, Set<Int>>,
): Map<Int, String> = buildMap {
    commandIdsByOperationIndex.forEach { (operationIndex, commandIds) ->
        val family = operations.getOrNull(operationIndex)?.javaClass?.simpleName
            ?: return@forEach
        commandIds.forEach { commandId -> put(commandId, family) }
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

private sealed interface CompositeFrameHandling {
    data class Ready(
        val handling: GPUPreparedSaveLayerFrameHandling.Ready,
        val capture: GPUPreparedCompositeCapture,
    ) : CompositeFrameHandling

    data class Refused(
        val code: String,
        val operationIndex: Int?,
        val facts: Map<String, String>,
    ) : CompositeFrameHandling
}

/**
 * Drops the per-layer command triplets of empty bounded saveLayers.
 *
 * An empty bounded layer has no children to isolate, so its PrepareLayerTarget /
 * RenderLayerChildren / CompositeLayer stream must not ride the frame: the prepared-surface
 * preflight requires every declared layer target to retain an exact children render, and a
 * no-op layer composites nothing (Skia leaves the parent untouched).
 */
private fun dropEmptyLayerCommands(
    commands: List<GPUPassCommand>,
    emptyTargetLabels: Set<String>,
): List<GPUPassCommand> {
    if (emptyTargetLabels.isEmpty()) return commands
    val kept = mutableListOf<GPUPassCommand>()
    var skippingTargetLabel: String? = null
    commands.forEach { command ->
        when (command) {
            is GPUPassCommand.PrepareLayerTarget -> {
                if (command.targetLabel in emptyTargetLabels) {
                    skippingTargetLabel = command.targetLabel
                } else {
                    kept += command
                }
            }
            is GPUPassCommand.CompositeLayer -> {
                if (skippingTargetLabel != null && command.sourceLabel == skippingTargetLabel) {
                    skippingTargetLabel = null
                } else {
                    kept += command
                }
            }
            else -> if (skippingTargetLabel == null) kept += command
        }
    }
    return kept
}

/**
 * Layer-target labels → the children split evidence for a composite frame.
 *
 * Children are core operations inside saveLayer scopes (the capture refuses image/text/
 * vertices children with `unsupported.composite.operation`), so each covered operation
 * inside a layer's original-operation range resolves to exactly the mapper commandIds its
 * source operation produced. The capture's expanded-operation indices are sublist-relative
 * (the capturer restarts indexing per nested list), so the layer's original op range comes
 * from the scope's save/restore operation indices — exact for top-level layers. Nested
 * layers are refused earlier with `unsupported.prepared-surface.layer-nesting` (the
 * preflight's documented contract), so this range mapping only ever sees flat topologies.
 * The label convention matches [GPUSaveLayerIsolatedTargetPlanner]'s isolated target labels
 * (`layer-target:<scopeId>`), which the merged composite commands declare and the
 * prepared-surface native preflight matches against the children renders.
 */
private fun childrenByTargetLabel(
    ready: CompositeFrameHandling.Ready,
    mapping: GPUOpMapping,
    coveredOperationIndices: Set<Int>,
    targetBounds: GPUPixelBounds,
): Map<String, GPUPreparedLayerChildrenSpec> {
    val capture = ready.capture
    val scopesByValue = capture.scopes.entries.associate { (id, scope) -> id.value to scope }
    return ready.handling.plan.layers.associate { layer ->
        val scope = scopesByValue[layer.saveRecord.scopeId.value]
            ?: throw IllegalStateException(
                "Composite plan layer ${layer.saveRecord.scopeId.value} lost its capture scope",
            )
        val saveOperationIndex = requireNotNull(scope.saveOperationIndex) {
            "Composite layer scope ${scope.id.value} lost its save operation index"
        }
        val restoreOperationIndex = requireNotNull(scope.restoreOperationIndex) {
            "Composite layer scope ${scope.id.value} lost its restore operation index"
        }
        val childOperationIndices = (saveOperationIndex + 1)
            .until(restoreOperationIndex)
            .filter { operationIndex -> operationIndex in coveredOperationIndices }
        val childCommandIds = childOperationIndices.flatMap { operationIndex ->
            mapping.commandIdsByOperationIndex[operationIndex].orEmpty()
        }.distinct()
        val state = scope.state
            ?: throw IllegalStateException(
                "Composite layer scope ${scope.id.value} lost its capture state",
            )
        val localBounds = state.bounds
            ?: throw IllegalStateException(
                "Composite layer scope ${scope.id.value} lost its device bounds",
            )
        "layer-target:${layer.saveRecord.scopeId.value}" to GPUPreparedLayerChildrenSpec(
            commandIds = childCommandIds,
            bounds = layerDeviceBounds(state, localBounds, targetBounds),
        )
    }
}

/**
 * Covered operation indices of empty saveLayers: layers with no captured children or with
 * fully offscreen device bounds. Their children never render into an isolated target, so
 * they must be elided from the flat pipeline (the frame's uniform slab must exactly cover
 * the accepted packets) and their composite triplets are dropped.
 */
private fun emptyLayerCoveredOperationIndices(
    ready: CompositeFrameHandling.Ready,
    operations: List<DisplayOp>,
    coveredOperationIndices: Set<Int>,
    targetBounds: GPUPixelBounds,
): Set<Int> {
    val scopesByValue = ready.capture.scopes.entries.associate { (id, scope) -> id.value to scope }
    val emptyCovered = mutableSetOf<Int>()
    ready.handling.plan.layers.forEach { layer ->
        val scope = scopesByValue[layer.saveRecord.scopeId.value] ?: return@forEach
        val state = scope.state ?: return@forEach
        val localBounds = state.bounds ?: return@forEach
        val save = scope.saveOperationIndex ?: return@forEach
        val restore = scope.restoreOperationIndex ?: return@forEach
        val childOperationIndices = (save + 1).until(restore)
            .filter { operationIndex -> operationIndex in coveredOperationIndices }
        val hasCoreChild = childOperationIndices.any { operationIndex ->
            val operation = operations[operationIndex]
            operation !is DisplayOp.BeginLayer && operation !is DisplayOp.EndLayer
        }
        if (layerDeviceBounds(state, localBounds, targetBounds).isEmpty || !hasCoreChild) {
            emptyCovered += childOperationIndices
        }
    }
    return emptyCovered
}

/**
 * Device-space bounds of a saveLayer scope: the captured local bounds mapped through the
 * layer's transform at BeginLayer, clamped to the surface target. The splitter clips the
 * layer children's scissors to these bounds, so they must be in the same (device) space
 * as the children's geometry; the clamp keeps the bounds valid for [GPUPixelBounds] when
 * the layer extends offscreen.
 */
private fun layerDeviceBounds(
    state: GPUPreparedCompositeScopeState,
    localBounds: GPUPreparedRectSnapshot,
    targetBounds: GPUPixelBounds,
): GPUPixelBounds {
    val matrix = Matrix3x3F32.of(
        sx = Float.fromBits(state.transform.scaleXBits),
        kx = Float.fromBits(state.transform.skewXBits),
        tx = Float.fromBits(state.transform.transXBits),
        ky = Float.fromBits(state.transform.skewYBits),
        sy = Float.fromBits(state.transform.scaleYBits),
        ty = Float.fromBits(state.transform.transYBits),
        persp0 = Float.fromBits(state.transform.persp0Bits),
        persp1 = Float.fromBits(state.transform.persp1Bits),
        persp2 = Float.fromBits(state.transform.persp2Bits),
    )
    val left = Float.fromBits(localBounds.leftBits)
    val top = Float.fromBits(localBounds.topBits)
    val right = Float.fromBits(localBounds.rightBits)
    val bottom = Float.fromBits(localBounds.bottomBits)
    val corners = listOf(
        matrix.transform(Point2F32(left, top)),
        matrix.transform(Point2F32(right, top)),
        matrix.transform(Point2F32(right, bottom)),
        matrix.transform(Point2F32(left, bottom)),
    )
    val mappedLeft = kotlin.math.floor(corners.minOf { it.x })
    val mappedTop = kotlin.math.floor(corners.minOf { it.y })
    val mappedRight = kotlin.math.ceil(corners.maxOf { it.x })
    val mappedBottom = kotlin.math.ceil(corners.maxOf { it.y })
    val leftClamped = maxOf(0, mappedLeft.toInt())
    val topClamped = maxOf(0, mappedTop.toInt())
    val rightClamped = maxOf(leftClamped, minOf(targetBounds.right, mappedRight.toInt()))
    val bottomClamped = maxOf(topClamped, minOf(targetBounds.bottom, mappedBottom.toInt()))
    return GPUPixelBounds(leftClamped, topClamped, rightClamped, bottomClamped)
}

/**
 * Top-level operation indices whose render evidence is carried by the scheduled
 * composite commands: every index BETWEEN a matched BeginLayer and its EndLayer,
 * inclusive of the closing EndLayer index (and of any inner BeginLayer index —
 * desired so nested layer markers are elided too). Those operations render once
 * into the isolated layer target via RenderLayerChildren, so the flat pipeline
 * must elide them.
 *
 * Requires a balanced input: [GPUPreparedCompositeCapturer] refuses unbalanced
 * layers (LAYER_UNBALANCED) before this runs, so an orphan EndLayer can never
 * drive [openLayers] negative here.
 */
private fun compositeCoveredOperationIndices(operations: List<DisplayOp>): Set<Int> {
    val covered = linkedSetOf<Int>()
    var openLayers = 0
    operations.forEachIndexed { operationIndex, operation ->
        if (openLayers > 0) covered += operationIndex
        when (operation) {
            is DisplayOp.BeginLayer -> openLayers++
            DisplayOp.EndLayer -> openLayers--
            else -> Unit
        }
    }
    return covered
}

/**
 * Fail-closed topology check for a captured composite frame.
 *
 * The composite route materializes SaveLayer scopes only; root-scope entries and
 * PaintedPicture/FilterPictureSource scopes are never covered by composite
 * commands, and the flat mapper cannot replay a DrawPicture. Any DrawPicture the
 * composite commands cannot cover refuses terminally instead of being silently
 * dropped from the frame.
 */
private fun compositeTopologyRefusal(
    operations: List<DisplayOp>,
    coveredOperationIndices: Set<Int>,
): GPUDiagnostic? {
    val uncoveredPicture = operations.withIndex().firstOrNull { (operationIndex, operation) ->
        operation is DisplayOp.DrawPicture &&
            (operation.paint != null || operationIndex !in coveredOperationIndices)
    } ?: return null
    return diagnostic(
        code = "unsupported.surface.prepared.mixed-composite-topology",
        message = "Prepared Surface composite frames cannot cover the picture topology.",
        facts = mapOf(
            "boundary" to "surface.composite",
            "operationIndex" to uncoveredPicture.index.toString(),
        ),
    )
}

private fun prepareCompositeFrameHandling(
    operations: List<DisplayOp>,
    capabilities: GPUCapabilities,
    taskListBuilder: GPUPreparedSurfaceFrameTaskListBuilder,
    context: GPUTargetPreparationContext,
): CompositeFrameHandling {
    val capture = GPUPreparedCompositeCapturer.capture(
        operations = operations,
        limits = GPUPreparedCompositeCaptureLimits(),
    )
    if (capture is GPUPreparedCompositeCaptureResult.Refused) {
        return CompositeFrameHandling.Refused(capture.code, capture.operationIndex, capture.facts)
    }
    val ready = capture as GPUPreparedCompositeCaptureResult.Ready
    // Nested saveLayers are a documented refusal: the prepared-surface preflight refuses
    // them with unsupported.prepared-surface.layer-nesting until nested materialization
    // lands. Refuse at the builder boundary too so the children split never mis-assigns
    // sublist-relative scope ranges for nested topologies.
    val nestedSaveLayer = ready.capture.scopes.values.any { scope ->
        scope.sourceKind == GPUPreparedCompositeScopeKind.SaveLayer &&
            scope.parentId?.let { parentId ->
                ready.capture.scopes[parentId]?.sourceKind == GPUPreparedCompositeScopeKind.SaveLayer
            } == true
    }
    if (nestedSaveLayer) {
        return CompositeFrameHandling.Refused(
            code = "unsupported.prepared-surface.layer-nesting",
            operationIndex = null,
            facts = mapOf(
                "reason" to "nested saveLayers require nesting materialization",
            ),
        )
    }
    val handling = taskListBuilder.handleSaveLayer(
        scopes = ready.capture.scopes,
        rootScopeId = ready.capture.rootScopeId,
        identity = ready.capture.identity,
        capabilities = GPUPreflightCapabilities(
            maxTextureSize = (capabilities.limits?.maxTextureDimension2D ?: 4096L).toInt(),
            maxColorAttachments = DEFAULT_MAX_COLOR_ATTACHMENTS,
        ),
        context = context,
    )
    return when (handling) {
        is GPUPreparedSaveLayerFrameHandling.Ready -> CompositeFrameHandling.Ready(handling, ready.capture)
        is GPUPreparedSaveLayerFrameHandling.Refused ->
            CompositeFrameHandling.Refused(handling.code, handling.operationIndex, handling.facts)
    }
}

private fun contextFor(request: GPUPreparedSurfaceFrameBuildRequest): GPUTargetPreparationContext =
    GPUTargetPreparationContext(
        targetId = request.target.value,
        frameId = request.frameId.value.toString(),
        deviceGeneration = request.deviceGeneration.value,
        budgetClass = "default",
    )

/** WebGPU minimum for color attachments; the saveLayer preflight runs with this floor. */
private const val DEFAULT_MAX_COLOR_ATTACHMENTS = 8

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
    elidedOperationIndices: Set<Int> = emptySet(),
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
        if (operationIndex in elidedOperationIndices) {
            return@flatMap emptyList()
        }
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
    var previousVisualCommandId = -1
    visualSources.zip(mapping.visualCommands).forEachIndexed { commandIndex, (source, visual) ->
        val operationIndex = source.operationIndex
        val visualCommandId = visual.normalized.commandId.value
        // Visual command identities are assigned from the global command order, so
        // non-visual operations (prepared vertices) consume adjacent command ids and
        // the visual ids stay strictly increasing in source order.
        if (visualCommandId <= previousVisualCommandId ||
            visual.normalized.ordering.paintOrder != visualCommandId
        ) {
            return PreparedImageVisuals.Refused(
                imageCommandSourceDiagnostic(
                    message = "Prepared Surface command identity no longer matches source order.",
                    facts = mapOf(
                        "commandId" to visualCommandId.toString(),
                        "operationIndex" to operationIndex.toString(),
                    ),
                ),
            )
        }
        previousVisualCommandId = visualCommandId
        if (source is PreparedVisualSource.Core) {
            if (visual.normalized is NormalizedDrawCommand.DrawImageRect ||
                visual.preparedImage != null
            ) {
                return PreparedImageVisuals.Refused(
                    imageCommandSourceDiagnostic(
                        message = "Prepared core operation was associated with image facts.",
                        facts = mapOf(
                            "commandId" to visualCommandId.toString(),
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
                            "commandId" to visualCommandId.toString(),
                            "operationIndex" to operationIndex.toString(),
                        ),
                    ),
                )
            }
            textCommandIds += visualCommandId
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
            textCommandIds += visualCommandId
            pathStrokeCommandIds += visualCommandId
            return@forEachIndexed
        }
        source as PreparedVisualSource.Image
        val command = visual.normalized as? NormalizedDrawCommand.DrawImageRect
            ?: return PreparedImageVisuals.Refused(
                imageCommandSourceDiagnostic(
                    message = "Prepared image source was associated with a non-image command.",
                    facts = mapOf(
                        "commandId" to visualCommandId.toString(),
                        "operationIndex" to operationIndex.toString(),
                    ),
                ),
            )
        val prepared = visual.preparedImage
            ?: return PreparedImageVisuals.Refused(
                imageCommandSourceDiagnostic(
                    message = "Prepared image command lost its lowerer facts.",
                    facts = mapOf(
                        "commandId" to visualCommandId.toString(),
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
                        "commandId" to visualCommandId.toString(),
                        "operationIndex" to operationIndex.toString(),
                    ),
                ),
            )
        }
        artifacts[visualCommandId] = artifact
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
    is GPUClipExecutionPlan.AnalyticMultiRect ->
        "clip.AnalyticMultiRect.aa".takeIf { elements.any { element -> element.antiAlias } }
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

/**
 * Fail-closed preflight; the authenticated prepared-vertices native route replaces the
 * refusal once the semantic-only recording evidence bijects with the prepared vertices payloads.
 * The exact semantic-only packet evidence is validated by [GPUPreparedVerticesSemanticBuilder];
 * this function only keeps the bijection authority.
 */
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
    return null
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
