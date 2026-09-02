package org.graphiks.kanvas.gpu.renderer.planning

import io.ygdrasil.webgpu.GPUTextureFormat
import java.util.Collections
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.graphiks.kanvas.color.ColorSpace
import org.graphiks.kanvas.gpu.plan.PlanId
import org.graphiks.kanvas.gpu.plan.PlanLogicalColorFormat
import org.graphiks.kanvas.gpu.plan.RenderGraph
import org.graphiks.kanvas.gpu.plan.W3SolidRectPlanCompiler
import org.graphiks.kanvas.gpu.renderer.capabilities.*
import org.graphiks.kanvas.gpu.renderer.diagnostics.*
import org.graphiks.kanvas.gpu.renderer.execution.*
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackRequestID
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskList
import org.graphiks.kanvas.gpu.renderer.telemetry.*
import org.graphiks.kanvas.render.ir.*
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.matrix.Matrix3x3F32

class GpuRenderBackendTest {
    @Test fun `backend target is closed to sRGB and a positive budget`() {
        assertFailsWith<IllegalArgumentException> { GpuRenderTargetConfig(SceneExtent(1, 1), ColorSpace.SRGB, 0) }
    }

    @Test fun `completed multi draw frame uses its immutable native metrics snapshot`() = runBlocking {
        val nativeCounters = linkedMapOf("native.render_passes" to 1L, "native.draws" to 6L)
        val prepared = FakePrepared(1, metricsSnapshot = { visualCommandCount ->
            GpuPreparedFrameMetricsSnapshot(
                visualCommandCount = visualCommandCount,
                pipelineBinds = 3L,
                draws = 2L,
                drawIndexed = 4L,
                nativeCounters = nativeCounters,
            )
        })
        val renderer = renderer(FakeOwner(listOf(FakeBackend(1, prepared))))
        val plan = assertIs<RenderPlanResult.Ready<RenderGraph>>(
            renderer.plan(
                scene(
                    2,
                    2,
                    listOf(
                        ColorARGB.fromPackedUInt(0xFFFF0000u),
                        ColorARGB.fromPackedUInt(0xFF0000FFu),
                    ),
                ),
                target(2, 2),
            ),
        ).plan

        val submission = renderer.submit(plan)
        prepared.awaitEvent("render")
        nativeCounters["native.draws"] = 99L
        prepared.future.complete(
            success(
                2,
                2,
                mapOf(
                    GPUFrameStructuralCounter.EncoderScope to 29L,
                    GPUFrameStructuralCounter.EncoderCreate to 41L,
                    GPUFrameStructuralCounter.QueueSubmit to 43L,
                ),
            ),
        )

        val output = assertIs<RenderExecutionResult.Completed<GpuFrameOutput>>(submission.await()).output
        assertEquals(2, output.metrics.opsDispatched)
        assertEquals(3, output.metrics.pipelineCount)
        assertEquals(6, output.metrics.drawCallCount)
        assertEquals(6L, output.nativeEvidenceCounters().getValue("native.draws"))
    }

    @Test fun `overflowed native metrics fail as a readback failure instead of truncating`() = runBlocking {
        val prepared = FakePrepared(1, metricsSnapshot = { visualCommandCount ->
            GpuPreparedFrameMetricsSnapshot(
                visualCommandCount = visualCommandCount,
                pipelineBinds = Long.MAX_VALUE,
                draws = 1L,
                drawIndexed = 0L,
                nativeCounters = mapOf("native.draws" to 1L),
            )
        })
        val renderer = renderer(FakeOwner(listOf(FakeBackend(1, prepared))))
        val submission = renderer.submit(issue(renderer, 2, 2))
        prepared.awaitEvent("render")
        prepared.future.complete(success(2, 2))

        assertEquals("w3.execution.readback_failure", executionCode(submission.await()))
    }

    @Test fun `same submission has one immutable completion and cancelled waiter keeps source alive`() = runBlocking {
        val prepared = FakePrepared(1); val renderer = renderer(FakeOwner(listOf(FakeBackend(1, prepared))))
        val submission = renderer.submit(issue(renderer, 2, 2)); prepared.awaitEvent("render")
        val cancelled = async { submission.await() }; cancelled.cancelAndJoin()
        assertTrue(!prepared.future.isCancelled)
        prepared.future.complete(success(2, 2))
        val first = submission.await(); val second = submission.await()
        assertTrue(first === second)
        assertEquals(4, assertIs<RenderExecutionResult.Completed<GpuFrameOutput>>(first).output.copyBytes().size / 4)
        assertEquals(listOf("render", "release"), prepared.events)
    }

    @Test fun `equivalent public plans share one target while their submissions serialize`() = runBlocking {
        val prepared = FakePrepared(1); val backend = FakeBackend(1, prepared); val renderer = renderer(FakeOwner(listOf(backend)))
        val first = renderer.submit(issue(renderer, 2, 2)); val second = renderer.submit(issue(renderer, 2, 2))
        prepared.awaitEvent("render"); delay(40)
        assertEquals(listOf("target:2x2", "render"), backend.events + prepared.events)
        prepared.future.complete(success(2, 2)); first.await(); second.await()
        assertEquals(listOf("target:2x2", "render", "release", "render", "release"), backend.events + prepared.events)
    }

    @Test fun `different public plans with one extent reuse their prepared target`() = runBlocking {
        val prepared = FakePrepared(1); val backend = FakeBackend(1, prepared); val renderer = renderer(FakeOwner(listOf(backend)))
        val red = issue(renderer, 2, 2, ColorARGB.fromPackedUInt(0xFFFF0000u))
        val blue = issue(renderer, 2, 2, ColorARGB.fromPackedUInt(0xFF0000FFu))
        assertTrue(red.id != blue.id)

        val redSubmission = renderer.submit(red); prepared.awaitEvent("render")
        prepared.future.complete(success(2, 2)); redSubmission.await()
        assertIs<RenderExecutionResult.Completed<GpuFrameOutput>>(renderer.submit(blue).await())
        assertEquals(listOf("target:2x2", "render", "release", "render", "release"), backend.events + prepared.events)
    }

    @Test fun `different session keys progress independently`() = runBlocking {
        val one = FakePrepared(1); val two = FakePrepared(1)
        val owner = FakeOwner(listOf(FakeBackend(1, one, two))); val context = GpuRenderContext(owner)
        val leftRenderer = renderer(owner, context, 2, 2); val rightRenderer = renderer(owner, context, 3, 2)
        val left = leftRenderer.submit(issue(leftRenderer, 2, 2)); val right = rightRenderer.submit(issue(rightRenderer, 3, 2))
        one.awaitEvent("render"); two.awaitEvent("render")
        one.future.complete(success(2, 2)); two.future.complete(success(3, 2)); left.await(); right.await()
        assertEquals(listOf("render", "release"), one.events)
        assertEquals(listOf("render", "release"), two.events)
    }

    @Test fun `device loss drains stale session and a fresh generation accepts a new submit`() = runBlocking {
        val stale = FakePrepared(1); val fresh = FakePrepared(2)
        val owner = FakeOwner(listOf(FakeBackend(1, stale), FakeBackend(2, fresh))); val renderer = renderer(owner)
        val lost = renderer.submit(issue(renderer, 2, 2)); stale.awaitEvent("render")
        stale.future.complete(failure("device.lost", mapOf("kind" to "DeviceLost")))
        assertEquals("w3.execution.device_failure", assertIs<RenderExecutionResult.DeviceFailure>(lost.await()).diagnostics.single().code.value)
        assertEquals(listOf("render", "release", "close"), stale.events)
        assertEquals(listOf("dispose:1"), owner.events)
        val freshPlan = assertIs<RenderPlanResult.Ready<RenderGraph>>(renderer.plan(scene(2, 2), target(2, 2))).plan
        assertEquals(2L, freshPlan.capabilities.deviceGeneration)
        val freshSubmit = renderer.submit(freshPlan); fresh.awaitEvent("render"); fresh.future.complete(success(2, 2)); freshSubmit.await()
        assertEquals(listOf("render", "release"), fresh.events)
    }

    @Test fun preparedGenerationMismatchDisposesExpectedRuntimeBeforeFreshPlan() = runBlocking {
        val mismatched = FakePrepared(2)
        val fresh = FakePrepared(2)
        val owner = FakeOwner(listOf(FakeBackend(1, mismatched), FakeBackend(2, fresh)))
        val renderer = renderer(owner)

        val stalePlan = issue(renderer, 2, 2)
        assertEquals("w3.execution.device_failure", executionCode(renderer.submit(stalePlan).await()))
        assertEquals(listOf("close"), mismatched.events)
        assertEquals(listOf("dispose:1"), owner.events)

        val refreshed = issue(renderer, 2, 2)
        assertEquals(2L, refreshed.capabilities.deviceGeneration)
        val freshSubmission = renderer.submit(refreshed)
        fresh.awaitEvent("render")
        fresh.future.complete(success(2, 2))
        assertIs<RenderExecutionResult.Completed<GpuFrameOutput>>(freshSubmission.await())
        Unit
    }

    @Test fun `device failure survives best effort cleanup of every stale session`() = runBlocking {
        val broken = FakePrepared(
            1,
            GPUFrameImmediateState.Refused(diag("submit.refused", mapOf("kind" to "DeviceLost"))),
        ).apply { closeFailure = IllegalStateException("broken close") }
        val healthy = FakePrepared(1)
        val owner = FakeOwner(listOf(FakeBackend(1, broken, healthy)))
        val context = GpuRenderContext(owner)
        val renderer = renderer(owner, context)
        val plan = issue(renderer, 2, 2)
        context.prepared(GpuRenderSessionKey(1, 2, 2, PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL))
        context.prepared(GpuRenderSessionKey(1, 3, 2, PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL))

        val failure = renderer.submit(plan)
        broken.awaitEvent("render")

        assertEquals("w3.execution.device_failure", executionCode(failure.await()))
        assertEquals(listOf("render", "release", "close"), broken.events)
        assertEquals(listOf("close"), healthy.events)
        assertEquals(listOf("dispose:1"), owner.events)
    }

    @Test fun `context drains a queued submission before close releases its owner`() = runBlocking {
        val dispatcher = QueuedDispatcher()
        val prepared = FakePrepared(1)
        val owner = FakeOwner(listOf(FakeBackend(1, prepared)))
        val context = GpuRenderContext(owner, workerDispatcher = dispatcher)
        val renderer = renderer(owner, context)
        val submission = renderer.submit(issue(renderer, 2, 2))
        withTimeout(2_000) { dispatcher.awaitWork() }

        val key = GpuRenderSessionKey(1, 2, 2, PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL)
        val closing = async(Dispatchers.Default) { context.close() }
        withTimeout(2_000) {
            while (context.prepared(key) != null) {
                yield()
            }
        }
        assertTrue(owner.events.isEmpty())

        dispatcher.runNext()
        assertEquals("w3.execution.device_failure", executionCode(submission.await()))
        closing.await()
        assertEquals(listOf("owner-close"), owner.events)
    }

    @Test fun `immediate device loss is structured while ordinary readback failure leaves generation alive`() = runBlocking {
        val lost = FakePrepared(1, GPUFrameImmediateState.Refused(diag("submit.refused", mapOf("kind" to "DeviceLost"))))
        val lostOwner = FakeOwner(listOf(FakeBackend(1, lost))); val lostRenderer = renderer(lostOwner)
        val loss = lostRenderer.submit(issue(lostRenderer, 2, 2)); lost.awaitEvent("render")
        assertEquals("w3.execution.device_failure", assertIs<RenderExecutionResult.DeviceFailure>(loss.await()).diagnostics.single().code.value)
        assertEquals(listOf("render", "release", "close"), lost.events)
        assertEquals(listOf("dispose:1"), lostOwner.events)

        val ordinary = FakePrepared(1); val normalOwner = FakeOwner(listOf(FakeBackend(1, ordinary))); val normal = renderer(normalOwner)
        val failed = normal.submit(issue(normal, 2, 2)); ordinary.awaitEvent("render")
        ordinary.future.completeExceptionally(IllegalStateException("readback"))
        assertEquals("w3.execution.readback_failure", assertIs<RenderExecutionResult.DeviceFailure>(failed.await()).diagnostics.single().code.value)
        assertEquals(listOf("render", "release"), ordinary.events)
        assertTrue(normalOwner.events.isEmpty())
    }

    @Test fun `unauthenticated reconstructed plan and a target for another backend never render`() = runBlocking {
        val unused = FakePrepared(1); val issuing = renderer(FakeOwner(listOf(FakeBackend(1, unused))))
        val plan = issue(issuing, 2, 2)
        val reconstructed = copyGraph(plan, PlanId("arbitrary-plan-id"))
        assertIs<RenderExecutionResult.InvalidPlan>(issuing.submit(reconstructed).await())
        assertTrue(unused.events.isEmpty())

        val otherTarget = FakePrepared(1); val rendererForOtherTarget = renderer(FakeOwner(listOf(FakeBackend(1, otherTarget))), width = 3, height = 2)
        assertIs<RenderExecutionResult.InvalidPlan>(rendererForOtherTarget.submit(plan).await())
        assertTrue(otherTarget.events.isEmpty())
    }

    @Test fun `equivalent recompilation does not evict an outstanding public plan`() = runBlocking {
        val prepared = FakePrepared(1); val renderer = renderer(FakeOwner(listOf(FakeBackend(1, prepared))))
        val first = issue(renderer, 2, 2); val equivalent = issue(renderer, 2, 2)
        val firstSubmission = renderer.submit(first); prepared.awaitEvent("render")
        prepared.future.complete(success(2, 2)); assertIs<RenderExecutionResult.Completed<GpuFrameOutput>>(firstSubmission.await())
        assertIs<RenderExecutionResult.Completed<GpuFrameOutput>>(renderer.submit(equivalent).await())
        Unit
    }

    @Test fun `unavailable and rejected submits have exact codes`() = runBlocking {
        val stale = issue(renderer(FakeOwner(listOf(FakeBackend(1, FakePrepared(1))))), 2, 2)
        assertEquals("w3.execution.device_failure", assertIs<RenderExecutionResult.DeviceFailure>(renderer(FakeOwner(emptyList())).submit(stale).await()).diagnostics.single().code.value)
        val refused = FakePrepared(1, GPUFrameImmediateState.FailedBeforeSubmit(diag("submit.refused"))); val refusedRenderer = renderer(FakeOwner(listOf(FakeBackend(1, refused))))
        val submission = refusedRenderer.submit(issue(refusedRenderer, 2, 2)); refused.awaitEvent("render")
        assertEquals("w3.execution.submit_failure", assertIs<RenderExecutionResult.DeviceFailure>(submission.await()).diagnostics.single().code.value)
        assertEquals(listOf("render", "release"), refused.events)
    }

    @Test fun `synchronous preparation exceptions are device failures`() = runBlocking {
        val prepared = FakePrepared(1)
        val native = FakeBackend(1, prepared).apply { prepareFailure = IllegalStateException("prepare") }
        val renderer = renderer(FakeOwner(listOf(native)))

        assertEquals("w3.execution.device_failure", executionCode(renderer.submit(issue(renderer, 2, 2)).await()))
        assertTrue(prepared.events.isEmpty())
    }

    @Test fun `synchronous render exceptions are submit failures`() = runBlocking {
        val prepared = FakePrepared(1).apply { renderFailure = IllegalStateException("submit") }
        val renderer = renderer(FakeOwner(listOf(FakeBackend(1, prepared))))
        val submission = renderer.submit(issue(renderer, 2, 2))
        prepared.awaitEvent("render")

        assertEquals("w3.execution.submit_failure", executionCode(submission.await()))
    }

    @Test fun `await and output failures are readback failures`() = runBlocking {
        val awaitPrepared = FakePrepared(1)
        val awaitOwner = FakeOwner(listOf(FakeBackend(1, awaitPrepared)))
        val awaitRenderer = renderer(
            awaitOwner,
            GpuRenderContext(awaitOwner, GpuCompletionAwaiter { throw IllegalStateException("await") }),
        )
        val awaitSubmission = awaitRenderer.submit(issue(awaitRenderer, 2, 2))
        awaitPrepared.awaitEvent("render")
        assertEquals("w3.execution.readback_failure", executionCode(awaitSubmission.await()))
        assertEquals(listOf("render", "release"), awaitPrepared.events)

        val malformed = FakePrepared(1)
        val malformedRenderer = renderer(FakeOwner(listOf(FakeBackend(1, malformed))))
        val malformedSubmission = malformedRenderer.submit(issue(malformedRenderer, 2, 2))
        malformed.awaitEvent("render")
        malformed.future.complete(success(1, 1))
        assertEquals("w3.execution.readback_failure", executionCode(malformedSubmission.await()))
    }

    private fun renderer(owner: FakeOwner, context: GpuRenderContext = GpuRenderContext(owner), width: Int = 2, height: Int = 2) = GpuRenderBackend(W3SolidRectPlanCompiler(), context, GpuRenderTargetConfig(SceneExtent(width, height), ColorSpace.SRGB, 1L shl 20))
    private fun issue(renderer: GpuRenderBackend, width: Int, height: Int, color: ColorARGB = ColorARGB.fromPackedUInt(0xFF4080C0u)) = assertIs<RenderPlanResult.Ready<RenderGraph>>(renderer.plan(scene(width, height, color), target(width, height))).plan
    private fun target(w: Int, h: Int) = RenderTargetDescriptor(SceneExtent(w, h), ColorSpace.SRGB)
    private fun scene(w: Int, h: Int, color: ColorARGB = ColorARGB.fromPackedUInt(0xFF4080C0u)): SceneSnapshot =
        scene(w, h, listOf(color))
    private fun scene(w: Int, h: Int, colors: List<ColorARGB>) = SceneSnapshot.of(
        SceneExtent(w, h),
        ColorSpace.SRGB,
        colors.map { color ->
            SceneCommand.Draw(
                DrawNode(
                    GeometryNode.Rect.of(RectF32(0f, 0f, w.toFloat(), h.toFloat())),
                    MaterialNode.Solid(color),
                    CoverageRequest.HARD_EDGE,
                    ClipStackNode.Empty,
                    BlendNode.SrcOver,
                    EffectStack.Empty,
                    Matrix3x3F32.Identity,
                    DrawOrigin.RECT,
                ),
            )
        },
    )
    private fun copyGraph(source: RenderGraph, id: PlanId) = RenderGraph.of(id, source.capabilityId, source.targetExtent, source.colorFormat, source.capabilities, source.budget, source.visualCommandCount, source.resources(), source.passes(), source.dependencies(), source.peakFrameLocalBytes)
    private fun success(
        w: Int,
        h: Int,
        telemetryCounters: Map<GPUFrameStructuralCounter, Long> = emptyMap(),
    ) = completed(
        GPUFrameStructuralOutcome.Succeeded,
        GPUSceneFrameOutput.ReadbackRgba(GPUReadbackRequestID("readback"), ByteArray(w * h * 4)),
        telemetryCounters = telemetryCounters,
    )
    private fun failure(code: String, facts: Map<String, String> = emptyMap()) = completed(GPUFrameStructuralOutcome.Failed, null, diag(code, facts))
    private fun completed(
        outcome: GPUFrameStructuralOutcome,
        output: GPUSceneFrameOutput?,
        diagnostic: GPUDiagnostic? = null,
        telemetryCounters: Map<GPUFrameStructuralCounter, Long> = emptyMap(),
    ) = GPUPreparedSceneCompletedFrameResult(
        GPUFrameAttemptID("test"),
        GPUFrameStructuralPhase.Completed,
        outcome,
        diagnostic,
        output,
        emptyList(),
        GPUFrameStructuralTelemetrySnapshot(
            GPUFrameAttemptID("test"),
            GPUFrameStructuralPhase.Completed,
            outcome,
            diagnostic?.code?.value,
            emptyList(),
            telemetryCounters,
        ),
    )
    private fun diag(code: String, facts: Map<String, String> = emptyMap()) = GPUDiagnostic(GPUDiagnosticCode(code), GPUDiagnosticDomain.Execution, GPUDiagnosticSeverity.Error, code, facts)
    private fun executionCode(result: RenderExecutionResult<*>): String =
        assertIs<RenderExecutionResult.DeviceFailure>(result).diagnostics.single().code.value

    private class FakeOwner(backends: List<FakeBackend>) : GpuBackendRuntimeOwnerPort {
        private val queue = ArrayDeque(backends)
        val events = Collections.synchronizedList(mutableListOf<String>())
        override fun createOrNull(): GpuBackendSessionPort? = queue.removeFirstOrNull()
        override fun disposeGeneration(deviceGeneration: GPUDeviceGenerationID) { events += "dispose:${deviceGeneration.value}" }
        override fun close() { events += "owner-close" }
    }
    private class FakeBackend(generation: Long, vararg sessions: FakePrepared) : GpuBackendSessionPort {
        override val deviceGeneration = GPUDeviceGenerationID(generation); override val capabilities = caps(); private val queue = ArrayDeque(sessions.asList())
        val events = Collections.synchronizedList(mutableListOf<String>())
        var prepareFailure: Throwable? = null
        override fun prepareSceneFrameSession(request: GPUOffscreenTargetRequest): GpuPreparedSceneSessionPort {
            events += "target:${request.width}x${request.height}"
            prepareFailure?.let { throw it }
            return queue.removeFirst()
        }
        override fun close() { events += "backend-close" }
    }
    private class FakePrepared(
        generation: Long,
        private val immediate: GPUFrameImmediateState = GPUFrameImmediateState.Submitted(GPUQueueCompletionTicketID("ticket")),
        private val metricsSnapshot: (Int) -> GpuPreparedFrameMetricsSnapshot = { visualCommandCount ->
            GpuPreparedFrameMetricsSnapshot(
                visualCommandCount = visualCommandCount,
                pipelineBinds = 1L,
                draws = 1L,
                drawIndexed = 0L,
                nativeCounters = mapOf("native.draws" to 1L),
            )
        },
    ) : GpuPreparedSceneSessionPort {
        override val deviceGeneration = GPUDeviceGenerationID(generation)
        val future = CompletableFuture<GPUPreparedSceneCompletedFrameResult>()
        val events = Collections.synchronizedList(mutableListOf<String>())
        var renderFailure: Throwable? = null
        var closeFailure: Throwable? = null
        override fun renderFrame(
            taskList: GPUTaskList,
            outputRequest: GPUSceneFrameOutputRequest,
            visualCommandCount: Int,
        ): GpuPreparedFrameHandle {
            events += "render"
            renderFailure?.let { throw it }
            return GpuPreparedFrameHandle(immediate, future, metricsSnapshot(visualCommandCount)) { events += "release" }
        }
        override fun close() { events += "close"; closeFailure?.let { throw it } }
        suspend fun awaitEvent(event: String) = withTimeout(2_000) { while (event !in events) delay(1) }
    }
    private class QueuedDispatcher : CoroutineDispatcher() {
        private val queued = ConcurrentLinkedQueue<Runnable>()
        private val scheduled = CompletableDeferred<Unit>()

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            queued += block
            scheduled.complete(Unit)
        }

        suspend fun awaitWork() { scheduled.await() }

        fun runNext() { checkNotNull(queued.poll()).run() }
    }
    private companion object { fun caps() = GPUCapabilities(GPUImplementationIdentity("fake", "fake", "fake", "fake"), listOf(GPUCapabilityFact("first_slice.fill_rect.native", "test", "supported", true, "w3")), snapshotId = "fake", limits = GPULimits(2048, 256, 256, maxBufferSize = 1L shl 20, maxDynamicUniformBuffersPerPipelineLayout = 1), supportedTextureFormats = setOf(GPUTextureFormat.RGBA8UnormSrgb), rendererFeatures = setOf(GPURendererFeature.RenderPass, GPURendererFeature.Readback)) }
}
