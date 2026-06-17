package org.graphiks.kanvas.gpu.renderer.scenes.catalog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SceneHumanDocumentationTest {
    @Test
    fun `human docs cover every executable scene exactly once`() {
        val diagnostics = GPURendererSceneHumanDocs.validateAgainst(GPURendererSceneRegistry.scenes)

        assertEquals(emptyList(), diagnostics)
        assertEquals(
            GPURendererSceneRegistry.scenes.map { it.sceneId.value }.toSet(),
            GPURendererSceneHumanDocs.docs.map { it.sceneId.value }.toSet(),
        )
        assertEquals(
            GPURendererSceneRegistry.scenes.size,
            GPURendererSceneHumanDocs.docs.size,
        )
    }

    @Test
    fun `human docs contain compact French fields for every executable scene`() {
        GPURendererSceneHumanDocs.docs.forEach { docs ->
            assertTrue(docs.french.intention.length >= 24, docs.sceneId.value)
            assertTrue(docs.french.validates.length >= 24, docs.sceneId.value)
            assertTrue(docs.french.nonClaims.length >= 24, docs.sceneId.value)
            assertTrue(docs.french.evidence.length >= 16, docs.sceneId.value)
        }
    }

    @Test
    fun `candidate scenes are separated from executable scenes and keep only blocked backlog milestones`() {
        val executableIds = GPURendererSceneRegistry.scenes.map { it.sceneId.value }.toSet()
        val candidateIds = GPURendererSceneHumanDocs.candidateScenes.map { it.sceneId.value }
        val candidateMilestones = GPURendererSceneHumanDocs.candidateScenes
            .flatMap { candidate -> candidate.roadmapLinks.map { it.milestone } }
            .toSet()

        assertTrue(candidateIds.none { it in executableIds })
        assertEquals(candidateIds.size, candidateIds.toSet().size)
        assertEquals(
            setOf("M2", "M3", "M5", "M6"),
            candidateMilestones,
        )
    }

    @Test
    fun `candidate scenes expose blocked pipeline statuses and French review text`() {
        GPURendererSceneHumanDocs.candidateScenes.forEach { candidate ->
            assertTrue(candidate.title.isNotBlank(), candidate.sceneId.value)
            assertTrue(candidate.tags.isNotEmpty(), candidate.sceneId.value)
            assertTrue(candidate.french.intention.length >= 24, candidate.sceneId.value)
            assertTrue(candidate.french.validationTarget.length >= 24, candidate.sceneId.value)
            assertTrue(candidate.french.nonClaims.length >= 24, candidate.sceneId.value)
            assertTrue(candidate.french.rationale.length >= 24, candidate.sceneId.value)
        }
    }

    @Test
    fun `ready candidates promoted to executable scenes leave only blocked backlog candidates`() {
        val promotedSceneIds = setOf(
            "product-route-smoke-lanes",
            "bitmap-sampler-matrix",
            "runtime-effect-uniform-ladder",
            "mesh-ribbon-depth-stack",
            "cache-frame-budget-strip",
            "legacy-parity-snapshot-board",
        )
        val executableIds = GPURendererSceneRegistry.scenes.map { it.sceneId.value }.toSet()
        val candidateIds = GPURendererSceneHumanDocs.candidateScenes.map { it.sceneId.value }.toSet()
        val candidateStatuses = GPURendererSceneHumanDocs.candidateScenes.map { it.status }.toSet()

        assertTrue(promotedSceneIds.all { it in executableIds })
        assertTrue(promotedSceneIds.none { it in candidateIds })
        assertTrue(
            promotedSceneIds.all { promotedId ->
                GPURendererSceneHumanDocs.docs.any { it.sceneId.value == promotedId }
            },
        )
        assertEquals(
            setOf(
                CandidateSceneStatus.RunnerGap,
                CandidateSceneStatus.DependencyGated,
            ),
            candidateStatuses,
        )
    }
}
