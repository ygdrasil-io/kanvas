package org.graphiks.kanvas.gpu.evidence.runner

import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.Collections
import java.util.IdentityHashMap
import kotlin.system.exitProcess
import org.graphiks.kanvas.gpu.evidence.artifacts.EvidenceBundleWriter
import org.graphiks.kanvas.gpu.evidence.artifacts.EvidenceCatalogWriter
import org.graphiks.kanvas.gpu.evidence.artifacts.EvidenceSelection
import org.graphiks.kanvas.gpu.evidence.artifacts.EvidenceSelectionParser
import org.graphiks.kanvas.gpu.evidence.artifacts.resolve
import org.graphiks.kanvas.gpu.evidence.catalog.GpuEvidenceCatalog
import org.graphiks.kanvas.gpu.evidence.catalog.OraclePolicy
import org.graphiks.kanvas.gpu.evidence.catalog.SceneObservation
import org.graphiks.kanvas.gpu.evidence.gate.EvidenceExpectationGate
import org.graphiks.kanvas.gpu.evidence.gate.EvidenceVerdict
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendSession

fun main(args: Array<String>): Unit = exitProcess(GpuEvidenceCliRunner(ProductEvidenceRuntimePort()).run(args))

interface EvidenceRuntimePort { fun open(): EvidenceBackendPort?; fun close(); fun dispose() }

class GpuEvidenceCliRunner(
    private val runtime: EvidenceRuntimePort,
    private val requestParser: (Array<String>) -> GpuEvidenceCliRequest = GpuEvidenceCliRequest::parse,
    private val cases: List<org.graphiks.kanvas.gpu.evidence.catalog.EvidenceCase> = GpuEvidenceCatalog.cases,
    private val checkedInPngLoader: (String) -> ByteArray = ::loadCheckedInPngBytes,
    private val writeGeneratedCatalog: (Path, Path, EvidenceSelection, Map<String, SceneObservation>, Map<String, Path>) -> Path =
        { stagingRepositoryRoot, generatedRoot, selection, observations, bundlePaths ->
            EvidenceCatalogWriter(stagingRepositoryRoot).writeGeneratedCatalog(generatedRoot, selection, observations, bundlePaths)
        },
) {
    fun run(args: Array<String>): Int = runResult(args).exitCode

    /** Visible to injected contract tests so a primary failure and its cleanup failures remain inspectable. */
    internal fun runResult(args: Array<String>): EvidenceCliRunResult {
        val request = try {
            requestParser(args)
        } catch (failure: Exception) {
            System.err.println("gpu evidence arguments rejected: ${failure.message}")
            return EvidenceCliRunResult(2, null)
        }
        val selectedCases = try {
            request.selection.resolve(cases)
        } catch (failure: Exception) {
            System.err.println("gpu evidence arguments rejected: ${failure.message}")
            return EvidenceCliRunResult(2, null)
        }
        val failures = mutableListOf<Throwable>()
        var primaryFailure: Throwable? = null
        var exitCode = 1
        try {
            val backend = runtime.open()
            if (backend == null) {
                System.err.println("gpu evidence unavailable: unavailable.gpu.backend: GPU backend runtime could not create a session.")
            } else {
                val publisher = GeneratedEvidenceRootPublisher(request.repositoryRoot, request.sourceCommit)
                val stagingRepositoryRoot = publisher.createStagingRepositoryRoot()
                try {
                    val executor = EvidenceCaseExecutor(backend, request.sourceCommit)
                    val writer = EvidenceBundleWriter(stagingRepositoryRoot, request.sourceCommit)
                    val observations = linkedMapOf<String, SceneObservation>()
                    val bundlePaths = linkedMapOf<String, Path>()
                    exitCode = selectedCases.fold(0) { code, evidenceCase ->
                        when (val result = executor.execute(evidenceCase)) {
                            is EvidenceExecutionResult.ExecutionFailure -> {
                                System.err.println("gpu evidence ${evidenceCase.descriptor.id.value} execution failed: ${result.stableReasonCode}: ${result.message}")
                                1
                            }
                            is EvidenceExecutionResult.Observed -> when (val observation = result.observation) {
                                is SceneObservation.Unavailable -> {
                                    System.err.println("gpu evidence unavailable: ${observation.stableReasonCode}: ${observation.message}")
                                    1
                                }
                                else -> {
                                    when (val verdict = EvidenceExpectationGate.evaluate(evidenceCase.descriptor, observation)) {
                                        is EvidenceVerdict.Pass -> {
                                            val expected = (observation as? SceneObservation.Rendered)?.let {
                                                requireNotNull(evidenceCase.oracle).render(evidenceCase.descriptor.width, evidenceCase.descriptor.height)
                                            }
                                            val checkedInPngBytes = (evidenceCase.descriptor.oracle as? OraclePolicy.CheckedInPng)
                                                ?.let { checkedInPngLoader(it.resourcePath) }
                                            val bundlePath = writer.writeGeneratedV2(
                                                descriptor = evidenceCase.descriptor,
                                                observation = observation,
                                                expectedRgba = expected,
                                                checkedInPngBytes = checkedInPngBytes,
                                            )
                                            observations[evidenceCase.descriptor.id.value] = observation
                                            bundlePaths[evidenceCase.descriptor.id.value] = bundlePath
                                            code
                                        }
                                        is EvidenceVerdict.Fail -> {
                                            System.err.println("gpu evidence ${evidenceCase.descriptor.id.value} failed: ${verdict.reason}")
                                            1
                                        }
                                        is EvidenceVerdict.Unavailable -> {
                                            System.err.println("gpu evidence unavailable: ${verdict.reason}")
                                            1
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (exitCode == 0) {
                        val generatedRoot = publisher.generatedRoot(stagingRepositoryRoot)
                        writeGeneratedCatalog(stagingRepositoryRoot, generatedRoot, request.selection, observations, bundlePaths)
                        publisher.publish(stagingRepositoryRoot)
                    }
                } finally {
                    publisher.cleanupStagingRepositoryRoot(stagingRepositoryRoot)
                }
            }
        } catch (failure: Exception) {
            primaryFailure = failure
            recordDistinctFailure(failures, failure)
            System.err.println("gpu evidence failed: ${failure.message}")
            exitCode = 1
        } catch (failure: Error) {
            primaryFailure = failure
            recordDistinctFailure(failures, failure)
        } finally {
            try {
                runtime.close()
            } catch (failure: Exception) {
                recordDistinctFailure(failures, failure)
            } catch (failure: Error) {
                recordDistinctFailure(failures, failure)
            }
            try {
                runtime.dispose()
            } catch (failure: Exception) {
                recordDistinctFailure(failures, failure)
            } catch (failure: Error) {
                recordDistinctFailure(failures, failure)
            }
        }
        val rootFailure = failures.firstOrNull { it is Error } ?: failures.firstOrNull()
        if (rootFailure == null) return EvidenceCliRunResult(exitCode, null)
        attachDistinctFailures(rootFailure, failures)
        if (rootFailure is Error) throw rootFailure
        if (primaryFailure == null) System.err.println("gpu evidence cleanup failed: ${rootFailure.message}")
        return EvidenceCliRunResult(1, rootFailure)
    }
}

private fun recordDistinctFailure(failures: MutableList<Throwable>, failure: Throwable) {
    if (failures.none { it === failure }) failures += failure
}

private fun attachDistinctFailures(root: Throwable, failures: List<Throwable>) {
    failures.forEach { failure ->
        when {
            failure === root -> Unit
            reachesFailureGraph(root, failure) -> Unit
            reachesFailureGraph(failure, root) -> root.addSuppressed(snapshotToAvoidFailureGraphCycle(failure))
            else -> root.addSuppressed(failure)
        }
    }
}

private fun reachesFailureGraph(source: Throwable, target: Throwable): Boolean {
    val pending = ArrayDeque<Throwable>()
    val visited = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
    pending.addLast(source)
    while (pending.isNotEmpty()) {
        val current = pending.removeLast()
        if (!visited.add(current)) continue
        if (current === target) return true
        current.cause?.let(pending::addLast)
        current.suppressed.forEach(pending::addLast)
    }
    return false
}

private fun snapshotToAvoidFailureGraphCycle(failure: Throwable): Exception =
    Exception("failure snapshotted to avoid a cycle: ${failure.javaClass.name}: ${failure.message}")

internal data class EvidenceCliRunResult(val exitCode: Int, val failure: Throwable?)

private class ProductEvidenceRuntimePort : EvidenceRuntimePort {
    private var session: GPUBackendSession? = null
    override fun open(): EvidenceBackendPort? = GPUBackendRuntimeFactory.createOrNull()?.also { session = it }?.let(::ProductEvidenceBackendPort)
    override fun close() { session?.close(); session = null }
    override fun dispose() = GPUBackendRuntimeFactory.dispose()
}

private fun loadCheckedInPngBytes(resourcePath: String): ByteArray {
    val candidates = listOf(resourcePath, resourcePath.removePrefix("/")).distinct()
    candidates.forEach { candidate ->
        Thread.currentThread().contextClassLoader?.getResourceAsStream(candidate)?.use { return it.readBytes() }
        GpuEvidenceCliRunner::class.java.classLoader.getResourceAsStream(candidate)?.use { return it.readBytes() }
        val absolute = if (candidate.startsWith('/')) candidate else "/$candidate"
        GpuEvidenceCliRunner::class.java.getResourceAsStream(absolute)?.use { return it.readBytes() }
    }
    error("checked-in PNG resource not found: $resourcePath")
}

internal class GeneratedEvidenceRootPublisher(
    repositoryRoot: Path,
    private val sourceCommit: String,
    private val moveStrategy: (Path, Path, Boolean) -> Unit = ::defaultGeneratedRootMove,
    private val cleanupStrategy: (Path) -> Unit = ::deleteGeneratedPublicationTree,
    private val diagnostic: (String) -> Unit = { message -> System.err.println(message) },
) {
    private val repositoryRoot = repositoryRoot.toAbsolutePath().normalize()
    private val repositoryRootReal: Path

    init {
        Files.createDirectories(this.repositoryRoot)
        require(!Files.isSymbolicLink(this.repositoryRoot)) { "repository root cannot be a symlink" }
        repositoryRootReal = this.repositoryRoot.toRealPath(NOFOLLOW_LINKS)
    }

    fun createStagingRepositoryRoot(): Path {
        ensureNoSymlinkComponents(repositoryRoot)
        return Files.createTempDirectory(repositoryRoot, ".gpu-evidence-stage-")
    }

    fun generatedRoot(repositoryRoot: Path): Path {
        val root = repositoryRoot.toAbsolutePath().normalize()
        val path = root.resolve("reports/gpu-renderer/evidence/correctness/generated").resolve(sourceCommit).normalize()
        require(path.startsWith(root)) { "generated evidence path escapes repository root" }
        return path
    }

    fun publish(stagingRepositoryRoot: Path): Path {
        val stagedRoot = generatedRoot(stagingRepositoryRoot)
        require(Files.isDirectory(stagedRoot, NOFOLLOW_LINKS) && !Files.isSymbolicLink(stagedRoot)) {
            "staged generated root does not exist: $stagedRoot"
        }
        val destination = generatedRoot(repositoryRoot)
        val parent = destination.parent ?: error("generated root has no parent")
        Files.createDirectories(parent)
        ensureNoSymlinkComponents(parent)
        swapRoot(stagedRoot, destination)
        return destination
    }

    fun cleanupStagingRepositoryRoot(stagingRepositoryRoot: Path) {
        if (Files.exists(stagingRepositoryRoot, NOFOLLOW_LINKS)) cleanupStrategy(stagingRepositoryRoot)
    }

    private fun swapRoot(staged: Path, destination: Path) {
        require(!Files.isSymbolicLink(destination)) { "generated evidence root cannot be a symlink" }
        val hadDestination = Files.exists(destination, NOFOLLOW_LINKS)
        if (hadDestination) {
            require(Files.isDirectory(destination, NOFOLLOW_LINKS)) { "generated evidence root must be a directory" }
        }
        val parent = destination.parent ?: error("generated root has no parent")
        var snapshot: Path? = null
        var snapshotReady = false
        var backup: Path? = null
        var destinationMoveAttempted = false
        var installAttempted = false
        var restored = false
        var installed = false
        try {
            if (hadDestination) {
                val snapshotRoot = Files.createTempDirectory(parent, ".${destination.fileName}.snapshot-")
                snapshot = snapshotRoot
                copyTree(destination, snapshotRoot)
                verifySnapshot(destination, snapshotRoot)
                snapshotReady = true
                backup = Files.createTempDirectory(parent, ".${destination.fileName}.backup-")
                destinationMoveAttempted = true
                moveStrategy(destination, backup.resolve(destination.fileName.toString()), true)
            }
            installAttempted = true
            moveStrategy(staged, destination, true)
            installed = true
        } catch (failure: Throwable) {
            if (hadDestination && snapshotReady && destinationMoveAttempted) {
                val backupDestination = backup?.resolve(destination.fileName.toString())
                val independentSnapshot = requireNotNull(snapshot)
                val verifiedBackup = backupDestination?.takeIf { isVerifiedSnapshot(independentSnapshot, it) }
                if (verifiedBackup != null) {
                    try {
                        if (Files.exists(destination, NOFOLLOW_LINKS)) deleteGeneratedPublicationTree(destination)
                        moveStrategy(verifiedBackup, destination, true)
                        restored = true
                    } catch (restoreFailure: Throwable) {
                        failure.addSuppressed(restoreFailure)
                        diagnostic("generated evidence rollback failed; verified backup retained at $backup")
                    }
                } else {
                    try {
                        if (Files.exists(destination, NOFOLLOW_LINKS)) deleteGeneratedPublicationTree(destination)
                        copyTree(independentSnapshot, destination)
                        verifySnapshot(independentSnapshot, destination)
                        restored = true
                    } catch (restoreFailure: Throwable) {
                        failure.addSuppressed(restoreFailure)
                        diagnostic("generated evidence rollback failed; independent snapshot retained at $snapshot; backup retained at $backup")
                    }
                }
            } else if (!hadDestination && installAttempted) {
                try {
                    if (Files.exists(destination, NOFOLLOW_LINKS)) deleteGeneratedPublicationTree(destination)
                } catch (cleanupFailure: Throwable) {
                    failure.addSuppressed(cleanupFailure)
                    diagnostic("generated evidence initial install cleanup failed; incomplete destination retained at $destination")
                }
            }
            throw failure
        } finally {
            if (restored || installed) {
                runCatching { backup?.let(cleanupStrategy) }
                    .onFailure { cleanupFailure ->
                        diagnostic("generated evidence publication succeeded; backup retained at $backup: ${cleanupFailure.message}")
                    }
                runCatching { snapshot?.let(::deleteGeneratedPublicationTree) }
                    .onFailure { cleanupFailure ->
                        diagnostic("generated evidence publication succeeded; snapshot retained at $snapshot: ${cleanupFailure.message}")
                    }
            } else if (!destinationMoveAttempted && !installAttempted) {
                runCatching { backup?.let(cleanupStrategy) }
                    .onFailure { cleanupFailure -> diagnostic("generated evidence setup cleanup failed; backup retained at $backup: ${cleanupFailure.message}") }
                runCatching { snapshot?.let(cleanupStrategy) }
                    .onFailure { cleanupFailure -> diagnostic("generated evidence setup cleanup failed; snapshot retained at $snapshot: ${cleanupFailure.message}") }
            }
        }
    }

    private fun copyTree(source: Path, destination: Path) {
        require(Files.isDirectory(source, NOFOLLOW_LINKS) && !Files.isSymbolicLink(source)) {
            "generated evidence source is not a regular directory: $source"
        }
        require(destination.normalize().startsWith(repositoryRoot)) { "generated evidence path escapes repository root" }
        Files.walk(source).use { stream ->
            stream.forEach { current ->
                require(!Files.isSymbolicLink(current)) { "generated evidence contains a symlink" }
                val relative = source.relativize(current)
                val target = destination.resolve(relative).normalize()
                require(target.startsWith(destination)) { "generated evidence path escapes source root" }
                if (Files.isDirectory(current, NOFOLLOW_LINKS)) Files.createDirectories(target)
                else Files.copy(current, target)
            }
        }
    }

    private fun verifySnapshot(expected: Path, actual: Path) {
        require(Files.isDirectory(actual, NOFOLLOW_LINKS) && !Files.isSymbolicLink(actual)) {
            "generated evidence snapshot is not a regular directory: $actual"
        }
        require(regularFilesByRelativePath(expected) == regularFilesByRelativePath(actual)) {
            "generated evidence snapshot does not match the original root"
        }
    }

    private fun isVerifiedSnapshot(expected: Path, candidate: Path?): Boolean =
        candidate != null && Files.exists(candidate, NOFOLLOW_LINKS) &&
            runCatching {
                verifySnapshot(expected, candidate)
                true
            }.getOrDefault(false)

    private fun regularFilesByRelativePath(root: Path): Map<String, List<Byte>> =
        Files.walk(root).use { stream ->
            stream.iterator().asSequence()
                .filter { Files.isRegularFile(it, NOFOLLOW_LINKS) }
                .associate { path ->
                    root.relativize(path).toString().replace('\\', '/') to Files.readAllBytes(path).toList()
                }
        }

    private fun ensureNoSymlinkComponents(path: Path) {
        require(path.startsWith(repositoryRoot)) { "path escapes repository root" }
        var current = repositoryRoot
        val relative = repositoryRoot.relativize(path)
        for (index in 0 until relative.nameCount) {
            current = current.resolve(relative.getName(index).toString())
            require(!Files.isSymbolicLink(current)) { "path contains a symlink" }
        }
        if (Files.exists(path, NOFOLLOW_LINKS)) require(path.toRealPath(NOFOLLOW_LINKS).startsWith(repositoryRootReal)) {
            "path escapes repository root"
        }
    }
}

private fun defaultGeneratedRootMove(source: Path, destination: Path, atomic: Boolean) {
    if (!atomic) {
        Files.move(source, destination, REPLACE_EXISTING)
        return
    }
    try {
        Files.move(source, destination, ATOMIC_MOVE)
    } catch (_: AtomicMoveNotSupportedException) {
        Files.move(source, destination, REPLACE_EXISTING)
    }
}

private fun deleteGeneratedPublicationTree(path: Path) {
    if (!Files.exists(path, NOFOLLOW_LINKS)) return
    if (Files.isDirectory(path, NOFOLLOW_LINKS)) Files.list(path).use { stream -> stream.forEach(::deleteGeneratedPublicationTree) }
    Files.deleteIfExists(path)
}

data class GpuEvidenceCliRequest(
    val repositoryRoot: Path,
    val sourceCommit: String,
    val selection: EvidenceSelection,
) {
    val sceneId: String?
        get() = (selection as? EvidenceSelection.Explicit)?.sceneIds?.singleOrNull()

    companion object {
        private val SHA = Regex("[0-9a-f]{40}")

        fun parse(args: Array<String>): GpuEvidenceCliRequest {
            var repositoryRoot: String? = null
            var sourceCommit: String? = null
            var all = false
            var scenesFile: Path? = null
            val sceneIds = mutableListOf<String>()
            var index = 0
            while (index < args.size) {
                when (args[index]) {
                    "--repository-root" -> {
                        require(repositoryRoot == null) { "duplicate --repository-root" }
                        repositoryRoot = value(args, ++index, "--repository-root")
                    }
                    "--source-commit" -> {
                        require(sourceCommit == null) { "duplicate --source-commit" }
                        sourceCommit = value(args, ++index, "--source-commit")
                    }
                    "--scene" -> sceneIds += value(args, ++index, "--scene")
                    "--scenes-file" -> {
                        require(scenesFile == null) { "duplicate --scenes-file" }
                        scenesFile = Path.of(value(args, ++index, "--scenes-file"))
                    }
                    "--all" -> {
                        require(!all) { "duplicate --all" }
                        all = true
                    }
                    else -> error("unknown argument: ${args[index]}")
                }
                index++
            }
            val root = Path.of(requireNotNull(repositoryRoot) { "--repository-root is required" }).toAbsolutePath().normalize()
            require(root.isAbsolute && Files.isDirectory(root)) { "repository root must be an existing directory" }
            val commit = requireNotNull(sourceCommit) { "--source-commit is required" }
            require(SHA.matches(commit) && commit.any { it != '0' }) { "source commit must be 40 lowercase hexadecimal characters" }
            scenesFile?.let { sceneIds += EvidenceSelectionParser.readSceneFile(it) }
            val selection = EvidenceSelectionParser.from(sceneIds, all)
            return GpuEvidenceCliRequest(root, commit, selection)
        }

        private fun value(args: Array<String>, index: Int, flag: String): String {
            require(index < args.size && !args[index].startsWith("--")) { "$flag requires a value" }
            return args[index]
        }
    }
}
