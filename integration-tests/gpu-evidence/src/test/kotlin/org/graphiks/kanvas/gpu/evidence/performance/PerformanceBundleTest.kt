package org.graphiks.kanvas.gpu.evidence.performance

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
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
}
