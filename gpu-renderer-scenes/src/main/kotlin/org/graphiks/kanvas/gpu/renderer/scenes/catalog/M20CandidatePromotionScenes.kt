package org.graphiks.kanvas.gpu.renderer.scenes.catalog

import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneColor
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneCommand
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneRect

val glyphAtlasStripScene: GPURendererScene<SceneCommand> =
    GPURendererScene(
        sceneId = SceneId("glyph-atlas-strip"),
        title = "Glyph Atlas Strip",
        description = "Line of A8 atlas glyphs sampling from a glyph texture atlas.",
        dimensions = SceneDimensions(320, 200),
        tags = setOf(SceneTag.Text),
        roadmapLinks = listOf(SceneRoadmapLink.milestone("M20")),
        expectation = SceneExpectation.ShouldRender,
        commands = listOf(
            SceneCommand.Clear(SceneColor(0.038f, 0.042f, 0.050f, 1f)),
            SceneCommand.TextRun(
                label = "glyph-atlas-strip-run",
                text = "ABCabc123",
                baselineX = 24f,
                baselineY = 100f,
                fontSourceId = "reports/font/fixtures/fonts/liberation/LiberationSans-Regular.ttf",
                fontFamily = "Liberation Sans",
                fontSize = 32f,
                color = SceneColor(0.92f, 0.94f, 0.96f, 1f),
                glyphRoute = "font.glyph.a8-atlas",
                webGpuCandidateRoute = "webgpu.text.glyph-atlas.a8-sampled",
                fallbackReason = "unsupported.text.draw_run_route_unavailable",
            ),
            SceneCommand.FillRect(
                label = "atlas-diagnostic-strip",
                rect = SceneRect(24f, 180f, 296f, 192f),
                color = SceneColor(0.74f, 0.76f, 0.82f, 1f),
                paintOrder = 1,
            ),
        ),
    )

val sdfGlyphScaleScene: GPURendererScene<SceneCommand> =
    GPURendererScene(
        sceneId = SceneId("sdf-glyph-scale"),
        title = "SDF Glyph Scale",
        description = "Same text rendered at 3 sizes with SDF smoothstep sampling.",
        dimensions = SceneDimensions(320, 200),
        tags = setOf(SceneTag.Text),
        roadmapLinks = listOf(SceneRoadmapLink.milestone("M20")),
        expectation = SceneExpectation.ShouldRender,
        commands = listOf(
            SceneCommand.Clear(SceneColor(0.040f, 0.044f, 0.050f, 1f)),
            SceneCommand.TextRun(
                label = "sdf-small",
                text = "Aa",
                baselineX = 24f,
                baselineY = 60f,
                fontSourceId = "reports/font/fixtures/fonts/liberation/LiberationSans-Regular.ttf",
                fontFamily = "Liberation Sans",
                fontSize = 18f,
                color = SceneColor(0.92f, 0.94f, 0.96f, 1f),
                glyphRoute = "font.glyph.sdf-atlas",
                webGpuCandidateRoute = "webgpu.text.glyph-atlas.sdf-sampled",
                fallbackReason = "unsupported.text.draw_run_route_unavailable",
            ),
            SceneCommand.TextRun(
                label = "sdf-medium",
                text = "Aa",
                baselineX = 24f,
                baselineY = 110f,
                fontSourceId = "reports/font/fixtures/fonts/liberation/LiberationSans-Regular.ttf",
                fontFamily = "Liberation Sans",
                fontSize = 32f,
                color = SceneColor(0.88f, 0.90f, 0.94f, 1f),
                glyphRoute = "font.glyph.sdf-atlas",
                webGpuCandidateRoute = "webgpu.text.glyph-atlas.sdf-sampled",
                fallbackReason = "unsupported.text.draw_run_route_unavailable",
                paintOrder = 1,
            ),
            SceneCommand.TextRun(
                label = "sdf-large",
                text = "Aa",
                baselineX = 24f,
                baselineY = 170f,
                fontSourceId = "reports/font/fixtures/fonts/liberation/LiberationSans-Regular.ttf",
                fontFamily = "Liberation Sans",
                fontSize = 48f,
                color = SceneColor(0.84f, 0.86f, 0.92f, 1f),
                glyphRoute = "font.glyph.sdf-atlas",
                webGpuCandidateRoute = "webgpu.text.glyph-atlas.sdf-sampled",
                fallbackReason = "unsupported.text.draw_run_route_unavailable",
                paintOrder = 2,
            ),
            SceneCommand.FillRect(
                label = "sdf-scale-diagnostic-strip",
                rect = SceneRect(24f, 186f, 296f, 196f),
                color = SceneColor(0.74f, 0.76f, 0.82f, 1f),
                paintOrder = 3,
            ),
        ),
    )

val m20CandidatePromotionScenes: List<GPURendererScene<SceneCommand>> =
    listOf(glyphAtlasStripScene, sdfGlyphScaleScene)
