package org.graphiks.kanvas.gpu.renderer.execution

import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceLease
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceLeaseCacheResult
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceLeaseKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GPUQueueManagerTest {
    @Test
    fun `presentation is output state and cannot complete submitted GPU work`() {
        val manager = GPUQueueManager()
        val ticket = queueCompletionTicket("ticket.presentation", frameId = 1L)
        val resource = GPUQueuedResourceRef("target:window-1")

        val submission = manager.recordSubmitted(
            ticket = ticket,
            label = "window-frame:frame-1",
            retainedResources = listOf(resource),
            outputApplicable = true,
        )

        assertEquals(GPUQueueOutputUpdate.Accepted, manager.recordPresented(submission.id))
        assertEquals(GPUQueueExecutionState.Submitted, manager.submission(submission.id)?.executionState)
        assertEquals(GPUQueueOutputState.Presented, manager.submission(submission.id)?.outputState)
        assertEquals(listOf(resource), manager.retainedResources(submission.id))
        assertEquals(0L, manager.telemetry.completed)
        assertEquals(0L, manager.telemetry.released)
    }

    @Test
    fun `real GPU success releases reusable resources exactly once`() {
        val manager = GPUQueueManager()
        val ticket = queueCompletionTicket("ticket.success", frameId = 2L)
        val resource = GPUQueuedResourceRef("lease:uniform-slab:frame-2")
        val submission = manager.recordSubmitted(
            ticket = ticket,
            label = "offscreen-pass:frame-2",
            retainedResources = listOf(resource),
        )

        assertEquals(
            GPUQueueCompletionAcceptance.Accepted(
                submissionId = submission.id,
                releasedResources = listOf(resource),
                quarantinedResources = emptyList(),
            ),
            manager.acceptGPUCompletion(ticket.ticketId, GPUQueueCompletionOutcome.Success),
        )
        assertEquals(
            GPUQueueCompletionAcceptance.Duplicate(ticket.ticketId),
            manager.acceptGPUCompletion(ticket.ticketId, GPUQueueCompletionOutcome.Success),
        )
        assertEquals(GPUQueueExecutionState.GPUCompleted, manager.submission(submission.id)?.executionState)
        assertTrue(manager.retainedResources(submission.id).isEmpty())
        assertEquals(1L, manager.telemetry.completed)
        assertEquals(1L, manager.telemetry.released)
    }

    @Test
    fun `GPU failure quarantines resources and release completed cannot repool them`() {
        val manager = GPUQueueManager()
        val ticket = queueCompletionTicket("ticket.failure", frameId = 3L)
        val resource = GPUQueuedResourceRef("lease:pipeline:frame-3")
        val submission = manager.recordSubmitted(
            ticket = ticket,
            label = "offscreen-pass:frame-3",
            retainedResources = listOf(resource),
        )

        assertEquals(
            GPUQueueCompletionAcceptance.Accepted(submission.id, emptyList(), listOf(resource)),
            manager.acceptGPUCompletion(
                ticket.ticketId,
                GPUQueueCompletionOutcome.Failure(GPUQueueCompletionFailureKind.DeviceLost),
            ),
        )
        assertEquals(emptyList(), manager.releaseCompleted())
        assertEquals(emptyList(), manager.quarantinedResources(submission.id))
        assertEquals(emptyList(), manager.retainedResources(submission.id))
        assertEquals(1L, manager.telemetry.quarantined)
        assertEquals(0L, manager.telemetry.released)
    }

    @Test
    fun `legacy presentation readback and target close events never fabricate completion`() {
        val legacyReasons = listOf(
            GPU_QUEUE_COMPLETION_PRESENTED,
            GPU_QUEUE_COMPLETION_PRESENT_FAILED,
            GPU_QUEUE_COMPLETION_READBACK_COMPLETE,
            GPU_QUEUE_COMPLETION_TARGET_CLOSE,
        )

        legacyReasons.forEachIndexed { index, reason ->
            val manager = GPUQueueManager()
            val resource = GPUQueuedResourceRef("lease:legacy:$index")
            val submission = manager.submit("legacy-frame:$index", listOf(resource))

            assertTrue(manager.markCompleted(submission.id, reason))
            assertEquals(emptyList(), manager.releaseCompleted())
            assertEquals(GPUQueueExecutionState.Submitted, manager.submission(submission.id)?.executionState)
            assertEquals(listOf(resource), manager.retainedResources(submission.id))
            assertEquals(0L, manager.telemetry.completed)
            assertEquals(0L, manager.telemetry.released)
        }
    }

    @Test
    fun `duplicate completion ticket is refused without orphaning first submission`() {
        val manager = GPUQueueManager()
        val ticket = queueCompletionTicket("ticket.duplicate", frameId = 4L)
        val first = assertIs<GPUQueueSubmissionRegistration.Accepted>(
            manager.tryRecordSubmitted(ticket, "frame:first", listOf(GPUQueuedResourceRef("lease:first"))),
        ).submission

        assertEquals(
            GPUQueueSubmissionRegistration.Duplicate(ticket.ticketId, first.id),
            manager.tryRecordSubmitted(ticket, "frame:second", listOf(GPUQueuedResourceRef("lease:second"))),
        )
        assertEquals(
            GPUQueueCompletionAcceptance.Accepted(first.id, listOf(GPUQueuedResourceRef("lease:first")), emptyList()),
            manager.acceptGPUCompletion(ticket.ticketId, GPUQueueCompletionOutcome.Success),
        )
        assertEquals(
            GPUQueueSubmissionRegistration.Duplicate(ticket.ticketId, first.id),
            manager.tryRecordSubmitted(ticket, "frame:after-terminal", listOf(GPUQueuedResourceRef("lease:third"))),
        )
        assertEquals(1L, manager.telemetry.submitted)
    }

    @Test
    fun `output transitions are closed and present failure stays independent`() {
        val manager = GPUQueueManager()
        val noOutput = manager.recordSubmitted(
            queueCompletionTicket("ticket.no-output", 5L),
            "offscreen:frame-5",
            emptyList(),
        )
        assertEquals(
            GPUQueueOutputUpdate.Invalid(noOutput.id, GPUQueueOutputState.NotApplicable, GPUQueueOutputState.Presented),
            manager.recordPresented(noOutput.id),
        )

        val output = manager.recordSubmitted(
            queueCompletionTicket("ticket.present-failed", 6L),
            "window:frame-6",
            listOf(GPUQueuedResourceRef("lease:window:6")),
            outputApplicable = true,
        )
        assertEquals(GPUQueueOutputUpdate.Accepted, manager.recordPresentFailed(output.id))
        assertEquals(GPUQueueOutputState.PresentFailed, manager.submission(output.id)?.outputState)
        assertEquals(GPUQueueExecutionState.Submitted, manager.submission(output.id)?.executionState)
        assertEquals(emptyList(), manager.releaseCompleted())
    }

    @Test
    fun `unknown and duplicate GPU callbacks are typed and do not change other submissions`() {
        val manager = GPUQueueManager()
        val firstTicket = queueCompletionTicket("ticket.first", 7L)
        val secondTicket = queueCompletionTicket("ticket.second", 8L)
        val first = manager.recordSubmitted(firstTicket, "frame:7", listOf(GPUQueuedResourceRef("lease:7")))
        val second = manager.recordSubmitted(secondTicket, "frame:8", listOf(GPUQueuedResourceRef("lease:8")))
        val unknown = GPUQueueCompletionTicketID("ticket.unknown")

        assertEquals(GPUQueueCompletionAcceptance.Unknown(unknown), manager.acceptGPUCompletion(unknown, GPUQueueCompletionOutcome.Success))
        assertIs<GPUQueueCompletionAcceptance.Accepted>(manager.acceptGPUCompletion(secondTicket.ticketId, GPUQueueCompletionOutcome.Success))
        assertEquals(GPUQueueCompletionAcceptance.Duplicate(secondTicket.ticketId), manager.acceptGPUCompletion(secondTicket.ticketId, GPUQueueCompletionOutcome.Success))
        assertEquals(GPUQueueExecutionState.Submitted, manager.submission(first.id)?.executionState)
        assertEquals(GPUQueueExecutionState.GPUCompleted, manager.submission(second.id)?.executionState)
    }

    @Test
    fun `terminal submission history stays bounded and detaches resource refs`() {
        val manager = GPUQueueManager()
        val firstTicket = queueCompletionTicket("queue-ticket.77.1.11.1000", 1_000L)

        repeat(2_000) { index ->
            val ticket = if (index == 0) {
                firstTicket
            } else {
                queueCompletionTicket("queue-ticket.77.${index + 1}.11.${1_000 + index}", 1_000L + index)
            }
            val registration = assertIs<GPUQueueSubmissionRegistration.Accepted>(
                manager.tryRecordSubmitted(
                    ticket,
                    "frame:${1_000 + index}",
                    listOf(GPUQueuedResourceRef("lease:${1_000 + index}")),
                ),
            )
            manager.acceptGPUCompletion(ticket.ticketId, GPUQueueCompletionOutcome.Success)
            assertTrue(registration.submission.retainedResources.isNotEmpty())
        }

        assertEquals(2_000L, manager.telemetry.submitted)
        assertEquals(2_000L, manager.telemetry.completed)
        assertTrue(manager.telemetry.submissions.size <= GPU_QUEUE_SUBMISSION_HISTORY_LIMIT)
        assertTrue(manager.telemetry.submissions.all { it.retainedResources.isEmpty() })
        assertTrue(
            manager.retainedSubmissionRecordCount <= GPU_QUEUE_SUBMISSION_HISTORY_LIMIT * 3,
        )
        assertEquals(
            GPUQueueCompletionAcceptance.Unknown(firstTicket.ticketId),
            manager.acceptGPUCompletion(firstTicket.ticketId, GPUQueueCompletionOutcome.Success),
        )
    }

    @Test
    fun `queue manager retains lease resources while submission is pending`() {
        val manager = GPUQueueManager()
        val lease = queueLease("uniform-slab:frame-1")

        val submission = manager.submitLeases(
            label = "frame-1",
            retainedLeases = listOf(lease),
        )

        assertEquals(listOf(submission.id), manager.pendingSubmissionIds())
        assertEquals(listOf(GPUQueuedResourceRef("lease:uniform-slab:frame-1")), manager.retainedResources(submission.id))
        assertEquals(emptyList(), manager.releaseCompleted())

        val dump = manager.telemetry.dumpLines().joinToString("\n")
        assertTrue(dump.contains("submitted=1 completed=0 released=0 pending=1 waits=0 unknownCompletions=0"))
        assertTrue(dump.contains("completion=pending"))
    }

    @Test
    fun `queue manager rejects pending as a completion reason`() {
        val manager = GPUQueueManager()
        val submission = manager.submit(
            label = "offscreen-pass:frame-1",
            retainedResources = listOf(GPUQueuedResourceRef("readback:frame-1")),
        )

        assertFailsWith<IllegalArgumentException> {
            manager.markCompleted(submission.id, GPU_QUEUE_COMPLETION_PENDING)
        }

        assertEquals(listOf(submission.id), manager.pendingSubmissionIds())
        val dump = manager.telemetry.dumpLines().joinToString("\n")
        assertTrue(dump.contains("submitted=1 completed=0 released=0 pending=1 waits=0 unknownCompletions=0"))
        assertTrue(dump.contains("completion=pending"))
    }

    @Test
    fun `queue manager pending ids can be filtered by label prefix`() {
        val manager = GPUQueueManager()
        val first = manager.submit(
            label = "offscreen-pass:frame-1",
            retainedResources = listOf(GPUQueuedResourceRef("target:offscreen-1")),
        )
        manager.submit(
            label = "window-frame:frame-2",
            retainedResources = listOf(GPUQueuedResourceRef("target:window-1")),
        )

        assertEquals(listOf(first.id), manager.pendingSubmissionIds(labelPrefix = "offscreen-pass:"))
    }

    @Test
    fun `queue manager telemetry records waits and unknown completions`() {
        val manager = GPUQueueManager()

        manager.recordWait()
        assertFalse(manager.markCompleted(GPUQueueSubmissionId(99), GPU_QUEUE_COMPLETION_READBACK_COMPLETE))

        val dump = manager.telemetry.dumpLines().joinToString("\n")
        assertTrue(dump.contains("waits=1"))
        assertTrue(dump.contains("unknownCompletions=1"))
    }

    @Test
    fun `queue manager rejects unsafe dump tokens`() {
        val manager = GPUQueueManager()
        val submission = manager.submit(
            label = "frame-1",
            retainedResources = listOf(GPUQueuedResourceRef("target:frame-1")),
        )

        assertFailsWith<IllegalArgumentException> {
            GPUQueuedResourceRef("bad ref")
        }
        assertFailsWith<IllegalArgumentException> {
            manager.submit(
                label = "bad label",
                retainedResources = listOf(GPUQueuedResourceRef("target:frame-1")),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            manager.pendingSubmissionIds(labelPrefix = "bad prefix")
        }
        assertFailsWith<IllegalArgumentException> {
            manager.markCompleted(submission.id, "bad reason")
        }
    }

}

private fun queueLease(leaseId: String): GPUResourceLease =
    GPUResourceLease(
        leaseId = leaseId,
        resourceKind = GPUResourceLeaseKind.UniformSlab,
        deviceGeneration = 11,
        descriptorHash = "sha256:uniform-slab-frame-1",
        ownerScope = "frame-1",
        usageLabels = listOf("copy_dst", "uniform"),
        releasePolicy = "submission-complete",
        cacheResult = GPUResourceLeaseCacheResult.Create,
    )

private fun queueCompletionTicket(id: String, frameId: Long): GPUQueueCompletionTicket =
    GPUQueueCompletionTicket(
        ticketId = GPUQueueCompletionTicketID(id),
        frameId = org.graphiks.kanvas.gpu.renderer.recording.GPUFrameID(frameId),
        deviceGeneration = org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID(11L),
    )

private fun GPUQueueManager.recordSubmitted(
    ticket: GPUQueueCompletionTicket,
    label: String,
    retainedResources: List<GPUQueuedResourceRef>,
    outputApplicable: Boolean = false,
): GPUQueueSubmission = assertIs<GPUQueueSubmissionRegistration.Accepted>(
    tryRecordSubmitted(ticket, label, retainedResources, outputApplicable),
).submission
