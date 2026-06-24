package org.graphiks.kanvas.gpu.renderer.scenes.catalog

import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneColor
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneCommand
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneRect

val performanceBudgetReviewScene: GPURendererScene<SceneCommand> =
    GPURendererScene(
        sceneId = SceneId("performance-budget-review"),
        title = "Performance Budget Review",
        description = "Performance budget lanes showing pass, warning, and fail thresholds without release-blocking or product activation claims.",
        dimensions = SceneDimensions(320, 200),
        tags = setOf(SceneTag.Rect, SceneTag.Cache),
        roadmapLinks = listOf(SceneRoadmapLink.ticket("KGPU-M23-001")),
        expectation = SceneExpectation.ShouldRender,
        commands = listOf(
            SceneCommand.Clear(SceneColor(0.036f, 0.040f, 0.048f, 1f)),
            SceneCommand.FillRect(
                label = "pass-budget-lane",
                rect = SceneRect(34f, 46f, 82f, 154f),
                color = SceneColor.green(0.90f),
                paintOrder = 1,
            ),
            SceneCommand.FillRect(
                label = "warning-budget-lane",
                rect = SceneRect(92f, 62f, 140f, 154f),
                color = SceneColor.amber(0.92f),
                paintOrder = 2,
            ),
            SceneCommand.FillRect(
                label = "fail-budget-lane",
                rect = SceneRect(150f, 78f, 198f, 154f),
                color = SceneColor(0.92f, 0.18f, 0.16f, 0.92f),
                paintOrder = 3,
            ),
            SceneCommand.FillRect(
                label = "reporting-only-nonblocking",
                rect = SceneRect(208f, 94f, 256f, 154f),
                color = SceneColor(0.74f, 0.76f, 0.82f, 1f),
                paintOrder = 4,
            ),
            SceneCommand.FillRect(
                label = "no-release-blocking-claim",
                rect = SceneRect(34f, 148f, 294f, 158f),
                color = SceneColor(0.84f, 0.88f, 0.94f, 0.92f),
                paintOrder = 5,
            ),
        ),
    )

val pipelineCacheTelemetryReviewScene: GPURendererScene<SceneCommand> =
    GPURendererScene(
        sceneId = SceneId("pipeline-cache-telemetry-review"),
        title = "Pipeline Cache Telemetry Review",
        description = "Pipeline cache hit rate, eviction count, and module count lanes without cache readiness movement.",
        dimensions = SceneDimensions(320, 200),
        tags = setOf(SceneTag.Rect, SceneTag.Cache),
        roadmapLinks = listOf(SceneRoadmapLink.ticket("KGPU-M23-002")),
        expectation = SceneExpectation.ShouldRender,
        commands = listOf(
            SceneCommand.Clear(SceneColor(0.034f, 0.038f, 0.046f, 1f)),
            SceneCommand.FillRect(
                label = "pipeline-hit-rate-observed",
                rect = SceneRect(34f, 48f, 82f, 154f),
                color = SceneColor.green(0.88f),
                paintOrder = 1,
            ),
            SceneCommand.FillRect(
                label = "pipeline-eviction-count-tracked",
                rect = SceneRect(92f, 64f, 140f, 154f),
                color = SceneColor.blue(0.84f),
                paintOrder = 2,
            ),
            SceneCommand.FillRect(
                label = "module-count-per-scene",
                rect = SceneRect(150f, 80f, 198f, 154f),
                color = SceneColor.amber(0.92f),
                paintOrder = 3,
            ),
            SceneCommand.FillRect(
                label = "no-cache-readiness-movement",
                rect = SceneRect(208f, 96f, 270f, 154f),
                color = SceneColor(0.74f, 0.76f, 0.82f, 1f),
                paintOrder = 4,
            ),
            SceneCommand.FillRect(
                label = "reporting-only-evidence",
                rect = SceneRect(34f, 148f, 294f, 158f),
                color = SceneColor(0.84f, 0.88f, 0.94f, 0.92f),
                paintOrder = 5,
            ),
        ),
    )

val frameGateM23BaselineScene: GPURendererScene<SceneCommand> =
    GPURendererScene(
        sceneId = SceneId("frame-gate-m23-baseline"),
        title = "Frame Gate M23 Baseline",
        description = "M23 60fps target and 30fps warning frame gate policy lanes with Apple M-series quarantine without release-blocking or product activation.",
        dimensions = SceneDimensions(320, 200),
        tags = setOf(SceneTag.Rect, SceneTag.Cache),
        roadmapLinks = listOf(SceneRoadmapLink.ticket("KGPU-M23-003")),
        expectation = SceneExpectation.ShouldRender,
        commands = listOf(
            SceneCommand.Clear(SceneColor(0.036f, 0.040f, 0.050f, 1f)),
            SceneCommand.FillRect(
                label = "sixty-fps-target-passing",
                rect = SceneRect(34f, 46f, 82f, 154f),
                color = SceneColor.green(0.88f),
                paintOrder = 1,
            ),
            SceneCommand.FillRect(
                label = "thirty-fps-warning-lane",
                rect = SceneRect(92f, 62f, 140f, 154f),
                color = SceneColor.amber(0.92f),
                paintOrder = 2,
            ),
            SceneCommand.FillRect(
                label = "m-series-quarantine-lane",
                rect = SceneRect(150f, 78f, 198f, 154f),
                color = SceneColor(0.44f, 0.34f, 0.86f, 0.92f),
                paintOrder = 3,
            ),
            SceneCommand.FillRect(
                label = "regression-quarantine-policy",
                rect = SceneRect(208f, 94f, 270f, 154f),
                color = SceneColor(0.22f, 0.70f, 0.78f, 0.92f),
                paintOrder = 4,
            ),
            SceneCommand.FillRect(
                label = "nonblocking-nonactivating",
                rect = SceneRect(34f, 148f, 294f, 158f),
                color = SceneColor(0.84f, 0.88f, 0.94f, 0.92f),
                paintOrder = 5,
            ),
        ),
    )

val pmEvidenceM23BundleScene: GPURendererScene<SceneCommand> =
    GPURendererScene(
        sceneId = SceneId("pm-evidence-m23-bundle"),
        title = "PM Evidence M23 Bundle",
        description = "M23 PM evidence bundle showing all families activated, gates green, and rollback tested without readiness movement.",
        dimensions = SceneDimensions(320, 200),
        tags = setOf(SceneTag.Rect, SceneTag.Cache),
        roadmapLinks = listOf(SceneRoadmapLink.ticket("KGPU-M23-004")),
        expectation = SceneExpectation.ShouldRender,
        commands = listOf(
            SceneCommand.Clear(SceneColor(0.034f, 0.038f, 0.046f, 1f)),
            SceneCommand.FillRect(
                label = "all-families-activated",
                rect = SceneRect(34f, 46f, 82f, 154f),
                color = SceneColor.green(0.90f),
                paintOrder = 1,
            ),
            SceneCommand.FillRect(
                label = "gates-green-lane",
                rect = SceneRect(92f, 62f, 140f, 154f),
                color = SceneColor.green(0.86f),
                paintOrder = 2,
            ),
            SceneCommand.FillRect(
                label = "rollback-tested-lane",
                rect = SceneRect(150f, 78f, 198f, 154f),
                color = SceneColor.blue(0.84f),
                paintOrder = 3,
            ),
            SceneCommand.FillRect(
                label = "pm-bundle-exported",
                rect = SceneRect(208f, 94f, 270f, 154f),
                color = SceneColor(0.44f, 0.34f, 0.86f, 0.92f),
                paintOrder = 4,
            ),
            SceneCommand.FillRect(
                label = "no-readiness-movement",
                rect = SceneRect(34f, 148f, 294f, 158f),
                color = SceneColor(0.84f, 0.88f, 0.94f, 0.92f),
                paintOrder = 5,
            ),
        ),
    )

val performanceGatesProductFlagScene: GPURendererScene<SceneCommand> =
    GPURendererScene(
        sceneId = SceneId("performance-gates-product-flag"),
        title = "Performance Gates Product Flag",
        description = "Performance gates product flag activation lane without release-blocking or readiness movement claims.",
        dimensions = SceneDimensions(320, 200),
        tags = setOf(SceneTag.Rect),
        roadmapLinks = listOf(SceneRoadmapLink.milestone("M23")),
        expectation = SceneExpectation.ShouldRender,
        commands = listOf(
            SceneCommand.Clear(SceneColor(0.036f, 0.042f, 0.050f, 1f)),
            SceneCommand.FillRect(
                label = "performance-gates-enabled",
                rect = SceneRect(48f, 48f, 272f, 152f),
                color = SceneColor.green(0.88f),
            ),
        ),
    )

val m23CandidatePromotionScenes: List<GPURendererScene<SceneCommand>> =
    listOf(
        performanceBudgetReviewScene,
        pipelineCacheTelemetryReviewScene,
        frameGateM23BaselineScene,
        pmEvidenceM23BundleScene,
        performanceGatesProductFlagScene,
    )
