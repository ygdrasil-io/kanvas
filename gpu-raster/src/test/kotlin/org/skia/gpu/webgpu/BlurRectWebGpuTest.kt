package org.skia.gpu.webgpu

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.tests.BlurRectGM

class BlurRectWebGpuTest {
    @Test
    fun `BlurRectGM matches raster`() {
        runGpuCrossTest(BlurRectGM(), floor = 52.0)
    }
}
