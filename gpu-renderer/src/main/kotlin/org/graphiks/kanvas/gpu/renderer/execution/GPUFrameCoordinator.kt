package org.graphiks.kanvas.gpu.renderer.execution

import java.util.Collections
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnostic
import org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlan
import org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlanner
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackRequestID
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskList
import org.graphiks.kanvas.gpu.renderer.recording.GPUTask
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUFrameAttemptID
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUFrameAttemptTelemetrySink
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUFrameStructuralEventKind
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUFrameStructuralOutcome
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUFrameStructuralPhase
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUFrameStructuralTelemetrySnapshot

/** Pure planning seam, hidden behind the coordinator product boundary. */
internal fun interface GPUFramePlanningPort {
    fun plan(taskList: GPUTaskList): GPUFramePlan
}

/** Transactional preflight seam, hidden behind the coordinator product boundary. */
internal fun interface GPUFramePreflightPort {
    fun preflight(framePlan: GPUFramePlan): GPUFramePreflightResult
}

/** Closed output algebra for completion, readback, or an opaque prepared window binding. */
sealed interface GPUSceneFrameOutputRequest {
    data object CurrentFrameCompletionOnly : GPUSceneFrameOutputRequest
    data class ReadbackRgba(val requestId: GPUReadbackRequestID) : GPUSceneFrameOutputRequest
    data class PresentToWindow(val preparedOutput: GPUPreparedWindowOutput) : GPUSceneFrameOutputRequest
}

/** Closed terminal output algebra. Readback bytes are defensively owned by this value. */
sealed interface GPUSceneFrameOutput {
    data object CurrentFrameCompletionOnly : GPUSceneFrameOutput

    class ReadbackRgba(
        val requestId: GPUReadbackRequestID,
        bytes: ByteArray,
    ) : GPUSceneFrameOutput {
        private val ownedBytes = bytes.copyOf()
        val bytes: ByteArray get() = ownedBytes.copyOf()
    }
}

/** Public completed product; executor seams and native handles never escape this value. */
class GPUPreparedSceneCompletedFrameResult internal constructor(
    val attemptId: GPUFrameAttemptID,
    val furthestPhase: GPUFrameStructuralPhase,
    val outcome: GPUFrameStructuralOutcome,
    val diagnostic: GPUDiagnostic?,
    val output: GPUSceneFrameOutput?,
    encodedScopeKinds: List<GPUEncoderOperationKind>,
    val telemetry: GPUFrameStructuralTelemetrySnapshot,
) {
    val encodedScopeKinds: List<GPUEncoderOperationKind> =
        Collections.unmodifiableList(ArrayList(encodedScopeKinds))
}

/** Public two-phase product returned only by the coordinator/session route. */
class GPUPreparedSceneFrameHandle internal constructor(
    val attemptId: GPUFrameAttemptID,
    val immediateState: GPUFrameImmediateState,
    val completion: CompletionStage<GPUPreparedSceneCompletedFrameResult>,
)

/** Sole planner -> preflight -> executor entry point for a prepared scene session. */
class GPUFrameCoordinator internal constructor(
    private val planner: GPUFramePlanningPort = GPUFramePlanningPort(GPUFramePlanner::plan),
    private val preflighter: GPUFramePreflightPort,
    private val executor: GPUFrameExecutionPort,
    private val attemptIdFactory: () -> GPUFrameAttemptID = ::nextGPUFrameAttemptID,
) {
    fun submit(
        taskList: GPUTaskList,
        outputRequest: GPUSceneFrameOutputRequest = GPUSceneFrameOutputRequest.CurrentFrameCompletionOnly,
    ): GPUPreparedSceneFrameHandle {
        val attemptId = attemptIdFactory()
        val telemetry = GPUFrameAttemptTelemetrySink(attemptId)
        telemetry.record(
            GPUFrameStructuralPhase.Recording,
            GPUFrameStructuralEventKind.AttemptStarted,
        )

        val plan = try {
            planner.plan(taskList)
        } catch (failure: Throwable) {
            return refused(
                attemptId,
                executionDiagnostic(
                    "failed.frame-coordinator.planning",
                    "Frame planner failed without a typed result.",
                    mapOf("failureClass" to failure::class.simpleName.orEmpty()),
                ),
                GPUFrameStructuralPhase.Planning,
                telemetry,
                GPUFrameStructuralEventKind.PlanningRefused,
                outputRequest,
            )
        }
        if (plan.atomicallyRefused) {
            val diagnostic = plan.diagnostics.firstOrNull { it.isTerminal }
                ?: executionDiagnostic(
                    "refused.frame-coordinator.planning",
                    "Frame planning refused without a terminal diagnostic.",
                )
            return refused(
                attemptId,
                diagnostic,
                GPUFrameStructuralPhase.Planning,
                telemetry,
                GPUFrameStructuralEventKind.PlanningRefused,
                outputRequest,
            )
        }
        telemetry.record(
            GPUFrameStructuralPhase.Planning,
            GPUFrameStructuralEventKind.PlanningAccepted,
        )

        val preflight = try {
            preflighter.preflight(plan)
        } catch (failure: Throwable) {
            GPUFramePreflightResult.Refused(
                executionDiagnostic(
                    "failed.frame-coordinator.preflight",
                    "Frame preflight failed without a typed result.",
                    mapOf("failureClass" to failure::class.simpleName.orEmpty()),
                ),
            )
        }
        return when (preflight) {
            is GPUFramePreflightResult.Refused -> refused(
                attemptId,
                preflight.diagnostic,
                GPUFrameStructuralPhase.Preflight,
                telemetry,
                GPUFrameStructuralEventKind.PreflightRefused,
                outputRequest,
            )
            is GPUFramePreflightResult.Prepared -> submitPrepared(
                preflight.frame,
                attemptId,
                telemetry,
                outputRequest,
            )
        }
    }

    private fun submitPrepared(
        frame: PreparedGPUFrame,
        attemptId: GPUFrameAttemptID,
        telemetry: GPUFrameAttemptTelemetrySink,
        outputRequest: GPUSceneFrameOutputRequest,
    ): GPUPreparedSceneFrameHandle {
        telemetry.record(
            GPUFrameStructuralPhase.Preflight,
            GPUFrameStructuralEventKind.PreflightAccepted,
        )
        val outputDiagnostic = validateOutput(frame, outputRequest)
        if (outputDiagnostic != null) {
            if (!frame.claimForRollback()) {
                return refused(
                    attemptId,
                    executionDiagnostic(
                        "failed.frame-coordinator.rollback-ownership",
                        "Prepared frame output refusal could not acquire exclusive rollback ownership.",
                    ),
                    GPUFrameStructuralPhase.Preflight,
                    telemetry,
                    GPUFrameStructuralEventKind.PreflightRefused,
                    outputRequest,
                )
            }
            frame.rollback.execute()
            return refused(
                attemptId,
                outputDiagnostic,
                GPUFrameStructuralPhase.Preflight,
                telemetry,
                GPUFrameStructuralEventKind.PreflightRefused,
                outputRequest,
            )
        }

        val execution = executor.execute(frame, attemptId, telemetry)
        return GPUPreparedSceneFrameHandle(
            attemptId = execution.attemptId,
            immediateState = execution.immediateState,
            completion = execution.completion.thenApply { result -> result.toProduct(outputRequest) },
        )
    }

    private fun validateOutput(
        frame: PreparedGPUFrame,
        outputRequest: GPUSceneFrameOutputRequest,
    ): GPUDiagnostic? {
        return when (outputRequest) {
            GPUSceneFrameOutputRequest.CurrentFrameCompletionOnly -> when {
                frame.hostActions.isNotEmpty() || frame.acquiredSurfaceOutput != null -> executionDiagnostic(
                    "invalid.frame-coordinator.completion-host-output",
                    "Completion-only output cannot retain surface host actions.",
                )
                frame.resources.outputOwnedReadbacks.isNotEmpty() -> executionDiagnostic(
                    "invalid.frame-coordinator.completion-output",
                    "Completion-only output cannot retain prepared readback outputs.",
                )
                else -> null
            }
            is GPUSceneFrameOutputRequest.ReadbackRgba -> {
                val planned = frame.resources.outputOwnedReadbacks.singleOrNull()
                when {
                    frame.hostActions.isNotEmpty() || frame.acquiredSurfaceOutput != null -> executionDiagnostic(
                        "invalid.frame-coordinator.readback-host-output",
                        "RGBA readback cannot retain surface host actions.",
                    )
                    planned == null -> executionDiagnostic(
                        "invalid.frame-coordinator.readback-output-missing",
                        "RGBA readback requires exactly one matching prepared output.",
                        mapOf("requestId" to outputRequest.requestId.value),
                    )
                    planned.request.requestId != outputRequest.requestId -> executionDiagnostic(
                        "invalid.frame-coordinator.readback-output-mismatch",
                        "RGBA readback request does not match the prepared output.",
                        mapOf(
                            "requested" to outputRequest.requestId.value,
                            "prepared" to planned.request.requestId.value,
                        ),
                    )
                    else -> null
                }
            }
            is GPUSceneFrameOutputRequest.PresentToWindow -> when {
                outputRequest.preparedOutput.isClosed -> executionDiagnostic(
                    "unsupported.frame-coordinator.window-output-closed",
                    "The prepared window output is closed.",
                )
                frame.resources.outputOwnedReadbacks.isNotEmpty() -> executionDiagnostic(
                    "invalid.frame-coordinator.window-readback-output",
                    "Window presentation cannot retain prepared readback outputs.",
                )
                frame.hostActions.map { it.kind } != listOf(
                    GPUHostActionKind.AcquireSurface,
                    GPUHostActionKind.Present,
                ) -> executionDiagnostic(
                    "invalid.frame-coordinator.window-host-actions",
                    "Window presentation requires exactly one acquire and one post-submit present action.",
                )
                frame.acquiredSurfaceOutput == null -> executionDiagnostic(
                    "invalid.frame-coordinator.window-output-missing",
                    "Window presentation requires one acquired surface output.",
                )
                !outputRequest.preparedOutput.matches(frame.acquiredSurfaceOutput) -> executionDiagnostic(
                    "stale.frame-coordinator.window-output",
                    "The acquired surface does not belong to the requested prepared window output generation.",
                )
                else -> null
            }
        }
    }

    private fun refused(
        attemptId: GPUFrameAttemptID,
        diagnostic: GPUDiagnostic,
        phase: GPUFrameStructuralPhase,
        telemetry: GPUFrameAttemptTelemetrySink,
        event: GPUFrameStructuralEventKind,
        outputRequest: GPUSceneFrameOutputRequest,
    ): GPUPreparedSceneFrameHandle {
        telemetry.record(phase, event)
        val snapshot = telemetry.seal(phase, GPUFrameStructuralOutcome.Refused, diagnostic.code.value)
        val completed = GPUPreparedSceneCompletedFrameResult(
            attemptId = attemptId,
            furthestPhase = phase,
            outcome = GPUFrameStructuralOutcome.Refused,
            diagnostic = diagnostic,
            output = null,
            encodedScopeKinds = emptyList(),
            telemetry = snapshot,
        )
        return GPUPreparedSceneFrameHandle(
            attemptId,
            GPUFrameImmediateState.Refused(diagnostic),
            CompletableFuture.completedFuture(completed),
        )
    }
}

/** Creates the complete frame-local coordinator stack for exactly one submitted frame. */
internal fun interface GPUFrameCoordinatorFactory {
    fun create(
        taskList: GPUTaskList,
        outputRequest: GPUSceneFrameOutputRequest,
    ): GPUFrameCoordinator
}

internal fun interface GPUPreparedSceneCompatibilityValidator {
    fun validate(taskList: GPUTaskList): GPUDiagnostic?
}

/** Handle-free native-work counters; no backend object escapes through this evidence surface. */
data class GPUPreparedSceneNativeCounters(
    val encoders: Long = 0L,
    val commandBuffers: Long = 0L,
    val targetCreations: Long = 0L,
    val targetCloses: Long = 0L,
    val targetNativeUses: Long = 0L,
    val submits: Long = 0L,
    val readbackCopies: Long = 0L,
    val activeNativePayloads: Int = 0,
    val outputOwnedNativePayloads: Int = 0,
    val quarantinedNativePayloads: Int = 0,
    val retentionRegistrations: Long = 0L,
    val retentionCompletions: Long = 0L,
    val retentionQuarantines: Long = 0L,
    val frameCoordinatorCreations: Long = 0L,
    val nativePayloadRegistrations: Long = 0L,
    val distinctRetentionTickets: Int = 0,
    val solidRectInvariantCreations: Long = 0L,
    val solidRectInvariantReuses: Long = 0L,
    val solidRectInvariantInvalidations: Long = 0L,
    val corePrimitiveInvariantCreations: Long = 0L,
    val corePrimitiveInvariantReuses: Long = 0L,
    val corePrimitiveInvariantInvalidations: Long = 0L,
    val registeredUniformInvariantCreations: Long = 0L,
    val registeredUniformInvariantReuses: Long = 0L,
    val separableBlurInvariantCreations: Long = 0L,
    val separableBlurInvariantReuses: Long = 0L,
    val separableBlurIntermediateCreations: Long = 0L,
    val separableBlurIntermediateReuses: Long = 0L,
    val destinationSnapshotCreations: Long = 0L,
    val destinationSnapshotReuses: Long = 0L,
    val colorGlyphInvariantCreations: Long = 0L,
    val colorGlyphAtlasCreations: Long = 0L,
    val colorGlyphAtlasUploads: Long = 0L,
    val colorGlyphAtlasReuses: Long = 0L,
    val colorGlyphAtlasInvalidations: Long = 0L,
    val colorGlyphCurrentAtlasBytes: Long = 0L,
    val colorGlyphPeakAtlasBytes: Long = 0L,
)

internal data class GPUPreparedSceneRenderCounters(
    val renderPasses: Long = 0L,
    val draws: Long = 0L,
    val drawIndexed: Long = 0L,
    val coverageMaskTextureCreations: Long = 0L,
    val coverageMaskSlotReuses: Long = 0L,
    val msaaColorTextureCreations: Long = 0L,
    val msaaColorSlotReuses: Long = 0L,
    val pathDepthStencilTextureCreations: Long = 0L,
    val pathDepthStencilSlotReuses: Long = 0L,
    val clipDepthStencilTextureCreations: Long = 0L,
    val clipDepthStencilSlotReuses: Long = 0L,
)

/** Reusable prepared session that owns target lifetime and serializes the sole coordinator route. */
class GPUPreparedSceneFrameSession internal constructor(
    private val coordinatorFactory: GPUFrameCoordinatorFactory,
    private val compatibilityValidator: GPUPreparedSceneCompatibilityValidator =
        GPUPreparedSceneCompatibilityValidator { null },
    private val closeAction: () -> Unit = {},
    private val attemptIdFactory: () -> GPUFrameAttemptID = ::nextGPUFrameAttemptID,
    private val nativeCountersFactory: () -> GPUPreparedSceneNativeCounters = { GPUPreparedSceneNativeCounters() },
    private val renderCountersFactory: () -> GPUPreparedSceneRenderCounters = {
        GPUPreparedSceneRenderCounters()
    },
    private val sceneTargetResolver: (GPUTaskList) -> GPUFrameTargetRef? = ::resolvePreparedSceneTarget,
) : AutoCloseable {
    private var state = State.Idle
    private var closeActionInProgress = false
    private var closeActionComplete = false

    internal constructor(coordinator: GPUFrameCoordinator) : this(
        coordinatorFactory = GPUFrameCoordinatorFactory { _, _ -> coordinator },
    )

    fun renderFrame(
        taskList: GPUTaskList,
        outputRequest: GPUSceneFrameOutputRequest = GPUSceneFrameOutputRequest.CurrentFrameCompletionOnly,
    ): GPUPreparedSceneFrameHandle {
        val submittedTaskList = when (outputRequest) {
            is GPUSceneFrameOutputRequest.PresentToWindow -> {
                outputRequest.preparedOutput.availabilityDiagnostic?.let { return localRefusal(it) }
                val sceneTarget = sceneTargetResolver(taskList) ?: return localRefusal(
                    "unsupported.prepared-scene-session.window-target-missing",
                    "Window presentation requires one canonical scene target.",
                )
                try {
                    outputRequest.preparedOutput.attachToFrame(taskList, sceneTarget)
                } catch (failure: Throwable) {
                    return localRefusal(
                        "unsupported.prepared-scene-session.window-output",
                        "The prepared window output is incompatible with this frame.",
                        mapOf("failureClass" to failure::class.simpleName.orEmpty()),
                    )
                }
            }
            else -> taskList
        }
        synchronized(this) {
            when (state) {
                State.Idle -> {
                    val compatibilityDiagnostic = compatibilityValidator.validate(submittedTaskList)
                    if (compatibilityDiagnostic != null) {
                        return localRefusal(compatibilityDiagnostic)
                    }
                    state = State.InFlight
                }
                State.InFlight -> return localRefusal(
                    "unsupported.prepared-scene-session.concurrent-frame",
                    "A prepared scene session accepts only one in-flight frame.",
                )
                State.CloseRequested,
                State.Closed,
                -> return localRefusal(
                    "unsupported.prepared-scene-session.closed",
                    "The prepared scene session is closed.",
                )
            }
        }

        val handle = try {
            coordinatorFactory.create(submittedTaskList, outputRequest).submit(submittedTaskList, outputRequest)
        } catch (failure: Throwable) {
            completeFrameState()
            return localRefusal(
                "failed.prepared-scene-session.coordinator",
                "The prepared scene session could not create or enter its frame-local coordinator.",
                mapOf("failureClass" to failure::class.simpleName.orEmpty()),
            )
        }
        handle.completion.whenComplete { _, _ -> completeFrameState() }
        return handle
    }

    /** Handle-free structural evidence for the reusable prepared-session route. */
    fun nativeCounters(): GPUPreparedSceneNativeCounters = nativeCountersFactory()

    internal fun renderCounters(): GPUPreparedSceneRenderCounters = renderCountersFactory()

    override fun close() {
        val closeNow = synchronized(this) {
            when (state) {
                State.Idle -> {
                    state = State.Closed
                    claimCloseAction()
                }
                State.InFlight -> {
                    state = State.CloseRequested
                    false
                }
                State.CloseRequested -> false
                State.Closed -> claimCloseAction()
            }
        }
        if (closeNow) runCloseAction()
    }

    private fun completeFrameState() {
        val closeNow = synchronized(this) {
            when (state) {
                State.InFlight -> {
                    state = State.Idle
                    false
                }
                State.CloseRequested -> {
                    state = State.Closed
                    claimCloseAction()
                }
                State.Idle,
                State.Closed,
                -> false
            }
        }
        if (closeNow) runCloseAction()
    }

    private fun claimCloseAction(): Boolean {
        if (closeActionComplete || closeActionInProgress) return false
        closeActionInProgress = true
        return true
    }

    private fun runCloseAction() {
        try {
            closeAction()
            synchronized(this) {
                closeActionComplete = true
                closeActionInProgress = false
            }
        } catch (failure: Throwable) {
            synchronized(this) { closeActionInProgress = false }
            throw failure
        }
    }

    private fun localRefusal(
        code: String,
        message: String,
        details: Map<String, String> = emptyMap(),
    ): GPUPreparedSceneFrameHandle = localRefusal(executionDiagnostic(code, message, details))

    private fun localRefusal(diagnostic: GPUDiagnostic): GPUPreparedSceneFrameHandle {
        val attemptId = attemptIdFactory()
        val telemetry = GPUFrameAttemptTelemetrySink(attemptId)
        telemetry.record(
            GPUFrameStructuralPhase.Preflight,
            GPUFrameStructuralEventKind.AttemptStarted,
        )
        telemetry.record(
            GPUFrameStructuralPhase.Preflight,
            GPUFrameStructuralEventKind.PreflightRefused,
        )
        val snapshot = telemetry.seal(
            GPUFrameStructuralPhase.Preflight,
            GPUFrameStructuralOutcome.Refused,
            diagnostic.code.value,
        )
        val completed = GPUPreparedSceneCompletedFrameResult(
            attemptId = attemptId,
            furthestPhase = GPUFrameStructuralPhase.Preflight,
            outcome = GPUFrameStructuralOutcome.Refused,
            diagnostic = diagnostic,
            output = null,
            encodedScopeKinds = emptyList(),
            telemetry = snapshot,
        )
        return GPUPreparedSceneFrameHandle(
            attemptId = attemptId,
            immediateState = GPUFrameImmediateState.Refused(diagnostic),
            completion = CompletableFuture.completedFuture(completed),
        )
    }

    private enum class State {
        Idle,
        InFlight,
        CloseRequested,
        Closed,
    }
}

private fun resolvePreparedSceneTarget(taskList: GPUTaskList): GPUFrameTargetRef? =
    taskList.tasks.filterIsInstance<GPUTask.PrepareResources>()
        .flatMap { it.requests }
        .singleOrNull { it.role == GPUFrameResourceRole.SceneTarget }
        ?.resource as? GPUFrameTargetRef

private fun GPUFrameExecutionCompletedResult.toProduct(
    outputRequest: GPUSceneFrameOutputRequest,
): GPUPreparedSceneCompletedFrameResult = GPUPreparedSceneCompletedFrameResult(
    attemptId = attemptId,
    furthestPhase = furthestPhase,
    outcome = outcome,
    diagnostic = diagnostic,
    output = if (outcome != GPUFrameStructuralOutcome.Succeeded) {
        null
    } else when (outputRequest) {
        GPUSceneFrameOutputRequest.CurrentFrameCompletionOnly -> GPUSceneFrameOutput.CurrentFrameCompletionOnly
        is GPUSceneFrameOutputRequest.ReadbackRgba -> {
            val completed = requireNotNull(readback) {
                "Successful readback execution must own terminal pixels"
            }
            require(completed.requestId == outputRequest.requestId) {
                "Successful readback execution must match the requested output"
            }
            GPUSceneFrameOutput.ReadbackRgba(completed.requestId, completed.bytes)
        }
        is GPUSceneFrameOutputRequest.PresentToWindow -> GPUSceneFrameOutput.CurrentFrameCompletionOnly
    },
    encodedScopeKinds = encodedScopeKinds,
    telemetry = telemetry,
)
