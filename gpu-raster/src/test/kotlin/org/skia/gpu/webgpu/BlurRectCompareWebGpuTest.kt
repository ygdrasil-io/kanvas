package org.skia.gpu.webgpu

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.tests.BlurRectCompareGM

class BlurRectCompareWebGpuTest {
    @Test
    fun `BlurRectCompareGM matches WebGPU`() {
        runGpuCrossTest(BlurRectCompareGM(), floor = 89.0)
    }
}
