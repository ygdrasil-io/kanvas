package org.graphiks.kanvas.gpu.renderer.scenes.catalog

import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneColor
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneCommand
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneRect

val blurRadiusLadderScene: GPURendererScene<SceneCommand> =
    GPURendererScene(
        sceneId = SceneId("blur-radius-ladder"),
        title = "Blur Radius Ladder",
        description = "Four circles with increasing blur radii demonstrating separable gaussian blur passes.",
        dimensions = SceneDimensions(320, 200),
        tags = setOf(SceneTag.Filter),
        roadmapLinks = listOf(SceneRoadmapLink.milestone("M19")),
        expectation = SceneExpectation.ShouldRender,
        commands = listOf(
            SceneCommand.Clear(SceneColor(0.034f, 0.038f, 0.046f, 1f)),
            SceneCommand.FillRect(
                label = "blur-radius-1",
                rect = SceneRect(24f, 48f, 72f, 160f),
                color = SceneColor.blue(0.84f),
                paintOrder = 1,
            ),
            SceneCommand.FillRect(
                label = "blur-radius-2",
                rect = SceneRect(88f, 48f, 136f, 160f),
                color = SceneColor.green(0.78f),
                paintOrder = 2,
            ),
            SceneCommand.FillRect(
                label = "blur-radius-3",
                rect = SceneRect(152f, 48f, 200f, 160f),
                color = SceneColor.amber(0.86f),
                paintOrder = 3,
            ),
            SceneCommand.FillRect(
                label = "blur-radius-4",
                rect = SceneRect(216f, 48f, 296f, 160f),
                color = SceneColor(0.92f, 0.18f, 0.16f, 0.92f),
                paintOrder = 4,
            ),
        ),
    )

val colorMatrixFilterScene: GPURendererScene<SceneCommand> =
    GPURendererScene(
        sceneId = SceneId("color-matrix-filter"),
        title = "Color Matrix Filter",
        description = "Three color transformations demonstrating 4x5 matrix multiplication via WGSL.",
        dimensions = SceneDimensions(320, 200),
        tags = setOf(SceneTag.Filter),
        roadmapLinks = listOf(SceneRoadmapLink.milestone("M19")),
        expectation = SceneExpectation.ShouldRender,
        commands = listOf(
            SceneCommand.Clear(SceneColor(0.034f, 0.038f, 0.046f, 1f)),
            SceneCommand.FillRect(
                label = "identity-lane",
                rect = SceneRect(24f, 52f, 96f, 154f),
                color = SceneColor.green(0.90f),
                paintOrder = 1,
            ),
            SceneCommand.FillRect(
                label = "saturation-lane",
                rect = SceneRect(112f, 52f, 184f, 154f),
                color = SceneColor.blue(0.84f),
                paintOrder = 2,
            ),
            SceneCommand.FillRect(
                label = "hue-rotate-lane",
                rect = SceneRect(200f, 52f, 296f, 154f),
                color = SceneColor.amber(0.86f),
                paintOrder = 3,
            ),
        ),
    )

val m19CandidatePromotionScenes: List<GPURendererScene<SceneCommand>> =
    listOf(blurRadiusLadderScene, colorMatrixFilterScene)
