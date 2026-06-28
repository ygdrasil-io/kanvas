package org.graphiks.kanvas.gpu.renderer.scenes.catalog

import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneCommand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class M26TextActivationScenesTest {
    @Test
    fun `path star gradient candidate carries M26 metadata and render expectation`() {
        val scene = pathStarGradientScene
        assertEquals("path-star-gradient", scene.sceneId.value)
        assertEquals("Path Star Gradient", scene.title)
        assertEquals(setOf(SceneTag.Path, SceneTag.Gradient), scene.tags)
        assertEquals(listOf("M26"), scene.roadmapLinks.map { it.milestone })
        assertEquals(SceneExpectation.ShouldRender, scene.expectation)
        assertTrue(scene.description.contains("gradient", ignoreCase = true))
        assertTrue(m26TextActivationScenes.contains(scene))
    }

    @Test
    fun `path star gradient uses clear and path fill gradient with star kind`() {
        val scene = pathStarGradientScene
        assertTrue(scene.commands[0] is SceneCommand.Clear)
        assertEquals(2, scene.commands.size)
        val gradientCommands = scene.commands.filterIsInstance<SceneCommand.PathFillGradient>()
        assertEquals(1, gradientCommands.size)
        val grad = gradientCommands.single()
        assertEquals("star-gradient-fill", grad.label)
        assertEquals("non-convex-star", grad.pathKind)
        assertEquals(1, grad.paintOrder)
    }

    @Test
    fun `text a8 hello candidate carries M26 metadata and render expectation`() {
        val scene = textA8HelloScene
        assertEquals("text-a8-hello", scene.sceneId.value)
        assertEquals("Text A8 Hello", scene.title)
        assertEquals(setOf(SceneTag.Text), scene.tags)
        assertEquals(listOf("M26"), scene.roadmapLinks.map { it.milestone })
        assertEquals(SceneExpectation.ShouldRender, scene.expectation)
        assertTrue(scene.description.contains("A8", ignoreCase = true))
        assertTrue(m26TextActivationScenes.contains(scene))
    }

    @Test
    fun `text a8 hello uses text run with empty fallback reason and a8 route`() {
        val scene = textA8HelloScene
        val textRuns = scene.commands.filterIsInstance<SceneCommand.TextRun>()
        assertTrue(scene.commands[0] is SceneCommand.Clear)
        assertEquals(3, scene.commands.size)
        assertEquals(1, textRuns.size)
        val textRun = textRuns.single()
        assertEquals("Hello Kanvas", textRun.text)
        assertEquals("font.glyph.a8-atlas", textRun.glyphRoute)
        assertEquals("", textRun.fallbackReason)
        assertEquals(36f, textRun.fontSize)
    }

    @Test
    fun `gradient path and text combined candidate carries M26 metadata and render expectation`() {
        val scene = gradientPathAndTextScene
        assertEquals("gradient-path-and-text", scene.sceneId.value)
        assertEquals("Gradient Path And Text", scene.title)
        assertEquals(setOf(SceneTag.Path, SceneTag.Gradient, SceneTag.Text), scene.tags)
        assertEquals(listOf("M26"), scene.roadmapLinks.map { it.milestone })
        assertEquals(SceneExpectation.ShouldRender, scene.expectation)
        assertTrue(scene.description.contains("gradient", ignoreCase = true))
        assertTrue(m26TextActivationScenes.contains(scene))
    }

    @Test
    fun `gradient path and text uses path fill gradient and text run with empty fallback`() {
        val scene = gradientPathAndTextScene
        assertTrue(scene.commands[0] is SceneCommand.Clear)
        assertEquals(3, scene.commands.size)
        val gradientCommands = scene.commands.filterIsInstance<SceneCommand.PathFillGradient>()
        assertEquals(1, gradientCommands.size)
        val textRuns = scene.commands.filterIsInstance<SceneCommand.TextRun>()
        assertEquals(1, textRuns.size)
        val textRun = textRuns.single()
        assertEquals("Kanvas", textRun.text)
        assertEquals("", textRun.fallbackReason)
        assertEquals(2, textRun.paintOrder)
    }
}
