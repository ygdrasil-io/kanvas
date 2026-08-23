package org.graphiks.kanvas.gpu.evidence.artifacts

import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
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

    private fun renderDescriptor() = EvidenceSceneDescriptor(EvidenceSceneId("render-scene"), "Render", "Purpose", 1, 1, 1, emptySet(), EvidenceExpectation.ShouldRender, OraclePolicy.GeneratedCpu("oracle", 1), ComparisonPolicy(1, 100.0, 1, "test"), emptySet())
    private fun refusalDescriptor() = EvidenceSceneDescriptor(EvidenceSceneId("refusal-scene"), "Refusal", "Purpose", 1, 1, 1, emptySet(), EvidenceExpectation.ShouldRefuse("unsupported.example"), OraclePolicy.StableRefusal, null, emptySet())
    private fun environment() = EvidenceEnvironment("abc123", "test", "1", "x86_64", "17", EvidenceAdapter("fake-adapter", null, null, null, null, null), null, null, true)
    private fun route(outcome: String = "rendered") = RouteEvidence("route", "attempt", "complete", outcome, emptyList(), emptyList(), emptyMap(), GPUBackendRuntimeTelemetry.Empty)
}
