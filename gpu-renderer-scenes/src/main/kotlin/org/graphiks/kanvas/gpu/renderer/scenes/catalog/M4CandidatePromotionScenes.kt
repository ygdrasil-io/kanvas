package org.graphiks.kanvas.gpu.renderer.scenes.catalog

import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneBitmapSampling
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneBitmapSource
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneColor
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneCommand
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneRect

val bitmapSamplerMatrixScene =
    GPURendererScene(
        sceneId = SceneId("bitmap-sampler-matrix"),
        title = "Bitmap Sampler Matrix",
        description = "Compact bitmap sampler matrix with clipped nearest and linear fixture cells.",
        dimensions = SceneDimensions(width = 320, height = 200),
        tags = setOf(SceneTag.Image, SceneTag.Clip, SceneTag.RRect),
        roadmapLinks = listOf(SceneRoadmapLink.milestone("M4")),
        expectation = SceneExpectation.ShouldRender,
        commands = listOf(
            SceneCommand.Clear(SceneColor(0.033f, 0.037f, 0.043f, 1f)),
            SceneCommand.FillRRect(
                label = "sampler-matrix-tray",
                rect = SceneRect(28f, 24f, 292f, 176f),
                radius = 18f,
                color = SceneColor(0.11f, 0.13f, 0.15f, 1f),
            ),
            SceneCommand.Clip(
                label = "sampler-matrix-viewport",
                rect = SceneRect(44f, 42f, 276f, 158f),
            ),
            SceneCommand.BitmapRect(
                label = "matrix-nearest-a",
                rect = SceneRect(52f, 50f, 138f, 98f),
                source = SceneBitmapSource(
                    topLeft = SceneColor(0.92f, 0.24f, 0.20f, 1f),
                    topRight = SceneColor(0.20f, 0.42f, 0.88f, 1f),
                    bottomLeft = SceneColor(0.96f, 0.72f, 0.18f, 1f),
                    bottomRight = SceneColor(0.20f, 0.70f, 0.46f, 1f),
                ),
                sampling = SceneBitmapSampling.Nearest,
                paintOrder = 1,
            ),
            SceneCommand.BitmapRect(
                label = "matrix-linear-a",
                rect = SceneRect(150f, 50f, 266f, 98f),
                source = SceneBitmapSource(
                    topLeft = SceneColor(0.18f, 0.46f, 0.88f, 1f),
                    topRight = SceneColor(0.90f, 0.32f, 0.22f, 1f),
                    bottomLeft = SceneColor(0.18f, 0.68f, 0.56f, 1f),
                    bottomRight = SceneColor(0.96f, 0.70f, 0.18f, 1f),
                ),
                sampling = SceneBitmapSampling.Linear,
                paintOrder = 2,
            ),
            SceneCommand.BitmapRect(
                label = "matrix-nearest-b",
                rect = SceneRect(52f, 110f, 138f, 150f),
                source = SceneBitmapSource(
                    topLeft = SceneColor(0.24f, 0.56f, 0.90f, 1f),
                    topRight = SceneColor(0.94f, 0.84f, 0.58f, 1f),
                    bottomLeft = SceneColor(0.16f, 0.70f, 0.50f, 1f),
                    bottomRight = SceneColor(0.42f, 0.24f, 0.78f, 1f),
                ),
                sampling = SceneBitmapSampling.Nearest,
                paintOrder = 3,
            ),
            SceneCommand.BitmapRect(
                label = "matrix-linear-b",
                rect = SceneRect(150f, 110f, 266f, 150f),
                source = SceneBitmapSource(
                    topLeft = SceneColor(0.88f, 0.26f, 0.22f, 1f),
                    topRight = SceneColor(0.20f, 0.50f, 0.86f, 1f),
                    bottomLeft = SceneColor(0.96f, 0.70f, 0.16f, 1f),
                    bottomRight = SceneColor(0.22f, 0.72f, 0.48f, 1f),
                ),
                sampling = SceneBitmapSampling.Linear,
                paintOrder = 4,
            ),
        ),
    )

val m4CandidatePromotionScenes =
    listOf(
        bitmapSamplerMatrixScene,
    )
