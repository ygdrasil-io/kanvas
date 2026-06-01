package org.skia.kadre.runtime

import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val WIDTH = 640
private const val HEIGHT = 420
private const val SOURCE_MANIFEST = "reports/wgsl-pipeline/scenes/generated/results.json"

internal data class GeneratedSceneReplayMetadata(
    val sceneId: String,
    val title: String,
    val status: String,
    val dashboardRow: String,
    val artifactRoot: String,
    val sourceReport: String,
    val cpuRoute: String,
    val gpuRoute: String,
    val pipelineKey: String,
    val tags: Set<String>,
)

internal sealed interface GeneratedMetadataReplayMapping {
    val metadata: GeneratedSceneReplayMetadata
    val status: String
    val reason: String

    data class Mapped(
        override val metadata: GeneratedSceneReplayMetadata,
        val scene: ReplaySceneEvidence,
        val cpuOracle: ReplayCpuOracleResult,
    ) : GeneratedMetadataReplayMapping {
        override val status: String = "metadata-mapped"
        override val reason: String = "m76.metadata.mapped-replay-contract"
    }

    data class Refused(
        override val metadata: GeneratedSceneReplayMetadata,
        override val reason: String,
    ) : GeneratedMetadataReplayMapping {
        override val status: String = "expected-unsupported"
    }
}

internal data class GeneratedMetadataReplayEvidence(
    val mappings: List<GeneratedMetadataReplayMapping>,
) {
    val sceneCount: Int get() = mappings.size
    val mappedSceneCount: Int get() = mappings.count { it is GeneratedMetadataReplayMapping.Mapped }
    val refusedMetadataCount: Int get() = mappings.count { it is GeneratedMetadataReplayMapping.Refused }
    val failedSceneCount: Int get() = 0

    fun toJson(): String = buildString {
        appendLine("{")
        appendLine("  \"schemaVersion\": 1,")
        appendLine("  \"generatedBy\": \"kadre-runtime:M76GeneratedMetadataReplay\",")
        appendLine("  \"linearIssues\": [\"FOR-92\", \"FOR-114\", \"FOR-115\", \"FOR-116\", \"FOR-117\", \"FOR-118\"],")
        appendLine("  \"packId\": \"m76-generated-metadata-replay-v1\",")
        appendLine("  \"sourceManifest\": ${SOURCE_MANIFEST.json()},")
        appendLine("  \"claimLevel\": \"selected-generated-metadata-to-replay-contract\",")
        appendLine("  \"readinessDelta\": 0,")
        appendLine("  \"sceneCount\": $sceneCount,")
        appendLine("  \"mappedSceneCount\": $mappedSceneCount,")
        appendLine("  \"refusedMetadataCount\": $refusedMetadataCount,")
        appendLine("  \"failedSceneCount\": $failedSceneCount,")
        appendLine("  \"mappingRules\": [")
        appendLine("    \"source status must be pass\",")
        appendLine("    \"source metadata must match a known bounded replay template\",")
        appendLine("    \"source tags must stay inside supported rect/gradient/bitmap/color-filter families\",")
        appendLine("    \"unsupported route families refuse with stable reasons\"")
        appendLine("  ],")
        appendLine("  \"nonClaims\": [")
        appendLine("    \"M76 maps selected generated metadata snapshots only; it does not parse arbitrary generated scenes at runtime.\",")
        appendLine("    \"M76 does not add broad SkCanvas or display-list replay.\",")
        appendLine("    \"M76 refusals are boundary evidence and do not count as rendering failures.\"")
        appendLine("  ],")
        appendLine("  \"mappings\": [")
        appendLine(mappings.joinToString(",\n") { "    ${it.toJson("    ")}" })
        appendLine()
        appendLine("  ]")
        append("}")
    }

    fun toMarkdown(): String = buildString {
        appendLine("# M76 Generated Metadata Replay")
        appendLine()
        appendLine("Status: `selected-metadata-replay-evidence`")
        appendLine()
        appendLine("M76 bridges selected generated dashboard metadata into typed Kadre replay contracts.")
        appendLine("The contract is intentionally narrow: only known bounded scene metadata maps; unsupported metadata refuses with stable reasons.")
        appendLine()
        appendLine("## PM Outcome")
        appendLine()
        appendLine("- Pack id: `m76-generated-metadata-replay-v1`")
        appendLine("- Source manifest: `$SOURCE_MANIFEST`")
        appendLine("- Metadata rows: `$sceneCount`")
        appendLine("- Mapped replay rows: `$mappedSceneCount`")
        appendLine("- Refused metadata rows: `$refusedMetadataCount`")
        appendLine("- Failed: `$failedSceneCount`")
        appendLine("- Readiness delta: `+0%`")
        appendLine()
        appendLine("## Source To Replay Routes")
        appendLine()
        appendLine("| Source scene | Mapping status | Reason | Replay scene | CPU route | GPU route |")
        appendLine("|---|---|---|---|---|---|")
        mappings.forEach { mapping ->
            val replayId = (mapping as? GeneratedMetadataReplayMapping.Mapped)?.scene?.id.orEmpty()
            appendLine(
                "| `${mapping.metadata.sceneId}` | `${mapping.status}` | `${mapping.reason}` | `${replayId}` | `${mapping.metadata.cpuRoute}` | `${mapping.metadata.gpuRoute}` |",
            )
        }
        appendLine()
        appendLine("## Mapping Rules")
        appendLine()
        appendLine("- Source status must be `pass`.")
        appendLine("- Source metadata must match a known bounded replay template.")
        appendLine("- Supported mapped families are current rect, linear-gradient, bitmap-nearest, and linear-gradient color-filter replay templates.")
        appendLine("- Path AA, image-filter DAG, text/font, runtime-effect, complex clip, and unknown metadata refuse until separate replay commands exist.")
        appendLine()
        appendLine("## Non-Claims")
        appendLine()
        appendLine("- M76 does not add arbitrary generated scene replay.")
        appendLine("- M76 does not add broad SkCanvas/display-list replay.")
        appendLine("- M76 refusals are expected boundary evidence, not failures.")
        appendLine()
        appendLine("## Validation")
        appendLine()
        appendLine("```bash")
        appendLine("rtk ./gradlew --no-daemon :kadre-runtime:test :kadre-runtime:pipelineM76GeneratedMetadataReplay")
        appendLine("```")
    }
}

private val m76SelectedSceneIds = listOf(
    "solid-rect",
    "linear-gradient-rect",
    "bitmap-rect-nearest",
    "gradient-color-filter-linear-kplus",
    "path-aa-convexpaths-edge-budget",
    "runtime-effect-simple",
)

private data class GeneratedSceneRouteContract(
    val cpuRoute: String,
    val gpuRoute: String,
    val pipelineKey: String,
)

private val m76RouteContracts = mapOf(
    "solid-rect" to GeneratedSceneRouteContract(
        cpuRoute = "cpu.descriptor.coverage-plan.solid-rect",
        gpuRoute = "webgpu.coverage.analytic-rect",
        pipelineKey = "coverageKind=analyticRect",
    ),
    "linear-gradient-rect" to GeneratedSceneRouteContract(
        cpuRoute = "cpu.shader.linear-gradient.rect",
        gpuRoute = "webgpu.generated.linear-gradient.rect",
        pipelineKey = "code=[entryPoint=fs_clamp,generatedPath=true,shaderFamily=linearGradient] state=[blendMode=kSrcOver]",
    ),
    "bitmap-rect-nearest" to GeneratedSceneRouteContract(
        cpuRoute = "cpu.image-rect.strict-nearest",
        gpuRoute = "webgpu.image-rect.strict-nearest",
        pipelineKey = "imageRect.strictNearest.promotedSmoke",
    ),
    "gradient-color-filter-linear-kplus" to GeneratedSceneRouteContract(
        cpuRoute = "cpu.shader.linear-gradient.color-filter.blend-kplus-oracle",
        gpuRoute = "webgpu.generated.linear-gradient.color-filter.blend-kplus",
        pipelineKey = "shaderFamily=linearGradient colorFilter=Blend(red,kPlus) coverage=analyticRect state=[blendMode=kSrcOver]",
    ),
    "path-aa-convexpaths-edge-budget" to GeneratedSceneRouteContract(
        cpuRoute = "cpu.coverage.path-aa-oracle",
        gpuRoute = "webgpu.coverage.path-aa.expected-unsupported",
        pipelineKey = "coverage=path-aa edgeBudget=exceeded status=expected-unsupported",
    ),
    "runtime-effect-simple" to GeneratedSceneRouteContract(
        cpuRoute = "cpu.runtime-effect.simple-registered",
        gpuRoute = "webgpu.runtime-effect.simple-registered",
        pipelineKey = "runtimeEffect=SimpleRT coverage=analyticRect state=[blendMode=kSrcOver]",
    ),
)

internal fun buildGeneratedMetadataReplayEvidence(manifestText: String): GeneratedMetadataReplayEvidence =
    GeneratedMetadataReplayEvidence(
        loadSelectedGeneratedMetadata(manifestText).map(::mapGeneratedMetadataToReplay),
    )

internal fun loadSelectedGeneratedMetadata(manifestText: String): List<GeneratedSceneReplayMetadata> {
    val root = Json.parseToJsonElement(manifestText).jsonObject
    val scenes = root["scenes"] as? JsonArray
        ?: error("M76 generated metadata manifest must contain scenes[]")
    val byId = scenes
        .mapNotNull { it as? JsonObject }
        .associateBy { it.stringField("id") }
    return m76SelectedSceneIds.map { sceneId ->
        val scene = byId[sceneId]
            ?: error("M76 selected generated scene metadata missing from manifest: $sceneId")
        val generation = scene["generation"] as? JsonObject
        val routeContract = m76RouteContracts.getValue(sceneId)
        GeneratedSceneReplayMetadata(
            sceneId = sceneId,
            title = scene.stringField("title"),
            status = scene.stringField("status"),
            dashboardRow = "$SOURCE_MANIFEST#$sceneId",
            artifactRoot = generation?.stringField("artifactRoot").orEmpty(),
            sourceReport = generation?.stringField("sourceReport").orEmpty(),
            cpuRoute = routeContract.cpuRoute,
            gpuRoute = routeContract.gpuRoute,
            pipelineKey = routeContract.pipelineKey,
            tags = scene.stringListField("tags").toSet(),
        )
    }
}

internal fun mapGeneratedMetadataToReplay(metadata: GeneratedSceneReplayMetadata): GeneratedMetadataReplayMapping {
    if (metadata.status != "pass") {
        return GeneratedMetadataReplayMapping.Refused(metadata, "m76.metadata.source-status-not-pass")
    }
    if (!metadata.tags.contains("route.gpu.webgpu")) {
        return GeneratedMetadataReplayMapping.Refused(metadata, "m76.metadata.missing-webgpu-route")
    }
    if (metadata.tags.any { it in unsupportedMetadataTags }) {
        return GeneratedMetadataReplayMapping.Refused(metadata, "m76.metadata.unsupported-route-family")
    }

    val template = M73_REPLAY_SCENES.firstOrNull { it.sourceSceneId == metadata.sceneId }
        ?: return GeneratedMetadataReplayMapping.Refused(metadata, "m76.metadata.no-replay-template")

    val scene = template.copy(
        id = "m76-${metadata.sceneId}-metadata-replay-v1",
        title = "M76 ${metadata.title}",
        source = "generated-scene-metadata",
        version = 1,
        commandSource = "m76-generated-scene-metadata-mapping",
        dashboardRow = metadata.dashboardRow,
        cpuRoute = metadata.cpuRoute,
        gpuRoute = metadata.gpuRoute,
        pipelineKey = metadata.pipelineKey,
    )
    val cpuOracle = renderReplayCpuOracle(WIDTH, HEIGHT, scene)
    return GeneratedMetadataReplayMapping.Mapped(
        metadata = metadata,
        scene = scene,
        cpuOracle = cpuOracle,
    )
}

private val unsupportedMetadataTags = setOf(
    "feature.path-aa",
    "feature.image-filter",
    "feature.text",
    "feature.runtime-effect",
    "feature.clip",
)

private fun JsonObject.stringField(name: String): String =
    (this[name] as? JsonPrimitive)?.jsonPrimitive?.content.orEmpty()

private fun JsonObject.stringListField(name: String): List<String> =
    (this[name] as? JsonArray)
        ?.mapNotNull { (it as? JsonPrimitive)?.jsonPrimitive?.content }
        .orEmpty()

private fun GeneratedMetadataReplayMapping.toJson(indent: String): String = buildString {
    appendLine("{")
    appendLine("$indent  \"sourceSceneId\": ${metadata.sceneId.json()},")
    appendLine("$indent  \"title\": ${metadata.title.json()},")
    appendLine("$indent  \"status\": ${status.json()},")
    appendLine("$indent  \"reason\": ${reason.json()},")
    appendLine("$indent  \"sourceStatus\": ${metadata.status.json()},")
    appendLine("$indent  \"dashboardRow\": ${metadata.dashboardRow.json()},")
    appendLine("$indent  \"artifactRoot\": ${metadata.artifactRoot.json()},")
    appendLine("$indent  \"sourceReport\": ${metadata.sourceReport.json()},")
    appendLine("$indent  \"cpuRoute\": ${metadata.cpuRoute.json()},")
    appendLine("$indent  \"gpuRoute\": ${metadata.gpuRoute.json()},")
    appendLine("$indent  \"pipelineKey\": ${metadata.pipelineKey.json()},")
    appendLine("$indent  \"tags\": [${metadata.tags.sorted().joinToString(", ") { it.json() }}],")
    when (val mapping = this@toJson) {
        is GeneratedMetadataReplayMapping.Mapped -> {
            appendLine("$indent  \"replaySceneId\": ${mapping.scene.id.json()},")
            appendLine("$indent  \"replayCommandSource\": ${mapping.scene.commandSource.json()},")
            appendLine("$indent  \"commandCounters\": {")
            appendLine("$indent    \"total\": ${mapping.scene.totalCommandCount},")
            appendLine("$indent    \"supported\": ${mapping.scene.supportedCommandCount},")
            appendLine("$indent    \"unsupported\": ${mapping.scene.unsupportedCommandCount},")
            appendLine("$indent    \"backgroundClear\": ${mapping.scene.commands.count { it is ReplayCommand.Clear }},")
            appendLine("$indent    \"fillRect\": ${mapping.scene.fillRectCount},")
            appendLine("$indent    \"animatedFillRect\": ${mapping.scene.animatedFillRectCount}")
            appendLine("$indent  },")
            appendLine("$indent  \"cpuReference\": {")
            appendLine("$indent    \"width\": $WIDTH,")
            appendLine("$indent    \"height\": $HEIGHT,")
            appendLine("$indent    \"checksum\": ${mapping.cpuOracle.sampledChecksum},")
            appendLine("$indent    \"nonTransparentPixels\": ${mapping.cpuOracle.nonTransparentPixels},")
            appendLine("$indent    \"bitmapSampledPixels\": ${mapping.cpuOracle.bitmapSampledPixels},")
            appendLine("$indent    \"oracleApi\": ${mapping.cpuOracle.api.json()}")
            appendLine("$indent  }")
        }
        is GeneratedMetadataReplayMapping.Refused -> {
            appendLine("$indent  \"replaySceneId\": null,")
            appendLine("$indent  \"commandCounters\": null,")
            appendLine("$indent  \"cpuReference\": null")
        }
    }
    append("$indent}")
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
    val manifestPath = args.firstOrNull()?.let(::Path)
        ?: Path(SOURCE_MANIFEST)
    val outputRoot = args.getOrNull(1)?.let(::Path)
        ?: Path("reports/wgsl-pipeline/m76-generated-metadata-replay")
    outputRoot.createDirectories()

    val evidence = buildGeneratedMetadataReplayEvidence(manifestPath.readText())
    outputRoot.resolve("evidence.json").writeText(evidence.toJson())
    outputRoot.resolve("evidence.md").writeText(evidence.toMarkdown())
}
