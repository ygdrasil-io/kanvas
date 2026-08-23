package org.graphiks.kanvas.gpu.evidence.artifacts

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.evidence.catalog.EvidenceEnvironment
import org.graphiks.kanvas.gpu.evidence.catalog.EvidenceExpectation
import org.graphiks.kanvas.gpu.evidence.catalog.GpuEvidenceCatalog
import org.graphiks.kanvas.gpu.evidence.catalog.ImageComparison
import org.graphiks.kanvas.gpu.evidence.catalog.RouteEvidence
import org.graphiks.kanvas.gpu.evidence.catalog.SceneObservation
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeTelemetry
import org.junit.jupiter.api.io.TempDir

class VerifyEvidenceCliTest {
    @TempDir
    lateinit var repository: Path

    @Test
    fun `verifier rejects missing extra and non-directory scene entries`() {
        writeAll(COMMIT)
        deleteTree(generatedRoot().resolve("solid-card-stack"))
        assertTrue(verify(COMMIT) != 0)

        writeAll(COMMIT)
        Files.createDirectory(generatedRoot().resolve("extra-scene"))
        assertTrue(verify(COMMIT) != 0)

        writeAll(COMMIT)
        Files.delete(generatedRoot().resolve("extra-scene"))
        Files.writeString(generatedRoot().resolve("not-a-scene"), "x")
        assertTrue(verify(COMMIT) != 0)
    }

    @Test
    fun `historical mode is explicit and accepts one internally consistent source commit`() {
        writeAll(COMMIT)
        assertTrue(VerifyEvidenceCliRunner().run(arrayOf("--root", generatedRoot().toString())) != 0)
        assertEquals(0, VerifyEvidenceCliRunner().run(arrayOf("--root", generatedRoot().toString(), "--allow-historical-commit")))
    }

    @Test
    fun `verifier rejects inconsistent source commits and non-pass verdicts`() {
        writeAll(COMMIT)
        val descriptor = GpuEvidenceCatalog.cases.first { it.descriptor.id.value == "solid-card-stack" }.descriptor
        val env = EvidenceEnvironment(OTHER_COMMIT, "test", "1", "test", "17", null, null, null, true)
        val route = RouteEvidence("route", "attempt", "complete", "rendered", emptyList(), emptyList(), emptyMap(), GPUBackendRuntimeTelemetry.Empty)
        val pixels = ByteArray(descriptor.width * descriptor.height * 4)
        val otherRoot = Files.createTempDirectory("other-evidence")
        EvidenceBundleWriter(otherRoot, OTHER_COMMIT).writeGenerated(descriptor, SceneObservation.Rendered(pixels, route, emptyList(), env, ImageComparison(true, 100.0, 0, 0, 0.0, ByteArray(pixels.size), 1)), pixels)
        val manifest = generatedRoot().resolve("solid-card-stack/manifest.json")
        Files.writeString(manifest, Files.readString(manifest).replace(COMMIT, OTHER_COMMIT))
        assertTrue(verify(COMMIT) != 0)

        writeAll(COMMIT)
        val failedDescriptor = GpuEvidenceCatalog.cases.first { it.descriptor.id.value == "solid-card-stack" }.descriptor
        val failedEnvironment = EvidenceEnvironment(COMMIT, "test", "1", "test", "17", null, null, null, true)
        val failedRoute = RouteEvidence("route", "attempt", "complete", "rendered", emptyList(), emptyList(), emptyMap(), GPUBackendRuntimeTelemetry.Empty)
        val failedPixels = ByteArray(failedDescriptor.width * failedDescriptor.height * 4)
        EvidenceBundleWriter(repository, COMMIT).writeGenerated(
            failedDescriptor,
            SceneObservation.Rendered(failedPixels, failedRoute, emptyList(), failedEnvironment, ImageComparison(false, 0.0, failedDescriptor.width * failedDescriptor.height, 255, 1.0, ByteArray(failedPixels.size), 1)),
            failedPixels,
        )
        assertTrue(verify(COMMIT) != 0)
    }

    private fun verify(commit: String) = VerifyEvidenceCliRunner().run(arrayOf("--root", generatedRoot().toString(), "--source-commit", commit))

    private fun writeAll(commit: String) {
        val writer = EvidenceBundleWriter(repository, commit)
        GpuEvidenceCatalog.cases.forEach { evidenceCase ->
            val descriptor = evidenceCase.descriptor
            val environment = EvidenceEnvironment(commit, "test", "1", "test", "17", null, null, null, true)
            val rendered = descriptor.expectation is EvidenceExpectation.ShouldRender
            val route = RouteEvidence("route", "attempt", "complete", if (rendered) "rendered" else "refused", emptyList(), emptyList(), emptyMap(), GPUBackendRuntimeTelemetry.Empty)
            val observation = if (rendered) {
                val pixels = ByteArray(descriptor.width * descriptor.height * 4)
                SceneObservation.Rendered(pixels, route, emptyList(), environment, ImageComparison(true, 100.0, 0, 0, 0.0, ByteArray(pixels.size), 1))
            } else {
                val reason = (descriptor.expectation as EvidenceExpectation.ShouldRefuse).stableReasonCode
                SceneObservation.Refused(reason, "test", 0, route, emptyList(), environment)
            }
            writer.writeGenerated(descriptor, observation, (observation as? SceneObservation.Rendered)?.rgba)
        }
    }

    private fun generatedRoot() = repository.resolve("reports/gpu-renderer/evidence/correctness/generated/$COMMIT")

    private fun deleteTree(path: Path) {
        if (!Files.exists(path)) return
        Files.walk(path).use { stream -> stream.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
    }

    companion object {
        private const val COMMIT = "0123456789abcdef0123456789abcdef01234567"
        private const val OTHER_COMMIT = "fedcba9876543210fedcba9876543210fedcba98"
    }
}
