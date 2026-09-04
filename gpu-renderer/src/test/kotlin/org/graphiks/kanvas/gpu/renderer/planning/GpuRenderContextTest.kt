package org.graphiks.kanvas.gpu.renderer.planning

import io.ygdrasil.webgpu.GPUTextureFormat
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.plan.PlanLogicalColorFormat
import org.graphiks.kanvas.gpu.renderer.capabilities.*
import org.graphiks.kanvas.gpu.renderer.execution.GPUOffscreenTargetRequest
import org.graphiks.kanvas.gpu.renderer.execution.GPUSceneFrameOutputRequest
import org.graphiks.kanvas.gpu.renderer.execution.GPUFrameImmediateState
import org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedSceneCompletedFrameResult
import org.graphiks.kanvas.gpu.renderer.execution.GPUQueueCompletionTicketID
import org.graphiks.kanvas.gpu.renderer.execution.GPUSceneFrameOutput
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendAdapterSummary
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendOffscreenTarget
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeNativeFactory
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendSession
import org.graphiks.kanvas.gpu.renderer.color.GPUColorInterpretation
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskList
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackRequestID
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUFrameAttemptID
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUFrameStructuralOutcome
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUFrameStructuralPhase
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUFrameStructuralTelemetrySnapshot
import org.graphiks.kanvas.color.ColorSpace
import org.graphiks.kanvas.render.ir.BlendNode
import org.graphiks.kanvas.render.ir.ClipStackNode
import org.graphiks.kanvas.render.ir.CoverageRequest
import org.graphiks.kanvas.render.ir.DrawNode
import org.graphiks.kanvas.render.ir.DrawOrigin
import org.graphiks.kanvas.render.ir.EffectStack
import org.graphiks.kanvas.render.ir.GeometryNode
import org.graphiks.kanvas.render.ir.MaterialNode
import org.graphiks.kanvas.render.ir.PaintNode
import org.graphiks.kanvas.render.ir.PaintStyleNode
import org.graphiks.kanvas.render.ir.RenderTargetDescriptor
import org.graphiks.kanvas.render.ir.SceneCommand
import org.graphiks.kanvas.render.ir.SceneExtent
import org.graphiks.kanvas.render.ir.SceneSnapshot
import org.graphiks.kanvas.render.ir.StrokeCapNode
import org.graphiks.kanvas.render.ir.StrokeJoinNode
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.PathBuilder
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.matrix.Matrix3x3F32

class GpuRenderContextTest {
    @Test
    fun `surface executor selects W3 for aligned and W4a for fractional rectangles`() {
        val context = planningContext()
        val executor = context.planSurfaceExecutor()

        val aligned = assertIs<GpuPlanSurfacePlanResult.Ready>(executor.plan(
            rectangleScene(RectF32(0f, 0f, 2f, 2f), CoverageRequest.HARD_EDGE),
            target(),
            1L shl 20,
        ))
        val fractional = assertIs<GpuPlanSurfacePlanResult.Ready>(executor.plan(
            rectangleScene(RectF32(0.5f, 0.5f, 1.5f, 1.5f), CoverageRequest.ANTIALIASED),
            target(),
            1L shl 20,
        ))

        assertTrue(aligned.token !== fractional.token)
    }

    @Test
    fun `surface executor keeps path scenes as typed gaps`() {
        val result = planningContext().planSurfaceExecutor().plan(
            SceneSnapshot.of(
                SceneExtent(2, 2),
                ColorSpace.SRGB,
                listOf(
                    SceneCommand.Draw(
                        DrawNode(
                            GeometryNode.Path(PathBuilder().addRect(RectF32(0f, 0f, 2f, 2f)).build()),
                            MaterialNode.Solid(ColorARGB.Red),
                            CoverageRequest.HARD_EDGE,
                            ClipStackNode.Empty,
                            BlendNode.SrcOver,
                            EffectStack.Empty,
                            Matrix3x3F32.Identity,
                            DrawOrigin.PATH,
                        ),
                    ),
                ),
            ),
            target(),
            1L shl 20,
        )

        assertIs<GpuPlanSurfacePlanResult.GapNotMigrated>(result)
    }

    @Test
    fun `fractional surface tokens are one shot and bound to their issuing context`() {
        val expectedBytes = byteArrayOf(
            1, 2, 3, 4,
            5, 6, 7, 8,
            9, 10, 11, 12,
            13, 14, 15, 16,
        )
        val firstContext = successfulPlanningContext(expectedBytes)
        val firstExecutor = firstContext.planSurfaceExecutor()
        val token = assertIs<GpuPlanSurfacePlanResult.Ready>(firstExecutor.plan(
            rectangleScene(RectF32(0.5f, 0.5f, 1.5f, 1.5f), CoverageRequest.ANTIALIASED),
            target(),
            1L shl 20,
        )).token

        val foreign = planningContext().planSurfaceExecutor().submit(token)
        val first = firstExecutor.submit(token)
        val second = firstExecutor.submit(token)

        assertEquals("w3.lowering.incompatible_plan", assertIs<GpuPlanSurfaceSubmitResult.Terminal>(foreign).diagnostics.single().code.value)
        assertContentEquals(expectedBytes, assertIs<GpuPlanSurfaceSubmitResult.Completed>(first).output.copyBytes())
        assertEquals("w3.lowering.incompatible_plan", assertIs<GpuPlanSurfaceSubmitResult.Terminal>(second).diagnostics.single().code.value)
    }

    @Test fun `session keys reject invalid ownership coordinates`() {
        assertFailsWith<IllegalArgumentException> { GpuRenderSessionKey(-1, 1, 1, PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL) }
        assertFailsWith<IllegalArgumentException> { GpuRenderSessionKey(0, 0, 1, PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL) }
    }

    @Test fun `context closes prepared sessions before owner and refuses new session creation`() {
        val events = mutableListOf<String>(); val session = Session(1, events); val native = Backend(1, session); val owner = Owner(native, events); val context = GpuRenderContext(owner)
        context.capabilities(); context.prepared(GpuRenderSessionKey(1, 2, 2, PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL))
        assertEquals(GPUColorInterpretation.LinearPremul, native.request!!.colorInterpretation)
        context.close(); context.close()
        assertEquals(listOf("session", "owner"), events)
        assertNull(context.prepared(GpuRenderSessionKey(1, 2, 2, PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL)))
    }

    @Test fun `context closes every prepared session and its owner when one close fails`() {
        val events = mutableListOf<String>()
        val broken = Session(1, events, "broken", closeFailure = IllegalStateException("broken close"))
        val healthy = Session(1, events, "healthy")
        val context = GpuRenderContext(Owner(Backend(1, broken, healthy), events))

        context.capabilities()
        context.prepared(GpuRenderSessionKey(1, 2, 2, PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL))
        context.prepared(GpuRenderSessionKey(1, 3, 2, PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL))

        context.close()

        assertEquals(listOf("broken", "healthy", "owner"), events)
    }

    @Test fun `generation invalidation evicts session then permits a new runtime snapshot`() {
        val events = mutableListOf<String>(); val stale = Session(1, events); val fresh = Session(2, events); val owner = Owner(Backend(1, stale), events, Backend(2, fresh)); val context = GpuRenderContext(owner)
        context.capabilities(); context.prepared(GpuRenderSessionKey(1, 2, 2, PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL))
        context.invalidateDeviceGeneration(GPUDeviceGenerationID(1))
        context.invalidateDeviceGeneration(GPUDeviceGenerationID(1))
        assertEquals(listOf("session", "dispose:1"), events)
        assertEquals(2L, context.capabilities()!!.deviceGeneration)
    }

    @Test fun `lifecycle epoch change discards cached runtime before the next plan`() {
        val events = mutableListOf<String>()
        val stale = Session(1, events)
        val fresh = Session(2, events)
        val owner = EpochOwner(Backend(1, stale), Backend(2, fresh))
        val context = GpuRenderContext(owner)

        assertEquals(1L, context.capabilities()!!.deviceGeneration)
        context.prepared(GpuRenderSessionKey(1, 2, 2, PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL))

        owner.advanceLifecycle()

        assertEquals(2L, context.capabilities()!!.deviceGeneration)
        assertEquals(listOf("session"), events)
    }

    @Test fun `invalidation blocks stale acquisition until its disposal drains`() {
        val events = mutableListOf<String>()
        val stale = Session(1, events)
        val disposeStarted = CountDownLatch(1)
        val allowDispose = CountDownLatch(1)
        val owner = BlockingOwner(Backend(1, stale), events, disposeStarted, allowDispose, Backend(2, Session(2, events)))
        val context = GpuRenderContext(owner)
        val staleKey = GpuRenderSessionKey(1, 2, 2, PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL)
        context.capabilities(); context.prepared(staleKey)

        val invalidation = Thread { context.invalidateDeviceGeneration(GPUDeviceGenerationID(1)) }
        invalidation.start()
        assertTrue(disposeStarted.await(2, java.util.concurrent.TimeUnit.SECONDS))
        assertNull(context.capabilities())
        assertNull(context.prepared(staleKey))
        allowDispose.countDown()
        invalidation.join(2_000)

        assertEquals(2L, context.capabilities()!!.deviceGeneration)
        assertEquals(listOf("open:1", "session", "dispose-start:1", "dispose-finish:1", "open:2"), events)
    }

    @Test fun `production completion awaiter never cancels its source future`() = runBlocking {
        val source = TrackingFuture<GPUPreparedSceneCompletedFrameResult>()
        val waiter = async { DefaultGpuCompletionAwaiter.await(source) }
        waiter.cancelAndJoin()
        assertTrue(!source.cancelCalled)
    }

    @Test fun `production contexts retain the runtime until their last owner closes`() {
        val events = mutableListOf<String>()
        val native = ProductionBackend(GPUBackendRuntimeNativeFactory.nextDeviceGeneration(), events)
        GPUBackendRuntimeNativeFactory.dispose()
        GPUBackendRuntimeNativeFactory.backendCreator = { native }
        val first = GpuRenderContext.createProduction()
        val second = GpuRenderContext.createProduction()

        try {
            val generation = first.capabilities()!!.deviceGeneration
            assertEquals(generation, second.capabilities()!!.deviceGeneration)

            first.close()

            assertTrue(events.isEmpty(), "closing one production context must retain the shared runtime")
            assertEquals(generation, second.capabilities()!!.deviceGeneration)

            second.close()

            assertEquals(listOf("runtime"), events)
        } finally {
            first.close()
            second.close()
            GPUBackendRuntimeNativeFactory.dispose()
            GPUBackendRuntimeNativeFactory.backendCreator = GPUBackendRuntimeNativeFactory.defaultBackendCreator
        }
    }

    @Test fun `prepared cache evicts the least recently used idle session at eight keys`() {
        val events = mutableListOf<String>()
        val sessions = (1..9).map { ordinal -> Session(1, events, "session-$ordinal") }
        val context = GpuRenderContext(Owner(Backend(1, sessions.first(), *sessions.drop(1).toTypedArray()), events))
        val keys = (1..9).map(::sessionKey)

        try {
            context.capabilities()
            keys.take(8).forEach(context::prepared)
            context.prepared(keys.first())

            context.prepared(keys.last())

            assertEquals(listOf("session-2"), events)
        } finally {
            context.close()
        }
    }

    @Test fun `prepared cache never evicts active sessions and rejects a ninth key`() = runBlocking {
        val events = mutableListOf<String>()
        val sessions = (1..9).map { ordinal -> Session(1, events, "session-$ordinal") }
        val context = GpuRenderContext(Owner(Backend(1, sessions.first(), *sessions.drop(1).toTypedArray()), events))
        val keys = (1..9).map(::sessionKey)
        val entered = CountDownLatch(8)
        val release = CountDownLatch(1)

        try {
            context.capabilities()
            keys.take(8).forEach(context::prepared)
            val active = keys.take(8).map { key ->
                async(Dispatchers.Default) {
                    val reservation = assertIs<GpuPreparedSessionAcquisition.Ready>(context.acquirePrepared(key)).reservation
                    context.withLease(reservation) {
                        entered.countDown()
                        release.await(2, java.util.concurrent.TimeUnit.SECONDS)
                        "released"
                    }
                }
            }
            assertTrue(entered.await(2, java.util.concurrent.TimeUnit.SECONDS))

            assertIs<GpuPreparedSessionAcquisition.Unavailable>(context.acquirePrepared(keys.last()))

            release.countDown()
            assertEquals(List(8) { "released" }, active.awaitAll())
            val ninth = assertIs<GpuPreparedSessionAcquisition.Ready>(context.acquirePrepared(keys.last()))
            assertEquals("released", context.withLease(ninth.reservation) { "released" })
        } finally {
            release.countDown()
            context.close()
        }
        Unit
    }

    @Test fun `newly prepared session remains runnable while a ninth key churns the idle LRU`() = runBlocking {
        val events = mutableListOf<String>()
        val sessions = (1..9).map { ordinal -> Session(1, events, "session-$ordinal") }
        val context = GpuRenderContext(Owner(Backend(1, sessions.first(), *sessions.drop(1).toTypedArray()), events))
        val keys = (1..9).map(::sessionKey)

        try {
            context.capabilities()
            keys.take(7).forEach(context::prepared)
            val acquired = assertIs<GpuPreparedSessionAcquisition.Ready>(context.acquirePrepared(keys[7]))

            keys.take(7).forEach(context::prepared)
            context.prepared(keys[8])

            val rendered = context.withLease(acquired.reservation) { "rendered" }

            assertEquals("rendered", rendered)
            assertTrue("session-8" !in events)
        } finally {
            context.close()
        }
    }

    @Test fun `reservation stays bound to the context that acquired it`() = runBlocking {
        val events = mutableListOf<String>()
        val first = GpuRenderContext(Owner(Backend(1, Session(1, events)), events))
        val second = GpuRenderContext(Owner(Backend(1, Session(1, events)), events))
        val key = sessionKey(1)

        try {
            first.capabilities()
            second.capabilities()
            val reservation = assertIs<GpuPreparedSessionAcquisition.Ready>(first.acquirePrepared(key)).reservation

            assertNull(second.withLease(reservation) { "foreign" })
            assertEquals("owned", first.withLease(reservation) { "owned" })
        } finally {
            first.close()
            second.close()
        }
    }

    private class Owner(first: Backend, private val events: MutableList<String>, vararg rest: Backend) : GpuBackendRuntimeOwnerPort {
        private val queue = ArrayDeque(listOf(first) + rest); override fun createOrNull(): GpuBackendSessionPort? = queue.removeFirstOrNull()
        override fun disposeGeneration(deviceGeneration: GPUDeviceGenerationID) { events += "dispose:${deviceGeneration.value}" }
        override fun close() { events += "owner" }
    }
    private class BlockingOwner(
        first: Backend,
        private val events: MutableList<String>,
        private val disposeStarted: CountDownLatch,
        private val allowDispose: CountDownLatch,
        vararg rest: Backend,
    ) : GpuBackendRuntimeOwnerPort {
        private val queue = ArrayDeque(listOf(first) + rest)
        override fun createOrNull(): GpuBackendSessionPort? = queue.removeFirstOrNull()?.also { events += "open:${it.deviceGeneration.value}" }
        override fun disposeGeneration(deviceGeneration: GPUDeviceGenerationID) {
            events += "dispose-start:${deviceGeneration.value}"
            disposeStarted.countDown()
            allowDispose.await()
            events += "dispose-finish:${deviceGeneration.value}"
        }
        override fun close() { events += "owner" }
    }
    private class EpochOwner(first: Backend, vararg rest: Backend) : GpuBackendRuntimeOwnerPort {
        private val queue = ArrayDeque(listOf(first) + rest)
        private var epoch = 0L
        override fun createOrNull(): GpuBackendSessionPort? = queue.removeFirstOrNull()
        override fun lifecycleEpoch(): Long = epoch
        override fun disposeGeneration(deviceGeneration: GPUDeviceGenerationID) = Unit
        override fun close() = Unit
        fun advanceLifecycle() { epoch += 1L }
    }
    private class Backend(generation: Long, first: Session, vararg rest: Session) : GpuBackendSessionPort {
        override val deviceGeneration = GPUDeviceGenerationID(generation); override val capabilities = caps()
        private val sessions = ArrayDeque(listOf(first) + rest)
        var request: GPUOffscreenTargetRequest? = null
        override fun prepareSceneFrameSession(request: GPUOffscreenTargetRequest): GpuPreparedSceneSessionPort { this.request = request; return sessions.removeFirst() }
        override fun close() = Unit
    }
    private class Session(
        generation: Long,
        private val events: MutableList<String>,
        private val closeEvent: String = "session",
        private val closeFailure: Throwable? = null,
        private val completedFrame: GPUPreparedSceneCompletedFrameResult? = null,
    ) : GpuPreparedSceneSessionPort {
        override val deviceGeneration = GPUDeviceGenerationID(generation)
        override fun renderFrame(
            taskList: GPUTaskList,
            outputRequest: GPUSceneFrameOutputRequest,
            visualCommandCount: Int,
        ): GpuPreparedFrameHandle {
            val completed = completedFrame ?: error("not used")
            return GpuPreparedFrameHandle(
                immediateState = GPUFrameImmediateState.Submitted(GPUQueueCompletionTicketID("test")),
                completion = CompletableFuture.completedFuture(completed),
                metricsSnapshot = GpuPreparedFrameMetricsSnapshot(
                    visualCommandCount = visualCommandCount,
                    pipelineBinds = 1L,
                    draws = 1L,
                    drawIndexed = 0L,
                    nativeCounters = mapOf("native.draws" to 1L),
                ),
            )
        }
        override fun close() { events += closeEvent; closeFailure?.let { throw it } }
    }
    private class ProductionBackend(
        override val deviceGeneration: GPUDeviceGenerationID,
        private val events: MutableList<String>,
    ) : GPUBackendSession {
        override val adapterInfo: GPUBackendAdapterSummary? = null
        override val capabilities: GPUCapabilities = caps()
        override fun createOffscreenTarget(request: GPUOffscreenTargetRequest): GPUBackendOffscreenTarget =
            error("not used")
        override fun prepareSceneFrameSession(request: GPUOffscreenTargetRequest) = error("not used")
        override fun close() { events += "runtime" }
    }
    private class TrackingFuture<T> : CompletableFuture<T>() { var cancelCalled = false; override fun cancel(mayInterruptIfRunning: Boolean): Boolean { cancelCalled = true; return super.cancel(mayInterruptIfRunning) } }
    private companion object {
        fun planningContext(): GpuRenderContext {
            val events = mutableListOf<String>()
            return GpuRenderContext(Owner(Backend(1, Session(1, events)), events))
        }
        fun successfulPlanningContext(expectedBytes: ByteArray): GpuRenderContext {
            val events = mutableListOf<String>()
            val completed = GPUPreparedSceneCompletedFrameResult(
                GPUFrameAttemptID("test"),
                GPUFrameStructuralPhase.Completed,
                GPUFrameStructuralOutcome.Succeeded,
                null,
                GPUSceneFrameOutput.ReadbackRgba(GPUReadbackRequestID("test"), expectedBytes),
                emptyList(),
                GPUFrameStructuralTelemetrySnapshot(
                    GPUFrameAttemptID("test"),
                    GPUFrameStructuralPhase.Completed,
                    GPUFrameStructuralOutcome.Succeeded,
                    null,
                    emptyList(),
                    emptyMap(),
                ),
            )
            return GpuRenderContext(Owner(Backend(1, Session(1, events, completedFrame = completed)), events))
        }
        fun target(): RenderTargetDescriptor = RenderTargetDescriptor(SceneExtent(2, 2), ColorSpace.SRGB)
        fun rectangleScene(bounds: RectF32, coverage: CoverageRequest): SceneSnapshot = SceneSnapshot.of(
            SceneExtent(2, 2),
            ColorSpace.SRGB,
            listOf(
                SceneCommand.Draw(
                    DrawNode(
                        GeometryNode.Rect.of(bounds),
                        MaterialNode.Solid(ColorARGB.Red),
                        coverage,
                        ClipStackNode.Empty,
                        BlendNode.SrcOver,
                        EffectStack.Empty,
                        Matrix3x3F32.Identity,
                        DrawOrigin.RECT,
                        PaintNode(
                            ColorARGB.Red,
                            null,
                            org.graphiks.kanvas.render.ir.BlendMode.SRC_OVER,
                            null,
                            null,
                            null,
                            null,
                            null,
                            PaintStyleNode.FILL,
                            0f,
                            StrokeCapNode.BUTT,
                            StrokeJoinNode.MITER,
                            4f,
                            true,
                        ),
                    ),
                ),
            ),
        )
        fun sessionKey(ordinal: Int) = GpuRenderSessionKey(
            deviceGeneration = 1,
            width = ordinal + 1,
            height = 1,
            internalFormat = PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL,
        )
        fun caps() = GPUCapabilities(
            GPUImplementationIdentity("fake", "fake", "fake", "fake"),
            emptyList(),
            snapshotId = "fake",
            limits = GPULimits(2048, 256, 256, maxBufferSize = 1L shl 20, maxDynamicUniformBuffersPerPipelineLayout = 1),
            supportedTextureFormats = setOf(GPUTextureFormat.RGBA8UnormSrgb),
            textureFormatSampleSupport = GPUTextureFormatSampleSupport(
                mapOf(GPUTextureFormat.RGBA8UnormSrgb to GPUTextureSampleCountSupport(setOf(1))),
            ),
            rendererFeatures = setOf(
                GPURendererFeature.RenderPass,
                GPURendererFeature.CopyUpload,
                GPURendererFeature.UniformBuffer,
                GPURendererFeature.Readback,
            ),
        )
    }
}
