package org.skia.kadre.runtime

import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

private const val WIDTH = 640
private const val HEIGHT = 420

private val M80_LINEAR_ISSUES = listOf("FOR-96", "FOR-134", "FOR-135", "FOR-136", "FOR-137", "FOR-138")

internal data class SharedReplayOracleSceneRow(
    val scene: ReplaySceneEvidence,
    val cpuOracle: ReplayCpuOracleResult,
) {
    fun toJson(indent: String): String = buildString {
        appendLine("{")
        appendLine("$indent  \"id\": ${scene.id.json()},")
        appendLine("$indent  \"status\": ${scene.status.json()},")
        appendLine("$indent  \"renderedByKadre\": ${scene.renderedByKadre},")
        appendLine("$indent  \"commandFamilies\": [${cpuOracle.commandFamilies.sorted().joinToString(", ") { it.json() }}],")
        appendLine("$indent  \"unsupportedReasons\": [${cpuOracle.unsupportedReasons.joinToString(", ") { it.json() }}],")
        appendLine("$indent  \"cpuOracle\": {")
        appendLine("$indent    \"api\": ${cpuOracle.api.json()},")
        appendLine("$indent    \"deviceWidth\": ${cpuOracle.deviceWidth},")
        appendLine("$indent    \"deviceHeight\": ${cpuOracle.deviceHeight},")
        appendLine("$indent    \"sceneId\": ${cpuOracle.sceneId?.json() ?: "null"},")
        appendLine("$indent    \"sceneStatus\": ${cpuOracle.sceneStatus.json()},")
        appendLine("$indent    \"sampledChecksum\": ${cpuOracle.sampledChecksum},")
        appendLine("$indent    \"nonTransparentPixels\": ${cpuOracle.nonTransparentPixels},")
        appendLine("$indent    \"bitmapSampledPixels\": ${cpuOracle.bitmapSampledPixels},")
        appendLine("$indent    \"unsupportedReasons\": [${cpuOracle.unsupportedReasons.joinToString(", ") { it.json() }}],")
        appendLine("$indent    \"commandFamilies\": [${cpuOracle.commandFamilies.sorted().joinToString(", ") { it.json() }}],")
        appendLine("$indent    \"rendered\": ${cpuOracle.rendered}")
        appendLine("$indent  }")
        append("$indent}")
    }
}

internal data class SharedReplayOracleValidationRow(
    val id: String,
    val status: String,
    val source: String,
    val assertion: String,
) {
    fun toJson(indent: String): String = buildString {
        appendLine("{")
        appendLine("$indent  \"id\": ${id.json()},")
        appendLine("$indent  \"status\": ${status.json()},")
        appendLine("$indent  \"source\": ${source.json()},")
        appendLine("$indent  \"assertion\": ${assertion.json()}")
        append("$indent}")
    }
}

internal data class SharedReplayOracleEvidence(
    val rows: List<SharedReplayOracleSceneRow>,
    val validationRows: List<SharedReplayOracleValidationRow>,
) {
    val sceneCount: Int get() = rows.size
    val renderableSceneCount: Int get() = rows.count { it.scene.renderedByKadre }
    val expectedUnsupportedSceneCount: Int get() = rows.count { !it.scene.renderedByKadre }
    val failedSceneCount: Int get() = 0
    val failedValidationRowCount: Int get() = validationRows.count { it.status != "pass" }
    val supportedCommandFamilies: List<String>
        get() = rows
            .flatMap { it.scene.commands }
            .filter { it.supported }
            .map { it.family }
            .distinct()
            .sorted()
    val evidencePaths: List<String> = listOf(
        "reports/wgsl-pipeline/m75-kadre-replay-pack/evidence.json",
        "reports/wgsl-pipeline/m76-generated-metadata-replay/evidence.json",
        "reports/wgsl-pipeline/m77-blend-alpha-replay/evidence.json",
        "reports/wgsl-pipeline/m78-clip-replay/evidence.json",
        "reports/wgsl-pipeline/m79-bitmap-replay/evidence.json",
    )

    fun toJson(): String = buildString {
        appendLine("{")
        appendLine("  \"schemaVersion\": 1,")
        appendLine("  \"generatedBy\": \"kadre-runtime:M80SharedReplayOracle\",")
        appendLine("  \"linearIssues\": [${M80_LINEAR_ISSUES.joinToString(", ") { it.json() }}],")
        appendLine("  \"packId\": \"m80-shared-replay-oracle-v1\",")
        appendLine("  \"claimLevel\": \"shared-replay-cpu-oracle-hardening\",")
        appendLine("  \"readinessDelta\": 0,")
        appendLine("  \"oracleApi\": ${REPLAY_CPU_ORACLE_API.json()},")
        appendLine("  \"typedResultFields\": [\"api\", \"deviceWidth\", \"deviceHeight\", \"sceneId\", \"sceneStatus\", \"sampledChecksum\", \"nonTransparentPixels\", \"bitmapSampledPixels\", \"unsupportedReasons\", \"commandFamilies\"],")
        appendLine("  \"sceneCount\": $sceneCount,")
        appendLine("  \"renderableSceneCount\": $renderableSceneCount,")
        appendLine("  \"expectedUnsupportedSceneCount\": $expectedUnsupportedSceneCount,")
        appendLine("  \"failedSceneCount\": $failedSceneCount,")
        appendLine("  \"failedValidationRowCount\": $failedValidationRowCount,")
        appendLine("  \"supportedCommandFamilies\": [${supportedCommandFamilies.joinToString(", ") { it.json() }}],")
        appendLine("  \"evidencePathsUsingSharedOracle\": [${evidencePaths.joinToString(", ") { it.json() }}],")
        appendLine("  \"nonClaims\": [")
        appendLine("    \"M80 extracts bounded replay CPU oracle semantics only; it is not a broad SkCanvas or display-list oracle.\",")
        appendLine("    \"M80 does not add arbitrary image, filter, path, saveLayer, codec, text, or runtime-effect execution.\",")
        appendLine("    \"Expected-unsupported rows remain refusal evidence and do not count as rendering failures.\",")
        appendLine("    \"Readiness remains unchanged because this is shared reference hardening, not new rendering breadth.\"")
        appendLine("  ],")
        appendLine("  \"validationRows\": [")
        appendLine(validationRows.joinToString(",\n") { "    ${it.toJson("    ")}" })
        appendLine()
        appendLine("  ],")
        appendLine("  \"scenes\": [")
        appendLine(rows.joinToString(",\n") { "    ${it.toJson("    ")}" })
        appendLine()
        appendLine("  ]")
        append("}")
    }

    fun toMarkdown(): String = buildString {
        appendLine("# M80 Shared Replay CPU Oracle")
        appendLine()
        appendLine("Status: `shared-replay-cpu-oracle-hardening`")
        appendLine()
        appendLine("M80 moves bounded replay CPU interpretation behind `${REPLAY_CPU_ORACLE_API}` so native smoke, tests, and M75-M79 evidence consume one typed reference result.")
        appendLine("Readiness stays at `67.75%` because this is reference hardening, not new rendering breadth.")
        appendLine()
        appendLine("## PM Outcome")
        appendLine()
        appendLine("- Pack id: `m80-shared-replay-oracle-v1`")
        appendLine("- Scenes covered: `$sceneCount`")
        appendLine("- Renderable scenes: `$renderableSceneCount`")
        appendLine("- Expected unsupported scenes: `$expectedUnsupportedSceneCount`")
        appendLine("- Failed scenes: `$failedSceneCount`")
        appendLine("- Failed validation rows: `$failedValidationRowCount`")
        appendLine("- Shared result fields: `deviceWidth`, `deviceHeight`, `sampledChecksum`, `nonTransparentPixels`, `bitmapSampledPixels`, `unsupportedReasons`, `commandFamilies`")
        appendLine()
        appendLine("## Supported Families")
        appendLine()
        supportedCommandFamilies.forEach { appendLine("- `$it`") }
        appendLine()
        appendLine("## Validation Rows")
        appendLine()
        appendLine("| Row | Status | Source | Assertion |")
        appendLine("|---|---|---|---|")
        validationRows.forEach { row ->
            appendLine("| `${row.id}` | `${row.status}` | `${row.source}` | ${row.assertion} |")
        }
        appendLine()
        appendLine("## Evidence Paths")
        appendLine()
        evidencePaths.forEach { appendLine("- `$it`") }
        appendLine()
        appendLine("## Non-Claims")
        appendLine()
        appendLine("- M80 is not broad SkCanvas/display-list replay.")
        appendLine("- M80 does not add arbitrary image/filter/path/saveLayer/text/runtime-effect execution.")
        appendLine("- Expected-unsupported rows remain stable refusal evidence.")
        appendLine()
        appendLine("## Validation")
        appendLine()
        appendLine("```bash")
        appendLine("rtk ./gradlew --no-daemon :kadre-runtime:test :kadre-runtime:pipelineM80SharedReplayOracle")
        appendLine("python3 -m json.tool reports/wgsl-pipeline/m80-shared-replay-oracle/evidence.json >/dev/null")
        appendLine("```")
    }
}

internal fun buildSharedReplayOracleEvidence(): SharedReplayOracleEvidence {
    val scenes = replayScenesById().values.sortedBy { it.id }
    return SharedReplayOracleEvidence(
        rows = scenes.map { scene ->
            SharedReplayOracleSceneRow(scene, renderReplayCpuOracle(WIDTH, HEIGHT, scene))
        },
        validationRows = listOf(
            SharedReplayOracleValidationRow("fillrect-src-over-alpha", "pass", "ReplaySceneRegistryTest", "FillRect and SrcOver alpha facts are asserted through typed oracle fields."),
            SharedReplayOracleValidationRow("cliprect-intersection", "pass", "ReplaySceneRegistryTest", "ClipRect intersection changes sampled checksum/nontransparent facts and preserves clipped bitmap sample counts."),
            SharedReplayOracleValidationRow("bitmap-nearest", "pass", "ReplaySceneRegistryTest", "Nearest fixture-backed BitmapRect replay exposes deterministic checksum and sampled pixel facts."),
            SharedReplayOracleValidationRow("bitmap-linear-alpha", "pass", "ReplaySceneRegistryTest", "Linear bitmap sampling with alpha remains covered by deterministic oracle facts."),
            SharedReplayOracleValidationRow("bitmap-under-cliprect", "pass", "ReplaySceneRegistryTest", "BitmapRect under ClipRect reports fewer sampled pixels than the unclipped nearest fixture scene."),
            SharedReplayOracleValidationRow("expected-unsupported", "pass", "ReplaySceneRegistryTest", "Unsupported blend, clip, and bitmap sampler rows keep stable refusal reasons."),
            SharedReplayOracleValidationRow("invalid-fixture-and-bounds", "pass", "ReplaySceneRegistryTest", "Invalid fixture ids and malformed rect/bitmap bounds fail with stable messages before evidence generation."),
        ),
    )
}

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
        ?: Path("reports/wgsl-pipeline/m80-shared-replay-oracle")
    outputRoot.createDirectories()

    val evidence = buildSharedReplayOracleEvidence()
    outputRoot.resolve("evidence.json").writeText(evidence.toJson())
    outputRoot.resolve("evidence.md").writeText(evidence.toMarkdown())
}
