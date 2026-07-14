package org.graphiks.kanvas.gpu.renderer.execution

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

/** Pure reservation request; no callback or backend registration is armed in Task 8. */
data class GPUQueueCompletionTicketRequest(
    val frameId: GPUFrameID,
    val deviceGeneration: GPUDeviceGenerationID,
)

/**
 * Monotone completion identity reserved before the late surface acquisition.
 *
 * This is deliberately not an armed callback and owns no queue/native state. A ticket abandoned
 * before submission therefore has nothing to cancel. Task 9 binds the identity to real completion.
 */
data class GPUQueueCompletionTicket(
    val ticketId: GPUQueueCompletionTicketID,
    val frameId: GPUFrameID,
    val deviceGeneration: GPUDeviceGenerationID,
)

/** Typed outcome of reserving exactly one completion identity for a frame. */
sealed interface GPUQueueCompletionTicketReservation {
    data class Reserved(val ticket: GPUQueueCompletionTicket) : GPUQueueCompletionTicketReservation
    data object Missing : GPUQueueCompletionTicketReservation
    data class Failed(val diagnostic: GPUDiagnostic) : GPUQueueCompletionTicketReservation
    data class Duplicate(val ticketId: GPUQueueCompletionTicketID) : GPUQueueCompletionTicketReservation
}

/** Handle-free Task 8 seam. A wgpu4k-backed completion implementation belongs to Task 9. */
fun interface GPUQueueCompletionProvider {
    fun reserveTicket(request: GPUQueueCompletionTicketRequest): GPUQueueCompletionTicketReservation
}
