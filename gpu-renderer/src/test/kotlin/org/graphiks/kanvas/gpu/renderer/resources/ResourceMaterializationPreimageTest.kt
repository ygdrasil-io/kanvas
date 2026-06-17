package org.graphiks.kanvas.gpu.renderer.resources

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class ResourceMaterializationPreimageTest {
    @Test
    fun `materialization preimage snapshots resources bindings and facts for stable dumps`() {
        val facts = mutableMapOf("slot" to "group1.binding1")
        val resources = mutableListOf(
            GPUMaterializedResourceReference(
                label = "image-texture:checker",
                role = GPUMaterializedResourceRole.SampledTexture,
                descriptorHash = "sha256:texture",
                generation = 3,
                lifetimeClass = "recording-local",
                usageLabels = listOf("texture_binding"),
                evidenceFacts = facts,
            ),
        )
        val bindings = mutableListOf("group1.binding1")
        val preimage = GPUResourceMaterializationPreimagePlan(
            planLabel = "image-sampler-binding",
            sourceGate = "gpu-renderer.sampler-boundary",
            accepted = true,
            resources = resources,
            bindingLabels = bindings,
        )

        facts["slot"] = "group1.binding2"
        resources += GPUMaterializedResourceReference(
            label = "mutated",
            role = GPUMaterializedResourceRole.Sampler,
            descriptorHash = "sha256:mutated",
            generation = 0,
            lifetimeClass = "mutated",
        )
        bindings += "group1.binding2"

        assertFalse(preimage.nonClaims.adapterBacked)
        assertFalse(preimage.nonClaims.liveHandles)
        assertFalse(preimage.nonClaims.productRoute)
        assertEquals(
            listOf(
                "resource-preimage:accepted plan=image-sampler-binding source=gpu-renderer.sampler-boundary resources=image-texture:checker bindings=group1.binding1 adapterBacked=false liveHandles=false productRoute=false",
                "resource-preimage:resource label=image-texture:checker role=sampled-texture generation=3 lifetime=recording-local descriptor=sha256:texture usage=texture_binding facts=slot=group1.binding1",
                "resource-preimage:nonclaim adapterBacked=false liveHandles=false productRoute=false providerCalled=false submitCalled=false",
            ),
            preimage.dumpLines(),
        )
    }

    @Test
    fun `refused materialization preimage records reason and non claims without resources`() {
        val preimage = GPUResourceMaterializationPreimagePlan(
            planLabel = "image-sampler-binding",
            sourceGate = "gpu-renderer.sampler-boundary",
            accepted = false,
            resources = emptyList(),
            refusalCode = "unsupported.texture.mipmap_unavailable",
        )

        assertEquals(
            listOf(
                "resource-preimage:refused plan=image-sampler-binding source=gpu-renderer.sampler-boundary reason=unsupported.texture.mipmap_unavailable resources=none bindings=none adapterBacked=false liveHandles=false productRoute=false",
                "resource-preimage:nonclaim adapterBacked=false liveHandles=false productRoute=false providerCalled=false submitCalled=false",
            ),
            preimage.dumpLines(),
        )
    }

    @Test
    fun `materialization non claims reject accidental live route flags`() {
        assertFailsWith<IllegalArgumentException> {
            GPUResourceMaterializationNonClaims(adapterBacked = true)
        }
        assertFailsWith<IllegalArgumentException> {
            GPUResourceMaterializationNonClaims(liveHandles = true)
        }
        assertFailsWith<IllegalArgumentException> {
            GPUResourceMaterializationNonClaims(productRoute = true)
        }
    }
}
