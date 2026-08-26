package org.graphiks.kanvas.gpu.evidence.artifacts

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Comparator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
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

class PromoteEvidenceCliTest {
    @TempDir
    lateinit var repository: Path

    @Test
    fun `promotion rejects a generated root that is not independently verified`() {
        val generated = generatedRoot(repository, COMMIT)
        Files.createDirectories(generated.resolve("solid-card-stack"))

        val result = PromoteEvidenceCliRunner().run(args(repository, COMMIT))

        assertTrue(result != 0)
        assertFalse(Files.exists(promotedRoot(repository)))
    }

    @Test
    fun `promotion rejects source commit mismatch and failed bundles`() {
        writeGeneratedRoot(repository, COMMIT, allSceneIds())

        val mismatch = PromoteEvidenceCliRunner().run(args(repository, OTHER_COMMIT))
        assertTrue(mismatch != 0)
        assertFalse(Files.exists(promotedRoot(repository)))

        val manifest = generatedRoot(repository, COMMIT).resolve("solid-card-stack/manifest.json")
        Files.writeString(
            manifest,
            Files.readString(manifest).replace("\"observedOutcome\":\"rendered\"", "\"observedOutcome\":\"refused\""),
        )

        val failed = PromoteEvidenceCliRunner().run(args(repository, COMMIT))
        assertTrue(failed != 0)
        assertFalse(Files.exists(promotedRoot(repository)))
    }

    @Test
    fun `promotion rejects tampered generated root environment without mutation`() {
        writeGeneratedRoot(repository, COMMIT, allSceneIds())
        val environment = generatedRoot(repository, COMMIT).resolve("environment.json")
        Files.writeString(
            environment,
            Files.readString(environment).replace("\"capabilityImplementation\":\"native\"", "\"capabilityImplementation\":\"software\""),
        )

        val result = PromoteEvidenceCliRunner().run(args(repository, COMMIT))

        assertTrue(result != 0)
        assertFalse(Files.exists(promotedRoot(repository)))
    }

    @Test
    fun `promotion requires reviewer and reason metadata`() {
        writeGeneratedRoot(repository, COMMIT, allSceneIds())

        assertTrue(PromoteEvidenceCliRunner().run(args(repository, COMMIT, reviewer = "")) != 0)
        assertTrue(PromoteEvidenceCliRunner().run(args(repository, COMMIT, reason = "")) != 0)
    }

    @Test
    fun `promotion request parser accepts explicit scene and scenes file selection`() {
        val scenesFile = Files.createTempFile("promotion-scenes", ".txt")
        Files.writeString(scenesFile, "custom-runtime-effect-unregistered-refusal\nsolid-card-stack\n")

        val explicit = PromoteEvidenceCliRequest.parse(
            arrayOf(
                "--repository-root", repository.toString(),
                "--source-commit", COMMIT,
                "--reviewer", "reviewer",
                "--reason", "reason",
                "--scene", "solid-card-stack",
            ),
        )
        assertEquals(EvidenceSelection.Explicit(listOf("solid-card-stack")), explicit.selection)

        val fromFile = PromoteEvidenceCliRequest.parse(
            arrayOf(
                "--repository-root", repository.toString(),
                "--source-commit", COMMIT,
                "--reviewer", "reviewer",
                "--reason", "reason",
                "--scenes-file", scenesFile.toString(),
            ),
        )
        assertEquals(
            EvidenceSelection.Explicit(listOf("custom-runtime-effect-unregistered-refusal", "solid-card-stack")),
            fromFile.selection,
        )

        val all = PromoteEvidenceCliRequest.parse(
            arrayOf(
                "--repository-root", repository.toString(),
                "--source-commit", COMMIT,
                "--reviewer", "reviewer",
                "--reason", "reason",
                "--all",
            ),
        )
        assertSame(EvidenceSelection.All, all.selection)
    }

    @Test
    fun `promotion request parser rejects unknown explicit scene ids`() {
        assertFailsWith<IllegalStateException> {
            PromoteEvidenceCliRequest.parse(
                arrayOf(
                    "--repository-root", repository.toString(),
                    "--source-commit", COMMIT,
                    "--reviewer", "reviewer",
                    "--reason", "reason",
                    "--scene", "unknown-scene",
                ),
            )
        }
    }

    @Test
    fun `initial promotion requires all and writes one root promotion record`() {
        writeGeneratedRoot(repository, COMMIT, allSceneIds())

        val selected = PromoteEvidenceCliRunner().run(
            args(repository, COMMIT, selection = arrayOf("--scene", "solid-card-stack")),
        )
        assertTrue(selected != 0)
        assertFalse(Files.exists(promotedRoot(repository)))

        val result = PromoteEvidenceCliRunner().run(args(repository, COMMIT, reviewer = "reviewer", reason = "initial"))

        assertEquals(0, result)
        assertEquals(allSceneIds().toSet(), sceneDirectories(promotedRoot(repository)))
        assertTrue(Files.isRegularFile(promotedRoot(repository).resolve("catalog.json")))
        assertTrue(Files.isRegularFile(promotedRoot(repository).resolve("environment.json")))
        assertTrue(Files.isRegularFile(promotedRoot(repository).resolve("promotion.json")))
        assertTrue(allSceneIds().all { !Files.exists(promotedRoot(repository).resolve(it).resolve("promotion.json")) })
        val promotion = rootPromotion(promotedRoot(repository))
        assertEquals(GPU_EVIDENCE_PROMOTION_SCHEMA_V2, promotion["schemaVersion"]!!.jsonPrimitive.content)
        assertEquals(allSceneIds().toSet(), promotionSceneIds(promotion))
        assertEquals(false, promotion["rebaseline"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun `initial all promotion accepts an empty promoted directory`() {
        writeGeneratedRoot(repository, COMMIT, allSceneIds())
        Files.createDirectories(promotedRoot(repository))

        val result = PromoteEvidenceCliRunner().run(args(repository, COMMIT))

        assertEquals(0, result)
        assertTrue(Files.isRegularFile(promotedRoot(repository).resolve("catalog.json")))
    }

    @Test
    fun `selected promotion keeps unselected bytes unchanged and updates catalog metadata`() {
        writePromotedRoot(repository, COMMIT)
        writeGeneratedRoot(repository, OTHER_COMMIT, listOf("solid-card-stack"))
        val beforeUnselectedBytes = snapshot(promotedRoot(repository).resolve("custom-runtime-effect-unregistered-refusal"))

        val result = PromoteEvidenceCliRunner().run(
            args(repository, OTHER_COMMIT, selection = arrayOf("--scene", "solid-card-stack"), reviewer = "reviewer", reason = "selected"),
        )

        assertEquals(0, result)
        val afterUnselectedBytes = snapshot(promotedRoot(repository).resolve("custom-runtime-effect-unregistered-refusal"))
        assertEquals(beforeUnselectedBytes, afterUnselectedBytes)
        val changedEntry = catalogEntry(promotedRoot(repository), "solid-card-stack")
        assertEquals(OTHER_COMMIT, changedEntry["sourceCommit"]!!.jsonPrimitive.content)
        val rootPromotion = rootPromotion(promotedRoot(repository))
        assertEquals(setOf("solid-card-stack"), promotionSceneIds(rootPromotion))
        assertFalse(Files.exists(promotedRoot(repository).resolve("solid-card-stack/promotion.json")))
    }

    @Test
    fun `selected promotion rejects an absent destination`() {
        writeGeneratedRoot(repository, COMMIT, listOf("solid-card-stack"))

        val result = PromoteEvidenceCliRunner().run(
            args(repository, COMMIT, selection = arrayOf("--scene", "solid-card-stack")),
        )

        assertTrue(result != 0)
        assertFalse(Files.exists(promotedRoot(repository)))
    }

    @Test
    fun `selected promotion rejects an absent selected generated bundle`() {
        writePromotedRoot(repository, COMMIT)
        writeGeneratedRoot(repository, OTHER_COMMIT, listOf("solid-card-stack"))
        deleteTree(generatedRoot(repository, OTHER_COMMIT).resolve("solid-card-stack"))
        val before = snapshot(promotedRoot(repository))

        val result = PromoteEvidenceCliRunner().run(
            args(repository, OTHER_COMMIT, selection = arrayOf("--scene", "solid-card-stack")),
        )

        assertTrue(result != 0)
        assertEquals(before, snapshot(promotedRoot(repository)))
    }

    @Test
    fun `selected promotion rejects an unknown generated scene`() {
        writePromotedRoot(repository, COMMIT)
        val generated = writeGeneratedRoot(repository, OTHER_COMMIT, listOf("solid-card-stack"))
        copyTree(generated.resolve("solid-card-stack"), generated.resolve("unknown-scene"))
        val before = snapshot(promotedRoot(repository))

        val result = PromoteEvidenceCliRunner().run(
            args(repository, OTHER_COMMIT, selection = arrayOf("--scene", "solid-card-stack")),
        )

        assertTrue(result != 0)
        assertEquals(before, snapshot(promotedRoot(repository)))
    }

    @Test
    fun `selected promotion rejects a different environment and requires rebaseline`() {
        writePromotedRoot(repository, COMMIT)
        writeGeneratedRoot(
            repository,
            OTHER_COMMIT,
            listOf("solid-card-stack"),
            environment = environment(OTHER_COMMIT, osVersion = "2"),
        )
        val stderr = ByteArrayOutputStream()
        val before = snapshot(promotedRoot(repository))

        val result = PromoteEvidenceCliRunner(stderr = PrintStream(stderr)).run(
            args(repository, OTHER_COMMIT, selection = arrayOf("--scene", "solid-card-stack")),
        )

        assertTrue(result != 0)
        assertEquals(before, snapshot(promotedRoot(repository)))
        assertTrue(stderr.toString().contains(EvidenceCatalogVerifier.ENVIRONMENT_MISMATCH_REQUIRES_REBASELINE))
    }

    @Test
    fun `selected promotion rejects a changed unselected staged scene without mutating the destination`() {
        writePromotedRoot(repository, COMMIT)
        writeGeneratedRoot(repository, OTHER_COMMIT, listOf("solid-card-stack"))
        val before = snapshot(promotedRoot(repository))

        val result = PromoteEvidenceCliRunner(
            beforeStagedVerification = { staged ->
                val route = staged.resolve("custom-runtime-effect-unregistered-refusal/route.json")
                Files.writeString(route, Files.readString(route).replace("\"routeId\":\"", "\"routeId\":\"tampered-"))
            },
        ).run(args(repository, OTHER_COMMIT, selection = arrayOf("--scene", "solid-card-stack")))

        assertTrue(result != 0)
        assertEquals(before, snapshot(promotedRoot(repository)))
    }

    @Test
    fun `selected promotion rejects a failed staged verification without mutating the destination`() {
        writePromotedRoot(repository, COMMIT)
        writeGeneratedRoot(repository, OTHER_COMMIT, listOf("solid-card-stack"))
        val before = snapshot(promotedRoot(repository))

        val result = PromoteEvidenceCliRunner(
            beforeStagedVerification = { staged ->
                val promotion = staged.resolve("promotion.json")
                Files.writeString(promotion, Files.readString(promotion).replace("\"reason\":\"reason\"", "\"reason\":\"\""))
            },
        ).run(args(repository, OTHER_COMMIT, selection = arrayOf("--scene", "solid-card-stack")))

        assertTrue(result != 0)
        assertEquals(before, snapshot(promotedRoot(repository)))
    }

    @Test
    fun `all rebaseline accepts comparison summaries and a changed environment`() {
        writePromotedRoot(repository, COMMIT)
        val changedEnvironment = environment(OTHER_COMMIT, osVersion = "2")
        writeGeneratedRoot(repository, OTHER_COMMIT, allSceneIds(), changedEnvironment)

        val result = PromoteEvidenceCliRunner().run(
            args(repository, OTHER_COMMIT, reviewer = "reviewer", reason = "rebaseline", rebaseline = true) +
                arrayOf("--prior-comparison", "old=100.0", "--new-comparison", "new=99.9"),
        )

        assertEquals(0, result)
        assertEquals(String(environmentJsonV2(changedEnvironment)), Files.readString(promotedRoot(repository).resolve("environment.json")))
        val promotion = rootPromotion(promotedRoot(repository))
        assertEquals(true, promotion["rebaseline"]!!.jsonPrimitive.boolean)
        assertEquals("old=100.0", promotion["priorComparison"]!!.jsonPrimitive.content)
        assertEquals("new=99.9", promotion["newComparison"]!!.jsonPrimitive.content)
        assertEquals(allSceneIds().toSet(), promotionSceneIds(promotion))
        assertEquals(OTHER_COMMIT, catalogEntry(promotedRoot(repository), "solid-card-stack")["sourceCommit"]!!.jsonPrimitive.content)
    }

    @Test
    fun `all rebaseline rejects corrupted root promotion metadata before swap`() {
        writePromotedRoot(repository, COMMIT)
        writeGeneratedRoot(repository, OTHER_COMMIT, allSceneIds(), environment(OTHER_COMMIT, osVersion = "2"))
        val before = snapshot(promotedRoot(repository))

        val result = PromoteEvidenceCliRunner(
            beforeStagedVerification = { staged ->
                val promotion = staged.resolve("promotion.json")
                Files.writeString(
                    promotion,
                    Files.readString(promotion)
                        .replace("\"rebaseline\":true", "\"rebaseline\":false")
                        .replace("\"priorComparison\":\"old=100.0\"", "\"priorComparison\":null")
                        .replace("\"newComparison\":\"new=99.9\"", "\"newComparison\":null"),
                )
            },
        ).run(
            args(repository, OTHER_COMMIT, reviewer = "reviewer", reason = "rebaseline", rebaseline = true) +
                arrayOf("--prior-comparison", "old=100.0", "--new-comparison", "new=99.9"),
        )

        assertTrue(result != 0)
        assertEquals(before, snapshot(promotedRoot(repository)))
    }

    @Test
    fun `late catalog root swap failure restores the old promoted tree byte for byte`() {
        writePromotedRoot(repository, COMMIT)
        val before = snapshot(promotedRoot(repository))
        writeGeneratedRoot(repository, OTHER_COMMIT, allSceneIds(), environment(OTHER_COMMIT, osVersion = "2"))
        var moves = 0

        val result = PromoteEvidenceCliRunner(
            moveStrategy = { source, destination, _ ->
                moves++
                if (moves == 2) throw IOException("injected late swap failure")
                Files.move(source, destination)
            },
        ).run(
            args(repository, OTHER_COMMIT, reviewer = "reviewer", reason = "rebaseline", rebaseline = true) +
                arrayOf("--prior-comparison", "old", "--new-comparison", "new"),
        )

        assertTrue(result != 0)
        assertTrue(moves >= 3)
        assertEquals(before, snapshot(promotedRoot(repository)))
    }

    @Test
    fun `partial non-atomic catalog root install restores the old promoted tree byte for byte`() {
        writePromotedRoot(repository, COMMIT)
        val before = snapshot(promotedRoot(repository))
        writeGeneratedRoot(repository, OTHER_COMMIT, allSceneIds(), environment(OTHER_COMMIT, osVersion = "2"))
        var partialInstall = false

        val result = PromoteEvidenceCliRunner(
            moveStrategy = { source, destination, _ ->
                if (source.fileName.toString().startsWith(".promoted.staged-")) {
                    copyTree(source, destination)
                    partialInstall = true
                    throw IOException("injected partial non-atomic catalog install")
                }
                Files.move(source, destination)
            },
        ).run(
            args(repository, OTHER_COMMIT, reviewer = "reviewer", reason = "rebaseline", rebaseline = true) +
                arrayOf("--prior-comparison", "old", "--new-comparison", "new"),
        )

        assertTrue(result != 0)
        assertTrue(partialInstall)
        assertEquals(before, snapshot(promotedRoot(repository)))
    }

    @Test
    fun `failed rollback preserves the backup root for recovery`() {
        writePromotedRoot(repository, COMMIT)
        val before = snapshot(promotedRoot(repository))
        writeGeneratedRoot(repository, OTHER_COMMIT, allSceneIds(), environment(OTHER_COMMIT, osVersion = "2"))
        var moves = 0
        val stderr = ByteArrayOutputStream()

        val result = PromoteEvidenceCliRunner(
            stderr = PrintStream(stderr),
            moveStrategy = { source, destination, _ ->
                moves++
                if (moves == 2 || moves == 3) throw IOException("injected swap and restore failure")
                Files.move(source, destination)
            },
        ).run(
            args(repository, OTHER_COMMIT, reviewer = "reviewer", reason = "rebaseline", rebaseline = true) +
                arrayOf("--prior-comparison", "old", "--new-comparison", "new"),
        )

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
        writeGeneratedRoot(repository, COMMIT, allSceneIds())
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

    private fun args(
        root: Path,
        commit: String,
        reviewer: String = "reviewer",
        reason: String = "reason",
        rebaseline: Boolean = false,
        selection: Array<String> = arrayOf("--all"),
    ): Array<String> = buildList {
        add("--repository-root"); add(root.toString())
        add("--source-commit"); add(commit)
        addAll(selection.asList())
        add("--reviewer"); add(reviewer)
        add("--reason"); add(reason)
        if (rebaseline) add("--rebaseline")
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
        promotedSceneIds: List<String> = allSceneIds(),
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
                sceneIds = promotedSceneIds,
                priorComparison = null,
                newComparison = null,
            ).toJson().canonicalBytes(),
        )
        return promoted
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

    private fun sceneDirectories(root: Path): Set<String> =
        if (!Files.exists(root)) {
            emptySet()
        } else {
            Files.list(root).use { stream ->
                stream.iterator().asSequence().filter(Files::isDirectory).map { it.fileName.toString() }.toSet()
            }
        }

    private fun rootPromotion(root: Path): JsonObject =
        EvidenceJson.parseToJsonElement(Files.readString(root.resolve("promotion.json"))).jsonObject

    private fun promotionSceneIds(promotion: JsonObject): Set<String> =
        promotion["sceneIds"]!!.jsonArray.map { it.jsonPrimitive.content }.toSet()

    private fun catalogEntry(root: Path, sceneId: String): JsonObject {
        val catalog = EvidenceJson.parseToJsonElement(Files.readString(root.resolve("catalog.json"))).jsonObject
        return catalog["scenes"]!!.jsonArray.map { it.jsonObject }.first { entry ->
            entry["sceneId"]!!.jsonPrimitive.content == sceneId
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

    private fun deleteTree(path: Path) {
        if (!Files.exists(path)) return
        Files.walk(path).sorted(Comparator.reverseOrder()).forEach(Files::delete)
    }

    private fun environment(sourceCommit: String, osVersion: String = "1"): EvidenceEnvironment =
        EvidenceEnvironment(
            sourceCommit = sourceCommit,
            osName = "test",
            osVersion = osVersion,
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
    }
}
