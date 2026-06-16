package org.graphiks.kanvas.gpu.renderer.scenes.catalog

import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneBitmapSampling
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneCommand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class M4CandidatePromotionScenesTest {
    @Test
    fun `bitmap sampler matrix candidate carries M4 metadata and render expectation`() {
        val scene = bitmapSamplerMatrixScene

        assertEquals("bitmap-sampler-matrix", scene.sceneId.value)
        assertEquals("Bitmap Sampler Matrix", scene.title)
        assertEquals(setOf(SceneTag.Image, SceneTag.Clip, SceneTag.RRect), scene.tags)
        assertEquals(listOf("M4"), scene.roadmapLinks.map { it.milestone })
        assertEquals(SceneExpectation.ShouldRender, scene.expectation)
        assertTrue(scene.description.contains("sampler", ignoreCase = true))
        assertEquals(listOf(scene), m4CandidatePromotionScenes)
    }

    @Test
    fun `bitmap sampler matrix candidate uses clipped four cell bitmap matrix with nearest and linear sampling`() {
        val scene = bitmapSamplerMatrixScene
        val bitmapCells = scene.commands.filterIsInstance<SceneCommand.BitmapRect>()

        assertIs<SceneCommand.Clear>(scene.commands[0])
        assertIs<SceneCommand.FillRRect>(scene.commands[1])
        assertIs<SceneCommand.Clip>(scene.commands[2])
        assertEquals(7, scene.commands.size)
        assertEquals(4, bitmapCells.size)
        assertTrue(bitmapCells.all { it.hasFixturePayload })
        assertEquals(
            listOf(
                "matrix-nearest-a",
                "matrix-linear-a",
                "matrix-nearest-b",
                "matrix-linear-b",
            ),
            bitmapCells.map { it.label },
        )
        assertEquals(
            listOf(
                SceneBitmapSampling.Nearest,
                SceneBitmapSampling.Linear,
                SceneBitmapSampling.Nearest,
                SceneBitmapSampling.Linear,
            ),
            bitmapCells.map { it.sampling },
        )
        assertEquals(listOf(1, 2, 3, 4), bitmapCells.map { it.paintOrder })
        assertTrue(
            scene.commands.all { command ->
                command is SceneCommand.Clear ||
                    command is SceneCommand.FillRRect ||
                    command is SceneCommand.Clip ||
                    command is SceneCommand.BitmapRect
            },
        )
    }
}
