package org.graphiks.kanvas.gpu.renderer.resources

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class GPUResourceLeaseContractsTest {
    @Test
    fun `resource lease dumps deterministic non handle facts`() {
        val lease = GPUResourceLease(
            leaseId = "uniform-slab:frame-1",
            resourceKind = GPUResourceLeaseKind.UniformSlab,
            deviceGeneration = 11,
            descriptorHash = "sha256:uniform-slab-frame-1",
            ownerScope = "fullscreen-pass",
            usageLabels = listOf("uniform", "copy_dst"),
            releasePolicy = "submission-complete",
            cacheResult = GPUResourceLeaseCacheResult.Create,
            evidenceFacts = mapOf(
                "alignment" to "256",
                "totalBytes" to "512",
            ),
        )

        assertEquals(
            listOf(
                "resource-provider.lease id=uniform-slab:frame-1 kind=uniform-slab result=create " +
                    "deviceGeneration=11 owner=fullscreen-pass release=submission-complete " +
                    "usage=copy_dst,uniform descriptor=sha256:uniform-slab-frame-1 " +
                    "facts=alignment=256;totalBytes=512",
            ),
            lease.dumpLines(),
        )
        assertFalse(lease.dumpLines().joinToString("\n").contains("@"))
        assertFalse(lease.dumpLines().joinToString("\n").contains("0x"))
    }

    @Test
    fun `resource lease rejects unsafe evidence values`() {
        assertFailsWith<IllegalArgumentException> {
            GPUResourceLease(
                leaseId = "bind-group:" + "0x123456",
                resourceKind = GPUResourceLeaseKind.BindGroup,
                deviceGeneration = 11,
                descriptorHash = "sha256:bind-group",
                ownerScope = "fullscreen-pass",
                usageLabels = listOf("uniform"),
                releasePolicy = "submission-complete",
                cacheResult = GPUResourceLeaseCacheResult.Create,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            GPUResourceLease(
                leaseId = "sampler:linear",
                resourceKind = GPUResourceLeaseKind.Sampler,
                deviceGeneration = 11,
                descriptorHash = "sha256:sampler",
                ownerScope = "sampler-cache",
                usageLabels = listOf("sampler"),
                releasePolicy = "descriptor-cache",
                cacheResult = GPUResourceLeaseCacheResult.Create,
                evidenceFacts = mapOf("backend" to ("W" + "GPUHandle")),
            )
        }
    }

    @Test
    fun `resource lease snapshots caller owned dump inputs`() {
        val usageLabels = mutableListOf("uniform")
        val facts = mutableMapOf("alignment" to "256")

        val lease = GPUResourceLease(
            leaseId = "uniform-slab:snapshot",
            resourceKind = GPUResourceLeaseKind.UniformSlab,
            deviceGeneration = 11,
            descriptorHash = "sha256:uniform-slab-snapshot",
            ownerScope = "snapshot-test",
            usageLabels = usageLabels,
            releasePolicy = "submission-complete",
            cacheResult = GPUResourceLeaseCacheResult.Create,
            evidenceFacts = facts,
        )

        usageLabels += "storage"
        facts["alignment"] = "512"
        facts["extra"] = "changed"

        assertEquals(
            listOf(
                "resource-provider.lease id=uniform-slab:snapshot kind=uniform-slab result=create " +
                    "deviceGeneration=11 owner=snapshot-test release=submission-complete " +
                    "usage=uniform descriptor=sha256:uniform-slab-snapshot " +
                    "facts=alignment=256",
            ),
            lease.dumpLines(),
        )
    }

    @Test
    fun `lease list dumps in stable order`() {
        val sampler = lease("sampler:linear", GPUResourceLeaseKind.Sampler)
        val uniform = lease("uniform-slab:frame-1", GPUResourceLeaseKind.UniformSlab)

        assertEquals(
            listOf(
                "resource-provider.lease id=sampler:linear kind=sampler result=create " +
                    "deviceGeneration=11 owner=unit release=submission-complete usage=uniform " +
                    "descriptor=sha256:sampler:linear facts=none",
                "resource-provider.lease id=uniform-slab:frame-1 kind=uniform-slab result=create " +
                    "deviceGeneration=11 owner=unit release=submission-complete usage=uniform " +
                    "descriptor=sha256:uniform-slab:frame-1 facts=none",
            ),
            listOf(uniform, sampler).dumpResourceLeaseLines(),
        )
    }

    @Test
    fun `uniform slab lease request validates alignment budget and ids`() {
        val request = GPUUniformSlabLeaseRequest(
            leaseId = "uniform-slab:frame-1",
            targetId = "root-target",
            frameId = "frame-1",
            deviceGeneration = 11,
            descriptorHash = "sha256:uniform-slab-frame-1",
            totalBytes = 512,
            alignmentBytes = 256,
            releasePolicy = "submission-complete",
            payloadCount = 2,
        )

        assertEquals(512, request.totalBytes)
        assertFailsWith<IllegalArgumentException> {
            request.copy(totalBytes = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            request.copy(alignmentBytes = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            request.copy(payloadCount = 0)
        }
    }

    @Test
    fun `lease factory failure maps to stable diagnostic`() {
        val failure = GPUResourceLeaseFactoryResult.Failed(
            diagnostic = GPUResourceDiagnostic.adapterCreateFailed(
                resourceLabel = "uniform-slab:frame-1",
                reason = "allocation-denied",
            ),
        )

        assertEquals("unsupported.resource.adapter_create_failed", failure.diagnostic.code)
        assertEquals("uniform-slab:frame-1", failure.diagnostic.resourceLabel)
        assertEquals(true, failure.diagnostic.terminal)
        assertEquals(mapOf("reason" to "allocation-denied"), failure.diagnostic.facts)
        assertEquals(
            "GPU resource adapter failed to create uniform-slab:frame-1.",
            failure.diagnostic.message,
        )
        assertFalse(failure.diagnostic.message.contains("allocation-denied"))
    }

    @Test
    fun `adapter create failed rejects unsafe reason`() {
        assertFailsWith<IllegalArgumentException> {
            GPUResourceDiagnostic.adapterCreateFailed(
                resourceLabel = "uniform-slab:frame-1",
                reason = "adapter-" + "w" + "gpu",
            )
        }
    }

    @Test
    fun `adapter create failed rejects unsafe resource label`() {
        assertFailsWith<IllegalArgumentException> {
            GPUResourceDiagnostic.adapterCreateFailed(
                resourceLabel = "bind-" + "w" + "gpu",
                reason = "allocation-denied",
            )
        }
    }

    @Test
    fun `evidence only factory creates uniform slab lease with stable evidence keys`() {
        val result = EvidenceOnlyGPUResourceLeaseFactory.createUniformSlab(
            GPUUniformSlabLeaseRequest(
                leaseId = "uniform-slab:frame-2",
                targetId = "root-target",
                frameId = "frame-2",
                deviceGeneration = 12,
                descriptorHash = "sha256:uniform-slab-frame-2",
                totalBytes = 1024,
                alignmentBytes = 256,
                releasePolicy = "submission-complete",
                payloadCount = 4,
            ),
        )

        val created = result as GPUResourceLeaseFactoryResult.Created
        assertEquals(
            mapOf(
                "alignment" to "256",
                "payloadCount" to "4",
                "target" to "root-target",
                "totalBytes" to "1024",
            ),
            created.lease.evidenceFacts,
        )
    }

    @Test
    fun `bind group lease request validates blank and unsafe labels`() {
        assertFailsWith<IllegalArgumentException> {
            bindGroupRequest(usageLabels = listOf("uniform", " "))
        }
        assertFailsWith<IllegalArgumentException> {
            bindGroupRequest(usageLabels = listOf("uniform", "bind-" + "w" + "gpu"))
        }
    }

    @Test
    fun `bind group lease factory snapshots request usage labels`() {
        val usageLabels = mutableListOf("uniform")
        val request = bindGroupRequest(usageLabels = usageLabels)

        usageLabels += "storage"

        assertEquals(listOf("uniform"), request.usageLabels)

        val created = EvidenceOnlyGPUResourceLeaseFactory.createBindGroup(request)
            as GPUResourceLeaseFactoryResult.Created

        usageLabels += "sampled"

        assertEquals(listOf("uniform"), created.lease.usageLabels)
        assertEquals(
            listOf(
                "resource-provider.lease id=bind-group:unit kind=bind-group result=create " +
                    "deviceGeneration=11 owner=fullscreen-pass release=submission-complete " +
                    "usage=uniform descriptor=sha256:bind-group:unit facts=none",
            ),
            created.lease.dumpLines(),
        )
    }

    @Test
    fun `evidence only factory creates bind group lease`() {
        val created = EvidenceOnlyGPUResourceLeaseFactory.createBindGroup(
            bindGroupRequest(usageLabels = listOf("uniform", "sampled")),
        ) as GPUResourceLeaseFactoryResult.Created

        assertEquals(GPUResourceLeaseKind.BindGroup, created.lease.resourceKind)
        assertEquals(GPUResourceLeaseCacheResult.Create, created.lease.cacheResult)
        assertEquals("fullscreen-pass", created.lease.ownerScope)
        assertEquals(listOf("uniform", "sampled"), created.lease.usageLabels)
    }

    private fun lease(id: String, kind: GPUResourceLeaseKind): GPUResourceLease =
        GPUResourceLease(
            leaseId = id,
            resourceKind = kind,
            deviceGeneration = 11,
            descriptorHash = "sha256:$id",
            ownerScope = "unit",
            usageLabels = listOf("uniform"),
            releasePolicy = "submission-complete",
            cacheResult = GPUResourceLeaseCacheResult.Create,
        )

    private fun bindGroupRequest(
        usageLabels: List<String>,
    ): GPUBindGroupLeaseRequest =
        GPUBindGroupLeaseRequest(
            leaseId = "bind-group:unit",
            deviceGeneration = 11,
            descriptorHash = "sha256:bind-group:unit",
            ownerScope = "fullscreen-pass",
            usageLabels = usageLabels,
            releasePolicy = "submission-complete",
        )
}
