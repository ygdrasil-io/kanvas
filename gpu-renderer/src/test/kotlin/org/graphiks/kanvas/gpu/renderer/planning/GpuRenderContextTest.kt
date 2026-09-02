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
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendAdapterSummary
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendOffscreenTarget
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeNativeFactory
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendSession
import org.graphiks.kanvas.gpu.renderer.color.GPUColorInterpretation
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskList

class GpuRenderContextTest {
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
                    context.withLease(key) {
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
            assertIs<GpuPreparedSessionAcquisition.Ready>(context.acquirePrepared(keys.last()))
        } finally {
            release.countDown()
            context.close()
        }
        Unit
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
    ) : GpuPreparedSceneSessionPort {
        override val deviceGeneration = GPUDeviceGenerationID(generation)
        override fun renderFrame(
            taskList: GPUTaskList,
            outputRequest: GPUSceneFrameOutputRequest,
            visualCommandCount: Int,
        ) = error("not used")
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
            rendererFeatures = setOf(GPURendererFeature.RenderPass, GPURendererFeature.Readback),
        )
    }
}
