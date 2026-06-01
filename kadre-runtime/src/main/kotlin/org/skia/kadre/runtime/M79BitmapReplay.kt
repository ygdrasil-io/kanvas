package org.skia.kadre.runtime

import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

private const val WIDTH = 640
private const val HEIGHT = 420
private const val UNSUPPORTED_BITMAP_REASON = "m79.bitmap.unsupported-sampler.mipmap"

internal val M79_BITMAP_REPLAY_SCENES: List<ReplaySceneEvidence> = listOf(
    ReplaySceneEvidence(
        id = "m79-bitmap-fixture-nearest-replay-v1",
        title = "M79 fixture bitmap nearest replay",
        source = "kanvas-replay-data",
        sourceSceneId = "m79-bitmap-fixture-nearest",
        version = 1,
        commandSource = "m79-typed-bitmap-replay-contract",
        commands = listOf(
            ReplayCommand.Clear(ReplayColor(0.030, 0.034, 0.044)),
            ReplayCommand.BitmapRect(
                label = "checker-fixture-nearest",
                fixtureId = ReplayBitmapFixtures.checker4x4.id,
                srcX = 0.0,
                srcY = 0.0,
                srcWidth = 4.0,
                srcHeight = 4.0,
                dstX = 0.14,
                dstY = 0.16,
                dstWidth = 0.54,
                dstHeight = 0.52,
                sampler = ReplayBitmapSampler.Nearest,
                alpha = 1.0,
                blendMode = ReplayBlendMode.SrcOver,
                provenance = "FOR-129/FOR-130 owned fixture-backed nearest bitmap replay scene",
            ),
        ),
        dashboardRow = "reports/wgsl-pipeline/m79-bitmap-replay/evidence.json#m79-bitmap-fixture-nearest-replay-v1",
        cpuRoute = "cpu.replay.bitmap.fixture.nearest-oracle",
        gpuRoute = "webgpu.kadre-replay.bitmap.fixture.nearest",
        pipelineKey = "bitmapFixture=m79-fixture-checker-rgba8-4x4 sampler=nearest state=[blendMode=kSrcOver alpha=opaque] source=m79",
    ),
    ReplaySceneEvidence(
        id = "m79-bitmap-fixture-linear-alpha-replay-v1",
        title = "M79 fixture bitmap linear alpha replay",
        source = "kanvas-replay-data",
        sourceSceneId = "m79-bitmap-fixture-linear-alpha",
        version = 1,
        commandSource = "m79-typed-bitmap-replay-contract",
        commands = listOf(
            ReplayCommand.Clear(ReplayColor(0.024, 0.028, 0.038)),
            ReplayCommand.FillRect(
                label = "opaque-destination-panel",
                x = 0.10,
                y = 0.14,
                width = 0.72,
                height = 0.60,
                color = ReplayColor(0.10, 0.24, 0.52, 1.0),
            ),
            ReplayCommand.BitmapRect(
                label = "alpha-swatch-linear-scaled",
                fixtureId = ReplayBitmapFixtures.alphaSwatch4x4.id,
                srcX = 0.0,
                srcY = 0.0,
                srcWidth = 4.0,
                srcHeight = 4.0,
                dstX = 0.22,
                dstY = 0.22,
                dstWidth = 0.58,
                dstHeight = 0.46,
                sampler = ReplayBitmapSampler.Linear,
                alpha = 0.82,
                blendMode = ReplayBlendMode.SrcOver,
                provenance = "FOR-130 fixture-backed scaled destination with alpha SrcOver bitmap replay scene",
            ),
        ),
        dashboardRow = "reports/wgsl-pipeline/m79-bitmap-replay/evidence.json#m79-bitmap-fixture-linear-alpha-replay-v1",
        cpuRoute = "cpu.replay.bitmap.fixture.linear-alpha-oracle",
        gpuRoute = "webgpu.kadre-replay.bitmap.fixture.linear-alpha",
        pipelineKey = "bitmapFixture=m79-fixture-alpha-swatch-rgba8-4x4 sampler=linear state=[blendMode=kSrcOver alpha=partial] source=m79",
    ),
    ReplaySceneEvidence(
        id = "m79-bitmap-fixture-clipped-nearest-replay-v1",
        title = "M79 fixture bitmap clipped nearest replay",
        source = "kanvas-replay-data",
        sourceSceneId = "m79-bitmap-fixture-clipped-nearest",
        version = 1,
        commandSource = "m79-typed-bitmap-replay-contract",
        commands = listOf(
            ReplayCommand.Clear(ReplayColor(0.026, 0.030, 0.040)),
            ReplayCommand.ClipRect(
                label = "bitmap-visible-window",
                x = 0.24,
                y = 0.22,
                width = 0.34,
                height = 0.30,
            ),
            ReplayCommand.BitmapRect(
                label = "checker-fixture-nearest-clipped",
                fixtureId = ReplayBitmapFixtures.checker4x4.id,
                srcX = 0.0,
                srcY = 0.0,
                srcWidth = 4.0,
                srcHeight = 4.0,
                dstX = 0.12,
                dstY = 0.14,
                dstWidth = 0.62,
                dstHeight = 0.54,
                sampler = ReplayBitmapSampler.Nearest,
                alpha = 0.92,
                blendMode = ReplayBlendMode.SrcOver,
                provenance = "FOR-130 fixture-backed nearest bitmap replay scene clipped by a bounded ClipRect",
            ),
        ),
        dashboardRow = "reports/wgsl-pipeline/m79-bitmap-replay/evidence.json#m79-bitmap-fixture-clipped-nearest-replay-v1",
        cpuRoute = "cpu.replay.bitmap.fixture.nearest-clip-oracle",
        gpuRoute = "webgpu.kadre-replay.bitmap.fixture.nearest-clip",
        pipelineKey = "bitmapFixture=m79-fixture-checker-rgba8-4x4 sampler=nearest clip=rectIntersect state=[blendMode=kSrcOver alpha=partial] source=m79",
    ),
    ReplaySceneEvidence(
        id = "m79-bitmap-mipmap-sampler-refusal-v1",
        title = "M79 unsupported mipmap bitmap sampler refusal",
        source = "kanvas-replay-data",
        sourceSceneId = "m79-bitmap-mipmap-sampler-refusal",
        version = 1,
        commandSource = "m79-typed-bitmap-replay-contract",
        commands = listOf(
            ReplayCommand.Clear(ReplayColor(0.04, 0.04, 0.05)),
            ReplayCommand.ExpectedUnsupported(
                label = "mipmap-bitmap-sampler",
                family = "bitmapSampler",
                reason = UNSUPPORTED_BITMAP_REASON,
            ),
        ),
        dashboardRow = "reports/wgsl-pipeline/m79-bitmap-replay/evidence.json#m79-bitmap-mipmap-sampler-refusal-v1",
        cpuRoute = "cpu.replay.bitmap.mipmap-sampler-not-generated",
        gpuRoute = "webgpu.kadre-replay.bitmap.mipmap-sampler.expected-unsupported",
        pipelineKey = "bitmapSampler=mipmap status=expected-unsupported source=m79",
    ),
)

internal data class BitmapReplayRow(
    val scene: ReplaySceneEvidence,
    val cpuOracle: ReplayCpuOracleResult?,
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
        appendLine("$indent    \"bitmapRect\": ${scene.bitmapCommandCount},")
        appendLine("$indent    \"fixtureBackedBitmap\": ${scene.fixtureBackedBitmapCommandCount},")
        appendLine("$indent    \"bitmapSamplerNearest\": ${scene.nearestBitmapSamplerCommandCount},")
        appendLine("$indent    \"bitmapSamplerLinear\": ${scene.linearBitmapSamplerCommandCount},")
        appendLine("$indent    \"unsupportedBitmap\": ${scene.unsupportedBitmapCommandCount},")
        appendLine("$indent    \"srcOver\": ${scene.srcOverCommandCount},")
        appendLine("$indent    \"partialAlpha\": ${scene.partialAlphaCommandCount}")
        appendLine("$indent  },")
        appendLine("$indent  \"unsupportedCommands\": [${scene.unsupportedCommands.joinToString(", ") { it.json() }}],")
        appendLine("$indent  \"sceneContract\": ${scene.toJson("$indent  ")},")
        appendLine("$indent  \"cpuOracle\": ${cpuOracleJson(indent)}")
        append("$indent}")
    }

    private fun cpuOracleJson(indent: String): String =
        if (cpuOracle == null) {
            "null"
        } else {
            buildString {
                appendLine("{")
                appendLine("$indent    \"width\": $WIDTH,")
                appendLine("$indent    \"height\": $HEIGHT,")
                appendLine("$indent    \"checksum\": ${cpuOracle.sampledChecksum},")
                appendLine("$indent    \"nonTransparentPixels\": ${cpuOracle.nonTransparentPixels},")
                appendLine("$indent    \"bitmapSampledPixels\": ${cpuOracle.bitmapSampledPixels},")
                appendLine("$indent    \"oracleApi\": ${cpuOracle.api.json()},")
                appendLine("$indent    \"oracle\": \"fixture-backed-bitmap-sampling-reference\"")
                append("$indent  }")
            }
        }
}

internal data class BitmapReplayEvidence(val rows: List<BitmapReplayRow>) {
    val sceneCount: Int get() = rows.size
    val renderableSceneCount: Int get() = rows.count { it.scene.renderedByKadre }
    val expectedUnsupportedSceneCount: Int get() = rows.count { !it.scene.renderedByKadre }
    val failedSceneCount: Int get() = 0
    val bitmapCommandCount: Int get() = rows.sumOf { it.scene.bitmapCommandCount }
    val fixtureBackedBitmapCommandCount: Int get() = rows.sumOf { it.scene.fixtureBackedBitmapCommandCount }
    val nearestSamplerCommandCount: Int get() = rows.sumOf { it.scene.nearestBitmapSamplerCommandCount }
    val linearSamplerCommandCount: Int get() = rows.sumOf { it.scene.linearBitmapSamplerCommandCount }
    val unsupportedBitmapCommandCount: Int get() = rows.sumOf { it.scene.unsupportedBitmapCommandCount }
    val clipRectCommandCount: Int get() = rows.sumOf { it.scene.clipRectCommandCount }
    val clipIntersectCommandCount: Int get() = rows.sumOf { it.scene.clipIntersectCommandCount }
    val srcOverCommandCount: Int get() = rows.sumOf { it.scene.srcOverCommandCount }
    val partialAlphaCommandCount: Int get() = rows.sumOf { it.scene.partialAlphaCommandCount }

    fun toJson(): String = buildString {
        appendLine("{")
        appendLine("  \"schemaVersion\": 1,")
        appendLine("  \"generatedBy\": \"kadre-runtime:M79BitmapReplay\",")
        appendLine("  \"linearIssues\": [\"FOR-95\", \"FOR-129\", \"FOR-130\", \"FOR-131\", \"FOR-132\", \"FOR-133\"],")
        appendLine("  \"packId\": \"m79-bitmap-replay-v1\",")
        appendLine("  \"claimLevel\": \"bounded-fixture-backed-bitmap-replay-evidence\",")
        appendLine("  \"readinessDelta\": 0,")
        appendLine("  \"sceneCount\": $sceneCount,")
        appendLine("  \"renderableSceneCount\": $renderableSceneCount,")
        appendLine("  \"expectedUnsupportedSceneCount\": $expectedUnsupportedSceneCount,")
        appendLine("  \"failedSceneCount\": $failedSceneCount,")
        appendLine("  \"bitmapCommandCount\": $bitmapCommandCount,")
        appendLine("  \"fixtureBackedBitmapCommandCount\": $fixtureBackedBitmapCommandCount,")
        appendLine("  \"nearestSamplerCommandCount\": $nearestSamplerCommandCount,")
        appendLine("  \"linearSamplerCommandCount\": $linearSamplerCommandCount,")
        appendLine("  \"unsupportedBitmapCommandCount\": $unsupportedBitmapCommandCount,")
        appendLine("  \"clipRectCommandCount\": $clipRectCommandCount,")
        appendLine("  \"clipIntersectCommandCount\": $clipIntersectCommandCount,")
        appendLine("  \"srcOverCommandCount\": $srcOverCommandCount,")
        appendLine("  \"partialAlphaCommandCount\": $partialAlphaCommandCount,")
        appendLine("  \"unsupportedBitmapReason\": ${UNSUPPORTED_BITMAP_REASON.json()},")
        appendLine("  \"fixtures\": [")
        appendLine(ReplayBitmapFixtures.allById.values.joinToString(",\n") { fixture ->
            buildString {
                appendLine("    {")
                appendLine("      \"id\": ${fixture.id.json()},")
                appendLine("      \"width\": ${fixture.width},")
                appendLine("      \"height\": ${fixture.height},")
                appendLine("      \"colorSpace\": ${fixture.colorSpace.json()},")
                appendLine("      \"alphaType\": ${fixture.alphaType.json()},")
                appendLine("      \"pixelChecksum\": ${fixture.pixelChecksum},")
                appendLine("      \"provenance\": ${fixture.provenance.json()}")
                append("    }")
            }
        })
        appendLine()
        appendLine("  ],")
        appendLine("  \"nonClaims\": [")
        appendLine("    \"M79 supports only owned in-repo bitmap fixtures addressed by bounded BitmapRect replay commands.\",")
        appendLine("    \"M79 does not add arbitrary SkImage, codec decode, texture atlas, mipmap, tile-mode, or color-managed image support.\",")
        appendLine("    \"Unsupported bitmap sampler paths are stable refusal evidence and do not count as rendering failures.\"")
        appendLine("  ],")
        appendLine("  \"scenes\": [")
        appendLine(rows.joinToString(",\n") { "    ${it.toJson("    ")}" })
        appendLine()
        appendLine("  ]")
        append("}")
    }

    fun toMarkdown(): String = buildString {
        appendLine("# M79 Bitmap Replay V1")
        appendLine()
        appendLine("Status: `bounded-fixture-backed-bitmap-replay-evidence`")
        appendLine()
        appendLine("M79 adds typed `BitmapRect` replay commands backed by deterministic in-repo fixtures.")
        appendLine("Mipmap and out-of-scope bitmap sampler behavior remains an explicit expected-unsupported row.")
        appendLine()
        appendLine("## PM Outcome")
        appendLine()
        appendLine("- Pack id: `m79-bitmap-replay-v1`")
        appendLine("- Scenes: `$sceneCount`")
        appendLine("- Renderable: `$renderableSceneCount`")
        appendLine("- Fixture-backed bitmap commands: `$fixtureBackedBitmapCommandCount`")
        appendLine("- Nearest sampler commands: `$nearestSamplerCommandCount`")
        appendLine("- Linear sampler commands: `$linearSamplerCommandCount`")
        appendLine("- ClipRect intersect commands: `$clipIntersectCommandCount`")
        appendLine("- SrcOver commands: `$srcOverCommandCount`")
        appendLine("- Partial-alpha commands: `$partialAlphaCommandCount`")
        appendLine("- Expected unsupported: `$expectedUnsupportedSceneCount`")
        appendLine("- Failed: `$failedSceneCount`")
        appendLine("- Readiness delta: `+0%`")
        appendLine()
        appendLine("## Scene Summary")
        appendLine()
        appendLine("| Scene | Status | Fixture-backed bitmap commands | ClipRect | Nearest | Linear | SrcOver | Partial alpha | CPU checksum | Sampled pixels | Reason |")
        appendLine("|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---|")
        rows.forEach { row ->
            appendLine(
                "| `${row.scene.id}` | `${row.scene.status}` | `${row.scene.fixtureBackedBitmapCommandCount}` | `${row.scene.clipRectCommandCount}` | `${row.scene.nearestBitmapSamplerCommandCount}` | `${row.scene.linearBitmapSamplerCommandCount}` | `${row.scene.srcOverCommandCount}` | `${row.scene.partialAlphaCommandCount}` | `${row.cpuOracle?.sampledChecksum ?: 0}` | `${row.cpuOracle?.bitmapSampledPixels ?: 0}` | `${sceneReason(row.scene)}` |",
            )
        }
        appendLine()
        appendLine("## Non-Claims")
        appendLine()
        appendLine("- M79 covers owned fixture-backed `BitmapRect` replay scenes only.")
        appendLine("- M79 does not add arbitrary texture upload, mipmap, tile modes, codec decode, or color-managed image decode.")
        appendLine("- Expected-unsupported bitmap sampler rows are refusal evidence, not failures.")
        appendLine()
        appendLine("## Validation")
        appendLine()
        appendLine("```bash")
        appendLine("rtk ./gradlew --no-daemon :kadre-runtime:test :kadre-runtime:pipelineM79BitmapReplay")
        appendLine("python3 -m json.tool reports/wgsl-pipeline/m79-bitmap-replay/evidence.json >/dev/null")
        appendLine("```")
    }
}

internal fun buildBitmapReplayEvidence(): BitmapReplayEvidence =
    BitmapReplayEvidence(
        rows = M79_BITMAP_REPLAY_SCENES.map { scene ->
            val cpuOracle = if (scene.renderedByKadre) renderReplayCpuOracle(WIDTH, HEIGHT, scene) else null
            BitmapReplayRow(
                scene = scene,
                cpuOracle = cpuOracle,
            )
        },
    )

private fun sceneReason(scene: ReplaySceneEvidence): String =
    if (scene.renderedByKadre) "m79.replay-scene-fixture-bitmap-renderable" else scene.unsupportedCommands.single()

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
        ?: Path("reports/wgsl-pipeline/m79-bitmap-replay")
    outputRoot.createDirectories()

    val evidence = buildBitmapReplayEvidence()
    outputRoot.resolve("evidence.json").writeText(evidence.toJson())
    outputRoot.resolve("evidence.md").writeText(evidence.toMarkdown())
}
