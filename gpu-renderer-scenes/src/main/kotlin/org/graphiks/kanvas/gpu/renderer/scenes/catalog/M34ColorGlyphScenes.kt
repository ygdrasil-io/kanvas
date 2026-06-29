package org.graphiks.kanvas.gpu.renderer.scenes.catalog

import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneColor
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneCommand

val colrV0ColorGlyphScene: GPURendererScene<SceneCommand> =
    GPURendererScene(
        sceneId = SceneId("colr-v0-color-glyph"),
        title = "COLRv0 Color Glyph",
        description = "Two-layer COLRv0 color glyph (red A over blue B) rendered via drawColorGlyphPass single-pass composite.",
        dimensions = SceneDimensions(64, 64),
        tags = setOf(SceneTag.Text),
        roadmapLinks = listOf(SceneRoadmapLink.ticket("KGPU-M34-002")),
        expectation = SceneExpectation.ShouldRender,
        commands = listOf(
            SceneCommand.Clear(SceneColor(0f, 0f, 0f, 1f)),
            SceneCommand.ColorTextRun(
                label = "colr-v0-composite",
                text = "AB",
                fontSize = 48f,
                layerColors = listOf(
                    SceneColor(1f, 0f, 0f, 1f),
                    SceneColor(0f, 0f, 1f, 1f),
                ),
                glyphText = "AB",
                glyphFontSize = 48f,
            ),
        ),
    )

val m34ColorGlyphScenes: List<GPURendererScene<SceneCommand>> =
    listOf(colrV0ColorGlyphScene)
