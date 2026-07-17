package org.graphiks.kanvas.gpu.renderer.execution

import java.util.Collections
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnostic
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticCode
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticDomain
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticSeverity
import org.graphiks.kanvas.gpu.renderer.resources.GPUPreparedConcreteResourceRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUSceneTarget
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackRequestID
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUFrameAttemptID
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUFrameAttemptTelemetrySink
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUFrameStructuralEventKind
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUFrameStructuralCounter
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUFrameStructuralOutcome
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUFrameStructuralPhase
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUFrameStructuralTelemetrySnapshot

/** Opaque result of finishing the one frame command encoder. */
@JvmInline
internal value class GPUFrameCommandBuffer(val value: String) {
    init {
        require(value.isNotBlank()) { "GPUFrameCommandBuffer.value must not be blank" }
    }
}

/** Non-throwing pre-submit disposal result; failures retain backend ownership for retry. */
internal sealed interface GPUFrameDiscardResult {
    data object Discarded : GPUFrameDiscardResult
    data object AlreadyReleased : GPUFrameDiscardResult
    data class Failed(val failureClass: String) : GPUFrameDiscardResult
}

/** Backend-neutral encoder port used until the native runtime adopts this boundary. */
internal interface GPUFrameCommandEncoder {
    fun encode(
        scope: GPUCommandEncoderScopePlan,
        preparedFrame: PreparedGPUFrame,
        sceneTarget: GPUSceneTarget,
        nativeOperand: GPUPreparedNativeScopeOperand?,
    )

    fun finish(): GPUFrameCommandBuffer
    fun discard(): GPUFrameDiscardResult
}

/** Minimal one-encoder/one-submit backend port. It cannot select or change routes. */
internal interface GPUFrameEncodingBackend {
    val deviceGeneration: GPUDeviceGenerationID
    val encodingMode: GPUFrameEncodingMode
        get() = GPUFrameEncodingMode.Instrumented
    fun createCommandEncoder(label: String): GPUFrameCommandEncoder
    fun discard(commandBuffer: GPUFrameCommandBuffer): GPUFrameDiscardResult
    fun submit(commandBuffer: GPUFrameCommandBuffer)
}

internal enum class GPUFrameEncodingMode {
    Instrumented,
    NativeOperandsRequired,
}

/** Deep immutable registration retained after submit until the exact queue completion. */
internal class GPUFrameRetentionRegistration(
    val ticket: GPUQueueCompletionTicket,
    ordinaryConcreteResources: List<String>,
    commandLeaseIds: List<String>,
    outputReadbackReservationIds: List<String>,
) {
    val ordinaryConcreteResources: List<String> =
        Collections.unmodifiableList(ArrayList(ordinaryConcreteResources))
    val commandLeaseIds: List<String> = Collections.unmodifiableList(ArrayList(commandLeaseIds))
    val outputReadbackReservationIds: List<String> =
        Collections.unmodifiableList(ArrayList(outputReadbackReservationIds))
}

/** Lifetime port; arm failure quarantines submitted resources instead of rolling them back. */
internal interface GPUFrameResourceRetention {
    fun registerAfterSubmit(registration: GPUFrameRetentionRegistration)

    /** Releases completion-owned state; output readback leases remain output-owned. */
    fun complete(ticket: GPUQueueCompletionTicket, outcome: GPUQueueCompletionOutcome)

    /** Observer hook. The executor-owned ledger retains the registration even if this throws. */
    fun quarantine(registration: GPUFrameRetentionRegistration, diagnostic: GPUDiagnostic)
}

/** Closed result of notifying a retention observer after the conservative ledger was updated. */
internal sealed interface GPUFrameRetentionLedgerResult {
    data object Applied : GPUFrameRetentionLedgerResult
    data object RegistrationMissing : GPUFrameRetentionLedgerResult
    data class ObserverFailed(val failureClass: String) : GPUFrameRetentionLedgerResult
}

/**
 * Strong in-process ownership ledger. Entries are installed before observer calls and are removed
 * only after a successful completion notification; observer failures therefore fail closed.
 */
internal class GPUFrameRetentionLedger(private val observer: GPUFrameResourceRetention) {
    private val retained = linkedMapOf<GPUQueueCompletionTicketID, MutableList<GPUFrameRetentionRegistration>>()

    fun registerAfterSubmit(registration: GPUFrameRetentionRegistration): GPUFrameRetentionLedgerResult {
        val inserted = retainIfAbsent(registration)
        if (!inserted) return GPUFrameRetentionLedgerResult.Applied
        return notifyObserver { observer.registerAfterSubmit(registration) }
    }

    fun complete(
        registration: GPUFrameRetentionRegistration,
        outcome: GPUQueueCompletionOutcome,
    ): GPUFrameRetentionLedgerResult {
        val present = synchronized(this) {
            retained[registration.ticket.ticketId]?.any { it === registration } == true
        }
        if (!present) return GPUFrameRetentionLedgerResult.RegistrationMissing
        val result = notifyObserver { observer.complete(registration.ticket, outcome) }
        if (result == GPUFrameRetentionLedgerResult.Applied) {
            removeExact(registration)
        }
        return result
    }

    fun quarantine(
        registration: GPUFrameRetentionRegistration,
        diagnostic: GPUDiagnostic,
    ): GPUFrameRetentionLedgerResult {
        retainIfAbsent(registration)
        return notifyObserver { observer.quarantine(registration, diagnostic) }
    }

    @Synchronized
    fun retainedRegistration(ticketId: GPUQueueCompletionTicketID): GPUFrameRetentionRegistration? =
        retained[ticketId]?.firstOrNull()

    @Synchronized
    fun retainedRegistrations(ticketId: GPUQueueCompletionTicketID): List<GPUFrameRetentionRegistration> =
        Collections.unmodifiableList(ArrayList(retained[ticketId].orEmpty()))

    @Synchronized
    private fun retainIfAbsent(registration: GPUFrameRetentionRegistration): Boolean {
        val registrations = retained.getOrPut(registration.ticket.ticketId) { mutableListOf() }
        if (registrations.any { it === registration }) return false
        registrations += registration
        return true
    }

    @Synchronized
    private fun removeExact(registration: GPUFrameRetentionRegistration) {
        val ticketId = registration.ticket.ticketId
        val registrations = retained[ticketId] ?: return
        val exactIndex = registrations.indexOfFirst { it === registration }
        if (exactIndex >= 0) registrations.removeAt(exactIndex)
        if (registrations.isEmpty()) retained.remove(ticketId)
    }

    private inline fun notifyObserver(action: () -> Unit): GPUFrameRetentionLedgerResult = try {
        action()
        GPUFrameRetentionLedgerResult.Applied
    } catch (failure: Throwable) {
        GPUFrameRetentionLedgerResult.ObserverFailed(failure::class.simpleName.orEmpty())
    }
}

/** Immediate state returned synchronously to the render thread. */
sealed interface GPUFrameImmediateState {
    data class Refused(val diagnostic: GPUDiagnostic) : GPUFrameImmediateState
    data class FailedBeforeSubmit(val diagnostic: GPUDiagnostic) : GPUFrameImmediateState
    data class Submitted(val ticketId: GPUQueueCompletionTicketID) : GPUFrameImmediateState
    data class FailedAfterSubmit(
        val ticketId: GPUQueueCompletionTicketID,
        val diagnostic: GPUDiagnostic,
    ) : GPUFrameImmediateState
}

/** Only final result for an executed frame; telemetry is unavailable before this value exists. */
internal class GPUFrameExecutionCompletedResult(
    val attemptId: GPUFrameAttemptID,
    val furthestPhase: GPUFrameStructuralPhase,
    val outcome: GPUFrameStructuralOutcome,
    val diagnostic: GPUDiagnostic?,
    encodedScopeKinds: List<GPUEncoderOperationKind>,
    val telemetry: GPUFrameStructuralTelemetrySnapshot,
    val readback: GPUFrameExecutionReadback? = null,
) {
    val encodedScopeKinds: List<GPUEncoderOperationKind> =
        Collections.unmodifiableList(ArrayList(encodedScopeKinds))
}

/** Immutable terminal pixels produced only after map, depad, unmap, and output cleanup. */
internal class GPUFrameExecutionReadback(
    val requestId: GPUReadbackRequestID,
    bytes: ByteArray,
) {
    private val ownedBytes = bytes.copyOf()
    val bytes: ByteArray get() = ownedBytes.copyOf()
}

/** Two-phase frame handle: immediate submit/refusal facts plus exact asynchronous completion. */
internal class GPUFrameExecutionHandle(
    val attemptId: GPUFrameAttemptID,
    val immediateState: GPUFrameImmediateState,
    val completion: CompletionStage<GPUFrameExecutionCompletedResult>,
)

/** Injectable coordinator boundary used to prove that execution is called at most once. */
internal fun interface GPUFrameExecutionPort {
    fun execute(
        preparedFrame: PreparedGPUFrame,
        attemptId: GPUFrameAttemptID,
        telemetry: GPUFrameAttemptTelemetrySink,
    ): GPUFrameExecutionHandle
}

/** Canonical ordered frame executor for an already-prepared scene target. */
internal class GPUFrameExecutor(
    private val sceneTarget: GPUSceneTarget,
    private val backend: GPUFrameEncodingBackend,
    private val completion: GPUQueueCompletionAccess,
    retention: GPUFrameResourceRetention,
    private val readback: GPUFrameReadbackAccess = GPUFrameReadbackAccess.Unavailable,
    private val attemptIdFactory: () -> GPUFrameAttemptID = ::nextGPUFrameAttemptID,
) : GPUFrameExecutionPort {
    private val retentionLedger = GPUFrameRetentionLedger(retention)

    internal fun retainedRegistration(ticketId: GPUQueueCompletionTicketID): GPUFrameRetentionRegistration? =
        retentionLedger.retainedRegistration(ticketId)
    fun execute(preparedFrame: PreparedGPUFrame): GPUFrameExecutionHandle {
        val attemptId = attemptIdFactory()
        val telemetry = GPUFrameAttemptTelemetrySink(attemptId)
        telemetry.record(
            GPUFrameStructuralPhase.Preflight,
            GPUFrameStructuralEventKind.AttemptStarted,
        )
        return execute(preparedFrame, attemptId, telemetry)
    }

    override fun execute(
        preparedFrame: PreparedGPUFrame,
        attemptId: GPUFrameAttemptID,
        telemetry: GPUFrameAttemptTelemetrySink,
    ): GPUFrameExecutionHandle {
        if (!preparedFrame.claimForExecution()) {
            val diagnostic = executionDiagnostic(
                "failed.frame-execution.already-claimed",
                "Prepared frame execution ownership was already claimed.",
            )
            telemetry.record(
                GPUFrameStructuralPhase.Preflight,
                GPUFrameStructuralEventKind.PreflightRefused,
            )
            return completedFailure(
                attemptId,
                diagnostic,
                GPUFrameStructuralPhase.Preflight,
                telemetry,
                GPUFrameImmediateState.FailedBeforeSubmit(diagnostic),
            )
        }
        staleDiagnostic(preparedFrame)?.let { diagnostic ->
            preparedFrame.rollbackAfterExecutionClaim()
            telemetry.record(
                GPUFrameStructuralPhase.Preflight,
                GPUFrameStructuralEventKind.PreflightRefused,
            )
            return completedFailure(
                attemptId,
                diagnostic,
                GPUFrameStructuralPhase.Preflight,
                telemetry,
                GPUFrameImmediateState.FailedBeforeSubmit(diagnostic),
            )
        }

        if (backend.encodingMode == GPUFrameEncodingMode.NativeOperandsRequired && !preparedFrame.hasNativePayload) {
            preparedFrame.rollbackAfterExecutionClaim()
            val diagnostic = executionDiagnostic(
                "unsupported.native-frame-payload.ownership-missing",
                "Native encoding backend requires one rollback-owned prepared payload.",
            )
            telemetry.record(GPUFrameStructuralPhase.Preflight, GPUFrameStructuralEventKind.PreflightRefused)
            return completedFailure(
                attemptId,
                diagnostic,
                GPUFrameStructuralPhase.Preflight,
                telemetry,
                GPUFrameImmediateState.FailedBeforeSubmit(diagnostic),
            )
        }

        var consumedNativePayload: GPUPreparedNativeFramePayload? = null
        if (preparedFrame.hasNativePayload) {
            when (
                val consumption = preparedFrame.rollback.consumeNativePayload(
                    preparedFrame.nativePayloadIdentity(),
                )
            ) {
                is GPUPreparedNativeFrameConsumption.Consumed -> {
                    consumedNativePayload = consumption.payload
                }
                is GPUPreparedNativeFrameConsumption.Refused -> {
                    preparedFrame.rollbackAfterExecutionClaim()
                    val diagnostic = executionDiagnostic(
                        consumption.code,
                        "Prepared native frame payload could not be consumed safely.",
                    )
                    telemetry.record(
                        GPUFrameStructuralPhase.Preflight,
                        GPUFrameStructuralEventKind.PreflightRefused,
                    )
                    return completedFailure(
                        attemptId,
                        diagnostic,
                        GPUFrameStructuralPhase.Preflight,
                        telemetry,
                        GPUFrameImmediateState.FailedBeforeSubmit(diagnostic),
                    )
                }
            }
        }

        val preparedReadbackOutput = preparedFrame.resources.outputOwnedReadbacks.singleOrNull()
        val nativeReadbackOperand = consumedNativePayload?.scopeOperands
            ?.filterIsInstance<GPUPreparedNativeScopeOperand.Readback>()
            ?.singleOrNull()
        if ((preparedFrame.resources.outputOwnedReadbacks.isNotEmpty() && preparedReadbackOutput == null) ||
            (preparedReadbackOutput == null) != (nativeReadbackOperand == null)
        ) {
            preparedFrame.rollbackAfterExecutionClaim()
            val diagnostic = executionDiagnostic(
                "invalid.frame-readback.native-output-mismatch",
                "Prepared readback output must match exactly one typed native readback operand.",
            )
            return completedFailure(
                attemptId,
                diagnostic,
                GPUFrameStructuralPhase.Preflight,
                telemetry,
                GPUFrameImmediateState.FailedBeforeSubmit(diagnostic),
            )
        }

        fun safeReadbackLifecycle(
            operation: String,
            action: () -> GPUFrameReadbackLifecycleResult,
        ): GPUFrameReadbackLifecycleResult = try {
            action()
        } catch (failure: Throwable) {
            GPUFrameReadbackLifecycleResult.Refused(
                executionDiagnostic(
                    "failed.frame-readback.lifecycle-callback",
                    "Readback lifecycle callback failed after submission.",
                    mapOf(
                        "operation" to operation,
                        "failureClass" to failure::class.simpleName.orEmpty(),
                    ),
                ),
            )
        }

        val encodedKinds = mutableListOf<GPUEncoderOperationKind>()
        var activeEncoder: GPUFrameCommandEncoder? = null
        val commandBuffer = try {
            val encoder = backend.createCommandEncoder("frame.${preparedFrame.semanticPlan.frameId.value}")
            activeEncoder = encoder
            telemetry.record(
                GPUFrameStructuralPhase.Encoding,
                GPUFrameStructuralEventKind.EncoderCreated,
                counter = GPUFrameStructuralCounter.EncoderCreate,
            )
            preparedFrame.encoderPlan.scopes.forEachIndexed { index, scope ->
                encoder.encode(
                    scope,
                    preparedFrame,
                    sceneTarget,
                    consumedNativePayload?.scopeOperands?.get(index),
                )
                encodedKinds += scope.operationKind
                telemetry.record(
                    GPUFrameStructuralPhase.Encoding,
                    GPUFrameStructuralEventKind.ScopeEncoded,
                    counter = GPUFrameStructuralCounter.EncoderScope,
                    label = scope.scopeLabel,
                )
            }
            encoder.finish().also {
                activeEncoder = null
                telemetry.record(
                    GPUFrameStructuralPhase.Encoding,
                    GPUFrameStructuralEventKind.EncoderFinished,
                    counter = GPUFrameStructuralCounter.EncoderFinish,
                )
            }
        } catch (failure: Throwable) {
            val discard = activeEncoder?.safeDiscard() ?: GPUFrameDiscardResult.AlreadyReleased
            preparedFrame.rollbackAfterExecutionClaim()
            val diagnostic = executionDiagnostic(
                "failed.frame-execution.encode",
                "Frame encoding failed before submission.",
                mapOf(
                    "failureClass" to failure::class.simpleName.orEmpty(),
                    "encoderDiscard" to discard.dumpLabel(),
                ),
            )
            return completedFailure(
                attemptId,
                diagnostic,
                GPUFrameStructuralPhase.Encoding,
                telemetry,
                GPUFrameImmediateState.FailedBeforeSubmit(diagnostic),
                encodedKinds,
            )
        }

        val ticket = preparedFrame.completionTicket
        val registration = preparedFrame.retentionRegistration()
        when (val submitOwnership = preparedFrame.rollback.enterSubmit()) {
            GPUPreparedNativeFrameBindingResult.Ready -> Unit
            is GPUPreparedNativeFrameBindingResult.Refused -> {
                val discard = backend.safeDiscard(commandBuffer)
                preparedFrame.rollbackAfterExecutionClaim()
                val diagnostic = executionDiagnostic(
                    submitOwnership.code,
                    submitOwnership.message,
                    mapOf("commandBufferDiscard" to discard.dumpLabel()),
                )
                return completedFailure(
                    attemptId,
                    diagnostic,
                    GPUFrameStructuralPhase.Encoding,
                    telemetry,
                    GPUFrameImmediateState.FailedBeforeSubmit(diagnostic),
                    encodedKinds,
                )
            }
        }
        try {
            backend.submit(commandBuffer)
            telemetry.record(
                GPUFrameStructuralPhase.Submitted,
                GPUFrameStructuralEventKind.QueueSubmitted,
                counter = GPUFrameStructuralCounter.QueueSubmit,
            )
        } catch (failure: Throwable) {
            val diagnostic = executionDiagnostic(
                "failed.frame-execution.submit",
                "Frame submission failed synchronously.",
                mapOf("failureClass" to failure::class.simpleName.orEmpty()),
            )
            retentionLedger.quarantine(registration, diagnostic)
            preparedFrame.rollback.quarantineNativeAfterSubmit()
            runCatching { completion.cancel(ticket) }
            telemetry.record(
                GPUFrameStructuralPhase.Submitted,
                GPUFrameStructuralEventKind.QueueSubmitFailed,
            )
            return completedFailure(
                attemptId,
                diagnostic,
                GPUFrameStructuralPhase.Submitted,
                telemetry,
                GPUFrameImmediateState.FailedAfterSubmit(ticket.ticketId, diagnostic),
                encodedKinds,
            )
        }

        if (preparedReadbackOutput != null) {
            when (
                val submitted = safeReadbackLifecycle("markSubmitted") {
                    readback.markSubmitted(
                        ticket,
                        preparedReadbackOutput,
                        requireNotNull(nativeReadbackOperand),
                    )
                }
            ) {
                GPUFrameReadbackLifecycleResult.Applied -> Unit
                is GPUFrameReadbackLifecycleResult.Refused -> {
                    retentionLedger.quarantine(registration, submitted.diagnostic)
                    preparedFrame.rollback.quarantineNativeAfterSubmit()
                    runCatching { completion.cancel(ticket) }
                    val snapshot = telemetry.seal(
                        GPUFrameStructuralPhase.Submitted,
                        GPUFrameStructuralOutcome.Failed,
                        submitted.diagnostic.code.value,
                    )
                    return GPUFrameExecutionHandle(
                        attemptId,
                        GPUFrameImmediateState.FailedAfterSubmit(ticket.ticketId, submitted.diagnostic),
                        CompletableFuture.completedFuture(
                            GPUFrameExecutionCompletedResult(
                                attemptId,
                                GPUFrameStructuralPhase.Submitted,
                                GPUFrameStructuralOutcome.Failed,
                                submitted.diagnostic,
                                encodedKinds,
                                snapshot,
                            ),
                        ),
                    )
                }
            }
        }

        val resultFuture = CompletableFuture<GPUFrameExecutionCompletedResult>()
        val immediate = GPUFrameImmediateState.Submitted(ticket.ticketId)
        val finalized = AtomicBoolean(false)
        val gpuDeliveryClaimed = AtomicBoolean(false)
        val readbackDeliveryClaimed = AtomicBoolean(false)
        val callbackLock = Any()
        var armReturned = false
        var pendingDelivery: GPUQueueCompletionDelivery.Accepted? = null

        fun finishTerminal(
            diagnostic: GPUDiagnostic?,
            completedReadback: GPUFrameExecutionReadback? = null,
        ) {
            if (!finalized.compareAndSet(false, true)) return
            val structuralOutcome = if (diagnostic == null) {
                GPUFrameStructuralOutcome.Succeeded
            } else {
                GPUFrameStructuralOutcome.Failed
            }
            telemetry.record(
                GPUFrameStructuralPhase.Completed,
                if (diagnostic == null) {
                    GPUFrameStructuralEventKind.CompletionSucceeded
                } else {
                    GPUFrameStructuralEventKind.CompletionFailed
                },
            )
            val snapshot = telemetry.seal(
                GPUFrameStructuralPhase.Completed,
                structuralOutcome,
                diagnostic?.code?.value,
            )
            resultFuture.complete(
                GPUFrameExecutionCompletedResult(
                    attemptId,
                    GPUFrameStructuralPhase.Completed,
                    structuralOutcome,
                    diagnostic,
                    encodedKinds,
                    snapshot,
                    completedReadback,
                ),
            )
        }

        fun finishReadback(delivery: GPUFrameReadbackMapDelivery) {
            if (!readbackDeliveryClaimed.compareAndSet(false, true)) return
            val expected = requireNotNull(preparedReadbackOutput)
            val operand = requireNotNull(nativeReadbackOperand)
            when (delivery) {
                is GPUFrameReadbackMapDelivery.Pixels -> {
                    val expectedBytes = expected.layout.width * expected.layout.height *
                        expected.layout.bytesPerPixel
                    var diagnostic = when {
                        delivery.requestId != expected.request.requestId -> executionDiagnostic(
                            "failed.frame-readback.request-mismatch",
                            "Mapped pixels belong to a different readback request.",
                        )
                        delivery.bytes.size != expectedBytes -> executionDiagnostic(
                            "failed.frame-readback.byte-count",
                            "Mapped readback byte count does not match the planned logical output.",
                            mapOf(
                                "expected" to expectedBytes.toString(),
                                "actual" to delivery.bytes.size.toString(),
                            ),
                        )
                        else -> null
                    }
                    val nativeReleased = preparedFrame.rollback.releaseNativeReadbackAfterOutput()
                    if (!nativeReleased) {
                        diagnostic = executionDiagnostic(
                            "failed.native-frame-payload.readback-release",
                            "Output-owned native readback payload could not be released after unmap.",
                        )
                        preparedFrame.rollback.quarantineNativeReadbackAfterOutput()
                    }
                    val finalizedPool = safeReadbackLifecycle("finalizeAfterNativeClose") {
                        readback.finalizeAfterNativeClose(
                            expected,
                            operand,
                            if (nativeReleased) {
                                GPUFrameReadbackNativeOutputSafety.Released
                            } else {
                                GPUFrameReadbackNativeOutputSafety.Quarantined
                            },
                        )
                    }
                    if (finalizedPool is GPUFrameReadbackLifecycleResult.Refused) {
                        diagnostic = finalizedPool.diagnostic
                    }
                    if (diagnostic == null) {
                        finishTerminal(
                            null,
                            GPUFrameExecutionReadback(delivery.requestId, delivery.bytes),
                        )
                    } else {
                        finishTerminal(diagnostic)
                    }
                }
                is GPUFrameReadbackMapDelivery.Failed -> {
                    val nativeReleased = when (delivery.safety) {
                        GPUFrameReadbackMapFailureSafety.SafeToRelease -> {
                            preparedFrame.rollback.releaseNativeReadbackAfterOutput().also { released ->
                                if (!released) preparedFrame.rollback.quarantineNativeReadbackAfterOutput()
                            }
                        }
                        GPUFrameReadbackMapFailureSafety.Quarantine -> {
                            preparedFrame.rollback.quarantineNativeReadbackAfterOutput()
                            false
                        }
                    }
                    val finalizedPool = safeReadbackLifecycle("finalizeAfterNativeClose") {
                        readback.finalizeAfterNativeClose(
                            expected,
                            operand,
                            if (nativeReleased) {
                                GPUFrameReadbackNativeOutputSafety.Released
                            } else {
                                GPUFrameReadbackNativeOutputSafety.Quarantined
                            },
                        )
                    }
                    if (finalizedPool is GPUFrameReadbackLifecycleResult.Refused) {
                        finishTerminal(finalizedPool.diagnostic)
                    } else {
                        finishTerminal(delivery.diagnostic)
                    }
                }
            }
        }

        fun completeDelivery(delivery: GPUQueueCompletionDelivery.Accepted) {
            if (!gpuDeliveryClaimed.compareAndSet(false, true)) return
            var diagnostic = if (delivery.ticketId != ticket.ticketId) {
                executionDiagnostic(
                    "failed.frame-execution.completion-ticket",
                    "Queue completion was delivered for a different ticket.",
                    mapOf(
                        "expected" to ticket.ticketId.value,
                        "actual" to delivery.ticketId.value,
                    ),
                )
            } else when (val outcome = delivery.outcome) {
                GPUQueueCompletionOutcome.Success -> null
                is GPUQueueCompletionOutcome.Failure -> executionDiagnostic(
                    "failed.frame-execution.queue-completion",
                    "Queue completion reported failure.",
                    buildMap {
                        put("kind", outcome.kind.name)
                        outcome.status?.let { put("status", it) }
                        outcome.message?.let { put("message", it) }
                    },
                )
            }
            if (diagnostic == null) {
                when (val release = retentionLedger.complete(registration, delivery.outcome)) {
                    GPUFrameRetentionLedgerResult.Applied -> {
                        if (preparedFrame.hasNativePayload &&
                            !preparedFrame.rollback.releaseNativeAfterCompletion()
                        ) {
                            diagnostic = executionDiagnostic(
                                "failed.native-frame-payload.release",
                                "Completed native frame payload could not be released safely.",
                            )
                            preparedFrame.rollback.quarantineNativeAfterSubmit()
                            retentionLedger.quarantine(registration, diagnostic)
                        }
                    }
                    GPUFrameRetentionLedgerResult.RegistrationMissing -> {
                        diagnostic = executionDiagnostic(
                            "failed.frame-execution.resource-lifetime",
                            "Completion-owned resources were missing from the strong retention ledger.",
                            mapOf("failureClass" to "RegistrationMissing"),
                        )
                        retentionLedger.quarantine(registration, diagnostic)
                    }
                    is GPUFrameRetentionLedgerResult.ObserverFailed -> {
                        diagnostic = executionDiagnostic(
                            "failed.frame-execution.resource-lifetime",
                            "Completion-owned resources could not be released safely.",
                            mapOf("failureClass" to release.failureClass),
                        )
                        retentionLedger.quarantine(registration, diagnostic)
                    }
                }
            } else {
                retentionLedger.quarantine(registration, diagnostic)
                preparedFrame.rollback.quarantineNativeAfterSubmit()
            }
            if (diagnostic != null) {
                preparedFrame.rollback.quarantineNativeAfterSubmit()
                if (preparedReadbackOutput != null) {
                    val failureKind = (delivery.outcome as? GPUQueueCompletionOutcome.Failure)?.kind
                        ?: GPUQueueCompletionFailureKind.CallbackFailure
                    val rejected = safeReadbackLifecycle("rejectGPUCompletion") {
                        readback.rejectGPUCompletion(
                            ticket,
                            preparedReadbackOutput,
                            requireNotNull(nativeReadbackOperand),
                            failureKind,
                        )
                    }
                    if (rejected is GPUFrameReadbackLifecycleResult.Refused) {
                        diagnostic = rejected.diagnostic
                    }
                }
            }
            if (diagnostic != null || preparedReadbackOutput == null) {
                finishTerminal(diagnostic)
                return
            }

            val completionAccepted = safeReadbackLifecycle("acceptGPUCompletion") {
                readback.acceptGPUCompletion(
                    ticket,
                    preparedReadbackOutput,
                    requireNotNull(nativeReadbackOperand),
                )
            }
            if (completionAccepted is GPUFrameReadbackLifecycleResult.Refused) {
                preparedFrame.rollback.quarantineNativeReadbackAfterOutput()
                finishTerminal(completionAccepted.diagnostic)
                return
            }
            if (!preparedFrame.rollback.claimNativeReadbackMapping()) {
                preparedFrame.rollback.quarantineNativeReadbackAfterOutput()
                val claimDiagnostic = executionDiagnostic(
                    "failed.native-frame-payload.readback-mapping-claim",
                    "Output-owned native readback payload could not be claimed for mapping.",
                )
                val finalizedPool = safeReadbackLifecycle("finalizeAfterNativeClose") {
                    readback.finalizeAfterNativeClose(
                        preparedReadbackOutput,
                        requireNotNull(nativeReadbackOperand),
                        GPUFrameReadbackNativeOutputSafety.Quarantined,
                    )
                }
                finishTerminal(
                    (finalizedPool as? GPUFrameReadbackLifecycleResult.Refused)?.diagnostic
                        ?: claimDiagnostic,
                )
                return
            }
            val mapResult = try {
                readback.mapAndDepad(
                    preparedReadbackOutput,
                    requireNotNull(nativeReadbackOperand),
                    GPUFrameReadbackMapSink(::finishReadback),
                )
            } catch (failure: Throwable) {
                GPUFrameReadbackMapArmResult.Refused(
                    executionDiagnostic(
                        "failed.frame-readback.map-arm",
                        "Readback mapping failed before returning a typed arm result.",
                        mapOf("failureClass" to failure::class.simpleName.orEmpty()),
                    ),
                )
            }
            if (mapResult is GPUFrameReadbackMapArmResult.Refused && !finalized.get()) {
                preparedFrame.rollback.quarantineNativeReadbackAfterOutput()
                finishTerminal(mapResult.diagnostic)
            }
        }

        when (val registrationResult = retentionLedger.registerAfterSubmit(registration)) {
            GPUFrameRetentionLedgerResult.Applied -> Unit
            GPUFrameRetentionLedgerResult.RegistrationMissing -> error(
                "registerAfterSubmit cannot return RegistrationMissing",
            )
            is GPUFrameRetentionLedgerResult.ObserverFailed -> {
                var diagnostic = executionDiagnostic(
                    "failed.frame-execution.resource-retention",
                    "Submitted frame resources could not be registered for completion retention.",
                    mapOf("failureClass" to registrationResult.failureClass),
                )
                retentionLedger.quarantine(registration, diagnostic)
                preparedFrame.rollback.quarantineNativeAfterSubmit()
                if (preparedReadbackOutput != null) {
                    val rejected = safeReadbackLifecycle("rejectGPUCompletion") {
                        readback.rejectGPUCompletion(
                            ticket,
                            preparedReadbackOutput,
                            requireNotNull(nativeReadbackOperand),
                            GPUQueueCompletionFailureKind.CallbackFailure,
                        )
                    }
                    if (rejected is GPUFrameReadbackLifecycleResult.Refused) {
                        diagnostic = rejected.diagnostic
                    }
                }
                runCatching { completion.cancel(ticket) }
                telemetry.record(
                    GPUFrameStructuralPhase.Submitted,
                    GPUFrameStructuralEventKind.CompletionArmFailed,
                )
                val snapshot = telemetry.seal(
                    GPUFrameStructuralPhase.Submitted,
                    GPUFrameStructuralOutcome.Failed,
                    diagnostic.code.value,
                )
                resultFuture.complete(
                    GPUFrameExecutionCompletedResult(
                        attemptId,
                        GPUFrameStructuralPhase.Submitted,
                        GPUFrameStructuralOutcome.Failed,
                        diagnostic,
                        encodedKinds,
                        snapshot,
                    ),
                )
                return GPUFrameExecutionHandle(
                    attemptId,
                    GPUFrameImmediateState.FailedAfterSubmit(ticket.ticketId, diagnostic),
                    resultFuture,
                )
            }
        }

        val armResult = try {
            completion.armAfterSubmit(ticket) { delivery ->
                val shouldComplete = synchronized(callbackLock) {
                    pendingDelivery = delivery
                    armReturned
                }
                if (shouldComplete) completeDelivery(delivery)
            }
        } catch (failure: Throwable) {
            GPUQueueCompletionArmResult.Refused(
                ticket.ticketId,
                GPUQueueCompletionFailureKind.CallbackFailure,
            )
        }

        val correctlyArmed = armResult is GPUQueueCompletionArmResult.Armed &&
            armResult.ticketId == ticket.ticketId
        if (correctlyArmed) {
            telemetry.record(
                GPUFrameStructuralPhase.Submitted,
                GPUFrameStructuralEventKind.CompletionArmed,
                counter = GPUFrameStructuralCounter.CompletionArm,
            )
            val deliveredDuringArm = synchronized(callbackLock) {
                armReturned = true
                pendingDelivery
            }
            deliveredDuringArm?.let(::completeDelivery)
        } else {
            synchronized(callbackLock) {
                armReturned = true
                gpuDeliveryClaimed.compareAndSet(false, true)
                finalized.compareAndSet(false, true)
            }
            var diagnostic = if (armResult is GPUQueueCompletionArmResult.Armed) {
                executionDiagnostic(
                    "failed.frame-execution.completion-arm-ticket",
                    "Submitted frame completion was armed for a different ticket.",
                    mapOf(
                        "expected" to ticket.ticketId.value,
                        "actual" to armResult.ticketId.value,
                    ),
                )
            } else {
                executionDiagnostic(
                    "failed.frame-execution.completion-arm",
                    "Submitted frame completion could not be armed.",
                    mapOf("armResult" to armResult::class.simpleName.orEmpty()),
                )
            }
            retentionLedger.quarantine(registration, diagnostic)
            preparedFrame.rollback.quarantineNativeAfterSubmit()
            if (preparedReadbackOutput != null) {
                val rejected = safeReadbackLifecycle("rejectGPUCompletion") {
                    readback.rejectGPUCompletion(
                        ticket,
                        preparedReadbackOutput,
                        requireNotNull(nativeReadbackOperand),
                        GPUQueueCompletionFailureKind.CallbackFailure,
                    )
                }
                if (rejected is GPUFrameReadbackLifecycleResult.Refused) {
                    diagnostic = rejected.diagnostic
                }
            }
            runCatching { completion.cancel(ticket) }
            telemetry.record(
                GPUFrameStructuralPhase.Submitted,
                GPUFrameStructuralEventKind.CompletionArmFailed,
            )
            val snapshot = telemetry.seal(
                GPUFrameStructuralPhase.Submitted,
                GPUFrameStructuralOutcome.Failed,
                diagnostic.code.value,
            )
            resultFuture.complete(
                GPUFrameExecutionCompletedResult(
                    attemptId,
                    GPUFrameStructuralPhase.Submitted,
                    GPUFrameStructuralOutcome.Failed,
                    diagnostic,
                    encodedKinds,
                    snapshot,
                ),
            )
        }

        return if (correctlyArmed) {
            GPUFrameExecutionHandle(attemptId, immediate, resultFuture)
        } else {
            val result = resultFuture.getNow(null)
            val diagnostic = requireNotNull(result?.diagnostic)
            GPUFrameExecutionHandle(
                attemptId,
                GPUFrameImmediateState.FailedAfterSubmit(ticket.ticketId, diagnostic),
                resultFuture,
            )
        }
    }

    private fun staleDiagnostic(frame: PreparedGPUFrame): GPUDiagnostic? {
        val base = when {
            frame.encoderPlan.contextIdentity != sceneTarget.targetId -> executionDiagnostic(
            "stale.frame-execution.target-identity",
            "Prepared frame target identity no longer matches the scene target.",
        )
            frame.generationSeal.deviceGeneration != backend.deviceGeneration -> executionDiagnostic(
            "stale.frame-execution.device-generation",
            "Prepared frame device generation no longer matches the backend.",
        )
            frame.generationSeal.deviceGeneration != sceneTarget.deviceGeneration -> executionDiagnostic(
            "stale.frame-execution.scene-device-generation",
            "Prepared frame device generation no longer matches the scene target.",
        )
            frame.generationSeal.targetGeneration != sceneTarget.targetGeneration -> executionDiagnostic(
            "stale.frame-execution.target-generation",
            "Prepared frame target generation no longer matches the scene target.",
        )
            else -> null
        }
        return base ?: msaaSceneTargetDiagnostic(frame)
    }

    private fun msaaSceneTargetDiagnostic(frame: PreparedGPUFrame): GPUDiagnostic? {
        val requests = frame.semanticPlan.steps
            .filterIsInstance<GPUFrameStep.RenderPassStep>()
            .mapNotNull(GPUFrameStep.RenderPassStep::sampleContinuation)
        if (requests.isEmpty()) return null
        val retained = sceneTarget.retainedMsaaAttachment ?: return executionDiagnostic(
            "unsupported.msaa.continuation_attachment_not_stored",
            "The scene target has no retained MSAA attachment authority.",
        )
        val key = requests.first().key
        return when {
            key.target.value != retained.targetId -> executionDiagnostic(
                "unsupported.msaa.continuation_target_identity",
                "The retained MSAA target identity does not match the prepared frame.",
            )
            key.deviceGeneration != retained.deviceGeneration -> executionDiagnostic(
                "unsupported.msaa.continuation_device_generation",
                "The retained MSAA device generation does not match the prepared frame.",
            )
            key.targetGeneration != retained.targetGeneration -> executionDiagnostic(
                "unsupported.msaa.continuation_target_generation",
                "The retained MSAA target generation does not match the prepared frame.",
            )
            key.samplePlan.sampleCount != retained.sampleCount -> executionDiagnostic(
                "unsupported.msaa.continuation_sample_plan",
                "The retained MSAA sample count does not match the prepared frame.",
            )
            key.colorFormat != retained.format ||
                key.colorInterpretation != retained.colorInterpretation -> executionDiagnostic(
                "unsupported.msaa.continuation_color_contract",
                "The retained MSAA color contract does not match the prepared frame.",
            )
            key.colorAttachment.value != retained.color.value ||
                key.depthStencilAttachment?.value != retained.depthStencil?.value -> executionDiagnostic(
                "unsupported.msaa.continuation_attachment_mismatch",
                "The retained MSAA attachment set does not match the prepared frame.",
            )
            else -> null
        }
    }
}

private fun GPUFrameCommandEncoder.safeDiscard(): GPUFrameDiscardResult = try {
    discard()
} catch (failure: Throwable) {
    GPUFrameDiscardResult.Failed(failure::class.simpleName.orEmpty())
}

private fun GPUFrameEncodingBackend.safeDiscard(
    commandBuffer: GPUFrameCommandBuffer,
): GPUFrameDiscardResult = try {
    discard(commandBuffer)
} catch (failure: Throwable) {
    GPUFrameDiscardResult.Failed(failure::class.simpleName.orEmpty())
}

private fun GPUFrameDiscardResult.dumpLabel(): String = when (this) {
    GPUFrameDiscardResult.Discarded -> "Discarded"
    GPUFrameDiscardResult.AlreadyReleased -> "AlreadyReleased"
    is GPUFrameDiscardResult.Failed -> "Failed"
}

private fun PreparedGPUFrame.nativePayloadIdentity(): GPUPreparedNativeFrameIdentity =
    GPUPreparedNativeFrameIdentity(
        frameId = semanticPlan.frameId,
        contextIdentity = encoderPlan.contextIdentity,
        encoderPlanId = encoderPlan.planId,
        deviceGeneration = generationSeal.deviceGeneration,
        targetGeneration = generationSeal.targetGeneration,
        scopes = encoderPlan.scopes.map { scope ->
            GPUPreparedNativeScopeKey(
                scope.sourceStepIndex,
                scope.operationKind,
                scope.resourceGenerationLabels,
                scope.nativeOperandKeys,
            )
        },
    )

private fun PreparedGPUFrame.retentionRegistration(): GPUFrameRetentionRegistration =
    GPUFrameRetentionRegistration(
        ticket = completionTicket,
        ordinaryConcreteResources = resources.ordinaryResources.map { resource ->
            when (val concrete = resource.concreteResource) {
                is GPUPreparedConcreteResourceRef.Texture -> concrete.ref.value
                is GPUPreparedConcreteResourceRef.Buffer -> concrete.ref.value
            }
        },
        commandLeaseIds = resources.commandResourceLeases.map { it.leaseId },
        outputReadbackReservationIds = resources.outputOwnedReadbacks.map { it.stagingLease.reservationId },
    )

private fun completedFailure(
    attemptId: GPUFrameAttemptID,
    diagnostic: GPUDiagnostic,
    phase: GPUFrameStructuralPhase,
    telemetry: GPUFrameAttemptTelemetrySink,
    immediateState: GPUFrameImmediateState,
    encodedKinds: List<GPUEncoderOperationKind> = emptyList(),
): GPUFrameExecutionHandle {
    val snapshot = telemetry.seal(phase, GPUFrameStructuralOutcome.Failed, diagnostic.code.value)
    val result = GPUFrameExecutionCompletedResult(
        attemptId,
        phase,
        GPUFrameStructuralOutcome.Failed,
        diagnostic,
        encodedKinds,
        snapshot,
    )
    return GPUFrameExecutionHandle(
        attemptId,
        immediateState,
        CompletableFuture.completedFuture(result),
    )
}

internal fun executionDiagnostic(
    code: String,
    message: String,
    facts: Map<String, String> = emptyMap(),
): GPUDiagnostic = GPUDiagnostic(
    code = GPUDiagnosticCode(code),
    domain = GPUDiagnosticDomain.Execution,
    severity = GPUDiagnosticSeverity.Error,
    message = message,
    facts = Collections.unmodifiableMap(LinkedHashMap(facts)),
)

private val nextAttemptOrdinal = AtomicLong(1L)

internal fun nextGPUFrameAttemptID(): GPUFrameAttemptID =
    GPUFrameAttemptID("frame-attempt.${nextAttemptOrdinal.getAndIncrement()}")
