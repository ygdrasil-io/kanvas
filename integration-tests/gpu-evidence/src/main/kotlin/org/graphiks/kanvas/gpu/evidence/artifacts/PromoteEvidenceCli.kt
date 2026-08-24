package org.graphiks.kanvas.gpu.evidence.artifacts

import java.io.PrintStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.time.Clock
import java.time.Instant
import kotlin.system.exitProcess
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.graphiks.kanvas.gpu.evidence.catalog.GpuEvidenceCatalog

/** Explicit, review-gated promotion of independently verified GPU evidence. */
fun main(args: Array<String>): Unit = exitProcess(PromoteEvidenceCliRunner().run(args))

data class PromoteEvidenceCliRequest(
    val repositoryRoot: Path,
    val sourceCommit: String,
    val reviewer: String,
    val reason: String,
    val rebaseline: Boolean,
    val priorComparison: String?,
    val newComparison: String?,
) {
    companion object {
        private val SOURCE_COMMIT = Regex("[0-9a-f]{40}")

        fun parse(args: Array<String>): PromoteEvidenceCliRequest {
            var repositoryRoot: String? = null
            var sourceCommit: String? = null
            var reviewer: String? = null
            var reason: String? = null
            var all = false
            var rebaseline = false
            var prior: String? = null
            var next: String? = null
            var index = 0
            while (index < args.size) {
                when (args[index]) {
                    "--repository-root" -> { require(repositoryRoot == null) { "duplicate --repository-root" }; repositoryRoot = value(args, ++index, "--repository-root") }
                    "--source-commit" -> { require(sourceCommit == null) { "duplicate --source-commit" }; sourceCommit = value(args, ++index, "--source-commit") }
                    "--reviewer" -> { require(reviewer == null) { "duplicate --reviewer" }; reviewer = value(args, ++index, "--reviewer") }
                    "--reason" -> { require(reason == null) { "duplicate --reason" }; reason = value(args, ++index, "--reason") }
                    "--all" -> { require(!all) { "duplicate --all" }; all = true }
                    "--rebaseline" -> { require(!rebaseline) { "duplicate --rebaseline" }; rebaseline = true }
                    "--prior-comparison", "--old-comparison", "--prior-comparison-summary" -> { require(prior == null) { "duplicate prior comparison" }; prior = value(args, ++index, args[index]) }
                    "--new-comparison", "--new-comparison-summary" -> { require(next == null) { "duplicate new comparison" }; next = value(args, ++index, args[index]) }
                    else -> error("unknown argument: ${args[index]}")
                }
                index++
            }
            require(all) { "--all is required; promotion never accepts an arbitrary scene or destination" }
            val root = Path.of(requireNotNull(repositoryRoot) { "--repository-root is required" }).toAbsolutePath().normalize()
            require(root.isAbsolute && Files.isDirectory(root, NOFOLLOW_LINKS)) { "repository root must be an existing directory" }
            require(!Files.isSymbolicLink(root)) { "repository root cannot be a symlink" }
            val commit = requireNotNull(sourceCommit) { "--source-commit is required" }
            require(SOURCE_COMMIT.matches(commit)) { "source commit must be 40 lowercase hexadecimal characters" }
            val actualReviewer = requireNotNull(reviewer) { "--reviewer is required" }
            val actualReason = requireNotNull(reason) { "--reason is required" }
            require(actualReviewer.isNotBlank()) { "--reviewer must not be blank" }
            require(actualReason.isNotBlank()) { "--reason must not be blank" }
            require((prior == null) == (next == null)) { "prior and new comparison summaries must be provided together" }
            if (prior != null) require(prior.isNotBlank() && next!!.isNotBlank()) { "comparison summaries must not be blank" }
            if (rebaseline) require(prior != null && next != null) { "--rebaseline requires prior and new comparison summaries" }
            return PromoteEvidenceCliRequest(root, commit, actualReviewer, actualReason, rebaseline, prior, next)
        }

        private fun value(args: Array<String>, index: Int, flag: String): String {
            require(index < args.size && !args[index].startsWith("--")) { "$flag requires a value" }
            return args[index]
        }
    }
}

private fun defaultPromotionMove(source: Path, destination: Path, atomic: Boolean) {
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

private fun defaultPromotionCleanup(path: Path) {
    if (!Files.exists(path, NOFOLLOW_LINKS)) return
    if (Files.isDirectory(path, NOFOLLOW_LINKS)) Files.list(path).use { stream -> stream.forEach(::defaultPromotionCleanup) }
    Files.deleteIfExists(path)
}

class PromoteEvidenceCliRunner internal constructor(
    private val stdout: PrintStream = System.out,
    private val stderr: PrintStream = System.err,
    private val clock: Clock = Clock.systemUTC(),
    private val moveStrategy: (Path, Path, Boolean) -> Unit = ::defaultPromotionMove,
    private val beforeStagedVerification: (Path) -> Unit = {},
    private val cleanupStrategy: (Path) -> Unit = ::defaultPromotionCleanup,
) {
    fun run(args: Array<String>): Int {
        val request = try {
            PromoteEvidenceCliRequest.parse(args)
        } catch (failure: Exception) {
            stderr.println("gpu evidence promotion arguments rejected: ${failure.message}")
            return 2
        }
        return try {
            promote(request)
            0
        } catch (failure: Exception) {
            stderr.println("gpu evidence promotion rejected: ${failure.message}")
            failure.suppressed.forEach { detail ->
                stderr.println("gpu evidence promotion detail: ${detail.message}")
            }
            1
        }
    }

    private fun promote(request: PromoteEvidenceCliRequest) {
        val roots = canonicalRoots(request.repositoryRoot, request.sourceCommit)
        ensureNoSymlinkComponents(request.repositoryRoot, roots.generated)
        ensureNoSymlinkComponents(request.repositoryRoot, roots.promoted.parent)
        require(Files.isDirectory(roots.generated, NOFOLLOW_LINKS)) { "generated evidence root does not exist: ${roots.generated}" }
        require(!Files.isSymbolicLink(roots.generated)) { "generated evidence root cannot be a symlink" }

        // Verification happens before any destination mutation, and does not create a GPU runtime.
        require(VerifyEvidenceCliRunner(stdout, stderr).run(arrayOf("--root", roots.generated.toString(), "--source-commit", request.sourceCommit)) == 0) {
            "generated evidence failed independent verification"
        }

        val sceneIds = GpuEvidenceCatalog.cases.map { it.descriptor.id.value }
        preflightPromotedRoot(roots.promoted, request.rebaseline)
        Files.createDirectories(roots.promoted.parent)
        val staged = Files.createTempDirectory(roots.promoted.parent, ".promoted.staged-")
        var swapped = false
        var primaryFailure: Throwable? = null
        try {
            sceneIds.forEach { sceneId ->
                val source = roots.generated.resolve(sceneId)
                val stagedScene = staged.resolve(sceneId)
                copyTree(source, stagedScene, request.repositoryRoot)
                writePromotion(stagedScene, sceneId, request)
            }
            beforeStagedVerification(staged)
            require(VerifyEvidenceCliRunner(stdout, stderr).run(arrayOf("--root", staged.toString(), "--source-commit", request.sourceCommit)) == 0) {
                "staged promotion failed independent verification"
            }
            swapCatalogRoot(staged, roots.promoted)
            swapped = true
        } catch (failure: Throwable) {
            primaryFailure = failure
            throw failure
        } finally {
            if (!swapped) {
                try {
                    cleanupStrategy(staged)
                } catch (cleanupFailure: Throwable) {
                    if (primaryFailure != null) {
                        primaryFailure.addSuppressed(cleanupFailure)
                    } else {
                        throw cleanupFailure
                    }
                }
            }
        }
        stdout.println("promoted ${sceneIds.size} GPU evidence scenes from ${request.sourceCommit}")
    }

    private fun preflightPromotedRoot(promoted: Path, rebaseline: Boolean) {
        if (!Files.exists(promoted, NOFOLLOW_LINKS)) {
            require(!rebaseline) { "rebaseline requires an existing promoted catalog" }
            return
        }
        require(!Files.isSymbolicLink(promoted)) { "promoted evidence root cannot be a symlink" }
        require(Files.isDirectory(promoted, NOFOLLOW_LINKS)) { "promoted evidence root must be a directory" }
        val entries = Files.list(promoted).use { stream -> stream.iterator().asSequence().toList() }
        if (!rebaseline) {
            require(entries.isEmpty()) { "destination already contains evidence; use --rebaseline with old/new comparison summaries" }
            return
        }
        require(entries.isNotEmpty()) { "rebaseline requires a non-empty promoted catalog" }
        require(VerifyEvidenceCliRunner(stdout, stderr).run(arrayOf("--root", promoted.toString(), "--allow-historical-commit")) == 0) {
            "existing promoted evidence is not an exact verified catalog"
        }
    }

    private fun canonicalRoots(repositoryRoot: Path, sourceCommit: String): PromotionRoots {
        val evidence = repositoryRoot.resolve("reports/gpu-renderer/evidence").normalize()
        val generated = evidence.resolve("correctness/generated").resolve(sourceCommit).normalize()
        val promoted = evidence.resolve("correctness/promoted").normalize()
        require(generated.startsWith(evidence) && promoted.startsWith(evidence)) { "canonical evidence path escapes reports/gpu-renderer/evidence" }
        return PromotionRoots(generated, promoted)
    }

    private fun copyTree(source: Path, destination: Path, repositoryRoot: Path) {
        require(Files.isDirectory(source, NOFOLLOW_LINKS) && !Files.isSymbolicLink(source)) { "source scene is not a directory: $source" }
        require(destination.normalize().startsWith(repositoryRoot)) { "staging path escapes repository root" }
        Files.walk(source).use { stream ->
            stream.forEach { current ->
                require(!Files.isSymbolicLink(current)) { "source evidence contains a symlink" }
                val relative = source.relativize(current)
                val target = destination.resolve(relative).normalize()
                require(target.startsWith(destination)) { "source evidence path escapes scene" }
                if (Files.isDirectory(current, NOFOLLOW_LINKS)) Files.createDirectories(target)
                else Files.copy(current, target)
            }
        }
    }

    private fun writePromotion(directory: Path, sceneId: String, request: PromoteEvidenceCliRequest) {
        val json = buildJsonObject {
            put("schemaVersion", GPU_EVIDENCE_PROMOTION_SCHEMA)
            put("sceneId", sceneId)
            put("sourceCommit", request.sourceCommit)
            put("promotedAtUtc", clock.instant().toString())
            put("reviewer", request.reviewer)
            put("reason", request.reason)
            put("rebaseline", request.rebaseline)
            put("priorComparison", request.priorComparison?.let(::JsonPrimitive) ?: JsonNull)
            put("newComparison", request.newComparison?.let(::JsonPrimitive) ?: JsonNull)
        }
        Files.writeString(directory.resolve("promotion.json"), json.toString())
    }

    private fun swapCatalogRoot(staged: Path, destination: Path) {
        val parent = destination.parent ?: error("promoted root has no parent")
        val backup = if (Files.exists(destination, NOFOLLOW_LINKS)) Files.createTempDirectory(parent, ".promoted.backup-") else null
        var restored = false
        var installed = false
        try {
            if (backup != null) moveStrategy(destination, backup.resolve(destination.fileName.toString()), true)
            moveStrategy(staged, destination, true)
            installed = true
        } catch (failure: Throwable) {
            if (backup != null && !Files.exists(destination, NOFOLLOW_LINKS)) {
                try {
                    moveStrategy(backup.resolve(destination.fileName.toString()), destination, true)
                    restored = true
                } catch (restoreFailure: Throwable) {
                    failure.addSuppressed(restoreFailure)
                    stderr.println("promotion rollback failed; backup retained at $backup")
                }
            }
            throw failure
        } finally {
            if (restored) {
                runCatching { backup?.let(::deleteTree) }
                    .onFailure { cleanupFailure -> stderr.println("promotion restored old catalog; backup retained at $backup: ${cleanupFailure.message}") }
            } else if (installed && backup != null) {
                runCatching { deleteTree(backup) }
                    .onFailure { cleanupFailure -> stderr.println("promotion installed new catalog; backup retained at $backup: ${cleanupFailure.message}") }
            }
        }
    }

    private fun ensureNoSymlinkComponents(root: Path, path: Path) {
        require(path.normalize().startsWith(root.normalize())) { "path escapes repository root" }
        var current = root
        val relative = root.relativize(path)
        for (index in 0 until relative.nameCount) {
            current = current.resolve(relative.getName(index).toString())
            require(!Files.isSymbolicLink(current)) { "path contains a symlink: $current" }
        }
    }

    private fun deleteTree(path: Path) {
        if (!Files.exists(path, NOFOLLOW_LINKS)) return
        if (Files.isDirectory(path, NOFOLLOW_LINKS)) Files.list(path).use { stream -> stream.forEach(::deleteTree) }
        Files.deleteIfExists(path)
    }

    private data class PromotionRoots(val generated: Path, val promoted: Path)
}
