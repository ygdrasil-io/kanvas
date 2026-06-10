package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RuntimeColorFilterSceneEvidenceTest {
    @Test
    fun `runtime LumaToAlpha color filter writes bounded scene evidence`() {
        val evidence = RuntimeColorFilterSceneEvidence.capture()

        assertEquals("runtime.color_filter_luma_to_alpha", evidence.stableId)
        assertEquals("wgsl/runtime_color_filter_luma_to_alpha", evidence.wgslImplementationId)
        assertEquals("none", evidence.cpuFallbackReason)
        assertEquals("none", evidence.webGpuFallbackReason)
        assertTrue(evidence.wgslValidated, "WGSL parser diagnostics: ${evidence.wgslDiagnostics}")
        assertTrue(evidence.wgslEntryPoints.contains("fragment:fs_main"))
        assertTrue(evidence.cpuComparison.similarity >= evidence.threshold)
        assertTrue(evidence.webGpuComparison.similarity >= evidence.threshold)
        assertTrue(evidence.stageOrder.contains("runtime color filter"))
    }
}
