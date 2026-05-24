package org.skia.gpu.webgpu

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.tests.SaveLayerWithBackdropGM

class SaveLayerWithBackdropWebGpuTest {
    @Test
    fun `SaveLayerWithBackdropGM renders close to reference PNG on the GPU backend`() {
        runGpuCrossTest(SaveLayerWithBackdropGM(), floor = 70.0)
    }
}
