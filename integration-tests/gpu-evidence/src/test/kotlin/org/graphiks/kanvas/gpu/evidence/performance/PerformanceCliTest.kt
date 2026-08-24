package org.graphiks.kanvas.gpu.evidence.performance

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFails
import kotlin.test.assertEquals
import org.graphiks.kanvas.gpu.evidence.catalog.GpuEvidenceCatalog

class PerformanceCliTest {
    @Test fun `parser accepts only the closed v1 frame configuration`() {
        val root = Files.createTempDirectory("gpu-performance-cli")
        val request = PerformanceRequest.parse(arrayOf("--repository-root", root.toString(), "--source-commit", "a".repeat(40)))
        assertEquals(10, request.config.warmupFrames)
        assertEquals(90, request.config.measuredFrames)
        assertFails { PerformanceRequest.parse(arrayOf("--repository-root", root.toString(), "--source-commit", "a".repeat(40), "--warmup-frames", "9")) }
        assertFails { PerformanceRequest.parse(arrayOf("--repository-root", root.toString(), "--source-commit", "a".repeat(40), "--measured-frames", "91")) }
    }

    @Test fun `parser rejects unknown or refused scenes`() {
        val root = Files.createTempDirectory("gpu-performance-cli")
        val prefix = arrayOf("--repository-root", root.toString(), "--source-commit", "a".repeat(40))
        GpuEvidenceCatalog.renderCases.forEach { evidenceCase ->
            assertEquals(evidenceCase.descriptor.id.value, PerformanceRequest.parse(prefix + arrayOf("--scene", evidenceCase.descriptor.id.value)).sceneId)
        }
        assertFails { PerformanceRequest.parse(prefix + arrayOf("--scene", "not-a-scene")) }
        assertFails { PerformanceRequest.parse(prefix + arrayOf("--scene", "custom-runtime-effect-unregistered-refusal")) }
    }
}
