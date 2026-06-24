package org.graphiks.kanvas.gpu.renderer.scenes.catalog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneCommand

class M18CandidatePromotionScenesTest {

    @Test
    fun `savelayer-isolated scene exists`() {
        val scene = m18CandidatePromotionScenes.first { it.sceneId.value == "savelayer-isolated" }
        assertEquals("SaveLayer Isolated", scene.title)
        assertTrue(scene.commands.isNotEmpty())
    }

    @Test
    fun `savelayer-isolated scene has savelayer command`() {
        val scene = m18CandidatePromotionScenes.first { it.sceneId.value == "savelayer-isolated" }
        assertTrue(scene.commands.any { it is SceneCommand.SaveLayer })
    }

    @Test
    fun `savelayer-isolated scene has clear and fill commands`() {
        val scene = m18CandidatePromotionScenes.first { it.sceneId.value == "savelayer-isolated" }
        assertTrue(scene.commands.any { it is SceneCommand.Clear })
        assertTrue(scene.commands.any { it is SceneCommand.FillRect })
    }

    @Test
    fun `dst-read-strategy scene exists`() {
        val scene = m18CandidatePromotionScenes.first { it.sceneId.value == "dst-read-strategy" }
        assertEquals("Destination Read Strategy", scene.title)
        assertTrue(scene.commands.isNotEmpty())
    }

    @Test
    fun `dst-read-strategy scene has fill commands`() {
        val scene = m18CandidatePromotionScenes.first { it.sceneId.value == "dst-read-strategy" }
        assertTrue(scene.commands.any { it is SceneCommand.FillRect })
    }

    @Test
    fun `m18 candidate promotion scenes are registered in the registry`() {
        val registeredIds = GPURendererSceneRegistry.scenes.map { it.sceneId.value }.toSet()
        assertTrue(registeredIds.contains("savelayer-isolated"))
        assertTrue(registeredIds.contains("dst-read-strategy"))
    }

    @Test
    fun `m18 scene list contains two scenes`() {
        assertEquals(2, m18CandidatePromotionScenes.size)
    }
}
