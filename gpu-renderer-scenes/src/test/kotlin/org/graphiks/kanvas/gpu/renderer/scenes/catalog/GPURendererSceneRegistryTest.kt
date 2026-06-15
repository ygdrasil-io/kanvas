package org.graphiks.kanvas.gpu.renderer.scenes.catalog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneCommand

class GPURendererSceneRegistryTest {
    @Test
    fun `initial registry has business named scenes for M0 through M10`() {
        val scenes = GPURendererSceneRegistry.scenes
        assertEquals(expectedScenes.map { it.sceneId }, scenes.map { it.sceneId.value })
        assertEquals(emptyList(), GPURendererSceneRegistry.registry.validate())
        val roadmapLinks = scenes.flatMap { it.roadmapLinks }
        assertTrue(roadmapLinks.map { it.milestone }.containsAll((0..10).map { "M$it" }))
        assertTrue(roadmapLinks.mapNotNull { it.rStage }.containsAll(RStage.entries))
        assertTrue(scenes.any { scene -> scene.roadmapLinks.any { it.milestone == "M10" } })
    }

    @Test
    fun `initial registry matches exact business scene matrix`() {
        val scenes = GPURendererSceneRegistry.scenes
        assertEquals(expectedScenes.size, scenes.size)

        expectedScenes.zip(scenes).forEach { (expected, scene) ->
            assertEquals(expected.sceneId, scene.sceneId.value)
            assertEquals(expected.tags, scene.tags, "${expected.sceneId} tags")
            assertEquals(
                expected.commandFamilies,
                scene.commands.map { it.family },
                "${expected.sceneId} command families",
            )
            assertEquals(
                expected.roadmapLinks,
                scene.roadmapLinks.map { RoadmapExpectation(it.milestone, it.rStage, it.ticketId) },
                "${expected.sceneId} roadmap links",
            )
            assertEquals(SceneExpectation.ShouldRender, scene.expectation, "${expected.sceneId} expectation")
            assertTrue(expected.tags.isNotEmpty(), "${expected.sceneId} matrix tags must not be empty")
            assertTrue(
                expected.commandFamilies.isNotEmpty(),
                "${expected.sceneId} matrix command families must not be empty",
            )
            assertTrue(
                expected.roadmapLinks.isNotEmpty(),
                "${expected.sceneId} matrix roadmap links must not be empty",
            )
            assertTrue(scene.tags.isNotEmpty(), "${expected.sceneId} tags must not be empty")
            assertTrue(scene.commands.isNotEmpty(), "${expected.sceneId} commands must not be empty")
            assertTrue(scene.roadmapLinks.isNotEmpty(), "${expected.sceneId} roadmap links must not be empty")
        }
    }

    @Test
    fun `solid card stack is the first renderable command subset`() {
        val scene = GPURendererSceneRegistry.registry.requireScene("solid-card-stack")
        assertEquals(SceneExpectation.ShouldRender, scene.expectation)
        assertTrue(scene.commands.any { it is SceneCommand.FillRect })
        assertTrue(scene.roadmapLinks.any { it.rStage == RStage.R6 })
    }

    private data class SceneExpectationRow(
        val sceneId: String,
        val tags: Set<SceneTag>,
        val commandFamilies: List<String>,
        val roadmapLinks: List<RoadmapExpectation>,
    )

    private data class RoadmapExpectation(
        val milestone: String,
        val rStage: RStage? = null,
        val ticketId: String? = null,
    )

    private companion object {
        val expectedScenes = listOf(
            SceneExpectationRow(
                sceneId = "solid-card-stack",
                tags = setOf(SceneTag.Rect),
                commandFamilies = listOf("clear", "fill-rect", "fill-rect", "fill-rect"),
                roadmapLinks = listOf(
                    RoadmapExpectation("M0", RStage.R0),
                    RoadmapExpectation("M1", RStage.R1),
                    RoadmapExpectation("M1", RStage.R2),
                    RoadmapExpectation("M1", RStage.R3),
                    RoadmapExpectation("M1", RStage.R4),
                    RoadmapExpectation("M1", RStage.R5),
                    RoadmapExpectation("M1", RStage.R6),
                ),
            ),
            SceneExpectationRow(
                sceneId = "rounded-panel-gradient",
                tags = setOf(SceneTag.RRect, SceneTag.Gradient, SceneTag.Clip),
                commandFamilies = listOf("fill-rrect", "linear-gradient-rect", "clip"),
                roadmapLinks = listOf(
                    RoadmapExpectation("M2", RStage.R1),
                    RoadmapExpectation("M2", RStage.R2),
                    RoadmapExpectation("M2", RStage.R3),
                ),
            ),
            SceneExpectationRow(
                sceneId = "path-badge-and-stroke",
                tags = setOf(SceneTag.RRect, SceneTag.Rect),
                commandFamilies = listOf("fill-rrect", "fill-rect"),
                roadmapLinks = listOf(RoadmapExpectation("M3")),
            ),
            SceneExpectationRow(
                sceneId = "clipped-avatar-grid",
                tags = setOf(SceneTag.Clip, SceneTag.Image),
                commandFamilies = listOf("clip", "bitmap-rect"),
                roadmapLinks = listOf(RoadmapExpectation("M3"), RoadmapExpectation("M5")),
            ),
            SceneExpectationRow(
                sceneId = "texture-swatch-board",
                tags = setOf(SceneTag.Image),
                commandFamilies = listOf("bitmap-rect", "bitmap-rect"),
                roadmapLinks = listOf(RoadmapExpectation("M4")),
            ),
            SceneExpectationRow(
                sceneId = "layered-shadow-card",
                tags = setOf(SceneTag.Layer, SceneTag.Filter),
                commandFamilies = listOf("save-layer", "filter-node"),
                roadmapLinks = listOf(RoadmapExpectation("M5")),
            ),
            SceneExpectationRow(
                sceneId = "filtered-photo-chip",
                tags = setOf(SceneTag.Filter, SceneTag.Image),
                commandFamilies = listOf("bitmap-rect", "filter-node"),
                roadmapLinks = listOf(RoadmapExpectation("M5")),
            ),
            SceneExpectationRow(
                sceneId = "receipt-text-run",
                tags = setOf(SceneTag.Text),
                commandFamilies = listOf("text-run"),
                roadmapLinks = listOf(RoadmapExpectation("M6")),
            ),
            SceneExpectationRow(
                sceneId = "runtime-effect-color-tile",
                tags = setOf(SceneTag.RuntimeEffect),
                commandFamilies = listOf("runtime-effect"),
                roadmapLinks = listOf(RoadmapExpectation("M7")),
            ),
            SceneExpectationRow(
                sceneId = "blend-mode-strip",
                tags = setOf(SceneTag.Rect),
                commandFamilies = listOf("fill-rect"),
                roadmapLinks = listOf(RoadmapExpectation("M7")),
            ),
            SceneExpectationRow(
                sceneId = "mesh-ribbon",
                tags = setOf(SceneTag.Vertices),
                commandFamilies = listOf("vertices"),
                roadmapLinks = listOf(RoadmapExpectation("M8")),
            ),
            SceneExpectationRow(
                sceneId = "cache-pressure-deck",
                tags = setOf(SceneTag.Rect),
                commandFamilies = listOf("fill-rect", "fill-rect"),
                roadmapLinks = listOf(RoadmapExpectation("M9")),
            ),
            SceneExpectationRow(
                sceneId = "legacy-route-comparison",
                tags = setOf(SceneTag.Rect),
                commandFamilies = listOf("fill-rect"),
                roadmapLinks = listOf(RoadmapExpectation("M10")),
            ),
        )
    }
}
