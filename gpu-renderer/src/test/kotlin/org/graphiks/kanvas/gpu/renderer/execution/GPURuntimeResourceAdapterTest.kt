package org.graphiks.kanvas.gpu.renderer.execution

import org.graphiks.kanvas.gpu.renderer.resources.GPUBindGroupLeaseRequest
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceLeaseCacheResult
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceLeaseFactoryResult
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceLeaseKind
import org.graphiks.kanvas.gpu.renderer.resources.GPUUniformSlabLeaseRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GPURuntimeResourceAdapterTest {
    @Test
    fun `create uniform slab registers lease facts and membership`() {
        val adapter = GPURuntimeResourceAdapter()

        val result = adapter.createUniformSlab(
            GPUUniformSlabLeaseRequest(
                leaseId = "uniform-slab:fullscreen:frame-1",
                targetId = "root-target",
                frameId = "frame-1",
                deviceGeneration = 11,
                descriptorHash = "sha256:uniform-slab-frame-1",
                totalBytes = 512,
                alignmentBytes = 256,
                releasePolicy = "submission-complete",
                payloadCount = 2,
            ),
        )

        val created = result as GPUResourceLeaseFactoryResult.Created

        assertEquals(GPUResourceLeaseKind.UniformSlab, created.lease.resourceKind)
        assertEquals(
            mapOf(
                "alignment" to "256",
                "payloadCount" to "2",
                "target" to "root-target",
                "totalBytes" to "512",
            ),
            created.lease.evidenceFacts,
        )
        assertTrue(adapter.containsLease("uniform-slab:fullscreen:frame-1"))
        assertFalse(created.lease.dumpLines().joinToString("\n").contains("@"))
    }

    @Test
    fun `fullscreen bind group is refused until prerequisite uniform slab exists`() {
        val adapter = GPURuntimeResourceAdapter()
        val bindGroupId = "bind-group:fullscreen:frame-1"

        val result = adapter.createBindGroup(
            GPUBindGroupLeaseRequest(
                leaseId = bindGroupId,
                deviceGeneration = 11,
                descriptorHash = "sha256:bind-group-frame-1",
                ownerScope = "frame-1",
                usageLabels = listOf("uniform"),
                releasePolicy = "submission-complete",
            ),
        )

        val failed = result as GPUResourceLeaseFactoryResult.Failed

        assertEquals("unsupported.resource.adapter_create_failed", failed.diagnostic.code)
        assertFalse(adapter.containsLease(bindGroupId))
        assertFalse(failed.diagnostic.message.contains("@"))
        assertFalse(failed.diagnostic.facts.values.joinToString("\n").contains("@"))
    }

    @Test
    fun `fullscreen bind group is accepted after prerequisite uniform slab exists`() {
        val adapter = GPURuntimeResourceAdapter()
        val uniformId = "uniform-slab:fullscreen:frame-1"
        val bindGroupId = "bind-group:fullscreen:frame-1"

        val uniform = adapter.createUniformSlab(
            GPUUniformSlabLeaseRequest(
                leaseId = uniformId,
                targetId = "root-target",
                frameId = "frame-1",
                deviceGeneration = 11,
                descriptorHash = "sha256:uniform-slab-frame-1",
                totalBytes = 512,
                alignmentBytes = 256,
                releasePolicy = "submission-complete",
                payloadCount = 2,
            ),
        )
        assertTrue(uniform is GPUResourceLeaseFactoryResult.Created)

        val result = adapter.createBindGroup(
            GPUBindGroupLeaseRequest(
                leaseId = bindGroupId,
                deviceGeneration = 11,
                descriptorHash = "sha256:bind-group-frame-1",
                ownerScope = "frame-1",
                usageLabels = listOf("uniform"),
                releasePolicy = "submission-complete",
            ),
        )

        val created = result as GPUResourceLeaseFactoryResult.Created

        assertEquals(GPUResourceLeaseKind.BindGroup, created.lease.resourceKind)
        assertEquals(GPUResourceLeaseCacheResult.Create, created.lease.cacheResult)
        assertTrue(adapter.containsLease(bindGroupId))
        assertFalse(created.lease.dumpLines().joinToString("\n").contains("@"))
    }

    @Test
    fun `non fullscreen bind group is accepted without prerequisite`() {
        val adapter = GPURuntimeResourceAdapter()
        val bindGroupId = "bind-group:shared:frame-1"

        val result = adapter.createBindGroup(
            GPUBindGroupLeaseRequest(
                leaseId = bindGroupId,
                deviceGeneration = 11,
                descriptorHash = "sha256:bind-group-shared",
                ownerScope = "frame-1",
                usageLabels = listOf("uniform"),
                releasePolicy = "submission-complete",
            ),
        )

        val created = result as GPUResourceLeaseFactoryResult.Created

        assertEquals(GPUResourceLeaseKind.BindGroup, created.lease.resourceKind)
        assertTrue(adapter.containsLease(bindGroupId))
        assertFalse(created.lease.dumpLines().joinToString("\n").contains("@"))
    }
}
