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
        assertTrue(registeredIds.contains("gaussian-blur-photo"))
        assertTrue(registeredIds.contains("color-matrix-tint"))
        assertTrue(registeredIds.contains("stroke-and-filter-card"))
    }

    @Test
    fun `m19 scene list contains five scenes`() {
        assertEquals(5, m19CandidatePromotionScenes.size)
    }

    @Test
    fun `gaussian-blur-photo scene exists`() {
        val scene = m19CandidatePromotionScenes.first { it.sceneId.value == "gaussian-blur-photo" }
        assertEquals("Gaussian Blur Photo", scene.title)
        assertTrue(scene.commands.isNotEmpty())
    }

    @Test
    fun `gaussian-blur-photo scene has clear and fill commands`() {
        val scene = m19CandidatePromotionScenes.first { it.sceneId.value == "gaussian-blur-photo" }
        assertTrue(scene.commands.any { it is SceneCommand.Clear })
        assertTrue(scene.commands.any { it is SceneCommand.FillRect })
    }

    @Test
    fun `color-matrix-tint scene exists`() {
        val scene = m19CandidatePromotionScenes.first { it.sceneId.value == "color-matrix-tint" }
        assertEquals("Color Matrix Tint", scene.title)
        assertTrue(scene.commands.isNotEmpty())
    }

    @Test
    fun `color-matrix-tint scene has clear and fill commands`() {
        val scene = m19CandidatePromotionScenes.first { it.sceneId.value == "color-matrix-tint" }
        assertTrue(scene.commands.any { it is SceneCommand.Clear })
        assertTrue(scene.commands.any { it is SceneCommand.FillRect })
    }

    @Test
    fun `stroke-and-filter-card scene exists`() {
        val scene = m19CandidatePromotionScenes.first { it.sceneId.value == "stroke-and-filter-card" }
        assertEquals("Stroke and Filter Card", scene.title)
        assertTrue(scene.commands.isNotEmpty())
    }

    @Test
    fun `stroke-and-filter-card scene has clear fill and stroke commands`() {
        val scene = m19CandidatePromotionScenes.first { it.sceneId.value == "stroke-and-filter-card" }
        assertTrue(scene.commands.any { it is SceneCommand.Clear })
        assertTrue(scene.commands.any { it is SceneCommand.FillRect })
        assertTrue(scene.commands.any { it is SceneCommand.Stroke })
    }
}
