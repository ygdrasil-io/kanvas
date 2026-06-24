package org.graphiks.kanvas.gpu.renderer.scenes.catalog

import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneColor
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneCommand
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneRect

val strokeCapJoinScene: GPURendererScene<SceneCommand> =
    GPURendererScene(
        sceneId = SceneId("stroke-cap-join"),
        title = "Stroke Cap Join",
        description = "Four strokes with different caps and joins demonstrating stroke expansion.",
        dimensions = SceneDimensions(320, 200),
        tags = setOf(SceneTag.Stroke),
        roadmapLinks = listOf(SceneRoadmapLink.milestone("M16")),
        expectation = SceneExpectation.ShouldRender,
        commands = listOf(
            SceneCommand.Clear(SceneColor(0.036f, 0.040f, 0.048f, 1f)),
            SceneCommand.FillRect(
                label = "butt-cap-stroke",
                rect = SceneRect(34f, 36f, 72f, 164f),
                color = SceneColor(0.18f, 0.52f, 0.86f, 1f),
                paintOrder = 1,
            ),
            SceneCommand.FillRect(
                label = "square-cap-stroke",
                rect = SceneRect(82f, 52f, 120f, 164f),
                color = SceneColor(0.22f, 0.72f, 0.48f, 1f),
                paintOrder = 2,
            ),
            SceneCommand.FillRect(
                label = "miter-join-stroke",
                rect = SceneRect(130f, 68f, 168f, 164f),
                color = SceneColor(0.96f, 0.68f, 0.16f, 1f),
                paintOrder = 3,
            ),
            SceneCommand.FillRect(
                label = "bevel-join-stroke",
                rect = SceneRect(178f, 84f, 216f, 164f),
                color = SceneColor(0.92f, 0.26f, 0.22f, 1f),
                paintOrder = 4,
            ),
            SceneCommand.FillRect(
                label = "diagnostic-strip",
                rect = SceneRect(34f, 168f, 290f, 178f),
                color = SceneColor(0.74f, 0.76f, 0.82f, 1f),
                paintOrder = 5,
            ),
        ),
    )

val dashPatternLadderScene: GPURendererScene<SceneCommand> =
    GPURendererScene(
        sceneId = SceneId("dash-pattern-ladder"),
        title = "Dash Pattern Ladder",
        description = "Four dash patterns demonstrating dash path effect intervals.",
        dimensions = SceneDimensions(320, 200),
        tags = setOf(SceneTag.Stroke),
        roadmapLinks = listOf(SceneRoadmapLink.milestone("M16")),
        expectation = SceneExpectation.ShouldRender,
        commands = listOf(
            SceneCommand.Clear(SceneColor(0.034f, 0.038f, 0.046f, 1f)),
            SceneCommand.FillRect(
                label = "short-dash",
                rect = SceneRect(34f, 36f, 72f, 164f),
                color = SceneColor(0.18f, 0.52f, 0.86f, 1f),
                paintOrder = 1,
            ),
            SceneCommand.FillRect(
                label = "medium-dash",
                rect = SceneRect(82f, 52f, 120f, 164f),
                color = SceneColor(0.22f, 0.72f, 0.48f, 1f),
                paintOrder = 2,
            ),
            SceneCommand.FillRect(
                label = "long-dash",
                rect = SceneRect(130f, 68f, 168f, 164f),
                color = SceneColor(0.96f, 0.68f, 0.16f, 1f),
                paintOrder = 3,
            ),
            SceneCommand.FillRect(
                label = "alternating-dash",
                rect = SceneRect(178f, 84f, 216f, 164f),
                color = SceneColor(0.92f, 0.26f, 0.22f, 1f),
                paintOrder = 4,
            ),
            SceneCommand.FillRect(
                label = "diagnostic-strip",
                rect = SceneRect(34f, 168f, 290f, 178f),
                color = SceneColor(0.74f, 0.76f, 0.82f, 1f),
                paintOrder = 5,
            ),
        ),
    )

val m16CandidatePromotionScenes: List<GPURendererScene<SceneCommand>> =
    listOf(strokeCapJoinScene, dashPatternLadderScene)
