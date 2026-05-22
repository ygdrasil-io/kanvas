package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.AyncYUVNoScaleGM

@Disabled("STUB.ASYNC_RESCALE_AND_READ_YUV: requires SkSurface.asyncRescaleAndReadPixelsYUV420")
class AyncYUVNoScaleCrossBackendTest {
    @Test
    fun `AyncYUVNoScaleGM placeholder`() {
        runCrossBackendTest(AyncYUVNoScaleGM(), rasterFloor = 0.0, gpuFloor = 0.0)
    }
}
