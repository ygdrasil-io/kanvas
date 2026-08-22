package org.graphiks.kanvas.gpu.evidence.artifacts

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.graphiks.kanvas.gpu.evidence.gate.EvidenceVerdict

sealed interface EvidenceBundleVerification {
    data class Verified(val sceneId: String, val verdict: EvidenceVerdict) : EvidenceBundleVerification
    data class Invalid(val sceneId: String?, val errors: List<String>) : EvidenceBundleVerification
}

object EvidenceBundleVerifier {
    fun verify(directory: Path, expectedSourceCommit: String): EvidenceBundleVerification {
        var sceneId: String? = directory.fileName?.toString()
        val errors = mutableListOf<String>()
        try {
            require(Files.isDirectory(directory)) { "bundle is not a directory" }
            val manifest = readObject(directory.resolve("manifest.json"), "manifest")
            manifest.requireKeys(setOf("schemaVersion", "sceneId", "expectation", "observedOutcome", "sourceCommit", "generatedAtUtc", "oracleKind", "oracleId", "oracleVersion", "files"))
            val schema = manifest.requiredString("schemaVersion")
            require(schema == GPU_EVIDENCE_SCHEMA) { "unsupported schemaVersion: $schema" }
            sceneId = manifest.requiredString("sceneId")
            require(sceneId == directory.fileName.toString()) { "manifest sceneId does not match directory" }
            val sourceCommit = manifest.requiredString("sourceCommit")
            require(sourceCommit == expectedSourceCommit) { "sourceCommit mismatch" }
            val observed = manifest.requiredString("observedOutcome")
            val expectation = manifest.requiredString("expectation")
            manifest.requiredString("generatedAtUtc")
            manifest.requiredString("oracleKind"); manifest.requiredString("oracleId"); manifest.requiredInt("oracleVersion")
            val fileObject = manifest.requiredObject("files")
            val hashes = fileObject.entries.associate { (name, value) ->
                require(isSafeFileName(name)) { "unsafe logical file name: $name" }
                name to value.jsonPrimitive.asString("hash")
            }
            val actual = Files.list(directory).use { stream -> stream.iterator().asSequence().map { it.fileName.toString() }.toSet() }
            val expected = if (observed == "rendered") RENDER_FILES else if (observed == "refused") REFUSAL_FILES else error("unknown observedOutcome")
            require(actual == expected) { "file set mismatch: expected=$expected actual=$actual" }
            require(hashes.keys == expected - "manifest.json") { "manifest file hashes are incomplete" }
            hashes.forEach { (name, expectedHash) -> require(expectedHash == sha256(Files.readAllBytes(directory.resolve(name)))) { "hash mismatch for $name" } }
            val environment = readObject(directory.resolve("environment.json"), "environment")
            environment.requireKeys(setOf("sourceCommit", "osName", "osVersion", "osArchitecture", "javaVersion", "deviceGeneration", "capabilityImplementation", "available", "adapter"))
            require(manifest.requiredString("sourceCommit") == environment.requiredString("sourceCommit")) { "environment sourceCommit mismatch" }
            environment.requiredString("osName"); environment.requiredString("osVersion"); environment.requiredString("osArchitecture"); environment.requiredString("javaVersion"); environment.optionalNullableLong("deviceGeneration"); environment.optionalNullableString("capabilityImplementation"); environment.requiredBoolean("available")
            val adapter = environment["adapter"]
            if (adapter != null && adapter !is JsonNull) {
                val adapterObject = adapter.jsonObject
                adapterObject.requireKeys(setOf("summary", "vendor", "device", "architecture", "description", "isFallbackAdapter"))
                adapterObject.optionalNullableString("summary"); adapterObject.optionalNullableString("vendor"); adapterObject.optionalNullableString("device"); adapterObject.optionalNullableString("architecture"); adapterObject.optionalNullableString("description"); adapterObject.optionalNullableBoolean("isFallbackAdapter")
            }
            val route = readObject(directory.resolve("route.json"), "route")
            route.requireKeys(setOf("routeId", "attemptId", "furthestPhase", "outcome", "encodedScopeKinds", "structuralEvents", "structuralCounters", "runtimeTelemetryDelta"))
            route.requiredString("routeId"); route.optionalString("attemptId"); route.optionalString("furthestPhase"); route.requiredString("outcome")
            route["encodedScopeKinds"]?.jsonArray?.forEach { it.jsonPrimitive.asString("encoded scope kind") } ?: error("encodedScopeKinds must be an array")
            route["structuralEvents"]?.jsonArray?.forEach { event -> val e = event.jsonObject; e.requiredString("kind"); e.requiredString("phase"); e.optionalString("label") } ?: error("structuralEvents must be an array")
            route.requiredObject("structuralCounters").forEach { (key, value) -> require(key.isNotBlank()); value.jsonPrimitive.longOrNull ?: error("structural counter must be a long") }
            val telemetry = route.requiredObject("runtimeTelemetryDelta")
            TELEMETRY_FIELDS.forEach { telemetry.requiredLong(it) }
            val stats = readObject(directory.resolve("stats.json"), "stats")
            stats.requireKeys(setOf("width", "height", "colorFormat", "colorInterpretation", "tolerance", "minimumSimilarityPercent", "similarityPercent", "differingPixels", "maxChannelDifference", "meanChannelDifference", "pass"))
            stats.requiredInt("width"); stats.requiredInt("height"); require(stats.requiredString("colorFormat") == "rgba8unorm") { "invalid colorFormat" }; require(stats.requiredString("colorInterpretation") == "encoded-premul-srgb") { "invalid colorInterpretation" }; stats.requiredInt("tolerance"); stats.requiredDouble("minimumSimilarityPercent"); stats.requiredDouble("similarityPercent"); stats.requiredInt("differingPixels"); stats.requiredInt("maxChannelDifference"); stats.requiredDouble("meanChannelDifference")
            val pass = stats.requiredBoolean("pass")
            val diagnostics = readObject(directory.resolve("diagnostics.json"), "diagnostics")
            diagnostics.requireKeys(setOf("attemptId", "diagnostics", "stableReasonCode", "message", "submissionDelta"))
            diagnostics.requiredString("attemptId"); diagnostics["diagnostics"]?.jsonArray?.forEach { it.jsonPrimitive.asString("diagnostic") } ?: error("diagnostics must be an array"); diagnostics.optionalNullableString("stableReasonCode"); diagnostics.optionalNullableString("message")
            val submissionDelta = diagnostics.requiredLong("submissionDelta")
            val reasonCode = diagnostics.optionalString("stableReasonCode")
            val verdictJson = readObject(directory.resolve("verdict.json"), "verdict")
            verdictJson.requireKeys(setOf("expectation", "observedOutcome", "verdictKind", "reason"))
            val recordedKind = verdictJson.requiredString("verdictKind")
            val recordedExpectation = verdictJson.requiredString("expectation")
            val recordedObserved = verdictJson.requiredString("observedOutcome")
            val recordedReason = verdictJson.requiredString("reason")
            require(recordedExpectation == expectation && recordedObserved == observed) { "verdict identity mismatch" }
            val reconstructed = if (expectation == "render") {
                if (observed == "rendered" && pass) EvidenceVerdict.Pass("rendered image passed comparison") else EvidenceVerdict.Fail("rendered image failed comparison")
            } else if (expectation.startsWith("refuse:") && observed == "refused") {
                val expectedReason = expectation.removePrefix("refuse:")
                when {
                    reasonCode != expectedReason -> EvidenceVerdict.Fail("expected refusal $expectedReason, got $reasonCode")
                    submissionDelta != 0L -> EvidenceVerdict.Fail("refusal submitted $submissionDelta command(s)")
                    else -> EvidenceVerdict.Pass("exact refusal before submission")
                }
            } else EvidenceVerdict.Fail("observation does not satisfy expectation")
            require(recordedKind == reconstructed.kind()) { "verdict kind mismatch" }
            require(recordedReason == reconstructed.reason()) { "verdict reason mismatch" }
            return EvidenceBundleVerification.Verified(sceneId, reconstructed)
        } catch (failure: Throwable) {
            errors += failure.message ?: failure::class.simpleName.orEmpty()
            return EvidenceBundleVerification.Invalid(sceneId, errors)
        }
    }

    private fun readObject(path: Path, label: String): JsonObject {
        require(Files.isRegularFile(path)) { "missing $label" }
        val parsed = EvidenceJson.parseToJsonElement(Files.readString(path))
        return parsed.jsonObject
    }

    private fun JsonObject.requiredString(key: String): String {
        val primitive = this[key] as? JsonPrimitive ?: error("$key must be a string")
        require(primitive.isString) { "$key must be a string" }
        return primitive.contentOrNull ?: error("$key must not be null")
    }
    private fun JsonObject.optionalString(key: String): String? {
        val value = this[key] ?: error("missing $key")
        if (value is JsonNull) return null
        return value.jsonPrimitive.asString(key)
    }
    private fun JsonObject.requiredInt(key: String): Int = (this[key] as? JsonPrimitive)?.intOrNull ?: error("$key must be an integer")
    private fun JsonObject.requiredDouble(key: String): Double = (this[key] as? JsonPrimitive)?.doubleOrNull ?: error("$key must be a number")
    private fun JsonObject.requiredLong(key: String): Long = (this[key] as? JsonPrimitive)?.longOrNull ?: error("$key must be a long")
    private fun JsonObject.requiredBoolean(key: String): Boolean = (this[key] as? JsonPrimitive)?.booleanOrNull ?: error("$key must be a boolean")
    private fun JsonObject.requiredObject(key: String): JsonObject = this[key]?.jsonObject ?: error("$key must be an object")
    private fun JsonObject.requireKeys(expected: Set<String>) { require(keys == expected) { "unexpected or missing keys: expected=$expected actual=$keys" } }
    private fun JsonObject.optionalNullableString(key: String): String? {
        val value = this[key] ?: error("missing $key")
        if (value is JsonNull) return null
        return value.jsonPrimitive.asString(key)
    }
    private fun JsonObject.optionalNullableBoolean(key: String): Boolean? {
        val value = this[key] ?: error("missing $key")
        if (value is JsonNull) return null
        return value.jsonPrimitive.booleanOrNull ?: error("$key must be a boolean or null")
    }
    private fun JsonObject.optionalNullableLong(key: String): Long? {
        val value = this[key] ?: error("missing $key")
        if (value is JsonNull) return null
        return value.jsonPrimitive.longOrNull ?: error("$key must be a long or null")
    }
    private fun JsonPrimitive.asString(label: String): String = require(isString && contentOrNull != null) { "$label must be a string" }.let { content }
    private fun isSafeFileName(name: String): Boolean = name.isNotBlank() && !name.startsWith('/') && !name.contains("\\") && !name.split('/').any { it == ".." || it.isBlank() } && name == Path.of(name).fileName.toString()
    private fun sha256(value: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(value).joinToString("") { "%02x".format(it) }
    private fun EvidenceVerdict.kind() = when (this) { is EvidenceVerdict.Pass -> "pass"; is EvidenceVerdict.Fail -> "fail"; is EvidenceVerdict.Unavailable -> "unavailable" }
    private fun EvidenceVerdict.reason() = when (this) { is EvidenceVerdict.Pass -> reason; is EvidenceVerdict.Fail -> reason; is EvidenceVerdict.Unavailable -> reason }
    private val RENDER_FILES = setOf("manifest.json", "gpu.png", "cpu.png", "diff.png", "stats.json", "route.json", "diagnostics.json", "environment.json", "verdict.json")
    private val REFUSAL_FILES = setOf("manifest.json", "stats.json", "route.json", "diagnostics.json", "environment.json", "verdict.json")
    private val TELEMETRY_FIELDS = setOf("renderPasses", "offscreenPasses", "windowPasses", "submissions", "commandBuffers", "buffersCreated", "texturesCreated", "intermediateTexturesCreated", "coverageMasksDestroyed", "destinationCopies", "destinationReadbackSnapshots", "msaaTargets", "msaaResolves", "bindGroupsCreated", "samplersCreated", "queueWrites", "uniformSlabsCreated", "uniformSlabBytesAllocated", "uniformSlabFallbacks", "passBatchPlans", "passBatchesAccepted", "passBatchCuts", "passBatchPackets")
}
