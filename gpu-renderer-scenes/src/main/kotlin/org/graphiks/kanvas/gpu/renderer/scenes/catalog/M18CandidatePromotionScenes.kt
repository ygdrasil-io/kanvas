package org.graphiks.kanvas.gpu.renderer.scenes.catalog

import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneColor
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneCommand
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneRect

val savelayerIsolatedScene: GPURendererScene<SceneCommand> =
    GPURendererScene(
        sceneId = SceneId("savelayer-isolated"),
        title = "SaveLayer Isolated",
        description = "Translucent group rendered into an offscreen layer target then composited onto a background.",
        dimensions = SceneDimensions(320, 200),
        tags = setOf(SceneTag.Layer),
        roadmapLinks = listOf(SceneRoadmapLink.milestone("M18")),
        expectation = SceneExpectation.ShouldRender,
        commands = listOf(
            SceneCommand.Clear(SceneColor(0.16f, 0.17f, 0.18f, 1f)),
            SceneCommand.FillRect(
                label = "background-bar",
                rect = SceneRect(24f, 40f, 296f, 160f),
                color = SceneColor.blue(0.40f),
                paintOrder = 1,
            ),
            SceneCommand.SaveLayer(
                label = "translucent-group",
                bounds = SceneRect(40f, 60f, 280f, 140f),
                contentRect = SceneRect(56f, 72f, 264f, 128f),
                radius = 12f,
                contentColor = SceneColor.green(0.70f),
                shadowColor = SceneColor(0.02f, 0.04f, 0.07f, 0.30f),
                shadowOffsetX = 6f,
                shadowOffsetY = 8f,
                paintOrder = 2,
            ),
            SceneCommand.FillRect(
                label = "translucent-child",
                rect = SceneRect(64f, 80f, 184f, 120f),
                color = SceneColor.amber(0.60f),
                paintOrder = 3,
            ),
        ),
    )

val dstReadStrategyScene: GPURendererScene<SceneCommand> =
    GPURendererScene(
        sceneId = SceneId("dst-read-strategy"),
        title = "Destination Read Strategy",
        description = "Blend mode requiring destination read via pass-split, copy, and bind strategy.",
        dimensions = SceneDimensions(320, 200),
        tags = setOf(SceneTag.Layer, SceneTag.Blend),
        roadmapLinks = listOf(SceneRoadmapLink.milestone("M18")),
        expectation = SceneExpectation.ShouldRender,
        commands = listOf(
            SceneCommand.Clear(SceneColor(0.034f, 0.038f, 0.046f, 1f)),
            SceneCommand.FillRect(
                label = "dst-background",
                rect = SceneRect(24f, 32f, 296f, 168f),
                color = SceneColor.green(0.50f),
                paintOrder = 1,
            ),
            SceneCommand.FillRect(
                label = "dst-foreground",
                rect = SceneRect(48f, 56f, 272f, 144f),
                color = SceneColor.amber(0.60f),
                paintOrder = 2,
            ),
        ),
    )

val m18CandidatePromotionScenes: List<GPURendererScene<SceneCommand>> =
    listOf(savelayerIsolatedScene, dstReadStrategyScene)
