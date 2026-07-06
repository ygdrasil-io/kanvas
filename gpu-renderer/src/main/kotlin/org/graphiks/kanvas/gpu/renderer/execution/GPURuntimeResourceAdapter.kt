package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.GPUBindGroup
import io.ygdrasil.webgpu.GPUBuffer
import org.graphiks.kanvas.gpu.renderer.resources.GPUBindGroupLeaseRequest
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceDiagnostic
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceLease
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceLeaseCacheResult
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceLeaseFactory
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceLeaseFactoryResult
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceLeaseKind
import org.graphiks.kanvas.gpu.renderer.resources.GPUUniformSlabLeaseRequest

class GPURuntimeResourceAdapter(
    private val requirePreparedResources: Boolean = false,
) : GPUResourceLeaseFactory, AutoCloseable {
    private val liveLeaseIds = linkedSetOf<String>()
    private val uniformSlabs = linkedMapOf<String, GPUBuffer>()
    private val bindGroups = linkedMapOf<String, GPUBindGroup>()
    private val pendingUniformSlabs = linkedMapOf<String, () -> GPUBuffer>()
    private val pendingBindGroups = linkedMapOf<String, () -> GPUBindGroup>()

    fun prepareUniformSlab(leaseId: String, createBuffer: () -> GPUBuffer) {
        pendingUniformSlabs[leaseId] = createBuffer
    }

    fun uniformSlabBuffer(leaseId: String): GPUBuffer? = uniformSlabs[leaseId]

    fun clearPreparedUniformSlab(leaseId: String) {
        pendingUniformSlabs.remove(leaseId)
    }

    fun prepareBindGroup(leaseId: String, createBindGroup: () -> GPUBindGroup) {
        pendingBindGroups[leaseId] = createBindGroup
    }

    fun bindGroup(leaseId: String): GPUBindGroup? = bindGroups[leaseId]

    fun clearPreparedBindGroup(leaseId: String) {
        pendingBindGroups.remove(leaseId)
    }

    override fun createUniformSlab(request: GPUUniformSlabLeaseRequest): GPUResourceLeaseFactoryResult {
        val buffer = uniformSlabs[request.leaseId] ?: run {
            val createBuffer = pendingUniformSlabs.remove(request.leaseId)
            if (createBuffer != null) {
                try {
                    createBuffer().also { created -> uniformSlabs[request.leaseId] = created }
                } catch (_: Throwable) {
                    return GPUResourceLeaseFactoryResult.Failed(
                        diagnostic = GPUResourceDiagnostic.adapterCreateFailed(
                            resourceLabel = request.leaseId,
                            reason = "uniform-slab-create-failed",
                        ),
                    )
                }
            } else if (requirePreparedResources) {
                return GPUResourceLeaseFactoryResult.Failed(
                    diagnostic = GPUResourceDiagnostic.adapterCreateFailed(
                        resourceLabel = request.leaseId,
                        reason = "uniform-slab-preparation-missing",
                    ),
                )
            } else {
                null
            }
        }
        if (buffer != null) {
            uniformSlabs[request.leaseId] = buffer
        }
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

        val bindGroup = bindGroups[request.leaseId] ?: run {
            val createBindGroup = pendingBindGroups.remove(request.leaseId)
            if (createBindGroup != null) {
                try {
                    createBindGroup().also { created -> bindGroups[request.leaseId] = created }
                } catch (_: Throwable) {
                    return GPUResourceLeaseFactoryResult.Failed(
                        diagnostic = GPUResourceDiagnostic.adapterCreateFailed(
                            resourceLabel = request.leaseId,
                            reason = "bind-group-create-failed",
                        ),
                    )
                }
            } else if (requirePreparedResources) {
                return GPUResourceLeaseFactoryResult.Failed(
                    diagnostic = GPUResourceDiagnostic.adapterCreateFailed(
                        resourceLabel = request.leaseId,
                        reason = "bind-group-preparation-missing",
                    ),
                )
            } else {
                null
            }
        }
        if (bindGroup != null) {
            bindGroups[request.leaseId] = bindGroup
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

    override fun close() {
        var firstFailure: Throwable? = null
        fun closeQuietly(block: () -> Unit) {
            try {
                block()
            } catch (error: Throwable) {
                if (firstFailure == null) {
                    firstFailure = error
                } else {
                    firstFailure.addSuppressed(error)
                }
            }
        }
        bindGroups.values.forEach { bindGroup -> closeQuietly { bindGroup.close() } }
        uniformSlabs.values.forEach { buffer -> closeQuietly { buffer.close() } }
        bindGroups.clear()
        uniformSlabs.clear()
        pendingBindGroups.clear()
        pendingUniformSlabs.clear()
        liveLeaseIds.clear()
        firstFailure?.let { throw it }
    }
}

private fun GPUBindGroupLeaseRequest.fullscreenPrerequisiteLeaseId(): String? {
    val prefix = "bind-group:fullscreen:"
    if (!leaseId.startsWith(prefix)) return null
    val suffix = leaseId.removePrefix(prefix).substringBefore(":slot:")
    return "uniform-slab:fullscreen:$suffix"
}
