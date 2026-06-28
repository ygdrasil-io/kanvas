package org.graphiks.kanvas.gpu.renderer.scenes.catalog

import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneColor
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneCommand
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneRect

val pathStarGradientScene: GPURendererScene<SceneCommand> =
    GPURendererScene(
        sceneId = SceneId("path-star-gradient"),
        title = "Path Star Gradient",
        description = "Non-convex star path filled with linear gradient via stencil-cover.",
        dimensions = SceneDimensions(320, 200),
        tags = setOf(SceneTag.Path, SceneTag.Gradient),
        roadmapLinks = listOf(SceneRoadmapLink.milestone("M26")),
        expectation = SceneExpectation.ShouldRender,
        commands = listOf(
            SceneCommand.Clear(SceneColor(0.036f, 0.040f, 0.048f, 1f)),
            SceneCommand.PathFillGradient(
                label = "star-gradient-fill",
                startColor = SceneColor.amber(0.92f),
                endColor = SceneColor.green(0.78f),
                paintOrder = 1,
                pathKind = "non-convex-star",
            ),
        ),
    )

val textA8HelloScene: GPURendererScene<SceneCommand> =
    GPURendererScene(
        sceneId = SceneId("text-a8-hello"),
        title = "Text A8 Hello",
        description = "Hello Kanvas text rendered via A8 glyph atlas route.",
        dimensions = SceneDimensions(320, 200),
        tags = setOf(SceneTag.Text),
        roadmapLinks = listOf(SceneRoadmapLink.milestone("M26")),
        expectation = SceneExpectation.ShouldRender,
        commands = listOf(
            SceneCommand.Clear(SceneColor(0.038f, 0.042f, 0.050f, 1f)),
            SceneCommand.TextRun(
                label = "hello-text",
                text = "Hello Kanvas",
                baselineX = 24f,
                baselineY = 108f,
                fontSourceId = "kanvas-skia/src/main/resources/fonts/liberation/LiberationSans-Regular.ttf",
                fontFamily = "Liberation Sans",
                fontSize = 36f,
                color = SceneColor(0.92f, 0.94f, 0.96f, 1f),
                glyphRoute = "font.glyph.a8-atlas",
                webGpuCandidateRoute = "webgpu.text.glyph-atlas.a8-sampled",
            ),
            SceneCommand.FillRect(
                label = "text-a8-diagnostic-strip",
                rect = SceneRect(24f, 184f, 296f, 196f),
                color = SceneColor(0.74f, 0.76f, 0.82f, 1f),
                paintOrder = 1,
            ),
        ),
    )

val gradientPathAndTextScene: GPURendererScene<SceneCommand> =
    GPURendererScene(
        sceneId = SceneId("gradient-path-and-text"),
        title = "Gradient Path And Text",
        description = "Gradient star path with text overlay via A8 atlas route.",
        dimensions = SceneDimensions(320, 200),
        tags = setOf(SceneTag.Path, SceneTag.Gradient, SceneTag.Text),
        roadmapLinks = listOf(SceneRoadmapLink.milestone("M26")),
        expectation = SceneExpectation.ShouldRender,
        commands = listOf(
            SceneCommand.Clear(SceneColor(0.034f, 0.038f, 0.046f, 1f)),
            SceneCommand.PathFillGradient(
                label = "combined-star-gradient",
                startColor = SceneColor.amber(0.88f),
                endColor = SceneColor(0.18f, 0.42f, 0.72f, 0.82f),
                paintOrder = 1,
                pathKind = "non-convex-star",
            ),
            SceneCommand.TextRun(
                label = "combined-text",
                text = "Kanvas",
                baselineX = 100f,
                baselineY = 110f,
                fontSourceId = "kanvas-skia/src/main/resources/fonts/liberation/LiberationSans-Regular.ttf",
                fontFamily = "Liberation Sans",
                fontSize = 32f,
                color = SceneColor(0.96f, 0.98f, 1f, 0.94f),
                glyphRoute = "font.glyph.a8-atlas",
                webGpuCandidateRoute = "webgpu.text.glyph-atlas.a8-sampled",
                paintOrder = 2,
            ),
        ),
    )

val m26TextActivationScenes: List<GPURendererScene<SceneCommand>> =
    listOf(pathStarGradientScene, textA8HelloScene, gradientPathAndTextScene)
