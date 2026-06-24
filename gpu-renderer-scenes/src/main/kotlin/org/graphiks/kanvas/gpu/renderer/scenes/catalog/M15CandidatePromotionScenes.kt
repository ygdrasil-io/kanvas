package org.graphiks.kanvas.gpu.renderer.scenes.catalog

import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneColor
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneCommand
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneRect

val pathFillStencilScene: GPURendererScene<SceneCommand> =
    GPURendererScene(
        sceneId = SceneId("path-fill-stencil"),
        title = "Path Fill Stencil",
        description = "Non-convex star path filled via two-pass stencil-cover rendering.",
        dimensions = SceneDimensions(320, 200),
        tags = setOf(SceneTag.Path),
        roadmapLinks = listOf(
            SceneRoadmapLink.milestone("M15"),
            SceneRoadmapLink.ticket("KGPU-M15-002"),
        ),
        expectation = SceneExpectation.ShouldRender,
        commands = listOf(
            SceneCommand.Clear(SceneColor(1f, 1f, 1f, 1f)),
            SceneCommand.PathFillStencil(
                label = "star-fill",
                fillColor = SceneColor.red(),
                paintOrder = 1,
                pathKind = "non-convex-star",
            ),
        ),
    )

val convexFanMeshScene: GPURendererScene<SceneCommand> =
    GPURendererScene(
        sceneId = SceneId("convex-fan-mesh"),
        title = "Convex Fan Mesh",
        description = "Convex octagon path filled via single-pass convex fan rendering.",
        dimensions = SceneDimensions(320, 200),
        tags = setOf(SceneTag.Path),
        roadmapLinks = listOf(
            SceneRoadmapLink.milestone("M15"),
            SceneRoadmapLink.ticket("KGPU-M15-003"),
        ),
        expectation = SceneExpectation.ShouldRender,
        commands = listOf(
            SceneCommand.Clear(SceneColor(1f, 1f, 1f, 1f)),
            SceneCommand.ConvexFanMesh(
                label = "octagon-fill",
                fillColor = SceneColor.blue(),
                paintOrder = 1,
                pathKind = "convex-octagon",
                vertexCount = 8,
            ),
        ),
    )

val m15CandidatePromotionScenes: List<GPURendererScene<SceneCommand>> =
    listOf(pathFillStencilScene, convexFanMeshScene)
