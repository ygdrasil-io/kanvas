package org.graphiks.kanvas.gpu.renderer.scenes.catalog

import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneColor
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneCommand
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneRect

val verticesColorMeshScene: GPURendererScene<SceneCommand> =
    GPURendererScene(
        sceneId = SceneId("vertices-color-mesh"),
        title = "Vertices Color Mesh",
        description = "Colored triangle mesh using per-vertex colors to exercise M22 vertices executor.",
        dimensions = SceneDimensions(320, 200),
        tags = setOf(SceneTag.Vertices),
        roadmapLinks = listOf(SceneRoadmapLink.milestone("M22")),
        expectation = SceneExpectation.ShouldRender,
        commands = listOf(
            SceneCommand.Clear(SceneColor(0.035f, 0.040f, 0.048f, 1f)),
            SceneCommand.MeshRibbon(
                label = "color-mesh",
                bounds = SceneRect(40f, 36f, 280f, 164f),
                startColor = SceneColor(0.10f, 0.52f, 0.86f, 1f),
                endColor = SceneColor(0.98f, 0.62f, 0.18f, 1f),
                thickness = 36f,
            ),
        ),
    )

val meshRibbonDepthScene: GPURendererScene<SceneCommand> =
    GPURendererScene(
        sceneId = SceneId("mesh-ribbon-depth"),
        title = "Mesh Ribbon Depth",
        description = "Three overlapping mesh ribbons with distinct colors demonstrating depth ordering.",
        dimensions = SceneDimensions(320, 200),
        tags = setOf(SceneTag.Vertices),
        roadmapLinks = listOf(SceneRoadmapLink.milestone("M22")),
        expectation = SceneExpectation.ShouldRender,
        commands = listOf(
            SceneCommand.Clear(SceneColor(0.040f, 0.048f, 0.060f, 1f)),
            SceneCommand.MeshRibbon(
                label = "depth-back",
                bounds = SceneRect(36f, 30f, 284f, 110f),
                startColor = SceneColor(0.10f, 0.52f, 0.86f, 0.92f),
                endColor = SceneColor(0.32f, 0.84f, 0.94f, 0.84f),
                thickness = 22f,
                paintOrder = 1,
            ),
            SceneCommand.MeshRibbon(
                label = "depth-middle",
                bounds = SceneRect(60f, 60f, 300f, 150f),
                startColor = SceneColor(0.80f, 0.28f, 0.78f, 0.88f),
                endColor = SceneColor(0.98f, 0.66f, 0.22f, 0.90f),
                thickness = 26f,
                paintOrder = 2,
            ),
            SceneCommand.MeshRibbon(
                label = "depth-front",
                bounds = SceneRect(82f, 50f, 308f, 170f),
                startColor = SceneColor(0.22f, 0.78f, 0.46f, 0.94f),
                endColor = SceneColor(0.94f, 0.34f, 0.42f, 0.86f),
                thickness = 30f,
                paintOrder = 3,
            ),
        ),
    )

val m22CandidatePromotionScenes: List<GPURendererScene<SceneCommand>> =
    listOf(verticesColorMeshScene, meshRibbonDepthScene)
