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
}
