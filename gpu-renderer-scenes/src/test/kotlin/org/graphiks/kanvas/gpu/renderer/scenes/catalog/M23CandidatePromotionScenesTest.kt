package org.graphiks.kanvas.gpu.renderer.scenes.catalog

import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneCommand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class M23CandidatePromotionScenesTest {

    @Test
    fun `performance-budget-review scene exists`() {
        val scene = m23CandidatePromotionScenes.first { it.sceneId.value == "performance-budget-review" }
        assertEquals("Performance Budget Review", scene.title)
        assertTrue(scene.commands.isNotEmpty())
    }

    @Test
    fun `performance-budget-review scene has fill rect commands`() {
        val scene = m23CandidatePromotionScenes.first { it.sceneId.value == "performance-budget-review" }
        assertTrue(scene.commands.any { it is SceneCommand.FillRect })
    }

    @Test
    fun `performance-budget-review scene has clear command`() {
        val scene = m23CandidatePromotionScenes.first { it.sceneId.value == "performance-budget-review" }
        assertTrue(scene.commands.any { it is SceneCommand.Clear })
    }

    @Test
    fun `pipeline-cache-telemetry-review scene exists`() {
        val scene = m23CandidatePromotionScenes.first { it.sceneId.value == "pipeline-cache-telemetry-review" }
        assertEquals("Pipeline Cache Telemetry Review", scene.title)
        assertTrue(scene.commands.isNotEmpty())
    }

    @Test
    fun `pipeline-cache-telemetry-review scene has fill rect commands`() {
        val scene = m23CandidatePromotionScenes.first { it.sceneId.value == "pipeline-cache-telemetry-review" }
        assertTrue(scene.commands.any { it is SceneCommand.FillRect })
    }

    @Test
    fun `frame-gate-m23-baseline scene exists`() {
        val scene = m23CandidatePromotionScenes.first { it.sceneId.value == "frame-gate-m23-baseline" }
        assertEquals("Frame Gate M23 Baseline", scene.title)
        assertTrue(scene.commands.isNotEmpty())
    }

    @Test
    fun `frame-gate-m23-baseline scene has fill rect commands`() {
        val scene = m23CandidatePromotionScenes.first { it.sceneId.value == "frame-gate-m23-baseline" }
        assertTrue(scene.commands.any { it is SceneCommand.FillRect })
    }

    @Test
    fun `pm-evidence-m23-bundle scene exists`() {
        val scene = m23CandidatePromotionScenes.first { it.sceneId.value == "pm-evidence-m23-bundle" }
        assertEquals("PM Evidence M23 Bundle", scene.title)
        assertTrue(scene.commands.isNotEmpty())
    }

    @Test
    fun `pm-evidence-m23-bundle scene has fill rect commands`() {
        val scene = m23CandidatePromotionScenes.first { it.sceneId.value == "pm-evidence-m23-bundle" }
        assertTrue(scene.commands.any { it is SceneCommand.FillRect })
    }

    @Test
    fun `performance-gates-product-flag scene exists`() {
        val scene = m23CandidatePromotionScenes.first { it.sceneId.value == "performance-gates-product-flag" }
        assertEquals("Performance Gates Product Flag", scene.title)
        assertTrue(scene.commands.isNotEmpty())
    }

    @Test
    fun `performance-gates-product-flag scene has fill rect commands`() {
        val scene = m23CandidatePromotionScenes.first { it.sceneId.value == "performance-gates-product-flag" }
        assertTrue(scene.commands.any { it is SceneCommand.FillRect })
    }

    @Test
    fun `m23 candidate promotion scenes are registered`() {
        val registeredIds = GPURendererSceneRegistry.scenes.map { it.sceneId.value }.toSet()
        assertTrue(registeredIds.contains("performance-budget-review"))
        assertTrue(registeredIds.contains("pipeline-cache-telemetry-review"))
        assertTrue(registeredIds.contains("frame-gate-m23-baseline"))
        assertTrue(registeredIds.contains("pm-evidence-m23-bundle"))
        assertTrue(registeredIds.contains("performance-gates-product-flag"))
    }

    @Test
    fun `m23 scene list contains five scenes`() {
        assertEquals(5, m23CandidatePromotionScenes.size)
    }
}
