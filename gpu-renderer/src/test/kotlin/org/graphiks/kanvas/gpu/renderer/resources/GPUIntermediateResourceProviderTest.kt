package org.graphiks.kanvas.gpu.renderer.resources

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import org.graphiks.kanvas.gpu.renderer.intermediates.GPUIntermediatePurpose
import org.graphiks.kanvas.gpu.renderer.intermediates.GPUIntermediateTextureDescriptor

class GPUIntermediateResourceProviderTest {
    @Test
    fun `intermediate texture create then reuse uses descriptor generation lifetime and owner`() {
        val provider = GPUConcreteResourceProvider()
        val context = context()
        val request = GPUIntermediateTextureMaterializationRequest(
            targetId = "target:main",
            descriptor = descriptor(),
            deviceGeneration = 5,
            actualResourceGeneration = 5,
            requiredUsageLabels = setOf("render_attachment", "texture_binding"),
            activeAttachmentSampled = false,
        )

        val first = assertIs<GPUResourceMaterializationDecision.Materialized>(
            provider.materializeIntermediateTexture(request, context),
        )
        val second = assertIs<GPUResourceMaterializationDecision.Materialized>(
            provider.materializeIntermediateTexture(request, context),
        )

        assertEquals(GPUResourceLeaseCacheResult.Create, first.dumpResourceLeaseSnapshot.single().cacheResult)
        assertEquals(GPUResourceLeaseCacheResult.Reuse, second.dumpResourceLeaseSnapshot.single().cacheResult)
        assertEquals(
            listOf("create", "reuse"),
            provider.telemetry.dumpEvents.filter { it.lane == "intermediate-texture" }.map { it.result },
        )
    }

    @Test
    fun `intermediate texture cache partitions by generation lifetime and owner`() {
        val provider = GPUConcreteResourceProvider()
        val context = context()
        val baseRequest = GPUIntermediateTextureMaterializationRequest(
            targetId = "target:main",
            descriptor = descriptor(),
            deviceGeneration = 5,
            actualResourceGeneration = 5,
            requiredUsageLabels = setOf("render_attachment", "texture_binding"),
            activeAttachmentSampled = false,
        )

        assertIs<GPUResourceMaterializationDecision.Materialized>(
            provider.materializeIntermediateTexture(baseRequest, context),
        )

        val generationVariant = assertIs<GPUResourceMaterializationDecision.Materialized>(
            provider.materializeIntermediateTexture(
                baseRequest.copy(
                    descriptor = descriptor(generation = 6),
                    actualResourceGeneration = 6,
                ),
                context,
            ),
        )
        val lifetimeVariant = assertIs<GPUResourceMaterializationDecision.Materialized>(
            provider.materializeIntermediateTexture(
                baseRequest.copy(
                    descriptor = descriptor(lifetimeClass = "frame-local"),
                ),
                context,
            ),
        )
        val ownerVariant = assertIs<GPUResourceMaterializationDecision.Materialized>(
            provider.materializeIntermediateTexture(
                baseRequest.copy(
                    descriptor = descriptor(ownerScope = "scope:layer-b"),
                ),
                context,
            ),
        )

        assertEquals(GPUResourceLeaseCacheResult.Create, generationVariant.dumpResourceLeaseSnapshot.single().cacheResult)
        assertEquals(GPUResourceLeaseCacheResult.Create, lifetimeVariant.dumpResourceLeaseSnapshot.single().cacheResult)
        assertEquals(GPUResourceLeaseCacheResult.Create, ownerVariant.dumpResourceLeaseSnapshot.single().cacheResult)
        assertEquals(
            listOf("create", "create", "create", "create"),
            provider.telemetry.dumpEvents.filter { it.lane == "intermediate-texture" }.map { it.result },
        )
    }

    @Test
    fun `intermediate texture refuses mismatched descriptor generation`() {
        val provider = GPUConcreteResourceProvider()
        val refused = assertIs<GPUResourceMaterializationDecision.Refused>(
            provider.materializeIntermediateTexture(
                GPUIntermediateTextureMaterializationRequest(
                    targetId = "target:main",
                    descriptor = descriptor(generation = 4),
                    deviceGeneration = 5,
                    actualResourceGeneration = 3,
                    requiredUsageLabels = setOf("render_attachment", "texture_binding"),
                    activeAttachmentSampled = false,
                ),
                context(),
            ),
        )

        assertEquals("unsupported.intermediate.generation_stale", refused.diagnostic.code)
    }

    @Test
    fun `intermediate texture refuses stale device generation separately`() {
        val provider = GPUConcreteResourceProvider()
        val refused = assertIs<GPUResourceMaterializationDecision.Refused>(
            provider.materializeIntermediateTexture(
                GPUIntermediateTextureMaterializationRequest(
                    targetId = "target:main",
                    descriptor = descriptor(),
                    deviceGeneration = 4,
                    actualResourceGeneration = 5,
                    requiredUsageLabels = setOf("render_attachment", "texture_binding"),
                    activeAttachmentSampled = false,
                ),
                context(),
            ),
        )

        assertEquals("unsupported.intermediate.device_generation_stale", refused.diagnostic.code)
    }

    @Test
    fun `intermediate texture refuses active attachment sampling`() {
        val provider = GPUConcreteResourceProvider()
        val refused = assertIs<GPUResourceMaterializationDecision.Refused>(
            provider.materializeIntermediateTexture(
                GPUIntermediateTextureMaterializationRequest(
                    targetId = "target:main",
                    descriptor = descriptor(),
                    deviceGeneration = 5,
                    actualResourceGeneration = 5,
                    requiredUsageLabels = setOf("render_attachment", "texture_binding"),
                    activeAttachmentSampled = true,
                ),
                context(),
            ),
        )

        assertEquals("unsupported.destination_read.active_attachment_sampled", refused.diagnostic.code)
    }

    @Test
    fun `intermediate texture request accepts validated interface descriptors`() {
        val provider = GPUConcreteResourceProvider()
        val request = GPUIntermediateTextureMaterializationRequest(
            targetId = "target:main",
            descriptor = descriptorImplementation(),
            deviceGeneration = 5,
            actualResourceGeneration = 5,
            requiredUsageLabels = setOf("render_attachment", "texture_binding"),
            activeAttachmentSampled = false,
        )

        val materialized = assertIs<GPUResourceMaterializationDecision.Materialized>(
            provider.materializeIntermediateTexture(request, context()),
        )

        assertEquals(GPUResourceLeaseCacheResult.Create, materialized.dumpResourceLeaseSnapshot.single().cacheResult)
        assertEquals(
            "resource-provider.cache lane=intermediate-texture result=create " +
                "key=target=target:main;descriptor=sha256:layer-a;bounds=bounds:layer-a;" +
                "format=rgba8unorm;usage=render_attachment+texture_binding;sampleCount=1;" +
                "generation=5;lifetime=layer-local;owner=scope:layer-a subject=intermediate:layer-a",
            provider.telemetry.dumpLines().single(),
        )
    }

    @Test
    fun `intermediate texture request rejects invalid interface descriptor implementations`() {
        val cases = listOf(
            "GPUIntermediateTextureMaterializationRequest.descriptor.label must not be blank" to
                descriptorImplementation(label = ""),
            "GPUIntermediateTextureMaterializationRequest.descriptor.purposeLabel must not be blank" to
                descriptorImplementation(purposeLabel = ""),
            "GPUIntermediateTextureMaterializationRequest.descriptor.width must be positive" to
                descriptorImplementation(width = 0),
            "GPUIntermediateTextureMaterializationRequest.descriptor.usageLabels must not contain blanks" to
                descriptorImplementation(usageLabels = listOf("render_attachment", "")),
            "GPUIntermediateTextureMaterializationRequest.descriptor.sampleCount must be positive" to
                descriptorImplementation(sampleCount = 0),
        )

        cases.forEach { (expectedMessage, invalidDescriptor) ->
            val error = assertFailsWith<IllegalArgumentException> {
                GPUIntermediateTextureMaterializationRequest(
                    targetId = "target:main",
                    descriptor = invalidDescriptor,
                    deviceGeneration = 5,
                    actualResourceGeneration = 5,
                    requiredUsageLabels = setOf("render_attachment", "texture_binding"),
                    activeAttachmentSampled = false,
                )
            }

            assertEquals(expectedMessage, error.message)
        }
    }

    private fun context(): GPUTargetPreparationContext =
        GPUTargetPreparationContext(
            targetId = "target:main",
            frameId = "frame:1",
            deviceGeneration = 5,
            budgetClass = "test",
        )

    private fun descriptor(
        generation: Long = 5,
        lifetimeClass: String = "layer-local",
        ownerScope: String = "scope:layer-a",
    ): GPUIntermediateTextureDescriptor =
        GPUIntermediateTextureDescriptor(
            label = "intermediate:layer-a",
            purpose = GPUIntermediatePurpose.LayerTarget,
            descriptorHash = "sha256:layer-a",
            sourceTargetLabel = "surface:main",
            boundsLabel = "bounds:layer-a",
            width = 100,
            height = 80,
            formatClass = "rgba8unorm",
            usageLabels = listOf("render_attachment", "texture_binding"),
            sampleCount = 1,
            generation = generation,
            lifetimeClass = lifetimeClass,
            ownerScope = ownerScope,
            byteEstimate = 32000,
        )

    private fun descriptorImplementation(
        label: String = "intermediate:layer-a",
        purposeLabel: String = "LayerTarget",
        descriptorHash: String = "sha256:layer-a",
        sourceTargetLabel: String = "surface:main",
        boundsLabel: String = "bounds:layer-a",
        width: Int = 100,
        height: Int = 80,
        formatClass: String = "rgba8unorm",
        usageLabels: List<String> = listOf("render_attachment", "texture_binding"),
        sampleCount: Int = 1,
        generation: Long = 5,
        lifetimeClass: String = "layer-local",
        ownerScope: String = "scope:layer-a",
        byteEstimate: Long = 32000,
    ): GPUIntermediateTextureMaterializationDescriptor =
        object : GPUIntermediateTextureMaterializationDescriptor {
            override val label: String = label
            override val purposeLabel: String = purposeLabel
            override val descriptorHash: String = descriptorHash
            override val sourceTargetLabel: String = sourceTargetLabel
            override val boundsLabel: String = boundsLabel
            override val width: Int = width
            override val height: Int = height
            override val formatClass: String = formatClass
            override val usageLabels: List<String> = usageLabels
            override val sampleCount: Int = sampleCount
            override val generation: Long = generation
            override val lifetimeClass: String = lifetimeClass
            override val ownerScope: String = ownerScope
            override val byteEstimate: Long = byteEstimate
        }
}
