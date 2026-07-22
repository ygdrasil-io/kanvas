package org.graphiks.kanvas.surface.gpu

import java.util.concurrent.CompletionStage
import java.util.concurrent.CompletionException
import java.util.concurrent.ExecutionException
import java.util.concurrent.atomic.AtomicLong
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnostic
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticCode
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticDomain
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticSeverity
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeTelemetry
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendSession
import org.graphiks.kanvas.gpu.renderer.execution.GPUFrameImmediateState
import org.graphiks.kanvas.gpu.renderer.execution.GPUOffscreenTargetRequest
import org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedSceneFrameSession
import org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedSceneNativeCounters
import org.graphiks.kanvas.gpu.renderer.execution.GPUSceneFrameOutput
import org.graphiks.kanvas.gpu.renderer.execution.GPUSceneFrameOutputRequest
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameID
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackRequestID
import org.graphiks.kanvas.gpu.renderer.recording.GPURecordingID
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskList
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUFrameAttemptID
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUFrameStructuralOutcome

internal data class GPUPreparedSurfaceExecutionRequest(
    val candidate: GPUPreparedSurfaceEligibility.Candidate,
    val width: Int,
    val height: Int,
)

internal data class GPUPreparedSurfaceExecutionEvidence(
    val targetCreations: Long,
    val targetCloses: Long,
    val frameCoordinatorCreations: Long,
    val encoders: Long,
    val commandBuffers: Long,
    val submits: Long,
    val readbackCopies: Long,
    val destinationSnapshotCreations: Long,
    val destinationReadbackSnapshots: Long,
    val renderPasses: Long,
    val draws: Long,
    val drawIndexed: Long,
    val pipelineBinds: Long,
    val activeNativePayloads: Int,
    val outputOwnedNativePayloads: Int,
    val quarantinedNativePayloads: Int,
    val retentionRegistrations: Long,
    val retentionCompletions: Long,
    val retentionQuarantines: Long,
    val distinctRetentionTickets: Int,
)

internal sealed interface GPUPreparedSurfaceExecutionResult {
    data class BeforePreparedEntryRefused(val diagnostic: GPUDiagnostic) : GPUPreparedSurfaceExecutionResult

    class Succeeded(
        rgba: ByteArray,
        val visualOperationCount: Int,
        val stateEventCount: Int,
        val evidence: GPUPreparedSurfaceExecutionEvidence,
    ) : GPUPreparedSurfaceExecutionResult {
        private val ownedRgba = rgba.copyOf()
        val rgba: ByteArray get() = ownedRgba.copyOf()
    }

    data class TerminalFailure(val diagnostic: GPUDiagnostic) : GPUPreparedSurfaceExecutionResult
}

internal fun interface GPUPreparedSurfaceExecutionPort {
    fun execute(request: GPUPreparedSurfaceExecutionRequest): GPUPreparedSurfaceExecutionResult
}

internal fun interface GPUPreparedSurfaceBackendPortFactory {
    fun open(): GPUPreparedSurfaceBackendPort?
}

internal interface GPUPreparedSurfaceBackendPort : AutoCloseable {
    val capabilities: GPUCapabilities?
    val deviceGeneration: GPUDeviceGenerationID
    val runtimeTelemetry: GPUBackendRuntimeTelemetry
    fun prepare(request: GPUOffscreenTargetRequest): GPUPreparedSurfaceSessionPort
}

internal interface GPUPreparedSurfaceSessionPort : AutoCloseable {
    fun submit(taskList: GPUTaskList, readbackId: GPUReadbackRequestID): GPUPreparedSurfaceSubmission
    fun counters(): GPUPreparedSceneNativeCounters
}

internal sealed interface GPUPreparedSurfaceImmediateState {
    data class Refused(val diagnostic: GPUDiagnostic) : GPUPreparedSurfaceImmediateState
    data class FailedBeforeSubmit(val diagnostic: GPUDiagnostic) : GPUPreparedSurfaceImmediateState
    data object Submitted : GPUPreparedSurfaceImmediateState
    data class FailedAfterSubmit(val diagnostic: GPUDiagnostic) : GPUPreparedSurfaceImmediateState
}

internal data class GPUPreparedSurfaceSubmission(
    val attemptId: GPUFrameAttemptID,
    val immediateState: GPUPreparedSurfaceImmediateState,
    val completion: CompletionStage<GPUPreparedSurfaceCompletion>,
)

/** Handle-free description of the closed scene-frame output algebra. */
internal enum class GPUPreparedSurfaceOutputKind {
    Absent,
    CurrentFrameCompletionOnly,
    ReadbackRgba,
}

internal class GPUPreparedSurfaceCompletion(
    val attemptId: GPUFrameAttemptID,
    val outcome: GPUFrameStructuralOutcome,
    val diagnostic: GPUDiagnostic?,
    val outputKind: GPUPreparedSurfaceOutputKind,
    val readbackId: GPUReadbackRequestID?,
    rgba: ByteArray?,
) {
    private val ownedRgba = rgba?.copyOf()
    val rgba: ByteArray? get() = ownedRgba?.copyOf()
}

internal class GPUPreparedSurfaceFrameExecutor(
    private val backendFactory: GPUPreparedSurfaceBackendPortFactory,
    private val frameBuilder: (GPUPreparedSurfaceFrameBuildRequest) -> GPUPreparedSurfaceFrameBuildResult =
        GPUPreparedSurfaceFrameBuilder::build,
    private val ordinal: AtomicLong = sharedOrdinal,
) : GPUPreparedSurfaceExecutionPort {
    override fun execute(request: GPUPreparedSurfaceExecutionRequest): GPUPreparedSurfaceExecutionResult {
        val backend = try {
            backendFactory.open()
        } catch (_: Throwable) {
            null
        } ?: return beforeRefusal(
            "unavailable.surface.prepared.backend",
            "The prepared Surface backend is unavailable.",
        )

        var session: GPUPreparedSurfaceSessionPort? = null
        var primary: GPUPreparedSurfaceExecutionResult? = null
        var pendingSuccess: PendingPreparedSuccess? = null
        var postCloseCounters: GPUPreparedSceneNativeCounters? = null
        var postCloseTelemetry: GPUBackendRuntimeTelemetry? = null
        try {
            val capabilities = backend.capabilities
            if (capabilities == null) {
                primary = beforeRefusal(
                    "legacy.surface.prepared.runtime-capabilities-unavailable",
                    "The prepared Surface backend did not expose capabilities.",
                )
            } else {
                val frameOrdinal = ordinal.incrementAndGet()
                val target = GPUFrameTargetRef("surface-prepared-target-$frameOrdinal")
                val recordingId = GPURecordingID("surface-prepared-recording-$frameOrdinal")
                val frameId = GPUFrameID(frameOrdinal)
                val readbackId = GPUReadbackRequestID("surface-prepared-readback-$frameOrdinal")
                val build = frameBuilder(
                    GPUPreparedSurfaceFrameBuildRequest(
                        candidate = request.candidate,
                        targetFacts = GPUTargetFacts(
                            request.width,
                            request.height,
                            request.candidate.color.physicalFormat.value,
                        ),
                        targetBounds = GPUPixelBounds(0, 0, request.width, request.height),
                        capabilities = capabilities,
                        deviceGeneration = backend.deviceGeneration,
                        target = target,
                        recordingId = recordingId,
                        frameId = frameId,
                        readbackRequestId = readbackId,
                    ),
                )
                primary = when (build) {
                    is GPUPreparedSurfaceFrameBuildResult.Refused ->
                        GPUPreparedSurfaceExecutionResult.BeforePreparedEntryRefused(build.diagnostic)
                    is GPUPreparedSurfaceFrameBuildResult.Ready -> {
                        val prepared = executePrepared(
                            request = request,
                            backend = backend,
                            build = build,
                            openSession = { opened -> session = opened },
                            onSuccess = { pendingSuccess = it },
                        )
                        prepared
                    }
                }
            }
        } catch (failure: Throwable) {
            primary = terminal(
                "failed.surface.prepared.completion",
                "The prepared Surface frame completion failed.",
                mapOf("failureClass" to failure.javaClass.name),
            )
        } finally {
            val activeSession = session
            if (activeSession != null) {
                try {
                    activeSession.close()
                    postCloseCounters = activeSession.counters()
                } catch (failure: Throwable) {
                    val existingCode = primaryCode(primary)
                    primary = terminal(
                        "failed.surface.prepared.session-close",
                        "The prepared Surface session could not close cleanly.",
                        closeFacts(failure, existingCode),
                    )
                }
            }
            if (primary == null && pendingSuccess != null) {
                try {
                    postCloseTelemetry = backend.runtimeTelemetry
                } catch (failure: Throwable) {
                    primary = terminal(
                        "failed.surface.prepared.completion",
                        "The prepared Surface frame completion failed.",
                        mapOf("failureClass" to failure.javaClass.name),
                    )
                }
            }
            try {
                backend.close()
            } catch (failure: Throwable) {
                val existingCode = primaryCode(primary)
                primary = terminal(
                    "failed.surface.prepared.backend-close",
                    "The prepared Surface backend port could not close cleanly.",
                    closeFacts(failure, existingCode),
                )
            }
        }

        val current = primary
        val pending = pendingSuccess
        if (current == null && pending != null) {
            val closed = postCloseCounters ?: return terminal(
                "failed.surface.prepared.completion",
                "The prepared Surface frame completion failed.",
                mapOf("failureClass" to IllegalStateException::class.java.name),
            )
            val telemetry = postCloseTelemetry ?: return terminal(
                "failed.surface.prepared.completion",
                "The prepared Surface frame completion failed.",
                mapOf("failureClass" to IllegalStateException::class.java.name),
            )
            return finalizeSuccess(pending, closed, telemetry)
        }
        return current ?: terminal(
            "invalid.surface.prepared.terminal-without-diagnostic",
            "Prepared Surface execution failed without a terminal diagnostic.",
        )
    }

    private fun executePrepared(
        request: GPUPreparedSurfaceExecutionRequest,
        backend: GPUPreparedSurfaceBackendPort,
        build: GPUPreparedSurfaceFrameBuildResult.Ready,
        openSession: (GPUPreparedSurfaceSessionPort) -> Unit,
        onSuccess: (PendingPreparedSuccess) -> Unit,
    ): GPUPreparedSurfaceExecutionResult? {
        val expectedByteCount = try {
            Math.multiplyExact(Math.multiplyExact(request.width.toLong(), request.height.toLong()), 4L)
        } catch (failure: ArithmeticException) {
            return terminal(
                "invalid.surface.prepared.readback-size",
                "Prepared Surface dimensions do not fit the RGBA readback contract.",
                mapOf(
                    "width" to request.width.toString(),
                    "height" to request.height.toString(),
                    "failureClass" to failure.javaClass.name,
                ),
            )
        }
        val telemetryBefore = backend.runtimeTelemetry
        val session = try {
            backend.prepare(
                GPUOffscreenTargetRequest(
                    width = request.width,
                    height = request.height,
                    colorFormat = request.candidate.color.physicalFormat,
                    colorInterpretation = request.candidate.color.interpretation,
                ),
            )
        } catch (failure: Throwable) {
            return terminal(
                "failed.surface.prepared.session-prepare",
                "The prepared Surface session could not be created.",
                mapOf("failureClass" to failure.javaClass.name),
            )
        }
        openSession(session)
        val beforeSubmit = session.counters()
        val submission = try {
            session.submit(build.taskList, build.readbackRequestId)
        } catch (failure: Throwable) {
            return terminal(
                "failed.surface.prepared.completion",
                "The prepared Surface frame completion failed.",
                mapOf("failureClass" to failure.javaClass.name),
            )
        }
        val completion = try {
            submission.completion.toCompletableFuture().get()
        } catch (failure: Throwable) {
            val cause = unwrapCompletionFailure(failure)
            return terminal(
                "failed.surface.prepared.completion",
                "The prepared Surface frame completion failed.",
                mapOf("failureClass" to cause.javaClass.name),
            )
        }
        val afterCompletion = session.counters()

        if (submission.attemptId != completion.attemptId) {
            return terminal(
                "invalid.surface.prepared.attempt-identity",
                "Prepared Surface completion belongs to a different frame attempt.",
                mapOf("expected" to submission.attemptId.value, "actual" to completion.attemptId.value),
            )
        }
        validateImmediateCompletion(submission.immediateState, completion)?.let { return it }
        if (completion.outcome != GPUFrameStructuralOutcome.Succeeded) {
            return GPUPreparedSurfaceExecutionResult.TerminalFailure(
                completion.diagnostic
                    ?: immediateDiagnostic(submission.immediateState)
                    ?: diagnostic(
                        "invalid.surface.prepared.terminal-without-diagnostic",
                        "Prepared Surface execution failed without a terminal diagnostic.",
                    ),
            )
        }
        if (completion.outputKind != GPUPreparedSurfaceOutputKind.ReadbackRgba) {
            return terminal(
                "invalid.surface.prepared.readback-output",
                "Prepared Surface completion did not provide the requested RGBA readback.",
                mapOf(
                    "expected" to GPUPreparedSurfaceOutputKind.ReadbackRgba.name,
                    "actual" to completion.outputKind.name,
                ),
            )
        }
        if (completion.readbackId != build.readbackRequestId) {
            return terminal(
                "invalid.surface.prepared.readback-output",
                "Prepared Surface completion did not provide the requested RGBA readback.",
                buildMap {
                    put("expected", build.readbackRequestId.value)
                    completion.readbackId?.let { put("actual", it.value) }
                },
            )
        }
        val rgba = completion.rgba ?: return terminal(
            "invalid.surface.prepared.readback-output",
            "Prepared Surface completion did not provide the requested RGBA readback.",
            mapOf("expected" to build.readbackRequestId.value),
        )
        if (rgba.size.toLong() != expectedByteCount) {
            return terminal(
                "invalid.surface.prepared.readback-byte-count",
                "Prepared Surface readback byte count does not match the target.",
                mapOf("expected" to expectedByteCount.toString(), "actual" to rgba.size.toString()),
            )
        }
        onSuccess(
            PendingPreparedSuccess(
                rgba,
                build.visualOperationCount,
                build.stateEventCount,
                beforeSubmit,
                afterCompletion,
                telemetryBefore,
            ),
        )
        return null
    }

    private fun finalizeSuccess(
        pending: PendingPreparedSuccess,
        afterClose: GPUPreparedSceneNativeCounters,
        telemetryAfter: GPUBackendRuntimeTelemetry,
    ): GPUPreparedSurfaceExecutionResult {
        return try {
            check(pending.beforeSubmit.targetCreations == 1L && pending.beforeSubmit.targetCloses == 0L)
            check(pending.afterCompletion.targetCreations == 1L && pending.afterCompletion.targetCloses == 0L)
            check(afterClose.targetCreations == 1L && afterClose.targetCloses == 1L)
            check(afterClose.activeNativePayloads == 0)
            check(afterClose.outputOwnedNativePayloads == 0)
            check(afterClose.quarantinedNativePayloads == 0)
            check(afterClose.retentionRegistrations == afterClose.retentionCompletions)
            check(afterClose.retentionQuarantines == 0L)
            check(afterClose.distinctRetentionTickets == 1)

            val evidence = GPUPreparedSurfaceExecutionEvidence(
                targetCreations = afterClose.targetCreations,
                targetCloses = afterClose.targetCloses,
                frameCoordinatorCreations = delta(
                    pending.beforeSubmit.frameCoordinatorCreations,
                    pending.afterCompletion.frameCoordinatorCreations,
                ),
                encoders = delta(pending.beforeSubmit.encoders, pending.afterCompletion.encoders),
                commandBuffers = delta(pending.beforeSubmit.commandBuffers, pending.afterCompletion.commandBuffers),
                submits = delta(pending.beforeSubmit.submits, pending.afterCompletion.submits),
                readbackCopies = delta(pending.beforeSubmit.readbackCopies, pending.afterCompletion.readbackCopies),
                destinationSnapshotCreations = delta(
                    pending.beforeSubmit.destinationSnapshotCreations,
                    pending.afterCompletion.destinationSnapshotCreations,
                ),
                destinationReadbackSnapshots = delta(
                    pending.telemetryBefore.destinationReadbackSnapshots,
                    telemetryAfter.destinationReadbackSnapshots,
                ),
                renderPasses = delta(pending.beforeSubmit.renderPasses, pending.afterCompletion.renderPasses),
                draws = delta(pending.beforeSubmit.draws, pending.afterCompletion.draws),
                drawIndexed = delta(pending.beforeSubmit.drawIndexed, pending.afterCompletion.drawIndexed),
                pipelineBinds = delta(pending.beforeSubmit.pipelineBinds, pending.afterCompletion.pipelineBinds),
                activeNativePayloads = afterClose.activeNativePayloads,
                outputOwnedNativePayloads = afterClose.outputOwnedNativePayloads,
                quarantinedNativePayloads = afterClose.quarantinedNativePayloads,
                retentionRegistrations = afterClose.retentionRegistrations,
                retentionCompletions = afterClose.retentionCompletions,
                retentionQuarantines = afterClose.retentionQuarantines,
                distinctRetentionTickets = afterClose.distinctRetentionTickets,
            )
            check(evidence.frameCoordinatorCreations == 1L)
            check(evidence.encoders == 1L)
            check(evidence.commandBuffers == 1L)
            check(evidence.submits == 1L)
            check(evidence.readbackCopies == 1L)
            check(evidence.destinationSnapshotCreations == 0L)
            check(evidence.destinationReadbackSnapshots == 0L)
            GPUPreparedSurfaceExecutionResult.Succeeded(
                pending.rgba,
                pending.visualOperationCount,
                pending.stateEventCount,
                evidence,
            )
        } catch (failure: Throwable) {
            terminal(
                "failed.surface.prepared.completion",
                "The prepared Surface frame completion failed.",
                mapOf("failureClass" to failure.javaClass.name),
            )
        }
    }

    private fun validateImmediateCompletion(
        immediate: GPUPreparedSurfaceImmediateState,
        completion: GPUPreparedSurfaceCompletion,
    ): GPUPreparedSurfaceExecutionResult.TerminalFailure? {
        val consistent = when (immediate) {
            is GPUPreparedSurfaceImmediateState.Refused ->
                completion.outcome == GPUFrameStructuralOutcome.Refused && completion.diagnostic == immediate.diagnostic
            is GPUPreparedSurfaceImmediateState.FailedBeforeSubmit ->
                completion.outcome == GPUFrameStructuralOutcome.Failed && completion.diagnostic == immediate.diagnostic
            GPUPreparedSurfaceImmediateState.Submitted ->
                completion.outcome != GPUFrameStructuralOutcome.Refused
            is GPUPreparedSurfaceImmediateState.FailedAfterSubmit ->
                completion.outcome == GPUFrameStructuralOutcome.Failed && completion.diagnostic == immediate.diagnostic
        }
        if (consistent) return null
        val immediateDiagnostic = immediateDiagnostic(immediate)
        return terminal(
            "invalid.surface.prepared.immediate-completion",
            "Prepared Surface immediate and completed states are inconsistent.",
            buildMap {
                put("immediate", immediate::class.simpleName.orEmpty())
                put("outcome", completion.outcome.name)
                immediateDiagnostic?.let { put("immediateCode", it.code.value) }
                completion.diagnostic?.let { put("completionCode", it.code.value) }
            },
        )
    }

    private fun delta(before: Long, after: Long): Long = Math.subtractExact(after, before).also {
        check(it >= 0L)
    }

    private class PendingPreparedSuccess(
        rgba: ByteArray,
        val visualOperationCount: Int,
        val stateEventCount: Int,
        val beforeSubmit: GPUPreparedSceneNativeCounters,
        val afterCompletion: GPUPreparedSceneNativeCounters,
        val telemetryBefore: GPUBackendRuntimeTelemetry,
    ) {
        private val ownedRgba = rgba.copyOf()
        val rgba: ByteArray get() = ownedRgba.copyOf()
    }

    private companion object {
        val sharedOrdinal = AtomicLong(0L)
    }
}

private fun immediateDiagnostic(state: GPUPreparedSurfaceImmediateState): GPUDiagnostic? = when (state) {
    is GPUPreparedSurfaceImmediateState.Refused -> state.diagnostic
    is GPUPreparedSurfaceImmediateState.FailedBeforeSubmit -> state.diagnostic
    GPUPreparedSurfaceImmediateState.Submitted -> null
    is GPUPreparedSurfaceImmediateState.FailedAfterSubmit -> state.diagnostic
}

private fun unwrapCompletionFailure(failure: Throwable): Throwable = when (failure) {
    is ExecutionException, is CompletionException -> failure.cause ?: failure
    else -> failure
}

private fun primaryCode(result: GPUPreparedSurfaceExecutionResult?): String? = when (result) {
    null -> null
    is GPUPreparedSurfaceExecutionResult.BeforePreparedEntryRefused -> result.diagnostic.code.value
    is GPUPreparedSurfaceExecutionResult.TerminalFailure -> result.diagnostic.code.value
    is GPUPreparedSurfaceExecutionResult.Succeeded -> null
}

private fun closeFacts(failure: Throwable, primaryCode: String?): Map<String, String> = buildMap {
    put("failureClass", failure.javaClass.name)
    primaryCode?.let { put("primaryCode", it) }
}

private fun beforeRefusal(code: String, message: String) =
    GPUPreparedSurfaceExecutionResult.BeforePreparedEntryRefused(diagnostic(code, message))

private fun terminal(
    code: String,
    message: String,
    facts: Map<String, String> = emptyMap(),
) = GPUPreparedSurfaceExecutionResult.TerminalFailure(diagnostic(code, message, facts))

private fun diagnostic(
    code: String,
    message: String,
    facts: Map<String, String> = emptyMap(),
) = GPUDiagnostic(
    code = GPUDiagnosticCode(code),
    domain = GPUDiagnosticDomain.Execution,
    severity = GPUDiagnosticSeverity.Error,
    message = message,
    facts = facts,
)

internal object GPUPreparedSurfaceNativeBackendPortFactory : GPUPreparedSurfaceBackendPortFactory {
    override fun open(): GPUPreparedSurfaceBackendPort? =
        GPUBackendRuntimeFactory.createOrNull()?.let(::GPUPreparedSurfaceNativeBackendPort)
}

private class GPUPreparedSurfaceNativeBackendPort(
    private val session: GPUBackendSession,
) : GPUPreparedSurfaceBackendPort {
    override val capabilities: GPUCapabilities? get() = session.capabilities
    override val deviceGeneration: GPUDeviceGenerationID get() = session.deviceGeneration
    override val runtimeTelemetry: GPUBackendRuntimeTelemetry get() = session.runtimeTelemetry

    override fun prepare(request: GPUOffscreenTargetRequest): GPUPreparedSurfaceSessionPort =
        GPUPreparedSurfaceNativeSessionPort(session.prepareSceneFrameSession(request))

    override fun close() = session.close()
}

private class GPUPreparedSurfaceNativeSessionPort(
    private val session: GPUPreparedSceneFrameSession,
) : GPUPreparedSurfaceSessionPort {
    override fun submit(
        taskList: GPUTaskList,
        readbackId: GPUReadbackRequestID,
    ): GPUPreparedSurfaceSubmission {
        val handle = session.renderFrame(
            taskList,
            GPUSceneFrameOutputRequest.ReadbackRgba(readbackId),
        )
        val immediate = when (val state = handle.immediateState) {
            is GPUFrameImmediateState.Refused -> GPUPreparedSurfaceImmediateState.Refused(state.diagnostic)
            is GPUFrameImmediateState.FailedBeforeSubmit ->
                GPUPreparedSurfaceImmediateState.FailedBeforeSubmit(state.diagnostic)
            is GPUFrameImmediateState.Submitted -> GPUPreparedSurfaceImmediateState.Submitted
            is GPUFrameImmediateState.FailedAfterSubmit ->
                GPUPreparedSurfaceImmediateState.FailedAfterSubmit(state.diagnostic)
        }
        return GPUPreparedSurfaceSubmission(
            attemptId = handle.attemptId,
            immediateState = immediate,
            completion = handle.completion.thenApply { completed ->
                val output = completed.output as? GPUSceneFrameOutput.ReadbackRgba
                GPUPreparedSurfaceCompletion(
                    attemptId = completed.attemptId,
                    outcome = completed.outcome,
                    diagnostic = completed.diagnostic,
                    outputKind = when (completed.output) {
                        null -> GPUPreparedSurfaceOutputKind.Absent
                        GPUSceneFrameOutput.CurrentFrameCompletionOnly ->
                            GPUPreparedSurfaceOutputKind.CurrentFrameCompletionOnly
                        is GPUSceneFrameOutput.ReadbackRgba -> GPUPreparedSurfaceOutputKind.ReadbackRgba
                    },
                    readbackId = output?.requestId,
                    rgba = output?.bytes,
                )
            },
        )
    }

    override fun counters(): GPUPreparedSceneNativeCounters = session.nativeCounters()

    override fun close() = session.close()
}
