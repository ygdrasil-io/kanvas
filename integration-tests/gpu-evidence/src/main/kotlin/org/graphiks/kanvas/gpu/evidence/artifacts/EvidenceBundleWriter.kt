package org.graphiks.kanvas.gpu.evidence.artifacts

import java.io.IOException
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemoryLayout
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandle
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.DirectoryStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.ByteBuffer
import java.nio.channels.WritableByteChannel
import java.nio.file.SecureDirectoryStream
import java.security.MessageDigest
import java.time.Clock
import java.util.UUID
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

class EvidenceBundleWriter internal constructor(
    repositoryRoot: Path,
    private val sourceCommit: String,
    private val clock: Clock = Clock.systemUTC(),
    private val moveStrategy: (Path, Path, Boolean) -> Unit = { source, destination, atomic ->
        if (atomic) Files.move(source, destination, ATOMIC_MOVE) else Files.move(source, destination)
    },
    private val secureFilesystem: SecureEvidenceFilesystem,
) {
    constructor(
        repositoryRoot: Path,
        sourceCommit: String,
        clock: Clock = Clock.systemUTC(),
        moveStrategy: (Path, Path, Boolean) -> Unit = { source, destination, atomic ->
            if (atomic) Files.move(source, destination, ATOMIC_MOVE) else Files.move(source, destination)
        },
    ) : this(repositoryRoot, sourceCommit, clock, moveStrategy, UnixSecureEvidenceFilesystem())

    private val root = repositoryRoot.toAbsolutePath().normalize()
    private val rootReal: Path

    init {
        require(SOURCE_COMMIT.matches(sourceCommit)) { "source commit must be a single safe path component" }
        Files.createDirectories(root)
        require(!Files.isSymbolicLink(root)) { "repository root cannot be a symlink" }
        rootReal = root.toRealPath(NOFOLLOW_LINKS)
        secureFilesystem.verifyAvailable(rootReal)
    }

    constructor(repositoryRoot: java.io.File, sourceCommit: String, clock: Clock = Clock.systemUTC()) :
        this(repositoryRoot.toPath(), sourceCommit, clock)

    fun writeGenerated(
        descriptor: EvidenceSceneDescriptor,
        observation: SceneObservation,
        expectedRgba: ByteArray? = null,
        attemptId: String = observation.routeAttemptId() ?: "attempt-1",
        checkedInPngBytes: ByteArray? = null,
    ): Path {
        require(SAFE_COMPONENT.matches(attemptId)) { "attempt id must be a single safe path component" }
        require(observation !is SceneObservation.Unavailable) { "unavailable observations cannot produce bundles" }
        require(observation.environment.sourceCommit == sourceCommit) { "observation sourceCommit does not match writer sourceCommit" }
        val destination = destination(descriptor.id.value)
        var temp: Path? = null
        return try {
            temp = siblingTemp(destination)
            Files.createDirectories(temp)
            writeBundle(temp, descriptor, observation, expectedRgba, attemptId, checkedInPngBytes)
            moveIntoPlace(temp, destination)
            temp = null
            destination
        } catch (failure: Throwable) {
            try {
                retainFailure(descriptor, observation, attemptId, failure)
            } catch (retentionFailure: SecureEvidenceFilesystemUnavailableException) {
                retentionFailure.addSuppressed(failure)
                throw retentionFailure
            }
            throw failure
        } finally {
            temp?.let { deleteTree(it) }
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
        checkedInPngBytes: ByteArray?,
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
            val policy = descriptor.comparison
            val oracleIsCheckedIn = descriptor.oracle is OraclePolicy.CheckedInPng
            val skiaBytes = if (oracleIsCheckedIn) {
                val original = requireNotNull(checkedInPngBytes) { "CheckedInPng requires original PNG bytes" }
                require(sha256(original) == descriptor.oracle.sha256) { "checked-in PNG bytes do not match oracle sha256" }
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
            clock.instant().toString(), oracleKind(descriptor.oracle), oracleId(descriptor.oracle), oracleVersion(descriptor.oracle), hashes, oracleProvenance(descriptor.oracle), oracleSha256(descriptor.oracle),
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
        if (!Files.exists(destination, NOFOLLOW_LINKS)) {
            try { moveStrategy(temp, destination, true) } catch (_: AtomicMoveNotSupportedException) {
                moveStrategy(temp, destination, false)
            }
            return
        }
        require(Files.isDirectory(destination, NOFOLLOW_LINKS)) { "evidence destination must be a directory" }
        val backup = Files.createTempDirectory(destination.parent, ".${destination.fileName}.backup-")
        deleteTree(backup)
        var backupInstalled = false
        var installSucceeded = false
        var restoreSucceeded = false
        try {
            try { moveStrategy(destination, backup, true) } catch (_: AtomicMoveNotSupportedException) { moveStrategy(destination, backup, false) }
            backupInstalled = true
            try {
                moveStrategy(temp, destination, true)
            } catch (_: AtomicMoveNotSupportedException) {
                moveStrategy(temp, destination, false)
            }
            installSucceeded = true
        } catch (failure: Throwable) {
            if (!Files.exists(destination, NOFOLLOW_LINKS) && Files.exists(backup, NOFOLLOW_LINKS)) {
                restoreSucceeded = runCatching { moveStrategy(backup, destination, true); true }.getOrElse {
                    runCatching { moveStrategy(backup, destination, false); true }.getOrDefault(false)
                }
            }
            throw failure
        } finally {
            if (installSucceeded || (backupInstalled && restoreSucceeded)) {
                if (Files.exists(backup, NOFOLLOW_LINKS)) deleteTree(backup)
            }
        }
    }

    private fun retainFailure(descriptor: EvidenceSceneDescriptor, observation: SceneObservation, attemptId: String, failure: Throwable) {
        try {
            val components = listOf("reports", "gpu-renderer", "evidence", "correctness", "generated", sourceCommit, "_failed", "${descriptor.id.value}-$attemptId")
            val opened = mutableListOf<SecureEvidenceDirectory>()
            try {
                var current = secureFilesystem.openRoot(root).also(opened::add)
                components.forEachIndexed { index, component ->
                    val existing = current.openDirectory(component)
                    if (index == components.lastIndex && existing != null) {
                        existing.close()
                        return
                    }
                    current = existing ?: current.createDirectory(component)
                    opened += current
                }
                current.openNewFile("diagnostics.json").use { channel ->
                    writeFully(channel, diagnosticsJson(observation, attemptId, failure.message ?: failure::class.simpleName.orEmpty()))
                }
                current.openNewFile("environment.json").use { channel ->
                    writeFully(channel, environmentJson(observation.environment))
                }
            } finally {
                opened.asReversed().forEach(SecureEvidenceDirectory::close)
            }
        } catch (unavailable: SecureEvidenceFilesystemUnavailableException) {
            throw unavailable
        } catch (_: Throwable) {
            // The original generation error remains authoritative when no additional path is safely writable.
        }
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

    private fun deleteTree(path: Path) {
        if (!Files.exists(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)) return
        if (Files.isDirectory(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            Files.list(path).use { stream -> stream.forEach { deleteTree(it) } }
        }
        Files.deleteIfExists(path)
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

    private fun environmentJson(e: EvidenceEnvironment): ByteArray = buildJsonObject {
        put("sourceCommit", e.sourceCommit); put("osName", e.osName); put("osVersion", e.osVersion); put("osArchitecture", e.osArchitecture); put("javaVersion", e.javaVersion); put("deviceGeneration", e.deviceGeneration); put("capabilityImplementation", e.capabilityImplementation); put("available", e.available)
        put("adapter", e.adapter?.let { buildJsonObject { put("summary", it.summary); put("vendor", it.vendor); put("device", it.device); put("architecture", it.architecture); put("description", it.description); put("isFallbackAdapter", it.isFallbackAdapter) } } ?: JsonNull)
    }.canonicalBytes()

    private fun oracleKind(o: OraclePolicy) = when (o) { is OraclePolicy.GeneratedCpu -> "generated-cpu"; is OraclePolicy.CheckedInPng -> "checked-in-png"; OraclePolicy.StableRefusal -> "stable-refusal" }
    private fun oracleId(o: OraclePolicy) = when (o) { is OraclePolicy.GeneratedCpu -> o.oracleId; is OraclePolicy.CheckedInPng -> o.resourcePath; OraclePolicy.StableRefusal -> "stable-refusal" }
    private fun oracleVersion(o: OraclePolicy) = when (o) { is OraclePolicy.GeneratedCpu -> o.version; else -> 1 }
    private fun oracleProvenance(o: OraclePolicy) = when (o) { is OraclePolicy.CheckedInPng -> o.provenance; is OraclePolicy.GeneratedCpu -> "generated-cpu"; OraclePolicy.StableRefusal -> "stable-refusal" }
    private fun oracleSha256(o: OraclePolicy) = (o as? OraclePolicy.CheckedInPng)?.sha256
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

/** Narrow handle-relative boundary used only for failure artifact retention. */
internal interface SecureEvidenceFilesystem {
    /** Verifies that secure native filesystem operations are available before any generation starts. */
    fun verifyAvailable(root: Path) {
        openRoot(root).close()
    }

    fun openRoot(root: Path): SecureEvidenceDirectory
}

/** Raised when secure handle-relative retention cannot be provided by this host. */
internal class SecureEvidenceFilesystemUnavailableException(
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause)

internal interface SecureEvidenceDirectory : AutoCloseable {
    /** Opens one existing child directory with NOFOLLOW semantics, or returns null when it is absent. */
    fun openDirectory(name: String): SecureEvidenceDirectory?

    /** Creates one child through this directory's operating-system handle, then opens it with NOFOLLOW semantics. */
    fun createDirectory(name: String): SecureEvidenceDirectory

    /** Opens a new file through this directory's operating-system handle with CREATE_NEW and NOFOLLOW semantics. */
    fun openNewFile(name: String): WritableByteChannel
}

/**
 * Unix implementation backed by public FFM calls only. Java's secure stream is used as a capability
 * gate, but no JDK-private descriptor is extracted from it: libc opens the repository root itself and
 * every child is subsequently addressed through its owned file descriptor.
 */
internal class UnixSecureEvidenceFilesystem(
    private val directoryStreamFactory: (Path) -> DirectoryStream<Path> = { Files.newDirectoryStream(it) },
) : SecureEvidenceFilesystem {
    override fun verifyAvailable(root: Path) {
        requireSecureDirectoryProvider(root)
        PosixEvidenceNativeShim.probe(root)
    }

    override fun openRoot(root: Path): SecureEvidenceDirectory {
        requireSecureDirectoryProvider(root)
        return PosixEvidenceNativeShim.openRoot(root)
    }

    private fun requireSecureDirectoryProvider(root: Path) {
        val stream = try {
            directoryStreamFactory(root)
        } catch (failure: Throwable) {
            throw SecureEvidenceFilesystemUnavailableException(
                "repository provider cannot open a secure directory stream for evidence retention",
                failure,
            )
        }
        stream.use {
            if (it !is SecureDirectoryStream<*>) {
                throw SecureEvidenceFilesystemUnavailableException(
                    "repository provider does not offer secure directory handles for evidence retention",
                )
            }
        }
    }
}

/**
 * Minimal POSIX FFM shim for secure evidence retention. It owns every native descriptor it opens and
 * never derives a descriptor from JDK implementation state.
 */
private object PosixEvidenceNativeShim {
    private val bindings: PosixEvidenceBindings by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        PosixEvidenceBindings.load()
    }

    fun probe(root: Path) {
        try {
            bindings.probe(root)
        } catch (failure: SecureEvidenceFilesystemUnavailableException) {
            throw failure
        } catch (failure: Throwable) {
            throw SecureEvidenceFilesystemUnavailableException(
                "native POSIX secure filesystem capability probe failed before evidence generation",
                failure,
            )
        }
    }

    fun openRoot(root: Path): SecureEvidenceDirectory = try {
        bindings.openRoot(root)
    } catch (failure: SecureEvidenceFilesystemUnavailableException) {
        throw failure
    } catch (failure: Throwable) {
        throw SecureEvidenceFilesystemUnavailableException(
            "native POSIX secure filesystem cannot open the repository root for failure retention",
            failure,
        )
    }
}

private class PosixEvidenceBindings private constructor(
    private val flags: PosixEvidenceFlags,
    private val openHandle: MethodHandle,
    private val openAtHandle: MethodHandle,
    private val mkdirAtHandle: MethodHandle,
    private val closeHandle: MethodHandle,
    private val writeHandle: MethodHandle,
    private val unlinkAtHandle: MethodHandle,
) {
    private val callStateLayout = Linker.Option.captureStateLayout()
    private val errnoOffset = callStateLayout.byteOffset(MemoryLayout.PathElement.groupElement("errno"))

    fun probe(root: Path) {
        val probeDirectoryName = ".kanvas-gpu-evidence-probe-${UUID.randomUUID()}"
        var rootDirectory: NativeEvidenceDirectory? = null
        var probeDirectory: NativeEvidenceDirectory? = null
        var probeFileCreated = false
        var probeDirectoryCreated = false
        var failure: Throwable? = null

        try {
            rootDirectory = openNativeRoot(root)
            probeDirectory = rootDirectory.createDirectory(probeDirectoryName) as NativeEvidenceDirectory
            probeDirectoryCreated = true
            probeDirectory.openNewFile(PROBE_FILE_NAME).use { channel ->
                probeFileCreated = true
                writeFully(channel, PROBE_BYTES)
            }
            probeDirectory.removeFileIfPresent(PROBE_FILE_NAME)
            probeFileCreated = false
            probeDirectory.close()
            probeDirectory = null
            rootDirectory.removeDirectoryIfPresent(probeDirectoryName)
            probeDirectoryCreated = false
        } catch (probeFailure: Throwable) {
            failure = probeFailure
        } finally {
            failure = cleanupProbe(
                failure,
                rootDirectory,
                probeDirectory,
                probeDirectoryName,
                probeFileCreated,
                probeDirectoryCreated,
            )
        }
        failure?.let { throw it }
    }

    fun openRoot(root: Path): SecureEvidenceDirectory = openNativeRoot(root)

    private fun openNativeRoot(root: Path): NativeEvidenceDirectory = NativeEvidenceDirectory(this, openDirectory(root.toString()))

    private fun openDirectoryAt(parentDescriptor: Int, name: String): NativeEvidenceDirectory? {
        requireSafeComponent(name)
        val result = withCString(name) { path -> callInt(openAtHandle, parentDescriptor, path, flags.directoryOpen, 0) }
        return when {
            result.value >= 0 -> NativeEvidenceDirectory(this, result.value)
            result.errno == ERRNO_NO_ENTRY -> null
            else -> throw nativeFailure("openat directory", name, result.errno)
        }
    }

    private fun createDirectoryAt(parentDescriptor: Int, name: String): NativeEvidenceDirectory {
        requireSafeComponent(name)
        val result = withCString(name) { path -> callInt(mkdirAtHandle, parentDescriptor, path, DIRECTORY_MODE) }
        if (result.value == 0) {
            return checkNotNull(openDirectoryAt(parentDescriptor, name)) { "new secure directory was not reopenable" }
        }
        if (result.errno == ERRNO_ALREADY_EXISTS) {
            return openDirectoryAt(parentDescriptor, name)
                ?: throw nativeFailure("open existing directory after mkdirat", name, result.errno)
        }
        throw nativeFailure("mkdirat", name, result.errno)
    }

    fun openNewFileAt(parentDescriptor: Int, name: String): WritableByteChannel {
        requireSafeComponent(name)
        val result = withCString(name) { path -> callInt(openAtHandle, parentDescriptor, path, flags.newFileOpen, FILE_MODE) }
        if (result.value < 0) throw nativeFailure("openat new file", name, result.errno)
        return NativeEvidenceWritableByteChannel(this, result.value)
    }

    fun write(descriptor: Int, source: ByteBuffer): Int {
        if (!source.hasRemaining()) return 0
        val requested = source.remaining()
        val result = Arena.ofConfined().use { arena ->
            val nativeBuffer = arena.allocate(requested.toLong())
            nativeBuffer.copyFrom(MemorySegment.ofBuffer(source.slice()))
            callLong(writeHandle, descriptor, nativeBuffer, requested.toLong())
        }
        if (result.value < 0L) throw nativeFailure("write", "file descriptor $descriptor", result.errno)
        require(result.value <= requested.toLong()) { "native write returned more bytes than requested" }
        source.position(source.position() + result.value.toInt())
        return result.value.toInt()
    }

    fun close(descriptor: Int) {
        val result = callInt(closeHandle, descriptor)
        if (result.value != 0) throw nativeFailure("close", "file descriptor $descriptor", result.errno)
    }

    fun removeFileIfPresent(parentDescriptor: Int, name: String) {
        removeAt(parentDescriptor, name, 0, "unlinkat file")
    }

    fun removeDirectoryIfPresent(parentDescriptor: Int, name: String) {
        removeAt(parentDescriptor, name, flags.removeDirectory, "unlinkat directory")
    }

    private fun removeAt(parentDescriptor: Int, name: String, removalFlag: Int, operation: String) {
        requireSafeComponent(name)
        val result = withCString(name) { path -> callInt(unlinkAtHandle, parentDescriptor, path, removalFlag) }
        if (result.value != 0 && result.errno != ERRNO_NO_ENTRY) throw nativeFailure(operation, name, result.errno)
    }

    private fun openDirectory(path: String): Int {
        val result = withCString(path) { cPath -> callInt(openHandle, cPath, flags.directoryOpen) }
        if (result.value < 0) throw nativeFailure("open repository root", path, result.errno)
        return result.value
    }

    private fun cleanupProbe(
        initialFailure: Throwable?,
        rootDirectory: NativeEvidenceDirectory?,
        probeDirectory: NativeEvidenceDirectory?,
        probeDirectoryName: String,
        probeFileCreated: Boolean,
        probeDirectoryCreated: Boolean,
    ): Throwable? {
        var failure = initialFailure
        fun cleanup(action: () -> Unit) {
            try {
                action()
            } catch (cleanupFailure: Throwable) {
                if (failure == null) failure = cleanupFailure else failure!!.addSuppressed(cleanupFailure)
            }
        }
        if (probeFileCreated) cleanup { probeDirectory?.removeFileIfPresent(PROBE_FILE_NAME) }
        if (probeDirectory != null) cleanup { probeDirectory.close() }
        if (probeDirectoryCreated) cleanup { rootDirectory?.removeDirectoryIfPresent(probeDirectoryName) }
        if (rootDirectory != null) cleanup { rootDirectory.close() }
        return failure
    }

    private fun callInt(handle: MethodHandle, vararg arguments: Any): PosixIntResult = Arena.ofConfined().use { arena ->
        val state = arena.allocate(callStateLayout)
        val value = handle.invokeWithArguments(listOf<Any>(state) + arguments.toList()) as Int
        PosixIntResult(value, state.get(ValueLayout.JAVA_INT, errnoOffset))
    }

    private fun callLong(handle: MethodHandle, vararg arguments: Any): PosixLongResult = Arena.ofConfined().use { arena ->
        val state = arena.allocate(callStateLayout)
        val value = handle.invokeWithArguments(listOf<Any>(state) + arguments.toList()) as Long
        PosixLongResult(value, state.get(ValueLayout.JAVA_INT, errnoOffset))
    }

    private fun <T> withCString(value: String, block: (MemorySegment) -> T): T = Arena.ofConfined().use { arena ->
        block(arena.allocateFrom(value))
    }

    private fun nativeFailure(operation: String, target: String, errno: Int): IOException =
        IOException("$operation failed for secure evidence retention at '$target' (errno=$errno)")

    private fun requireSafeComponent(value: String) {
        require(value.isNotBlank() && value != "." && value != ".." && !value.contains('/') && !value.contains('\\')) {
            "unsafe secure directory component"
        }
    }

    private data class PosixIntResult(val value: Int, val errno: Int)
    private data class PosixLongResult(val value: Long, val errno: Int)

    private class NativeEvidenceDirectory(
        private val bindings: PosixEvidenceBindings,
        private var descriptor: Int,
    ) : SecureEvidenceDirectory {
        override fun openDirectory(name: String): SecureEvidenceDirectory? = bindings.openDirectoryAt(openDescriptor(), name)

        override fun createDirectory(name: String): SecureEvidenceDirectory = bindings.createDirectoryAt(openDescriptor(), name)

        override fun openNewFile(name: String): WritableByteChannel = bindings.openNewFileAt(openDescriptor(), name)

        fun removeFileIfPresent(name: String) = bindings.removeFileIfPresent(openDescriptor(), name)

        fun removeDirectoryIfPresent(name: String) = bindings.removeDirectoryIfPresent(openDescriptor(), name)

        override fun close() {
            val ownedDescriptor = descriptor
            if (ownedDescriptor < 0) return
            descriptor = CLOSED_DESCRIPTOR
            bindings.close(ownedDescriptor)
        }

        private fun openDescriptor(): Int {
            check(descriptor >= 0) { "secure evidence directory is closed" }
            return descriptor
        }
    }

    private class NativeEvidenceWritableByteChannel(
        private val bindings: PosixEvidenceBindings,
        private var descriptor: Int,
    ) : WritableByteChannel {
        override fun isOpen(): Boolean = descriptor >= 0

        override fun write(source: ByteBuffer): Int = bindings.write(openDescriptor(), source)

        override fun close() {
            val ownedDescriptor = descriptor
            if (ownedDescriptor < 0) return
            descriptor = CLOSED_DESCRIPTOR
            bindings.close(ownedDescriptor)
        }

        private fun openDescriptor(): Int {
            check(descriptor >= 0) { "secure evidence file channel is closed" }
            return descriptor
        }
    }

    companion object {
        fun load(): PosixEvidenceBindings {
            val flags = PosixEvidenceFlags.forCurrentOperatingSystem()
            try {
                val linker = Linker.nativeLinker()
                val lookup = linker.defaultLookup()
                fun symbol(name: String): MemorySegment = lookup.find(name).orElseThrow {
                    SecureEvidenceFilesystemUnavailableException("libc symbol '$name' is unavailable for secure evidence retention")
                }
                return PosixEvidenceBindings(
                    flags = flags,
                    openHandle = linker.downcallHandle(
                        symbol("open"),
                        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT),
                        Linker.Option.captureCallState("errno"),
                    ),
                    openAtHandle = linker.downcallHandle(
                        symbol("openat"),
                        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT),
                        Linker.Option.firstVariadicArg(3),
                        Linker.Option.captureCallState("errno"),
                    ),
                    mkdirAtHandle = linker.downcallHandle(
                        symbol("mkdirat"),
                        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT),
                        Linker.Option.captureCallState("errno"),
                    ),
                    closeHandle = linker.downcallHandle(
                        symbol("close"),
                        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT),
                        Linker.Option.captureCallState("errno"),
                    ),
                    writeHandle = linker.downcallHandle(
                        symbol("write"),
                        FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG),
                        Linker.Option.captureCallState("errno"),
                    ),
                    unlinkAtHandle = linker.downcallHandle(
                        symbol("unlinkat"),
                        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT),
                        Linker.Option.captureCallState("errno"),
                    ),
                )
            } catch (failure: SecureEvidenceFilesystemUnavailableException) {
                throw failure
            } catch (failure: Throwable) {
                throw SecureEvidenceFilesystemUnavailableException(
                    "native access or required POSIX secure filesystem symbols are unavailable",
                    failure,
                )
            }
        }

        private const val DIRECTORY_MODE = 448 // 0700
        private const val FILE_MODE = 384 // 0600
        private const val ERRNO_NO_ENTRY = 2
        private const val ERRNO_ALREADY_EXISTS = 17
        private const val CLOSED_DESCRIPTOR = -1
        private const val PROBE_FILE_NAME = "probe.bin"
        private val PROBE_BYTES = byteArrayOf(0x4b, 0x47, 0x50, 0x55)
    }
}

private data class PosixEvidenceFlags(
    val directoryOpen: Int,
    val newFileOpen: Int,
    val removeDirectory: Int,
) {
    companion object {
        fun forCurrentOperatingSystem(): PosixEvidenceFlags {
            val operatingSystem = System.getProperty("os.name").lowercase()
            return when {
                operatingSystem.contains("mac") || operatingSystem.contains("darwin") -> macOs()
                operatingSystem.contains("linux") -> linux()
                else -> throw SecureEvidenceFilesystemUnavailableException(
                    "native POSIX secure evidence retention supports only macOS and Linux (found '${System.getProperty("os.name")}')",
                )
            }
        }

        private fun macOs(): PosixEvidenceFlags {
            val noFollow = 0x00000100
            val create = 0x00000200
            val exclusive = 0x00000800
            val directory = 0x00100000
            val closeOnExec = 0x01000000
            return PosixEvidenceFlags(
                directoryOpen = directory or noFollow or closeOnExec,
                newFileOpen = 0x0001 or create or exclusive or noFollow or closeOnExec,
                removeDirectory = 0x0080,
            )
        }

        private fun linux(): PosixEvidenceFlags {
            val noFollow = 0x00020000
            val create = 0x00000040
            val exclusive = 0x00000080
            val directory = 0x00010000
            val closeOnExec = 0x00080000
            return PosixEvidenceFlags(
                directoryOpen = directory or noFollow or closeOnExec,
                newFileOpen = 0x0001 or create or exclusive or noFollow or closeOnExec,
                removeDirectory = 0x0200,
            )
        }
    }
}

private fun writeFully(channel: WritableByteChannel, bytes: ByteArray) {
    val buffer = ByteBuffer.wrap(bytes)
    while (buffer.hasRemaining()) {
        require(channel.write(buffer) > 0) { "secure file channel made no progress" }
    }
}
