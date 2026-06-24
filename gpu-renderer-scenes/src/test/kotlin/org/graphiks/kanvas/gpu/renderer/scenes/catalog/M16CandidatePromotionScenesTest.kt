package org.graphiks.kanvas.gpu.renderer.scenes.catalog

import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneCommand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class M16CandidatePromotionScenesTest {
    @Test
    fun `stroke cap join candidate carries M16 metadata and render expectation`() {
        val scene = strokeCapJoinScene
        assertEquals("stroke-cap-join", scene.sceneId.value)
        assertEquals("Stroke Cap Join", scene.title)
        assertEquals(setOf(SceneTag.Stroke), scene.tags)
        assertEquals(listOf("M16"), scene.roadmapLinks.map { it.milestone })
        assertEquals(SceneExpectation.ShouldRender, scene.expectation)
        assertTrue(scene.description.contains("stroke", ignoreCase = true))
        assertTrue(m16CandidatePromotionScenes.contains(scene))
    }

    @Test
    fun `stroke cap join uses five fill rect lanes for cap and join variants`() {
        val scene = strokeCapJoinScene
        val fills = scene.commands.filterIsInstance<SceneCommand.FillRect>()
        assertTrue(scene.commands[0] is SceneCommand.Clear)
        assertEquals(6, scene.commands.size)
        assertEquals(5, fills.size)
        assertEquals(
            listOf("butt-cap-stroke", "square-cap-stroke", "miter-join-stroke", "bevel-join-stroke", "diagnostic-strip"),
            fills.map { it.label },
        )
        assertEquals(listOf(1, 2, 3, 4, 5), fills.map { it.paintOrder })
    }

    @Test
    fun `dash pattern ladder candidate carries M16 metadata and render expectation`() {
        val scene = dashPatternLadderScene
        assertEquals("dash-pattern-ladder", scene.sceneId.value)
        assertEquals("Dash Pattern Ladder", scene.title)
        assertEquals(setOf(SceneTag.Stroke), scene.tags)
        assertEquals(listOf("M16"), scene.roadmapLinks.map { it.milestone })
        assertEquals(SceneExpectation.ShouldRender, scene.expectation)
        assertTrue(scene.description.contains("dash", ignoreCase = true))
        assertTrue(m16CandidatePromotionScenes.contains(scene))
    }

    @Test
    fun `dash pattern ladder uses five fill rect lanes for dash intervals`() {
        val scene = dashPatternLadderScene
        val fills = scene.commands.filterIsInstance<SceneCommand.FillRect>()
        assertTrue(scene.commands[0] is SceneCommand.Clear)
        assertEquals(6, scene.commands.size)
        assertEquals(5, fills.size)
        assertEquals(
            listOf("short-dash", "medium-dash", "long-dash", "alternating-dash", "diagnostic-strip"),
            fills.map { it.label },
        )
        assertEquals(listOf(1, 2, 3, 4, 5), fills.map { it.paintOrder })
    }
}
