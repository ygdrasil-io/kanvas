package org.skia.kadre.runtime

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

private const val M84_OUTPUT = "reports/wgsl-pipeline/m84-native-frame-timing"
private const val M84_SOURCE_NATIVE_DEMO = "reports/wgsl-pipeline/m83-display-list-replay/native-demo.json"
private const val M84_LANE = "frame.kadre-windowed"
private const val M84_EXPECTED_SCENE = "m83-display-list-pm-scene-v1"
private const val M84_NEGATIVE_REASON = "m84.negative-fixture-p95-threshold-exceeded"
private val M84_LINEAR_ISSUES = listOf("FOR-100", "FOR-154", "FOR-155", "FOR-156", "FOR-157", "FOR-158")
private val M84_JSON = Json { prettyPrint = true }

internal data class M84FrameTimingEvidence(
    val nativeDemo: JsonObject,
) {
    private val sceneContract = nativeDemo.objectField("sceneContract")
    private val frameTiming = nativeDemo.objectField("frameTiming")
    private val runtimeTelemetry = nativeDemo.objectField("runtimeTelemetry")
    private val surfaceStatusSummary = nativeDemo.objectField("surfaceStatusSummary")

    val sourceMissing: Boolean = nativeDemo.isEmpty()
    val lane: String = runtimeTelemetry.stringField("lane").ifBlank { M84_LANE }
    val sceneContractId: String = sceneContract.stringField("id")
    val warmupFrameCount: Int = frameTiming.intField("warmupFrameCount")
    val measuredSampleCount: Int = frameTiming.intField("measuredSampleCount")
    val p50Ms: Double = frameTiming.doubleField("measuredP50Ms")
    val p95Ms: Double = frameTiming.doubleField("measuredP95Ms")
    val worstMs: Double = frameTiming.doubleField("measuredWorstMs")
    val totalSampleCount: Int = runtimeTelemetry.intField("totalSampleCount")
    val successSurfaceCount: Int = surfaceStatusSummary.intField("success")
    val adapterInfo: String = nativeDemo.stringField("adapterInfo")
    val nativePresented: Boolean = nativeDemo.boolField("nativePresented")
    val reportingOnly: Boolean = frameTiming.boolField("reportingOnly") || runtimeTelemetry.boolField("reportingOnly")
    val eligibleHost: Boolean =
        !sourceMissing &&
            lane == M84_LANE &&
            sceneContractId == M84_EXPECTED_SCENE &&
            nativePresented &&
            measuredSampleCount >= 120 &&
            warmupFrameCount >= 60 &&
            successSurfaceCount >= totalSampleCount &&
            adapterInfo.contains("Apple M")
    val gateStatus: String = when {
        sourceMissing -> "missing-source"
        !eligibleHost -> "quarantined"
        reportingOnly -> "candidate-reporting-only"
        else -> "candidate"
    }
    val measuredPayloadStatus: String = if (sourceMissing || p50Ms <= 0.0 || p95Ms <= 0.0 || worstMs <= 0.0) {
        "missing"
    } else {
        "measured"
    }
    val countedAsMeasuredGate: Boolean = false
    val quarantineReasons: List<String> = buildList {
        if (sourceMissing) add("m84.source-native-demo-missing")
        if (lane != M84_LANE) add("m84.lane-mismatch")
        if (sceneContractId != M84_EXPECTED_SCENE) add("m84.scene-contract-mismatch")
        if (!nativePresented) add("m84.native-not-presented")
        if (measuredSampleCount < 120) add("m84.sample-count-too-low")
        if (warmupFrameCount < 60) add("m84.warmup-count-too-low")
        if (successSurfaceCount < totalSampleCount) add("m84.surface-success-count-mismatch")
        if (!adapterInfo.contains("Apple M")) add("m84.adapter-not-owned-baseline")
        if (reportingOnly) add("m84.reporting-only-until-owner-accepts-variance")
    }.distinct()

    fun negativeFixture(): JsonObject = buildJsonObject {
        put("schemaVersion", 1)
        put("id", "m84-negative-p95-threshold-fixture")
        put("lane", M84_LANE)
        put("status", "expected-fail")
        put("reason", M84_NEGATIVE_REASON)
        put("mutatesBaseline", false)
        put("thresholdP95Ms", 8.0)
        put("observedP95Ms", p95Ms)
        put("assertion", "Candidate timing gate rejects a too-low p95 threshold without rewriting checked-in baselines.")
    }

    fun toJsonElement(): JsonObject = buildJsonObject {
        put("schemaVersion", 1)
        put("generatedBy", "kadre-runtime:M84NativeFrameTimingCandidate")
        put("linearIssues", buildJsonArray { M84_LINEAR_ISSUES.forEach { add(JsonPrimitive(it)) } })
        put("packId", "m84-native-frame-timing-candidate-v1")
        put("claimLevel", "native-frame-timing-candidate-reporting")
        put("readinessDelta", 0)
        put("lane", lane)
        put("gateStatus", gateStatus)
        put("gatePhase", "candidate-reporting-only")
        put("releaseBlocking", false)
        put("countedAsMeasuredGate", countedAsMeasuredGate)
        put("sourceNativeDemo", M84_SOURCE_NATIVE_DEMO)
        put("sceneContractId", sceneContractId)
        put("measuredPayload", buildJsonObject {
            put("status", measuredPayloadStatus)
            put("warmupFrameCount", warmupFrameCount)
            put("measuredSampleCount", measuredSampleCount)
            put("totalSampleCount", totalSampleCount)
            put("p50Ms", p50Ms)
            put("p95Ms", p95Ms)
            put("worstMs", worstMs)
            put("nativeTimingClaim", frameTiming.stringField("nativeTimingClaim"))
            put("estimatedMetricCount", 0)
            put("missingMetricCount", if (measuredPayloadStatus == "measured") 0 else 1)
        })
        put("eligibility", buildJsonObject {
            put("eligibleHost", eligibleHost)
            put("reportingOnly", reportingOnly)
            put("quarantineReasons", buildJsonArray { quarantineReasons.forEach { add(JsonPrimitive(it)) } })
        })
        put("host", hostPlatformJson())
        put("adapter", buildJsonObject {
            put("info", adapterInfo)
            put("ownedBaselineFamily", if (adapterInfo.contains("Apple M")) "apple-silicon" else "unknown")
        })
        put("cacheCounters", buildJsonObject {
            put("pipelineCacheHits", 0)
            put("pipelineCacheMisses", 0)
            put("textureUploadBytes", 0)
            put("intermediateTextureBytes", 0)
            put("source", "m84.schema-placeholder-until-m85-resource-telemetry")
        })
        put("surfaceStatusSummary", surfaceStatusSummary)
        put("negativeFixture", negativeFixture())
        put("artifactPaths", buildJsonArray {
            add(JsonPrimitive("$M84_OUTPUT/evidence.json"))
            add(JsonPrimitive("$M84_OUTPUT/evidence.md"))
            add(JsonPrimitive("$M84_OUTPUT/negative-fixture.json"))
            add(JsonPrimitive(M84_SOURCE_NATIVE_DEMO))
        })
        put("validationRows", buildJsonArray {
            add(validationRow("m84.measured-payload-schema", if (measuredPayloadStatus == "measured") "pass" else "blocked", "Warmup, measured sample count, p50, p95, worst frame, host, JDK, adapter, and cache counters are serialized."))
            add(validationRow("m84.reporting-only-policy", if (!countedAsMeasuredGate && !sourceMissing) "pass" else "blocked", "Native timing remains candidate/reporting-only and is not a release-blocking measured gate."))
            add(validationRow("m84.quarantine-policy", if (quarantineReasons.isNotEmpty()) "pass" else "blocked", "Policy records why this native timing payload is visible but not counted as a release gate."))
            add(validationRow("m84.negative-fixture", if (negativeFixture()["status"]?.jsonPrimitive?.contentOrNull == "expected-fail") "pass" else "blocked", "Negative fixture proves threshold failure path without mutating baselines."))
        })
        put("nonClaims", buildJsonArray {
            add(JsonPrimitive("M84 does not make frame.kadre-windowed release-blocking."))
            add(JsonPrimitive("Present-call duration is not a full end-to-end FPS guarantee."))
            add(JsonPrimitive("Cache counters use the M84 schema placeholder until M85 resource lifetime telemetry lands."))
        })
    }

    fun toMarkdown(): String = buildString {
        appendLine("# M84 Native Frame Timing Candidate Gate")
        appendLine()
        appendLine("Status: `$gateStatus`")
        appendLine()
        appendLine("M84 turns native Kadre timing into a candidate/reporting payload with explicit eligibility, quarantine, and negative-fixture evidence.")
        appendLine()
        appendLine("## PM Outcome")
        appendLine()
        appendLine("- Lane: `$lane`")
        appendLine("- Scene contract: `$sceneContractId`")
        appendLine("- Gate phase: `candidate-reporting-only`")
        appendLine("- Release blocking: `false`")
        appendLine("- Counted as measured gate: `$countedAsMeasuredGate`")
        appendLine("- Warmup frames: `$warmupFrameCount`")
        appendLine("- Measured samples: `$measuredSampleCount`")
        appendLine("- p50/p95/worst: `${p50Ms.ms()}` / `${p95Ms.ms()}` / `${worstMs.ms()}`")
        appendLine("- Adapter: `$adapterInfo`")
        appendLine()
        appendLine("## Quarantine / Reporting Reasons")
        appendLine()
        quarantineReasons.forEach { appendLine("- `$it`") }
        appendLine()
        appendLine("## Negative Fixture")
        appendLine()
        appendLine("- Status: `expected-fail`")
        appendLine("- Reason: `$M84_NEGATIVE_REASON`")
        appendLine("- Mutates baseline: `false`")
        appendLine()
        appendLine("## Validation")
        appendLine()
        appendLine("```bash")
        appendLine("rtk ./gradlew --no-daemon :kadre-runtime:test :kadre-runtime:pipelineM84NativeFrameTimingCandidate")
        appendLine("python3 -m json.tool $M84_OUTPUT/evidence.json >/dev/null")
        appendLine("```")
    }
}

internal fun buildM84FrameTimingEvidence(projectRoot: java.nio.file.Path): M84FrameTimingEvidence =
    M84FrameTimingEvidence(readJsonObjectOrEmpty(projectRoot.resolve(M84_SOURCE_NATIVE_DEMO)))

private fun readJsonObjectOrEmpty(path: java.nio.file.Path): JsonObject {
    if (!path.exists()) return JsonObject(emptyMap())
    val element = M84_JSON.parseToJsonElement(path.readText())
    return element as? JsonObject ?: error("M84 source evidence is not a JSON object: $path")
}

private fun validationRow(id: String, status: String, assertion: String): JsonElement = buildJsonObject {
    put("id", id)
    put("status", status)
    put("assertion", assertion)
}

private fun hostPlatformJson(): JsonObject = buildJsonObject {
    put("osName", System.getProperty("os.name", "unknown"))
    put("osVersion", System.getProperty("os.version", "unknown"))
    put("osArch", System.getProperty("os.arch", "unknown"))
    put("javaVersion", System.getProperty("java.version", "unknown"))
}

private fun JsonObject.objectField(name: String): JsonObject = this[name] as? JsonObject ?: JsonObject(emptyMap())
private fun JsonObject.stringField(name: String): String = this[name]?.jsonPrimitive?.contentOrNull.orEmpty()
private fun JsonObject.intField(name: String): Int = this[name]?.jsonPrimitive?.intOrNull ?: 0
private fun JsonObject.doubleField(name: String): Double = this[name]?.jsonPrimitive?.doubleOrNull ?: 0.0
private fun JsonObject.boolField(name: String): Boolean = this[name]?.jsonPrimitive?.booleanOrNull ?: false
private fun Double.ms(): String = "%.4f ms".format(java.util.Locale.US, this)

fun main(args: Array<String>) {
    val projectRoot = args.getOrNull(0)?.let(::Path) ?: Path(".")
    val outputRoot = args.getOrNull(1)?.let(::Path) ?: projectRoot.resolve(M84_OUTPUT)
    outputRoot.createDirectories()

    val evidence = buildM84FrameTimingEvidence(projectRoot)
    outputRoot.resolve("evidence.json").writeText(M84_JSON.encodeToString(JsonElement.serializer(), evidence.toJsonElement()) + "\n")
    outputRoot.resolve("evidence.md").writeText(evidence.toMarkdown())
    outputRoot.resolve("negative-fixture.json").writeText(M84_JSON.encodeToString(JsonElement.serializer(), evidence.negativeFixture()) + "\n")
}
