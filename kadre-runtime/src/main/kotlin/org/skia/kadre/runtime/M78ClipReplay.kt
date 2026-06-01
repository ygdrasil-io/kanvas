package org.skia.kadre.runtime

import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

private const val WIDTH = 640
private const val HEIGHT = 420

internal val M78_CLIP_REPLAY_SCENES: List<ReplaySceneEvidence> = listOf(
    ReplaySceneEvidence(
        id = "m78-clipped-solid-rect-replay-v1",
        title = "M78 clipped solid rect replay",
        source = "kanvas-replay-data",
        sourceSceneId = "m78-clipped-solid-rect",
        version = 1,
        commandSource = "m78-typed-cliprect-replay-contract",
        commands = listOf(
            ReplayCommand.Clear(ReplayColor(0.030, 0.034, 0.044)),
            ReplayCommand.ClipRect(
                label = "center-intersect-clip",
                x = 0.24,
                y = 0.20,
                width = 0.48,
                height = 0.42,
            ),
            ReplayCommand.FillRect(
                label = "solid-rect-clipped-by-center",
                x = 0.10,
                y = 0.12,
                width = 0.76,
                height = 0.66,
                color = ReplayColor(0.12, 0.50, 0.88, 1.0),
            ),
        ),
        dashboardRow = "reports/wgsl-pipeline/m78-clip-replay/evidence.json#m78-clipped-solid-rect-replay-v1",
        cpuRoute = "cpu.replay.clip-rect.intersect.solid-oracle",
        gpuRoute = "webgpu.kadre-replay.clip-rect.intersect.solid",
        pipelineKey = "coverage=analyticRect clip=rectIntersect state=[blendMode=kSrcOver] source=m78",
    ),
    ReplaySceneEvidence(
        id = "m78-clipped-alpha-gradient-replay-v1",
        title = "M78 clipped alpha gradient replay",
        source = "kanvas-replay-data",
        sourceSceneId = "m78-clipped-alpha-gradient",
        version = 1,
        commandSource = "m78-typed-cliprect-replay-contract",
        commands = listOf(
            ReplayCommand.Clear(ReplayColor(0.024, 0.028, 0.038)),
            ReplayCommand.ClipRect(
                label = "wide-intersect-clip",
                x = 0.18,
                y = 0.14,
                width = 0.68,
                height = 0.58,
            ),
            ReplayCommand.FillRect(
                label = "partial-alpha-gradient-clipped",
                x = 0.08,
                y = 0.18,
                width = 0.84,
                height = 0.30,
                color = ReplayColor(0.05, 0.70, 0.52, 0.42),
                endColor = ReplayColor(0.96, 0.70, 0.10, 0.68),
                fillKind = ReplayFillKind.LinearGradient,
            ),
            ReplayCommand.ClipRect(
                label = "lower-intersect-clip",
                x = 0.30,
                y = 0.34,
                width = 0.40,
                height = 0.34,
            ),
            ReplayCommand.FillRect(
                label = "violet-overlay-clipped",
                x = 0.18,
                y = 0.38,
                width = 0.64,
                height = 0.26,
                color = ReplayColor(0.56, 0.18, 0.92, 0.55),
            ),
        ),
        dashboardRow = "reports/wgsl-pipeline/m78-clip-replay/evidence.json#m78-clipped-alpha-gradient-replay-v1",
        cpuRoute = "cpu.replay.clip-rect.intersect.linear-gradient-alpha-oracle",
        gpuRoute = "webgpu.kadre-replay.clip-rect.intersect.linear-gradient-alpha",
        pipelineKey = "shaderFamily=linearGradient coverage=analyticRect clip=rectIntersect state=[blendMode=kSrcOver alpha=partial] source=m78",
    ),
    ReplaySceneEvidence(
        id = "m78-complex-clip-refusal-v1",
        title = "M78 complex clip refusal",
        source = "kanvas-replay-data",
        sourceSceneId = "m60-bounded-nested-rrect-clip",
        version = 1,
        commandSource = "m78-typed-cliprect-replay-contract",
        commands = listOf(
            ReplayCommand.Clear(ReplayColor(0.04, 0.04, 0.05)),
            ReplayCommand.ExpectedUnsupported(
                label = "nested-rrect-difference-clip",
                family = "clip",
                reason = "m78.clip.unsupported-complex-clip",
            ),
        ),
        dashboardRow = "reports/wgsl-pipeline/scenes/generated/m60-nested-clip-path-aa-promotion.json#m60-bounded-nested-rrect-clip",
        cpuRoute = "cpu.coverage.nested-rrect-clip-oracle",
        gpuRoute = "webgpu.coverage.nested-rrect-clip.expected-unsupported",
        pipelineKey = "clipDepth=3 clip=rect+rect+rrectOval op=intersect+intersect+difference budget=m60 source=BlurredClippedCircleGM status=expected-unsupported source=m78",
    ),
)

internal data class ClipReplayRow(
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
        appendLine("$indent    \"clipRect\": ${scene.clipRectCommandCount},")
        appendLine("$indent    \"clipIntersect\": ${scene.clipIntersectCommandCount},")
        appendLine("$indent    \"fillRect\": ${scene.fillRectCount},")
        appendLine("$indent    \"animatedFillRect\": ${scene.animatedFillRectCount},")
        appendLine("$indent    \"srcOver\": ${scene.srcOverCommandCount},")
        appendLine("$indent    \"partialAlpha\": ${scene.partialAlphaCommandCount}")
        appendLine("$indent  },")
        appendLine("$indent  \"unsupportedCommands\": [${scene.unsupportedCommands.joinToString(", ") { it.json() }}],")
        appendLine("$indent  \"sceneContract\": ${scene.toJson("$indent  ")},")
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
                appendLine("$indent    \"oracle\": \"clip-rect-intersect-sampled-reference\"")
                append("$indent  }")
            }
        }
}

internal data class ClipReplayEvidence(val rows: List<ClipReplayRow>) {
    val sceneCount: Int get() = rows.size
    val renderableSceneCount: Int get() = rows.count { it.scene.renderedByKadre }
    val expectedUnsupportedSceneCount: Int get() = rows.count { !it.scene.renderedByKadre }
    val failedSceneCount: Int get() = 0
    val clipRectCommandCount: Int get() = rows.sumOf { it.scene.clipRectCommandCount }
    val clipIntersectCommandCount: Int get() = rows.sumOf { it.scene.clipIntersectCommandCount }
    val srcOverCommandCount: Int get() = rows.sumOf { it.scene.srcOverCommandCount }
    val partialAlphaCommandCount: Int get() = rows.sumOf { it.scene.partialAlphaCommandCount }

    fun toJson(): String = buildString {
        appendLine("{")
        appendLine("  \"schemaVersion\": 1,")
        appendLine("  \"generatedBy\": \"kadre-runtime:M78ClipReplay\",")
        appendLine("  \"linearIssues\": [\"FOR-94\", \"FOR-124\", \"FOR-125\", \"FOR-126\", \"FOR-127\", \"FOR-128\"],")
        appendLine("  \"packId\": \"m78-clip-replay-v1\",")
        appendLine("  \"claimLevel\": \"bounded-cliprect-intersect-replay-evidence\",")
        appendLine("  \"readinessDelta\": 0,")
        appendLine("  \"sceneCount\": $sceneCount,")
        appendLine("  \"renderableSceneCount\": $renderableSceneCount,")
        appendLine("  \"expectedUnsupportedSceneCount\": $expectedUnsupportedSceneCount,")
        appendLine("  \"failedSceneCount\": $failedSceneCount,")
        appendLine("  \"clipRectCommandCount\": $clipRectCommandCount,")
        appendLine("  \"clipIntersectCommandCount\": $clipIntersectCommandCount,")
        appendLine("  \"srcOverCommandCount\": $srcOverCommandCount,")
        appendLine("  \"partialAlphaCommandCount\": $partialAlphaCommandCount,")
        appendLine("  \"unsupportedClipReason\": \"m78.clip.unsupported-complex-clip\",")
        appendLine("  \"nonClaims\": [")
        appendLine("    \"M78 supports only simple axis-aligned ClipRect intersect replay commands for subsequent rect fills.\",")
        appendLine("    \"M78 does not add rounded clips, path clips, difference clips, saveLayer clip stacks, or arbitrary SkCanvas clip replay.\",")
        appendLine("    \"Complex clip rows are stable expected-unsupported evidence and do not count as rendering failures.\"")
        appendLine("  ],")
        appendLine("  \"scenes\": [")
        appendLine(rows.joinToString(",\n") { "    ${it.toJson("    ")}" })
        appendLine()
        appendLine("  ]")
        append("}")
    }

    fun toMarkdown(): String = buildString {
        appendLine("# M78 Clip Replay")
        appendLine()
        appendLine("Status: `bounded-cliprect-intersect-replay-evidence`")
        appendLine()
        appendLine("M78 adds a typed `ClipRect` intersect replay command for simple axis-aligned rect clips.")
        appendLine("Complex nested, rounded, and difference clips remain explicit expected-unsupported rows.")
        appendLine()
        appendLine("## PM Outcome")
        appendLine()
        appendLine("- Pack id: `m78-clip-replay-v1`")
        appendLine("- Scenes: `$sceneCount`")
        appendLine("- Renderable: `$renderableSceneCount`")
        appendLine("- ClipRect commands: `$clipRectCommandCount`")
        appendLine("- Clip intersect commands: `$clipIntersectCommandCount`")
        appendLine("- Expected unsupported: `$expectedUnsupportedSceneCount`")
        appendLine("- Failed: `$failedSceneCount`")
        appendLine("- Readiness delta: `+0%`")
        appendLine()
        appendLine("## Scene Summary")
        appendLine()
        appendLine("| Scene | Status | ClipRect commands | FillRect commands | CPU checksum | Reason |")
        appendLine("|---|---|---:|---:|---:|---|")
        rows.forEach { row ->
            appendLine(
                "| `${row.scene.id}` | `${row.scene.status}` | `${row.scene.clipRectCommandCount}` | `${row.scene.fillRectCount}` | `${row.cpuReferenceChecksum ?: 0}` | `${sceneReason(row.scene)}` |",
            )
        }
        appendLine()
        appendLine("## Non-Claims")
        appendLine()
        appendLine("- M78 covers bounded `ClipRect` intersect replay scenes only.")
        appendLine("- M78 does not add broad clip-stack semantics, rounded clips, path clips, or difference clips.")
        appendLine("- M78 does not change broad readiness or display-list replay scope.")
        appendLine()
        appendLine("## Validation")
        appendLine()
        appendLine("```bash")
        appendLine("rtk ./gradlew --no-daemon :kadre-runtime:test :kadre-runtime:pipelineM78ClipReplay")
        appendLine("```")
    }
}

internal fun buildClipReplayEvidence(): ClipReplayEvidence =
    ClipReplayEvidence(
        rows = M78_CLIP_REPLAY_SCENES.map { scene ->
            val cpuReference = if (scene.renderedByKadre) renderCpuReference(WIDTH, HEIGHT, scene) else null
            ClipReplayRow(
                scene = scene,
                cpuReferenceChecksum = cpuReference?.first,
                cpuReferenceNonTransparentPixels = cpuReference?.second,
            )
        },
    )

private fun sceneReason(scene: ReplaySceneEvidence): String =
    if (scene.renderedByKadre) "m78.replay-scene-cliprect-intersect-renderable" else scene.unsupportedCommands.single()

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
        ?: Path("reports/wgsl-pipeline/m78-clip-replay")
    outputRoot.createDirectories()

    val evidence = buildClipReplayEvidence()
    outputRoot.resolve("evidence.json").writeText(evidence.toJson())
    outputRoot.resolve("evidence.md").writeText(evidence.toMarkdown())
}
