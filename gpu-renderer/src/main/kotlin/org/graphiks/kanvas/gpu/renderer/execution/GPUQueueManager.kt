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
        require(value.isNotBlank()) { "GPUQueuedResourceRef.value must not be blank" }
        require(value.isQueueDumpSafeToken()) {
            "GPUQueuedResourceRef.value must use dump-safe GPU evidence labels"
        }
    }
}

data class GPUQueueSubmission(
    val id: GPUQueueSubmissionId,
    val label: String,
    val retainedResources: List<GPUQueuedResourceRef>,
    val completed: Boolean = false,
    val released: Boolean = false,
    val completion: String = GPU_QUEUE_COMPLETION_PENDING,
) {
    init {
        require(label.isNotBlank()) { "GPUQueueSubmission.label must not be blank" }
        require(label.isQueueDumpSafeToken()) { "GPUQueueSubmission.label must be dump-safe" }
        require(completion.isQueueDumpSafeToken()) { "GPUQueueSubmission.completion must be dump-safe" }
    }
}

data class GPUQueueTelemetry(
    val submitted: Long = 0L,
    val completed: Long = 0L,
    val released: Long = 0L,
    val pending: Long = 0L,
    val waits: Long = 0L,
    val unknownCompletions: Long = 0L,
    val submissions: List<GPUQueueSubmission> = emptyList(),
) {
    fun dumpLines(): List<String> =
        listOf(
            "gpu-queue.telemetry submitted=$submitted completed=$completed released=$released " +
                "pending=$pending waits=$waits unknownCompletions=$unknownCompletions",
        ) + submissions.map { submission ->
            "gpu-queue.submission id=${submission.id.value} label=${submission.label} " +
                "retained=${submission.retainedResources.size} completed=${submission.completed} " +
                "released=${submission.released} completion=${submission.completion}"
        }
}

class GPUQueueManager {
    private var nextSubmissionId: Long = 1L
    private val submissions = linkedMapOf<GPUQueueSubmissionId, GPUQueueSubmission>()
    private var waitCount: Long = 0L
    private var unknownCompletionCount: Long = 0L

    val telemetry: GPUQueueTelemetry
        get() {
            val orderedSubmissions = submissions.values.toList()
            return GPUQueueTelemetry(
                submitted = orderedSubmissions.size.toLong(),
                completed = orderedSubmissions.count { submission -> submission.completed }.toLong(),
                released = orderedSubmissions.count { submission -> submission.released }.toLong(),
                pending = orderedSubmissions.count { submission -> !submission.completed }.toLong(),
                waits = waitCount,
                unknownCompletions = unknownCompletionCount,
                submissions = orderedSubmissions,
            )
        }

    fun submit(
        label: String,
        retainedResources: List<GPUQueuedResourceRef>,
    ): GPUQueueSubmission {
        val submission = GPUQueueSubmission(
            id = GPUQueueSubmissionId(nextSubmissionId++),
            label = label,
            retainedResources = retainedResources.toList(),
        )
        submissions[submission.id] = submission
        return submission
    }

    fun submitLeases(
        label: String,
        retainedLeases: List<GPUResourceLease>,
    ): GPUQueueSubmission =
        submit(
            label = label,
            retainedResources = retainedLeases.map { lease ->
                GPUQueuedResourceRef("lease:${lease.leaseId}")
            },
        )

    fun markCompleted(id: GPUQueueSubmissionId): Boolean =
        markCompleted(id, GPU_QUEUE_COMPLETION_PENDING)

    fun markCompleted(
        id: GPUQueueSubmissionId,
        completion: String,
    ): Boolean {
        require(completion.isQueueDumpSafeToken()) { "completion must be dump-safe" }
        val current = submissions[id]
        if (current == null) {
            unknownCompletionCount += 1L
            return false
        }
        if (!current.completed) {
            submissions[id] = current.copy(completed = true, completion = completion)
        }
        return true
    }

    fun pendingSubmissionIds(labelPrefix: String? = null): List<GPUQueueSubmissionId> {
        labelPrefix?.let { prefix ->
            require(prefix.isQueueDumpSafeToken()) { "labelPrefix must be dump-safe" }
        }
        return submissions.values
            .asSequence()
            .filter { submission -> !submission.completed }
            .filter { submission -> labelPrefix == null || submission.label.startsWith(labelPrefix) }
            .map { submission -> submission.id }
            .toList()
    }

    fun retainedResources(id: GPUQueueSubmissionId): List<GPUQueuedResourceRef> =
        submissions[id]
            ?.takeUnless { submission -> submission.released }
            ?.retainedResources
            .orEmpty()

    fun releaseCompleted(): List<GPUQueuedResourceRef> {
        val releasedResources = mutableListOf<GPUQueuedResourceRef>()
        submissions.entries.forEach { (id, submission) ->
            if (submission.completed && !submission.released) {
                releasedResources += submission.retainedResources
                submissions[id] = submission.copy(released = true)
            }
        }
        return releasedResources
    }

    fun recordWait() {
        waitCount += 1L
    }
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
internal const val GPU_QUEUE_COMPLETION_READBACK_COMPLETE = "readback-complete"
internal const val GPU_QUEUE_COMPLETION_PRESENTED = "presented"
internal const val GPU_QUEUE_COMPLETION_TARGET_CLOSE = "target-close"
