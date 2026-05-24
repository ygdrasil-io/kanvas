package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.SaveLayerWithBackdropGM

class SaveLayerWithBackdropCrossBackendTest {
    @Test
    fun `SaveLayerWithBackdropGM matches reference on raster and GPU backends`() {
        runCrossBackendTest(SaveLayerWithBackdropGM(), rasterFloor = 70.0, gpuFloor = 70.0)
    }
}
