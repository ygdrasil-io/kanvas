package org.graphiks.kanvas.gpu.renderer.execution

import org.graphiks.kanvas.gpu.renderer.resources.GPUBindGroupLeaseRequest
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceDiagnostic
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceLease
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceLeaseCacheResult
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceLeaseFactory
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceLeaseFactoryResult
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceLeaseKind
import org.graphiks.kanvas.gpu.renderer.resources.GPUUniformSlabLeaseRequest

class GPURuntimeResourceAdapter : GPUResourceLeaseFactory {
    private val liveLeaseIds = linkedSetOf<String>()

    override fun createUniformSlab(request: GPUUniformSlabLeaseRequest): GPUResourceLeaseFactoryResult {
        liveLeaseIds += request.leaseId
        return GPUResourceLeaseFactoryResult.Created(
            GPUResourceLease(
                leaseId = request.leaseId,
                resourceKind = GPUResourceLeaseKind.UniformSlab,
                deviceGeneration = request.deviceGeneration,
                descriptorHash = request.descriptorHash,
                ownerScope = request.frameId,
                usageLabels = listOf("copy_dst", "uniform"),
                releasePolicy = request.releasePolicy,
                cacheResult = GPUResourceLeaseCacheResult.Create,
                evidenceFacts = mapOf(
                    "alignment" to request.alignmentBytes.toString(),
                    "payloadCount" to request.payloadCount.toString(),
                    "target" to request.targetId,
                    "totalBytes" to request.totalBytes.toString(),
                ),
            ),
        )
    }

    override fun createBindGroup(request: GPUBindGroupLeaseRequest): GPUResourceLeaseFactoryResult {
        val prerequisiteLeaseId = request.fullscreenPrerequisiteLeaseId()
        if (prerequisiteLeaseId != null && prerequisiteLeaseId !in liveLeaseIds) {
            return GPUResourceLeaseFactoryResult.Failed(
                diagnostic = GPUResourceDiagnostic.adapterCreateFailed(
                    resourceLabel = request.leaseId,
                    reason = "uniform-slab-lease-missing",
                ),
            )
        }

        liveLeaseIds += request.leaseId
        return GPUResourceLeaseFactoryResult.Created(
            GPUResourceLease(
                leaseId = request.leaseId,
                resourceKind = GPUResourceLeaseKind.BindGroup,
                deviceGeneration = request.deviceGeneration,
                descriptorHash = request.descriptorHash,
                ownerScope = request.ownerScope,
                usageLabels = request.usageLabels,
                releasePolicy = request.releasePolicy,
                cacheResult = GPUResourceLeaseCacheResult.Create,
            ),
        )
    }

    fun containsLease(leaseId: String): Boolean = leaseId in liveLeaseIds
}

private fun GPUBindGroupLeaseRequest.fullscreenPrerequisiteLeaseId(): String? {
    val prefix = "bind-group:fullscreen:"
    if (!leaseId.startsWith(prefix)) return null
    return "uniform-slab:fullscreen:" + leaseId.removePrefix(prefix)
}
