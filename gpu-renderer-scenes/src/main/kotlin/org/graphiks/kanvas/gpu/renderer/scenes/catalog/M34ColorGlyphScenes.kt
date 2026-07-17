package org.graphiks.kanvas.gpu.renderer.scenes.catalog

import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneColor
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneCommand

val colrV0ColorGlyphScene: GPURendererScene<SceneCommand> =
    GPURendererScene(
        sceneId = SceneId("colr-v0-color-glyph"),
        title = "COLRv0 Color Glyph",
        description = "Real Skia colr.ttf glyph 2 rendered from its CPAL red and black layers through the prepared WebGPU frame route.",
        dimensions = SceneDimensions(64, 64),
        tags = setOf(SceneTag.Text),
        roadmapLinks = listOf(SceneRoadmapLink.ticket("KGPU-M34-002")),
        expectation = SceneExpectation.ShouldRender,
        commands = listOf(
            SceneCommand.Clear(SceneColor(0f, 0f, 0f, 1f)),
            SceneCommand.ColorTextRun(
                label = "colr-v0-composite",
                colrFontResource = "/fonts/skia/colr.ttf",
                colrBaseGlyphId = 2,
                colrFontSize = 48f,
                glyphOriginX = 8,
                glyphOriginY = 8,
            ),
        ),
    )

val m34ColorGlyphScenes: List<GPURendererScene<SceneCommand>> =
    listOf(colrV0ColorGlyphScene)
