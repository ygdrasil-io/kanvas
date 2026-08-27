package org.graphiks.kanvas.gpu.evidence.artifacts

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertSame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.evidence.catalog.EvidenceEnvironment
import org.graphiks.kanvas.gpu.evidence.catalog.EvidenceAdapter
import org.graphiks.kanvas.gpu.evidence.catalog.EvidenceExpectation
import org.graphiks.kanvas.gpu.evidence.catalog.GpuEvidenceCatalog
import org.graphiks.kanvas.gpu.evidence.catalog.ImageComparison
import org.graphiks.kanvas.gpu.evidence.catalog.RouteEvidence
import org.graphiks.kanvas.gpu.evidence.catalog.SceneObservation
import org.graphiks.kanvas.gpu.evidence.compare.EvidenceComparator
import org.graphiks.kanvas.gpu.evidence.programs.KanvasSurfaceProgram
import org.graphiks.kanvas.gpu.evidence.runner.RoutedSceneProgram
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeTelemetry
import org.junit.jupiter.api.io.TempDir

class VerifyEvidenceCliTest {
    @TempDir
    lateinit var repository: Path

    @Test
    fun `verifier rejects missing extra and non-directory scene entries`() {
        writeAll(COMMIT)
        deleteTree(generatedRoot().resolve("solid-card-stack"))
        assertTrue(verify(COMMIT) != 0)

        writeAll(COMMIT)
        Files.createDirectory(generatedRoot().resolve("extra-scene"))
        assertTrue(verify(COMMIT) != 0)

        writeAll(COMMIT)
        Files.delete(generatedRoot().resolve("extra-scene"))
        Files.writeString(generatedRoot().resolve("not-a-scene"), "x")
        assertTrue(verify(COMMIT) != 0)
    }

    @Test
    fun `historical mode is explicit and accepts one internally consistent source commit`() {
        writeAll(COMMIT)
        assertTrue(VerifyEvidenceCliRunner().run(arrayOf("--root", generatedRoot().toString(), "--all")) != 0)
        assertEquals(0, VerifyEvidenceCliRunner().run(arrayOf("--root", generatedRoot().toString(), "--allow-historical-commit", "--all")))
    }

    @Test
    fun `request parser captures explicit selection and all mode`() {
        val explicit = VerifyEvidenceCliRequest.parse(
            arrayOf(
                "--root", repository.toString(),
                "--source-commit", COMMIT,
                "--scene", "solid-card-stack",
                "--scene", "custom-runtime-effect-unregistered-refusal",
            ),
        )
        assertEquals(
            EvidenceSelection.Explicit(listOf("custom-runtime-effect-unregistered-refusal", "solid-card-stack")),
            explicit.selection,
        )

        val all = VerifyEvidenceCliRequest.parse(arrayOf("--root", repository.toString(), "--source-commit", COMMIT, "--all"))
        assertSame(EvidenceSelection.All, all.selection)
    }

    @Test
    fun `v2 verifier accepts selected generated roots and all still requires the complete catalogue`() {
        writeSelectedV2(listOf("solid-card-stack", "custom-runtime-effect-unregistered-refusal"))

        assertEquals(
            0,
            VerifyEvidenceCliRunner().run(
                arrayOf(
                    "--root", generatedRoot().toString(),
                    "--source-commit", COMMIT,
                    "--scene", "solid-card-stack",
                    "--scene", "custom-runtime-effect-unregistered-refusal",
                ),
            ),
        )
        assertTrue(
            VerifyEvidenceCliRunner().run(
                arrayOf("--root", generatedRoot().toString(), "--source-commit", COMMIT, "--all"),
            ) != 0,
        )
    }

    @Test
    fun `v2 verifier reports invalid scenes on stderr instead of only a global failure`() {
        writeSelectedV2(listOf("solid-card-stack", "custom-runtime-effect-unregistered-refusal"))
        val scene = generatedRoot().resolve("solid-card-stack")
        replaceAndRefresh(scene, "route.json", "\"routeId\":\"kanvas.surface.render\"", "\"routeId\":\"wrong.route\"")
        val stderr = ByteArrayOutputStream()

        assertTrue(
            VerifyEvidenceCliRunner(stderr = PrintStream(stderr)).run(
                arrayOf(
                    "--root", generatedRoot().toString(),
                    "--source-commit", COMMIT,
                    "--scene", "solid-card-stack",
                    "--scene", "custom-runtime-effect-unregistered-refusal",
                ),
            ) != 0,
        )
        assertTrue(stderr.toString().contains("solid-card-stack: invalid"))
        assertFalse(stderr.toString().contains("gpu evidence verification failed: solid-card-stack"))
    }

    @Test
    fun `v1 generated roots still verify through the legacy scene path`() {
        writeAll(COMMIT)

        assertEquals(0, VerifyEvidenceCliRunner().run(arrayOf("--root", generatedRoot().toString(), "--source-commit", COMMIT, "--all")))
    }

    @Test
    fun `v2 promoted roots still verify when historical mode is supplied by the promoted Gradle task`() {
        writePromotedV2()
        assertTrue(Files.isRegularFile(promotedRoot().resolve("catalog.json")))
        assertTrue(Files.isRegularFile(promotedRoot().resolve("environment.json")))
        assertTrue(Files.isRegularFile(promotedRoot().resolve("promotion.json")))
        assertFalse(Files.exists(promotedRoot().resolve("solid-card-stack/environment.json")))
        assertFalse(Files.exists(promotedRoot().resolve("solid-card-stack/promotion.json")))

        assertEquals(0, VerifyEvidenceCliRunner().run(arrayOf("--root", promotedRoot().toString(), "--allow-historical-commit", "--require-promotion", "--all")))
    }

    @Test
    fun `promoted verification rejects a generated v2 root without promotion metadata`() {
        writeSelectedV2(GpuEvidenceCatalog.cases.map { it.descriptor.id.value }.sorted())

        assertTrue(
            VerifyEvidenceCliRunner().run(
                arrayOf("--root", generatedRoot().toString(), "--allow-historical-commit", "--require-promotion", "--all"),
            ) != 0,
        )
    }

    @Test
    fun `historical mode rejects a manifest symlink before reading it`() {
        writeAll(COMMIT)
        val manifest = generatedRoot().resolve("solid-card-stack/manifest.json")
        val backup = repository.resolve("manifest-backup.json")
        Files.move(manifest, backup)
        Files.createSymbolicLink(manifest, backup)

        val stderr = ByteArrayOutputStream()
        assertTrue(VerifyEvidenceCliRunner(stderr = PrintStream(stderr)).run(arrayOf("--root", generatedRoot().toString(), "--allow-historical-commit", "--all")) != 0)
        assertTrue(stderr.toString().contains("manifest must be a regular non-symlink file"))
    }

    @Test
    fun `verifier rejects inconsistent source commits and non-pass verdicts`() {
        writeAll(COMMIT)
        val descriptor = GpuEvidenceCatalog.renderCases.first { it.descriptor.id.value == "solid-card-stack" }.descriptor
        val env = EvidenceEnvironment(OTHER_COMMIT, "test", "1", "test", "17", EvidenceAdapter("test-adapter", "test-vendor", "test-device", "test-architecture", "test-description", false), 1L, "native", true)
            val route = RouteEvidence("kanvas.surface.render", "attempt", "Completed", "rendered", emptyList(), emptyList(), mapOf("queue.submit" to 1L, "render.draw" to 1L, "render.pipelineBind" to 1L), GPUBackendRuntimeTelemetry(submissions = 1L))
        val pixels = ByteArray(descriptor.width * descriptor.height * 4)
        val otherRoot = Files.createTempDirectory("other-evidence")
        val comparison = EvidenceComparator().compare(pixels, pixels, descriptor.width, descriptor.height, requireNotNull(descriptor.comparison))
        EvidenceBundleWriter(otherRoot, OTHER_COMMIT).writeGenerated(descriptor, SceneObservation.Rendered(pixels, route, emptyList(), env, comparison), pixels)
        val manifest = generatedRoot().resolve("solid-card-stack/manifest.json")
        Files.writeString(manifest, Files.readString(manifest).replace(COMMIT, OTHER_COMMIT))
        assertTrue(verify(COMMIT) != 0)

        writeAll(COMMIT)
        val failedDescriptor = GpuEvidenceCatalog.renderCases.first { it.descriptor.id.value == "solid-card-stack" }.descriptor
        val failedEnvironment = EvidenceEnvironment(COMMIT, "test", "1", "test", "17", EvidenceAdapter("test-adapter", "test-vendor", "test-device", "test-architecture", "test-description", false), 1L, "native", true)
        val failedRoute = RouteEvidence("kanvas.surface.render", "attempt", "Completed", "rendered", emptyList(), emptyList(), mapOf("queue.submit" to 1L, "render.draw" to 1L, "render.pipelineBind" to 1L), GPUBackendRuntimeTelemetry(submissions = 1L))
        val failedPixels = ByteArray(failedDescriptor.width * failedDescriptor.height * 4)
        val failedOracle = requireNotNull(GpuEvidenceCatalog.renderCases.first { it.descriptor.id.value == "solid-card-stack" }.oracle).render(failedDescriptor.width, failedDescriptor.height)
        val failedComparison = EvidenceComparator().compare(failedPixels, failedOracle, failedDescriptor.width, failedDescriptor.height, requireNotNull(failedDescriptor.comparison))
        EvidenceBundleWriter(repository, COMMIT).writeGenerated(
            failedDescriptor,
            SceneObservation.Rendered(failedPixels, failedRoute, emptyList(), failedEnvironment, failedComparison),
            failedOracle,
        )
        assertTrue(verify(COMMIT) != 0)
    }

    @Test
    fun `coherent unavailable bundle is rejected by the verifier gate`() {
        writeAll(COMMIT)
        val scene = generatedRoot().resolve("custom-runtime-effect-unregistered-refusal")
        replaceAndRefresh(scene, "route.json", "\"outcome\":\"refused\"", "\"outcome\":\"unavailable\"")
        replaceAndRefresh(scene, "environment.json", "\"available\":true", "\"available\":false")
        replaceAndRefresh(scene, "verdict.json", "\"observedOutcome\":\"refused\"", "\"observedOutcome\":\"unavailable\"")
        replaceAndRefresh(scene, "verdict.json", "\"verdictKind\":\"pass\"", "\"verdictKind\":\"unavailable\"")
        replaceAndRefresh(scene, "verdict.json", "\"reason\":\"exact refusal before submission\"", "\"reason\":\"scene unavailable: unsupported.runtime_effect.custom_wgsl_not_registered\"")
        val manifest = scene.resolve("manifest.json")
        Files.writeString(manifest, Files.readString(manifest).replace("\"observedOutcome\":\"refused\"", "\"observedOutcome\":\"unavailable\""))
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()

        assertTrue(VerifyEvidenceCliRunner(PrintStream(stdout), PrintStream(stderr)).run(arrayOf("--root", generatedRoot().toString(), "--source-commit", COMMIT, "--all")) != 0)
        assertTrue(stdout.toString().contains("custom-runtime-effect-unregistered-refusal: unavailable"))
        assertFalse(stderr.toString().contains("invalid JSON"))
    }

    @Test
    fun `verifier rejects a root with one coherent bundle from a different environment`() {
        writeAll(COMMIT)
        val scene = generatedRoot().resolve("solid-card-stack")
        replaceAndRefresh(scene, "environment.json", "\"osVersion\":\"1\"", "\"osVersion\":\"different\"")

        assertTrue(verify(COMMIT) != 0)
    }

    private fun verify(commit: String) = VerifyEvidenceCliRunner().run(arrayOf("--root", generatedRoot().toString(), "--source-commit", commit, "--all"))

    private fun writeAll(commit: String) {
        val writer = EvidenceBundleWriter(repository, commit)
        GpuEvidenceCatalog.cases.forEach { evidenceCase ->
            val descriptor = evidenceCase.descriptor
            val environment = EvidenceEnvironment(commit, "test", "1", "test", "17", EvidenceAdapter("test-adapter", "test-vendor", "test-device", "test-architecture", "test-description", false), 1L, "native", true)
            val rendered = descriptor.expectation is EvidenceExpectation.ShouldRender
            val routeId = routeId(evidenceCase.program)
            val route = RouteEvidence(routeId, "attempt", if (rendered) "Completed" else null, if (rendered) "rendered" else "refused", emptyList(), emptyList(), if (rendered) mapOf("queue.submit" to 1L, "render.draw" to 1L, "render.pipelineBind" to 1L) else emptyMap(), GPUBackendRuntimeTelemetry(submissions = if (rendered) 1L else 0L))
            val observation = if (rendered) {
                val pixels = requireNotNull(evidenceCase.oracle).render(descriptor.width, descriptor.height)
                val comparison = EvidenceComparator().compare(pixels, pixels, descriptor.width, descriptor.height, requireNotNull(descriptor.comparison))
                SceneObservation.Rendered(pixels, route, emptyList(), environment, comparison)
            } else {
                val reason = (descriptor.expectation as EvidenceExpectation.ShouldRefuse).stableReasonCode
                SceneObservation.Refused(reason, "test", 0, route, emptyList(), environment)
            }
            writer.writeGenerated(descriptor, observation, (observation as? SceneObservation.Rendered)?.rgba)
        }
    }

    private fun writeSelectedV2(sceneIds: List<String>) {
        val selectedCases = sceneIds.map { id -> GpuEvidenceCatalog.cases.first { it.descriptor.id.value == id } }
        val writer = EvidenceBundleWriter(repository, COMMIT)
        val observations = linkedMapOf<String, SceneObservation>()
        val bundlePaths = linkedMapOf<String, Path>()
        selectedCases.forEach { evidenceCase ->
            val descriptor = evidenceCase.descriptor
            val environment = EvidenceEnvironment(COMMIT, "test", "1", "test", "17", EvidenceAdapter("test-adapter", "test-vendor", "test-device", "test-architecture", "test-description", false), 1L, "native", true)
            val rendered = descriptor.expectation is EvidenceExpectation.ShouldRender
            val routeId = routeId(evidenceCase.program)
            val route = RouteEvidence(routeId, "attempt", if (rendered) "Completed" else null, if (rendered) "rendered" else "refused", emptyList(), emptyList(), if (rendered) mapOf("queue.submit" to 1L, "render.draw" to 1L, "render.pipelineBind" to 1L) else emptyMap(), GPUBackendRuntimeTelemetry(submissions = if (rendered) 1L else 0L))
            val observation = if (rendered) {
                val pixels = requireNotNull(evidenceCase.oracle).render(descriptor.width, descriptor.height)
                val comparison = EvidenceComparator().compare(pixels, pixels, descriptor.width, descriptor.height, requireNotNull(descriptor.comparison))
                SceneObservation.Rendered(pixels, route, emptyList(), environment, comparison)
            } else {
                val reason = (descriptor.expectation as EvidenceExpectation.ShouldRefuse).stableReasonCode
                SceneObservation.Refused(reason, "test", 0, route, emptyList(), environment)
            }
            observations[descriptor.id.value] = observation
            bundlePaths[descriptor.id.value] = if (observation is SceneObservation.Rendered) {
                writer.writeGeneratedV2(descriptor, observation, observation.rgba, "attempt-${descriptor.id.value}")
            } else {
                writer.writeGeneratedV2(descriptor, observation as SceneObservation.Refused, attemptId = "attempt-${descriptor.id.value}")
            }
        }
        EvidenceCatalogWriter(repository).writeGeneratedCatalog(
            root = generatedRoot(),
            selection = EvidenceSelection.Explicit(sceneIds),
            observations = observations,
            bundlePaths = bundlePaths,
        )
    }

    private fun routeId(program: org.graphiks.kanvas.gpu.evidence.runner.EvidenceProgram): String = when (program) {
        is KanvasSurfaceProgram -> program.routeId
        is RoutedSceneProgram -> program.routeId
        else -> error("unsupported evidence program: ${program::class.qualifiedName}")
    }

    private fun generatedRoot() = repository.resolve("reports/gpu-renderer/evidence/correctness/generated/$COMMIT")

    private fun promotedRoot() = repository.resolve("reports/gpu-renderer/evidence/correctness/promoted")

    private fun writePromotedV2() {
        writeSelectedV2(GpuEvidenceCatalog.cases.map { it.descriptor.id.value }.sorted())
        val generated = generatedRoot()
        val promoted = promotedRoot()
        Files.createDirectories(promoted)
        Files.copy(generated.resolve("environment.json"), promoted.resolve("environment.json"))
        Files.writeString(
            promoted.resolve("catalog.json"),
            Files.readString(generated.resolve("catalog.json")).replace("\"promotion\":null", "\"promotion\":\"promotion.json\""),
        )
        Files.write(
            promoted.resolve("promotion.json"),
            EvidencePromotionV2(
                schemaVersion = GPU_EVIDENCE_PROMOTION_SCHEMA_V2,
                promotedAtUtc = "1970-01-01T00:00:00Z",
                reviewer = "reviewer",
                reason = "initial",
                rebaseline = false,
                sceneIds = GpuEvidenceCatalog.cases.map { it.descriptor.id.value }.sorted(),
                priorComparison = null,
                newComparison = null,
            ).toJson().canonicalBytes(),
        )
        GpuEvidenceCatalog.cases.map { it.descriptor.id.value }.sorted().forEach { sceneId ->
            copyTree(generated.resolve(sceneId), promoted.resolve(sceneId))
        }
    }

    private fun deleteTree(path: Path) {
        if (!Files.exists(path)) return
        Files.walk(path).use { stream -> stream.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
    }

    private fun copyTree(source: Path, destination: Path) {
        Files.walk(source).use { stream ->
            stream.forEach { current ->
                val relative = source.relativize(current)
                val target = destination.resolve(relative)
                if (Files.isDirectory(current)) Files.createDirectories(target) else Files.copy(current, target)
            }
        }
    }

    private fun replaceAndRefresh(scene: Path, file: String, from: String, to: String) {
        val path = scene.resolve(file)
        Files.writeString(path, Files.readString(path).replace(from, to))
        val manifest = scene.resolve("manifest.json")
        val text = Files.readString(manifest)
        val hash = java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).joinToString("") { "%02x".format(it) }
        val key = "\"$file\":\""
        val start = text.indexOf(key) + key.length
        val end = text.indexOf('"', start)
        Files.writeString(manifest, text.substring(0, start) + hash + text.substring(end))
    }

    companion object {
        private const val COMMIT = "0123456789abcdef0123456789abcdef01234567"
        private const val OTHER_COMMIT = "fedcba9876543210fedcba9876543210fedcba98"
    }
}
