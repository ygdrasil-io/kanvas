package org.graphiks.kanvas.gpu.renderer.resources

data class GPUNullBufferMaterializationRequest(
    val label: String,
    val byteSize: Long,
    val deviceGeneration: Long,
) {
    init {
        require(label.isNotBlank()) { "GPUNullBufferMaterializationRequest.label must not be blank" }
        requireConcreteProviderDumpSafe(
            fieldName = "GPUNullBufferMaterializationRequest.label",
            value = label,
        )
        require(byteSize > 0L) { "GPUNullBufferMaterializationRequest.byteSize must be positive" }
        require(deviceGeneration >= 0L) {
            "GPUNullBufferMaterializationRequest.deviceGeneration must be non-negative"
        }
    }
}

data class GPUConcreteResourceProviderEvent(
    val lane: String,
    val result: String,
    val keyHash: String,
    val subjectHash: String,
) {
    init {
        require(lane.isNotBlank()) { "GPUConcreteResourceProviderEvent.lane must not be blank" }
        require(result.isNotBlank()) { "GPUConcreteResourceProviderEvent.result must not be blank" }
        require(keyHash.isNotBlank()) { "GPUConcreteResourceProviderEvent.keyHash must not be blank" }
        require(subjectHash.isNotBlank()) { "GPUConcreteResourceProviderEvent.subjectHash must not be blank" }
        requireConcreteProviderDumpSafe("GPUConcreteResourceProviderEvent.lane", lane)
        requireConcreteProviderDumpSafe("GPUConcreteResourceProviderEvent.result", result)
        requireConcreteProviderDumpSafe("GPUConcreteResourceProviderEvent.keyHash", keyHash)
        requireConcreteProviderDumpSafe("GPUConcreteResourceProviderEvent.subjectHash", subjectHash)
    }
}

class GPUConcreteResourceProviderTelemetry(
    events: List<GPUConcreteResourceProviderEvent> = emptyList(),
) {
    val dumpEvents: List<GPUConcreteResourceProviderEvent> = events.toList()

    fun plus(event: GPUConcreteResourceProviderEvent): GPUConcreteResourceProviderTelemetry =
        GPUConcreteResourceProviderTelemetry(dumpEvents + event)

    fun dumpLines(): List<String> =
        dumpEvents.map { event ->
            "resource-provider.cache lane=${event.lane} result=${event.result} " +
                "key=${event.keyHash} subject=${event.subjectHash}"
        }
}

class GPUConcreteResourceProvider(
    private val payloadProvider: GPUResourceProvider = ValidatingPayloadResourceProvider(),
    private val textureSamplerProvider: GPUResourceProvider = ValidatingTextureSamplerResourceProvider(),
    private val leaseFactory: GPUResourceLeaseFactory = EvidenceOnlyGPUResourceLeaseFactory,
) : GPUResourceProvider {
    private val nullBufferKeys = linkedSetOf<String>()
    private val uniformSlabLeases = linkedMapOf<GPUUniformSlabLeaseCacheKey, GPUResourceLease>()
    private val textureSamplerLeaseKeys = linkedSetOf<GPUTextureSamplerLeaseCacheKey>()
    private var mutableTelemetry = GPUConcreteResourceProviderTelemetry()

    val telemetry: GPUConcreteResourceProviderTelemetry
        get() = mutableTelemetry

    fun materializeNullBuffer(
        request: GPUNullBufferMaterializationRequest,
        context: GPUTargetPreparationContext,
    ): GPUResourceMaterializationDecision {
        if (request.deviceGeneration != context.deviceGeneration) {
            val diagnostic = GPUResourceDiagnostic.deviceGenerationStale(
                resourceLabel = request.label,
                expectedDeviceGeneration = context.deviceGeneration,
                actualDeviceGeneration = request.deviceGeneration,
                resourceKind = "resource",
            )
            record("null-buffer", "stale-generation", request.label, context.targetId)
            return GPUResourceMaterializationDecision.Refused(
                diagnostic = diagnostic,
                targetId = context.targetId,
                resourcePlanLabels = listOf(request.label),
            )
        }

        val key = "${context.deviceGeneration}:${request.label}:${request.byteSize}"
        val result = if (nullBufferKeys.add(key)) "create" else "reuse"
        record("null-buffer", result, key, context.targetId)

        val operand = GPUMaterializedCommandOperandReference(
            label = "null-buffer:${request.label}",
            kind = GPUMaterializedCommandOperandKind.UniformBuffer,
            descriptorHash = "null-buffer:${request.byteSize}",
            deviceGeneration = context.deviceGeneration,
            ownerScope = "resource-provider:null-buffer",
            usageLabels = listOf("uniform"),
            invalidationPolicy = "device-generation",
            evidenceFacts = mapOf(
                "byteSize" to request.byteSize.toString(),
                "zeroFilled" to "true",
            ),
        )

        return GPUResourceMaterializationDecision.Materialized(
            resources = emptyList(),
            targetId = context.targetId,
            resourcePlanLabels = listOf(request.label),
            operandBridge = listOf(
                GPUMaterializedCommandOperandBinding(
                    commandLabel = "setBindGroup",
                    operand = operand,
                ),
            ),
        )
    }

    fun materializeFullscreenUniformSlabLease(
        request: GPUUniformSlabLeaseRequest,
        context: GPUTargetPreparationContext,
    ): GPUResourceMaterializationDecision {
        if (request.targetId != context.targetId) {
            val diagnostic = GPUResourceDiagnostic.resourceTargetMismatch(
                resourceLabel = request.leaseId,
                requestTargetId = request.targetId,
                contextTargetId = context.targetId,
            )
            record("uniform-slab", "target-mismatch", request.leaseId, context.targetId)
            return GPUResourceMaterializationDecision.Refused(
                diagnostic = diagnostic,
                targetId = context.targetId,
                resourcePlanLabels = listOf(request.leaseId),
            )
        }

        if (request.deviceGeneration != context.deviceGeneration) {
            val diagnostic = GPUResourceDiagnostic.deviceGenerationStale(
                resourceLabel = request.leaseId,
                expectedDeviceGeneration = context.deviceGeneration,
                actualDeviceGeneration = request.deviceGeneration,
                resourceKind = "resource",
            )
            record("uniform-slab", "stale-generation", request.leaseId, context.targetId)
            return GPUResourceMaterializationDecision.Refused(
                diagnostic = diagnostic,
                targetId = context.targetId,
                resourcePlanLabels = listOf(request.leaseId),
            )
        }

        val key = GPUUniformSlabLeaseCacheKey.from(request)
        uniformSlabLeases[key]?.let { cachedLease ->
            val lease = cachedLease.copy(cacheResult = GPUResourceLeaseCacheResult.Reuse)
            record("uniform-slab", lease.cacheResult.dumpToken, key.dumpToken(), context.targetId)
            return GPUResourceMaterializationDecision.Materialized(
                resources = emptyList(),
                targetId = context.targetId,
                resourcePlanLabels = listOf(request.leaseId),
                resourceLeases = listOf(lease),
            )
        }

        return when (val leaseResult = leaseFactory.createUniformSlab(request)) {
            is GPUResourceLeaseFactoryResult.Failed -> {
                record("uniform-slab", "adapter-failure", request.leaseId, context.targetId)
                GPUResourceMaterializationDecision.Refused(
                    diagnostic = leaseResult.diagnostic,
                    targetId = context.targetId,
                    resourcePlanLabels = listOf(request.leaseId),
                )
            }
            is GPUResourceLeaseFactoryResult.Created -> {
                val lease = leaseResult.lease.snapshotForUniformSlabCache()
                uniformSlabLeases[key] = lease
                record("uniform-slab", lease.cacheResult.dumpToken, key.dumpToken(), context.targetId)
                GPUResourceMaterializationDecision.Materialized(
                    resources = emptyList(),
                    targetId = context.targetId,
                    resourcePlanLabels = listOf(request.leaseId),
                    resourceLeases = listOf(lease),
                )
            }
        }
    }

    override fun materializePayloadBindings(
        request: GPUPayloadMaterializationRequest,
        context: GPUTargetPreparationContext,
    ): GPUResourceMaterializationDecision {
        val decision = payloadProvider.materializePayloadBindings(request, context)
        decision.payloadEvents().forEach { event ->
            record(
                lane = event.lane,
                result = event.result.dumpToken,
                keyHash = event.keyHash,
                subjectHash = event.subjectHash,
            )
        }
        return decision
    }

    override fun materializeTextureSamplerBinding(
        request: GPUTextureSamplerMaterializationRequest,
        context: GPUTargetPreparationContext,
    ): GPUResourceMaterializationDecision {
        val decision = textureSamplerProvider.materializeTextureSamplerBinding(request, context)
        return when (decision) {
            is GPUResourceMaterializationDecision.Materialized -> {
                val cacheKey = GPUTextureSamplerLeaseCacheKey.from(request)
                val cacheResult = if (textureSamplerLeaseKeys.add(cacheKey)) {
                    GPUResourceLeaseCacheResult.Create
                } else {
                    GPUResourceLeaseCacheResult.Reuse
                }
                val leases = listOf(
                    GPUResourceLease(
                        leaseId = "texture:${request.ownership.ownerLabel}",
                        resourceKind = GPUResourceLeaseKind.Texture,
                        deviceGeneration = request.deviceGeneration,
                        descriptorHash = request.textureDescriptor.materializationDescriptorHashForProvider(),
                        ownerScope = request.ownership.ownerLabel,
                        usageLabels = request.requiredTextureUsageLabelsForProvider(),
                        releasePolicy = request.ownership.releasePolicy,
                        cacheResult = cacheResult,
                    ),
                    GPUResourceLease(
                        leaseId = "texture-view:${request.binding.bindingLabel}",
                        resourceKind = GPUResourceLeaseKind.TextureView,
                        deviceGeneration = request.deviceGeneration,
                        descriptorHash = request.viewDescriptorHashForProvider(),
                        ownerScope = request.ownership.ownerLabel,
                        usageLabels = listOf("texture_binding"),
                        releasePolicy = request.ownership.releasePolicy,
                        cacheResult = cacheResult,
                    ),
                    GPUResourceLease(
                        leaseId = "sampler:${request.binding.bindingLabel}",
                        resourceKind = GPUResourceLeaseKind.Sampler,
                        deviceGeneration = request.deviceGeneration,
                        descriptorHash = request.samplerDescriptorHashForProvider(),
                        ownerScope = "sampler-cache",
                        usageLabels = listOf("sampler"),
                        releasePolicy = "descriptor-cache",
                        cacheResult = cacheResult,
                    ),
                )
                record(
                    lane = "texture-sampler",
                    result = cacheResult.dumpToken,
                    keyHash = cacheKey.dumpToken(),
                    subjectHash = request.binding.bindingLabel,
                )
                decision.copyWithResourceLeases(leases)
            }
            is GPUResourceMaterializationDecision.Refused -> {
                record("texture-sampler", "failure", request.bindingLayoutHash, request.binding.bindingLabel)
                decision
            }
            is GPUResourceMaterializationDecision.Deferred -> {
                record("texture-sampler", "deferred", request.bindingLayoutHash, request.binding.bindingLabel)
                decision
            }
        }
    }

    private fun record(lane: String, result: String, keyHash: String, subjectHash: String) {
        mutableTelemetry = mutableTelemetry.plus(
            GPUConcreteResourceProviderEvent(
                lane = lane,
                result = result,
                keyHash = keyHash,
                subjectHash = subjectHash,
            ),
        )
    }
}

private data class GPUUniformSlabLeaseCacheKey(
    val leaseId: String,
    val targetId: String,
    val frameId: String,
    val descriptorHash: String,
    val deviceGeneration: Long,
    val totalBytes: Long,
    val alignmentBytes: Long,
    val payloadCount: Int,
    val releasePolicy: String,
) {
    fun dumpToken(): String =
        "lease=$leaseId;target=$targetId;frame=$frameId;descriptor=$descriptorHash;" +
            "deviceGeneration=$deviceGeneration;totalBytes=$totalBytes;" +
            "alignmentBytes=$alignmentBytes;payloadCount=$payloadCount;release=$releasePolicy"

    companion object {
        fun from(request: GPUUniformSlabLeaseRequest): GPUUniformSlabLeaseCacheKey =
            GPUUniformSlabLeaseCacheKey(
                leaseId = request.leaseId,
                targetId = request.targetId,
                frameId = request.frameId,
                descriptorHash = request.descriptorHash,
                deviceGeneration = request.deviceGeneration,
                totalBytes = request.totalBytes,
                alignmentBytes = request.alignmentBytes,
                payloadCount = request.payloadCount,
                releasePolicy = request.releasePolicy,
            )
    }
}

private data class GPUTextureSamplerLeaseCacheKey(
    val targetId: String,
    val bindingLayoutHash: String,
    val textureWidth: Int,
    val textureHeight: Int,
    val textureFormat: String,
    val textureSampleCount: Int,
    val textureUsageLabels: List<String>,
    val viewTextureDescriptorHash: String,
    val viewDimension: String,
    val viewMipRangeFirst: Int,
    val viewMipRangeLast: Int,
    val viewArrayLayerRangeFirst: Int,
    val viewArrayLayerRangeLast: Int,
    val samplerAddressModeU: String,
    val samplerAddressModeV: String,
    val samplerMagFilter: String,
    val samplerMinFilter: String,
    val samplerMipmapFilter: String,
    val samplerLodMinClamp: String,
    val samplerLodMaxClamp: String,
    val samplerCompareMode: String,
    val samplerMaxAnisotropy: Int,
    val samplerCapabilityRequirements: List<String>,
    val deviceGeneration: Long,
    val actualResourceGeneration: Long,
) {
    fun dumpToken(): String =
        "target=$targetId;layout=$bindingLayoutHash;texture=${textureWidth}x$textureHeight:$textureFormat:" +
            "samples=$textureSampleCount:usage=${textureUsageLabels.joinToString("+")};" +
            "view=$viewTextureDescriptorHash:$viewDimension:$viewMipRangeFirst..$viewMipRangeLast:" +
            "$viewArrayLayerRangeFirst..$viewArrayLayerRangeLast;" +
            "sampler=$samplerAddressModeU:$samplerAddressModeV:$samplerMagFilter:$samplerMinFilter:" +
            "$samplerMipmapFilter:$samplerLodMinClamp:$samplerLodMaxClamp:$samplerCompareMode:" +
            "$samplerMaxAnisotropy:${samplerCapabilityRequirements.joinToString("+")};" +
            "deviceGeneration=$deviceGeneration;resourceGeneration=$actualResourceGeneration"

    companion object {
        fun from(request: GPUTextureSamplerMaterializationRequest): GPUTextureSamplerLeaseCacheKey =
            GPUTextureSamplerLeaseCacheKey(
                targetId = request.targetId,
                bindingLayoutHash = request.bindingLayoutHash,
                textureWidth = request.textureDescriptor.width,
                textureHeight = request.textureDescriptor.height,
                textureFormat = request.textureDescriptor.format,
                textureSampleCount = request.textureDescriptor.sampleCount,
                textureUsageLabels = request.textureDescriptor.usageLabels.sorted(),
                viewTextureDescriptorHash = request.viewDescriptor.textureDescriptorHash,
                viewDimension = request.viewDescriptor.viewDimension,
                viewMipRangeFirst = request.viewDescriptor.mipRange.first,
                viewMipRangeLast = request.viewDescriptor.mipRange.last,
                viewArrayLayerRangeFirst = request.viewDescriptor.arrayLayerRange.first,
                viewArrayLayerRangeLast = request.viewDescriptor.arrayLayerRange.last,
                samplerAddressModeU = request.samplerDescriptor.addressModeU,
                samplerAddressModeV = request.samplerDescriptor.addressModeV,
                samplerMagFilter = request.samplerDescriptor.magFilter,
                samplerMinFilter = request.samplerDescriptor.minFilter,
                samplerMipmapFilter = request.samplerDescriptor.mipmapFilter,
                samplerLodMinClamp = request.samplerDescriptor.lodMinClamp,
                samplerLodMaxClamp = request.samplerDescriptor.lodMaxClamp,
                samplerCompareMode = request.samplerDescriptor.compareMode,
                samplerMaxAnisotropy = request.samplerDescriptor.maxAnisotropy,
                samplerCapabilityRequirements = request.samplerDescriptor.capabilityRequirements.sorted(),
                deviceGeneration = request.deviceGeneration,
                actualResourceGeneration = request.actualResourceGeneration,
            )
    }
}

private fun GPUResourceLease.snapshotForUniformSlabCache(): GPUResourceLease =
    copy(
        usageLabels = dumpUsageLabelsSnapshot,
        evidenceFacts = dumpEvidenceFactsSnapshot,
    )

private fun GPUResourceMaterializationDecision.copyWithResourceLeases(
    leases: List<GPUResourceLease>,
): GPUResourceMaterializationDecision =
    when (this) {
        is GPUResourceMaterializationDecision.Materialized -> copy(resourceLeases = leases)
        is GPUResourceMaterializationDecision.Refused -> copy(resourceLeases = leases)
        is GPUResourceMaterializationDecision.Deferred -> this
    }

private fun GPUResourceMaterializationDecision.payloadEvents(): List<GPUPayloadMaterializationTelemetryEvent> =
    when (this) {
        is GPUResourceMaterializationDecision.Materialized -> dumpPayloadTelemetrySnapshot
        is GPUResourceMaterializationDecision.Refused -> dumpPayloadTelemetrySnapshot
        is GPUResourceMaterializationDecision.Deferred -> emptyList()
    }

private fun GPUTextureDescriptor.materializationDescriptorHashForProvider(): String =
    listOf(
        "texture",
        "${width}x$height",
        format,
        "samples=$sampleCount",
        usageLabels.sorted().joinToString("+"),
    ).joinToString(":")

private fun GPUTextureSamplerMaterializationRequest.requiredTextureUsageLabelsForProvider(): List<String> =
    dumpRequiredTextureUsageLabelsSnapshot.sorted()

private fun GPUTextureSamplerMaterializationRequest.viewDescriptorHashForProvider(): String =
    listOf(
        "texture-view",
        viewDescriptor.textureDescriptorHash,
        viewDescriptor.viewDimension,
        viewDescriptor.mipRange.toString(),
        viewDescriptor.arrayLayerRange.toString(),
    ).joinToString(":")

private fun GPUTextureSamplerMaterializationRequest.samplerDescriptorHashForProvider(): String =
    listOf(
        "sampler",
        samplerDescriptor.addressModeU,
        samplerDescriptor.addressModeV,
        samplerDescriptor.magFilter,
        samplerDescriptor.minFilter,
        samplerDescriptor.mipmapFilter,
        samplerDescriptor.lodMinClamp,
        samplerDescriptor.lodMaxClamp,
        samplerDescriptor.compareMode,
        samplerDescriptor.maxAnisotropy.toString(),
        samplerDescriptor.capabilityRequirements.sorted().joinToString("+"),
    ).joinToString(":")

private val CONCRETE_PROVIDER_RAW_BACKEND_TOKEN = "w" + "gpu"
private val CONCRETE_PROVIDER_UNSAFE_DUMP_PATTERN =
    Regex("(?i)(@|0x[0-9a-f]{6,}|$CONCRETE_PROVIDER_RAW_BACKEND_TOKEN|externaltexturehandle|gpu[a-z0-9]*handle)")

private fun requireConcreteProviderDumpSafe(fieldName: String, value: String) {
    require(!CONCRETE_PROVIDER_UNSAFE_DUMP_PATTERN.containsMatchIn(value)) {
        "$fieldName must use dump-safe GPU evidence labels"
    }
}
