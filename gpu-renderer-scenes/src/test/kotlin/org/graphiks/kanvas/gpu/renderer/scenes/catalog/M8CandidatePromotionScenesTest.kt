package org.graphiks.kanvas.gpu.renderer.scenes.catalog

import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneCommand
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneRect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class M8CandidatePromotionScenesTest {
    @Test
    fun `mesh ribbon depth stack scene matches M8 candidate metadata`() {
        val scene = meshRibbonDepthStackScene

        assertEquals("mesh-ribbon-depth-stack", scene.sceneId.value)
        assertEquals("Mesh Ribbon Depth Stack", scene.title)
        assertEquals(setOf(SceneTag.Vertices, SceneTag.RRect, SceneTag.Clip), scene.tags)
        assertEquals(listOf("M8"), scene.roadmapLinks.map { it.milestone })
        assertEquals(SceneExpectation.ShouldRender, scene.expectation)
        assertEquals(listOf(scene), m8CandidatePromotionScenes)
    }

    @Test
    fun `mesh ribbon depth stack scene uses only supported command families with overlapping ribbons`() {
        val scene = meshRibbonDepthStackScene
        val panel = assertIs<SceneCommand.FillRRect>(scene.commands[1])
        val clip = assertIs<SceneCommand.Clip>(scene.commands[2])
        val ribbons = scene.commands.filterIsInstance<SceneCommand.MeshRibbon>()

        assertIs<SceneCommand.Clear>(scene.commands[0])
        assertEquals(6, scene.commands.size)
        assertEquals(
            setOf("clear", "fill-rrect", "clip", "vertices"),
            scene.commands.map { it.family }.toSet(),
        )
        assertEquals(3, ribbons.size)
        assertEquals(listOf("back-ribbon", "middle-ribbon", "front-ribbon"), ribbons.map { it.label })
        assertEquals(listOf(1, 2, 3), ribbons.map { it.paintOrder })
        assertTrue(ribbons.all { it.hasFixturePayload })
        assertTrue(ribbons.all { it.meshKind == "bounded-ribbon-strip" })
        assertTrue(ribbons.all { clip.rect.contains(requireNotNull(it.bounds)) })
        assertTrue(panel.rect.contains(clip.rect))
        assertTrue(overlaps(requireNotNull(ribbons[0].bounds), requireNotNull(ribbons[1].bounds)))
        assertTrue(overlaps(requireNotNull(ribbons[1].bounds), requireNotNull(ribbons[2].bounds)))
        assertEquals(3, ribbons.map { requireNotNull(it.startColor) }.toSet().size)
        assertEquals(3, ribbons.map { requireNotNull(it.endColor) }.toSet().size)
    }

    private fun overlaps(left: SceneRect, right: SceneRect): Boolean =
        left.left < right.right &&
            right.left < left.right &&
            left.top < right.bottom &&
            right.top < left.bottom
}
