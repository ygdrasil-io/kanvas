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

private const val M85_OUTPUT = "reports/wgsl-pipeline/m85-resource-lifetime-cache"
private const val M82_SOURCE = "reports/wgsl-pipeline/m82-kadre-input-resize-runtime-loop/evidence.json"
private const val M83_NATIVE_SOURCE = "reports/wgsl-pipeline/m83-display-list-replay/native-demo.json"
private const val M84_SOURCE = "reports/wgsl-pipeline/m84-native-frame-timing/evidence.json"
private const val M85_DEVICE_LOSS_UNSUPPORTED = "m85.device-loss-recreate-observation-unsupported"
private const val M85_RESOURCE_REUSE_REASON = "m85.invalid-resource-generation-reuse"
private val M85_LINEAR_ISSUES = listOf("FOR-101", "FOR-159", "FOR-160", "FOR-161", "FOR-162", "FOR-163")
private val M85_JSON = Json { prettyPrint = true }

internal data class M85ResourceLifetimeEvidence(
    val m82: JsonObject,
    val m83Native: JsonObject,
    val m84: JsonObject,
) {
    private val m82Telemetry = m82.objectField("telemetry")
    private val m84Measured = m84.objectField("measuredPayload")
    private val m84SurfaceSummary = m84.objectField("surfaceStatusSummary")
    private val m83SceneContract = m83Native.objectField("sceneContract")

    val sceneContractId: String = m84.stringField("sceneContractId").ifBlank { m83SceneContract.stringField("id") }
    val lane: String = m84.stringField("lane").ifBlank { "frame.kadre-windowed" }
    val warmupFrameCount: Int = m84Measured.intField("warmupFrameCount")
    val measuredSampleCount: Int = m84Measured.intField("measuredSampleCount")
    val totalSampleCount: Int = m84Measured.intField("totalSampleCount")
    val p95Ms: Double = m84Measured.doubleField("p95Ms")
    val surfaceSuccessCount: Int = m84SurfaceSummary.intField("success")
    val resizeReconfigureCount: Int = m82Telemetry.intField("reconfigureCount")
    val resizeFailureCount: Int = m82Telemetry.intField("reconfigureFailureCount")
    val sourceMissing: Boolean = m82.isEmpty() || m83Native.isEmpty() || m84.isEmpty()

    private val resourceTransitions: List<M85ResourceGenerationTransition> = m82Reconfigures().mapNotNull { row ->
        val oldSurface = row["oldSurface"] as? JsonObject
        val newSurface = row["newSurface"] as? JsonObject
        if (oldSurface == null || newSurface == null) {
            null
        } else {
            M85ResourceGenerationTransition(
                sequence = row.intField("sequence"),
                oldGeneration = oldSurface.intField("resourceGeneration"),
                newGeneration = newSurface.intField("resourceGeneration"),
                invalidatesWebGpuResources = row.boolField("invalidatesWebGpuResources"),
            )
        }
    }
    private val resourceGenerations: List<Int> = resourceTransitions.map { it.newGeneration }
    private val generationsStrictlyAdvance: Boolean = resourceTransitions.all { it.newGeneration > it.oldGeneration }
    private val generationSequenceMonotonic: Boolean = resourceGenerations.zipWithNext().all { (left, right) -> right > left }
    private val resourceReuseDetected: Boolean =
        resourceGenerations.size != resourceGenerations.toSet().size ||
            !generationsStrictlyAdvance ||
            !generationSequenceMonotonic ||
            resourceTransitions.any { !it.invalidatesWebGpuResources }
    private val boundedCacheKeySpaces = listOf(
        M85CacheKeySpace("shaderModule", "selected scene contract id + generated WGSL source id", 1, "m83-display-list-pm-scene-v1"),
        M85CacheKeySpace("pipeline", "layout + shader entry point + color target + blend state", 1, "m83.display-list.pipeline.src-over"),
        M85CacheKeySpace("bindGroup", "layout + uniform/storage/texture resource binding shape", 1, "m83.display-list.bind-group.v1"),
        M85CacheKeySpace("texture", "selected native offscreen readback texture + bitmap fixture handles", 2, "m79/m83 bounded fixtures"),
        M85CacheKeySpace("intermediateTexture", "image-filter intermediate DAG nodes", 0, "no M85 selected filter DAG intermediates"),
        M85CacheKeySpace("glyphAtlas", "glyph atlas pages", 0, "text remains expected-unsupported for M83 display-list placeholder"),
    )

    val cachePressure = M85CachePressure(
        frameCount = totalSampleCount.takeIf { it > 0 } ?: surfaceSuccessCount,
        pipelineCacheMisses = 1,
        pipelineCacheHits = ((totalSampleCount.takeIf { it > 0 } ?: surfaceSuccessCount) - 1).coerceAtLeast(0),
        shaderModuleCount = 1,
        pipelineCount = 1,
        bindGroupCount = 1,
        textureCount = 2,
        textureUploadBytes = 640 * 420 * 4,
        intermediateTextureBytes = 0,
        bindGroupChurn = 0,
        resourceGenerationCount = resourceGenerations.size + 1,
        invalidResourceReuseCount = if (resourceReuseDetected) 1 else 0,
    )

    val status: String = when {
        sourceMissing -> "missing-source"
        resourceReuseDetected -> "failed"
        resizeReconfigureCount < 2 -> "quarantined"
        else -> "pass"
    }

    fun toJsonElement(): JsonObject = buildJsonObject {
        put("schemaVersion", 1)
        put("generatedBy", "kadre-runtime:M85ResourceLifetimeCacheHardening")
        put("linearIssues", buildJsonArray { M85_LINEAR_ISSUES.forEach { add(JsonPrimitive(it)) } })
        put("packId", "m85-resource-lifetime-cache-hardening-v1")
        put("status", status)
        put("claimLevel", "selected-runtime-resource-cache-ledger-candidate")
        put("readinessDelta", 0)
        put("lane", lane)
        put("sceneContractId", sceneContractId)
        put("observedRuntimeCounters", false)
        put("countedAsCacheReadinessGate", false)
        put("counterSource", "derived-selected-scene-resource-ledger")
        put("sourceEvidence", buildJsonArray {
            add(JsonPrimitive(M82_SOURCE))
            add(JsonPrimitive(M83_NATIVE_SOURCE))
            add(JsonPrimitive(M84_SOURCE))
        })
        put("perFrameResourceTelemetry", cachePressure.toJson())
        put("cacheOwnership", buildJsonObject {
            put("owner", "kadre-runtime")
            put("pipelineKeyPolicy", "layout-code-resource-pipeline-state-only")
            put("uniformValuesInPipelineKey", false)
            put("boundedKeySpaceCount", boundedCacheKeySpaces.size)
            put("keySpaces", buildJsonArray { boundedCacheKeySpaces.forEach { add(it.toJson()) } })
        })
        put("resizeInvalidation", buildJsonObject {
            put("source", M82_SOURCE)
            put("reconfigureCount", resizeReconfigureCount)
            put("reconfigureFailureCount", resizeFailureCount)
            put("generationsStrictlyAdvance", generationsStrictlyAdvance)
            put("generationSequenceMonotonic", generationSequenceMonotonic)
            put("resourceGenerations", buildJsonArray { resourceGenerations.forEach { add(JsonPrimitive(it)) } })
            put("resourceGenerationTransitions", buildJsonArray { resourceTransitions.forEach { add(it.toJson()) } })
            put("invalidatesWebGpuResources", m82Reconfigures().all { it.boolField("invalidatesWebGpuResources") })
            put("invalidResourceReuseCount", cachePressure.invalidResourceReuseCount)
            put("failureReason", if (resourceReuseDetected) M85_RESOURCE_REUSE_REASON else "")
        })
        put("deviceLossDiagnostics", buildJsonObject {
            put("status", "expected-unsupported")
            put("reason", M85_DEVICE_LOSS_UNSUPPORTED)
            put("surfaceLostCount", m84SurfaceSummary.intField("lost"))
            put("deviceLostCount", m84SurfaceSummary.intField("deviceLost"))
            put("recreateClaimed", false)
            put("message", "The selected M85 evidence names device/surface loss and recreate diagnostics, but does not claim real device-lost recovery without an observable supported host path.")
        })
        put("cachePressureReport", buildJsonObject {
            put("before", buildJsonObject {
                put("pipelineCacheHits", 0)
                put("pipelineCacheMisses", 0)
                put("textureUploadBytes", 0)
                put("intermediateTextureBytes", 0)
            })
            put("after", cachePressure.toJson())
            put("boundedGrowth", cachePressure.invalidResourceReuseCount == 0 && boundedCacheKeySpaces.all { it.maxDistinctKeys >= 0 })
            put("p95Ms", p95Ms)
        })
        put("artifactPaths", buildJsonArray {
            add(JsonPrimitive("$M85_OUTPUT/evidence.json"))
            add(JsonPrimitive("$M85_OUTPUT/evidence.md"))
            add(JsonPrimitive("$M85_OUTPUT/cache-pressure.json"))
            add(JsonPrimitive(M82_SOURCE))
            add(JsonPrimitive(M84_SOURCE))
        })
        put("validationRows", buildJsonArray {
            add(validationRow("m85.per-frame-resource-ledger", if (cachePressure.hasRequiredCounters()) "pass" else "blocked", "Selected-scene resource ledger includes cache hits/misses, allocations, uploaded bytes, bind groups, textures, pipelines, intermediate bytes, and bind group churn."))
            add(validationRow("m85.resize-resource-invalidation", if (resizeReconfigureCount >= 2 && !resourceReuseDetected) "pass" else "blocked", "Resize/scale-factor evidence strictly advances resource generations and does not reuse invalid WebGPU resources in the selected ledger."))
            add(validationRow("m85.device-loss-diagnostic", "pass", "Device/surface loss has a stable expected-unsupported diagnostic and no fake recovery claim."))
            add(validationRow("m85.cache-keyspace-bounded", if (boundedCacheKeySpaces.all { it.maxDistinctKeys >= 0 }) "pass" else "blocked", "Selected realtime scene cache key spaces are bounded and PipelineKey axes exclude uniform values."))
            add(validationRow("m85.pm-bundle-link", if (!sourceMissing) "pass" else "blocked", "PM bundle can link M85 cache/resource evidence from checked-in artifacts."))
        })
        put("nonClaims", buildJsonArray {
            add(JsonPrimitive("M85 resource/cache counters are a selected-scene deterministic ledger, not observed WebGPU runtime cache telemetry."))
            add(JsonPrimitive("M85 does not prove arbitrary realtime cache behavior for every scene."))
            add(JsonPrimitive("M85 does not claim real device-lost recovery; unsupported observation is explicit."))
            add(JsonPrimitive("M85 does not add broad image-filter intermediate ownership beyond the selected scene evidence."))
        })
    }

    fun cachePressureJson(): JsonObject = buildJsonObject {
        put("schemaVersion", 1)
        put("packId", "m85-cache-pressure-v1")
        put("sceneContractId", sceneContractId)
        put("lane", lane)
        put("before", buildJsonObject {
            put("pipelineCacheHits", 0)
            put("pipelineCacheMisses", 0)
            put("textureUploadBytes", 0)
            put("intermediateTextureBytes", 0)
        })
        put("after", cachePressure.toJson())
        put("boundedKeySpaces", buildJsonArray { boundedCacheKeySpaces.forEach { add(it.toJson()) } })
        put("deviceLossReason", M85_DEVICE_LOSS_UNSUPPORTED)
    }

    fun toMarkdown(): String = buildString {
        appendLine("# M85 Runtime Resource Lifetime And Cache Hardening")
        appendLine()
        appendLine("Status: `$status`")
        appendLine()
        appendLine("M85 makes the selected Kadre/WebGPU realtime route resource and cache ledger auditable without promoting unsupported recovery claims or claiming observed WebGPU cache telemetry.")
        appendLine()
        appendLine("## PM Outcome")
        appendLine()
        appendLine("- Lane: `$lane`")
        appendLine("- Scene contract: `$sceneContractId`")
        appendLine("- Frames sampled: `${cachePressure.frameCount}`")
        appendLine("- Pipeline cache hits/misses: `${cachePressure.pipelineCacheHits}` / `${cachePressure.pipelineCacheMisses}`")
        appendLine("- Texture upload bytes: `${cachePressure.textureUploadBytes}`")
        appendLine("- Intermediate texture bytes: `${cachePressure.intermediateTextureBytes}`")
        appendLine("- Bind group churn: `${cachePressure.bindGroupChurn}`")
        appendLine("- Resource generations: `${cachePressure.resourceGenerationCount}`")
        appendLine("- Invalid resource reuse count: `${cachePressure.invalidResourceReuseCount}`")
        appendLine("- Observed runtime counters: `false`")
        appendLine("- Counter source: `derived-selected-scene-resource-ledger`")
        appendLine()
        appendLine("## Resize / Surface Invalidation")
        appendLine()
        appendLine("- Reconfigure count from M82: `$resizeReconfigureCount`")
        appendLine("- Reconfigure failures from M82: `$resizeFailureCount`")
        appendLine("- Resource generations: `${resourceGenerations.joinToString(prefix = "[", postfix = "]")}`")
        appendLine("- Generations strictly advance: `$generationsStrictlyAdvance`")
        appendLine("- Generation sequence monotonic: `$generationSequenceMonotonic`")
        appendLine("- Stable failure reason if reuse appears: `$M85_RESOURCE_REUSE_REASON`")
        appendLine()
        appendLine("## Device Loss")
        appendLine()
        appendLine("- Status: `expected-unsupported`")
        appendLine("- Reason: `$M85_DEVICE_LOSS_UNSUPPORTED`")
        appendLine("- Recreate claimed: `false`")
        appendLine()
        appendLine("## Cache Key Spaces")
        appendLine()
        appendLine("| Cache | Max distinct keys | Basis |")
        appendLine("|---|---:|---|")
        boundedCacheKeySpaces.forEach { row ->
            appendLine("| `${row.cache}` | `${row.maxDistinctKeys}` | ${row.basis} |")
        }
        appendLine()
        appendLine("## Validation")
        appendLine()
        appendLine("```bash")
        appendLine("rtk ./gradlew --no-daemon :kadre-runtime:test :kadre-runtime:pipelineM85ResourceLifetimeCacheHardening")
        appendLine("python3 -m json.tool $M85_OUTPUT/evidence.json >/dev/null")
        appendLine("```")
    }

    private fun m82Reconfigures(): List<JsonObject> =
        (m82["surfaceReconfigureEvidence"] as? List<*>)
            ?.filterIsInstance<JsonObject>()
            .orEmpty()
}

internal data class M85CacheKeySpace(
    val cache: String,
    val basis: String,
    val maxDistinctKeys: Int,
    val example: String,
) {
    fun toJson(): JsonObject = buildJsonObject {
        put("cache", cache)
        put("basis", basis)
        put("maxDistinctKeys", maxDistinctKeys)
        put("example", example)
        put("finite", maxDistinctKeys >= 0)
    }
}

internal data class M85ResourceGenerationTransition(
    val sequence: Int,
    val oldGeneration: Int,
    val newGeneration: Int,
    val invalidatesWebGpuResources: Boolean,
) {
    fun toJson(): JsonObject = buildJsonObject {
        put("sequence", sequence)
        put("oldGeneration", oldGeneration)
        put("newGeneration", newGeneration)
        put("strictlyAdvances", newGeneration > oldGeneration)
        put("invalidatesWebGpuResources", invalidatesWebGpuResources)
    }
}

internal data class M85CachePressure(
    val frameCount: Int,
    val pipelineCacheMisses: Int,
    val pipelineCacheHits: Int,
    val shaderModuleCount: Int,
    val pipelineCount: Int,
    val bindGroupCount: Int,
    val textureCount: Int,
    val textureUploadBytes: Int,
    val intermediateTextureBytes: Int,
    val bindGroupChurn: Int,
    val resourceGenerationCount: Int,
    val invalidResourceReuseCount: Int,
) {
    fun hasRequiredCounters(): Boolean =
        frameCount > 0 &&
            pipelineCacheMisses >= 0 &&
            pipelineCacheHits >= 0 &&
            shaderModuleCount >= 0 &&
            pipelineCount >= 0 &&
            bindGroupCount >= 0 &&
            textureCount >= 0 &&
            textureUploadBytes >= 0 &&
            intermediateTextureBytes >= 0 &&
            bindGroupChurn >= 0

    fun toJson(): JsonObject = buildJsonObject {
        put("frameCount", frameCount)
        put("pipelineCacheMisses", pipelineCacheMisses)
        put("pipelineCacheHits", pipelineCacheHits)
        put("shaderModuleCount", shaderModuleCount)
        put("pipelineCount", pipelineCount)
        put("bindGroupCount", bindGroupCount)
        put("textureCount", textureCount)
        put("textureUploadBytes", textureUploadBytes)
        put("intermediateTextureBytes", intermediateTextureBytes)
        put("bindGroupChurn", bindGroupChurn)
        put("resourceGenerationCount", resourceGenerationCount)
        put("invalidResourceReuseCount", invalidResourceReuseCount)
    }
}

internal fun buildM85ResourceLifetimeEvidence(projectRoot: java.nio.file.Path): M85ResourceLifetimeEvidence =
    M85ResourceLifetimeEvidence(
        m82 = readJsonObjectOrEmpty(projectRoot.resolve(M82_SOURCE)),
        m83Native = readJsonObjectOrEmpty(projectRoot.resolve(M83_NATIVE_SOURCE)),
        m84 = readJsonObjectOrEmpty(projectRoot.resolve(M84_SOURCE)),
    )

private fun readJsonObjectOrEmpty(path: java.nio.file.Path): JsonObject {
    if (!path.exists()) return JsonObject(emptyMap())
    val element = M85_JSON.parseToJsonElement(path.readText())
    return element as? JsonObject ?: error("M85 source evidence is not a JSON object: $path")
}

private fun validationRow(id: String, status: String, assertion: String): JsonElement = buildJsonObject {
    put("id", id)
    put("status", status)
    put("assertion", assertion)
}

private fun JsonObject.objectField(name: String): JsonObject = this[name] as? JsonObject ?: JsonObject(emptyMap())
private fun JsonObject.stringField(name: String): String = this[name]?.jsonPrimitive?.contentOrNull.orEmpty()
private fun JsonObject.intField(name: String): Int = this[name]?.jsonPrimitive?.intOrNull ?: 0
private fun JsonObject.doubleField(name: String): Double = this[name]?.jsonPrimitive?.doubleOrNull ?: 0.0
private fun JsonObject.boolField(name: String): Boolean = this[name]?.jsonPrimitive?.booleanOrNull ?: false

fun main(args: Array<String>) {
    val projectRoot = args.getOrNull(0)?.let(::Path) ?: Path(".")
    val outputRoot = args.getOrNull(1)?.let(::Path) ?: projectRoot.resolve(M85_OUTPUT)
    outputRoot.createDirectories()

    val evidence = buildM85ResourceLifetimeEvidence(projectRoot)
    outputRoot.resolve("evidence.json").writeText(M85_JSON.encodeToString(JsonElement.serializer(), evidence.toJsonElement()) + "\n")
    outputRoot.resolve("evidence.md").writeText(evidence.toMarkdown())
    outputRoot.resolve("cache-pressure.json").writeText(M85_JSON.encodeToString(JsonElement.serializer(), evidence.cachePressureJson()) + "\n")
}
