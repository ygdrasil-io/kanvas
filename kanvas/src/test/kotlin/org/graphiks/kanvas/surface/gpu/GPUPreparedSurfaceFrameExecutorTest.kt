package org.graphiks.kanvas.surface.gpu

import java.util.concurrent.CompletableFuture
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.graphiks.kanvas.canvas.ClipStack
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilityFact
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUFirstSliceCapabilityName.PATH_FILL_STENCIL_COVER
import org.graphiks.kanvas.gpu.renderer.capabilities.GPULimits
import org.graphiks.kanvas.gpu.renderer.capabilities.GPURendererFeature
import org.graphiks.kanvas.gpu.renderer.color.GPUColorFormat as CanonicalGPUColorFormat
import org.graphiks.kanvas.gpu.renderer.color.GPUColorInterpretation
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
import org.graphiks.kanvas.gpu.renderer.recording.GPUDestinationSnapshotOperation
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUFrameAttemptID
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUFrameStructuralOutcome
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.surface.GPUColorFormat
import org.graphiks.kanvas.surface.RenderConfig
import org.graphiks.kanvas.text.KanvasGlyphRun
import org.graphiks.kanvas.text.TextBlob
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect

class GPUPreparedSurfaceFrameExecutorTest {
    @Test
    fun `pure pre-backend no-op gate accepts only empty glyph runs with exact accounting`() {
        val request = executionRequest(
            listOf(
                DisplayOp.SetTransform(Matrix33.identity()),
                emptyGlyphText(),
                nonEmptyText(
                    typeface = liberationTypeface(),
                    clip = targetEmptyClip(),
                ),
            ),
            width = 8,
            height = 8,
        )

        val noOp = assertIs<GPUPreparedSurfaceFrameBuildResult.NoOp>(
            GPUPreparedSurfacePreBackendNoOpGate.classify(request),
        )

        assertEquals(1, noOp.stateEventCount)
        assertEquals(setOf(1, 2), noOp.acceptedTextOperationIndices)
        assertEquals(setOf(1), noOp.elidedTextOperationIndices)
        assertEquals(setOf(2), noOp.culledTextOperationIndices)
        assertEquals(0, noOp.textMetrics.glyphCount)
        assertEquals(0, noOp.textMetrics.instanceCount)
    }

    @Test
    fun `empty glyph and valid integral target-empty text complete before backend open`() {
        var backendOpenCalls = 0
        val backend = FakeBackend(capabilities(), FakeSession())
        val executor = GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceBackendPortFactory {
            backendOpenCalls++
            backend
        })
        val request = executionRequest(
            listOf(
                emptyGlyphText(),
                nonEmptyText(
                    typeface = liberationTypeface(),
                    clip = targetEmptyClip(),
                ),
            ),
            width = 8,
            height = 8,
        )

        val result = executor.execute(request)
        val success = assertIs<GPUPreparedSurfaceExecutionResult.Succeeded>(result, result.toString())

        assertContentEquals(ByteArray(8 * 8 * 4), success.rgba)
        assertEquals(0, success.visualOperationCount)
        assertEquals(0, success.evidence.targetCreations)
        assertEquals(0, success.evidence.frameCoordinatorCreations)
        assertEquals(0, success.evidence.submits)
        assertEquals(0, backendOpenCalls)
        assertEquals(0, backend.prepareCalls)
        assertEquals(0, backend.closeCalls)
    }

    @Test
    fun `mixed target-empty text and visible rect bypasses no-op gate and creates one target`() {
        var backendOpenCalls = 0
        val session = FakeSession()
        val backend = FakeBackend(capabilities(preparedText = true), session)
        val executor = GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceBackendPortFactory {
            backendOpenCalls++
            backend
        })
        val request = executionRequest(
            listOf(
                nonEmptyText(
                    typeface = liberationTypeface(),
                    clip = targetEmptyClip(),
                ),
                DisplayOp.DrawRect(
                    Rect.fromLTRB(0f, 0f, 1f, 4f),
                    Paint.fill(Color.RED).copy(antiAlias = false),
                    Matrix33.identity(),
                    ClipStack.WideOpen,
                ),
            ),
            width = 1,
            height = 4,
        )

        val success = assertIs<GPUPreparedSurfaceExecutionResult.Succeeded>(executor.execute(request))

        assertEquals(1, success.visualOperationCount)
        assertEquals(1, success.evidence.targetCreations)
        assertEquals(1, backendOpenCalls)
        assertEquals(1, backend.prepareCalls)
        assertEquals(1, session.submitCalls)
    }

    @Test
    fun `missing typeface and destination-read TextA8 are never swallowed by pre-backend no-op`() {
        val missingTypeface = executionRequest(
            listOf(nonEmptyText(typeface = null, clip = targetEmptyClip())),
            width = 32,
            height = 32,
        )
        val destinationReadTextA8 = executionRequest(
            listOf(
                nonEmptyText(
                    typeface = liberationTypeface(),
                    clip = targetEmptyClip(),
                    blendMode = BlendMode.DARKEN,
                ),
            ),
            width = 32,
            height = 32,
        )
        assertEquals(null, GPUPreparedSurfacePreBackendNoOpGate.classify(missingTypeface))
        assertEquals(null, GPUPreparedSurfacePreBackendNoOpGate.classify(destinationReadTextA8))

        var missingTypefaceBackendOpenCalls = 0
        val missingTypefaceSession = FakeSession()
        val missingTypefaceBackend = FakeBackend(
            capabilities(preparedText = true),
            missingTypefaceSession,
        )
        val missingTypefaceRefused =
            assertIs<GPUPreparedSurfaceExecutionResult.BeforePreparedEntryRefused>(
                GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceBackendPortFactory {
                    missingTypefaceBackendOpenCalls++
                    missingTypefaceBackend
                }).execute(missingTypeface),
            )
        assertEquals(
            org.graphiks.kanvas.glyph.gpu.GPUTextRefusalCodes.TYPEFACE_MISSING,
            missingTypefaceRefused.diagnostic.code.value,
        )
        assertEquals(1, missingTypefaceBackendOpenCalls)
        assertEquals(0, missingTypefaceBackend.prepareCalls)
        assertEquals(0, missingTypefaceSession.submitCalls)
        assertEquals(0, missingTypefaceSession.closeCalls)
        assertEquals(1, missingTypefaceBackend.closeCalls)

        var destinationReadBackendOpenCalls = 0
        val destinationReadSession = FakeSession()
        val destinationReadBackend = FakeBackend(
            capabilities(preparedText = true),
            destinationReadSession,
        )
        val refused = assertIs<GPUPreparedSurfaceExecutionResult.BeforePreparedEntryRefused>(
            GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceBackendPortFactory {
                destinationReadBackendOpenCalls++
                destinationReadBackend
            }).execute(destinationReadTextA8),
        )
        assertEquals("invalid.preflight.text.blend", refused.diagnostic.code.value)
        assertEquals(1, destinationReadBackendOpenCalls)
        assertEquals(0, destinationReadBackend.prepareCalls)
        assertEquals(0, destinationReadSession.submitCalls)
        assertEquals(0, destinationReadSession.closeCalls)
    }

    @Test
    fun `pre-backend no-op classification never queries typeface outlines`() {
        val typeface = ThrowingOutlineTypeface()
        val targetEmpty = executionRequest(
            listOf(nonEmptyText(typeface = typeface, clip = targetEmptyClip())),
            width = 8,
            height = 8,
        )
        val mixed = executionRequest(
            listOf(
                nonEmptyText(typeface = typeface, clip = targetEmptyClip()),
                DisplayOp.DrawRect(
                    Rect.fromLTRB(0f, 0f, 1f, 4f),
                    Paint.fill(Color.RED).copy(antiAlias = false),
                    Matrix33.identity(),
                    ClipStack.WideOpen,
                ),
            ),
            width = 1,
            height = 4,
        )

        assertIs<GPUPreparedSurfaceFrameBuildResult.NoOp>(
            GPUPreparedSurfacePreBackendNoOpGate.classify(targetEmpty),
        )
        assertEquals(null, GPUPreparedSurfacePreBackendNoOpGate.classify(mixed))
        assertEquals(0, typeface.getGlyphPathCalls)
    }

    @Test
    fun `execution values defensively own every readback array`() {
        val attempt = GPUFrameAttemptID("attempt")
        val readback = GPUReadbackRequestID("readback")
        val completionSource = byteArrayOf(1, 2, 3, 4)
        val completion = GPUPreparedSurfaceCompletion(
            attempt,
            GPUFrameStructuralOutcome.Succeeded,
            null,
            GPUPreparedSurfaceOutputKind.ReadbackRgba,
            readback,
            completionSource,
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
    fun `empty and state event only frames classify as no-ops and complete transparent before backend open`() {
        val stateOnly = executionRequest(
            listOf(
                DisplayOp.SetTransform(Matrix33.translate(1f, 2f)),
                DisplayOp.SetClip(ClipStack.WideOpen),
                DisplayOp.Annotation(Rect.fromLTRB(0f, 0f, 1f, 1f), "key", "value"),
                DisplayOp.FlushAndSnapshot(Rect.fromLTRB(0f, 0f, 1f, 1f)),
            ),
            width = 4,
            height = 4,
        )
        val empty = executionRequest(emptyList(), width = 4, height = 4)

        val stateNoOp = assertIs<GPUPreparedSurfaceFrameBuildResult.NoOp>(
            GPUPreparedSurfacePreBackendNoOpGate.classify(stateOnly),
        )
        assertEquals(4, stateNoOp.stateEventCount)
        assertTrue(stateNoOp.acceptedTextOperationIndices.isEmpty())
        assertTrue(stateNoOp.elidedTextOperationIndices.isEmpty())
        assertTrue(stateNoOp.culledTextOperationIndices.isEmpty())

        val emptyNoOp = assertIs<GPUPreparedSurfaceFrameBuildResult.NoOp>(
            GPUPreparedSurfacePreBackendNoOpGate.classify(empty),
        )
        assertEquals(0, emptyNoOp.stateEventCount)
        assertTrue(emptyNoOp.acceptedTextOperationIndices.isEmpty())

        var backendOpenCalls = 0
        val executor = GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceBackendPortFactory {
            backendOpenCalls++
            FakeBackend(capabilities(), FakeSession())
        })
        listOf(stateOnly, empty).forEach { request ->
            val success = assertIs<GPUPreparedSurfaceExecutionResult.Succeeded>(executor.execute(request))
            assertContentEquals(ByteArray(4 * 4 * 4), success.rgba)
            assertEquals(0, success.visualOperationCount)
            assertEquals(0, success.evidence.targetCreations)
            assertEquals(0, success.evidence.frameCoordinatorCreations)
            assertEquals(0, success.evidence.submits)
        }
        assertEquals(0, backendOpenCalls)
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
            "unavailable.surface.prepared.runtime-capabilities",
            assertIs<GPUPreparedSurfaceExecutionResult.BeforePreparedEntryRefused>(
                noCapabilities.execute(request),
            ).diagnostic.code.value,
        )
        assertEquals(0, noCapabilitiesBackend.prepareCalls)
        assertEquals(1, noCapabilitiesBackend.closeCalls)
    }

    @Test
    fun `backend open failure is a terminal diagnostic with the thrown failure class`() {
        val failure = assertIs<GPUPreparedSurfaceExecutionResult.TerminalFailure>(
            GPUPreparedSurfaceFrameExecutor(
                GPUPreparedSurfaceBackendPortFactory { throw IllegalStateException("open failed") },
            ).execute(request()),
        )

        assertEquals("failed.surface.prepared.backend-open", failure.diagnostic.code.value)
        assertEquals(IllegalStateException::class.java.name, failure.diagnostic.facts["failureClass"])
    }

    @Test
    fun `destination-read prepared text blend refuses before native entry`() {
        val session = FakeSession()
        val backend = FakeBackend(capabilities(preparedText = true), session)
        val executor = GPUPreparedSurfaceFrameExecutor(
            GPUPreparedSurfaceBackendPortFactory { backend },
        )

        val refused = assertIs<GPUPreparedSurfaceExecutionResult.BeforePreparedEntryRefused>(
            executor.execute(
                textRequest(
                    BlendMode.DARKEN,
                    ClipStack.DeviceRect(Rect.fromLTRB(6f, 6f, 14f, 14f), antiAlias = false),
                ),
            ),
        )

        assertEquals("invalid.preflight.text.blend", refused.diagnostic.code.value)
        assertEquals(0, backend.prepareCalls)
        assertEquals(0, session.submitCalls)
        assertEquals(0, session.closeCalls)
        assertEquals(1, backend.closeCalls)
    }

    @Test
    fun `success preserves build facts and returns exact frame-local evidence after checkin`() {
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
        assertEquals(0, session.closeCalls)
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
    fun `success captures runtime telemetry before closing the backend`() {
        val backend = FakeBackend(
            capabilities(),
            FakeSession(),
            rejectTelemetryAfterClose = true,
        )

        assertIs<GPUPreparedSurfaceExecutionResult.Succeeded>(
            GPUPreparedSurfaceFrameExecutor(
                GPUPreparedSurfaceBackendPortFactory { backend },
            ).execute(request()),
        )

        assertEquals(2, backend.telemetryReads)
        assertEquals(1, backend.closeCalls)
    }

    @Test
    fun `two executions reuse one session with distinct target recording frame and readback identities`() {
        val session = FakeSession()
        val backend = FakeBackend(capabilities(), session)
        val executor = GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceBackendPortFactory { backend })

        val first = assertIs<GPUPreparedSurfaceExecutionResult.Succeeded>(executor.execute(request()))
        val second = assertIs<GPUPreparedSurfaceExecutionResult.Succeeded>(executor.execute(request()))

        assertEquals(0L, first.evidence.targetCloses)
        assertEquals(0L, second.evidence.targetCreations)
        assertEquals(1, backend.prepareCalls)
        assertEquals(2, session.submitCalls)
        assertEquals(0, session.closeCalls)
        val firstTasks = session.submittedTaskLists[0]
        val secondTasks = session.submittedTaskLists[1]
        assertNotEquals(firstTasks.frameId, secondTasks.frameId)
        assertNotEquals(firstTasks.recordingSeals.single().recordingId, secondTasks.recordingSeals.single().recordingId)
        // One session binds one canonical logical target for its whole lifetime; the
        // per-frame recording/frame/readback identities above remain distinct.
        assertEquals(sceneTarget(firstTasks), sceneTarget(secondTasks))
        assertNotEquals(session.submittedReadbackIds[0], session.submittedReadbackIds[1])
    }

    @Test
    fun `size transition closes the old session and creates exactly one new session`() {
        val backend = TransitionBackend(capabilities())
        val executor = GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceBackendPortFactory { backend })

        val first = assertIs<GPUPreparedSurfaceExecutionResult.Succeeded>(
            executor.execute(transitionRequest(64, 64)),
        )
        val transition = assertIs<GPUPreparedSurfaceExecutionResult.Succeeded>(
            executor.execute(transitionRequest(32, 32)),
        )
        val after = assertIs<GPUPreparedSurfaceExecutionResult.Succeeded>(
            executor.execute(transitionRequest(32, 32)),
        )

        assertEquals(1L, first.evidence.targetCreations, "the first frame creates its session target")
        assertEquals(0L, first.evidence.targetCloses, "the first frame checks the session in")
        assertEquals(1L, first.evidence.frameCoordinatorCreations)
        assertEquals(
            1L, transition.evidence.targetCreations,
            "the size change creates exactly one new session target",
        )
        assertEquals(
            1L, transition.evidence.targetCloses,
            "the size change closes exactly one old session",
        )
        assertEquals(1L, transition.evidence.frameCoordinatorCreations)
        assertEquals(0L, after.evidence.targetCreations, "the new size is reused after the transition")
        assertEquals(0L, after.evidence.targetCloses)
        assertEquals(2, backend.prepareCalls, "two sessions are prepared across the transition")
        assertEquals(1, backend.createdSessions[0].closeCalls, "the old session is closed exactly once")
        assertEquals(0, backend.createdSessions[1].closeCalls, "the new session is checked in")
        assertEquals(64, backend.preparedRequests[0].width)
        assertEquals(32, backend.preparedRequests[1].width)
        // The invariant delta machinery reports zeros on a creating frame's reuse fields and
        // positive reuse only on the subsequent frame of the same session.
        assertEquals(0L, transition.evidence.invariantCounters.corePrimitiveReuses)
        assertEquals(1L, transition.evidence.invariantCounters.corePrimitiveCreations)
        assertEquals(0L, after.evidence.invariantCounters.corePrimitiveCreations)
        assertTrue(after.evidence.invariantCounters.corePrimitiveReuses > 0L)
    }

    @Test
    fun `format transition closes the old session and creates exactly one new session`() {
        val backend = TransitionBackend(capabilities())
        val executor = GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceBackendPortFactory { backend })
        val rgbaRequest = transitionRequest(16, 16, RenderConfig.DEFAULT)
        val bgraRequest = transitionRequest(
            16, 16,
            RenderConfig.DEFAULT.copy(gpuColorFormat = GPUColorFormat.BGRA8_UNORM),
        )

        val first = assertIs<GPUPreparedSurfaceExecutionResult.Succeeded>(executor.execute(rgbaRequest))
        val transition = assertIs<GPUPreparedSurfaceExecutionResult.Succeeded>(executor.execute(bgraRequest))

        assertEquals(1L, first.evidence.targetCreations)
        assertEquals(0L, first.evidence.targetCloses)
        assertEquals(
            1L, transition.evidence.targetCreations,
            "the format change creates exactly one new session",
        )
        assertEquals(
            1L, transition.evidence.targetCloses,
            "the format change closes exactly one old session",
        )
        assertEquals(2, backend.prepareCalls)
        assertEquals(1, backend.createdSessions[0].closeCalls, "the RGBA8 session is closed exactly once")
        assertEquals(0, backend.createdSessions[1].closeCalls)
        assertEquals(
            CanonicalGPUColorFormat.RGBA8UnormSrgb,
            backend.preparedRequests[0].colorFormat,
        )
        assertEquals(
            CanonicalGPUColorFormat.BGRA8Unorm,
            backend.preparedRequests[1].colorFormat,
            "the new session is prepared on the BGRA8 physical format",
        )
        assertEquals(
            GPUColorInterpretation.EncodedPremulSrgb,
            backend.preparedRequests[1].colorInterpretation,
            "the new session is prepared on the BGRA8 interpretation",
        )
    }

    @Test
    fun `device generation transition invalidates the stale session without a second close`() {
        val backend = TransitionBackend(capabilities())
        val executor = GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceBackendPortFactory { backend })

        val first = assertIs<GPUPreparedSurfaceExecutionResult.Succeeded>(
            executor.execute(transitionRequest(16, 16)),
        )
        backend.disposeSession()
        backend.deviceGeneration = GPUDeviceGenerationID(backend.deviceGeneration.value + 1)
        val transition = assertIs<GPUPreparedSurfaceExecutionResult.Succeeded>(
            executor.execute(transitionRequest(16, 16)),
        )

        assertEquals(1L, first.evidence.targetCreations)
        assertEquals(0L, first.evidence.targetCloses)
        assertEquals(
            1L, transition.evidence.targetCreations,
            "the generation change creates exactly one new session",
        )
        assertEquals(
            0L, transition.evidence.targetCloses,
            "the factory-disposed session is invalidated without an executor close",
        )
        assertEquals(1, backend.createdSessions[0].closeCalls, "the stale-generation session is already disposed")
        assertEquals(0, backend.createdSessions[1].closeCalls)
        assertEquals(
            listOf(91L, 92L), backend.prepareGenerations,
            "the new session is prepared on the new generation",
        )
    }

    @Test
    fun `owner transition creates a fresh session on a new executor instance`() {
        val firstBackend = TransitionBackend(capabilities())
        val secondBackend = TransitionBackend(capabilities())
        val executorA = GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceBackendPortFactory { firstBackend })
        val executorB = GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceBackendPortFactory { secondBackend })

        assertIs<GPUPreparedSurfaceExecutionResult.Succeeded>(executorA.execute(transitionRequest(16, 16)))
        val owner = assertIs<GPUPreparedSurfaceExecutionResult.Succeeded>(
            executorB.execute(transitionRequest(16, 16)),
        )

        assertEquals(
            1L, owner.evidence.targetCreations,
            "the second owner's first frame creates its own session",
        )
        assertEquals(0L, owner.evidence.targetCloses)
        assertEquals(1, firstBackend.prepareCalls)
        assertEquals(
            0, firstBackend.createdSessions.single().closeCalls,
            "the first owner's session is untouched by the second owner",
        )
        assertEquals(
            1, firstBackend.createdSessions.single().submitCalls,
            "the first owner's session is untouched by the second owner",
        )
        assertEquals(1, secondBackend.prepareCalls)
        assertEquals(0, secondBackend.createdSessions.single().closeCalls)
    }

    @Test
    fun `close transition after dispose completes a subsequent frame on a new generation`() {
        val backend = TransitionBackend(capabilities())
        val executor = GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceBackendPortFactory { backend })

        val first = assertIs<GPUPreparedSurfaceExecutionResult.Succeeded>(
            executor.execute(transitionRequest(16, 16)),
        )
        backend.disposeSession()
        backend.deviceGeneration = GPUDeviceGenerationID(backend.deviceGeneration.value + 1)
        val reopened = assertIs<GPUPreparedSurfaceExecutionResult.Succeeded>(
            executor.execute(transitionRequest(16, 16)),
        )

        assertEquals(1L, first.evidence.targetCreations)
        assertEquals(
            1L, reopened.evidence.targetCreations,
            "the disposed backend reopens one fresh session",
        )
        assertEquals(
            0L, reopened.evidence.targetCloses,
            "the disposed session is invalidated without a second executor close",
        )
        assertEquals(
            1, backend.createdSessions[0].closeCalls,
            "the disposed session is not closed again by the executor",
        )
        assertEquals(0, backend.createdSessions[1].closeCalls, "the reopened session is checked in")
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
                        GPUPreparedSurfaceOutputKind.Absent,
                        null,
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
                        GPUPreparedSurfaceCompletion(
                            attempt,
                            outcome,
                            expected,
                            GPUPreparedSurfaceOutputKind.Absent,
                            null,
                            null,
                        ),
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
    fun `destination read core frame executes ready with copy then formula evidence`() {
        // One hard DARKEN rect over the frame's initial (cleared) target: this is the
        // single-key dst-read shape the prepared lane actually executes (the snapshot copy runs
        // before the single render pass, and the dst-read formula program blends in the shader).
        // The mixed destination-then-source shape is pinned by the fallback test below.
        val operations = listOf(
            DisplayOp.DrawRect(
                Rect.fromLTRB(4f, 4f, 28f, 20f),
                Paint.fill(Color.BLUE).copy(antiAlias = false, blendMode = BlendMode.DARKEN),
                Matrix33.identity(),
                ClipStack.WideOpen,
            ),
        )
        val session = FakeSession(submissionFactory = { readbackId ->
            successSubmission(readbackId, ByteArray(32 * 24 * 4))
        })
        val backend = FakeBackend(capabilities(), session)

        val result = GPUPreparedSurfaceFrameExecutor(
            GPUPreparedSurfaceBackendPortFactory { backend },
        ).execute(executionRequest(operations, width = 32, height = 24))
        val success = assertIs<GPUPreparedSurfaceExecutionResult.Succeeded>(result)

        assertEquals(1, success.evidence.destinationSnapshotCreations)
        assertEquals(1, success.evidence.destinationCopies)
        assertEquals(0, success.evidence.destinationReadbackSnapshots)
        val evidence = success.evidence.destinationReadEvidence.single()
        assertEquals("darken", evidence.modeLabel)
        assertEquals("copy-then-formula", evidence.action)
        assertEquals(setOf(evidence.commandId), success.evidence.destinationReadTextCommandIds)
        assertEquals(1, backend.prepareCalls)
        assertEquals(1, session.submitCalls)
        assertEquals(0, session.closeCalls)
    }

    @Test
    fun `mixed fixed and destination read core frame executes ready after the multi render dst copy admission`() {
        // A destination rect (fixed SRC_OVER) followed by a DARKEN source rect over it: under the
        // production capability snapshot this splits into two renders with the ordered snapshot
        // copy between them. The multi-render dst-copy shape is admitted on the
        // prepared direct lane, so the executor now takes the real build and submits the frame
        // instead of refusing before prepared entry. The fake capabilities collapse the frame
        // into the single-render shape, so the executor-level behavior (prepare + submit, no
        // close) is exercised through the default build seam; the end-to-end real-cap pixels are
        // pinned by GPUAllApiBlendSurfaceTest and GPUPathClipRegressionTest.
        val operations = listOf(
            DisplayOp.DrawRect(
                Rect.fromLTRB(0f, 0f, 32f, 24f),
                Paint.fill(Color.RED).copy(antiAlias = false),
                Matrix33.identity(),
                ClipStack.WideOpen,
            ),
            DisplayOp.DrawRect(
                Rect.fromLTRB(4f, 4f, 28f, 20f),
                Paint.fill(Color.BLUE).copy(antiAlias = false, blendMode = BlendMode.DARKEN),
                Matrix33.identity(),
                ClipStack.WideOpen,
            ),
        )
        val session = FakeSession(submissionFactory = { readbackId ->
            successSubmission(readbackId, ByteArray(32 * 24 * 4))
        })
        val backend = FakeBackend(capabilities(), session)

        val result = GPUPreparedSurfaceFrameExecutor(
            GPUPreparedSurfaceBackendPortFactory { backend },
        ).execute(executionRequest(operations, width = 32, height = 24))
        val success = assertIs<GPUPreparedSurfaceExecutionResult.Succeeded>(result)

        assertEquals(1, success.evidence.destinationSnapshotCreations)
        assertEquals(1, success.evidence.destinationCopies)
        assertEquals(0, success.evidence.destinationReadbackSnapshots)
        assertEquals(1, backend.prepareCalls)
        assertEquals(1, session.submitCalls)
        assertEquals(0, session.closeCalls)
    }

    @Test
    fun `deduplicated destination snapshot evidence allows multiple copy consumers`() {
        val operations = listOf(
            DisplayOp.DrawRect(
                Rect.fromLTRB(0f, 0f, 32f, 24f),
                Paint.fill(Color.RED).copy(antiAlias = false, blendMode = BlendMode.DARKEN),
                Matrix33.identity(),
                ClipStack.WideOpen,
            ),
            DisplayOp.DrawRect(
                Rect.fromLTRB(4f, 4f, 28f, 20f),
                Paint.fill(Color.BLUE).copy(antiAlias = false, blendMode = BlendMode.DARKEN),
                Matrix33.identity(),
                ClipStack.WideOpen,
            ),
        )
        val session = FakeSession(submissionFactory = { readbackId ->
            successSubmission(readbackId, ByteArray(32 * 24 * 4))
        })
        val backend = FakeBackend(capabilities(), session)

        val result = GPUPreparedSurfaceFrameExecutor(
            GPUPreparedSurfaceBackendPortFactory { backend },
        ).execute(executionRequest(operations, width = 32, height = 24))
        val success = assertIs<GPUPreparedSurfaceExecutionResult.Succeeded>(result)

        assertEquals(1, success.evidence.destinationSnapshotCreations)
        assertEquals(2, success.evidence.destinationCopies)
        assertEquals(2, success.evidence.destinationReadEvidence.size)
        assertEquals(2, success.evidence.destinationReadTextCommandIds.size)
        assertEquals(1, success.evidence.destinationReadEvidence.map { it.snapshotLabel }.toSet().size)
        assertEquals(1, backend.prepareCalls)
        assertEquals(1, session.submitCalls)
        assertEquals(0, session.closeCalls)
    }

    @Test
    fun `failed completion without diagnostic and successful completion without output are canonical terminals`() {
        val missingDiagnostic = FakeSession(submissionFactory = { readbackId ->
            val attempt = GPUFrameAttemptID("attempt-missing-diagnostic")
            GPUPreparedSurfaceSubmission(
                attempt,
                GPUPreparedSurfaceImmediateState.Submitted,
                CompletableFuture.completedFuture(
                    GPUPreparedSurfaceCompletion(
                        attempt,
                        GPUFrameStructuralOutcome.Failed,
                        null,
                        GPUPreparedSurfaceOutputKind.Absent,
                        null,
                        null,
                    ),
                ),
            )
        })
        val missingOutput = FakeSession(submissionFactory = { _ ->
            val attempt = GPUFrameAttemptID("attempt-missing-output")
            GPUPreparedSurfaceSubmission(
                attempt,
                GPUPreparedSurfaceImmediateState.Submitted,
                CompletableFuture.completedFuture(
                    GPUPreparedSurfaceCompletion(
                        attempt,
                        GPUFrameStructuralOutcome.Succeeded,
                        null,
                        GPUPreparedSurfaceOutputKind.Absent,
                        null,
                        null,
                    ),
                ),
            )
        })
        val completionOnlyOutput = FakeSession(submissionFactory = { _ ->
            val attempt = GPUFrameAttemptID("attempt-completion-only-output")
            GPUPreparedSurfaceSubmission(
                attempt,
                GPUPreparedSurfaceImmediateState.Submitted,
                CompletableFuture.completedFuture(
                    GPUPreparedSurfaceCompletion(
                        attempt,
                        GPUFrameStructuralOutcome.Succeeded,
                        null,
                        GPUPreparedSurfaceOutputKind.CurrentFrameCompletionOnly,
                        null,
                        null,
                    ),
                ),
            )
        })

        val noDiagnostic = executeFailure(missingDiagnostic)
        val noOutput = executeFailure(missingOutput)
        val completionOnly = executeFailure(completionOnlyOutput)

        assertEquals("invalid.surface.prepared.terminal-without-diagnostic", noDiagnostic.diagnostic.code.value)
        assertEquals("invalid.surface.prepared.readback-output", noOutput.diagnostic.code.value)
        assertEquals("ReadbackRgba", noOutput.diagnostic.facts["expected"])
        assertEquals("Absent", noOutput.diagnostic.facts["actual"])
        assertEquals("invalid.surface.prepared.readback-output", completionOnly.diagnostic.code.value)
        assertEquals("ReadbackRgba", completionOnly.diagnostic.facts["expected"])
        assertEquals("CurrentFrameCompletionOnly", completionOnly.diagnostic.facts["actual"])
    }

    @Test
    fun `readback size overflow and invalid after-close counters are terminal`() {
        val backend = FakeBackend(capabilities(), FakeSession())
        val overflow = GPUPreparedSurfaceFrameExecutor(
            backendFactory = GPUPreparedSurfaceBackendPortFactory { backend },
            frameBuilder = { readyBuild() },
        ).execute(request().copy(width = Int.MAX_VALUE, height = Int.MAX_VALUE))
        val invalidCounters = FakeSession(
            afterCloseCountersOverride = frameCounters().copy(activeNativePayloads = 1),
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
                            null, GPUPreparedSurfaceOutputKind.ReadbackRgba, readbackId, ByteArray(16),
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
        val session = FakeSession(
            submitFailure = IllegalStateException("submit detail"),
            closeFailure = IllegalStateException("session close detail"),
        )
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
            "unavailable.surface.prepared.runtime-capabilities",
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

    private fun executionRequest(
        operations: List<DisplayOp>,
        width: Int,
        height: Int,
    ): GPUPreparedSurfaceExecutionRequest = GPUPreparedSurfaceExecutionRequest(
        GPUPreparedSurfaceEligibility.Candidate(
            operations = operations,
            config = RenderConfig.DEFAULT,
            color = assertIs(RenderConfig.DEFAULT.mapPreparedGpuColorConfig()),
        ),
        width,
        height,
    )

    private fun transitionRequest(
        width: Int,
        height: Int,
        config: RenderConfig = RenderConfig.DEFAULT,
    ): GPUPreparedSurfaceExecutionRequest {
        val operations = listOf(
            DisplayOp.DrawRect(
                Rect.fromLTRB(0f, 0f, 1f, 4f),
                Paint.fill(Color.RED).copy(antiAlias = false),
                Matrix33.identity(),
                ClipStack.WideOpen,
            ),
        )
        return GPUPreparedSurfaceExecutionRequest(
            assertIs(GPUPreparedSurfaceFrameGate.classify(operations, config)),
            width,
            height,
        )
    }

    private fun emptyGlyphText(): DisplayOp.DrawText = DisplayOp.DrawText(
        blob = TextBlob(
            glyphRuns = listOf(
                KanvasGlyphRun(emptyList(), emptyList(), fontSize = 16f),
            ),
            fontSize = 16f,
        ),
        x = 4f,
        y = 24f,
        paint = Paint.fill(Color.WHITE),
        transform = Matrix33.identity(),
        clip = ClipStack.WideOpen,
    )

    private fun nonEmptyText(
        typeface: org.graphiks.kanvas.text.Typeface?,
        glyph: UShort = 36u,
        clip: ClipStack = ClipStack.WideOpen,
        blendMode: BlendMode = BlendMode.SRC_OVER,
    ): DisplayOp.DrawText = DisplayOp.DrawText(
        blob = TextBlob(
            glyphRuns = listOf(
                KanvasGlyphRun(
                    glyphs = listOf(glyph),
                    positions = listOf(Point(0f, 0f)),
                    fontSize = 16f,
                ),
            ),
            typeface = typeface,
            fontSize = 16f,
        ),
        x = 4f,
        y = 24f,
        paint = Paint.fill(Color.WHITE).copy(blendMode = blendMode),
        transform = Matrix33.identity(),
        clip = clip,
    )

    private fun targetEmptyClip(): ClipStack.DeviceRect = ClipStack.DeviceRect(
        Rect.fromLTRB(40f, 0f, 48f, 8f),
        antiAlias = false,
    )

    private fun textRequest(
        blendMode: BlendMode,
        clip: ClipStack = ClipStack.WideOpen,
    ): GPUPreparedSurfaceExecutionRequest {
        val operations = listOf(
            DisplayOp.DrawText(
                blob = TextBlob(
                    glyphRuns = listOf(
                        KanvasGlyphRun(
                            glyphs = listOf(36u),
                            positions = listOf(Point(0f, 0f)),
                            fontSize = 16f,
                        ),
                    ),
                    typeface = liberationTypeface(),
                    fontSize = 16f,
                ),
                x = 4f,
                y = 24f,
                paint = Paint.fill(Color.WHITE).copy(blendMode = blendMode),
                transform = Matrix33.identity(),
                clip = clip,
            ),
        )
        return GPUPreparedSurfaceExecutionRequest(
            GPUPreparedSurfaceEligibility.Candidate(
                operations = operations,
                config = RenderConfig.DEFAULT,
                color = assertIs(RenderConfig.DEFAULT.mapPreparedGpuColorConfig()),
            ),
            32,
            32,
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

    private fun capabilities(
        fillRect: Boolean = true,
        preparedText: Boolean = false,
    ): GPUCapabilities {
        val base = GPUProductFlagConfig().buildCapabilities()
        val facts = buildList {
            if (fillRect) add(capability("first_slice.fill_rect.native"))
            if (preparedText) add(capability("first_slice.draw_text_run.a8_atlas"))
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
                    attempt,
                    GPUFrameStructuralOutcome.Succeeded,
                    null,
                    GPUPreparedSurfaceOutputKind.ReadbackRgba,
                    readbackId,
                    bytes,
                ),
            ),
        )
    }

    private fun evidence() = GPUPreparedSurfaceExecutionEvidence(
        1, 0, 1, 1, 1, 1, 1, 0, 0, 1, 1, 0, 1,
        0, 0, 0, 1, 1, 0, 1,
        GPUPreparedSurfaceExecutionRouteMarker.PreparedSurfaceDirect,
    )

    private class FakeBackend(
        override val capabilities: GPUCapabilities?,
        private val session: FakeSession,
        private val closeFailure: Throwable? = null,
        private val prepareFailure: Throwable? = null,
        private val rejectTelemetryAfterClose: Boolean = false,
    ) : GPUPreparedSurfaceBackendPort {
        override val deviceGeneration = GPUDeviceGenerationID(91)
        var telemetryReads = 0
            private set
        override val runtimeTelemetry: GPUBackendRuntimeTelemetry
            get() {
                if (rejectTelemetryAfterClose && closeCalls > 0) {
                    error("runtime telemetry is unavailable after backend close")
                }
                telemetryReads++
                return GPUBackendRuntimeTelemetry(destinationReadbackSnapshots = 7L)
            }
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

    private class ThrowingOutlineTypeface : org.graphiks.kanvas.text.Typeface {
        override val fontName: String = "throwing-outline-probe"
        var getGlyphPathCalls = 0
            private set

        override fun glyphIdForCodepoint(codepoint: Int): Int = codepoint

        override fun getAdvance(glyphId: Int, fontSize: Float): Float = 1f

        override fun getGlyphPath(
            glyphId: Int,
            fontSize: Float,
        ): org.graphiks.kanvas.geometry.Path? {
            getGlyphPathCalls++
            error("pre-backend no-op classification queried a typeface outline")
        }
    }

    private class FakeSession(
        private val submissionFactory: ((GPUReadbackRequestID) -> GPUPreparedSurfaceSubmission)? = null,
        private val closeFailure: Throwable? = null,
        private val submitFailure: Throwable? = null,
        private val afterCloseCountersOverride: GPUPreparedSceneNativeCounters? = null,
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
                            GPUPreparedSurfaceOutputKind.ReadbackRgba,
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

        // Session-state fake: counters are cumulative across the session's frames. The first
        // read of a session reports the created state; later reads report the state after the
        // completed frames so far; the executor's per-frame afterClose read (every third read)
        // can be overridden to probe invalid checkin counters.
        override fun counters(): GPUPreparedSceneNativeCounters {
            counterReads++
            val completedFrames = if (counterReads == 1) 0 else (counterReads + 1) / 3
            return when {
                closed -> afterCloseCountersOverride ?: closedCounters()
                counterReads == 1 -> GPUPreparedSceneNativeCounters(targetCreations = 1)
                counterReads % 3 == 0 -> afterCloseCountersOverride ?: postCompletionCounters(completedFrames)
                else -> postCompletionCounters(completedFrames)
            }
        }

        override fun close() {
            closeCalls++
            closed = true
            closeFailure?.let { throw it }
        }

        private fun postCompletionCounters(completedFrames: Int) = GPUPreparedSceneNativeCounters(
            encoders = completedFrames.toLong(),
            commandBuffers = completedFrames.toLong(),
            targetCreations = 1,
            submits = completedFrames.toLong(),
            readbackCopies = completedFrames.toLong(),
            retentionRegistrations = completedFrames.toLong(),
            retentionCompletions = completedFrames.toLong(),
            frameCoordinatorCreations = completedFrames.toLong(),
            distinctRetentionTickets = completedFrames,
            renderPasses = completedFrames.toLong(),
            draws = completedFrames.toLong(),
            pipelineBinds = completedFrames.toLong(),
            destinationSnapshotCreations = destinationSnapshotResourceCount() * completedFrames,
            destinationCopies = destinationReadCommandIds().size.toLong() * completedFrames,
        )

        private fun destinationSnapshotResourceCount(): Long = submittedTaskLists.lastOrNull()?.tasks
            ?.filterIsInstance<GPUTask.DestinationSnapshots>()
            ?.flatMap { task -> task.payload.operations }
            ?.map { operation -> operation.snapshot }
            ?.toSet()
            ?.size
            ?.toLong()
            ?: 0L

        private fun destinationReadCommandIds(): Set<Int> =
            submittedTaskLists.lastOrNull()?.tasks
                ?.filterIsInstance<GPUTask.DestinationSnapshots>()
                ?.flatMap { task -> task.payload.operations }
                ?.flatMap(GPUDestinationSnapshotOperation::consumers)
                ?.map { consumer -> consumer.commandId.value }
                ?.toSet()
                .orEmpty()

        private fun closedCounters() = GPUPreparedSceneNativeCounters(
            targetCreations = 1,
            targetCloses = 1,
        )
    }

    /**
     * Transition-matrix backend fake: every [prepare] creates a fresh [RecordingSession] and
     * records the request plus the device generation it was prepared on; [deviceGeneration] is
     * mutable so the matrix can advance it between executes; [disposeSession] simulates the
     * backend factory disposing the currently cached session out from under the executor.
     */
    private class TransitionBackend(
        override val capabilities: GPUCapabilities,
    ) : GPUPreparedSurfaceBackendPort {
        override var deviceGeneration = GPUDeviceGenerationID(91)
        var prepareCalls = 0
            private set
        val preparedRequests = mutableListOf<GPUOffscreenTargetRequest>()
        val prepareGenerations = mutableListOf<Long>()
        val createdSessions = mutableListOf<RecordingSession>()
        var closeCalls = 0
            private set

        override val runtimeTelemetry: GPUBackendRuntimeTelemetry
            get() = GPUBackendRuntimeTelemetry(destinationReadbackSnapshots = 7L)

        override fun prepare(request: GPUOffscreenTargetRequest): GPUPreparedSurfaceSessionPort {
            prepareCalls++
            preparedRequests += request
            prepareGenerations += deviceGeneration.value
            return RecordingSession(byteCount = request.width * request.height * 4)
                .also(createdSessions::add)
        }

        fun disposeSession() {
            createdSessions.lastOrNull()?.close()
        }

        override fun close() {
            closeCalls++
        }
    }

    /** Session fake with cumulative counters advanced by [submit], mirroring the native
     *  session-cache semantics: the first frame of a session creates the core-primitive
     *  invariants and every later frame reuses them. */
    private class RecordingSession(
        private val byteCount: Int,
    ) : GPUPreparedSurfaceSessionPort {
        var submitCalls = 0
            private set
        var closeCalls = 0
            private set
        private var completedFrames = 0

        override fun submit(
            taskList: GPUTaskList,
            readbackId: GPUReadbackRequestID,
        ): GPUPreparedSurfaceSubmission {
            submitCalls++
            val attempt = GPUFrameAttemptID("attempt-matrix")
            completedFrames++
            return GPUPreparedSurfaceSubmission(
                attempt,
                GPUPreparedSurfaceImmediateState.Submitted,
                CompletableFuture.completedFuture(
                    GPUPreparedSurfaceCompletion(
                        attempt,
                        GPUFrameStructuralOutcome.Succeeded,
                        null,
                        GPUPreparedSurfaceOutputKind.ReadbackRgba,
                        readbackId,
                        ByteArray(byteCount) { (it + 1).toByte() },
                    ),
                ),
            )
        }

        override fun counters(): GPUPreparedSceneNativeCounters {
            val frames = completedFrames
            return GPUPreparedSceneNativeCounters(
                encoders = frames.toLong(),
                commandBuffers = frames.toLong(),
                targetCreations = 1,
                targetCloses = 0,
                submits = frames.toLong(),
                readbackCopies = frames.toLong(),
                retentionRegistrations = frames.toLong(),
                retentionCompletions = frames.toLong(),
                frameCoordinatorCreations = frames.toLong(),
                distinctRetentionTickets = frames,
                renderPasses = frames.toLong(),
                draws = frames.toLong(),
                pipelineBinds = frames.toLong(),
                corePrimitiveInvariantCreations = if (frames >= 1) 1L else 0L,
                corePrimitiveInvariantReuses = if (frames >= 1) (frames - 1).toLong() else 0L,
            )
        }

        override fun close() {
            closeCalls++
        }
    }

    private fun frameCounters() = GPUPreparedSceneNativeCounters(
        encoders = 1,
        commandBuffers = 1,
        targetCreations = 1,
        targetCloses = 0,
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
