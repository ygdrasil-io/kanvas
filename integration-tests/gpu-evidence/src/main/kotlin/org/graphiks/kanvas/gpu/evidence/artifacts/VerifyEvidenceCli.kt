package org.graphiks.kanvas.gpu.evidence.artifacts

import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import kotlin.system.exitProcess
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.graphiks.kanvas.gpu.evidence.catalog.GpuEvidenceCatalog
import org.graphiks.kanvas.gpu.evidence.gate.EvidenceVerdict

/** Headless verifier for generated or checked-in GPU evidence. */
fun main(args: Array<String>): Unit = exitProcess(VerifyEvidenceCliRunner().run(args))

data class VerifyEvidenceCliRequest(
    val root: Path,
    val sourceCommit: String?,
    val allowHistoricalCommit: Boolean,
    val requirePromotion: Boolean,
    val selection: EvidenceSelection,
) {
    companion object {
        private val SOURCE_COMMIT = Regex("[0-9a-f]{40}")

        fun parse(args: Array<String>): VerifyEvidenceCliRequest {
            var root: String? = null
            var sourceCommit: String? = null
            var historical = false
            var requirePromotion = false
            val sceneIds = mutableListOf<String>()
            var scenesFile: Path? = null
            var all = false
            var index = 0
            while (index < args.size) {
                when (args[index]) {
                    "--root" -> {
                        require(root == null) { "duplicate --root" }
                        require(index + 1 < args.size && !args[index + 1].startsWith("--")) { "--root requires a path" }
                        root = args[++index]
                    }
                    "--source-commit" -> {
                        require(sourceCommit == null) { "duplicate --source-commit" }
                        require(index + 1 < args.size && !args[index + 1].startsWith("--")) { "--source-commit requires a commit" }
                        sourceCommit = args[++index]
                    }
                    "--allow-historical-commit" -> {
                        require(!historical) { "duplicate --allow-historical-commit" }
                        historical = true
                    }
                    "--require-promotion" -> {
                        require(!requirePromotion) { "duplicate --require-promotion" }
                        requirePromotion = true
                    }
                    "--scene" -> {
                        require(index + 1 < args.size && !args[index + 1].startsWith("--")) { "--scene requires an id" }
                        sceneIds += args[++index]
                    }
                    "--scenes-file" -> {
                        require(scenesFile == null) { "duplicate --scenes-file" }
                        require(index + 1 < args.size && !args[index + 1].startsWith("--")) { "--scenes-file requires a path" }
                        scenesFile = Path.of(args[++index]).toAbsolutePath().normalize()
                    }
                    "--all" -> {
                        require(!all) { "duplicate --all" }
                        all = true
                    }
                    else -> error("unknown argument: ${args[index]}")
                }
                index++
            }
            val path = Path.of(requireNotNull(root) { "--root is required" }).toAbsolutePath().normalize()
            require(path.isAbsolute && Files.isDirectory(path, NOFOLLOW_LINKS)) { "--root must be an existing directory" }
            require(!Files.isSymbolicLink(path)) { "--root cannot be a symlink" }
            require(!(historical && sourceCommit != null)) { "--allow-historical-commit cannot be combined with --source-commit" }
            sourceCommit?.let { require(SOURCE_COMMIT.matches(it)) { "source commit must be 40 lowercase hexadecimal characters" } }
            scenesFile?.let { sceneIds += EvidenceSelectionParser.readSceneFile(it) }
            val selection = EvidenceSelectionParser.from(sceneIds, all)
            return VerifyEvidenceCliRequest(path, sourceCommit, historical, requirePromotion, selection)
        }
    }
}

class VerifyEvidenceCliRunner(
    private val stdout: PrintStream = System.out,
    private val stderr: PrintStream = System.err,
) {
    fun run(args: Array<String>): Int {
        val request = try {
            VerifyEvidenceCliRequest.parse(args)
        } catch (failure: Exception) {
            stderr.println("gpu evidence verification arguments rejected: ${failure.message}")
            return 2
        }
        return try {
            verify(request)
        } catch (failure: Exception) {
            stderr.println("gpu evidence verification failed: ${failure.message}")
            1
        }
    }

    private fun verify(request: VerifyEvidenceCliRequest): Int {
        return when (detectLayout(request.root)) {
            EvidenceRootLayout.V1 -> {
                require(!request.requirePromotion) { "--require-promotion requires a v2 promoted root" }
                verifyV1(request)
            }
            EvidenceRootLayout.V2 -> verifyV2(request)
        }
    }

    private fun verifyV1(request: VerifyEvidenceCliRequest): Int {
        require(request.sourceCommit != null || request.allowHistoricalCommit) {
            "--allow-historical-commit is required when --source-commit is omitted"
        }
        val expectedCases = request.selection.resolve(GpuEvidenceCatalog.cases)
        val expectedIds = expectedCases.map { it.descriptor.id.value }
        require(expectedIds.toSet().size == expectedIds.size) { "catalog contains duplicate scene ids" }
        val entries = Files.list(request.root).use { stream -> stream.iterator().asSequence().toList() }
        require(entries.none { Files.isSymbolicLink(it) }) { "evidence root contains a symlink" }
        val names = entries.map { it.fileName.toString() }.toSet()
        require(names == expectedIds.toSet()) {
            "scene directory set mismatch: expected=${expectedIds.toSet()} actual=$names"
        }
        require(entries.all { Files.isDirectory(it, NOFOLLOW_LINKS) }) { "evidence root contains a non-directory entry" }

        val commit = request.sourceCommit ?: commonManifestCommit(entries)
        var canonicalEnvironment: EvidenceEnvironmentIdentity? = null
        val results = expectedCases.map { evidenceCase ->
            val sceneId = evidenceCase.descriptor.id.value
            val directory = request.root.resolve(sceneId)
            val expected = EvidenceVerificationExpectation.fromCase(
                evidenceCase = evidenceCase,
                sourceCommit = commit,
                expectedRgba = evidenceCase.oracle?.render(evidenceCase.descriptor.width, evidenceCase.descriptor.height),
            )
            when (val result = EvidenceBundleVerifier.verify(directory, expected)) {
                is EvidenceBundleVerification.Invalid -> {
                    stderr.println("$sceneId: invalid (${result.errors.joinToString("; ")})")
                    false
                }
                is EvidenceBundleVerification.Verified -> {
                    val passed = result.verdict is EvidenceVerdict.Pass
                    stdout.println("$sceneId: ${result.verdict.kind()}")
                    if (!passed) stderr.println("$sceneId: ${result.verdict.reason()}")
                    val coherent = canonicalEnvironment?.let { it == result.environment } ?: run {
                        canonicalEnvironment = result.environment
                        true
                    }
                    if (!coherent) stderr.println("$sceneId: environment identity differs from the evidence root")
                    passed && coherent
                }
            }
        }
        return if (results.all { it }) 0 else 1
    }

    private fun verifyV2(request: VerifyEvidenceCliRequest): Int {
        val expectedCases = request.selection.resolve(GpuEvidenceCatalog.cases)
        val verification = try {
            EvidenceCatalogVerifier.verify(
                root = request.root,
                selection = request.selection,
                cases = GpuEvidenceCatalog.cases,
                expectedSourceCommit = request.sourceCommit,
                requirePromotion = request.requirePromotion,
            )
        } catch (failure: EvidenceCatalogVerificationException) {
            failure.sceneFailures.forEach { sceneFailure ->
                stderr.println("${sceneFailure.sceneId}: invalid (${sceneFailure.errors.joinToString("; ")})")
            }
            return 1
        }
        val results = expectedCases.map { evidenceCase ->
            val sceneId = evidenceCase.descriptor.id.value
            val expected = EvidenceVerificationExpectation.fromCase(
                evidenceCase = evidenceCase,
                sourceCommit = verification.sourceCommits.getValue(sceneId),
                expectedRgba = evidenceCase.oracle?.render(evidenceCase.descriptor.width, evidenceCase.descriptor.height),
            )
            when (
                val result = EvidenceBundleVerifier.verifyV2(
                    request.root.resolve(sceneId),
                    expected,
                    verification.environment,
                    verification.sourceCommits.getValue(sceneId),
                )
            ) {
                is EvidenceBundleVerification.Invalid -> {
                    stderr.println("$sceneId: invalid (${result.errors.joinToString("; ")})")
                    false
                }
                is EvidenceBundleVerification.Verified -> {
                    val passed = result.verdict is EvidenceVerdict.Pass
                    stdout.println("$sceneId: ${result.verdict.kind()}")
                    if (!passed) stderr.println("$sceneId: ${result.verdict.reason()}")
                    passed
                }
            }
        }
        return if (results.all { it } && verification.sceneIds == expectedCases.map { it.descriptor.id.value }.sorted()) 0 else 1
    }

    /** Internal-only preflight for an existing promoted root during rebaseline. */
    internal fun verifyHistoricalSubset(root: Path, selection: EvidenceSelection = EvidenceSelection.All): Int {
        return try {
            require(Files.isDirectory(root, NOFOLLOW_LINKS)) { "historical evidence root must be a directory" }
            require(!Files.isSymbolicLink(root)) { "historical evidence root cannot be a symlink" }
            val expectedById = GpuEvidenceCatalog.cases.associateBy { it.descriptor.id.value }
            require(expectedById.size == GpuEvidenceCatalog.cases.size) { "catalog contains duplicate scene ids" }
            val entries = Files.list(root).use { stream -> stream.iterator().asSequence().toList() }
            require(entries.isNotEmpty()) { "historical evidence root must be non-empty" }
            require(entries.none { Files.isSymbolicLink(it) }) { "historical evidence root contains a symlink" }
            require(entries.all { Files.isDirectory(it, NOFOLLOW_LINKS) }) { "historical evidence root contains a non-directory entry" }
            val names = entries.map { it.fileName.toString() }.toSet()
            require(names.all { it in expectedById }) { "historical evidence root contains unknown scene ids: ${names - expectedById.keys}" }
            if (selection is EvidenceSelection.Explicit) {
                require(names == selection.sceneIds.toSet()) {
                    "scene directory set mismatch: expected=${selection.sceneIds.toSet()} actual=$names"
                }
            }
            val commit = commonManifestCommit(entries)
            val results = entries.map { directory ->
                val sceneId = directory.fileName.toString()
                requireNotNull(expectedById[sceneId])
                val promotion = directory.resolve("promotion.json")
                require(Files.isRegularFile(promotion, NOFOLLOW_LINKS) && !Files.isSymbolicLink(promotion)) {
                    "$sceneId: historical bundle requires a regular promotion.json"
                }
                when (val result = EvidenceBundleVerifier.verifyRecorded(directory, commit)) {
                    is EvidenceBundleVerification.Invalid -> {
                        stderr.println("$sceneId: invalid (${result.errors.joinToString("; ")})")
                        false
                    }
                    is EvidenceBundleVerification.Verified -> {
                        val passed = result.verdict is EvidenceVerdict.Pass
                        stdout.println("$sceneId: ${result.verdict.kind()}")
                        if (!passed) stderr.println("$sceneId: ${result.verdict.reason()}")
                        passed
                    }
                }
            }
            if (results.all { it }) 0 else 1
        } catch (failure: Exception) {
            stderr.println("gpu historical evidence verification failed: ${failure.message}")
            1
        }
    }

    private fun commonManifestCommit(entries: List<Path>): String {
        val commits = entries.map { path ->
            val manifestPath = path.resolve("manifest.json")
            require(Files.isRegularFile(manifestPath, NOFOLLOW_LINKS) && !Files.isSymbolicLink(manifestPath)) {
                "manifest must be a regular non-symlink file"
            }
            val manifest = EvidenceJson.parseToJsonElement(Files.readString(manifestPath)).jsonObject
            val commit = (manifest["sourceCommit"] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
            require(commit != null && SOURCE_COMMIT.matches(commit)) { "manifest sourceCommit must be 40 lowercase hexadecimal characters" }
            commit
        }.toSet()
        require(commits.size == 1) { "evidence bundles do not agree on one source commit: $commits" }
        return commits.single()
    }

    private fun EvidenceVerdict.kind(): String = when (this) {
        is EvidenceVerdict.Pass -> "pass"
        is EvidenceVerdict.Fail -> "fail"
        is EvidenceVerdict.Unavailable -> "unavailable"
    }

    private fun EvidenceVerdict.reason(): String = when (this) {
        is EvidenceVerdict.Pass -> reason
        is EvidenceVerdict.Fail -> reason
        is EvidenceVerdict.Unavailable -> reason
    }

    private fun detectLayout(root: Path): EvidenceRootLayout = when {
        Files.exists(root.resolve("catalog.json"), NOFOLLOW_LINKS) -> EvidenceRootLayout.V2
        else -> EvidenceRootLayout.V1
    }

    private enum class EvidenceRootLayout { V1, V2 }

    private companion object {
        val SOURCE_COMMIT = Regex("[0-9a-f]{40}")
    }
}
