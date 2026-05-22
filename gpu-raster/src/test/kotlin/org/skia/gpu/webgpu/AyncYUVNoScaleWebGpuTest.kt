package org.skia.gpu.webgpu

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.tests.AyncYUVNoScaleGM

@Disabled("STUB.ASYNC_RESCALE_AND_READ_YUV: requires SkSurface.asyncRescaleAndReadPixelsYUV420")
class AyncYUVNoScaleWebGpuTest {
    @Test
    fun `AyncYUVNoScaleGM placeholder`() {
        runGpuCrossTest(AyncYUVNoScaleGM(), floor = 0.0)
    }
}
