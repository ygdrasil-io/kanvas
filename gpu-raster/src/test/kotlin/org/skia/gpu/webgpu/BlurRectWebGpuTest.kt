package org.skia.gpu.webgpu

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.tests.BlurRectGM

@Disabled("STUB.BLUR_RECTS_FULL: needs full SkBlurStyle x shader x clip x scale matrix")
class BlurRectWebGpuTest {
    @Test
    fun `BlurRectGM placeholder`() {
        runGpuCrossTest(BlurRectGM(), floor = 0.0)
    }
}
