package org.graphiks.kanvas.gpu.renderer.resources

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPayloadFingerprint
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPayloadSlotID
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPayloadUploadPlan
import org.graphiks.kanvas.gpu.renderer.payloads.GPUResourceBindingBlock
import org.graphiks.kanvas.gpu.renderer.payloads.GPUResourceBindingSlot
import org.graphiks.kanvas.gpu.renderer.payloads.GPUUniformPayloadBlock
import org.graphiks.kanvas.gpu.renderer.payloads.GPUUniformPayloadSlot

class GPUConcreteResourceProviderTest {
    @Test
    fun `concrete provider materializes null buffer once per generation`() {
        val provider = GPUConcreteResourceProvider()
        val context = targetPreparationContext()

        val first = assertIs<GPUResourceMaterializationDecision.Materialized>(
            provider.materializeNullBuffer(
                GPUNullBufferMaterializationRequest("null-uniform", 16, context.deviceGeneration),
                context,
            ),
        )
        val second = assertIs<GPUResourceMaterializationDecision.Materialized>(
            provider.materializeNullBuffer(
                GPUNullBufferMaterializationRequest("null-uniform", 16, context.deviceGeneration),
                context,
            ),
        )

        assertEquals(listOf("create", "reuse"), provider.telemetry.dumpEvents.map { it.result })
        assertEquals(
            first.dumpOperandBridgeSnapshot.single().operand.label,
            second.dumpOperandBridgeSnapshot.single().operand.label,
        )
    }

    @Test
    fun `concrete provider refuses stale null buffer generation`() {
        val provider = GPUConcreteResourceProvider()
        val decision = provider.materializeNullBuffer(
            GPUNullBufferMaterializationRequest("null-uniform", 16, deviceGeneration = 7),
            targetPreparationContext(deviceGeneration = 8),
        )

        val refused = assertIs<GPUResourceMaterializationDecision.Refused>(decision)
        assertEquals("unsupported.resource.device_generation_stale", refused.diagnostic.code)
    }

    @Test
    fun `concrete provider retries fullscreen uniform slab creation after factory failure`() {
        var createCalls = 0
        val provider = GPUConcreteResourceProvider(
            leaseFactory = object : GPUResourceLeaseFactory {
                override fun createUniformSlab(
                    request: GPUUniformSlabLeaseRequest,
                ): GPUResourceLeaseFactoryResult {
                    createCalls += 1
                    return if (createCalls == 1) {
                        GPUResourceLeaseFactoryResult.Failed(
                            diagnostic = GPUResourceDiagnostic.adapterCreateFailed(
                                resourceLabel = request.leaseId,
                                reason = "allocation-denied",
                            ),
                        )
                    } else {
                        EvidenceOnlyGPUResourceLeaseFactory.createUniformSlab(request)
                    }
                }

                override fun createBindGroup(
                    request: GPUBindGroupLeaseRequest,
                ): GPUResourceLeaseFactoryResult =
                    EvidenceOnlyGPUResourceLeaseFactory.createBindGroup(request)
            },
        )
        val context = targetPreparationContext()

        val first = assertIs<GPUResourceMaterializationDecision.Refused>(
            provider.materializeFullscreenUniformSlabLease(
                request = fullscreenUniformSlabLeaseRequest(),
                context = context,
            ),
        )
        val second = assertIs<GPUResourceMaterializationDecision.Materialized>(
            provider.materializeFullscreenUniformSlabLease(
                request = fullscreenUniformSlabLeaseRequest(),
                context = context,
            ),
        )

        assertEquals("unsupported.resource.adapter_create_failed", first.diagnostic.code)
        assertEquals(GPUResourceLeaseCacheResult.Create, second.dumpResourceLeaseSnapshot.single().cacheResult)
        assertEquals(
            listOf("adapter-failure", "create"),
            provider.telemetry.dumpEvents
                .filter { event -> event.lane == "uniform-slab" }
                .map { event -> event.result },
        )
    }

    @Test
    fun `concrete provider preserves custom fullscreen uniform slab lease evidence on reuse`() {
        var createCalls = 0
        val request = fullscreenUniformSlabLeaseRequest()
        val provider = GPUConcreteResourceProvider(
            leaseFactory = object : GPUResourceLeaseFactory {
                override fun createUniformSlab(
                    request: GPUUniformSlabLeaseRequest,
                ): GPUResourceLeaseFactoryResult {
                    createCalls += 1
                    return GPUResourceLeaseFactoryResult.Created(
                        customFullscreenUniformSlabLease(request),
                    )
                }

                override fun createBindGroup(
                    request: GPUBindGroupLeaseRequest,
                ): GPUResourceLeaseFactoryResult =
                    EvidenceOnlyGPUResourceLeaseFactory.createBindGroup(request)
            },
        )
        val context = targetPreparationContext()

        val first = assertIs<GPUResourceMaterializationDecision.Materialized>(
            provider.materializeFullscreenUniformSlabLease(
                request = request,
                context = context,
            ),
        )
        val second = assertIs<GPUResourceMaterializationDecision.Materialized>(
            provider.materializeFullscreenUniformSlabLease(
                request = request,
                context = context,
            ),
        )

        val createdLease = customFullscreenUniformSlabLease(request)
        assertEquals(1, createCalls)
        assertEquals(createdLease, first.dumpResourceLeaseSnapshot.single())
        assertEquals(
            createdLease.copy(cacheResult = GPUResourceLeaseCacheResult.Reuse),
            second.dumpResourceLeaseSnapshot.single(),
        )
        assertEquals(
            listOf("create", "reuse"),
            provider.telemetry.dumpEvents
                .filter { event -> event.lane == "uniform-slab" }
                .map { event -> event.result },
        )
    }

    @Test
    fun `concrete provider records texture sampler create then reuse`() {
        val provider = GPUConcreteResourceProvider()
        val context = targetPreparationContext()

        val first = assertIs<GPUResourceMaterializationDecision.Materialized>(
            provider.materializeTextureSamplerBinding(textureSamplerRequest(), context),
        )
        val second = assertIs<GPUResourceMaterializationDecision.Materialized>(
            provider.materializeTextureSamplerBinding(textureSamplerRequest(), context),
        )

        assertEquals(3, first.dumpResourceLeaseSnapshot.size)
        assertEquals(
            listOf("create", "reuse"),
            provider.telemetry.dumpEvents
                .filter { event -> event.lane == "texture-sampler" }
                .map { event -> event.result },
        )
        assertEquals(
            setOf(GPUResourceLeaseCacheResult.Reuse),
            second.dumpResourceLeaseSnapshot.map { lease -> lease.cacheResult }.toSet(),
        )
    }

    @Test
    fun `concrete provider refuses fullscreen uniform slab target mismatch`() {
        var createCalls = 0
        val provider = GPUConcreteResourceProvider(
            leaseFactory = object : GPUResourceLeaseFactory {
                override fun createUniformSlab(
                    request: GPUUniformSlabLeaseRequest,
                ): GPUResourceLeaseFactoryResult {
                    createCalls += 1
                    return EvidenceOnlyGPUResourceLeaseFactory.createUniformSlab(request)
                }

                override fun createBindGroup(
                    request: GPUBindGroupLeaseRequest,
                ): GPUResourceLeaseFactoryResult =
                    EvidenceOnlyGPUResourceLeaseFactory.createBindGroup(request)
            },
        )
        val refused = assertIs<GPUResourceMaterializationDecision.Refused>(
            provider.materializeFullscreenUniformSlabLease(
                request = fullscreenUniformSlabLeaseRequest(targetId = "other-target"),
                context = targetPreparationContext(),
            ),
        )

        assertEquals(0, createCalls)
        assertEquals("unsupported.resource.target_mismatch", refused.diagnostic.code)
        assertEquals("root-target", refused.targetId)
        assertEquals(listOf("uniform-slab:fullscreen:frame-1"), refused.dumpResourcePlanLabelsSnapshot)
        assertEquals(
            listOf("target-mismatch"),
            provider.telemetry.dumpEvents
                .filter { event -> event.lane == "uniform-slab" }
                .map { event -> event.result },
        )
    }

    @Test
    fun `concrete provider snapshots mutable fullscreen uniform slab lease before reuse`() {
        val usageLabels = mutableListOf("copy_dst", "uniform")
        val evidenceFacts = mutableMapOf(
            "factory" to "mutable",
            "payloadCount" to "one",
        )
        val request = fullscreenUniformSlabLeaseRequest()
        val provider = GPUConcreteResourceProvider(
            leaseFactory = object : GPUResourceLeaseFactory {
                override fun createUniformSlab(
                    request: GPUUniformSlabLeaseRequest,
                ): GPUResourceLeaseFactoryResult =
                    GPUResourceLeaseFactoryResult.Created(
                        GPUResourceLease(
                            leaseId = request.leaseId,
                            resourceKind = GPUResourceLeaseKind.UniformSlab,
                            deviceGeneration = request.deviceGeneration,
                            descriptorHash = request.descriptorHash,
                            ownerScope = request.frameId,
                            usageLabels = usageLabels,
                            releasePolicy = request.releasePolicy,
                            cacheResult = GPUResourceLeaseCacheResult.Create,
                            evidenceFacts = evidenceFacts,
                        ),
                    )

                override fun createBindGroup(
                    request: GPUBindGroupLeaseRequest,
                ): GPUResourceLeaseFactoryResult =
                    EvidenceOnlyGPUResourceLeaseFactory.createBindGroup(request)
            },
        )
        val context = targetPreparationContext()

        val first = assertIs<GPUResourceMaterializationDecision.Materialized>(
            provider.materializeFullscreenUniformSlabLease(
                request = request,
                context = context,
            ),
        )
        usageLabels += "storage"
        evidenceFacts["factory"] = "changed"
        evidenceFacts["extra"] = "changed"
        val second = assertIs<GPUResourceMaterializationDecision.Materialized>(
            provider.materializeFullscreenUniformSlabLease(
                request = request,
                context = context,
            ),
        )

        assertEquals(
            listOf("copy_dst", "uniform"),
            first.dumpResourceLeaseSnapshot.single().usageLabels,
        )
        assertEquals(
            mapOf(
                "factory" to "mutable",
                "payloadCount" to "one",
            ),
            first.dumpResourceLeaseSnapshot.single().evidenceFacts,
        )
        assertEquals(
            listOf("copy_dst", "uniform"),
            second.dumpResourceLeaseSnapshot.single().usageLabels,
        )
        assertEquals(
            mapOf(
                "factory" to "mutable",
                "payloadCount" to "one",
            ),
            second.dumpResourceLeaseSnapshot.single().evidenceFacts,
        )
        assertEquals(GPUResourceLeaseCacheResult.Reuse, second.dumpResourceLeaseSnapshot.single().cacheResult)
        assertEquals(
            listOf(
                "resource-provider.lease id=uniform-slab:fullscreen:frame-1 kind=uniform-slab result=reuse " +
                    "deviceGeneration=11 owner=frame-1 release=submission-complete usage=copy_dst,uniform " +
                    "descriptor=sha256:fullscreen-uniform-slab facts=factory=mutable;payloadCount=one",
            ),
            second.dumpResourceLeaseSnapshot.dumpResourceLeaseLines(),
        )
    }

    @Test
    fun `concrete provider includes lifetime facts in fullscreen uniform slab lease key`() {
        val provider = GPUConcreteResourceProvider()
        val context = targetPreparationContext()

        provider.materializeFullscreenUniformSlabLease(
            request = fullscreenUniformSlabLeaseRequest(),
            context = context,
        )
        provider.materializeFullscreenUniformSlabLease(
            request = fullscreenUniformSlabLeaseRequest(releasePolicy = "frame-complete"),
            context = context,
        )
        provider.materializeFullscreenUniformSlabLease(
            request = fullscreenUniformSlabLeaseRequest(payloadCount = 2),
            context = context,
        )

        assertEquals(
            listOf("create", "create", "create"),
            provider.telemetry.dumpEvents
                .filter { event -> event.lane == "uniform-slab" }
                .map { event -> event.result },
        )
    }

    @Test
    fun `concrete provider creates then reuses fullscreen uniform slab lease`() {
        val provider = GPUConcreteResourceProvider()
        val context = targetPreparationContext()
        val first = assertIs<GPUResourceMaterializationDecision.Materialized>(
            provider.materializeFullscreenUniformSlabLease(
                request = fullscreenUniformSlabLeaseRequest(),
                context = context,
            ),
        )
        val second = assertIs<GPUResourceMaterializationDecision.Materialized>(
            provider.materializeFullscreenUniformSlabLease(
                request = fullscreenUniformSlabLeaseRequest(),
                context = context,
            ),
        )

        assertEquals(
            listOf("create", "reuse"),
            provider.telemetry.dumpEvents
                .filter { event -> event.lane == "uniform-slab" }
                .map { event -> event.result },
        )
        assertEquals(
            listOf(
                "resource-provider.lease id=uniform-slab:fullscreen:frame-1 kind=uniform-slab result=create " +
                    "deviceGeneration=11 owner=frame-1 release=submission-complete usage=copy_dst,uniform " +
                    "descriptor=sha256:fullscreen-uniform-slab facts=alignment=256;payloadCount=1;target=root-target;totalBytes=256",
            ),
            first.dumpResourceLeaseSnapshot.dumpResourceLeaseLines(),
        )
        assertEquals(
            GPUResourceLeaseCacheResult.Reuse,
            second.dumpResourceLeaseSnapshot.single().cacheResult,
        )
    }

    @Test
    fun `concrete provider refuses stale fullscreen uniform slab generation`() {
        val provider = GPUConcreteResourceProvider()
        val refused = assertIs<GPUResourceMaterializationDecision.Refused>(
            provider.materializeFullscreenUniformSlabLease(
                request = fullscreenUniformSlabLeaseRequest(deviceGeneration = 7),
                context = targetPreparationContext(deviceGeneration = 8),
            ),
        )

        assertEquals("unsupported.resource.device_generation_stale", refused.diagnostic.code)
        assertEquals(
            listOf("stale-generation"),
            provider.telemetry.dumpEvents
                .filter { event -> event.lane == "uniform-slab" }
                .map { event -> event.result },
        )
    }

    @Test
    fun `concrete provider reuses payload upload and bind group keys`() {
        val provider = GPUConcreteResourceProvider()
        val context = targetPreparationContext()
        provider.materializePayloadBindings(payloadRequest(), context)

        val second = assertIs<GPUResourceMaterializationDecision.Materialized>(
            provider.materializePayloadBindings(payloadRequest(), context),
        )

        assertEquals(
            listOf("reuse", "reuse"),
            second.dumpPayloadTelemetrySnapshot.map { it.result.dumpToken },
        )
        assertContains(
            provider.telemetry.dumpLines().joinToString("\n"),
            "resource-provider.cache lane=bind-group result=reuse",
        )
    }

    @Test
    fun `concrete provider rejects null buffer labels unsafe for dumps during materialization`() {
        val provider = GPUConcreteResourceProvider()

        assertFailsWith<IllegalArgumentException> {
            provider.materializeNullBuffer(
                GPUNullBufferMaterializationRequest("null@" + "uniform", 16, 11),
                targetPreparationContext(),
            )
        }
    }

    @Test
    fun `concrete provider rejects null buffer labels unsafe for dumps during stale refusal`() {
        val provider = GPUConcreteResourceProvider()

        assertFailsWith<IllegalArgumentException> {
            provider.materializeNullBuffer(
                GPUNullBufferMaterializationRequest("null-" + "0x123456", 16, 7),
                targetPreparationContext(deviceGeneration = 8),
            )
        }
    }

    @Test
    fun `concrete provider records deferred texture sampler telemetry faithfully`() {
        val provider = GPUConcreteResourceProvider(
            textureSamplerProvider = object : GPUResourceProvider {
                override fun materializeTextureSamplerBinding(
                    request: GPUTextureSamplerMaterializationRequest,
                    context: GPUTargetPreparationContext,
                ): GPUResourceMaterializationDecision =
                    GPUResourceMaterializationDecision.Deferred(
                        reasonCode = "resource.waiting_for_gpu",
                        targetId = context.targetId,
                        taskIds = request.taskIds,
                        resourcePlanLabels = request.resourcePlanLabels,
                    )
            },
        )

        val decision = provider.materializeTextureSamplerBinding(
            textureSamplerRequest(),
            targetPreparationContext(),
        )

        val deferred = assertIs<GPUResourceMaterializationDecision.Deferred>(decision)
        assertEquals("resource.waiting_for_gpu", deferred.reasonCode)
        assertContains(
            provider.telemetry.dumpLines().joinToString("\n"),
            "resource-provider.cache lane=texture-sampler result=deferred",
        )
    }
}

private fun targetPreparationContext(deviceGeneration: Long = 11L): GPUTargetPreparationContext =
    GPUTargetPreparationContext(
        targetId = "root-target",
        frameId = "frame-1",
        deviceGeneration = deviceGeneration,
        budgetClass = "unit",
    )

private fun fullscreenUniformSlabLeaseRequest(
    deviceGeneration: Long = 11L,
    targetId: String = "root-target",
    releasePolicy: String = "submission-complete",
    payloadCount: Int = 1,
): GPUUniformSlabLeaseRequest =
    GPUUniformSlabLeaseRequest(
        leaseId = "uniform-slab:fullscreen:frame-1",
        targetId = targetId,
        frameId = "frame-1",
        deviceGeneration = deviceGeneration,
        descriptorHash = "sha256:fullscreen-uniform-slab",
        totalBytes = 256,
        alignmentBytes = 256,
        releasePolicy = releasePolicy,
        payloadCount = payloadCount,
    )

private fun customFullscreenUniformSlabLease(
    request: GPUUniformSlabLeaseRequest,
): GPUResourceLease =
    GPUResourceLease(
        leaseId = request.leaseId,
        resourceKind = GPUResourceLeaseKind.UniformSlab,
        deviceGeneration = request.deviceGeneration,
        descriptorHash = "sha256:factory-fullscreen-uniform-slab",
        ownerScope = "factory-owner",
        usageLabels = listOf("storage", "uniform"),
        releasePolicy = "factory-release",
        cacheResult = GPUResourceLeaseCacheResult.Create,
        evidenceFacts = mapOf(
            "factory" to "custom",
            "payloadCount" to "factory-one",
            "totalBytes" to "factory-allocated",
        ),
    )

private fun payloadRequest(): GPUPayloadMaterializationRequest =
    GPUPayloadMaterializationRequest(
        targetId = "root-target",
        packetId = "packet-1",
        taskIds = listOf("task-payload"),
        resourcePlanLabels = listOf("payload:unit"),
        uniformBlock = GPUUniformPayloadBlock(
            fingerprint = GPUPayloadFingerprint("uniform-fingerprint-unit"),
            packingPlanHash = "layout-unit",
            byteSize = 4,
            zeroedPadding = true,
            scope = "frame-1",
            bytes = listOf(1, 2, 3, 4),
        ),
        uniformSlot = GPUUniformPayloadSlot(
            slotId = GPUPayloadSlotID("pass:uniform:0"),
            fingerprint = GPUPayloadFingerprint("uniform-fingerprint-unit"),
            byteOffset = 0,
        ),
        resourceBlock = GPUResourceBindingBlock(
            fingerprint = GPUPayloadFingerprint("resource-fingerprint-unit"),
            bindingPlanHash = "layout-unit",
            bindingCount = 1,
            resourceDescriptorLabels = listOf("uniform:unit"),
            dynamicOffsets = listOf(0),
        ),
        resourceSlot = GPUResourceBindingSlot(
            slotId = GPUPayloadSlotID("pass:resource:0"),
            fingerprint = GPUPayloadFingerprint("resource-fingerprint-unit"),
            bindingIndex = 0,
        ),
        uploadPlan = GPUPayloadUploadPlan(
            planHash = "upload-unit",
            byteRanges = listOf(0L until 4L),
            stagingScope = "frame-1",
            budgetClass = "unit",
            beforeUseToken = "before-draw",
        ),
        reflectedBindingLayoutHash = "layout-unit",
        deviceGeneration = 11,
        payloadGeneration = 0,
        alignmentBytes = 256,
        uploadBudgetBytes = 256,
        uploadCapabilityAvailable = true,
        maxDynamicOffsets = 1,
        requiredUniformUsageLabels = setOf("copy_dst", "uniform"),
        availableUniformUsageLabels = setOf("copy_dst", "uniform"),
    )

private fun textureSamplerRequest(): GPUTextureSamplerMaterializationRequest =
    GPUTextureSamplerMaterializationRequest(
        targetId = "root-target",
        packetId = "packet-texture-1",
        taskIds = listOf("task-texture"),
        resourcePlanLabels = listOf("texture:unit"),
        allocation = GPUTextureAllocationPlan.CreateTexture(
            descriptor = GPUTextureDescriptor(
                width = 1,
                height = 1,
                format = "rgba8unorm",
                usageLabels = setOf("copy_dst", "texture_binding"),
            ),
            ownership = GPUTextureOwnershipPlan(
                ownerLabel = "texture-owner",
                lifetimeClass = "frame",
                releasePolicy = "pass-end",
                canAliasScratch = false,
            ),
        ),
        ownership = GPUTextureOwnershipPlan(
            ownerLabel = "texture-owner",
            lifetimeClass = "frame",
            releasePolicy = "pass-end",
            canAliasScratch = false,
        ),
        textureDescriptor = GPUTextureDescriptor(
            width = 1,
            height = 1,
            format = "rgba8unorm",
            usageLabels = setOf("copy_dst", "texture_binding"),
        ),
        viewDescriptor = GPUTextureViewDescriptor(
            textureDescriptorHash = "texture-unit",
            viewDimension = "2d",
            mipRange = 0..0,
            arrayLayerRange = 0..0,
        ),
        samplerDescriptor = GPUSamplerDescriptor(
            addressModeU = "clamp-to-edge",
            addressModeV = "clamp-to-edge",
            magFilter = "linear",
            minFilter = "linear",
            mipmapFilter = "nearest",
        ),
        binding = GPUSampledTextureBinding(
            bindingLabel = "sampled-texture.unit",
            view = GPUTextureViewDescriptor(
                textureDescriptorHash = "texture-unit",
                viewDimension = "2d",
                mipRange = 0..0,
                arrayLayerRange = 0..0,
            ),
            sampler = GPUSamplerDescriptor(
                addressModeU = "clamp-to-edge",
                addressModeV = "clamp-to-edge",
                magFilter = "linear",
                minFilter = "linear",
                mipmapFilter = "nearest",
            ),
            useToken = GPUUseToken(1L),
        ),
        bindingLayoutHash = "layout-texture-unit",
        deviceGeneration = 11,
        expectedResourceGeneration = 0,
        actualResourceGeneration = 0,
        requiredTextureUsageLabels = setOf("copy_dst", "texture_binding"),
        availableTextureUsageLabels = setOf("copy_dst", "texture_binding"),
        requiredMipLevels = 1,
        uploadBytes = 4,
        uploadBudgetBytes = 256,
        uploadCapabilityAvailable = true,
    )
