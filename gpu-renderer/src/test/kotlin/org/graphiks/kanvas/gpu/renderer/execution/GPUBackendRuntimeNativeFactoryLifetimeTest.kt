package org.graphiks.kanvas.gpu.renderer.execution

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID

class GPUBackendRuntimeNativeFactoryLifetimeTest {

    @Test
    fun `concurrent creates never duplicate the backend session`() {
        val entered = CountDownLatch(1)
        val gate = CountDownLatch(1)
        val creatorInvocations = AtomicInteger(0)
        val firstSession = AtomicReference<GPUBackendSession?>()
        GPUBackendRuntimeNativeFactory.backendCreator = {
            val invocation = creatorInvocations.incrementAndGet()
            if (invocation == 1) {
                entered.countDown()
                assertTrue(gate.await(10, TimeUnit.SECONDS), "first creator must be allowed through")
            }
            InertBackendSession(GPUBackendRuntimeNativeFactory.nextDeviceGeneration())
        }
        try {
            val first = Thread {
                firstSession.set(GPUBackendRuntimeNativeFactory.createOrNull())
            }
            first.start()
            assertTrue(entered.await(10, TimeUnit.SECONDS), "the first creator must enter the factory")
            // Release the first creator before the concurrent create: the factory lock serializes
            // the second call behind the in-flight creation, so the creator runs exactly once and
            // both callers receive the same stamped generation.
            gate.countDown()
            val second = GPUBackendRuntimeNativeFactory.createOrNull()
            first.join(10_000)
            assertNotNull(second, "a concurrent create returns a session")
            assertEquals(1, creatorInvocations.get(), "concurrent creates must share one backend")
            assertEquals(
                firstSession.get()?.deviceGeneration, second.deviceGeneration,
                "both callers receive the same shared device generation",
            )
        } finally {
            gate.countDown()
            GPUBackendRuntimeNativeFactory.dispose()
            GPUBackendRuntimeNativeFactory.backendCreator = GPUBackendRuntimeNativeFactory.defaultBackendCreator
        }
    }

    @Test
    fun `every post dispose creation advances the device generation exactly once`() {
        GPUBackendRuntimeNativeFactory.backendCreator = {
            InertBackendSession(GPUBackendRuntimeNativeFactory.nextDeviceGeneration())
        }
        try {
            val first = GPUBackendRuntimeNativeFactory.createOrNull()
            assertNotNull(first)
            val firstGeneration = first.deviceGeneration.value
            GPUBackendRuntimeNativeFactory.dispose()
            val second = GPUBackendRuntimeNativeFactory.createOrNull()
            assertNotNull(second)
            assertEquals(firstGeneration + 1L, second.deviceGeneration.value, "one dispose advances the generation by exactly one")
            GPUBackendRuntimeNativeFactory.dispose()
            val third = GPUBackendRuntimeNativeFactory.createOrNull()
            assertNotNull(third)
            assertEquals(firstGeneration + 2L, third.deviceGeneration.value)
        } finally {
            GPUBackendRuntimeNativeFactory.dispose()
            GPUBackendRuntimeNativeFactory.backendCreator = GPUBackendRuntimeNativeFactory.defaultBackendCreator
        }
    }

    @Test
    fun `repeated dispose is idempotent and never leaks the session after recreation`() {
        val lastBackend = AtomicReference<InertBackendSession?>()
        GPUBackendRuntimeNativeFactory.backendCreator = {
            InertBackendSession(GPUBackendRuntimeNativeFactory.nextDeviceGeneration())
                .also { lastBackend.set(it) }
        }
        try {
            repeat(3) { GPUBackendRuntimeNativeFactory.dispose() }
            val created = GPUBackendRuntimeNativeFactory.createOrNull()
            assertNotNull(created)
            repeat(3) { GPUBackendRuntimeNativeFactory.dispose() }
            assertEquals(
                1, lastBackend.get()?.closeCount,
                "the shared backend is closed exactly once across repeated dispose",
            )
        } finally {
            GPUBackendRuntimeNativeFactory.backendCreator = GPUBackendRuntimeNativeFactory.defaultBackendCreator
        }
    }
}

/** Minimal [GPUBackendSession] fake carrying the factory-stamped device generation. */
private class InertBackendSession(
    override val deviceGeneration: GPUDeviceGenerationID,
) : GPUBackendSession {
    override val adapterInfo: GPUBackendAdapterSummary? = null

    /** Number of close() invocations; used to prove repeated dispose closes the backend once. */
    var closeCount = 0
        private set

    override fun createOffscreenTarget(request: GPUOffscreenTargetRequest): GPUBackendOffscreenTarget =
        error("InertBackendSession cannot create offscreen targets")

    override fun prepareSceneFrameSession(request: GPUOffscreenTargetRequest): GPUPreparedSceneFrameSession =
        error("InertBackendSession cannot prepare scene frame sessions")

    override fun close() {
        closeCount += 1
    }
}
