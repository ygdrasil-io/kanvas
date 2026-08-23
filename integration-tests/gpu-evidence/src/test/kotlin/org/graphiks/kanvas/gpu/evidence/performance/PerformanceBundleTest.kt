package org.graphiks.kanvas.gpu.evidence.performance

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.test.assertFails
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.assertContains
import org.junit.jupiter.api.io.TempDir

class PerformanceBundleTest {
    @TempDir lateinit var root: Path

    @Test fun `bundle round trips and verifier recomputes hashes`() {
        val run = PerformanceRun.fixture()
        val path = PerformanceBundleWriter(root, run.sourceCommit).writeGenerated(run)
        assertEquals(
            PerformanceBundleVerification.Verified,
            PerformanceBundleVerifier.verify(path, run.sourceCommit),
        )
        val timings = path.resolve("timings.json")
        Files.writeString(timings, Files.readString(timings).replace("100", "101"))
        assertIs<PerformanceBundleVerification.Invalid>(PerformanceBundleVerifier.verify(path, run.sourceCommit))
    }

    @Test fun `bundle path is canonical generated source commit and scene`() {
        val run = PerformanceRun.fixture()
        val path = PerformanceBundleWriter(root, run.sourceCommit).writeGenerated(run)
        assertTrue(path.endsWith("reports/gpu-renderer/evidence/performance/generated/${run.sourceCommit}/${run.sceneId}"))
        assertEquals(setOf("manifest.json", "environment.json", "eligibility.json", "timings.json", "telemetry.json", "diagnostics.json", "verdict.json"), Files.list(path).use { stream -> stream.iterator().asSequence().map { file -> file.fileName.toString() }.toSet() })
    }

    @Test fun `eligible capture may finish with a failed measurement verdict`() {
        val run = PerformanceRun.fixture().copy(verdict = PerformanceVerdict.Failed("cold validation failed"))
        val path = PerformanceBundleWriter(root, run.sourceCommit).writeGenerated(run)
        assertIs<PerformanceBundleVerification.Verified>(PerformanceBundleVerifier.verify(path, run.sourceCommit))
    }

    @Test fun `verifier rejects a required child symlink`() {
        val run = PerformanceRun.fixture()
        val path = PerformanceBundleWriter(root, run.sourceCommit).writeGenerated(run)
        val target = root.resolve("outside-timings.json")
        Files.copy(path.resolve("timings.json"), target)
        Files.delete(path.resolve("timings.json"))
        Files.createSymbolicLink(path.resolve("timings.json"), target)
        assertIs<PerformanceBundleVerification.Invalid>(PerformanceBundleVerifier.verify(path, run.sourceCommit))
    }

    @Test fun `verifier recomputes nearest rank statistics after hashes are refreshed`() {
        val run = PerformanceRun.fixture()
        val path = PerformanceBundleWriter(root, run.sourceCommit).writeGenerated(run)
        val timings = path.resolve("timings.json")
        Files.writeString(timings, Files.readString(timings).replace("\"p50Nanos\":144", "\"p50Nanos\":1"))
        refreshHash(path, "timings.json")
        assertIs<PerformanceBundleVerification.Invalid>(PerformanceBundleVerifier.verify(path, run.sourceCommit))
    }

    @Test fun `verifier rejects configuration tampering even after hashes are refreshed`() {
        val run = PerformanceRun.fixture()
        val path = PerformanceBundleWriter(root, run.sourceCommit).writeGenerated(run)
        val timings = path.resolve("timings.json")
        Files.writeString(timings, Files.readString(timings).replace("\"warmupFrames\":10", "\"warmupFrames\":9"))
        refreshHash(path, "timings.json")
        assertIs<PerformanceBundleVerification.Invalid>(PerformanceBundleVerifier.verify(path, run.sourceCommit))
    }

    @Test fun `verifier rejects duplicate keys even when the tampered hash is refreshed`() {
        val run = PerformanceRun.fixture()
        val path = PerformanceBundleWriter(root, run.sourceCommit).writeGenerated(run)
        val telemetry = path.resolve("telemetry.json")
        val raw = Files.readString(telemetry)
        Files.writeString(telemetry, raw.dropLast(1) + ",\"cold\":{}")
        refreshHash(path, "telemetry.json")
        assertIs<PerformanceBundleVerification.Invalid>(PerformanceBundleVerifier.verify(path, run.sourceCommit))
    }

    @Test fun `verifier rejects missing malformed and extra entries without throwing`() {
        val run = PerformanceRun.fixture()
        val missing = PerformanceBundleWriter(root, run.sourceCommit).writeGenerated(run)
        Files.delete(missing.resolve("timings.json"))
        assertIs<PerformanceBundleVerification.Invalid>(PerformanceBundleVerifier.verify(missing, run.sourceCommit))

        val malformed = PerformanceBundleWriter(root, run.sourceCommit).writeGenerated(run)
        Files.writeString(malformed.resolve("telemetry.json"), "{")
        assertIs<PerformanceBundleVerification.Invalid>(PerformanceBundleVerifier.verify(malformed, run.sourceCommit))

        val extra = PerformanceBundleWriter(root, run.sourceCommit).writeGenerated(run)
        Files.writeString(extra.resolve("extra.json"), "{}")
        assertIs<PerformanceBundleVerification.Invalid>(PerformanceBundleVerifier.verify(extra, run.sourceCommit))
    }

    @Test fun `failed replacement restores the previous bundle byte for byte`() {
        val run = PerformanceRun.fixture()
        val writer = PerformanceBundleWriter(root, run.sourceCommit)
        val path = writer.writeGenerated(run)
        val before = Files.list(path).use { it.iterator().asSequence().associate { file -> file.fileName.toString() to Files.readAllBytes(file).toList() } }
        val failingWriter = PerformanceBundleWriter(root, run.sourceCommit, moveStrategy = { from, to ->
            if (from.parent.fileName.toString().startsWith(".${run.sceneId}.tmp-")) error("injected late move failure")
            Files.move(from, to)
        })
        assertFails { failingWriter.writeGenerated(run) }
        val after = Files.list(path).use { it.iterator().asSequence().associate { file -> file.fileName.toString() to Files.readAllBytes(file).toList() } }
        assertEquals(before, after)
    }

    @Test fun `missing telemetry counters are explicit unavailable metrics`() {
        val run = PerformanceRun.fixture().copy(
            eligibility = PerformanceVerdict.Unavailable("telemetry unavailable"),
            verdict = PerformanceVerdict.Unavailable("telemetry unavailable"),
            coldReadbackNanos = null,
            timings = null,
            timingSamplesNanos = emptyList(),
            telemetry = PerformanceTelemetry.Empty,
        )
        val path = PerformanceBundleWriter(root, run.sourceCommit).writeGenerated(run)
        assertContains(Files.readString(path.resolve("telemetry.json")), "\"source\":\"Unavailable\"")
        assertIs<PerformanceBundleVerification.Verified>(PerformanceBundleVerifier.verify(path, run.sourceCommit))
    }

    @Test fun `diagnostic bundle accepts empty timings with unavailable telemetry`() {
        val run = PerformanceRun.fixture().copy(
            eligibility = PerformanceVerdict.DiagnosticOnly("fallback adapter"),
            verdict = PerformanceVerdict.DiagnosticOnly("fallback adapter"),
            coldReadbackNanos = null,
            timings = null,
            timingSamplesNanos = emptyList(),
        )
        val path = PerformanceBundleWriter(root, run.sourceCommit).writeGenerated(run)
        assertIs<PerformanceBundleVerification.Verified>(PerformanceBundleVerifier.verify(path, run.sourceCommit))
    }

    private fun refreshHash(path: Path, name: String) {
        val digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path.resolve(name))).joinToString("") { "%02x".format(it) }
        val manifest = path.resolve("manifest.json")
        val raw = Files.readString(manifest)
        val old = Regex("(\\\"$name\\\":\\\")([0-9a-f]+)(\\\")").find(raw)!!.groupValues[2]
        Files.writeString(manifest, raw.replace(old, digest))
    }
}
