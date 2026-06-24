package org.graphiks.kanvas.gpu.renderer.scenes.catalog

import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneColor
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneCommand
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneRect

val roundedRectSolidsScene: GPURendererScene<SceneCommand> =
    GPURendererScene(
        sceneId = SceneId("rounded-rect-solids"),
        title = "Rounded Rect Solids",
        description = "FillRRect commands with solid colors and varying radii exercising the native rrect coverage route.",
        dimensions = SceneDimensions(320, 200),
        tags = setOf(SceneTag.RRect),
        roadmapLinks = listOf(SceneRoadmapLink.milestone("M10")),
        expectation = SceneExpectation.ShouldRender,
        commands = listOf(
            SceneCommand.Clear(SceneColor(0.036f, 0.040f, 0.048f, 1f)),
            SceneCommand.FillRRect(
                label = "rrect-large-radius",
                rect = SceneRect(30f, 32f, 146f, 168f),
                radius = 28f,
                color = SceneColor.green(0.88f),
            ),
            SceneCommand.FillRRect(
                label = "rrect-small-radius",
                rect = SceneRect(174f, 48f, 290f, 152f),
                radius = 8f,
                color = SceneColor.blue(0.84f),
            ),
            SceneCommand.FillRRect(
                label = "rrect-medium-radius",
                rect = SceneRect(102f, 88f, 218f, 170f),
                radius = 16f,
                color = SceneColor.amber(0.78f),
                paintOrder = 1,
            ),
        ),
    )

val linearGradientLanesScene: GPURendererScene<SceneCommand> =
    GPURendererScene(
        sceneId = SceneId("linear-gradient-lanes"),
        title = "Linear Gradient Lanes",
        description = "Linear gradient rects with clamped tile mode exercising native gradient route evidence.",
        dimensions = SceneDimensions(320, 200),
        tags = setOf(SceneTag.Rect, SceneTag.Gradient),
        roadmapLinks = listOf(SceneRoadmapLink.milestone("M10")),
        expectation = SceneExpectation.ShouldRender,
        commands = listOf(
            SceneCommand.Clear(SceneColor(0.040f, 0.044f, 0.052f, 1f)),
            SceneCommand.LinearGradientRect(
                label = "horizontal-gradient",
                rect = SceneRect(28f, 52f, 292f, 96f),
                startColor = SceneColor.green(0.92f),
                endColor = SceneColor.amber(0.88f),
                paintOrder = 1,
            ),
            SceneCommand.LinearGradientRect(
                label = "vertical-gradient",
                rect = SceneRect(60f, 114f, 120f, 176f),
                startColor = SceneColor.blue(0.90f),
                endColor = SceneColor.red(),
                paintOrder = 2,
            ),
            SceneCommand.LinearGradientRect(
                label = "diagonal-gradient",
                rect = SceneRect(200f, 114f, 280f, 176f),
                startColor = SceneColor.amber(0.92f),
                endColor = SceneColor.green(0.82f),
                paintOrder = 3,
            ),
        ),
    )

val scissorOverlayScene: GPURendererScene<SceneCommand> =
    GPURendererScene(
        sceneId = SceneId("scissor-overlay"),
        title = "Scissor Overlay",
        description = "Fill rects inside a scissor clip showing native scissor route evidence.",
        dimensions = SceneDimensions(320, 200),
        tags = setOf(SceneTag.Rect, SceneTag.Clip),
        roadmapLinks = listOf(SceneRoadmapLink.milestone("M10")),
        expectation = SceneExpectation.ShouldRender,
        commands = listOf(
            SceneCommand.Clear(SceneColor(0.035f, 0.039f, 0.047f, 1f)),
            SceneCommand.Clip(
                label = "scissor-window",
                rect = SceneRect(64f, 56f, 256f, 144f),
            ),
            SceneCommand.FillRect(
                label = "full-span-rect",
                rect = SceneRect(24f, 28f, 296f, 172f),
                color = SceneColor.green(0.86f),
                paintOrder = 1,
            ),
            SceneCommand.FillRect(
                label = "overlay-marker-left",
                rect = SceneRect(78f, 66f, 110f, 134f),
                color = SceneColor.blue(0.82f),
                paintOrder = 2,
            ),
            SceneCommand.FillRect(
                label = "overlay-marker-right",
                rect = SceneRect(210f, 66f, 242f, 134f),
                color = SceneColor.amber(0.84f),
                paintOrder = 3,
            ),
        ),
    )

val m10M13RrectGradientScissorScenes: List<GPURendererScene<SceneCommand>> =
    listOf(roundedRectSolidsScene, linearGradientLanesScene, scissorOverlayScene)
