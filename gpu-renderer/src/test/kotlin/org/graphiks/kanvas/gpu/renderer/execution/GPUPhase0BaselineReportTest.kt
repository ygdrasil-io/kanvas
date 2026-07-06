package org.graphiks.kanvas.gpu.renderer.execution

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

class GPUPhase0BaselineReportTest {
    @Test
    fun `phase 0 baseline report documents local closure without implementation wording`() {
        val report = listOf(
            Path.of("reports/gpu-renderer/phase-0-baseline.md"),
            Path.of("..", "reports", "gpu-renderer", "phase-0-baseline.md"),
        ).firstOrNull(Files::isRegularFile)
            ?: Path.of("reports/gpu-renderer/phase-0-baseline.md")

        assertTrue(Files.isRegularFile(report), "Phase 0 baseline report must exist")

        val text = Files.readString(report)

        assertTrue(text.contains("Phase 0 baseline locale close"))
        assertTrue(text.contains("command buffers"))
        assertTrue(text.contains("render passes"))
        assertTrue(text.contains("submissions"))
        assertTrue(text.contains("buffers/textures/samplers/bind groups"))
        assertTrue(text.contains("queue writes"))
        assertTrue(text.contains("uniform slab counters"))
        assertTrue(text.contains("maxTextureDimension2D"))
        assertTrue(text.contains("copyBytesPerRowAlignment"))
        assertTrue(text.contains("minUniformBufferOffsetAlignment"))
        assertTrue(text.contains("aggregation par GM"))
        assertTrue(text.contains("integration dashboard GM"))
        assertTrue(text.contains("rapport par famille"))
        assertTrue(!text.contains("@"))
        assertTrue(!text.contains("0x"))
        assertTrue(!text.contains("WGPU"))
        assertTrue(!text.contains("wgpu"))
        assertTrue(!text.contains("Metal"))
    }
}
