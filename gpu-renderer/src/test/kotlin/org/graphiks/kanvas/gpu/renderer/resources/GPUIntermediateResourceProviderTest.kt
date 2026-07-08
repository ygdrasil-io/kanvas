package org.graphiks.kanvas.gpu.renderer.resources

import kotlin.test.Test
import kotlin.test.assertEquals
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
}
