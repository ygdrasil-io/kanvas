package org.graphiks.kanvas.gpu.evidence.artifacts

import java.io.PrintStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.time.Clock
import kotlin.system.exitProcess
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.graphiks.kanvas.gpu.evidence.catalog.GpuEvidenceCatalog

/** Explicit, review-gated promotion of independently verified GPU evidence. */
fun main(args: Array<String>): Unit = exitProcess(PromoteEvidenceCliRunner().run(args))

data class PromoteEvidenceCliRequest(
    val repositoryRoot: Path,
    val sourceCommit: String,
    val selection: EvidenceSelection,
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
            val sceneIds = mutableListOf<String>()
            var scenesFile: Path? = null
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
                    "--scene" -> sceneIds += value(args, ++index, "--scene")
                    "--scenes-file" -> {
                        require(scenesFile == null) { "duplicate --scenes-file" }
                        scenesFile = Path.of(value(args, ++index, "--scenes-file")).toAbsolutePath().normalize()
                    }
                    "--all" -> { require(!all) { "duplicate --all" }; all = true }
                    "--rebaseline" -> { require(!rebaseline) { "duplicate --rebaseline" }; rebaseline = true }
                    "--prior-comparison", "--old-comparison", "--prior-comparison-summary" -> { require(prior == null) { "duplicate prior comparison" }; prior = value(args, ++index, args[index]) }
                    "--new-comparison", "--new-comparison-summary" -> { require(next == null) { "duplicate new comparison" }; next = value(args, ++index, args[index]) }
                    else -> error("unknown argument: ${args[index]}")
                }
                index++
            }
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
            require(rebaseline || (prior == null && next == null)) { "comparison summaries require --rebaseline" }
            if (rebaseline) require(prior != null && next != null) { "--rebaseline requires prior and new comparison summaries" }
            if (rebaseline) require(all) { "--rebaseline requires --all" }
            scenesFile?.let { sceneIds += EvidenceSelectionParser.readSceneFile(it) }
            val selection = EvidenceSelectionParser.from(sceneIds, all)
            if (selection is EvidenceSelection.Explicit) selection.resolve(GpuEvidenceCatalog.cases)
            return PromoteEvidenceCliRequest(root, commit, selection, actualReviewer, actualReason, rebaseline, prior, next)
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
            promoteSelected(request)
            0
        } catch (failure: Exception) {
            stderr.println("gpu evidence promotion rejected: ${failure.message}")
            failure.suppressed.forEach { detail ->
                stderr.println("gpu evidence promotion detail: ${detail.message}")
            }
            1
        }
    }

    internal fun promoteSelected(request: PromoteEvidenceCliRequest) {
        val roots = canonicalRoots(request.repositoryRoot, request.sourceCommit)
        ensureNoSymlinkComponents(request.repositoryRoot, roots.generated)
        ensureNoSymlinkComponents(request.repositoryRoot, roots.promoted.parent)
        require(Files.isDirectory(roots.generated, NOFOLLOW_LINKS)) { "generated evidence root does not exist: ${roots.generated}" }
        require(!Files.isSymbolicLink(roots.generated)) { "generated evidence root cannot be a symlink" }

        // Verification happens before any destination mutation, and does not create a GPU runtime.
        require(VerifyEvidenceCliRunner(stdout, stderr).run(verificationArguments(roots.generated, request.sourceCommit, request.selection)) == 0) {
            "generated evidence failed independent verification"
        }
        val generated = validateCatalogRoot(roots.generated, request.selection, request.sourceCommit)
        val existing = preflightPromotedRoot(roots.promoted, request)
        val sceneIds = selectedSceneIds(request.selection)
        val environmentBytes = when {
            existing == null -> generated.environmentBytes
            request.selection is EvidenceSelection.Explicit -> {
                require(existing.environmentBytes.contentEquals(generated.environmentBytes)) {
                    EvidenceCatalogVerifier.ENVIRONMENT_MISMATCH_REQUIRES_REBASELINE
                }
                existing.environmentBytes
            }
            else -> generated.environmentBytes
        }
        val catalogEntries = when (existing) {
            null -> generated.entriesBySceneId.values.sortedBy(EvidenceCatalogEntry::sceneId)
            else -> mergeEntries(existing, generated, sceneIds)
        }
        Files.createDirectories(roots.promoted.parent)
        val staged = Files.createTempDirectory(roots.promoted.parent, ".promoted.staged-")
        var swapped = false
        var primaryFailure: Throwable? = null
        try {
            if (existing != null) {
                copyTree(roots.promoted, staged, request.repositoryRoot)
            }
            sceneIds.forEach { sceneId ->
                val source = roots.generated.resolve(sceneId)
                val stagedScene = staged.resolve(sceneId)
                deleteTree(stagedScene)
                copyTree(source, stagedScene, request.repositoryRoot)
            }
            writeRootMetadata(staged, environmentBytes, catalogEntries, request, sceneIds)
            beforeStagedVerification(staged)
            verifyStagedPromotionMetadata(staged, request, sceneIds)
            require(VerifyEvidenceCliRunner(stdout, stderr).run(verificationArguments(staged, null, EvidenceSelection.All)) == 0) {
                "staged promotion failed independent verification"
            }
            existing?.let {
                verifyNoUnrelatedChanges(
                    promoted = roots.promoted,
                    staged = staged,
                    request = request,
                    selectedSceneIds = sceneIds,
                )
            }
            swapCatalogRoot(staged, roots.promoted, request.repositoryRoot)
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

    private fun preflightPromotedRoot(promoted: Path, request: PromoteEvidenceCliRequest): ValidatedCatalogRoot? {
        if (!Files.exists(promoted, NOFOLLOW_LINKS)) {
            require(!request.rebaseline) { "rebaseline requires an existing promoted catalog" }
            require(request.selection == EvidenceSelection.All) { "selected promotion requires an existing promoted catalog" }
            return null
        }
        require(!Files.isSymbolicLink(promoted)) { "promoted evidence root cannot be a symlink" }
        require(Files.isDirectory(promoted, NOFOLLOW_LINKS)) { "promoted evidence root must be a directory" }
        val entries = Files.list(promoted).use { stream -> stream.iterator().asSequence().toList() }
        if (entries.isEmpty()) {
            require(!request.rebaseline) { "rebaseline requires an existing promoted catalog" }
            require(request.selection == EvidenceSelection.All) { "selected promotion requires an existing promoted catalog" }
            return null
        }
        // A catalogue may legitimately predate a newly added scene. Validate every
        // scene it declares before staging the full rebaseline, rather than asking
        // the old root to already contain the scene being promoted.
        val existingSceneIds = readCatalogEntries(promoted).map(EvidenceCatalogEntry::sceneId)
        require(existingSceneIds.isNotEmpty()) { "promoted catalog contains no scenes" }
        val existing = validateCatalogRoot(
            promoted,
            EvidenceSelection.Explicit(existingSceneIds),
            null,
            requirePromotion = true,
        )
        if (request.selection == EvidenceSelection.All) {
            require(request.rebaseline) { "destination already contains evidence; use --all --rebaseline with old/new comparison summaries" }
        } else {
            require(!request.rebaseline) { "--rebaseline requires --all" }
        }
        return existing
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

    private fun verificationArguments(root: Path, sourceCommit: String?, selection: EvidenceSelection): Array<String> = buildList {
        add("--root")
        add(root.toString())
        if (sourceCommit != null) {
            add("--source-commit")
            add(sourceCommit)
        }
        when (selection) {
            EvidenceSelection.All -> add("--all")
            is EvidenceSelection.Explicit -> selection.sceneIds.forEach { sceneId ->
                add("--scene")
                add(sceneId)
            }
        }
    }.toTypedArray()

    private fun validateCatalogRoot(
        root: Path,
        selection: EvidenceSelection,
        expectedSourceCommit: String?,
        requirePromotion: Boolean = false,
    ): ValidatedCatalogRoot {
        val verification = EvidenceCatalogVerifier.verify(
            root = root,
            selection = selection,
            cases = GpuEvidenceCatalog.cases,
            expectedSourceCommit = expectedSourceCommit,
            requirePromotion = requirePromotion,
        )
        return ValidatedCatalogRoot(
            entriesBySceneId = readCatalogEntries(root).associateBy(EvidenceCatalogEntry::sceneId),
            environmentBytes = verification.environment.toJson().canonicalBytes(),
        )
    }

    private fun readCatalogEntries(root: Path): List<EvidenceCatalogEntry> {
        val catalog = EvidenceJson.parseToJsonElement(Files.readString(root.resolve("catalog.json"))).jsonObject
        return catalog["scenes"]!!.jsonArray.map { entry ->
            val scene = entry.jsonObject
            EvidenceCatalogEntry(
                sceneId = scene["sceneId"]!!.jsonPrimitive.content,
                sourceCommit = scene["sourceCommit"]!!.jsonPrimitive.content,
                manifest = scene["manifest"]!!.jsonPrimitive.content,
                manifestSha256 = scene["manifestSha256"]!!.jsonPrimitive.content,
            )
        }.sortedBy(EvidenceCatalogEntry::sceneId)
    }

    private fun selectedSceneIds(selection: EvidenceSelection): List<String> = when (selection) {
        EvidenceSelection.All -> GpuEvidenceCatalog.cases.map { it.descriptor.id.value }.sorted()
        is EvidenceSelection.Explicit -> selection.sceneIds
    }

    private fun mergeEntries(
        existing: ValidatedCatalogRoot,
        generated: ValidatedCatalogRoot,
        sceneIds: List<String>,
    ): List<EvidenceCatalogEntry> {
        return GpuEvidenceCatalog.cases.map { it.descriptor.id.value }.sorted().map { sceneId ->
            if (sceneId in sceneIds) {
                generated.entriesBySceneId.getValue(sceneId)
            } else {
                existing.entriesBySceneId.getValue(sceneId)
            }
        }
    }

    private fun writeRootMetadata(
        staged: Path,
        environmentBytes: ByteArray,
        entries: List<EvidenceCatalogEntry>,
        request: PromoteEvidenceCliRequest,
        sceneIds: List<String>,
    ) {
        Files.write(staged.resolve("environment.json"), environmentBytes)
        Files.write(
            staged.resolve("catalog.json"),
            EvidenceCatalogV2(
                schemaVersion = GPU_EVIDENCE_CATALOG_SCHEMA_V2,
                environment = "environment.json",
                promotion = "promotion.json",
                scenes = entries,
            ).toJson().canonicalBytes(),
        )
        Files.write(
            staged.resolve("promotion.json"),
            EvidencePromotionV2(
                schemaVersion = GPU_EVIDENCE_PROMOTION_SCHEMA_V2,
                promotedAtUtc = clock.instant().toString(),
                reviewer = request.reviewer,
                reason = request.reason,
                rebaseline = request.rebaseline,
                sceneIds = sceneIds,
                priorComparison = request.priorComparison,
                newComparison = request.newComparison,
            ).toJson().canonicalBytes(),
        )
    }

    private fun verifyStagedPromotionMetadata(
        staged: Path,
        request: PromoteEvidenceCliRequest,
        sceneIds: List<String>,
    ) {
        val promotion = EvidenceJson.parseToJsonElement(Files.readString(staged.resolve("promotion.json"))).jsonObject
        val actualSceneIds = promotion["sceneIds"]!!.jsonArray.map { it.jsonPrimitive.content }.sorted()
        require(actualSceneIds == sceneIds.sorted()) { "promotion sceneIds do not match the requested selection" }
        require(promotion["reviewer"]!!.jsonPrimitive.content == request.reviewer) { "promotion reviewer does not match the request" }
        require(promotion["reason"]!!.jsonPrimitive.content == request.reason) { "promotion reason does not match the request" }
        require(promotion["rebaseline"]!!.jsonPrimitive.boolean == request.rebaseline) {
            "promotion rebaseline flag does not match the request"
        }
        val actualPrior = promotion["priorComparison"]?.jsonPrimitive?.contentOrNull
        val actualNew = promotion["newComparison"]?.jsonPrimitive?.contentOrNull
        require(actualPrior == request.priorComparison) { "promotion priorComparison does not match the request" }
        require(actualNew == request.newComparison) { "promotion newComparison does not match the request" }
        if (request.rebaseline) {
            require(!actualPrior.isNullOrBlank() && !actualNew.isNullOrBlank()) {
                "promotion rebaseline metadata must include nonblank comparison summaries"
            }
        }
    }

    private fun verifyNoUnrelatedChanges(
        promoted: Path,
        staged: Path,
        request: PromoteEvidenceCliRequest,
        selectedSceneIds: List<String>,
    ) {
        val allowedRootPaths = buildSet {
            add("catalog.json")
            add("promotion.json")
            if (request.selection == EvidenceSelection.All && request.rebaseline) add("environment.json")
        }
        val allowedScenePrefixes = selectedSceneIds.map { "$it/" }
        val changed = changedRegularFiles(before = promoted, after = staged).filterNot { relativePath ->
            relativePath in allowedRootPaths || allowedScenePrefixes.any(relativePath::startsWith)
        }
        require(changed.isEmpty()) {
            "staged promotion modified unrelated paths: ${changed.sorted().joinToString(", ")}"
        }
    }

    private fun changedRegularFiles(before: Path, after: Path): Set<String> {
        val beforeFiles = regularFilesByRelativePath(before)
        val afterFiles = regularFilesByRelativePath(after)
        return (beforeFiles.keys + afterFiles.keys).filterTo(sortedSetOf()) { relativePath ->
            val previous = beforeFiles[relativePath]
            val next = afterFiles[relativePath]
            previous == null || next == null || !previous.contentEquals(next)
        }
    }

    private fun regularFilesByRelativePath(root: Path): Map<String, ByteArray> =
        Files.walk(root).use { stream ->
            stream.iterator().asSequence()
                .filter { Files.isRegularFile(it, NOFOLLOW_LINKS) }
                .associate { path ->
                    root.relativize(path).toString().replace('\\', '/') to Files.readAllBytes(path)
                }
        }

    private fun swapCatalogRoot(staged: Path, destination: Path, repositoryRoot: Path) {
        val parent = destination.parent ?: error("promoted root has no parent")
        val hadDestination = Files.exists(destination, NOFOLLOW_LINKS)
        var snapshot: Path? = null
        var snapshotReady = false
        var backup: Path? = null
        var destinationMoveAttempted = false
        var installAttempted = false
        var restored = false
        var installed = false
        try {
            if (hadDestination) {
                val snapshotRoot = Files.createTempDirectory(parent, ".promoted.snapshot-")
                snapshot = snapshotRoot
                copyTree(destination, snapshotRoot, repositoryRoot)
                verifySnapshot(destination, snapshotRoot)
                snapshotReady = true
                backup = Files.createTempDirectory(parent, ".promoted.backup-")
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
                        if (Files.exists(destination, NOFOLLOW_LINKS)) deleteTree(destination)
                        moveStrategy(verifiedBackup, destination, true)
                        restored = true
                    } catch (restoreFailure: Throwable) {
                        failure.addSuppressed(restoreFailure)
                        try {
                            if (Files.exists(destination, NOFOLLOW_LINKS)) deleteTree(destination)
                            copyTree(independentSnapshot, destination, repositoryRoot)
                            verifySnapshot(independentSnapshot, destination)
                            restored = true
                            stderr.println("promotion backup restore failed; independent snapshot restored the old catalog")
                        } catch (snapshotRestoreFailure: Throwable) {
                            failure.addSuppressed(snapshotRestoreFailure)
                            try {
                                if (Files.exists(destination, NOFOLLOW_LINKS)) deleteTree(destination)
                            } catch (cleanupFailure: Throwable) {
                                failure.addSuppressed(cleanupFailure)
                            }
                            stderr.println("promotion rollback failed; independent snapshot and backup retained at $snapshot and $backup")
                        }
                    }
                } else {
                    try {
                        if (Files.exists(destination, NOFOLLOW_LINKS)) deleteTree(destination)
                        copyTree(independentSnapshot, destination, repositoryRoot)
                        verifySnapshot(independentSnapshot, destination)
                        restored = true
                    } catch (restoreFailure: Throwable) {
                        failure.addSuppressed(restoreFailure)
                        stderr.println("promotion rollback failed; independent snapshot retained at $snapshot; backup retained at $backup")
                    }
                }
            } else if (!hadDestination && installAttempted) {
                try {
                    if (Files.exists(destination, NOFOLLOW_LINKS)) deleteTree(destination)
                } catch (cleanupFailure: Throwable) {
                    failure.addSuppressed(cleanupFailure)
                    stderr.println("promotion initial install cleanup failed; incomplete destination retained at $destination")
                }
            }
            throw failure
        } finally {
            if (restored || installed) {
                runCatching { backup?.let(::deleteTree) }
                    .onFailure { cleanupFailure -> stderr.println("promotion publication succeeded; backup retained at $backup: ${cleanupFailure.message}") }
                runCatching { snapshot?.let(::deleteTree) }
                    .onFailure { cleanupFailure -> stderr.println("promotion publication succeeded; snapshot retained at $snapshot: ${cleanupFailure.message}") }
            } else if (!destinationMoveAttempted && !installAttempted) {
                runCatching { backup?.let(::deleteTree) }
                    .onFailure { cleanupFailure -> stderr.println("promotion setup cleanup failed; backup retained at $backup: ${cleanupFailure.message}") }
                runCatching { snapshot?.let(::deleteTree) }
                    .onFailure { cleanupFailure -> stderr.println("promotion setup cleanup failed; snapshot retained at $snapshot: ${cleanupFailure.message}") }
            }
        }
    }

    private fun verifySnapshot(expected: Path, actual: Path) {
        require(Files.isDirectory(actual, NOFOLLOW_LINKS) && !Files.isSymbolicLink(actual)) {
            "promotion snapshot is not a regular directory: $actual"
        }
        require(changedRegularFiles(expected, actual).isEmpty()) {
            "promotion snapshot does not match the original catalog"
        }
    }

    private fun isVerifiedSnapshot(expected: Path, candidate: Path?): Boolean =
        candidate != null && Files.exists(candidate, NOFOLLOW_LINKS) &&
            runCatching {
                verifySnapshot(expected, candidate)
                true
            }.getOrDefault(false)

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

    private data class ValidatedCatalogRoot(
        val entriesBySceneId: Map<String, EvidenceCatalogEntry>,
        val environmentBytes: ByteArray,
    )
}
