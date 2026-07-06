package org.graphiks.kanvas.gpu.renderer.resources

enum class GPUResourceLeaseKind(val dumpToken: String) {
    UniformSlab("uniform-slab"),
    NullBuffer("null-buffer"),
    BindGroup("bind-group"),
    Texture("texture"),
    TextureView("texture-view"),
    Sampler("sampler"),
}

enum class GPUResourceLeaseCacheResult(val dumpToken: String) {
    Create("create"),
    Reuse("reuse"),
    Refuse("refuse"),
    Deferred("deferred"),
    StaleGeneration("stale-generation"),
    AdapterFailure("adapter-failure"),
}

data class GPUResourceLease(
    val leaseId: String,
    val resourceKind: GPUResourceLeaseKind,
    val deviceGeneration: Long,
    val descriptorHash: String,
    val ownerScope: String,
    val usageLabels: List<String>,
    val releasePolicy: String,
    val cacheResult: GPUResourceLeaseCacheResult,
    val evidenceFacts: Map<String, String> = emptyMap(),
) {
    internal val dumpUsageLabelsSnapshot: List<String> = usageLabels.toList()
    internal val dumpEvidenceFactsSnapshot: Map<String, String> = evidenceFacts.toMap()

    init {
        require(leaseId.isNotBlank()) { "GPUResourceLease.leaseId must not be blank" }
        requireLeaseDumpSafe("GPUResourceLease.leaseId", leaseId)
        require(deviceGeneration >= 0L) { "GPUResourceLease.deviceGeneration must be non-negative" }
        require(descriptorHash.isNotBlank()) { "GPUResourceLease.descriptorHash must not be blank" }
        requireLeaseDumpSafe("GPUResourceLease.descriptorHash", descriptorHash)
        require(ownerScope.isNotBlank()) { "GPUResourceLease.ownerScope must not be blank" }
        requireLeaseDumpSafe("GPUResourceLease.ownerScope", ownerScope)
        require(usageLabels.isNotEmpty()) { "GPUResourceLease.usageLabels must not be empty" }
        usageLabels.forEach { usage ->
            require(usage.isNotBlank()) { "GPUResourceLease.usageLabels must not contain blank values" }
            requireLeaseDumpSafe("GPUResourceLease.usageLabels", usage)
        }
        require(releasePolicy.isNotBlank()) { "GPUResourceLease.releasePolicy must not be blank" }
        requireLeaseDumpSafe("GPUResourceLease.releasePolicy", releasePolicy)
        evidenceFacts.forEach { (key, value) ->
            require(key.isNotBlank()) { "GPUResourceLease.evidenceFacts keys must not be blank" }
            require(value.isNotBlank()) { "GPUResourceLease.evidenceFacts values must not be blank" }
            requireLeaseDumpSafe("GPUResourceLease.evidenceFacts key", key)
            requireLeaseDumpSafe("GPUResourceLease.evidenceFacts value", value)
        }
    }

    fun dumpLines(): List<String> =
        listOf(
            "resource-provider.lease id=$leaseId kind=${resourceKind.dumpToken} " +
                "result=${cacheResult.dumpToken} deviceGeneration=$deviceGeneration " +
                "owner=$ownerScope release=$releasePolicy " +
                "usage=${dumpUsageLabelsSnapshot.dumpLeaseList()} descriptor=$descriptorHash " +
                "facts=${dumpEvidenceFactsSnapshot.dumpLeaseFacts()}",
        )
}

fun List<GPUResourceLease>.dumpResourceLeaseLines(): List<String> =
    sortedWith(
        compareBy<GPUResourceLease> { lease -> lease.leaseId }
            .thenBy { lease -> lease.resourceKind.dumpToken },
    ).flatMap { lease -> lease.dumpLines() }

private fun List<String>.dumpLeaseList(): String =
    if (isEmpty()) "none" else sorted().joinToString(",")

private fun Map<String, String>.dumpLeaseFacts(): String =
    if (isEmpty()) {
        "none"
    } else {
        entries.sortedBy { entry -> entry.key }
            .joinToString(";") { entry -> "${entry.key}=${entry.value}" }
    }

private val RESOURCE_LEASE_RAW_IMPL_TOKEN = "w" + "gpu"
private val RESOURCE_LEASE_UNSAFE_DUMP_PATTERN =
    Regex("(?i)(@|0x[0-9a-f]{6,}|$RESOURCE_LEASE_RAW_IMPL_TOKEN|externaltexturehandle|gpu[a-z0-9]*handle)")

private fun requireLeaseDumpSafe(fieldName: String, value: String) {
    require(!RESOURCE_LEASE_UNSAFE_DUMP_PATTERN.containsMatchIn(value)) {
        "$fieldName must use dump-safe GPU evidence labels"
    }
}

data class GPUUniformSlabLeaseRequest(
    val leaseId: String,
    val targetId: String,
    val frameId: String,
    val deviceGeneration: Long,
    val descriptorHash: String,
    val totalBytes: Long,
    val alignmentBytes: Long,
    val releasePolicy: String,
    val payloadCount: Int,
) {
    init {
        require(leaseId.isNotBlank()) { "GPUUniformSlabLeaseRequest.leaseId must not be blank" }
        requireLeaseDumpSafe("GPUUniformSlabLeaseRequest.leaseId", leaseId)
        require(targetId.isNotBlank()) { "GPUUniformSlabLeaseRequest.targetId must not be blank" }
        requireLeaseDumpSafe("GPUUniformSlabLeaseRequest.targetId", targetId)
        require(frameId.isNotBlank()) { "GPUUniformSlabLeaseRequest.frameId must not be blank" }
        requireLeaseDumpSafe("GPUUniformSlabLeaseRequest.frameId", frameId)
        require(deviceGeneration >= 0L) {
            "GPUUniformSlabLeaseRequest.deviceGeneration must be non-negative"
        }
        require(descriptorHash.isNotBlank()) {
            "GPUUniformSlabLeaseRequest.descriptorHash must not be blank"
        }
        requireLeaseDumpSafe("GPUUniformSlabLeaseRequest.descriptorHash", descriptorHash)
        require(totalBytes > 0L) { "GPUUniformSlabLeaseRequest.totalBytes must be positive" }
        require(alignmentBytes > 0L) {
            "GPUUniformSlabLeaseRequest.alignmentBytes must be positive"
        }
        require(releasePolicy.isNotBlank()) {
            "GPUUniformSlabLeaseRequest.releasePolicy must not be blank"
        }
        requireLeaseDumpSafe("GPUUniformSlabLeaseRequest.releasePolicy", releasePolicy)
        require(payloadCount > 0) { "GPUUniformSlabLeaseRequest.payloadCount must be positive" }
    }
}

data class GPUBindGroupLeaseRequest(
    val leaseId: String,
    val deviceGeneration: Long,
    val descriptorHash: String,
    val ownerScope: String,
    val usageLabels: List<String>,
    val releasePolicy: String,
) {
    init {
        require(leaseId.isNotBlank()) { "GPUBindGroupLeaseRequest.leaseId must not be blank" }
        require(deviceGeneration >= 0L) {
            "GPUBindGroupLeaseRequest.deviceGeneration must be non-negative"
        }
        require(descriptorHash.isNotBlank()) {
            "GPUBindGroupLeaseRequest.descriptorHash must not be blank"
        }
        require(ownerScope.isNotBlank()) { "GPUBindGroupLeaseRequest.ownerScope must not be blank" }
        require(usageLabels.isNotEmpty()) { "GPUBindGroupLeaseRequest.usageLabels must not be empty" }
        require(releasePolicy.isNotBlank()) {
            "GPUBindGroupLeaseRequest.releasePolicy must not be blank"
        }
        requireLeaseDumpSafe("GPUBindGroupLeaseRequest.leaseId", leaseId)
        requireLeaseDumpSafe("GPUBindGroupLeaseRequest.descriptorHash", descriptorHash)
        requireLeaseDumpSafe("GPUBindGroupLeaseRequest.ownerScope", ownerScope)
        usageLabels.forEach { usageLabel ->
            require(usageLabel.isNotBlank()) {
                "GPUBindGroupLeaseRequest.usageLabels must not contain blank values"
            }
            requireLeaseDumpSafe("GPUBindGroupLeaseRequest.usageLabels", usageLabel)
        }
        requireLeaseDumpSafe("GPUBindGroupLeaseRequest.releasePolicy", releasePolicy)
    }

    internal val dumpUsageLabelsSnapshot: List<String> = usageLabels.toList()
}

sealed interface GPUResourceLeaseFactoryResult {
    data class Created(val lease: GPUResourceLease) : GPUResourceLeaseFactoryResult

    data class Failed(val diagnostic: GPUResourceDiagnostic) : GPUResourceLeaseFactoryResult
}

interface GPUResourceLeaseFactory {
    fun createUniformSlab(request: GPUUniformSlabLeaseRequest): GPUResourceLeaseFactoryResult

    fun createBindGroup(request: GPUBindGroupLeaseRequest): GPUResourceLeaseFactoryResult
}

object EvidenceOnlyGPUResourceLeaseFactory : GPUResourceLeaseFactory {
    override fun createUniformSlab(request: GPUUniformSlabLeaseRequest): GPUResourceLeaseFactoryResult =
        GPUResourceLeaseFactoryResult.Created(
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

    override fun createBindGroup(request: GPUBindGroupLeaseRequest): GPUResourceLeaseFactoryResult =
        GPUResourceLeaseFactoryResult.Created(
            GPUResourceLease(
                leaseId = request.leaseId,
                resourceKind = GPUResourceLeaseKind.BindGroup,
                deviceGeneration = request.deviceGeneration,
                descriptorHash = request.descriptorHash,
                ownerScope = request.ownerScope,
                usageLabels = request.dumpUsageLabelsSnapshot,
                releasePolicy = request.releasePolicy,
                cacheResult = GPUResourceLeaseCacheResult.Create,
            ),
        )
}
