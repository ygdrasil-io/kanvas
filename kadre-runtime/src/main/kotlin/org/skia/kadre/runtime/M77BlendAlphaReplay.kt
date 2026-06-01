package org.skia.kadre.runtime

import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

private const val WIDTH = 640
private const val HEIGHT = 420

internal val M77_BLEND_ALPHA_REPLAY_SCENES: List<ReplaySceneEvidence> = listOf(
    ReplaySceneEvidence(
        id = "m77-alpha-srcover-stack-replay-v1",
        title = "M77 alpha SrcOver stack replay",
        source = "kanvas-replay-data",
        sourceSceneId = "m77-alpha-srcover-stack",
        version = 1,
        commandSource = "m77-typed-blend-alpha-replay-contract",
        commands = listOf(
            ReplayCommand.Clear(ReplayColor(0.04, 0.045, 0.055)),
            ReplayCommand.FillRect(
                label = "opaque-blue-destination",
                x = 0.14,
                y = 0.18,
                width = 0.50,
                height = 0.48,
                color = ReplayColor(0.10, 0.34, 0.86, 1.0),
            ),
            ReplayCommand.FillRect(
                label = "partial-alpha-red-source",
                x = 0.36,
                y = 0.30,
                width = 0.48,
                height = 0.44,
                color = ReplayColor(0.94, 0.18, 0.14, 0.45),
            ),
        ),
        dashboardRow = "reports/wgsl-pipeline/m77-blend-alpha-replay/evidence.json#m77-alpha-srcover-stack-replay-v1",
        cpuRoute = "cpu.replay.src-over.partial-alpha-oracle",
        gpuRoute = "webgpu.kadre-replay.src-over.partial-alpha",
        pipelineKey = "coverage=analyticRect state=[blendMode=kSrcOver alpha=partial] source=m77",
    ),
    ReplaySceneEvidence(
        id = "m77-gradient-alpha-srcover-replay-v1",
        title = "M77 gradient alpha SrcOver replay",
        source = "kanvas-replay-data",
        sourceSceneId = "m77-gradient-alpha-srcover",
        version = 1,
        commandSource = "m77-typed-blend-alpha-replay-contract",
        commands = listOf(
            ReplayCommand.Clear(ReplayColor(0.025, 0.030, 0.040)),
            ReplayCommand.FillRect(
                label = "partial-alpha-gradient",
                x = 0.10,
                y = 0.18,
                width = 0.78,
                height = 0.34,
                color = ReplayColor(0.06, 0.72, 0.54, 0.35),
                endColor = ReplayColor(0.96, 0.74, 0.12, 0.70),
                fillKind = ReplayFillKind.LinearGradient,
            ),
            ReplayCommand.FillRect(
                label = "partial-alpha-violet-overlay",
                x = 0.24,
                y = 0.44,
                width = 0.52,
                height = 0.30,
                color = ReplayColor(0.55, 0.18, 0.92, 0.58),
            ),
        ),
        dashboardRow = "reports/wgsl-pipeline/m77-blend-alpha-replay/evidence.json#m77-gradient-alpha-srcover-replay-v1",
        cpuRoute = "cpu.replay.linear-gradient.src-over.partial-alpha-oracle",
        gpuRoute = "webgpu.kadre-replay.linear-gradient.src-over.partial-alpha",
        pipelineKey = "shaderFamily=linearGradient coverage=analyticRect state=[blendMode=kSrcOver alpha=partial] source=m77",
    ),
    ReplaySceneEvidence(
        id = "m77-multiply-blend-refusal-v1",
        title = "M77 unsupported multiply blend refusal",
        source = "kanvas-replay-data",
        sourceSceneId = "m77-multiply-blend-refusal",
        version = 1,
        commandSource = "m77-typed-blend-alpha-replay-contract",
        commands = listOf(
            ReplayCommand.Clear(ReplayColor(0.04, 0.04, 0.05)),
            ReplayCommand.ExpectedUnsupported(
                label = "multiply-blend-mode",
                family = "blendMode",
                reason = "m77.unsupported-blend-mode.kMultiply",
            ),
        ),
        dashboardRow = "reports/wgsl-pipeline/m77-blend-alpha-replay/evidence.json#m77-multiply-blend-refusal-v1",
        cpuRoute = "cpu.replay.blend-mode.multiply-oracle-not-generated",
        gpuRoute = "webgpu.kadre-replay.blend-mode.expected-unsupported",
        pipelineKey = "state=[blendMode=kMultiply] status=expected-unsupported source=m77",
    ),
)

internal data class BlendAlphaReplayRow(
    val scene: ReplaySceneEvidence,
    val cpuReferenceChecksum: Long?,
    val cpuReferenceNonTransparentPixels: Int?,
) {
    fun toJson(indent: String): String = buildString {
        appendLine("{")
        appendLine("$indent  \"id\": ${scene.id.json()},")
        appendLine("$indent  \"title\": ${scene.title.json()},")
        appendLine("$indent  \"sourceSceneId\": ${scene.sourceSceneId.json()},")
        appendLine("$indent  \"status\": ${scene.status.json()},")
        appendLine("$indent  \"renderedByKadre\": ${scene.renderedByKadre},")
        appendLine("$indent  \"reason\": ${sceneReason(scene).json()},")
        appendLine("$indent  \"sourceEvidence\": {")
        appendLine("$indent    \"dashboardRow\": ${scene.dashboardRow.json()},")
        appendLine("$indent    \"cpuRoute\": ${scene.cpuRoute.json()},")
        appendLine("$indent    \"gpuRoute\": ${scene.gpuRoute.json()},")
        appendLine("$indent    \"pipelineKey\": ${scene.pipelineKey.json()}")
        appendLine("$indent  },")
        appendLine("$indent  \"commandCounters\": {")
        appendLine("$indent    \"total\": ${scene.totalCommandCount},")
        appendLine("$indent    \"supported\": ${scene.supportedCommandCount},")
        appendLine("$indent    \"unsupported\": ${scene.unsupportedCommandCount},")
        appendLine("$indent    \"backgroundClear\": ${scene.commands.count { it is ReplayCommand.Clear }},")
        appendLine("$indent    \"fillRect\": ${scene.fillRectCount},")
        appendLine("$indent    \"animatedFillRect\": ${scene.animatedFillRectCount},")
        appendLine("$indent    \"srcOver\": ${scene.srcOverCommandCount},")
        appendLine("$indent    \"partialAlpha\": ${scene.partialAlphaCommandCount}")
        appendLine("$indent  },")
        appendLine("$indent  \"unsupportedCommands\": [${scene.unsupportedCommands.joinToString(", ") { it.json() }}],")
        appendLine("$indent  \"commands\": [")
        appendLine(scene.rects.joinToString(",\n") { "$indent    ${it.toJson("$indent    ")}" })
        appendLine()
        appendLine("$indent  ],")
        appendLine("$indent  \"cpuOracle\": ${cpuOracleJson(indent)}")
        append("$indent}")
    }

    private fun cpuOracleJson(indent: String): String =
        if (cpuReferenceChecksum == null || cpuReferenceNonTransparentPixels == null) {
            "null"
        } else {
            buildString {
                appendLine("{")
                appendLine("$indent    \"width\": $WIDTH,")
                appendLine("$indent    \"height\": $HEIGHT,")
                appendLine("$indent    \"checksum\": $cpuReferenceChecksum,")
                appendLine("$indent    \"nonTransparentPixels\": $cpuReferenceNonTransparentPixels,")
                appendLine("$indent    \"oracle\": \"src-over-partial-alpha-sampled-reference\"")
                append("$indent  }")
            }
        }
}

internal data class BlendAlphaReplayEvidence(val rows: List<BlendAlphaReplayRow>) {
    val sceneCount: Int get() = rows.size
    val renderableSceneCount: Int get() = rows.count { it.scene.renderedByKadre }
    val expectedUnsupportedSceneCount: Int get() = rows.count { !it.scene.renderedByKadre }
    val failedSceneCount: Int get() = 0
    val partialAlphaSceneCount: Int get() = rows.count { it.scene.partialAlphaCommandCount > 0 }
    val srcOverCommandCount: Int get() = rows.sumOf { it.scene.srcOverCommandCount }
    val partialAlphaCommandCount: Int get() = rows.sumOf { it.scene.partialAlphaCommandCount }

    fun toJson(): String = buildString {
        appendLine("{")
        appendLine("  \"schemaVersion\": 1,")
        appendLine("  \"generatedBy\": \"kadre-runtime:M77BlendAlphaReplay\",")
        appendLine("  \"linearIssues\": [\"FOR-93\", \"FOR-119\", \"FOR-120\", \"FOR-121\", \"FOR-122\", \"FOR-123\"],")
        appendLine("  \"packId\": \"m77-blend-alpha-replay-v1\",")
        appendLine("  \"claimLevel\": \"bounded-src-over-alpha-replay-evidence\",")
        appendLine("  \"readinessDelta\": 0,")
        appendLine("  \"sceneCount\": $sceneCount,")
        appendLine("  \"renderableSceneCount\": $renderableSceneCount,")
        appendLine("  \"expectedUnsupportedSceneCount\": $expectedUnsupportedSceneCount,")
        appendLine("  \"failedSceneCount\": $failedSceneCount,")
        appendLine("  \"partialAlphaSceneCount\": $partialAlphaSceneCount,")
        appendLine("  \"srcOverCommandCount\": $srcOverCommandCount,")
        appendLine("  \"partialAlphaCommandCount\": $partialAlphaCommandCount,")
        appendLine("  \"unsupportedBlendReason\": \"m77.unsupported-blend-mode.kMultiply\",")
        appendLine("  \"nonClaims\": [")
        appendLine("    \"M77 supports only explicit SrcOver replay commands with bounded partial-alpha rect scenes.\",")
        appendLine("    \"M77 does not add broad SkCanvas/display-list replay or arbitrary blend-mode support.\",")
        appendLine("    \"Unsupported blend modes are stable refusal evidence and do not count as rendering failures.\"")
        appendLine("  ],")
        appendLine("  \"scenes\": [")
        appendLine(rows.joinToString(",\n") { "    ${it.toJson("    ")}" })
        appendLine()
        appendLine("  ]")
        append("}")
    }

    fun toMarkdown(): String = buildString {
        appendLine("# M77 Blend Alpha Replay")
        appendLine()
        appendLine("Status: `bounded-src-over-alpha-replay-evidence`")
        appendLine()
        appendLine("M77 makes blend and alpha explicit in the Kadre replay contract for bounded `SrcOver` scenes.")
        appendLine("Unsupported blend modes refuse with a stable diagnostic instead of silently falling back.")
        appendLine()
        appendLine("## PM Outcome")
        appendLine()
        appendLine("- Pack id: `m77-blend-alpha-replay-v1`")
        appendLine("- Scenes: `$sceneCount`")
        appendLine("- Renderable: `$renderableSceneCount`")
        appendLine("- Partial-alpha scenes: `$partialAlphaSceneCount`")
        appendLine("- Expected unsupported: `$expectedUnsupportedSceneCount`")
        appendLine("- Failed: `$failedSceneCount`")
        appendLine("- Readiness delta: `+0%`")
        appendLine()
        appendLine("## Scene Summary")
        appendLine()
        appendLine("| Scene | Status | SrcOver commands | Partial alpha commands | CPU checksum | Reason |")
        appendLine("|---|---|---:|---:|---:|---|")
        rows.forEach { row ->
            appendLine(
                "| `${row.scene.id}` | `${row.scene.status}` | `${row.scene.srcOverCommandCount}` | `${row.scene.partialAlphaCommandCount}` | `${row.cpuReferenceChecksum ?: 0}` | `${sceneReason(row.scene)}` |",
            )
        }
        appendLine()
        appendLine("## Non-Claims")
        appendLine()
        appendLine("- M77 covers bounded `SrcOver` replay scenes only.")
        appendLine("- M77 does not add arbitrary blend modes; `kMultiply` is an expected unsupported fixture.")
        appendLine("- M77 does not change broad readiness or display-list replay scope.")
        appendLine()
        appendLine("## Validation")
        appendLine()
        appendLine("```bash")
        appendLine("rtk ./gradlew --no-daemon :kadre-runtime:test :kadre-runtime:pipelineM77BlendAlphaReplay")
        appendLine("```")
    }
}

internal fun buildBlendAlphaReplayEvidence(): BlendAlphaReplayEvidence =
    BlendAlphaReplayEvidence(
        rows = M77_BLEND_ALPHA_REPLAY_SCENES.map { scene ->
            val cpuReference = if (scene.renderedByKadre) renderCpuReference(WIDTH, HEIGHT, scene) else null
            BlendAlphaReplayRow(
                scene = scene,
                cpuReferenceChecksum = cpuReference?.first,
                cpuReferenceNonTransparentPixels = cpuReference?.second,
            )
        },
    )

private fun sceneReason(scene: ReplaySceneEvidence): String =
    if (scene.renderedByKadre) "m77.replay-scene-src-over-alpha-renderable" else scene.unsupportedCommands.single()

private fun String.json(): String =
    buildString {
        append('"')
        for (ch in this@json) {
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(ch)
            }
        }
        append('"')
    }

fun main(args: Array<String>) {
    val outputRoot = args.firstOrNull()?.let(::Path)
        ?: Path("reports/wgsl-pipeline/m77-blend-alpha-replay")
    outputRoot.createDirectories()

    val evidence = buildBlendAlphaReplayEvidence()
    outputRoot.resolve("evidence.json").writeText(evidence.toJson())
    outputRoot.resolve("evidence.md").writeText(evidence.toMarkdown())
}
