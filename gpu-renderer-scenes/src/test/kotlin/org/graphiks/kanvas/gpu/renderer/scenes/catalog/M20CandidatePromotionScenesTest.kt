package org.graphiks.kanvas.gpu.renderer.scenes.catalog

import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneCommand
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class M20CandidatePromotionScenesTest {
    @Test
    fun `glyph atlas strip candidate carries M20 metadata and render expectation`() {
        val scene = glyphAtlasStripScene
        assertEquals("glyph-atlas-strip", scene.sceneId.value)
        assertEquals("Glyph Atlas Strip", scene.title)
        assertEquals(setOf(SceneTag.Text), scene.tags)
        assertEquals(listOf("M20"), scene.roadmapLinks.map { it.milestone })
        assertEquals(SceneExpectation.ShouldRender, scene.expectation)
        assertTrue(scene.description.contains("glyph", ignoreCase = true))
        assertTrue(m20CandidatePromotionScenes.contains(scene))
    }

    @Test
    fun `glyph atlas strip uses text run with a8-atlas route and diagnostic fill`() {
        val scene = glyphAtlasStripScene
        val textRuns = scene.commands.filterIsInstance<SceneCommand.TextRun>()
        assertTrue(scene.commands[0] is SceneCommand.Clear)
        assertEquals(3, scene.commands.size)
        assertEquals(1, textRuns.size)
        val textRun = textRuns.single()
        assertEquals("ABCabc123", textRun.text)
        assertEquals("font.glyph.a8-atlas", textRun.glyphRoute)
        assertContains(textRun.fallbackReason, "unsupported.text")
    }

    @Test
    fun `sdf glyph scale candidate carries M20 metadata and render expectation`() {
        val scene = sdfGlyphScaleScene
        assertEquals("sdf-glyph-scale", scene.sceneId.value)
        assertEquals("SDF Glyph Scale", scene.title)
        assertEquals(setOf(SceneTag.Text), scene.tags)
        assertEquals(listOf("M20"), scene.roadmapLinks.map { it.milestone })
        assertEquals(SceneExpectation.ShouldRender, scene.expectation)
        assertTrue(scene.description.contains("SDF", ignoreCase = true))
        assertTrue(m20CandidatePromotionScenes.contains(scene))
    }

    @Test
    fun `sdf glyph scale uses three text runs at 18 32 and 48pt`() {
        val scene = sdfGlyphScaleScene
        val textRuns = scene.commands.filterIsInstance<SceneCommand.TextRun>()
        assertTrue(scene.commands[0] is SceneCommand.Clear)
        assertEquals(5, scene.commands.size)
        assertEquals(3, textRuns.size)
        assertEquals(listOf("Aa", "Aa", "Aa"), textRuns.map { it.text })
        assertEquals(listOf("sdf-small", "sdf-medium", "sdf-large"), textRuns.map { it.label })
        assertEquals(listOf(18f, 32f, 48f), textRuns.map { it.fontSize })
        assertTrue(textRuns.all { it.glyphRoute == "font.glyph.sdf-atlas" })
    }
}
