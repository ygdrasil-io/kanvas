package org.skia.gpu.webgpu

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.tests.SaveLayerWithBackdropGM

@Disabled("STUB.BACKDROP_FILTER: backdrop slot is copy-only, no image-filter execution yet")
class SaveLayerWithBackdropWebGpuTest {
    @Test
    fun `SaveLayerWithBackdropGM placeholder`() {
        runGpuCrossTest(SaveLayerWithBackdropGM(), floor = 0.0)
    }
}
