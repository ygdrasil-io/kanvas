package org.graphiks.kanvas.gpu.evidence.artifacts

import java.nio.file.DirectoryStream
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.evidence.catalog.ComparisonPolicy
import org.graphiks.kanvas.gpu.evidence.catalog.EvidenceEnvironment
import org.graphiks.kanvas.gpu.evidence.catalog.EvidenceExpectation
import org.graphiks.kanvas.gpu.evidence.catalog.EvidenceSceneDescriptor
import org.graphiks.kanvas.gpu.evidence.catalog.EvidenceSceneId
import org.graphiks.kanvas.gpu.evidence.catalog.ImageComparison
import org.graphiks.kanvas.gpu.evidence.catalog.OraclePolicy
import org.graphiks.kanvas.gpu.evidence.catalog.RouteEvidence
import org.graphiks.kanvas.gpu.evidence.catalog.SceneObservation
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeTelemetry

class EvidenceBundleRound5RegressionTest {
    @Test fun `secure filesystem capability failures are typed before generation and during retention`() {
        val root = Files.createTempDirectory("gpu-evidence")
        val unavailable = object : SecureEvidenceFilesystem {
            override fun verifyAvailable(root: Path) {
                throw SecureEvidenceFilesystemUnavailableException("native access is unavailable")
            }

            override fun openRoot(root: Path): SecureEvidenceDirectory = error("generation must not begin")
        }

        val constructionFailure = assertFailsWith<SecureEvidenceFilesystemUnavailableException> {
            EvidenceBundleWriter(root, COMMIT, FIXED_CLOCK, secureFilesystem = unavailable)
        }
        assertContains(constructionFailure.message.orEmpty(), "native access is unavailable")
        assertFalse(Files.exists(root.resolve("reports/gpu-renderer/evidence/correctness/generated")))

        val becomesUnavailableDuringRetention = object : SecureEvidenceFilesystem {
            override fun verifyAvailable(root: Path) = Unit

            override fun openRoot(root: Path): SecureEvidenceDirectory {
                throw SecureEvidenceFilesystemUnavailableException("native handle capability was lost")
            }
        }
        val writer = EvidenceBundleWriter(root, COMMIT, FIXED_CLOCK, secureFilesystem = becomesUnavailableDuringRetention)

        val retentionFailure = assertFailsWith<SecureEvidenceFilesystemUnavailableException> {
            writer.writeGenerated(renderDescriptor(), rendered(), expectedRgba = ByteArray(3), attemptId = "attempt")
        }
        assertContains(retentionFailure.message.orEmpty(), "native handle capability was lost")
        assertTrue(
            retentionFailure.suppressed.any { it is IllegalArgumentException && it.message?.contains("CPU RGBA byte count") == true },
            "the triggering write failure must remain available to callers",
        )
    }

    @Test fun `nonsecure directory stream is closed before secure filesystem refusal`() {
        val stream = CloseTrackingDirectoryStream()

        val failure = assertFailsWith<SecureEvidenceFilesystemUnavailableException> {
            UnixSecureEvidenceFilesystem(directoryStreamFactory = { stream }).verifyAvailable(Files.createTempDirectory("gpu-evidence"))
        }

        assertContains(failure.message.orEmpty(), "secure directory handles")
        assertEquals(1, stream.closeCalls)
    }

    private fun renderDescriptor() = EvidenceSceneDescriptor(
        EvidenceSceneId("render-scene"), "Render", "Purpose", 1, 1, 1, emptySet(),
        EvidenceExpectation.ShouldRender, OraclePolicy.GeneratedCpu("oracle", 1),
        ComparisonPolicy(1, 100.0, 1, "test"), emptySet(),
    )

    private fun rendered() = SceneObservation.Rendered(
        byteArrayOf(1, 2, 3, 4),
        RouteEvidence("route", "attempt", "complete", "rendered", emptyList(), emptyList(), emptyMap(), GPUBackendRuntimeTelemetry.Empty),
        emptyList(),
        EvidenceEnvironment(COMMIT, "test", "1", "x86_64", "25", null, null, null, true),
        ImageComparison(true, 100.0, 0, 0, 0.0, ByteArray(4), 1),
    )

    private class CloseTrackingDirectoryStream : DirectoryStream<Path> {
        var closeCalls = 0

        override fun iterator(): MutableIterator<Path> = mutableListOf<Path>().iterator()

        override fun close() {
            closeCalls++
        }
    }

    companion object {
        private const val COMMIT = "abc123"
        private val FIXED_CLOCK = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)
    }
}
