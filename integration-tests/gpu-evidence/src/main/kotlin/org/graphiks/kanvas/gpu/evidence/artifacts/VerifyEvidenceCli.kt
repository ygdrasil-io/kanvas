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
) {
    companion object {
        private val SOURCE_COMMIT = Regex("[0-9a-f]{40}")

        fun parse(args: Array<String>): VerifyEvidenceCliRequest {
            var root: String? = null
            var sourceCommit: String? = null
            var historical = false
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
                    else -> error("unknown argument: ${args[index]}")
                }
                index++
            }
            val path = Path.of(requireNotNull(root) { "--root is required" }).toAbsolutePath().normalize()
            require(path.isAbsolute && Files.isDirectory(path, NOFOLLOW_LINKS)) { "--root must be an existing directory" }
            require(!Files.isSymbolicLink(path)) { "--root cannot be a symlink" }
            sourceCommit?.let { require(SOURCE_COMMIT.matches(it)) { "source commit must be 40 lowercase hexadecimal characters" } }
            return VerifyEvidenceCliRequest(path, sourceCommit, historical)
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
        require(request.sourceCommit != null || request.allowHistoricalCommit) {
            "--allow-historical-commit is required when --source-commit is omitted"
        }
        val expectedIds = GpuEvidenceCatalog.cases.map { it.descriptor.id.value }
        require(expectedIds.toSet().size == expectedIds.size) { "catalog contains duplicate scene ids" }
        val entries = Files.list(request.root).use { stream -> stream.iterator().asSequence().toList() }
        require(entries.none { Files.isSymbolicLink(it) }) { "evidence root contains a symlink" }
        val names = entries.map { it.fileName.toString() }.toSet()
        require(names == expectedIds.toSet()) {
            "scene directory set mismatch: expected=${expectedIds.toSet()} actual=$names"
        }
        require(entries.all { Files.isDirectory(it, NOFOLLOW_LINKS) }) { "evidence root contains a non-directory entry" }

        val commit = request.sourceCommit ?: commonManifestCommit(entries)
        val results = GpuEvidenceCatalog.cases.map { evidenceCase ->
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
                    passed
                }
            }
        }
        return if (results.all { it }) 0 else 1
    }

    /** Internal-only preflight for an existing promoted root during rebaseline. */
    internal fun verifyHistoricalSubset(root: Path): Int {
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
            val commit = commonManifestCommit(entries)
            val results = entries.map { directory ->
                val sceneId = directory.fileName.toString()
                val evidenceCase = requireNotNull(expectedById[sceneId])
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

    private companion object {
        val SOURCE_COMMIT = Regex("[0-9a-f]{40}")
    }
}
