package org.graphiks.kanvas.gpu.renderer.scenes.catalog

import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneColor
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneBitmapSampling
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneBitmapSource
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneCommand
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneFilterKind
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneRect

object GPURendererSceneRegistry {
    val scenes: List<GPURendererScene<SceneCommand>> = listOf(
        scene(
            id = "solid-card-stack",
            title = "Solid Card Stack",
            description = "Rectangles with alpha values and deterministic draw order.",
            tags = setOf(SceneTag.Rect),
            links = listOf(
                SceneRoadmapLink.milestone("M0", RStage.R0),
                SceneRoadmapLink.milestone("M1", RStage.R1),
                SceneRoadmapLink.milestone("M1", RStage.R2),
                SceneRoadmapLink.milestone("M1", RStage.R3),
                SceneRoadmapLink.milestone("M1", RStage.R4),
                SceneRoadmapLink.milestone("M1", RStage.R5),
                SceneRoadmapLink.milestone("M1", RStage.R6),
            ),
            commands = listOf(
                SceneCommand.Clear(SceneColor(0.035f, 0.04f, 0.05f, 1f)),
                SceneCommand.FillRect(
                    "back-card",
                    SceneRect(24f, 28f, 210f, 136f),
                    SceneColor.blue(0.84f),
                    1,
                ),
                SceneCommand.FillRect(
                    "middle-card",
                    SceneRect(64f, 56f, 248f, 164f),
                    SceneColor.green(0.78f),
                    2,
                ),
                SceneCommand.FillRect(
                    "front-card",
                    SceneRect(112f, 88f, 292f, 184f),
                    SceneColor.amber(0.86f),
                    3,
                ),
            ),
        ),
        scene(
            id = "rounded-panel-gradient",
            title = "Rounded Panel Gradient",
            description = "Rounded rect with linear gradient and scissor.",
            tags = setOf(SceneTag.RRect, SceneTag.Gradient, SceneTag.Clip),
            links = listOf(
                SceneRoadmapLink.milestone("M2", RStage.R1),
                SceneRoadmapLink.milestone("M2", RStage.R2),
                SceneRoadmapLink.milestone("M2", RStage.R3),
            ),
            commands = listOf(
                SceneCommand.FillRRect(
                    label = "rounded-panel",
                    rect = SceneRect(40f, 30f, 280f, 170f),
                    radius = 24f,
                    color = SceneColor.blue(0.70f),
                ),
                SceneCommand.Clip(
                    label = "panel-scissor",
                    rect = SceneRect(64f, 46f, 256f, 154f),
                ),
                SceneCommand.LinearGradientRect(
                    label = "panel-gradient",
                    rect = SceneRect(40f, 30f, 280f, 170f),
                    startColor = SceneColor.amber(0.92f),
                    endColor = SceneColor.green(0.72f),
                    paintOrder = 1,
                ),
            ),
        ),
        scene(
            id = "release-gate-progress-board",
            title = "Release Gate Progress Board",
            description = "Bounded progress board with a rounded panel, simple scissor clip, and sorted overlay marker.",
            tags = setOf(SceneTag.Rect, SceneTag.RRect, SceneTag.Gradient, SceneTag.Clip),
            links = listOf(
                SceneRoadmapLink.ticket("KGPU-M2-003"),
                SceneRoadmapLink.ticket("KGPU-M2-004"),
            ),
            commands = listOf(
                SceneCommand.Clear(SceneColor(0.045f, 0.048f, 0.055f, 1f)),
                SceneCommand.FillRRect(
                    label = "gate-panel",
                    rect = SceneRect(34f, 42f, 286f, 158f),
                    radius = 18f,
                    color = SceneColor(0.12f, 0.15f, 0.17f, 1f),
                ),
                SceneCommand.Clip(
                    label = "gate-progress-scissor",
                    rect = SceneRect(54f, 82f, 232f, 126f),
                ),
                SceneCommand.LinearGradientRect(
                    label = "gate-progress-fill",
                    rect = SceneRect(46f, 76f, 274f, 134f),
                    startColor = SceneColor.green(0.92f),
                    endColor = SceneColor.amber(0.88f),
                    paintOrder = 1,
                ),
                SceneCommand.FillRect(
                    label = "gate-threshold-marker",
                    rect = SceneRect(210f, 78f, 222f, 132f),
                    color = SceneColor(0.96f, 0.98f, 0.94f, 0.90f),
                    paintOrder = 2,
                ),
            ),
        ),
        scene(
            id = "path-badge-and-stroke",
            title = "Path Badge And Stroke",
            description = "Rounded badge and rectangular stroke proxy using current command families.",
            tags = setOf(SceneTag.RRect, SceneTag.Rect),
            links = listOf(SceneRoadmapLink.milestone("M3")),
            commands = listOf(
                SceneCommand.FillRRect(
                    label = "badge-fill",
                    rect = SceneRect(56f, 52f, 248f, 132f),
                    radius = 32f,
                    color = SceneColor.green(0.86f),
                ),
                SceneCommand.FillRect(
                    "stroke-proxy",
                    SceneRect(72f, 72f, 220f, 84f),
                    SceneColor.red(),
                    paintOrder = 1,
                ),
            ),
        ),
        scene(
            id = "clipped-avatar-grid",
            title = "Clipped Avatar Grid",
            description = "Repeated content through bounded clip variants.",
            tags = setOf(SceneTag.Clip, SceneTag.Image),
            links = listOf(
                SceneRoadmapLink.milestone("M3"),
                SceneRoadmapLink.milestone("M5"),
            ),
            commands = listOf(
                SceneCommand.Clip(
                    label = "avatar-rrect-clip",
                    rect = SceneRect(36f, 28f, 284f, 172f),
                ),
                SceneCommand.BitmapRect(
                    label = "avatar-cell",
                    rect = SceneRect(24f, 20f, 296f, 180f),
                    source = SceneBitmapSource(
                        topLeft = SceneColor.blue(0.95f),
                        topRight = SceneColor.green(0.88f),
                        bottomLeft = SceneColor.amber(0.92f),
                        bottomRight = SceneColor.red(),
                    ),
                    sampling = SceneBitmapSampling.Linear,
                    paintOrder = 1,
                ),
            ),
        ),
        scene(
            id = "texture-swatch-board",
            title = "Texture Swatch Board",
            description = "Already-decoded bitmap swatches represented by bitmap-rect commands.",
            tags = setOf(SceneTag.Image),
            links = listOf(SceneRoadmapLink.milestone("M4")),
            commands = listOf(
                SceneCommand.BitmapRect(
                    label = "nearest-swatch",
                    rect = SceneRect(36f, 42f, 148f, 154f),
                    source = SceneBitmapSource(
                        topLeft = SceneColor.red(),
                        topRight = SceneColor.blue(),
                        bottomLeft = SceneColor.green(),
                        bottomRight = SceneColor.amber(),
                    ),
                    sampling = SceneBitmapSampling.Nearest,
                ),
                SceneCommand.BitmapRect(
                    label = "linear-swatch",
                    rect = SceneRect(172f, 42f, 284f, 154f),
                    source = SceneBitmapSource(
                        topLeft = SceneColor.amber(),
                        topRight = SceneColor.green(),
                        bottomLeft = SceneColor.blue(),
                        bottomRight = SceneColor.red(),
                    ),
                    sampling = SceneBitmapSampling.Linear,
                    paintOrder = 1,
                ),
            ),
        ),
        scene(
            id = "asset-intake-thumbnail-grid",
            title = "Asset Intake Thumbnail Grid",
            description = "Already-decoded asset thumbnails clipped inside an intake tray with upload-ready fixture payloads.",
            tags = setOf(SceneTag.Image, SceneTag.Clip, SceneTag.RRect),
            links = listOf(
                SceneRoadmapLink.ticket("KGPU-M4-001"),
                SceneRoadmapLink.ticket("KGPU-M4-002"),
            ),
            commands = listOf(
                SceneCommand.Clear(SceneColor(0.035f, 0.038f, 0.044f, 1f)),
                SceneCommand.FillRRect(
                    label = "asset-intake-tray",
                    rect = SceneRect(28f, 24f, 292f, 176f),
                    radius = 18f,
                    color = SceneColor(0.12f, 0.14f, 0.16f, 1f),
                ),
                SceneCommand.Clip(
                    label = "thumbnail-grid-viewport",
                    rect = SceneRect(44f, 42f, 276f, 158f),
                ),
                SceneCommand.BitmapRect(
                    label = "hero-upload-thumb",
                    rect = SceneRect(50f, 48f, 160f, 152f),
                    source = SceneBitmapSource(
                        topLeft = SceneColor(0.18f, 0.40f, 0.86f, 1f),
                        topRight = SceneColor(0.22f, 0.72f, 0.48f, 1f),
                        bottomLeft = SceneColor(0.96f, 0.70f, 0.18f, 1f),
                        bottomRight = SceneColor(0.92f, 0.26f, 0.22f, 1f),
                    ),
                    sampling = SceneBitmapSampling.Linear,
                    paintOrder = 1,
                ),
                SceneCommand.BitmapRect(
                    label = "secondary-upload-thumb",
                    rect = SceneRect(176f, 54f, 270f, 146f),
                    source = SceneBitmapSource(
                        topLeft = SceneColor(0.94f, 0.88f, 0.60f, 1f),
                        topRight = SceneColor(0.24f, 0.54f, 0.88f, 1f),
                        bottomLeft = SceneColor(0.16f, 0.68f, 0.54f, 1f),
                        bottomRight = SceneColor(0.38f, 0.24f, 0.76f, 1f),
                    ),
                    sampling = SceneBitmapSampling.Nearest,
                    paintOrder = 2,
                ),
            ),
        ),
        scene(
            id = "layered-shadow-card",
            title = "Layered Shadow Card",
            description = "Bounded shadow-card layer fixture with explicit drop-shadow filter route.",
            tags = setOf(SceneTag.Layer, SceneTag.Filter),
            links = listOf(SceneRoadmapLink.milestone("M5")),
            commands = listOf(
                SceneCommand.Clear(SceneColor(0.16f, 0.17f, 0.18f, 1f)),
                SceneCommand.SaveLayer(
                    label = "shadow-card-layer",
                    bounds = SceneRect(32f, 28f, 288f, 172f),
                    contentRect = SceneRect(48f, 44f, 270f, 154f),
                    radius = 20f,
                    contentColor = SceneColor(0.98f, 0.98f, 0.94f, 1f),
                    shadowColor = SceneColor(0.02f, 0.04f, 0.07f, 0.44f),
                    shadowOffsetX = 10f,
                    shadowOffsetY = 12f,
                ),
                SceneCommand.FilterNode(
                    label = "shadow-blur",
                    inputLabel = "shadow-card-layer",
                    kind = SceneFilterKind.DropShadow,
                    strength = 0.72f,
                ),
            ),
        ),
        scene(
            id = "filtered-photo-chip",
            title = "Filtered Photo Chip",
            description = "Bitmap and filter command markers for bounded route observation.",
            tags = setOf(SceneTag.Filter, SceneTag.Image),
            links = listOf(SceneRoadmapLink.milestone("M5")),
            commands = listOf(
                SceneCommand.BitmapRect(
                    label = "photo",
                    rect = SceneRect(48f, 34f, 272f, 166f),
                    source = SceneBitmapSource(
                        topLeft = SceneColor.red(),
                        topRight = SceneColor.blue(0.92f),
                        bottomLeft = SceneColor.green(0.90f),
                        bottomRight = SceneColor.amber(),
                    ),
                    sampling = SceneBitmapSampling.Linear,
                ),
                SceneCommand.FilterNode(
                    label = "chip-filter",
                    inputLabel = "photo",
                    kind = SceneFilterKind.LumaTint,
                    strength = 0.72f,
                ),
            ),
        ),
        scene(
            id = "receipt-text-run",
            title = "Receipt Text Run",
            description = "Simple typed text-run path and future A8 atlas route.",
            tags = setOf(SceneTag.Text),
            links = listOf(SceneRoadmapLink.milestone("M6")),
            commands = listOf(
                SceneCommand.TextRun(
                    label = "receipt-line",
                    text = "TOTAL 42.00",
                    baselineX = 42f,
                    baselineY = 118f,
                    fontSourceId = "kanvas-skia/src/main/resources/fonts/liberation/LiberationSans-Regular.ttf",
                    fontFamily = "Liberation Sans",
                    fontSize = 28f,
                    color = SceneColor(0.08f, 0.09f, 0.10f, 1f),
                ),
            ),
        ),
        scene(
            id = "runtime-effect-color-tile",
            title = "Runtime Effect Color Tile",
            description = "Registered SimpleRT runtime-effect tile with reflected gColor parameter intent.",
            tags = setOf(SceneTag.RuntimeEffect),
            links = listOf(SceneRoadmapLink.milestone("M7")),
            commands = listOf(
                SceneCommand.RuntimeEffectTile(
                    label = "simple-rt-color",
                    rect = SceneRect(48f, 36f, 272f, 164f),
                    stableId = "runtime.simple_rt",
                    wgslImplementationId = "wgsl/runtime_simple_rt",
                    uniformColor = SceneColor(0.18f, 0.42f, 0.72f, 1f),
                ),
            ),
        ),
        scene(
            id = "blend-mode-strip",
            title = "Blend Mode Strip",
            description = "Rect strip reserved for future explicit blend coverage.",
            tags = setOf(SceneTag.Rect),
            links = listOf(SceneRoadmapLink.milestone("M7")),
            commands = listOf(
                SceneCommand.FillRect(
                    "src-over-strip",
                    SceneRect(40f, 40f, 260f, 92f),
                    SceneColor.blue(0.72f),
                ),
            ),
        ),
        scene(
            id = "translucent-card-overlap",
            title = "Translucent Card Overlap",
            description = "Overlapping partial-alpha cards exercising bounded SrcOver blend order.",
            tags = setOf(SceneTag.Rect, SceneTag.Blend),
            links = listOf(SceneRoadmapLink.ticket("KGPU-M7-003")),
            commands = listOf(
                SceneCommand.Clear(SceneColor(0.04f, 0.045f, 0.05f, 1f)),
                SceneCommand.FillRect(
                    label = "bottom-blue-card",
                    rect = SceneRect(42f, 38f, 214f, 142f),
                    color = SceneColor.blue(0.68f),
                    paintOrder = 1,
                ),
                SceneCommand.FillRect(
                    label = "middle-green-card",
                    rect = SceneRect(88f, 62f, 260f, 166f),
                    color = SceneColor.green(0.62f),
                    paintOrder = 2,
                ),
                SceneCommand.FillRect(
                    label = "top-amber-card",
                    rect = SceneRect(128f, 28f, 286f, 128f),
                    color = SceneColor.amber(0.58f),
                    paintOrder = 3,
                ),
            ),
        ),
        scene(
            id = "mesh-ribbon",
            title = "Mesh Ribbon",
            description = "Bounded ribbon-strip fixture for vertices visibility without broad DrawVertices promotion.",
            tags = setOf(SceneTag.Vertices),
            links = listOf(SceneRoadmapLink.milestone("M8")),
            commands = listOf(
                SceneCommand.Clear(SceneColor(0.05f, 0.07f, 0.09f, 1f)),
                SceneCommand.MeshRibbon(
                    label = "ribbon",
                    bounds = SceneRect(36f, 42f, 284f, 158f),
                    startColor = SceneColor(0.10f, 0.52f, 0.86f, 1f),
                    endColor = SceneColor(0.98f, 0.62f, 0.18f, 1f),
                    thickness = 28f,
                ),
            ),
        ),
        scene(
            id = "cache-pressure-deck",
            title = "Cache Pressure Deck",
            description = "Repeated rect commands for future cache telemetry observation.",
            tags = setOf(SceneTag.Rect),
            links = listOf(SceneRoadmapLink.milestone("M9")),
            commands = listOf(
                SceneCommand.FillRect(
                    "cache-card-a",
                    SceneRect(24f, 24f, 160f, 112f),
                    SceneColor.green(),
                ),
                SceneCommand.FillRect(
                    "cache-card-b",
                    SceneRect(176f, 48f, 300f, 150f),
                    SceneColor.amber(),
                ),
            ),
        ),
        scene(
            id = "cache-source-ledger-board",
            title = "Cache Source Ledger Board",
            description = "Visible cache telemetry source classes without release-gate or readiness movement claims.",
            tags = setOf(SceneTag.Rect, SceneTag.Cache),
            links = listOf(SceneRoadmapLink.ticket("KGPU-M9-001")),
            commands = listOf(
                SceneCommand.Clear(SceneColor(0.040f, 0.044f, 0.052f, 1f)),
                SceneCommand.FillRect(
                    label = "observed-runtime-source",
                    rect = SceneRect(30f, 46f, 82f, 154f),
                    color = SceneColor.green(),
                    paintOrder = 1,
                ),
                SceneCommand.FillRect(
                    label = "observed-partial-source",
                    rect = SceneRect(90f, 66f, 142f, 154f),
                    color = SceneColor.amber(),
                    paintOrder = 2,
                ),
                SceneCommand.FillRect(
                    label = "derived-report-source",
                    rect = SceneRect(150f, 86f, 202f, 154f),
                    color = SceneColor.blue(),
                    paintOrder = 3,
                ),
                SceneCommand.FillRect(
                    label = "unavailable-runtime-source",
                    rect = SceneRect(210f, 106f, 262f, 154f),
                    color = SceneColor.red(),
                    paintOrder = 4,
                ),
                SceneCommand.FillRect(
                    label = "reporting-only-source",
                    rect = SceneRect(270f, 126f, 302f, 154f),
                    color = SceneColor(0.72f, 0.76f, 0.82f, 1f),
                    paintOrder = 5,
                ),
            ),
        ),
        scene(
            id = "legacy-route-comparison",
            title = "Legacy Route Comparison",
            description = "Rect command marker for future legacy-route comparison.",
            tags = setOf(SceneTag.Rect),
            links = listOf(SceneRoadmapLink.milestone("M10")),
            commands = listOf(
                SceneCommand.FillRect(
                    "comparison-rect",
                    SceneRect(48f, 48f, 272f, 152f),
                    SceneColor.blue(),
                ),
            ),
        ),
    )

    val registry: SceneRegistry<SceneCommand> = SceneRegistry(scenes)

    private fun scene(
        id: String,
        title: String,
        description: String,
        tags: Set<SceneTag>,
        links: List<SceneRoadmapLink>,
        commands: List<SceneCommand>,
    ): GPURendererScene<SceneCommand> =
        GPURendererScene(
            sceneId = SceneId(id),
            title = title,
            description = description,
            dimensions = SceneDimensions(320, 200),
            tags = tags,
            roadmapLinks = links,
            expectation = SceneExpectation.ShouldRender,
            commands = commands,
        )
}
