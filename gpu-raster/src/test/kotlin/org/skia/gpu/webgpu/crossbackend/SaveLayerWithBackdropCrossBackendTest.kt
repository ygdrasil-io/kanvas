package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.SaveLayerWithBackdropGM

@Disabled("STUB.BACKDROP_FILTER: backdrop slot is copy-only, no image-filter execution yet")
class SaveLayerWithBackdropCrossBackendTest {
    @Test
    fun `SaveLayerWithBackdropGM placeholder`() {
        runCrossBackendTest(SaveLayerWithBackdropGM(), rasterFloor = 0.0, gpuFloor = 0.0)
    }
}
