package org.graphiks.kanvas.gpu.renderer.recording

import org.graphiks.kanvas.gpu.renderer.analysis.GPUDrawAnalysis
import org.graphiks.kanvas.gpu.renderer.analysis.GPUDrawAnalysisDecision
import org.graphiks.kanvas.gpu.renderer.product.GPUProductFlagConfig
import org.graphiks.kanvas.gpu.renderer.analysis.GPUDrawAnalysisRecord
import org.graphiks.kanvas.gpu.renderer.analysis.GPUAnalysisDependency
import org.graphiks.kanvas.gpu.renderer.analysis.GPUAnalysisDiagnostic
import org.graphiks.kanvas.gpu.renderer.analysis.GPUColorGlyphRoutePlanner
import org.graphiks.kanvas.gpu.renderer.analysis.GPUFirstRoutePlan
import org.graphiks.kanvas.gpu.renderer.analysis.GPUFirstRoutePlanner
import org.graphiks.kanvas.gpu.renderer.analysis.SortKey
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.collections.immutableList
import org.graphiks.kanvas.gpu.renderer.collections.immutableMap
import org.graphiks.kanvas.gpu.renderer.commands.GPUDrawCommandID
import org.graphiks.kanvas.gpu.renderer.commands.GPUBounds
import org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand
import org.graphiks.kanvas.gpu.renderer.commands.GPUFrameProvenance
import org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationSnapshotGroupingResult
import org.graphiks.kanvas.gpu.renderer.destination.CopyAsDrawMaterialization
import org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationSnapshotMaterialization
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnostic
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticCode
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticDomain
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticSeverity
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlan
import org.graphiks.kanvas.gpu.renderer.passes.GPUFirstRoutePassBuilder
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketID
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchEligibility
import org.graphiks.kanvas.gpu.renderer.passes.GPUProvisionalRenderSegmentKey
import org.graphiks.kanvas.gpu.renderer.passes.GPUSampleContinuationKey
import org.graphiks.kanvas.gpu.renderer.passes.GPUSamplePlan
import org.graphiks.kanvas.gpu.renderer.passes.GPURefusalScope
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryBudgetPlan
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryCategory
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUse
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTextureRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceCopyRegion
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourcePreparationRequest
import org.graphiks.kanvas.gpu.renderer.resources.GPUTextureCopyLayout
import org.graphiks.kanvas.gpu.renderer.resources.GPUUploadLayout
import org.graphiks.kanvas.gpu.renderer.state.GPULoadStorePlan
import org.graphiks.kanvas.gpu.renderer.state.GPUStorePlan
import org.graphiks.kanvas.gpu.renderer.routing.GPUFirstRouteDecisionBuilder
import org.graphiks.kanvas.gpu.renderer.routing.GPURouteDecision
import org.graphiks.kanvas.font.atlas.GlyphAtlasUploadPlan
import org.graphiks.kanvas.gpu.renderer.analysis.GPUTextA8RoutePlanner
import org.graphiks.kanvas.gpu.renderer.text.GPUTextDiagnosticCodes
import org.graphiks.kanvas.gpu.renderer.text.GPUTextRouteDecision
import org.graphiks.kanvas.gpu.renderer.intermediates.GPUIntermediateIdentity

/** Stable recording identifier. */
@JvmInline
value class GPURecordingID(val value: String) {
    init {
        require(value.isNotBlank()) { "GPURecordingID.value must not be blank" }
    }
}

/** Shared recorder scope identity. */
@JvmInline
value class GPUSharedScope(val value: String) {
    init {
        require(value.isNotBlank()) { "GPUSharedScope.value must not be blank" }
    }
}

/** Recorder-local scope identity. */
@JvmInline
value class GPURecorderScope(val value: String) {
    init {
        require(value.isNotBlank()) { "GPURecorderScope.value must not be blank" }
    }
}

/** Frame-local scope identity. */
@JvmInline
value class GPUFrameScope(val value: String) {
    init {
        require(value.isNotBlank()) { "GPUFrameScope.value must not be blank" }
    }
}

/** Atlas mutation scope identity. */
@JvmInline
value class GPUAtlasScope(val value: String) {
    init {
        require(value.isNotBlank()) { "GPUAtlasScope.value must not be blank" }
    }
}

/**
 * Compatibility key used to decide whether a closed recording can be replayed.
 *
 * The recording package owns this key. It contains only stable assumptions:
 * command-shape version, dictionary/runtime snapshots, capability class,
 * target format class, resource topology class, and replay policy. It never
 * stores surface leases, backend handles, bind groups, or other transient
 * resource identity. Blank fields are rejected by data construction where a
 * value class is used; replay failures are reported by [GPURecordingReplayResult]
 * rather than by mutating the key.
 */
data class GPURecordingCompatibilityKey(
    val keyHash: String,
    val commandShapeVersion: Int,
    val dictionaryVersion: String,
    val runtimeRegistrySnapshot: String,
    val capabilityClass: String,
    val targetFormatClass: String,
    val resourceTopologyClass: String,
    val replayPolicy: String,
)

/**
 * Deterministic dump of a compatibility key.
 *
 * The dump is evidence, not an execution permission. Its hash mirrors the key
 * hash, and its lines are safe to compare in tests or PM bundles without
 * exposing backend resources.
 */
data class GPURecordingCompatibilityKeyDump(
    val keyHash: String,
    val lines: List<String>,
)

/**
 * Deterministic dump of analysis decisions before materialization.
 *
 * The dump belongs to the recording package because it freezes which analysis
 * decisions were converted into tasks. It references analysis records by ID and
 * stable hashes only; resource materialization, backend handles, and submission
 * outcomes must not appear here.
 */
data class GPUAnalysisDecisionDump(
    val dumpId: String,
    val recordingId: GPURecordingID,
    val decisionHash: String,
    val lines: List<String>,
)

/**
 * Immutable GPU recording produced by [GPURecorder.close].
 *
 * Ownership stays with the recording package. A recording contains immutable
 * analysis, pre-materialization task-list evidence, route diagnostics, feature
 * assumptions, and replay compatibility facts. It does not own concrete GPU
 * resources or backend submission state. Replay must be checked through
 * [checkReplayCompatibility]; one-shot or incompatible recordings refuse with
 * stable diagnostics instead of falling back to CPU rendering.
 */
data class GPURecording(
    val recordingId: GPURecordingID,
    val compatibilityKey: GPURecordingCompatibilityKey,
    val analysis: GPUDrawAnalysis,
    val analysisDecisionDump: GPUAnalysisDecisionDump,
    val analysisHash: String,
    val taskList: GPUTaskList,
    val routeDiagnostics: List<String>,
    val featureAssumptions: List<String>,
    val recordedCommands: List<NormalizedDrawCommand> = emptyList(),
)

/**
 * Dependency token between target-ordered recordings.
 *
 * This is the Kanvas-owned adaptation of Graphite-style ordered recording
 * submission: it preserves explicit insertion dependencies without importing
 * Graphite classes, bit layouts, or scheduler ownership.
 */
data class GPURecordingDependencyToken(
    val tokenLabel: String,
    val fromRecordingId: GPURecordingID,
    val toRecordingId: GPURecordingID,
    val dependencyKind: String,
    val reasonCode: String,
)

/**
 * Recording with explicit order, target scope, and cross-recording dependencies.
 *
 * The value is evidence for target insertion, not a submitted command buffer.
 * A later recording may depend on an earlier recording through dependency
 * tokens; a refused recording remains visible but does not erase later
 * recordings. Invalid ordering inputs fail by ordinary collection construction
 * before backend work exists.
 */
data class GPUOrderedRecording(
    val recordingId: GPURecordingID,
    val insertionOrder: Long,
    val targetScope: GPUFrameScope,
    val barrierClass: String,
    val dependencyTokens: List<GPURecordingDependencyToken> = emptyList(),
)

/**
 * Builds ordered-recording evidence for one target insertion list.
 *
 * The object records Kanvas-owned dependency facts only. It does not schedule a
 * broad render graph, allocate resources, or submit work. Empty input returns an
 * empty list; non-empty input preserves list order and adds a dependency token
 * from each previous recording to the next.
 */
object GPURecordingOrder {
    /** Returns ordered recording facts for the provided target scope. */
    fun orderedRecordings(
        recordings: List<GPURecording>,
        targetScope: GPUFrameScope,
    ): List<GPUOrderedRecording> =
        recordings.mapIndexed { index, recording ->
            val previous = recordings.getOrNull(index - 1)
            GPUOrderedRecording(
                recordingId = recording.recordingId,
                insertionOrder = index.toLong(),
                targetScope = targetScope,
                barrierClass = if (previous == null) "target-insertion-start" else "target-order-barrier",
                dependencyTokens = previous?.let { prior ->
                    listOf(
                        GPURecordingDependencyToken(
                            tokenLabel = "ordered.${prior.recordingId.value}->${recording.recordingId.value}",
                            fromRecordingId = prior.recordingId,
                            toRecordingId = recording.recordingId,
                            dependencyKind = "recording-order",
                            reasonCode = "preserve.target.insertion.order",
                        ),
                    )
                } ?: emptyList(),
            )
        }
}

/**
 * Recorder for already-normalized first-route commands.
 *
 * The recorder owns command intake for the R5 slice. It accepts immutable
 * [NormalizedDrawCommand] values, plans the currently supported FillRect route,
 * explicitly refuses text handoff commands, and closes into an immutable
 * [GPURecording]. It does not accept Canvas state, allocate materials/resources,
 * submit backend work, or hide unsupported draws behind CPU fallback. Calling [record] after [close] fails with
 * [IllegalStateException]; unsupported command facts become refused analysis and
 * task diagnostics instead of exceptions.
 */
class GPURecorder(
    private val recordingId: GPURecordingID,
    private val frameId: GPUFrameID,
    private val capabilities: GPUCapabilities = GPUProductFlagConfig.fromSystemProperties().buildCapabilities(),
    private val deviceGeneration: GPUDeviceGenerationID = GPUDeviceGenerationID(0),
    private val sharedScope: GPUSharedScope = GPUSharedScope("shared.default"),
    private val recorderScope: GPURecorderScope = GPURecorderScope("recorder.${recordingId.value}"),
    private val frameScope: GPUFrameScope = GPUFrameScope("frame.default"),
) {
    private val commands = mutableListOf<NormalizedDrawCommand>()
    private var closedRecording: GPURecording? = null

    /** Returns a snapshot of recorded commands (defensive copy). */
    fun recordedCommands(): List<NormalizedDrawCommand> = commands.toList()

    /** Records one already-normalized command into this recorder scope. */
    fun record(command: NormalizedDrawCommand) {
        check(closedRecording == null) { "GPURecorder.record cannot be called after close" }
        commands += command
    }

    /**
     * Closes the recorder and returns immutable recording evidence.
     *
     * The first call performs analysis and task-list assembly. Later calls
     * return the same immutable recording. Resource preparation and command
     * submission remain out of scope for this slice.
     */
    fun close(): GPURecording {
        closedRecording?.let { return it }

        val plans = commands.map(::planCommand)
        val analysis = GPUDrawAnalysis(
            analysisId = "analysis.${recordingId.value}",
            records = plans.map { plan -> plan.analysisRecord },
            dependencies = plans.analysisDependencies(),
            occlusionProofs = emptyList(),
            diagnostics = plans.flatMap { plan -> plan.analysisRecord.diagnostics },
        )
        val analysisDecisionDump = analysisDecisionDump(recordingId = recordingId, plans = plans)
        val compatibilityKey = compatibilityKey(commands = commands, capabilities = capabilities)
        val taskList = taskList(
            recordingId = recordingId,
            frameId = frameId,
            deviceGeneration = deviceGeneration,
            compatibilityKey = compatibilityKey,
            capabilities = capabilities,
            plans = plans,
        )
        val recording = GPURecording(
            recordingId = recordingId,
            compatibilityKey = compatibilityKey,
            analysis = analysis,
            analysisDecisionDump = analysisDecisionDump,
            analysisHash = analysisDecisionDump.decisionHash,
            taskList = taskList,
            routeDiagnostics = plans.map { plan -> plan.routeDiagnostic() },
            featureAssumptions = capabilities.featureAssumptions(),
            recordedCommands = commands.toList(),
        )

        closedRecording = recording
        return recording
    }

    private fun planCommand(command: NormalizedDrawCommand): GPUFirstRoutePlan =
        when (command) {
            is NormalizedDrawCommand.FillRect -> GPUFirstRoutePlanner(capabilities = capabilities).plan(command)
            is NormalizedDrawCommand.FillRRect -> GPUFirstRoutePlanner(capabilities = capabilities).plan(command)
            is NormalizedDrawCommand.DrawTextRun -> planDrawTextRun(command)
            is NormalizedDrawCommand.FillPath -> GPUFirstRoutePlanner(capabilities = capabilities).plan(command)
            is NormalizedDrawCommand.DrawImageRect -> GPUFirstRoutePlanner(capabilities = capabilities).plan(command)
            is NormalizedDrawCommand.ApplyFilter -> GPUFirstRoutePlanner(capabilities = capabilities).plan(command)
            is NormalizedDrawCommand.DrawLayer -> GPUFirstRoutePlanner(capabilities = capabilities).plan(command)
        }

    private fun planDrawTextRun(command: NormalizedDrawCommand.DrawTextRun): GPUFirstRoutePlan {
        if (command.colorGlyphPlans.isNotEmpty()) {
            return GPUColorGlyphRoutePlanner().plan(command)
        }
        val descriptor = command.glyphRunDescriptor
        if (descriptor != null) {
            val textRouteDecision = GPUTextA8RoutePlanner().planTextRoute(descriptor)
            return when (textRouteDecision) {
                is GPUTextRouteDecision.Accepted -> GPUTextA8RoutePlanner().plan(command)
                is GPUTextRouteDecision.Refused -> refusedDrawTextRunPlan(command)
            }
        }
        return refusedDrawTextRunPlan(command)
    }

    private fun refusedDrawTextRunPlan(command: NormalizedDrawCommand.DrawTextRun): GPUFirstRoutePlan {
        val code = "unsupported.text.draw_run_route_unavailable"
        val recordId = "analysis.draw_text_run.${command.commandId.value}"
        val diagnostic = GPUAnalysisDiagnostic(
            code = code,
            recordId = recordId,
            decisionId = "refused.draw_text_run.${command.commandId.value}",
            terminal = true,
        )
        val textDiagnostics = command.routeDiagnostics.map { textDiagnostic ->
            GPUAnalysisDiagnostic(
                code = textDiagnostic.code,
                recordId = recordId,
                decisionId = "text.draw_text_run.${command.commandId.value}",
                terminal = textDiagnostic.terminal,
            )
        }
        val payloadDiagnostics = command.textPayloadLeakDiagnostics(recordId = recordId)
        val analysisRecord = GPUDrawAnalysisRecord(
            recordId = recordId,
            commandIdValue = command.commandId.value,
            commandFamily = "DrawTextRun",
            boundsHash = command.bounds.recordingBoundsHash(),
            routeDecisionLabel = "refused.$code",
            materialKeyHash = "none",
            renderStepCandidates = emptyList(),
            sortKey = SortKey(command.ordering.paintOrder.toLong()),
            diagnostics = listOf(diagnostic) + textDiagnostics + payloadDiagnostics,
        )
        return GPUFirstRoutePlan(
            analysisRecord = analysisRecord,
            analysisDecision = GPUDrawAnalysisDecision.Refuse(recordId = recordId, diagnostic = diagnostic),
            routeDecision = GPUFirstRouteDecisionBuilder.refused(code = code, stage = "analysis"),
            pass = GPUFirstRoutePassBuilder.refusedFillRect(
                commandIdValue = command.commandId.value,
                targetStateHash = command.recordingTargetStateHash(),
                code = code,
            ),
        )
    }

    private fun refusedFillPathPlan(command: NormalizedDrawCommand.FillPath): GPUFirstRoutePlan {
        val code = "unsupported.command.fill_path"
        val recordId = "analysis.fill_path.${command.commandId.value}"
        val diagnostic = GPUAnalysisDiagnostic(
            code = code,
            recordId = recordId,
            decisionId = "refused.fill_path.${command.commandId.value}",
            terminal = true,
        )
        val analysisRecord = GPUDrawAnalysisRecord(
            recordId = recordId,
            commandIdValue = command.commandId.value,
            commandFamily = "FillPath",
            boundsHash = command.bounds.recordingBoundsHash(),
            routeDecisionLabel = "refused.$code",
            materialKeyHash = "none",
            renderStepCandidates = emptyList(),
            sortKey = SortKey(command.ordering.paintOrder.toLong()),
            diagnostics = listOf(diagnostic),
        )
        return GPUFirstRoutePlan(
            analysisRecord = analysisRecord,
            analysisDecision = GPUDrawAnalysisDecision.Refuse(recordId = recordId, diagnostic = diagnostic),
            routeDecision = GPUFirstRouteDecisionBuilder.refused(code = code, stage = "analysis"),
            pass = GPUFirstRoutePassBuilder.refusedFillRect(
                commandIdValue = command.commandId.value,
                targetStateHash = command.recordingTargetStateHash(),
                code = code,
            ),
        )
    }

    private fun NormalizedDrawCommand.DrawTextRun.textPayloadLeakDiagnostics(
        recordId: String,
    ): List<GPUAnalysisDiagnostic> {
        val dumpableFields = buildList {
            textLayoutResultId?.let(::add)
            glyphRunId?.let(::add)
            addAll(glyphRunDescriptorRefs)
            artifactRefs.forEach { ref ->
                add(ref.artifactType)
                add(ref.artifactId)
                add(ref.artifactKeyHash)
                add(ref.generationToken)
                ref.routeHint?.let(::add)
            }
            addAll(artifactKeyHashes)
            addAll(atlasGenerationTokens)
            addAll(uploadDependencyFacts)
            routeDiagnostics.forEach { textDiagnostic ->
                add(textDiagnostic.code)
                add(textDiagnostic.message)
            }
        }
        return buildList {
            if (dumpableFields.any(::containsSkiaLikeToken)) {
                add(
                    GPUAnalysisDiagnostic(
                        code = GPUTextDiagnosticCodes.SK_TYPE_LEAKED,
                        recordId = recordId,
                        decisionId = "text.payload_leak.${commandId.value}",
                        terminal = true,
                    ),
                )
            }
            if (dumpableFields.any(::containsCpuRenderedTextureToken)) {
                add(
                    GPUAnalysisDiagnostic(
                        code = GPUTextDiagnosticCodes.CPU_RENDERED_TEXTURE_FORBIDDEN,
                        recordId = recordId,
                        decisionId = "text.cpu_texture_forbidden.${commandId.value}",
                        terminal = true,
                    ),
                )
            }
        }
    }

    @Suppress("unused")
    private fun scopeEvidence(): List<String> =
        listOf(sharedScope.value, recorderScope.value, frameScope.value)
}

private fun containsSkiaLikeToken(value: String): Boolean =
    value.contains("org.skia.") ||
        value.split('#', ':', '/', '.', '$').any { token -> token.startsWith("Sk") }

private fun containsCpuRenderedTextureToken(value: String): Boolean {
    val normalized = value.lowercase()
    return normalized.contains("cpu-rendered-texture") ||
        normalized.contains("cpu_rendered_texture") ||
        normalized.contains("cpu rendered texture")
}

/**
 * Result of a replay compatibility check.
 *
 * Replay checks never mutate the recording. The current R5 implementation
 * exposes one-shot refusal even when the target key matches, so later replayable
 * recordings can reuse the same API without changing failure behavior.
 */
sealed interface GPURecordingReplayResult {
    /** Replay is legal under the supplied compatibility key. */
    data class Replayable(
        val recordingId: GPURecordingID,
        val compatibilityKeyDump: GPURecordingCompatibilityKeyDump,
    ) : GPURecordingReplayResult

    /** Replay is refused with stable diagnostic evidence. */
    data class Refused(
        val diagnostic: GPURecordingDiagnostic,
        val compatibilityKeyDump: GPURecordingCompatibilityKeyDump,
    ) : GPURecordingReplayResult
}

/**
 * Checks whether this recording can be replayed for the supplied target key.
 *
 * A key mismatch refuses with `replay.compatibility_key_mismatch`. A matching
 * key still refuses when the recording policy is `one-shot`, which is the only
 * policy emitted by the R5 first-route slice.
 */
fun GPURecording.checkReplayCompatibility(
    targetKey: GPURecordingCompatibilityKey,
): GPURecordingReplayResult {
    val dump = compatibilityKey.dump()
    val diagnosticCode = when {
        compatibilityKey.keyHash != targetKey.keyHash -> "replay.compatibility_key_mismatch"
        compatibilityKey.replayPolicy == replayPolicyOneShot -> "replay.one_shot_recording"
        else -> null
    }

    return if (diagnosticCode == null) {
        GPURecordingReplayResult.Replayable(recordingId = recordingId, compatibilityKeyDump = dump)
    } else {
        GPURecordingReplayResult.Refused(
            diagnostic = GPURecordingDiagnostic(
                code = diagnosticCode,
                recordingId = recordingId,
                taskId = null,
                terminal = true,
            ),
            compatibilityKeyDump = dump,
        )
    }
}

/** Stable phase classes used for deterministic topological tie-breaking. */
enum class GPUTaskPhase {
    Prepare,
    Upload,
    Compute,
    Render,
    Copy,
    Transition,
    Readback,
    Output,
    Refusal,
}

/** Exact relation between one Task 5 member and its canonical render packet. */
data class GPUDestinationSnapshotConsumerRef(
    val groupingCommandId: String,
    val renderTaskId: GPUTaskID,
    val packetId: GPUDrawPacketID,
    val commandId: GPUDrawCommandID,
) {
    init {
        require(groupingCommandId.isNotBlank()) {
            "GPUDestinationSnapshotConsumerRef.groupingCommandId must not be blank"
        }
    }
}

/** Exact relation between one Task 5 refusal and its canonical refused task. */
data class GPUDestinationSnapshotRefusalBinding(
    val groupingCommandId: String,
    val refusedTaskId: GPUTaskID,
    val commandId: GPUDrawCommandID,
) {
    init {
        require(groupingCommandId.isNotBlank()) {
            "GPUDestinationSnapshotRefusalBinding.groupingCommandId must not be blank"
        }
    }
}

/** One normalized Task 5 materialization with its exact source and consumers. */
sealed interface GPUDestinationSnapshotOperation {
    val groupIndex: Int
    val source: GPUFrameResourceRef
    val sourceIntermediate: GPUIntermediateIdentity?
    val snapshot: GPUFrameTextureRef
    val logicalBounds: org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
    val consumers: List<GPUDestinationSnapshotConsumerRef>

    class TextureCopy(
        override val groupIndex: Int,
        override val source: GPUFrameResourceRef,
        override val snapshot: GPUFrameTextureRef,
        override val logicalBounds: org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds,
        val copyLayout: GPUTextureCopyLayout,
        consumers: List<GPUDestinationSnapshotConsumerRef>,
    ) : GPUDestinationSnapshotOperation {
        override val sourceIntermediate: GPUIntermediateIdentity? = null
        override val consumers: List<GPUDestinationSnapshotConsumerRef> = immutableList(consumers)
    }

    class CopyAsDraw(
        override val groupIndex: Int,
        override val source: GPUFrameResourceRef,
        override val sourceIntermediate: GPUIntermediateIdentity,
        override val snapshot: GPUFrameTextureRef,
        override val logicalBounds: org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds,
        consumers: List<GPUDestinationSnapshotConsumerRef>,
    ) : GPUDestinationSnapshotOperation {
        override val consumers: List<GPUDestinationSnapshotConsumerRef> = immutableList(consumers)
    }
}

/** Typed resources and exact associations needed to lower Task 5 grouping output. */
class GPUDestinationSnapshotTaskPayload(
    grouping: GPUDestinationSnapshotGroupingResult,
    operations: List<GPUDestinationSnapshotOperation>,
    refusalBindings: List<GPUDestinationSnapshotRefusalBinding> = emptyList(),
) {
    val grouping: GPUDestinationSnapshotGroupingResult = grouping.snapshotGrouping()
    val operations: List<GPUDestinationSnapshotOperation> =
        immutableList(operations.sortedBy(GPUDestinationSnapshotOperation::groupIndex))
    val refusalBindings: List<GPUDestinationSnapshotRefusalBinding> = immutableList(refusalBindings)

    init {
        val expectedIndices = grouping.groups.indices.toSet()
        val materializationIndices = grouping.materializations.map { it.groupIndex }
        val operationIndices = operations.map { it.groupIndex }
        require(materializationIndices.toSet() == expectedIndices &&
            materializationIndices.distinct().size == materializationIndices.size
        ) { "Task 5 materializations must cover every destination group exactly" }
        require(operationIndices.toSet() == expectedIndices && operationIndices.distinct().size == operationIndices.size) {
            "Destination snapshot operations must cover every Task 5 group exactly"
        }

        val materializations = grouping.materializations.associateBy { it.groupIndex }
        val normalizedOperations = operations.associateBy { it.groupIndex }
        grouping.groups.forEachIndexed { index, group ->
            val materialization = materializations.getValue(index)
            val operation = normalizedOperations.getValue(index)
            require(operation.logicalBounds == group.logicalBounds &&
                materialization.logicalBounds == group.logicalBounds
            ) { "Destination snapshot bounds must match Task 5 grouping exactly" }
            when (materialization) {
                is GPUDestinationSnapshotMaterialization.TextureCopy -> require(
                    operation is GPUDestinationSnapshotOperation.TextureCopy &&
                        group.key.sourceIntermediate == null &&
                        operation.source is GPUFrameTargetRef &&
                        operation.source.value == group.key.target.value &&
                        operation.source.value != operation.snapshot.value,
                ) { "TextureCopy operation must match its Task 5 materialization and canonical source" }
                is CopyAsDrawMaterialization -> require(
                    operation is GPUDestinationSnapshotOperation.CopyAsDraw &&
                        operation.source is GPUFrameTextureRef &&
                        operation.source != operation.snapshot &&
                        operation.sourceIntermediate == materialization.sourceIntermediate &&
                        operation.sourceIntermediate == group.key.sourceIntermediate,
                ) { "CopyAsDraw operation must preserve the exact Task 5 intermediate source" }
            }
        }
    }
}

private fun GPUDestinationSnapshotGroupingResult.snapshotGrouping(): GPUDestinationSnapshotGroupingResult =
    copy(
        groups = immutableList(groups.map { group ->
            group.copy(
                members = immutableList(group.members),
                decisionDump = immutableList(group.decisionDump),
            )
        }),
        materializations = immutableList(materializations),
        refusals = immutableList(refusals.map { refusal ->
            refusal.copy(facts = immutableMap(refusal.facts))
        }),
        decisionDump = immutableList(decisionDump),
    )

/** Typed identity for one composite command and its draw-only child scope. */
@JvmInline
value class GPUCompositeScopeID(val value: String) {
    init {
        require(value.isNotBlank()) { "GPUCompositeScopeID.value must not be blank" }
    }
}

/** Child-owned proof that a render task belongs to one composite refusal scope. */
data class GPUTaskCompositeMembership(
    val scopeId: GPUCompositeScopeID,
    val parentCommandId: GPUDrawCommandID,
    val childOrdinal: Int,
    val provenanceToken: GPUCompositeProvenanceToken,
) {
    init {
        require(childOrdinal >= 0) { "GPUTaskCompositeMembership.childOrdinal must be non-negative" }
    }
}

/** Task emitted by recording and planning with one typed semantic payload. */
sealed interface GPUTask {
    val taskId: GPUTaskID
    val recordingId: GPURecordingID
    val phase: GPUTaskPhase
    val compositeMembership: GPUTaskCompositeMembership? get() = null

    /** Sole render authority; packets and blend decisions are supplied, never reconstructed. */
    class Render(
        override val taskId: GPUTaskID,
        override val recordingId: GPURecordingID,
        override val phase: GPUTaskPhase,
        val target: GPUFrameTargetRef,
        val loadStore: GPULoadStorePlan,
        val samplePlan: GPUSamplePlan,
        resourceUses: List<GPUFrameResourceUse> = emptyList(),
        val provisionalSegmentKey: GPUProvisionalRenderSegmentKey = GPUProvisionalRenderSegmentKey(
            "target.${target.value}.sample.${samplePlan.specializationKey}",
        ),
        drawPackets: List<GPUDrawPacket>,
        batchEligibilityByPacketId: Map<GPUDrawPacketID, GPUPassBatchEligibility>,
        val sampleContinuationKey: GPUSampleContinuationKey? = null,
        override val compositeMembership: GPUTaskCompositeMembership? = null,
    ) : GPUTask {
        val drawPackets: List<GPUDrawPacket> = immutableList(drawPackets)
        val resourceUses: List<GPUFrameResourceUse> = immutableList(resourceUses)
        val blendPlans: List<GPUBlendPlan> = immutableList(drawPackets.map { packet ->
            requireNotNull(packet.blendPlan) {
                "GPUTask.Render packets must retain their canonical GPUBlendPlan"
            }
        })
        val batchEligibilityByPacketId: Map<GPUDrawPacketID, GPUPassBatchEligibility> =
            immutableMap(batchEligibilityByPacketId)
        val frameProvenanceByPacketId: Map<GPUDrawPacketID, GPUFrameProvenance> =
            immutableMap(drawPackets.associate { packet -> packet.packetId to packet.frameProvenance })

        init {
            require(phase == GPUTaskPhase.Render) { "GPUTask.Render requires Render phase" }
            require(drawPackets.isNotEmpty()) { "GPUTask.Render.drawPackets must not be empty" }
            require(batchEligibilityByPacketId.keys == drawPackets.map { it.packetId }.toSet()) {
                "GPUTask.Render batching eligibility must cover every packet exactly"
            }
        }

        val passId: String get() = drawPackets.first().passId
        val analysisRecordId: String get() = drawPackets.first().analysisRecordId
        val renderStepIds: List<String> get() = drawPackets.map { it.renderStepId.value }
        val pipelineKeyHashes: List<String> get() = drawPackets.mapNotNull { it.renderPipelineKey?.value }
        val preMaterialization: Boolean get() = true
        val materializedResourceLabels: List<String> get() = emptyList()
    }

    class PrepareResources(
        override val taskId: GPUTaskID,
        override val recordingId: GPURecordingID,
        override val phase: GPUTaskPhase,
        requests: List<GPUResourcePreparationRequest>,
    ) : GPUTask {
        val requests: List<GPUResourcePreparationRequest> = immutableList(requests)
    }

    class Compute(
        override val taskId: GPUTaskID,
        override val recordingId: GPURecordingID,
        override val phase: GPUTaskPhase,
        val target: GPUFrameTargetRef,
        resourceUses: List<GPUFrameResourceUse>,
        dispatches: List<GPUComputeDispatch>,
    ) : GPUTask {
        val resourceUses: List<GPUFrameResourceUse> = immutableList(resourceUses)
        val dispatches: List<GPUComputeDispatch> = immutableList(dispatches)
    }

    class Copy(
        override val taskId: GPUTaskID,
        override val recordingId: GPURecordingID,
        override val phase: GPUTaskPhase,
        val source: GPUFrameResourceRef,
        val destination: GPUFrameResourceRef,
        regions: List<GPUResourceCopyRegion>,
    ) : GPUTask {
        val regions: List<GPUResourceCopyRegion> = immutableList(regions)
    }

    data class Upload(
        override val taskId: GPUTaskID,
        override val recordingId: GPURecordingID,
        override val phase: GPUTaskPhase,
        val staging: GPUFrameBufferRef,
        val destination: GPUFrameResourceRef,
        val layout: GPUUploadLayout,
    ) : GPUTask

    class Barrier(
        override val taskId: GPUTaskID,
        override val recordingId: GPURecordingID,
        override val phase: GPUTaskPhase,
        orderedUseTokens: List<GPUTaskUseToken>,
        val reasonCode: String,
    ) : GPUTask {
        val orderedUseTokens: List<GPUTaskUseToken> = immutableList(orderedUseTokens)
    }

    data class DestinationSnapshots(
        override val taskId: GPUTaskID,
        override val recordingId: GPURecordingID,
        override val phase: GPUTaskPhase,
        val payload: GPUDestinationSnapshotTaskPayload,
    ) : GPUTask

    data class TargetTransition(
        override val taskId: GPUTaskID,
        override val recordingId: GPURecordingID,
        override val phase: GPUTaskPhase,
        val parent: GPUFrameTargetRef,
        val child: GPUFrameTargetRef,
        val transitionKind: GPUTargetTransitionKind,
    ) : GPUTask

    data class Readback(
        override val taskId: GPUTaskID,
        override val recordingId: GPURecordingID,
        override val phase: GPUTaskPhase,
        val source: GPUFrameTargetRef,
        val staging: GPUFrameBufferRef,
        val request: GPUFrameReadbackRequest,
    ) : GPUTask

    data class Output(
        override val taskId: GPUTaskID,
        override val recordingId: GPURecordingID,
        override val phase: GPUTaskPhase,
        val scene: GPUFrameTargetRef,
        val descriptor: GPUSurfaceOutputDescriptor,
    ) : GPUTask

    class Refused(
        override val taskId: GPUTaskID,
        override val recordingId: GPURecordingID,
        override val phase: GPUTaskPhase,
        val commandId: GPUDrawCommandID,
        val scope: org.graphiks.kanvas.gpu.renderer.passes.GPURefusalScope,
        val compositeScopeId: GPUCompositeScopeID? = null,
        provenanceTokens: List<GPUCompositeProvenanceToken>,
        consumedChildTaskIds: List<GPUTaskID>,
        diagnostic: GPUDiagnostic,
    ) : GPUTask {
        val provenanceTokens: List<GPUCompositeProvenanceToken> = immutableList(provenanceTokens)
        val consumedChildTaskIds: List<GPUTaskID> = immutableList(consumedChildTaskIds)
        val diagnostic: GPUDiagnostic = diagnostic.copy(facts = immutableMap(diagnostic.facts))

        init {
            require(diagnostic.isTerminal) { "GPUTask.Refused requires a terminal diagnostic" }
            when (scope) {
                GPURefusalScope.RefusedLeafDrawStep -> require(
                    compositeScopeId == null && provenanceTokens.isEmpty() && consumedChildTaskIds.isEmpty(),
                ) { "Leaf refusal must not consume composite child provenance" }
                GPURefusalScope.RefusedCompositeCommand -> require(
                    compositeScopeId != null && provenanceTokens.isNotEmpty() &&
                        provenanceTokens.size == consumedChildTaskIds.size &&
                        consumedChildTaskIds.distinct().size == consumedChildTaskIds.size,
                ) { "Composite refusal requires one provenance token per unique consumed child task" }
                GPURefusalScope.AtomicFrameFailure -> require(compositeScopeId == null) {
                    "Atomic frame refusal must not own a composite child scope"
                }
            }
        }
    }
}

/**
 * Ordered task list with dependency evidence.
 *
 * The list belongs to a closed recording. It may contain render, barrier,
 * upload/copy, and refused tasks, but the R5 first route emits only render or
 * refused tasks plus explicit ordering dependencies. Diagnostics are terminal
 * when a refused task blocks that command; they do not imply CPU fallback.
 */
class GPUTaskList(
    val frameId: GPUFrameID,
    val capabilitySeal: GPUFrameCapabilitySeal,
    recordingSeals: List<GPURecordingSeal>,
    val expectedReplayKeyHash: String,
    tasks: List<GPUTask>,
    dependencies: List<GPUTaskDependency>,
    phaseOrder: List<GPUTaskPhase>,
    memoryBudget: GPUFrameMemoryBudgetPlan,
    diagnostics: List<GPUDiagnostic> = emptyList(),
) {
    val recordingSeals: List<GPURecordingSeal> = immutableList(recordingSeals)
    val tasks: List<GPUTask> = immutableList(tasks)
    val dependencies: List<GPUTaskDependency> = immutableList(dependencies)
    val phaseOrder: List<GPUTaskPhase> = immutableList(phaseOrder)
    val memoryBudget: GPUFrameMemoryBudgetPlan = memoryBudget.snapshotForFramePlan()
    val diagnostics: List<GPUDiagnostic> = immutableList(
        diagnostics.map { it.copy(facts = immutableMap(it.facts)) },
    )

    init {
        require(expectedReplayKeyHash.isNotBlank()) {
            "GPUTaskList.expectedReplayKeyHash must not be blank"
        }
        require(phaseOrder.distinct().size == phaseOrder.size) {
            "GPUTaskList.phaseOrder must not contain duplicates"
        }
        require(frameId == capabilitySeal.frameId) {
            "GPUTaskList.frameId must match GPUFrameCapabilitySeal.frameId"
        }
    }

    /** Returns stable task and dependency lines for tests and evidence bundles. */
    fun dumpLines(): List<String> =
        tasks.map { task -> task.dumpLine() } +
            dependencies.map { dependency -> dependency.dumpLine() }
}

/**
 * Dependency between planned tasks.
 *
 * Dependencies name only task IDs and stable use tokens. They order future
 * preparation/encoding work but do not represent completed GPU fences or
 * backend synchronization handles.
 */
data class GPUTaskDependency(
    val fromTaskId: GPUTaskID,
    val toTaskId: GPUTaskID,
    val dependencyKind: String,
    val useToken: GPUTaskUseToken? = null,
    val reasonCode: String,
)

/**
 * Diagnostic emitted by recording.
 *
 * The diagnostic is immutable evidence for recording, task, or replay refusal.
 * Terminal diagnostics stop the affected command or replay attempt; they do not
 * authorize hidden CPU rendering or backend submission.
 */
data class GPURecordingDiagnostic(
    val code: String,
    val recordingId: GPURecordingID? = null,
    val taskId: GPUTaskID? = null,
    val terminal: Boolean,
)

/** Builds a deterministic dump for this compatibility key. */
fun GPURecordingCompatibilityKey.dump(): GPURecordingCompatibilityKeyDump =
    GPURecordingCompatibilityKeyDump(
        keyHash = keyHash,
        lines = compatibilityKeyLines(
            commandShapeVersion = commandShapeVersion,
            dictionaryVersion = dictionaryVersion,
            runtimeRegistrySnapshot = runtimeRegistrySnapshot,
            capabilityClass = capabilityClass,
            targetFormatClass = targetFormatClass,
            resourceTopologyClass = resourceTopologyClass,
            replayPolicy = replayPolicy,
        ),
    )

private fun List<GPUFirstRoutePlan>.analysisDependencies(): List<GPUAnalysisDependency> =
    zipWithNext().mapIndexed { index, (from, to) ->
        GPUAnalysisDependency(
            fromRecordId = from.analysisRecord.recordId,
            toRecordId = to.analysisRecord.recordId,
            kind = "paint-order",
            barrierGeneration = index.toLong(),
            reasonCode = "preserve.paint.order",
        )
    }

private fun analysisDecisionDump(
    recordingId: GPURecordingID,
    plans: List<GPUFirstRoutePlan>,
): GPUAnalysisDecisionDump {
    val lines = plans.map { plan -> plan.analysisDecision.dumpLine() }
    return GPUAnalysisDecisionDump(
        dumpId = "analysis-decision.${recordingId.value}",
        recordingId = recordingId,
        decisionHash = stableHash(prefix = "analysis-decision", lines = lines),
        lines = lines,
    )
}

private fun GPUDrawAnalysisDecision.dumpLine(): String =
    when (this) {
        is GPUDrawAnalysisDecision.Candidate ->
            "decision:candidate:$recordId:$routeDecisionLabel"
        is GPUDrawAnalysisDecision.Cull ->
            "decision:cull:$recordId:$reasonCode"
        is GPUDrawAnalysisDecision.Discard ->
            "decision:discard:$recordId:$reasonCode"
        is GPUDrawAnalysisDecision.Refuse ->
            "decision:refuse:$recordId:${diagnostic.code}"
    }

private fun taskList(
    recordingId: GPURecordingID,
    frameId: GPUFrameID,
    deviceGeneration: GPUDeviceGenerationID,
    compatibilityKey: GPURecordingCompatibilityKey,
    capabilities: GPUCapabilities,
    plans: List<GPUFirstRoutePlan>,
): GPUTaskList {
    val tasks = plans.map { plan -> plan.task(recordingId = recordingId) }
    val dependencies = tasks
        .zipWithNext()
        .mapIndexed { index, (from, to) ->
            val renderPair = from is GPUTask.Render && to is GPUTask.Render
            GPUTaskDependency(
                fromTaskId = from.taskId,
                toTaskId = to.taskId,
                dependencyKind = if (renderPair) "render-order" else "task-order",
                useToken = GPUTaskUseToken(
                    "recording.${recordingId.value}.${if (renderPair) "render" else "task"}.$index->${index + 1}",
                ),
                reasonCode = "preserve.paint.order",
            )
        }

    val capabilitySeal = GPUFrameCapabilitySeal.capture(
        frameId = frameId,
        deviceGeneration = deviceGeneration,
        capabilities = capabilities,
    )
    return GPUTaskList(
        frameId = frameId,
        capabilitySeal = capabilitySeal,
        recordingSeals = listOf(
            GPURecordingSeal(
                recordingId = recordingId,
                insertionOrder = 0L,
                compatibilityKeyHash = compatibilityKey.keyHash,
                replayKeyHash = compatibilityKey.keyHash,
                capabilitySealHash = capabilitySeal.sealHash,
            ),
        ),
        expectedReplayKeyHash = compatibilityKey.keyHash,
        tasks = tasks,
        dependencies = dependencies,
        phaseOrder = GPUTaskPhase.entries,
        memoryBudget = emptyFrameMemoryBudget(),
        diagnostics = tasks.filterIsInstance<GPUTask.Refused>().map { task -> task.diagnostic },
    )
}

private fun GPUFirstRoutePlan.task(recordingId: GPURecordingID): GPUTask =
    when (val decision = analysisDecision) {
        is GPUDrawAnalysisDecision.Candidate ->
            GPUTask.Render(
                taskId = GPUTaskID("task.render.${analysisRecord.commandIdValue}"),
                recordingId = recordingId,
                phase = GPUTaskPhase.Render,
                target = GPUFrameTargetRef("frame.scene"),
                loadStore = GPULoadStorePlan(loadOp = "load", storePlan = GPUStorePlan.Store),
                samplePlan = GPUSamplePlan.SingleSampleFrame,
                provisionalSegmentKey = pass.provisionalSegmentKey,
                drawPackets = pass.drawPackets,
                batchEligibilityByPacketId = pass.batchEligibilityByPacketId,
            )
        is GPUDrawAnalysisDecision.Refuse -> {
            val taskId = GPUTaskID("task.refused.${analysisRecord.commandIdValue}")
            GPUTask.Refused(
                taskId = taskId,
                recordingId = recordingId,
                phase = GPUTaskPhase.Refusal,
                commandId = GPUDrawCommandID(analysisRecord.commandIdValue),
                scope = GPURefusalScope.RefusedLeafDrawStep,
                provenanceTokens = emptyList(),
                consumedChildTaskIds = emptyList(),
                diagnostic = decision.diagnostic.toCanonicalRecordingDiagnostic(),
            )
        }
        is GPUDrawAnalysisDecision.Cull ->
            analysisRecord.nonExecutableTask(recordingId = recordingId, code = decision.reasonCode)
        is GPUDrawAnalysisDecision.Discard ->
            analysisRecord.nonExecutableTask(recordingId = recordingId, code = decision.reasonCode)
    }

private fun GPUDrawAnalysisRecord.nonExecutableTask(
    recordingId: GPURecordingID,
    code: String,
): GPUTask.Refused {
    val taskId = GPUTaskID("task.refused.$commandIdValue")
    return GPUTask.Refused(
        taskId = taskId,
        recordingId = recordingId,
        phase = GPUTaskPhase.Refusal,
        commandId = GPUDrawCommandID(commandIdValue),
        scope = GPURefusalScope.RefusedLeafDrawStep,
        provenanceTokens = emptyList(),
        consumedChildTaskIds = emptyList(),
        diagnostic = GPUDiagnostic(
            code = GPUDiagnosticCode(code),
            domain = GPUDiagnosticDomain.Recording,
            severity = GPUDiagnosticSeverity.Error,
            message = code,
        ),
    )
}

private fun GPUTask.dumpLine(): String =
    when (this) {
        is GPUTask.Render ->
            "task:render:${taskId.value}:$passId:$analysisRecordId:" +
                (if (preMaterialization) "pre_materialization" else "materialized") +
                (sampleContinuationKey?.let { ":sampleContinuation=${it.stableLabel()}" } ?: "")
        is GPUTask.PrepareResources ->
            "task:prepare:${taskId.value}:${requests.joinToString(",") { it.resource.value }}"
        is GPUTask.Compute ->
            "task:compute:${taskId.value}:${dispatches.joinToString(",") { it.programKey.value }}"
        is GPUTask.Copy -> "task:copy:${taskId.value}:${source.value}->${destination.value}"
        is GPUTask.Upload -> "task:upload:${taskId.value}:${staging.value}->${destination.value}"
        is GPUTask.Barrier -> "task:barrier:${taskId.value}:$reasonCode"
        is GPUTask.DestinationSnapshots ->
            "task:destination-snapshots:${taskId.value}:${payload.grouping.groups.size}"
        is GPUTask.TargetTransition ->
            "task:target-transition:${taskId.value}:${transitionKind.name}"
        is GPUTask.Readback -> "task:readback:${taskId.value}:${request.requestId.value}"
        is GPUTask.Output -> "task:output:${taskId.value}:${descriptor.output.value}"
        is GPUTask.Refused -> "task:refused:${taskId.value}:${diagnostic.code.value}"
    }

private fun GPUSampleContinuationKey.stableLabel(): String =
    "${target.value}@${targetGeneration}:${deviceGeneration.value}:" +
        "${colorFormat.value}:${colorInterpretation.value}:${samplePlan.specializationKey}:" +
        "${colorAttachment.value}:${depthStencilAttachment?.value ?: "none"}"

private fun GPUTaskDependency.dumpLine(): String =
    "dependency:$dependencyKind:${fromTaskId.value}->${toTaskId.value}:${useToken?.value ?: "none"}"

private fun GPUAnalysisDiagnostic.toCanonicalRecordingDiagnostic(): GPUDiagnostic =
    GPUDiagnostic(
        code = GPUDiagnosticCode(code),
        domain = GPUDiagnosticDomain.Recording,
        severity = if (terminal) GPUDiagnosticSeverity.Error else GPUDiagnosticSeverity.Warning,
        message = message ?: code,
        facts = facts.toMap(),
        isTerminal = terminal,
    )

private fun emptyFrameMemoryBudget(): GPUFrameMemoryBudgetPlan =
    GPUFrameMemoryBudgetPlan(
        peakFrameTransientBytes = 0L,
        targetResidentBytes = 0L,
        categoryTotals = GPUFrameMemoryCategory.entries.associateWith { 0L },
        deviceLimitFacts = emptyList(),
        configuredAggregateBudgetBytes = 1L,
        diagnostic = null,
    )

private fun GPUFirstRoutePlan.routeDiagnostic(): String =
    when (val decision = routeDecision) {
        is GPURouteDecision.Native -> "route:${decision.route.consumerKind}"
        is GPURouteDecision.Prepared -> "route:${decision.route.consumerKind}"
        is GPURouteDecision.ReferenceOnly -> "reference:${decision.route.oracleName}"
        is GPURouteDecision.Refused -> "refused:${decision.diagnostic.code}"
    }

private fun compatibilityKey(
    commands: List<NormalizedDrawCommand>,
    capabilities: GPUCapabilities,
): GPURecordingCompatibilityKey {
    val lines = compatibilityKeyLines(
        commandShapeVersion = commandShapeVersionFirstRoute,
        dictionaryVersion = "material.dictionary.none",
        runtimeRegistrySnapshot = "runtime.registry.none",
        capabilityClass = capabilities.capabilityClass(),
        targetFormatClass = commands.targetFormatClass(),
        resourceTopologyClass = "pre_materialization.no_concrete_resources",
        replayPolicy = replayPolicyOneShot,
    )

    return GPURecordingCompatibilityKey(
        keyHash = stableHash(prefix = "recording-key", lines = lines),
        commandShapeVersion = commandShapeVersionFirstRoute,
        dictionaryVersion = "material.dictionary.none",
        runtimeRegistrySnapshot = "runtime.registry.none",
        capabilityClass = capabilities.capabilityClass(),
        targetFormatClass = commands.targetFormatClass(),
        resourceTopologyClass = "pre_materialization.no_concrete_resources",
        replayPolicy = replayPolicyOneShot,
    )
}

private fun compatibilityKeyLines(
    commandShapeVersion: Int,
    dictionaryVersion: String,
    runtimeRegistrySnapshot: String,
    capabilityClass: String,
    targetFormatClass: String,
    resourceTopologyClass: String,
    replayPolicy: String,
): List<String> =
    listOf(
        "commandShapeVersion=$commandShapeVersion",
        "dictionaryVersion=$dictionaryVersion",
        "runtimeRegistrySnapshot=$runtimeRegistrySnapshot",
        "capabilityClass=$capabilityClass",
        "targetFormatClass=$targetFormatClass",
        "resourceTopologyClass=$resourceTopologyClass",
        "replayPolicy=$replayPolicy",
    )

private fun List<NormalizedDrawCommand>.targetFormatClass(): String {
    val formats = map { command -> command.layer.target.colorFormat }.distinct().sorted()
    return when (formats.size) {
        0 -> "none"
        1 -> formats.single()
        else -> formats.joinToString("+")
    }
}

private fun GPUBounds.recordingBoundsHash(): String =
    "bounds:$left,$top,$right,$bottom"

private fun NormalizedDrawCommand.recordingTargetStateHash(): String =
    "target.${layer.target.colorFormat}.${layer.target.width}x${layer.target.height}"

private fun GPUCapabilities.capabilityClass(): String =
    facts
        .filter { fact -> fact.affectsValidity }
        .sortedBy { fact -> fact.name }
        .joinToString(",") { fact -> "${fact.name}=${fact.value}" }
        .ifEmpty { "none" }

private fun GPUCapabilities.featureAssumptions(): List<String> =
    facts
        .filter { fact -> fact.affectsValidity }
        .sortedBy { fact -> fact.name }
        .map { fact -> "capability:${fact.name}=${fact.value}" }

private fun stableHash(prefix: String, lines: List<String>): String =
    "$prefix:${Integer.toUnsignedString(lines.joinToString("|").hashCode(), 16)}"

private const val commandShapeVersionFirstRoute = 2
private const val replayPolicyOneShot = "one-shot"
