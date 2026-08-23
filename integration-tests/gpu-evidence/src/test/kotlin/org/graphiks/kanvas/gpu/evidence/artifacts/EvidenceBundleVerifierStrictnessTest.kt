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
import org.graphiks.kanvas.test.ComparisonUtils

class EvidenceBundleVerifierStrictnessTest {
    @Test fun `verifier reproduces render expectation refusal reason`() {
        val path = bundle(renderDescriptor(), refusedObservation())
        val verified = assertIs<EvidenceBundleVerification.Verified>(strict(path, renderDescriptor()))
        assertEquals(EvidenceVerdict.Fail("scene refused: unsupported.example"), verified.verdict)
    }

    @Test fun `verifier reproduces refusal expectation rendered reason`() {
        val path = bundle(refusalDescriptor(), renderedObservation())
        assertIs<EvidenceBundleVerification.Invalid>(strict(path, refusalDescriptor()))
    }

    @Test fun `verifier rejects contradictory stats after manifest hash is refreshed`() {
        val path = bundle(renderDescriptor(), renderedObservation())
        replace(path.resolve("stats.json"), "\"similarityPercent\":100.0", "\"similarityPercent\":0.0")
        refreshHash(path, "stats.json")
        assertIs<EvidenceBundleVerification.Invalid>(strict(path, renderDescriptor()))
    }

    @Test fun `verifier rejects tampered expectation even when manifest is internally coherent`() {
        val path = bundle(renderDescriptor(), renderedObservation())
        replace(path.resolve("manifest.json"), "\"expectation\":\"render\"", "\"expectation\":\"refuse:forged\"")
        replaceAndRefresh(path, "verdict.json", "\"expectation\":\"render\"", "\"expectation\":\"refuse:forged\"")
        replaceAndRefresh(path, "verdict.json", "\"verdictKind\":\"pass\"", "\"verdictKind\":\"fail\"")
        replaceAndRefresh(path, "verdict.json", "\"reason\":\"rendered image passed comparison\"", "\"reason\":\"scene rendered instead of refusing\"")
        assertIs<EvidenceBundleVerification.Invalid>(strict(path, renderDescriptor()))
    }

    @Test fun `verifier rejects tampered oracle identity after hash refresh`() {
        val path = bundle(renderDescriptor(), renderedObservation())
        replace(path.resolve("manifest.json"), "\"oracleId\":\"oracle\"", "\"oracleId\":\"forged-oracle\"")
        assertIs<EvidenceBundleVerification.Invalid>(strict(path, renderDescriptor()))
    }

    @Test fun `verifier rejects tampered policy threshold after stats hash refresh`() {
        val path = bundle(renderDescriptor(), renderedObservation())
        replace(path.resolve("stats.json"), "\"minimumSimilarityPercent\":100.0", "\"minimumSimilarityPercent\":0.0")
        refreshHash(path, "stats.json")
        assertIs<EvidenceBundleVerification.Invalid>(strict(path, renderDescriptor()))
    }

    @Test fun `verifier rejects tampered png pixels after all hashes are refreshed`() {
        val path = bundle(renderDescriptor(), renderedObservation())
        listOf("gpu.png", "cpu.png", "diff.png").forEach { name ->
            val replacement = Files.createTempFile("valid-tampered-$name", ".png").toFile()
            try {
                ComparisonUtils.saveRgbaAsPng(byteArrayOf(9, 8, 7, 6), 1, 1, replacement)
                Files.write(path.resolve(name), replacement.readBytes())
            } finally {
                replacement.delete()
            }
            refreshHash(path, name)
            assertIs<EvidenceBundleVerification.Invalid>(strict(path, renderDescriptor()), name)
            val fresh = bundle(renderDescriptor(), renderedObservation())
            val second = Files.createTempFile("valid-tampered-$name", ".png").toFile()
            ComparisonUtils.saveRgbaAsPng(byteArrayOf(9, 8, 7, 6), 1, 1, second)
            Files.write(fresh.resolve(name), second.readBytes())
            second.delete()
            refreshHash(fresh, name)
            assertIs<EvidenceBundleVerification.Invalid>(strict(fresh, renderDescriptor()), name)
        }
    }

    @Test fun `verifier rejects route outcome and submission telemetry mismatch`() {
        val path = bundle(refusalDescriptor(), refusedObservation())
        replace(path.resolve("route.json"), "\"outcome\":\"refused\"", "\"outcome\":\"rendered\"")
        replace(path.resolve("route.json"), "\"submissions\":0", "\"submissions\":1")
        refreshHash(path, "route.json")
        assertIs<EvidenceBundleVerification.Invalid>(strict(path, refusalDescriptor()))
    }

    @Test fun `verifier rejects nonterminal route or missing render submission proof`() {
        val phase = bundle(renderDescriptor(), renderedObservation())
        replaceAndRefresh(phase, "route.json", "\"furthestPhase\":\"Completed\"", "\"furthestPhase\":\"Prepared\"")
        assertIs<EvidenceBundleVerification.Invalid>(strict(phase, renderDescriptor()))

        val submission = bundle(renderDescriptor(), renderedObservation())
        replaceAndRefresh(submission, "route.json", "\"submissions\":1", "\"submissions\":0")
        replaceAndRefresh(submission, "route.json", "\"furthestPhase\":\"Completed\"", "\"furthestPhase\":\"Prepared\"")
        assertIs<EvidenceBundleVerification.Invalid>(strict(submission, renderDescriptor()))
    }

    @Test fun `verifier rejects route id mismatch after route hash refresh`() {
        val path = bundle(renderDescriptor(), renderedObservation())
        replaceAndRefresh(path, "route.json", "\"routeId\":\"route\"", "\"routeId\":\"forged-route\"")
        assertIs<EvidenceBundleVerification.Invalid>(strict(path, renderDescriptor()))
    }

    @Test fun `verifier rejects every negative route counter after hash refresh`() {
        val path = bundle(renderDescriptor(), renderedObservation())
        replaceAndRefresh(path, "route.json", "\"submissions\":1", "\"submissions\":-1")
        assertIs<EvidenceBundleVerification.Invalid>(strict(path, renderDescriptor()))
    }

    @Test fun `verifier rejects negative runtime telemetry after hash refresh`() {
        val path = bundle(renderDescriptor(), renderedObservation())
        replaceAndRefresh(path, "route.json", "\"windowPasses\":0,\"submissions\":1", "\"windowPasses\":0,\"submissions\":-1")
        assertIs<EvidenceBundleVerification.Invalid>(strict(path, renderDescriptor()))
    }

    @Test fun `available evidence requires a nonblank adapter summary`() {
        val missing = bundle(renderDescriptor(), renderedObservation())
        replaceAdapter(missing, "null")
        refreshHash(missing, "environment.json")
        assertIs<EvidenceBundleVerification.Invalid>(strict(missing, renderDescriptor()))

        val blank = bundle(renderDescriptor(), renderedObservation())
        replace(blank.resolve("environment.json"), "\"summary\":\"fake-adapter\"", "\"summary\":\" \"")
        refreshHash(blank, "environment.json")
        assertIs<EvidenceBundleVerification.Invalid>(strict(blank, renderDescriptor()))
    }

    @Test fun `coherent unavailable evidence with null adapter is verified as unavailable`() {
        val path = bundle(refusalDescriptor(), refusedObservation())
        replaceAndRefresh(path, "route.json", "\"outcome\":\"refused\"", "\"outcome\":\"unavailable\"")
        replaceAndRefresh(path, "environment.json", "\"available\":true", "\"available\":false")
        replaceAdapter(path, "null")
        refreshHash(path, "environment.json")
        replaceAndRefresh(path, "verdict.json", "\"observedOutcome\":\"refused\"", "\"observedOutcome\":\"unavailable\"")
        replaceAndRefresh(path, "verdict.json", "\"verdictKind\":\"pass\"", "\"verdictKind\":\"unavailable\"")
        replaceAndRefresh(path, "verdict.json", "\"reason\":\"exact refusal before submission\"", "\"reason\":\"scene unavailable: unsupported.example\"")
        val manifest = path.resolve("manifest.json")
        Files.writeString(manifest, Files.readString(manifest).replace("\"observedOutcome\":\"refused\"", "\"observedOutcome\":\"unavailable\""))

        val verified = assertIs<EvidenceBundleVerification.Verified>(strict(path, refusalDescriptor()))

        assertEquals(EvidenceVerdict.Unavailable("scene unavailable: unsupported.example"), verified.verdict)
    }

    @Test fun `checked in oracle uses skia and preserves provenance`() {
        val descriptor = renderDescriptor(OraclePolicy.CheckedInPng("oracle.png", sha256(ORIGINAL), "release-reference"))
        val path = bundle(descriptor, renderedObservation(), expected = PIXEL, checkedInBytes = ORIGINAL)
        assertTrue(Files.exists(path.resolve("skia.png")))
        assertFalse(Files.exists(path.resolve("cpu.png")))
        assertIs<EvidenceBundleVerification.Verified>(strict(path, descriptor, PIXEL, ORIGINAL))
    }

    @Test fun `duplicate keys and quoted primitives are invalid even with refreshed hashes`() {
        val path = bundle(renderDescriptor(), renderedObservation())
        replace(path.resolve("route.json"), "{\"routeId\":\"route\"", "{\"routeId\":\"route\",\"routeId\":\"route\"")
        refreshHash(path, "route.json")
        assertIs<EvidenceBundleVerification.Invalid>(strict(path, renderDescriptor()))
    }

    @Test fun `quoted numeric and unknown nested telemetry fields are invalid`() {
        val path = bundle(renderDescriptor(), renderedObservation())
        replace(path.resolve("route.json"), "\"submissions\":1", "\"submissions\":\"1\"")
        refreshHash(path, "route.json")
        assertIs<EvidenceBundleVerification.Invalid>(strict(path, renderDescriptor()))
        val second = bundle(renderDescriptor(), renderedObservation())
        replace(second.resolve("route.json"), "\"submissions\":1", "\"submissions\":1,\"unknown\":0")
        refreshHash(second, "route.json")
        assertIs<EvidenceBundleVerification.Invalid>(strict(second, renderDescriptor()))
    }

    @Test fun `quoted structural counter is invalid after hash refresh`() {
        val path = bundle(renderDescriptor(), renderedObservation())
        replace(path.resolve("route.json"), "\"structuralCounters\":{\"queue.submit\":1}", "\"structuralCounters\":{\"queue.submit\":\"1\"}")
        refreshHash(path, "route.json")
        assertIs<EvidenceBundleVerification.Invalid>(strict(path, renderDescriptor()))
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
        assertIs<EvidenceBundleVerification.Verified>(strict(old, renderDescriptor()))
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
    private fun strict(path: Path, descriptor: EvidenceSceneDescriptor, expected: ByteArray? = if (descriptor.expectation is EvidenceExpectation.ShouldRender) PIXEL else null, checkedIn: ByteArray? = null) = EvidenceBundleVerifier.verify(
        path,
        EvidenceVerificationExpectation.fromDescriptor(descriptor, COMMIT, expected, checkedIn, "route", verifyPixels = true),
    )
    private fun renderDescriptor(oracle: OraclePolicy = OraclePolicy.GeneratedCpu("oracle", 1)) = EvidenceSceneDescriptor(EvidenceSceneId("render-scene"), "Render", "Purpose", 1, 1, 1, emptySet(), EvidenceExpectation.ShouldRender, oracle, ComparisonPolicy(1, 100.0, 1, "test"), emptySet())
    private fun refusalDescriptor() = EvidenceSceneDescriptor(EvidenceSceneId("refusal-scene"), "Refusal", "Purpose", 1, 1, 1, emptySet(), EvidenceExpectation.ShouldRefuse("unsupported.example"), OraclePolicy.StableRefusal, null, emptySet())
    private fun renderedObservation() = SceneObservation.Rendered(PIXEL, route("rendered", 1), emptyList(), environment(), ImageComparison(true, 100.0, 0, 0, 0.0, ByteArray(4), 1))
    private fun refusedObservation() = SceneObservation.Refused("unsupported.example", "unsupported", 0, route("refused", 0), emptyList(), environment())
    private fun route(outcome: String, submissions: Long) = RouteEvidence("route", "attempt", "Completed", outcome, emptyList(), emptyList(), if (submissions > 0L) mapOf("queue.submit" to submissions) else emptyMap(), GPUBackendRuntimeTelemetry(submissions = submissions))
    private fun environment() = EvidenceEnvironment(COMMIT, "test", "1", "x86_64", "17", EvidenceAdapter("fake-adapter", null, null, null, null, null), null, null, true)
    private fun replace(path: Path, from: String, to: String) { Files.writeString(path, Files.readString(path).replace(from, to)) }
    private fun replaceAndRefresh(path: Path, name: String, from: String, to: String) {
        replace(path.resolve(name), from, to)
        refreshHash(path, name)
    }
    private fun replaceAdapter(path: Path, replacement: String) {
        val text = Files.readString(path.resolve("environment.json"))
        val start = text.indexOf("\"adapter\":")
        val end = text.indexOf('}', start) + 1
        Files.writeString(path.resolve("environment.json"), text.substring(0, start) + "\"adapter\":" + replacement + text.substring(end))
    }
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
        private val ORIGINAL = run {
            val file = Files.createTempFile("gpu-evidence-oracle", ".png").toFile()
            ComparisonUtils.saveRgbaAsPng(byteArrayOf(1, 2, 3, 4), 1, 1, file)
            file.readBytes().also { file.delete() }
        }
        private val FIXED_CLOCK = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)
    }
}
