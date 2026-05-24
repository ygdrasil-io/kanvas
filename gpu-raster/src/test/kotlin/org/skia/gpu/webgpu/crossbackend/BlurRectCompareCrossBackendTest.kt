package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.BlurRectCompareGM

class BlurRectCompareCrossBackendTest {
    @Test
    fun `BlurRectCompareGM matches raster and WebGPU`() {
        runCrossBackendTest(BlurRectCompareGM(), rasterFloor = 84.0, gpuFloor = 89.0)
    }
}
