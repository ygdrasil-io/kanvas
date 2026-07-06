package org.graphiks.kanvas.gpu.renderer.resources

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPayloadFingerprint
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPayloadSlotID
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPayloadUploadPlan
import org.graphiks.kanvas.gpu.renderer.payloads.GPUResourceBindingBlock
import org.graphiks.kanvas.gpu.renderer.payloads.GPUResourceBindingFact
import org.graphiks.kanvas.gpu.renderer.payloads.GPUResourceBindingKind
import org.graphiks.kanvas.gpu.renderer.payloads.GPUResourceBindingSlot
import org.graphiks.kanvas.gpu.renderer.payloads.GPUUniformPayloadBlock
import org.graphiks.kanvas.gpu.renderer.payloads.GPUUniformPayloadSlot

/** Verifies KGPU-M11-002 payload upload and bind-group materialization contracts. */
class GPUPayloadMaterializationProviderTest {
    /** Accepted uniform payloads become upload buffers and bind-group command operands. */
    @Test
    fun `payload provider uploads uniform bytes and materializes bind group bridge`() {
        val decision = ValidatingPayloadResourceProvider().materializePayloadBindings(
            request = payloadMaterializationRequest(),
            context = targetPreparationContext(),
        )

        val materialized = assertIs<GPUResourceMaterializationDecision.Materialized>(decision)
        assertEquals(emptyList(), materialized.resources)
        assertEquals(
            listOf(
                "payload-upload:pass-a:uniform:0" to GPUMaterializedCommandOperandKind.UniformBuffer,
                "bind-group:pass-a:resource:0" to GPUMaterializedCommandOperandKind.BindGroup,
            ),
            materialized.dumpOperandBridgeSnapshot.map { binding -> binding.operand.label to binding.operand.kind },
        )
        assertEquals(
            listOf("create", "create"),
            materialized.dumpPayloadTelemetrySnapshot.map { event -> event.result.dumpToken },
        )

        val lines = materialized.dumpLines()
        assertContains(
            lines,
            "resource.materialization:operand operand=payload-upload:pass-a:uniform:0 kind=uniform-buffer " +
                "deviceGeneration=11 owner=payload-scope:pass-a usage=copy_dst,uniform " +
                "invalidation=pass-end descriptor=uniform-fingerprint-solid " +
                "facts=alignment=256;bindingLayout=layout-solid-v1;byteSize=64;generation=7;" +
                "scope=pass-a;uploadPlan=upload-solid-v1;uploadScope=pass-a-staging;zeroedPadding=true",
        )
        assertContains(
            lines,
            "resource.materialization:operand operand=bind-group:pass-a:resource:0 kind=bind-group " +
                "deviceGeneration=11 owner=payload-scope:pass-a usage=uniform " +
                "invalidation=pass-end descriptor=layout-solid-v1 " +
                "facts=bindingCount=1;dynamicOffsets=0;layoutHash=layout-solid-v1;" +
                "resourceDescriptors=uniform:solid-payload;uniformBuffer=payload-upload:pass-a:uniform:0",
        )
        assertContains(
            lines,
            "payload.materialization.telemetry lane=uniform-upload result=create " +
                "key=uniform-fingerprint-solid subject=upload-solid-v1 productRouteActivated=false",
        )
        assertContains(
            lines,
            "payload.materialization.telemetry lane=bind-group result=create " +
                "key=resource-fingerprint-solid subject=layout-solid-v1 productRouteActivated=false",
        )
        assertFalse(lines.joinToString("\n").contains("WGPU"))
        assertFalse(lines.joinToString("\n").contains("@0x"))
    }

    /** Accepted payload provider requests can seed the slab batch planner evidence. */
    @Test
    fun `accepted payload provider request can seed a payload slab batch plan`() {
        val request = payloadMaterializationRequest()
        val providerDecision = ValidatingPayloadResourceProvider().materializePayloadBindings(
            request = request,
            context = targetPreparationContext(),
        )
        assertIs<GPUResourceMaterializationDecision.Materialized>(providerDecision)

        val planning = GPUPayloadSlabBatchPlanner.plan(
            GPUPayloadSlabBatchRequest(
                targetId = "root-target",
                frameId = "frame-1",
                sourceLabel = "fullscreen-uniform-pass",
                deviceGeneration = 11L,
                alignmentBytes = 256L,
                uploadBudgetBytes = 1024L,
                payloadRequests = listOf(request),
            ),
        )

        val plan = assertIs<GPUPayloadSlabBatchPlanningResult.Accepted>(planning).plan
        assertEquals("packet-9", plan.slotBindings.single().packetId)
        assertEquals("pass-a:uniform:0", plan.slotBindings.single().uniformSlotId)
        assertEquals("pass-a:resource:0", plan.slotBindings.single().resourceSlotId)
        assertEquals("uniform-fingerprint-solid", plan.slotBindings.single().payloadFingerprint)
        assertContains(
            plan.dumpLines(),
            "payload-slab.batch.slot source=fullscreen-uniform-pass slot=packet-9:pass-a:uniform:0:pass-a:resource:0 " +
                "packet=packet-9 uniformSlot=pass-a:uniform:0 resourceSlot=pass-a:resource:0 offset=0 " +
                "payloadBytes=64 payloadFingerprint=uniform-fingerprint-solid layout=layout-solid-v1",
        )
    }

    @Test
    fun `accepted gradient payload provider request can seed a gradient payload slab batch plan`() {
        val firstRequest = payloadMaterializationRequest(
            packetId = "gradient-packet-0",
            resourcePlanLabel = "gradient-linear-0",
            uniformBlock = uniformBlock(
                fingerprint = "uniform-fingerprint-gradient-0",
                packingPlanHash = "linear-gradient-layout-v1",
                bytes = gradientUniformBytes(seed = 1),
            ),
            uniformSlot = uniformSlot(
                slotId = "gradient-pass:uniform:0",
                fingerprint = "uniform-fingerprint-gradient-0",
            ),
            resourceBlock = resourceBlock(
                fingerprint = "resource-fingerprint-gradient-0",
                bindingPlanHash = "layout:linear-gradient-material-block:v1",
                resourceDescriptorLabels = listOf("uniform:gradient-material-payload"),
            ),
            resourceSlot = resourceSlot(
                slotId = "gradient-pass:resource:0",
                fingerprint = "resource-fingerprint-gradient-0",
            ),
            uploadPlan = uploadPlan(
                planHash = "upload-gradient-v1-0",
                stagingScope = "gradient-pass-staging",
                beforeUseToken = "before-gradient-draw-0",
            ),
            reflectedBindingLayoutHash = "layout:linear-gradient-material-block:v1",
        )
        val secondRequest = payloadMaterializationRequest(
            packetId = "gradient-packet-1",
            resourcePlanLabel = "gradient-linear-1",
            uniformBlock = uniformBlock(
                fingerprint = "uniform-fingerprint-gradient-1",
                packingPlanHash = "linear-gradient-layout-v1",
                bytes = gradientUniformBytes(seed = 2),
            ),
            uniformSlot = uniformSlot(
                slotId = "gradient-pass:uniform:1",
                fingerprint = "uniform-fingerprint-gradient-1",
            ),
            resourceBlock = resourceBlock(
                fingerprint = "resource-fingerprint-gradient-1",
                bindingPlanHash = "layout:linear-gradient-material-block:v1",
                resourceDescriptorLabels = listOf("uniform:gradient-material-payload"),
            ),
            resourceSlot = resourceSlot(
                slotId = "gradient-pass:resource:1",
                fingerprint = "resource-fingerprint-gradient-1",
            ),
            uploadPlan = uploadPlan(
                planHash = "upload-gradient-v1-1",
                stagingScope = "gradient-pass-staging",
                beforeUseToken = "before-gradient-draw-1",
            ),
            reflectedBindingLayoutHash = "layout:linear-gradient-material-block:v1",
        )

        val provider = ValidatingPayloadResourceProvider()
        val context = targetPreparationContext()
        assertIs<GPUResourceMaterializationDecision.Materialized>(
            provider.materializePayloadBindings(firstRequest, context),
        )
        assertIs<GPUResourceMaterializationDecision.Materialized>(
            provider.materializePayloadBindings(secondRequest, context),
        )

        val slab = GPUPayloadSlabBatchPlanner.plan(
            GPUPayloadSlabBatchRequest(
                targetId = context.targetId,
                frameId = context.frameId,
                sourceLabel = "gradient-material-pass",
                deviceGeneration = context.deviceGeneration,
                alignmentBytes = 256L,
                uploadBudgetBytes = 1024L,
                payloadRequests = listOf(firstRequest, secondRequest),
            ),
        )

        val accepted = assertIs<GPUPayloadSlabBatchPlanningResult.Accepted>(slab)
        assertEquals("gradient-material-pass", accepted.plan.sourceLabel)
        assertEquals(2, accepted.plan.slotBindings.size)
        assertEquals(
            listOf("layout:linear-gradient-material-block:v1", "layout:linear-gradient-material-block:v1"),
            accepted.plan.slotBindings.map { binding -> binding.reflectedBindingLayoutHash },
        )
        assertEquals(
            listOf("gradient-packet-0", "gradient-packet-1"),
            accepted.plan.slotBindings.map { binding -> binding.packetId },
        )
        assertEquals(
            listOf("gradient-pass:uniform:0", "gradient-pass:uniform:1"),
            accepted.plan.slotBindings.map { binding -> binding.uniformSlotId },
        )
        assertEquals(
            listOf("gradient-pass:resource:0", "gradient-pass:resource:1"),
            accepted.plan.slotBindings.map { binding -> binding.resourceSlotId },
        )
        assertEquals(
            listOf("uniform-fingerprint-gradient-0", "uniform-fingerprint-gradient-1"),
            accepted.plan.slotBindings.map { binding -> binding.payloadFingerprint },
        )
        assertEquals(listOf(0L, 256L), accepted.plan.slotBindings.map { binding -> binding.alignedOffset })
        assertEquals(listOf(64L, 64L), accepted.plan.slotBindings.map { binding -> binding.payloadBytes })
        assertContains(
            accepted.plan.dumpLines(),
            "payload-slab.batch.slot source=gradient-material-pass " +
                "slot=gradient-packet-0:gradient-pass:uniform:0:gradient-pass:resource:0 " +
                "packet=gradient-packet-0 uniformSlot=gradient-pass:uniform:0 " +
                "resourceSlot=gradient-pass:resource:0 offset=0 payloadBytes=64 " +
                "payloadFingerprint=uniform-fingerprint-gradient-0 " +
                "layout=layout:linear-gradient-material-block:v1",
        )
    }

    /** Provider-owned cache facts report reuse without changing support status. */
    @Test
    fun `payload provider records bind group and upload reuse telemetry`() {
        val provider = ValidatingPayloadResourceProvider()

        provider.materializePayloadBindings(
            request = payloadMaterializationRequest(),
            context = targetPreparationContext(),
        )
        val second = provider.materializePayloadBindings(
            request = payloadMaterializationRequest(),
            context = targetPreparationContext(),
        )

        val materialized = assertIs<GPUResourceMaterializationDecision.Materialized>(second)
        assertEquals(
            listOf("reuse", "reuse"),
            materialized.dumpPayloadTelemetrySnapshot.map { event -> event.result.dumpToken },
        )
        assertContains(
            materialized.dumpLines(),
            "payload.materialization.telemetry lane=bind-group result=reuse " +
                "key=resource-fingerprint-solid subject=layout-solid-v1 productRouteActivated=false",
        )
    }

    /** Pass-local fingerprints must not create false reuse facts across payload scopes. */
    @Test
    fun `payload provider reuse telemetry is scoped by target pass slot and payload generation`() {
        val provider = ValidatingPayloadResourceProvider()

        provider.materializePayloadBindings(
            request = payloadMaterializationRequest(),
            context = targetPreparationContext(),
        )
        val differentPass = provider.materializePayloadBindings(
            request = payloadMaterializationRequest(
                uniformBlock = uniformBlock(scope = "pass-b"),
                uniformSlot = uniformSlot(slotId = "pass-b:uniform:0"),
                resourceBlock = resourceBlock(),
                resourceSlot = resourceSlot(slotId = "pass-b:resource:0"),
            ),
            context = targetPreparationContext(),
        )
        val differentGeneration = provider.materializePayloadBindings(
            request = payloadMaterializationRequest(payloadGeneration = 8L),
            context = targetPreparationContext(),
        )

        assertEquals(
            listOf("create", "create"),
            assertIs<GPUResourceMaterializationDecision.Materialized>(
                differentPass,
            ).dumpPayloadTelemetrySnapshot.map { event -> event.result.dumpToken },
        )
        assertEquals(
            listOf("create", "create"),
            assertIs<GPUResourceMaterializationDecision.Materialized>(
                differentGeneration,
            ).dumpPayloadTelemetrySnapshot.map { event -> event.result.dumpToken },
        )
    }

    /** Invalid payload or layout facts refuse before command encoding. */
    @Test
    fun `payload provider refuses missing usage stale generation layout mismatch dynamic offsets budget and capability`() {
        assertPayloadRefused(
            request = payloadMaterializationRequest(availableUniformUsageLabels = setOf("copy_dst")),
            expectedCode = "unsupported.resource.command_operand_usage_missing",
        )
        assertPayloadRefused(
            request = payloadMaterializationRequest(deviceGeneration = 10),
            expectedCode = "unsupported.texture.device_generation_stale",
        )
        assertPayloadRefused(
            request = payloadMaterializationRequest(resourceBlock = resourceBlock(bindingPlanHash = "layout-other-v1")),
            expectedCode = "unsupported.resource.binding_layout_mismatch",
        )
        assertPayloadRefused(
            request = payloadMaterializationRequest(resourceBlock = resourceBlock(dynamicOffsets = listOf(0L, 256L))),
            expectedCode = "unsupported.resource.dynamic_offsets_exceeded",
        )
        assertPayloadRefused(
            request = payloadMaterializationRequest(uploadBudgetBytes = 16L),
            expectedCode = "budget.resource.upload_exceeded",
        )
        assertPayloadRefused(
            request = payloadMaterializationRequest(uploadCapabilityAvailable = false),
            expectedCode = "unsupported.resource.upload_capability_missing",
        )
    }

    /** Upload byte ranges must cover exactly the uniform payload byte count. */
    @Test
    fun `payload provider refuses invalid upload plan byte ranges`() {
        listOf(
            emptyList(),
            listOf(0L..31L),
            listOf(1L..64L),
            listOf(0L..15L, 17L..63L),
            listOf(0L..63L, 32L..63L),
            listOf(0L..64L),
        ).forEach { byteRanges ->
            assertPayloadRefused(
                request = payloadMaterializationRequest(uploadPlan = uploadPlan(byteRanges = byteRanges)),
                expectedCode = "unsupported.resource.upload_range_invalid",
            )
        }
    }

    /** Resource binding blocks must carry enough facts to refuse unsupported or stale sampled resources. */
    @Test
    fun `payload provider refuses sampled resources without valid binding facts`() {
        assertPayloadRefused(
            request = payloadMaterializationRequest(
                resourceBlock = resourceBlock(resourceDescriptorLabels = listOf("texture:paint-image")),
            ),
            expectedCode = "unsupported.resource.binding_fact_missing",
        )
        assertPayloadRefused(
            request = payloadMaterializationRequest(
                resourceBlock = resourceBlock(resourceDescriptorLabels = listOf("storage:gradient-stops")),
            ),
            expectedCode = "unsupported.resource.binding_fact_missing",
        )
        assertPayloadRefused(
            request = payloadMaterializationRequest(
                resourceBlock = resourceBlock(
                    resourceDescriptorLabels = listOf("texture:paint-image"),
                    bindingFacts = listOf(
                        sampledTextureFact(availableUsageLabels = setOf("copy_src")),
                    ),
                ),
            ),
            expectedCode = "unsupported.resource.command_operand_usage_missing",
        )
        assertPayloadRefused(
            request = payloadMaterializationRequest(
                resourceBlock = resourceBlock(
                    resourceDescriptorLabels = listOf("texture:paint-image"),
                    bindingFacts = listOf(
                        sampledTextureFact(actualGeneration = 6L),
                    ),
                ),
            ),
            expectedCode = "unsupported.resource.binding_generation_stale",
        )
        assertPayloadRefused(
            request = payloadMaterializationRequest(
                resourceBlock = resourceBlock(
                    resourceDescriptorLabels = listOf("texture:paint-image", "sampler:other-image"),
                    bindingFacts = listOf(
                        sampledTextureFact(),
                        samplerFact(bindingLabel = "sampler:other-image"),
                    ),
                ),
            ),
            expectedCode = "unsupported.resource.sampled_texture_sampler_missing",
        )
        assertPayloadRefused(
            request = payloadMaterializationRequest(
                resourceBlock = resourceBlock(
                    resourceDescriptorLabels = listOf(
                        "texture:paint-image",
                        "texture:mask-image",
                        "sampler:paint-image",
                    ),
                    bindingFacts = listOf(
                        sampledTextureFact(),
                        sampledTextureFact(bindingLabel = "texture:mask-image"),
                        samplerFact(),
                    ),
                ),
            ),
            expectedCode = "unsupported.resource.sampled_texture_sampler_missing",
        )
        assertPayloadRefused(
            request = payloadMaterializationRequest(
                resourceBlock = resourceBlock(
                    resourceDescriptorLabels = listOf("texture:paint-image"),
                    bindingFacts = listOf(
                        sampledTextureFact(),
                        samplerFact(),
                    ),
                ),
            ),
            expectedCode = "unsupported.resource.binding_fact_unexpected",
        )
    }

    /** Accepted sampled binding facts must be visible in bind group evidence dumps. */
    @Test
    fun `payload provider dumps structured sampled resource binding facts`() {
        val decision = ValidatingPayloadResourceProvider().materializePayloadBindings(
            request = payloadMaterializationRequest(
                resourceBlock = resourceBlock(
                    resourceDescriptorLabels = listOf("texture:paint-image", "sampler:paint-image"),
                    bindingFacts = listOf(sampledTextureFact(), samplerFact()),
                ),
            ),
            context = targetPreparationContext(),
        )

        val materialized = assertIs<GPUResourceMaterializationDecision.Materialized>(decision)
        assertEquals(
            listOf(
                "payload-upload:pass-a:uniform:0" to GPUMaterializedCommandOperandKind.UniformBuffer,
                "bind-group:pass-a:resource:0" to GPUMaterializedCommandOperandKind.BindGroup,
                "texture-view:texture:paint-image" to GPUMaterializedCommandOperandKind.TextureView,
                "sampler:sampler:paint-image" to GPUMaterializedCommandOperandKind.Sampler,
            ),
            materialized.dumpOperandBridgeSnapshot.map { binding -> binding.operand.label to binding.operand.kind },
        )
        assertContains(
            materialized.dumpLines(),
            "resource.materialization:operand operand=bind-group:pass-a:resource:0 kind=bind-group " +
                "deviceGeneration=11 owner=payload-scope:pass-a usage=sampler,texture_binding,uniform " +
                "invalidation=pass-end descriptor=layout-solid-v1 " +
                "facts=bindingCount=2;dynamicOffsets=0;layoutHash=layout-solid-v1;" +
                "resourceBindingFacts=sampler:paint-image:sampler:" +
                "descriptor=sampler-descriptor:paint-image:usage=sampler:generation=7," +
                "texture:paint-image:sampled-texture:" +
                "descriptor=texture-descriptor:paint-image:usage=texture_binding:generation=7;" +
                "resourceDescriptors=sampler:paint-image,texture:paint-image;" +
                "uniformBuffer=payload-upload:pass-a:uniform:0",
        )
    }

    private fun assertPayloadRefused(
        request: GPUPayloadMaterializationRequest,
        expectedCode: String,
    ) {
        val decision = ValidatingPayloadResourceProvider().materializePayloadBindings(
            request = request,
            context = targetPreparationContext(),
        )

        val refused = assertIs<GPUResourceMaterializationDecision.Refused>(decision)
        assertContains(refused.diagnostics.map { diagnostic -> diagnostic.code }, expectedCode)
        assertEquals(
            listOf("failure", "failure"),
            refused.dumpPayloadTelemetrySnapshot.map { event -> event.result.dumpToken },
        )
        assertFalse(refused.dumpLines().joinToString("\n").contains("WGPU"))
    }

    private fun payloadMaterializationRequest(
        deviceGeneration: Long = 11L,
        payloadGeneration: Long = 7L,
        uploadBudgetBytes: Long = 256L,
        uploadCapabilityAvailable: Boolean = true,
        availableUniformUsageLabels: Set<String> = setOf("copy_dst", "uniform"),
        packetId: String = "packet-9",
        resourcePlanLabel: String = "solid-fill",
        reflectedBindingLayoutHash: String = "layout-solid-v1",
        uniformBlock: GPUUniformPayloadBlock = uniformBlock(),
        uniformSlot: GPUUniformPayloadSlot = uniformSlot(),
        resourceBlock: GPUResourceBindingBlock = resourceBlock(),
        resourceSlot: GPUResourceBindingSlot = resourceSlot(),
        uploadPlan: GPUPayloadUploadPlan = uploadPlan(),
    ): GPUPayloadMaterializationRequest =
        GPUPayloadMaterializationRequest(
            targetId = "root-target",
            packetId = packetId,
            taskIds = listOf("task-payload-upload"),
            resourcePlanLabels = listOf("payload-materialization:$resourcePlanLabel"),
            uniformBlock = uniformBlock,
            uniformSlot = uniformSlot,
            resourceBlock = resourceBlock,
            resourceSlot = resourceSlot,
            uploadPlan = uploadPlan,
            reflectedBindingLayoutHash = reflectedBindingLayoutHash,
            deviceGeneration = deviceGeneration,
            payloadGeneration = payloadGeneration,
            alignmentBytes = 256L,
            uploadBudgetBytes = uploadBudgetBytes,
            uploadCapabilityAvailable = uploadCapabilityAvailable,
            maxDynamicOffsets = 1,
            requiredUniformUsageLabels = setOf("copy_dst", "uniform"),
            availableUniformUsageLabels = availableUniformUsageLabels,
        )

    private fun uniformBlock(
        scope: String = "pass-a",
        fingerprint: String = "uniform-fingerprint-solid",
        packingPlanHash: String = "solid-rect-layout-v1",
        bytes: List<Int> = listOf(1, 2, 3, 4) + List(60) { 0 },
    ): GPUUniformPayloadBlock =
        GPUUniformPayloadBlock(
            fingerprint = GPUPayloadFingerprint(fingerprint),
            packingPlanHash = packingPlanHash,
            byteSize = bytes.size.toLong(),
            zeroedPadding = true,
            scope = scope,
            bytes = bytes,
        )

    private fun uniformSlot(
        slotId: String = "pass-a:uniform:0",
        fingerprint: String = "uniform-fingerprint-solid",
    ): GPUUniformPayloadSlot =
        GPUUniformPayloadSlot(
            slotId = GPUPayloadSlotID(slotId),
            fingerprint = GPUPayloadFingerprint(fingerprint),
            byteOffset = 0L,
        )

    private fun resourceBlock(
        fingerprint: String = "resource-fingerprint-solid",
        bindingPlanHash: String = "layout-solid-v1",
        dynamicOffsets: List<Long> = listOf(0L),
        resourceDescriptorLabels: List<String> = listOf("uniform:solid-payload"),
        bindingFacts: List<GPUResourceBindingFact> = emptyList(),
        bindingCount: Int = resourceDescriptorLabels.size,
    ): GPUResourceBindingBlock =
        GPUResourceBindingBlock(
            fingerprint = GPUPayloadFingerprint(fingerprint),
            bindingPlanHash = bindingPlanHash,
            bindingCount = bindingCount,
            resourceDescriptorLabels = resourceDescriptorLabels,
            dynamicOffsets = dynamicOffsets,
            bindingFacts = bindingFacts,
        )

    private fun resourceSlot(
        slotId: String = "pass-a:resource:0",
        fingerprint: String = "resource-fingerprint-solid",
    ): GPUResourceBindingSlot =
        GPUResourceBindingSlot(
            slotId = GPUPayloadSlotID(slotId),
            fingerprint = GPUPayloadFingerprint(fingerprint),
            bindingIndex = 0,
        )

    private fun sampledTextureFact(
        bindingLabel: String = "texture:paint-image",
        actualGeneration: Long = 7L,
        availableUsageLabels: Set<String> = setOf("texture_binding"),
    ): GPUResourceBindingFact =
        GPUResourceBindingFact(
            bindingLabel = bindingLabel,
            kind = GPUResourceBindingKind.SampledTexture,
            descriptorHash = "texture-descriptor:${bindingLabel.substringAfter(':')}",
            requiredUsageLabels = setOf("texture_binding"),
            availableUsageLabels = availableUsageLabels,
            expectedResourceGeneration = 7L,
            actualResourceGeneration = actualGeneration,
        )

    private fun samplerFact(
        bindingLabel: String = "sampler:paint-image",
        actualGeneration: Long = 7L,
        availableUsageLabels: Set<String> = setOf("sampler"),
    ): GPUResourceBindingFact =
        GPUResourceBindingFact(
            bindingLabel = bindingLabel,
            kind = GPUResourceBindingKind.Sampler,
            descriptorHash = "sampler-descriptor:${bindingLabel.substringAfter(':')}",
            requiredUsageLabels = setOf("sampler"),
            availableUsageLabels = availableUsageLabels,
            expectedResourceGeneration = 7L,
            actualResourceGeneration = actualGeneration,
        )

    private fun uploadPlan(
        planHash: String = "upload-solid-v1",
        byteRanges: List<LongRange> = listOf(0L..63L),
        stagingScope: String = "pass-a-staging",
        beforeUseToken: String = "before-draw-9",
    ): GPUPayloadUploadPlan =
        GPUPayloadUploadPlan(
            planHash = planHash,
            byteRanges = byteRanges,
            stagingScope = stagingScope,
            budgetClass = "unit-test",
            beforeUseToken = beforeUseToken,
        )

    private fun gradientUniformBytes(seed: Int): List<Int> {
        val bytes = ByteArray(64)
        bytes[0] = seed.toByte()
        bytes[4] = (seed + 1).toByte()
        bytes[8] = (seed + 2).toByte()
        bytes[12] = 1
        return bytes.map { byte -> byte.toInt() and 0xff }
    }

    private fun targetPreparationContext(): GPUTargetPreparationContext =
        GPUTargetPreparationContext(
            targetId = "root-target",
            frameId = "frame-1",
            deviceGeneration = 11,
            budgetClass = "unit-test",
        )
}
