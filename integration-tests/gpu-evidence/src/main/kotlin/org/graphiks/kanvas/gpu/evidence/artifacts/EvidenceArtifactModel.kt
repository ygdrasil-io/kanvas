package org.graphiks.kanvas.gpu.evidence.artifacts

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.graphiks.kanvas.gpu.evidence.catalog.EvidenceAdapter

const val GPU_EVIDENCE_SCHEMA = "gpu-evidence-v1"
const val GPU_EVIDENCE_CATALOG_SCHEMA_V2 = "gpu-evidence-catalog-v2"
const val GPU_EVIDENCE_SCENE_SCHEMA_V2 = "gpu-evidence-scene-v2"
const val GPU_EVIDENCE_PROMOTION_SCHEMA_V2 = "gpu-evidence-promotion-v2"

data class EvidenceManifest(
    val schemaVersion: String,
    val sceneId: String,
    val expectation: String,
    val observedOutcome: String,
    val sourceCommit: String,
    val generatedAtUtc: String,
    val oracleKind: String,
    val oracleId: String,
    val oracleVersion: Int,
    val files: Map<String, String>,
    val oracleProvenance: String = "generated",
    val oracleSha256: String? = null,
)

data class EvidenceManifestV2(
    val schemaVersion: String,
    val sceneId: String,
    val expectation: String,
    val observedOutcome: String,
    val oracleKind: String,
    val oracleId: String,
    val oracleVersion: Int,
    val files: Map<String, String>,
    val oracleProvenance: String = "generated",
    val oracleSha256: String? = null,
)

data class EvidenceStats(
    val width: Int,
    val height: Int,
    val colorFormat: String,
    val colorInterpretation: String,
    val tolerance: Int,
    val minimumSimilarityPercent: Double,
    val similarityPercent: Double,
    val differingPixels: Int,
    val maxChannelDifference: Int,
    val meanChannelDifference: Double,
    val pass: Boolean,
)

data class EvidenceVerdictRecord(
    val expectation: String,
    val observedOutcome: String,
    val verdictKind: String,
    val reason: String,
)

data class EvidenceCatalogEntry(
    val sceneId: String,
    val sourceCommit: String,
    val manifest: String,
    val manifestSha256: String,
)

data class EvidenceCatalogV2(
    val schemaVersion: String,
    val environment: String,
    val promotion: String?,
    val scenes: List<EvidenceCatalogEntry>,
)

data class EvidenceEnvironmentV2(
    val schemaVersion: String,
    val osName: String,
    val osVersion: String,
    val osArchitecture: String,
    val javaVersion: String,
    val deviceGeneration: Long?,
    val capabilityImplementation: String?,
    val available: Boolean,
    val adapter: EvidenceAdapter?,
)

data class EvidencePromotionV2(
    val schemaVersion: String,
    val promotedAtUtc: String,
    val reviewer: String,
    val reason: String,
    val rebaseline: Boolean,
    val sceneIds: List<String>,
    val priorComparison: String?,
    val newComparison: String?,
)

internal val EvidenceJson = Json { prettyPrint = false; explicitNulls = true; ignoreUnknownKeys = false }

internal fun EvidenceManifest.toJson(): JsonObject = buildJsonObject {
    put("schemaVersion", schemaVersion); put("sceneId", sceneId); put("expectation", expectation)
    put("observedOutcome", observedOutcome); put("sourceCommit", sourceCommit); put("generatedAtUtc", generatedAtUtc)
    put("oracleKind", oracleKind); put("oracleId", oracleId); put("oracleVersion", oracleVersion); put("oracleProvenance", oracleProvenance); put("oracleSha256", oracleSha256)
    put("files", buildJsonObject { files.toSortedMap().forEach { (name, hash) -> put(name, hash) } })
}

internal fun EvidenceManifestV2.toJson(): JsonObject = buildJsonObject {
    put("schemaVersion", schemaVersion); put("sceneId", sceneId); put("expectation", expectation)
    put("observedOutcome", observedOutcome); put("oracleKind", oracleKind); put("oracleId", oracleId)
    put("oracleVersion", oracleVersion); put("oracleProvenance", oracleProvenance); put("oracleSha256", oracleSha256)
    put("files", buildJsonObject { files.toSortedMap().forEach { (name, hash) -> put(name, hash) } })
}

internal fun EvidenceStats.toJson(): JsonObject = buildJsonObject {
    put("width", width); put("height", height); put("colorFormat", colorFormat)
    put("colorInterpretation", colorInterpretation); put("tolerance", tolerance)
    put("minimumSimilarityPercent", minimumSimilarityPercent); put("similarityPercent", similarityPercent)
    put("differingPixels", differingPixels); put("maxChannelDifference", maxChannelDifference)
    put("meanChannelDifference", meanChannelDifference); put("pass", pass)
}

internal fun EvidenceVerdictRecord.toJson(): JsonObject = buildJsonObject {
    put("expectation", expectation); put("observedOutcome", observedOutcome)
    put("verdictKind", verdictKind); put("reason", reason)
}

internal fun EvidenceCatalogEntry.toJson(): JsonObject = buildJsonObject {
    put("sceneId", sceneId); put("sourceCommit", sourceCommit); put("manifest", manifest); put("manifestSha256", manifestSha256)
}

internal fun EvidenceCatalogV2.toJson(): JsonObject = buildJsonObject {
    put("schemaVersion", schemaVersion); put("environment", environment); put("promotion", promotion)
    put("scenes", JsonArray(scenes.sortedBy(EvidenceCatalogEntry::sceneId).map { it.toJson() }))
}

internal fun EvidenceEnvironmentV2.toJson(): JsonObject = buildJsonObject {
    put("schemaVersion", schemaVersion); put("osName", osName); put("osVersion", osVersion)
    put("osArchitecture", osArchitecture); put("javaVersion", javaVersion); put("deviceGeneration", deviceGeneration)
    put("capabilityImplementation", capabilityImplementation); put("available", available)
    put("adapter", adapter?.toJson() ?: JsonNull)
}

internal fun EvidencePromotionV2.toJson(): JsonObject = buildJsonObject {
    put("schemaVersion", schemaVersion); put("promotedAtUtc", promotedAtUtc); put("reviewer", reviewer)
    put("reason", reason); put("rebaseline", rebaseline)
    put("sceneIds", buildJsonArray { sceneIds.sorted().forEach { add(JsonPrimitive(it)) } })
    put("priorComparison", priorComparison); put("newComparison", newComparison)
}

private fun EvidenceAdapter.toJson(): JsonObject = buildJsonObject {
    put("summary", summary); put("vendor", vendor); put("device", device)
    put("architecture", architecture); put("description", description); put("isFallbackAdapter", isFallbackAdapter)
}

internal fun JsonObject.canonicalBytes(): ByteArray = EvidenceJson.encodeToString(JsonObject.serializer(), this).toByteArray(Charsets.UTF_8)
