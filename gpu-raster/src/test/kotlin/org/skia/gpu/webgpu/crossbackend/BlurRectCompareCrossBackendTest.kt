package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.BlurRectCompareGM

@Disabled("STUB.BLUR_RECT_COMPARE: needs analytic-vs-brute-force gaussian harness")
class BlurRectCompareCrossBackendTest {
    @Test
    fun `BlurRectCompareGM placeholder`() {
        runCrossBackendTest(BlurRectCompareGM(), rasterFloor = 0.0, gpuFloor = 0.0)
    }
}
