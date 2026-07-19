package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.GPUBindGroup
import io.ygdrasil.webgpu.GPUBuffer
import io.ygdrasil.webgpu.GPUTexture
import io.ygdrasil.webgpu.GPUTextureFormat
import io.ygdrasil.webgpu.GPUTextureUsage
import io.ygdrasil.webgpu.GPUTextureView
import java.lang.reflect.Proxy
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
    fun `all uniform32 uniform64 uniform160 transitions replace only the incompatible bind group`() {
        val identities = listOf(
            PRODUCTION_CORE_PRIMITIVE_COMPONENT_IDENTITY,
            PRODUCTION_CORE_PRIMITIVE_ANALYTIC_CLIP_COMPONENT_IDENTITY,
            PRODUCTION_CORE_PRIMITIVE_ANALYTIC_INTERSECTION4_COMPONENT_IDENTITY,
        )
        val transitions = identities.flatMap { from ->
            identities.filter { it != from }.map { to -> from to to }
        }

        transitions.forEach { (from, to) ->
            val factory = FakeFactory()
            val pool = GPUWgpu4kCorePrimitiveFramePool(GENERATION, factory)
            val initial = pool.acquire(requirements(componentIdentity = from)).acquiredLease()
            initial.rollbackBeforeSubmit()

            val transitioned = pool.acquire(requirements(componentIdentity = to)).acquiredLease()

            assertEquals(initial.slotId, transitioned.slotId, "$from -> $to")
            assertSame(initial.handles.vertexBuffer, transitioned.handles.vertexBuffer, "$from -> $to")
            assertSame(initial.handles.indexBuffer, transitioned.handles.indexBuffer, "$from -> $to")
            assertSame(initial.handles.uniformBuffer, transitioned.handles.uniformBuffer, "$from -> $to")
            assertNotSame(initial.handles.bindGroup, transitioned.handles.bindGroup, "$from -> $to")
            assertEquals(to, transitioned.handles.componentIdentity, "$from -> $to")
            assertEquals(1, factory.closeAttempts(initial.handles.bindGroup), "$from -> $to")
            assertEquals(0, factory.closeAttempts(initial.handles.uniformBuffer), "$from -> $to")

            transitioned.rollbackBeforeSubmit()
            pool.close()
        }
    }

    @Test
    fun `failed transition to uniform160 rolls back and a retry replaces only the bind group`() {
        val factory = FakeFactory()
        val pool = GPUWgpu4kCorePrimitiveFramePool(GENERATION, factory)
        val uniform64 = pool.acquire(
            requirements(componentIdentity = PRODUCTION_CORE_PRIMITIVE_ANALYTIC_CLIP_COMPONENT_IDENTITY),
        ).acquiredLease()
        uniform64.rollbackBeforeSubmit()
        factory.failNext(GPUWgpu4kCorePrimitiveFramePoolResource.BindGroup)

        val refused = assertIs<GPUWgpu4kCorePrimitiveFramePoolCheckout.Refused>(
            pool.acquire(
                requirements(
                    componentIdentity = PRODUCTION_CORE_PRIMITIVE_ANALYTIC_INTERSECTION4_COMPONENT_IDENTITY,
                ),
            ),
        )

        assertIs<GPUWgpu4kCorePrimitiveFramePoolRefusal.AllocationFailed>(refused.reason)
        assertEquals(0, factory.closeAttempts(uniform64.handles.bindGroup))
        assertEquals(0, factory.closeAttempts(uniform64.handles.uniformBuffer))

        val retried = pool.acquire(
            requirements(
                componentIdentity = PRODUCTION_CORE_PRIMITIVE_ANALYTIC_INTERSECTION4_COMPONENT_IDENTITY,
            ),
        ).acquiredLease()
        assertSame(uniform64.handles.vertexBuffer, retried.handles.vertexBuffer)
        assertSame(uniform64.handles.indexBuffer, retried.handles.indexBuffer)
        assertSame(uniform64.handles.uniformBuffer, retried.handles.uniformBuffer)
        assertNotSame(uniform64.handles.bindGroup, retried.handles.bindGroup)
        assertEquals(1, factory.closeAttempts(uniform64.handles.bindGroup))
        assertEquals(0, factory.closeAttempts(uniform64.handles.uniformBuffer))

        retried.rollbackBeforeSubmit()
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
    fun `clip attachment is exact reusable and strictly separate from path attachment`() {
        val factory = FakeFactory()
        val pool = GPUWgpu4kCorePrimitiveFramePool(GENERATION, factory)
        val path = pathDepthStencil(width = 63, height = 47)
        val clip = clipDepthStencil(width = 63, height = 47)

        val first = pool.acquire(
            requirements(pathDepthStencil = path, clipDepthStencil = clip),
        ).acquiredLease()
        val firstPath = requireNotNull(first.handles.pathDepthStencil)
        val firstClip = requireNotNull(first.handles.clipDepthStencil)

        assertEquals(path, firstPath.requirement)
        assertEquals(clip, firstClip.requirement)
        assertNotSame(firstPath.texture, firstClip.texture)
        assertNotSame(firstPath.view, firstClip.view)
        assertEquals(
            listOf(
                GPUWgpu4kCorePrimitiveFramePoolResource.PathDepthStencilTexture,
                GPUWgpu4kCorePrimitiveFramePoolResource.PathDepthStencilView,
                GPUWgpu4kCorePrimitiveFramePoolResource.ClipDepthStencilTexture,
                GPUWgpu4kCorePrimitiveFramePoolResource.ClipDepthStencilView,
            ),
            factory.creations.map(CreatedHandle::resource).takeLast(4),
        )
        first.rollbackBeforeSubmit()

        val reused = pool.acquire(
            requirements(pathDepthStencil = path, clipDepthStencil = clip),
        ).acquiredLease()
        assertSame(firstPath.texture, requireNotNull(reused.handles.pathDepthStencil).texture)
        assertSame(firstPath.view, requireNotNull(reused.handles.pathDepthStencil).view)
        assertSame(firstClip.texture, requireNotNull(reused.handles.clipDepthStencil).texture)
        assertSame(firstClip.view, requireNotNull(reused.handles.clipDepthStencil).view)

        reused.rollbackBeforeSubmit()
        pool.close()
    }

    @Test
    fun `clip attachment requirement is exact D24S8 target sized single sample render attachment`() {
        assertFailsWith<IllegalArgumentException> {
            GPUWgpu4kCorePrimitiveClipDepthStencilRequirement(
                0,
                47,
                GPUTextureFormat.Depth24PlusStencil8,
                1,
                GPUTextureUsage.RenderAttachment,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            GPUWgpu4kCorePrimitiveClipDepthStencilRequirement(
                63,
                47,
                GPUTextureFormat.RGBA8Unorm,
                1,
                GPUTextureUsage.RenderAttachment,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            GPUWgpu4kCorePrimitiveClipDepthStencilRequirement(
                63,
                47,
                GPUTextureFormat.Depth24PlusStencil8,
                4,
                GPUTextureUsage.RenderAttachment,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            GPUWgpu4kCorePrimitiveClipDepthStencilRequirement(
                63,
                47,
                GPUTextureFormat.Depth24PlusStencil8,
                1,
                GPUTextureUsage.TextureBinding,
            )
        }
    }

    @Test
    fun `clip resize is transactional and view failure preserves the published attachment`() {
        val factory = FakeFactory()
        val pool = GPUWgpu4kCorePrimitiveFramePool(GENERATION, factory)
        val initialRequirement = clipDepthStencil(width = 63, height = 47)
        val replacementRequirement = clipDepthStencil(width = 65, height = 49)
        val initial = pool.acquire(
            requirements(clipDepthStencil = initialRequirement),
        ).acquiredLease()
        val initialClip = requireNotNull(initial.handles.clipDepthStencil)
        initial.rollbackBeforeSubmit()
        factory.failNext(GPUWgpu4kCorePrimitiveFramePoolResource.ClipDepthStencilView)

        val refused = assertIs<GPUWgpu4kCorePrimitiveFramePoolCheckout.Refused>(
            pool.acquire(requirements(clipDepthStencil = replacementRequirement)),
        )

        assertEquals(
            GPUWgpu4kCorePrimitiveFramePoolResource.ClipDepthStencilView,
            assertIs<GPUWgpu4kCorePrimitiveFramePoolRefusal.AllocationFailed>(refused.reason).resource,
        )
        val unpublishedTexture = factory.creations.last {
            it.resource == GPUWgpu4kCorePrimitiveFramePoolResource.ClipDepthStencilTexture
        }.handle
        assertEquals(1, factory.closeAttempts(unpublishedTexture))
        assertEquals(0, factory.closeAttempts(initialClip.view))
        assertEquals(0, factory.closeAttempts(initialClip.texture))

        val replacement = pool.acquire(
            requirements(clipDepthStencil = replacementRequirement),
        ).acquiredLease()
        val replacementClip = requireNotNull(replacement.handles.clipDepthStencil)
        assertEquals(initial.slotId, replacement.slotId)
        assertEquals(replacementRequirement, replacementClip.requirement)
        assertNotSame(initialClip.texture, replacementClip.texture)
        assertNotSame(initialClip.view, replacementClip.view)
        assertSame(initial.handles.vertexBuffer, replacement.handles.vertexBuffer)
        assertSame(initial.handles.indexBuffer, replacement.handles.indexBuffer)
        assertSame(initial.handles.uniformBuffer, replacement.handles.uniformBuffer)
        assertSame(initial.handles.bindGroup, replacement.handles.bindGroup)
        assertEquals(1, factory.closeAttempts(initialClip.view))
        assertEquals(1, factory.closeAttempts(initialClip.texture))

        replacement.rollbackBeforeSubmit()
        pool.close()
    }

    @Test
    fun `clip leases honor generation saturation completion and uncertain quarantine`() {
        val factory = FakeFactory()
        val pool = GPUWgpu4kCorePrimitiveFramePool(GENERATION, factory)
        val clip = clipDepthStencil(width = 63, height = 47)
        val mismatch = pool.acquire(
            requirements(
                deviceGeneration = GPUDeviceGenerationID(8),
                clipDepthStencil = clip,
            ),
        )
        assertEquals(
            GPUWgpu4kCorePrimitiveFramePoolRefusal.DeviceGenerationMismatch(
                GENERATION,
                GPUDeviceGenerationID(8),
            ),
            assertIs<GPUWgpu4kCorePrimitiveFramePoolCheckout.Refused>(mismatch).reason,
        )
        assertTrue(factory.creations.isEmpty())

        val leases = List(3) {
            pool.acquire(requirements(clipDepthStencil = clip)).acquiredLease()
        }
        assertEquals(
            3,
            leases.map { requireNotNull(it.handles.clipDepthStencil).texture }.distinct().size,
        )
        assertEquals(
            GPUWgpu4kCorePrimitiveFramePoolRefusal.Saturated(3),
            assertIs<GPUWgpu4kCorePrimitiveFramePoolCheckout.Refused>(
                pool.acquire(requirements(clipDepthStencil = clip)),
            ).reason,
        )

        leases[0].markSubmitted()
        leases[1].quarantineUncertain()
        leases[2].rollbackBeforeSubmit()
        val rolledBack = pool.acquire(requirements(clipDepthStencil = clip)).acquiredLease()
        assertEquals(leases[2].slotId, rolledBack.slotId)
        rolledBack.rollbackBeforeSubmit()
        leases[0].completeSuccessfully()
        val completed = pool.acquire(requirements(clipDepthStencil = clip)).acquiredLease()
        assertEquals(leases[0].slotId, completed.slotId)
        assertSame(
            requireNotNull(leases[0].handles.clipDepthStencil).texture,
            requireNotNull(completed.handles.clipDepthStencil).texture,
        )
        completed.rollbackBeforeSubmit()
        pool.close()
    }

    @Test
    fun `failed clip view close blocks texture and retries without double closing independent handles`() {
        val factory = FakeFactory()
        val pool = GPUWgpu4kCorePrimitiveFramePool(GENERATION, factory)
        val lease = pool.acquire(
            requirements(clipDepthStencil = clipDepthStencil(width = 63, height = 47)),
        ).acquiredLease()
        val clip = requireNotNull(lease.handles.clipDepthStencil)
        lease.rollbackBeforeSubmit()
        factory.failCloseOnce(clip.view)

        val failure = assertFailsWith<GPUWgpu4kCorePrimitiveFramePoolCloseFailure> { pool.close() }

        assertEquals(2, failure.retainedHandleCount)
        assertEquals(1, factory.closeAttempts(clip.view))
        assertEquals(0, factory.closeAttempts(clip.texture))
        assertEquals(1, factory.closeAttempts(lease.handles.bindGroup))
        assertEquals(1, factory.closeAttempts(lease.handles.uniformBuffer))
        assertEquals(1, factory.closeAttempts(lease.handles.indexBuffer))
        assertEquals(1, factory.closeAttempts(lease.handles.vertexBuffer))

        pool.close()

        assertEquals(2, factory.closeAttempts(clip.view))
        assertEquals(1, factory.closeAttempts(clip.texture))
        assertEquals(1, factory.closeAttempts(lease.handles.bindGroup))
        assertEquals(1, factory.closeAttempts(lease.handles.uniformBuffer))
        assertEquals(1, factory.closeAttempts(lease.handles.indexBuffer))
        assertEquals(1, factory.closeAttempts(lease.handles.vertexBuffer))
        assertTrue(
            factory.closeEvents.indexOf("close:clipDepthStencilView") <
                factory.closeEvents.indexOf("close:clipDepthStencilTexture"),
        )
    }

    @Test
    fun `coverage mask requirement is exact rgba8 single sample render attachment and texture binding`() {
        val exactUsage = GPUTextureUsage.RenderAttachment or GPUTextureUsage.TextureBinding

        val exact = coverageMask(width = 63, height = 47)

        assertEquals(63, exact.width)
        assertEquals(47, exact.height)
        assertEquals(GPUTextureFormat.RGBA8Unorm, exact.format)
        assertEquals(1, exact.sampleCount)
        assertEquals(exactUsage, exact.usage)
        listOf(
            { exact.copy(width = 0) },
            { exact.copy(height = 0) },
            { exact.copy(format = GPUTextureFormat.BGRA8Unorm) },
            { exact.copy(sampleCount = 4) },
            { exact.copy(usage = GPUTextureUsage.RenderAttachment) },
            { exact.copy(usage = GPUTextureUsage.TextureBinding) },
            { exact.copy(usage = exactUsage or GPUTextureUsage.CopySrc) },
        ).forEach { invalid -> assertFailsWith<IllegalArgumentException> { invalid() } }
    }

    @Test
    fun `coverage mask requirements refuse path and clip depth stencil before native creation`() {
        listOf<() -> GPUWgpu4kCorePrimitiveFramePoolRequirements>(
            {
                requirements(
                    coverageMask = coverageMask(32, 32),
                    pathDepthStencil = pathDepthStencil(32, 32),
                    componentIdentity =
                        PRODUCTION_CORE_PRIMITIVE_COVERAGE_MASK_PRODUCER_COMPONENT_IDENTITY,
                )
            },
            {
                requirements(
                    coverageMask = coverageMask(32, 32),
                    clipDepthStencil = clipDepthStencil(32, 32),
                    componentIdentity =
                        PRODUCTION_CORE_PRIMITIVE_COVERAGE_MASK_PRODUCER_COMPONENT_IDENTITY,
                )
            },
        ).forEach { invalidRequirements ->
            val factory = FakeFactory()
            val pool = GPUWgpu4kCorePrimitiveFramePool(GENERATION, factory)

            assertFailsWith<IllegalArgumentException> { pool.acquire(invalidRequirements()) }

            assertTrue(factory.creations.isEmpty())
            pool.close()
        }
    }

    @Test
    fun `coverage mask initial creation failures retire every unpublished handle and retry slot zero`() {
        val allocationOrder = listOf(
            GPUWgpu4kCorePrimitiveFramePoolResource.VertexBuffer,
            GPUWgpu4kCorePrimitiveFramePoolResource.IndexBuffer,
            GPUWgpu4kCorePrimitiveFramePoolResource.UniformBuffer,
            GPUWgpu4kCorePrimitiveFramePoolResource.BindGroup,
            GPUWgpu4kCorePrimitiveFramePoolResource.CoverageMaskTexture,
            GPUWgpu4kCorePrimitiveFramePoolResource.CoverageMaskView,
            GPUWgpu4kCorePrimitiveFramePoolResource.CoverageMaskConsumerBindGroup,
        )
        val failurePoints = listOf(
            GPUWgpu4kCorePrimitiveFramePoolResource.VertexBuffer,
            GPUWgpu4kCorePrimitiveFramePoolResource.IndexBuffer,
            GPUWgpu4kCorePrimitiveFramePoolResource.UniformBuffer,
            GPUWgpu4kCorePrimitiveFramePoolResource.BindGroup,
            GPUWgpu4kCorePrimitiveFramePoolResource.CoverageMaskTexture,
            GPUWgpu4kCorePrimitiveFramePoolResource.CoverageMaskView,
            GPUWgpu4kCorePrimitiveFramePoolResource.CoverageMaskConsumerBindGroup,
        )
        failurePoints.forEach { failingResource ->
            val factory = FakeFactory()
            val pool = GPUWgpu4kCorePrimitiveFramePool(GENERATION, factory)
            val request = maskRequirements(coverageMask(63, 47))
            factory.failNext(failingResource)

            val refused = assertIs<GPUWgpu4kCorePrimitiveFramePoolCheckout.Refused>(
                pool.acquire(request),
            )

            assertEquals(
                failingResource,
                assertIs<GPUWgpu4kCorePrimitiveFramePoolRefusal.AllocationFailed>(
                    refused.reason,
                ).resource,
            )
            val unpublished = factory.creations.toList()
            assertEquals(
                allocationOrder.takeWhile { resource -> resource != failingResource },
                unpublished.map(CreatedHandle::resource),
                failingResource.name,
            )
            unpublished.forEach { created ->
                assertEquals(1, factory.closeAttempts(created.handle), failingResource.name)
            }

            val retried = pool.acquire(request).acquiredLease()
            assertEquals(0, retried.slotId, failingResource.name)
            retried.rollbackBeforeSubmit()
            pool.close()
            unpublished.forEach { created ->
                assertEquals(1, factory.closeAttempts(created.handle), failingResource.name)
            }
        }
    }

    @Test
    fun `coverage mask creates producer and consumer bindings once and reuses the exact slot`() {
        val factory = FakeFactory()
        val pool = GPUWgpu4kCorePrimitiveFramePool(GENERATION, factory)
        val mask = coverageMask(width = 63, height = 47)
        val request = requirements(
            componentIdentity = PRODUCTION_CORE_PRIMITIVE_COVERAGE_MASK_PRODUCER_COMPONENT_IDENTITY,
            coverageMask = mask,
        )

        val first = pool.acquire(request).acquiredLease()
        val firstMask = requireNotNull(first.handles.coverageMask)

        assertEquals(mask, firstMask.requirement)
        assertEquals(PRODUCTION_CORE_PRIMITIVE_COVERAGE_MASK_PRODUCER_COMPONENT_IDENTITY,
            first.handles.componentIdentity)
        assertEquals(
            listOf(
                GPUWgpu4kCorePrimitiveFramePoolResource.VertexBuffer,
                GPUWgpu4kCorePrimitiveFramePoolResource.IndexBuffer,
                GPUWgpu4kCorePrimitiveFramePoolResource.UniformBuffer,
                GPUWgpu4kCorePrimitiveFramePoolResource.BindGroup,
                GPUWgpu4kCorePrimitiveFramePoolResource.CoverageMaskTexture,
                GPUWgpu4kCorePrimitiveFramePoolResource.CoverageMaskView,
                GPUWgpu4kCorePrimitiveFramePoolResource.CoverageMaskConsumerBindGroup,
            ),
            factory.creations.map(CreatedHandle::resource),
        )
        first.rollbackBeforeSubmit()

        val reused = pool.acquire(request).acquiredLease()
        val reusedMask = requireNotNull(reused.handles.coverageMask)
        assertEquals(first.slotId, reused.slotId)
        assertSame(first.handles.uniformBuffer, reused.handles.uniformBuffer)
        assertSame(first.handles.bindGroup, reused.handles.bindGroup)
        assertSame(firstMask.texture, reusedMask.texture)
        assertSame(firstMask.view, reusedMask.view)
        assertSame(firstMask.consumerBindGroup, reusedMask.consumerBindGroup)
        assertEquals(7, factory.creations.size)

        reused.rollbackBeforeSubmit()
        pool.close()
    }

    @Test
    fun `coverage mask resize replaces only texture view and consumer bind group transactionally`() {
        val factory = FakeFactory()
        val pool = GPUWgpu4kCorePrimitiveFramePool(GENERATION, factory)
        val initial = pool.acquire(maskRequirements(coverageMask(63, 47))).acquiredLease()
        val initialMask = requireNotNull(initial.handles.coverageMask)
        initial.rollbackBeforeSubmit()

        val resized = pool.acquire(maskRequirements(coverageMask(65, 49))).acquiredLease()
        val resizedMask = requireNotNull(resized.handles.coverageMask)

        assertSame(initial.handles.vertexBuffer, resized.handles.vertexBuffer)
        assertSame(initial.handles.indexBuffer, resized.handles.indexBuffer)
        assertSame(initial.handles.uniformBuffer, resized.handles.uniformBuffer)
        assertSame(initial.handles.bindGroup, resized.handles.bindGroup)
        assertNotSame(initialMask.texture, resizedMask.texture)
        assertNotSame(initialMask.view, resizedMask.view)
        assertNotSame(initialMask.consumerBindGroup, resizedMask.consumerBindGroup)
        assertEquals(1, factory.closeAttempts(initialMask.consumerBindGroup))
        assertEquals(1, factory.closeAttempts(initialMask.view))
        assertEquals(1, factory.closeAttempts(initialMask.texture))
        assertEquals(0, factory.closeAttempts(initial.handles.bindGroup))
        assertEquals(0, factory.closeAttempts(initial.handles.uniformBuffer))

        resized.rollbackBeforeSubmit()
        pool.close()
    }

    @Test
    fun `coverage mask uniform growth replaces both bind groups but preserves texture and geometry`() {
        val factory = FakeFactory()
        val pool = GPUWgpu4kCorePrimitiveFramePool(GENERATION, factory)
        val mask = coverageMask(63, 47)
        val initial = pool.acquire(maskRequirements(mask)).acquiredLease()
        val initialMask = requireNotNull(initial.handles.coverageMask)
        initial.rollbackBeforeSubmit()

        val grown = pool.acquire(maskRequirements(mask, uniformBytes = 4L * 1024L + 1L)).acquiredLease()
        val grownMask = requireNotNull(grown.handles.coverageMask)

        assertSame(initial.handles.vertexBuffer, grown.handles.vertexBuffer)
        assertSame(initial.handles.indexBuffer, grown.handles.indexBuffer)
        assertNotSame(initial.handles.uniformBuffer, grown.handles.uniformBuffer)
        assertNotSame(initial.handles.bindGroup, grown.handles.bindGroup)
        assertSame(initialMask.texture, grownMask.texture)
        assertSame(initialMask.view, grownMask.view)
        assertNotSame(initialMask.consumerBindGroup, grownMask.consumerBindGroup)
        assertEquals(1, factory.closeAttempts(initial.handles.bindGroup))
        assertEquals(1, factory.closeAttempts(initialMask.consumerBindGroup))
        assertEquals(1, factory.closeAttempts(initial.handles.uniformBuffer))
        assertEquals(0, factory.closeAttempts(initialMask.view))
        assertEquals(0, factory.closeAttempts(initialMask.texture))

        grown.rollbackBeforeSubmit()
        pool.close()
    }

    @Test
    fun `coverage mask combined resize and uniform growth publishes one complete replacement`() {
        val factory = FakeFactory()
        val pool = GPUWgpu4kCorePrimitiveFramePool(GENERATION, factory)
        val initial = pool.acquire(maskRequirements(coverageMask(63, 47))).acquiredLease()
        val initialMask = requireNotNull(initial.handles.coverageMask)
        initial.rollbackBeforeSubmit()

        val grown = pool.acquire(
            maskRequirements(coverageMask(65, 49), uniformBytes = 4L * 1024L + 1L),
        ).acquiredLease()
        val grownMask = requireNotNull(grown.handles.coverageMask)

        assertEquals(initial.slotId, grown.slotId)
        assertSame(initial.handles.vertexBuffer, grown.handles.vertexBuffer)
        assertSame(initial.handles.indexBuffer, grown.handles.indexBuffer)
        assertNotSame(initial.handles.uniformBuffer, grown.handles.uniformBuffer)
        assertNotSame(initial.handles.bindGroup, grown.handles.bindGroup)
        assertNotSame(initialMask.texture, grownMask.texture)
        assertNotSame(initialMask.view, grownMask.view)
        assertNotSame(initialMask.consumerBindGroup, grownMask.consumerBindGroup)
        listOf(
            initial.handles.uniformBuffer,
            initial.handles.bindGroup,
            initialMask.texture,
            initialMask.view,
            initialMask.consumerBindGroup,
        ).forEach { retired -> assertEquals(1, factory.closeAttempts(retired)) }

        grown.rollbackBeforeSubmit()
        val reused = pool.acquire(
            maskRequirements(coverageMask(65, 49), uniformBytes = 4L * 1024L + 1L),
        ).acquiredLease()
        assertEquals(initial.slotId, reused.slotId)
        assertSame(grown.handles.uniformBuffer, reused.handles.uniformBuffer)
        assertSame(grown.handles.bindGroup, reused.handles.bindGroup)
        assertSame(grownMask.texture, requireNotNull(reused.handles.coverageMask).texture)
        assertSame(grownMask.view, requireNotNull(reused.handles.coverageMask).view)
        assertSame(
            grownMask.consumerBindGroup,
            requireNotNull(reused.handles.coverageMask).consumerBindGroup,
        )
        reused.rollbackBeforeSubmit()
        pool.close()
    }

    @Test
    fun `coverage mask combined growth failures preserve the published slot without partial replacement`() {
        listOf(
            GPUWgpu4kCorePrimitiveFramePoolResource.UniformBuffer,
            GPUWgpu4kCorePrimitiveFramePoolResource.BindGroup,
            GPUWgpu4kCorePrimitiveFramePoolResource.CoverageMaskTexture,
            GPUWgpu4kCorePrimitiveFramePoolResource.CoverageMaskView,
            GPUWgpu4kCorePrimitiveFramePoolResource.CoverageMaskConsumerBindGroup,
        ).forEach { failingResource ->
            val factory = FakeFactory()
            val pool = GPUWgpu4kCorePrimitiveFramePool(GENERATION, factory)
            val initialRequirement = coverageMask(63, 47)
            val initial = pool.acquire(maskRequirements(initialRequirement)).acquiredLease()
            val initialMask = requireNotNull(initial.handles.coverageMask)
            initial.rollbackBeforeSubmit()
            val creationCountBeforeFailure = factory.creations.size
            factory.failNext(failingResource)

            val refused = assertIs<GPUWgpu4kCorePrimitiveFramePoolCheckout.Refused>(
                pool.acquire(maskRequirements(coverageMask(65, 49), uniformBytes = 4L * 1024L + 1L)),
            )

            assertEquals(
                failingResource,
                assertIs<GPUWgpu4kCorePrimitiveFramePoolRefusal.AllocationFailed>(refused.reason).resource,
            )
            assertEquals(0, factory.closeAttempts(initial.handles.uniformBuffer), failingResource.name)
            assertEquals(0, factory.closeAttempts(initial.handles.bindGroup), failingResource.name)
            assertEquals(0, factory.closeAttempts(initialMask.consumerBindGroup), failingResource.name)
            assertEquals(0, factory.closeAttempts(initialMask.view), failingResource.name)
            assertEquals(0, factory.closeAttempts(initialMask.texture), failingResource.name)
            val unpublished = factory.creations.drop(creationCountBeforeFailure)
            val replacementOrder = listOf(
                GPUWgpu4kCorePrimitiveFramePoolResource.UniformBuffer,
                GPUWgpu4kCorePrimitiveFramePoolResource.BindGroup,
                GPUWgpu4kCorePrimitiveFramePoolResource.CoverageMaskTexture,
                GPUWgpu4kCorePrimitiveFramePoolResource.CoverageMaskView,
                GPUWgpu4kCorePrimitiveFramePoolResource.CoverageMaskConsumerBindGroup,
            )
            assertEquals(
                replacementOrder.takeWhile { resource -> resource != failingResource },
                unpublished.map(CreatedHandle::resource),
                failingResource.name,
            )
            unpublished.forEach { created ->
                assertEquals(1, factory.closeAttempts(created.handle), failingResource.name)
            }

            val reused = pool.acquire(maskRequirements(initialRequirement)).acquiredLease()
            val reusedMask = requireNotNull(reused.handles.coverageMask)
            assertEquals(initial.slotId, reused.slotId)
            assertSame(initial.handles.vertexBuffer, reused.handles.vertexBuffer)
            assertSame(initial.handles.indexBuffer, reused.handles.indexBuffer)
            assertSame(initial.handles.uniformBuffer, reused.handles.uniformBuffer)
            assertSame(initial.handles.bindGroup, reused.handles.bindGroup)
            assertSame(initialMask.consumerBindGroup, reusedMask.consumerBindGroup)
            assertSame(initialMask.view, reusedMask.view)
            assertSame(initialMask.texture, reusedMask.texture)
            reused.rollbackBeforeSubmit()
            pool.close()
            unpublished.forEach { created ->
                assertEquals(1, factory.closeAttempts(created.handle), failingResource.name)
            }
        }
    }

    @Test
    fun `pending retired mask consumer remains a prerequisite of its shared uniform`() {
        val factory = FakeFactory()
        val pool = GPUWgpu4kCorePrimitiveFramePool(GENERATION, factory)
        val initial = pool.acquire(maskRequirements(coverageMask(63, 47))).acquiredLease()
        val initialMask = requireNotNull(initial.handles.coverageMask)
        initial.rollbackBeforeSubmit()
        factory.failClose(initialMask.consumerBindGroup, attempts = 2)

        val resized = pool.acquire(maskRequirements(coverageMask(65, 49))).acquiredLease()
        val resizedMask = requireNotNull(resized.handles.coverageMask)
        assertEquals(1, factory.closeAttempts(initialMask.consumerBindGroup))
        assertEquals(0, factory.closeAttempts(initial.handles.uniformBuffer))
        resized.rollbackBeforeSubmit()

        assertFailsWith<GPUWgpu4kCorePrimitiveFramePoolCloseFailure> { pool.close() }

        assertEquals(2, factory.closeAttempts(initialMask.consumerBindGroup))
        assertEquals(1, factory.closeAttempts(resizedMask.consumerBindGroup))
        assertEquals(1, factory.closeAttempts(resized.handles.bindGroup))
        assertEquals(0, factory.closeAttempts(initial.handles.uniformBuffer))
        assertEquals(0, factory.closeAttempts(initialMask.view))
        assertEquals(0, factory.closeAttempts(initialMask.texture))

        pool.close()

        assertEquals(3, factory.closeAttempts(initialMask.consumerBindGroup))
        assertEquals(1, factory.closeAttempts(resizedMask.consumerBindGroup))
        assertEquals(1, factory.closeAttempts(resized.handles.bindGroup))
        assertEquals(1, factory.closeAttempts(initial.handles.uniformBuffer))
        assertEquals(1, factory.closeAttempts(initialMask.view))
        assertEquals(1, factory.closeAttempts(initialMask.texture))
        assertTrue(
            factory.closeEvents.lastIndexOf("close:coverageMaskConsumerBindGroup") <
                factory.closeEvents.indexOf("close:uniform"),
        )
    }

    @Test
    fun `coverage mask completion reuses while uncertain completion quarantines the whole slot`() {
        val factory = FakeFactory()
        val pool = GPUWgpu4kCorePrimitiveFramePool(GENERATION, factory)
        val request = maskRequirements(coverageMask(32, 32))
        val completed = pool.acquire(request).acquiredLease()
        completed.markSubmitted()
        completed.completeSuccessfully()

        val reused = pool.acquire(request).acquiredLease()
        assertSame(requireNotNull(completed.handles.coverageMask).texture,
            requireNotNull(reused.handles.coverageMask).texture)
        reused.markSubmitted()
        reused.quarantineUncertain()

        val replacement = pool.acquire(request).acquiredLease()
        assertNotSame(requireNotNull(reused.handles.coverageMask).texture,
            requireNotNull(replacement.handles.coverageMask).texture)
        replacement.rollbackBeforeSubmit()
        pool.close()
    }

    @Test
    fun `failed coverage consumer bind group close blocks uniform view and texture then retries exactly`() {
        val factory = FakeFactory()
        val pool = GPUWgpu4kCorePrimitiveFramePool(GENERATION, factory)
        val lease = pool.acquire(maskRequirements(coverageMask(32, 32))).acquiredLease()
        val mask = requireNotNull(lease.handles.coverageMask)
        lease.rollbackBeforeSubmit()
        factory.failCloseOnce(mask.consumerBindGroup)

        assertFailsWith<GPUWgpu4kCorePrimitiveFramePoolCloseFailure> { pool.close() }

        assertEquals(1, factory.closeAttempts(mask.consumerBindGroup))
        assertEquals(1, factory.closeAttempts(lease.handles.bindGroup))
        assertEquals(0, factory.closeAttempts(lease.handles.uniformBuffer))
        assertEquals(0, factory.closeAttempts(mask.view))
        assertEquals(0, factory.closeAttempts(mask.texture))

        pool.close()

        assertEquals(2, factory.closeAttempts(mask.consumerBindGroup))
        assertEquals(1, factory.closeAttempts(lease.handles.bindGroup))
        assertEquals(1, factory.closeAttempts(lease.handles.uniformBuffer))
        assertEquals(1, factory.closeAttempts(mask.view))
        assertEquals(1, factory.closeAttempts(mask.texture))
        assertTrue(factory.closeEvents.indexOf("close:coverageMaskConsumerBindGroup") <
            factory.closeEvents.lastIndexOf("close:coverageMaskView"))
        assertTrue(factory.closeEvents.lastIndexOf("close:coverageMaskView") <
            factory.closeEvents.lastIndexOf("close:coverageMaskTexture"))
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

    @Test
    fun `pending retired main bind group remains a prerequisite of its shared uniform`() {
        val factory = FakeFactory()
        val pool = GPUWgpu4kCorePrimitiveFramePool(GENERATION, factory)
        val uniform32 = pool.acquire(requirements()).acquiredLease()
        uniform32.rollbackBeforeSubmit()
        factory.failClose(uniform32.handles.bindGroup, attempts = 2)

        val uniform64 = pool.acquire(
            requirements(componentIdentity = PRODUCTION_CORE_PRIMITIVE_ANALYTIC_CLIP_COMPONENT_IDENTITY),
        ).acquiredLease()

        assertEquals(uniform32.slotId, uniform64.slotId)
        assertSame(uniform32.handles.uniformBuffer, uniform64.handles.uniformBuffer)
        assertNotSame(uniform32.handles.bindGroup, uniform64.handles.bindGroup)
        assertEquals(1, factory.closeAttempts(uniform32.handles.bindGroup))
        assertEquals(0, factory.closeAttempts(uniform32.handles.uniformBuffer))
        uniform64.rollbackBeforeSubmit()

        assertFailsWith<GPUWgpu4kCorePrimitiveFramePoolCloseFailure> { pool.close() }

        assertEquals(2, factory.closeAttempts(uniform32.handles.bindGroup))
        assertEquals(1, factory.closeAttempts(uniform64.handles.bindGroup))
        assertEquals(0, factory.closeAttempts(uniform32.handles.uniformBuffer))

        pool.close()

        assertEquals(3, factory.closeAttempts(uniform32.handles.bindGroup))
        assertEquals(1, factory.closeAttempts(uniform64.handles.bindGroup))
        assertEquals(1, factory.closeAttempts(uniform32.handles.uniformBuffer))
        assertEquals(1, factory.closeAttempts(uniform64.handles.indexBuffer))
        assertEquals(1, factory.closeAttempts(uniform64.handles.vertexBuffer))
        assertTrue(
            factory.closeEvents.lastIndexOf("close:bindGroup") <
                factory.closeEvents.indexOf("close:uniform"),
        )
    }

    private fun GPUWgpu4kCorePrimitiveFramePoolCheckout.acquiredLease() =
        assertIs<GPUWgpu4kCorePrimitiveFramePoolCheckout.Acquired>(this).lease

    private fun requirements(
        deviceGeneration: GPUDeviceGenerationID = GENERATION,
        vertexBytes: Long = 1L,
        indexBytes: Long = 1L,
        uniformBytes: Long = 1L,
        pathDepthStencil: GPUWgpu4kCorePrimitivePathDepthStencilRequirement? = null,
        clipDepthStencil: GPUWgpu4kCorePrimitiveClipDepthStencilRequirement? = null,
        coverageMask: GPUWgpu4kCorePrimitiveCoverageMaskRequirement? = null,
        componentIdentity: GPUWgpu4kCorePrimitiveComponentIdentity =
            PRODUCTION_CORE_PRIMITIVE_COMPONENT_IDENTITY,
    ) = GPUWgpu4kCorePrimitiveFramePoolRequirements(
        deviceGeneration = deviceGeneration,
        vertexBytes = vertexBytes,
        indexBytes = indexBytes,
        uniformBytes = uniformBytes,
        pathDepthStencil = pathDepthStencil,
        componentIdentity = componentIdentity,
        clipDepthStencil = clipDepthStencil,
        coverageMask = coverageMask,
    )

    private fun maskRequirements(
        coverageMask: GPUWgpu4kCorePrimitiveCoverageMaskRequirement,
        uniformBytes: Long = 1L,
    ) = requirements(
        uniformBytes = uniformBytes,
        coverageMask = coverageMask,
        componentIdentity = PRODUCTION_CORE_PRIMITIVE_COVERAGE_MASK_PRODUCER_COMPONENT_IDENTITY,
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

    private fun clipDepthStencil(
        width: Int,
        height: Int,
    ) = GPUWgpu4kCorePrimitiveClipDepthStencilRequirement(
        width = width,
        height = height,
        format = GPUTextureFormat.Depth24PlusStencil8,
        sampleCount = 1,
        usage = GPUTextureUsage.RenderAttachment,
    )

    private fun coverageMask(
        width: Int,
        height: Int,
    ) = GPUWgpu4kCorePrimitiveCoverageMaskRequirement(
        width = width,
        height = height,
        format = GPUTextureFormat.RGBA8Unorm,
        sampleCount = 1,
        usage = GPUTextureUsage.RenderAttachment or GPUTextureUsage.TextureBinding,
    )

    private class FakeFactory : GPUWgpu4kCorePrimitiveFramePoolFactory {
        val creations = mutableListOf<CreatedHandle>()
        val closeEvents = mutableListOf<String>()
        private val closeCounts = IdentityHashMap<AutoCloseable, Int>()
        private val closeFailuresRemaining = IdentityHashMap<AutoCloseable, Int>()
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

        override fun createClipDepthStencilTexture(
            requirement: GPUWgpu4kCorePrimitiveClipDepthStencilRequirement,
        ): GPUTexture = create(
            GPUWgpu4kCorePrimitiveFramePoolResource.ClipDepthStencilTexture,
            "clipDepthStencilTexture",
            GPUTexture::class.java,
            clipDepthStencilRequirement = requirement,
        )

        override fun createClipDepthStencilView(texture: GPUTexture): GPUTextureView = create(
            GPUWgpu4kCorePrimitiveFramePoolResource.ClipDepthStencilView,
            "clipDepthStencilView",
            GPUTextureView::class.java,
        )

        override fun createCoverageMaskTexture(
            requirement: GPUWgpu4kCorePrimitiveCoverageMaskRequirement,
        ): GPUTexture = create(
            GPUWgpu4kCorePrimitiveFramePoolResource.CoverageMaskTexture,
            "coverageMaskTexture",
            GPUTexture::class.java,
            coverageMaskRequirement = requirement,
        )

        override fun createCoverageMaskView(texture: GPUTexture): GPUTextureView = create(
            GPUWgpu4kCorePrimitiveFramePoolResource.CoverageMaskView,
            "coverageMaskView",
            GPUTextureView::class.java,
        )

        override fun createCoverageMaskConsumerBindGroup(
            uniformBuffer: GPUBuffer,
            coverageMaskView: GPUTextureView,
        ): GPUBindGroup = create(
            GPUWgpu4kCorePrimitiveFramePoolResource.CoverageMaskConsumerBindGroup,
            "coverageMaskConsumerBindGroup",
            GPUBindGroup::class.java,
        )

        fun failNext(resource: GPUWgpu4kCorePrimitiveFramePoolResource) {
            nextFailure = resource
        }

        fun failCloseOnce(handle: AutoCloseable) {
            failClose(handle, attempts = 1)
        }

        fun failClose(handle: AutoCloseable, attempts: Int) {
            require(attempts > 0)
            closeFailuresRemaining[handle] = attempts
        }

        fun closeAttempts(handle: AutoCloseable): Int = closeCounts.getOrDefault(handle, 0)

        @Suppress("UNCHECKED_CAST")
        private fun <T : AutoCloseable> create(
            resource: GPUWgpu4kCorePrimitiveFramePoolResource,
            label: String,
            type: Class<T>,
            pathDepthStencilRequirement: GPUWgpu4kCorePrimitivePathDepthStencilRequirement? = null,
            clipDepthStencilRequirement: GPUWgpu4kCorePrimitiveClipDepthStencilRequirement? = null,
            coverageMaskRequirement: GPUWgpu4kCorePrimitiveCoverageMaskRequirement? = null,
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
                        val remainingFailures = closeFailuresRemaining.getOrDefault(closeable, 0)
                        if (remainingFailures > 0) {
                            closeFailuresRemaining[closeable] = remainingFailures - 1
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
            creations += CreatedHandle(
                resource,
                handle,
                pathDepthStencilRequirement,
                clipDepthStencilRequirement,
                coverageMaskRequirement,
                componentIdentity,
            )
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
        val clipDepthStencilRequirement: GPUWgpu4kCorePrimitiveClipDepthStencilRequirement? = null,
        val coverageMaskRequirement: GPUWgpu4kCorePrimitiveCoverageMaskRequirement? = null,
        val componentIdentity: GPUWgpu4kCorePrimitiveComponentIdentity? = null,
    )

    private companion object {
        val GENERATION = GPUDeviceGenerationID(7)
    }
}
