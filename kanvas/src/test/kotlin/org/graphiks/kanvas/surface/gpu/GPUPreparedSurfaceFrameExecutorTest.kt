package org.graphiks.kanvas.surface.gpu

import java.util.concurrent.CompletableFuture
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.graphiks.kanvas.canvas.ClipStack
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilityFact
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUFirstSliceCapabilityName.PATH_FILL_STENCIL_COVER
import org.graphiks.kanvas.gpu.renderer.capabilities.GPULimits
import org.graphiks.kanvas.gpu.renderer.capabilities.GPURendererFeature
import org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnostic
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticCode
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticDomain
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticSeverity
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeTelemetry
import org.graphiks.kanvas.gpu.renderer.execution.GPUOffscreenTargetRequest
import org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedSceneNativeCounters
import org.graphiks.kanvas.gpu.renderer.product.GPUProductFlagConfig
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackRequestID
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameID
import org.graphiks.kanvas.gpu.renderer.recording.GPURecordingID
import org.graphiks.kanvas.gpu.renderer.recording.GPUTask
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskList
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUFrameAttemptID
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUFrameStructuralOutcome
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.surface.RenderConfig
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Rect

class GPUPreparedSurfaceFrameExecutorTest {
    @Test
    fun `execution values defensively own every readback array`() {
        val attempt = GPUFrameAttemptID("attempt")
        val readback = GPUReadbackRequestID("readback")
        val completionSource = byteArrayOf(1, 2, 3, 4)
        val completion = GPUPreparedSurfaceCompletion(
            attempt, GPUFrameStructuralOutcome.Succeeded, null, readback, completionSource,
        )
        completionSource[0] = 9
        val first = completion.rgba!!
        first[1] = 9
        assertContentEquals(byteArrayOf(1, 2, 3, 4), completion.rgba)

        val successSource = byteArrayOf(5, 6, 7, 8)
        val success = GPUPreparedSurfaceExecutionResult.Succeeded(successSource, 1, 0, evidence())
        successSource[0] = 9
        val returned = success.rgba
        returned[1] = 9
        assertContentEquals(byteArrayOf(5, 6, 7, 8), success.rgba)
    }

    @Test
    fun `backend and capabilities unavailable refuse before prepared entry`() {
        val request = request()
        val unavailable = GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceBackendPortFactory { null })
        val noCapabilitiesBackend = FakeBackend(capabilities = null, session = FakeSession())
        val noCapabilities = GPUPreparedSurfaceFrameExecutor(
            GPUPreparedSurfaceBackendPortFactory { noCapabilitiesBackend },
        )

        assertEquals(
            "unavailable.surface.prepared.backend",
            assertIs<GPUPreparedSurfaceExecutionResult.BeforePreparedEntryRefused>(
                unavailable.execute(request),
            ).diagnostic.code.value,
        )
        assertEquals(
            "legacy.surface.prepared.runtime-capabilities-unavailable",
            assertIs<GPUPreparedSurfaceExecutionResult.BeforePreparedEntryRefused>(
                noCapabilities.execute(request),
            ).diagnostic.code.value,
        )
        assertEquals(0, noCapabilitiesBackend.prepareCalls)
        assertEquals(1, noCapabilitiesBackend.closeCalls)
    }

    @Test
    fun `success preserves build facts and returns exact frame-local evidence after close`() {
        val session = FakeSession()
        val backend = FakeBackend(capabilities(), session)
        val executor = GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceBackendPortFactory { backend })

        val success = assertIs<GPUPreparedSurfaceExecutionResult.Succeeded>(executor.execute(request()))

        assertContentEquals(ByteArray(16) { (it + 1).toByte() }, success.rgba)
        assertEquals(1, success.visualOperationCount)
        assertEquals(0, success.stateEventCount)
        assertEquals(evidence(), success.evidence)
        assertEquals(1, backend.prepareCalls)
        assertEquals(1, backend.closeCalls)
        assertEquals(1, session.submitCalls)
        assertEquals(1, session.closeCalls)
        assertEquals(1, backend.preparedRequests.single().width)
        assertEquals(4, backend.preparedRequests.single().height)
        assertEquals(request().candidate.color.physicalFormat, backend.preparedRequests.single().colorFormat)
        assertEquals(request().candidate.color.interpretation, backend.preparedRequests.single().colorInterpretation)
        assertEquals(session.submittedReadbackIds.single(), session.completedReadbackIds.single())

        val taskList = session.submittedTaskLists.single()
        assertEquals(GPUDeviceGenerationID(91), taskList.capabilitySeal.deviceGeneration)
        val target = taskList.tasks.filterIsInstance<GPUTask.PrepareResources>()
            .flatMap(GPUTask.PrepareResources::requests)
            .single { it.role == GPUFrameResourceRole.SceneTarget }.resource
        assertIs<GPUFrameTargetRef>(target)
    }

    @Test
    fun `two executions allocate distinct target recording frame and readback identities`() {
        val sessions = mutableListOf<FakeSession>()
        val executor = GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceBackendPortFactory {
            FakeBackend(capabilities(), FakeSession().also(sessions::add))
        })

        assertIs<GPUPreparedSurfaceExecutionResult.Succeeded>(executor.execute(request()))
        assertIs<GPUPreparedSurfaceExecutionResult.Succeeded>(executor.execute(request()))

        val firstTasks = sessions[0].submittedTaskLists.single()
        val secondTasks = sessions[1].submittedTaskLists.single()
        assertNotEquals(firstTasks.frameId, secondTasks.frameId)
        assertNotEquals(firstTasks.recordingSeals.single().recordingId, secondTasks.recordingSeals.single().recordingId)
        assertNotEquals(sceneTarget(firstTasks), sceneTarget(secondTasks))
        assertNotEquals(sessions[0].submittedReadbackIds.single(), sessions[1].submittedReadbackIds.single())
    }

    @Test
    fun `pure builder refusal never prepares a session and closes the backend`() {
        val backend = FakeBackend(capabilities(fillRect = false), FakeSession())
        val executor = GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceBackendPortFactory { backend })

        val refusal = assertIs<GPUPreparedSurfaceExecutionResult.BeforePreparedEntryRefused>(
            executor.execute(request()),
        )

        assertEquals("unsupported.pipeline.capability_missing", refusal.diagnostic.code.value)
        assertEquals(0, backend.prepareCalls)
        assertEquals(1, backend.closeCalls)
    }

    @Test
    fun `prepared immediate completion mismatch is terminal and never becomes a refusal`() {
        val mismatch = FakeSession(submissionFactory = { readbackId ->
            val attempt = GPUFrameAttemptID("attempt-mismatch")
            GPUPreparedSurfaceSubmission(
                attempt,
                GPUPreparedSurfaceImmediateState.Submitted,
                CompletableFuture.completedFuture(
                    GPUPreparedSurfaceCompletion(
                        attempt,
                        GPUFrameStructuralOutcome.Refused,
                        diagnostic("refused.test.completion"),
                        readbackId,
                        null,
                    ),
                ),
            )
        })

        val failure = assertIs<GPUPreparedSurfaceExecutionResult.TerminalFailure>(
            GPUPreparedSurfaceFrameExecutor(
                GPUPreparedSurfaceBackendPortFactory { FakeBackend(capabilities(), mismatch) },
            ).execute(request()),
        )

        assertEquals("invalid.surface.prepared.immediate-completion", failure.diagnostic.code.value)
        assertEquals(1, mismatch.closeCalls)
    }

    @Test
    fun `prepare and submit exceptions are terminal after prepared entry`() {
        val prepareFailure = FakeBackend(
            capabilities(),
            FakeSession(),
            prepareFailure = IllegalArgumentException("unstable prepare detail"),
        )
        val submitSession = FakeSession(submitFailure = IllegalStateException("unstable submit detail"))

        val prepared = assertIs<GPUPreparedSurfaceExecutionResult.TerminalFailure>(
            GPUPreparedSurfaceFrameExecutor(
                GPUPreparedSurfaceBackendPortFactory { prepareFailure },
            ).execute(request()),
        )
        val submitted = assertIs<GPUPreparedSurfaceExecutionResult.TerminalFailure>(
            GPUPreparedSurfaceFrameExecutor(
                GPUPreparedSurfaceBackendPortFactory { FakeBackend(capabilities(), submitSession) },
            ).execute(request()),
        )

        assertEquals("failed.surface.prepared.session-prepare", prepared.diagnostic.code.value)
        assertEquals(IllegalArgumentException::class.java.name, prepared.diagnostic.facts["failureClass"])
        assertEquals(1, prepareFailure.closeCalls)
        assertEquals("failed.surface.prepared.completion", submitted.diagnostic.code.value)
        assertEquals(IllegalStateException::class.java.name, submitted.diagnostic.facts["failureClass"])
        assertEquals(1, submitSession.closeCalls)
    }

    @Test
    fun `all coherent immediate failure variants preserve the original diagnostic`() {
        val refused = diagnostic("refused.test.immediate")
        val failedBefore = diagnostic("failed.test.before-submit")
        val failedAfter = diagnostic("failed.test.after-submit")
        val submittedFailed = diagnostic("failed.test.submitted")
        val cases = listOf(
            Triple(GPUPreparedSurfaceImmediateState.Refused(refused), GPUFrameStructuralOutcome.Refused, refused),
            Triple(
                GPUPreparedSurfaceImmediateState.FailedBeforeSubmit(failedBefore),
                GPUFrameStructuralOutcome.Failed,
                failedBefore,
            ),
            Triple(
                GPUPreparedSurfaceImmediateState.FailedAfterSubmit(failedAfter),
                GPUFrameStructuralOutcome.Failed,
                failedAfter,
            ),
            Triple(GPUPreparedSurfaceImmediateState.Submitted, GPUFrameStructuralOutcome.Failed, submittedFailed),
        )

        cases.forEach { (immediate, outcome, expected) ->
            val session = FakeSession(submissionFactory = { readbackId ->
                val attempt = GPUFrameAttemptID("attempt-${expected.code.value}")
                GPUPreparedSurfaceSubmission(
                    attempt,
                    immediate,
                    CompletableFuture.completedFuture(
                        GPUPreparedSurfaceCompletion(attempt, outcome, expected, readbackId, null),
                    ),
                )
            })
            val failure = assertIs<GPUPreparedSurfaceExecutionResult.TerminalFailure>(
                GPUPreparedSurfaceFrameExecutor(
                    GPUPreparedSurfaceBackendPortFactory { FakeBackend(capabilities(), session) },
                ).execute(request()),
            )

            assertEquals(expected, failure.diagnostic)
            assertEquals(1, session.closeCalls)
        }
    }

    @Test
    fun `failed completion without diagnostic and successful completion without output are canonical terminals`() {
        val missingDiagnostic = FakeSession(submissionFactory = { readbackId ->
            val attempt = GPUFrameAttemptID("attempt-missing-diagnostic")
            GPUPreparedSurfaceSubmission(
                attempt,
                GPUPreparedSurfaceImmediateState.Submitted,
                CompletableFuture.completedFuture(
                    GPUPreparedSurfaceCompletion(attempt, GPUFrameStructuralOutcome.Failed, null, readbackId, null),
                ),
            )
        })
        val missingOutput = FakeSession(submissionFactory = { _ ->
            val attempt = GPUFrameAttemptID("attempt-missing-output")
            GPUPreparedSurfaceSubmission(
                attempt,
                GPUPreparedSurfaceImmediateState.Submitted,
                CompletableFuture.completedFuture(
                    GPUPreparedSurfaceCompletion(attempt, GPUFrameStructuralOutcome.Succeeded, null, null, null),
                ),
            )
        })

        val noDiagnostic = executeFailure(missingDiagnostic)
        val noOutput = executeFailure(missingOutput)

        assertEquals("invalid.surface.prepared.terminal-without-diagnostic", noDiagnostic.diagnostic.code.value)
        assertEquals("invalid.surface.prepared.readback-output", noOutput.diagnostic.code.value)
    }

    @Test
    fun `readback size overflow and invalid post-close counters are terminal`() {
        val backend = FakeBackend(capabilities(), FakeSession())
        val overflow = GPUPreparedSurfaceFrameExecutor(
            backendFactory = GPUPreparedSurfaceBackendPortFactory { backend },
            frameBuilder = { readyBuild() },
        ).execute(request().copy(width = Int.MAX_VALUE, height = Int.MAX_VALUE))
        val invalidCounters = FakeSession(
            postCloseCountersOverride = postCloseCounters().copy(activeNativePayloads = 1),
        )

        val overflowFailure = assertIs<GPUPreparedSurfaceExecutionResult.TerminalFailure>(overflow)
        val counterFailure = executeFailure(invalidCounters)

        assertEquals("invalid.surface.prepared.readback-size", overflowFailure.diagnostic.code.value)
        assertEquals(ArithmeticException::class.java.name, overflowFailure.diagnostic.facts["failureClass"])
        assertEquals(0, backend.prepareCalls)
        assertEquals("failed.surface.prepared.completion", counterFailure.diagnostic.code.value)
    }

    @Test
    fun `wrong attempt readback id byte count and exceptional completion are exact terminal diagnostics`() {
        val cases = listOf(
            FakeSession(submissionFactory = { readbackId ->
                val attempt = GPUFrameAttemptID("expected")
                GPUPreparedSurfaceSubmission(
                    attempt,
                    GPUPreparedSurfaceImmediateState.Submitted,
                    CompletableFuture.completedFuture(
                        GPUPreparedSurfaceCompletion(
                            GPUFrameAttemptID("actual"), GPUFrameStructuralOutcome.Succeeded,
                            null, readbackId, ByteArray(16),
                        ),
                    ),
                )
            }) to "invalid.surface.prepared.attempt-identity",
            FakeSession(submissionFactory = { _ -> successSubmission(GPUReadbackRequestID("wrong"), ByteArray(16)) }) to
                "invalid.surface.prepared.readback-output",
            FakeSession(submissionFactory = { readbackId -> successSubmission(readbackId, ByteArray(15)) }) to
                "invalid.surface.prepared.readback-byte-count",
            FakeSession(submissionFactory = { _ ->
                GPUPreparedSurfaceSubmission(
                    GPUFrameAttemptID("exceptional"),
                    GPUPreparedSurfaceImmediateState.Submitted,
                    CompletableFuture.failedFuture(IllegalStateException("unstable detail")),
                )
            }) to "failed.surface.prepared.completion",
        )

        cases.forEach { (session, expected) ->
            val failure = assertIs<GPUPreparedSurfaceExecutionResult.TerminalFailure>(
                GPUPreparedSurfaceFrameExecutor(
                    GPUPreparedSurfaceBackendPortFactory { FakeBackend(capabilities(), session) },
                ).execute(request()),
                expected,
            )
            assertEquals(expected, failure.diagnostic.code.value)
            assertEquals(1, session.closeCalls)
        }
    }

    @Test
    fun `session and backend close failures are terminal with primary code provenance`() {
        val session = FakeSession(closeFailure = IllegalStateException("session detail"))
        val backend = FakeBackend(
            capabilities(), session, closeFailure = UnsupportedOperationException("backend detail"),
        )

        val failure = assertIs<GPUPreparedSurfaceExecutionResult.TerminalFailure>(
            GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceBackendPortFactory { backend }).execute(request()),
        )

        assertEquals("failed.surface.prepared.backend-close", failure.diagnostic.code.value)
        assertEquals("failed.surface.prepared.session-close", failure.diagnostic.facts["primaryCode"])
        assertEquals(UnsupportedOperationException::class.java.name, failure.diagnostic.facts["failureClass"])
        assertTrue("detail" !in failure.diagnostic.message)
    }

    @Test
    fun `backend close failure before prepared entry overrides legacy with primary provenance`() {
        val backend = FakeBackend(
            capabilities = null,
            session = FakeSession(),
            closeFailure = UnsupportedOperationException("backend detail"),
        )

        val failure = assertIs<GPUPreparedSurfaceExecutionResult.TerminalFailure>(
            GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceBackendPortFactory { backend }).execute(request()),
        )

        assertEquals("failed.surface.prepared.backend-close", failure.diagnostic.code.value)
        assertEquals(
            "legacy.surface.prepared.runtime-capabilities-unavailable",
            failure.diagnostic.facts["primaryCode"],
        )
    }

    private fun request(): GPUPreparedSurfaceExecutionRequest {
        val operations = listOf(
            DisplayOp.DrawRect(
                Rect.fromLTRB(0f, 0f, 1f, 4f),
                Paint.fill(Color.RED).copy(antiAlias = false),
                Matrix33.identity(),
                ClipStack.WideOpen,
            ),
        )
        return GPUPreparedSurfaceExecutionRequest(
            assertIs(GPUPreparedSurfaceFrameGate.classify(operations, RenderConfig.DEFAULT)),
            1,
            4,
        )
    }

    private fun sceneTarget(taskList: GPUTaskList): GPUFrameTargetRef =
        taskList.tasks.filterIsInstance<GPUTask.PrepareResources>()
            .flatMap(GPUTask.PrepareResources::requests)
            .single { it.role == GPUFrameResourceRole.SceneTarget }.resource as GPUFrameTargetRef

    private fun readyBuild(): GPUPreparedSurfaceFrameBuildResult.Ready {
        val candidate = request().candidate
        return assertIs(
            GPUPreparedSurfaceFrameBuilder.build(
                GPUPreparedSurfaceFrameBuildRequest(
                    candidate = candidate,
                    targetFacts = GPUTargetFacts(1, 4, candidate.color.physicalFormat.value),
                    targetBounds = GPUPixelBounds(0, 0, 1, 4),
                    capabilities = capabilities(),
                    deviceGeneration = GPUDeviceGenerationID(91),
                    target = GPUFrameTargetRef("overflow-target"),
                    recordingId = GPURecordingID("overflow-recording"),
                    frameId = GPUFrameID(1),
                    readbackRequestId = GPUReadbackRequestID("overflow-readback"),
                ),
            ),
        )
    }

    private fun executeFailure(session: FakeSession) =
        assertIs<GPUPreparedSurfaceExecutionResult.TerminalFailure>(
            GPUPreparedSurfaceFrameExecutor(
                GPUPreparedSurfaceBackendPortFactory { FakeBackend(capabilities(), session) },
            ).execute(request()),
        )

    private fun capabilities(fillRect: Boolean = true): GPUCapabilities {
        val base = GPUProductFlagConfig().buildCapabilities()
        val facts = buildList {
            if (fillRect) add(capability("first_slice.fill_rect.native"))
            add(capability(PATH_FILL_STENCIL_COVER))
        }
        return GPUCapabilities(
            implementation = base.implementation,
            facts = base.facts + facts,
            knownUnsupportedFacts = base.knownUnsupportedFacts,
            snapshotId = "${base.snapshotId}:surface-executor:$fillRect",
            limits = GPULimits(
                maxTextureDimension2D = 8192,
                copyBytesPerRowAlignment = 256,
                minUniformBufferOffsetAlignment = 256,
                maxBufferSize = 1L shl 30,
                maxDynamicUniformBuffersPerPipelineLayout = 1,
            ),
            rendererFeatures = setOf(GPURendererFeature.RenderPass, GPURendererFeature.Readback),
        )
    }

    private fun capability(name: String) = GPUCapabilityFact(
        name, "test", "supported", true, "test:$name",
    )

    private fun diagnostic(code: String) = GPUDiagnostic(
        GPUDiagnosticCode(code), GPUDiagnosticDomain.Execution, GPUDiagnosticSeverity.Error, code,
    )

    private fun successSubmission(readbackId: GPUReadbackRequestID, bytes: ByteArray): GPUPreparedSurfaceSubmission {
        val attempt = GPUFrameAttemptID("attempt-success")
        return GPUPreparedSurfaceSubmission(
            attempt,
            GPUPreparedSurfaceImmediateState.Submitted,
            CompletableFuture.completedFuture(
                GPUPreparedSurfaceCompletion(
                    attempt, GPUFrameStructuralOutcome.Succeeded, null, readbackId, bytes,
                ),
            ),
        )
    }

    private fun evidence() = GPUPreparedSurfaceExecutionEvidence(
        1, 1, 1, 1, 1, 1, 1, 0, 0, 1, 1, 0, 1,
        0, 0, 0, 1, 1, 0, 1,
    )

    private class FakeBackend(
        override val capabilities: GPUCapabilities?,
        private val session: FakeSession,
        private val closeFailure: Throwable? = null,
        private val prepareFailure: Throwable? = null,
    ) : GPUPreparedSurfaceBackendPort {
        override val deviceGeneration = GPUDeviceGenerationID(91)
        private var telemetryRead = 0
        override val runtimeTelemetry: GPUBackendRuntimeTelemetry
            get() = GPUBackendRuntimeTelemetry(destinationReadbackSnapshots = 7L)
                .also { telemetryRead++ }
        val preparedRequests = mutableListOf<GPUOffscreenTargetRequest>()
        var prepareCalls = 0
        var closeCalls = 0

        override fun prepare(request: GPUOffscreenTargetRequest): GPUPreparedSurfaceSessionPort {
            prepareCalls++
            preparedRequests += request
            prepareFailure?.let { throw it }
            return session
        }

        override fun close() {
            closeCalls++
            closeFailure?.let { throw it }
        }
    }

    private class FakeSession(
        private val submissionFactory: ((GPUReadbackRequestID) -> GPUPreparedSurfaceSubmission)? = null,
        private val closeFailure: Throwable? = null,
        private val submitFailure: Throwable? = null,
        private val postCloseCountersOverride: GPUPreparedSceneNativeCounters? = null,
    ) : GPUPreparedSurfaceSessionPort {
        val submittedTaskLists = mutableListOf<GPUTaskList>()
        val submittedReadbackIds = mutableListOf<GPUReadbackRequestID>()
        val completedReadbackIds = mutableListOf<GPUReadbackRequestID>()
        var submitCalls = 0
        var closeCalls = 0
        private var counterReads = 0
        private var closed = false

        override fun submit(taskList: GPUTaskList, readbackId: GPUReadbackRequestID): GPUPreparedSurfaceSubmission {
            submitCalls++
            submittedTaskLists += taskList
            submittedReadbackIds += readbackId
            submitFailure?.let { throw it }
            val submission = submissionFactory?.invoke(readbackId) ?: run {
                val attempt = GPUFrameAttemptID("attempt-success")
                GPUPreparedSurfaceSubmission(
                    attempt,
                    GPUPreparedSurfaceImmediateState.Submitted,
                    CompletableFuture.completedFuture(
                        GPUPreparedSurfaceCompletion(
                            attempt,
                            GPUFrameStructuralOutcome.Succeeded,
                            null,
                            readbackId,
                            ByteArray(16) { (it + 1).toByte() },
                        ),
                    ),
                )
            }
            return submission.also { actual ->
                actual.completion.thenAccept { completion ->
                    completion.readbackId?.let(completedReadbackIds::add)
                }
            }
        }

        override fun counters(): GPUPreparedSceneNativeCounters {
            counterReads++
            return when {
                closed -> postCloseCountersOverride ?: postCloseCounters()
                counterReads == 1 -> GPUPreparedSceneNativeCounters(targetCreations = 1)
                else -> postCompletionCounters()
            }
        }

        override fun close() {
            closeCalls++
            closed = true
            closeFailure?.let { throw it }
        }

        private fun postCompletionCounters() = GPUPreparedSceneNativeCounters(
            encoders = 1,
            commandBuffers = 1,
            targetCreations = 1,
            submits = 1,
            readbackCopies = 1,
            retentionRegistrations = 1,
            retentionCompletions = 1,
            frameCoordinatorCreations = 1,
            distinctRetentionTickets = 1,
            renderPasses = 1,
            draws = 1,
            pipelineBinds = 1,
        )

        private fun postCloseCounters() = postCompletionCounters().copy(targetCloses = 1)
    }

    private fun postCloseCounters() = GPUPreparedSceneNativeCounters(
        encoders = 1,
        commandBuffers = 1,
        targetCreations = 1,
        targetCloses = 1,
        submits = 1,
        readbackCopies = 1,
        retentionRegistrations = 1,
        retentionCompletions = 1,
        frameCoordinatorCreations = 1,
        distinctRetentionTickets = 1,
        renderPasses = 1,
        draws = 1,
        pipelineBinds = 1,
    )
}
