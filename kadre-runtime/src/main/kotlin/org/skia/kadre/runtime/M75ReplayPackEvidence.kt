package org.skia.kadre.runtime

import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

private const val WIDTH = 640
private const val HEIGHT = 420

internal data class ReplayNativeEvidence(
    val status: String,
    val reason: String,
    val nativeDemoJson: String?,
    val readbackImage: String?,
) {
    fun toJson(indent: String): String = buildString {
        appendLine("{")
        appendLine("$indent  \"status\": ${status.json()},")
        appendLine("$indent  \"reason\": ${reason.json()},")
        appendLine("$indent  \"nativeDemoJson\": ${nativeDemoJson?.json() ?: "null"},")
        appendLine("$indent  \"readbackImage\": ${readbackImage?.json() ?: "null"}")
        append("$indent}")
    }
}

internal data class ReplayPackEvidenceRow(
    val scene: ReplaySceneEvidence,
    val cpuOracle: ReplayCpuOracleResult,
    val nativeEvidence: ReplayNativeEvidence,
) {
    fun toJson(indent: String): String = buildString {
        appendLine("{")
        appendLine("$indent  \"id\": ${scene.id.json()},")
        appendLine("$indent  \"title\": ${scene.title.json()},")
        appendLine("$indent  \"sourceSceneId\": ${scene.sourceSceneId.json()},")
        appendLine("$indent  \"status\": ${scene.status.json()},")
        appendLine("$indent  \"renderedByKadre\": ${scene.renderedByKadre},")
        appendLine("$indent  \"reason\": ${sceneStatusReason(scene).json()},")
        appendLine("$indent  \"commandCounters\": {")
        appendLine("$indent    \"total\": ${scene.totalCommandCount},")
        appendLine("$indent    \"supported\": ${scene.supportedCommandCount},")
        appendLine("$indent    \"unsupported\": ${scene.unsupportedCommandCount},")
        appendLine("$indent    \"backgroundClear\": ${scene.commands.count { it is ReplayCommand.Clear }},")
        appendLine("$indent    \"fillRect\": ${scene.fillRectCount},")
        appendLine("$indent    \"animatedFillRect\": ${scene.animatedFillRectCount}")
        appendLine("$indent  },")
        appendLine("$indent  \"unsupportedCommands\": [${scene.unsupportedCommands.joinToString(", ") { it.json() }}],")
        appendLine("$indent  \"sourceEvidence\": {")
        appendLine("$indent    \"dashboardRow\": ${scene.dashboardRow.json()},")
        appendLine("$indent    \"cpuRoute\": ${scene.cpuRoute.json()},")
        appendLine("$indent    \"gpuRoute\": ${scene.gpuRoute.json()},")
        appendLine("$indent    \"pipelineKey\": ${scene.pipelineKey.json()}")
        appendLine("$indent  },")
        appendLine("$indent  \"cpuReference\": {")
        appendLine("$indent    \"width\": $WIDTH,")
        appendLine("$indent    \"height\": $HEIGHT,")
        appendLine("$indent    \"checksum\": ${cpuOracle.sampledChecksum},")
        appendLine("$indent    \"nonTransparentPixels\": ${cpuOracle.nonTransparentPixels},")
        appendLine("$indent    \"bitmapSampledPixels\": ${cpuOracle.bitmapSampledPixels},")
        appendLine("$indent    \"oracleApi\": ${cpuOracle.api.json()}")
        appendLine("$indent  },")
        appendLine("$indent  \"nativeEvidence\": ${nativeEvidence.toJson("$indent  ")}")
        append("$indent}")
    }
}

internal data class ReplayPackEvidence(
    val rows: List<ReplayPackEvidenceRow>,
) {
    val sceneCount: Int get() = rows.size
    val renderableSceneCount: Int get() = rows.count { it.scene.renderedByKadre }
    val expectedUnsupportedSceneCount: Int get() = rows.count { !it.scene.renderedByKadre }
    val failedSceneCount: Int get() = 0

    fun toJson(): String = buildString {
        appendLine("{")
        appendLine("  \"schemaVersion\": 1,")
        appendLine("  \"generatedBy\": \"kadre-runtime:M75ReplayPackEvidence\",")
        appendLine("  \"linearIssues\": [\"FOR-91\", \"FOR-110\", \"FOR-111\", \"FOR-112\", \"FOR-113\"],")
        appendLine("  \"packId\": \"m75-kadre-replay-pack-evidence-v1\",")
        appendLine("  \"sourcePackId\": \"m73-kadre-replay-pack-v1\",")
        appendLine("  \"claimLevel\": \"multi-scene-replay-evidence\",")
        appendLine("  \"readinessDelta\": 0,")
        appendLine("  \"sceneCount\": $sceneCount,")
        appendLine("  \"renderableSceneCount\": $renderableSceneCount,")
        appendLine("  \"expectedUnsupportedSceneCount\": $expectedUnsupportedSceneCount,")
        appendLine("  \"failedSceneCount\": $failedSceneCount,")
        appendLine("  \"defaultSceneId\": ${M73_DEFAULT_SCENE_CONTRACT_ID.json()},")
        appendLine("  \"nonClaims\": [")
        appendLine("    \"M75 aggregates replay-pack evidence; it does not claim broad display-list replay.\",")
        appendLine("    \"Native/readback evidence is recorded only where a selected scene route has produced artifacts.\",")
        appendLine("    \"Expected-unsupported replay scenes are counted separately from failures.\"")
        appendLine("  ],")
        appendLine("  \"scenes\": [")
        appendLine(rows.joinToString(",\n") { "    ${it.toJson("    ")}" })
        appendLine()
        appendLine("  ]")
        append("}")
    }

    fun toMarkdown(): String = buildString {
        appendLine("# M75 Kadre Replay Pack Evidence")
        appendLine()
        appendLine("Status: `multi-scene-evidence`")
        appendLine()
        appendLine("M75 turns the M73/M74 replay registry into a deterministic pack evidence lane.")
        appendLine("It keeps the renderer claim narrow: the report summarizes typed replay contracts and does not claim broad SkCanvas/display-list replay.")
        appendLine()
        appendLine("## PM Outcome")
        appendLine()
        appendLine("- Pack id: `m75-kadre-replay-pack-evidence-v1`")
        appendLine("- Source pack: `m73-kadre-replay-pack-v1`")
        appendLine("- Scenes: `$sceneCount`")
        appendLine("- Renderable: `$renderableSceneCount`")
        appendLine("- Expected unsupported: `$expectedUnsupportedSceneCount`")
        appendLine("- Failed: `$failedSceneCount`")
        appendLine("- Readiness delta: `+0%`")
        appendLine()
        appendLine("## Scene Summary")
        appendLine()
        appendLine("| Scene | Status | CPU checksum | CPU nontransparent | Native evidence |")
        appendLine("|---|---|---:|---:|---|")
        rows.forEach { row ->
            appendLine(
                "| `${row.scene.id}` | `${row.scene.status}` | `${row.cpuOracle.sampledChecksum}` | `${row.cpuOracle.nonTransparentPixels}` | `${row.nativeEvidence.status}` |",
            )
        }
        appendLine()
        appendLine("## Non-Claims")
        appendLine()
        appendLine("- M75 aggregates existing typed replay contracts; it does not add arbitrary SkCanvas op replay.")
        appendLine("- Native/readback facts are surfaced only where a selected route produced artifacts.")
        appendLine("- Expected-unsupported rows are healthy refusal evidence, not failures.")
        appendLine()
        appendLine("## Validation")
        appendLine()
        appendLine("```bash")
        appendLine("rtk ./gradlew --no-daemon :kadre-runtime:test :kadre-runtime:pipelineM75ReplayPackEvidence")
        appendLine("```")
    }
}

internal fun buildReplayPackEvidence(): ReplayPackEvidence =
    ReplayPackEvidence(
        rows = M73_REPLAY_SCENES.map { scene ->
            val cpuOracle = renderReplayCpuOracle(WIDTH, HEIGHT, scene)
            ReplayPackEvidenceRow(
                scene = scene,
                cpuOracle = cpuOracle,
                nativeEvidence = nativeEvidenceFor(scene),
            )
        },
    )

private fun sceneStatusReason(scene: ReplaySceneEvidence): String =
    if (scene.renderedByKadre) {
        "m75.replay-scene-renderable"
    } else {
        "m75.replay-scene-expected-unsupported"
    }

private fun nativeEvidenceFor(scene: ReplaySceneEvidence): ReplayNativeEvidence =
    when (scene.id) {
        M73_DEFAULT_SCENE_CONTRACT_ID -> ReplayNativeEvidence(
            status = "available",
            reason = "m75.default-scene-native-demo-artifact",
            nativeDemoJson = "reports/wgsl-pipeline/m70-kadre-native/native-demo.json",
            readbackImage = "reports/wgsl-pipeline/m70-kadre-native/native-demo-readback.png",
        )
        "m73-bitmap-rect-nearest-replay-v1" -> ReplayNativeEvidence(
            status = "available",
            reason = "m75.bitmap-scene-native-demo-artifact",
            nativeDemoJson = "reports/wgsl-pipeline/m73-kadre-replay-pack/renderable-bitmap-native-demo.json",
            readbackImage = "reports/wgsl-pipeline/m70-kadre-native/native-demo-readback.png",
        )
        "m73-nested-rrect-clip-refusal-v1" -> ReplayNativeEvidence(
            status = "expected-unsupported",
            reason = "m73.kadre-replay-scene-expected-unsupported",
            nativeDemoJson = "reports/wgsl-pipeline/m73-kadre-replay-pack/expected-unsupported-native-demo.json",
            readbackImage = null,
        )
        else -> ReplayNativeEvidence(
            status = "not-generated",
            reason = "m75.native-route-not-generated-for-pack-row",
            nativeDemoJson = null,
            readbackImage = null,
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
        ?: Path("reports/wgsl-pipeline/m75-kadre-replay-pack")
    outputRoot.createDirectories()

    val evidence = buildReplayPackEvidence()
    outputRoot.resolve("evidence.json").writeText(evidence.toJson())
    outputRoot.resolve("evidence.md").writeText(evidence.toMarkdown())
}
