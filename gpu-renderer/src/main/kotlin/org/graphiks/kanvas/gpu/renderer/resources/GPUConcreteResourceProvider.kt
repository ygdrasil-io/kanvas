package org.graphiks.kanvas.gpu.renderer.resources

data class GPUNullBufferMaterializationRequest(
    val label: String,
    val byteSize: Long,
    val deviceGeneration: Long,
) {
    init {
        require(label.isNotBlank()) { "GPUNullBufferMaterializationRequest.label must not be blank" }
        requireResourceDumpSafe(
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
        requireResourceDumpSafe("GPUConcreteResourceProviderEvent.lane", lane)
        requireResourceDumpSafe("GPUConcreteResourceProviderEvent.result", result)
        requireResourceDumpSafe("GPUConcreteResourceProviderEvent.keyHash", keyHash)
        requireResourceDumpSafe("GPUConcreteResourceProviderEvent.subjectHash", subjectHash)
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
    private val bindGroupLeases = linkedMapOf<GPUBindGroupLeaseCacheKey, GPUResourceLease>()
    private val textureSamplerLeaseKeys = linkedSetOf<GPUTextureSamplerLeaseCacheKey>()
    private val intermediateTextureLeases = linkedMapOf<GPUIntermediateTextureLeaseCacheKey, GPUResourceLease>()
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
        val cacheResult = if (nullBufferKeys.add(key)) {
            GPUResourceLeaseCacheResult.Create
        } else {
            GPUResourceLeaseCacheResult.Reuse
        }
        record("null-buffer", cacheResult.dumpToken, key, context.targetId)
        val lease = GPUResourceLease(
            leaseId = "null-buffer:${request.label}",
            resourceKind = GPUResourceLeaseKind.NullBuffer,
            deviceGeneration = context.deviceGeneration,
            descriptorHash = "null-buffer:${request.byteSize}",
            ownerScope = "resource-provider:null-buffer",
            usageLabels = listOf("uniform"),
            releasePolicy = "device-generation",
            cacheResult = cacheResult,
            evidenceFacts = mapOf(
                "byteSize" to request.byteSize.toString(),
                "zeroFilled" to "true",
            ),
        )

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
            resourceLeases = listOf(lease),
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

    fun materializeBindGroupLease(
        request: GPUBindGroupLeaseRequest,
        context: GPUTargetPreparationContext,
    ): GPUResourceMaterializationDecision {
        if (request.deviceGeneration != context.deviceGeneration) {
            val diagnostic = GPUResourceDiagnostic.deviceGenerationStale(
                resourceLabel = request.leaseId,
                expectedDeviceGeneration = context.deviceGeneration,
                actualDeviceGeneration = request.deviceGeneration,
                resourceKind = "resource",
            )
            record("bind-group", "stale-generation", request.leaseId, context.targetId)
            return GPUResourceMaterializationDecision.Refused(
                diagnostic = diagnostic,
                targetId = context.targetId,
                resourcePlanLabels = listOf(request.leaseId),
            )
        }

        val key = GPUBindGroupLeaseCacheKey.from(request)
        bindGroupLeases[key]?.let { cachedLease ->
            val lease = cachedLease.copy(cacheResult = GPUResourceLeaseCacheResult.Reuse)
            record("bind-group", lease.cacheResult.dumpToken, key.dumpToken(), context.targetId)
            return GPUResourceMaterializationDecision.Materialized(
                resources = emptyList(),
                targetId = context.targetId,
                resourcePlanLabels = listOf(request.leaseId),
                resourceLeases = listOf(lease),
            )
        }

        return when (val leaseResult = leaseFactory.createBindGroup(request)) {
            is GPUResourceLeaseFactoryResult.Failed -> {
                record("bind-group", "adapter-failure", request.leaseId, context.targetId)
                GPUResourceMaterializationDecision.Refused(
                    diagnostic = leaseResult.diagnostic,
                    targetId = context.targetId,
                    resourcePlanLabels = listOf(request.leaseId),
                )
            }
            is GPUResourceLeaseFactoryResult.Created -> {
                val lease = leaseResult.lease.snapshotForBindGroupCache()
                bindGroupLeases[key] = lease
                record("bind-group", lease.cacheResult.dumpToken, key.dumpToken(), context.targetId)
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

    fun materializeIntermediateTexture(
        request: GPUIntermediateTextureMaterializationRequest,
        context: GPUTargetPreparationContext,
    ): GPUResourceMaterializationDecision {
        val descriptor = request.validatedDescriptor
        val diagnostic = when {
            request.targetId != context.targetId ->
                GPUResourceDiagnostic.resourceTargetMismatch(
                    resourceLabel = descriptor.label,
                    requestTargetId = request.targetId,
                    contextTargetId = context.targetId,
                )
            request.deviceGeneration != context.deviceGeneration ->
                GPUResourceDiagnostic.deviceGenerationStale(
                    resourceLabel = descriptor.label,
                    expectedDeviceGeneration = context.deviceGeneration,
                    actualDeviceGeneration = request.deviceGeneration,
                    resourceKind = "intermediate",
                )
            request.actualResourceGeneration != descriptor.generation ->
                GPUResourceDiagnostic(
                    code = "unsupported.intermediate.generation_stale",
                    resourceLabel = descriptor.label,
                    message = "intermediate generation ${request.actualResourceGeneration} != descriptor generation ${descriptor.generation}",
                    terminal = true,
                )
            request.activeAttachmentSampled ->
                GPUResourceDiagnostic(
                    code = "unsupported.destination_read.active_attachment_sampled",
                    resourceLabel = descriptor.label,
                    message = "intermediate texture would sample the active attachment",
                    terminal = true,
                )
            (request.requiredUsageLabels - descriptor.usageLabels.toSet()).isNotEmpty() ->
                GPUResourceDiagnostic.textureUsageMissing(
                    resourceLabel = descriptor.label,
                    missingUsageLabels = request.requiredUsageLabels - descriptor.usageLabels.toSet(),
                    availableUsageLabels = descriptor.usageLabels.toSet(),
                )
            else -> null
        }
        if (diagnostic != null) {
            record("intermediate-texture", "refuse", descriptor.descriptorHash, descriptor.label)
            return GPUResourceMaterializationDecision.Refused(
                diagnostic = diagnostic,
                targetId = context.targetId,
                resourcePlanLabels = listOf(descriptor.label),
            )
        }

        val key = GPUIntermediateTextureLeaseCacheKey.from(request)
        intermediateTextureLeases[key]?.let { cachedLease ->
            val lease = cachedLease.copy(cacheResult = GPUResourceLeaseCacheResult.Reuse)
            record("intermediate-texture", lease.cacheResult.dumpToken, key.dumpToken(), descriptor.label)
            return GPUResourceMaterializationDecision.Materialized(
                resources = listOf(GPUTextureResourceRef("texture-ref:${descriptor.label}")),
                targetId = context.targetId,
                resourcePlanLabels = listOf(descriptor.label),
                resourceLeases = listOf(lease),
            )
        }

        val lease = GPUResourceLease(
            leaseId = "intermediate:${descriptor.label}",
            resourceKind = GPUResourceLeaseKind.Texture,
            deviceGeneration = context.deviceGeneration,
            descriptorHash = descriptor.descriptorHash,
            ownerScope = descriptor.ownerScope,
            usageLabels = descriptor.usageLabels,
            releasePolicy = descriptor.lifetimeClass,
            cacheResult = GPUResourceLeaseCacheResult.Create,
            evidenceFacts = mapOf(
                "purpose" to descriptor.purposeLabel,
                "bounds" to descriptor.boundsLabel,
                "sampleCount" to descriptor.sampleCount.toString(),
            ),
        )
        intermediateTextureLeases[key] = lease
        record("intermediate-texture", lease.cacheResult.dumpToken, key.dumpToken(), descriptor.label)
        return GPUResourceMaterializationDecision.Materialized(
            resources = listOf(GPUTextureResourceRef("texture-ref:${descriptor.label}")),
            targetId = context.targetId,
            resourcePlanLabels = listOf(descriptor.label),
            resourceLeases = listOf(lease),
        )
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
                decision.withResourceLeases(leases)
            }
            is GPUResourceMaterializationDecision.Refused -> {
                record(
                    lane = "texture-sampler",
                    result = GPUResourceLeaseCacheResult.Refuse.dumpToken,
                    keyHash = request.bindingLayoutHash,
                    subjectHash = request.binding.bindingLabel,
                )
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

private data class GPUBindGroupLeaseCacheKey(
    val leaseId: String,
    val descriptorHash: String,
    val ownerScope: String,
    val usageLabels: List<String>,
    val deviceGeneration: Long,
    val releasePolicy: String,
) {
    fun dumpToken(): String =
        "lease=$leaseId;descriptor=$descriptorHash;owner=$ownerScope;" +
            "usage=${usageLabels.joinToString("+")};deviceGeneration=$deviceGeneration;release=$releasePolicy"

    companion object {
        fun from(request: GPUBindGroupLeaseRequest): GPUBindGroupLeaseCacheKey =
            GPUBindGroupLeaseCacheKey(
                leaseId = request.leaseId,
                descriptorHash = request.descriptorHash,
                ownerScope = request.ownerScope,
                usageLabels = request.usageLabels.sorted(),
                deviceGeneration = request.deviceGeneration,
                releasePolicy = request.releasePolicy,
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
    val bindingLabel: String,
    val ownerLabel: String,
    val lifetimeClass: String,
    val releasePolicy: String,
    val canAliasScratch: Boolean,
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
        "target=$targetId;layout=$bindingLayoutHash;binding=$bindingLabel;owner=$ownerLabel;" +
            "lifetime=$lifetimeClass;release=$releasePolicy;canAliasScratch=$canAliasScratch;" +
            "texture=${textureWidth}x$textureHeight:$textureFormat:" +
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
                bindingLabel = request.binding.bindingLabel,
                ownerLabel = request.ownership.ownerLabel,
                lifetimeClass = request.ownership.lifetimeClass,
                releasePolicy = request.ownership.releasePolicy,
                canAliasScratch = request.ownership.canAliasScratch,
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

private fun GPUResourceLease.snapshotForBindGroupCache(): GPUResourceLease =
    copy(
        usageLabels = dumpUsageLabelsSnapshot,
        evidenceFacts = dumpEvidenceFactsSnapshot,
    )

private fun GPUResourceMaterializationDecision.Materialized.withResourceLeases(
    leases: List<GPUResourceLease>,
): GPUResourceMaterializationDecision.Materialized = copy(resourceLeases = leases)

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
