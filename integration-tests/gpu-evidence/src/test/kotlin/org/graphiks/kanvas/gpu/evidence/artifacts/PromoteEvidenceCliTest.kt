package org.graphiks.kanvas.gpu.evidence.artifacts

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.boolean
import org.graphiks.kanvas.gpu.evidence.catalog.EvidenceEnvironment
import org.graphiks.kanvas.gpu.evidence.catalog.EvidenceAdapter
import org.graphiks.kanvas.gpu.evidence.catalog.EvidenceExpectation
import org.graphiks.kanvas.gpu.evidence.catalog.EvidenceSceneDescriptor
import org.graphiks.kanvas.gpu.evidence.catalog.EvidenceSceneId
import org.graphiks.kanvas.gpu.evidence.catalog.ImageComparison
import org.graphiks.kanvas.gpu.evidence.catalog.OraclePolicy
import org.graphiks.kanvas.gpu.evidence.catalog.RouteEvidence
import org.graphiks.kanvas.gpu.evidence.catalog.SceneObservation
import org.graphiks.kanvas.gpu.evidence.catalog.GpuEvidenceCatalog
import org.graphiks.kanvas.gpu.evidence.compare.EvidenceComparator
import org.graphiks.kanvas.gpu.evidence.programs.KanvasSurfaceProgram
import org.graphiks.kanvas.gpu.evidence.runner.RoutedSceneProgram
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeTelemetry
import org.junit.jupiter.api.io.TempDir

class PromoteEvidenceCliTest {
    @TempDir
    lateinit var repository: Path

    @Test
    fun `promotion rejects a generated root that is not independently verified`() {
        val commit = COMMIT
        val generated = repository.resolve("reports/gpu-renderer/evidence/correctness/generated/$commit")
        Files.createDirectories(generated.resolve("solid-card-stack"))

        val result = PromoteEvidenceCliRunner().run(args(repository, commit))

        assertTrue(result != 0)
        assertFalse(Files.exists(promotedRoot(repository).resolve("solid-card-stack")))
    }

    @Test
    fun `promotion rejects source commit mismatch and failed or unavailable bundles`() {
        writeAllBundles(repository, COMMIT)
        val mismatch = PromoteEvidenceCliRunner().run(args(repository, OTHER_COMMIT))
        assertTrue(mismatch != 0)
        assertFalse(Files.exists(promotedRoot(repository).resolve("solid-card-stack")))

        val failedManifest = generatedRoot(repository).resolve("solid-card-stack/manifest.json")
        Files.writeString(failedManifest, Files.readString(failedManifest).replace("\"observedOutcome\":\"rendered\"", "\"observedOutcome\":\"unavailable\""))
        val failed = PromoteEvidenceCliRunner().run(args(repository, COMMIT))
        assertTrue(failed != 0)
        assertFalse(Files.exists(promotedRoot(repository).resolve("solid-card-stack")))
    }

    @Test
    fun `promotion rejects a coherent fail bundle without mutating the destination`() {
        writeAllBundles(repository, COMMIT)
        val descriptor = GpuEvidenceCatalog.renderCases.first { it.descriptor.id.value == "solid-card-stack" }.descriptor
        val environment = EvidenceEnvironment(COMMIT, "test", "1", "test", "17", EvidenceAdapter("fake-adapter", null, null, null, null, null), null, null, true)
        val route = RouteEvidence("fail-route", "attempt", "Completed", "rendered", emptyList(), emptyList(), mapOf("queue.submit" to 1L), GPUBackendRuntimeTelemetry(submissions = 1L))
        val pixels = ByteArray(descriptor.width * descriptor.height * 4)
        val oracle = requireNotNull(GpuEvidenceCatalog.renderCases.first { it.descriptor.id.value == "solid-card-stack" }.oracle).render(descriptor.width, descriptor.height)
        val comparison = EvidenceComparator().compare(pixels, oracle, descriptor.width, descriptor.height, requireNotNull(descriptor.comparison))
        EvidenceBundleWriter(repository, COMMIT).writeGenerated(
            descriptor,
            SceneObservation.Rendered(
                pixels,
                route,
                emptyList(),
                environment,
                comparison,
            ),
            oracle,
        )
        val stderr = ByteArrayOutputStream()

        val result = PromoteEvidenceCliRunner(stderr = PrintStream(stderr)).run(args(repository, COMMIT))

        assertTrue(result != 0)
        assertFalse(Files.exists(promotedRoot(repository)))
        assertTrue(stderr.toString().contains("generated evidence failed independent verification"))
    }

    @Test
    fun `promotion requires reviewer and reason metadata`() {
        writeAllBundles(repository, COMMIT)

        assertTrue(PromoteEvidenceCliRunner().run(args(repository, COMMIT, reviewer = "")) != 0)
        assertTrue(PromoteEvidenceCliRunner().run(args(repository, COMMIT, reason = "")) != 0)
    }

    @Test
    fun `promotion rejects existing destination without rebaseline and requires comparison metrics`() {
        writeAllBundles(repository, COMMIT)
        val destination = promotedRoot(repository).resolve("solid-card-stack")
        Files.createDirectories(destination)
        Files.writeString(destination.resolve("sentinel"), "keep")

        val withoutRebaseline = PromoteEvidenceCliRunner().run(args(repository, COMMIT))
        assertTrue(withoutRebaseline != 0)
        assertTrue(Files.exists(destination.resolve("sentinel")))

        val withoutMetrics = PromoteEvidenceCliRunner().run(args(repository, COMMIT, rebaseline = true))
        assertTrue(withoutMetrics != 0)
        assertTrue(Files.exists(destination.resolve("sentinel")))
    }

    @Test
    fun `promotion verifies every current catalog source before replacing destinations`() {
        writeAllBundles(repository, COMMIT)
        val result = PromoteEvidenceCliRunner().run(args(repository, COMMIT, reviewer = "reviewer", reason = "initial"))

        assertEquals(0, result)
        assertEquals(GpuEvidenceCatalog.cases.map { it.descriptor.id.value }.toSet(), sceneDirectories(promotedRoot(repository)))
        val metadata = Files.readString(promotedRoot(repository).resolve("solid-card-stack/promotion.json"))
        val json = EvidenceJson.parseToJsonElement(metadata).jsonObject
        assertEquals("gpu-evidence-promotion-v1", json["schemaVersion"]!!.jsonPrimitive.content)
        assertEquals(COMMIT, json["sourceCommit"]!!.jsonPrimitive.content)
        assertEquals("reviewer", json["reviewer"]!!.jsonPrimitive.content)
        assertEquals("initial", json["reason"]!!.jsonPrimitive.content)
        assertEquals(false, json["rebaseline"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun `rebaseline replaces existing scenes only with old and new comparison summaries`() {
        writeAllBundles(repository, COMMIT)
        assertEquals(0, PromoteEvidenceCliRunner().run(args(repository, COMMIT, reviewer = "reviewer", reason = "initial")))

        writeAllBundles(repository, COMMIT)
        val result = PromoteEvidenceCliRunner().run(
            args(repository, COMMIT, reviewer = "reviewer", reason = "reviewed rebaseline", rebaseline = true)
                .toList().toTypedArray() + arrayOf("--prior-comparison", "old=100.0", "--new-comparison", "new=99.9"),
        )

        assertEquals(0, result)
        val json = EvidenceJson.parseToJsonElement(Files.readString(promotedRoot(repository).resolve("solid-card-stack/promotion.json"))).jsonObject
        assertEquals(true, json["rebaseline"]!!.jsonPrimitive.boolean)
        assertEquals("old=100.0", json["priorComparison"]!!.jsonPrimitive.content)
        assertEquals("new=99.9", json["newComparison"]!!.jsonPrimitive.content)
    }

    @Test
    fun `late catalog root swap failure restores the old promoted tree byte for byte`() {
        writeAllBundles(repository, COMMIT)
        assertEquals(0, PromoteEvidenceCliRunner().run(args(repository, COMMIT, reviewer = "reviewer", reason = "initial")))
        val before = snapshot(promotedRoot(repository))
        writeAllBundles(repository, COMMIT)
        var moves = 0
        val result = PromoteEvidenceCliRunner(
            moveStrategy = { source, destination, _ ->
                moves++
                if (moves == 2) throw IOException("injected late swap failure")
                Files.move(source, destination)
            },
        ).run(args(repository, COMMIT, reviewer = "reviewer", reason = "rebaseline", rebaseline = true).toList().toTypedArray() + arrayOf("--prior-comparison", "old", "--new-comparison", "new"))

        assertTrue(result != 0)
        assertTrue(moves >= 3)
        assertEquals(before, snapshot(promotedRoot(repository)))
    }

    @Test
    fun `failed rollback preserves the backup root for recovery`() {
        writeAllBundles(repository, COMMIT)
        assertEquals(0, PromoteEvidenceCliRunner().run(args(repository, COMMIT, reviewer = "reviewer", reason = "initial")))
        val before = snapshot(promotedRoot(repository))
        writeAllBundles(repository, COMMIT)
        var moves = 0
        val stderr = ByteArrayOutputStream()
        val result = PromoteEvidenceCliRunner(
            stderr = PrintStream(stderr),
            moveStrategy = { source, destination, _ ->
                moves++
                if (moves == 2 || moves == 3) throw IOException("injected swap and restore failure")
                Files.move(source, destination)
            },
        ).run(args(repository, COMMIT, reviewer = "reviewer", reason = "rebaseline", rebaseline = true).toList().toTypedArray() + arrayOf("--prior-comparison", "old", "--new-comparison", "new"))

        assertTrue(result != 0)
        assertFalse(Files.exists(promotedRoot(repository)))
        val backup = Files.list(promotedRoot(repository).parent).use { stream ->
            stream.iterator().asSequence().firstOrNull { it.fileName.toString().startsWith(".promoted.backup-") }
        }
        assertTrue(backup != null)
        assertEquals(before, snapshot(requireNotNull(backup).resolve("promoted")))
        assertTrue(stderr.toString().contains(requireNotNull(backup).toString()))
    }

    @Test
    fun `promotion preserves the primary swap failure when staged cleanup also fails`() {
        writeAllBundles(repository, COMMIT)
        val stderr = ByteArrayOutputStream()

        val result = PromoteEvidenceCliRunner(
            stderr = PrintStream(stderr),
            moveStrategy = { _, _, _ -> throw IOException("primary swap failure") },
            cleanupStrategy = { throw IOException("staged cleanup failure") },
        ).run(args(repository, COMMIT))

        assertTrue(result != 0)
        assertTrue(stderr.toString().contains("primary swap failure"))
        assertTrue(stderr.toString().contains("staged cleanup failure"))
    }

    @Test
    fun `rebaseline preflight rejects an extra promoted entry before staging`() {
        writeAllBundles(repository, COMMIT)
        assertEquals(0, PromoteEvidenceCliRunner().run(args(repository, COMMIT, reviewer = "reviewer", reason = "initial")))
        Files.createDirectory(promotedRoot(repository).resolve("unexpected-scene"))
        writeAllBundles(repository, COMMIT)

        val result = PromoteEvidenceCliRunner().run(args(repository, COMMIT, rebaseline = true).toList().toTypedArray() + arrayOf("--prior-comparison", "old", "--new-comparison", "new"))

        assertTrue(result != 0)
        assertTrue(Files.isDirectory(promotedRoot(repository).resolve("unexpected-scene")))
    }

    @Test
    fun `blank rebaseline summaries are rejected`() {
        writeAllBundles(repository, COMMIT)
        val result = PromoteEvidenceCliRunner().run(args(repository, COMMIT, rebaseline = true).toList().toTypedArray() + arrayOf("--prior-comparison", " ", "--new-comparison", "new"))
        assertTrue(result != 0)
    }

    @Test
    fun `tampered staged promotion metadata is rejected before swap`() {
        writeAllBundles(repository, COMMIT)
        val result = PromoteEvidenceCliRunner(
            beforeStagedVerification = { staged ->
                val metadata = staged.resolve("solid-card-stack/promotion.json")
                Files.writeString(metadata, Files.readString(metadata).replace("\"reason\":\"reason\"", "\"reason\":\"\""))
            },
        ).run(args(repository, COMMIT))

        assertTrue(result != 0)
        assertFalse(Files.exists(promotedRoot(repository)))
    }

    @Test
    fun `generation writer cannot write into canonical promoted tree`() {
        val promoted = promotedRoot(repository)
        assertFailsWith<IllegalArgumentException> {
            writeAllBundles(promoted, COMMIT)
        }
    }

    private fun args(
        root: Path,
        commit: String,
        reviewer: String = "reviewer",
        reason: String = "reason",
        rebaseline: Boolean = false,
    ): Array<String> = buildList {
        add("--repository-root"); add(root.toString())
        add("--source-commit"); add(commit)
        add("--all")
        add("--reviewer"); add(reviewer)
        add("--reason"); add(reason)
        if (rebaseline) add("--rebaseline")
    }.toTypedArray()

    private fun writeAllBundles(root: Path, commit: String) {
        val writer = EvidenceBundleWriter(root, commit)
        GpuEvidenceCatalog.cases.forEach { evidenceCase ->
            val descriptor = evidenceCase.descriptor
            val environment = EvidenceEnvironment(commit, "test", "1", "test", "17", EvidenceAdapter("fake-adapter", null, null, null, null, null), null, null, true)
            val rendered = descriptor.expectation is EvidenceExpectation.ShouldRender
            val route = RouteEvidence(routeId(evidenceCase.program), "attempt", if (rendered) "Completed" else null, if (rendered) "rendered" else "refused", emptyList(), emptyList(), if (rendered) mapOf("queue.submit" to 1L) else emptyMap(), GPUBackendRuntimeTelemetry(submissions = if (rendered) 1L else 0L))
            val observation = when (descriptor.expectation) {
                EvidenceExpectation.ShouldRender -> {
                    val pixels = requireNotNull(evidenceCase.oracle).render(descriptor.width, descriptor.height)
                    SceneObservation.Rendered(pixels, route, emptyList(), environment, EvidenceComparator().compare(pixels, pixels, descriptor.width, descriptor.height, requireNotNull(descriptor.comparison)))
                }
                is EvidenceExpectation.ShouldRefuse -> SceneObservation.Refused(descriptor.expectation.stableReasonCode, "test refusal", 0, route, emptyList(), environment)
            }
            writer.writeGenerated(descriptor, observation, if (observation is SceneObservation.Rendered) observation.rgba else null)
        }
    }

    private fun routeId(program: org.graphiks.kanvas.gpu.evidence.runner.EvidenceProgram): String = when (program) {
        is KanvasSurfaceProgram -> program.routeId
        is RoutedSceneProgram -> program.routeId
        else -> error("unsupported evidence program: ${program::class.qualifiedName}")
    }

    private fun generatedRoot(root: Path) = root.resolve("reports/gpu-renderer/evidence/correctness/generated/$COMMIT")
    private fun promotedRoot(root: Path) = root.resolve("reports/gpu-renderer/evidence/correctness/promoted")
    private fun sceneDirectories(root: Path): Set<String> = if (!Files.exists(root)) emptySet() else Files.list(root).use { it.filter(Files::isDirectory).map { path -> path.fileName.toString() }.toList().toSet() }
    private fun snapshot(root: Path): Map<String, List<Byte>> = if (!Files.exists(root)) emptyMap() else Files.walk(root).use { stream ->
        stream.iterator().asSequence().filter(Files::isRegularFile).associate { root.relativize(it).toString() to Files.readAllBytes(it).toList() }
    }

    companion object {
        private const val COMMIT = "0123456789abcdef0123456789abcdef01234567"
        private const val OTHER_COMMIT = "fedcba9876543210fedcba9876543210fedcba98"
    }
}
