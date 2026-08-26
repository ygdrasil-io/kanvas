package org.graphiks.kanvas.gpu.evidence.artifacts

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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

class IncrementalPromotionRegressionTest {
    @TempDir
    lateinit var repository: Path

    @Test
    fun `selected promotion leaves every unselected regular file byte-identical`() {
        writePromotedRoot(repository, COMMIT)
        writeGeneratedRoot(repository, OTHER_COMMIT, listOf(SELECTED_SCENE))
        val before = snapshotRegularFiles(promotedRoot(repository).resolve(UNSELECTED_SCENE))

        val result = PromoteEvidenceCliRunner().run(
            args(repository, OTHER_COMMIT, selection = arrayOf("--scene", SELECTED_SCENE), reviewer = "reviewer", reason = "selected"),
        )

        assertEquals(0, result)
        assertEquals(before, snapshotRegularFiles(promotedRoot(repository).resolve(UNSELECTED_SCENE)))
    }

    @Test
    fun `selected promotion rejects unrelated staged scene file changes before swap`() {
        writePromotedRoot(repository, COMMIT)
        writeGeneratedRoot(repository, OTHER_COMMIT, listOf(SELECTED_SCENE))
        val before = snapshot(promotedRoot(repository))
        val stderr = ByteArrayOutputStream()

        val result = PromoteEvidenceCliRunner(
            stderr = PrintStream(stderr),
            beforeStagedVerification = { staged ->
                val manifest = staged.resolve("$UNSELECTED_SCENE/manifest.json")
                Files.writeString(manifest, Files.readString(manifest) + "\n")
                refreshCatalogManifestHash(staged, UNSELECTED_SCENE)
            },
        ).run(args(repository, OTHER_COMMIT, selection = arrayOf("--scene", SELECTED_SCENE)))

        assertTrue(result != 0)
        assertEquals(before, snapshot(promotedRoot(repository)))
        assertTrue(stderr.toString().contains("$UNSELECTED_SCENE/manifest.json"))
    }

    private fun args(
        root: Path,
        commit: String,
        reviewer: String = "reviewer",
        reason: String = "reason",
        selection: Array<String> = arrayOf("--all"),
    ): Array<String> = buildList {
        add("--repository-root"); add(root.toString())
        add("--source-commit"); add(commit)
        addAll(selection.asList())
        add("--reviewer"); add(reviewer)
        add("--reason"); add(reason)
    }.toTypedArray()

    private fun writeGeneratedRoot(
        root: Path,
        commit: String,
        sceneIds: List<String>,
        environment: EvidenceEnvironment = environment(commit),
    ): Path {
        val writer = EvidenceBundleWriter(root, commit)
        val observations = linkedMapOf<String, SceneObservation>()
        val bundles = linkedMapOf<String, Path>()
        selectedCases(sceneIds).forEach { evidenceCase ->
            val sceneId = evidenceCase.descriptor.id.value
            val observation = observation(evidenceCase, environment.copy(sourceCommit = commit))
            observations[sceneId] = observation
            bundles[sceneId] = when (observation) {
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
                is SceneObservation.Unavailable -> error("unsupported observation")
            }
        }
        return EvidenceCatalogWriter(root).writeGeneratedCatalog(
            root = generatedRoot(root, commit),
            selection = EvidenceSelection.Explicit(sceneIds),
            observations = observations,
            bundlePaths = bundles,
        )
    }

    private fun writePromotedRoot(
        root: Path,
        commit: String,
        environment: EvidenceEnvironment = environment(commit),
    ): Path {
        val generated = writeGeneratedRoot(root, commit, allSceneIds(), environment)
        val promoted = promotedRoot(root)
        Files.createDirectories(promoted)
        allSceneIds().forEach { sceneId ->
            copyTree(generated.resolve(sceneId), promoted.resolve(sceneId))
        }
        Files.write(promoted.resolve("environment.json"), environmentJsonV2(environment))
        Files.write(
            promoted.resolve("catalog.json"),
            EvidenceCatalogV2(
                schemaVersion = GPU_EVIDENCE_CATALOG_SCHEMA_V2,
                environment = "environment.json",
                promotion = "promotion.json",
                scenes = allSceneIds().map { sceneId ->
                    EvidenceCatalogEntry(
                        sceneId = sceneId,
                        sourceCommit = commit,
                        manifest = "$sceneId/manifest.json",
                        manifestSha256 = sha256(Files.readAllBytes(promoted.resolve(sceneId).resolve("manifest.json"))),
                    )
                },
            ).toJson().canonicalBytes(),
        )
        Files.write(
            promoted.resolve("promotion.json"),
            EvidencePromotionV2(
                schemaVersion = GPU_EVIDENCE_PROMOTION_SCHEMA_V2,
                promotedAtUtc = "1970-01-01T00:00:00Z",
                reviewer = "reviewer",
                reason = "initial",
                rebaseline = false,
                sceneIds = allSceneIds(),
                priorComparison = null,
                newComparison = null,
            ).toJson().canonicalBytes(),
        )
        return promoted
    }

    private fun refreshCatalogManifestHash(root: Path, sceneId: String) {
        val catalog = EvidenceJson.parseToJsonElement(Files.readString(root.resolve("catalog.json"))).jsonObject
        val entries = catalog["scenes"]!!.jsonArray.map { scene ->
            val entry = scene.jsonObject
            EvidenceCatalogEntry(
                sceneId = entry["sceneId"]!!.jsonPrimitive.content,
                sourceCommit = entry["sourceCommit"]!!.jsonPrimitive.content,
                manifest = entry["manifest"]!!.jsonPrimitive.content,
                manifestSha256 = if (entry["sceneId"]!!.jsonPrimitive.content == sceneId) {
                    sha256(Files.readAllBytes(root.resolve(sceneId).resolve("manifest.json")))
                } else {
                    entry["manifestSha256"]!!.jsonPrimitive.content
                },
            )
        }
        Files.write(
            root.resolve("catalog.json"),
            EvidenceCatalogV2(
                schemaVersion = catalog["schemaVersion"]!!.jsonPrimitive.content,
                environment = catalog["environment"]!!.jsonPrimitive.content,
                promotion = catalog["promotion"]!!.jsonPrimitive.content,
                scenes = entries,
            ).toJson().canonicalBytes(),
        )
    }

    private fun allSceneIds(): List<String> = GpuEvidenceCatalog.cases.map { it.descriptor.id.value }.sorted()

    private fun selectedCases(sceneIds: List<String>): List<EvidenceCase> =
        sceneIds.map { sceneId -> GpuEvidenceCatalog.cases.first { it.descriptor.id.value == sceneId } }

    private fun observation(evidenceCase: EvidenceCase, environment: EvidenceEnvironment): SceneObservation {
        val descriptor = evidenceCase.descriptor
        val rendered = descriptor.expectation is EvidenceExpectation.ShouldRender
        val route = RouteEvidence(
            routeId = routeId(evidenceCase.program),
            attemptId = "attempt",
            furthestPhase = if (rendered) "Completed" else null,
            outcome = if (rendered) "rendered" else "refused",
            encodedScopeKinds = emptyList(),
            structuralEvents = emptyList(),
            structuralCounters = if (rendered) {
                mapOf("queue.submit" to 1L, "render.draw" to 1L, "render.pipelineBind" to 1L)
            } else {
                emptyMap()
            },
            runtimeTelemetryDelta = GPUBackendRuntimeTelemetry(submissions = if (rendered) 1L else 0L),
        )
        return when (val expectation = descriptor.expectation) {
            EvidenceExpectation.ShouldRender -> {
                val pixels = requireNotNull(evidenceCase.oracle).render(descriptor.width, descriptor.height)
                val comparison = EvidenceComparator().compare(pixels, pixels, descriptor.width, descriptor.height, requireNotNull(descriptor.comparison))
                SceneObservation.Rendered(pixels, route, emptyList(), environment, comparison)
            }
            is EvidenceExpectation.ShouldRefuse -> {
                SceneObservation.Refused(expectation.stableReasonCode, "test refusal", 0L, route, emptyList(), environment)
            }
        }
    }

    private fun routeId(program: EvidenceProgram): String = when (program) {
        is KanvasSurfaceProgram -> program.routeId
        is RoutedSceneProgram -> program.routeId
        else -> error("unsupported evidence program: ${program::class.qualifiedName}")
    }

    private fun generatedRoot(root: Path, commit: String): Path =
        root.resolve("reports/gpu-renderer/evidence/correctness/generated/$commit")

    private fun promotedRoot(root: Path): Path =
        root.resolve("reports/gpu-renderer/evidence/correctness/promoted")

    private fun snapshotRegularFiles(root: Path): Map<String, List<Byte>> =
        Files.walk(root).use { stream ->
            stream.iterator().asSequence().filter(Files::isRegularFile).associate { path ->
                root.relativize(path).toString() to Files.readAllBytes(path).toList()
            }
        }

    private fun snapshot(root: Path): Map<String, List<Byte>> =
        if (!Files.exists(root)) {
            emptyMap()
        } else {
            Files.walk(root).use { stream ->
                stream.iterator().asSequence().filter(Files::isRegularFile).associate { path ->
                    root.relativize(path).toString() to Files.readAllBytes(path).toList()
                }
            }
        }

    private fun copyTree(source: Path, destination: Path) {
        Files.walk(source).use { stream ->
            stream.forEach { current ->
                val relative = source.relativize(current)
                val target = destination.resolve(relative.toString())
                when {
                    Files.isDirectory(current) -> Files.createDirectories(target)
                    else -> {
                        Files.createDirectories(target.parent)
                        Files.copy(current, target)
                    }
                }
            }
        }
    }

    private fun environment(sourceCommit: String): EvidenceEnvironment =
        EvidenceEnvironment(
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

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    companion object {
        private const val COMMIT = "0123456789abcdef0123456789abcdef01234567"
        private const val OTHER_COMMIT = "fedcba9876543210fedcba9876543210fedcba98"
        private const val SELECTED_SCENE = "solid-card-stack"
        private const val UNSELECTED_SCENE = "custom-runtime-effect-unregistered-refusal"
    }
}
