package org.graphiks.kanvas.gpu.renderer.resources

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUImplementationIdentity
import org.graphiks.kanvas.gpu.renderer.capabilities.GPULimits
import org.graphiks.kanvas.gpu.renderer.capabilities.GPURendererFeature
import org.graphiks.kanvas.gpu.renderer.color.GPUColorFormat
import org.graphiks.kanvas.gpu.renderer.color.GPUColorInterpretation
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackLayoutPlan
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackLayoutPlanner
import org.graphiks.kanvas.gpu.renderer.execution.GPUConcreteFrameReadbackAccess
import org.graphiks.kanvas.gpu.renderer.execution.GPUFrameCoreTestFixture
import org.graphiks.kanvas.gpu.renderer.execution.GPUFrameNativeReadbackMapper
import org.graphiks.kanvas.gpu.renderer.execution.GPUFrameMappedReadbackRange
import org.graphiks.kanvas.gpu.renderer.execution.GPUFrameNativeReadbackMapDelivery
import org.graphiks.kanvas.gpu.renderer.execution.GPUFrameNativeReadbackMapSink
import org.graphiks.kanvas.gpu.renderer.execution.GPUFrameReadbackLifecycleResult
import org.graphiks.kanvas.gpu.renderer.execution.GPUFrameReadbackMapFailureSafety
import org.graphiks.kanvas.gpu.renderer.execution.GPUFrameReadbackNativeOutputSafety
import org.graphiks.kanvas.gpu.renderer.execution.GPUFrameReadbackMapArmResult
import org.graphiks.kanvas.gpu.renderer.execution.GPUFrameReadbackMapDelivery
import org.graphiks.kanvas.gpu.renderer.execution.GPUFrameReadbackMapSink
import org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedNativeScopeOperand
import org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedReadbackOutput
import org.graphiks.kanvas.gpu.renderer.execution.GPUQueueCompletionTicket
import org.graphiks.kanvas.gpu.renderer.execution.GPUQueueCompletionTicketID
import org.graphiks.kanvas.gpu.renderer.execution.GPUQueueCompletionFailureKind
import org.graphiks.kanvas.gpu.renderer.execution.executionDiagnostic
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPayloadFingerprint
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPayloadSlotID
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPayloadUploadPlan
import org.graphiks.kanvas.gpu.renderer.payloads.GPUResourceBindingBlock
import org.graphiks.kanvas.gpu.renderer.payloads.GPUResourceBindingSlot
import org.graphiks.kanvas.gpu.renderer.payloads.GPUUniformPayloadBlock
import org.graphiks.kanvas.gpu.renderer.payloads.GPUUniformPayloadSlot
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameReadbackRequest
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameID
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackPixelFormat
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackRequestID

class GPUConcreteResourceProviderTest {
    @Test
    fun `concrete provider binds its lease factory only by exact identity`() {
        val adapter = org.graphiks.kanvas.gpu.renderer.execution.GPURuntimeResourceAdapter()
        val provider = GPUConcreteResourceProvider(leaseFactory = adapter)

        assertTrue(provider.isBackedBy(adapter))
        assertFalse(provider.isBackedBy(EvidenceOnlyGPUResourceLeaseFactory))
    }

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
            listOf(
                "resource-provider.lease id=null-buffer:null-uniform kind=null-buffer result=create " +
                    "deviceGeneration=11 owner=resource-provider:null-buffer release=device-generation " +
                    "usage=uniform descriptor=null-buffer:16 facts=byteSize=16;zeroFilled=true",
            ),
            first.dumpResourceLeaseSnapshot.dumpResourceLeaseLines(),
        )
        assertEquals(GPUResourceLeaseCacheResult.Reuse, second.dumpResourceLeaseSnapshot.single().cacheResult)
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
            setOf(GPUResourceLeaseCacheResult.Create),
            first.dumpResourceLeaseSnapshot.map { lease -> lease.cacheResult }.toSet(),
        )
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
        assertFalse(first.dumpResourceLeaseSnapshot.dumpResourceLeaseLines().joinToString("\n").contains("@"))
        assertFalse(provider.telemetry.dumpLines().joinToString("\n").contains("@"))
    }

    @Test
    fun `concrete provider treats texture sampler lease identity changes as create`() {
        val provider = GPUConcreteResourceProvider()
        val context = targetPreparationContext()
        val baseRequest = textureSamplerRequest()
        provider.materializeTextureSamplerBinding(baseRequest, context)

        val variants = listOf(
            baseRequest.copy(
                binding = baseRequest.binding.copy(bindingLabel = "sampled-texture.unit.variant"),
            ),
            baseRequest.copy(
                ownership = baseRequest.ownership.copy(ownerLabel = "texture-owner-variant"),
            ),
            baseRequest.copy(
                ownership = baseRequest.ownership.copy(releasePolicy = "frame-end"),
            ),
            baseRequest.copy(
                expectedResourceGeneration = 1,
                actualResourceGeneration = 1,
            ),
            baseRequest.copy(
                ownership = baseRequest.ownership.copy(lifetimeClass = "scratch"),
            ),
            baseRequest.copy(
                ownership = baseRequest.ownership.copy(canAliasScratch = true),
            ),
        )

        variants.forEach { request ->
            val materialized = assertIs<GPUResourceMaterializationDecision.Materialized>(
                provider.materializeTextureSamplerBinding(request, context),
            )
            assertEquals(
                setOf(GPUResourceLeaseCacheResult.Create),
                materialized.dumpResourceLeaseSnapshot.map { lease -> lease.cacheResult }.toSet(),
            )
        }

        assertEquals(
            listOf("create") + List(variants.size) { "create" },
            provider.telemetry.dumpEvents
                .filter { event -> event.lane == "texture-sampler" }
                .map { event -> event.result },
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

    @Test
    fun `concrete provider records refused texture sampler telemetry as refuse`() {
        val provider = GPUConcreteResourceProvider(
            textureSamplerProvider = object : GPUResourceProvider {
                override fun materializeTextureSamplerBinding(
                    request: GPUTextureSamplerMaterializationRequest,
                    context: GPUTargetPreparationContext,
                ): GPUResourceMaterializationDecision =
                    GPUResourceMaterializationDecision.Refused(
                        diagnostic = GPUResourceDiagnostic.deviceGenerationStale(
                            resourceLabel = request.binding.bindingLabel,
                            expectedDeviceGeneration = context.deviceGeneration,
                            actualDeviceGeneration = request.deviceGeneration - 1,
                            resourceKind = "resource",
                        ),
                        targetId = context.targetId,
                        resourcePlanLabels = request.resourcePlanLabels,
                    )
            },
        )

        val decision = provider.materializeTextureSamplerBinding(
            textureSamplerRequest(),
            targetPreparationContext(),
        )

        val refused = assertIs<GPUResourceMaterializationDecision.Refused>(decision)
        assertEquals("unsupported.resource.device_generation_stale", refused.diagnostic.code)
        val lines = provider.telemetry.dumpLines().joinToString("\n")
        assertContains(lines, "resource-provider.cache lane=texture-sampler result=refuse")
        assertFalse(lines.contains("result=failure"))
    }

    @Test
    fun `concrete provider owns completion safe scratch lifecycle through resource provider`() {
        val provider: GPUResourceProvider = GPUConcreteResourceProvider()
        val generation = GPUDeviceGenerationID(11)
        val lease = assertIs<GPUScratchTextureReservationResult.Accepted>(
            provider.reserveScratchTexture(
                request = providerScratchRequest("scratch", "frame-1", generation),
                budget = providerBudget(8_192),
                capabilities = providerCapabilities(),
            ),
        ).lease

        assertIs<GPUScratchLifecycleResult.Accepted>(
            provider.markScratchSubmitted("frame-1", GPUResourceSubmissionID(1), generation),
        )
        val beforeCompletion = assertIs<GPUScratchTextureReservationResult.Accepted>(
            provider.reserveScratchTexture(
                request = providerScratchRequest("before", "frame-2", generation),
                budget = providerBudget(12_288),
                capabilities = providerCapabilities(),
            ),
        ).lease
        assertNotEquals(lease.resourceRef, beforeCompletion.resourceRef)

        assertIs<GPUScratchLifecycleResult.Accepted>(
            provider.acceptScratchCompletion(GPUResourceSubmissionID(1), generation),
        )
    }

    @Test
    fun `concrete provider shares physical scratch and readback budget ownership`() {
        val provider: GPUResourceProvider = GPUConcreteResourceProvider()
        val generation = GPUDeviceGenerationID(11)
        assertIs<GPUScratchTextureReservationResult.Accepted>(
            provider.reserveScratchTexture(
                request = providerScratchRequest("scratch", "frame-1", generation),
                budget = providerBudget(4_500),
                capabilities = providerCapabilities(),
            ),
        )
        val readbackPlan = assertIs<GPUReadbackLayoutPlan.Planned>(
            GPUReadbackLayoutPlanner().plan(
                request = GPUFrameReadbackRequest(
                    requestId = GPUReadbackRequestID("provider-readback"),
                    sourceBounds = GPUPixelBounds(0, 0, 65, 2),
                    pixelFormat = GPUReadbackPixelFormat.Rgba8Unorm,
                    outputColorInterpretation = GPUColorInterpretation("srgb-premul"),
                ),
                capabilities = providerCapabilities(),
            ),
        )

        val refused = assertIs<GPUReadbackStagingReservationResult.Refused>(
            provider.reserveReadbackStaging(
                GPUReadbackStagingReservationRequest(
                    reservationId = "readback",
                    ownerScope = "frame-1",
                    deviceGeneration = generation,
                    descriptor = readbackPlan.stagingDescriptor,
                    backingBufferBytes = readbackPlan.stagingDescriptor.minimumBufferBytes,
                    budgetPlan = providerBudget(4_500),
                ),
            ),
        )

        assertEquals("unsupported.readback_staging.aggregate_budget_exceeded", refused.diagnostic.code.value)
        assertEquals("4096", refused.diagnostic.facts["category.ReusableScratch"])
        assertEquals("772", refused.diagnostic.facts["category.ReadbackStaging"])
    }

    @Test
    fun `concrete provider shares physical readback and scratch budget ownership in reverse order`() {
        val provider: GPUResourceProvider = GPUConcreteResourceProvider()
        val generation = GPUDeviceGenerationID(11)
        val readbackPlan = assertIs<GPUReadbackLayoutPlan.Planned>(
            GPUReadbackLayoutPlanner().plan(
                request = GPUFrameReadbackRequest(
                    requestId = GPUReadbackRequestID("provider-readback-first"),
                    sourceBounds = GPUPixelBounds(0, 0, 65, 2),
                    pixelFormat = GPUReadbackPixelFormat.Rgba8Unorm,
                    outputColorInterpretation = GPUColorInterpretation("srgb-premul"),
                ),
                capabilities = providerCapabilities(),
            ),
        )
        assertIs<GPUReadbackStagingReservationResult.Accepted>(
            provider.reserveReadbackStaging(
                GPUReadbackStagingReservationRequest(
                    reservationId = "readback-first",
                    ownerScope = "frame-1",
                    deviceGeneration = generation,
                    descriptor = readbackPlan.stagingDescriptor,
                    backingBufferBytes = readbackPlan.stagingDescriptor.minimumBufferBytes,
                    budgetPlan = providerBudget(4_500),
                ),
            ),
        )

        val refused = assertIs<GPUScratchTextureReservationResult.Refused>(
            provider.reserveScratchTexture(
                request = providerScratchRequest("scratch-second", "frame-1", generation),
                budget = providerBudget(4_500),
                capabilities = providerCapabilities(),
            ),
        )

        assertEquals("unsupported.scratch_texture.aggregate_budget_exceeded", refused.diagnostic.code.value)
        assertEquals("4096", refused.diagnostic.facts["category.ReusableScratch"])
        assertEquals("772", refused.diagnostic.facts["category.ReadbackStaging"])
    }

    @Test
    fun `resource provider exposes rollback eviction and generation invalidation for both physical pools`() {
        val provider: GPUResourceProvider = GPUConcreteResourceProvider()
        val generation = GPUDeviceGenerationID(11)
        assertIs<GPUScratchTextureReservationResult.Accepted>(
            provider.reserveScratchTexture(
                providerScratchRequest("scratch", "frame-1", generation),
                providerBudget(16_384),
                providerCapabilities(),
            ),
        )
        val descriptor = assertIs<GPUReadbackLayoutPlan.Planned>(
            GPUReadbackLayoutPlanner().plan(
                GPUFrameReadbackRequest(
                    requestId = GPUReadbackRequestID("maintenance"),
                    sourceBounds = GPUPixelBounds(0, 0, 65, 2),
                    pixelFormat = GPUReadbackPixelFormat.Rgba8Unorm,
                    outputColorInterpretation = GPUColorInterpretation("srgb-premul"),
                ),
                providerCapabilities(),
            ),
        ).stagingDescriptor
        assertIs<GPUReadbackStagingReservationResult.Accepted>(
            provider.reserveReadbackStaging(
                GPUReadbackStagingReservationRequest(
                    reservationId = "readback",
                    ownerScope = "frame-1",
                    deviceGeneration = generation,
                    descriptor = descriptor,
                    backingBufferBytes = descriptor.minimumBufferBytes,
                    budgetPlan = providerBudget(16_384),
                ),
            ),
        )

        val rollback = assertIs<GPUPhysicalPoolMaintenanceDecision.Applied<GPUPhysicalPoolRollbackSummary>>(
            provider.rollbackPhysicalPoolsBeforeSubmit("frame-1"),
        ).value
        assertEquals(listOf("scratch"), rollback.scratch.releasedReservationIds)
        assertEquals(listOf("readback"), rollback.readback.releasedReservationIds)

        val evicted = assertIs<GPUPhysicalPoolMaintenanceDecision.Applied<GPUPhysicalPoolEvictionSummary>>(
            provider.evictPhysicalPoolsUntil(0),
        ).value
        assertEquals(1, evicted.scratchEntries.size)
        assertEquals(1, evicted.readbackEntries.size)

        assertIs<GPUScratchTextureReservationResult.Accepted>(
            provider.reserveScratchTexture(
                providerScratchRequest("old-generation", "frame-2", generation),
                providerBudget(16_384),
                providerCapabilities(),
            ),
        )
        val invalidated =
            assertIs<GPUPhysicalPoolMaintenanceDecision.Applied<GPUPhysicalPoolInvalidationSummary>>(
                provider.invalidatePhysicalPoolsBefore(GPUDeviceGenerationID(12)),
            ).value
        assertEquals(1, invalidated.scratchEntries.size)
    }

    @Test
    fun `provider rolls back interleaved physical acquisitions in one global LIFO order`() {
        val concreteProvider = GPUConcreteResourceProvider()
        val provider: GPUResourceProvider = concreteProvider
        val generation = GPUDeviceGenerationID(11)
        val budget = providerBudget(32_768)
        val scratchA = assertIs<GPUScratchTextureReservationResult.Accepted>(
            provider.reserveScratchTexture(
                providerScratchRequest("scratch-a", "frame-lifo", generation),
                budget,
                providerCapabilities(),
            ),
        ).lease
        val readbackDescriptor = assertIs<GPUReadbackLayoutPlan.Planned>(
            GPUReadbackLayoutPlanner().plan(
                GPUFrameReadbackRequest(
                    requestId = GPUReadbackRequestID("provider-lifo"),
                    sourceBounds = GPUPixelBounds(0, 0, 65, 2),
                    pixelFormat = GPUReadbackPixelFormat.Rgba8Unorm,
                    outputColorInterpretation = GPUColorInterpretation("srgb-premul"),
                ),
                providerCapabilities(),
            ),
        ).stagingDescriptor
        val readbackB = assertIs<GPUReadbackStagingReservationResult.Accepted>(
            provider.reserveReadbackStaging(
                GPUReadbackStagingReservationRequest(
                    reservationId = "readback-b",
                    ownerScope = "frame-lifo",
                    deviceGeneration = generation,
                    descriptor = readbackDescriptor,
                    backingBufferBytes = readbackDescriptor.minimumBufferBytes,
                    budgetPlan = budget,
                ),
            ),
        ).lease
        val scratchC = assertIs<GPUScratchTextureReservationResult.Accepted>(
            provider.reserveScratchTexture(
                providerScratchRequest(
                    "scratch-c",
                    "frame-lifo",
                    generation,
                    format = GPUColorFormat("bgra8unorm"),
                ),
                budget,
                providerCapabilities(),
            ),
        ).lease
        assertEquals(3, concreteProvider.pendingPhysicalReservationCount)

        val rollback = assertIs<GPUPhysicalPoolMaintenanceDecision.Applied<GPUPhysicalPoolRollbackSummary>>(
            provider.rollbackPhysicalPoolsBeforeSubmit("frame-lifo"),
        ).value
        assertEquals(0, concreteProvider.pendingPhysicalReservationCount)

        assertEquals(
            listOf("scratch:scratch-c", "readback:readback-b", "scratch:scratch-a"),
            rollback.releaseOrder.map { release ->
                when (release) {
                    is GPUPhysicalPoolRollbackRelease.Scratch -> "scratch:${release.reservationId}"
                    is GPUPhysicalPoolRollbackRelease.Readback -> "readback:${release.reservationId}"
                }
            },
        )
        assertEquals(listOf(scratchC.resourceRef, scratchA.resourceRef), rollback.scratch.resourceRefs)
        assertEquals(listOf(readbackB.resourceRef), rollback.readback.resourceRefs)

        val duplicate = assertIs<GPUPhysicalPoolMaintenanceDecision.Applied<GPUPhysicalPoolRollbackSummary>>(
            provider.rollbackPhysicalPoolsBeforeSubmit("frame-lifo"),
        ).value
        assertEquals(emptyList(), duplicate.releaseOrder)
        assertEquals(emptyList(), duplicate.scratch.resourceRefs)
        assertEquals(emptyList(), duplicate.readback.resourceRefs)
        assertIs<GPUScratchLifecycleResult.Refused>(
            provider.markScratchSubmitted("frame-lifo", GPUResourceSubmissionID(91), generation),
        )
        assertIs<GPUReadbackStagingLifecycleResult.Refused>(
            provider.markReadbackSubmitted(listOf(readbackB), GPUResourceSubmissionID(92)),
        )
    }

    @Test
    fun `provider journal removes submitted scratch and readback reservations exactly`() {
        val concreteProvider = GPUConcreteResourceProvider()
        val provider: GPUResourceProvider = concreteProvider
        val generation = GPUDeviceGenerationID(11)
        val budget = providerBudget(16_384)
        assertIs<GPUScratchTextureReservationResult.Accepted>(
            provider.reserveScratchTexture(
                providerScratchRequest("journal-scratch", "frame-journal", generation),
                budget,
                providerCapabilities(),
            ),
        )
        val descriptor = assertIs<GPUReadbackLayoutPlan.Planned>(
            GPUReadbackLayoutPlanner().plan(
                GPUFrameReadbackRequest(
                    requestId = GPUReadbackRequestID("journal-readback"),
                    sourceBounds = GPUPixelBounds(0, 0, 4, 2),
                    pixelFormat = GPUReadbackPixelFormat.Rgba8Unorm,
                    outputColorInterpretation = GPUColorInterpretation("srgb-premul"),
                ),
                providerCapabilities(),
            ),
        ).stagingDescriptor
        val readback = assertIs<GPUReadbackStagingReservationResult.Accepted>(
            provider.reserveReadbackStaging(
                GPUReadbackStagingReservationRequest(
                    reservationId = "journal-readback",
                    ownerScope = "frame-journal",
                    deviceGeneration = generation,
                    descriptor = descriptor,
                    backingBufferBytes = descriptor.minimumBufferBytes,
                    budgetPlan = budget,
                ),
            ),
        ).lease
        assertEquals(2, concreteProvider.pendingPhysicalReservationCount)

        assertIs<GPUScratchLifecycleResult.Accepted>(
            provider.markScratchSubmitted("frame-journal", GPUResourceSubmissionID(101), generation),
        )
        assertEquals(1, concreteProvider.pendingPhysicalReservationCount)
        assertIs<GPUReadbackStagingLifecycleResult.Accepted>(
            provider.markReadbackSubmitted(listOf(readback), GPUResourceSubmissionID(102)),
        )
        assertEquals(0, concreteProvider.pendingPhysicalReservationCount)
    }

    @Test
    fun `concrete frame readback bridge retains staging through delayed map then makes it reusable`() {
        val provider = GPUConcreteResourceProvider()
        val events = mutableListOf<String>()
        val lifecycleProvider = object : GPUFrameResourcePreflightProvider by provider {
            override fun markReadbackSubmitted(
                leases: List<GPUReadbackStagingLease>,
                submissionId: GPUResourceSubmissionID,
            ): GPUReadbackStagingLifecycleResult {
                events += "pool:submit"
                return provider.markReadbackSubmitted(leases, submissionId)
            }

            override fun acceptReadbackGPUCompletion(
                submissionId: GPUResourceSubmissionID,
                deviceGeneration: GPUDeviceGenerationID,
            ): GPUReadbackStagingLifecycleResult {
                events += "pool:complete"
                return provider.acceptReadbackGPUCompletion(submissionId, deviceGeneration)
            }

            override fun markReadbackMapped(
                lease: GPUReadbackStagingLease,
            ): GPUReadbackStagingLifecycleResult {
                events += "pool:map"
                return provider.markReadbackMapped(lease)
            }

            override fun markReadbackDepadded(
                lease: GPUReadbackStagingLease,
            ): GPUReadbackStagingLifecycleResult {
                events += "pool:depad"
                return provider.markReadbackDepadded(lease)
            }

            override fun releaseReadbackAfterUnmap(
                lease: GPUReadbackStagingLease,
            ): GPUReadbackStagingLifecycleResult {
                events += "pool:release"
                return provider.releaseReadbackAfterUnmap(lease)
            }
        }
        val generation = GPUDeviceGenerationID(11)
        val budget = providerBudget(16_384)
        val request = GPUFrameReadbackRequest(
            requestId = GPUReadbackRequestID("bridge-readback"),
            sourceBounds = GPUPixelBounds(0, 0, 4, 4),
            pixelFormat = GPUReadbackPixelFormat.Rgba8Unorm,
            outputColorInterpretation = GPUColorInterpretation("srgb-premul"),
        )
        val planned = assertIs<GPUReadbackLayoutPlan.Planned>(
            GPUReadbackLayoutPlanner().plan(request, providerCapabilities()),
        )
        fun reserve(id: String, owner: String): GPUReadbackStagingLease =
            assertIs<GPUReadbackStagingReservationResult.Accepted>(
                provider.reserveReadbackStaging(
                    GPUReadbackStagingReservationRequest(
                        reservationId = id,
                        ownerScope = owner,
                        deviceGeneration = generation,
                        descriptor = planned.stagingDescriptor,
                        backingBufferBytes = planned.stagingDescriptor.minimumBufferBytes,
                        budgetPlan = budget,
                    ),
                ),
            ).lease

        val first = reserve("bridge-a", "frame-a")
        val output = GPUPreparedReadbackOutput(
            stagingResource = GPUFrameBufferRef("buffer.bridge-a"),
            concreteResource = GPUPreparedConcreteResourceRef.Buffer(first.resourceRef),
            resourceGeneration = 1,
            request = request,
            layout = planned.layout,
            stagingLease = first,
        )
        var pendingSink: GPUFrameNativeReadbackMapSink? = null
        val bridge = GPUConcreteFrameReadbackAccess(
            lifecycleProvider,
            GPUFrameNativeReadbackMapper { _, _, sink ->
                events += "mapper:armed"
                pendingSink = sink
                GPUFrameReadbackMapArmResult.Armed
            },
        )
        val ticket = GPUQueueCompletionTicket(
            GPUQueueCompletionTicketID("ticket.bridge-a"),
            GPUFrameID(41),
            generation,
        )
        val operand = GPUFrameCoreTestFixture.nativePayload(withReadback = true)
            .scopeOperands.filterIsInstance<GPUPreparedNativeScopeOperand.Readback>().single()

        assertEquals(GPUFrameReadbackLifecycleResult.Applied, bridge.markSubmitted(ticket, output, operand))
        assertEquals(
            GPUFrameReadbackLifecycleResult.Applied,
            bridge.acceptGPUCompletion(ticket, output, operand),
        )
        var terminalDelivery: GPUFrameReadbackMapDelivery? = null
        assertEquals(
            GPUFrameReadbackMapArmResult.Armed,
            bridge.mapAndDepad(
                output,
                operand,
                GPUFrameReadbackMapSink {
                    events += "terminal"
                    terminalDelivery = it
                },
            ),
        )

        val concurrent = reserve("bridge-b", "frame-b")
        assertNotEquals(first.resourceRef, concurrent.resourceRef)

        val mapped = ByteArray(planned.layout.totalBufferBytes.toInt())
        pendingSink!!.accept(
            GPUFrameNativeReadbackMapDelivery.Mapped(
                object : GPUFrameMappedReadbackRange {
                    override fun copyBytesFromZero(): ByteArray = mapped.copyOf().also {
                        events += "range:read"
                    }
                    override fun unmap() {
                        events += "range:unmap"
                    }
                },
            ),
        )
        assertIs<GPUFrameReadbackMapDelivery.Pixels>(terminalDelivery)
        assertEquals(
            listOf(
                "pool:submit",
                "pool:complete",
                "mapper:armed",
                "pool:map",
                "range:read",
                "pool:depad",
                "range:unmap",
                "terminal",
            ),
            events,
        )
        val stillNotReusable = reserve("bridge-c-before-close", "frame-c-before-close")
        assertNotEquals(first.resourceRef, stillNotReusable.resourceRef)
        events += "native:close"
        assertEquals(
            GPUFrameReadbackLifecycleResult.Applied,
            bridge.finalizeAfterNativeClose(
                output,
                operand,
                GPUFrameReadbackNativeOutputSafety.Released,
            ),
        )
        assertEquals("pool:release", events.last())
        val eventsAfterTerminal = events.toList()
        pendingSink!!.accept(
            GPUFrameNativeReadbackMapDelivery.Failed(
                executionDiagnostic("failed.test.duplicate-map", "duplicate"),
                org.graphiks.kanvas.gpu.renderer.execution.GPUFrameReadbackMapFailureSafety.Quarantine,
            ),
        )
        assertEquals(eventsAfterTerminal, events)
        val reusable = reserve("bridge-c", "frame-c")
        assertEquals(first.resourceRef, reusable.resourceRef)
    }

    @Test
    fun `duplicate readback reservation refuses before provider and preserves first ticket ledger`() {
        val provider = GPUConcreteResourceProvider()
        val generation = GPUDeviceGenerationID(32)
        val request = GPUFrameReadbackRequest(
            GPUReadbackRequestID("duplicate-reservation"),
            GPUPixelBounds(0, 0, 4, 4),
            GPUReadbackPixelFormat.Rgba8Unorm,
            GPUColorInterpretation("srgb-premul"),
        )
        val planned = assertIs<GPUReadbackLayoutPlan.Planned>(
            GPUReadbackLayoutPlanner().plan(request, providerCapabilities()),
        )
        val lease = assertIs<GPUReadbackStagingReservationResult.Accepted>(
            provider.reserveReadbackStaging(
                GPUReadbackStagingReservationRequest(
                    "duplicate-reservation",
                    "owner-duplicate",
                    generation,
                    planned.stagingDescriptor,
                    planned.stagingDescriptor.minimumBufferBytes,
                    providerBudget(16_384),
                ),
            ),
        ).lease
        val output = GPUPreparedReadbackOutput(
            GPUFrameBufferRef("buffer.duplicate"),
            GPUPreparedConcreteResourceRef.Buffer(lease.resourceRef),
            1,
            request,
            planned.layout,
            lease,
        )
        var providerSubmitCalls = 0
        var providerAcceptCalls = 0
        var providerRejectCalls = 0
        val observed = object : GPUFrameResourcePreflightProvider by provider {
            override fun markReadbackSubmitted(
                leases: List<GPUReadbackStagingLease>,
                submissionId: GPUResourceSubmissionID,
            ): GPUReadbackStagingLifecycleResult {
                providerSubmitCalls += 1
                return provider.markReadbackSubmitted(leases, submissionId)
            }

            override fun acceptReadbackGPUCompletion(
                submissionId: GPUResourceSubmissionID,
                deviceGeneration: GPUDeviceGenerationID,
            ): GPUReadbackStagingLifecycleResult {
                providerAcceptCalls += 1
                return provider.acceptReadbackGPUCompletion(submissionId, deviceGeneration)
            }

            override fun rejectReadbackGPUCompletion(
                submissionId: GPUResourceSubmissionID,
                deviceGeneration: GPUDeviceGenerationID,
                failure: GPUReadbackCompletionFailure,
            ): GPUReadbackStagingLifecycleResult {
                providerRejectCalls += 1
                return provider.rejectReadbackGPUCompletion(submissionId, deviceGeneration, failure)
            }
        }
        val bridge = GPUConcreteFrameReadbackAccess(
            observed,
            GPUFrameNativeReadbackMapper { _, _, _ -> error("not mapped") },
        )
        val firstTicket = GPUQueueCompletionTicket(
            GPUQueueCompletionTicketID("ticket.duplicate.first"),
            GPUFrameID(71),
            generation,
        )
        val secondTicket = GPUQueueCompletionTicket(
            GPUQueueCompletionTicketID("ticket.duplicate.second"),
            GPUFrameID(72),
            generation,
        )

        val operand = GPUFrameCoreTestFixture.nativePayload(withReadback = true)
            .scopeOperands.filterIsInstance<GPUPreparedNativeScopeOperand.Readback>().single()
        assertEquals(
            GPUFrameReadbackLifecycleResult.Applied,
            bridge.markSubmitted(firstTicket, output, operand),
        )
        val refused = assertIs<GPUFrameReadbackLifecycleResult.Refused>(
            bridge.markSubmitted(secondTicket, output, operand),
        )
        assertEquals("failed.frame-readback.duplicate-reservation", refused.diagnostic.code.value)
        assertEquals(1, providerSubmitCalls)
        val copiedTicketRefusal = assertIs<GPUFrameReadbackLifecycleResult.Refused>(
            bridge.acceptGPUCompletion(firstTicket.copy(), output, operand),
        )
        assertEquals(
            "failed.frame-readback.completion-identity-mismatch",
            copiedTicketRefusal.diagnostic.code.value,
        )
        val copiedOutput = output.copy()
        val copiedOutputRefusal = assertIs<GPUFrameReadbackLifecycleResult.Refused>(
            bridge.acceptGPUCompletion(firstTicket, copiedOutput, operand),
        )
        assertEquals(
            "failed.frame-readback.completion-identity-mismatch",
            copiedOutputRefusal.diagnostic.code.value,
        )
        val copiedOperand = GPUFrameCoreTestFixture.nativePayload(withReadback = true)
            .scopeOperands.filterIsInstance<GPUPreparedNativeScopeOperand.Readback>().single()
        val copiedOperandRefusal = assertIs<GPUFrameReadbackLifecycleResult.Refused>(
            bridge.rejectGPUCompletion(
                firstTicket,
                output,
                copiedOperand,
                GPUQueueCompletionFailureKind.CallbackFailure,
            ),
        )
        assertEquals(
            "failed.frame-readback.completion-identity-mismatch",
            copiedOperandRefusal.diagnostic.code.value,
        )
        assertEquals(0, providerAcceptCalls)
        assertEquals(0, providerRejectCalls)
        assertEquals(
            GPUFrameReadbackLifecycleResult.Applied,
            bridge.acceptGPUCompletion(firstTicket, output, operand),
        )
        assertEquals(1, providerAcceptCalls)
        assertEquals(0, providerRejectCalls)
    }

    @Test
    fun `physical provider serializes readback callbacks and reservations on one owner monitor`() {
        val provider = GPUConcreteResourceProvider()
        val generation = GPUDeviceGenerationID(33)
        val planned = assertIs<GPUReadbackLayoutPlan.Planned>(
            GPUReadbackLayoutPlanner().plan(
                GPUFrameReadbackRequest(
                    GPUReadbackRequestID("serialized-provider"),
                    GPUPixelBounds(0, 0, 4, 4),
                    GPUReadbackPixelFormat.Rgba8Unorm,
                    GPUColorInterpretation("srgb-premul"),
                ),
                providerCapabilities(),
            ),
        )
        fun request(id: String) = GPUReadbackStagingReservationRequest(
            id,
            "owner-$id",
            generation,
            planned.stagingDescriptor,
            planned.stagingDescriptor.minimumBufferBytes,
            providerBudget(16_384),
        )
        val first = assertIs<GPUReadbackStagingReservationResult.Accepted>(
            provider.reserveReadbackStaging(request("serialized-first")),
        ).lease
        val submission = GPUResourceSubmissionID(330)
        assertIs<GPUReadbackStagingLifecycleResult.Accepted>(
            provider.markReadbackSubmitted(listOf(first), submission),
        )
        assertIs<GPUReadbackStagingLifecycleResult.Accepted>(
            provider.acceptReadbackGPUCompletion(submission, generation),
        )

        val mappingAttempted = CountDownLatch(1)
        val mappingDone = CountDownLatch(1)
        val reservationAttempted = CountDownLatch(1)
        val reservationDone = CountDownLatch(1)
        val mappingResult = AtomicReference<GPUReadbackStagingLifecycleResult>()
        val reservationResult = AtomicReference<GPUReadbackStagingReservationResult>()
        val mappingThread: Thread
        val reservationThread: Thread
        synchronized(provider) {
            mappingThread = Thread({
                mappingAttempted.countDown()
                mappingResult.set(provider.markReadbackMapped(first))
                mappingDone.countDown()
            }, "provider-callback")
            reservationThread = Thread({
                reservationAttempted.countDown()
                reservationResult.set(provider.reserveReadbackStaging(request("serialized-second")))
                reservationDone.countDown()
            }, "provider-reservation")
            mappingThread.start()
            reservationThread.start()
            assertTrue(mappingAttempted.await(1, TimeUnit.SECONDS))
            assertTrue(reservationAttempted.await(1, TimeUnit.SECONDS))
            assertFalse(mappingDone.await(100, TimeUnit.MILLISECONDS))
            assertFalse(reservationDone.await(100, TimeUnit.MILLISECONDS))
        }
        mappingThread.join(1_000)
        reservationThread.join(1_000)

        assertFalse(mappingThread.isAlive)
        assertFalse(reservationThread.isAlive)
        assertIs<GPUReadbackStagingLifecycleResult.Accepted>(mappingResult.get())
        val second = assertIs<GPUReadbackStagingReservationResult.Accepted>(reservationResult.get()).lease
        assertNotEquals(first.resourceRef, second.resourceRef)
    }

    @Test
    fun `readback mapper refused or throw quarantines pool state and removes exact bridge ledger`() {
        listOf("refused", "throw").forEachIndexed { index, mode ->
            val provider = GPUConcreteResourceProvider()
            val generation = GPUDeviceGenerationID(20 + index.toLong())
            val request = GPUFrameReadbackRequest(
                requestId = GPUReadbackRequestID("map-arm-$mode"),
                sourceBounds = GPUPixelBounds(0, 0, 4, 4),
                pixelFormat = GPUReadbackPixelFormat.Rgba8Unorm,
                outputColorInterpretation = GPUColorInterpretation("srgb-premul"),
            )
            val planned = assertIs<GPUReadbackLayoutPlan.Planned>(
                GPUReadbackLayoutPlanner().plan(request, providerCapabilities()),
            )
            fun output(id: String): GPUPreparedReadbackOutput {
                val lease = assertIs<GPUReadbackStagingReservationResult.Accepted>(
                    provider.reserveReadbackStaging(
                        GPUReadbackStagingReservationRequest(
                            reservationId = id,
                            ownerScope = "owner-$id",
                            deviceGeneration = generation,
                            descriptor = planned.stagingDescriptor,
                            backingBufferBytes = planned.stagingDescriptor.minimumBufferBytes,
                            budgetPlan = providerBudget(16_384),
                        ),
                    ),
                ).lease
                return GPUPreparedReadbackOutput(
                    GPUFrameBufferRef("buffer.$id"),
                    GPUPreparedConcreteResourceRef.Buffer(lease.resourceRef),
                    1,
                    request,
                    planned.layout,
                    lease,
                )
            }
            val poolEvents = mutableListOf<String>()
            val observedProvider = object : GPUFrameResourcePreflightProvider by provider {
                override fun quarantineReadbackAfterSubmit(
                    lease: GPUReadbackStagingLease,
                ): GPUReadbackStagingLifecycleResult {
                    poolEvents += "fail-closed"
                    return provider.quarantineReadbackAfterSubmit(lease)
                }
            }
            val bridge = GPUConcreteFrameReadbackAccess(
                observedProvider,
                GPUFrameNativeReadbackMapper { _, _, _ ->
                    if (mode == "throw") error("mapper arm exploded")
                    GPUFrameReadbackMapArmResult.Refused(
                        executionDiagnostic("failed.test.mapper-refused", "refused"),
                    )
                },
            )
            val first = output("$mode-a")
            val ticket = GPUQueueCompletionTicket(
                GPUQueueCompletionTicketID("ticket.$mode"),
                GPUFrameID(50 + index.toLong()),
                generation,
            )
            val operand = GPUFrameCoreTestFixture.nativePayload(withReadback = true)
                .scopeOperands.filterIsInstance<GPUPreparedNativeScopeOperand.Readback>().single()
            assertEquals(GPUFrameReadbackLifecycleResult.Applied, bridge.markSubmitted(ticket, first, operand))
            assertEquals(
                GPUFrameReadbackLifecycleResult.Applied,
                bridge.acceptGPUCompletion(ticket, first, operand),
            )

            assertIs<GPUFrameReadbackMapArmResult.Refused>(
                bridge.mapAndDepad(first, operand, GPUFrameReadbackMapSink { error("no delivery") }),
            )
            assertEquals(listOf("fail-closed"), poolEvents)

            val second = output("$mode-b")
            val secondOperand = GPUFrameCoreTestFixture.nativePayload(withReadback = true)
                .scopeOperands.filterIsInstance<GPUPreparedNativeScopeOperand.Readback>().single()
            assertEquals(
                GPUFrameReadbackLifecycleResult.Applied,
                bridge.markSubmitted(ticket, second, secondOperand),
            )
            assertNotEquals(first.stagingLease.resourceRef, second.stagingLease.resourceRef)
        }
    }

    @Test
    fun `distinct bridges sharing one provider allocate unique submission ids`() {
        val provider = GPUConcreteResourceProvider()
        val generation = GPUDeviceGenerationID(31)
        val request = GPUFrameReadbackRequest(
            GPUReadbackRequestID("bridge-id-unique"),
            GPUPixelBounds(0, 0, 4, 4),
            GPUReadbackPixelFormat.Rgba8Unorm,
            GPUColorInterpretation("srgb-premul"),
        )
        val planned = assertIs<GPUReadbackLayoutPlan.Planned>(
            GPUReadbackLayoutPlanner().plan(request, providerCapabilities()),
        )
        fun output(id: String): GPUPreparedReadbackOutput {
            val lease = assertIs<GPUReadbackStagingReservationResult.Accepted>(
                provider.reserveReadbackStaging(
                    GPUReadbackStagingReservationRequest(
                        id,
                        "owner-$id",
                        generation,
                        planned.stagingDescriptor,
                        planned.stagingDescriptor.minimumBufferBytes,
                        providerBudget(16_384),
                    ),
                ),
            ).lease
            return GPUPreparedReadbackOutput(
                GPUFrameBufferRef("buffer.$id"),
                GPUPreparedConcreteResourceRef.Buffer(lease.resourceRef),
                1,
                request,
                planned.layout,
                lease,
            )
        }
        val submissionIds = mutableListOf<GPUResourceSubmissionID>()
        val observed = object : GPUFrameResourcePreflightProvider by provider {
            override fun markReadbackSubmitted(
                leases: List<GPUReadbackStagingLease>,
                submissionId: GPUResourceSubmissionID,
            ): GPUReadbackStagingLifecycleResult {
                submissionIds += submissionId
                return provider.markReadbackSubmitted(leases, submissionId)
            }
        }
        val unusedMapper = GPUFrameNativeReadbackMapper { _, _, _ ->
            error("mapping is not part of submission-id evidence")
        }
        val first = output("unique-a")
        val second = output("unique-b")
        val firstBridge = GPUConcreteFrameReadbackAccess(
            observed,
            unusedMapper,
        )
        val secondBridge = GPUConcreteFrameReadbackAccess(
            observed,
            unusedMapper,
        )
        val firstOperand = GPUFrameCoreTestFixture.nativePayload(withReadback = true)
            .scopeOperands.filterIsInstance<GPUPreparedNativeScopeOperand.Readback>().single()
        val secondOperand = GPUFrameCoreTestFixture.nativePayload(withReadback = true)
            .scopeOperands.filterIsInstance<GPUPreparedNativeScopeOperand.Readback>().single()

        assertEquals(
            GPUFrameReadbackLifecycleResult.Applied,
            firstBridge.markSubmitted(
                GPUQueueCompletionTicket(GPUQueueCompletionTicketID("ticket.unique-a"), GPUFrameID(61), generation),
                first,
                firstOperand,
            ),
        )
        assertEquals(
            GPUFrameReadbackLifecycleResult.Applied,
            secondBridge.markSubmitted(
                GPUQueueCompletionTicket(GPUQueueCompletionTicketID("ticket.unique-b"), GPUFrameID(62), generation),
                second,
                secondOperand,
            ),
        )
        assertEquals(2, submissionIds.distinct().size)
    }

    @Test
    fun `impossible shared eviction is atomic and preserves every reclaimable entry`() {
        val provider: GPUResourceProvider = GPUConcreteResourceProvider()
        val generation = GPUDeviceGenerationID(11)
        val budget = providerBudget(16_384)
        val reclaimable = assertIs<GPUScratchTextureReservationResult.Accepted>(
            provider.reserveScratchTexture(
                providerScratchRequest("reclaimable", "frame-old", generation),
                budget,
                providerCapabilities(),
            ),
        ).lease
        assertIs<GPUPhysicalPoolMaintenanceDecision.Applied<GPUPhysicalPoolRollbackSummary>>(
            provider.rollbackPhysicalPoolsBeforeSubmit("frame-old"),
        )
        val reserved = assertIs<GPUScratchTextureReservationResult.Accepted>(
            provider.reserveScratchTexture(
                providerScratchRequest(
                    "reserved",
                    "frame-live",
                    generation,
                    format = GPUColorFormat("bgra8unorm"),
                ),
                budget,
                providerCapabilities(),
            ),
        ).lease

        val refused = assertIs<GPUPhysicalPoolMaintenanceDecision.Refused>(
            provider.evictPhysicalPoolsUntil(0),
        )
        assertEquals(
            "unsupported.physical_pool.insufficient_reclaimable_bytes",
            refused.diagnostic.code.value,
        )

        assertIs<GPUPhysicalPoolMaintenanceDecision.Applied<GPUPhysicalPoolRollbackSummary>>(
            provider.rollbackPhysicalPoolsBeforeSubmit("frame-live"),
        )
        val evicted = assertIs<GPUPhysicalPoolMaintenanceDecision.Applied<GPUPhysicalPoolEvictionSummary>>(
            provider.evictPhysicalPoolsUntil(0),
        ).value
        assertEquals(
            setOf(reclaimable.resourceRef, reserved.resourceRef),
            evicted.scratchEntries.map { entry -> entry.resourceRef }.toSet(),
        )
        assertEquals(0, evicted.remainingResidentBytes)
    }

    @Test
    fun `provider purges reclaimable physical entries and retries admission deterministically`() {
        val provider: GPUResourceProvider = GPUConcreteResourceProvider()
        val generation = GPUDeviceGenerationID(11)
        assertIs<GPUScratchTextureReservationResult.Accepted>(
            provider.reserveScratchTexture(
                providerScratchRequest("first", "frame-1", generation),
                providerBudget(4_096),
                providerCapabilities(),
            ),
        )
        assertIs<GPUPhysicalPoolMaintenanceDecision.Applied<GPUPhysicalPoolRollbackSummary>>(
            provider.rollbackPhysicalPoolsBeforeSubmit("frame-1"),
        )

        val replacement = assertIs<GPUScratchTextureReservationResult.Accepted>(
            provider.reserveScratchTexture(
                providerScratchRequest(
                    "replacement",
                    "frame-2",
                    generation,
                    format = GPUColorFormat("bgra8unorm"),
                ),
                providerBudget(4_096),
                providerCapabilities(),
            ),
        )
        assertEquals(4_096, replacement.lease.physicalBytes)
    }

    @Test
    fun `asynchronous provider exception delivers one quarantined failure and always unmaps`() {
        listOf("map", "depad").forEachIndexed { index, failurePoint ->
            val provider = GPUConcreteResourceProvider()
            val fixture = providerReadbackFixture(
                provider,
                "async-provider-$failurePoint",
                GPUDeviceGenerationID(80 + index.toLong()),
            )
            val observed = object : GPUFrameResourcePreflightProvider by provider {
                override fun markReadbackMapped(
                    lease: GPUReadbackStagingLease,
                ): GPUReadbackStagingLifecycleResult {
                    if (failurePoint == "map") error("map transition failed")
                    return provider.markReadbackMapped(lease)
                }

                override fun markReadbackDepadded(
                    lease: GPUReadbackStagingLease,
                ): GPUReadbackStagingLifecycleResult {
                    if (failurePoint == "depad") error("depad transition failed")
                    return provider.markReadbackDepadded(lease)
                }
            }
            var nativeSink: GPUFrameNativeReadbackMapSink? = null
            val bridge = GPUConcreteFrameReadbackAccess(
                observed,
                GPUFrameNativeReadbackMapper { _, _, sink ->
                    nativeSink = sink
                    GPUFrameReadbackMapArmResult.Armed
                },
            )
            assertEquals(
                GPUFrameReadbackLifecycleResult.Applied,
                bridge.markSubmitted(fixture.ticket, fixture.output, fixture.operand),
            )
            assertEquals(
                GPUFrameReadbackLifecycleResult.Applied,
                bridge.acceptGPUCompletion(fixture.ticket, fixture.output, fixture.operand),
            )
            val deliveries = mutableListOf<GPUFrameReadbackMapDelivery>()
            assertEquals(
                GPUFrameReadbackMapArmResult.Armed,
                bridge.mapAndDepad(fixture.output, fixture.operand) { deliveries += it },
            )
            var unmapCalls = 0
            val nativeDelivery = GPUFrameNativeReadbackMapDelivery.Mapped(
                object : GPUFrameMappedReadbackRange {
                    override fun copyBytesFromZero(): ByteArray =
                        ByteArray(fixture.output.layout.totalBufferBytes.toInt())
                    override fun unmap() {
                        unmapCalls += 1
                    }
                },
            )

            requireNotNull(nativeSink).accept(nativeDelivery)
            requireNotNull(nativeSink).accept(nativeDelivery)

            val failed = assertIs<GPUFrameReadbackMapDelivery.Failed>(deliveries.single())
            assertEquals(GPUFrameReadbackMapFailureSafety.Quarantine, failed.safety)
            assertEquals("failed.frame-readback.terminal-callback", failed.diagnostic.code.value)
            assertEquals(1, unmapCalls)
            assertEquals(
                GPUFrameReadbackLifecycleResult.Applied,
                bridge.finalizeAfterNativeClose(
                    fixture.output,
                    fixture.operand,
                    GPUFrameReadbackNativeOutputSafety.Quarantined,
                ),
            )
            assertEquals(
                "failed.frame-readback.finalize-identity-mismatch",
                assertIs<GPUFrameReadbackLifecycleResult.Refused>(
                    bridge.finalizeAfterNativeClose(
                        fixture.output,
                        fixture.operand,
                        GPUFrameReadbackNativeOutputSafety.Quarantined,
                    ),
                ).diagnostic.code.value,
            )
        }
    }

    @Test
    fun `accept refusal or throw quarantines exact lease and retains ledger only until cleanup succeeds`() {
        listOf("refuse", "throw", "cleanup-retry").forEachIndexed { index, mode ->
            val provider = GPUConcreteResourceProvider()
            val fixture = providerReadbackFixture(
                provider,
                "accept-fail-closed-$mode",
                GPUDeviceGenerationID(90 + index.toLong()),
            )
            var cleanupCalls = 0
            val observed = object : GPUFrameResourcePreflightProvider by provider {
                override fun acceptReadbackGPUCompletion(
                    submissionId: GPUResourceSubmissionID,
                    deviceGeneration: GPUDeviceGenerationID,
                ): GPUReadbackStagingLifecycleResult {
                    if (mode == "throw") error("accept transition failed")
                    return GPUReadbackStagingLifecycleResult.Refused(
                        executionDiagnostic("failed.test.accept-refused", "accept refused"),
                    )
                }

                override fun quarantineReadbackAfterSubmit(
                    lease: GPUReadbackStagingLease,
                ): GPUReadbackStagingLifecycleResult {
                    cleanupCalls += 1
                    if (mode == "cleanup-retry" && cleanupCalls == 1) {
                        return GPUReadbackStagingLifecycleResult.Refused(
                            executionDiagnostic("failed.test.cleanup-refused", "cleanup refused"),
                        )
                    }
                    return provider.quarantineReadbackAfterSubmit(lease)
                }
            }
            val bridge = GPUConcreteFrameReadbackAccess(
                observed,
                GPUFrameNativeReadbackMapper { _, _, _ -> error("must not map") },
            )
            assertEquals(
                GPUFrameReadbackLifecycleResult.Applied,
                bridge.markSubmitted(fixture.ticket, fixture.output, fixture.operand),
            )

            assertIs<GPUFrameReadbackLifecycleResult.Refused>(
                bridge.acceptGPUCompletion(fixture.ticket, fixture.output, fixture.operand),
            )
            if (mode == "cleanup-retry") {
                assertIs<GPUFrameReadbackLifecycleResult.Refused>(
                    bridge.acceptGPUCompletion(fixture.ticket, fixture.output, fixture.operand),
                )
                assertEquals(2, cleanupCalls)
            } else {
                assertEquals(1, cleanupCalls)
            }
            assertEquals(
                "failed.frame-readback.submission-missing",
                assertIs<GPUFrameReadbackLifecycleResult.Refused>(
                    bridge.acceptGPUCompletion(fixture.ticket, fixture.output, fixture.operand),
                ).diagnostic.code.value,
            )
            assertEquals(
                GPUFrameReadbackLifecycleResult.Applied,
                provider.quarantineReadbackAfterSubmit(fixture.output.stagingLease).let {
                    if (it is GPUReadbackStagingLifecycleResult.Accepted) {
                        GPUFrameReadbackLifecycleResult.Applied
                    } else {
                        error("fail-closed lease must remain exactly quarantined")
                    }
                },
            )
        }
    }

    @Test
    fun `finalize refusal or throw quarantines exact lease and retries retained cleanup record`() {
        listOf("refuse", "throw", "cleanup-retry").forEachIndexed { index, mode ->
            val provider = GPUConcreteResourceProvider()
            val fixture = providerReadbackFixture(
                provider,
                "finalize-fail-closed-$mode",
                GPUDeviceGenerationID(100 + index.toLong()),
            )
            var cleanupCalls = 0
            val observed = object : GPUFrameResourcePreflightProvider by provider {
                override fun releaseReadbackAfterUnmap(
                    lease: GPUReadbackStagingLease,
                ): GPUReadbackStagingLifecycleResult {
                    if (mode == "throw") error("release transition failed")
                    return GPUReadbackStagingLifecycleResult.Refused(
                        executionDiagnostic("failed.test.release-refused", "release refused"),
                    )
                }

                override fun quarantineReadbackAfterSubmit(
                    lease: GPUReadbackStagingLease,
                ): GPUReadbackStagingLifecycleResult {
                    cleanupCalls += 1
                    if (mode == "cleanup-retry" && cleanupCalls == 1) {
                        return GPUReadbackStagingLifecycleResult.Refused(
                            executionDiagnostic("failed.test.cleanup-refused", "cleanup refused"),
                        )
                    }
                    return provider.quarantineReadbackAfterSubmit(lease)
                }
            }
            val bridge = GPUConcreteFrameReadbackAccess(
                observed,
                GPUFrameNativeReadbackMapper { output, _, sink ->
                    sink.accept(
                        GPUFrameNativeReadbackMapDelivery.Mapped(
                            object : GPUFrameMappedReadbackRange {
                                override fun copyBytesFromZero(): ByteArray =
                                    ByteArray(output.layout.totalBufferBytes.toInt())
                                override fun unmap() = Unit
                            },
                        ),
                    )
                    GPUFrameReadbackMapArmResult.Armed
                },
            )
            assertEquals(
                GPUFrameReadbackLifecycleResult.Applied,
                bridge.markSubmitted(fixture.ticket, fixture.output, fixture.operand),
            )
            assertEquals(
                GPUFrameReadbackLifecycleResult.Applied,
                bridge.acceptGPUCompletion(fixture.ticket, fixture.output, fixture.operand),
            )
            assertEquals(
                GPUFrameReadbackMapArmResult.Armed,
                bridge.mapAndDepad(fixture.output, fixture.operand) {
                    assertIs<GPUFrameReadbackMapDelivery.Pixels>(it)
                },
            )

            assertIs<GPUFrameReadbackLifecycleResult.Refused>(
                bridge.finalizeAfterNativeClose(
                    fixture.output,
                    fixture.operand,
                    GPUFrameReadbackNativeOutputSafety.Released,
                ),
            )
            if (mode == "cleanup-retry") {
                assertIs<GPUFrameReadbackLifecycleResult.Refused>(
                    bridge.finalizeAfterNativeClose(
                        fixture.output,
                        fixture.operand,
                        GPUFrameReadbackNativeOutputSafety.Released,
                    ),
                )
                assertEquals(2, cleanupCalls)
            } else {
                assertEquals(1, cleanupCalls)
            }
            assertEquals(
                "failed.frame-readback.finalize-identity-mismatch",
                assertIs<GPUFrameReadbackLifecycleResult.Refused>(
                    bridge.finalizeAfterNativeClose(
                        fixture.output,
                        fixture.operand,
                        GPUFrameReadbackNativeOutputSafety.Released,
                    ),
                ).diagnostic.code.value,
            )
        }
    }
    @Test
    fun `prepared destination snapshot carries lease logical bounds and approximate physical backing`() {
        val provider = GPUConcreteResourceProvider()
        val generation = GPUDeviceGenerationID(31)
        val session = provider.beginFramePreparation(91, generation)
        val logicalBounds = GPUPixelBounds(1, 1, 3, 3)
        val preparation = GPUResourcePreparationRequest(
            resource = GPUFrameTextureRef("snapshot.logical-2x2"),
            descriptor = GPUFrameTextureDescriptor(logicalBounds, GPUColorFormat("rgba8unorm"), 1),
            role = GPUFrameResourceRole.DestinationSnapshot,
            usages = setOf(GPUFrameResourceUsage.CopyDestination, GPUFrameResourceUsage.TextureBinding),
            lifetime = GPUFrameResourceLifetime.FrameLocal,
            byteSize = 16,
            diagnosticLabel = "snapshot.logical-2x2",
        )

        val prepared = assertIs<GPUFrameResourcePreparationDecision.Prepared>(
            provider.prepareFrameResource(
                GPUFrameResourcePreparationInput(
                    preparation = preparation,
                    ownerScope = session.ownerScope,
                    deviceGeneration = generation,
                    resourceGeneration = 4,
                    firstStep = 1,
                    lastStepExclusive = 4,
                    budgetPlan = providerBudget(16_384),
                    capabilities = providerCapabilities(),
                ),
            ),
        )
        val allocation = requireNotNull(prepared.textureAllocation)
        assertEquals(logicalBounds, allocation.logicalBounds)
        assertEquals(16, allocation.backingWidth)
        assertEquals(16, allocation.backingHeight)
        assertEquals(GPUColorFormat("rgba8unorm"), allocation.format)
        assertEquals(1, allocation.sampleCount)
        assertEquals(
            setOf(GPUFrameResourceUsage.CopyDestination, GPUFrameResourceUsage.TextureBinding),
            allocation.usages,
        )

        assertIs<GPUPhysicalPoolMaintenanceDecision.Applied<GPUPhysicalPoolRollbackSummary>>(
            provider.rollbackFrameResourcesBeforeSubmit(session.ownerScope),
        )
        assertEquals(0, provider.pendingPhysicalReservationCount)
    }
}

private data class ProviderReadbackFixture(
    val output: GPUPreparedReadbackOutput,
    val operand: GPUPreparedNativeScopeOperand.Readback,
    val ticket: GPUQueueCompletionTicket,
)

private fun providerReadbackFixture(
    provider: GPUConcreteResourceProvider,
    id: String,
    generation: GPUDeviceGenerationID,
): ProviderReadbackFixture {
    val request = GPUFrameReadbackRequest(
        GPUReadbackRequestID(id),
        GPUPixelBounds(0, 0, 4, 4),
        GPUReadbackPixelFormat.Rgba8Unorm,
        GPUColorInterpretation("srgb-premul"),
    )
    val planned = assertIs<GPUReadbackLayoutPlan.Planned>(
        GPUReadbackLayoutPlanner().plan(request, providerCapabilities()),
    )
    val lease = assertIs<GPUReadbackStagingReservationResult.Accepted>(
        provider.reserveReadbackStaging(
            GPUReadbackStagingReservationRequest(
                id,
                "owner-$id",
                generation,
                planned.stagingDescriptor,
                planned.stagingDescriptor.minimumBufferBytes,
                providerBudget(16_384),
            ),
        ),
    ).lease
    return ProviderReadbackFixture(
        GPUPreparedReadbackOutput(
            GPUFrameBufferRef("buffer.$id"),
            GPUPreparedConcreteResourceRef.Buffer(lease.resourceRef),
            1,
            request,
            planned.layout,
            lease,
        ),
        GPUFrameCoreTestFixture.nativePayload(withReadback = true)
            .scopeOperands.filterIsInstance<GPUPreparedNativeScopeOperand.Readback>().single(),
        GPUQueueCompletionTicket(
            GPUQueueCompletionTicketID("ticket.$id"),
            GPUFrameID(id.hashCode().toLong().let(Math::abs)),
            generation,
        ),
    )
}

private fun providerScratchRequest(
    id: String,
    scope: String,
    generation: GPUDeviceGenerationID,
    format: GPUColorFormat = GPUColorFormat("rgba8unorm"),
): GPUScratchTextureReservationRequest {
    val bounds = GPUPixelBounds(0, 0, 17, 17)
    return GPUScratchTextureReservationRequest(
        reservationId = id,
        reservationScope = scope,
        preparation = GPUResourcePreparationRequest(
            resource = GPUFrameTextureRef("provider-$id"),
            descriptor = GPUFrameTextureDescriptor(bounds, format, 1),
            role = GPUFrameResourceRole.FilterTarget,
            usages = setOf(
                GPUFrameResourceUsage.RenderAttachment,
                GPUFrameResourceUsage.TextureBinding,
            ),
            lifetime = GPUFrameResourceLifetime.FrameLocal,
            byteSize = bounds.checkedByteSize(4, 1),
            diagnosticLabel = "provider-$id",
        ),
        deviceGeneration = generation,
        firstStep = 0,
        lastStepExclusive = 2,
    )
}

private fun providerBudget(configuredBytes: Long): GPUFrameMemoryBudgetPlan =
    GPUFrameMemoryBudgetPlan(
        peakFrameTransientBytes = 0,
        targetResidentBytes = 0,
        categoryTotals = GPUFrameMemoryCategory.entries.associateWith { 0L },
        deviceLimitFacts = emptyList(),
        configuredAggregateBudgetBytes = configuredBytes,
        diagnostic = null,
    )

private fun providerCapabilities(): GPUCapabilities = GPUCapabilities(
    implementation = GPUImplementationIdentity(
        facadeName = "GPU",
        implementationName = "unit",
        adapterName = "unit-adapter",
        deviceName = "unit-device",
    ),
    facts = emptyList(),
    snapshotId = "provider-task7",
    limits = GPULimits(
        maxTextureDimension2D = 16_384,
        copyBytesPerRowAlignment = 256,
        minUniformBufferOffsetAlignment = 256,
        maxBufferSize = 1L shl 30,
    ),
    rendererFeatures = setOf(GPURendererFeature.Readback),
)

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
