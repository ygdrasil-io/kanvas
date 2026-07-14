package org.graphiks.kanvas.gpu.renderer.execution

import java.util.concurrent.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GPUQueueCompletionAdapterTest {
    @Test
    fun `closed native gate refuses reservation before preflight`() {
        val adapter = GPUQueueCompletionAdapter.disabled(
            reasonCode = "unsupported.queue-completion.corrected-artifact-unavailable",
        )

        val refusal = assertIs<GPUQueueCompletionTicketReservation.Failed>(
            adapter.reserveTicket(ticketRequest(frameId = 1L)),
        )

        assertEquals(
            "unsupported.queue-completion.corrected-artifact-unavailable",
            refusal.diagnostic.code.value,
        )
    }

    @Test
    fun `reserved ticket stays unarmed until submit succeeds`() = runBlocking {
        var invocations = 0
        val adapter = enabledTestAdapter {
            invocations += 1
            Result.success(Unit)
        }
        val ticket = assertIs<GPUQueueCompletionTicketReservation.Reserved>(
            adapter.reserveTicket(ticketRequest(frameId = 2L)),
        ).ticket

        assertEquals(
            GPUQueueCompletionDelivery.Unarmed(ticket.ticketId),
            adapter.awaitCompletion(ticket),
        )
        assertEquals(0, invocations)
    }

    @Test
    fun `arm enters completion invoker before returning without waiting for completion`() = runBlocking {
        val release = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val adapter = enabledTestAdapter {
            events += "invoker-entered"
            release.await()
            events += "invoker-completed"
            Result.success(Unit)
        }
        val ticket = adapter.reserve(frameId = 3L)

        events += "submit"
        assertEquals(GPUQueueCompletionArmResult.Armed(ticket.ticketId), adapter.armAfterSubmit(ticket))
        events += "present"

        assertEquals(listOf("submit", "invoker-entered", "present"), events)
        release.complete(Unit)
        assertEquals(
            GPUQueueCompletionDelivery.Accepted(ticket.ticketId, GPUQueueCompletionOutcome.Success),
            adapter.awaitCompletion(ticket),
        )
    }

    @Test
    fun `many concurrent awaiters share one backend invocation`() = runBlocking {
        val release = CompletableDeferred<Unit>()
        var invocations = 0
        val adapter = enabledTestAdapter {
            invocations += 1
            release.await()
            Result.success(Unit)
        }
        val ticket = adapter.reserve(frameId = 4L)
        adapter.armAfterSubmit(ticket)

        val awaiters = List(8) { async { adapter.awaitCompletion(ticket) } }
        assertEquals(1, invocations)
        release.complete(Unit)

        assertTrue(
            awaiters.awaitAll().all {
                it == GPUQueueCompletionDelivery.Accepted(ticket.ticketId, GPUQueueCompletionOutcome.Success)
            },
        )
        assertEquals(1, invocations)
    }

    @Test
    fun `throw and result failure normalize to typed callback failure`() = runBlocking {
        listOf<GPUQueueCompletionInvoker>(
            GPUQueueCompletionInvoker { error("callback threw") },
            GPUQueueCompletionInvoker { Result.failure(IllegalStateException("callback failed")) },
        ).forEachIndexed { index, invoker ->
            val adapter = enabledTestAdapter(invoker)
            val ticket = adapter.reserve(frameId = 10L + index)
            adapter.armAfterSubmit(ticket)

            assertEquals(
                GPUQueueCompletionDelivery.Accepted(
                    ticket.ticketId,
                    GPUQueueCompletionOutcome.Failure(GPUQueueCompletionFailureKind.CallbackFailure),
                ),
                adapter.awaitCompletion(ticket),
            )
        }
    }

    @Test
    fun `thrown cancellation normalizes to typed cancellation`() = runBlocking {
        val adapter = enabledTestAdapter { throw CancellationException("cancelled") }
        val ticket = adapter.reserve(frameId = 20L)
        adapter.armAfterSubmit(ticket)

        assertEquals(
            GPUQueueCompletionDelivery.Accepted(
                ticket.ticketId,
                GPUQueueCompletionOutcome.Failure(GPUQueueCompletionFailureKind.Cancelled),
            ),
            adapter.awaitCompletion(ticket),
        )
    }

    @Test
    fun `explicit cancellation close and device loss terminalize exactly once`() = runBlocking {
        suspend fun verifyTerminal(
            frameId: Long,
            expected: GPUQueueCompletionFailureKind,
            terminalize: (GPUQueueCompletionAdapter, GPUQueueCompletionTicket) -> GPUQueueCompletionDelivery,
        ) {
            val release = CompletableDeferred<Unit>()
            val adapter = enabledTestAdapter {
                release.await()
                Result.success(Unit)
            }
            val ticket = adapter.reserve(frameId)
            adapter.armAfterSubmit(ticket)

            val first = terminalize(adapter, ticket)
            assertEquals(
                GPUQueueCompletionDelivery.Accepted(ticket.ticketId, GPUQueueCompletionOutcome.Failure(expected)),
                first,
            )
            assertEquals(first, adapter.awaitCompletion(ticket))
            assertEquals(GPUQueueCompletionDelivery.Duplicate(ticket.ticketId), terminalize(adapter, ticket))
        }

        verifyTerminal(30L, GPUQueueCompletionFailureKind.Cancelled) { adapter, ticket ->
            adapter.cancel(ticket)
        }
        var closeCalled = false
        verifyTerminal(31L, GPUQueueCompletionFailureKind.AdapterClosed) { adapter, ticket ->
            if (!closeCalled) {
                closeCalled = true
                adapter.close().single()
            } else {
                adapter.cancel(ticket)
            }
        }
        verifyTerminal(32L, GPUQueueCompletionFailureKind.DeviceLost) { adapter, ticket ->
            adapter.deviceLost(ticket.deviceGeneration).single()
        }
    }

    @Test
    fun `unknown duplicate and closed operations are deterministic`() = runBlocking {
        val adapter = enabledTestAdapter { Result.success(Unit) }
        val unknown = GPUQueueCompletionTicket(
            GPUQueueCompletionTicketID("ticket.unknown"),
            GPUFrameID(99L),
            GPUDeviceGenerationID(11L),
        )
        assertEquals(GPUQueueCompletionArmResult.Unknown(unknown.ticketId), adapter.armAfterSubmit(unknown))
        assertEquals(GPUQueueCompletionDelivery.Unknown(unknown.ticketId), adapter.awaitCompletion(unknown))

        val ticket = adapter.reserve(40L)
        assertEquals(GPUQueueCompletionArmResult.Armed(ticket.ticketId), adapter.armAfterSubmit(ticket))
        assertEquals(GPUQueueCompletionArmResult.Duplicate(ticket.ticketId), adapter.armAfterSubmit(ticket))
        val accepted = adapter.awaitCompletion(ticket)
        assertEquals(accepted, adapter.awaitCompletion(ticket))
        assertEquals(GPUQueueCompletionDelivery.Duplicate(ticket.ticketId), adapter.cancel(ticket))

        adapter.close()
        assertIs<GPUQueueCompletionTicketReservation.Failed>(adapter.reserveTicket(ticketRequest(41L)))
        Unit
    }

    @Test
    fun `revision capability and acceptance evidence must match exactly`() {
        val requirement = GPUQueueCompletionCapabilityRequirement("revision.expected", "completion-capability")
        listOf(
            GPUQueueCompletionCapabilityEvidence("revision.other", "completion-capability", true),
            GPUQueueCompletionCapabilityEvidence("revision.expected", "other-capability", true),
            GPUQueueCompletionCapabilityEvidence("revision.expected", "completion-capability", false),
        ).forEachIndexed { index, evidence ->
            val adapter = GPUQueueCompletionAdapter(requirement, evidence) { Result.success(Unit) }
            val refusal = assertIs<GPUQueueCompletionTicketReservation.Failed>(
                adapter.reserveTicket(ticketRequest(50L + index)),
            )
            assertTrue(refusal.diagnostic.code.value.startsWith("unsupported.queue-completion."))
        }
    }

    @Test
    fun `ordered many in flight tickets retain their own identity and generation`() = runBlocking {
        val releases = List(6) { CompletableDeferred<Unit>() }
        var invocation = 0
        val adapter = enabledTestAdapter {
            val current = invocation++
            releases[current].await()
            Result.success(Unit)
        }
        val tickets = (0 until 6).map { index -> adapter.reserve(60L + index) }
        tickets.forEach { adapter.armAfterSubmit(it) }
        assertEquals(6, invocation)

        releases.indices.reversed().forEach { releases[it].complete(Unit) }
        val deliveries = tickets.map { adapter.awaitCompletion(it) }

        assertEquals(tickets.map { it.ticketId }, deliveries.map { (it as GPUQueueCompletionDelivery.Accepted).ticketId })
        assertEquals(6, deliveries.map { (it as GPUQueueCompletionDelivery.Accepted).ticketId }.distinct().size)
        assertFalse(deliveries.any { it is GPUQueueCompletionDelivery.Unknown })
    }

    @Test
    fun `reserved ticket invalidated by close or device loss refuses later arm without hanging`() = runBlocking {
        val closedAdapter = enabledTestAdapter { Result.success(Unit) }
        val closedTicket = closedAdapter.reserve(70L)
        closedAdapter.close()
        assertEquals(
            GPUQueueCompletionArmResult.Refused(closedTicket.ticketId, GPUQueueCompletionFailureKind.AdapterClosed),
            closedAdapter.armAfterSubmit(closedTicket),
        )

        val lostAdapter = enabledTestAdapter { Result.success(Unit) }
        val lostTicket = lostAdapter.reserve(71L)
        lostAdapter.deviceLost(lostTicket.deviceGeneration)
        assertEquals(
            GPUQueueCompletionArmResult.Refused(lostTicket.ticketId, GPUQueueCompletionFailureKind.DeviceLost),
            lostAdapter.armAfterSubmit(lostTicket),
        )
        assertIs<GPUQueueCompletionTicketReservation.Failed>(
            lostAdapter.reserveTicket(ticketRequest(72L)),
        )
        Unit
    }

    @Test
    fun `canonical sink releases success and quarantines every terminal failure exactly once`() = runBlocking {
        suspend fun runCase(
            frameId: Long,
            expectedReleased: Boolean,
            invoker: GPUQueueCompletionInvoker,
            terminalize: ((GPUQueueCompletionAdapter, GPUQueueCompletionTicket) -> Unit)? = null,
        ) {
            val manager = GPUQueueManager()
            val adapter = enabledTestAdapter(invoker)
            val ticket = adapter.reserve(frameId)
            val resource = GPUQueuedResourceRef("lease:canonical:$frameId")
            val registration = assertIs<GPUQueueSubmissionRegistration.Accepted>(
                manager.tryRecordSubmitted(ticket, "frame:$frameId", listOf(resource)),
            )
            val submission = registration.submission

            adapter.armAfterSubmit(ticket, registration.completionSink)
            terminalize?.invoke(adapter, ticket)
            adapter.awaitCompletion(ticket)

            if (expectedReleased) {
                assertEquals(GPUQueueExecutionState.GPUCompleted, manager.submission(submission.id)?.executionState)
                assertEquals(1L, manager.telemetry.released)
                assertEquals(0L, manager.telemetry.quarantined)
            } else {
                assertEquals(GPUQueueExecutionState.FailedAfterSubmit, manager.submission(submission.id)?.executionState)
                assertEquals(0L, manager.telemetry.released)
                assertEquals(1L, manager.telemetry.quarantined)
                assertEquals(emptyList(), manager.releaseCompleted())
            }
        }

        runCase(80L, true, GPUQueueCompletionInvoker { Result.success(Unit) })
        runCase(81L, false, GPUQueueCompletionInvoker { Result.failure(IllegalStateException("failed")) })
        runCase(82L, false, pendingInvoker()) { adapter, ticket -> adapter.cancel(ticket) }
        runCase(83L, false, pendingInvoker()) { adapter, _ -> adapter.close() }
        runCase(84L, false, pendingInvoker()) { adapter, ticket -> adapter.deviceLost(ticket.deviceGeneration) }
    }

    @Test
    fun `terminal ticket storage remains bounded across thousands of frames`() = runBlocking {
        val adapter = enabledTestAdapter { Result.success(Unit) }
        val first = adapter.reserve(1_000L)

        repeat(2_000) { index ->
            val ticket = if (index == 0) first else adapter.reserve(1_000L + index)
            adapter.armAfterSubmit(ticket)
            adapter.awaitCompletion(ticket)
        }

        assertTrue(adapter.retainedTicketRecordCount <= GPU_QUEUE_COMPLETION_TOMBSTONE_LIMIT)
        assertEquals(
            GPUQueueCompletionDelivery.Expired(first.ticketId),
            adapter.awaitCompletion(first),
        )
    }
}

private fun enabledTestAdapter(
    invoker: GPUQueueCompletionInvoker,
): GPUQueueCompletionAdapter = GPUQueueCompletionAdapter(
    requirement = GPUQueueCompletionCapabilityRequirement(
        implementationRevision = "wgpu4k.test.revision",
        capability = "on-submitted-work-done",
    ),
    evidence = GPUQueueCompletionCapabilityEvidence(
        implementationRevision = "wgpu4k.test.revision",
        capability = "on-submitted-work-done",
        accepted = true,
    ),
    invoker = invoker,
)

private fun ticketRequest(frameId: Long): GPUQueueCompletionTicketRequest =
    GPUQueueCompletionTicketRequest(
        frameId = GPUFrameID(frameId),
        deviceGeneration = GPUDeviceGenerationID(11L),
    )

private fun GPUQueueCompletionAdapter.reserve(frameId: Long): GPUQueueCompletionTicket =
    assertIs<GPUQueueCompletionTicketReservation.Reserved>(reserveTicket(ticketRequest(frameId))).ticket

private fun GPUQueueCompletionAdapter.armAfterSubmit(
    ticket: GPUQueueCompletionTicket,
): GPUQueueCompletionArmResult = armAfterSubmit(ticket, GPUQueueCompletionSink { })

private fun pendingInvoker(): GPUQueueCompletionInvoker {
    val never = CompletableDeferred<Unit>()
    return GPUQueueCompletionInvoker {
        never.await()
        Result.success(Unit)
    }
}

private fun queueCompletionTicket(id: String, frameId: Long): GPUQueueCompletionTicket =
    GPUQueueCompletionTicket(
        ticketId = GPUQueueCompletionTicketID(id),
        frameId = GPUFrameID(frameId),
        deviceGeneration = GPUDeviceGenerationID(11L),
    )
