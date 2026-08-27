package org.graphiks.kanvas.gpu.evidence.artifacts

import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.nio.file.StandardOpenOption.WRITE
import java.security.MessageDigest
import java.time.Clock
import org.graphiks.kanvas.gpu.evidence.catalog.*
import org.graphiks.kanvas.gpu.evidence.gate.EvidenceExpectationGate
import org.graphiks.kanvas.gpu.evidence.gate.EvidenceVerdict
import org.graphiks.kanvas.gpu.evidence.programs.KanvasSurfaceProgram
import org.graphiks.kanvas.gpu.evidence.runner.RoutedSceneProgram
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeTelemetry
import org.graphiks.kanvas.test.ComparisonUtils
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.add
import kotlinx.serialization.json.JsonNull

class EvidenceBundleWriter internal constructor(
    repositoryRoot: Path,
    private val sourceCommit: String,
    private val clock: Clock = Clock.systemUTC(),
    private val moveStrategy: (Path, Path, Boolean) -> Unit = { source, destination, atomic ->
        if (atomic) Files.move(source, destination, ATOMIC_MOVE) else Files.move(source, destination)
    },
    private val cleanupStrategy: (Path) -> Unit = ::deleteEvidenceTree,
) {
    private val root = repositoryRoot.toAbsolutePath().normalize()
    private val rootReal: Path

    init {
        require(SOURCE_COMMIT.matches(sourceCommit)) { "source commit must be a single safe path component" }
        Files.createDirectories(root)
        require(!Files.isSymbolicLink(root)) { "repository root cannot be a symlink" }
        rootReal = root.toRealPath(NOFOLLOW_LINKS)
    }

    constructor(repositoryRoot: java.io.File, sourceCommit: String, clock: Clock = Clock.systemUTC()) :
        this(repositoryRoot.toPath(), sourceCommit, clock)

    internal fun writeGeneratedStrict(
        descriptor: EvidenceSceneDescriptor,
        observation: SceneObservation,
        expectedRgba: ByteArray?,
        attemptId: String = observation.routeAttemptId() ?: "attempt-1",
        checkedInPngBytes: ByteArray? = null,
        expectedRouteId: String,
    ): Path = writeGeneratedBundle(descriptor, observation, expectedRgba, attemptId, checkedInPngBytes) { temp ->
        writeBundleV1(temp, descriptor, observation, expectedRgba, attemptId, checkedInPngBytes)
        val verification = EvidenceBundleVerifier.verify(
            temp,
            EvidenceVerificationExpectation(sourceCommit, descriptor, expectedRgba, checkedInPngBytes, expectedRouteId),
        )
        require(verification is EvidenceBundleVerification.Verified) {
            val errors = (verification as EvidenceBundleVerification.Invalid).errors.joinToString("; ")
            "generated evidence bundle failed independent verification: $errors"
        }
    }

    fun writeGeneratedV2(
        descriptor: EvidenceSceneDescriptor,
        observation: SceneObservation,
        expectedRgba: ByteArray? = null,
        attemptId: String = observation.routeAttemptId() ?: "attempt-1",
        checkedInPngBytes: ByteArray? = null,
    ): Path = writeGeneratedBundle(descriptor, observation, expectedRgba, attemptId, checkedInPngBytes) { temp ->
        writeBundleV2(temp, descriptor, observation, expectedRgba, attemptId, checkedInPngBytes)
    }

    private fun writeGeneratedBundle(
        descriptor: EvidenceSceneDescriptor,
        observation: SceneObservation,
        expectedRgba: ByteArray?,
        attemptId: String,
        checkedInPngBytes: ByteArray?,
        writeStagingBundle: (Path) -> Unit,
    ): Path {
        require(SAFE_COMPONENT.matches(attemptId)) { "attempt id must be a single safe path component" }
        require(observation !is SceneObservation.Unavailable) { "unavailable observations cannot produce bundles" }
        require(observation.environment.sourceCommit == sourceCommit) { "observation sourceCommit does not match writer sourceCommit" }
        val destination = destination(descriptor.id.value)
        var stagingRoot: Path? = null
        var primaryFailure: Throwable? = null
        return try {
            stagingRoot = siblingTemp(destination)
            val temp = stagingRoot.resolve(destination.fileName)
            Files.createDirectory(temp)
            writeStagingBundle(temp)
            moveIntoPlace(temp, destination)
            destination
        } catch (failure: Throwable) {
            primaryFailure = failure
            runCatching { retainFailure(descriptor, observation, attemptId, failure) }
                .exceptionOrNull()
                ?.let(failure::addSuppressed)
            throw failure
        } finally {
            stagingRoot?.let { path ->
                try {
                    cleanupStrategy(path)
                } catch (cleanupFailure: Throwable) {
                    primaryFailure?.addSuppressed(cleanupFailure) ?: throw cleanupFailure
                }
            }
        }
    }

    fun writeGenerated(
        evidenceCase: EvidenceCase,
        observation: SceneObservation,
        expectedRgba: ByteArray? = null,
        attemptId: String = observation.routeAttemptId() ?: "attempt-1",
        checkedInPngBytes: ByteArray? = null,
    ): Path = writeGeneratedStrict(
        descriptor = evidenceCase.descriptor,
        observation = observation,
        expectedRgba = expectedRgba,
        attemptId = attemptId,
        checkedInPngBytes = checkedInPngBytes,
        expectedRouteId = when (val program = evidenceCase.program) {
            is KanvasSurfaceProgram -> program.routeId
            is RoutedSceneProgram -> program.routeId
            else -> error("catalog scene program must carry a route id")
        },
    )

    private fun destination(sceneId: String): Path {
        require(!sceneId.contains('/')) { "scene id cannot contain path separators" }
        val path = root.resolve("reports/gpu-renderer/evidence/correctness/generated")
            .resolve(sourceCommit).resolve(sceneId).normalize()
        require(path.parent != null && path.parent!!.parent != null)
        require(path.startsWith(root)) { "evidence destination escapes repository root" }
        require(!path.toString().contains("/promoted/")) { "promoted evidence is not writable" }
        return path
    }

    private fun writeBundleV1(
        directory: Path,
        descriptor: EvidenceSceneDescriptor,
        observation: SceneObservation,
        expectedRgba: ByteArray?,
        attemptId: String,
        checkedInPngBytes: ByteArray?,
    ) {
        writeBundle(
            directory = directory,
            descriptor = descriptor,
            observation = observation,
            expectedRgba = expectedRgba,
            attemptId = attemptId,
            checkedInPngBytes = checkedInPngBytes,
            includeEnvironment = true,
        ) { expectation, observed, hashes ->
            EvidenceManifest(
                GPU_EVIDENCE_SCHEMA,
                descriptor.id.value,
                expectation,
                observed,
                sourceCommit,
                clock.instant().toString(),
                oracleKind(descriptor.oracle),
                oracleId(descriptor.oracle),
                oracleVersion(descriptor.oracle),
                hashes,
                oracleProvenance(descriptor.oracle),
                oracleSha256(descriptor.oracle),
            ).toJson().canonicalBytes()
        }
    }

    private fun writeBundleV2(
        directory: Path,
        descriptor: EvidenceSceneDescriptor,
        observation: SceneObservation,
        expectedRgba: ByteArray?,
        attemptId: String,
        checkedInPngBytes: ByteArray?,
    ) {
        writeBundle(
            directory = directory,
            descriptor = descriptor,
            observation = observation,
            expectedRgba = expectedRgba,
            attemptId = attemptId,
            checkedInPngBytes = checkedInPngBytes,
            includeEnvironment = false,
        ) { expectation, observed, hashes ->
            EvidenceManifestV2(
                GPU_EVIDENCE_SCENE_SCHEMA_V2,
                descriptor.id.value,
                expectation,
                observed,
                oracleKind(descriptor.oracle),
                oracleId(descriptor.oracle),
                oracleVersion(descriptor.oracle),
                hashes,
                oracleProvenance(descriptor.oracle),
                oracleSha256(descriptor.oracle),
            ).toJson().canonicalBytes()
        }
    }

    private fun writeBundle(
        directory: Path,
        descriptor: EvidenceSceneDescriptor,
        observation: SceneObservation,
        expectedRgba: ByteArray?,
        attemptId: String,
        checkedInPngBytes: ByteArray?,
        includeEnvironment: Boolean,
        manifestBytes: (expectation: String, observed: String, hashes: Map<String, String>) -> ByteArray,
    ) {
        val files = linkedMapOf<String, ByteArray>()
        val rendered = observation as? SceneObservation.Rendered
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
            val policy = descriptor.comparison
            val oracleIsCheckedIn = descriptor.oracle is OraclePolicy.CheckedInPng
            val skiaBytes = if (oracleIsCheckedIn) {
                val original = requireNotNull(checkedInPngBytes) { "CheckedInPng requires original PNG bytes" }
                require(sha256Hex(original) == descriptor.oracle.sha256) { "checked-in PNG bytes do not match oracle sha256" }
                original.copyOf()
            } else null
            files[if (oracleIsCheckedIn) "skia.png" else "cpu.png"] = skiaBytes ?: cpuPng
            files["gpu.png"] = gpuPng
            files["diff.png"] = pngBytes(diff, descriptor.width, descriptor.height)
            files["stats.json"] = EvidenceStats(
                descriptor.width, descriptor.height, "rgba8unorm", "encoded-premul-srgb",
                policy?.perChannelTolerance ?: 0, policy?.minimumSimilarityPercent ?: 100.0,
                rendered.comparison.similarityPercent, rendered.comparison.differingPixels,
                rendered.comparison.maxChannelDifference, rendered.comparison.meanChannelDifference,
                rendered.comparison.passed,
            ).toJson().canonicalBytes()
        } else {
            files["stats.json"] = EvidenceStats(
                descriptor.width, descriptor.height, "rgba8unorm", "encoded-premul-srgb",
                descriptor.comparison?.perChannelTolerance ?: 0,
                descriptor.comparison?.minimumSimilarityPercent ?: 100.0,
                100.0, 0, 0, 0.0, true,
            ).toJson().canonicalBytes()
        }
        files["route.json"] = routeJson(observation.route(), attemptId)
        files["diagnostics.json"] = diagnosticsJson(observation, attemptId)
        if (includeEnvironment) files["environment.json"] = environmentJson(observation.environment)
        val verdict = EvidenceExpectationGate.evaluate(descriptor, observation)
        files["verdict.json"] = EvidenceVerdictRecord(expectation, observed, verdict.kind(), verdict.reason()).toJson().canonicalBytes()
        val hashes = files.mapValues { sha256Hex(it.value) }
        files["manifest.json"] = manifestBytes(expectation, observed, hashes)
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
        ensureNoSymlinkComponents(destination.parent!!)
        var current = root
        val relative = root.relativize(destination.parent!!)
        for (index in 0 until relative.nameCount) {
            current = current.resolve(relative.getName(index).toString())
            require(!Files.isSymbolicLink(current)) { "evidence destination contains a symlink" }
        }
        Files.createDirectories(destination.parent)
        require(destination.parent!!.toRealPath(NOFOLLOW_LINKS).startsWith(rootReal)) { "destination parent escapes repository root" }
        return Files.createTempDirectory(destination.parent, ".${destination.fileName}.tmp-")
    }

    private fun moveIntoPlace(temp: Path, destination: Path) {
        require(!Files.isSymbolicLink(destination)) { "evidence destination cannot be a symlink" }
        if (Files.exists(destination, NOFOLLOW_LINKS)) {
            require(Files.isDirectory(destination, NOFOLLOW_LINKS)) { "evidence destination must be a directory" }
            deleteEvidenceTree(destination)
        }
        try {
            moveStrategy(temp, destination, true)
        } catch (_: AtomicMoveNotSupportedException) {
            moveStrategy(temp, destination, false)
        }
    }

    private fun retainFailure(descriptor: EvidenceSceneDescriptor, observation: SceneObservation, attemptId: String, failure: Throwable) {
        val failedParent = root.resolve("reports/gpu-renderer/evidence/correctness/generated")
            .resolve(sourceCommit)
            .resolve("_failed")
            .normalize()
        require(failedParent.startsWith(root)) { "failure destination escapes repository root" }
        ensureNoSymlinkComponents(failedParent)
        Files.createDirectories(failedParent)
        require(!Files.isSymbolicLink(failedParent)) { "failure destination cannot be a symlink" }

        val failed = failedParent.resolve("${descriptor.id.value}-$attemptId").normalize()
        require(failed.parent == failedParent) { "failure attempt escapes generated evidence root" }
        if (Files.exists(failed, NOFOLLOW_LINKS)) {
            require(!Files.isSymbolicLink(failed)) { "failure attempt cannot be a symlink" }
            return
        }
        Files.createDirectory(failed)
        Files.write(
            failed.resolve("diagnostics.json"),
            diagnosticsJson(observation, attemptId, failure.message ?: failure::class.simpleName.orEmpty()),
            CREATE_NEW,
            WRITE,
        )
        Files.write(
            failed.resolve("environment.json"),
            environmentJson(observation.environment),
            CREATE_NEW,
            WRITE,
        )
    }

    private fun ensureNoSymlinkComponents(path: Path) {
        require(path.startsWith(root)) { "path escapes repository root" }
        var current = root
        val relative = root.relativize(path)
        for (index in 0 until relative.nameCount) {
            current = current.resolve(relative.getName(index).toString())
            require(!Files.isSymbolicLink(current)) { "path contains a symlink" }
        }
        if (Files.exists(path, NOFOLLOW_LINKS)) require(path.toRealPath(NOFOLLOW_LINKS).startsWith(rootReal)) { "path escapes repository root" }
    }

    private fun routeJson(route: RouteEvidence, attemptId: String): ByteArray = buildJsonObject {
        put("routeId", route.routeId); put("attemptId", attemptId)
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
        put("message", (observation as? SceneObservation.Refused)?.message); put("submissionDelta", when (observation) { is SceneObservation.Rendered -> observation.route.runtimeTelemetryDelta.submissions; is SceneObservation.Refused -> observation.submissionDelta; is SceneObservation.Unavailable -> 0L })
        if (extra != null) put("writeFailure", extra)
    }.canonicalBytes()

    internal fun environmentJson(e: EvidenceEnvironment): ByteArray = buildJsonObject {
        put("sourceCommit", e.sourceCommit); put("osName", e.osName); put("osVersion", e.osVersion); put("osArchitecture", e.osArchitecture); put("javaVersion", e.javaVersion); put("deviceGeneration", e.deviceGeneration); put("capabilityImplementation", e.capabilityImplementation); put("available", e.available)
        put("adapter", e.adapter?.let { buildJsonObject { put("summary", it.summary); put("vendor", it.vendor); put("device", it.device); put("architecture", it.architecture); put("description", it.description); put("isFallbackAdapter", it.isFallbackAdapter) } } ?: JsonNull)
    }.canonicalBytes()

    private fun oracleKind(o: OraclePolicy) = when (o) { is OraclePolicy.GeneratedCpu -> "generated-cpu"; is OraclePolicy.CheckedInPng -> "checked-in-png"; OraclePolicy.StableRefusal -> "stable-refusal" }
    private fun oracleId(o: OraclePolicy) = when (o) { is OraclePolicy.GeneratedCpu -> o.oracleId; is OraclePolicy.CheckedInPng -> o.resourcePath; OraclePolicy.StableRefusal -> "stable-refusal" }
    private fun oracleVersion(o: OraclePolicy) = when (o) { is OraclePolicy.GeneratedCpu -> o.version; else -> 1 }
    private fun oracleProvenance(o: OraclePolicy) = when (o) { is OraclePolicy.CheckedInPng -> o.provenance; is OraclePolicy.GeneratedCpu -> "generated-cpu"; OraclePolicy.StableRefusal -> "stable-refusal" }
    private fun oracleSha256(o: OraclePolicy) = (o as? OraclePolicy.CheckedInPng)?.sha256
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

internal fun environmentJsonV2(e: EvidenceEnvironment): ByteArray = EvidenceEnvironmentV2(
    schemaVersion = GPU_EVIDENCE_CATALOG_SCHEMA_V2,
    osName = e.osName,
    osVersion = e.osVersion,
    osArchitecture = e.osArchitecture,
    javaVersion = e.javaVersion,
    deviceGeneration = e.deviceGeneration,
    capabilityImplementation = e.capabilityImplementation,
    available = e.available,
    adapter = e.adapter,
).toJson().canonicalBytes()

internal fun sha256Hex(value: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(value).joinToString("") { "%02x".format(it) }

private fun deleteEvidenceTree(path: Path) {
    if (!Files.exists(path, NOFOLLOW_LINKS)) return
    if (Files.isDirectory(path, NOFOLLOW_LINKS)) {
        Files.list(path).use { stream -> stream.forEach(::deleteEvidenceTree) }
    }
    Files.deleteIfExists(path)
}
