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

    private fun assertRenderInvalid(tamper: (Path) -> Unit) { val path = renderBundle(); tamper(path); assertIs<EvidenceBundleVerification.Invalid>(EvidenceBundleVerifier.verify(path, "abc123")) }
    private fun assertRefusalInvalid(tamper: (Path) -> Unit) { val path = refusalBundle(); tamper(path); assertIs<EvidenceBundleVerification.Invalid>(EvidenceBundleVerifier.verify(path, "abc123")) }
    private fun renderBundle(): Path {
        val root = Files.createTempDirectory("gpu-evidence")
        val descriptor = EvidenceSceneDescriptor(EvidenceSceneId("render-scene"), "Render", "Purpose", 1, 1, 1, emptySet(), EvidenceExpectation.ShouldRender, OraclePolicy.GeneratedCpu("oracle", 1), ComparisonPolicy(1, 100.0, 1, "test"), emptySet())
        val observation = SceneObservation.Rendered(byteArrayOf(1, 2, 3, 4), route(), emptyList(), environment(), ImageComparison(true, 100.0, 0, 0, 0.0, ByteArray(4), 1))
        return EvidenceBundleWriter(root, "abc123", Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)).writeGenerated(descriptor, observation, byteArrayOf(1, 2, 3, 4), "attempt")
    }
    private fun refusalBundle(): Path {
        val root = Files.createTempDirectory("gpu-evidence")
        val descriptor = EvidenceSceneDescriptor(EvidenceSceneId("refusal-scene"), "Refusal", "Purpose", 1, 1, 1, emptySet(), EvidenceExpectation.ShouldRefuse("unsupported.example"), OraclePolicy.StableRefusal, null, emptySet())
        val observation = SceneObservation.Refused("unsupported.example", "unsupported", 0, route("refused"), emptyList(), environment())
        return EvidenceBundleWriter(root, "abc123", Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)).writeGenerated(descriptor, observation, attemptId = "attempt")
    }
    private fun replace(path: Path, from: String, to: String) { Files.writeString(path, Files.readString(path).replace(from, to)) }
    private fun environment() = EvidenceEnvironment("abc123", "test", "1", "x86_64", "17", null, null, null, true)
    private fun route(outcome: String = "rendered") = RouteEvidence("route", null, null, outcome, emptyList(), emptyList(), emptyMap(), GPUBackendRuntimeTelemetry.Empty)
}
