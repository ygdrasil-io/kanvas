package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.SrcRectConstraintGM

@Disabled("WEBGPU.SRC_RECT_CONSTRAINT: strict/fast sampler-domain parity is not wired through bitmap_shader.wgsl yet")
class SrcRectConstraintCrossBackendTest {
    @Test
    fun `SrcRectConstraintGM strict and fast columns`() {
        runCrossBackendTest(SrcRectConstraintGM(), rasterFloor = 0.0, gpuFloor = 0.0)
    }
}
