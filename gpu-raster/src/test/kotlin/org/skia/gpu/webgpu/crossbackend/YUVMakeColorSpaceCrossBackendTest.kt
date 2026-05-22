package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.YUVMakeColorSpaceGM

@Disabled("STUB.YUVA_PIXMAPS: SkImage.MakeFromYUVAPixmaps + makeColorSpace not implemented")
class YUVMakeColorSpaceCrossBackendTest {
    @Test
    fun `YUVMakeColorSpaceGM placeholder`() {
        runCrossBackendTest(YUVMakeColorSpaceGM(), rasterFloor = 0.0, gpuFloor = 0.0)
    }
}
