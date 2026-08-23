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
    private val moveStrategy: (Path, Path) -> Unit = { from, to ->
        try {
            Files.move(from, to, ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(from, to)
        }
    },
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
            val verification = PerformanceBundleVerifier.verifyStaging(staging, sourceCommit)
            require(verification is PerformanceBundleVerification.Verified) {
                "performance bundle failed independent verification: ${(verification as PerformanceBundleVerification.Invalid).errors.joinToString("; ")}"
            }
            var backup: Path? = null
            if (Files.exists(destination, NOFOLLOW_LINKS)) {
                require(!Files.isSymbolicLink(destination) && Files.isDirectory(destination, NOFOLLOW_LINKS)) { "destination must be a directory" }
                backup = Files.createTempDirectory(stagingParent, ".${destination.fileName}.backup-")
                deleteTree(backup)
                moveStrategy(destination, backup)
            }
            try {
                moveStrategy(staging, destination)
            } catch (failure: Exception) {
                if (Files.exists(destination, NOFOLLOW_LINKS)) deleteTree(destination)
                if (backup != null && Files.exists(backup, NOFOLLOW_LINKS)) moveStrategy(backup, destination)
                throw failure
            }
            if (backup != null) deleteTree(backup)
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
    private fun telemetry(run: PerformanceRun) = buildJsonObject {
        put("cold", phaseTelemetry(run.telemetry.cold)); put("warmup", phaseTelemetry(run.telemetry.warmup)); put("measured", phaseTelemetry(run.telemetry.measured))
        run.telemetry.total?.let { put("total", phaseTelemetry(it)) }
    }
    private fun phaseTelemetry(phase: PerformanceTelemetrySnapshot) = buildJsonObject { put("before", metricMap(phase.before)); put("after", metricMap(phase.after)); put("delta", metricMap(phase.delta)) }
    private fun metricMap(map: Map<String, PerformanceMetric>) = buildJsonObject {
        val values = if (map.isEmpty()) PERFORMANCE_COUNTER_KEYS.associateWith {
            PerformanceMetric(null, MetricSource.Unavailable, "telemetry unavailable")
        } else map
        values.toSortedMap().forEach { (key, value) ->
            put(key, buildJsonObject { put("value", value.value); put("source", value.source.name); put("reason", value.reason) })
        }
    }
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
    private val json = PerformanceJson

    fun verify(bundle: Path, sourceCommit: String): PerformanceBundleVerification {
        return runCatching { verifyInternal(bundle, sourceCommit, allowStaging = false) }
            .getOrElse { PerformanceBundleVerification.Invalid(listOf("bundle is invalid: ${it.message ?: it::class.simpleName}")) }
    }

    internal fun verifyStaging(bundle: Path, sourceCommit: String): PerformanceBundleVerification {
        return runCatching { verifyInternal(bundle, sourceCommit, allowStaging = true) }
            .getOrElse { PerformanceBundleVerification.Invalid(listOf("bundle is invalid: ${it.message ?: it::class.simpleName}")) }
    }

    private fun verifyInternal(bundle: Path, sourceCommit: String, allowStaging: Boolean): PerformanceBundleVerification {
        val errors = mutableListOf<String>()
        if (!sourceCommit.matches(Regex("[0-9a-f]{40}"))) errors += "invalid source commit"
        if (!Files.isDirectory(bundle, NOFOLLOW_LINKS) || Files.isSymbolicLink(bundle)) return PerformanceBundleVerification.Invalid(listOf("bundle is not a directory"))
        val names = Files.list(bundle).use { stream -> stream.iterator().asSequence().map { it.fileName.toString() }.toSet() }
        if (names != required) errors += "required files mismatch"
        required.forEach { name ->
            val child = bundle.resolve(name)
            if (Files.isSymbolicLink(child) || !Files.isRegularFile(child, NOFOLLOW_LINKS)) errors += "required entry is not a regular file: $name"
        }
        val manifest = parseObject(bundle.resolve("manifest.json"), "manifest", errors) ?: return PerformanceBundleVerification.Invalid(errors)
        requireKeys(manifest, setOf("schema", "sourceCommit", "sceneId", "coldFrames", "warmupFrames", "measuredFrames", "gateVersion", "generatedAtUtc", "hashes"), "manifest", errors)
        if (manifest["schema"]?.jsonPrimitive?.content != GPU_EVIDENCE_PERFORMANCE_SCHEMA) errors += "schema mismatch"
        if (manifest["sourceCommit"]?.jsonPrimitive?.content != sourceCommit || (!allowStaging && bundle.parent?.fileName?.toString() != sourceCommit)) errors += "source commit mismatch"
        if (manifest["sceneId"]?.jsonPrimitive?.content != bundle.fileName.toString()) errors += "scene mismatch"
        if (!allowStaging) {
            val absolute = bundle.toAbsolutePath().normalize()
            val tail = (0 until absolute.nameCount).map { absolute.getName(it).toString() }.takeLast(7)
            if (tail != listOf("reports", "gpu-renderer", "evidence", "performance", "generated", sourceCommit, bundle.fileName.toString())) errors += "non-canonical bundle path"
        }
        if (manifest["coldFrames"]?.jsonPrimitive?.content != "1" || manifest["warmupFrames"]?.jsonPrimitive?.content != "10" || manifest["measuredFrames"]?.jsonPrimitive?.content != "90" || manifest["gateVersion"]?.jsonPrimitive?.content != "1") errors += "manifest counts or gate mismatch"
        val hashes = manifest["hashes"]?.jsonObject ?: run { errors += "hashes missing"; JsonObject(emptyMap()) }
        if (hashes.keys != required.filter { it != "manifest.json" }.toSet()) errors += "hash key set mismatch"
        required.filter { it != "manifest.json" }.forEach { name ->
            val actual = runCatching { sha256(Files.readAllBytes(bundle.resolve(name))) }.getOrNull()
            if (hashes[name]?.jsonPrimitive?.content != actual) errors += "hash mismatch: $name"
        }
        parseObject(bundle.resolve("environment.json"), "environment", errors)?.let { environment ->
            requireKeys(environment, setOf("sourceCommit", "osName", "osVersion", "osArchitecture", "javaVersion", "deviceGeneration", "adapter"), "environment", errors)
            if (environment["sourceCommit"]?.jsonPrimitive?.content != sourceCommit) errors += "environment source commit mismatch"
            val adapterElement = environment["adapter"]
            if (adapterElement is JsonObject) {
                requireKeys(adapterElement, setOf("summary", "vendor", "device", "architecture", "description", "isFallbackAdapter", "backend", "driver"), "adapter", errors)
                verifyUnavailableRecord(adapterElement["backend"], "adapter backend", errors)
                verifyUnavailableRecord(adapterElement["driver"], "adapter driver", errors)
            } else if (adapterElement !is JsonNull) {
                errors += "adapter is invalid"
            }
        }
        val eligibility = parseObject(bundle.resolve("eligibility.json"), "eligibility", errors)
        val verdict = parseObject(bundle.resolve("verdict.json"), "verdict", errors)
        eligibility?.let { requireKeys(it, setOf("kind", "reason"), "eligibility", errors) }
        verdict?.let { requireKeys(it, setOf("kind", "reason"), "verdict", errors) }
        val eligibilityKind = eligibility?.get("kind")?.jsonPrimitive?.content
        val verdictKind = verdict?.get("kind")?.jsonPrimitive?.content
        val eligibilityKinds = setOf("EligibleMeasurement", "DiagnosticOnly", "Unavailable")
        val verdictKinds = eligibilityKinds + "Failed"
        if (eligibilityKind !in eligibilityKinds || verdictKind !in verdictKinds) errors += "eligibility/verdict kind unknown"
        if (eligibilityKind == "EligibleMeasurement") {
            val adapter = (parseObject(bundle.resolve("environment.json"), "environment", mutableListOf())?.get("adapter") as? JsonObject)
            if (adapter == null) errors += "eligible measurement requires adapter identity"
            else {
                val identity = listOf("summary", "vendor", "device", "architecture", "description").any { !adapter[it]?.jsonPrimitive?.contentOrNull.isNullOrBlank() }
                if (!identity) errors += "eligible measurement adapter identity missing"
                if (adapter["isFallbackAdapter"]?.jsonPrimitive?.content != "false") errors += "eligible measurement cannot use fallback adapter"
            }
        }
        if (eligibility != null && verdict != null) {
            val reasonsMatch = eligibility["reason"]?.jsonPrimitive?.content == verdict["reason"]?.jsonPrimitive?.content
            val allowedFailure = verdictKind == "Failed"
            if ((allowedFailure && eligibilityKind != "EligibleMeasurement") || (!allowedFailure && eligibilityKind != verdictKind) || (!allowedFailure && !reasonsMatch)) errors += "verdict does not match eligibility"
        }
        val timings = parseObject(bundle.resolve("timings.json"), "timings", errors)
        if (timings != null) {
            requireKeys(timings, setOf("coldReadbackNanos", "warmupFrames", "measuredFrames", "samples", "summary"), "timings", errors)
            if (timings["warmupFrames"]?.jsonPrimitive?.content != "10" || timings["measuredFrames"]?.jsonPrimitive?.content != "90") errors += "timing config mismatch"
            val measured = timings["measuredFrames"]?.jsonPrimitive?.content?.toIntOrNull()
            val samples = timings["samples"]?.jsonArray
            if (measured == null || samples == null || samples.any { it !is JsonPrimitive || it.jsonPrimitive.isString || it.jsonPrimitive.content.toLongOrNull() == null }) errors += "timings provenance is invalid"
            else if (eligibilityKind == "EligibleMeasurement" && verdictKind != "Failed" && samples.size != measured) errors += "timing sample count mismatch"
            else if (eligibilityKind != "EligibleMeasurement" && samples.isNotEmpty()) errors += "diagnostic timing samples must be empty"
            else if (samples != null && samples.size > measured) errors += "timing sample count exceeds configured count"
            timings["summary"]?.let { summaryElement ->
                if (summaryElement is JsonObject) {
                    requireKeys(summaryElement, setOf("sampleCount", "p50Nanos", "p95Nanos", "source"), "timing summary", errors)
                    val values = samples.orEmpty().mapNotNull { it.jsonPrimitive.content.toLongOrNull() }
                    if (summaryElement["source"]?.jsonPrimitive?.content != MetricSource.Observed.name) errors += "timing summary provenance is invalid"
                    if (values.isNotEmpty()) {
                        val expected = FrameTimingSummary.fromSamples(values)
                        if (summaryElement["sampleCount"]?.jsonPrimitive?.content != expected.sampleCount.toString() || summaryElement["p50Nanos"]?.jsonPrimitive?.content != expected.p50Nanos.toString() || summaryElement["p95Nanos"]?.jsonPrimitive?.content != expected.p95Nanos.toString() || summaryElement["source"]?.jsonPrimitive?.content != expected.source.name) errors += "timing summary does not match samples"
                    }
                } else if (summaryElement !is kotlinx.serialization.json.JsonNull) errors += "timing summary is invalid"
                else if (samples != null && samples.isNotEmpty()) errors += "timing summary missing for samples"
            }
        }
        parseObject(bundle.resolve("telemetry.json"), "telemetry", errors)?.let { telemetry ->
            val phaseNames = setOf("cold", "warmup", "measured", "total")
            if (!telemetry.keys.all { it in phaseNames } || !setOf("cold", "warmup", "measured").all { it in telemetry.keys }) errors += "telemetry phase key set mismatch"
            telemetry.forEach { (phaseName, phaseElement) -> if (phaseElement is JsonObject) verifyPhase(phaseName, phaseElement, errors) else errors += "telemetry phase is invalid" }
            if (eligibilityKind == "EligibleMeasurement" && verdictKind != "Failed") {
                mapOf("cold" to 1L, "warmup" to 10L, "measured" to 90L, "total" to 101L).forEach { (phaseName, expected) ->
                    val submissions = (telemetry[phaseName] as? JsonObject)?.get("delta")?.jsonObject?.get("submissions")?.jsonObject
                    if (submissions?.get("source")?.jsonPrimitive?.content != MetricSource.Derived.name || submissions["value"]?.jsonPrimitive?.content?.toLongOrNull() != expected) errors += "$phaseName submissions provenance mismatch"
                }
            }
        }
        parseObject(bundle.resolve("diagnostics.json"), "diagnostics", errors)?.let { diagnostics ->
            requireKeys(diagnostics, setOf("diagnostics"), "diagnostics", errors)
            val values = diagnostics["diagnostics"] as? kotlinx.serialization.json.JsonArray
            if (values == null || values.any { it !is JsonPrimitive || !it.jsonPrimitive.isString }) errors += "diagnostics values invalid"
        }
        return if (errors.isEmpty()) PerformanceBundleVerification.Verified else PerformanceBundleVerification.Invalid(errors)
    }
    private fun parseObject(path: Path, label: String, errors: MutableList<String>): JsonObject? = runCatching {
        if (!Files.isRegularFile(path, NOFOLLOW_LINKS)) error("missing")
        val raw = Files.readString(path)
        val object_ = json.parseToJsonElement(raw).jsonObject
        if (raw != json.encodeToString(JsonObject.serializer(), object_)) error("non-canonical")
        object_
    }.getOrElse { errors += "$label is invalid"; null }
    private fun requireKeys(object_: JsonObject, expected: Set<String>, label: String, errors: MutableList<String>) { if (object_.keys != expected) errors += "$label key set mismatch" }
    private fun verifyUnavailableRecord(element: kotlinx.serialization.json.JsonElement?, label: String, errors: MutableList<String>) {
        val record = element as? JsonObject
        if (record == null) { errors += "$label record invalid"; return }
        requireKeys(record, setOf("value", "source", "reason"), label, errors)
        if (record["value"] !is JsonNull || record["source"]?.jsonPrimitive?.content != MetricSource.Unavailable.name || record["reason"]?.jsonPrimitive?.contentOrNull.isNullOrBlank()) errors += "$label provenance invalid"
    }
    private fun verifyPhase(name: String, phase: JsonObject, errors: MutableList<String>) {
        requireKeys(phase, setOf("before", "after", "delta"), "$name telemetry", errors)
        val before = phase["before"]?.jsonObject ?: run { errors += "$name before invalid"; return }
        val after = phase["after"]?.jsonObject ?: run { errors += "$name after invalid"; return }
        val delta = phase["delta"]?.jsonObject ?: run { errors += "$name delta invalid"; return }
        if (before.keys != PERFORMANCE_COUNTER_KEYS || after.keys != PERFORMANCE_COUNTER_KEYS || delta.keys != PERFORMANCE_COUNTER_KEYS) errors += "$name metric key sets mismatch"
        before.keys.forEach { key ->
            val b = before[key]?.jsonObject; val a = after[key]?.jsonObject; val d = delta[key]?.jsonObject
            val bs = b?.get("source")?.jsonPrimitive?.content; val as_ = a?.get("source")?.jsonPrimitive?.content; val ds = d?.get("source")?.jsonPrimitive?.content
            if (b == null || a == null || d == null || b.keys != setOf("value", "source", "reason") || a.keys != b.keys || d.keys != b.keys) errors += "$name metric record invalid: $key"
            if (bs !in setOf("Observed", "Unavailable") || as_ !in setOf("Observed", "Unavailable") || ds !in setOf("Derived", "Unavailable")) errors += "$name metric source invalid: $key"
            val bv = b?.get("value")?.jsonPrimitive?.content?.toLongOrNull(); val av = a?.get("value")?.jsonPrimitive?.content?.toLongOrNull(); val dv = d?.get("value")?.jsonPrimitive?.content?.toLongOrNull()
            if (bs != "Unavailable" && (bv == null || bv < 0L) || as_ != "Unavailable" && (av == null || av < 0L) || ds != "Unavailable" && (dv == null || dv < 0L)) errors += "$name metric value invalid: $key"
            if (ds == "Derived" && bv != null && av != null && dv != av - bv) errors += "$name metric delta mismatch: $key"
            if (bs == "Unavailable" && b?.get("reason")?.jsonPrimitive?.contentOrNull.isNullOrBlank() || as_ == "Unavailable" && a?.get("reason")?.jsonPrimitive?.contentOrNull.isNullOrBlank() || ds == "Unavailable" && d?.get("reason")?.jsonPrimitive?.contentOrNull.isNullOrBlank()) errors += "$name unavailable metric reason missing: $key"
            if (bs == "Unavailable" && b?.get("value") !is JsonNull || as_ == "Unavailable" && a?.get("value") !is JsonNull || ds == "Unavailable" && d?.get("value") !is JsonNull) errors += "$name unavailable metric value must be null: $key"
            if (bs != "Unavailable" && b?.get("reason") !is JsonNull || as_ != "Unavailable" && a?.get("reason") !is JsonNull || ds != "Unavailable" && d?.get("reason") !is JsonNull) errors += "$name available metric reason must be null: $key"
        }
    }
    private fun sha256(value: ByteArray) = MessageDigest.getInstance("SHA-256").digest(value).joinToString("") { "%02x".format(it) }
}
