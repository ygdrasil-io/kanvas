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
    }

    /** Accepted sampled binding facts must be visible in bind group evidence dumps. */
    @Test
    fun `payload provider dumps structured sampled resource binding facts`() {
        val decision = ValidatingPayloadResourceProvider().materializePayloadBindings(
            request = payloadMaterializationRequest(
                resourceBlock = resourceBlock(
                    resourceDescriptorLabels = listOf("texture:paint-image"),
                    bindingFacts = listOf(sampledTextureFact()),
                ),
            ),
            context = targetPreparationContext(),
        )

        val materialized = assertIs<GPUResourceMaterializationDecision.Materialized>(decision)
        assertContains(
            materialized.dumpLines(),
            "resource.materialization:operand operand=bind-group:pass-a:resource:0 kind=bind-group " +
                "deviceGeneration=11 owner=payload-scope:pass-a usage=uniform " +
                "invalidation=pass-end descriptor=layout-solid-v1 " +
                "facts=bindingCount=1;dynamicOffsets=0;layoutHash=layout-solid-v1;" +
                "resourceBindingFacts=texture:paint-image:sampled-texture:" +
                "descriptor=texture-descriptor:paint-image:usage=texture_binding:generation=7;" +
                "resourceDescriptors=texture:paint-image;uniformBuffer=payload-upload:pass-a:uniform:0",
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
        uniformBlock: GPUUniformPayloadBlock = uniformBlock(),
        uniformSlot: GPUUniformPayloadSlot = uniformSlot(),
        resourceBlock: GPUResourceBindingBlock = resourceBlock(),
        resourceSlot: GPUResourceBindingSlot = resourceSlot(),
        uploadPlan: GPUPayloadUploadPlan = uploadPlan(),
    ): GPUPayloadMaterializationRequest =
        GPUPayloadMaterializationRequest(
            targetId = "root-target",
            packetId = "packet-9",
            taskIds = listOf("task-payload-upload"),
            resourcePlanLabels = listOf("payload-materialization:solid-fill"),
            uniformBlock = uniformBlock,
            uniformSlot = uniformSlot,
            resourceBlock = resourceBlock,
            resourceSlot = resourceSlot,
            uploadPlan = uploadPlan,
            reflectedBindingLayoutHash = "layout-solid-v1",
            deviceGeneration = deviceGeneration,
            payloadGeneration = payloadGeneration,
            alignmentBytes = 256L,
            uploadBudgetBytes = uploadBudgetBytes,
            uploadCapabilityAvailable = uploadCapabilityAvailable,
            maxDynamicOffsets = 1,
            requiredUniformUsageLabels = setOf("copy_dst", "uniform"),
            availableUniformUsageLabels = availableUniformUsageLabels,
        )

    private fun uniformBlock(scope: String = "pass-a"): GPUUniformPayloadBlock =
        GPUUniformPayloadBlock(
            fingerprint = GPUPayloadFingerprint("uniform-fingerprint-solid"),
            packingPlanHash = "solid-rect-layout-v1",
            byteSize = 64L,
            zeroedPadding = true,
            scope = scope,
            bytes = listOf(1, 2, 3, 4) + List(60) { 0 },
        )

    private fun uniformSlot(slotId: String = "pass-a:uniform:0"): GPUUniformPayloadSlot =
        GPUUniformPayloadSlot(
            slotId = GPUPayloadSlotID(slotId),
            fingerprint = GPUPayloadFingerprint("uniform-fingerprint-solid"),
            byteOffset = 0L,
        )

    private fun resourceBlock(
        bindingPlanHash: String = "layout-solid-v1",
        dynamicOffsets: List<Long> = listOf(0L),
        resourceDescriptorLabels: List<String> = listOf("uniform:solid-payload"),
        bindingFacts: List<GPUResourceBindingFact> = emptyList(),
    ): GPUResourceBindingBlock =
        GPUResourceBindingBlock(
            fingerprint = GPUPayloadFingerprint("resource-fingerprint-solid"),
            bindingPlanHash = bindingPlanHash,
            bindingCount = 1,
            resourceDescriptorLabels = resourceDescriptorLabels,
            dynamicOffsets = dynamicOffsets,
            bindingFacts = bindingFacts,
        )

    private fun resourceSlot(slotId: String = "pass-a:resource:0"): GPUResourceBindingSlot =
        GPUResourceBindingSlot(
            slotId = GPUPayloadSlotID(slotId),
            fingerprint = GPUPayloadFingerprint("resource-fingerprint-solid"),
            bindingIndex = 0,
        )

    private fun sampledTextureFact(
        actualGeneration: Long = 7L,
        availableUsageLabels: Set<String> = setOf("texture_binding"),
    ): GPUResourceBindingFact =
        GPUResourceBindingFact(
            bindingLabel = "texture:paint-image",
            kind = GPUResourceBindingKind.SampledTexture,
            descriptorHash = "texture-descriptor:paint-image",
            requiredUsageLabels = setOf("texture_binding"),
            availableUsageLabels = availableUsageLabels,
            expectedResourceGeneration = 7L,
            actualResourceGeneration = actualGeneration,
        )

    private fun uploadPlan(byteRanges: List<LongRange> = listOf(0L..63L)): GPUPayloadUploadPlan =
        GPUPayloadUploadPlan(
            planHash = "upload-solid-v1",
            byteRanges = byteRanges,
            stagingScope = "pass-a-staging",
            budgetClass = "unit-test",
            beforeUseToken = "before-draw-9",
        )

    private fun targetPreparationContext(): GPUTargetPreparationContext =
        GPUTargetPreparationContext(
            targetId = "root-target",
            frameId = "frame-1",
            deviceGeneration = 11,
            budgetClass = "unit-test",
        )
}
