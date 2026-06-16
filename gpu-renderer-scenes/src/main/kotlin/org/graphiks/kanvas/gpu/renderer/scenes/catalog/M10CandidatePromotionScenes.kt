package org.graphiks.kanvas.gpu.renderer.scenes.catalog

import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneColor
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneCommand
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneRect

val legacyParitySnapshotBoardScene: GPURendererScene<SceneCommand> =
    GPURendererScene(
        sceneId = SceneId("legacy-parity-snapshot-board"),
        title = "Legacy Parity Snapshot Board",
        description = "Legacy parity review board with evidence, parity, and retirement blocker lanes for snapshot promotion.",
        dimensions = SceneDimensions(width = 320, height = 200),
        tags = setOf(SceneTag.LegacyComparison, SceneTag.Rect, SceneTag.RRect),
        roadmapLinks = listOf(SceneRoadmapLink.milestone("M10")),
        expectation = SceneExpectation.ShouldRender,
        commands = listOf(
            SceneCommand.Clear(SceneColor(0.034f, 0.039f, 0.047f, 1f)),
            SceneCommand.FillRRect(
                label = "legacy-parity-review-panel",
                rect = SceneRect(20f, 22f, 300f, 180f),
                radius = 18f,
                color = SceneColor(0.10f, 0.12f, 0.15f, 1f),
            ),
            SceneCommand.Clip(
                label = "legacy-parity-review-window",
                rect = SceneRect(34f, 38f, 286f, 164f),
            ),
            SceneCommand.FillRect(
                label = "evidence-lane-header",
                rect = SceneRect(42f, 46f, 116f, 70f),
                color = SceneColor.blue(0.88f),
                paintOrder = 1,
            ),
            SceneCommand.FillRect(
                label = "parity-lane-header",
                rect = SceneRect(124f, 46f, 198f, 70f),
                color = SceneColor.green(0.88f),
                paintOrder = 2,
            ),
            SceneCommand.FillRect(
                label = "retirement-blocker-lane-header",
                rect = SceneRect(206f, 46f, 278f, 70f),
                color = SceneColor.amber(0.92f),
                paintOrder = 3,
            ),
            SceneCommand.FillRect(
                label = "snapshot-evidence-captured",
                rect = SceneRect(48f, 82f, 104f, 150f),
                color = SceneColor(0.84f, 0.88f, 0.94f, 0.94f),
                paintOrder = 4,
            ),
            SceneCommand.FillRect(
                label = "snapshot-evidence-gap",
                rect = SceneRect(88f, 96f, 114f, 150f),
                color = SceneColor.red(),
                paintOrder = 5,
            ),
            SceneCommand.FillRect(
                label = "parity-delta-stable",
                rect = SceneRect(132f, 78f, 170f, 150f),
                color = SceneColor.green(0.92f),
                paintOrder = 6,
            ),
            SceneCommand.FillRect(
                label = "parity-manual-review-required",
                rect = SceneRect(176f, 92f, 196f, 150f),
                color = SceneColor(0.60f, 0.46f, 0.90f, 0.94f),
                paintOrder = 7,
            ),
            SceneCommand.FillRect(
                label = "retirement-blocker-open",
                rect = SceneRect(214f, 74f, 244f, 150f),
                color = SceneColor.red(),
                paintOrder = 8,
            ),
            SceneCommand.FillRect(
                label = "retirement-blocker-contained",
                rect = SceneRect(250f, 104f, 272f, 150f),
                color = SceneColor.amber(0.84f),
                paintOrder = 9,
            ),
        ),
    )

val m10CandidatePromotionScenes: List<GPURendererScene<SceneCommand>> =
    listOf(legacyParitySnapshotBoardScene)
