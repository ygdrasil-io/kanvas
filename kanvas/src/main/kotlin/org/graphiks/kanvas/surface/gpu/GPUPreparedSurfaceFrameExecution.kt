package org.graphiks.kanvas.surface.gpu

import java.util.concurrent.CompletionStage
import java.util.concurrent.CompletionException
import java.util.concurrent.ExecutionException
import java.util.concurrent.atomic.AtomicLong
import org.graphiks.kanvas.canvas.ClipStack
import org.graphiks.kanvas.canvas.DisplayOp
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
import org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedTextFrameCounters
import org.graphiks.kanvas.gpu.renderer.execution.GPUSceneFrameOutput
import org.graphiks.kanvas.gpu.renderer.execution.GPUSceneFrameOutputRequest
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameID
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackRequestID
import org.graphiks.kanvas.gpu.renderer.recording.GPURecordingID
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskList
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUFrameAttemptID
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUFrameStructuralOutcome
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.types.Matrix33

internal data class GPUPreparedSurfaceExecutionRequest(
    val candidate: GPUPreparedSurfaceEligibility.Candidate,
    val width: Int,
    val height: Int,
    val output: GPUPreparedSurfaceRequestedOutput = GPUPreparedSurfaceRequestedOutput.ReadbackRgba,
)

internal enum class GPUPreparedSurfaceRequestedOutput {
    CompletionOnly,
    ReadbackRgba,
}

internal enum class GPUPreparedSurfaceExecutionRouteMarker(
    val stableLabel: String,
) {
    PreparedSurfaceDirect("prepared.surface.direct"),
}

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
    val routeMarker: GPUPreparedSurfaceExecutionRouteMarker,
    val textCounters: GPUPreparedTextFrameCounters = GPUPreparedTextFrameCounters(),
    val destinationCopies: Long = 0L,
    val destinationReadTextCommandIds: Set<Int> = emptySet(),
    val destinationReadEvidence: List<GPUPreparedSurfaceDestinationReadEvidence> =
        emptyList(),
)

internal sealed interface GPUPreparedSurfaceExecutionResult {
    data class BeforePreparedEntryRefused(val diagnostic: GPUDiagnostic) : GPUPreparedSurfaceExecutionResult

    class Succeeded(
        rgba: ByteArray,
        val visualOperationCount: Int,
        val stateEventCount: Int,
        val evidence: GPUPreparedSurfaceExecutionEvidence,
        val outputKind: GPUPreparedSurfaceOutputKind = GPUPreparedSurfaceOutputKind.ReadbackRgba,
    ) : GPUPreparedSurfaceExecutionResult {
        private val ownedRgba = rgba.copyOf()
        val rgba: ByteArray get() = ownedRgba.copyOf()
    }

    data class TerminalFailure(val diagnostic: GPUDiagnostic) : GPUPreparedSurfaceExecutionResult
}

internal fun interface GPUPreparedSurfaceExecutionPort {
    fun execute(request: GPUPreparedSurfaceExecutionRequest): GPUPreparedSurfaceExecutionResult
}

/** Pure proof for text-only no-op frames that need no backend or capability facts. */
internal object GPUPreparedSurfacePreBackendNoOpGate {
    fun classify(
        request: GPUPreparedSurfaceExecutionRequest,
    ): GPUPreparedSurfaceFrameBuildResult.NoOp? {
        if (request.width <= 0 || request.height <= 0) return null
        val acceptedTextOperationIndices = linkedSetOf<Int>()
        val elidedTextOperationIndices = linkedSetOf<Int>()
        val culledTextOperationIndices = linkedSetOf<Int>()
        var stateEventCount = 0
        request.candidate.operations.forEachIndexed { operationIndex, operation ->
            when (operation) {
                is DisplayOp.DrawText -> {
                    acceptedTextOperationIndices += operationIndex
                    if (operation.blob.glyphRuns.all { run -> run.glyphs.isEmpty() }) {
                        elidedTextOperationIndices += operationIndex
                    } else if (operation.hasConservativeTargetEmptyTextProof(
                            request.width,
                            request.height,
                        )
                    ) {
                        culledTextOperationIndices += operationIndex
                    } else {
                        return null
                    }
                }
                is DisplayOp.SetTransform,
                is DisplayOp.SetClip,
                is DisplayOp.Annotation,
                -> stateEventCount++
                else -> return null
            }
        }
        if (acceptedTextOperationIndices.isEmpty() ||
            acceptedTextOperationIndices !=
            elidedTextOperationIndices + culledTextOperationIndices
        ) return null
        return GPUPreparedSurfaceFrameBuildResult.NoOp(
            stateEventCount = stateEventCount,
            textMetrics = GPUPreparedTextFrameMetrics(
                glyphCount = 0,
                uniqueMaskCount = 0,
                instanceCount = 0,
                a8InstanceCount = 0,
                colorGlyphInstanceCount = 0,
                pathStrokeDrawCount = 0,
                subRunCount = 0,
                pageCount = 0,
                pageBytes = 0,
                instanceBytes = 0,
            ),
            acceptedTextOperationIndices = acceptedTextOperationIndices.toSet(),
            elidedTextOperationIndices = elidedTextOperationIndices.toSet(),
            culledTextOperationIndices = culledTextOperationIndices.toSet(),
        )
    }
}

internal fun DisplayOp.DrawText.hasConservativeTargetEmptyTextProof(
    width: Int,
    height: Int,
): Boolean {
    if (blob.typeface == null) return false
    if (blob.variationCoordinates.isNotEmpty() || !blob.fontSize.isFinite() || blob.fontSize <= 0f ||
        !x.isFinite() || !y.isFinite() || transform != Matrix33.identity()
    ) return false
    if (paint.blendMode != BlendMode.SRC_OVER || paint.style != PaintStyle.FILL ||
        paint.shader != null || paint.colorFilter != null || paint.maskFilter != null ||
        paint.pathEffect != null || paint.imageFilter != null || paint.blender != null
    ) return false
    if (!clip.isExactTargetEmptyDeviceRect(width, height)) return false
    return blob.glyphRuns.all { run ->
        run.glyphs.isNotEmpty() && run.glyphs.size == run.positions.size &&
            run.fontSize.isFinite() && run.fontSize > 0f &&
            run.positions.all { position -> position.x.isFinite() && position.y.isFinite() }
    }
}

private fun ClipStack.isExactTargetEmptyDeviceRect(width: Int, height: Int): Boolean {
    val deviceRect = this as? ClipStack.DeviceRect ?: return false
    val rect = deviceRect.rect
    val integral = listOf(rect.left, rect.top, rect.right, rect.bottom).all { value ->
        value.isFinite() && value % 1f == 0f
    }
    return !deviceRect.antiAlias && integral && (
        rect.isEmpty || rect.right <= 0f || rect.bottom <= 0f ||
            rect.left >= width.toFloat() || rect.top >= height.toFloat()
        )
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
    fun submitCompletionOnly(taskList: GPUTaskList): GPUPreparedSurfaceSubmission =
        throw UnsupportedOperationException("Completion-only prepared Surface output is unavailable.")
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
        GPUPreparedSurfaceFrameBuildSession()::build,
    private val ordinal: AtomicLong = sharedOrdinal,
) : GPUPreparedSurfaceExecutionPort {
    override fun execute(request: GPUPreparedSurfaceExecutionRequest): GPUPreparedSurfaceExecutionResult {
        GPUPreparedSurfacePreBackendNoOpGate.classify(request)?.let { noOp ->
            return completeNoOp(request, noOp)
        }
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
                    "unavailable.surface.prepared.runtime-capabilities",
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
                        includeReadback =
                            request.output == GPUPreparedSurfaceRequestedOutput.ReadbackRgba,
                    ),
                )
                primary = when (build) {
                    is GPUPreparedSurfaceFrameBuildResult.Refused ->
                        GPUPreparedSurfaceExecutionResult.BeforePreparedEntryRefused(build.diagnostic)
                    is GPUPreparedSurfaceFrameBuildResult.NoOp ->
                        completeNoOp(request, build)
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

    private fun completeNoOp(
        request: GPUPreparedSurfaceExecutionRequest,
        build: GPUPreparedSurfaceFrameBuildResult.NoOp,
    ): GPUPreparedSurfaceExecutionResult {
        val rgba = when (request.output) {
            GPUPreparedSurfaceRequestedOutput.CompletionOnly -> ByteArray(0)
            GPUPreparedSurfaceRequestedOutput.ReadbackRgba -> try {
                ByteArray(
                    Math.toIntExact(
                        Math.multiplyExact(
                            Math.multiplyExact(request.width.toLong(), request.height.toLong()),
                            4L,
                        ),
                    ),
                )
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
        }
        check(
            build.acceptedTextOperationIndices ==
                build.elidedTextOperationIndices + build.culledTextOperationIndices,
        )
        return GPUPreparedSurfaceExecutionResult.Succeeded(
            rgba = rgba,
            visualOperationCount = 0,
            stateEventCount = build.stateEventCount,
            evidence = GPUPreparedSurfaceExecutionEvidence(
                targetCreations = 0,
                targetCloses = 0,
                frameCoordinatorCreations = 0,
                encoders = 0,
                commandBuffers = 0,
                submits = 0,
                readbackCopies = 0,
                destinationSnapshotCreations = 0,
                destinationReadbackSnapshots = 0,
                renderPasses = 0,
                draws = 0,
                drawIndexed = 0,
                pipelineBinds = 0,
                activeNativePayloads = 0,
                outputOwnedNativePayloads = 0,
                quarantinedNativePayloads = 0,
                retentionRegistrations = 0,
                retentionCompletions = 0,
                retentionQuarantines = 0,
                distinctRetentionTickets = 0,
                routeMarker = GPUPreparedSurfaceExecutionRouteMarker.PreparedSurfaceDirect,
                textCounters = GPUPreparedTextFrameCounters(),
            ),
            outputKind = when (request.output) {
                GPUPreparedSurfaceRequestedOutput.CompletionOnly ->
                    GPUPreparedSurfaceOutputKind.CurrentFrameCompletionOnly
                GPUPreparedSurfaceRequestedOutput.ReadbackRgba ->
                    GPUPreparedSurfaceOutputKind.ReadbackRgba
            },
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
            when (request.output) {
                GPUPreparedSurfaceRequestedOutput.CompletionOnly ->
                    session.submitCompletionOnly(build.taskList)
                GPUPreparedSurfaceRequestedOutput.ReadbackRgba ->
                    session.submit(build.taskList, build.readbackRequestId)
            }
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
            val diagnostic = completion.diagnostic
                ?: immediateDiagnostic(submission.immediateState)
                ?: diagnostic(
                    "invalid.surface.prepared.terminal-without-diagnostic",
                    "Prepared Surface execution failed without a terminal diagnostic.",
                )
            // Documented prepared-route residuals: shapes the prepared direct lane genuinely cannot
            // execute yet continue on the legacy route (which renders them with the GPU
            // copy-then-formula composer) instead of becoming terminal. Multi-key passes that mix
            // destination-reading keys with non-dst-reading keys cannot share one bind-group layout
            // (the dst-read fragment layout appends the snapshot texture and sampler), and
            // dst-read modes without a formula program have no shading pipeline; both are
            // classified for Task 6.
            if (completion.outcome == GPUFrameStructuralOutcome.Refused &&
                diagnostic.code.value in preparedRouteLegacyFallbackRefusalCodes
            ) {
                return GPUPreparedSurfaceExecutionResult.BeforePreparedEntryRefused(diagnostic)
            }
            return GPUPreparedSurfaceExecutionResult.TerminalFailure(diagnostic)
        }
        val rgba = when (request.output) {
            GPUPreparedSurfaceRequestedOutput.CompletionOnly -> {
                if (completion.outputKind != GPUPreparedSurfaceOutputKind.CurrentFrameCompletionOnly ||
                    completion.readbackId != null ||
                    completion.rgba != null
                ) {
                    return terminal(
                        "invalid.surface.prepared.completion-only-output",
                        "Prepared Surface completion did not provide completion-only output.",
                        mapOf(
                            "expected" to GPUPreparedSurfaceOutputKind.CurrentFrameCompletionOnly.name,
                            "actual" to completion.outputKind.name,
                        ),
                    )
                }
                ByteArray(0)
            }
            GPUPreparedSurfaceRequestedOutput.ReadbackRgba -> {
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
                val bytes = completion.rgba ?: return terminal(
                    "invalid.surface.prepared.readback-output",
                    "Prepared Surface completion did not provide the requested RGBA readback.",
                    mapOf("expected" to build.readbackRequestId.value),
                )
                if (bytes.size.toLong() != expectedByteCount) {
                    return terminal(
                        "invalid.surface.prepared.readback-byte-count",
                        "Prepared Surface readback byte count does not match the target.",
                        mapOf("expected" to expectedByteCount.toString(), "actual" to bytes.size.toString()),
                    )
                }
                bytes
            }
        }
        onSuccess(
            PendingPreparedSuccess(
                rgba,
                build.visualOperationCount,
                build.stateEventCount,
                completion.outputKind,
                build.textMetrics,
                build.textCommandIds,
                build.pathStrokeCommandIds,
                build.destinationReadTextCommandIds,
                build.destinationReadEvidence,
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
                textCounters = GPUPreparedTextFrameCounters(
                    a8Instances = pending.textMetrics.a8InstanceCount,
                    colorGlyphInstances = pending.textMetrics.colorGlyphInstanceCount,
                    pathStrokeDraws = pending.textMetrics.pathStrokeDrawCount,
                    pageCount = pending.textMetrics.pageCount,
                    pageBytes = pending.textMetrics.pageBytes,
                    subRuns = pending.textMetrics.subRunCount,
                    draws = Math.toIntExact(
                        pending.textCommandIds.sumOf { commandId ->
                            commandDelta(pending, commandId).totalDraws
                        },
                    ),
                    bindGroups = Math.toIntExact(
                        pending.textCommandIds.sumOf { commandId ->
                            commandDelta(pending, commandId).bindGroups
                        },
                    ),
                    submits = if (pending.textCommandIds.none { commandId ->
                            commandDelta(pending, commandId).totalDraws > 0L
                        }
                    ) {
                        0
                    } else {
                        Math.toIntExact(
                            delta(pending.beforeSubmit.submits, pending.afterCompletion.submits),
                        )
                    },
                ),
                routeMarker = GPUPreparedSurfaceExecutionRouteMarker.PreparedSurfaceDirect,
                destinationCopies = delta(
                    pending.beforeSubmit.destinationCopies,
                    pending.afterCompletion.destinationCopies,
                ),
                destinationReadTextCommandIds = pending.destinationReadTextCommandIds,
                destinationReadEvidence = pending.destinationReadEvidence,
            )
            check(evidence.frameCoordinatorCreations == 1L)
            check(evidence.encoders == 1L)
            check(evidence.commandBuffers == 1L)
            check(evidence.submits == 1L)
            check(
                evidence.readbackCopies ==
                    if (pending.outputKind == GPUPreparedSurfaceOutputKind.ReadbackRgba) 1L else 0L,
            )
            check(
                evidence.destinationSnapshotCreations ==
                    pending.destinationReadTextCommandIds.size.toLong(),
            )
            check(
                evidence.destinationCopies ==
                    pending.destinationReadTextCommandIds.size.toLong(),
            )
            check(
                evidence.destinationReadEvidence
                    .mapTo(linkedSetOf()) { route -> route.commandId } ==
                    pending.destinationReadTextCommandIds,
            )
            check(
                evidence.destinationReadEvidence.size.toLong() ==
                    evidence.destinationCopies,
            )
            check(evidence.destinationReadbackSnapshots == 0L)
            check(
                pending.pathStrokeCommandIds.count { commandId ->
                    commandDelta(pending, commandId).totalDraws > 0L
                } == evidence.textCounters.pathStrokeDraws,
            )
            GPUPreparedSurfaceExecutionResult.Succeeded(
                pending.rgba,
                pending.visualOperationCount,
                pending.stateEventCount,
                evidence,
                pending.outputKind,
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

    private fun commandDelta(
        pending: PendingPreparedSuccess,
        commandId: Int,
    ): org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedNativeCommandEncodingCounters {
        val before = pending.beforeSubmit.commandsByCommandId[commandId]
            ?: org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedNativeCommandEncodingCounters()
        val after = pending.afterCompletion.commandsByCommandId[commandId]
            ?: org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedNativeCommandEncodingCounters()
        return org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedNativeCommandEncodingCounters(
            draws = delta(before.draws, after.draws),
            drawIndexed = delta(before.drawIndexed, after.drawIndexed),
            bindGroups = delta(before.bindGroups, after.bindGroups),
        )
    }

    private class PendingPreparedSuccess(
        rgba: ByteArray,
        val visualOperationCount: Int,
        val stateEventCount: Int,
        val outputKind: GPUPreparedSurfaceOutputKind,
        val textMetrics: GPUPreparedTextFrameMetrics,
        val textCommandIds: Set<Int>,
        val pathStrokeCommandIds: Set<Int>,
        val destinationReadTextCommandIds: Set<Int>,
        val destinationReadEvidence: List<GPUPreparedSurfaceDestinationReadEvidence>,
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

/**
 * Prepared-route refusals that document shapes the prepared direct lane genuinely cannot execute
 * and must fall back to the legacy route instead of failing the frame: multi-key passes mixing
 * destination-reading keys with non-dst-reading keys (no shared bind-group layout),
 * destination-reading modes without a formula program, and the multi-render dst-copy shape
 * (destination pass, ordered snapshot copy, consuming pass). Task 6 classifies the residuals.
 *
 * The pre-3c mixed-lane dst-copy codes are deliberately NOT listed here. Verified empirically
 * (probes through the real builder/executor): `unsupported.prepared-surface.destination-copy`
 * requires a ColorGlyph destination-reading consumer, but every non-fixed-function text blend
 * (including all destination-read text) is refused earlier by the surface text preparer
 * (`invalid.preflight.text.blend`), so no buildable frame reaches the mixed-lane site; and
 * `destination-copy-semantic-shape` requires a destination-copy frame with non-admitted
 * semantics, but every mixed attempt (rect+vertices, rect+image, text+rect) is refused earlier
 * by the recording preflight (`invalid.preflight.core_primitive_direct_geometry_resources`,
 * `unsupported.preflight.sampled_image_unmaterialized`). Even a hypothetical frame reaching
 * those codes always contains a terminal-family operation (text/image/vertices), so the router
 * converts the fallback into Terminal anyway; no legacy-fated frame regressed.
 */
private val preparedRouteLegacyFallbackRefusalCodes = setOf(
    "unsupported.native-core-primitive.multi-key-component",
    "unsupported.native-core-primitive.dst-read-formula",
    "unsupported.native-core-primitive.multi-render-dst-copy",
    "unsupported.native-core-primitive.analytic-shape-multi-key",
)

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

    override fun submitCompletionOnly(taskList: GPUTaskList): GPUPreparedSurfaceSubmission {
        val handle = session.renderFrame(
            taskList,
            GPUSceneFrameOutputRequest.CurrentFrameCompletionOnly,
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
                    readbackId = null,
                    rgba = null,
                )
            },
        )
    }

    override fun counters(): GPUPreparedSceneNativeCounters = session.nativeCounters()

    override fun close() = session.close()
}
