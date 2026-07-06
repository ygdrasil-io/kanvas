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
    private val uniformSlabLeases = linkedMapOf<String, GPUResourceLease>()
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

        val key = listOf(
            request.leaseId,
            request.targetId,
            request.frameId,
            request.descriptorHash,
            request.deviceGeneration.toString(),
            request.totalBytes.toString(),
            request.alignmentBytes.toString(),
            request.payloadCount.toString(),
            request.releasePolicy,
        ).joinToString("|")
        uniformSlabLeases[key]?.let { cachedLease ->
            val lease = cachedLease.copy(cacheResult = GPUResourceLeaseCacheResult.Reuse)
            record("uniform-slab", lease.cacheResult.dumpToken, key, context.targetId)
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
                val lease = leaseResult.lease
                uniformSlabLeases[key] = lease
                record("uniform-slab", lease.cacheResult.dumpToken, key, context.targetId)
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
        val result = when (decision) {
            is GPUResourceMaterializationDecision.Materialized -> "create"
            is GPUResourceMaterializationDecision.Refused -> "failure"
            is GPUResourceMaterializationDecision.Deferred -> "deferred"
        }
        record("texture-sampler", result, request.bindingLayoutHash, request.binding.bindingLabel)
        return decision
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

private fun GPUResourceMaterializationDecision.payloadEvents(): List<GPUPayloadMaterializationTelemetryEvent> =
    when (this) {
        is GPUResourceMaterializationDecision.Materialized -> dumpPayloadTelemetrySnapshot
        is GPUResourceMaterializationDecision.Refused -> dumpPayloadTelemetrySnapshot
        is GPUResourceMaterializationDecision.Deferred -> emptyList()
    }

private val CONCRETE_PROVIDER_RAW_BACKEND_TOKEN = "w" + "gpu"
private val CONCRETE_PROVIDER_UNSAFE_DUMP_PATTERN =
    Regex("(?i)(@|0x[0-9a-f]{6,}|$CONCRETE_PROVIDER_RAW_BACKEND_TOKEN|externaltexturehandle|gpu[a-z0-9]*handle)")

private fun requireConcreteProviderDumpSafe(fieldName: String, value: String) {
    require(!CONCRETE_PROVIDER_UNSAFE_DUMP_PATTERN.containsMatchIn(value)) {
        "$fieldName must use dump-safe GPU evidence labels"
    }
}
