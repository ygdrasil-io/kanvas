package org.graphiks.kanvas.gpu.evidence.artifacts

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.PrintStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Comparator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.graphiks.kanvas.gpu.evidence.catalog.EvidenceAdapter
import org.graphiks.kanvas.gpu.evidence.catalog.EvidenceCase
import org.graphiks.kanvas.gpu.evidence.catalog.EvidenceEnvironment
import org.graphiks.kanvas.gpu.evidence.catalog.EvidenceExpectation
import org.graphiks.kanvas.gpu.evidence.catalog.GpuEvidenceCatalog
import org.graphiks.kanvas.gpu.evidence.catalog.RouteEvidence
import org.graphiks.kanvas.gpu.evidence.catalog.SceneObservation
import org.graphiks.kanvas.gpu.evidence.compare.EvidenceComparator
import org.graphiks.kanvas.gpu.evidence.programs.KanvasSurfaceProgram
import org.graphiks.kanvas.gpu.evidence.runner.RoutedSceneProgram
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeTelemetry
import org.junit.jupiter.api.io.TempDir

class MigratePromotedEvidenceCliTest {
    @TempDir
    lateinit var repository: Path

    @Test
    fun `migration converts a complete promoted v1 root into v2 without changing evidence bytes`() {
        writePromotedV1Root(repository, COMMIT)
        val beforeHashes = sceneImageHashes(promotedRoot(repository))
        val beforeVerdicts = sceneVerdicts(promotedRoot(repository))
        val beforeCommits = sceneManifestCommits(promotedRoot(repository))

        val result = MigratePromotedEvidenceCliRunner(clock = FIXED_CLOCK).run(args(repository))

        assertEquals(0, result)
        val root = promotedRoot(repository)
        assertEquals(allSceneIds().toSet(), sceneDirectories(root))
        assertTrue(Files.isRegularFile(root.resolve("catalog.json")))
        assertTrue(Files.isRegularFile(root.resolve("environment.json")))
        assertTrue(Files.isRegularFile(root.resolve("promotion.json")))
        assertEquals(beforeHashes, sceneImageHashes(root))
        assertEquals(beforeVerdicts, sceneVerdicts(root))
        allSceneIds().forEach { sceneId ->
            val manifest = readJson(root.resolve("$sceneId/manifest.json"))
            assertEquals(GPU_EVIDENCE_SCENE_SCHEMA_V2, manifest["schemaVersion"]!!.jsonPrimitive.content)
            assertFalse("sourceCommit" in manifest.keys)
            assertFalse("generatedAtUtc" in manifest.keys)
            assertFalse(Files.exists(root.resolve("$sceneId/environment.json")))
            assertFalse(Files.exists(root.resolve("$sceneId/promotion.json")))
            assertEquals(beforeCommits.getValue(sceneId), catalogEntry(root, sceneId)["sourceCommit"]!!.jsonPrimitive.content)
        }
        val environment = readJson(root.resolve("environment.json"))
        assertEquals(GPU_EVIDENCE_CATALOG_SCHEMA_V2, environment["schemaVersion"]!!.jsonPrimitive.content)
        val promotion = readJson(root.resolve("promotion.json"))
        assertEquals(GPU_EVIDENCE_PROMOTION_SCHEMA_V2, promotion["schemaVersion"]!!.jsonPrimitive.content)
        assertEquals("reviewer", promotion["reviewer"]!!.jsonPrimitive.content)
        assertEquals("reason", promotion["reason"]!!.jsonPrimitive.content)
        assertEquals(true, promotion["rebaseline"]!!.jsonPrimitive.boolean)
        assertEquals(FIXED_CLOCK.instant().toString(), promotion["promotedAtUtc"]!!.jsonPrimitive.content)
        assertEquals(allSceneIds(), promotion["sceneIds"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertTrue(promotion["priorComparison"] is JsonNull)
        assertTrue(promotion["newComparison"] is JsonNull)
        assertEquals(0, VerifyEvidenceCliRunner().run(arrayOf("--root", root.toString(), "--all")))
    }

    @Test
    fun `migration rejects a missing promoted root without creating v2 metadata`() {
        val result = MigratePromotedEvidenceCliRunner().run(args(repository))

        assertTrue(result != 0)
        assertFalse(Files.exists(promotedRoot(repository).resolve("catalog.json")))
        assertFalse(Files.exists(promotedRoot(repository).resolve("environment.json")))
        assertFalse(Files.exists(promotedRoot(repository).resolve("promotion.json")))
    }

    @Test
    fun `migration rejects incomplete v1 promoted evidence before conversion`() {
        writePromotedV1Root(repository, COMMIT)
        val root = promotedRoot(repository)
        deleteTree(root.resolve("solid-card-stack"))
        val before = snapshot(root)

        val result = MigratePromotedEvidenceCliRunner().run(args(repository))

        assertTrue(result != 0)
        assertEquals(before, snapshot(root))
        assertFalse(Files.exists(root.resolve("catalog.json")))
    }

    @Test
    fun `migration rejects mixed promoted roots without mutating v1`() {
        writePromotedV1Root(repository, COMMIT)
        val root = promotedRoot(repository)
        Files.writeString(root.resolve("catalog.json"), "{}")
        val before = snapshot(root)

        val result = MigratePromotedEvidenceCliRunner().run(args(repository))

        assertTrue(result != 0)
        assertEquals(before, snapshot(root))
        assertTrue(Files.exists(root.resolve("solid-card-stack/environment.json")))
        assertTrue(Files.exists(root.resolve("solid-card-stack/promotion.json")))
    }

    @Test
    fun `migration rejects mixed v1 environments before staging`() {
        writePromotedV1Root(repository, COMMIT)
        val root = promotedRoot(repository)
        val environment = root.resolve("solid-card-stack/environment.json")
        Files.writeString(environment, Files.readString(environment).replace("\"osVersion\":\"1\"", "\"osVersion\":\"2\""))
        val before = snapshot(root)
        var stagedVerificationCalled = false

        val result = MigratePromotedEvidenceCliRunner(
            beforeStagedVerification = {
                stagedVerificationCalled = true
            },
        ).run(args(repository))

        assertTrue(result != 0)
        assertFalse(stagedVerificationCalled)
        assertEquals(before, snapshot(root))
        assertFalse(Files.exists(root.resolve("catalog.json")))
    }

    @Test
    fun `migration rejects malformed scene promotion metadata without mutating v1`() {
        writePromotedV1Root(repository, COMMIT)
        val root = promotedRoot(repository)
        val promotion = root.resolve("solid-card-stack/promotion.json")
        Files.writeString(promotion, Files.readString(promotion).replace("\"reason\":\"initial\"", "\"reason\":\"\""))
        val before = snapshot(root)

        val result = MigratePromotedEvidenceCliRunner().run(args(repository))

        assertTrue(result != 0)
        assertEquals(before, snapshot(root))
        assertFalse(Files.exists(root.resolve("environment.json")))
    }

    @Test
    fun `migration rejects invalid v1 rebaseline promotion metadata without mutating v1`() {
        writePromotedV1Root(repository, COMMIT)
        val root = promotedRoot(repository)
        val promotion = root.resolve("solid-card-stack/promotion.json")
        Files.writeString(
            promotion,
            Files.readString(promotion)
                .replace("\"rebaseline\":false", "\"rebaseline\":true")
                .replace("\"priorComparison\":null", "\"priorComparison\":\"\"")
                .replace("\"newComparison\":null", "\"newComparison\":\"\""),
        )
        val before = snapshot(root)

        val result = MigratePromotedEvidenceCliRunner().run(args(repository))

        assertTrue(result != 0)
        assertEquals(before, snapshot(root))
        assertFalse(Files.exists(root.resolve("environment.json")))
    }

    @Test
    fun `late migration swap failure restores the original v1 root byte for byte`() {
        writePromotedV1Root(repository, COMMIT)
        val before = snapshot(promotedRoot(repository))
        var moves = 0

        val result = MigratePromotedEvidenceCliRunner(
            clock = FIXED_CLOCK,
            moveStrategy = { source, destination, _ ->
                moves++
                if (moves == 2) throw IOException("injected late swap failure")
                Files.move(source, destination)
            },
        ).run(args(repository))

        assertTrue(result != 0)
        assertTrue(moves >= 3)
        assertEquals(before, snapshot(promotedRoot(repository)))
    }

    @Test
    fun `partial non-atomic swap failure restores the original v1 root byte for byte`() {
        writePromotedV1Root(repository, COMMIT)
        val before = snapshot(promotedRoot(repository))
        var fallbackEntered = false

        val result = MigratePromotedEvidenceCliRunner(
            clock = FIXED_CLOCK,
            moveStrategy = { source, destination, atomic ->
                if (source.fileName.toString().startsWith(".promoted.v2-staged-") && atomic) {
                    throw AtomicMoveNotSupportedException(source.toString(), destination.toString(), "injected fallback")
                }
                if (source.fileName.toString().startsWith(".promoted.v2-staged-") && !atomic) {
                    fallbackEntered = true
                    copyTree(source, destination)
                    throw IOException("injected partial non-atomic swap failure")
                }
                Files.move(source, destination)
            },
        ).run(args(repository))

        assertTrue(result != 0)
        assertTrue(fallbackEntered)
        assertEquals(before, snapshot(promotedRoot(repository)))
    }

    @Test
    fun `partial first migration backup move restores the original v1 root byte for byte`() {
        writePromotedV1Root(repository, COMMIT)
        val before = snapshot(promotedRoot(repository))

        val result = MigratePromotedEvidenceCliRunner(
            clock = FIXED_CLOCK,
            moveStrategy = { source, destination, _ ->
                if (source == promotedRoot(repository)) {
                    val sourceFile = Files.walk(source).use { stream ->
                        stream.filter(Files::isRegularFile).findFirst().orElseThrow()
                    }
                    val relative = source.relativize(sourceFile)
                    val partial = destination.resolve(relative)
                    Files.createDirectories(partial.parent)
                    Files.copy(sourceFile, partial)
                    throw IOException("injected partial migration backup move")
                }
                Files.move(source, destination)
            },
        ).run(args(repository))

        assertTrue(result != 0)
        assertEquals(before, snapshot(promotedRoot(repository)))
    }

    @Test
    fun `migration preserves the primary swap failure when staged cleanup also fails`() {
        writePromotedV1Root(repository, COMMIT)
        val stderr = ByteArrayOutputStream()

        val result = MigratePromotedEvidenceCliRunner(
            stderr = PrintStream(stderr),
            moveStrategy = { _, _, _ -> throw IOException("primary swap failure") },
            cleanupStrategy = { throw IOException("staged cleanup failure") },
        ).run(args(repository))

        assertTrue(result != 0)
        assertTrue(stderr.toString().contains("primary swap failure"))
        assertTrue(stderr.toString().contains("staged cleanup failure"))
    }

    private fun args(root: Path, reviewer: String = "reviewer", reason: String = "reason"): Array<String> = arrayOf(
        "--repository-root", root.toString(),
        "--reviewer", reviewer,
        "--reason", reason,
    )

    private fun writePromotedV1Root(
        root: Path,
        commit: String,
        environment: EvidenceEnvironment = environment(commit),
    ): Path {
        val writer = EvidenceBundleWriter(root, commit, FIXED_CLOCK)
        val generated = generatedRoot(root, commit)
        val promoted = promotedRoot(root)
        allCases().forEach { evidenceCase ->
            val descriptor = evidenceCase.descriptor
            val observation = observation(evidenceCase, environment.copy(sourceCommit = commit))
            writer.writeGenerated(descriptor, observation, (observation as? SceneObservation.Rendered)?.rgba, "attempt-${descriptor.id.value}")
        }
        Files.createDirectories(promoted)
        allSceneIds().forEach { sceneId ->
            copyTree(generated.resolve(sceneId), promoted.resolve(sceneId))
            Files.write(promoted.resolve("$sceneId/promotion.json"), promotionJsonV1(sceneId, commit))
        }
        deleteTree(root.resolve("reports/gpu-renderer/evidence/correctness/generated"))
        return promoted
    }

    private fun promotionJsonV1(sceneId: String, commit: String): ByteArray = buildJsonObject {
        put("schemaVersion", GPU_EVIDENCE_PROMOTION_SCHEMA)
        put("sceneId", sceneId)
        put("sourceCommit", commit)
        put("promotedAtUtc", FIXED_CLOCK.instant().toString())
        put("reviewer", "historical-reviewer")
        put("reason", "initial")
        put("rebaseline", false)
        put("priorComparison", JsonNull)
        put("newComparison", JsonNull)
    }.canonicalBytes()

    private fun allCases(): List<EvidenceCase> = GpuEvidenceCatalog.cases.sortedBy { it.descriptor.id.value }

    private fun allSceneIds(): List<String> = allCases().map { it.descriptor.id.value }

    private fun observation(evidenceCase: EvidenceCase, environment: EvidenceEnvironment): SceneObservation {
        val descriptor = evidenceCase.descriptor
        val rendered = descriptor.expectation is EvidenceExpectation.ShouldRender
        val routeId = routeId(evidenceCase)
        val route = RouteEvidence(
            routeId = routeId,
            attemptId = "attempt-${descriptor.id.value}",
            furthestPhase = if (rendered) "Completed" else null,
            outcome = if (rendered) "rendered" else "refused",
            encodedScopeKinds = emptyList(),
            structuralEvents = emptyList(),
            structuralCounters = if (rendered) mapOf("queue.submit" to 1L, "render.draw" to 1L, "render.pipelineBind" to 1L) else emptyMap(),
            runtimeTelemetryDelta = GPUBackendRuntimeTelemetry(submissions = if (rendered) 1L else 0L),
        )
        return when (val expectation = descriptor.expectation) {
            EvidenceExpectation.ShouldRender -> {
                val pixels = requireNotNull(evidenceCase.oracle).render(descriptor.width, descriptor.height)
                val comparison = EvidenceComparator().compare(pixels, pixels, descriptor.width, descriptor.height, requireNotNull(descriptor.comparison))
                SceneObservation.Rendered(pixels, route, emptyList(), environment, comparison)
            }
            is EvidenceExpectation.ShouldRefuse -> {
                SceneObservation.Refused(expectation.stableReasonCode, "test", 0L, route, emptyList(), environment)
            }
        }
    }

    private fun routeId(evidenceCase: EvidenceCase): String = when (val program = evidenceCase.program) {
        is KanvasSurfaceProgram -> program.routeId
        is RoutedSceneProgram -> program.routeId
        else -> error("unsupported evidence program: ${program::class.qualifiedName}")
    }

    private fun generatedRoot(root: Path, commit: String): Path =
        root.resolve("reports/gpu-renderer/evidence/correctness/generated/$commit")

    private fun promotedRoot(root: Path): Path =
        root.resolve("reports/gpu-renderer/evidence/correctness/promoted")

    private fun sceneDirectories(root: Path): Set<String> =
        Files.list(root).use { stream ->
            stream
                .filter { Files.isDirectory(it) }
                .map { it.fileName.toString() }
                .toList()
                .toSet()
        }

    private fun catalogEntry(root: Path, sceneId: String) =
        readJson(root.resolve("catalog.json"))["scenes"]!!.jsonArray.map { it.jsonObject }.first { it["sceneId"]!!.jsonPrimitive.content == sceneId }

    private fun sceneManifestCommits(root: Path): Map<String, String> =
        allSceneIds().associateWith { sceneId ->
            readJson(root.resolve("$sceneId/manifest.json"))["sourceCommit"]!!.jsonPrimitive.content
        }

    private fun sceneVerdicts(root: Path): Map<String, String> =
        allSceneIds().associateWith { sceneId ->
            Files.readString(root.resolve("$sceneId/verdict.json"))
        }

    private fun sceneImageHashes(root: Path): Map<String, Map<String, String>> =
        allSceneIds().associateWith { sceneId ->
            listOf("cpu.png", "skia.png", "gpu.png", "diff.png")
                .filter { Files.exists(root.resolve("$sceneId/$it")) }
                .associateWith { fileName -> sha256(Files.readAllBytes(root.resolve("$sceneId/$fileName"))) }
        }

    private fun readJson(path: Path) = EvidenceJson.parseToJsonElement(Files.readString(path)).jsonObject

    private fun snapshot(root: Path): Map<String, String> =
        Files.walk(root).use { stream ->
            stream.iterator().asSequence()
                .filter { Files.isRegularFile(it) }
                .sorted()
                .associate { path -> root.relativize(path).toString() to sha256(Files.readAllBytes(path)) }
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

    private fun deleteTree(path: Path) {
        if (!Files.exists(path)) return
        Files.walk(path).use { stream ->
            stream.sorted(Comparator.reverseOrder()).forEach(Files::delete)
        }
    }

    private fun environment(commit: String, osVersion: String = "1"): EvidenceEnvironment = EvidenceEnvironment(
        sourceCommit = commit,
        osName = "test",
        osVersion = osVersion,
        osArchitecture = "test",
        javaVersion = "17",
        adapter = EvidenceAdapter("test-adapter", "test-vendor", "test-device", "test-architecture", "test-description", false),
        deviceGeneration = 1L,
        capabilityImplementation = "native",
        available = true,
    )

    private fun sha256(value: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(value).joinToString("") { "%02x".format(it) }

    private companion object {
        private const val COMMIT = "0123456789abcdef0123456789abcdef01234567"
        private val FIXED_CLOCK: Clock = Clock.fixed(Instant.parse("1970-01-01T00:00:00Z"), ZoneOffset.UTC)
    }
}
