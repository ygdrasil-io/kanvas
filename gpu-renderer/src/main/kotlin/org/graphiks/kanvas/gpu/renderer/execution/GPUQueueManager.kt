package org.graphiks.kanvas.gpu.renderer.execution

import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceLease

@JvmInline
value class GPUQueueSubmissionId(val value: Long) {
    init {
        require(value > 0L) { "GPUQueueSubmissionId.value must be positive" }
    }
}

@JvmInline
value class GPUQueuedResourceRef(val value: String) {
    init {
        require(value.isQueueDumpSafeToken()) {
            "GPUQueuedResourceRef.value must use dump-safe GPU evidence labels"
        }
    }
}

enum class GPUQueueExecutionState {
    Planned,
    Prepared,
    Encoded,
    Submitted,
    GPUCompleted,
    FailedPreSubmit,
    FailedAfterSubmit,
}

enum class GPUQueueOutputState { NotApplicable, Acquired, Presented, PresentFailed }

sealed interface GPUQueueOutputUpdate {
    data object Accepted : GPUQueueOutputUpdate
    data class Duplicate(val submissionId: GPUQueueSubmissionId) : GPUQueueOutputUpdate
    data class Unknown(val submissionId: GPUQueueSubmissionId) : GPUQueueOutputUpdate
    data class Invalid(
        val submissionId: GPUQueueSubmissionId,
        val current: GPUQueueOutputState,
        val requested: GPUQueueOutputState,
    ) : GPUQueueOutputUpdate
}

sealed interface GPUQueueSubmissionRegistration {
    class Accepted(
        val submission: GPUQueueSubmission,
        val completionSink: GPUQueueCompletionSink,
    ) : GPUQueueSubmissionRegistration
    data class Duplicate(
        val ticketId: GPUQueueCompletionTicketID,
        val existingSubmissionId: GPUQueueSubmissionId,
    ) : GPUQueueSubmissionRegistration
}

sealed interface GPUQueueCompletionAcceptance {
    data class Accepted(
        val submissionId: GPUQueueSubmissionId,
        val releasedResources: List<GPUQueuedResourceRef>,
        val quarantinedResources: List<GPUQueuedResourceRef>,
    ) : GPUQueueCompletionAcceptance

    data class Duplicate(val ticketId: GPUQueueCompletionTicketID) : GPUQueueCompletionAcceptance
    data class Unknown(val ticketId: GPUQueueCompletionTicketID) : GPUQueueCompletionAcceptance
}

data class GPUQueueSubmission(
    val id: GPUQueueSubmissionId,
    val label: String,
    val retainedResources: List<GPUQueuedResourceRef>,
    val retainedResourceCount: Int = retainedResources.size,
    val completed: Boolean = false,
    val released: Boolean = false,
    val quarantined: Boolean = false,
    val completion: String = GPU_QUEUE_COMPLETION_PENDING,
    val completionTicketId: GPUQueueCompletionTicketID? = null,
    val executionState: GPUQueueExecutionState = GPUQueueExecutionState.Submitted,
    val outputState: GPUQueueOutputState = GPUQueueOutputState.NotApplicable,
) {
    init {
        require(label.isQueueDumpSafeToken()) { "GPUQueueSubmission.label must be dump-safe" }
        require(completion.isQueueDumpSafeToken()) { "GPUQueueSubmission.completion must be dump-safe" }
        require(!completed || completion != GPU_QUEUE_COMPLETION_PENDING) {
            "completed GPUQueueSubmission must use a terminal completion reason"
        }
        require(!(released && quarantined)) { "A queued resource set cannot be released and quarantined" }
    }
}

data class GPUQueueTelemetry(
    val submitted: Long = 0L,
    val completed: Long = 0L,
    val released: Long = 0L,
    val quarantined: Long = 0L,
    val failed: Long = 0L,
    val pending: Long = 0L,
    val waits: Long = 0L,
    val unknownCompletions: Long = 0L,
    val submissions: List<GPUQueueSubmission> = emptyList(),
) {
    fun dumpLines(): List<String> =
        listOf(
            "gpu-queue.telemetry submitted=$submitted completed=$completed released=$released " +
                "pending=$pending waits=$waits unknownCompletions=$unknownCompletions " +
                "failed=$failed quarantined=$quarantined",
        ) + submissions.map { submission ->
            "gpu-queue.submission id=${submission.id.value} label=${submission.label} " +
                "retained=${submission.retainedResourceCount} completed=${submission.completed} " +
                "released=${submission.released} quarantined=${submission.quarantined} " +
                "execution=${submission.executionState} output=${submission.outputState} " +
                "completion=${submission.completion}"
        }
}

class GPUQueueManager : AutoCloseable {
    private var nextSubmissionId: Long = 1L
    private val submissions = linkedMapOf<GPUQueueSubmissionId, GPUQueueSubmission>()
    private val submissionsByTicket = linkedMapOf<GPUQueueCompletionTicketID, GPUQueueSubmissionId>()
    private val terminalTickets = linkedMapOf<GPUQueueCompletionTicketID, GPUQueueSubmissionId>()
    private val terminalSubmissionOrder = ArrayDeque<GPUQueueSubmissionId>()
    private var waitCount: Long = 0L
    private var unknownCompletionCount: Long = 0L
    private var submittedCount: Long = 0L
    private var completedCount: Long = 0L
    private var releasedCount: Long = 0L
    private var quarantinedCount: Long = 0L
    private var failedCount: Long = 0L

    internal val retainedSubmissionRecordCount: Int
        @Synchronized get() = submissions.size + submissionsByTicket.size + terminalTickets.size

    val telemetry: GPUQueueTelemetry
        @Synchronized get() {
            val ordered = submissions.values.toList()
            return GPUQueueTelemetry(
                submitted = submittedCount,
                completed = completedCount,
                released = releasedCount,
                quarantined = quarantinedCount,
                failed = failedCount,
                pending = ordered.count { it.executionState == GPUQueueExecutionState.Submitted }.toLong(),
                waits = waitCount,
                unknownCompletions = unknownCompletionCount,
                submissions = ordered,
            )
        }

    @Synchronized
    fun submit(
        label: String,
        retainedResources: List<GPUQueuedResourceRef>,
    ): GPUQueueSubmission = createSubmission(label, retainedResources, ticketId = null, outputApplicable = false)

    @Synchronized
    fun tryRecordSubmitted(
        ticket: GPUQueueCompletionTicket,
        label: String,
        retainedResources: List<GPUQueuedResourceRef>,
        outputApplicable: Boolean = false,
    ): GPUQueueSubmissionRegistration {
        submissionsByTicket[ticket.ticketId]?.let { existing ->
            return GPUQueueSubmissionRegistration.Duplicate(ticket.ticketId, existing)
        }
        terminalTickets[ticket.ticketId]?.let { existing ->
            return GPUQueueSubmissionRegistration.Duplicate(ticket.ticketId, existing)
        }
        val submission = createSubmission(label, retainedResources, ticket.ticketId, outputApplicable)
        submissionsByTicket[ticket.ticketId] = submission.id
        val sink = GPUQueueCompletionSink { delivery ->
            check(delivery.ticketId == ticket.ticketId) {
                "Queue completion sink received a ticket owned by another submission"
            }
            val acceptance = acceptGPUCompletion(delivery.ticketId, delivery.outcome)
            check(acceptance is GPUQueueCompletionAcceptance.Accepted) {
                "Queue completion sink rejected terminal delivery: ${acceptance::class.simpleName}"
            }
        }
        return GPUQueueSubmissionRegistration.Accepted(submission, sink)
    }

    private fun createSubmission(
        label: String,
        retainedResources: List<GPUQueuedResourceRef>,
        ticketId: GPUQueueCompletionTicketID?,
        outputApplicable: Boolean,
    ): GPUQueueSubmission = GPUQueueSubmission(
        id = GPUQueueSubmissionId(nextSubmissionId++),
        label = label,
        retainedResources = retainedResources.toList(),
        completionTicketId = ticketId,
        outputState = if (outputApplicable) GPUQueueOutputState.Acquired else GPUQueueOutputState.NotApplicable,
    ).also {
        submissions[it.id] = it
        submittedCount += 1L
    }

    @Synchronized
    fun submission(id: GPUQueueSubmissionId): GPUQueueSubmission? = submissions[id]

    @Synchronized
    fun recordPresented(id: GPUQueueSubmissionId): GPUQueueOutputUpdate =
        transitionOutput(id, GPUQueueOutputState.Presented)

    @Synchronized
    fun recordPresentFailed(id: GPUQueueSubmissionId): GPUQueueOutputUpdate =
        transitionOutput(id, GPUQueueOutputState.PresentFailed)

    private fun transitionOutput(
        id: GPUQueueSubmissionId,
        requested: GPUQueueOutputState,
    ): GPUQueueOutputUpdate {
        val current = submissions[id] ?: return GPUQueueOutputUpdate.Unknown(id)
        return when {
            current.outputState == requested -> GPUQueueOutputUpdate.Duplicate(id)
            current.outputState != GPUQueueOutputState.Acquired ->
                GPUQueueOutputUpdate.Invalid(id, current.outputState, requested)
            else -> {
                submissions[id] = current.copy(outputState = requested)
                GPUQueueOutputUpdate.Accepted
            }
        }
    }

    @Synchronized
    internal fun acceptGPUCompletion(
        ticketId: GPUQueueCompletionTicketID,
        outcome: GPUQueueCompletionOutcome,
    ): GPUQueueCompletionAcceptance {
        terminalTickets[ticketId]?.let { return GPUQueueCompletionAcceptance.Duplicate(ticketId) }
        val id = submissionsByTicket[ticketId] ?: run {
            unknownCompletionCount += 1L
            return GPUQueueCompletionAcceptance.Unknown(ticketId)
        }
        val current = submissions.getValue(id)
        if (current.executionState != GPUQueueExecutionState.Submitted) {
            return GPUQueueCompletionAcceptance.Duplicate(ticketId)
        }
        return when (outcome) {
            GPUQueueCompletionOutcome.Success -> {
                submissions[id] = current.copy(
                    retainedResources = emptyList(),
                    completed = true,
                    released = true,
                    completion = GPU_QUEUE_COMPLETION_GPU_COMPLETED,
                    executionState = GPUQueueExecutionState.GPUCompleted,
                )
                completedCount += 1L
                releasedCount += 1L
                retainTerminal(ticketId, id, retainUntilTeardown = false)
                GPUQueueCompletionAcceptance.Accepted(id, current.retainedResources, emptyList())
            }
            is GPUQueueCompletionOutcome.Failure -> {
                submissions[id] = current.copy(
                    completed = true,
                    quarantined = true,
                    completion = outcome.kind.dumpLabel,
                    executionState = GPUQueueExecutionState.FailedAfterSubmit,
                )
                failedCount += 1L
                quarantinedCount += 1L
                retainTerminal(ticketId, id, retainUntilTeardown = true)
                GPUQueueCompletionAcceptance.Accepted(id, emptyList(), current.retainedResources)
            }
        }
    }

    private fun retainTerminal(
        ticketId: GPUQueueCompletionTicketID,
        submissionId: GPUQueueSubmissionId,
        retainUntilTeardown: Boolean,
    ) {
        submissionsByTicket.remove(ticketId)
        terminalTickets[ticketId] = submissionId
        if (retainUntilTeardown) return
        terminalSubmissionOrder += submissionId
        while (terminalSubmissionOrder.size > GPU_QUEUE_SUBMISSION_HISTORY_LIMIT) {
            val oldest = terminalSubmissionOrder.removeFirst()
            val removed = submissions.remove(oldest) ?: continue
            removed.completionTicketId?.let(terminalTickets::remove)
        }
    }

    fun submitLeases(
        label: String,
        retainedLeases: List<GPUResourceLease>,
    ): GPUQueueSubmission = submit(
        label,
        retainedLeases.map { GPUQueuedResourceRef("lease:${it.leaseId}") },
    )

    @Synchronized
    fun markCompleted(
        id: GPUQueueSubmissionId,
        completion: String,
    ): Boolean {
        require(completion.isQueueDumpSafeToken()) { "completion must be dump-safe" }
        require(completion != GPU_QUEUE_COMPLETION_PENDING) { "completion must use a real completion reason" }
        if (submissions[id] == null) {
            unknownCompletionCount += 1L
            return false
        }
        when (completion) {
            GPU_QUEUE_COMPLETION_PRESENTED -> recordPresented(id)
            GPU_QUEUE_COMPLETION_PRESENT_FAILED -> recordPresentFailed(id)
            GPU_QUEUE_COMPLETION_READBACK_COMPLETE, GPU_QUEUE_COMPLETION_TARGET_CLOSE -> Unit
        }
        return true
    }

    @Synchronized
    fun pendingSubmissionIds(labelPrefix: String? = null): List<GPUQueueSubmissionId> {
        labelPrefix?.let { require(it.isQueueDumpSafeToken()) { "labelPrefix must be dump-safe" } }
        return submissions.values
            .filter { it.executionState == GPUQueueExecutionState.Submitted }
            .filter { labelPrefix == null || it.label.startsWith(labelPrefix) }
            .map(GPUQueueSubmission::id)
    }

    @Synchronized
    fun retainedResources(id: GPUQueueSubmissionId): List<GPUQueuedResourceRef> =
        submissions[id]
            ?.takeIf { it.executionState == GPUQueueExecutionState.Submitted && !it.released && !it.quarantined }
            ?.retainedResources
            .orEmpty()

    @Synchronized
    fun quarantinedResources(id: GPUQueueSubmissionId): List<GPUQueuedResourceRef> =
        submissions[id]?.takeIf(GPUQueueSubmission::quarantined)?.retainedResources.orEmpty()

    @Synchronized
    fun releaseCompleted(): List<GPUQueuedResourceRef> {
        val released = mutableListOf<GPUQueuedResourceRef>()
        submissions.entries.forEach { (id, submission) ->
            if (submission.executionState == GPUQueueExecutionState.GPUCompleted && !submission.released) {
                released += submission.retainedResources
                submissions[id] = submission.copy(released = true)
            }
        }
        return released
    }

    @Synchronized
    fun recordWait() {
        waitCount += 1L
    }

    /** Drops terminal queue evidence and every quarantined resource reference at queue teardown. */
    @Synchronized
    override fun close() {
        submissions.clear()
        submissionsByTicket.clear()
        terminalTickets.clear()
        terminalSubmissionOrder.clear()
    }
}

private val GPUQueueCompletionFailureKind.dumpLabel: String
    get() = when (this) {
        GPUQueueCompletionFailureKind.CallbackFailure -> "callback-failure"
        GPUQueueCompletionFailureKind.DeviceLost -> "device-lost"
        GPUQueueCompletionFailureKind.AdapterClosed -> "adapter-closed"
        GPUQueueCompletionFailureKind.Cancelled -> "cancelled"
    }

private fun String.isQueueDumpSafeToken(): Boolean =
    isNotBlank() &&
        matches(QUEUE_DUMP_SAFE_LABEL_PATTERN) &&
        !QUEUE_RAW_HANDLE_DUMP_PATTERN.containsMatchIn(this) &&
        '@' !in this

private val QUEUE_DUMP_SAFE_LABEL_PATTERN = Regex("^[A-Za-z0-9._:-]+$")
private val QUEUE_RAW_BACKEND_TOKEN = "w" + "gpu"
private val QUEUE_RAW_HANDLE_DUMP_PATTERN =
    Regex("(?i)($QUEUE_RAW_BACKEND_TOKEN|externaltexturehandle|gpu[a-z0-9]*handle|@0x[0-9a-f]+|0x[0-9a-f]{6,})")

internal const val GPU_QUEUE_COMPLETION_PENDING = "pending"
internal const val GPU_QUEUE_COMPLETION_GPU_COMPLETED = "gpu-completed"
internal const val GPU_QUEUE_COMPLETION_READBACK_COMPLETE = "readback-complete"
internal const val GPU_QUEUE_COMPLETION_PRESENT_FAILED = "present-failed"
internal const val GPU_QUEUE_COMPLETION_PRESENTED = "presented"
internal const val GPU_QUEUE_COMPLETION_TARGET_CLOSE = "target-close"
internal const val GPU_QUEUE_SUBMISSION_HISTORY_LIMIT = 128
