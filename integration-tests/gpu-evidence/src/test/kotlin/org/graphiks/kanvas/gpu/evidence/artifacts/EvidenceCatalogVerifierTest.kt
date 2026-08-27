package org.graphiks.kanvas.gpu.evidence.artifacts

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.graphiks.kanvas.gpu.evidence.catalog.EvidenceAdapter
import org.graphiks.kanvas.gpu.evidence.catalog.EvidenceCase
import org.graphiks.kanvas.gpu.evidence.catalog.EvidenceEnvironment
import org.graphiks.kanvas.gpu.evidence.catalog.EvidenceExpectation
import org.graphiks.kanvas.gpu.evidence.catalog.GpuEvidenceCatalog
import org.graphiks.kanvas.gpu.evidence.catalog.RouteEvidence
import org.graphiks.kanvas.gpu.evidence.catalog.SceneObservation
import org.graphiks.kanvas.gpu.evidence.compare.EvidenceComparator
import org.graphiks.kanvas.gpu.evidence.programs.KanvasSurfaceProgram
import org.graphiks.kanvas.gpu.evidence.runner.EvidenceProgram
import org.graphiks.kanvas.gpu.evidence.runner.RoutedSceneProgram
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeTelemetry
import org.junit.jupiter.api.io.TempDir

class EvidenceCatalogVerifierTest {
    @TempDir
    lateinit var repository: Path

    @Test
    fun `selected generated v2 catalog verifies known strict subset`() {
        val cases = selectedCases()
        val root = writeV2Root(cases, mapOf(cases[0].descriptor.id.value to COMMIT_A, cases[1].descriptor.id.value to COMMIT_A))

        val verification = EvidenceCatalogVerifier.verify(
            root = root,
            selection = EvidenceSelection.Explicit(cases.map { it.descriptor.id.value }),
            cases = cases,
            expectedSourceCommit = COMMIT_A,
        )

        assertEquals(cases.map { it.descriptor.id.value }.sorted(), verification.sceneIds)
        assertEquals(cases.associate { it.descriptor.id.value to COMMIT_A }, verification.sourceCommits)
        assertEquals(GPU_EVIDENCE_CATALOG_SCHEMA_V2, verification.environment.schemaVersion)
    }

    @Test
    fun `complete v2 verification rejects a strict subset root`() {
        val cases = selectedCases()
        val root = writeV2Root(cases.take(1), mapOf(cases.first().descriptor.id.value to COMMIT_A))

        assertFailsWith<IllegalArgumentException> {
            EvidenceCatalogVerifier.verify(
                root = root,
                selection = EvidenceSelection.All,
                cases = cases,
                expectedSourceCommit = COMMIT_A,
            )
        }
    }

    @Test
    fun `v2 verification rejects an unknown scene directory in the root`() {
        val cases = selectedCases()
        val root = writeV2Root(cases, mapOf(cases[0].descriptor.id.value to COMMIT_A, cases[1].descriptor.id.value to COMMIT_A))
        val unknown = root.resolve("unknown-scene")
        Files.createDirectory(unknown)
        Files.copy(root.resolve(cases.first().descriptor.id.value).resolve("manifest.json"), unknown.resolve("manifest.json"))

        assertFailsWith<IllegalArgumentException> {
            EvidenceCatalogVerifier.verify(
                root = root,
                selection = EvidenceSelection.Explicit(cases.map { it.descriptor.id.value }),
                cases = cases,
                expectedSourceCommit = COMMIT_A,
            )
        }
    }

    @Test
    fun `v2 verification rejects a wrong manifest hash`() {
        val cases = selectedCases()
        val root = writeV2Root(cases, mapOf(cases[0].descriptor.id.value to COMMIT_A, cases[1].descriptor.id.value to COMMIT_A))
        replaceInCatalog(root, "\"manifestSha256\":\"${manifestSha(root, cases.first().descriptor.id.value)}\"", "\"manifestSha256\":\"${"0".repeat(64)}\"")

        assertFailsWith<IllegalArgumentException> {
            EvidenceCatalogVerifier.verify(
                root = root,
                selection = EvidenceSelection.Explicit(cases.map { it.descriptor.id.value }),
                cases = cases,
                expectedSourceCommit = COMMIT_A,
            )
        }
    }

    @Test
    fun `v2 verification rejects a tampered root environment`() {
        val cases = selectedCases()
        val root = writeV2Root(cases, mapOf(cases[0].descriptor.id.value to COMMIT_A, cases[1].descriptor.id.value to COMMIT_A))
        val environment = root.resolve("environment.json")
        Files.writeString(environment, Files.readString(environment).replace("\"available\":true", "\"available\":false"))

        assertFailsWith<IllegalArgumentException> {
            EvidenceCatalogVerifier.verify(
                root = root,
                selection = EvidenceSelection.Explicit(cases.map { it.descriptor.id.value }),
                cases = cases,
                expectedSourceCommit = COMMIT_A,
            )
        }
    }

    @Test
    fun `complete v2 verification accepts heterogeneous source commits with one root environment`() {
        val cases = selectedCases()
        val commits = mapOf(
            cases[0].descriptor.id.value to COMMIT_A,
            cases[1].descriptor.id.value to COMMIT_B,
        )
        val root = writeV2Root(cases, commits)

        val verification = EvidenceCatalogVerifier.verify(
            root = root,
            selection = EvidenceSelection.All,
            cases = cases,
            expectedSourceCommit = null,
        )

        assertEquals(cases.map { it.descriptor.id.value }.sorted(), verification.sceneIds)
        assertEquals(commits, verification.sourceCommits)
    }

    @Test
    fun `complete v2 verification accepts promotion metadata for a strict subset of catalog scenes`() {
        val cases = selectedCases()
        val root = writeV2Root(
            cases = cases,
            sourceCommits = mapOf(cases[0].descriptor.id.value to COMMIT_A, cases[1].descriptor.id.value to COMMIT_A),
            promotionSceneIds = listOf(cases[0].descriptor.id.value),
        )

        val verification = EvidenceCatalogVerifier.verify(
            root = root,
            selection = EvidenceSelection.All,
            cases = cases,
            expectedSourceCommit = COMMIT_A,
        )

        assertEquals(cases.map { it.descriptor.id.value }.sorted(), verification.sceneIds)
    }

    @Test
    fun `v2 verification rejects comparison summaries on a non-rebaseline promotion`() {
        val cases = selectedCases()
        val root = writeV2Root(
            cases = cases,
            sourceCommits = mapOf(cases[0].descriptor.id.value to COMMIT_A, cases[1].descriptor.id.value to COMMIT_A),
            promotionSceneIds = listOf(cases.first().descriptor.id.value),
        )
        val promotion = root.resolve("promotion.json")
        Files.writeString(
            promotion,
            Files.readString(promotion)
                .replace("\"priorComparison\":null", "\"priorComparison\":\"old\"")
                .replace("\"newComparison\":null", "\"newComparison\":\"new\""),
        )

        assertFailsWith<IllegalArgumentException> {
            EvidenceCatalogVerifier.verify(
                root = root,
                selection = EvidenceSelection.All,
                cases = cases,
                expectedSourceCommit = COMMIT_A,
            )
        }
    }

    @Test
    fun `v2 rebaseline promotion requires paired nonblank comparison summaries`() {
        val cases = selectedCases()
        val root = writeV2Root(
            cases = cases,
            sourceCommits = mapOf(cases[0].descriptor.id.value to COMMIT_A, cases[1].descriptor.id.value to COMMIT_A),
            promotionSceneIds = listOf(cases.first().descriptor.id.value),
        )
        val promotion = root.resolve("promotion.json")
        Files.writeString(
            promotion,
            Files.readString(promotion).replace("\"rebaseline\":false", "\"rebaseline\":true"),
        )

        assertFailsWith<IllegalArgumentException> {
            EvidenceCatalogVerifier.verify(
                root = root,
                selection = EvidenceSelection.All,
                cases = cases,
                expectedSourceCommit = COMMIT_A,
            )
        }

        Files.writeString(
            promotion,
            Files.readString(promotion)
                .replace("\"priorComparison\":null", "\"priorComparison\":\"\"")
                .replace("\"newComparison\":null", "\"newComparison\":\"new\""),
        )
        assertFailsWith<IllegalArgumentException> {
            EvidenceCatalogVerifier.verify(
                root = root,
                selection = EvidenceSelection.All,
                cases = cases,
                expectedSourceCommit = COMMIT_A,
            )
        }
    }

    @Test
    fun `v2 verification rejects promotion metadata for an unknown scene`() {
        val cases = selectedCases()
        val root = writeV2Root(
            cases = cases,
            sourceCommits = mapOf(cases[0].descriptor.id.value to COMMIT_A, cases[1].descriptor.id.value to COMMIT_A),
            promotionSceneIds = listOf("unknown-scene"),
        )

        assertFailsWith<IllegalArgumentException> {
            EvidenceCatalogVerifier.verify(
                root = root,
                selection = EvidenceSelection.All,
                cases = cases,
                expectedSourceCommit = COMMIT_A,
            )
        }
    }

    private fun selectedCases(): List<EvidenceCase> = listOf(
        GpuEvidenceCatalog.renderCases.first { it.descriptor.id.value == "solid-card-stack" },
        GpuEvidenceCatalog.refusalCases.first { it.descriptor.id.value == "custom-runtime-effect-unregistered-refusal" },
    )

    private fun writeV2Root(
        cases: List<EvidenceCase>,
        sourceCommits: Map<String, String>,
        promotionSceneIds: List<String>? = null,
    ): Path {
        val rootCommit = COMMIT_A
        val generatedRoot = repository.resolve("reports/gpu-renderer/evidence/correctness/generated/$rootCommit")
        if (sourceCommits.values.toSet().size > 1) {
            return writeMixedCommitRoot(generatedRoot, cases, sourceCommits, promotionSceneIds)
        }
        val writer = EvidenceBundleWriter(repository, rootCommit)
        val observations = linkedMapOf<String, SceneObservation>()
        val bundlePaths = linkedMapOf<String, Path>()
        cases.forEach { evidenceCase ->
            val sceneId = evidenceCase.descriptor.id.value
            val environment = environment(sourceCommits.getValue(sceneId))
            val observation = observation(evidenceCase, environment)
            observations[sceneId] = observation
            bundlePaths[sceneId] = when (observation) {
                is SceneObservation.Rendered -> writer.writeGeneratedV2(
                    evidenceCase.descriptor,
                    observation,
                    requireNotNull(evidenceCase.oracle).render(evidenceCase.descriptor.width, evidenceCase.descriptor.height),
                    "attempt-$sceneId",
                )
                is SceneObservation.Refused -> writer.writeGeneratedV2(
                    evidenceCase.descriptor,
                    observation,
                    attemptId = "attempt-$sceneId",
                )
                is SceneObservation.Unavailable -> error("unsupported test observation")
            }
        }
        val root = EvidenceCatalogWriter(repository).writeGeneratedCatalog(
            root = generatedRoot,
            selection = EvidenceSelection.Explicit(cases.map { it.descriptor.id.value }),
            observations = observations,
            bundlePaths = bundlePaths,
        )
        if (promotionSceneIds != null) addPromotion(root, promotionSceneIds)
        return root
    }

    private fun writeMixedCommitRoot(
        root: Path,
        cases: List<EvidenceCase>,
        sourceCommits: Map<String, String>,
        promotionSceneIds: List<String>? = null,
    ): Path {
        Files.createDirectories(root)
        val observations = linkedMapOf<String, SceneObservation>()
        cases.forEach { evidenceCase ->
            val sceneId = evidenceCase.descriptor.id.value
            val commit = sourceCommits.getValue(sceneId)
            val environment = environment(commit)
            val observation = observation(evidenceCase, environment)
            observations[sceneId] = observation
            val writer = EvidenceBundleWriter(repository, commit)
            val bundle = when (observation) {
                is SceneObservation.Rendered -> writer.writeGeneratedV2(
                    evidenceCase.descriptor,
                    observation,
                    requireNotNull(evidenceCase.oracle).render(evidenceCase.descriptor.width, evidenceCase.descriptor.height),
                    "attempt-$sceneId",
                )
                is SceneObservation.Refused -> writer.writeGeneratedV2(
                    evidenceCase.descriptor,
                    observation,
                    attemptId = "attempt-$sceneId",
                )
                is SceneObservation.Unavailable -> error("unsupported test observation")
            }
            copyTree(bundle, root.resolve(sceneId))
        }
        Files.write(root.resolve("environment.json"), environmentJsonV2(observations.values.first().environment))
        val entries = cases.map { evidenceCase ->
            val sceneId = evidenceCase.descriptor.id.value
            EvidenceCatalogEntry(
                sceneId = sceneId,
                sourceCommit = sourceCommits.getValue(sceneId),
                manifest = "$sceneId/manifest.json",
                manifestSha256 = manifestSha(root, sceneId),
            )
        }
        Files.write(
            root.resolve("catalog.json"),
            EvidenceCatalogV2(
                schemaVersion = GPU_EVIDENCE_CATALOG_SCHEMA_V2,
                environment = "environment.json",
                promotion = if (promotionSceneIds == null) null else "promotion.json",
                scenes = entries,
            ).toJson().canonicalBytes(),
        )
        if (promotionSceneIds != null) addPromotion(root, promotionSceneIds)
        return root
    }

    private fun addPromotion(root: Path, promotionSceneIds: List<String>) {
        Files.write(
            root.resolve("promotion.json"),
            EvidencePromotionV2(
                schemaVersion = GPU_EVIDENCE_PROMOTION_SCHEMA_V2,
                promotedAtUtc = "1970-01-01T00:00:00Z",
                reviewer = "reviewer",
                reason = "selected",
                rebaseline = false,
                sceneIds = promotionSceneIds,
                priorComparison = null,
                newComparison = null,
            ).toJson().canonicalBytes(),
        )
        replaceInCatalog(root, "\"promotion\":null", "\"promotion\":\"promotion.json\"")
    }

    private fun observation(evidenceCase: EvidenceCase, environment: EvidenceEnvironment): SceneObservation {
        val descriptor = evidenceCase.descriptor
        val routeId = routeId(evidenceCase.program)
        return if (descriptor.expectation is EvidenceExpectation.ShouldRender) {
            val pixels = requireNotNull(evidenceCase.oracle).render(descriptor.width, descriptor.height)
            val route = RouteEvidence(
                routeId = routeId,
                attemptId = "attempt",
                furthestPhase = "Completed",
                outcome = "rendered",
                encodedScopeKinds = emptyList(),
                structuralEvents = emptyList(),
                structuralCounters = mapOf("queue.submit" to 1L, "render.draw" to 1L, "render.pipelineBind" to 1L),
                runtimeTelemetryDelta = GPUBackendRuntimeTelemetry(submissions = 1L),
            )
            val comparison = EvidenceComparator().compare(pixels, pixels, descriptor.width, descriptor.height, requireNotNull(descriptor.comparison))
            SceneObservation.Rendered(pixels, route, emptyList(), environment, comparison)
        } else {
            val route = RouteEvidence(
                routeId = routeId,
                attemptId = "attempt",
                furthestPhase = null,
                outcome = "refused",
                encodedScopeKinds = emptyList(),
                structuralEvents = emptyList(),
                structuralCounters = emptyMap(),
                runtimeTelemetryDelta = GPUBackendRuntimeTelemetry(submissions = 0L),
            )
            SceneObservation.Refused(
                stableReasonCode = (descriptor.expectation as EvidenceExpectation.ShouldRefuse).stableReasonCode,
                message = "test",
                submissionDelta = 0L,
                route = route,
                diagnostics = emptyList(),
                environment = environment,
            )
        }
    }

    private fun environment(sourceCommit: String) = EvidenceEnvironment(
        sourceCommit = sourceCommit,
        osName = "test",
        osVersion = "1",
        osArchitecture = "x86_64",
        javaVersion = "17",
        adapter = EvidenceAdapter("test-adapter", "test-vendor", "test-device", "test-architecture", "test-description", false),
        deviceGeneration = 1L,
        capabilityImplementation = "native",
        available = true,
    )

    private fun routeId(program: EvidenceProgram): String = when (program) {
        is KanvasSurfaceProgram -> program.routeId
        is RoutedSceneProgram -> program.routeId
        else -> error("unsupported evidence program: ${program::class.qualifiedName}")
    }

    private fun manifestSha(root: Path, sceneId: String): String = sha256(Files.readAllBytes(root.resolve(sceneId).resolve("manifest.json")))

    private fun replaceInCatalog(root: Path, from: String, to: String) {
        val catalog = root.resolve("catalog.json")
        Files.writeString(catalog, Files.readString(catalog).replace(from, to))
    }

    private fun copyTree(source: Path, destination: Path) {
        Files.walk(source).use { stream ->
            stream.forEach { path ->
                val relative = source.relativize(path)
                val target = destination.resolve(relative.toString())
                when {
                    Files.isDirectory(path) -> Files.createDirectories(target)
                    else -> {
                        Files.createDirectories(target.parent)
                        Files.copy(path, target)
                    }
                }
            }
        }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    companion object {
        private const val COMMIT_A = "0123456789abcdef0123456789abcdef01234567"
        private const val COMMIT_B = "fedcba9876543210fedcba9876543210fedcba98"
    }
}
