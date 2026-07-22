package org.graphiks.kanvas.gpu.renderer.execution

import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackLayoutDepadder
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackDepadPlan

import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnostic
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackRequestID
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import io.ygdrasil.webgpu.GPUMapMode
import io.ygdrasil.webgpu.GPUTextureFormat
import kotlinx.coroutines.runBlocking
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourcePreflightProvider
import org.graphiks.kanvas.gpu.renderer.resources.GPUReadbackCompletionFailure
import org.graphiks.kanvas.gpu.renderer.resources.GPUReadbackMapFailureSafety
import org.graphiks.kanvas.gpu.renderer.resources.GPUReadbackStagingLifecycleResult
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceSubmissionID

/** Typed lifecycle result; no status string is interpreted by frame execution. */
internal sealed interface GPUFrameReadbackLifecycleResult {
    data object Applied : GPUFrameReadbackLifecycleResult
    data class Refused(val diagnostic: GPUDiagnostic) : GPUFrameReadbackLifecycleResult
}

/** Mapping is asynchronous and may complete after the exact queue-completion callback returns. */
internal sealed interface GPUFrameReadbackMapArmResult {
    data object Armed : GPUFrameReadbackMapArmResult
    data class Refused(val diagnostic: GPUDiagnostic) : GPUFrameReadbackMapArmResult
}

internal enum class GPUFrameReadbackMapFailureSafety {
    SafeToRelease,
    Quarantine,
}

internal enum class GPUFrameReadbackNativeOutputSafety {
    Released,
    Quarantined,
}

internal sealed interface GPUFrameReadbackMapDelivery {
    class Pixels(
        val requestId: GPUReadbackRequestID,
        bytes: ByteArray,
    ) : GPUFrameReadbackMapDelivery {
        private val ownedBytes = bytes.copyOf()
        val bytes: ByteArray get() = ownedBytes.copyOf()
    }

    data class Failed(
        val diagnostic: GPUDiagnostic,
        val safety: GPUFrameReadbackMapFailureSafety,
    ) : GPUFrameReadbackMapDelivery
}

internal fun interface GPUFrameReadbackMapSink {
    fun accept(delivery: GPUFrameReadbackMapDelivery)
}

/**
 * Exact output lifecycle boundary. Implementations bridge the concrete readback pool and the
 * native staging buffer; mapping must include depadding and guaranteed unmap before delivery.
 */
internal interface GPUFrameReadbackAccess {
    fun markSubmitted(
        ticket: GPUQueueCompletionTicket,
        output: GPUPreparedReadbackOutput,
        operand: GPUPreparedNativeScopeOperand.Readback,
    ): GPUFrameReadbackLifecycleResult

    fun acceptGPUCompletion(
        ticket: GPUQueueCompletionTicket,
        output: GPUPreparedReadbackOutput,
        operand: GPUPreparedNativeScopeOperand.Readback,
    ): GPUFrameReadbackLifecycleResult

    fun rejectGPUCompletion(
        ticket: GPUQueueCompletionTicket,
        output: GPUPreparedReadbackOutput,
        operand: GPUPreparedNativeScopeOperand.Readback,
        failure: GPUQueueCompletionFailureKind,
    ): GPUFrameReadbackLifecycleResult

    fun mapAndDepad(
        output: GPUPreparedReadbackOutput,
        operand: GPUPreparedNativeScopeOperand.Readback,
        sink: GPUFrameReadbackMapSink,
    ): GPUFrameReadbackMapArmResult

    fun finalizeAfterNativeClose(
        output: GPUPreparedReadbackOutput,
        operand: GPUPreparedNativeScopeOperand.Readback,
        safety: GPUFrameReadbackNativeOutputSafety,
    ): GPUFrameReadbackLifecycleResult

    data object Unavailable : GPUFrameReadbackAccess {
        private fun refused(): GPUFrameReadbackLifecycleResult.Refused =
            GPUFrameReadbackLifecycleResult.Refused(
                executionDiagnostic(
                    "unsupported.frame-readback.access-unavailable",
                    "Native readback lifecycle access is unavailable.",
                ),
            )

        override fun markSubmitted(
            ticket: GPUQueueCompletionTicket,
            output: GPUPreparedReadbackOutput,
            operand: GPUPreparedNativeScopeOperand.Readback,
        ): GPUFrameReadbackLifecycleResult = refused()

        override fun acceptGPUCompletion(
            ticket: GPUQueueCompletionTicket,
            output: GPUPreparedReadbackOutput,
            operand: GPUPreparedNativeScopeOperand.Readback,
        ): GPUFrameReadbackLifecycleResult = refused()

        override fun rejectGPUCompletion(
            ticket: GPUQueueCompletionTicket,
            output: GPUPreparedReadbackOutput,
            operand: GPUPreparedNativeScopeOperand.Readback,
            failure: GPUQueueCompletionFailureKind,
        ): GPUFrameReadbackLifecycleResult = refused()

        override fun mapAndDepad(
            output: GPUPreparedReadbackOutput,
            operand: GPUPreparedNativeScopeOperand.Readback,
            sink: GPUFrameReadbackMapSink,
        ): GPUFrameReadbackMapArmResult = GPUFrameReadbackMapArmResult.Refused(refused().diagnostic)

        override fun finalizeAfterNativeClose(
            output: GPUPreparedReadbackOutput,
            operand: GPUPreparedNativeScopeOperand.Readback,
            safety: GPUFrameReadbackNativeOutputSafety,
        ): GPUFrameReadbackLifecycleResult = refused()
    }
}

/** Mapped range remains valid until [unmap] and never escapes the lifecycle bridge. */
internal interface GPUFrameMappedReadbackRange {
    fun copyBytesFromZero(): ByteArray
    fun unmap()
}

internal sealed interface GPUFrameNativeReadbackMapDelivery {
    data class Mapped(val range: GPUFrameMappedReadbackRange) : GPUFrameNativeReadbackMapDelivery
    data class Failed(
        val diagnostic: GPUDiagnostic,
        val safety: GPUFrameReadbackMapFailureSafety,
    ) : GPUFrameNativeReadbackMapDelivery
}

internal fun interface GPUFrameNativeReadbackMapSink {
    fun accept(delivery: GPUFrameNativeReadbackMapDelivery)
}

/** Native mapper only establishes mapping; the concrete bridge owns read/depad/unmap/release. */
internal fun interface GPUFrameNativeReadbackMapper {
    fun map(
        output: GPUPreparedReadbackOutput,
        operand: GPUPreparedNativeScopeOperand.Readback,
        sink: GPUFrameNativeReadbackMapSink,
    ): GPUFrameReadbackMapArmResult
}

/** Concrete bridge from queue-ticket ownership to the existing output-owned physical pool. */
internal class GPUConcreteFrameReadbackAccess(
    private val provider: GPUFrameResourcePreflightProvider,
    private val mapper: GPUFrameNativeReadbackMapper,
) : GPUFrameReadbackAccess {
    private enum class TerminalState { Pixels, FailedSafe, FailedQuarantine }
    private class SubmissionRecord(
        val ticket: GPUQueueCompletionTicket,
        val submission: GPUResourceSubmissionID,
        val output: GPUPreparedReadbackOutput,
        val operand: GPUPreparedNativeScopeOperand.Readback,
        var terminalState: TerminalState? = null,
    )

    private val submissions = linkedMapOf<GPUQueueCompletionTicketID, SubmissionRecord>()
    private val ticketByReservation = linkedMapOf<String, GPUQueueCompletionTicketID>()

    override fun markSubmitted(
        ticket: GPUQueueCompletionTicket,
        output: GPUPreparedReadbackOutput,
        operand: GPUPreparedNativeScopeOperand.Readback,
    ): GPUFrameReadbackLifecycleResult {
        if (!matches(output, operand)) return refused(
            "failed.frame-readback.output-operand-mismatch",
            "Readback output and native operand identity do not match.",
        )
        val record = synchronized(this) {
            if (ticket.ticketId in submissions) return refused(
                "failed.frame-readback.duplicate-submission",
                "Readback ticket was already submitted.",
            )
            if (output.stagingLease.reservationId in ticketByReservation) return refused(
                "failed.frame-readback.duplicate-reservation",
                "Readback staging reservation is already owned by another ticket.",
            )
            SubmissionRecord(
                ticket,
                GPUResourceSubmissionID(GPUFrameReadbackSubmissionIds.next()),
                output,
                operand,
            ).also {
                submissions[ticket.ticketId] = it
                ticketByReservation[output.stagingLease.reservationId] = ticket.ticketId
            }
        }
        val result = providerTransition("markSubmitted") {
            provider.markReadbackSubmitted(listOf(output.stagingLease), record.submission)
        }
        return when (result) {
            is GPUReadbackStagingLifecycleResult.Accepted -> GPUFrameReadbackLifecycleResult.Applied
            is GPUReadbackStagingLifecycleResult.Refused -> failClosed(
                record,
                GPUFrameReadbackLifecycleResult.Refused(result.diagnostic),
            )
        }
    }

    override fun acceptGPUCompletion(
        ticket: GPUQueueCompletionTicket,
        output: GPUPreparedReadbackOutput,
        operand: GPUPreparedNativeScopeOperand.Readback,
    ): GPUFrameReadbackLifecycleResult {
        val record = synchronized(this) { submissions[ticket.ticketId] }
            ?: return refused(
                "failed.frame-readback.submission-missing",
                "Readback completion has no exact submitted pool entry.",
            )
        if (!record.matches(ticket, output, operand)) return refused(
            "failed.frame-readback.completion-identity-mismatch",
            "Readback completion does not match the exact submitted output and operand.",
        )
        val result = providerTransition("acceptGPUCompletion") {
            provider.acceptReadbackGPUCompletion(record.submission, ticket.deviceGeneration)
        }
        return when (result) {
            is GPUReadbackStagingLifecycleResult.Accepted -> GPUFrameReadbackLifecycleResult.Applied
            is GPUReadbackStagingLifecycleResult.Refused -> failClosed(
                record,
                GPUFrameReadbackLifecycleResult.Refused(result.diagnostic),
            )
        }
    }

    override fun rejectGPUCompletion(
        ticket: GPUQueueCompletionTicket,
        output: GPUPreparedReadbackOutput,
        operand: GPUPreparedNativeScopeOperand.Readback,
        failure: GPUQueueCompletionFailureKind,
    ): GPUFrameReadbackLifecycleResult {
        val record = synchronized(this) { submissions[ticket.ticketId] } ?: return refused(
                "failed.frame-readback.submission-missing",
                "Readback failure has no exact submitted pool entry.",
            )
        if (!record.matches(ticket, output, operand)) return refused(
            "failed.frame-readback.completion-identity-mismatch",
            "Readback failure does not match the exact submitted output and operand.",
        )
        val poolFailure = when (failure) {
            GPUQueueCompletionFailureKind.DeviceLost -> GPUReadbackCompletionFailure.DeviceLost
            GPUQueueCompletionFailureKind.CallbackFailure,
            GPUQueueCompletionFailureKind.AdapterClosed,
            GPUQueueCompletionFailureKind.Cancelled,
            -> GPUReadbackCompletionFailure.Uncertain
        }
        val result = providerTransition("rejectGPUCompletion") {
            provider.rejectReadbackGPUCompletion(
                record.submission,
                ticket.deviceGeneration,
                poolFailure,
            )
        }
        return when (result) {
            is GPUReadbackStagingLifecycleResult.Accepted -> {
                removeRecord(record)
                GPUFrameReadbackLifecycleResult.Applied
            }
            is GPUReadbackStagingLifecycleResult.Refused -> failClosed(
                record,
                GPUFrameReadbackLifecycleResult.Refused(result.diagnostic),
            )
        }
    }

    override fun mapAndDepad(
        output: GPUPreparedReadbackOutput,
        operand: GPUPreparedNativeScopeOperand.Readback,
        sink: GPUFrameReadbackMapSink,
    ): GPUFrameReadbackMapArmResult {
        val record = recordFor(output, operand) ?: return GPUFrameReadbackMapArmResult.Refused(
            refused(
                "failed.frame-readback.mapping-identity-mismatch",
                "Readback mapping does not match the exact submitted output and operand.",
            ).diagnostic,
        )
        val mappingDelivered = AtomicBoolean(false)
        val result = try {
            mapper.map(
                output,
                operand,
                GPUFrameNativeReadbackMapSink { delivery ->
                    if (!mappingDelivered.compareAndSet(false, true)) {
                        return@GPUFrameNativeReadbackMapSink
                    }
                    val forwarded = try {
                        when (delivery) {
                            is GPUFrameNativeReadbackMapDelivery.Mapped ->
                                terminalMapped(output, delivery.range)
                            is GPUFrameNativeReadbackMapDelivery.Failed -> terminalFailure(
                                GPUFrameReadbackMapDelivery.Failed(delivery.diagnostic, delivery.safety),
                            )
                        }
                    } catch (failure: Throwable) {
                        if (delivery is GPUFrameNativeReadbackMapDelivery.Mapped) {
                            runCatching { delivery.range.unmap() }
                        }
                        terminalCallbackFailure("terminal-callback", failure)
                    }
                    synchronized(this) {
                        record.terminalState = when (forwarded) {
                            is GPUFrameReadbackMapDelivery.Pixels -> TerminalState.Pixels
                            is GPUFrameReadbackMapDelivery.Failed -> when (forwarded.safety) {
                                GPUFrameReadbackMapFailureSafety.SafeToRelease -> TerminalState.FailedSafe
                                GPUFrameReadbackMapFailureSafety.Quarantine -> TerminalState.FailedQuarantine
                            }
                        }
                    }
                    try {
                        sink.accept(forwarded)
                    } catch (failure: Throwable) {
                        failClosed(record, lifecycleCallbackFailure("terminal-sink", failure))
                    }
                },
            )
        } catch (failure: Throwable) {
            GPUFrameReadbackMapArmResult.Refused(
                executionDiagnostic(
                    "failed.frame-readback.mapper-arm",
                    "Native readback mapper threw before returning a typed arm result.",
                    mapOf("failureClass" to failure::class.simpleName.orEmpty()),
                ),
            )
        }
        if (result !is GPUFrameReadbackMapArmResult.Refused) return result
        val cleanup = failClosed(
            record,
            GPUFrameReadbackLifecycleResult.Refused(result.diagnostic),
        )
        return GPUFrameReadbackMapArmResult.Refused(
            (cleanup as? GPUFrameReadbackLifecycleResult.Refused)?.diagnostic ?: result.diagnostic,
        )
    }

    override fun finalizeAfterNativeClose(
        output: GPUPreparedReadbackOutput,
        operand: GPUPreparedNativeScopeOperand.Readback,
        safety: GPUFrameReadbackNativeOutputSafety,
    ): GPUFrameReadbackLifecycleResult {
        val record = recordFor(output, operand) ?: return refused(
            "failed.frame-readback.finalize-identity-mismatch",
            "Readback finalization does not match the exact submitted output and operand.",
        )
        val terminal = synchronized(this) { record.terminalState }
        val result = providerTransition("finalizeAfterNativeClose") {
            when {
                terminal == TerminalState.Pixels && safety == GPUFrameReadbackNativeOutputSafety.Released ->
                    provider.releaseReadbackAfterUnmap(output.stagingLease)
                else -> provider.markReadbackMapFailed(
                    output.stagingLease,
                    if (safety == GPUFrameReadbackNativeOutputSafety.Released &&
                        terminal == TerminalState.FailedSafe
                    ) {
                        GPUReadbackMapFailureSafety.SafeUnmapAndReleaseProven
                    } else {
                        GPUReadbackMapFailureSafety.ReleaseUncertain
                    },
                )
            }
        }
        return when (result) {
            is GPUReadbackStagingLifecycleResult.Accepted -> {
                removeRecord(record)
                GPUFrameReadbackLifecycleResult.Applied
            }
            is GPUReadbackStagingLifecycleResult.Refused -> failClosed(
                record,
                GPUFrameReadbackLifecycleResult.Refused(result.diagnostic),
            )
        }
    }

    private fun terminalMapped(
        output: GPUPreparedReadbackOutput,
        range: GPUFrameMappedReadbackRange,
    ): GPUFrameReadbackMapDelivery {
        val terminal = try {
            when (val mapped = provider.markReadbackMapped(output.stagingLease)) {
                is GPUReadbackStagingLifecycleResult.Refused -> GPUFrameReadbackMapDelivery.Failed(
                    mapped.diagnostic,
                    GPUFrameReadbackMapFailureSafety.SafeToRelease,
                )
                is GPUReadbackStagingLifecycleResult.Accepted -> {
                    val mappedBytes = range.copyBytesFromZero()
                    when (val depadded = GPUReadbackLayoutDepadder.depad(mappedBytes, output.layout)) {
                        is GPUReadbackDepadPlan.Depadded -> {
                            when (val marked = provider.markReadbackDepadded(output.stagingLease)) {
                                is GPUReadbackStagingLifecycleResult.Accepted ->
                                    GPUFrameReadbackMapDelivery.Pixels(
                                        output.request.requestId,
                                        depadded.copyBytes(),
                                    )
                                is GPUReadbackStagingLifecycleResult.Refused ->
                                    GPUFrameReadbackMapDelivery.Failed(
                                        marked.diagnostic,
                                        GPUFrameReadbackMapFailureSafety.SafeToRelease,
                                    )
                            }
                        }
                        is GPUReadbackDepadPlan.Refused -> GPUFrameReadbackMapDelivery.Failed(
                            depadded.diagnostic,
                            GPUFrameReadbackMapFailureSafety.SafeToRelease,
                        )
                    }
                }
            }
        } catch (failure: Throwable) {
            terminalCallbackFailure("mapped-range", failure)
        }
        val unmapped = runCatching { range.unmap() }.isSuccess

        if (!unmapped) {
            return GPUFrameReadbackMapDelivery.Failed(
                executionDiagnostic(
                    "failed.frame-readback.unmap",
                    "Mapped readback buffer could not be unmapped safely.",
                ),
                GPUFrameReadbackMapFailureSafety.Quarantine,
            )
        }

        return terminal
    }

    private fun terminalFailure(
        delivery: GPUFrameReadbackMapDelivery.Failed,
    ): GPUFrameReadbackMapDelivery = delivery

    private inline fun providerTransition(
        operation: String,
        action: () -> GPUReadbackStagingLifecycleResult,
    ): GPUReadbackStagingLifecycleResult = try {
        action()
    } catch (failure: Throwable) {
        GPUReadbackStagingLifecycleResult.Refused(
            executionDiagnostic(
                "failed.frame-readback.lifecycle-callback",
                "Readback pool lifecycle callback failed.",
                mapOf(
                    "operation" to operation,
                    "failureClass" to failure::class.simpleName.orEmpty(),
                ),
            ),
        )
    }

    private fun failClosed(
        record: SubmissionRecord,
        primary: GPUFrameReadbackLifecycleResult.Refused,
    ): GPUFrameReadbackLifecycleResult {
        val cleanup = providerTransition("quarantineReadbackAfterSubmit") {
            provider.quarantineReadbackAfterSubmit(record.output.stagingLease)
        }
        return when (cleanup) {
            is GPUReadbackStagingLifecycleResult.Accepted -> {
                removeRecord(record)
                primary
            }
            is GPUReadbackStagingLifecycleResult.Refused -> cleanup.toFrameResult()
        }
    }

    private fun lifecycleCallbackFailure(
        operation: String,
        failure: Throwable,
    ): GPUFrameReadbackLifecycleResult.Refused = GPUFrameReadbackLifecycleResult.Refused(
        executionDiagnostic(
            "failed.frame-readback.lifecycle-callback",
            "Readback lifecycle callback failed.",
            mapOf(
                "operation" to operation,
                "failureClass" to failure::class.simpleName.orEmpty(),
            ),
        ),
    )

    private fun terminalCallbackFailure(
        operation: String,
        failure: Throwable,
    ): GPUFrameReadbackMapDelivery.Failed = GPUFrameReadbackMapDelivery.Failed(
        executionDiagnostic(
            "failed.frame-readback.terminal-callback",
            "Asynchronous readback terminal processing failed.",
            mapOf(
                "operation" to operation,
                "failureClass" to failure::class.simpleName.orEmpty(),
            ),
        ),
        GPUFrameReadbackMapFailureSafety.Quarantine,
    )

    @Synchronized
    private fun recordFor(
        output: GPUPreparedReadbackOutput,
        operand: GPUPreparedNativeScopeOperand.Readback,
    ): SubmissionRecord? {
        val ticket = ticketByReservation[output.stagingLease.reservationId] ?: return null
        return submissions[ticket]?.takeIf { it.output === output && it.operand === operand }
    }

    @Synchronized
    private fun removeRecord(record: SubmissionRecord) {
        if (submissions[record.ticket.ticketId] === record) submissions.remove(record.ticket.ticketId)
        if (ticketByReservation[record.output.stagingLease.reservationId] == record.ticket.ticketId) {
            ticketByReservation.remove(record.output.stagingLease.reservationId)
        }
    }

    private fun SubmissionRecord.matches(
        ticket: GPUQueueCompletionTicket,
        output: GPUPreparedReadbackOutput,
        operand: GPUPreparedNativeScopeOperand.Readback,
    ): Boolean = this.ticket === ticket && this.output === output && this.operand === operand

    private fun matches(
        output: GPUPreparedReadbackOutput,
        operand: GPUPreparedNativeScopeOperand.Readback,
    ): Boolean = operand.destination.ownership == GPUPreparedNativeOperandOwnership.OutputOwnedReadback &&
        operand.layout.originX == output.request.sourceBounds.left &&
        operand.layout.originY == output.request.sourceBounds.top &&
        operand.layout.width == output.layout.width &&
        operand.layout.height == output.layout.height &&
        operand.layout.bytesPerRow == output.layout.paddedBytesPerRow &&
        operand.layout.rowsPerImage == output.layout.rowsPerImage &&
        operand.layout.bufferOffset == output.layout.bufferOffset &&
        operand.layout.mappedSize == output.layout.totalBufferBytes

    private fun GPUReadbackStagingLifecycleResult.toFrameResult(): GPUFrameReadbackLifecycleResult = when (this) {
        is GPUReadbackStagingLifecycleResult.Accepted -> GPUFrameReadbackLifecycleResult.Applied
        is GPUReadbackStagingLifecycleResult.Refused -> GPUFrameReadbackLifecycleResult.Refused(diagnostic)
    }

    private fun refused(code: String, message: String): GPUFrameReadbackLifecycleResult.Refused =
        GPUFrameReadbackLifecycleResult.Refused(executionDiagnostic(code, message))
}

/** Process-global injective IDs avoid collisions between bridges sharing one physical provider. */
private object GPUFrameReadbackSubmissionIds {
    private val next = AtomicLong(1L)
    fun next(): Long = next.getAndIncrement().also {
        check(it > 0L) { "GPU frame readback submission ID space exhausted" }
    }
}

/** Public-facade-only wgpu4k mapper. Its executor must never be the render thread. */
internal class GPUWgpu4kNativeReadbackMapper(
    private val mappingExecutor: Executor,
) : GPUFrameNativeReadbackMapper, AutoCloseable {
    private val pending = linkedSetOf<MappingTask>()
    private var closed = false

    private inner class MappingTask(
        private val output: GPUPreparedReadbackOutput,
        private val operand: GPUPreparedNativeScopeOperand.Readback,
        private val sink: GPUFrameNativeReadbackMapSink,
    ) : Runnable {
        private val terminal = AtomicBoolean(false)
        private val deliveryInProgress = AtomicBoolean(false)

        override fun run() {
            if (terminal.get()) return
            val mapResult = try {
                runBlocking {
                    operand.destination.buffer.mapAsync(
                        GPUMapMode.Read,
                        0uL,
                        output.layout.totalBufferBytes.toULong(),
                    )
                }
            } catch (failure: Throwable) {
                Result.failure(failure)
            }
            val failure = mapResult.exceptionOrNull()
            deliver(
                if (failure != null) {
                    GPUFrameNativeReadbackMapDelivery.Failed(
                        executionDiagnostic(
                            "failed.frame-readback.map",
                            "wgpu4k readback mapping failed.",
                            mapOf("failureClass" to failure::class.simpleName.orEmpty()),
                        ),
                        GPUFrameReadbackMapFailureSafety.Quarantine,
                    )
                } else {
                    GPUFrameNativeReadbackMapDelivery.Mapped(
                        WgpuMappedReadbackRange(
                            operand.destination.buffer,
                            output.layout.totalBufferBytes,
                        ),
                    )
                },
            )
        }

        fun cancelForTeardown() {
            deliver(
                GPUFrameNativeReadbackMapDelivery.Failed(
                    executionDiagnostic(
                        "failed.frame-readback.mapping-teardown",
                        "Readback mapping was cancelled by prepared scene teardown.",
                    ),
                    GPUFrameReadbackMapFailureSafety.Quarantine,
                ),
            )
        }

        fun abandonRejected() {
            terminal.set(true)
            synchronized(this@GPUWgpu4kNativeReadbackMapper) { pending.remove(this) }
        }

        private fun deliver(delivery: GPUFrameNativeReadbackMapDelivery) {
            if (terminal.get() || !deliveryInProgress.compareAndSet(false, true)) return
            try {
                sink.accept(delivery)
                terminal.set(true)
                synchronized(this@GPUWgpu4kNativeReadbackMapper) { pending.remove(this) }
            } finally {
                deliveryInProgress.set(false)
            }
        }
    }

    override fun map(
        output: GPUPreparedReadbackOutput,
        operand: GPUPreparedNativeScopeOperand.Readback,
        sink: GPUFrameNativeReadbackMapSink,
    ): GPUFrameReadbackMapArmResult {
        validate(output, operand)?.let { return GPUFrameReadbackMapArmResult.Refused(it) }
        val task = synchronized(this) {
            if (closed) {
                return GPUFrameReadbackMapArmResult.Refused(
                    executionDiagnostic(
                        "failed.frame-readback.mapping-executor",
                        "Readback mapping cannot be scheduled after mapper teardown.",
                    ),
                )
            }
            MappingTask(output, operand, sink).also { pending += it }
        }
        return try {
            mappingExecutor.execute(task)
            GPUFrameReadbackMapArmResult.Armed
        } catch (failure: Throwable) {
            task.abandonRejected()
            GPUFrameReadbackMapArmResult.Refused(
                executionDiagnostic(
                    "failed.frame-readback.mapping-executor",
                    "Readback mapping could not be scheduled off the render thread.",
                    mapOf("failureClass" to failure::class.simpleName.orEmpty()),
                ),
            )
        }
    }

    override fun close() {
        val cancelling = synchronized(this) {
            closed = true
            pending.toList()
        }
        var firstFailure: Throwable? = null
        cancelling.forEach { task ->
            try {
                task.cancelForTeardown()
            } catch (failure: Throwable) {
                if (firstFailure == null) firstFailure = failure else firstFailure.addSuppressed(failure)
            }
        }
        val remaining = synchronized(this) { pending.size }
        if (remaining > 0) {
            throw GPUOwnedNativeCloseIncompleteException(
                ownerLabel = "readback-mapper",
                remainingOwnerCount = remaining,
                failures = listOfNotNull(firstFailure),
            )
        }
    }

    private fun validate(
        output: GPUPreparedReadbackOutput,
        operand: GPUPreparedNativeScopeOperand.Readback,
    ): GPUDiagnostic? {
        val layout = operand.layout
        return when {
            operand.destination.ownership != GPUPreparedNativeOperandOwnership.OutputOwnedReadback ->
                executionDiagnostic(
                    "invalid.frame-readback.native-ownership",
                    "Native staging buffer must be output-owned.",
                )
            layout.originX != output.request.sourceBounds.left ||
                layout.originY != output.request.sourceBounds.top ||
                layout.width != output.layout.width ||
                layout.height != output.layout.height ||
                layout.bytesPerRow != output.layout.paddedBytesPerRow ||
                layout.rowsPerImage != output.layout.rowsPerImage ||
                layout.bufferOffset != output.layout.bufferOffset ||
                layout.mappedSize != output.layout.totalBufferBytes ||
                layout.format != GPUTextureFormat.RGBA8Unorm -> executionDiagnostic(
                "invalid.frame-readback.native-layout",
                "Native readback operand does not exactly match the preflighted RGBA8 layout.",
            )
            else -> null
        }
    }

    private class WgpuMappedReadbackRange(
        private val buffer: io.ygdrasil.webgpu.GPUBuffer,
        private val byteSize: Long,
    ) : GPUFrameMappedReadbackRange {
        private val copied = AtomicBoolean(false)
        private val unmapped = AtomicBoolean(false)

        override fun copyBytesFromZero(): ByteArray {
            check(!unmapped.get()) { "Mapped range was already unmapped" }
            check(copied.compareAndSet(false, true)) { "Mapped range may be copied only once" }
            return buffer.getMappedRange(0uL, byteSize.toULong()).toByteArray()
        }

        override fun unmap() {
            check(unmapped.compareAndSet(false, true)) { "Mapped range may be unmapped only once" }
            buffer.unmap()
        }
    }
}
