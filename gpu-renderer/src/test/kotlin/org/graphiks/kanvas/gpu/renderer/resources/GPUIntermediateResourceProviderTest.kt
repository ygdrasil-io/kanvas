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
    fun `intermediate texture refuses stale generation`() {
        val provider = GPUConcreteResourceProvider()
        val refused = assertIs<GPUResourceMaterializationDecision.Refused>(
            provider.materializeIntermediateTexture(
                GPUIntermediateTextureMaterializationRequest(
                    targetId = "target:main",
                    descriptor = descriptor(generation = 4),
                    deviceGeneration = 5,
                    actualResourceGeneration = 4,
                    requiredUsageLabels = setOf("render_attachment", "texture_binding"),
                    activeAttachmentSampled = false,
                ),
                context(),
            ),
        )

        assertEquals("unsupported.intermediate.generation_stale", refused.diagnostic.code)
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

    private fun descriptor(generation: Long = 5): GPUIntermediateTextureDescriptor =
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
            lifetimeClass = "layer-local",
            ownerScope = "scope:layer-a",
            byteEstimate = 32000,
        )
}
