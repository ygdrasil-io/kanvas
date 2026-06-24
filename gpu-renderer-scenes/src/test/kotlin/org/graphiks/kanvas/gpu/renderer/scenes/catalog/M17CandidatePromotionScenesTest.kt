package org.graphiks.kanvas.gpu.renderer.scenes.catalog

import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneBitmapSampling
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneCommand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class M17CandidatePromotionScenesTest {
    @Test
    fun `tile mode strip candidate carries M17 metadata and render expectation`() {
        val scene = tileModeStripScene

        assertEquals("tile-mode-strip", scene.sceneId.value)
        assertEquals("Tile Mode Strip", scene.title)
        assertEquals(setOf(SceneTag.Image), scene.tags)
        assertEquals(listOf("M17"), scene.roadmapLinks.map { it.milestone })
        assertEquals(SceneExpectation.ShouldRender, scene.expectation)
        assertTrue(scene.description.contains("tile", ignoreCase = true))
        assertEquals(listOf(tileModeStripScene), m17CandidatePromotionScenes)
    }

    @Test
    fun `tile mode strip uses four bitmap bands with different tile modes`() {
        val scene = tileModeStripScene
        val bitmapBands = scene.commands.filterIsInstance<SceneCommand.BitmapRect>()

        assertIs<SceneCommand.Clear>(scene.commands[0])
        assertEquals(6, scene.commands.size)
        assertEquals(4, bitmapBands.size)
        assertTrue(bitmapBands.all { it.hasFixturePayload })
        assertEquals(
            listOf(
                "clamp-band",
                "repeat-band",
                "mirror-band",
                "decal-band",
            ),
            bitmapBands.map { it.label },
        )
        assertEquals(
            listOf(
                SceneBitmapSampling.Nearest,
                SceneBitmapSampling.Linear,
                SceneBitmapSampling.Nearest,
                SceneBitmapSampling.Linear,
            ),
            bitmapBands.map { it.sampling },
        )
        assertEquals(listOf(1, 2, 3, 4), bitmapBands.map { it.paintOrder })
        assertTrue(
            scene.commands.all { command ->
                command is SceneCommand.Clear ||
                    command is SceneCommand.BitmapRect ||
                    command is SceneCommand.FillRect
            },
        )
    }
}
