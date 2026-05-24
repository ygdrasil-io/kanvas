package org.skia.gpu.webgpu

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.tests.SurfacePropsGM

class SurfacePropsWebGpuTest {
    @Test
    fun `SurfacePropsGM renders close to reference PNG on the GPU backend`() {
        runGpuCrossTest(SurfacePropsGM(), floor = 95.0)
    }
}
