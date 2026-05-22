package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.BlurRectGM

@Disabled("STUB.BLUR_RECTS_FULL: needs full SkBlurStyle x shader x clip x scale matrix")
class BlurRectCrossBackendTest {
    @Test
    fun `BlurRectGM placeholder`() {
        runCrossBackendTest(BlurRectGM(), rasterFloor = 0.0, gpuFloor = 0.0)
    }
}
