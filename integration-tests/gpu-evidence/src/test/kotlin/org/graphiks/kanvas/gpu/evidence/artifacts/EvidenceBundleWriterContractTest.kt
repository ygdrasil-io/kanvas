package org.graphiks.kanvas.gpu.evidence.artifacts

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.evidence.catalog.*
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeTelemetry
import org.graphiks.kanvas.test.ComparisonUtils

class EvidenceBundleWriterContractTest {
    @Test fun `first generation failure keeps typed diagnostics under failed`() {
        val root = Files.createTempDirectory("gpu-evidence")
        val writer = EvidenceBundleWriter(root, COMMIT, FIXED_CLOCK)

        kotlin.test.assertFailsWith<IllegalArgumentException> {
            writer.writeGenerated(renderDescriptor(), rendered(), ByteArray(3), "attempt")
        }

        val failed = root.resolve("reports/gpu-renderer/evidence/correctness/generated/$COMMIT/_failed/render-scene-attempt")
        assertTrue(Files.isRegularFile(failed.resolve("diagnostics.json")))
        assertTrue(Files.isRegularFile(failed.resolve("environment.json")))
        assertFalse(Files.isSymbolicLink(failed))
    }

    @Test fun `pre-existing failed final symlink cannot escape repository`() {
        val root = Files.createTempDirectory("gpu-evidence-root")
        val outside = Files.createTempDirectory("gpu-evidence-outside")
        val failedParent = root.resolve("reports/gpu-renderer/evidence/correctness/generated/abc123/_failed")
        Files.createDirectories(failedParent)
        Files.createSymbolicLink(failedParent.resolve("render-scene-attempt"), outside)
        val writer = EvidenceBundleWriter(root, COMMIT, FIXED_CLOCK)
        val failure = kotlin.test.assertFailsWith<IllegalArgumentException> {
            writer.writeGenerated(renderDescriptor(), rendered(), ByteArray(3), "attempt")
        }
        assertTrue(failure.message.orEmpty().contains("CPU RGBA byte count does not match descriptor"))
        assertTrue(failure.suppressed.any { it.message.orEmpty().contains("failure attempt cannot be a symlink") })
        assertFalse(Files.exists(outside.resolve("diagnostics.json")))
        assertTrue(Files.isSymbolicLink(failedParent.resolve("render-scene-attempt")))
    }

    @Test fun `staging cleanup failure is suppressed by the generation failure`() {
        val root = Files.createTempDirectory("gpu-evidence")
        val writer = EvidenceBundleWriter(
            root,
            COMMIT,
            FIXED_CLOCK,
            cleanupStrategy = { throw IOException("injected cleanup failure") },
        )

        val failure = kotlin.test.assertFailsWith<IllegalArgumentException> {
            writer.writeGenerated(renderDescriptor(), rendered(), ByteArray(3), "attempt")
        }

        assertTrue(failure.message.orEmpty().contains("CPU RGBA byte count does not match descriptor"))
        assertTrue(failure.suppressed.any { it.message == "injected cleanup failure" })
    }

    @Test fun `one render submission is preserved and verifies`() {
        val root = Files.createTempDirectory("gpu-evidence")
        val observation = SceneObservation.Rendered(PIXEL, route("rendered", 1), emptyList(), environment(), ImageComparison(true, 100.0, 0, 0, 0.0, ByteArray(4), 1))
        val path = EvidenceBundleWriter(root, COMMIT, FIXED_CLOCK).writeGenerated(renderDescriptor(), observation, PIXEL, "attempt")
        assertIs<EvidenceBundleVerification.Verified>(verifyFixtureIntegrity(path, COMMIT))
    }

    @Test fun `v2 scene writer omits environment and promotion metadata`() {
        val root = Files.createTempDirectory("gpu-evidence")
        val observation = SceneObservation.Rendered(PIXEL, route("rendered", 1), emptyList(), environment(), ImageComparison(true, 100.0, 0, 0, 0.0, ByteArray(4), 1))

        val path = EvidenceBundleWriter(root, COMMIT, FIXED_CLOCK).writeGeneratedV2(renderDescriptor(), observation, PIXEL, "attempt")

        assertEquals(
            setOf("manifest.json", "gpu.png", "cpu.png", "diff.png", "stats.json", "route.json", "diagnostics.json", "verdict.json"),
            Files.list(path).use { stream -> stream.iterator().asSequence().map { p -> p.fileName.toString() }.toSet() },
        )
        assertFalse(Files.exists(path.resolve("environment.json")))
        assertFalse(Files.exists(path.resolve("promotion.json")))
        assertIs<EvidenceBundleVerification.Verified>(
            EvidenceBundleVerifier.verifyV2(
                path,
                EvidenceVerificationExpectation(COMMIT, renderDescriptor(), PIXEL, null, "route"),
                EvidenceEnvironmentV2(
                    schemaVersion = GPU_EVIDENCE_CATALOG_SCHEMA_V2,
                    osName = "test",
                    osVersion = "1",
                    osArchitecture = "x86_64",
                    javaVersion = "17",
                    deviceGeneration = 1L,
                    capabilityImplementation = "native",
                    available = true,
                    adapter = EvidenceAdapter("test-adapter", "test-vendor", "test-device", "test-architecture", "test-description", false),
                ),
                COMMIT,
            ),
        )
    }

    @Test fun `descriptor writer requires an external render oracle`() {
        val root = Files.createTempDirectory("gpu-evidence")
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            EvidenceBundleWriter(root, COMMIT, FIXED_CLOCK).writeGenerated(renderDescriptor(), rendered(), attemptId = "attempt")
        }
    }

    @Test fun `checked in writer preserves original differently encoded png bytes`() {
        val originalFile = Files.createTempFile("checked-in-oracle", ".png").toFile()
        ComparisonUtils.saveRgbaAsPng(byteArrayOf(1, 2, 3, 4), 1, 1, originalFile)
        val original = originalFile.readBytes().also { originalFile.delete() }
        val descriptor = renderDescriptor(OraclePolicy.CheckedInPng("oracle.png", sha256(original), "checked-in-release"))
        val root = Files.createTempDirectory("gpu-evidence")
        val path = EvidenceBundleWriter(root, COMMIT, FIXED_CLOCK).writeGenerated(descriptor, rendered(), PIXEL, "attempt", checkedInPngBytes = original)
        assertContentEquals(original, Files.readAllBytes(path.resolve("skia.png")))
        assertIs<EvidenceBundleVerification.Verified>(verifyFixtureIntegrity(path, COMMIT))
    }

    @Test fun `escaped duplicate and quoted optional primitives are invalid`() {
        val path = EvidenceBundleWriter(Files.createTempDirectory("gpu-evidence"), COMMIT, FIXED_CLOCK).writeGenerated(renderDescriptor(), rendered(), PIXEL, "attempt")
        replace(path.resolve("route.json"), "\"routeId\":\"route\"", "\"routeId\":\"route\",\"\\u0072outeId\":\"route\"")
        refreshHash(path, "route.json")
        assertIs<EvidenceBundleVerification.Invalid>(verifyFixtureIntegrity(path, COMMIT))
        val second = EvidenceBundleWriter(Files.createTempDirectory("gpu-evidence"), COMMIT, FIXED_CLOCK).writeGenerated(renderDescriptor(), rendered(), PIXEL, "attempt")
        replace(second.resolve("environment.json"), "\"available\":true", "\"available\":\"true\"")
        refreshHash(second, "environment.json")
        assertIs<EvidenceBundleVerification.Invalid>(verifyFixtureIntegrity(second, COMMIT))
        val third = EvidenceBundleWriter(Files.createTempDirectory("gpu-evidence"), COMMIT, FIXED_CLOCK).writeGenerated(renderDescriptor(), rendered(), PIXEL, "attempt")
        replace(third.resolve("route.json"), "\"submissions\":1", "\"submissions\":\"1\"")
        refreshHash(third, "route.json")
        assertIs<EvidenceBundleVerification.Invalid>(verifyFixtureIntegrity(third, COMMIT))
        val adapterObservation = SceneObservation.Rendered(PIXEL, route("rendered", 1), emptyList(), environment().copy(adapter = EvidenceAdapter("s", "v", "d", "a", "description", false)), ImageComparison(true, 100.0, 0, 0, 0.0, ByteArray(4), 1))
        val fourth = EvidenceBundleWriter(Files.createTempDirectory("gpu-evidence"), COMMIT, FIXED_CLOCK).writeGenerated(renderDescriptor(), adapterObservation, PIXEL, "attempt")
        replace(fourth.resolve("environment.json"), "\"isFallbackAdapter\":false", "\"isFallbackAdapter\":\"false\"")
        refreshHash(fourth, "environment.json")
        assertIs<EvidenceBundleVerification.Invalid>(verifyFixtureIntegrity(fourth, COMMIT))
    }

    @Test fun `replacement falls back when atomic moves are unavailable`() {
        var atomicAttempts = 0
        val fallback: (Path, Path, Boolean) -> Unit = { source, destination, atomic ->
            if (atomic) { atomicAttempts++; throw java.nio.file.AtomicMoveNotSupportedException(source.toString(), destination.toString(), "test") }
            Files.move(source, destination)
        }
        val root = Files.createTempDirectory("gpu-evidence")
        val writer = EvidenceBundleWriter(root, COMMIT, FIXED_CLOCK, fallback)
        val path = writer.writeGenerated(renderDescriptor(), rendered(), PIXEL, "attempt")
        assertTrue(atomicAttempts > 0)
        assertIs<EvidenceBundleVerification.Verified>(verifyFixtureIntegrity(path, COMMIT))
    }

    @Test fun `successful replacement installs the new generated bundle`() {
        val root = Files.createTempDirectory("gpu-evidence")
        val writer = EvidenceBundleWriter(root, COMMIT, FIXED_CLOCK)
        val original = writer.writeGenerated(renderDescriptor(), rendered(), PIXEL, "attempt")

        val replacementPixels = byteArrayOf(9, 8, 7, 6)
        val replacement = writer.writeGenerated(renderDescriptor(), rendered(replacementPixels), replacementPixels, "attempt-2")

        assertTrue(original == replacement)
        assertTrue(Files.readString(replacement.resolve("route.json")).contains("\"attemptId\":\"attempt-2\""))
        assertIs<EvidenceBundleVerification.Verified>(verifyFixtureIntegrity(replacement, COMMIT))
    }

    private fun renderDescriptor(oracle: OraclePolicy = OraclePolicy.GeneratedCpu("oracle", 1)) = EvidenceSceneDescriptor(EvidenceSceneId("render-scene"), "Render", "Purpose", 1, 1, 1, emptySet(), EvidenceExpectation.ShouldRender, oracle, ComparisonPolicy(1, 100.0, 1, "test"), emptySet())
    private fun rendered(pixels: ByteArray = PIXEL) = SceneObservation.Rendered(pixels, route("rendered", 1), emptyList(), environment(), ImageComparison(true, 100.0, 0, 0, 0.0, ByteArray(4), 1))
    private fun route(outcome: String, submissions: Long) = RouteEvidence("route", "attempt", if (submissions > 0L) "Completed" else null, outcome, emptyList(), emptyList(), if (submissions > 0L) mapOf("queue.submit" to submissions, "render.draw" to 1L, "render.pipelineBind" to 1L) else emptyMap(), GPUBackendRuntimeTelemetry(submissions = submissions))
    private fun environment() = EvidenceEnvironment(COMMIT, "test", "1", "x86_64", "17", EvidenceAdapter("test-adapter", "test-vendor", "test-device", "test-architecture", "test-description", false), 1L, "native", true)
    private fun replace(path: Path, from: String, to: String) { Files.writeString(path, Files.readString(path).replace(from, to)) }
    private fun refreshHash(path: Path, name: String) { val manifest = path.resolve("manifest.json"); val hash = sha256(Files.readAllBytes(path.resolve(name))); val text = Files.readString(manifest); val key = "\"$name\":\""; val start = text.indexOf(key) + key.length; val end = text.indexOf('"', start); Files.writeString(manifest, text.substring(0, start) + hash + text.substring(end)) }
    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    companion object { private const val COMMIT = "abc123"; private val PIXEL = byteArrayOf(1, 2, 3, 4); private val FIXED_CLOCK = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC) }
}
