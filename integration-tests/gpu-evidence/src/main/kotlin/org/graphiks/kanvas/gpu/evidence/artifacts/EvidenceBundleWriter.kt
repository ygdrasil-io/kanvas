package org.graphiks.kanvas.gpu.evidence.artifacts

import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.security.MessageDigest
import java.time.Clock
import org.graphiks.kanvas.gpu.evidence.catalog.*
import org.graphiks.kanvas.gpu.evidence.gate.EvidenceExpectationGate
import org.graphiks.kanvas.gpu.evidence.gate.EvidenceVerdict
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeTelemetry
import org.graphiks.kanvas.test.ComparisonUtils
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.add
import kotlinx.serialization.json.JsonNull

class EvidenceBundleWriter(
    repositoryRoot: Path,
    private val sourceCommit: String,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val root = repositoryRoot.toAbsolutePath().normalize()

    init {
        require(SOURCE_COMMIT.matches(sourceCommit)) { "source commit must be a single safe path component" }
        Files.createDirectories(root)
    }

    constructor(repositoryRoot: java.io.File, sourceCommit: String, clock: Clock = Clock.systemUTC()) :
        this(repositoryRoot.toPath(), sourceCommit, clock)

    fun writeGenerated(
        descriptor: EvidenceSceneDescriptor,
        observation: SceneObservation,
        expectedRgba: ByteArray? = null,
        attemptId: String = observation.routeAttemptId() ?: "attempt-1",
    ): Path {
        require(SAFE_COMPONENT.matches(attemptId)) { "attempt id must be a single safe path component" }
        require(observation !is SceneObservation.Unavailable) { "unavailable observations cannot produce bundles" }
        require(observation.environment.sourceCommit == sourceCommit) { "observation sourceCommit does not match writer sourceCommit" }
        val destination = destination(descriptor.id.value)
        return try {
            val temp = siblingTemp(destination)
            Files.createDirectories(temp)
            writeBundle(temp, descriptor, observation, expectedRgba, attemptId)
            moveIntoPlace(temp, destination)
            destination
        } catch (failure: Throwable) {
            retainFailure(descriptor, observation, attemptId, failure)
            throw failure
        }
    }

    private fun destination(sceneId: String): Path {
        require(!sceneId.contains('/')) { "scene id cannot contain path separators" }
        val path = root.resolve("reports/gpu-renderer/evidence/correctness/generated")
            .resolve(sourceCommit).resolve(sceneId).normalize()
        require(path.parent != null && path.parent!!.parent != null)
        require(path.startsWith(root)) { "evidence destination escapes repository root" }
        require(!path.toString().contains("/promoted/")) { "promoted evidence is not writable" }
        return path
    }

    private fun writeBundle(
        directory: Path,
        descriptor: EvidenceSceneDescriptor,
        observation: SceneObservation,
        expectedRgba: ByteArray?,
        attemptId: String,
    ) {
        val files = linkedMapOf<String, ByteArray>()
        val rendered = observation as? SceneObservation.Rendered
        val refused = observation as? SceneObservation.Refused
        val observed = if (rendered != null) "rendered" else "refused"
        val expectation = when (val e = descriptor.expectation) {
            EvidenceExpectation.ShouldRender -> "render"
            is EvidenceExpectation.ShouldRefuse -> "refuse:${e.stableReasonCode}"
        }
        if (rendered != null) {
            val cpu = expectedRgba ?: rendered.rgba
            require(cpu.size == descriptor.width * descriptor.height * 4) { "CPU RGBA byte count does not match descriptor" }
            val gpuPng = pngBytes(rendered.rgba, descriptor.width, descriptor.height)
            val cpuPng = pngBytes(cpu, descriptor.width, descriptor.height)
            val diff = rendered.comparison.diffRgba
            files["gpu.png"] = gpuPng
            files["cpu.png"] = cpuPng
            files["diff.png"] = pngBytes(diff, descriptor.width, descriptor.height)
            files["stats.json"] = EvidenceStats(
                descriptor.width, descriptor.height, "rgba8unorm", "encoded-premul-srgb",
                descriptor.comparison!!.perChannelTolerance, descriptor.comparison.minimumSimilarityPercent,
                rendered.comparison.similarityPercent, rendered.comparison.differingPixels,
                rendered.comparison.maxChannelDifference, rendered.comparison.meanChannelDifference,
                rendered.comparison.passed,
            ).toJson().canonicalBytes()
        } else {
            files["stats.json"] = EvidenceStats(
                descriptor.width, descriptor.height, "rgba8unorm", "encoded-premul-srgb", 0, 100.0,
                100.0, 0, 0, 0.0, true,
            ).toJson().canonicalBytes()
        }
        files["route.json"] = routeJson(observation.route(), attemptId)
        files["diagnostics.json"] = diagnosticsJson(observation, attemptId)
        files["environment.json"] = environmentJson(observation.environment)
        val verdict = EvidenceExpectationGate.evaluate(descriptor, observation)
        files["verdict.json"] = EvidenceVerdictRecord(expectation, observed, verdict.kind(), verdict.reason()).toJson().canonicalBytes()
        val hashes = files.mapValues { sha256(it.value) }
        files["manifest.json"] = EvidenceManifest(
            GPU_EVIDENCE_SCHEMA, descriptor.id.value, expectation, observed, sourceCommit,
            clock.instant().toString(), oracleKind(descriptor.oracle), oracleId(descriptor.oracle), oracleVersion(descriptor.oracle), hashes,
        ).toJson().canonicalBytes()
        files.toSortedMap().forEach { (name, bytes) ->
            val target = directory.resolve(name)
            Files.write(target, bytes)
        }
    }

    private fun pngBytes(rgba: ByteArray, width: Int, height: Int): ByteArray {
        val temp = Files.createTempFile("gpu-evidence-png-", ".png").toFile()
        return try { ComparisonUtils.saveRgbaAsPng(rgba, width, height, temp); temp.readBytes() } finally { temp.delete() }
    }

    private fun siblingTemp(destination: Path): Path {
        var current = root
        val relative = root.relativize(destination.parent!!)
        for (index in 0 until relative.nameCount) {
            current = current.resolve(relative.getName(index).toString())
            require(!Files.isSymbolicLink(current)) { "evidence destination contains a symlink" }
        }
        Files.createDirectories(destination.parent)
        return Files.createTempDirectory(destination.parent, ".${destination.fileName}.tmp-")
    }

    private fun moveIntoPlace(temp: Path, destination: Path) {
        Files.deleteIfExists(destination)
        try { Files.move(temp, destination, ATOMIC_MOVE) } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temp, destination, REPLACE_EXISTING)
        }
    }

    private fun retainFailure(descriptor: EvidenceSceneDescriptor, observation: SceneObservation, attemptId: String, failure: Throwable) {
        runCatching {
            val failed = root.resolve("reports/gpu-renderer/evidence/correctness/generated").resolve(sourceCommit)
                .resolve("_failed").resolve("${descriptor.id.value}-$attemptId").normalize()
            if (!failed.startsWith(root)) return@runCatching
            Files.createDirectories(failed)
            Files.write(failed.resolve("diagnostics.json"), diagnosticsJson(observation, attemptId, failure.message ?: failure::class.simpleName.orEmpty()))
            Files.write(failed.resolve("environment.json"), environmentJson(observation.environment))
        }
    }

    private fun routeJson(route: RouteEvidence, attemptId: String): ByteArray = buildJsonObject {
        put("routeId", route.routeId); put("attemptId", route.attemptId ?: attemptId)
        put("furthestPhase", route.furthestPhase); put("outcome", route.outcome)
        put("encodedScopeKinds", buildJsonArray { route.encodedScopeKinds.forEach(::add) })
        put("structuralEvents", buildJsonArray { route.structuralEvents.forEach { e -> add(buildJsonObject { put("kind", e.kind); put("phase", e.phase); put("label", e.label) }) } })
        put("structuralCounters", buildJsonObject { route.structuralCounters.toSortedMap().forEach { (k, v) -> put(k, v) } })
        put("runtimeTelemetryDelta", telemetryJson(route.runtimeTelemetryDelta))
    }.canonicalBytes()

    private fun telemetryJson(t: GPUBackendRuntimeTelemetry) = buildJsonObject {
        put("renderPasses", t.renderPasses); put("offscreenPasses", t.offscreenPasses); put("windowPasses", t.windowPasses); put("submissions", t.submissions); put("commandBuffers", t.commandBuffers)
        put("buffersCreated", t.buffersCreated); put("texturesCreated", t.texturesCreated); put("intermediateTexturesCreated", t.intermediateTexturesCreated); put("coverageMasksDestroyed", t.coverageMasksDestroyed); put("destinationCopies", t.destinationCopies); put("destinationReadbackSnapshots", t.destinationReadbackSnapshots); put("msaaTargets", t.msaaTargets); put("msaaResolves", t.msaaResolves); put("bindGroupsCreated", t.bindGroupsCreated); put("samplersCreated", t.samplersCreated); put("queueWrites", t.queueWrites); put("uniformSlabsCreated", t.uniformSlabsCreated); put("uniformSlabBytesAllocated", t.uniformSlabBytesAllocated); put("uniformSlabFallbacks", t.uniformSlabFallbacks); put("passBatchPlans", t.passBatchPlans); put("passBatchesAccepted", t.passBatchesAccepted); put("passBatchCuts", t.passBatchCuts); put("passBatchPackets", t.passBatchPackets)
    }

    private fun diagnosticsJson(observation: SceneObservation, attemptId: String, extra: String? = null): ByteArray = buildJsonObject {
        put("attemptId", attemptId); put("diagnostics", buildJsonArray { observation.diagnostics().forEach(::add) })
        put("stableReasonCode", (observation as? SceneObservation.Refused)?.stableReasonCode)
        put("message", (observation as? SceneObservation.Refused)?.message); put("submissionDelta", (observation as? SceneObservation.Refused)?.submissionDelta ?: 0L)
        if (extra != null) put("writeFailure", extra)
    }.canonicalBytes()

    private fun environmentJson(e: EvidenceEnvironment): ByteArray = buildJsonObject {
        put("sourceCommit", e.sourceCommit); put("osName", e.osName); put("osVersion", e.osVersion); put("osArchitecture", e.osArchitecture); put("javaVersion", e.javaVersion); put("deviceGeneration", e.deviceGeneration); put("capabilityImplementation", e.capabilityImplementation); put("available", e.available)
        put("adapter", e.adapter?.let { buildJsonObject { put("summary", it.summary); put("vendor", it.vendor); put("device", it.device); put("architecture", it.architecture); put("description", it.description); put("isFallbackAdapter", it.isFallbackAdapter) } } ?: JsonNull)
    }.canonicalBytes()

    private fun oracleKind(o: OraclePolicy) = when (o) { is OraclePolicy.GeneratedCpu -> "generated-cpu"; is OraclePolicy.CheckedInPng -> "checked-in-png"; OraclePolicy.StableRefusal -> "stable-refusal" }
    private fun oracleId(o: OraclePolicy) = when (o) { is OraclePolicy.GeneratedCpu -> o.oracleId; is OraclePolicy.CheckedInPng -> o.resourcePath; OraclePolicy.StableRefusal -> "stable-refusal" }
    private fun oracleVersion(o: OraclePolicy) = when (o) { is OraclePolicy.GeneratedCpu -> o.version; else -> 1 }
    private fun sha256(value: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(value).joinToString("") { "%02x".format(it) }
    private fun SceneObservation.route() = when (this) { is SceneObservation.Rendered -> route; is SceneObservation.Refused -> route; is SceneObservation.Unavailable -> error("unavailable") }
    private fun SceneObservation.routeAttemptId() = route().attemptId
    private fun SceneObservation.diagnostics() = when (this) { is SceneObservation.Rendered -> diagnostics; is SceneObservation.Refused -> diagnostics; is SceneObservation.Unavailable -> emptyList() }
    private fun EvidenceVerdict.kind() = when (this) { is EvidenceVerdict.Pass -> "pass"; is EvidenceVerdict.Fail -> "fail"; is EvidenceVerdict.Unavailable -> "unavailable" }
    private fun EvidenceVerdict.reason() = when (this) { is EvidenceVerdict.Pass -> reason; is EvidenceVerdict.Fail -> reason; is EvidenceVerdict.Unavailable -> reason }
    companion object {
        private val SOURCE_COMMIT = Regex("[A-Za-z0-9][A-Za-z0-9._-]*")
        private val SAFE_COMPONENT = Regex("[A-Za-z0-9][A-Za-z0-9._-]*")
    }
}
