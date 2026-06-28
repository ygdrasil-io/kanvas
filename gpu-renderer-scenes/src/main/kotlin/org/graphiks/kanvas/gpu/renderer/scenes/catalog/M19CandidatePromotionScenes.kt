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

val gaussianBlurPhotoScene: GPURendererScene<SceneCommand> =
    GPURendererScene(
        sceneId = SceneId("gaussian-blur-photo"),
        title = "Gaussian Blur Photo",
        description = "Gaussian blur filter applied to a colored rect using separable gaussian passes.",
        dimensions = SceneDimensions(320, 200),
        tags = setOf(SceneTag.Filter),
        roadmapLinks = listOf(SceneRoadmapLink.milestone("M19")),
        expectation = SceneExpectation.ShouldRender,
        commands = listOf(
            SceneCommand.Clear(SceneColor(0.034f, 0.038f, 0.046f, 1f)),
            SceneCommand.FillRect(
                label = "photo-blur",
                rect = SceneRect(48f, 36f, 272f, 164f),
                color = SceneColor.blue(0.88f),
                paintOrder = 1,
            ),
        ),
    )

val colorMatrixTintScene: GPURendererScene<SceneCommand> =
    GPURendererScene(
        sceneId = SceneId("color-matrix-tint"),
        title = "Color Matrix Tint",
        description = "Color matrix filter transformation applied to a colored rect via 4x5 WGSL matrix.",
        dimensions = SceneDimensions(320, 200),
        tags = setOf(SceneTag.Filter),
        roadmapLinks = listOf(SceneRoadmapLink.milestone("M19")),
        expectation = SceneExpectation.ShouldRender,
        commands = listOf(
            SceneCommand.Clear(SceneColor(0.034f, 0.038f, 0.046f, 1f)),
            SceneCommand.FillRect(
                label = "tint-rect",
                rect = SceneRect(48f, 36f, 272f, 164f),
                color = SceneColor.green(0.86f),
                paintOrder = 1,
            ),
        ),
    )

val strokeAndFilterCardScene: GPURendererScene<SceneCommand> =
    GPURendererScene(
        sceneId = SceneId("stroke-and-filter-card"),
        title = "Stroke and Filter Card",
        description = "Combined scene with a stroked rectangle and a Gaussian blur filter applied to a fill rect.",
        dimensions = SceneDimensions(320, 200),
        tags = setOf(SceneTag.Stroke, SceneTag.Filter),
        roadmapLinks = listOf(SceneRoadmapLink.milestone("M16"), SceneRoadmapLink.milestone("M19")),
        expectation = SceneExpectation.ShouldRender,
        commands = listOf(
            SceneCommand.Clear(SceneColor(0.036f, 0.040f, 0.048f, 1f)),
            SceneCommand.FillRect(
                label = "card-blur-fill",
                rect = SceneRect(48f, 36f, 272f, 164f),
                color = SceneColor.amber(0.78f),
                paintOrder = 1,
            ),
            SceneCommand.Stroke(
                label = "card-outline",
                rect = SceneRect(48f, 36f, 272f, 164f),
                strokeColor = SceneColor.blue(0.92f),
                strokeWidth = 4f,
                paintOrder = 2,
            ),
        ),
    )

val m19CandidatePromotionScenes: List<GPURendererScene<SceneCommand>> =
    listOf(blurRadiusLadderScene, colorMatrixFilterScene, gaussianBlurPhotoScene, colorMatrixTintScene, strokeAndFilterCardScene)
