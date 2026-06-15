package org.graphiks.kanvas.gpu.renderer.scenes.catalog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneCommand

class GPURendererSceneRegistryTest {
    @Test
    fun `initial registry has business named scenes for M0 through M10`() {
        val scenes = GPURendererSceneRegistry.scenes
        assertEquals(
            listOf(
                "solid-card-stack",
                "rounded-panel-gradient",
                "path-badge-and-stroke",
                "clipped-avatar-grid",
                "texture-swatch-board",
                "layered-shadow-card",
                "filtered-photo-chip",
                "receipt-text-run",
                "runtime-effect-color-tile",
                "blend-mode-strip",
                "mesh-ribbon",
                "cache-pressure-deck",
                "legacy-route-comparison",
            ),
            scenes.map { it.sceneId.value },
        )
        assertEquals(emptyList(), GPURendererSceneRegistry.registry.validate())
        val roadmapLinks = scenes.flatMap { it.roadmapLinks }
        assertTrue(roadmapLinks.map { it.milestone }.containsAll((0..10).map { "M$it" }))
        assertTrue(roadmapLinks.mapNotNull { it.rStage }.containsAll(RStage.entries))
        assertTrue(scenes.any { scene -> scene.roadmapLinks.any { it.milestone == "M10" } })
    }

    @Test
    fun `solid card stack is the first renderable command subset`() {
        val scene = GPURendererSceneRegistry.registry.requireScene("solid-card-stack")
        assertEquals(SceneExpectation.ShouldRender, scene.expectation)
        assertTrue(scene.commands.any { it is SceneCommand.FillRect })
        assertTrue(scene.roadmapLinks.any { it.rStage == RStage.R6 })
    }
}
