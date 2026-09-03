package org.graphiks.kanvas.gpu.renderer.planning

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.execution.GPUFrameCoordinator
import org.graphiks.kanvas.gpu.renderer.execution.GPUFrameCoordinatorFactory
import org.graphiks.kanvas.gpu.renderer.execution.GPUFrameCoreTestFixture
import org.graphiks.kanvas.gpu.renderer.execution.GPUFrameExecutionCompletedResult
import org.graphiks.kanvas.gpu.renderer.execution.GPUFrameExecutionHandle
import org.graphiks.kanvas.gpu.renderer.execution.GPUFrameExecutionPort
import org.graphiks.kanvas.gpu.renderer.execution.GPUFrameImmediateState
import org.graphiks.kanvas.gpu.renderer.execution.GPUFramePlanningPort
import org.graphiks.kanvas.gpu.renderer.execution.GPUFramePreflightPort
import org.graphiks.kanvas.gpu.renderer.execution.GPUFramePreflightResult
import org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedNativeFrameDraft
import org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedNativeFrameRegistration
import org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedSceneFrameSession
import org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedSceneNativeCounters
import org.graphiks.kanvas.gpu.renderer.execution.GPURuntimeResourceAdapter
import org.graphiks.kanvas.gpu.renderer.execution.GPUSceneFrameOutputRequest
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUFrameAttemptID
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUFrameAttemptTelemetrySink
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUFrameStructuralOutcome
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUFrameStructuralPhase

class GpuRenderRuntimePortsTest {
    @Test
    fun `interleaved prepared sessions report native payload registration per frame`() {
        val registry = GPURuntimeResourceAdapter()
        val firstRegistered = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val first = GpuPreparedSceneSessionAdapter(
            session(
                registry = registry,
                onSubmitted = {
                    firstRegistered.countDown()
                    check(releaseFirst.await(2, TimeUnit.SECONDS))
                },
            ),
        )
        val second = GpuPreparedSceneSessionAdapter(session(registry) {})
        val firstResult = AtomicReference<GpuPreparedFrameHandle?>()

        try {
            val firstThread = Thread {
                firstResult.set(
                    first.renderFrame(
                        GPUFrameCoreTestFixture.taskList(),
                        GPUSceneFrameOutputRequest.CurrentFrameCompletionOnly,
                        visualCommandCount = 0,
                    ),
                )
            }
            firstThread.start()
            assertTrue(firstRegistered.await(2, TimeUnit.SECONDS))

            val secondResult = second.renderFrame(
                GPUFrameCoreTestFixture.taskList(),
                GPUSceneFrameOutputRequest.CurrentFrameCompletionOnly,
                visualCommandCount = 0,
            )
            releaseFirst.countDown()
            firstThread.join(2_000)
            val firstHandle = assertNotNull(firstResult.get())

            assertEquals(1L, firstHandle.metricsSnapshot.nativeCounters.getValue("nativePayloadRegistrations"))
            assertEquals(1L, secondResult.metricsSnapshot.nativeCounters.getValue("nativePayloadRegistrations"))
            firstHandle.completion.toCompletableFuture().get(2, TimeUnit.SECONDS)
            secondResult.completion.toCompletableFuture().get(2, TimeUnit.SECONDS)
        } finally {
            releaseFirst.countDown()
            first.close()
            second.close()
            registry.close()
        }
    }

    private fun session(
        registry: GPURuntimeResourceAdapter,
        onSubmitted: () -> Unit,
    ): GPUPreparedSceneFrameSession = GPUPreparedSceneFrameSession(
        deviceGeneration = GPUDeviceGenerationID(7),
        coordinatorFactory = GPUFrameCoordinatorFactory { _, _ ->
            GPUFrameCoordinator(
                planner = GPUFramePlanningPort {
                    GPUFrameCoreTestFixture.preparedFrame().semanticPlan
                },
                preflighter = GPUFramePreflightPort {
                    val registration = assertIs<GPUPreparedNativeFrameRegistration.Registered>(
                        registry.registerPreparedNativeFrameDraft(
                            GPUPreparedNativeFrameDraft(GPUFrameCoreTestFixture.nativePayload()),
                        ),
                    )
                    GPUFramePreflightResult.Prepared(
                        GPUFrameCoreTestFixture.preparedFrame(
                            nativePayloadOwnership = registration.ownership,
                        ),
                    )
                },
                executor = GPUFrameExecutionPort { frame, attemptId, _ ->
                    onSubmitted()
                    GPUFrameExecutionHandle(
                        attemptId = attemptId,
                        immediateState = GPUFrameImmediateState.Submitted(frame.completionTicket.ticketId),
                        completion = CompletableFuture.completedFuture(completed(attemptId)),
                    )
                },
            )
        },
        nativeCountersFactory = {
            GPUPreparedSceneNativeCounters(
                nativePayloadRegistrations = registry.preparedNativeFramePayloadRegistrationCount,
            )
        },
    )

    private fun completed(attemptId: GPUFrameAttemptID): GPUFrameExecutionCompletedResult {
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
}
