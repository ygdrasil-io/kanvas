package org.graphiks.kanvas.gpu.evidence.artifacts

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.LinkOption.NOFOLLOW_LINKS
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
import org.graphiks.kanvas.gpu.evidence.catalog.EvidenceExpectation
import org.graphiks.kanvas.gpu.evidence.catalog.OraclePolicy
import org.graphiks.kanvas.gpu.evidence.compare.EvidenceComparator
import org.graphiks.kanvas.test.ComparisonUtils

sealed interface EvidenceBundleVerification {
    data class Verified(val sceneId: String, val verdict: EvidenceVerdict) : EvidenceBundleVerification
    data class Invalid(val sceneId: String?, val errors: List<String>) : EvidenceBundleVerification
}

object EvidenceBundleVerifier {
    fun verify(directory: Path, expected: EvidenceVerificationExpectation): EvidenceBundleVerification {
        return verifyInternal(directory, expected.sourceCommit, expected)
    }

    private fun verifyInternal(directory: Path, expectedSourceCommit: String, expected: EvidenceVerificationExpectation): EvidenceBundleVerification {
        var sceneId: String? = directory.fileName?.toString()
        val errors = mutableListOf<String>()
        try {
            require(Files.isDirectory(directory, NOFOLLOW_LINKS)) { "bundle is not a directory" }
            require(!Files.isSymbolicLink(directory)) { "bundle directory cannot be a symlink" }
            val manifest = readObject(directory.resolve("manifest.json"), "manifest")
            manifest.requireKeys(setOf("schemaVersion", "sceneId", "expectation", "observedOutcome", "sourceCommit", "generatedAtUtc", "oracleKind", "oracleId", "oracleVersion", "oracleProvenance", "oracleSha256", "files"))
            val schema = manifest.requiredString("schemaVersion")
            require(schema == GPU_EVIDENCE_SCHEMA) { "unsupported schemaVersion: $schema" }
            sceneId = manifest.requiredString("sceneId")
            require(sceneId == directory.fileName.toString()) { "manifest sceneId does not match directory" }
            val sourceCommit = manifest.requiredString("sourceCommit")
            require(sourceCommit == expectedSourceCommit) { "sourceCommit mismatch" }
            val observed = manifest.requiredString("observedOutcome")
            val expectation = manifest.requiredString("expectation")
            manifest.requiredString("generatedAtUtc")
            val oracleKind = manifest.requiredString("oracleKind")
            manifest.requiredString("oracleId"); manifest.requiredInt("oracleVersion"); manifest.requiredString("oracleProvenance")
            val oracleSha256 = manifest.optionalNullableString("oracleSha256")
            val descriptor = expected.descriptor
            require(sceneId == descriptor.id.value) { "scene id does not match expected case" }
            require(expectation == descriptor.expectation.manifestValue()) { "expectation does not match expected case" }
            require(oracleKind == descriptor.oracle.kind()) { "oracle kind does not match expected case" }
            require(manifest.requiredString("oracleId") == descriptor.oracle.id()) { "oracle id does not match expected case" }
            require(manifest.requiredInt("oracleVersion") == descriptor.oracle.version()) { "oracle version does not match expected case" }
            require(manifest.requiredString("oracleProvenance") == descriptor.oracle.provenance()) { "oracle provenance does not match expected case" }
            require(oracleSha256 == descriptor.oracle.sha256()) { "oracle sha256 does not match expected case" }
            val fileObject = manifest.requiredObject("files")
            val hashes = fileObject.entries.associate { (name, value) ->
                require(isSafeFileName(name)) { "unsafe logical file name: $name" }
                name to value.jsonPrimitive.asString("hash")
            }
            val actualPaths = Files.list(directory).use { stream -> stream.iterator().asSequence().toList() }
            require(actualPaths.none { Files.isSymbolicLink(it) }) { "bundle contains symlink" }
            val actual = actualPaths.map { it.fileName.toString() }.toSet()
            val expectedFiles = if (observed == "rendered") {
                if (oracleKind == "checked-in-png") CHECKED_IN_RENDER_FILES else RENDER_FILES
            } else if (observed == "refused" || observed == "unavailable") REFUSAL_FILES else error("unknown observedOutcome")
            require(actual == expectedFiles || actual == expectedFiles + "promotion.json") { "file set mismatch: expected=$expectedFiles actual=$actual" }
            if ("promotion.json" in actual) verifyPromotion(directory, sceneId, sourceCommit)
            require(hashes.keys == expectedFiles - "manifest.json") { "manifest file hashes are incomplete" }
            hashes.forEach { (name, expectedHash) -> require(expectedHash == sha256(Files.readAllBytes(directory.resolve(name)))) { "hash mismatch for $name" } }
            if (oracleKind == "checked-in-png") {
                require(oracleSha256 != null) { "checked-in oracle must declare sha256" }
                require(oracleSha256 == sha256(Files.readAllBytes(directory.resolve("skia.png")))) { "checked-in oracle sha256 mismatch" }
            } else require(oracleSha256 == null) { "generated oracle must not declare checked-in sha256" }
            val environment = readObject(directory.resolve("environment.json"), "environment")
            environment.requireKeys(setOf("sourceCommit", "osName", "osVersion", "osArchitecture", "javaVersion", "deviceGeneration", "capabilityImplementation", "available", "adapter"))
            require(manifest.requiredString("sourceCommit") == environment.requiredString("sourceCommit")) { "environment sourceCommit mismatch" }
            environment.requiredString("osName"); environment.requiredString("osVersion"); environment.requiredString("osArchitecture"); environment.requiredString("javaVersion"); environment.optionalNullableLong("deviceGeneration"); environment.optionalNullableString("capabilityImplementation"); val environmentAvailable = environment.requiredBoolean("available")
            require(if (observed == "unavailable") !environmentAvailable else environmentAvailable) { "environment availability contradicts observed outcome" }
            val adapter = environment["adapter"]
            if (adapter != null && adapter !is JsonNull) {
                val adapterObject = adapter.jsonObject
                adapterObject.requireKeys(setOf("summary", "vendor", "device", "architecture", "description", "isFallbackAdapter"))
                val adapterSummary = adapterObject.optionalNullableString("summary")
                require(!environmentAvailable || !adapterSummary.isNullOrBlank()) { "available evidence requires a nonblank adapter summary" }
                adapterObject.optionalNullableString("vendor"); adapterObject.optionalNullableString("device"); adapterObject.optionalNullableString("architecture"); adapterObject.optionalNullableString("description"); adapterObject.optionalNullableBoolean("isFallbackAdapter")
            } else {
                require(!environmentAvailable) { "available evidence requires a nonblank adapter summary" }
            }
            val route = readObject(directory.resolve("route.json"), "route")
            route.requireKeys(setOf("routeId", "attemptId", "furthestPhase", "outcome", "encodedScopeKinds", "structuralEvents", "structuralCounters", "runtimeTelemetryDelta"))
            val routeId = route.requiredString("routeId")
            route.optionalString("attemptId")
            val furthestPhase = route.optionalString("furthestPhase")
            route.requiredString("outcome")
            require(routeId == expected.expectedRouteId) { "route id does not match expected case" }
            route["encodedScopeKinds"]?.jsonArray?.forEach { it.jsonPrimitive.asString("encoded scope kind") } ?: error("encodedScopeKinds must be an array")
            route["structuralEvents"]?.jsonArray?.forEach { event -> val e = event.jsonObject; e.requireKeys(setOf("kind", "phase", "label")); e.requiredString("kind"); e.requiredString("phase"); e.optionalString("label") } ?: error("structuralEvents must be an array")
            route.requiredObject("structuralCounters").forEach { (key, value) ->
                require(key.isNotBlank())
                val counter = value.jsonPrimitive.takeUnless { it.isString }?.longOrNull ?: error("structural counter must be a long")
                require(counter >= 0L) { "structural counter must be non-negative" }
            }
            val telemetry = route.requiredObject("runtimeTelemetryDelta")
            telemetry.requireKeys(TELEMETRY_FIELDS)
            TELEMETRY_FIELDS.forEach { require(telemetry.requiredLong(it) >= 0L) { "runtime telemetry counter must be non-negative: $it" } }
            val stats = readObject(directory.resolve("stats.json"), "stats")
            stats.requireKeys(setOf("width", "height", "colorFormat", "colorInterpretation", "tolerance", "minimumSimilarityPercent", "similarityPercent", "differingPixels", "maxChannelDifference", "meanChannelDifference", "pass"))
            val width = stats.requiredInt("width"); val height = stats.requiredInt("height"); require(width > 0 && height > 0) { "invalid dimensions" }; require(stats.requiredString("colorFormat") == "rgba8unorm") { "invalid colorFormat" }; require(stats.requiredString("colorInterpretation") == "encoded-premul-srgb") { "invalid colorInterpretation" }; val tolerance = stats.requiredInt("tolerance"); require(tolerance in 0..255); val minimumSimilarity = stats.requiredDouble("minimumSimilarityPercent"); require(minimumSimilarity in 0.0..100.0); val similarity = stats.requiredDouble("similarityPercent"); require(similarity in 0.0..100.0); val differingPixels = stats.requiredInt("differingPixels"); val totalPixels = Math.multiplyExact(width, height); require(differingPixels in 0..totalPixels); val maxChannelDifference = stats.requiredInt("maxChannelDifference"); require(maxChannelDifference in 0..255); val meanChannelDifference = stats.requiredDouble("meanChannelDifference"); require(meanChannelDifference >= 0.0)
            val pass = stats.requiredBoolean("pass")
            val policy = expected.descriptor.comparison
            require(width == expected.descriptor.width && height == expected.descriptor.height) { "dimensions do not match expected case" }
            require(tolerance == (policy?.perChannelTolerance ?: 0)) { "tolerance does not match expected case" }
            require(minimumSimilarity == (policy?.minimumSimilarityPercent ?: 100.0)) { "minimum similarity does not match expected case" }
            val expectedSimilarity = (totalPixels - differingPixels).toDouble() / totalPixels.toDouble() * 100.0
            require(kotlin.math.abs(expectedSimilarity - similarity) <= 1e-9) { "similarity contradicts differingPixels" }
            require(pass == (similarity >= minimumSimilarity)) { "stats pass contradicts similarity threshold" }
            val diagnostics = readObject(directory.resolve("diagnostics.json"), "diagnostics")
            diagnostics.requireKeys(setOf("attemptId", "diagnostics", "stableReasonCode", "message", "submissionDelta"))
            diagnostics.requiredString("attemptId"); diagnostics["diagnostics"]?.jsonArray?.forEach { it.jsonPrimitive.asString("diagnostic") } ?: error("diagnostics must be an array"); diagnostics.optionalNullableString("stableReasonCode"); diagnostics.optionalNullableString("message")
            val diagnosticAttemptId = diagnostics.requiredString("attemptId")
            val submissionDelta = diagnostics.requiredLong("submissionDelta")
            require(submissionDelta >= 0L) { "submissionDelta must be non-negative" }
            val reasonCode = diagnostics.optionalString("stableReasonCode")
            val routeAttemptId = route.optionalString("attemptId")
            require(routeAttemptId == diagnosticAttemptId) { "route and diagnostics attempt ids differ" }
            val routeOutcome = route.requiredString("outcome")
            require(routeOutcome == observed) { "route outcome does not match observed outcome" }
            val telemetrySubmissions = telemetry.requiredLong("submissions")
            require(telemetrySubmissions == submissionDelta) { "route submissions differ from diagnostics" }
            if (observed == "rendered") {
                require(furthestPhase == "Completed") { "rendered evidence must reach Completed" }
                require((route.requiredObject("structuralCounters")["queue.submit"]?.jsonPrimitive?.longOrNull ?: 0L) > 0L) { "rendered evidence requires queue.submit proof" }
                require(telemetrySubmissions > 0L) { "rendered evidence requires submission telemetry" }
            }
            if (expected.descriptor.expectation is EvidenceExpectation.ShouldRefuse) {
                val reason = (expected.descriptor.expectation as EvidenceExpectation.ShouldRefuse).stableReasonCode
                if (observed == "refused") require(reasonCode == reason) { "refusal reason does not match expected case" }
                require(submissionDelta == 0L && telemetrySubmissions == 0L) { "refusal submitted commands" }
            }
            if (observed == "refused") require(submissionDelta == 0L && telemetrySubmissions == 0L) { "refusal submitted commands" }
            val verdictJson = readObject(directory.resolve("verdict.json"), "verdict")
            verdictJson.requireKeys(setOf("expectation", "observedOutcome", "verdictKind", "reason"))
            val recordedKind = verdictJson.requiredString("verdictKind")
            val recordedExpectation = verdictJson.requiredString("expectation")
            val recordedObserved = verdictJson.requiredString("observedOutcome")
            val recordedReason = verdictJson.requiredString("reason")
            require(recordedExpectation == expectation && recordedObserved == observed) { "verdict identity mismatch" }
            val reconstructed = when {
                expectation == "render" && observed == "rendered" -> if (pass) EvidenceVerdict.Pass("rendered image passed comparison") else EvidenceVerdict.Fail("rendered image failed comparison")
                expectation == "render" && observed == "refused" -> EvidenceVerdict.Fail("scene refused: ${reasonCode ?: "unknown"}")
                expectation == "render" && observed == "unavailable" -> EvidenceVerdict.Unavailable("scene unavailable: ${reasonCode ?: "unknown"}")
                expectation.startsWith("refuse:") && observed == "rendered" -> EvidenceVerdict.Fail("scene rendered instead of refusing")
                expectation.startsWith("refuse:") && observed == "refused" -> {
                    val expectedReason = expectation.removePrefix("refuse:")
                    when {
                        reasonCode != expectedReason -> EvidenceVerdict.Fail("expected refusal $expectedReason, got $reasonCode")
                        submissionDelta != 0L -> EvidenceVerdict.Fail("refusal submitted $submissionDelta command(s)")
                        else -> EvidenceVerdict.Pass("exact refusal before submission")
                    }
                }
                expectation.startsWith("refuse:") && observed == "unavailable" -> EvidenceVerdict.Unavailable("scene unavailable: ${reasonCode ?: "unknown"}")
                else -> error("unknown expectation/observation pair")
            }
            require(recordedKind == reconstructed.kind()) { "verdict kind mismatch" }
            require(recordedReason == reconstructed.reason()) { "verdict reason mismatch" }
            if (observed == "rendered") {
                val comparisonPolicy = requireNotNull(expected.descriptor.comparison)
                val gpu = decodePng(directory.resolve("gpu.png"), width, height)
                val oracle = when (expected.descriptor.oracle) {
                    is OraclePolicy.GeneratedCpu -> {
                        val expectedPixels = requireNotNull(expected.expectedRgba)
                        val cpu = decodePng(directory.resolve("cpu.png"), width, height)
                        require(cpu.contentEquals(expectedPixels)) { "CPU PNG does not match expected oracle pixels" }
                        expectedPixels
                    }
                    is OraclePolicy.CheckedInPng -> {
                        val expectedPng = requireNotNull(expected.checkedInPngBytes)
                        require(Files.readAllBytes(directory.resolve("skia.png")).contentEquals(expectedPng)) { "checked-in oracle PNG does not match expected bytes" }
                        decodePng(directory.resolve("skia.png"), width, height)
                    }
                    OraclePolicy.StableRefusal -> error("rendered evidence cannot use StableRefusal oracle")
                }
                val recomputed = EvidenceComparator().compare(gpu, oracle, width, height, comparisonPolicy)
                require(similarity == recomputed.similarityPercent) { "similarity does not match recomputed comparison" }
                require(differingPixels == recomputed.differingPixels) { "differingPixels does not match recomputed comparison" }
                require(maxChannelDifference == recomputed.maxChannelDifference) { "maxChannelDifference does not match recomputed comparison" }
                require(meanChannelDifference == recomputed.meanChannelDifference) { "meanChannelDifference does not match recomputed comparison" }
                require(pass == recomputed.passed) { "pass does not match recomputed comparison" }
                require(decodePng(directory.resolve("diff.png"), width, height).contentEquals(recomputed.diffRgba)) { "diff PNG does not match recomputed comparison" }
            }
            return EvidenceBundleVerification.Verified(sceneId, reconstructed)
        } catch (failure: Throwable) {
            errors += failure.message ?: failure::class.simpleName.orEmpty()
            return EvidenceBundleVerification.Invalid(sceneId, errors)
        }
    }

    private fun readObject(path: Path, label: String): JsonObject {
        require(Files.isRegularFile(path, NOFOLLOW_LINKS)) { "missing $label" }
        val text = Files.readString(path)
        rejectDuplicateKeys(text)
        val parsed = EvidenceJson.parseToJsonElement(text)
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
    private fun JsonObject.requiredInt(key: String): Int = (this[key] as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull ?: error("$key must be an integer")
    private fun JsonObject.requiredDouble(key: String): Double = (this[key] as? JsonPrimitive)?.takeUnless { it.isString }?.doubleOrNull ?: error("$key must be a number")
    private fun JsonObject.requiredLong(key: String): Long = (this[key] as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull ?: error("$key must be a long")
    private fun JsonObject.requiredBoolean(key: String): Boolean = (this[key] as? JsonPrimitive)?.takeUnless { it.isString }?.booleanOrNull ?: error("$key must be a boolean")
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
        return value.jsonPrimitive.takeUnless { it.isString }?.booleanOrNull ?: error("$key must be a boolean or null")
    }
    private fun JsonObject.optionalNullableLong(key: String): Long? {
        val value = this[key] ?: error("missing $key")
        if (value is JsonNull) return null
        return value.jsonPrimitive.takeUnless { it.isString }?.longOrNull ?: error("$key must be a long or null")
    }
    private fun verifyPromotion(directory: Path, sceneId: String, sourceCommit: String) {
        val promotion = readObject(directory.resolve("promotion.json"), "promotion")
        promotion.requireKeys(setOf("schemaVersion", "sceneId", "sourceCommit", "promotedAtUtc", "reviewer", "reason", "rebaseline", "priorComparison", "newComparison"))
        require(promotion.requiredString("schemaVersion") == GPU_EVIDENCE_PROMOTION_SCHEMA) { "unsupported promotion schemaVersion" }
        require(promotion.requiredString("sceneId") == sceneId) { "promotion sceneId mismatch" }
        require(promotion.requiredString("sourceCommit") == sourceCommit) { "promotion sourceCommit mismatch" }
        promotion.requiredString("promotedAtUtc")
        require(promotion.requiredString("reviewer").isNotBlank()) { "promotion reviewer must not be blank" }
        require(promotion.requiredString("reason").isNotBlank()) { "promotion reason must not be blank" }
        val rebaseline = promotion.requiredBoolean("rebaseline")
        val prior = promotion["priorComparison"]
        val next = promotion["newComparison"]
        require((prior is JsonNull) == (next is JsonNull)) { "promotion comparison summaries must be paired" }
        if (rebaseline) require(prior !is JsonNull && next !is JsonNull) { "rebaseline requires old/new comparison summaries" }
        if (prior != null && prior !is JsonNull) require(prior is JsonPrimitive && prior.isString && prior.content.isNotBlank()) { "priorComparison must be a nonblank string or null" }
        if (next != null && next !is JsonNull) require(next is JsonPrimitive && next.isString && next.content.isNotBlank()) { "newComparison must be a nonblank string or null" }
    }
    private fun JsonPrimitive.asString(label: String): String = require(isString && contentOrNull != null) { "$label must be a string" }.let { content }
    private fun decodePng(path: Path, width: Int, height: Int): ByteArray {
        val image = ComparisonUtils.readPngAsSrgbBufferedImage(path.toFile())
        require(image.width == width && image.height == height) { "PNG dimensions do not match stats" }
        return ComparisonUtils.bufferedImageToRgba(image)
    }
    private fun EvidenceExpectation.manifestValue(): String = when (this) {
        EvidenceExpectation.ShouldRender -> "render"
        is EvidenceExpectation.ShouldRefuse -> "refuse:$stableReasonCode"
    }
    private fun OraclePolicy.kind(): String = when (this) {
        is OraclePolicy.GeneratedCpu -> "generated-cpu"
        is OraclePolicy.CheckedInPng -> "checked-in-png"
        OraclePolicy.StableRefusal -> "stable-refusal"
    }
    private fun OraclePolicy.id(): String = when (this) {
        is OraclePolicy.GeneratedCpu -> oracleId
        is OraclePolicy.CheckedInPng -> resourcePath
        OraclePolicy.StableRefusal -> "stable-refusal"
    }
    private fun OraclePolicy.version(): Int = when (this) {
        is OraclePolicy.GeneratedCpu -> version
        is OraclePolicy.CheckedInPng -> 1
        OraclePolicy.StableRefusal -> 1
    }
    private fun OraclePolicy.provenance(): String = when (this) {
        is OraclePolicy.GeneratedCpu -> "generated-cpu"
        is OraclePolicy.CheckedInPng -> provenance
        OraclePolicy.StableRefusal -> "stable-refusal"
    }
    private fun OraclePolicy.sha256(): String? = when (this) {
        is OraclePolicy.CheckedInPng -> sha256
        else -> null
    }
    private fun isSafeFileName(name: String): Boolean = name.isNotBlank() && !name.startsWith('/') && !name.contains("\\") && !name.split('/').any { it == ".." || it.isBlank() } && name == Path.of(name).fileName.toString()
    private fun sha256(value: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(value).joinToString("") { "%02x".format(it) }
    private fun rejectDuplicateKeys(text: String) {
        val objects = ArrayDeque<MutableSet<String>>()
        var index = 0
        while (index < text.length) {
            when (text[index]) {
                '"' -> {
                    val start = index++
                    while (index < text.length) {
                        if (text[index] == '\\') index += 2
                        else if (text[index++] == '"') break
                    }
                    var next = index
                    while (next < text.length && text[next].isWhitespace()) next++
                    if (next < text.length && text[next] == ':' && objects.isNotEmpty()) {
                        val rawKey = text.substring(start, index)
                        val key = runCatching { EvidenceJson.parseToJsonElement(rawKey).jsonPrimitive.content }.getOrElse { rawKey }
                        require(objects.last().add(key)) { "duplicate JSON key: $key" }
                    }
                }
                '{' -> objects.addLast(mutableSetOf())
                '}' -> if (objects.isNotEmpty()) objects.removeLast()
            }
            index++
        }
    }
    private fun EvidenceVerdict.kind() = when (this) { is EvidenceVerdict.Pass -> "pass"; is EvidenceVerdict.Fail -> "fail"; is EvidenceVerdict.Unavailable -> "unavailable" }
    private fun EvidenceVerdict.reason() = when (this) { is EvidenceVerdict.Pass -> reason; is EvidenceVerdict.Fail -> reason; is EvidenceVerdict.Unavailable -> reason }
    private val RENDER_FILES = setOf("manifest.json", "gpu.png", "cpu.png", "diff.png", "stats.json", "route.json", "diagnostics.json", "environment.json", "verdict.json")
    private val CHECKED_IN_RENDER_FILES = setOf("manifest.json", "gpu.png", "skia.png", "diff.png", "stats.json", "route.json", "diagnostics.json", "environment.json", "verdict.json")
    private val REFUSAL_FILES = setOf("manifest.json", "stats.json", "route.json", "diagnostics.json", "environment.json", "verdict.json")
    private val TELEMETRY_FIELDS = setOf("renderPasses", "offscreenPasses", "windowPasses", "submissions", "commandBuffers", "buffersCreated", "texturesCreated", "intermediateTexturesCreated", "coverageMasksDestroyed", "destinationCopies", "destinationReadbackSnapshots", "msaaTargets", "msaaResolves", "bindGroupsCreated", "samplersCreated", "queueWrites", "uniformSlabsCreated", "uniformSlabBytesAllocated", "uniformSlabFallbacks", "passBatchPlans", "passBatchesAccepted", "passBatchCuts", "passBatchPackets")
}

const val GPU_EVIDENCE_PROMOTION_SCHEMA = "gpu-evidence-promotion-v1"
