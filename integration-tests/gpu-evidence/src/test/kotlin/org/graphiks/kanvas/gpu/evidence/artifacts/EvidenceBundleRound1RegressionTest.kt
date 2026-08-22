package org.graphiks.kanvas.gpu.evidence.artifacts

import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.evidence.catalog.*
import org.graphiks.kanvas.gpu.evidence.gate.EvidenceVerdict
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeTelemetry

class EvidenceBundleRound1RegressionTest {
    @Test fun `verifier reproduces render expectation refusal reason`() {
        val path = bundle(renderDescriptor(), refusedObservation())
        val verified = assertIs<EvidenceBundleVerification.Verified>(EvidenceBundleVerifier.verify(path, COMMIT))
        assertEquals(EvidenceVerdict.Fail("scene refused: unsupported.example"), verified.verdict)
    }

    @Test fun `verifier reproduces refusal expectation rendered reason`() {
        val path = bundle(refusalDescriptor(), renderedObservation())
        val verified = assertIs<EvidenceBundleVerification.Verified>(EvidenceBundleVerifier.verify(path, COMMIT))
        assertEquals(EvidenceVerdict.Fail("scene rendered instead of refusing"), verified.verdict)
    }

    @Test fun `verifier rejects contradictory stats after manifest hash is refreshed`() {
        val path = bundle(renderDescriptor(), renderedObservation())
        replace(path.resolve("stats.json"), "\"similarityPercent\":100.0", "\"similarityPercent\":0.0")
        refreshHash(path, "stats.json")
        assertIs<EvidenceBundleVerification.Invalid>(EvidenceBundleVerifier.verify(path, COMMIT))
    }

    @Test fun `verifier rejects route outcome and submission telemetry mismatch`() {
        val path = bundle(refusalDescriptor(), refusedObservation())
        replace(path.resolve("route.json"), "\"outcome\":\"refused\"", "\"outcome\":\"rendered\"")
        replace(path.resolve("route.json"), "\"submissions\":0", "\"submissions\":1")
        refreshHash(path, "route.json")
        assertIs<EvidenceBundleVerification.Invalid>(EvidenceBundleVerifier.verify(path, COMMIT))
    }

    @Test fun `checked in oracle uses skia and preserves provenance`() {
        val descriptor = renderDescriptor(OraclePolicy.CheckedInPng("oracle.png", sha256(ORIGINAL), "release-reference"))
        val path = bundle(descriptor, renderedObservation(), expected = PIXEL, checkedInBytes = ORIGINAL)
        assertTrue(Files.exists(path.resolve("skia.png")))
        assertFalse(Files.exists(path.resolve("cpu.png")))
        assertIs<EvidenceBundleVerification.Verified>(EvidenceBundleVerifier.verify(path, COMMIT))
    }

    @Test fun `duplicate keys and quoted primitives are invalid even with refreshed hashes`() {
        val path = bundle(renderDescriptor(), renderedObservation())
        replace(path.resolve("route.json"), "{\"routeId\":\"route\"", "{\"routeId\":\"route\",\"routeId\":\"route\"")
        refreshHash(path, "route.json")
        assertIs<EvidenceBundleVerification.Invalid>(EvidenceBundleVerifier.verify(path, COMMIT))
    }

    @Test fun `quoted numeric and unknown nested telemetry fields are invalid`() {
        val path = bundle(renderDescriptor(), renderedObservation())
        replace(path.resolve("route.json"), "\"submissions\":0", "\"submissions\":\"0\"")
        refreshHash(path, "route.json")
        assertIs<EvidenceBundleVerification.Invalid>(EvidenceBundleVerifier.verify(path, COMMIT))
        val second = bundle(renderDescriptor(), renderedObservation())
        replace(second.resolve("route.json"), "\"submissions\":0", "\"submissions\":0,\"unknown\":0")
        refreshHash(second, "route.json")
        assertIs<EvidenceBundleVerification.Invalid>(EvidenceBundleVerifier.verify(second, COMMIT))
    }

    @Test fun `symlinked repository path cannot receive failure artifacts`() {
        val root = Files.createTempDirectory("gpu-evidence-root")
        val outside = Files.createTempDirectory("gpu-evidence-outside")
        Files.createSymbolicLink(root.resolve("reports"), outside)
        val writer = EvidenceBundleWriter(root, COMMIT, FIXED_CLOCK)
        kotlin.test.assertFailsWith<Throwable> { writer.writeGenerated(renderDescriptor(), renderedObservation(), expectedRgba = ByteArray(3), attemptId = "attempt") }
        assertFalse(Files.exists(outside.resolve("gpu-renderer")))
    }

    @Test fun `failed replacement cleans temp and preserves old bundle`() {
        val root = Files.createTempDirectory("gpu-evidence-root")
        val writer = EvidenceBundleWriter(root, COMMIT, FIXED_CLOCK)
        val old = writer.writeGenerated(renderDescriptor(), renderedObservation(), PIXEL, "attempt")
        kotlin.test.assertFailsWith<Throwable> { writer.writeGenerated(renderDescriptor(), renderedObservation(), ByteArray(3), "attempt-2") }
        assertIs<EvidenceBundleVerification.Verified>(EvidenceBundleVerifier.verify(old, COMMIT))
        assertEquals(emptySet(), Files.list(old.parent).use { stream -> stream.filter { it.fileName.toString().contains(".tmp-") }.toList().toSet() })
    }

    @Test fun `fixed clock and identical inputs produce byte identical bundle`() {
        val first = bundle(renderDescriptor(), renderedObservation())
        val second = bundle(renderDescriptor(), renderedObservation())
        val names = Files.list(first).use { stream -> stream.map { it.fileName.toString() }.toList() }
        names.forEach { name -> assertEquals(Files.readAllBytes(first.resolve(name)).toList(), Files.readAllBytes(second.resolve(name)).toList(), name) }
    }

    private fun bundle(descriptor: EvidenceSceneDescriptor, observation: SceneObservation, expected: ByteArray? = null, checkedInBytes: ByteArray? = null): Path =
        EvidenceBundleWriter(Files.createTempDirectory("gpu-evidence"), COMMIT, FIXED_CLOCK).writeGenerated(descriptor, observation, expected ?: PIXEL, "attempt", checkedInPngBytes = checkedInBytes)
    private fun renderDescriptor(oracle: OraclePolicy = OraclePolicy.GeneratedCpu("oracle", 1)) = EvidenceSceneDescriptor(EvidenceSceneId("render-scene"), "Render", "Purpose", 1, 1, 1, emptySet(), EvidenceExpectation.ShouldRender, oracle, ComparisonPolicy(1, 100.0, 1, "test"), emptySet())
    private fun refusalDescriptor() = EvidenceSceneDescriptor(EvidenceSceneId("refusal-scene"), "Refusal", "Purpose", 1, 1, 1, emptySet(), EvidenceExpectation.ShouldRefuse("unsupported.example"), OraclePolicy.StableRefusal, null, emptySet())
    private fun renderedObservation() = SceneObservation.Rendered(PIXEL, route("rendered", 0), emptyList(), environment(), ImageComparison(true, 100.0, 0, 0, 0.0, ByteArray(4), 1))
    private fun refusedObservation() = SceneObservation.Refused("unsupported.example", "unsupported", 0, route("refused", 0), emptyList(), environment())
    private fun route(outcome: String, submissions: Long) = RouteEvidence("route", "attempt", "complete", outcome, emptyList(), emptyList(), emptyMap(), GPUBackendRuntimeTelemetry(submissions = submissions))
    private fun environment() = EvidenceEnvironment(COMMIT, "test", "1", "x86_64", "17", null, null, null, true)
    private fun replace(path: Path, from: String, to: String) { Files.writeString(path, Files.readString(path).replace(from, to)) }
    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun refreshHash(path: Path, name: String) {
        val manifest = path.resolve("manifest.json")
        val hash = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path.resolve(name))).joinToString("") { "%02x".format(it) }
        val text = Files.readString(manifest)
        val key = "\"$name\":\""
        val start = text.indexOf(key) + key.length
        val end = text.indexOf('"', start)
        Files.writeString(manifest, text.substring(0, start) + hash + text.substring(end))
    }
    companion object {
        private const val COMMIT = "abc123"
        private val PIXEL = byteArrayOf(1, 2, 3, 4)
        private val ORIGINAL = byteArrayOf(1, 2, 3, 4, 5, 6)
        private val FIXED_CLOCK = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)
    }
}
