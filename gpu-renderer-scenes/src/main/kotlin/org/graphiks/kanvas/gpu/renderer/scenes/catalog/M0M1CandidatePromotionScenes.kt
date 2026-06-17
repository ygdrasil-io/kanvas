package org.graphiks.kanvas.gpu.renderer.scenes.catalog

import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneColor
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneCommand
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneRect

val productRouteSmokeLanesScene: GPURendererScene<SceneCommand> =
    GPURendererScene(
        sceneId = SceneId("product-route-smoke-lanes"),
        title = "Product Route Smoke Lanes",
        description = "Horizontal smoke lanes compare legacy, candidate product route, and rollback refusal signals without claiming product activation.",
        dimensions = SceneDimensions(320, 200),
        tags = setOf(SceneTag.Rect, SceneTag.LegacyComparison),
        roadmapLinks = listOf(
            SceneRoadmapLink.milestone("M0"),
            SceneRoadmapLink.milestone("M1"),
        ),
        expectation = SceneExpectation.ShouldRender,
        commands = listOf(
            SceneCommand.Clear(SceneColor(0.036f, 0.040f, 0.047f, 1f)),
            SceneCommand.FillRect(
                label = "legacy-smoke-lane",
                rect = SceneRect(30f, 42f, 290f, 68f),
                color = SceneColor(0.45f, 0.50f, 0.58f, 1f),
                paintOrder = 1,
            ),
            SceneCommand.FillRect(
                label = "candidate-smoke-lane",
                rect = SceneRect(30f, 86f, 290f, 112f),
                color = SceneColor.green(0.90f),
                paintOrder = 2,
            ),
            SceneCommand.FillRect(
                label = "rollback-smoke-lane",
                rect = SceneRect(30f, 130f, 290f, 156f),
                color = SceneColor.amber(0.90f),
                paintOrder = 3,
            ),
            SceneCommand.FillRect(
                label = "legacy-lane-signal",
                rect = SceneRect(46f, 48f, 92f, 62f),
                color = SceneColor.blue(0.88f),
                paintOrder = 4,
            ),
            SceneCommand.FillRect(
                label = "candidate-route-signal",
                rect = SceneRect(156f, 92f, 214f, 106f),
                color = SceneColor(0.86f, 0.94f, 0.90f, 1f),
                paintOrder = 5,
            ),
            SceneCommand.FillRect(
                label = "rollback-refusal-signal",
                rect = SceneRect(232f, 136f, 278f, 150f),
                color = SceneColor.red(),
                paintOrder = 6,
            ),
            SceneCommand.FillRect(
                label = "smoke-lane-divider-left",
                rect = SceneRect(118f, 36f, 124f, 162f),
                color = SceneColor(0.20f, 0.24f, 0.30f, 1f),
                paintOrder = 7,
            ),
            SceneCommand.FillRect(
                label = "smoke-lane-divider-right",
                rect = SceneRect(210f, 36f, 216f, 162f),
                color = SceneColor(0.20f, 0.24f, 0.30f, 1f),
                paintOrder = 8,
            ),
        ),
    )

val m0M1CandidatePromotionScenes: List<GPURendererScene<SceneCommand>> =
    listOf(productRouteSmokeLanesScene)
