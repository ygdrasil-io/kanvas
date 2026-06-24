package org.graphiks.kanvas.gpu.renderer.scenes.catalog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneCommand

class M19CandidatePromotionScenesTest {

    @Test
    fun `blur-radius-ladder scene exists`() {
        val scene = m19CandidatePromotionScenes.first { it.sceneId.value == "blur-radius-ladder" }
        assertEquals("Blur Radius Ladder", scene.title)
        assertTrue(scene.commands.isNotEmpty())
    }

    @Test
    fun `blur-radius-ladder scene has clear and fill commands`() {
        val scene = m19CandidatePromotionScenes.first { it.sceneId.value == "blur-radius-ladder" }
        assertTrue(scene.commands.any { it is SceneCommand.Clear })
        assertTrue(scene.commands.any { it is SceneCommand.FillRect })
    }

    @Test
    fun `color-matrix-filter scene exists`() {
        val scene = m19CandidatePromotionScenes.first { it.sceneId.value == "color-matrix-filter" }
        assertEquals("Color Matrix Filter", scene.title)
        assertTrue(scene.commands.isNotEmpty())
    }

    @Test
    fun `color-matrix-filter scene has clear and fill commands`() {
        val scene = m19CandidatePromotionScenes.first { it.sceneId.value == "color-matrix-filter" }
        assertTrue(scene.commands.any { it is SceneCommand.Clear })
        assertTrue(scene.commands.any { it is SceneCommand.FillRect })
    }

    @Test
    fun `m19 candidate promotion scenes are registered in the registry`() {
        val registeredIds = GPURendererSceneRegistry.scenes.map { it.sceneId.value }.toSet()
        assertTrue(registeredIds.contains("blur-radius-ladder"))
        assertTrue(registeredIds.contains("color-matrix-filter"))
    }

    @Test
    fun `m19 scene list contains two scenes`() {
        assertEquals(2, m19CandidatePromotionScenes.size)
    }
}
