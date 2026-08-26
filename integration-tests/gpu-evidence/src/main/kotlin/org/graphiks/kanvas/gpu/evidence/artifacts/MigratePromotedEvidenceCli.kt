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
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.graphiks.kanvas.gpu.evidence.catalog.EvidenceAdapter
import org.graphiks.kanvas.gpu.evidence.catalog.EvidenceEnvironment
import org.graphiks.kanvas.gpu.evidence.catalog.GpuEvidenceCatalog

/** Mechanical migration of promoted GPU evidence from scene-rooted v1 metadata to root-level v2 metadata. */
fun main(args: Array<String>): Unit = exitProcess(MigratePromotedEvidenceCliRunner().run(args))

data class MigratePromotedEvidenceCliRequest(
    val repositoryRoot: Path,
    val reviewer: String,
    val reason: String,
) {
    companion object {
        fun parse(args: Array<String>): MigratePromotedEvidenceCliRequest {
            var repositoryRoot: String? = null
            var reviewer: String? = null
            var reason: String? = null
            var index = 0
            while (index < args.size) {
                when (args[index]) {
                    "--repository-root" -> {
                        require(repositoryRoot == null) { "duplicate --repository-root" }
                        repositoryRoot = value(args, ++index, "--repository-root")
                    }
                    "--reviewer" -> {
                        require(reviewer == null) { "duplicate --reviewer" }
                        reviewer = value(args, ++index, "--reviewer")
                    }
                    "--reason" -> {
                        require(reason == null) { "duplicate --reason" }
                        reason = value(args, ++index, "--reason")
                    }
                    else -> error("unknown argument: ${args[index]}")
                }
                index++
            }
            val root = Path.of(requireNotNull(repositoryRoot) { "--repository-root is required" }).toAbsolutePath().normalize()
            require(root.isAbsolute && Files.isDirectory(root, NOFOLLOW_LINKS)) { "repository root must be an existing directory" }
            require(!Files.isSymbolicLink(root)) { "repository root cannot be a symlink" }
            val actualReviewer = requireNotNull(reviewer) { "--reviewer is required" }
            val actualReason = requireNotNull(reason) { "--reason is required" }
            require(actualReviewer.isNotBlank()) { "--reviewer must not be blank" }
            require(actualReason.isNotBlank()) { "--reason must not be blank" }
            return MigratePromotedEvidenceCliRequest(root, actualReviewer, actualReason)
        }

        private fun value(args: Array<String>, index: Int, flag: String): String {
            require(index < args.size && !args[index].startsWith("--")) { "$flag requires a value" }
            return args[index]
        }
    }
}

private fun defaultMigrationMove(source: Path, destination: Path, atomic: Boolean) {
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

private fun defaultMigrationCleanup(path: Path) {
    if (!Files.exists(path, NOFOLLOW_LINKS)) return
    if (Files.isDirectory(path, NOFOLLOW_LINKS)) Files.list(path).use { stream -> stream.forEach(::defaultMigrationCleanup) }
    Files.deleteIfExists(path)
}

class MigratePromotedEvidenceCliRunner internal constructor(
    private val stdout: PrintStream = System.out,
    private val stderr: PrintStream = System.err,
    private val clock: Clock = Clock.systemUTC(),
    private val moveStrategy: (Path, Path, Boolean) -> Unit = ::defaultMigrationMove,
    private val beforeStagedVerification: (Path) -> Unit = {},
    private val cleanupStrategy: (Path) -> Unit = ::defaultMigrationCleanup,
) {
    fun run(args: Array<String>): Int {
        val request = try {
            MigratePromotedEvidenceCliRequest.parse(args)
        } catch (failure: Exception) {
            stderr.println("gpu evidence migration arguments rejected: ${failure.message}")
            return 2
        }
        return try {
            migrate(request)
            0
        } catch (failure: Exception) {
            stderr.println("gpu evidence migration rejected: ${failure.message}")
            failure.suppressed.forEach { detail ->
                stderr.println("gpu evidence migration detail: ${detail.message}")
            }
            1
        }
    }

    internal fun migrate(request: MigratePromotedEvidenceCliRequest) {
        val promoted = canonicalPromotedRoot(request.repositoryRoot)
        ensureNoSymlinkComponents(request.repositoryRoot, promoted.parent)
        require(Files.isDirectory(promoted, NOFOLLOW_LINKS)) { "promoted evidence root does not exist: $promoted" }
        require(!Files.isSymbolicLink(promoted)) { "promoted evidence root cannot be a symlink" }
        require(
            VerifyEvidenceCliRunner(stdout, stderr).run(
                arrayOf("--root", promoted.toString(), "--allow-historical-commit", "--all"),
            ) == 0,
        ) { "promoted evidence failed independent v1 verification" }

        val validated = readValidatedV1Root(promoted)
        val staged = Files.createTempDirectory(promoted.parent, ".promoted.v2-staged-")
        var swapped = false
        var primaryFailure: Throwable? = null
        try {
            validated.scenes.forEach { scene ->
                stageScene(scene, promoted.resolve(scene.sceneId), staged.resolve(scene.sceneId), request.repositoryRoot)
            }
            writeRootMetadata(staged, validated, request)
            beforeStagedVerification(staged)
            verifyStagedMetadata(staged, request, validated.sceneIds)
            require(VerifyEvidenceCliRunner(stdout, stderr).run(arrayOf("--root", staged.toString(), "--all")) == 0) {
                "staged migrated evidence failed independent verification"
            }
            swapPromotedRoot(staged, promoted, request.repositoryRoot)
            swapped = true
        } catch (failure: Throwable) {
            primaryFailure = failure
            throw failure
        } finally {
            if (!swapped) {
                try {
                    cleanupStrategy(staged)
                } catch (cleanupFailure: Throwable) {
                    primaryFailure?.addSuppressed(cleanupFailure) ?: throw cleanupFailure
                }
            }
        }
        stdout.println("migrated ${validated.scenes.size} GPU evidence scenes to v2")
    }

    private fun canonicalPromotedRoot(repositoryRoot: Path): Path {
        val evidence = repositoryRoot.resolve("reports/gpu-renderer/evidence").normalize()
        val promoted = evidence.resolve("correctness/promoted").normalize()
        require(promoted.startsWith(evidence)) { "canonical evidence path escapes reports/gpu-renderer/evidence" }
        return promoted
    }

    private fun readValidatedV1Root(root: Path): ValidatedV1Root {
        val sceneIds = GpuEvidenceCatalog.cases.map { it.descriptor.id.value }.sorted()
        val scenes = sceneIds.map { sceneId ->
            val directory = root.resolve(sceneId)
            readScene(directory)
        }
        val environment = scenes.first().environment
        return ValidatedV1Root(
            scenes = scenes,
            sceneIds = sceneIds,
            environmentBytes = environmentJsonV2(environment),
        )
    }

    private fun readScene(directory: Path): MigratedScene {
        require(Files.isDirectory(directory, NOFOLLOW_LINKS) && !Files.isSymbolicLink(directory)) {
            "scene directory is not a regular directory: ${directory.fileName}"
        }
        val manifest = readObject(directory.resolve("manifest.json"), "manifest")
        manifest.requireKeys(
            setOf(
                "schemaVersion",
                "sceneId",
                "expectation",
                "observedOutcome",
                "sourceCommit",
                "generatedAtUtc",
                "oracleKind",
                "oracleId",
                "oracleVersion",
                "oracleProvenance",
                "oracleSha256",
                "files",
            ),
        )
        require(manifest.requiredString("schemaVersion") == GPU_EVIDENCE_SCHEMA) { "unsupported manifest schemaVersion" }
        val sceneId = manifest.requiredString("sceneId")
        require(sceneId == directory.fileName.toString()) { "manifest sceneId does not match directory" }
        val sourceCommit = manifest.requiredString("sourceCommit")
        val files = manifest.requiredObject("files").entries.associate { (name, value) ->
            name to value.jsonPrimitive.asString("manifest hash")
        }
        require("environment.json" in files) { "$sceneId: manifest must reference environment.json" }
        val environment = readEnvironment(directory.resolve("environment.json"), sourceCommit)
        EvidenceBundleVerifier.verifyHistoricalPromotionRecord(
            promotion = readObject(directory.resolve("promotion.json"), "promotion"),
            sceneId = sceneId,
            sourceCommit = sourceCommit,
        )
        val imageHashes = files.filterKeys { it in IMAGE_FILES }
        val manifestV2Bytes = EvidenceManifestV2(
            schemaVersion = GPU_EVIDENCE_SCENE_SCHEMA_V2,
            sceneId = sceneId,
            expectation = manifest.requiredString("expectation"),
            observedOutcome = manifest.requiredString("observedOutcome"),
            oracleKind = manifest.requiredString("oracleKind"),
            oracleId = manifest.requiredString("oracleId"),
            oracleVersion = manifest.requiredInt("oracleVersion"),
            files = files - "environment.json",
            oracleProvenance = manifest.requiredString("oracleProvenance"),
            oracleSha256 = manifest.optionalNullableString("oracleSha256"),
        ).toJson().canonicalBytes()
        return MigratedScene(sceneId, sourceCommit, environment, imageHashes, manifestV2Bytes)
    }

    private fun readEnvironment(path: Path, sourceCommit: String): EvidenceEnvironment {
        val environment = readObject(path, "environment")
        environment.requireKeys(setOf("sourceCommit", "osName", "osVersion", "osArchitecture", "javaVersion", "deviceGeneration", "capabilityImplementation", "available", "adapter"))
        require(environment.requiredString("sourceCommit") == sourceCommit) { "environment sourceCommit mismatch" }
        val adapter = environment["adapter"]?.let { value ->
            if (value is JsonNull) return@let null
            val adapterObject = value.jsonObject
            adapterObject.requireKeys(setOf("summary", "vendor", "device", "architecture", "description", "isFallbackAdapter"))
            EvidenceAdapter(
                summary = adapterObject.optionalNullableString("summary"),
                vendor = adapterObject.optionalNullableString("vendor"),
                device = adapterObject.optionalNullableString("device"),
                architecture = adapterObject.optionalNullableString("architecture"),
                description = adapterObject.optionalNullableString("description"),
                isFallbackAdapter = adapterObject.optionalNullableBoolean("isFallbackAdapter"),
            )
        }
        return EvidenceEnvironment(
            sourceCommit = sourceCommit,
            osName = environment.requiredString("osName"),
            osVersion = environment.requiredString("osVersion"),
            osArchitecture = environment.requiredString("osArchitecture"),
            javaVersion = environment.requiredString("javaVersion"),
            adapter = adapter,
            deviceGeneration = environment.optionalNullableLong("deviceGeneration"),
            capabilityImplementation = environment.optionalNullableString("capabilityImplementation"),
            available = environment.requiredBoolean("available"),
        )
    }

    private fun stageScene(scene: MigratedScene, source: Path, destination: Path, repositoryRoot: Path) {
        copyTree(source, destination, repositoryRoot)
        scene.imageHashes.forEach { (name, expectedHash) ->
            require(sha256Hex(Files.readAllBytes(source.resolve(name))) == expectedHash) {
                "${scene.sceneId}: source $name hash mismatch"
            }
            require(sha256Hex(Files.readAllBytes(destination.resolve(name))) == expectedHash) {
                "${scene.sceneId}: staged $name hash mismatch"
            }
        }
        deleteSceneMetadata(destination.resolve("environment.json"))
        deleteSceneMetadata(destination.resolve("promotion.json"))
        Files.write(destination.resolve("manifest.json"), scene.manifestV2Bytes)
    }

    private fun writeRootMetadata(staged: Path, validated: ValidatedV1Root, request: MigratePromotedEvidenceCliRequest) {
        Files.write(staged.resolve("environment.json"), validated.environmentBytes)
        Files.write(
            staged.resolve("catalog.json"),
            EvidenceCatalogV2(
                schemaVersion = GPU_EVIDENCE_CATALOG_SCHEMA_V2,
                environment = "environment.json",
                promotion = "promotion.json",
                scenes = validated.scenes.map { scene ->
                    EvidenceCatalogEntry(
                        sceneId = scene.sceneId,
                        sourceCommit = scene.sourceCommit,
                        manifest = "${scene.sceneId}/manifest.json",
                        manifestSha256 = sha256Hex(scene.manifestV2Bytes),
                    )
                },
            ).toJson().canonicalBytes(),
        )
        Files.write(
            staged.resolve("promotion.json"),
            EvidencePromotionV2(
                schemaVersion = GPU_EVIDENCE_PROMOTION_SCHEMA_V2,
                promotedAtUtc = clock.instant().toString(),
                reviewer = request.reviewer,
                reason = request.reason,
                rebaseline = true,
                sceneIds = validated.sceneIds,
                priorComparison = null,
                newComparison = null,
            ).toJson().canonicalBytes(),
        )
    }

    private fun verifyStagedMetadata(staged: Path, request: MigratePromotedEvidenceCliRequest, sceneIds: List<String>) {
        val promotion = readObject(staged.resolve("promotion.json"), "promotion")
        require(promotion.requiredString("reviewer") == request.reviewer) { "promotion reviewer does not match the request" }
        require(promotion.requiredString("reason") == request.reason) { "promotion reason does not match the request" }
        require(promotion.requiredBoolean("rebaseline")) { "migration promotion must be rebaseline=true" }
        val actualSceneIds = promotion["sceneIds"]?.jsonArray?.map { it.jsonPrimitive.content } ?: error("promotion sceneIds must be an array")
        require(actualSceneIds == sceneIds) { "promotion sceneIds do not match the migrated catalogue" }
        require(promotion.optionalNullableString("priorComparison") == null) { "migration promotion priorComparison must be null" }
        require(promotion.optionalNullableString("newComparison") == null) { "migration promotion newComparison must be null" }
    }

    private fun swapPromotedRoot(staged: Path, destination: Path, repositoryRoot: Path) {
        val parent = destination.parent ?: error("promoted root has no parent")
        var snapshot: Path? = null
        var snapshotReady = false
        var backup: Path? = null
        var destinationMoveAttempted = false
        var installAttempted = false
        var restored = false
        var installed = false
        try {
            val snapshotRoot = Files.createTempDirectory(parent, ".promoted.snapshot-")
            snapshot = snapshotRoot
            copyTree(destination, snapshotRoot, repositoryRoot)
            verifySnapshot(destination, snapshotRoot)
            snapshotReady = true
            backup = Files.createTempDirectory(parent, ".promoted.backup-")
            val backupDestination = backup.resolve(destination.fileName.toString())
            destinationMoveAttempted = true
            moveWithFallback(destination, backupDestination)
            installAttempted = true
            moveWithFallback(staged, destination)
            installed = true
        } catch (failure: Throwable) {
            if (snapshotReady && destinationMoveAttempted) {
                val backupDestination = backup?.resolve(destination.fileName.toString())
                val independentSnapshot = requireNotNull(snapshot)
                val verifiedBackup = backupDestination?.takeIf { isVerifiedSnapshot(independentSnapshot, it) }
                if (verifiedBackup != null) {
                    try {
                        if (Files.exists(destination, NOFOLLOW_LINKS)) deleteTree(destination)
                        moveWithFallback(verifiedBackup, destination)
                        restored = true
                    } catch (restoreFailure: Throwable) {
                        failure.addSuppressed(restoreFailure)
                        try {
                            if (Files.exists(destination, NOFOLLOW_LINKS)) deleteTree(destination)
                            copyTree(independentSnapshot, destination, repositoryRoot)
                            verifySnapshot(independentSnapshot, destination)
                            restored = true
                            stderr.println("migration backup restore failed; independent snapshot restored the old root")
                        } catch (snapshotRestoreFailure: Throwable) {
                            failure.addSuppressed(snapshotRestoreFailure)
                            try {
                                if (Files.exists(destination, NOFOLLOW_LINKS)) deleteTree(destination)
                            } catch (cleanupFailure: Throwable) {
                                failure.addSuppressed(cleanupFailure)
                            }
                            stderr.println("migration rollback failed; independent snapshot and backup retained at $snapshot and $backup")
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
                        stderr.println("migration rollback failed; independent snapshot retained at $snapshot; backup retained at $backup")
                    }
                }
            }
            throw failure
        } finally {
            if (restored || installed) {
                runCatching { backup?.let(::deleteTree) }
                    .onFailure { cleanupFailure -> stderr.println("migration publication succeeded; backup retained at $backup: ${cleanupFailure.message}") }
                runCatching { snapshot?.let(::deleteTree) }
                    .onFailure { cleanupFailure -> stderr.println("migration publication succeeded; snapshot retained at $snapshot: ${cleanupFailure.message}") }
            } else if (!destinationMoveAttempted && !installAttempted) {
                runCatching { backup?.let(::deleteTree) }
                    .onFailure { cleanupFailure -> stderr.println("migration setup cleanup failed; backup retained at $backup: ${cleanupFailure.message}") }
                runCatching { snapshot?.let(::deleteTree) }
                    .onFailure { cleanupFailure -> stderr.println("migration setup cleanup failed; snapshot retained at $snapshot: ${cleanupFailure.message}") }
            }
        }
    }

    private fun moveWithFallback(source: Path, destination: Path) {
        try {
            moveStrategy(source, destination, true)
        } catch (_: AtomicMoveNotSupportedException) {
            moveStrategy(source, destination, false)
        }
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
                if (Files.isDirectory(current, NOFOLLOW_LINKS)) Files.createDirectories(target) else Files.copy(current, target)
            }
        }
    }

    private fun verifySnapshot(expected: Path, actual: Path) {
        require(Files.isDirectory(actual, NOFOLLOW_LINKS) && !Files.isSymbolicLink(actual)) {
            "migration snapshot is not a regular directory: $actual"
        }
        require(regularFilesByRelativePath(expected) == regularFilesByRelativePath(actual)) {
            "migration snapshot does not match the original root"
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

    private fun deleteSceneMetadata(path: Path) {
        require(Files.isRegularFile(path, NOFOLLOW_LINKS) && !Files.isSymbolicLink(path)) {
            "scene metadata must be a regular non-symlink file: $path"
        }
        Files.delete(path)
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

    private fun readObject(path: Path, label: String): JsonObject {
        require(Files.isRegularFile(path, NOFOLLOW_LINKS)) { "missing $label" }
        require(!Files.isSymbolicLink(path)) { "$label cannot be a symlink" }
        val text = Files.readString(path)
        rejectDuplicateKeys(text)
        return EvidenceJson.parseToJsonElement(text).jsonObject
    }

    private fun JsonObject.requiredString(key: String): String {
        val primitive = this[key] as? JsonPrimitive ?: error("$key must be a string")
        require(primitive.isString) { "$key must be a string" }
        return primitive.contentOrNull ?: error("$key must not be null")
    }

    private fun JsonObject.requiredInt(key: String): Int =
        this[key]?.jsonPrimitive?.takeUnless { it.isString }?.content?.toIntOrNull() ?: error("$key must be an integer")

    private fun JsonObject.requiredBoolean(key: String): Boolean =
        (this[key] as? JsonPrimitive)?.takeUnless { it.isString }?.booleanOrNull ?: error("$key must be a boolean")

    private fun JsonObject.requiredObject(key: String): JsonObject = this[key]?.jsonObject ?: error("$key must be an object")

    private fun JsonObject.requireKeys(expected: Set<String>) {
        require(keys == expected) { "unexpected or missing keys: expected=$expected actual=$keys" }
    }

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

    private fun JsonPrimitive.asString(label: String): String =
        require(isString && contentOrNull != null) { "$label must be a string" }.let { content }

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

    private data class MigratedScene(
        val sceneId: String,
        val sourceCommit: String,
        val environment: EvidenceEnvironment,
        val imageHashes: Map<String, String>,
        val manifestV2Bytes: ByteArray,
    )

    private data class ValidatedV1Root(
        val scenes: List<MigratedScene>,
        val sceneIds: List<String>,
        val environmentBytes: ByteArray,
    )

    private companion object {
        val IMAGE_FILES = setOf("cpu.png", "skia.png", "gpu.png", "diff.png")
    }
}
