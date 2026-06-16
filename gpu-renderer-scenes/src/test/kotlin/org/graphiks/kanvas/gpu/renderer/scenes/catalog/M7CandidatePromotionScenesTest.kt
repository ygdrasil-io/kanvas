package org.graphiks.kanvas.gpu.renderer.scenes.catalog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneCommand

class M7CandidatePromotionScenesTest {
    @Test
    fun `runtime effect uniform ladder exports the candidate as an executable M7 scene`() {
        val scene = runtimeEffectUniformLadderScene

        assertEquals("runtime-effect-uniform-ladder", scene.sceneId.value)
        assertEquals("Runtime Effect Uniform Ladder", scene.title)
        assertEquals(setOf(SceneTag.RuntimeEffect, SceneTag.RRect, SceneTag.Clip), scene.tags)
        assertEquals(listOf("M7"), scene.roadmapLinks.map { it.milestone })
        assertEquals(SceneExpectation.ShouldRender, scene.expectation)
        assertEquals(SceneDimensions(320, 200), scene.dimensions)
        assertSame(scene, m7CandidatePromotionScenes.single())
    }

    @Test
    fun `runtime effect uniform ladder builds a clipped SimpleRT color ladder`() {
        val scene = runtimeEffectUniformLadderScene
        val background = assertIs<SceneCommand.Clear>(scene.commands[0])
        val panel = assertIs<SceneCommand.FillRRect>(scene.commands[1])
        val clip = assertIs<SceneCommand.Clip>(scene.commands[2])
        val tiles = scene.commands.drop(3).map { assertIs<SceneCommand.RuntimeEffectTile>(it) }

        assertEquals(0.040f, background.color.r)
        assertEquals("runtime-effect-uniform-ladder-panel", panel.label)
        assertEquals("runtime-effect-uniform-ladder-clip", clip.label)
        assertEquals(4, tiles.size)
        assertEquals(
            listOf(
                "simple-rt-ladder-step-1",
                "simple-rt-ladder-step-2",
                "simple-rt-ladder-step-3",
                "simple-rt-ladder-step-4",
            ),
            tiles.map { it.label },
        )
        assertEquals((1..4).toList(), tiles.map { it.paintOrder })
        assertTrue(tiles.all { it.hasFixturePayload })
        assertTrue(tiles.all { it.isRegisteredSimpleRt })
        assertEquals(
            listOf(
                0.18f to 0.28f,
                0.32f to 0.46f,
                0.50f to 0.66f,
                0.72f to 0.84f,
            ),
            tiles.map { it.uniformColor!!.g to it.uniformColor.b },
        )
        assertTrue(tiles.all { clip.rect.contains(it.rect!!) })
    }
}
