package org.graphiks.kanvas.gpu.renderer.geometry

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class AtlasPolicyRefusalGateTest {
    @Test
    fun `path atlas selector evidence refuses until policy facts are complete`() {
        val refusal = GPUAtlasPolicyRefusalGate().evaluate(
            request = GPUAtlasPolicyRequest(
                routeLabel = "path-atlas",
                artifactType = "PathAtlasArtifact",
                policyMode = "PersistentAtlas",
                contentKeyLabel = "path:triangle:v1",
                boundsLabel = "mask[0,0,16,16]",
                availableFacts = setOf(
                    "content-key",
                    "bounds-proof",
                    "selector-route-dump",
                ),
                selectorEvidenceOnly = true,
            ),
        )

        assertEquals("unsupported.atlas.policy_unavailable", refusal.diagnostic.code)
        assertEquals("RefuseRequired", refusal.classification)
        assertContains(refusal.missingFacts, "budget-policy")
        assertContains(refusal.missingFacts, "generation-policy")
        assertContains(refusal.missingFacts, "sync-policy")
        assertContains(refusal.missingFacts, "gpu-sampling-evidence")
        assertEquals(
            listOf(
                "atlas-policy:refused row=gpu-renderer.atlas-policy-refusal classification=RefuseRequired route=path-atlas artifact=PathAtlasArtifact policy=PersistentAtlas reason=unsupported.atlas.policy_unavailable",
                "atlas-policy:required facts=content-key,bounds-proof,budget-policy,generation-policy,eviction-policy,use-token-policy,mutation-ordering,upload-before-sample,sync-policy,gpu-sampling-evidence",
                "atlas-policy:missing facts=budget-policy,generation-policy,eviction-policy,use-token-policy,mutation-ordering,upload-before-sample,sync-policy,gpu-sampling-evidence,selector-only-evidence",
                "atlas-policy:nonclaim no-atlas-generation no-path-atlas-support no-coverage-atlas-support no-selector-only-support no-hidden-cpu-texture-fallback",
            ),
            refusal.dumpLines(),
        )
    }

    @Test
    fun `coverage atlas refuses missing upload or synchronization evidence`() {
        val refusal = GPUAtlasPolicyRefusalGate().evaluate(
            request = GPUAtlasPolicyRequest(
                routeLabel = "coverage-atlas",
                artifactType = "CoverageMaskArtifact",
                policyMode = "FrameLocalMask",
                contentKeyLabel = "coverage:clip-stack:v1",
                boundsLabel = "mask[4,4,32,32]",
                availableFacts = setOf(
                    "content-key",
                    "bounds-proof",
                    "budget-policy",
                    "generation-policy",
                    "eviction-policy",
                    "use-token-policy",
                    "mutation-ordering",
                ),
            ),
        )

        assertEquals("unsupported.atlas.sync_unavailable", refusal.diagnostic.code)
        assertEquals(listOf("upload-before-sample", "sync-policy"), refusal.missingFacts)
        assertEquals(
            listOf(
                "atlas-policy:refused row=gpu-renderer.atlas-policy-refusal classification=RefuseRequired route=coverage-atlas artifact=CoverageMaskArtifact policy=FrameLocalMask reason=unsupported.atlas.sync_unavailable",
                "atlas-policy:required facts=content-key,bounds-proof,budget-policy,generation-policy,eviction-policy,use-token-policy,mutation-ordering,upload-before-sample,sync-policy",
                "atlas-policy:missing facts=upload-before-sample,sync-policy",
                "atlas-policy:nonclaim no-atlas-generation no-path-atlas-support no-coverage-atlas-support no-selector-only-support no-hidden-cpu-texture-fallback",
            ),
            refusal.dumpLines(),
        )
    }

    @Test
    fun `nondeterministic atlas keys refuse before dumping raw handles`() {
        val refusal = GPUAtlasPolicyRefusalGate().evaluate(
            request = GPUAtlasPolicyRequest(
                routeLabel = "path-atlas",
                artifactType = "PathAtlasArtifact",
                policyMode = "RecordingLocalAtlas",
                contentKeyLabel = "handle:0xdeadbeef",
                boundsLabel = "mask[0,0,16,16]",
                availableFacts = setOf(
                    "content-key",
                    "bounds-proof",
                    "budget-policy",
                    "generation-policy",
                    "eviction-policy",
                    "use-token-policy",
                    "mutation-ordering",
                    "upload-before-sample",
                    "sync-policy",
                    "gpu-sampling-evidence",
                ),
            ),
        )

        assertEquals("unsupported.atlas.key_nondeterministic", refusal.diagnostic.code)
        val dump = refusal.dumpLines().joinToString("\n")
        assertFalse(dump.contains("handle"))
        assertFalse(dump.contains("0xdeadbeef"))
        assertContains(dump, "reason=unsupported.atlas.key_nondeterministic")
    }
}
