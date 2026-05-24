package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.BlurRectGM

class BlurRectCrossBackendTest {
    @Test
    fun `BlurRectGM matches raster and reference`() {
        runCrossBackendTest(BlurRectGM(), rasterFloor = 55.0, gpuFloor = 52.0)
    }
}
