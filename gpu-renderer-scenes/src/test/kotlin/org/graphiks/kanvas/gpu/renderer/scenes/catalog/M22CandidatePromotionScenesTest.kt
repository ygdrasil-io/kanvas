package org.graphiks.kanvas.gpu.renderer.scenes.catalog

import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneCommand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class M22CandidatePromotionScenesTest {
    @Test
    fun `vertices color mesh scene matches M22 candidate metadata`() {
        val scene = verticesColorMeshScene

        assertEquals("vertices-color-mesh", scene.sceneId.value)
        assertEquals("Vertices Color Mesh", scene.title)
        assertEquals(setOf(SceneTag.Vertices), scene.tags)
        assertEquals(listOf("M22"), scene.roadmapLinks.map { it.milestone })
        assertEquals(SceneExpectation.ShouldRender, scene.expectation)
        assertTrue(m22CandidatePromotionScenes.contains(scene))
    }

    @Test
    fun `vertices color mesh scene uses mesh ribbon command`() {
        val scene = verticesColorMeshScene
        val clear = assertIs<SceneCommand.Clear>(scene.commands[0])
        val ribbon = assertIs<SceneCommand.MeshRibbon>(scene.commands[1])

        assertEquals(2, scene.commands.size)
        assertTrue(ribbon.hasFixturePayload)
        assertEquals("bounded-ribbon-strip", ribbon.meshKind)
        assertEquals("color-mesh", ribbon.label)
    }

    @Test
    fun `mesh ribbon depth scene matches M22 candidate metadata`() {
        val scene = meshRibbonDepthScene

        assertEquals("mesh-ribbon-depth", scene.sceneId.value)
        assertEquals("Mesh Ribbon Depth", scene.title)
        assertEquals(setOf(SceneTag.Vertices), scene.tags)
        assertEquals(listOf("M22"), scene.roadmapLinks.map { it.milestone })
        assertEquals(SceneExpectation.ShouldRender, scene.expectation)
        assertTrue(m22CandidatePromotionScenes.contains(scene))
    }

    @Test
    fun `mesh ribbon depth scene has three overlapping ribbons`() {
        val scene = meshRibbonDepthScene
        val ribbons = scene.commands.filterIsInstance<SceneCommand.MeshRibbon>()

        assertIs<SceneCommand.Clear>(scene.commands[0])
        assertEquals(4, scene.commands.size)
        assertEquals(3, ribbons.size)
        assertEquals(listOf("depth-back", "depth-middle", "depth-front"), ribbons.map { it.label })
        assertEquals(listOf(1, 2, 3), ribbons.map { it.paintOrder })
        assertTrue(ribbons.all { it.hasFixturePayload })
        assertTrue(ribbons.all { it.meshKind == "bounded-ribbon-strip" })
        assertEquals(3, ribbons.map { requireNotNull(it.startColor) }.toSet().size)
        assertEquals(3, ribbons.map { requireNotNull(it.endColor) }.toSet().size)
    }

    @Test
    fun `m22 candidate promotion scenes list contains both scenes`() {
        assertEquals(2, m22CandidatePromotionScenes.size)
        assertEquals(listOf(verticesColorMeshScene, meshRibbonDepthScene), m22CandidatePromotionScenes)
    }
}
