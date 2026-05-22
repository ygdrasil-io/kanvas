package org.skia.gpu.webgpu

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.tests.BlurRectCompareGM

@Disabled("STUB.BLUR_RECT_COMPARE: needs analytic-vs-brute-force gaussian harness")
class BlurRectCompareWebGpuTest {
    @Test
    fun `BlurRectCompareGM placeholder`() {
        runGpuCrossTest(BlurRectCompareGM(), floor = 0.0)
    }
}
