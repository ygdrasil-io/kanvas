package org.graphiks.kanvas.gpu.renderer.execution

import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnostic
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameID

/** Validated handle-free identity reserved for one future queue-completion proof. */
@JvmInline
value class GPUQueueCompletionTicketID(val value: String) {
    init {
        requireExecutionDumpSafe("GPUQueueCompletionTicketID.value", value)
    }
}

private val EXECUTION_DUMP_SAFE_LABEL_PATTERN = Regex("^[A-Za-z0-9._:-]+$")
private val EXECUTION_UNSAFE_DUMP_PATTERN =
    Regex("(?i)(externaltexturehandle|gpu[a-z0-9]*handle|@|0x[0-9a-f]{6,})")

internal fun requireExecutionDumpSafe(fieldName: String, value: String) {
    require(value.isExecutionDumpSafe()) { "$fieldName must use dump-safe GPU evidence labels" }
}

internal fun String.isExecutionDumpSafe(): Boolean =
    isNotBlank() &&
        matches(EXECUTION_DUMP_SAFE_LABEL_PATTERN) &&
        !EXECUTION_UNSAFE_DUMP_PATTERN.containsMatchIn(this)

data class GPUQueueCompletionTicketRequest(
    val frameId: GPUFrameID,
    val deviceGeneration: GPUDeviceGenerationID,
)

data class GPUQueueCompletionTicket(
    val ticketId: GPUQueueCompletionTicketID,
    val frameId: GPUFrameID,
    val deviceGeneration: GPUDeviceGenerationID,
)

sealed interface GPUQueueCompletionTicketReservation {
    data class Reserved(val ticket: GPUQueueCompletionTicket) : GPUQueueCompletionTicketReservation
    data object Missing : GPUQueueCompletionTicketReservation
    data class Failed(val diagnostic: GPUDiagnostic) : GPUQueueCompletionTicketReservation
    data class Duplicate(val ticketId: GPUQueueCompletionTicketID) : GPUQueueCompletionTicketReservation
}

fun interface GPUQueueCompletionProvider {
    fun reserveTicket(request: GPUQueueCompletionTicketRequest): GPUQueueCompletionTicketReservation
}

fun interface GPUQueueCompletionInvoker {
    suspend fun awaitSubmittedWorkDone(): Result<Unit>
}

fun interface GPUQueueCompletionSink {
    fun accept(delivery: GPUQueueCompletionDelivery.Accepted)
}

data class GPUQueueCompletionCapabilityRequirement(
    val implementationRevision: String,
    val capability: String,
) {
    init {
        requireExecutionDumpSafe("implementationRevision", implementationRevision)
        requireExecutionDumpSafe("capability", capability)
    }
}

data class GPUQueueCompletionCapabilityEvidence(
    val implementationRevision: String,
    val capability: String,
    val accepted: Boolean,
) {
    init {
        requireExecutionDumpSafe("implementationRevision", implementationRevision)
        requireExecutionDumpSafe("capability", capability)
    }
}

enum class GPUQueueCompletionFailureKind {
    CallbackFailure,
    DeviceLost,
    AdapterClosed,
    Cancelled,
}

sealed interface GPUQueueCompletionOutcome {
    data object Success : GPUQueueCompletionOutcome
    data class Failure(val kind: GPUQueueCompletionFailureKind) : GPUQueueCompletionOutcome
}

sealed interface GPUQueueCompletionDelivery {
    data class Accepted(
        val ticketId: GPUQueueCompletionTicketID,
        val outcome: GPUQueueCompletionOutcome,
    ) : GPUQueueCompletionDelivery

    data class Unarmed(val ticketId: GPUQueueCompletionTicketID) : GPUQueueCompletionDelivery
    data class Duplicate(val ticketId: GPUQueueCompletionTicketID) : GPUQueueCompletionDelivery
    data class Unknown(val ticketId: GPUQueueCompletionTicketID) : GPUQueueCompletionDelivery
    data class Expired(val ticketId: GPUQueueCompletionTicketID) : GPUQueueCompletionDelivery
}

sealed interface GPUQueueCompletionArmResult {
    data class Armed(val ticketId: GPUQueueCompletionTicketID) : GPUQueueCompletionArmResult
    data class Duplicate(val ticketId: GPUQueueCompletionTicketID) : GPUQueueCompletionArmResult
    data class Unknown(val ticketId: GPUQueueCompletionTicketID) : GPUQueueCompletionArmResult
    data class Expired(val ticketId: GPUQueueCompletionTicketID) : GPUQueueCompletionArmResult
    data class Refused(
        val ticketId: GPUQueueCompletionTicketID,
        val reason: GPUQueueCompletionFailureKind,
    ) : GPUQueueCompletionArmResult
}

class GPUQueueCompletionAdapter(
    private val requirement: GPUQueueCompletionCapabilityRequirement,
    private val evidence: GPUQueueCompletionCapabilityEvidence,
    private val disabledReasonCode: String? = null,
    private val invoker: GPUQueueCompletionInvoker,
) : GPUQueueCompletionProvider {
    private enum class TicketState { Reserved, Armed, Invoking, Terminal }

    private class TicketRecord(
        val ticket: GPUQueueCompletionTicket,
        var state: TicketState = TicketState.Reserved,
        val completion: CompletableDeferred<GPUQueueCompletionDelivery.Accepted> = CompletableDeferred(),
        var sink: GPUQueueCompletionSink? = null,
        var invocationJob: Job? = null,
        var terminalOutcome: GPUQueueCompletionOutcome? = null,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val adapterInstanceId = nextAdapterInstanceId.getAndIncrement()
    private val tickets = linkedMapOf<GPUQueueCompletionTicketID, TicketRecord>()
    private val terminalOrder = ArrayDeque<GPUQueueCompletionTicketID>()
    private var nextTicketId = 1L
    private var closed = false
    private val lostDeviceGenerations = linkedSetOf<GPUDeviceGenerationID>()

    internal val retainedTicketRecordCount: Int
        @Synchronized get() = tickets.size

    @Synchronized
    override fun reserveTicket(
        request: GPUQueueCompletionTicketRequest,
    ): GPUQueueCompletionTicketReservation {
        val reason = disabledReasonCode ?: when {
            closed -> "unsupported.queue-completion.adapter-closed"
            request.deviceGeneration in lostDeviceGenerations -> "unsupported.queue-completion.device-lost"
            !evidence.accepted -> "unsupported.queue-completion.capability-unaccepted"
            evidence.implementationRevision != requirement.implementationRevision ->
                "unsupported.queue-completion.revision-mismatch"
            evidence.capability != requirement.capability ->
                "unsupported.queue-completion.capability-mismatch"
            else -> null
        }
        if (reason != null) {
            return GPUQueueCompletionTicketReservation.Failed(
                preflightDiagnostic(reason, "Exact queue-completion capability evidence is unavailable."),
            )
        }
        val ticket = GPUQueueCompletionTicket(
            ticketId = GPUQueueCompletionTicketID(
                "queue-ticket.$adapterInstanceId.${nextTicketId++}." +
                    "${request.deviceGeneration.value}.${request.frameId.value}",
            ),
            frameId = request.frameId,
            deviceGeneration = request.deviceGeneration,
        )
        tickets[ticket.ticketId] = TicketRecord(ticket)
        return GPUQueueCompletionTicketReservation.Reserved(ticket)
    }

    fun armAfterSubmit(
        ticket: GPUQueueCompletionTicket,
        sink: GPUQueueCompletionSink,
    ): GPUQueueCompletionArmResult {
        val record = synchronized(this) {
            val current = tickets[ticket.ticketId]
                ?: return missingArmResult(ticket.ticketId)
            if (current.ticket != ticket) return GPUQueueCompletionArmResult.Unknown(ticket.ticketId)
            if (current.state == TicketState.Terminal) {
                val reason = (current.terminalOutcome as? GPUQueueCompletionOutcome.Failure)?.kind
                return if (reason != null) {
                    GPUQueueCompletionArmResult.Refused(ticket.ticketId, reason)
                } else {
                    GPUQueueCompletionArmResult.Duplicate(ticket.ticketId)
                }
            }
            if (closed) {
                return GPUQueueCompletionArmResult.Refused(
                    ticket.ticketId,
                    GPUQueueCompletionFailureKind.AdapterClosed,
                )
            }
            if (ticket.deviceGeneration in lostDeviceGenerations) {
                return GPUQueueCompletionArmResult.Refused(
                    ticket.ticketId,
                    GPUQueueCompletionFailureKind.DeviceLost,
                )
            }
            if (current.state != TicketState.Reserved) {
                return GPUQueueCompletionArmResult.Duplicate(ticket.ticketId)
            }
            current.state = TicketState.Armed
            current.sink = sink
            current
        }
        val job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            val shouldInvoke = synchronized(this@GPUQueueCompletionAdapter) {
                if (record.state == TicketState.Armed) {
                    record.state = TicketState.Invoking
                    true
                } else {
                    false
                }
            }
            if (!shouldInvoke) return@launch
            val outcome = try {
                invoker.awaitSubmittedWorkDone().fold(
                    onSuccess = { GPUQueueCompletionOutcome.Success },
                    onFailure = {
                        GPUQueueCompletionOutcome.Failure(GPUQueueCompletionFailureKind.CallbackFailure)
                    },
                )
            } catch (_: CancellationException) {
                GPUQueueCompletionOutcome.Failure(GPUQueueCompletionFailureKind.Cancelled)
            } catch (_: Throwable) {
                GPUQueueCompletionOutcome.Failure(GPUQueueCompletionFailureKind.CallbackFailure)
            }
            terminalize(record, outcome, cancelInvocation = false)
        }
        synchronized(this) {
            record.invocationJob = job
            if (record.state == TicketState.Terminal && job.isActive) job.cancel()
        }
        return GPUQueueCompletionArmResult.Armed(ticket.ticketId)
    }

    suspend fun awaitCompletion(ticket: GPUQueueCompletionTicket): GPUQueueCompletionDelivery {
        val record = synchronized(this) { tickets[ticket.ticketId] }
            ?: return missingDelivery(ticket.ticketId)
        if (record.ticket != ticket) return GPUQueueCompletionDelivery.Unknown(ticket.ticketId)
        if (synchronized(this) { record.state == TicketState.Reserved }) {
            return GPUQueueCompletionDelivery.Unarmed(ticket.ticketId)
        }
        return record.completion.await()
    }

    private fun deliverFailure(
        ticket: GPUQueueCompletionTicket,
        kind: GPUQueueCompletionFailureKind,
    ): GPUQueueCompletionDelivery {
        val record = synchronized(this) { tickets[ticket.ticketId] }
            ?: return missingDelivery(ticket.ticketId)
        if (record.ticket != ticket) return GPUQueueCompletionDelivery.Unknown(ticket.ticketId)
        return terminalize(
            record,
            GPUQueueCompletionOutcome.Failure(kind),
            cancelInvocation = true,
            allowReserved = false,
        )
    }

    fun cancel(ticket: GPUQueueCompletionTicket): GPUQueueCompletionDelivery =
        deliverFailure(ticket, GPUQueueCompletionFailureKind.Cancelled)

    fun deviceLost(
        deviceGeneration: GPUDeviceGenerationID,
    ): List<GPUQueueCompletionDelivery> {
        val snapshot = synchronized(this) {
            lostDeviceGenerations += deviceGeneration
            tickets.values.filter { it.ticket.deviceGeneration == deviceGeneration }
        }
        return snapshot.map { record ->
            terminalize(
                record,
                GPUQueueCompletionOutcome.Failure(GPUQueueCompletionFailureKind.DeviceLost),
                cancelInvocation = true,
                allowReserved = true,
            )
        }
    }

    fun close(): List<GPUQueueCompletionDelivery> {
        val snapshot = synchronized(this) {
            if (closed) return emptyList()
            closed = true
            tickets.values.toList()
        }
        val deliveries = snapshot.map { record ->
            terminalize(
                record,
                GPUQueueCompletionOutcome.Failure(GPUQueueCompletionFailureKind.AdapterClosed),
                cancelInvocation = true,
                allowReserved = true,
            )
        }
        scope.cancel()
        return deliveries
    }

    private fun terminalize(
        record: TicketRecord,
        outcome: GPUQueueCompletionOutcome,
        cancelInvocation: Boolean,
        allowReserved: Boolean = false,
    ): GPUQueueCompletionDelivery {
        val claim = synchronized(this) {
            when (record.state) {
                TicketState.Reserved -> if (!allowReserved) {
                    return GPUQueueCompletionDelivery.Unarmed(record.ticket.ticketId)
                } else {
                    val delivery = GPUQueueCompletionDelivery.Accepted(record.ticket.ticketId, outcome)
                    record.state = TicketState.Terminal
                    record.terminalOutcome = outcome
                    Triple(delivery, record.sink, record.invocationJob)
                }
                TicketState.Terminal -> return GPUQueueCompletionDelivery.Duplicate(record.ticket.ticketId)
                TicketState.Armed, TicketState.Invoking -> {
                    val delivery = GPUQueueCompletionDelivery.Accepted(record.ticket.ticketId, outcome)
                    record.state = TicketState.Terminal
                    record.terminalOutcome = outcome
                    Triple(delivery, record.sink, record.invocationJob)
                }
            }
        }
        try {
            claim.second?.accept(claim.first)
        } finally {
            record.completion.complete(claim.first)
            if (cancelInvocation) claim.third?.cancel()
            synchronized(this) {
                record.sink = null
                record.invocationJob = null
                terminalOrder += record.ticket.ticketId
                pruneTerminalRecords()
            }
        }
        return claim.first
    }

    private fun pruneTerminalRecords() {
        while (terminalOrder.size > GPU_QUEUE_COMPLETION_TOMBSTONE_LIMIT) {
            val oldest = terminalOrder.removeFirst()
            tickets[oldest]?.takeIf { it.state == TicketState.Terminal }?.let { tickets.remove(oldest) }
        }
    }

    private fun missingDelivery(ticketId: GPUQueueCompletionTicketID): GPUQueueCompletionDelivery =
        if (isExpiredOwnedTicket(ticketId)) {
            GPUQueueCompletionDelivery.Expired(ticketId)
        } else {
            GPUQueueCompletionDelivery.Unknown(ticketId)
        }

    private fun missingArmResult(ticketId: GPUQueueCompletionTicketID): GPUQueueCompletionArmResult =
        if (isExpiredOwnedTicket(ticketId)) {
            GPUQueueCompletionArmResult.Expired(ticketId)
        } else {
            GPUQueueCompletionArmResult.Unknown(ticketId)
        }

    private fun isExpiredOwnedTicket(ticketId: GPUQueueCompletionTicketID): Boolean {
        val parts = ticketId.value.split('.')
        if (parts.size != 5 || parts[0] != "queue-ticket") return false
        val owner = parts[1].toLongOrNull() ?: return false
        val sequence = parts[2].toLongOrNull() ?: return false
        parts[3].toLongOrNull() ?: return false
        parts[4].toLongOrNull() ?: return false
        return owner == adapterInstanceId && sequence in 1 until nextTicketId
    }

    companion object {
        private val nextAdapterInstanceId = AtomicLong(1L)

        fun disabled(reasonCode: String): GPUQueueCompletionAdapter = GPUQueueCompletionAdapter(
            requirement = GPUQueueCompletionCapabilityRequirement("unavailable", "on-submitted-work-done"),
            evidence = GPUQueueCompletionCapabilityEvidence("unavailable", "on-submitted-work-done", false),
            disabledReasonCode = reasonCode,
            invoker = GPUQueueCompletionInvoker { Result.failure(IllegalStateException("disabled")) },
        )
    }
}

internal const val GPU_QUEUE_COMPLETION_TOMBSTONE_LIMIT = 128
