package org.graphiks.kanvas.gpu.evidence.artifacts

import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import org.graphiks.kanvas.gpu.evidence.catalog.*
import org.graphiks.kanvas.gpu.evidence.gate.EvidenceVerdict
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeTelemetry

class EvidenceBundleRoundTripTest {
    @Test fun `writer has no native operating system dependency`() {
        val previousOsName = System.getProperty("os.name")
        try {
            System.setProperty("os.name", "Kanvas test filesystem")
            val root = Files.createTempDirectory("gpu-evidence")
            val writer = EvidenceBundleWriter(root, "abc123", Clock.fixed(Instant.EPOCH, ZoneOffset.UTC))
            val descriptor = refusalDescriptor()
            val observation = SceneObservation.Refused(
                "unsupported.example",
                "unsupported",
                0,
                route("refused"),
                emptyList(),
                environment(),
            )

            val path = writer.writeGenerated(descriptor, observation, attemptId = "attempt-portable")

            assertIs<EvidenceBundleVerification.Verified>(verifyFixtureIntegrity(path, "abc123"))
        } finally {
            if (previousOsName == null) System.clearProperty("os.name") else System.setProperty("os.name", previousOsName)
        }
    }

    @Test fun `render bundle has complete deterministic file set and verifies`() {
        val root = Files.createTempDirectory("gpu-evidence")
        val writer = EvidenceBundleWriter(root, "abc123", Clock.fixed(Instant.parse("2026-01-02T03:04:05Z"), ZoneOffset.UTC))
        val descriptor = renderDescriptor()
        val observation = SceneObservation.Rendered(
            byteArrayOf(1, 2, 3, 4), route(), emptyList(), environment(),
            ImageComparison(true, 100.0, 0, 0, 0.0, ByteArray(4), 1),
        )
        val path = writer.writeGenerated(descriptor, observation, byteArrayOf(1, 2, 3, 4), "attempt-1")
        assertEquals(setOf("manifest.json", "gpu.png", "cpu.png", "diff.png", "stats.json", "route.json", "diagnostics.json", "environment.json", "verdict.json"), Files.list(path).use { stream -> stream.iterator().asSequence().map { p -> p.fileName.toString() }.toSet() })
        val result = verifyFixtureIntegrity(path, "abc123")
        val verified = assertIs<EvidenceBundleVerification.Verified>(result)
        assertEquals("render-scene", verified.sceneId)
        assertIs<EvidenceVerdict.Pass>(verified.verdict)
    }

    @Test fun `refusal bundle omits images and verifies exact reason and zero submissions`() {
        val root = Files.createTempDirectory("gpu-evidence")
        val writer = EvidenceBundleWriter(root, "abc123", Clock.fixed(Instant.EPOCH, ZoneOffset.UTC))
        val descriptor = refusalDescriptor()
        val observation = SceneObservation.Refused("unsupported.example", "unsupported", 0, route("refused"), listOf("no-submit"), environment())
        val path = writer.writeGenerated(descriptor, observation, attemptId = "attempt-2")
        assertEquals(setOf("manifest.json", "stats.json", "route.json", "diagnostics.json", "environment.json", "verdict.json"), Files.list(path).use { stream -> stream.iterator().asSequence().map { p -> p.fileName.toString() }.toSet() })
        val verified = assertIs<EvidenceBundleVerification.Verified>(verifyFixtureIntegrity(path, "abc123"))
        assertIs<EvidenceVerdict.Pass>(verified.verdict)
    }

    @Test fun `render v2 bundle keeps only scene evidence files`() {
        val root = Files.createTempDirectory("gpu-evidence")
        val writer = EvidenceBundleWriter(root, "abc123", Clock.fixed(Instant.parse("2026-01-02T03:04:05Z"), ZoneOffset.UTC))
        val descriptor = renderDescriptor()
        val observation = SceneObservation.Rendered(
            byteArrayOf(1, 2, 3, 4), route(), emptyList(), environment(),
            ImageComparison(true, 100.0, 0, 0, 0.0, ByteArray(4), 1),
        )

        val path = writer.writeGeneratedV2(descriptor, observation, byteArrayOf(1, 2, 3, 4), "attempt-1")

        assertEquals(setOf("manifest.json", "gpu.png", "cpu.png", "diff.png", "stats.json", "route.json", "diagnostics.json", "verdict.json"), Files.list(path).use { stream -> stream.iterator().asSequence().map { p -> p.fileName.toString() }.toSet() })
        assertFalse(Files.exists(path.resolve("environment.json")))
        assertFalse(Files.exists(path.resolve("promotion.json")))
        val manifest = json(path, "manifest.json")
        assertEquals(GPU_EVIDENCE_SCENE_SCHEMA_V2, manifest.string("schemaVersion"))
        assertFalse("sourceCommit" in manifest)
        assertFalse("generatedAtUtc" in manifest)
    }

    @Test fun `refusal v2 bundle omits images and root metadata`() {
        val root = Files.createTempDirectory("gpu-evidence")
        val writer = EvidenceBundleWriter(root, "abc123", Clock.fixed(Instant.EPOCH, ZoneOffset.UTC))
        val descriptor = refusalDescriptor()
        val observation = SceneObservation.Refused("unsupported.example", "unsupported", 0, route("refused"), listOf("no-submit"), environment())

        val path = writer.writeGeneratedV2(descriptor, observation, attemptId = "attempt-2")

        assertEquals(setOf("manifest.json", "stats.json", "route.json", "diagnostics.json", "verdict.json"), Files.list(path).use { stream -> stream.iterator().asSequence().map { p -> p.fileName.toString() }.toSet() })
        assertFalse(Files.exists(path.resolve("environment.json")))
        assertFalse(Files.exists(path.resolve("promotion.json")))
        val manifest = json(path, "manifest.json")
        assertEquals(GPU_EVIDENCE_SCENE_SCHEMA_V2, manifest.string("schemaVersion"))
        assertFalse("sourceCommit" in manifest)
        assertFalse("generatedAtUtc" in manifest)
    }

    private fun renderDescriptor() = EvidenceSceneDescriptor(EvidenceSceneId("render-scene"), "Render", "Purpose", 1, 1, 1, emptySet(), EvidenceExpectation.ShouldRender, OraclePolicy.GeneratedCpu("oracle", 1), ComparisonPolicy(1, 100.0, 1, "test"), emptySet())
    private fun refusalDescriptor() = EvidenceSceneDescriptor(EvidenceSceneId("refusal-scene"), "Refusal", "Purpose", 1, 1, 1, emptySet(), EvidenceExpectation.ShouldRefuse("unsupported.example"), OraclePolicy.StableRefusal, null, emptySet())
    private fun environment() = EvidenceEnvironment("abc123", "test", "1", "x86_64", "17", EvidenceAdapter("test-adapter", "test-vendor", "test-device", "test-architecture", "test-description", false), 1L, "native", true)
    private fun route(outcome: String = "rendered") = RouteEvidence("route", "attempt", if (outcome == "rendered") "Completed" else null, outcome, emptyList(), emptyList(), if (outcome == "rendered") mapOf("queue.submit" to 1L, "render.draw" to 1L, "render.pipelineBind" to 1L) else emptyMap(), GPUBackendRuntimeTelemetry(submissions = if (outcome == "rendered") 1L else 0L))
    private fun json(path: java.nio.file.Path, name: String) = EvidenceJson.parseToJsonElement(Files.readString(path.resolve(name))).jsonObject
    private fun kotlinx.serialization.json.JsonObject.string(key: String) = this[key]!!.jsonPrimitive.content
}
