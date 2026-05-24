package org.skia.gpu.webgpu

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.tests.SrcRectConstraintGM

@Disabled("WEBGPU.SRC_RECT_CONSTRAINT: strict/fast sampler-domain parity is not wired through bitmap_shader.wgsl yet")
class SrcRectConstraintWebGpuTest {
    @Test
    fun `SrcRectConstraintGM strict and fast columns`() {
        runGpuCrossTest(SrcRectConstraintGM(), floor = 0.0)
    }
}
