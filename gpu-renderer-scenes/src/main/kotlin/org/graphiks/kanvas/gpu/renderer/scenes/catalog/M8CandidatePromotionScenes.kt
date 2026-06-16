package org.graphiks.kanvas.gpu.renderer.scenes.catalog

import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneColor
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneCommand
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneRect

val meshRibbonDepthStackScene: GPURendererScene<SceneCommand> =
    GPURendererScene(
        sceneId = SceneId("mesh-ribbon-depth-stack"),
        title = "Mesh Ribbon Depth Stack",
        description = "Stacked bounded mesh ribbons inside a clipped rounded panel to expose depth ordering.",
        dimensions = SceneDimensions(320, 200),
        tags = setOf(SceneTag.Vertices, SceneTag.RRect, SceneTag.Clip),
        roadmapLinks = listOf(SceneRoadmapLink.milestone("M8")),
        expectation = SceneExpectation.ShouldRender,
        commands = listOf(
            SceneCommand.Clear(SceneColor(0.040f, 0.048f, 0.060f, 1f)),
            SceneCommand.FillRRect(
                label = "depth-stack-panel",
                rect = SceneRect(28f, 24f, 292f, 176f),
                radius = 20f,
                color = SceneColor(0.11f, 0.13f, 0.16f, 1f),
            ),
            SceneCommand.Clip(
                label = "depth-stack-window",
                rect = SceneRect(42f, 40f, 278f, 160f),
            ),
            SceneCommand.MeshRibbon(
                label = "back-ribbon",
                bounds = SceneRect(54f, 54f, 238f, 116f),
                startColor = SceneColor(0.10f, 0.52f, 0.86f, 0.92f),
                endColor = SceneColor(0.32f, 0.84f, 0.94f, 0.84f),
                thickness = 22f,
                paintOrder = 1,
            ),
            SceneCommand.MeshRibbon(
                label = "middle-ribbon",
                bounds = SceneRect(74f, 76f, 258f, 140f),
                startColor = SceneColor(0.80f, 0.28f, 0.78f, 0.88f),
                endColor = SceneColor(0.98f, 0.66f, 0.22f, 0.90f),
                thickness = 26f,
                paintOrder = 2,
            ),
            SceneCommand.MeshRibbon(
                label = "front-ribbon",
                bounds = SceneRect(92f, 64f, 276f, 150f),
                startColor = SceneColor(0.22f, 0.78f, 0.46f, 0.94f),
                endColor = SceneColor(0.94f, 0.34f, 0.42f, 0.86f),
                thickness = 30f,
                paintOrder = 3,
            ),
        ),
    )

val m8CandidatePromotionScenes: List<GPURendererScene<SceneCommand>> =
    listOf(meshRibbonDepthStackScene)
