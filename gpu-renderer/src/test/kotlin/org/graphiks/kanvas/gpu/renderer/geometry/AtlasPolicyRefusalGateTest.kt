package org.graphiks.kanvas.gpu.renderer.geometry

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

class AtlasPolicyRefusalGateTest {
    @Test
    fun `path atlas selector evidence refuses until policy facts are complete`() {
        val result = GPUAtlasPolicyRefusalGate().evaluate(
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
        val refusal = assertIs<GPUAtlasPolicyResult.Refused>(result).refusal

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
            result.dumpLines(),
        )
    }

    @Test
    fun `coverage atlas refuses missing upload or synchronization evidence`() {
        val result = GPUAtlasPolicyRefusalGate().evaluate(
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
        val refusal = assertIs<GPUAtlasPolicyResult.Refused>(result).refusal

        assertEquals("unsupported.atlas.sync_unavailable", refusal.diagnostic.code)
        assertEquals(listOf("upload-before-sample", "sync-policy"), refusal.missingFacts)
        assertEquals(
            listOf(
                "atlas-policy:refused row=gpu-renderer.atlas-policy-refusal classification=RefuseRequired route=coverage-atlas artifact=CoverageMaskArtifact policy=FrameLocalMask reason=unsupported.atlas.sync_unavailable",
                "atlas-policy:required facts=content-key,bounds-proof,budget-policy,generation-policy,eviction-policy,use-token-policy,mutation-ordering,upload-before-sample,sync-policy",
                "atlas-policy:missing facts=upload-before-sample,sync-policy",
                "atlas-policy:nonclaim no-atlas-generation no-path-atlas-support no-coverage-atlas-support no-selector-only-support no-hidden-cpu-texture-fallback",
            ),
            result.dumpLines(),
        )
    }

    @Test
    fun `nondeterministic atlas keys refuse before dumping raw handles`() {
        val result = GPUAtlasPolicyRefusalGate().evaluate(
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
        val refusal = assertIs<GPUAtlasPolicyResult.Refused>(result).refusal

        assertEquals("unsupported.atlas.key_nondeterministic", refusal.diagnostic.code)
        val dump = result.dumpLines().joinToString("\n")
        assertFalse(dump.contains("handle"))
        assertFalse(dump.contains("0xdeadbeef"))
        assertContains(dump, "reason=unsupported.atlas.key_nondeterministic")
    }

    @Test
    fun `path atlas accepts when all required facts are present and key is canonical`() {
        val result = GPUAtlasPolicyRefusalGate().evaluate(
            request = GPUAtlasPolicyRequest(
                routeLabel = "path-atlas",
                artifactType = "PathAtlasArtifact",
                policyMode = "RecordingLocalAtlas",
                contentKeyLabel = "path:rect:v1",
                boundsLabel = "mask[0,0,64,64]",
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
        val accepted = assertIs<GPUAtlasPolicyResult.Accepted>(result)

        assertEquals("atlas.policy.accepted", accepted.diagnostic.code)
        assertEquals("PathAtlas", accepted.policy.atlasKind)
        assertEquals("generation-stale-with-use-token", accepted.policy.evictionPolicy)
        assertEquals(64 * 1024 * 1024L, accepted.policy.budget.maxBytes)
        assertEquals(256, accepted.policy.budget.maxEntries)
        assertContains(accepted.entryRef.value, "PathAtlas")
        assertContains(accepted.entryRef.value, "path_rect_v1")
        assertEquals("upload-before-sample", accepted.mutationPlan.operation)
        assertEquals(
            listOf(
                "atlas-policy:accepted row=gpu-renderer.atlas-policy-accepted route=path-atlas artifact=PathAtlasArtifact policy=RecordingLocalAtlas atlasKind=PathAtlas eviction=generation-stale-with-use-token entryRef=PathAtlas:path_rect_v1:mask_0_0_64_64:v1 mutation=upload-before-sample",
                "atlas-policy:accepted budget=maxBytes=67108864 maxEntries=256 pressure=path-coverage-medium",
            ),
            result.dumpLines(),
        )
    }

    @Test
    fun `coverage atlas accepts when all required facts are present`() {
        val result = GPUAtlasPolicyRefusalGate().evaluate(
            request = GPUAtlasPolicyRequest(
                routeLabel = "coverage-atlas",
                artifactType = "CoverageMaskArtifact",
                policyMode = "FrameLocalMask",
                contentKeyLabel = "coverage:clip-rect:v1",
                boundsLabel = "mask[0,0,32,32]",
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
                ),
            ),
        )
        val accepted = assertIs<GPUAtlasPolicyResult.Accepted>(result)

        assertEquals("atlas.policy.accepted", accepted.diagnostic.code)
        assertEquals("CoverageAtlas", accepted.policy.atlasKind)
        assertContains(accepted.entryRef.value, "CoverageAtlas")
        assertContains(accepted.entryRef.value, "coverage_clip_rect_v1")
        assertEquals("upload-before-sample", accepted.mutationPlan.operation)
        assertEquals(
            listOf(
                "atlas-policy:accepted row=gpu-renderer.atlas-policy-accepted route=coverage-atlas artifact=CoverageMaskArtifact policy=FrameLocalMask atlasKind=CoverageAtlas eviction=generation-stale-with-use-token entryRef=CoverageAtlas:coverage_clip_rect_v1:mask_0_0_32_32:v1 mutation=upload-before-sample",
                "atlas-policy:accepted budget=maxBytes=67108864 maxEntries=256 pressure=path-coverage-medium",
            ),
            result.dumpLines(),
        )
    }
}
