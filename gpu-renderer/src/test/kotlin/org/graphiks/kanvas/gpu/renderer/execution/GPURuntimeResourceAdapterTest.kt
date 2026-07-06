package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.ArrayBuffer
import io.ygdrasil.webgpu.GPUBindGroup
import io.ygdrasil.webgpu.GPUBuffer
import io.ygdrasil.webgpu.GPUBufferMapState
import io.ygdrasil.webgpu.GPUBufferUsage
import io.ygdrasil.webgpu.GPUMapMode
import io.ygdrasil.webgpu.GPUSize64
import org.graphiks.kanvas.gpu.renderer.resources.GPUBindGroupLeaseRequest
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceLeaseCacheResult
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceLeaseFactoryResult
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceLeaseKind
import org.graphiks.kanvas.gpu.renderer.resources.GPUUniformSlabLeaseRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
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

    @Test
    fun `strict adapter refuses unprepared uniform slabs`() {
        val adapter = GPURuntimeResourceAdapter(requirePreparedResources = true)

        val result = adapter.createUniformSlab(fullscreenUniformRequest())

        val failed = result as GPUResourceLeaseFactoryResult.Failed
        assertEquals("unsupported.resource.adapter_create_failed", failed.diagnostic.code)
        assertEquals("uniform-slab-preparation-missing", failed.diagnostic.facts["reason"])
        assertNull(adapter.uniformSlabBuffer("uniform-slab:fullscreen:frame-1"))
        assertFalse(adapter.containsLease("uniform-slab:fullscreen:frame-1"))
    }

    @Test
    fun `strict adapter reuses prepared native handles without recreating them`() {
        val adapter = GPURuntimeResourceAdapter(requirePreparedResources = true)
        val buffer = CountingGPUBuffer(label = "unit-buffer")
        val bindGroup = CountingGPUBindGroup(label = "unit-bind-group")
        var bufferCreates = 0
        var bindGroupCreates = 0
        val uniformId = "uniform-slab:fullscreen:frame-1"
        val bindGroupId = "bind-group:fullscreen:frame-1:slot:payload-0"

        adapter.prepareUniformSlab(uniformId) {
            bufferCreates += 1
            buffer
        }
        assertTrue(adapter.createUniformSlab(fullscreenUniformRequest()) is GPUResourceLeaseFactoryResult.Created)
        assertTrue(adapter.createUniformSlab(fullscreenUniformRequest()) is GPUResourceLeaseFactoryResult.Created)
        adapter.prepareBindGroup(bindGroupId) {
            bindGroupCreates += 1
            bindGroup
        }
        assertTrue(adapter.createBindGroup(fullscreenBindGroupRequest(bindGroupId)) is GPUResourceLeaseFactoryResult.Created)
        assertTrue(adapter.createBindGroup(fullscreenBindGroupRequest(bindGroupId)) is GPUResourceLeaseFactoryResult.Created)

        assertEquals(1, bufferCreates)
        assertEquals(1, bindGroupCreates)
        assertSame(buffer, adapter.uniformSlabBuffer(uniformId))
        assertSame(bindGroup, adapter.bindGroup(bindGroupId))
    }

    @Test
    fun `close releases prepared native handles and clears lease membership`() {
        val adapter = GPURuntimeResourceAdapter(requirePreparedResources = true)
        val buffer = CountingGPUBuffer(label = "unit-buffer")
        val bindGroup = CountingGPUBindGroup(label = "unit-bind-group")
        val uniformId = "uniform-slab:fullscreen:frame-1"
        val bindGroupId = "bind-group:fullscreen:frame-1:slot:payload-0"
        adapter.prepareUniformSlab(uniformId) { buffer }
        assertTrue(adapter.createUniformSlab(fullscreenUniformRequest()) is GPUResourceLeaseFactoryResult.Created)
        adapter.prepareBindGroup(bindGroupId) { bindGroup }
        assertTrue(adapter.createBindGroup(fullscreenBindGroupRequest(bindGroupId)) is GPUResourceLeaseFactoryResult.Created)

        adapter.close()

        assertEquals(1, bindGroup.closeCount)
        assertEquals(1, buffer.closeCount)
        assertFalse(adapter.containsLease(uniformId))
        assertFalse(adapter.containsLease(bindGroupId))
        assertNull(adapter.uniformSlabBuffer(uniformId))
        assertNull(adapter.bindGroup(bindGroupId))
    }
}

private fun fullscreenUniformRequest(): GPUUniformSlabLeaseRequest =
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
    )

private fun fullscreenBindGroupRequest(leaseId: String): GPUBindGroupLeaseRequest =
    GPUBindGroupLeaseRequest(
        leaseId = leaseId,
        deviceGeneration = 11,
        descriptorHash = "sha256:bind-group-frame-1",
        ownerScope = "frame-1",
        usageLabels = listOf("uniform"),
        releasePolicy = "submission-complete",
    )

private class CountingGPUBuffer(
    override var label: String,
) : GPUBuffer {
    var closeCount: Int = 0
        private set

    override val size: ULong = 512u
    override val usage: Set<GPUBufferUsage> = setOf(GPUBufferUsage.Uniform)
    override val mapState: GPUBufferMapState = GPUBufferMapState.Unmapped

    override suspend fun mapAsync(mode: GPUMapMode, offset: GPUSize64, size: GPUSize64?): Result<Unit> =
        Result.success(Unit)

    override fun getMappedRange(offset: GPUSize64, size: GPUSize64?): ArrayBuffer =
        error("CountingGPUBuffer does not expose mapped ranges")

    override fun unmap() = Unit

    override fun close() {
        closeCount += 1
    }
}

private class CountingGPUBindGroup(
    override var label: String,
) : GPUBindGroup {
    var closeCount: Int = 0
        private set

    override fun close() {
        closeCount += 1
    }
}
