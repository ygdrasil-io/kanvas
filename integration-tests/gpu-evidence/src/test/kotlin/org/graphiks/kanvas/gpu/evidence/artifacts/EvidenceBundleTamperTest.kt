package org.graphiks.kanvas.gpu.evidence.artifacts

import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertIs
import org.graphiks.kanvas.gpu.evidence.catalog.*
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeTelemetry

class EvidenceBundleTamperTest {
    @Test fun `removing a required file is invalid`() = assertRenderInvalid { Files.delete(it.resolve("stats.json")) }
    @Test fun `changing source commit is invalid`() = assertRenderInvalid { replace(it.resolve("manifest.json"), "abc123", "def456") }
    @Test fun `changing scene id is invalid`() = assertRenderInvalid { replace(it.resolve("manifest.json"), "render-scene", "other-scene") }
    @Test fun `altering png bytes is invalid`() = assertRenderInvalid { path -> val bytes = Files.readAllBytes(path.resolve("gpu.png")); bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte(); Files.write(path.resolve("gpu.png"), bytes) }
    @Test fun `replacing schema version is invalid`() = assertRenderInvalid { replace(it.resolve("manifest.json"), GPU_EVIDENCE_SCHEMA, "gpu-evidence-v0") }
    @Test fun `changing refusal reason is invalid`() = assertRefusalInvalid { replace(it.resolve("diagnostics.json"), "unsupported.example", "changed.reason") }
    @Test fun `setting refusal submission delta to one is invalid`() = assertRefusalInvalid { replace(it.resolve("diagnostics.json"), "\"submissionDelta\":0", "\"submissionDelta\":1") }

    private fun assertRenderInvalid(tamper: (Path) -> Unit) { val descriptor = renderDescriptor(); val path = renderBundle(descriptor); tamper(path); assertIs<EvidenceBundleVerification.Invalid>(EvidenceBundleVerifier.verify(path, EvidenceVerificationExpectation("abc123", descriptor, PIXEL, null, "route"))) }
    private fun assertRefusalInvalid(tamper: (Path) -> Unit) { val descriptor = refusalDescriptor(); val path = refusalBundle(descriptor); tamper(path); assertIs<EvidenceBundleVerification.Invalid>(EvidenceBundleVerifier.verify(path, EvidenceVerificationExpectation("abc123", descriptor, null, null, "route"))) }
    private fun renderDescriptor() = EvidenceSceneDescriptor(EvidenceSceneId("render-scene"), "Render", "Purpose", 1, 1, 1, emptySet(), EvidenceExpectation.ShouldRender, OraclePolicy.GeneratedCpu("oracle", 1), ComparisonPolicy(1, 100.0, 1, "test"), emptySet())
    private fun refusalDescriptor() = EvidenceSceneDescriptor(EvidenceSceneId("refusal-scene"), "Refusal", "Purpose", 1, 1, 1, emptySet(), EvidenceExpectation.ShouldRefuse("unsupported.example"), OraclePolicy.StableRefusal, null, emptySet())
    private val PIXEL = byteArrayOf(1, 2, 3, 4)
    private fun renderBundle(descriptor: EvidenceSceneDescriptor): Path {
        val root = Files.createTempDirectory("gpu-evidence")
        val observation = SceneObservation.Rendered(PIXEL, route(), emptyList(), environment(), ImageComparison(true, 100.0, 0, 0, 0.0, ByteArray(4), 1))
        return EvidenceBundleWriter(root, "abc123", Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)).writeGenerated(descriptor, observation, PIXEL, "attempt")
    }
    private fun refusalBundle(descriptor: EvidenceSceneDescriptor): Path {
        val root = Files.createTempDirectory("gpu-evidence")
        val observation = SceneObservation.Refused("unsupported.example", "unsupported", 0, route("refused"), emptyList(), environment())
        return EvidenceBundleWriter(root, "abc123", Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)).writeGenerated(descriptor, observation, attemptId = "attempt")
    }
    private fun replace(path: Path, from: String, to: String) { Files.writeString(path, Files.readString(path).replace(from, to)) }
    private fun environment() = EvidenceEnvironment("abc123", "test", "1", "x86_64", "17", EvidenceAdapter("test-adapter", "test-vendor", "test-device", "test-architecture", "test-description", false), 1L, "native", true)
    private fun route(outcome: String = "rendered") = RouteEvidence("route", "attempt", if (outcome == "rendered") "Completed" else null, outcome, emptyList(), emptyList(), if (outcome == "rendered") mapOf("queue.submit" to 1L, "render.draw" to 1L, "render.pipelineBind" to 1L) else emptyMap(), GPUBackendRuntimeTelemetry(submissions = if (outcome == "rendered") 1L else 0L))
}
