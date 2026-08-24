package org.graphiks.kanvas.gpu.evidence.artifacts

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

const val GPU_EVIDENCE_SCHEMA = "gpu-evidence-v1"

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

internal val EvidenceJson = Json { prettyPrint = false; explicitNulls = true; ignoreUnknownKeys = false }

internal fun EvidenceManifest.toJson(): JsonObject = buildJsonObject {
    put("schemaVersion", schemaVersion); put("sceneId", sceneId); put("expectation", expectation)
    put("observedOutcome", observedOutcome); put("sourceCommit", sourceCommit); put("generatedAtUtc", generatedAtUtc)
    put("oracleKind", oracleKind); put("oracleId", oracleId); put("oracleVersion", oracleVersion); put("oracleProvenance", oracleProvenance); put("oracleSha256", oracleSha256)
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

internal fun JsonObject.canonicalBytes(): ByteArray = EvidenceJson.encodeToString(JsonObject.serializer(), this).toByteArray(Charsets.UTF_8)
