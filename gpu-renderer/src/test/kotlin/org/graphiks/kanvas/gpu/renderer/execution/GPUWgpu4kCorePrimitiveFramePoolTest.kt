package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.GPUBindGroup
import io.ygdrasil.webgpu.GPUBuffer
import io.ygdrasil.webgpu.GPUTexture
import io.ygdrasil.webgpu.GPUTextureFormat
import io.ygdrasil.webgpu.GPUTextureUsage
import io.ygdrasil.webgpu.GPUTextureView
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
    fun `uniform layout transition replaces only the incompatible bind group transactionally`() {
        val factory = FakeFactory()
        val pool = GPUWgpu4kCorePrimitiveFramePool(GENERATION, factory)
        val legacy = pool.acquire(requirements()).acquiredLease()
        legacy.rollbackBeforeSubmit()

        val analytic = pool.acquire(
            requirements(componentIdentity = PRODUCTION_CORE_PRIMITIVE_ANALYTIC_CLIP_COMPONENT_IDENTITY),
        ).acquiredLease()

        assertEquals(legacy.slotId, analytic.slotId)
        assertSame(legacy.handles.vertexBuffer, analytic.handles.vertexBuffer)
        assertSame(legacy.handles.indexBuffer, analytic.handles.indexBuffer)
        assertSame(legacy.handles.uniformBuffer, analytic.handles.uniformBuffer)
        assertNotSame(legacy.handles.bindGroup, analytic.handles.bindGroup)
        assertEquals(
            PRODUCTION_CORE_PRIMITIVE_ANALYTIC_CLIP_COMPONENT_IDENTITY,
            analytic.handles.componentIdentity,
        )
        assertEquals(
            listOf(
                PRODUCTION_CORE_PRIMITIVE_COMPONENT_IDENTITY,
                PRODUCTION_CORE_PRIMITIVE_ANALYTIC_CLIP_COMPONENT_IDENTITY,
            ),
            factory.creations.filter {
                it.resource == GPUWgpu4kCorePrimitiveFramePoolResource.BindGroup
            }.map(CreatedHandle::componentIdentity),
        )
        assertEquals(1, factory.closeAttempts(legacy.handles.bindGroup))
        assertEquals(0, factory.closeAttempts(legacy.handles.uniformBuffer))

        analytic.rollbackBeforeSubmit()
        pool.close()
    }

    @Test
    fun `failed uniform layout transition keeps the published legacy slot untouched`() {
        val factory = FakeFactory()
        val pool = GPUWgpu4kCorePrimitiveFramePool(GENERATION, factory)
        val legacy = pool.acquire(requirements()).acquiredLease()
        legacy.rollbackBeforeSubmit()
        factory.failNext(GPUWgpu4kCorePrimitiveFramePoolResource.BindGroup)

        val refused = assertIs<GPUWgpu4kCorePrimitiveFramePoolCheckout.Refused>(
            pool.acquire(
                requirements(componentIdentity = PRODUCTION_CORE_PRIMITIVE_ANALYTIC_CLIP_COMPONENT_IDENTITY),
            ),
        )

        assertEquals(
            GPUWgpu4kCorePrimitiveFramePoolRefusal.AllocationFailed(
                GPUWgpu4kCorePrimitiveFramePoolResource.BindGroup,
                "IllegalStateException",
                "injected BindGroup allocation failure",
            ),
            refused.reason,
        )
        assertEquals(0, factory.closeAttempts(legacy.handles.bindGroup))
        assertEquals(0, factory.closeAttempts(legacy.handles.uniformBuffer))
        val reused = pool.acquire(requirements()).acquiredLease()
        assertSame(legacy.handles.vertexBuffer, reused.handles.vertexBuffer)
        assertSame(legacy.handles.indexBuffer, reused.handles.indexBuffer)
        assertSame(legacy.handles.uniformBuffer, reused.handles.uniformBuffer)
        assertSame(legacy.handles.bindGroup, reused.handles.bindGroup)

        reused.rollbackBeforeSubmit()
        pool.close()
    }

    @Test
    fun `path attachment keeps its exact non power of two extent and reuses the same texture and view`() {
        val factory = FakeFactory()
        val pool = GPUWgpu4kCorePrimitiveFramePool(GENERATION, factory)
        val attachment = pathDepthStencil(width = 63, height = 47)

        val first = pool.acquire(requirements(pathDepthStencil = attachment)).acquiredLease()
        val firstDepthStencil = requireNotNull(first.handles.pathDepthStencil)

        assertEquals(attachment, firstDepthStencil.requirement)
        assertEquals(
            listOf(
                GPUWgpu4kCorePrimitiveFramePoolResource.VertexBuffer,
                GPUWgpu4kCorePrimitiveFramePoolResource.IndexBuffer,
                GPUWgpu4kCorePrimitiveFramePoolResource.UniformBuffer,
                GPUWgpu4kCorePrimitiveFramePoolResource.BindGroup,
                GPUWgpu4kCorePrimitiveFramePoolResource.PathDepthStencilTexture,
                GPUWgpu4kCorePrimitiveFramePoolResource.PathDepthStencilView,
            ),
            factory.creations.map(CreatedHandle::resource),
        )
        assertEquals(
            listOf(attachment),
            factory.creations.mapNotNull(CreatedHandle::pathDepthStencilRequirement),
        )
        first.rollbackBeforeSubmit()

        val reused = pool.acquire(requirements(pathDepthStencil = attachment)).acquiredLease()
        val reusedDepthStencil = requireNotNull(reused.handles.pathDepthStencil)

        assertEquals(first.slotId, reused.slotId)
        assertSame(firstDepthStencil.texture, reusedDepthStencil.texture)
        assertSame(firstDepthStencil.view, reusedDepthStencil.view)
        assertSame(first.handles.vertexBuffer, reused.handles.vertexBuffer)
        assertSame(first.handles.indexBuffer, reused.handles.indexBuffer)
        assertSame(first.handles.uniformBuffer, reused.handles.uniformBuffer)
        assertSame(first.handles.bindGroup, reused.handles.bindGroup)
        assertEquals(6, factory.creations.size)

        reused.rollbackBeforeSubmit()
        pool.close()
    }

    @Test
    fun `direct slot adds only the path attachment while preserving every existing pooled handle`() {
        val factory = FakeFactory()
        val pool = GPUWgpu4kCorePrimitiveFramePool(GENERATION, factory)
        val direct = pool.acquire(requirements()).acquiredLease()
        direct.rollbackBeforeSubmit()
        val attachment = pathDepthStencil(width = 63, height = 47)

        val path = pool.acquire(requirements(pathDepthStencil = attachment)).acquiredLease()
        val pathDepthStencil = requireNotNull(path.handles.pathDepthStencil)

        assertEquals(direct.slotId, path.slotId)
        assertSame(direct.handles.vertexBuffer, path.handles.vertexBuffer)
        assertSame(direct.handles.indexBuffer, path.handles.indexBuffer)
        assertSame(direct.handles.uniformBuffer, path.handles.uniformBuffer)
        assertSame(direct.handles.bindGroup, path.handles.bindGroup)
        assertEquals(attachment, pathDepthStencil.requirement)
        assertEquals(
            listOf(
                GPUWgpu4kCorePrimitiveFramePoolResource.VertexBuffer,
                GPUWgpu4kCorePrimitiveFramePoolResource.IndexBuffer,
                GPUWgpu4kCorePrimitiveFramePoolResource.UniformBuffer,
                GPUWgpu4kCorePrimitiveFramePoolResource.BindGroup,
                GPUWgpu4kCorePrimitiveFramePoolResource.PathDepthStencilTexture,
                GPUWgpu4kCorePrimitiveFramePoolResource.PathDepthStencilView,
            ),
            factory.creations.map(CreatedHandle::resource),
        )
        assertTrue(factory.closeEvents.isEmpty())

        path.rollbackBeforeSubmit()
        pool.close()
    }

    @Test
    fun `path only growth never shrinks retained buffer capacities or recreates them later`() {
        val factory = FakeFactory()
        val pool = GPUWgpu4kCorePrimitiveFramePool(GENERATION, factory)
        val largeRequirements = requirements(
            vertexBytes = 16L * 1024L + 1L,
            indexBytes = 4L * 1024L + 1L,
            uniformBytes = 4L * 1024L + 1L,
        )
        val largeDirect = pool.acquire(largeRequirements).acquiredLease()
        assertEquals(
            GPUWgpu4kCorePrimitiveFramePoolCapacities(
                vertexBytes = 32L * 1024L,
                indexBytes = 8L * 1024L,
                uniformBytes = 8L * 1024L,
            ),
            largeDirect.capacities,
        )
        largeDirect.rollbackBeforeSubmit()

        val smallPath = pool.acquire(
            requirements(pathDepthStencil = pathDepthStencil(width = 63, height = 47)),
        ).acquiredLease()

        assertEquals(largeDirect.slotId, smallPath.slotId)
        assertEquals(largeDirect.capacities, smallPath.capacities)
        assertSame(largeDirect.handles.vertexBuffer, smallPath.handles.vertexBuffer)
        assertSame(largeDirect.handles.indexBuffer, smallPath.handles.indexBuffer)
        assertSame(largeDirect.handles.uniformBuffer, smallPath.handles.uniformBuffer)
        assertSame(largeDirect.handles.bindGroup, smallPath.handles.bindGroup)
        requireNotNull(smallPath.handles.pathDepthStencil)
        smallPath.rollbackBeforeSubmit()

        val largeDirectAgain = pool.acquire(largeRequirements).acquiredLease()

        assertEquals(largeDirect.slotId, largeDirectAgain.slotId)
        assertEquals(largeDirect.capacities, largeDirectAgain.capacities)
        assertSame(largeDirect.handles.vertexBuffer, largeDirectAgain.handles.vertexBuffer)
        assertSame(largeDirect.handles.indexBuffer, largeDirectAgain.handles.indexBuffer)
        assertSame(largeDirect.handles.uniformBuffer, largeDirectAgain.handles.uniformBuffer)
        assertSame(largeDirect.handles.bindGroup, largeDirectAgain.handles.bindGroup)
        assertEquals(
            listOf(
                GPUWgpu4kCorePrimitiveFramePoolResource.VertexBuffer,
                GPUWgpu4kCorePrimitiveFramePoolResource.IndexBuffer,
                GPUWgpu4kCorePrimitiveFramePoolResource.UniformBuffer,
                GPUWgpu4kCorePrimitiveFramePoolResource.BindGroup,
                GPUWgpu4kCorePrimitiveFramePoolResource.PathDepthStencilTexture,
                GPUWgpu4kCorePrimitiveFramePoolResource.PathDepthStencilView,
            ),
            factory.creations.map(CreatedHandle::resource),
        )

        largeDirectAgain.rollbackBeforeSubmit()
        pool.close()
    }

    @Test
    fun `path attachment resize publishes the replacement only after both new handles exist`() {
        val factory = FakeFactory()
        val pool = GPUWgpu4kCorePrimitiveFramePool(GENERATION, factory)
        val initialRequirement = pathDepthStencil(width = 63, height = 47)
        val replacementRequirement = pathDepthStencil(width = 65, height = 49)
        val initial = pool.acquire(requirements(pathDepthStencil = initialRequirement)).acquiredLease()
        val initialDepthStencil = requireNotNull(initial.handles.pathDepthStencil)
        initial.rollbackBeforeSubmit()

        val replacement = pool.acquire(
            requirements(pathDepthStencil = replacementRequirement),
        ).acquiredLease()
        val replacementDepthStencil = requireNotNull(replacement.handles.pathDepthStencil)

        assertEquals(initial.slotId, replacement.slotId)
        assertEquals(replacementRequirement, replacementDepthStencil.requirement)
        assertNotSame(initialDepthStencil.texture, replacementDepthStencil.texture)
        assertNotSame(initialDepthStencil.view, replacementDepthStencil.view)
        assertSame(initial.handles.vertexBuffer, replacement.handles.vertexBuffer)
        assertSame(initial.handles.indexBuffer, replacement.handles.indexBuffer)
        assertSame(initial.handles.uniformBuffer, replacement.handles.uniformBuffer)
        assertSame(initial.handles.bindGroup, replacement.handles.bindGroup)
        assertEquals(
            listOf(initialRequirement, replacementRequirement),
            factory.creations.mapNotNull(CreatedHandle::pathDepthStencilRequirement),
        )
        assertEquals(
            listOf("close:pathDepthStencilView", "close:pathDepthStencilTexture"),
            factory.closeEvents,
        )
        assertEquals(1, factory.closeAttempts(initialDepthStencil.view))
        assertEquals(1, factory.closeAttempts(initialDepthStencil.texture))

        replacement.rollbackBeforeSubmit()
        pool.close()
    }

    @Test
    fun `path attachment texture allocation failure leaves the old slot published and untouched`() {
        val factory = FakeFactory()
        val pool = GPUWgpu4kCorePrimitiveFramePool(GENERATION, factory)
        val initialRequirement = pathDepthStencil(width = 63, height = 47)
        val initial = pool.acquire(requirements(pathDepthStencil = initialRequirement)).acquiredLease()
        val initialDepthStencil = requireNotNull(initial.handles.pathDepthStencil)
        initial.rollbackBeforeSubmit()
        factory.failNext(GPUWgpu4kCorePrimitiveFramePoolResource.PathDepthStencilTexture)

        val refused = assertIs<GPUWgpu4kCorePrimitiveFramePoolCheckout.Refused>(
            pool.acquire(requirements(pathDepthStencil = pathDepthStencil(width = 65, height = 49))),
        )

        assertEquals(
            GPUWgpu4kCorePrimitiveFramePoolResource.PathDepthStencilTexture,
            assertIs<GPUWgpu4kCorePrimitiveFramePoolRefusal.AllocationFailed>(refused.reason).resource,
        )
        assertEquals(0, factory.closeAttempts(initialDepthStencil.view))
        assertEquals(0, factory.closeAttempts(initialDepthStencil.texture))
        val reused = pool.acquire(requirements(pathDepthStencil = initialRequirement)).acquiredLease()
        assertSame(initialDepthStencil.texture, requireNotNull(reused.handles.pathDepthStencil).texture)
        assertSame(initialDepthStencil.view, requireNotNull(reused.handles.pathDepthStencil).view)

        reused.rollbackBeforeSubmit()
        pool.close()
    }

    @Test
    fun `path attachment view allocation failure closes only the unpublished new texture`() {
        val factory = FakeFactory()
        val pool = GPUWgpu4kCorePrimitiveFramePool(GENERATION, factory)
        val initialRequirement = pathDepthStencil(width = 63, height = 47)
        val initial = pool.acquire(requirements(pathDepthStencil = initialRequirement)).acquiredLease()
        val initialDepthStencil = requireNotNull(initial.handles.pathDepthStencil)
        initial.rollbackBeforeSubmit()
        factory.failNext(GPUWgpu4kCorePrimitiveFramePoolResource.PathDepthStencilView)

        val refused = assertIs<GPUWgpu4kCorePrimitiveFramePoolCheckout.Refused>(
            pool.acquire(requirements(pathDepthStencil = pathDepthStencil(width = 65, height = 49))),
        )

        assertEquals(
            GPUWgpu4kCorePrimitiveFramePoolResource.PathDepthStencilView,
            assertIs<GPUWgpu4kCorePrimitiveFramePoolRefusal.AllocationFailed>(refused.reason).resource,
        )
        val unpublishedTexture = factory.creations.last {
            it.resource == GPUWgpu4kCorePrimitiveFramePoolResource.PathDepthStencilTexture
        }.handle
        assertEquals(1, factory.closeAttempts(unpublishedTexture))
        assertEquals(0, factory.closeAttempts(initialDepthStencil.view))
        assertEquals(0, factory.closeAttempts(initialDepthStencil.texture))
        val reused = pool.acquire(requirements(pathDepthStencil = initialRequirement)).acquiredLease()
        assertSame(initialDepthStencil.texture, requireNotNull(reused.handles.pathDepthStencil).texture)
        assertSame(initialDepthStencil.view, requireNotNull(reused.handles.pathDepthStencil).view)

        reused.rollbackBeforeSubmit()
        pool.close()
    }

    @Test
    fun `three in flight path frames own distinct attachments and the fourth refuses`() {
        val factory = FakeFactory()
        val pool = GPUWgpu4kCorePrimitiveFramePool(GENERATION, factory)
        val request = requirements(pathDepthStencil = pathDepthStencil(width = 63, height = 47))

        val leases = List(3) { pool.acquire(request).acquiredLease() }

        assertEquals(listOf(0, 1, 2), leases.map { it.slotId })
        assertEquals(3, leases.map { requireNotNull(it.handles.pathDepthStencil).texture }.distinct().size)
        assertEquals(3, leases.map { requireNotNull(it.handles.pathDepthStencil).view }.distinct().size)
        assertEquals(
            GPUWgpu4kCorePrimitiveFramePoolRefusal.Saturated(maxSlots = 3),
            assertIs<GPUWgpu4kCorePrimitiveFramePoolCheckout.Refused>(pool.acquire(request)).reason,
        )

        leases.forEach { it.rollbackBeforeSubmit() }
        pool.close()
    }

    @Test
    fun `failed path view close blocks its texture while independent slot handles close and retry once`() {
        val factory = FakeFactory()
        val pool = GPUWgpu4kCorePrimitiveFramePool(GENERATION, factory)
        val lease = pool.acquire(
            requirements(pathDepthStencil = pathDepthStencil(width = 63, height = 47)),
        ).acquiredLease()
        val depthStencil = requireNotNull(lease.handles.pathDepthStencil)
        lease.rollbackBeforeSubmit()
        factory.failCloseOnce(depthStencil.view)

        val failure = assertFailsWith<GPUWgpu4kCorePrimitiveFramePoolCloseFailure> { pool.close() }

        assertEquals(2, failure.retainedHandleCount)
        assertEquals(
            listOf(
                "close:pathDepthStencilView",
                "close:bindGroup",
                "close:uniform",
                "close:index",
                "close:vertex",
            ),
            factory.closeEvents,
        )
        assertEquals(1, factory.closeAttempts(depthStencil.view))
        assertEquals(0, factory.closeAttempts(depthStencil.texture))

        pool.close()

        assertEquals(
            listOf(
                "close:pathDepthStencilView",
                "close:bindGroup",
                "close:uniform",
                "close:index",
                "close:vertex",
                "close:pathDepthStencilView",
                "close:pathDepthStencilTexture",
            ),
            factory.closeEvents,
        )
        assertEquals(2, factory.closeAttempts(depthStencil.view))
        assertEquals(1, factory.closeAttempts(depthStencil.texture))
        assertEquals(1, factory.closeAttempts(lease.handles.bindGroup))
        assertEquals(1, factory.closeAttempts(lease.handles.uniformBuffer))
        assertEquals(1, factory.closeAttempts(lease.handles.indexBuffer))
        assertEquals(1, factory.closeAttempts(lease.handles.vertexBuffer))
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
        listOf(
            GPUWgpu4kCorePrimitiveFramePoolResource.VertexBuffer,
            GPUWgpu4kCorePrimitiveFramePoolResource.IndexBuffer,
            GPUWgpu4kCorePrimitiveFramePoolResource.UniformBuffer,
        ).forEach { invalidResource ->
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
        pathDepthStencil: GPUWgpu4kCorePrimitivePathDepthStencilRequirement? = null,
        componentIdentity: GPUWgpu4kCorePrimitiveComponentIdentity =
            PRODUCTION_CORE_PRIMITIVE_COMPONENT_IDENTITY,
    ) = GPUWgpu4kCorePrimitiveFramePoolRequirements(
        deviceGeneration,
        vertexBytes,
        indexBytes,
        uniformBytes,
        pathDepthStencil,
        componentIdentity,
    )

    private fun pathDepthStencil(
        width: Int,
        height: Int,
    ) = GPUWgpu4kCorePrimitivePathDepthStencilRequirement(
        width = width,
        height = height,
        format = GPUTextureFormat.Depth24PlusStencil8,
        sampleCount = 1,
        usage = GPUTextureUsage.RenderAttachment,
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

        override fun createBindGroup(
            componentIdentity: GPUWgpu4kCorePrimitiveComponentIdentity,
            uniformBuffer: GPUBuffer,
        ): GPUBindGroup = create(
            GPUWgpu4kCorePrimitiveFramePoolResource.BindGroup,
            "bindGroup",
            GPUBindGroup::class.java,
            componentIdentity = componentIdentity,
        )

        override fun createPathDepthStencilTexture(
            requirement: GPUWgpu4kCorePrimitivePathDepthStencilRequirement,
        ): GPUTexture = create(
            GPUWgpu4kCorePrimitiveFramePoolResource.PathDepthStencilTexture,
            "pathDepthStencilTexture",
            GPUTexture::class.java,
            requirement,
        )

        override fun createPathDepthStencilView(texture: GPUTexture): GPUTextureView = create(
            GPUWgpu4kCorePrimitiveFramePoolResource.PathDepthStencilView,
            "pathDepthStencilView",
            GPUTextureView::class.java,
        )

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
            pathDepthStencilRequirement: GPUWgpu4kCorePrimitivePathDepthStencilRequirement? = null,
            componentIdentity: GPUWgpu4kCorePrimitiveComponentIdentity? = null,
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
            creations += CreatedHandle(resource, handle, pathDepthStencilRequirement, componentIdentity)
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
        val pathDepthStencilRequirement: GPUWgpu4kCorePrimitivePathDepthStencilRequirement? = null,
        val componentIdentity: GPUWgpu4kCorePrimitiveComponentIdentity? = null,
    )

    private companion object {
        val GENERATION = GPUDeviceGenerationID(7)
    }
}
