package org.graphiks.kanvas.gpu.renderer.execution

import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnostic
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticCode
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticDomain
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticSeverity
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskList
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackRequestID
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUFrameAttemptID
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUFrameAttemptTelemetrySink
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUFrameStructuralOutcome
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUFrameStructuralPhase
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUFrameStructuralEventKind

class GPUFrameCoordinatorTest {
    @Test
    fun `attempt telemetry refuses phase regression before final sealing`() {
        val sink = GPUFrameAttemptTelemetrySink(GPUFrameAttemptID("attempt.monotonic"))
        sink.record(GPUFrameStructuralPhase.Preflight, GPUFrameStructuralEventKind.PreflightAccepted)

        assertFailsWith<IllegalArgumentException> {
            sink.record(GPUFrameStructuralPhase.Planning, GPUFrameStructuralEventKind.PlanningAccepted)
        }
    }

    @Test
    fun `attempt telemetry refuses an event outside its closed phase`() {
        val sink = GPUFrameAttemptTelemetrySink(GPUFrameAttemptID("attempt.event-phase"))

        assertFailsWith<IllegalArgumentException> {
            sink.record(GPUFrameStructuralPhase.Completed, GPUFrameStructuralEventKind.EncoderCreated)
        }
    }

    @Test
    fun `planning refusal completes immediately and never calls preflight or executor`() {
        val calls = mutableListOf<String>()
        val refused = diagnostic("refused.frame-planning.test")
        val coordinator = GPUFrameCoordinator(
            planner = GPUFramePlanningPort {
                calls += "planner"
                GPUFrameCoreTestFixture.refusedPlan(refused)
            },
            preflighter = GPUFramePreflightPort {
                calls += "preflight"
                error("must not run")
            },
            executor = GPUFrameExecutionPort { _, _, _ ->
                calls += "executor"
                error("must not run")
            },
            attemptIdFactory = { GPUFrameAttemptID("attempt.refused") },
        )

        val handle = coordinator.submit(
            GPUFrameCoreTestFixture.taskList(),
            GPUSceneFrameOutputRequest.CurrentFrameCompletionOnly,
        )

        assertEquals(listOf("planner"), calls)
        assertIs<GPUFrameImmediateState.Refused>(handle.immediateState)
        assertTrue(handle.completion.toCompletableFuture().isDone)
        val completed = handle.completion.toCompletableFuture().get(2, TimeUnit.SECONDS)
        assertEquals("refused.frame-planning.test", completed.diagnostic?.code?.value)
        assertEquals(GPUFrameStructuralPhase.Planning, completed.furthestPhase)
    }

    @Test
    fun `preflight refusal completes immediately and does not call executor`() {
        val calls = mutableListOf<String>()
        val prepared = GPUFrameCoreTestFixture.preparedFrame()
        val refusal = diagnostic("refused.frame-preflight.test")
        val coordinator = GPUFrameCoordinator(
            planner = GPUFramePlanningPort { prepared.semanticPlan.also { calls += "planner" } },
            preflighter = GPUFramePreflightPort {
                calls += "preflight"
                GPUFramePreflightResult.Refused(refusal)
            },
            executor = GPUFrameExecutionPort { _, _, _ ->
                calls += "executor"
                error("must not run")
            },
        )

        val handle = coordinator.submit(GPUFrameCoreTestFixture.taskList())

        assertEquals(listOf("planner", "preflight"), calls)
        assertIs<GPUFrameImmediateState.Refused>(handle.immediateState)
        assertTrue(handle.completion.toCompletableFuture().isDone)
        assertEquals(
            "refused.frame-preflight.test",
            handle.completion.toCompletableFuture().get(2, TimeUnit.SECONDS).diagnostic?.code?.value,
        )
    }

    @Test
    fun `successful coordinator calls executor once and preserves pending then final handle phases`() {
        val calls = mutableListOf<String>()
        val prepared = GPUFrameCoreTestFixture.preparedFrame()
        val pending = CompletableFuture<GPUFrameExecutionCompletedResult>()
        val coordinator = GPUFrameCoordinator(
            planner = GPUFramePlanningPort { prepared.semanticPlan.also { calls += "planner" } },
            preflighter = GPUFramePreflightPort {
                calls += "preflight"
                GPUFramePreflightResult.Prepared(prepared)
            },
            executor = GPUFrameExecutionPort { frame, attemptId, telemetry ->
                calls += "executor:${frame.semanticPlan.frameId.value}:${attemptId.value}"
                GPUFrameExecutionHandle(
                    attemptId = attemptId,
                    immediateState = GPUFrameImmediateState.Submitted(frame.completionTicket.ticketId),
                    completion = pending,
                )
            },
            attemptIdFactory = { GPUFrameAttemptID("attempt.success") },
        )

        val handle = coordinator.submit(GPUFrameCoreTestFixture.taskList())

        assertEquals(listOf("planner", "preflight", "executor:7:attempt.success"), calls)
        assertIs<GPUFrameImmediateState.Submitted>(handle.immediateState)
        assertFalse(handle.completion.toCompletableFuture().isDone)
        val sink = GPUFrameAttemptTelemetrySink(GPUFrameAttemptID("attempt.success"))
        val telemetry = sink.seal(
            furthestPhase = GPUFrameStructuralPhase.Completed,
            outcome = GPUFrameStructuralOutcome.Succeeded,
            diagnosticCode = null,
        )
        pending.complete(
            GPUFrameExecutionCompletedResult(
                attemptId = GPUFrameAttemptID("attempt.success"),
                furthestPhase = GPUFrameStructuralPhase.Completed,
                outcome = GPUFrameStructuralOutcome.Succeeded,
                diagnostic = null,
                encodedScopeKinds = emptyList(),
                telemetry = telemetry,
            ),
        )

        val completed = handle.completion.toCompletableFuture().get(2, TimeUnit.SECONDS)
        assertEquals(GPUFrameStructuralOutcome.Succeeded, completed.outcome)
        assertEquals("attempt.success", completed.attemptId.value)
        assertEquals(GPUSceneFrameOutput.CurrentFrameCompletionOnly, completed.output)
    }

    @Test
    fun `coordinator propagates an explicit asynchronous completion failure`() {
        val prepared = GPUFrameCoreTestFixture.preparedFrame()
        val pending = CompletableFuture<GPUFrameExecutionCompletedResult>()
        val attemptId = GPUFrameAttemptID("attempt.completion-failure")
        val coordinator = GPUFrameCoordinator(
            planner = GPUFramePlanningPort { prepared.semanticPlan },
            preflighter = GPUFramePreflightPort { GPUFramePreflightResult.Prepared(prepared) },
            executor = GPUFrameExecutionPort { frame, id, _ ->
                GPUFrameExecutionHandle(
                    attemptId = id,
                    immediateState = GPUFrameImmediateState.Submitted(frame.completionTicket.ticketId),
                    completion = pending,
                )
            },
            attemptIdFactory = { attemptId },
        )

        val handle = coordinator.submit(GPUFrameCoreTestFixture.taskList())
        val failure = diagnostic("failed.frame-execution.queue-completion")
        val sink = GPUFrameAttemptTelemetrySink(attemptId)
        val telemetry = sink.seal(
            furthestPhase = GPUFrameStructuralPhase.Completed,
            outcome = GPUFrameStructuralOutcome.Failed,
            diagnosticCode = failure.code.value,
        )
        pending.complete(
            GPUFrameExecutionCompletedResult(
                attemptId = attemptId,
                furthestPhase = GPUFrameStructuralPhase.Completed,
                outcome = GPUFrameStructuralOutcome.Failed,
                diagnostic = failure,
                encodedScopeKinds = listOf(GPUEncoderOperationKind.Render),
                telemetry = telemetry,
            ),
        )

        val completed = handle.completion.toCompletableFuture().get(2, TimeUnit.SECONDS)
        assertEquals(GPUFrameStructuralOutcome.Failed, completed.outcome)
        assertEquals(failure.code, completed.diagnostic?.code)
        assertEquals(listOf(GPUEncoderOperationKind.Render), completed.encodedScopeKinds)
        assertEquals(null, completed.output)
        assertEquals(telemetry, completed.telemetry)
    }

    @Test
    fun `readback output request without matching planned output refuses before executor`() {
        val calls = mutableListOf<String>()
        val prepared = GPUFrameCoreTestFixture.preparedFrame()
        val coordinator = GPUFrameCoordinator(
            planner = GPUFramePlanningPort { prepared.semanticPlan },
            preflighter = GPUFramePreflightPort { GPUFramePreflightResult.Prepared(prepared) },
            executor = GPUFrameExecutionPort { _, _, _ ->
                calls += "executor"
                error("must not run")
            },
        )

        val handle = coordinator.submit(
            GPUFrameCoreTestFixture.taskList(),
            GPUSceneFrameOutputRequest.ReadbackRgba(GPUReadbackRequestID("readback.missing")),
        )

        assertTrue(calls.isEmpty())
        assertIs<GPUFrameImmediateState.Refused>(handle.immediateState)
        assertEquals(
            "invalid.frame-coordinator.readback-output-missing",
            handle.completion.toCompletableFuture().get().diagnostic?.code?.value,
        )
    }

    @Test
    fun `matching terminal executor readback becomes the successful coordinator output`() {
        val attemptId = GPUFrameAttemptID("attempt.readback-success")
        val requestId = GPUReadbackRequestID("readback.main")
        val prepared = GPUFrameCoreTestFixture.preparedFrame(readbackRequestId = requestId)
        val bytes = byteArrayOf(0, 0, 0, 0, 255.toByte(), 0, 0, 255.toByte())
        val sink = GPUFrameAttemptTelemetrySink(attemptId)
        val telemetry = sink.seal(
            furthestPhase = GPUFrameStructuralPhase.Completed,
            outcome = GPUFrameStructuralOutcome.Succeeded,
            diagnosticCode = null,
        )
        val coordinator = GPUFrameCoordinator(
            planner = GPUFramePlanningPort { prepared.semanticPlan },
            preflighter = GPUFramePreflightPort { GPUFramePreflightResult.Prepared(prepared) },
            executor = GPUFrameExecutionPort { frame, id, _ ->
                GPUFrameExecutionHandle(
                    attemptId = id,
                    immediateState = GPUFrameImmediateState.Submitted(frame.completionTicket.ticketId),
                    completion = CompletableFuture.completedFuture(
                        GPUFrameExecutionCompletedResult(
                            attemptId = id,
                            furthestPhase = GPUFrameStructuralPhase.Completed,
                            outcome = GPUFrameStructuralOutcome.Succeeded,
                            diagnostic = null,
                            readback = GPUFrameExecutionReadback(requestId, bytes),
                            encodedScopeKinds = listOf(
                                GPUEncoderOperationKind.Render,
                                GPUEncoderOperationKind.Readback,
                            ),
                            telemetry = telemetry,
                        ),
                    ),
                )
            },
            attemptIdFactory = { attemptId },
        )

        val completed = coordinator.submit(
            GPUFrameCoreTestFixture.taskList(),
            GPUSceneFrameOutputRequest.ReadbackRgba(requestId),
        ).completion.toCompletableFuture().get(2, TimeUnit.SECONDS)

        val output = assertIs<GPUSceneFrameOutput.ReadbackRgba>(completed.output)
        assertEquals(requestId, output.requestId)
        assertContentEquals(bytes, output.bytes)
        bytes[0] = 99
        assertContentEquals(byteArrayOf(0, 0, 0, 0, 255.toByte(), 0, 0, 255.toByte()), output.bytes)
    }

    @Test
    fun `completion only refuses acquired and present host actions before executor`() {
        val calls = mutableListOf<String>()
        val rollbackEvents = mutableListOf<String>()
        val prepared = GPUFrameCoreTestFixture.preparedFrame(
            rollbackEvents = rollbackEvents,
            withHostActions = true,
        )
        val coordinator = GPUFrameCoordinator(
            planner = GPUFramePlanningPort { prepared.semanticPlan },
            preflighter = GPUFramePreflightPort { GPUFramePreflightResult.Prepared(prepared) },
            executor = GPUFrameExecutionPort { _, _, _ ->
                calls += "executor"
                error("must not run")
            },
        )

        val handle = coordinator.submit(GPUFrameCoreTestFixture.taskList())

        assertTrue(calls.isEmpty())
        assertEquals(listOf("rollback:resources"), rollbackEvents)
        assertEquals(
            "invalid.frame-coordinator.completion-host-output",
            handle.completion.toCompletableFuture().get().diagnostic?.code?.value,
        )
    }

    @Test
    fun `output refusal cannot rollback a frame already owned by execution`() {
        val calls = mutableListOf<String>()
        val rollbackEvents = mutableListOf<String>()
        val prepared = GPUFrameCoreTestFixture.preparedFrame(
            rollbackEvents = rollbackEvents,
            withHostActions = true,
        )
        assertTrue(prepared.claimForExecution())
        val coordinator = GPUFrameCoordinator(
            planner = GPUFramePlanningPort { prepared.semanticPlan },
            preflighter = GPUFramePreflightPort { GPUFramePreflightResult.Prepared(prepared) },
            executor = GPUFrameExecutionPort { _, _, _ ->
                calls += "executor"
                error("must not run")
            },
        )

        val result = coordinator.submit(GPUFrameCoreTestFixture.taskList())
            .completion.toCompletableFuture().get(2, TimeUnit.SECONDS)

        assertTrue(calls.isEmpty())
        assertTrue(rollbackEvents.isEmpty())
        assertEquals("failed.frame-coordinator.rollback-ownership", result.diagnostic?.code?.value)
    }

    @Test
    fun `task 10 output algebra is closed to completion and rgba readback`() {
        fun label(request: GPUSceneFrameOutputRequest): String = when (request) {
            GPUSceneFrameOutputRequest.CurrentFrameCompletionOnly -> "completion"
            is GPUSceneFrameOutputRequest.ReadbackRgba -> "readback:${request.requestId.value}"
            is GPUSceneFrameOutputRequest.PresentToWindow -> "present"
        }

        assertEquals("completion", label(GPUSceneFrameOutputRequest.CurrentFrameCompletionOnly))
        assertEquals(
            "readback:readback.main",
            label(GPUSceneFrameOutputRequest.ReadbackRgba(GPUReadbackRequestID("readback.main"))),
        )
    }

    @Test
    fun `terminal readback output owns immutable rgba bytes`() {
        val source = byteArrayOf(1, 2, 3, 4)
        val output = GPUSceneFrameOutput.ReadbackRgba(
            GPUReadbackRequestID("readback.main"),
            source,
        )

        source[0] = 9
        assertContentEquals(byteArrayOf(1, 2, 3, 4), output.bytes)
        val exposed = output.bytes
        exposed[1] = 9
        assertContentEquals(byteArrayOf(1, 2, 3, 4), output.bytes)
    }

    @Test
    fun `scene output request is closed to current frame completion or one rgba readback`() {
        assertEquals(
            setOf("CurrentFrameCompletionOnly", "ReadbackRgba", "PresentToWindow"),
            GPUSceneFrameOutputRequest::class.java.declaredClasses.map(Class<*>::getSimpleName).toSet(),
        )
    }

    @Test
    fun `prepared scene session refuses a concurrent frame before entering the coordinator`() {
        val executions = mutableListOf<GPUFrameAttemptID>()
        val pending = CompletableFuture<GPUFrameExecutionCompletedResult>()
        var nextAttempt = 0
        val session = GPUPreparedSceneFrameSession(
            GPUFrameCoordinator(
                planner = GPUFramePlanningPort { GPUFrameCoreTestFixture.preparedFrame().semanticPlan },
                preflighter = GPUFramePreflightPort {
                    GPUFramePreflightResult.Prepared(GPUFrameCoreTestFixture.preparedFrame())
                },
                executor = GPUFrameExecutionPort { frame, attemptId, _ ->
                    executions += attemptId
                    GPUFrameExecutionHandle(
                        attemptId = attemptId,
                        immediateState = GPUFrameImmediateState.Submitted(frame.completionTicket.ticketId),
                        completion = pending,
                    )
                },
                attemptIdFactory = { GPUFrameAttemptID("attempt.session.${++nextAttempt}") },
            ),
        )

        val first = session.renderFrame(GPUFrameCoreTestFixture.taskList())
        val concurrent = session.renderFrame(GPUFrameCoreTestFixture.taskList())

        assertIs<GPUFrameImmediateState.Submitted>(first.immediateState)
        assertIs<GPUFrameImmediateState.Refused>(concurrent.immediateState)
        assertEquals(
            "unsupported.prepared-scene-session.concurrent-frame",
            concurrent.completion.toCompletableFuture().get().diagnostic?.code?.value,
        )
        assertEquals(listOf(GPUFrameAttemptID("attempt.session.1")), executions)
    }

    @Test
    fun `closing a prepared scene session refuses new frames without completing its in flight frame`() {
        val attemptId = GPUFrameAttemptID("attempt.session.in-flight")
        val pending = CompletableFuture<GPUFrameExecutionCompletedResult>()
        var closes = 0
        val coordinator =
            GPUFrameCoordinator(
                planner = GPUFramePlanningPort { GPUFrameCoreTestFixture.preparedFrame().semanticPlan },
                preflighter = GPUFramePreflightPort {
                    GPUFramePreflightResult.Prepared(GPUFrameCoreTestFixture.preparedFrame())
                },
                executor = GPUFrameExecutionPort { frame, id, _ ->
                    GPUFrameExecutionHandle(
                        attemptId = id,
                        immediateState = GPUFrameImmediateState.Submitted(frame.completionTicket.ticketId),
                        completion = pending,
                    )
                },
                attemptIdFactory = { attemptId },
            )
        val session = GPUPreparedSceneFrameSession(
            coordinatorFactory = GPUFrameCoordinatorFactory { _, _ -> coordinator },
            closeAction = { closes += 1 },
        )

        val inFlight = session.renderFrame(GPUFrameCoreTestFixture.taskList())
        assertIs<AutoCloseable>(session).close()

        assertFalse(inFlight.completion.toCompletableFuture().isDone)
        assertEquals(0, closes)
        val afterClose = session.renderFrame(GPUFrameCoreTestFixture.taskList())
        assertIs<GPUFrameImmediateState.Refused>(afterClose.immediateState)
        assertEquals(
            "unsupported.prepared-scene-session.closed",
            afterClose.completion.toCompletableFuture().get().diagnostic?.code?.value,
        )

        pending.complete(successfulExecutionResult(attemptId))
        assertEquals(
            GPUFrameStructuralOutcome.Succeeded,
            inFlight.completion.toCompletableFuture().get(2, TimeUnit.SECONDS).outcome,
        )
        assertEquals(1, closes)
        session.close()
        assertEquals(1, closes)
    }

    @Test
    fun `prepared scene session invokes close action exactly once across repeated close`() {
        var closes = 0
        val session = GPUPreparedSceneFrameSession(
            coordinatorFactory = GPUFrameCoordinatorFactory { _, _ -> error("must not render") },
            closeAction = { closes += 1 },
        )

        session.close()
        session.close()

        assertEquals(1, closes)
    }

    @Test
    fun `throwing close action remains retryable until it completes once`() {
        var closes = 0
        var failuresRemaining = 1
        val session = GPUPreparedSceneFrameSession(
            coordinatorFactory = GPUFrameCoordinatorFactory { _, _ -> error("must not render") },
            closeAction = {
                closes += 1
                if (failuresRemaining > 0) {
                    failuresRemaining -= 1
                    error("close failed")
                }
            },
        )

        assertFailsWith<IllegalStateException> { session.close() }
        session.close()
        session.close()

        assertEquals(2, closes)
    }

    @Test
    fun `real prepared child session retains its lease across persistent tier failures`() {
        listOf("encoding", "mapping", "cache", "target").forEach { failingLabel ->
            val events = mutableListOf<String>()
            val probes = listOf("encoding", "mapping", "cache", "target").associateWith { label ->
                TierCloseProbe(
                    label = label,
                    closeFailuresRemaining = if (label == failingLabel) 2 else 0,
                    events = events,
                )
            }
            val registry = GPUPreparedSceneChildRegistry { events += "parent" }
            val lease = registry.reserve()
            val teardown = GPUPreparedSceneChildTeardown(
                ownerTiers = listOf(
                    GPUPreparedSceneChildOwnerTier(
                        label = "activity",
                        owners = listOf(probes.getValue("encoding"), probes.getValue("mapping")),
                    ),
                    GPUPreparedSceneChildOwnerTier(
                        label = "cache",
                        owners = listOf(probes.getValue("cache")),
                    ),
                    GPUPreparedSceneChildOwnerTier(
                        label = "target",
                        owners = listOf(probes.getValue("target")),
                    ),
                ),
                releaseLease = AutoCloseable {
                    events += "lease"
                    lease.close()
                },
            )
            val child = GPUPreparedSceneFrameSession(
                coordinatorFactory = GPUFrameCoordinatorFactory { _, _ -> error("must not render") },
                closeAction = teardown::close,
            )
            lease.bind(child)

            assertFailsWith<GPUOwnedNativeCloseIncompleteException>(failingLabel) { child.close() }
            assertEquals(0, probes.getValue("target").successfulCloses, failingLabel)
            assertFalse("lease" in events, failingLabel)
            assertFalse("parent" in events, failingLabel)

            assertFailsWith<GPUOwnedNativeCloseIncompleteException>(failingLabel) { registry.close() }
            assertEquals(0, probes.getValue("target").successfulCloses, failingLabel)
            assertFalse("lease" in events, failingLabel)
            assertFalse("parent" in events, failingLabel)

            registry.close()
            registry.close()

            probes.values.forEach { probe -> assertEquals(1, probe.successfulCloses, failingLabel) }
            assertEquals(3, probes.getValue(failingLabel).closeAttempts, failingLabel)
            probes.filterKeys { it != failingLabel }.values.forEach { probe ->
                assertEquals(1, probe.closeAttempts, failingLabel)
            }
            val cacheIndex = events.indexOf("cache")
            assertTrue(events.indexOf("encoding") in 0 until cacheIndex, failingLabel)
            assertTrue(events.indexOf("mapping") in 0 until cacheIndex, failingLabel)
            assertTrue(cacheIndex < events.indexOf("target"), failingLabel)
            assertTrue(events.indexOf("target") < events.indexOf("lease"), failingLabel)
            assertTrue(events.indexOf("lease") < events.indexOf("parent"), failingLabel)
            assertEquals(1, events.count { it == "lease" }, failingLabel)
            assertEquals(1, events.count { it == "parent" }, failingLabel)
        }
    }

    @Test
    fun `backend child registry closes idle child before parent teardown`() {
        val events = mutableListOf<String>()
        val registry = GPUPreparedSceneChildRegistry { events += "parent-teardown" }
        val lease = registry.reserve()
        val child = GPUPreparedSceneFrameSession(
            coordinatorFactory = GPUFrameCoordinatorFactory { _, _ -> error("must not render") },
            closeAction = {
                events += "child-close"
                lease.close()
            },
        )
        lease.bind(child)

        registry.close()
        registry.close()

        assertEquals(listOf("child-close", "parent-teardown"), events)
    }

    @Test
    fun `backend child registry defers parent teardown until in flight child completion`() {
        val events = mutableListOf<String>()
        val pending = CompletableFuture<GPUFrameExecutionCompletedResult>()
        val attemptId = GPUFrameAttemptID("attempt.parent-close")
        val coordinator = GPUFrameCoordinator(
            planner = GPUFramePlanningPort { GPUFrameCoreTestFixture.preparedFrame().semanticPlan },
            preflighter = GPUFramePreflightPort {
                GPUFramePreflightResult.Prepared(GPUFrameCoreTestFixture.preparedFrame())
            },
            executor = GPUFrameExecutionPort { frame, id, _ ->
                GPUFrameExecutionHandle(
                    attemptId = id,
                    immediateState = GPUFrameImmediateState.Submitted(frame.completionTicket.ticketId),
                    completion = pending,
                )
            },
            attemptIdFactory = { attemptId },
        )
        val registry = GPUPreparedSceneChildRegistry { events += "parent-teardown" }
        val lease = registry.reserve()
        val child = GPUPreparedSceneFrameSession(
            coordinatorFactory = GPUFrameCoordinatorFactory { _, _ -> coordinator },
            closeAction = {
                events += "child-close"
                lease.close()
            },
        )
        lease.bind(child)
        val inFlight = child.renderFrame(GPUFrameCoreTestFixture.taskList())

        registry.close()

        assertFalse(inFlight.completion.toCompletableFuture().isDone)
        assertTrue(events.isEmpty())
        pending.complete(successfulExecutionResult(attemptId))
        inFlight.completion.toCompletableFuture().get(2, TimeUnit.SECONDS)
        assertEquals(listOf("child-close", "parent-teardown"), events)
    }

    private fun successfulExecutionResult(attemptId: GPUFrameAttemptID): GPUFrameExecutionCompletedResult {
        val telemetry = GPUFrameAttemptTelemetrySink(attemptId).seal(
            furthestPhase = GPUFrameStructuralPhase.Completed,
            outcome = GPUFrameStructuralOutcome.Succeeded,
            diagnosticCode = null,
        )
        return GPUFrameExecutionCompletedResult(
            attemptId = attemptId,
            furthestPhase = GPUFrameStructuralPhase.Completed,
            outcome = GPUFrameStructuralOutcome.Succeeded,
            diagnostic = null,
            encodedScopeKinds = emptyList(),
            telemetry = telemetry,
        )
    }

    private class TierCloseProbe(
        private val label: String,
        private var closeFailuresRemaining: Int,
        private val events: MutableList<String>,
    ) : AutoCloseable {
        var closeAttempts: Int = 0
            private set
        var successfulCloses: Int = 0
            private set

        override fun close() {
            closeAttempts += 1
            if (closeFailuresRemaining > 0) {
                closeFailuresRemaining -= 1
                error("$label close failed")
            }
            check(successfulCloses == 0) { "$label closed more than once" }
            successfulCloses += 1
            events += label
        }
    }

    private fun diagnostic(code: String): GPUDiagnostic = GPUDiagnostic(
        code = GPUDiagnosticCode(code),
        domain = GPUDiagnosticDomain.Execution,
        severity = GPUDiagnosticSeverity.Error,
        message = code,
    )
}
