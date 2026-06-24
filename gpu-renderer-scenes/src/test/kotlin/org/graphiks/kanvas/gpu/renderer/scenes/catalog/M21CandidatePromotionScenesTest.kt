package org.graphiks.kanvas.gpu.renderer.scenes.catalog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneCommand

class M21CandidatePromotionScenesTest {

    @Test
    fun `runtime-effect-uniform scene exists`() {
        val scene = m21CandidatePromotionScenes.first { it.sceneId.value == "runtime-effect-uniform" }
        assertEquals("Runtime Effect Uniform", scene.title)
        assertTrue(scene.commands.isNotEmpty())
    }

    @Test
    fun `runtime-effect-uniform scene has runtime effect tiles`() {
        val scene = m21CandidatePromotionScenes.first { it.sceneId.value == "runtime-effect-uniform" }
        assertTrue(scene.commands.any { it is SceneCommand.RuntimeEffectTile })
    }

    @Test
    fun `runtime-effect-uniform scene has clear command`() {
        val scene = m21CandidatePromotionScenes.first { it.sceneId.value == "runtime-effect-uniform" }
        assertTrue(scene.commands.any { it is SceneCommand.Clear })
    }

    @Test
    fun `runtime-effect-child scene exists`() {
        val scene = m21CandidatePromotionScenes.first { it.sceneId.value == "runtime-effect-child" }
        assertEquals("Runtime Effect Child", scene.title)
        assertTrue(scene.commands.isNotEmpty())
    }

    @Test
    fun `runtime-effect-child scene has runtime effect tile`() {
        val scene = m21CandidatePromotionScenes.first { it.sceneId.value == "runtime-effect-child" }
        assertTrue(scene.commands.any { it is SceneCommand.RuntimeEffectTile })
    }

    @Test
    fun `m21 candidate promotion scenes are registered in the registry`() {
        val registeredIds = GPURendererSceneRegistry.scenes.map { it.sceneId.value }.toSet()
        assertTrue(registeredIds.contains("runtime-effect-uniform"))
        assertTrue(registeredIds.contains("runtime-effect-child"))
    }

    @Test
    fun `m21 scene list contains two scenes`() {
        assertEquals(2, m21CandidatePromotionScenes.size)
    }
}
