package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.GPUBindGroup
import io.ygdrasil.webgpu.GPUBuffer
import java.lang.reflect.Proxy
import java.util.Collections
import java.util.IdentityHashMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID

class GPUWgpu4kCorePrimitiveFramePoolTest {
    @Test
    fun `initial checkout uses generation scoped power of two floors and rollback reuses the slot`() {
        val factory = FakeFactory()
        val pool = GPUWgpu4kCorePrimitiveFramePool(GENERATION, factory)

        val first = pool.acquire(requirements())
            .let { assertIs<GPUWgpu4kCorePrimitiveFramePoolCheckout.Acquired>(it).lease }

        assertEquals(GENERATION, first.deviceGeneration)
        assertEquals(
            GPUWgpu4kCorePrimitiveFramePoolCapacities(
                vertexBytes = 16L * 1024L,
                indexBytes = 4L * 1024L,
                uniformBytes = 4L * 1024L,
            ),
            first.capacities,
        )
        assertEquals(
            listOf(
                GPUWgpu4kCorePrimitiveFramePoolResource.VertexBuffer,
                GPUWgpu4kCorePrimitiveFramePoolResource.IndexBuffer,
                GPUWgpu4kCorePrimitiveFramePoolResource.UniformBuffer,
                GPUWgpu4kCorePrimitiveFramePoolResource.BindGroup,
            ),
            factory.creations.map(CreatedHandle::resource),
        )
        assertIs<GPUWgpu4kCorePrimitiveFramePoolLeaseTransition.Applied>(first.rollbackBeforeSubmit())

        val reused = pool.acquire(requirements())
            .let { assertIs<GPUWgpu4kCorePrimitiveFramePoolCheckout.Acquired>(it).lease }
        assertEquals(first.slotId, reused.slotId)
        assertSame(first.handles.vertexBuffer, reused.handles.vertexBuffer)
        assertSame(first.handles.indexBuffer, reused.handles.indexBuffer)
        assertSame(first.handles.uniformBuffer, reused.handles.uniformBuffer)
        assertSame(first.handles.bindGroup, reused.handles.bindGroup)
        assertEquals(4, factory.creations.size)

        reused.rollbackBeforeSubmit()
        pool.close()
    }

    @Test
    fun `generation mismatch and a fourth concurrent checkout are typed non blocking refusals`() {
        val factory = FakeFactory()
        val pool = GPUWgpu4kCorePrimitiveFramePool(GENERATION, factory)

        val mismatch = assertIs<GPUWgpu4kCorePrimitiveFramePoolCheckout.Refused>(
            pool.acquire(requirements(deviceGeneration = GPUDeviceGenerationID(8))),
        )
        assertEquals(
            GPUWgpu4kCorePrimitiveFramePoolRefusal.DeviceGenerationMismatch(
                expected = GENERATION,
                observed = GPUDeviceGenerationID(8),
            ),
            mismatch.reason,
        )
        val leases = List(3) {
            pool.acquire(requirements())
                .let { result -> assertIs<GPUWgpu4kCorePrimitiveFramePoolCheckout.Acquired>(result).lease }
        }
        assertEquals(listOf(0, 1, 2), leases.map { it.slotId })

        val saturated = assertIs<GPUWgpu4kCorePrimitiveFramePoolCheckout.Refused>(
            pool.acquire(requirements()),
        )
        assertEquals(GPUWgpu4kCorePrimitiveFramePoolRefusal.Saturated(maxSlots = 3), saturated.reason)
        leases.forEach { it.rollbackBeforeSubmit() }
        pool.close()
    }

    @Test
    fun `zero sized direct slabs are typed refusals and allocate nothing`() {
        GPUWgpu4kCorePrimitiveFramePoolResource.entries.forEach { invalidResource ->
            if (invalidResource == GPUWgpu4kCorePrimitiveFramePoolResource.BindGroup) return@forEach
            val factory = FakeFactory()
            val pool = GPUWgpu4kCorePrimitiveFramePool(GENERATION, factory)
            val request = requirements(
                vertexBytes = if (invalidResource == GPUWgpu4kCorePrimitiveFramePoolResource.VertexBuffer) 0L else 1L,
                indexBytes = if (invalidResource == GPUWgpu4kCorePrimitiveFramePoolResource.IndexBuffer) 0L else 1L,
                uniformBytes = if (invalidResource == GPUWgpu4kCorePrimitiveFramePoolResource.UniformBuffer) 0L else 1L,
            )

            val refusal = assertIs<GPUWgpu4kCorePrimitiveFramePoolCheckout.Refused>(pool.acquire(request))

            assertEquals(
                GPUWgpu4kCorePrimitiveFramePoolRefusal.InvalidCapacity(invalidResource, 0L),
                refusal.reason,
            )
            assertTrue(factory.creations.isEmpty())
            pool.close()
        }
    }

    @Test
    fun `submitted slots become reusable only after certain completion`() {
        val factory = FakeFactory()
        val pool = GPUWgpu4kCorePrimitiveFramePool(GENERATION, factory)
        val submitted = pool.acquire(requirements()).acquiredLease()
        assertIs<GPUWgpu4kCorePrimitiveFramePoolLeaseTransition.Applied>(submitted.markSubmitted())

        val whilePending = pool.acquire(requirements()).acquiredLease()
        assertNotSame(submitted.handles.vertexBuffer, whilePending.handles.vertexBuffer)
        whilePending.rollbackBeforeSubmit()
        val pendingStillNotReused = pool.acquire(requirements()).acquiredLease()
        assertEquals(whilePending.slotId, pendingStillNotReused.slotId)
        pendingStillNotReused.rollbackBeforeSubmit()

        assertIs<GPUWgpu4kCorePrimitiveFramePoolLeaseTransition.Applied>(submitted.completeSuccessfully())
        val afterCompletion = pool.acquire(requirements()).acquiredLease()
        assertEquals(submitted.slotId, afterCompletion.slotId)
        assertSame(submitted.handles.vertexBuffer, afterCompletion.handles.vertexBuffer)
        afterCompletion.rollbackBeforeSubmit()
        pool.close()
    }

    @Test
    fun `uncertain submit or completion quarantines the slot permanently`() {
        val pool = GPUWgpu4kCorePrimitiveFramePool(GENERATION, FakeFactory())
        val uncertainSubmit = pool.acquire(requirements()).acquiredLease()
        assertIs<GPUWgpu4kCorePrimitiveFramePoolLeaseTransition.Applied>(uncertainSubmit.quarantineUncertain())
        val uncertainCompletion = pool.acquire(requirements()).acquiredLease()
        uncertainCompletion.markSubmitted()
        assertIs<GPUWgpu4kCorePrimitiveFramePoolLeaseTransition.Applied>(uncertainCompletion.quarantineUncertain())
        val third = pool.acquire(requirements()).acquiredLease()
        third.quarantineUncertain()

        assertEquals(
            GPUWgpu4kCorePrimitiveFramePoolRefusal.Saturated(3),
            assertIs<GPUWgpu4kCorePrimitiveFramePoolCheckout.Refused>(pool.acquire(requirements())).reason,
        )
        assertIs<GPUWgpu4kCorePrimitiveFramePoolLeaseTransition.Refused>(
            uncertainCompletion.completeSuccessfully(),
        )
        pool.close()
    }

    @Test
    fun `quarantined slots are never reused and session teardown closes every handle once`() {
        val factory = FakeFactory()
        val pool = GPUWgpu4kCorePrimitiveFramePool(GENERATION, factory)
        val quarantined = pool.acquire(requirements()).acquiredLease()
        quarantined.quarantineUncertain()

        val replacement = pool.acquire(requirements()).acquiredLease()

        assertNotSame(quarantined.handles.vertexBuffer, replacement.handles.vertexBuffer)
        assertNotSame(quarantined.handles.indexBuffer, replacement.handles.indexBuffer)
        assertNotSame(quarantined.handles.uniformBuffer, replacement.handles.uniformBuffer)
        assertNotSame(quarantined.handles.bindGroup, replacement.handles.bindGroup)
        replacement.rollbackBeforeSubmit()
        pool.close()
        listOf(
            quarantined.handles.vertexBuffer,
            quarantined.handles.indexBuffer,
            quarantined.handles.uniformBuffer,
            quarantined.handles.bindGroup,
            replacement.handles.vertexBuffer,
            replacement.handles.indexBuffer,
            replacement.handles.uniformBuffer,
            replacement.handles.bindGroup,
        ).forEach { handle -> assertEquals(1, factory.closeAttempts(handle)) }
    }

    @Test
    fun `vertex and index growth preserves the uniform buffer and bind group`() {
        val factory = FakeFactory()
        val pool = GPUWgpu4kCorePrimitiveFramePool(GENERATION, factory)
        val initial = pool.acquire(requirements()).acquiredLease()
        initial.rollbackBeforeSubmit()

        val grown = pool.acquire(
            requirements(vertexBytes = 16L * 1024L + 1L, indexBytes = 4L * 1024L + 1L),
        ).acquiredLease()

        assertEquals(
            GPUWgpu4kCorePrimitiveFramePoolCapacities(32L * 1024L, 8L * 1024L, 4L * 1024L),
            grown.capacities,
        )
        assertNotSame(initial.handles.vertexBuffer, grown.handles.vertexBuffer)
        assertNotSame(initial.handles.indexBuffer, grown.handles.indexBuffer)
        assertSame(initial.handles.uniformBuffer, grown.handles.uniformBuffer)
        assertSame(initial.handles.bindGroup, grown.handles.bindGroup)
        assertEquals(1, factory.closeAttempts(initial.handles.vertexBuffer))
        assertEquals(1, factory.closeAttempts(initial.handles.indexBuffer))
        assertEquals(0, factory.closeAttempts(initial.handles.uniformBuffer))
        assertEquals(0, factory.closeAttempts(initial.handles.bindGroup))
        grown.rollbackBeforeSubmit()
        pool.close()
    }

    @Test
    fun `uniform growth recreates its bind group without replacing geometry`() {
        val factory = FakeFactory()
        val pool = GPUWgpu4kCorePrimitiveFramePool(GENERATION, factory)
        val initial = pool.acquire(requirements()).acquiredLease()
        initial.rollbackBeforeSubmit()

        val grown = pool.acquire(requirements(uniformBytes = 4L * 1024L + 1L)).acquiredLease()

        assertEquals(8L * 1024L, grown.capacities.uniformBytes)
        assertSame(initial.handles.vertexBuffer, grown.handles.vertexBuffer)
        assertSame(initial.handles.indexBuffer, grown.handles.indexBuffer)
        assertNotSame(initial.handles.uniformBuffer, grown.handles.uniformBuffer)
        assertNotSame(initial.handles.bindGroup, grown.handles.bindGroup)
        assertEquals(1, factory.closeAttempts(initial.handles.uniformBuffer))
        assertEquals(1, factory.closeAttempts(initial.handles.bindGroup))
        assertEquals(0, factory.closeAttempts(initial.handles.vertexBuffer))
        assertEquals(0, factory.closeAttempts(initial.handles.indexBuffer))
        grown.rollbackBeforeSubmit()
        pool.close()
    }

    @Test
    fun `failed uniform bind group growth rolls back new handles and preserves the old slot`() {
        val factory = FakeFactory()
        val pool = GPUWgpu4kCorePrimitiveFramePool(GENERATION, factory)
        val initial = pool.acquire(requirements()).acquiredLease()
        initial.rollbackBeforeSubmit()
        factory.failNext(GPUWgpu4kCorePrimitiveFramePoolResource.BindGroup)

        val refused = assertIs<GPUWgpu4kCorePrimitiveFramePoolCheckout.Refused>(
            pool.acquire(requirements(uniformBytes = 4L * 1024L + 1L)),
        )

        assertIs<GPUWgpu4kCorePrimitiveFramePoolRefusal.AllocationFailed>(refused.reason)
        assertEquals(
            GPUWgpu4kCorePrimitiveFramePoolResource.BindGroup,
            refused.reason.resource,
        )
        val failedUniform = factory.creations.last { it.resource == GPUWgpu4kCorePrimitiveFramePoolResource.UniformBuffer }
        assertEquals(1, factory.closeAttempts(failedUniform.handle))
        assertEquals(0, factory.closeAttempts(initial.handles.uniformBuffer))
        assertEquals(0, factory.closeAttempts(initial.handles.bindGroup))

        val reused = pool.acquire(requirements()).acquiredLease()
        assertSame(initial.handles.vertexBuffer, reused.handles.vertexBuffer)
        assertSame(initial.handles.indexBuffer, reused.handles.indexBuffer)
        assertSame(initial.handles.uniformBuffer, reused.handles.uniformBuffer)
        assertSame(initial.handles.bindGroup, reused.handles.bindGroup)
        reused.rollbackBeforeSubmit()
        pool.close()
    }

    @Test
    fun `close starts draining refuses live leases and then closes in dependency order`() {
        val factory = FakeFactory()
        val pool = GPUWgpu4kCorePrimitiveFramePool(GENERATION, factory)
        val live = pool.acquire(requirements()).acquiredLease()

        val refused = assertFailsWith<GPUWgpu4kCorePrimitiveFramePoolCloseRefused> { pool.close() }
        assertEquals(listOf(live.leaseId), refused.liveLeaseIds)
        assertEquals(
            GPUWgpu4kCorePrimitiveFramePoolRefusal.Closing,
            assertIs<GPUWgpu4kCorePrimitiveFramePoolCheckout.Refused>(pool.acquire(requirements())).reason,
        )
        assertIs<GPUWgpu4kCorePrimitiveFramePoolLeaseTransition.Applied>(live.rollbackBeforeSubmit())

        pool.close()
        assertEquals(
            listOf("close:bindGroup", "close:uniform", "close:index", "close:vertex"),
            factory.closeEvents,
        )
    }

    @Test
    fun `close retries only failed handles without double close`() {
        val factory = FakeFactory()
        val pool = GPUWgpu4kCorePrimitiveFramePool(GENERATION, factory)
        val lease = pool.acquire(requirements()).acquiredLease()
        lease.rollbackBeforeSubmit()
        factory.failCloseOnce(lease.handles.uniformBuffer)

        val failure = assertFailsWith<GPUWgpu4kCorePrimitiveFramePoolCloseFailure> { pool.close() }
        assertEquals(1, failure.retainedHandleCount)
        assertEquals(
            listOf("close:bindGroup", "close:uniform", "close:index", "close:vertex"),
            factory.closeEvents,
        )

        pool.close()
        assertEquals(
            listOf("close:bindGroup", "close:uniform", "close:index", "close:vertex", "close:uniform"),
            factory.closeEvents,
        )
        assertEquals(1, factory.closeAttempts(lease.handles.bindGroup))
        assertEquals(2, factory.closeAttempts(lease.handles.uniformBuffer))
        assertEquals(1, factory.closeAttempts(lease.handles.indexBuffer))
        assertEquals(1, factory.closeAttempts(lease.handles.vertexBuffer))
    }

    @Test
    fun `failed bind group close blocks its uniform while independent geometry still closes`() {
        val factory = FakeFactory()
        val pool = GPUWgpu4kCorePrimitiveFramePool(GENERATION, factory)
        val lease = pool.acquire(requirements()).acquiredLease()
        lease.rollbackBeforeSubmit()
        factory.failCloseOnce(lease.handles.bindGroup)

        val failure = assertFailsWith<GPUWgpu4kCorePrimitiveFramePoolCloseFailure> { pool.close() }

        assertEquals(2, failure.retainedHandleCount)
        assertEquals(
            listOf("close:bindGroup", "close:index", "close:vertex"),
            factory.closeEvents,
        )
        assertEquals(1, factory.closeAttempts(lease.handles.bindGroup))
        assertEquals(0, factory.closeAttempts(lease.handles.uniformBuffer))
        assertEquals(1, factory.closeAttempts(lease.handles.indexBuffer))
        assertEquals(1, factory.closeAttempts(lease.handles.vertexBuffer))

        pool.close()

        assertEquals(
            listOf("close:bindGroup", "close:index", "close:vertex", "close:bindGroup", "close:uniform"),
            factory.closeEvents,
        )
        assertEquals(2, factory.closeAttempts(lease.handles.bindGroup))
        assertEquals(1, factory.closeAttempts(lease.handles.uniformBuffer))
        assertEquals(1, factory.closeAttempts(lease.handles.indexBuffer))
        assertEquals(1, factory.closeAttempts(lease.handles.vertexBuffer))
    }

    @Test
    fun `uniform growth retirement preserves old bind group to uniform close barrier`() {
        val factory = FakeFactory()
        val pool = GPUWgpu4kCorePrimitiveFramePool(GENERATION, factory)
        val initial = pool.acquire(requirements()).acquiredLease()
        initial.rollbackBeforeSubmit()
        factory.failCloseOnce(initial.handles.bindGroup)

        val grown = pool.acquire(requirements(uniformBytes = 4L * 1024L + 1L)).acquiredLease()

        assertEquals(listOf("close:bindGroup"), factory.closeEvents)
        assertEquals(1, factory.closeAttempts(initial.handles.bindGroup))
        assertEquals(0, factory.closeAttempts(initial.handles.uniformBuffer))
        grown.rollbackBeforeSubmit()

        pool.close()

        assertEquals(2, factory.closeAttempts(initial.handles.bindGroup))
        assertEquals(1, factory.closeAttempts(initial.handles.uniformBuffer))
        assertEquals(1, factory.closeAttempts(grown.handles.bindGroup))
        assertEquals(1, factory.closeAttempts(grown.handles.uniformBuffer))
        assertEquals(1, factory.closeAttempts(grown.handles.indexBuffer))
        assertEquals(1, factory.closeAttempts(grown.handles.vertexBuffer))
    }

    private fun GPUWgpu4kCorePrimitiveFramePoolCheckout.acquiredLease() =
        assertIs<GPUWgpu4kCorePrimitiveFramePoolCheckout.Acquired>(this).lease

    private fun requirements(
        deviceGeneration: GPUDeviceGenerationID = GENERATION,
        vertexBytes: Long = 1L,
        indexBytes: Long = 1L,
        uniformBytes: Long = 1L,
    ) = GPUWgpu4kCorePrimitiveFramePoolRequirements(
        deviceGeneration,
        vertexBytes,
        indexBytes,
        uniformBytes,
    )

    private class FakeFactory : GPUWgpu4kCorePrimitiveFramePoolFactory {
        val creations = mutableListOf<CreatedHandle>()
        val closeEvents = mutableListOf<String>()
        private val closeCounts = IdentityHashMap<AutoCloseable, Int>()
        private val closeFailures = Collections.newSetFromMap(IdentityHashMap<AutoCloseable, Boolean>())
        private val consumedCloseFailures = Collections.newSetFromMap(IdentityHashMap<AutoCloseable, Boolean>())
        private var nextFailure: GPUWgpu4kCorePrimitiveFramePoolResource? = null

        override fun createVertexBuffer(capacityBytes: Long): GPUBuffer =
            create(GPUWgpu4kCorePrimitiveFramePoolResource.VertexBuffer, "vertex", GPUBuffer::class.java)

        override fun createIndexBuffer(capacityBytes: Long): GPUBuffer =
            create(GPUWgpu4kCorePrimitiveFramePoolResource.IndexBuffer, "index", GPUBuffer::class.java)

        override fun createUniformBuffer(capacityBytes: Long): GPUBuffer =
            create(GPUWgpu4kCorePrimitiveFramePoolResource.UniformBuffer, "uniform", GPUBuffer::class.java)

        override fun createBindGroup(uniformBuffer: GPUBuffer): GPUBindGroup =
            create(GPUWgpu4kCorePrimitiveFramePoolResource.BindGroup, "bindGroup", GPUBindGroup::class.java)

        fun failNext(resource: GPUWgpu4kCorePrimitiveFramePoolResource) {
            nextFailure = resource
        }

        fun failCloseOnce(handle: AutoCloseable) {
            closeFailures += handle
        }

        fun closeAttempts(handle: AutoCloseable): Int = closeCounts.getOrDefault(handle, 0)

        @Suppress("UNCHECKED_CAST")
        private fun <T : AutoCloseable> create(
            resource: GPUWgpu4kCorePrimitiveFramePoolResource,
            label: String,
            type: Class<T>,
        ): T {
            if (nextFailure == resource) {
                nextFailure = null
                error("injected $resource allocation failure")
            }
            val handle = Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { proxy, method, args ->
                when (method.name) {
                    "close" -> {
                        val closeable = proxy as AutoCloseable
                        closeCounts[closeable] = closeCounts.getOrDefault(closeable, 0) + 1
                        closeEvents += "close:$label"
                        if (closeable in closeFailures && consumedCloseFailures.add(closeable)) {
                            error("injected $label close failure")
                        }
                        null
                    }
                    "hashCode" -> System.identityHashCode(proxy)
                    "equals" -> proxy === args?.firstOrNull()
                    "toString" -> label
                    else -> defaultValue(method.returnType)
                }
            } as T
            creations += CreatedHandle(resource, handle)
            return handle
        }

        private fun defaultValue(type: Class<*>): Any? = when (type) {
            java.lang.Boolean.TYPE -> false
            java.lang.Byte.TYPE -> 0.toByte()
            java.lang.Short.TYPE -> 0.toShort()
            java.lang.Integer.TYPE -> 0
            java.lang.Long.TYPE -> 0L
            java.lang.Float.TYPE -> 0f
            java.lang.Double.TYPE -> 0.0
            java.lang.Character.TYPE -> 0.toChar()
            else -> null
        }
    }

    private data class CreatedHandle(
        val resource: GPUWgpu4kCorePrimitiveFramePoolResource,
        val handle: AutoCloseable,
    )

    private companion object {
        val GENERATION = GPUDeviceGenerationID(7)
    }
}
