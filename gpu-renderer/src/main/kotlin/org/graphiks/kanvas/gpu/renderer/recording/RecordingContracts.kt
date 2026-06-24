package org.graphiks.kanvas.gpu.renderer.recording

import org.graphiks.kanvas.gpu.renderer.analysis.GPUDrawAnalysis
import org.graphiks.kanvas.gpu.renderer.analysis.GPUDrawAnalysisDecision
import org.graphiks.kanvas.gpu.renderer.analysis.GPUDrawAnalysisRecord
import org.graphiks.kanvas.gpu.renderer.analysis.GPUAnalysisDependency
import org.graphiks.kanvas.gpu.renderer.analysis.GPUAnalysisDiagnostic
import org.graphiks.kanvas.gpu.renderer.analysis.GPUFirstRoutePlan
import org.graphiks.kanvas.gpu.renderer.analysis.GPUFirstRoutePlanner
import org.graphiks.kanvas.gpu.renderer.analysis.SortKey
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.commands.GPUBounds
import org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand
import org.graphiks.kanvas.gpu.renderer.passes.GPUFirstRoutePassBuilder
import org.graphiks.kanvas.gpu.renderer.routing.GPUFirstRouteDecisionBuilder
import org.graphiks.kanvas.gpu.renderer.routing.GPURouteDecision
import org.graphiks.kanvas.font.atlas.GlyphAtlasUploadPlan
import org.graphiks.kanvas.gpu.renderer.analysis.GPUTextA8RoutePlanner
import org.graphiks.kanvas.gpu.renderer.text.GPUTextDiagnosticCodes
import org.graphiks.kanvas.gpu.renderer.text.GPUTextRouteDecision

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
    private val capabilities: GPUCapabilities,
    private val sharedScope: GPUSharedScope = GPUSharedScope("shared.default"),
    private val recorderScope: GPURecorderScope = GPURecorderScope("recorder.${recordingId.value}"),
    private val frameScope: GPUFrameScope = GPUFrameScope("frame.default"),
) {
    private val commands = mutableListOf<NormalizedDrawCommand>()
    private var closedRecording: GPURecording? = null

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
        val taskList = taskList(recordingId = recordingId, plans = plans)
        val compatibilityKey = compatibilityKey(commands = commands, capabilities = capabilities)
        val recording = GPURecording(
            recordingId = recordingId,
            compatibilityKey = compatibilityKey,
            analysis = analysis,
            analysisDecisionDump = analysisDecisionDump,
            analysisHash = analysisDecisionDump.decisionHash,
            taskList = taskList,
            routeDiagnostics = plans.map { plan -> plan.routeDiagnostic() },
            featureAssumptions = capabilities.featureAssumptions(),
        )

        closedRecording = recording
        return recording
    }

    private fun planCommand(command: NormalizedDrawCommand): GPUFirstRoutePlan =
        when (command) {
            is NormalizedDrawCommand.FillRect -> GPUFirstRoutePlanner(capabilities = capabilities).plan(command)
            is NormalizedDrawCommand.FillRRect -> GPUFirstRoutePlanner(capabilities = capabilities).plan(command)
            is NormalizedDrawCommand.DrawTextRun -> planDrawTextRun(command)
            is NormalizedDrawCommand.FillPath -> refusedFillPathPlan(command)
        }

    private fun planDrawTextRun(command: NormalizedDrawCommand.DrawTextRun): GPUFirstRoutePlan {
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

/** Task emitted by recording and planning. */
sealed interface GPUTask {
    /** Stable task identifier inside one recording. */
    val taskId: String

    /**
     * Pre-materialization render task for accepted draw work.
     *
     * The task references pass, analysis, render-step, and pipeline-key evidence
     * only. `preMaterialization` must stay true for this first slice, and
     * `materializedResourceLabels` must remain empty until resource preparation
     * work lands.
     */
    data class Render(
        override val taskId: String,
        val passId: String,
        val analysisRecordId: String,
        val routeDecisionLabel: String,
        val renderStepIds: List<String>,
        val pipelineKeyHashes: List<String>,
        val preMaterialization: Boolean,
        val materializedResourceLabels: List<String>,
    ) : GPUTask

    /** Resource preparation task. */
    data class PrepareResources(
        override val taskId: String,
        val resourcePlanLabels: List<String>,
    ) : GPUTask

    /** Draw pass task retained for package surface compatibility. */
    data class DrawPass(
        override val taskId: String,
        val passLabel: String,
    ) : GPUTask

    /** Compute task. */
    data class Compute(
        override val taskId: String,
        val programLabel: String,
    ) : GPUTask

    /** Copy task. */
    data class Copy(
        override val taskId: String,
        val copyLabel: String,
    ) : GPUTask

    /** Upload task. */
    data class Upload(
        override val taskId: String,
        val uploadLabel: String,
    ) : GPUTask

    /** Barrier task. */
    data class Barrier(
        override val taskId: String,
        val reasonCode: String,
    ) : GPUTask

    /** Refused task with terminal diagnostic evidence. */
    data class Refused(
        override val taskId: String,
        val diagnostic: GPURecordingDiagnostic,
    ) : GPUTask
}

/**
 * Ordered task list with dependency evidence.
 *
 * The list belongs to a closed recording. It may contain render, barrier,
 * upload/copy, and refused tasks, but the R5 first route emits only render or
 * refused tasks plus explicit ordering dependencies. Diagnostics are terminal
 * when a refused task blocks that command; they do not imply CPU fallback.
 */
data class GPUTaskList(
    val tasks: List<GPUTask>,
    val dependencies: List<GPUTaskDependency>,
    val phaseOrder: List<String>,
    val diagnostics: List<GPURecordingDiagnostic> = emptyList(),
) {
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
    val fromTaskId: String,
    val toTaskId: String,
    val dependencyKind: String,
    val useTokenLabel: String? = null,
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
    val taskId: String? = null,
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
    plans: List<GPUFirstRoutePlan>,
): GPUTaskList {
    val tasks = plans.map { plan -> plan.task(recordingId = recordingId) }
    val dependencies = tasks
        .filterIsInstance<GPUTask.Render>()
        .zipWithNext()
        .mapIndexed { index, (from, to) ->
            GPUTaskDependency(
                fromTaskId = from.taskId,
                toTaskId = to.taskId,
                dependencyKind = "render-order",
                useTokenLabel = "recording.${recordingId.value}.render.$index->${index + 1}",
                reasonCode = "preserve.paint.order",
            )
        }

    return GPUTaskList(
        tasks = tasks,
        dependencies = dependencies,
        phaseOrder = listOf("analysis", "task-list"),
        diagnostics = tasks.filterIsInstance<GPUTask.Refused>().map { task -> task.diagnostic },
    )
}

private fun GPUFirstRoutePlan.task(recordingId: GPURecordingID): GPUTask =
    when (val decision = analysisDecision) {
        is GPUDrawAnalysisDecision.Candidate ->
            GPUTask.Render(
                taskId = "task.render.${analysisRecord.commandIdValue}",
                passId = pass.passId,
                analysisRecordId = analysisRecord.recordId,
                routeDecisionLabel = decision.routeDecisionLabel,
                renderStepIds = pass.invocations.map { invocation -> invocation.renderStepId.value },
                pipelineKeyHashes = pass.pipelineKeys,
                preMaterialization = true,
                materializedResourceLabels = emptyList(),
            )
        is GPUDrawAnalysisDecision.Refuse -> {
            val taskId = "task.refused.${analysisRecord.commandIdValue}"
            GPUTask.Refused(
                taskId = taskId,
                diagnostic = GPURecordingDiagnostic(
                    code = decision.diagnostic.code,
                    recordingId = recordingId,
                    taskId = taskId,
                    terminal = decision.diagnostic.terminal,
                ),
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
    val taskId = "task.refused.$commandIdValue"
    return GPUTask.Refused(
        taskId = taskId,
        diagnostic = GPURecordingDiagnostic(
            code = code,
            recordingId = recordingId,
            taskId = taskId,
            terminal = true,
        ),
    )
}

private fun GPUTask.dumpLine(): String =
    when (this) {
        is GPUTask.Render ->
            "task:render:$taskId:$passId:$analysisRecordId:" +
                if (preMaterialization) "pre_materialization" else "materialized"
        is GPUTask.PrepareResources -> "task:prepare:$taskId:${resourcePlanLabels.joinToString(",")}"
        is GPUTask.DrawPass -> "task:draw-pass:$taskId:$passLabel"
        is GPUTask.Compute -> "task:compute:$taskId:$programLabel"
        is GPUTask.Copy -> "task:copy:$taskId:$copyLabel"
        is GPUTask.Upload -> "task:upload:$taskId:$uploadLabel"
        is GPUTask.Barrier -> "task:barrier:$taskId:$reasonCode"
        is GPUTask.Refused -> "task:refused:$taskId:${diagnostic.code}"
    }

private fun GPUTaskDependency.dumpLine(): String =
    "dependency:$dependencyKind:$fromTaskId->$toTaskId:${useTokenLabel ?: "none"}"

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
