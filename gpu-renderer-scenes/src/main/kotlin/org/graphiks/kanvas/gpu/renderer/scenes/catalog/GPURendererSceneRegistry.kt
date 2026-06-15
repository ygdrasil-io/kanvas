package org.graphiks.kanvas.gpu.renderer.scenes.catalog

import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneColor
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneCommand
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
                SceneCommand.LinearGradientRect("panel-gradient"),
                SceneCommand.Clip("panel-scissor"),
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
                SceneCommand.Clip("avatar-rrect-clip"),
                SceneCommand.BitmapRect("avatar-cell"),
            ),
        ),
        scene(
            id = "texture-swatch-board",
            title = "Texture Swatch Board",
            description = "Already-decoded bitmap swatches represented by bitmap-rect commands.",
            tags = setOf(SceneTag.Image),
            links = listOf(SceneRoadmapLink.milestone("M4")),
            commands = listOf(
                SceneCommand.BitmapRect("nearest-swatch"),
                SceneCommand.BitmapRect("linear-swatch"),
            ),
        ),
        scene(
            id = "layered-shadow-card",
            title = "Layered Shadow Card",
            description = "Layer and filter command markers for bounded route observation.",
            tags = setOf(SceneTag.Layer, SceneTag.Filter),
            links = listOf(SceneRoadmapLink.milestone("M5")),
            commands = listOf(
                SceneCommand.SaveLayer("card-layer"),
                SceneCommand.FilterNode("shadow-blur"),
            ),
        ),
        scene(
            id = "filtered-photo-chip",
            title = "Filtered Photo Chip",
            description = "Bitmap and filter command markers for bounded route observation.",
            tags = setOf(SceneTag.Filter, SceneTag.Image),
            links = listOf(SceneRoadmapLink.milestone("M5")),
            commands = listOf(
                SceneCommand.BitmapRect("photo"),
                SceneCommand.FilterNode("chip-filter"),
            ),
        ),
        scene(
            id = "receipt-text-run",
            title = "Receipt Text Run",
            description = "Simple typed text-run path and future A8 atlas route.",
            tags = setOf(SceneTag.Text),
            links = listOf(SceneRoadmapLink.milestone("M6")),
            commands = listOf(SceneCommand.TextRun("receipt-line")),
        ),
        scene(
            id = "runtime-effect-color-tile",
            title = "Runtime Effect Color Tile",
            description = "Runtime-effect tile command marker with bounded parameter intent.",
            tags = setOf(SceneTag.RuntimeEffect),
            links = listOf(SceneRoadmapLink.milestone("M7")),
            commands = listOf(SceneCommand.RuntimeEffectTile("simple-rt-color")),
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
            id = "mesh-ribbon",
            title = "Mesh Ribbon",
            description = "Draw vertices and mesh-like geometry visibility.",
            tags = setOf(SceneTag.Vertices),
            links = listOf(SceneRoadmapLink.milestone("M8")),
            commands = listOf(SceneCommand.MeshRibbon("ribbon")),
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
