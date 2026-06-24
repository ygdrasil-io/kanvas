package org.graphiks.kanvas.gpu.renderer.scenes.catalog

import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneBitmapSampling
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneBitmapSource
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneColor
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneCommand
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneRect

val tileModeStripScene =
    GPURendererScene(
        sceneId = SceneId("tile-mode-strip"),
        title = "Tile Mode Strip",
        description = "Four bitmap bands with clamp, repeat, mirror, and decal tile mode intent markers.",
        dimensions = SceneDimensions(width = 320, height = 200),
        tags = setOf(SceneTag.Image),
        roadmapLinks = listOf(SceneRoadmapLink.milestone("M17")),
        expectation = SceneExpectation.ShouldRender,
        commands = listOf(
            SceneCommand.Clear(SceneColor(0.040f, 0.044f, 0.050f, 1f)),
            SceneCommand.BitmapRect(
                label = "clamp-band",
                rect = SceneRect(28f, 32f, 72f, 168f),
                source = SceneBitmapSource(
                    topLeft = SceneColor(0.18f, 0.44f, 0.86f, 1f),
                    topRight = SceneColor(0.22f, 0.72f, 0.48f, 1f),
                    bottomLeft = SceneColor(0.96f, 0.70f, 0.18f, 1f),
                    bottomRight = SceneColor(0.92f, 0.26f, 0.22f, 1f),
                ),
                sampling = SceneBitmapSampling.Nearest,
                paintOrder = 1,
            ),
            SceneCommand.BitmapRect(
                label = "repeat-band",
                rect = SceneRect(84f, 32f, 128f, 168f),
                source = SceneBitmapSource(
                    topLeft = SceneColor(0.24f, 0.56f, 0.90f, 1f),
                    topRight = SceneColor(0.94f, 0.84f, 0.58f, 1f),
                    bottomLeft = SceneColor(0.16f, 0.70f, 0.50f, 1f),
                    bottomRight = SceneColor(0.42f, 0.24f, 0.78f, 1f),
                ),
                sampling = SceneBitmapSampling.Linear,
                paintOrder = 2,
            ),
            SceneCommand.BitmapRect(
                label = "mirror-band",
                rect = SceneRect(140f, 32f, 184f, 168f),
                source = SceneBitmapSource(
                    topLeft = SceneColor(0.88f, 0.26f, 0.22f, 1f),
                    topRight = SceneColor(0.20f, 0.50f, 0.86f, 1f),
                    bottomLeft = SceneColor(0.96f, 0.70f, 0.16f, 1f),
                    bottomRight = SceneColor(0.22f, 0.72f, 0.48f, 1f),
                ),
                sampling = SceneBitmapSampling.Nearest,
                paintOrder = 3,
            ),
            SceneCommand.BitmapRect(
                label = "decal-band",
                rect = SceneRect(196f, 32f, 240f, 168f),
                source = SceneBitmapSource(
                    topLeft = SceneColor(0.20f, 0.52f, 0.88f, 1f),
                    topRight = SceneColor(0.90f, 0.34f, 0.22f, 1f),
                    bottomLeft = SceneColor(0.96f, 0.70f, 0.18f, 1f),
                    bottomRight = SceneColor(0.24f, 0.70f, 0.52f, 1f),
                ),
                sampling = SceneBitmapSampling.Linear,
                paintOrder = 4,
            ),
            SceneCommand.FillRect(
                label = "tile-mode-diagnostics-strip",
                rect = SceneRect(28f, 176f, 248f, 184f),
                color = SceneColor(0.74f, 0.76f, 0.82f, 1f),
                paintOrder = 5,
            ),
        ),
    )

val m17CandidatePromotionScenes =
    listOf(
        tileModeStripScene,
    )
