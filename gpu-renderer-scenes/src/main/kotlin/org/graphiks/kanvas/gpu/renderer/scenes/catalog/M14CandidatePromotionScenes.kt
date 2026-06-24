package org.graphiks.kanvas.gpu.renderer.scenes.catalog

import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneColor
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneCommand
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneRect

val radialSwatchScene: GPURendererScene<SceneCommand> =
    GPURendererScene(
        sceneId = SceneId("radial-swatch"),
        title = "Radial Swatch",
        description = "Three radial gradient rects with varying centers and radii exercising native radial gradient route evidence.",
        dimensions = SceneDimensions(320, 200),
        tags = setOf(SceneTag.Rect, SceneTag.Gradient),
        roadmapLinks = listOf(SceneRoadmapLink.milestone("M14")),
        expectation = SceneExpectation.ShouldRender,
        commands = listOf(
            SceneCommand.Clear(SceneColor(0.040f, 0.044f, 0.052f, 1f)),
            SceneCommand.RadialGradientRect(
                label = "radial-center-left",
                rect = SceneRect(16f, 24f, 104f, 176f),
                centerX = 60f,
                centerY = 100f,
                radius = 44f,
                startColor = SceneColor.amber(0.92f),
                endColor = SceneColor.green(0.72f),
                paintOrder = 1,
            ),
            SceneCommand.RadialGradientRect(
                label = "radial-center-middle",
                rect = SceneRect(116f, 40f, 204f, 160f),
                centerX = 160f,
                centerY = 100f,
                radius = 60f,
                startColor = SceneColor.blue(0.90f),
                endColor = SceneColor.red(),
                paintOrder = 2,
            ),
            SceneCommand.RadialGradientRect(
                label = "radial-offset-center",
                rect = SceneRect(216f, 52f, 304f, 148f),
                centerX = 240f,
                centerY = 70f,
                radius = 48f,
                startColor = SceneColor.green(0.88f),
                endColor = SceneColor.amber(0.82f),
                paintOrder = 3,
            ),
        ),
    )

val sweepDiskScene: GPURendererScene<SceneCommand> =
    GPURendererScene(
        sceneId = SceneId("sweep-disk"),
        title = "Sweep Disk",
        description = "Three sweep gradient rects with different start/end angles exercising native sweep gradient route evidence.",
        dimensions = SceneDimensions(320, 200),
        tags = setOf(SceneTag.Rect, SceneTag.Gradient),
        roadmapLinks = listOf(SceneRoadmapLink.milestone("M14")),
        expectation = SceneExpectation.ShouldRender,
        commands = listOf(
            SceneCommand.Clear(SceneColor(0.040f, 0.044f, 0.052f, 1f)),
            SceneCommand.SweepGradientRect(
                label = "sweep-full-360",
                rect = SceneRect(16f, 24f, 104f, 176f),
                centerX = 60f,
                centerY = 100f,
                startAngle = 0f,
                endAngle = 360f,
                startColor = SceneColor.amber(0.92f),
                endColor = SceneColor.green(0.72f),
                paintOrder = 1,
            ),
            SceneCommand.SweepGradientRect(
                label = "sweep-half-180",
                rect = SceneRect(116f, 40f, 204f, 160f),
                centerX = 160f,
                centerY = 100f,
                startAngle = 0f,
                endAngle = 180f,
                startColor = SceneColor.blue(0.90f),
                endColor = SceneColor.red(),
                paintOrder = 2,
            ),
            SceneCommand.SweepGradientRect(
                label = "sweep-quarter-90",
                rect = SceneRect(216f, 52f, 304f, 148f),
                centerX = 240f,
                centerY = 100f,
                startAngle = 45f,
                endAngle = 135f,
                startColor = SceneColor.green(0.88f),
                endColor = SceneColor.amber(0.82f),
                paintOrder = 3,
            ),
        ),
    )

val m14RadialSweepScenes: List<GPURendererScene<SceneCommand>> =
    listOf(radialSwatchScene, sweepDiskScene)
