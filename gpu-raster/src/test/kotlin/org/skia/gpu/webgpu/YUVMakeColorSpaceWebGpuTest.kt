package org.skia.gpu.webgpu

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.tests.YUVMakeColorSpaceGM

@Disabled("STUB.YUVA_PIXMAPS: SkImage.MakeFromYUVAPixmaps + makeColorSpace not implemented")
class YUVMakeColorSpaceWebGpuTest {
    @Test
    fun `YUVMakeColorSpaceGM placeholder`() {
        runGpuCrossTest(YUVMakeColorSpaceGM(), floor = 0.0)
    }
}
