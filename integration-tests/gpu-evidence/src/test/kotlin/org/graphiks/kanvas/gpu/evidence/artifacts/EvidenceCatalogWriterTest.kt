package org.graphiks.kanvas.gpu.evidence.artifacts

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import org.graphiks.kanvas.gpu.evidence.catalog.ComparisonPolicy
import org.graphiks.kanvas.gpu.evidence.catalog.EvidenceAdapter
import org.graphiks.kanvas.gpu.evidence.catalog.EvidenceEnvironment
import org.graphiks.kanvas.gpu.evidence.catalog.EvidenceExpectation
import org.graphiks.kanvas.gpu.evidence.catalog.EvidenceSceneDescriptor
import org.graphiks.kanvas.gpu.evidence.catalog.EvidenceSceneId
import org.graphiks.kanvas.gpu.evidence.catalog.ImageComparison
import org.graphiks.kanvas.gpu.evidence.catalog.OraclePolicy
import org.graphiks.kanvas.gpu.evidence.catalog.RouteEvidence
import org.graphiks.kanvas.gpu.evidence.catalog.SceneObservation
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeTelemetry

class EvidenceCatalogWriterTest {
    @Test fun `catalog writer emits deterministic root metadata for selected v2 bundles`() {
        val repository = Files.createTempDirectory("gpu-evidence")
        val writer = EvidenceBundleWriter(repository, COMMIT, FIXED_CLOCK)
        val rendered = renderedObservation()
        val refused = refusedObservation()
        val renderedPath = writer.writeGeneratedV2(renderDescriptor(), rendered, PIXELS, "attempt-render")
        val refusedPath = writer.writeGeneratedV2(refusalDescriptor(), refused, attemptId = "attempt-refuse")
        val generatedRoot = repository.resolve("reports/gpu-renderer/evidence/correctness/generated/$COMMIT")

        val result = EvidenceCatalogWriter(repository).writeGeneratedCatalog(
            root = generatedRoot,
            selection = EvidenceSelection.Explicit(listOf("render-scene", "refusal-scene")),
            observations = linkedMapOf("render-scene" to rendered, "refusal-scene" to refused),
            bundlePaths = linkedMapOf("render-scene" to renderedPath, "refusal-scene" to refusedPath),
        )

        assertEquals(generatedRoot, result)
        val environment = json(generatedRoot, "environment.json")
        assertEquals(setOf("schemaVersion", "osName", "osVersion", "osArchitecture", "javaVersion", "deviceGeneration", "capabilityImplementation", "available", "adapter"), environment.keys)
        assertEquals(GPU_EVIDENCE_CATALOG_SCHEMA_V2, environment.string("schemaVersion"))
        assertFalse("sourceCommit" in environment)
        assertEquals("test", environment.string("osName"))
        assertEquals(1L, environment["deviceGeneration"]!!.jsonPrimitive.long)
        assertEquals(true, environment["available"]!!.jsonPrimitive.boolean)

        val catalogText = Files.readString(generatedRoot.resolve("catalog.json"))
        val catalog = EvidenceJson.parseToJsonElement(catalogText).jsonObject
        assertEquals(setOf("schemaVersion", "environment", "promotion", "scenes"), catalog.keys)
        assertEquals(GPU_EVIDENCE_CATALOG_SCHEMA_V2, catalog.string("schemaVersion"))
        assertEquals("environment.json", catalog.string("environment"))
        assertEquals(null, catalog["promotion"]!!.jsonPrimitive.contentOrNull)
        val scenes = catalog["scenes"]!!.jsonArray
        assertEquals(listOf("refusal-scene", "render-scene"), scenes.map { it.jsonObject.string("sceneId") })
        scenes.forEach { entry ->
            val scene = entry.jsonObject
            val manifestPath = generatedRoot.resolve(scene.string("sceneId")).resolve("manifest.json")
            assertEquals("${scene.string("sceneId")}/manifest.json", scene.string("manifest"))
            assertEquals(sha256(Files.readAllBytes(manifestPath)), scene.string("manifestSha256"))
            assertEquals(COMMIT, scene.string("sourceCommit"))
        }
        assertEquals(
            """{"schemaVersion":"gpu-evidence-catalog-v2","environment":"environment.json","promotion":null,"scenes":[{"sceneId":"refusal-scene","sourceCommit":"abc123","manifest":"refusal-scene/manifest.json","manifestSha256":"${sha256(Files.readAllBytes(refusedPath.resolve("manifest.json")))}"},{"sceneId":"render-scene","sourceCommit":"abc123","manifest":"render-scene/manifest.json","manifestSha256":"${sha256(Files.readAllBytes(renderedPath.resolve("manifest.json")))}"}]}""",
            catalogText,
        )
    }

    @Test fun `catalog writer rejects mixed environment identities beyond source commit`() {
        val repository = Files.createTempDirectory("gpu-evidence")
        val writer = EvidenceBundleWriter(repository, COMMIT, FIXED_CLOCK)
        val rendered = renderedObservation()
        val refused = refusedObservation().copy(environment = environment(osVersion = "2"))
        val generatedRoot = repository.resolve("reports/gpu-renderer/evidence/correctness/generated/$COMMIT")

        val failure = assertFailsWith<IllegalArgumentException> {
            EvidenceCatalogWriter(repository).writeGeneratedCatalog(
                root = generatedRoot,
                selection = EvidenceSelection.Explicit(listOf("render-scene", "refusal-scene")),
                observations = linkedMapOf("render-scene" to rendered, "refusal-scene" to refused),
                bundlePaths = linkedMapOf(
                    "render-scene" to writer.writeGeneratedV2(renderDescriptor(), rendered, PIXELS, "attempt-render"),
                    "refusal-scene" to writer.writeGeneratedV2(refusalDescriptor(), refused.copy(environment = environment()), attemptId = "attempt-refuse"),
                ),
            )
        }

        assertEquals(true, failure.message.orEmpty().contains("environment"))
    }

    @Test fun `catalog writer rejects bundle paths outside the generated root`() {
        val repository = Files.createTempDirectory("gpu-evidence")
        val outsider = Files.createTempDirectory("gpu-evidence-outside")
        val writer = EvidenceBundleWriter(repository, COMMIT, FIXED_CLOCK)
        val observation = renderedObservation()
        val generatedRoot = repository.resolve("reports/gpu-renderer/evidence/correctness/generated/$COMMIT")
        val outsideBundle = writer.writeGeneratedV2(renderDescriptor(), observation, PIXELS, "attempt-render")
        val copiedOutside = outsider.resolve("render-scene")
        Files.createDirectories(copiedOutside)
        Files.copy(outsideBundle.resolve("manifest.json"), copiedOutside.resolve("manifest.json"))

        assertFailsWith<IllegalArgumentException> {
            EvidenceCatalogWriter(repository).writeGeneratedCatalog(
                root = generatedRoot,
                selection = EvidenceSelection.Explicit(listOf("render-scene")),
                observations = mapOf("render-scene" to observation),
                bundlePaths = mapOf("render-scene" to copiedOutside),
            )
        }
    }

    private fun renderDescriptor() = EvidenceSceneDescriptor(EvidenceSceneId("render-scene"), "Render", "Purpose", 1, 1, 1, emptySet(), EvidenceExpectation.ShouldRender, OraclePolicy.GeneratedCpu("oracle", 1), ComparisonPolicy(1, 100.0, 1, "test"), emptySet())
    private fun refusalDescriptor() = EvidenceSceneDescriptor(EvidenceSceneId("refusal-scene"), "Refusal", "Purpose", 1, 1, 1, emptySet(), EvidenceExpectation.ShouldRefuse("unsupported.example"), OraclePolicy.StableRefusal, null, emptySet())
    private fun renderedObservation() = SceneObservation.Rendered(PIXELS, route("rendered", 1L), emptyList(), environment(), ImageComparison(true, 100.0, 0, 0, 0.0, ByteArray(4), 1))
    private fun refusedObservation() = SceneObservation.Refused("unsupported.example", "unsupported", 0, route("refused", 0L), listOf("no-submit"), environment())
    private fun route(outcome: String, submissions: Long) = RouteEvidence("route", "attempt", if (submissions > 0L) "Completed" else null, outcome, emptyList(), emptyList(), if (submissions > 0L) mapOf("queue.submit" to submissions, "render.draw" to 1L, "render.pipelineBind" to 1L) else emptyMap(), GPUBackendRuntimeTelemetry(submissions = submissions))
    private fun environment(osVersion: String = "1") = EvidenceEnvironment(COMMIT, "test", osVersion, "x86_64", "17", EvidenceAdapter("test-adapter", "test-vendor", "test-device", "test-architecture", "test-description", false), 1L, "native", true)
    private fun json(path: Path, name: String) = EvidenceJson.parseToJsonElement(Files.readString(path.resolve(name))).jsonObject
    private fun kotlinx.serialization.json.JsonObject.string(key: String) = this[key]!!.jsonPrimitive.content
    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    companion object {
        private const val COMMIT = "abc123"
        private val FIXED_CLOCK = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)
        private val PIXELS = byteArrayOf(1, 2, 3, 4)
    }
}
