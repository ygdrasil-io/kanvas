package org.graphiks.kanvas.gpu.evidence.performance

import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.security.MessageDigest
import java.time.Clock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private val PerformanceJson = kotlinx.serialization.json.Json { prettyPrint = false; explicitNulls = true; ignoreUnknownKeys = false }

@OptIn(ExperimentalSerializationApi::class)
class PerformanceBundleWriter internal constructor(
    repositoryRoot: Path,
    private val sourceCommit: String,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val root = repositoryRoot.toAbsolutePath().normalize()
    private val rootReal: Path

    init {
        require(sourceCommit.matches(SOURCE_COMMIT)) { "source commit must be lowercase 40-hex" }
        require(!Files.isSymbolicLink(root)) { "repository root cannot be a symlink" }
        Files.createDirectories(root)
        rootReal = root.toRealPath(NOFOLLOW_LINKS)
    }

    constructor(repositoryRoot: java.io.File, sourceCommit: String, clock: Clock = Clock.systemUTC()) : this(repositoryRoot.toPath(), sourceCommit, clock)

    fun writeGenerated(run: PerformanceRun): Path {
        require(run.sourceCommit == sourceCommit) { "run sourceCommit does not match writer" }
        val destination = destination(run.sceneId)
        val stagingParent = destination.parent
        ensureSafe(stagingParent)
        Files.createDirectories(stagingParent)
        val stagingRoot = Files.createTempDirectory(stagingParent, ".${destination.fileName}.tmp-")
        val staging = stagingRoot.resolve(destination.fileName)
        return try {
            Files.createDirectory(staging)
            val files = content(run)
            files.forEach { (name, bytes) -> Files.write(staging.resolve(name), bytes) }
            val verification = PerformanceBundleVerifier.verify(staging, sourceCommit)
            require(verification is PerformanceBundleVerification.Verified) {
                "performance bundle failed independent verification: ${(verification as PerformanceBundleVerification.Invalid).errors.joinToString("; ")}"
            }
            if (Files.exists(destination, NOFOLLOW_LINKS)) {
                require(!Files.isSymbolicLink(destination) && Files.isDirectory(destination, NOFOLLOW_LINKS)) { "destination must be a directory" }
                deleteTree(destination)
            }
            try {
                Files.move(staging, destination, ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(staging, destination)
            }
            destination
        } finally {
            deleteTree(stagingRoot)
        }
    }

    private fun destination(sceneId: String): Path {
        require(sceneId.matches(SCENE)) { "scene id must use lower-kebab-case" }
        val path = root.resolve("reports/gpu-renderer/evidence/performance/generated").resolve(sourceCommit).resolve(sceneId).normalize()
        require(path.startsWith(root) && !path.toString().contains("/promoted/") && !path.toString().contains("/correctness/")) { "performance destination escapes canonical root" }
        return path
    }

    private fun content(run: PerformanceRun): Map<String, ByteArray> {
        val files = linkedMapOf<String, ByteArray>()
        files["environment.json"] = environment(run).bytes()
        files["eligibility.json"] = eligibility(run).bytes()
        files["timings.json"] = timings(run).bytes()
        files["telemetry.json"] = telemetry(run).bytes()
        files["diagnostics.json"] = buildJsonObject { put("diagnostics", buildJsonArray { run.diagnostics.forEach { value -> add(JsonPrimitive(value)) } }) }.bytes()
        files["verdict.json"] = verdict(run.verdict).bytes()
        val hashes = files.mapValues { sha256(it.value) }
        files["manifest.json"] = buildJsonObject {
            put("schema", GPU_EVIDENCE_PERFORMANCE_SCHEMA)
            put("sourceCommit", run.sourceCommit); put("sceneId", run.sceneId)
            put("coldFrames", 1); put("warmupFrames", run.config.warmupFrames); put("measuredFrames", run.config.measuredFrames)
            put("gateVersion", run.config.gateVersion); put("generatedAtUtc", clock.instant().toString())
            put("hashes", buildJsonObject { hashes.toSortedMap().forEach { (key, value) -> put(key, value) } })
        }.bytes()
        return files
    }

    private fun environment(run: PerformanceRun) = buildJsonObject {
        val e = run.environment
        put("sourceCommit", e.sourceCommit); put("osName", e.osName); put("osVersion", e.osVersion); put("osArchitecture", e.osArchitecture); put("javaVersion", e.javaVersion); put("deviceGeneration", e.deviceGeneration)
        put("adapter", e.adapter?.let { buildJsonObject {
            put("summary", it.summary); put("vendor", it.vendor); put("device", it.device); put("architecture", it.architecture); put("description", it.description); put("isFallbackAdapter", it.isFallbackAdapter)
            put("backend", unavailableAdapterFact(null, "GPUAdapterInfo does not expose backend"))
            put("driver", unavailableAdapterFact(null, "GPUAdapterInfo does not expose driver"))
        } } ?: JsonNull)
    }
    private fun eligibility(run: PerformanceRun) = buildJsonObject { put("kind", kind(run.eligibility)); put("reason", run.eligibility.reason) }
    private fun timings(run: PerformanceRun) = buildJsonObject {
        put("coldReadbackNanos", run.coldReadbackNanos); put("warmupFrames", run.config.warmupFrames); put("measuredFrames", run.config.measuredFrames)
        put("samples", buildJsonArray { run.timingSamplesNanos.forEach { value -> add(JsonPrimitive(value)) } })
        put("summary", run.timings?.let { buildJsonObject { put("sampleCount", it.sampleCount); put("p50Nanos", it.p50Nanos); put("p95Nanos", it.p95Nanos); put("source", it.source.name) } } ?: JsonNull)
    }
    private fun telemetry(run: PerformanceRun) = buildJsonObject { put("before", metricMap(run.telemetry.before)); put("after", metricMap(run.telemetry.after)); put("delta", metricMap(run.telemetry.delta)) }
    private fun metricMap(map: Map<String, PerformanceMetric>) = buildJsonObject { map.toSortedMap().forEach { (key, value) -> put(key, buildJsonObject { put("value", value.value); put("source", value.source.name); put("reason", value.reason) }) } }
    private fun unavailableAdapterFact(value: String?, reason: String) = if (value == null) buildJsonObject { put("value", null); put("source", MetricSource.Unavailable.name); put("reason", reason) } else buildJsonObject { put("value", value); put("source", MetricSource.Observed.name); put("reason", null) }
    private fun verdict(verdict: PerformanceVerdict) = buildJsonObject { put("kind", kind(verdict)); put("reason", verdict.reason) }
    private fun kind(verdict: PerformanceVerdict) = when (verdict) { is PerformanceVerdict.EligibleMeasurement -> "EligibleMeasurement"; is PerformanceVerdict.DiagnosticOnly -> "DiagnosticOnly"; is PerformanceVerdict.Unavailable -> "Unavailable"; is PerformanceVerdict.Failed -> "Failed" }
    private fun JsonObject.bytes() = PerformanceJson.encodeToString(JsonObject.serializer(), this).toByteArray(Charsets.UTF_8)
    private fun ensureSafe(path: Path) { require(path.startsWith(root)); var current = root; root.relativize(path).forEach { current = current.resolve(it.toString()); require(!Files.isSymbolicLink(current)); if (Files.exists(current, NOFOLLOW_LINKS)) require(current.toRealPath(NOFOLLOW_LINKS).startsWith(rootReal)) } }
    private fun sha256(value: ByteArray) = MessageDigest.getInstance("SHA-256").digest(value).joinToString("") { "%02x".format(it) }
    private fun deleteTree(path: Path) { if (!Files.exists(path, NOFOLLOW_LINKS)) return; if (Files.isDirectory(path, NOFOLLOW_LINKS)) Files.list(path).use { it.forEach(::deleteTree) }; Files.deleteIfExists(path) }
    companion object { private val SOURCE_COMMIT = Regex("[0-9a-f]{40}"); private val SCENE = Regex("[a-z0-9]+(?:-[a-z0-9]+)*") }
}

sealed interface PerformanceBundleVerification {
    data object Verified : PerformanceBundleVerification
    data class Invalid(val errors: List<String>) : PerformanceBundleVerification
}

@OptIn(ExperimentalSerializationApi::class)
object PerformanceBundleVerifier {
    private val required = setOf("manifest.json", "environment.json", "eligibility.json", "timings.json", "telemetry.json", "diagnostics.json", "verdict.json")
    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = false }

    fun verify(bundle: Path, sourceCommit: String): PerformanceBundleVerification {
        val errors = mutableListOf<String>()
        if (!sourceCommit.matches(Regex("[0-9a-f]{40}"))) errors += "invalid source commit"
        if (!Files.isDirectory(bundle, NOFOLLOW_LINKS) || Files.isSymbolicLink(bundle)) return PerformanceBundleVerification.Invalid(listOf("bundle is not a directory"))
        val names = Files.list(bundle).use { stream -> stream.iterator().asSequence().map { it.fileName.toString() }.toSet() }
        if (names != required) errors += "required files mismatch"
        required.forEach { name ->
            val child = bundle.resolve(name)
            if (Files.isSymbolicLink(child) || !Files.isRegularFile(child, NOFOLLOW_LINKS)) errors += "required entry is not a regular file: $name"
        }
        val manifest = runCatching { json.parseToJsonElement(Files.readString(bundle.resolve("manifest.json"))).jsonObject }.getOrElse { return PerformanceBundleVerification.Invalid(listOf("manifest is invalid")) }
        if (manifest["schema"]?.jsonPrimitive?.content != GPU_EVIDENCE_PERFORMANCE_SCHEMA) errors += "schema mismatch"
        if (manifest["sourceCommit"]?.jsonPrimitive?.content != sourceCommit) errors += "source commit mismatch"
        val hashes = manifest["hashes"]?.jsonObject ?: run { errors += "hashes missing"; JsonObject(emptyMap()) }
        required.filter { it != "manifest.json" }.forEach { name -> if (hashes[name]?.jsonPrimitive?.content != sha256(Files.readAllBytes(bundle.resolve(name)))) errors += "hash mismatch: $name" }
        val eligibility = runCatching { json.parseToJsonElement(Files.readString(bundle.resolve("eligibility.json"))).jsonObject }.getOrElse { errors += "eligibility is invalid"; null }
        val verdict = runCatching { json.parseToJsonElement(Files.readString(bundle.resolve("verdict.json"))).jsonObject }.getOrElse { errors += "verdict is invalid"; null }
        val eligibilityKind = eligibility?.get("kind")?.jsonPrimitive?.content
        val verdictKind = verdict?.get("kind")?.jsonPrimitive?.content
        if (eligibilityKind == null || verdictKind == null) errors += "eligibility/verdict kind missing"
        if (eligibility != null && verdict != null) {
            val reasonsMatch = eligibility["reason"]?.jsonPrimitive?.content == verdict["reason"]?.jsonPrimitive?.content
            val allowedFailure = verdictKind == "Failed"
            if ((!allowedFailure && eligibilityKind != verdictKind) || (!allowedFailure && !reasonsMatch)) errors += "verdict does not match eligibility"
        }
        val timings = runCatching { json.parseToJsonElement(Files.readString(bundle.resolve("timings.json"))).jsonObject }.getOrElse { errors += "timings are invalid"; null }
        if (timings != null) {
            val measured = timings["measuredFrames"]?.jsonPrimitive?.content?.toIntOrNull()
            val samples = timings["samples"]?.jsonArray
            if (measured == null || samples == null || samples.any { it !is JsonPrimitive || it.jsonPrimitive.isString || it.jsonPrimitive.content.toLongOrNull() == null }) errors += "timings provenance is invalid"
            else if (samples.size != measured && verdictKind != "Failed") errors += "timing sample count mismatch"
        }
        val telemetry = runCatching { json.parseToJsonElement(Files.readString(bundle.resolve("telemetry.json"))).jsonObject }.getOrElse { errors += "telemetry is invalid"; null }
        telemetry?.values?.forEach { phase ->
            if (phase !is kotlinx.serialization.json.JsonObject) errors += "telemetry phase is invalid"
            else phase.values.forEach { metric ->
                val record = metric as? kotlinx.serialization.json.JsonObject
                val source = record?.get("source")?.jsonPrimitive?.content
                val value = record?.get("value")?.jsonPrimitive
                val reason = record?.get("reason")?.jsonPrimitive?.contentOrNull
                if (source !in setOf("Observed", "Derived", "Unavailable")) errors += "telemetry metric source is invalid"
                if (source == "Unavailable" && reason.isNullOrBlank()) errors += "unavailable metric reason missing"
                if (source != "Unavailable" && (value == null || value.isString || value.content.toLongOrNull() == null || value.content.toLong() < 0L)) errors += "available metric value is invalid"
            }
        }
        return if (errors.isEmpty()) PerformanceBundleVerification.Verified else PerformanceBundleVerification.Invalid(errors)
    }
    private fun sha256(value: ByteArray) = MessageDigest.getInstance("SHA-256").digest(value).joinToString("") { "%02x".format(it) }
}
